package dev.octoexpress.network

import dev.octoexpress.model.ActivityEvent
import dev.octoexpress.model.ActivityEventKind
import dev.octoexpress.model.BranchRef
import dev.octoexpress.model.CodeSearchHit
import dev.octoexpress.model.FileChangeStatus
import dev.octoexpress.model.FileContent
import dev.octoexpress.model.IssueOrPullKind
import dev.octoexpress.model.IssueOrPullState
import dev.octoexpress.model.IssueOrPullSummary
import dev.octoexpress.model.LabelTag
import dev.octoexpress.model.MergeMethod
import dev.octoexpress.model.NotificationReason
import dev.octoexpress.model.NotificationSubjectType
import dev.octoexpress.model.NotificationThread
import dev.octoexpress.model.OrganizationSummary
import dev.octoexpress.model.PullFileChange
import dev.octoexpress.model.RepoSummary
import dev.octoexpress.model.ReviewEvent
import dev.octoexpress.model.TreeEntry
import dev.octoexpress.model.TreeEntryType
import dev.octoexpress.model.UserProfile
import dev.octoexpress.model.UserSummary
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
private data class NotificationDto(
    val id: String,
    val unread: Boolean,
    val reason: String,
    @SerialName("updated_at") val updatedAt: String,
    val subject: NotificationSubjectDto,
    val repository: NotificationRepoDto
)

@Serializable
private data class NotificationSubjectDto(
    val title: String,
    val type: String,
    val url: String? = null,
    @SerialName("latest_comment_url") val latestCommentUrl: String? = null
)

@Serializable
private data class NotificationRepoDto(
    val name: String,
    @SerialName("full_name") val fullName: String,
    val private: Boolean = false,
    val owner: NotificationOwnerDto? = null
)

@Serializable
private data class NotificationOwnerDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String = ""
)

@Serializable
private data class PullFileDto(
    val filename: String,
    @SerialName("previous_filename") val previousFilename: String? = null,
    val status: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val patch: String? = null
)

@Serializable
private data class BranchDto(
    val name: String,
    val commit: BranchCommitDto
)

@Serializable
private data class BranchCommitDto(
    val sha: String
)

@Serializable
private data class ContentItemDto(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long = 0L,
    val type: String,
    val content: String? = null,
    val encoding: String? = null,
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class CommentRequest(val body: String)

@Serializable
private data class IssueStateRequest(val state: String)

@Serializable
private data class CreateIssueRequest(val title: String, val body: String)

@Serializable
private data class CreateIssueResponse(val number: Int)

@Serializable
private data class SubmitReviewRequest(val event: String, val body: String = "")

@Serializable
private data class MergePullRequest(
    @SerialName("merge_method") val mergeMethod: String,
    @SerialName("commit_title") val commitTitle: String? = null
)

@Serializable
private data class RestRepoOwnerDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String = ""
)

@Serializable
private data class RestRepoDto(
    @SerialName("node_id") val nodeId: String = "",
    val id: Long = 0L,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val description: String? = null,
    val language: String? = null,
    @SerialName("stargazers_count") val stargazersCount: Int = 0,
    @SerialName("forks_count") val forksCount: Int = 0,
    @SerialName("open_issues_count") val openIssuesCount: Int = 0,
    val private: Boolean = false,
    val fork: Boolean = false,
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("updated_at") val updatedAt: String = "",
    val owner: RestRepoOwnerDto? = null
) {
    fun toRepoSummary(viewerHasStarred: Boolean = false): RepoSummary = RepoSummary(
        id = nodeId.ifBlank { "rest_$id" },
        name = name,
        owner = owner?.login ?: fullName.substringBefore('/'),
        fullName = fullName,
        description = description,
        language = language,
        languageColorHex = null,
        stargazersCount = stargazersCount,
        forksCount = forksCount,
        openIssuesCount = openIssuesCount,
        isPrivate = private,
        isFork = fork,
        defaultBranch = defaultBranch.ifBlank { "main" },
        updatedAt = updatedAt,
        ownerAvatarUrl = owner?.avatarUrl.orEmpty(),
        viewerHasStarred = viewerHasStarred
    )
}

@Serializable
private data class RestRepoSearchResponse(
    val items: List<RestRepoDto> = emptyList()
)

@Serializable
private data class RestEventActorDto(
    val login: String = "ghost",
    @SerialName("avatar_url") val avatarUrl: String = ""
)

@Serializable
private data class RestEventRepoDto(
    val name: String = ""
)

@Serializable
private data class RestEventDto(
    val id: String,
    val type: String = "",
    val actor: RestEventActorDto = RestEventActorDto(),
    val repo: RestEventRepoDto = RestEventRepoDto(),
    val payload: JsonObject? = null,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
private data class RestUserProfileDto(
    val login: String,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String = "",
    val bio: String? = null,
    val company: String? = null,
    val location: String? = null,
    val blog: String? = null,
    @SerialName("twitter_username") val twitterUsername: String? = null,
    val email: String? = null,
    val followers: Int = 0,
    val following: Int = 0,
    @SerialName("public_repos") val publicRepos: Int = 0,
    @SerialName("public_gists") val publicGists: Int = 0,
    val type: String = "User",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class RestOrgDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String = "",
    val description: String? = null
)

@Serializable
private data class RestUserFollowItemDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class RestUserSearchResponse(
    val items: List<RestUserFollowItemDto> = emptyList()
)

@Serializable
private data class RestSearchLabelDto(
    val name: String,
    val color: String = "6750A4",
    val description: String? = null
)

@Serializable
private data class RestSearchIssueItemDto(
    @SerialName("node_id") val nodeId: String = "",
    val id: Long = 0L,
    val number: Int,
    val title: String,
    val state: String = "open",
    val draft: Boolean = false,
    @SerialName("repository_url") val repositoryUrl: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    val comments: Int = 0,
    val user: RestEventActorDto? = null,
    val labels: List<RestSearchLabelDto> = emptyList(),
    @SerialName("pull_request") val pullRequest: JsonObject? = null
)

@Serializable
private data class RestIssueSearchResponse(
    val items: List<RestSearchIssueItemDto> = emptyList()
)

@Serializable
private data class RestCodeTextMatchDto(
    val fragment: String = ""
)

@Serializable
private data class RestCodeRepoDto(
    @SerialName("full_name") val fullName: String = "",
    val name: String = "",
    val owner: RestEventActorDto? = null
)

@Serializable
private data class RestCodeItemDto(
    val name: String,
    val path: String,
    val sha: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val repository: RestCodeRepoDto = RestCodeRepoDto(),
    @SerialName("text_matches") val textMatches: List<RestCodeTextMatchDto> = emptyList()
)

@Serializable
private data class RestCodeSearchResponse(
    val items: List<RestCodeItemDto> = emptyList()
)

class GitHubRestService(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://api.github.com"
) {
    suspend fun fetchNotifications(
        all: Boolean = false,
        participating: Boolean = false,
        page: Int = 1,
        perPage: Int = 40
    ): List<NotificationThread> {
        val response = httpClient.get("$baseUrl/notifications") {
            header(HttpHeaders.CacheControl, "no-cache")
            header(HttpHeaders.Pragma, "no-cache")
            parameter("all", all)
            parameter("participating", participating)
            parameter("page", page)
            parameter("per_page", perPage)
        }
        if (!response.status.isSuccess()) {
            throw GitHubApiException(response.status.value, "Failed to load notifications (${response.status.value})")
        }
        return response.body<List<NotificationDto>>().map { dto ->
            NotificationThread(
                id = dto.id,
                unread = dto.unread,
                reason = parseReason(dto.reason),
                updatedAt = dto.updatedAt,
                subjectTitle = dto.subject.title,
                subjectType = parseSubjectType(dto.subject.type),
                subjectUrl = dto.subject.url,
                latestCommentUrl = dto.subject.latestCommentUrl,
                repoFullName = dto.repository.fullName,
                repoOwner = dto.repository.owner?.login ?: dto.repository.fullName.substringBefore('/'),
                repoName = dto.repository.name,
                repoOwnerAvatarUrl = dto.repository.owner?.avatarUrl.orEmpty(),
                isPrivate = dto.repository.private
            )
        }
    }

    suspend fun markNotificationRead(threadId: String) {
        val res = httpClient.patch("$baseUrl/notifications/threads/$threadId")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to mark thread read")
        }
    }

    suspend fun markNotificationDone(threadId: String) {
        val res = httpClient.delete("$baseUrl/notifications/threads/$threadId")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to mark thread done")
        }
    }

    suspend fun markAllNotificationsRead() {
        val res = httpClient.put("$baseUrl/notifications") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("read" to true))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to mark all notifications read")
        }
    }

    suspend fun fetchPullFiles(owner: String, repo: String, number: Int): List<PullFileChange> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/pulls/$number/files") {
            parameter("per_page", 100)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load PR files (${res.status.value})")
        }
        return res.body<List<PullFileDto>>().map { dto ->
            PullFileChange(
                filename = dto.filename,
                previousFilename = dto.previousFilename,
                status = parseFileStatus(dto.status),
                additions = dto.additions,
                deletions = dto.deletions,
                patch = dto.patch,
                hunks = DiffParser.parse(dto.patch)
            )
        }
    }

    suspend fun postComment(owner: String, repo: String, number: Int, body: String) {
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/issues/$number/comments") {
            contentType(ContentType.Application.Json)
            setBody(CommentRequest(body))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to post comment (${res.status.value})")
        }
    }

    suspend fun setIssueOrPullState(owner: String, repo: String, number: Int, open: Boolean) {
        val res = httpClient.patch("$baseUrl/repos/$owner/$repo/issues/$number") {
            contentType(ContentType.Application.Json)
            setBody(IssueStateRequest(if (open) "open" else "closed"))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to update state (${res.status.value})")
        }
    }

    suspend fun createIssue(owner: String, repo: String, title: String, body: String): Int {
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/issues") {
            contentType(ContentType.Application.Json)
            setBody(CreateIssueRequest(title, body))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to create issue (${res.status.value})")
        }
        return res.body<CreateIssueResponse>().number
    }

    suspend fun submitPullReview(
        owner: String,
        repo: String,
        number: Int,
        event: ReviewEvent,
        body: String
    ) {
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/pulls/$number/reviews") {
            contentType(ContentType.Application.Json)
            setBody(SubmitReviewRequest(event = event.apiValue, body = body))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to submit review (${res.status.value})")
        }
    }

    suspend fun mergePullRequest(
        owner: String,
        repo: String,
        number: Int,
        method: MergeMethod,
        commitTitle: String? = null
    ) {
        val res = httpClient.put("$baseUrl/repos/$owner/$repo/pulls/$number/merge") {
            contentType(ContentType.Application.Json)
            setBody(MergePullRequest(mergeMethod = method.apiValue, commitTitle = commitTitle))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Merge failed (${res.status.value})")
        }
    }

    suspend fun fetchBranches(owner: String, repo: String): List<BranchRef> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/branches") {
            parameter("per_page", 100)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load branches (${res.status.value})")
        }
        return res.body<List<BranchDto>>().map { BranchRef(name = it.name, sha = it.commit.sha) }
    }

    suspend fun fetchDirectoryContents(
        owner: String,
        repo: String,
        path: String = "",
        ref: String? = null
    ): List<TreeEntry> {
        val cleanPath = path.trimStart('/')
        val url = if (cleanPath.isEmpty()) {
            "$baseUrl/repos/$owner/$repo/contents"
        } else {
            "$baseUrl/repos/$owner/$repo/contents/$cleanPath"
        }
        val res = httpClient.get(url) {
            if (!ref.isNullOrBlank()) parameter("ref", ref)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to list directory (${res.status.value})")
        }
        return res.body<List<ContentItemDto>>()
            .map { item ->
                TreeEntry(
                    path = item.path,
                    name = item.name,
                    type = if (item.type == "dir") TreeEntryType.TREE else TreeEntryType.BLOB,
                    sha = item.sha,
                    size = item.size
                )
            }
            .sortedWith(compareBy<TreeEntry> { it.type != TreeEntryType.TREE }.thenBy { it.name.lowercase() })
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun fetchFileContent(
        owner: String,
        repo: String,
        path: String,
        ref: String? = null
    ): FileContent {
        val cleanPath = path.trimStart('/')
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/contents/$cleanPath") {
            if (!ref.isNullOrBlank()) parameter("ref", ref)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to fetch file (${res.status.value})")
        }
        val dto = res.body<ContentItemDto>()
        val raw = dto.content
        val decoded = if (raw != null && dto.encoding == "base64") {
            val bytes = Base64.Mime.decode(raw)
            val isLikelyBinary = bytes.take(512).any { it == 0.toByte() }
            if (isLikelyBinary) {
                return FileContent(
                    path = dto.path,
                    name = dto.name,
                    size = dto.size,
                    content = "",
                    isBinary = true,
                    htmlUrl = dto.htmlUrl
                )
            }
            bytes.decodeToString()
        } else {
            raw.orEmpty()
        }
        return FileContent(
            path = dto.path,
            name = dto.name,
            size = dto.size,
            content = decoded,
            isBinary = false,
            htmlUrl = dto.htmlUrl
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun fetchReadme(owner: String, repo: String, ref: String? = null): String? {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/readme") {
            if (!ref.isNullOrBlank()) parameter("ref", ref)
        }
        if (!res.status.isSuccess()) return null
        val dto = res.body<ContentItemDto>()
        return if (dto.content != null && dto.encoding == "base64") {
            runCatching { Base64.Mime.decode(dto.content).decodeToString() }.getOrNull()
        } else {
            dto.content
        }
    }

    suspend fun setStarred(owner: String, repo: String, starred: Boolean) {
        val url = "$baseUrl/user/starred/$owner/$repo"
        val res = if (starred) {
            httpClient.put(url) {
                header(HttpHeaders.ContentLength, "0")
            }
        } else {
            httpClient.delete(url)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to update star (${res.status.value})")
        }
    }

    suspend fun fetchViewerRepositoriesRest(perPage: Int = 40): List<RepoSummary> {
        val res = httpClient.get("$baseUrl/user/repos") {
            parameter("sort", "updated")
            parameter("direction", "desc")
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load repositories (${res.status.value})")
        }
        return res.body<List<RestRepoDto>>().map { it.toRepoSummary() }
    }

    suspend fun fetchStarredRepositoriesRest(perPage: Int = 40): List<RepoSummary> {
        val res = httpClient.get("$baseUrl/user/starred") {
            parameter("sort", "created")
            parameter("direction", "desc")
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load starred repositories (${res.status.value})")
        }
        return res.body<List<RestRepoDto>>().map { it.toRepoSummary(viewerHasStarred = true) }
    }

    suspend fun searchRepositoriesRest(query: String, perPage: Int = 30): List<RepoSummary> {
        val res = httpClient.get("$baseUrl/search/repositories") {
            parameter("q", query)
            parameter("sort", "stars")
            parameter("order", "desc")
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Repository search failed (${res.status.value})")
        }
        return res.body<RestRepoSearchResponse>().items.map { it.toRepoSummary() }
    }

    suspend fun fetchReceivedEvents(login: String, page: Int = 1, perPage: Int = 30): List<ActivityEvent> {
        val res = httpClient.get("$baseUrl/users/$login/received_events") {
            parameter("page", page)
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load activity feed (${res.status.value})")
        }
        return res.body<List<RestEventDto>>().map(::parseActivityEvent)
    }

    suspend fun fetchUserEvents(login: String, page: Int = 1, perPage: Int = 30): List<ActivityEvent> {
        val res = httpClient.get("$baseUrl/users/$login/events") {
            parameter("page", page)
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load user events (${res.status.value})")
        }
        return res.body<List<RestEventDto>>().map(::parseActivityEvent)
    }

    suspend fun fetchUserProfileRest(login: String): UserProfile {
        val res = httpClient.get("$baseUrl/users/$login")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load user @$login (${res.status.value})")
        }
        val dto = res.body<RestUserProfileDto>()
        val isOrg = dto.type.equals("Organization", ignoreCase = true)
        val isFollowing = if (!isOrg) checkViewerFollowing(dto.login) else false
        return UserProfile(
            login = dto.login,
            name = dto.name,
            avatarUrl = dto.avatarUrl,
            bio = dto.bio,
            company = dto.company,
            location = dto.location,
            blogUrl = dto.blog?.takeIf { it.isNotBlank() },
            twitterUsername = dto.twitterUsername?.takeIf { it.isNotBlank() },
            email = dto.email?.takeIf { it.isNotBlank() },
            followersCount = dto.followers,
            followingCount = dto.following,
            publicReposCount = dto.publicRepos,
            publicGistsCount = dto.publicGists,
            isOrganization = isOrg,
            viewerIsFollowing = isFollowing,
            createdAt = dto.createdAt,
            htmlUrl = dto.htmlUrl
        )
    }

    suspend fun checkViewerFollowing(login: String): Boolean {
        val res = httpClient.get("$baseUrl/user/following/$login")
        return res.status.value == 204
    }

    suspend fun setFollowingUser(login: String, follow: Boolean) {
        val url = "$baseUrl/user/following/$login"
        val res = if (follow) {
            httpClient.put(url) {
                header(HttpHeaders.ContentLength, "0")
            }
        } else {
            httpClient.delete(url)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to update follow state (${res.status.value})")
        }
    }

    suspend fun fetchUserRepositories(login: String, isOrganization: Boolean = false, perPage: Int = 40): List<RepoSummary> {
        val endpoint = if (isOrganization) {
            "$baseUrl/orgs/$login/repos"
        } else {
            "$baseUrl/users/$login/repos"
        }
        val res = httpClient.get(endpoint) {
            parameter("sort", "updated")
            parameter("direction", "desc")
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load repositories for @$login (${res.status.value})")
        }
        return res.body<List<RestRepoDto>>().map { it.toRepoSummary() }
    }

    suspend fun fetchUserStarredRepositories(login: String, perPage: Int = 30): List<RepoSummary> {
        val res = httpClient.get("$baseUrl/users/$login/starred") {
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load starred repos for @$login (${res.status.value})")
        }
        return res.body<List<RestRepoDto>>().map { it.toRepoSummary() }
    }

    suspend fun fetchUserOrganizations(login: String): List<OrganizationSummary> {
        val res = httpClient.get("$baseUrl/users/$login/orgs") {
            parameter("per_page", 30)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load organizations (${res.status.value})")
        }
        return res.body<List<RestOrgDto>>().map {
            OrganizationSummary(login = it.login, avatarUrl = it.avatarUrl, description = it.description)
        }
    }

    suspend fun fetchUserFollowers(login: String, perPage: Int = 40): List<UserSummary> {
        val res = httpClient.get("$baseUrl/users/$login/followers") {
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load followers (${res.status.value})")
        }
        return res.body<List<RestUserFollowItemDto>>().map {
            UserSummary(login = it.login, avatarUrl = it.avatarUrl, htmlUrl = it.htmlUrl)
        }
    }

    suspend fun fetchUserFollowing(login: String, perPage: Int = 40): List<UserSummary> {
        val res = httpClient.get("$baseUrl/users/$login/following") {
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load following (${res.status.value})")
        }
        return res.body<List<RestUserFollowItemDto>>().map {
            UserSummary(login = it.login, avatarUrl = it.avatarUrl, htmlUrl = it.htmlUrl)
        }
    }

    suspend fun searchIssuesAndPullsRest(query: String, perPage: Int = 30): List<IssueOrPullSummary> {
        val res = httpClient.get("$baseUrl/search/issues") {
            parameter("q", query)
            parameter("sort", "updated")
            parameter("order", "desc")
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Issue search failed (${res.status.value})")
        }
        return res.body<RestIssueSearchResponse>().items.map { item ->
            val repoParts = item.repositoryUrl.removePrefix("$baseUrl/repos/").split('/')
            val repoOwner = repoParts.getOrNull(0).orEmpty()
            val repoName = repoParts.getOrNull(1).orEmpty()
            val repoFull = if (repoOwner.isNotBlank() && repoName.isNotBlank()) "$repoOwner/$repoName" else ""
            IssueOrPullSummary(
                id = item.nodeId.ifBlank { "issue_${item.id}_${item.number}" },
                number = item.number,
                title = item.title,
                kind = if (item.pullRequest != null) IssueOrPullKind.PULL_REQUEST else IssueOrPullKind.ISSUE,
                state = if (item.state.equals("closed", ignoreCase = true)) IssueOrPullState.CLOSED else IssueOrPullState.OPEN,
                isDraft = item.draft,
                repoFullName = repoFull,
                repoOwner = repoOwner,
                repoName = repoName,
                author = UserSummary(
                    login = item.user?.login ?: "ghost",
                    avatarUrl = item.user?.avatarUrl.orEmpty()
                ),
                createdAt = item.createdAt,
                updatedAt = item.updatedAt,
                commentCount = item.comments,
                labels = item.labels.map { LabelTag(it.name, it.color, it.description) }
            )
        }
    }

    suspend fun searchCodeRest(query: String, perPage: Int = 30): List<CodeSearchHit> {
        val res = httpClient.get("$baseUrl/search/code") {
            header(HttpHeaders.Accept, "application/vnd.github.text-match+json")
            parameter("q", query)
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Code search failed (${res.status.value})")
        }
        return res.body<RestCodeSearchResponse>().items.map { item ->
            val full = item.repository.fullName
            val owner = item.repository.owner?.login ?: full.substringBefore('/', "")
            val name = item.repository.name.ifBlank { full.substringAfter('/', "") }
            CodeSearchHit(
                name = item.name,
                path = item.path,
                sha = item.sha,
                repoFullName = full,
                repoOwner = owner,
                repoName = name,
                htmlUrl = item.htmlUrl,
                fragments = item.textMatches.mapNotNull { it.fragment.takeIf(String::isNotBlank) }
            )
        }
    }

    suspend fun searchUsersRest(query: String, perPage: Int = 30): List<UserSummary> {
        val res = httpClient.get("$baseUrl/search/users") {
            parameter("q", query)
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "User search failed (${res.status.value})")
        }
        return res.body<RestUserSearchResponse>().items.map {
            UserSummary(login = it.login, avatarUrl = it.avatarUrl, htmlUrl = it.htmlUrl)
        }
    }

    suspend fun fetchTrendingRepositories(language: String? = null, perPage: Int = 25): List<RepoSummary> {
        val langPart = language?.trim()?.takeIf { it.isNotEmpty() }?.let { " language:$it" } ?: ""
        return searchRepositoriesRest(query = "stars:>500$langPart", perPage = perPage)
    }

    private fun parseActivityEvent(dto: RestEventDto): ActivityEvent {
        val repoFull = dto.repo.name
        val repoOwner = repoFull.substringBefore('/', "")
        val repoName = repoFull.substringAfter('/', repoFull)
        val payload = dto.payload

        fun pStr(obj: JsonObject?, key: String): String? =
            obj?.get(key)?.let { if (it is kotlinx.serialization.json.JsonNull) null else it.jsonPrimitive.contentOrNull }

        fun pInt(obj: JsonObject?, key: String): Int? =
            obj?.get(key)?.let { if (it is kotlinx.serialization.json.JsonNull) null else it.jsonPrimitive.intOrNull }

        fun pObj(obj: JsonObject?, key: String): JsonObject? = obj?.get(key) as? JsonObject
        fun pArr(obj: JsonObject?, key: String): JsonArray? = obj?.get(key) as? JsonArray

        var detailTitle: String? = null
        var issueNum: Int? = null
        var commitSha: String? = null
        var branchName: String? = null
        var releaseTag: String? = null

        val kind: ActivityEventKind
        val summary: String

        when (dto.type) {
            "PushEvent" -> {
                kind = ActivityEventKind.PUSH
                val branch = pStr(payload, "ref")?.removePrefix("refs/heads/") ?: "main"
                branchName = branch
                val commits = pArr(payload, "commits")
                val size = pInt(payload, "size") ?: commits?.size ?: 1
                val firstCommit = commits?.firstOrNull() as? JsonObject
                commitSha = pStr(firstCommit, "sha") ?: pStr(payload, "head")
                detailTitle = pStr(firstCommit, "message")?.lineSequence()?.firstOrNull()
                summary = "pushed $size commit${if (size == 1) "" else "s"} to $branch"
            }
            "PullRequestEvent" -> {
                kind = ActivityEventKind.PULL_REQUEST
                val action = pStr(payload, "action") ?: "updated"
                val pr = pObj(payload, "pull_request")
                issueNum = pInt(pr, "number") ?: pInt(payload, "number")
                detailTitle = pStr(pr, "title")
                val merged = pStr(pr, "merged_at") != null
                val verb = if (action == "closed" && merged) "merged" else action
                summary = "$verb pull request${issueNum?.let { " #$it" } ?: ""}"
            }
            "IssuesEvent" -> {
                kind = ActivityEventKind.ISSUES
                val action = pStr(payload, "action") ?: "updated"
                val issue = pObj(payload, "issue")
                issueNum = pInt(issue, "number")
                detailTitle = pStr(issue, "title")
                summary = "$action issue${issueNum?.let { " #$it" } ?: ""}"
            }
            "IssueCommentEvent" -> {
                kind = ActivityEventKind.ISSUE_COMMENT
                val issue = pObj(payload, "issue")
                issueNum = pInt(issue, "number")
                detailTitle = pStr(issue, "title")
                summary = "commented on${issueNum?.let { " #$it" } ?: " thread"}"
            }
            "PullRequestReviewEvent", "PullRequestReviewCommentEvent" -> {
                kind = ActivityEventKind.PULL_REQUEST_REVIEW
                val pr = pObj(payload, "pull_request")
                issueNum = pInt(pr, "number")
                detailTitle = pStr(pr, "title")
                val state = pStr(pObj(payload, "review"), "state")
                summary = when (state?.lowercase()) {
                    "approved" -> "approved pull request${issueNum?.let { " #$it" } ?: ""}"
                    "changes_requested" -> "requested changes on${issueNum?.let { " #$it" } ?: ""}"
                    else -> "reviewed pull request${issueNum?.let { " #$it" } ?: ""}"
                }
            }
            "WatchEvent" -> {
                kind = ActivityEventKind.WATCH
                summary = "starred repository"
            }
            "ForkEvent" -> {
                kind = ActivityEventKind.FORK
                val forkee = pObj(payload, "forkee")
                detailTitle = pStr(forkee, "full_name")
                summary = "forked repository"
            }
            "CreateEvent" -> {
                kind = ActivityEventKind.CREATE
                val refType = pStr(payload, "ref_type") ?: "ref"
                val ref = pStr(payload, "ref")
                branchName = if (refType == "branch") ref else null
                summary = if (ref != null) "created $refType $ref" else "created repository"
            }
            "DeleteEvent" -> {
                kind = ActivityEventKind.DELETE
                val refType = pStr(payload, "ref_type") ?: "ref"
                val ref = pStr(payload, "ref")
                summary = "deleted $refType ${ref.orEmpty()}".trim()
            }
            "ReleaseEvent" -> {
                kind = ActivityEventKind.RELEASE
                val release = pObj(payload, "release")
                releaseTag = pStr(release, "tag_name")
                detailTitle = pStr(release, "name") ?: releaseTag
                val action = pStr(payload, "action") ?: "published"
                summary = "$action release ${releaseTag.orEmpty()}".trim()
            }
            "PublicEvent" -> {
                kind = ActivityEventKind.PUBLIC
                summary = "made repository public"
            }
            "MemberEvent" -> {
                kind = ActivityEventKind.MEMBER
                val memberLogin = pStr(pObj(payload, "member"), "login")
                val action = pStr(payload, "action") ?: "added"
                summary = "$action ${memberLogin?.let { "@$it" } ?: "collaborator"}"
            }
            else -> {
                kind = ActivityEventKind.OTHER
                summary = dto.type.removeSuffix("Event").ifBlank { "activity" }
            }
        }

        return ActivityEvent(
            id = dto.id,
            kind = kind,
            actor = UserSummary(
                login = dto.actor.login,
                avatarUrl = dto.actor.avatarUrl,
                htmlUrl = "https://github.com/${dto.actor.login}"
            ),
            repoFullName = repoFull,
            repoOwner = repoOwner,
            repoName = repoName,
            summaryText = summary,
            detailTitle = detailTitle,
            targetIssueNumber = issueNum,
            commitSha = commitSha,
            branchName = branchName,
            releaseTag = releaseTag,
            createdAt = dto.createdAt
        )
    }

    private fun parseReason(raw: String): NotificationReason = when (raw.lowercase()) {
        "assign" -> NotificationReason.ASSIGN
        "author" -> NotificationReason.AUTHOR
        "comment" -> NotificationReason.COMMENT
        "ci_activity" -> NotificationReason.CI_ACTIVITY
        "invitation" -> NotificationReason.INVITATION
        "manual" -> NotificationReason.MANUAL
        "mention" -> NotificationReason.MENTION
        "review_requested" -> NotificationReason.REVIEW_REQUESTED
        "security_alert" -> NotificationReason.SECURITY_ALERT
        "state_change" -> NotificationReason.STATE_CHANGE
        "subscribed" -> NotificationReason.SUBSCRIBED
        "team_mention" -> NotificationReason.TEAM_MENTION
        else -> NotificationReason.OTHER
    }

    private fun parseSubjectType(raw: String): NotificationSubjectType = when (raw) {
        "Issue" -> NotificationSubjectType.ISSUE
        "PullRequest" -> NotificationSubjectType.PULL_REQUEST
        "Commit" -> NotificationSubjectType.COMMIT
        "Release" -> NotificationSubjectType.RELEASE
        "Discussion" -> NotificationSubjectType.DISCUSSION
        "CheckSuite" -> NotificationSubjectType.CHECK_SUITE
        else -> NotificationSubjectType.UNKNOWN
    }

    private fun parseFileStatus(raw: String): FileChangeStatus = when (raw.lowercase()) {
        "added" -> FileChangeStatus.ADDED
        "removed" -> FileChangeStatus.REMOVED
        "renamed" -> FileChangeStatus.RENAMED
        "copied" -> FileChangeStatus.COPIED
        "changed" -> FileChangeStatus.CHANGED
        else -> FileChangeStatus.MODIFIED
    }
}
