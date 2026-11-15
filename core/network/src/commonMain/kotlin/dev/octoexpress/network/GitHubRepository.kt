package dev.octoexpress.network

import dev.octoexpress.auth.SessionManager
import dev.octoexpress.model.BranchRef
import dev.octoexpress.model.FileContent
import dev.octoexpress.model.IssueFilterPreset
import dev.octoexpress.model.IssueOrPullDetail
import dev.octoexpress.model.IssueOrPullKind
import dev.octoexpress.model.IssueOrPullSummary
import dev.octoexpress.model.IssueStateFilter
import dev.octoexpress.model.MergeMethod
import dev.octoexpress.model.NotificationThread
import dev.octoexpress.model.PullFileChange
import dev.octoexpress.model.RateLimitInfo
import dev.octoexpress.model.RepoDetail
import dev.octoexpress.model.RepoSummary
import dev.octoexpress.model.ReviewEvent
import dev.octoexpress.model.TreeEntry
import dev.octoexpress.model.ViewerProfile
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private data class CacheEntry<V>(
    val value: V,
    val recordedAt: TimeMark = TimeSource.Monotonic.markNow()
)

private class LruMemoryCache<K, V>(
    private val ttl: Duration,
    private val maxEntries: Int = 64
) {
    private val mutex = Mutex()

    @Volatile
    private var snapshot: Map<K, CacheEntry<V>> = emptyMap()

    fun peek(key: K): V? = snapshot[key]?.value

    fun peekIfFresh(key: K): V? {
        val entry = snapshot[key] ?: return null
        return if (entry.recordedAt.elapsedNow() < ttl) entry.value else null
    }

    suspend fun put(key: K, value: V) {
        mutex.withLock {
            val next = LinkedHashMap<K, CacheEntry<V>>(snapshot.size + 1)
            for ((k, v) in snapshot) {
                if (k != key) next[k] = v
            }
            next[key] = CacheEntry(value)
            while (next.size > maxEntries) {
                val eldest = next.keys.firstOrNull() ?: break
                next.remove(eldest)
            }
            snapshot = next
        }
    }

    suspend fun invalidate(key: K) {
        mutex.withLock {
            if (key in snapshot) {
                val next = LinkedHashMap(snapshot)
                next.remove(key)
                snapshot = next
            }
        }
    }

    suspend fun invalidateWhere(predicate: (K) -> Boolean) {
        mutex.withLock {
            if (snapshot.keys.any(predicate)) {
                val next = LinkedHashMap<K, CacheEntry<V>>()
                for ((k, v) in snapshot) {
                    if (!predicate(k)) next[k] = v
                }
                snapshot = next
            }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            snapshot = emptyMap()
        }
    }
}

private data class NotificationsKey(val all: Boolean, val participating: Boolean, val page: Int)
private data class MyIssuesKey(val preset: IssueFilterPreset, val viewerLogin: String, val stateFilter: IssueStateFilter)
private data class RepoKey(val owner: String, val repo: String)
private data class IssuePullKey(val owner: String, val repo: String, val number: Int)
private data class RepoIssuesKey(val owner: String, val repo: String, val pullRequests: Boolean, val stateFilter: IssueStateFilter)
private data class RepoPathKey(val owner: String, val repo: String, val path: String, val ref: String?)

class GitHubRepository(
    private val sessionManager: SessionManager,
    private val networkConfig: GitHubNetworkConfig = GitHubNetworkConfig(sessionManager),
    private val httpClient: HttpClient = networkConfig.createHttpClient(),
    private val restService: GitHubRestService = GitHubRestService(httpClient, networkConfig.baseUrl),
    private val graphQlService: GitHubGraphQlService = GitHubGraphQlService(httpClient, networkConfig.baseUrl)
) {
    val rateLimit: StateFlow<RateLimitInfo> = networkConfig.rateLimit

    private val profileCache = LruMemoryCache<String, ViewerProfile>(ttl = 300.seconds, maxEntries = 2)
    private val notificationsCache = LruMemoryCache<NotificationsKey, List<NotificationThread>>(ttl = 30.seconds, maxEntries = 12)
    private val myIssuesCache = LruMemoryCache<MyIssuesKey, List<IssueOrPullSummary>>(ttl = 60.seconds, maxEntries = 24)
    private val issueDetailCache = LruMemoryCache<IssuePullKey, IssueOrPullDetail>(ttl = 45.seconds, maxEntries = 64)
    private val pullFilesCache = LruMemoryCache<IssuePullKey, List<PullFileChange>>(ttl = 60.seconds, maxEntries = 48)
    private val viewerReposCache = LruMemoryCache<String, List<RepoSummary>>(ttl = 120.seconds, maxEntries = 4)
    private val searchReposCache = LruMemoryCache<String, List<RepoSummary>>(ttl = 90.seconds, maxEntries = 16)
    private val repoDetailCache = LruMemoryCache<RepoKey, RepoDetail>(ttl = 120.seconds, maxEntries = 32)
    private val repoIssuesCache = LruMemoryCache<RepoIssuesKey, List<IssueOrPullSummary>>(ttl = 60.seconds, maxEntries = 48)
    private val dirCache = LruMemoryCache<RepoPathKey, List<TreeEntry>>(ttl = 180.seconds, maxEntries = 96)
    private val fileCache = LruMemoryCache<RepoPathKey, FileContent>(ttl = 180.seconds, maxEntries = 64)
    private val readmeCache = LruMemoryCache<RepoPathKey, String?>(ttl = 180.seconds, maxEntries = 32)

    suspend fun clearCache() {
        profileCache.clear()
        notificationsCache.clear()
        myIssuesCache.clear()
        issueDetailCache.clear()
        pullFilesCache.clear()
        viewerReposCache.clear()
        searchReposCache.clear()
        repoDetailCache.clear()
        repoIssuesCache.clear()
        dirCache.clear()
        fileCache.clear()
        readmeCache.clear()
    }

    // ---- Synchronous lock-free cache peeks for instant UI rendering ----

    fun peekNotifications(all: Boolean = false, participating: Boolean = false, page: Int = 1): List<NotificationThread>? =
        notificationsCache.peek(NotificationsKey(all, participating, page))

    fun peekMyIssuesAndPulls(
        preset: IssueFilterPreset,
        viewerLogin: String,
        stateFilter: IssueStateFilter = IssueStateFilter.OPEN
    ): List<IssueOrPullSummary>? = myIssuesCache.peek(MyIssuesKey(preset, viewerLogin, stateFilter))

    fun peekIssueOrPullDetail(owner: String, repo: String, number: Int): IssueOrPullDetail? =
        issueDetailCache.peek(IssuePullKey(owner, repo, number))

    fun peekPullFiles(owner: String, repo: String, number: Int): List<PullFileChange>? =
        pullFilesCache.peek(IssuePullKey(owner, repo, number))

    fun peekViewerRepositories(): List<RepoSummary>? = viewerReposCache.peek("MY")

    fun peekStarredRepositories(): List<RepoSummary>? = viewerReposCache.peek("STARRED")

    fun peekRepoDetail(owner: String, repo: String): RepoDetail? = repoDetailCache.peek(RepoKey(owner, repo))

    fun peekRepoIssuesOrPulls(
        owner: String,
        repo: String,
        pullRequests: Boolean,
        stateFilter: IssueStateFilter = IssueStateFilter.OPEN
    ): List<IssueOrPullSummary>? = repoIssuesCache.peek(RepoIssuesKey(owner, repo, pullRequests, stateFilter))

    fun peekDirectoryContents(owner: String, repo: String, path: String = "", ref: String? = null): List<TreeEntry>? =
        dirCache.peek(RepoPathKey(owner, repo, path, ref))

    fun peekFileContent(owner: String, repo: String, path: String, ref: String? = null): FileContent? =
        fileCache.peek(RepoPathKey(owner, repo, path, ref))

    fun peekReadme(owner: String, repo: String, ref: String? = null): String? =
        readmeCache.peek(RepoPathKey(owner, repo, "", ref))

    // ---- Cached / Parallel Network Operations ----

    suspend fun getViewerProfile(forceRefresh: Boolean = false): Result<ViewerProfile> {
        if (!forceRefresh) {
            profileCache.peekIfFresh("viewer")?.let { return Result.success(it) }
        }
        return runCatching { graphQlService.fetchViewerProfile() }
            .onSuccess { profileCache.put("viewer", it) }
    }

    suspend fun getNotifications(
        all: Boolean = false,
        participating: Boolean = false,
        page: Int = 1,
        forceRefresh: Boolean = true
    ): Result<List<NotificationThread>> {
        val key = NotificationsKey(all, participating, page)
        if (!forceRefresh) {
            notificationsCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatching {
            restService.fetchNotifications(all = all, participating = participating, page = page)
        }.onSuccess {
            notificationsCache.put(key, it)
        }
    }

    suspend fun markNotificationRead(threadId: String): Result<Unit> = runCatching {
        restService.markNotificationRead(threadId)
    }.onSuccess {
        notificationsCache.clear()
    }

    suspend fun markNotificationDone(threadId: String): Result<Unit> = runCatching {
        restService.markNotificationDone(threadId)
    }.onSuccess {
        notificationsCache.clear()
    }

    suspend fun markAllNotificationsRead(): Result<Unit> = runCatching {
        restService.markAllNotificationsRead()
    }.onSuccess {
        notificationsCache.clear()
    }

    suspend fun getMyIssuesAndPulls(
        preset: IssueFilterPreset,
        viewerLogin: String,
        stateFilter: IssueStateFilter = IssueStateFilter.OPEN,
        forceRefresh: Boolean = false
    ): Result<List<IssueOrPullSummary>> {
        val key = MyIssuesKey(preset, viewerLogin, stateFilter)
        if (!forceRefresh) {
            myIssuesCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatching {
            graphQlService.fetchIssuesAndPulls(preset, viewerLogin, stateFilter)
        }.onSuccess {
            myIssuesCache.put(key, it)
        }
    }

    suspend fun getIssueOrPullDetail(
        owner: String,
        repo: String,
        number: Int,
        forceRefresh: Boolean = false
    ): Result<IssueOrPullDetail> {
        val key = IssuePullKey(owner, repo, number)
        if (!forceRefresh) {
            issueDetailCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatching {
            graphQlService.fetchIssueOrPullDetail(owner, repo, number)
        }.onSuccess {
            issueDetailCache.put(key, it)
        }
    }

    suspend fun getPullFiles(
        owner: String,
        repo: String,
        number: Int,
        forceRefresh: Boolean = false
    ): Result<List<PullFileChange>> {
        val key = IssuePullKey(owner, repo, number)
        if (!forceRefresh) {
            pullFilesCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatching {
            restService.fetchPullFiles(owner, repo, number)
        }.onSuccess {
            pullFilesCache.put(key, it)
        }
    }

    suspend fun prefetchIssueOrPull(owner: String, repo: String, number: Int) {
        val detail = getIssueOrPullDetail(owner, repo, number, forceRefresh = false).getOrNull() ?: return
        if (detail.kind == IssueOrPullKind.PULL_REQUEST && peekPullFiles(owner, repo, number) == null) {
            getPullFiles(owner, repo, number, forceRefresh = false)
        }
    }

    suspend fun postComment(
        owner: String,
        repo: String,
        number: Int,
        body: String
    ): Result<Unit> = runCatching {
        restService.postComment(owner, repo, number, body)
    }.onSuccess {
        issueDetailCache.invalidate(IssuePullKey(owner, repo, number))
    }

    suspend fun setIssueOrPullState(
        owner: String,
        repo: String,
        number: Int,
        open: Boolean
    ): Result<Unit> = runCatching {
        restService.setIssueOrPullState(owner, repo, number, open)
    }.onSuccess {
        issueDetailCache.invalidate(IssuePullKey(owner, repo, number))
        myIssuesCache.clear()
        repoIssuesCache.invalidateWhere { it.owner == owner && it.repo == repo }
    }

    suspend fun createIssue(
        owner: String,
        repo: String,
        title: String,
        body: String
    ): Result<Int> = runCatching {
        restService.createIssue(owner, repo, title, body)
    }.onSuccess {
        myIssuesCache.clear()
        repoIssuesCache.invalidateWhere { it.owner == owner && it.repo == repo && !it.pullRequests }
    }

    suspend fun submitPullReview(
        owner: String,
        repo: String,
        number: Int,
        event: ReviewEvent,
        body: String
    ): Result<Unit> = runCatching {
        restService.submitPullReview(owner, repo, number, event, body)
    }.onSuccess {
        issueDetailCache.invalidate(IssuePullKey(owner, repo, number))
        myIssuesCache.clear()
    }

    suspend fun mergePullRequest(
        owner: String,
        repo: String,
        number: Int,
        method: MergeMethod,
        commitTitle: String? = null
    ): Result<Unit> = runCatching {
        restService.mergePullRequest(owner, repo, number, method, commitTitle)
    }.onSuccess {
        issueDetailCache.invalidate(IssuePullKey(owner, repo, number))
        myIssuesCache.clear()
        repoIssuesCache.invalidateWhere { it.owner == owner && it.repo == repo && it.pullRequests }
    }

    suspend fun getViewerRepositories(forceRefresh: Boolean = false): Result<List<RepoSummary>> {
        if (!forceRefresh) {
            viewerReposCache.peekIfFresh("MY")?.let { return Result.success(it) }
        }
        return runCatching {
            graphQlService.fetchViewerRepositories()
        }.onSuccess {
            viewerReposCache.put("MY", it)
        }
    }

    suspend fun getStarredRepositories(forceRefresh: Boolean = false): Result<List<RepoSummary>> {
        if (!forceRefresh) {
            viewerReposCache.peekIfFresh("STARRED")?.let { return Result.success(it) }
        }
        return runCatching {
            graphQlService.fetchStarredRepositories()
        }.onSuccess {
            viewerReposCache.put("STARRED", it)
        }
    }

    suspend fun searchRepositories(query: String, forceRefresh: Boolean = false): Result<List<RepoSummary>> {
        val trimmed = query.trim()
        if (!forceRefresh) {
            searchReposCache.peekIfFresh(trimmed)?.let { return Result.success(it) }
        }
        return runCatching {
            graphQlService.searchRepositories(trimmed)
        }.onSuccess {
            searchReposCache.put(trimmed, it)
        }
    }

    suspend fun getRepoDetail(
        owner: String,
        repo: String,
        forceRefresh: Boolean = false
    ): Result<RepoDetail> {
        val key = RepoKey(owner, repo)
        if (!forceRefresh) {
            repoDetailCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatching {
            coroutineScope {
                val detailDeferred = async { graphQlService.fetchRepoDetail(owner, repo) }
                val branchesDeferred = async { runCatching { restService.fetchBranches(owner, repo) }.getOrDefault(emptyList()) }
                detailDeferred.await().copy(branches = branchesDeferred.await())
            }
        }.onSuccess {
            repoDetailCache.put(key, it)
        }
    }

    suspend fun getRepoIssuesOrPulls(
        owner: String,
        repo: String,
        pullRequests: Boolean,
        stateFilter: IssueStateFilter = IssueStateFilter.OPEN,
        forceRefresh: Boolean = false
    ): Result<List<IssueOrPullSummary>> {
        val key = RepoIssuesKey(owner, repo, pullRequests, stateFilter)
        if (!forceRefresh) {
            repoIssuesCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatching {
            graphQlService.fetchRepoIssuesAndPulls(owner, repo, pullRequests, stateFilter)
        }.onSuccess {
            repoIssuesCache.put(key, it)
        }
    }

    suspend fun getBranches(owner: String, repo: String): Result<List<BranchRef>> = runCatching {
        restService.fetchBranches(owner, repo)
    }

    suspend fun getDirectoryContents(
        owner: String,
        repo: String,
        path: String = "",
        ref: String? = null,
        forceRefresh: Boolean = false
    ): Result<List<TreeEntry>> {
        val key = RepoPathKey(owner, repo, path, ref)
        if (!forceRefresh) {
            dirCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatching {
            restService.fetchDirectoryContents(owner, repo, path, ref)
        }.onSuccess {
            dirCache.put(key, it)
        }
    }

    suspend fun getFileContent(
        owner: String,
        repo: String,
        path: String,
        ref: String? = null,
        forceRefresh: Boolean = false
    ): Result<FileContent> {
        val key = RepoPathKey(owner, repo, path, ref)
        if (!forceRefresh) {
            fileCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatching {
            restService.fetchFileContent(owner, repo, path, ref)
        }.onSuccess {
            fileCache.put(key, it)
        }
    }

    suspend fun getReadme(
        owner: String,
        repo: String,
        ref: String? = null,
        forceRefresh: Boolean = false
    ): Result<String?> {
        val key = RepoPathKey(owner, repo, "", ref)
        if (!forceRefresh) {
            readmeCache.peekIfFresh(key)?.let { return Result.success(it) }
        }
        return runCatching {
            restService.fetchReadme(owner, repo, ref)
        }.onSuccess {
            readmeCache.put(key, it)
        }
    }

    suspend fun setStarred(owner: String, repo: String, starred: Boolean): Result<Unit> = runCatching {
        restService.setStarred(owner, repo, starred)
    }.onSuccess {
        repoDetailCache.invalidate(RepoKey(owner, repo))
        viewerReposCache.invalidate("STARRED")
    }
}
