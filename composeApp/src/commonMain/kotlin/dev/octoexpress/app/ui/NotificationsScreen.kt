package dev.octoexpress.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.GitCommitHorizontal
import com.composables.icons.lucide.GitPullRequest
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.X
import dev.octoexpress.app.AppContainer
import dev.octoexpress.app.IssuePullTarget
import dev.octoexpress.model.NotificationReason
import dev.octoexpress.model.NotificationSubjectType
import dev.octoexpress.model.NotificationThread
import dev.octoexpress.ui.components.ExpressiveStatusPill
import dev.octoexpress.ui.components.OctoConnectedButtonGroup
import dev.octoexpress.ui.components.OctoFloatingToolbar
import dev.octoexpress.ui.components.OctoLoadingState
import dev.octoexpress.ui.components.OctoWavyProgress
import dev.octoexpress.ui.components.UserAvatar
import dev.octoexpress.ui.components.expressiveAnimateContentSize
import kotlinx.coroutines.launch

private enum class InboxFilter(val label: String) {
    UNREAD("Unread"),
    PARTICIPATING("Participating"),
    ALL("All")
}

private enum class ReasonFilter(val label: String) {
    ALL("All Reasons"),
    REVIEWS("Review Req"),
    MENTIONS("Mentions"),
    ASSIGNED("Assigned"),
    CI("CI")
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
fun NotificationsScreen(
    container: AppContainer,
    deepLinkTarget: IssuePullTarget? = null,
    onConsumeDeepLink: () -> Unit = {},
    onMessage: (String) -> Unit
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<IssuePullTarget>()
    val scope = rememberCoroutineScope()

    var filter by remember { mutableStateOf(InboxFilter.UNREAD) }
    var reasonFilter by remember { mutableStateOf(ReasonFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    val liveUnread by container.unreadThreads.collectAsState()
    var threads by remember {
        mutableStateOf(
            liveUnread
                ?: container.repository.peekNotifications(all = false, participating = false)
                ?: emptyList()
        )
    }
    var isLoading by remember { mutableStateOf(threads.isEmpty() && liveUnread == null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Android System Back from Detail pane back to Inbox list
    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    LaunchedEffect(deepLinkTarget) {
        if (deepLinkTarget != null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, deepLinkTarget)
            onConsumeDeepLink()
        }
    }

    LaunchedEffect(liveUnread, filter) {
        if (filter == InboxFilter.UNREAD && liveUnread != null) {
            threads = liveUnread!!
            isLoading = false
        }
    }

    fun prefetchVisible(loaded: List<NotificationThread>) {
        val targets = loaded.mapNotNull { thread ->
            thread.subjectNumber?.let { num ->
                IssuePullTarget(thread.repoOwner, thread.repoName, num, thread.subjectTitle)
            }
        }
        container.prefetchIssuePullTargets(targets)
    }

    fun loadNotifications(forceRefresh: Boolean = true, showRefreshFeedback: Boolean = false) {
        scope.launch {
            val cached = container.repository.peekNotifications(
                all = filter == InboxFilter.ALL,
                participating = filter == InboxFilter.PARTICIPATING
            )
            if (cached != null) {
                threads = cached
                isLoading = false
            } else if (threads.isEmpty()) {
                isLoading = true
            }
            if (forceRefresh) {
                isRefreshing = true
            }
            errorMsg = null

            if (filter == InboxFilter.UNREAD && forceRefresh) {
                container.syncNotifications()
                    .onSuccess { check ->
                        threads = check.allUnread
                        isLoading = false
                        isRefreshing = false
                        if (showRefreshFeedback) {
                            onMessage(
                                if (check.allUnread.isEmpty()) "Inbox is up to date (0 unread)"
                                else "Synced ${check.allUnread.size} unread notifications"
                            )
                        }
                    }
                    .onFailure {
                        val msg = it.message ?: "Failed to load notifications"
                        errorMsg = msg
                        isLoading = false
                        isRefreshing = false
                        if (threads.isNotEmpty() || showRefreshFeedback) {
                            onMessage(msg)
                        }
                    }
            } else {
                container.repository.getNotifications(
                    all = filter == InboxFilter.ALL,
                    participating = filter == InboxFilter.PARTICIPATING,
                    forceRefresh = forceRefresh
                ).onSuccess { loaded ->
                    threads = loaded
                    if (filter == InboxFilter.UNREAD) {
                        container.updateUnreadThreads(loaded)
                    } else {
                        container.updateUnreadCount(loaded.count { it.unread })
                    }
                    prefetchVisible(loaded)
                    isLoading = false
                    isRefreshing = false
                    if (showRefreshFeedback) {
                        onMessage("Synced ${loaded.size} notifications")
                    }
                }.onFailure {
                    val msg = it.message ?: "Failed to load notifications"
                    errorMsg = msg
                    isLoading = false
                    isRefreshing = false
                    if (threads.isNotEmpty() || showRefreshFeedback) {
                        onMessage(msg)
                    }
                }
            }
        }
    }

    LaunchedEffect(filter) {
        val cached = container.repository.peekNotifications(
            all = filter == InboxFilter.ALL,
            participating = filter == InboxFilter.PARTICIPATING
        )
        if (cached != null) {
            threads = cached
            isLoading = false
        }
        loadNotifications(forceRefresh = true)
    }

    val visibleThreads = remember(threads, reasonFilter, searchQuery) {
        threads.filter { t ->
            val reasonOk = when (reasonFilter) {
                ReasonFilter.ALL -> true
                ReasonFilter.REVIEWS -> t.reason == NotificationReason.REVIEW_REQUESTED
                ReasonFilter.MENTIONS -> t.reason == NotificationReason.MENTION || t.reason == NotificationReason.TEAM_MENTION
                ReasonFilter.ASSIGNED -> t.reason == NotificationReason.ASSIGN
                ReasonFilter.CI -> t.reason == NotificationReason.CI_ACTIVITY
            }
            val q = searchQuery.trim()
            val queryOk = q.isEmpty() ||
                t.subjectTitle.contains(q, ignoreCase = true) ||
                t.repoFullName.contains(q, ignoreCase = true)
            reasonOk && queryOk
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Inbox",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Text(
                                    text = "${visibleThreads.size} / ${threads.size} threads",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            OctoConnectedButtonGroup(
                                options = InboxFilter.entries,
                                selected = filter,
                                onSelect = { filter = it },
                                labelProvider = { it.label },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ReasonFilter.entries.forEach { rf ->
                                    FilterChip(
                                        selected = reasonFilter == rf,
                                        onClick = { reasonFilter = rf },
                                        label = { Text(rf.label) }
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Filter by repo or title...") },
                                leadingIcon = { Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = if (searchQuery.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Lucide.X, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                } else null,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if ((isLoading || isRefreshing) && threads.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                OctoWavyProgress()
                            }
                        }

                        PullToRefreshBox(
                            isRefreshing = isRefreshing && threads.isNotEmpty(),
                            onRefresh = { loadNotifications(forceRefresh = true, showRefreshFeedback = true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            when {
                                isLoading && threads.isEmpty() -> {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        OctoLoadingState(message = "Syncing notifications...")
                                    }
                                }

                                errorMsg != null && threads.isEmpty() -> {
                                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = errorMsg!!,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }

                                visibleThreads.isEmpty() -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Lucide.Inbox,
                                            contentDescription = null,
                                            modifier = Modifier.size(56.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text("Inbox Zero!", style = MaterialTheme.typography.headlineSmall)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = if (filter == InboxFilter.UNREAD) {
                                                "No unread notifications. Check 'All' for already-read threads or tap Refresh."
                                            } else {
                                                "No notifications matching '${filter.label}'."
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 92.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(
                                        items = visibleThreads,
                                        key = { it.id },
                                        contentType = { "notification_thread" }
                                    ) { thread ->
                                        val dismissState = rememberSwipeToDismissBoxState(
                                            confirmValueChange = { value ->
                                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                                    threads = threads.filterNot { it.id == thread.id }
                                                    container.updateUnreadCount(threads.count { it.unread })
                                                    scope.launch {
                                                        container.repository.markNotificationDone(thread.id)
                                                            .onSuccess { onMessage("Marked done") }
                                                            .onFailure { loadNotifications() }
                                                    }
                                                    true
                                                } else false
                                            }
                                        )

                                        SwipeToDismissBox(
                                            state = dismissState,
                                            enableDismissFromStartToEnd = false,
                                            backgroundContent = {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 20.dp),
                                                    contentAlignment = Alignment.CenterEnd
                                                ) {
                                                    Icon(
                                                        Lucide.Check,
                                                        contentDescription = "Done",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        ) {
                                            NotificationThreadCard(
                                                thread = thread,
                                                selected = navigator.currentDestination?.contentKey?.let {
                                                    it.owner == thread.repoOwner &&
                                                        it.repo == thread.repoName &&
                                                        it.number == thread.subjectNumber
                                                } == true,
                                                onClick = {
                                                    val num = thread.subjectNumber
                                                    if (num != null && (thread.subjectType == NotificationSubjectType.ISSUE || thread.subjectType == NotificationSubjectType.PULL_REQUEST)) {
                                                        if (thread.unread) {
                                                            threads = threads.map {
                                                                if (it.id == thread.id) it.copy(unread = false) else it
                                                            }
                                                            container.updateUnreadCount(threads.count { it.unread })
                                                            scope.launch {
                                                                container.repository.markNotificationRead(thread.id)
                                                            }
                                                        }
                                                        scope.launch {
                                                            navigator.navigateTo(
                                                                ListDetailPaneScaffoldRole.Detail,
                                                                IssuePullTarget(
                                                                    owner = thread.repoOwner,
                                                                    repo = thread.repoName,
                                                                    number = num,
                                                                    hintTitle = thread.subjectTitle
                                                                )
                                                            )
                                                        }
                                                    } else {
                                                        onMessage("Subject type ${thread.subjectType} preview isn't inline yet.")
                                                    }
                                                },
                                                onMarkDone = {
                                                    threads = threads.filterNot { it.id == thread.id }
                                                    container.updateUnreadCount(threads.count { it.unread })
                                                    scope.launch {
                                                        container.repository.markNotificationDone(thread.id)
                                                            .onFailure { loadNotifications() }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }

                    OctoFloatingToolbar(
                        expanded = true,
                        vibrant = true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        FilledTonalIconButton(onClick = { loadNotifications(forceRefresh = true, showRefreshFeedback = true) }) {
                            Icon(Lucide.RefreshCw, contentDescription = "Refresh")
                        }
                        Spacer(Modifier.width(6.dp))
                        FilledTonalIconButton(
                            onClick = {
                                scope.launch {
                                    container.repository.markAllNotificationsRead()
                                        .onSuccess {
                                            onMessage("All notifications marked read")
                                            loadNotifications(forceRefresh = true)
                                        }
                                        .onFailure { onMessage(it.message ?: "Failed") }
                                }
                            }
                        ) {
                            Icon(Lucide.CheckCheck, contentDescription = "Mark all read")
                        }
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                val target = navigator.currentDestination?.contentKey
                if (target != null) {
                    IssuePullDetailPane(
                        target = target,
                        container = container,
                        showBackButton = navigator.canNavigateBack(),
                        onBack = { scope.launch { navigator.navigateBack() } },
                        onMessage = onMessage
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Lucide.BellOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Select a notification to triage",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun NotificationThreadCard(
    thread: NotificationThread,
    selected: Boolean,
    onClick: () -> Unit,
    onMarkDone: () -> Unit
) {
    val icon = when (thread.subjectType) {
        NotificationSubjectType.PULL_REQUEST -> Lucide.GitPullRequest
        NotificationSubjectType.ISSUE -> Lucide.CircleDot
        NotificationSubjectType.RELEASE -> Lucide.Tag
        else -> Lucide.GitCommitHorizontal
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                thread.unread -> MaterialTheme.colorScheme.surfaceContainerHigh
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            UserAvatar(
                avatarUrl = thread.repoOwnerAvatarUrl,
                login = thread.repoOwner,
                sizeDp = 34
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (thread.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = thread.repoFullName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveStatusPill(
                        label = thread.reason.name.lowercase().replace('_', ' '),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = thread.subjectTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (thread.unread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onMarkDone, modifier = Modifier.size(32.dp)) {
                Icon(
                    Lucide.Check,
                    contentDescription = "Mark done",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
