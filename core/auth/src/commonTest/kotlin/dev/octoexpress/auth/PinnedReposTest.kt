package dev.octoexpress.auth

import dev.octoexpress.model.AuthSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class InMemoryPinnedTokenStore(
    initialPinned: List<String> = emptyList()
) : TokenStore {
    var session: AuthSession? = AuthSession("tok_pins", userLogin = "octocat")
    var pinnedRepos: List<String> = initialPinned

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
    override suspend fun loadSeenNotificationMarkers(): Map<String, String> = emptyMap()
    override suspend fun savePinnedRepos(repos: List<String>) { pinnedRepos = repos }
    override suspend fun loadPinnedRepos(): List<String> = pinnedRepos
}

class PinnedReposTest {
    @Test
    fun togglePinnedRepoPersistsDeduplicatesAndClearsOnSignOut() = runTest {
        val store = InMemoryPinnedTokenStore(initialPinned = listOf("octo/alpha", "invalid", "octo/alpha"))
        val http = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })
        val manager = SessionManager(store, DeviceFlowClient(http))

        manager.initialize()
        assertEquals(listOf("octo/alpha"), manager.pinnedRepos.value)

        assertTrue(manager.togglePinnedRepo("octo/beta"))
        assertEquals(listOf("octo/beta", "octo/alpha"), manager.pinnedRepos.value)
        assertEquals(listOf("octo/beta", "octo/alpha"), store.pinnedRepos)

        // Toggle off matching case-insensitively
        assertFalse(manager.togglePinnedRepo("OCTO/ALPHA"))
        assertEquals(listOf("octo/beta"), manager.pinnedRepos.value)
        assertEquals(listOf("octo/beta"), store.pinnedRepos)

        // Invalid names ignored
        assertFalse(manager.togglePinnedRepo("noslash"))
        assertEquals(listOf("octo/beta"), manager.pinnedRepos.value)

        manager.signOut()
        assertTrue(manager.pinnedRepos.value.isEmpty())
        assertTrue(store.pinnedRepos.isEmpty())
    }
}
