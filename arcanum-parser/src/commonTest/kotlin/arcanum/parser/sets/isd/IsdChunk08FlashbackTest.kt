package arcanum.parser.sets.isd

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Color
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * ISD chunk 08: Flashback, part 2 (CR 702.34a: cast from the graveyard for the flashback cost, then exiled, CR 118.9). The spell part reads
 * as it does without flashback.
 */
class IsdChunk08FlashbackTest {
    private val instantOrSorcery = ObjectFilter.Or(listOf(instant, sorcery))

    // CR 119.1 (gaining life), 702.34a
    @Test
    fun gnawTheMarrow() = card(
        name = "Gnaw the Marrow",
        cost = "{2}{G}",
        type = "Instant",
        text = """
            You gain 2 life for each creature card in your graveyard.
            Flashback {2}{G} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(doing(Effect.GainLife(arcanum.api.Players.You, Value.Times(count(inYourGraveyard(creature)), 2)))),
        Keyword.Flashback(manaCost("{2}{G}")),
    )

    // CR 400.6 (the owner's library), 702.34a
    @Test
    fun graspOfShadows() = card(
        name = "Grasp of Shadows",
        cost = "{3}{U}",
        type = "Sorcery",
        text = """
            Put target creature on top of its owner's library.
            Flashback {7}{U} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(Step.Do(Effect.Move(target("creature"), Destination(Zone.Library, libraryPosition = 0))), targets = listOf(slot("creature", creature))),
        Keyword.Flashback(manaCost("{7}{U}")),
    )

    // CR 111.1, 702.34a
    @Test
    fun moanOfTheRestless() = card(
        name = "Moan of the Restless",
        cost = "{2}{B}{B}",
        type = "Sorcery",
        text = """
            Create two 2/2 black Zombie creature tokens.
            Flashback {5}{B}{B} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(create(zombie, n(2))),
        Keyword.Flashback(manaCost("{5}{B}{B}")),
    )

    // CR 702.34a, 611.2c (the set of cards is fixed when it resolves), 118.9 (the flashback cost is the mana cost)
    @Test
    fun pastInEmbers() = card(
        name = "Past in Embers",
        cost = "{3}{R}",
        type = "Sorcery",
        text = """
            Each instant and sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal to its mana cost.
            Flashback {4}{R} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(Step.Do(Effect.ApplyMechanics(
            mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.Flashback(Cost(listOf(CostPart.ItsManaCost)))))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(instantOrSorcery) + yourGraveyard))),
            duration = Duration.EndOfTurn,
        ))),
        Keyword.Flashback(manaCost("{4}{R}")),
    )

    // CR 702.34a
    @Test
    fun purifyTheTomb() = card(
        name = "Purify the Tomb",
        cost = "{W}",
        type = "Instant",
        text = """
            Exile target card from a graveyard.
            Flashback {W} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(Step.Do(Effect.Move(target("card"), Destination(Zone.Exile), postBind = "exiled")),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
        Keyword.Flashback(manaCost("{W}")),
    )

    // CR 611.2a (until end of turn), 702.34a
    @Test
    fun rallyTheCrofters() = card(
        name = "Rally the Crofters",
        cost = "{2}{W}",
        type = "Instant",
        text = """
            Creatures you control get +2/+0 until end of turn.
            Flashback {2}{R} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(pump(Objects.Each(and(creature, you)), 2, 0)),
        Keyword.Flashback(manaCost("{2}{R}")),
    )

    // CR 120.3 (damage to creatures), 702.34a
    @Test
    fun rollingTremor() = card(
        name = "Rolling Tremor",
        cost = "{2}{R}",
        type = "Sorcery",
        text = """
            Rolling Tremor deals 2 damage to each creature without flying.
            Flashback {4}{R}{R} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(damage(2, toObjects(Objects.Each(and(creature, not(kw(Keyword.Flying))))))),
        Keyword.Flashback(manaCost("{4}{R}{R}")),
    )

    // CR 701.8 (exile), 201.2 (same name), 702.34a
    @Test
    fun severTheLineage() = card(
        name = "Sever the Lineage",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            Exile target creature and all other creatures with the same name as that creature.
            Flashback {5}{B}{B} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(move(Objects.Union(listOf(target("creature"),
            Objects.Each(and(not(ObjectFilter.Is(target("creature"))), creature, ObjectFilter.SameNameAs(target("creature")))))), Zone.Exile),
            targets = listOf(slot("creature", creature))),
        Keyword.Flashback(manaCost("{5}{B}{B}")),
    )

    // CR 400.3 (the owner's hand), 702.34a
    @Test
    fun silentExit() = card(
        name = "Silent Exit",
        cost = "{U}",
        type = "Sorcery",
        text = """
            Return target creature to its owner's hand.
            Flashback {4}{U} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(move(target("creature"), Zone.Hand), targets = listOf(slot("creature", creature))),
        Keyword.Flashback(manaCost("{4}{U}")),
    )

    // CR 111.1, 702.34a: one token per creature card in your graveyard when it resolves
    @Test
    fun weaveOfSpiders() = card(
        name = "Weave of Spiders",
        cost = "{4}{G}",
        type = "Sorcery",
        text = """
            Create a 1/2 green Spider creature token with reach for each creature card in your graveyard.
            Flashback {6}{B} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(create(creatureToken("Spider", listOf(Color.Green), 1, 2, listOf(Keyword.Reach)), count(inYourGraveyard(creature)))),
        Keyword.Flashback(manaCost("{6}{B}")),
    )

    // CR 121.1, 702.34a
    @Test
    fun thinkAnew() = card(
        name = "Think Anew",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Draw a card.
            Flashback {2}{U} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(draw(1)),
        Keyword.Flashback(manaCost("{2}{U}")),
    )

    // CR 122.1, 115.2 (each of up to two targets), 702.34a
    @Test
    fun travelProvisionsTwo() = card(
        name = "Travel Stores",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """
            Put a +1/+1 counter on each of up to two target creatures.
            Flashback {1}{W} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(counters(target("creatures")), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
        Keyword.Flashback(manaCost("{1}{W}")),
    )

    // CR 110.1, 702.34a
    @Test
    fun unburialRitual() = card(
        name = "Unburial Ritual",
        cost = "{4}{B}",
        type = "Sorcery",
        text = """
            Return target creature card from your graveyard to the battlefield.
            Flashback {3}{W} $flashbackReminder
        """,
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(creature)))),
        Keyword.Flashback(manaCost("{3}{W}")),
    )
}
