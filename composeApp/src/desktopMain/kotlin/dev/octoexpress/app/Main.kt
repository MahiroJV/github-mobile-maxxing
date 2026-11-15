package dev.octoexpress.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.octoexpress.app.resources.Res
import dev.octoexpress.app.resources.app_icon
import dev.octoexpress.model.AppBranding
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension

fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 840.dp)
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "${AppBranding.APP_NAME} — GitHub Client",
        icon = painterResource(Res.drawable.app_icon)
    ) {
        window.minimumSize = Dimension(420, 600)
        OctoExpressApp()
    }
}
