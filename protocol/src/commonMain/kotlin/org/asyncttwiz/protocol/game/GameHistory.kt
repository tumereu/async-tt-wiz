package org.asyncttwiz.protocol.game

/** One event of a game's tree as stored, with its [depth] (the root is 0, its children 1). */
data class HistoryNode(val event: GameEvent, val depth: Int)

/**
 * Where a game's history tree is kept (I-D54 §4): the events by id and the encoded checkpoints ([EngineJson.encodeModel])
 * by event id. The root, [rootId], is no event: its checkpoint is the game's state before the first event.
 */
interface HistoryStore {
    val rootId: String
    fun node(id: String): HistoryNode?
    fun checkpoint(id: String): String?

    /** [id]'s node and its ancestors, nearest first, stopping at the first one with a checkpoint (whose node is not
     * included) or after [limit] nodes. A store backed by a database may read them in one query. */
    fun line(id: String, limit: Int): List<HistoryNode> = buildList {
        var cursor = id
        while (size < limit && cursor != rootId && checkpoint(cursor) == null) {
            val node = node(cursor) ?: throw NoSuchElementException("No event $cursor")
            add(node)
            cursor = node.event.parentId
        }
    }
}

/**
 * A game's history tree with engine-state checkpoints along it (I-D54 §4; owner rule: never rebuild from the root, games
 * reach 10k+ events): an event at a depth that is a multiple of [every] has a checkpoint, so the state at any event is
 * the nearest checkpoint above it plus at most [every] − 1 events applied ([stateAt]); a state already at hand moves to
 * a nearby event by reverting and applying the events between ([move]).
 */
class GameHistory(val store: HistoryStore, val codec: EngineJson, val every: Int = CHECKPOINT_EVERY) {
    init { require(every > 0) }

    /** Events applied or reverted so far (tests check the work stays bounded). */
    var steps: Int = 0
        private set

    fun depthOf(id: String): Int = if (id == store.rootId) 0 else (store.node(id) ?: throw NoSuchElementException("No event $id")).depth

    /** Whether an event at [depth] gets a checkpoint. */
    fun checkpointed(depth: Int): Boolean = depth % every == 0

    /** The state at [id]: the nearest checkpoint at or above it, then the events below it applied in order. */
    fun stateAt(id: String): GameModel {
        val line = store.line(id, every)
        val top = line.lastOrNull()?.event?.parentId ?: id
        val checkpoint = store.checkpoint(top) ?: throw IllegalStateException("No checkpoint within $every events above $id")
        val model = codec.decodeModel(checkpoint)
        for (node in line.asReversed()) { model.apply(node.event); steps++ }
        return model
    }

    /**
     * [model], the state at [from], moved to the state at [to]: the events from [from] up to where the two lines meet
     * are reverted and those down to [to] applied, when that is no more than [every] events and stays exact (see
     * [GameModel.stale]); otherwise the state is taken from a checkpoint ([stateAt]). [model] is used up either way.
     */
    fun move(model: GameModel, from: String, to: String): GameModel {
        if (from == to) return model
        val up = mutableListOf<GameEvent>()
        val down = mutableListOf<GameEvent>()
        var a = from; var b = to
        var depthA = depthOf(a); var depthB = depthOf(b)
        while (a != b) {
            if (up.size + down.size >= every) return stateAt(to)
            if (depthA >= depthB) {
                val node = store.node(a) ?: return stateAt(to)
                up += node.event; a = node.event.parentId; depthA--
            } else {
                val node = store.node(b) ?: return stateAt(to)
                down += node.event; b = node.event.parentId; depthB--
            }
        }
        for (event in up) {
            model.revert(event); steps++
            if (model.stale) return stateAt(to)
        }
        for (event in down.asReversed()) { model.apply(event); steps++ }
        return model
    }

    /** What a store keeps for an event at [depth] whose state is [model]: its checkpoint, or null. */
    fun checkpointFor(depth: Int, model: GameModel): String? = if (checkpointed(depth)) codec.encodeModel(model) else null

    companion object {
        /** Events between checkpoints: a cold state is one checkpoint decoded plus at most this many events applied. */
        const val CHECKPOINT_EVERY = 64
    }
}

/** A [HistoryStore] in memory: tests, and a device's copy of a game. */
class InMemoryHistory(override val rootId: String, initial: String) : HistoryStore {
    private val nodes = mutableMapOf<String, HistoryNode>()
    private val checkpoints = mutableMapOf(rootId to initial)

    override fun node(id: String): HistoryNode? = nodes[id]
    override fun checkpoint(id: String): String? = checkpoints[id]

    val size: Int get() = nodes.size
    val checkpointCount: Int get() = checkpoints.size

    /** Adds [event] under its parent; [checkpoint] as [GameHistory.checkpointFor] gives it for the event's depth. */
    fun add(event: GameEvent, checkpoint: String?): HistoryNode {
        require(event.id !in nodes && event.id != rootId) { "Event ${event.id} exists" }
        val depth = if (event.parentId == rootId) 1 else (nodes[event.parentId] ?: throw NoSuchElementException("No parent ${event.parentId}")).depth + 1
        return HistoryNode(event, depth).also { node ->
            nodes[event.id] = node
            if (checkpoint != null) checkpoints[event.id] = checkpoint
        }
    }

    /** Takes the events [ids] out with their checkpoints (a rewind deletes the events after its target, I-D56 §10). */
    fun remove(ids: Set<String>) {
        nodes.keys.removeAll(ids)
        checkpoints.keys.removeAll(ids)
    }

    fun depthOfParent(event: GameEvent): Int = if (event.parentId == rootId) 0 else nodes.getValue(event.parentId).depth
}
