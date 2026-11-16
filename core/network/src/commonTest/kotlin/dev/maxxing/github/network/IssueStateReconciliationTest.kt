package dev.maxxing.github.network

import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.auth.TokenStore
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.IssueFilterPreset
import dev.maxxing.github.model.IssueOrPullKind
import dev.maxxing.github.model.IssueOrPullState
import dev.maxxing.github.model.IssueOrPullSummary
import dev.maxxing.github.model.IssueStateFilter
import dev.maxxing.github.model.MergeMethod
import dev.maxxing.github.model.UserSummary
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class IssueStateTestTokenStore : TokenStore {
    var session: AuthSession? = AuthSession("tok_state", userLogin = "octocat")
    override suspend fun saveSession(session: AuthSession) { this.session = session }
    override suspend fun loadSession() = session
    override suspend fun clearSession() { session = null }
    override suspend fun saveClientId(clientId: String) {}
    override suspend fun loadClientId(): String? = null
    override suspend fun saveThemePreferences(prefs: ThemePreferences) {}
    override suspend fun loadThemePreferences() = ThemePreferences()
    override suspend fun saveNotificationPreferences(prefs: NotificationPreferences) {}
    override suspend fun loadNotificationPreferences() = NotificationPreferences()
    override suspend fun saveSeenNotificationMarkers(markers: Map<String, String>) {}
    override suspend fun loadSeenNotificationMarkers() = emptyMap<String, String>()
}

class IssueStateReconciliationTest {

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false })
        }
    }

    private fun issueJsonNode(number: Int, title: String, state: String): String = """
        {
          "__typename": "Issue",
          "id": "I_$number",
          "number": $number,
          "title": "$title",
          "state": "$state",
          "createdAt": "2026-11-15T10:00:00Z",
          "updatedAt": "2026-11-15T10:00:00Z",
          "author": { "login": "octocat", "avatarUrl": "https://example.com/a.png", "url": "https://github.com/octocat" },
          "repository": { "nameWithOwner": "octo/app", "name": "app", "owner": { "login": "octo" } },
          "comments": { "totalCount": 1 },
          "labels": { "nodes": [] }
        }
    """.trimIndent()

    @Test
    fun `closing and reopening issue immediately updates cached lists and resists search index lag`() = runTest {
        val patchedBodies = mutableListOf<String>()
        // Simulate GitHub search index lag: even after PATCH closes #42, search queries
        // keep returning #42 as OPEN under is:open and omit #42 under is:closed!
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/repos/octo/app/issues/42" && request.method == HttpMethod.Patch -> {
                    val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    patchedBodies += body
                    respond(
                        content = """{"id": 42, "number": 42, "state": "closed"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                path == "/graphql" -> {
                    val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    val nodesJson = when {
                        body.contains("is:open") -> "[${issueJsonNode(42, "Bug in list", "OPEN")},${issueJsonNode(43, "Still open", "OPEN")}]"
                        body.contains("is:closed") -> "[${issueJsonNode(10, "Old closed", "CLOSED")}]"
                        else -> "[${issueJsonNode(42, "Bug in list", "OPEN")},${issueJsonNode(43, "Still open", "OPEN")},${issueJsonNode(10, "Old closed", "CLOSED")}]"
                    }
                    respond(
                        content = """{"data":{"search":{"nodes":$nodesJson}}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unexpected request: ${request.method.value} $path")
            }
        }

        val sessionManager = SessionManager(IssueStateTestTokenStore()).also { it.initialize() }
        val repo = GitHubRepository(sessionManager, httpClient = createClient(engine))

        val initialOpen = repo.getMyIssuesAndPulls(IssueFilterPreset.CREATED, "octocat", IssueStateFilter.OPEN).getOrThrow()
        assertEquals(listOf(42, 43), initialOpen.map { it.number })
        val initialClosed = repo.getMyIssuesAndPulls(IssueFilterPreset.CREATED, "octocat", IssueStateFilter.CLOSED).getOrThrow()
        assertEquals(listOf(10), initialClosed.map { it.number })

        val revBefore = repo.issueStateRevision.value
        repo.setIssueOrPullState("octo", "app", 42, open = false).getOrThrow()
        assertTrue(patchedBodies.single().contains("closed"))
        assertTrue(repo.issueStateRevision.value > revBefore)

        // Cached OPEN, CLOSED, and ALL should update synchronously
        val cachedOpenAfterClose = repo.peekMyIssuesAndPulls(IssueFilterPreset.CREATED, "octocat", IssueStateFilter.OPEN)!!
        assertEquals(listOf(43), cachedOpenAfterClose.map { it.number })
        val cachedClosedAfterClose = repo.peekMyIssuesAndPulls(IssueFilterPreset.CREATED, "octocat", IssueStateFilter.CLOSED)!!
        assertEquals(listOf(42, 10), cachedClosedAfterClose.map { it.number })
        assertEquals(IssueOrPullState.CLOSED, cachedClosedAfterClose.first().state)

        // Even a forced network refresh during search index lag must NOT put #42 back in OPEN or drop it from CLOSED
        val refreshedOpen = repo.getMyIssuesAndPulls(
            IssueFilterPreset.CREATED,
            "octocat",
            IssueStateFilter.OPEN,
            forceRefresh = true
        ).getOrThrow()
        assertEquals(listOf(43), refreshedOpen.map { it.number })

        val refreshedClosed = repo.getMyIssuesAndPulls(
            IssueFilterPreset.CREATED,
            "octocat",
            IssueStateFilter.CLOSED,
            forceRefresh = true
        ).getOrThrow()
        assertEquals(listOf(42, 10), refreshedClosed.map { it.number })

        // Reopening moves #42 back to OPEN and removes it from CLOSED
        repo.setIssueOrPullState("octo", "app", 42, open = true).getOrThrow()
        assertEquals(listOf(42, 43), repo.peekMyIssuesAndPulls(IssueFilterPreset.CREATED, "octocat", IssueStateFilter.OPEN)!!.map { it.number })
        assertEquals(listOf(10), repo.peekMyIssuesAndPulls(IssueFilterPreset.CREATED, "octocat", IssueStateFilter.CLOSED)!!.map { it.number })
    }

    @Test
    fun `merging PR immediately moves it from open to closed with MERGED state`() = runTest {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/repos/octo/app/pulls/77/merge" && request.method == HttpMethod.Put -> {
                    respond(
                        content = """{"sha":"abc123","merged":true}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                path == "/graphql" -> {
                    val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    val prNode = """
                        {
                          "__typename": "PullRequest",
                          "id": "PR_77",
                          "number": 77,
                          "title": "Add express delivery",
                          "state": "OPEN",
                          "isDraft": false,
                          "createdAt": "2026-11-15T10:00:00Z",
                          "updatedAt": "2026-11-15T10:00:00Z",
                          "author": { "login": "octocat", "avatarUrl": "https://example.com/a.png", "url": "https://github.com/octocat" },
                          "repository": { "nameWithOwner": "octo/app", "name": "app", "owner": { "login": "octo" } },
                          "comments": { "totalCount": 0 },
                          "labels": { "nodes": [] },
                          "reviewDecision": null,
                          "commits": { "nodes": [] }
                        }
                    """.trimIndent()
                    val nodes = if (body.contains("is:open")) "[$prNode]" else "[]"
                    respond(
                        content = """{"data":{"search":{"nodes":$nodes}}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unexpected: $path")
            }
        }

        val sessionManager = SessionManager(IssueStateTestTokenStore()).also { it.initialize() }
        val repo = GitHubRepository(sessionManager, httpClient = createClient(engine))

        val openBefore = repo.getMyIssuesAndPulls(IssueFilterPreset.CREATED, "octocat", IssueStateFilter.OPEN).getOrThrow()
        assertEquals(listOf(77), openBefore.map { it.number })

        repo.mergePullRequest("octo", "app", 77, MergeMethod.SQUASH).getOrThrow()

        assertTrue(repo.peekMyIssuesAndPulls(IssueFilterPreset.CREATED, "octocat", IssueStateFilter.OPEN)!!.isEmpty())
        val closedAfter = repo.getMyIssuesAndPulls(IssueFilterPreset.CREATED, "octocat", IssueStateFilter.CLOSED, forceRefresh = true).getOrThrow()
        assertEquals(1, closedAfter.size)
        assertEquals(IssueOrPullState.MERGED, closedAfter.first().state)
    }

    @Test
    fun `pure reconcileIssueListWithStateOverrides applies overrides consistently`() {
        val sample = IssueOrPullSummary(
            id = "1",
            number = 5,
            title = "Sample",
            kind = IssueOrPullKind.ISSUE,
            state = IssueOrPullState.OPEN,
            repoFullName = "octo/app",
            repoOwner = "octo",
            repoName = "app",
            author = UserSummary("octocat"),
            createdAt = "",
            updatedAt = ""
        )
        val overrides = mapOf(("octo/app" to 5) to IssueOrPullState.CLOSED)

        assertTrue(reconcileIssueListWithStateOverrides(listOf(sample), IssueStateFilter.OPEN, overrides).isEmpty())
        val closedList = reconcileIssueListWithStateOverrides(emptyList(), IssueStateFilter.CLOSED, overrides, listOf(sample))
        assertEquals(listOf(IssueOrPullState.CLOSED), closedList.map { it.state })
    }
}
