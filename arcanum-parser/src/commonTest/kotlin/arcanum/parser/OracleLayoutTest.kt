package arcanum.parser

import arcanum.api.Layout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** OracleParser.layout: a card's layout from its card-data layout name and printed faces; invented cards. */
class OracleLayoutTest {
    private val creature = OracleCard("Tidewing Regent", "{3}{U}{U}", "Creature — Dragon", "Flying", power = "4", toughness = "4")
    private fun spell(type: String) = OracleCard("Swell and Surge", "{1}{U}", type, "Draw a card.")

    @Test fun oneFaceIsNormalWhateverItsLayoutName() {
        assertEquals(Layout.Normal, OracleParser.layout("saga", listOf(creature)))
        assertEquals(Layout.Normal, OracleParser.layout("prepare", listOf(creature)))
    }

    // CR 715, 720: card data names an Omen card's layout "adventure"; the inset spell's subtype tells them apart
    @Test fun anAdventureLayoutWithAnOmenSpellIsAnOmenCard() {
        assertEquals(Layout.Adventure, OracleParser.layout("adventure", listOf(creature, spell("Sorcery — Adventure"))))
        assertEquals(Layout.Omen, OracleParser.layout("adventure", listOf(creature, spell("Instant — Omen"))))
        assertEquals(Layout.Omen, OracleParser.layout("omen", listOf(creature, spell("Instant — Omen"))))
    }

    // CR 722
    @Test fun aPreparationCard() {
        assertEquals(Layout.Prepare, OracleParser.layout("prepare", listOf(creature, spell("Sorcery"))))
    }

    // CR 712.4, 701.42a: the partner from the reminder text, or from the meld ability's "a creature named"
    @Test fun aMeldCardNamesItsPartner() {
        val back = OracleCard("Thornwall Colossus", null, "Creature — Elemental", "Trample", power = "9", toughness = "9", colorIndicator = "G")
        val reminder = OracleCard("Bramble Twin, Elder Root", "{2}{G}", "Legendary Creature — Elf", "Reach\n(Melds with Hollow Twin, Last Seed.)")
        val ability = OracleCard("Hollow Twin, Last Seed", "{3}{G}", "Legendary Creature — Elf",
            "At the beginning of your first main phase, if you both own and control Hollow Twin and a creature named Bramble Twin, " +
                "Elder Root, you may pay {3}{G}{G}. If you do, exile them, then meld them into Thornwall Colossus.")
        assertEquals(Layout.Meld("Hollow Twin, Last Seed"), OracleParser.layout("meld", listOf(reminder, back)))
        assertEquals(Layout.Meld("Bramble Twin, Elder Root"), OracleParser.layout("meld", listOf(ability, back)))
        assertNull(OracleParser.layout("meld", listOf(creature, back)), "no partner named")
    }

    @Test fun aLayoutWithoutAParserLayoutIsNull() {
        assertNull(OracleParser.layout("reversible_card", listOf(creature, creature)))
    }
}
