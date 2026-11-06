package arcanum.parser.sets.dft

import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
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

/**
 * Aetherdrift chunk 10: cycling (CR 702.29) on white and blue cards and the colourless land: "when you cycle this card" (CR 702.29c) with
 * the X of a cycling {X} cost (CR 107.3a), losing indestructible before a wipe (CR 613.1f), stun counters (CR 122.1d), exchanging control
 * (CR 701.12), top or bottom of the library, entering as a copy (CR 707.9), and a search of three zones for a chosen name (CR 701.23).
 */
class DftChunk10CyclingWhiteBlueTest {
    @Test
    fun basraRiderOfDawn() = card(
        name = "Basra, Rider of Dawn",
        cost = "{W}",
        type = "Legendary Creature — Human Knight",
        text = """
            {W}, {T}, Exert Basra: Create a 1/1 white Cat creature token with lifelink. (An exerted creature won't untap during your next untap step.)
            Cycling {2}{W} ({2}{W}, Discard this card: Draw a card.)
            When you cycle this card, Cats you control gain hexproof and indestructible until end of turn.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        activated(cost(mana("{W}"), tap, CostPart.Action(KeywordAction.Exert(Objects.Self))),
            create(creatureToken("Cat", listOf(Color.White), 1, 1, abilities = listOf(Keyword.Lifelink)))),
        cycling("{2}{W}"),
        triggered(youCycleThis, gains(Objects.Each(and(sub("Cat"), you)), Keyword.Hexproof, Keyword.Indestructible)),
    )

    @Test
    fun valiantTakedown() = card(
        name = "Valiant Takedown",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Destroy target creature with toughness 4 or greater.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        spell(destroy(target("creature")), targets = listOf(slot("creature", and(creature, ObjectFilter.Toughness(Comparison.AtLeast, n(4)))))),
        cycling("{2}"),
    )

    @Test
    fun gleamshieldParry() = card(
        name = "Gleamshield Parry",
        cost = "{W}",
        type = "Instant",
        text = """
            Target creature gets +2/+2 until end of turn.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(spell(pump(target("creature"), 2, 2), targets = listOf(slot("creature", creature))), cycling("{2}"))

    // CR 613.1f: indestructible is lost first, so the destruction works
    @Test
    fun spectacularWreckage() = card(
        name = "Spectacular Wreckage",
        cost = "{3}{W}{W}",
        type = "Sorcery",
        text = """
            All creatures and Vehicles lose indestructible until end of turn, then destroy all creatures and Vehicles.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        spell(untilEndOfTurn(Objects.Each(creatureOrVehicle), Mechanic.LoseAbilities(Keyword.Indestructible.kind)), destroy(Objects.Each(creatureOrVehicle))),
        cycling("{2}"),
    )

    // CR 107.3a: X is the X paid for the cycling
    @Test
    fun valorsDreadnought() = card(
        name = "Valor's Dreadnought",
        cost = "{4}{W}{W}{W}",
        type = "Legendary Artifact — Vehicle",
        text = """
            Flying, first strike, lifelink
            Crew 3
            Cycling {X}{2}{W} ({X}{2}{W}, Discard this card: Draw a card.)
            When you cycle this card, create X 1/1 colorless Pilot creature tokens with "This token saddles Mounts and crews Vehicles as though its power were 2 greater."
        """,
        pt = "7/7",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.FirstStrike,
        Keyword.Lifelink,
        Keyword.Crew(3),
        cycling("{X}{2}{W}"),
        triggered(youCycleThis, create(pilotToken, count = Value.EventX)),
    )

    @Test
    fun howlersHauler() = card(
        name = "Howler's Hauler",
        cost = "{3}{U}",
        type = "Creature — Seal Pirate",
        text = """
            Cycling {1}{U} ({1}{U}, Discard this card: Draw a card.)
            When you cycle this card, target creature or Vehicle an opponent controls gets -3/-0 until end of turn.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        cycling("{1}{U}"),
        triggered(youCycleThis, pump(target("permanent"), -3, 0), targets = listOf(slot("permanent", and(creatureOrVehicle, opponents)))),
    )

    @Test
    fun scavengingSkyray() = card(
        name = "Scavenging Skyray",
        cost = "{1}{U}",
        type = "Creature — Fish Pirate",
        text = """
            Flying
            Whenever you discard one or more cards, put that many +1/+1 counters on this creature.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
        pt = "1/2",
    ) parsesTo abilities(Keyword.Flying, youDiscardPutThatMany, cycling("{2}"))

    // CR 122.1d: a stun counter is removed instead of untapping
    @Test
    fun stalledEngine() = card(
        name = "Stalled Engine",
        cost = "{1}{U}",
        type = "Sorcery",
        text = """
            Tap target creature or Vehicle, then put three stun counters on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        spell(tapIt(target("permanent")), counters(target("permanent"), 3, CounterKind.Stun), targets = listOf(slot("permanent", creatureOrVehicle))),
        cycling("{2}"),
    )

    // CR 701.12a: both must be exchangeable
    @Test
    fun swapTheHelm() = card(
        name = "Swap the Helm",
        cost = "{4}{U}",
        type = "Sorcery",
        text = """
            Exchange control of target artifact or creature you control and target artifact or creature an opponent controls.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        spell(doing(Effect.ExchangeControl(target("permanent"), target("second permanent"))),
            targets = listOf(slot("permanent", and(or(artifact, creature), you)), slot("second permanent", and(or(artifact, creature), opponents)))),
        cycling("{2}"),
    )

    @Test
    fun tumbleUp() = card(
        name = "Tumble Up",
        cost = "{3}{U}",
        type = "Instant",
        text = """
            Target nonland permanent's owner puts it on their choice of the top or bottom of their library.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Option(listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(target("permanent"))),
            Step.If(Condition.Chose("position", "top"), then = listOf(move(target("permanent"), Zone.Library, position = 0)),
                otherwise = listOf(move(target("permanent"), Zone.Library, position = -1))),
            targets = listOf(slot("permanent", nonlandPermanent))),
        cycling("{2}"),
    )

    // CR 707.9, 614.1c: it may enter as a copy
    @Test
    fun tallowShapethief() = card(
        name = "Tallow Shapethief",
        cost = "{3}{U}",
        type = "Creature — Shapeshifter",
        text = """
            Flash
            You may have this creature enter as a copy of an artifact or creature you control.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
        pt = "0/0",
    ) parsesTo abilities(
        Keyword.Flash,
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(
            Modification.EntersAsCopy(of = and(or(artifact, creature), you), optional = true)))),
        cycling("{2}"),
    )

    // CR 701.23: a search of the graveyard, hand and library
    @Test
    fun ancestralVendetta() = card(
        name = "Ancestral Vendetta",
        cost = "{3}{B}",
        type = "Sorcery",
        text = "Choose a card name. Search target opponent's graveyard, hand, and library for up to four cards with that name and exile them. Then that player shuffles.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.CardName(ObjectFilter.Any), bind = "name", chooser = Players.You),
            doing(Effect.Search(Players.Target("opponent"), ObjectFilter.ChosenName("name"), max = n(4), zones = listOf(Zone.Graveyard, Zone.Hand, Zone.Library),
                bind = "found", searcher = Players.You)),
            move(Objects.Bound("found"), Zone.Exile),
            doing(Effect.Shuffle(Players.Target("opponent"))),
            targets = listOf(targetOpponent)),
    )

    // CR 614.12: the colour chosen as it enters
    @Test
    fun nightBazaar() = card(
        name = "Night Bazaar",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped. As it enters, choose a color.
            {T}: Add one mana of the chosen color.
            Cycling {3} ({3}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        entersTapped,
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Color(), bind = "color", chooser = Players.You, remember = true)))))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Remembered("color")))), Players.You))),
        cycling("{3}"),
    )
}
