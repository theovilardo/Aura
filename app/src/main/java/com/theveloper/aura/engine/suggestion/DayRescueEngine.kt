package com.theveloper.aura.engine.suggestion

import com.theveloper.aura.domain.model.Suggestion
import com.theveloper.aura.domain.model.SuggestionStatus
import com.theveloper.aura.domain.model.SuggestionType
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.domain.repository.SuggestionRepository
import com.theveloper.aura.domain.repository.TaskRepository
import com.theveloper.aura.domain.repository.UserPatternRepository
import com.theveloper.aura.engine.llm.LLMServiceFactory
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DayRescueEngine @Inject constructor(
    private val taskRepository: TaskRepository,
    private val userPatternRepository: UserPatternRepository,
    private val llmServiceFactory: LLMServiceFactory,
    private val suggestionRepository: SuggestionRepository
) {
    suspend fun runDayRescue() {
        // F5-05 Day Rescue Engine
        val activeTasks = taskRepository.getTasksFlow().first().filter { it.status == TaskStatus.ACTIVE }
        if (activeTasks.isEmpty()) return
        
        val tasksMapList = activeTasks.map { task ->
            mapOf("id" to task.id, "title" to task.title, "type" to task.type.name, "priority" to task.priority)
        }
        val tasksJsonString = JSONArray(tasksMapList).toString()
        
        // Take patterns from all types just to show the LLM user behaviour
        val allPatterns = activeTasks.map { it.type }.distinct().map { type ->
            userPatternRepository.getPatternsForType(type)
        }.flatten()
        val patternsMapList = allPatterns.map { p ->
            mapOf("type" to p.taskType.name, "hour" to p.hourOfDay, "completionRate" to p.completionRate)
        }
        val patternsJsonString = JSONArray(patternsMapList).toString()

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = timeFormat.format(Date())

        val route = llmServiceFactory.resolveAdvancedService()
        if (!route.service.supportsDayRescue) return
        val planJsonString = runCatching {
            route.service.getDayRescuePlan(tasksJsonString, patternsJsonString, currentTime)
        }.getOrElse {
            return
        }
        
        // LLM replies with a JSON array of day rescue action objects
        val planArray = try { JSONArray(planJsonString) } catch (e: Exception) { return }
        
        for (i in 0 until planArray.length()) {
            val element = planArray.getJSONObject(i)
            val taskId = element.getString("taskId")
            val daysOffset = element.getInt("daysOffset")
            val reasoning = element.getString("reasoning")
            
            // By requesting an explicit shift, we can map this onto RESCHEDULE_REMINDER or a specific DayRescue capability.
            // Since SuggestionType doesn't have an explicit DAY_RESCUE we reuse RESCHEDULE_REMINDER passing the offset.
            
            val payload = JSONObject().apply {
                put("daysOffset", daysOffset)
            }.toString()
            
            val suggestion = Suggestion(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                type = SuggestionType.RESCHEDULE_REMINDER, // Adaptable
                status = SuggestionStatus.PENDING,
                payloadJson = payload,
                reasoning = reasoning,
                createdAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + (24L * 60 * 60 * 1000) // The day rescue lives just one day
            )
            
            suggestionRepository.save(suggestion)
        }
    }
}
