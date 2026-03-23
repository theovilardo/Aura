package com.theveloper.aura.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class Platform {
    ANDROID,
    MACOS,
    WINDOWS,
    LINUX
}
