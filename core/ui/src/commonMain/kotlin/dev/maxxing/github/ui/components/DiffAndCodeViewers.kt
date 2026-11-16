package dev.maxxing.github.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.FileCode
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquarePlus
import dev.maxxing.github.model.DiffLine
import dev.maxxing.github.model.DiffLineKind
import dev.maxxing.github.model.FileChangeStatus
import dev.maxxing.github.model.PullFileChange

/**
 * Emits flat, virtualized LazyColumn items for a PR file diff so large diffs scroll at 120fps.
 */
fun LazyListScope.diffFileLazyItems(
    file: PullFileChange,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onCommentFile: (() -> Unit)? = null,
    onClickLine: ((DiffLine) -> Unit)? = null
) {
    item(key = "diff_hdr_${file.filename}", contentType = "diff_header") {
        DiffFileHeaderRow(
            file = file,
            expanded = expanded,
            onToggleExpand = onToggleExpand,
            onCommentFile = onCommentFile
        )
    }

    if (!expanded) return

    if (file.hunks.isEmpty()) {
        item(key = "diff_empty_${file.filename}", contentType = "diff_empty") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (file.patch == null) "Binary file or diff too large to display inline." else "Empty patch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
        return
    }

    file.hunks.forEachIndexed { hunkIdx, hunk ->
        item(key = "diff_hunk_${file.filename}_$hunkIdx", contentType = "diff_hunk") {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = hunk.header,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        itemsIndexed(
            items = hunk.lines,
            key = { lineIdx, _ -> "diff_line_${file.filename}_${hunkIdx}_$lineIdx" },
            contentType = { _, _ -> "diff_line" }
        ) { _, line ->
            DiffLineRow(
                line = line,
                onClick = if (onClickLine != null && line.kind != DiffLineKind.META) {
                    { onClickLine(line) }
                } else null
            )
        }
    }
}

@Composable
private fun DiffFileHeaderRow(
    file: PullFileChange,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onCommentFile: (() -> Unit)? = null
) {
    val statusColor = when (file.status) {
        FileChangeStatus.ADDED -> Color(0xFF16A34A)
        FileChangeStatus.REMOVED -> MaterialTheme.colorScheme.error
        FileChangeStatus.RENAMED -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Lucide.ChevronDown else Lucide.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Lucide.FileCode,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = file.filename,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "+${file.additions}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF22C55E),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "-${file.deletions}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            ExpressiveStatusPill(
                label = file.status.name.take(3),
                containerColor = statusColor.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.onSurface
            )
            if (onCommentFile != null) {
                IconButton(onClick = onCommentFile, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Lucide.MessageSquarePlus,
                        contentDescription = "Comment on file",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DiffLineRow(
    line: DiffLine,
    onClick: (() -> Unit)? = null
) {
    val lineBg = when (line.kind) {
        DiffLineKind.ADDED -> Color(0xFF22C55E).copy(alpha = 0.14f)
        DiffLineKind.REMOVED -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
        DiffLineKind.META -> MaterialTheme.colorScheme.surfaceContainerHighest
        DiffLineKind.CONTEXT -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val prefix = when (line.kind) {
        DiffLineKind.ADDED -> "+"
        DiffLineKind.REMOVED -> "-"
        DiffLineKind.META -> " "
        DiffLineKind.CONTEXT -> " "
    }
    val prefixColor = when (line.kind) {
        DiffLineKind.ADDED -> Color(0xFF16A34A)
        DiffLineKind.REMOVED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(lineBg)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = line.oldLineNumber?.toString()?.padStart(4, ' ') ?: "    ",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = line.newLineNumber?.toString()?.padStart(4, ' ') ?: "    ",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = prefix,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            ),
            color = prefixColor
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = line.content,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DiffFileCard(
    file: PullFileChange,
    initiallyExpanded: Boolean = true,
    modifier: Modifier = Modifier
) {
    var expanded by remember(file.filename) { mutableStateOf(initiallyExpanded) }
    Column(modifier = modifier.fillMaxWidth()) {
        DiffFileHeaderRow(
            file = file,
            expanded = expanded,
            onToggleExpand = { expanded = !expanded }
        )
        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val hScroll = rememberScrollState()
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(hScroll)
                        .padding(bottom = 8.dp)
                ) {
                    file.hunks.forEach { hunk ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = hunk.header,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                        hunk.lines.forEach { line ->
                            DiffLineRow(line = line)
                        }
                    }
                }
            }
        }
    }
}

data class CodeDisplayLine(
    val lineNumber: Int?,
    val text: String
)

private const val CODE_MAX_LINE_SEGMENT_CHARS = 1000
private const val CODE_MAX_HIGHLIGHT_CHARS = 512

/**
 * Prepares source code lines for high-FPS virtualized rendering by splitting ultra-long
 * minified/generated single lines into bounded segments while preserving 1-based line numbers.
 */
fun prepareCodeDisplayLines(
    code: String,
    maxSegmentChars: Int = CODE_MAX_LINE_SEGMENT_CHARS
): List<CodeDisplayLine> {
    if (code.isEmpty()) return listOf(CodeDisplayLine(1, ""))
    val rawLines = code.split('\n')
    val out = ArrayList<CodeDisplayLine>(rawLines.size)
    rawLines.forEachIndexed { index, raw ->
        val clean = if (raw.endsWith('\r')) raw.dropLast(1) else raw
        if (clean.length <= maxSegmentChars) {
            out.add(CodeDisplayLine(index + 1, clean))
        } else {
            var start = 0
            var first = true
            while (start < clean.length) {
                val end = (start + maxSegmentChars).coerceAtMost(clean.length)
                out.add(CodeDisplayLine(if (first) index + 1 else null, clean.substring(start, end)))
                first = false
                start = end
            }
        }
    }
    return out
}

@Composable
fun CodeViewer(
    code: String,
    filename: String,
    modifier: Modifier = Modifier
) {
    val displayLines = remember(code) { prepareCodeDisplayLines(code) }
    val keywordColor = MaterialTheme.colorScheme.primary
    val stringColor = Color(0xFF16A34A)
    val commentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val numberColor = MaterialTheme.colorScheme.tertiary
    val defaultColor = MaterialTheme.colorScheme.onSurface

    val maxLineNum = remember(displayLines) {
        displayLines.lastOrNull { it.lineNumber != null }?.lineNumber ?: displayLines.size
    }
    val gutterWidth = remember(maxLineNum) {
        val digits = maxLineNum.toString().length
        (digits * 8 + 18).coerceAtLeast(42).dp
    }

    val syncHighlighted = remember(displayLines, filename, keywordColor, defaultColor) {
        if (displayLines.size <= 150) {
            displayLines.map { line ->
                highlightCodeLineFast(
                    line = line.text,
                    keywordColor = keywordColor,
                    stringColor = stringColor,
                    commentColor = commentColor,
                    numberColor = numberColor,
                    defaultColor = defaultColor
                )
            }
        } else null
    }
    val asyncHighlighted by produceState<List<AnnotatedString>?>(
        initialValue = syncHighlighted,
        displayLines,
        filename,
        keywordColor,
        defaultColor
    ) {
        if (syncHighlighted == null) {
            value = withContext(Dispatchers.Default) {
                displayLines.map { line ->
                    highlightCodeLineFast(
                        line = line.text,
                        keywordColor = keywordColor,
                        stringColor = stringColor,
                        commentColor = commentColor,
                        numberColor = numberColor,
                        defaultColor = defaultColor
                    )
                }
            }
        }
    }
    val prebuiltLines = syncHighlighted ?: asyncHighlighted

    val hScroll = rememberScrollState()
    val codeTextStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.5.sp,
        lineHeight = 18.sp
    )
    val lineNumStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
    val lineNumColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(hScroll),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            itemsIndexed(
                items = displayLines,
                key = { index, _ -> index },
                contentType = { _, _ -> "code_line" }
            ) { index, displayLine ->
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.width(gutterWidth)) {
                        Text(
                            text = displayLine.lineNumber?.toString().orEmpty(),
                            style = lineNumStyle,
                            color = lineNumColor
                        )
                    }
                    val styled = prebuiltLines?.getOrNull(index)
                    if (styled != null) {
                        Text(
                            text = styled,
                            style = codeTextStyle,
                            softWrap = false
                        )
                    } else {
                        Text(
                            text = displayLine.text,
                            style = codeTextStyle,
                            color = defaultColor,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

private val CODE_KEYWORDS = setOf(
    "fun", "val", "var", "class", "interface", "object", "data", "sealed", "enum", "return",
    "if", "else", "when", "for", "while", "import", "package", "private", "public", "internal",
    "override", "suspend", "actual", "expect", "const", "typealias", "def", "async", "await",
    "function", "export", "from", "let", "struct", "impl", "fn", "pub", "use", "match",
    "true", "false", "null", "None", "self", "this", "super"
)

private fun isIdentStart(ch: Char): Boolean = ch in 'a'..'z' || ch in 'A'..'Z' || ch == '_'
private fun isIdentPart(ch: Char): Boolean = isIdentStart(ch) || ch in '0'..'9'

/**
 * Zero-regex O(N) syntax highlighter safe for running in background batch workers without
 * Regex engine backtracking or per-frame UI thread allocations.
 */
fun highlightCodeLineFast(
    line: String,
    keywordColor: Color,
    stringColor: Color,
    commentColor: Color,
    numberColor: Color,
    defaultColor: Color
): AnnotatedString {
    if (line.isEmpty() || line.length > CODE_MAX_HIGHLIGHT_CHARS) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = defaultColor)) { append(line) }
        }
    }
    return buildAnnotatedString {
        var i = 0
        val len = line.length
        while (i < len) {
            val c = line[i]
            when {
                c == '#' || (c == '/' && i + 1 < len && line[i + 1] == '/') -> {
                    withStyle(SpanStyle(color = commentColor)) {
                        append(line, i, len)
                    }
                    break
                }
                c == '"' || c == '\'' -> {
                    val quote = c
                    var j = i + 1
                    while (j < len) {
                        val q = line[j]
                        if (q == '\\' && j + 1 < len) {
                            j += 2
                        } else if (q == quote) {
                            j++
                            break
                        } else {
                            j++
                        }
                    }
                    withStyle(SpanStyle(color = stringColor)) {
                        append(line, i, j)
                    }
                    i = j
                }
                c in '0'..'9' -> {
                    var j = i + 1
                    var sawDot = false
                    while (j < len) {
                        val d = line[j]
                        if (d in '0'..'9') {
                            j++
                        } else if (d == '.' && !sawDot && j + 1 < len && line[j + 1] in '0'..'9') {
                            sawDot = true
                            j++
                        } else {
                            break
                        }
                    }
                    withStyle(SpanStyle(color = numberColor)) {
                        append(line, i, j)
                    }
                    i = j
                }
                isIdentStart(c) -> {
                    var j = i + 1
                    while (j < len && isIdentPart(line[j])) j++
                    val word = line.substring(i, j)
                    if (word in CODE_KEYWORDS) {
                        withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold)) {
                            append(word)
                        }
                    } else {
                        withStyle(SpanStyle(color = defaultColor)) {
                            append(word)
                        }
                    }
                    i = j
                }
                else -> {
                    var j = i + 1
                    while (j < len) {
                        val n = line[j]
                        if (n == '#' || n == '"' || n == '\'' || n in '0'..'9' || isIdentStart(n) ||
                            (n == '/' && j + 1 < len && line[j + 1] == '/')
                        ) {
                            break
                        }
                        j++
                    }
                    withStyle(SpanStyle(color = defaultColor)) {
                        append(line, i, j)
                    }
                    i = j
                }
            }
        }
    }
}
