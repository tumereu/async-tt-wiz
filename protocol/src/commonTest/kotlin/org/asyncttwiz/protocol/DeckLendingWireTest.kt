package org.asyncttwiz.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** LOB-244..LOB-252: deck categories, a game's deck settings and the invitation's lent-decks line on the wire. */
class DeckLendingWireTest {
    private val box = DeckCategory("c-box", "Battle box")
    private val red = DeckCategory("c-red", "Red", box.id)
    private val mono = DeckCategory("c-mono", "Mono", red.id)
    private val other = DeckCategory("c-other", "Aggro")
    private val all = listOf(box, red, mono, other)

    // LOB-259, LOB-245: choosing a category includes its subcategories; a category never moves into its own subtree
    @Test fun categoriesFormATree() {
        assertEquals(setOf(box.id, red.id, mono.id), DeckCategories.within(all, listOf(box.id)))
        assertEquals(setOf(red.id, mono.id, other.id), DeckCategories.within(all, listOf(red.id, other.id, "unknown")))
        assertEquals("Battle box › Red › Mono", DeckCategories.pathName(all, mono.id))
        assertEquals(listOf(other to 0, box to 0, red to 1, mono to 2), DeckCategories.ordered(all))
        assertFalse(DeckCategories.canMove(all, box.id, mono.id))
        assertFalse(DeckCategories.canMove(all, box.id, box.id))
        assertTrue(DeckCategories.canMove(all, mono.id, null))
        assertTrue(DeckCategories.canMove(all, other.id, red.id))
        assertFalse(DeckCategories.name(" "))
        assertFalse(DeckCategories.name(" padded"))
        assertTrue(DeckCategories.name("Battle box"))
    }

    // LOB-247, LOB-248: the settings' defaults are left out of a summary; the settings that need lending count only while lending
    @Test fun deckSettingsOnTheWire() {
        val json = GameWire.json
        val game = GameSummary("g", "Azure Spark", "2026-11-19T12:00:00Z", AccountIdentity("a", "Tuomas"), AccountIdentity("b", "Riley"),
            "round", "invite", InvitationState.PENDING)
        assertFalse("deckSettings" in json.encodeToString(GameSummary.serializer(), game))
        val settings = DeckSettings(DeckLending.Categories(listOf(box.id)), onlyLent = true, sameDeck = false, random = true, redeal = true)
        val sent = json.encodeToString(GameSummary.serializer(), game.copy(deckSettings = settings))
        assertEquals(settings, json.decodeFromString(GameSummary.serializer(), sent).deckSettings)
        assertEquals(DeckLending.All, json.decodeFromString(DeckSettings.serializer(), json.encodeToString(DeckSettings.serializer(), DeckSettings(DeckLending.All))).lend)
        assertTrue(settings.onlyLentDecks && settings.dealt && settings.newDealOnRematch)
        val off = settings.copy(lend = DeckLending.Off)
        assertFalse(off.lending || off.onlyLentDecks || off.dealt || off.newDealOnRematch)
    }

    // LOB-247: a deck settings change is a lobby request of the game's preparation round
    @Test fun aDeckSettingsCommandIsValidated() {
        fun command(settings: DeckSettings, invitation: String? = null) = GameCommand("00000000-0000-4000-8000-000000000001",
            GameScope("00000000-0000-4000-8000-000000000002", "00000000-0000-4000-8000-000000000003", invitation),
            GameAction.SetDeckSettings(settings), "00000000-0000-4000-8000-000000000004", "1")
        assertTrue(GameWire.valid(command(DeckSettings(DeckLending.All))))
        assertTrue(GameWire.valid(command(DeckSettings(DeckLending.Categories(listOf("00000000-0000-4000-8000-000000000005"))))))
        assertFalse(GameWire.valid(command(DeckSettings(DeckLending.Categories(emptyList())))))
        assertFalse(GameWire.valid(command(DeckSettings(DeckLending.Categories(listOf("not an id"))))))
        assertFalse(GameWire.valid(command(DeckSettings(), invitation = "00000000-0000-4000-8000-000000000006")))
    }

    // LOB-252: an invitation's notification says when its creator lends decks
    @Test fun anInvitationsNotificationSaysDecksAreLent() {
        val hint = ActivityHint("1", ActivityKind.INVITATION, "g", "i", "a", resourceName = "Azure Spark", opponentName = "Tuomas")
        assertEquals("Tuomas invited you · Azure Spark" to null, ActivityTexts.notification(hint))
        assertEquals("Tuomas invited you · Azure Spark" to "Tuomas lends you decks", ActivityTexts.notification(hint.copy(lendsDecks = true)))
        assertNull(ActivityTexts.detail(hint.copy(lendsDecks = true, podId = "pod")), "a pod challenge's decks are the pod's")
    }
}
