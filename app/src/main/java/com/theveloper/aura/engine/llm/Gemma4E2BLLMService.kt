package com.theveloper.aura.engine.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Gemma4E2BLLMService @Inject constructor(
    @ApplicationContext context: Context
) : LiteRtLocalLLMService(
    context = context,
    spec = ModelCatalog.gemma4E2B,
    tier = LLMTier.GEMMA_4_E2B,
    defaultMaxOutputTokens = 1024
)
