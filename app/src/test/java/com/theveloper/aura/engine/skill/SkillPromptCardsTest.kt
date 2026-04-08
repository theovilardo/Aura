package com.theveloper.aura.engine.skill

import com.theveloper.aura.domain.model.TaskType
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillPromptCardsTest {

    @Test
    fun `compact checklist card keeps atomic item contract explicit`() {
        val checklist = requireNotNull(SkillRegistry.resolveUi("checklist"))

        val rendered = SkillPromptCards.renderUiSkillCards(
            definitions = listOf(checklist),
            profile = PromptProfile.LOCAL_COMPACT
        )

        assertTrue(rendered.contains("items=[{label,isSuggested}]"))
        assertTrue(rendered.contains("expand known bundles, recipes, kits, and ingredient groups"))
    }

    @Test
    fun `compact planner prompt exposes enriched skill semantics for local models`() {
        val prompt = SkillPromptCards.buildCompactPlannerPrompt(TaskType.GENERAL)

        assertTrue(prompt.contains("Function skills improve content; they never replace UI."))
        assertTrue(prompt.contains("clarification is the last resort"))
        assertTrue(prompt.contains("resource-curator"))
        assertTrue(prompt.contains("use_when: The user explicitly asks for links, references, docs, or sources."))
    }

    @Test
    fun `compact final task prompt uses final schema instead of planner schema`() {
        val prompt = SkillPromptCards.buildCompactFinalTaskPrompt(TaskType.GENERAL)

        assertTrue(prompt.contains("Final task JSON schema:"))
        assertTrue(prompt.contains("Never return a planner-only payload"))
        assertTrue(prompt.contains("\"skills\": ["))
        assertTrue(!prompt.contains("Plan JSON schema:"))
    }
}
