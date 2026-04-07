package com.theveloper.aura.engine.llm

import android.content.Context
import com.theveloper.aura.engine.classifier.LLMClassificationContext
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
    defaultMaxOutputTokens = 1024
) {
    override fun localClassifierSystemPrompt(context: LLMClassificationContext): String {
        return super.localClassifierSystemPrompt(context)
    }
}
