package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import arcanum.engine.state.ObjectId
import org.asyncttwiz.android.AutomatumBoards
import org.asyncttwiz.android.HandRow
import org.asyncttwiz.automatum.CardPlay
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ZON-079 (owner, 2026-11-12) on two phones: a card its player may cast from another zone (flashback from the graveyard), or
 * whose ability they may use there (unearth), shows at the right end of their hand row with its zone's badge and is used from
 * there as a hand card is, its chips included; the other player's table shows no such card.
 */
class HandRowOffZoneTest : E2e() {
    private val inRow get() = "Ember Flare, owner ${alice.name}, untapped, from graveyard"
    private val ghoulInRow get() = "Ember Ghoul, owner ${alice.name}, untapped, from graveyard"
    private val repriseInRow get() = "Ember Reprise, owner ${alice.name}, untapped, from graveyard"

    // ZON-079, CR 702.34a: Alice casts her Ember Flare from her hand, then from her graveyard by its flashback through her hand
    // row: two taps, Autotap tapping her Mountain; it is exiled and she gained 2 life twice
    @Test fun aFlashbackCardIsCastFromTheHandRow() {
        startGame(TestDeck("Flashback", listOf(TestCards.mountain to 20, TestCards.flare to 20)), TestDeck.glimmers(), deal = { a, _ ->
            hand(a).count { it == "Ember Flare" } >= 1 && hand(a).count { it == "Mountain" } >= 1
        })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").size == 1 && alice.inOwnMain() }
        alice.playFromHand("Ember Flare", this)
        waitUntil("the Flare in her graveyard, on the server", along = listOf(bob)) {
            alice.named(alice.state.player(alice.me).graveyard, "Ember Flare").size == 1 && alice.inOwnMain() &&
                alice.engine { device.session.pending.isEmpty() }
        }
        waitUntil("the Flare in her hand row: ${alice.screen()}") { alice.shows(hasContentDescription(inRow)) }
        assertFalse(bob.shows(hasContentDescription("from graveyard", substring = true)), "nothing of it in Bob's hand row")
        waitUntil("Alice's game settled") { alice.settled(this) }
        alice.described(inRow).performClick()
        waitUntil("the Flare raised in her hand row") { alice.raisedInHand() == "Ember Flare" && alice.settled(this) }
        alice.described(inRow).performClick()
        waitUntil("the Flare exiled, on the server", along = listOf(bob)) {
            alice.named(alice.state.exile, "Ember Flare").size == 1 && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
        }
        assertEquals(24, alice.state.player(alice.me).life, "2 life from each cast")
        assertEquals(true, alice.state.obj(alice.field("Mountain").single())?.tapped, "Autotap tapped the Mountain")
        assertFalse(alice.shows(hasContentDescription(inRow)), "out of her hand row")
    }

    private val greyed = SemanticsMatcher("greyed") { "can't be paid now" in it.config.getOrElse(SemanticsProperties.StateDescription) { "" } }

    /** Whether [card] glows on [player]'s table (TBL-117): Automatum lets them do something with it now other than make mana. */
    private fun glows(player: E2ePlayer, card: ObjectId): Boolean = AutomatumBoards.cachedCardPlays(player.shown.board, player.id)
        ?.of(atw(card)).orEmpty().any { it.legal && it.kind != CardPlay.Kind.Mana }

    /** Alice plays a Mountain and casts [name] (free) from her hand. */
    private fun mountainAndCast(name: String) {
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").size == 1 && alice.inOwnMain() }
        alice.playFromHand(name, this)
    }

    // ZON-079, ZON-080, TBL-117, CR 702.84a: Alice's Ember Ghoul, cast and sacrificed, is in her hand row from her graveyard,
    // glowing (her Mountain pays its unearth); a tap raises it with its Unearth {R} chip, and the chip unearths it: the Ghoul is
    // on the battlefield on both phones, out of her hand row
    @Test fun anUnearthCardIsUnearthedFromTheHandRow() {
        startGame(TestDeck("Unearth", listOf(TestCards.mountain to 20, TestCards.ghoul to 20)), TestDeck.glimmers(), deal = { a, _ ->
            hand(a).count { it == "Ember Ghoul" } >= 1 && hand(a).count { it == "Mountain" } >= 1
        })
        bothOpen()
        bothKeep(bobAway = false)
        mountainAndCast("Ember Ghoul")
        waitUntil("the Ghoul on the battlefield", along = listOf(bob)) { alice.field("Ember Ghoul").size == 1 && alice.settled(this) && alice.inOwnMain() }
        // Its one ability: a tap sacrifices it
        alice.battlefieldCard("Ember Ghoul").performClick()
        waitUntil("the Ghoul in her graveyard, on the server", along = listOf(bob)) {
            alice.named(alice.state.player(alice.me).graveyard, "Ember Ghoul").size == 1 && alice.inOwnMain() &&
                alice.state.stack.isEmpty() && alice.engine { device.session.pending.isEmpty() }
        }
        waitUntil("the Ghoul in her hand row: ${alice.screen()}") { alice.shows(hasContentDescription(ghoulInRow)) }
        waitUntil("Alice's game settled") { alice.settled(this) }
        assertTrue(glows(alice, alice.named(alice.state.player(alice.me).graveyard, "Ember Ghoul").single()), "it glows")
        alice.described(ghoulInRow).performClick()
        waitUntil("the Ghoul raised with its chip: ${alice.screen()}") {
            alice.raisedInHand() == "Ember Ghoul" && alice.shows(hasContentDescription("Way Unearth {R}"))
        }
        assertFalse(alice.shows(hasContentDescription("Way Unearth {R}") and greyed), "Unearth can be paid")
        alice.node(hasContentDescription("Way Unearth {R}")).performClick()
        waitUntil("the Ghoul unearthed, on the server and on Bob's phone", along = listOf(bob)) {
            alice.field("Ember Ghoul").size == 1 && bob.field("Ember Ghoul", owner = alice.me).size == 1 && alice.inOwnMain() &&
                alice.engine { device.session.pending.isEmpty() }
        }
        assertEquals(true, alice.state.obj(alice.field("Mountain").single())?.tapped, "Autotap tapped the Mountain")
        // Her table's hand row (its semantics keep the last animation's start, where the Ghoul was still in it)
        assertTrue(HandRow.cards(alice.shown.board, alice.id).isEmpty(), "out of her hand row")
        assertFalse(alice.refusalSeen, "no refusal or shake: ${alice.shownTexts}")
    }

    // ZON-079, ZON-080, TBL-128, CR 702.34a: Alice's Ember Reprise in her graveyard with one Mountain: its flashback {R}{R} can't
    // be paid, its "{R}: Return this card from your graveyard to your hand" can. It glows; a tap raises it, no shake, with the
    // greyed Flashback chip and the live ability chip, which returns it to her hand
    @Test fun aCardWhoseCastCantBePaidButWhoseAbilityCanRaisesWithBothChips() {
        startGame(TestDeck("Reprise", listOf(TestCards.mountain to 20, TestCards.reprise to 20)), TestDeck.glimmers(), deal = { a, _ ->
            hand(a).count { it == "Ember Reprise" } >= 1 && hand(a).count { it == "Mountain" } >= 1
        })
        bothOpen()
        bothKeep(bobAway = false)
        mountainAndCast("Ember Reprise")
        waitUntil("the Reprise in her graveyard, on the server", along = listOf(bob)) {
            alice.named(alice.state.player(alice.me).graveyard, "Ember Reprise").size == 1 && alice.inOwnMain() &&
                alice.engine { device.session.pending.isEmpty() }
        }
        waitUntil("the Reprise in her hand row: ${alice.screen()}") { alice.shows(hasContentDescription(repriseInRow)) }
        waitUntil("Alice's game settled") { alice.settled(this) }
        assertTrue(glows(alice, alice.named(alice.state.player(alice.me).graveyard, "Ember Reprise").single()), "it glows")
        alice.described(repriseInRow).performClick()
        val ability = hasContentDescription("Way Activate {R}")
        waitUntil("the Reprise raised with its chips: ${alice.screen()}") {
            alice.raisedInHand() == "Ember Reprise" && alice.shows(hasContentDescription("Way Flashback {R}{R}")) && alice.shows(ability)
        }
        assertTrue(alice.shows(hasContentDescription("Way Flashback {R}{R}") and greyed), "Flashback greyed: ${alice.screen()}")
        assertFalse(alice.shows(ability and greyed), "the ability can be paid")
        assertFalse(alice.refusalSeen, "no shake: ${alice.shownTexts}")
        alice.node(ability).performClick()
        waitUntil("the Reprise back in her hand, on the server", along = listOf(bob)) {
            alice.hand("Ember Reprise").isNotEmpty() && alice.state.player(alice.me).graveyard.isEmpty() && alice.inOwnMain() &&
                alice.engine { device.session.pending.isEmpty() }
        }
        assertFalse(alice.refusalSeen, "no refusal or shake: ${alice.shownTexts}")
    }
}
