package com.theveloper.aura.engine.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Gemma3nE2BLLMService @Inject constructor(
    @ApplicationContext context: Context
) : LiteRtLocalLLMService(
    context = context,
    spec = ModelCatalog.gemma3nE2B,
    tier = LLMTier.GEMMA_3N_E2B,
    backendName = "CPU",
    defaultMaxOutputTokens = 1024
)
