package org.asyncttwiz.e2e

import org.asyncttwiz.android.TablePresentation
import org.asyncttwiz.protocol.PlayZone
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-05 (SYS-072): a token that is a copy of a card (offspring, populate, embalm, myriad, encore, …) shows the
 * copied card's picture, not a token picture.
 */
class CopyTokenPictureTest : E2e() {
    // SYS-072: Alice casts an Ember Twin, which enters and makes a token copy of itself: on both tables the token is
    // drawn with the Twin's own picture, still as a token
    @Test fun aTokenCopyOfACardShowsTheCardsPicture() {
        startGame(TestDeck.thirds(TestCards.twin), deal = { a, _ -> "Mountain" in hand(a) && "Ember Twin" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Twin", this)
        waitUntil("the Twin and its copy on Alice's battlefield", along = listOf(bob)) {
            alice.field("Ember Twin").size == 2 && alice.inOwnMain()
        }
        waitUntil("both on Bob's table", along = listOf(bob)) { bob.field("Ember Twin", alice.me).size == 2 }
        val art = assertNotNull(TestCards.twin.definition.faces.single().scryfallId)
        for (player in listOf(alice, bob)) {
            val play = player.shown.board
            fun name(card: org.asyncttwiz.protocol.PlayCard) = play.definitions.firstOrNull { it.digest == card.definition }?.definition?.name
            val twins = play.board.cards.filter { name(it) == "Ember Twin" }
            val token = assertNotNull(twins.singleOrNull { it.token }, "${player.name}: the token copy among $twins")
            val face = play.definitions.single { it.digest == token.definition }.definition.faces.single()
            assertEquals(art, face.scryfallId, "${player.name}: the token copy is drawn with the Twin's picture")
            val scene = TablePresentation.scene(game, play, player.id, 412f, 892f, false)
            val item = scene.items.single { it.cardId == token.id && it.zone == PlayZone.BATTLEFIELD }
            assertNotNull(item.artwork, "${player.name}: the token copy's picture on the table")
            assertTrue(item.token, "${player.name}: still drawn as a token")
        }
    }
}
