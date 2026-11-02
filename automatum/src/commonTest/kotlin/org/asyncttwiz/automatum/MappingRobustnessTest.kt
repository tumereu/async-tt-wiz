package org.asyncttwiz.automatum

import arcanum.api.Color
import arcanum.api.FaceDefinition
import arcanum.api.Keyword
import arcanum.api.PredefinedTokens
import arcanum.api.PrintedNumber
import arcanum.api.Subtype
import arcanum.parser.OracleParser
import org.asyncttwiz.protocol.MtgTag
import org.asyncttwiz.protocol.PlayZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The mapping's own guards (I0 review #2, #6, #9, #10): tag size and contents, unreadable engine data, token card data. */
class MappingRobustnessTest {
    private val table = AtwTable("robust")
    private val alice get() = table.alice
    private val bob get() = table.bob

    // D33, D60: a tag names a card's definition by reference; the definition comes back from the board's card data
    @Test fun tagsNameCardDefinitionsByReference() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(alice, PlayZone.HAND, "Storm Spark")
        table.put(alice, PlayZone.LIBRARY, "Mountain")
        table.put(bob, PlayZone.LIBRARY, "Island")
        val rt = RoundTrip(table.state(), table.definitions)
        rt.cast(alice, "Storm Spark")
        rt.target(arcanum.engine.state.TargetRef.Player(arcanum.engine.state.PlayerId(bob)))
        rt.settle()
        rt.resolveAll()
        val data = rt.tags.filterNotNull().map { it.data }.filter { it.isNotEmpty() }
        assertTrue(data.isNotEmpty())
        // What everyone saw (the spell's name on the Stack) stays; its definition and printed ability are references
        assertTrue(data.none { "\"faces\"" in it || "deals 3 damage" in it }, "a tag carries a card's definition: $data")
        assertTrue(data.any { TagCodec.CARD in it } && data.any { TagCodec.PRINTED in it })
        // Every round trip compared the log rebuilt from these tags with the engine's own (RoundTrip): it reads back exactly
    }

    // P8: tag data the mapping can't read is reported, not silently skipped (the engine would miss this turn's events)
    @Test fun unreadableTagIsReported() {
        table.put(alice, PlayZone.LIBRARY, "Mountain")
        table.put(bob, PlayZone.LIBRARY, "Island")
        val state = table.state()
        val tags = listOf(MtgTag(listOf("died"), data = """{"log":[{"type":"add","entry":{"turn":1}}]}"""))
        val problem = assertFailsWith<Unsupported> { EngineLog.from(tags, state, table.definitions) }
        assertEquals(1, problem.reasons.size)
        assertEquals(emptyList(), EngineLog.from(listOf(null, MtgTag(listOf("died"))), state, table.definitions))
    }

    // P8, D62: a ledger or Stack item the engine can't read is listed by unsupported() and refused by from()
    @Test fun unreadableLedgerIsUnsupported() {
        val mountain = table.put(alice, PlayZone.BATTLEFIELD, "Mountain").single()
        table.put(alice, PlayZone.LIBRARY, "Mountain")
        table.put(bob, PlayZone.LIBRARY, "Island")
        val state = table.state()
        val broken = state.copy(board = state.board.copy(cards = state.board.cards.map { if (it.id == mountain) it.copy(ledger = """{"phasedOut":"x"}""") else it }))
        assertTrue(EngineState.unsupported(broken, table.definitions).any { "ledger" in it })
        assertFailsWith<Unsupported> { EngineState.from(broken, table.definitions) }
    }

    // I-D2: a token's ATW card data carries its rules text, which reads back as its abilities
    @Test fun tokenRulesTextReadsBackAsItsAbilities() {
        val spirit = FaceDefinition("Spirit Token", colorIndicator = listOf(Color.White), types = listOf(arcanum.api.CardType.Creature),
            subtypes = listOf(Subtype("Spirit")), power = PrintedNumber.Fixed(1), toughness = PrintedNumber.Fixed(1),
            abilities = listOf(Keyword.Flying, Keyword.Haste))
        assertEquals("Flying, haste", AtwTokens.rulesText(spirit))
        assertEquals(spirit.abilities, OracleParser.parse(spirit.name, AtwTokens.rulesText(spirit)).abilities)
        val treasure = PredefinedTokens.face("Treasure")!!
        assertEquals("{T}, Sacrifice this token: Add one mana of any color.", AtwTokens.rulesText(treasure))
        val data = AtwTokens.submitted(arcanum.api.CardDefinition(arcanum.api.Layout.Normal, listOf(spirit)))
        assertEquals("Flying, haste", data.definition.faces.single().rulesText)
    }
}
