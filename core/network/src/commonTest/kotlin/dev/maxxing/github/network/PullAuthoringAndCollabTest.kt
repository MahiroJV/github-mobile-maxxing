package dev.maxxing.github.network

import dev.maxxing.github.auth.DeviceFlowClient
import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.auth.TokenStore
import dev.maxxing.github.model.AuthSession
import dev.maxxing.github.model.TimelineEvent
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class PullCollabTestTokenStore : TokenStore {
    var session: AuthSession? = AuthSession("tok_collab", userLogin = "octocat")
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

class PullAuthoringAndCollabTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
    }

    @Test
    fun prCreationDraftToggleReviewersDiffCommentsReactionsAndCommentEditsWork() = runTest {
        var isDraftNow = true
        var thumbsUpCount = 1
        var viewerReacted = false
        var commentBody = "Needs minor refactor"
        var commentDeleted = false
        var requestedReviewersSeen: List<String> = emptyList()
        var diffLineCommentSeen: String? = null
        var diffFileCommentSeen: String? = null

        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            val raw = (request.body as? TextContent)?.text.orEmpty()
            when {
                path == "/repos/octocat/app/pulls" && request.method == HttpMethod.Post -> {
                    val obj = json.parseToJsonElement(raw).jsonObject
                    assertEquals("feat/pr", obj["head"]?.jsonPrimitive?.content)
                    assertEquals("main", obj["base"]?.jsonPrimitive?.content)
                    assertEquals(true, obj["draft"]?.jsonPrimitive?.boolean)
                    respond(
                        content = """{ "number": 77 }""",
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                path == "/repos/octocat/app/pulls/77/requested_reviewers" && request.method == HttpMethod.Post -> {
                    val obj = json.parseToJsonElement(raw).jsonObject
                    requestedReviewersSeen = obj["reviewers"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                    respond("{}", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
                }

                path == "/repos/octocat/app/pulls/77/comments" && request.method == HttpMethod.Post -> {
                    val obj = json.parseToJsonElement(raw).jsonObject
                    if (obj.containsKey("line")) {
                        diffLineCommentSeen = "${obj["path"]?.jsonPrimitive?.content}:${obj["line"]?.jsonPrimitive?.int}:${obj["side"]?.jsonPrimitive?.content}"
                    } else {
                        diffFileCommentSeen = "${obj["path"]?.jsonPrimitive?.content}:${obj["subject_type"]?.jsonPrimitive?.content}"
                    }
                    respond("{}", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
                }

                path == "/graphql" && request.method == HttpMethod.Post -> {
                    val reqObj = json.parseToJsonElement(raw).jsonObject
                    val query = reqObj["query"]?.jsonPrimitive?.content.orEmpty()
                    val vars = reqObj["variables"]?.jsonObject

                    when {
                        query.contains("markPullRequestReadyForReview") -> {
                            isDraftNow = false
                            respond("""{ "data": { "markPullRequestReadyForReview": { "pullRequest": { "id": "PR_77", "isDraft": false } } } }""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                        }
                        query.contains("convertPullRequestToDraft") -> {
                            isDraftNow = true
                            respond("""{ "data": { "convertPullRequestToDraft": { "pullRequest": { "id": "PR_77", "isDraft": true } } } }""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                        }
                        query.contains("addReaction") -> {
                            viewerReacted = true
                            thumbsUpCount++
                            respond("""{ "data": { "addReaction": { "reaction": { "content": "THUMBS_UP" } } } }""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                        }
                        query.contains("removeReaction") -> {
                            viewerReacted = false
                            thumbsUpCount--
                            respond("""{ "data": { "removeReaction": { "reaction": { "content": "THUMBS_UP" } } } }""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                        }
                        query.contains("updateIssueComment") -> {
                            commentBody = vars?.get("body")?.jsonPrimitive?.content.orEmpty()
                            respond("""{ "data": { "updateIssueComment": { "issueComment": { "id": "IC_1" } } } }""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                        }
                        query.contains("deleteIssueComment") -> {
                            commentDeleted = true
                            respond("""{ "data": { "deleteIssueComment": { "clientMutationId": null } } }""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                        }
                        query.contains("issueOrPullRequest(number: 77)") -> {
                            val commentNode = if (commentDeleted) "" else {
                                """
                                {
                                  "__typename": "IssueComment",
                                  "id": "IC_1",
                                  "createdAt": "2026-11-15T12:00:00Z",
                                  "body": "$commentBody",
                                  "authorAssociation": "OWNER",
                                  "viewerCanUpdate": true,
                                  "viewerCanDelete": true,
                                  "author": { "login": "octocat", "avatarUrl": "", "url": "" },
                                  "reactionGroups": [
                                    { "content": "THUMBS_UP", "viewerHasReacted": $viewerReacted, "reactors": { "totalCount": $thumbsUpCount } }
                                  ]
                                }
                                """.trimIndent()
                            }
                            respond(
                                content = """
                                    {
                                      "data": {
                                        "repository": {
                                          "viewerPermission": "ADMIN",
                                          "issueOrPullRequest": {
                                            "__typename": "PullRequest",
                                            "id": "PR_77",
                                            "number": 77,
                                            "title": "Add PR collab",
                                            "body": "Body text",
                                            "state": "OPEN",
                                            "isDraft": $isDraftNow,
                                            "url": "https://github.com/octocat/app/pull/77",
                                            "viewerCanUpdate": true,
                                            "createdAt": "2026-11-15T11:50:00Z",
                                            "updatedAt": "2026-11-15T12:00:00Z",
                                            "headRefName": "feat/pr",
                                            "headRefOid": "sha999deadbeef",
                                            "baseRefName": "main",
                                            "additions": 10,
                                            "deletions": 2,
                                            "changedFiles": 1,
                                            "mergeable": "MERGEABLE",
                                            "reviewDecision": null,
                                            "author": { "login": "octocat", "avatarUrl": "", "url": "" },
                                            "labels": { "nodes": [] },
                                            "assignees": { "nodes": [] },
                                            "reviewRequests": {
                                              "nodes": [
                                                { "requestedReviewer": { "login": "hubot", "avatarUrl": "", "url": "" } }
                                              ]
                                            },
                                            "milestone": null,
                                            "reactionGroups": [
                                              { "content": "ROCKET", "viewerHasReacted": true, "reactors": { "totalCount": 3 } }
                                            ],
                                            "commits": { "nodes": [] },
                                            "timelineItems": { "nodes": [$commentNode] }
                                          }
                                        }
                                      }
                                    }
                                """.trimIndent(),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }
                        else -> error("Unexpected GraphQL query: $query")
                    }
                }

                else -> respond("Not found: $path", HttpStatusCode.NotFound)
            }
        }

        val http = createClient(engine)
        val sessionManager = SessionManager(PullCollabTestTokenStore(), DeviceFlowClient(http))
        sessionManager.initialize()
        val repo = GitHubRepository(sessionManager, httpClient = http)

        val prNumber = repo.createPullRequest("octocat", "app", "Add PR collab", "Body", "feat/pr", "main", draft = true).getOrThrow()
        assertEquals(77, prNumber)

        val d1 = repo.getIssueOrPullDetail("octocat", "app", 77).getOrThrow()
        assertTrue(d1.isDraft)
        assertEquals("sha999deadbeef", d1.headRefOid)
        assertEquals(listOf("hubot"), d1.requestedReviewers.map { it.login })
        assertEquals(1, d1.reactions.size)
        assertEquals("ROCKET", d1.reactions[0].content)
        val c1 = d1.timeline.filterIsInstance<TimelineEvent.CommentEvent>().first()
        assertTrue(c1.viewerCanUpdate)
        assertTrue(c1.viewerCanDelete)

        repo.setPullRequestDraftState("octocat", "app", 77, "PR_77", draft = false).getOrThrow()
        assertNull(repo.peekIssueOrPullDetail("octocat", "app", 77))
        val d2 = repo.getIssueOrPullDetail("octocat", "app", 77).getOrThrow()
        assertFalse(d2.isDraft)

        repo.requestPullReviewers("octocat", "app", 77, listOf("alice", "bob")).getOrThrow()
        assertEquals(listOf("alice", "bob"), requestedReviewersSeen)

        repo.createPullDiffComment("octocat", "app", 77, "sha999deadbeef", "src/Main.kt", "Nit", line = 42, side = "RIGHT").getOrThrow()
        assertEquals("src/Main.kt:42:RIGHT", diffLineCommentSeen)
        repo.createPullDiffComment("octocat", "app", 77, "sha999deadbeef", "README.md", "File note", line = null).getOrThrow()
        assertEquals("README.md:file", diffFileCommentSeen)

        val addedReact = repo.toggleReaction("octocat", "app", 77, "IC_1", "THUMBS_UP", currentlyReacted = false).getOrThrow()
        assertTrue(addedReact)
        assertEquals(2, thumbsUpCount)
        val removedReact = repo.toggleReaction("octocat", "app", 77, "IC_1", "THUMBS_UP", currentlyReacted = true).getOrThrow()
        assertFalse(removedReact)
        assertEquals(1, thumbsUpCount)

        repo.updateIssueComment("octocat", "app", 77, "IC_1", "Updated comment body").getOrThrow()
        val d3 = repo.getIssueOrPullDetail("octocat", "app", 77).getOrThrow()
        assertEquals("Updated comment body", d3.timeline.filterIsInstance<TimelineEvent.CommentEvent>().first().body)

        repo.deleteIssueComment("octocat", "app", 77, "IC_1").getOrThrow()
        val d4 = repo.getIssueOrPullDetail("octocat", "app", 77).getOrThrow()
        assertTrue(d4.timeline.filterIsInstance<TimelineEvent.CommentEvent>().isEmpty())
    }
}
