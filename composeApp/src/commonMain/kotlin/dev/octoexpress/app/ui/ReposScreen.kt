package dev.octoexpress.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.File
import com.composables.icons.lucide.FileCode
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.GitBranch
import com.composables.icons.lucide.GitFork
import com.composables.icons.lucide.GitPullRequest
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.X
import dev.octoexpress.app.AppContainer
import dev.octoexpress.app.IssuePullTarget
import dev.octoexpress.model.CodeSearchHit
import dev.octoexpress.model.FileContent
import dev.octoexpress.model.GlobalSearchKind
import dev.octoexpress.model.IssueOrPullKind
import dev.octoexpress.model.IssueOrPullState
import dev.octoexpress.model.IssueOrPullSummary
import dev.octoexpress.model.IssueStateFilter
import dev.octoexpress.model.RepoDetail
import dev.octoexpress.model.RepoSummary
import dev.octoexpress.model.TreeEntry
import dev.octoexpress.model.TreeEntryType
import dev.octoexpress.model.UserSummary
import dev.octoexpress.ui.components.CodeViewer
import dev.octoexpress.ui.components.ExpressiveStatusPill
import dev.octoexpress.ui.components.GitHubMarkdown
import dev.octoexpress.ui.components.OctoConnectedButtonGroup
import dev.octoexpress.ui.components.OctoLoadingState
import dev.octoexpress.ui.components.OctoWavyProgress
import dev.octoexpress.ui.components.UserAvatar
import dev.octoexpress.ui.components.expressiveAnimateContentSize
import dev.octoexpress.ui.components.parseHexColor
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private enum class RepoListTab(val label: String) {
    MINE("My Repos"),
    STARRED("Starred"),
    SEARCH("Search")
}

private enum class RepoDetailTab(val label: String) {
    CODE("Code"),
    ISSUES("Issues"),
    PULLS("PRs")
}

private val TRENDING_LANGUAGES = listOf("All", "Kotlin", "TypeScript", "Python", "Rust", "Go", "Swift")

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ReposScreen(
    container: AppContainer,
    onOpenUser: (String) -> Unit = {},
    onMessage: (String) -> Unit
) {
    var selectedRepo by remember { mutableStateOf<RepoSummary?>(null) }
    var selectedInitialFilePath by remember { mutableStateOf<String?>(null) }
    var selectedIssueTarget by remember { mutableStateOf<IssuePullTarget?>(null) }

    BackHandler(enabled = selectedIssueTarget != null) {
        selectedIssueTarget = null
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
                initialFilePath = selectedInitialFilePath,
                onBack = {
                    selectedRepo = null
                    selectedInitialFilePath = null
                },
                onOpenIssueOrPull = { selectedIssueTarget = it },
                onOpenUser = onOpenUser,
                onMessage = onMessage
            )
        }

        else -> {
            RepoListPane(
                container = container,
                onSelectRepo = {
                    selectedInitialFilePath = null
                    selectedRepo = it
                },
                onSelectCodeHit = { hit ->
                    selectedInitialFilePath = hit.path
                    selectedRepo = RepoSummary(
                        id = "code_${hit.repoFullName}",
                        name = hit.repoName,
                        owner = hit.repoOwner,
                        fullName = hit.repoFullName
                    )
                },
                onSelectIssuePull = { selectedIssueTarget = it },
                onSelectUser = onOpenUser
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoListPane(
    container: AppContainer,
    onSelectRepo: (RepoSummary) -> Unit,
    onSelectCodeHit: (CodeSearchHit) -> Unit,
    onSelectIssuePull: (IssuePullTarget) -> Unit,
    onSelectUser: (String) -> Unit
) {
    var activeTab by remember { mutableStateOf(RepoListTab.MINE) }
    var searchKind by remember { mutableStateOf(GlobalSearchKind.REPOSITORIES) }
    var searchQuery by remember { mutableStateOf("") }
    var submittedSearchQuery by remember { mutableStateOf("") }
    var trendingLang by remember { mutableStateOf("All") }

    var repos by remember {
        mutableStateOf(container.repository.peekViewerRepositories().orEmpty())
    }
    var issueHits by remember { mutableStateOf<List<IssueOrPullSummary>>(emptyList()) }
    var codeHits by remember { mutableStateOf<List<CodeSearchHit>>(emptyList()) }
    var userHits by remember { mutableStateOf<List<UserSummary>>(emptyList()) }
    var trendingRepos by remember {
        mutableStateOf(container.repository.peekTrendingRepositories(null).orEmpty())
    }

    var isLoading by remember { mutableStateOf(repos.isEmpty()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var requestSeq by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    fun loadTrending(langLabel: String = trendingLang, forceRefresh: Boolean = false) {
        val currentSeq = ++requestSeq
        val apiLang = langLabel.takeUnless { it == "All" }
        scope.launch {
            val cached = container.repository.peekTrendingRepositories(apiLang)
            if (cached != null) {
                trendingRepos = cached
                isLoading = false
            } else {
                trendingRepos = emptyList()
                isLoading = true
            }
            if (forceRefresh) isRefreshing = true
            errorMsg = null
            val res = container.repository.getTrendingRepositories(apiLang, forceRefresh = forceRefresh)
            if (requestSeq != currentSeq || activeTab != RepoListTab.SEARCH || submittedSearchQuery.isNotBlank()) {
                return@launch
            }
            res.onSuccess {
                trendingRepos = it
                isLoading = false
                isRefreshing = false
            }.onFailure {
                errorMsg = it.message ?: "Failed to load trending repositories"
                isLoading = false
                isRefreshing = false
            }
        }
    }

    fun loadRepos(
        forceRefresh: Boolean = false,
        targetTab: RepoListTab = activeTab,
        targetSearchKind: GlobalSearchKind = searchKind
    ) {
        val trimmedQuery = searchQuery.trim()
        if (targetTab == RepoListTab.SEARCH) {
            submittedSearchQuery = trimmedQuery
            if (trimmedQuery.isEmpty()) {
                loadTrending(trendingLang, forceRefresh = forceRefresh)
                return
            }
        }

        val currentSeq = ++requestSeq
        scope.launch {
            when (targetTab) {
                RepoListTab.MINE -> {
                    val cached = container.repository.peekViewerRepositories()
                    if (cached != null) { repos = cached; isLoading = false } else { repos = emptyList(); isLoading = true }
                }
                RepoListTab.STARRED -> {
                    val cached = container.repository.peekStarredRepositories()
                    if (cached != null) { repos = cached; isLoading = false } else { repos = emptyList(); isLoading = true }
                }
                RepoListTab.SEARCH -> when (targetSearchKind) {
                    GlobalSearchKind.REPOSITORIES -> {
                        val cached = container.repository.peekSearchRepositories(trimmedQuery)
                        if (cached != null) { repos = cached; isLoading = false } else { repos = emptyList(); isLoading = true }
                    }
                    GlobalSearchKind.ISSUES_PRS -> {
                        val cached = container.repository.peekSearchIssuesAndPullsGlobal(trimmedQuery)
                        if (cached != null) { issueHits = cached; isLoading = false } else { issueHits = emptyList(); isLoading = true }
                    }
                    GlobalSearchKind.CODE -> {
                        val cached = container.repository.peekSearchCode(trimmedQuery)
                        if (cached != null) { codeHits = cached; isLoading = false } else { codeHits = emptyList(); isLoading = true }
                    }
                    GlobalSearchKind.USERS -> {
                        val cached = container.repository.peekSearchUsers(trimmedQuery)
                        if (cached != null) { userHits = cached; isLoading = false } else { userHits = emptyList(); isLoading = true }
                    }
                }
            }
            if (forceRefresh) isRefreshing = true
            errorMsg = null

            when (targetTab) {
                RepoListTab.MINE -> container.repository.getViewerRepositories(forceRefresh = forceRefresh)
                    .also { if (requestSeq != currentSeq || activeTab != targetTab) return@launch }
                    .onSuccess { repos = it; isLoading = false; isRefreshing = false }
                    .onFailure { errorMsg = it.message ?: "Failed to load repositories"; isLoading = false; isRefreshing = false }
                RepoListTab.STARRED -> container.repository.getStarredRepositories(forceRefresh = forceRefresh)
                    .also { if (requestSeq != currentSeq || activeTab != targetTab) return@launch }
                    .onSuccess { repos = it; isLoading = false; isRefreshing = false }
                    .onFailure { errorMsg = it.message ?: "Failed to load starred repositories"; isLoading = false; isRefreshing = false }
                RepoListTab.SEARCH -> when (targetSearchKind) {
                    GlobalSearchKind.REPOSITORIES -> container.repository.searchRepositories(trimmedQuery, forceRefresh = forceRefresh)
                        .also { if (requestSeq != currentSeq || activeTab != targetTab || searchKind != targetSearchKind) return@launch }
                        .onSuccess { repos = it; isLoading = false; isRefreshing = false }
                        .onFailure { errorMsg = it.message ?: "Repository search failed"; isLoading = false; isRefreshing = false }
                    GlobalSearchKind.ISSUES_PRS -> container.repository.searchIssuesAndPullsGlobal(trimmedQuery, forceRefresh = forceRefresh)
                        .also { if (requestSeq != currentSeq || activeTab != targetTab || searchKind != targetSearchKind) return@launch }
                        .onSuccess { issueHits = it; isLoading = false; isRefreshing = false }
                        .onFailure { errorMsg = it.message ?: "Issue search failed"; isLoading = false; isRefreshing = false }
                    GlobalSearchKind.CODE -> container.repository.searchCode(trimmedQuery, forceRefresh = forceRefresh)
                        .also { if (requestSeq != currentSeq || activeTab != targetTab || searchKind != targetSearchKind) return@launch }
                        .onSuccess { codeHits = it; isLoading = false; isRefreshing = false }
                        .onFailure { errorMsg = it.message ?: "Code search failed"; isLoading = false; isRefreshing = false }
                    GlobalSearchKind.USERS -> container.repository.searchUsers(trimmedQuery, forceRefresh = forceRefresh)
                        .also { if (requestSeq != currentSeq || activeTab != targetTab || searchKind != targetSearchKind) return@launch }
                        .onSuccess { userHits = it; isLoading = false; isRefreshing = false }
                        .onFailure { errorMsg = it.message ?: "User search failed"; isLoading = false; isRefreshing = false }
                }
            }
        }
    }

    LaunchedEffect(activeTab, searchKind, trendingLang) {
        if (activeTab != RepoListTab.SEARCH) {
            loadRepos(forceRefresh = false, targetTab = activeTab)
        } else if (searchQuery.isNotBlank()) {
            loadRepos(forceRefresh = false, targetTab = RepoListTab.SEARCH, targetSearchKind = searchKind)
        } else {
            submittedSearchQuery = ""
            loadTrending(trendingLang, forceRefresh = false)
        }
    }

    val hasCurrentItems = when {
        activeTab != RepoListTab.SEARCH -> repos.isNotEmpty()
        submittedSearchQuery.isBlank() -> trendingRepos.isNotEmpty()
        searchKind == GlobalSearchKind.REPOSITORIES -> repos.isNotEmpty()
        searchKind == GlobalSearchKind.ISSUES_PRS -> issueHits.isNotEmpty()
        searchKind == GlobalSearchKind.CODE -> codeHits.isNotEmpty()
        searchKind == GlobalSearchKind.USERS -> userHits.isNotEmpty()
        else -> false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (activeTab == RepoListTab.SEARCH && submittedSearchQuery.isBlank()) "Explore" else "Repositories",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { loadRepos(forceRefresh = true) }) {
                    Icon(Lucide.RefreshCw, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            OctoConnectedButtonGroup(
                options = RepoListTab.entries,
                selected = activeTab,
                onSelect = { activeTab = it },
                labelProvider = { if (it == RepoListTab.SEARCH) "Explore / Search" else it.label },
                modifier = Modifier.fillMaxWidth()
            )
            if (activeTab == RepoListTab.SEARCH) {
                Spacer(Modifier.height(10.dp))
                OctoConnectedButtonGroup(
                    options = GlobalSearchKind.entries,
                    selected = searchKind,
                    onSelect = { searchKind = it },
                    labelProvider = { it.label },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                when (searchKind) {
                                    GlobalSearchKind.REPOSITORIES -> "Search repositories..."
                                    GlobalSearchKind.ISSUES_PRS -> "Search issues & PRs..."
                                    GlobalSearchKind.CODE -> "Search code (e.g. remember repo:foo/bar)..."
                                    GlobalSearchKind.USERS -> "Search GitHub users & orgs..."
                                }
                            )
                        },
                        leadingIcon = { Icon(Lucide.Search, contentDescription = null) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        submittedSearchQuery = ""
                                        errorMsg = null
                                        loadTrending(trendingLang, forceRefresh = false)
                                    }
                                ) {
                                    Icon(Lucide.X, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                if (searchQuery.isNotBlank()) {
                                    loadRepos(forceRefresh = true, targetTab = RepoListTab.SEARCH)
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                    focusManager.clearFocus()
                                    if (searchQuery.isNotBlank()) {
                                        loadRepos(forceRefresh = true, targetTab = RepoListTab.SEARCH)
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                    )
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            loadRepos(forceRefresh = true, targetTab = RepoListTab.SEARCH)
                        },
                        enabled = searchQuery.isNotBlank()
                    ) {
                        Text("Search")
                    }
                }
                if (submittedSearchQuery.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(TRENDING_LANGUAGES, key = { it }) { lang ->
                            FilterChip(
                                selected = trendingLang == lang,
                                onClick = { trendingLang = lang },
                                leadingIcon = if (lang == "All") {
                                    { Icon(Lucide.Flame, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                label = { Text(if (lang == "All") "Trending All" else lang) }
                            )
                        }
                    }
                }
            }
            if ((isLoading || isRefreshing) && hasCurrentItems) {
                Spacer(Modifier.height(8.dp))
                OctoWavyProgress()
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing && hasCurrentItems,
            onRefresh = { loadRepos(forceRefresh = true) },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && !hasCurrentItems -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        OctoLoadingState(
                            message = if (activeTab == RepoListTab.SEARCH && submittedSearchQuery.isBlank()) {
                                "Loading trending repositories..."
                            } else {
                                "Loading results..."
                            }
                        )
                    }
                }

                errorMsg != null && !hasCurrentItems -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { loadRepos(forceRefresh = true) }) {
                            Text("Retry")
                        }
                    }
                }

                !hasCurrentItems -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = when (activeTab) {
                                RepoListTab.SEARCH -> if (submittedSearchQuery.isBlank()) {
                                    "No trending repositories found."
                                } else {
                                    "No ${searchKind.label} matched \"$submittedSearchQuery\"."
                                }
                                RepoListTab.STARRED -> "No starred repositories found."
                                RepoListTab.MINE -> "No repositories found."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                activeTab == RepoListTab.SEARCH && submittedSearchQuery.isBlank() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = trendingRepos,
                            key = { "trending_${it.id}" },
                            contentType = { "repo_summary" }
                        ) { repo ->
                            RepoSummaryCard(repo = repo, onClick = { onSelectRepo(repo) })
                        }
                    }
                }

                activeTab == RepoListTab.SEARCH && searchKind == GlobalSearchKind.ISSUES_PRS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = issueHits,
                            key = { "issue_${it.id}" },
                            contentType = { "issue_search_hit" }
                        ) { item ->
                            SearchIssuePullCard(
                                item = item,
                                onOpenUser = onSelectUser,
                                onClick = {
                                    onSelectIssuePull(
                                        IssuePullTarget(
                                            owner = item.repoOwner,
                                            repo = item.repoName,
                                            number = item.number,
                                            hintTitle = item.title
                                        )
                                    )
                                }
                            )
                        }
                    }
                }

                activeTab == RepoListTab.SEARCH && searchKind == GlobalSearchKind.CODE -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = codeHits,
                            key = { "${it.repoFullName}:${it.path}:${it.sha}" },
                            contentType = { "code_search_hit" }
                        ) { hit ->
                            CodeSearchHitCard(hit = hit, onClick = { onSelectCodeHit(hit) })
                        }
                    }
                }

                activeTab == RepoListTab.SEARCH && searchKind == GlobalSearchKind.USERS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = userHits,
                            key = { "user_${it.login}" },
                            contentType = { "user_search_hit" }
                        ) { user ->
                            SearchUserCard(user = user, onClick = { onSelectUser(user.login) })
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = repos,
                            key = { it.id },
                            contentType = { "repo_summary" }
                        ) { repo ->
                            RepoSummaryCard(repo = repo, onClick = { onSelectRepo(repo) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchIssuePullCard(
    item: IssueOrPullSummary,
    onOpenUser: (String) -> Unit,
    onClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (item.kind == IssueOrPullKind.PULL_REQUEST) Lucide.GitPullRequest else Lucide.CircleDot,
                    contentDescription = null,
                    tint = if (item.state == IssueOrPullState.OPEN) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${item.repoFullName} #${item.number}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                ExpressiveStatusPill(
                    label = when {
                        item.isDraft -> "Draft"
                        item.state == IssueOrPullState.OPEN -> "Open"
                        item.state == IssueOrPullState.MERGED -> "Merged"
                        else -> "Closed"
                    },
                    containerColor = if (item.state == IssueOrPullState.OPEN) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (item.state == IssueOrPullState.OPEN) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UserAvatar(
                    avatarUrl = item.author.avatarUrl,
                    login = item.author.login,
                    sizeDp = 20,
                    modifier = Modifier.clickable { onOpenUser(item.author.login) }
                )
                Text(
                    text = "@${item.author.login}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenUser(item.author.login) }
                )
            }
        }
    }
}

@Composable
private fun CodeSearchHitCard(
    hit: CodeSearchHit,
    onClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Lucide.FileCode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = hit.path,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = hit.repoFullName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            hit.fragments.firstOrNull()?.let { snippet ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = snippet.trim(),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchUserCard(
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
                Text("@${user.login}", style = MaterialTheme.typography.titleMedium)
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
            Icon(Lucide.Users, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RepoSummaryCard(
    repo: RepoSummary,
    onClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(repo.ownerAvatarUrl, repo.owner, sizeDp = 24)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = repo.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (repo.isPrivate) {
                    ExpressiveStatusPill(
                        label = "Private",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        icon = Lucide.Lock
                    )
                }
            }

            if (!repo.description.isNullOrBlank()) {
                Text(
                    text = repo.description!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                repo.language?.let { lang ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(repo.languageColorHex ?: "6E40C9"))
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(lang, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(repo.stargazersCount.toString(), style = MaterialTheme.typography.labelMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.GitFork, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(repo.forksCount.toString(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
fun RepoDetailScreen(
    repo: RepoSummary,
    container: AppContainer,
    initialFilePath: String? = null,
    onBack: () -> Unit,
    onOpenIssueOrPull: (IssuePullTarget) -> Unit,
    onOpenUser: (String) -> Unit = {},
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var detail by remember(repo.fullName) {
        mutableStateOf(container.repository.peekRepoDetail(repo.owner, repo.name))
    }
    var currentRef by remember(repo.fullName) { mutableStateOf(detail?.summary?.defaultBranch ?: repo.defaultBranch) }
    var isStarred by remember(repo.fullName) { mutableStateOf(detail?.summary?.viewerHasStarred ?: repo.viewerHasStarred) }
    var activeTab by remember(repo.fullName) { mutableStateOf(RepoDetailTab.CODE) }

    var currentPath by remember(repo.fullName) { mutableStateOf("") }
    var entries by remember(repo.fullName) {
        mutableStateOf(container.repository.peekDirectoryContents(repo.owner, repo.name, "", currentRef).orEmpty())
    }
    var openedFile by remember(repo.fullName) { mutableStateOf<FileContent?>(null) }
    var readme by remember(repo.fullName) {
        mutableStateOf(container.repository.peekReadme(repo.owner, repo.name, currentRef))
    }

    var issueStateFilter by remember(repo.fullName) { mutableStateOf(IssueStateFilter.OPEN) }
    var pullStateFilter by remember(repo.fullName) { mutableStateOf(IssueStateFilter.OPEN) }
    var repoIssues by remember(repo.fullName) {
        mutableStateOf(
            container.repository.peekRepoIssuesOrPulls(repo.owner, repo.name, pullRequests = false, stateFilter = IssueStateFilter.OPEN)
        )
    }
    var repoPulls by remember(repo.fullName) {
        mutableStateOf(
            container.repository.peekRepoIssuesOrPulls(repo.owner, repo.name, pullRequests = true, stateFilter = IssueStateFilter.OPEN)
        )
    }
    var issuesLoading by remember(repo.fullName) { mutableStateOf(false) }
    var pullsLoading by remember(repo.fullName) { mutableStateOf(false) }
    var issuesError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var pullsError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var isLoading by remember(repo.fullName) { mutableStateOf(detail == null && entries.isEmpty()) }

    var showBranchMenu by remember { mutableStateOf(false) }
    var showCreateIssueDialog by remember { mutableStateOf(false) }
    var newIssueTitle by remember { mutableStateOf("") }
    var newIssueBody by remember { mutableStateOf("") }

    fun loadDirectory(path: String, ref: String, forceRefresh: Boolean = false) {
        scope.launch {
            openedFile = null
            currentPath = path
            val cached = container.repository.peekDirectoryContents(repo.owner, repo.name, path, ref)
            if (cached != null) {
                entries = cached
                isLoading = false
            } else {
                isLoading = true
            }
            container.repository.getDirectoryContents(repo.owner, repo.name, path, ref, forceRefresh = forceRefresh)
                .onSuccess { entries = it }
                .onFailure { onMessage(it.message ?: "Failed to load directory") }
            isLoading = false
        }
    }

    fun loadFile(path: String, ref: String) {
        scope.launch {
            val cached = container.repository.peekFileContent(repo.owner, repo.name, path, ref)
            if (cached != null) {
                openedFile = cached
                isLoading = false
            } else {
                isLoading = true
            }
            container.repository.getFileContent(repo.owner, repo.name, path, ref, forceRefresh = false)
                .onSuccess { openedFile = it }
                .onFailure { onMessage(it.message ?: "Failed to load file") }
            isLoading = false
        }
    }

    fun loadRepoIssues(filter: IssueStateFilter, forceRefresh: Boolean = false) {
        scope.launch {
            val cached = container.repository.peekRepoIssuesOrPulls(repo.owner, repo.name, pullRequests = false, stateFilter = filter)
            if (cached != null) {
                repoIssues = cached
                issuesLoading = false
            } else {
                issuesLoading = true
            }
            issuesError = null
            container.repository.getRepoIssuesOrPulls(repo.owner, repo.name, pullRequests = false, stateFilter = filter, forceRefresh = forceRefresh)
                .onSuccess { loaded ->
                    repoIssues = loaded
                    issuesLoading = false
                    container.prefetchIssuePullTargets(
                        loaded.take(3).map { IssuePullTarget(repo.owner, repo.name, it.number, it.title) }
                    )
                }
                .onFailure {
                    issuesError = it.message ?: "Failed to load issues"
                    issuesLoading = false
                }
        }
    }

    fun loadRepoPulls(filter: IssueStateFilter, forceRefresh: Boolean = false) {
        scope.launch {
            val cached = container.repository.peekRepoIssuesOrPulls(repo.owner, repo.name, pullRequests = true, stateFilter = filter)
            if (cached != null) {
                repoPulls = cached
                pullsLoading = false
            } else {
                pullsLoading = true
            }
            pullsError = null
            container.repository.getRepoIssuesOrPulls(repo.owner, repo.name, pullRequests = true, stateFilter = filter, forceRefresh = forceRefresh)
                .onSuccess { loaded ->
                    repoPulls = loaded
                    pullsLoading = false
                    container.prefetchIssuePullTargets(
                        loaded.take(3).map { IssuePullTarget(repo.owner, repo.name, it.number, it.title) }
                    )
                }
                .onFailure {
                    pullsError = it.message ?: "Failed to load pull requests"
                    pullsLoading = false
                }
        }
    }

    // Android System Back navigates file -> parent dir -> repo list
    BackHandler(enabled = true) {
        when {
            openedFile != null -> openedFile = null
            activeTab == RepoDetailTab.CODE && currentPath.isNotBlank() -> {
                val parent = currentPath.substringBeforeLast('/', "")
                loadDirectory(parent, currentRef)
            }
            else -> onBack()
        }
    }

    LaunchedEffect(repo.fullName, initialFilePath) {
        isLoading = detail == null && entries.isEmpty()
        val initialParent = initialFilePath?.substringBeforeLast('/', "")?.takeIf { it.isNotBlank() } ?: ""
        currentPath = initialParent
        var resolvedRef = currentRef
        coroutineScope {
            val detailJob = async { container.repository.getRepoDetail(repo.owner, repo.name) }
            val readmeJob = async { container.repository.getReadme(repo.owner, repo.name, resolvedRef) }
            val entriesJob = async { container.repository.getDirectoryContents(repo.owner, repo.name, initialParent, resolvedRef) }

            detailJob.await().onSuccess {
                detail = it
                isStarred = it.summary.viewerHasStarred
                resolvedRef = it.summary.defaultBranch
                currentRef = resolvedRef
            }
            readmeJob.await().onSuccess { readme = it }
            entriesJob.await().onSuccess { entries = it }
        }
        if (!initialFilePath.isNullOrBlank()) {
            loadFile(initialFilePath, resolvedRef)
        } else {
            isLoading = false
        }

        // Prefetch Open Issues & Open PRs in background so switching tabs is instant
        scope.launch {
            container.repository.getRepoIssuesOrPulls(repo.owner, repo.name, pullRequests = false, stateFilter = IssueStateFilter.OPEN)
                .onSuccess { if (issueStateFilter == IssueStateFilter.OPEN && repoIssues == null) repoIssues = it }
        }
        scope.launch {
            container.repository.getRepoIssuesOrPulls(repo.owner, repo.name, pullRequests = true, stateFilter = IssueStateFilter.OPEN)
                .onSuccess { if (pullStateFilter == IssueStateFilter.OPEN && repoPulls == null) repoPulls = it }
        }
    }

    LaunchedEffect(activeTab, issueStateFilter, repo.fullName) {
        if (activeTab == RepoDetailTab.ISSUES) {
            loadRepoIssues(issueStateFilter, forceRefresh = false)
        }
    }

    LaunchedEffect(activeTab, pullStateFilter, repo.fullName) {
        if (activeTab == RepoDetailTab.PULLS) {
            loadRepoPulls(pullStateFilter, forceRefresh = false)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Lucide.ArrowLeft, contentDescription = "Back")
                    }
                    Text(
                        text = repo.fullName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = repo.owner.isNotBlank()) { onOpenUser(repo.owner) }
                    )
                    FilledTonalButton(
                        onClick = {
                            val next = !isStarred
                            isStarred = next
                            scope.launch {
                                container.repository.setStarred(repo.owner, repo.name, next)
                                    .onSuccess { onMessage(if (next) "Starred" else "Unstarred") }
                                    .onFailure { isStarred = !next }
                            }
                        }
                    ) {
                        Icon(Lucide.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isStarred) "Starred" else "Star")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = { showCreateIssueDialog = true }) {
                        Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Issue")
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box {
                        FilledTonalButton(onClick = { showBranchMenu = true }) {
                            Icon(Lucide.GitBranch, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(currentRef)
                            Spacer(Modifier.width(4.dp))
                            Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(15.dp))
                        }
                        DropdownMenu(expanded = showBranchMenu, onDismissRequest = { showBranchMenu = false }) {
                            val branches = detail?.branches.orEmpty()
                            if (branches.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(currentRef) },
                                    onClick = { showBranchMenu = false }
                                )
                            } else {
                                branches.forEach { b ->
                                    DropdownMenuItem(
                                        text = { Text(b.name) },
                                        onClick = {
                                            showBranchMenu = false
                                            currentRef = b.name
                                            loadDirectory("", b.name)
                                            scope.launch {
                                                container.repository.getReadme(repo.owner, repo.name, b.name)
                                                    .onSuccess { readme = it }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OctoConnectedButtonGroup(
                        options = RepoDetailTab.entries,
                        selected = activeTab,
                        onSelect = { activeTab = it },
                        labelProvider = { it.label },
                        iconProvider = {
                            when (it) {
                                RepoDetailTab.CODE -> Lucide.Folder
                                RepoDetailTab.ISSUES -> Lucide.CircleDot
                                RepoDetailTab.PULLS -> Lucide.GitPullRequest
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isLoading) {
                    Spacer(Modifier.height(8.dp))
                    OctoWavyProgress()
                }
            }
        }

        when (activeTab) {
            RepoDetailTab.CODE -> {
                if (openedFile != null) {
                    val file = openedFile!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = {
                                    val parent = currentPath.substringBeforeLast('/', "")
                                    openedFile = null
                                    if (parent == currentPath) loadDirectory("", currentRef)
                                    else loadDirectory(currentPath, currentRef)
                                }
                            ) {
                                Icon(Lucide.ArrowLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Back to directory")
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = file.path,
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        when {
                            file.isBinary -> Text("Binary file (${file.size} bytes)")
                            file.name.endsWith(".md", ignoreCase = true) -> {
                                Card(
                                    shape = MaterialTheme.shapes.large,
                                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                                ) {
                                    GitHubMarkdown(file.content, modifier = Modifier.padding(16.dp))
                                }
                            }
                            else -> {
                                CodeViewer(
                                    code = file.content,
                                    filename = file.name,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Breadcrumbs
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TextButton(onClick = { loadDirectory("", currentRef) }) {
                                Text(repo.name)
                            }
                            val segments = if (currentPath.isBlank()) emptyList() else currentPath.split('/')
                            segments.forEachIndexed { idx, segment ->
                                val targetPath = segments.take(idx + 1).joinToString("/")
                                Text("/", modifier = Modifier.align(Alignment.CenterVertically))
                                TextButton(onClick = { loadDirectory(targetPath, currentRef) }) {
                                    Text(segment)
                                }
                            }
                        }

                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                entries.forEach { entry ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (entry.type == TreeEntryType.TREE) {
                                                    loadDirectory(entry.path, currentRef)
                                                } else {
                                                    loadFile(entry.path, currentRef)
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (entry.type == TreeEntryType.TREE) Lucide.Folder else Lucide.File,
                                            contentDescription = null,
                                            tint = if (entry.type == TreeEntryType.TREE) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = entry.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (entry.type == TreeEntryType.BLOB && entry.size > 0) {
                                            Text(
                                                text = "${entry.size} B",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!readme.isNullOrBlank() && currentPath.isBlank()) {
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Text(
                                        text = "README.md",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    GitHubMarkdown(readme!!)
                                }
                            }
                        }
                    }
                }
            }

            RepoDetailTab.ISSUES -> {
                RepoIssuesOrPullsTabContent(
                    kindLabel = "Issues",
                    stateFilter = issueStateFilter,
                    onSelectFilter = { issueStateFilter = it },
                    items = repoIssues,
                    isLoading = issuesLoading,
                    errorMsg = issuesError,
                    onRefresh = { loadRepoIssues(issueStateFilter, forceRefresh = true) },
                    onOpenItem = { item ->
                        onOpenIssueOrPull(
                            IssuePullTarget(repo.owner, repo.name, item.number, item.title)
                        )
                    }
                )
            }

            RepoDetailTab.PULLS -> {
                RepoIssuesOrPullsTabContent(
                    kindLabel = "Pull Requests",
                    stateFilter = pullStateFilter,
                    onSelectFilter = { pullStateFilter = it },
                    items = repoPulls,
                    isLoading = pullsLoading,
                    errorMsg = pullsError,
                    onRefresh = { loadRepoPulls(pullStateFilter, forceRefresh = true) },
                    onOpenItem = { item ->
                        onOpenIssueOrPull(
                            IssuePullTarget(repo.owner, repo.name, item.number, item.title)
                        )
                    }
                )
            }
        }
    }

    if (showCreateIssueDialog) {
        AlertDialog(
            onDismissRequest = { showCreateIssueDialog = false },
            title = { Text("New Issue in ${repo.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newIssueTitle,
                        onValueChange = { newIssueTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newIssueBody,
                        onValueChange = { newIssueBody = it },
                        label = { Text("Description (Markdown)") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = newIssueTitle.isNotBlank(),
                    onClick = {
                        val title = newIssueTitle
                        val body = newIssueBody
                        showCreateIssueDialog = false
                        scope.launch {
                            container.repository.createIssue(repo.owner, repo.name, title, body)
                                .onSuccess { createdNumber ->
                                    newIssueTitle = ""
                                    newIssueBody = ""
                                    onMessage("Created Issue #$createdNumber")
                                    onOpenIssueOrPull(IssuePullTarget(repo.owner, repo.name, createdNumber, title))
                                }
                                .onFailure { onMessage(it.message ?: "Failed to create issue") }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateIssueDialog = false }) { Text("Cancel") }
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoIssuesOrPullsTabContent(
    kindLabel: String,
    stateFilter: IssueStateFilter,
    onSelectFilter: (IssueStateFilter) -> Unit,
    items: List<IssueOrPullSummary>?,
    isLoading: Boolean,
    errorMsg: String?,
    onRefresh: () -> Unit,
    onOpenItem: (IssueOrPullSummary) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IssueStateFilter.entries.forEach { sf ->
                FilterChip(
                    selected = stateFilter == sf,
                    onClick = { onSelectFilter(sf) },
                    label = { Text(sf.label) }
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(Lucide.RefreshCw, contentDescription = "Refresh $kindLabel", modifier = Modifier.size(18.dp))
            }
        }

        if (isLoading && !items.isNullOrEmpty()) {
            OctoWavyProgress(modifier = Modifier.padding(horizontal = 16.dp))
        }

        PullToRefreshBox(
            isRefreshing = isLoading && !items.isNullOrEmpty(),
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && items == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        OctoLoadingState(message = "Loading $kindLabel...")
                    }
                }

                errorMsg != null && items.isNullOrEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(errorMsg, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(onClick = onRefresh) {
                            Text("Retry")
                        }
                    }
                }

                items.isNullOrEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (kindLabel == "Issues") Lucide.CircleDot else Lucide.GitPullRequest,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No ${stateFilter.label} $kindLabel",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "There are no ${stateFilter.label.lowercase()} ${kindLabel.lowercase()} in this repository.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = items,
                            key = { it.id },
                            contentType = { "repo_issue_pull_summary" }
                        ) { item ->
                            IssuePullSummaryCard(item = item) {
                                onOpenItem(item)
                            }
                        }
                    }
                }
            }
        }
    }
}
