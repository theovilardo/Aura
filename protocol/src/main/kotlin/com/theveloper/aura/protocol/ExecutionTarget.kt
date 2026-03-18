package com.theveloper.aura.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class ExecutionTarget {
    LOCAL_PHONE,
    REMOTE_DESKTOP,
    CLOUD,
    ANY
}
