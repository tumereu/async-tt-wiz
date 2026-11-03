package arcanum.parser.sets.tmt

import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.Subtype
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Teenage Mutant Ninja Turtles chunk 01: Mutagen tokens (CR 111.10v: a colorless Mutagen artifact token with "{1}, {T}, Sacrifice this
 * token: Put a +1/+1 counter on target creature. Activate only as a sorcery.") and the +1/+1 counters they feed: created as a creature
 * enters, leaves or attacks, by spells, one for each counter on a creature that left (CR 603.10a); activated abilities of artifact tokens
 * costing less (CR 601.2f); counters put plus one (CR 614.1a); counters moved from an artifact put into a graveyard.
 */
class TmtChunk01MutagenTest {
    @Test
    fun shellbackConscript() = card(
        name = "Shellback Conscript",
        cost = "{1}{U}",
        type = "Creature — Crab Mutant Soldier",
        text = "When this creature enters, create a Mutagen token. (It's an artifact with \"{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.\")",
        pt = "0/3",
    ) parsesTo abilities(
        triggered(entersSelf, createMutagen()),
    )

    @Test
    fun sludgeSurge() = card(
        name = "Sludge Surge",
        cost = "{1}{U}{U}",
        type = "Instant",
        text = "Counter target spell. Create a Mutagen token. (It's an artifact with \"{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.\")",
    ) parsesTo abilities(
        spell(doing(Effect.Counter(target("spell"))), createMutagen(), targets = listOf(slot("spell", spellFilter))),
    )

    @Test
    fun glidefinReefMarauder() = card(
        name = "Glidefin, Reef Marauder",
        cost = "{3}{U}",
        type = "Legendary Creature — Fish Mutant",
        text = """
            Flying
            When Glidefin enters, create a Mutagen token. (It's an artifact with "{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.")
            {2}, Remove a +1/+1 counter from a creature you control: Draw a card.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, createMutagen()),
        activated(cost(mana("{2}"), CostPart.RemoveCounters(p1p1, n(1), CostObjects.Choose(creatureYouControl, count = n(1)))), draw(1)),
    )

    // CR 401.4: the owner chooses top or bottom
    @Test
    fun downTheDrain() = card(
        name = "Down the Drain",
        cost = "{3}{U}",
        type = "Instant",
        text = "Target creature's owner puts it on their choice of the top or bottom of their library. You create a Mutagen token. (It's an artifact with \"{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.\")",
    ) parsesTo abilities(
        spell(
            Step.Choose(arcanum.api.Choice.Option(listOf("top", "bottom")), bind = "position", chooser = arcanum.api.Players.OwnerOf(target("creature"))),
            Step.If(Condition.Chose("position", "top"),
                then = listOf(move(target("creature"), Zone.Library, position = 0)),
                otherwise = listOf(move(target("creature"), Zone.Library, position = -1))),
            createMutagen(),
            targets = listOf(slot("creature", creature))),
    )

    // CR 509.1b: the restriction is checked as blockers are declared
    @Test
    fun pipShellBuddy() = card(
        name = "Pip, Shell Buddy",
        cost = "{2}{G}{G}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = """
            Each creature you control with a counter on it can't be blocked by more than one creature.
            Whenever Pip enters or attacks, create a Mutagen token. (It's an artifact with "{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.")
        """,
        pt = "4/4",
    ) parsesTo abilities(
        static(Mechanic.MaximumBlockers(1), affects = Objects.Each(and(creature, you, ObjectFilter.HasCounter(null, atLeast = 1)))),
        triggered(entersOrAttacksSelf, createMutagen()),
    )

    // CR 614.1a: a replacement effect for each event that puts counters
    @Test
    fun zanyOozeEnthusiast() = card(
        name = "Zany, Ooze Enthusiast",
        cost = "{1}{G}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = """
            When Zany enters, create a Mutagen token. (It's an artifact with "{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.")
            If one or more +1/+1 counters would be put on a creature you control, that many plus one +1/+1 counters are put on it instead.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        triggered(entersSelf, createMutagen()),
        static(Mechanic.Replacement(EventPattern.CountersPut(creatureYouControl, p1p1), modify = listOf(Modification.AddAmount(n(1))))),
    )

    // CR 107.3m: X is the X it was cast with; CR 601.2f: activation costs reduced
    @Test
    fun gloopLivingSludge() = card(
        name = "Gloop, Living Sludge",
        cost = "{X}{G}{G}",
        type = "Legendary Creature — Ooze Mutant",
        text = """
            Trample
            Activated abilities of artifact tokens you control cost {1} less to activate.
            When Gloop enters, create X Mutagen tokens. (They're artifacts with "{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.")
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Trample,
        static(Mechanic.CostChange(ObjectFilter.AbilityOf(kind = arcanum.api.AbilityKind.Activated, source = and(artifact, token, you)),
            CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
        triggered(entersSelf, createMutagen(Value.CastX(Objects.Self))),
    )

    @Test
    fun cascadingMutation() = card(
        name = "Cascading Mutation",
        cost = "{2}{G}",
        type = "Sorcery",
        text = "Destroy up to one target artifact, enchantment, or creature with flying. Create a Mutagen token. (It's an artifact with \"{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.\")",
    ) parsesTo abilities(
        spell(destroy(target("permanent")), createMutagen(),
            targets = listOf(slot("permanent", or(artifact, enchantment, and(creature, kw(Keyword.Flying))), min = 0))),
    )

    @Test
    fun menagerieRunaways() = card(
        name = "Menagerie Runaways",
        cost = "{1}{G}",
        type = "Creature — Boar Rhino",
        text = "When this creature leaves the battlefield, create a Mutagen token. (It's an artifact with \"{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.\")",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(diesSelfLeaves, createMutagen()),
    )

    @Test
    fun grandCroakerSwampKhan() = card(
        name = "Grand Croaker, Swamp Khan",
        cost = "{G}{U}",
        type = "Legendary Creature — Frog Mutant Rogue",
        text = """
            Trample
            Whenever Grand Croaker or another Mutant you control enters, create a Mutagen token. (It's an artifact with "{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.")
        """,
        pt = "1/3",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, mutant, you))), createMutagen()),
    )

    // CR 603.10a: the counters it had as it last existed on the battlefield
    @Test
    fun thePrimordialSludge() = card(
        name = "The Primordial Sludge",
        cost = "{2}",
        type = "Legendary Artifact",
        text = """
            Whenever a creature you control with a +1/+1 counter on it leaves the battlefield, create a Mutagen token for each +1/+1 counter on it. (A Mutagen token is an artifact with "{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.")
            {T}: Exile target card from a graveyard. Create a Mutagen token.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(and(creature, you, ObjectFilter.HasCounter(p1p1, atLeast = 1)), from = Zone.Battlefield)),
            createMutagen(Value.Of(NumericProperty.Counters(p1p1), leftOne, Combine.Single))),
        activated(cost(tap), move(target("card"), Zone.Exile, postBind = "exiled"), createMutagen(), targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
    )

    // CR 205.1b: it becomes a creature in addition to its types; CR 603.10a: the counters it had
    @Test
    fun tinkerGearheadSavant() = card(
        name = "Tinker, Gearhead Savant",
        cost = "{3}{U}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = """
            {T}: Put three +1/+1 counters on target artifact you control. If it isn't a creature, it becomes a 0/0 Robot creature in addition to its other types. Activate only as a sorcery.
            Whenever an artifact you control is put into a graveyard from the battlefield, if it had counters on it, put those counters on up to one target artifact or creature you control.
        """,
        pt = "3/5",
    ) parsesTo abilities(
        activated(cost(tap),
            counters(target("artifact"), 3),
            Step.If(Condition.ObjectMatches(target("artifact"), not(creature)), then = listOf(Step.Do(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Robot")))),
                Mechanic.SetPowerToughness(n(0), n(0))), Affected.Objects(target("artifact")), arcanum.api.Duration.Indefinitely)))),
            targets = listOf(slot("artifact", and(artifact, you))), rules = asSorcery),
        triggered(dies(and(artifact, you)),
            doing(Effect.PutCountersOf(target("permanent"), leftOne)),
            targets = listOf(slot("permanent", and(or(artifact, creature), you), min = 0)),
            interveningIf = Condition.ObjectMatches(leftOne, ObjectFilter.HasCounter(null, atLeast = 1))),
    )
}
