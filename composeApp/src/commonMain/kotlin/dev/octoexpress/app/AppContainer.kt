package dev.octoexpress.app

import dev.octoexpress.app.notifications.PlatformNotifier
import dev.octoexpress.auth.SessionManager
import dev.octoexpress.model.IssueFilterPreset
import dev.octoexpress.model.IssueStateFilter
import dev.octoexpress.model.NotificationThread
import dev.octoexpress.model.ViewerProfile
import dev.octoexpress.network.GitHubRepository
import dev.octoexpress.network.NotificationCheckResult
import dev.octoexpress.network.NotificationMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class IssuePullTarget(
    val owner: String,
    val repo: String,
    val number: Int,
    val hintTitle: String? = null
)

class AppContainer {
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessionManager: SessionManager = SessionManager(
        defaultClientId = OctoBuildConfig.DEFAULT_GITHUB_CLIENT_ID
    )
    val repository: GitHubRepository = GitHubRepository(sessionManager)
    val notificationMonitor: NotificationMonitor = NotificationMonitor(sessionManager, repository)

    private val _viewerProfile = MutableStateFlow<ViewerProfile?>(null)
    val viewerProfile: StateFlow<ViewerProfile?> = _viewerProfile.asStateFlow()

    private val _unreadNotificationCount = MutableStateFlow(0)
    val unreadNotificationCount: StateFlow<Int> = _unreadNotificationCount.asStateFlow()

    private val _unreadThreads = MutableStateFlow<List<NotificationThread>?>(null)
    val unreadThreads: StateFlow<List<NotificationThread>?> = _unreadThreads.asStateFlow()

    fun updateUnreadCount(count: Int) {
        _unreadNotificationCount.value = count.coerceAtLeast(0)
    }

    fun updateUnreadThreads(threads: List<NotificationThread>) {
        _unreadThreads.value = threads
        _unreadNotificationCount.value = threads.count { it.unread }
    }

    suspend fun syncNotifications(): Result<NotificationCheckResult> {
        return notificationMonitor.checkForNewNotifications().onSuccess { check ->
            _unreadThreads.value = check.allUnread
            _unreadNotificationCount.value = check.allUnread.size
            if (check.newlyArrivedForOs.isNotEmpty()) {
                PlatformNotifier.showGitHubNotifications(check.newlyArrivedForOs)
            }
            val topTargets = check.allUnread.mapNotNull { thread ->
                thread.subjectNumber?.let { num ->
                    IssuePullTarget(thread.repoOwner, thread.repoName, num, thread.subjectTitle)
                }
            }.take(3)
            prefetchIssuePullTargets(topTargets)
        }
    }

    fun prefetchIssuePullTargets(targets: List<IssuePullTarget>) {
        targets.take(3).forEach { target ->
            backgroundScope.launch {
                repository.prefetchIssueOrPull(target.owner, target.repo, target.number)
            }
        }
    }

    fun warmUpWorkspace(login: String?) {
        backgroundScope.launch {
            launch { repository.getNotifications(all = true, participating = false, forceRefresh = false) }
            launch { repository.getViewerRepositories(forceRefresh = false) }
            launch { repository.getStarredRepositories(forceRefresh = false) }
            if (!login.isNullOrBlank()) {
                launch {
                    repository.getMyIssuesAndPulls(
                        preset = IssueFilterPreset.CREATED,
                        viewerLogin = login,
                        stateFilter = IssueStateFilter.OPEN,
                        forceRefresh = false
                    ).onSuccess { list ->
                        prefetchIssuePullTargets(
                            list.take(3).map { IssuePullTarget(it.repoOwner, it.repoName, it.number, it.title) }
                        )
                    }
                }
            }
        }
    }

    suspend fun refreshViewerProfile(): ViewerProfile? {
        return repository.getViewerProfile().getOrNull()?.also {
            _viewerProfile.value = it
        }
    }

    fun clearCachedProfile() {
        _viewerProfile.value = null
        _unreadNotificationCount.value = 0
        _unreadThreads.value = null
        backgroundScope.launch {
            repository.clearCache()
        }
    }
}
