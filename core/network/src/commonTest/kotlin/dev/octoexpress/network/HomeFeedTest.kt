package dev.octoexpress.network

import dev.octoexpress.auth.DeviceFlowClient
import dev.octoexpress.auth.NotificationPreferences
import dev.octoexpress.auth.SessionManager
import dev.octoexpress.auth.ThemePreferences
import dev.octoexpress.auth.TokenStore
import dev.octoexpress.model.ActivityEventKind
import dev.octoexpress.model.AuthSession
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
import kotlin.test.assertNull

private class HomeTestTokenStore : TokenStore {
    var session: AuthSession? = AuthSession("tok_home", userLogin = "octocat")
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

class HomeFeedTest {

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    @Test
    fun contributionCalendarParsesAllLevelsAndMissingUserThrows404() = runTest {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            if (callCount == 1) {
                respond(
                    content = """
                        {
                          "data": {
                            "user": {
                              "contributionsCollection": {
                                "contributionCalendar": {
                                  "totalContributions": 421,
                                  "weeks": [
                                    {
                                      "contributionDays": [
                                        { "date": "2026-11-08", "contributionCount": 0, "contributionLevel": "NONE" },
                                        { "date": "2026-11-09", "contributionCount": 2, "contributionLevel": "FIRST_QUARTILE" },
                                        { "date": "2026-11-10", "contributionCount": 5, "contributionLevel": "SECOND_QUARTILE" },
                                        { "date": "2026-11-11", "contributionCount": 9, "contributionLevel": "THIRD_QUARTILE" },
                                        { "date": "2026-11-12", "contributionCount": 14, "contributionLevel": "FOURTH_QUARTILE" }
                                      ]
                                    }
                                  ]
                                }
                              }
                            }
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"data": {"user": null}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val gql = GitHubGraphQlService(createClient(engine))
        val cal = gql.fetchContributionCalendar("octocat")
        assertEquals(421, cal.totalContributions)
        assertEquals(1, cal.weeks.size)
        assertEquals(listOf(0, 1, 2, 3, 4), cal.weeks[0].days.map { it.level })
        assertEquals(14, cal.weeks[0].days[4].count)

        val err = assertFailsWith<GitHubApiException> {
            gql.fetchContributionCalendar("missing-user")
        }
        assertEquals(404, err.statusCode)
    }

    @Test
    fun activityEventsParseAllGitHubEventKindsCorrectly() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/users/octocat/received_events" -> respond(
                    content = """
                        [
                          {
                            "id": "e1",
                            "type": "PushEvent",
                            "actor": { "login": "alice", "avatar_url": "https://a" },
                            "repo": { "name": "alice/app" },
                            "created_at": "2026-11-15T09:00:00Z",
                            "payload": {
                              "ref": "refs/heads/feature/ui",
                              "size": 2,
                              "commits": [
                                { "sha": "abc1234", "message": "Add Home heatmap\n\nExtra body" }
                              ]
                            }
                          },
                          {
                            "id": "e2",
                            "type": "PullRequestEvent",
                            "actor": { "login": "bob", "avatar_url": "" },
                            "repo": { "name": "octo/core" },
                            "created_at": "2026-11-15T08:00:00Z",
                            "payload": {
                              "action": "closed",
                              "number": 77,
                              "pull_request": {
                                "number": 77,
                                "title": "Merge KMP cache layer",
                                "merged_at": "2026-11-15T08:00:00Z"
                              }
                            }
                          },
                          {
                            "id": "e3",
                            "type": "IssuesEvent",
                            "actor": { "login": "carol", "avatar_url": "" },
                            "repo": { "name": "octo/core" },
                            "created_at": "2026-11-15T07:00:00Z",
                            "payload": {
                              "action": "opened",
                              "issue": { "number": 19, "title": "Support Release Notes" }
                            }
                          },
                          {
                            "id": "e4",
                            "type": "ReleaseEvent",
                            "actor": { "login": "dan", "avatar_url": "" },
                            "repo": { "name": "octo/core" },
                            "created_at": "2026-11-15T06:00:00Z",
                            "payload": {
                              "action": "published",
                              "release": { "tag_name": "v2.0.0", "name": "OctoExpress 2.0" }
                            }
                          },
                          {
                            "id": "e5",
                            "type": "WatchEvent",
                            "actor": { "login": "eve", "avatar_url": "" },
                            "repo": { "name": "octo/core" },
                            "created_at": "2026-11-15T05:00:00Z",
                            "payload": { "action": "started" }
                          },
                          {
                            "id": "e6",
                            "type": "PullRequestReviewEvent",
                            "actor": { "login": "frank", "avatar_url": "" },
                            "repo": { "name": "octo/core" },
                            "created_at": "2026-11-15T04:00:00Z",
                            "payload": {
                              "pull_request": { "number": 88, "title": "Fast scroll" },
                              "review": { "state": "approved" }
                            }
                          }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }

        val rest = GitHubRestService(createClient(engine))
        val events = rest.fetchReceivedEvents("octocat")
        assertEquals(6, events.size)

        assertEquals(ActivityEventKind.PUSH, events[0].kind)
        assertEquals("feature/ui", events[0].branchName)
        assertEquals("abc1234", events[0].commitSha)
        assertEquals("Add Home heatmap", events[0].detailTitle)
        assertEquals("pushed 2 commits to feature/ui", events[0].summaryText)

        assertEquals(ActivityEventKind.PULL_REQUEST, events[1].kind)
        assertEquals(77, events[1].targetIssueNumber)
        assertEquals("merged pull request #77", events[1].summaryText)

        assertEquals(ActivityEventKind.ISSUES, events[2].kind)
        assertEquals(19, events[2].targetIssueNumber)
        assertEquals("opened issue #19", events[2].summaryText)

        assertEquals(ActivityEventKind.RELEASE, events[3].kind)
        assertEquals("v2.0.0", events[3].releaseTag)
        assertEquals("published release v2.0.0", events[3].summaryText)

        assertEquals(ActivityEventKind.WATCH, events[4].kind)
        assertEquals("starred repository", events[4].summaryText)

        assertEquals(ActivityEventKind.PULL_REQUEST_REVIEW, events[5].kind)
        assertEquals(88, events[5].targetIssueNumber)
        assertEquals("approved pull request #88", events[5].summaryText)
    }

    @Test
    fun repositoryCachesAndRefreshesHomeFeedAndCalendar() = runTest {
        var receivedCalls = 0
        var userCalls = 0
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/graphql" -> respond(
                    content = """
                        {
                          "data": {
                            "user": {
                              "contributionsCollection": {
                                "contributionCalendar": {
                                  "totalContributions": 120,
                                  "weeks": []
                                }
                              }
                            }
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/users/octocat/received_events" -> {
                    receivedCalls++
                    respond(
                        content = """
                            [
                              {
                                "id": "rec_$receivedCalls",
                                "type": "WatchEvent",
                                "actor": { "login": "alice", "avatar_url": "" },
                                "repo": { "name": "alice/kmp" },
                                "created_at": "2026-11-15T09:00:00Z",
                                "payload": {}
                              }
                            ]
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "/users/octocat/events" -> {
                    userCalls++
                    respond(
                        content = "[]",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }

        val sessionManager = SessionManager(
            tokenStore = HomeTestTokenStore(),
            deviceFlowClient = DeviceFlowClient(HttpClient(engine))
        )
        sessionManager.initialize()
        val repo = GitHubRepository(sessionManager, httpClient = createClient(engine))

        assertEquals(120, repo.getContributionCalendar("octocat").getOrThrow().totalContributions)
        assertEquals(120, repo.peekContributionCalendar("octocat")?.totalContributions)

        val first = repo.getReceivedEvents("octocat").getOrThrow()
        assertEquals("rec_1", first[0].id)
        // Cached hit should not increment network call count
        assertEquals("rec_1", repo.getReceivedEvents("octocat").getOrThrow()[0].id)
        assertEquals(1, receivedCalls)

        // Force refresh hits network again
        val refreshed = repo.getReceivedEvents("octocat", forceRefresh = true).getOrThrow()
        assertEquals("rec_2", refreshed[0].id)
        assertEquals(2, receivedCalls)

        repo.getUserEvents("octocat").getOrThrow()
        assertEquals(1, userCalls)

        repo.clearCache()
        assertNull(repo.peekContributionCalendar("octocat"))
        assertNull(repo.peekReceivedEvents("octocat"))
        assertNull(repo.peekUserEvents("octocat"))
    }
}
