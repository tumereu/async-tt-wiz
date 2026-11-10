package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.CatalogPrinting
import org.asyncttwiz.storage.DeckImportResult
import org.asyncttwiz.storage.DeckSection
import org.asyncttwiz.storage.ImportIssue
import org.asyncttwiz.storage.ResolvedDeckEntry
import org.junit.Test
import kotlin.test.*

class CardListEditsTest {
    private fun printing(name: String, id: String = name.lowercase().replace(' ', '-'), set: String = "TST", number: String = "1") =
        CatalogPrinting(id, name, set, number, "English", "", null, false, emptyList(), "{}")
    private fun entry(name: String, quantity: Int, section: DeckSection = DeckSection.MAINBOARD, id: String = name.lowercase().replace(' ', '-')) =
        ResolvedDeckEntry(section, quantity, listOf(printing(name, id)), listOf(1))

    // LOB-106: tapping a result adds one copy to the chosen section; another printing of a listed card joins it.
    @Test fun addGrowsTheSameCardInTheChosenSectionOrAppendsANewEntry() {
        val list = listOf(entry("Rune Bear", 2))
        val grown = CardListEdits.add(CardListKind.DECK, list, listOf(printing("Rune Bear", "other-printing")), DeckSection.MAINBOARD)
        assertNull(grown.refused)
        assertEquals(listOf(3), grown.entries.map { it.quantity })
        assertEquals("rune-bear", grown.entries.single().printing.id, "The listed printing is kept")
        val side = CardListEdits.add(CardListKind.DECK, list, listOf(printing("Rune Bear")), DeckSection.SIDEBOARD).entries
        assertEquals(listOf(DeckSection.MAINBOARD to 2, DeckSection.SIDEBOARD to 1), side.map { it.section to it.quantity })
        assertEquals(1, CardListEdits.count(side, "rune bear", DeckSection.SIDEBOARD))
        assertEquals(2, CardListEdits.count(side, "Rune Bear", DeckSection.MAINBOARD))
    }

    // LOB-063, DRF-007: the limit counts every section.
    @Test fun addingPastTheLimitIsRefusedWithAReason() {
        val full = listOf(entry("Rune Bear", 600), entry("Rune Bolt", 400, DeckSection.MAYBEBOARD))
        val refused = CardListEdits.add(CardListKind.DECK, full, listOf(printing("Rune Wall")), DeckSection.SIDEBOARD)
        assertEquals("A deck holds at most 1000 cards.", refused.refused)
        assertSame(full, refused.entries)
        assertEquals("A deck holds at most 1000 cards.",
            CardListEdits.increase(CardListKind.DECK, full, "rune-bear", DeckSection.MAINBOARD).refused)
        assertNull(CardListEdits.add(CardListKind.CUBE, full, listOf(printing("Rune Wall")), DeckSection.MAYBEBOARD).refused)
    }

    // LOB-142, LOB-108: the filter reads a listed card as search does: the catalog's card down to the entry's own
    // printing, otherwise its definition, which holds no rarity or legalities
    @Test fun aListedCardMatchesTheFilterAsSearchReadsIt() {
        fun query(text: String) = (CardSearchQuery.parse(text) as CardSearchParse.Valid).query
        val definition = CardDefinition("Rune Bear", listOf(DefinitionFace("face", "Rune Bear", typeLine = "Creature — Bear",
            manaCost = "{1}{G}", rulesText = "Vigilance", power = "2", toughness = "2")))
        val entry = ResolvedDeckEntry(DeckSection.MAINBOARD, 1, listOf(printing("Rune Bear", set = "AAA")), listOf(1))
        val read = listSearchCard(entry, definition, null)
        for (text in listOf("rune", "t:bear", "o:vigilance", "c:g", "id:g", "mv=2", "pow=2", "s:aaa", "c:g t:creature", "-t:land"))
            assertTrue(query(text).matches(read), text)
        for (text in listOf("t:land", "c:r", "s:bbb", "r:rare", "f:modern")) assertFalse(query(text).matches(read), text)
        val known = CardSearchCard("Rune Bear", faces = read.faces, identity = 16, legal = setOf("modern"),
            printings = listOf(CardSearchPrinting("bbb", "rare"), CardSearchPrinting("aaa", "common")))
        val catalog = listSearchCard(entry, definition, known)
        for (text in listOf("f:modern", "r:common", "s:aaa", "id:g")) assertTrue(query(text).matches(catalog), text)
        for (text in listOf("r:rare", "s:bbb")) assertFalse(query(text).matches(catalog), "$text is another printing, not this entry's")
    }

    // LOB-105: the card menu's +1, −1, Remove all and Move to…
    @Test fun menuEditsChangeOneEntryAndMovesJoinTheTargetSection() {
        val list = listOf(entry("Rune Bear", 2), entry("Rune Bolt", 1), entry("Rune Bear", 1, DeckSection.SIDEBOARD))
        assertEquals(listOf(3, 1, 1), CardListEdits.change(list, "rune-bear", DeckSection.MAINBOARD, 1).map { it.quantity })
        assertEquals(listOf(2, 1), CardListEdits.change(list, "rune-bear", DeckSection.SIDEBOARD, -1).map { it.quantity })
        assertEquals(listOf("Rune Bear", "Rune Bear"), CardListEdits.removeAll(list, "rune-bolt", DeckSection.MAINBOARD).map { it.printing.name })
        val moved = CardListEdits.move(list, "rune-bear", DeckSection.MAINBOARD, DeckSection.SIDEBOARD)
        assertEquals(listOf(DeckSection.MAINBOARD to 1, DeckSection.SIDEBOARD to 3), moved.map { it.section to it.quantity })
        val one = CardListEdits.move(list, "rune-bolt", DeckSection.MAINBOARD, DeckSection.MAYBEBOARD, 1)
        assertEquals(DeckSection.MAYBEBOARD, one.last().section)
        assertEquals(listOf(2, 1, 1), one.map { it.quantity })
        assertEquals(listOf(1, 1, 1), CardListEdits.removeOne(list, "rune bear", DeckSection.MAINBOARD).map { it.quantity })
    }

    // LOB-105, LOB-048 (owner, 2026-11-09): right moves a copy one section up, left one down, in the order Main, Side, Maybe,
    // wrapping; a cube's two sections either way to the other
    @Test fun swipesMoveUpRightAndDownLeft() {
        assertEquals(DeckSection.MAYBEBOARD, CardListKind.DECK.swipe(DeckSection.MAINBOARD, right = true))
        assertEquals(DeckSection.SIDEBOARD, CardListKind.DECK.swipe(DeckSection.MAINBOARD, right = false))
        assertEquals(DeckSection.MAINBOARD, CardListKind.DECK.swipe(DeckSection.SIDEBOARD, right = true))
        assertEquals(DeckSection.MAYBEBOARD, CardListKind.DECK.swipe(DeckSection.SIDEBOARD, right = false))
        assertEquals(DeckSection.SIDEBOARD, CardListKind.DECK.swipe(DeckSection.MAYBEBOARD, right = true))
        assertEquals(DeckSection.MAINBOARD, CardListKind.DECK.swipe(DeckSection.MAYBEBOARD, right = false))
        for (right in listOf(true, false)) {
            assertEquals(DeckSection.MAYBEBOARD, CardListKind.CUBE.swipe(DeckSection.MAINBOARD, right))
            assertEquals(DeckSection.MAINBOARD, CardListKind.CUBE.swipe(DeckSection.MAYBEBOARD, right))
        }
    }

    // LOB-059: lines with problems are underlined in red; general problems (line 0) and a changed text underline nothing.
    @Test fun problemLinesUnderlineOnlyTheLinesALookupFlagged() {
        val text = "Deck\n4 Rune Bear\r\nBad line\n\n2 Unknown"
        val result = DeckImportResult(text, emptyList(), listOf(ImportIssue(3, "Bad"), ImportIssue(5, "Unknown"), ImportIssue(0, "Too many")), null)
        assertEquals(setOf(3, 5), problemLines(text, result))
        assertEquals(emptySet(), problemLines("$text\n1 Rune Bolt", result))
        assertEquals(emptySet(), problemLines(text, null))
        val red = androidx.compose.ui.graphics.Color.Red
        val shown = ProblemLines(setOf(3, 4, 5), red).filter(androidx.compose.ui.text.AnnotatedString(text)).text
        assertEquals(text, shown.text, "The text itself is unchanged")
        assertEquals(listOf("Bad line", "2 Unknown"), shown.spanStyles.map { text.substring(it.start, it.end) }, "An empty line has nothing to underline")
        assertTrue(shown.spanStyles.all { it.item.color == red && it.item.textDecoration == androidx.compose.ui.text.style.TextDecoration.Underline })
    }

    // LOB-059: the Text tab shows a Cards tab edit in the same shape an existing list opens with.
    @Test fun textRewriteMatchesTheEditorSourceShape() {
        val card = CardDefinition("Rune Bear", listOf(DefinitionFace("rune", "Rune Bear", sourceData = "{\"setCode\":\"TST\",\"number\":\"7\"}")))
        val digest = DeckJson.digest(card)
        val detail = DeckDetail(SavedDeck(DeckSummary("d", "Deck", "1", "now", 2, 0),
            listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, digest, 2))), mapOf(digest to card))
        val source = DeckEditorSource(detail)
        val resolved = source.resolve(source.text) { error("No lookup") }
        assertEquals(source.text, CardListEdits.text(CardListKind.DECK, resolved.entries))
        assertEquals("Deck\n2 Rune Bear (TST) 1\nSideboard\nMaybeboard\n1 Rune Bolt (TST) 1",
            CardListEdits.text(CardListKind.DECK, listOf(entry("Rune Bear", 2), entry("Rune Bolt", 1, DeckSection.MAYBEBOARD))))
        assertEquals("Deck\n2 Rune Bear (TST) 1", CardListEdits.text(CardListKind.CUBE, listOf(entry("Rune Bear", 2))))
    }

    // LOB-119: quick chips: any chosen chip of one kind, all kinds and the typed text together.
    @Test fun quickChipsBecomeOneQuery() {
        assertEquals("", cardSearchQuery(" ", emptySet(), emptySet(), emptySet()))
        assertEquals("goblin (c:r or c:g) t:creature (mv=1 or mv>=6)", cardSearchQuery(" goblin ", setOf(SearchColor.G, SearchColor.R),
            setOf(SearchType.CREATURE), setOf(SearchManaValue.SIX, SearchManaValue.ONE)))
        assertEquals("(bolt or shock) c:c", cardSearchQuery("bolt or shock", setOf(SearchColor.C), emptySet(), emptySet()))
        CardSearchQuery.parse(cardSearchQuery("goblin", SearchColor.entries.toSet(), SearchType.entries.toSet(), SearchManaValue.entries.toSet()))
            .let { assertIs<CardSearchParse.Valid>(it) }
    }

    // LOB-106
    @Test fun doneLabelCountsTheCardsAddedInThisVisit() {
        assertEquals("Done", cardSearchDoneLabel(0))
        assertEquals("Done · +1 card", cardSearchDoneLabel(1))
        assertEquals("Done · +3 cards", cardSearchDoneLabel(3))
        assertEquals("Done · −2 cards", cardSearchDoneLabel(-2))
    }

    // LOB-056, DRF-010: Maybe shows only when it holds cards.
    @Test fun savedListsShowMaybeOnlyWithCards() {
        assertEquals(SavedDeckSection.PREPARED, savedListSections(SavedDeckSection.PREPARED, listOf(SavedDeckSection.MAINBOARD)))
        assertEquals(SavedDeckSection.entries, savedListSections(SavedDeckSection.PREPARED, listOf(SavedDeckSection.MAYBEBOARD)))
        assertEquals(SavedDeckSection.CUBE, savedListSections(listOf(SavedDeckSection.MAINBOARD), listOf(SavedDeckSection.MAYBEBOARD)))
        assertEquals("2 main · 1 maybe", cardListSummary(CardListKind.CUBE, listOf(entry("Rune Bear", 2), entry("Rune Bolt", 1, DeckSection.MAYBEBOARD))))
        assertEquals("0 main", cardListSummary(CardListKind.DECK, emptyList()))
    }

    // SYS-054: one colour per section.
    @Test fun sectionsHaveTheirOwnColours() {
        assertEquals(PodPickGreen, SavedDeckSection.MAINBOARD.sectionColor())
        assertEquals(PodBuildBlue, SavedDeckSection.SIDEBOARD.sectionColor())
        assertEquals(SectionMaybeAmber, SavedDeckSection.MAYBEBOARD.sectionColor())
        assertEquals(SectionMaybeYellow, SavedDeckSection.MAYBEBOARD.sectionFill())
    }

    // DRF-141
    @Test fun preparedCubeMovesJoinTheOtherSection() {
        val cube = PodCube("Runes", listOf(SavedCubeEntry("a", 2), SavedCubeEntry("b", 1, SavedDeckSection.MAYBEBOARD)))
        val moved = cube.moved(PodCubeMove("a", SavedDeckSection.MAINBOARD, 1))!!
        assertEquals(listOf(SavedCubeEntry("a", 1), SavedCubeEntry("b", 1, SavedDeckSection.MAYBEBOARD),
            SavedCubeEntry("a", 1, SavedDeckSection.MAYBEBOARD)), moved.entries)
        val back = moved.moved(PodCubeMove("a", SavedDeckSection.MAINBOARD, 1).inverse())!!
        assertEquals(listOf(SavedCubeEntry("a", 2), SavedCubeEntry("b", 1, SavedDeckSection.MAYBEBOARD)), back.entries)
        assertNull(cube.moved(PodCubeMove("b", SavedDeckSection.MAYBEBOARD, 2)))
    }

    @Test fun resultsOfAnEditStayResolved() {
        val result = DeckImportResult("", listOf(entry("Rune Bear", 1)), emptyList(), null)
        assertTrue(result.resolved)
    }
}
