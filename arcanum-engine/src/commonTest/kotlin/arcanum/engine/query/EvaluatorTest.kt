package arcanum.engine.query

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.view.Functioning
import arcanum.engine.view.WorkBoard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The building blocks of definitions, worked out in a game (D19, D27, D47). */
class EvaluatorTest {
    private fun Scenario.evaluator(you: String = "alice", self: String? = null): Evaluator {
        val view = view()
        val board = WorkBoard(state, view.objects.mapNotNull { (id, o) ->
            o.characteristics?.let { id to arcanum.engine.view.Partial(it, o.controller) }
        }.toMap().toMutableMap())
        return Evaluator(board, Context(you = arcanum.engine.state.PlayerId(you), self = self?.let { card(it) }))
    }

    private val creatures = ObjectFilter.Type(CardType.Creature)

    // CR 109.2: a filter without a zone means permanents on the battlefield; InZone names other zones
    @Test
    fun eachLooksAtTheBattlefieldUnlessAZoneIsNamed() = scenario {
        alice.battlefield("Elvish Seer")
        alice.graveyard("Lineage Druid")
        val e = evaluator()
        assertEquals(listOf(card("Elvish Seer")), e.objects(Objects.Each(creatures)))
        assertEquals(listOf(card("Lineage Druid")), e.objects(Objects.Each(ObjectFilter.And(listOf(creatures, ObjectFilter.InZone(Zone.Graveyard))))))
    }

    // CR 102.2, 102.3: in a multiplayer game every other player is an opponent
    @Test
    fun opponentsInMultiplayer() = scenario(players = 3) {
        val e = evaluator(you = "bob")
        assertEquals(listOf("alice", "carol"), e.players(Players.Each(PlayerFilter.Opponent)).map { it.value })
    }

    // D27: numbers over a group combine as written; an empty group gives 0
    @Test
    fun valuesOverGroups() = scenario {
        alice.battlefield("Elvish Seer", "Gloomcrest Herald")
        val e = evaluator()
        val mine = Objects.Each(creatures)
        // The Herald's anthem makes the Seer 2/2: the evaluator reads the view's characteristics
        assertEquals(4, e.value(Value.Of(NumericProperty.Power, mine, Combine.Total)))
        assertEquals(2, e.value(Value.Of(NumericProperty.Power, mine, Combine.Greatest)))
        assertEquals(0, e.value(Value.Of(NumericProperty.Power, Objects.Each(ObjectFilter.Type(CardType.Planeswalker)), Combine.Greatest)))
    }

    // An object condition over no objects is false ("if an artifact was destroyed this way")
    @Test
    fun objectConditionOverNothingIsFalse() = scenario {
        val e = evaluator()
        assertFalse(e.holds(Condition.ObjectMatches(Objects.Each(creatures), creatures)))
        assertTrue(e.holds(Condition.Not(Condition.Exists(creatures, atLeast = 1))))
    }

    // CR 113.6: where abilities work by default
    @Test
    fun functioningZones() {
        fun abilities(name: String) = Cards.definition(name).faces.first().abilities
        // CR 113.6j: cycling's cost discards the card, so it works in the hand
        val cycling = arcanum.engine.view.Copiable.expand(abilities("Hallowed Mire Triad").mapIndexed { i, a ->
            arcanum.engine.view.AbilityInstance(a, arcanum.engine.view.AbilityOrigin.Printed(0, i))
        }).map { it.ability }.filterIsInstance<Ability.Activated>().single()
        assertEquals(setOf(Zone.Hand), Functioning.zones(cycling, instantOrSorcery = false))
        // CR 113.6a: characteristic-defining abilities work everywhere
        val cda = abilities("Lord of the Aetherforge").filterIsInstance<Ability.Static>().first { it.characteristicDefining }
        assertEquals(Functioning.EVERYWHERE, Functioning.zones(cda, instantOrSorcery = false))
        // CR 113.6: a permanent's static ability works on the battlefield
        val anthem = abilities("Elvish Hierophant").filterIsInstance<Ability.Static>().single()
        assertEquals(setOf(Zone.Battlefield), Functioning.zones(anthem, instantOrSorcery = false))
    }
}
