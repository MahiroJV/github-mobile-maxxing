plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidLibrary {
        namespace = "dev.maxxing.github.core.ui"
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
        commonMain.dependencies {
            api(projects.core.model)
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.material3.adaptive.navsuite)
            api(libs.compose.ui)
            api(libs.compose.ui.backhandler)
            api(libs.navigationevent.compose)
            api(libs.compose.adaptive)
            api(libs.compose.adaptive.layout)
            api(libs.compose.adaptive.navigation)
            api(libs.material.kolor)
            api(libs.icons.lucide)
            api(libs.coil.compose)
            api(libs.coil.network.ktor3)
            api(libs.coil.svg)
            api(libs.markdown.renderer)
            api(libs.markdown.renderer.m3)
            api(libs.markdown.renderer.coil3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
