package com.theveloper.aura.engine.llm

import android.content.Context
import com.theveloper.aura.BuildConfig
import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.data.repository.AppSettingsRepository
import com.theveloper.aura.engine.classifier.LLMClassificationContext
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class GroqLLMService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val appSettingsRepository: AppSettingsRepository
) : LLMService {

    override val tier: LLMTier = LLMTier.GROQ_API

    override fun isAvailable(): Boolean = currentApiKey().isNotBlank()

    override suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput {
        require(isAvailable()) { throw GroqAPIKeyMissingException() }

        val response = callGroq(
            messages = listOf(
                GroqMessage(role = "system", content = loadAsset("system_prompt.txt")),
                GroqMessage(role = "user", content = buildClassifierPrompt(input, context))
            )
        )
        return runCatching {
            val normalized = response.stripCodeFences().normalizeTaskDslJson()
            auraJson.decodeFromString<TaskDSLOutput>(normalized)
        }.getOrElse {
            throw TaskDSLParseException(response)
        }
    }

    override suspend fun getDayRescuePlan(
        tasksJson: String,
        patternsJson: String,
        currentTime: String
    ): String {
        if (!isAvailable()) {
            return "[]"
        }

        val prompt = buildString {
            appendLine("Current time: $currentTime")
            appendLine("Active tasks: $tasksJson")
            appendLine("User patterns: $patternsJson")
            appendLine("Return only a valid JSON array.")
        }
        return callGroq(
            messages = listOf(
                GroqMessage(role = "system", content = loadAsset("day_rescue_prompt.txt")),
                GroqMessage(role = "user", content = prompt)
            ),
            maxTokens = 512
        ).stripCodeFences()
    }

    override suspend fun complete(prompt: String): String {
        require(isAvailable()) { throw GroqAPIKeyMissingException() }
        return callGroq(messages = listOf(GroqMessage(role = "user", content = prompt)))
    }

    private suspend fun callGroq(
        messages: List<GroqMessage>,
        maxTokens: Int = 512
    ): String = withContext(Dispatchers.IO) {
        val requestBody = auraJson.encodeToString(
            GroqChatRequest(
                model = "gemma2-9b-it",
                maxCompletionTokens = maxTokens,
                messages = messages
            )
        )

        val request = Request.Builder()
            .url(BuildConfig.GROQ_BASE_URL + "chat/completions")
            .header("Authorization", "Bearer ${currentApiKey()}")
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw GroqAPIException(response.code)
            }
            val payload = auraJson.decodeFromString<GroqChatResponse>(response.body?.string().orEmpty())
            payload.choices.firstOrNull()?.message?.content?.trim().orEmpty()
        }
    }

    private fun loadAsset(name: String): String {
        return context.assets.open(name).bufferedReader().use { it.readText() }
    }

    private fun buildClassifierPrompt(input: String, context: LLMClassificationContext): String {
        return buildString {
            appendLine("User input: $input")
            context.extractedDates.takeIf { it.isNotEmpty() }?.let {
                appendLine("Extracted dates (epoch ms): $it")
            }
            context.extractedNumbers.takeIf { it.isNotEmpty() }?.let {
                appendLine("Extracted numbers: $it")
            }
            context.extractedLocations.takeIf { it.isNotEmpty() }?.let {
                appendLine("Extracted locations: $it")
            }
            if (context.memoryContext.isNotBlank()) {
                appendLine("Relevant memory:")
                appendLine(context.memoryContext)
            }
        }
    }

    private fun currentApiKey(): String {
        val storedKey = runBlocking {
            appSettingsRepository.getSnapshot().groqAccessToken.trim()
        }
        return storedKey.ifBlank { BuildConfig.GROQ_API_KEY.trim() }
    }

    @Serializable
    private data class GroqChatRequest(
        val model: String,
        val temperature: Double = 0.1,
        val maxCompletionTokens: Int,
        val messages: List<GroqMessage>
    )

    @Serializable
    private data class GroqMessage(
        val role: String,
        val content: String
    )

    @Serializable
    private data class GroqChatResponse(
        val choices: List<GroqChoice> = emptyList()
    )

    @Serializable
    private data class GroqChoice(
        val message: GroqMessage
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
