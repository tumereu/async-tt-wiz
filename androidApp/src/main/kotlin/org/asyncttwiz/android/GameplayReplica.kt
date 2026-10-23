package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Serialized accepted generation and volatile overlays. UI consumes endpoints, never rollback/replay waypoints. */
class GameplayReplica {
    private data class Prediction(val command: GameCommand, val actor: String,
        var commitRevision: String? = null, var withdrawn: Boolean = false)
    private val predictions = linkedMapOf<String, Prediction>()
    private val records = linkedMapOf<String, GameRevision>()
    var tree: DownloadedTree? = null
        private set
    var revision: String? = null
        private set
    var accepted: PlayState? = null
        private set
    var display: PlayState? = null
        private set
    val retainedRecords get() = records.values.toList()
    val pendingIds get() = predictions.keys.toList()

    fun predict(command: GameCommand, actor: String): Boolean {
        if (command.requestId in predictions) return false
        val base = display ?: return false
        val retained = displayTree
        if (retained != null) {
            if (HistoryContinuation.change(retained, actor, command).tree == null) return false
        } else if (GameplayRules.change(base, actor, command).state == null) return false
        predictions[command.requestId] = Prediction(command, actor)
        rebase(); return true
    }
    fun withdraw(requestId: String) { predictions[requestId]?.withdrawn = true; rebase() }
    fun retire(requestId: String) { predictions.remove(requestId); rebase() }
    fun result(result: GameResult) {
        val prediction = predictions[result.requestId] ?: return
        require(result.scope == prediction.command.scope)
        when (result.outcome) {
            "accepted" -> { GameWire.ordinal(requireNotNull(result.commitRevision)); prediction.commitRevision = result.commitRevision }
            "noOp", "rejected" -> predictions.remove(result.requestId)
            else -> error("Invalid result")
        }
        rebase()
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
        accepted = play; revision = head.revision; records.clear(); rebase(); return true
    }
    /** False means a gap/new incarnation: fetch a coherent baseline, never apply on an unmatched base. */
    fun apply(update: GameRevision): Boolean {
        val current = accepted ?: return false
        val at = revision ?: return false
        require(update.gameId == current.gameId && update.summary.id == current.gameId &&
            update.summary.revision == update.resultRevision)
        if (update.play?.let { it.id != current.id } == true || update.summary.playId != current.id) return false
        if (GameWire.ordinal(update.resultRevision) <= GameWire.ordinal(at)) {
            correlate(update); rebase(); return true
        }
        if (update.baseRevision != at) return false
        predictions[update.requestId]?.let { require(update.scope == it.command.scope) }
        val nextTree = if (update.treeDelta != null) tree?.let { TreeSync.apply(it, update.treeDelta!!) } ?: return false
            else if (update.play == null && update.mutation == null) tree else null
        val endpoint = nextTree?.tree?.let { requireNotNull(it.stateAt(it.currentNodeId)) } ?: update.play ?: current
        if (nextTree != null && update.play != null) require(endpoint == update.play)
        tree = nextTree
        accepted = endpoint; revision = update.resultRevision; records[update.resultRevision] = update.copy(chatMessage = null)
        correlate(update); rebase(); return true
    }
    /** Build in isolation, catch up to the published generation, correlate originals, then switch once. */
    fun baseline(baseline: GameplayBaseline, tail: List<GameRevision> = emptyList(), through: String = baseline.snapshot.head.revision): Boolean {
        GameplaySync.validate(baseline.snapshot)
        val head = baseline.snapshot.head
        var state = head.play!!
        var retained = baseline.snapshot.treeRecords.takeIf { it.isNotEmpty() }?.let {
            TreeSync.decode(it.asSequence(), head.gameId, state.id) }
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
        accepted = state; revision = at; records.clear(); records.putAll(staged)
        staged.values.forEach(::correlate)
        for (status in baseline.statuses) if (status.status == "final") result(status.result!!)
        rebase(); return true
    }
    private fun correlate(update: GameRevision) {
        predictions[update.requestId]?.let {
            require(update.scope == it.command.scope)
            it.commitRevision = update.resultRevision
        }
    }
    private var displayTree: GameplayTree? = null
    private fun rebase() {
        var next = accepted ?: return
        var nextTree = tree?.tree
        val at = revision!!
        predictions.entries.removeAll { (_, prediction) -> prediction.commitRevision?.let {
            GameWire.ordinal(at) >= GameWire.ordinal(it) } == true }
        for (prediction in predictions.values) {
            if (prediction.withdrawn) continue
            if (nextTree != null) {
                val change = HistoryContinuation.change(nextTree, prediction.actor, prediction.command)
                if (change.tree == null) prediction.withdrawn = true else {
                    val predicted = requireNotNull(change.tree)
                    nextTree = predicted
                    next = requireNotNull(predicted.stateAt(predicted.currentNodeId))
                }
            } else {
                val change = GameplayRules.change(next, prediction.actor, prediction.command)
                if (change.state == null) prediction.withdrawn = true else next = requireNotNull(change.state)
            }
        }
        displayTree = nextTree
        display = next
    }

    /** Worker-prepared accepted generation: no disk/replay work is hidden in the UI publication. */
    fun published(head: GameHead, generation: DownloadedTree, at: String,
        statuses: List<GameRequestStatus>, tail: List<GameRevision>): Boolean {
        val tree = generation.tree
        val endpoint = requireNotNull(tree.stateAt(tree.currentNodeId))
        if (revision?.let { GameWire.ordinal(it) > GameWire.ordinal(at) } == true) return false
        require(head.gameId == endpoint.gameId && head.play?.id == endpoint.id)
        this.tree = generation; accepted = endpoint; revision = at; records.clear()
        tail.forEach(::correlate)
        for (status in statuses) if (status.status == "final") result(requireNotNull(status.result))
        rebase(); return true
    }
}
