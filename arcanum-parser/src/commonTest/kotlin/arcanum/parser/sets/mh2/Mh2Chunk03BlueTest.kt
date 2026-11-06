package arcanum.parser.sets.mh2

import arcanum.api.Ability
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaPaymentWay
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Modern Horizons 2 chunk 03: blue cards, first part: switching power and toughness (CR 613.4d), "whenever you sacrifice a token",
 * creatures losing all abilities (CR 613.1f) with a sacrifice at the next end step, a spell's mana value in a cast trigger, a
 * characteristic-defining power (CR 604.3), investigate (CR 701.16), delirium (CR 207.2c), cycling's "when you cycle" (CR 702.29c),
 * channel with X targets (CR 107.3a), suspend on a search of an opponent's library (CR 701.23), affinity for tokens (CR 702.41),
 * replicate (CR 702.56), counting card types in a graveyard, delve's exiled cards (CR 702.66) and "becomes the target" (CR 115.10).
 */
class Mh2Chunk03BlueTest {
    // CR 613.4d: switching is layer 7d
    @Test
    fun skyshiftProtist() = card(
        name = "Skyshift Protist",
        cost = "{3}{U}",
        type = "Creature — Elemental Beast",
        text = """
            Flying
            Discard a card: Switch this creature's power and toughness until end of turn.
        """,
        pt = "2/4",
    ) parsesTo abilities(Keyword.Flying, activated(cost(discardACard), untilEndOfTurn(Objects.Self, Mechanic.SwitchPowerToughness)))

    // CR 701.21a: a token you sacrifice
    @Test
    fun ladenSkyrigger() = card(
        name = "Laden Skyrigger",
        cost = "{2}{U}",
        type = "Creature — Human Pirate",
        text = """
            When this creature enters, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            Whenever you sacrifice a token, this creature gains flying until end of turn.
        """,
        pt = "3/1",
    ) parsesTo abilities(
        triggered(entersSelf, createTreasure),
        triggered(on(EventPattern.Sacrifice(and(token, you))), gains(Objects.Self, Keyword.Flying)),
    )

    // CR 613.1f: losing all abilities is layer 6
    @Test
    fun strippedBare() = card(
        name = "Stripped Bare",
        cost = "{1}{U}",
        type = "Enchantment",
        text = """
            Flash
            When this enchantment enters, draw a card.
            Creatures lose all abilities.
            At the beginning of the end step, sacrifice this enchantment.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, draw(1)),
        static(Mechanic.LoseAbilities(), affects = Objects.Each(creature)),
        triggered(stepBegins(TurnStep.End, PlayerFilter.Any), doing(Effect.Sacrifice(Objects.Self))),
    )

    @Test
    fun etheriumWeaver() = card(
        name = "Etherium Weaver",
        cost = "{2}{U}",
        type = "Artifact Creature — Human Wizard",
        text = "Whenever you cast a spell with mana value 4 or greater, create a 1/1 colorless Thopter artifact creature token with flying.",
        pt = "2/1",
    ) parsesTo abilities(triggered(youCast(and(spellFilter, manaValue(Comparison.AtLeast, 4))), create(thopter)))

    // CR 604.3: a characteristic-defining ability
    @Test
    fun filigreeSteward() = card(
        name = "Filigree Steward",
        cost = "{2}{U}{U}",
        type = "Artifact Creature — Homunculus",
        text = """
            Flying
            Filigree Steward's power is equal to the number of artifacts you control.
        """,
        pt = "*/3",
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.SetPowerToughness(count(and(artifact, you)), null), characteristicDefining = true),
    )

    @Test
    fun tidehound() = card(
        name = "Tidehound",
        cost = "{U}",
        type = "Creature — Elemental Dog",
        text = "{3}, {T}: Investigate. (Create a Clue token. It's an artifact with \"{2}, Sacrifice this token: Draw a card.\")",
        pt = "1/2",
    ) parsesTo abilities(activated(cost(mana("{3}"), tap), investigate))

    // CR 207.2c: delirium is an ability word
    @Test
    fun fetidSentinel() = card(
        name = "Fetid Sentinel",
        cost = "{1}{U}",
        type = "Creature — Nightmare Bird",
        text = """
            Flying
            When this creature enters, surveil 1. (Look at the top card of your library. You may put that card into your graveyard.)
            Delirium — This creature gets +1/+0 as long as there are four or more card types among cards in your graveyard.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, surveil(1)),
        static(pt(1, 0), condition = delirium).copy(abilityWord = "Delirium"),
    )

    // CR 702.29c: "when you cycle this card"
    @Test
    fun shatteredReason() = card(
        name = "Shattered Reason",
        cost = "{U}{U}{U}",
        type = "Sorcery",
        text = """
            Each opponent mills fourteen cards.
            Cycling {1}{U} ({1}{U}, Discard this card: Draw a card.)
            When you cycle this card, each opponent mills four cards.
        """,
    ) parsesTo abilities(
        spell(mill(14, eachOpponentPlayers)),
        Keyword.Cycling(manaCost("{1}{U}")),
        triggered(on(EventPattern.Cycle(isSelf, PlayerFilter.You)), mill(4, eachOpponentPlayers)),
    )

    // CR 107.3a: X target creatures, X announced as the channel ability is activated from the hand
    @Test
    fun lanternlitDrifter() = card(
        name = "Lanternlit Drifter",
        cost = "{2}{U}",
        type = "Creature — Spirit",
        text = """
            Flying
            {2}{U}: Another target creature gains flying until end of turn.
            Channel — {X}{U}, Discard this card: X target creatures gain flying until end of turn.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        activated(cost(mana("{2}{U}")), gains(target("creature"), Keyword.Flying), targets = listOf(slot("creature", and(another, creature)))),
        Ability.Activated(
            cost(mana("{X}{U}"), CostPart.Discard(CostObjects.Self)),
            Instructions(
                targets = listOf(TargetSlot("creatures", TargetKind.Objects(creature), min = x, max = x)),
                steps = listOf(gains(target("creatures"), Keyword.Flying)),
                announced = listOf("X"),
            ),
            anyTime,
            abilityWord = "Channel",
        ),
    )

    @Test
    fun firmTestimony() = card(
        name = "Firm Testimony",
        cost = "{U}",
        type = "Sorcery",
        text = """
            Create a 0/3 blue Crab creature token.
            Investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
    ) parsesTo abilities(spell(create(crab), investigate))

    // CR 701.23a: you search another player's library; that player shuffles
    @Test
    fun foreseenTreachery() = card(
        name = "Foreseen Treachery",
        cost = null,
        type = "Sorcery",
        text = """
            Suspend 3—{1}{U}{U} (Rather than cast this card from your hand, pay {1}{U}{U} and exile it with three time counters on it. At the beginning of your upkeep, remove a time counter. When the last is removed, you may cast it without paying its mana cost.)
            Search target opponent's library for a creature card and put that card onto the battlefield under your control. Then that player shuffles.
        """,
    ) parsesTo abilities(
        Keyword.Suspend(3, manaCost("{1}{U}{U}")),
        spell(
            doing(Effect.Search(Players.Target("opponent"), creature, max = n(1), zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            move(Objects.Bound("found"), Zone.Battlefield, controller = Players.You),
            doing(Effect.Shuffle(Players.Target("opponent"))),
            targets = listOf(targetOpponent),
        ),
    )

    // CR 702.41a affinity for tokens
    @Test
    fun scrapSerpent() = card(
        name = "Scrap Serpent",
        cost = "{5}{U}{U}",
        type = "Creature — Serpent",
        text = """
            Affinity for tokens (This spell costs {1} less to cast for each token you control.)
            Whenever a token you control enters, tap target nonland permanent an opponent controls. It doesn't untap during its controller's next untap step.
        """,
        pt = "5/6",
    ) parsesTo abilities(
        Keyword.Affinity(token, "tokens"),
        triggered(enters(and(token, you)), tapIt(target("permanent")), doesntUntapNext(target("permanent")),
            targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents)))),
    )

    // CR 702.56a replicate
    @Test
    fun lapseOfAttention() = card(
        name = "Lapse of Attention",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Replicate {U} (When you cast this spell, copy it for each time you paid its replicate cost. You may choose new targets for the copies.)
            Counter target spell unless its controller pays {2}.
        """,
    ) parsesTo abilities(
        Keyword.Replicate(manaCost("{U}")),
        spell(*counterUnless("{2}").toTypedArray(), targets = listOf(slot("spell", spellFilter))),
    )

    // CR 205.2a: the card types among the cards
    @Test
    fun wakingVisions() = card(
        name = "Waking Visions",
        cost = "{3}{U}{U}",
        type = "Sorcery",
        text = "Draw X cards, where X is the number of card types among cards in your graveyard.",
    ) parsesTo abilities(spell(doing(Effect.Draw(Players.You, Value.CountDistinct(yourGraveyardCards, SharedQuality.CardType)))))

    @Test
    fun innerVoyage() = card(
        name = "Inner Voyage",
        cost = "{4}{U}{U}",
        type = "Instant",
        text = """
            Draw three cards.
            Basic landcycling {1}{U} ({1}{U}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(spell(draw(3)), basicLandcycling("{1}{U}"))

    // CR 702.66a: the cards exiled for delve; CR 614.1c: it enters with counters
    @Test
    fun murkwaterTyrant() = card(
        name = "Murkwater Tyrant",
        cost = "{5}{U}{U}",
        type = "Creature — Dragon",
        text = """
            Delve (Each card you exile from your graveyard while casting this spell pays for {1}.)
            Flying
            This creature enters with a +1/+1 counter on it for each instant and sorcery card exiled with it.
            Whenever an instant or sorcery card leaves your graveyard, put a +1/+1 counter on this creature.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Delve,
        Keyword.Flying,
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.EntersWithCounters(p1p1,
            Value.Count(Objects.Where(Objects.PaidWith(ManaPaymentWay.ExileFromGraveyard, Objects.Self), or(instant, sorcery))))))),
        triggered(on(EventPattern.ZoneChange(and(or(instant, sorcery), ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Graveyard)),
            counters(Objects.Self)),
    )

    @Test
    fun arcaneErasure() = card(
        name = "Arcane Erasure",
        cost = "{2}{U}",
        type = "Enchantment",
        text = """
            At the beginning of your upkeep, scry 1.
            Whenever you discard a card, each opponent mills two cards. (They put the top two cards of their library into their graveyard.)
        """,
    ) parsesTo abilities(
        triggered(yourUpkeep, scry(1)),
        triggered(on(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.You)), mill(2, eachOpponentPlayers)),
    )

    @Test
    fun packageMyr() = card(
        name = "Package Myr",
        cost = "{1}{U}",
        type = "Artifact Creature — Clue Myr",
        text = "{2}, Sacrifice this creature: Draw a card.",
        pt = "2/1",
    ) parsesTo abilities(activated(cost(mana("{2}"), sacrificeSelf), draw(1)))

    // CR 115.10: becoming the target of any spell or ability
    @Test
    fun illusoryDreadjaw() = card(
        name = "Illusory Dreadjaw",
        cost = "{2}{U}{U}",
        type = "Creature — Dinosaur Illusion",
        text = """
            Trample
            When this creature becomes the target of a spell or ability, sacrifice it.
        """,
        pt = "6/6",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(on(EventPattern.BecomesTarget(isSelf, by = ObjectFilter.Any)), doing(Effect.Sacrifice(Objects.Self))),
    )
}
