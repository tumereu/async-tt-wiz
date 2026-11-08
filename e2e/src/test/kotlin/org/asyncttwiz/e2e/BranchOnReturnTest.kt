package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import arcanum.engine.input.Input
import org.asyncttwiz.android.EngineGame
import org.asyncttwiz.android.EngineShownProbe
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-06: Alice assumed Bob's blocks and played on; Bob then declared other blocks. Coming back to
 * her phone she was told "Bob has made moves · Tap to watch", which didn't say her line was taken back. Owner
 * 2026-11-07 (catch-up rules): coming back to a game whose line the other player's real answer branched while she was
 * away opens it as she left it with "Bob responded differently · Tap to watch"; her tap shows the branch (HIS-097,
 * HIS-106): the take-back to the branch point with its note; owner 2026-11-08: it stops there ("Bob responded differently
 * from here · Tap to continue"), and her next tap plays Bob's new events back.
 */
class BranchOnReturnTest : E2e() {
    /** Alice leaves the app (her phone then hears nothing until she is back; Bob is away too, so he isn't told). */
    private fun aliceLeaves() {
        alice.away()
        alice.link.cut()
    }

    @After fun noProbe() { EngineShownProbe.listener = null }

    /** What Alice's table is given from her return on: every board her game publishes (several may come between two
     * frames, [EngineShownProbe]), the first centre text it showed, and how many boards it had when she tapped it. */
    private class Seen {
        val boards = java.util.concurrent.CopyOnWriteArrayList<EngineGame.Shown>()
        var watch: String? = null
        var tapped = -1
    }

    /** Alice's app comes back from the background ([reopen] false), or starts again and opens the game from the Games
     * list ([reopen]: loaded anew from the server). */
    private fun aliceComesBack(reopen: Boolean = false): Seen {
        val seen = Seen()
        EngineShownProbe.listener = { me, shown -> if (me == alice.id) seen.boards += shown }
        everyRun += { if (seen.watch == null && alice.showsWatch()) seen.watch = alice.watchText() }
        if (reopen) {
            alice = restart(alice)
            alice.openGame(game, this)
        } else alice.back()
        return seen
    }

    /** HIS-097, HIS-106, HIS-094 (owner 2026-11-07): back, Alice's table waited as she left it ([discarded] shown) with
     * "Bob responded differently · Tap to watch", nothing moving; her tap took it back to the branch point (the take-back,
     * with timeline mode's look) with [note] and stopped there (owner 2026-11-08), her next tap played Bob's new events
     * back ([theirs]), and she is live at the new head; [discarded] is gone. */
    private fun assertBranchShown(seen: Seen, note: String, discarded: GameEvent, theirs: (GameEvent) -> Boolean) {
        waitUntil("Alice's centre text") { alice.showsWatch() && alice.current() }
        assertEquals("${bob.name} responded differently · Tap to watch", alice.watchText())
        repeat(5) { run(skip = java.time.Duration.ofMillis(600)) }
        assertTrue(seen.boards.none { it.transition?.undone != null }, "nothing taken back before her tap")
        assertTrue(seen.boards.all { s -> s.held.any { it.id == discarded.id } } && seen.boards.last().status == GameDevice.Status.CatchUp,
            "her table as she left it, waiting: ${seen.boards.map { it.status }}")
        assertEquals("${bob.name} responded differently · Tap to watch", seen.watch, "the first centre text says what waits")
        seen.tapped = seen.boards.size
        alice.tapToWatch()
        fun takeBack(): Int = seen.boards.indexOfFirst { it.transition?.undone?.branch == true && it.transition!!.backward.isNotEmpty() }
        waitUntil("Alice's table going back to the branch point") { takeBack() >= 0 }
        waitUntil("Alice told") { alice.told(note) }
        // HIS-106 (owner 2026-11-08): the take-back stops at the branch point until her next tap
        alice.waitsAtBranchStop(this, bob) { seen.boards.any { s -> s.held.any(theirs) } }
        val stopped = seen.boards.size
        alice.tapToWatch()
        waitUntil("Bob's answer played back on Alice's table, her table live at the new head") {
            !alice.shown.behind && alice.status !is GameDevice.Status.Playing && alice.shown.held.any(theirs)
        }
        val back = seen.boards[takeBack()]
        assertTrue(back.takingBack, "the take-back is shown as a branch's (HIS-106: timeline mode's look, no pause or leaving)")
        assertTrue(back.transition!!.steps.isNotEmpty(), "the take-back goes back step by step")
        val played = seen.boards.indexOfFirst { s -> s.held.any(theirs) }
        assertTrue(played > takeBack(), "the take-back first, then Bob's new events")
        assertTrue(takeBack() >= seen.tapped, "the take-back only after her tap")
        assertTrue(seen.boards.subList(seen.tapped, stopped).none { it.status == GameDevice.Status.CatchUp && !it.takingBack },
            "after her tap paused only at the branch point: ${seen.boards.map { "${it.status}/${it.takingBack}" }}")
        assertTrue(seen.boards.drop(stopped).none { it.status == GameDevice.Status.CatchUp },
            "after her tap at the branch point never paused for a centre text again: ${seen.boards.map { "${it.status}/${it.branchStop}" }}")
        assertTrue(alice.engine { device.session.events }.none { it.id == discarded.id }, "her assumed line is discarded")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // HIS-097, HIS-106, CMB-040, CMB-049 (owner playtest 2026-11-06): Alice attacks with her Ember Cub while Bob is away,
    // assumes his Ember Herald blocks it ("Assume blockers") and plays on to his turn; she leaves the app; Bob comes back
    // and, at his playback's stop at the assumed blocks, declares no blocks. Alice comes back: her table as she left it
    // with "Bob responded differently · Tap to watch"; her tap shows the branch, with "Bob blocked differently — your End
    // turn was taken back"
    @Test fun assumedBlocksAnsweredDifferentlyWaitForATapOnReturn() {
        val (assumed, noBlocks) = bobBlocksDifferentlyWhileAliceIsAway()
        val seen = aliceComesBack()
        assertBranchShown(seen, "${bob.name} blocked differently — your End turn was taken back", assumed, noBlocks)
    }

    // HIS-097, HIS-106, HIS-033 (owner 2026-11-07): as above, but Alice's app starts again and she opens the game from the
    // Games list (loaded anew): her table opens where she left it and waits for her tap, as above (a device loaded anew
    // doesn't know her End turn: the note names none)
    @Test fun assumedBlocksAnsweredDifferentlyWaitForATapWhenTheGameOpensAgain() {
        val (assumed, noBlocks) = bobBlocksDifferentlyWhileAliceIsAway()
        val seen = aliceComesBack(reopen = true)
        waitUntil("Alice's table") { seen.boards.isNotEmpty() }
        assertTrue(seen.boards.first().held.any { it.id == assumed.id }, "opened where she left it, her assumed blocks shown")
        assertBranchShown(seen, "${bob.name} blocked differently", assumed, noBlocks)
    }

    /**
     * Alice attacks with her Ember Cub while Bob is away, assumes his Ember Herald blocks it ("Assume blockers") and plays
     * on to his turn; she leaves the app; Bob comes back and, at his playback's stop at the assumed blocks, declares no
     * blocks. Alice's assumed blocks and which events are Bob's no blocks.
     */
    private fun bobBlocksDifferentlyWhileAliceIsAway(): Pair<GameEvent, (GameEvent) -> Boolean> {
        startGame(TestDeck.thirds(TestCards.cub), TestDeck.thirds(TestCards.herald), aliceSettings = PlaySettings(alwaysAssumeResolves = true),
            deal = { a, b -> "Mountain" in hand(a) && "Ember Cub" in hand(a) && "Mountain" in hand(b) && "Ember Herald" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Cub", this)
        waitUntil("the Cub on the battlefield", along = listOf(bob)) { alice.field("Ember Cub").isNotEmpty() && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain", along = listOf(alice)) { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }
        bob.playFromHand("Ember Herald", this)
        waitUntil("Bob's Herald and its token", along = listOf(alice)) {
            bob.field("Ember Herald").isNotEmpty() && bob.state.battlefield.any { bob.state.obj(it)?.token == true } && bob.inOwnMain()
        }
        bob.hold("To combat")
        waitUntil("Alice's next Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.number >= 3 }
        away(bob, alice)

        // Alice attacks and assumes Bob's Herald blocks her Cub (CMB-040: placed on the lanes as he would)
        alice.tap("To combat")
        waitUntil("Alice declaring attackers") { alice.state.turn.step == TurnStep.DeclareAttackers }
        alice.stageAttacker("Ember Cub", this)
        waitUntil("the Cub staged") { alice.stepArrow() == "Attack (1)" }
        alice.tap("Attack (1)")
        waitUntil("Alice in assume mode for Bob's blockers") { alice.showsControl("Assume no blockers") }
        alice.combatWorkedOut(this)
        alice.battlefieldCard("Ember Herald", owner = bob).performClick()
        run(skip = java.time.Duration.ofSeconds(1)) // past the double-tap window: the tap is a single tap
        // Her own attacking Cub's copy in the band (CMB-034)
        alice.nodes(hasContentDescription("Projection, Ember Cub, owner ${alice.name}, ", substring = true)).onFirst().performClick()
        waitUntil("the block placed") { alice.showsControl("Assume blockers") }
        alice.tap("Assume blockers")
        fun assumedBlocks(): GameEvent? = alice.engine { device.session.events + device.session.pending }.firstOrNull { e ->
            e.assumed?.assumedBy == alice.me && (e.assumed?.input as? Input.DeclareBlockers)?.blocks?.isNotEmpty() == true }
        waitUntil("the block assumed") { assumedBlocks() != null }
        val assumed = assumedBlocks()!!
        // She plays on: past her priority after the blocks, the combat damage, to Bob's turn
        waitUntil("Alice's turn over, all of it on the server") {
            if (alice.status == GameDevice.Status.YourMove) alice.once {
                if (alice.inOwnMain(TurnStep.PostcombatMain)) alice.stepArrow()?.let(alice::hold) else alice.stepArrow()?.let(alice::tap)
            }
            alice.state.turn.active == bob.me && alice.engine { device.session.pending.isEmpty() }
        }
        aliceLeaves()

        // Bob comes back, watches to the assumed blocks and declares no blocks instead
        bob.back()
        waitUntil("Bob's playback stopping at Alice's assumed blocks") {
            val stop = bob.status as? GameDevice.Status.Stopped
            if (bob.status == GameDevice.Status.CatchUp) bob.once { bob.tapToWatch() }
            else if (stop != null && stop.assumed !is Input.DeclareBlockers) bob.once { bob.stepArrow()?.let(bob::tap) }
            stop?.assumed is Input.DeclareBlockers
        }
        assertEquals(assumed.id, (bob.status as GameDevice.Status.Stopped).event.id)
        assertEquals("No blocks", bob.stepArrow())
        bob.tap("No blocks")
        fun noBlocks(e: GameEvent) = e.author == bob.id && e.assumed == null &&
            e.engine?.steps.orEmpty().any { (it.input as? Input.DeclareBlockers)?.blocks?.isEmpty() == true }
        waitUntil("Bob's no blocks on the server's main line") {
            bob.engine { device.session.pending.isEmpty() && device.session.events.any(::noBlocks) }
        }
        return assumed to ::noBlocks
    }

    // HIS-097, HIS-106, HIS-105: Alice casts an Ember Herald while Bob is away, assumes his pass ("Always assume
    // resolves") and ends her turn; she leaves the app; Bob comes back and, at his playback's stop at her spell, casts an
    // Idle Glimmer instead of letting it resolve. Alice comes back: her table as she left it with "Bob responded
    // differently · Tap to watch"; her tap shows the branch, with "Bob responded — your End turn was taken back"
    @Test fun anAssumedPassAnsweredWithAResponseWaitsForATapOnReturn() {
        val cast = aliceCastsAHeraldWhileBobIsAway()
        aliceLeaves()
        bob.back()
        waitUntil("Bob's playback stopping at Alice's Herald") {
            if (bob.status == GameDevice.Status.CatchUp) bob.once { bob.tapToWatch() }
            bob.status is GameDevice.Status.Stopped && bob.state.stack.isNotEmpty()
        }
        val glimmer = bob.hand("Idle Glimmer").first()
        bob.playFromHand("Idle Glimmer", this)
        fun bobsCast(e: GameEvent) = e.author == bob.id && e.engine?.steps.orEmpty().any { (it.input as? Input.Cast)?.card == glimmer }
        waitUntil("Bob's Glimmer on the server's main line") {
            bob.engine { device.session.pending.isEmpty() && device.session.events.any(::bobsCast) }
        }

        val seen = aliceComesBack()
        assertBranchShown(seen, "${bob.name} responded — your End turn was taken back", cast.next, ::bobsCast)
        assertFalse(alice.shown.behind)
    }
}
