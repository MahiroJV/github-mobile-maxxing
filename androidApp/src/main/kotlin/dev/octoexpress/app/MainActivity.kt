package dev.octoexpress.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import dev.octoexpress.app.notifications.PlatformNotifier
import dev.octoexpress.auth.AndroidAuthContextHolder

class MainActivity : ComponentActivity() {
    private var deepLinkTarget by mutableStateOf<IssuePullTarget?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled gracefully by PlatformNotifier */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val initialBarStyle = if (systemDark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(
            statusBarStyle = initialBarStyle,
            navigationBarStyle = initialBarStyle
        )
        super.onCreate(savedInstanceState)
        enableMaxRefreshRate()
        AndroidAuthContextHolder.init(this)
        PlatformNotifier.ensureInitialized()
        requestNotificationPermissionIfNeeded()
        deepLinkTarget = parseDeepLink(intent)

        setContent {
            var isDarkTheme by mutableStateOf(systemDark)
            val view = LocalView.current

            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDarkTheme
                controller.isAppearanceLightNavigationBars = !isDarkTheme
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isStatusBarContrastEnforced = false
                    window.isNavigationBarContrastEnforced = false
                }
            }

            LaunchedEffect(isDarkTheme) {
                val barStyle = if (isDarkTheme) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }
            OctoExpressApp(
                deepLinkTarget = deepLinkTarget,
                onConsumeDeepLink = { deepLinkTarget = null },
                onDarkThemeChanged = { isDarkTheme = it }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseDeepLink(intent)?.let { deepLinkTarget = it }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun enableMaxRefreshRate() {
        runCatching {
            val dm = getSystemService(DISPLAY_SERVICE) as? DisplayManager ?: return
            val targetDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return
            val maxMode = targetDisplay.supportedModes.maxByOrNull { it.refreshRate } ?: return
            val params = window.attributes
            if (params.preferredDisplayModeId != maxMode.modeId) {
                params.preferredDisplayModeId = maxMode.modeId
                window.attributes = params
            }
        }
    }

    private fun parseDeepLink(intent: Intent?): IssuePullTarget? {
        val data = intent?.data ?: return null
        if (data.scheme != "octoexpress" || data.host != "thread") return null
        val segments = data.pathSegments
        if (segments.size < 3) return null
        val owner = segments[0]
        val repo = segments[1]
        val number = segments[2].toIntOrNull() ?: return null
        return IssuePullTarget(owner = owner, repo = repo, number = number)
    }
}
