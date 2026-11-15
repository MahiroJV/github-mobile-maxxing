package dev.octoexpress.network

import dev.octoexpress.auth.DeviceFlowClient
import dev.octoexpress.auth.NotificationPreferences
import dev.octoexpress.auth.SessionManager
import dev.octoexpress.auth.ThemePreferences
import dev.octoexpress.auth.TokenStore
import dev.octoexpress.model.AuthSession
import dev.octoexpress.model.computeLanguageShares
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class InsightsTokenStore : TokenStore {
    private var session: AuthSession? = AuthSession("tok_insights", userLogin = "octocat")
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

class RepoInsightsTest {

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false })
        }
    }

    @Test
    fun `computeLanguageShares sorts descending and assigns percentages and linguist colors`() {
        val shares = computeLanguageShares(
            mapOf(
                "TypeScript" to 25_000L,
                "Kotlin" to 70_000L,
                "Shell" to 5_000L,
                "Empty" to 0L
            )
        )
        assertEquals(listOf("Kotlin", "TypeScript", "Shell"), shares.map { it.name })
        assertEquals(70.0f, shares[0].percentage)
        assertEquals("#A97BFF", shares[0].colorHex)
        assertEquals(25.0f, shares[1].percentage)
        assertEquals(5.0f, shares[2].percentage)
    }

    @Test
    fun `getRepoInsights loads languages commit pulse contributors and caches result`() = runTest {
        var hits = 0
        val engine = MockEngine { request ->
            hits++
            val path = request.url.encodedPath
            when {
                path == "/repos/octo/pulse/languages" -> respond(
                    content = """{"Kotlin":8000,"Shell":2000}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                path == "/repos/octo/pulse/stats/commit_activity" -> respond(
                    content = """[{"week":1700000000,"total":12,"days":[1,2,3,4,2,0,0]},{"week":1700604800,"total":5,"days":[0,1,2,2,0,0,0]}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                path == "/repos/octo/pulse/stats/contributors" -> respond(
                    content = """
                        [
                          {
                            "total": 4,
                            "weeks": [{"w":1700000000,"a":20,"d":5,"c":4}],
                            "author": {"login":"bob","avatar_url":"https://a/b.png","html_url":"https://github.com/bob"}
                          },
                          {
                            "total": 18,
                            "weeks": [{"w":1700000000,"a":150,"d":30,"c":18}],
                            "author": {"login":"alice","avatar_url":"https://a/a.png","html_url":"https://github.com/alice"}
                          }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected request: $path")
            }
        }

        val http = createClient(engine)
        val sm = SessionManager(InsightsTokenStore(), DeviceFlowClient(http, "cid"))
        sm.initialize()
        val repo = GitHubRepository(sm, httpClient = http)

        val insights = repo.getRepoInsights("octo", "pulse").getOrThrow()
        assertFalse(insights.statsComputing)
        assertEquals(10_000L, insights.totalLanguageBytes)
        assertEquals(listOf("Kotlin", "Shell"), insights.languages.map { it.name })
        assertEquals(17, insights.weeklyActivity.sumOf { it.total })
        assertEquals(listOf("alice", "bob"), insights.contributors.map { it.login })
        assertEquals(150, insights.contributors.first().additions)
        assertEquals(30, insights.contributors.first().deletions)

        assertNotNull(repo.peekRepoInsights("octo", "pulse"))
        val hitsBeforeCache = hits
        val cached = repo.getRepoInsights("octo", "pulse").getOrThrow()
        assertEquals(insights, cached)
        assertEquals(hitsBeforeCache, hits)
    }

    @Test
    fun `getRepoInsights handles GitHub 202 computing responses gracefully with contributor fallback`() = runTest {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/repos/octo/warm/languages" -> respond(
                    content = """{"Rust":12000}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                path == "/repos/octo/warm/stats/commit_activity" -> respond(
                    content = """{}""",
                    status = HttpStatusCode.Accepted,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                path == "/repos/octo/warm/stats/contributors" -> respond(
                    content = """{}""",
                    status = HttpStatusCode.Accepted,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                path == "/repos/octo/warm/contributors" -> respond(
                    content = """[{"login":"ferris","avatar_url":"https://a/f.png","html_url":"https://github.com/ferris"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected request: $path")
            }
        }

        val http = createClient(engine)
        val sm = SessionManager(InsightsTokenStore(), DeviceFlowClient(http, "cid"))
        sm.initialize()
        val repo = GitHubRepository(sm, httpClient = http)

        val insights = repo.getRepoInsights("octo", "warm").getOrThrow()
        assertTrue(insights.statsComputing)
        assertEquals(listOf("Rust"), insights.languages.map { it.name })
        assertTrue(insights.weeklyActivity.isEmpty())
        assertEquals(listOf("ferris"), insights.contributors.map { it.login })
    }
}
