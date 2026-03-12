package com.theveloper.aura.engine.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Gemma1BLLMService @Inject constructor(
    @ApplicationContext context: Context
) : LiteRtLocalLLMService(
    context = context,
    spec = ModelCatalog.gemma3_1B,
    tier = LLMTier.GEMMA_3_1B,
    backendName = "CPU",
    defaultMaxOutputTokens = 512
)
