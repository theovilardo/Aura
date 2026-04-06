package com.theveloper.aura.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theveloper.aura.domain.model.AutomationStatus
import com.theveloper.aura.domain.model.AutomationStepType
import com.theveloper.aura.domain.model.NotificationChannel as AuraChannel
import com.theveloper.aura.domain.repository.AutomationRepository
import com.theveloper.aura.domain.repository.TaskRepository
import com.theveloper.aura.engine.llm.LLMServiceFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes an automation's [AutomationExecutionPlan] step by step.
 *
 * Steps:
 * 1. GATHER_CONTEXT: Query local DB for relevant data.
 * 2. LLM_PROCESS: Send gathered context to LLM for processing.
 * 3. OUTPUT: Deliver result (notification, task update, etc.).
 */
@HiltWorker
class AutomationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val automationRepository: AutomationRepository,
    private val taskRepository: TaskRepository,
    private val llmServiceFactory: LLMServiceFactory
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val automationId = inputData.getString(KEY_AUTOMATION_ID) ?: return@withContext Result.failure()

        val automation = automationRepository.getById(automationId) ?: return@withContext Result.failure()

        if (automation.status != AutomationStatus.ACTIVE) {
            Log.d(TAG, "Automation $automationId is ${automation.status}, skipping")
            return@withContext Result.success()
        }

        try {
            val gatheredContext = StringBuilder()
            var llmResult = ""

            for (step in automation.executionPlan.steps) {
                when (step.type) {
                    AutomationStepType.GATHER_CONTEXT -> {
                        val data = gatherContext(step.params)
                        gatheredContext.appendLine(data)
                    }
                    AutomationStepType.LLM_PROCESS -> {
                        llmResult = processWithLLM(
                            instruction = step.params["instruction"] ?: automation.prompt,
                            context = gatheredContext.toString()
                        )
                    }
                    AutomationStepType.OUTPUT -> {
                        deliverOutput(
                            automation.title,
                            llmResult.ifBlank { gatheredContext.toString() },
                            step.params
                        )
                    }
                }
            }

            automationRepository.updateExecutionResult(
                id = automationId,
                executedAt = System.currentTimeMillis(),
                resultJson = llmResult.take(5000), // truncate for storage
                failureCount = 0
            )

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Automation $automationId failed", e)
            val newFailCount = automation.failureCount + 1
            if (newFailCount >= automation.maxRetries) {
                automationRepository.updateStatus(automationId, AutomationStatus.FAILED)
            } else {
                automationRepository.updateExecutionResult(
                    id = automationId,
                    executedAt = System.currentTimeMillis(),
                    resultJson = "ERROR: ${e.message}",
                    failureCount = newFailCount
                )
            }
            Result.retry()
        }
    }

    /**
     * Gather context from local database based on step params.
     */
    private suspend fun gatherContext(params: Map<String, String>): String {
        val query = params["query"] ?: "active_items"
        return when {
            query.contains("task") || query.contains("active_items") -> {
                val tasks = taskRepository.getAllTasks()
                tasks.joinToString("\n") { "- [${it.status}] ${it.title} (${it.type})" }
            }
            else -> "No context gathered for query: $query"
        }
    }

    /**
     * Process gathered context through LLM.
     */
    private suspend fun processWithLLM(instruction: String, context: String): String {
        return try {
            val route = llmServiceFactory.resolvePrimaryService()
            val prompt = buildString {
                appendLine("You are a personal assistant automation. Respond in the same language as the instruction.")
                appendLine()
                appendLine("Instruction: $instruction")
                appendLine()
                appendLine("Context data:")
                appendLine(context)
                appendLine()
                appendLine("Provide a concise, useful response:")
            }
            route.service.complete(prompt)
        } catch (e: Exception) {
            Log.w(TAG, "LLM processing failed", e)
            "Could not process with AI: ${e.message}"
        }
    }

    /**
     * Deliver the automation result.
     */
    private fun deliverOutput(title: String, result: String, params: Map<String, String>) {
        val format = params["format"] ?: "notification"
        when (format) {
            "notification" -> showNotification(title, result)
            else -> showNotification(title, result) // default to notification
        }
    }

    private fun showNotification(title: String, body: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "AUTOMATIONS"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "AURA Automations", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Automation execution results" }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(title.hashCode(), notification)
    }

    companion object {
        private const val TAG = "AutomationWorker"
        const val KEY_AUTOMATION_ID = "automation_id"
    }
}
