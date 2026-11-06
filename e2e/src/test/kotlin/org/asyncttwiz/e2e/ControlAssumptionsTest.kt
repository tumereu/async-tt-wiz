package org.asyncttwiz.e2e

import arcanum.engine.input.Input
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Control mode and assumptions (owner, 2026-11-06): each phone learns the other player's response mode and assumes their
 * pass where its Assume setting does or their own mode would pass (their Quick judged on their real hand), and an
 * automatic assumption is a press of Assume: a live move of the player it is about that arrives after it branches
 * instead of being refused (found by worker H: in Control a second spell while holding priority was refused, "That
 * wasn't taken").
 */
class ControlAssumptionsTest : E2e() {
    /** Every wait with an Assume button [player]'s table shows from now on. */
    private fun assumeOffers(player: () -> E2ePlayer): MutableList<GameDevice.Offer> {
        val offers = mutableListOf<GameDevice.Offer>()
        everyRun += { (player().game?.shown?.status as? GameDevice.Status.Waiting)?.offer?.let(offers::add) }
        return offers
    }

    /** The events [by]'s phone assumed about [about]'s pass, on its line. */
    private fun assumedPasses(by: E2ePlayer, about: E2ePlayer) = by.engine { device.session.events + device.session.pending }
        .filter { it.assumed?.assumedBy == by.me && it.assumed?.input == Input.PassPriority(about.me) }

    /** Alice, in Control from the start, passes her own priority (the main button) until [done]; Bob plays along. */
    private fun alicePassesUntil(what: String, done: () -> Boolean) = waitUntil(what, along = listOf(bob)) {
        if (!done() && alice.status == GameDevice.Status.YourMove && alice.engine { device.session.pending.isEmpty() })
            alice.stepArrow()?.takeIf { it in listOf("Pass", "Next", "Resolve") }?.let { arrow -> alice.once { alice.tap(arrow) } }
        done()
    }

    // STK-036, SYS-077, HIS-097 (owner 2026-11-06, spec rule 6): Alice in Control casts an Idle Glimmer and keeps priority;
    // Bob's phone ("Assume resolves" Always) assumes her pass; her second Glimmer, cast while holding priority before that
    // assumption reached her phone, is taken: Bob's line branches (his note), her phone says nothing was refused
    @Test fun aSecondSpellWhileHoldingPriorityBranchesTheAssumption() {
        startGame(TestDeck.glimmers(), TestDeck.thirds(), bobSettings = PlaySettings(alwaysAssumeResolves = true),
            deal = { a, _ -> hand(a).count { it == "Idle Glimmer" } >= 2 })
        bothOpen()
        bothKeep(bobAway = false)
        alice.game!!.settings(alice.shown.settings.copy(mode = ResponseMode.FullControl))
        waitUntil("Alice in Control") { alice.shown.settings.mode == ResponseMode.FullControl }
        alice.playFromHand("Idle Glimmer", this)
        waitUntil("Alice's first Glimmer on the server, her priority kept", along = listOf(bob)) {
            alice.state.stack.size == 1 && alice.state.priority.holder == alice.me && alice.engine { device.session.pending.isEmpty() }
        }
        // What the server sends Alice now waits on the way (her own sends go out)
        alice.link.holdIncoming()
        waitUntil("Bob's phone assuming her pass, on the server") {
            assumedPasses(bob, alice).isNotEmpty() && bob.engine { device.session.pending.isEmpty() }
        }
        alice.playFromHand("Idle Glimmer", this)
        waitUntil("Alice's second Glimmer sent") { alice.engine { device.session.pending.isNotEmpty() } }
        alice.link.releaseIncoming()
        waitUntil("Bob's line branched by Alice's answer", along = listOf(bob)) {
            bob.told("responded — your later actions were taken back") && quiet()
        }
        assertTrue(!alice.refusalSeen, "Alice's second spell wasn't refused: ${alice.shownTexts}")
        val casts = alice.engine { device.session.events }.count { e -> e.engine?.steps.orEmpty().any { (it.input as? Input.Cast)?.player == alice.me } }
        assertEquals(2, casts, "both Glimmers on the main line")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // HIS-096, STK-036, TRN-058 (owner 2026-11-06): Alice is in Control and has an Ember Warden; Bob ("Assume resolves"
    // Never) casts an Idle Glimmer and her Warden's trigger goes on top: with Alice holding priority over it his phone
    // waits with Assume (Never respects her Control), and the game is her move: the server names her, with a "your move"
    // notification
    @Test fun neverWaitsAtYourOwnTriggerWhileYouHoldPriority() {
        startGame(TestDeck("Wardens", listOf(TestCards.mountain to 20, TestCards.warden to 20)), TestDeck.glimmers(),
            aliceSettings = PlaySettings(mode = ResponseMode.FullControl), bobSettings = PlaySettings().assuming(AssumeResolves.Never),
            deal = { a, b -> "Ember Warden" in hand(a) && "Idle Glimmer" in hand(b) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        alicePassesUntil("Alice's Main 1") { alice.inOwnMain() }
        alice.playFromHand("Ember Warden", this)
        alicePassesUntil("the Warden on Alice's battlefield") { alice.field("Ember Warden").isNotEmpty() && alice.inOwnMain() }
        alice.hold(alice.stepArrow()!!)
        // Bob's turn: his own main phase (Alice passing her priority in his upkeep, as Control stops there)
        waitUntil("Bob's Main 1") {
            if (alice.status == GameDevice.Status.YourMove && alice.engine { device.session.pending.isEmpty() })
                alice.stepArrow()?.takeIf { it in listOf("Pass", "Next", "Resolve") }?.let { arrow -> alice.once { alice.tap(arrow) } }
            // Bob plays along until his own turn (in it his phone goes on by itself to his main phase)
            if (bob.state.turn.active != bob.me || bob.status is GameDevice.Status.Stopped || bob.status == GameDevice.Status.CatchUp) bob.playAlong()
            bob.inOwnMain() && quiet()
        }
        val before = ServerSetup.gameNotice(alice.account, game.id)?.first
        val assumed = assumedPasses(bob, alice).size
        bob.playFromHand("Idle Glimmer", this)
        // Bob's own priority with Alice's trigger on top: he lets it be (his main button) when his phone stops there
        waitUntil("Alice holding priority over her Warden's trigger", along = emptyList()) {
            if (bob.status == GameDevice.Status.YourMove && bob.stepArrow() == "Resolve" && bob.state.stack.size == 2) bob.once { bob.tap("Resolve") }
            alice.state.let { s -> s.stack.size == 2 && s.priority.holder == alice.me } && alice.status == GameDevice.Status.YourMove
        }
        waitUntil("Bob's phone waiting for Alice with Assume") {
            (bob.status as? GameDevice.Status.Waiting)?.offer == GameDevice.Offer.AssumeResolves && bob.state.stack.size == 2
        }
        repeat(3) { run(skip = Duration.ofSeconds(20)) }
        assertEquals(assumed, assumedPasses(bob, alice).size, "Bob's phone assumed nothing of hers")
        assertTrue(bob.status is GameDevice.Status.Waiting, "still waiting: ${bob.status}")
        // Her move, and she is told
        waitUntil("the server naming Alice's move") { ServerSetup.head(alice.account, game.id).summary.attentionHolder == alice.id }
        waitUntil("Alice's \"your move\" notification") {
            ServerSetup.gameNotice(alice.account, game.id)?.let { (id, move) -> id != before && move } == true
        }
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // HIS-096, STK-036 (owner 2026-11-06; the spec names Bob's "Normal", which its rules say assumes here: see the
    // implementor's report): Alice is in Control and Bob's phone is on Never: after her own cast, with her priority kept,
    // his phone waits for her instead of assuming her pass
    @Test fun neverWaitsForYourPassAfterYourOwnCastInControl() {
        startGame(TestDeck.glimmers(), TestDeck.thirds(), aliceSettings = PlaySettings(mode = ResponseMode.FullControl),
            bobSettings = PlaySettings().assuming(AssumeResolves.Never), deal = { a, _ -> "Idle Glimmer" in hand(a) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        alicePassesUntil("Alice's Main 1") { alice.inOwnMain() }
        val assumed = assumedPasses(bob, alice).size
        alice.playFromHand("Idle Glimmer", this)
        waitUntil("Bob's phone waiting for Alice after her Glimmer") {
            (bob.status as? GameDevice.Status.Waiting)?.offer == GameDevice.Offer.AssumeResolves && bob.state.stack.size == 1
        }
        repeat(3) { run(skip = Duration.ofSeconds(20)) }
        assertEquals(assumed, assumedPasses(bob, alice).size, "Bob's phone assumed nothing of hers")
        assertEquals(alice.me, alice.state.priority.holder, "Alice holds priority over her own spell")
        assertEquals(GameDevice.Status.YourMove, alice.status)
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // HIS-096 (owner 2026-11-06, spec rule 5): Bob is in Quick and holds only lands, so he couldn't respond to anything;
    // Alice's phone on Never assumes his passes at once (his own mode would pass them), also at her Herald on the Stack,
    // his pause: no wait with Assume while he is away
    @Test fun neverAssumesAtOnceWhatTheirQuickWouldPass() {
        startGame(TestDeck.thirds(TestCards.herald), TestDeck("Lands", listOf(TestCards.mountain to 40)),
            aliceSettings = PlaySettings().assuming(AssumeResolves.Never), deal = { a, _ -> "Mountain" in hand(a) && "Ember Herald" in hand(a) })
        bothOpen()
        bothKeep(bobAway = true)
        val offers = assumeOffers { alice }
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Herald", this)
        waitUntil("the Herald and its token on Alice's battlefield") {
            alice.field("Ember Herald").isNotEmpty() && alice.state.battlefield.any { alice.state.obj(it)?.token == true } && alice.inOwnMain()
        }
        assertTrue(offers.isEmpty(), "no wait with Assume: $offers")
        assertTrue(assumedPasses(alice, bob).isNotEmpty(), "his passes assumed")
        assertTrue(alice.leaks.leaks.isEmpty())
    }
}
