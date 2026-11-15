package dev.octoexpress.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RepoDetailTabOrderTest {
    @Test
    fun `repo detail tabs start with Code Issues PRs and omit standalone Commits tab`() {
        val labels = RepoDetailTab.entries.map { it.label }
        assertEquals(
            listOf("Code", "Issues", "PRs", "Actions", "Releases", "Discussions", "Security"),
            labels
        )
        assertFalse("Commits" in labels)
    }
}
