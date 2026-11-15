package dev.octoexpress.network

import dev.octoexpress.model.IssueFilterPreset
import dev.octoexpress.model.IssueOrPullKind
import dev.octoexpress.model.IssueOrPullState
import dev.octoexpress.model.IssueStateFilter
import dev.octoexpress.model.NotificationReason
import dev.octoexpress.model.NotificationSubjectType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class GitHubServicesTest {

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    @Test
    fun restServiceParsesNotificationsAndFileContent() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/notifications" -> {
                    assertEquals("no-cache", request.headers[HttpHeaders.CacheControl])
                    respond(
                    content = """
                        [
                          {
                            "id": "991",
                            "unread": true,
                            "reason": "review_requested",
                            "updated_at": "2026-11-15T08:00:00Z",
                            "subject": {
                              "title": "Add MD3E Expressive Theme",
                              "type": "PullRequest",
                              "url": "https://api.github.com/repos/octo/app/pulls/42"
                            },
                            "repository": {
                              "name": "app",
                              "full_name": "octo/app",
                              "private": false,
                              "owner": { "login": "octo", "avatar_url": "https://example.com/a.png" }
                            }
                          }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                }
                "/repos/octo/app/contents/README.md" -> respond(
                    content = """
                        {
                          "name": "README.md",
                          "path": "README.md",
                          "sha": "abc",
                          "size": 12,
                          "type": "file",
                          "encoding": "base64",
                          "content": "SGVsbG8gS01QCg=="
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }

        val rest = GitHubRestService(createClient(engine))
        val notifications = rest.fetchNotifications()
        assertEquals(1, notifications.size)
        assertEquals(NotificationReason.REVIEW_REQUESTED, notifications[0].reason)
        assertEquals(NotificationSubjectType.PULL_REQUEST, notifications[0].subjectType)
        assertEquals(42, notifications[0].subjectNumber)

        val file = rest.fetchFileContent("octo", "app", "README.md")
        assertEquals("Hello KMP\n", file.content)
    }

    @Test
    fun graphQlServiceAppliesClosedAndAllStateFilters() = runTest {
        val capturedQueries = mutableListOf<String>()
        val engine = MockEngine { request ->
            val bodyText = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            capturedQueries.add(bodyText)
            respond(
                content = """{"data":{"search":{"nodes":[]},"repository":{"pullRequests":{"nodes":[]}}}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val gql = GitHubGraphQlService(createClient(engine))
        gql.fetchIssuesAndPulls(IssueFilterPreset.CREATED, "neutron42", IssueStateFilter.CLOSED)
        gql.fetchIssuesAndPulls(IssueFilterPreset.CREATED, "neutron42", IssueStateFilter.ALL)
        gql.fetchRepoIssuesAndPulls("octo", "app", pullRequests = true, stateFilter = IssueStateFilter.CLOSED)
        gql.fetchRepoIssuesAndPulls("octo", "app", pullRequests = true, stateFilter = IssueStateFilter.ALL)

        assertEquals(true, capturedQueries[0].contains("is:closed"))
        assertEquals(false, capturedQueries[1].contains("is:open"))
        assertEquals(false, capturedQueries[1].contains("is:closed"))
        assertEquals(true, capturedQueries[2].contains("[CLOSED, MERGED]"))
        assertEquals(true, capturedQueries[3].contains("[OPEN, CLOSED, MERGED]"))
    }

    @Test
    fun graphQlServiceParsesSearchResults() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/graphql") {
                respond(
                    content = """
                        {
                          "data": {
                            "search": {
                              "nodes": [
                                {
                                  "__typename": "PullRequest",
                                  "id": "PR_1",
                                  "number": 128,
                                  "title": "Implement OAuth Device Flow",
                                  "state": "OPEN",
                                  "isDraft": false,
                                  "createdAt": "2026-11-14T10:00:00Z",
                                  "updatedAt": "2026-11-15T07:00:00Z",
                                  "reviewDecision": "APPROVED",
                                  "author": { "login": "neutron42", "avatarUrl": "https://example.com/u.png", "url": "" },
                                  "repository": { "nameWithOwner": "neutron42/octo", "name": "octo", "owner": { "login": "neutron42" } },
                                  "comments": { "totalCount": 4 },
                                  "labels": { "nodes": [ { "name": "enhancement", "color": "a2eeef", "description": "New feature" } ] },
                                  "commits": { "nodes": [ { "commit": { "statusCheckRollup": { "state": "SUCCESS" } } } ] }
                                }
                              ]
                            }
                          }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                error("Unexpected path")
            }
        }

        val gql = GitHubGraphQlService(createClient(engine))
        val items = gql.fetchIssuesAndPulls(IssueFilterPreset.CREATED, "neutron42")
        assertEquals(1, items.size)
        assertEquals(IssueOrPullKind.PULL_REQUEST, items[0].kind)
        assertEquals(IssueOrPullState.OPEN, items[0].state)
        assertEquals(128, items[0].number)
        assertEquals("SUCCESS", items[0].checkRollup?.state)
        assertEquals("enhancement", items[0].labels.first().name)
    }
}
