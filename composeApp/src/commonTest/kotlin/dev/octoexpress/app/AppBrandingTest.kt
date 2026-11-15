package dev.octoexpress.app

import dev.octoexpress.model.AppBranding
import kotlin.test.Test
import kotlin.test.assertEquals

class AppBrandingTest {
    @Test
    fun appBrandingUsesGithubMobileMaxxingIdentity() {
        assertEquals("github-mobile-maxxing", AppBranding.APP_NAME)
        assertEquals("github-mobile-maxxing-KMP/1.0", AppBranding.USER_AGENT)
    }
}
