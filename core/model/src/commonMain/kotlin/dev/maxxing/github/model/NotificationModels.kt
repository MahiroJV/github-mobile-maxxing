package dev.maxxing.github.model

import kotlinx.serialization.Serializable

@Serializable
enum class NotificationSubjectType {
    ISSUE,
    PULL_REQUEST,
    COMMIT,
    RELEASE,
    DISCUSSION,
    CHECK_SUITE,
    UNKNOWN
}

@Serializable
enum class NotificationReason {
    ASSIGN,
    AUTHOR,
    COMMENT,
    CI_ACTIVITY,
    INVITATION,
    MANUAL,
    MENTION,
    REVIEW_REQUESTED,
    SECURITY_ALERT,
    STATE_CHANGE,
    SUBSCRIBED,
    TEAM_MENTION,
    OTHER
}

@Serializable
data class NotificationThread(
    val id: String,
    val unread: Boolean,
    val reason: NotificationReason,
    val updatedAt: String,
    val subjectTitle: String,
    val subjectType: NotificationSubjectType,
    val subjectUrl: String?,
    val latestCommentUrl: String?,
    val repoFullName: String,
    val repoOwner: String,
    val repoName: String,
    val repoOwnerAvatarUrl: String = "",
    val isPrivate: Boolean = false
) {
    val subjectNumber: Int?
        get() = if (subjectType == NotificationSubjectType.ISSUE || subjectType == NotificationSubjectType.PULL_REQUEST) {
            subjectUrl?.substringAfterLast('/')?.toIntOrNull()
        } else {
            null
        }

    val checkSuiteId: Long?
        get() = if (subjectType == NotificationSubjectType.CHECK_SUITE) {
            subjectUrl?.substringAfterLast('/')?.toLongOrNull()
        } else {
            null
        }

    val commitSha: String?
        get() = if (subjectType == NotificationSubjectType.COMMIT) {
            subjectUrl?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        } else {
            null
        }

    val htmlFallbackUrl: String
        get() = when (subjectType) {
            NotificationSubjectType.ISSUE -> subjectNumber?.let { "https://github.com/$repoFullName/issues/$it" }
                ?: "https://github.com/$repoFullName/issues"
            NotificationSubjectType.PULL_REQUEST -> subjectNumber?.let { "https://github.com/$repoFullName/pull/$it" }
                ?: "https://github.com/$repoFullName/pulls"
            NotificationSubjectType.CHECK_SUITE -> "https://github.com/$repoFullName/actions"
            NotificationSubjectType.COMMIT -> commitSha?.let { "https://github.com/$repoFullName/commit/$it" }
                ?: "https://github.com/$repoFullName/commits"
            NotificationSubjectType.RELEASE -> "https://github.com/$repoFullName/releases"
            NotificationSubjectType.DISCUSSION -> "https://github.com/$repoFullName/discussions"
            NotificationSubjectType.UNKNOWN -> "https://github.com/$repoFullName"
        }
}
