package dev.octoexpress.network

import dev.octoexpress.auth.DeviceFlowClient
import dev.octoexpress.auth.NotificationPreferences
import dev.octoexpress.auth.SessionManager
import dev.octoexpress.auth.ThemePreferences
import dev.octoexpress.auth.TokenStore
import dev.octoexpress.model.AuthSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class ActionsTestTokenStore : TokenStore {
    var session: AuthSession? = AuthSession("tok_act", userLogin = "octocat")
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
    override suspend fun loadSeenNotificationMarkers() = emptyMap<String, String>()
}

class ActionsAndChecksTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
    }

    @Test
    fun workflowsRunsJobsLogsDispatchRerunCancelAndChecksWork() = runTest {
        var runStatus = "completed"
        var runConclusion: String? = "failure"
        var dispatchedRef: String? = null
        var dispatchedEnv: String? = null
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/repos/octocat/app/actions/workflows" && request.method == HttpMethod.Get -> respond(
                    content = """
                        {
                          "workflows": [
                            { "id": 101, "name": "CI Build", "path": ".github/workflows/ci.yml", "state": "active", "html_url": "https://github.com/octocat/app/actions/workflows/ci.yml" }
                          ]
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                path == "/repos/octocat/app/actions/runs" && request.method == HttpMethod.Get -> respond(
                    content = """
                        {
                          "workflow_runs": [
                            {
                              "id": 5001,
                              "run_number": 88,
                              "name": "CI Build",
                              "display_title": "feat: add actions",
                              "head_branch": "main",
                              "head_sha": "deadbeef1234",
                              "event": "push",
                              "status": "$runStatus",
                              "conclusion": ${runConclusion?.let { "\"$it\"" } ?: "null"},
                              "actor": { "login": "octocat", "avatar_url": "" },
                              "created_at": "2026-11-15T11:00:00Z",
                              "updated_at": "2026-11-15T11:02:00Z",
                              "html_url": "https://github.com/octocat/app/actions/runs/5001"
                            }
                          ]
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                path == "/repos/octocat/app/actions/runs/5001/jobs" && request.method == HttpMethod.Get -> respond(
                    content = """
                        {
                          "jobs": [
                            {
                              "id": 9001,
                              "name": "unit-tests",
                              "status": "completed",
                              "conclusion": "success",
                              "steps": [
                                { "name": "Checkout", "number": 1, "status": "completed", "conclusion": "success" },
                                { "name": "Gradle test", "number": 2, "status": "completed", "conclusion": "success" }
                              ]
                            }
                          ]
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                path == "/repos/octocat/app/actions/jobs/9001/logs" && request.method == HttpMethod.Get -> respond(
                    content = "2026-11-15T11:00:01Z Running :core:network:desktopTest\nBUILD SUCCESSFUL",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )

                path == "/repos/octocat/app/actions/runs/5001/rerun-failed-jobs" && request.method == HttpMethod.Post -> {
                    runStatus = "queued"
                    runConclusion = null
                    respond(content = "", status = HttpStatusCode.Created)
                }

                path == "/repos/octocat/app/actions/runs/5001/cancel" && request.method == HttpMethod.Post -> {
                    runStatus = "completed"
                    runConclusion = "cancelled"
                    respond(content = "", status = HttpStatusCode.Accepted)
                }

                path == "/repos/octocat/app/actions/workflows/101/dispatches" && request.method == HttpMethod.Post -> {
                    val bodyObj = json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    dispatchedRef = bodyObj["ref"]?.jsonPrimitive?.content
                    dispatchedEnv = bodyObj["inputs"]?.jsonObject?.get("env")?.jsonPrimitive?.content
                    respond(content = "", status = HttpStatusCode.NoContent)
                }

                path == "/repos/octocat/app/commits/feat%2Fci/check-runs" && request.method == HttpMethod.Get -> respond(
                    content = """
                        {
                          "check_runs": [
                            {
                              "id": 77,
                              "name": "android-release",
                              "status": "completed",
                              "conclusion": "success",
                              "app": { "name": "GitHub Actions" },
                              "output": { "title": "Build passed", "summary": "All 29 tests passed" },
                              "details_url": "https://github.com/octocat/app/runs/77"
                            }
                          ]
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                else -> error("Unexpected request: ${request.method} $path")
            }
        }

        val sessionManager = SessionManager(ActionsTestTokenStore(), DeviceFlowClient(HttpClient(engine)))
        sessionManager.initialize()
        val repo = GitHubRepository(sessionManager, httpClient = createClient(engine))

        val workflows = repo.getWorkflows("octocat", "app").getOrThrow()
        assertEquals(listOf("CI Build"), workflows.map { it.name })

        val runs1 = repo.getWorkflowRuns("octocat", "app").getOrThrow()
        assertEquals(1, runs1.size)
        assertEquals("failure", runs1[0].conclusion)

        val jobs = repo.getWorkflowRunJobs("octocat", "app", 5001L).getOrThrow()
        assertEquals(2, jobs[0].steps.size)

        val logs = repo.getJobLogs("octocat", "app", 9001L).getOrThrow()
        assertTrue(logs.contains("BUILD SUCCESSFUL"))

        repo.rerunWorkflow("octocat", "app", 5001L, failedOnly = true).getOrThrow()
        assertNull(repo.peekWorkflowRuns("octocat", "app"))

        val runs2 = repo.getWorkflowRuns("octocat", "app").getOrThrow()
        assertEquals("queued", runs2[0].status)

        repo.cancelWorkflowRun("octocat", "app", 5001L).getOrThrow()
        val runs3 = repo.getWorkflowRuns("octocat", "app").getOrThrow()
        assertEquals("cancelled", runs3[0].conclusion)

        repo.dispatchWorkflow("octocat", "app", 101L, "main", mapOf("env" to "staging")).getOrThrow()
        assertEquals("main", dispatchedRef)
        assertEquals("staging", dispatchedEnv)

        val checks = repo.getCheckRunsForRef("octocat", "app", "feat/ci").getOrThrow()
        assertEquals(1, checks.size)
        assertEquals("success", checks[0].conclusion)
        assertEquals("GitHub Actions", checks[0].appName)
    }
}
