package dev.maxxing.github.auth

import dev.maxxing.github.model.AuthMethod
import dev.maxxing.github.model.AuthSession
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private class MemoryTokenStore(var session: AuthSession?) : TokenStore {
    var clientId: String? = "Iv1.app"
    var pinned: List<String> = listOf("octocat/hello")
    override suspend fun saveSession(session: AuthSession) { this.session = session }
    override suspend fun loadSession() = session
    override suspend fun clearSession() { session = null }
    override suspend fun saveClientId(clientId: String) { this.clientId = clientId }
    override suspend fun loadClientId(): String? = clientId
    override suspend fun saveThemePreferences(prefs: ThemePreferences) {}
    override suspend fun loadThemePreferences() = ThemePreferences()
    override suspend fun saveNotificationPreferences(prefs: NotificationPreferences) {}
    override suspend fun loadNotificationPreferences() = NotificationPreferences()
    override suspend fun saveSeenNotificationMarkers(markers: Map<String, String>) {}
    override suspend fun loadSeenNotificationMarkers(): Map<String, String> = emptyMap()
    override suspend fun savePinnedRepos(repos: List<String>) { pinned = repos }
    override suspend fun loadPinnedRepos(): List<String> = pinned
}

class SessionExpiryTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private var refreshCalls = 0

    private fun deviceFlowClient(refreshBody: String, status: HttpStatusCode = HttpStatusCode.OK): DeviceFlowClient {
        val engine = MockEngine {
            refreshCalls++
            respond(refreshBody, status, jsonHeaders)
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        }
        return DeviceFlowClient(httpClient = http, nowEpochSeconds = { NOW })
    }

    private val refreshedBody =
        """{"access_token":"ghu_new","token_type":"bearer","expires_in":28800,"refresh_token":"ghr_new"}"""

    @Test
    fun rejectedTokenWithoutRefreshTokenEndsTheSessionButKeepsPins() = runTest {
        val store = MemoryTokenStore(AuthSession("ghp_pat", authMethod = AuthMethod.PERSONAL_ACCESS_TOKEN))
        val manager = SessionManager(store, deviceFlowClient("{}"), nowEpochSeconds = { NOW })
        manager.initialize()

        assertNull(manager.onTokenRejected("ghp_pat"))

        assertNull(manager.session.value)
        assertNull(store.session)
        assertNotNull(manager.sessionNotice.value)
        assertEquals(listOf("octocat/hello"), manager.pinnedRepos.value)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun rejectedTokenIsRefreshedWhenARefreshTokenExists() = runTest {
        val store = MemoryTokenStore(AuthSession("ghu_old", refreshToken = "ghr_old", userLogin = "octocat"))
        val manager = SessionManager(store, deviceFlowClient(refreshedBody), nowEpochSeconds = { NOW })
        manager.initialize()

        assertEquals("ghu_new", manager.onTokenRejected("ghu_old"))

        assertEquals("ghu_new", store.session?.accessToken)
        assertEquals("ghr_new", store.session?.refreshToken)
        assertEquals("octocat", manager.session.value?.userLogin)
        assertNull(manager.sessionNotice.value)
    }

    @Test
    fun staleRejectionReusesTheTokenAnotherRequestAlreadyRefreshed() = runTest {
        val store = MemoryTokenStore(AuthSession("ghu_old", refreshToken = "ghr_old"))
        val manager = SessionManager(store, deviceFlowClient(refreshedBody), nowEpochSeconds = { NOW })
        manager.initialize()

        assertEquals("ghu_new", manager.onTokenRejected("ghu_old"))
        assertEquals("ghu_new", manager.onTokenRejected("ghu_old"))

        assertEquals(1, refreshCalls)
    }

    @Test
    fun refusedRefreshTokenEndsTheSession() = runTest {
        val store = MemoryTokenStore(AuthSession("ghu_old", refreshToken = "ghr_old"))
        val manager = SessionManager(
            store,
            deviceFlowClient("""{"error":"bad_refresh_token"}"""),
            nowEpochSeconds = { NOW }
        )
        manager.initialize()

        assertNull(manager.onTokenRejected("ghu_old"))

        assertNull(manager.session.value)
        assertNotNull(manager.sessionNotice.value)
    }

    @Test
    fun outageDuringRefreshKeepsTheSession() = runTest {
        val store = MemoryTokenStore(AuthSession("ghu_old", refreshToken = "ghr_old"))
        val manager = SessionManager(
            store,
            deviceFlowClient("down", HttpStatusCode.ServiceUnavailable),
            nowEpochSeconds = { NOW }
        )
        manager.initialize()

        assertNull(manager.onTokenRejected("ghu_old"))

        assertEquals("ghu_old", manager.session.value?.accessToken)
        assertNull(manager.sessionNotice.value)
    }

    @Test
    fun tokenAboutToExpireIsRefreshedBeforeUse() = runTest {
        val store = MemoryTokenStore(
            AuthSession("ghu_old", refreshToken = "ghr_old", expiresAtEpochSeconds = NOW + 30)
        )
        val manager = SessionManager(store, deviceFlowClient(refreshedBody), nowEpochSeconds = { NOW })
        manager.initialize()

        assertEquals("ghu_new", manager.freshAccessToken())
        assertEquals(NOW + 28_800, manager.session.value?.expiresAtEpochSeconds)
    }

    @Test
    fun tokenWithTimeLeftOrNoExpiryIsUsedAsIs() = runTest {
        val longLived = SessionManager(
            MemoryTokenStore(AuthSession("ghu_ok", refreshToken = "ghr", expiresAtEpochSeconds = NOW + 3_600)),
            deviceFlowClient(refreshedBody),
            nowEpochSeconds = { NOW }
        ).also { it.initialize() }
        val classic = SessionManager(
            MemoryTokenStore(AuthSession("gho_classic")),
            deviceFlowClient(refreshedBody),
            nowEpochSeconds = { NOW }
        ).also { it.initialize() }

        assertEquals("ghu_ok", longLived.freshAccessToken())
        assertEquals("gho_classic", classic.freshAccessToken())
        assertEquals(0, refreshCalls)
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
