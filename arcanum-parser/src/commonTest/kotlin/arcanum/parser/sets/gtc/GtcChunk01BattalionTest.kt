package arcanum.parser.sets.gtc

import arcanum.api.Ability
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Gatecrash chunk 01: battalion. Battalion is an ability word (CR 207.2c) on "Whenever this creature and at least two
 * other creatures attack": it triggers when this creature is declared as an attacker together with at least two other
 * creatures (CR 508.1a, 508.3a), and how many still attack as it resolves doesn't matter. With it: "gains" and "gets"
 * until end of turn, "Untap it", any-target damage, "can't block this turn", and one effect that gives several rules
 * until end of turn and this turn.
 */
class GtcChunk01BattalionTest {
    private val battalion = Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self), withOthers = 2))

    private fun battalion(vararg steps: Step, targets: List<TargetSlot> = emptyList()) = Ability.Triggered(
        trigger = battalion,
        instructions = Instructions(targets = targets, steps = steps.toList()),
        abilityWord = "Battalion",
    )

    // CR 508.1a: the creatures are declared as attackers together
    @Test
    fun shieldwallRecruit() = card(
        name = "Shieldwall Recruit",
        cost = "{W}",
        type = "Creature — Human Soldier",
        pt = "1/1",
        text = "Battalion — Whenever this creature and at least two other creatures attack, this creature gets +2/+2 until end of turn.",
    ) parsesTo abilities(
        battalion(pump(Objects.Self, 2, 2)),
    )

    @Test
    fun skyjekDaredevil() = card(
        name = "Skyjek Daredevil",
        cost = "{1}{W}",
        type = "Creature — Human Knight",
        pt = "3/1",
        text = "Battalion — Whenever this creature and at least two other creatures attack, this creature gains flying until end of turn.",
    ) parsesTo abilities(
        battalion(gains(Objects.Self, Keyword.Flying)),
    )

    @Test
    fun battlefieldMender() = card(
        name = "Battlefield Mender",
        cost = "{2}{W}",
        type = "Creature — Human Cleric",
        pt = "3/3",
        text = """
            Battalion — Whenever this creature and at least two other creatures attack, creatures you control gain indestructible until end of turn.
            Sacrifice this creature: Counter target spell with {X} in its mana cost unless its controller pays {3}.
        """,
    ) parsesTo abilities(
        battalion(gains(Objects.Each(creatureYouControl), Keyword.Indestructible)),
        Ability.Activated(
            cost = costOf(arcanum.api.CostPart.Sacrifice(arcanum.api.CostObjects.Self)),
            instructions = Instructions(
                targets = listOf(slot("spell", ObjectFilter.And(listOf(ObjectFilter.Category(arcanum.api.ObjectCategory.Spell), ObjectFilter.XInManaCost)))),
                steps = listOf(
                    Step.Pay(mana("{3}"), payer = Players.ControllerOf(Objects.Target("spell")), bind = "paid"),
                    Step.If(arcanum.api.Condition.Not(arcanum.api.Condition.Did("paid")), then = listOf(Step.Do(Effect.Counter(Objects.Target("spell"))))),
                ),
            ),
            rules = anyTime,
        ),
    )

    // "Untap it": it is this creature, the attacker
    @Test
    fun squadronBreaker() = card(
        name = "Squadron Breaker",
        cost = "{4}{W}",
        type = "Creature — Human Soldier",
        pt = "3/5",
        text = "Battalion — Whenever this creature and at least two other creatures attack, this creature gets +1/+1 until end of turn. Untap it.",
    ) parsesTo abilities(
        battalion(pump(Objects.Self, 1, 1), Step.Do(Effect.Untap(Objects.Self))),
    )

    @Test
    fun sapperBrigade() = card(
        name = "Sapper Brigade",
        cost = "{1}{R}",
        type = "Creature — Human Soldier",
        pt = "1/2",
        text = "Battalion — Whenever this creature and at least two other creatures attack, this creature deals 1 damage to any target.",
    ) parsesTo abilities(
        battalion(
            Step.Do(Effect.DealDamage(Recipients(target = "any"), n(1), source = Objects.Self)),
            targets = listOf(anyTarget),
        ),
    )

    @Test
    fun fistfireSkirmisher() = card(
        name = "Fistfire Skirmisher",
        cost = "{1}{R}",
        type = "Creature — Human Soldier",
        pt = "2/1",
        text = "Battalion — Whenever this creature and at least two other creatures attack, target creature can't block this turn.",
    ) parsesTo abilities(
        battalion(
            untilEndOfTurn(Objects.Target("creature"), Mechanic.CantBlock(attackers = ObjectFilter.Any)),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // One continuous effect with all three rules, ending with the turn ("until end of turn" and "this turn", CR 611.2a)
    @Test
    fun rallyGoblin() = card(
        name = "Rally Goblin",
        cost = "{R}",
        type = "Creature — Goblin Soldier",
        pt = "1/1",
        text = """
            Haste
            Battalion — Whenever this creature and at least two other creatures attack, creatures you control gain first strike and trample until end of turn and can't be blocked by creature tokens this turn.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        battalion(untilEndOfTurn(Objects.Each(creatureYouControl),
            Mechanic.GainAbilities(listOf(Keyword.FirstStrike, Keyword.Trample)),
            Mechanic.CantBeBlockedBy(ObjectFilter.And(listOf(creature, token))),
        )),
    )

    @Test
    fun warmindFootman() = card(
        name = "Warmind Footman",
        cost = "{2}{R}",
        type = "Creature — Elemental Soldier",
        pt = "2/3",
        text = "Battalion — Whenever this creature and at least two other creatures attack, this creature gets +2/+0 until end of turn.",
    ) parsesTo abilities(
        battalion(pump(Objects.Self, 2, 0)),
    )

    @Test
    fun pyreheartAvenger() = card(
        name = "Pyreheart Avenger",
        cost = "{2}{R}{W}",
        type = "Creature — Angel",
        pt = "3/3",
        text = """
            Flying
            Battalion — Whenever this creature and at least two other creatures attack, this creature deals 3 damage to any target and you gain 3 life.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        battalion(
            Step.Do(Effect.DealDamage(Recipients(target = "any"), n(3), source = Objects.Self)),
            Step.Do(Effect.GainLife(Players.You, n(3))),
            targets = listOf(anyTarget),
        ),
    )

    @Test
    fun hornlineVeteran() = card(
        name = "Hornline Veteran",
        cost = "{2}{R}{W}",
        type = "Creature — Minotaur Soldier",
        pt = "3/1",
        text = "Battalion — Whenever this creature and at least two other creatures attack, this creature gains double strike until end of turn. (It deals both first-strike and regular combat damage.)",
    ) parsesTo abilities(
        battalion(gains(Objects.Self, Keyword.DoubleStrike)),
    )

    @Test
    fun wojekPikemen() = card(
        name = "Wojek Pikemen",
        cost = "{R}{W}",
        type = "Creature — Human Soldier",
        pt = "3/2",
        text = "Battalion — Whenever this creature and at least two other creatures attack, this creature gains first strike until end of turn.",
    ) parsesTo abilities(
        battalion(gains(Objects.Self, Keyword.FirstStrike)),
    )
}
