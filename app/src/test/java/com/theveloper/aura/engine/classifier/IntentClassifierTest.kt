package com.theveloper.aura.engine.classifier

import com.theveloper.aura.domain.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentClassifierTest {

    private val subject = IntentClassifier()

    // --- FINANCE: currency symbols and codes ---

    @Test
    fun `dollar sign with amount is classified as finance`() {
        val result = subject.classify("Save \$500 per month")
        assertEquals(TaskType.FINANCE, result.taskType)
        assertTrue(result.confidence >= 0.45f)
    }

    @Test
    fun `euro symbol is classified as finance`() {
        val result = subject.classify("Pay rent €1200")
        assertEquals(TaskType.FINANCE, result.taskType)
        assertTrue(result.confidence >= 0.45f)
    }

    @Test
    fun `ISO currency code USD is classified as finance`() {
        val result = subject.classify("Budget: 2000 USD for the trip")
        assertEquals(TaskType.FINANCE, result.taskType)
        assertTrue(result.confidence >= 0.45f)
    }

    @Test
    fun `ISO currency code EUR is classified as finance`() {
        val result = subject.classify("Presupuesto de 500 EUR para el viaje")
        assertEquals(TaskType.FINANCE, result.taskType)
        assertTrue(result.confidence >= 0.45f)
    }

    // --- HEALTH: set×rep notation ---

    @Test
    fun `set-rep notation 3x10 is classified as health`() {
        val result = subject.classify("Squats 3x10, push-ups 4x12")
        assertEquals(TaskType.HEALTH, result.taskType)
        assertTrue(result.confidence >= 0.45f)
    }

    @Test
    fun `x notation is classified as health without relying on language keywords`() {
        val result = subject.classify("Bench press 3x8")
        assertEquals(TaskType.HEALTH, result.taskType)
        assertTrue(result.confidence >= 0.45f)
    }

    @Test
    fun `multiplication symbol notation is classified as health`() {
        val result = subject.classify("Sentadillas 4×12")
        assertEquals(TaskType.HEALTH, result.taskType)
        assertTrue(result.confidence >= 0.45f)
    }

    // --- EVENT: date and time patterns ---

    @Test
    fun `ISO date format is classified as event`() {
        val result = subject.classify("Meeting on 2025-06-15")
        assertEquals(TaskType.EVENT, result.taskType)
        assertTrue(result.confidence >= 0.44f)
    }

    @Test
    fun `time pattern is classified as event`() {
        val result = subject.classify("Call at 14:30")
        assertEquals(TaskType.EVENT, result.taskType)
        assertTrue(result.confidence >= 0.44f)
    }

    @Test
    fun `date with time has higher confidence than date alone`() {
        val withDateTime = subject.classify("Appointment on 2025-06-15 at 10:00")
        val withDateOnly = subject.classify("Appointment on 2025-06-15")
        assertTrue(withDateTime.confidence >= withDateOnly.confidence)
    }

    // --- GENERAL: no structural signals ---

    @Test
    fun `plain text with no signals is classified as general with low confidence`() {
        val result = subject.classify("I want to learn something new")
        assertEquals(TaskType.GENERAL, result.taskType)
        assertTrue(result.confidence <= 0.35f)
    }

    @Test
    fun `blank input returns general with zero confidence`() {
        val result = subject.classify("   ")
        assertEquals(TaskType.GENERAL, result.taskType)
        assertEquals(0f, result.confidence)
    }

    // --- LANGUAGE AGNOSTICISM ---

    @Test
    fun `currency signal works in any language context`() {
        // Portuguese
        assertEquals(TaskType.FINANCE, subject.classify("Pagar aluguel R\$1500").taskType)
        // French
        assertEquals(TaskType.FINANCE, subject.classify("Budget de 800€ pour les courses").taskType)
        // Japanese context with yen
        assertEquals(TaskType.FINANCE, subject.classify("旅行予算 ¥50000").taskType)
    }

    @Test
    fun `set-rep notation works regardless of surrounding language`() {
        assertEquals(TaskType.HEALTH, subject.classify("Flexiones 3x15 cada día").taskType)
        assertEquals(TaskType.HEALTH, subject.classify("Pompes 4x10 le matin").taskType)
        assertEquals(TaskType.HEALTH, subject.classify("Liegestütze 3x12").taskType)
    }

    @Test
    fun `max confidence is capped at 0_55`() {
        // Even with a very strong structural signal, confidence should never exceed 0.55
        val result = subject.classify("Pay \$5,000 USD budget expense €200 £100")
        assertTrue("Confidence should be capped: ${result.confidence}", result.confidence <= 0.55f)
    }
}
