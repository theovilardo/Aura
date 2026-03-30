package com.theveloper.aura.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.theveloper.aura.data.db.Converters

@Database(
    entities = [
        TaskEntity::class,
        TaskComponentEntity::class,
        ChecklistItemEntity::class,
        HabitSignalEntity::class,
        UserPatternEntity::class,
        ReminderEntity::class,
        FetcherConfigEntity::class,
        SuggestionEntity::class,
        SyncQueueEntity::class,
        MemorySlotEntity::class,
        ComponentRuleEntity::class,
        PairedDeviceEntity::class,
        // v5: Multi-Creation-Type
        AuraReminderEntity::class,
        ReminderChecklistItemEntity::class,
        AuraAutomationEntity::class,
        AuraEventEntity::class,
        EventSubActionEntity::class,
        EventComponentEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskComponentDao(): TaskComponentDao
    abstract fun checklistItemDao(): ChecklistItemDao
    abstract fun habitSignalDao(): HabitSignalDao
    abstract fun userPatternDao(): UserPatternDao
    abstract fun reminderDao(): ReminderDao
    abstract fun fetcherConfigDao(): FetcherConfigDao
    abstract fun suggestionDao(): SuggestionDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun memorySlotDao(): MemorySlotDao
    abstract fun componentRuleDao(): ComponentRuleDao
    abstract fun pairedDeviceDao(): PairedDeviceDao

    // v5: Multi-Creation-Type DAOs
    abstract fun auraReminderDao(): AuraReminderDao
    abstract fun reminderChecklistItemDao(): ReminderChecklistItemDao
    abstract fun auraAutomationDao(): AuraAutomationDao
    abstract fun auraEventDao(): AuraEventDao
    abstract fun eventSubActionDao(): EventSubActionDao
    abstract fun eventComponentDao(): EventComponentDao
}
