package arcanum.parser.sets.isd

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * ISD chunk 04: Equipment (CR 301.5, 702.6). "Equipped creature" is the creature this Equipment is attached to; a condition on it
 * ("As long as equipped creature is a Human") is checked continuously (CR 611.3a).
 */
class IsdChunk04EquipTest {
    private val equipped = Objects.AttachedTo(Objects.Self)
    private fun equip(cost: String) = Keyword.Equip(manaCost(cost))
    private val isHuman = Condition.ObjectMatches(equipped, sub("Human"))
    private val doubled = listOf(Modification.MultiplyAmount(2))

    // CR 614.1a: two replacements; only combat damage; the second only for damage dealt to the equipped creature by another creature
    @Test
    fun zealotsScourge() = card(
        name = "Zealot's Scourge",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            If equipped creature would deal combat damage, it deals double that damage instead.
            If another creature would deal combat damage to equipped creature, it deals double that damage to equipped creature instead.
            Equip {2}
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.Damage(ObjectFilter.Is(equipped), RecipientFilter(), combat = true), modify = doubled)),
        static(Mechanic.Replacement(EventPattern.Damage(and(not(ObjectFilter.Is(equipped)), creature),
            RecipientFilter(objects = ObjectFilter.Is(equipped)), combat = true), modify = doubled)),
        equip("{2}"),
    )

    // CR 613.4c, 208.1: +X/+0 counted continuously from your graveyard's instant and sorcery cards; CR 702.7a
    @Test
    fun spellscribesGlaive() = card(
        name = "Spellscribe's Glaive",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature has first strike and gets +X/+0, where X is the number of instant and sorcery cards in your graveyard.
            Equip {2}
        """,
    ) parsesTo abilities(
        static(has(Keyword.FirstStrike), Mechanic.ModifyPowerToughness(count(inYourGraveyard(or(instant, sorcery))), n(0)), affects = equipped),
        equip("{2}"),
    )

    // CR 611.3a, 613.4c: the bonus applies only while the equipped creature is a Human
    @Test
    fun honedHayfork() = card(
        name = "Honed Hayfork",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature has first strike.
            As long as equipped creature is a Human, it gets +1/+1.
            Equip {1}
        """,
    ) parsesTo abilities(
        static(has(Keyword.FirstStrike), affects = equipped),
        static(pt(1, 1), affects = equipped, condition = isHuman),
        equip("{1}"),
    )

    // CR 611.3a, 613.4c: "an additional +1/+0" is a second modification, on top of the first
    @Test
    fun silverchasedDirk() = card(
        name = "Silverchased Dirk",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+0.
            As long as equipped creature is a Human, it gets an additional +1/+0.
            Equip {2}
        """,
    ) parsesTo abilities(
        static(pt(2, 0), affects = equipped),
        static(pt(1, 0), affects = equipped, condition = isHuman),
        equip("{2}"),
    )

    // CR 508.1m, 506.2: the defending player reveals until a land, the land too (CR 701.20a); the attacker gets +1/+0 for each card
    // revealed; then they go to that player's graveyard
    @Test
    fun skullboreKnife() {
        val revealed = Objects.Union(listOf(Objects.Bound("found"), Objects.Bound("rest")))
        val attacker = Objects.EventObject(EventRole.Object)
        card(
            name = "Skullbore Knife",
            cost = "{3}",
            type = "Artifact — Equipment",
            text = """
                Whenever equipped creature attacks, defending player reveals cards from the top of their library until they reveal a land card. The creature gets +1/+0 until end of turn for each card revealed this way. That player puts the revealed cards into their graveyard.
                Equip {2}
            """,
        ) parsesTo abilities(
            triggered(on(EventPattern.Attack(ObjectFilter.Is(equipped))),
                doing(Effect.RevealUntil(land, Players.DefendingPlayerOf(attacker), n(1), reveal = true, foundBind = "found", restBind = "rest")),
                doing(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(Value.Count(revealed), n(0))), Affected.Objects(attacker), Duration.EndOfTurn)),
                move(revealed, Zone.Graveyard)),
            equip("{2}"),
        )
    }

    // CR 509.3b, 509.3d: once for each Vampire it blocks or that blocks it; "that creature" is that Vampire; CR 701.19c
    @Test
    fun ashwoodSpike() = card(
        name = "Ashwood Spike",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+0.
            Whenever equipped creature blocks or becomes blocked by a Vampire, destroy that creature. It can't be regenerated.
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(1, 0), affects = equipped),
        triggered(Trigger.On(listOf(EventPattern.Block(ObjectFilter.Is(equipped), sub("Vampire"), perAttacker = true),
            EventPattern.BecomesBlocked(ObjectFilter.Is(equipped), sub("Vampire"), perBlocker = true))),
            doing(Effect.Destroy(Objects.EventObject(EventRole.OtherCombatant), noRegeneration = true))),
        equip("{1}"),
    )

    // CR 509.1b; CR 113.10, 301.5c: the granted ability names the Equipment, sacrificed and the source of the damage
    @Test
    fun flaringBrand() = card(
        name = "Flaring Brand",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature can't be blocked by Vampires or Zombies.
            Equipped creature has "{T}, Sacrifice Flaring Brand: Flaring Brand deals 2 damage to any target."
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeBlockedBy(or(sub("Vampire"), sub("Zombie"))), affects = equipped),
        static(has(activated(cost(tap, CostPart.Sacrifice(CostObjects.These(Objects.Granter))),
            damage(2, toAny, source = Objects.Granter), targets = listOf(anyTarget))), affects = equipped),
        equip("{1}"),
    )

    // CR 611.3a, 702.15a: lifelink only while the equipped creature is a Human
    @Test
    fun slaughtermansHatchet() = card(
        name = "Slaughterman's Hatchet",
        cost = "{3}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +3/+0.
            As long as equipped creature is a Human, it has lifelink.
            Equip {3}
        """,
    ) parsesTo abilities(
        static(pt(3, 0), affects = equipped),
        static(has(Keyword.Lifelink), affects = equipped, condition = isHuman),
        equip("{3}"),
    )

    // CR 702.9a
    @Test
    fun stitchedPinions() = card(
        name = "Stitched Pinions",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature has flying.
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(has(Keyword.Flying), affects = equipped),
        equip("{1}"),
    )

    // CR 702.6a, 602.2b: an equip cost that sacrifices a creature
    @Test
    fun fiendforgedCuirass() = card(
        name = "Fiendforged Cuirass",
        cost = "{4}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +4/+2.
            Equip—Sacrifice a creature.
        """,
    ) parsesTo abilities(
        static(pt(4, 2), affects = equipped),
        Keyword.Equip(cost(sacrificeA(creature))),
    )

    // CR 613.4c, 702.11a
    @Test
    fun visageOfTheArchangel() = card(
        name = "Visage of the Archangel",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+2 and has hexproof. (It can't be the target of spells or abilities your opponents control.)
            Equip {3}
        """,
    ) parsesTo abilities(
        static(pt(1, 2), has(Keyword.Hexproof), affects = equipped),
        equip("{3}"),
    )
}
