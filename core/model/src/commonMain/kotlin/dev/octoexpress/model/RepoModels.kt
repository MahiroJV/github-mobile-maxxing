package dev.octoexpress.model

import kotlinx.serialization.Serializable

@Serializable
data class RepoSummary(
    val id: String,
    val name: String,
    val owner: String,
    val fullName: String,
    val description: String? = null,
    val language: String? = null,
    val languageColorHex: String? = null,
    val stargazersCount: Int = 0,
    val forksCount: Int = 0,
    val openIssuesCount: Int = 0,
    val isPrivate: Boolean = false,
    val isFork: Boolean = false,
    val defaultBranch: String = "main",
    val updatedAt: String = "",
    val ownerAvatarUrl: String = "",
    val viewerHasStarred: Boolean = false
)

@Serializable
data class RepoDetail(
    val summary: RepoSummary,
    val homepageUrl: String? = null,
    val licenseName: String? = null,
    val topics: List<String> = emptyList(),
    val watchersCount: Int = 0,
    val branches: List<BranchRef> = emptyList()
)

@Serializable
data class BranchRef(
    val name: String,
    val sha: String
)

@Serializable
enum class TreeEntryType {
    TREE,
    BLOB
}

@Serializable
data class TreeEntry(
    val path: String,
    val name: String,
    val type: TreeEntryType,
    val sha: String,
    val size: Long = 0L
)

@Serializable
data class FileContent(
    val path: String,
    val name: String,
    val size: Long,
    val content: String,
    val isBinary: Boolean = false,
    val htmlUrl: String = ""
)
