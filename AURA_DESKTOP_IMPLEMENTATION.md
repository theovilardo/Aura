# Aura Desktop - Documento de Implementacion

## 1. Vision General

Aura Desktop es el companion de escritorio para Aura Android. Su rol principal es **recibir tareas creadas en el telefono y ejecutar acciones en el escritorio** segun las configuraciones del usuario. No es una replica del app movil; es un agente de ejecucion controlado remotamente.

### Rol de cada plataforma

| Plataforma | Responsabilidad |
|------------|----------------|
| **Android** | Creacion de tareas, clasificacion NLU, UI principal, seleccion de entorno/proveedor |
| **Desktop** | Ejecucion de acciones del sistema, hosting de modelos Ollama, servidor WebSocket, sincronizacion de datos |

### Principios de diseno

1. **El telefono manda, el desktop ejecuta** - Android es el orquestador; Desktop es un worker.
2. **Seguridad por defecto** - Toda conexion requiere pairing previo con codigo de 6 digitos y secreto compartido.
3. **Offline-first** - Desktop opera aunque Android se desconecte; encola respuestas y sincroniza al reconectar.
4. **Minimo footprint** - Sin UI pesada; vive en la system tray con panel de status minimalista.

---

## 2. Stack Tecnologico

### Tecnologia recomendada

| Componente | Tecnologia | Justificacion |
|------------|-----------|---------------|
| **Lenguaje** | Kotlin | Reutiliza el modulo `:protocol` directamente (JVM puro) |
| **UI Framework** | Compose Multiplatform (Desktop) | Comparte paradigma con Android; ideal para panel de control |
| **WebSocket Server** | Ktor (Server) con WebSocket plugin | Kotlin-nativo, coroutines, ligero |
| **Serialization** | kotlinx.serialization | Ya definida en `:protocol`; compatibilidad directa |
| **DI** | Koin | Ligero para desktop (Hilt es Android-only) |
| **Persistencia local** | SQLDelight o un simple JSON file store | Para configuracion, dispositivos pareados, cola de sincronizacion |
| **System tray** | java.awt.SystemTray + Compose window | Integrado en JVM; sin dependencias extra |
| **Build** | Gradle con Compose Multiplatform plugin | Un solo build system, comparte `:protocol` |
| **Packaging** | conveyor o jpackage | Genera .dmg (macOS), .msi (Windows), .deb/.AppImage (Linux) |

### Estructura de modulos Gradle

```
Aura/
├── app/                    # Android (existente)
├── protocol/               # Compartido JVM (existente)
└── desktop/                # NUEVO - Aura Desktop
    ├── build.gradle.kts
    └── src/main/kotlin/com/theveloper/aura/desktop/
```

El modulo `desktop` depende de `protocol` directamente:

```kotlin
// desktop/build.gradle.kts
plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":protocol"))
    implementation("io.ktor:ktor-server-core:2.3.x")
    implementation("io.ktor:ktor-server-websockets:2.3.x")
    implementation("io.ktor:ktor-server-netty:2.3.x")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.insert-koin:koin-core:3.5.x")
    implementation(compose.desktop.currentOs)
}
```

---

## 3. Arquitectura de la Aplicacion Desktop

### Diagrama de capas

```
┌─────────────────────────────────────────────────┐
│                   System Tray                    │
│            (icono + menu contextual)             │
├─────────────────────────────────────────────────┤
│              Panel de Control (UI)               │
│  Compose Desktop: status, dispositivos, logs     │
├─────────────────────────────────────────────────┤
│              WebSocket Server (Ktor)             │
│  Puerto configurable (default: 8765)             │
│  Endpoint: ws://IP:8765/ws/{deviceId}            │
├─────────────────────────────────────────────────┤
│              Message Router                       │
│  Deserializa EcosystemMessage → despacha         │
├──────────────┬──────────────────────────────────┤
│ Action       │  Lifecycle                        │
│ Executor     │  Manager                          │
│ (14 actions) │  (heartbeat, pairing, capability) │
├──────────────┴──────────────────────────────────┤
│              Security Layer                       │
│  Pairing store, shared secret, validacion        │
├─────────────────────────────────────────────────┤
│              Platform Services                    │
│  File I/O, Shell, Apps, Clipboard, Notifications │
│  Ollama Client (HTTP → localhost:11434)          │
├─────────────────────────────────────────────────┤
│              Config Store                         │
│  JSON file: ~/. aura/desktop-config.json         │
└─────────────────────────────────────────────────┘
```

### Paquetes principales

```
com.theveloper.aura.desktop/
├── AuraDesktopApp.kt           # Entry point, system tray, Compose window
├── server/
│   ├── WebSocketServer.kt      # Ktor server, acepta conexiones WS
│   ├── MessageRouter.kt        # Deserializa y despacha mensajes
│   └── SessionManager.kt       # Sesiones activas por deviceId
├── executor/
│   ├── ActionExecutor.kt       # Interface base
│   ├── FileExecutor.kt         # FILE_READ, FILE_WRITE, FILE_SEND, FILE_LIST, FILE_DELETE
│   ├── ShellExecutor.kt        # COMMAND_EXEC
│   ├── AppExecutor.kt          # APP_OPEN
│   ├── ClipboardExecutor.kt    # CLIPBOARD_GET, CLIPBOARD_SET
│   ├── NotificationExecutor.kt # NOTIFICATION (sistema nativo)
│   ├── StatusExecutor.kt       # STATUS_REPORT, RECENT_FILES
│   └── OllamaExecutor.kt       # OLLAMA_COMPLETE, OLLAMA_LIST
├── lifecycle/
│   ├── HeartbeatEmitter.kt     # Emite heartbeats periodicos al Android
│   ├── PairingHandler.kt       # Maneja flujo de pairing
│   └── CapabilityReporter.kt   # Genera y envia DeviceCapabilityReport
├── security/
│   ├── PairingStore.kt         # Dispositivos pareados (persistidos)
│   ├── SharedSecretCrypto.kt   # Cifrado E2E con secreto compartido
│   └── PermissionGuard.kt      # Valida permisos antes de ejecutar acciones
├── platform/
│   ├── PlatformDetector.kt     # Detecta macOS/Windows/Linux
│   ├── OllamaClient.kt        # HTTP client a Ollama (localhost:11434)
│   └── NativeNotification.kt  # Notificaciones nativas del SO
├── config/
│   ├── DesktopConfig.kt        # Modelo de configuracion
│   └── ConfigStore.kt          # Lee/escribe ~/.aura/desktop-config.json
├── ui/
│   ├── ControlPanel.kt         # Ventana Compose principal
│   ├── StatusView.kt           # Estado de conexion, dispositivos
│   ├── LogView.kt              # Log de acciones ejecutadas
│   └── SettingsView.kt         # Configuracion del usuario
└── di/
    └── DesktopModule.kt        # Koin module definitions
```

---

## 4. Protocolo de Comunicacion

### Ya definido en el modulo `:protocol`

El protocolo ya esta implementado y listo para reusar. Estos son los tipos de mensaje que Desktop debe manejar:

### Mensajes que Desktop RECIBE (desde Android)

| Tipo | Payload | Accion Desktop |
|------|---------|----------------|
| `ACTION_REQUEST` | `ActionRequest(action, params, requiresConfirmation)` | Ejecutar accion del sistema y responder |
| `PAIRING_REQUEST` | `PairingRequest(deviceId, deviceName, platform, publicKey)` | Mostrar codigo de pairing de 6 digitos |

### Mensajes que Desktop ENVIA (hacia Android)

| Tipo | Payload | Cuando |
|------|---------|--------|
| `ACTION_RESPONSE` | `ActionResponse(requestId, success, data, error, metadata)` | Tras ejecutar cada ActionRequest |
| `HEARTBEAT` | `DeviceHeartbeat(deviceId, timestamp, cpu, memory, thermal, ollamaRunning)` | Cada 10 segundos |
| `CAPABILITY_REPORT` | `DeviceCapabilityReport(deviceId, name, platform, supportedActions, ollamaModels)` | Al conectarse un dispositivo |
| `PAIRING_ACK` | `PairingAck(deviceId, name, platform, publicKey, accepted, pairingCode)` | Respuesta al PairingRequest |
| `EVENT` | `DeviceEvent(event, data)` | Eventos espontaneos (ej: archivo recibido, Ollama termino de cargar) |

### Formato de mensaje (JSON sobre WebSocket)

```json
{
  "id": "uuid-correlacion",
  "type": "ACTION_REQUEST",
  "payload": {
    "payloadType": "action_request",
    "action": "FILE_READ",
    "params": { "path": "/Users/theo/Documents/notas.txt" },
    "requiresConfirmation": false
  }
}
```

La serializacion ya esta resuelta con `ProtocolSerializer` en el modulo `:protocol` usando `kotlinx.serialization` polimorfico.

---

## 5. Las 14 Acciones Desktop (ActionExecutor)

Cada `DesktopAction` del enum tiene un executor correspondiente. Aqui se detalla exactamente que debe hacer cada uno:

### 5.1 Operaciones de archivos

| Accion | Params (JsonObject) | Respuesta (data) | Notas |
|--------|---------------------|-------------------|-------|
| `FILE_READ` | `{"path": "..."}` | `{"content": "base64-o-texto", "size": N, "mimeType": "..."}` | Limitar tamano maximo configurable (default 10MB) |
| `FILE_WRITE` | `{"path": "...", "content": "...", "encoding": "utf8\|base64"}` | `{"bytesWritten": N}` | Crear directorios intermedios si no existen |
| `FILE_SEND` | `{"path": "...", "destinationDeviceId": "..."}` | `{"sent": true}` | Transferencia entre dispositivos (futuro) |
| `FILE_LIST` | `{"path": "...", "recursive": false, "glob": "*.txt"}` | `{"files": [{"name": "...", "size": N, "isDir": bool, "modified": long}]}` | Depth limit para recursive |
| `FILE_DELETE` | `{"path": "..."}` | `{"deleted": true}` | Mover a trash del SO, no borrar permanentemente |

### 5.2 Ejecucion de comandos

| Accion | Params | Respuesta | Notas |
|--------|--------|-----------|-------|
| `COMMAND_EXEC` | `{"command": "...", "workingDir": "~", "timeout": 30000}` | `{"stdout": "...", "stderr": "...", "exitCode": N}` | **CRITICO**: Whitelist de comandos permitidos + sandbox. Timeout default 30s. |

### 5.3 Aplicaciones

| Accion | Params | Respuesta | Notas |
|--------|--------|-----------|-------|
| `APP_OPEN` | `{"appName": "Safari", "args": ["https://..."]}` | `{"launched": true, "pid": N}` | macOS: `open -a`, Windows: `start`, Linux: `xdg-open` |

### 5.4 Clipboard

| Accion | Params | Respuesta | Notas |
|--------|--------|-----------|-------|
| `CLIPBOARD_GET` | `{}` | `{"content": "...", "type": "text\|image"}` | Usar java.awt.Toolkit |
| `CLIPBOARD_SET` | `{"content": "...", "type": "text"}` | `{"set": true}` | Solo texto por ahora |

### 5.5 Notificaciones

| Accion | Params | Respuesta | Notas |
|--------|--------|-----------|-------|
| `NOTIFICATION` | `{"title": "...", "body": "...", "sound": true}` | `{"shown": true}` | macOS: osascript, Windows: toast, Linux: notify-send |

### 5.6 Status

| Accion | Params | Respuesta | Notas |
|--------|--------|-----------|-------|
| `STATUS_REPORT` | `{}` | `{"cpu": F, "memory": F, "disk": F, "uptime": L, "ollamaRunning": bool}` | Usa `oshi` library o Runtime.exec |
| `RECENT_FILES` | `{"limit": 10}` | `{"files": [...]}` | macOS: plist recent items, Windows: recent folder |

### 5.7 Ollama (LLM local)

| Accion | Params | Respuesta | Notas |
|--------|--------|-----------|-------|
| `OLLAMA_COMPLETE` | `{"model": "llama3", "prompt": "...", "system": "...", "temperature": 0.7}` | `{"response": "...", "model": "...", "durationMs": N}` | HTTP POST a `http://localhost:11434/api/generate` |
| `OLLAMA_LIST` | `{}` | `{"models": [{"name": "...", "size": N, "parameterCount": "...", "quantization": "..."}]}` | HTTP GET `http://localhost:11434/api/tags` |

---

## 6. Flujo de Conexion (Pairing)

### Secuencia completa

```
  ANDROID                                          DESKTOP
     │                                                │
     │  1. Usuario ingresa IP:puerto del desktop      │
     │                                                │
     │──── WebSocket connect ─────────────────────────│
     │     ws://192.168.1.50:8765/ws/{androidDeviceId}│
     │                                                │
     │──── PAIRING_REQUEST ──────────────────────────▶│
     │     {deviceId, deviceName, platform, publicKey} │
     │                                                │
     │                    Desktop genera codigo 6 dig  │
     │                    y lo muestra en pantalla     │
     │                                                │
     │◀─── PAIRING_ACK ──────────────────────────────│
     │     {deviceId, deviceName, platform, publicKey, │
     │      accepted: true, pairingCode: "483921"}    │
     │                                                │
     │  2. Usuario ingresa codigo en Android           │
     │                                                │
     │  3. Ambos derivan shared secret del par         │
     │     de llaves publicas + codigo                 │
     │                                                │
     │◀─── CAPABILITY_REPORT ─────────────────────────│
     │     {supportedActions: [...],                   │
     │      ollamaModels: [...],                       │
     │      protocolVersion: 1}                        │
     │                                                │
     │◀─── HEARTBEAT (cada 10s) ──────────────────────│
     │     {cpu, memory, thermal, ollamaRunning}       │
     │                                                │
     │  CONEXION ESTABLECIDA ✓                         │
```

### Persistencia del pairing

Desktop almacena en `~/.aura/paired-devices.json`:

```json
[
  {
    "deviceId": "android-uuid",
    "deviceName": "Pixel 8 de Theo",
    "platform": "ANDROID",
    "publicKey": "base64...",
    "sharedSecret": "base64...(cifrado)",
    "pairedAt": 1742342400000,
    "lastSeenAt": 1742345600000
  }
]
```

Android persiste lo mismo en la tabla `paired_devices` de Room (ya implementado).

---

## 7. Flujo de Ejecucion de una Tarea

### Escenario: usuario crea tarea en Android que requiere accion desktop

```
  ANDROID                                          DESKTOP
     │                                                │
     │  1. Usuario: "Abre el archivo notas.txt"       │
     │                                                │
     │  2. TaskClassifier → tipo GENERAL               │
     │     ExecutionRouter → target REMOTE_DESKTOP     │
     │                                                │
     │──── ACTION_REQUEST ───────────────────────────▶│
     │     id: "req-001"                              │
     │     action: FILE_READ                           │
     │     params: {path: "~/Documents/notas.txt"}     │
     │                                                │
     │              3. MessageRouter recibe             │
     │                 → FileExecutor.read()           │
     │                 → Lee archivo del disco          │
     │                                                │
     │◀─── ACTION_RESPONSE ──────────────────────────│
     │     requestId: "req-001"                        │
     │     success: true                               │
     │     data: {content: "...", size: 1234}          │
     │     metadata: {executedAt, deviceId, durationMs}│
     │                                                │
     │  4. Android muestra resultado al usuario        │
```

### Escenario: tarea con Ollama

```
  ANDROID                                          DESKTOP
     │                                                │
     │  1. Usuario: "Resume este PDF"                  │
     │     ExecutionRouter → provider: ollama-llama3   │
     │     (DesktopOllamaProvider)                     │
     │                                                │
     │──── ACTION_REQUEST ───────────────────────────▶│
     │     action: OLLAMA_COMPLETE                     │
     │     params: {model: "llama3",                   │
     │              prompt: "Resume: ...",              │
     │              system: "...(system_prompt.txt)"}   │
     │                                                │
     │              2. OllamaExecutor                   │
     │                 → HTTP POST localhost:11434      │
     │                 → Espera respuesta del modelo    │
     │                                                │
     │◀─── ACTION_RESPONSE ──────────────────────────│
     │     data: {response: "El PDF trata sobre...",   │
     │            model: "llama3", durationMs: 4200}   │
     │                                                │
     │  3. Android procesa respuesta como              │
     │     TaskDSLOutput y crea la tarea               │
```

---

## 8. Servidor WebSocket (Implementacion)

### WebSocketServer.kt - Esqueleto

```kotlin
class WebSocketServer(
    private val port: Int = 8765,
    private val messageRouter: MessageRouter,
    private val sessionManager: SessionManager,
    private val pairingStore: PairingStore
) {
    private var server: ApplicationEngine? = null

    fun start() {
        server = embeddedServer(Netty, port = port) {
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(15)
                timeout = Duration.ofSeconds(30)
                maxFrameSize = Long.MAX_VALUE
            }

            routing {
                webSocket("/ws/{deviceId}") {
                    val deviceId = call.parameters["deviceId"]
                        ?: return@webSocket close(
                            CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing deviceId")
                        )

                    // Validar que el dispositivo esta pareado
                    if (!pairingStore.isPaired(deviceId)) {
                        // Permitir solo PAIRING_REQUEST si no esta pareado
                        sessionManager.registerPendingSession(deviceId, this)
                    } else {
                        sessionManager.registerSession(deviceId, this)
                    }

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                val message = ProtocolSerializer.decode(text)
                                val response = messageRouter.route(deviceId, message)
                                if (response != null) {
                                    send(Frame.Text(ProtocolSerializer.encode(response)))
                                }
                            }
                        }
                    } finally {
                        sessionManager.unregisterSession(deviceId)
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
    }
}
```

### MessageRouter.kt - Despacho de mensajes

```kotlin
class MessageRouter(
    private val actionExecutor: ActionExecutorRegistry,
    private val pairingHandler: PairingHandler,
    private val permissionGuard: PermissionGuard
) {
    suspend fun route(deviceId: String, message: EcosystemMessage): EcosystemMessage? {
        return when (message.type) {
            MessageType.ACTION_REQUEST -> {
                val request = message.payload as ActionRequest

                // Validar permisos
                if (!permissionGuard.isAllowed(deviceId, request.action)) {
                    return buildErrorResponse(message.id, "Action not permitted")
                }

                // Si requiere confirmacion, mostrar dialogo en desktop
                if (request.requiresConfirmation) {
                    // Mostrar en UI y esperar aprobacion del usuario
                }

                val result = actionExecutor.execute(request)
                EcosystemMessage(
                    id = UUID.randomUUID().toString(),
                    type = MessageType.ACTION_RESPONSE,
                    payload = ActionResponse(
                        requestId = message.id,
                        success = result.success,
                        data = result.data,
                        error = result.error,
                        metadata = ActionMetadata(
                            executedAt = System.currentTimeMillis(),
                            deviceId = getLocalDeviceId(),
                            durationMs = result.durationMs
                        )
                    )
                )
            }
            MessageType.PAIRING_REQUEST -> {
                pairingHandler.handleRequest(message.payload as PairingRequest)
            }
            else -> null // Desktop no recibe otros tipos
        }
    }
}
```

---

## 9. Sistema de Permisos y Seguridad

### Niveles de permisos por accion

```kotlin
enum class PermissionLevel {
    ALWAYS_ALLOW,    // Sin confirmacion
    ASK_FIRST_TIME,  // Pedir una vez, recordar
    ALWAYS_ASK,      // Confirmacion cada vez
    DENY             // Bloqueado
}
```

### Configuracion por defecto

| Accion | Nivel default | Razon |
|--------|--------------|-------|
| `FILE_READ` | ASK_FIRST_TIME | Acceso a archivos sensibles |
| `FILE_WRITE` | ALWAYS_ASK | Modifica datos del usuario |
| `FILE_SEND` | ALWAYS_ASK | Transferencia de datos |
| `FILE_LIST` | ALWAYS_ALLOW | Solo metadatos |
| `FILE_DELETE` | ALWAYS_ASK | Destructivo |
| `COMMAND_EXEC` | ALWAYS_ASK | Potencialmente peligroso |
| `APP_OPEN` | ASK_FIRST_TIME | Lanza procesos |
| `CLIPBOARD_GET` | ALWAYS_ALLOW | Lectura |
| `CLIPBOARD_SET` | ALWAYS_ALLOW | No destructivo |
| `NOTIFICATION` | ALWAYS_ALLOW | Solo visual |
| `STATUS_REPORT` | ALWAYS_ALLOW | Solo lectura |
| `RECENT_FILES` | ALWAYS_ALLOW | Solo metadatos |
| `OLLAMA_COMPLETE` | ALWAYS_ALLOW | Procesamiento local |
| `OLLAMA_LIST` | ALWAYS_ALLOW | Solo lectura |

### Restricciones de COMMAND_EXEC

```kotlin
// Whitelist de comandos base (configurable por el usuario)
val DEFAULT_COMMAND_WHITELIST = setOf(
    "ls", "dir", "cat", "head", "tail", "wc",
    "date", "whoami", "hostname", "uptime",
    "python3", "node", "git status", "git log",
    "open", "xdg-open", "start"
)

// Blacklist absoluta (nunca permitidos)
val COMMAND_BLACKLIST = setOf(
    "rm -rf", "mkfs", "dd", "format",
    "shutdown", "reboot", "halt",
    "chmod 777", "curl | sh", "wget | sh"
)
```

### Sandboxing de rutas de archivos

```kotlin
// Solo acceso dentro de directorios permitidos
val DEFAULT_ALLOWED_PATHS = listOf(
    System.getProperty("user.home"),  // ~/
    // NO: /etc, /usr, /System, C:\Windows
)

// Directorios explicitamente bloqueados
val BLOCKED_PATHS = listOf(
    "/.ssh", "/.gnupg", "/.aws",
    "/Keychain", "/Passwords"
)
```

---

## 10. Heartbeat y Monitoreo

### HeartbeatEmitter.kt

El desktop emite heartbeats a todos los dispositivos conectados cada 10 segundos:

```kotlin
class HeartbeatEmitter(
    private val sessionManager: SessionManager,
    private val platformServices: PlatformServices
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start() {
        scope.launch {
            while (isActive) {
                val heartbeat = DeviceHeartbeat(
                    deviceId = getLocalDeviceId(),
                    timestamp = System.currentTimeMillis(),
                    cpuLoadPercent = platformServices.getCpuLoad(),
                    memoryUsedPercent = platformServices.getMemoryUsage(),
                    thermalState = platformServices.getThermalState(),
                    ollamaRunning = platformServices.isOllamaRunning()
                )

                val message = EcosystemMessage(
                    id = UUID.randomUUID().toString(),
                    type = MessageType.HEARTBEAT,
                    payload = heartbeat
                )

                sessionManager.broadcastToAll(ProtocolSerializer.encode(message))
                delay(10_000)
            }
        }
    }
}
```

### CapabilityReporter.kt

Se envia automaticamente al conectarse un nuevo dispositivo:

```kotlin
class CapabilityReporter(
    private val ollamaClient: OllamaClient,
    private val platformDetector: PlatformDetector,
    private val config: DesktopConfig
) {
    suspend fun generateReport(): DeviceCapabilityReport {
        val ollamaModels = try {
            ollamaClient.listModels()
        } catch (_: Exception) {
            emptyList()
        }

        return DeviceCapabilityReport(
            deviceId = getLocalDeviceId(),
            deviceName = platformDetector.getDeviceName(),
            platform = platformDetector.getPlatform(), // MACOS, WINDOWS, LINUX
            supportedActions = DesktopAction.entries.toList(),
            ollamaModels = ollamaModels.map { model ->
                OllamaModelInfo(
                    name = model.name,
                    sizeBytes = model.size,
                    parameterCount = model.details?.parameterSize,
                    quantization = model.details?.quantizationLevel
                )
            },
            protocolVersion = 1
        )
    }
}
```

---

## 11. Integracion con Ollama

### OllamaClient.kt

Ollama corre como servicio independiente en `localhost:11434`. Desktop actua como proxy:

```kotlin
class OllamaClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "http://localhost:11434"
) {
    suspend fun complete(model: String, prompt: String, system: String? = null): OllamaResponse {
        val response = httpClient.post("$baseUrl/api/generate") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("model", model)
                put("prompt", prompt)
                system?.let { put("system", it) }
                put("stream", false)
            })
        }
        return response.body<OllamaResponse>()
    }

    suspend fun listModels(): List<OllamaModel> {
        val response = httpClient.get("$baseUrl/api/tags")
        return response.body<OllamaModelList>().models
    }

    fun isRunning(): Boolean {
        return try {
            // HTTP GET http://localhost:11434/ devuelve "Ollama is running"
            true
        } catch (_: Exception) {
            false
        }
    }
}
```

### Ciclo de vida con Android

1. Desktop detecta modelos Ollama al iniciar y al cambiar
2. Envia `CAPABILITY_REPORT` con `ollamaModels` al Android
3. Android auto-registra `DesktopOllamaProvider` por cada modelo en `ProviderRegistry`
4. Cuando `ExecutionRouter` selecciona un provider de escritorio, Android envia `OLLAMA_COMPLETE`
5. Desktop proxea la request a Ollama y devuelve el resultado

---

## 12. Configuracion del Usuario

### Archivo: `~/.aura/desktop-config.json`

```json
{
  "server": {
    "port": 8765,
    "autoStart": true,
    "discoverable": true
  },
  "security": {
    "requirePairing": true,
    "autoApproveActions": ["CLIPBOARD_GET", "CLIPBOARD_SET", "NOTIFICATION", "STATUS_REPORT", "OLLAMA_COMPLETE", "OLLAMA_LIST", "RECENT_FILES", "FILE_LIST"],
    "alwaysAskActions": ["FILE_WRITE", "FILE_DELETE", "COMMAND_EXEC", "FILE_SEND"],
    "deniedActions": [],
    "allowedPaths": ["~"],
    "blockedPaths": ["~/.ssh", "~/.gnupg", "~/.aws"]
  },
  "ollama": {
    "enabled": true,
    "baseUrl": "http://localhost:11434",
    "autoDetectModels": true
  },
  "commands": {
    "whitelist": ["ls", "cat", "head", "python3", "node", "git"],
    "maxTimeoutMs": 60000
  },
  "ui": {
    "startMinimized": true,
    "showNotificationOnAction": true,
    "theme": "system"
  }
}
```

---

## 13. UI del Desktop (Compose Multiplatform)

### Pantallas

La UI es deliberadamente minimalista. Vive en la system tray y se abre como panel al hacer click.

#### 13.1 Panel de Status (pantalla principal)

```
┌─────────────────────────────────────────┐
│  Aura Desktop                     [─][×]│
├─────────────────────────────────────────┤
│  ● Servidor activo  puerto 8765         │
│  IP local: 192.168.1.50                 │
│                                         │
│  Dispositivos conectados (1):           │
│  ┌───────────────────────────────────┐  │
│  │ 📱 Pixel 8 de Theo               │  │
│  │    Conectado hace 2 min           │  │
│  │    Acciones ejecutadas: 14        │  │
│  └───────────────────────────────────┘  │
│                                         │
│  Ollama: ● Activo (3 modelos)           │
│  CPU: 12%  RAM: 45%                     │
├─────────────────────────────────────────┤
│  [Log de actividad]  [Configuracion]    │
└─────────────────────────────────────────┘
```

#### 13.2 Log de Actividad

```
┌─────────────────────────────────────────┐
│  Actividad reciente                     │
├─────────────────────────────────────────┤
│  14:32  FILE_READ ~/Documents/notas.txt │
│         ✓ 1.2KB leidos (45ms)           │
│                                         │
│  14:31  OLLAMA_COMPLETE llama3          │
│         ✓ 247 tokens (3.2s)             │
│                                         │
│  14:30  CLIPBOARD_SET                   │
│         ✓ Texto copiado                 │
│                                         │
│  14:28  COMMAND_EXEC "git status"       │
│         ⚠ Requirio aprobacion           │
│         ✓ exit 0 (120ms)               │
└─────────────────────────────────────────┘
```

#### 13.3 Configuracion

```
┌─────────────────────────────────────────┐
│  Configuracion                          │
├─────────────────────────────────────────┤
│  Servidor                               │
│    Puerto: [8765]                       │
│    [✓] Iniciar con el sistema           │
│    [✓] Modo descubrible en LAN          │
│                                         │
│  Seguridad                              │
│    Acciones automaticas:                │
│    [✓] Clipboard  [✓] Notificaciones   │
│    [✓] Status     [✓] Ollama           │
│    [ ] Archivos   [ ] Comandos          │
│                                         │
│  Ollama                                 │
│    URL: [http://localhost:11434]         │
│    [✓] Auto-detectar modelos            │
│    Modelos: llama3, codellama, mistral  │
│                                         │
│  Dispositivos pareados                  │
│    📱 Pixel 8 de Theo  [Desparear]      │
│                                         │
│  [Guardar]  [Restablecer defaults]      │
└─────────────────────────────────────────┘
```

---

## 14. Manejo de Errores y Reconexion

### Desde el lado Desktop

```kotlin
// Errores en ejecucion de acciones
sealed class ExecutionError(val code: String, val message: String) {
    class FileNotFound(path: String) : ExecutionError("FILE_NOT_FOUND", "File not found: $path")
    class PermissionDenied(action: String) : ExecutionError("PERMISSION_DENIED", "Action denied: $action")
    class Timeout(action: String) : ExecutionError("TIMEOUT", "Action timed out: $action")
    class OllamaUnavailable : ExecutionError("OLLAMA_DOWN", "Ollama is not running")
    class CommandFailed(exitCode: Int) : ExecutionError("CMD_FAILED", "Command exited with $exitCode")
    class InternalError(detail: String) : ExecutionError("INTERNAL", detail)
}
```

Cada error se mapea a un `ActionResponse(success = false, error = "{code}: {message}")`.

### Reconexion automatica

- Android ya implementa auto-reconnect en `AuraWebSocketClient` (backoff de 3s).
- Desktop debe aceptar reconexiones al mismo `/ws/{deviceId}` sin requerir re-pairing.
- Si el `sharedSecret` ya existe en `PairingStore`, la sesion se restablece automaticamente.
- Desktop envia un nuevo `CAPABILITY_REPORT` en cada reconexion (los modelos de Ollama pueden haber cambiado).

---

## 15. Diferencias por Plataforma

### Abstracciones necesarias

```kotlin
interface PlatformServices {
    fun getPlatform(): Platform
    fun getDeviceName(): String
    fun getCpuLoad(): Float
    fun getMemoryUsage(): Float
    fun getThermalState(): String?
    fun isOllamaRunning(): Boolean
    fun openApplication(name: String, args: List<String>): ProcessResult
    fun showNotification(title: String, body: String)
    fun getRecentFiles(limit: Int): List<FileInfo>
    fun moveToTrash(path: String): Boolean
}
```

### Implementaciones por SO

| Funcionalidad | macOS | Windows | Linux |
|---------------|-------|---------|-------|
| Abrir app | `open -a "AppName"` | `start "" "app.exe"` | `xdg-open` / nombre directo |
| Notificacion | `osascript -e 'display notification'` | `New-BurntToast` o WinRT toast | `notify-send` |
| Archivos recientes | `~/Library/Application Support/com.apple.sharedfilelist` | `%APPDATA%\Microsoft\Windows\Recent` | `~/.local/share/recently-used.xbel` |
| Mover a trash | `com.apple.NSFileManager.trashItem` | `SHFileOperation MOVE` | `gio trash` |
| CPU/Memory | `sysctl` o `oshi` library | WMI o `oshi` | `/proc/stat`, `/proc/meminfo` o `oshi` |
| Thermal | `powermetrics` | WMI | `/sys/class/thermal` |

**Recomendacion**: Usar la libreria [OSHI](https://github.com/oshi/oshi) para metricas de sistema cross-platform.

---

## 16. Plan de Implementacion por Fases

### Fase 1 - Nucleo funcional (MVP)

**Objetivo**: Desktop que se conecta, parea, y ejecuta acciones basicas.

| # | Tarea | Detalle |
|---|-------|---------|
| 1.1 | Setup del modulo `desktop/` | Gradle, dependencias, Compose Desktop, enlace con `:protocol` |
| 1.2 | WebSocket Server (Ktor) | Endpoint `/ws/{deviceId}`, acepta conexiones |
| 1.3 | MessageRouter | Deserializa `EcosystemMessage`, despacha por tipo |
| 1.4 | PairingHandler + PairingStore | Flujo de pairing completo con codigo de 6 digitos |
| 1.5 | HeartbeatEmitter | Emision periodica de heartbeats |
| 1.6 | CapabilityReporter | Envio de capabilities al conectar |
| 1.7 | FileExecutor | FILE_READ, FILE_WRITE, FILE_LIST, FILE_DELETE |
| 1.8 | ClipboardExecutor | CLIPBOARD_GET, CLIPBOARD_SET |
| 1.9 | NotificationExecutor | NOTIFICATION nativa |
| 1.10 | System Tray basico | Icono + "Abrir panel" / "Salir" |
| 1.11 | UI: Panel de status | Estado del server, dispositivos conectados |

**Entregable**: Desktop que se parea con Android y ejecuta operaciones de archivos, clipboard, y notificaciones.

### Fase 2 - Ollama y ejecucion de comandos

| # | Tarea | Detalle |
|---|-------|---------|
| 2.1 | OllamaClient | HTTP client a localhost:11434 |
| 2.2 | OllamaExecutor | OLLAMA_COMPLETE, OLLAMA_LIST |
| 2.3 | Deteccion automatica de modelos | Polling de Ollama + actualizacion de capabilities |
| 2.4 | ShellExecutor | COMMAND_EXEC con whitelist y sandbox |
| 2.5 | AppExecutor | APP_OPEN multi-plataforma |
| 2.6 | StatusExecutor | STATUS_REPORT, RECENT_FILES |
| 2.7 | PermissionGuard completo | UI de confirmacion para acciones restringidas |
| 2.8 | UI: Log de actividad | Historial de acciones ejecutadas |
| 2.9 | UI: Configuracion | Settings editables desde la UI |

**Entregable**: Desktop que sirve como proxy de Ollama y ejecuta comandos de forma segura.

### Fase 3 - Pulido y distribucion

| # | Tarea | Detalle |
|---|-------|---------|
| 3.1 | Auto-start con el sistema | Launch agent (macOS), Startup folder (Windows), systemd (Linux) |
| 3.2 | Cifrado E2E | SharedSecretCrypto para mensajes sensibles |
| 3.3 | Sync bidireccional | Sincronizacion de datos de tareas (DirectSyncManager) |
| 3.4 | Packaging multiplataforma | .dmg, .msi, .deb/.AppImage via conveyor o jpackage |
| 3.5 | Auto-update | Mecanismo de actualizacion automatica |
| 3.6 | Testing | Unit tests para executors, integration tests para WebSocket |
| 3.7 | Documentacion de usuario | Guia de setup y uso |

---

## 17. Modelo de Datos Local del Desktop

Desktop no necesita una base de datos completa. Solo necesita:

### Config Store (`~/.aura/desktop-config.json`)
- Configuracion del servidor, seguridad, Ollama, UI (ver seccion 12)

### Paired Devices (`~/.aura/paired-devices.json`)
- Lista de dispositivos pareados con secretos compartidos

### Action Log (`~/.aura/action-log.jsonl`)
- Append-only, una linea JSON por accion ejecutada
- Rotacion automatica (mantener ultimos 1000 registros)

```json
{"timestamp":1742345600000,"deviceId":"android-uuid","action":"FILE_READ","params":{"path":"~/doc.txt"},"success":true,"durationMs":45}
```

### Sync Queue (`~/.aura/sync-queue/`)
- Archivos pendientes de sincronizar cuando Android se reconecte

---

## 18. Testing Strategy

### Unit Tests

| Componente | Que testear |
|------------|-------------|
| `MessageRouter` | Despacho correcto por tipo de mensaje |
| `FileExecutor` | Lectura, escritura, listado, borrado; validacion de paths |
| `ShellExecutor` | Whitelist/blacklist, timeout, sandboxing |
| `OllamaClient` | Parsing de respuestas, manejo de errores |
| `PairingHandler` | Flujo completo de pairing, generacion de codigo |
| `PermissionGuard` | Validacion de permisos por accion y dispositivo |
| `ProtocolSerializer` | Serialize/deserialize round-trip de todos los MessagePayload |

### Integration Tests

| Test | Descripcion |
|------|-------------|
| WebSocket handshake | Conectar, enviar pairing, recibir ack |
| Action round-trip | Enviar ActionRequest, recibir ActionResponse |
| Heartbeat stream | Verificar emision periodica |
| Reconnect | Desconectar y reconectar sin re-pairing |
| Ollama proxy | End-to-end con Ollama real (si disponible) |

---

## 19. Referencia Rapida de Archivos del Modulo Protocol (Reutilizables)

Estos archivos ya existen y el desktop los consume directamente:

| Archivo | Contenido |
|---------|-----------|
| `protocol/EcosystemMessages.kt` | `EcosystemMessage`, `MessageType`, todos los payloads |
| `protocol/DesktopAction.kt` | Enum con las 14 acciones |
| `protocol/Platform.kt` | Enum: ANDROID, MACOS, WINDOWS, LINUX |
| `protocol/ExecutionTarget.kt` | Enum: LOCAL_PHONE, REMOTE_DESKTOP, CLOUD, ANY |
| `protocol/ProtocolSerializer.kt` | Configuracion de kotlinx.serialization polimorfico |

---

## 20. Checklist de Compatibilidad con Android

Antes de dar por terminada cada fase, verificar:

- [ ] Android puede conectarse via WebSocket al desktop
- [ ] Pairing funciona end-to-end (Android inicia, Desktop muestra codigo, Android confirma)
- [ ] `DeviceCapabilityReport` se recibe y Android registra providers en `ProviderRegistry`
- [ ] Heartbeats llegan y `DeviceRegistry` actualiza `lastHeartbeatAt`
- [ ] `DesktopActionClient` puede enviar cada accion y recibir respuesta
- [ ] `DesktopOllamaProvider` funciona como proveedor LLM desde Android
- [ ] Reconexion automatica funciona sin re-pairing
- [ ] Las acciones que requieren confirmacion muestran dialogo en desktop
- [ ] El log de acciones se muestra en la UI del desktop
- [ ] `ExecutionRouter` en Android ruta correctamente a `REMOTE_DESKTOP`
