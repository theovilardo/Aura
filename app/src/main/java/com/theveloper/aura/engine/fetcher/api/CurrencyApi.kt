package com.theveloper.aura.engine.fetcher.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

@Serializable
data class CurrencyResponse(
    val result: String,
    @SerialName("time_last_update_unix")
    val timeLastUpdateUnix: Long,
    @SerialName("conversion_rate")
    val conversionRate: Double
)

interface CurrencyApi {
    @GET("{apiKey}/pair/{from}/{to}")
    suspend fun getExchangeRate(
        @Path("apiKey") apiKey: String,
        @Path("from") from: String,
        @Path("to") to: String
    ): CurrencyResponse
}
