package org.asyncttwiz.android

import arcanum.api.CardType
import arcanum.api.CardTypeRef
import arcanum.api.ObjectFilter
import org.asyncttwiz.storage.CatalogCard
import org.asyncttwiz.storage.CatalogPrinting
import org.junit.Test
import kotlin.test.assertEquals

/** The card records' names for "choose a card name" (STK-041): each name of a card apart (CR 709.4a, 201.4b, 201.4d). */
class CatalogNamesTest {
    private fun printing(card: String, face: String?, type: String, token: Boolean = false) = CatalogPrinting(
        id = "$card/$face", name = card, set = "AAA", number = "1", language = "English", side = if (face == null) "" else "a", scryfallId = null,
        token = token, otherFaces = emptyList(), json = """{"type":"$type"${face?.let { ""","faceName":"$it"""" }.orEmpty()}}""")

    private val split = CatalogCard("split", "Dawn // Dusk", listOf(printing("Dawn // Dusk", "Dawn", "Instant"), printing("Dawn // Dusk", "Dusk", "Sorcery")))
    private val modal = CatalogCard("modal", "Dawn Path // Dawn Crag", listOf(printing("Dawn Path // Dawn Crag", "Dawn Path", "Instant"), printing("Dawn Path // Dawn Crag", "Dawn Crag", "Land")))
    private val plain = CatalogCard("plain", "Dawn Bear", listOf(printing("Dawn Bear", null, "Creature — Bear")))
    private val token = CatalogCard("token", "Dawn Soldier", listOf(printing("Dawn Soldier", null, "Creature — Soldier", token = true)))
    private val land = ObjectFilter.Type(CardTypeRef.Literal(CardType.Land))

    // CR 709.4a, 201.4b: a half by itself, not both
    @Test
    fun aSplitCardOffersEachHalfAndNeverBoth() {
        assertEquals(listOf("Dawn", "Dusk"), catalogNames(listOf(split), "d", ObjectFilter.Any))
        assertEquals(listOf("Dusk"), catalogNames(listOf(split), "dus", ObjectFilter.Any))
        assertEquals(emptyList(), catalogNames(listOf(split), "Dawn // Dusk", ObjectFilter.Any))
    }

    // CR 201.4b, 201.4d: the restriction is judged by the part that has the name
    @Test
    fun theRestrictionIsJudgedByThatPartAlone() {
        val nonland = ObjectFilter.Not(land)
        assertEquals(listOf("Dawn Path"), catalogNames(listOf(modal), "dawn", nonland))
        assertEquals(listOf("Dawn Crag"), catalogNames(listOf(modal), "dawn", land))
        assertEquals(listOf("Dusk"), catalogNames(listOf(split), "d", ObjectFilter.Type(CardTypeRef.Literal(CardType.Sorcery))))
    }

    // CR 201.4: a card's one name, and no token's
    @Test
    fun aOneNamedCardOffersItsNameAndATokenNone() {
        assertEquals(listOf("Dawn Bear"), catalogNames(listOf(plain, token), "dawn", ObjectFilter.Any))
    }
}
