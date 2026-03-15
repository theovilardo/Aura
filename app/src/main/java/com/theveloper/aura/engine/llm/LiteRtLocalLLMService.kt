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

    override fun isAvailable(): Boolean = spec.file(context).exists()

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
            val modelPath = spec.file(context).takeIf { it.exists() }?.absolutePath
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
            debugError("[$requestLabel] La generación local falló.", unwrapped)
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
            debugError("No se pudo parsear la salida del modelo local.", it)
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
            ?: throw IllegalArgumentException("No existe Backend.$name")
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

    private fun localClassifierSystemPrompt(): String {
        return """
            You are AURA's task UI builder. Output a single valid JSON object. No markdown, no extra text.

            LANGUAGE RULE: All user-facing text (title, label, text, items) MUST be in the same language as the user's input.

            Root fields (all required): semantic, title, type, priority, targetDateMs, components, reminders, fetchers.
            title is REQUIRED: never empty, derived from user input.
            type: GENERAL|TRAVEL|HABIT|HEALTH|PROJECT|FINANCE|EVENT|GOAL

            semantic (always first, before components):
            {"action":"verb+complement","items":["atomic 1-3 word nouns, no verbs"],"subject":"context","goal":"measurable result or empty","frequency":"recurrence or empty"}

            COMPONENT CONFIGS (config_type must equal component type):
            CHECKLIST: config_type, label, allowAddItems, items=[{"label":"...","isSuggested":false}]
              → items MUST be the real things from the prompt: products, ingredients, exercises, steps.
              → Add inferred items with isSuggested=true. Unknown → leave empty + needsClarification=true.
            NOTES: config_type, text (markdown string), isMarkdown=true
              → text MUST contain useful content from the prompt. Use ## headers, **bold**, - lists.
              → Recipe → steps + tips. Workout → exercise notes. Event → agenda. Never use placeholders.
            METRIC_TRACKER: config_type, unit, label, history=[], goal (optional number)
              → unit MUST match context: "kg","lb","km","ml","reps","h","steps","USD","€", etc.
              → goal = the numeric target the user mentioned. Omit if not specified.
            COUNTDOWN: config_type, targetDate (epoch ms, 0 if unknown), label
              → use extracted date if available. No date → targetDate=0 + needsClarification=true.
            HABIT_RING: config_type, frequency (DAILY or WEEKLY), label, targetCount (optional int)
              → daily/everyday → DAILY. weekly/each week → WEEKLY. Implied recurrence → DAILY.
            PROGRESS_BAR: config_type, source="MANUAL", label, manualProgress=0.0
            DATA_FEED: config_type, fetcherConfigId, displayLabel, status="LOADING"
              → Only for explicit live data requests (weather, exchange rates, flights).

            SELECTION RULES:
            semantic.items not empty → CHECKLIST (fill items from prompt)
            semantic.frequency not empty → HABIT_RING
            semantic.goal measurable → METRIC_TRACKER (set unit + goal)
            Future date/deadline → COUNTDOWN
            Sequential completable stages → PROGRESS_BAR
            Useful context or info → NOTES (fill with real content)
            Live external data requested → DATA_FEED
            Nothing specific → GENERAL + NOTES only
            Minimum useful components only. Do not add speculatively.

            TYPE: EVENT(specific future date/event), GOAL(milestones+progress), HABIT(recurring), HEALTH(fitness/medical), TRAVEL(trip), FINANCE(budget/savings), PROJECT(deliverable), else GENERAL

            Example 1 — shopping list: "I need milk, eggs, bread and coffee for the week"
            semantic: {"action":"buy groceries","items":["milk","eggs","bread","coffee"],"subject":"supermarket","goal":"","frequency":""}
            title: "Weekly shopping", type: "GENERAL"
            components: [CHECKLIST items=[milk,eggs,bread,coffee], NOTES text="## Shopping tips\n- Check expiry dates\n- Buy in bulk if on sale"]

            Example 2 — recipe: "chocolate cake recipe"
            semantic: {"action":"bake cake","items":["200g flour","150g sugar","3 eggs","100g butter","50g cocoa"],"subject":"kitchen","goal":"","frequency":""}
            title: "Chocolate cake", type: "GENERAL"
            components: [CHECKLIST items=[200g flour,150g sugar,3 eggs,100g butter,50g cocoa isSuggested=true], NOTES text="## Preparation\n1. Preheat oven 180°C\n2. Mix dry ingredients\n3. Beat eggs with butter\n4. Combine and bake 35 min\n\n**Tip:** don't overmix the batter"]

            Example 3 — workout: "gym routine to lose weight, recommend exercises"
            semantic: {"action":"workout","items":["squats","push-ups","lunges","plank","burpees"],"subject":"gym","goal":"lose weight","frequency":"daily"}
            title: "Gym routine", type: "HEALTH"
            components: [CHECKLIST items=[squats,push-ups,lunges,plank,burpees isSuggested=true], HABIT_RING freq=DAILY, METRIC_TRACKER unit="kg" goal=target, NOTES text="## Routine tips\n- **Rest:** 60s between sets\n- **Sets:** 3–4 per exercise\n- **Warm up** 5 min before starting"]

            Example 4 — event: "meeting with the client on Friday at 3pm"
            semantic: {"action":"attend meeting","items":[],"subject":"client","goal":"","frequency":""}
            title: "Meeting with client", type: "EVENT"
            components: [COUNTDOWN targetDate=from extracted date, NOTES text="## Meeting agenda\n- Review project status\n- Discuss next steps\n- **Bring:** proposal document"]
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
