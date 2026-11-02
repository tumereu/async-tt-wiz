package org.asyncttwiz.automatum

import arcanum.api.CardDefinition
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.CommandIds
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameCommand
import org.asyncttwiz.protocol.GameScope
import org.asyncttwiz.protocol.GameplayHistory
import org.asyncttwiz.protocol.GameplayMutation
import org.asyncttwiz.protocol.GameplayRules
import org.asyncttwiz.protocol.MtgTag
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayBatches
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.RandomV1
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * Drives engine flows through ATW (the I0 acceptance check, integration spec §3): every call builds the engine state from
 * the ATW board, performs, maps the changes to one ATW batch, applies it member by member with ATW's reducer as the
 * server does, rebuilds the engine state from the new board, and checks it against the engine's own result (exactly with
 * full information; what each player can see on a viewer's board), and that undoing the batch gives the board back.
 */
internal class RoundTrip(var state: PlayState, val definitions: Map<String, CardDefinition>, seed: Long = 1) {
    private val random = Random(seed)
    private var requests = 0
    /** The history's tags so far, in order (the engine's event log, [EngineLog]). */
    val tags = mutableListOf<MtgTag?>()
    /** Each batch's mutations, for undo. */
    val batches = mutableListOf<List<GameplayMutation>>()
    var largestBatch = 0
        private set
    /** The largest tag data (UTF-8 bytes) a member carried. */
    var largestTag = 0
        private set
    var lastPlan: BatchPlan? = null
        private set
    var lastCall: EngineCall? = null
        private set

    fun seed(): String = RandomV1.encodeSeed(Random(random.nextLong()).nextBytes(32))

    /** The engine state of the board as [viewer] sees it. */
    fun engine(viewer: String? = null): GameState = EngineState.from(state, definitions, viewer, EngineLog.from(tags, state, definitions))

    /** Performs [input] as [actor] in [viewer]'s view: one engine call, one ATW batch, checked. */
    fun perform(input: Input, actor: String, viewer: String? = null): EngineCall {
        val call = EngineCall(state, definitions, viewer, EngineLog.from(tags, state, definitions), ::seed)
        call.perform(input)
        apply(call, actor)
        return call
    }

    /** Applies [call]'s changes as one ATW batch and checks the round trip. */
    fun apply(call: EngineCall, actor: String) {
        lastCall = call
        if (call.changes.isEmpty()) return
        val command = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "request/${requests++}"),
            GameScope(state.gameId, playId = state.id), GameAction.PlayBatch(listOf(PlayAction.PassAttention)),
            CommandIds.uuidV5(AtwTable.NAMESPACE, "stream"), "1")
        val plan = EngineBatch.plan(call, actor, command)
        lastPlan = plan
        largestBatch = maxOf(largestBatch, plan.actions.size)
        largestTag = maxOf(largestTag, plan.tags.maxOfOrNull { it?.data?.encodeToByteArray()?.size ?: 0 } ?: 0)
        // As the server: each member through the reducer, with its tag
        val batch = command.copy(action = plan.action)
        var next = state
        val mutations = mutableListOf<GameplayMutation>()
        for ((i, action) in plan.actions.withIndex()) {
            val change = GameplayRules.change(next, actor, PlayBatches.member(batch, i, action))
            assertEquals("accepted", change.outcome, "member $i $action: ${change.reason}")
            next = change.state!!
            mutations += change.mutation!!
        }
        assertEquals(plan.state.board, next.board, "the plan's board is the reducer's")
        // Undo of the batch gives the board back (D59, SYS-010)
        var undone = next
        for (m in mutations.reversed()) undone = undone.copy(board = assertNotNull(GameplayHistory.reverse(undone, m), "undo of $m"))
        assertEquals(state.board, undone.board, "undoing the batch restores the board")
        val previous = state
        state = next
        tags += mutations.map { it.tag }
        batches += mutations
        val rebuilt = engine(call.viewer)
        val problems = if (call.viewer == null) StateCompare.exact(call.working, rebuilt) else StateCompare.visible(call, rebuilt)
        if (problems.isNotEmpty()) fail("Round trip differs after ${call.changes.size} changes (${plan.actions.size} actions):\n" +
            problems.take(12).joinToString("\n") + "\nchanges: ${call.changes.map { it::class.simpleName }}\nactions: " +
            plan.actions.joinToString("\n  ") { it.toString().take(200) } + "\nbefore: ${previous.board.turn}")
    }

    // ---- Choosing inputs as a player would

    fun name(o: GameObject?): String? = o?.definition?.faces?.first()?.name

    /** The object named [name] in [zone] (any zone when null). */
    fun obj(name: String, zone: Zone? = null, viewer: String? = null): ObjectId = engine(viewer).objects.values
        .firstOrNull { name(it) == name && (zone == null || it.zone == zone) }?.id ?: fail("No $name in ${zone ?: "any zone"}")

    fun options(player: String, viewer: String? = null): List<Input> = Engine.options(engine(viewer), PlayerId(player))

    fun cast(player: String, name: String, viewer: String? = null): EngineCall {
        val state = engine(viewer)
        val input = options(player, viewer).filterIsInstance<Input.Cast>().firstOrNull { name(state.obj(it.card)) == name }
            ?: fail("$player can't cast $name: ${options(player, viewer)}")
        return perform(input, player, viewer)
    }

    fun play(player: String, name: String): EngineCall {
        val state = engine()
        val input = options(player).filterIsInstance<Input.PlayLand>().firstOrNull { name(state.obj(it.card)) == name }
            ?: fail("$player can't play $name")
        return perform(input, player)
    }

    fun activate(player: String, name: String): EngineCall {
        val state = engine()
        val input = options(player).filterIsInstance<Input.Activate>().firstOrNull { name(state.obj(it.source)) == name }
            ?: fail("$player can't activate $name: ${options(player)}")
        return perform(input, player)
    }

    val decision: Decision? get() = engine().pending?.decision

    /** Answers the open decision with the first listed answer [pick] accepts (default: the first). */
    fun answer(viewer: String? = null, pick: (Answer) -> Boolean = { true }): EngineCall {
        val d = assertNotNull(engine(viewer).pending?.decision, "no open decision")
        val answers = options(d.player.value, viewer).filterIsInstance<Input.Answer>()
        val input = answers.firstOrNull { pick(it.answer) } ?: fail("no answer to $d among $answers")
        return perform(input, d.player.value, viewer)
    }

    /** Answers targets with [target]. */
    fun target(target: TargetRef) = answer { it is Answer.Targets && it.chosen == listOf(target) }

    /** Answers the decisions of a cast or activation (mana first suggested) until none is open or [until] holds. */
    fun settle(until: (Decision) -> Boolean = { false }) {
        while (true) {
            val d = decision ?: return
            if (until(d)) return
            when (d) {
                is Decision.ManaPayment -> perform(Input.Answer(d.id, Answer.ManaPayment(d.suggestion)), d.player.value)
                else -> answer()
            }
        }
    }

    fun pass(player: String, viewer: String? = null) = perform(Input.PassPriority(PlayerId(player)), player, viewer)

    /** Both pass until the Stack is empty (or a decision opens). */
    fun resolveAll() {
        var guard = 20
        while (guard-- > 0) {
            val s = engine()
            if (s.pending != null) return
            val holder = s.priority.holder ?: return
            if (s.stack.isEmpty() && s.priority.passes == 0 && guard < 19) return
            pass(holder.value)
            if (engine().stack.isEmpty() && engine().pending == null) return
        }
    }
}

/** How a rebuilt engine state is compared with the engine's own result. */
internal object StateCompare {
    /**
     * Full information: equal, but for the documented differences — the timestamp counter (ATW's clock is the highest
     * stamp on the board, D34, never above the engine's), and the order of the Battlefield and Exile (one list each in the
     * engine, per player in ATW; rebuilt in timestamp order).
     */
    fun exact(expected: GameState, rebuilt: GameState): List<String> {
        val problems = mutableListOf<String>()
        if (rebuilt.timestampCounter > expected.timestampCounter)
            problems += "timestamp counter ${rebuilt.timestampCounter} > engine's ${expected.timestampCounter}"
        fun sorted(s: GameState, ids: List<ObjectId>) = ids.map { s.objOrThrow(it) }.sortedWith(Derived.order).map { it.id }
        fun check(what: String, a: Any?, b: Any?) { if (a != b) problems += "$what:\n    engine  $a\n    rebuilt $b" }
        for ((a, b) in expected.players.zip(rebuilt.players)) {
            check("player ${a.id}", a, b)
        }
        check("players", expected.players.map { it.id }, rebuilt.players.map { it.id })
        val ids = (expected.objects.keys + rebuilt.objects.keys)
        for (id in ids) check("object $id", expected.objects[id], rebuilt.objects[id])
        check("battlefield", sorted(expected, expected.battlefield), rebuilt.battlefield)
        check("exile", sorted(expected, expected.exile), rebuilt.exile)
        check("stack", expected.stack, rebuilt.stack)
        check("command", expected.command, rebuilt.command)
        check("stack items", expected.stackItems, rebuilt.stackItems)
        check("effects", expected.effects, rebuilt.effects)
        check("delayed triggers", expected.delayedTriggers, rebuilt.delayedTriggers)
        check("turn", expected.turn, rebuilt.turn)
        check("priority", expected.priority, rebuilt.priority)
        check("combat", expected.combat, rebuilt.combat)
        check("ledger", expected.ledger, rebuilt.ledger)
        check("log", expected.log, rebuilt.log)
        check("pending", expected.pending, rebuilt.pending)
        check("triggered", expected.triggered, rebuilt.triggered)
        check("skip first draw", expected.skipFirstDraw, rebuilt.skipFirstDraw)
        check("viewer", expected.viewer, rebuilt.viewer)
        return problems
    }

    /**
     * A viewer's board: what that player can see is the same (D60) — turn, priority, life, counters, pools, zone sizes,
     * every public card by its ATW card and name, permanents' status, the Stack, the open decision. A viewer's state names
     * the cards a shuffle hid by new ids (D60) that ATW doesn't keep, and may remember cards it saw (a card put into a
     * library known) that ATW's view doesn't show: those are the documented differences.
     */
    fun visible(call: EngineCall, rebuilt: GameState): List<String> {
        val expected = call.working
        fun card(o: ObjectId) = when (o) {
            is ObjectId.Card -> call.hidden[o.card] ?: call.ids.atw(o.card)
            is ObjectId.StackItem -> o.toString()
        }
        fun lines(s: GameState, atw: (ObjectId) -> String): List<String> = buildList {
            val t = s.turn
            add("turn ${t.number} ${t.active} ${t.step} lands ${t.landsPlayed.toSortedMap(compareBy { it.value })} " +
                "began ${t.turnStarted.toSortedMap(compareBy { it.value })} extra ${t.extraTurns} priority ${s.priority} " +
                "pending ${s.pending?.decision?.let { it::class.simpleName + " " + it.player }}")
            add("combat ${s.combat?.attackers?.map { atw(it.id) }} ${s.combat?.blockers?.map { atw(it.id) }}")
            s.players.forEach { p ->
                add("${p.id} life ${p.life} counters ${p.counters} pool ${p.manaPool} lost ${p.lost} hand ${p.hand.size} " +
                    "library ${p.library.size} graveyard ${p.graveyard.map(atw)}")
            }
            s.objects.values.filter { it.zone != Zone.Library && it.zone != Zone.Hand }.map { o ->
                "${atw(o.id)} ${o.zone} ${o.definition?.faces?.first()?.name} ${o.controller} tapped ${o.tapped} down ${o.faceDown} " +
                    "counters ${o.counters} damage ${o.damage} attached ${o.attachedTo}"
            }.sorted().forEach(::add)
            add("stack ${s.stack.map { id -> s.stackItems[id]?.let { "${it.kind}/${it.controller}/${it.source?.let(atw)}" } ?: atw(id) }}")
        }
        val a = lines(expected, ::card)
        val b = lines(rebuilt) { o -> (o as? ObjectId.Card)?.card?.value ?: o.toString() }
        return a.zip(b).filter { (x, y) -> x != y }.map { (x, y) -> "  engine  $x\n  rebuilt $y" } +
            listOfNotNull("${a.size} lines vs ${b.size}".takeIf { a.size != b.size })
    }
}
