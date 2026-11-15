package dev.octoexpress.network

import dev.octoexpress.auth.DeviceFlowClient
import dev.octoexpress.auth.NotificationPreferences
import dev.octoexpress.auth.SessionManager
import dev.octoexpress.auth.ThemePreferences
import dev.octoexpress.auth.TokenStore
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
import kotlin.test.assertTrue

private class InMemoryTokenStore(
    initialSession: AuthSession? = AuthSession("tok_123", userLogin = "octocat")
) : TokenStore {
    var session: AuthSession? = initialSession
    var clientId: String? = null
    var themePrefs = ThemePreferences()
    var notifPrefs = NotificationPreferences()
    var markers: Map<String, String> = emptyMap()

    override suspend fun saveSession(session: AuthSession) { this.session = session }
    override suspend fun loadSession(): AuthSession? = session
    override suspend fun clearSession() { session = null }
    override suspend fun saveClientId(clientId: String) { this.clientId = clientId }
    override suspend fun loadClientId(): String? = clientId
    override suspend fun saveThemePreferences(prefs: ThemePreferences) { themePrefs = prefs }
    override suspend fun loadThemePreferences(): ThemePreferences = themePrefs
    override suspend fun saveNotificationPreferences(prefs: NotificationPreferences) { notifPrefs = prefs }
    override suspend fun loadNotificationPreferences(): NotificationPreferences = notifPrefs
    override suspend fun saveSeenNotificationMarkers(markers: Map<String, String>) { this.markers = markers }
    override suspend fun loadSeenNotificationMarkers(): Map<String, String> = markers
}

class NotificationMonitorTest {

    @Test
    fun baselineThenNewUrgentAndNonUrgentNotificationsAreFilteredCorrectly() = runTest {
        var pollRound = 0
        val engine = MockEngine {
            pollRound++
            val payload = when (pollRound) {
                1 -> """
                    [
                      {
                        "id": "t1",
                        "unread": true,
                        "reason": "subscribed",
                        "updated_at": "2026-11-15T08:00:00Z",
                        "subject": { "title": "Old thread", "type": "Issue", "url": "https://api.github.com/repos/a/b/issues/1" },
                        "repository": { "name": "b", "full_name": "a/b", "owner": { "login": "a" } }
                      }
                    ]
                """.trimIndent()
                else -> """
                    [
                      {
                        "id": "t1",
                        "unread": true,
                        "reason": "subscribed",
                        "updated_at": "2026-11-15T08:00:00Z",
                        "subject": { "title": "Old thread", "type": "Issue", "url": "https://api.github.com/repos/a/b/issues/1" },
                        "repository": { "name": "b", "full_name": "a/b", "owner": { "login": "a" } }
                      },
                      {
                        "id": "t2",
                        "unread": true,
                        "reason": "review_requested",
                        "updated_at": "2026-11-15T08:05:00Z",
                        "subject": { "title": "Need your review!", "type": "PullRequest", "url": "https://api.github.com/repos/a/b/pulls/2" },
                        "repository": { "name": "b", "full_name": "a/b", "owner": { "login": "a" } }
                      },
                      {
                        "id": "t3",
                        "unread": true,
                        "reason": "subscribed",
                        "updated_at": "2026-11-15T08:06:00Z",
                        "subject": { "title": "Noisy CI comment", "type": "Issue", "url": "https://api.github.com/repos/a/b/issues/3" },
                        "repository": { "name": "b", "full_name": "a/b", "owner": { "login": "a" } }
                      }
                    ]
                """.trimIndent()
            }
            respond(
                content = payload,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val store = InMemoryTokenStore()
        val sessionManager = SessionManager(
            tokenStore = store,
            deviceFlowClient = DeviceFlowClient(HttpClient(engine))
        )
        sessionManager.initialize()
        sessionManager.updateNotificationPreferences(
            NotificationPreferences(osNotificationsEnabled = true, urgentOnly = true)
        )

        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val repo = GitHubRepository(
            sessionManager = sessionManager,
            httpClient = httpClient
        )
        val monitor = NotificationMonitor(sessionManager, repo)

        // First poll establishes baseline without spamming OS notifications
        val baseline = monitor.checkForNewNotifications().getOrThrow()
        assertEquals(1, baseline.allUnread.size)
        assertTrue(baseline.newlyArrivedForOs.isEmpty())

        // Second poll sees t2 (review_requested) & t3 (subscribed); urgentOnly filters out t3
        val second = monitor.checkForNewNotifications().getOrThrow()
        assertEquals(3, second.allUnread.size)
        assertEquals(listOf("t2"), second.newlyArrivedForOs.map { it.id })
    }

    @Test
    fun emptyInitialInboxStillAlertsOnFirstRealNotification() = runTest {
        var pollRound = 0
        val engine = MockEngine {
            pollRound++
            val payload = if (pollRound == 1) {
                "[]"
            } else {
                """
                [
                  {
                    "id": "t_first",
                    "unread": true,
                    "reason": "mention",
                    "updated_at": "2026-11-15T09:00:00Z",
                    "subject": { "title": "Ping!", "type": "Issue", "url": "https://api.github.com/repos/a/b/issues/9" },
                    "repository": { "name": "b", "full_name": "a/b", "owner": { "login": "a" } }
                  }
                ]
                """.trimIndent()
            }
            respond(
                content = payload,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val store = InMemoryTokenStore()
        val sessionManager = SessionManager(
            tokenStore = store,
            deviceFlowClient = DeviceFlowClient(HttpClient(engine))
        )
        sessionManager.initialize()

        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val repo = GitHubRepository(sessionManager = sessionManager, httpClient = httpClient)
        val monitor = NotificationMonitor(sessionManager, repo)

        val baseline = monitor.checkForNewNotifications().getOrThrow()
        assertTrue(baseline.allUnread.isEmpty())
        assertTrue(baseline.newlyArrivedForOs.isEmpty())

        val second = monitor.checkForNewNotifications().getOrThrow()
        assertEquals(listOf("t_first"), second.newlyArrivedForOs.map { it.id })
    }
}
