package dev.maxxing.github.ui.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class MaxxingTopHeaderDefaultsTest {
    @Test
    fun topHeaderDefaultsEnforceUniformVerticalAlignmentAcrossScreens() {
        assertEquals(16.dp, MaxxingTopHeaderDefaults.HorizontalPadding)
        assertEquals(14.dp, MaxxingTopHeaderDefaults.ExpandedVerticalPadding)
        assertEquals(6.dp, MaxxingTopHeaderDefaults.CollapsedVerticalPadding)
        assertEquals(48.dp, MaxxingTopHeaderDefaults.TitleRowMinHeight)
        assertEquals(10.dp, MaxxingTopHeaderDefaults.TabBarTopSpacing)
    }
}
