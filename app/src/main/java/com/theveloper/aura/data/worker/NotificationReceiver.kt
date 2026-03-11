package com.theveloper.aura.data.worker

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.theveloper.aura.domain.model.SignalType
import com.theveloper.aura.engine.habit.HabitEngine
import com.theveloper.aura.engine.reminder.ReminderEngine
import com.theveloper.aura.domain.usecase.UpdateTaskStatusUseCase
import com.theveloper.aura.domain.model.TaskStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderEngine: ReminderEngine
    @Inject lateinit var habitEngine: HabitEngine
    @Inject lateinit var updateTaskStatusUseCase: UpdateTaskStatusUseCase

    companion object {
        const val ACTION_COMPLETE = "com.theveloper.aura.ACTION_COMPLETE"
        const val ACTION_SNOOZE = "com.theveloper.aura.ACTION_SNOOZE"
        const val ACTION_DISMISS = "com.theveloper.aura.ACTION_DISMISS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra("reminderId") ?: return
        val taskId = intent.getStringExtra("taskId") ?: return
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // cancel the notification
        notificationManager.cancel(reminderId.hashCode())

        CoroutineScope(Dispatchers.IO).launch {
            when (intent.action) {
                ACTION_COMPLETE -> {
                    habitEngine.logSignal(taskId, SignalType.TASK_COMPLETED)
                    reminderEngine.onReminderResponse(reminderId, SignalType.TASK_COMPLETED)
                    updateTaskStatusUseCase(taskId, TaskStatus.COMPLETED)
                }
                ACTION_SNOOZE -> {
                    habitEngine.logSignal(taskId, SignalType.REMINDER_SNOOZED)
                    reminderEngine.onReminderResponse(reminderId, SignalType.REMINDER_SNOOZED)
                    // TODO: Para posponer 30 min, crearíamos un OneTimeWorkRequest de 30 mins
                }
                ACTION_DISMISS -> {
                    habitEngine.logSignal(taskId, SignalType.REMINDER_DISMISSED)
                    reminderEngine.onReminderResponse(reminderId, SignalType.REMINDER_DISMISSED)
                }
            }
        }
    }
}
