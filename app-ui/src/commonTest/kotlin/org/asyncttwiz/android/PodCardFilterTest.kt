package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.CatalogPrinting
import kotlin.test.*

/** The draft's quick filter over the Rotisserie pool and Build (DRF-250..DRF-255). Invented cards. */
class PodCardFilterTest {
    private fun card(name: String, cost: String, type: String, text: String = "", printing: String? = null) =
        CardDefinition(name, listOf(DefinitionFace("face-$name", name, typeLine = type, rulesText = text, manaCost = cost)), printingId = printing)
    private val cards = listOf(
        card("Ember Lash", "{R}", "Instant", "Ember Lash deals 3 damage to any target.", "p-lash"),
        card("Mossback Cub", "{1}{G}", "Creature — Bear", printing = "p-cub"),
        card("Tide Glimpse", "{U}", "Instant", "Draw a card.", "p-glimpse"),
        card("Waystone Idol", "{3}", "Artifact", "{T}: Add {C}.", "p-idol"),
        card("Skyforge Charm", "{R}{W}", "Instant", "Choose one — Skyforge Charm deals 4 damage to target creature; or creatures you control gain flying.", "p-charm"),
        card("Grove Sentinel", "{4}{G}{G}", "Creature — Treefolk Warrior", "Reach", "p-sentinel"))
    private val definitions = cards.mapIndexed { index, card -> "d$index" to card }.toMap()
    private val board = cards.indices.map { LimitedSlot(DraftCopy("c$it", "d$it"), null) }
    private val printings = mapOf("p-lash" to listOf(CardSearchPrinting("tst", "common")),
        "p-cub" to listOf(CardSearchPrinting("tst", "uncommon")), "p-glimpse" to listOf(CardSearchPrinting("tst", "common")),
        "p-idol" to listOf(CardSearchPrinting("tst", "rare")), "p-charm" to listOf(CardSearchPrinting("tst", "mythic")))
    private fun names(filter: PodCardFilter, known: Map<String, List<CardSearchPrinting>> = printings) =
        podRotisserieFilter(board, definitions, filter, known).map { definitions.getValue(it.copy.definition).name }

    // DRF-251: typed words match the name, the type line or the rules text, in any case; search terms work too
    @Test fun typedWordsMatchNameTypeOrRulesText() {
        assertEquals(listOf("Mossback Cub"), names(PodCardFilter(text = "MOSS")))
        assertEquals(listOf("Ember Lash", "Tide Glimpse", "Skyforge Charm"), names(PodCardFilter(text = "instant")))
        assertEquals(listOf("Ember Lash", "Skyforge Charm"), names(PodCardFilter(text = "damage")))
        assertEquals(listOf("Grove Sentinel"), names(PodCardFilter(text = "treefolk reach")), "every word applies")
        assertEquals(listOf("Mossback Cub", "Grove Sentinel"), names(PodCardFilter(text = "t:creature")))
        assertEquals(listOf("Tide Glimpse"), names(PodCardFilter(text = "instant -damage")))
        assertEquals(listOf("Ember Lash"), names(PodCardFilter(text = "!\"ember lash\"")), "an exact name is the name only")
    }

    // DRF-251: text that can't be read gives its reason and is left out, the chips still applying
    @Test fun unreadableTextIsLeftOutWithItsReason() {
        val filter = PodCardFilter(text = "t:", types = setOf(SearchType.ARTIFACT))
        assertNotNull(filter.error)
        assertEquals(listOf("Waystone Idol"), names(filter))
        assertEquals(cards.map { it.name }, names(PodCardFilter(text = "t:")))
        assertNull(PodCardFilter(text = "lash").error)
    }

    // DRF-252: within a row any chosen chip matches, and the rows all apply; Multicolour sits in the colours' row
    @Test fun chipsMatchAnyOfARowAndEveryRow() {
        assertEquals(listOf("Ember Lash", "Skyforge Charm"), names(PodCardFilter(colors = setOf(SearchColor.R))))
        assertEquals(listOf("Mossback Cub", "Skyforge Charm", "Grove Sentinel"),
            names(PodCardFilter(colors = setOf(SearchColor.G), multicolor = true)))
        assertEquals(listOf("Skyforge Charm"), names(PodCardFilter(multicolor = true)))
        assertEquals(listOf("Waystone Idol"), names(PodCardFilter(colors = setOf(SearchColor.C))))
        assertEquals(listOf("Ember Lash", "Mossback Cub", "Skyforge Charm"), names(PodCardFilter(colors = setOf(SearchColor.R, SearchColor.G),
            values = setOf(SearchManaValue.ONE, SearchManaValue.TWO))))
        assertEquals(listOf("Grove Sentinel"), names(PodCardFilter(values = setOf(SearchManaValue.SIX))), "6+")
        assertEquals(listOf("Mossback Cub", "Waystone Idol", "Grove Sentinel"),
            names(PodCardFilter(types = setOf(SearchType.CREATURE, SearchType.ARTIFACT))))
        assertEquals(listOf("Skyforge Charm"), names(PodCardFilter(text = "flying", types = setOf(SearchType.INSTANT))))
    }

    // DRF-252: rarity is the card's printing's, from card records; a card whose printing they don't know matches no rarity
    @Test fun rarityIsThePrintingsFromCardRecords() {
        assertEquals(listOf("Ember Lash", "Tide Glimpse"), names(PodCardFilter(rarities = setOf(SearchRarity.COMMON))))
        assertEquals(listOf("Waystone Idol", "Skyforge Charm"), names(PodCardFilter(rarities = setOf(SearchRarity.RARE, SearchRarity.MYTHIC))))
        assertEquals(listOf("Mossback Cub"), names(PodCardFilter(rarities = setOf(SearchRarity.UNCOMMON))))
        assertEquals(emptyList(), names(PodCardFilter(rarities = setOf(SearchRarity.COMMON)), known = emptyMap()))
        assertEquals(cards.map { it.name }, names(PodCardFilter(), known = emptyMap()))
    }

    // DRF-252: a printing's card from card records narrowed to the printing's own set (its rarity there); unknown ones left out
    @Test fun cardRecordsGiveEachPrintingItsOwnSetsRarity() {
        fun printing(id: String, set: String) = CatalogPrinting(id, "Card $id", set, "1", "English", "", null, false, emptyList(), "{}")
        val faces = mapOf("p1" to listOf(printing("p1", "AAA")), "p2" to listOf(printing("p2-front", "BBB"), printing("p2", "BBB")))
        val data = mapOf(
            "p1" to CardSearchCard("Card p1", faces = emptyList(), printings = listOf(CardSearchPrinting("zzz", "rare"), CardSearchPrinting("aaa", "common"))),
            "p2-front" to CardSearchCard("Card p2", faces = emptyList(), printings = listOf(CardSearchPrinting("bbb", "mythic"))))
        var asked: Collection<CatalogPrinting> = emptyList()
        val read = podFilterPrintings(listOf("p1", "p2", "p3"), { ids -> faces.filterKeys { it in ids } }) { asked = it; data }
        assertEquals(mapOf("p1" to listOf(CardSearchPrinting("aaa", "common")), "p2" to listOf(CardSearchPrinting("bbb", "mythic"))), read)
        assertEquals(listOf("p1", "p2-front"), asked.map { it.id }, "each card's front printing")
    }

    // DRF-253: anything set makes the filter active; Clear empties text and chips at once
    @Test fun activeUntilCleared() {
        val state = PodCardFilterState()
        assertFalse(state.filter.active)
        state.text("  ")
        assertFalse(state.filter.active, "blank text is nothing")
        state.toggle(SearchRarity.RARE)
        assertTrue(state.filter.active)
        state.toggle(SearchRarity.RARE)
        assertFalse(state.filter.active)
        state.text("cub"); state.toggleMulticolor(); state.toggle(SearchManaValue.TWO); state.toggle(SearchType.LAND); state.toggle(SearchColor.W)
        assertTrue(state.filter.active)
        state.clear()
        assertEquals(PodCardFilter(), state.filter)
    }

    // DRF-254: Build's columns keep their places, each showing only its matching cards
    @Test fun buildColumnsShowOnlyMatches() {
        fun tile(n: Int) = PodBuildTile("d$n", listOf("c$n"), cards[n], emptySet())
        val columns = listOf(listOf(tile(0), tile(2)), listOf(tile(1)), emptyList(), listOf(tile(3)))
        assertSame(columns, podBuildFiltered(columns, PodCardFilter()))
        assertEquals(listOf(listOf(tile(0), tile(2)), emptyList(), emptyList(), emptyList()),
            podBuildFiltered(columns, PodCardFilter(types = setOf(SearchType.INSTANT))))
        assertEquals(listOf(emptyList(), emptyList(), emptyList(), listOf(tile(3))),
            podBuildFiltered(columns, PodCardFilter(rarities = setOf(SearchRarity.RARE)), printings))
    }

    // DRF-255: each pod keeps a filter for its pool and one for Build, the same while the app runs
    @Test fun eachPodAndPlaceHasItsOwnFilter() {
        val filters = PodCardFilters()
        val pool = filters.of("pod-a", PodFilterPlace.Pool)
        pool.text("lash")
        assertSame(pool, filters.of("pod-a", PodFilterPlace.Pool))
        assertEquals("lash", filters.of("pod-a", PodFilterPlace.Pool).filter.text)
        assertEquals(PodCardFilter(), filters.of("pod-a", PodFilterPlace.Build).filter)
        assertEquals(PodCardFilter(), filters.of("pod-b", PodFilterPlace.Pool).filter)
    }

    // DRF-252: card records are read once per printing on the filters' worker; what they didn't know is asked again when a popup opens
    @Test fun printingsAreReadOnceAndUnknownOnesAgainOnOpening() {
        val asked = mutableListOf<Collection<String>>()
        val filters = PodCardFilters({ ids -> asked += ids; ids.filter { it != "p-new" }.associateWith { listOf(CardSearchPrinting("tst", "rare")) } })
        filters.load(listOf("p-lash", "p-new"))
        filters.load(listOf("p-lash", "p-new", "p-cub"))
        assertEquals(listOf(listOf("p-lash", "p-new"), listOf("p-cub")), asked.map { it.toList() })
        assertEquals(setOf("p-lash", "p-cub"), filters.printings.keys)
        filters.opened()
        filters.load(listOf("p-lash", "p-new", "p-cub"))
        assertEquals(listOf("p-new"), asked.last().toList())
    }
}
