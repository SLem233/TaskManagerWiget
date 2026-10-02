package ru.slem.taskwidget

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.time.LocalDate

object DeadlineNotifications {
    private const val CHANNEL_ID = "task_deadlines"
    private const val NOTIFICATION_ID = 4201

    fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    fun update(context: Context, tasks: List<IndexedTask>, today: LocalDate = LocalDate.now()) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val summary = DeadlineAlerts.summarize(tasks, today)
        if (summary.severity == DeadlineSeverity.NONE) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        val channel = NotificationChannel(CHANNEL_ID, "Сроки задач", NotificationManager.IMPORTANCE_DEFAULT)
        channel.setSound(null, null)
        channel.enableVibration(false)
        manager.createNotificationChannel(channel)
        val title = if (summary.overdue > 0) "Просроченные задачи" else "Сегодня дедлайн"
        val detail = buildList {
            if (summary.overdue > 0) add("Просрочено: ${summary.overdue}")
            if (summary.dueToday > 0) add("Дедлайн сегодня: ${summary.dueToday}")
        }.joinToString(" · ")
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (summary.overdue > 0) R.drawable.ic_bell_overdue else R.drawable.ic_bell)
            .setColor(summary.color)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}