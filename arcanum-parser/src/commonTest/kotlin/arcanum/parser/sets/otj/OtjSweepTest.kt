package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * OTJ sweep: siblings of the set's parser fixes ("its power" read as this object's in a trigger about another object).
 */
class OtjSweepTest {
    private val attackerPower = Value.Of(NumericProperty.Power, Objects.EventObject(EventRole.Object), Combine.Single)

    // CR 608.2c: "Whenever another Cat you control attacks, …, where X is its power": the attacking Cat's power (the ability never names this
    // creature)
    @Test
    fun whereXIsTheAttackersPower() = card(
        name = "Prideleader of the Mesa", cost = "{1}{G}", type = "Creature — Cat Warrior", pt = "1/1",
        text = """
            Whenever another Cat you control attacks, it gets +X/+X until end of turn, where X is its power.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Attack(and(another, sub("Cat"), ObjectFilter.ControlledBy(PlayerFilter.You)))),
            untilEndOfTurn(Objects.EventObject(EventRole.Object), Mechanic.ModifyPowerToughness(attackerPower, attackerPower))),
    )

    // CR 608.2c: "it deals damage equal to its power" in an attack trigger of another creature: the attacking creature deals it
    @Test
    fun attackingCreatureDealsTheDamage() = card(
        name = "Gunsmoke Creed", cost = "{2}{R}", type = "Enchantment",
        text = """
            Whenever a creature you control attacks, it deals damage equal to its power to any target.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Attack(creatureYouControl)),
            Step.Do(Effect.DealDamage(toAny, attackerPower, source = Objects.EventObject(EventRole.Object))), targets = listOf(anyTarget)),
    )

    // CR 608.2c: an ability that names this creature keeps "its" as this creature's ("this creature deals damage equal to its power")
    @Test
    fun itsStaysThisCreaturesWhenItNamesItself() = card(
        name = "Gulch Longbowman", cost = "{2}{G}", type = "Creature — Elf Archer", pt = "2/2",
        text = """
            Whenever a creature you control attacks, this creature deals damage equal to its power to any target.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Attack(creatureYouControl)),
            Step.Do(Effect.DealDamage(toAny, Value.Of(NumericProperty.Power, Objects.Self, Combine.Single), source = Objects.Self)),
            targets = listOf(anyTarget)),
    )
}
