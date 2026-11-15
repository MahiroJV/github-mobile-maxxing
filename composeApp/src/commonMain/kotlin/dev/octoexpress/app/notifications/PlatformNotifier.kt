package dev.octoexpress.app.notifications

import dev.octoexpress.model.NotificationThread

expect object PlatformNotifier {
    fun ensureInitialized()
    fun showGitHubNotifications(threads: List<NotificationThread>)
    fun showTestNotification(): Boolean
    fun syncBackgroundSchedule(enabled: Boolean)
}
