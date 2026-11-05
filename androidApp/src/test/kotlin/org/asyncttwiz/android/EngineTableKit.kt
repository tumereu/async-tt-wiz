package org.asyncttwiz.android

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Alice's table on a game played on engine state, wired as [EngineTableScreen] wires it: her [EngineGame] on the simulated
 * server ([EngineCombatScenario], Bob's game beside it), its [EngineTableRoutes] and the [TableToolkitController] acting
 * through them, with the table's scene ([TablePresentation.scene]) and gestures ([TableGestureCoordinator]) on her board.
 * Bob's table ([bobRoutes], [bobToolkit]) is wired the same way. The helpers play the game on to the points the tests look
 * at (Alice's Main 1, a ready creature, combat).
 */
internal class EngineTableKit(val scenario: EngineCombatScenario = EngineCombatScenario()) {
    val f = scenario.f
    val alice = scenario.alice
    val bob = scenario.bob
    val me = PlayerId(f.a)
    val other = PlayerId(f.b)
    val routes = EngineTableRoutes(f.a, { alice }, {}, { alice.play() }).apply { opponentName = "Bob" }
    val toolkit = toolkit(f.a, alice, routes)
    val bobRoutes = EngineTableRoutes(f.b, { bob }, {}, { bob.play() }).apply { opponentName = "Alice" }
    val bobToolkit = toolkit(f.b, bob, bobRoutes)
    val scope get() = TableActionScope(f.game.id, alice.play().playId)

    private fun toolkit(viewer: String, game: EngineGame, routes: EngineTableRoutes) =
        TableToolkitController(viewer, { game.play() }, { routes.canAct() && routes.review == null },
            { action, _ -> routes.local(listOf(action)) }, playBatch = { actions, id -> routes.local(actions, id) }, engine = routes)

    fun state(): GameState = alice.device.model.engine
    fun atw(id: ObjectId): String = (id as ObjectId.Card).card.value
    fun hand(name: String, player: PlayerId = me): List<ObjectId> = scenario.cards(state(), player, state().player(player).hand, name)
    fun field(name: String, player: PlayerId = me): List<ObjectId> = scenario.cards(state(), player, state().battlefield, name)

    /** [viewer]'s scene of their own table (portrait). */
    fun scene(viewer: String = f.a): TableScene =
        TablePresentation.scene(f.game, (if (viewer == f.a) alice else bob).play(), viewer, 412f, 892f, false,
            (if (viewer == f.a) toolkit else bobToolkit).layoutState.value)

    /** The table item of [card] in [viewer]'s scene: its real card (not a combat or Stack copy) in [zone]. */
    fun item(card: String, zone: PlayZone = PlayZone.BATTLEFIELD, viewer: String = f.a): TableItem =
        scene(viewer).items.first { it.kind == TableItemKind.CARD && it.sourceId == card && it.zone == zone && it.viewport != "combat" &&
            !it.projection }

    /** A pile of [owner]'s in [viewer]'s scene. */
    fun pile(zone: PlayZone, owner: String = f.a, viewer: String = f.a): TableItem =
        scene(viewer).items.first { it.zone == zone && it.owner == owner && it.kind == TableItemKind.PILE }

    /** A tap on [item] as a finger makes it on [viewer]'s table (the gesture's single tap, after the double-tap window). */
    fun tap(item: TableItem, by: TableToolkitController = toolkit, viewer: String = f.a) {
        val (gestures, _) = gestures(by, viewer)
        gestures.down(item.bounds.center, 0); gestures.up(item.bounds.center, 40); gestures.tick(1_000)
    }

    /**
     * [by]'s gestures on [viewer]'s scene, each intent dispatched as the table's surface ([TableSurface]) dispatches it:
     * a tap's actions open what it was made on, a tap on the empty table closes ability chips, drops the combat
     * selection and lowers a raised hand card, the camera stays the viewer's own, the rest goes to the toolkit. Returns
     * the intents emitted.
     */
    fun gestures(by: TableToolkitController = toolkit, viewer: String = f.a): Pair<TableGestureCoordinator, MutableList<TableIntent>> {
        val emitted = mutableListOf<TableIntent>()
        lateinit var owner: TableGestureCoordinator
        owner = TableGestureCoordinator({ intent ->
            emitted += intent
            when (intent) {
                TableIntent.DismissTapUi -> by.dismissTapUi()
                is TableIntent.Peek -> by.peek(intent.cardId)
                is TableIntent.SearchPile -> by.openPublicPile(intent.containerId, toggle = false)
                is TableIntent.Camera -> by.layoutState.value = by.layoutState.value.copy(camera = intent.camera)
                is TableIntent.Scroll, is TableIntent.Overscroll -> {}
                TableIntent.EmptyTap -> { by.closeAbilities(); by.combat.clearSelection(); by.hand.lower() }
                is TableIntent.Actions -> owner.actionItem?.let { by.open(owner.capturedActions ?: intent, it) }
                else -> by.handle(intent)
            }
        })
        owner.update(scene(viewer), true)
        return owner to emitted
    }

    /** A point of [viewer]'s battlefield with no card. */
    fun emptyPoint(viewer: String = f.a): TablePoint {
        val scene = scene(viewer)
        val field = scene.fields.first { it.owner == viewer }.bounds
        return generateSequence(0) { it + 1 }.take(40).map { TablePoint(field.x + 4f + it * 9f, field.y + 4f) }
            .first { scene.hit(it) == null }
    }

    /** A tap on the empty table, as a finger makes it. */
    fun emptyTap(by: TableToolkitController = toolkit, viewer: String = f.a) {
        val point = emptyPoint(viewer)
        val (gestures, emitted) = gestures(by, viewer)
        gestures.down(point, 0); gestures.up(point, 40); gestures.tick(1_000)
        check(TableIntent.EmptyTap in emitted) { "$emitted" }
    }

    fun doubleTap(gestures: TableGestureCoordinator, point: TablePoint) {
        gestures.down(point, 1_000); gestures.up(point, 1_040); gestures.down(point, 1_120); gestures.up(point, 1_160)
        gestures.tick(2_000)
    }

    /** A finger pressed on [from], dragged to [to] and let go. */
    fun drag(gestures: TableGestureCoordinator, from: TablePoint, to: TablePoint) {
        gestures.down(from, 0)
        gestures.move(to, 80)
        gestures.up(to, 100)
        gestures.tick(2_000)
    }

    /** What Bob does in his own Main 1 with priority and the Stack empty before passing (true: he acted). */
    var bobMain: ((GameState) -> Boolean)? = null

    /** Delivers, flushes and plays on; Bob (when [bobActs]) passes, declares no attacks and no blocks by himself (with
     * [bobMain] first in his own Main 1). */
    fun settle(bobActs: Boolean = true) {
        repeat(3) {
            scenario.settle()
            if (!bobActs || scenario.online[f.b] == false) return
            val status = bob.device.status
            val s = bob.device.model.engine
            val main = s.turn.active == other && s.turn.step == TurnStep.PrecombatMain && s.stack.isEmpty() && s.priority.holder == other
            if (main && (status == GameDevice.Status.YourMove || status is GameDevice.Status.Stopped) && bobMain?.invoke(s) == true) return@repeat
            if (status is GameDevice.Status.Stopped) { bob.act(status.assumed); return@repeat }
            if (status != GameDevice.Status.YourMove) return
            val options = Engine.options(s, other)
            val decision = s.pending?.decision
            when {
                decision != null && Engine.decider(s, decision.player) == other ->
                    options.firstOrNull { it is Input.Answer && it.decision == decision.id }?.let { bob.send(listOf(it)) }
                options.any { it is Input.DeclareAttackers } -> bob.send(listOf(Input.DeclareAttackers(other, emptyList())))
                options.any { it is Input.DeclareBlockers } -> bob.send(listOf(Input.DeclareBlockers(other, emptyList())))
                s.priority.holder == other -> bob.pass()
            }
        }
    }

    /** Settles until [done] (at most [rounds] times), failing with [what] when it never holds. */
    fun playOn(what: String, rounds: Int = 20, bobActs: Boolean = true, done: () -> Boolean) {
        repeat(rounds) { if (done()) return; settle(bobActs) }
        if (!done()) fail("$what: ${state().turn}; Alice: ${alice.device.status}; Bob: ${bob.device.status} at ${bob.device.model.engine.turn.step}")
    }

    /** Alice's own Main 1 with priority, the Stack empty. */
    fun aliceMain(s: GameState = state()) = alice.device.status == GameDevice.Status.YourMove && s.turn.active == me &&
        s.turn.step == TurnStep.PrecombatMain && s.stack.isEmpty() && s.priority.holder == me

    /** Both keep (Alice through her table's Keep), then on to Alice's Main 1. */
    fun opening() {
        assertTrue(toolkit.opening(TableActionIntent.Keep(scope)), "Alice keeps")
        bob.keep(emptyList())
        playOn("Alice's Main 1") { aliceMain() }
    }

    /** Alice keeps seven and plays nothing; in her second turn she has eight cards at her cleanup step: her discard, an
     * Automatum choice of hers Bob's table waits for. */
    fun toAliceDiscard(): arcanum.engine.decision.Decision.ChooseObjects {
        opening()
        assertTrue(toolkit.automatumStep(TableActionIntent.End(scope)), "End turn")
        playOn("Alice's second Main 1") { aliceMain() && state().turn.number >= 3 }
        kotlin.test.assertEquals(8, state().player(me).hand.size)
        assertTrue(toolkit.automatumStep(TableActionIntent.End(scope)), "End turn")
        playOn("Alice's discard") { state().pending?.decision is arcanum.engine.decision.Decision.ChooseObjects }
        val decision = kotlin.test.assertIs<arcanum.engine.decision.Decision.ChooseObjects>(state().pending?.decision)
        kotlin.test.assertEquals(me, decision.player)
        return decision
    }

    /** Alice plays a Mountain from her hand. */
    fun playLand(): String {
        val mountain = atw(hand("Mountain").first())
        assertTrue(toolkit.cast.start(mountain), "the Mountain is played")
        settle()
        assertTrue(mountain in field("Mountain").map(::atw), "the Mountain is on the battlefield")
        return mountain
    }

    /** Alice casts an Ember Cub (Autotap) and lets it resolve, back in her Main 1. */
    fun castCub(): String {
        val cub = atw(hand("Ember Cub").first())
        assertTrue(toolkit.cast.start(cub), "the cast begins: ${toolkit.error}")
        playOn("the Cub resolving") { cub in field("Ember Cub").map(::atw) && aliceMain() }
        return cub
    }

    /** From the start to Alice's next Main 1 with a Cub ready to attack (a Mountain, a Cub, End turn, Bob's turn). */
    fun toReadyCub(): String {
        opening()
        playLand()
        val cub = castCub()
        assertTrue(toolkit.automatumStep(TableActionIntent.End(scope)), "End turn")
        playOn("Alice's next Main 1 with a ready Cub") { aliceMain() && scenario.creatures(state(), me, ready = true).isNotEmpty() }
        return cub
    }

    /** Bob's Main 1 plan: a Mountain, then a Storm Spark (at the first target the engine lists). Bob's decision stops are
     * then his own to answer ([settle]). */
    fun bobCastsSpark() {
        scenario.confirmStops[f.b] = false
        bobMain = { s ->
            val land = scenario.cards(s, other, s.player(other).hand, "Mountain").firstOrNull()
            val spark = scenario.cards(s, other, s.player(other).hand, "Storm Spark").firstOrNull()
            when {
                (s.turn.landsPlayed[other] ?: 0) == 0 && land != null -> { bob.send(listOf(Input.PlayLand(other, land))); true }
                spark != null && scenario.cards(s, other, s.battlefield, "Mountain").any { s.obj(it)?.tapped == false } -> {
                    bobMain = null
                    bob.send(scenario.cast(s, other, Input.Cast(other, spark))); true
                }
                else -> false
            }
        }
    }

    /** From Alice's Main 1 on to her declare attackers step. */
    fun toAttacks() {
        assertTrue(toolkit.automatumStep(TableActionIntent.Next(scope)), "To combat")
        playOn("Alice declaring attackers") { Engine.options(state(), me).any { it is Input.DeclareAttackers } }
    }
}
