package dev.octoexpress.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState

private val HTML_IMG_REGEX = Regex("""<img\b([^>]*)/?>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HTML_ATTR_REGEX = Regex("""([a-zA-Z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""")
private val HTML_PICTURE_SOURCE_REGEX = Regex("""</?(?:picture|source)\b[^>]*>""", RegexOption.IGNORE_CASE)
private val HTML_BR_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val HTML_ALIGN_WRAPPER_REGEX = Regex("""</?(?:p|div|center)\b[^>]*>""", RegexOption.IGNORE_CASE)
private val MARKDOWN_IMAGE_REGEX = Regex("""!\[([^\]]*)\]\(([^)\s]+)(?:\s+"[^"]*")?\)""")
private val GITHUB_BLOB_OR_RAW_REGEX = Regex(
    """^https?://github\.com/([^/]+)/([^/]+)/(?:blob|raw)/(.+)$""",
    RegexOption.IGNORE_CASE
)

internal fun resolveGitHubImageUrl(
    rawUrl: String,
    repoOwner: String? = null,
    repoName: String? = null,
    branchRef: String? = null,
    documentPath: String = ""
): String {
    val trimmed = rawUrl.trim().removePrefix("<").removeSuffix(">")
    if (trimmed.isBlank()) return trimmed
    if (trimmed.startsWith("data:", ignoreCase = true) || trimmed.startsWith("#")) return trimmed

    if (trimmed.startsWith("//")) {
        return "https:$trimmed"
    }

    GITHUB_BLOB_OR_RAW_REGEX.matchEntire(trimmed)?.let { match ->
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        val rest = match.groupValues[3]
        return "https://raw.githubusercontent.com/$owner/$repo/$rest"
    }

    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        return trimmed
    }

    val owner = repoOwner?.trim()?.takeIf { it.isNotEmpty() } ?: return trimmed
    val repo = repoName?.trim()?.takeIf { it.isNotEmpty() } ?: return trimmed
    val ref = branchRef?.trim()?.takeIf { it.isNotEmpty() } ?: "HEAD"

    val resolvedPath = if (trimmed.startsWith("/")) {
        trimmed.trimStart('/')
    } else {
        val baseDir = documentPath.trim('/').substringBeforeLast('/', "")
        val rawSegments = buildList {
            if (baseDir.isNotEmpty()) addAll(baseDir.split('/'))
            addAll(trimmed.split('/'))
        }
        val normalized = mutableListOf<String>()
        for (segment in rawSegments) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (normalized.isNotEmpty()) normalized.removeAt(normalized.lastIndex)
                else -> normalized += segment
            }
        }
        normalized.joinToString("/")
    }

    return "https://raw.githubusercontent.com/$owner/$repo/$ref/$resolvedPath"
}

fun normalizeGitHubMarkdown(
    content: String,
    repoOwner: String? = null,
    repoName: String? = null,
    branchRef: String? = null,
    documentPath: String = ""
): String {
    if (content.isBlank()) return content
    var result = content
        .replace(HTML_PICTURE_SOURCE_REGEX, "")
        .replace(HTML_BR_REGEX, "\n")
        .replace(HTML_ALIGN_WRAPPER_REGEX, "\n")

    result = HTML_IMG_REGEX.replace(result) { match ->
        val attrsRaw = match.groupValues[1]
        val attrs = mutableMapOf<String, String>()
        for (attr in HTML_ATTR_REGEX.findAll(attrsRaw)) {
            val key = attr.groupValues[1].lowercase()
            val value = attr.groupValues[2].ifEmpty {
                attr.groupValues[3].ifEmpty { attr.groupValues[4] }
            }
            attrs[key] = value
        }
        val src = attrs["src"].orEmpty().trim()
        if (src.isEmpty()) {
            ""
        } else {
            val alt = attrs["alt"].orEmpty().replace("\n", " ").trim()
            val resolved = resolveGitHubImageUrl(
                rawUrl = src,
                repoOwner = repoOwner,
                repoName = repoName,
                branchRef = branchRef,
                documentPath = documentPath
            )
            "![$alt]($resolved)"
        }
    }

    result = MARKDOWN_IMAGE_REGEX.replace(result) { match ->
        val alt = match.groupValues[1]
        val url = match.groupValues[2]
        val resolved = resolveGitHubImageUrl(
            rawUrl = url,
            repoOwner = repoOwner,
            repoName = repoName,
            branchRef = branchRef,
            documentPath = documentPath
        )
        "![$alt]($resolved)"
    }

    return result
}

@Composable
fun GitHubMarkdown(
    content: String,
    modifier: Modifier = Modifier,
    repoOwner: String? = null,
    repoName: String? = null,
    branchRef: String? = null,
    documentPath: String = ""
) {
    if (content.isBlank()) return
    val normalized = remember(content, repoOwner, repoName, branchRef, documentPath) {
        normalizeGitHubMarkdown(
            content = content,
            repoOwner = repoOwner,
            repoName = repoName,
            branchRef = branchRef,
            documentPath = documentPath
        )
    }
    val markdownState = rememberMarkdownState(
        content = normalized,
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
