import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val defaultGitHubClientId: String = localProps.getProperty("github.client.id", "")
    .replace("\"", "")

val generateOctoConfig by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/octoConfig/kotlin")
    inputs.property("clientId", defaultGitHubClientId)
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile.resolve("dev/octoexpress/app")
        dir.mkdirs()
        dir.resolve("OctoBuildConfig.kt").writeText(
            """
            package dev.octoexpress.app

            internal object OctoBuildConfig {
                const val DEFAULT_GITHUB_CLIENT_ID: String = "$defaultGitHubClientId"
            }
            """.trimIndent()
        )
    }
}

kotlin {
    androidLibrary {
        namespace = "dev.octoexpress.app.shared"
        compileSdk {
            version = release(37) {
                minorApiLevel = 2
            }
        }
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generateOctoConfig.map { layout.buildDirectory.dir("generated/octoConfig/kotlin") })
            dependencies {
                implementation(projects.core.model)
                api(projects.core.auth)
                implementation(projects.core.network)
                implementation(projects.core.ui)
                implementation(libs.compose.components.resources)
                implementation(libs.androidx.lifecycle.viewmodelCompose)
                implementation(libs.androidx.lifecycle.runtimeCompose)
            }
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.work.runtime)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.octoexpress.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "OctoExpress"
            packageVersion = "1.0.0"
            description = "Fast Material 3 Expressive GitHub Client"
            linux {
                packageName = "octoexpress"
                shortcut = true
            }
        }
    }
}
