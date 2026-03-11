package com.theveloper.aura.engine.classifier

import android.content.Context
import com.theveloper.aura.BuildConfig
import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroqLLMService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) : LLMService {

    override suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput {
        if (BuildConfig.GROQ_API_KEY.isBlank()) {
            return TaskDSLBuilder.buildFallback(input, context)
        }

        return runCatching {
            val response = withContext(Dispatchers.IO) {
                val requestBody = auraJson.encodeToString(
                    GroqChatRequest(
                        messages = listOf(
                            GroqMessage(role = "system", content = loadSystemPrompt()),
                            GroqMessage(role = "user", content = buildUserPrompt(input, context))
                        )
                    )
                )

                val request = Request.Builder()
                    .url(BuildConfig.GROQ_BASE_URL + "chat/completions")
                    .header("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                    .header("Content-Type", "application/json")
                    .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Groq request failed with HTTP ${response.code}" }
                    response.body?.string().orEmpty()
                }
            }

            val payload = auraJson.decodeFromString<GroqChatResponse>(response)
            val content = payload.choices.firstOrNull()?.message?.content?.trim().orEmpty()
            auraJson.decodeFromString<TaskDSLOutput>(stripCodeFences(content))
        }.getOrElse {
            TaskDSLBuilder.buildFallback(input, context)
        }
    }

    private fun loadSystemPrompt(): String {
        return context.assets.open("system_prompt.txt").bufferedReader().use { it.readText() }
    }

    private fun buildUserPrompt(input: String, context: LLMClassificationContext): String {
        return buildString {
            appendLine("Input del usuario: $input")
            appendLine("Intent hint: ${context.intentHint?.name ?: "UNKNOWN"}")
            appendLine("Confidence: ${context.intentConfidence}")
            appendLine("Fechas extraidas: ${context.extractedDates}")
            appendLine("Numeros extraidos: ${context.extractedNumbers}")
            appendLine("Ubicaciones extraidas: ${context.extractedLocations}")
        }
    }

    private fun stripCodeFences(value: String): String {
        return value
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    @Serializable
    private data class GroqChatRequest(
        val model: String = "llama-3.1-8b-instant",
        val temperature: Double = 0.2,
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
