package arcanum.parser

import kotlin.test.Test
import kotlin.test.assertEquals

/** Test cards are invented (names and costs are ours, wording follows real oracle templates). */
class OracleNormalizerTest {
    private fun patterns(name: String, text: String, keywords: List<String> = emptyList(), types: Set<String> = emptySet()) =
        OracleNormalizer.patterns(OracleFace(name, text, keywords), types)

    private fun ability(text: String) = Pattern(PatternKind.ABILITY, text)

    @Test
    fun numbersBecomeNSoAnthemsOfDifferentSizeShareAPattern() {
        assertEquals(listOf(ability("Other artifact creatures you control get +N/+N.")),
            patterns("Brightforge Banner", "Other artifact creatures you control get +1/+1."))
        assertEquals(listOf(ability("Other artifact creatures you control get +N/+N.")),
            patterns("Gearwright Standard", "Other artifact creatures you control get +2/+2."))
    }

    @Test
    fun cardNameAndThisObjectWordingBothBecomeTilde() {
        assertEquals(listOf(ability("When ~ enters, draw a card.")),
            patterns("Emberlash Adept", "When Emberlash Adept enters, draw a card."))
        assertEquals(listOf(ability("When ~ enters, draw a card.")),
            patterns("Thornveil Scout", "When this creature enters, draw a card."))
    }

    @Test
    fun legendaryShortNameBecomesTilde() {
        assertEquals(listOf(ability("Whenever ~ attacks, ~ deals N damage to any target.")),
            patterns("Velka, Storm Herald", "Whenever Velka attacks, Velka deals 2 damage to any target."))
    }

    @Test
    fun nameInsideALongerWordIsKept() {
        assertEquals(listOf(ability("Create a Fogbound token.")), patterns("Fog", "Create a Fogbound token."))
    }

    @Test
    fun keywordLinesSplitIntoOneKeywordEachWithoutReminderText() {
        assertEquals(listOf(Pattern(PatternKind.KEYWORD, "Flying"), Pattern(PatternKind.KEYWORD, "Vigilance")),
            patterns("Skyreach Warden", "Flying, vigilance (Attacking doesn't cause this creature to tap.)",
                listOf("Flying", "Vigilance")))
    }

    @Test
    fun keywordCostsAreNormalised() {
        assertEquals(listOf(Pattern(PatternKind.KEYWORD, "Equip {M}"), Pattern(PatternKind.KEYWORD, "Ward—Pay N life.")),
            patterns("Runed Gauntlet", "Equip {2}{W}\nWard—Pay 3 life.", listOf("Equip", "Ward")))
    }

    @Test
    fun aSentenceStartingWithAKeywordIsNotAKeywordLine() {
        assertEquals(listOf(ability("Flying creatures you control get +N/+N.")),
            patterns("Windcaller's Hymn", "Flying creatures you control get +1/+1.", listOf("Flying")))
    }

    @Test
    fun manaRunsBecomeOneSymbolButTapStays() {
        assertEquals(listOf(ability("{M}, {T}: Add {M}.")), patterns("Mossgrown Idol", "{2}{G}, {T}: Add {G}{G}."))
    }

    @Test
    fun abilityWordIsDropped() {
        assertEquals(listOf(ability("Whenever a land you control enters, ~ deals N damage to each opponent.")),
            patterns("Cinderstep Pathfinder",
                "Landfall — Whenever a land you control enters, Cinderstep Pathfinder deals 1 damage to each opponent."))
    }

    @Test
    fun modesAndChaptersAreTheirOwnKinds() {
        assertEquals(
            listOf(ability("Choose N —"), Pattern(PatternKind.MODE, "Draw N cards."),
                Pattern(PatternKind.MODE, "~ deals N damage to target creature.")),
            patterns("Splitmind Charm", "Choose one —\n• Draw two cards.\n• Splitmind Charm deals 2 damage to target creature."))
        assertEquals(listOf(Pattern(PatternKind.CHAPTER, "Create a N/N [COLOR] Soldier creature token.")),
            patterns("Tale of the Watch", "I, II — Create a 1/1 white Soldier creature token."))
    }

    @Test
    fun coloursAndCreatureTypesIncludingPluralsBecomePlaceholders() {
        assertEquals(listOf(Pattern(PatternKind.KEYWORD, "Protection from [COLOR]")),
            patterns("Ashguard Knight", "Protection from red", listOf("Protection")))
        assertEquals(listOf(ability("Other [TYPE] you control get +N/+N."), ability("[TYPE] you control have haste.")),
            patterns("Warchief of the Thicket", "Other Elves you control get +1/+1.\nGoblins you control have haste.",
                types = setOf("Elf", "Goblin")))
    }
}
