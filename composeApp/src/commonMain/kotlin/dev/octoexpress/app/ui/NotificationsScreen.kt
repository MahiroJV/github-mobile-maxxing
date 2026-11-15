package dev.octoexpress.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import dev.octoexpress.ui.components.OctoDropdownSelector
import dev.octoexpress.ui.components.OctoFloatingToolbar
import dev.octoexpress.ui.components.OctoLoadingState
import dev.octoexpress.ui.components.OctoPredictiveBackContainer
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
    ExperimentalMaterial3ExpressiveApi::class
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
    var isFilterInputActive by remember { mutableStateOf(false) }
    val filterFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
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

    LaunchedEffect(isFilterInputActive) {
        if (isFilterInputActive) {
            filterFocusRequester.requestFocus()
        }
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

    var requestSeq by remember { mutableStateOf(0) }

    fun loadNotifications(forceRefresh: Boolean = true, showRefreshFeedback: Boolean = false) {
        val reqFilter = filter
        val seq = ++requestSeq
        val cached = container.repository.peekNotifications(
            all = reqFilter == InboxFilter.ALL,
            participating = reqFilter == InboxFilter.PARTICIPATING
        )
        if (cached != null) {
            threads = cached
            isLoading = false
        } else {
            threads = emptyList()
            isLoading = true
        }
        isRefreshing = forceRefresh && cached != null
        errorMsg = null

        scope.launch {
            if (reqFilter == InboxFilter.UNREAD && forceRefresh) {
                container.syncNotifications()
                    .onSuccess { check ->
                        if (seq != requestSeq) return@onSuccess
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
                        if (seq != requestSeq) return@onFailure
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
                    all = reqFilter == InboxFilter.ALL,
                    participating = reqFilter == InboxFilter.PARTICIPATING,
                    forceRefresh = forceRefresh
                ).onSuccess { loaded ->
                    if (seq != requestSeq) return@onSuccess
                    threads = loaded
                    if (reqFilter == InboxFilter.UNREAD) {
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
                    if (seq != requestSeq) return@onFailure
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

    val headerTracker = remember { RepoHeaderCollapseTracker() }
    val headerNestedScroll = remember(headerTracker) {
        headerTracker.toNestedScrollConnection { !isFilterInputActive && searchQuery.isEmpty() }
    }
    val showExpandedHeader = !headerTracker.isCollapsed || isFilterInputActive || searchQuery.isNotEmpty()
    val headerVerticalPadding by animateDpAsState(if (showExpandedHeader) 14.dp else 6.dp)

    LaunchedEffect(filter, reasonFilter) {
        headerTracker.expand()
    }

    OctoPredictiveBackContainer(
        enabled = isFilterInputActive || navigator.canNavigateBack(),
        onBack = {
            when {
                isFilterInputActive -> {
                    focusManager.clearFocus()
                    isFilterInputActive = false
                }
                navigator.canNavigateBack() -> scope.launch { navigator.navigateBack() }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = headerVerticalPadding)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Inbox",
                                        style = if (showExpandedHeader) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge
                                    )
                                    OctoCollapsibleSection(visible = showExpandedHeader) {
                                        Text(
                                            text = "${visibleThreads.size} / ${threads.size} threads",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                OctoDropdownSelector(
                                    options = InboxFilter.entries,
                                    selected = filter,
                                    onSelect = { filter = it },
                                    labelProvider = { it.label }
                                )
                            }

                            OctoCollapsibleSection(visible = showExpandedHeader) {
                                Column {
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
                                    if (!isFilterInputActive && searchQuery.isEmpty()) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                                .clickable { isFilterInputActive = true }
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    Lucide.Search,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "Filter by repo or title...",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    } else {
                                        OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            placeholder = { Text("Filter by repo or title...") },
                                            leadingIcon = { Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                            trailingIcon = {
                                                IconButton(
                                                    onClick = {
                                                        searchQuery = ""
                                                        focusManager.clearFocus()
                                                        isFilterInputActive = false
                                                    }
                                                ) {
                                                    Icon(Lucide.X, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                                }
                                            },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions = KeyboardActions(
                                                onSearch = {
                                                    focusManager.clearFocus()
                                                    if (searchQuery.isEmpty()) isFilterInputActive = false
                                                }
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(filterFocusRequester)
                                        )
                                    }
                                }
                            }

                            if ((isLoading || isRefreshing) && threads.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                OctoWavyProgress()
                            }
                        }

                        PullToRefreshBox(
                            isRefreshing = isRefreshing && threads.isNotEmpty(),
                            onRefresh = { loadNotifications(forceRefresh = true, showRefreshFeedback = true) },
                            modifier = Modifier.fillMaxSize().nestedScroll(headerNestedScroll)
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
                    if (thread.unread) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = androidx.compose.foundation.shape.CircleShape,
                            modifier = Modifier.size(8.dp)
                        ) {}
                    }
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
