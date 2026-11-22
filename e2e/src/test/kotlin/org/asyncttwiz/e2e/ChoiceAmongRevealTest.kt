package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import arcanum.engine.state.ObjectId
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner decision 2026-11-21: when an effect reveals, looks at or exiles cards and then asks its player to choose among them, that
 * player gets one panel, the choice, showing all the cards (those that can't be chosen dimmed under "Other cards"), instead of a reveal
 * panel and then the choice. "Important that these remain as reveals to the other player": Bob's table still stops at a reveal or a
 * face-up exile with its panel, and of a private look he sees nothing. Alice casts her free Ember Unveiling (reveal the top four, a
 * creature card among them to choose), Ember Glimpse (look at the top four, a creature card among them to reveal) or Ember Excavation
 * (exile the top three, choose one of them).
 */
class ChoiceAmongRevealTest : E2e() {
    private val inChoice = hasAnyAncestor(hasContentDescription("Automatum choice"))
    private val inReveal = hasAnyAncestor(hasContentDescription("Reveal"))

    /** A game where Alice holds [spell] and her top [n] cards hold an Ember Cub and a Mountain; both phones at her Main 1.
     * Bob holds Mountains and Storm Sparks, which he can't cast in her first turn, so his phone passes by itself; hers assumes his passes
     * (no player plays along for him: that would tap his reveal's OK). */
    private fun start(spell: TestCard, n: Int = 4) {
        startGame(TestDeck("Choices", listOf(TestCards.mountain to 14, spell to 13, TestCards.cub to 13)),
            TestDeck("Sparks", listOf(TestCards.mountain to 20, TestCards.spark to 20)),
            aliceSettings = PlaySettings(assume = AssumeResolves.Always), deal = { a, _ ->
            spell.name in hand(a) && library(a).take(n).let { top -> "Ember Cub" in top && "Mountain" in top }
        })
        bothOpen()
        bothKeep(bobAway = false)
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
    }

    /** Alice casts [spell] and passes it on (STK-026) up to her choice among the cards, open on her phone. */
    private fun aliceCastsUpToHerChoice(spell: TestCard): Decision.ChooseObjects {
        alice.playFromHand(spell.name, this)
        waitUntil("Alice's choice among the cards") {
            if (alice.stepArrow() == "Resolve" || alice.stepArrow() == "Pass") alice.once { alice.tap(alice.stepArrow()!!) }
            alice.state.pending?.decision is Decision.ChooseObjects && alice.shows(hasContentDescription(spell.name) and inChoice)
        }
        return alice.state.pending?.decision as Decision.ChooseObjects
    }

    private fun name(id: ObjectId): String = assertNotNull(cardName(alice.state, id), "the name of $id")

    // STK-224, STK-225, HIS-155, HIS-110, HIS-150, STK-190: Alice's Unveiling reveals her top four cards: her phone opens no reveal panel,
    // only the choice, with the Ember Cub to choose and the other revealed cards dimmed under "Other cards" (a tap on one picks nothing);
    // Bob's phone stops at the reveal with its panel, "Alice revealed · Ember Unveiling", all four cards in it, closed by his own OK
    @Test fun aRevealThenChooseIsOnePanelForAliceAndStillARevealForBob() {
        start(TestCards.unveiling)
        val d = aliceCastsUpToHerChoice(TestCards.unveiling)
        assertTrue(d.others.isNotEmpty(), "other cards beside the choice: $d")
        assertNull(alice.shown.reveal, "no reveal panel of Alice's")
        assertFalse(alice.showsRevealOk(), "no reveal OK on her phone: ${alice.screen()}")
        assertTrue(alice.shows(hasText("Other cards") and isHeading()), "Other cards: ${alice.screen()}")
        assertTrue(alice.shows(hasContentDescription("Other cards, ${d.others.size}")), "every revealed card she can't choose: ${alice.screen()}")
        assertTrue(alice.shows(hasContentDescription("Ember Cub") and inChoice), "the Cub to choose")
        // Bob's table: the reveal, with all four cards
        val title = "${alice.name} revealed · Ember Unveiling"
        waitUntil("the reveal panel on Bob's phone") { bob.showsText(title) && bob.showsRevealOk() }
        val revealed = assertNotNull(bob.shown.reveal).cards
        assertEquals(4, revealed.size)
        assertEquals((d.options + d.others).map { (it as ObjectId.Card).card }.toSet(), revealed.toSet(), "all four in Bob's panel")
        for (id in d.options + d.others) assertTrue(bob.shows(hasContentDescription(name(id)) and inReveal), "${name(id)} in Bob's panel: ${bob.screen()}")
        // A card she can't choose stays unpicked; the Cub is picked, and OK puts it into her hand
        val other = d.others.map(::name).first { it != "Ember Unveiling" }
        alice.nodes(hasContentDescription(other) and inChoice)[0].performClick()
        assertFalse(alice.shows(hasContentDescription("$other, chosen")), "an other card isn't picked")
        alice.nodes(hasContentDescription("Ember Cub") and inChoice)[0].performClick()
        waitUntil("the Cub picked") { alice.shows(hasContentDescription("Ember Cub, chosen") and inChoice) }
        val hand = alice.state.player(alice.me).hand.size
        alice.text("OK").performClick()
        waitUntil("the Cub in Alice's hand, on the server") {
            alice.state.player(alice.me).hand.size == hand + 1 && alice.engine { device.session.pending.isEmpty() }
        }
        assertTrue(bob.showsText(title), "Bob's panel waits for his own OK")
        bob.revealOk()
        waitUntil("Bob's panel closed") { !bob.showsText(title) }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty(), "no leaks: ${alice.leaks.leaks + bob.leaks.leaks}")
    }

    // STK-224, STK-225: Alice's Glimpse looks at her top four cards privately: her choice shows them all, the Cub to reveal and the others
    // dimmed under "Other cards"; Bob's phone opens no panel and shows none of those cards while she chooses; once she reveals the Cub,
    // his phone opens the reveal panel with the Cub alone
    @Test fun aPrivateLookThenChooseIsOnePanelForAliceAndShowsBobNothingHidden() {
        start(TestCards.glimpse)
        val d = aliceCastsUpToHerChoice(TestCards.glimpse)
        assertTrue(d.others.isNotEmpty(), "other cards beside the choice: $d")
        assertNull(alice.shown.reveal, "no panel of Alice's")
        assertTrue(alice.shows(hasText("Other cards") and isHeading()), "Other cards: ${alice.screen()}")
        assertTrue(alice.shows(hasContentDescription("Other cards, ${d.others.size}")), "every card looked at she can't choose: ${alice.screen()}")
        val looked = (d.options + d.others).map { (it as ObjectId.Card).card }.toSet()
        // Bob, at the same point, sees nothing of the look
        waitUntil("Bob's table at Alice's choice") { bob.state.pending?.decision is Decision.ChooseObjects }
        run()
        assertNull(bob.shown.reveal, "no panel on Bob's phone for a private look")
        val bobSees = arcanum.engine.Host.visible(bob.state, bob.me).mapNotNull { bob.state.obj(it)?.card }.toSet()
        assertTrue(looked.none { it in bobSees }, "none of the cards looked at visible to Bob")
        assertFalse(bob.shows(hasContentDescription("Ember Cub")), "no Cub drawn on Bob's screen: ${bob.screen()}")
        assertFalse(bob.shows(hasText("Other cards")), "no other cards on Bob's screen: ${bob.screen()}")
        // She reveals the Cub: Bob's reveal panel shows it alone
        alice.nodes(hasContentDescription("Ember Cub") and inChoice)[0].performClick()
        waitUntil("the Cub picked") { alice.shows(hasContentDescription("Ember Cub, chosen") and inChoice) }
        alice.text("OK").performClick()
        waitUntil("the reveal of the Cub on Bob's phone") {
            bob.showsText("${alice.name} revealed · Ember Glimpse") && bob.showsRevealOk()
        }
        val revealed = assertNotNull(bob.shown.reveal).cards
        assertEquals(1, revealed.size, "the revealed Cub alone")
        assertTrue(bob.shows(hasContentDescription("Ember Cub") and inReveal), "the Cub in Bob's panel: ${bob.screen()}")
        assertNull(alice.shown.reveal, "the Cub she chose isn't news to Alice")
        bob.revealOk()
        waitUntil("Bob's panel closed, the game settled") { !bob.showsRevealOk() && alice.engine { device.session.pending.isEmpty() } }
        val after = arcanum.engine.Host.visible(bob.state, bob.me).mapNotNull { bob.state.obj(it)?.card }.toSet()
        assertTrue((looked - revealed.toSet()).none { it in after }, "the cards she didn't reveal stay hidden from Bob")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty(), "no leaks: ${alice.leaks.leaks + bob.leaks.leaks}")
    }

    // STK-225, HIS-233, HIS-110: Alice's Excavation exiles her top three cards face up and asks her to choose one of them: her phone
    // opens no exile panel, only the choice with the three cards; Bob's phone stops at the exile with its panel, "Alice exiled 3 cards
    // from their library", all three in it
    @Test fun anExileThenChooseIsOnePanelForAliceAndStillShownToBob() {
        start(TestCards.excavation, n = 3)
        val d = aliceCastsUpToHerChoice(TestCards.excavation)
        assertEquals(3, d.options.size, "the three exiled cards to choose from")
        assertNull(alice.shown.reveal, "no exile panel of Alice's")
        assertFalse(alice.showsRevealOk(), "no reveal OK on her phone: ${alice.screen()}")
        for (id in d.options) assertTrue(alice.shows(hasContentDescription(name(id)) and inChoice), "${name(id)} in her choice: ${alice.screen()}")
        waitUntil("the exile's panel on Bob's phone") { bob.showsText("${alice.name} exiled 3 cards from their library") && bob.showsRevealOk() }
        val shown = assertNotNull(bob.shown.reveal)
        assertEquals(alice.me, assertNotNull(shown.exiled).owner)
        assertEquals(d.options.map { (it as ObjectId.Card).card }.toSet(), shown.cards.toSet(), "all three in Bob's panel")
        for (id in d.options) assertTrue(bob.shows(hasContentDescription(name(id)) and inReveal), "${name(id)} in Bob's panel: ${bob.screen()}")
        bob.revealOk()
        waitUntil("Bob's panel closed") { !bob.showsRevealOk() }
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty(), "no leaks: ${alice.leaks.leaks + bob.leaks.leaks}")
    }
}
