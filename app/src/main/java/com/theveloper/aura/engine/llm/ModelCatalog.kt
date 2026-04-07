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
    val bestFor: String,
    val caution: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val tier: LLMTier,
    val slot: LocalModelSlot,
    val isRequired: Boolean,
    val requiresAuthentication: Boolean = false,
    val accessPageUrl: String = "",
    val legacyFileNames: List<String> = emptyList(),
    val isRuntimeCompatible: Boolean = true,
    val runtimeCompatibilityNote: String = ""
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
        bestFor = "General organization, structured task building, and reliable offline prompts.",
        caution = "Safer on storage and thermals, but not as rich as the heavier 1.5B-class models.",
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

    val gemma4E2B = ModelSpec(
        id = "gemma4-e2b",
        displayName = "Gemma 4 E2B",
        summary = "New Gemma 4 option with much stronger local headroom than the lightweight models.",
        bestFor = "Richer drafting, planning, and longer local prompts on phones that can comfortably run advanced-tier models.",
        caution = "A major step up in download size and memory pressure from the primary-slot models, so it is best on stronger phones.",
        fileName = "gemma-4-E2B-it.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        sizeBytes = 2_583_000_000L,
        tier = LLMTier.GEMMA_4_E2B,
        slot = LocalModelSlot.ADVANCED,
        isRequired = false,
        requiresAuthentication = false,
        accessPageUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"
    )

    val qwen3_0_6B = ModelSpec(
        id = "qwen3-0.6b",
        displayName = "Qwen 3 0.6B",
        summary = "The lightest option for short drafting, organization, and general content.",
        bestFor = "Quick categorization, simple drafting, and low-footprint daily assistance.",
        caution = "Lowest download pressure, but also the shortest context and least headroom for complex prompts.",
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
        bestFor = "Planning, longer structured outputs, and richer general-purpose writing.",
        caution = "Noticeably heavier on storage, RAM, and sustained device temperature than the lightweight models.",
        fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        sizeBytes = 1_600_000_000L,
        tier = LLMTier.QWEN_2_5_1_5B,
        slot = LocalModelSlot.ADVANCED,
        isRequired = false,
        requiresAuthentication = false,
        accessPageUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct"
    )

    val gemma4E4B = ModelSpec(
        id = "gemma4-e4b",
        displayName = "Gemma 4 E4B",
        summary = "Largest Gemma 4 download in the library, built for the richest on-device responses.",
        bestFor = "Deep local planning, richer reasoning, and the most capable Gemma 4 experience on high-end phones.",
        caution = "Very large download and working set; this is one of the easiest local models to push weaker phones into thermal or memory trouble.",
        fileName = "gemma-4-E4B-it.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        sizeBytes = 3_650_000_000L,
        tier = LLMTier.GEMMA_4_E4B,
        slot = LocalModelSlot.ADVANCED,
        isRequired = false,
        requiresAuthentication = false,
        accessPageUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm"
    )

    val gemma3nE2B = ModelSpec(
        id = "gemma3n-e2b",
        displayName = "Gemma 3n E2B",
        summary = "Highest-capacity local option, best reserved for high-end phones and richer prompts.",
        bestFor = "The richest local responses when the phone can sustain a very large model comfortably.",
        caution = "Very heavy download and the easiest option to push weaker phones into memory or thermal stress.",
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
    val advancedModels = listOf(qwen2_5_1_5B, gemma4E2B, gemma4E4B, gemma3nE2B)
    val selectablePrimaryModels = primaryModels.filter { it.isRuntimeCompatible }
    val selectableAdvancedModels = advancedModels.filter { it.isRuntimeCompatible }

    val defaultPrimary = gemma3_1B
    val defaultAdvanced = qwen2_5_1_5B

    val all = primaryModels + advancedModels

    fun findById(id: String?): ModelSpec? = all.firstOrNull { it.id == id }

    fun primaryById(id: String?): ModelSpec = selectablePrimaryModels.firstOrNull { it.id == id } ?: defaultPrimary

    fun advancedById(id: String?): ModelSpec = selectableAdvancedModels.firstOrNull { it.id == id } ?: defaultAdvanced
}
