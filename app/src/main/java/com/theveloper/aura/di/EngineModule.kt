package com.theveloper.aura.di

import com.theveloper.aura.engine.classifier.GroqLLMService
import com.theveloper.aura.engine.classifier.LLMService
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
    abstract fun bindLLMService(
        groqLLMService: GroqLLMService
    ): LLMService
}
