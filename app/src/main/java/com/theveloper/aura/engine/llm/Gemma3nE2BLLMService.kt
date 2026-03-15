package com.theveloper.aura.engine.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Gemma3nE2BLLMService @Inject constructor(
    @ApplicationContext private val appContext: Context
) : LiteRtLocalLLMService(
    context = appContext,
    spec = ModelCatalog.gemma3nE2B,
    tier = LLMTier.GEMMA_3N_E2B,
    backendName = "CPU",
    defaultMaxOutputTokens = 1024
) {
    /**
     * E2B has enough capacity for the full system prompt with detailed reference docs.
     */
    override fun localClassifierSystemPrompt(): String {
        return appContext.assets.open("system_prompt.txt").bufferedReader().use { it.readText() }
    }
}
