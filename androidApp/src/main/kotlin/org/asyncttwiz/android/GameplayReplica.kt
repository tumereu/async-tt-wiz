package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Serialized accepted generation and volatile overlays. UI consumes endpoints, never rollback/replay waypoints. */
class GameplayReplica(private val tableChanges: (TableChangeBatch) -> Unit = {}) {
    private val animatedPredictions = mutableSetOf<String>()
    private fun effects(from: PlayState?, previous: GameplayTree?, skip: Set<String> = emptySet(),
        next: PlayState? = display, nextTree: GameplayTree? = displayTree) {
        next ?: return
        val batch = TableChangeExtraction.between(from?.currentNodeId, next.currentNodeId,
            nextTree?.nodes.orEmpty(), previous?.nodes.orEmpty())
        tableChanges(batch.copy(events = batch.events.filter { it.mutation.requestId !in skip }, cursor = display?.currentNodeId))
    }
    private data class Prediction(val command: GameCommand, val actor: String,
        var commitRevision: String? = null, var withdrawn: Boolean = false)
    private val predictions = linkedMapOf<String, Prediction>()
    private val records = linkedMapOf<String, GameRevision>()
    var tree: DownloadedTree? = null
        private set
    /** Retained with accepted game/play data across table exits. */
    var history: HistoryTree? = null
        private set
    var revision: String? = null
        private set
    var accepted: PlayState? = null
        private set
    var display: PlayState? = null
        private set
    val retainedRecords get() = records.values.toList()
    val pendingIds get() = predictions.keys.toList()
    var applyFailure: String? = null
        private set
    /** Presentation only. Accepted review/storage continue to use tree. */
    var displayTree: GameplayTree? = null
        private set
    fun predicted(requestId: String) = predictions[requestId]?.withdrawn == false

    fun predict(command: GameCommand, actor: String): Boolean {
        if (command.requestId in predictions) return false
        val base = display ?: return false
        val retained = displayTree
        if (command.action !is GameAction.Play && predictions.values.any { it.withdrawn }) return false
        if (retained != null) {
            // A valid no-op (e.g. an already-reset corner after a move) still belongs on the
            // ordinary queue. It has no predicted node, but is not an enqueue failure.
            if (predictChange(retained, actor, command).reason != null) return false
        } else if (GameplayRules.change(base, actor, command).state == null) return false
        predictions[command.requestId] = Prediction(command, actor)
        rebase()
        effects(base, retained)
        animatedPredictions.add(command.requestId)
        return true
    }
    fun withdraw(requestId: String) {
        val before = display?.currentNodeId
        predictions[requestId]?.withdrawn = true; rebase()
        if (display?.currentNodeId != before) tableChanges(TableChangeBatch(jump = true))
    }
    fun retire(requestId: String) {
        val before = display?.currentNodeId
        predictions.remove(requestId); animatedPredictions.remove(requestId); rebase()
        if (display?.currentNodeId != before) tableChanges(TableChangeBatch(jump = true))
    }
    fun result(result: GameResult) {
        val before = display?.currentNodeId
        correlateResult(result)
        if (result.outcome != "accepted") animatedPredictions.remove(result.requestId)
        rebase()
        if (display?.currentNodeId != before) tableChanges(TableChangeBatch(jump = true))
    }
    private fun correlateResult(result: GameResult) {
        val prediction = predictions[result.requestId] ?: return
        require(result.scope == prediction.command.scope)
        when (result.outcome) {
            "accepted" -> { GameWire.ordinal(requireNotNull(result.commitRevision)); prediction.commitRevision = result.commitRevision }
            "noOp", "rejected" -> predictions.remove(result.requestId)
            else -> error("Invalid result")
        }
    }
    /** A root-only first head is already a complete current-tree snapshot in this engine. */
    fun root(head: GameHead): Boolean {
        val play = head.play ?: return false
        if (play.currentNodeId != play.root.id || predictions.isNotEmpty()) return false
        if (revision?.let { GameWire.ordinal(it) > GameWire.ordinal(head.revision) } == true) return false
        require(head.gameId == head.summary.id && head.revision == head.summary.revision &&
            head.summary.lifecycle == GameLifecycle.ACTIVE && head.summary.playId == play.id && head.gameId == play.gameId &&
            head.summary.preparationRound == play.preparationRound && play.engineVersion == 1 && play.readFormat == 1)
        tree = TreeSync.generation(GameplayTree(play))
        history = HistoryTree.ingest(tree!!.tree, head.revision)
        accepted = play; revision = head.revision; records.clear(); rebase()
        animatedPredictions.clear(); tableChanges(TableChangeBatch(jump = true)); return true
    }
    /** False means a gap/new incarnation: fetch a coherent baseline, never apply on an unmatched base. */
    fun apply(update: GameRevision): Boolean {
        applyFailure = null
        fun fail(reason: String): Boolean { applyFailure = reason; return false }
        val current = accepted ?: return fail("acceptedMissing")
        val at = revision ?: return fail("revisionMissing")
        require(update.gameId == current.gameId && update.summary.id == current.gameId &&
            update.summary.revision == update.resultRevision)
        if (update.play?.let { it.id != current.id } == true || update.summary.playId != current.id) return fail("playChanged")
        if (GameWire.ordinal(update.resultRevision) <= GameWire.ordinal(at)) {
            correlate(update); rebase(); return true
        }
        if (update.baseRevision != at) return fail("revisionGap:local=$at:base=${update.baseRevision}:result=${update.resultRevision}")
        predictions[update.requestId]?.let { require(update.scope == it.command.scope) }
        val nextTree = if (update.treeDelta != null) tree?.let { TreeSync.apply(it, update.treeDelta!!) } ?: return fail("treeMissing")
            else if (update.play == null && update.mutation == null) tree else null
        val endpoint = nextTree?.tree?.let { requireNotNull(it.stateAt(it.currentNodeId)) } ?: update.play ?: current
        if (nextTree != null && update.play != null) require(endpoint == update.play)
        val before = accepted
        val previous = tree?.tree
        val predicted = animatedPredictions.toSet()
        val confirmation = update.requestId in predicted
        animatedPredictions.remove(update.requestId)
        tree = nextTree
        history = nextTree?.let { HistoryTree.ingest(it.tree, update.resultRevision, history) }
        accepted = endpoint; revision = update.resultRevision; records[update.resultRevision] = update.copy(chatMessage = null)
        correlate(update); rebase()
        if (!confirmation) effects(before, previous, predicted, accepted, tree?.tree)
        return true
    }
    /** Build in isolation, catch up to the published generation, correlate originals, then switch once. */
    fun baseline(baseline: GameplayBaseline, tail: List<GameRevision> = emptyList(), through: String = baseline.snapshot.head.revision): Boolean {
        GameplaySync.validate(baseline.snapshot)
        val head = baseline.snapshot.head
        var state = head.play!!
        var retained = baseline.snapshot.treeRecords.takeIf { it.isNotEmpty() }?.let {
            TreeSync.decode(it.asSequence(), head.gameId, state.id) }
        retained?.let { state = requireNotNull(it.tree.stateAt(it.tree.currentNodeId)) }
        var at = head.revision
        val staged = baseline.snapshot.records.map { it.copy(chatMessage = null) }.associateByTo(linkedMapOf()) { it.resultRevision }
        for (update in tail.sortedBy { GameWire.ordinal(it.resultRevision) }) {
            if (GameWire.ordinal(update.resultRevision) <= GameWire.ordinal(at)) continue
            if (update.baseRevision != at || update.play?.let { it.id != state.id } == true || update.gameId != head.gameId) return false
            require(update.summary.revision == update.resultRevision && update.summary.playId == state.id)
            if (retained != null) {
                val delta = update.treeDelta
                if (delta != null) retained = TreeSync.apply(retained, delta)
                else if (update.play != null && update.play != state) return false
                state = requireNotNull(retained.tree.stateAt(retained.tree.currentNodeId))
                update.play?.let { require(it == state) }
            } else state = update.play ?: state
            at = update.resultRevision; staged[at] = update.copy(chatMessage = null)
        }
        if (GameWire.ordinal(at) < GameWire.ordinal(through) ||
            revision?.let { GameWire.ordinal(at) < GameWire.ordinal(it) } == true) return false
        val known = staged.values.map { it.requestId }.toSet()
        val statuses = baseline.statuses.associateBy { it.requestId }
        // A snapshot can cross an original whose result/delta has not arrived. Absence is not rejection.
        if (predictions.values.any { it.command.requestId !in known && it.commitRevision == null &&
                statuses[it.command.requestId]?.status !in setOf("final", "pending", "notAdmitted") }) return false
        for (status in baseline.statuses) if (status.status == "final") {
            val result = requireNotNull(status.result)
            require(result.requestId == status.requestId)
            predictions[status.requestId]?.let { require(result.scope == it.command.scope) }
            if (result.outcome == "accepted") GameWire.ordinal(requireNotNull(result.commitRevision))
            else require(result.outcome in setOf("noOp", "rejected"))
        }
        staged.values.forEach { update -> predictions[update.requestId]?.let { require(update.scope == it.command.scope) } }
        tree = retained
        history = retained?.let { HistoryTree.ingest(it.tree, at) }
        accepted = state; revision = at; records.clear(); records.putAll(staged)
        staged.values.forEach(::correlate)
        for (status in baseline.statuses) if (status.status == "final") correlateResult(status.result!!)
        rebase(); animatedPredictions.retainAll(predictions.keys); tableChanges(TableChangeBatch(jump = true)); return true
    }
    private fun correlate(update: GameRevision) {
        predictions[update.requestId]?.let {
            require(update.scope == it.command.scope)
            it.commitRevision = update.resultRevision
        }
    }
    /** The same bounded inverse/forward traversal as authoritative processing, including selective controls. */
    private fun predictChange(tree: GameplayTree, actor: String, command: GameCommand): GameplayTreeChange {
        if (command.action !is GameAction.Play && command.action != GameAction.Undo && command.action != GameAction.Redo)
            return GameplayTreeChange(reason = "predictionUnavailable")
        return HistoryContinuation.change(tree, actor, command)
    }

    private fun rebase() {
        var next = accepted ?: return
        var nextTree = tree?.tree
        val at = revision!!
        predictions.entries.removeAll { (_, prediction) -> prediction.commitRevision?.let {
            GameWire.ordinal(at) >= GameWire.ordinal(it) } == true }
        var controlBlocked = false
        var uncertainOriginal = false
        for (prediction in predictions.values) {
            val control = prediction.command.action !is GameAction.Play
            if (prediction.withdrawn) {
                uncertainOriginal = true
                if (control) controlBlocked = true
                continue
            }
            // A control that no longer predicts must not leave later overlays on its old board.
            if (controlBlocked) continue
            if (control && uncertainOriginal) {
                prediction.withdrawn = true; controlBlocked = true
                continue
            }
            if (nextTree != null) {
                val change = predictChange(nextTree, prediction.actor, prediction.command)
                if (change.reason != null) {
                    prediction.withdrawn = true; uncertainOriginal = true
                    if (control) controlBlocked = true
                } else if (change.tree != null) {
                    val predicted = requireNotNull(change.tree)
                    nextTree = predicted
                    next = requireNotNull(predicted.stateAt(predicted.currentNodeId))
                }
            } else {
                val change = GameplayRules.change(next, prediction.actor, prediction.command)
                if (change.state == null) {
                    prediction.withdrawn = true; uncertainOriginal = true
                    if (control) controlBlocked = true
                } else next = requireNotNull(change.state)
            }
        }
        displayTree = nextTree
        display = next
    }

    /** Worker-prepared accepted generation: no disk/replay work is hidden in the UI publication. */
    fun published(head: GameHead, generation: DownloadedTree, at: String,
        statuses: List<GameRequestStatus>, tail: List<GameRevision>, indexed: HistoryTree, animate: Boolean = false): Boolean {
        val tree = generation.tree
        val endpoint = requireNotNull(tree.stateAt(tree.currentNodeId))
        if (revision?.let { GameWire.ordinal(it) > GameWire.ordinal(at) } == true) return false
        require(head.gameId == endpoint.gameId && head.play?.id == endpoint.id)
        val before = accepted
        val previous = this.tree?.tree
        val predicted = animatedPredictions.toSet()
        this.tree = generation; accepted = endpoint; revision = at; records.clear()
        history = indexed
        tail.forEach(::correlate)
        // Correlate the whole snapshot before replaying its remaining tail: status ordering must
        // not temporarily replay already-accepted originals and permanently withdraw later controls.
        for (status in statuses) if (status.status == "final") correlateResult(requireNotNull(status.result))
        rebase()
        if (animate) {
            val confirmation = tail.isNotEmpty() && tail.all { it.requestId in predicted }
            tail.forEach { animatedPredictions.remove(it.requestId) }
            if (!confirmation) effects(before, previous, predicted, accepted, this.tree?.tree)
        } else {
            animatedPredictions.retainAll(predictions.keys)
            tableChanges(TableChangeBatch(jump = true))
        }
        return true
    }
}
