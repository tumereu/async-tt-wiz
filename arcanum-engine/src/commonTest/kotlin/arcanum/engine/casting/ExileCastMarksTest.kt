package arcanum.engine.casting

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Cost
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.foretell.Foretell
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.GameObject
import arcanum.engine.state.MarkGained
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Marks that let a card be cast from exile ([arcanum.api.MarkRules]): cards that became foretold or plotted by any path, foretell given by
 * another object, what can change foretelling itself, and marks an effect may only put where they can live.
 */
class ExileCastMarksTest {
    private val mana = { text: String -> Cost.mana(ManaCost.parse(text)!!) }
    private val tidings = Cards.invent("Tidings of the Shoal", "{3}{U}{U}", "Instant", "Draw a card.")
    private val omens = Cards.invent("Omens of the Shoal", "{3}{U}{U}", "Instant", """
        Draw a card.
        Foretell {1}{U} (During your turn, you may pay {2} and exile this card from your hand face down. Cast it on a later turn for its foretell cost.)
    """)
    private val idol = Cards.invent("Plain Idol", "{0}", "Artifact", "")

    /** An enchantment that gives each instant in a hand foretell {1}. */
    private val prophet = Cards.define("Shoal Prophet", CardDefinition(Layout.Normal, listOf(FaceDefinition("Shoal Prophet", types = listOf(CardType.Enchantment),
        abilities = listOf(Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Foretell(mana("{1}"))))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.Type(CardType.Instant)))))))))))

    private fun Scenario.foretellActions() = Engine.options(state, alice.id).filterIsInstance<Input.Activate>().filter { it.ability == Foretell.RULE }
    private fun Scenario.castsOf(id: arcanum.engine.state.ObjectId) = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().filter { it.card == id }
    private fun Scenario.libraries() = players.forEach { it.library("Forest", "Forest", "Forest", "Forest", "Forest") }

    private fun Scenario.marked(name: String, vararg marks: ObjectMark, change: (GameObject) -> GameObject = { it }): arcanum.engine.state.ObjectId {
        val id = card(name)
        state.objects[id] = change(state.objOrThrow(id).copy(marks = marks.toList()))
        return id
    }

    /** A spell with [steps], cast free in the scenarios that are about what it does. */
    private fun spell(name: String, vararg steps: Step): String = Cards.define(name, CardDefinition(Layout.Normal, listOf(FaceDefinition(
        name, types = listOf(CardType.Sorcery), abilities = listOf(Ability.Spell(Instructions(steps = steps.toList())))))))

    /** A sorcery that exiles the cards in graveyards and puts [mark] on them. */
    private fun exilingAndMarking(name: String, mark: ObjectMark) = spell(name,
        Step.Do(Effect.Move(Objects.Each(ObjectFilter.InZone(Zone.Graveyard)), Destination(Zone.Exile), postBind = "them")),
        Step.Do(Effect.Mark(Objects.Bound("them"), mark)))

    // ---- Foretell another object gives

    // CR 702.143a, e: a card foretold through foretell another object gives it keeps that cost in exile, even once that object is gone
    @Test
    fun aCardForetoldThroughGrantedForetellIsCastableLaterForThatCost() = scenario {
        alice.battlefield(prophet, "Island", "Island", "Island")
        alice.hand(tidings)
        libraries()
        assertEquals(1, foretellActions().size)
        alice.takesSpecialAction(tidings, "foretell", paying = listOf("Island", "Island"))
        val id = card(tidings)
        assertTrue(state.objOrThrow(id).faceDown)
        // CR 702.143a: not in the turn it was foretold
        assertTrue(castsOf(id).isEmpty())
        // The giver leaving changes nothing: the cost was the card's as it was foretold
        change(arcanum.engine.change.Changes.move(state, listOf(card(prophet)), Zone.Graveyard))
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertEquals(1, castsOf(id).size)
        alice.casts(tidings)
        allPass()
        expect { alice.graveyard has tidings }
    }

    // CR 702.143a: "any foretell cost it has": a printed one and one another object gave it are each a way to cast it
    @Test
    fun printedAndGrantedForetellCostsAreBothWays() = scenario {
        alice.battlefield(prophet, "Island", "Island")
        alice.hand(omens)
        libraries()
        alice.takesSpecialAction(omens, "foretell")
        val id = card(omens)
        advanceTo(TurnStep.Upkeep, turn = 3)
        val ways = castsOf(id).map { it.way.permission }
        assertEquals(2, ways.size, "$ways")
        assertEquals(2, ways.distinct().size, "each cost is a way of its own: $ways")
    }

    /** "Its mana cost reduced by {2}". */
    private val lessTwo = Cost(listOf(arcanum.api.CostPart.ItsManaCostLess(ManaCost.parse("{2}")!!)))

    /** The mana cost of the way [id] is cast now, read from its open payment (paid with what the scenario has). */
    private fun Scenario.castsFor(id: arcanum.engine.state.ObjectId): ManaCost {
        perform(castsOf(id).single())
        return decision<arcanum.engine.decision.Decision.ManaPayment>().cost
    }

    // CR 702.143d: "It becomes foretold. Its foretell cost is its mana cost reduced by {2}": the effect gives the card that cost, which it
    // is cast for on a later turn though it has no foretell
    @Test
    fun anEffectThatForetellsGivesAForetellCost() = scenario {
        val word = spell("Valkyrie Word",
            Step.Do(Effect.Move(Objects.Each(ObjectFilter.InZone(Zone.Graveyard)), Destination(Zone.Exile, faceDown = true), postBind = "them")),
            Step.Do(Effect.Mark(Objects.Bound("them"), ObjectMark.Foretold, cost = lessTwo)))
        alice.battlefield("Island", "Island", "Island")
        alice.graveyard(tidings)
        alice.hand(word)
        libraries()
        alice.castsWithoutCosts(word)
        resolve()
        val id = card(tidings)
        assertTrue(ObjectMark.Foretold in state.objOrThrow(id).marks)
        assertEquals(listOf(lessTwo), state.objOrThrow(id).facts.gained(ObjectMark.Foretold)?.costs)
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertEquals("{1}{U}{U}", castsFor(id).toString())
    }

    // CR 702.143a, d: foretell another object gives with "its foretell cost is equal to its mana cost reduced by {2}": the cost is the
    // card's own mana cost less {2}, kept as it is foretold
    @Test
    fun grantedForetellCanCostItsManaCostLessTwo() = scenario {
        val devourer = Cards.define("Hungry Prophet", CardDefinition(Layout.Normal, listOf(FaceDefinition("Hungry Prophet", types = listOf(CardType.Enchantment),
            abilities = listOf(Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Foretell(lessTwo)))),
                affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.Type(CardType.Instant)))))))))))
        alice.battlefield(devourer, "Island", "Island", "Island")
        alice.hand(tidings)
        libraries()
        alice.takesSpecialAction(tidings, "foretell", paying = listOf("Island", "Island"))
        val id = card(tidings)
        change(arcanum.engine.change.Changes.move(state, listOf(card(devourer)), Zone.Graveyard))
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertEquals("{1}{U}{U}", castsFor(id).toString())
    }

    // CR 702.143a: "any foretell cost it has": foretell given to a card already foretold in exile is a way to cast it too
    @Test
    fun foretellGivenLaterInExileIsAWay() = scenario {
        val foretelling = exilingAndMarking("Late Foretelling Word", ObjectMark.Foretold)
        val giver = Cards.define("Exile Prophet", CardDefinition(Layout.Normal, listOf(FaceDefinition("Exile Prophet", types = listOf(CardType.Enchantment),
            abilities = listOf(Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Foretell(mana("{1}"))))),
                affects = Affected.Objects(Objects.Each(ObjectFilter.InZone(Zone.Exile)))))))))
        alice.battlefield("Island")
        alice.graveyard(tidings)
        alice.hand(foretelling, giver)
        libraries()
        alice.castsWithoutCosts(foretelling)
        resolve()
        val id = card(tidings)
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertTrue(castsOf(id).isEmpty(), "no foretell cost: no way")
        change(arcanum.engine.change.Changes.move(state, listOf(card(giver)), Zone.Battlefield))
        assertEquals("{1}", castsFor(id).toString())
    }

    // ---- Effect.Mark on a card where the mark can live

    // CR 702.170c, d: an effect makes a card in exile plotted; it is cast free on a later turn, as a sorcery
    @Test
    fun aCardInExileBecomesPlottedByAnEffect() = scenario {
        val plotting = exilingAndMarking("Plotting Word", ObjectMark.Plotted)
        val bolt = Cards.invent("Quiet Bolt", "{4}{R}", "Instant", "You gain 1 life.")
        alice.graveyard(bolt)
        alice.hand(plotting)
        libraries()
        alice.castsWithoutCosts(plotting)
        resolve()
        val id = card(bolt)
        val o = state.objOrThrow(id)
        assertEquals(Zone.Exile, o.zone)
        assertTrue(ObjectMark.Plotted in o.marks, "marks=${o.marks}")
        assertEquals(state.turn.number, o.facts.gained(ObjectMark.Plotted)?.turn)
        assertTrue(castsOf(id).isEmpty(), "not in the turn it became plotted")
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertTrue(castsOf(id).isEmpty(), "only as a sorcery, even an instant")
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        assertEquals(1, castsOf(id).size)
        alice.casts(bolt)
        allPass()
        expect { alice.life shouldBe 21 }
    }

    // CR 702.143d: an effect makes a card in exile foretold; its owner may look at it and cast it for a foretell cost it has on a later turn
    @Test
    fun aCardInExileBecomesForetoldByAnEffect() = scenario {
        val foretelling = exilingAndMarking("Foretelling Word", ObjectMark.Foretold)
        alice.battlefield("Island", "Island")
        alice.graveyard(omens)
        alice.hand(foretelling)
        libraries()
        alice.castsWithoutCosts(foretelling)
        resolve()
        val id = card(omens)
        assertTrue(ObjectMark.Foretold in state.objOrThrow(id).marks)
        assertTrue(castsOf(id).isEmpty())
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertEquals(1, castsOf(id).size)
    }

    // CR 701.60b, 701.37b, 702.171b, 722.3a: a mark for permanents isn't put on a card in another zone
    @Test
    fun aPermanentsMarkIsntPutOnACardInExile() = scenario {
        val suspecting = exilingAndMarking("Accusing Word", ObjectMark.Suspected)
        alice.graveyard(idol)
        alice.hand(suspecting)
        alice.castsWithoutCosts(suspecting)
        resolve()
        assertFalse(ObjectMark.Suspected in state.objOrThrow(card(idol)).marks)
    }

    // CR 702.170c: a plotted card lives in exile: a permanent isn't made plotted
    @Test
    fun aPermanentIsntMadePlotted() = scenario {
        alice.battlefield(idol)
        val word = spell("Misplaced Word", Step.Do(Effect.Mark(Objects.Each(ObjectFilter.InZone(Zone.Battlefield)), ObjectMark.Plotted)))
        alice.hand(word)
        alice.castsWithoutCosts(word)
        resolve()
        assertFalse(ObjectMark.Plotted in state.objOrThrow(card(idol)).marks)
    }

    // ---- What can change foretelling

    // CR 601.2f, 702.143a: "foretelling costs {1} less": a cost change names foretelling's special action like any activation
    @Test
    fun foretellingCanCostLess() = scenario {
        val tutor = Cards.define("Cheap Prophecy", CardDefinition(Layout.Normal, listOf(FaceDefinition("Cheap Prophecy", types = listOf(CardType.Enchantment),
            abilities = listOf(Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.AbilityOf(keyword = Keyword.Foretell.Kind),
                arcanum.api.CostChange.Less(ManaCost.parse("{1}")!!, Value.Fixed(1)))), affects = Affected.Objects(Objects.Self)))))))
        alice.battlefield(tutor, "Island")
        alice.hand(omens)
        assertEquals(1, foretellActions().size, "{2} less {1} is payable with one land")
        alice.takesSpecialAction(omens, "foretell", paying = listOf("Island"))
        assertEquals(Zone.Exile, state.objOrThrow(card(omens)).zone)
    }

    // CR 118.9: "the first card you foretell each turn costs {0} to foretell": the first is free, the second isn't
    @Test
    fun theFirstForetellEachTurnCanBeFree() = scenario {
        val mentor = Cards.define("Free Prophecy", CardDefinition(Layout.Normal, listOf(FaceDefinition("Free Prophecy", types = listOf(CardType.Enchantment),
            abilities = listOf(Ability.Static(listOf(Mechanic.AbilityAlternativeCost(ObjectFilter.AbilityOf(keyword = Keyword.Foretell.Kind),
                mana("{0}"), firstEachTurn = true)), affects = Affected.Players(Players.You)))))))
        val second = Cards.invent("Second Omens", "{3}{U}", "Instant", "Foretell {U}\nDraw a card.")
        alice.battlefield(mentor)
        alice.hand(omens, second)
        assertEquals(2, foretellActions().size, "no land needed for the first")
        alice.takesSpecialAction(omens, "foretell")
        assertEquals(Zone.Exile, state.objOrThrow(card(omens)).zone)
        assertTrue(foretellActions().isEmpty(), "the second costs {2} again, with no land to pay")
    }

    // CR 702.143a: an effect can let foretelling be done any time its player has priority, not only during their turn
    @Test
    fun foretellingCanBeAllowedAnyTime() = scenario {
        val seer = Cards.define("Sleepless Prophecy", CardDefinition(Layout.Normal, listOf(FaceDefinition("Sleepless Prophecy", types = listOf(CardType.Enchantment),
            abilities = listOf(Ability.Static(listOf(Mechanic.ActivateAtInstantSpeed(ObjectFilter.AbilityOf(keyword = Keyword.Foretell.Kind))),
                affects = Affected.Players(Players.You)))))))
        bob.battlefield(seer, "Island", "Island")
        bob.hand(omens)
        alice.passes()
        assertEquals(1, Engine.options(state, bob.id).filterIsInstance<Input.Activate>().count { it.ability == Foretell.RULE })
        bob.takesSpecialAction(omens, "foretell")
        assertEquals(Zone.Exile, state.objOrThrow(card(omens)).zone)
    }

    // CR 702.143a: without such an effect it stays {2}, during your turn
    @Test
    fun aCostChangeForAnotherAbilityLeavesForetellingAlone() = scenario {
        val other = Cards.define("Equip Prophecy", CardDefinition(Layout.Normal, listOf(FaceDefinition("Equip Prophecy", types = listOf(CardType.Enchantment),
            abilities = listOf(Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.AbilityOf(keyword = Keyword.Equip.Kind),
                arcanum.api.CostChange.Less(ManaCost.parse("{2}")!!, Value.Fixed(1)))), affects = Affected.Objects(Objects.Self)))))))
        alice.battlefield(other)
        alice.hand(omens)
        assertTrue(foretellActions().isEmpty())
    }

    // ---- The record

    // CR 702.143a: the mark and its turn go through one generic record; foretelling writes it with the cost the card had
    @Test
    fun foretellingRecordsTheTurnAndCosts() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(omens)
        alice.takesSpecialAction(omens, "foretell")
        val facts = state.objOrThrow(card(omens)).facts
        assertEquals(listOf(MarkGained(ObjectMark.Foretold, state.turn.number, listOf(mana("{1}{U}")))), facts.marksGained)
    }

    // ---- Abilities while marked

    // CR 702.171b: the table's end-of-turn mark goes in the cleanup step
    @Test
    fun aMarkThatLastsUntilEndOfTurnGoes() = scenario {
        alice.battlefield(idol)
        players.forEach { it.library("Forest", "Forest") }
        val id = marked(idol, ObjectMark.Monstrous, ObjectMark.Saddled)
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(listOf<ObjectMark>(ObjectMark.Monstrous), state.objOrThrow(id).marks)
    }

    // CR 701.60c: a suspected permanent has both menace and "This creature can't block": a later loss of all abilities takes both, so it can block
    @Test
    fun aLaterLossOfAllAbilitiesTakesBothOfWhatSuspectedGives() = scenario {
        val ledger = Cards.invent("Accusing Ledger", "{0}", "Artifact", "{0}: Suspect target creature.")
        val hush = Cards.invent("Sudden Quiet", "{0}", "Instant", "Target creature loses all abilities until end of turn.")
        val cub = Cards.invent("Silent Cub", "{0}", "Creature — Bear", "", "2/2")
        alice.battlefield(ledger, cub)
        alice.hand(hush)
        alice.activates(ledger, target = cub)
        allPass()
        alice.castsWithoutCosts(hush, target = cub)
        allPass()
        assertTrue(ObjectMark.Suspected in state.objOrThrow(card(cub)).marks)
        val v = Engine.view(state)[card(cub)]!!
        assertFalse(v.characteristics!!.hasKeyword(Keyword.Menace))
        assertFalse(v.has { it is Mechanic.CantBlock })
    }

    // CR 701.60a, c: no longer suspected, it has neither
    @Test
    fun noLongerSuspectedEndsBoth() = scenario {
        val cub = Cards.invent("Cleared Cub", "{0}", "Creature — Bear", "", "2/2")
        val cleanse = spell("Clearing Word", Step.Do(Effect.Mark(Objects.Each(ObjectFilter.Type(CardType.Creature)), ObjectMark.Suspected, remove = true)))
        alice.battlefield(cub)
        alice.hand(cleanse)
        marked(cub, ObjectMark.Suspected)
        alice.castsWithoutCosts(cleanse)
        resolve()
        assertFalse(ObjectMark.Suspected in state.objOrThrow(card(cub)).marks)
        val v = Engine.view(state)[card(cub)]!!
        assertFalse(v.characteristics!!.hasKeyword(Keyword.Menace))
        assertFalse(v.has { it is Mechanic.CantBlock })
    }

    // CR 701.60c: a suspected permanent has menace and can't block for as long as it's suspected, without an ability on the card
    @Test
    fun aSuspectedPermanentHasMenaceAndCantBlockFromTheTable() = scenario {
        val cub = Cards.invent("Plain Cub", "{1}", "Creature — Bear", "", "1/1")
        alice.battlefield(cub)
        val id = marked(cub, ObjectMark.Suspected)
        val v = Engine.view(state)[id]!!
        assertTrue(v.characteristics!!.hasKeyword(Keyword.Menace))
        assertTrue(v.has { it == Mechanic.CantBlock(ObjectFilter.Any) })
    }
}
