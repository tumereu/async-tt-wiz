package arcanum.parser.sets.fdn

import arcanum.api.Affected
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.ColorRef
import arcanum.api.Modification
import arcanum.api.ManaKind
import arcanum.api.RecipientFilter
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import kotlin.test.Test

/** Chunk 10a of FDN: get / have / become / prevent wordings (pumps, lords, hexproof, prevention, uncounterable). Invented names, the real oracle text. */
class FdnChunk10aGetHaveTest {
    // CR 603.2 (trigger), 608.2c ("you may have": the choice is made on resolution), 611.2c, 613.4c
    @Test
    fun battleRattleShaman() = card(
        name = "Drumcall Shaman",
        cost = "{3}{R}",
        type = "Creature — Goblin Shaman",
        pt = "2/2",
        text = "At the beginning of combat on your turn, you may have target creature get +2/+0 until end of turn.",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, Step.May(listOf(pump(target("creature"), 2, 0)), player = Players.You),
            targets = listOf(slot("creature", creature))),
    )

    // CR 613.4c (layer 7c), 205.3m (subtypes), 702.2 (deathtouch)
    @Test
    fun deathBaron() = card(
        name = "Crypt Overseer",
        cost = "{1}{B}{B}",
        type = "Creature — Zombie Wizard",
        pt = "2/2",
        text = "Skeletons you control and other Zombies you control get +1/+1 and have deathtouch. (Any amount of damage they deal to a creature is enough to destroy it.)",
    ) parsesTo abilities(
        static(pt(1, 1), has(Keyword.Deathtouch), affects = Objects.Each(and(sub("Skeleton"), you))),
        static(pt(1, 1), has(Keyword.Deathtouch), affects = Objects.Each(and(another, sub("Zombie"), you))),
    )

    // CR 614.12 (as it enters), 105.1 (colors), 613.4c, 106.1b (mana of the chosen color)
    @Test
    fun heraldicBanner() = card(
        name = "Prismatic Pennant",
        cost = "{3}",
        type = "Artifact",
        text = """
            As this artifact enters, choose a color.
            Creatures you control of the chosen color get +1/+0.
            {T}: Add one mana of the chosen color.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Color(), bind = "color", chooser = Players.You, remember = true)))))),
        static(pt(1, 0), affects = Objects.Each(and(creature, you, ObjectFilter.OfColor(ColorRef.Remembered("color"))))),
        activated(cost(tap), doing(Effect.AddMana(arcanum.api.ManaProduction(ManaKind.Choose(n(1), colors = listOf(ColorRef.Remembered("color")))), Players.You))),
    )

    // CR 509.1c (must be blocked), 611.2c (locked in as it resolves), 613.4c
    @Test
    fun joraga() = card(
        name = "Vanguard Rally",
        cost = "{4}{G}{G}",
        type = "Sorcery",
        text = "Each creature you control gets +3/+3 until end of turn and must be blocked this turn if able.",
    ) parsesTo abilities(
        spell(untilEndOfTurn(Objects.Each(and(creature, you)), pt(3, 3), Mechanic.MustBeBlocked)),
    )

    // CR 603.10a (dies), 400.7 (a new object), 122.1, 613.1d (layer 4: type), 613.1f (layer 6: ability)
    @Test
    fun valkyriesCall() = card(
        name = "Dawnward Recall",
        cost = "{3}{W}{W}",
        type = "Enchantment",
        text = "Whenever a nontoken, non-Angel creature you control dies, return that card to the battlefield under its owner's control with a +1/+1 counter on it. It has flying and is an Angel in addition to its other types.",
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(and(not(token), not(sub("Angel")), creature, you), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.Do(Effect.Move(Objects.EventObject(arcanum.api.EventRole.After), arcanum.api.Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.EventObject(arcanum.api.EventRole.After)),
                counters = listOf(arcanum.api.CountersToPut(p1p1, n(1)))), postBind = "returned")),
            Step.Do(Effect.ApplyMechanics(listOf(has(Keyword.Flying), Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Angel"))))),
                Affected.Objects(Objects.Bound("returned")), Duration.Indefinitely))),
    )

    // CR 602.2 (activated), 122.1, 613.1d, 613.4b (layer 7b: 0/0), 205.1b ("It's still a land": a reminder), 608.2c
    @Test
    fun crawlingBarrens() = card(
        name = "Stirring Wastes",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {4}: Put two +1/+1 counters on this land. Then you may have it become a 0/0 Elemental creature until end of turn. It's still a land.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(arcanum.api.ManaProduction(ManaKind.Fixed(arcanum.api.ManaCost.parse("{C}")!!)), Players.You))),
        activated(manaCost("{4}"), counters(Objects.Self, 2),
            Step.May(listOf(untilEndOfTurn(Objects.Self, Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Elemental")))),
                Mechanic.SetPowerToughness(n(0), n(0)))), player = Players.You)),
    )

    // CR 702.11c (a player with hexproof), 615.1 (prevention), 120.2 (combat and noncombat damage), 702.3 (defender)
    @Test
    fun crystalBarricade() = card(
        name = "Bulwark Husk",
        cost = "{1}{W}",
        type = "Artifact Creature — Wall",
        pt = "0/4",
        text = """
            Defender (This creature can't attack.)
            You have hexproof. (You can't be the target of spells or abilities your opponents control.)
            Prevent all noncombat damage that would be dealt to other creatures you control.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        playerStatic(Mechanic.CantBeTargeted(by = opponents)),
        static(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = and(another, creature, you)), combat = false))),
    )

    // CR 101.2 (can't beats can), 701.6 (counter)
    @Test
    fun sphinxOfTheFinalWord() = card(
        name = "Oracle of Last Words",
        cost = "{5}{U}{U}",
        type = "Creature — Sphinx",
        pt = "5/5",
        text = """
            This spell can't be countered.
            Flying
            Hexproof (This creature can't be the target of spells or abilities your opponents control.)
            Instant and sorcery spells you control can't be countered.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeCountered),
        Keyword.Flying,
        Keyword.Hexproof,
        static(Mechanic.CantBeCountered, affects = Objects.Each(and(or(instant, sorcery), spellFilter, you))),
    )
}
