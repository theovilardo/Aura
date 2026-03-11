package com.theveloper.aura.core.json

import kotlinx.serialization.json.Json

val auraJson = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "config_type"
    encodeDefaults = true
}
