package dev.octoexpress.app.ui

import dev.octoexpress.model.IssueStateFilter
import kotlin.test.Test
import kotlin.test.assertEquals

class TabFilterUnificationTest {
    @Test
    fun inboxReasonFiltersPreserveTabOrderAndLabels() {
        assertEquals(
            listOf(
                ReasonFilter.ALL,
                ReasonFilter.REVIEWS,
                ReasonFilter.MENTIONS,
                ReasonFilter.ASSIGNED,
                ReasonFilter.CI
            ),
            ReasonFilter.entries
        )
        assertEquals(
            listOf("All Reasons", "Review Req", "Mentions", "Assigned", "CI"),
            ReasonFilter.entries.map { it.label }
        )
    }

    @Test
    fun issueStateFiltersPreserveTabOrderAndLabels() {
        assertEquals(
            listOf(
                IssueStateFilter.OPEN,
                IssueStateFilter.CLOSED,
                IssueStateFilter.ALL
            ),
            IssueStateFilter.entries
        )
        assertEquals(
            listOf("Open", "Closed", "All"),
            IssueStateFilter.entries.map { it.label }
        )
    }
}
