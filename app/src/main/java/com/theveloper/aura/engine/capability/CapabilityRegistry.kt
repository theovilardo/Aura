package com.theveloper.aura.engine.capability

import android.util.Log
import com.theveloper.aura.domain.model.TaskStatus
import com.theveloper.aura.domain.usecase.ArchiveTaskUseCase
import com.theveloper.aura.domain.usecase.CreateTaskUseCase
import com.theveloper.aura.domain.usecase.UpdateTaskStatusUseCase
import com.theveloper.aura.engine.dsl.TaskDSLOutput
import com.theveloper.aura.engine.dsl.TaskDSLValidator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CapabilityRegistry @Inject constructor(
    private val createTaskUseCase: CreateTaskUseCase,
    private val updateTaskStatusUseCase: UpdateTaskStatusUseCase,
    private val archiveTaskUseCase: ArchiveTaskUseCase
) {

    suspend fun execute(request: CapabilityRequest): CapabilityExecutionResult {
        val sanitizedRequest = sanitizeParams(request)
        validate(sanitizedRequest)?.let { reason ->
            return CapabilityExecutionResult(errorMessage = reason)
        }

        Log.d(TAG, "Executing capability ${sanitizedRequest::class.simpleName}")

        return runCatching {
            when (sanitizedRequest) {
                is CapabilityRequest.ArchiveTask -> {
                    archiveTaskUseCase(sanitizedRequest.taskId)
                    CapabilityExecutionResult(response = CapabilityResponse.Success)
                }
                is CapabilityRequest.CreateTask -> {
                    val task = createTaskUseCase(sanitizedRequest.dsl)
                    CapabilityExecutionResult(response = CapabilityResponse.TaskCreated(task.id))
                }
                is CapabilityRequest.UpdateTaskStatus -> {
                    updateTaskStatusUseCase(sanitizedRequest.taskId, sanitizedRequest.status)
                    CapabilityExecutionResult(response = CapabilityResponse.Success)
                }
            }
        }.getOrElse { error ->
            CapabilityExecutionResult(errorMessage = error.message ?: "Capability execution failed")
        }
    }

    fun sanitizeParams(request: CapabilityRequest): CapabilityRequest {
        return when (request) {
            is CapabilityRequest.ArchiveTask -> request.copy(taskId = request.taskId.trim())
            is CapabilityRequest.CreateTask -> request.copy(
                dsl = request.dsl.copy(
                    title = request.dsl.title.trim(),
                    priority = request.dsl.priority.coerceIn(0, 3)
                )
            )
            is CapabilityRequest.UpdateTaskStatus -> request.copy(taskId = request.taskId.trim())
        }
    }

    private fun validate(request: CapabilityRequest): String? {
        return when (request) {
            is CapabilityRequest.ArchiveTask -> request.taskId.takeIf { it.isBlank() }?.let { "Task id is required" }
            is CapabilityRequest.CreateTask -> when (val validation = TaskDSLValidator.validate(request.dsl)) {
                TaskDSLValidator.ValidationResult.Valid -> null
                is TaskDSLValidator.ValidationResult.Invalid -> validation.reason
            }
            is CapabilityRequest.UpdateTaskStatus -> request.taskId.takeIf { it.isBlank() }?.let { "Task id is required" }
        }
    }

    private companion object {
        const val TAG = "CapabilityRegistry"
    }
}

sealed interface CapabilityRequest {
    data class CreateTask(val dsl: TaskDSLOutput) : CapabilityRequest
    data class UpdateTaskStatus(val taskId: String, val status: TaskStatus) : CapabilityRequest
    data class ArchiveTask(val taskId: String) : CapabilityRequest
}

data class CapabilityExecutionResult(
    val response: CapabilityResponse? = null,
    val errorMessage: String? = null
)

sealed interface CapabilityResponse {
    data class TaskCreated(val taskId: String) : CapabilityResponse
    data object Success : CapabilityResponse
}
