package dev.octoexpress.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState

@Composable
fun GitHubMarkdown(
    content: String,
    modifier: Modifier = Modifier
) {
    if (content.isBlank()) return
    val markdownState = rememberMarkdownState(
        content = content,
        retainState = true
    )
    Markdown(
        markdownState = markdownState,
        colors = markdownColor(
            text = MaterialTheme.colorScheme.onSurface,
            codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
            inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
            dividerColor = MaterialTheme.colorScheme.outlineVariant
        ),
        typography = markdownTypography(
            h1 = MaterialTheme.typography.headlineSmall,
            h2 = MaterialTheme.typography.titleLarge,
            h3 = MaterialTheme.typography.titleMedium,
            paragraph = MaterialTheme.typography.bodyMedium,
            code = MaterialTheme.typography.bodySmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        ),
        imageTransformer = Coil3ImageTransformerImpl,
        modifier = modifier.fillMaxWidth()
    )
}
