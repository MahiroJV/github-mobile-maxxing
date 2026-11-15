package dev.octoexpress.model

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
        get() = subjectUrl?.substringAfterLast('/')?.toIntOrNull()
}
