package dev.maxxing.github.network

import dev.maxxing.github.auth.DeviceFlowClient
import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.auth.TokenStore
import dev.maxxing.github.model.AuthSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
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

private class InterceptorTestTokenStore(var session: AuthSession?) : TokenStore {
    override suspend fun saveSession(session: AuthSession) { this.session = session }
    override suspend fun loadSession() = session
    override suspend fun clearSession() { session = null }
    override suspend fun saveClientId(clientId: String) {}
    override suspend fun loadClientId(): String? = "Iv1.app"
    override suspend fun saveThemePreferences(prefs: ThemePreferences) {}
    override suspend fun loadThemePreferences() = ThemePreferences()
    override suspend fun saveNotificationPreferences(prefs: NotificationPreferences) {}
    override suspend fun loadNotificationPreferences() = NotificationPreferences()
    override suspend fun saveSeenNotificationMarkers(markers: Map<String, String>) {}
    override suspend fun loadSeenNotificationMarkers(): Map<String, String> = emptyMap()
}

class SessionAuthInterceptorTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    /** One engine plays both github.com (token refresh) and api.github.com (which accepts only [validToken]). */
    private fun engine(validToken: String, seenAuth: MutableList<String?>) = MockEngine { request ->
        if (request.url.encodedPath == "/login/oauth/access_token") {
            respond(
                """{"access_token":"ghu_new","token_type":"bearer","expires_in":28800,"refresh_token":"ghr_new"}""",
                HttpStatusCode.OK,
                jsonHeaders
            )
        } else {
            val auth = request.headers[HttpHeaders.Authorization]
            seenAuth += auth
            if (auth == "Bearer $validToken") {
                respond("""{"login":"octocat"}""", HttpStatusCode.OK, jsonHeaders)
            } else {
                respond("""{"message":"Bad credentials"}""", HttpStatusCode.Unauthorized, jsonHeaders)
            }
        }
    }

    private suspend fun setUp(session: AuthSession, engine: MockEngine): Pair<SessionManager, HttpClient> {
        val authHttp = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val manager = SessionManager(InterceptorTestTokenStore(session), DeviceFlowClient(authHttp))
        manager.initialize()
        val api = HttpClient(engine).apply { installSessionAuth(manager, "https://api.github.com") }
        return manager to api
    }

    @Test
    fun expiredTokenIsRefreshedAndTheRequestRetriedOnce() = runTest {
        val seenAuth = mutableListOf<String?>()
        val (manager, api) = setUp(
            AuthSession("ghu_old", refreshToken = "ghr_old"),
            engine(validToken = "ghu_new", seenAuth)
        )

        val response = api.get("https://api.github.com/user")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf<String?>("Bearer ghu_old", "Bearer ghu_new"), seenAuth)
        assertEquals("ghu_new", manager.session.value?.accessToken)
    }

    @Test
    fun revokedTokenWithoutRefreshEndsTheSession() = runTest {
        val seenAuth = mutableListOf<String?>()
        val (manager, api) = setUp(AuthSession("gho_revoked"), engine(validToken = "other", seenAuth))

        val response = api.get("https://api.github.com/user")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(listOf<String?>("Bearer gho_revoked"), seenAuth)
        assertNull(manager.session.value)
        assertNotNull(manager.sessionNotice.value)
    }

    @Test
    fun unauthorizedFromAnotherHostDoesNotEndTheSession() = runTest {
        val seenAuth = mutableListOf<String?>()
        val (manager, api) = setUp(AuthSession("gho_ok"), engine(validToken = "other", seenAuth))

        val response = api.get("https://objects.example.com/asset")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("gho_ok", manager.session.value?.accessToken)
    }

    @Test
    fun validTokenIsSentUntouched() = runTest {
        val seenAuth = mutableListOf<String?>()
        val (manager, api) = setUp(AuthSession("gho_ok"), engine(validToken = "gho_ok", seenAuth))

        assertEquals(HttpStatusCode.OK, api.get("https://api.github.com/user").status)
        assertEquals(listOf<String?>("Bearer gho_ok"), seenAuth)
        assertEquals("gho_ok", manager.session.value?.accessToken)
    }
}
