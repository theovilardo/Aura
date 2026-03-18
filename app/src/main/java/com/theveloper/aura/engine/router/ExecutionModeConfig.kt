package com.theveloper.aura.engine.router

import com.theveloper.aura.engine.classifier.AiExecutionMode

/**
 * Ecosystem-level execution mode. Extends the existing [AiExecutionMode] with
 * finer-grained control for multi-device routing.
 */
enum class ExecutionMode(val storageValue: String) {
    ASK_FIRST("ask_first"),
    AUTO_DECIDE("auto_decide"),
    AUTO_SILENT("auto_silent"),
    MANUAL("manual");

    companion object {
        fun fromStorage(value: String?): ExecutionMode =
            entries.firstOrNull { it.storageValue == value } ?: AUTO_DECIDE

        /** Maps legacy AiExecutionMode to the new ecosystem ExecutionMode. */
        fun fromLegacy(legacy: AiExecutionMode): ExecutionMode = when (legacy) {
            AiExecutionMode.AUTO -> AUTO_DECIDE
            AiExecutionMode.LOCAL_FIRST -> AUTO_DECIDE
            AiExecutionMode.CLOUD_FIRST -> AUTO_DECIDE
        }
    }
}

data class ExecutionModeConfig(
    val defaultMode: ExecutionMode = ExecutionMode.AUTO_DECIDE,
    val preferDesktopForHighComplexity: Boolean = true,
    val preferLocalWhenQualitySufficient: Boolean = true,
    val maxAutoCostUsdPerRequest: Float = 0.05f
)
