package dev.octoexpress.model

import kotlinx.serialization.Serializable

@Serializable
enum class AuthMethod {
    DEVICE_FLOW,
    PERSONAL_ACCESS_TOKEN
}

@Serializable
data class AuthSession(
    val accessToken: String,
    val tokenType: String = "bearer",
    val scope: String = "",
    val authMethod: AuthMethod = AuthMethod.DEVICE_FLOW,
    val userLogin: String? = null
)

sealed interface DeviceCodeState {
    data object Idle : DeviceCodeState
    data object RequestingCode : DeviceCodeState
    data class AwaitingUser(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresInSeconds: Int,
        val intervalSeconds: Int,
        val statusMessage: String? = null
    ) : DeviceCodeState

    data class Verifying(val message: String = "Signing in...") : DeviceCodeState
    data class Authenticated(val session: AuthSession) : DeviceCodeState
    data class Error(val message: String, val isRecoverable: Boolean = true) : DeviceCodeState
}

@Serializable
data class RateLimitInfo(
    val limit: Int = 5000,
    val remaining: Int = 5000,
    val resetEpochSeconds: Long = 0L
)
