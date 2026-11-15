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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.FileDiff
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.GitCommitHorizontal
import com.composables.icons.lucide.GitMerge
import com.composables.icons.lucide.GitPullRequest
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.MessageSquarePlus
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.X
import dev.octoexpress.app.AppContainer
import dev.octoexpress.app.IssuePullTarget
import dev.octoexpress.auth.openUrlInBrowser
import dev.octoexpress.model.IssueOrPullDetail
import dev.octoexpress.model.IssueOrPullKind
import dev.octoexpress.model.IssueOrPullState
import dev.octoexpress.model.LabelTag
import dev.octoexpress.model.MergeMethod
import dev.octoexpress.model.MilestoneSummary
import dev.octoexpress.model.PullFileChange
import dev.octoexpress.model.ReviewEvent
import dev.octoexpress.model.TimelineEvent
import dev.octoexpress.model.UserSummary
import dev.octoexpress.ui.components.ExpressiveStatusPill
import dev.octoexpress.ui.components.GitHubLabelChip
import dev.octoexpress.ui.components.GitHubMarkdown
import dev.octoexpress.ui.components.OctoConnectedButtonGroup
import dev.octoexpress.ui.components.OctoFloatingToolbar
import dev.octoexpress.ui.components.OctoLoadingState
import dev.octoexpress.ui.components.OctoSplitActionButton
import dev.octoexpress.ui.components.OctoWavyProgress
import dev.octoexpress.ui.components.SplitActionOption
import dev.octoexpress.ui.components.UserAvatar
import dev.octoexpress.ui.components.diffFileLazyItems
import kotlinx.coroutines.launch

private enum class DetailTab(val label: String) {
    CONVERSATION("Conversation"),
    FILES("Files & Diff")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IssuePullDetailPane(
    target: IssuePullTarget,
    container: AppContainer,
    showBackButton: Boolean = false,
    onBack: () -> Unit = {},
    onMessage: (String) -> Unit = {}
) {
    var detail by remember(target) {
        mutableStateOf(container.repository.peekIssueOrPullDetail(target.owner, target.repo, target.number))
    }
    var files by remember(target) {
        mutableStateOf(container.repository.peekPullFiles(target.owner, target.repo, target.number).orEmpty())
    }
    var isLoading by remember(target) { mutableStateOf(detail == null) }
    var filesLoading by remember(target) { mutableStateOf(false) }
    var errorMsg by remember(target) { mutableStateOf<String?>(null) }
    var selectedTab by remember(target) { mutableStateOf(DetailTab.CONVERSATION) }

    var showCommentDialog by remember { mutableStateOf(false) }
    var commentDraft by remember { mutableStateOf("") }
    var showReviewDialog by remember { mutableStateOf(false) }
    var reviewBody by remember { mutableStateOf("") }
    var reviewEvent by remember { mutableStateOf(ReviewEvent.APPROVE) }
    var selectedMergeMethod by remember { mutableStateOf(MergeMethod.SQUASH) }
    var actionBusy by remember { mutableStateOf(false) }

    var showLabelsDialog by remember { mutableStateOf(false) }
    var availableLabels by remember(target) { mutableStateOf<List<LabelTag>>(emptyList()) }
    var selectedLabelNames by remember(target) { mutableStateOf<Set<String>>(emptySet()) }
    var newLabelName by remember { mutableStateOf("") }
    var newLabelColor by remember { mutableStateOf("6E40C9") }

    var showAssigneesDialog by remember { mutableStateOf(false) }
    var availableAssignees by remember(target) { mutableStateOf<List<UserSummary>>(emptyList()) }
    var selectedAssigneeLogins by remember(target) { mutableStateOf<Set<String>>(emptySet()) }

    var showMilestoneDialog by remember { mutableStateOf(false) }
    var availableMilestones by remember(target) { mutableStateOf<List<MilestoneSummary>>(emptyList()) }
    var selectedMilestoneNumber by remember(target) { mutableStateOf<Int?>(null) }

    val scope = rememberCoroutineScope()

    fun loadDetail(forceRefresh: Boolean = false) {
        scope.launch {
            val cached = container.repository.peekIssueOrPullDetail(target.owner, target.repo, target.number)
            if (cached != null) {
                detail = cached
            } else {
                isLoading = true
            }
            errorMsg = null
            container.repository.getIssueOrPullDetail(target.owner, target.repo, target.number, forceRefresh = forceRefresh)
                .onSuccess { loaded ->
                    detail = loaded
                    isLoading = false
                    if (loaded.kind == IssueOrPullKind.PULL_REQUEST && (files.isEmpty() || forceRefresh)) {
                        filesLoading = files.isEmpty()
                        container.repository.getPullFiles(target.owner, target.repo, target.number, forceRefresh = forceRefresh)
                            .onSuccess { files = it }
                        filesLoading = false
                    }
                }
                .onFailure {
                    errorMsg = it.message ?: "Failed to load details"
                    isLoading = false
                }
        }
    }

    LaunchedEffect(target) {
        loadDetail(forceRefresh = false)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && detail == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OctoLoadingState(message = "Loading #${target.number}...")
                }
            }

            errorMsg != null && detail == null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = errorMsg ?: "Unknown error",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (showBackButton) {
                            OutlinedButton(onClick = onBack) { Text("Back") }
                        }
                        Button(onClick = { loadDetail(forceRefresh = true) }) { Text("Retry") }
                    }
                }
            }

            detail != null -> {
                val item = detail!!
                val isPr = item.kind == IssueOrPullKind.PULL_REQUEST

                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Bar
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (showBackButton) {
                                    IconButton(onClick = onBack) {
                                        Icon(Lucide.ArrowLeft, contentDescription = "Back")
                                    }
                                }
                                Text(
                                    text = "${item.repoFullName} #${item.number}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { loadDetail(forceRefresh = true) }) {
                                    Icon(Lucide.RefreshCw, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                                }
                                if (item.htmlUrl.isNotBlank()) {
                                    IconButton(onClick = { openUrlInBrowser(item.htmlUrl) }) {
                                        Icon(Lucide.ExternalLink, contentDescription = "Open in browser", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            if (actionBusy || isLoading) {
                                OctoWavyProgress(modifier = Modifier.padding(vertical = 4.dp))
                            }

                            if (isPr) {
                                Spacer(Modifier.height(6.dp))
                                OctoConnectedButtonGroup(
                                    options = DetailTab.entries,
                                    selected = selectedTab,
                                    onSelect = { selectedTab = it },
                                    labelProvider = { tab ->
                                        if (tab == DetailTab.FILES) "${tab.label} (${item.changedFilesCount})" else tab.label
                                    },
                                    iconProvider = { tab ->
                                        if (tab == DetailTab.CONVERSATION) Lucide.MessageSquare else Lucide.FileDiff
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (selectedTab == DetailTab.CONVERSATION || !isPr) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                DetailHeaderCard(
                                    item = item,
                                    onEditLabels = {
                                        selectedLabelNames = item.labels.map { it.name }.toSet()
                                        showLabelsDialog = true
                                        scope.launch {
                                            container.repository.getRepoLabels(target.owner, target.repo)
                                                .onSuccess { availableLabels = it }
                                                .onFailure { onMessage(it.message ?: "Failed to load labels") }
                                        }
                                    },
                                    onEditAssignees = {
                                        selectedAssigneeLogins = item.assignees.map { it.login }.toSet()
                                        showAssigneesDialog = true
                                        scope.launch {
                                            container.repository.getRepoAssignees(target.owner, target.repo)
                                                .onSuccess { availableAssignees = it }
                                                .onFailure { onMessage(it.message ?: "Failed to load assignees") }
                                        }
                                    },
                                    onEditMilestone = {
                                        selectedMilestoneNumber = item.milestone?.number
                                        showMilestoneDialog = true
                                        scope.launch {
                                            container.repository.getRepoMilestones(target.owner, target.repo)
                                                .onSuccess { availableMilestones = it }
                                                .onFailure { onMessage(it.message ?: "Failed to load milestones") }
                                        }
                                    }
                                )
                            }

                            if (item.body.isNotBlank()) {
                                item {
                                    ElevatedCard(
                                        shape = MaterialTheme.shapes.large,
                                        colors = CardDefaults.elevatedCardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                UserAvatar(item.author.avatarUrl, item.author.login, sizeDp = 24)
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = "@${item.author.login}",
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = item.createdAt.take(10),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(Modifier.height(12.dp))
                                            GitHubMarkdown(item.body)
                                        }
                                    }
                                }
                            }

                            items(
                                items = item.timeline,
                                key = { it.id },
                                contentType = { it::class }
                            ) { event ->
                                TimelineItemCard(event)
                            }
                        }
                    } else {
                        val expandedFiles = remember(target, files) {
                            mutableStateMapOf<String, Boolean>().apply {
                                val defaultOpen = files.size <= 6
                                files.forEach { put(it.filename, defaultOpen) }
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (filesLoading) {
                                item(key = "files_loading", contentType = "loading") {
                                    OctoWavyProgress()
                                }
                            }
                            if (!filesLoading && files.isEmpty()) {
                                item(key = "files_empty", contentType = "empty") {
                                    Text(
                                        text = "No file diffs returned for this pull request.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            files.forEach { file ->
                                val isExpanded = expandedFiles[file.filename] ?: true
                                diffFileLazyItems(
                                    file = file,
                                    expanded = isExpanded,
                                    onToggleExpand = { expandedFiles[file.filename] = !isExpanded }
                                )
                            }
                        }
                    }
                }

                var pendingMergeConfirmMethod by remember(target) { mutableStateOf<MergeMethod?>(null) }
                var pendingStateConfirmOpen by remember(target) { mutableStateOf<Boolean?>(null) }

                // MD3E Floating Action Toolbar at bottom
                OctoFloatingToolbar(
                    expanded = true,
                    vibrant = true,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { showCommentDialog = true },
                        enabled = !actionBusy
                    ) {
                        Icon(Lucide.MessageSquarePlus, contentDescription = "Comment")
                    }

                    if (isPr && item.state == IssueOrPullState.OPEN) {
                        Spacer(Modifier.width(6.dp))
                        FilledTonalButton(
                            onClick = { showReviewDialog = true },
                            enabled = !actionBusy
                        ) {
                            Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Review")
                        }

                        if (item.viewerCanMerge) {
                            Spacer(Modifier.width(6.dp))
                            val mergeOptions = remember {
                                MergeMethod.entries.map {
                                    SplitActionOption(it, it.displayLabel, Lucide.GitMerge)
                                }
                            }
                            OctoSplitActionButton(
                                selectedOption = mergeOptions.first { it.value == selectedMergeMethod },
                                options = mergeOptions,
                                enabled = !actionBusy && !item.isDraft,
                                onSelectOption = { selectedMergeMethod = it },
                                onPrimaryClick = { method ->
                                    pendingMergeConfirmMethod = method
                                }
                            )
                        }
                    }

                    if (item.state != IssueOrPullState.MERGED && item.viewerCanCloseOrReopen) {
                        val willOpen = item.state == IssueOrPullState.CLOSED
                        val mergeVisible = isPr && item.state == IssueOrPullState.OPEN && item.viewerCanMerge
                        Spacer(Modifier.width(6.dp))
                        if (mergeVisible) {
                            FilledTonalIconButton(
                                onClick = { pendingStateConfirmOpen = willOpen },
                                enabled = !actionBusy
                            ) {
                                Icon(
                                    if (willOpen) Lucide.CircleDot else Lucide.X,
                                    contentDescription = if (willOpen) "Reopen" else "Close"
                                )
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { pendingStateConfirmOpen = willOpen },
                                enabled = !actionBusy
                            ) {
                                Icon(
                                    if (willOpen) Lucide.CircleDot else Lucide.X,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (willOpen) "Reopen" else "Close")
                            }
                        }
                    }
                }

                pendingMergeConfirmMethod?.let { method ->
                    AlertDialog(
                        onDismissRequest = { pendingMergeConfirmMethod = null },
                        icon = { Icon(Lucide.GitMerge, contentDescription = null) },
                        title = { Text("Merge #${target.number}?") },
                        text = {
                            Text(
                                "Are you sure you want to merge into ${item.baseRefName ?: "base"} " +
                                    "using ${method.displayLabel}?\n\n${item.title}"
                            )
                        },
                        confirmButton = {
                            Button(
                                enabled = !actionBusy,
                                onClick = {
                                    pendingMergeConfirmMethod = null
                                    scope.launch {
                                        actionBusy = true
                                        container.repository.mergePullRequest(
                                            target.owner, target.repo, target.number, method
                                        ).onSuccess {
                                            onMessage("Merged #${target.number} (${method.apiValue})")
                                            loadDetail(forceRefresh = true)
                                        }.onFailure {
                                            onMessage(it.message ?: "Merge failed")
                                        }
                                        actionBusy = false
                                    }
                                }
                            ) {
                                Text("Merge")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingMergeConfirmMethod = null }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                pendingStateConfirmOpen?.let { willOpen ->
                    val kindLabel = if (isPr) "pull request" else "issue"
                    AlertDialog(
                        onDismissRequest = { pendingStateConfirmOpen = null },
                        icon = {
                            Icon(
                                if (willOpen) Lucide.CircleDot else Lucide.X,
                                contentDescription = null
                            )
                        },
                        title = { Text(if (willOpen) "Reopen #${target.number}?" else "Close #${target.number}?") },
                        text = {
                            Text(
                                "Are you sure you want to ${if (willOpen) "reopen" else "close"} " +
                                    "this $kindLabel?\n\n${item.title}"
                            )
                        },
                        confirmButton = {
                            Button(
                                enabled = !actionBusy,
                                colors = if (!willOpen) {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                } else {
                                    ButtonDefaults.buttonColors()
                                },
                                onClick = {
                                    pendingStateConfirmOpen = null
                                    scope.launch {
                                        actionBusy = true
                                        container.repository.setIssueOrPullState(
                                            target.owner, target.repo, target.number, open = willOpen
                                        ).onSuccess {
                                            onMessage(
                                                if (willOpen) "Reopened #${target.number}"
                                                else "Closed #${target.number}"
                                            )
                                            loadDetail(forceRefresh = true)
                                        }.onFailure {
                                            onMessage(it.message ?: "Failed to update state")
                                        }
                                        actionBusy = false
                                    }
                                }
                            ) {
                                Text(if (willOpen) "Reopen" else "Close")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingStateConfirmOpen = null }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }

    if (showCommentDialog) {
        AlertDialog(
            onDismissRequest = { showCommentDialog = false },
            title = { Text("Comment on #${target.number}") },
            text = {
                OutlinedTextField(
                    value = commentDraft,
                    onValueChange = { commentDraft = it },
                    label = { Text("Markdown comment") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = commentDraft.isNotBlank() && !actionBusy,
                    onClick = {
                        val text = commentDraft
                        showCommentDialog = false
                        scope.launch {
                            actionBusy = true
                            container.repository.postComment(target.owner, target.repo, target.number, text)
                                .onSuccess {
                                    commentDraft = ""
                                    onMessage("Comment posted")
                                    loadDetail(forceRefresh = true)
                                }
                                .onFailure { onMessage(it.message ?: "Failed to post comment") }
                            actionBusy = false
                        }
                    }
                ) {
                    Icon(Lucide.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Post")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommentDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showReviewDialog) {
        AlertDialog(
            onDismissRequest = { showReviewDialog = false },
            title = { Text("Submit PR Review") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OctoConnectedButtonGroup(
                        options = ReviewEvent.entries,
                        selected = reviewEvent,
                        onSelect = { reviewEvent = it },
                        labelProvider = { it.displayLabel },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reviewBody,
                        onValueChange = { reviewBody = it },
                        label = { Text("Review summary (optional for Approve)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !actionBusy && (reviewEvent == ReviewEvent.APPROVE || reviewBody.isNotBlank()),
                    onClick = {
                        val event = reviewEvent
                        val body = reviewBody
                        showReviewDialog = false
                        scope.launch {
                            actionBusy = true
                            container.repository.submitPullReview(target.owner, target.repo, target.number, event, body)
                                .onSuccess {
                                    reviewBody = ""
                                    onMessage("Review submitted (${event.displayLabel})")
                                    loadDetail(forceRefresh = true)
                                }
                                .onFailure { onMessage(it.message ?: "Review submission failed") }
                            actionBusy = false
                        }
                    }
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReviewDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showLabelsDialog) {
        AlertDialog(
            onDismissRequest = { showLabelsDialog = false },
            title = { Text("Manage Labels") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(210.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (availableLabels.isEmpty()) {
                            Text("No labels found in repository.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        availableLabels.forEach { label ->
                            val checked = label.name in selectedLabelNames
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedLabelNames = if (checked) {
                                            selectedLabelNames - label.name
                                        } else {
                                            selectedLabelNames + label.name
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selectedLabelNames = if (isChecked) {
                                            selectedLabelNames + label.name
                                        } else {
                                            selectedLabelNames - label.name
                                        }
                                    }
                                )
                                GitHubLabelChip(name = label.name, colorHex = label.colorHex)
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = newLabelName,
                            onValueChange = { newLabelName = it },
                            label = { Text("New label") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalIconButton(
                            enabled = newLabelName.isNotBlank() && !actionBusy,
                            onClick = {
                                val name = newLabelName.trim()
                                scope.launch {
                                    actionBusy = true
                                    container.repository.createRepoLabel(target.owner, target.repo, name, newLabelColor)
                                        .onSuccess { created ->
                                            availableLabels = (availableLabels + created).distinctBy { it.name }
                                            selectedLabelNames = selectedLabelNames + created.name
                                            newLabelName = ""
                                            onMessage("Created label ${created.name}")
                                        }
                                        .onFailure { onMessage(it.message ?: "Failed to create label") }
                                    actionBusy = false
                                }
                            }
                        ) {
                            Icon(Lucide.Plus, contentDescription = "Create label")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !actionBusy,
                    onClick = {
                        val nextLabels = selectedLabelNames.toList()
                        showLabelsDialog = false
                        scope.launch {
                            actionBusy = true
                            container.repository.updateIssueOrPullMetadata(
                                owner = target.owner,
                                repo = target.repo,
                                number = target.number,
                                labels = nextLabels
                            ).onSuccess {
                                onMessage("Updated labels")
                                loadDetail(forceRefresh = true)
                            }.onFailure {
                                onMessage(it.message ?: "Failed to update labels")
                            }
                            actionBusy = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLabelsDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAssigneesDialog) {
        AlertDialog(
            onDismissRequest = { showAssigneesDialog = false },
            title = { Text("Manage Assignees") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().height(250.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (availableAssignees.isEmpty()) {
                        Text("No assignable users returned.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    availableAssignees.forEach { user ->
                        val checked = user.login in selectedAssigneeLogins
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAssigneeLogins = if (checked) {
                                        selectedAssigneeLogins - user.login
                                    } else {
                                        selectedAssigneeLogins + user.login
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selectedAssigneeLogins = if (isChecked) {
                                        selectedAssigneeLogins + user.login
                                    } else {
                                        selectedAssigneeLogins - user.login
                                    }
                                }
                            )
                            UserAvatar(user.avatarUrl, user.login, sizeDp = 24)
                            Text("@${user.login}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !actionBusy,
                    onClick = {
                        val nextAssignees = selectedAssigneeLogins.toList()
                        showAssigneesDialog = false
                        scope.launch {
                            actionBusy = true
                            container.repository.updateIssueOrPullMetadata(
                                owner = target.owner,
                                repo = target.repo,
                                number = target.number,
                                assignees = nextAssignees
                            ).onSuccess {
                                onMessage("Updated assignees")
                                loadDetail(forceRefresh = true)
                            }.onFailure {
                                onMessage(it.message ?: "Failed to update assignees")
                            }
                            actionBusy = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAssigneesDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showMilestoneDialog) {
        AlertDialog(
            onDismissRequest = { showMilestoneDialog = false },
            title = { Text("Select Milestone") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().height(230.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMilestoneNumber = null }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMilestoneNumber == null,
                            onClick = { selectedMilestoneNumber = null }
                        )
                        Text("No milestone", style = MaterialTheme.typography.bodyMedium)
                    }
                    availableMilestones.forEach { ms ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMilestoneNumber = ms.number }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedMilestoneNumber == ms.number,
                                onClick = { selectedMilestoneNumber = ms.number }
                            )
                            Column {
                                Text(ms.title, style = MaterialTheme.typography.bodyMedium)
                                ms.dueOn?.take(10)?.let { due ->
                                    Text("Due $due", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !actionBusy,
                    onClick = {
                        val chosen = selectedMilestoneNumber
                        showMilestoneDialog = false
                        scope.launch {
                            actionBusy = true
                            container.repository.updateIssueOrPullMetadata(
                                owner = target.owner,
                                repo = target.repo,
                                number = target.number,
                                milestoneNumber = chosen,
                                clearMilestone = chosen == null
                            ).onSuccess {
                                onMessage(if (chosen == null) "Cleared milestone" else "Updated milestone")
                                loadDetail(forceRefresh = true)
                            }.onFailure {
                                onMessage(it.message ?: "Failed to update milestone")
                            }
                            actionBusy = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMilestoneDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHeaderCard(
    item: IssueOrPullDetail,
    onEditLabels: () -> Unit,
    onEditAssignees: () -> Unit,
    onEditMilestone: () -> Unit
) {
    val (stateLabel, stateBg, stateFg) = when {
        item.isDraft -> Triple(
            "DRAFT",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        item.state == IssueOrPullState.OPEN -> Triple(
            "OPEN",
            Color(0xFF16A34A),
            Color.White
        )
        item.state == IssueOrPullState.MERGED -> Triple(
            "MERGED",
            Color(0xFF7C3AED),
            Color.White
        )
        else -> Triple(
            "CLOSED",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError
        )
    }

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExpressiveStatusPill(
                    label = stateLabel,
                    containerColor = stateBg,
                    contentColor = stateFg,
                    icon = when {
                        item.isDraft -> Lucide.Pencil
                        item.state == IssueOrPullState.MERGED -> Lucide.GitMerge
                        item.state == IssueOrPullState.CLOSED -> Lucide.CircleCheck
                        item.kind == IssueOrPullKind.PULL_REQUEST -> Lucide.GitPullRequest
                        else -> Lucide.CircleDot
                    }
                )
                item.checkRollup?.let { rollup ->
                    val checkColor = when (rollup.state) {
                        "SUCCESS" -> Color(0xFF16A34A)
                        "FAILURE", "ERROR" -> MaterialTheme.colorScheme.error
                        else -> Color(0xFFD97706)
                    }
                    ExpressiveStatusPill(
                        label = "CI: ${rollup.state}",
                        containerColor = checkColor.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
                item.reviewDecision?.let { decision ->
                    ExpressiveStatusPill(
                        label = decision.replace('_', ' '),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall
            )

            if (item.kind == IssueOrPullKind.PULL_REQUEST && item.headRefName != null && item.baseRefName != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "${item.headRefName} → ${item.baseRefName}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "+${item.additions} / -${item.deletions}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (item.labels.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.labels.forEach { label ->
                        GitHubLabelChip(name = label.name, colorHex = label.colorHex)
                    }
                }
            }

            if (item.assignees.isNotEmpty() || item.milestone != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.assignees.forEach { user ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            UserAvatar(user.avatarUrl, user.login, sizeDp = 20)
                            Text("@${user.login}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    item.milestone?.let { ms ->
                        ExpressiveStatusPill(
                            label = ms.title,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            icon = Lucide.Flag
                        )
                    }
                }
            }

            if (item.viewerCanEditMetadata) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AssistChip(
                        onClick = onEditLabels,
                        leadingIcon = { Icon(Lucide.Tag, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text("Labels (${item.labels.size})") }
                    )
                    AssistChip(
                        onClick = onEditAssignees,
                        leadingIcon = { Icon(Lucide.Users, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text("Assignees (${item.assignees.size})") }
                    )
                    AssistChip(
                        onClick = onEditMilestone,
                        leadingIcon = { Icon(Lucide.Flag, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text(item.milestone?.title ?: "Milestone") }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineItemCard(event: TimelineEvent) {
    when (event) {
        is TimelineEvent.CommentEvent -> {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(event.author.avatarUrl, event.author.login, sizeDp = 22)
                        Spacer(Modifier.width(8.dp))
                        Text("@${event.author.login}", style = MaterialTheme.typography.labelLarge)
                        val association = event.authorAssociation
                        if (!association.isNullOrBlank() && association != "NONE") {
                            Spacer(Modifier.width(8.dp))
                            ExpressiveStatusPill(
                                label = association,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = event.createdAt.take(10),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    GitHubMarkdown(event.body)
                }
            }
        }

        is TimelineEvent.ReviewEventItem -> {
            val badgeColor = when (event.state) {
                "APPROVED" -> Color(0xFF16A34A)
                "CHANGES_REQUESTED" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.secondary
            }
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = badgeColor.copy(alpha = 0.08f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(event.author.avatarUrl, event.author.login, sizeDp = 22)
                        Spacer(Modifier.width(8.dp))
                        Text("@${event.author.login}", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(8.dp))
                        ExpressiveStatusPill(
                            label = event.state.replace('_', ' '),
                            containerColor = badgeColor.copy(alpha = 0.2f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (event.body.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        GitHubMarkdown(event.body)
                    }
                }
            }
        }

        is TimelineEvent.CommitEvent -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Lucide.GitCommitHorizontal,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = event.oidShort,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = event.messageHeadline,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        is TimelineEvent.StateChangeEvent -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val actor = event.actor
                if (actor != null) {
                    UserAvatar(actor.avatarUrl, actor.login, sizeDp = 18)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = "@${actor?.login ?: "system"} ${event.actionLabel} • ${event.createdAt.take(10)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
