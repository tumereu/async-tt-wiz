package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*
import java.util.concurrent.ExecutorService

/** LobbyController's open table: gameplay, annotations, result requests and face inspections sent for
 * the selected game, its personal history (timeline) controller, and the session's trees read for
 * composition. Main thread. */
internal class LobbyTableActions(private val scope: LobbyScope, private val table: State<LobbyTableView>,
    private val accountId: () -> String?, private val worker: ExecutorService, private val post: (() -> Unit) -> Unit,
    private val session: GameSession?) {
    private var state: LobbyState
        get() = scope.state
        set(value) { scope.state = value }
    private val closed get() = scope.closed
    private val epoch get() = scope.epoch
    /** The selected game's trees live in the session, outside snapshot state. Bumped whenever one of them
     * is replaced, and read by [retainedTree]/[displayTree]/[retainedHistory], so composition that reads a
     * tree recomposes when it changes (and not merely when some unrelated lobby field happens to). */
    private val treeRevision = mutableLongStateOf(0L)
    private var shownTrees: List<Any?> = emptyList()
    var historyOwner: TableHistoryController? = null
        private set
    fun resetHistory() { historyOwner?.close(); historyOwner = null }
    val history: TableHistoryController get() = historyOwner ?: TableHistoryController(worker, post, ::reviewHistory,
        submit = { action, invocation, continuation ->
            val game = state.detail
            val actor = accountId()
            game != null && actor != null && !closed && session?.gameplay(game, actor, action, invocation, continuation,
                captured = { historyOwner?.captured(it) }) == true
        }).also { historyOwner = it }
    /** Bumps [treeRevision] when the selected game's trees ([trees]) are other objects than those shown. */
    fun showTrees(trees: List<Any?>) {
        if (trees.size != shownTrees.size || trees.indices.any { trees[it] !== shownTrees[it] }) {
            shownTrees = trees; treeRevision.longValue++
        }
    }
    /** The result lock for the table right now, the one gate every table entry point consults (see
     * [ResultActions.blocksAt]). Reviewing: the shown point. Continuing: the armed point, then its predicted
     * new branch. Otherwise: the live endpoint. Branching away from a suggestion leaves it on the old branch. */
    fun resultLocked(): Boolean {
        // Read through the narrow view: the table calls this during composition.
        val shown = table.value
        val game = shown.detail ?: return false
        val personal = historyOwner
        val continuing = personal?.armed != null
        val reviewing = personal?.playback?.position?.mode == HistoryMode.PLAYBACK && !personal.provisional
        val board = if (reviewing) personal?.view?.state else if (continuing) shown.play else null
        return ResultActions.blocksAt(game, shown.acceptedPlay, shown.play, board, inTimeline = reviewing || continuing)
    }
    fun captureResult(): ResultTarget? = state.detail?.let { ResultActions.capture(it, state.play) }
    fun suggestResult(target: ResultTarget, outcome: SuggestedResult, note: String = ""): Boolean {
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        if (ResultActions.capture(game, state.play) != target) return false
        return !closed && gameplay(PlayAction.SuggestResult(outcome, ResultNoteInput.clean(note)))
    }
    fun acceptResult(target: ResultAcceptanceTarget): Boolean {
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        return !closed && session?.acceptResult(game, actor, target) == true
    }
    fun rejectResult(target: ResultAcceptanceTarget): Boolean {
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        return !closed && session?.rejectResult(game, actor, target) == true
    }
    fun gameplay(action: PlayAction, invocationId: String? = null, continuation: Continuation? = null): Boolean {
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        val personal = historyOwner
        if (continuation == null && personal != null) {
            if (personal.armed == null && personal.playback?.position?.mode == HistoryMode.PLAYBACK) return false
            return personal.gameplay(action, invocationId) { session?.play(game) }
        }
        return !closed && session?.gameplay(game, actor, action, invocationId, continuation) == true
    }
    fun historyControl(action: GameAction): Boolean {
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        return !closed && session?.historyControl(game, actor, action) == true
    }
    /** [tags]: an automated batch's tag per member (Automatum, rules v36). */
    fun gameplayBatch(actions: List<PlayAction>, requestId: String, tags: List<MtgTag?> = emptyList(), replaces: String? = null): Boolean {
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        if (closed) { replaces?.let(::dropPreview); return false }
        val personal = historyOwner
        if (personal != null && personal.armed == null && personal.playback?.position?.mode == HistoryMode.PLAYBACK) {
            replaces?.let(::dropPreview); return false
        }
        fun send(continuation: Continuation?) = session?.gameplayBatch(game, actor, actions, requestId, continuation,
            captured = { historyOwner?.captured(it) }, tags = tags, replaces = replaces) == true
        return if (personal != null) personal.gameplay({ session?.play(game) }, ::send) else send(null)
    }
    /** I-D47: a cast shown at once on this device while its batch is worked out; only on the live line (not while a
     * timeline point is held or reviewed, where the batch continues from another board). */
    fun previewBatch(actions: List<PlayAction>, requestId: String, tags: List<MtgTag?>): Boolean {
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        if (closed) return false
        val personal = historyOwner
        if (personal != null && (personal.armed != null || personal.playback?.position?.mode == HistoryMode.PLAYBACK)) return false
        return session?.previewBatch(game, actor, actions, tags, requestId) == true
    }
    fun previewShownOn(requestId: String): String? = state.detail?.let { session?.previewShownOn(it, requestId) }
    fun dropPreview(requestId: String) { state.detail?.let { session?.dropPreview(it, requestId) } }
    /** Accepted disclosure observer, not Boolean enqueue -> inspection. The UI supplies its exact
     * inspector lifetime and closes the returned subscription on Back/dismissal/replacement. */
    fun inspectFace(action: PlayAction.InspectFace, lifetimeId: String, isCurrent: () -> Boolean,
        observed: (InspectionEvent) -> Unit): InspectionSubscription? {
        val game = state.detail ?: return null
        val actor = accountId() ?: return null
        val generation = epoch
        return session?.inspectFace(game, actor, action, lifetimeId, isCurrent = {
            !closed && generation == epoch && accountId() == actor && state.selected == game.id &&
                state.detail?.playId == game.playId && isCurrent()
        }, observed = observed, continuation = historyOwner?.armed?.let { Continuation(it, true) },
            captured = { historyOwner?.captured(it) }).also { if (it != null) historyOwner?.inspectionSubmitted(state.play) }
    }
    fun retainedTree() = selectedTree(GameSession::retainedTree)
    fun retainedHistory() = selectedTree(GameSession::retainedHistory)
    fun displayTree() = selectedTree(GameSession::displayTree)
    private fun <T> selectedTree(read: GameSession.(String) -> T?): T? {
        treeRevision.longValue // Observed: the session's trees are not snapshot state.
        val selected = table.value.selected ?: return null
        return session?.read(selected)
    }
    fun reviewHistory(nodeId: String?, complete: (DownloadedHistoryState) -> Unit): Boolean =
        state.selected?.let { session?.review(it, nodeId, complete) } == true
    fun reviewHistoryTarget(target: CapturedHistoryTarget, isCurrent: () -> Boolean,
        complete: (CapturedHistoryReview) -> Unit): Boolean {
        if (closed || state.selected != target.gameId || state.detail?.playId != target.playId || accountId() != target.accountId) return false
        val generation = epoch
        return session?.reviewTarget(target, isCurrent = {
            !closed && generation == epoch && accountId() == target.accountId && state.selected == target.gameId &&
                state.detail?.playId == target.playId && isCurrent()
        }, complete = complete) == true
    }
}
