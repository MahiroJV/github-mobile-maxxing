package dev.octoexpress.network

import dev.octoexpress.model.IssueOrPullDetail
import dev.octoexpress.model.IssueOrPullKind
import dev.octoexpress.model.IssueOrPullState
import dev.octoexpress.model.IssueOrPullSummary
import dev.octoexpress.model.IssueStateFilter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal data class NormalizedIssueKey(
    val ownerLower: String,
    val repoLower: String,
    val number: Int
) {
    companion object {
        fun of(owner: String, repo: String, number: Int): NormalizedIssueKey =
            NormalizedIssueKey(owner.trim().lowercase(), repo.trim().lowercase(), number)

        fun of(summary: IssueOrPullSummary): NormalizedIssueKey =
            of(summary.repoOwner, summary.repoName, summary.number)
    }
}

internal data class RecentIssueStateEntry(
    val targetState: IssueOrPullState,
    val summaryHint: IssueOrPullSummary?,
    val recordedAt: TimeMark = TimeSource.Monotonic.markNow()
) {
    fun isActive(ttl: Duration = 90.seconds): Boolean = recordedAt.elapsedNow() < ttl
}

internal fun IssueOrPullDetail.toSummary(commentCount: Int = 0): IssueOrPullSummary =
    IssueOrPullSummary(
        id = id,
        number = number,
        title = title,
        kind = kind,
        state = state,
        isDraft = isDraft,
        repoFullName = repoFullName,
        repoOwner = repoOwner,
        repoName = repoName,
        author = author,
        createdAt = createdAt,
        updatedAt = updatedAt,
        commentCount = commentCount,
        labels = labels,
        checkRollup = checkRollup,
        reviewDecision = reviewDecision
    )

/**
 * Reconciles fetched or cached issue/PR lists against recent local state mutations
 * (close/reopen/merge) so GitHub Search eventual-consistency lag never resurrects
 * a closed item in the Open list or hides it from Closed/All lists.
 */
fun reconcileIssueListWithStateOverrides(
    items: List<IssueOrPullSummary>,
    stateFilter: IssueStateFilter,
    overrides: Map<Pair<String, Int>, IssueOrPullState>,
    hintCandidates: List<IssueOrPullSummary> = emptyList()
): List<IssueOrPullSummary> {
    if (overrides.isEmpty() && hintCandidates.isEmpty()) return items

    fun keyFor(summary: IssueOrPullSummary): Pair<String, Int> =
        summary.repoFullName.trim().lowercase().ifBlank {
            "${summary.repoOwner.trim().lowercase()}/${summary.repoName.trim().lowercase()}"
        } to summary.number

    val updated = items.map { item ->
        val overrideState = overrides[keyFor(item)]
        if (overrideState != null && overrideState != item.state) {
            item.copy(state = overrideState)
        } else {
            item
        }
    }

    val existingKeys = updated.mapTo(HashSet(updated.size)) { keyFor(it) }
    val appendedFromHints = hintCandidates.mapNotNull { hint ->
        val key = keyFor(hint)
        val overrideState = overrides[key] ?: return@mapNotNull null
        if (key in existingKeys) return@mapNotNull null
        val adjusted = hint.copy(state = overrideState)
        val matches = when (stateFilter) {
            IssueStateFilter.OPEN -> adjusted.state == IssueOrPullState.OPEN
            IssueStateFilter.CLOSED -> adjusted.state == IssueOrPullState.CLOSED || adjusted.state == IssueOrPullState.MERGED
            IssueStateFilter.ALL -> true
        }
        if (matches) adjusted else null
    }

    return (appendedFromHints + updated).filter { item ->
        when (stateFilter) {
            IssueStateFilter.OPEN -> item.state == IssueOrPullState.OPEN
            IssueStateFilter.CLOSED -> item.state == IssueOrPullState.CLOSED || item.state == IssueOrPullState.MERGED
            IssueStateFilter.ALL -> true
        }
    }
}

internal fun patchIssueListForSingleMutation(
    current: List<IssueOrPullSummary>,
    stateFilter: IssueStateFilter,
    targetKey: NormalizedIssueKey,
    newState: IssueOrPullState,
    summaryHint: IssueOrPullSummary?
): List<IssueOrPullSummary> {
    val updatedHint = summaryHint?.copy(state = newState)
    val withoutOrUpdated = current.mapNotNull { item ->
        val matchesTarget = NormalizedIssueKey.of(item) == targetKey
        val next = if (matchesTarget) item.copy(state = newState) else item
        val fitsFilter = when (stateFilter) {
            IssueStateFilter.OPEN -> next.state == IssueOrPullState.OPEN
            IssueStateFilter.CLOSED -> next.state == IssueOrPullState.CLOSED || next.state == IssueOrPullState.MERGED
            IssueStateFilter.ALL -> true
        }
        if (fitsFilter) next else null
    }
    if (updatedHint == null) return withoutOrUpdated
    val fitsNew = when (stateFilter) {
        IssueStateFilter.OPEN -> updatedHint.state == IssueOrPullState.OPEN
        IssueStateFilter.CLOSED -> updatedHint.state == IssueOrPullState.CLOSED || updatedHint.state == IssueOrPullState.MERGED
        IssueStateFilter.ALL -> true
    }
    if (!fitsNew || withoutOrUpdated.any { NormalizedIssueKey.of(it) == targetKey }) {
        return withoutOrUpdated
    }
    return listOf(updatedHint) + withoutOrUpdated
}
