package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Layout
import arcanum.api.PrintedNumber
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.DefinitionFace
import org.asyncttwiz.protocol.PlayZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** SYS-064 (I-D21): a card the device couldn't read plays with its printed values and manual abilities instead of blocking the game. */
class PrintedCardsTest {
    private val table = AtwTable("printed")
    private val alice get() = table.alice
    private val bob get() = table.bob

    // A split card made before card data kept its halves (old deck data): one face with the halves' combined text
    @Test fun anOldSplitCardIsItsFirstTypeLineWithItsWholeTextManual() {
        val old = CardDefinition("Fire // Ice", listOf(DefinitionFace("f", "Fire // Ice", typeLine = "Instant",
            rulesText = "Fire\n{1}{R}\nInstant\nFire deals 2 damage divided as you choose among one or two targets.\n\n────────\n\nIce\n{1}{U}\nInstant\nTap target permanent.\nDraw a card.")),
            provider = "mtgjson", layout = "split")
        val printed = PrintedCards.definition(old)
        assertEquals(Layout.Normal, printed.layout)
        val face = printed.faces.single()
        assertEquals("Fire // Ice", face.name)
        assertEquals(listOf<CardType>(CardType.Instant), face.types)
        assertEquals(old.faces.single().rulesText, (face.abilities.single() as Ability.Unparsed).text)
    }

    @Test fun printedValuesAreReadLeniently() {
        val card = CardDefinition("Grizzled Odd", listOf(DefinitionFace("g", "Grizzled Odd", typeLine = "Legendary Snow Creature — Bear Druid",
            manaCost = "{1}{G}", rulesText = "Some wording the parser doesn't know.", power = "2", toughness = "*",
            sourceData = """{"colors":["G"],"manaCost":"{1}{G}"}""")))
        val face = PrintedCards.definition(card).faces.single()
        assertEquals("{1}{G}", face.manaCost.toString())
        assertEquals(listOf<Supertype>(Supertype.Legendary, Supertype.Snow), face.supertypes)
        assertEquals(listOf<CardType>(CardType.Creature), face.types)
        assertEquals(listOf(Subtype("Bear"), Subtype("Druid")), face.subtypes)
        assertEquals(PrintedNumber.Fixed(2), face.power)
        assertEquals(PrintedNumber.Star(), face.toughness)
        assertEquals(null, face.colorIndicator, "its colour comes from its mana cost")
        val back = CardDefinition("Back", listOf(DefinitionFace("b", "Back", typeLine = "Creature — Wolf",
            sourceData = """{"colors":["G","R"]}""")))
        assertEquals(listOf<Color>(Color.Red, Color.Green).toSet(), PrintedCards.definition(back).faces.single().colorIndicator!!.toSet())
    }

    // The game goes on with an unreadable card on the battlefield: it is a creature with its printed P/T
    @Test fun anUnreadableCardDoesntBlockTheGame() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(alice, PlayZone.HAND, "Storm Spark")
        table.put(alice, PlayZone.LIBRARY, "Mountain", "Mountain")
        val cub = table.put(bob, PlayZone.BATTLEFIELD, "Excavation Tinker").single()
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val state = table.state()
        val unreadable = state.board.cards.single { it.id == cub }.definition
        val definitions = table.definitions - unreadable
        assertTrue(EngineState.unsupported(state, definitions).isEmpty())
        val rt = RoundTrip(state, definitions)
        val obj = rt.engine().objects.values.single { it.card.value == cub }
        assertTrue(CardType.Creature in obj.definition!!.faces.single().types)
        rt.cast(alice, "Storm Spark")
        rt.target(TargetRef.Player(PlayerId(bob)))
        rt.settle()
        rt.resolveAll()
        assertEquals(17, rt.engine().player(PlayerId(bob)).life)
    }
}
