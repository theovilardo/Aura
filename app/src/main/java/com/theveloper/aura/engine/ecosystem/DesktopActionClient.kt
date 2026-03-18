package com.theveloper.aura.engine.ecosystem

import com.theveloper.aura.protocol.ActionRequest
import com.theveloper.aura.protocol.ActionResponse
import com.theveloper.aura.protocol.DesktopAction
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level API for requesting actions on a connected desktop device.
 * Translates typed method calls into [ActionRequest] messages sent over WebSocket.
 */
@Singleton
class DesktopActionClient @Inject constructor(
    private val webSocketClient: AuraWebSocketClient,
    private val deviceRegistry: DeviceRegistry
) {

    fun isDesktopAvailable(): Boolean = deviceRegistry.hasDesktopAvailable()

    suspend fun readFile(path: String): ActionResponse =
        sendAction(DesktopAction.FILE_READ, buildJsonObject { put("path", path) })

    suspend fun writeFile(path: String, content: String): ActionResponse =
        sendAction(DesktopAction.FILE_WRITE, buildJsonObject {
            put("path", path)
            put("content", content)
        })

    suspend fun listFiles(directory: String, pattern: String? = null): ActionResponse =
        sendAction(DesktopAction.FILE_LIST, buildJsonObject {
            put("path", directory)
            pattern?.let { put("pattern", it) }
        })

    suspend fun deleteFile(path: String): ActionResponse =
        sendAction(DesktopAction.FILE_DELETE, buildJsonObject {
            put("path", path)
        }, requiresConfirmation = true)

    suspend fun execCommand(command: String): ActionResponse =
        sendAction(DesktopAction.COMMAND_EXEC, buildJsonObject {
            put("command", command)
        }, requiresConfirmation = true)

    suspend fun openApp(appName: String): ActionResponse =
        sendAction(DesktopAction.APP_OPEN, buildJsonObject { put("app", appName) })

    suspend fun getClipboard(): ActionResponse =
        sendAction(DesktopAction.CLIPBOARD_GET, JsonObject(emptyMap()))

    suspend fun setClipboard(text: String): ActionResponse =
        sendAction(DesktopAction.CLIPBOARD_SET, buildJsonObject { put("text", text) })

    suspend fun showNotification(title: String, body: String): ActionResponse =
        sendAction(DesktopAction.NOTIFICATION, buildJsonObject {
            put("title", title)
            put("body", body)
        })

    suspend fun getStatusReport(): ActionResponse =
        sendAction(DesktopAction.STATUS_REPORT, JsonObject(emptyMap()))

    suspend fun ollamaComplete(model: String, prompt: String, systemPrompt: String = ""): ActionResponse =
        sendAction(DesktopAction.OLLAMA_COMPLETE, buildJsonObject {
            put("model", model)
            put("prompt", prompt)
            if (systemPrompt.isNotBlank()) put("system", systemPrompt)
        })

    suspend fun ollamaListModels(): ActionResponse =
        sendAction(DesktopAction.OLLAMA_LIST, JsonObject(emptyMap()))

    private suspend fun sendAction(
        action: DesktopAction,
        params: JsonObject,
        requiresConfirmation: Boolean = false
    ): ActionResponse {
        return webSocketClient.sendAction(
            ActionRequest(
                action = action,
                params = params,
                requiresConfirmation = requiresConfirmation
            )
        )
    }
}
