package com.theveloper.aura.engine.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Qwen3SmallLLMService @Inject constructor(
    @ApplicationContext context: Context
) : LiteRtLocalLLMService(
    context = context,
    spec = ModelCatalog.qwen3_0_6B,
    tier = LLMTier.QWEN_3_0_6B,
    backendName = "CPU",
    defaultMaxOutputTokens = 768
)
