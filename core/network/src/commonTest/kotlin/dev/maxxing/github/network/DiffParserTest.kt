package dev.maxxing.github.network

import dev.maxxing.github.model.DiffLineKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiffParserTest {

    @Test
    fun parsesMultiHunkUnifiedDiffWithLineNumbers() {
        val patch = """
            @@ -10,4 +10,5 @@ fun greet() {
             val a = 1
            -val b = 2
            +val b = 3
            +val c = 4
             return a + b
            @@ -30,2 +31,2 @@ fun bye() {
            -println("old")
            +println("new")
        """.trimIndent()

        val hunks = DiffParser.parse(patch)
        assertEquals(2, hunks.size)

        val first = hunks[0]
        assertEquals(10, first.oldStart)
        assertEquals(10, first.newStart)
        assertEquals(5, first.lines.size)

        assertEquals(DiffLineKind.CONTEXT, first.lines[0].kind)
        assertEquals(10, first.lines[0].oldLineNumber)
        assertEquals(10, first.lines[0].newLineNumber)

        assertEquals(DiffLineKind.REMOVED, first.lines[1].kind)
        assertEquals(11, first.lines[1].oldLineNumber)
        assertNull(first.lines[1].newLineNumber)

        assertEquals(DiffLineKind.ADDED, first.lines[2].kind)
        assertNull(first.lines[2].oldLineNumber)
        assertEquals(11, first.lines[2].newLineNumber)

        assertEquals(DiffLineKind.ADDED, first.lines[3].kind)
        assertEquals(12, first.lines[3].newLineNumber)

        assertEquals(DiffLineKind.CONTEXT, first.lines[4].kind)
        assertEquals(12, first.lines[4].oldLineNumber)
        assertEquals(13, first.lines[4].newLineNumber)

        val second = hunks[1]
        assertEquals(30, second.oldStart)
        assertEquals(31, second.newStart)
        assertEquals(2, second.lines.size)
    }

    @Test
    fun returnsEmptyForNullOrBlankPatch() {
        assertEquals(emptyList(), DiffParser.parse(null))
        assertEquals(emptyList(), DiffParser.parse("   "))
    }
}
