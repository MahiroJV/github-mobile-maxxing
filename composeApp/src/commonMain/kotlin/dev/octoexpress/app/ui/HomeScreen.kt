package dev.octoexpress.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.BookMarked
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.FileCode
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.FolderGit2
import com.composables.icons.lucide.GitBranch
import com.composables.icons.lucide.GitCommitHorizontal
import com.composables.icons.lucide.GitFork
import com.composables.icons.lucide.GitPullRequest
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.User
import com.composables.icons.lucide.Users
import dev.octoexpress.app.AppContainer
import dev.octoexpress.app.IssuePullTarget
import dev.octoexpress.model.ActivityEvent
import dev.octoexpress.model.ActivityEventKind
import dev.octoexpress.model.ContributionCalendar
import dev.octoexpress.model.RepoSummary
import dev.octoexpress.ui.components.OctoDropdownSelector
import dev.octoexpress.ui.components.OctoFilledIcons
import dev.octoexpress.ui.components.OctoLoadingState
import dev.octoexpress.ui.components.OctoWavyProgress
import dev.octoexpress.ui.components.formatCompactCount
import dev.octoexpress.ui.components.UserAvatar
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private enum class FeedTab(val label: String) {
    FOLLOWING("Following"),
    MY_ACTIVITY("My Activity")
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
fun HomeScreen(
    container: AppContainer,
    unreadCount: Int,
    onNavigateInbox: () -> Unit,
    onNavigateIssuesPrs: () -> Unit,
    onNavigateRepos: () -> Unit,
    onOpenGists: () -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onMessage: (String) -> Unit
) {
    var selectedRepo by remember { mutableStateOf<RepoSummary?>(null) }
    var selectedIssueTarget by remember { mutableStateOf<IssuePullTarget?>(null) }

    BackHandler(enabled = selectedIssueTarget != null || selectedRepo != null) {
        when {
            selectedIssueTarget != null -> selectedIssueTarget = null
            selectedRepo != null -> selectedRepo = null
        }
    }

    when {
        selectedIssueTarget != null -> {
            IssuePullDetailPane(
                target = selectedIssueTarget!!,
                container = container,
                showBackButton = true,
                onBack = { selectedIssueTarget = null },
                onMessage = onMessage
            )
        }

        selectedRepo != null -> {
            RepoDetailScreen(
                repo = selectedRepo!!,
                container = container,
                onBack = { selectedRepo = null },
                onOpenIssueOrPull = { selectedIssueTarget = it },
                onMessage = onMessage
            )
        }

        else -> {
            HomeDashboardPane(
                container = container,
                unreadCount = unreadCount,
                onNavigateInbox = onNavigateInbox,
                onNavigateIssuesPrs = onNavigateIssuesPrs,
                onNavigateRepos = onNavigateRepos,
                onOpenRepo = { owner, name ->
                    selectedRepo = RepoSummary(
                        id = "$owner/$name",
                        name = name,
                        owner = owner,
                        fullName = "$owner/$name"
                    )
                },
                onOpenIssueOrPull = { selectedIssueTarget = it },
                onOpenGists = onOpenGists,
                onOpenUser = onOpenUser,
                onMessage = onMessage
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeDashboardPane(
    container: AppContainer,
    unreadCount: Int,
    onNavigateInbox: () -> Unit,
    onNavigateIssuesPrs: () -> Unit,
    onNavigateRepos: () -> Unit,
    onOpenRepo: (owner: String, name: String) -> Unit,
    onOpenIssueOrPull: (IssuePullTarget) -> Unit,
    onOpenGists: () -> Unit,
    onOpenUser: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    val profile by container.viewerProfile.collectAsState()
    val session by container.sessionManager.session.collectAsState()
    val pinnedRepoNames by container.sessionManager.pinnedRepos.collectAsState()
    val login = profile?.login ?: session?.userLogin.orEmpty()

    var feedTab by remember { mutableStateOf(FeedTab.FOLLOWING) }
    var calendar by remember(login) {
        mutableStateOf(if (login.isNotBlank()) container.repository.peekContributionCalendar(login) else null)
    }
    var recentRepos by remember {
        mutableStateOf(container.repository.peekViewerRepositories().orEmpty())
    }
    var feedEvents by remember(login, feedTab) {
        mutableStateOf(
            if (login.isBlank()) emptyList()
            else when (feedTab) {
                FeedTab.FOLLOWING -> container.repository.peekReceivedEvents(login).orEmpty()
                FeedTab.MY_ACTIVITY -> container.repository.peekUserEvents(login).orEmpty()
            }
        )
    }
    var isLoading by remember { mutableStateOf(feedEvents.isEmpty() && calendar == null && login.isNotBlank()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var requestSeq by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    fun loadHome(forceRefresh: Boolean = false) {
        if (login.isBlank()) return
        val reqTab = feedTab
        val seq = ++requestSeq
        val cachedCal = container.repository.peekContributionCalendar(login)
        val cachedRepos = container.repository.peekViewerRepositories()
        val cachedFeed = when (reqTab) {
            FeedTab.FOLLOWING -> container.repository.peekReceivedEvents(login)
            FeedTab.MY_ACTIVITY -> container.repository.peekUserEvents(login)
        }
        if (cachedCal != null) calendar = cachedCal
        if (cachedRepos != null) recentRepos = cachedRepos
        if (cachedFeed != null) {
            feedEvents = cachedFeed
            isLoading = false
        } else {
            feedEvents = emptyList()
            isLoading = true
        }
        isRefreshing = forceRefresh
        errorMsg = null

        scope.launch {
            coroutineScope {
                val calJob = async { container.repository.getContributionCalendar(login, forceRefresh = forceRefresh) }
                val reposJob = async { container.repository.getViewerRepositories(forceRefresh = forceRefresh) }
                val feedJob = async {
                    when (reqTab) {
                        FeedTab.FOLLOWING -> container.repository.getReceivedEvents(login, forceRefresh = forceRefresh)
                        FeedTab.MY_ACTIVITY -> container.repository.getUserEvents(login, forceRefresh = forceRefresh)
                    }
                }
                calJob.await().onSuccess { if (seq == requestSeq) calendar = it }
                reposJob.await().onSuccess { if (seq == requestSeq) recentRepos = it }
                feedJob.await()
                    .onSuccess { if (seq == requestSeq) feedEvents = it }
                    .onFailure {
                        if (seq == requestSeq) {
                            errorMsg = it.message ?: "Failed to load activity feed"
                            if (forceRefresh) onMessage(errorMsg!!)
                        }
                    }
            }
            if (seq == requestSeq) {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(login, feedTab) {
        loadHome(forceRefresh = false)
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(
                    avatarUrl = profile?.avatarUrl.orEmpty(),
                    login = login.ifBlank { "?" },
                    sizeDp = 36,
                    modifier = Modifier.clickable(enabled = login.isNotBlank()) { onOpenUser(login) }
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = login.isNotBlank()) { onOpenUser(login) }
                ) {
                    Text(
                        text = profile?.name ?: "@${login.ifBlank { "GitHub" }}",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (login.isNotBlank()) {
                        Text(
                            text = "@$login",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onOpenGists) {
                    Icon(Lucide.FileCode, contentDescription = "Gists", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { loadHome(forceRefresh = true) }) {
                    Icon(Lucide.RefreshCw, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                }
            }

            if ((isLoading || isRefreshing) && (feedEvents.isNotEmpty() || calendar != null)) {
                Spacer(Modifier.height(8.dp))
                OctoWavyProgress()
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing && (feedEvents.isNotEmpty() || calendar != null),
            onRefresh = { loadHome(forceRefresh = true) },
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading && feedEvents.isEmpty() && calendar == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OctoLoadingState(message = "Loading GitHub home...")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    calendar?.let { cal ->
                        item(key = "contributions", contentType = "contribution_heatmap") {
                            ContributionHeatmapCard(calendar = cal)
                        }
                    }

                    if (pinnedRepoNames.isNotEmpty()) {
                        item(key = "pinned_repos_strip", contentType = "pinned_repos") {
                            val repoByFullName = remember(recentRepos) {
                                recentRepos.associateBy { it.fullName.lowercase() }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = OctoFilledIcons.Bookmark,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Pinned Bookmarks",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pinnedRepoNames.forEach { fullName ->
                                        val owner = fullName.substringBefore('/', "")
                                        val name = fullName.substringAfter('/', fullName)
                                        val summary = repoByFullName[fullName.lowercase()]
                                        AssistChip(
                                            onClick = { if (owner.isNotBlank()) onOpenRepo(owner, name) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (summary?.isPrivate == true) Lucide.Lock else Lucide.Bookmark,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            label = { Text(fullName, maxLines = 1) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (recentRepos.isNotEmpty()) {
                        item(key = "recent_repos_strip", contentType = "recent_repos") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Lucide.FolderGit2, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = "Recent Repositories",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(124.dp)
                                ) {
                                    items(
                                        items = recentRepos.take(10),
                                        key = { it.id },
                                        contentType = { "recent_repo_chip" }
                                    ) { repo ->
                                        ElevatedCard(
                                            shape = MaterialTheme.shapes.large,
                                            colors = CardDefaults.elevatedCardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                            ),
                                            modifier = Modifier
                                                .width(216.dp)
                                                .fillMaxHeight()
                                                .clickable { onOpenRepo(repo.owner, repo.name) }
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize().padding(14.dp),
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (repo.isPrivate) Lucide.Lock else Lucide.BookMarked,
                                                            contentDescription = if (repo.isPrivate) "Private repository" else "Public repository",
                                                            modifier = Modifier.size(13.dp),
                                                            tint = if (repo.isPrivate) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                                        )
                                                        Text(
                                                            text = repo.fullName,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        if (repo.isFork) {
                                                            Icon(
                                                                Lucide.GitFork,
                                                                contentDescription = "Fork",
                                                                modifier = Modifier.size(12.dp),
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = repo.description?.takeIf { it.isNotBlank() }.orEmpty(),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        minLines = 2,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    repo.language?.let { lang ->
                                                        Text(
                                                            text = lang,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                    }
                                                    if (repo.viewerHasStarred) {
                                                        Surface(
                                                            color = Color(0xFFEAB308).copy(alpha = 0.18f),
                                                            shape = MaterialTheme.shapes.small
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                            ) {
                                                                Icon(
                                                                    OctoFilledIcons.Star,
                                                                    contentDescription = "Starred",
                                                                    tint = Color(0xFFEAB308),
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                                Text(
                                                                    formatCompactCount(repo.stargazersCount),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = Color(0xFFB45309)
                                                                )
                                                            }
                                                        }
                                                    } else {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                        ) {
                                                            Icon(
                                                                Lucide.Star,
                                                                contentDescription = "Not starred",
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Text(
                                                                formatCompactCount(repo.stargazersCount),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "feed_header", contentType = "feed_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Lucide.Activity, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Activity Feed",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            OctoDropdownSelector(
                                options = FeedTab.entries,
                                selected = feedTab,
                                onSelect = { feedTab = it },
                                labelProvider = { it.label },
                                iconProvider = { tab ->
                                    if (tab == FeedTab.FOLLOWING) Lucide.Users else Lucide.User
                                }
                            )
                        }
                    }

                    if (feedEvents.isEmpty()) {
                        item(key = "feed_empty", contentType = "empty") {
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = errorMsg ?: "No recent activity events yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (errorMsg != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = feedEvents,
                            key = { "${feedTab.name}_${it.id}" },
                            contentType = { "activity_event" }
                        ) { event ->
                            ActivityEventCard(
                                event = event,
                                onOpenActor = { onOpenUser(event.actor.login) },
                                onClick = {
                                    val num = event.targetIssueNumber
                                    if (num != null && event.repoOwner.isNotBlank()) {
                                        onOpenIssueOrPull(
                                            IssuePullTarget(event.repoOwner, event.repoName, num, event.detailTitle)
                                        )
                                    } else if (event.repoOwner.isNotBlank()) {
                                        onOpenRepo(event.repoOwner, event.repoName)
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

@Composable
fun ContributionHeatmapCard(calendar: ContributionCalendar) {
    val baseGreen = Color(0xFF2EA043)
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Lucide.Flame, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Contributions", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "${calendar.totalContributions} in the last year",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState(Int.MAX_VALUE)),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                calendar.weeks.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.days.forEach { day ->
                            val cellColor = when (day.level) {
                                1 -> baseGreen.copy(alpha = 0.28f)
                                2 -> baseGreen.copy(alpha = 0.50f)
                                3 -> baseGreen.copy(alpha = 0.75f)
                                4 -> baseGreen
                                else -> emptyColor
                            }
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(cellColor)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Less", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                listOf(emptyColor, baseGreen.copy(0.28f), baseGreen.copy(0.50f), baseGreen.copy(0.75f), baseGreen).forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(c)
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text("More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ActivityEventCard(
    event: ActivityEvent,
    onOpenActor: () -> Unit = {},
    onClick: () -> Unit
) {
    val (icon: ImageVector, iconTint: Color) = when (event.kind) {
        ActivityEventKind.PUSH -> Lucide.GitCommitHorizontal to MaterialTheme.colorScheme.primary
        ActivityEventKind.PULL_REQUEST, ActivityEventKind.PULL_REQUEST_REVIEW -> Lucide.GitPullRequest to Color(0xFF7C3AED)
        ActivityEventKind.ISSUES -> Lucide.CircleDot to Color(0xFF16A34A)
        ActivityEventKind.ISSUE_COMMENT -> Lucide.MessageSquare to MaterialTheme.colorScheme.secondary
        ActivityEventKind.WATCH -> OctoFilledIcons.Star to Color(0xFFEAB308)
        ActivityEventKind.FORK -> Lucide.GitFork to Color(0xFF0284C7)
        ActivityEventKind.CREATE -> Lucide.GitBranch to Color(0xFF16A34A)
        ActivityEventKind.DELETE -> Lucide.Trash2 to MaterialTheme.colorScheme.error
        ActivityEventKind.RELEASE -> Lucide.Tag to MaterialTheme.colorScheme.tertiary
        ActivityEventKind.PUBLIC -> Lucide.Globe to MaterialTheme.colorScheme.primary
        ActivityEventKind.MEMBER -> Lucide.Users to MaterialTheme.colorScheme.secondary
        ActivityEventKind.OTHER -> Lucide.Activity to MaterialTheme.colorScheme.primary
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UserAvatar(
                event.actor.avatarUrl,
                event.actor.login,
                sizeDp = 32,
                modifier = Modifier.clickable(onClick = onOpenActor)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = iconTint.copy(alpha = 0.14f),
                        shape = CircleShape,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = iconTint)
                        }
                    }
                    Text(
                        text = "@${event.actor.login} ${event.summaryText}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (event.createdAt.length >= 10) {
                        Text(
                            text = event.createdAt.take(10),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = event.repoFullName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (!event.detailTitle.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = event.detailTitle!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
