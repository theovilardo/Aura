package com.theveloper.aura.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.aura.data.db.FetcherConfigDao
import com.theveloper.aura.domain.model.DataFeedStatus
import com.theveloper.aura.engine.fetcher.FetchResult
import com.theveloper.aura.engine.fetcher.FetcherEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class DataFeedUiState(
    val status: DataFeedStatus = DataFeedStatus.LOADING,
    val value: String? = null,
    val lastValue: String? = null,
    val lastUpdatedAt: Long? = null,
    val errorMessage: String? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class DataFeedViewModel @Inject constructor(
    private val fetcherConfigDao: FetcherConfigDao,
    private val fetcherEngine: FetcherEngine
) : ViewModel() {

    private val _configId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DataFeedUiState> = _configId
        .filterNotNull()
        .flatMapLatest { id ->
            fetcherConfigDao.getConfigFlow(id).map { entity ->
                if (entity == null) {
                    return@map DataFeedUiState(status = DataFeedStatus.ERROR, errorMessage = "Config missing.")
                }
                
                val lastJson = entity.lastResultJson
                if (lastJson != null) {
                    val parsedValue = parseDisplayValue(lastJson)
                    DataFeedUiState(
                        status = DataFeedStatus.DATA,
                        value = parsedValue,
                        lastUpdatedAt = entity.lastUpdatedAt
                    )
                } else {
                    DataFeedUiState(status = DataFeedStatus.STALE) // Initially or pending
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DataFeedUiState()
        )

    fun initialize(fetcherConfigId: String) {
        if (_configId.value != fetcherConfigId) {
            _configId.value = fetcherConfigId
        }
    }

    fun refreshFeed() {
        val currentId = _configId.value ?: return
        
        viewModelScope.launch {
            // Optimistic loading
            val currentEntity = fetcherConfigDao.getConfigById(currentId) ?: return@launch
            
            val paramsMap = runCatching { 
                org.json.JSONObject(currentEntity.params).let { json ->
                    json.keys().asSequence().associateWith { json.getString(it) }
                }
            }.getOrDefault(emptyMap())

            val result = fetcherEngine.fetch(currentEntity.type, paramsMap)
            when (result) {
                is FetchResult.Success -> {
                    fetcherConfigDao.updateLastResult(
                        configId = currentId,
                        json = result.data,
                        timestamp = System.currentTimeMillis()
                    )
                }
                is FetchResult.Error -> {
                    // Could notify error locally logic but flow emit will just hold state.
                }
                else -> Unit
            }
        }
    }
    
    private fun parseDisplayValue(jsonStr: String): String {
        return try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            val list = mutableListOf<String>()
            while (keys.hasNext()) {
                val next = keys.next()
                list.add("$next: ${json.getString(next)}")
            }
            list.joinToString(" | ")
        } catch (e: Exception) {
            "Parsed: $jsonStr"
        }
    }
}
