package arcanum.parser.sets.dft

import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Aetherdrift chunk 03: effects that make Vehicles artifact creatures (CR 301.7b) and Mounts saddled (CR 702.171b) outside their own crew and
 * saddle abilities: at the beginning of combat, as they enter, when returned from the graveyard (for good), for all Vehicles you control,
 * and the Vehicle tokens with crew 1 (CR 111.4).
 */
class DftChunk03VehiclesBecomeCreaturesTest {
    // CR 702.171b: saddled until end of turn; a permanent that is both a Mount and a Vehicle gets both
    @Test
    fun alacrianOutfitters() = card(
        name = "Alacrian Outfitters",
        cost = "{3}{W}",
        type = "Artifact",
        text = """
            Creatures you control get +0/+1 and have vigilance.
            At the beginning of combat on your turn, choose up to one target Mount or Vehicle you control. Until end of turn, that permanent becomes saddled if it's a Mount and becomes an artifact creature if it's a Vehicle.
        """,
    ) parsesTo abilities(
        static(pt(0, 1), has(Keyword.Vigilance), affects = Objects.Each(creatureYouControl)),
        triggered(beginningOfCombatOnYourTurn,
            Step.If(Condition.ObjectMatches(target("permanent"), mount), then = listOf(doing(Effect.Mark(target("permanent"), ObjectMark.Saddled)))),
            Step.If(Condition.ObjectMatches(target("permanent"), vehicle), then = listOf(becomesArtifactCreature(target("permanent")))),
            targets = listOf(slot("permanent", and(mountOrVehicle, you), min = 0))),
    )

    // CR 611.2a: no duration: it stays an artifact creature
    @Test
    fun retune() = card(
        name = "Retune",
        cost = "{3}{W}",
        type = "Sorcery",
        text = "Return target artifact card from your graveyard to the battlefield. If it's a Vehicle, it becomes an artifact creature.",
    ) parsesTo abilities(
        spell(
            move(target("card"), Zone.Battlefield, postBind = "returned"),
            Step.If(Condition.ObjectMatches(Objects.Bound("returned"), vehicle), then = listOf(doing(Effect.ApplyMechanics(
                listOf(Mechanic.SetTypes(add = listOf(arcanum.api.CardType.Artifact, arcanum.api.CardType.Creature))),
                arcanum.api.Affected.Objects(Objects.Bound("returned")), arcanum.api.Duration.Indefinitely)))),
            targets = listOf(slot("card", inYourGraveyard(artifact)))),
    )

    // CR 111.4: a Vehicle token is an artifact with crew 1, a creature only once crewed
    @Test
    fun muLanyingGaleRacer() = card(
        name = "Mu Lanying, Gale Racer",
        cost = "{2}{U}{U}",
        type = "Legendary Creature — Human Wizard Pilot",
        text = """
            When Mu Lanying enters, create a 3/2 colorless Vehicle artifact token with crew 1.
            Vehicles you control have flying.
            Whenever one or more creatures you control with flying deal combat damage to a player, draw a card.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        triggered(entersSelf, create(vehicleToken)),
        static(has(Keyword.Flying), affects = Objects.Each(and(vehicle, you))),
        triggered(Trigger.On(listOf(EventPattern.Damage(and(creature, you, kw(Keyword.Flying.kind)), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            oneOrMore = true, batchPerPlayer = true), draw(1)),
    )

    @Test
    fun chandraSparkRacer() = card(
        name = "Chandra, Spark Racer",
        cost = "{3}{R}",
        type = "Legendary Planeswalker — Chandra",
        text = """
            At the beginning of combat on your turn, choose up to one target Vehicle you control. Until end of turn, it becomes an artifact creature and gains haste.
            [+2]: You may sacrifice an artifact or discard a card. If you do, draw a card.
            [0]: Create a 3/2 colorless Vehicle artifact token with crew 1.
            [−7]: You get an emblem with "Whenever an artifact you control enters, this emblem deals 3 damage to any target."
        """,
        loyalty = "4",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn,
            untilEndOfTurn(target("Vehicle"), Mechanic.SetTypes(add = listOf(arcanum.api.CardType.Artifact, arcanum.api.CardType.Creature)), has(Keyword.Haste)),
            targets = listOf(slot("Vehicle", and(vehicle, you), min = 0))),
        activated(cost(CostPart.Loyalty(n(2))),
            Step.Pay(Cost(listOf(CostPart.OneOf(listOf(cost(sacrificeA(artifact)), cost(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1)))))))),
                payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(1)))),
        activated(cost(CostPart.Loyalty(n(0))), create(vehicleToken)),
        activated(cost(CostPart.Loyalty(n(-7))), doing(Effect.CreateEmblem(listOf(
            triggered(enters(and(artifact, you)), damage(3, toAny), targets = listOf(anyTarget))), Players.You))),
    )

    // CR 603.7: the returned cards are sacrificed at the next end step; the haste goes to the Vehicles made creatures too (CR 611.2c)
    @Test
    fun pushTheEnvelope() = card(
        name = "Push the Envelope",
        cost = "{5}{R}{R}",
        type = "Sorcery",
        text = """
            Return all Mount and Vehicle cards from your graveyard to the battlefield. Sacrifice them at the beginning of the next end step.
            Vehicles you control become artifact creatures until end of turn. Creatures you control gain haste until end of turn.
        """,
    ) parsesTo abilities(
        spell(
            move(Objects.Each(inYourGraveyard(mountOrVehicle)), Zone.Battlefield, postBind = "returned"),
            delayed(stepBegins(TurnStep.End, PlayerFilter.Any), doing(Effect.Sacrifice(Objects.Bound("returned")))),
            becomesArtifactCreature(Objects.Each(and(vehicle, you))),
            gains(Objects.Each(creatureYouControl), Keyword.Haste),
        ),
    )

    // CR 603.4: checked as it triggers and as it resolves
    // CR 506.4: "attacked this combat" counts creatures declared as attackers even after removal from combat
    @Test
    fun fearlessCorsair() = card(
        name = "Fearless Corsair",
        cost = "{1}{U}{R}",
        type = "Creature — Fish Pirate",
        text = """
            Haste
            Vehicles you control have haste.
            Whenever you attack, if a Pirate and a Vehicle attacked this combat, draw three cards, then discard two cards.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Haste,
        static(has(Keyword.Haste), affects = Objects.Each(and(vehicle, you))),
        triggered(youAttack, draw(3), discard(2), interveningIf = Condition.And(listOf(
            Condition.Exists(and(sub("Pirate"), ObjectFilter.AttackedThisCombat), atLeast = 1), Condition.Exists(and(vehicle, ObjectFilter.AttackedThisCombat), atLeast = 1)))),
    )

    @Test
    fun koladinTriumphRacer() = card(
        name = "Koladin, Triumph Racer",
        cost = "{R}{W}",
        type = "Legendary Creature — Human Pilot",
        text = """
            Mounts and Vehicles you control have haste.
            Whenever a Mount you control enters, it becomes saddled until end of turn.
            Whenever a Vehicle you control enters, it becomes an artifact creature until end of turn.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        static(has(Keyword.Haste), affects = Objects.Each(and(mountOrVehicle, you))),
        triggered(enters(and(mount, you)), doing(Effect.Mark(enteredOne, ObjectMark.Saddled))),
        triggered(enters(and(vehicle, you)), becomesArtifactCreature(enteredOne)),
    )

    @Test
    fun pathlightMatrix() = card(
        name = "Pathlight Matrix",
        cost = "{2}",
        type = "Artifact",
        text = """
            When this artifact enters, draw a card.
            {2}, {T}: Target Mount you control becomes saddled until end of turn. Activate only as a sorcery.
            {2}, {T}: Target Vehicle you control becomes an artifact creature until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, draw(1)),
        activated(cost(mana("{2}"), tap), doing(Effect.Mark(target("Mount"), ObjectMark.Saddled)), targets = listOf(slot("Mount", and(mount, you))), rules = asSorcery),
        activated(cost(mana("{2}"), tap), becomesArtifactCreature(target("Vehicle")), targets = listOf(slot("Vehicle", and(vehicle, you)))),
    )
}
