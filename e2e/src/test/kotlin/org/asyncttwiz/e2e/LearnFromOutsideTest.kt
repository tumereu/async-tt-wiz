package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import org.asyncttwiz.protocol.PresenceState
import org.asyncttwiz.protocol.SavedDeckSection
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-09: cards with Learn are supported, outside the game meaning Side and Maybe. Alice's deck has Lessons in
 * Side and in Maybe, and a card that is no Lesson in Side; she learns.
 */
class LearnFromOutsideTest : E2e() {
    private val inPanel = hasAnyAncestor(hasContentDescription("Automatum choice"))
    private val inReveal = hasAnyAncestor(hasContentDescription("Reveal"))
    private val takeALesson = "A Lesson card from outside the game"

    /** Alice's spell resolves up to Learn's question, Bob's phone playing along. */
    private fun toLearnsQuestion() {
        waitUntil("Learn's question", along = listOf(bob)) {
            // Alice passes her own spell on (STK-026) as the main button says
            if (alice.stepArrow() == "Resolve" || alice.stepArrow() == "Pass") alice.once { alice.tap(alice.stepArrow()!!) }
            alice.showsText(takeALesson)
        }
        assertIs<Decision.ChooseOption>(alice.state.pending?.decision)
    }

    // LOB-141, CR 701.48a (constructed): the Lessons of Side and of Maybe are offered, neither was in the library, the Hound isn't
    // offered. The one she takes is revealed to Bob's phone, which shows nothing of the rest of her Side and Maybe
    @Test fun learnOffersTheSideAndMaybeLessonsAndRevealsOnlyTheOneTaken() {
        startGame(TestDeck.studies(), deal = { a, _ -> "Ember Study" in hand(a) })
        bothOpen()
        val outside = setOf("Cinder Hound", "Ember Primer", "Ember Treatise")
        assertEquals(outside, alice.state.player(alice.me).outside.map { cardName(alice.state, it) }.toSet())
        val notOutside = alice.state.player(alice.me).library + alice.state.player(alice.me).hand
        assertTrue(notOutside.none { cardName(alice.state, it) in outside }, "none of them in her library or hand")
        bothKeep(bobAway = false)

        alice.playFromHand("Ember Study", this)
        toLearnsQuestion()
        alice.text(takeALesson).performClick()
        waitUntil("the Lessons in Alice's panel", along = listOf(bob)) { alice.shows(hasContentDescription("Ember Treatise") and inPanel) }
        assertTrue(alice.shows(hasContentDescription("Ember Primer") and inPanel), "the Side's Lesson offered too: ${alice.screen()}")
        assertFalse(alice.shows(hasContentDescription("Cinder Hound") and inPanel), "no Lesson, not offered: ${alice.screen()}")
        for (card in outside) assertFalse(bob.shows(hasContentDescription(card)), "$card not on Bob's phone: ${bob.screen()}")

        alice.nodes(hasContentDescription("Ember Treatise") and inPanel)[0].performClick()
        waitUntil("the Treatise picked") { alice.shows(hasContentDescription("Ember Treatise, chosen") and inPanel) }
        alice.text("OK").performClick()

        // Bob isn't playing along here: his phone waits at the reveal
        waitUntil("the reveal on Bob's phone") { bob.shown.reveal != null && bob.shows(hasContentDescription("Ember Treatise") and inReveal) }
        assertTrue(bob.showsText("${alice.name} revealed · Ember Study"), bob.screen())
        for (card in outside - "Ember Treatise") assertFalse(bob.shows(hasContentDescription(card)), "$card not on Bob's phone: ${bob.screen()}")
        bob.revealOk()
        waitUntil("the Treatise in Alice's hand, on the server", along = listOf(bob)) {
            alice.hand("Ember Treatise").size == 1 && alice.engine { device.session.pending.isEmpty() }
        }
        assertEquals(setOf("Cinder Hound", "Ember Primer"), alice.state.player(alice.me).outside.map { cardName(alice.state, it) }.toSet())
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    // LOB-141, CR 701.48a (pod): the same from a draft: the copy of the Lesson she moved to Side and the one she moved to Maybe are
    // what Learn offers, and neither is in her library
    @Test fun aPodDecksSideAndMaybeLessonsAreOfferedToo() {
        launchPlayers()
        val lobby = ServerSetup.podGameOfOneCard(alice.account, bob.account, "E2E learn ${UUID.randomUUID().toString().take(6)}", TestCards.lecture)
        val deck = ServerSetup.head(alice.account, lobby.id).preparation!!.players.single { it.accountId == alice.id }.deck!!
        fun copies(section: SavedDeckSection) = deck.entries.filter { it.section == section && it.definition == TestCards.lecture.digest }
            .flatMap { entry -> List(entry.quantity) { deck.card(entry, it).id } }
        val side = copies(SavedDeckSection.SIDEBOARD)
        val maybe = copies(SavedDeckSection.MAYBEBOARD)
        assertEquals(1 to 1, side.size to maybe.size, "one copy each")

        game = ServerSetup.readyBoth(alice.account, bob.account, lobby)
        for (player in listOf(bob, alice)) player.openGame(game, this)
        for (player in listOf(alice, bob)) waitUntil("${player.name} seeing the other player online") {
            player.lobby.state.opponentPresence?.presence?.state == PresenceState.ONLINE
        }
        val outside = alice.state.player(alice.me).outside.map(::atw)
        assertTrue((side + maybe).all { it in outside }, "both outside the game: $outside")
        assertTrue(alice.state.player(alice.me).library.map(::atw).none { it in side + maybe }, "neither in her library")
        bothKeep(bobAway = false)

        alice.playFromHand("Ember Lecture", this)
        toLearnsQuestion()
        alice.text(takeALesson).performClick()
        waitUntil("the Lessons in Alice's panel", along = listOf(bob)) {
            alice.state.pending?.decision is Decision.ChooseObjects && alice.shows(hasContentDescription("Ember Lecture") and inPanel)
        }
        val choice = assertIs<Decision.ChooseObjects>(alice.state.pending?.decision)
        assertEquals((side + maybe).toSet(), choice.options.map(::atw).toSet(), "the Lessons of Side and Maybe, none of the Side's basics")
        // The two Lessons, and the spell itself as the panel's source (STK-044), which is a copy of the same card
        assertEquals(3, alice.nodes(hasContentDescription("Ember Lecture") and inPanel).fetchSemanticsNodes().size)

        // The source comes first, in the panel's heading
        alice.nodes(hasContentDescription("Ember Lecture") and inPanel)[2].performClick()
        waitUntil("a Lesson picked") { alice.shows(hasContentDescription("Ember Lecture, chosen") and inPanel) }
        alice.text("OK").performClick()
        waitUntil("one of them in Alice's hand, on the server", along = listOf(bob)) {
            alice.state.player(alice.me).hand.map(::atw).any { it in side + maybe } && alice.engine { device.session.pending.isEmpty() }
        }
        assertEquals(1, alice.state.player(alice.me).outside.map(::atw).count { it in side + maybe }, "the other stays outside")
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
