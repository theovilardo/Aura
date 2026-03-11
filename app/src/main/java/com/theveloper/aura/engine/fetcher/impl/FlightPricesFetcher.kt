package com.theveloper.aura.engine.fetcher.impl

import com.theveloper.aura.domain.model.FetcherType
import com.theveloper.aura.engine.fetcher.FetchResult
import com.theveloper.aura.engine.fetcher.Fetcher
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlightPricesFetcher @Inject constructor(
    private val json: Json
) : Fetcher {
    override val type: FetcherType = FetcherType.FLIGHT_PRICES

    override suspend fun fetch(params: Map<String, String>): FetchResult {
        val origin = params["origin"] ?: return FetchResult.MissingParams("origin")
        val destination = params["destination"] ?: return FetchResult.MissingParams("destination")
        val dateFrom = params["date_from"] ?: return FetchResult.MissingParams("date_from")
        val dateTo = params["date_to"] ?: return FetchResult.MissingParams("date_to")

        // Mockeando respuesta para el MVP segun F4-05 y Decisión Técnica
        delay(1000) // Simular red

        val randomPrice = (300..1500).random()
        
        return try {
            val resultJson = json.encodeToString(
                mapOf(
                    "precio_minimo" to "US$$randomPrice",
                    "aerolinea" to "AuraAirlines",
                    "link" to "https://vuelos.ejemplo.com/comprar?origen=$origin&destino=$destination",
                    "origen" to origin,
                    "destino" to destination
                )
            )
            
            FetchResult.Success(resultJson)
        } catch (e: Exception) {
            FetchResult.Error(e.message ?: "Failed to mock flights data")
        }
    }
}
