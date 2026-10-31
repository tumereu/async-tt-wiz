package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class PodLimitedModelsTest {
    private fun card(name: String, cost: String, type: String) =
        CardDefinition(name, listOf(DefinitionFace("face-$name", name, typeLine = type, manaCost = cost)))
    private val cards = listOf(card("Shock", "{R}", "Instant"), card("Grizzly Bears", "{1}{G}", "Creature — Bear"),
        card("Opt", "{U}", "Instant"), card("Sol Ring", "{1}", "Artifact"), card("Boros Charm", "{R}{W}", "Instant"))
    private val definitions = cards.mapIndexed { index, card -> "d$index" to card }.toMap()
    private val board = cards.indices.map { LimitedSlot(DraftCopy("c$it", "d$it"), "bob".takeIf { _ -> it == 1 }) }

    // DRF-144: the pool sorts like Build columns and filters with the catalog search syntax.
    @Test fun rotisseriePoolSortsByColourAndFiltersWithCardSearch() {
        assertEquals(listOf("Opt", "Shock", "Grizzly Bears", "Boros Charm", "Sol Ring"),
            podRotisserieOrder(board, definitions).map { definitions.getValue(it.copy.definition).name })
        fun names(query: String) = podRotisserieFilter(board, definitions, query).map { definitions.getValue(it.copy.definition).name }
        assertEquals(listOf("Shock", "Opt", "Boros Charm"), names("t:instant"))
        assertEquals(listOf("Shock", "Boros Charm"), names("c:r"))
        assertEquals(listOf("Grizzly Bears"), names("mv=2 t:creature"))
        assertEquals(listOf("Sol Ring"), names("ring"))
        assertEquals(cards.map { it.name }, names("  "))
        assertEquals(cards.map { it.name }, names("t:(")) // an unreadable query filters nothing
    }

    // DRF-071, DRF-144
    @Test fun turnTextsNameWhoseTurnItIs() {
        val members = listOf(AccountIdentity("a", "Ann"), AccountIdentity("b", "Bob"))
        assertEquals("Your pick", podTurnText("a", "a", members))
        assertEquals("Waiting for Bob", podTurnText("b", "a", members))
        assertEquals("Finished", podTurnText(null, "a", members))
    }

    // DRF-013: turn-based engines read "Your pick" without a pack count; Rochester counts its packs.
    @Test fun turnBasedPodsReadYourPickWithoutACount() {
        val ann = AccountIdentity("a", "Ann")
        fun summary(mode: DraftMode) = PodSummary("p", "Pod", PodPhase.DRAFTING, ann, listOf(ann), PodAttention.YOUR_PICK, 2, "", mode)
        assertEquals("Your pick", podStatus(summary(DraftMode.ROTISSERIE)))
        assertEquals("Your pick", podStatus(summary(DraftMode.WINSTON)))
        assertEquals("Your pick · 2 packs", podStatus(summary(DraftMode.ROCHESTER)))
        assertEquals("Your pick · 2 packs", podStatus(summary(DraftMode.BOOSTER)))
    }

    // DRF-146: your packs first, then the rest by number, each with whose pick it is.
    @Test fun rochesterPacksPutYourPicksFirst() {
        val members = listOf(AccountIdentity("a", "Ann"), AccountIdentity("b", "Bob"))
        fun pack(number: Int, current: String?) = LimitedPackView("p$number", number, "a", emptyList(), current, 0)
        val view = LimitedView(DraftMode.ROCHESTER, "a", listOf("a", "b"), emptyList(), false,
            packs = listOf(pack(1, "b"), pack(2, "a"), pack(3, null), pack(4, "a")))
        assertEquals(listOf(2, 4, 1, 3), podRochesterOrder(view).map { it.number })
        assertEquals(listOf("Waiting for Bob · Pack 1", "Your pick · Pack 2", "Finished · Pack 3"),
            view.packs.take(3).map { podRochesterHeader(it, view, members) })
    }

    // DRF-147, DRF-148
    @Test fun winstonPassAndPileHeaders() {
        val members = listOf(AccountIdentity("a", "Ann"), AccountIdentity("b", "Bob"))
        val card = DraftCopy("c", "d")
        val view = LimitedView(DraftMode.WINSTON, "a", listOf("a", "b"), emptyList(), false, currentPlayer = "a",
            stack = 0, piles = listOf(listOf(card), listOf(card, null), emptyList()), pile = 1)
        assertFalse(podWinstonCanPass(view))
        assertTrue(podWinstonCanPass(view.copy(pile = 0)))
        assertTrue(podWinstonCanPass(view.copy(stack = 1)))
        assertEquals("Pile 2 · 2 cards · you look at it", podPileHeader(1, 2, view, members))
        assertEquals("Pile 1 · 1 card", podPileHeader(0, 1, view, members))
        assertEquals("Pile 2 · 2 cards · Bob looks at it", podPileHeader(1, 2, view.copy(currentPlayer = "b"), members))
        assertEquals("Your pick · Stack: 0 cards", podPileTurnText(view, members))
    }
}
