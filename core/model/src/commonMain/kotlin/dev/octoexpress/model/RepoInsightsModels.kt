package dev.octoexpress.model

import kotlin.math.roundToInt

data class RepoLanguageShare(
    val name: String,
    val bytes: Long,
    val percentage: Float,
    val colorHex: String
)

data class WeeklyCommitActivity(
    val weekEpochSeconds: Long,
    val total: Int,
    val days: List<Int> = emptyList()
)

data class ContributorStat(
    val login: String,
    val avatarUrl: String,
    val htmlUrl: String = "",
    val totalCommits: Int,
    val additions: Int = 0,
    val deletions: Int = 0
)

data class RepoInsightsData(
    val languages: List<RepoLanguageShare> = emptyList(),
    val totalLanguageBytes: Long = 0L,
    val weeklyActivity: List<WeeklyCommitActivity> = emptyList(),
    val contributors: List<ContributorStat> = emptyList(),
    val statsComputing: Boolean = false
)

private val GITHUB_LANGUAGE_COLORS = mapOf(
    "kotlin" to "#A97BFF",
    "java" to "#B07219",
    "typescript" to "#3178C6",
    "javascript" to "#F1E05A",
    "python" to "#3572A5",
    "rust" to "#DEA584",
    "go" to "#00ADD8",
    "swift" to "#F05138",
    "c++" to "#F34B7D",
    "c" to "#555555",
    "c#" to "#178600",
    "dart" to "#00B4AB",
    "ruby" to "#701516",
    "php" to "#4F5D95",
    "shell" to "#89E051",
    "html" to "#E34C26",
    "css" to "#563D7C",
    "scss" to "#C6538C",
    "dockerfile" to "#384D54",
    "yaml" to "#CB171E",
    "json" to "#292929",
    "gradle" to "#02303A",
    "objective-c" to "#438EFF",
    "scala" to "#C22D40"
)

private val FALLBACK_LANG_PALETTE = listOf(
    "#6750A4",
    "#006A6A",
    "#984061",
    "#4C662B",
    "#7D5260",
    "#2E6B9E"
)

fun computeLanguageShares(bytesByLanguage: Map<String, Long>): List<RepoLanguageShare> {
    val positive = bytesByLanguage.entries
        .filter { it.key.isNotBlank() && it.value > 0L }
        .sortedByDescending { it.value }
    if (positive.isEmpty()) return emptyList()
    val totalBytes = positive.sumOf { it.value }.coerceAtLeast(1L)
    return positive.mapIndexed { idx, (name, bytes) ->
        val rawPct = (bytes.toDouble() / totalBytes.toDouble()) * 100.0
        val roundedPct = ((rawPct * 10.0).roundToInt() / 10.0).toFloat()
        val colorHex = GITHUB_LANGUAGE_COLORS[name.trim().lowercase()]
            ?: FALLBACK_LANG_PALETTE[idx % FALLBACK_LANG_PALETTE.size]
        RepoLanguageShare(
            name = name,
            bytes = bytes,
            percentage = roundedPct,
            colorHex = colorHex
        )
    }
}
