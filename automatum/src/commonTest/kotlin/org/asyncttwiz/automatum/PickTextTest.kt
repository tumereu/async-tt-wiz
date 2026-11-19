package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** Which pick is which (owner, 2026-11-11): the text of what is cast or activated with the phrase chosen now, and each one
 * chosen as what was chosen ([PickText]). */
class PickTextTest {
    private val table = EngineTable()
    private val alice get() = table.alice
    private fun start(state: arcanum.engine.state.GameState, input: Input) =
        CastFlow.start(table.source(state), alice, input, CastFlow.Seeds(table::seed))

    private fun CastFlow.picked(costs: List<Pair<arcanum.engine.decision.CostPartAt, List<arcanum.engine.state.ObjectId>>> = emptyList(),
        rulesText: String? = null): PickText =
        assertNotNull(PickText.of(state, assertNotNull(PickText.announcing(call.pending?.continuation)), pending, costs, rulesText = rulesText))

    /** The parts as text: the phrase chosen now in [brackets], a chosen phrase as <names>, bracketed names as (names). */
    private fun PickText.shown(name: (TargetRef) -> String): String = parts.joinToString("") { part ->
        when (part) {
            is PickText.Part.Plain -> part.text
            is PickText.Part.Now -> "[${part.text}]"
            is PickText.Part.Chosen -> "<" + (part.chosen.joinToString(" and ") { name(it) }.ifEmpty { part.none }) + ">"
            is PickText.Part.After -> " (" + part.chosen.joinToString(" and ") { name(it) } + ")"
        }
    }

    // STK-047, STK-142, STK-143, CR 601.2c: a spell's targets in the order its text names them: the first while it is chosen, then in its place
    // what was chosen while the second is chosen
    @Test fun targetsChosenNowAndBefore() {
        val brawl = Cards.invent("Pick Brawl", "{0}", "Sorcery", "Target creature you control fights target creature you don't control.")
        val (seer) = table.put(alice, Zone.Battlefield, "Elvish Seer")
        val (elves) = table.put(table.bob, Zone.Battlefield, "Thicketwood Elves")
        val (card) = table.put(alice, Zone.Hand, brawl)
        val state = table.state()
        var flow = start(state, assertNotNull(table.plays(state).of(card).first().input))
        val names = mapOf<TargetRef, String>(TargetRef.Object(table.obj(seer)) to "Elvish Seer", TargetRef.Object(table.obj(elves)) to "Thicketwood Elves")
        assertEquals("[Target creature you control] fights target creature you don't control.", flow.picked().shown { names.getValue(it) })
        flow = flow.answer(Answer.Targets(listOf(TargetRef.Object(table.obj(seer)))))
        val second = flow.picked()
        assertEquals("<Elvish Seer> fights [target creature you don't control].", second.shown { names.getValue(it) })
        assertEquals(listOf<Pair<TargetRef, String>>(TargetRef.Object(table.obj(seer)) to "Target"), second.roles)
    }

    // STK-047, STK-143, CR 702.6a, 601.2c, 601.2h: Equip—Sacrifice a creature: the equip target has no phrase of its own, so the keyword's
    // name is highlighted while it is chosen and the name follows it in brackets; then the sacrifice's phrase is chosen
    @Test fun anEquipTargetThenItsSacrifice() {
        val hauberkText = "Equipped creature gets +4/+2.\nEquip—Sacrifice a creature."
        val hauberk = Cards.invent("Pick Hauberk", "{0}", "Artifact — Equipment", hauberkText)
        val (equipment) = table.put(alice, Zone.Battlefield, hauberk)
        val (seer, elves) = table.put(alice, Zone.Battlefield, "Elvish Seer", "Thicketwood Elves")
        val state = table.state()
        val equip = table.plays(state).of(equipment).first { it.text.startsWith("Equip") }
        var flow = start(state, assertNotNull(equip.input))
        val names = mapOf<TargetRef, String>(TargetRef.Object(table.obj(seer)) to "Elvish Seer", TargetRef.Object(table.obj(elves)) to "Thicketwood Elves")
        assertIs<Decision.Targets>(flow.pending)
        assertEquals("[Equip]—Sacrifice a creature.", flow.picked(rulesText = hauberkText).shown { names.getValue(it) })
        flow = flow.answer(Answer.Targets(listOf(TargetRef.Object(table.obj(seer)))))
        val sacrifice = assertIs<Decision.ChooseObjects>(flow.pending)
        assertEquals(arcanum.engine.decision.CostPartAt(null, 0), sacrifice.cost)
        val text = flow.picked(rulesText = hauberkText)
        assertEquals("Equip (Elvish Seer)—Sacrifice [a creature].", text.shown { names.getValue(it) })
        assertEquals(listOf<Pair<TargetRef, String>>(TargetRef.Object(table.obj(seer)) to "Equip"), text.roles)
        val withPicked = PickText.of(flow.state, assertNotNull(PickText.announcing(flow.call.pending?.continuation)), sacrifice, emptyList(),
            picked = listOf(TargetRef.Object(table.obj(elves))), rulesText = hauberkText)
        assertEquals("Sacrificed", withPicked?.roles?.toMap()?.get(TargetRef.Object(table.obj(elves))))
    }

    // STK-047, STK-143, CR 603.3d (found 2026-11-12, C3a): a trigger with two target slots, its targets chosen as it is put on the
    // Stack: while the second is chosen, the first phrase reads as what was chosen for it
    @Test fun aTriggersSecondTargetShowsTheFirstAsChosen() {
        val instigator = Cards.invent("Pick Instigator", "{0}", "Enchantment",
            "Whenever you cast a spell, target creature you control fights target creature you don't control.")
        val spark = Cards.invent("Pick Quiet Spark", "{0}", "Sorcery", "You gain 1 life.")
        table.put(alice, Zone.Battlefield, instigator)
        // Two candidates for each slot, so each is asked
        val (seer) = table.put(alice, Zone.Battlefield, "Elvish Seer", "Elvish Seer")
        val (elves) = table.put(table.bob, Zone.Battlefield, "Thicketwood Elves", "Thicketwood Elves")
        val (card) = table.put(alice, Zone.Hand, spark)
        val state = table.state()
        fun perform(input: Input): Decision {
            val out = arcanum.engine.Engine.perform(state, input, org.asyncttwiz.protocol.game.EnginePreferences.of(state))
            out.changes.forEach(state::apply)
            return assertNotNull(state.pending?.decision, "a decision open")
        }
        val names = mapOf<TargetRef, String>(TargetRef.Object(table.obj(seer)) to "Elvish Seer", TargetRef.Object(table.obj(elves)) to "Thicketwood Elves")
        fun text(decision: Decision) = assertNotNull(PickText.of(state,
            assertNotNull(PickText.announcing(state.pending?.continuation, decision)), decision, emptyList())).shown { names.getValue(it) }
        val first = assertIs<Decision.Targets>(perform(Input.Cast(PlayerId(alice), table.obj(card))))
        assertIs<arcanum.engine.decision.Continuation.PlacingTriggers>(state.pending?.continuation)
        assertEquals("Whenever you cast a spell, [target creature you control] fights target creature you don't control.", text(first))
        val second = assertIs<Decision.Targets>(perform(Input.Answer(first.id, Answer.Targets(listOf(TargetRef.Object(table.obj(seer)))))))
        assertEquals("Whenever you cast a spell, <Elvish Seer> fights [target creature you don't control].", text(second))
    }

    /** The text shown while each target of a sorcery [name] with [text] is picked, in order (the first candidate each time), a gift's
     * promise answered with [promise]: the phrase picked now in [brackets], a chosen one as <names>. */
    private fun pickedInTurn(name: String, text: String, promise: Boolean = false): List<String> {
        val t = EngineTable()
        val card = Cards.invent(name, "{0}", "Sorcery", text)
        val (seer) = t.put(t.alice, Zone.Battlefield, "Elvish Seer")
        val (elves) = t.put(t.bob, Zone.Battlefield, "Thicketwood Elves")
        val (c) = t.put(t.alice, Zone.Hand, card)
        val state = t.state()
        val names = mapOf<TargetRef, String>(TargetRef.Object(t.obj(seer)) to "Elvish Seer", TargetRef.Object(t.obj(elves)) to "Thicketwood Elves")
        var flow = CastFlow.start(t.source(state), t.alice, assertNotNull(t.plays(state).of(c).first().input), CastFlow.Seeds(t::seed))
        val shown = mutableListOf<String>()
        while (true) flow = when (val pending = flow.pending) {
            is Decision.May -> flow.answer(Answer.YesNo(promise))
            is Decision.Targets -> {
                shown += assertNotNull(PickText.of(flow.state, assertNotNull(PickText.announcing(flow.call.pending?.continuation)), pending,
                    emptyList()), "a text for ${pending.slot} of \"$text\"").shown { names[it] ?: "a player" }
                flow.answer(Answer.Targets(listOf(pending.options.first())))
            }
            else -> break
        }
        return shown
    }

    // STK-047, STK-142, CR 702.174a, 702.174j, 601.2c (owner, prod 0.2.0: a gift spell's two targets were picked with nothing highlighted):
    // gift adds a spell ability of its own, without words, before the spell's; the spell's printed one is the text, promised
    // or not, and both targets of its one sentence read as which pick is which
    @Test fun aGiftSpellsTwoTargetsInOneSentence() {
        val text = "Gift a tapped Fish (You may promise an opponent a gift as you cast this spell. If you do, they create a tapped 1/1 " +
            "blue Fish creature token before its other effects.)\nChoose target creature you control and target creature you don't " +
            "control. Put a +1/+1 counter on the creature you control if the gift was promised. Then those creatures fight each other."
        val expected = listOf(
            "Choose [target creature you control] and target creature you don't control. Put a +1/+1 counter on the creature you " +
                "control if the gift was promised. Then those creatures fight each other.",
            "Choose <Elvish Seer> and [target creature you don't control]. Put a +1/+1 counter on the creature you control if the " +
                "gift was promised. Then those creatures fight each other.")
        assertEquals(expected, pickedInTurn("Pick Quarrel", text, promise = true))
        assertEquals(expected, pickedInTurn("Pick Quarrel", text, promise = false))
    }

    // STK-047, STK-142, CR 601.2c: target wordings of other templates: the phrase picked now is the whole target phrase, after the
    // card's name and in a sentence's subject alike
    @Test fun targetWordingsOfSeveralTemplates() {
        assertEquals(listOf("Pick Wording deals 3 damage to [target creature or planeswalker]. If you control a Raccoon, you may " +
            "discard a card. If you do, draw a card."),
            pickedInTurn("Pick Wording", "Pick Wording deals 3 damage to target creature or planeswalker. If you control a Raccoon, you may discard a " +
                "card. If you do, draw a card."))
        assertEquals(listOf("[Target creature you control] fights target creature you don't control.",
            "<Elvish Seer> fights [target creature you don't control]."),
            pickedInTurn("Pick Scuffle", "Target creature you control fights target creature you don't control."))
        assertEquals(listOf("Choose [target creature you control] and target creature you don't control. Then those creatures fight each other.",
            "Choose <Elvish Seer> and [target creature you don't control]. Then those creatures fight each other."),
            pickedInTurn("Pick Duel", "Choose target creature you control and target creature you don't control. Then those creatures fight each other."))
        assertEquals(listOf("[Target player] draws two cards."), pickedInTurn("Pick Lesson", "Target player draws two cards."))
    }

    // A phrase nothing was chosen for reads as none of it
    @Test fun noneChosen() {
        assertEquals("no creature", PickText.none("up to one target creature"))
        assertEquals("no creatures", PickText.none("any number of target creatures"))
        assertEquals("no creature you control", PickText.none("up to one other target creature you control"))
        assertEquals(PlayerId(alice).value, alice)
    }

    // D5 (problem report MDLPM7), STK-047, CR 608.2g, 601.2c: a spell cast while something resolves ("you may cast that card") has its
    // targets picked with its own text, the phrase picked now shining, though its announcement is inside the resolution
    @Test fun aSpellCastWhileSomethingResolvesShowsItsText() {
        val forage = Cards.invent("Pick Forage", "{0}", "Sorcery",
            "Exile cards from the top of your library until you exile a nonland card. You may cast that card without paying its mana cost.")
        val split = Cards.invent("Pick Split", "{1}{G}", "Sorcery", "Target creature you control fights target creature you don't control.")
        table.put(alice, Zone.Library, split)
        table.put(alice, Zone.Battlefield, "Elvish Seer", "Elvish Seer")
        val (card) = table.put(alice, Zone.Hand, forage)
        table.put(table.bob, Zone.Battlefield, "Elvish Seer", "Thicketwood Elves")
        val state = table.state()
        fun perform(input: Input) = arcanum.engine.Engine.perform(state, input, org.asyncttwiz.protocol.game.EnginePreferences.of(state)).changes.forEach(state::apply)
        perform(Input.Cast(PlayerId(alice), table.obj(card)))
        repeat(4) { state.pending?.decision?.let { d -> if (d is Decision.ManaPayment) perform(Input.Answer(d.id, Answer.ManaPayment(d.suggestion))) }
            if (state.pending == null) state.priority.holder?.let { perform(Input.PassPriority(it)) } }
        val may = assertIs<Decision.May>(state.pending?.decision, "the cast is offered")
        perform(Input.Answer(may.id, Answer.YesNo(true)))
        val first = assertIs<Decision.Targets>(state.pending?.decision)
        assertIs<arcanum.engine.decision.Continuation.Resolving>(state.pending?.continuation)
        val text = assertNotNull(PickText.of(state, assertNotNull(PickText.announcing(state.pending?.continuation, first, state),
            "the cast by the effect is the announcement: source ${first.source} in ${first.source?.let { state.obj(it)?.zone }}"), first, emptyList()))
        assertEquals("[Target creature you control] fights target creature you don't control.", text.shown { "?" })
    }
}
