package dev.maxxing.github.ui.components

import androidx.navigationevent.NavigationEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaxxingPredictiveBackTest {
    @Test
    fun zeroProgressLeavesScreenUntransformed() {
        val rest = calculatePredictiveBackVisualState(0f, NavigationEvent.EDGE_LEFT)
        assertEquals(PredictiveBackVisualState(), rest)

        val negative = calculatePredictiveBackVisualState(-0.5f, NavigationEvent.EDGE_RIGHT)
        assertEquals(PredictiveBackVisualState(), negative)
    }

    @Test
    fun progressFromLeftAndRightEdgesScalesRoundsAndShiftsOppositely() {
        val leftSwipe = calculatePredictiveBackVisualState(0.5f, NavigationEvent.EDGE_LEFT)
        val rightSwipe = calculatePredictiveBackVisualState(0.5f, NavigationEvent.EDGE_RIGHT)

        assertTrue(leftSwipe.scale in 0.90f..0.99f)
        assertTrue(leftSwipe.cornerRadiusDp > 0f)
        assertTrue(leftSwipe.translationXFraction > 0f)
        assertTrue(rightSwipe.translationXFraction < 0f)
        assertEquals(-leftSwipe.translationXFraction, rightSwipe.translationXFraction)

        val full = calculatePredictiveBackVisualState(1f, NavigationEvent.EDGE_LEFT)
        assertEquals(0.90f, full.scale)
        assertEquals(28f, full.cornerRadiusDp)
        assertEquals(0f, full.scrimAlpha)
    }
}
