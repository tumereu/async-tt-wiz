package arcanum.parser.sets.fdn

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** The FDN Auras (chunk 02). The invented names carry the oracle text of the real cards. */
class FdnChunk02AurasTest {
    private val enchanted = Objects.AttachedTo(Objects.Self)
    private val enchantCreature = Keyword.Enchant(TargetKind.Objects(creature))
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private fun subtype(name: String) = SubtypeRef.Literal(Subtype(name))

    // CR 702.8a, 702.5a, 613.1 (layers 4, 5, 6, 7b), 205.1a: the card type is set, so the others go
    @Test
    fun swallowedByEels() = card(
        name = "Swallowed by Eels",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Flash (You may cast this spell any time you could cast an instant.)
            Enchant creature
            Enchanted creature loses all abilities and is a black Skeleton creature with base power and toughness 1/1. (It loses all other colors, card types, and creature types.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        enchantCreature,
        static(
            Mechanic.LoseAbilities(),
            Mechanic.SetColors(listOf(ColorRef.Literal(Color.Black))),
            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(subtype("Skeleton")), replaceTypes = true, replaceSubtypes = true),
            Mechanic.SetPowerToughness(n(1), n(1)),
            affects = enchanted,
        ),
    )

    // CR 702.5a: one list of what it can enchant, not three keywords; 613.1 (layers 4, 5, 6), 205.1a
    @Test
    fun entombedInTheStar() = card(
        name = "Entombed in the Star",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature, land, or planeswalker
            Enchanted permanent is a colorless land with "{T}: Add {C}" and loses all other card types and abilities.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(or(creature, land, planeswalker))),
        static(
            Mechanic.SetTypes(add = listOf(CardType.Land), replaceTypes = true, replaceSubtypes = true),
            Mechanic.SetColors(emptyList()),
            Mechanic.LoseAbilities(),
            has(activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You)))),
            affects = enchanted,
        ),
    )

    // CR 612.8: a name set by an effect; 613.1 (layers 4, 5, 6, 7b), 205.1a
    @Test
    fun newAlias() = card(
        name = "New Alias",
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
            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(subtype("Citizen")), replaceTypes = true, replaceSubtypes = true),
            Mechanic.SetPowerToughness(n(1), n(1)),
            affects = enchanted,
        ),
    )

    // CR 205.1b: "in addition to its other types" keeps them (Angel added, neither flag replaces); CR 603.10a: the death looks back; CR 400.7: "this card" is
    // the new object in the graveyard by then
    @Test
    fun seraphicFate() = card(
        name = "Seraphic Fate",
        cost = "{2}{W}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +4/+4, has flying and first strike, and is an Angel in addition to its other types.
            When enchanted creature dies, return this card to its owner's hand.
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(
            pt(4, 4),
            has(Keyword.Flying, Keyword.FirstStrike),
            Mechanic.SetTypes(addSubtypes = listOf(subtype("Angel"))),
            affects = enchanted,
        ),
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(enchanted), from = Zone.Battlefield, to = Zone.Graveyard)), move(Objects.SelfAfter, Zone.Hand)),
    )

    // CR 613.4c
    @Test
    fun forestbarkMantle() {
        val forests = Value.Count(Objects.Each(and(sub("Forest"), you)))
        card(
            name = "Forestbark Mantle",
            cost = "{2}{G}",
            type = "Enchantment — Aura",
            text = """
                Enchant creature
                Enchanted creature gets +1/+1 for each Forest you control.
            """,
        ) parsesTo abilities(
            enchantCreature,
            static(Mechanic.ModifyPowerToughness(forests, forests), affects = enchanted),
        )
    }

    // CR 613.1b (layer 2)
    @Test
    fun wrongfulSeizure() = card(
        name = "Wrongful Seizure",
        cost = "{4}{U}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant permanent
            You control enchanted permanent.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(permanentFilter)),
        static(Mechanic.ControlledBy(Players.You), affects = enchanted),
    )

    // CR 106.5: "any one color" is one color chosen for both mana
    @Test
    fun freshVistas() = card(
        name = "Fresh Vistas",
        cost = "{2}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            When this Aura enters, put a +1/+1 counter on target creature you control.
            Enchanted land has "{T}: Add two mana of any one color."
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(land)),
        triggered(entersSelf, counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
        static(has(activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(2))), Players.You)))), affects = enchanted),
    )

    // CR 608.2c: "it" in the second sentence is the enchanted creature the counter went on, not this Aura; CR 701.21a
    @Test
    fun trialOfTheHuntress() = card(
        name = "Trial of the Huntress",
        cost = "{1}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Whenever enchanted creature attacks, put a +1/+1 counter on it. Then if it has three or more +1/+1 counters on it, sacrifice this Aura.
            When you sacrifice this Aura, search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        enchantCreature,
        triggered(
            on(EventPattern.Attack(ObjectFilter.Is(enchanted))),
            counters(Objects.EventObject(EventRole.Object)),
            Step.If(Condition.ObjectMatches(Objects.EventObject(EventRole.Object), ObjectFilter.HasCounter(p1p1, atLeast = 3)), then = listOf(sacrificeIt)),
        ),
        triggered(
            on(EventPattern.Sacrifice(ObjectFilter.Is(Objects.Self))),
            search(basicLand(), max = 2),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
            shuffle,
        ),
    )

    // CR 508.1, 509.1
    @Test
    fun sereneOath() = card(
        name = "Serene Oath",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature can't attack or block.
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), affects = enchanted),
    )

    // CR 502.3
    @Test
    fun moonlitShackle() = card(
        name = "Moonlit Shackle",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, tap enchanted creature.
            Enchanted creature doesn't untap during its controller's untap step.
        """,
    ) parsesTo abilities(
        enchantCreature,
        triggered(entersSelf, tapIt(enchanted)),
        static(Mechanic.DoesntUntap, affects = enchanted),
    )

    // CR 702.4a
    @Test
    fun pairedEdgeGrace() = card(
        name = "Paired Edge Grace",
        cost = "{1}{W}{W}",
        type = "Enchantment — Aura",
        text = """
            Flash (You may cast this spell any time you could cast an instant.)
            Enchant creature
            Enchanted creature has double strike. (It deals both first-strike and regular combat damage.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        enchantCreature,
        static(has(Keyword.DoubleStrike), affects = enchanted),
    )

    // CR 702.19, 702.15
    @Test
    fun steadfastVigor() = card(
        name = "Steadfast Vigor",
        cost = "{1}{G}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+2 and has trample and lifelink. (Damage dealt by the creature also causes its controller to gain that much life.)
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(pt(2, 2), has(Keyword.Trample, Keyword.Lifelink), affects = enchanted),
    )

    // CR 702.111
    @Test
    fun feralCraving() = card(
        name = "Feral Craving",
        cost = "{2}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+1 and has menace. (It can't be blocked except by two or more creatures.)
        """,
    ) parsesTo abilities(
        enchantCreature,
        static(pt(2, 1), has(Keyword.Menace), affects = enchanted),
    )
}
