package com.theveloper.aura.ui.renderer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.theveloper.aura.domain.model.SignalType
import com.theveloper.aura.domain.model.Task
import com.theveloper.aura.domain.model.TaskComponent

@Composable
fun ComponentRenderer(
    component: TaskComponent,
    onSignal: (SignalType) -> Unit,
    modifier: Modifier = Modifier
) {
    UiSkillRendererRegistry.Render(component = component, onSignal = onSignal, modifier = modifier)
}

@Composable
fun InterpretedComponentRenderer(
    component: TaskComponent,
    onSignal: (SignalType) -> Unit,
    onOpenNotes: (TaskComponent) -> Unit = {},
    modifier: Modifier = Modifier
) {
    UiSkillRendererRegistry.RenderInterpreted(
        component = component,
        onSignal = onSignal,
        onOpenNotes = onOpenNotes,
        modifier = modifier
    )
}

@Composable
fun EditableComponentRenderer(
    task: Task,
    component: TaskComponent,
    onTaskChange: (Task) -> Unit,
    onSignal: (SignalType) -> Unit,
    onEditNotes: (TaskComponent) -> Unit = {},
    modifier: Modifier = Modifier
) {
    UiSkillRendererRegistry.RenderEditable(
        task = task,
        component = component,
        onTaskChange = onTaskChange,
        onSignal = onSignal,
        onEditNotes = onEditNotes,
        modifier = modifier
    )
}
