package org.asyncttwiz.e2e

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.input.Input
import org.asyncttwiz.protocol.PresenceState
import org.asyncttwiz.protocol.game.GameDevice
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasContentDescription
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The core loop of a game on Alice's phone, against the server, with Bob's phone beside it (moved from the interim
 * EngineCoreLoopTest): keep, a land and a creature cast from the hand with Autotap, Undo, the same cast again, the step
 * arrow to combat, an attack staged by taps and declared by the arrow, combat damage, End turn.
 */
class CoreLoopTest : E2e() {
    /** Alice holds a Mountain and an Ember Cub; Bob a Mountain (to play in his turn and stay at seven cards). */
    private fun start() {
        startGame(deal = { a, b -> "Mountain" in hand(a) && "Ember Cub" in hand(a) && "Mountain" in hand(b) })
        bothOpen()
    }

    /** Both keep (Alice through her table's Keep), then on to Alice's Main 1. */
    private fun opening() {
        alice.tap("Keep")
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
    }

    /** Alice plays a Mountain from her hand. */
    private fun playLand(): String {
        val mountain = atw(alice.hand("Mountain").first())
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on Alice's battlefield", along = listOf(bob)) {
            mountain in alice.field("Mountain").map(::atw) && alice.showsOnBattlefield("Mountain")
        }
        return mountain
    }

    /** Bob's app leaves the game; Alice's phone hears that he is away. */
    private fun bobAway() {
        away(bob, alice)
        waitUntil("Alice seeing Bob away") { alice.lobby.state.opponentPresence?.presence?.state == PresenceState.OFFLINE }
    }

    /** The server's main line ends where Alice's phone's does, with nothing of hers unsent. */
    private fun serverTook(what: String, along: List<E2ePlayer> = emptyList()) =
        waitUntil("the server taking $what", along = along) {
            val head = alice.engine { device.session.head.takeIf { device.session.pending.isEmpty() } }
            head != null && serverGame()?.headId == head
        }

    /** Alice casts an Ember Cub from her hand with Autotap: nothing to ask, it goes at once. */
    private fun castCub(along: List<E2ePlayer> = emptyList()): String {
        assertTrue(alice.shown.settings.autotap)
        val cub = atw(alice.hand("Ember Cub").first())
        var panel = false
        alice.playFromHand("Ember Cub", this)
        waitUntil("the Cub leaving Alice's hand") {
            panel = panel || alice.shows(hasContentDescription("Cast panel")) || alice.shows(hasContentDescription("Choose targets"))
            cub !in alice.hand("Ember Cub").map(::atw)
        }
        assertFalse(panel, "the cast went out without asking anything")
        assertFalse(alice.refusalSeen, "no refusal")
        serverTook("the cast", along)
        return cub
    }

    // Problem report GXKJSE, HIS-084, STK-024: after an Undo (the next event starts a new line, its library reshuffle
    // first), the same cast with Autotap goes out again; the decisions the cast panel answered on the board are answered
    @Test fun castUndoAndTheSameCastAgain() {
        start()
        opening()
        playLand()
        bobAway()
        val cub = castCub()
        alice.tap("Undo")
        waitUntil("the Cub back in Alice's hand") { cub in alice.hand("Ember Cub").map(::atw) }
        assertFalse(alice.shows(androidx.compose.ui.test.hasTestTag("rewind-confirm")), "nothing after the cast is Bob's: the undo is free")
        assertTrue(alice.field("Mountain").none { alice.state.obj(it)?.tapped == true }, "the Mountain is untapped again")
        assertEquals(castCub(), cub)
        assertEquals(alice.engine { device.session.head }, alice.engine { device.session.localHead })
    }

    /** Plays on from Alice's first turn to her next Main 1 with a Cub ready to attack. */
    private fun toReadyCub() {
        opening()
        playLand()
        val cub = castCub(along = listOf(bob))
        waitUntil("the Cub resolving onto the battlefield", along = listOf(bob)) { cub in alice.field("Ember Cub").map(::atw) && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Alice's next Main 1 with a ready Cub", along = listOf(bob)) { alice.inOwnMain() && readyCubs().isNotEmpty() }
    }

    /** Alice's Cubs that can attack: untapped, not summoning sick. */
    private fun readyCubs() = alice.state.let { s ->
        val view = Engine.view(s)
        alice.field("Ember Cub").filter { s.obj(it)?.tapped == false && view[it]?.summoningSick == false }
    }

    // Problem report XGN86Q, TRN-057: from Main 1 with a creature ready the step arrow ("To combat") reaches declaring
    // attackers, and there it declares what the lanes hold (no panel shows at a declaration)
    @Test fun theStepArrowGoesToCombatAndDeclares() {
        start()
        toReadyCub()
        assertEquals("To combat", alice.stepArrow())
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) {
            Engine.options(alice.state, alice.me).any { it is Input.DeclareAttackers }
        }
        assertEquals(TurnStep.DeclareAttackers, alice.state.turn.step)
        assertEquals("No attack", alice.stepArrow())
        alice.tap("No attack")
        waitUntil("the attack declared", along = listOf(bob)) {
            alice.state.turn.step != TurnStep.DeclareAttackers || Engine.options(alice.state, alice.me).none { it is Input.DeclareAttackers }
        }
        assertFalse(alice.refusalSeen, "no refusal")
    }

    // The whole loop (TRN-057, HIS-084, STK-024, CMB-028): keep, land, cast with Autotap, Undo, the same cast again, To
    // combat, an attack staged and declared by the arrow, combat damage, End turn handing the turn to Bob
    @Test fun theCoreLoop() {
        start()
        opening()
        playLand()
        bobAway()
        val cub = castCub()
        alice.tap("Undo")
        waitUntil("the Cub back in Alice's hand") { cub in alice.hand("Ember Cub").map(::atw) }
        assertEquals(castCub(), cub)
        // Bob comes back and lets it resolve
        bob.back()
        waitUntil("the Cub resolving onto the battlefield", along = listOf(bob)) { cub in alice.field("Ember Cub").map(::atw) && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Alice's next Main 1 with a ready Cub", along = listOf(bob)) { alice.inOwnMain() && readyCubs().isNotEmpty() }
        val turn = alice.state.turn.number
        val ready = readyCubs()
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) { alice.state.turn.step == TurnStep.DeclareAttackers }
        // CMB-028, problem report XGN86Q: a tap on each ready Cub stages it in the lanes (the first one opens the combat
        // area in the same batch, its Place naming the area the Open made)
        for ((n, attacker) in ready.withIndex()) {
            alice.stageAttacker("Ember Cub", this, index = n)
            waitUntil("the Cub staged") { alice.shown.board.engine!!.table.attackDraft.orEmpty().size == n + 1 }
            assertFalse(alice.refusalSeen, "the Cub is staged")
        }
        assertEquals(ready.map(::atw).toSet(), alice.shown.board.engine!!.table.attackDraft.orEmpty().map { atw(it.attacker) }.toSet())
        assertEquals("Attack (${ready.size})", alice.stepArrow())
        val life = alice.state.player(bob.me).life
        alice.tap("Attack (${ready.size})")
        // Alice passes on with the arrow where she holds priority (the declare blockers step) until the damage
        waitUntil("the Cubs' damage", along = listOf(bob)) {
            val s = alice.state
            if (alice.status == GameDevice.Status.YourMove && s.priority.holder == alice.me && s.player(bob.me).life == life &&
                alice.stepArrow() in listOf("Next", "Pass")) alice.tap(alice.stepArrow()!!)
            s.player(bob.me).life < life
        }
        assertEquals(life - 2 * ready.size, alice.state.player(bob.me).life, "the Cubs' damage")
        waitUntil("Alice's Main 2", along = listOf(bob)) {
            if (alice.status == GameDevice.Status.YourMove && alice.state.turn.step != TurnStep.PostcombatMain &&
                alice.stepArrow() in listOf("Next", "Pass")) alice.tap(alice.stepArrow()!!)
            alice.status == GameDevice.Status.YourMove && alice.state.turn.step == TurnStep.PostcombatMain
        }
        alice.hold("End turn")
        waitUntil("Bob's turn", along = listOf(bob)) { alice.state.turn.number > turn }
        assertEquals(turn + 1, alice.state.turn.number, "Bob's turn")
        assertEquals(bob.me, alice.state.turn.active)
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty(), "${alice.leaks.leaks} ${bob.leaks.leaks}")
        assertTrue(alice.leaks.checked > 0)
    }
}
