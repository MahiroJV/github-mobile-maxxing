package dev.maxxing.github.auth

import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.DeviceCodeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

class SessionManager(
    private val tokenStore: TokenStore = createPlatformTokenStore(),
    private val deviceFlowClient: DeviceFlowClient = DeviceFlowClient(),
    private val defaultClientId: String = "",
    private val nowEpochSeconds: () -> Long = { Clock.System.now().epochSeconds }
) {
    private val refreshMutex = Mutex()

    private val _sessionNotice = MutableStateFlow<String?>(null)

    /** Why the user was signed out without asking (expired or revoked token), shown on the sign-in screen. */
    val sessionNotice: StateFlow<String?> = _sessionNotice.asStateFlow()

    private val _storageWarning = MutableStateFlow<String?>(null)

    /** Set when the platform could not store the session in a secure keystore. */
    val storageWarning: StateFlow<String?> = _storageWarning.asStateFlow()

    private val _session = MutableStateFlow<AuthSession?>(null)
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    private val _clientId = MutableStateFlow(defaultClientId)
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    private val _themePreferences = MutableStateFlow(ThemePreferences())
    val themePreferences: StateFlow<ThemePreferences> = _themePreferences.asStateFlow()

    private val _notificationPreferences = MutableStateFlow(NotificationPreferences())
    val notificationPreferences: StateFlow<NotificationPreferences> = _notificationPreferences.asStateFlow()

    private val _pinnedRepos = MutableStateFlow<List<String>>(emptyList())
    val pinnedRepos: StateFlow<List<String>> = _pinnedRepos.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    suspend fun initialize() {
        val savedClientId = tokenStore.loadClientId()
        if (!savedClientId.isNullOrBlank()) {
            _clientId.value = savedClientId
        }
        _themePreferences.value = tokenStore.loadThemePreferences()
        _notificationPreferences.value = tokenStore.loadNotificationPreferences()
        _pinnedRepos.value = tokenStore.loadPinnedRepos()
            .map { it.trim() }
            .filter { '/' in it }
            .distinct()
        _session.value = tokenStore.loadSession()
        _storageWarning.value = tokenStore.sessionStorageWarning()
        _isInitialized.value = true
    }

    private suspend fun storeSession(session: AuthSession) {
        tokenStore.saveSession(session)
        _session.value = session
        _storageWarning.value = tokenStore.sessionStorageWarning()
    }

    /**
     * The access token to send with a request, refreshed first when it is about to expire.
     * Returns null when signed out.
     */
    suspend fun freshAccessToken(): String? {
        val current = _session.value ?: return null
        if (current.refreshToken == null || !current.expiresWithin(nowEpochSeconds(), EXPIRY_MARGIN_SECONDS)) {
            return current.accessToken
        }
        return refreshMutex.withLock {
            val latest = _session.value ?: return@withLock null
            if (latest.accessToken != current.accessToken) return@withLock latest.accessToken
            when (val result = deviceFlowClient.refreshAccessToken(_clientId.value, latest)) {
                is TokenRefreshResult.Refreshed -> {
                    storeSession(result.session)
                    result.session.accessToken
                }
                // Let the request go out with the old token; a 401 will settle it.
                is TokenRefreshResult.Unavailable -> latest.accessToken
                is TokenRefreshResult.Rejected -> {
                    expireSession()
                    null
                }
            }
        }
    }

    /**
     * Call when GitHub answers 401 to a request sent with [rejectedToken]. Returns a replacement
     * token to retry with, or null when there is none (the session was ended, or GitHub could not
     * be reached to refresh it).
     */
    suspend fun onTokenRejected(rejectedToken: String): String? = refreshMutex.withLock {
        val current = _session.value ?: return@withLock null
        // Another request already replaced the token while this one was in flight.
        if (current.accessToken != rejectedToken) return@withLock current.accessToken
        if (current.refreshToken != null) {
            when (val result = deviceFlowClient.refreshAccessToken(_clientId.value, current)) {
                is TokenRefreshResult.Refreshed -> {
                    storeSession(result.session)
                    return@withLock result.session.accessToken
                }
                is TokenRefreshResult.Unavailable -> return@withLock null
                is TokenRefreshResult.Rejected -> Unit
            }
        }
        expireSession()
        null
    }

    private suspend fun expireSession() {
        tokenStore.clearSession()
        _sessionNotice.value = "Your GitHub session expired or was revoked. Please sign in again."
        _session.value = null
    }

    fun dismissSessionNotice() {
        _sessionNotice.value = null
    }

    suspend fun updateClientId(newClientId: String) {
        val trimmed = newClientId.trim()
        _clientId.value = trimmed
        tokenStore.saveClientId(trimmed)
    }

    fun startDeviceFlow(clientIdOverride: String? = null): Flow<DeviceCodeState> {
        val targetId = (clientIdOverride ?: _clientId.value).trim()
        return deviceFlowClient.startDeviceFlow(targetId).onEach { state ->
            if (state is DeviceCodeState.Authenticated) {
                _sessionNotice.value = null
                storeSession(state.session)
                if (targetId.isNotEmpty()) {
                    _clientId.value = targetId
                    tokenStore.saveClientId(targetId)
                }
            }
        }
    }

    suspend fun signInWithPat(token: String): Result<AuthSession> {
        return deviceFlowClient.verifyPersonalAccessToken(token).onSuccess { verified ->
            _sessionNotice.value = null
            storeSession(verified)
        }
    }

    suspend fun updateThemePreferences(prefs: ThemePreferences) {
        _themePreferences.value = prefs
        tokenStore.saveThemePreferences(prefs)
    }

    suspend fun updateNotificationPreferences(prefs: NotificationPreferences) {
        _notificationPreferences.value = prefs
        tokenStore.saveNotificationPreferences(prefs)
    }

    suspend fun loadSeenNotificationMarkers(): Map<String, String> =
        tokenStore.loadSeenNotificationMarkers()

    suspend fun saveSeenNotificationMarkers(markers: Map<String, String>) {
        tokenStore.saveSeenNotificationMarkers(markers)
    }

    suspend fun togglePinnedRepo(fullName: String): Boolean {
        val normalized = fullName.trim()
        if ('/' !in normalized) return false
        val current = _pinnedRepos.value
        val existingIdx = current.indexOfFirst { it.equals(normalized, ignoreCase = true) }
        val next: List<String>
        val nowPinned: Boolean
        if (existingIdx >= 0) {
            next = current.toMutableList().apply { removeAt(existingIdx) }
            nowPinned = false
        } else {
            next = (listOf(normalized) + current).take(24)
            nowPinned = true
        }
        _pinnedRepos.value = next
        tokenStore.savePinnedRepos(next)
        return nowPinned
    }

    suspend fun signOut() {
        tokenStore.clearSession()
        tokenStore.saveSeenNotificationMarkers(emptyMap())
        tokenStore.savePinnedRepos(emptyList())
        _pinnedRepos.value = emptyList()
        _sessionNotice.value = null
        _storageWarning.value = null
        _session.value = null
    }

    private companion object {
        const val EXPIRY_MARGIN_SECONDS = 60L
    }
}
