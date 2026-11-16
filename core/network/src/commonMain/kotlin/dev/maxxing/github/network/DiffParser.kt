package dev.maxxing.github.network

import dev.maxxing.github.model.DiffHunk
import dev.maxxing.github.model.DiffLine
import dev.maxxing.github.model.DiffLineKind

object DiffParser {
    private val HUNK_HEADER_REGEX = Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@(.*)$""")

    fun parse(patch: String?): List<DiffHunk> {
        if (patch.isNullOrBlank()) return emptyList()

        val hunks = mutableListOf<DiffHunk>()
        var currentHeader: String? = null
        var oldStart = 0
        var oldCount = 0
        var newStart = 0
        var newCount = 0
        var oldLine = 0
        var newLine = 0
        var currentLines = mutableListOf<DiffLine>()

        fun flushHunk() {
            val header = currentHeader ?: return
            hunks.add(
                DiffHunk(
                    header = header,
                    oldStart = oldStart,
                    oldCount = oldCount,
                    newStart = newStart,
                    newCount = newCount,
                    lines = currentLines.toList()
                )
            )
            currentLines = mutableListOf()
        }

        for (rawLine in patch.lines()) {
            val match = HUNK_HEADER_REGEX.matchEntire(rawLine)
            if (match != null) {
                flushHunk()
                currentHeader = rawLine
                oldStart = match.groupValues[1].toInt()
                oldCount = match.groupValues[2].toIntOrNull() ?: 1
                newStart = match.groupValues[3].toInt()
                newCount = match.groupValues[4].toIntOrNull() ?: 1
                oldLine = oldStart
                newLine = newStart
                continue
            }

            if (currentHeader == null) continue

            when {
                rawLine.startsWith("+") -> {
                    currentLines.add(
                        DiffLine(
                            kind = DiffLineKind.ADDED,
                            content = rawLine.drop(1),
                            oldLineNumber = null,
                            newLineNumber = newLine++
                        )
                    )
                }
                rawLine.startsWith("-") -> {
                    currentLines.add(
                        DiffLine(
                            kind = DiffLineKind.REMOVED,
                            content = rawLine.drop(1),
                            oldLineNumber = oldLine++,
                            newLineNumber = null
                        )
                    )
                }
                rawLine.startsWith("\\") -> {
                    currentLines.add(
                        DiffLine(
                            kind = DiffLineKind.META,
                            content = rawLine,
                            oldLineNumber = null,
                            newLineNumber = null
                        )
                    )
                }
                else -> {
                    val content = if (rawLine.startsWith(" ")) rawLine.drop(1) else rawLine
                    currentLines.add(
                        DiffLine(
                            kind = DiffLineKind.CONTEXT,
                            content = content,
                            oldLineNumber = oldLine++,
                            newLineNumber = newLine++
                        )
                    )
                }
            }
        }
        flushHunk()
        return hunks
    }
}
