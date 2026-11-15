package dev.octoexpress.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.vector.ImageVector
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.composables.icons.lucide.FolderGit2
import com.composables.icons.lucide.GitPullRequest
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import dev.octoexpress.app.notifications.PlatformNotifier
import dev.octoexpress.app.ui.IssuesPullsScreen
import dev.octoexpress.app.ui.NotificationsScreen
import dev.octoexpress.app.ui.ReposScreen
import dev.octoexpress.app.ui.SettingsScreen
import dev.octoexpress.app.ui.SignInScreen
import dev.octoexpress.ui.components.OctoLoadingState
import dev.octoexpress.ui.theme.OctoExpressTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class AppDestination(
    val label: String,
    val icon: ImageVector
) {
    INBOX("Inbox", Lucide.Inbox),
    ISSUES_PRS("Issues & PRs", Lucide.GitPullRequest),
    REPOS("Repos", Lucide.FolderGit2),
    SETTINGS("Settings", Lucide.Settings)
}

@Composable
fun OctoExpressApp(
    deepLinkTarget: IssuePullTarget? = null,
    onConsumeDeepLink: () -> Unit = {}
) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .crossfade(false)
            .build()
    }

    val container = remember { AppContainer() }
    val isInitialized by container.sessionManager.isInitialized.collectAsState()
    val session by container.sessionManager.session.collectAsState()
    val themePrefs by container.sessionManager.themePreferences.collectAsState()
    val notifPrefs by container.sessionManager.notificationPreferences.collectAsState()

    val viewerProfile by container.viewerProfile.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val appScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        PlatformNotifier.ensureInitialized()
        container.sessionManager.initialize()
    }

    LaunchedEffect(session) {
        if (session != null) {
            val loaded = container.refreshViewerProfile()
            container.warmUpWorkspace(loaded?.login ?: session?.userLogin)
        }
    }

    LaunchedEffect(session, notifPrefs.osNotificationsEnabled) {
        PlatformNotifier.syncBackgroundSchedule(
            enabled = session != null && notifPrefs.osNotificationsEnabled
        )
    }

    // Immediate sync whenever app returns to foreground (ON_RESUME)
    DisposableEffect(lifecycleOwner, session) {
        if (session == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appScope.launch {
                    container.syncNotifications()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Foreground real-time notification watcher across Android & Linux Desktop
    LaunchedEffect(session, notifPrefs) {
        if (session == null) return@LaunchedEffect
        while (isActive) {
            container.syncNotifications()
            val intervalMs = (notifPrefs.pollIntervalMinutes.coerceAtLeast(1)) * 60_000L
            delay(intervalMs)
        }
    }

    OctoExpressTheme(
        seedColorHex = themePrefs.seedColorHex,
        useDynamicColor = themePrefs.useDynamicColor,
        darkThemeOverride = themePrefs.darkModeOverride
    ) {
        when {
            !isInitialized -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OctoLoadingState(message = "Starting OctoExpress...")
                }
            }

            session == null -> {
                SignInScreen(
                    sessionManager = container.sessionManager,
                    onSignedIn = { /* session StateFlow transitions automatically */ }
                )
            }

            else -> {
                MainAuthenticatedShell(
                    container = container,
                    deepLinkTarget = deepLinkTarget,
                    onConsumeDeepLink = onConsumeDeepLink
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MainAuthenticatedShell(
    container: AppContainer,
    deepLinkTarget: IssuePullTarget?,
    onConsumeDeepLink: () -> Unit
) {
    var currentDestination by remember { mutableStateOf(AppDestination.INBOX) }
    val unreadCount by container.unreadNotificationCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(deepLinkTarget) {
        if (deepLinkTarget != null) {
            currentDestination = AppDestination.INBOX
        }
    }

    // Android System Back returns to Inbox tab before exiting app
    BackHandler(enabled = currentDestination != AppDestination.INBOX) {
        currentDestination = AppDestination.INBOX
    }

    val showMessage: (String) -> Unit = { msg ->
        scope.launch {
            snackbarHostState.showSnackbar(msg)
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestination.entries.forEach { dest ->
                item(
                    icon = {
                        if (dest == AppDestination.INBOX && unreadCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge {
                                        Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                                    }
                                }
                            ) {
                                Icon(dest.icon, contentDescription = dest.label)
                            }
                        } else {
                            Icon(dest.icon, contentDescription = dest.label)
                        }
                    },
                    label = { Text(dest.label) },
                    selected = currentDestination == dest,
                    onClick = { currentDestination = dest }
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when (currentDestination) {
                    AppDestination.INBOX -> NotificationsScreen(
                        container = container,
                        deepLinkTarget = deepLinkTarget,
                        onConsumeDeepLink = onConsumeDeepLink,
                        onMessage = showMessage
                    )
                    AppDestination.ISSUES_PRS -> IssuesPullsScreen(
                        container = container,
                        onMessage = showMessage
                    )
                    AppDestination.REPOS -> ReposScreen(
                        container = container,
                        onMessage = showMessage
                    )
                    AppDestination.SETTINGS -> SettingsScreen(
                        container = container,
                        onSignedOut = { currentDestination = AppDestination.INBOX },
                        onMessage = showMessage
                    )
                }
            }
        }
    }
}
