package arcanum.parser.sets.mh2

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Modern Horizons 2 chunk 04: blue cards, second part: a delirium activation condition, "if you've discarded a card this turn", overload on
 * a type-changing spell (CR 702.96, 613.1d), landwalk (CR 702.14), a split card (CR 709), a library position (CR 401.4), an Aura's
 * intervening if, a token copy (CR 707.2), typecycling, a spell put on top or bottom by its owner, a creature exiled with time counters
 * gaining suspend (CR 702.62), conditional indestructible and ward for other Merfolk (CR 702.21), converge, kicker with a land type
 * change for as long as it stays (CR 305.7, 611.2b) and metalcraft.
 */
class Mh2Chunk04BlueTest {
    @Test
    fun frenziedSeer() = card(
        name = "Frenzied Seer",
        cost = "{1}{U}",
        type = "Creature — Merfolk Wizard",
        text = """
            {U}, {T}: Draw a card, then discard a card.
            Delirium — {2}{U}, {T}: Draw a card. Activate only if there are four or more card types among cards in your graveyard.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        activated(cost(mana("{U}"), tap), draw(1), discard(1)),
        activated(cost(mana("{2}{U}"), tap), draw(1), rules = anyTime.copy(condition = delirium)).copy(abilityWord = "Delirium"),
    )

    // CR 608.2c: the condition is checked as the spell resolves
    @Test
    fun readjust() = card(
        name = "Readjust",
        cost = "{1}{U}",
        type = "Instant",
        text = "Return target creature to its owner's hand. If you've discarded a card this turn, draw a card.",
    ) parsesTo abilities(
        spell(move(target("creature"), Zone.Hand),
            Step.If(Condition.HappenedThisTurn(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.You), atLeast = 1, measure = Measure.Events), then = listOf(draw(1))),
            targets = listOf(slot("creature", creature))),
    )

    // CR 702.96b: overloaded, "target" becomes "each"; CR 611.2c: the affected artifacts are fixed as the spell resolves
    @Test
    fun wakeAndStir() = card(
        name = "Wake and Stir",
        cost = "{1}{U}",
        type = "Sorcery",
        text = """
            Target noncreature artifact you control becomes a 0/0 artifact creature. Put four +1/+1 counters on each artifact that became a creature this way.
            Overload {4}{U}{U} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        spell(
            Step.Bind(target("artifact"), "became"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)), Mechanic.SetPowerToughness(n(0), n(0))),
                arcanum.api.Affected.Objects(Objects.Bound("became")), Duration.Indefinitely)),
            counters(Objects.Bound("became"), 4),
            targets = listOf(slot("artifact", and(noncreature, artifact, you))),
        ),
        Keyword.Overload(manaCost("{4}{U}{U}"), Instructions(steps = listOf(
            Step.Bind(Objects.Each(and(noncreature, artifact, you)), "became"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)), Mechanic.SetPowerToughness(n(0), n(0))),
                arcanum.api.Affected.Objects(Objects.Bound("became")), Duration.Indefinitely)),
            counters(Objects.Bound("became"), 4),
        ))),
    )

    // CR 702.14c islandwalk
    @Test
    fun rishadanDeckhand() = card(
        name = "Rishadan Deckhand",
        cost = "{U}",
        type = "Creature — Merfolk",
        text = """
            Islandwalk (This creature can't be blocked as long as defending player controls an Island.)
            {1}, {T}: Tap target land.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.Landwalk(sub("Island"), "Islandwalk"),
        activated(cost(mana("{1}"), tap), tapIt(target("land")), targets = listOf(slot("land", land))),
    )

    // CR 709.3: each half is cast on its own
    @Test
    fun spokenFinished() = cardFaces(
        Layout.Split,
        card(name = "Spoken", cost = "{2}{U}", type = "Sorcery", text = "Return target instant or sorcery card from your graveyard to your hand.") to abilities(
            spell(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(or(instant, sorcery))))),
        ),
        card(name = "Finished", cost = "{3}{U}", type = "Instant",
            text = "Tap up to two target creatures. Those creatures don't untap during their controller's next untap step.") to abilities(
            spell(tapIt(target("creatures")), doesntUntapNext(target("creatures")), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
        ),
    )

    @Test
    fun scuttlesurge() = card(
        name = "Scuttlesurge",
        cost = "{1}{U}",
        type = "Enchantment",
        text = """
            {1}, Discard a card: Create a 0/3 blue Crab creature token.
            Delirium — Crabs you control get +1/+1 as long as there are four or more card types among cards in your graveyard.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}"), discardACard), create(crab)),
        static(pt(1, 1), affects = Objects.Each(and(sub("Crab"), you)), condition = delirium).copy(abilityWord = "Delirium"),
    )

    // CR 401.4: third from the top is position 2
    @Test
    fun fracturedPride() = card(
        name = "Fractured Pride",
        cost = "{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets -3/-0.
            {3}{U}{U}: Put enchanted creature into its owner's library third from the top.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(pt(-3, 0), affects = equipped),
        activated(cost(mana("{3}{U}{U}")), move(equipped, Zone.Library, position = 2)),
    )

    // CR 603.4: an intervening "if"
    @Test
    fun suchSparkle() = card(
        name = "Such Sparkle",
        cost = "{2}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, if you control a token, tap enchanted creature, then scry 2.
            Enchanted creature doesn't untap during its controller's untap step.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        triggered(entersSelf, tapIt(equipped), scry(2), interveningIf = Condition.Exists(and(token, you), atLeast = 1)),
        static(Mechanic.DoesntUntap, affects = equipped),
    )

    // CR 707.2: a token that's a copy of another token
    @Test
    fun sampleHoarder() = card(
        name = "Sample Hoarder",
        cost = "{4}{U}",
        type = "Creature — Vedalken Wizard",
        text = """
            When this creature enters, create a 1/1 green Squirrel creature token and a 0/3 blue Crab creature token.
            When this creature dies, create a token that's a copy of target token you control.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        triggered(entersSelf, create(squirrel), create(crab)),
        triggered(diesSelf, create(TokenSpec.CopyOf(target("token"))), targets = listOf(slot("token", and(token, you)))),
    )

    @Test
    fun ironfinLeviathan() = card(
        name = "Ironfin Leviathan",
        cost = "{5}{U}",
        type = "Creature — Whale",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            Whenever an artifact you control enters, untap this creature.
        """,
        pt = "3/4",
    ) parsesTo abilities(Keyword.Affinity(artifact, "artifacts"), triggered(enters(and(artifact, you)), untapIt(Objects.Self)))

    @Test
    fun walkBetween() = card(
        name = "Walk Between",
        cost = "{3}{U}{U}",
        type = "Sorcery",
        text = """
            Return two target creatures to their owners' hands.
            Wizardcycling {2} ({2}, Discard this card: Search your library for a Wizard card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        spell(move(target("creatures"), Zone.Hand), targets = listOf(slot("creatures", creature, min = 2, max = 2))),
        Keyword.Typecycling(sub("Wizard"), "Wizardcycling", manaCost("{2}")),
    )

    // CR 401.4: its owner chooses top or bottom
    @Test
    fun finesse() = card(
        name = "Finesse",
        cost = "{2}{U}{U}",
        type = "Creature — Elemental Incarnation",
        text = """
            Flash
            Flying
            When this creature enters, choose up to one target creature spell or planeswalker spell. Its owner puts it on their choice of the top or bottom of their library.
            Evoke—Exile a blue card from your hand.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(entersSelf,
            Step.Choose(Choice.Option(listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(target("spell"))),
            Step.If(Condition.Chose("position", "top"), then = listOf(move(target("spell"), Zone.Library, position = 0)),
                otherwise = listOf(move(target("spell"), Zone.Library, position = -1))),
            targets = listOf(slot("spell", and(or(and(creature, spellFilter), planeswalker), spellFilter), min = 0))),
        Keyword.Evoke(Cost(listOf(CostPart.Exile(CostObjects.Choose(ObjectFilter.OfColor(arcanum.api.Color.Blue), count = n(1)), from = Zone.Hand)))),
    )

    // CR 702.62b: a card in exile with suspend and a time counter is suspended; one without suspend gains it
    @Test
    fun holdInAbeyance() = card(
        name = "Hold in Abeyance",
        cost = "{U}",
        type = "Instant",
        text = "Exile target creature and put two time counters on it. If it doesn't have suspend, it gains suspend. (At the beginning of its owner's upkeep, they remove a time counter. When the last is removed, they may play it without paying its mana cost. If it's a creature, it has haste.)",
    ) parsesTo abilities(
        spell(
            move(target("creature"), Zone.Exile, postBind = "exiled"),
            counters(Objects.Bound("exiled"), 2, CounterKind.Time),
            Step.If(Condition.Not(Condition.ObjectMatches(Objects.Bound("exiled"), kw(Keyword.Suspend.Kind))), then = listOf(
                doing(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Suspend(0, Cost(emptyList()))))),
                    arcanum.api.Affected.Objects(Objects.Bound("exiled")), Duration.Indefinitely)))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 702.21a ward
    @Test
    fun ysvelinOfTideAndSky() = card(
        name = "Ysvelin of Tide and Sky",
        cost = "{1}{U}{U}",
        type = "Legendary Creature — Merfolk God",
        text = """
            Ysvelin has indestructible as long as you control at least two other Merfolk.
            Whenever Ysvelin attacks, draw a card.
            Other Merfolk you control have ward {1}. (Whenever another Merfolk you control becomes the target of a spell or ability an opponent controls, counter it unless that player pays {1}.)
        """,
        pt = "3/4",
    ) parsesTo abilities(
        static(has(Keyword.Indestructible), condition = Condition.Exists(and(another, sub("Merfolk"), you), atLeast = 2)),
        triggered(attacksSelf, draw(1)),
        static(has(Keyword.Ward(manaCost("{1}"))), affects = Objects.Each(and(another, sub("Merfolk"), you))),
    )

    // CR 601.2h: the colours of mana spent to cast it
    @Test
    fun clearTheHeavens() = card(
        name = "Clear the Heavens",
        cost = "{X}{U}{U}",
        type = "Sorcery",
        text = "Converge — Create a 1/1 colorless Thopter artifact creature token with flying for each color of mana spent to cast this spell.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(create(thopter, count = Value.ManaSpent(distinctColors = true, of = Objects.Self))), announced = listOf("X")),
            abilityWord = "Converge"),
    )

    @Test
    fun mindOverseer() = card(
        name = "Mind Overseer",
        cost = "{6}{U}",
        type = "Artifact Creature — Construct",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            Flying
            When this creature enters, draw two cards.
        """,
        pt = "2/2",
    ) parsesTo abilities(Keyword.Affinity(artifact, "artifacts"), Keyword.Flying, triggered(entersSelf, draw(2)))

    // CR 305.7: becoming an Island replaces its land types; CR 611.2b: for as long as this creature stays
    @Test
    fun currentSculptor() = card(
        name = "Current Sculptor",
        cost = "{U}",
        type = "Creature — Merfolk Wizard",
        text = """
            Kicker {1} (You may pay an additional {1} as you cast this spell.)
            When this creature enters, if it was kicked, target land becomes an Island for as long as this creature remains on the battlefield.
            This creature gets +1/+1 as long as an opponent controls an Island.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{1}"))),
        triggered(entersSelf,
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Island"))), replaceSubtypes = true)),
                arcanum.api.Affected.Objects(target("land")), Duration.UntilLeaves(Objects.Self))),
            targets = listOf(slot("land", land)), interveningIf = kicked),
        static(pt(1, 1), condition = Condition.Exists(and(sub("Island"), opponents), atLeast = 1)),
    )

    // CR 207.2c metalcraft
    @Test
    fun vedalkenInterloper() = card(
        name = "Vedalken Interloper",
        cost = "{1}{U}",
        type = "Creature — Vedalken Rogue",
        text = """
            This creature can't be blocked.
            Metalcraft — This creature gets +1/+0 as long as you control three or more artifacts.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        static(Mechanic.CantBeBlocked),
        static(pt(1, 0), condition = Condition.Exists(and(artifact, you), atLeast = 3)).copy(abilityWord = "Metalcraft"),
    )
}
