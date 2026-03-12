# AURA — LLM Local: Plan de Implementación
## LiteRT-LM + Tier System
> Referencia de implementación — Marzo 2026

---

## Contexto y decisiones clave

El ecosistema de on-device ML en Android cambió significativamente en 2025. El stack recomendado por Google a partir de mid-2025 es **LiteRT-LM**, que reemplaza a MediaPipe LLM Inference API (deprecated). Los documentos anteriores de AURA referenciaban MediaPipe — esta guía lo reemplaza completamente.

**Tres decisiones que guían todo lo que sigue:**

1. **Gemini Nano NO es la base.** Solo corre en ~20 dispositivos flagship. Para Android 10+ general es inútil como tier principal.
2. **LiteRT-LM es el stack correcto.** API Kotlin nativa, open source, soporta CPU/GPU/NPU, mantenido activamente por Google AI Edge.
3. **Gemma 3 1B es el tier principal del MVP.** Menor de 1GB, corre en gama media real, suficiente para clasificación y clarification loop.

---

## 1. Stack de dependencias

```toml
# libs.versions.toml
[versions]
litert-lm = "0.7.0"               # LiteRT-LM (incluye soporte NPU)
litert = "1.0.1"                   # LiteRT core (ex-TFLite)
mlkit-entity = "16.0.0"            # ML Kit Entity Extraction
mlkit-language = "17.0.0"          # ML Kit Language ID

[libraries]
# LiteRT-LM — runtime principal de inferencia
litert-lm = { group = "com.google.ai.edge.litert", name = "litert-lm", version.ref = "litert-lm" }

# LiteRT core — para IntentClassifier TFLite
litert-core = { group = "com.google.ai.edge.litert", name = "litert", version.ref = "litert" }

# ML Kit — entity extraction on-device
mlkit-entity-extraction = { group = "com.google.mlkit", name = "entity-extraction", version.ref = "mlkit-entity" }
mlkit-language-id = { group = "com.google.mlkit", name = "language-id", version.ref = "mlkit-language" }
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(libs.litert.lm)
    implementation(libs.litert.core)
    implementation(libs.mlkit.entity.extraction)
    implementation(libs.mlkit.language.id)
}
```

---

## 2. Modelos disponibles y cuándo usar cada uno

| Modelo | Tamaño | Tier | Uso en AURA | Mínimo hardware |
|---|---|---|---|---|
| Gemma 3 1B (int4) | ~600MB | Principal | Clasificación, clarification loop | Gama media Android 10+ |
| Gemma 3n E2B (int4) | ~1GB | Avanzado | Memory Writer, Compactor, Day Rescue | Gama media-alta, NPU preferible |
| Gemini Nano (AICore) | Sin descarga | Premium | Clasificación rápida en flagships | Pixel 8+, S24+, select flagships |
| Groq API | Sin descarga | Fallback | Todo, cuando no hay modelo local | Cualquier dispositivo con internet |
| Rules Only | Sin descarga | Mínimo | Clasificación básica sin LLM | Cualquier dispositivo |

**Fuentes de descarga de modelos (formato `.litertlm`):**
- Gemma 3 1B: `https://huggingface.co/litert-community/Gemma3-1B-IT`
- Gemma 3n E2B: `https://huggingface.co/google/gemma-3n-E2B-it-litert-lm`

---

## 3. Detección de tier — LLMTierDetector

```kotlin
// engine/llm/LLMTierDetector.kt

enum class LLMTier {
    GEMINI_NANO,      // Pixel 8+, S24+, flagships con AICore
    GEMMA_3N_E2B,     // Hardware potente, NPU disponible
    GEMMA_3_1B,       // Tier principal — gama media moderna
    GROQ_API,         // Fallback opt-in del usuario
    RULES_ONLY        // Sin LLM
}

data class TierDetectionResult(
    val primaryTier: LLMTier,
    val supportsAdvancedTier: Boolean,  // puede correr Gemma 3n E2B además
    val reasonForTier: String           // explicación para mostrar al usuario
)

class LLMTierDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences
) {

    suspend fun detect(): TierDetectionResult = withContext(Dispatchers.IO) {

        // 1. Verificar Gemini Nano via AICore
        if (isGeminiNanoAvailable()) {
            return@withContext TierDetectionResult(
                primaryTier = LLMTier.GEMINI_NANO,
                supportsAdvancedTier = hasEnoughRAM(minGb = 4f),
                reasonForTier = "Tu dispositivo tiene IA integrada al sistema operativo"
            )
        }

        val ram = getTotalRAMgb()
        val hasGPU = hasVulkanCompute()
        val hasNPU = hasNPUSupport()

        // 2. Hardware suficiente para Gemma 3 1B (tier principal)
        // Requisito mínimo: 3GB RAM total, cualquier GPU OpenGL ES 3.1+
        if (ram >= 3f) {
            val supportsAdvanced = (ram >= 4f) && (hasGPU || hasNPU)
            return@withContext TierDetectionResult(
                primaryTier = LLMTier.GEMMA_3_1B,
                supportsAdvancedTier = supportsAdvanced,
                reasonForTier = "Tu dispositivo puede procesar IA localmente"
            )
        }

        // 3. Hardware insuficiente para modelo local
        if (userPreferences.allowExternalAPI) {
            return@withContext TierDetectionResult(
                primaryTier = LLMTier.GROQ_API,
                supportsAdvancedTier = false,
                reasonForTier = "Usando Groq API (tus datos no incluyen información personal)"
            )
        }

        // 4. Sin LLM
        return@withContext TierDetectionResult(
            primaryTier = LLMTier.RULES_ONLY,
            supportsAdvancedTier = false,
            reasonForTier = "Modo básico: clasificación por reglas"
        )
    }

    private fun getTotalRAMgb(): Float {
        val memInfo = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024f * 1024f * 1024f)
    }

    private fun hasVulkanCompute(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE)
        } else false
    }

    private fun hasNPUSupport(): Boolean {
        // Heurística: Snapdragon 7xx+, Dimensity 8xx+, Exynos 2xxx
        // LiteRT-LM detecta NPU internamente al inicializar,
        // aquí solo estimamos para mostrar info al usuario
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
               getTotalRAMgb() >= 6f
    }

    private suspend fun isGeminiNanoAvailable(): Boolean {
        // Gemini Nano via ML Kit GenAI APIs (disponible desde I/O 2025)
        return try {
            val client = GenerativeModel("gemini-nano")
            client.generateContent("test")
            true
        } catch (e: Exception) {
            false
        }
    }
}
```

---

## 4. Interfaz unificada LLMService

Todo el sistema de AURA interactúa con esta interfaz. El tier es transparente para el Classifier, el Memory Writer y el Compactor.

```kotlin
// engine/llm/LLMService.kt

interface LLMService {

    val tier: LLMTier

    // Para el Classifier — genera Task DSL
    suspend fun classify(
        input: String,
        memoryContext: String = "",
        systemPrompt: String
    ): TaskDSLOutput

    // Para el Memory Writer y Compactor — texto libre
    suspend fun complete(prompt: String): String

    // Para el Clarification Loop — genera una pregunta concreta
    suspend fun generateClarification(
        taskContext: String,
        missingField: MissingField
    ): String

    // ¿Puede correr el Memory Writer? (requiere modelo avanzado)
    val supportsMemoryWriter: Boolean
        get() = tier in listOf(
            LLMTier.GEMMA_3N_E2B,
            LLMTier.GEMINI_NANO,
            LLMTier.GROQ_API
        )

    // ¿Puede correr Day Rescue completo?
    val supportsDayRescue: Boolean
        get() = tier != LLMTier.RULES_ONLY
}
```

---

## 5. Implementación por tier

### 5.1 Gemma 3 1B — Tier principal

```kotlin
// engine/llm/Gemma1BLLMService.kt

class Gemma1BLLMService @Inject constructor(
    @ApplicationContext private val context: Context
) : LLMService {

    override val tier = LLMTier.GEMMA_3_1B

    private var session: LlmInference? = null

    // Llamar en background antes de mostrar la UI de creación de tareas
    suspend fun initialize() = withContext(Dispatchers.IO) {
        val modelPath = getModelPath()

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(512)          // suficiente para Task DSL
            .setTemperature(0.1f)       // bajo = output predecible y estructurado
            .setTopK(40)
            .setRandomSeed(42)
            .build()

        session = LlmInference.createFromOptions(context, options)
    }

    override suspend fun classify(
        input: String,
        memoryContext: String,
        systemPrompt: String
    ): TaskDSLOutput = withContext(Dispatchers.Default) {
        val fullPrompt = buildClassifierPrompt(systemPrompt, memoryContext, input)
        val raw = session?.generateResponse(fullPrompt)
            ?: throw LLMNotInitializedException()
        parseTaskDSL(raw)
    }

    override suspend fun complete(prompt: String): String =
        withContext(Dispatchers.Default) {
            session?.generateResponse(prompt)
                ?: throw LLMNotInitializedException()
        }

    override suspend fun generateClarification(
        taskContext: String,
        missingField: MissingField
    ): String = withContext(Dispatchers.Default) {
        // Para Gemma 1B usamos un prompt muy corto y directo
        val prompt = """
            El usuario quiere crear una tarea: "$taskContext"
            Falta información sobre: ${missingField.fieldName}
            Escribí UNA sola pregunta corta y amigable para pedírsela.
            Solo la pregunta, sin explicaciones adicionales.
        """.trimIndent()
        session?.generateResponse(prompt)?.trim()
            ?: missingField.question  // fallback al texto hardcodeado
    }

    fun isInitialized() = session != null

    fun release() {
        session?.close()
        session = null
    }

    private fun getModelPath(): String {
        // El modelo se descarga al almacenamiento interno la primera vez
        val modelFile = File(context.filesDir, "models/gemma3-1b-int4.litertlm")
        if (!modelFile.exists()) throw ModelNotDownloadedException()
        return modelFile.absolutePath
    }

    private fun buildClassifierPrompt(
        systemPrompt: String,
        memoryContext: String,
        input: String
    ): String = buildString {
        append(systemPrompt)
        if (memoryContext.isNotBlank()) {
            append("\n\nCONTEXTO DEL USUARIO:\n$memoryContext")
        }
        append("\n\nINPUT: $input")
        append("\n\nResponder SOLO con JSON válido:")
    }
}
```

### 5.2 Gemma 3n E2B — Tier avanzado

```kotlin
// engine/llm/Gemma3nE2BLLMService.kt

class Gemma3nE2BLLMService @Inject constructor(
    @ApplicationContext private val context: Context
) : LLMService {

    override val tier = LLMTier.GEMMA_3N_E2B
    override val supportsMemoryWriter = true

    private var session: LlmInference? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val modelPath = getModelPath()

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)         // más tokens para Memory Writer
            .setTemperature(0.1f)
            .setTopK(40)
            // LiteRT-LM detecta NPU automáticamente si está disponible
            .build()

        session = LlmInference.createFromOptions(context, options)
    }

    override suspend fun classify(
        input: String,
        memoryContext: String,
        systemPrompt: String
    ): TaskDSLOutput = withContext(Dispatchers.Default) {
        val prompt = buildClassifierPrompt(systemPrompt, memoryContext, input)
        val raw = session?.generateResponse(prompt)
            ?: throw LLMNotInitializedException()
        parseTaskDSL(raw)
    }

    override suspend fun complete(prompt: String): String =
        withContext(Dispatchers.Default) {
            session?.generateResponse(prompt)
                ?: throw LLMNotInitializedException()
        }

    override suspend fun generateClarification(
        taskContext: String,
        missingField: MissingField
    ): String = withContext(Dispatchers.Default) {
        val prompt = """
            Contexto de tarea: "$taskContext"
            Campo faltante: ${missingField.fieldName}
            Generá una pregunta concisa y amigable en español.
            Solo la pregunta, sin texto adicional.
        """.trimIndent()
        session?.generateResponse(prompt)?.trim() ?: missingField.question
    }

    fun release() {
        session?.close()
        session = null
    }

    private fun getModelPath(): String {
        val modelFile = File(context.filesDir, "models/gemma3n-e2b-int4.litertlm")
        if (!modelFile.exists()) throw ModelNotDownloadedException()
        return modelFile.absolutePath
    }
}
```

### 5.3 Groq API — Fallback opt-in

```kotlin
// engine/llm/GroqLLMService.kt

class GroqLLMService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val userPreferences: UserPreferences
) : LLMService {

    override val tier = LLMTier.GROQ_API
    override val supportsMemoryWriter = true

    // Modelo en Groq: Gemma2 9B — más capaz que los modelos locales
    private val model = "gemma2-9b-it"
    private val baseUrl = "https://api.groq.com/openai/v1/chat/completions"

    override suspend fun classify(
        input: String,
        memoryContext: String,
        systemPrompt: String
    ): TaskDSLOutput = withContext(Dispatchers.IO) {
        val messages = buildList {
            add(Message("system", systemPrompt))
            if (memoryContext.isNotBlank()) {
                add(Message("system", "CONTEXTO DEL USUARIO:\n$memoryContext"))
            }
            add(Message("user", input))
        }
        val raw = callGroq(messages, maxTokens = 512)
        parseTaskDSL(raw)
    }

    override suspend fun complete(prompt: String): String =
        withContext(Dispatchers.IO) {
            callGroq(
                messages = listOf(Message("user", prompt)),
                maxTokens = 512
            )
        }

    override suspend fun generateClarification(
        taskContext: String,
        missingField: MissingField
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            El usuario quiere crear: "$taskContext"
            Necesito pedirle: ${missingField.fieldName}
            Escribí una sola pregunta corta en español.
        """.trimIndent()
        callGroq(listOf(Message("user", prompt)), maxTokens = 100).trim()
    }

    private suspend fun callGroq(
        messages: List<Message>,
        maxTokens: Int
    ): String {
        val apiKey = userPreferences.groqApiKey
            ?: throw GroqAPIKeyMissingException()

        val requestBody = GroqRequest(
            model = model,
            messages = messages,
            maxTokens = maxTokens,
            temperature = 0.1f,
            responseFormat = ResponseFormat(type = "json_object")
        ).toJson()

        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) throw GroqAPIException(response.code)

        return parseGroqResponse(response.body?.string() ?: "")
    }
}
```

### 5.4 Rules Only — Sin LLM

```kotlin
// engine/llm/RulesOnlyLLMService.kt

class RulesOnlyLLMService @Inject constructor(
    private val intentClassifier: IntentClassifier,
    private val entityExtractor: EntityExtractorService
) : LLMService {

    override val tier = LLMTier.RULES_ONLY
    override val supportsMemoryWriter = false
    override val supportsDayRescue = false

    override suspend fun classify(
        input: String,
        memoryContext: String,
        systemPrompt: String
    ): TaskDSLOutput {
        val entities = entityExtractor.extract(input)
        val intent = intentClassifier.classify(input)
        return TaskDSLBuilder()
            .withType(intent.taskType)
            .withEntities(entities)
            .buildBasic()  // sin content population
    }

    override suspend fun complete(prompt: String): String =
        throw UnsupportedOperationException("Rules Only tier no soporta complete()")

    override suspend fun generateClarification(
        taskContext: String,
        missingField: MissingField
    ): String = missingField.question  // pregunta hardcodeada del schema
}
```

---

## 6. Factory — LLMServiceFactory

```kotlin
// engine/llm/LLMServiceFactory.kt

class LLMServiceFactory @Inject constructor(
    private val detector: LLMTierDetector,
    private val userPreferences: UserPreferences,
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val intentClassifier: IntentClassifier,
    private val entityExtractor: EntityExtractorService
) {

    private var cachedService: LLMService? = null
    private var cachedAdvancedService: LLMService? = null

    // LLM principal — usado por el Classifier y el Clarification Loop
    suspend fun getPrimaryService(): LLMService {
        cachedService?.let { return it }

        val detection = detector.detect()

        val service: LLMService = when (detection.primaryTier) {
            LLMTier.GEMINI_NANO -> GeminiNanoLLMService(context)
            LLMTier.GEMMA_3_1B  -> Gemma1BLLMService(context).also {
                if (!it.isInitialized()) it.initialize()
            }
            LLMTier.GROQ_API    -> GroqLLMService(httpClient, userPreferences)
            LLMTier.RULES_ONLY  -> RulesOnlyLLMService(intentClassifier, entityExtractor)
            else                -> RulesOnlyLLMService(intentClassifier, entityExtractor)
        }

        return service.also { cachedService = it }
    }

    // LLM avanzado — usado por Memory Writer, Compactor y Day Rescue
    // Puede ser el mismo que el principal o uno más capaz
    suspend fun getAdvancedService(): LLMService {
        cachedAdvancedService?.let { return it }

        val detection = detector.detect()

        val service: LLMService = when {
            // Si el tier principal ya soporta Memory Writer, usarlo
            detection.primaryTier == LLMTier.GEMINI_NANO -> {
                GeminiNanoLLMService(context)
            }
            // Si el hardware soporta Gemma 3n E2B, usarlo para tareas avanzadas
            detection.supportsAdvancedTier && isGemma3nModelDownloaded() -> {
                Gemma3nE2BLLMService(context).also { it.initialize() }
            }
            // Si hay API key de Groq, usarla para tareas avanzadas
            userPreferences.allowExternalAPI && userPreferences.groqApiKey != null -> {
                GroqLLMService(httpClient, userPreferences)
            }
            // Fallback: usar el mismo que el tier principal
            else -> getPrimaryService()
        }

        return service.also { cachedAdvancedService = it }
    }

    private fun isGemma3nModelDownloaded(): Boolean {
        return File(context.filesDir, "models/gemma3n-e2b-int4.litertlm").exists()
    }
}
```

---

## 7. Descarga de modelos — ModelDownloadManager

La descarga es el punto más crítico de UX. Solo por WiFi por defecto, con progreso visible, cancelable.

```kotlin
// engine/llm/ModelDownloadManager.kt

data class ModelSpec(
    val id: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val tier: LLMTier,
    val isRequired: Boolean  // false = opcional (Gemma 3n E2B)
)

val GEMMA_3_1B = ModelSpec(
    id = "gemma3-1b",
    fileName = "gemma3-1b-int4.litertlm",
    downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm",
    sizeBytes = 620_000_000L,   // ~600MB
    tier = LLMTier.GEMMA_3_1B,
    isRequired = true
)

val GEMMA_3N_E2B = ModelSpec(
    id = "gemma3n-e2b",
    fileName = "gemma3n-e2b-int4.litertlm",
    downloadUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma3n-e2b-it-int4.litertlm",
    sizeBytes = 1_100_000_000L, // ~1GB
    tier = LLMTier.GEMMA_3N_E2B,
    isRequired = false
)

sealed class DownloadState {
    object Idle : DownloadState()
    object WaitingForWifi : DownloadState()
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    object Processing : DownloadState()  // verificando checksum, moviendo archivo
    object Complete : DownloadState()
    data class Error(val reason: String, val canRetry: Boolean) : DownloadState()
}

class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val userPreferences: UserPreferences
) {

    fun downloadModel(
        spec: ModelSpec,
        wifiOnly: Boolean = true
    ): Flow<DownloadState> = flow {

        val targetFile = File(context.filesDir, "models/${spec.fileName}")
        if (targetFile.exists()) {
            emit(DownloadState.Complete)
            return@flow
        }

        // Verificar conectividad
        if (wifiOnly && !isOnWifi()) {
            emit(DownloadState.WaitingForWifi)
            return@flow
        }

        // Crear directorio si no existe
        targetFile.parentFile?.mkdirs()
        val tempFile = File(context.filesDir, "models/${spec.fileName}.tmp")

        emit(DownloadState.Downloading(0f, 0L, spec.sizeBytes))

        try {
            val request = Request.Builder().url(spec.downloadUrl).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(DownloadState.Error("HTTP ${response.code}", canRetry = true))
                return@flow
            }

            val body = response.body ?: run {
                emit(DownloadState.Error("Respuesta vacía", canRetry = true))
                return@flow
            }

            var bytesRead = 0L
            val buffer = ByteArray(8 * 1024)

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        emit(DownloadState.Downloading(
                            progress = bytesRead.toFloat() / spec.sizeBytes,
                            bytesDownloaded = bytesRead,
                            totalBytes = spec.sizeBytes
                        ))
                    }
                }
            }

            emit(DownloadState.Processing)
            tempFile.renameTo(targetFile)
            emit(DownloadState.Complete)

        } catch (e: IOException) {
            tempFile.delete()
            emit(DownloadState.Error(e.message ?: "Error de red", canRetry = true))
        }
    }.flowOn(Dispatchers.IO)

    fun isModelDownloaded(spec: ModelSpec): Boolean {
        return File(context.filesDir, "models/${spec.fileName}").exists()
    }

    fun deleteModel(spec: ModelSpec) {
        File(context.filesDir, "models/${spec.fileName}").delete()
    }

    fun getModelSizeOnDisk(spec: ModelSpec): Long {
        return File(context.filesDir, "models/${spec.fileName}").length()
    }

    private fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
```

---

## 8. UI de configuración y descarga

### 8.1 Onboarding — selección de LLM

```kotlin
// ui/onboarding/LLMSetupScreen.kt

@Composable
fun LLMSetupScreen(
    detectionResult: TierDetectionResult,
    downloadState: DownloadState,
    onDownloadModel: (wifiOnly: Boolean) -> Unit,
    onSkipDownload: () -> Unit,
    onEnableGroq: () -> Unit,
    onContinueWithBasic: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {

        when {
            // Tier 1: Gemini Nano disponible
            detectionResult.primaryTier == LLMTier.GEMINI_NANO -> {
                LLMSetupSuccess(
                    title = "IA integrada disponible",
                    description = "Tu dispositivo tiene IA integrada al sistema operativo. " +
                                  "Todo se procesa localmente.",
                    icon = "✓"
                )
            }

            // Tier 2/3: Modelo descargable disponible
            detectionResult.primaryTier == LLMTier.GEMMA_3_1B -> {
                when (downloadState) {
                    is DownloadState.Idle -> {
                        LLMDownloadPrompt(
                            title = "Descargar modelo de IA",
                            description = "Para procesar todo localmente necesito descargar " +
                                          "un modelo (~600MB). Tus datos nunca van a salir " +
                                          "del teléfono.",
                            onDownloadWifi = { onDownloadModel(true) },
                            onDownloadNow = { onDownloadModel(false) },
                            onSkip = onSkipDownload
                        )
                    }
                    is DownloadState.WaitingForWifi -> {
                        LLMWifiWaiting(
                            onDownloadAnyway = { onDownloadModel(false) },
                            onSkip = onSkipDownload
                        )
                    }
                    is DownloadState.Downloading -> {
                        LLMDownloadProgress(
                            progress = downloadState.progress,
                            downloaded = downloadState.bytesDownloaded,
                            total = downloadState.totalBytes
                        )
                    }
                    is DownloadState.Complete -> {
                        LLMSetupSuccess(
                            title = "Modelo listo",
                            description = "Todo listo. La IA corre completamente en tu dispositivo.",
                            icon = "✓"
                        )
                    }
                    is DownloadState.Error -> {
                        LLMDownloadError(
                            reason = downloadState.reason,
                            canRetry = downloadState.canRetry,
                            onRetry = { onDownloadModel(true) },
                            onUseGroq = onEnableGroq,
                            onBasic = onContinueWithBasic
                        )
                    }
                    else -> {}
                }
            }

            // Tier 4/5: Sin modelo local posible
            else -> {
                LLMFallbackOptions(
                    onEnableGroq = onEnableGroq,
                    onContinueWithBasic = onContinueWithBasic
                )
            }
        }
    }
}
```

### 8.2 Settings — gestión de modelos

```kotlin
// ui/settings/LLMSettingsSection.kt

@Composable
fun LLMSettingsSection(
    currentTier: LLMTier,
    isGemma1BDownloaded: Boolean,
    isGemma3nDownloaded: Boolean,
    gemma1BsizeOnDisk: Long,
    gemma3nSizeOnDisk: Long,
    groqApiKey: String?,
    onDownloadGemma1B: () -> Unit,
    onDeleteGemma1B: () -> Unit,
    onDownloadGemma3n: () -> Unit,
    onDeleteGemma3n: () -> Unit,
    onSaveGroqKey: (String) -> Unit
) {
    Column {
        SettingsSectionHeader("Inteligencia Artificial")

        // Estado actual
        CurrentTierCard(tier = currentTier)

        // Gemma 3 1B — modelo principal
        ModelManagementRow(
            name = "Gemma 3 1B",
            description = "Clasificación y creación de tareas",
            isDownloaded = isGemma1BDownloaded,
            sizeBytes = if (isGemma1BDownloaded) gemma1BsizeOnDisk else 620_000_000L,
            isActive = currentTier == LLMTier.GEMMA_3_1B,
            onDownload = onDownloadGemma1B,
            onDelete = onDeleteGemma1B
        )

        // Gemma 3n E2B — modelo avanzado (opcional)
        ModelManagementRow(
            name = "Gemma 3n E2B",
            description = "Memoria contextual y Day Rescue (avanzado)",
            isDownloaded = isGemma3nDownloaded,
            sizeBytes = if (isGemma3nDownloaded) gemma3nSizeOnDisk else 1_100_000_000L,
            isActive = currentTier == LLMTier.GEMMA_3N_E2B,
            badge = "Opcional",
            onDownload = onDownloadGemma3n,
            onDelete = onDeleteGemma3n
        )

        // Groq API
        GroqApiKeyRow(
            currentKey = groqApiKey,
            onSave = onSaveGroqKey
        )
    }
}
```

---

## 9. Hilt — módulo de inyección

```kotlin
// di/LLMModule.kt

@Module
@InstallIn(SingletonComponent::class)
object LLMModule {

    @Provides
    @Singleton
    fun provideLLMTierDetector(
        @ApplicationContext context: Context,
        userPreferences: UserPreferences
    ): LLMTierDetector = LLMTierDetector(context, userPreferences)

    @Provides
    @Singleton
    fun provideModelDownloadManager(
        @ApplicationContext context: Context,
        httpClient: OkHttpClient,
        userPreferences: UserPreferences
    ): ModelDownloadManager = ModelDownloadManager(context, httpClient, userPreferences)

    @Provides
    @Singleton
    fun provideLLMServiceFactory(
        detector: LLMTierDetector,
        userPreferences: UserPreferences,
        @ApplicationContext context: Context,
        httpClient: OkHttpClient,
        intentClassifier: IntentClassifier,
        entityExtractor: EntityExtractorService
    ): LLMServiceFactory = LLMServiceFactory(
        detector, userPreferences, context, httpClient, intentClassifier, entityExtractor
    )
}
```

---

## 10. Errores y excepciones

```kotlin
// engine/llm/LLMExceptions.kt

class LLMNotInitializedException :
    Exception("El modelo LLM no fue inicializado. Llamar initialize() primero.")

class ModelNotDownloadedException :
    Exception("El modelo no fue descargado. Iniciar descarga desde Settings.")

class GroqAPIKeyMissingException :
    Exception("API key de Groq no configurada.")

class GroqAPIException(val code: Int) :
    Exception("Error de Groq API: HTTP $code")

class TaskDSLParseException(val rawResponse: String) :
    Exception("No se pudo parsear el Task DSL desde: $rawResponse")
```

---

## 11. Orden de implementación

```
Paso 1 — Dependencias y estructura de paquetes
    - Agregar litert-lm y litert a libs.versions.toml
    - Crear paquete engine/llm/
    - Crear todas las interfaces y excepciones

Paso 2 — LLMTierDetector
    - Implementar detección de RAM, GPU y AICore
    - Unit tests con distintos escenarios de hardware

Paso 3 — RulesOnlyLLMService
    - Implementar primero el tier más simple
    - Permite desarrollar el resto del sistema sin modelo descargado

Paso 4 — ModelDownloadManager
    - Flow de descarga con progreso
    - Tests con servidor mock

Paso 5 — Gemma1BLLMService
    - Integración con LiteRT-LM
    - Requiere dispositivo físico para testear (no corre en emulador)
    - Verificar que el JSON output sea parseable

Paso 6 — LLMSetupScreen (onboarding)
    - UI de descarga con todos los estados
    - Probar en dispositivo real con WiFi on/off

Paso 7 — GroqLLMService
    - Integración HTTP
    - Verificar sanitización de PII en interceptor
    - Test de parsing de response JSON

Paso 8 — Gemma3nE2BLLMService (opcional para MVP)
    - Solo si el hardware del dispositivo de desarrollo lo soporta
    - Puede dejarse para después del MVP si no hay dispositivo compatible

Paso 9 — LLMServiceFactory + Hilt module
    - Conectar todo
    - Smoke test del flujo completo: input → classify → TaskDSLOutput

Paso 10 — LLMSettingsSection
    - UI de gestión de modelos en Settings
    - Delete model + re-download flow
```

---

## 12. Consideraciones importantes

**El modelo nunca va en el APK.** Se descarga al `filesDir` del usuario la primera vez. Esto mantiene el APK liviano y le da control al usuario sobre cuándo descargar.

**No corre en emulador.** LiteRT-LM requiere GPU real. Todo el desarrollo de integración del modelo necesita dispositivo físico. El `RulesOnlyLLMService` es el reemplazo para desarrollo en emulador.

**Primera inicialización es lenta.** LiteRT-LM optimiza los pesos del modelo para el hardware específico del dispositivo en el primer `createFromOptions()`. Puede tardar 5-15 segundos. Las inicializaciones posteriores son rápidas porque cachea el resultado. Mostrar un loading state la primera vez.

**El modelo ocupa RAM mientras está activo.** Gemma 3 1B ocupa ~700MB de RAM en uso. Llamar `release()` cuando la app va a background por más de 5 minutos. Re-inicializar cuando vuelve a foreground. Gestionar esto desde el `ViewModel` con `Lifecycle.Event`.

**JSON output no está garantizado.** A diferencia de Groq API que tiene `response_format: json_object`, los modelos locales pueden fallar en generar JSON válido. Siempre envolver el parse en try/catch con fallback a `RulesOnlyLLMService.classify()` si el parse falla 2 veces seguidas.

---

*Reemplaza la sección "Configuración del LLM" de los documentos anteriores.*
*Compatible con: MVP Reference Document v1.0, Plan de Implementación v1.0, Intelligence Layer v1.0.*