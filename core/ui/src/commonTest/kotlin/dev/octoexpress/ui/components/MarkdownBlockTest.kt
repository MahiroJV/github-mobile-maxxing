package dev.octoexpress.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownBlockTest {
    @Test
    fun resolvesRelativeHtmlAndGithubBlobImagesInReadmeAndSubdirs() {
        val raw = """
            <p align="center">
              <picture>
                <source srcset="docs/dark.png" media="(prefers-color-scheme: dark)">
                <img src="./docs/banner.png" alt="Project Banner" width="400" />
              </picture>
            </p>
            ![Logo](/assets/logo.svg)
            ![Demo](https://github.com/octocat/hello/blob/main/media/demo.gif)
            ![Ext](https://img.shields.io/badge/build-passing-brightgreen.svg)
        """.trimIndent()

        val normalizedRoot = normalizeGitHubMarkdown(
            content = raw,
            repoOwner = "octocat",
            repoName = "hello",
            branchRef = "main",
            documentPath = "README.md"
        )

        assertTrue("![Project Banner](https://raw.githubusercontent.com/octocat/hello/main/docs/banner.png)" in normalizedRoot)
        assertTrue("![Logo](https://raw.githubusercontent.com/octocat/hello/main/assets/logo.svg)" in normalizedRoot)
        assertTrue("![Demo](https://raw.githubusercontent.com/octocat/hello/main/media/demo.gif)" in normalizedRoot)
        assertTrue("![Ext](https://img.shields.io/badge/build-passing-brightgreen.svg)" in normalizedRoot)

        val subDoc = "![Diagram](../img/arch.png)"
        val normalizedSub = normalizeGitHubMarkdown(
            content = subDoc,
            repoOwner = "octocat",
            repoName = "hello",
            branchRef = "dev",
            documentPath = "docs/guide/intro.md"
        )
        assertEquals(
            "![Diagram](https://raw.githubusercontent.com/octocat/hello/dev/docs/img/arch.png)",
            normalizedSub
        )
    }
}
