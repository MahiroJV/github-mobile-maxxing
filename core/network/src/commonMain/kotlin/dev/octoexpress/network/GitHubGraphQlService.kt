package dev.octoexpress.network

import dev.octoexpress.model.CheckRollupSummary
import dev.octoexpress.model.ContributionCalendar
import dev.octoexpress.model.ContributionDay
import dev.octoexpress.model.ContributionWeek
import dev.octoexpress.model.IssueFilterPreset
import dev.octoexpress.model.IssueOrPullDetail
import dev.octoexpress.model.IssueOrPullKind
import dev.octoexpress.model.IssueOrPullState
import dev.octoexpress.model.IssueOrPullSummary
import dev.octoexpress.model.IssueStateFilter
import dev.octoexpress.model.LabelTag
import dev.octoexpress.model.MilestoneSummary
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
        val v = data.obj("viewer") ?: throw GitHubApiException(500, "Missing viewer in GraphQL response")
        return ViewerProfile(
            login = v.str("login") ?: "",
            name = v.str("name"),
            avatarUrl = v.str("avatarUrl") ?: "",
            bio = v.str("bio"),
            company = v.str("company"),
            location = v.str("location"),
            followersCount = v.obj("followers")?.int("totalCount") ?: 0,
            followingCount = v.obj("following")?.int("totalCount") ?: 0,
            publicReposCount = v.obj("repositories")?.int("totalCount") ?: 0,
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

        val nodes = data.obj("search")?.arr("nodes") ?: return emptyList()
        return nodes.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val typeName = obj.str("__typename") ?: return@mapNotNull null
            if (typeName != "Issue" && typeName != "PullRequest") return@mapNotNull null
            val repoObj = obj.obj("repository") ?: return@mapNotNull null
            val authorObj = obj.obj("author")
            val stateStr = obj.str("state") ?: "OPEN"
            val statusState = (obj.obj("commits")
                ?.arr("nodes")
                ?.firstOrNull() as? JsonObject)
                ?.obj("commit")
                ?.obj("statusCheckRollup")
                ?.str("state")

            IssueOrPullSummary(
                id = obj.str("id") ?: "",
                number = obj.int("number") ?: 0,
                title = obj.str("title") ?: "",
                kind = if (typeName == "PullRequest") IssueOrPullKind.PULL_REQUEST else IssueOrPullKind.ISSUE,
                state = parseState(stateStr),
                isDraft = obj.bool("isDraft") ?: false,
                repoFullName = repoObj.str("nameWithOwner") ?: "",
                repoOwner = repoObj.obj("owner")?.str("login") ?: "",
                repoName = repoObj.str("name") ?: "",
                author = parseUser(authorObj),
                createdAt = obj.str("createdAt") ?: "",
                updatedAt = obj.str("updatedAt") ?: "",
                commentCount = obj.obj("comments")?.int("totalCount") ?: 0,
                labels = parseLabels(obj.obj("labels")?.arr("nodes")),
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
                    milestone { number title description state dueOn }
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
                    milestone { number title description state dueOn }
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
        val item = data.obj("repository")?.obj("issueOrPullRequest")
            ?: throw GitHubApiException(404, "Issue or PR #$number not found in $owner/$repo")

        val typeName = item.str("__typename") ?: "Issue"
        val isPr = typeName == "PullRequest"
        val authorObj = item.obj("author")
        val statusState = (item.obj("commits")
            ?.arr("nodes")
            ?.firstOrNull() as? JsonObject)
            ?.obj("commit")
            ?.obj("statusCheckRollup")
            ?.str("state")

        val timelineNodes = item.obj("timelineItems")?.arr("nodes") ?: JsonArray(emptyList())
        val timeline = timelineNodes.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            when (obj.str("__typename")) {
                "IssueComment" -> TimelineEvent.CommentEvent(
                    id = obj.str("id") ?: "",
                    createdAt = obj.str("createdAt") ?: "",
                    author = parseUser(obj.obj("author")),
                    body = obj.str("body") ?: "",
                    authorAssociation = obj.str("authorAssociation")
                )
                "PullRequestReview" -> TimelineEvent.ReviewEventItem(
                    id = obj.str("id") ?: "",
                    createdAt = obj.str("createdAt") ?: "",
                    author = parseUser(obj.obj("author")),
                    state = obj.str("state") ?: "COMMENTED",
                    body = obj.str("body") ?: ""
                )
                "PullRequestCommit" -> {
                    val commit = obj.obj("commit")
                    TimelineEvent.CommitEvent(
                        id = obj.str("id") ?: "",
                        createdAt = commit?.str("committedDate") ?: "",
                        oidShort = commit?.str("abbreviatedOid") ?: "",
                        messageHeadline = commit?.str("messageHeadline") ?: "",
                        authorName = commit?.obj("author")?.str("name") ?: ""
                    )
                }
                "MergedEvent" -> TimelineEvent.StateChangeEvent(
                    id = obj.str("id") ?: "",
                    createdAt = obj.str("createdAt") ?: "",
                    actor = parseUser(obj.obj("actor")),
                    actionLabel = "merged into ${obj.str("mergeRefName") ?: "base"}"
                )
                "ClosedEvent" -> TimelineEvent.StateChangeEvent(
                    id = obj.str("id") ?: "",
                    createdAt = obj.str("createdAt") ?: "",
                    actor = parseUser(obj.obj("actor")),
                    actionLabel = "closed this"
                )
                "ReopenedEvent" -> TimelineEvent.StateChangeEvent(
                    id = obj.str("id") ?: "",
                    createdAt = obj.str("createdAt") ?: "",
                    actor = parseUser(obj.obj("actor")),
                    actionLabel = "reopened this"
                )
                else -> null
            }
        }

        val assignees = item.obj("assignees")?.arr("nodes")
            ?.mapNotNull { (it as? JsonObject)?.let(::parseUser) } ?: emptyList()

        val milestone = item.obj("milestone")?.let { ms ->
            val num = ms.int("number")
            val mTitle = ms.str("title")
            if (num != null && mTitle != null) {
                MilestoneSummary(
                    number = num,
                    title = mTitle,
                    description = ms.str("description"),
                    state = ms.str("state") ?: "OPEN",
                    dueOn = ms.str("dueOn")
                )
            } else null
        }

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
            labels = parseLabels(item.obj("labels")?.arr("nodes")),
            assignees = assignees,
            milestone = milestone,
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
        val nodes = data.obj("viewer")?.obj("repositories")?.arr("nodes")
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
        val nodes = data.obj("viewer")?.obj("starredRepositories")?.arr("nodes")
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
        val nodes = data.obj("search")?.arr("nodes") ?: return emptyList()
        return nodes.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            if (obj.str("nameWithOwner").isNullOrBlank()) return@mapNotNull null
            parseRepoSummary(obj)
        }
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
        val repoObj = data.obj("repository")
            ?: throw GitHubApiException(404, "Repository $owner/$name not found")
        val topics = repoObj.obj("repositoryTopics")?.arr("nodes")
            ?.mapNotNull { (it as? JsonObject)?.obj("topic")?.str("name") } ?: emptyList()

        return RepoDetail(
            summary = parseRepoSummary(repoObj),
            homepageUrl = repoObj.str("homepageUrl"),
            licenseName = repoObj.obj("licenseInfo")?.str("name"),
            topics = topics,
            watchersCount = repoObj.obj("watchers")?.int("totalCount") ?: 0
        )
    }

    suspend fun fetchContributionCalendar(login: String): ContributionCalendar {
        val data = execute(
            query = """
                query(${'$'}login: String!) {
                  user(login: ${'$'}login) {
                    contributionsCollection {
                      contributionCalendar {
                        totalContributions
                        weeks {
                          contributionDays {
                            date
                            contributionCount
                            contributionLevel
                          }
                        }
                      }
                    }
                  }
                }
            """.trimIndent(),
            variables = mapOf("login" to login)
        )
        val calendar = data.obj("user")
            ?.obj("contributionsCollection")
            ?.obj("contributionCalendar")
            ?: throw GitHubApiException(404, "Contribution calendar not found for @$login")

        val total = calendar.int("totalContributions") ?: 0
        val weeks = calendar.arr("weeks")?.mapNotNull { weekEl ->
            val weekObj = weekEl as? JsonObject ?: return@mapNotNull null
            val days = weekObj.arr("contributionDays")?.mapNotNull { dayEl ->
                val dayObj = dayEl as? JsonObject ?: return@mapNotNull null
                ContributionDay(
                    date = dayObj.str("date") ?: return@mapNotNull null,
                    count = dayObj.int("contributionCount") ?: 0,
                    level = parseContributionLevel(dayObj.str("contributionLevel"))
                )
            } ?: emptyList()
            ContributionWeek(days = days)
        } ?: emptyList()

        return ContributionCalendar(totalContributions = total, weeks = weeks)
    }

    private fun parseContributionLevel(raw: String?): Int = when (raw?.uppercase()) {
        "FIRST_QUARTILE" -> 1
        "SECOND_QUARTILE" -> 2
        "THIRD_QUARTILE" -> 3
        "FOURTH_QUARTILE" -> 4
        else -> 0
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
        val nodes = data.obj("repository")?.obj(field)?.arr("nodes")
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
                author = parseUser(obj.obj("author")),
                createdAt = obj.str("createdAt") ?: "",
                updatedAt = obj.str("updatedAt") ?: "",
                commentCount = obj.obj("comments")?.int("totalCount") ?: 0,
                labels = parseLabels(obj.obj("labels")?.arr("nodes")),
                reviewDecision = obj.str("reviewDecision")
            )
        }
    }

    private fun parseRepoSummary(obj: JsonObject): RepoSummary {
        val ownerObj = obj.obj("owner")
        val langObj = obj.obj("primaryLanguage")
        val defaultBranch = obj.obj("defaultBranchRef")?.str("name") ?: "main"
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
            openIssuesCount = obj.obj("issues")?.int("totalCount") ?: 0,
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

    internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

    internal fun JsonObject.str(key: String): String? =
        this[key]?.let { if (it is kotlinx.serialization.json.JsonNull) null else it.jsonPrimitive.contentOrNull }

    internal fun JsonObject.int(key: String): Int? =
        this[key]?.let { if (it is kotlinx.serialization.json.JsonNull) null else it.jsonPrimitive.intOrNull }

    internal fun JsonObject.bool(key: String): Boolean? =
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
