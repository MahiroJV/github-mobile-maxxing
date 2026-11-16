package dev.maxxing.github.network

import dev.maxxing.github.auth.DeviceFlowClient
import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.auth.TokenStore
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.FileChangeStatus
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class CommitsTestTokenStore : TokenStore {
    var session: AuthSession? = AuthSession("tok_commits", userLogin = "octocat")
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

class CommitsAndCompareTest {

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    @Test
    fun commitsDetailTagsAndCompareEndpointsParseAndCacheCorrectly() = runTest {
        var commitListCalls = 0
        var commitDetailCalls = 0
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when (path) {
                "/repos/octocat/hello/commits" -> {
                    commitListCalls++
                    respond(
                        content = """
                            [
                              {
                                "sha": "abc1234567890",
                                "commit": {
                                  "message": "feat: add dark mode\n\nFull description line.",
                                  "author": { "name": "Octo Cat", "date": "2026-11-15T10:00:00Z" },
                                  "committer": { "name": "GitHub", "date": "2026-11-15T10:01:00Z" },
                                  "verification": { "verified": true, "reason": "valid" }
                                },
                                "author": { "login": "octocat", "avatar_url": "https://a/1.png" },
                                "parents": [{ "sha": "000111222333" }]
                              }
                            ]
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                "/repos/octocat/hello/commits/abc1234567890" -> {
                    commitDetailCalls++
                    respond(
                        content = """
                            {
                              "sha": "abc1234567890",
                              "html_url": "https://github.com/octocat/hello/commit/abc1234567890",
                              "commit": {
                                "message": "feat: add dark mode\n\nExtra details",
                                "author": { "name": "Octo Cat", "date": "2026-11-15T10:00:00Z" },
                                "verification": { "verified": true, "reason": "valid" }
                              },
                              "author": { "login": "octocat", "avatar_url": "" },
                              "parents": [{ "sha": "000111222333" }],
                              "stats": { "additions": 4, "deletions": 1, "total": 5 },
                              "files": [
                                {
                                  "filename": "Theme.kt",
                                  "status": "modified",
                                  "additions": 4,
                                  "deletions": 1,
                                  "patch": "@@ -1,2 +1,5 @@\n-val dark = false\n+val dark = true"
                                }
                              ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                "/repos/octocat/hello/tags" -> respond(
                    content = """
                        [
                          { "name": "v1.2.0", "commit": { "sha": "abc1234567890" } },
                          { "name": "v1.1.0", "commit": { "sha": "000111222333" } }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                "/repos/octocat/hello/compare/main...feat%2Fdark" -> respond(
                    content = """
                        {
                          "status": "ahead",
                          "ahead_by": 2,
                          "behind_by": 0,
                          "total_commits": 2,
                          "html_url": "https://github.com/octocat/hello/compare/main...feat/dark",
                          "commits": [
                            {
                              "sha": "abc1234567890",
                              "commit": {
                                "message": "feat: add dark mode",
                                "author": { "name": "Octo Cat", "date": "2026-11-15T10:00:00Z" },
                                "verification": { "verified": false, "reason": "unsigned" }
                              },
                              "author": { "login": "octocat", "avatar_url": "" },
                              "parents": []
                            }
                          ],
                          "files": [
                            {
                              "filename": "NewFile.kt",
                              "status": "added",
                              "additions": 12,
                              "deletions": 0,
                              "patch": "@@ -0,0 +1,2 @@\n+line1\n+line2"
                            }
                          ]
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                else -> error("Unexpected path: $path")
            }
        }

        val sessionManager = SessionManager(
            tokenStore = CommitsTestTokenStore(),
            deviceFlowClient = DeviceFlowClient(HttpClient(engine))
        )
        sessionManager.initialize()
        val repo = GitHubRepository(sessionManager, httpClient = createClient(engine))

        val commits = repo.getRepoCommits("octocat", "hello", ref = "main").getOrThrow()
        assertEquals(1, commits.size)
        assertEquals("abc1234", commits[0].shortSha)
        assertEquals("feat: add dark mode", commits[0].headline)
        assertEquals("Full description line.", commits[0].body)
        assertTrue(commits[0].isVerified)
        assertEquals(listOf("000111222333"), commits[0].parentShas)
        // Cached second hit
        repo.getRepoCommits("octocat", "hello", ref = "main").getOrThrow()
        assertEquals(1, commitListCalls)

        val detail = repo.getCommitDetail("octocat", "hello", "abc1234567890").getOrThrow()
        assertEquals(4, detail.additions)
        assertEquals(1, detail.deletions)
        assertEquals(1, detail.changedFilesCount)
        assertEquals("Theme.kt", detail.files[0].filename)
        assertEquals(1, detail.files[0].hunks.size)
        // Cached second hit
        repo.getCommitDetail("octocat", "hello", "abc1234567890").getOrThrow()
        assertEquals(1, commitDetailCalls)

        val tags = repo.getRepoTags("octocat", "hello").getOrThrow()
        assertEquals(listOf("v1.2.0", "v1.1.0"), tags.map { it.name })

        val compare = repo.compareRefs("octocat", "hello", "main", "feat/dark").getOrThrow()
        assertEquals("ahead", compare.status)
        assertEquals(2, compare.aheadBy)
        assertEquals(0, compare.behindBy)
        assertEquals(1, compare.commits.size)
        assertFalse(compare.commits[0].isVerified)
        assertEquals(FileChangeStatus.ADDED, compare.files[0].status)

        repo.clearCache()
        assertNull(repo.peekRepoCommits("octocat", "hello", ref = "main"))
        assertNull(repo.peekCommitDetail("octocat", "hello", "abc1234567890"))
    }

    @Test
    fun commitsInferAvatarFromNoreplyEmailOrNonBotCommitterWhenAuthorIsNull() = runTest {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when (path) {
                "/repos/octocat/personal/commits" -> respond(
                    content = """
                        [
                          {
                            "sha": "111aaaa",
                            "commit": {
                              "message": "local noreply commit",
                              "author": { "name": "Local User", "email": "12345+neutron42@users.noreply.github.com", "date": "2026-11-15T10:00:00Z" }
                            },
                            "author": null,
                            "committer": { "login": "web-flow", "avatar_url": "https://avatars.example/webflow.png" }
                          },
                          {
                            "sha": "222bbbb",
                            "commit": {
                              "message": "committer fallback commit",
                              "author": { "name": "Dev", "email": "dev@example.com", "date": "2026-11-15T09:00:00Z" }
                            },
                            "author": null,
                            "committer": { "login": "realuser", "avatar_url": "https://avatars.example/realuser.png" }
                          },
                          {
                            "sha": "333cccc",
                            "commit": {
                              "message": "author login without avatar url",
                              "author": { "name": "Octo", "date": "2026-11-15T08:00:00Z" }
                            },
                            "author": { "login": "octocat", "avatar_url": "" }
                          }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val sessionManager = SessionManager(
            tokenStore = CommitsTestTokenStore(),
            deviceFlowClient = DeviceFlowClient(HttpClient(engine))
        )
        sessionManager.initialize()
        val repo = GitHubRepository(sessionManager, httpClient = createClient(engine))

        val commits = repo.getRepoCommits("octocat", "personal").getOrThrow()
        assertEquals(3, commits.size)

        // Commit 1: noreply email extracted, web-flow ignored
        assertEquals("neutron42", commits[0].authorLogin)
        assertEquals("https://avatars.githubusercontent.com/neutron42?size=96", commits[0].authorAvatarUrl)

        // Commit 2: real committer used when author is null
        assertEquals("realuser", commits[1].authorLogin)
        assertEquals("https://avatars.example/realuser.png", commits[1].authorAvatarUrl)

        // Commit 3: author login without avatar_url gets github avatar url
        // Commit 3: author login without avatar url gets github avatar url
        assertEquals("octocat", commits[2].authorLogin)
        assertEquals("https://avatars.githubusercontent.com/octocat?size=96", commits[2].authorAvatarUrl)
    }

    @Test
    fun forkAndUpstreamCommitsStayIsolatedAndCrossForkCompareEncodesColonRefs() = runTest {
        val seenRequests = mutableListOf<String>()
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            val sha = request.url.parameters["sha"].orEmpty()
            seenRequests += "$path?sha=$sha"
            assertEquals("no-cache", request.headers[HttpHeaders.CacheControl])
            when (path) {
                "/repos/upstream/app/commits" -> respond(
                    content = """
                        [
                          {
                            "sha": "up11111",
                            "commit": { "message": "upstream: base release", "author": { "name": "Up", "date": "2026-11-15T10:00:00Z" } },
                            "author": { "login": "upstream", "avatar_url": "" }
                          }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/repos/forker/app/commits" -> when (sha) {
                    "empty-init" -> respond(
                        content = """{"message":"Git Repository is empty."}""",
                        status = HttpStatusCode.Conflict,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                    "feat/fork-ui" -> respond(
                        content = """
                            [
                              {
                                "sha": "fk22222",
                                "commit": { "message": "fork: implement custom header", "author": { "name": "Forker", "date": "2026-11-16T10:00:00Z" } },
                                "author": { "login": "forker", "avatar_url": "" }
                              }
                            ]
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                    else -> respond(
                        content = """
                            [
                              {
                                "sha": "fk11111",
                                "commit": { "message": "fork: sync main", "author": { "name": "Forker", "date": "2026-11-16T08:00:00Z" } },
                                "author": { "login": "forker", "avatar_url": "" }
                              },
                              {
                                "sha": "up11111",
                                "commit": { "message": "upstream: base release", "author": { "name": "Up", "date": "2026-11-15T10:00:00Z" } },
                                "author": { "login": "upstream", "avatar_url": "" }
                              }
                            ]
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "/repos/forker/app/compare/upstream:main...feat%2Ffork-ui" -> respond(
                    content = """
                        {
                          "status": "ahead",
                          "ahead_by": 1,
                          "behind_by": 0,
                          "total_commits": 1,
                          "commits": [
                            {
                              "sha": "fk22222",
                              "commit": { "message": "fork: implement custom header", "author": { "name": "Forker", "date": "2026-11-16T10:00:00Z" } },
                              "author": { "login": "forker", "avatar_url": "" }
                            }
                          ],
                          "files": []
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected commit/compare request: $path")
            }
        }
        val sessionManager = SessionManager(
            tokenStore = CommitsTestTokenStore(),
            deviceFlowClient = DeviceFlowClient(HttpClient(engine))
        )
        sessionManager.initialize()
        val repo = GitHubRepository(sessionManager, httpClient = createClient(engine))

        val upstreamCommits = repo.getRepoCommits("upstream", "app", ref = "main").getOrThrow()
        assertEquals(listOf("up11111"), upstreamCommits.map { it.sha })

        // Same repo name & branch under fork owner must NOT reuse upstream commits cache
        val forkMainCommits = repo.getRepoCommits("forker", "app", ref = "main").getOrThrow()
        assertEquals(listOf("fk11111", "up11111"), forkMainCommits.map { it.sha })

        // Case-insensitive owner lookup reuses fork cache cleanly
        assertEquals(listOf("fk11111", "up11111"), repo.peekRepoCommits("Forker", "App", "main")?.map { it.sha })

        // Fork branch commits stay distinct
        val forkFeatureCommits = repo.getRepoCommits("forker", "app", ref = "feat/fork-ui").getOrThrow()
        assertEquals(listOf("fk22222"), forkFeatureCommits.map { it.sha })

        // Newly created/empty fork returning 409 resolves cleanly to emptyList()
        val emptyForkCommits = repo.getRepoCommits("forker", "app", ref = "empty-init").getOrThrow()
        assertTrue(emptyForkCommits.isEmpty())

        // Cross-fork comparison preserves ':' between owner and branch while encoding '/'
        val compare = repo.compareRefs("forker", "app", base = "upstream:main", head = "feat/fork-ui").getOrThrow()
        assertEquals(1, compare.aheadBy)
        assertEquals(listOf("fk22222"), compare.commits.map { it.sha })
        assertTrue("/repos/forker/app/compare/upstream:main...feat%2Ffork-ui?sha=" in seenRequests)
    }
}
