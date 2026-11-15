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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class ReleasesTestTokenStore : TokenStore {
    var session: AuthSession? = AuthSession("tok_rel", userLogin = "octocat")
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

class ReleasesTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
    }

    @Test
    fun releasesParseAssetsCacheAndCreateReleaseInvalidatesCaches() = runTest {
        var releasesFetches = 0
        var tagsFetches = 0
        var releaseCreated = false
        var postedPayload: String? = null

        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/repos/octocat/app/releases" && request.method == HttpMethod.Get -> {
                    releasesFetches++
                    val createdEntry = if (releaseCreated) {
                        """
                        {
                          "id": 2002,
                          "tag_name": "v2.0.0-beta.1",
                          "name": "v2.0.0 Beta 1",
                          "body": "## Beta Notes",
                          "draft": false,
                          "prerelease": true,
                          "author": { "login": "octocat", "avatar_url": "" },
                          "published_at": "2026-11-15T12:00:00Z",
                          "created_at": "2026-11-15T12:00:00Z",
                          "assets": [],
                          "tarball_url": "https://api.github.com/repos/octocat/app/tarball/v2.0.0-beta.1",
                          "zipball_url": "https://api.github.com/repos/octocat/app/zipball/v2.0.0-beta.1",
                          "html_url": "https://github.com/octocat/app/releases/tag/v2.0.0-beta.1"
                        },
                        """.trimIndent()
                    } else {
                        ""
                    }
                    respond(
                        content = """
                            [
                              $createdEntry
                              {
                                "id": 1001,
                                "tag_name": "v1.4.0",
                                "name": "Release 1.4.0",
                                "body": "## Highlights\n- Added Releases UI",
                                "draft": false,
                                "prerelease": false,
                                "author": { "login": "octocat", "avatar_url": "https://avatars.example/octo.png" },
                                "published_at": "2026-11-10T09:30:00Z",
                                "created_at": "2026-11-10T09:20:00Z",
                                "assets": [
                                  {
                                    "id": 99,
                                    "name": "octoexpress-release.apk",
                                    "size": 18432000,
                                    "download_count": 42,
                                    "browser_download_url": "https://github.com/octocat/app/releases/download/v1.4.0/octoexpress-release.apk",
                                    "content_type": "application/vnd.android.package-archive"
                                  }
                                ],
                                "tarball_url": "https://api.github.com/repos/octocat/app/tarball/v1.4.0",
                                "zipball_url": "https://api.github.com/repos/octocat/app/zipball/v1.4.0",
                                "html_url": "https://github.com/octocat/app/releases/tag/v1.4.0"
                              }
                            ]
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                path == "/repos/octocat/app/tags" && request.method == HttpMethod.Get -> {
                    tagsFetches++
                    val betaTag = if (releaseCreated) """{ "name": "v2.0.0-beta.1", "commit": { "sha": "bbb222" } },""" else ""
                    respond(
                        content = """[$betaTag{ "name": "v1.4.0", "commit": { "sha": "aaa111" } }]""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                path == "/repos/octocat/app/releases" && request.method == HttpMethod.Post -> {
                    postedPayload = (request.body as? TextContent)?.text
                    releaseCreated = true
                    respond(
                        content = """
                            {
                              "id": 2002,
                              "tag_name": "v2.0.0-beta.1",
                              "name": "v2.0.0 Beta 1",
                              "body": "## Beta Notes",
                              "draft": false,
                              "prerelease": true,
                              "author": { "login": "octocat", "avatar_url": "" },
                              "published_at": "2026-11-15T12:00:00Z",
                              "created_at": "2026-11-15T12:00:00Z",
                              "assets": [],
                              "tarball_url": "https://api.github.com/repos/octocat/app/tarball/v2.0.0-beta.1",
                              "zipball_url": "https://api.github.com/repos/octocat/app/zipball/v2.0.0-beta.1",
                              "html_url": "https://github.com/octocat/app/releases/tag/v2.0.0-beta.1"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                else -> respond("Not found: $path", HttpStatusCode.NotFound)
            }
        }

        val http = createClient(engine)
        val tokenStore = ReleasesTestTokenStore()
        val sessionManager = SessionManager(tokenStore, DeviceFlowClient(http))
        sessionManager.initialize()
        val repo = GitHubRepository(sessionManager, httpClient = http)

        assertNull(repo.peekReleases("octocat", "app"))

        val initial = repo.getReleases("octocat", "app").getOrThrow()
        assertEquals(1, initial.size)
        val r1 = initial.first()
        assertEquals("v1.4.0", r1.tagName)
        assertEquals("Release 1.4.0", r1.name)
        assertFalse(r1.isDraft)
        assertFalse(r1.isPrerelease)
        assertEquals(1, r1.assets.size)
        assertEquals("octoexpress-release.apk", r1.assets.first().name)
        assertEquals(18432000L, r1.assets.first().sizeBytes)
        assertEquals(42, r1.assets.first().downloadCount)
        assertEquals(
            "https://github.com/octocat/app/releases/download/v1.4.0/octoexpress-release.apk",
            r1.assets.first().downloadUrl
        )
        assertNotNull(repo.peekReleases("octocat", "app"))

        // Second non-forced read hits cache, not network
        val cached = repo.getReleases("octocat", "app", forceRefresh = false).getOrThrow()
        assertEquals(1, cached.size)
        assertEquals(1, releasesFetches)

        // Warm tags cache before create
        val tagsBefore = repo.getRepoTags("octocat", "app").getOrThrow()
        assertEquals(listOf("v1.4.0"), tagsBefore.map { it.name })
        assertEquals(1, tagsFetches)

        val created = repo.createRelease(
            owner = "octocat",
            repo = "app",
            tagName = "v2.0.0-beta.1",
            name = "v2.0.0 Beta 1",
            body = "## Beta Notes",
            targetCommitish = "main",
            isDraft = false,
            isPrerelease = true,
            generateReleaseNotes = false
        ).getOrThrow()
        assertEquals("v2.0.0-beta.1", created.tagName)
        assertTrue(created.isPrerelease)

        val payloadJson = json.parseToJsonElement(assertNotNull(postedPayload)).jsonObject
        assertEquals("v2.0.0-beta.1", payloadJson["tag_name"]?.jsonPrimitive?.content)
        assertEquals("main", payloadJson["target_commitish"]?.jsonPrimitive?.content)
        assertEquals(true, payloadJson["prerelease"]?.jsonPrimitive?.boolean)
        assertEquals(false, payloadJson["generate_release_notes"]?.jsonPrimitive?.boolean)

        // Caches were invalidated by createRelease, so regular reads fetch updated list & tags
        assertNull(repo.peekReleases("octocat", "app"))
        val updatedReleases = repo.getReleases("octocat", "app").getOrThrow()
        assertEquals(listOf("v2.0.0-beta.1", "v1.4.0"), updatedReleases.map { it.tagName })
        assertEquals(2, releasesFetches)

        val updatedTags = repo.getRepoTags("octocat", "app").getOrThrow()
        assertEquals(listOf("v2.0.0-beta.1", "v1.4.0"), updatedTags.map { it.name })
        assertEquals(2, tagsFetches)
    }
}
