package com.theveloper.aura.engine.fetcher.impl

import com.theveloper.aura.domain.model.FetcherType
import com.theveloper.aura.engine.fetcher.FetchResult
import com.theveloper.aura.engine.fetcher.Fetcher
import com.theveloper.aura.engine.fetcher.api.WeatherApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherFetcher @Inject constructor(
    private val weatherApi: WeatherApi,
    private val json: Json
) : Fetcher {
    override val type: FetcherType = FetcherType.WEATHER

    override suspend fun fetch(params: Map<String, String>): FetchResult {
        val lat = params["latitude"] ?: return FetchResult.MissingParams("latitude")
        val lon = params["longitude"] ?: return FetchResult.MissingParams("longitude")

        return try {
            val response = weatherApi.getCurrentWeather(lat, lon)
            
            // Generate a simple JSON to store the result that the DataFeedComponent can digest easily
            val resultJson = json.encodeToString(
                mapOf(
                    "temperature" to "${response.currentWeather.temperature}°C",
                    "description" to getWeatherDescription(response.currentWeather.weathercode)
                )
            )
            
            FetchResult.Success(resultJson)
        } catch (e: Exception) {
            e.printStackTrace()
            FetchResult.Error(e.message ?: "Failed to fetch weather")
        }
    }
    
    // WMO Weather interpretation codes
    private fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "Cielo despejado"
            1, 2, 3 -> "Parcialmente nublado"
            45, 48 -> "Niebla"
            51, 53, 55 -> "Llovizna"
            61, 63, 65 -> "Lluvia"
            71, 73, 75 -> "Nieve"
            95 -> "Tormenta"
            else -> "Desconocido"
        }
    }
}
