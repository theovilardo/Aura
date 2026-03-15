package com.theveloper.aura.engine.llm

import android.content.Context
import java.io.File

enum class LocalModelSlot {
    PRIMARY,
    ADVANCED
}

data class ModelSpec(
    val id: String,
    val displayName: String,
    val summary: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val tier: LLMTier,
    val slot: LocalModelSlot,
    val isRequired: Boolean,
    val requiresAuthentication: Boolean = false,
    val accessPageUrl: String = "",
    val legacyFileNames: List<String> = emptyList()
) {
    fun file(context: Context): File = File(context.filesDir, "models/$fileName")

    fun allFiles(context: Context): List<File> {
        return buildList {
            add(file(context))
            legacyFileNames.forEach { name ->
                add(File(context.filesDir, "models/$name"))
            }
        }
    }

    fun installedFile(context: Context): File? = allFiles(context).firstOrNull { it.exists() }
}

object ModelCatalog {
    val gemma3_1B = ModelSpec(
        id = "gemma3-1b",
        displayName = "Gemma 3 1B",
        summary = "Balanced default for structured task building and offline use.",
        fileName = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
        sizeBytes = 584_000_000L,
        tier = LLMTier.GEMMA_3_1B,
        slot = LocalModelSlot.PRIMARY,
        isRequired = true,
        requiresAuthentication = true,
        accessPageUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT",
        legacyFileNames = listOf("gemma3-1b-it-int4.litertlm")
    )

    val qwen3_0_6B = ModelSpec(
        id = "qwen3-0.6b",
        displayName = "Qwen 3 0.6B",
        summary = "The lightest option for short drafting, organization, and general content.",
        fileName = "Qwen3-0.6B.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
        sizeBytes = 614_000_000L,
        tier = LLMTier.QWEN_3_0_6B,
        slot = LocalModelSlot.PRIMARY,
        isRequired = false,
        requiresAuthentication = false,
        accessPageUrl = "https://huggingface.co/litert-community/Qwen3-0.6B"
    )

    val qwen2_5_1_5B = ModelSpec(
        id = "qwen2.5-1.5b",
        displayName = "Qwen 2.5 1.5B",
        summary = "Heavier than 1B-class models, but usually stronger for planning and richer writing.",
        fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        sizeBytes = 1_600_000_000L,
        tier = LLMTier.QWEN_2_5_1_5B,
        slot = LocalModelSlot.ADVANCED,
        isRequired = false,
        requiresAuthentication = false,
        accessPageUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct"
    )

    val gemma3nE2B = ModelSpec(
        id = "gemma3n-e2b",
        displayName = "Gemma 3n E2B",
        summary = "Highest-capacity local option, best reserved for high-end phones and richer prompts.",
        fileName = "gemma3n-e2b-it-int4.litertlm",
        downloadUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma3n-e2b-it-int4.litertlm",
        sizeBytes = 3_660_000_000L,
        tier = LLMTier.GEMMA_3N_E2B,
        slot = LocalModelSlot.ADVANCED,
        isRequired = false,
        requiresAuthentication = true,
        accessPageUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm"
    )

    val primaryModels = listOf(gemma3_1B, qwen3_0_6B)
    val advancedModels = listOf(qwen2_5_1_5B, gemma3nE2B)

    val defaultPrimary = gemma3_1B
    val defaultAdvanced = qwen2_5_1_5B

    val all = primaryModels + advancedModels

    fun findById(id: String?): ModelSpec? = all.firstOrNull { it.id == id }

    fun primaryById(id: String?): ModelSpec = primaryModels.firstOrNull { it.id == id } ?: defaultPrimary

    fun advancedById(id: String?): ModelSpec = advancedModels.firstOrNull { it.id == id } ?: defaultAdvanced
}
