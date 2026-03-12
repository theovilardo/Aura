package com.theveloper.aura.engine.memory

import com.theveloper.aura.domain.model.MemorySlot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryCompactor @Inject constructor() {

    fun compact(slot: MemorySlot): MemorySlot {
        val compacted = slot.content
            .split(Regex("\\s+"))
            .take(slot.maxTokens.coerceAtLeast(1))
            .joinToString(" ")
            .trim()

        return slot.copy(
            content = compacted,
            version = slot.version + 1,
            tokenCount = compacted.countApproxTokens()
        )
    }
}

internal fun String.countApproxTokens(): Int {
    return trim()
        .split(Regex("\\s+"))
        .count { it.isNotBlank() }
}
