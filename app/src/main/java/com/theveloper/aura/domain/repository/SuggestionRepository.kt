package com.theveloper.aura.domain.repository

import com.theveloper.aura.domain.model.Suggestion
import com.theveloper.aura.domain.model.SuggestionStatus
import kotlinx.coroutines.flow.Flow

interface SuggestionRepository {
    suspend fun save(suggestion: Suggestion)
    fun getPendingSuggestions(currentTime: Long = System.currentTimeMillis()): Flow<List<Suggestion>>
    suspend fun updateStatus(id: String, status: SuggestionStatus)
    suspend fun markExpired(currentTime: Long = System.currentTimeMillis())
}
