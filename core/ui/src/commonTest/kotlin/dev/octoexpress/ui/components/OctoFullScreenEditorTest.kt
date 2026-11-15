package dev.octoexpress.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class OctoFullScreenEditorTest {
    @Test
    fun measureEditorTextHandlesEmptySingleAndMultiLineText() {
        assertEquals(EditorTextMetrics(0, 0), measureEditorText(""))
        assertEquals("0 lines · 0 chars", formatEditorMetrics(""))

        assertEquals(EditorTextMetrics(1, 5), measureEditorText("hello"))
        assertEquals("1 line · 5 chars", formatEditorMetrics("hello"))

        assertEquals(EditorTextMetrics(2, 11), measureEditorText("line1\nline2"))
        assertEquals(EditorTextMetrics(3, 9), measureEditorText("a\nb\ncdefg"))
        assertEquals("3 lines · 9 chars", formatEditorMetrics("a\nb\ncdefg"))
    }
}
