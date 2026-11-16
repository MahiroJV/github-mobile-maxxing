package dev.maxxing.github.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.abs

/**
 * Tracks vertical scroll direction to collapse/expand dense repository header metadata
 * with hysteresis so small touch jitters or horizontal scrolls do not flicker the layout.
 */
class RepoHeaderCollapseTracker(
    private val collapseThresholdPx: Float = 36f,
    private val expandThresholdPx: Float = 28f
) {
    var isCollapsed by mutableStateOf(false)
        private set

    private var accumulatedDeltaPx: Float = 0f

    fun onVerticalScrollDelta(dy: Float, dx: Float = 0f, canCollapse: Boolean = true) {
        if (!canCollapse) {
            if (isCollapsed) {
                expand()
            } else {
                accumulatedDeltaPx = 0f
            }
            return
        }
        if (abs(dy) <= abs(dx) || dy == 0f) return
        // Reset opposing accumulator immediately when reversing direction for crisp response
        if ((dy < 0f && accumulatedDeltaPx > 0f) || (dy > 0f && accumulatedDeltaPx < 0f)) {
            accumulatedDeltaPx = 0f
        }
        accumulatedDeltaPx = (accumulatedDeltaPx + dy).coerceIn(-collapseThresholdPx * 1.5f, expandThresholdPx * 1.5f)
        if (!isCollapsed && accumulatedDeltaPx <= -collapseThresholdPx) {
            isCollapsed = true
            accumulatedDeltaPx = 0f
        } else if (isCollapsed && accumulatedDeltaPx >= expandThresholdPx) {
            isCollapsed = false
            accumulatedDeltaPx = 0f
        }
    }

    fun expand() {
        isCollapsed = false
        accumulatedDeltaPx = 0f
    }

    fun toNestedScrollConnection(canCollapse: () -> Boolean = { true }): NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (source == NestedScrollSource.UserInput) {
                val effectiveY = if (consumed.y != 0f) consumed.y else available.y
                val effectiveX = if (consumed.y != 0f) consumed.x else available.x
                onVerticalScrollDelta(dy = effectiveY, dx = effectiveX, canCollapse = canCollapse())
            }
            return Offset.Zero
        }
    }
}

/**
 * Shared animated show/hide container for collapsible top header controls.
 */
@Composable
fun MaxxingCollapsibleSection(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        content()
    }
}
