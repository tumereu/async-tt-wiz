@file:OptIn(arcanum.engine.state.ArcanumTestSetup::class)

package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.ManaSymbol
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Timing
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.state.Bindings
import arcanum.engine.state.EffectId
import arcanum.engine.state.EffectObject
import arcanum.engine.state.NamedValue
import arcanum.engine.state.PlayerId
import arcanum.engine.state.setup
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TBL-104, TBL-183 (owner, 2026-11-12: Scheming Fence): Inspect lists every effect on a card from the engine's view of its effects:
 * what it copies, the abilities it has from another permanent, an ability it gained by its words; TBL-136: exactly those
 * cards carry the effects marker. TBL-124, TBL-157: an ability without words of its own reads as the mana it makes.
 */
class InspectEffectsTest {
    init {
        Cards.invent("Ember Herald", "{1}{R}", "Creature — Human", "", "2/2")
        Cards.invent("Hill Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
        Cards.invent("Ember Cairn", "{0}", "Artifact", "{T}: Add {R}.\n{5}: You gain 1 life.")
        Cards.invent("Valor Mantle", "{1}{W}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets +2/+2 and has flying.")
    }
    private val table = EngineTable()
    private val herald = table.put(table.alice, Zone.Battlefield, "Ember Herald").single()
    private val ogre = table.put(table.bob, Zone.Battlefield, "Hill Ogre").single()
    private val cairn = table.put(table.alice, Zone.Battlefield, "Ember Cairn").single()

    private fun mana(production: ManaKind) = Ability.Activated(Cost(listOf(CostPart.TapSelf)),
        Instructions(steps = listOf(Step.Do(Effect.AddMana(ManaProduction(production), Players.You)))),
        ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You))

    /** The board with one effect on Alice's Herald, made by her Herald, until end of turn. */
    private fun board(mechanic: Mechanic, bound: Map<String, NamedValue> = emptyMap()): ComputedBoard = board(listOf(mechanic), bound)

    /** The board with one effect of [mechanics] on Alice's Herald, made by her Herald, lasting [duration]. */
    private fun board(mechanics: List<Mechanic>, bound: Map<String, NamedValue> = emptyMap(),
        duration: Duration = Duration.EndOfTurn): ComputedBoard {
        val state = table.state()
        val id = table.obj(herald)
        state.setup.effects[EffectId("e1")] = EffectObject(EffectId("e1"), mechanics,
            Affected.Objects(Objects.Bound("affected")), locked = listOf(id), source = id, controller = PlayerId(table.alice),
            timestamp = 100, createdTurn = state.turn.number, duration = duration, names = Bindings(bound))
        return ComputedBoard.of(table.board(state, table.alice), table.base().definitions, table.definitions, table.alice, state, Engine.view(state))
    }

    private fun lines(board: ComputedBoard) = board.effects(listOf(herald), table.alice).getValue(herald).map { it.text }

    // CR 707.2, 613.2a: a copy effect is a line of its own, naming what it copies and how long
    @Test fun aCopyNamesWhatItCopies() {
        val board = board(Mechanic.CopyOf(Objects.Bound("original")), mapOf("original" to NamedValue.Objects(listOf(table.obj(ogre)))))
        assertEquals(listOf("Copy of Hill Ogre until end of turn"), lines(board))
        assertEquals(setOf(herald), board.withEffects, "the copy marks the Herald, nothing else")
    }

    // TBL-136 (web frame rate, 2026-11-15): the board's values as the page receives them carry the effects markers the engine side
    // worked out with its own view, so the page (its copy of the state) marks the same cards without running the layers again
    @Test fun thePagesBoardMarksTheCardsTheEngineSideMarked() {
        val board = board(Mechanic.CopyOf(Objects.Bound("original")), mapOf("original" to NamedValue.Objects(listOf(table.obj(ogre)))))
        val wire = board.wire()
        assertEquals(setOf(herald), wire.withEffects)
        assertEquals(setOf(herald), ComputedBoard.restored(wire, table.state()).withEffects, "marked from the values received")
    }

    // CR 613.1f: what it has "all activated abilities of" names that permanent
    @Test fun abilitiesOfAnotherPermanentNameIt() {
        val board = board(Mechanic.GainAbilitiesOf(Objects.Bound("lender")), mapOf("lender" to NamedValue.Objects(listOf(table.obj(cairn)))))
        assertEquals(listOf("Has the activated abilities of Ember Cairn until end of turn"), lines(board))
    }

    // CR 613.1f: a gained ability other than a keyword reads as its words
    @Test fun aGainedAbilityReadsAsItsWords() {
        val board = board(Mechanic.GainAbilities(listOf(mana(ManaKind.Fixed(ManaCost(listOf(ManaSymbol.Colored(Color.Green))))))))
        assertEquals(listOf("Gains \"{T}: Add {G}.\" until end of turn"), lines(board))
        assertEquals(emptySet(), board(Mechanic.GainAbilities(emptyList())).withEffects, "an effect that changes nothing marks nothing")
    }

    // TBL-124, TBL-157: a mana ability without words of its own says the mana it makes, in symbols
    @Test fun aManaAbilityWithoutWordsSaysItsMana() {
        assertEquals("{T}: Add {G}.", AbilityText.of(mana(ManaKind.Fixed(ManaCost(listOf(ManaSymbol.Colored(Color.Green))))), "test"))
        assertEquals("{T}: Add {R} or {G}.", AbilityText.of(mana(ManaKind.Choose(Value.Fixed(1),
            listOf(ColorRef.Literal(Color.Red), ColorRef.Literal(Color.Green)))), "test"))
        assertEquals("{T}: Add {W}, {U}, or {B}.", AbilityText.of(mana(ManaKind.Choose(Value.Fixed(1),
            listOf(ColorRef.Literal(Color.White), ColorRef.Literal(Color.Blue), ColorRef.Literal(Color.Black)))), "test"))
        assertEquals("{T}: Add one mana of any color.", AbilityText.of(mana(ManaKind.Choose(Value.Fixed(1))), "test"))
        assertEquals("{T}: Add two mana in any combination of colors.", AbilityText.of(mana(ManaKind.Choose(Value.Fixed(2), combination = true)), "test"))
    }

    private val pump = Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(2))

    // TBL-136, TBL-200 (owner, 2026-11-19): an until-end-of-turn change of only power and toughness shows on the table (TBL-112: the P/T
    // changes, with its ⌛), so it gets no sparkle; Inspect still lists it. With anything else in it, or lasting longer, it does.
    @Test fun anUntilEndOfTurnPowerChangeAloneHasNoSparkle() {
        val board = board(pump)
        assertEquals(listOf("+2/+2 until end of turn"), lines(board))
        assertEquals(emptySet(), board.withEffects, "the P/T and its ⌛ already tell it")
        assertEquals(setOf(herald), board(listOf(pump, Mechanic.GainAbilities(listOf(Keyword.Trample)))).withEffects, "the trample is seen only in Inspect")
        assertEquals(setOf(herald), board(listOf(pump), duration = Duration.UntilEndOfYourNextTurn).withEffects, "no ⌛: it lasts longer")
    }

    // TBL-136, TBL-200 (owner, 2026-11-19): what an Aura or Equipment attached to a card gives it is told by the card drawn under it: no
    // sparkle for it; the sparkle stays for any other effect on the card
    @Test fun whatAnAttachedCardGivesHasNoSparkle() {
        val mantle = table.put(table.alice, Zone.Battlefield, "Valor Mantle").single()
        fun attached(): arcanum.engine.state.GameState = table.state().also { state ->
            state.setup.objects[table.obj(mantle)] = state.objects.getValue(table.obj(mantle))
                .copy(attachedTo = arcanum.engine.state.TargetRef.Object(table.obj(herald)))
        }
        fun of(state: arcanum.engine.state.GameState) =
            ComputedBoard.of(table.board(state, table.alice), table.base().definitions, table.definitions, table.alice, state, Engine.view(state))
        val state = attached()
        assertEquals("4/4", Engine.view(state)[table.obj(herald)]?.characteristics?.let { "${it.power}/${it.toughness}" }, "the Mantle applies")
        val listed = of(state).effects(listOf(herald), table.alice).getValue(herald).map { it.text }
        assertEquals(listOf("Flying, +2/+2"), listed, "Inspect lists it")
        assertEquals(emptySet(), of(state).withEffects, "the Mantle under the Herald tells it")
        val other = attached()
        other.setup.effects[EffectId("e2")] = EffectObject(EffectId("e2"), listOf(Mechanic.GainAbilities(listOf(Keyword.Trample))),
            Affected.Objects(Objects.Bound("affected")), locked = listOf(table.obj(herald)), source = table.obj(ogre),
            controller = PlayerId(table.bob), timestamp = 200, createdTurn = other.turn.number, duration = Duration.EndOfTurn)
        assertEquals(setOf(herald), of(other).withEffects, "another effect on the Herald still marks it")
    }
}
