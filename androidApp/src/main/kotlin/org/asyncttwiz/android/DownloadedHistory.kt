package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.GameplayTreeStore
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

data class DownloadedHistoryState(val revision: Long? = null, val cursor: String? = null,
    val reviewing: Boolean = false, val state: PlayState? = null,
    val downloadIncomplete: Boolean = false, val error: String? = null, val contextVersion: String? = null) {
    val hasCompleteGeneration get() = revision != null
    val readOnly get() = true

    /** Own-area downloaded real faces need no event; opponent covered holds disclose nothing.
     * Other-side / contents inspection is deliberately absent from this read-only API.
     */
    fun holdFace(accountId: String, cardId: String): DefinitionFace? {
        val play = state ?: return null
        val card = play.board.cards.find { it.id == cardId } ?: return null
        val ownArea = play.board.containers.find { it.id == card.containerId }?.accountId == accountId
        if (card.covered && !ownArea) return null
        return play.definitions.find { it.digest == card.definition }?.definition?.faces?.find { it.faceId == card.faceId }
    }
}

/** Usable downloaded-review coordinator, with no transport, request submission, logged-inspection
 * or speculative journal dependency. I26b wires it to the real relational tree/session and route.
 * Construct with a cache bound on the storage worker. post must enqueue on the UI thread; the
 * scope predicate must reflect current auth + game incarnation/deletion before any display.
 */
class DownloadedHistory(private val cache: GameplayTreeStore, private val post: (() -> Unit) -> Unit,
    private val currentScope: () -> Boolean, private val changed: (DownloadedHistoryState) -> Unit,
    private val worker: ExecutorService = Executors.newSingleThreadExecutor(),
    uiThread: Thread = Thread.currentThread()) : AutoCloseable {
    private val epoch = AtomicLong()
    private val download = TreeDownload(cache, uiThread)
    // Worker-owned, accepted/derived only. No second authoritative local tree or board journal.
    private var accepted: PublishedDownloadedTree? = null
    private var staged: StagedDownloadedTree? = null
    private var cursor: String? = null
    private var restored = false
    private var incomplete = false

    fun load() = enqueue {
        accepted = download.accepted()
        incomplete = cache.transfer() != null
        display()
    }

    /** Automatically fetch all chunks. Never publishes staging as missing/completed history. */
    fun stage(plan: TreeDownloadPlan, openChunk: (Int) -> InputStream) = enqueue {
        incomplete = true; display()
        staged = download.stage(plan, openChunk)
        display()
    }

    /** Called after the session has supplied interleaved arrivals and an independent verified head.
     * A gap/pruned suffix requests a newer full generation; old accepted review remains usable.
     */
    fun publish(tail: List<TreeRecordDelta>, verifiedHead: Long) = enqueue {
        val pending = staged
        if (pending == null) { incomplete = true; display(); return@enqueue }
        when (val result = download.publish(pending, tail, verifiedHead)) {
            is TreePublication.Published -> { accepted = result.value; incomplete = false }
            TreePublication.RestartRequired -> { incomplete = true }
        }
        staged = null
        display()
    }

    fun apply(delta: TreeRecordDelta) = enqueue {
        when (val result = download.apply(delta)) {
            is TreePublication.Published -> accepted = result.value
            TreePublication.RestartRequired -> incomplete = true
        }
        display()
    }

    /** Local selection does not activate a branch, change Redo, submit or log any inspection. */
    fun select(nodeId: String) = enqueue {
        val tree = accepted?.generation?.tree ?: return@enqueue
        val resolved = tree.resolveCursor(nodeId) ?: return@enqueue
        cursor = resolved
        cache.reviewCursor(resolved)
        restored = true
        display()
    }

    fun followCurrent() = enqueue { cursor = null; cache.reviewCursor(null); restored = true; display() }

    private fun display(error: String? = null) {
        val published = accepted
        val tree = published?.generation?.tree
        val selected = cursor
        val reviewing = selected != null
        val node = if (selected != null) tree?.resolveCursor(selected) else tree?.currentNodeId
        if (reviewing && node != null && cursor != node) {
            cursor = node
            cache.reviewCursor(node)
        }
        // Heavy replay happens here on the serial worker, never in the posted UI callback.
        val state = node?.let { tree?.stateAt(it) }
        val view = DownloadedHistoryState(published?.version?.revision, node, reviewing, state, incomplete,
            error ?: if (published != null && state == null) "Downloaded history is unavailable." else null,
            node?.let { tree?.contextVersion(it) })
        deliver(view)
    }

    private fun deliver(view: DownloadedHistoryState) {
        val generation = epoch.get()
        if (generation != 0L || !cache.isCurrent()) return
        post { if (epoch.get() == generation && currentScope()) changed(view) }
    }

    private fun enqueue(action: () -> Unit) {
        val generation = epoch.get()
        if (generation != 0L) return
        worker.execute {
            if (epoch.get() != generation) return@execute
            try {
                if (!restored) {
                    cursor = cache.reviewCursor()
                    accepted = download.accepted()
                    incomplete = cache.transfer() != null
                    restored = true
                }
                action()
            }
            catch (_: Exception) {
                // No SQL, private payloads or paths escape. Staging/old accepted records survive.
                if (epoch.get() == generation) runCatching { display("Download or recovery is incomplete. Retry when available.") }
            }
        }
    }

    override fun close() {
        epoch.incrementAndGet()
        cache.invalidate()
        worker.shutdown() // already-running blocking work retains its normal finally/close cleanup
    }
}
