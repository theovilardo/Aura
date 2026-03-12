package com.theveloper.aura.di

import com.theveloper.aura.engine.classifier.AiExecutionModeStore
import com.theveloper.aura.engine.classifier.DataStoreAiExecutionModeStore
import com.theveloper.aura.engine.classifier.HeuristicOnDeviceTaskDslService
import com.theveloper.aura.engine.classifier.OnDeviceTaskDslService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Binds
    @Singleton
    abstract fun bindOnDeviceTaskDslService(
        heuristicOnDeviceTaskDslService: HeuristicOnDeviceTaskDslService
    ): OnDeviceTaskDslService

    @Binds
    @Singleton
    abstract fun bindAiExecutionModeStore(
        dataStoreAiExecutionModeStore: DataStoreAiExecutionModeStore
    ): AiExecutionModeStore
}
