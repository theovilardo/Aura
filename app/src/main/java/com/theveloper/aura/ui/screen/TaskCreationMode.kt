package com.theveloper.aura.ui.screen

enum class TaskCreationMode(val navValue: String) {
    PROMPT("prompt"),
    MANUAL("manual");

    companion object {
        fun fromNavValue(value: String?): TaskCreationMode {
            return entries.firstOrNull { it.navValue == value } ?: PROMPT
        }
    }
}
