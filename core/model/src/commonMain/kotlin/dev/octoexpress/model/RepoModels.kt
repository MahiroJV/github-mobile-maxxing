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

@Serializable
data class CommitSummary(
    val sha: String,
    val shortSha: String,
    val headline: String,
    val body: String = "",
    val authorName: String,
    val authorLogin: String? = null,
    val authorAvatarUrl: String = "",
    val committedAt: String = "",
    val parentShas: List<String> = emptyList(),
    val verificationReason: String? = null,
    val isVerified: Boolean = false
)

@Serializable
data class CommitDetail(
    val summary: CommitSummary,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changedFilesCount: Int = 0,
    val files: List<PullFileChange> = emptyList(),
    val htmlUrl: String = ""
)

@Serializable
data class TagRef(
    val name: String,
    val commitSha: String
)

@Serializable
data class BranchCompareResult(
    val baseRef: String,
    val headRef: String,
    val status: String,
    val aheadBy: Int = 0,
    val behindBy: Int = 0,
    val totalCommits: Int = 0,
    val commits: List<CommitSummary> = emptyList(),
    val files: List<PullFileChange> = emptyList(),
    val htmlUrl: String = ""
)

@Serializable
data class ReleaseAsset(
    val id: Long,
    val name: String,
    val sizeBytes: Long = 0L,
    val downloadCount: Int = 0,
    val downloadUrl: String = "",
    val contentType: String = ""
)

@Serializable
data class ReleaseSummary(
    val id: Long,
    val tagName: String,
    val name: String,
    val body: String = "",
    val isDraft: Boolean = false,
    val isPrerelease: Boolean = false,
    val authorLogin: String = "",
    val authorAvatarUrl: String = "",
    val publishedAt: String = "",
    val createdAt: String = "",
    val assets: List<ReleaseAsset> = emptyList(),
    val tarballUrl: String = "",
    val zipballUrl: String = "",
    val htmlUrl: String = ""
)
