package dev.octoexpress.network

import dev.octoexpress.model.CheckRollupSummary
import dev.octoexpress.model.IssueFilterPreset
import dev.octoexpress.model.IssueOrPullDetail
import dev.octoexpress.model.IssueOrPullKind
import dev.octoexpress.model.IssueOrPullState
import dev.octoexpress.model.IssueOrPullSummary
import dev.octoexpress.model.IssueStateFilter
import dev.octoexpress.model.LabelTag
import dev.octoexpress.model.RepoDetail
import dev.octoexpress.model.RepoSummary
import dev.octoexpress.model.TimelineEvent
import dev.octoexpress.model.UserSummary
import dev.octoexpress.model.ViewerProfile
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class GraphQlRequest(
    val query: String,
    val variables: Map<String, String> = emptyMap()
)

@Serializable
private data class GraphQlResponse(
    val data: JsonObject? = null,
    val errors: List<GraphQlError>? = null
)

@Serializable
private data class GraphQlError(
    val message: String
)

class GitHubGraphQlService(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://api.github.com"
) {
    private suspend fun execute(
        query: String,
        variables: Map<String, String> = emptyMap()
    ): JsonObject {
        val response = httpClient.post("$baseUrl/graphql") {
            contentType(ContentType.Application.Json)
            setBody(GraphQlRequest(query = query, variables = variables))
        }
        if (!response.status.isSuccess()) {
            throw GitHubApiException(response.status.value, "GraphQL HTTP ${response.status.value}")
        }
        val body = response.body<GraphQlResponse>()
        if (!body.errors.isNullOrEmpty() && body.data == null) {
            throw GitHubApiException(422, body.errors.joinToString("; ") { it.message })
        }
        return body.data ?: throw GitHubApiException(500, "Empty GraphQL response")
    }

    suspend fun fetchViewerProfile(): ViewerProfile {
        val data = execute(
            """
            query {
              viewer {
                login
                name
                avatarUrl
                bio
                company
                location
                url
                followers { totalCount }
                following { totalCount }
                repositories(ownerAffiliations: OWNER) { totalCount }
              }
            }
            """.trimIndent()
        )
        val v = data["viewer"]!!.jsonObject
        return ViewerProfile(
            login = v.str("login") ?: "",
            name = v.str("name"),
            avatarUrl = v.str("avatarUrl") ?: "",
            bio = v.str("bio"),
            company = v.str("company"),
            location = v.str("location"),
            followersCount = v["followers"]?.jsonObject?.int("totalCount") ?: 0,
            followingCount = v["following"]?.jsonObject?.int("totalCount") ?: 0,
            publicReposCount = v["repositories"]?.jsonObject?.int("totalCount") ?: 0,
            htmlUrl = v.str("url") ?: ""
        )
    }

    suspend fun fetchIssuesAndPulls(
        preset: IssueFilterPreset,
        viewerLogin: String,
        stateFilter: IssueStateFilter = IssueStateFilter.OPEN
    ): List<IssueOrPullSummary> {
        val qualifier = when (preset) {
            IssueFilterPreset.CREATED -> "author:$viewerLogin"
            IssueFilterPreset.ASSIGNED -> "assignee:$viewerLogin"
            IssueFilterPreset.REVIEW_REQUESTED -> "is:pr review-requested:$viewerLogin"
            IssueFilterPreset.MENTIONED -> "mentions:$viewerLogin"
        }
        val stateQualifier = when (stateFilter) {
            IssueStateFilter.OPEN -> "is:open "
            IssueStateFilter.CLOSED -> "is:closed "
            IssueStateFilter.ALL -> ""
        }
        val searchQuery = "$stateQualifier$qualifier sort:updated-desc"

        val data = execute(
            query = """
                query(${'$'}q: String!) {
                  search(query: ${'$'}q, type: ISSUE, first: 35) {
                    nodes {
                      __typename
                      ... on Issue {
                        id
                        number
                        title
                        state
                        createdAt
                        updatedAt
                        author { login avatarUrl url }
                        repository { nameWithOwner name owner { login } }
                        comments { totalCount }
                        labels(first: 6) { nodes { name color description } }
                      }
                      ... on PullRequest {
                        id
                        number
                        title
                        state
                        isDraft
                        createdAt
                        updatedAt
                        reviewDecision
                        author { login avatarUrl url }
                        repository { nameWithOwner name owner { login } }
                        comments { totalCount }
                        labels(first: 6) { nodes { name color description } }
                        commits(last: 1) {
                          nodes {
                            commit {
                              statusCheckRollup { state }
                            }
                          }
                        }
                      }
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf("q" to searchQuery)
        )

        val nodes = data["search"]?.jsonObject?.get("nodes")?.jsonArray ?: return emptyList()
        return nodes.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val typeName = obj.str("__typename") ?: return@mapNotNull null
            if (typeName != "Issue" && typeName != "PullRequest") return@mapNotNull null
            val repoObj = obj["repository"]?.jsonObject ?: return@mapNotNull null
            val authorObj = obj["author"]?.jsonObject
            val stateStr = obj.str("state") ?: "OPEN"
            val statusState = obj["commits"]?.jsonObject
                ?.get("nodes")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("commit")?.jsonObject
                ?.get("statusCheckRollup")?.jsonObject
                ?.str("state")

            IssueOrPullSummary(
                id = obj.str("id") ?: "",
                number = obj.int("number") ?: 0,
                title = obj.str("title") ?: "",
                kind = if (typeName == "PullRequest") IssueOrPullKind.PULL_REQUEST else IssueOrPullKind.ISSUE,
                state = parseState(stateStr),
                isDraft = obj.bool("isDraft") ?: false,
                repoFullName = repoObj.str("nameWithOwner") ?: "",
                repoOwner = repoObj["owner"]?.jsonObject?.str("login") ?: "",
                repoName = repoObj.str("name") ?: "",
                author = UserSummary(
                    login = authorObj?.str("login") ?: "ghost",
                    avatarUrl = authorObj?.str("avatarUrl") ?: "",
                    htmlUrl = authorObj?.str("url") ?: ""
                ),
                createdAt = obj.str("createdAt") ?: "",
                updatedAt = obj.str("updatedAt") ?: "",
                commentCount = obj["comments"]?.jsonObject?.int("totalCount") ?: 0,
                labels = parseLabels(obj["labels"]?.jsonObject?.get("nodes")?.jsonArray),
                checkRollup = statusState?.let { CheckRollupSummary(state = it) },
                reviewDecision = obj.str("reviewDecision")
            )
        }
    }

    suspend fun fetchIssueOrPullDetail(
        owner: String,
        repo: String,
        number: Int
    ): IssueOrPullDetail {
        val query = """
            query {
              repository(owner: "$owner", name: "$repo") {
                issueOrPullRequest(number: $number) {
                  __typename
                  ... on Issue {
                    id
                    number
                    title
                    body
                    state
                    url
                    createdAt
                    updatedAt
                    author { login avatarUrl url }
                    labels(first: 10) { nodes { name color description } }
                    assignees(first: 6) { nodes { login avatarUrl url } }
                    timelineItems(first: 50, itemTypes: [ISSUE_COMMENT, CLOSED_EVENT, REOPENED_EVENT]) {
                      nodes {
                        __typename
                        ... on IssueComment {
                          id
                          createdAt
                          body
                          authorAssociation
                          author { login avatarUrl url }
                        }
                        ... on ClosedEvent {
                          id
                          createdAt
                          actor { login avatarUrl url }
                        }
                        ... on ReopenedEvent {
                          id
                          createdAt
                          actor { login avatarUrl url }
                        }
                      }
                    }
                  }
                  ... on PullRequest {
                    id
                    number
                    title
                    body
                    state
                    isDraft
                    url
                    createdAt
                    updatedAt
                    headRefName
                    baseRefName
                    additions
                    deletions
                    changedFiles
                    mergeable
                    reviewDecision
                    author { login avatarUrl url }
                    labels(first: 10) { nodes { name color description } }
                    assignees(first: 6) { nodes { login avatarUrl url } }
                    commits(last: 1) {
                      nodes {
                        commit {
                          statusCheckRollup { state }
                        }
                      }
                    }
                    timelineItems(first: 60, itemTypes: [ISSUE_COMMENT, PULL_REQUEST_REVIEW, PULL_REQUEST_COMMIT, MERGED_EVENT, CLOSED_EVENT, REOPENED_EVENT]) {
                      nodes {
                        __typename
                        ... on IssueComment {
                          id
                          createdAt
                          body
                          authorAssociation
                          author { login avatarUrl url }
                        }
                        ... on PullRequestReview {
                          id
                          createdAt
                          state
                          body
                          author { login avatarUrl url }
                        }
                        ... on PullRequestCommit {
                          id
                          commit {
                            abbreviatedOid
                            messageHeadline
                            committedDate
                            author { name }
                          }
                        }
                        ... on MergedEvent {
                          id
                          createdAt
                          actor { login avatarUrl url }
                          mergeRefName
                        }
                        ... on ClosedEvent {
                          id
                          createdAt
                          actor { login avatarUrl url }
                        }
                        ... on ReopenedEvent {
                          id
                          createdAt
                          actor { login avatarUrl url }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val data = execute(query)
        val item = data["repository"]?.jsonObject?.get("issueOrPullRequest")?.jsonObject
            ?: throw GitHubApiException(404, "Issue or PR #$number not found in $owner/$repo")

        val typeName = item.str("__typename") ?: "Issue"
        val isPr = typeName == "PullRequest"
        val authorObj = item["author"]?.jsonObject
        val statusState = item["commits"]?.jsonObject
            ?.get("nodes")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("commit")?.jsonObject
            ?.get("statusCheckRollup")?.jsonObject
            ?.str("state")

        val timelineNodes = item["timelineItems"]?.jsonObject?.get("nodes")?.jsonArray ?: JsonArray(emptyList())
        val timeline = timelineNodes.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            when (obj.str("__typename")) {
                "IssueComment" -> TimelineEvent.CommentEvent(
                    id = obj.str("id") ?: "",
                    createdAt = obj.str("createdAt") ?: "",
                    author = parseUser(obj["author"]?.jsonObject),
                    body = obj.str("body") ?: "",
                    authorAssociation = obj.str("authorAssociation")
                )
                "PullRequestReview" -> TimelineEvent.ReviewEventItem(
                    id = obj.str("id") ?: "",
                    createdAt = obj.str("createdAt") ?: "",
                    author = parseUser(obj["author"]?.jsonObject),
                    state = obj.str("state") ?: "COMMENTED",
                    body = obj.str("body") ?: ""
                )
                "PullRequestCommit" -> {
                    val commit = obj["commit"]?.jsonObject
                    TimelineEvent.CommitEvent(
                        id = obj.str("id") ?: "",
                        createdAt = commit?.str("committedDate") ?: "",
                        oidShort = commit?.str("abbreviatedOid") ?: "",
                        messageHeadline = commit?.str("messageHeadline") ?: "",
                        authorName = commit?.get("author")?.jsonObject?.str("name") ?: ""
                    )
                }
                "MergedEvent" -> TimelineEvent.StateChangeEvent(
                    id = obj.str("id") ?: "",
                    createdAt = obj.str("createdAt") ?: "",
                    actor = obj["actor"]?.jsonObject?.let { parseUser(it) },
                    actionLabel = "merged into ${obj.str("mergeRefName") ?: "base"}"
                )
                "ClosedEvent" -> TimelineEvent.StateChangeEvent(
                    id = obj.str("id") ?: "",
                    createdAt = obj.str("createdAt") ?: "",
                    actor = obj["actor"]?.jsonObject?.let { parseUser(it) },
                    actionLabel = "closed this"
                )
                "ReopenedEvent" -> TimelineEvent.StateChangeEvent(
                    id = obj.str("id") ?: "",
                    createdAt = obj.str("createdAt") ?: "",
                    actor = obj["actor"]?.jsonObject?.let { parseUser(it) },
                    actionLabel = "reopened this"
                )
                else -> null
            }
        }

        val assignees = item["assignees"]?.jsonObject?.get("nodes")?.jsonArray
            ?.mapNotNull { (it as? JsonObject)?.let(::parseUser) } ?: emptyList()

        return IssueOrPullDetail(
            id = item.str("id") ?: "",
            number = item.int("number") ?: number,
            title = item.str("title") ?: "",
            body = item.str("body") ?: "",
            kind = if (isPr) IssueOrPullKind.PULL_REQUEST else IssueOrPullKind.ISSUE,
            state = parseState(item.str("state") ?: "OPEN"),
            isDraft = item.bool("isDraft") ?: false,
            repoFullName = "$owner/$repo",
            repoOwner = owner,
            repoName = repo,
            author = parseUser(authorObj),
            createdAt = item.str("createdAt") ?: "",
            updatedAt = item.str("updatedAt") ?: "",
            labels = parseLabels(item["labels"]?.jsonObject?.get("nodes")?.jsonArray),
            assignees = assignees,
            headRefName = item.str("headRefName"),
            baseRefName = item.str("baseRefName"),
            additions = item.int("additions") ?: 0,
            deletions = item.int("deletions") ?: 0,
            changedFilesCount = item.int("changedFiles") ?: 0,
            mergeable = item.str("mergeable"),
            reviewDecision = item.str("reviewDecision"),
            checkRollup = statusState?.let { CheckRollupSummary(state = it) },
            htmlUrl = item.str("url") ?: "",
            timeline = timeline
        )
    }

    suspend fun fetchViewerRepositories(): List<RepoSummary> {
        val data = execute(
            """
            query {
              viewer {
                repositories(first: 40, orderBy: { field: UPDATED_AT, direction: DESC }, ownerAffiliations: [OWNER, COLLABORATOR, ORGANIZATION_MEMBER]) {
                  nodes { $REPO_FIELDS }
                }
              }
            }
            """.trimIndent()
        )
        val nodes = data["viewer"]?.jsonObject?.get("repositories")?.jsonObject?.get("nodes")?.jsonArray
            ?: return emptyList()
        return nodes.mapNotNull { (it as? JsonObject)?.let(::parseRepoSummary) }
    }

    suspend fun fetchStarredRepositories(): List<RepoSummary> {
        val data = execute(
            """
            query {
              viewer {
                starredRepositories(first: 40, orderBy: { field: STARRED_AT, direction: DESC }) {
                  nodes { $REPO_FIELDS }
                }
              }
            }
            """.trimIndent()
        )
        val nodes = data["viewer"]?.jsonObject?.get("starredRepositories")?.jsonObject?.get("nodes")?.jsonArray
            ?: return emptyList()
        return nodes.mapNotNull { (it as? JsonObject)?.let(::parseRepoSummary) }
    }

    suspend fun searchRepositories(query: String): List<RepoSummary> {
        val data = execute(
            query = """
                query(${'$'}q: String!) {
                  search(query: ${'$'}q, type: REPOSITORY, first: 30) {
                    nodes {
                      ... on Repository { $REPO_FIELDS }
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf("q" to query)
        )
        val nodes = data["search"]?.jsonObject?.get("nodes")?.jsonArray ?: return emptyList()
        return nodes.mapNotNull { (it as? JsonObject)?.let(::parseRepoSummary) }
    }

    suspend fun fetchRepoDetail(owner: String, name: String): RepoDetail {
        val data = execute(
            """
            query {
              repository(owner: "$owner", name: "$name") {
                $REPO_FIELDS
                homepageUrl
                licenseInfo { name }
                watchers { totalCount }
                repositoryTopics(first: 10) {
                  nodes { topic { name } }
                }
              }
            }
            """.trimIndent()
        )
        val repoObj = data["repository"]?.jsonObject
            ?: throw GitHubApiException(404, "Repository $owner/$name not found")
        val topics = repoObj["repositoryTopics"]?.jsonObject?.get("nodes")?.jsonArray
            ?.mapNotNull { it.jsonObject["topic"]?.jsonObject?.str("name") } ?: emptyList()

        return RepoDetail(
            summary = parseRepoSummary(repoObj),
            homepageUrl = repoObj.str("homepageUrl"),
            licenseName = repoObj["licenseInfo"]?.jsonObject?.str("name"),
            topics = topics,
            watchersCount = repoObj["watchers"]?.jsonObject?.int("totalCount") ?: 0
        )
    }

    suspend fun fetchRepoIssuesAndPulls(
        owner: String,
        name: String,
        pullRequests: Boolean,
        stateFilter: IssueStateFilter = IssueStateFilter.OPEN
    ): List<IssueOrPullSummary> {
        val field = if (pullRequests) "pullRequests" else "issues"
        val statesClause = when (stateFilter) {
            IssueStateFilter.OPEN -> "[OPEN]"
            IssueStateFilter.CLOSED -> if (pullRequests) "[CLOSED, MERGED]" else "[CLOSED]"
            IssueStateFilter.ALL -> if (pullRequests) "[OPEN, CLOSED, MERGED]" else "[OPEN, CLOSED]"
        }
        val data = execute(
            """
            query {
              repository(owner: "$owner", name: "$name") {
                $field(first: 30, states: $statesClause, orderBy: { field: UPDATED_AT, direction: DESC }) {
                  nodes {
                    id
                    number
                    title
                    state
                    ${if (pullRequests) "isDraft reviewDecision" else ""}
                    createdAt
                    updatedAt
                    author { login avatarUrl url }
                    comments { totalCount }
                    labels(first: 6) { nodes { name color description } }
                  }
                }
              }
            }
            """.trimIndent()
        )
        val nodes = data["repository"]?.jsonObject?.get(field)?.jsonObject?.get("nodes")?.jsonArray
            ?: return emptyList()
        return nodes.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            IssueOrPullSummary(
                id = obj.str("id") ?: "",
                number = obj.int("number") ?: 0,
                title = obj.str("title") ?: "",
                kind = if (pullRequests) IssueOrPullKind.PULL_REQUEST else IssueOrPullKind.ISSUE,
                state = parseState(obj.str("state") ?: "OPEN"),
                isDraft = obj.bool("isDraft") ?: false,
                repoFullName = "$owner/$name",
                repoOwner = owner,
                repoName = name,
                author = parseUser(obj["author"]?.jsonObject),
                createdAt = obj.str("createdAt") ?: "",
                updatedAt = obj.str("updatedAt") ?: "",
                commentCount = obj["comments"]?.jsonObject?.int("totalCount") ?: 0,
                labels = parseLabels(obj["labels"]?.jsonObject?.get("nodes")?.jsonArray),
                reviewDecision = obj.str("reviewDecision")
            )
        }
    }

    private fun parseRepoSummary(obj: JsonObject): RepoSummary {
        val ownerObj = obj["owner"]?.jsonObject
        val langObj = obj["primaryLanguage"]?.jsonObject
        val defaultBranch = obj["defaultBranchRef"]?.jsonObject?.str("name") ?: "main"
        return RepoSummary(
            id = obj.str("id") ?: "",
            name = obj.str("name") ?: "",
            owner = ownerObj?.str("login") ?: "",
            fullName = obj.str("nameWithOwner") ?: "",
            description = obj.str("description"),
            language = langObj?.str("name"),
            languageColorHex = langObj?.str("color"),
            stargazersCount = obj.int("stargazerCount") ?: 0,
            forksCount = obj.int("forkCount") ?: 0,
            openIssuesCount = obj["issues"]?.jsonObject?.int("totalCount") ?: 0,
            isPrivate = obj.bool("isPrivate") ?: false,
            isFork = obj.bool("isFork") ?: false,
            defaultBranch = defaultBranch,
            updatedAt = obj.str("updatedAt") ?: "",
            ownerAvatarUrl = ownerObj?.str("avatarUrl") ?: "",
            viewerHasStarred = obj.bool("viewerHasStarred") ?: false
        )
    }

    private fun parseUser(obj: JsonObject?): UserSummary = UserSummary(
        login = obj?.str("login") ?: "ghost",
        avatarUrl = obj?.str("avatarUrl") ?: "",
        htmlUrl = obj?.str("url") ?: ""
    )

    private fun parseLabels(array: JsonArray?): List<LabelTag> {
        if (array == null) return emptyList()
        return array.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            LabelTag(
                name = obj.str("name") ?: return@mapNotNull null,
                colorHex = obj.str("color") ?: "6750A4",
                description = obj.str("description")
            )
        }
    }

    private fun parseState(raw: String): IssueOrPullState = when (raw.uppercase()) {
        "MERGED" -> IssueOrPullState.MERGED
        "CLOSED" -> IssueOrPullState.CLOSED
        else -> IssueOrPullState.OPEN
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.let { if (it is kotlinx.serialization.json.JsonNull) null else it.jsonPrimitive.contentOrNull }

    private fun JsonObject.int(key: String): Int? =
        this[key]?.let { if (it is kotlinx.serialization.json.JsonNull) null else it.jsonPrimitive.intOrNull }

    private fun JsonObject.bool(key: String): Boolean? =
        this[key]?.let { if (it is kotlinx.serialization.json.JsonNull) null else it.jsonPrimitive.booleanOrNull }

    companion object {
        private const val REPO_FIELDS = """
            id
            name
            nameWithOwner
            description
            isPrivate
            isFork
            stargazerCount
            forkCount
            updatedAt
            viewerHasStarred
            owner { login avatarUrl }
            primaryLanguage { name color }
            defaultBranchRef { name }
            issues(states: OPEN) { totalCount }
        """
    }
}
