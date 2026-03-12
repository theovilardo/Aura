package com.theveloper.aura.engine.llm

import android.content.Context
import java.io.File

data class ModelSpec(
    val id: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val tier: LLMTier,
    val isRequired: Boolean,
    val requiresAuthentication: Boolean = false,
    val accessPageUrl: String = ""
) {
    fun file(context: Context): File = File(context.filesDir, "models/$fileName")
}

object ModelCatalog {
    val gemma3_1B = ModelSpec(
        id = "gemma3-1b",
        fileName = "gemma3-1b-it-int4.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm",
        sizeBytes = 620_000_000L,
        tier = LLMTier.GEMMA_3_1B,
        isRequired = true,
        requiresAuthentication = true,
        accessPageUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT"
    )

    val gemma3nE2B = ModelSpec(
        id = "gemma3n-e2b",
        fileName = "gemma3n-e2b-it-int4.litertlm",
        downloadUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma3n-e2b-it-int4.litertlm",
        sizeBytes = 1_100_000_000L,
        tier = LLMTier.GEMMA_3N_E2B,
        isRequired = false,
        requiresAuthentication = true,
        accessPageUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm"
    )

    val all = listOf(gemma3_1B, gemma3nE2B)
}
