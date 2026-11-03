package org.asyncttwiz.android

import arcanum.api.Ability
import arcanum.api.Layout
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.automatum.StepFlow
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ParsedCard
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-03 #1 (TRN-063): the step arrow's words are exactly what its tap does. Seeded games between two
 * devices through the app's controllers and the server's gate (as `GameService`): the players play lands, cast creatures
 * and instants, attack and block now and then, and otherwise tap the step arrow. Before each tap the arrow's words are
 * read from its plan ([TableToolkitController.plannedStep], as the bottom bar shows them); after it the board the server
 * keeps is checked against them, e.g. "To combat" stops in combat (at the attack when something can attack), "End turn"
 * ends the turn, "Next" stays in the turn, "Pass" is the other player's pass, "Resolve" resolves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StepArrowGamesTest {
    private val f = TableStatesFixture

    private class Invented(val face: DefinitionFace, oracle: OracleCard) {
        val submitted = CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val definition = OracleParser.parse(oracle).let { arcanum.api.CardDefinition(Layout.Normal, listOf(it)) }
        val creature = "Creature" in face.typeLine
        val land = "Land" in face.typeLine
        val instant = face.typeLine == "Instant"
    }
    private fun invented(id: String, name: String, cost: String?, type: String, text: String, pt: String? = null) = Invented(
        DefinitionFace(id, name, typeLine = type, manaCost = cost.orEmpty(), rulesText = text, corner = pt.orEmpty()),
        OracleCard(name, cost, type, text, pt?.substringBefore("/"), pt?.substringAfter("/")))

    private val bear = invented("bear", "Stout Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val brute = invented("brute", "Ironhide Brute", "{3}{G}", "Creature — Beast", "Trample", "5/5")
    private val squire = invented("squire", "Dawnlance Squire", "{1}{R}", "Creature — Human Knight", "First strike", "2/1")
    private val hound = invented("hound", "Dusk Hound", "{1}{R}", "Creature — Dog", "Menace", "2/2")
    private val runner = invented("runner", "Ember Runner", "{R}", "Creature — Elemental", "Haste", "1/1")
    private val killer = invented("killer", "Quartzknife Killer", "{1}{R}", "Creature — Lizard Assassin",
        "Whenever this creature attacks, defending player loses 1 life and you gain 1 life.", "1/3")
    private val spark = invented("spark", "Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target.")
    private val growth = invented("growth", "Wild Surge", "{G}", "Instant", "Target creature gets +3/+3 until end of turn.")
    private val mountain = invented("mountain", "Mountain", null, "Basic Land — Mountain", "")
    private val forest = invented("forest", "Forest", null, "Basic Land — Forest", "")
    private val all = listOf(bear, brute, squire, hound, runner, killer, spark, growth, mountain, forest)

    @Before fun definitions() {
        val byDigest = all.associate { it.submitted.digest to it.definition }
        AutomatumBoards.definitionSource = { submitted -> submitted.mapNotNull { s ->
            byDigest[s.digest]?.let { s.digest to ParsedCard.Read(it, 0) } }.toMap() }
    }
    @After fun forget() {
        kotlinx.coroutines.runBlocking(AutomatumBoards.boardWorker) {}
        AutomatumBoards.reset(); AutomatumBoards.definitionSource = null; DeviceRandom.seeded(null)
    }

    private fun what(play: PlayState, card: String): Invented? =
        play.board.cards.singleOrNull { it.id == card }?.let { c -> all.firstOrNull { it.submitted.digest == c.definition } }

    /** A new game as the server starts it: 30-card decks in an unknown order, seven in each hand, both "Mulliganing". */
    private fun start(random: kotlin.random.Random, fullControl: Set<String>): PlayState {
        fun deck(): Invented = when (random.nextInt(12)) {
            in 0..4 -> if (random.nextBoolean()) mountain else forest
            5 -> spark
            6 -> growth
            else -> listOf(bear, brute, squire, hound, runner, killer).random(random)
        }
        val cards = mutableListOf<PlayCard>()
        val containers = mutableListOf<PlayContainer>()
        for (player in listOf(f.a, f.b)) {
            val name = if (player == f.a) "a" else "b"
            val ids = List(30) { f.id("arrowsim/$player/card/$it") }
            val drawn = ids.take(7)
            val rest = ids - drawn.toSet()
            ids.forEach { id -> val w = deck(); cards += PlayCard(id, w.submitted.digest, player,
                f.id("$name/${if (id in drawn) "HAND" else "LIBRARY"}"), w.face.faceId, corner = w.face.corner) }
            for (zone in PlayZone.entries.filter { it != PlayZone.STACK }) containers += PlayContainer(f.id("$name/$zone"), zone, player,
                when (zone) { PlayZone.LIBRARY -> rest; PlayZone.HAND -> drawn; else -> emptyList() },
                layers = if (zone == PlayZone.LIBRARY) LibraryLayers.initial(f.id("arrowsim/$player/layer"), rest) else emptyList())
        }
        containers += PlayContainer(f.id("arrowsim/STACK"), PlayZone.STACK)
        val seed = RandomV1.encodeSeed(random.nextBytes(32))
        val initial = f.initial
        return PlayState(initial.gameId, initial.id, initial.preparationRound, initial.startRequestId, initial.root, initial.root.id,
            BoardState(listOf(f.a, f.b).map { PlayPlayer(it, status = PlayerStatus.OPENING, fullControl = it in fullControl) },
                cards.sortedBy { it.id }, containers, TurnState(f.a)),
            all.map { it.submitted }, listOf(f.a, f.b).map { p -> InitialShuffle(p, seed, cards.filter { it.owner == p }.map { it.id }) },
            InitialStarter(StarterChoice(f.a), f.a))
    }

    private var tree: GameplayTree? = null
    private var requests = 0
    private fun live(): PlayState = tree!!.stateAt(tree!!.currentNodeId)!!
    private fun serve(actor: String, action: GameAction, request: String): Boolean {
        val play = live()
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), action, f.id("client"), "1")
        val change = AutomatumOnly.change(tree!!, actor, command)
        change.tree?.let { tree = it }
        return change.reason == null
    }

    private fun toolkit(player: String) = TableToolkitController(player, { live() }, { true },
        { action, _ -> serve(player, GameAction.Play(action), f.id("arrowsim/${requests++}")) },
        playBatch = { actions, request -> serve(player, GameAction.PlayBatch(actions), request) },
        automatedBatch = { batch, request -> serve(player, batch, request) }, playTree = { tree })

    private fun tags(play: PlayState) = engineTags(tree, play.currentNodeId)
    private fun scene(play: PlayState, viewer: String) = TablePresentation.scene(f.game, play, viewer, 412f, 892f, false)
    private fun field(play: PlayState, player: String) =
        play.board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == player }.cards
    private fun hand(play: PlayState, player: String) =
        play.board.containers.single { it.zone == PlayZone.HAND && it.accountId == player }.cards
    private fun position(play: PlayState) = TurnPosition.of(play.board.turn)!!
    private fun number(play: PlayState) = play.board.turn.number.toInt()
    private val combat = TurnPosition.BEGIN_COMBAT..TurnPosition.END_COMBAT

    /** Whether [play]'s turn's player could attack from here: on a copy, passing for them up to their declare attackers
     * step stops at their declaration (the engine declares no attackers by itself when nothing can attack, CR 508.8). */
    private fun couldAttack(play: PlayState): Boolean = runCatching {
        val flow = AutomatumBoards.sharedFlow(play, tags(play)) ?: return false
        val active = play.board.turn.accountId
        val call = flow.step(active, StepFlow.Until.Step(TurnPosition.DECLARE_ATTACKERS))
        val s = call.working
        s.turn.number == flow.state.turn.number && StepFlow.position(s) == TurnPosition.DECLARE_ATTACKERS &&
            s.pending == null && s.priority.holder == null && s.combat == null
    }.getOrDefault(false)

    private class Stats {
        val failures = mutableListOf<String>()
        val labels = mutableMapOf<String, Int>()
        /** Taps whose board alone would have said "To combat" (the old words) where the tap did something else. */
        var notToCombat = 0
        override fun toString() = "labels $labels, board said To combat but not $notToCombat, failures ${failures.size} ${failures.take(6)}"
    }

    /** The arrow of [player]'s device on the live board: its words from the tap's plan, then the tap; what the server
     * keeps afterwards is checked against the words. */
    private fun tapArrow(device: TableToolkitController, player: String, stats: Stats) {
        val before = live()
        device.planStep()
        val step = device.plannedStep(before)
        if (step == null) { stats.failures += "turn ${number(before)} ${position(before)}: no plan for $player's arrow (${device.flows.stepPlan})"; return }
        val label = step.label
        if (AutomatumStep.of(before) == AutomatumStep.ToCombat && step != AutomatumStep.ToCombat) stats.notToCombat++
        val attackable = step == AutomatumStep.ToCombat || step == AutomatumStep.EndTurn || step == AutomatumStep.Next
        val could = attackable && before.board.turn.accountId == player && position(before) < TurnPosition.BEGIN_COMBAT && couldAttack(before)
        val blocked = stepBlocked(before, device.cast.open, AutomatumBoards.combat(before) { tags(before) })
        if (blocked != null) { stats.failures += "the arrow is greyed: $blocked"; return }
        if (!device.perform(TableActionIntent.Next(TableActionScope(before.gameId, before.id)))) {
            stats.failures += "$label refused: ${device.error}"; return
        }
        val after = live()
        stats.labels.merge(label.substringBefore(" ("), 1, Int::plus)
        val sameTurn = number(after) == number(before)
        val at = "turn ${number(before)} ${position(before)} → turn ${number(after)} ${position(after)}"
        fun wrong(why: String) { stats.failures += "\"$label\" $at: $why" }
        if (after === before) wrong("nothing happened")
        when (step) {
            AutomatumStep.ToCombat -> if (!sameTurn || position(after) !in combat) wrong("not in combat")
                else if (could && position(after) > TurnPosition.DECLARE_ATTACKERS) wrong("went past the attack")
            AutomatumStep.EndTurn -> if (sameTurn && position(after) < TurnPosition.END_STEP && after.board.resultSuggestion == null)
                wrong("the turn didn't end")
                else if (could) wrong("something could attack")
            AutomatumStep.Next -> {
                val attacking = before.board.combat?.pairs.orEmpty().any { it.attackers.isNotEmpty() }
                if (!sameTurn && !attacking) wrong("the turn ended")
                if (sameTurn && position(before) < TurnPosition.BEGIN_COMBAT && position(after) in combat) wrong("went to combat")
                if (could && position(after) > TurnPosition.END_COMBAT) wrong("something could attack")
            }
            AutomatumStep.Pass -> if (before.board.turn.accountId == player) wrong("the turn's player passed")
            AutomatumStep.Resolve -> if (CombatStackRules.stack(after.board).cards == CombatStackRules.stack(before.board).cards &&
                sameTurn && position(after) == position(before)) wrong("nothing resolved")
            is AutomatumStep.Attack -> if (sameTurn && position(after) == TurnPosition.DECLARE_ATTACKERS && after.board.priority == null)
                wrong("still waiting for attackers")
            is AutomatumStep.Block -> if (sameTurn && position(after) == TurnPosition.DECLARE_BLOCKERS && after.board.priority == null &&
                after.board.attentionHolder == player) wrong("still waiting for blockers")
            AutomatumStep.Waiting -> wrong("a tap while a choice is open")
        }
    }

    private fun play(seed: Long, actions: Int, probe: ((PlayState, String) -> Unit)? = null): Stats {
        val random = kotlin.random.Random(seed)
        DeviceRandom.seeded(seed)
        val fullControl = listOf(f.a, f.b).filter { random.nextInt(3) == 0 }.toSet()
        tree = GameplayTree(start(random, fullControl))
        val devices = mapOf(f.a to toolkit(f.a), f.b to toolkit(f.b))
        val stats = Stats()
        repeat(actions) {
            val play = live()
            if (play.board.resultSuggestion != null || play.board.players.any { (it.life.toIntOrNull() ?: 1) <= 0 }) return stats
            // TRN-062: both keep their seven
            play.board.players.firstOrNull { it.status == PlayerStatus.OPENING }?.let { opening ->
                val d = devices.getValue(opening.accountId)
                if (!d.opening(TableActionIntent.Keep(TableActionScope(play.gameId, play.id)))) stats.failures += "keep: ${d.error}"
                return@repeat
            }
            val flow = AutomatumBoards.sharedFlow(play, tags(play)) ?: run { stats.failures += "no flow"; return stats }
            val decision = flow.decision
            if (decision != null) {
                val d = devices.getValue(decision.player.value)
                d.flows.show(flow)
                val answer = when (decision) {
                    is Decision.Divide -> Answer.Division(decision.suggested)
                    is Decision.ManaPayment -> Answer.ManaPayment(decision.suggestion)
                    else -> flow.listedAnswers().randomOrNull(random)
                }
                if (answer == null || !d.flows.answer(answer)) { stats.failures += "answer refused: ${d.error} ($decision)"; return stats }
                return@repeat
            }
            val actor = flow.priority ?: flow.holder ?: flow.declaring ?: run { stats.failures += "nobody acts"; return stats }
            val d = devices.getValue(actor)
            val stackEmpty = CombatStackRules.stack(play.board).cards.isEmpty()
            val main = position(play) == TurnPosition.PRECOMBAT_MAIN || position(play) == TurnPosition.POSTCOMBAT_MAIN
            val items = { scene(live(), actor).items.filter { it.viewport != "combat" } }
            when {
                flow.declaring == actor && actor == play.board.turn.accountId && random.nextBoolean() ->
                    for (c in field(play, actor)) if (what(live(), c)?.creature == true && random.nextBoolean()) {
                        items().firstOrNull { it.cardId == c && it.zone == PlayZone.BATTLEFIELD }?.let { d.open(TableIntent.Actions(it.id, null), it) }
                        d.dismissError()
                    }
                flow.declaring == actor && random.nextInt(3) == 0 -> {
                    val attackers = play.board.combat?.pairs.orEmpty().flatMap { it.attackers }.map { it.sourceId }
                    val blocker = field(play, actor).firstOrNull { what(play, it)?.creature == true }
                    if (blocker != null && attackers.isNotEmpty()) {
                        scene(live(), actor).items.firstOrNull { it.cardId == blocker && it.viewport != "combat" && it.zone == PlayZone.BATTLEFIELD }
                            ?.let { d.open(TableIntent.Actions(it.id, null), it) }
                        if (d.combat.selected == blocker) scene(live(), actor).items.firstOrNull { it.viewport == "combat" && it.sourceId == attackers.first() }
                            ?.let { d.open(TableIntent.Actions(it.id, null), it) }
                        d.combat.clearSelection(); d.dismissError()
                    }
                }
            }
            // A land or a spell now and then where the player holds priority
            val castable = hand(play, actor).filter { card -> what(play, card)?.let { w ->
                if (stackEmpty && main && actor == play.board.turn.accountId) true else w.instant } == true }
            if (flow.declaring == null && castable.isNotEmpty() && random.nextInt(5) < 2) {
                val card = castable.random(random)
                if (d.cast.start(card)) {
                    var guard = 3
                    while (d.cast.open && d.cast.targeting && guard-- > 0) {
                        val glow = d.cast.glow(live())
                        val target = items().filter { it.id in glow || it.cardId in glow }.randomOrNull(random)
                        if (target == null) { d.cast.cancel(); break }
                        d.open(TableIntent.Actions(target.id, null), target)
                    }
                    if (d.cast.open) d.cast.cast()
                    if (d.cast.open) d.cast.cancel()
                }
                d.dismissError()
                if (live() !== play) return@repeat
            }
            // Clear a staging the arrow can't declare (menace with one blocker)
            if (stepBlocked(live(), false, AutomatumBoards.combat(live()) { tags(live()) }) != null) live().let { now ->
                automatumCombatQuickActions(now, toolkitQuickContext(now, actor).copy(automatumCombat = AutomatumBoards.combat(now) { tags(now) })
                    .withCombatTarget(d.combat, now, "them"), TableActionScope(now.gameId, now.id)).orEmpty()
                    .firstOrNull { it.name == "Clear" }?.takeIf { it.enabled }?.let { d.perform(it.intent) }
            }
            probe?.invoke(live(), actor)
            tapArrow(d, actor, stats)
        }
        return stats
    }

    // TRN-063 (owner playtest 2026-11-03 #1): over seeded games the step arrow's words always say what its tap does
    @Test fun theStepArrowSaysWhatItsTapDoes() {
        val seen = mutableMapOf<String, Int>()
        var notToCombat = 0
        for (seed in listOf(3L, 11L, 29L, 47L)) {
            val stats = play(seed, actions = 260)
            println("ARROWSIM seed $seed: $stats")
            assertTrue(stats.failures.isEmpty(), "seed $seed: $stats")
            stats.labels.forEach { (k, v) -> seen.merge(k, v, Int::plus) }
            notToCombat += stats.notToCombat
        }
        // The playtest's case: a first main phase with nothing that can attack, where "To combat" ended the turn
        assertTrue(notToCombat > 0, "no main phase without attackers came up: $seen")
        for (label in listOf("To combat", "End turn", "Next", "Pass", "Attack", "No attack", "No blocks"))
            assertTrue((seen[label] ?: 0) > 0, "\"$label\" was tapped in these games: $seen")
    }

    /** A device that only measures: it acts on [board] (a copy, so nothing about it is worked out yet) and records when
     * its batch would go out, sending nothing. */
    private class Probe(val player: String, val board: PlayState, tree: () -> GameplayTree?) {
        var sentAt = 0L
        var members = 0
        var batch: GameAction.PlayBatch? = null
        val toolkit = TableToolkitController(player, { board }, { true }, { _, _ -> false },
            automatedBatch = { batch, _ -> sentAt = System.nanoTime(); members = batch.actions.size; this.batch = batch; true },
            playTree = tree)
    }

    // SYS-025 (owner playtest 2026-11-03 #6): where the step arrow's tap spends its time, before (everything worked out
    // on the tap) and after (planned in the background as the board appears, the tap sends at once); then the batch's
    // local prediction. Prints the numbers and asserts only that both taps send the same batch (no timing tests).
    @Test fun probeTheStepArrowsTap() {
        val cold = mutableListOf<Long>(); val planned = mutableListOf<Long>(); val tap = mutableListOf<Long>(); val apply = mutableListOf<Long>()
        var compared = 0
        play(29L, actions = 200) { play, actor ->
            if (number(play) < 4 || stepBlocked(play, false, AutomatumBoards.combat(play) { tags(play) }) != null) return@play
            // Before: the tap works the flow, the passes and the batch out, then sends
            val before = Probe(actor, play.copy(board = play.board.copy()), { tree })
            var start = System.nanoTime()
            if (!before.toolkit.perform(TableActionIntent.Next(TableActionScope(play.gameId, play.id))) || before.batch == null) return@play
            cold += before.sentAt - start
            // After: planned when the board appears (in the background in the app), the tap only sends
            val after = Probe(actor, play.copy(board = play.board.copy()), { tree })
            start = System.nanoTime()
            after.toolkit.planStep()
            planned += System.nanoTime() - start
            start = System.nanoTime()
            after.toolkit.perform(TableActionIntent.Next(TableActionScope(play.gameId, play.id)))
            tap += after.sentAt - start
            assertTrue(after.members == before.members, "the planned tap sends the same batch: ${after.members} vs ${before.members}")
            compared++
            // The local prediction of the batch (the device applies it as the server will, SYS-025)
            val batch = after.batch!!
            val command = GameCommand(f.id("probe/$compared"), GameScope(play.gameId, playId = play.id), batch, f.id("client"), "1")
            start = System.nanoTime()
            AutomatumOnly.change(tree!!, actor, command)
            apply += System.nanoTime() - start
        }
        fun List<Long>.ms() = if (isEmpty()) "-" else "median ${sorted()[size / 2] / 1_000_000} ms, max ${max() / 1_000_000} ms"
        println("probe step arrow ($compared taps from turn 4): before, the tap ${cold.ms()}; after, planned in the background ${planned.ms()}, " +
            "the tap ${tap.ms()}; the local prediction ${apply.ms()}")
        assertTrue(compared > 10, "taps compared: $compared")
    }
}
