package dev.octoexpress.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import dev.octoexpress.app.notifications.PlatformNotifier
import dev.octoexpress.auth.AndroidAuthContextHolder

class MainActivity : ComponentActivity() {
    private var deepLinkTarget by mutableStateOf<IssuePullTarget?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled gracefully by PlatformNotifier */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        enableMaxRefreshRate()
        AndroidAuthContextHolder.init(this)
        PlatformNotifier.ensureInitialized()
        requestNotificationPermissionIfNeeded()
        deepLinkTarget = parseDeepLink(intent)

        setContent {
            OctoExpressApp(
                deepLinkTarget = deepLinkTarget,
                onConsumeDeepLink = { deepLinkTarget = null }
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
