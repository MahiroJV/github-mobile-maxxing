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
import com.composables.icons.lucide.X
import dev.octoexpress.app.AppContainer
import dev.octoexpress.app.IssuePullTarget
import dev.octoexpress.model.FileContent
import dev.octoexpress.model.IssueOrPullSummary
import dev.octoexpress.model.IssueStateFilter
import dev.octoexpress.model.RepoDetail
import dev.octoexpress.model.RepoSummary
import dev.octoexpress.model.TreeEntry
import dev.octoexpress.model.TreeEntryType
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ReposScreen(
    container: AppContainer,
    onMessage: (String) -> Unit
) {
    var selectedRepo by remember { mutableStateOf<RepoSummary?>(null) }
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
                onBack = { selectedRepo = null },
                onOpenIssueOrPull = { selectedIssueTarget = it },
                onMessage = onMessage
            )
        }

        else -> {
            RepoListPane(
                container = container,
                onSelectRepo = { selectedRepo = it }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoListPane(
    container: AppContainer,
    onSelectRepo: (RepoSummary) -> Unit
) {
    var activeTab by remember { mutableStateOf(RepoListTab.MINE) }
    var searchQuery by remember { mutableStateOf("") }
    var submittedSearchQuery by remember { mutableStateOf("") }
    var repos by remember {
        mutableStateOf(container.repository.peekViewerRepositories().orEmpty())
    }
    var isLoading by remember { mutableStateOf(repos.isEmpty()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var requestSeq by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    fun loadRepos(forceRefresh: Boolean = false, targetTab: RepoListTab = activeTab) {
        val currentSeq = ++requestSeq
        val trimmedQuery = searchQuery.trim()
        if (targetTab == RepoListTab.SEARCH) {
            submittedSearchQuery = trimmedQuery
            if (trimmedQuery.isEmpty()) {
                repos = emptyList()
                isLoading = false
                isRefreshing = false
                errorMsg = null
                return
            }
        }
        scope.launch {
            val cached = when (targetTab) {
                RepoListTab.MINE -> container.repository.peekViewerRepositories()
                RepoListTab.STARRED -> container.repository.peekStarredRepositories()
                RepoListTab.SEARCH -> container.repository.peekSearchRepositories(trimmedQuery)
            }
            if (cached != null) {
                repos = cached
                isLoading = false
            } else {
                repos = emptyList()
                isLoading = true
            }
            if (forceRefresh) isRefreshing = true
            errorMsg = null
            val res = when (targetTab) {
                RepoListTab.MINE -> container.repository.getViewerRepositories(forceRefresh = forceRefresh)
                RepoListTab.STARRED -> container.repository.getStarredRepositories(forceRefresh = forceRefresh)
                RepoListTab.SEARCH -> container.repository.searchRepositories(trimmedQuery, forceRefresh = forceRefresh)
            }
            if (requestSeq != currentSeq || activeTab != targetTab) return@launch
            res.onSuccess {
                repos = it
                isLoading = false
                isRefreshing = false
            }.onFailure {
                errorMsg = it.message ?: "Failed to load repositories"
                isLoading = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(activeTab) {
        if (activeTab != RepoListTab.SEARCH) {
            loadRepos(forceRefresh = false, targetTab = activeTab)
        } else if (searchQuery.isNotBlank()) {
            loadRepos(forceRefresh = false, targetTab = RepoListTab.SEARCH)
        } else {
            requestSeq++
            repos = emptyList()
            errorMsg = null
            isLoading = false
            isRefreshing = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Repositories",
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
                labelProvider = { it.label },
                modifier = Modifier.fillMaxWidth()
            )
            if (activeTab == RepoListTab.SEARCH) {
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search GitHub repositories...") },
                        leadingIcon = { Icon(Lucide.Search, contentDescription = null) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        submittedSearchQuery = ""
                                        repos = emptyList()
                                        errorMsg = null
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
            }
            if ((isLoading || isRefreshing) && repos.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                OctoWavyProgress()
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing && repos.isNotEmpty(),
            onRefresh = { loadRepos(forceRefresh = true) },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && repos.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        OctoLoadingState(message = "Loading repositories...")
                    }
                }

                errorMsg != null && repos.isEmpty() -> {
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

                repos.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = when (activeTab) {
                                RepoListTab.SEARCH -> if (submittedSearchQuery.isBlank()) {
                                    "Enter a query to search repositories."
                                } else {
                                    "No repositories matched \"$submittedSearchQuery\"."
                                }
                                RepoListTab.STARRED -> "No starred repositories found."
                                RepoListTab.MINE -> "No repositories found."
                            },
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
private fun RepoSummaryCard(
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
    onBack: () -> Unit,
    onOpenIssueOrPull: (IssuePullTarget) -> Unit,
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

    LaunchedEffect(repo.fullName) {
        isLoading = detail == null && entries.isEmpty()
        coroutineScope {
            val detailJob = async { container.repository.getRepoDetail(repo.owner, repo.name) }
            val readmeJob = async { container.repository.getReadme(repo.owner, repo.name, currentRef) }
            val entriesJob = async { container.repository.getDirectoryContents(repo.owner, repo.name, "", currentRef) }

            detailJob.await().onSuccess {
                detail = it
                isStarred = it.summary.viewerHasStarred
                currentRef = it.summary.defaultBranch
            }
            readmeJob.await().onSuccess { readme = it }
            entriesJob.await().onSuccess { entries = it }
        }
        isLoading = false

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
                        modifier = Modifier.weight(1f)
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
