package org.asyncttwiz.server.game

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.Outcome
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.RandomKind
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.state.CardId
import arcanum.engine.state.ControlSince
import arcanum.engine.state.CounterEntry
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TurnState
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.TableState
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * I-D54 phase 1 spike: the Arcanum engine runs inside the server JVM, and what `Engine.perform` costs there for typical
 * inputs (whole games of themed decks of invented cube cards) and heavy ones (large boards, a board wipe, many
 * triggers), with the cost of applying the changes and of the JSON the server would store (events and checkpoints).
 * Prints timings, asserts none (the owner's rule: no timing tests). Runs only with `-Patw.engineSpike=true`:
 * `./scripts/gradle.sh :server:test -Patw.engineSpike=true --tests '*EngineOnServerSpikeTest*'`.
 */
class EngineOnServerSpikeTest {
    private val enabled get() = System.getProperty("atw.engineSpike") == "true"
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")

    /** Per input kind: microseconds of perform, apply and event JSON, and the event's compact JSON bytes. */
    private class Samples {
        val perform = mutableMapOf<String, MutableList<Long>>()
        val apply = mutableListOf<Long>()
        val encode = mutableListOf<Long>()
        val bytes = mutableListOf<Int>()
        val inlineBytes = mutableListOf<Int>()
        val changes = mutableListOf<Int>()
    }

    private fun kind(input: Input): String = when (input) {
        is Input.Answer -> "answer:" + input.answer::class.simpleName
        else -> input::class.simpleName ?: "?"
    }

    /** A random player and host on the public API only: random answers by the host, every other input from options. */
    private class Chooser(seed: Long) {
        val rnd = Random(seed)
        var inputsThisTurn = 0
        fun next(state: GameState): Input {
            val d = state.pending?.decision
            if (d is Decision.Random) return Input.Answer(d.id, random(d.what))
            check(d !is Decision.HostFill && d !is Decision.OtherView) { "full information has no host fills: $d" }
            if (state.turn.number == 0 && d == null) return Input.StartGame()
            val listed = state.players.flatMap { Engine.options(state, it.id) }.distinct()
            check(listed.isNotEmpty()) { "stuck at ${d ?: state.turn}" }
            if (d != null) return listed.random(rnd)
            val doing = listed.filterNot { it is Input.PassPriority || (it is Input.DeclareAttackers && it.attacks.isEmpty()) ||
                (it is Input.DeclareBlockers && it.blocks.isEmpty()) || it is Input.Concede }
            val passing = listed.filter { it !in doing && it !is Input.Concede }
            return if (inputsThisTurn < 60 && doing.isNotEmpty() && rnd.nextInt(3) > 0) doing.random(rnd)
            else passing.ifEmpty { listed.filter { it !is Input.Concede } }.random(rnd)
        }

        private fun random(kind: RandomKind): Answer = when (kind) {
            RandomKind.CoinFlip -> Answer.CoinFlip(rnd.nextBoolean())
            is RandomKind.Die -> Answer.DieRolls(List(kind.count) { rnd.nextInt(1, kind.sides + 1) })
            is RandomKind.Objects -> {
                check(!kind.newIds) { "full information keeps ids" }
                Answer.RandomObjects(kind.among.shuffled(rnd).take(kind.count))
            }
        }
    }

    private val inline = Json { encodeDefaults = false }

    /** Plays one whole game of [decks]; records each input's costs into [samples]; returns the final state. */
    private fun game(seed: Long, decks: Pair<List<String>, List<String>>, samples: Samples?): Pair<GameState, Int> {
        val seats = listOf(alice to decks.first, bob to decks.second).map { (p, names) ->
            Seat(p, names.mapIndexed { i, name -> DeckCard(CardId("${p.value}.$i"), Cards.definition(name)) })
        }
        val state = Engine.newGame(seats)
        val definitions = GameDefinitions.of(state)
        val codec = EngineJson(definitions)
        val chooser = Chooser(seed)
        var inputs = 0
        var turn = 0
        while (Engine.result(state) == null && state.turn.number < 60 && inputs < 6000) {
            val input = chooser.next(state)
            val t0 = System.nanoTime()
            val outcome = Engine.perform(state, input)
            val t1 = System.nanoTime()
            outcome.changes.forEach { state.apply(it) }
            val t2 = System.nanoTime()
            val compact = codec.encodeChanges(outcome.changes)
            val t3 = System.nanoTime()
            if (samples != null) {
                samples.perform.getOrPut(kind(input)) { mutableListOf() } += (t1 - t0) / 1000
                samples.apply += (t2 - t1) / 1000
                samples.encode += (t3 - t2) / 1000
                samples.bytes += compact.length
                samples.inlineBytes += inline.encodeToString(ListSerializer(Change.serializer()), outcome.changes).length
                samples.changes += outcome.changes.size
            }
            inputs++
            chooser.inputsThisTurn++
            if (state.turn.number != turn) { turn = state.turn.number; chooser.inputsThisTurn = 0 }
        }
        return state to inputs
    }

    private fun List<Long>.pct(p: Double): Long = sorted().let { if (it.isEmpty()) 0 else it[((it.size - 1) * p).toInt()] }
    private fun ms(us: Long) = "%.2f".format(us / 1000.0)

    @Test
    fun spike() {
        if (!enabled) return
        println("SPIKE JVM ${Runtime.version()} on ${System.getProperty("os.arch")}, ${Runtime.getRuntime().availableProcessors()} cores, " +
            "max heap ${Runtime.getRuntime().maxMemory() / (1 shl 20)} MiB")
        // A cold JVM: the first game after start (the engine interpreted until the JIT compiles its hot paths)
        val cold = Samples()
        game(99L, Decks.creatures to Decks.spells, cold).second.let { n ->
            val all = cold.perform.values.flatten()
            println("SPIKE cold JVM, first game ($n inputs): perform median ${ms(all.pct(0.5))} ms, p95 ${ms(all.pct(0.95))} ms, max ${ms(all.max())} ms")
        }
        // Warm up: the JIT compiles the engine's hot paths (the server's steady state).
        repeat(6) { game(100L + it, Decks.creatures to Decks.spells, null) }

        val samples = Samples()
        var inputs = 0
        var finalStates = mutableListOf<GameState>()
        val t0 = System.nanoTime()
        for ((i, pair) in Decks.pairs.withIndex()) repeat(3) { g ->
            val (state, n) = game(1000L + i * 10 + g, pair, samples)
            inputs += n; finalStates += state
        }
        val wall = (System.nanoTime() - t0) / 1e6
        println("SPIKE typical: ${finalStates.size} whole games, $inputs inputs, ${"%.0f".format(wall)} ms in all " +
            "(${"%.2f".format(wall / inputs)} ms per input incl. options for the random player)")
        val all = samples.perform.values.flatten()
        println("SPIKE perform, all inputs: median ${ms(all.pct(0.5))} ms, p95 ${ms(all.pct(0.95))} ms, p99 ${ms(all.pct(0.99))} ms, max ${ms(all.max())} ms")
        samples.perform.entries.sortedByDescending { it.value.size }.forEach { (k, v) ->
            println("SPIKE perform $k (${v.size}): median ${ms(v.pct(0.5))} ms, p95 ${ms(v.pct(0.95))} ms, max ${ms(v.max())} ms")
        }
        println("SPIKE apply changes: median ${ms(samples.apply.pct(0.5))} ms, p95 ${ms(samples.apply.pct(0.95))} ms, max ${ms(samples.apply.max())} ms; " +
            "changes per input median ${samples.changes.map { it.toLong() }.pct(0.5)}, max ${samples.changes.max()}")
        println("SPIKE event JSON (compact, definitions by reference): encode median ${ms(samples.encode.pct(0.5))} ms, max ${ms(samples.encode.max())} ms; " +
            "bytes median ${samples.bytes.map { it.toLong() }.pct(0.5)}, p95 ${samples.bytes.map { it.toLong() }.pct(0.95)}, max ${samples.bytes.max()}, " +
            "total ${samples.bytes.sum() / 1024} KiB (inline definitions: ${samples.inlineBytes.sum() / 1024} KiB)")
        // Checkpoints: the whole state of the longest game as stored (definitions once per game)
        val longest = finalStates.maxBy { it.log.size }
        val model = GameModel(longest, TableState())
        val codec = EngineJson(GameDefinitions.of(longest))
        var encoded = ""
        val enc = timeUs(5) { encoded = codec.encodeModel(model) }
        val dec = timeUs(5) { codec.decodeModel(encoded) }
        val copy = timeUs(5) { longest.deepCopy() }
        println("SPIKE checkpoint of a turn-${longest.turn.number} state (${longest.objects.size} objects, log ${longest.log.size}): " +
            "${encoded.length / 1024} KiB, encode ${ms(enc)} ms, decode ${ms(dec)} ms, deepCopy ${ms(copy)} ms; " +
            "definitions ${codec.definitions.size}, ${codec.encodeDefinitions().length / 1024} KiB once per game")
        // As GameModel keeps it: logged changes older than last turn dropped (what a stored checkpoint holds)
        val trimmed = GameModel(longest.deepCopy().also { it.forgetOlderTurns() }, TableState())
        var stored = ""
        val encTrimmed = timeUs(5) { stored = codec.encodeModel(trimmed) }
        val decTrimmed = timeUs(5) { codec.decodeModel(stored) }
        println("SPIKE checkpoint as stored (log of this and last turn: ${trimmed.engine.log.size}): ${stored.length / 1024} KiB, " +
            "encode ${ms(encTrimmed)} ms, decode ${ms(decTrimmed)} ms")

        heavy()
    }

    private fun timeUs(times: Int, block: () -> Unit): Long {
        block()
        return List(times) { val t = System.nanoTime(); block(); (System.nanoTime() - t) / 1000 }.sorted()[times / 2]
    }

    // ---- Heavy inputs on constructed boards

    private class Board(val state: GameState) {
        private var next = 0
        fun put(owner: PlayerId, zone: Zone, vararg names: String, tapped: Boolean = false,
            counters: List<Pair<CounterKind, Int>> = emptyList()): List<ObjectId> = names.map { name ->
            val id = ObjectId.Card(CardId("${owner.value}.${++next}"), 0)
            val stamp = ++state.timestampCounter
            val onBattlefield = zone == Zone.Battlefield
            state.objects[id] = GameObject(id = id, owner = owner, controller = if (onBattlefield) owner else null, zone = zone,
                definition = Cards.definition(name), tapped = tapped, timestamp = stamp,
                counters = counters.map { (k, n) -> CounterEntry(k, n, stamp) },
                controlSince = if (onBattlefield) ControlSince(owner, -1) else null)
            when (zone) {
                Zone.Battlefield -> state.battlefield.add(id)
                Zone.Hand -> state.updatePlayerForTest(owner) { it.copy(hand = it.hand + id) }
                Zone.Library -> state.updatePlayerForTest(owner) { it.copy(library = it.library + id) }
                else -> error("not used")
            }
            id
        }
    }

    private fun board(step: TurnStep = TurnStep.PrecombatMain, build: Board.() -> Unit): GameState {
        val state = GameState.start(listOf(alice, bob))
        state.turn = TurnState(3, alice, step, turnStarted = mapOf(alice to 3, bob to 2))
        Board(state).build()
        return state
    }

    /** Median microseconds of [block] on fresh copies of [state] (the first runs warm up). */
    private fun heavyCase(label: String, state: GameState, block: (GameState) -> Pair<Long, Int>) {
        val runs = List(7) { block(state.deepCopy()) }.drop(2)
        val us = runs.map { it.first }.sorted()
        println("SPIKE heavy $label: ${state.battlefield.size} permanents: median ${ms(us[us.size / 2])} ms, max ${ms(us.max())} ms, " +
            "${runs.first().second} changes")
    }

    private fun GameState.go(input: Input): Outcome = Engine.perform(this, input).also { o -> o.changes.forEach { apply(it) } }

    private fun timed(state: GameState, input: Input): Pair<Long, Int> {
        val t = System.nanoTime()
        val o = Engine.perform(state, input)
        val us = (System.nanoTime() - t) / 1000
        o.changes.forEach { state.apply(it) }
        return us to o.changes.size
    }

    private fun castAndPay(state: GameState, card: ObjectId, modes: List<Int>? = null, target: TargetRef? = null) {
        state.go(Input.Cast(alice, card))
        repeat(4) {
            when (val d = state.pending?.decision) {
                is Decision.Modes -> state.go(Input.Answer(d.id, Answer.Modes(modes!!)))
                is Decision.Targets -> state.go(Input.Answer(d.id, Answer.Targets(listOf(target ?: d.options.first()))))
                is Decision.ManaPayment -> state.go(Input.Answer(d.id, Answer.ManaPayment(d.suggestion)))
                else -> return
            }
        }
    }

    private fun heavy() {
        // 1. A turn change on a 90-permanent board: cleanup, untap of 45 permanents, upkeep, draw
        val busy = board(TurnStep.End) {
            put(alice, Zone.Battlefield, *Array(25) { "Mountain" }, tapped = true)
            put(alice, Zone.Battlefield, *Array(20) { "Tinkered Mite" }, tapped = true)
            put(bob, Zone.Battlefield, *Array(25) { "Island" }, tapped = true)
            put(bob, Zone.Battlefield, *Array(20) { "Tinkered Mite" }, tapped = true)
            put(alice, Zone.Library, *Array(40) { "Mountain" }); put(bob, Zone.Library, *Array(40) { "Island" })
        }
        heavyCase("pass priority, typical (alice passes, 90 permanents)", busy) { s -> timed(s, Input.PassPriority(alice)) }
        heavyCase("turn change (bob's pass: cleanup, untap 45, upkeep, draw)", busy) { s ->
            s.go(Input.PassPriority(alice)); timed(s, Input.PassPriority(bob))
        }
        // 2. Layers: 30 lords and +1/+1 counters, 60+ permanents, one pass
        val lords = board {
            repeat(6) {
                put(alice, Zone.Battlefield, "Elvish Hierophant", "Bough-Crowned Oracle", "Elvish Seer", "Thicketwood Elves",
                    counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
                put(alice, Zone.Battlefield, "Lord of the Aetherforge")
                put(bob, Zone.Battlefield, "Elvish Hierophant", "Elvish Seer", "Tinkered Mite", "Iron Foreman", "Forest")
            }
            put(alice, Zone.Battlefield, "Mountain"); put(alice, Zone.Hand, "Storm Spark")
        }
        heavyCase("pass priority with 30 lords and counters (layers)", lords) { s -> timed(s, Input.PassPriority(alice)) }
        heavyCase("cast Storm Spark at a lord (cast + pay)", lords) { s ->
            val spark = s.player(alice).hand.single()
            val t = System.nanoTime()
            castAndPay(s, spark, target = TargetRef.Object(s.battlefield.first()))
            (System.nanoTime() - t) / 1000 to 0
        }
        heavyCase("resolve Storm Spark on the lord board (SBA, layers again)", lords) { s ->
            castAndPay(s, s.player(alice).hand.single(), target = TargetRef.Object(s.battlefield.first()))
            s.go(Input.PassPriority(alice)); timed(s, Input.PassPriority(bob))
        }
        // 3. A board wipe: Blazing Confluence, 1 damage to each creature three times, 60 small creatures die
        val wipe = board {
            put(alice, Zone.Battlefield, *Array(4) { "Mountain" })
            put(alice, Zone.Battlefield, *Array(30) { "Elvish Seer" })
            put(bob, Zone.Battlefield, *Array(30) { "Tinkered Mite" })
            put(alice, Zone.Hand, "Blazing Confluence")
        }
        heavyCase("board wipe resolves (Blazing Confluence, 60 creatures die)", wipe) { s ->
            castAndPay(s, s.player(alice).hand.single(), modes = listOf(0, 0, 0))
            s.go(Input.PassPriority(alice)); timed(s, Input.PassPriority(bob))
        }
        // 4. Ten death triggers at once
        val mourners = board {
            put(alice, Zone.Battlefield, *Array(10) { "Crimson Mourner" })
            put(alice, Zone.Battlefield, "Mountain"); put(alice, Zone.Hand, "Storm Spark")
            put(bob, Zone.Battlefield, "Elvish Seer")
        }
        heavyCase("Storm Spark resolves, a creature dies, 10 Mourner triggers", mourners) { s ->
            val seer = s.battlefield.last()
            castAndPay(s, s.player(alice).hand.single(), target = TargetRef.Object(seer))
            s.go(Input.PassPriority(alice)); timed(s, Input.PassPriority(bob))
        }
        heavyCase("answering the 10 triggers' order", mourners) { s ->
            val seer = s.battlefield.last()
            castAndPay(s, s.player(alice).hand.single(), target = TargetRef.Object(seer))
            s.go(Input.PassPriority(alice)); s.go(Input.PassPriority(bob))
            when (val d = s.pending?.decision) {
                is Decision.OrderTriggers -> timed(s, Input.Answer(d.id, Answer.Order(d.triggers.map { it.index })))
                is Decision.Targets -> timed(s, Input.Answer(d.id, Answer.Targets(listOf(d.options.first()))))
                else -> 0L to 0
            }
        }
        // 5. Combat: 20 attackers, 20 blockers, damage
        val combat = board(TurnStep.BeginningOfCombat) {
            put(alice, Zone.Battlefield, *Array(20) { "Thicketwood Elves" })
            put(bob, Zone.Battlefield, *Array(20) { "Tinkered Mite" })
        }
        heavyCase("declare 20 attackers", combat) { s ->
            s.go(Input.PassPriority(alice)); s.go(Input.PassPriority(bob))
            val attack = Engine.options(s, alice).filterIsInstance<Input.DeclareAttackers>().maxBy { it.attacks.size }
            timed(s, attack)
        }
        heavyCase("declare 20 blockers and deal combat damage", combat) { s ->
            s.go(Input.PassPriority(alice)); s.go(Input.PassPriority(bob))
            s.go(Engine.options(s, alice).filterIsInstance<Input.DeclareAttackers>().maxBy { it.attacks.size })
            while (s.turn.step != TurnStep.DeclareBlockers || s.pending != null) {
                val p = s.priority.holder ?: break
                s.go(Input.PassPriority(p))
            }
            val block = Engine.options(s, bob).filterIsInstance<Input.DeclareBlockers>().maxBy { it.blocks.size }
            val t = System.nanoTime()
            var changes = s.go(block).changes.size
            while (s.turn.step != TurnStep.EndOfCombat && s.priority.holder != null && s.pending == null) {
                changes += s.go(Input.PassPriority(s.priority.holder!!)).changes.size
            }
            (System.nanoTime() - t) / 1000 to changes
        }
    }

    /** Four themed 40-card decks of invented cube cards (as the engine's review 3 has them). */
    private object Decks {
        private fun lands(a: String, b: String) = List(8) { a } + List(8) { b }
        val artifacts = lands("Island", "Mountain") + listOf(
            "Tinkered Mite", "Gearwing Glider", "Sun Band", "Lotus Frond", "Prismatic Mote", "Skull Bracer", "Bonevise",
            "Runner's Skiff", "Tidal Spellcharge", "Cinder Spellcharge", "Iron Foreman", "Uniskelion", "Striding Arbalest",
            "Lord of the Aetherforge", "Tavi, Thopter Savant", "Cogitating Sentry", "Scrapquilt Automaton", "Volatile Myr",
            "Myr Gleaner", "Scrap Dredger", "Hangar Strider", "Goblin Solderer", "Refitting Workshop", "Ondra, Lurker of the Mere")
        val graveyard = lands("Swamp", "Forest") + listOf(
            "Festering Gremlin", "Stitchwork Porter", "Undying Valet", "Wreckage Analyst", "Satyr Pathseeker", "Gorehaunt",
            "Bloodsworn Duelist", "Tombscuttler", "Netherbound Turncoat", "Kettle Kitten", "Stairway from the Depths",
            "Fiend Sculptor", "Wight of the Relic Vault", "Corpse Gnawer", "Plague Shamblers", "Unyielding Dead",
            "Rebirth from the Mire", "Shadowsting", "Neckless Horseman", "Undead Soothsayer", "Burrowed Shambler",
            "Shuffling Wight", "Sinister Rootwork", "Undying Chronicler")
        val spells = lands("Island", "Mountain") + listOf(
            "Storm Spark", "Cinder Bolt", "Magma Needle", "Flash Surge", "Grindstone Rasp", "Stormforge Charm",
            "Faithless Rummage", "Mull It Over", "Glance Ahead", "Foretell the Way", "Thought Siphon", "Mind Chill",
            "Linked Lightning", "Flash of Mirroring", "Mounting Retribution", "Novice Flamecaller", "Wyrm's Fury Conduit",
            "Ysolde, the Unbound Scholar", "Grubb, the Fingerless", "Expanse // Eruption", "Denial", "Spell Prick", "Click Back",
            "Twincast Adept")
        val creatures = lands("Forest", "Plains") + listOf(
            "Elvish Seer", "Thicketwood Elves", "Canopy Visionary", "Elvish Hierophant", "Bough-Crowned Oracle",
            "Elvish Drillmaster", "Haven Warden", "Larder Ouphes", "Loyal Hearthhound", "Hollowell Investigator",
            "Wall of Portents", "Golem Grafter", "Torvanna, Elder-Tree Spirit", "Radiant Acolyte", "Brisa, Bounding Shepherd",
            "Aldric, Fist of the Seraphs", "Seasoned Pathfinder", "Sunning Mudlizard", "Bestower of Sigils", "Commanding Orator",
            "Ranger-Marshal of Dawn", "Elenwyn, Spinner of Dawn", "Knight of the Relic Vault", "Podrick Burrowfoot")
        val pairs = listOf(creatures to spells, artifacts to graveyard, creatures to graveyard, artifacts to spells)
    }
}

private fun GameState.updatePlayerForTest(id: PlayerId, change: (arcanum.engine.state.PlayerState) -> arcanum.engine.state.PlayerState) {
    val i = players.indexOfFirst { it.id == id }
    players[i] = change(players[i])
}
