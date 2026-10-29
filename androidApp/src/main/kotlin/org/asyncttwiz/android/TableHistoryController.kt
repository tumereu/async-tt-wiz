package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/** Personal display/armed intent only. The session remains the sole tree, prediction and FIFO owner.
 * Cold indexing and graph projection run on the supplied worker. */
class TableHistoryController(private val worker: Executor, private val post: (() -> Unit) -> Unit,
    private val review: (String?, (DownloadedHistoryState) -> Unit) -> Boolean,
    private val submit: (PlayAction, String?, Continuation?) -> Boolean) : AutoCloseable {
    var playback by mutableStateOf<HistoryPlayback?>(null); private set
    var view by mutableStateOf<DownloadedHistoryState?>(null); private set
    var armed by mutableStateOf<String?>(null); private set
    var continuationTarget by mutableStateOf<String?>(null); private set
    var provisional by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var gate by mutableStateOf<ResultPlaybackGate?>(null); private set
    var foreground by mutableStateOf(true); private set
    var usernames by mutableStateOf<Map<String, String>>(emptyMap()); private set
    internal var explorerHistory by mutableStateOf<HistoryTree?>(null); private set
    private var tree: GameplayTree? = null
    private var firstRequest: String? = null
    private var precedingPresented: String? = null
    @Volatile private var epoch = 0L
    private var indexedEpoch = 0L
    private var displayEpoch = 0L
    // Observed: canArm/canRequestContinuation read it in composition, and it can change while [view] stays equal.
    private var viewVersion by mutableStateOf<String?>(null)
    private var closed = false
    private var textViewer: String? = null
    private val indexes = LatestReview(worker)
    // Worker-only presentation cache; no per-node boards and no cross-thread tree cache mutation.
    private var textIndex: HistoryTree? = null
    private var acceptedIndex: HistoryTree? = null
    private var acceptedTree: GameplayTree? = null
    // Event texts made while a board was at hand (live end, predictions, playback/review points).
    // Merged from the index and text workers; read through immutable snapshots.
    private val textCache = AtomicReference(HistoryTextCache(null, emptyMap()))
    private val textWork = LatestReview(worker)
    private var opening = false
    private var lastSeen: String? = null
    private var recordSeen: ((String, String) -> Unit)? = null
    private var lastPresentedLive: Pair<String, String>? = null
    val changes = TableChanges()
    /** The short text of the shown board's latest group; see [EventTextFeed]. */
    internal val eventText = EventTextFeed(worker, post)
    private var stepChanges: Pair<String, TableChangeBatch>? = null
    private val pendingChanges = ArrayDeque<TableChangeBatch>()
    private fun clearChanges() { changes.clear(); stepChanges = null; pendingChanges.clear() }
    fun liveChanges(batch: TableChangeBatch) {
        if (batch.jump) clearChanges()
        else if (!closed && foreground && batch.events.isNotEmpty() &&
            (playback?.position?.mode == HistoryMode.LIVE || armed != null)) {
            if (pendingChanges.size >= 256) clearChanges()
            pendingChanges.addLast(batch)
        }
    }
    /** Align the effects with the actual displayed endpoint, including held live reveals and
     * coalesced rapid predictions. The pending window has a fixed bound, independent of history. */
    fun showChanges(cursor: String) {
        val through = pendingChanges.indexOfLast { it.cursor == cursor }
        if (through >= 0) {
            val events = (0..through).flatMap { pendingChanges.removeFirst().events }
            changes.apply(TableChangeBatch(events, cursor = cursor))
        }
    }
    private fun playbackChanges(from: String, to: String, index: HistoryTree) {
        stepChanges = to to tablePlaybackChanges(from, to, index)
    }

    /** Configure once before the first update; ordinary review never changes this bookmark. */
    fun openFrom(nodeId: String?, record: (String, String) -> Unit) {
        if (playback != null || opening) return
        opening = true; lastSeen = nodeId; recordSeen = record
    }

    fun update(value: GameplayTree, version: String, game: GameSummary? = null, viewer: String? = null,
        accepted: GameplayTree = value, indexed: HistoryTree? = null) {
        val names = game?.let { listOf(it.inviter, it.recipient).associate { user -> user.accountId to user.username } }.orEmpty()
        if (closed || (tree === value && acceptedTree === accepted && playback?.tree?.liveVersion == version && usernames == names && textViewer == viewer)) return
        tree = value; acceptedTree = accepted; usernames = names; textViewer = viewer
        val generation = ++epoch
        val isolated = value.materializedCopy()
        val acceptedCopy = if (accepted.nodes === value.nodes) isolated else accepted.materializedCopy()
        indexes.submit("index") {
            if (generation != epoch) return@submit
            val retained = indexed?.takeIf { it.rootId == value.initial.root.id && it.nodes === accepted.nodes }
            val previous = retained?.takeIf { it.nodes === value.nodes }
                ?: textIndex?.takeIf { it.rootId == value.initial.root.id } ?: retained
            val index = HistoryTree(value.initial.root.id, emptyList(), value.currentNodeId, version,
                containers = value.initial.board.containers, source = isolated, previous = previous, lazyPresentation = true)
            val acceptedHistory = if (acceptedCopy === isolated) index else HistoryTree(accepted.initial.root.id,
                emptyList(), accepted.currentNodeId, version, containers = accepted.initial.board.containers,
                source = acceptedCopy, previous = retained ?: acceptedIndex?.takeIf { it.rootId == accepted.initial.root.id }, lazyPresentation = true)
            if (generation != epoch) return@submit
            index.texts = TimelineTexts(isolated, viewer, names, liveTexts(isolated, viewer, names, generation))
            textIndex = index; acceptedIndex = acceptedHistory
            post {
                if (!closed && generation == epoch) {
                    indexedEpoch = generation
                    explorerHistory = acceptedHistory
                    val old = playback
                    if (old == null || old.tree.rootId != index.rootId) playback = HistoryPlayback(index, ::playbackChanges,
                        { clearChanges() }).also {
                        if (opening) it.openFrom(lastSeen)
                    }
                    else old.receive(index, viewer)
                    if (!foreground) playback?.background()
                    val result = value.nodes[value.currentNodeId]?.takeIf { it.action is PlayAction.SuggestResult }
                    if (result != null && playback?.position?.mode == HistoryMode.LIVE &&
                        precedingPresented !in setOf(result.parentId, result.id)) playback?.holdLiveAt(result.parentId)
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
        val atHead = position.mode == HistoryMode.LIVE && !playback.livePanelReveal
        val node = if (atHead) null else position.cursorId
        if (!review(node) { next ->
            if (!closed && generation == displayEpoch && treeGeneration == epoch && this.playback === playback &&
                playback.position == position && playback.tree === index) {
                // Session review captures independently of this asynchronously built index. It may
                // already be ahead even before update() is called; wait for the matching index.
                if (next.cursor != null && (!index.contains(next.cursor) ||
                        (atHead && next.cursor != index.sharedHeadId))) return@review
                view = next; viewVersion = index.liveVersion; error = next.error
                next.state?.let { reviewTexts(index, it) }
                eventText.displayed(next.cursor, next.state, position.mode, index.nodes, textViewer, usernames, index::isAncestor)
                stepChanges?.takeIf { it.first == next.cursor }?.let { changes.apply(it.second); stepChanges = null }
                if (position.mode == HistoryMode.PLAYBACK && next.cursor != null && next.cursor != position.cursorId)
                    playback.scrub(next.cursor)
            }
        }) error = "History is not downloaded yet."
    }

    /** Worker: texts of the events just before the live end, stepped back from its board (no replay).
     * Events a change rewrote or re-parented lose their old texts first, and so do their descendants. */
    private fun liveTexts(tree: GameplayTree, viewer: String?, names: Map<String, String>, generation: Long): HistoryTextCache {
        val retained = tree.endpointIndex() // Built here, so presentation lookups never index on the UI thread.
        val previous = textIndex?.source?.takeIf { it.initial.root.id == tree.initial.root.id }
        val changed = previous?.let(tree::changedNodeIdsSince).orEmpty()
        val dropped = if (changed.isEmpty()) emptySet() else retained.descendants(changed.filter(tree.nodes::containsKey)) + changed
        val head = tree.preparedState(tree.currentNodeId)
        val added = head?.let { historyTextsNear(tree, it, textCache.get().forContext(viewer, names), HEAD_TEXT_WINDOW,
            dropped = dropped) { generation == epoch } }.orEmpty()
        return textCache.updateAndGet { it.forContext(viewer, names).with(added, dropped) }
    }

    /** A shown board names its own event, its neighbours and a short stretch ahead on the live line;
     * work is queued only when one of those has no text yet. */
    private fun reviewTexts(index: HistoryTree, state: PlayState) {
        val source = index.source ?: return
        val cursor = state.currentNodeId
        if (state.id != source.initial.id || !index.contains(cursor)) return
        val viewer = textViewer; val names = usernames
        val cache = textCache.get().forContext(viewer, names)
        val retained = source.endpointIndex()
        val line = retained.paths[index.sharedHeadId]
        val depth = retained.positions[cursor]?.depth
        val forward = if (line != null && depth != null && line.getOrNull(depth) == cursor)
            line.subList(depth + 1, minOf(line.size, depth + 1 + REVIEW_TEXT_AHEAD)) else emptyList()
        fun missing(id: String) = id in source.nodes && cache.text(source, id) == null
        if (!missing(cursor) && retained.children[cursor].orEmpty().none(::missing) && forward.none(::missing)) return
        val generation = epoch
        textWork.submit("texts") {
            if (generation != epoch) return@submit
            val added = historyTextsNear(source, state, cache, REVIEW_TEXT_BACK, forward, REVIEW_TEXT_AHEAD) { generation == epoch }
            if (added.isEmpty()) return@submit
            textCache.updateAndGet { it.forContext(viewer, names).with(added) }
            post {
                val shown = playback?.tree
                if (!closed && generation == epoch && shown === index && textViewer == viewer && usernames == names)
                    shown.texts = TimelineTexts(source, viewer, names, textCache.get())
            }
        }
    }

    fun select(node: String) { clearChanges(); cancel(); playback?.takeIf { it.tree.contains(node) }?.scrub(node) }
    fun canArm(node: String): Boolean {
        val playback = playback ?: return false
        return playback.canContinue(node) && view?.cursor == node && view?.state != null && viewVersion == playback.tree.liveVersion
    }
    fun canRequestContinuation(node: String): Boolean {
        val player = playback ?: return false
        return foreground && player.position.mode == HistoryMode.PLAYBACK && player.position.cursorId == node &&
            player.tree.contains(node) && view?.cursor == node && view?.state != null && viewVersion == player.tree.liveVersion
    }
    /** Explicit continue actions arm the original exact boundary immediately. No shared mutation. */
    fun requestContinuation(node: String): Boolean {
        if (!canRequestContinuation(node)) return false
        if (node == playback?.tree?.sharedHeadId && node == tree?.currentNodeId) {
            cancelContinuation()
            return true
        }
        playback?.pause()
        continuationTarget = null
        return arm(node)
    }
    /** Only toggling review off offers a choice; Back still abandons review immediately. */
    fun requestExitReview(allowContinuation: Boolean = true) {
        val player = playback
        val cursor = player?.position?.cursorId
        if (armed == null && player != null && cursor != null && cursor != player.tree.sharedHeadId &&
            allowContinuation && canRequestContinuation(cursor)) {
            player.pause()
            continuationTarget = cursor
        } else if (cursor != null && cursor == player.tree.sharedHeadId) cancelContinuation()
        else current()
    }
    fun dismissContinuation() { continuationTarget = null }
    fun confirmContinuation(): Boolean {
        val target = continuationTarget ?: return false
        continuationTarget = null
        return requestContinuation(target)
    }
    fun arm(node: String): Boolean {
        if (!canArm(node)) return false
        armed = node; provisional = false; firstRequest = null; gate = null
        return true
    }
    fun cancel() { armed = null; continuationTarget = null; provisional = false; firstRequest = null; gate = null }
    /** Abandon only local intent and show the live head; no gameplay or control request. */
    fun cancelContinuation() { clearChanges(); cancel(); playback?.continuationApplied() }
    fun current() { if (armed != null) cancelContinuation() else { cancel(); playback?.play() } }
    fun updateForeground(value: Boolean) {
        if (!value) clearChanges()
        foreground = value
        if (value) playback?.foreground() else playback?.background()
    }
    /** Returning from the background is still waiting for its catch-up at an unchanged head. */
    val resumeHeld: Boolean get() = playback?.resumeHeld == true
    /** The session has received everything the server had after returning; [current] is its display
     * tree. Resolves the return pause only once that exact tree has been indexed into the playback. */
    fun resumeCaughtUp(current: GameplayTree?) {
        if (closed || !foreground || current == null || tree !== current || indexedEpoch != epoch) return
        playback?.caughtUp()
    }

    /** Every basic/manual/Library/token/quick/combat lowering callback passes this same seam. */
    fun gameplay(action: PlayAction, invocation: String?, predicted: () -> PlayState?): Boolean {
        return gameplay(predicted) { continuation -> submit(action, invocation, continuation) }
    }
    fun gameplay(predicted: () -> PlayState?, send: (Continuation?) -> Boolean): Boolean {
        val boundary = armed
        val before = predicted()?.currentNodeId
        val sent = send(boundary?.let { Continuation(it, explicit = true) })
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
        if (!closed && live && foreground && playback?.position?.mode == HistoryMode.LIVE &&
            playback?.position?.cursorId == state.currentNodeId) {
            val shown = state.gameId to state.currentNodeId
            if (shown != lastPresentedLive) { lastPresentedLive = shown; recordSeen?.invoke(shown.first, shown.second) }
        }
        if (state.board.resultSuggestion != null && state.currentNodeId != accepted?.currentNodeId) { gate = null; return }
        val suggestion = accepted?.board?.resultSuggestion
        val node = suggestion?.let { tree?.nodes?.get(it.id) }
            ?: accepted?.currentNodeId?.let { tree?.nodes?.get(it) }?.takeIf { it.action is PlayAction.SuggestResult }
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

    fun leaving() { clearChanges(); lastPresentedLive?.let { recordSeen?.invoke(it.first, it.second) } }
    override fun close() { leaving(); eventText.clear(); closed = true; epoch++; displayEpoch++; indexes.clear(); textWork.clear(); cancel(); view = null; tree = null; playback = null
        acceptedTree = null; explorerHistory = null; acceptedIndex = null; textIndex = null; textCache.set(HistoryTextCache(null, emptyMap())) }
}

/** Events before the live end that get texts from its board on each update (stepped back, never replayed). */
internal const val HEAD_TEXT_WINDOW = 128
/** Events before / after a shown board that get texts from it. */
internal const val REVIEW_TEXT_BACK = 64
internal const val REVIEW_TEXT_AHEAD = 32
