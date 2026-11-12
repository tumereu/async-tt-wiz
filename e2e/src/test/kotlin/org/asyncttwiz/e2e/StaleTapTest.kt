package org.asyncttwiz.e2e

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.android.EngineWorker
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner problem report E48RDE (prod 0.2.0-beta2, 2026-11-07): with several of his abilities on the Stack, a tap of the main
 * button said "That wasn't taken". His Assume resolved the top one and his phone, in Quick, then passed for him over the
 * ability below (HIS-109: exactly a press of Resolve); his own tap of Resolve on the board in between (his priority, made
 * on board v404) reached the phone after it had published that pass (v405) and was refused. A tap whose input is the very
 * press the phone made there by itself is that press: it goes quietly, with nothing made again (never the next ability's
 * resolution). Alice's phone holds what its engine thread shows ([Stepped]): the board in between reaches her screen, the
 * one after her phone's own pass waits.
 *
 * A full e2e run, 2026-11-08 (CombatBandTest, "no refusal"): the same press folded into the player's own action. Her Attack's
 * board shows at once (SYS-025), before her pass that follows by itself is folded into its event, and says Next; her tap of
 * Next there was told "That wasn't taken". Tapped while the event is still being finished, it was worse: made on the board
 * the phone had reached, a point she never saw.
 */
class StaleTapTest : E2e() {
    private val held = Stepped()
    private val deck = TestDeck("Altars", listOf(TestCards.mountain to 14, TestCards.altar to 13, TestCards.martyr to 13))

    /**
     * The app's engine thread, but what it posts to the main thread waits while [holding] (as [HeldWorker]); [releaseUntil]
     * shows the posts one at a time until a condition holds, keeping the rest waiting.
     */
    private class Stepped : EngineWorker {
        @Volatile var holding = false
        private val held = ConcurrentLinkedQueue<() -> Unit>()
        var observe: () -> Unit = {}
        /**
         * While on, everything queued for the engine thread waits, in order ([releaseFollowUps]), whichever thread queued it:
         * an own action's follow-up and also the clock's tick, what the phone sends, and the server's answer to it, each of
         * which publishes a board of its own. An action already running finishes (its event, inline on the engine thread) and
         * the board it published on the way stays the newest; an own action's tap ([early], [call]) isn't queued through
         * here, so it comes next, as while the action's event is being finished. Nothing blocks the engine thread: the main
         * thread may wait for it, as a tap does.
         */
        @Volatile var holdingFollowUps = false
        private val followUps = ConcurrentLinkedQueue<() -> Unit>()
        override fun run(work: () -> Unit) { if (holdingFollowUps) followUps.add(work) else EngineWorker.Background.run(work) }
        override fun <T> call(work: () -> T): T = EngineWorker.Background.call(work)
        override fun early(work: (release: (() -> Unit)?) -> Unit) = EngineWorker.Background.early(work)
        override fun main(work: () -> Unit) = EngineWorker.Background.main { if (holding) held.add(work) else { work(); observe() } }
        override fun frame(work: () -> Unit) = EngineWorker.Background.frame { main(work) }

        /** Main thread. A post on its way (on the main looper, not held yet) is let in first. */
        fun releaseUntil(condition: () -> Boolean) {
            var idle = 0
            while (!condition()) {
                val work = held.poll()
                if (work == null) {
                    check(idle++ < 100) { "nothing more held" }
                    EngineWorker.Background.call { }
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                    continue
                }
                work(); observe()
            }
        }

        fun release() {
            holding = false
            while (true) { val work = held.poll() ?: break; work(); observe() }
        }

        fun releaseFollowUps() {
            holdingFollowUps = false
            while (true) { val work = followUps.poll() ?: break; EngineWorker.Background.run(work) }
        }
    }

    /** Every note Alice's table was given (each once, before the table shows and drops it). */
    private val told = mutableListOf<GameDevice.Note>()
    private val seenNotes = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<GameDevice.Note, Boolean>())
    private fun watchNotes() { (alice.game ?: alice.last)?.notes?.forEach { if (seenNotes.add(it)) told += it } }

    private fun waitingWithAssume(stack: Int) = alice.settled(this) && alice.state.stack.size == stack &&
        (alice.status as? GameDevice.Status.Waiting)?.offer == GameDevice.Offer.AssumeResolves

    /** Alice casts [name] (free) and, as Bob is away and his pause waits for him, taps Assume so it resolves. */
    private fun castAndAssume(name: String) {
        alice.playFromHand(name, this)
        waitUntil("Alice waiting for Bob at her $name") { waitingWithAssume(stack = 1) }
        alice.tap("Assume resolves")
        waitUntil("her $name on the battlefield") { alice.field(name).isNotEmpty() && alice.inOwnMain() && alice.settled(this) }
    }

    /** The Altar's activation sacrificing the Martyr, with Alice's own answers, as the table makes it. */
    private fun activation(s: GameState): List<Input> =
        withAnswers(s, assertNotNull(Engine.options(s, alice.me).filterIsInstance<Input.Activate>().firstOrNull(), "the Altar's ability"))

    /** [first] with Alice's own answers to what it asks, as the table makes them. */
    private fun withAnswers(s: GameState, first: Input): List<Input> {
        val copy = s.deepCopy()
        val inputs = mutableListOf(first)
        var input: Input = first
        repeat(10) {
            Engine.perform(copy, input).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: return inputs
            if (d is Decision.Random || Host.decider(copy, d.player) != alice.me) return inputs
            input = Engine.options(copy, alice.me).filterIsInstance<Input.Answer>().first { it.decision == d.id }
            inputs += input
        }
        return inputs
    }

    /** Alice's own passes and assumptions on her phone's line after [from], oldest first. */
    private fun inputsAfter(from: String): List<Input> = alice.engine {
        val line = device.session.events + device.session.pending
        line.drop(line.indexOfFirst { it.id == from } + 1).mapNotNull { e -> e.engine?.steps?.firstNotNullOfOrNull { it.input } }
    }

    // HIS-109, SYS-012, SYS-011 (owner problem report E48RDE): Alice's Altar ability and her Martyr's death trigger on the
    // Stack, Bob away; she taps Assume (the trigger resolves) and then Resolve on the board where she holds priority over
    // her ability, as her phone (Quick) passes there by itself: the same press, so no "That wasn't taken", no second pass,
    // and her ability still waits for Bob (never resolved by the tap)
    @Test fun aResolveTappedAsHerPhonePassesByItselfGoesQuietly() {
        startGame(deck, TestDeck.sparks(), aliceWorker = held, bobSettings = PlaySettings(mode = ResponseMode.Normal),
            deal = { a, _ -> "Ember Altar" in hand(a) && "Ember Martyr" in hand(a) })
        held.observe = ::watchNotes
        bothOpen()
        bothKeep(bobAway = true)
        castAndAssume("Ember Altar")
        castAndAssume("Ember Martyr")
        val acted = alice.game!!.send(activation(alice.state))
        assertFalse(acted is GameDevice.Acted.Refused, "activated: $acted")
        waitUntil("Alice waiting for Bob over her trigger and her ability") { waitingWithAssume(stack = 2) }
        val bobLife = alice.state.player(bob.me).life
        val activated = alice.engine { device.session.tip }

        held.holding = true
        alice.tap("Assume resolves")
        waitUntil("her phone passing for her by itself over her ability") {
            alice.engine { device.model.engine.let { it.stack.size == 1 && it.priority.holder == bob.me } }
        }
        // Her screen shows the board after her Assume (her priority over her ability), not yet her phone's own pass
        held.releaseUntil { alice.state.stack.size == 1 && alice.state.priority.holder == alice.me }
        assertEquals("Resolve", alice.stepArrow())
        alice.tap("Resolve")
        held.release()
        repeat(5) { run(skip = java.time.Duration.ofMillis(500)) }

        assertTrue(told.none { it is GameDevice.Note.Refused } && !alice.refusalSeen, "no refusal note: $told ${alice.shownTexts}")
        assertTrue(AppLog.text().lines().any { "Own pass on board" in it && "already done" in it }, "the tap taken as the same press")
        assertEquals(listOf<Input>(Input.PassPriority(bob.me), Input.PassPriority(alice.me)), inputsAfter(activated),
            "her assumption and one pass of hers")
        assertEquals(1, alice.state.stack.size, "her ability still on the Stack")
        assertEquals(bobLife, alice.state.player(bob.me).life, "her ability not resolved")
        waitUntil("Alice waiting for Bob over her ability with Assume") { waitingWithAssume(stack = 1) }
        assertEquals(bobLife, alice.state.player(bob.me).life, "her ability still not resolved")
    }

    // HIS-109, SYS-012, SYS-011 (a full e2e run, 2026-11-08): Alice attacks; her phone shows the attack at once with Next on
    // the main button, then folds her pass into the attack's event and assumes Bob's pass. Her tap of Next on the attack's own
    // board reaches the phone after all that: the pass her phone made there by itself, so no "That wasn't taken" and no
    // second pass; Bob's own Pass then confirms what her phone assumed
    @Test fun aNextTappedOnHerAttackAsHerPhonePassesByItselfGoesQuietly() {
        startGame(TestDeck.thirds(TestCards.cub), TestDeck.thirds(TestCards.herald), aliceWorker = held,
            deal = { a, b -> "Mountain" in hand(a) && "Ember Cub" in hand(a) && "Mountain" in hand(b) })
        held.observe = ::watchNotes
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Cub", this)
        waitUntil("the Cub on the battlefield", along = listOf(bob)) { alice.field("Ember Cub").isNotEmpty() && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        // A land, so he has no card to discard at his end step
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain", along = listOf(alice)) { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's next Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.number >= 3 }
        alice.tap("To combat")
        alice.stageAttacker("Ember Cub", this)
        waitUntil("the Cub staged") { alice.shown.board.engine!!.table.attackDraft.orEmpty().size == 1 }
        waitUntil("her Attack ready") {
            alice.settled(this) && org.asyncttwiz.android.AutomatumBoards.cachedCombat(alice.shown.board) != null && alice.stepArrow() == "Attack (1)"
        }
        val before = alice.engine { device.session.tip }

        held.holding = true
        alice.tap("Attack (1)")
        waitUntil("her phone past Bob's pass it assumed") { alice.engine { device.model.engine.turn.step } == TurnStep.DeclareBlockers }
        // Her screen shows the attack's own board, not yet what her phone did after it
        held.releaseUntil { alice.state.combat?.attackers.orEmpty().isNotEmpty() }
        assertEquals(alice.me, alice.state.priority.holder, "her priority on the attack's own board")
        assertEquals("Next", alice.stepArrow())
        alice.tap("Next")
        held.release()
        waitUntil("Bob's stop at her assumption") {
            bob.settled(this) && (bob.status is GameDevice.Status.Stopped || bob.status == GameDevice.Status.YourMove) && bob.stepArrow() == "Pass"
        }
        bob.tap("Pass")
        // Bob has no creature to block with: the game goes on to the Cub's damage
        val life = alice.state.player(bob.me).life
        waitUntil("the Cub's damage", along = listOf(alice, bob)) { alice.state.player(bob.me).life < life }

        assertTrue(told.none { it is GameDevice.Note.Refused } && !alice.refusalSeen && !bob.refusalSeen, "no refusal: $told ${alice.shownTexts}")
        assertTrue(AppLog.text().lines().any { "Own pass on board" in it && "already done" in it }, "the tap taken as the same press")
        assertEquals(listOf<Input>(Input.PassPriority(alice.me), Input.PassPriority(bob.me)), inputsInEvents(before).drop(1).take(2),
            "after her attack, her pass in it, then Bob's pass assumed")
        assertEquals(life - 2, alice.state.player(bob.me).life, "the Cub's damage, once")
    }

    // HIS-109, SYS-011 (the same run's race, one step earlier): Alice casts a spell Bob, with an empty hand, can only pass over,
    // so her pass and his fold into the cast's event and it resolves in it, back in her Main 1. Her tap of Resolve on the
    // cast's own board reaches the phone while that board is still the newest it has published: it is the pass made there,
    // never a pass of the Main 1 she hasn't seen it come back to
    @Test fun aResolveTappedWhileHerCastIsBeingFinishedPassesNothingMore() {
        startGame(deck, TestDeck("Martyrs", listOf(TestCards.martyr to 40)), bobStarts = true, aliceWorker = held,
            deal = { a, _ -> "Ember Martyr" in hand(a) })
        held.observe = ::watchNotes
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        for (n in 1..7) {
            waitUntil("Bob settled", along = listOf(alice)) { bob.settled(this) && bob.inOwnMain() }
            bob.game!!.send(listOf(Input.Cast(bob.me, bob.hand("Ember Martyr").first())))
            waitUntil("Bob's Martyr $n", along = listOf(alice)) { bob.field("Ember Martyr").size == n && bob.inOwnMain() }
        }
        bob.hold("To combat")
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.active == alice.me && alice.settled(this) }
        assertTrue(alice.state.player(bob.me).hand.isEmpty(), "Bob's hand empty")
        val turn = alice.state.turn.number
        val before = alice.engine { device.session.tip }
        val cast = withAnswers(alice.state, Input.Cast(alice.me, alice.hand("Ember Martyr").first()))

        // From here her phone's engine thread only finishes what is under way: nothing can publish a board but her cast
        held.holdingFollowUps = true
        waitUntil("nothing on its way on Alice's phone") { alice.current() }
        alice.game!!.send(cast)
        waitUntil("the cast's own board on her screen") {
            alice.shown.ownActionShown && alice.state.stack.size == 1 && alice.state.priority.holder == alice.me && alice.stepArrow() == "Resolve"
        }
        alice.tap("Resolve")
        assertTrue(AppLog.text().lines().any { "Own pass on board" in it && "already done" in it }, "the tap taken as the same press")
        held.releaseFollowUps()
        repeat(5) { run(skip = java.time.Duration.ofMillis(500)) }
        waitUntil("her Martyr on the battlefield") { alice.field("Ember Martyr").size == 1 && alice.settled(this) }

        assertTrue(told.none { it is GameDevice.Note.Refused } && !alice.refusalSeen, "no refusal: $told ${alice.shownTexts}")
        assertEquals(turn to TurnStep.PrecombatMain, alice.state.turn.number to alice.state.turn.step, "still her Main 1")
        assertEquals(GameDevice.Status.YourMove, alice.status)
        assertEquals(cast + listOf<Input>(Input.PassPriority(alice.me), Input.PassPriority(bob.me)), inputsInEvents(before), "the cast and its passes only")
    }

    /** Every input on Alice's phone's line after [from], oldest first, the ones folded into an event too. */
    private fun inputsInEvents(from: String): List<Input> = alice.engine {
        val line = device.session.events + device.session.pending
        line.drop(line.indexOfFirst { it.id == from } + 1).flatMap { e -> e.engine?.steps.orEmpty().mapNotNull { it.input } }
    }
}
