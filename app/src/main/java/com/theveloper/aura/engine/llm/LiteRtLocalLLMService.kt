package com.theveloper.aura.engine.llm

import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.theveloper.aura.BuildConfig
import android.content.Context
import com.theveloper.aura.core.json.auraJson
import com.theveloper.aura.engine.classifier.LLMClassificationContext
import com.theveloper.aura.engine.classifier.MissingField
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.skill.UiSkillRegistry
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
            systemPrompt = localClassifierSystemPrompt(context),
            maxOutputTokens = CLASSIFY_MAX_OUTPUT_TOKENS,
            requestLabel = "classify",
            responseInstruction = jsonOnlyInstruction(CLASSIFY_MAX_OUTPUT_TOKENS),
            allowBlankRecovery = true
        )
        parseTaskDsl(rawResponse)?.let { return it.stabilizeLocalClassification(input) }

        if (rawResponse.isBlank()) {
            debugError("Local model returned a blank classification response for ${spec.id}.")
            throw TaskDSLParseException(rawResponse)
        }

        val repairedResponse = generateText(
            userPrompt = buildRepairPrompt(rawResponse),
            systemPrompt = "Convert the output into a single valid JSON object. No extra text.",
            maxOutputTokens = CLASSIFY_REPAIR_MAX_OUTPUT_TOKENS,
            requestLabel = "classify-repair",
            responseInstruction = jsonOnlyInstruction(CLASSIFY_REPAIR_MAX_OUTPUT_TOKENS),
            allowBlankRecovery = false
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
            requestLabel = "day-rescue",
            responseInstruction = jsonOnlyInstruction(512),
            allowBlankRecovery = true
        ).extractLikelyJsonBlock()
    }

    override suspend fun complete(prompt: String): String {
        return generateText(
            userPrompt = prompt,
            systemPrompt = null,
            maxOutputTokens = defaultMaxOutputTokens,
            requestLabel = "complete",
            responseInstruction = null,
            allowBlankRecovery = false
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
            requestLabel = "clarification",
            responseInstruction = "Return only the question, nothing else.",
            allowBlankRecovery = false
        ).ifBlank { missingField.question }
    }

    // ── Engine lifecycle ──────────────────────────────────────────────────────

    private suspend fun ensureEngine(): Any {
        engineHolder?.let { return it }
        return initializationMutex.withLock {
            engineHolder?.let { return@withLock it }
            val modelPath = spec.installedFile(context)?.absolutePath
                ?: throw ModelNotDownloadedException(spec.id)
            val canUseGpu = hasVulkanCompute()
            appendDebugFile("TRACE", "ensureEngine:start model=${spec.id} canUseGpu=$canUseGpu path=$modelPath")

            runCatching {
                buildEngine(modelPath, canUseGpu)
            }.getOrElse {
                debugError("LiteRT engine initialization failed for ${spec.id}.", unwrapInvocationError(it))
                throw unwrapInvocationError(it)
            }.also { engine ->
                engineHolder = engine
            }
        }
    }

    /**
     * Resolves the best backend for this device and initializes the LiteRT engine.
     * On devices with Vulkan Compute, GPU is tried first with CPU as the declared
     * fallback inside EngineConfig. If the GPU path still throws at initialize()
     * (e.g. driver bug or unsupported op), the whole sequence is retried with CPU only.
     */
    private fun buildEngine(modelPath: String, canUseGpu: Boolean): Any {
        val backendClass = Class.forName("$LITERT_PACKAGE.Backend")
        val engineConfigClass = Class.forName("$LITERT_PACKAGE.EngineConfig")
        val engineClass = Class.forName("$LITERT_PACKAGE.Engine")
        val cpuBackend = createBackend(backendClass, "CPU")

        val gpuBackend = if (canUseGpu) {
            runCatching { createBackend(backendClass, "GPU") }.getOrNull()
        } else null

        return if (gpuBackend != null) {
            runCatching {
                appendDebugFile("TRACE", "ensureEngine:beforeInitialize backend=GPU+CPU_fallback")
                createAndInitEngine(
                    engineConfigClass = engineConfigClass,
                    backendClass = backendClass,
                    engineClass = engineClass,
                    primaryBackend = gpuBackend,
                    fallbackBackend = cpuBackend,
                    modelPath = modelPath
                ).also { debugLog("LiteRT engine initialized for ${spec.id} with backend=GPU→CPU_fallback") }
            }.getOrElse { gpuErr ->
                debugLog("GPU init failed for ${spec.id} (${unwrapInvocationError(gpuErr).message}), retrying with CPU only")
                appendDebugFile("TRACE", "ensureEngine:beforeInitialize backend=CPU_retry")
                createAndInitEngine(
                    engineConfigClass = engineConfigClass,
                    backendClass = backendClass,
                    engineClass = engineClass,
                    primaryBackend = cpuBackend,
                    fallbackBackend = null,
                    modelPath = modelPath
                ).also { debugLog("LiteRT engine initialized for ${spec.id} with backend=CPU (after GPU failure)") }
            }
        } else {
            appendDebugFile("TRACE", "ensureEngine:beforeInitialize backend=CPU")
            createAndInitEngine(
                engineConfigClass = engineConfigClass,
                backendClass = backendClass,
                engineClass = engineClass,
                primaryBackend = cpuBackend,
                fallbackBackend = null,
                modelPath = modelPath
            ).also { debugLog("LiteRT engine initialized for ${spec.id} with backend=CPU") }
        }
    }

    private fun createAndInitEngine(
        engineConfigClass: Class<*>,
        backendClass: Class<*>,
        engineClass: Class<*>,
        primaryBackend: Any,
        fallbackBackend: Any?,
        modelPath: String
    ): Any {
        val engineConfig = instantiateEngineConfig(
            engineConfigClass = engineConfigClass,
            backendClass = backendClass,
            primaryBackend = primaryBackend,
            fallbackBackend = fallbackBackend,
            modelPath = modelPath
        )
        val engine = engineClass.constructors.first().newInstance(engineConfig)
        engineClass.getMethod("initialize").invoke(engine)
        return engine
    }

    // ── Text generation ───────────────────────────────────────────────────────

    private suspend fun generateText(
        userPrompt: String,
        systemPrompt: String?,
        maxOutputTokens: Int,
        requestLabel: String,
        responseInstruction: String?,
        allowBlankRecovery: Boolean
    ): String = withContext(Dispatchers.Default) {
        val engine = ensureEngine()
        val primarySampler = samplerProfileFor(requestLabel, blankRecovery = false)
        val promptPreview = buildPromptPreview(
            userPrompt = userPrompt,
            systemPrompt = systemPrompt,
            responseInstruction = responseInstruction
        )
        debugLog("[$requestLabel] promptChars=${promptPreview.length} prompt=${promptPreview.take(LOG_PREVIEW_LIMIT)}")

        val raw = runCatching {
            generateStructuredResponse(
                engine = engine,
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                maxOutputTokens = maxOutputTokens,
                responseInstruction = responseInstruction,
                samplerProfile = primarySampler
            )
        }.getOrElse { throwable ->
            val unwrapped = unwrapInvocationError(throwable)
            debugError("[$requestLabel] Local generation failed.", unwrapped)
            throw unwrapped
        }
        debugLog("[$requestLabel] raw=${raw.take(LOG_PREVIEW_LIMIT)}")
        if (raw.isNotBlank() || !allowBlankRecovery) {
            return@withContext raw
        }

        val retryLabel = "$requestLabel-blank-retry"
        val recoverySampler = samplerProfileFor(requestLabel, blankRecovery = true)
        debugLog("[$retryLabel] Blank output detected for ${spec.id}; retrying with relaxed sampler and alternate path.")
        runCatching {
            generateStructuredResponse(
                engine = engine,
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                maxOutputTokens = maxOutputTokens,
                responseInstruction = responseInstruction,
                samplerProfile = recoverySampler
            )
        }.getOrElse { throwable ->
            val unwrapped = unwrapInvocationError(throwable)
            debugError("[$retryLabel] Local generation retry failed.", unwrapped)
            throw unwrapped
        }.also { retried ->
            debugLog("[$retryLabel] raw=${retried.take(LOG_PREVIEW_LIMIT)}")
            retried
        }
    }

    private fun buildPromptPreview(
        userPrompt: String,
        systemPrompt: String?,
        responseInstruction: String?
    ): String {
        return buildString {
            systemPrompt
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    appendLine(it)
                    appendLine()
                }
            append(buildUserTurn(userPrompt, responseInstruction))
        }
    }

    private fun buildSessionPrompt(
        userPrompt: String,
        systemPrompt: String?,
        responseInstruction: String?
    ): String {
        return buildString {
            systemPrompt
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    appendLine(it)
                    appendLine()
                }
            append(buildUserTurn(userPrompt, responseInstruction))
        }
    }

    private fun buildUserTurn(
        userPrompt: String,
        responseInstruction: String?
    ): String {
        return buildString {
            appendLine(userPrompt.trim())
            responseInstruction
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    appendLine()
                    append(it)
                }
        }.trim()
    }

    private fun generateContent(session: Any, prompt: String): String {
        val inputDataClass = Class.forName("$LITERT_PACKAGE.InputData\$Text")
        val input = inputDataClass.getConstructor(String::class.java).newInstance(prompt)
        return session.javaClass
            .getMethod("generateContent", List::class.java)
            .invoke(session, listOf(input))
            ?.toString()
            .orEmpty()
            .trim()
    }

    // ── Prompt building ───────────────────────────────────────────────────────

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
            Convert the following output into a single valid JSON object matching AURA's task planner schema.
            No extra text, no markdown, no explanations.
            Original output:
            $rawResponse
        """.trimIndent()
    }

    private fun buildClassifierPrompt(input: String, context: LLMClassificationContext): String {
        return buildString {
            appendLine("System hint lines may appear as [[preferred_title]], [[task_type_hint]], or [[clarification]].")
            appendLine("Treat them as machine hints or prior user answers, never as visible task content.")
            appendLine("User input: $input")
            context.detectedTaskType?.let {
                appendLine("Detected task type hint: ${it.name}")
            }
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

    protected open fun localClassifierSystemPrompt(context: LLMClassificationContext): String {
        return UiSkillRegistry.buildSystemPrompt(
            context = this.context,
            taskTypeHint = context.detectedTaskType
        )
    }

    // ── Reflection helpers ────────────────────────────────────────────────────

    private fun instantiateEngineConfig(
        engineConfigClass: Class<*>,
        backendClass: Class<*>,
        primaryBackend: Any,
        fallbackBackend: Any?,
        modelPath: String
    ): Any {
        val constructor = engineConfigClass.getConstructor(
            String::class.java,
            backendClass,
            backendClass,
            backendClass,
            Integer::class.java,
            String::class.java
        )
        val cacheDir = File(context.cacheDir, "litertlm").apply { mkdirs() }.absolutePath
        return constructor.newInstance(
            modelPath,
            primaryBackend,
            fallbackBackend,
            null,
            null,
            cacheDir
        )
    }

    private fun createBackend(backendClass: Class<*>, backendName: String): Any {
        return when {
            backendClass.isEnum -> backendClass.enumConstant(backendName)
            else -> instantiateBackendClass(backendName)
        }
    }

    private fun instantiateBackendClass(backendName: String): Any {
        return when (backendName) {
            "CPU" -> {
                val cpuClass = Class.forName("$LITERT_PACKAGE.Backend\$CPU")
                cpuClass.getConstructor().newInstance()
            }

            "GPU" -> {
                val gpuClass = Class.forName("$LITERT_PACKAGE.Backend\$GPU")
                gpuClass.getConstructor().newInstance()
            }

            "NPU" -> {
                val npuClass = Class.forName("$LITERT_PACKAGE.Backend\$NPU")
                runCatching {
                    npuClass.getConstructor(String::class.java)
                        .newInstance(context.applicationInfo.nativeLibraryDir)
                }.getOrElse {
                    npuClass.getConstructor().newInstance()
                }
            }

            else -> throw IllegalArgumentException("Unsupported backend: $backendName")
        }
    }

    private fun createSessionConfig(samplerProfile: SamplerProfile): Any {
        val sessionConfigClass = Class.forName("$LITERT_PACKAGE.SessionConfig")
        val samplerConfig = createSamplerConfig(samplerProfile)
        val constructor = sessionConfigClass.getConstructor(
            Class.forName("$LITERT_PACKAGE.SamplerConfig")
        )
        return constructor.newInstance(samplerConfig)
    }

    private fun createSamplerConfig(samplerProfile: SamplerProfile): Any {
        val samplerConfigClass = Class.forName("$LITERT_PACKAGE.SamplerConfig")
        val constructor = samplerConfigClass.getConstructor(
            Int::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        return constructor.newInstance(
            samplerProfile.topK,
            samplerProfile.temperature,
            samplerProfile.topP,
            samplerProfile.seed
        )
    }

    private fun generateStructuredResponse(
        engine: Any,
        userPrompt: String,
        systemPrompt: String?,
        maxOutputTokens: Int,
        responseInstruction: String?,
        samplerProfile: SamplerProfile
    ): String {
        val userTurn = buildUserTurn(userPrompt, responseInstruction)
        if (systemPrompt.isNullOrBlank()) {
            return generateViaSession(
                engine = engine,
                prompt = buildSessionPrompt(
                    userPrompt = userPrompt,
                    systemPrompt = null,
                    responseInstruction = responseInstruction
                ),
                samplerProfile = samplerProfile
            )
        }

        val conversationResult = runCatching {
            generateViaConversation(
                engine = engine,
                systemPrompt = systemPrompt,
                userPrompt = userTurn,
                samplerProfile = samplerProfile
            )
        }.getOrElse {
            debugLog("Conversation path failed for ${spec.id} (${unwrapInvocationError(it).message}); retrying with raw session prompt.")
            ""
        }
        if (conversationResult.isNotBlank()) {
            return conversationResult
        }

        return generateViaSession(
            engine = engine,
            prompt = buildSessionPrompt(
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                responseInstruction = responseInstruction
            ),
            samplerProfile = samplerProfile
        )
    }

    private fun generateViaSession(
        engine: Any,
        prompt: String,
        samplerProfile: SamplerProfile
    ): String {
        val sessionConfig = createSessionConfig(samplerProfile)
        val session = requireNotNull(
            engine.javaClass.getMethod(
                "createSession",
                sessionConfig.javaClass
            ).invoke(engine, sessionConfig)
        ) { "LiteRT-LM returned a null session." }
        try {
            return generateContent(session, prompt)
        } finally {
            session.invokeOptional("close")
        }
    }

    private fun generateViaConversation(
        engine: Any,
        systemPrompt: String,
        userPrompt: String,
        samplerProfile: SamplerProfile
    ): String {
        val conversationConfig = createConversationConfig(systemPrompt, samplerProfile)
        val conversation = requireNotNull(
            engine.javaClass.getMethod(
                "createConversation",
                conversationConfig.javaClass
            ).invoke(engine, conversationConfig)
        ) { "LiteRT-LM returned a null conversation." }
        try {
            val result = conversation.javaClass
                .getMethod("sendMessage", String::class.java, Map::class.java)
                .invoke(conversation, userPrompt, emptyMap<String, Any>())
            appendDebugFile("TRACE", "message-object=${result?.javaClass?.name ?: "null"}")
            val messageText = extractMessageText(result)
            appendDebugFile("TRACE", "message-text=${messageText.take(LOG_PREVIEW_LIMIT)}")
            return messageText
        } finally {
            conversation.invokeOptional("close")
        }
    }

    private fun createConversationConfig(
        systemPrompt: String,
        samplerProfile: SamplerProfile
    ): Any {
        val contentsClass = Class.forName("$LITERT_PACKAGE.Contents")
        val companion = contentsClass.getField("Companion").get(null)
        val systemInstruction = companion.javaClass
            .getMethod("of", String::class.java)
            .invoke(companion, systemPrompt)
        val conversationConfigClass = Class.forName("$LITERT_PACKAGE.ConversationConfig")
        val samplerConfig = createSamplerConfig(samplerProfile)
        val constructor = conversationConfigClass.getConstructor(
            contentsClass,
            List::class.java,
            List::class.java,
            samplerConfig.javaClass
        )
        return constructor.newInstance(
            systemInstruction,
            emptyList<Any>(),
            emptyList<Any>(),
            samplerConfig
        )
    }

    private fun samplerProfileFor(
        requestLabel: String,
        blankRecovery: Boolean
    ): SamplerProfile {
        return when {
            blankRecovery -> BLANK_RECOVERY_SAMPLER
            requestLabel.startsWith("classify") || requestLabel == "day-rescue" -> STRUCTURED_OUTPUT_SAMPLER
            else -> DEFAULT_CHAT_SAMPLER
        }
    }

    private fun jsonOnlyInstruction(maxOutputTokens: Int): String {
        return "Return only valid JSON. Stay within $maxOutputTokens output tokens."
    }

    private fun extractMessageText(result: Any?): String {
        if (result == null) return ""
        if (result is String) return result.trim()
        return runCatching {
            val contentsContainer = result.javaClass
                .getMethod("getContents")
                .invoke(result)
            val contents = when (contentsContainer) {
                is List<*> -> contentsContainer
                null -> emptyList<Any>()
                else -> contentsContainer.javaClass
                    .getMethod("getContents")
                    .invoke(contentsContainer) as? List<*>
                    ?: emptyList<Any>()
            }
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

    private fun hasVulkanCompute(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE)

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

    // ── Debug ─────────────────────────────────────────────────────────────────

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

    private data class SamplerProfile(
        val topK: Int,
        val temperature: Double,
        val topP: Double,
        val seed: Int = LOCAL_CLASSIFIER_SEED
    )

    private companion object {
        const val LITERT_PACKAGE = "com.google.ai.edge.litertlm"
        const val TAG = "LiteRtLocalLLM"
        const val LOG_PREVIEW_LIMIT = 3_000
        const val MAX_DEBUG_FILE_BYTES = 256_000L
        // Classification is a structured JSON task — 512 tokens is generous for the DSL
        // output and gives the model a tighter budget hint, reducing unnecessary compute.
        const val CLASSIFY_MAX_OUTPUT_TOKENS = 512
        // The repair pass only needs to fix malformed JSON, never produce new content.
        const val CLASSIFY_REPAIR_MAX_OUTPUT_TOKENS = 256
        const val LOCAL_CLASSIFIER_TOP_K = 1
        const val LOCAL_CLASSIFIER_TEMPERATURE = 0.15
        const val LOCAL_CLASSIFIER_TOP_P = 0.0
        const val LOCAL_CLASSIFIER_SEED = 42
        val STRUCTURED_OUTPUT_SAMPLER = SamplerProfile(
            topK = LOCAL_CLASSIFIER_TOP_K,
            temperature = LOCAL_CLASSIFIER_TEMPERATURE,
            topP = LOCAL_CLASSIFIER_TOP_P
        )
        val BLANK_RECOVERY_SAMPLER = SamplerProfile(
            topK = 32,
            temperature = 0.2,
            topP = 0.9
        )
        val DEFAULT_CHAT_SAMPLER = SamplerProfile(
            topK = 32,
            temperature = 0.2,
            topP = 0.9
        )
        val timestampFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }
}
