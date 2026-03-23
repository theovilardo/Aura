package com.theveloper.aura.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class DesktopAction {
    FILE_READ,
    FILE_WRITE,
    FILE_SEND,
    FILE_LIST,
    FILE_DELETE,
    COMMAND_EXEC,
    APP_OPEN,
    CLIPBOARD_GET,
    CLIPBOARD_SET,
    NOTIFICATION,
    STATUS_REPORT,
    RECENT_FILES,
    OLLAMA_COMPLETE,
    OLLAMA_LIST
}
