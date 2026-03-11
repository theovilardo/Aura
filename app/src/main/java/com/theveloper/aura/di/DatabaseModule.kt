package com.theveloper.aura.di

import android.content.Context
import androidx.room.Room
import com.theveloper.aura.data.db.AuraDatabase
import com.theveloper.aura.data.db.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AuraDatabase {
        return Room.databaseBuilder(
            context,
            AuraDatabase::class.java,
            "aura_database"
        ).build()
    }

    @Provides
    fun provideTaskDao(db: AuraDatabase): TaskDao {
        return db.taskDao()
    }

    @Provides
    fun provideTaskComponentDao(db: AuraDatabase): com.theveloper.aura.data.db.TaskComponentDao {
        return db.taskComponentDao()
    }

    @Provides
    fun provideChecklistItemDao(db: AuraDatabase): com.theveloper.aura.data.db.ChecklistItemDao {
        return db.checklistItemDao()
    }

    @Provides
    fun provideHabitSignalDao(db: AuraDatabase): com.theveloper.aura.data.db.HabitSignalDao {
        return db.habitSignalDao()
    }

    @Provides
    fun provideUserPatternDao(db: AuraDatabase): com.theveloper.aura.data.db.UserPatternDao {
        return db.userPatternDao()
    }

    @Provides
    fun provideReminderDao(db: AuraDatabase): com.theveloper.aura.data.db.ReminderDao {
        return db.reminderDao()
    }

    @Provides
    fun provideFetcherConfigDao(db: AuraDatabase): com.theveloper.aura.data.db.FetcherConfigDao {
        return db.fetcherConfigDao()
    }

    @Provides
    fun provideSuggestionDao(db: AuraDatabase): com.theveloper.aura.data.db.SuggestionDao {
        return db.suggestionDao()
    }

    @Provides
    fun provideSyncQueueDao(db: AuraDatabase): com.theveloper.aura.data.db.SyncQueueDao {
        return db.syncQueueDao()
    }
}
