package com.theveloper.aura.di

import com.theveloper.aura.engine.fetcher.Fetcher
import com.theveloper.aura.engine.fetcher.impl.CurrencyFetcher
import com.theveloper.aura.engine.fetcher.impl.FlightPricesFetcher
import com.theveloper.aura.engine.fetcher.impl.WeatherFetcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class FetcherModule {

    @Binds
    @IntoSet
    abstract fun bindWeatherFetcher(
        fetcher: WeatherFetcher
    ): Fetcher

    @Binds
    @IntoSet
    abstract fun bindCurrencyFetcher(
        fetcher: CurrencyFetcher
    ): Fetcher

    @Binds
    @IntoSet
    abstract fun bindFlightPricesFetcher(
        fetcher: FlightPricesFetcher
    ): Fetcher
}
