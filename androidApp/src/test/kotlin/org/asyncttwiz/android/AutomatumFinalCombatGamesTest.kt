package org.asyncttwiz.android

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.automatum.EngineState
import org.asyncttwiz.automatum.PausePoints
import org.asyncttwiz.automatum.StepFlow
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ParsedCard
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * Final adversarial review of combat and pause points (report `arcanum-final-combat-review.md`, outside the repo): seeded
 * two-device games with combat-heavy decks played through the app's controllers. Each device runs
 * [AutomatumPauseDriver] on its view; its player goes online and offline, plays lands and creatures and casts at their
 * stops, stages attackers and assigns blockers by taps and commits them with the combat quick actions, answers choices
 * (divisions with the suggestion), and at their pauses reacts (an instant at an attacker or the other player), holds or
 * lets it go; Full control is on for some players. The server takes each device's requests onto one history in order.
 * Counted: requests the server refused, reactions lost, a game nobody moves on (stalls), controller failures, and
 * boards the engine can't take over. Invented cards (oracle templates of the cube and the sets) parsed as on a device.
 * Deterministic: time is the test clock, the devices' request ids and seeds come from [DeviceRandom] seeded with the
 * game's seed, and background work ([QueuedWork]) runs when the test drains it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AutomatumFinalCombatGamesTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    private class Invented(val face: DefinitionFace, oracle: OracleCard, extra: List<Ability> = emptyList()) {
        val submitted = CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val definition = OracleParser.parse(oracle).let { parsed ->
            arcanum.api.CardDefinition(Layout.Normal, listOf(parsed.copy(abilities = parsed.abilities + extra)))
        }
        val creature = "Creature" in face.typeLine
        val land = "Land" in face.typeLine
        val instant = face.typeLine == "Instant"
    }
    private fun invented(id: String, name: String, cost: String?, type: String, text: String, pt: String? = null,
        shown: String = text, extra: List<Ability> = emptyList()) = Invented(
        DefinitionFace(id, name, typeLine = type, manaCost = cost.orEmpty(), rulesText = shown, corner = pt.orEmpty()),
        OracleCard(name, cost, type, text, pt?.substringBefore("/"), pt?.substringAfter("/")), extra)

    // Keyword fighters (cube templates)
    private val brute = invented("brute", "Ironhide Brute", "{3}{G}", "Creature — Beast", "Trample", "5/5")
    private val squire = invented("squire", "Dawnlance Squire", "{1}{R}", "Creature — Human Knight", "First strike", "2/1")
    private val duelist = invented("duelist", "Twinblade Duelist", "{1}{R}{R}", "Creature — Human Warrior", "Double strike", "2/2")
    private val viper = invented("viper", "Viper Adept", "{1}{G}", "Creature — Snake", "Deathtouch", "1/1")
    private val acolyte = invented("acolyte", "Bloodbond Acolyte", "{1}{G}", "Creature — Vampire", "Lifelink", "2/2")
    private val hound = invented("hound", "Dusk Hound", "{1}{R}", "Creature — Dog", "Menace", "2/2")
    private val bear = invented("bear", "Stout Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val warden = invented("warden", "Gate Warden", "{2}{G}", "Creature — Human Soldier", "", "3/3",
        shown = "Gate Warden can block an additional creature each combat.",
        extra = listOf(Ability.Static(listOf(Mechanic.CanBlockAdditional(1)), Affected.Objects(Objects.Self))))
    // The sets' combat templates (GTC battalion and bloodrush, BLB attack triggers, RTR combat damage triggers and
    // unleash, DSK an attack trigger with a target)
    private val recruit = invented("recruit", "Shieldwall Recruit", "{R}", "Creature — Human Soldier",
        "Battalion — Whenever this creature and at least two other creatures attack, this creature gets +2/+2 until end of turn.", "1/1")
    private val goblin = invented("goblin", "Brandskin Goblin", "{1}{R}", "Creature — Goblin Warrior",
        "Bloodrush — {R}, Discard this card: Target attacking creature gets +2/+1 until end of turn.", "2/1")
    private val killer = invented("killer", "Quartzknife Killer", "{1}{R}", "Creature — Lizard Assassin",
        "Whenever this creature attacks, defending player loses 1 life and you gain 1 life.", "1/3")
    private val pilferer = invented("pilferer", "Pilferer of Whispers", "{2}{G}", "Creature — Human Rogue",
        "Whenever this creature deals combat damage to a player, draw a card.", "2/2")
    private val brawler = invented("brawler", "Chainhook Brawler", "{1}{R}", "Creature — Human Warrior",
        "Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)", "2/1")
    private val lurker = invented("lurker", "Clammy Lurker", "{3}{G}", "Creature — Horror",
        "Whenever this creature attacks, another target attacking creature can't be blocked this turn.", "2/5")
    private val spark = invented("spark", "Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target.")
    private val growth = invented("growth", "Wild Surge", "{G}", "Instant", "Target creature gets +3/+3 until end of turn.")
    private val mountain = invented("mountain", "Mountain", null, "Basic Land — Mountain", "")
    private val forest = invented("forest", "Forest", null, "Basic Land — Forest", "")
    private val creatures = listOf(brute, squire, duelist, viper, acolyte, hound, bear, warden, recruit, goblin, killer, pilferer, brawler, lurker)
    private val all = creatures + listOf(spark, growth, mountain, forest)

    private val aHand = f.id("a/HAND")
    private val bHand = f.id("b/HAND")
    private val aField = f.id("a/BATTLEFIELD")
    private val bField = f.id("b/BATTLEFIELD")

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

    /** Alice's main phase of turn 3, Automatum on: each player has three Mountains, two Forests and two creatures (there
     * since turn 1), a hand of five and a library of the deck in an unknown order. */
    private fun simBoard(random: kotlin.random.Random): PlayState {
        val initial = f.initial
        val keep = mutableMapOf<String, Invented>()
        fun deck(): Invented = when (random.nextInt(20)) {
            in 0..6 -> if (random.nextBoolean()) mountain else forest
            in 7..8 -> spark
            9 -> growth
            else -> creatures.random(random)
        }
        for ((hand, field) in listOf(aHand to aField, bHand to bField)) {
            initial.board.containers.single { it.id == hand }.cards.take(5).forEach { keep[it] = deck() }
            val lands = initial.board.cards.filter { it.containerId == field }.map { it.id }
            lands.take(7).forEachIndexed { i, id -> keep[id] = when (i) { 0, 1, 2 -> mountain; 3, 4 -> forest; else -> creatures.random(random) } }
        }
        for (library in initial.board.containers.filter { it.zone == PlayZone.LIBRARY }) for (id in library.cards) keep[id] = deck()
        val owner = { card: PlayCard -> initial.board.containers.single { c -> c.id == card.containerId }.accountId ?: card.owner }
        return initial.copy(definitions = initial.definitions + all.map { it.submitted },
            board = initial.board.copy(priority = MtgPriority(f.a),
                turn = TurnState(f.a, "3", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step),
                cards = initial.board.cards.filter { it.id in keep }.map { card -> keep.getValue(card.id).let { w ->
                    card.copy(owner = owner(card), definition = w.submitted.digest, faceId = w.face.faceId, corner = w.face.corner, tapped = false,
                        covered = card.containerId == aHand || card.containerId == bHand || initial.board.containers.single { it.id == card.containerId }.zone == PlayZone.LIBRARY,
                        enteredTurn = if (card.containerId == aField || card.containerId == bField) "1" else "") } },
                containers = initial.board.containers.map { it.copy(cards = it.cards.filter { id -> id in keep }) }.map {
                    if (it.zone == PlayZone.LIBRARY && it.cards.size >= 2) it.copy(layers = LibraryLayers.initial(f.id("layer/${it.id}"), it.cards)) else it }))
    }

    // ---- The server

    private var tree: GameplayTree? = null
    private var requests = 0
    private fun serve(actor: String, action: GameAction, request: String, from: String?): GameplayTreeChange {
        val history = tree!!
        val play = history.stateAt(history.currentNodeId)!!
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), action, f.id("client"), "1",
            continuation = from?.let { Continuation(it, explicit = true) })
        // As the server: its gate, then the shared reducer (pivot review #11)
        val change = AutomatumOnly.change(history, actor, command)
        change.tree?.let { tree = it }
        return change
    }

    // ---- The simulated devices

    private sealed class Plan(val at: Long, val node: String) {
        class Think(at: Long, node: String) : Plan(at, node)
        class Declare(at: Long, node: String) : Plan(at, node)
        class Answer(at: Long, node: String) : Plan(at, node)
        class React(at: Long, node: String) : Plan(at, node)
        class Hold(at: Long, node: String) : Plan(at, node)
        class Release(at: Long, node: String) : Plan(at, node)
    }

    /**
     * The device's worker for Automatum's engine work (SYS-025) as the app's [AutomatumWork.Background] runs it, but on the
     * test's own schedule: a job waits until the simulation [drain]s it (the worker finished; its result applied on the
     * main thread), so other events can arrive in between, as on a phone; the same game on every run.
     */
    private class QueuedWork : AutomatumWork {
        private val jobs = ArrayDeque<() -> Unit>()
        val pending: Boolean get() = jobs.isNotEmpty()
        override fun <T> run(work: () -> T, done: (T) -> Unit) { jobs += { done(work()) } }
        /** Runs every job, also those the results queue meanwhile. */
        fun drain() { while (jobs.isNotEmpty()) jobs.removeFirst().invoke() }
    }

    private inner class Device(val player: String, var settings: PauseSettings, val work: QueuedWork?) {
        val name = if (player == f.a) "alice" else "bob"
        var online = true
        var held: String? = null
        var view: PlayState = tree!!.stateAt(tree!!.currentNodeId)!!
        var live: PlayState = view
        val queue = mutableListOf<Triple<GameAction, String, String?>>()
        /** The node each queued request was made on (request → node), to tell what came between. */
        val madeOn = mutableMapOf<String, String>()
        /** Manual actions (lane drafts) go to the server at once, so the next tap sees them (the app's own prediction). */
        var manual: (PlayAction, String?) -> Boolean = { _, _ -> false }
        var manualBatch: (List<PlayAction>, String) -> Boolean = { _, _ -> false }
        // These games keep a cast open (its mana section, I-D47 Autotap off) while the table goes on, then Cast it
        val toolkit = TableToolkitController(player, { TableBoard.of(if (held != null) view else live) }, { true }, { action, _ -> manual(action, null) },
            playBatch = { actions, request -> manualBatch(actions, request) },
            automatedBatch = { batch, request -> queue += Triple(batch, request, held); madeOn[request] = (if (held != null) view else live).currentNodeId; true }, playTree = { tree },
            continuation = { held?.let { Continuation(it, explicit = true) } }, automatumWork = work ?: AutomatumWork.Inline).also { it.cast.autotap = false }
        val routes = object : PauseRoutes {
            override fun hold(node: String): Boolean { held = node; view = tree!!.stateAt(node)!!; return true }
            override fun holding(node: String) = held == node
            override fun releaseLive() {}
            override fun leaveHold() { held = null; view = tree!!.stateAt(tree!!.currentNodeId)!! }
        }
        init { toolkit.pause.routes = routes }
        var plan: Plan? = null
        var casting: Pair<Long, String?>? = null
        var castAt: Int? = null
        /** The point held went away (an Undo of the other player's event). */
        var holdGone = false
        var heldAtStart = false
        var lastPoint: String? = null
        /** The board [AutomatumCombatHost] last worked combat out for (with [work]). */
        var combatShown: BoardState? = null
    }

    private class Stats {
        val refused = mutableListOf<String>()
        val lost = mutableListOf<String>()
        val stalls = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val unsupported = mutableListOf<String>()
        val counts = mutableMapOf<String, Int>()
        var events = 0
        var turns = 0
        var end = "ticks"
        fun count(what: String) { counts.merge(what, 1, Int::plus) }
        override fun toString() = "events $events, turn $turns, end $end, counts $counts\n  refused ${refused.size} ${refused.take(8)}" +
            "\n  lost ${lost.size} ${lost.take(6)}\n  stalls ${stalls.size} ${stalls.take(6)}\n  failures ${failures.size} ${failures.take(6)}" +
            "\n  unsupported ${unsupported.size} ${unsupported.take(3)}"
    }

    private fun scene(play: PlayState, viewer: String) = TablePresentation.scene(f.game, TableBoard.of(play), viewer, 412f, 892f, false)

    /** [background]: each device's Automatum work runs as on a phone, off the main thread with its result later
     * ([QueuedWork]). */
    private fun simulate(seed: Long, ticks: Int, ids: Long = seed, background: Boolean = false): Stats {
        val random = kotlin.random.Random(seed)
        // The devices' request ids and seeds from the game's seed: the same game on every run
        DeviceRandom.seeded(ids)
        val start = simBoard(random)
        tree = GameplayTree(start)
        val stats = Stats()
        fun settings() = PauseSettings(responseMillis = listOf(1_000L, 2_000L, 3_000L).random(random),
            proceedAtOnce = random.nextInt(4) == 0, fullControl = random.nextInt(3) == 0)
        val devices = listOf(Device(f.a, settings(), QueuedWork().takeIf { background }), Device(f.b, settings(), QueuedWork().takeIf { background }))
        // Full control into the game as the devices' settings say (SYS-071)
        tree = GameplayTree(start.copy(board = start.board.copy(players = start.board.players.map { p ->
            p.copy(fullControl = devices.single { it.player == p.accountId }.settings.fullControl) })))
        devices.forEach { it.view = tree!!.stateAt(tree!!.currentNodeId)!!; it.live = it.view }
        val head = mutableStateOf(tree!!.stateAt(tree!!.currentNodeId)!!)
        val tick = mutableStateOf(0)
        fun other(d: Device) = devices.single { it !== d }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            tick.value
            val live = head.value
            for (d in devices) if (d.online)
                androidx.compose.runtime.key(d.player) {
                    AutomatumPauseDriver(d.toolkit, null, TableBoard.of(d.view), live.currentNodeId, liveShown = d.held == null,
                        opponentOnline = other(d).online, settings = d.settings)
                }
        }
        var now = 0L
        var changedAt = 0L
        var flippedAt = 0L
        val reactions = mutableMapOf<String, String>()
        fun liveState() = tree!!.stateAt(tree!!.currentNodeId)!!
        fun refresh() {
            val live = liveState()
            // By the board: an Undo of an earlier event replays the later ones, changing the board at the same newest event
            if (live.board !== head.value.board) { changedAt = now; stats.events++ }
            head.value = live
            for (d in devices) {
                if (d.live.board !== live.board) {
                    d.live = live
                    if (d.held == null && d.toolkit.cast.open) d.toolkit.cast.follow()
                }
                val before = d.view
                if (d.held == null) d.view = d.toolkit.pause.shown?.takeIf { it.running && it.role is PauseRole.Yours &&
                    it.node == d.view.currentNodeId && tree!!.stateAt(it.node) != null }?.let { d.view } ?: live
                else if (tree!!.stateAt(d.held!!) == null) { d.held = null; d.view = live; d.holdGone = true }
                if (d.view !== before) {
                    AutomatumBoards.compute(d.view, d.player)
                    if (d.toolkit.cast.open) d.toolkit.cast.follow()
                }
            }
            stats.turns = live.board.turn.number.toInt()
            tick.value++
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        for (d in devices) d.manual = { action, _ ->
            val change = serve(d.player, GameAction.Play(action), f.id("sim/$seed/${requests++}"), d.held)
            if (change.reason != null) stats.refused += "${change.reason} ${d.name} manual ${action.toString().take(100)}"
            refresh()
            change.reason == null
        }
        for (d in devices) d.manualBatch = { actions, request ->
            val change = serve(d.player, GameAction.PlayBatch(actions), request, d.held)
            if (change.reason != null) stats.refused += "${change.reason} ${d.name} manual batch ${actions.joinToString { it.toString().take(80) }}"
            refresh()
            change.reason == null
        }
        fun serveQueues() {
            for (d in devices) while (d.queue.isNotEmpty()) {
                val (action, request, from) = d.queue.removeAt(0)
                val change = serve(d.player, action, request, from)
                val reaction = reactions.remove(request)
                if (change.reason != null) {
                    val base = d.madeOn[request]
                    val history = tree!!
                    val between = generateSequence(history.currentNodeId) { c -> history.nodes[c]?.parentId?.takeIf { c != base } }
                        .takeWhile { it != base }.mapNotNull { history.nodes[it] }.toList().asReversed()
                        .filter { it.tag?.folded != true }.joinToString(" ") { n -> (if (n.author == f.a) "A:" else "B:") +
                            ((n.action as? PlayAction.Mtg)?.action?.let { a -> a::class.simpleName } ?: n.action::class.simpleName) }
                    stats.refused += "between: [$between]"
                    val members = (action as? GameAction.PlayBatch)?.actions.orEmpty()
                    stats.refused += "${change.reason} ${d.name}${if (from != null) " (held)" else ""}${reaction?.let { " reaction" } ?: ""}: " +
                        members.take(2).joinToString { it.toString().take(80) }
                    if (reaction != null) stats.lost += "$reaction: refused (${change.reason})"
                }
                if (from != null) d.routes.leaveHold()
                refresh()
            }
            refresh()
        }
        fun tags(play: PlayState) = engineTags(tree, play.currentNodeId)
        val workRandom = kotlin.random.Random(seed * 31 + 7)
        /** [d]'s worker catches up: its results apply, and combat on the shown board is worked out as
         * [AutomatumCombatHost] does. Nothing with inline work. */
        fun drain(d: Device) {
            val work = d.work ?: return
            work.drain()
            if (d.combatShown !== d.view.board) {
                d.combatShown = d.view.board
                d.toolkit.combat.show(TableBoard.of(d.view), AutomatumBoards.combat(d.view) { tags(d.view) })
            }
            // As SharedFlowHost: the panel's flow is the shown board's once worked out
            d.toolkit.flows.show(if (!d.toolkit.cast.open && AutomatumBoards.sharedFlowShown(d.view))
                AutomatumBoards.sharedFlow(d.view, tags(d.view)) else null)
            refresh()
            work.drain()
        }
        fun flowOf(d: Device) = AutomatumBoards.sharedFlow(d.view, tags(d.view))
        fun hand(d: Device) = d.view.board.containers.single { it.id == (if (d.player == f.a) aHand else bHand) }.cards
        fun field(play: PlayState, player: String) = play.board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == player }.cards
        fun tapItem(d: Device, item: TableItem) = d.toolkit.open(TableIntent.Actions(item.id, null), item)
        fun quick(d: Device) = automatumCombatQuickActions(TableBoard.of(d.view), toolkitQuickContext(TableBoard.of(d.view), d.player)
            .copy(automatumCombat = AutomatumBoards.combat(d.view) { tags(d.view) })
            .withCombatTarget(d.toolkit.combat, TableBoard.of(d.view), "them"), TableActionScope(d.view.gameId, d.view.id)).orEmpty()
        /** Begins casting [card]; picks a target when asked: an attacking creature of the other player when there is one,
         * else that player. False when it can't be begun. */
        fun begin(d: Device, card: String): Boolean {
            AutomatumBoards.compute(d.view, d.player)
            if (!d.toolkit.cast.start(card)) return false
            drain(d)
            var guard = 3
            while (d.toolkit.cast.open && d.toolkit.cast.targeting && guard-- > 0) {
                val items = scene(d.view, d.player).items.filter { it.viewport != "combat" }
                val glow = d.toolkit.cast.glow(TableBoard.of(d.view))
                val legal = items.filter { it.id in glow || it.cardId in glow }
                val attackers = d.view.board.combat?.pairs.orEmpty().flatMap { it.attackers }.map { it.sourceId }
                val target = legal.filter { it.cardId in attackers }.randomOrNull(random)
                    ?: legal.firstOrNull { it.id == "life/${other(d).player}" } ?: legal.randomOrNull(random)
                if (target == null) { d.toolkit.cast.cancel(); return false }
                tapItem(d, target)
                drain(d)
            }
            return d.toolkit.cast.open
        }
        /** A reaction that went nowhere: the player lets the pause they held go (as the app's Let it resolve). */
        fun release(d: Device) { d.held?.let { node -> d.plan = Plan.Release(now + 500, node) } }
        fun finish(d: Device, path: String?) {
            if (!d.toolkit.cast.open) {
                path?.let { stats.lost += "$it: dropped while casting (${d.toolkit.error}; held ${d.held != null} (at the start ${d.heldAtStart}, the point went away ${d.holdGone}), ${d.name}'s view node " +
                    "in the tree ${tree!!.stateAt(d.view.currentNodeId) != null}, undos ${stats.counts["undo"] ?: 0}, events since ${stats.events - (d.castAt ?: 0)})" }
                release(d); return
            }
            val before = d.queue.size
            val taken = d.toolkit.cast.cast()
            drain(d)
            if (!taken || d.queue.size == before) {
                path?.let { stats.lost += "$it: not sent (${d.toolkit.error})" }
                d.toolkit.cast.cancel(); release(d); return
            }
            path?.let { reactions[d.queue.last().second] = it }
        }
        fun act(d: Device, plan: Plan) {
            val live = head.value
            val there = live.currentNodeId == plan.node || d.held == plan.node || d.view.currentNodeId == plan.node
            if (!there) { stats.count("plan outdated"); return }
            drain(d)
            when (plan) {
                is Plan.Think -> {
                    val cards = hand(d).mapNotNull { id -> what(d.view, id)?.let { id to it } }
                    val land = cards.firstOrNull { it.second.land }
                    val spell = cards.filter { !it.second.land }.shuffled(random).firstOrNull()
                    val queued = d.queue.size
                    when {
                        land != null && random.nextInt(3) > 0 -> { if (begin(d, land.first)) finish(d, null); stats.count(if (d.queue.size > queued) "land" else "land refused") }
                        spell != null && random.nextInt(3) > 0 && begin(d, spell.first) -> { finish(d, null); stats.count(if (d.queue.size > queued) "cast" else "cast refused") }
                        else -> {
                            d.toolkit.flows.step(if (random.nextInt(4) == 0) StepFlow.Until.EndOfTurn else StepFlow.Until.Pass)
                            drain(d)
                            if (d.toolkit.flows.acting != null) stats.failures += "${d.name}'s Next step at a stop would act for the other player"
                            stats.count("step")
                        }
                    }
                }
                is Plan.Declare -> {
                    val flow = flowOf(d) ?: return
                    if (flow.declaring != d.player) return
                    val attacking = flow.declaring == d.view.board.turn.accountId
                    if (attacking) {
                        for (c in field(d.view, d.player)) if (what(d.view, c)?.creature == true && random.nextInt(3) > 0) {
                            scene(d.view, d.player).items.firstOrNull { it.cardId == c && it.viewport != "combat" && it.zone == PlayZone.BATTLEFIELD }?.let { tapItem(d, it) }
                            d.toolkit.dismissError()
                        }
                        // The step arrow attacks (TRN-063); greyed (a staging Automatum doesn't allow), Clear first
                        fun blocked() = stepBlocked(TableBoard.of(d.view), false, AutomatumBoards.combat(d.view) { tags(d.view) })
                        if (blocked() != null) quick(d).firstOrNull { it.name == "Clear" }?.takeIf { it.enabled }?.let { d.toolkit.perform(it.intent) }
                        val label = AutomatumStep.of(TableBoard.of(d.view)).label
                        val why = blocked()
                        if (why != null || !d.toolkit.perform(TableActionIntent.Next(TableActionScope(d.view.gameId, d.view.id))))
                            stats.failures += "attack: $label ${why ?: d.toolkit.error}"
                        else stats.count("attack ${label.substringBefore(" (")}")
                        drain(d)
                    } else {
                        val attackers = d.view.board.combat?.pairs.orEmpty().flatMap { it.attackers }.map { it.sourceId }
                        for (c in field(d.view, d.player)) if (attackers.isNotEmpty() && what(d.view, c)?.creature == true && random.nextBoolean()) {
                            scene(d.view, d.player).items.firstOrNull { it.cardId == c && it.viewport != "combat" && it.zone == PlayZone.BATTLEFIELD }?.let { tapItem(d, it) }
                            if (d.toolkit.combat.selected == c) scene(d.view, d.player).items.firstOrNull { it.viewport == "combat" && it.sourceId == attackers.random(random) }?.let { tapItem(d, it) }
                            d.toolkit.combat.clearSelection(); d.toolkit.dismissError()
                        }
                        // The step arrow declares (TRN-063): Block (N), or No blocks once Clear emptied a draft it greys
                        fun blocked() = stepBlocked(TableBoard.of(d.view), false, AutomatumBoards.combat(d.view) { tags(d.view) })
                        if (blocked() != null) quick(d).firstOrNull { it.name == "Clear" }?.takeIf { it.enabled }?.let { d.toolkit.perform(it.intent) }
                        val label = AutomatumStep.of(TableBoard.of(d.view)).label
                        val why = blocked()
                        if (why != null || !d.toolkit.perform(TableActionIntent.Next(TableActionScope(d.view.gameId, d.view.id))))
                            stats.failures += "blocks: $label ${why ?: d.toolkit.error}"
                        else stats.count(label.substringBefore(" ("))
                        drain(d)
                    }
                }
                is Plan.Answer -> {
                    val flow = flowOf(d) ?: return
                    val decision = flow.decision ?: return
                    d.toolkit.flows.show(flow)
                    if (decision.player.value != d.player) d.toolkit.flows.chooseFor(decision)
                    val answer = when (decision) {
                        is Decision.Divide -> Answer.Division(decision.suggested)
                        is Decision.ManaPayment -> Answer.ManaPayment(decision.suggestion)
                        else -> flow.listedAnswers().randomOrNull(random)
                    }
                    if (answer == null || !d.toolkit.flows.answer(answer)) stats.failures += "answer refused: ${d.toolkit.error} ($decision)"
                    else stats.count(if (decision.player.value == d.player) "answer" else "answer for them")
                    drain(d)
                }
                is Plan.React -> {
                    val cards = hand(d).filter { what(d.view, it)?.instant == true }.shuffled(random)
                    val card = cards.firstOrNull { begin(d, it) }
                    if (card == null) { stats.count("react: nothing castable"); release(d); return }
                    stats.count("react")
                    d.casting = now + random.nextLong(500, 2_500) to "reaction at ${TurnPosition.of(d.view.board.turn)}"
                    d.castAt = stats.events; d.holdGone = false; d.heldAtStart = d.held != null
                }
                is Plan.Hold -> { d.toolkit.pause.hold(plan.node); d.plan = Plan.Release(now + random.nextLong(1_000, 5_000), plan.node) }
                is Plan.Release -> { if (d.held == plan.node && !d.toolkit.pause.passBehind(plan.node)) d.toolkit.flows.pass(d.player); drain(d) }
            }
        }
        refresh()
        repeat(ticks) {
            now += 100
            compose.mainClock.advanceTimeBy(100)
            // The workers catch up now and then (an engine call takes 100–300 ms on a phone)
            for (d in devices) if (d.work?.pending == true && workRandom.nextInt(3) > 0) drain(d)
            serveQueues()
            val live = head.value
            if (live.board.players.any { (it.life.toIntOrNull() ?: 1) <= 0 }) { stats.end = "a player lost"; return stats }
            for (d in devices) {
                if (random.nextInt(200) == 0) { d.online = !d.online; flippedAt = now; tick.value++ }
                if (!d.online) continue
                try {
                    // The player switches Full control in the settings now and then (SYS-071)
                    if (random.nextInt(600) == 0) {
                        d.settings = d.settings.copy(fullControl = !d.settings.fullControl, fullControlChanged = now + 1)
                        stats.count("full control switched"); tick.value++
                    }
                    // The player takes back their latest event now and then (SYS-004)
                    if (random.nextInt(500) == 0 && d.held == null && d.plan == null && d.casting == null && !d.toolkit.cast.open) {
                        val change = serve(d.player, GameAction.Undo, f.id("sim/$seed/undo/${requests++}"), null)
                        stats.count(if (change.reason == null) "undo" else "undo refused ${change.reason}")
                        refresh()
                    }
                    val casting = d.casting
                    if (casting != null && now >= casting.first) { d.casting = null; finish(d, casting.second) }
                    if (d.casting != null) continue
                    if (d.held != null && d.plan == null && d.casting == null && !d.toolkit.cast.open && now - changedAt > 6_000) {
                        d.routes.leaveHold(); refresh(); stats.count("left a hold for live")
                    }
                    val plan = d.plan
                    if (plan != null) { if (now >= plan.at) { d.plan = null; act(d, plan) }; continue }
                    val node = d.view.currentNodeId
                    val flow = flowOf(d)
                    if (flow == null) { stats.unsupported += "turn ${d.view.board.turn.number}"; continue }
                    val decision = flow.decision
                    val since = now - maxOf(changedAt, flippedAt)
                    // An Undo can bring back a choice or a declaration already made at this point: after a while the
                    // player makes it again (by hand, as at a pause an Undo brought back)
                    val again = d.lastPoint != node || since > 10_000
                    when {
                        decision != null -> if (d.held == null && again &&
                            (decision.player.value == d.player || (!other(d).online && since > 3_000))) {
                            d.lastPoint = node; d.plan = Plan.Answer(now + random.nextLong(300, 2_000), node)
                        }
                        flow.declaring != null -> if (d.held == null && again &&
                            (flow.declaring == d.player || (!other(d).online && since > 3_000))) {
                            if (flow.declaring == d.player) { d.lastPoint = node; d.plan = Plan.Declare(now + random.nextLong(300, 2_500), node) }
                        }
                        else -> {
                            val at = PausePoints.at(d.view.board)?.takeIf { it.holder == d.player } ?: continue
                            if (at.point == PausePoints.Point.Stop) {
                                if (d.lastPoint != node || since > 3_000) { d.lastPoint = node; d.plan = Plan.Think(now + random.nextLong(300, 1_500), node) }
                            } else if (d.lastPoint != node) {
                                d.lastPoint = node
                                val roll = random.nextInt(10)
                                val delay = random.nextLong(200, 3_000)
                                if (roll < 4) d.plan = Plan.React(now + delay, node) else if (roll < 5) d.plan = Plan.Hold(now + delay, node)
                            } else if (d.held == null && since > 10_000 && devices.any { it.toolkit.pause.passedAt(node) }) {
                                // A pause an Undo brought back: no device passes it again by itself (pausereview #4); the player does
                                stats.count("pause after undo passed by hand")
                                if (!d.toolkit.pause.passBehind(node)) d.toolkit.flows.pass(d.player)
                                drain(d)
                                changedAt = now
                            } else if (d.held == null && since > 10_000) {
                                stats.stalls += "turn ${d.view.board.turn.number} ${TurnPosition.of(d.view.board.turn)} ${d.name}'s pause (by ${
                                    if (tree!!.nodes[node]?.author == f.a) "alice" else "bob"}; " + devices.joinToString { "${it.name} online ${it.online} " +
                                    "shown ${it.toolkit.pause.shown?.let { s -> "${s.role::class.simpleName} running ${s.running}" }} passed ${it.toolkit.pause.passedAt(node)}" } + ")"
                                if (!d.toolkit.pause.passBehind(node)) d.toolkit.flows.pass(d.player)
                                drain(d)
                                changedAt = now
                            }
                        }
                    }
                    // Nothing has happened for long while both are online: who should act?
                    if (since > 20_000 && devices.all { it.online } && d.player == f.a) {
                        stats.stalls += "turn ${live.board.turn.number} ${TurnPosition.of(live.board.turn)}: nothing for 20 s " +
                            "(decision ${decision?.let { it::class.simpleName + " of " + (if (it.player.value == f.a) "alice" else "bob") }}, declaring ${flow.declaring?.take(4)}, " +
                            "holder ${flow.holder?.take(4)}, point ${PausePoints.at(live.board)}, held ${devices.map { it.held != null }}, plans ${devices.map { it.plan?.let { p -> p::class.simpleName } }})"
                        changedAt = now
                    }
                } catch (problem: Throwable) {
                    stats.failures += "${d.name}: ${problem::class.simpleName}: ${problem.message?.take(300)}"
                }
            }
        }
        // The final board is one the engine takes
        runCatching { EngineState.from(liveState(), AutomatumBoards.definitions(liveState()), null, emptyList()) }
            .onFailure { stats.failures += "final board: ${it.message?.take(200)}" }
        return stats
    }

    // finalreview-combat: seeded two-device games with combat through the controllers, pauses, holds, reactions and
    // presence changes: the server refuses nothing but races, no reaction is lost, no game stalls, no controller fails
    private fun game(seed: Long, ids: Long = seed, background: Boolean = false): Stats {
        val stats = simulate(seed, ticks = 5_000, ids, background)
        println("COMBATSIM seed $seed ids $ids${if (background) " background" else ""}: head ${tree?.currentNodeId} $stats")
        assertTrue(stats.events > 30, "the game moved: $stats")
        assertTrue(stats.stalls.isEmpty(), "no game left where nobody moves it on: $stats")
        assertTrue(stats.failures.isEmpty(), "no controller failure: $stats")
        assertTrue(stats.unsupported.isEmpty(), "every board taken over: $stats")
        // A reaction is lost only when the point it was made at was taken back (the other player's Undo)
        assertTrue(stats.lost.all { "the point went away true" in it }, "no reaction lost: $stats")
        assertTrue(stats.refused.none { "reaction" in it }, "no reaction refused: $stats")
        return stats
    }

    @Test fun twoDeviceCombatGames1() { game(1L) }
    @Test fun twoDeviceCombatGames2() { game(2L) }
    @Test fun twoDeviceCombatGames3() { game(3L) }
    @Test fun twoDeviceCombatGames4() { game(4L) }
    @Test fun twoDeviceCombatGames5() { game(5L) }
    @Test fun twoDeviceCombatGames6() { game(6L) }

    // The same games with each device's Automatum work done as on a phone, off the main thread with its result later
    // (SYS-025): other events arrive between a tap or an automatic pass and its result. Deterministic: the test drains the
    // work on its own schedule, so a seed plays the same game on every run, however busy the machine is
    @Test fun twoDeviceCombatGamesWithBackgroundWork1() { game(1L, background = true) }
    @Test fun twoDeviceCombatGamesWithBackgroundWork2() { game(2L, background = true) }
    @Test fun twoDeviceCombatGamesWithBackgroundWork3() { game(3L, background = true) }
    @Test fun twoDeviceCombatGamesWithBackgroundWork4() { game(4L, background = true) }
    @Test fun twoDeviceCombatGamesWithBackgroundWork5() { game(5L, background = true) }
    @Test fun twoDeviceCombatGamesWithBackgroundWork6() { game(6L, background = true) }
}
