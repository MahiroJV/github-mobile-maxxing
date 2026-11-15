package dev.octoexpress.network

import dev.octoexpress.model.ActivityEvent
import dev.octoexpress.model.ActivityEventKind
import dev.octoexpress.model.BranchCompareResult
import dev.octoexpress.model.BranchRef
import dev.octoexpress.model.CheckRunSummary
import dev.octoexpress.model.CodeSearchHit
import dev.octoexpress.model.CommitDetail
import dev.octoexpress.model.CommitSummary
import dev.octoexpress.model.DependabotAlertSummary
import dev.octoexpress.model.FileChangeStatus
import dev.octoexpress.model.FileContent
import dev.octoexpress.model.IssueOrPullKind
import dev.octoexpress.model.IssueOrPullState
import dev.octoexpress.model.IssueOrPullSummary
import dev.octoexpress.model.LabelTag
import dev.octoexpress.model.MergeMethod
import dev.octoexpress.model.MilestoneSummary
import dev.octoexpress.model.NotificationReason
import dev.octoexpress.model.NotificationSubjectType
import dev.octoexpress.model.NotificationThread
import dev.octoexpress.model.OrganizationSummary
import dev.octoexpress.model.PullFileChange
import dev.octoexpress.model.ReleaseAsset
import dev.octoexpress.model.ReleaseSummary
import dev.octoexpress.model.RepoSummary
import dev.octoexpress.model.ReviewEvent
import dev.octoexpress.model.SecurityAdvisorySummary
import dev.octoexpress.model.TagRef
import dev.octoexpress.model.TreeEntry
import dev.octoexpress.model.TreeEntryType
import dev.octoexpress.model.UserProfile
import dev.octoexpress.model.UserSummary
import dev.octoexpress.model.WorkflowJobStep
import dev.octoexpress.model.WorkflowJobSummary
import dev.octoexpress.model.WorkflowRunSummary
import dev.octoexpress.model.WorkflowSummary
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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
private data class RestCommitAuthorMetaDto(
    val name: String = "",
    val email: String = "",
    val date: String = ""
)

@Serializable
private data class RestCommitVerificationDto(
    val verified: Boolean = false,
    val reason: String? = null
)

@Serializable
private data class RestCommitMetaDto(
    val message: String = "",
    val author: RestCommitAuthorMetaDto? = null,
    val committer: RestCommitAuthorMetaDto? = null,
    val verification: RestCommitVerificationDto? = null
)

@Serializable
private data class RestCommitUserDto(
    val login: String? = null,
    @SerialName("avatar_url") val avatarUrl: String = ""
)

@Serializable
private data class RestCommitParentDto(
    val sha: String = ""
)

@Serializable
private data class RestCommitStatsDto(
    val additions: Int = 0,
    val deletions: Int = 0,
    val total: Int = 0
)

@Serializable
private data class RestCommitDto(
    val sha: String = "",
    val commit: RestCommitMetaDto = RestCommitMetaDto(),
    val author: RestCommitUserDto? = null,
    val committer: RestCommitUserDto? = null,
    val parents: List<RestCommitParentDto> = emptyList(),
    val stats: RestCommitStatsDto? = null,
    val files: List<PullFileDto> = emptyList(),
    @SerialName("html_url") val htmlUrl: String = ""
) {
    fun toCommitSummary(): CommitSummary {
        val normalized = commit.message.trimEnd()
        val splitIdx = normalized.indexOf('\n')
        val headline = if (splitIdx >= 0) normalized.substring(0, splitIdx).trim() else normalized
        val body = if (splitIdx >= 0) normalized.substring(splitIdx + 1).trim() else ""
        val nonBotCommitter = committer?.takeIf { !it.login.equals("web-flow", ignoreCase = true) }
        val noreplyLogin = commit.author?.email?.let(::extractGitHubLoginFromNoreplyEmail)
            ?: commit.committer?.email?.let(::extractGitHubLoginFromNoreplyEmail)
        val resolvedLogin = author?.login ?: noreplyLogin ?: nonBotCommitter?.login
        val resolvedAvatarUrl = author?.avatarUrl?.takeIf { it.isNotBlank() }
            ?: nonBotCommitter?.avatarUrl?.takeIf { it.isNotBlank() }
            ?: resolvedLogin?.let { "https://avatars.githubusercontent.com/$it?size=96" }
            ?: ""
        return CommitSummary(
            sha = sha,
            shortSha = sha.take(7),
            headline = headline.ifBlank { "(no commit message)" },
            body = body,
            authorName = commit.author?.name?.ifBlank { resolvedLogin ?: "unknown" }
                ?: resolvedLogin ?: "unknown",
            authorLogin = resolvedLogin,
            authorAvatarUrl = resolvedAvatarUrl,
            committedAt = commit.committer?.date?.ifBlank { commit.author?.date.orEmpty() }
                ?: commit.author?.date.orEmpty(),
            parentShas = parents.mapNotNull { it.sha.takeIf(String::isNotBlank) },
            verificationReason = commit.verification?.reason,
            isVerified = commit.verification?.verified == true
        )
    }
}

private fun extractGitHubLoginFromNoreplyEmail(email: String): String? {
    val trimmed = email.trim()
    val suffix = "@users.noreply.github.com"
    if (!trimmed.endsWith(suffix, ignoreCase = true)) return null
    val localPart = trimmed.substringBefore('@')
    val login = if ('+' in localPart) localPart.substringAfter('+') else localPart
    return login.takeIf { it.isNotBlank() }
}

@Serializable
private data class RestTagCommitDto(
    val sha: String = ""
)

@Serializable
private data class RestTagDto(
    val name: String = "",
    val commit: RestTagCommitDto = RestTagCommitDto()
)

@Serializable
private data class RestCompareDto(
    val status: String = "identical",
    @SerialName("ahead_by") val aheadBy: Int = 0,
    @SerialName("behind_by") val behindBy: Int = 0,
    @SerialName("total_commits") val totalCommits: Int = 0,
    val commits: List<RestCommitDto> = emptyList(),
    val files: List<PullFileDto> = emptyList(),
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class RestWorkflowDto(
    val id: Long,
    val name: String = "",
    val path: String = "",
    val state: String = "active",
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class RestWorkflowsResponse(
    val workflows: List<RestWorkflowDto> = emptyList()
)

@Serializable
private data class RestWorkflowRunDto(
    val id: Long,
    @SerialName("run_number") val runNumber: Int = 0,
    val name: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    @SerialName("head_branch") val headBranch: String? = null,
    @SerialName("head_sha") val headSha: String = "",
    val event: String = "",
    val status: String = "queued",
    val conclusion: String? = null,
    val actor: RestCommitUserDto? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class RestWorkflowRunsResponse(
    @SerialName("workflow_runs") val workflowRuns: List<RestWorkflowRunDto> = emptyList()
)

@Serializable
private data class RestWorkflowJobStepDto(
    val name: String = "",
    val number: Int = 0,
    val status: String = "queued",
    val conclusion: String? = null
)

@Serializable
private data class RestWorkflowJobDto(
    val id: Long,
    val name: String = "",
    val status: String = "queued",
    val conclusion: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val steps: List<RestWorkflowJobStepDto> = emptyList(),
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class RestWorkflowJobsResponse(
    val jobs: List<RestWorkflowJobDto> = emptyList()
)

@Serializable
private data class RestCheckAppDto(
    val name: String? = null
)

@Serializable
private data class RestCheckOutputDto(
    val title: String? = null,
    val summary: String? = null
)

@Serializable
private data class RestCheckRunDto(
    val id: Long,
    val name: String = "",
    val status: String = "queued",
    val conclusion: String? = null,
    val app: RestCheckAppDto? = null,
    val output: RestCheckOutputDto? = null,
    @SerialName("details_url") val detailsUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
private data class RestCheckRunsResponse(
    @SerialName("check_runs") val checkRuns: List<RestCheckRunDto> = emptyList()
)

@Serializable
private data class RestReleaseAssetDto(
    val id: Long,
    val name: String = "",
    val size: Long = 0L,
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    @SerialName("content_type") val contentType: String = ""
)

@Serializable
private data class RestReleaseDto(
    val id: Long,
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val author: RestCommitUserDto? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    val assets: List<RestReleaseAssetDto> = emptyList(),
    @SerialName("tarball_url") val tarballUrl: String? = null,
    @SerialName("zipball_url") val zipballUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String = ""
) {
    fun toReleaseSummary(): ReleaseSummary = ReleaseSummary(
        id = id,
        tagName = tagName,
        name = name?.takeIf { it.isNotBlank() } ?: tagName,
        body = body.orEmpty(),
        isDraft = draft,
        isPrerelease = prerelease,
        authorLogin = author?.login.orEmpty(),
        authorAvatarUrl = author?.avatarUrl.orEmpty(),
        publishedAt = publishedAt.orEmpty(),
        createdAt = createdAt,
        assets = assets.map {
            ReleaseAsset(
                id = it.id,
                name = it.name,
                sizeBytes = it.size,
                downloadCount = it.downloadCount,
                downloadUrl = it.browserDownloadUrl,
                contentType = it.contentType
            )
        },
        tarballUrl = tarballUrl.orEmpty(),
        zipballUrl = zipballUrl.orEmpty(),
        htmlUrl = htmlUrl
    )
}

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

@Serializable
private data class CreateRepoRequest(
    val name: String,
    val description: String = "",
    val private: Boolean = false,
    @SerialName("auto_init") val autoInit: Boolean = true
)

@Serializable
private data class SubscriptionRequest(
    val subscribed: Boolean,
    val ignored: Boolean = false
)

@Serializable
private data class SubscriptionResponse(
    val subscribed: Boolean = false,
    val ignored: Boolean = false
)

@Serializable
private data class CreateLabelRequest(
    val name: String,
    val color: String,
    val description: String = ""
)

@Serializable
private data class RestMilestoneDto(
    val number: Int,
    val title: String,
    val description: String? = null,
    val state: String = "open",
    @SerialName("due_on") val dueOn: String? = null,
    @SerialName("open_issues") val openIssues: Int = 0,
    @SerialName("closed_issues") val closedIssues: Int = 0
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
        return res.body<List<PullFileDto>>().map { it.toPullFileChange() }
    }

    private fun PullFileDto.toPullFileChange(): PullFileChange = PullFileChange(
        filename = filename,
        previousFilename = previousFilename,
        status = parseFileStatus(status),
        additions = additions,
        deletions = deletions,
        patch = patch,
        hunks = DiffParser.parse(patch)
    )

    suspend fun fetchRepoCommits(
        owner: String,
        repo: String,
        ref: String? = null,
        path: String? = null,
        perPage: Int = 30
    ): List<CommitSummary> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/commits") {
            ref?.takeIf { it.isNotBlank() }?.let { parameter("sha", it) }
            path?.takeIf { it.isNotBlank() }?.let { parameter("path", it) }
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load commits (${res.status.value})")
        }
        return res.body<List<RestCommitDto>>().map { it.toCommitSummary() }
    }

    suspend fun fetchCommitDetail(owner: String, repo: String, sha: String): CommitDetail {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/commits/$sha")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load commit $sha (${res.status.value})")
        }
        val dto = res.body<RestCommitDto>()
        val files = dto.files.map { it.toPullFileChange() }
        return CommitDetail(
            summary = dto.toCommitSummary(),
            additions = dto.stats?.additions ?: files.sumOf { it.additions },
            deletions = dto.stats?.deletions ?: files.sumOf { it.deletions },
            changedFilesCount = files.size,
            files = files,
            htmlUrl = dto.htmlUrl
        )
    }

    suspend fun fetchRepoTags(owner: String, repo: String, perPage: Int = 30): List<TagRef> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/tags") {
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load tags (${res.status.value})")
        }
        return res.body<List<RestTagDto>>().map { TagRef(name = it.name, commitSha = it.commit.sha) }
    }

    suspend fun compareRefs(
        owner: String,
        repo: String,
        base: String,
        head: String
    ): BranchCompareResult {
        val encodedBase = base.encodeURLPathPart()
        val encodedHead = head.encodeURLPathPart()
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/compare/${encodedBase}...${encodedHead}")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to compare $base...$head (${res.status.value})")
        }
        val dto = res.body<RestCompareDto>()
        return BranchCompareResult(
            baseRef = base,
            headRef = head,
            status = dto.status,
            aheadBy = dto.aheadBy,
            behindBy = dto.behindBy,
            totalCommits = dto.totalCommits,
            commits = dto.commits.map { it.toCommitSummary() },
            files = dto.files.map { it.toPullFileChange() },
            htmlUrl = dto.htmlUrl
        )
    }

    suspend fun fetchWorkflows(owner: String, repo: String): List<WorkflowSummary> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/actions/workflows")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load workflows (${res.status.value})")
        }
        return res.body<RestWorkflowsResponse>().workflows.map {
            WorkflowSummary(id = it.id, name = it.name, path = it.path, state = it.state, htmlUrl = it.htmlUrl)
        }
    }

    suspend fun fetchWorkflowRuns(
        owner: String,
        repo: String,
        workflowId: Long? = null,
        branch: String? = null,
        perPage: Int = 25
    ): List<WorkflowRunSummary> {
        val url = if (workflowId != null) {
            "$baseUrl/repos/$owner/$repo/actions/workflows/$workflowId/runs"
        } else {
            "$baseUrl/repos/$owner/$repo/actions/runs"
        }
        val res = httpClient.get(url) {
            branch?.takeIf { it.isNotBlank() }?.let { parameter("branch", it) }
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load workflow runs (${res.status.value})")
        }
        return res.body<RestWorkflowRunsResponse>().workflowRuns.map {
            WorkflowRunSummary(
                id = it.id,
                runNumber = it.runNumber,
                name = it.name.orEmpty().ifBlank { it.displayTitle.orEmpty() },
                displayTitle = it.displayTitle.orEmpty().ifBlank { it.name.orEmpty() },
                headBranch = it.headBranch.orEmpty(),
                headSha = it.headSha,
                event = it.event,
                status = it.status,
                conclusion = it.conclusion,
                actorLogin = it.actor?.login.orEmpty(),
                actorAvatarUrl = it.actor?.avatarUrl.orEmpty(),
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                htmlUrl = it.htmlUrl
            )
        }
    }

    suspend fun fetchWorkflowRunJobs(owner: String, repo: String, runId: Long): List<WorkflowJobSummary> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/actions/runs/$runId/jobs") {
            parameter("per_page", 50)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load jobs for run #$runId (${res.status.value})")
        }
        return res.body<RestWorkflowJobsResponse>().jobs.map { job ->
            WorkflowJobSummary(
                id = job.id,
                name = job.name,
                status = job.status,
                conclusion = job.conclusion,
                startedAt = job.startedAt,
                completedAt = job.completedAt,
                steps = job.steps.map { s ->
                    WorkflowJobStep(name = s.name, number = s.number, status = s.status, conclusion = s.conclusion)
                },
                htmlUrl = job.htmlUrl
            )
        }
    }

    suspend fun fetchJobLogs(owner: String, repo: String, jobId: Long): String {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/actions/jobs/$jobId/logs")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load job logs (${res.status.value})")
        }
        val full = res.bodyAsText()
        // Keep last 40KB if an action log is huge so mobile rendering stays fluid
        return if (full.length > 40_000) "... (truncated to latest 40 KB) ...\n" + full.takeLast(40_000) else full
    }

    suspend fun rerunWorkflow(owner: String, repo: String, runId: Long, failedOnly: Boolean = false) {
        val action = if (failedOnly) "rerun-failed-jobs" else "rerun"
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/actions/runs/$runId/$action")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to re-run workflow (${res.status.value})")
        }
    }

    suspend fun cancelWorkflowRun(owner: String, repo: String, runId: Long) {
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/actions/runs/$runId/cancel")
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to cancel run (${res.status.value})")
        }
    }

    suspend fun dispatchWorkflow(
        owner: String,
        repo: String,
        workflowId: Long,
        ref: String,
        inputs: Map<String, String> = emptyMap()
    ) {
        val payload = buildJsonObject {
            put("ref", ref)
            if (inputs.isNotEmpty()) {
                put(
                    "inputs",
                    buildJsonObject {
                        inputs.forEach { (k, v) -> put(k, v) }
                    }
                )
            }
        }
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/actions/workflows/$workflowId/dispatches") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to dispatch workflow (${res.status.value})")
        }
    }

    suspend fun fetchCheckRunsForRef(owner: String, repo: String, ref: String): List<CheckRunSummary> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/commits/${ref.encodeURLPathPart()}/check-runs") {
            parameter("per_page", 50)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load check runs (${res.status.value})")
        }
        return res.body<RestCheckRunsResponse>().checkRuns.map { cr ->
            CheckRunSummary(
                id = cr.id,
                name = cr.name,
                status = cr.status,
                conclusion = cr.conclusion,
                appName = cr.app?.name,
                title = cr.output?.title,
                summary = cr.output?.summary,
                detailsUrl = cr.detailsUrl?.ifBlank { cr.htmlUrl } ?: cr.htmlUrl
            )
        }
    }

    suspend fun fetchReleases(owner: String, repo: String, perPage: Int = 20): List<ReleaseSummary> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/releases") {
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load releases (${res.status.value})")
        }
        return res.body<List<RestReleaseDto>>().map { it.toReleaseSummary() }
    }

    suspend fun createRelease(
        owner: String,
        repo: String,
        tagName: String,
        name: String,
        body: String = "",
        targetCommitish: String? = null,
        isDraft: Boolean = false,
        isPrerelease: Boolean = false,
        generateReleaseNotes: Boolean = true
    ): ReleaseSummary {
        val payload = buildJsonObject {
            put("tag_name", tagName)
            if (name.isNotBlank()) put("name", name)
            if (body.isNotBlank()) put("body", body)
            targetCommitish?.takeIf { it.isNotBlank() }?.let { put("target_commitish", it) }
            put("draft", isDraft)
            put("prerelease", isPrerelease)
            put("generate_release_notes", generateReleaseNotes)
        }
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/releases") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to create release (${res.status.value})")
        }
        return res.body<RestReleaseDto>().toReleaseSummary()
    }

    suspend fun fetchDependabotAlerts(
        owner: String,
        repo: String
    ): List<DependabotAlertSummary> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/dependabot/alerts") {
            parameter("per_page", 30)
            parameter("state", "open")
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load Dependabot alerts (${res.status.value})")
        }
        val items = res.body<JsonArray>()
        return items.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val number = obj["number"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val adv = obj["security_advisory"] as? JsonObject
            val vuln = obj["security_vulnerability"] as? JsonObject
            val dep = obj["dependency"] as? JsonObject
            val pkg = (dep?.get("package") as? JsonObject) ?: (vuln?.get("package") as? JsonObject)
            val firstPatched = (vuln?.get("first_patched_version") as? JsonObject)
                ?.get("identifier")?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
            DependabotAlertSummary(
                number = number,
                state = obj["state"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: "open",
                severity = vuln?.get("severity")?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    ?: adv?.get("severity")?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                    ?: "medium",
                packageName = pkg?.get("name")?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: "unknown",
                ecosystem = pkg?.get("ecosystem")?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: "",
                summary = adv?.get("summary")?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: "Security alert #$number",
                vulnerableVersionRange = vuln?.get("vulnerable_version_range")?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: "",
                patchedVersion = firstPatched,
                createdAt = obj["created_at"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: "",
                htmlUrl = obj["html_url"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: ""
            )
        }
    }

    suspend fun fetchSecurityAdvisories(
        owner: String,
        repo: String
    ): List<SecurityAdvisorySummary> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/security-advisories") {
            parameter("per_page", 20)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load security advisories (${res.status.value})")
        }
        val items = res.body<JsonArray>()
        return items.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val ghsaId = obj["ghsa_id"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }
                ?: return@mapNotNull null
            SecurityAdvisorySummary(
                ghsaId = ghsaId,
                summary = obj["summary"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: ghsaId,
                severity = obj["severity"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: "medium",
                state = obj["state"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: "published",
                publishedAt = obj["published_at"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: "",
                htmlUrl = obj["html_url"]?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull } ?: ""
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

    suspend fun createRepositoryRest(
        name: String,
        description: String = "",
        isPrivate: Boolean = false,
        autoInit: Boolean = true
    ): RepoSummary {
        val res = httpClient.post("$baseUrl/user/repos") {
            contentType(ContentType.Application.Json)
            setBody(CreateRepoRequest(name = name.trim(), description = description.trim(), private = isPrivate, autoInit = autoInit))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to create repository (${res.status.value})")
        }
        return res.body<RestRepoDto>().toRepoSummary()
    }

    suspend fun forkRepositoryRest(owner: String, repo: String): RepoSummary {
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/forks") {
            header(HttpHeaders.ContentLength, "0")
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to fork repository (${res.status.value})")
        }
        return res.body<RestRepoDto>().toRepoSummary()
    }

    suspend fun fetchRepoSubscription(owner: String, repo: String): Boolean {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/subscription")
        if (res.status.value == 404) return false
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to read watch status (${res.status.value})")
        }
        val dto = res.body<SubscriptionResponse>()
        return dto.subscribed && !dto.ignored
    }

    suspend fun setRepoSubscription(owner: String, repo: String, subscribed: Boolean) {
        val url = "$baseUrl/repos/$owner/$repo/subscription"
        val res = if (subscribed) {
            httpClient.put(url) {
                contentType(ContentType.Application.Json)
                setBody(SubscriptionRequest(subscribed = true, ignored = false))
            }
        } else {
            httpClient.delete(url)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to update watch status (${res.status.value})")
        }
    }

    suspend fun fetchRepoStargazers(owner: String, repo: String, perPage: Int = 40): List<UserSummary> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/stargazers") {
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load stargazers (${res.status.value})")
        }
        return res.body<List<RestUserFollowItemDto>>().map {
            UserSummary(login = it.login, avatarUrl = it.avatarUrl, htmlUrl = it.htmlUrl)
        }
    }

    suspend fun fetchRepoForks(owner: String, repo: String, perPage: Int = 30): List<RepoSummary> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/forks") {
            parameter("sort", "stargazers")
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load forks (${res.status.value})")
        }
        return res.body<List<RestRepoDto>>().map { it.toRepoSummary() }
    }

    suspend fun fetchRepoContributors(owner: String, repo: String, perPage: Int = 30): List<UserSummary> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/contributors") {
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load contributors (${res.status.value})")
        }
        return res.body<List<RestUserFollowItemDto>>().map {
            UserSummary(login = it.login, avatarUrl = it.avatarUrl, htmlUrl = it.htmlUrl)
        }
    }

    suspend fun fetchRepoLabels(owner: String, repo: String, perPage: Int = 100): List<LabelTag> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/labels") {
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load labels (${res.status.value})")
        }
        return res.body<List<RestSearchLabelDto>>().map {
            LabelTag(name = it.name, colorHex = it.color, description = it.description)
        }
    }

    suspend fun createRepoLabel(
        owner: String,
        repo: String,
        name: String,
        colorHex: String,
        description: String = ""
    ): LabelTag {
        val cleanColor = colorHex.removePrefix("#").trim().take(6).ifBlank { "6E40C9" }
        val res = httpClient.post("$baseUrl/repos/$owner/$repo/labels") {
            contentType(ContentType.Application.Json)
            setBody(CreateLabelRequest(name = name.trim(), color = cleanColor, description = description.trim()))
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to create label (${res.status.value})")
        }
        val dto = res.body<RestSearchLabelDto>()
        return LabelTag(name = dto.name, colorHex = dto.color, description = dto.description)
    }

    suspend fun fetchRepoMilestones(owner: String, repo: String, perPage: Int = 50): List<MilestoneSummary> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/milestones") {
            parameter("state", "open")
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load milestones (${res.status.value})")
        }
        return res.body<List<RestMilestoneDto>>().map {
            MilestoneSummary(
                number = it.number,
                title = it.title,
                description = it.description,
                state = it.state,
                dueOn = it.dueOn,
                openIssues = it.openIssues,
                closedIssues = it.closedIssues
            )
        }
    }

    suspend fun fetchRepoAssignees(owner: String, repo: String, perPage: Int = 50): List<UserSummary> {
        val res = httpClient.get("$baseUrl/repos/$owner/$repo/assignees") {
            parameter("per_page", perPage)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to load assignees (${res.status.value})")
        }
        return res.body<List<RestUserFollowItemDto>>().map {
            UserSummary(login = it.login, avatarUrl = it.avatarUrl, htmlUrl = it.htmlUrl)
        }
    }

    suspend fun updateIssueMetadata(
        owner: String,
        repo: String,
        number: Int,
        labels: List<String>? = null,
        assignees: List<String>? = null,
        milestoneNumber: Int? = null,
        clearMilestone: Boolean = false
    ) {
        val payload = buildJsonObject {
            if (labels != null) {
                put("labels", JsonArray(labels.map { JsonPrimitive(it) }))
            }
            if (assignees != null) {
                put("assignees", JsonArray(assignees.map { JsonPrimitive(it) }))
            }
            if (clearMilestone) {
                put("milestone", JsonNull)
            } else if (milestoneNumber != null) {
                put("milestone", milestoneNumber)
            }
        }
        val res = httpClient.patch("$baseUrl/repos/$owner/$repo/issues/$number") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!res.status.isSuccess()) {
            throw GitHubApiException(res.status.value, "Failed to update metadata (${res.status.value})")
        }
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
