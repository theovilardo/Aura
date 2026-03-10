package com.theveloper.aura.di

import com.theveloper.aura.data.repository.TaskRepositoryImpl
import com.theveloper.aura.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindTaskRepository(
        fakeTaskRepository: com.theveloper.aura.data.repository.FakeTaskRepository
    ): TaskRepository
}
