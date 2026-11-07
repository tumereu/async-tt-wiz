package org.asyncttwiz.e2e

import arcanum.engine.input.Input
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-07 (TRN-017, STK-029, TRN-070): holding Resolve, or a Stack wait's Assume, resolves the whole
 * Stack: the phone presses Resolve at each of the player's priority points and Assume at each of the other player's,
 * whatever their response mode, until the Stack is empty; the other player's real input is never passed over (it ends
 * the chain, and the player gets priority with it on top).
 */
class HoldResolveChainTest : E2e() {
    /** Every input of [player]'s line from [from] on (their pending events too), with who assumed it. */
    private fun madeSince(player: E2ePlayer, from: String): List<Pair<Input?, String?>> = player.engine {
        (device.session.events + device.session.pending).dropWhile { it.id != from }.drop(1)
    }.map { e -> e.engine?.steps?.firstNotNullOfOrNull { it.input } to e.assumed?.assumedBy?.value }

    /** The names on [player]'s Stack, bottom first. */
    private fun stack(player: E2ePlayer): List<String?> = player.state.let { s -> s.stack.map { cardName(s, it) } }

    /**
     * Alice (Control: she keeps priority after her own casts, STK-036) with Ember Glints, Bob (Normal: his pause at her
     * spells waits for him, with Assume) with Idle Glimmers; both keep, Alice in her Main 1 casts two Glints, holding
     * priority over them. With [bobAway] Bob leaves after the keep.
     */
    private fun aliceCastsTwoGlints(bobAway: Boolean) {
        startGame(TestDeck("Glints", listOf(TestCards.mountain to 20, TestCards.glint to 20)), TestDeck.glimmers(),
            bobSettings = PlaySettings(mode = ResponseMode.Normal),
            deal = { a, b -> hand(a).count { it == "Ember Glint" } >= 2 && "Idle Glimmer" in hand(b) })
        bothOpen()
        bothKeep(bobAway = bobAway)
        // Control from her main phase on (her table's response mode, TRN-059)
        alice.game!!.settings(alice.shown.settings.copy(mode = ResponseMode.FullControl))
        waitUntil("Alice in Control") { alice.shown.settings.mode == ResponseMode.FullControl && alice.inOwnMain() }
        alice.playFromHand("Ember Glint", this)
        waitUntil("Alice holding priority over her Glint") { stack(alice) == listOf("Ember Glint") && alice.stepArrow() == "Resolve" }
        alice.playFromHand("Ember Glint", this)
        waitUntil("Alice holding priority over both Glints") {
            stack(alice) == listOf("Ember Glint", "Ember Glint") && alice.stepArrow() == "Resolve" && alice.current()
        }
    }

    // TRN-017, STK-029 (owner 2026-11-07): Bob away; Alice holds Resolve over her two Glints: her phone passes for her,
    // assumes Bob's pass (his Normal would wait for him there), passes for her again (her Control would stop there) and
    // assumes his pass again: both Glints resolve with one hold, and she is back in her main phase
    @Test fun holdingResolveResolvesTheWholeStack() {
        aliceCastsTwoGlints(bobAway = true)
        val from = alice.shown.tip
        alice.hold("Resolve")
        waitUntil("both Glints resolved") { alice.state.stack.isEmpty() && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() } }
        assertEquals(listOf(Input.PassPriority(alice.me) to null, Input.PassPriority(bob.me) to alice.id,
            Input.PassPriority(alice.me) to null, Input.PassPriority(bob.me) to alice.id), madeSince(alice, from),
            "Resolve, Assume, Resolve, Assume")
        assertEquals(24, alice.state.player(alice.me).life, "both Glints resolved")
        assertFalse(alice.engine { device.chaining }, "the chain ended with the Stack")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // TRN-017, TRN-070 (owner 2026-11-07): Bob away; Alice taps Resolve once and her phone waits for Bob with "Assume
    // resolves"; holding it assumes his pass, passes for her (Control would stop) and assumes his pass again: the whole
    // Stack resolves
    @Test fun holdingAssumeResolvesTheWholeStack() {
        aliceCastsTwoGlints(bobAway = true)
        alice.tap("Resolve")
        waitUntil("Alice waiting for Bob with Assume resolves") { alice.showsControl("Assume resolves") && alice.current() }
        val from = alice.shown.tip
        alice.hold("Assume resolves")
        waitUntil("both Glints resolved") { alice.state.stack.isEmpty() && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() } }
        assertEquals(listOf(Input.PassPriority(bob.me) to alice.id, Input.PassPriority(alice.me) to null, Input.PassPriority(bob.me) to alice.id),
            madeSince(alice, from), "Assume, Resolve, Assume")
        assertEquals(24, alice.state.player(alice.me).life)
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // TRN-017, SYS-077, HIS-109 (owner 2026-11-07): Bob live; Alice taps Resolve and waits for him over her second Glint;
    // Bob casts an Idle Glimmer there, which reaches the server before her hold of "Assume resolves" does (her phone hasn't
    // heard of it): her chain's press isn't taken, the chain is over, and her table shows Bob's Glimmer on top with her
    // priority over it (her Control stops there): nothing of hers passed over it
    @Test fun theirResponseLandingMidChainIsNeverPassedOver() {
        aliceCastsTwoGlints(bobAway = false)
        alice.tap("Resolve")
        waitUntil("Alice waiting for Bob with Assume resolves; Bob may respond") {
            alice.showsControl("Assume resolves") && alice.current() && bob.status == GameDevice.Status.YourMove &&
                stack(bob) == listOf("Ember Glint", "Ember Glint") && bob.current()
        }
        // What the server sends Alice now waits on the way (her own sends go out)
        alice.link.holdIncoming()
        val glimmer = bob.hand("Idle Glimmer").first()
        val cast = bob.game!!.send(listOf(Input.Cast(bob.me, glimmer)))
        assertTrue(cast is GameDevice.Acted.Sent || cast == GameDevice.Acted.Made, "Bob's Glimmer: $cast")
        fun bobsCast(e: GameEvent) = e.author == bob.id && e.engine?.steps.orEmpty().any { (it.input as? Input.Cast)?.card == glimmer }
        waitUntil("Bob's Glimmer on the server") { bob.engine { device.session.pending.isEmpty() && device.session.events.any(::bobsCast) } }
        alice.hold("Assume resolves")
        waitUntil("Alice's press sent") { alice.engine { device.session.pending.isNotEmpty() } }
        alice.link.releaseIncoming()
        waitUntil("Alice's table with Bob's Glimmer on top, her priority over it") {
            stack(alice) == listOf("Ember Glint", "Ember Glint", "Idle Glimmer") && alice.status == GameDevice.Status.YourMove &&
                alice.engine { device.session.pending.isEmpty() } && quiet()
        }
        assertFalse(alice.engine { device.chaining }, "Bob's input ended the chain")
        val line = alice.engine { device.session.events }
        val at = line.indexOfFirst(::bobsCast)
        assertTrue(at >= 0 && line.drop(at + 1).none { it.author == alice.id }, "nothing of Alice's after Bob's Glimmer")
        run(skip = java.time.Duration.ofSeconds(3))
        assertEquals(listOf("Ember Glint", "Ember Glint", "Idle Glimmer"), stack(alice), "still waiting for her")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }
}
