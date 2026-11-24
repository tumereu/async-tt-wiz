package arcanum.parser.sets.snc

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** SNC chunk 05: Auras and Equipment (CR 303.4, 301.5, 702.5, 702.6). */
class SncChunk05EnchantEquipTest {
    private val enchantCreature = Keyword.Enchant(TargetKind.Objects(creature))
    private val enchanted: Objects = Objects.AttachedTo(Objects.Self)
    private val enchantedDies = on(EventPattern.ZoneChange(ObjectFilter.Is(enchanted), from = Zone.Battlefield, to = Zone.Graveyard))
    private fun equip(cost: String) = Keyword.Equip(manaCost(cost))
    private val citizen = creatureToken("Citizen", listOf(Color.Green, Color.White), 1, 1)

    // CR 508.1c, 509.1a (can't attack or block), 301.5c/113.10 (a granted activated ability names the Aura: Granter; "it" is the Aura,
    // which its controller sacrifices, CR 701.21a), 602.5d (sorcery timing)
    @Test
    fun kidnappersLeash() = card(
        name = "Kidnapper's Leash",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature can't attack or block and has "{7}: Kidnapper's Leash's controller sacrifices it and draws a card. Activate only as a sorcery."
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(
            Mechanic.CantAttack(),
            Mechanic.CantBlock(ObjectFilter.Any),
            has(activated(cost(mana("{7}")),
                Step.Do(Effect.Sacrifice(Objects.Granter, by = Players.ControllerOf(Objects.Granter))),
                Step.Do(Effect.Draw(Players.ControllerOf(Objects.Granter), n(1))),
                rules = asSorcery)),
            affects = enchanted,
        ),
    )

    // CR 508.1d (a requirement to attack a given player), 603.10a (the Aura's dies trigger looks back)
    @Test
    fun mostHuntedFugitive() = card(
        name = "Most Hunted Fugitive",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            All creatures attack enchanted creature's controller each combat if able.
            When enchanted creature dies, draw a card.
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(Mechanic.MustAttack(Recipients(players = Players.ControllerOf(enchanted))), affects = Objects.Each(creature)),
        triggered(enchantedDies, draw(1)),
    )

    // CR 111.10a (two Treasure tokens), 702.8a (flash)
    @Test
    fun bountyPoster() = card(
        name = "Bounty Poster",
        cost = "{2}{G}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            Enchanted creature gets +2/+1.
            When enchanted creature dies, create two Treasure tokens.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        enchantCreature,
        static(pt(2, 1), affects = enchanted),
        triggered(enchantedDies, createTreasure(n(2))),
    )

    // CR 118.9, 601.3e: cast from the graveyard for an alternative cost
    @Test
    fun revenantWard() = card(
        name = "Revenant Ward",
        cost = "{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +1/+1.
            You may cast this card from your graveyard by paying {2}{W} rather than paying its mana cost.
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(pt(1, 1), affects = enchanted),
        static(Mechanic.MayPlay(from = Zone.Graveyard, how = PlayKind.Cast, cost = cost(mana("{2}{W}")), holder = PlayerFilter.You)),
    )

    // CR 701.26a (tap), 608.2c ("you create": the Aura's controller), 509.1b (a token's own "can't be blocked"), 302.6 / 502.3
    @Test
    fun concreteSlumber() = card(
        name = "Concrete Slumber",
        cost = "{2}{U}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, tap enchanted creature and you create a 1/1 blue Fish creature token with "This token can't be blocked."
            Enchanted creature doesn't untap during its controller's untap step.
        """,
    ) parsesTo abilities(
        enchantCreature,
        triggered(entersSelf, tapIt(enchanted),
            create(creatureToken("Fish", listOf(Color.Blue), 1, 1, listOf(static(Mechanic.CantBeBlocked))))),
        static(Mechanic.DoesntUntap, affects = enchanted),
    )

    // CR 702.110a (menace), 113.10 (a granted trigger), 111.10a, 603.10a
    @Test
    fun lightFingersCharm() = card(
        name = "Light Fingers Charm",
        cost = "{R}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature has menace and "Whenever this creature deals combat damage to a player, create a Treasure token." (It can't be blocked except by two or more creatures. The token is an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            When enchanted creature dies, draw a card.
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(has(Keyword.Menace, triggered(combatDamageToAPlayer, createTreasure())), affects = enchanted),
        triggered(enchantedDies, draw(1)),
    )

    // CR 613.1 (layers 4, 5, 6, 7b), 205.1a, 612.8: same wording as an earlier set's Aura
    @Test
    fun newIdentityProgram() = card(
        name = "New Identity Program",
        cost = "{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature loses all abilities and is a green and white Citizen creature with base power and toughness 1/1 named Legitimate Businessperson. (It loses all other colors, card types, creature types, and names.)
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(
            Mechanic.SetName("Legitimate Businessperson"),
            Mechanic.LoseAbilities(),
            Mechanic.SetColors(listOf(ColorRef.Literal(Color.Green), ColorRef.Literal(Color.White))),
            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Citizen"))), replaceTypes = true, replaceSubtypes = true),
            Mechanic.SetPowerToughness(n(1), n(1)),
            affects = enchanted,
        ),
    )

    // CR 113.10: the creature's controller activates the granted ability; 509.1h / 702.? n/a. The damaged creature is one blocking this creature
    @Test
    fun sparkLobber() = card(
        name = "Spark Lobber",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature has "{1}: This creature deals 1 damage to target creature that's blocking it."
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(has(activated(manaCost("{1}"),
            damage(1, Recipients(objects = target("creature"))),
            targets = listOf(slot("creature", and(creature, ObjectFilter.Blocking(Objects.Self)))))), affects = enchanted),
        equip("{1}"),
    )

    // CR 702.4b (double strike), 301.5 (Equipment attached to it), 603.4 n/a; static condition counts Equipment attached
    @Test
    fun ironFistDuo() = card(
        name = "Iron Fist Duo",
        cost = "{4}",
        type = "Artifact — Equipment",
        text = """
            When you cast this spell, copy it. (The copy becomes a token.)
            Equipped creature has double strike as long as two or more Equipment are attached to it.
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(youCast(ObjectFilter.Is(Objects.Self)), doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = false))),
        static(has(Keyword.DoubleStrike), affects = enchanted, condition = Condition.Compare(
            Value.Count(Objects.AttachmentsOf(enchanted, equipment)), Comparison.AtLeast, n(2))),
        equip("{1}"),
    )

    // CR 702.6e (Equip planeswalker: attaches to a planeswalker as though it were a creature), 305/306.5 (loyalty), 205.1b (a creature in
    // addition to its other types; no longer a planeswalker), 122.1 (each counter, of any kind)
    @Test
    fun wanderingBladeOfKings() = card(
        name = "Wandering Blade of Kings",
        cost = "{1}",
        type = "Legendary Artifact — Equipment",
        text = """
            Equipped creature gets +1/+1 for each counter on it.
            Equipped permanent isn't a planeswalker and is a creature in addition to its other types. (Loyalty abilities can still be activated.)
            Equip planeswalker {1}
            Equip {3}
        """,
    ) parsesTo abilities(
        static(Mechanic.ModifyPowerToughness(Value.Of(NumericProperty.Counters(null), enchanted, Combine.Single),
            Value.Of(NumericProperty.Counters(null), enchanted, Combine.Single)), affects = enchanted),
        static(Mechanic.SetTypes(add = listOf(CardType.Creature), remove = listOf(CardType.Planeswalker)), affects = enchanted),
        Keyword.Equip(manaCost("{1}"), planeswalker = true),
        equip("{3}"),
    )

    // CR 502.3 (doesn't untap), 113.10 (a granted trigger at the creature's controller's end step: "your" = its controller)
    @Test
    fun leadBoots() = card(
        name = "Lead Boots",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +3/+3 and has "At the beginning of your end step, tap this creature."
            Equipped creature doesn't untap during its controller's untap step.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(3, 3), has(triggered(stepBegins(TurnStep.End, PlayerFilter.You), tapIt(Objects.Self))), affects = enchanted),
        static(Mechanic.DoesntUntap, affects = enchanted),
        equip("{2}"),
    )

    // CR 702.6c ("Equip Citizen": a restriction on targets only; two equip abilities, 702.6d)
    @Test
    fun ribbonShovel() = card(
        name = "Ribbon Shovel",
        cost = "{1}{G}{W}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+1 and has trample.
            Equip Citizen {1}
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(2, 1), has(Keyword.Trample), affects = enchanted),
        Keyword.Equip(manaCost("{1}"), onto = sub("Citizen")),
        equip("{3}"),
    )

    // CR 701.3a (attach to it, the new token), 301.5c / 113.10 + CR 701.21 (the granted cost sacrifices the Equipment: Granter)
    @Test
    fun neighborsPryBar() = card(
        name = "Neighbor's Pry Bar",
        cost = "{1}{W}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, create a 1/1 green and white Citizen creature token, then attach this Equipment to it.
            Equipped creature gets +1/+1 and has "{W}, {T}, Sacrifice Neighbor's Pry Bar: Destroy target artifact or enchantment."
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(citizen, bind = "token"), doing(Effect.Attach(Objects.Self, Recipients(objects = Objects.Bound("token"))))),
        static(pt(1, 1), has(activated(cost(mana("{W}"), tap, CostPart.Sacrifice(CostObjects.These(Objects.Granter))),
            destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment))))), affects = enchanted),
        equip("{2}"),
    )

    // CR 111.10a, 702.9a
    @Test
    fun giltWingHarness() = card(
        name = "Gilt Wing Harness",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            Equipped creature has flying.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        triggered(entersSelf, createTreasure()),
        static(has(Keyword.Flying), affects = enchanted),
        equip("{2}"),
    )

    @Test
    fun pneumaticDrill() = card(
        name = "Pneumatic Drill",
        cost = "{1}{R}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+0.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(2, 0), affects = enchanted),
        equip("{2}"),
    )

    // CR 701.3a (attach when it enters, to a target; first strike until end of turn is for the creature, not the Equipment), 702.8a
    @Test
    fun fastHandKnife() = card(
        name = "Fast Hand Knife",
        cost = "{3}",
        type = "Artifact — Equipment",
        text = """
            Flash
            When this Equipment enters, attach it to target creature you control. That creature gains first strike until end of turn.
            Equipped creature gets +1/+1.
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, doing(Effect.Attach(Objects.Self, Recipients(objects = target("creature")))), gains(target("creature"), Keyword.FirstStrike),
            targets = listOf(slot("creature", creatureYouControl))),
        static(pt(1, 1), affects = enchanted),
        equip("{1}"),
    )
}
