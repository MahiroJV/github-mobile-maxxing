package dev.octoexpress.app.notifications

import dev.octoexpress.model.NotificationReason
import dev.octoexpress.model.NotificationThread
import dev.octoexpress.network.NotificationMonitor

actual object PlatformNotifier {
    actual fun ensureInitialized() {
        // No-op on Linux Desktop; notifications use notify-send / D-Bus on demand.
    }

    actual fun showGitHubNotifications(threads: List<NotificationThread>) {
        if (threads.isEmpty()) return
        if (threads.size <= 3) {
            threads.forEach { thread ->
                val urgency = if (thread.reason in NotificationMonitor.URGENT_REASONS) "critical" else "normal"
                val reasonLabel = formatReason(thread.reason)
                sendLinuxNotification(
                    title = "${thread.repoFullName} ($reasonLabel)",
                    body = thread.subjectTitle,
                    urgency = urgency
                )
            }
        } else {
            val preview = threads.take(3).joinToString("\n") { "• ${it.repoFullName}: ${it.subjectTitle}" }
            sendLinuxNotification(
                title = "${threads.size} new GitHub notifications",
                body = preview,
                urgency = "normal"
            )
        }
    }

    actual fun showTestNotification(): Boolean {
        return sendLinuxNotification(
            title = "OctoExpress Test Notification",
            body = "Desktop notifications are active and working!",
            urgency = "normal"
        )
    }

    actual fun syncBackgroundSchedule(enabled: Boolean) {
        // Desktop foreground coroutine poller manages polling interval automatically.
    }

    private fun sendLinuxNotification(title: String, body: String, urgency: String): Boolean {
        return runCatching {
            val pb = ProcessBuilder(
                "notify-send",
                "-a", "OctoExpress",
                "-u", urgency,
                "-i", "dialog-information",
                "--",
                title,
                body
            )
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
            pb.environment().remove("LD_LIBRARY_PATH")
            val proc = pb.start()
            proc.outputStream.close()
            true
        }.getOrDefault(false)
    }

    private fun formatReason(reason: NotificationReason): String =
        reason.name.lowercase().replace('_', ' ')
}
