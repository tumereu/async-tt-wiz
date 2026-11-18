package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.engine.cards.Cards
import arcanum.engine.state.CardId
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TargetRef
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

    // STK-050, STK-124: holding an ability on the Stack names its chosen modes on the ability's tile (Inspect shows every face)
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

    // STK-046, STK-050, HIS-091, HIS-121 (owner playtest 2026-11-07, CR 702.29a, CR 702.29c): cycling a card with a "when you cycle"
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

    // ---- Targets named in the text (owner 2026-11-07: "Lightning Bolt deals 3 damage to Llanowar Elves")

    private val cub = TargetRef.Object(ObjectId.Card(CardId("cub"), 0))
    private val pup = TargetRef.Object(ObjectId.Card(CardId("pup"), 0))
    private val bob = TargetRef.Player(PlayerId("bob"))
    private val shown = mapOf(cub to "Ember Cub", pup to "Ash Pup", bob to "Bob")

    /** A Stack item of [name]'s spell (its first spell ability) with its slots' targets in order ([targets]: per slot). */
    private fun spell(name: String, text: String, vararg targets: List<TargetRef>, modes: List<Int> = emptyList(), x: Int? = null): StackItem {
        val ability = Cards.definition(Cards.invent(name, "{1}{R}", "Instant", text)).faces.single().abilities.filterIsInstance<Ability.Spell>().single()
        val instructions = ability.instructions
        val slots = instructions.targets.map { it.name } + modes.withIndex().flatMap { (position, index) ->
            instructions.modes!!.options[index].instructions.targets.map { slot ->
                if (instructions.targets.any { it.name == slot.name } ||
                    modes.take(position).any { i -> instructions.modes!!.options[i].instructions.targets.any { it.name == slot.name } })
                    "${slot.name}@$position" else slot.name
            }
        }
        return StackItem(ObjectId.Card(CardId(name), 0), StackItemKind.Spell, PlayerId("alice"), null, ability,
            targets = slots.zip(targets).toMap(), modes = modes, announced = listOfNotNull(x?.let { "X" to it }).toMap(), timestamp = 1)
    }

    /** [text] with each target part as "[names]". */
    private fun named(item: StackItem, text: TargetedText?): String = requireNotNull(text).text { keys ->
        "[" + keys.flatMap { item.targets[it].orEmpty() }.joinToString(" and ") { shown.getValue(it) } + "]"
    }

    @Test fun aSingleTargetIsNamedInPlace() {
        val bolt = spell("Spanned Spark", "Spanned Spark deals 3 damage to any target.", listOf(cub))
        assertEquals("deals 3 damage to [Ember Cub]", named(bolt, StackAbilityText.targetedEffect(bolt, source = "Spanned Spark")))
        assertEquals("Spanned Spark deals 3 damage to [Ember Cub].", named(bolt, StackAbilityText.targetedText(bolt)))
        assertEquals(emptyList(), StackAbilityText.targetedEffect(bolt)!!.unplaced)
    }

    @Test fun twoTargetsAreEachNamedWhereTheTextNamesThem() {
        val brawl = spell("Spanned Brawl", "Target creature you control fights target creature you don't control.", listOf(cub), listOf(pup))
        assertEquals("[Ember Cub] fights [Ash Pup]", named(brawl, StackAbilityText.targetedEffect(brawl)))
    }

    // Two targets for one phrase are named together there
    @Test fun severalTargetsOfOnePhrase() {
        val snare = spell("Spanned Snare", "Tap up to two target creatures.", listOf(cub, pup))
        assertEquals("tap [Ember Cub and Ash Pup]", named(snare, StackAbilityText.targetedEffect(snare)))
    }

    @Test fun aPlayerTarget() {
        val insight = spell("Spanned Insight", "Target player draws two cards.", listOf(bob))
        assertEquals("[Bob] draws 2 cards", named(insight, StackAbilityText.targetedEffect(insight)))
    }

    // CR 700.2: a chosen mode's targets are named in its line, in the short effect and in the whole text
    @Test fun aModesTargetsAreNamedInItsLine() {
        val rasp = spell("Spanned Rasp", "Choose one —\n• Spanned Rasp deals 3 damage to target creature.\n• Destroy target artifact.",
            listOf(pup), modes = listOf(0))
        assertEquals("deals 3 damage to [Ash Pup]", named(rasp, StackAbilityText.targetedEffect(rasp, source = "Spanned Rasp")))
        assertEquals("Choose one —\n• Spanned Rasp deals 3 damage to [Ash Pup].\n• Destroy target artifact.",
            named(rasp, StackAbilityText.targetedText(rasp)))
    }

    @Test fun xIsFilledIn() {
        val burst = spell("Spanned Burst", "Spanned Burst deals X damage to any target.", listOf(bob), x = 4)
        assertEquals("deals 4 damage to [Bob]", named(burst, StackAbilityText.targetedEffect(burst, source = "Spanned Burst")))
        assertEquals("Spanned Burst deals 4 damage to [Bob].", named(burst, StackAbilityText.targetedText(burst)))
    }

    // A slot whose phrase the parser couldn't place (a keyword action that targets) stays for the host to name after it
    @Test fun aTargetWithoutAPhraseIsUnplaced() {
        val heave = spell("Spanned Heave", "Earthbend 2.", listOf(cub))
        val text = requireNotNull(StackAbilityText.targetedEffect(heave))
        assertEquals("earthbend 2", named(heave, text))
        assertEquals(heave.targets.keys.toList(), text.unplaced)
    }

    // A phrase nothing was chosen for stays as printed
    @Test fun aPhraseWithoutTargetsStaysPrinted() {
        val snare = spell("Spanned Snare", "Tap up to two target creatures.", emptyList())
        assertEquals("tap up to 2 target creatures", named(snare, StackAbilityText.targetedEffect(snare)))
    }
}

/** A gift spell cast through the engine (CastFlow), its Stack item's texts. */
class GiftStackTextTest {
    // STK-046, STK-050, CR 702.174a, 702.174j (2026-11-16 candidate, missing text): gift adds a wordless spell ability of its own
    // before the spell's printed one; the spell's Stack item reads as the printed one, its targets named in place, promised or
    // not (before, its line said nothing after "cast" and Inspect had no text)
    @Test fun aGiftSpellsStackItemReadsAsItsPrintedText() {
        for (promise in listOf(true, false)) {
            val t = EngineTable()
            val card = Cards.invent("Stack Quarrel", "{0}", "Sorcery", "Gift a tapped Fish (You may promise an opponent a gift as you " +
                "cast this spell. If you do, they create a tapped 1/1 blue Fish creature token before its other effects.)\nChoose " +
                "target creature you control and target creature you don't control. Put a +1/+1 counter on the creature you control " +
                "if the gift was promised. Then those creatures fight each other.")
            val (seer) = t.put(t.alice, arcanum.api.Zone.Battlefield, "Elvish Seer")
            val (elves) = t.put(t.bob, arcanum.api.Zone.Battlefield, "Thicketwood Elves")
            val (c) = t.put(t.alice, arcanum.api.Zone.Hand, card)
            val state = t.state()
            val names = mapOf<TargetRef, String>(TargetRef.Object(t.obj(seer)) to "Elvish Seer", TargetRef.Object(t.obj(elves)) to "Thicketwood Elves")
            var flow = CastFlow.start(t.source(state), t.alice, requireNotNull(t.plays(state).of(c).first().input), CastFlow.Seeds(t::seed))
            while (true) flow = when (val pending = flow.pending) {
                is arcanum.engine.decision.Decision.May -> flow.answer(arcanum.engine.decision.Answer.YesNo(promise))
                is arcanum.engine.decision.Decision.Targets -> flow.answer(arcanum.engine.decision.Answer.Targets(listOf(pending.options.first())))
                else -> break
            }
            val item = flow.state.stack.map { flow.state.stackItems.getValue(it) }.single { it.kind == StackItemKind.Spell }
            fun named(text: TargetedText?) = requireNotNull(text) { "a text for the gift spell (promised: $promise)" }.text { keys ->
                "[" + keys.flatMap { item.targets[it].orEmpty() }.joinToString(" and ") { names.getValue(it) } + "]"
            }
            assertEquals("choose [Elvish Seer] and [Thicketwood Elves]. Put a +1/+1 counter on the creature you control if the gift was " +
                "promised. Then those creatures fight each other", named(StackAbilityText.targetedEffect(item, source = "Stack Quarrel")))
            assertEquals("Choose [Elvish Seer] and [Thicketwood Elves]. Put a +1/+1 counter on the creature you control if the gift was " +
                "promised. Then those creatures fight each other.", named(StackAbilityText.targetedText(item)))
        }
    }
}
