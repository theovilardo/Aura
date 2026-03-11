package com.theveloper.aura.engine.fetcher.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class OpenMeteoResponse(
    @SerialName("current_weather")
    val currentWeather: CurrentWeather
)

@Serializable
data class CurrentWeather(
    val temperature: Double,
    val windspeed: Double,
    val winddirection: Double,
    val weathercode: Int,
    val time: String
)

interface WeatherApi {
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: String,
        @Query("longitude") longitude: String,
        @Query("current_weather") currentWeather: Boolean = true
    ): OpenMeteoResponse
}
