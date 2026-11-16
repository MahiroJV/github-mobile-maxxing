package dev.maxxing.github.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkflowSummary(
    val id: Long,
    val name: String,
    val path: String,
    val state: String,
    val htmlUrl: String = ""
)

@Serializable
data class WorkflowRunSummary(
    val id: Long,
    val runNumber: Int,
    val name: String,
    val displayTitle: String,
    val headBranch: String,
    val headSha: String,
    val event: String,
    val status: String, // queued, in_progress, completed
    val conclusion: String? = null, // success, failure, cancelled, skipped, timed_out
    val actorLogin: String = "",
    val actorAvatarUrl: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val htmlUrl: String = ""
)

@Serializable
data class WorkflowJobStep(
    val name: String,
    val number: Int,
    val status: String,
    val conclusion: String? = null
)

@Serializable
data class WorkflowJobSummary(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val steps: List<WorkflowJobStep> = emptyList(),
    val htmlUrl: String = ""
)

@Serializable
data class CheckRunSummary(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String? = null,
    val appName: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val detailsUrl: String = ""
)
