package org.asyncttwiz.e2e

import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * SYS-011 (owner, 2026-11-04; moved from the interim SameDecisionTapTest): an own action tapped on a board the phone
 * has already moved on from (the other player's event arrived, not on screen yet) is made on the newer board when the
 * same decision still waits for the player there, and refused with SYS-012's note when the board changed in a way that
 * matters. Alice's phone holds what its engine thread shows ([HeldWorker]) while the newer board arrives. The rules of
 * sameness themselves, the server race with the refusal arriving before the revision (an order one socket never
 * delivers) and a move lost to the other player's concession (on the server it ends the play and the table closes, so
 * nothing is left to tell) stay unit tests in the app (SameDecisionRulesTest).
 */
class SameDecisionTapTest : E2e() {
    private val held = HeldWorker()
    private val deck = TestDeck("Sparks", listOf(TestCards.mountain to 22, TestCards.spark to 12, TestCards.glimmer to 6))

    /** With [bobGlimmer] Bob holds an Idle Glimmer too, and his playback's Full control is on (it stops at every pass
     * assumed for him, HIS-095). */
    private fun start(alice: PlaySettings? = null, bobGlimmer: Boolean = false) {
        startGame(deck, deck, aliceSettings = alice, aliceWorker = held,
            bobSettings = if (bobGlimmer) PlaySettings(playbackFullControl = true) else null,
            deal = { a, b -> "Mountain" in hand(a) && "Storm Spark" in hand(a) && "Idle Glimmer" in hand(a) && "Mountain" in hand(b) &&
                (!bobGlimmer || "Idle Glimmer" in hand(b)) })
        held.observe = ::watchNotes
        bothOpen()
    }

    /** Bob keeps and goes away, Alice keeps (his passes assumed) and stands in her first main phase with priority, after
     * playing a Mountain when [land]. */
    private fun aliceInMain(land: Boolean = false) {
        bob.tap("Keep")
        waitUntil("Bob's keep on Alice's table") { alice.shown.held.any { it.author == bob.id } }
        away(bob, alice)
        alice.tap("Keep")
        waitUntil("Alice's Main 1") { alice.inOwnMain() }
        if (land) {
            alice.playFromHand("Mountain", this)
            waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        }
        assertEquals(TurnStep.PrecombatMain, alice.state.turn.step)
        assertEquals(GameDevice.Status.YourMove, alice.status)
    }

    /** Bob comes back and is shown what Alice's phone assumed for him (his passes): the server takes his local head,
     * which reaches Alice's engine as a revision that changes nothing in the game (her table holds its board). */
    private fun bobConfirms() {
        waitUntil("all of Alice's on the server") { alice.engine { device.session.pending.isEmpty() } }
        val heads = alice.engine { device.localHeads[bob.me] }
        bob = restart(bob)
        bob.openGame(game, this)
        waitUntil("Bob confirming his assumed passes", along = listOf(bob)) { alice.engine { device.localHeads[bob.me] } != heads }
    }

    /** Spark's cast with Alice's own answers (its first target, the suggested payment), as the cast panel makes it. */
    private fun spark(s: GameState): List<Input> {
        val cast = assertNotNull(Engine.options(s, alice.me).filterIsInstance<Input.Cast>().firstOrNull(), "a Storm Spark castable")
        val copy = s.deepCopy()
        val inputs = mutableListOf<Input>(cast)
        var input: Input = cast
        repeat(10) {
            Engine.perform(copy, input).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: return inputs
            if (d is Decision.Random || Engine.decider(copy, d.player) != alice.me) return inputs
            input = if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
            else Engine.options(copy, alice.me).filterIsInstance<Input.Answer>().filter { it.decision == d.id }.first()
            inputs += input
        }
        return inputs
    }

    private fun sameDecisionLogged(what: String) = AppLog.text().lines().any { "Own $what made on board" in it && "the same decision" in it }
    /** Every note Alice's table was given (each once, before the table shows and drops it). */
    private val told = mutableListOf<GameDevice.Note>()
    private val seenNotes = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<GameDevice.Note, Boolean>())
    private fun watchNotes() { (alice.game ?: alice.last)?.notes?.forEach { if (seenNotes.add(it)) told += it } }
    private val notes: List<GameDevice.Note> get() { watchNotes(); return told.toList() }

    // SYS-011: Alice taps To combat (her pass) in her main phase while Bob's confirmation of the passes assumed for him
    // arrives and rebuilds her board: the same decision still waits for her there, so her pass is made on the newer board
    @Test fun aPassTappedWhileTheOpponentsPassArrivesIsMade() {
        start()
        aliceInMain()
        val shown = alice.shown
        held.holding = true
        bobConfirms()
        assertEquals(shown.version, alice.shown.version, "the table still shows the board from before Bob's revision")
        alice.tap("To combat")
        held.release()
        assertTrue(notes.none { it is GameDevice.Note.Refused }, "no refusal note: $notes")
        assertTrue(sameDecisionLogged("pass"), "made on the newer board as the same decision")
        waitUntil("her turn on") { alice.state.turn.let { it.step to it.number } != (TurnStep.PrecombatMain to 1) }
        assertFalse(alice.refusalSeen)
    }

    // SYS-011: Alice casts Storm Spark while his revision arrives that changes nothing in the game: the cast, its target and
    // its payment are the same decision on the newer board, so it is made there
    @Test fun aCastWhileTheOpponentsMoveChangesNothingRelevantIsMade() {
        start()
        aliceInMain(land = true)
        val inputs = spark(alice.state)
        held.holding = true
        bobConfirms()
        val acted = alice.game!!.send(inputs)
        held.release()
        assertFalse(acted is GameDevice.Acted.Refused, "her cast is taken: $acted")
        assertTrue(notes.none { it is GameDevice.Note.Refused }, "no refusal note: $notes")
        assertTrue(sameDecisionLogged("send"), "made on the newer board as the same decision")
        waitUntil("Storm Spark cast") { alice.engine { (device.session.events + device.session.pending) }
            .any { e -> e.engine?.steps.orEmpty().any { it.input is Input.Cast } } }
    }

    // SYS-011, TRN-070 (analysis 2026-11-05; flaky AssumeBySettingTest): Alice casts Storm Spark while Bob is away, so her
    // table waits for him with Assume; the server's confirmation of her cast reaches her phone's engine (a board published
    // again, the same game) but not her screen yet when she taps Assume: the board she tapped on was never moved past, so
    // her Assume is made, with no note
    @Test fun anAssumeTappedWhileHerOwnCastIsConfirmedIsMade() {
        start()
        aliceInMain(land = true)
        alice.link.holdIncoming()
        assertFalse(alice.game!!.send(spark(alice.state)) is GameDevice.Acted.Refused)
        waitUntil("Alice waiting for Bob with Assume, her cast on its way") {
            (alice.status as? GameDevice.Status.Waiting)?.offer == GameDevice.Offer.AssumeResolves &&
                alice.engine { device.session.pending.isNotEmpty() }
        }
        fun assumed() = alice.engine { (device.session.events + device.session.pending).count { it.assumed?.assumedBy == alice.me } }
        val before = assumed()
        val shown = alice.shown
        held.holding = true
        alice.link.releaseIncoming()
        waitUntil("the server's confirmation of her cast on her phone") { alice.engine { device.session.pending.isEmpty() } }
        assertEquals(shown.version, alice.shown.version, "the table still shows the board from before the confirmation")
        alice.tap("Assume")
        held.release()
        assertTrue(notes.none { it is GameDevice.Note.Refused }, "no refusal note: $notes")
        waitUntil("the Spark resolved on her assumption of Bob's pass") {
            alice.state.stack.isEmpty() && assumed() > before
        }
        assertFalse(alice.refusalSeen)
    }

    // SYS-011, SYS-012: Alice casts Storm Spark on her main phase while Bob's real answer arrives instead of a pass her
    // phone assumed for him (he casts an Idle Glimmer there): a branch, the board she tapped on is gone; refused with the note
    @Test fun aCastOnABoardThatChangedInAWayThatMattersIsRefusedWithTheNote() {
        start(bobGlimmer = true)
        aliceInMain(land = true)
        val inputs = spark(alice.state)
        waitUntil("all of Alice's on the server") { alice.engine { device.session.pending.isEmpty() } }
        val tip = alice.engine { device.session.head }
        held.holding = true
        bob = restart(bob)
        bob.openGame(game, this)
        waitUntil("Bob's playback stopping at a pass assumed for him") {
            if (bob.status == GameDevice.Status.CatchUp) bob.once { bob.tapToWatch() }
            bob.status is GameDevice.Status.Stopped
        }
        assertIs<GameDevice.Acted.Sent>(bob.game!!.send(listOf(Input.Cast(bob.me, bob.hand("Idle Glimmer").first()))))
        waitUntil("Bob's branch on Alice's phone") { alice.engine { device.session.events.none { it.id == tip } } }
        val acted = alice.game!!.send(inputs)
        held.release()
        assertEquals(GameDevice.Acted.Refused("stale"), acted)
        waitUntil("told with SYS-012's note") { notes.any { it == GameDevice.Note.Refused("stale") } }
        assertTrue(alice.engine { device.session.events + device.session.pending }.none { e -> e.author == alice.id &&
            e.engine?.steps.orEmpty().any { it.input is Input.Cast } }, "her Storm Spark not cast")
    }

    // SYS-011 (switchreview #6 kept): a fast second tap of the arrow on the still-shown board would pass at a point Alice
    // never saw (Full control: her beginning of combat); a different decision, so it isn't taken
    @Test fun aFastSecondPassIsNotMadeAtAPointNeverShown() {
        start()
        aliceInMain()
        alice.game!!.settings(PlaySettings(mode = ResponseMode.FullControl))
        waitUntil("Full control") { alice.shown.settings.mode == ResponseMode.FullControl }
        val shown = alice.shown
        held.holding = true
        alice.tap("To combat")
        waitUntil("Alice's phone past main 1") { alice.engine { device.model.engine.turn.step } != TurnStep.PrecombatMain }
        assertEquals(shown.version, alice.shown.version, "the table still shows main 1")
        alice.game!!.pass()
        held.release()
        waitUntil("Alice told") { notes.any { it == GameDevice.Note.Refused("stale") } }
        assertEquals(TurnStep.BeginningOfCombat, alice.engine { device.model.engine.turn.step }, "her first pass only")
    }

    // SYS-076 (smoke 2026-11-04): Alice taps To combat (her pass in main 1), then End turn (holding the arrow) before the
    // board her phone moved on to by itself (Bob's passes assumed, past an empty combat) reaches the screen: End turn may
    // be made at any priority of her own turn, so it is made on the newer board, not dropped
    @Test fun endTurnTappedWhileTheDeviceStepsOnByItselfIsMade() {
        // HIS-096: her phone goes on past Bob's pause at her end step
        start(PlaySettings(alwaysAssumeResolves = true))
        aliceInMain()
        val turn = alice.state.turn.number
        held.holding = true
        alice.tap("To combat")
        waitUntil("Alice's phone going on by itself") { alice.engine { device.model.engine.turn.step } != TurnStep.PrecombatMain }
        assertEquals(TurnStep.PrecombatMain, alice.state.turn.step, "the table still shows main 1")
        alice.hold("To combat")
        held.release()
        assertTrue(notes.none { it is GameDevice.Note.Refused }, "no refusal note: $notes")
        assertTrue(sameDecisionLogged("endTurn"), "made on the newer board")
        waitUntil("her turn ended") { alice.state.let { s -> s.turn.number > turn && s.turn.active != alice.me } }
    }

    // SYS-076, SYS-012 (smoke 2026-11-04): a second End turn on Alice's turn after the first one already took the game into
    // Bob's turn is another decision: not taken, and the note comes with a board of its own, so the table (which reads
    // notes when a board arrives) tells it at once
    @Test fun endTurnOnABoardOfAnEndedTurnIsRefusedWithTheNoteAtOnce() {
        // HIS-096: her phone goes on past Bob's pause at her end step
        start(PlaySettings(alwaysAssumeResolves = true))
        aliceInMain()
        held.holding = true
        alice.hold("To combat")
        waitUntil("Alice's phone in Bob's turn") { alice.engine { device.model.engine.turn.active } != alice.me }
        assertEquals(alice.me, alice.state.turn.active, "the table still shows her turn")
        val seen = mutableListOf<Pair<Long, Boolean>>()
        held.observe = { watchNotes(); seen += alice.game!!.version to alice.game!!.notes.any { it == GameDevice.Note.Refused("stale") } }
        alice.game!!.endTurn()
        held.release()
        waitUntil("Alice told") { seen.any { it.second } }
        val told = seen.indexOfFirst { it.second }
        assertTrue(told > 0 && seen[told].first > seen[told - 1].first, "the note arrived with a new board: $seen")
    }

    // ---- The server's race: the other player's event taken before the player's own move

    /**
     * Both live; both keep and Alice casts Idle Glimmer (free) in her first main phase, so her game waits for Bob at his
     * pause. What the server sends Alice's phone is held in the network; Bob passes (taken by the server) while Alice,
     * hearing nothing from him, turns "Always assume resolves" on and her phone assumes that pass, the Glimmer resolves and she is back in her
     * main phase, where she makes [move]. Her assumption and her move reach the server after his pass: refused as stale,
     * delivered after his revision (one socket keeps the server's order).
     */
    private fun race(move: () -> Unit) {
        start()
        bothKeep(bobAway = false)
        alice.playFromHand("Idle Glimmer", this)
        waitUntil("Alice waiting for Bob at her Glimmer") {
            alice.engine { device.session.pending.isEmpty() } && alice.status is GameDevice.Status.Waiting && alice.state.stack.isNotEmpty()
        }
        alice.link.holdIncoming()
        val before = bob.engine { device.session.events.size }
        waitUntil("Bob's pass on the server", along = listOf(bob)) {
            bob.engine { device.session.pending.isEmpty() && device.session.events.size > before && device.model.engine.stack.isEmpty() }
        }
        // No word from Bob: Alice turns "Always assume resolves" on and her phone assumes his pass
        alice.tap("Always assume resolves")
        waitUntil("Alice in main 1 on Bob's assumed pass") { alice.inOwnMain() }
        told.clear()
        move()
        waitUntil("Alice's move sent") { alice.engine { device.session.pending.isNotEmpty() } }
        alice.link.releaseIncoming()
        waitUntil("Alice's phone following the server again") { alice.engine { device.session.pending.isEmpty() } }
    }

    private fun made(input: (Input) -> Boolean): Boolean = alice.engine { device.session.events + device.session.pending }
        .any { e: GameEvent -> e.author == alice.id && e.engine?.steps.orEmpty().any { it.assumedBy == null && it.input?.let(input) == true } }

    // SYS-077: the server took Bob's pass before Alice's own pass made on her phone's assumption of it; on his pass the
    // same decision waits for her, so her pass is made again there without a note (his revision first)
    @Test fun aPassLostToTheOpponentsSamePassIsMadeAgain() {
        race { alice.tap("To combat") }
        assertTrue(notes.none { it is GameDevice.Note.Refused } && !alice.refusalSeen, "no refusal note: $notes")
        assertTrue(made { it == Input.PassPriority(alice.me) }, "her pass made again")
        assertTrue(alice.engine { device.repro(codec).log }.any { it.startsWith("remake PassPriority") }, "made again after the race")
        assertNotEquals(TurnStep.PrecombatMain, alice.state.turn.step, "her main 1 is over")
    }

    // SYS-077: the same with a Mountain played and Storm Spark cast on it (its target and payment as she chose them): each
    // made again on the one before
    @Test fun aCastLostToTheOpponentsSamePassIsMadeAgain() {
        race {
            val land = Engine.options(alice.state, alice.me).filterIsInstance<Input.PlayLand>().first()
            assertFalse(alice.game!!.send(listOf(land)) is GameDevice.Acted.Refused)
            waitUntil("the Mountain played") { alice.field("Mountain").isNotEmpty() }
            alice.game!!.send(spark(alice.state))
        }
        assertTrue(notes.none { it is GameDevice.Note.Refused } && !alice.refusalSeen, "no refusal note: $notes")
        assertTrue(made { it is Input.PlayLand } && made { it is Input.Cast }, "her land and cast made again")
    }
}
