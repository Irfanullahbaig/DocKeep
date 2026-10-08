package com.dockeep.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dockeep.app.DocKeepApplication
import com.dockeep.app.MainActivity
import com.dockeep.app.R
import com.dockeep.app.domain.ExpiryStatus
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object ExpiryNotifications {
    const val CHANNEL_ID = "dockeep_expiry"
    const val WORK_NAME = "dockeep_expiry_check"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Expiry reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Local reminders for documents that are expiring soon."
        }
        manager.createNotificationChannel(channel)
    }

    fun schedule(context: Context) {
        runCatching {
            val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    fun cancel(context: Context) {
        runCatching {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        }
    }

    fun show(context: Context, expiringSoon: Int, expired: Int) {
        if (expiringSoon == 0 && expired == 0) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val text = buildString {
            if (expired > 0) append("$expired expired")
            if (expired > 0 && expiringSoon > 0) append(" · ")
            if (expiringSoon > 0) append("$expiringSoon expiring soon")
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dockeep_logo)
            .setContentTitle("Document expiry")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("You have $text. Open DocKeep to review them. Nothing was sent off this device."))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}

class ExpiryCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? DocKeepApplication ?: return Result.success()
        val notificationsEnabled = app.container.settingsRepository.settings.first().expiryNotificationsEnabled
        if (!notificationsEnabled) return Result.success()
        val docs = app.container.documentRepository.getAllDocuments()
        val expiring = docs.count { it.expiryStatus == ExpiryStatus.EXPIRING_SOON }
        val expired = docs.count { it.expiryStatus == ExpiryStatus.EXPIRED }
        if (expiring > 0 || expired > 0) {
            ExpiryNotifications.show(applicationContext, expiring, expired)
        }
        return Result.success()
    }
}

class ExpiryNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ExpiryNotifications.ensureChannel(context)
        ExpiryNotifications.schedule(context)
    }
}
