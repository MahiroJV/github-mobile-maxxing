package dev.octoexpress.network

import dev.octoexpress.auth.DeviceFlowClient
import dev.octoexpress.auth.NotificationPreferences
import dev.octoexpress.auth.SessionManager
import dev.octoexpress.auth.ThemePreferences
import dev.octoexpress.auth.TokenStore
import dev.octoexpress.model.AuthSession
import dev.octoexpress.model.IssueOrPullKind
import dev.octoexpress.model.IssueOrPullState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class SearchTestTokenStore : TokenStore {
    var session: AuthSession? = AuthSession("tok_search", userLogin = "octocat")
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

class GlobalSearchTest {

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    @Test
    fun searchesIssuesPullsCodeAndUsers() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/search/issues" -> respond(
                    content = """
                        {
                          "total_count": 2,
                          "items": [
                            {
                              "id": 101,
                              "node_id": "I_kw1",
                              "number": 42,
                              "title": "Crash on Android 15",
                              "state": "open",
                              "draft": false,
                              "comments": 3,
                              "created_at": "2026-11-01T00:00:00Z",
                              "updated_at": "2026-11-10T00:00:00Z",
                              "repository_url": "https://api.github.com/repos/octo/app",
                              "user": { "login": "alice", "avatar_url": "https://a/1.png" },
                              "labels": [{ "name": "bug", "color": "d73a4a" }]
                            },
                            {
                              "id": 102,
                              "node_id": "PR_kw2",
                              "number": 99,
                              "title": "WIP: compose navigation",
                              "state": "closed",
                              "draft": true,
                              "comments": 1,
                              "created_at": "2026-11-02T00:00:00Z",
                              "updated_at": "2026-11-11T00:00:00Z",
                              "repository_url": "https://api.github.com/repos/jetbrains/compose-multiplatform",
                              "pull_request": { "url": "https://api.github.com/repos/jetbrains/compose-multiplatform/pulls/99" },
                              "user": { "login": "bob", "avatar_url": "https://a/2.png" },
                              "labels": []
                            }
                          ]
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                "/search/code" -> {
                    assertEquals(
                        "application/vnd.github.text-match+json",
                        request.headers[HttpHeaders.Accept]
                    )
                    respond(
                        content = """
                            {
                              "total_count": 1,
                              "items": [
                                {
                                  "name": "ReposScreen.kt",
                                  "path": "composeApp/src/commonMain/kotlin/ReposScreen.kt",
                                  "sha": "abc123",
                                  "html_url": "https://github.com/octo/app/blob/main/ReposScreen.kt",
                                  "repository": {
                                    "full_name": "octo/app",
                                    "name": "app",
                                    "owner": { "login": "octo", "avatar_url": "" }
                                  },
                                  "text_matches": [
                                    { "fragment": "fun RepoListPane(container: AppContainer)" }
                                  ]
                                }
                              ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                "/search/users" -> respond(
                    content = """
                        {
                          "total_count": 1,
                          "items": [
                            { "login": "kotlin-dev", "avatar_url": "https://a/k.png", "html_url": "https://github.com/kotlin-dev" }
                          ]
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }

        val rest = GitHubRestService(createClient(engine))
        val issues = rest.searchIssuesAndPullsRest("compose crash")
        assertEquals(2, issues.size)
        assertEquals(IssueOrPullKind.ISSUE, issues[0].kind)
        assertEquals(IssueOrPullState.OPEN, issues[0].state)
        assertEquals("octo", issues[0].repoOwner)
        assertEquals("app", issues[0].repoName)
        assertEquals("bug", issues[0].labels.single().name)

        assertEquals(IssueOrPullKind.PULL_REQUEST, issues[1].kind)
        assertEquals(IssueOrPullState.CLOSED, issues[1].state)
        assertTrue(issues[1].isDraft)
        assertEquals("jetbrains/compose-multiplatform", issues[1].repoFullName)

        val code = rest.searchCodeRest("RepoListPane")
        assertEquals(1, code.size)
        assertEquals("composeApp/src/commonMain/kotlin/ReposScreen.kt", code[0].path)
        assertEquals("octo/app", code[0].repoFullName)
        assertTrue(code[0].fragments.single().contains("RepoListPane"))

        val users = rest.searchUsersRest("kotlin")
        assertEquals("kotlin-dev", users.single().login)
    }

    @Test
    fun trendingQueriesAndRepositoryCachesWorkCorrectly() = runTest {
        val seenQueries = mutableListOf<String>()
        var callCount = 0
        val engine = MockEngine { request ->
            callCount++
            when (request.url.encodedPath) {
                "/search/repositories" -> {
                    val q = request.url.parameters["q"].orEmpty()
                    seenQueries += q
                    respond(
                        content = """
                            {
                              "total_count": 1,
                              "items": [
                                {
                                  "id": 900,
                                  "node_id": "R_900",
                                  "name": "compose-samples",
                                  "full_name": "android/compose-samples",
                                  "description": "Jetpack Compose samples",
                                  "language": null,
                                  "stargazers_count": 20000,
                                  "forks_count": 4000,
                                  "open_issues_count": 10,
                                  "private": false,
                                  "fork": false,
                                  "default_branch": "main",
                                  "updated_at": "2026-11-15T00:00:00Z",
                                  "owner": { "login": "android", "avatar_url": "" }
                                }
                              ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "/search/code" -> respond(
                    content = """{"message":"API rate limit exceeded"}""",
                    status = HttpStatusCode.Forbidden,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }

        val http = createClient(engine)
        val store = SearchTestTokenStore()
        val sessionManager = SessionManager(store, DeviceFlowClient(http, "cid"))
        sessionManager.initialize()
        val repo = GitHubRepository(sessionManager, httpClient = http)

        // Blank search should return empty immediately without HTTP calls
        assertEquals(emptyList(), repo.searchIssuesAndPullsGlobal("   ").getOrThrow())
        assertEquals(emptyList(), repo.searchCode("").getOrThrow())
        assertEquals(emptyList(), repo.searchUsers(" ").getOrThrow())
        assertEquals(0, callCount)

        // Trending All & Trending Kotlin
        val allTrending = repo.getTrendingRepositories(language = null).getOrThrow()
        assertEquals("android/compose-samples", allTrending.single().fullName)
        assertNotNull(repo.peekTrendingRepositories(null))

        val kotlinTrending = repo.getTrendingRepositories(language = "Kotlin").getOrThrow()
        assertEquals(1, kotlinTrending.size)
        // Cached hit should not trigger another request
        repo.getTrendingRepositories(language = "Kotlin").getOrThrow()
        assertEquals(listOf("stars:>500", "stars:>500 language:Kotlin"), seenQueries)

        // Error propagation check
        val codeErr = assertFailsWith<GitHubApiException> {
            GitHubRestService(http).searchCodeRest("fail")
        }
        assertEquals(403, codeErr.statusCode)
    }
}
