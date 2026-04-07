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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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

    @Provides
    fun provideComponentRuleDao(db: AuraDatabase): com.theveloper.aura.data.db.ComponentRuleDao {
        return db.componentRuleDao()
    }

    @Provides
    fun providePairedDeviceDao(db: AuraDatabase): com.theveloper.aura.data.db.PairedDeviceDao {
        return db.pairedDeviceDao()
    }

    // v5: Multi-Creation-Type DAOs

    @Provides
    fun provideAuraReminderDao(db: AuraDatabase): com.theveloper.aura.data.db.AuraReminderDao {
        return db.auraReminderDao()
    }

    @Provides
    fun provideReminderChecklistItemDao(db: AuraDatabase): com.theveloper.aura.data.db.ReminderChecklistItemDao {
        return db.reminderChecklistItemDao()
    }

    @Provides
    fun provideAuraAutomationDao(db: AuraDatabase): com.theveloper.aura.data.db.AuraAutomationDao {
        return db.auraAutomationDao()
    }

    @Provides
    fun provideAuraEventDao(db: AuraDatabase): com.theveloper.aura.data.db.AuraEventDao {
        return db.auraEventDao()
    }

    @Provides
    fun provideEventSubActionDao(db: AuraDatabase): com.theveloper.aura.data.db.EventSubActionDao {
        return db.eventSubActionDao()
    }

    @Provides
    fun provideEventComponentDao(db: AuraDatabase): com.theveloper.aura.data.db.EventComponentDao {
        return db.eventComponentDao()
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

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS paired_devices (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    platform TEXT NOT NULL,
                    connection_url TEXT NOT NULL,
                    relay_url TEXT,
                    shared_secret TEXT NOT NULL DEFAULT '',
                    last_seen_at INTEGER NOT NULL,
                    paired_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS component_rules (
                    id TEXT NOT NULL PRIMARY KEY,
                    task_id TEXT NOT NULL,
                    trigger_component_id TEXT NOT NULL,
                    trigger_event TEXT NOT NULL,
                    trigger_condition_json TEXT,
                    target_component_id TEXT NOT NULL,
                    action TEXT NOT NULL,
                    action_params_json TEXT,
                    is_enabled INTEGER NOT NULL DEFAULT 1,
                    priority INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    created_by TEXT NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_component_rules_task_id ON component_rules(task_id)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_component_rules_trigger_component_id ON component_rules(trigger_component_id)"
            )
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // ── aura_reminders ──────────────────────────────────────────────
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS aura_reminders (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    body TEXT NOT NULL DEFAULT '',
                    reminder_type TEXT NOT NULL,
                    scheduled_at INTEGER NOT NULL,
                    repeat_count INTEGER NOT NULL DEFAULT 0,
                    interval_ms INTEGER NOT NULL DEFAULT 0,
                    cron_expression TEXT NOT NULL DEFAULT '',
                    linked_task_id TEXT,
                    links TEXT NOT NULL DEFAULT '[]',
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_aura_reminders_linked_task_id ON aura_reminders(linked_task_id)"
            )

            // ── reminder_checklist_items ─────────────────────────────────────
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS reminder_checklist_items (
                    id TEXT NOT NULL PRIMARY KEY,
                    reminder_id TEXT NOT NULL,
                    text TEXT NOT NULL,
                    is_completed INTEGER NOT NULL DEFAULT 0,
                    sort_order INTEGER NOT NULL,
                    FOREIGN KEY(reminder_id) REFERENCES aura_reminders(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_reminder_checklist_items_reminder_id ON reminder_checklist_items(reminder_id)"
            )

            // ── aura_automations ────────────────────────────────────────────
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS aura_automations (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    prompt TEXT NOT NULL,
                    cron_expression TEXT NOT NULL,
                    execution_plan TEXT NOT NULL,
                    output_type TEXT NOT NULL DEFAULT 'NOTIFICATION',
                    last_execution_at INTEGER,
                    last_result_json TEXT,
                    status TEXT NOT NULL DEFAULT 'ACTIVE',
                    failure_count INTEGER NOT NULL DEFAULT 0,
                    max_retries INTEGER NOT NULL DEFAULT 3,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // ── aura_events ─────────────────────────────────────────────────
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS aura_events (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL DEFAULT '',
                    start_at INTEGER NOT NULL,
                    end_at INTEGER NOT NULL,
                    status TEXT NOT NULL DEFAULT 'UPCOMING',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // ── event_sub_actions ───────────────────────────────────────────
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS event_sub_actions (
                    id TEXT NOT NULL PRIMARY KEY,
                    event_id TEXT NOT NULL,
                    type TEXT NOT NULL,
                    title TEXT NOT NULL DEFAULT '',
                    cron_expression TEXT NOT NULL DEFAULT '',
                    interval_ms INTEGER NOT NULL DEFAULT 0,
                    prompt TEXT NOT NULL DEFAULT '',
                    config TEXT NOT NULL DEFAULT '{}',
                    enabled INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY(event_id) REFERENCES aura_events(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_event_sub_actions_event_id ON event_sub_actions(event_id)"
            )

            // ── event_components (link table) ───────────────────────────────
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS event_components (
                    id TEXT NOT NULL PRIMARY KEY,
                    event_id TEXT NOT NULL,
                    component_id TEXT NOT NULL,
                    FOREIGN KEY(event_id) REFERENCES aura_events(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_event_components_event_id ON event_components(event_id)"
            )
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE tasks ADD COLUMN function_skills_json TEXT NOT NULL DEFAULT '[]'"
            )
            database.execSQL(
                "ALTER TABLE task_components ADD COLUMN skill_id TEXT"
            )
            database.execSQL(
                "ALTER TABLE task_components ADD COLUMN skill_runtime TEXT"
            )
        }
    }
}
