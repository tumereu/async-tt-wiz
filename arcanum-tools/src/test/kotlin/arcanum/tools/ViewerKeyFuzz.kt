@file:OptIn(ArcanumTestSetup::class)

package arcanum.tools

import arcanum.api.CardDefinition
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.RandomKind
import arcanum.engine.input.Input
import arcanum.engine.state.ArcanumTestSetup
import arcanum.engine.state.CardId
import arcanum.engine.state.ControlSince
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.setup
import arcanum.engine.state.start
import arcanum.engine.turn.LoopKey
import arcanum.parser.OracleParser
import kotlin.random.Random
import kotlin.system.exitProcess

/**
 * The viewer key's fuzz over the supported sets (first turn during mulligans §7.4; a set worker's checklist item), from the
 * pinned MTGJSON file like SetSupport (never committed): seeded random turns with each set's fully supported cards on twin
 * boards that differ only in the opponent's hidden cards and their number (Bob's hand and library). Alice acts at random,
 * Bob only passes. Whenever Alice's keys ([Host.loopKey] with her as the viewer) of the twins are equal, what she may see must
 * be equal too: the objects [Host.visible] gives her, by place, and their characteristics in [Engine.view]. A difference is a
 * value the key leaves out that she could see: printed with the cards on the board, exit 1.
 * Args: [set codes, comma-separated; default every supported set] [games per set, default 20].
 */
fun main(args: Array<String>) {
    val supported = supportedSets()
    val codes = args.getOrNull(0)?.split(',')?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() }.orEmpty().ifEmpty { supported.keys.toList() }
    val games = args.getOrNull(1)?.toIntOrNull() ?: 20
    var failed = 0
    var compared = 0
    forEachPinnedSet(codes) { code, records ->
        val cards = setCards(records).mapNotNull { card ->
            runCatching {
                val printed = card.faces.map(::oracleCard)
                OracleParser.parseCard(OracleParser.layout(card.layout, printed) ?: return@runCatching null, printed)
            }.getOrNull()?.takeIf { arcanum.engine.CardSupport.manualParts(it).isEmpty() }
        }
        if (cards.size < 4) { println("$code: too few supported cards (${cards.size})"); return@forEachPinnedSet }
        repeat(games) { n ->
            val result = runCatching { ViewerKeyGame(code, cards, n.toLong()).play() }
                .getOrElse { e -> ViewerKeyGame.Outcome(0, "the game stopped on ${e::class.simpleName}: ${e.message?.take(160)}", fault = false) }
            compared += result.compared
            if (result.fault) { failed++; println("$code game $n: ${result.text}") }
        }
        println("$code: ${cards.size} cards, $games games")
    }
    println("Viewer key fuzz: $compared equal keys compared, $failed games where Alice could see a difference")
    if (failed > 0) exitProcess(1)
}

/** One seeded game on twin boards ([main]). */
private class ViewerKeyGame(val code: String, val cards: List<CardDefinition>, seed: Long) {
    class Outcome(val compared: Int, val text: String, val fault: Boolean)

    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private val rnd = Random(seed * 7919 + code.hashCode())
    private var next = 0

    private fun board(): GameState = GameState.start(listOf(alice, bob)).also { s ->
        s.setup.turn = s.turn.copy(turnStarted = mapOf(alice to 1, bob to 0))
        s.players.indices.forEach { i -> s.setup.players[i] = s.players[i].copy(library = emptyList()) }
    }

    private fun GameState.put(owner: PlayerId, definition: CardDefinition, zone: Zone, tag: String): ObjectId {
        val id = ObjectId.Card(CardId("${owner.value}.$tag${++next}"), 0)
        val on = zone == Zone.Battlefield
        with(setup) {
            val t = ++timestampCounter
            objects[id] = GameObject(id = id, owner = owner, controller = if (on) owner else null, zone = zone, definition = definition,
                timestamp = t, controlSince = if (on) ControlSince(owner, -1) else null)
            val p = players.indexOfFirst { it.id == owner }
            when (zone) {
                Zone.Battlefield -> battlefield.add(id)
                Zone.Hand -> players[p] = players[p].copy(hand = players[p].hand + id)
                else -> players[p] = players[p].copy(library = players[p].library + id)
            }
        }
        return id
    }

    /** The twins: the same board for Alice; Bob's hand and library of other cards, one more in hand on the second. */
    fun play(): Outcome {
        val a = board()
        val b = board()
        val shared = List(4) { cards.random(rnd) } to List(5) { cards.random(rnd) }
        val library = List(10) { cards.random(rnd) }
        val bobsField = List(4) { cards.random(rnd) }
        for (s in listOf(a, b)) {
            next = 0
            shared.first.forEach { s.put(alice, it, Zone.Battlefield, "b") }
            shared.second.forEach { s.put(alice, it, Zone.Hand, "h") }
            library.forEach { s.put(alice, it, Zone.Library, "l") }
            bobsField.forEach { s.put(bob, it, Zone.Battlefield, "b") }
        }
        repeat(5) { a.put(bob, cards.random(rnd), Zone.Hand, "x") }
        repeat(6) { b.put(bob, cards.random(rnd), Zone.Hand, "y") }
        repeat(10) { a.put(bob, cards.random(rnd), Zone.Library, "x"); b.put(bob, cards.random(rnd), Zone.Library, "y") }
        var compared = 0
        repeat(STEPS) { step ->
            if (Engine.result(a) != null || Engine.result(b) != null) return Outcome(compared, "over", false)
            val keyA = Host.loopKey(a, alice)
            val keyB = Host.loopKey(b, alice)
            if (keyA != keyB) return Outcome(compared, "keys differ at step $step", false)
            compared++
            differs(a, keyA, b, keyB)?.let { return Outcome(compared, "step $step: equal keys but $it; board ${names(a)}", true) }
            val d = a.pending?.decision
            val (inputA, inputB) = when {
                d is Decision.Random -> Input.Answer(d.id, random(d.what)) to Input.Answer(b.pending!!.decision.id, random((b.pending!!.decision as Decision.Random).what))
                d != null && Host.decider(a, d.player) == alice -> {
                    val answer = Engine.options(a, alice).filterIsInstance<Input.Answer>().randomOrNull(rnd) ?: return Outcome(compared, "no answer", false)
                    answer to (carry(answer, keyA, d.id.value, keyB, b.pending?.decision?.id?.value) ?: return Outcome(compared, "no place", false))
                }
                d != null -> return Outcome(compared, "Bob's decision", false)
                a.priority.holder == bob -> Input.PassPriority(bob) to Input.PassPriority(bob)
                a.priority.holder == alice -> {
                    val listed = Engine.options(a, alice)
                    val doing = listed.filterNot { it is Input.PassPriority }
                    val input = if (doing.isNotEmpty() && rnd.nextInt(3) > 0) doing.random(rnd) else listed.random(rnd)
                    input to (carry(input, keyA, null, keyB, null) ?: return Outcome(compared, "no place", false))
                }
                else -> {
                    val input = (Engine.options(a, alice).filterIsInstance<Input.DeclareAttackers>() +
                        Engine.options(a, bob).filterIsInstance<Input.DeclareBlockers>().filter { it.blocks.isEmpty() }).randomOrNull(rnd)
                        ?: return Outcome(compared, "nothing to declare", false)
                    input to (carry(input, keyA, null, keyB, null) ?: return Outcome(compared, "no place", false))
                }
            }
            val outA = runCatching { Engine.perform(a, inputA) }.getOrElse { return Outcome(compared, "refused", false) }
            val outB = runCatching { Engine.perform(b, inputB) }.getOrElse { return Outcome(compared, "refused on the twin", false) }
            outA.changes.forEach(a::apply)
            outB.changes.forEach(b::apply)
        }
        return Outcome(compared, "done", false)
    }

    /** What Alice may see that differs between [a] and [b] (by place), or null. */
    private fun differs(a: GameState, keyA: LoopKey, b: GameState, keyB: LoopKey): String? {
        val seenA = Host.visible(a, alice).mapNotNull { keyA.place(it)?.let { p -> p to it } }.toMap()
        val seenB = Host.visible(b, alice).mapNotNull { keyB.place(it)?.let { p -> p to it } }.toMap()
        if (seenA.keys != seenB.keys) return "she may see other objects (${seenA.keys - seenB.keys} / ${seenB.keys - seenA.keys})"
        val viewA = Engine.view(a)
        val viewB = Engine.view(b)
        for ((place, id) in seenA) {
            val x = viewA.objects[id]?.characteristics?.let(::seen)
            val y = viewB.objects[seenB.getValue(place)]?.characteristics?.let(::seen)
            if (x != y) return "$place's characteristics differ: $x / $y"
        }
        return null
    }

    /** [c] as the viewer could tell it: without timestamps (a counter, which the other player's hidden moves shift). */
    private fun seen(c: arcanum.engine.view.Characteristics): String {
        val json = kotlinx.serialization.json.Json
        fun strip(e: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement = when (e) {
            // Effect ids are timestamps too (the key names them by place)
            is kotlinx.serialization.json.JsonObject -> kotlinx.serialization.json.JsonObject(e.filterKeys { it != "timestamp" }
                .mapValues { (k, v) -> if (k == "effect" && v is kotlinx.serialization.json.JsonPrimitive) kotlinx.serialization.json.JsonPrimitive("effect") else strip(v) })
            is kotlinx.serialization.json.JsonArray -> kotlinx.serialization.json.JsonArray(e.map(::strip))
            else -> e
        }
        return strip(json.encodeToJsonElement(arcanum.engine.view.Characteristics.serializer(), c)).toString()
    }

    private fun names(s: GameState) = s.objects.values.filter { it.zone == Zone.Battlefield }.map { it.definition.faces.first().name }

    private fun random(what: RandomKind): Answer = when (what) {
        RandomKind.CoinFlip -> Answer.CoinFlip(true)
        is RandomKind.Die -> Answer.DieRolls(List(what.count) { 1 })
        is RandomKind.Objects -> Answer.RandomObjects(what.among.take(what.count))
    }

    /** As the host's carry: each object an input names by the one in the same place of the twin. */
    private fun carry(input: Input, keyA: LoopKey, decisionA: String?, keyB: LoopKey, decisionB: String?): Input? {
        var missing = false
        val json = kotlinx.serialization.json.Json
        fun walk(e: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonElement = when (e) {
            is kotlinx.serialization.json.JsonObject -> kotlinx.serialization.json.JsonObject(e.mapValues { walk(it.value) })
            is kotlinx.serialization.json.JsonArray -> kotlinx.serialization.json.JsonArray(e.map(::walk))
            is kotlinx.serialization.json.JsonPrimitive -> when {
                !e.isString -> e
                decisionA != null && e.content == decisionA -> kotlinx.serialization.json.JsonPrimitive(decisionB)
                e.content.startsWith("c:") || e.content.startsWith("s:") -> {
                    val there = keyA.place(json.decodeFromJsonElement(ObjectId.serializer(), e))?.let(keyB::objectAt)
                    if (there == null) { missing = true; e } else json.encodeToJsonElement(ObjectId.serializer(), there)
                }
                else -> e
            }
        }
        return json.decodeFromJsonElement(Input.serializer(), walk(json.encodeToJsonElement(Input.serializer(), input))).takeIf { !missing }
    }

    companion object {
        private const val STEPS = 40
    }
}
