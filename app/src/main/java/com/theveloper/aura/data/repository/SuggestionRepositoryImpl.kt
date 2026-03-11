package com.theveloper.aura.data.repository

import com.theveloper.aura.data.db.SuggestionDao
import com.theveloper.aura.data.mapper.toDomain
import com.theveloper.aura.data.mapper.toEntity
import com.theveloper.aura.domain.model.Suggestion
import com.theveloper.aura.domain.model.SuggestionStatus
import com.theveloper.aura.domain.repository.SuggestionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SuggestionRepositoryImpl @Inject constructor(
    private val suggestionDao: SuggestionDao
) : SuggestionRepository {

    override suspend fun save(suggestion: Suggestion) {
        suggestionDao.insert(suggestion.toEntity())
    }

    override fun getPendingSuggestions(currentTime: Long): Flow<List<Suggestion>> {
        return suggestionDao.getPendingSuggestions(currentTime).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun updateStatus(id: String, status: SuggestionStatus) {
        suggestionDao.updateStatus(id, status.name)
    }

    override suspend fun markExpired(currentTime: Long) {
        suggestionDao.markExpired(currentTime)
    }
}
