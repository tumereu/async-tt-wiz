package arcanum.parser.sets.isd

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * ISD chunk 11: a {0} alternative cost, a cost reduction, "until end of turn" pumps and keyword grants, control change, activated-ability
 * prohibition, and blocking restrictions ("can't be blocked", "except by", "can't be blocked by", "can't block").
 */
class IsdChunk11CostsAndRestrictionsTest {
    private val human = sub("Human")
    private fun xPumpOf(objects: Objects, x: Value) = untilEndOfTurn(objects, Mechanic.ModifyPowerToughness(x, x))

    // CR 118.9: an alternative cost for only the Zombie creature spells its controller casts
    @Test
    fun rooftopGale() = card(
        name = "Rooftop Gale",
        cost = "{5}{U}",
        type = "Enchantment",
        text = "You may pay {0} rather than pay the mana cost for Zombie creature spells you cast.",
    ) parsesTo abilities(
        static(Mechanic.AlternativeCost(cost = Cost.mana(ManaCost.parse("{0}")!!)),
            affects = Objects.Each(and(sub("Zombie"), creature, ObjectFilter.WasCast()))),
    )

    // CR 611.2a, 107.3a: X is the number of creatures you control when the ability resolves
    @Test
    fun elderOfBriars() = card(
        name = "Elder of Briars",
        cost = "{2}{G}",
        type = "Creature — Human Advisor",
        pt = "2/3",
        text = "{3}{G}: Target creature gets +X/+X until end of turn, where X is the number of creatures you control.",
    ) parsesTo abilities(
        activated(cost(mana("{3}{G}")), xPumpOf(target("creature"), count(creatureYouControl)), targets = listOf(slot("creature", creature))),
    )

    // CR 508.1m, 509.3b: "attacks or blocks" are two events of one trigger
    @Test
    fun hamletWarden() = card(
        name = "Hamlet Warden",
        cost = "{1}{G}",
        type = "Creature — Human Warrior",
        pt = "2/2",
        text = "Whenever this creature attacks or blocks, other Humans you control get +1/+1 until end of turn.",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.Attack(ObjectFilter.Is(Objects.Self)), EventPattern.Block(ObjectFilter.Is(Objects.Self), ObjectFilter.Any))),
            pump(Objects.Each(and(another, human, you)), 1, 1)),
    )

    // CR 611.2c: only creatures the opponents control now
    @Test
    fun hystericalDread() = card(
        name = "Hysterical Dread",
        cost = "{2}{U}",
        type = "Instant",
        text = "Creatures your opponents control get -4/-0 until end of turn.",
    ) parsesTo abilities(spell(pump(Objects.Each(creatureOpponentControls), -4, 0)))

    // CR 702.15a
    @Test
    fun momentOfCourage() = card(
        name = "Moment of Courage",
        cost = "{1}{W}",
        type = "Instant",
        text = "Target creature gets +2/+2 and gains lifelink until end of turn. (Damage dealt by the creature also causes its controller to gain that much life.)",
    ) parsesTo abilities(
        spell(pump(target("creature"), 2, 2, has(Keyword.Lifelink)), targets = listOf(slot("creature", creature))),
    )

    // CR 702.11a
    @Test
    fun rangersWard() = card(
        name = "Ranger's Ward",
        cost = "{G}",
        type = "Instant",
        text = "Target creature you control gets +1/+1 and gains hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)",
    ) parsesTo abilities(
        spell(pump(target("creature"), 1, 1, has(Keyword.Hexproof)), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 602.2: the sacrifice is a cost; the pump applies to the creatures you control as it resolves
    @Test
    fun selflessTemplar() = card(
        name = "Selfless Templar",
        cost = "{W}",
        type = "Creature — Human Cleric",
        pt = "1/1",
        text = "{1}{W}, Sacrifice this creature: Creatures you control get +1/+1 until end of turn.",
    ) parsesTo abilities(
        activated(cost(mana("{1}{W}"), sacrificeSelf), pump(Objects.Each(creatureYouControl), 1, 1)),
    )

    // CR 701.12 (control), 701.26a (untap), 702.19 (trample), 702.10 (haste)
    @Test
    fun traitorousTide() = card(
        name = "Traitorous Tide",
        cost = "{1}{R}{R}",
        type = "Sorcery",
        text = "Gain control of target creature until end of turn. Untap it. It gains trample and haste until end of turn.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("creature")), Duration.EndOfTurn)),
            Step.Do(Effect.Untap(target("creature"))),
            gains(target("creature"), Keyword.Trample, Keyword.Haste),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 702.7a
    @Test
    fun vampiricRage() = card(
        name = "Vampiric Rage",
        cost = "{1}{R}",
        type = "Instant",
        text = "Vampire creatures you control get +2/+0 and gain first strike until end of turn. (They deal combat damage before creatures without first strike.)",
    ) parsesTo abilities(
        spell(pump(Objects.Each(and(sub("Vampire"), creature, you)), 2, 0, has(Keyword.FirstStrike))),
    )

    // CR 509.1b: the restriction applies to the blockers
    @Test
    fun orchardWisp() = card(
        name = "Orchard Wisp",
        cost = "{2}{G}",
        type = "Creature — Spirit",
        pt = "2/2",
        text = "This creature can't be blocked except by creatures with flying or reach.",
    ) parsesTo abilities(
        static(Mechanic.CanBeBlockedOnlyBy(and(creature, or(kw(Keyword.Flying), kw(Keyword.Reach))))),
    )

    // CR 602.5: it affects every player's activated abilities of artifacts
    @Test
    fun stonyStillness() = card(
        name = "Stony Stillness",
        cost = "{1}{W}",
        type = "Enchantment",
        text = "Activated abilities of artifacts can't be activated.",
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.CantActivate(abilities = ObjectFilter.AbilityOf(source = or(artifact)))),
            affects = Affected.Players(players = Players.Each(filter = PlayerFilter.Any)),
        ),
    )

    // CR 509.1b
    @Test
    fun unseenStalker() = card(
        name = "Unseen Stalker",
        cost = "{1}{U}",
        type = "Creature — Human Rogue",
        pt = "1/1",
        text = """
            Hexproof (This creature can't be the target of spells or abilities your opponents control.)
            This creature can't be blocked.
        """,
    ) parsesTo abilities(Keyword.Hexproof, static(Mechanic.CantBeBlocked))

    // CR 509.1b: the restriction applies to the blockers
    @Test
    fun stromkirkSquire() = card(
        name = "Stromkirk Squire",
        cost = "{R}",
        type = "Creature — Vampire Noble",
        pt = "1/1",
        text = """
            This creature can't be blocked by Humans.
            Whenever this creature deals combat damage to a player, put a +1/+1 counter on it.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeBlockedBy(human)),
        triggered(combatDamageToAPlayer, counters(Objects.Self, n(1))),
    )

    // CR 509.1a
    @Test
    fun vampireLurker() = card(
        name = "Vampire Lurker",
        cost = "{1}{B}",
        type = "Creature — Vampire Scout",
        pt = "2/1",
        text = """
            Flying
            This creature can't block.
        """,
    ) parsesTo abilities(Keyword.Flying, static(Mechanic.CantBlock(attackers = ObjectFilter.Any)))

    // CR 601.2f: the reduction counts every creature on the battlefield; the damage is dealt to each creature, the spell included in neither
    @Test
    fun blasphemousRite() = card(
        name = "Blasphemous Rite",
        cost = "{8}{R}",
        type = "Sorcery",
        text = """
            This spell costs {1} less to cast for each creature on the battlefield.
            Blasphemous Rite deals 13 damage to each creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, count(creature)))),
        spell(damage(13, toObjects(Objects.Each(creature)))),
    )

    // CR 601.2f: creature spells you cast; CR 613.4c (-1/-1 to creatures you control)
    @Test
    fun heartlessBinding() = card(
        name = "Heartless Binding",
        cost = "{1}{B}",
        type = "Enchantment",
        text = """
            Creature spells you cast cost {2} less to cast.
            Creatures you control get -1/-1.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(creature, spellFilter, you), CostChange.Less(ManaCost.parse("{2}")!!, n(1)))),
        static(pt(-1, -1), affects = Objects.Each(creatureYouControl)),
    )
}
