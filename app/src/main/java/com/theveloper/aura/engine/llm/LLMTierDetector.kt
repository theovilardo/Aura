package com.theveloper.aura.engine.llm

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LLMTierDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun detect(
        cloudFallbackAllowed: Boolean,
        groqConfigured: Boolean
    ): TierDetectionResult = withContext(Dispatchers.Default) {
        if (isGeminiNanoLikelyAvailable()) {
            return@withContext TierDetectionResult(
                primaryTier = LLMTier.GEMINI_NANO,
                supportsAdvancedTier = hasEnoughRam(minGb = 6f),
                reasonForTier = "This device looks compatible with built-in system AI."
            )
        }

        val ramGb = getTotalRamGb()
        val hasGpu = hasVulkanCompute()
        val hasNpu = hasNpuHint()

        if (ramGb >= 3f) {
            return@withContext TierDetectionResult(
                primaryTier = LLMTier.GEMMA_3_1B,
                supportsAdvancedTier = ramGb >= 6f && (hasGpu || hasNpu),
                reasonForTier = "The hardware should handle a downloadable local model."
            )
        }

        if (cloudFallbackAllowed && groqConfigured) {
            return@withContext TierDetectionResult(
                primaryTier = LLMTier.GROQ_API,
                supportsAdvancedTier = false,
                reasonForTier = "Cloud fallback is recommended because the hardware looks limited."
            )
        }

        TierDetectionResult(
            primaryTier = LLMTier.RULES_ONLY,
            supportsAdvancedTier = false,
            reasonForTier = "Aura will stay on rules until a compatible backend is ready."
        )
    }

    private fun getTotalRamGb(): Float {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem / (1024f * 1024f * 1024f)
    }

    private fun hasEnoughRam(minGb: Float): Boolean = getTotalRamGb() >= minGb

    private fun hasVulkanCompute(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE)
    }

    private fun hasNpuHint(): Boolean {
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
            "tensor" in Build.HARDWARE.lowercase() ||
                manufacturer.contains("qualcomm") ||
                model.contains("snapdragon") ||
                model.contains("dimensity") ||
                manufacturer.contains("samsung") && Regex("s2[4-9]").containsMatchIn(model)
            )
    }

    private fun isGeminiNanoLikelyAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false
        }

        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        val pixelSupported = manufacturer.contains("google") &&
            (model.contains("pixel 8") || model.contains("pixel 9"))
        val galaxySupported = manufacturer.contains("samsung") &&
            Regex("s24|s25").containsMatchIn(model)
        return pixelSupported || galaxySupported
    }
}
