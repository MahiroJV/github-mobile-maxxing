package dev.octoexpress.model

import kotlinx.serialization.Serializable

@Serializable
data class UserSummary(
    val login: String,
    val avatarUrl: String = "",
    val htmlUrl: String = ""
)

@Serializable
data class ViewerProfile(
    val login: String,
    val name: String? = null,
    val avatarUrl: String = "",
    val bio: String? = null,
    val company: String? = null,
    val location: String? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val publicReposCount: Int = 0,
    val htmlUrl: String = ""
)
