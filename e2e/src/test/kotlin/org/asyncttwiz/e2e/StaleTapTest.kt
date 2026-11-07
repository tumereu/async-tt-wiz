package org.asyncttwiz.e2e

import arcanum.engine.Engine
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
        override fun run(work: () -> Unit) = EngineWorker.Background.run(work)
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
    private fun activation(s: GameState): List<Input> {
        val activate = assertNotNull(Engine.options(s, alice.me).filterIsInstance<Input.Activate>().firstOrNull(), "the Altar's ability")
        val copy = s.deepCopy()
        val inputs = mutableListOf<Input>(activate)
        var input: Input = activate
        repeat(10) {
            Engine.perform(copy, input).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: return inputs
            if (d is Decision.Random || Engine.decider(copy, d.player) != alice.me) return inputs
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
}
