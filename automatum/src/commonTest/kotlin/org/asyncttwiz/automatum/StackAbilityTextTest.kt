package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.engine.cards.Cards
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import org.asyncttwiz.protocol.MtgRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StackAbilityTextTest {
    private val heron = Cards.invent("Tidewatch Heron", "{2}{U}", "Creature — Bird", """
        Flying
        When this creature enters, choose one —
        • Tap target nonland permanent.
        • Untap target nonland permanent.
    """, "2/2")

    private fun entry(modes: List<Int>) = Cards.definition(heron).faces.single().abilities.filterIsInstance<Ability.Triggered>().single()
        .let { ability ->
            EngineCodec.stackItem(StackItem(ObjectId.StackItem("S1"), StackItemKind.Triggered, PlayerId("alice"), null, ability,
                modes = modes, timestamp = 1)) { MtgRef.StackItem("S1") }
        }

    // STK-050: holding an ability on the Stack names its chosen modes on the ability's tile (Inspect shows every face)
    @Test fun aTriggeredAbilityNamesItsChosenModes() {
        assertEquals(listOf("Untap target nonland permanent."), StackAbilityText.modes(entry(listOf(1))))
        assertTrue(StackAbilityText.modes(entry(emptyList())).isEmpty())
    }

    // STK-046, STK-050 (owner playtest 2026-11-07): a mode chosen for a trigger is what its line says it does
    @Test fun aChosenModeIsTheShortEffect() {
        assertEquals("untap target nonland permanent", StackAbilityText.effect(EngineCodec.stackItem(entry(listOf(1)))))
        assertEquals("Triggered ability", StackAbilityText.kind(entry(listOf(1))))
    }

    // Owner playtest 2026-11-07 (Fractured Sanity): the short form of an effect for the Stack line
    @Test fun shortEffects() {
        assertEquals("each opponent mills 14 cards", StackAbilityText.short("Each opponent mills fourteen cards."))
        assertEquals("deals 3 damage to any target", StackAbilityText.short("Storm Spark deals 3 damage to any target.", "Storm Spark"))
        assertEquals("deals 4 damage to any target", StackAbilityText.short("Ember Burst deals X damage to any target.", "Ember Burst", x = 4))
        assertEquals("each opponent mills 4 cards",
            StackAbilityText.short("When you cycle this card, each opponent mills four cards.", triggered = true))
        assertEquals("draw a card", StackAbilityText.short("{2}{G}, {T}: Draw a card.", activated = true))
        // A bare keyword line reads as its reminder text, without the cost
        assertEquals("draw a card", StackAbilityText.short("Cycling {R} ({R}, Discard this card: Draw a card.)", activated = true))
        // A name in front stays capitalised
        assertEquals("Ember Herald gets +1/+0 until end of turn", StackAbilityText.short("Ember Herald gets +1/+0 until end of turn."))
        assertEquals("you gain 2 life", StackAbilityText.short("At the beginning of your upkeep, you gain two life.", triggered = true))
    }

    // STK-046, STK-050, HIS-091 (owner playtest 2026-11-07, CR 702.29a, CR 702.29c): cycling a card with a "when you cycle"
    // trigger puts the cycling ability and on it the cycling trigger on the Stack, each named by its kind; the spell's own
    // effect reads short
    @Test fun aCyclingTriggerAndItsCyclingAreNamedByKind() {
        val table = EngineTable()
        val reason = Cards.invent("Shattered Reason", "{2}{U}", "Instant",
            "Each opponent mills fourteen cards.\nCycling {R}\nWhen you cycle this card, each opponent mills four cards.")
        table.put(table.alice, arcanum.api.Zone.Battlefield, "Mountain")
        val (card) = table.put(table.alice, arcanum.api.Zone.Hand, reason)
        val state = table.state()
        val options = requireNotNull(HandCasts.options(table.source(state), table.definitions, table.alice, card,
            HandPlays.of(state, table.alice, null).of(card), 0, emptyList(), CastFlow.Seeds(table::seed)))
        val cycle = requireNotNull(options.ways.single { it.name == "Cycling" }.input)
        var flow = CastFlow.start(table.source(state), table.alice, cycle, CastFlow.Seeds(table::seed))
        (flow.pending as? arcanum.engine.decision.Decision.ManaPayment)?.let {
            flow = flow.answer(arcanum.engine.decision.Answer.ManaPayment(it.suggestion)) }
        val items = flow.state.stack.map { flow.state.stackItems.getValue(it) }
        assertEquals(listOf("Cycling ability", "Cycling trigger"), items.map { StackAbilityText.kind(it) })
        assertTrue(StackAbilityText.cycling(items[0]))
        assertEquals("each opponent mills 4 cards", StackAbilityText.effect(items[1], source = "Shattered Reason"))
        // The structured items the table gets say the same
        val board = table.board(flow.state)
        assertEquals(listOf("Cycling ability", "Cycling trigger"), board.stackEffects.map { StackAbilityText.kind(requireNotNull(it.item)) }.sortedBy { it })
        val spell = Cards.definition(reason).faces.single().abilities.filterIsInstance<Ability.Spell>().single()
        assertEquals("each opponent mills 14 cards", StackAbilityText.effect(StackItem(ObjectId.StackItem("S2"),
            StackItemKind.Spell, PlayerId("alice"), null, spell, timestamp = 2), source = "Shattered Reason"))
    }
}
