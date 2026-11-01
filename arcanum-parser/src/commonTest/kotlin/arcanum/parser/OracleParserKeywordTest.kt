package arcanum.parser

import arcanum.api.Ability
import arcanum.api.Keyword
import arcanum.api.Cost
import arcanum.api.ManaCost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Keyword lines (CR 702). Test cards are invented: our own names and costs, real oracle wording. */
class OracleParserKeywordTest {
    private fun abilities(text: String, name: String = "Test Card"): List<Ability> = OracleParser.parse(name, text).abilities
    private fun mana(text: String) = Cost.mana(ManaCost.parse(text)!!)

    // CR 702.9 Flying, 702.7 First strike, 702.15 Lifelink
    @Test
    fun keywordLineGivesOneAbilityPerKeyword() {
        assertEquals(listOf(Keyword.Flying, Keyword.FirstStrike, Keyword.Lifelink),
            abilities("Flying, first strike, lifelink"))
    }

    // CR 702.19 Trample: reminder text is ignored
    @Test
    fun reminderTextIsIgnored() {
        assertEquals(listOf(Keyword.Trample),
            abilities("Trample (This creature can deal excess combat damage to the player or planeswalker it's attacking.)"))
    }

    // CR 702.122 Crew, 702.164 Toxic
    @Test
    fun keywordsWithANumber() {
        assertEquals(listOf(Keyword.Crew(3)), abilities("Crew 3"))
        assertEquals(listOf(Keyword.Toxic(2)), abilities("Toxic 2"))
    }

    // CR 702.6 Equip, 702.29 Cycling, 702.33 Kicker, 702.33c Multikicker
    @Test
    fun keywordsWithAManaCost() {
        assertEquals(listOf(Keyword.Equip(mana("{2}{W}"))), abilities("Equip {2}{W}"))
        assertEquals(listOf(Keyword.Cycling(mana("{U/P}"))), abilities("Cycling {U/P}"))
        assertEquals(listOf(Keyword.Kicker(listOf(mana("{1}{R}")))), abilities("Kicker {1}{R}"))
        assertEquals(listOf(Keyword.Multikicker(mana("{X}"))), abilities("Multikicker {X}"))
    }

    // CR 702.62 Suspend
    @Test
    fun keywordWithNumberAndManaCost() {
        assertEquals(listOf(Keyword.Suspend(4, mana("{1}{R}"))), abilities("Suspend 4—{1}{R}"))
    }

    // CR 702.21 Ward: only mana costs so far; other costs stay unparsed
    @Test
    fun wardWithManaCostParsesButOtherCostsStayUnparsed() {
        assertEquals(listOf(Keyword.Ward(mana("{2}"))), abilities("Ward {2}"))
        assertEquals(listOf(Ability.Unparsed("Ward—Pay 3 life.")), abilities("Ward—Pay 3 life."))
        assertEquals(listOf(Keyword.Flying, Ability.Unparsed("ward—Discard a card.")),
            abilities("Flying; ward—Discard a card."))
    }

    @Test
    fun keywordWrittenWithTheWrongValuesStaysUnparsed() {
        listOf("Flying 2", "Crew", "Crew X", "Equip", "Equip legendary creature {3}", "Kicker {1}{U} and/or {2}{B}",
            "Suspend 4", "Partner—Friends forever").forEach { text ->
            assertEquals(listOf(Ability.Unparsed(text)), abilities(text), text)
        }
    }

    @Test
    fun unknownLaterPartsStayUnparsedOneByOne() {
        assertEquals(listOf(Keyword.Flying, Ability.Unparsed("quickdraw"), Keyword.Haste),
            abilities("Flying, quickdraw, haste"))
    }

    @Test
    fun sentencesAreNotKeywordLines() {
        listOf("Flying creatures you control get +1/+1.", "When Test Card enters, draw a card.",
            "Choose one —", "Threshold — Test Card gets +2/+2 as long as seven or more cards are in your graveyard.")
            .forEach { text -> assertTrue(abilities(text).none { it is Keyword }, text) }
    }

    @Test
    fun eachLineIsReadOnItsOwnAndUnparsedLinesKeepTheirReminderText() {
        assertEquals(listOf(Keyword.Vigilance, Keyword.Equip(mana("{3}")),
            Ability.Unparsed("Equipped creature has shroud. (It can't be the target of spells or abilities.)")),
            abilities("Vigilance\nEquip {3}\nEquipped creature has shroud. (It can't be the target of spells or abilities.)"))
    }

    @Test
    fun longerKeywordNamesWin() {
        assertEquals(listOf(Keyword.DoubleStrike), abilities("Double strike"))
        assertEquals(listOf(Keyword.Megamorph(mana("{5}{G}"))), abilities("Megamorph {5}{G}"))
        assertEquals(listOf(Keyword.ForMirrodin), abilities("For Mirrodin!"))
    }

    @Test
    fun everyKnownKeywordParsesAndRendersBackUnchanged() {
        KeywordSyntax.all.forEach { syntax ->
            val text = syntax.text + when (syntax) {
                is KeywordSyntax.None -> ""
                is KeywordSyntax.Amount -> " 3"
                is KeywordSyntax.Cost -> " {1}{W}"
                is KeywordSyntax.AmountAndCost -> " 2—{G}"
            }
            val parsed = abilities(text).single()
            assertEquals(syntax.text, (parsed as Keyword).text, text)
            assertEquals(text, OracleRenderer.render(parsed), text)
        }
    }

    // Every keyword cites a rule of CR 702 in the pinned version.
    @Test
    fun everyKeywordCitesARuleOfChapter702() {
        KeywordSyntax.all.forEach { syntax ->
            val keyword = OracleParser.keyword(syntax.text + when (syntax) {
                is KeywordSyntax.None -> ""; is KeywordSyntax.Amount -> " 1"
                is KeywordSyntax.Cost -> " {1}"; is KeywordSyntax.AmountAndCost -> " 1—{1}"
            })!!
            assertTrue(Regex("""702\.\d+[a-z]?""").matches(keyword.rule), "${keyword.text}: ${keyword.rule}")
        }
    }
}
