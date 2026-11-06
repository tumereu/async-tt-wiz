package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import org.asyncttwiz.protocol.EngineRecordKind
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner gallery review, 2026-11-06 (emblems as chips): Alice casts an Ember Sigil and gets an emblem; it shows as a small
 * round chip by her life square on both phones, and holding it inspects her emblems until the finger lifts, showing the
 * emblem's text (bug found 2026-11-06: the parser dropped the quoted ability's text, so emblems had none).
 */
class EmblemInspectTest : E2e() {
    private val emblem = hasContentDescription("Emblem: ", substring = true)
    private val chips = hasTestTag("emblem-chip") and emblem
    /** The Ember Sigil's emblem's ability ([TestCards.sigil]). */
    private val EMBLEM_TEXT = "Creatures you control get +1/+1."

    /** Inspect's emblem tiles [player] sees now (the chips aside). */
    private fun tiles(player: E2ePlayer) = player.nodes(emblem).fetchSemanticsNodes().size - player.nodes(chips).fetchSemanticsNodes().size

    // TRN-053, SYS-066: the emblem's chip on both tables; holding it on either inspects Alice's emblems
    @Test fun holdingAnEmblemChipInspectsTheEmblems() {
        startGame(TestDeck.sigils(), aliceSettings = PlaySettings(alwaysAssumeResolves = true),
            deal = { a, _ -> "Ember Sigil" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Sigil", this)
        waitUntil("Alice's emblem", along = listOf(bob)) {
            if (alice.stepArrow() == "Resolve") alice.once { alice.tap("Resolve") }
            alice.shown.board.board.engineRecords.any { it.kind == EngineRecordKind.Emblem && it.player == alice.me.value } &&
                alice.inOwnMain()
        }
        waitUntil("the emblem chip by Alice's life square") { alice.shows(chips) }
        assertEquals(1, alice.nodes(chips).fetchSemanticsNodes().size)
        assertEquals(0, tiles(alice), "no Inspect before the hold")
        holding(alice.node(chips)) {
            assertTrue(alice.shows(hasContentDescription("Card inspection content")), alice.screen())
            assertEquals(1, tiles(alice), "the emblem's tile: ${alice.screen()}")
            assertTrue(alice.showsText("Emblem"), "its tile says what it is")
            // TRN-053 (bug 2026-11-06): the emblem's tile shows its text, the quoted ability as printed
            assertTrue(alice.showsText(EMBLEM_TEXT, substring = true), "the emblem's text: ${alice.screen()}")
        }
        assertTrue(!alice.shows(hasContentDescription("Card inspection content")), "closed on release")
        // Bob sees Alice's emblem by her life square and inspects it the same way
        waitUntil("Alice's emblem on Bob's table", along = listOf(alice)) { bob.shows(chips) }
        holding(bob.node(chips)) {
            assertEquals(1, tiles(bob), bob.screen())
            assertTrue(bob.showsText(EMBLEM_TEXT, substring = true), "the emblem's text on Bob's table: ${bob.screen()}")
        }
        assertTrue(!alice.refusalSeen && !bob.refusalSeen, "no refusal")
    }
}
