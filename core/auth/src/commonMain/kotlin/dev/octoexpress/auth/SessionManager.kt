package dev.octoexpress.auth

import dev.octoexpress.model.AuthSession
import dev.octoexpress.model.DeviceCodeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach

class SessionManager(
    private val tokenStore: TokenStore = createPlatformTokenStore(),
    private val deviceFlowClient: DeviceFlowClient = DeviceFlowClient(),
    private val defaultClientId: String = ""
) {
    private val _session = MutableStateFlow<AuthSession?>(null)
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    private val _clientId = MutableStateFlow(defaultClientId)
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    private val _themePreferences = MutableStateFlow(ThemePreferences())
    val themePreferences: StateFlow<ThemePreferences> = _themePreferences.asStateFlow()

    private val _notificationPreferences = MutableStateFlow(NotificationPreferences())
    val notificationPreferences: StateFlow<NotificationPreferences> = _notificationPreferences.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    suspend fun initialize() {
        val savedClientId = tokenStore.loadClientId()
        if (!savedClientId.isNullOrBlank()) {
            _clientId.value = savedClientId
        }
        _themePreferences.value = tokenStore.loadThemePreferences()
        _notificationPreferences.value = tokenStore.loadNotificationPreferences()
        _session.value = tokenStore.loadSession()
        _isInitialized.value = true
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
                tokenStore.saveSession(state.session)
                _session.value = state.session
                if (targetId.isNotEmpty()) {
                    _clientId.value = targetId
                    tokenStore.saveClientId(targetId)
                }
            }
        }
    }

    suspend fun signInWithPat(token: String): Result<AuthSession> {
        return deviceFlowClient.verifyPersonalAccessToken(token).onSuccess { verified ->
            tokenStore.saveSession(verified)
            _session.value = verified
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

    suspend fun signOut() {
        tokenStore.clearSession()
        tokenStore.saveSeenNotificationMarkers(emptyMap())
        _session.value = null
    }
}
