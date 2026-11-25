package org.asyncttwiz.android

import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.CardSearchPrinting
import org.asyncttwiz.protocol.DefinitionFace
import org.asyncttwiz.protocol.DraftColor
import org.asyncttwiz.protocol.DraftCopy
import kotlin.test.*

/** DRF-292: what the draft screens show of the bot with "Show bot picks" on; invented cards. */
class DraftBotViewTest {
    private fun card(name: String, cost: String, type: String, text: String = "", power: String = "", toughness: String = "") =
        CardDefinition(name, listOf(DefinitionFace("f-$name", name, typeLine = type, manaCost = cost, rulesText = text,
            power = power, toughness = toughness)), printingId = "p-$name")
    private val definitions = mapOf(
        "d-cub" to card("Dawn Cub", "{1}{W}", "Creature — Bear", power = "2", toughness = "2"),
        "d-tide" to card("Tide Cub", "{1}{U}", "Creature — Bear", power = "2", toughness = "2"),
        "d-hex" to card("Hex Bolt", "{B}", "Instant", "Destroy target creature."),
    )

    // DRF-292: the card records' rarity counts (the Tide Cub is a mythic here), the pool gives the colours, and the badge
    // and breakdown say it in words
    @Test fun theBotScoresTheOfferFromTheCardRecordsAndThePool() {
        val offer = listOf(DraftCopy("a", "d-cub"), DraftCopy("b", "d-tide"), DraftCopy("c", "d-missing"))
        val printings = mapOf("p-Tide Cub" to listOf(CardSearchPrinting("tst", "mythic")))
        val choice = draftBotChoice(offer, emptyList(), 1, definitions, printings)
        assertEquals(listOf("b"), choice.picks)
        assertEquals("Bot pick 51", draftBotBadgeText(choice, "b"))
        assertEquals("Bot 37", draftBotBadgeText(choice, "a"))
        assertEquals("Bot 40", draftBotBadgeText(choice, "c"))
        assertNull(draftBotBadgeText(choice, "not offered"))
        assertEquals(listOf("Base 40", "Mythic +14", "Stats 2/2 for 2 -3", "Total 51"), draftBotBreakdown(choice.score("b")!!))
        assertEquals(listOf("Unknown card 40", "Total 40"), draftBotBreakdown(choice.score("c")!!))
    }

    // DRF-292, DRF-312: where the pod's bots pick by card ratings, so does the view: a rated card's first part is its rating,
    // one without starts "No rating: base"
    @Test fun theViewUsesThePodsCardRatings() {
        val ratings = org.asyncttwiz.protocol.DraftBotRatings("TST", mapOf("Dawn Cub" to 600, "Tide Cub" to 520, "Filler" to 560))
        val offer = listOf(DraftCopy("a", "d-cub"), DraftCopy("b", "d-tide"), DraftCopy("c", "d-hex"))
        val choice = draftBotChoice(offer, emptyList(), 1, definitions, emptyMap(), ratings)
        assertEquals("Rating (GIH WR 60.0%) ${ratings.points(600)}", draftBotBreakdown(choice.score("a")!!).first())
        assertEquals("No rating: base 40", draftBotBreakdown(choice.score("c")!!).first())
        assertTrue(choice.score("a")!!.total > choice.score("b")!!.total)
    }

    // DRF-312: the view takes the ratings of a pod of one set whose bots pick by them, none for a cube or several sets
    @Test fun theRatedSetIsThePodsOneSet() {
        val set = org.asyncttwiz.protocol.PodSource(kind = org.asyncttwiz.protocol.PodSourceKind.SET, setCode = "TST", packSets = listOf("TST"))
        assertEquals("TST", podBotRatingsSet(set.copy(botRatings = true)))
        assertNull(podBotRatingsSet(set), "the server has no ratings for it")
        assertEquals("TST", podBotRatingsSet(set.copy(packSets = emptyList(), botRatings = true)))
        assertNull(podBotRatingsSet(set.copy(packSets = listOf("TST", "TWL"), botRatings = true)))
        assertNull(podBotRatingsSet(org.asyncttwiz.protocol.PodSource("Cube", 360)))
    }

    // DRF-292: the pick number is the cards picked over the keep count, plus one: thirteen white picks commit the bot fully
    @Test fun thePoolCommitsTheBotToItsColours() {
        val pool = List(13) { DraftCopy("w$it", "d-cub") }
        val offer = listOf(DraftCopy("a", "d-cub"), DraftCopy("b", "d-tide"), DraftCopy("c", "d-hex"))
        val one = draftBotChoice(offer, pool, 1, definitions, emptyMap())
        assertEquals(listOf(DraftColor.W), one.colors)
        assertEquals(100, one.commitment)
        // Hex Bolt's black can still be the second colour, so the removal stays the pick
        assertEquals(listOf("c"), one.picks)
        // Keeping two a pick, the same 13 cards are the 7th pick: less committed
        val two = draftBotChoice(offer, pool, 2, definitions, emptyMap())
        assertTrue(two.commitment in 1..99, "${two.commitment}")
        assertEquals(listOf("c", "a"), two.picks)
    }
}
