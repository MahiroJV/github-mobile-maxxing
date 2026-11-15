package dev.octoexpress.app

import dev.octoexpress.model.RepoSummary

enum class ShellDestination {
    HOME,
    INBOX,
    ISSUES_PRS,
    REPOS,
    SETTINGS
}

data class ShellNavState(
    val currentDestination: ShellDestination = ShellDestination.HOME,
    val profileBackStack: List<String> = emptyList(),
    val overlayRepo: RepoSummary? = null,
    val overlayIssue: IssuePullTarget? = null,
    val overlayGistsUser: String? = null,
    val tabReselectNonce: Int = 0
) {
    val hasOverlay: Boolean
        get() = overlayGistsUser != null ||
            overlayIssue != null ||
            overlayRepo != null ||
            profileBackStack.isNotEmpty()

    fun selectBottomDestination(destination: ShellDestination): ShellNavState {
        val cleared = copy(
            currentDestination = destination,
            profileBackStack = emptyList(),
            overlayRepo = null,
            overlayIssue = null,
            overlayGistsUser = null,
            tabReselectNonce = if (currentDestination == destination || hasOverlay) {
                tabReselectNonce + 1
            } else {
                tabReselectNonce
            }
        )
        return cleared
    }

    fun openUserProfile(login: String): ShellNavState {
        if (login.isBlank()) return this
        return copy(
            overlayRepo = null,
            overlayIssue = null,
            overlayGistsUser = null,
            profileBackStack = profileBackStack + login
        )
    }

    fun popBackOrReturnHome(): ShellNavState = when {
        overlayGistsUser != null -> copy(overlayGistsUser = null)
        overlayIssue != null -> copy(overlayIssue = null)
        overlayRepo != null -> copy(overlayRepo = null)
        profileBackStack.isNotEmpty() -> copy(profileBackStack = profileBackStack.dropLast(1))
        currentDestination != ShellDestination.HOME -> copy(currentDestination = ShellDestination.HOME)
        else -> this
    }
}
