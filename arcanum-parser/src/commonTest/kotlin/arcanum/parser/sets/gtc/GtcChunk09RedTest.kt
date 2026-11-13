package arcanum.parser.sets.gtc

import arcanum.api.DamageOccurrence
import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Gatecrash chunk 09: red. Gaining control until end of turn (CR 613.1b), X damage from a sacrificed creature, a Gate
 * tapped as a cost, attacking or blocking only with others (CR 506.5), named counters (CR 122.1), winning the game
 * (CR 104.2b), creatures that must attack (CR 508.1d) and block (CR 509.1c), damage to a creature and those with its name
 * (CR 201.2), damage equal to a count, damage that can't be prevented (CR 615.12), and sacrifices of a target player's
 * choice.
 */
class GtcChunk09RedTest {
    private fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList()) = Ability.Spell(Instructions(targets = targets, steps = steps.toList()))
    private val enchanted = Objects.AttachedTo(Objects.Self)
    private val blaze = CounterKind.Named("blaze")

    // CR 613.1b: control until end of turn; untapped; haste
    @Test
    fun actOfBetrayal() = card(
        name = "Act of Betrayal",
        cost = "{2}{R}",
        type = "Sorcery",
        text = "Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn. (It can attack and {T} this turn.)",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(Objects.Target("creature"), Mechanic.ControlledBy(Players.You)),
            Step.Do(Effect.Untap(Objects.Target("creature"))),
            gains(Objects.Target("creature"), Keyword.Haste),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 107.3a, 602.2b: X announced as it is activated
    @Test
    fun emberElemental() = card(
        name = "Ember Elemental",
        cost = "{3}{R}",
        type = "Creature — Elemental",
        pt = "2/2",
        text = "{X}{R}, {T}, Sacrifice this creature: It deals X damage to any target.",
    ) parsesTo abilities(
        Ability.Activated(
            costOf(manaPart("{X}{R}"), CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Self)),
            Instructions(targets = listOf(anyTarget), steps = listOf(Step.Do(Effect.DealDamage(Recipients(target = "any"), Value.Announced("X"), source = Objects.Self))),
                announced = listOf("X")),
            rules = anyTime,
        ),
    )

    @Test
    fun sparkingPerimeter() = card(
        name = "Sparking Perimeter",
        cost = "{1}{R}",
        type = "Enchantment",
        text = "Tap an untapped Gate you control: This enchantment deals 1 damage to each opponent.",
    ) parsesTo abilities(
        Ability.Activated(
            costOf(CostPart.TapObjects(CostObjects.Choose(ObjectFilter.Sub(Subtype("Gate")), count = n(1)))),
            steps(Step.Do(Effect.DealDamage(Recipients(players = Players.Each(PlayerFilter.Opponent)), n(1), source = Objects.Self))),
            rules = anyTime,
        ),
    )

    // CR 506.5: it attacks or blocks only together with another creature
    @Test
    fun cinderBeast() = card(
        name = "Cinder Beast",
        cost = "{2}{R}",
        type = "Creature — Beast",
        pt = "3/4",
        text = "This creature can't attack or block alone.",
    ) parsesTo abilities(Ability.Static(listOf(Mechanic.CantAttackAlone, Mechanic.CantBlockAlone), affects = Affected.Objects(Objects.Self)))

    // CR 122.1: blaze counters; one trigger for each combat damage event of a creature you control
    @Test
    fun sixAlarmBlaze() = card(
        name = "Six-Alarm Blaze",
        cost = "{1}{R}{R}",
        type = "Enchantment",
        text = """
            Whenever a creature you control deals combat damage, put a blaze counter on this enchantment.
            Remove five blaze counters from this enchantment: It deals 5 damage to any target.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.Damage(creatureYouControl, RecipientFilter(), combat = true, per = DamageOccurrence.Source)),
            steps(Step.Do(Effect.PutCounters(Objects.Self, blaze, n(1)))),
        ),
        Ability.Activated(
            costOf(CostPart.RemoveCounters(blaze, n(5), CostObjects.Self)),
            Instructions(targets = listOf(anyTarget), steps = listOf(Step.Do(Effect.DealDamage(Recipients(target = "any"), n(5), source = Objects.Self)))),
            rules = anyTime,
        ),
    )

    @Test
    fun furnaceStreetDenizen() = card(
        name = "Furnace Street Denizen",
        cost = "{R}",
        type = "Creature — Goblin Warrior",
        pt = "1/1",
        text = "Whenever another red creature you control enters, this creature gets +1/+0 until end of turn.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), ObjectFilter.OfColor(Color.Red), creature, you)),
                to = Zone.Battlefield)),
            steps(pump(Objects.Self, 1, 0)),
        ),
    )

    @Test
    fun defiantResistance() = card(
        name = "Defiant Resistance",
        cost = "{R}",
        type = "Instant",
        text = "Target blocking creature gets +3/+0 and gains first strike until end of turn.",
    ) parsesTo abilities(
        spell(
            pump(Objects.Target("creature"), 3, 0, Mechanic.GainAbilities(listOf(Keyword.FirstStrike))),
            targets = listOf(slot("creature", ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Blocking), creature)))),
        ),
    )

    // CR 613.1b: control for good; CR 603.4, 104.2b: checked as it triggers and resolves
    @Test
    fun hellwingTyrant() = card(
        name = "Hellwing Tyrant",
        cost = "{4}{R}{R}",
        type = "Creature — Dragon",
        pt = "6/5",
        text = """
            Flying, trample
            Whenever this creature deals combat damage to a player, gain control of all artifacts that player controls.
            At the beginning of your upkeep, if you control twenty or more artifacts, you win the game.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Trample,
        Ability.Triggered(combatDamageToPlayer, steps(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)),
            Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Artifact), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer)))))),
            Duration.Indefinitely)))),
        Ability.Triggered(yourUpkeep, steps(Step.Do(Effect.WinGame(Players.You))),
            interveningIf = arcanum.api.Condition.Exists(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Artifact), you)), atLeast = 20)),
    )

    // CR 508.1d: they attack each combat if able
    @Test
    fun hellrouserGoblin() = card(
        name = "Hellrouser Goblin",
        cost = "{2}{R}",
        type = "Creature — Goblin Berserker",
        pt = "2/2",
        text = "Creatures you control have haste and attack each combat if able.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Haste)), Mechanic.MustAttack()), affects = Affected.Objects(Objects.Each(creatureYouControl))),
    )

    // CR 201.2: the same name as the target, as the spell resolves
    @Test
    fun seekingLightning() = card(
        name = "Seeking Lightning",
        cost = "{2}{R}{R}",
        type = "Instant",
        text = "Seeking Lightning deals 4 damage to target creature and each other creature with the same name as that creature.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.DealDamage(Recipients(objects = Objects.Union(listOf(Objects.Target("creature"), Objects.Each(ObjectFilter.And(listOf(
                ObjectFilter.Not(ObjectFilter.Is(Objects.Target("creature"))), creature, ObjectFilter.SameNameAs(Objects.Target("creature")))))))), n(4), source = Objects.Self)),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun wildSkills() = card(
        name = "Wild Skills",
        cost = "{1}{R}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +3/+0 and has menace. (It can't be blocked except by two or more creatures.)
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(n(3), n(0)), Mechanic.GainAbilities(listOf(Keyword.Menace))), affects = Affected.Objects(enchanted)),
    )

    // CR 509.1c: it must block if able; the other creatures of the target's controller can't
    @Test
    fun markedForRuin() = card(
        name = "Marked for Ruin",
        cost = "{3}{R}",
        type = "Sorcery",
        text = "Target creature an opponent controls blocks this turn if able. Untap that creature. Other creatures that player controls can't block this turn.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(Objects.Target("creature"), Mechanic.MustBlock(ObjectFilter.Any)),
            Step.Do(Effect.Untap(Objects.Target("creature"))),
            untilEndOfTurn(Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.ControllerOf(Objects.Target("creature")))),
                ObjectFilter.Not(ObjectFilter.Is(Objects.Target("creature")))))), Mechanic.CantBlock(ObjectFilter.Any)),
            targets = listOf(slot("creature", creatureOpponentControls)),
        ),
    )

    // CR 608.2h: counted as it resolves
    @Test
    fun massRaid() = card(
        name = "Mass Raid",
        cost = "{1}{R}{R}",
        type = "Instant",
        text = "Mass Raid deals damage to any target equal to the number of creatures you control.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.DealDamage(Recipients(target = "any"), Value.Count(Objects.Each(creatureYouControl)), source = Objects.Self)),
            targets = listOf(anyTarget),
        ),
    )

    @Test
    fun blazingPrimordial() = card(
        name = "Blazing Primordial",
        cost = "{5}{R}{R}",
        type = "Creature — Avatar",
        pt = "6/4",
        text = """
            Haste
            When this creature enters, for each opponent, gain control of up to one target creature that player controls until end of turn. Untap those creatures. They gain haste until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        whenThisEnters(
            untilEndOfTurn(Objects.Target("creature"), Mechanic.ControlledBy(Players.You)),
            Step.Do(Effect.Untap(Objects.Target("creature"))),
            gains(Objects.Target("creature"), Keyword.Haste),
            targets = listOf(TargetSlot("creature", TargetKind.Objects(ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.SlotPlayer))))),
                min = n(0), max = n(1), perPlayer = PlayerFilter.Opponent)),
        ),
    )

    @Test
    fun streetMugging() = card(
        name = "Street Mugging",
        cost = "{R}",
        type = "Sorcery",
        text = "Street Mugging deals 2 damage to target creature. That creature can't block this turn.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), n(2), source = Objects.Self)),
            untilEndOfTurn(Objects.Target("creature"), Mechanic.CantBlock(ObjectFilter.Any)),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 615.12: damage that can't be prevented; a player or planeswalker target (CR 115.4)
    @Test
    fun skullCrusherStrike() = card(
        name = "Skull Crusher Strike",
        cost = "{1}{R}",
        type = "Instant",
        text = "Players can't gain life this turn. Damage can't be prevented this turn. Skull Crusher Strike deals 3 damage to target player or planeswalker.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantGainLife), Affected.Players(Players.Each(PlayerFilter.Any)), Duration.EndOfTurn)),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.DamageCantBePrevented(EventPattern.Damage(ObjectFilter.Any, RecipientFilter()))), affects = null, duration = Duration.EndOfTurn)),
            Step.Do(Effect.DealDamage(Recipients(target = "player or planeswalker"), n(3), source = Objects.Self)),
            targets = listOf(playerOrPlaneswalker),
        ),
    )

    // CR 701.21a: one sacrifice of both, each chosen by that player
    @Test
    fun structuralRuin() = card(
        name = "Structural Ruin",
        cost = "{5}{R}",
        type = "Sorcery",
        text = "Target player sacrifices an artifact and a land of their choice. Structural Ruin deals 2 damage to that player.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Artifact), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player"))))), n(1), n(1)),
                bind = "sacrificed", chooser = Players.Target("player")),
            Step.Choose(Choice.Objects(ObjectFilter.And(listOf(land, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player"))))), n(1), n(1)),
                bind = "sacrificed2", chooser = Players.Target("player")),
            Step.Do(Effect.Sacrifice(Objects.Union(listOf(Objects.Bound("sacrificed"), Objects.Bound("sacrificed2"))))),
            Step.Do(Effect.DealDamage(Recipients(players = Players.Target("player")), n(2), source = Objects.Self)),
            targets = listOf(targetPlayer),
        ),
    )

    @Test
    fun tinStreetBazaar() = card(
        name = "Tin Street Bazaar",
        cost = "{4}{R}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            Enchanted land has "{T}, Discard a card: Draw a card."
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(land)),
        Ability.Static(
            listOf(Mechanic.GainAbilities(listOf(Ability.Activated(
                costOf(CostPart.TapSelf, CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), count = n(1)))),
                steps(Step.Do(Effect.Draw(Players.You, n(1)))),
                rules = anyTime,
            )))),
            affects = Affected.Objects(enchanted),
        ),
    )

    @Test
    fun thunderfistColossus() = card(
        name = "Thunderfist Colossus",
        cost = "{4}{R}",
        type = "Creature — Giant Soldier",
        pt = "4/4",
        text = "{W}: This creature gains vigilance until end of turn.",
    ) parsesTo abilities(Ability.Activated(mana("{W}"), steps(gains(Objects.Self, Keyword.Vigilance)), rules = anyTime))
}
