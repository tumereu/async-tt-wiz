package org.asyncttwiz.android

import arcanum.api.Layout
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.EngineProbes
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.setup.StartOptions
import arcanum.engine.state.CardId
import arcanum.engine.state.ControlSince
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.DeckJson
import org.asyncttwiz.protocol.DefinitionFace
import org.asyncttwiz.protocol.EngineSnapshot
import org.asyncttwiz.protocol.PlayCard
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.LineRandom
import org.asyncttwiz.protocol.game.TableState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End turn engine speed (backlog, owner 2026-11-06): what holding End turn and a single step pass cost on the device's
 * engine path ([EngineGame] with the engine thread inline: the device's own steps, the information rule, building the
 * board), on a small board and a heavy one (about 44 permanents a side: anthems, a lord, equipment and auras attached,
 * upkeep and end step triggers, mana creatures and pumpers, instants in hand). Prints medians of warm runs (in [ROUNDS]
 * rounds over the cases, each run on a game loaded afresh at Alice's first main phase), who works out the layers, and a
 * sampled profile (each method's share of the samples); never asserts on time (owner rule).
 *
 * Runs only with `-Patw.bench=true`:
 * `./scripts/gradle.sh :androidApp:testDebugUnitTest -Patw.bench=true --tests '*EndTurnBenchmark*' -i`
 * (`-Patw.bench.runs=N` measured runs per case and round, default 15; `-Patw.bench.profile=false` skips the profile).
 */
class EndTurnBenchmark {
    private val enabled get() = System.getProperty("atw.bench") == "true"
    private val runs get() = System.getProperty("atw.bench.runs")?.toIntOrNull() ?: 15
    private val profile get() = System.getProperty("atw.bench.profile") != "false"

    @Test fun endTurnAndStepPasses() {
        if (!enabled) return
        val boards = listOf(BenchBoard("small", BenchBoard.SMALL), BenchBoard("heavy", BenchBoard.HEAVY),
            // Castability: reducers on the board, a hand of cards the mana can't pay, each with targets: every look at the hand looks through them
            BenchBoard("reducers", BenchBoard.REDUCERS, BenchBoard.UNAFFORDABLE),
            // The search's worst: a reduction whose amount reads the targets can't be bounded: each target is tried
            BenchBoard("readers", BenchBoard.REDUCERS, BenchBoard.UNBOUNDED))
        for (board in boards) println("BENCH board ${board.name}: ${board.permanents} permanents, ${board.parsedWarnings}")
        val ops = listOf<Pair<String, (EngineGame) -> Unit>>(
            "endTurn" to { g -> g.endTurn() },
            "pass" to { g -> g.pass() },
        )
        // Rounds interleaved over the cases, so a slower spell of the machine touches every case alike
        val cases = boards.flatMap { board -> ops.map { (name, op) -> Triple(board, name, op) } }
        cases.forEach { (board, _, op) -> repeat(5) { once(board, op) } }
        val all = cases.associate { (board, name, _) -> "${board.name.padEnd(5)} $name" to mutableListOf<Run>() }
        repeat(ROUNDS) { round ->
            for ((board, name, op) in cases) {
                val runs = List(runs) { once(board, op) }
                all.getValue("${board.name.padEnd(5)} $name") += runs
                println("BENCH round ${round + 1} ${board.name.padEnd(5)} $name: ${Measured(runs).summary()}")
            }
        }
        val results = all.map { (case, runs) -> "BENCH $case: ${Measured(runs).summary()}" }
        for (board in boards.reversed()) for ((name, op) in ops) census(board, name, op)
        if (profile) for (board in boards.reversed().take(1)) for ((name, op) in ops.take(1)) profiled(board, name, op)
        println("BENCH summary\n" + results.joinToString("\n"))
    }

    private companion object { const val ROUNDS = 3 }

    private class Run(val totalMs: Double, val engineMs: Double, val infoMs: Double, val boardMs: Double, val afterMs: Double,
        val layers: Int, val events: Int)

    private class Measured(val runs: List<Run>) {
        private fun median(f: (Run) -> Double) = runs.map(f).sorted().let { it[it.size / 2] }
        fun summary(): String = "total %.1f ms (engine %.1f, info %.1f, board %.1f, after %.1f), layers %d, events %d [median of %d]".format(
            median { it.totalMs }, median { it.engineMs }, median { it.infoMs }, median { it.boardMs }, median { it.afterMs },
            runs.map { it.layers }.sorted()[runs.size / 2], runs.map { it.events }.sorted()[runs.size / 2], runs.size)
    }

    private fun once(board: BenchBoard, op: (EngineGame) -> Unit): Run {
        val game = board.game()
        var probed: Triple<Long, Long, Long>? = null
        game.probe = { _, waited, acted, built -> probed = Triple(waited, acted, built) }
        val info0 = game.read { it.informationNanos }
        val tip0 = game.read { it.session.tip }
        val events0 = game.read { it.session.pending.size + it.session.events.size }
        var layers = 0
        EngineProbes.layersWorkedOut = { layers++ }
        val t0 = System.nanoTime()
        try { op(game) } finally { EngineProbes.layersWorkedOut = null }
        val total = System.nanoTime() - t0
        val info = game.read { it.informationNanos } - info0
        check(game.read { it.session.tip } != tip0) { "nothing happened" }
        val (_, acted, built) = probed ?: Triple(0L, 0L, 0L)
        val events = game.read { it.session.pending.size + it.session.events.size } - events0
        return Run(total / 1e6, (acted - info.coerceAtMost(acted)) / 1e6, info / 1e6, built / 1e6,
            (total - acted - built) / 1e6, layers, events)
    }

    /** Who works out the layers in one run of [op] on [board]: each full layer computation by the nearest host-side
     * method (device, app) and the outermost engine entry point on its stack, and whether it is a cast's provisional view. */
    private fun census(board: BenchBoard, name: String, op: (EngineGame) -> Unit) {
        val game = board.game()
        val counts = HashMap<String, Int>()
        fun key(e: StackTraceElement) = e.className.substringAfterLast('.').substringBefore('$') + "." + e.methodName.substringBefore('$')
        EngineProbes.layersWorkedOut = {
            val stack = Thread.currentThread().stackTrace.toList().filter { !it.className.contains("EndTurnBenchmark") }
            val host = stack.firstOrNull { it.className.startsWith("org.asyncttwiz.") }?.let(::key) ?: "?"
            val apiAt = stack.indexOfLast { it.className == "arcanum.engine.Engine" }
            val api = if (apiAt >= 0) key(stack[apiAt]) else "(internal)"
            val path = stack.take(if (apiAt >= 0) apiAt else stack.size).filter { it.className.startsWith("arcanum.engine.") }.map(::key)
                .filter { k -> listOf("Options.", "AutoPass.", "Perform.", "Plan.", "Casting.", "Payment.", "Mana.", "Knowledge.", "Snapshot.", "Recorder.", "Triggers.", "Steps.", "StateBased")
                    .any { k.startsWith(it) } }.distinct().take(4).reversed().joinToString(">")
            counts.merge("$host <- $api: $path", 1, Int::plus)
        }
        try { op(game) } finally { EngineProbes.layersWorkedOut = null }
        println("BENCH census ${board.name} $name: ${counts.values.sum()} layer computations")
        counts.entries.sortedByDescending { it.value }.forEach { (k, v) -> println("BENCH   %4d %s".format(v, k)) }
    }

    /** A sampled profile of [op] on [board] (a sampler thread reading this thread's stack every millisecond): the share
     * of samples each method is on the stack (inclusive) and on top of the stack among the app's and engine's methods. */
    private fun profiled(board: BenchBoard, name: String, op: (EngineGame) -> Unit) {
        val target = Thread.currentThread()
        val samples = java.util.concurrent.ConcurrentLinkedQueue<Array<StackTraceElement>>()
        val sampling = java.util.concurrent.atomic.AtomicBoolean(false)
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val sampler = Thread {
            while (!stop.get()) {
                if (sampling.get()) samples += target.stackTrace
                Thread.sleep(0, 500_000)
            }
        }.apply { isDaemon = true; start() }
        repeat(3) { once(board, op) }
        // Java Flight Recorder's samples too: not biased to safepoints, so their top frames are where the time is spent
        val recording = jdk.jfr.Recording().apply {
            enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(1))
            start()
        }
        repeat(runs * 2) {
            val game = board.game()
            sampling.set(true)
            try { op(game) } finally { sampling.set(false) }
        }
        recording.stop()
        stop.set(true)
        sampler.join()
        val file = java.nio.file.Files.createTempFile("bench", ".jfr")
        recording.dump(file)
        recording.close()
        val leaves = HashMap<String, Int>()
        val owners = HashMap<String, Int>()
        var flight = 0
        for (event in jdk.jfr.consumer.RecordingFile.readAllEvents(file)) {
            if (event.eventType.name != "jdk.ExecutionSample") continue
            if (event.getThread("sampledThread")?.javaThreadId != @Suppress("DEPRECATION") target.id) continue
            val frames = event.stackTrace?.frames ?: continue
            flight++
            frames.firstOrNull()?.let { f -> leaves.merge(f.method.type.name.substringAfterLast('.') + "." + f.method.name, 1, Int::plus) }
            // The first two of the engine's or the app's own methods, from the top: who the library time is spent for
            // (ids' equality and the state's object lookup are everyone's: their callers are named instead)
            frames.filter { val t = it.method.type.name; (t.startsWith("arcanum.") || t.startsWith("org.asyncttwiz.")) &&
                t != "arcanum.engine.state.CardId" && !t.startsWith("arcanum.engine.state.ObjectId") && it.method.name != "obj" }.take(2)
                .joinToString(" < ") { f -> f.method.type.name.substringAfterLast('.') + "." + f.method.name + ":" + f.lineNumber }
                .takeIf { it.isNotEmpty() }?.let { owners.merge(it, 1, Int::plus) }
        }
        java.nio.file.Files.deleteIfExists(file)
        println("BENCH flight recorder ${board.name} $name: $flight samples, top frames")
        leaves.entries.sortedByDescending { it.value }.take(40).forEach { (k, v) -> println("BENCH   top %5.1f%% %s".format(100.0 * v / flight.coerceAtLeast(1), k)) }
        owners.entries.sortedByDescending { it.value }.take(40).forEach { (k, v) -> println("BENCH   own %5.1f%% %s".format(100.0 * v / flight.coerceAtLeast(1), k)) }
        val own = { e: StackTraceElement -> e.className.startsWith("arcanum.") || e.className.startsWith("org.asyncttwiz.") }
        fun key(e: StackTraceElement) = e.className.substringAfterLast('.').substringBefore('$') + "." + e.methodName.substringBefore('$')
        val inclusive = HashMap<String, Int>()
        val self = HashMap<String, Int>()
        val all = samples.filter { s -> s.any { own(it) && key(it) != "EndTurnBenchmark.profiled" } }
        for (s in all) {
            s.filter(own).map(::key).toSet().forEach { inclusive.merge(it, 1, Int::plus) }
            s.firstOrNull(own)?.let { self.merge(key(it), 1, Int::plus) }
        }
        val n = all.size.coerceAtLeast(1)
        println("BENCH profile ${board.name} $name: ${all.size} samples")
        inclusive.entries.sortedByDescending { it.value }.take(70).forEach { (k, v) -> println("BENCH   incl %5.1f%% %s".format(100.0 * v / n, k)) }
        self.entries.sortedByDescending { it.value }.take(30).forEach { (k, v) -> println("BENCH   self %5.1f%% %s".format(100.0 * v / n, k)) }
    }
}

/**
 * A game at Alice's first main phase with [board]'s permanents on both sides (the same for each player unless [other]
 * gives Bob's), as a device
 * loads it from the server's snapshot: both players kept, every permanent in play since before the turn (no summoning
 * sickness), equipment and auras attached to the creatures in order. The rest of each 60-card deck (Mountains, Storm
 * Sparks, Ember Cubs) is in the library and the opening hand.
 */
internal class BenchBoard(val name: String, private val board: List<Pair<BenchCard, Int>>, private val filler: List<Pair<BenchCard, Int>> = listOf(BenchCard.MOUNTAIN to 20, BenchCard.SPARK to 12, BenchCard.CUB to 8),
    /** Cards put into each player's hand besides the opening hand (taken out of the filler). */
    hand: List<Pair<BenchCard, Int>> = emptyList(), private val life: Int? = null,
    /** Bob's permanents when they differ from Alice's ([board]). */
    private val other: List<Pair<BenchCard, Int>> = board) {
    private val f = TableStatesFixture
    val a = f.a
    val b = f.b
    private fun boardOf(p: String) = if (p == b) other else board
    private val decks = listOf(a, b).associateWith { p ->
        (boardOf(p) + filler + hand).flatMap { (card, n) -> List(n) { card } }.mapIndexed { i, card -> f.id("$p/card/$i") to card }
    }
    val permanents get() = board.sumOf { it.second } + other.sumOf { it.second }
    val parsedWarnings: String = (board + other + filler + hand).map { it.first }.distinct()
        .filter { c -> c.definition.faces.any { face -> face.abilities.any { it is arcanum.api.Ability.Unparsed } } }
        .let { if (it.isEmpty()) "every card parsed" else "UNPARSED: " + it.joinToString { c -> c.name } }

    private val base: PlayState = f.initial.let { p ->
        val playCards = decks.flatMap { (player, deck) -> deck.map { (id, card) ->
            PlayCard(id, card.submitted.digest, player, f.id("${if (player == a) "a" else "b"}/LIBRARY"), card.submitted.definition.faces.single().faceId)
        } }
        p.copy(definitions = decks.values.flatten().map { it.second.submitted }.distinctBy { it.digest }.sortedBy { it.digest },
            board = p.board.copy(cards = playCards, stackEffects = emptyList(), combat = null, engineRecords = emptyList(),
                containers = p.board.containers.map { c ->
                    if (c.zone == PlayZone.LIBRARY && !c.temp) c.copy(cards = playCards.filter { it.containerId == c.id }.map { it.id }, layers = emptyList())
                    else c.copy(cards = emptyList(), layers = emptyList())
                }))
    }

    private val snapshot: EngineSnapshot = run {
        val model = GameModel(Engine.newGame(listOf(a, b).map { p ->
            Seat(PlayerId(p), decks.getValue(p).map { (id, card) -> DeckCard(CardId(id), card.definition) })
        }).also { s -> s.players.toList().forEach { p -> s.apply(arcanum.engine.change.Changes.skipWhenNoChoice(s, p.id, false)) } },
            TableState(secret = LineRandom.secret(ByteArray(32) { 5 })))
        fun event(id: String, parent: String, author: String, input: (GameState) -> Input) = EventBuilder(model, f.id(id), f.id(parent), author).run {
            perform(input(model.engine)); answerRandoms(); build()
        }
        fun keep(p: String): (GameState) -> Input = { s ->
            Engine.options(s, PlayerId(p)).first { i ->
                (i is Input.SetupAnswer && i.player == PlayerId(p) && i.answer == arcanum.engine.decision.Answer.Option("Keep")) ||
                    (i is Input.Answer && i.answer == arcanum.engine.decision.Answer.Option("Keep"))
            }
        }
        event("start", "root", "server") {
            Input.StartGame(StartOptions(startingPlayer = PlayerId(a), simultaneousMulligans = true, freeFirstMulligan = true))
        }
        event("keep/b", "start", b, keep(b))
        val head = event("keep/a", "keep/b", a, keep(a))
        check(model.engine.turn.number == 1) { "turn ${model.engine.turn}" }
        for (p in listOf(a, b)) put(model.engine, PlayerId(p), boardOf(p))
        for (p in listOf(a, b)) toHand(model.engine, PlayerId(p), hand)
        if (life != null) model.engine.players.indices.forEach { i -> model.engine.players[i] = model.engine.players[i].copy(life = life) }
        val codec = EngineJson(GameDefinitions.of(model.engine))
        EngineSnapshot(codec.encodeDefinitions(), f.id("root"), head.id, codec.encodeModel(model),
            localHeads = mapOf(a to head.id, b to head.id), settings = PLAYS_ON)
    }

    /** Puts [player]'s cards of [board] onto the battlefield (from the library, else the hand), untapped and in play
     * since before the turn; equipment and auras attached to the creatures in turn. */
    private fun put(s: GameState, player: PlayerId, board: List<Pair<BenchCard, Int>>) {
        val moved = mutableListOf<ObjectId>()
        for ((card, n) in board) repeat(n) {
            val p = s.player(player)
            val id = (p.library + p.hand).first { id -> id !in moved && s.obj(id)?.definition == card.definition }
            moved += id
        }
        val index = s.players.indexOfFirst { it.id == player }
        val p = s.players[index]
        s.players[index] = p.copy(library = p.library - moved.toSet(), hand = p.hand - moved.toSet(),
            libraryPlaces = p.libraryPlaces - moved.toSet())
        val creatures = moved.filter { s.obj(it)!!.definition!!.faces[0].types.contains(arcanum.api.CardType.Creature) }
        var next = 0
        for (id in moved) {
            val o = s.obj(id)!!
            val face = o.definition!!.faces[0]
            val attaches = face.subtypes.any { it.name == "Equipment" || it.name == "Aura" }
            s.objects[id] = o.copy(zone = Zone.Battlefield, controller = player, controlSince = ControlSince(player, -1),
                timestamp = ++s.timestampCounter,
                attachedTo = if (attaches && creatures.isNotEmpty()) TargetRef.Object(creatures[next++ % creatures.size]) else null)
            s.battlefield.add(id)
        }
    }

    /** Moves [player]'s cards of [hand] from the library into the hand. */
    private fun toHand(s: GameState, player: PlayerId, hand: List<Pair<BenchCard, Int>>) {
        val moved = mutableListOf<ObjectId>()
        for ((card, n) in hand) {
            val inHand = s.player(player).hand.count { id -> s.obj(id)?.definition == card.definition }
            repeat(n - inHand) { moved += s.player(player).library.first { id -> id !in moved && s.obj(id)?.definition == card.definition } }
        }
        val index = s.players.indexOfFirst { it.id == player }
        val p = s.players[index]
        s.players[index] = p.copy(library = p.library - moved.toSet(), hand = p.hand + moved, libraryPlaces = p.libraryPlaces - moved.toSet())
        for (id in moved) s.objects[id] = s.obj(id)!!.copy(zone = Zone.Hand)
    }

    /** The game loaded afresh, at Alice's first main phase with priority. */
    fun game(): EngineGame {
        var n = 0
        val game = EngineGame(f.game.id, a, base, snapshot, ids = { "bench-${++n}" })
        game.tick(1_000)
        val status = game.shown!!.status
        check(status == GameDevice.Status.YourMove) { "status $status" }
        val s = game.read { it.model.engine }
        check(s.turn.step == arcanum.api.TurnStep.PrecombatMain && s.priority.holder == PlayerId(a)) { "at ${s.turn} ${s.priority}" }
        return game
    }

    companion object {
        val SMALL = listOf(BenchCard.MOUNTAIN to 3, BenchCard.FOREST to 2, BenchCard.CUB to 2, BenchCard.BRUTE to 1)
        val HEAVY = listOf(
            BenchCard.MOUNTAIN to 7, BenchCard.FOREST to 7, BenchCard.BANNER to 2, BenchCard.MARSHAL to 1, BenchCard.ELF_LORD to 1,
            BenchCard.CLERIC to 3, BenchCard.IMP to 2, BenchCard.SENTINEL to 1, BenchCard.BRUTE to 3, BenchCard.KNIGHT to 3,
            BenchCard.TENDER to 2, BenchCard.HOUND to 2, BenchCard.ELF to 3, BenchCard.PLATE to 4, BenchCard.WINGS to 3,
        )
        val REDUCERS = HEAVY + listOf(BenchCard.COIN to 2)
        val UNBOUNDED = listOf(BenchCard.MOUNTAIN to 8, BenchCard.FAR_READER to 24, BenchCard.SPARK to 4, BenchCard.CUB to 4)
        val UNAFFORDABLE = listOf(BenchCard.MOUNTAIN to 8, BenchCard.FAR_BANISH to 12, BenchCard.FAR_REBUKE to 12, BenchCard.SPARK to 4, BenchCard.CUB to 4)
    }
}

/** An invented card (our names, oracle wordings of real templates), as the server and a device parse it. */
internal class BenchCard(val name: String, cost: String?, type: String, text: String = "", power: String? = null, toughness: String? = null) {
    val submitted = CardDefinition(name, listOf(DefinitionFace(TableStatesFixture.id("face/$name"), name, typeLine = type,
        manaCost = cost.orEmpty(), rulesText = text, power = power.orEmpty(), toughness = toughness.orEmpty())))
        .let { SubmittedDefinition(DeckJson.digest(it), it) }
    val definition = arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(OracleCard(name, cost, type, text, power, toughness))))

    companion object {
        val MOUNTAIN = BenchCard("Mountain", null, "Basic Land — Mountain")
        val COIN = BenchCard("Coin Medallion", "{2}", "Artifact", "Creature spells you cast cost {1} less to cast.")
        val FAR_READER = BenchCard("Far Measure", "{19}{W}", "Instant", "This spell costs {1} less to cast for each target creature.\nTap up to five target creatures.")
        val FAR_BANISH = BenchCard("Far Banishing", "{19}{W}", "Instant", "Exile target creature.")
        val FAR_REBUKE = BenchCard("Far Rebuke", "{19}{W}", "Instant", "This spell costs {2} less to cast if it targets a tapped creature.\nExile target creature.")
        val FOREST = BenchCard("Forest", null, "Basic Land — Forest")
        val SPARK = BenchCard("Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target.")
        val CUB = BenchCard("Ember Cub", "{R}", "Creature — Elemental Cat", "", "2", "1")
        val BRUTE = BenchCard("Thornback Brute", "{2}{G}", "Creature — Beast", "Trample", "3", "3")
        val KNIGHT = BenchCard("Vigil Knight", "{1}{W}", "Creature — Human Knight", "Vigilance, lifelink", "2", "2")
        val BANNER = BenchCard("Banner of the Host", "{2}{W}", "Enchantment", "Creatures you control get +1/+1.")
        val MARSHAL = BenchCard("Warband Marshal", "{1}{W}{W}", "Creature — Human Soldier", "Other creatures you control get +1/+1.", "2", "2")
        val ELF_LORD = BenchCard("Grovecall Elder", "{1}{G}{G}", "Creature — Elf Druid", "Other Elf creatures you control get +1/+1.", "2", "2")
        val ELF = BenchCard("Grove Skirmisher", "{G}", "Creature — Elf Warrior", "", "1", "1")
        val TENDER = BenchCard("Grove Tender", "{G}", "Creature — Elf Druid", "{T}: Add {G}.", "1", "1")
        val HOUND = BenchCard("Cinder Hound", "{R}", "Creature — Elemental Dog", "{R}: This creature gets +1/+0 until end of turn.", "2", "1")
        val CLERIC = BenchCard("Dawnward Cleric", "{1}{W}", "Creature — Human Cleric", "At the beginning of your upkeep, you gain 1 life.", "1", "3")
        val IMP = BenchCard("Duskward Imp", "{1}{B}", "Creature — Imp", "Flying\nAt the beginning of your end step, each opponent loses 1 life.", "1", "1")
        val SENTINEL = BenchCard("Watchful Sentinel", "{2}{W}", "Creature — Human Soldier", "Whenever another creature you control enters, you gain 1 life.", "2", "3")
        val PLATE = BenchCard("Ironhide Plate", "{2}", "Artifact — Equipment", "Equipped creature gets +2/+2.\nEquip {2}")
        val WINGS = BenchCard("Wings of the Gale", "{1}{U}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets +1/+1 and has flying.")
    }
}
