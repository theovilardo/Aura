package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentClassifierTest {

    private val subject = IntentClassifier()

    @Test
    fun `meeting with date is classified as event`() {
        val result = subject.classify("Reunion con marketing el viernes")

        assertEquals(TaskType.EVENT, result.taskType)
        assertTrue(result.confidence >= 0.9f)
    }

    @Test
    fun `learning goal is classified as goal`() {
        val result = subject.classify("Quiero terminar el curso de Kotlin")

        assertEquals(TaskType.GOAL, result.taskType)
        assertTrue(result.confidence >= 0.9f)
    }
}
