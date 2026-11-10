package arcanum.parser.sets.fdn

import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 11a of FDN: put / return / reveal wordings of eight cards. Invented names, the real oracle text. */
class FdnChunk11aPutReturnTest {
    // CR 603.2 (trigger), 115.1 ("other": not this creature), 115.4 (up to two targets), 122.1
    @Test
    fun felidarSavior() = card(
        name = "Lanternlit Savior",
        cost = "{3}{W}",
        type = "Creature — Cat Beast",
        pt = "2/3",
        text = """
            Lifelink (Damage dealt by this creature also causes you to gain that much life.)
            When this creature enters, put a +1/+1 counter on each of up to two other target creatures you control.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(entersSelf, counters(target("creatures"), 1), targets = listOf(slot("creatures", and(creature, you, another), min = 0, max = 2))),
    )

    // CR 701.20a (reveal), 400.7 (new objects), 107.3 (X in the spell), 202.3 (mana value)
    @Test
    fun genesisWave() = card(
        name = "Tidal Surge Wave",
        cost = "{X}{G}{G}{G}",
        type = "Sorcery",
        text = "Reveal the top X cards of your library. You may put any number of permanent cards with mana value X or less from among them onto the battlefield. Then put all cards revealed this way that weren't put onto the battlefield into your graveyard.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Reveal(Objects.LibraryCards(Players.You, Value.Announced(name = "X"), fromTop = true), bind = "revealed")),
            Step.Choose(Choice.Objects(and(permanentCard, ObjectFilter.ManaValue(Comparison.AtMost, Value.Announced(name = "X"))), n(0), null,
                from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield),
            move(Objects.Except(Objects.Bound("revealed"), Objects.Bound("chosen")), Zone.Graveyard),
            announced = listOf("X"),
        ),
    )

    // CR 603.2 (cast trigger), 105.1 (colors), 122.1: one counter for each color of the spell (a colorless spell has none)
    @Test
    fun ramosCounters() = card(
        name = "Prismara, Dragon Engine",
        cost = "{6}",
        type = "Legendary Artifact Creature — Dragon",
        pt = "4/4",
        text = "Whenever you cast a spell, put a +1/+1 counter on Prismara for each of that spell's colors.",
    ) parsesTo abilities(
        triggered(youCast(ObjectFilter.Any),
            counters(Objects.Self, Value.SharedColors(Objects.EventObject(EventRole.Object), listOf(ColorRef.Of(Objects.EventObject(EventRole.Object)))))),
    )

    // CR 400.7 (each card is a new object), 108.3 (owner), 110.2 (you control them)
    @Test
    fun riseOfTheDarkRealms() = card(
        name = "Grave Dominion Rite",
        cost = "{7}{B}{B}",
        type = "Sorcery",
        text = "Put all creature cards from all graveyards onto the battlefield under your control.",
    ) parsesTo abilities(
        spell(move(Objects.Each(and(creature, inZone(Zone.Graveyard))), Zone.Battlefield, controller = Players.You)),
    )

    // CR 603.2, 701.20a (reveal until), 401.4 (random order): the optional reveal holds the whole sequence
    @Test
    fun spinnerOfSouls() = card(
        name = "Soulweb Spinner",
        cost = "{2}{G}",
        type = "Creature — Spider Spirit",
        pt = "4/3",
        text = """
            Reach
            Whenever another nontoken creature you control dies, you may reveal cards from the top of your library until you reveal a creature card. Put that card into your hand and the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(on(EventPattern.ZoneChange(and(another, not(token), creature, you), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.May(listOf(
                doing(Effect.RevealUntil(creature, Players.You, n(1), reveal = true, foundBind = "found", restBind = "rest")),
                move(Objects.Bound("found"), Zone.Hand),
                Step.Do(Effect.Move(Objects.Bound("rest"), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))), player = Players.You)),
    )

    // CR 603.2, 115.1 ("another"), 608.2h (its power as it last existed), 202.3
    @Test
    fun fiendishPanda() = card(
        name = "Spiteful Panda",
        cost = "{2}{W}{B}",
        type = "Creature — Bear Demon",
        pt = "3/2",
        text = """
            Whenever you gain life, put a +1/+1 counter on this creature.
            When this creature dies, return another target non-Bear creature card with mana value less than or equal to this creature's power from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.GainLife(PlayerFilter.You)), counters(Objects.Self, 1)),
        triggered(diesSelf, move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", and(not(sub("Bear")), creature, ObjectFilter.ManaValue(Comparison.AtMost, power(Objects.Self)),
                *yourGraveyard.toTypedArray(), not(ObjectFilter.Is(Objects.SelfAfter)))))),
    )

    // CR 115.1 (target player), 400.3 (owner's hand), 109.1
    @Test
    fun riversRebuke() = card(
        name = "Floodtide Rebuke",
        cost = "{4}{U}{U}",
        type = "Sorcery",
        text = "Return all nonland permanents target player controls to their owner's hand.",
    ) parsesTo abilities(
        spell(move(Objects.Each(and(nonland, permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player"))))), Zone.Hand),
            targets = listOf(targetPlayer)),
    )

    // CR 602.2 (activated), 701.20 (look, reveal), 400.7, 401.4: the rest to the bottom in a random order
    @Test
    fun lootExuberantExplorer() = card(
        name = "Foxfire, Eager Explorer",
        cost = "{2}{G}",
        type = "Legendary Creature — Beast Noble",
        pt = "1/4",
        text = "{4}{G}{G}, {T}: Look at the top six cards of your library. You may reveal a creature card with mana value less than or equal to the number of lands you control from among them and put it onto the battlefield. Put the rest on the bottom in a random order.",
    ) parsesTo abilities(
        activated(cost(mana("{4}{G}{G}"), tap),
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(6), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(and(creature, ObjectFilter.ManaValue(Comparison.AtMost, count(and(land, you)))), n(0), n(1), from = Objects.Bound("seen")),
                bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Battlefield),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )
}
