package com.theveloper.aura.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theveloper.aura.data.db.AuraDatabase
import com.theveloper.aura.data.db.TaskComponentDao
import com.theveloper.aura.data.db.TaskDao
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.domain.model.TaskType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskRepositoryImplTest {

    private lateinit var db: AuraDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var taskComponentDao: TaskComponentDao
    private lateinit var repository: TaskRepositoryImpl

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AuraDatabase::class.java
        ).build()
        taskDao = db.taskDao()
        taskComponentDao = db.taskComponentDao()
        repository = TaskRepositoryImpl(taskDao, taskComponentDao)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRetrieveTask() = runBlocking {
        val task = Task(
            id = "test-1",
            title = "Test Task",
            type = TaskType.GENERAL,
            status = TaskStatus.ACTIVE,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        repository.insertTask(task)
        
        val tasks = repository.getTasksFlow().first()
        assertEquals(1, tasks.size)
        assertEquals("Test Task", tasks[0].title)
    }
}
