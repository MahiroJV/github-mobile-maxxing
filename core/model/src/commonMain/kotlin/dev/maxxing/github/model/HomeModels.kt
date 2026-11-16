package dev.maxxing.github.model

import kotlinx.serialization.Serializable

@Serializable
data class ContributionDay(
    val date: String,
    val count: Int,
    val level: Int // 0..4
)

@Serializable
data class ContributionWeek(
    val days: List<ContributionDay>
)

@Serializable
data class ContributionCalendar(
    val totalContributions: Int,
    val weeks: List<ContributionWeek>
)

@Serializable
enum class ActivityEventKind {
    PUSH,
    PULL_REQUEST,
    ISSUES,
    ISSUE_COMMENT,
    PULL_REQUEST_REVIEW,
    WATCH,
    FORK,
    CREATE,
    DELETE,
    RELEASE,
    PUBLIC,
    MEMBER,
    OTHER
}

@Serializable
data class ActivityEvent(
    val id: String,
    val kind: ActivityEventKind,
    val actor: UserSummary,
    val repoFullName: String,
    val repoOwner: String,
    val repoName: String,
    val summaryText: String,
    val detailTitle: String? = null,
    val targetIssueNumber: Int? = null,
    val commitSha: String? = null,
    val branchName: String? = null,
    val releaseTag: String? = null,
    val createdAt: String = ""
)
