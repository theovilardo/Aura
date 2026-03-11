package com.theveloper.aura

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import javax.inject.Inject

@HiltAndroidApp
class AuraApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
