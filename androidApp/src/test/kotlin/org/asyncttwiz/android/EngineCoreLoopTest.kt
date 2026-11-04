package org.asyncttwiz.android

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The core loop of a game on engine state through the app's own paths: Alice's table ([TableToolkitController] on the
 * [EngineTableRoutes] of her [EngineGame], as the table screen wires them) on the simulated server ([EngineAppSim]) with
 * Bob's game beside it: keep, a land and a creature cast from the hand through the cast panel with Autotap, Undo, the
 * same cast again, the step arrow to combat, an attack staged in the lanes and declared by the arrow, End turn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EngineCoreLoopTest {
    private val scenario = EngineCombatScenario()
    private val f = scenario.f
    private val alice = scenario.alice
    private val bob = scenario.bob
    private val me = PlayerId(f.a)
    private val other = PlayerId(f.b)
    private val routes = EngineTableRoutes(f.a, { alice }, {}, { alice.play() }).apply { opponentName = "Bob" }
    private val toolkit = TableToolkitController(f.a, { alice.play() }, { routes.canAct() }, { action, _ -> routes.local(listOf(action)) },
        playBatch = { actions, requestId -> routes.local(actions, requestId) }, engine = routes)
    private val scope get() = TableActionScope(f.game.id, alice.play().playId)

    /** A tap on Alice's battlefield card [card] as the table makes it (its item in the scene). */
    private fun tap(card: String): Boolean {
        val scene = TablePresentation.scene(f.game, alice.play(), f.a, 412f, 892f, false)
        val item = scene.items.firstOrNull { it.kind == TableItemKind.CARD && it.sourceId == card && it.zone == org.asyncttwiz.protocol.PlayZone.BATTLEFIELD &&
            it.viewport != "combat" } ?: return false
        toolkit.open(TableIntent.Actions(item.id, null), item)
        return true
    }

    @After fun forget() = AutomatumBoards.reset()

    private fun state(): GameState = alice.device.model.engine
    private fun atw(id: ObjectId): String = (id as ObjectId.Card).card.value
    private fun hand(name: String): List<ObjectId> = scenario.cards(state(), me, state().player(me).hand, name)
    private fun field(name: String): List<ObjectId> = scenario.cards(state(), me, state().battlefield, name)

    /** Delivers, flushes and plays on; Bob (when [bobActs]) passes, declares no attacks and no blocks by himself. */
    private fun settle(bobActs: Boolean = true) {
        repeat(3) {
            scenario.settle()
            if (!bobActs || scenario.online[f.b] == false || bob.device.status != GameDevice.Status.YourMove) return
            val s = bob.device.model.engine
            val options = Engine.options(s, other)
            val decision = s.pending?.decision
            when {
                // Bob's own choices (a discard to hand size): the first the engine lists
                decision != null && Engine.decider(s, decision.player) == other ->
                    options.firstOrNull { it is Input.Answer && it.decision == decision.id }?.let { bob.send(listOf(it)) }
                options.any { it is Input.DeclareAttackers } -> bob.send(listOf(Input.DeclareAttackers(other, emptyList())))
                options.any { it is Input.DeclareBlockers } -> bob.send(listOf(Input.DeclareBlockers(other, emptyList())))
                s.priority.holder == other -> bob.pass()
            }
        }
    }

    /** Settles until [done] (at most [rounds] times), failing with [what] when it never holds. */
    private fun playOn(what: String, rounds: Int = 20, done: () -> Boolean) {
        repeat(rounds) { if (done()) return; settle() }
        if (!done()) fail("$what: ${state().turn}; Bob: ${bob.device.status} at ${bob.device.model.engine.turn.step}")
    }

    /** Alice's own Main 1 with priority, the Stack empty. */
    private fun aliceMain(s: GameState = state()) = alice.device.status == GameDevice.Status.YourMove && s.turn.active == me &&
        s.turn.step == TurnStep.PrecombatMain && s.stack.isEmpty() && s.priority.holder == me

    /** Both keep (Alice through her table's Keep), then on to Alice's Main 1. */
    private fun opening() {
        assertTrue(toolkit.opening(org.asyncttwiz.android.TableActionIntent.Keep(scope)), "Alice keeps")
        bob.keep(emptyList())
        playOn("Alice's Main 1") { aliceMain() }
    }

    /** Alice plays a Mountain from her hand (the cast panel plays a land at once). */
    private fun playLand() {
        val mountain = hand("Mountain").first()
        assertTrue(toolkit.cast.start(atw(mountain)), "the Mountain is played")
        settle()
        assertTrue(atw(mountain) in field("Mountain").map(::atw), "the Mountain is on the battlefield")
    }

    /** Alice casts an Ember Cub from her hand through the cast panel with Autotap: nothing to ask, it goes at once. */
    private fun castCub(): String {
        assertTrue(toolkit.cast.autotap)
        val cub = atw(hand("Ember Cub").first())
        assertTrue(toolkit.cast.start(cub), "the cast begins")
        assertNull(toolkit.error, "no refusal")
        assertTrue(!toolkit.cast.open, "the cast went out without asking anything")
        settle()
        assertTrue(cub !in hand("Ember Cub").map(::atw), "the Cub left the hand")
        assertEquals(scenario.sim.head, alice.device.session.head, "the server took the cast")
        assertTrue(alice.device.session.pending.isEmpty())
        return cub
    }

    // Problem report GXKJSE, HIS-084, STK-024: after an Undo (the next event starts a new line, its library reshuffle
    // first), the same cast with Autotap goes out again; the decisions the cast panel answered on the board are answered
    @Test fun castUndoAndTheSameCastAgain() {
        opening()
        playLand()
        scenario.online[f.b] = false
        val cub = castCub()
        assertIs<GameDevice.Undo.Free>(alice.undo(), "nothing after the cast is Bob's")
        settle(bobActs = false)
        assertTrue(cub in hand("Ember Cub").map(::atw), "the Cub is back in Alice's hand")
        assertTrue(field("Mountain").none { state().obj(it)?.tapped == true }, "the Mountain is untapped again")
        assertEquals(castCub(), cub)
        assertEquals(scenario.sim.head, alice.device.session.head, "the server took the cast")
        assertEquals(alice.device.session.head, alice.device.session.localHead)
    }

    /** Plays on from Alice's first turn to her next Main 1 with a Cub ready to attack. */
    private fun toReadyCub() {
        opening()
        playLand()
        val cub = castCub()
        playOn("the Cub resolving onto the battlefield") { cub in field("Ember Cub").map(::atw) && aliceMain() }
        assertTrue(toolkit.automatumStep(TableActionIntent.End(scope)), "End turn")
        playOn("Alice's next Main 1 with a ready Cub") { aliceMain() && scenario.creatures(state(), me, ready = true).isNotEmpty() }
    }

    // Problem report XGN86Q, TRN-057: from Main 1 with a creature ready the step arrow ("To combat") reaches declaring
    // attackers, and there it declares what the lanes hold (no panel shows at a declaration)
    @Test fun theStepArrowGoesToCombatAndDeclares() {
        toReadyCub()
        assertEquals(AutomatumStep.ToCombat, AutomatumStep.of(alice.play()))
        assertTrue(toolkit.automatumStep(TableActionIntent.Next(scope)), "To combat")
        settle()
        assertTrue(Engine.options(state(), me).any { it is Input.DeclareAttackers }, "Alice declares attackers: ${state().turn}")
        assertEquals(TurnStep.DeclareAttackers, state().turn.step)
        assertEquals(AutomatumStep.Attack(0), AutomatumStep.of(alice.play()))
        assertTrue(toolkit.automatumStep(TableActionIntent.Next(scope)), "No attack")
        assertNull(toolkit.error)
        settle()
        assertTrue(state().turn.step != TurnStep.DeclareAttackers || Engine.options(state(), me).none { it is Input.DeclareAttackers },
            "the attack was declared: ${state().turn}")
    }

    // The whole loop (TRN-057, HIS-084, STK-024, CMB-028): keep, land, cast with Autotap, Undo, the same cast again, To
    // combat, an attack staged and declared by the arrow, combat damage, End turn handing the turn to Bob
    @Test fun theCoreLoop() {
        opening()
        playLand()
        scenario.online[f.b] = false
        val cub = castCub()
        assertIs<GameDevice.Undo.Free>(alice.undo())
        settle(bobActs = false)
        assertEquals(castCub(), cub)
        // Bob comes back and lets it resolve
        scenario.online[f.b] = true
        playOn("the Cub resolving onto the battlefield") { cub in field("Ember Cub").map(::atw) && aliceMain() }
        assertTrue(toolkit.automatumStep(TableActionIntent.End(scope)), "End turn")
        playOn("Alice's next Main 1 with a ready Cub") { aliceMain() && scenario.creatures(state(), me, ready = true).isNotEmpty() }
        val turn = state().turn.number
        val ready = scenario.creatures(state(), me, ready = true)
        assertTrue(toolkit.automatumStep(TableActionIntent.Next(scope)), "To combat")
        settle()
        assertEquals(TurnStep.DeclareAttackers, state().turn.step)
        // CMB-028, problem report XGN86Q: a tap on each ready Cub stages it in the lanes (the first one opens the combat
        // area in the same batch, its Place naming the area the Open made)
        for (attacker in ready) {
            assertTrue(tap(atw(attacker)), "the Cub is tapped on the table")
            assertNull(toolkit.error, "the Cub is staged")
            settle()
        }
        assertEquals(ready.map(::atw).toSet(), alice.device.model.table.attackDraft.orEmpty().map { atw(it.attacker) }.toSet())
        assertEquals(AutomatumStep.Attack(ready.size), AutomatumStep.of(alice.play()))
        val life = state().player(other).life
        assertTrue(toolkit.automatumStep(TableActionIntent.Next(scope)), "Attack (${ready.size})")
        assertNull(toolkit.error)
        // Alice passes on with the arrow where she holds priority (the declare blockers step) until the damage
        playOn("the Cubs' damage") {
            if (alice.device.status == GameDevice.Status.YourMove && state().priority.holder == me && state().player(other).life == life)
                assertTrue(toolkit.automatumStep(TableActionIntent.Next(scope)), "Next")
            state().player(other).life < life
        }
        assertEquals(life - 2 * ready.size, state().player(other).life, "the Cubs' damage")
        playOn("Alice's Main 2") { alice.device.status == GameDevice.Status.YourMove && state().turn.step == TurnStep.PostcombatMain }
        assertTrue(toolkit.automatumStep(TableActionIntent.End(scope)), "End turn")
        playOn("Bob's turn") { state().turn.number > turn }
        assertEquals(turn + 1, state().turn.number, "Bob's turn")
        assertEquals(other, state().turn.active)
        assertTrue(scenario.sim.leaks.isEmpty())
    }
}
