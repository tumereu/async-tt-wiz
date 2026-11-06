package arcanum.parser.sets.ltr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Lord of the Rings chunk 07: red cards, first part. Damage to each creature blocking it (CR 509.1h), one creature dealing damage
 * to another of the same player, damage to each of up to two targets, a spell that can't be copied copying spells (CR 707.10), an
 * additional combat phase (CR 500.8), the greatest power on the battlefield, excess noncombat damage (CR 120.4a), damage that can't be
 * prevented (CR 615.12), a reflexive trigger after amassing using the amassed Army (CR 603.12, 701.47c), damage to a dying creature's
 * controller, menace while equipping a legendary creature, goad (CR 701.15) and historic spells (CR 700.6).
 */
class LtrChunk07RedTest {
    // CR 509.1h: each creature blocking it
    @Test
    fun battleWornGoblin() = card(
        name = "Battle-Worn Goblin",
        cost = "{1}{R}",
        type = "Creature — Goblin Warrior",
        text = "Whenever this creature becomes blocked, it deals 1 damage to each creature blocking it.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(on(EventPattern.BecomesBlocked(ObjectFilter.Is(Objects.Self), ObjectFilter.Any)),
            damage(1, toObjects(Objects.Each(and(creature, ObjectFilter.Blocking(Objects.Self)))))),
    )

    // CR 714.2b
    @Test
    fun bookOfTheDeepRecords() = card(
        name = "Book of the Deep Records",
        cost = "{2}{R}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Amass Orcs 1. (Put a +1/+1 counter on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)
            II — Amass Orcs 2.
            III — Creatures you control get +1/+0 and gain menace until end of turn.
        """,
    ) parsesTo abilities(
        chapter(1, amassOrcs(1)),
        chapter(2, amassOrcs(2)),
        chapter(3, pump(Objects.Each(creatureYouControl), 1, 0, has(Keyword.Menace))),
    )

    // CR 120.3: the first target deals damage equal to its power to the second, both that player's
    @Test
    fun sunderingOfTheCompany() = card(
        name = "Sundering of the Company",
        cost = "{1}{R}",
        type = "Sorcery",
        text = "Target creature an opponent controls deals damage equal to its power to another target creature that player controls. The Ring tempts you.",
    ) parsesTo abilities(
        spell(damage(power(target("creature")), toObjects(target("second creature")), source = target("creature")), ringTempts,
            targets = listOf(slot("creature", creatureOpponentControls),
                TargetSlot("second creature", TargetKind.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.ControllerOf(target("creature")))))),
                    min = n(1), max = n(1), distinctFrom = listOf("creature")))),
    )

    // CR 700.2: modes with their own targets
    @Test
    fun thrownIntoTheFlame() = card(
        name = "Thrown into the Flame",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Thrown into the Flame deals 1 damage to each of up to two target creatures.
            • Exile target artifact.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(damage(1, toObjects(target("creatures"))), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
            mode(move(target("artifact"), Zone.Exile), targets = listOf(slot("artifact", artifact)))))),
    )

    // CR 707.10: copies of target spells; this one can't be copied
    @Test
    fun showOfPower() = card(
        name = "Show of Power",
        cost = "{1}{R}{R}",
        type = "Instant",
        text = """
            This spell can't be copied.
            Copy any number of target instant and/or sorcery spells. You may choose new targets for the copies.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCopied),
        spell(doing(Effect.CopySpell(target("spells"), Players.You, newTargets = true)),
            targets = listOf(slot("spells", and(or(instant, sorcery), spellFilter), min = 0, max = null))),
    )

    // CR 500.8: an additional combat phase; once each turn
    @Test
    fun eothainMarshalOfThePlains() = card(
        name = "Eothain, Marshal of the Plains",
        cost = "{2}{R}{R}",
        type = "Legendary Creature — Human Knight",
        text = """
            Haste
            Whenever one or more other attacking legendary creatures you control die, untap all creatures you control. After this phase, there is an additional combat phase. This ability triggers only once each turn.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(and(another, attacking, legendary, creature, you), from = Zone.Battlefield, to = Zone.Graveyard)), oneOrMore = true),
            untapIt(Objects.Each(creatureYouControl)), doing(Effect.ExtraCombat()), perTurn = 1),
    )

    // CR 603.4: the greatest power among creatures on the battlefield
    @Test
    fun elfhelmOfTheRiddermark() = card(
        name = "Elfhelm of the Riddermark",
        cost = "{4}{R}",
        type = "Legendary Creature — Human Knight",
        text = """
            Haste
            Whenever Elfhelm attacks, if you control a creature with the greatest power among creatures on the battlefield, create a 1/1 white Human Soldier creature token.
        """,
        pt = "5/4",
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(attacksSelf, create(humanSoldier), interveningIf = Condition.Exists(and(creature, you,
            ObjectFilter.Power(Comparison.AtLeast, Value.Of(NumericProperty.Power, Objects.Each(creature), arcanum.api.Combine.Greatest))), atLeast = 1)),
    )

    @Test
    fun ironHillsFlamesmith() = card(
        name = "Iron Hills Flamesmith",
        cost = "{1}{R}",
        type = "Creature — Dwarf Artificer",
        text = "Whenever you cast an instant or sorcery spell, this creature deals 1 damage to each opponent.",
        pt = "2/1",
    ) parsesTo abilities(
        triggered(youCast(or(instant, sorcery)), damage(1, eachOpponent)),
    )

    @Test
    fun grimboldLordOfWestfold() = card(
        name = "Grimbold, Lord of Westfold",
        cost = "{3}{R}",
        type = "Legendary Creature — Human Soldier",
        text = "Whenever Grimbold or another Human you control enters, creatures you control get +1/+0 until end of turn.",
        pt = "3/3",
    ) parsesTo abilities(
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, sub("Human"), you))), pump(Objects.Each(creatureYouControl), 1, 0)),
    )

    // CR 120.4a: excess noncombat damage dealt to an opponent's creature
    @Test
    fun fallOfTheRiverIsle() = card(
        name = "Fall of the River Isle",
        cost = "{2}{R}",
        type = "Enchantment",
        text = """
            Whenever a creature an opponent controls is dealt excess noncombat damage, amass Orcs X, where X is that excess damage. (Put X +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)
            {7}{R}: This enchantment deals 7 damage to target creature.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = creatureOpponentControls), combat = false, excess = true)),
            amassOrcs(Value.EventAmount)),
        activated(cost(mana("{7}{R}")), damage(7, toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
    )

    // CR 615.12: damage can't be prevented this turn; each other creature with the same controller
    @Test
    fun flameFuryFoes() = card(
        name = "Flame, Fury, Foes!",
        cost = "{X}{R}",
        type = "Sorcery",
        text = "Damage can't be prevented this turn. Flame, Fury, Foes! deals X damage to target creature and 1 damage to each other creature with the same controller.",
    ) parsesTo abilities(
        spell(doing(Effect.ApplyMechanics(listOf(Mechanic.DamageCantBePrevented(EventPattern.Damage(ObjectFilter.Any, RecipientFilter()))), null, Duration.EndOfTurn)),
            damage(Value.Announced(name = "X"), toObjects(target("creature"))),
            damage(1, toObjects(Objects.Each(and(creature, not(ObjectFilter.Is(target("creature"))),
                ObjectFilter.ControlledBy(PlayerFilter.Is(Players.ControllerOf(target("creature")))))))),
            targets = listOf(slot("creature", creature)), announced = listOf("X")),
    )

    @Test
    fun burningInscription() = card(
        name = "Burning Inscription",
        cost = "{2}{R}",
        type = "Enchantment",
        text = """
            When this enchantment enters, the Ring tempts you.
            Whenever you cast an instant or sorcery spell, this enchantment deals 2 damage to each opponent.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, ringTempts),
        triggered(youCast(or(instant, sorcery)), damage(2, eachOpponent)),
    )

    // CR 509.1a: creatures without flying can't block this turn
    @Test
    fun fireOfTheWhiteTower() = card(
        name = "Fire of the White Tower",
        cost = "{3}{R}",
        type = "Sorcery",
        text = "Destroy target artifact or land. Creatures without flying can't block this turn.",
    ) parsesTo abilities(
        spell(destroy(target("permanent")), untilEndOfTurn(Objects.Each(and(creature, not(kw(Keyword.Flying)))), Mechanic.CantBlock(attackers = ObjectFilter.Any)),
            targets = listOf(slot("permanent", or(artifact, land)))),
    )

    // CR 603.12, 701.47c: "When you do", X the amassed Army's power
    @Test
    fun raidOfOrcs() = card(
        name = "Raid of Orcs",
        cost = "{3}{R}",
        type = "Sorcery",
        text = "Amass Orcs 2. When you do, Raid of Orcs deals X damage to target creature an opponent controls, where X is the amassed Army's power. (To amass Orcs 2, put two +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)",
    ) parsesTo abilities(
        spell(amassOrcs(n(2), bind = "army"), doing(Effect.ReflexiveTrigger(Instructions(targets = listOf(slot("creature", creatureOpponentControls)),
            steps = listOf(damage(power(Objects.Bound("army")), toObjects(target("creature")))))))),
    )

    // CR 603.10a: the dying creature's controller as it last existed
    @Test
    fun doriCounterOfKills() = card(
        name = "Dori, Counter of Kills",
        cost = "{3}{R}",
        type = "Legendary Creature — Dwarf Warrior",
        text = """
            Trample
            Whenever a creature an opponent controls dies, Dori deals 1 damage to that creature's controller.
        """,
        pt = "4/3",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(on(EventPattern.ZoneChange(creatureOpponentControls, from = Zone.Battlefield, to = Zone.Graveyard)),
            damage(1, Recipients(players = Players.ControllerOf(Objects.EventObject(EventRole.Before))))),
    )

    @Test
    fun dorisAxe() = card(
        name = "Dori's Axe",
        cost = "{2}{R}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +3/+0.
            As long as equipped creature is legendary, it has menace. (It can't be blocked except by two or more creatures.)
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(3, 0), affects = Objects.AttachedTo(Objects.Self)),
        static(has(Keyword.Menace), affects = Objects.AttachedTo(Objects.Self),
            condition = Condition.ObjectMatches(Objects.AttachedTo(Objects.Self), legendary)),
        Keyword.Equip(manaCost("{2}")),
    )

    @Test
    fun dorisFury() = card(
        name = "Dori's Fury",
        cost = "{1}{R}",
        type = "Instant",
        text = "Target creature gets +3/+2 until end of turn. If it's legendary, it also gains trample until end of turn.",
    ) parsesTo abilities(
        spell(pump(target("creature"), 3, 2), Step.If(Condition.ObjectMatches(target("creature"), legendary), then = listOf(gains(target("creature"), Keyword.Trample))),
            targets = listOf(slot("creature", creature))),
    )

    // CR 700.6, 701.15
    @Test
    fun noriDwarfEmissary() = card(
        name = "Nori, Dwarf Emissary",
        cost = "{2}{R}",
        type = "Legendary Creature — Dwarf Advisor",
        text = """
            Whenever you cast a historic spell, create a Treasure token. This ability triggers only once each turn. (Artifacts, legendaries, and Sagas are historic.)
            {T}, Sacrifice a Treasure: Goad target creature. (Until your next turn, that creature attacks each combat if able and attacks a player other than you if able.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(youCast(or(artifact, legendary, sub("Saga"))), createTreasure(), perTurn = 1),
        activated(cost(tap, sacrificeA(sub("Treasure"))), doing(Effect.Action(KeywordAction.Goad(target("creature")))), targets = listOf(slot("creature", creature))),
    )

    // CR 603.10a: its power as it last existed
    @Test
    fun goblinFlameleaper() = card(
        name = "Goblin Flameleaper",
        cost = "{1}{R}",
        type = "Creature — Goblin Warrior",
        text = """
            {1}{R}: This creature gets +1/+0 until end of turn.
            When this creature dies, it deals damage equal to its power to target creature an opponent controls.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        activated(cost(mana("{1}{R}")), pump(Objects.Self, 1, 0)),
        triggered(diesSelf, damage(power(Objects.Self), toObjects(target("creature"))),
            targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 603.12, 701.47c: control until end of turn of a creature no stronger than the amassed Army
    @Test
    fun ugrakBrashInstigator() = card(
        name = "Ugrak, Brash Instigator",
        cost = "{2}{R}",
        type = "Legendary Creature — Goblin Soldier",
        text = "When Ugrak enters, amass Orcs 2. When you do, until end of turn, gain control of target nonlegendary creature an opponent controls with power less than or equal to the amassed Army's power. Untap that creature. It gains haste until end of turn.",
        pt = "1/1",
    ) parsesTo abilities(
        triggered(entersSelf, amassOrcs(n(2), bind = "army"), doing(Effect.ReflexiveTrigger(Instructions(
            targets = listOf(slot("creature", and(not(legendary), creature, opponents, ObjectFilter.Power(Comparison.AtMost, power(Objects.Bound("army")))))),
            steps = listOf(untilEndOfTurn(target("creature"), Mechanic.ControlledBy(Players.You)), untapIt(target("creature")), gains(target("creature"), Keyword.Haste)))))),
    )

    @Test
    fun southronSpearmaster() = card(
        name = "Southron Spearmaster",
        cost = "{2}{R}",
        type = "Creature — Human Warrior",
        text = """
            Reach
            At the beginning of combat on your turn, another target creature you control gets +1/+0 until end of turn.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(beginningOfCombatOnYourTurn, pump(target("creature"), 1, 0), targets = listOf(slot("creature", and(another, creature, you)))),
    )

    @Suppress("unused") private val unused = listOf(CostObjects.Self, CostPart.TapSelf)
}
