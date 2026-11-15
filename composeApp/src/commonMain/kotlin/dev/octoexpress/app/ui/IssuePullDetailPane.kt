package dev.octoexpress.app.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.FileDiff
import com.composables.icons.lucide.GitCommitHorizontal
import com.composables.icons.lucide.GitMerge
import com.composables.icons.lucide.GitPullRequest
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.MessageSquarePlus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.X
import dev.octoexpress.app.AppContainer
import dev.octoexpress.app.IssuePullTarget
import dev.octoexpress.auth.openUrlInBrowser
import dev.octoexpress.model.IssueOrPullDetail
import dev.octoexpress.model.IssueOrPullKind
import dev.octoexpress.model.IssueOrPullState
import dev.octoexpress.model.MergeMethod
import dev.octoexpress.model.PullFileChange
import dev.octoexpress.model.ReviewEvent
import dev.octoexpress.model.TimelineEvent
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
                                DetailHeaderCard(item)
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

                    Spacer(Modifier.width(6.dp))

                    if (isPr && item.state == IssueOrPullState.OPEN) {
                        FilledTonalButton(
                            onClick = { showReviewDialog = true },
                            enabled = !actionBusy
                        ) {
                            Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Review")
                        }

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
                        )
                    } else if (item.state != IssueOrPullState.MERGED) {
                        val willOpen = item.state == IssueOrPullState.CLOSED
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    actionBusy = true
                                    container.repository.setIssueOrPullState(
                                        target.owner, target.repo, target.number, open = willOpen
                                    ).onSuccess {
                                        onMessage(if (willOpen) "Reopened #${target.number}" else "Closed #${target.number}")
                                        loadDetail(forceRefresh = true)
                                    }.onFailure {
                                        onMessage(it.message ?: "Failed to update state")
                                    }
                                    actionBusy = false
                                }
                            },
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHeaderCard(item: IssueOrPullDetail) {
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
                    icon = if (item.kind == IssueOrPullKind.PULL_REQUEST) Lucide.GitPullRequest else Lucide.CircleDot
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
