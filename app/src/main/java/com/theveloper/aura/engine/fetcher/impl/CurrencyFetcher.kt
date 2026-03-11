package com.theveloper.aura.engine.fetcher.impl

import com.theveloper.aura.BuildConfig
import com.theveloper.aura.domain.model.FetcherType
import com.theveloper.aura.engine.fetcher.FetchResult
import com.theveloper.aura.engine.fetcher.Fetcher
import com.theveloper.aura.engine.fetcher.api.CurrencyApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrencyFetcher @Inject constructor(
    private val currencyApi: CurrencyApi,
    private val json: Json
) : Fetcher {
    override val type: FetcherType = FetcherType.CURRENCY_EXCHANGE

    override suspend fun fetch(params: Map<String, String>): FetchResult {
        val from = params["from"] ?: return FetchResult.MissingParams("from")
        val to = params["to"] ?: return FetchResult.MissingParams("to")

        return try {
            val response = currencyApi.getExchangeRate(BuildConfig.EXCHANGERATE_API_KEY, from, to)
            
            val resultJson = json.encodeToString(
                mapOf(
                    "tasa_conversion" to response.conversionRate.toString(),
                    "desde" to from,
                    "hacia" to to
                )
            )
            
            FetchResult.Success(resultJson)
        } catch (e: Exception) {
            e.printStackTrace()
            FetchResult.Error(e.message ?: "Failed to fetch currency")
        }
    }
}
