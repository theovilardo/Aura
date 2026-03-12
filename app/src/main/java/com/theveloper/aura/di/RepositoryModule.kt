package com.theveloper.aura.di

import com.theveloper.aura.data.repository.TaskRepositoryImpl
import com.theveloper.aura.domain.repository.TaskRepository
import com.theveloper.aura.data.repository.HabitRepositoryImpl
import com.theveloper.aura.domain.repository.HabitRepository
import com.theveloper.aura.data.repository.ReminderRepositoryImpl
import com.theveloper.aura.domain.repository.ReminderRepository
import com.theveloper.aura.data.repository.UserPatternRepositoryImpl
import com.theveloper.aura.domain.repository.UserPatternRepository
import com.theveloper.aura.data.repository.FetcherConfigRepositoryImpl
import com.theveloper.aura.data.repository.MemoryRepositoryImpl
import com.theveloper.aura.domain.repository.FetcherConfigRepository
import com.theveloper.aura.domain.repository.MemoryRepository
import com.theveloper.aura.data.repository.SuggestionRepositoryImpl
import com.theveloper.aura.domain.repository.SuggestionRepository
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
        taskRepositoryImpl: TaskRepositoryImpl
    ): TaskRepository
    @Binds
    @Singleton
    abstract fun bindHabitRepository(
        habitRepositoryImpl: HabitRepositoryImpl
    ): HabitRepository

    @Binds
    @Singleton
    abstract fun bindReminderRepository(
        reminderRepositoryImpl: ReminderRepositoryImpl
    ): ReminderRepository

    @Binds
    @Singleton
    abstract fun bindUserPatternRepository(
        userPatternRepositoryImpl: UserPatternRepositoryImpl
    ): UserPatternRepository

    @Binds
    @Singleton
    abstract fun bindFetcherConfigRepository(
        fetcherConfigRepositoryImpl: FetcherConfigRepositoryImpl
    ): FetcherConfigRepository

    @Binds
    @Singleton
    abstract fun bindSuggestionRepository(
        suggestionRepositoryImpl: SuggestionRepositoryImpl
    ): SuggestionRepository

    @Binds
    @Singleton
    abstract fun bindMemoryRepository(
        memoryRepositoryImpl: MemoryRepositoryImpl
    ): MemoryRepository
}
