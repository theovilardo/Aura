package com.theveloper.aura.engine.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Qwen25InstructLLMService @Inject constructor(
    @ApplicationContext context: Context
) : LiteRtLocalLLMService(
    context = context,
    spec = ModelCatalog.qwen2_5_1_5B,
    tier = LLMTier.QWEN_2_5_1_5B,
    defaultMaxOutputTokens = 1024
)
