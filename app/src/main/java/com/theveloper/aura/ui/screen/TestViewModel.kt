package com.theveloper.aura.ui.screen

import androidx.lifecycle.ViewModel
import com.theveloper.aura.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TestViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {
    // Just for testing Hilt
}
