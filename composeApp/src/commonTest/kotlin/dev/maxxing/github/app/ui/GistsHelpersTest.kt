package dev.maxxing.github.app.ui

import dev.maxxing.github.model.GistSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GistsHelpersTest {
    @Test
    fun resolveGistUrlUsesTrimmedHtmlUrlWhenPresent() {
        val gist = GistSummary(
            id = "abc123",
            ownerLogin = "octocat",
            htmlUrl = "  https://gist.github.com/octocat/abc123  "
        )
        assertEquals("https://gist.github.com/octocat/abc123", resolveGistUrl(gist))
    }

    @Test
    fun resolveGistUrlFallsBackToOwnerAndIdOrIdOnly() {
        val withOwner = GistSummary(id = "abc123", ownerLogin = "octocat", htmlUrl = "")
        assertEquals("https://gist.github.com/octocat/abc123", resolveGistUrl(withOwner))

        val idOnly = GistSummary(id = "abc123", ownerLogin = "", htmlUrl = "  ")
        assertEquals("https://gist.github.com/abc123", resolveGistUrl(idOnly))

        val empty = GistSummary(id = " ", ownerLogin = "octocat", htmlUrl = "")
        assertNull(resolveGistUrl(empty))
    }
}
