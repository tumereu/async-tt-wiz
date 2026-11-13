package arcanum.parser.sets.gtc

import arcanum.api.DamageOccurrence
import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Gatecrash chunk 13: Boros (red and white). Counters that count up each upkeep (CR 122.1), the first attack each turn
 * and an additional combat phase (CR 500.8), X damage divided among any number of targets (CR 601.2d) with what it did to
 * them, a modal charm (CR 700.2), two separate "target creature" targets (CR 115.3), damage equal to a count, and a
 * creature that redirects the damage it is dealt.
 */
class GtcChunk13BorosTest {
    private fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList(), announced: List<String> = emptyList()) =
        Ability.Spell(Instructions(targets = targets, steps = steps.toList(), announced = announced))
    private val muster = CounterKind.Named("muster")
    private val soldier = creatureToken("Soldier", listOf(Color.Red, Color.White), 1, 1, listOf(Keyword.Haste))

    // CR 122.1: named counters; the tokens counted as it resolves (after this counter)
    @Test
    fun musterTheLegion() = card(
        name = "Muster the Legion",
        cost = "{3}{R}{W}",
        type = "Enchantment",
        text = "At the beginning of your upkeep, put a muster counter on this enchantment. Then create a 1/1 red and white Soldier creature token with haste for each muster counter on this enchantment.",
    ) parsesTo abilities(
        Ability.Triggered(yourUpkeep, steps(
            Step.Do(Effect.PutCounters(Objects.Self, muster, n(1))),
            Step.Do(Effect.CreateToken(soldier, Value.Of(NumericProperty.Counters(muster), Objects.Self, Combine.Single), Players.You)),
        )),
    )

    // CR 603.2: the first time it attacks each turn; CR 500.8: another combat phase after this one
    @Test
    fun aurelisTheBattleAngel() = card(
        name = "Aurelis, the Battle Angel",
        cost = "{2}{R}{R}{W}{W}",
        type = "Legendary Creature — Angel",
        pt = "3/4",
        text = """
            Flying, vigilance, haste
            Whenever Aurelis attacks for the first time each turn, untap all creatures you control. After this phase, there is an additional combat phase.
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.Vigilance, Keyword.Haste,
        Ability.Triggered(
            Trigger.On(listOf(EventPattern.Attack(ObjectFilter.Is(Objects.Self))), nthThisTurn = 1),
            steps(Step.Do(Effect.Untap(Objects.Each(creatureYouControl))), Step.Do(Effect.ExtraCombat())),
        ),
    )

    // CR 601.2d: divided as it is cast; the creatures and players dealt damage by this spell this turn are those it hurt
    @Test
    fun aurelissWrath() {
        val dealtBySelf = EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter())
        card(
            name = "Aurelis's Wrath",
            cost = "{X}{R}{W}",
            type = "Instant",
            text = "Aurelis's Wrath deals X damage divided as you choose among any number of targets. Tap each creature dealt damage this way. Players dealt damage this way can't cast noncreature spells this turn.",
        ) parsesTo abilities(
            spell(
                Step.Do(Effect.DealDamage(Recipients(target = "any"), Value.DividedShare, source = Objects.Self)),
                Step.Do(Effect.Tap(Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.ThisTurn(dealtBySelf, role = EventRole.Object)))))),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantCast(ObjectFilter.And(listOf(ObjectFilter.Not(creature), ObjectFilter.Category(ObjectCategory.Spell))))),
                    Affected.Players(Players.Each(PlayerFilter.ThisTurn(dealtBySelf))), Duration.EndOfTurn)),
                targets = listOf(anyTarget.copy(min = n(0), max = null, divided = Value.Announced("X"))),
                announced = listOf("X"),
            ),
        )
    }

    // CR 700.2: one mode
    @Test
    fun legionCharm() = card(
        name = "Legion Charm",
        cost = "{R}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Legion Charm deals 4 damage to target player or planeswalker.
            • Permanents you control gain indestructible until end of turn.
            • Target creature gains double strike until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(playerOrPlaneswalker), steps = listOf(Step.Do(Effect.DealDamage(Recipients(target = "player or planeswalker"), n(4), source = Objects.Self))))),
            Mode(steps(gains(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Permanent), you))), Keyword.Indestructible))),
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(gains(Objects.Target("creature"), Keyword.DoubleStrike)))),
        ), min = 1, max = 1, chooser = Players.You))),
    )

    @Test
    fun bastionCyclops() = card(
        name = "Bastion Cyclops",
        cost = "{3}{R}{W}",
        type = "Creature — Cyclops Soldier",
        pt = "3/3",
        text = """
            Whenever this creature attacks, it gets +3/+0 until end of turn.
            Whenever this creature blocks, it gets +0/+3 until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Triggered(attacksSelf, steps(pump(Objects.Self, 3, 0))),
        Ability.Triggered(Trigger.On(EventPattern.Block(ObjectFilter.Is(Objects.Self), ObjectFilter.Any)), steps(pump(Objects.EventObject(EventRole.Object), 0, 3))),
    )

    // CR 608.2h: counted as it resolves
    @Test
    fun forgeChampion() = card(
        name = "Forge Champion",
        cost = "{4}{R}{W}",
        type = "Creature — Elemental Soldier",
        pt = "4/4",
        text = """
            When this creature enters, it deals damage to any target equal to the number of creatures you control.
            {R}: This creature gets +1/+0 until end of turn.
            {W}: This creature gets +0/+1 until end of turn.
        """,
    ) parsesTo abilities(
        whenThisEnters(Step.Do(Effect.DealDamage(Recipients(target = "any"), Value.Count(Objects.Each(creatureYouControl)), source = Objects.Self)), targets = listOf(anyTarget)),
        Ability.Activated(mana("{R}"), steps(pump(Objects.Self, 1, 0)), rules = anyTime),
        Ability.Activated(mana("{W}"), steps(pump(Objects.Self, 0, 1)), rules = anyTime),
    )

    // CR 115.3: two targets, which may be the same creature
    @Test
    fun battleGlory() = card(
        name = "Battle Glory",
        cost = "{R}{W}",
        type = "Instant",
        text = """
            Target creature gets +3/+0 until end of turn.
            Target creature gets +0/+3 until end of turn.
        """,
    ) parsesTo abilities(
        spell(
            pump(Objects.Target("creature"), 3, 0),
            pump(Objects.Target("second creature"), 0, 3),
            targets = listOf(slot("creature", creature), slot("second creature", creature)),
        ),
    )

    // CR 513.1: at each end step
    @Test
    fun sparkLegionnaire() = card(
        name = "Spark Legionnaire",
        cost = "{1}{R}{R}{W}",
        type = "Creature — Elemental Soldier",
        pt = "6/1",
        text = """
            Trample, lifelink, haste
            At the beginning of the end step, sacrifice this creature.
        """,
    ) parsesTo abilities(
        Keyword.Trample, Keyword.Lifelink, Keyword.Haste,
        Ability.Triggered(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)), steps(Step.Do(Effect.Sacrifice(Objects.Self)))),
    )

    @Test
    fun sunforgeGuildmage() = card(
        name = "Sunforge Guildmage",
        cost = "{R}{W}",
        type = "Creature — Human Wizard",
        pt = "2/2",
        text = """
            {1}{R}{W}: Creatures you control get +1/+0 until end of turn.
            {2}{R}{W}: Create a 1/1 red and white Soldier creature token with haste.
        """,
    ) parsesTo abilities(
        Ability.Activated(mana("{1}{R}{W}"), steps(pump(Objects.Each(creatureYouControl), 1, 0)), rules = anyTime),
        Ability.Activated(mana("{2}{R}{W}"), steps(Step.Do(Effect.CreateToken(soldier, n(1), Players.You))), rules = anyTime),
    )

    @Test
    fun brightfirePaladin() = card(
        name = "Brightfire Paladin",
        cost = "{R}{W}",
        type = "Creature — Human Knight",
        pt = "2/2",
        text = """
            Vigilance
            {R}{W}: This creature gets +2/+0 until end of turn.
            {R}{W}: This creature gains first strike until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Ability.Activated(mana("{R}{W}"), steps(pump(Objects.Self, 2, 0)), rules = anyTime),
        Ability.Activated(mana("{R}{W}"), steps(gains(Objects.Self, Keyword.FirstStrike)), rules = anyTime),
    )

    @Test
    fun arrowsOfRetribution() = card(
        name = "Arrows of Retribution",
        cost = "{2}{R/W}",
        type = "Instant",
        text = "Arrows of Retribution deals 4 damage to target attacking or blocking creature.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), n(4), source = Objects.Self)),
            targets = listOf(slot("creature", ObjectFilter.And(listOf(ObjectFilter.Or(listOf(ObjectFilter.HasStatus(Status.Attacking), ObjectFilter.HasStatus(Status.Blocking))), creature)))),
        ),
    )

    // The damage it was dealt, from any source (CR 120)
    @Test
    fun legionReckoner() = card(
        name = "Legion Reckoner",
        cost = "{R/W}{R/W}{R/W}",
        type = "Creature — Minotaur Wizard",
        pt = "3/3",
        text = """
            Whenever this creature is dealt damage, it deals that much damage to any target.
            {R/W}: This creature gains first strike until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)), per = DamageOccurrence.Recipient)),
            Instructions(targets = listOf(anyTarget), steps = listOf(Step.Do(Effect.DealDamage(Recipients(target = "any"), Value.EventAmount, source = Objects.Self)))),
        ),
        Ability.Activated(mana("{R/W}"), steps(gains(Objects.Self, Keyword.FirstStrike)), rules = anyTime),
    )

    @Test
    fun shatteringStrike() = card(
        name = "Shattering Strike",
        cost = "{1}{R/W}",
        type = "Instant",
        text = "Exile target artifact.",
    ) parsesTo abilities(
        spell(Step.Do(Effect.Move(Objects.Target("artifact"), Destination(Zone.Exile))), targets = listOf(slot("artifact", ObjectFilter.Type(CardType.Artifact)))),
    )
}
