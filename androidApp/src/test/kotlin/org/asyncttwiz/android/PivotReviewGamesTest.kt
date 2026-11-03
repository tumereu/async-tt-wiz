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
 * Adversarial review of the Automatum-always-on pivot (I-D52; report `arcanum-pivot-review.md`, outside the repo): seeded
 * two-device games played through the app's controllers from the opening to a result, every request through the server's
 * gate ([AutomatumOnly]) before the shared reducer, as `GameService` takes them. The devices mulligan and keep (TRN-062),
 * then play as in [AutomatumFinalCombatGamesTest] (lands, creatures, casts at stops and reactions at pauses, staged
 * attacks and blocks, answers, holds, presence changes, Undo); now and then a player suggests a draw, which the other
 * declines or accepts (TRN-046); a game ends when a player loses by the rules (then they concede, TRN-064, since the
 * rules' result doesn't end it: pivotreview #1), a draw is accepted, or a player concedes at the end. Counted: requests
 * the server refused (the gate's refusals separately), stalls, controller failures, boards the engine can't take over.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PivotReviewGamesTest {
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

    /** A new game as the server starts it (I-D49): 40-card decks of the invented cards in an unknown order, seven drawn
     * into each hand, both players "Mulliganing", Alice starting in her first untap step. */
    private fun simBoard(random: kotlin.random.Random): PlayState {
        fun deck(): Invented = when (random.nextInt(20)) {
            in 0..7 -> if (random.nextBoolean()) mountain else forest
            in 8..9 -> spark
            10 -> growth
            else -> creatures.random(random)
        }
        val cards = mutableListOf<PlayCard>()
        val containers = mutableListOf<PlayContainer>()
        for (player in listOf(f.a, f.b)) {
            val name = if (player == f.a) "a" else "b"
            val ids = List(40) { f.id("pivotsim/$player/card/$it") }
            val drawn = ids.take(7)
            val rest = ids - drawn.toSet()
            ids.forEach { id -> val w = deck(); cards += PlayCard(id, w.submitted.digest, player,
                f.id("$name/${if (id in drawn) "HAND" else "LIBRARY"}"), w.face.faceId, corner = w.face.corner) }
            for (zone in PlayZone.entries.filter { it != PlayZone.STACK }) containers += PlayContainer(f.id("$name/$zone"), zone, player,
                when (zone) { PlayZone.LIBRARY -> rest; PlayZone.HAND -> drawn; else -> emptyList() },
                layers = if (zone == PlayZone.LIBRARY) LibraryLayers.initial(f.id("pivotsim/$player/layer"), rest) else emptyList())
        }
        containers += PlayContainer(f.id("pivotsim/STACK"), PlayZone.STACK)
        val seed = RandomV1.encodeSeed(random.nextBytes(32))
        val initial = f.initial
        return PlayState(initial.gameId, initial.id, initial.preparationRound, initial.startRequestId, initial.root, initial.root.id,
            BoardState(listOf(f.a, f.b).map { PlayPlayer(it, status = PlayerStatus.OPENING) }, cards.sortedBy { it.id }, containers, TurnState(f.a)),
            all.map { it.submitted }, listOf(f.a, f.b).map { p -> InitialShuffle(p, seed, cards.filter { it.owner == p }.map { it.id }) },
            InitialStarter(StarterChoice(f.a), f.a))
    }

    // ---- The server

    private var tree: GameplayTree? = null
    private var requests = 0
    /** Requests the server's gate refused (I-D52: a change by hand). */
    private val gateRefused = mutableListOf<String>()
    private fun serve(actor: String, action: GameAction, request: String, from: String?): GameplayTreeChange {
        val history = tree!!
        val play = history.stateAt(history.currentNodeId)!!
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), action, f.id("client"), "1",
            continuation = from?.let { Continuation(it, explicit = true) })
        // As GameService: the gate (I-D52), then the shared reducer
        val change = AutomatumOnly.change(history, actor, command)
        if (change.reason == RuleRefusals.AUTOMATUM_ONLY) gateRefused += "${if (actor == f.a) "alice" else "bob"}: ${action.toString().take(160)}"
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
        val toolkit = TableToolkitController(player, { if (held != null) view else live }, { true }, { action, _ -> manual(action, null) },
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

    private fun scene(play: PlayState, viewer: String) = TablePresentation.scene(f.game, play, viewer, 412f, 892f, false)

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
                    AutomatumPauseDriver(d.toolkit, null, d.view, live.currentNodeId, liveShown = d.held == null && live.board.resultSuggestion == null,
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
                d.toolkit.combat.show(d.view, AutomatumBoards.combat(d.view) { tags(d.view) })
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
        fun quick(d: Device) = automatumCombatQuickActions(d.view, toolkitQuickContext(d.view, d.player)
            .copy(automatumCombat = AutomatumBoards.combat(d.view) { tags(d.view) })
            .withCombatTarget(d.toolkit.combat, d.view, "them"), TableActionScope(d.view.gameId, d.view.id)).orEmpty()
        /** Begins casting [card]; picks a target when asked: an attacking creature of the other player when there is one,
         * else that player. False when it can't be begun. */
        fun begin(d: Device, card: String): Boolean {
            AutomatumBoards.compute(d.view, d.player)
            if (!d.toolkit.cast.start(card)) return false
            drain(d)
            var guard = 3
            while (d.toolkit.cast.open && d.toolkit.cast.targeting && guard-- > 0) {
                val items = scene(d.view, d.player).items.filter { it.viewport != "combat" }
                val glow = d.toolkit.cast.glow(d.view)
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
                        fun blocked() = stepBlocked(d.view, false, AutomatumBoards.combat(d.view) { tags(d.view) })
                        if (blocked() != null) quick(d).firstOrNull { it.name == "Clear" }?.takeIf { it.enabled }?.let { d.toolkit.perform(it.intent) }
                        val label = AutomatumStep.of(d.view).label
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
                        fun blocked() = stepBlocked(d.view, false, AutomatumBoards.combat(d.view) { tags(d.view) })
                        if (blocked() != null) quick(d).firstOrNull { it.name == "Clear" }?.takeIf { it.enabled }?.let { d.toolkit.perform(it.intent) }
                        val label = AutomatumStep.of(d.view).label
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
            val loser = live.board.players.firstOrNull { (it.life.toIntOrNull() ?: 1) <= 0 }?.accountId
            if (loser != null) {
                // CR 104.3b: the rules ended the game; ATW's play goes on (pivotreview #1): the loser concedes (TRN-064)
                val flow = AutomatumBoards.sharedFlow(live, tags(live))
                if (flow?.holder != null || flow?.declaring != null) stats.failures += "the engine goes on after a loss"
                if (live.board.resultSuggestion == null) stats.count("rules' result not recorded")
                concede(loser, stats)
                stats.end = "lost by the rules, conceded"; return stats
            }
            val suggestion = live.board.resultSuggestion
            if (suggestion != null) {
                // TRN-046: the other player answers a waiting suggestion; nobody plays meanwhile (TRN-049)
                if (now - changedAt > 1_500) {
                    val other = if (suggestion.author == f.a) f.b else f.a
                    if ((live.board.turn.number.toIntOrNull() ?: 0) >= 12 && random.nextInt(3) == 0) { stats.end = "draw accepted"; stats.count("draw accepted"); return stats }
                    if (serve(other, GameAction.Play(PlayAction.DeclineResult(suggestion.id)), f.id("sim/$seed/decline/${requests++}"), null).reason == null)
                        stats.count("draw declined") else stats.failures += "decline refused"
                    refresh(); changedAt = now
                }
                return@repeat
            }
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
                    // TRN-062: the device's player mulligans or keeps (both at the same time)
                    if (PlayerStatus.anyOpening(d.view.board)) {
                        if (AutomatumDrags.opening(d.view.board, d.player) && random.nextInt(8) == 0) {
                            val scope = TableActionScope(d.view.gameId, d.view.id)
                            val taken = AutomatumOpening.taken(d.view.board, d.player)
                            val mulligan = taken < 2 && random.nextInt(3) == 0
                            val ok = if (mulligan) d.toolkit.opening(TableActionIntent.OpeningMulligan(scope))
                                else d.toolkit.opening(TableActionIntent.Keep(scope)) && (!d.toolkit.keeping ||
                                    d.toolkit.confirmKeep(hand(d).take(AutomatumOpening.owed(d.view.board, d.player))))
                            if (!ok) stats.failures += "opening: ${d.toolkit.error}" else stats.count(if (mulligan) "mulligan" else "keep")
                            refresh(); changedAt = now
                        }
                        continue
                    }
                    // TRN-046: now and then a player suggests a draw
                    if (random.nextInt(1_500) == 0 && d.held == null && d.plan == null && !d.toolkit.cast.open && head.value.board.resultSuggestion == null) {
                        if (serve(d.player, GameAction.Play(PlayAction.SuggestResult(SuggestedResult.DRAW)), f.id("sim/$seed/suggest/${requests++}"), null).reason == null)
                            stats.count("draw suggested") else stats.failures += "suggestion refused"
                        refresh(); changedAt = now
                        continue
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
                    // The first turn begins: the starting player's step arrow at the untap step, where nobody holds priority
                    if (decision == null && flow.declaring == null && flow.holder == d.player && d.view.board.priority == null && d.held == null) {
                        if (since > 500) { if (!d.toolkit.flows.step(StepFlow.Until.Pass)) stats.failures += "first step: ${d.toolkit.error}"; drain(d); changedAt = now }
                        continue
                    }
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
        // Nobody won by the rules within the ticks: a player concedes (TRN-064)
        if (stats.end == "ticks") { concede(listOf(f.a, f.b).random(random), stats); stats.end = "conceded" }
        // The final board is one the engine takes
        runCatching { EngineState.from(liveState(), AutomatumBoards.definitions(liveState()), null, emptyList()) }
            .onFailure { stats.failures += "final board: ${it.message?.take(200)}" }
        return stats
    }

    // finalreview-combat: seeded two-device games with combat through the controllers, pauses, holds, reactions and
    // presence changes: the server refuses nothing but races, no reaction is lost, no game stalls, no controller fails
    /** [player] concedes (CR 104.3a, TRN-064): the server's concession batch through the gate, as `GameService` sends
     * it, ends with their own suggested loss, which the server then accepts. */
    private fun concede(player: String, stats: Stats) {
        val board = tree!!.stateAt(tree!!.currentNodeId)!!.board
        val change = serve(player, GameAction.PlayBatch(GameResultRules.concession(board, player)), f.id("concede/${requests++}"), null)
        if (change.reason != null) { stats.failures += "concession refused: ${change.reason}"; return }
        val suggestion = tree!!.stateAt(tree!!.currentNodeId)!!.board.resultSuggestion
        if (suggestion?.author != player || suggestion.outcome != SuggestedResult.LOSS) stats.failures += "concession left $suggestion"
    }

    private fun game(seed: Long, ids: Long = seed, background: Boolean = false): Stats {
        gateRefused.clear()
        val stats = simulate(seed, ticks = 5_000, ids, background)
        println("PIVOTSIM gate refused ${gateRefused.size} ${gateRefused.take(6)}")
        println("PIVOTSIM seed $seed ids $ids${if (background) " background" else ""}: head ${tree?.currentNodeId} $stats")
        assertTrue(stats.events > 30, "the game moved: $stats")
        assertTrue(gateRefused.isEmpty(), "the server's gate refused nothing the app sent: $gateRefused")
        assertTrue((stats.counts["keep"] ?: 0) >= 2, "both players kept: $stats")
        assertTrue(stats.stalls.isEmpty(), "no game left where nobody moves it on: $stats")
        assertTrue(stats.failures.isEmpty(), "no controller failure: $stats")
        assertTrue(stats.unsupported.isEmpty(), "every board taken over: $stats")
        // A reaction is lost only when the point it was made at was taken back (the other player's Undo)
        assertTrue(stats.lost.all { "the point went away true" in it }, "no reaction lost: $stats")
        assertTrue(stats.refused.none { "reaction" in it }, "no reaction refused: $stats")
        return stats
    }

    // TRN-062, TRN-064, TRN-046, SYS-073 (I-D52)
    @Test fun gamesFromTheOpeningToAResult1() { game(11L) }
    @Test fun gamesFromTheOpeningToAResult2() { game(12L) }
    @Test fun gamesFromTheOpeningToAResult3() { game(13L) }
    @Test fun gamesFromTheOpeningToAResult4() { game(15L) }
    @Test fun gamesFromTheOpeningToAResultWithBackgroundWork1() { game(11L, background = true) }
    @Test fun gamesFromTheOpeningToAResultWithBackgroundWork2() { game(14L, background = true) }
}
