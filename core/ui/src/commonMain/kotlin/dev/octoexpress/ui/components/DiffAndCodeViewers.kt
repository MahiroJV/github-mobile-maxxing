package dev.octoexpress.ui.components

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.octoexpress.model.DiffLine
import dev.octoexpress.model.DiffLineKind
import dev.octoexpress.model.FileChangeStatus
import dev.octoexpress.model.PullFileChange

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

@Composable
fun CodeViewer(
    code: String,
    filename: String,
    modifier: Modifier = Modifier
) {
    val lines = remember(code) { code.lines() }
    val keywordColor = MaterialTheme.colorScheme.primary
    val stringColor = Color(0xFF16A34A)
    val commentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val numberColor = MaterialTheme.colorScheme.tertiary
    val defaultColor = MaterialTheme.colorScheme.onSurface

    val hScroll = rememberScrollState()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxSize()
    ) {
        SelectionContainer {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(hScroll),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                itemsIndexed(
                    items = lines,
                    key = { index, _ -> index },
                    contentType = { _, _ -> "code_line" }
                ) { index, rawLine ->
                    val highlighted = remember(rawLine, filename, keywordColor, defaultColor) {
                        highlightCodeLine(
                            line = rawLine,
                            keywordColor = keywordColor,
                            stringColor = stringColor,
                            commentColor = commentColor,
                            numberColor = numberColor,
                            defaultColor = defaultColor
                        )
                    }
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(46.dp)) {
                            Text(
                                text = (index + 1).toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        }
                        Text(
                            text = highlighted,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            ),
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

private val TOKEN_REGEX = Regex("""(//.*|#.*|"(?:\\.|[^"])*"|'(?:\\.|[^'])*'|\b\d+(?:\.\d+)?\b|\b[A-Za-z_][A-Za-z0-9_]*\b)""")

private fun highlightCodeLine(
    line: String,
    keywordColor: Color,
    stringColor: Color,
    commentColor: Color,
    numberColor: Color,
    defaultColor: Color
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in TOKEN_REGEX.findAll(line)) {
        if (match.range.first > cursor) {
            withStyle(SpanStyle(color = defaultColor)) {
                append(line.substring(cursor, match.range.first))
            }
        }
        val token = match.value
        val style = when {
            token.startsWith("//") || token.startsWith("#") -> SpanStyle(color = commentColor)
            token.startsWith("\"") || token.startsWith("'") -> SpanStyle(color = stringColor)
            token.first().isDigit() -> SpanStyle(color = numberColor)
            token in CODE_KEYWORDS -> SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold)
            else -> SpanStyle(color = defaultColor)
        }
        withStyle(style) { append(token) }
        cursor = match.range.last + 1
    }
    if (cursor < line.length) {
        withStyle(SpanStyle(color = defaultColor)) {
            append(line.substring(cursor))
        }
    }
}
