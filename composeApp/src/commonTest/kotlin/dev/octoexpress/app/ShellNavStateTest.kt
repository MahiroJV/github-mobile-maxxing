package dev.octoexpress.app

import dev.octoexpress.model.RepoSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShellNavStateTest {
    @Test
    fun bottomDestinationSelectionAlwaysClearsProfileAndOverlays() {
        val sampleRepo = RepoSummary(id = "r1", name = "app", owner = "hubot", fullName = "hubot/app")
        var state = ShellNavState(currentDestination = ShellDestination.HOME)
            .openUserProfile("hubot")
            .copy(
                overlayRepo = sampleRepo,
                overlayIssue = IssuePullTarget("hubot", "app", 9),
                overlayGistsUser = "hubot"
            )

        assertTrue(state.hasOverlay)

        // Switching to another bottom nav destination prioritizes bottom nav and clears profile stack/overlays
        state = state.selectBottomDestination(ShellDestination.REPOS)
        assertEquals(ShellDestination.REPOS, state.currentDestination)
        assertFalse(state.hasOverlay)
        assertTrue(state.profileBackStack.isEmpty())
        assertNull(state.overlayRepo)
        assertNull(state.overlayIssue)
        assertNull(state.overlayGistsUser)

        // Even re-tapping the currently active bottom destination while inside a profile returns to that root tab
        state = state.openUserProfile("alice")
        assertTrue(state.hasOverlay)
        state = state.selectBottomDestination(ShellDestination.REPOS)
        assertEquals(ShellDestination.REPOS, state.currentDestination)
        assertFalse(state.hasOverlay)
        assertTrue(state.tabReselectNonce > 0)
    }
}
