package dev.maxxing.github.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.maxxing.github.auth.AndroidAuthContextHolder
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.NotificationReason
import dev.maxxing.github.model.NotificationThread
import dev.maxxing.github.network.GitHubRepository
import dev.maxxing.github.network.NotificationMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private const val CHANNEL_URGENT = "maxxing_urgent"
private const val CHANNEL_ACTIVITY = "maxxing_activity"
private const val WORK_NAME = "github_maxxing_notification_sync"

private const val ACTION_MARK_READ = "dev.maxxing.github.app.ACTION_MARK_READ"
private const val ACTION_MARK_DONE = "dev.maxxing.github.app.ACTION_MARK_DONE"
private const val EXTRA_THREAD_ID = "thread_id"
private const val EXTRA_NOTIF_ID = "notif_id"

actual object PlatformNotifier {
    actual fun ensureInitialized() {
        val context = runCatching { AndroidAuthContextHolder.requireContext() }.getOrNull() ?: return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val urgentChannel = NotificationChannel(
            CHANNEL_URGENT,
            "Mentions & Review Requests",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High-priority GitHub mentions, review requests, and assignments."
        }

        val activityChannel = NotificationChannel(
            CHANNEL_ACTIVITY,
            "Repository Activity",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "General GitHub repository updates and subscribed threads."
        }

        manager.createNotificationChannel(urgentChannel)
        manager.createNotificationChannel(activityChannel)
    }

    actual fun showGitHubNotifications(threads: List<NotificationThread>) {
        if (threads.isEmpty()) return
        val context = runCatching { AndroidAuthContextHolder.requireContext() }.getOrNull() ?: return
        if (!hasNotificationPermission(context)) return
        ensureInitialized()

        val nm = NotificationManagerCompat.from(context)
        threads.take(6).forEach { thread ->
            val notifId = thread.id.hashCode()
            val isUrgent = thread.reason in NotificationMonitor.URGENT_REASONS
            val channelId = if (isUrgent) CHANNEL_URGENT else CHANNEL_ACTIVITY

            val contentIntent = buildOpenThreadIntent(context, thread, notifId)
            val markReadIntent = buildActionIntent(context, ACTION_MARK_READ, thread.id, notifId)
            val markDoneIntent = buildActionIntent(context, ACTION_MARK_DONE, thread.id, notifId)

            val reasonLabel = thread.reason.name.lowercase().replace('_', ' ')
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("${thread.repoFullName} • $reasonLabel")
                .setContentText(thread.subjectTitle)
                .setStyle(NotificationCompat.BigTextStyle().bigText(thread.subjectTitle))
                .setAutoCancel(true)
                .setPriority(
                    if (isUrgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
                )
                .setContentIntent(contentIntent)
                .addAction(0, "Mark Read", markReadIntent)
                .addAction(0, "Done", markDoneIntent)
                .build()

            nm.notify(notifId, notification)
        }
    }

    actual fun showTestNotification(): Boolean {
        val context = runCatching { AndroidAuthContextHolder.requireContext() }.getOrNull() ?: return false
        if (!hasNotificationPermission(context)) return false
        ensureInitialized()

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                9001,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_URGENT)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("${AppBranding.APP_NAME} Notifications Ready")
            .setContentText("New mentions & review requests will appear here with Mark Read / Done actions.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
            .build()

        NotificationManagerCompat.from(context).notify(9001, notification)
        return true
    }

    actual fun syncBackgroundSchedule(enabled: Boolean) {
        val context = runCatching { AndroidAuthContextHolder.requireContext() }.getOrNull() ?: return
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<NotificationSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildOpenThreadIntent(
        context: Context,
        thread: NotificationThread,
        requestCode: Int
    ): PendingIntent {
        val num = thread.subjectNumber
        val intent = if (num != null) {
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("githubmaxxing://thread/${thread.repoOwner}/${thread.repoName}/$num")
            ).apply {
                setPackage(context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildActionIntent(
        context: Context,
        action: String,
        threadId: String,
        notifId: Int
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_NOTIF_ID, notifId)
        }
        val code = (action + threadId).hashCode()
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class NotificationSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        AndroidAuthContextHolder.init(applicationContext)
        val sessionManager = SessionManager()
        sessionManager.initialize()
        if (sessionManager.session.value == null) return Result.success()
        if (!sessionManager.notificationPreferences.value.osNotificationsEnabled) return Result.success()

        val repository = GitHubRepository(sessionManager)
        val monitor = NotificationMonitor(sessionManager, repository)
        val check = monitor.checkForNewNotifications()
        check.onSuccess { res ->
            if (res.newlyArrivedForOs.isNotEmpty()) {
                PlatformNotifier.showGitHubNotifications(res.newlyArrivedForOs)
            }
        }
        return if (check.isSuccess) Result.success() else Result.retry()
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getStringExtra(EXTRA_THREAD_ID) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        val action = intent.action ?: return

        NotificationManagerCompat.from(context).cancel(notifId)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                AndroidAuthContextHolder.init(context.applicationContext)
                val sessionManager = SessionManager()
                sessionManager.initialize()
                if (sessionManager.session.value != null) {
                    val repo = GitHubRepository(sessionManager)
                    when (action) {
                        ACTION_MARK_READ -> repo.markNotificationRead(threadId)
                        ACTION_MARK_DONE -> repo.markNotificationDone(threadId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
