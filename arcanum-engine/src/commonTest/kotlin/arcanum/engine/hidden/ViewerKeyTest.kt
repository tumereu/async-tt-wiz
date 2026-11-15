package arcanum.engine.hidden

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.RandomKind
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.setup.keep
import arcanum.engine.setup.mulligan
import arcanum.engine.setup.startGame
import arcanum.engine.state.GameState
import arcanum.engine.state.LoggedChange
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.turn.LoopKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * [Host.loopKey] with a viewer: the position as one player could tell it. Equal however the other player's hidden cards
 * moved (which shifts every counter: object ids, timestamps, the input count), and different whenever anything the viewer
 * may see differs.
 */
class ViewerKeyTest {
    // Hidden information (spec §7.0): two bases that differ only by extra moves among Bob's hidden cards (his hand swapped
    // with cards of his library, his library reordered, more inputs counted: every counter shifted) give Alice equal keys
    // all the way through the same seeded random play, as long as nothing shows her one of Bob's cards
    @Test
    fun countersShiftedByTheOpponentsHiddenCardsDontCount() {
        var compared = 0
        repeat(GAMES) { compared += game(SEED + it) }
        assertTrue(compared > GAMES * 5, "the games compared keys at $compared points")
    }

    private fun game(seed: Long): Int {
        var steps = 0
        scenario(seed = seed) {
            val rnd = Random(seed)
            val pool = Cards.cubeNames
            players.forEach { p ->
                p.battlefield(*(List(4) { BASICS.random(rnd) } + List(2) { pool.random(rnd) }).toTypedArray())
                p.hand(*List(5) { pool.random(rnd) }.toTypedArray())
                p.library(*List(12) { (pool + BASICS).random(rnd) }.toTypedArray())
            }
            val a = state.deepCopy()
            val b = shifted(a, bob.id)
            assertEquals(Host.loopKey(a, alice.id), Host.loopKey(b, alice.id), "Game $seed: the bases differ: ${differs(a, b, alice.id)}")
            steps = play(seed, a, b, alice.id, bob.id, rnd)
        }
        return steps
    }

    // CR 103.5 (with StartOptions.simultaneousMulligans), spec §7.0: Bob's mulligans (his hand shuffled back, new hands drawn,
    // cards put on the bottom) don't change Alice's key through her first turn and his draw (how many cards he holds is
    // hidden too)
    @Test
    fun theOpponentsMulligansDontChangeTheKey() {
        val swamps = arcanum.engine.setup.cards(listOf("Swamp", "Undead Soothsayer"), "Swamp", "Undead Soothsayer")
        val mountains = arcanum.engine.setup.cards(listOf("Mountain", "Glint-Tusk Corsair"), "Mountain", "Glint-Tusk Corsair")
        fun keys(mulligans: Int): List<Pair<GameState, LoopKey>> {
            val out = mutableListOf<Pair<GameState, LoopKey>>()
            scenario(start = arcanum.engine.setup.table(swamps, mountains)) {
                startGame(arcanum.engine.setup.StartOptions(startingPlayer = alice.id, simultaneousMulligans = true, freeFirstMulligan = true))
                keep(alice)
                repeat(mulligans) {
                    mulligan(bob)
                    (pending as? Decision.ChooseObjects)?.let { d -> answer(Answer.Objects(state.player(bob.id).hand.takeLast(d.min))) }
                }
                keep(bob)
                while (state.turn.number < 2 || state.turn.step != arcanum.api.TurnStep.PrecombatMain) {
                    out += state.deepCopy().let { it to Host.loopKey(it, alice.id) }
                    if (state.priority.holder != null) perform(Input.PassPriority(state.priority.holder!!))
                    else perform(Engine.options(state, state.turn.active).first { it is Input.DeclareAttackers })
                }
            }
            return out
        }
        val kept = keys(0)
        for (n in 1..2) {
            val other = keys(n)
            assertEquals(kept.size, other.size)
            kept.zip(other).forEachIndexed { i, (a, b) ->
                assertEquals(a.second, b.second, "$n mulligans, point $i: ${differs(a.first, b.first, PlayerId("alice"))}")
            }
        }
    }

    /** [a] with [other]'s hand put on the bottom of their library and as many cards drawn from its top, the library then
     * reordered, and five more inputs counted. */
    private fun shifted(a: GameState, other: PlayerId): GameState {
        val b = a.deepCopy()
        val hand = b.player(other).hand.toList()
        b.apply(Changes.move(b, hand, Zone.Library, Changes.Position.Bottom))
        b.apply(Changes.move(b, b.player(other).library.take(hand.size), Zone.Hand))
        val library = b.player(other).library
        b.apply(Change.Reorder(Zone.Library, other, library, library.reversed()))
        b.apply(Change.InputCounted(b.inputCount, b.inputCount + 5))
        // Made before this turn (another player's mulligans are made in the opening): not in this turn's log
        b._log = a.log.toMutableList()
        return b
    }

    /** Plays [a] and [b] side by side: [viewer] makes random inputs on [a], each made on [b] in the same places; [other]
     * only passes and declares no blocks. Stops at the first point that would show [viewer] one of [other]'s cards (they
     * differ between the two), a decision of [other]'s, or the game's end. Returns the points compared. */
    private fun play(seed: Long, a: GameState, b: GameState, viewer: PlayerId, other: PlayerId, rnd: Random): Int {
        repeat(STEPS) { step ->
            if (Engine.result(a) != null) return step
            val keyA = Host.loopKey(a, viewer)
            val keyB = Host.loopKey(b, viewer)
            if (keyA != keyB) fail("Game $seed, step $step: the keys differ at ${a.pending?.decision ?: a.turn}: ${differs(a, b, viewer)}")
            val d = a.pending?.decision
            val inputA: Input
            val inputB: Input
            when {
                d is Decision.Random -> {
                    inputA = Input.Answer(d.id, random(d.what))
                    inputB = Input.Answer(b.pending!!.decision.id, random((b.pending!!.decision as Decision.Random).what))
                }
                d != null && Host.decider(a, d.player) == viewer -> {
                    val answers = Engine.options(a, viewer).filterIsInstance<Input.Answer>()
                    if (answers.isEmpty()) return step
                    inputA = answers.random(rnd)
                    inputB = carry(inputA, a, keyA, b, keyB) ?: return step
                }
                d != null -> return step
                a.priority.holder == other -> { inputA = Input.PassPriority(other); inputB = inputA }
                a.priority.holder == viewer -> {
                    val listed = Engine.options(a, viewer)
                    val doing = listed.filterNot { it is Input.PassPriority }
                    inputA = if (doing.isNotEmpty() && rnd.nextInt(3) > 0) doing.random(rnd) else listed.random(rnd)
                    inputB = carry(inputA, a, keyA, b, keyB) ?: return step
                }
                else -> {
                    val listed = Engine.options(a, viewer).filterIsInstance<Input.DeclareAttackers>() +
                        Engine.options(a, other).filterIsInstance<Input.DeclareBlockers>().filter { it.blocks.isEmpty() }
                    inputA = listed.randomOrNull(rnd) ?: return step
                    inputB = carry(inputA, a, keyA, b, keyB) ?: return step
                }
            }
            val outA = Engine.perform(a, inputA)
            val outB = try { Engine.perform(b, inputB) } catch (e: Exception) {
                fail("Game $seed, step $step: $inputA, made in the same places as $inputB, was refused on the shifted base: ${e.message}")
            }
            // A card of the other player's shown: the identities differ between the bases
            val owned = { s: GameState, cards: Set<arcanum.engine.state.CardId> -> cards.any { c -> s.current(c)?.owner == other } }
            if (owned(a, Host.revealed(a, outA.changes, viewer)) || owned(b, Host.revealed(b, outB.changes, viewer))) return step
            outA.changes.forEach(a::apply)
            outB.changes.forEach(b::apply)
        }
        return STEPS
    }

    /** Where [viewer]'s canonical texts of [a] and [b] first differ. */
    private fun differs(a: GameState, b: GameState, viewer: PlayerId): String {
        val x = arcanum.engine.turn.Loops.viewerText(a, viewer).first
        val y = arcanum.engine.turn.Loops.viewerText(b, viewer).first
        val at = x.indices.firstOrNull { it >= y.length || x[it] != y[it] } ?: x.length
        return "\n…${x.substring((at - 300).coerceAtLeast(0), (at + 200).coerceAtMost(x.length))}\n…${y.substring((at - 300).coerceAtLeast(0), (at + 200).coerceAtMost(y.length))}"
    }

    /** A random answer that takes the cards in the order asked (the same places on both bases). */
    private fun random(what: RandomKind): Answer = when (what) {
        RandomKind.CoinFlip -> Answer.CoinFlip(true)
        is RandomKind.Die -> Answer.DieRolls(List(what.count) { 1 })
        is RandomKind.Objects -> Answer.RandomObjects(what.among.take(what.count))
    }

    /** [input] made on [a] (key [keyA]) as the same input on [b] (key [keyB]): each object it names by the one in the
     * same place there, the open decision by the open decision; null when something it names has no place there. */
    private fun carry(input: Input, a: GameState, keyA: LoopKey, b: GameState, keyB: LoopKey): Input? {
        val decisionA = a.pending?.decision?.id?.value
        val decisionB = b.pending?.decision?.id?.value
        var missing = false
        fun walk(e: JsonElement): JsonElement = when (e) {
            is JsonObject -> JsonObject(e.mapValues { walk(it.value) })
            is JsonArray -> JsonArray(e.map(::walk))
            is JsonPrimitive -> when {
                !e.isString -> e
                e.content == decisionA -> JsonPrimitive(decisionB)
                e.content.startsWith("c:") || e.content.startsWith("s:") -> {
                    val id = Json.decodeFromJsonElement(ObjectId.serializer(), e)
                    val there = keyA.place(id)?.let(keyB::objectAt)
                    if (there == null) { missing = true; e } else Json.encodeToJsonElement(ObjectId.serializer(), there)
                }
                else -> e
            }
        }
        val carried = Json.decodeFromJsonElement(Input.serializer(), walk(Json.encodeToJsonElement(Input.serializer(), input)))
        return carried.takeIf { !missing }
    }

    // Hidden information (spec §7.0): what every kind of change does to what the viewer may see shows in their key, not
    // only its entry in the log: for each change made in seeded random games on something Alice may see, her key of the
    // state after it differs from her key of the state before it with only that log entry added; counters
    // (the input count, timestamps) and what only shows (a look) are no position
    @Test
    fun everyKindOfChangeShowsInTheKey() {
        val seen = mutableSetOf<String>()
        repeat(SENSITIVITY_GAMES) { sensitivity(SEED + 500 + it, seen) }
        seen += samples()
        val kinds = Change.serializer().descriptor.getElementDescriptor(1).let { d -> (0 until d.elementsCount).map(d::getElementName) }.toSet()
        val missing = kinds - seen - NO_POSITION
        assertTrue(missing.isEmpty(), "No change of these kinds was tried (add a sample): $missing")
    }

    /** The kinds of change the random games of cube cards don't make, each made by hand on a board where Alice sees it,
     * and the kinds tried. */
    private fun samples(): Set<String> {
        val kinds = mutableSetOf<String>()
        scenario {
            alice.battlefield("Island", "Island", "Glint-Tusk Corsair")
            alice.graveyard("Swamp", "Mountain")
            alice.library("Forest", "Plains")
            bob.battlefield("Mountain")
            val effect = effect(alice, arcanum.api.Mechanic.CantCast(arcanum.api.ObjectFilter.Any), affects = arcanum.api.Affected.Players(arcanum.api.Players.You))
            val s = state.deepCopy()
            val creature = s.battlefield.first { s.obj(it)?.definition?.faces?.first()?.name == "Glint-Tusk Corsair" }
            val gy = s.player(alice.id).graveyard
            val trigger = arcanum.engine.state.DelayedTrigger(arcanum.engine.state.EffectId("D99"),
                arcanum.api.Trigger.On(arcanum.api.EventPattern.StepBegins(arcanum.api.TurnStep.Upkeep, arcanum.api.PlayerFilter.You)),
                arcanum.api.Instructions(steps = emptyList()), source = creature, controller = alice.id, timestamp = 99, createdTurn = 1)
            val withTrigger = s.deepCopy().also { it.apply(Change.DelayedTriggerAdded(trigger)) }
            val made = s.effects.getValue(effect)
            val samples = listOf(
                s to Change.Counters(arcanum.engine.state.TargetRef.Object(creature), arcanum.api.CounterKind.PowerToughness(1, 1), null,
                    arcanum.engine.state.CounterEntry(arcanum.api.CounterKind.PowerToughness(1, 1), 1, 50)),
                s to Change.Lost(bob.id, null, arcanum.engine.setup.LossReason.ZeroLife),
                s to Change.EmptyLibraryDraw(alice.id, false, true),
                s to Change.GameStatusChanged(null, arcanum.engine.state.GameStatus.DayNight(true)),
                s to Change.PlayerStatusChanged(alice.id, null, arcanum.engine.state.PlayerStatus.Speed(1)),
                s to Change.ObjectStatusChanged(creature, null, arcanum.engine.state.ObjectStatus.Goaded(listOf(bob.id))),
                s to Change.Memory(creature, "chosen", null, arcanum.engine.state.NamedValue.Players(listOf(bob.id))),
                s to Change.ControlStart(creature, s.obj(creature)!!.controlSince, arcanum.engine.state.ControlSince(alice.id, 1)),
                s to Change.Reorder(Zone.Graveyard, alice.id, gy, gy.reversed()),
                s to Change.EffectRemoved(made),
                s to Change.EffectUpdated(made, made.copy(controller = bob.id)),
                s to Change.DelayedTriggerAdded(trigger),
                withTrigger to Change.DelayedTriggerRemoved(trigger),
            )
            for ((before, change) in samples) kinds += shows(before, change, alice.id)
        }
        scenario {
            // A spell on the Stack, its controller changed (CR 110.2b)
            alice.battlefield("Island")
            alice.hand("Glance Ahead")
            alice.casts("Glance Ahead")
            val cast = state.deepCopy()
            val item = cast.stackItems.values.first()
            kinds += shows(cast, Change.StackItemUpdated(item, item.copy(controller = bob.id)), alice.id)
        }
        return kinds
    }

    /** [change] made on [before] shows in [viewer]'s key, beyond its log entry; its kind. */
    private fun shows(before: GameState, change: Change, viewer: PlayerId): String {
        val after = before.deepCopy().also { it.apply(change) }
        val logged = before.deepCopy().also { if (change.isEvent) it._log.add(LoggedChange(after.turn.number, change)) }
        assertNotEquals(Host.loopKey(logged, viewer), Host.loopKey(after, viewer), "$change didn't show in the key")
        return Json.encodeToJsonElement(Change.serializer(), change).let { (it as JsonObject)["type"]!!.toString().trim('"') }
    }

    private fun sensitivity(seed: Long, seen: MutableSet<String>) = scenario(seed = seed) {
        val rnd = Random(seed)
        val pool = Cards.cubeNames
        players.forEach { p ->
            p.battlefield(*(List(4) { BASICS.random(rnd) } + List(2) { pool.random(rnd) }).toTypedArray())
            p.hand(*List(5) { pool.random(rnd) }.toTypedArray())
            p.library(*List(12) { (pool + BASICS).random(rnd) }.toTypedArray())
        }
        val s = state.deepCopy()
        repeat(STEPS) { step ->
            if (Engine.result(s) != null) return@scenario
            val d = s.pending?.decision
            val listed = if (d is Decision.Random) listOf(Input.Answer(d.id, random(d.what)))
                else players.flatMap { Engine.options(s, it.id) }.distinct()
            if (listed.isEmpty()) return@scenario
            val doing = listed.filterNot { it is Input.PassPriority }
            val input = if (doing.isNotEmpty() && rnd.nextInt(3) > 0) doing.random(rnd) else listed.random(rnd)
            for (change in Engine.perform(s, input).changes) {
                val kind = Json.encodeToJsonElement(Change.serializer(), change).let { (it as JsonObject)["type"]!!.toString().trim('"') }
                val visible = Host.visible(s, alice.id)
                val named = named(change)
                val before = s.deepCopy()
                s.apply(change)
                if (kind in NO_POSITION) continue
                // Only what Alice may see (some object it names visible to her before or after it, or none named at all)
                if (named.isNotEmpty() && named.none { it in visible || it in Host.visible(s, alice.id) }) continue
                seen += kind
                val logged = before.deepCopy().also { if (change.isEvent) it._log.add(LoggedChange(s.turn.number, change)) }
                if (kind in LOG_ONLY) assertNotEquals(Host.loopKey(before, alice.id), Host.loopKey(s, alice.id), "Game $seed, step $step: $change")
                else assertNotEquals(Host.loopKey(logged, alice.id), Host.loopKey(s, alice.id), "Game $seed, step $step: $change didn't show in Alice's key")
            }
        }
    }

    /** The objects [change] names (its JSON's object ids). */
    private fun named(change: Change): Set<ObjectId> {
        val ids = mutableSetOf<ObjectId>()
        fun walk(e: JsonElement) {
            when (e) {
                // Not what caused it (a source Alice may see moving a card she may not)
                is JsonObject -> e.filterKeys { it != "by" }.forEach { (k, v) -> walk(JsonPrimitive(k)); walk(v) }
                is JsonArray -> e.forEach(::walk)
                is JsonPrimitive -> if (e.isString && (e.content.startsWith("c:") && e.content.lastIndexOf('#') > 2 || e.content.startsWith("s:")))
                    runCatching { Json.decodeFromJsonElement(ObjectId.serializer(), e) }.getOrNull()?.let(ids::add)
            }
        }
        walk(Json.encodeToJsonElement(Change.serializer(), change))
        return ids
    }

    companion object {
        private const val GAMES = 20
        private const val SENSITIVITY_GAMES = 30
        private const val STEPS = 40
        private const val SEED = 9_100L
        private val BASICS = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")
        /** Counters (the input count, a timestamp), a look (shown, never part of the state: the information rule counts it),
         * a shuffle (a library's order, which nobody may see) and which places in a library are known (of cards the viewer can't
         * see: a card they may look at shows by its place in the library itself). */
        private val NO_POSITION = setOf("inputs", "restamped", "shown", "shuffle", "library-places")
        /** Changes that are only their log entry (an event that happened, damage dealt). */
        private val LOG_ONLY = setOf("happened", "damage")
    }
}
