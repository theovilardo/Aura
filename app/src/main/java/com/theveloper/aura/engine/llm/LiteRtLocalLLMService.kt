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
        parseTaskDsl(rawResponse)?.let { return it }

        val repairedResponse = generateText(
            userPrompt = buildRepairPrompt(rawResponse),
            systemPrompt = "Convertí la salida en un único JSON válido. No agregues texto extra.",
            maxOutputTokens = defaultMaxOutputTokens,
            requestLabel = "classify-repair"
        )
        parseTaskDsl(repairedResponse)?.let { return it }

        debugError("No se pudo parsear la respuesta del modelo local para clasificación.")
        throw TaskDSLParseException(rawResponse)
    }

    override suspend fun getDayRescuePlan(
        tasksJson: String,
        patternsJson: String,
        currentTime: String
    ): String {
        val prompt = buildString {
            appendLine("Hora actual: $currentTime")
            appendLine("Tareas activas: $tasksJson")
            appendLine("Patrones del usuario: $patternsJson")
            appendLine("Respondé únicamente con un array JSON válido.")
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
            Contexto de tarea: "$taskContext"
            Campo faltante: ${missingField.fieldName}
            Escribí una sola pregunta corta y amigable en español.
            Solo la pregunta.
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
        ) { "LiteRT-LM devolvió una sesión nula." }
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
            append("Devolvé solo JSON válido y mantenete dentro de $maxOutputTokens tokens de salida.")
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
            Convertí la siguiente salida en un único JSON válido que cumpla exactamente el schema TaskDSLOutput.
            No agregues texto, markdown ni explicaciones.
            Salida original:
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
        return constructor.newInstance(20, 0.9, 0.0, 42)
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
            appendLine("Input del usuario: $input")
            appendLine("Intent hint: ${context.intentHint?.name ?: "UNKNOWN"}")
            context.extractedDates.takeIf { it.isNotEmpty() }?.let {
                appendLine("Fechas extraídas: $it")
            }
            context.extractedNumbers.takeIf { it.isNotEmpty() }?.let {
                appendLine("Números extraídos: $it")
            }
            context.extractedLocations.takeIf { it.isNotEmpty() }?.let {
                appendLine("Ubicaciones extraídas: $it")
            }
            if (context.memoryContext.isNotBlank()) {
                appendLine("Memoria relevante:")
                appendLine(context.memoryContext.trim().take(400))
            }
            appendLine("Devolvé exactamente un objeto JSON TaskDSLOutput válido, sin texto extra.")
        }
    }

    private fun localClassifierSystemPrompt(): String {
        return """
            Sos el clasificador de tareas de AURA.
            Respondé con un único JSON TaskDSLOutput válido, sin markdown ni explicaciones.
            Campos raíz: title, type, priority (0..3), targetDateMs, components, reminders, fetchers.
            Cada component debe incluir: type, sortOrder único, config, populatedFromInput y needsClarification.
            En config, config_type debe ser igual al type del componente.
            Configs mínimas:
            - CHECKLIST: config_type, label, allowAddItems, items opcional.
            - COUNTDOWN: config_type, targetDate, label.
            - NOTES: config_type, text, isMarkdown.
            - PROGRESS_BAR: config_type, source, label, manualProgress opcional.
            - HABIT_RING: config_type, frequency, label, targetCount opcional.
            - METRIC_TRACKER: config_type, unit, label, history, goal opcional.
            - DATA_FEED: config_type, fetcherConfigId, displayLabel, status.
            Si falta un dato bloqueante, poné needsClarification=true.
            Si parece viaje, preferí COUNTDOWN + CHECKLIST.
            Si parece hábito, preferí HABIT_RING.
            Si parece proyecto, preferí PROGRESS_BAR + NOTES.
            Si es ambiguo, usá GENERAL + NOTES.
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
        val timestampFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }
}
