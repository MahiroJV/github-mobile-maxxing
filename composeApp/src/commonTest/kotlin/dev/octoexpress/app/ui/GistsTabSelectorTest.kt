package dev.octoexpress.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class GistsTabSelectorTest {
    @Test
    fun gistsTabsExposeMineAndStarredForConnectedPillGroup() {
        assertEquals(listOf(GistsTab.MINE, GistsTab.STARRED), GistsTab.entries)
        assertEquals("My Gists", GistsTab.MINE.label)
        assertEquals("Starred", GistsTab.STARRED.label)
    }
}
