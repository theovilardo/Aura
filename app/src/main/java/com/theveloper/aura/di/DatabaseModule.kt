package com.theveloper.aura.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        )
            .addMigrations(MIGRATION_1_2)
            .build()
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

    @Provides
    fun provideMemorySlotDao(db: AuraDatabase): com.theveloper.aura.data.db.MemorySlotDao {
        return db.memorySlotDao()
    }

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE task_components ADD COLUMN needs_clarification INTEGER NOT NULL DEFAULT 0"
            )
            database.execSQL(
                "ALTER TABLE checklist_items ADD COLUMN is_suggested INTEGER NOT NULL DEFAULT 0"
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS memory_slots (
                    id TEXT NOT NULL PRIMARY KEY,
                    category TEXT NOT NULL,
                    content TEXT NOT NULL,
                    last_updated_at INTEGER NOT NULL,
                    version INTEGER NOT NULL DEFAULT 0,
                    token_count INTEGER NOT NULL,
                    max_tokens INTEGER NOT NULL DEFAULT 300
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_memory_slots_category ON memory_slots(category)"
            )
        }
    }
}
