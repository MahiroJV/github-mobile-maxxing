package dev.maxxing.github.network

import dev.maxxing.github.auth.DeviceFlowClient
import dev.maxxing.github.auth.NotificationPreferences
import dev.maxxing.github.auth.SessionManager
import dev.maxxing.github.auth.ThemePreferences
import dev.maxxing.github.auth.TokenStore
import dev.maxxing.github.model.AuthSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class ProfileTestTokenStore : TokenStore {
    var session: AuthSession? = AuthSession("tok_prof", userLogin = "octocat")
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

class UserProfileTest {

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    @Test
    fun fetchesUserAndOrganizationProfilesWithFollowState() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/users/torvalds" -> respond(
                    content = """
                        {
                          "login": "torvalds",
                          "name": "Linus Torvalds",
                          "avatar_url": "https://avatars/torvalds.png",
                          "bio": "Creator of Linux",
                          "company": "Linux Foundation",
                          "location": "Portland, OR",
                          "blog": "https://kernel.org",
                          "followers": 210000,
                          "following": 0,
                          "public_repos": 8,
                          "public_gists": 0,
                          "type": "User",
                          "created_at": "2011-09-03T15:26:22Z",
                          "html_url": "https://github.com/torvalds"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/user/following/torvalds" -> respond(
                    content = "",
                    status = HttpStatusCode.NoContent
                )
                "/users/JetBrains" -> respond(
                    content = """
                        {
                          "login": "JetBrains",
                          "name": "JetBrains",
                          "avatar_url": "https://avatars/jb.png",
                          "type": "Organization",
                          "followers": 50000,
                          "public_repos": 400,
                          "html_url": "https://github.com/JetBrains"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/users/nobody-404" -> respond(
                    content = """{"message":"Not Found"}""",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }

        val rest = GitHubRestService(createClient(engine))
        val linus = rest.fetchUserProfileRest("torvalds")
        assertEquals("Linus Torvalds", linus.name)
        assertFalse(linus.isOrganization)
        assertTrue(linus.viewerIsFollowing)
        assertEquals("https://kernel.org", linus.blogUrl)

        val jb = rest.fetchUserProfileRest("JetBrains")
        assertTrue(jb.isOrganization)
        assertFalse(jb.viewerIsFollowing)
        assertEquals(400, jb.publicReposCount)

        val err = assertFailsWith<GitHubApiException> {
            rest.fetchUserProfileRest("nobody-404")
        }
        assertEquals(404, err.statusCode)
    }

    @Test
    fun followUnfollowReposOrgsAndFollowersWorkWithCacheInvalidation() = runTest {
        var followingState = false
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/users/alice" -> respond(
                    content = """{"login":"alice","type":"User","followers":10,"following":5,"public_repos":3}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/user/following/alice" -> when (request.method) {
                    HttpMethod.Get -> respond(
                        content = "",
                        status = if (followingState) HttpStatusCode.NoContent else HttpStatusCode.NotFound
                    )
                    HttpMethod.Put -> {
                        followingState = true
                        respond("", HttpStatusCode.NoContent)
                    }
                    HttpMethod.Delete -> {
                        followingState = false
                        respond("", HttpStatusCode.NoContent)
                    }
                    else -> error("Unexpected method")
                }
                "/users/alice/repos" -> respond(
                    content = """[{"id":1,"name":"demo","full_name":"alice/demo","owner":{"login":"alice"}}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/orgs/my-org/repos" -> respond(
                    content = """[{"id":2,"name":"org-sdk","full_name":"my-org/org-sdk","owner":{"login":"my-org"}}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/users/alice/orgs" -> respond(
                    content = """[{"login":"my-org","avatar_url":"https://a","description":"Org desc"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/users/alice/followers" -> respond(
                    content = """[{"login":"bob","avatar_url":"https://b","html_url":"https://github.com/bob"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/users/alice/following" -> respond(
                    content = """[{"login":"carol","avatar_url":"https://c","html_url":"https://github.com/carol"}]""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }

        val sessionManager = SessionManager(
            tokenStore = ProfileTestTokenStore(),
            deviceFlowClient = DeviceFlowClient(HttpClient(engine))
        )
        sessionManager.initialize()
        val repo = GitHubRepository(sessionManager, httpClient = createClient(engine))

        val initial = repo.getUserProfile("alice").getOrThrow()
        assertFalse(initial.viewerIsFollowing)

        repo.setFollowingUser("alice", true).getOrThrow()
        assertNull(repo.peekUserProfile("alice"))
        assertTrue(repo.getUserProfile("alice").getOrThrow().viewerIsFollowing)

        repo.setFollowingUser("alice", false).getOrThrow()
        assertFalse(repo.getUserProfile("alice").getOrThrow().viewerIsFollowing)

        assertEquals("alice/demo", repo.getUserRepositories("alice", isOrganization = false).getOrThrow()[0].fullName)
        assertEquals("my-org/org-sdk", repo.getUserRepositories("my-org", isOrganization = true).getOrThrow()[0].fullName)
        assertEquals("my-org", repo.getUserOrganizations("alice").getOrThrow()[0].login)
        assertEquals("bob", repo.getUserFollowers("alice").getOrThrow()[0].login)
        assertEquals("carol", repo.getUserFollowing("alice").getOrThrow()[0].login)
    }

    @Test
    fun followNormalizesAtPrefixAnd404SurfacesScopeGuidance() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/user/following/alice" -> respond("", HttpStatusCode.NoContent)
                "/user/following/bob" -> respond(
                    content = """{"message":"Not Found"}""",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
        }
        val sessionManager = SessionManager(
            tokenStore = ProfileTestTokenStore(),
            deviceFlowClient = DeviceFlowClient(HttpClient(engine))
        )
        sessionManager.initialize()
        val repo = GitHubRepository(sessionManager, httpClient = createClient(engine))

        repo.setFollowingUser(" @alice ", true).getOrThrow()

        val failure = repo.setFollowingUser("@bob", true).exceptionOrNull()
        assertTrue(failure is GitHubApiException)
        assertEquals(404, failure.statusCode)
        assertTrue(failure.message.contains("user:follow"))
    }
}
