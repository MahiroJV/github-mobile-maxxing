package dev.maxxing.github.auth

import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.AuthMethod
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.DeviceCodeState
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("interval") val interval: Int = 5
)

/** GitHub answers some failures with HTTP 200 and an `error` body, so every field is optional. */
@Serializable
private data class DeviceCodeDto(
    @SerialName("device_code") val deviceCode: String? = null,
    @SerialName("user_code") val userCode: String? = null,
    @SerialName("verification_uri") val verificationUri: String? = null,
    @SerialName("expires_in") val expiresIn: Int? = null,
    @SerialName("interval") val interval: Int? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
)

@Serializable
private data class AccessTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("scope") val scope: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("refresh_token_expires_in") val refreshTokenExpiresIn: Long? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("interval") val interval: Int? = null
)

@Serializable
private data class PatUserCheckDto(
    val login: String
)

sealed interface TokenRefreshResult {
    data class Refreshed(val session: AuthSession) : TokenRefreshResult

    /** GitHub refused the refresh token: the session is over and the user must sign in again. */
    data class Rejected(val reason: String) : TokenRefreshResult

    /** GitHub could not be reached or answered unexpectedly: the session may still be valid. */
    data class Unavailable(val reason: String) : TokenRefreshResult
}

class DeviceFlowClient(
    private val httpClient: HttpClient = defaultAuthHttpClient(),
    private val githubBaseUrl: String = "https://github.com",
    private val apiBaseUrl: String = "https://api.github.com",
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
    private val nowEpochSeconds: () -> Long = { Clock.System.now().epochSeconds }
) {
    fun startDeviceFlow(
        clientId: String,
        scope: String = DEFAULT_OAUTH_SCOPE
    ): Flow<DeviceCodeState> = flow {
        val trimmedId = clientId.trim()
        if (trimmedId.isEmpty()) {
            emit(DeviceCodeState.Error("OAuth Client ID is required."))
            return@flow
        }

        emit(DeviceCodeState.RequestingCode)

        val response = try {
            httpClient.post("$githubBaseUrl/login/device/code") {
                header(HttpHeaders.Accept, "application/json")
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("client_id", trimmedId)
                            append("scope", scope)
                        }
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(DeviceCodeState.Error("Failed to contact GitHub: ${e.message ?: "Network error"}"))
            return@flow
        }

        val dto = try {
            response.body<DeviceCodeDto>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val codeResponse = dto?.toDeviceCodeResponse()
        if (codeResponse == null) {
            emit(DeviceCodeState.Error(deviceCodeFailureMessage(response.status, dto)))
            return@flow
        }

        var intervalSec = codeResponse.interval.coerceAtLeast(1)
        var elapsedSec = 0

        emit(
            DeviceCodeState.AwaitingUser(
                deviceCode = codeResponse.deviceCode,
                userCode = codeResponse.userCode,
                verificationUri = codeResponse.verificationUri,
                expiresInSeconds = codeResponse.expiresIn,
                intervalSeconds = intervalSec,
                statusMessage = "Enter this code on GitHub to authorize ${AppBranding.APP_NAME}."
            )
        )

        while (elapsedSec < codeResponse.expiresIn) {
            delayMillis(intervalSec * 1000L)
            elapsedSec += intervalSec

            val tokenRes = try {
                httpClient.post("$githubBaseUrl/login/oauth/access_token") {
                    header(HttpHeaders.Accept, "application/json")
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("client_id", trimmedId)
                                append("device_code", codeResponse.deviceCode)
                                append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                            }
                        )
                    )
                }.body<AccessTokenResponse>()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emit(
                    DeviceCodeState.AwaitingUser(
                        deviceCode = codeResponse.deviceCode,
                        userCode = codeResponse.userCode,
                        verificationUri = codeResponse.verificationUri,
                        expiresInSeconds = (codeResponse.expiresIn - elapsedSec).coerceAtLeast(0),
                        intervalSeconds = intervalSec,
                        statusMessage = "Temporary network hiccup, retrying..."
                    )
                )
                continue
            }

            if (!tokenRes.accessToken.isNullOrBlank()) {
                emit(DeviceCodeState.Verifying("Fetching profile..."))
                val login = fetchUserLogin(tokenRes.accessToken)
                val now = nowEpochSeconds()
                val session = AuthSession(
                    accessToken = tokenRes.accessToken,
                    tokenType = tokenRes.tokenType ?: "bearer",
                    scope = tokenRes.scope ?: scope,
                    authMethod = AuthMethod.DEVICE_FLOW,
                    userLogin = login,
                    refreshToken = tokenRes.refreshToken?.takeIf { it.isNotBlank() },
                    expiresAtEpochSeconds = tokenRes.expiresIn?.let { now + it },
                    refreshTokenExpiresAtEpochSeconds = tokenRes.refreshTokenExpiresIn?.let { now + it }
                )
                emit(DeviceCodeState.Authenticated(session))
                return@flow
            }

            when (tokenRes.error) {
                "authorization_pending" -> {
                    emit(
                        DeviceCodeState.AwaitingUser(
                            deviceCode = codeResponse.deviceCode,
                            userCode = codeResponse.userCode,
                            verificationUri = codeResponse.verificationUri,
                            expiresInSeconds = (codeResponse.expiresIn - elapsedSec).coerceAtLeast(0),
                            intervalSeconds = intervalSec,
                            statusMessage = "Waiting for browser authorization..."
                        )
                    )
                }

                "slow_down" -> {
                    intervalSec = tokenRes.interval ?: (intervalSec + 5)
                    emit(
                        DeviceCodeState.AwaitingUser(
                            deviceCode = codeResponse.deviceCode,
                            userCode = codeResponse.userCode,
                            verificationUri = codeResponse.verificationUri,
                            expiresInSeconds = (codeResponse.expiresIn - elapsedSec).coerceAtLeast(0),
                            intervalSeconds = intervalSec,
                            statusMessage = "Rate limited by GitHub; polling every ${intervalSec}s..."
                        )
                    )
                }

                "expired_token" -> {
                    emit(DeviceCodeState.Error("The device code expired. Request a new code."))
                    return@flow
                }

                "access_denied" -> {
                    emit(DeviceCodeState.Error("Authorization was denied in the browser."))
                    return@flow
                }

                else -> {
                    val desc = tokenRes.errorDescription ?: tokenRes.error ?: "Unknown OAuth error"
                    emit(DeviceCodeState.Error(desc))
                    return@flow
                }
            }
        }

        emit(DeviceCodeState.Error("Device code timed out. Please try again."))
    }

    /**
     * Exchanges the session's refresh token for a new access token. Only expiring tokens
     * (GitHub Apps) carry a refresh token; GitHub rotates it on every refresh.
     */
    suspend fun refreshAccessToken(clientId: String, session: AuthSession): TokenRefreshResult {
        val refreshToken = session.refreshToken
            ?: return TokenRefreshResult.Rejected("This session has no refresh token.")
        val trimmedId = clientId.trim()
        if (trimmedId.isEmpty()) {
            return TokenRefreshResult.Rejected("OAuth Client ID is missing.")
        }
        val tokenRes = try {
            val response = httpClient.post("$githubBaseUrl/login/oauth/access_token") {
                header(HttpHeaders.Accept, "application/json")
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("client_id", trimmedId)
                            append("grant_type", "refresh_token")
                            append("refresh_token", refreshToken)
                        }
                    )
                )
            }
            if (response.status.value >= 500) {
                return TokenRefreshResult.Unavailable("GitHub returned HTTP ${response.status.value}.")
            }
            response.body<AccessTokenResponse>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return TokenRefreshResult.Unavailable(e.message ?: "Network error")
        }

        if (tokenRes.accessToken.isNullOrBlank()) {
            return TokenRefreshResult.Rejected(
                tokenRes.errorDescription ?: tokenRes.error ?: "GitHub did not issue a new token."
            )
        }
        val now = nowEpochSeconds()
        return TokenRefreshResult.Refreshed(
            session.copy(
                accessToken = tokenRes.accessToken,
                tokenType = tokenRes.tokenType ?: session.tokenType,
                scope = tokenRes.scope?.takeIf { it.isNotBlank() } ?: session.scope,
                refreshToken = tokenRes.refreshToken?.takeIf { it.isNotBlank() } ?: refreshToken,
                expiresAtEpochSeconds = tokenRes.expiresIn?.let { now + it },
                refreshTokenExpiresAtEpochSeconds = tokenRes.refreshTokenExpiresIn?.let { now + it }
                    ?: session.refreshTokenExpiresAtEpochSeconds
            )
        )
    }

    private fun DeviceCodeDto.toDeviceCodeResponse(): DeviceCodeResponse? {
        if (error != null) return null
        return DeviceCodeResponse(
            deviceCode = deviceCode?.takeIf { it.isNotBlank() } ?: return null,
            userCode = userCode?.takeIf { it.isNotBlank() } ?: return null,
            verificationUri = verificationUri?.takeIf { it.isNotBlank() } ?: return null,
            expiresIn = expiresIn ?: return null,
            interval = interval ?: 5
        )
    }

    private fun deviceCodeFailureMessage(status: HttpStatusCode, dto: DeviceCodeDto?): String = when {
        dto?.error == "device_flow_disabled" ->
            "Device Flow is not enabled for this app. Turn on \"Enable Device Flow\" in the app's settings on GitHub."
        dto?.error == "incorrect_client_credentials" || status == HttpStatusCode.NotFound ->
            "GitHub does not recognise this Client ID. Check it and try again."
        dto?.error != null -> dto.errorDescription ?: "GitHub rejected the request (${dto.error})."
        status != HttpStatusCode.OK ->
            "GitHub rejected device code request (${status.value}). Verify your Client ID."
        else -> "GitHub sent an unexpected response. Please try again."
    }

    suspend fun verifyPersonalAccessToken(rawToken: String): Result<AuthSession> {
        val token = rawToken.trim()
        if (token.isEmpty()) {
            return Result.failure(IllegalArgumentException("Token cannot be empty"))
        }
        return try {
            val response = httpClient.get("$apiBaseUrl/user") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
            }
            if (response.status != HttpStatusCode.OK) {
                Result.failure(IllegalStateException("GitHub returned HTTP ${response.status.value}. Check token scopes."))
            } else {
                val user = response.body<PatUserCheckDto>()
                val resolvedScope = response.headers["X-OAuth-Scopes"]
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: "pat"
                Result.success(
                    AuthSession(
                        accessToken = token,
                        tokenType = "bearer",
                        scope = resolvedScope,
                        authMethod = AuthMethod.PERSONAL_ACCESS_TOKEN,
                        userLogin = user.login
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchUserLogin(token: String): String? {
        return try {
            val res = httpClient.get("$apiBaseUrl/user") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }
            if (res.status == HttpStatusCode.OK) res.body<PatUserCheckDto>().login else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val DEFAULT_OAUTH_SCOPE = "repo gist notifications read:user user:follow read:org"

        fun defaultAuthHttpClient(): HttpClient = HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }
                )
            }
        }
    }
}
