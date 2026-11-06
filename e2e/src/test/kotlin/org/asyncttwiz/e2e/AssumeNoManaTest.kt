package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-05 evening: "Assume resolves" has a third choice, "When they have no mana": the other player's
 * pause is assumed at once while the table shows them no untapped mana source, no mana in their pool and no ability they
 * could activate; otherwise it waits with Assume on the main button. Alice plays with that choice; Bob is online but
 * doesn't answer.
 */
class AssumeNoManaTest : E2e() {
    /** Every wait with an Assume button Alice's table shows from now on. */
    private fun assumeOffers(): List<GameDevice.Offer> {
        val offers = mutableListOf<GameDevice.Offer>()
        everyRun += { (alice.game?.shown?.status as? GameDevice.Status.Waiting)?.offer?.let(offers::add) }
        return offers
    }

    /** Alice casts a Storm Spark at Bob: a tap raises it, a tap casts it (Autotap pays), a tap on his life square targets him. */
    private fun aliceSparksBob() {
        alice.playFromHand("Storm Spark", this)
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose any target") and isHeading()) }
        alice.node(hasContentDescription("${bob.name}, life", substring = true)).performClick()
    }

    // HIS-096, TRN-059, TRN-070: in her first turn Bob has no mana source: her Spark's pause for him is assumed at once;
    // in her next turn he has an untapped Mountain: her Spark waits for him with "Assume resolves"
    @Test fun aSpellIsAssumedAtOnceOnlyWhileTheyHaveNoMana() {
        startGame(TestDeck.sparks(), TestDeck.sparks(), aliceSettings = PlaySettings().assuming(AssumeResolves.WhenNoMana),
            deal = { a, b -> "Mountain" in hand(a) && hand(a).count { it == "Storm Spark" } >= 2 && "Mountain" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        assertEquals(AssumeResolves.WhenNoMana, alice.shown.settings.assume)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }

        // Bob has nothing on the battlefield: no wait
        val offers = assumeOffers()
        aliceSparksBob()
        waitUntil("the Spark resolved without waiting for Bob") {
            alice.state.player(bob.me).life == 17 && alice.inOwnMain()
        }
        assertTrue(offers.isEmpty(), "no wait with Assume: $offers")
        assertTrue(alice.engine { (device.session.events + device.session.pending).any { it.assumed?.assumedBy == alice.me } },
            "Bob's pass assumed by Alice's device")

        // Her turn ends; Bob plays a Mountain in his and ends it
        alice.hold(requireNotNull(alice.stepArrow()))
        waitUntil("Alice's next Main 1", along = listOf(alice, bob)) { alice.inOwnMain() && alice.state.turn.number >= 3 }
        val mountain = alice.field("Mountain", owner = bob.me).single()
        assertFalse(alice.state.obj(mountain)!!.tapped, "Bob's Mountain untapped")

        // Now he has an untapped Mountain: her Spark waits for him
        aliceSparksBob()
        waitUntil("Alice waiting for Bob at her Spark") {
            (alice.status as? GameDevice.Status.Waiting)?.offer == GameDevice.Offer.AssumeResolves
        }
        assertEquals(17, alice.state.player(bob.me).life, "not resolved yet")
        alice.tap("Assume resolves")
        waitUntil("the Spark resolved on the assumed pass") { alice.state.player(bob.me).life == 14 }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
