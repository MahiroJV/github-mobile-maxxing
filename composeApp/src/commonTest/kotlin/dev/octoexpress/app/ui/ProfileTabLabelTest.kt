package dev.octoexpress.app.ui

import dev.octoexpress.model.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileTabLabelTest {
    @Test
    fun formatProfileTabLabelIncludesCountsForReposFollowersAndFollowing() {
        val profile = UserProfile(
            login = "octocat",
            publicReposCount = 20,
            followersCount = 34,
            followingCount = 139
        )
        assertEquals("Repos (20)", formatProfileTabLabel(ProfileTab.REPOS, profile))
        assertEquals("Followers (34)", formatProfileTabLabel(ProfileTab.FOLLOWERS, profile))
        assertEquals("Following (139)", formatProfileTabLabel(ProfileTab.FOLLOWING, profile))
        assertEquals("Starred", formatProfileTabLabel(ProfileTab.STARRED, profile))
        assertEquals("Activity", formatProfileTabLabel(ProfileTab.ACTIVITY, profile))
    }
}
