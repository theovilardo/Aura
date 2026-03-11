package com.theveloper.aura.engine.fetcher

import com.theveloper.aura.domain.model.FetcherType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FetcherEngine @Inject constructor(
    private val fetchers: Set<@JvmSuppressWildcards Fetcher>
) {
    suspend fun fetch(type: FetcherType, params: Map<String, String>): FetchResult {
        val fetcher = fetchers.find { it.type == type }
        return fetcher?.fetch(params) ?: FetchResult.NotSupported
    }
}
