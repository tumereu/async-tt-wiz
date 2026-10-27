package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CardKeywordsTest {
    private fun card(id: String, note: String = "", eotNote: String = "") =
        PlayCard(id, "definition", "a", "zone", "face", note = note, eotNote = eotNote)

    @Test fun parsesCaseInsensitiveTrimmedCommaListsAndRejectsAnyOtherToken() {
        assertEquals(emptySet(), CardKeywords.parse(""))
        assertEquals(emptySet(), CardKeywords.parse("  "))
        assertEquals(setOf("Flying", "First strike", "Can't block"), CardKeywords.parse(" flying ,FIRST STRIKE,, can’t  block "))
        assertNull(CardKeywords.parse("Flying, gets +1/+1"))
        assertNull(CardKeywords.parse("Flying Trample"))
        assertNull(CardKeywords.parse("X=3"))
        assertEquals(setOf("Trample"), CardKeywords.granted("Trample, X=3, no flying"))
    }

    @Test fun formatsInListOrderAndRoundTripsEveryKeyword() {
        assertEquals("Flying, Trample", CardKeywords.format(setOf("Trample", "Flying")))
        assertEquals("", CardKeywords.format(emptySet()))
        assertEquals(16, CardKeywords.all.size)
        val everything = CardKeywords.format(CardKeywords.all.toSet())
        assertEquals(CardKeywords.all.toSet(), CardKeywords.parse(everything))
        assertEquals(CardKeywords.all.toSet(), CardKeywords.parse(everything.uppercase()))
    }

    @Test fun togglingAddsAndRemovesWithoutTouchingOtherKeywords() {
        val trample = card("a", note = "trample")
        assertEquals(listOf<PlayAction>(PlayAction.SetNote("a", "Flying, Trample")),
            CardKeywords.toggle(listOf(trample), "Flying", eot = false))
        assertEquals(listOf<PlayAction>(PlayAction.SetNote("a", "")), CardKeywords.toggle(listOf(trample), "Trample", eot = false))
        assertEquals(listOf<PlayAction>(PlayAction.SetEotNote("a", "Haste")),
            CardKeywords.toggle(listOf(trample), "Haste", eot = true))
        assertTrue(CardKeywords.toggle(listOf(card("a", note = "Custom text")), "Flying", eot = false).isEmpty())
    }

    @Test fun multiSelectionIsOnOnlyWhenAllHaveItAndTogglesAllOrDisables() {
        val cards = listOf(card("a", note = "Flying, Trample"), card("b", note = "Flying"), card("c"))
        assertEquals(emptySet(), CardKeywords.state(cards.map { it.note }))
        assertEquals(setOf("Flying"), CardKeywords.state(cards.take(2).map { it.note }))
        assertEquals(listOf<PlayAction>(PlayAction.SetNote("b", "Flying, Trample"), PlayAction.SetNote("c", "Trample")),
            CardKeywords.toggle(cards, "Trample", eot = false))
        assertEquals(listOf<PlayAction>(PlayAction.SetNote("a", "Trample"), PlayAction.SetNote("b", "")),
            CardKeywords.toggle(cards.take(2), "Flying", eot = false))
        val custom = cards + card("d", note = "Hello")
        assertNull(CardKeywords.state(custom.map { it.note }))
        assertTrue(CardKeywords.toggle(custom, "Haste", eot = false).isEmpty())
        assertNull(CardKeywords.state(emptyList()))
    }
}
