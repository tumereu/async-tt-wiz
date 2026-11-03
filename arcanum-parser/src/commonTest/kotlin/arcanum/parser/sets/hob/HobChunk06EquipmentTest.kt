package arcanum.parser.sets.hob

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Hobbit chunk 06: Equipment and Dwarves. Tokens created and equipped at once (CR 701.3); a named Equipment token with
 * equip (CR 111.4) and a reflexive trigger after creating it (CR 603.12); equip only onto a Wizard (CR 702.6a); triggers of the
 * equipped creature triggering an additional time (CR 603.2d); equip abilities targeting this creature costing less (CR 601.2f,
 * 602.2b); hone counters (CR 122.1j); attaching any number of target Equipment, with a trigger when one became attached; damage
 * then destroying a Dragon dealt damage this way; a chosen creature type; entering tapped unless you control an Equipment.
 */
class HobChunk06EquipmentTest {
    // CR 701.3a: attached to the token as it resolves
    @Test
    fun dwarvenStabbingSword() = card(
        name = "Dwarven Stabbing-Sword",
        cost = "{3}{W}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, create a 2/2 red Dwarf creature token, then attach this Equipment to it.
            Equipped creature gets +1/+2.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(dwarf, bind = "token"), Step.Do(Effect.Attach(Objects.Self, toObjects(Objects.Bound("token"))))),
        static(pt(1, 2), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 111.4: a named token with its abilities
    @Test
    fun copperFellsSmith() = card(
        name = "Copper Fells Smith",
        cost = "{1}{W}",
        type = "Creature — Dwarf Artificer",
        text = """
            Double strike
            When this creature enters, create a colorless Equipment artifact token named Axe with "Equipped creature gets +1/+0" and equip {2}.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        triggered(entersSelf, create(axe)),
    )

    // CR 603.2d; 702.6a: "Equip Wizard" attaches only to a Wizard
    @Test
    fun magesCudgel() = card(
        name = "Mage's Cudgel",
        cost = "{1}{U}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature has prowess. (Whenever its controller casts a noncreature spell, that creature gets +1/+1 until end of turn.)
            If a triggered ability of equipped creature triggers, that ability triggers an additional time.
            Equip Wizard {1}
            Equip {3}
        """,
    ) parsesTo abilities(
        static(has(Keyword.Prowess), affects = Objects.AttachedTo(Objects.Self)),
        static(Mechanic.TriggersAdditionalTime(null, ObjectFilter.Is(Objects.AttachedTo(Objects.Self)))),
        Keyword.Equip(manaCost("{1}"), onto = sub("Wizard")),
        Keyword.Equip(manaCost("{3}")),
    )

    // CR 701.21a: the opponent chooses what they sacrifice
    @Test
    fun roughTwistedKnife() = card(
        name = "Rough Twisted Knife",
        cost = "{2}{B}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, target opponent sacrifices a creature of their choice.
            Equipped creature gets +2/+1.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))), n(1), n(1)), bind = "sacrificed",
                chooser = Players.Target("opponent")),
            Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            targets = listOf(targetOpponent)),
        static(pt(2, 1), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 603.12: "When you do" after creating the token; 611.2c: the attacking equipped creatures fixed as it resolves
    @Test
    fun darviSteelboot() = card(
        name = "Darvi Steelboot",
        cost = "{2}{R}",
        type = "Legendary Creature — Dwarf Warrior",
        text = """
            When Darvi enters, create a colorless Equipment artifact token named Axe with "Equipped creature gets +1/+0" and equip {2}. When you do, attach it to target creature you control.
            Whenever Darvi attacks, each equipped attacking creature gains double strike until end of turn.
        """,
        pt = "1/4",
    ) parsesTo abilities(
        triggered(entersSelf, create(axe, bind = "token"),
            Step.Do(Effect.ReflexiveTrigger(Instructions(targets = listOf(slot("creature", creatureYouControl)),
                steps = listOf(Step.Do(Effect.Attach(Objects.Bound("token"), toObjects(target("creature"))))))))),
        triggered(attacksSelf, gains(Objects.Each(and(creature, attacking, ObjectFilter.Attached(attachments = equipment))), Keyword.DoubleStrike)),
    )

    // CR 601.2f, 602.2b: the equip ability's target is chosen before its cost is determined
    @Test
    fun dwarvenBasher() = card(
        name = "Dwarven Basher",
        cost = "{R}",
        type = "Creature — Dwarf Warrior",
        text = "Equip abilities you activate that target this creature cost {2} less to activate.",
        pt = "2/1",
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(ObjectFilter.AbilityOf(keyword = Keyword.Equip.Kind), you, ObjectFilter.Targets(objects = Objects.Self)),
            CostChange.Less(ManaCost.parse("{2}")!!, n(1)))),
    )

    // CR 701.3
    @Test
    fun copperFellsStalwart() = card(
        name = "Copper Fells Stalwart",
        cost = "{4}{R}",
        type = "Creature — Dwarf Warrior",
        text = """
            Reach, trample
            When this creature enters, attach target Equipment you control to up to one target creature you control.
        """,
        pt = "4/5",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        triggered(entersSelf, Step.Do(Effect.Attach(target("Equipment"), toObjects(target("creature")))),
            targets = listOf(slot("Equipment", and(equipment, you)), slot("creature", creatureYouControl, min = 0))),
    )

    // CR 118.12
    @Test
    fun tatteredShortPike() = card(
        name = "Tattered Short Pike",
        cost = "{1}{R}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, you may discard a card. If you do, draw two cards.
            Equipped creature gets +2/+0.
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.Pay(cost(discardACard), payer = Players.You, bind = "paid"), Step.If(Condition.Did("paid"), then = listOf(draw(2)))),
        static(pt(2, 0), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{3}")),
    )

    /** "When one or more Equipment become attached to that creature this way": a reflexive trigger when at least one of the targets
     * is attached to it (CR 603.12); the creature deals the damage (CR 120.1). */
    // CR 603.12
    @Test
    fun tharnHillKing() = card(
        name = "Tharn, Hill-King",
        cost = "{3}{R}",
        type = "Legendary Creature — Dwarf Noble",
        text = """
            Trample
            When Tharn enters, attach any number of target Equipment you control to target creature you control. When one or more Equipment become attached to that creature this way, that creature deals damage equal to its power to up to one target creature.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf,
            Step.Do(Effect.Attach(target("Equipment"), toObjects(target("creature")))),
            Step.If(Condition.Exists(and(ObjectFilter.Is(target("Equipment")), ObjectFilter.Attached(to = target("creature"))), atLeast = 1), then = listOf(
                Step.Do(Effect.ReflexiveTrigger(Instructions(targets = listOf(slot("other creature", creature, min = 0)),
                    steps = listOf(damage(power(target("creature")), toObjects(target("other creature")), source = target("creature")))))))),
            targets = listOf(slot("Equipment", and(equipment, you), min = 0, max = null), slot("creature", creatureYouControl))),
    )

    // CR 122.1j: a hone counter on an Equipment gives +1/+0 to the equipped creature
    @Test
    fun dwerinArmsmaster() = card(
        name = "Dwerin, Armsmaster",
        cost = "{1}{R/W}",
        type = "Legendary Creature — Dwarf Warrior",
        text = """
            First strike
            Whenever Dwerin enters or attacks, put a hone counter on each Equipment you control. (Each hone counter on an Equipment grants +1/+0 to equipped creature.)
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.FirstStrike,
        triggered(entersOrAttacksSelf, counters(Objects.Each(and(equipment, you)), kind = CounterKind.Hone)),
    )

    // CR 120.3: "dealt damage this way": the damage actually dealt
    @Test
    fun theDarkBolt() = card(
        name = "The Dark Bolt",
        cost = "{3}",
        type = "Legendary Artifact — Equipment",
        text = """
            Flash
            When The Dark Bolt enters, it deals 1 damage to any target. If a Dragon is dealt damage this way, destroy it.
            Equipped creature gets +1/+1 and has reach.
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf,
            Step.Do(Effect.DealDamage(toAny, n(1), Objects.Self, bind = "damage")),
            Step.If(Condition.And(listOf(Condition.Compare(Value.Bound("damage"), Comparison.Greater, n(0)), Condition.ObjectMatches(target("any"), sub("Dragon")))),
                then = listOf(destroy(target("any")))),
            targets = listOf(anyTarget)),
        static(pt(1, 1), has(Keyword.Reach), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{1}")),
    )

    // CR 701.3
    @Test
    fun dwarvenPick() = card(
        name = "Dwarven Pick",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, attach it to target Dwarf you control.
            Equipped creature gets +2/+2 and has ward {1}. (Whenever equipped creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {1}.)
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, Step.Do(Effect.Attach(Objects.Self, toObjects(target("Dwarf")))), targets = listOf(slot("Dwarf", and(sub("Dwarf"), you)))),
        static(pt(2, 2), has(Keyword.Ward(manaCost("{1}"))), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{3}")),
    )

    // CR 205.3m: a creature type chosen as it resolves
    @Test
    fun grimcleaveGoblinSplitter() = card(
        name = "Grimcleave, Goblin-splitter",
        cost = "{3}",
        type = "Legendary Artifact — Equipment",
        text = """
            Equipped creature gets +2/+2 and has trample.
            Whenever equipped creature deals combat damage to a player, choose a creature type. Create a Treasure token for each creature you control of that type.
            Equip {3}
        """,
    ) parsesTo abilities(
        static(pt(2, 2), has(Keyword.Trample), affects = Objects.AttachedTo(Objects.Self)),
        triggered(arcanum.api.Trigger.On(EventPattern.Damage(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            Step.Choose(Choice.CreatureType, bind = "type", chooser = Players.You),
            createTreasure(count(and(creature, you, ObjectFilter.Sub(SubtypeRef.Chosen("type")))))),
        Keyword.Equip(manaCost("{3}")),
    )

    // CR 122.1j: hone counters on it for each creature the target opponent controls
    @Test
    fun prickTobbosBlade() = card(
        name = "Prick, Tobbo's Blade",
        cost = "{2}",
        type = "Legendary Artifact — Equipment",
        text = """
            Flash
            When Prick enters, put a hone counter on Prick for each creature target opponent controls. Attach Prick to up to one target creature you control. (Each hone counter on an Equipment grants +1/+0 to equipped creature.)
            Equip {3}
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf,
            counters(Objects.Self, count(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent"))))), kind = CounterKind.Hone),
            Step.Do(Effect.Attach(Objects.Self, toObjects(target("creature")))),
            targets = listOf(targetOpponent, slot("creature", creatureYouControl, min = 0))),
        Keyword.Equip(manaCost("{3}")),
    )

    @Test
    fun muchUsedLadle() = card(
        name = "Much-Used Ladle",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, you gain 2 life.
            Equipped creature gets +1/+1.
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, gainLife(2)),
        static(pt(1, 1), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{1}")),
    )

    // CR 701.3: "you may attach an Equipment" is a choice of zero or one
    @Test
    fun pledgeToKhazrund() = card(
        name = "Pledge to Khazrund",
        cost = "{1}{W}",
        type = "Instant",
        text = "Untap target creature you control. It gets +2/+2 until end of turn. If it's a Dwarf, you may attach an Equipment you control to it.",
    ) parsesTo abilities(
        spell(untapIt(target("creature")), pump(target("creature"), 2, 2),
            Step.If(Condition.ObjectMatches(target("creature"), sub("Dwarf")), then = listOf(
                Step.Choose(Choice.Objects(and(equipment, you), n(0), n(1)), bind = "equipment", chooser = Players.You),
                Step.Do(Effect.Attach(Objects.Bound("equipment"), toObjects(target("creature")))))),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 614.1d, 305.6; 602.2b: the activation cost reduced for each Equipment
    @Test
    fun theSolitaryPeak() = card(
        name = "The Solitary Peak",
        cost = null,
        type = "Land — Mountain",
        text = """
            ({T}: Add {R}.)
            This land enters tapped unless you control an Equipment.
            {4}{R}, {T}: Create a 2/2 red Dwarf creature token. This ability costs {1} less to activate for each Equipment you control. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
            condition = Condition.Not(Condition.Exists(and(equipment, you), atLeast = 1))),
        activated(cost(mana("{4}{R}"), tap), create(dwarf),
            rules = asSorcery.copy(costChange = CostChange.Less(ManaCost.parse("{1}")!!, count(and(equipment, you))))),
    )
}
