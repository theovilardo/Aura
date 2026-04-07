package com.theveloper.aura.engine.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Gemma4E4BLLMService @Inject constructor(
    @ApplicationContext private val appContext: Context
) : LiteRtLocalLLMService(
    context = appContext,
    spec = ModelCatalog.gemma4E4B,
    tier = LLMTier.GEMMA_4_E4B,
    defaultMaxOutputTokens = 1024
) {
    override fun localClassifierSystemPrompt(): String {
        return appContext.assets.open("system_prompt.txt").bufferedReader().use { it.readText() }
    }
}
