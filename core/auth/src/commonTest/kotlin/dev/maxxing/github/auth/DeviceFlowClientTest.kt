package dev.maxxing.github.auth

import dev.maxxing.github.model.AuthMethod
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
        assertTrue(requestedDeviceScope?.split(' ')?.contains("gist") == true)
        assertTrue(DeviceFlowClient.DEFAULT_OAUTH_SCOPE.split(' ').contains("gist"))
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
}
