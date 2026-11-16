package dev.maxxing.github.auth

import dev.maxxing.github.model.AuthMethod
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.DeviceCodeState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceFlowClientTest {

    private fun createMockClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    @Test
    fun deviceFlowHandlesPendingSlowDownAndSuccess() = runTest {
        var tokenPollAttempt = 0
        val recordedDelays = mutableListOf<Long>()
        var requestedDeviceScope: String? = null
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when (path) {
                "/login/device/code" -> {
                    requestedDeviceScope = (request.body as? io.ktor.client.request.forms.FormDataContent)
                        ?.formData?.get("scope")
                    respond(
                        content = """{"device_code":"dev_123","user_code":"ABCD-EFGH","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "/login/oauth/access_token" -> {
                    tokenPollAttempt++
                    val payload = when (tokenPollAttempt) {
                        1 -> """{"error":"authorization_pending"}"""
                        2 -> """{"error":"slow_down","interval":10}"""
                        else -> """{"access_token":"gho_test_token","token_type":"bearer","scope":"repo gist notifications"}"""
                    }
                    respond(
                        content = payload,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "/user" -> respond(
                    content = """{"login":"octocat"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected path: $path")
            }
        }

        val client = DeviceFlowClient(
            httpClient = createMockClient(engine),
            delayMillis = { ms -> recordedDelays.add(ms) }
        )

        val states = client.startDeviceFlow("Ov23liTestClient").toList()

        assertIs<DeviceCodeState.RequestingCode>(states[0])
        val sentScopes = requestedDeviceScope?.split(' ').orEmpty()
        assertTrue("gist" in sentScopes)
        assertTrue("user:follow" in sentScopes)
        assertTrue("gist" in DeviceFlowClient.DEFAULT_OAUTH_SCOPE.split(' '))
        assertTrue("user:follow" in DeviceFlowClient.DEFAULT_OAUTH_SCOPE.split(' '))
        val firstAwait = assertIs<DeviceCodeState.AwaitingUser>(states[1])
        assertEquals("ABCD-EFGH", firstAwait.userCode)
        assertEquals(listOf(5000L, 5000L, 10000L), recordedDelays)

        val authed = assertIs<DeviceCodeState.Authenticated>(states.last())
        assertEquals("gho_test_token", authed.session.accessToken)
        assertEquals("octocat", authed.session.userLogin)
        assertEquals(AuthMethod.DEVICE_FLOW, authed.session.authMethod)
    }

    @Test
    fun deviceFlowHandlesAccessDenied() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/login/device/code" -> respond(
                    content = """{"device_code":"dev_123","user_code":"1234-5678","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/login/oauth/access_token" -> respond(
                    content = """{"error":"access_denied"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected path")
            }
        }

        val client = DeviceFlowClient(
            httpClient = createMockClient(engine),
            delayMillis = {}
        )
        val states = client.startDeviceFlow("Ov23liTestClient").toList()
        val errorState = assertIs<DeviceCodeState.Error>(states.last())
        assertTrue(errorState.message.contains("denied", ignoreCase = true))
    }

    @Test
    fun verifyPersonalAccessTokenChecksAuth() = runTest {
        val engine = MockEngine { request ->
            val auth = request.headers[HttpHeaders.Authorization]
            if (auth == "Bearer ghp_valid") {
                respond(
                    content = """{"login":"neutron42"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"message":"Bad credentials"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val client = DeviceFlowClient(httpClient = createMockClient(engine))
        val ok = client.verifyPersonalAccessToken("ghp_valid")
        assertTrue(ok.isSuccess)
        assertEquals("neutron42", ok.getOrThrow().userLogin)

        val bad = client.verifyPersonalAccessToken("ghp_bad")
        assertTrue(bad.isFailure)
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun deviceCodeErrorBodyWithHttp200ShowsTheRealReason() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"device_flow_disabled","error_description":"Device Flow must be explicitly enabled for this App"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }
        val states = DeviceFlowClient(createMockClient(engine), delayMillis = {})
            .startDeviceFlow("Ov23liTestClient").toList()

        val error = assertIs<DeviceCodeState.Error>(states.last())
        assertTrue(error.message.contains("Device Flow is not enabled"), error.message)
        assertTrue(!error.message.contains("Failed to contact"), error.message)
    }

    @Test
    fun unknownClientIdIsReportedAsSuch() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"Not Found"}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders
            )
        }
        val states = DeviceFlowClient(createMockClient(engine), delayMillis = {})
            .startDeviceFlow("bogus").toList()

        val error = assertIs<DeviceCodeState.Error>(states.last())
        assertTrue(error.message.contains("does not recognise this Client ID"), error.message)
    }

    @Test
    fun deviceFlowKeepsRefreshTokenAndExpiryOfExpiringTokens() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/login/device/code" -> respond(
                    content = """{"device_code":"dev_1","user_code":"ABCD-EFGH","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders
                )
                "/login/oauth/access_token" -> respond(
                    content = """{"access_token":"ghu_first","token_type":"bearer","scope":"","expires_in":28800,"refresh_token":"ghr_first","refresh_token_expires_in":15897600}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders
                )
                else -> respond("""{"login":"octocat"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }
        val states = DeviceFlowClient(
            httpClient = createMockClient(engine),
            delayMillis = {},
            nowEpochSeconds = { 1_000L }
        ).startDeviceFlow("Iv1.app").toList()

        val session = assertIs<DeviceCodeState.Authenticated>(states.last()).session
        assertEquals("ghr_first", session.refreshToken)
        assertEquals(1_000L + 28_800L, session.expiresAtEpochSeconds)
        assertEquals(1_000L + 15_897_600L, session.refreshTokenExpiresAtEpochSeconds)
    }

    @Test
    fun classicOAuthTokensHaveNoExpiry() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/login/device/code" -> respond(
                    content = """{"device_code":"dev_1","user_code":"ABCD-EFGH","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders
                )
                "/login/oauth/access_token" -> respond(
                    content = """{"access_token":"gho_x","token_type":"bearer","scope":"repo"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders
                )
                else -> respond("""{"login":"octocat"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }
        val states = DeviceFlowClient(createMockClient(engine), delayMillis = {})
            .startDeviceFlow("Ov23liTestClient").toList()

        val session = assertIs<DeviceCodeState.Authenticated>(states.last()).session
        assertNull(session.refreshToken)
        assertNull(session.expiresAtEpochSeconds)
    }

    @Test
    fun refreshRotatesTokensAndKeepsIdentity() = runTest {
        var sentForm: io.ktor.http.Parameters? = null
        val engine = MockEngine { request ->
            sentForm = (request.body as io.ktor.client.request.forms.FormDataContent).formData
            respond(
                content = """{"access_token":"ghu_second","token_type":"bearer","expires_in":28800,"refresh_token":"ghr_second","refresh_token_expires_in":100000}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }
        val client = DeviceFlowClient(createMockClient(engine), nowEpochSeconds = { 50_000L })
        val old = AuthSession(
            accessToken = "ghu_first",
            userLogin = "octocat",
            refreshToken = "ghr_first",
            expiresAtEpochSeconds = 40_000L
        )

        val result = assertIs<TokenRefreshResult.Refreshed>(client.refreshAccessToken("Iv1.app", old))

        assertEquals("refresh_token", sentForm?.get("grant_type"))
        assertEquals("ghr_first", sentForm?.get("refresh_token"))
        assertEquals("Iv1.app", sentForm?.get("client_id"))
        assertEquals("ghu_second", result.session.accessToken)
        assertEquals("ghr_second", result.session.refreshToken)
        assertEquals(78_800L, result.session.expiresAtEpochSeconds)
        assertEquals("octocat", result.session.userLogin)
    }

    @Test
    fun refreshDistinguishesRejectionFromOutage() = runTest {
        val session = AuthSession(accessToken = "ghu_first", refreshToken = "ghr_first")

        val rejecting = MockEngine {
            respond("""{"error":"bad_refresh_token","error_description":"The refresh token passed is incorrect or expired."}""", HttpStatusCode.OK, jsonHeaders)
        }
        assertIs<TokenRefreshResult.Rejected>(
            DeviceFlowClient(createMockClient(rejecting)).refreshAccessToken("Iv1.app", session)
        )

        val down = MockEngine { respond("oops", HttpStatusCode.BadGateway) }
        assertIs<TokenRefreshResult.Unavailable>(
            DeviceFlowClient(createMockClient(down)).refreshAccessToken("Iv1.app", session)
        )

        val offline = MockEngine { throw kotlinx.io.IOException("no route") }
        assertIs<TokenRefreshResult.Unavailable>(
            DeviceFlowClient(createMockClient(offline)).refreshAccessToken("Iv1.app", session)
        )
    }
}
