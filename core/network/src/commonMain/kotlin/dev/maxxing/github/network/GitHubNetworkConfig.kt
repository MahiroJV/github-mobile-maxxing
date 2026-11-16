package dev.maxxing.github.network

import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.model.AppBranding
import dev.maxxing.github.model.RateLimitInfo
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class GitHubApiException(
    val statusCode: Int,
    override val message: String
) : Exception(message)

class GitHubNetworkConfig(
    private val sessionManager: SessionManager,
    val baseUrl: String = "https://api.github.com"
) {
    private val _rateLimit = MutableStateFlow(RateLimitInfo())
    val rateLimit: StateFlow<RateLimitInfo> = _rateLimit.asStateFlow()

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun createHttpClient(): HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(DefaultRequest) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            header(HttpHeaders.UserAgent, AppBranding.USER_AGENT)
        }
        HttpResponseValidator {
            validateResponse { response ->
                val limit = response.headers["X-RateLimit-Limit"]?.toIntOrNull()
                val remaining = response.headers["X-RateLimit-Remaining"]?.toIntOrNull()
                val reset = response.headers["X-RateLimit-Reset"]?.toLongOrNull()
                if (limit != null && remaining != null) {
                    _rateLimit.value = RateLimitInfo(
                        limit = limit,
                        remaining = remaining,
                        resetEpochSeconds = reset ?: 0L
                    )
                }
            }
        }
    }.apply { installSessionAuth(sessionManager, baseUrl) }
}

/**
 * Sends the current access token with every request (refreshing it first when it is about to
 * expire) and reacts to a 401 from the GitHub API: the token is refreshed and the request retried
 * once, or, when that is not possible, the session is ended so the app returns to sign-in.
 */
fun HttpClient.installSessionAuth(sessionManager: SessionManager, apiBaseUrl: String) {
    val apiHost = Url(apiBaseUrl).host.lowercase()
    plugin(HttpSend).intercept { request ->
        val token = if (request.headers.contains(HttpHeaders.Authorization)) {
            null
        } else {
            sessionManager.freshAccessToken()?.also { request.headers[HttpHeaders.Authorization] = "Bearer $it" }
        }
        val call = execute(request)
        if (token == null ||
            call.response.status != HttpStatusCode.Unauthorized ||
            request.url.host.lowercase() != apiHost
        ) {
            return@intercept call
        }
        val replacement = sessionManager.onTokenRejected(token) ?: return@intercept call
        request.headers[HttpHeaders.Authorization] = "Bearer $replacement"
        execute(request)
    }
}
