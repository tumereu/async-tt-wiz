package arcanum.parser.sets.tla

import arcanum.api.AbilityLabel
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunk 04: waterbend costs (CR 701.67a: for each generic mana, an untapped artifact or creature may be tapped
 * instead): of activated abilities (with {T}, exhaust), as an additional cost of a spell, mandatory or optional (CR 601.2b, 701.67b), with X;
 * what the spells do when it was paid; extra turns and control of another player's combat phase (CR 723.2).
 */
class TlaChunk04WaterbendCostsTest {
    private fun additionalWaterbend(cost: String, optional: Boolean = false) = static(Mechanic.AdditionalCost(cost = cost(waterbend(cost)), optional = optional))

    // CR 701.67a
    @Test
    fun tideclanRallier() = card(
        name = "Tideclan Rallier",
        cost = "{1}{W}",
        type = "Creature — Human Soldier Ally",
        text = "Waterbend {5}: Look at the top four cards of your library. You may reveal a creature card with power 3 or less from among them and put it into your hand. Put the rest on the bottom of your library in a random order. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)",
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(waterbend("{5}")),
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(and(creature, power(Comparison.AtMost, 3)), n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    // CR 601.2b, 701.67b: waterbend as an additional cost
    @Test
    fun kindlyBrookSpirit() = card(
        name = "Kindly Brook Spirit",
        cost = "{U}{U}",
        type = "Creature — Spirit",
        text = """
            As an additional cost to cast this spell, waterbend {5}. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
            Flying, ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
            When this creature enters, scry 2.
        """,
        pt = "4/5",
    ) parsesTo abilities(
        additionalWaterbend("{5}"),
        Keyword.Flying,
        Keyword.Ward(manaCost("{2}")),
        triggered(entersSelf, scry(2)),
    )

    // CR 107.3 (X announced for the waterbend cost), 608.2d (counters distributed among chosen creatures), 122.1d
    @Test
    fun breakingSurge() = card(
        name = "Breaking Surge",
        cost = "{U}{U}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, waterbend {X}. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
            Tap up to X target creatures, then distribute three stun counters among any number of tapped creatures your opponents control. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """,
    ) parsesTo abilities(
        additionalWaterbend("{X}"),
        spell(
            tapIt(target("creatures")),
            Step.Choose(Choice.Objects(and(tapped, creature, opponents), n(1), n(3)), bind = "stunned", chooser = Players.You),
            doing(Effect.PutCounters(Objects.Bound("stunned"), CounterKind.Stun, n(3), distributed = true)),
            targets = listOf(TargetSlot("creatures", TargetKind.Objects(creature), min = n(0), max = x)),
            announced = listOf("X")),
    )

    // CR 613.4b
    @Test
    fun suppleTideshaper() = card(
        name = "Supple Tideshaper",
        cost = "{3}{U}",
        type = "Creature — Human Warrior Ally",
        text = """
            Vigilance
            Waterbend {3}: This creature has base power and toughness 5/2 until end of turn. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """,
        pt = "2/5",
    ) parsesTo abilities(
        Keyword.Vigilance,
        activated(cost(waterbend("{3}")), untilEndOfTurn(Objects.Self, Mechanic.SetPowerToughness(n(5), n(2)))),
    )

    @Test
    fun spoutJumper() = card(
        name = "Spout Jumper",
        cost = "{4}{U}",
        type = "Creature — Human Warrior Ally",
        text = """
            Flying
            Waterbend {4}: Draw a card, then discard a card. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """,
        pt = "4/3",
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(waterbend("{4}")), draw(1), discard(1)),
    )

    // CR 702.29e
    @Test
    fun colossalCarp() = card(
        name = "Colossal Carp",
        cost = "{4}{U}{U}",
        type = "Creature — Fish",
        text = """
            Waterbend {3}: This creature can't be blocked this turn. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
            Islandcycling {2} ({2}, Discard this card: Search your library for an Island card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "5/7",
    ) parsesTo abilities(
        activated(cost(waterbend("{3}")), untilEndOfTurn(Objects.Self, Mechanic.CantBeBlocked)),
        Keyword.Typecycling(sub("Island"), "Islandcycling", manaCost("{2}")),
    )

    // CR 702.177a: an exhaust ability with a waterbend cost; "becomes an artifact creature" for good (layer 4)
    @Test
    fun siegeDivingBell() = card(
        name = "Siege Diving Bell",
        cost = "{2}{U}",
        type = "Artifact — Vehicle",
        text = """
            When this Vehicle enters, return up to one other target nonland permanent to its owner's hand.
            Exhaust — Waterbend {3}: This Vehicle becomes an artifact creature. Put three +1/+1 counters on it. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}. Activate each exhaust ability only once.)
        """,
        pt = "0/0",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("permanent"), Zone.Hand), targets = listOf(slot("permanent", and(another, nonland, permanentFilter), min = 0))),
        activated(cost(waterbend("{3}")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature))), Affected.Objects(Objects.Self), Duration.Indefinitely)),
            counters(Objects.Self, 3),
            label = AbilityLabel.Exhaust),
    )

    // CR 603.4
    @Test
    fun nerysShapingProdigy() = card(
        name = "Nerys, Shaping Prodigy",
        cost = "{2}{U}",
        type = "Legendary Creature — Human Warrior Ally",
        text = """
            At the beginning of your end step, if Nerys is tapped, put a +1/+1 counter on her.
            Waterbend {6}: Draw a card. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """,
        pt = "2/3",
    ) parsesTo abilities(
        triggered(yourEndStep, counters(Objects.Self), interveningIf = arcanum.api.Condition.ObjectMatches(Objects.Self, tapped)),
        activated(cost(waterbend("{6}")), draw(1)),
    )

    // CR 714.2c, 500.7 (extra turn), 702.177a
    @Test
    fun theTaleOfOsric() = cardFaces(Layout.Transform,
        card(
            name = "The Tale of Osric",
            cost = "{2}{U}{U}",
            type = "Enchantment — Saga",
            text = """
                (As this Saga enters and after your draw step, add a lore counter.)
                I, II — Scry 2, then draw a card.
                III — Exile this Saga, then return it to the battlefield transformed under your control.
            """,
        ) to abilities(
            chapters(listOf(1, 2), scry(2), draw(1)),
            exileAndReturnTransformed,
        ),
        card(
            name = "Herald Osric",
            cost = null,
            type = "Legendary Creature — Avatar",
            text = """
                Whenever you cast a spell, create a 1/1 colorless Spirit creature token with "This token can't block or be blocked by non-Spirit creatures."
                Exhaust — Waterbend {20}: Take an extra turn after this one. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}. Activate each exhaust ability only once.)
            """,
            pt = "4/3",
        ) to abilities(
            triggered(youCast(ObjectFilter.Any), create(spiritToken)),
            activated(cost(waterbend("{20}")), doing(Effect.ExtraTurn(Players.You)), label = AbilityLabel.Exhaust),
        ),
    )

    @Test
    fun northernIcePatrol() = card(
        name = "Northern Ice Patrol",
        cost = "{2}{U}",
        type = "Creature — Human Soldier Ally",
        text = """
            {T}: Untap another target permanent you control.
            Waterbend {3}, {T}: Tap target creature an opponent controls. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """,
        pt = "2/3",
    ) parsesTo abilities(
        activated(cost(tap), untapIt(target("permanent")), targets = listOf(slot("permanent", and(another, permanentFilter, you)))),
        activated(cost(waterbend("{3}"), tap), tapIt(target("creature")), targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 601.2b (an optional additional cost), 723.1, 723.2
    @Test
    fun secretOfVeinshaping() = card(
        name = "Secret of Veinshaping",
        cost = "{U}{U}{U}{U}",
        type = "Sorcery — Lesson",
        text = """
            As an additional cost to cast this spell, you may waterbend {10}.
            You control target opponent during their next combat phase. If this spell's additional cost was paid, you control that player during their next turn instead. (You see all cards that player could see and make all decisions for them.)
            Exile Secret of Veinshaping.
        """,
    ) parsesTo abilities(
        additionalWaterbend("{10}", optional = true),
        spell(
            Step.If(additionalPaid, then = listOf(doing(Effect.ControlNextTurn(Players.Target("opponent"), Players.You))),
                otherwise = listOf(doing(Effect.ControlNextTurn(Players.Target("opponent"), Players.You, combatPhase = true)))),
            move(Objects.Self, Zone.Exile),
            targets = listOf(targetOpponent)),
    )

    // CR 601.2b, 402.2 (no maximum hand size)
    @Test
    fun holySpringRevival() = card(
        name = "Holy Spring Revival",
        cost = "{1}{U}{U}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, you may waterbend {6}. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
            Draw two cards. If this spell's additional cost was paid, instead shuffle your graveyard into your library, draw seven cards, and you have no maximum hand size for the rest of the game.
            Exile Holy Spring Revival.
        """,
    ) parsesTo abilities(
        additionalWaterbend("{6}", optional = true),
        spell(
            Step.If(additionalPaid, then = listOf(
                doing(Effect.Move(Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))), Destination(Zone.Library))),
                shuffle,
                draw(7),
                doing(Effect.ApplyMechanics(listOf(Mechanic.NoMaximumHandSize), Affected.Players(Players.You), Duration.Indefinitely))),
                otherwise = listOf(draw(2))),
            move(Objects.Self, Zone.Exile)),
    )
}
