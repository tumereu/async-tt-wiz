package arcanum.parser.sets.dft

import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Aetherdrift chunk 13: red cards: the top card exiled and playable this turn, a mode destroying a Vehicle, a power defined by the greatest
 * mana value among artifacts (CR 604.3), returning a chosen card if you sacrifice (CR 118.12), additional combat phases with a repeating
 * untap trigger (CR 500.8, 603.7b), a reflexive trigger on a sacrifice (CR 603.12), +X/+0 from the X paid, and a sorcery-speed once-each-turn
 * rummage.
 */
class DftChunk13RedTest {
    @Test
    fun countOnFortune() = card(
        name = "Count on Fortune",
        cost = "{R}{R}{R}",
        type = "Enchantment",
        text = "At the beginning of your upkeep, exile the top card of your library. You may play that card this turn.",
    ) parsesTo abilities(triggered(yourUpkeep, *exileTopMayPlayThisTurn))

    @Test
    fun crashAndBlaze() = card(
        name = "Crash and Blaze",
        cost = "{3}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Destroy target Vehicle.
            • Crash and Blaze deals 6 damage to target creature or planeswalker.
        """,
    ) parsesTo abilities(
        modal(
            mode(destroy(target("Vehicle")), targets = listOf(slot("Vehicle", vehicle))),
            mode(damage(6, toObjects(target("permanent"))), targets = listOf(slot("permanent", or(creature, planeswalker)))),
        ),
    )

    // CR 604.3: its power in every zone
    @Test
    fun darettoRocketEngineer() = card(
        name = "Daretto, Rocket Engineer",
        cost = "{4}{R}",
        type = "Legendary Creature — Goblin Artificer",
        text = """
            Daretto's power is equal to the greatest mana value among artifacts you control.
            Whenever Daretto enters or attacks, choose target artifact card in your graveyard. You may sacrifice an artifact. If you do, return the chosen card to the battlefield.
        """,
        pt = "*/5",
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(Value.Of(NumericProperty.ManaValue, Objects.Each(and(artifact, you)), Combine.Greatest), null), characteristicDefining = true),
        triggered(entersOrAttacksSelf, Step.Pay(cost(sacrificeA(artifact)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(move(target("card"), Zone.Battlefield))),
            targets = listOf(slot("card", inYourGraveyard(artifact)))),
    )

    // CR 500.8: the additional combats follow this main phase; CR 603.7b: the untap trigger works at each of them this turn
    @Test
    fun wideOpenThrottle() = card(
        name = "Wide Open Throttle",
        cost = "{4}{R}{R}",
        type = "Sorcery",
        text = """
            After this main phase, there are two additional combat phases.
            At the beginning of each combat this turn, untap all creatures that attacked this turn.
        """,
    ) parsesTo abilities(
        spell(
            Step.Repeat(n(2), listOf(doing(Effect.ExtraCombat()))),
            doing(Effect.CreateDelayedTrigger(stepBegins(TurnStep.BeginningOfCombat, PlayerFilter.Any),
                Instructions(steps = listOf(untapIt(Objects.Each(and(creature, ObjectFilter.ThisTurn(EventPattern.Attack(ObjectFilter.Any))))))),
                duration = Duration.EndOfTurn, repeats = true)),
        ),
    )

    // CR 603.12: the target is chosen once the creature or Vehicle was sacrificed
    @Test
    fun gastalWrecker() = card(
        name = "Gastal Wrecker",
        cost = "{2}{R}",
        type = "Creature — Human Berserker",
        text = "When this creature enters, you may sacrifice a creature or Vehicle. When you do, destroy target artifact an opponent controls.",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Pay(cost(sacrificeA(creatureOrVehicle)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(destroy(target("artifact")), targets = listOf(slot("artifact", and(artifact, opponents))))))),
    )

    @Test
    fun pedalToTheFloor() = card(
        name = "Pedal to the Floor",
        cost = "{X}{R}",
        type = "Instant",
        text = "Target creature gets +X/+0 and gains first strike until end of turn.",
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(x, n(0)), has(Keyword.FirstStrike)), targets = listOf(slot("creature", creature)),
            announced = listOf("X")),
    )

    @Test
    fun thunderheadCannoneer() = card(
        name = "Thunderhead Cannoneer",
        cost = "{4}{R}",
        type = "Creature — Shark Pirate",
        text = """
            Reach
            Discard a card: Draw a card. Activate only as a sorcery and only once each turn.
        """,
        pt = "4/5",
    ) parsesTo abilities(
        Keyword.Reach,
        activated(cost(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1)))), draw(1), rules = asSorcery.copy(perTurn = 1)),
    )

    @Test
    fun lightningJolt() = card(
        name = "Lightning Jolt",
        cost = "{1}{R}",
        type = "Instant",
        text = "Lightning Jolt deals 3 damage to any target.",
    ) parsesTo abilities(spell(damage(3, toAny), targets = listOf(anyTarget)))

    @Test
    fun tyraxScaleTyrant() = card(
        name = "Tyrax, Scale Tyrant",
        cost = "{1}{R}",
        type = "Legendary Creature — Dinosaur Warrior",
        pt = "4/1",
    ) parsesTo abilities()
}
