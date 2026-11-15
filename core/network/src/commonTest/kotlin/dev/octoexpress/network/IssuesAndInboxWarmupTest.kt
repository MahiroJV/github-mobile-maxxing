package dev.octoexpress.network

import dev.octoexpress.auth.DeviceFlowClient
import dev.octoexpress.auth.NotificationPreferences
import dev.octoexpress.auth.SessionManager
import dev.octoexpress.auth.ThemePreferences
import dev.octoexpress.auth.TokenStore
import dev.octoexpress.model.AuthSession
import dev.octoexpress.model.IssueFilterPreset
import dev.octoexpress.model.IssueStateFilter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private class WarmupTestTokenStore : TokenStore {
    var session: AuthSession? = AuthSession("tok_warm", userLogin = "octocat")
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

class IssuesAndInboxWarmupTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
    }

    @Test
    fun slowEmptyAssignedRequestDoesNotOverwriteCachedCreatedPreset() = runTest {
        val engine = MockEngine { request ->
            val bodyText = (request.body as TextContent).text
            val vars = json.parseToJsonElement(bodyText).jsonObject["variables"]?.jsonObject
            val q = vars?.get("q")?.jsonPrimitive?.content.orEmpty()
            if (q.contains("assignee:octocat")) {
                delay(40)
                respond(
                    content = """{"data":{"search":{"nodes":[]}}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """
                        {
                          "data": {
                            "search": {
                              "nodes": [
                                {
                                  "__typename": "Issue",
                                  "id": "I_CREATED_1",
                                  "number": 7,
                                  "title": "Created issue stays cached",
                                  "state": "OPEN",
                                  "createdAt": "2026-11-15T10:00:00Z",
                                  "updatedAt": "2026-11-15T10:00:00Z",
                                  "repository": { "nameWithOwner": "octocat/app", "owner": { "login": "octocat" }, "name": "app" },
                                  "author": { "login": "octocat", "avatarUrl": "", "url": "" },
                                  "comments": { "totalCount": 0 },
                                  "labels": { "nodes": [] }
                                }
                              ]
                            }
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val sessionManager = SessionManager(WarmupTestTokenStore(), DeviceFlowClient(HttpClient(engine)))
        sessionManager.initialize()
        val repo = GitHubRepository(sessionManager, httpClient = createClient(engine))

        val createdJob = async {
            repo.getMyIssuesAndPulls(IssueFilterPreset.CREATED, "octocat", IssueStateFilter.OPEN)
        }
        val assignedJob = async {
            repo.getMyIssuesAndPulls(IssueFilterPreset.ASSIGNED, "octocat", IssueStateFilter.OPEN)
        }
        val created = createdJob.await().getOrThrow()
        val assigned = assignedJob.await().getOrThrow()

        assertEquals(1, created.size)
        assertEquals(0, assigned.size)

        val peekedCreated = repo.peekMyIssuesAndPulls(IssueFilterPreset.CREATED, "octocat", IssueStateFilter.OPEN)
        assertNotNull(peekedCreated)
        assertEquals(1, peekedCreated.size)
        assertEquals("Created issue stays cached", peekedCreated[0].title)
        assertEquals(0, repo.peekMyIssuesAndPulls(IssueFilterPreset.ASSIGNED, "octocat", IssueStateFilter.OPEN)?.size)
    }

    @Test
    fun notificationsCacheKeepsUnreadAllAndParticipatingIsolated() = runTest {
        val engine = MockEngine { request ->
            val all = request.url.parameters["all"] == "true"
            val participating = request.url.parameters["participating"] == "true"
            val body = when {
                all -> """[{"id":"n_all","unread":false,"reason":"subscribed","updated_at":"2026-11-15T10:00:00Z","subject":{"title":"All item","type":"Issue","url":null},"repository":{"name":"r","full_name":"o/r","owner":{"login":"o"}}}]"""
                participating -> """[]"""
                else -> """[{"id":"n_unread","unread":true,"reason":"mention","updated_at":"2026-11-15T10:00:00Z","subject":{"title":"Unread ping","type":"PullRequest","url":null},"repository":{"name":"r","full_name":"o/r","owner":{"login":"o"}}}]"""
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val sessionManager = SessionManager(WarmupTestTokenStore(), DeviceFlowClient(HttpClient(engine)))
        sessionManager.initialize()
        val repo = GitHubRepository(sessionManager, httpClient = createClient(engine))

        val unread = repo.getNotifications(all = false, participating = false).getOrThrow()
        val allList = repo.getNotifications(all = true, participating = false).getOrThrow()
        val partList = repo.getNotifications(all = false, participating = true).getOrThrow()

        assertEquals(listOf("n_unread"), unread.map { it.id })
        assertEquals(listOf("n_all"), allList.map { it.id })
        assertEquals(0, partList.size)
        assertEquals(listOf("n_unread"), repo.peekNotifications(all = false, participating = false)?.map { it.id })
    }
}
