package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*
import java.util.concurrent.Executor

/** Personal display/armed intent only. The session remains the sole tree, prediction and FIFO owner.
 * Cold indexing, graph projection and return previews run on the supplied worker. */
class TableHistoryController(private val worker: Executor, private val post: (() -> Unit) -> Unit,
    private val review: (String?, (DownloadedHistoryState) -> Unit) -> Boolean,
    private val submit: (PlayAction, String?, Continuation?) -> Boolean,
    private val control: (GameAction) -> Boolean) : AutoCloseable {
    var playback by mutableStateOf<HistoryPlayback?>(null); private set
    var view by mutableStateOf<DownloadedHistoryState?>(null); private set
    var armed by mutableStateOf<String?>(null); private set
    var provisional by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var gate by mutableStateOf<ResultPlaybackGate?>(null); private set
    var foreground by mutableStateOf(true); private set
    var confirmation by mutableStateOf<BranchArchiveConfirmation?>(null); private set
    var choices by mutableStateOf<List<BranchReturnChoice>>(emptyList()); private set
    var branch by mutableStateOf<String?>(null); private set
    private var tree: GameplayTree? = null
    private var firstRequest: String? = null
    private var precedingPresented: String? = null
    @Volatile private var epoch = 0L
    private var indexedEpoch = 0L
    private var displayEpoch = 0L
    private var viewVersion: String? = null
    private var closed = false

    fun update(value: GameplayTree, version: String) {
        if (closed || (tree === value && playback?.tree?.liveVersion == version)) return
        tree = value
        val generation = ++epoch
        worker.execute {
            val index = HistoryTree(value.initial.root.id, value.nodes.values.toList(), value.currentNodeId, version)
            post {
                if (!closed && generation == epoch) {
                    indexedEpoch = generation
                    val old = playback
                    if (old == null || old.tree.rootId != index.rootId) playback = HistoryPlayback(index)
                    else old.receive(index)
                    if (!foreground) playback?.background()
                    val result = value.nodes[value.currentNodeId]?.takeIf { it.action is PlayAction.SuggestResult }
                    if (result != null && (old == null || old.position.mode == HistoryMode.LIVE) &&
                        precedingPresented !in setOf(result.parentId, result.id)) {
                        playback?.scrub(result.parentId)
                        playback?.play()
                    }
                    val boundary = armed
                    if (!provisional && boundary != null && value.resolveCursor(boundary) != boundary) cancel()
                }
            }
        }
    }

    fun displayPosition() {
        val playback = playback ?: return
        if (closed || indexedEpoch != epoch) return
        val position = playback.position
        val index = playback.tree
        val treeGeneration = epoch
        val generation = ++displayEpoch
        val node = if (position.mode == HistoryMode.LIVE) null else position.cursorId
        if (!review(node) { next ->
            if (!closed && generation == displayEpoch && treeGeneration == epoch && this.playback === playback &&
                playback.position == position && playback.tree === index) {
                // Session review captures independently of this asynchronously built index. It may
                // already be ahead even before update() is called; wait for the matching index.
                if (next.cursor != null && (!index.contains(next.cursor) ||
                        (position.mode == HistoryMode.LIVE && next.cursor != index.sharedHeadId))) return@review
                view = next; viewVersion = index.liveVersion; error = next.error
                if (next.cursor != null && next.cursor != position.cursorId) playback.scrub(next.cursor)
            }
        }) error = "History is not downloaded yet."
    }

    fun select(node: String) { cancel(); playback?.takeIf { it.tree.contains(node) }?.scrub(node) }
    fun canArm(node: String): Boolean {
        val playback = playback ?: return false
        return playback.canContinue(node) && view?.cursor == node && view?.state != null && viewVersion == playback.tree.liveVersion
    }
    fun arm(node: String): Boolean {
        if (!canArm(node)) return false
        armed = node; provisional = false; firstRequest = null; gate = null
        return true
    }
    fun cancel() { armed = null; provisional = false; firstRequest = null; gate = null }
    fun current() { cancel(); playback?.let { it.scrub(it.tree.sharedHeadId); it.continuationApplied() } }
    fun updateForeground(value: Boolean) {
        foreground = value
        if (value) playback?.foreground() else playback?.background()
    }

    /** Every basic/manual/Library/token/quick/combat lowering callback passes this same seam. */
    fun gameplay(action: PlayAction, invocation: String?, predicted: () -> PlayState?): Boolean {
        val boundary = armed
        val before = predicted()?.currentNodeId
        val sent = submit(action, invocation, boundary?.let { Continuation(it, explicit = true) })
        if (sent && boundary != null) {
            val after = predicted()
            if (after != null && after.currentNodeId != before) {
                provisional = true
                armed = after.currentNodeId
            }
        }
        return sent
    }
    fun captured(command: GameCommand) {
        if (command.continuation != null && firstRequest == null) firstRequest = command.requestId
    }
    fun inspectionSubmitted(predicted: PlayState?) {
        if (armed != null && predicted != null) { armed = predicted.currentNodeId; provisional = true }
    }
    fun settled(command: GameCommand, result: GameResult) {
        if (command.requestId != firstRequest) return
        if (result.outcome == "accepted") { cancel(); playback?.continuationApplied() }
        else {
            val original = command.continuation?.nodeId
            cancel()
            if (original != null && playback?.tree?.contains(original) == true) {
                playback?.scrub(original); armed = original
            }
        }
    }

    fun acceptedTarget(review: CapturedHistoryReview) {
        val cursor = review.view.cursor
        if (closed || tree?.initial?.id != review.target.playId) return
        if (cursor == null || review.view.state == null) { error = review.view.error ?: "This history boundary is unavailable."; return }
        cancel(); playback?.takeIf { it.tree.contains(cursor) }?.scrub(cursor)
        view = review.view
        viewVersion = playback?.tree?.liveVersion
    }

    /** Called only after a frame has actually presented this endpoint. Current S must be preceded
     * by its final board; historical/stale/removed suggestions never receive an actionable gate. */
    fun presented(state: PlayState, accepted: PlayState?, live: Boolean) {
        if (state.board.resultSuggestion != null && state.currentNodeId != accepted?.currentNodeId) { gate = null; return }
        val suggestion = accepted?.board?.resultSuggestion
        val node = suggestion?.let { tree?.nodes?.get(it.id) }
            ?: tree?.nodes?.values?.singleOrNull { (it.action as? PlayAction.SuggestResult) != null &&
                it.id == accepted?.currentNodeId }
        if (suggestion == null || node == null || !live || state.id != accepted.id ||
            state.currentNodeId != accepted.currentNodeId || state.board.resultSuggestion?.id != suggestion.id) {
            gate = null
            precedingPresented = state.currentNodeId
            return
        }
        if (precedingPresented == node.parentId || gate?.suggestionId == suggestion.id)
            gate = ResultPlaybackGate(ResultTarget(state.gameId, state.id), suggestion.id)
        precedingPresented = state.currentNodeId
    }

    fun prepareReturn(branchId: String, chosen: BranchArchiveTarget? = null, restore: Boolean = false) {
        val value = tree ?: return
        val generation = epoch
        branch = branchId; confirmation = null
        worker.execute {
            val catalog = value.archiveCatalog()
            val preview = BranchArchives.previewReturn(value, catalog, branchId, chosen, restore)
            val action = preview.target?.let { CapturedBranchArchiveAction(GameScope(value.initial.gameId, playId = value.initial.id),
                BranchArchiveIntent.ArchiveAndReturn(branchId, it, restore, preview.returnHead)) }
            val options = value.branchHeads.keys.filter { it != branchId }.mapNotNull { id -> catalog.head(id)?.let {
                BranchReturnChoice(it, "Continuation ${id.take(8)} · ${it.nodeId.take(8)}", id in value.branchArchives.archivedBranchIds)
            } }
            post { if (!closed && generation == epoch && branch == branchId) {
                confirmation = BranchArchiveConfirmation(action, preview); choices = options
            } }
        }
    }
    fun dismissReturn() { branch = null; confirmation = null; choices = emptyList() }
    fun request(action: CapturedBranchArchiveAction): Boolean {
        val value = tree ?: return false
        return action.scope == GameScope(value.initial.gameId, playId = value.initial.id) && control(GameAction.BranchArchive(action.intent))
    }
    override fun close() { closed = true; epoch++; displayEpoch++; cancel(); view = null; tree = null; playback = null; dismissReturn() }
}
