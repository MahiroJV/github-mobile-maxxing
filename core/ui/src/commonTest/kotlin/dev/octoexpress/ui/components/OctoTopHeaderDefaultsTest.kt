package dev.octoexpress.ui.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class OctoTopHeaderDefaultsTest {
    @Test
    fun topHeaderDefaultsEnforceUniformVerticalAlignmentAcrossScreens() {
        assertEquals(16.dp, OctoTopHeaderDefaults.HorizontalPadding)
        assertEquals(14.dp, OctoTopHeaderDefaults.ExpandedVerticalPadding)
        assertEquals(6.dp, OctoTopHeaderDefaults.CollapsedVerticalPadding)
        assertEquals(48.dp, OctoTopHeaderDefaults.TitleRowMinHeight)
        assertEquals(10.dp, OctoTopHeaderDefaults.TabBarTopSpacing)
    }
}
