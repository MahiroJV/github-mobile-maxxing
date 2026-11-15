package dev.octoexpress.network

import dev.octoexpress.auth.SessionManager
import dev.octoexpress.model.NotificationReason
import dev.octoexpress.model.NotificationThread

data class NotificationCheckResult(
    val allUnread: List<NotificationThread>,
    val newlyArrivedForOs: List<NotificationThread>
)

class NotificationMonitor(
    private val sessionManager: SessionManager,
    private val repository: GitHubRepository
) {
    suspend fun checkForNewNotifications(): Result<NotificationCheckResult> {
        if (sessionManager.session.value == null) {
            return Result.success(NotificationCheckResult(emptyList(), emptyList()))
        }

        val prefs = sessionManager.notificationPreferences.value
        val previousMarkers = sessionManager.loadSeenNotificationMarkers()

        return repository.getNotifications(all = false, participating = false).map { unread ->
            val nextMarkers = previousMarkers.toMutableMap()
            val candidates = mutableListOf<NotificationThread>()

            val isFirstBaseline = previousMarkers.isEmpty()
            for (thread in unread) {
                val prevUpdated = previousMarkers[thread.id]
                if (!isFirstBaseline && (prevUpdated == null || prevUpdated != thread.updatedAt)) {
                    candidates.add(thread)
                }
                nextMarkers[thread.id] = thread.updatedAt
            }

            // Persist a sentinel marker so an initially-empty inbox (0 unread) does NOT
            // keep isFirstBaseline = true forever and swallow the first real notification.
            nextMarkers[BASELINE_MARKER_KEY] = "1"

            // Keep map bounded to active unread threads + baseline sentinel
            val activeIds = unread.mapTo(HashSet()) { it.id }
            val trimmedMarkers = if (nextMarkers.size > 200) {
                nextMarkers.filterKeys { it in activeIds || it == BASELINE_MARKER_KEY }
            } else {
                nextMarkers
            }
            sessionManager.saveSeenNotificationMarkers(trimmedMarkers)

            val forOs = if (!prefs.osNotificationsEnabled) {
                emptyList()
            } else if (prefs.urgentOnly) {
                candidates.filter { it.reason in URGENT_REASONS }
            } else {
                candidates
            }

            NotificationCheckResult(
                allUnread = unread,
                newlyArrivedForOs = forOs
            )
        }
    }

    companion object {
        internal const val BASELINE_MARKER_KEY = "__octo_baseline__"

        val URGENT_REASONS = setOf(
            NotificationReason.REVIEW_REQUESTED,
            NotificationReason.MENTION,
            NotificationReason.TEAM_MENTION,
            NotificationReason.ASSIGN,
            NotificationReason.SECURITY_ALERT,
            NotificationReason.AUTHOR
        )
    }
}
