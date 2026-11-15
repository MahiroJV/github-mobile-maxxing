package dev.octoexpress.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Building2
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Link2
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.UserCheck
import com.composables.icons.lucide.UserPlus
import dev.octoexpress.app.AppContainer
import dev.octoexpress.app.IssuePullTarget
import dev.octoexpress.auth.openUrlInBrowser
import dev.octoexpress.model.ActivityEvent
import dev.octoexpress.model.ContributionCalendar
import dev.octoexpress.model.OrganizationSummary
import dev.octoexpress.model.RepoSummary
import dev.octoexpress.model.UserProfile
import dev.octoexpress.model.UserSummary
import dev.octoexpress.ui.components.ExpressiveStatusPill
import dev.octoexpress.ui.components.OctoConnectedButtonGroup
import dev.octoexpress.ui.components.OctoLoadingState
import dev.octoexpress.ui.components.OctoWavyProgress
import dev.octoexpress.ui.components.UserAvatar
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private enum class ProfileTab(val label: String) {
    REPOS("Repos"),
    STARRED("Starred"),
    ACTIVITY("Activity"),
    FOLLOWERS("Followers"),
    FOLLOWING("Following")
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
fun UserProfileScreen(
    login: String,
    container: AppContainer,
    onBack: () -> Unit,
    onOpenRepo: (RepoSummary) -> Unit,
    onOpenIssueOrPull: (IssuePullTarget) -> Unit,
    onOpenUser: (String) -> Unit,
    onMessage: (String) -> Unit
) {
    val viewerProfile by container.viewerProfile.collectAsState()
    val session by container.sessionManager.session.collectAsState()
    val selfLogin = viewerProfile?.login ?: session?.userLogin

    var profile by remember(login) { mutableStateOf(container.repository.peekUserProfile(login)) }
    var isFollowing by remember(login) { mutableStateOf(profile?.viewerIsFollowing ?: false) }
    var orgs by remember(login) { mutableStateOf<List<OrganizationSummary>>(emptyList()) }
    var calendar by remember(login) { mutableStateOf(container.repository.peekContributionCalendar(login)) }
    var activeTab by remember(login) { mutableStateOf(ProfileTab.REPOS) }

    var repos by remember(login) { mutableStateOf(container.repository.peekUserRepositories(login).orEmpty()) }
    var starred by remember(login) { mutableStateOf<List<RepoSummary>>(emptyList()) }
    var events by remember(login) { mutableStateOf<List<ActivityEvent>>(emptyList()) }
    var followers by remember(login) { mutableStateOf<List<UserSummary>>(emptyList()) }
    var following by remember(login) { mutableStateOf<List<UserSummary>>(emptyList()) }

    var isLoading by remember(login) { mutableStateOf(profile == null) }
    var tabLoading by remember(login) { mutableStateOf(false) }
    var isRefreshing by remember(login) { mutableStateOf(false) }
    var errorMsg by remember(login) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = true) {
        onBack()
    }

    fun loadProfile(forceRefresh: Boolean = false) {
        scope.launch {
            if (profile == null) isLoading = true
            if (forceRefresh) isRefreshing = true
            errorMsg = null
            coroutineScope {
                val profJob = async { container.repository.getUserProfile(login, forceRefresh = forceRefresh) }
                val orgsJob = async { container.repository.getUserOrganizations(login, forceRefresh = forceRefresh) }
                val calJob = async { container.repository.getContributionCalendar(login, forceRefresh = forceRefresh) }

                profJob.await()
                    .onSuccess { p ->
                        profile = p
                        isFollowing = p.viewerIsFollowing
                        container.repository.getUserRepositories(login, isOrganization = p.isOrganization, forceRefresh = forceRefresh)
                            .onSuccess { repos = it }
                    }
                    .onFailure { errorMsg = it.message ?: "Failed to load profile" }

                orgsJob.await().onSuccess { orgs = it }
                calJob.await().onSuccess { calendar = it }
            }
            isLoading = false
            isRefreshing = false
        }
    }

    fun loadActiveTab(tab: ProfileTab, forceRefresh: Boolean = false) {
        val currentProfile = profile ?: return
        scope.launch {
            tabLoading = true
            when (tab) {
                ProfileTab.REPOS -> container.repository.getUserRepositories(login, currentProfile.isOrganization, forceRefresh)
                    .onSuccess { repos = it }
                ProfileTab.STARRED -> container.repository.getUserStarredRepositories(login, forceRefresh)
                    .onSuccess { starred = it }
                ProfileTab.ACTIVITY -> container.repository.getUserEvents(login, forceRefresh)
                    .onSuccess { events = it }
                ProfileTab.FOLLOWERS -> container.repository.getUserFollowers(login, forceRefresh)
                    .onSuccess { followers = it }
                ProfileTab.FOLLOWING -> container.repository.getUserFollowing(login, forceRefresh)
                    .onSuccess { following = it }
            }
            tabLoading = false
        }
    }

    LaunchedEffect(login) {
        loadProfile(forceRefresh = false)
    }

    LaunchedEffect(activeTab, profile?.login) {
        if (profile != null) {
            loadActiveTab(activeTab, forceRefresh = false)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Lucide.ArrowLeft, contentDescription = "Back")
                    }
                    Text(
                        text = "@$login",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { loadProfile(forceRefresh = true) }) {
                        Icon(Lucide.RefreshCw, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                    }
                    if (!profile?.htmlUrl.isNullOrBlank()) {
                        IconButton(onClick = { openUrlInBrowser(profile!!.htmlUrl) }) {
                            Icon(Lucide.ExternalLink, contentDescription = "Open in browser", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (isLoading || isRefreshing || tabLoading) {
                    OctoWavyProgress(modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                loadProfile(forceRefresh = true)
                loadActiveTab(activeTab, forceRefresh = true)
            },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && profile == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        OctoLoadingState(message = "Loading @$login...")
                    }
                }

                errorMsg != null && profile == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { loadProfile(forceRefresh = true) }) {
                            Text("Retry")
                        }
                    }
                }

                profile != null -> {
                    val p = profile!!
                    val isSelf = selfLogin.equals(p.login, ignoreCase = true)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "profile_header", contentType = "header") {
                            ElevatedCard(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        UserAvatar(p.avatarUrl, p.login, sizeDp = 64)
                                        Spacer(Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    text = p.name ?: "@${p.login}",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (p.isOrganization) {
                                                    ExpressiveStatusPill(
                                                        label = "Org",
                                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "@${p.login}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (!isSelf && !p.isOrganization) {
                                            if (isFollowing) {
                                                OutlinedButton(
                                                    onClick = {
                                                        isFollowing = false
                                                        scope.launch {
                                                            container.repository.setFollowingUser(p.login, false)
                                                                .onSuccess { onMessage("Unfollowed @${p.login}") }
                                                                .onFailure {
                                                                    isFollowing = true
                                                                    onMessage(it.message ?: "Follow update failed")
                                                                }
                                                        }
                                                    }
                                                ) {
                                                    Icon(Lucide.UserCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Following")
                                                }
                                            } else {
                                                FilledTonalButton(
                                                    onClick = {
                                                        isFollowing = true
                                                        scope.launch {
                                                            container.repository.setFollowingUser(p.login, true)
                                                                .onSuccess { onMessage("Following @${p.login}") }
                                                                .onFailure {
                                                                    isFollowing = false
                                                                    onMessage(it.message ?: "Follow update failed")
                                                                }
                                                        }
                                                    }
                                                ) {
                                                    Icon(Lucide.UserPlus, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Follow")
                                                }
                                            }
                                        }
                                    }

                                    if (!p.bio.isNullOrBlank()) {
                                        Text(p.bio!!, style = MaterialTheme.typography.bodyMedium)
                                    }

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        p.company?.takeIf { it.isNotBlank() }?.let { c ->
                                            MetaRow(Lucide.Building2, c)
                                        }
                                        p.location?.takeIf { it.isNotBlank() }?.let { loc ->
                                            MetaRow(Lucide.MapPin, loc)
                                        }
                                        p.blogUrl?.takeIf { it.isNotBlank() }?.let { blog ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.clickable {
                                                    val url = if (blog.startsWith("http")) blog else "https://$blog"
                                                    openUrlInBrowser(url)
                                                }
                                            ) {
                                                Icon(Lucide.Link2, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(4.dp))
                                                Text(blog, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = activeTab == ProfileTab.REPOS,
                                            onClick = { activeTab = ProfileTab.REPOS },
                                            label = { Text("${p.publicReposCount} repos") }
                                        )
                                        FilterChip(
                                            selected = activeTab == ProfileTab.FOLLOWERS,
                                            onClick = { activeTab = ProfileTab.FOLLOWERS },
                                            label = { Text("${p.followersCount} followers") }
                                        )
                                        if (!p.isOrganization) {
                                            FilterChip(
                                                selected = activeTab == ProfileTab.FOLLOWING,
                                                onClick = { activeTab = ProfileTab.FOLLOWING },
                                                label = { Text("${p.followingCount} following") }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (orgs.isNotEmpty()) {
                            item(key = "orgs", contentType = "orgs") {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Organizations", style = MaterialTheme.typography.titleSmall)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(orgs, key = { it.login }) { org ->
                                            ElevatedCard(
                                                shape = MaterialTheme.shapes.medium,
                                                modifier = Modifier.clickable { onOpenUser(org.login) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    UserAvatar(org.avatarUrl, org.login, sizeDp = 24)
                                                    Text(org.login, style = MaterialTheme.typography.labelLarge)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (!p.isOrganization && calendar != null) {
                            item(key = "profile_calendar", contentType = "calendar") {
                                ContributionHeatmapCard(calendar = calendar!!)
                            }
                        }

                        item(key = "profile_tabs", contentType = "tabs") {
                            val visibleTabs = if (p.isOrganization) {
                                listOf(ProfileTab.REPOS, ProfileTab.ACTIVITY)
                            } else {
                                ProfileTab.entries
                            }
                            OctoConnectedButtonGroup(
                                options = visibleTabs,
                                selected = if (activeTab in visibleTabs) activeTab else ProfileTab.REPOS,
                                onSelect = { activeTab = it },
                                labelProvider = { it.label },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        when (activeTab) {
                            ProfileTab.REPOS -> {
                                if (repos.isEmpty()) {
                                    item(key = "empty_repos") { ProfileEmptyNotice("No public repositories.") }
                                } else {
                                    items(repos, key = { "pr_${it.fullName}" }, contentType = { "repo" }) { repo ->
                                        RepoSummaryCard(repo = repo, onClick = { onOpenRepo(repo) })
                                    }
                                }
                            }
                            ProfileTab.STARRED -> {
                                if (starred.isEmpty()) {
                                    item(key = "empty_starred") { ProfileEmptyNotice("No starred repositories.") }
                                } else {
                                    items(starred, key = { "ps_${it.fullName}" }, contentType = { "repo" }) { repo ->
                                        RepoSummaryCard(repo = repo, onClick = { onOpenRepo(repo) })
                                    }
                                }
                            }
                            ProfileTab.ACTIVITY -> {
                                if (events.isEmpty()) {
                                    item(key = "empty_activity") { ProfileEmptyNotice("No recent public activity.") }
                                } else {
                                    items(events, key = { "pe_${it.id}" }, contentType = { "event" }) { ev ->
                                        ActivityEventCard(
                                            event = ev,
                                            onClick = {
                                                val num = ev.targetIssueNumber
                                                if (num != null && ev.repoOwner.isNotBlank()) {
                                                    onOpenIssueOrPull(IssuePullTarget(ev.repoOwner, ev.repoName, num, ev.detailTitle))
                                                } else if (ev.repoOwner.isNotBlank()) {
                                                    onOpenRepo(
                                                        RepoSummary(
                                                            id = ev.repoFullName,
                                                            name = ev.repoName,
                                                            owner = ev.repoOwner,
                                                            fullName = ev.repoFullName
                                                        )
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            ProfileTab.FOLLOWERS -> {
                                if (followers.isEmpty()) {
                                    item(key = "empty_followers") { ProfileEmptyNotice("No followers yet.") }
                                } else {
                                    items(followers, key = { "fl_${it.login}" }, contentType = { "user" }) { user ->
                                        UserSummaryCard(user = user, onClick = { onOpenUser(user.login) })
                                    }
                                }
                            }
                            ProfileTab.FOLLOWING -> {
                                if (following.isEmpty()) {
                                    item(key = "empty_following") { ProfileEmptyNotice("Not following anyone yet.") }
                                } else {
                                    items(following, key = { "fg_${it.login}" }, contentType = { "user" }) { user ->
                                        UserSummaryCard(user = user, onClick = { onOpenUser(user.login) })
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

@Composable
fun UserSummaryCard(
    user: UserSummary,
    onClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UserAvatar(user.avatarUrl, user.login, sizeDp = 40)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "@${user.login}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (user.htmlUrl.isNotBlank()) {
                    Text(
                        text = user.htmlUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileEmptyNotice(message: String) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }
}
