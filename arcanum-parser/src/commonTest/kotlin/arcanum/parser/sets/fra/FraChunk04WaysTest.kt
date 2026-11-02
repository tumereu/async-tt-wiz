package arcanum.parser.sets.fra

import arcanum.api.AbilityKind
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.targetPlayer
import arcanum.parser.sets.gtc.you
import kotlin.test.Test

/**
 * Reality Fracture chunk 04: the "Way of" legendary enchantments. Each empowers Jace as it enters (CR 701.71a) and then
 * grants planeswalkers you control a loyalty ability (CR 606, 613.1f: granted in layer 6, "this planeswalker" being the one
 * that has it, CR 113.10) or triggers on gaining life, on a creature dying or on activating a loyalty ability (one that
 * removed two or more loyalty counters: its cost, CR 606.4); "When you next cast an instant or sorcery spell this turn" is a
 * delayed trigger (CR 603.7), an additional land play this turn an effect (CR 305.2).
 */
class FraChunk04WaysTest {
    // CR 613.1f
    @Test
    fun pathOfTheMender() = card(
        name = "Path of the Mender",
        cost = "{3}{W}",
        type = "Legendary Enchantment",
        text = """
            When Path of the Mender enters, empower Jace 5. (Put five loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
            Planeswalkers you control have "[−2]: Create a 2/2 colorless Wizard Soldier creature token named Cadet. Surveil 1."
        """,
    ) parsesTo abilities(
            triggered(entersSelf, empower(5)),
            static(Mechanic.GainAbilities(listOf(loyalty(-2, createCadets(1), surveil(1)))), affects = Objects.Each(and(planeswalker, you))),
        )

    @Test
    fun pathOfTheTutor() = card(
        name = "Path of the Tutor",
        cost = "{2}{W}",
        type = "Legendary Enchantment",
        text = """
            When Path of the Tutor enters, empower Jace 5. (Put five loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
            Whenever you gain life, put a loyalty counter on each planeswalker you control.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, empower(5)),
            triggered(youGainLife, counters(Objects.Each(and(planeswalker, you)), kind = CounterKind.Loyalty)),
        )

    // CR 603.7: triggers once, the next such spell
    @Test
    fun pathOfTheFrostcaster() = card(
        name = "Path of the Frostcaster",
        cost = "{2}{U}",
        type = "Legendary Enchantment",
        text = """
            When Path of the Frostcaster enters, empower Jace 5. (Put five loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
            Planeswalkers you control have "[−3]: When you next cast an instant or sorcery spell this turn, copy that spell. You may choose new targets for the copy."
        """,
    ) parsesTo abilities(
            triggered(entersSelf, empower(5)),
            static(Mechanic.GainAbilities(listOf(loyalty(-3, Step.Do(Effect.CreateDelayedTrigger(
                youCast(and(or(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery)), spellFilter)),
                Instructions(steps = listOf(Step.Do(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = true)))),
                duration = Duration.EndOfTurn))))), affects = Objects.Each(and(planeswalker, you))),
        )

    // CR 606.4: the counters removed to pay its cost
    @Test
    fun pathOfTheThoughtshaper() = card(
        name = "Path of the Thoughtshaper",
        cost = "{4}{U}",
        type = "Legendary Enchantment",
        text = """
            When Path of the Thoughtshaper enters, empower Jace 5. (Put five loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
            Whenever you activate a loyalty ability, if you removed two or more loyalty counters to activate it, draw a card.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, empower(5)),
            triggered(on(EventPattern.Activate(ObjectFilter.Any, PlayerFilter.You, kind = AbilityKind.Loyalty, removedLoyaltyAtLeast = 2)), draw(1)),
        )

    // CR 118.12
    @Test
    fun pathOfTheDoomcaller() = card(
        name = "Path of the Doomcaller",
        cost = "{2}{B}",
        type = "Legendary Enchantment",
        text = """
            When Path of the Doomcaller enters, empower Jace 5. (Put five loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
            Planeswalkers you control have "[−2]: You may sacrifice a creature. If you do, create a 4/4 green Beast creature token with trample."
        """,
    ) parsesTo abilities(
            triggered(entersSelf, empower(5)),
            static(Mechanic.GainAbilities(listOf(loyalty(-2,
                Step.Pay(cost(CostPart.Sacrifice(CostObjects.Choose(creature, n(1)))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.CreateToken(creatureToken("Beast", listOf(Color.Green), 4, 4, abilities = listOf(Keyword.Trample)), n(1), Players.You))))))), affects = Objects.Each(and(planeswalker, you))),
        )

    @Test
    fun pathOfTheGravecaller() = card(
        name = "Path of the Gravecaller",
        cost = "{1}{B}",
        type = "Legendary Enchantment",
        text = """
            When Path of the Gravecaller enters, empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
            Whenever a creature you control dies, put a loyalty counter on each planeswalker you control.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, empower(2)),
            triggered(on(EventPattern.ZoneChange(creatureYouControl, from = Zone.Battlefield, to = Zone.Graveyard)), counters(Objects.Each(and(planeswalker, you)), kind = CounterKind.Loyalty)),
        )

    // CR 605.1a: a loyalty ability isn't a mana ability
    @Test
    fun pathOfTheFlamecaller() = card(
        name = "Path of the Flamecaller",
        cost = "{1}{R}",
        type = "Legendary Enchantment",
        text = """
            When Path of the Flamecaller enters, empower Jace 2. (Put two loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
            Planeswalkers you control have "[+1]: Add {R}."
        """,
    ) parsesTo abilities(
            triggered(entersSelf, empower(2)),
            static(Mechanic.GainAbilities(listOf(loyalty(1, Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!)), Players.You))))), affects = Objects.Each(and(planeswalker, you))),
        )

    @Test
    fun pathOfTheBattlelord() = card(
        name = "Path of the Battlelord",
        cost = "{2}{R}",
        type = "Legendary Enchantment",
        text = """
            When Path of the Battlelord enters, empower Jace 5. (Put five loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
            Planeswalkers you control have "[−4]: This planeswalker deals 2 damage to up to one target creature or planeswalker and 2 damage to target player."
        """,
    ) parsesTo abilities(
            triggered(entersSelf, empower(5)),
            static(Mechanic.GainAbilities(listOf(loyalty(-4,
                damage(2, Recipients(objects = target("permanent"))),
                damage(2, Recipients(players = Players.Target("player"))),
                targets = listOf(slot("permanent", creatureOrPlaneswalker, min = 0), targetPlayer)))), affects = Objects.Each(and(planeswalker, you))),
        )

    // CR 305.2
    @Test
    fun pathOfTheContradiction() = card(
        name = "Path of the Contradiction",
        cost = "{2}{G}",
        type = "Legendary Enchantment",
        text = """
            When Path of the Contradiction enters, empower Jace 5. (Put five loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
            Whenever you activate a loyalty ability, you gain 1 life. You may play an additional land this turn.
        """,
    ) parsesTo abilities(
            triggered(entersSelf, empower(5)),
            triggered(youActivateLoyalty, gainLife(1), Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ExtraLandPlays(1)), Affected.Players(Players.You), Duration.EndOfTurn))),
        )

    @Test
    fun pathOfTheBeastcaller() = card(
        name = "Path of the Beastcaller",
        cost = "{4}{G}",
        type = "Legendary Enchantment",
        text = """
            When Path of the Beastcaller enters, empower Jace 7. (Put seven loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
            Planeswalkers you control have "[−4]: Create a 4/4 green Beast creature token with trample."
        """,
    ) parsesTo abilities(
            triggered(entersSelf, empower(7)),
            static(Mechanic.GainAbilities(listOf(loyalty(-4, Step.Do(Effect.CreateToken(creatureToken("Beast", listOf(Color.Green), 4, 4, abilities = listOf(Keyword.Trample)), n(1), Players.You))))), affects = Objects.Each(and(planeswalker, you))),
        )
}
