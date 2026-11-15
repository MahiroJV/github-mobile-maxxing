package dev.octoexpress.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.File
import com.composables.icons.lucide.FileCode
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderGit2
import com.composables.icons.lucide.GitBranch
import com.composables.icons.lucide.GitCommitHorizontal
import com.composables.icons.lucide.GitFork
import com.composables.icons.lucide.GitMerge
import com.composables.icons.lucide.GitPullRequest
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.X
import dev.octoexpress.app.AppContainer
import dev.octoexpress.app.IssuePullTarget
import dev.octoexpress.auth.openUrlInBrowser
import dev.octoexpress.model.BranchCompareResult
import dev.octoexpress.model.CodeSearchHit
import dev.octoexpress.model.CommitDetail
import dev.octoexpress.model.CommitSummary
import dev.octoexpress.model.DiscussionDetail
import dev.octoexpress.model.DiscussionListData
import dev.octoexpress.model.FileContent
import dev.octoexpress.model.GlobalSearchKind
import dev.octoexpress.model.IssueOrPullKind
import dev.octoexpress.model.IssueOrPullState
import dev.octoexpress.model.IssueOrPullSummary
import dev.octoexpress.model.IssueStateFilter
import dev.octoexpress.model.ReleaseSummary
import dev.octoexpress.model.RepoDetail
import dev.octoexpress.model.RepoSecurityOverview
import dev.octoexpress.model.RepoSummary
import dev.octoexpress.model.TagRef
import dev.octoexpress.model.TreeEntry
import dev.octoexpress.model.TreeEntryType
import dev.octoexpress.model.UserSummary
import dev.octoexpress.model.WorkflowJobSummary
import dev.octoexpress.model.WorkflowRunSummary
import dev.octoexpress.model.WorkflowSummary
import dev.octoexpress.ui.components.CodeViewer
import dev.octoexpress.ui.components.ExpressiveStatusPill
import dev.octoexpress.ui.components.GitHubMarkdown
import dev.octoexpress.ui.components.OctoConnectedButtonGroup
import dev.octoexpress.ui.components.OctoFilledIcons
import dev.octoexpress.ui.components.OctoLoadingState
import dev.octoexpress.ui.components.OctoStarAmber
import dev.octoexpress.ui.components.OctoWavyProgress
import dev.octoexpress.ui.components.StarActionButton
import dev.octoexpress.ui.components.UserAvatar
import dev.octoexpress.ui.components.diffFileLazyItems
import dev.octoexpress.ui.components.expressiveAnimateContentSize
import dev.octoexpress.ui.components.formatCompactCount
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
    COMMITS("Commits"),
    ACTIONS("Actions"),
    RELEASES("Releases"),
    DISCUSSIONS("Discussions"),
    SECURITY("Security"),
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
                onOpenRepo = { nextRepo ->
                    selectedInitialFilePath = null
                    selectedRepo = nextRepo
                },
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
                onSelectUser = onOpenUser,
                onMessage = onMessage
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
    onSelectUser: (String) -> Unit,
    onMessage: (String) -> Unit
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
    var showCreateRepoDialog by remember { mutableStateOf(false) }
    var newRepoName by remember { mutableStateOf("") }
    var newRepoDesc by remember { mutableStateOf("") }
    var newRepoPrivate by remember { mutableStateOf(false) }
    var newRepoAutoInit by remember { mutableStateOf(true) }
    var createRepoBusy by remember { mutableStateOf(false) }
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

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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
                FilledTonalButton(
                    onClick = { showCreateRepoDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
                IconButton(onClick = { loadRepos(forceRefresh = true) }) {
                    Icon(Lucide.RefreshCw, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            OctoConnectedButtonGroup(
                options = RepoListTab.entries,
                selected = activeTab,
                onSelect = { activeTab = it },
                labelProvider = { if (it == RepoListTab.SEARCH) "Explore" else it.label },
                iconProvider = {
                    when (it) {
                        RepoListTab.MINE -> Lucide.FolderGit2
                        RepoListTab.STARRED -> OctoFilledIcons.Star
                        RepoListTab.SEARCH -> Lucide.Compass
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (activeTab == RepoListTab.SEARCH) {
                Spacer(Modifier.height(10.dp))
                OctoConnectedButtonGroup(
                    options = GlobalSearchKind.entries,
                    selected = searchKind,
                    onSelect = { searchKind = it },
                    labelProvider = { it.label },
                    iconProvider = {
                        when (it) {
                            GlobalSearchKind.REPOSITORIES -> Lucide.Folder
                            GlobalSearchKind.ISSUES_PRS -> Lucide.CircleDot
                            GlobalSearchKind.CODE -> Lucide.FileCode
                            GlobalSearchKind.USERS -> Lucide.Users
                        }
                    },
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

    if (showCreateRepoDialog) {
        AlertDialog(
            onDismissRequest = { if (!createRepoBusy) showCreateRepoDialog = false },
            title = { Text("Create repository") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newRepoName,
                        onValueChange = { newRepoName = it },
                        label = { Text("Repository name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newRepoDesc,
                        onValueChange = { newRepoDesc = it },
                        label = { Text("Description (optional)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Lucide.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Private repository", modifier = Modifier.weight(1f))
                        Switch(checked = newRepoPrivate, onCheckedChange = { newRepoPrivate = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Lucide.File, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Initialize with README", modifier = Modifier.weight(1f))
                        Switch(checked = newRepoAutoInit, onCheckedChange = { newRepoAutoInit = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = newRepoName.isNotBlank() && !createRepoBusy,
                    onClick = {
                        val rName = newRepoName.trim()
                        val rDesc = newRepoDesc.trim()
                        createRepoBusy = true
                        scope.launch {
                            container.repository.createRepository(rName, rDesc, newRepoPrivate, newRepoAutoInit)
                                .onSuccess { created ->
                                    createRepoBusy = false
                                    showCreateRepoDialog = false
                                    newRepoName = ""
                                    newRepoDesc = ""
                                    onMessage("Created ${created.fullName}")
                                    onSelectRepo(created)
                                }
                                .onFailure {
                                    createRepoBusy = false
                                    onMessage(it.message ?: "Failed to create repository")
                                }
                        }
                    }
                ) {
                    Text(if (createRepoBusy) "Creating..." else "Create")
                }
            },
            dismissButton = {
                TextButton(enabled = !createRepoBusy, onClick = { showCreateRepoDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
                    icon = when {
                        item.isDraft -> Lucide.Pencil
                        item.state == IssueOrPullState.MERGED -> Lucide.GitMerge
                        item.state == IssueOrPullState.CLOSED -> Lucide.CircleCheck
                        item.kind == IssueOrPullKind.PULL_REQUEST -> Lucide.GitPullRequest
                        else -> Lucide.CircleDot
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
                    Icon(
                        imageVector = if (repo.viewerHasStarred) OctoFilledIcons.Star else Lucide.Star,
                        contentDescription = if (repo.viewerHasStarred) "Starred" else "Stars",
                        tint = if (repo.viewerHasStarred) OctoStarAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatCompactCount(repo.stargazersCount),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (repo.viewerHasStarred) FontWeight.Bold else FontWeight.Medium,
                        color = if (repo.viewerHasStarred) OctoStarAmber else MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.GitFork, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(formatCompactCount(repo.forksCount), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private enum class RepoPeopleTab(val label: String) {
    STARGAZERS("Stars"),
    FORKS("Forks"),
    CONTRIBUTORS("Contributors")
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
    onOpenRepo: (RepoSummary) -> Unit = {},
    onOpenUser: (String) -> Unit = {},
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val viewerProfile by container.viewerProfile.collectAsState()
    var detail by remember(repo.fullName) {
        mutableStateOf(container.repository.peekRepoDetail(repo.owner, repo.name))
    }
    var currentRef by remember(repo.fullName) { mutableStateOf(detail?.summary?.defaultBranch ?: repo.defaultBranch) }
    var isStarred by remember(repo.fullName) { mutableStateOf(detail?.summary?.viewerHasStarred ?: repo.viewerHasStarred) }
    var isWatching by remember(repo.fullName) {
        mutableStateOf(container.repository.peekRepoSubscription(repo.owner, repo.name) ?: false)
    }
    var forkBusy by remember(repo.fullName) { mutableStateOf(false) }
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

    var showPeopleDialog by remember { mutableStateOf(false) }
    var peopleTab by remember { mutableStateOf(RepoPeopleTab.STARGAZERS) }
    var stargazersList by remember(repo.fullName) { mutableStateOf<List<UserSummary>?>(null) }
    var forksList by remember(repo.fullName) { mutableStateOf<List<RepoSummary>?>(null) }
    var contributorsList by remember(repo.fullName) { mutableStateOf<List<UserSummary>?>(null) }
    var peopleLoading by remember { mutableStateOf(false) }

    var repoTags by remember(repo.fullName) { mutableStateOf<List<TagRef>>(emptyList()) }
    var repoCommits by remember(repo.fullName, currentRef) {
        mutableStateOf(container.repository.peekRepoCommits(repo.owner, repo.name, currentRef))
    }
    var commitsLoading by remember(repo.fullName, currentRef) { mutableStateOf(false) }
    var commitsError by remember(repo.fullName, currentRef) { mutableStateOf<String?>(null) }

    var openedCommitSha by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var openedCommitDetail by remember(repo.fullName) { mutableStateOf<CommitDetail?>(null) }
    var commitDetailLoading by remember(repo.fullName) { mutableStateOf(false) }

    var showCompareView by remember(repo.fullName) { mutableStateOf(false) }
    var compareBase by remember(repo.fullName) { mutableStateOf(repo.defaultBranch) }
    var compareHead by remember(repo.fullName) { mutableStateOf(currentRef) }
    var compareResult by remember(repo.fullName) { mutableStateOf<BranchCompareResult?>(null) }
    var compareLoading by remember(repo.fullName) { mutableStateOf(false) }
    var compareError by remember(repo.fullName) { mutableStateOf<String?>(null) }

    var workflows by remember(repo.fullName) { mutableStateOf<List<WorkflowSummary>>(emptyList()) }
    var selectedWorkflowId by remember(repo.fullName) { mutableStateOf<Long?>(null) }
    var workflowRuns by remember(repo.fullName) {
        mutableStateOf(container.repository.peekWorkflowRuns(repo.owner, repo.name))
    }
    var actionsLoading by remember(repo.fullName) { mutableStateOf(false) }
    var actionsError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var openedRun by remember(repo.fullName) { mutableStateOf<WorkflowRunSummary?>(null) }
    var runJobs by remember(repo.fullName) { mutableStateOf<List<WorkflowJobSummary>?>(null) }
    var jobsLoading by remember(repo.fullName) { mutableStateOf(false) }
    var openedLogJob by remember(repo.fullName) { mutableStateOf<WorkflowJobSummary?>(null) }
    var jobLogsText by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var jobLogsLoading by remember(repo.fullName) { mutableStateOf(false) }
    var actionsBusy by remember(repo.fullName) { mutableStateOf(false) }
    var showDispatchDialog by remember { mutableStateOf(false) }
    var releases by remember(repo.fullName) { mutableStateOf(container.repository.peekReleases(repo.owner, repo.name)) }
    var releasesLoading by remember(repo.fullName) { mutableStateOf(false) }
    var releasesError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var showCreateReleaseDialog by remember { mutableStateOf(false) }
    var creatingRelease by remember { mutableStateOf(false) }
    var createReleaseError by remember { mutableStateOf<String?>(null) }
    var selectedDiscussionCategoryId by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var discussionsData by remember(repo.fullName) {
        mutableStateOf(container.repository.peekDiscussions(repo.owner, repo.name))
    }
    var discussionsLoading by remember(repo.fullName) { mutableStateOf(false) }
    var discussionsError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var discussionsRequestSeq by remember(repo.fullName) { mutableStateOf(0) }
    var openedDiscussionNumber by remember(repo.fullName) { mutableStateOf<Int?>(null) }
    var discussionDetail by remember(repo.fullName) { mutableStateOf<DiscussionDetail?>(null) }
    var discussionDetailLoading by remember(repo.fullName) { mutableStateOf(false) }
    var discussionDetailError by remember(repo.fullName) { mutableStateOf<String?>(null) }
    var discussionCommentBusy by remember(repo.fullName) { mutableStateOf(false) }
    var showCreateDiscussionDialog by remember { mutableStateOf(false) }
    var creatingDiscussion by remember { mutableStateOf(false) }
    var createDiscussionError by remember { mutableStateOf<String?>(null) }
    var securityOverview by remember(repo.fullName) {
        mutableStateOf(container.repository.peekRepoSecurityOverview(repo.owner, repo.name))
    }
    var securityLoading by remember(repo.fullName) { mutableStateOf(false) }
    var securityError by remember(repo.fullName) { mutableStateOf<String?>(null) }

    fun loadDiscussions(categoryId: String? = selectedDiscussionCategoryId, forceRefresh: Boolean = false) {
        val reqSeq = ++discussionsRequestSeq
        scope.launch {
            val cached = container.repository.peekDiscussions(repo.owner, repo.name, categoryId)
            if (cached != null) {
                discussionsData = cached
                discussionsLoading = false
            } else {
                discussionsLoading = true
            }
            discussionsError = null
            container.repository.getDiscussions(repo.owner, repo.name, categoryId, forceRefresh = forceRefresh)
                .onSuccess {
                    if (reqSeq == discussionsRequestSeq) {
                        discussionsData = it
                        discussionsLoading = false
                    }
                }
                .onFailure {
                    if (reqSeq == discussionsRequestSeq) {
                        discussionsError = it.message ?: "Failed to load discussions"
                        discussionsLoading = false
                    }
                }
        }
    }

    fun openDiscussion(number: Int, forceRefresh: Boolean = false) {
        openedDiscussionNumber = number
        scope.launch {
            val cached = container.repository.peekDiscussionDetail(repo.owner, repo.name, number)
            if (cached != null) {
                discussionDetail = cached
                discussionDetailLoading = false
            } else {
                discussionDetailLoading = true
            }
            discussionDetailError = null
            container.repository.getDiscussionDetail(repo.owner, repo.name, number, forceRefresh = forceRefresh)
                .onSuccess {
                    if (openedDiscussionNumber == number) {
                        discussionDetail = it
                        discussionDetailLoading = false
                    }
                }
                .onFailure {
                    if (openedDiscussionNumber == number) {
                        discussionDetailError = it.message ?: "Failed to load discussion"
                        discussionDetailLoading = false
                    }
                }
        }
    }

    fun loadSecurityOverview(forceRefresh: Boolean = false) {
        scope.launch {
            val cached = container.repository.peekRepoSecurityOverview(repo.owner, repo.name)
            if (cached != null) {
                securityOverview = cached
                securityLoading = false
            } else {
                securityLoading = true
            }
            securityError = null
            container.repository.getRepoSecurityOverview(repo.owner, repo.name, forceRefresh = forceRefresh)
                .onSuccess {
                    securityOverview = it
                    securityLoading = false
                }
                .onFailure {
                    securityError = it.message ?: "Failed to load security overview"
                    securityLoading = false
                }
        }
    }

    fun loadReleases(forceRefresh: Boolean = false) {
        scope.launch {
            val cached = container.repository.peekReleases(repo.owner, repo.name)
            if (cached != null) {
                releases = cached
                releasesLoading = false
            } else {
                releasesLoading = true
            }
            releasesError = null
            container.repository.getReleases(repo.owner, repo.name, forceRefresh = forceRefresh)
                .onSuccess {
                    releases = it
                    releasesLoading = false
                }
                .onFailure {
                    releasesError = it.message ?: "Failed to load releases"
                    releasesLoading = false
                }
        }
    }

    fun loadActions(workflowId: Long? = selectedWorkflowId, forceRefresh: Boolean = false) {
        scope.launch {
            val cached = container.repository.peekWorkflowRuns(repo.owner, repo.name, workflowId)
            if (cached != null) {
                workflowRuns = cached
                actionsLoading = false
            } else {
                actionsLoading = true
            }
            actionsError = null
            container.repository.getWorkflows(repo.owner, repo.name, forceRefresh = forceRefresh)
                .onSuccess { workflows = it }
            container.repository.getWorkflowRuns(repo.owner, repo.name, workflowId, forceRefresh = forceRefresh)
                .onSuccess {
                    workflowRuns = it
                    actionsLoading = false
                }
                .onFailure {
                    actionsError = it.message ?: "Failed to load Actions runs"
                    actionsLoading = false
                }
        }
    }

    fun openRun(run: WorkflowRunSummary, forceRefresh: Boolean = false) {
        openedRun = run
        openedLogJob = null
        jobLogsText = null
        scope.launch {
            jobsLoading = true
            container.repository.getWorkflowRunJobs(repo.owner, repo.name, run.id, forceRefresh = forceRefresh)
                .onSuccess { runJobs = it }
                .onFailure { onMessage(it.message ?: "Failed to load jobs") }
            jobsLoading = false
        }
    }

    fun loadJobLog(job: WorkflowJobSummary) {
        openedLogJob = job
        jobLogsText = null
        scope.launch {
            jobLogsLoading = true
            container.repository.getJobLogs(repo.owner, repo.name, job.id)
                .onSuccess { jobLogsText = it }
                .onFailure { onMessage(it.message ?: "Logs unavailable (check repo scope / expiration)") }
            jobLogsLoading = false
        }
    }

    fun loadCommits(ref: String, forceRefresh: Boolean = false) {
        scope.launch {
            val cached = container.repository.peekRepoCommits(repo.owner, repo.name, ref)
            if (cached != null) {
                repoCommits = cached
                commitsLoading = false
            } else {
                commitsLoading = true
            }
            commitsError = null
            container.repository.getRepoCommits(repo.owner, repo.name, ref, forceRefresh = forceRefresh)
                .onSuccess {
                    repoCommits = it
                    commitsLoading = false
                }
                .onFailure {
                    commitsError = it.message ?: "Failed to load commits"
                    commitsLoading = false
                }
        }
    }

    fun openCommit(sha: String) {
        openedCommitSha = sha
        scope.launch {
            val cached = container.repository.peekCommitDetail(repo.owner, repo.name, sha)
            if (cached != null) {
                openedCommitDetail = cached
                commitDetailLoading = false
            } else {
                openedCommitDetail = null
                commitDetailLoading = true
            }
            container.repository.getCommitDetail(repo.owner, repo.name, sha, forceRefresh = false)
                .onSuccess { openedCommitDetail = it }
                .onFailure { onMessage(it.message ?: "Failed to load commit diff") }
            commitDetailLoading = false
        }
    }

    fun runCompare(base: String, head: String, forceRefresh: Boolean = false) {
        compareBase = base
        compareHead = head
        scope.launch {
            compareLoading = true
            compareError = null
            container.repository.compareRefs(repo.owner, repo.name, base, head, forceRefresh = forceRefresh)
                .onSuccess { compareResult = it }
                .onFailure { compareError = it.message ?: "Compare failed" }
            compareLoading = false
        }
    }

    fun openPeopleTab(tab: RepoPeopleTab) {
        peopleTab = tab
        showPeopleDialog = true
        scope.launch {
            peopleLoading = true
            when (tab) {
                RepoPeopleTab.STARGAZERS -> container.repository.getRepoStargazers(repo.owner, repo.name)
                    .onSuccess { stargazersList = it }
                    .onFailure { onMessage(it.message ?: "Failed to load stargazers") }
                RepoPeopleTab.FORKS -> container.repository.getRepoForks(repo.owner, repo.name)
                    .onSuccess { forksList = it }
                    .onFailure { onMessage(it.message ?: "Failed to load forks") }
                RepoPeopleTab.CONTRIBUTORS -> container.repository.getRepoContributors(repo.owner, repo.name)
                    .onSuccess { contributorsList = it }
                    .onFailure { onMessage(it.message ?: "Failed to load contributors") }
            }
            peopleLoading = false
        }
    }

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

    // Android System Back navigates commit -> compare -> file -> parent dir -> repo list
    BackHandler(enabled = true) {
        when {
            activeTab == RepoDetailTab.ACTIONS && openedLogJob != null -> {
                openedLogJob = null
                jobLogsText = null
            }
            activeTab == RepoDetailTab.ACTIONS && openedRun != null -> {
                openedRun = null
                runJobs = null
            }
            activeTab == RepoDetailTab.DISCUSSIONS && openedDiscussionNumber != null -> {
                openedDiscussionNumber = null
                discussionDetail = null
            }
            activeTab == RepoDetailTab.COMMITS && openedCommitSha != null -> {
                openedCommitSha = null
                openedCommitDetail = null
            }
            activeTab == RepoDetailTab.COMMITS && showCompareView -> showCompareView = false
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
                compareBase = resolvedRef
                compareHead = resolvedRef
            }
            readmeJob.await().onSuccess { readme = it }
            entriesJob.await().onSuccess { entries = it }
        }
        if (!initialFilePath.isNullOrBlank()) {
            loadFile(initialFilePath, resolvedRef)
        } else {
            isLoading = false
        }

        // Prefetch Open Issues, Open PRs, Tags, Commits & Watch state in background
        scope.launch {
            container.repository.getRepoSubscription(repo.owner, repo.name)
                .onSuccess { isWatching = it }
        }
        scope.launch {
            container.repository.getRepoTags(repo.owner, repo.name)
                .onSuccess { repoTags = it }
        }
        scope.launch {
            container.repository.getRepoCommits(repo.owner, repo.name, resolvedRef)
                .onSuccess { if (currentRef == resolvedRef && repoCommits == null) repoCommits = it }
        }
        scope.launch {
            container.repository.getRepoIssuesOrPulls(repo.owner, repo.name, pullRequests = false, stateFilter = IssueStateFilter.OPEN)
                .onSuccess { if (issueStateFilter == IssueStateFilter.OPEN && repoIssues == null) repoIssues = it }
        }
        scope.launch {
            container.repository.getReleases(repo.owner, repo.name)
                .onSuccess { if (releases == null) releases = it }
        }
        scope.launch {
            container.repository.getRepoIssuesOrPulls(repo.owner, repo.name, pullRequests = true, stateFilter = IssueStateFilter.OPEN)
                .onSuccess { if (pullStateFilter == IssueStateFilter.OPEN && repoPulls == null) repoPulls = it }
        }
    }

    LaunchedEffect(activeTab, currentRef, repo.fullName) {
        if (activeTab == RepoDetailTab.COMMITS && !showCompareView && openedCommitSha == null) {
            loadCommits(currentRef, forceRefresh = false)
        }
        if (activeTab == RepoDetailTab.ACTIONS && openedRun == null) {
            loadActions(selectedWorkflowId, forceRefresh = false)
        }
        if (activeTab == RepoDetailTab.RELEASES) {
            loadReleases(forceRefresh = false)
        }
        if (activeTab == RepoDetailTab.DISCUSSIONS && openedDiscussionNumber == null) {
            loadDiscussions(selectedDiscussionCategoryId, forceRefresh = false)
        }
        if (activeTab == RepoDetailTab.SECURITY) {
            loadSecurityOverview(forceRefresh = false)
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
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
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
                        onClick = { showCreateIssueDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Issue", maxLines = 1, softWrap = false)
                    }
                }

                Spacer(Modifier.height(8.dp))
                // 1) MD3E Connected Social Action Group (Star · Watch · Fork · Network)
                val isLightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
                val starCount = (detail?.summary?.stargazersCount ?: repo.stargazersCount).let { base ->
                    if (isStarred == (detail?.summary?.viewerHasStarred ?: repo.viewerHasStarred)) base
                    else if (isStarred) base + 1 else (base - 1).coerceAtLeast(0)
                }
                val forksNum = detail?.summary?.forksCount ?: repo.forksCount
                val starCheckedBg = if (isLightTheme) Color(0xFFFEF3C7) else Color(0xFF453208)
                val starCheckedFg = if (isLightTheme) Color(0xFF92400E) else Color(0xFFFCD34D)
                val starIconTint = if (isLightTheme) Color(0xFFD97706) else Color(0xFFFBBF24)
                val actionPad = PaddingValues(horizontal = 11.dp, vertical = 6.dp)

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                ) {
                    ToggleButton(
                        checked = isStarred,
                        onCheckedChange = {
                            val next = !isStarred
                            isStarred = next
                            scope.launch {
                                container.repository.setStarred(repo.owner, repo.name, next)
                                    .onSuccess { onMessage(if (next) "Starred ${repo.fullName}" else "Unstarred ${repo.fullName}") }
                                    .onFailure { isStarred = !next }
                            }
                        },
                        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                        contentPadding = actionPad,
                        colors = FilledTonalToggleButtonDefaults.colors(
                            checkedContainerColor = starCheckedBg,
                            checkedContentColor = starCheckedFg
                        )
                    ) {
                        Icon(
                            imageVector = if (isStarred) OctoFilledIcons.Star else Lucide.Star,
                            contentDescription = if (isStarred) "Starred" else "Star",
                            tint = if (isStarred) starIconTint else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isStarred) "Starred ${formatCompactCount(starCount)}" else "Star ${formatCompactCount(starCount)}",
                            fontWeight = if (isStarred) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    ToggleButton(
                        checked = isWatching,
                        onCheckedChange = {
                            val next = !isWatching
                            isWatching = next
                            scope.launch {
                                container.repository.setRepoSubscription(repo.owner, repo.name, next)
                                    .onSuccess { onMessage(if (next) "Watching ${repo.fullName}" else "Unwatched ${repo.fullName}") }
                                    .onFailure {
                                        isWatching = !next
                                        onMessage(it.message ?: "Watch update failed")
                                    }
                            }
                        },
                        shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                        contentPadding = actionPad,
                        colors = FilledTonalToggleButtonDefaults.colors(
                            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (isWatching) Lucide.Eye else Lucide.EyeOff,
                            contentDescription = if (isWatching) "Watching" else "Watch",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isWatching) "Watching" else "Watch",
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    ToggleButton(
                        checked = false,
                        enabled = !forkBusy,
                        onCheckedChange = {
                            forkBusy = true
                            scope.launch {
                                container.repository.forkRepository(repo.owner, repo.name)
                                    .onSuccess { forked ->
                                        forkBusy = false
                                        onMessage("Forked to ${forked.fullName}")
                                        onOpenRepo(forked)
                                    }
                                    .onFailure {
                                        forkBusy = false
                                        onMessage(it.message ?: "Fork failed")
                                    }
                            }
                        },
                        shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                        contentPadding = actionPad,
                        colors = FilledTonalToggleButtonDefaults.colors()
                    ) {
                        Icon(Lucide.GitFork, contentDescription = "Fork", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (forkBusy) "Forking..." else "Fork ${formatCompactCount(forksNum)}",
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    ToggleButton(
                        checked = false,
                        onCheckedChange = { openPeopleTab(RepoPeopleTab.STARGAZERS) },
                        shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        contentPadding = actionPad,
                        colors = FilledTonalToggleButtonDefaults.colors()
                    ) {
                        Icon(Lucide.Users, contentDescription = "Network", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Network", maxLines = 1, softWrap = false)
                    }
                }

                Spacer(Modifier.height(8.dp))
                // 2) Distinct Git Reference Field (10.dp rounded rectangle) + Compare button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showBranchMenu = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Lucide.GitBranch,
                                    contentDescription = "Branch or tag",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = currentRef,
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Lucide.ChevronDown,
                                    contentDescription = "Switch branch or tag",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        DropdownMenu(expanded = showBranchMenu, onDismissRequest = { showBranchMenu = false }) {
                            val branches = detail?.branches.orEmpty()
                            val selectRef: (String) -> Unit = { selected ->
                                showBranchMenu = false
                                currentRef = selected
                                openedCommitSha = null
                                loadDirectory("", selected)
                                if (activeTab == RepoDetailTab.COMMITS && !showCompareView) {
                                    loadCommits(selected, forceRefresh = false)
                                }
                                scope.launch {
                                    container.repository.getReadme(repo.owner, repo.name, selected)
                                        .onSuccess { readme = it }
                                }
                            }
                            if (branches.isEmpty() && repoTags.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(currentRef) },
                                    onClick = { showBranchMenu = false }
                                )
                            } else {
                                branches.forEach { b ->
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Lucide.GitBranch, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                        text = { Text(b.name) },
                                        onClick = { selectRef(b.name) }
                                    )
                                }
                                if (repoTags.isNotEmpty()) {
                                    HorizontalDivider()
                                    repoTags.take(20).forEach { tag ->
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Lucide.Tag, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                            text = { Text(tag.name) },
                                            onClick = { selectRef(tag.name) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    FilledTonalButton(
                        shape = RoundedCornerShape(10.dp),
                        onClick = {
                            openedCommitSha = null
                            showCompareView = true
                            activeTab = RepoDetailTab.COMMITS
                            val branches = detail?.branches.orEmpty()
                            val base = detail?.summary?.defaultBranch ?: repo.defaultBranch
                            val head = if (currentRef != base) currentRef else {
                                branches.firstOrNull { it.name != base }?.name ?: currentRef
                            }
                            compareBase = base
                            compareHead = head
                            runCompare(base, head)
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Icon(Lucide.ArrowLeftRight, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Compare", maxLines = 1, softWrap = false)
                    }
                }

                Spacer(Modifier.height(6.dp))
                // 3) Underlined Scrollable Section Tabs (distinct from action buttons above)
                PrimaryScrollableTabRow(
                    selectedTabIndex = RepoDetailTab.entries.indexOf(activeTab),
                    containerColor = Color.Transparent,
                    edgePadding = 0.dp
                ) {
                    RepoDetailTab.entries.forEach { tab ->
                        val icon = when (tab) {
                            RepoDetailTab.CODE -> Lucide.Folder
                            RepoDetailTab.COMMITS -> Lucide.GitCommitHorizontal
                            RepoDetailTab.ACTIONS -> Lucide.Play
                            RepoDetailTab.RELEASES -> Lucide.Tag
                            RepoDetailTab.DISCUSSIONS -> Lucide.MessageSquare
                            RepoDetailTab.SECURITY -> Lucide.ShieldCheck
                            RepoDetailTab.ISSUES -> Lucide.CircleDot
                            RepoDetailTab.PULLS -> Lucide.GitPullRequest
                        }
                        val selected = activeTab == tab
                        Tab(
                            selected = selected,
                            onClick = {
                                activeTab = tab
                                if (tab != RepoDetailTab.COMMITS) {
                                    showCompareView = false
                                    openedCommitSha = null
                                }
                                if (tab != RepoDetailTab.ACTIONS) {
                                    openedRun = null
                                    openedLogJob = null
                                }
                                if (tab != RepoDetailTab.DISCUSSIONS) {
                                    openedDiscussionNumber = null
                                }
                            },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Text(tab.label, maxLines = 1, softWrap = false)
                                }
                            }
                        )
                    }
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

            RepoDetailTab.COMMITS -> {
                val allRefs = remember(detail?.branches, repoTags, currentRef, repo.defaultBranch) {
                    buildList {
                        addAll(detail?.branches.orEmpty().map { it.name })
                        addAll(repoTags.map { it.name })
                        add(currentRef)
                        add(repo.defaultBranch)
                    }.distinct()
                }
                val avatarResolver: (CommitSummary) -> String = { commit ->
                    resolveCommitAuthorAvatarUrl(
                        commit = commit,
                        repoOwner = repo.owner,
                        repoOwnerAvatarUrl = detail?.summary?.ownerAvatarUrl?.ifBlank { repo.ownerAvatarUrl } ?: repo.ownerAvatarUrl,
                        viewerLogin = viewerProfile?.login,
                        viewerName = viewerProfile?.name,
                        viewerAvatarUrl = viewerProfile?.avatarUrl
                    )
                }
                when {
                    openedCommitSha != null -> {
                        RepoCommitDetailView(
                            sha = openedCommitSha!!,
                            detail = openedCommitDetail,
                            isLoading = commitDetailLoading,
                            commitAvatarResolver = avatarResolver,
                            onBack = {
                                openedCommitSha = null
                                openedCommitDetail = null
                            },
                            onOpenParent = { parentSha -> openCommit(parentSha) },
                            onOpenUser = onOpenUser
                        )
                    }
                    showCompareView -> {
                        RepoBranchCompareView(
                            availableRefs = allRefs,
                            baseRef = compareBase,
                            headRef = compareHead,
                            result = compareResult,
                            isLoading = compareLoading,
                            errorMsg = compareError,
                            commitAvatarResolver = avatarResolver,
                            onSelectBase = { base -> compareBase = base; runCompare(base, compareHead) },
                            onSelectHead = { head -> compareHead = head; runCompare(compareBase, head) },
                            onSwapRefs = {
                                val nextBase = compareHead
                                val nextHead = compareBase
                                compareBase = nextBase
                                compareHead = nextHead
                                runCompare(nextBase, nextHead)
                            },
                            onRefresh = { runCompare(compareBase, compareHead, forceRefresh = true) },
                            onBackToCommits = { showCompareView = false },
                            onOpenCommit = { sha -> openCommit(sha) },
                            onOpenUser = onOpenUser
                        )
                    }
                    else -> {
                        RepoCommitsTabContent(
                            currentRef = currentRef,
                            commits = repoCommits,
                            isLoading = commitsLoading,
                            errorMsg = commitsError,
                            commitAvatarResolver = avatarResolver,
                            onRefresh = { loadCommits(currentRef, forceRefresh = true) },
                            onOpenCompare = {
                                showCompareView = true
                                runCompare(compareBase, compareHead)
                            },
                            onOpenCommit = { commit -> openCommit(commit.sha) },
                            onOpenUser = onOpenUser
                        )
                    }
                }
            }

            RepoDetailTab.ACTIONS -> {
                when {
                    openedLogJob != null -> {
                        WorkflowLogViewer(
                            job = openedLogJob!!,
                            logs = jobLogsText,
                            isLoading = jobLogsLoading,
                            onBack = {
                                openedLogJob = null
                                jobLogsText = null
                            }
                        )
                    }
                    openedRun != null -> {
                        WorkflowRunDetailView(
                            run = openedRun!!,
                            jobs = runJobs,
                            isLoading = jobsLoading,
                            actionBusy = actionsBusy,
                            onBack = {
                                openedRun = null
                                runJobs = null
                            },
                            onRefresh = { openRun(openedRun!!, forceRefresh = true) },
                            onRerun = { failedOnly ->
                                actionsBusy = true
                                scope.launch {
                                    container.repository.rerunWorkflow(repo.owner, repo.name, openedRun!!.id, failedOnly)
                                        .onSuccess {
                                            onMessage(if (failedOnly) "Re-running failed jobs" else "Re-running workflow")
                                            loadActions(selectedWorkflowId, forceRefresh = true)
                                            openRun(openedRun!!, forceRefresh = true)
                                        }
                                        .onFailure { onMessage(it.message ?: "Re-run failed") }
                                    actionsBusy = false
                                }
                            },
                            onCancel = {
                                actionsBusy = true
                                scope.launch {
                                    container.repository.cancelWorkflowRun(repo.owner, repo.name, openedRun!!.id)
                                        .onSuccess {
                                            onMessage("Cancellation requested")
                                            loadActions(selectedWorkflowId, forceRefresh = true)
                                            openRun(openedRun!!, forceRefresh = true)
                                        }
                                        .onFailure { onMessage(it.message ?: "Cancel failed") }
                                    actionsBusy = false
                                }
                            },
                            onOpenLogs = { job -> loadJobLog(job) }
                        )
                    }
                    else -> {
                        RepoActionsTabContent(
                            workflows = workflows,
                            selectedWorkflowId = selectedWorkflowId,
                            runs = workflowRuns,
                            isLoading = actionsLoading,
                            errorMsg = actionsError,
                            onSelectWorkflow = { wfId ->
                                selectedWorkflowId = wfId
                                loadActions(wfId, forceRefresh = false)
                            },
                            onRefresh = { loadActions(selectedWorkflowId, forceRefresh = true) },
                            onOpenDispatch = { showDispatchDialog = true },
                            onOpenRun = { run -> openRun(run) }
                        )
                    }
                }
            }

            RepoDetailTab.RELEASES -> {
                RepoReleasesTabContent(
                    releases = releases,
                    tags = repoTags,
                    isLoading = releasesLoading,
                    errorMsg = releasesError,
                    onRefresh = { loadReleases(forceRefresh = true) },
                    onOpenCreateRelease = {
                        createReleaseError = null
                        showCreateReleaseDialog = true
                    },
                    onSelectTag = { tag ->
                        currentRef = tag.name
                        currentPath = ""
                        openedFile = null
                        activeTab = RepoDetailTab.CODE
                        loadDirectory("", tag.name)
                    }
                )
            }

            RepoDetailTab.DISCUSSIONS -> {
                val activeNum = openedDiscussionNumber
                if (activeNum != null) {
                    DiscussionDetailView(
                        detail = discussionDetail,
                        isLoading = discussionDetailLoading,
                        commentBusy = discussionCommentBusy,
                        errorMsg = discussionDetailError,
                        onBack = {
                            openedDiscussionNumber = null
                            discussionDetail = null
                        },
                        onRefresh = { openDiscussion(activeNum, forceRefresh = true) },
                        onToggleUpvote = { dSummary ->
                            scope.launch {
                                container.repository.toggleDiscussionUpvote(
                                    owner = repo.owner,
                                    repo = repo.name,
                                    subjectId = dSummary.id,
                                    currentlyUpvoted = dSummary.viewerHasUpvoted,
                                    discussionNumber = dSummary.number
                                ).onSuccess { (upvoted, count) ->
                                    discussionDetail = discussionDetail?.let { cur ->
                                        cur.copy(summary = cur.summary.copy(viewerHasUpvoted = upvoted, upvoteCount = count))
                                    }
                                    discussionsData = discussionsData?.let { cur ->
                                        cur.copy(
                                            discussions = cur.discussions.map {
                                                if (it.id == dSummary.id) it.copy(viewerHasUpvoted = upvoted, upvoteCount = count) else it
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        onSubmitComment = { commentBody, replyToId ->
                            val dId = discussionDetail?.summary?.id ?: return@DiscussionDetailView
                            scope.launch {
                                discussionCommentBusy = true
                                container.repository.addDiscussionComment(
                                    owner = repo.owner,
                                    repo = repo.name,
                                    discussionNumber = activeNum,
                                    discussionId = dId,
                                    body = commentBody,
                                    replyToId = replyToId
                                ).onSuccess {
                                    discussionCommentBusy = false
                                    openDiscussion(activeNum, forceRefresh = true)
                                }.onFailure { err ->
                                    discussionCommentBusy = false
                                    discussionDetailError = err.message ?: "Failed to post comment"
                                }
                            }
                        }
                    )
                } else {
                    RepoDiscussionsTabContent(
                        data = discussionsData,
                        selectedCategoryId = selectedDiscussionCategoryId,
                        onSelectCategory = { catId ->
                            selectedDiscussionCategoryId = catId
                            loadDiscussions(catId, forceRefresh = false)
                        },
                        isLoading = discussionsLoading,
                        errorMsg = discussionsError,
                        onRefresh = { loadDiscussions(selectedDiscussionCategoryId, forceRefresh = true) },
                        onOpenCreateDiscussion = {
                            createDiscussionError = null
                            showCreateDiscussionDialog = true
                        },
                        onOpenDiscussion = { discussion -> openDiscussion(discussion.number) },
                        onToggleUpvote = { dSummary ->
                            scope.launch {
                                container.repository.toggleDiscussionUpvote(
                                    owner = repo.owner,
                                    repo = repo.name,
                                    subjectId = dSummary.id,
                                    currentlyUpvoted = dSummary.viewerHasUpvoted,
                                    discussionNumber = dSummary.number
                                ).onSuccess { (upvoted, count) ->
                                    discussionsData = discussionsData?.let { cur ->
                                        cur.copy(
                                            discussions = cur.discussions.map {
                                                if (it.id == dSummary.id) it.copy(viewerHasUpvoted = upvoted, upvoteCount = count) else it
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            RepoDetailTab.SECURITY -> {
                RepoSecurityTabContent(
                    overview = securityOverview,
                    isLoading = securityLoading,
                    errorMsg = securityError,
                    onRefresh = { loadSecurityOverview(forceRefresh = true) }
                )
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

    if (showCreateReleaseDialog) {
        CreateReleaseDialog(
            defaultTargetBranch = currentRef.ifBlank { repo.defaultBranch },
            isSubmitting = creatingRelease,
            errorMsg = createReleaseError,
            onDismiss = { showCreateReleaseDialog = false },
            onCreate = { tagName, title, body, targetCommitish, isDraft, isPrerelease, generateNotes ->
                scope.launch {
                    creatingRelease = true
                    createReleaseError = null
                    container.repository.createRelease(
                        owner = repo.owner,
                        repo = repo.name,
                        tagName = tagName,
                        name = title,
                        body = body,
                        targetCommitish = targetCommitish.takeIf { it.isNotBlank() },
                        isDraft = isDraft,
                        isPrerelease = isPrerelease,
                        generateReleaseNotes = generateNotes
                    ).onSuccess {
                        creatingRelease = false
                        showCreateReleaseDialog = false
                        loadReleases(forceRefresh = true)
                        container.repository.getRepoTags(repo.owner, repo.name, forceRefresh = true)
                            .onSuccess { repoTags = it }
                    }.onFailure { err ->
                        creatingRelease = false
                        createReleaseError = err.message ?: "Failed to create release"
                    }
                }
            }
        )
    }

    if (showCreateDiscussionDialog) {
        val discData = discussionsData
        CreateDiscussionDialog(
            categories = discData?.categories.orEmpty(),
            isSubmitting = creatingDiscussion,
            errorMsg = createDiscussionError,
            onDismiss = { showCreateDiscussionDialog = false },
            onCreate = { categoryId, title, body ->
                val repoId = discData?.repositoryId
                if (repoId.isNullOrBlank()) {
                    createDiscussionError = "Repository metadata not ready yet"
                    return@CreateDiscussionDialog
                }
                scope.launch {
                    creatingDiscussion = true
                    createDiscussionError = null
                    container.repository.createDiscussion(
                        owner = repo.owner,
                        repo = repo.name,
                        repositoryId = repoId,
                        categoryId = categoryId,
                        title = title,
                        body = body
                    ).onSuccess { created ->
                        creatingDiscussion = false
                        showCreateDiscussionDialog = false
                        loadDiscussions(selectedDiscussionCategoryId, forceRefresh = true)
                        openDiscussion(created.number, forceRefresh = true)
                    }.onFailure { err ->
                        creatingDiscussion = false
                        createDiscussionError = err.message ?: "Failed to create discussion"
                    }
                }
            }
        )
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

    if (showPeopleDialog) {
        AlertDialog(
            onDismissRequest = { showPeopleDialog = false },
            title = { Text("${repo.name} Network") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OctoConnectedButtonGroup(
                        options = RepoPeopleTab.entries,
                        selected = peopleTab,
                        onSelect = { openPeopleTab(it) },
                        labelProvider = { it.label },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (peopleLoading) {
                        OctoWavyProgress()
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().height(280.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (peopleTab) {
                            RepoPeopleTab.STARGAZERS -> {
                                val list = stargazersList.orEmpty()
                                if (!peopleLoading && list.isEmpty()) {
                                    Text("No stargazers yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                list.forEach { user ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showPeopleDialog = false
                                                onOpenUser(user.login)
                                            }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        UserAvatar(user.avatarUrl, user.login, sizeDp = 28)
                                        Text("@${user.login}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            RepoPeopleTab.FORKS -> {
                                val list = forksList.orEmpty()
                                if (!peopleLoading && list.isEmpty()) {
                                    Text("No forks yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                list.forEach { fork ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showPeopleDialog = false
                                                onOpenRepo(fork)
                                            }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Lucide.GitFork, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text(fork.fullName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        Icon(OctoFilledIcons.Star, contentDescription = null, tint = OctoStarAmber, modifier = Modifier.size(13.dp))
                                        Text(formatCompactCount(fork.stargazersCount), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            RepoPeopleTab.CONTRIBUTORS -> {
                                val list = contributorsList.orEmpty()
                                if (!peopleLoading && list.isEmpty()) {
                                    Text("No contributors listed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                list.forEach { user ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showPeopleDialog = false
                                                onOpenUser(user.login)
                                            }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        UserAvatar(user.avatarUrl, user.login, sizeDp = 28)
                                        Text("@${user.login}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPeopleDialog = false }) { Text("Close") }
            }
        )
    }

    if (showDispatchDialog) {
        DispatchWorkflowDialog(
            workflows = workflows.filter { it.state == "active" }.ifEmpty { workflows },
            branches = detail?.branches.orEmpty().map { it.name }.ifEmpty { listOf(currentRef) },
            defaultBranch = currentRef,
            busy = actionsBusy,
            onDismiss = { showDispatchDialog = false },
            onDispatch = { workflowId, ref, inputs ->
                actionsBusy = true
                scope.launch {
                    container.repository.dispatchWorkflow(repo.owner, repo.name, workflowId, ref, inputs)
                        .onSuccess {
                            showDispatchDialog = false
                            onMessage("Dispatched workflow on $ref")
                            loadActions(selectedWorkflowId, forceRefresh = true)
                        }
                        .onFailure { onMessage(it.message ?: "Workflow dispatch failed") }
                    actionsBusy = false
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoCommitsTabContent(
    currentRef: String,
    commits: List<CommitSummary>?,
    isLoading: Boolean,
    errorMsg: String?,
    commitAvatarResolver: (CommitSummary) -> String = { it.authorAvatarUrl },
    onRefresh: () -> Unit,
    onOpenCompare: () -> Unit,
    onOpenCommit: (CommitSummary) -> Unit,
    onOpenUser: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpressiveStatusPill(
                label = currentRef,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                icon = Lucide.GitBranch
            )
            OutlinedButton(
                onClick = onOpenCompare,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Lucide.ArrowLeftRight, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text("Compare", maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(Lucide.RefreshCw, contentDescription = "Refresh Commits", modifier = Modifier.size(18.dp))
            }
        }

        if (isLoading && !commits.isNullOrEmpty()) {
            OctoWavyProgress(modifier = Modifier.padding(horizontal = 16.dp))
        }

        PullToRefreshBox(
            isRefreshing = isLoading && !commits.isNullOrEmpty(),
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && commits == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        OctoLoadingState(message = "Loading commits...")
                    }
                }
                errorMsg != null && commits.isNullOrEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(errorMsg, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(onClick = onRefresh) { Text("Retry") }
                    }
                }
                commits.isNullOrEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No commits found for $currentRef", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items = commits, key = { it.sha }, contentType = { "commit_summary" }) { commit ->
                            CommitSummaryCard(
                                commit = commit,
                                resolvedAvatarUrl = commitAvatarResolver(commit),
                                onClick = { onOpenCommit(commit) },
                                onOpenUser = onOpenUser
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun resolveCommitAuthorAvatarUrl(
    commit: CommitSummary,
    repoOwner: String,
    repoOwnerAvatarUrl: String,
    viewerLogin: String?,
    viewerName: String?,
    viewerAvatarUrl: String?
): String {
    if (commit.authorAvatarUrl.isNotBlank()) return commit.authorAvatarUrl
    val loginMatchesViewer = !viewerLogin.isNullOrBlank() && (
        commit.authorLogin.equals(viewerLogin, ignoreCase = true) ||
            commit.authorName.equals(viewerLogin, ignoreCase = true) ||
            (!viewerName.isNullOrBlank() && commit.authorName.equals(viewerName, ignoreCase = true)) ||
            (commit.authorLogin == null && repoOwner.equals(viewerLogin, ignoreCase = true))
        )
    if (loginMatchesViewer) {
        viewerAvatarUrl?.takeIf { it.isNotBlank() }?.let { return it }
        repoOwnerAvatarUrl.takeIf { it.isNotBlank() }?.let { return it }
        viewerLogin?.let { return "https://avatars.githubusercontent.com/$it?size=96" }
    }
    if (commit.authorLogin == null && commit.authorName.equals(repoOwner, ignoreCase = true)) {
        if (repoOwnerAvatarUrl.isNotBlank()) return repoOwnerAvatarUrl
        return "https://avatars.githubusercontent.com/$repoOwner?size=96"
    }
    commit.authorLogin?.takeIf { it.isNotBlank() }?.let { login ->
        return "https://avatars.githubusercontent.com/$login?size=96"
    }
    return ""
}

@Composable
private fun CommitSummaryCard(
    commit: CommitSummary,
    resolvedAvatarUrl: String = commit.authorAvatarUrl,
    onClick: () -> Unit,
    onOpenUser: (String) -> Unit
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UserAvatar(
                avatarUrl = resolvedAvatarUrl.ifBlank { commit.authorAvatarUrl },
                login = commit.authorLogin ?: commit.authorName,
                sizeDp = 34,
                modifier = Modifier.clickable(enabled = !commit.authorLogin.isNullOrBlank()) {
                    commit.authorLogin?.let(onOpenUser)
                }
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = commit.headline,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = commit.shortSha,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (commit.isVerified) {
                        Icon(
                            Lucide.ShieldCheck,
                            contentDescription = "Verified commit",
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = commit.authorLogin?.let { "@$it" } ?: commit.authorName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (commit.committedAt.length >= 10) {
                        Text(
                            text = commit.committedAt.take(10),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepoCommitDetailView(
    sha: String,
    detail: CommitDetail?,
    isLoading: Boolean,
    commitAvatarResolver: (CommitSummary) -> String = { it.authorAvatarUrl },
    onBack: () -> Unit,
    onOpenParent: (String) -> Unit,
    onOpenUser: (String) -> Unit
) {
    val expandedFiles = remember(sha) { mutableStateMapOf<String, Boolean>() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Commits")
            }
            Text(
                text = sha.take(7),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary
            )
            detail?.let { d ->
                Spacer(Modifier.weight(1f))
                Text(
                    text = "+${d.additions} / -${d.deletions} (${d.changedFilesCount} files)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            isLoading && detail == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OctoLoadingState(message = "Loading commit ${sha.take(7)}...")
                }
            }
            detail == null -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Commit details unavailable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "commit_header") {
                        ElevatedCard(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(detail.summary.headline, style = MaterialTheme.typography.titleMedium)
                                if (detail.summary.body.isNotBlank()) {
                                    Text(
                                        text = detail.summary.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    UserAvatar(
                                        commitAvatarResolver(detail.summary).ifBlank { detail.summary.authorAvatarUrl },
                                        detail.summary.authorLogin ?: detail.summary.authorName,
                                        sizeDp = 24,
                                        modifier = Modifier.clickable(enabled = !detail.summary.authorLogin.isNullOrBlank()) {
                                            detail.summary.authorLogin?.let(onOpenUser)
                                        }
                                    )
                                    Text(
                                        detail.summary.authorLogin?.let { "@$it" } ?: detail.summary.authorName,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    if (detail.summary.isVerified) {
                                        ExpressiveStatusPill(
                                            label = "Verified",
                                            containerColor = Color(0xFF16A34A).copy(alpha = 0.16f),
                                            contentColor = Color(0xFF16A34A),
                                            icon = Lucide.ShieldCheck
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    if (detail.summary.committedAt.length >= 10) {
                                        Text(
                                            detail.summary.committedAt.take(10),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (detail.summary.parentShas.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        detail.summary.parentShas.forEach { parent ->
                                            AssistChip(
                                                onClick = { onOpenParent(parent) },
                                                leadingIcon = { Icon(Lucide.GitCommitHorizontal, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                                label = { Text("Parent ${parent.take(7)}", fontFamily = FontFamily.Monospace) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    detail.files.forEach { file ->
                        val isExpanded = expandedFiles[file.filename] ?: (detail.files.size <= 3)
                        diffFileLazyItems(
                            file = file,
                            expanded = isExpanded,
                            onToggleExpand = { expandedFiles[file.filename] = !isExpanded }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoBranchCompareView(
    availableRefs: List<String>,
    baseRef: String,
    headRef: String,
    result: BranchCompareResult?,
    isLoading: Boolean,
    errorMsg: String?,
    onSelectBase: (String) -> Unit,
    onSelectHead: (String) -> Unit,
    onSwapRefs: () -> Unit,
    onRefresh: () -> Unit,
    onBackToCommits: () -> Unit,
    onOpenCommit: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    commitAvatarResolver: (CommitSummary) -> String = { it.authorAvatarUrl }
) {
    val expandedFiles = remember(baseRef, headRef) { mutableStateMapOf<String, Boolean>() }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = onBackToCommits) {
                    Icon(Lucide.ArrowLeft, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Commits")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh) {
                    Icon(Lucide.RefreshCw, contentDescription = "Refresh Compare", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RefSelectorDropdown(
                    label = "Base",
                    selectedRef = baseRef,
                    options = availableRefs,
                    onSelect = onSelectBase,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSwapRefs) {
                    Icon(Lucide.ArrowLeftRight, contentDescription = "Swap base and head", modifier = Modifier.size(16.dp))
                }
                RefSelectorDropdown(
                    label = "Head",
                    selectedRef = headRef,
                    options = availableRefs,
                    onSelect = onSelectHead,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OctoLoadingState(message = "Comparing $baseRef...$headRef...")
                }
            }
            errorMsg != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = onRefresh) { Text("Retry") }
                }
            }
            result == null -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Select two refs to compare.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "compare_summary") {
                        ElevatedCard(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ExpressiveStatusPill(
                                        label = result.status.uppercase(),
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        icon = Lucide.ArrowLeftRight
                                    )
                                    Text(
                                        text = "Ahead ${result.aheadBy} · Behind ${result.behindBy}",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                Text(
                                    text = "${result.totalCommits} commits · ${result.files.size} changed files",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (result.commits.isNotEmpty()) {
                        item(key = "compare_commits_title") {
                            Text("Commits (${result.commits.size})", style = MaterialTheme.typography.titleSmall)
                        }
                        items(items = result.commits, key = { "cmp_${it.sha}" }, contentType = { "commit_summary" }) { commit ->
                            CommitSummaryCard(
                                commit = commit,
                                resolvedAvatarUrl = commitAvatarResolver(commit),
                                onClick = { onOpenCommit(commit.sha) },
                                onOpenUser = onOpenUser
                            )
                        }
                    }

                    if (result.files.isNotEmpty()) {
                        item(key = "compare_files_title") {
                            Text("Changed Files (${result.files.size})", style = MaterialTheme.typography.titleSmall)
                        }
                        result.files.forEach { file ->
                            val isExpanded = expandedFiles[file.filename] ?: (result.files.size <= 3)
                            diffFileLazyItems(
                                file = file,
                                expanded = isExpanded,
                                onToggleExpand = { expandedFiles[file.filename] = !isExpanded }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RefSelectorDropdown(
    label: String,
    selectedRef: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("$label: $selectedRef", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { refName ->
                DropdownMenuItem(
                    text = { Text(refName) },
                    onClick = {
                        expanded = false
                        onSelect(refName)
                    }
                )
            }
        }
    }
}

@Composable
private fun actionStatusVisual(status: String, conclusion: String?): Pair<ImageVector, Color> {
    return when {
        status != "completed" -> Lucide.Clock to Color(0xFFD97706)
        conclusion == "success" -> Lucide.CircleCheck to Color(0xFF16A34A)
        conclusion == "failure" || conclusion == "timed_out" -> Lucide.X to MaterialTheme.colorScheme.error
        else -> Lucide.CircleDot to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoActionsTabContent(
    workflows: List<WorkflowSummary>,
    selectedWorkflowId: Long?,
    runs: List<WorkflowRunSummary>?,
    isLoading: Boolean,
    errorMsg: String?,
    onSelectWorkflow: (Long?) -> Unit,
    onRefresh: () -> Unit,
    onOpenDispatch: () -> Unit,
    onOpenRun: (WorkflowRunSummary) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(key = "wf_all") {
                    FilterChip(
                        selected = selectedWorkflowId == null,
                        onClick = { onSelectWorkflow(null) },
                        label = { Text("All runs") }
                    )
                }
                items(items = workflows, key = { it.id }) { wf ->
                    FilterChip(
                        selected = selectedWorkflowId == wf.id,
                        onClick = { onSelectWorkflow(wf.id) },
                        label = { Text(wf.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
            if (workflows.isNotEmpty()) {
                FilledTonalButton(
                    onClick = onOpenDispatch,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Lucide.Play, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Run", maxLines = 1, softWrap = false)
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Lucide.RefreshCw, contentDescription = "Refresh runs", modifier = Modifier.size(18.dp))
            }
        }

        if (isLoading && !runs.isNullOrEmpty()) {
            OctoWavyProgress(modifier = Modifier.padding(horizontal = 16.dp))
        }

        PullToRefreshBox(
            isRefreshing = isLoading && !runs.isNullOrEmpty(),
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && runs == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        OctoLoadingState(message = "Loading workflow runs...")
                    }
                }
                errorMsg != null && runs.isNullOrEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(errorMsg, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(onClick = onRefresh) { Text("Retry") }
                    }
                }
                runs.isNullOrEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Lucide.Play, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        Text("No workflow runs yet", style = MaterialTheme.typography.titleMedium)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items = runs, key = { it.id }, contentType = { "workflow_run" }) { run ->
                            WorkflowRunSummaryCard(run = run, onClick = { onOpenRun(run) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowRunSummaryCard(
    run: WorkflowRunSummary,
    onClick: () -> Unit
) {
    val (statusIcon, statusTint) = actionStatusVisual(run.status, run.conclusion)
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(statusIcon, contentDescription = run.conclusion ?: run.status, tint = statusTint, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = run.displayTitle.ifBlank { run.name },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${run.name} #${run.runNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (run.headBranch.isNotBlank()) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                            Text(
                                text = run.headBranch,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (run.event.isNotBlank()) {
                        Text(
                            text = run.event,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowRunDetailView(
    run: WorkflowRunSummary,
    jobs: List<WorkflowJobSummary>?,
    isLoading: Boolean,
    actionBusy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRerun: (failedOnly: Boolean) -> Unit,
    onCancel: () -> Unit,
    onOpenLogs: (WorkflowJobSummary) -> Unit
) {
    val (statusIcon, statusTint) = actionStatusVisual(run.status, run.conclusion)
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Runs")
            }
            Text("#${run.runNumber}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (run.htmlUrl.isNotBlank()) {
                IconButton(onClick = { openUrlInBrowser(run.htmlUrl) }) {
                    Icon(Lucide.ExternalLink, contentDescription = "Open in browser", modifier = Modifier.size(16.dp))
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Lucide.RefreshCw, contentDescription = "Refresh jobs", modifier = Modifier.size(18.dp))
            }
        }

        if (isLoading && jobs == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                OctoLoadingState(message = "Loading jobs...")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "run_summary_header") {
                    ElevatedCard(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(statusIcon, contentDescription = null, tint = statusTint, modifier = Modifier.size(20.dp))
                                Text(run.displayTitle.ifBlank { run.name }, style = MaterialTheme.typography.titleMedium)
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                if (run.status != "completed") {
                                    FilledTonalButton(enabled = !actionBusy, onClick = onCancel) {
                                        Icon(Lucide.X, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Cancel Run")
                                    }
                                } else {
                                    FilledTonalButton(enabled = !actionBusy, onClick = { onRerun(false) }) {
                                        Icon(Lucide.RefreshCw, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Re-run All")
                                    }
                                    if (run.conclusion == "failure") {
                                        OutlinedButton(enabled = !actionBusy, onClick = { onRerun(true) }) {
                                            Text("Re-run Failed")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                items(items = jobs.orEmpty(), key = { it.id }, contentType = { "workflow_job" }) { job ->
                    val (jIcon, jTint) = actionStatusVisual(job.status, job.conclusion)
                    ElevatedCard(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(jIcon, contentDescription = null, tint = jTint, modifier = Modifier.size(18.dp))
                                Text(job.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                FilledTonalButton(
                                    onClick = { onOpenLogs(job) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Lucide.FileCode, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Logs")
                                }
                            }
                            job.steps.forEach { step ->
                                val (sIcon, sTint) = actionStatusVisual(step.status, step.conclusion)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Icon(sIcon, contentDescription = null, tint = sTint, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = "${step.number}. ${step.name}",
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
            }
        }
    }
}

@Composable
private fun WorkflowLogViewer(
    job: WorkflowJobSummary,
    logs: String?,
    isLoading: Boolean,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Jobs")
            }
            Text(job.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                OctoLoadingState(message = "Loading logs...")
            }
            logs.isNullOrBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No logs returned for this job.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> CodeViewer(
                code = logs,
                filename = "${job.name}.log",
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DispatchWorkflowDialog(
    workflows: List<WorkflowSummary>,
    branches: List<String>,
    defaultBranch: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onDispatch: (workflowId: Long, ref: String, inputs: Map<String, String>) -> Unit
) {
    var selectedWf by remember(workflows) { mutableStateOf(workflows.firstOrNull()) }
    var ref by remember(defaultBranch) { mutableStateOf(defaultBranch) }
    var inputsRaw by remember { mutableStateOf("") }
    var showWfMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Run Workflow") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(
                        onClick = { showWfMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedWf?.name ?: "Select workflow", modifier = Modifier.weight(1f))
                        Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = showWfMenu, onDismissRequest = { showWfMenu = false }) {
                        workflows.forEach { wf ->
                            DropdownMenuItem(
                                text = { Text(wf.name) },
                                onClick = {
                                    selectedWf = wf
                                    showWfMenu = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = ref,
                    onValueChange = { ref = it },
                    label = { Text("Branch or tag ref (e.g. ${branches.firstOrNull() ?: "main"})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = inputsRaw,
                    onValueChange = { inputsRaw = it },
                    label = { Text("Inputs (optional, key=value per line)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && selectedWf != null && ref.isNotBlank(),
                onClick = {
                    val inputsMap = inputsRaw.lineSequence()
                        .map { it.trim() }
                        .filter { '=' in it }
                        .associate {
                            val idx = it.indexOf('=')
                            it.substring(0, idx).trim() to it.substring(idx + 1).trim()
                        }
                    selectedWf?.let { wf -> onDispatch(wf.id, ref.trim(), inputsMap) }
                }
            ) {
                Text(if (busy) "Starting..." else "Run Workflow")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
