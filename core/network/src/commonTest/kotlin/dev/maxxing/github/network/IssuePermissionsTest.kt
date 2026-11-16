package dev.maxxing.github.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IssuePermissionsTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun mockGraphQl(responsePayload: String): GitHubGraphQlService {
        val engine = MockEngine {
            respond(
                content = responsePayload,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        return GitHubGraphQlService(client)
    }

    @Test
    fun collaboratorWritePermissionGrantsCloseMergeAndMetadata() = runTest {
        val gql = mockGraphQl(
            """
            {
              "data": {
                "repository": {
                  "viewerPermission": "WRITE",
                  "issueOrPullRequest": {
                    "__typename": "PullRequest",
                    "id": "PR_1",
                    "number": 42,
                    "title": "Add dark mode",
                    "body": "Body",
                    "state": "OPEN",
                    "isDraft": false,
                    "url": "https://github.com/org/app/pull/42",
                    "viewerCanUpdate": true,
                    "createdAt": "2026-11-15T10:00:00Z",
                    "updatedAt": "2026-11-15T10:00:00Z",
                    "headRefName": "feat/dark",
                    "baseRefName": "main",
                    "additions": 10,
                    "deletions": 2,
                    "changedFiles": 1,
                    "mergeable": "MERGEABLE",
                    "reviewDecision": "APPROVED",
                    "author": { "login": "alice", "avatarUrl": "", "url": "" },
                    "labels": { "nodes": [] },
                    "assignees": { "nodes": [] },
                    "milestone": null,
                    "commits": { "nodes": [] },
                    "timelineItems": { "nodes": [] }
                  }
                }
              }
            }
            """.trimIndent()
        )
        val detail = gql.fetchIssueOrPullDetail("org", "app", 42)
        assertEquals("WRITE", detail.viewerPermission)
        assertTrue(detail.viewerCanUpdate)
        assertTrue(detail.viewerCanCloseOrReopen)
        assertTrue(detail.viewerCanMerge)
        assertTrue(detail.viewerCanEditMetadata)
    }

    @Test
    fun triagePermissionGrantsCloseAndMetadataWithoutMerge() = runTest {
        val gql = mockGraphQl(
            """
            {
              "data": {
                "repository": {
                  "viewerPermission": "TRIAGE",
                  "issueOrPullRequest": {
                    "__typename": "PullRequest",
                    "id": "PR_2",
                    "number": 43,
                    "title": "Fix bug",
                    "body": "",
                    "state": "OPEN",
                    "isDraft": false,
                    "url": "https://github.com/org/app/pull/43",
                    "viewerCanUpdate": false,
                    "createdAt": "2026-11-15T10:00:00Z",
                    "updatedAt": "2026-11-15T10:00:00Z",
                    "author": { "login": "bob", "avatarUrl": "", "url": "" },
                    "labels": { "nodes": [] },
                    "assignees": { "nodes": [] },
                    "milestone": null,
                    "commits": { "nodes": [] },
                    "timelineItems": { "nodes": [] }
                  }
                }
              }
            }
            """.trimIndent()
        )
        val detail = gql.fetchIssueOrPullDetail("org", "app", 43)
        assertEquals("TRIAGE", detail.viewerPermission)
        assertTrue(detail.viewerCanCloseOrReopen)
        assertFalse(detail.viewerCanMerge)
        assertTrue(detail.viewerCanEditMetadata)
    }

    @Test
    fun authorWithReadAccessCanCloseOwnIssueButCannotMergeOrEditMetadata() = runTest {
        val gql = mockGraphQl(
            """
            {
              "data": {
                "repository": {
                  "viewerPermission": "READ",
                  "issueOrPullRequest": {
                    "__typename": "Issue",
                    "id": "I_10",
                    "number": 10,
                    "title": "Crash on startup",
                    "body": "Stacktrace",
                    "state": "OPEN",
                    "url": "https://github.com/org/app/issues/10",
                    "viewerCanUpdate": true,
                    "createdAt": "2026-11-15T10:00:00Z",
                    "updatedAt": "2026-11-15T10:00:00Z",
                    "author": { "login": "me", "avatarUrl": "", "url": "" },
                    "labels": { "nodes": [] },
                    "assignees": { "nodes": [] },
                    "milestone": null,
                    "timelineItems": { "nodes": [] }
                  }
                }
              }
            }
            """.trimIndent()
        )
        val detail = gql.fetchIssueOrPullDetail("org", "app", 10)
        assertTrue(detail.viewerCanCloseOrReopen)
        assertFalse(detail.viewerCanMerge)
        assertFalse(detail.viewerCanEditMetadata)
    }

    @Test
    fun publicVisitorCannotCloseMergeOrEditMetadata() = runTest {
        val gql = mockGraphQl(
            """
            {
              "data": {
                "repository": {
                  "viewerPermission": null,
                  "issueOrPullRequest": {
                    "__typename": "PullRequest",
                    "id": "PR_99",
                    "number": 99,
                    "title": "Upstream PR",
                    "body": "",
                    "state": "OPEN",
                    "isDraft": false,
                    "url": "https://github.com/org/app/pull/99",
                    "viewerCanUpdate": false,
                    "createdAt": "2026-11-15T10:00:00Z",
                    "updatedAt": "2026-11-15T10:00:00Z",
                    "author": { "login": "stranger", "avatarUrl": "", "url": "" },
                    "labels": { "nodes": [] },
                    "assignees": { "nodes": [] },
                    "milestone": null,
                    "commits": { "nodes": [] },
                    "timelineItems": { "nodes": [] }
                  }
                }
              }
            }
            """.trimIndent()
        )
        val detail = gql.fetchIssueOrPullDetail("org", "app", 99)
        assertNull(detail.viewerPermission)
        assertFalse(detail.viewerCanUpdate)
        assertFalse(detail.viewerCanCloseOrReopen)
        assertFalse(detail.viewerCanMerge)
        assertFalse(detail.viewerCanEditMetadata)
    }
}
