package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.GameplayTreeStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

/** Adapter for an ALREADY authorized ActivityTarget.GROUP (never a raw push hint). Group metadata
 * labels the captured target; the exact boundary alone selects review, including parent fallback. */
data class CapturedHistoryTarget(val accountId: String, val gameId: String, val playId: String,
    val groupId: String?, val boundaryNodeId: String)
data class CapturedHistoryReview(val target: CapturedHistoryTarget, val view: DownloadedHistoryState)

/** GameSession's personal history review: resolves a timeline cursor or a captured boundary on an
 * isolated copy of the retained tree, on the review worker, and persists the review cursor. */
internal class SessionHistoryReview(private val scope: SessionScope, reviewWorker: ExecutorService,
    private val checkAccount: () -> Unit, private val replicaOf: (String) -> GameplayReplica?,
    private val summaryOf: (String) -> GameSummary?, private val cacheOf: (String) -> GameplayTreeStore?,
    private val observedPlay: (String) -> String?) {
    private val reviews = LatestReview(reviewWorker)
    private val reviewVersions = ConcurrentHashMap<String, Long>()
    /** Personal read-only review, including offline. Cold replay and cursor persistence are worker
     * work; account/incarnation/clear fences are checked before disk and again before UI delivery. */
    fun review(gameId: String, nodeId: String?, complete: (DownloadedHistoryState) -> Unit): Boolean {
        val timing = if (Diagnostics.enabled) DebugLatency.begin(gameId, "HistoryReview",
            (replicaOf(gameId)?.tree?.tree?.nodes?.size ?: 0) + 1) else null
        checkAccount()
        val retained = replicaOf(gameId)?.tree?.tree ?: return false
        val revision = replicaOf(gameId)?.revision?.let(GameWire::ordinal) ?: return false
        val cache = cacheOf(gameId)
        val generation = scope.epoch; val owner = scope.account
        val request = reviewVersions.merge(gameId, 1L, Long::plus)!!
        fun valid() = !scope.closed && generation == scope.epoch && owner == scope.account && scope.sameAccount() &&
            observedPlay(gameId) == retained.initial.id && reviewVersions[gameId] == request
        if (nodeId == null) {
            reviews.cancel(gameId)
            val state = replicaOf(gameId)?.accepted ?: return false
            complete(DownloadedHistoryState(revision, state.currentNodeId, false, state))
            // Personal disk persistence is coalesced separately; LIVE never queues tree work.
            reviews.submit(gameId) { if (valid()) cache?.reviewCursor(null) }
            timing?.submitted(null, true); timing?.finish()
            return true
        }
        val isolated = retained.materializedCopy()
        reviews.submit(gameId) {
            if (!valid()) return@submit
            val cursor = isolated.resolveCursor(nodeId)
            val state = cursor?.let(isolated::prepareReview)
            if (!valid()) return@submit
            cache?.reviewCursor(cursor)
            val view = DownloadedHistoryState(revision, cursor, true, state,
                error = if (state == null) "Downloaded history is unavailable." else null,
                contextVersion = cursor?.let(isolated::contextVersion))
            scope.post { if (valid()) {
                // Transfer a prepared cache slot, never share the review worker's tree instance.
                if (replicaOf(gameId)?.tree?.tree === retained) retained.receivePreparedReview(isolated)
                complete(view)
                timing?.submitted(null, true); timing?.finish()
            } }
        }
        return true
    }

    /** Personal exact-boundary route for an authorized activity/history consumer. Stale scope/access
     * or route lifetime is refused, never replaced by current or converted to a shared control. */
    fun reviewTarget(target: CapturedHistoryTarget, isCurrent: () -> Boolean,
        complete: (CapturedHistoryReview) -> Unit): Boolean {
        checkAccount()
        val summary = summaryOf(target.gameId) ?: return false
        val retained = replicaOf(target.gameId)?.tree?.tree ?: return false
        val revision = replicaOf(target.gameId)?.revision?.let(GameWire::ordinal) ?: return false
        val cache = cacheOf(target.gameId)
        if (!listOfNotNull(target.accountId, target.gameId, target.playId, target.groupId, target.boundaryNodeId).all(DeckValidation::uuid) ||
            scope.account != target.accountId || summary.lifecycle != GameLifecycle.ACTIVE || summary.playId != target.playId ||
            retained.initial.id != target.playId || retained.initial.gameId != target.gameId ||
            target.accountId !in retained.initial.board.players.map { it.accountId } ||
            target.accountId !in listOf(summary.inviter.accountId, summary.recipient.accountId) || !isCurrent()) return false
        val generation = scope.epoch
        val key = "target:${target.gameId}"
        val request = reviewVersions.merge(key, 1L, Long::plus)!!
        fun valid() = !scope.closed && generation == scope.epoch && scope.account == target.accountId && scope.sameAccount() &&
            observedPlay(target.gameId) == target.playId && reviewVersions[key] == request && runCatching(isCurrent).getOrDefault(false)
        val isolated = retained.materializedCopy()
        reviews.submit(key) {
            if (!valid()) return@submit
            val resolved = isolated.resolveCursor(target.boundaryNodeId)
            val state = resolved?.let(isolated::prepareReview)
            if (!valid()) return@submit
            if (state != null) cache?.reviewCursor(resolved)
            val review = CapturedHistoryReview(target, DownloadedHistoryState(revision, resolved, reviewing = true,
                state = state, error = if (state == null) "This retained history boundary is unavailable." else null,
                contextVersion = resolved?.let(isolated::contextVersion)))
            scope.post { if (valid()) {
                if (replicaOf(target.gameId)?.tree?.tree === retained) retained.receivePreparedReview(isolated)
                complete(review)
            } }
        }
        return true
    }
    fun clear() { reviews.clear(); reviewVersions.clear() }
}
