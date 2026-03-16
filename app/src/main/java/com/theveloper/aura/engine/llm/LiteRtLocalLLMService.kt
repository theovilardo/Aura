package com.theveloper.aura.engine.llm

import android.util.Log
import com.theveloper.aura.BuildConfig
import android.content.Context
import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.engine.classifier.LLMClassificationContext
import com.theveloper.aura.engine.classifier.MissingField
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString

abstract class LiteRtLocalLLMService constructor(
    @ApplicationContext private val context: Context,
    private val spec: ModelSpec,
    override val tier: LLMTier,
    private val backendName: String,
    private val defaultMaxOutputTokens: Int
) : LLMService {

    private val initializationMutex = Mutex()
    @Volatile
    private var engineHolder: Any? = null
    private val debugFile = File(context.filesDir, "debug/llm-debug.log")

    override fun isAvailable(): Boolean = spec.installedFile(context) != null

    suspend fun initialize() {
        ensureEngine()
    }

    fun release() {
        engineHolder?.invokeOptional("close")
        engineHolder = null
    }

    override suspend fun classify(input: String, context: LLMClassificationContext): TaskDSLOutput {
        val prompt = buildClassifierPrompt(input, context)
        val rawResponse = generateText(
            userPrompt = prompt,
            systemPrompt = localClassifierSystemPrompt(),
            maxOutputTokens = defaultMaxOutputTokens,
            requestLabel = "classify"
        )
        parseTaskDsl(rawResponse)?.let { return it.stabilizeLocalClassification(input) }

        val repairedResponse = generateText(
            userPrompt = buildRepairPrompt(rawResponse),
            systemPrompt = "Convert the output into a single valid JSON object. No extra text.",
            maxOutputTokens = defaultMaxOutputTokens,
            requestLabel = "classify-repair"
        )
        parseTaskDsl(repairedResponse)?.let { return it.stabilizeLocalClassification(input) }

        debugError("Failed to parse local model response for classification.")
        throw TaskDSLParseException(rawResponse)
    }

    override suspend fun getDayRescuePlan(
        tasksJson: String,
        patternsJson: String,
        currentTime: String
    ): String {
        val prompt = buildString {
            appendLine("Current time: $currentTime")
            appendLine("Active tasks: $tasksJson")
            appendLine("User patterns: $patternsJson")
            appendLine("Return only a valid JSON array.")
        }
        return generateText(
            userPrompt = prompt,
            systemPrompt = loadAsset("day_rescue_prompt.txt"),
            maxOutputTokens = 512,
            requestLabel = "day-rescue"
        ).extractLikelyJsonBlock()
    }

    override suspend fun complete(prompt: String): String {
        return generateText(
            userPrompt = prompt,
            systemPrompt = null,
            maxOutputTokens = defaultMaxOutputTokens,
            requestLabel = "complete"
        )
    }

    override suspend fun generateClarification(taskContext: String, missingField: MissingField): String {
        val prompt = """
            Task context: "$taskContext"
            Missing field: ${missingField.fieldName}
            Write a single short, friendly question in the same language as the task context.
            Return only the question, nothing else.
        """.trimIndent()
        return generateText(
            userPrompt = prompt,
            systemPrompt = null,
            maxOutputTokens = 128,
            requestLabel = "clarification"
        ).ifBlank { missingField.question }
    }

    private suspend fun ensureEngine(): Any {
        engineHolder?.let { return it }
        return initializationMutex.withLock {
            engineHolder?.let { return@withLock it }
            val modelPath = spec.installedFile(context)?.absolutePath
                ?: throw ModelNotDownloadedException(spec.id)
            appendDebugFile("TRACE", "ensureEngine:start model=${spec.id} backend=$backendName path=$modelPath")

            runCatching {
                val backendClass = Class.forName("$LITERT_PACKAGE.Backend")
                val engineConfigClass = Class.forName("$LITERT_PACKAGE.EngineConfig")
                val engineClass = Class.forName("$LITERT_PACKAGE.Engine")
                val backend = backendClass.enumConstant(backendName)
                val engineConfig = instantiateEngineConfig(
                    engineConfigClass = engineConfigClass,
                    backend = backend,
                    modelPath = modelPath
                )
                val engine = engineClass.constructors.first()
                    .newInstance(engineConfig)
                appendDebugFile("TRACE", "ensureEngine:beforeInitialize")
                engineClass.getMethod("initialize").invoke(engine)
                debugLog("LiteRT engine initialized for ${spec.id} with backend=$backendName")
                engine.also { initialized ->
                    engineHolder = initialized
                }
            }.getOrElse {
                debugError("LiteRT engine initialization failed for ${spec.id}.", unwrapInvocationError(it))
                throw unwrapInvocationError(it)
            }
        }
    }

    private suspend fun generateText(
        userPrompt: String,
        systemPrompt: String?,
        maxOutputTokens: Int,
        requestLabel: String
    ): String = withContext(Dispatchers.Default) {
        val engine = ensureEngine()
        val prompt = buildConversationPrompt(userPrompt, systemPrompt, maxOutputTokens)
        debugLog("[$requestLabel] promptChars=${prompt.length} prompt=${prompt.take(LOG_PREVIEW_LIMIT)}")
        val sessionConfig = createSessionConfig()
        val session = requireNotNull(
            engine.javaClass.getMethod(
                "createSession",
                sessionConfig.javaClass
            ).invoke(engine, sessionConfig)
        ) { "LiteRT-LM returned a null session." }
        try {
            val raw = generateContent(session, prompt)
            debugLog("[$requestLabel] raw=${raw.take(LOG_PREVIEW_LIMIT)}")
            raw
        } catch (throwable: Throwable) {
            val unwrapped = unwrapInvocationError(throwable)
            debugError("[$requestLabel] Local generation failed.", unwrapped)
            throw unwrapped
        } finally {
            session.invokeOptional("close")
        }
    }

    private fun buildConversationPrompt(
        userPrompt: String,
        systemPrompt: String?,
        maxOutputTokens: Int
    ): String {
        return buildString {
            systemPrompt
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    appendLine(it)
                    appendLine()
                }
            appendLine(userPrompt.trim())
            appendLine()
            append("Return only valid JSON. Stay within $maxOutputTokens output tokens.")
        }
    }

    private fun generateContent(session: Any, prompt: String): String {
        val inputDataClass = Class.forName("$LITERT_PACKAGE.InputData\$Text")
        val input = inputDataClass.getConstructor(String::class.java).newInstance(prompt)
        val result = session.javaClass
            .getMethod("generateContent", List::class.java)
            .invoke(session, listOf(input))
        val messageText = extractMessageText(result)
        appendDebugFile("TRACE", "message-object=${result?.javaClass?.name ?: "null"}")
        appendDebugFile("TRACE", "message-text=${messageText.take(LOG_PREVIEW_LIMIT)}")
        return messageText
    }

    private fun parseTaskDsl(rawResponse: String): TaskDSLOutput? {
        val candidate = rawResponse.extractLikelyJsonBlock()
        debugLog("json-candidate=${candidate.take(LOG_PREVIEW_LIMIT)}")
        val normalized = candidate.normalizeTaskDslJson()
        if (normalized != candidate) {
            debugLog("normalized-json=${normalized.take(LOG_PREVIEW_LIMIT)}")
        }
        return runCatching {
            auraJson.decodeFromString<TaskDSLOutput>(normalized)
        }.getOrElse {
            debugError("Could not parse local model output.", it)
            null
        }
    }

    private fun buildRepairPrompt(rawResponse: String): String {
        return """
            Convert the following output into a single valid JSON object matching the TaskDSLOutput schema.
            No extra text, no markdown, no explanations.
            Original output:
            $rawResponse
        """.trimIndent()
    }

    private fun instantiateEngineConfig(
        engineConfigClass: Class<*>,
        backend: Any,
        modelPath: String
    ): Any {
        val constructor = engineConfigClass.getConstructor(
            String::class.java,
            backend.javaClass,
            backend.javaClass,
            backend.javaClass,
            Integer::class.java,
            String::class.java
        )
        val cacheDir = File(context.cacheDir, "litertlm").apply { mkdirs() }.absolutePath
        return constructor.newInstance(
            modelPath,
            backend,
            null,
            null,
            null,
            cacheDir
        )
    }

    private fun extractMessageText(result: Any?): String {
        if (result == null) return ""
        return runCatching {
            val contents = result.javaClass
                .getMethod("getContents")
                .invoke(result) as? List<*>
                ?: emptyList<Any>()
            contents.joinToString(separator = "") { content ->
                runCatching {
                    content?.javaClass
                        ?.getMethod("getText")
                        ?.invoke(content)
                        ?.toString()
                        .orEmpty()
                }.getOrDefault(content?.toString().orEmpty())
            }.trim()
        }.getOrElse {
            result.toString().trim()
        }
    }

    private fun createSessionConfig(): Any {
        val sessionConfigClass = Class.forName("$LITERT_PACKAGE.SessionConfig")
        val samplerConfig = createSamplerConfig()
        val constructor = sessionConfigClass.getConstructor(
            Class.forName("$LITERT_PACKAGE.SamplerConfig")
        )
        return constructor.newInstance(samplerConfig)
    }

    private fun createSamplerConfig(): Any {
        val samplerConfigClass = Class.forName("$LITERT_PACKAGE.SamplerConfig")
        val constructor = samplerConfigClass.getConstructor(
            Int::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        return constructor.newInstance(
            LOCAL_CLASSIFIER_TOP_K,
            LOCAL_CLASSIFIER_TEMPERATURE,
            LOCAL_CLASSIFIER_TOP_P,
            LOCAL_CLASSIFIER_SEED
        )
    }

    private fun Class<*>.enumConstant(name: String): Any {
        val constants = enumConstants ?: emptyArray<Any>()
        return constants.firstOrNull { it.toString() == name }
            ?: throw IllegalArgumentException("Backend.$name does not exist")
    }

    private fun Any.invokeOptional(methodName: String) {
        runCatching {
            javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 }
                ?.invoke(this)
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
                appendLine(context.memoryContext.trim().take(400))
            }
        }
    }

    protected open fun localClassifierSystemPrompt(): String {
        return """
            Task UI builder. Return ONLY valid JSON. No markdown fences, no extra text.
            LANGUAGE: All text fields must match the user's input language.

            JSON schema:
            {"semantic":{"action":"verb phrase","items":["1-3 word nouns"],"subject":"context","goal":"measurable or empty","frequency":"DAILY or WEEKLY or empty"},"title":"short title from input","type":"GENERAL|TRAVEL|HABIT|HEALTH|PROJECT|FINANCE|EVENT|GOAL","priority":0,"targetDateMs":0,"components":[...],"reminders":[],"fetchers":[]}

            Each component: {"type":"...","sortOrder":N,"config":{...},"populatedFromInput":false,"needsClarification":false}
            config_type MUST equal the component type.

            CHECKLIST: {"config_type":"CHECKLIST","label":"...","allowAddItems":true,"items":[{"label":"...","isSuggested":false}]}
            Items = real things from prompt (products, exercises, steps). Inferred items: isSuggested=true. Unknown: items=[], needsClarification=true.
            NOTES: {"config_type":"NOTES","text":"markdown","isMarkdown":true}
            Text = real useful content with ## headers, **bold**, - lists. Never empty placeholders.
            METRIC_TRACKER: {"config_type":"METRIC_TRACKER","unit":"kg|km|reps|...","label":"...","history":[],"goal":number_or_null}
            COUNTDOWN: {"config_type":"COUNTDOWN","targetDate":epoch_ms_or_0,"label":"..."}
            HABIT_RING: {"config_type":"HABIT_RING","frequency":"DAILY","label":"...","targetCount":1}
            PROGRESS_BAR: {"config_type":"PROGRESS_BAR","source":"MANUAL","label":"...","manualProgress":0.0}

            When to use: items→CHECKLIST, recurring→HABIT_RING, measurable goal→METRIC_TRACKER, date→COUNTDOWN, useful info→NOTES. Minimum components only.
            Type: HEALTH(physical fitness/medical/body metrics ONLY), EVENT(future date), TRAVEL(trip), FINANCE(money), HABIT(recurring), GOAL(learning/skill development/personal growth/milestones/roadmap), PROJECT(deliverable), else GENERAL. IMPORTANT: learning, studying, recommendations, self-improvement → GOAL, never HEALTH.

            Example — input: "gym routine, recommend exercises for legs"
            {"semantic":{"action":"workout","items":["squats","lunges","leg press","calf raises"],"subject":"gym","goal":"","frequency":""},"title":"Gym routine","type":"HEALTH","priority":0,"targetDateMs":0,"components":[{"type":"CHECKLIST","sortOrder":0,"config":{"config_type":"CHECKLIST","label":"Exercises","allowAddItems":true,"items":[{"label":"squats","isSuggested":true},{"label":"lunges","isSuggested":true},{"label":"leg press","isSuggested":true},{"label":"calf raises","isSuggested":true}]},"populatedFromInput":true,"needsClarification":false},{"type":"NOTES","sortOrder":1,"config":{"config_type":"NOTES","text":"## Workout tips\n- **Sets:** 3-4 per exercise\n- **Reps:** 8-12\n- **Rest:** 60-90s between sets\n- Warm up 5 min before starting","isMarkdown":true},"populatedFromInput":true,"needsClarification":false}],"reminders":[],"fetchers":[]}
        """.trimIndent()
    }

    private fun debugLog(message: String) {
        if (!BuildConfig.DEBUG) return
        Log.i(TAG, message)
        appendDebugFile("INFO", message)
    }

    private fun debugError(message: String, throwable: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        Log.e(TAG, message, throwable)
        appendDebugFile("ERROR", buildString {
            append(message)
            throwable?.let {
                append("\n")
                append(Log.getStackTraceString(it))
            }
        })
    }

    private fun appendDebugFile(level: String, message: String) {
        runCatching {
            debugFile.parentFile?.mkdirs()
            if (debugFile.exists() && debugFile.length() > MAX_DEBUG_FILE_BYTES) {
                debugFile.writeText("")
            }
            val timestamp = timestampFormatter.format(Date())
            debugFile.appendText("[$timestamp][$level] $message\n\n")
        }
    }

    private fun unwrapInvocationError(throwable: Throwable): Throwable {
        return (throwable as? java.lang.reflect.InvocationTargetException)?.targetException ?: throwable
    }

    private companion object {
        const val LITERT_PACKAGE = "com.google.ai.edge.litertlm"
        const val TAG = "LiteRtLocalLLM"
        const val LOG_PREVIEW_LIMIT = 3_000
        const val MAX_DEBUG_FILE_BYTES = 256_000L
        const val LOCAL_CLASSIFIER_TOP_K = 1
        const val LOCAL_CLASSIFIER_TEMPERATURE = 0.15
        const val LOCAL_CLASSIFIER_TOP_P = 0.0
        const val LOCAL_CLASSIFIER_SEED = 42
        val timestampFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }
}
