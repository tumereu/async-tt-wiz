package org.asyncttwiz.automatum

import arcanum.api.CardDefinition
import arcanum.engine.state.GameState
import org.asyncttwiz.protocol.CommandIds
import org.asyncttwiz.protocol.Continuation
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameCommand
import org.asyncttwiz.protocol.GameScope
import org.asyncttwiz.protocol.GameplayTree
import org.asyncttwiz.protocol.GameplayTreeChange
import org.asyncttwiz.protocol.HistoryContinuation
import org.asyncttwiz.protocol.MtgTag
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.RandomV1
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.GameWire
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Engine calls on a shared history tree, as the server keeps it (final sync review): each call's batch goes through
 * [HistoryContinuation] (the server's and the clients' one processing path), so Undo, Redo, reverting a flow (I-D19),
 * switching Automatum and branches act on the same tree. The engine's event log is read from the tree's tags along the
 * current line ([engineTags] as the app walks it). Every call's batch is checked: the state rebuilt from the tree's new
 * head equals the engine's own result.
 */
internal class TreeGame(start: PlayState, val definitions: Map<String, CardDefinition>, seed: Long = 1, private val name: String = "tree") {
    var tree = GameplayTree(start)
    private val random = Random(seed)
    private var requests = 0

    val head: String get() = tree.currentNodeId
    val state: PlayState get() = tree.stateAt(tree.currentNodeId)!!

    fun seed(): String = RandomV1.encodeSeed(Random(random.nextLong()).nextBytes(32))

    /** The tags of the events from the start up to [nodeId], in order (as the app's `engineTags`). */
    fun tags(nodeId: String = head): List<MtgTag?> {
        val tags = ArrayList<MtgTag?>()
        var id: String? = nodeId
        while (id != null) {
            val node = tree.nodes[id] ?: break
            tags += node.tag
            id = node.parentId
        }
        return tags.asReversed()
    }

    fun engine(at: String = head): GameState {
        val play = tree.stateAt(at)!!
        return EngineState.from(play, definitions, null, EngineLog.from(tags(at), play, definitions))
    }

    fun call(at: String = head): EngineCall {
        val play = tree.stateAt(at)!!
        return EngineCall(play, definitions, null, EngineLog.from(tags(at), play, definitions), ::seed)
    }

    fun command(action: GameAction, from: String? = null) = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "$name/request/${requests++}"),
        GameScope(state.gameId, playId = state.id), action, CommandIds.uuidV5(AtwTable.NAMESPACE, "$name/stream"), "1",
        continuation = from?.let { Continuation(it, explicit = true) })

    /** [call]'s batch sent by [actor] (from [from]: a branch), as the server takes it; checked unless [check] is false.
     * Returns the server's answer (its tree is kept when accepted). */
    fun send(call: EngineCall, actor: String, from: String? = null, check: Boolean = true): GameplayTreeChange {
        if (call.changes.isEmpty()) return GameplayTreeChange()
        val command = command(GameAction.PlayBatch(listOf(PlayAction.PassAttention)), from)
        val plan = EngineBatch.plan(call, actor, command)
        val change = HistoryContinuation.change(tree, actor, command.copy(action = plan.action))
        val next = change.tree ?: return change
        tree = next
        if (check) {
            assertEquals(plan.state.board, state.board, "the plan's board is the server's")
            val problems = StateCompare.exact(call.working, engine())
            if (problems.isNotEmpty()) fail("Rebuilt from the tree differs:\n" + problems.take(8).joinToString("\n"))
        }
        return change
    }

    /** One call made by [body] on the current head, sent by [actor]; fails when the server refuses it. */
    fun run(actor: String, body: (EngineCall) -> Unit): EngineCall = call().also { c ->
        body(c)
        val change = send(c, actor)
        if (c.changes.isNotEmpty() && change.tree == null) fail("the server refused the batch: ${change.reason}")
    }

    /** A history control or an ordinary event by [actor]; the tree is kept when accepted. */
    fun control(actor: String, action: GameAction, from: String? = null): GameplayTreeChange =
        HistoryContinuation.change(tree, actor, command(action, from)).also { change -> change.tree?.let { tree = it } }

    fun manual(actor: String, action: PlayAction, from: String? = null): GameplayTreeChange = control(actor, GameAction.Play(action), from)
}

/** The paths where two boards differ, as JSON (for messages); empty when equal. */
internal fun boardDiff(a: BoardState, b: BoardState, limit: Int = 8): String {
    val out = mutableListOf<String>()
    fun walk(path: String, x: JsonElement?, y: JsonElement?) {
        if (out.size >= limit || x == y) return
        if (x is JsonObject && y is JsonObject) for (k in (x.keys + y.keys)) walk("$path.$k", x[k], y[k])
        else if (x is JsonArray && y is JsonArray && x.size == y.size) for (i in x.indices) walk("$path[$i]", x[i], y[i])
        else out += "$path: ${x.toString().take(300)} -> ${y.toString().take(300)}"
    }
    walk("", GameWire.json.encodeToJsonElement(BoardState.serializer(), a), GameWire.json.encodeToJsonElement(BoardState.serializer(), b))
    return out.joinToString("\n")
}
