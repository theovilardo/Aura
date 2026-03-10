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
}
