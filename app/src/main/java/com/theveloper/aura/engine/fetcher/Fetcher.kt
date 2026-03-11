package com.theveloper.aura.engine.fetcher

import com.theveloper.aura.domain.model.FetcherType

sealed class FetchResult {
    data class Success(val data: String) : FetchResult() // data here represents the JSON string to cache
    data class MissingParams(val missingParam: String) : FetchResult()
    object NotSupported : FetchResult()
    data class Error(val reason: String) : FetchResult()
}

interface Fetcher {
    val type: FetcherType
    suspend fun fetch(params: Map<String, String>): FetchResult
}
