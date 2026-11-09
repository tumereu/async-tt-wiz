package arcanum.parser.sets.fdn

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.EventPattern
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.api.Destination
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 14 of FDN: replacement effects. Enters-tapped lands and creatures, a chosen creature type, enters with counters, exile instead. Invented names, the real oracle text. */
class FdnChunk14ReplacementTest {
    // CR 614.12, 607.2d, 613.4c
    @Test
    fun adaptiveEngine() = card(
        name = "Adaptive Engine",
        cost = "{3}",
        type = "Artifact Creature — Construct",
        pt = "2/2",
        text = """
            As this creature enters, choose a creature type.
            This creature is the chosen type in addition to its other types.
            Other creatures you control of the chosen type get +1/+1.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(
            event = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.CreatureType, bind = "type", remember = true, chooser = Players.You)))),
        )), affects = Affected.Objects(Objects.Self)),
        Ability.Static(
            listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Remembered("type")))),
            affects = Affected.Objects(Objects.Self),
        ),
        static(pt(1, 1), affects = Objects.Each(and(another, creature, you, ObjectFilter.Sub(SubtypeRef.Remembered("type"))))),
    )

    // CR 614.1c, 603.6a, 119.1
    @Test
    fun consulsAuthority() = card(
        name = "Consul's Authority",
        cost = "{W}",
        type = "Enchantment",
        text = """
            Creatures your opponents control enter tapped.
            Whenever a creature an opponent controls enters, you gain 1 life.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(and(creature, opponents), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped))),
        triggered(enters(and(creature, opponents)), gainLife(1)),
    )

    // CR 614.1c
    @Test
    fun gloomyGhoul() = card(
        name = "Gloomy Ghoul",
        cost = "{B}",
        type = "Creature — Zombie",
        pt = "2/2",
        text = "This creature enters tapped.",
    ) parsesTo abilities(entersTapped)

    // CR 614.1c, 122.6 (enters with counters)
    @Test
    fun bane() = card(
        name = "Hydra's Bane",
        cost = "{3}{G}{G}",
        type = "Creature — Hydra",
        pt = "0/0",
        text = """
            This creature enters with four +1/+1 counters on it.
            {2}{G}{G}: Put X +1/+1 counters on this creature, where X is its power.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, Value.Fixed(4))))),
        activated(manaCost("{2}{G}{G}"), counters(Objects.Self, power(Objects.Self))),
    )

    // CR 614.1a, 400.7
    @Test
    fun militantDryad() = card(
        name = "Militant Dryad",
        cost = "{G/W}",
        type = "Creature — Dryad Soldier",
        pt = "2/1",
        text = """
            ({G/W} can be paid with either {G} or {W}.)
            If an instant or sorcery card would be put into a graveyard from anywhere, exile it instead.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(or(instant, sorcery), to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
    )

    // CR 614.1c (enters tapped), 605.1a
    @Test
    fun azureGate() = card(
        name = "Azure Gate",
        cost = null,
        type = "Land — Gate",
        text = """
            This land enters tapped.
            {T}: Add {W} or {U}.
        """,
    ) parsesTo abilities(
        entersTapped,
        addEither("W", "U"),
    )

    // CR 614.1c (enters tapped), 603.6a, 119.1, 605.1a
    @Test
    fun rotfenCaves() = card(
        name = "Rotfen Caves",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, you gain 1 life.
            {T}: Add {B} or {R}.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf, gainLife(1)),
        addEither("B", "R"),
    )

    // CR 614.1c (enters tapped), 603.6a, 119.1, 605.1a
    @Test
    fun sunlitSands() = card(
        name = "Sunlit Sands",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, you gain 1 life.
            {T}: Add {G} or {W}.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf, gainLife(1)),
        addEither("G", "W"),
    )

    // CR 614.1c (enters tapped), 605.1a
    @Test
    fun crimsonGate() = card(
        name = "Crimson Gate",
        cost = null,
        type = "Land — Gate",
        text = """
            This land enters tapped.
            {T}: Add {R} or {W}.
        """,
    ) parsesTo abilities(
        entersTapped,
        addEither("R", "W"),
    )

    // CR 614.1c (enters tapped), 605.1a
    @Test
    fun gloomGate() = card(
        name = "Gloom Gate",
        cost = null,
        type = "Land — Gate",
        text = """
            This land enters tapped.
            {T}: Add {U} or {B}.
        """,
    ) parsesTo abilities(
        entersTapped,
        addEither("U", "B"),
    )

    // CR 614.1c (enters tapped), 603.6a, 119.1, 605.1a
    @Test
    fun murkyBackwater() = card(
        name = "Murky Backwater",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, you gain 1 life.
            {T}: Add {U} or {B}.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf, gainLife(1)),
        addEither("U", "B"),
    )

    // CR 614.1c (enters tapped), 605.1a
    @Test
    fun mireGate() = card(
        name = "Mire Gate",
        cost = null,
        type = "Land — Gate",
        text = """
            This land enters tapped.
            {T}: Add {B} or {G}.
        """,
    ) parsesTo abilities(
        entersTapped,
        addEither("B", "G"),
    )

    // CR 614.1c (enters tapped), 605.1a
    @Test
    fun rampageGate() = card(
        name = "Rampage Gate",
        cost = null,
        type = "Land — Gate",
        text = """
            This land enters tapped.
            {T}: Add {R} or {G}.
        """,
    ) parsesTo abilities(
        entersTapped,
        addEither("R", "G"),
    )

    // CR 614.1c (enters tapped), 605.1a
    @Test
    fun stormGate() = card(
        name = "Storm Gate",
        cost = null,
        type = "Land — Gate",
        text = """
            This land enters tapped.
            {T}: Add {U} or {R}.
        """,
    ) parsesTo abilities(
        entersTapped,
        addEither("U", "R"),
    )

    // CR 614.1c (enters tapped), 603.6a, 119.1, 605.1a
    @Test
    fun mossyHollow() = card(
        name = "Mossy Hollow",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            When this land enters, you gain 1 life.
            {T}: Add {B} or {G}.
        """,
    ) parsesTo abilities(
        entersTapped,
        triggered(entersSelf, gainLife(1)),
        addEither("B", "G"),
    )
}
