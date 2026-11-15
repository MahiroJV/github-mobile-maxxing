package dev.octoexpress.network

import dev.octoexpress.model.BranchRef
import dev.octoexpress.model.FileChangeStatus
import dev.octoexpress.model.FileContent
import dev.octoexpress.model.MergeMethod
import dev.octoexpress.model.NotificationReason
import dev.octoexpress.model.NotificationSubjectType
import dev.octoexpress.model.NotificationThread
import dev.octoexpress.model.PullFileChange
import dev.octoexpress.model.ReviewEvent
import dev.octoexpress.model.TreeEntry
import dev.octoexpress.model.TreeEntryType
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
