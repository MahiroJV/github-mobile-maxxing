package dev.maxxing.github.app.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepoHeaderCollapseTrackerTest {
    @Test
    fun `header collapses on scroll down beyond threshold and expands cleanly on scroll up`() {
        val tracker = RepoHeaderCollapseTracker(collapseThresholdPx = 36f, expandThresholdPx = 28f)
        assertFalse(tracker.isCollapsed)

        // Small jitter downward does not collapse yet
        tracker.onVerticalScrollDelta(dy = -15f)
        assertFalse(tracker.isCollapsed)

        // Horizontal gesture does not trigger collapse
        tracker.onVerticalScrollDelta(dy = -10f, dx = 30f)
        assertFalse(tracker.isCollapsed)

        // Cumulative downward scroll past 36px collapses header
        tracker.onVerticalScrollDelta(dy = -25f)
        assertTrue(tracker.isCollapsed)

        // Small upward jitter stays collapsed
        tracker.onVerticalScrollDelta(dy = 10f)
        assertTrue(tracker.isCollapsed)

        // Reversing down resets upward accumulator, then scrolling up >= 28px expands again
        tracker.onVerticalScrollDelta(dy = -5f)
        assertTrue(tracker.isCollapsed)
        tracker.onVerticalScrollDelta(dy = 30f)
        assertFalse(tracker.isCollapsed)

        // Programmatic expand resets collapsed state on tab change
        tracker.onVerticalScrollDelta(dy = -40f)
        assertTrue(tracker.isCollapsed)
        tracker.expand()
        assertFalse(tracker.isCollapsed)
    }

    @Test
    fun `canCollapse false prevents collapse and re-expands active search header`() {
        val tracker = RepoHeaderCollapseTracker(collapseThresholdPx = 36f, expandThresholdPx = 28f)

        // Scrolling down while search input is active must keep header visible
        tracker.onVerticalScrollDelta(dy = -80f, canCollapse = false)
        assertFalse(tracker.isCollapsed)

        // Collapse normally when idle
        tracker.onVerticalScrollDelta(dy = -60f, canCollapse = true)
        assertTrue(tracker.isCollapsed)

        // Becoming uncollapsible (e.g., activating search) immediately expands
        tracker.onVerticalScrollDelta(dy = -10f, canCollapse = false)
        assertFalse(tracker.isCollapsed)
    }
}
