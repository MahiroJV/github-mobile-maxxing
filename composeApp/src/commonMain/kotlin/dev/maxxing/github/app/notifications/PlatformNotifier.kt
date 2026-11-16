package dev.maxxing.github.app.notifications

import dev.maxxing.github.model.NotificationThread

expect object PlatformNotifier {
    fun ensureInitialized()
    fun showGitHubNotifications(threads: List<NotificationThread>)
    fun showTestNotification(): Boolean
    fun syncBackgroundSchedule(enabled: Boolean)
}
