package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import arcanum.engine.state.PlayerId
import org.asyncttwiz.android.GAME_END_DIALOG_TAG
import org.asyncttwiz.android.GAME_END_OK_TAG
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-11-19 (LOB-247..LOB-256): a new player plays with minimal hassle. Alice lends Bob, a fresh account with no deck of
 * his own, decks from her Battle box category: as a Random battle box both just press Ready and the server deals the decks
 * (and deals again for the rematch), or Bob picks one of her lent decks himself.
 */
class DeckLendingFlowTest : E2e() {
    private val boxDecks = listOf(TestDeck("Mountains", listOf(TestCards.mountain to 40)), TestDeck("Forests", listOf(TestCards.forest to 40)),
        TestDeck("Islands", listOf(TestCards.island to 40)))
    private val dialog = hasTestTag(GAME_END_DIALOG_TAG)

    /** The deck of [boxDecks] [player] was dealt, by the cards in their opening hand. */
    private fun dealt(player: E2ePlayer): String {
        val state = player.game!!.shown!!.engine
        val hand = state.player(PlayerId(player.id)).hand.mapNotNull { cardName(state, it) }.toSet()
        return boxDecks.single { deck -> deck.entries.single().first.name in hand }.name
    }

    /** Opens [game]'s lobby from the Games list (it is still being set up, so it opens as its lobby). */
    private fun E2ePlayer.openLobby(game: GameSummary) {
        waitUntil("$name's menu") { shows(hasTestTag("menu-GAMES")) }
        tagged("menu-GAMES").performClick()
        waitUntil("the game in $name's Games list") { showsText(game.gameName) }
        nodes(hasText(game.gameName)).onLast().performScrollTo().performClick()
        waitUntil("$name in the game's lobby") { shows(hasTestTag("lobby-history-tab")) }
    }

    /** Bob concedes from Table actions and Alice taps OK on the result: both are back in the lobby. */
    private fun bobConcedes() {
        bob.tap("Table actions")
        waitUntil("Bob's table actions") { bob.showsText("Concede") }
        bob.text("Concede").performClick()
        waitUntil("the Concede dialog") { bob.showsText("You lose this game at once.") }
        bob.text("Concede").performClick()
        val ok = hasTestTag(GAME_END_OK_TAG) and isEnabled()
        waitUntil("Alice's result to OK") { alice.shows(hasText("You won") and hasAnyAncestor(dialog)) && alice.shows(ok) }
        alice.node(ok).performClick()
        waitUntil("both back in the lobby") {
            listOf(alice, bob).all { it.lobby.state.detail?.lifecycle == GameLifecycle.LOBBY && it.lobby.state.detail?.resultNotice == null }
        }
    }

    // LOB-250, LOB-251, LOB-252, LOB-249, LOB-254, LOB-247: Alice's Random battle box (only lent decks, never the same deck, new
    // decks on rematch); Bob accepts her invitation, which says she lends him decks, and only presses Ready: the game starts
    // with a different box deck dealt to each; after Bob concedes, the rematch's lobby has no decks and both Readys deal again
    @Test fun aFreshAccountPlaysARandomBattleBoxAndTheRematchDealsAgain() {
        launchPlayers()
        val (a, b) = alice.account to bob.account
        assertTrue(ServerSetup.decks(b).isEmpty(), "Bob has no deck of his own")
        val box = ServerSetup.category(a, "Battle box")
        for (deck in boxDecks) ServerSetup.deck(a, deck, box.id)
        val game = ServerSetup.invite(a, b, "E2E battle box ${UUID.randomUUID().toString().take(6)}")
        val settings = DeckSettings(DeckLending.Categories(listOf(box.id)), onlyLent = true, sameDeck = false, random = true, redeal = true)
        assertEquals(listOf("accepted", "accepted"), ServerSetup.attempt(a, game, GameAction.SetDeckSettings(settings), GameAction.Ready)
            .map { it.reason ?: it.outcome })

        // Bob's Games list: the invitation says Alice lends him decks; he accepts it there
        waitUntil("Bob's menu") { bob.shows(hasTestTag("menu-GAMES")) }
        bob.tagged("menu-GAMES").performClick()
        waitUntil("the invitation saying decks are lent") { bob.showsText("${a.identity.username} lends you decks") }
        val accept = hasAnyChild(hasContentDescription("Accept invitation")) and isEnabled()
        waitUntil("Bob's Accept") { bob.shows(accept) }
        bob.node(accept).performClick()
        waitUntil("the invitation accepted") { ServerSetup.head(b, game.id).summary.invitationState == InvitationState.ACCEPTED }

        // Accepting opens the game's lobby (LOB-034), where nobody chooses: Bob's deck is dealt as the game starts; Ready is all he does
        waitUntil("Bob in the game's lobby") { bob.shows(hasTestTag("lobby-history-tab")) }
        waitUntil("Bob's lobby with the deal to come") { bob.showsText("Dealt at random when the game starts") }
        assertTrue(!bob.showsText("Choose deck"), "no Choose deck with Random")
        val ready = hasClickAction() and hasAnyDescendant(hasText("Ready")) and isEnabled()
        waitUntil("Bob's Ready") { bob.shows(ready) }
        bob.node(ready).performClick()
        waitUntil("the game started") { ServerSetup.head(b, game.id).summary.lifecycle == GameLifecycle.ACTIVE }

        alice.openGame(game, this)
        // Bob's lobby turns into the table as the game starts
        waitUntil("Bob's table") { bob.game?.shown != null }
        val first = dealt(alice) to dealt(bob)
        assertNotEquals(first.first, first.second, "different decks without Same deck allowed")

        // The rematch: back in the lobby the decks are gone, to be dealt again; both press Ready and a new deal starts it
        bobConcedes()
        waitUntil("the rematch's lobby without decks") {
            listOf(alice, bob).all { player -> player.lobby.state.preparation?.players?.all { it.deck == null && !it.ready } == true }
        }
        // The rematch is a new lobby round
        assertEquals(listOf("accepted"), ServerSetup.attempt(a, ServerSetup.head(a, game.id).summary, GameAction.Ready).map { it.reason ?: it.outcome })
        waitUntil("Bob's Ready for the rematch") { bob.shows(ready) }
        bob.node(ready).performClick()
        waitUntil("the rematch started") { ServerSetup.head(b, game.id).summary.lifecycle == GameLifecycle.ACTIVE }
        waitUntil("Bob's rematch table") { bob.game?.shown?.engine?.let { s -> s.player(PlayerId(bob.id)).hand.isNotEmpty() } == true &&
            bob.lobby.state.detail?.playId != null }
        assertTrue(dealt(bob) in boxDecks.map { it.name })
    }

    // LOB-255, LOB-256, LOB-053: Each picks: Bob opens Choose deck, takes Alice's lent Burn from under "<Alice>'s decks", and his
    // card shows it from her; the server holds the copy with its lender; nothing is saved to Bob's account
    @Test fun eachPicksBorrowsALentDeck() {
        launchPlayers()
        val (a, b) = alice.account to bob.account
        val box = ServerSetup.category(a, "Battle box")
        val burn = ServerSetup.deck(a, TestDeck("Burn", listOf(TestCards.mountain to 20, TestCards.spark to 20)), box.id)
        ServerSetup.deck(a, TestDeck("Not lent", listOf(TestCards.forest to 40)))
        val game = ServerSetup.invitation(a, b, "E2E borrowed ${UUID.randomUUID().toString().take(6)}")
        assertEquals(listOf("accepted"), ServerSetup.attempt(a, game,
            GameAction.SetDeckSettings(DeckSettings(DeckLending.Categories(listOf(box.id))))).map { it.reason ?: it.outcome })

        bob.openLobby(game)
        waitUntil("Bob's Choose deck") { bob.showsText("Choose deck") }
        bob.text("Choose deck").performScrollTo().performClick()
        waitUntil("Alice's lent decks first") { bob.showsText("${a.identity.username.uppercase()}'S DECKS") && bob.showsText("Burn") }
        assertTrue(!bob.showsText("Not lent"), "a deck Alice doesn't lend isn't listed")
        bob.text("Burn").performScrollTo().performClick()
        bob.text("Use selected deck").performClick()
        waitUntil("the borrowed deck chosen, on the server") {
            bob.lobby.state.acceptedPreparation?.players?.firstOrNull { it.accountId == b.id }?.deck?.lender == a.id
        }
        waitUntil("Bob's card naming the lender") { bob.showsText("from ${a.identity.username}", substring = true) }
        val chosen = bob.lobby.state.acceptedPreparation!!.players.single { it.accountId == b.id }
        assertEquals(burn.deck.id, chosen.deck!!.sourceId)
        assertTrue(chosen.deckShown, "a borrowed deck shows to both players")
        assertTrue(ServerSetup.decks(b).isEmpty(), "nothing saved to Bob's account")
    }
}
