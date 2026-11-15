package dev.octoexpress.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import dev.octoexpress.app.notifications.PlatformNotifier
import dev.octoexpress.app.ui.HomeScreen
import dev.octoexpress.app.ui.IssuePullDetailPane
import dev.octoexpress.app.ui.IssuesPullsScreen
import dev.octoexpress.app.ui.NotificationsScreen
import dev.octoexpress.app.ui.RepoDetailScreen
import dev.octoexpress.app.ui.ReposScreen
import dev.octoexpress.app.ui.SettingsScreen
import dev.octoexpress.app.ui.SignInScreen
import dev.octoexpress.app.ui.UserProfileScreen
import dev.octoexpress.model.RepoSummary
import dev.octoexpress.ui.components.OctoLoadingState
import dev.octoexpress.ui.theme.OctoExpressTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class AppDestination(
    val label: String,
    val icon: ImageVector
) {
    HOME("Home", Lucide.House),
    INBOX("Inbox", Lucide.Inbox),
    ISSUES_PRS("Issues & PRs", Lucide.GitPullRequest),
    REPOS("Repos", Lucide.FolderGit2),
    SETTINGS("Settings", Lucide.Settings)
}

@Composable
fun OctoExpressApp(
    deepLinkTarget: IssuePullTarget? = null,
    onConsumeDeepLink: () -> Unit = {},
    onDarkThemeChanged: (Boolean) -> Unit = {}
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

    val resolvedDarkTheme = themePrefs.darkModeOverride ?: isSystemInDarkTheme()
    LaunchedEffect(resolvedDarkTheme) {
        onDarkThemeChanged(resolvedDarkTheme)
    }

    OctoExpressTheme(
        seedColorHex = themePrefs.seedColorHex,
        useDynamicColor = themePrefs.useDynamicColor,
        darkThemeOverride = themePrefs.darkModeOverride
    ) {
        when {
            !isInitialized -> {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        OctoLoadingState(message = "Starting OctoExpress...")
                    }
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
    var currentDestination by remember { mutableStateOf(AppDestination.HOME) }
    var profileBackStack by remember { mutableStateOf<List<String>>(emptyList()) }
    var overlayRepo by remember { mutableStateOf<RepoSummary?>(null) }
    var overlayIssue by remember { mutableStateOf<IssuePullTarget?>(null) }
    val unreadCount by container.unreadNotificationCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val openUserProfile: (String) -> Unit = { targetLogin ->
        if (targetLogin.isNotBlank()) {
            overlayRepo = null
            overlayIssue = null
            profileBackStack = profileBackStack + targetLogin
        }
    }

    LaunchedEffect(deepLinkTarget) {
        if (deepLinkTarget != null) {
            profileBackStack = emptyList()
            overlayRepo = null
            overlayIssue = null
            currentDestination = AppDestination.INBOX
        }
    }

    // Android System Back pops profile/overlay stack or returns to Home tab before exiting app
    BackHandler(
        enabled = overlayIssue != null || overlayRepo != null || profileBackStack.isNotEmpty() || currentDestination != AppDestination.HOME
    ) {
        when {
            overlayIssue != null -> overlayIssue = null
            overlayRepo != null -> overlayRepo = null
            profileBackStack.isNotEmpty() -> profileBackStack = profileBackStack.dropLast(1)
            else -> currentDestination = AppDestination.HOME
        }
    }

    val showMessage: (String) -> Unit = { msg ->
        scope.launch {
            snackbarHostState.showSnackbar(msg)
        }
    }

    NavigationSuiteScaffold(
        containerColor = MaterialTheme.colorScheme.surface,
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
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                when {
                    overlayIssue != null -> IssuePullDetailPane(
                        target = overlayIssue!!,
                        container = container,
                        showBackButton = true,
                        onBack = { overlayIssue = null },
                        onMessage = showMessage
                    )
                    overlayRepo != null -> RepoDetailScreen(
                        repo = overlayRepo!!,
                        container = container,
                        onBack = { overlayRepo = null },
                        onOpenIssueOrPull = { overlayIssue = it },
                        onOpenUser = openUserProfile,
                        onMessage = showMessage
                    )
                    profileBackStack.isNotEmpty() -> UserProfileScreen(
                        login = profileBackStack.last(),
                        container = container,
                        onBack = { profileBackStack = profileBackStack.dropLast(1) },
                        onOpenRepo = { overlayRepo = it },
                        onOpenIssueOrPull = { overlayIssue = it },
                        onOpenUser = { nextLogin -> profileBackStack = profileBackStack + nextLogin },
                        onMessage = showMessage
                    )
                    else -> when (currentDestination) {
                        AppDestination.HOME -> HomeScreen(
                            container = container,
                            unreadCount = unreadCount,
                            onNavigateInbox = { currentDestination = AppDestination.INBOX },
                            onNavigateIssuesPrs = { currentDestination = AppDestination.ISSUES_PRS },
                            onNavigateRepos = { currentDestination = AppDestination.REPOS },
                            onOpenUser = openUserProfile,
                            onMessage = showMessage
                        )
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
                            onOpenUser = openUserProfile,
                            onMessage = showMessage
                        )
                        AppDestination.SETTINGS -> SettingsScreen(
                            container = container,
                            onOpenUser = openUserProfile,
                            onSignedOut = { currentDestination = AppDestination.HOME },
                            onMessage = showMessage
                        )
                    }
                }
            }
        }
    }
}
