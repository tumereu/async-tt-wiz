package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import arcanum.engine.input.Input
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-05: assumptions depend only on the player's setting, never on the other player's presence. With "Always
 * assume resolves" off (the default) the other player's pause is a wait with Assume on the main button, also while they
 * are away; on, it is assumed at once. Blocks are assumed only by the main button ("Assume no blockers" / "Assume
 * blockers", the blocks placed on the lanes).
 */
class AssumeBySettingTest : E2e() {
    /** Every wait with an Assume button Alice's table shows from now on. */
    private fun assumeOffers(): MutableList<GameDevice.Offer> {
        val offers = mutableListOf<GameDevice.Offer>()
        everyRun += { (alice.game?.shown?.status as? GameDevice.Status.Waiting)?.offer?.let(offers::add) }
        return offers
    }

    // HIS-096, TRN-070, STK-028: Bob is away; Alice's Ember Herald waits for him with Assume on the main button (nothing
    // under the Stack), however long he stays away; Assume goes on (her trigger waits the same way); with "Assume
    // resolves" set to Always from her quick actions her End turn goes past his pause at her end step at once
    @Test fun aPauseWaitsWithAssumeUnlessAlwaysAssumeResolves() {
        startGame(TestDeck.thirds(TestCards.herald), TestDeck.thirds(TestCards.herald),
            deal = { a, _ -> "Mountain" in hand(a) && "Ember Herald" in hand(a) })
        bothOpen()
        bothKeep(bobAway = true)
        assertEquals(org.asyncttwiz.protocol.game.AssumeResolves.Normal, alice.shown.settings.assume, "Normal by default")
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        val herald = atw(alice.hand("Ember Herald").first())
        alice.playFromHand("Ember Herald", this)
        waitUntil("Alice waiting for Bob at her spell") {
            (alice.status as? GameDevice.Status.Waiting)?.offer == GameDevice.Offer.AssumeResolves
        }
        assertTrue(alice.showsText("${bob.name} may respond to Ember Herald"), alice.screen())
        assertTrue(alice.shows(hasTestTag("engine-wait-action")) && alice.showsControl("Assume resolves"), "Assume on the main button: ${alice.screen()}")
        assertTrue(!alice.shows(hasTestTag("stack-assume")) && !alice.shows(hasTestTag("stack-resolve")), "nothing under the Stack")
        // Away for minutes: still a wait, nothing more assumed (his passes in her upkeep, not pauses, were assumed at once)
        fun assumedCount() = alice.engine { (device.session.events + device.session.pending).count { it.assumed?.assumedBy == alice.me } }
        val assumed = assumedCount()
        repeat(3) { run(skip = Duration.ofMinutes(1)) }
        assertIs<GameDevice.Status.Waiting>(alice.status)
        assertEquals(assumed, assumedCount(), "nothing more assumed")
        assertEquals("STACK", where(alice.shown.board, herald))
        alice.tap("Assume resolves")
        waitUntil("Alice waiting for Bob at her trigger") {
            where(alice.shown.board, herald) == "BATTLEFIELD" && (alice.status as? GameDevice.Status.Waiting)?.offer == GameDevice.Offer.AssumeResolves
        }
        alice.tap("Assume resolves")
        waitUntil("the token, Alice in her Main 1") { alice.state.battlefield.any { alice.state.obj(it)?.token == true } && alice.inOwnMain() }
        assertTrue(assumedCount() >= assumed + 2, "her two Assume taps")

        // "Assume resolves" set to Always (a tap goes Never → When they have no mana → Always): her end step (his pause)
        // goes by at once
        alice.tap("Assume resolves setting")
        waitUntil("the setting at When they have no mana") { alice.shown.settings.assume == org.asyncttwiz.protocol.game.AssumeResolves.WhenNoMana }
        alice.tap("Assume resolves setting")
        waitUntil("the setting at Always") { alice.shown.settings.assume == org.asyncttwiz.protocol.game.AssumeResolves.Always }
        val offers = assumeOffers()
        alice.hold("To combat")
        waitUntil("Bob's turn on Alice's table") { alice.state.turn.active == bob.me && alice.engine { device.session.pending.isEmpty() } }
        assertTrue(offers.isEmpty(), "no wait with Assume: $offers")
        assertTrue(alice.leaks.leaks.isEmpty())
    }

    // CMB-040, HIS-096, TRN-070 (owner, 2026-11-05, 2026-11-06): Alice attacks while Bob is away, "Assume resolves"
    // Never: his pause right after her attack is assumed at once and his blockers wait in assume mode on the lanes with
    // "Assume no blockers" on the main button; nothing is assumed by itself, however long he stays away; the button
    // assumes no blocks; his priority after the blocks waits with "Assume no response" (the setting); her End turn then
    // goes past his pause at her end step at once
    @Test fun blocksAreAssumedOnlyFromTheMainButton() {
        startGame(TestDeck.thirds(TestCards.cub), TestDeck.thirds(TestCards.herald),
            deal = { a, b -> "Mountain" in hand(a) && "Ember Cub" in hand(a) && "Mountain" in hand(b) && "Ember Herald" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Cub", this)
        waitUntil("the Cub on the battlefield", along = listOf(bob)) { alice.field("Ember Cub").isNotEmpty() && alice.inOwnMain() }
        alice.hold("To combat")
        // Bob's turn: a Mountain and an Ember Herald, so he has creatures that may block
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
        alice.tap("To combat")
        waitUntil("Alice declaring attackers") { alice.state.turn.step == TurnStep.DeclareAttackers }
        alice.stageAttacker("Ember Cub", this)
        waitUntil("the Cub staged") { alice.stepArrow() == "Attack (1)" }
        val offers = assumeOffers()
        alice.tap("Attack (1)")
        // Bob's pause after her attack is assumed at once: straight on to his blockers
        waitUntil("Alice waiting for Bob's blockers") {
            (alice.status as? GameDevice.Status.Waiting)?.let { it.reason == GameDevice.Wait.Blockers && it.offer == GameDevice.Offer.AssumeBlockers } == true
        }
        fun assumedBlocks() = alice.engine { device.session.events + device.session.pending }
            .count { e -> e.assumed?.assumedBy == alice.me && e.assumed?.input is Input.DeclareBlockers }
        repeat(3) { run(skip = Duration.ofMinutes(1)) }
        assertEquals(0, assumedBlocks(), "nothing assumed by itself")
        assertTrue(alice.showsText("${bob.name} is declaring blockers"), alice.screen())
        assertTrue(!alice.showsText("Assume ${bob.name}'s blockers"), "no screen: the lanes are the assume mode")
        alice.tap("Assume no blockers")
        waitUntil("the blocks assumed") {
            alice.engine { device.session.events + device.session.pending }.any { e ->
                e.assumed?.assumedBy == alice.me && (e.assumed?.input as? Input.DeclareBlockers)?.blocks?.isEmpty() == true }
        }
        assertTrue(offers.none { it == GameDevice.Offer.AssumeResolves }, "no wait with Assume after her attack: $offers")
        waitUntil("the game on past Bob's assumed blocks") { alice.status == GameDevice.Status.YourMove && alice.stepArrow() != null }
        // Her own priority after the blocks, then his: a wait with Assume (the setting)
        alice.tap(alice.stepArrow()!!)
        waitUntil("Alice waiting for Bob after his blocks") {
            (alice.status as? GameDevice.Status.Waiting)?.offer == GameDevice.Offer.AssumeResolves
        }
        alice.tap("Assume no response")
        waitUntil("Alice in her second main phase") { alice.inOwnMain(TurnStep.PostcombatMain) }
        offers.clear()
        alice.hold(alice.stepArrow()!!)
        waitUntil("Bob's turn on Alice's table") { alice.state.turn.active == bob.me && alice.engine { device.session.pending.isEmpty() } }
        assertTrue(offers.isEmpty(), "no wait with Assume at her end step: $offers")
        assertTrue(alice.leaks.leaks.isEmpty())
    }
}
