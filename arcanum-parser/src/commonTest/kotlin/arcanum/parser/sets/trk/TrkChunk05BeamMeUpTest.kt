package arcanum.parser.sets.trk

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * TRK chunk 05: "Beam me up [cost]" (CR 702.196a): cast this instant or sorcery from your graveyard for [cost] and by returning a
 * creature you control to its owner's hand; then it is exiled. The keyword keeps its mana cost; its expansion adds the return.
 */
class TrkChunk05BeamMeUpTest {
    private fun beamMeUp(cost: String) = Keyword.BeamMeUp(cost(mana(cost)))
    private val found = Objects.Bound("found")

    // CR 702.196a, 111.1 (a colorless artifact creature token)
    @Test
    fun simulatedFoe() = card(
        name = "Simulated Foe",
        cost = "{2}{G}",
        type = "Sorcery",
        text = """
            Create a 3/3 colorless Illusion artifact creature token.
            Beam me up {3}{G} (You may cast this card from your graveyard for {3}{G} if you also return a creature you control to its owner's hand. Then exile this spell.)
        """,
    ) parsesTo abilities(
        spell(create(creatureToken("Illusion", emptyList(), 3, 3, types = listOf(CardType.Artifact, CardType.Creature)))),
        beamMeUp("{3}{G}"),
    )

    // CR 702.196a, 702.10 (a token with haste)
    @Test
    fun hullBreachRaid() = card(
        name = "Hull Breach Raid",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            Create a 2/1 red Klingon Warrior creature token with haste.
            Beam me up {2}{R} (You may cast this card from your graveyard for {2}{R} if you also return a creature you control to its owner's hand. Then exile this spell.)
        """,
    ) parsesTo abilities(
        spell(create(creatureToken("Klingon Warrior", listOf(Color.Red), 2, 1, listOf(Keyword.Haste)))),
        beamMeUp("{2}{R}"),
    )

    // CR 702.196a, 400.3 (its owner's hand)
    @Test
    fun recallPulse() = card(
        name = "Recall Pulse",
        cost = "{1}{U}",
        type = "Sorcery",
        text = """
            Return target creature an opponent controls to its owner's hand.
            Beam me up {3}{U} (You may cast this card from your graveyard for {3}{U} if you also return a creature you control to its owner's hand. Then exile this spell.)
        """,
    ) parsesTo abilities(
        spell(move(target("creature"), Zone.Hand), targets = listOf(slot("creature", creatureOpponentControls))),
        beamMeUp("{3}{U}"),
    )

    // CR 702.196a, 701.23 (search), 701.20 (reveal)
    @Test
    fun fleetRequisition() = card(
        name = "Fleet Requisition",
        cost = "{1}{W}",
        type = "Sorcery",
        text = """
            Search your library for an Aura, Equipment, or Spacecraft card, reveal it, put it into your hand, then shuffle.
            Beam me up {3}{W} (You may cast this card from your graveyard for {3}{W} if you also return a creature you control to its owner's hand. Then exile this spell.)
        """,
    ) parsesTo abilities(
        spell(search(or(sub("Aura"), equipment, sub("Spacecraft")), reveal = true), move(found, Zone.Hand), shuffle),
        beamMeUp("{3}{W}"),
    )

    // CR 702.196a, 701.23 (search), 205.3i (Planet is a land type, not a basic one)
    @Test
    fun outerRimSurvey() = card(
        name = "Outer Rim Survey",
        cost = "{2}{G}",
        type = "Sorcery",
        text = """
            Search your library for a basic land card or Planet card, put it onto the battlefield tapped, then shuffle.
            Beam me up {1}{G} (You may cast this card from your graveyard for {1}{G} if you also return a creature you control to its owner's hand. Then exile this spell.)
        """,
    ) parsesTo abilities(
        spell(search(or(basicLand(), sub("Planet"))), move(found, Zone.Battlefield, tapped = true), shuffle),
        beamMeUp("{1}{G}"),
    )

    // CR 702.196a, 121.1 (draw)
    @Test
    fun openChannelPing() = card(
        name = "Open Channel Ping",
        cost = "{U}",
        type = "Sorcery",
        text = """
            Draw a card.
            Beam me up {2}{U} (You may cast this card from your graveyard for {2}{U} if you also return a creature you control to its owner's hand. Then exile this spell.)
        """,
    ) parsesTo abilities(
        spell(draw(1)),
        beamMeUp("{2}{U}"),
    )

    // CR 702.196a, 401.5 (looking at library cards)
    @Test
    fun bufferStasis() = card(
        name = "Buffer Stasis",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            Look at the top two cards of your library. Put one of them into your hand and the other into your graveyard.
            Beam me up {1}{B} (You may cast this card from your graveyard for {1}{B} if you also return a creature you control to its owner's hand. Then exile this spell.)
        """,
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(2), fromTop = true), Players.You, bind = "looked")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), Zone.Graveyard)),
        beamMeUp("{1}{B}"),
    )

    // CR 702.196a, 601.2d (distributing counters among one or two targets)
    @Test
    fun rawPlasmaGift() = card(
        name = "Raw Plasma Gift",
        cost = "{1}{W}",
        type = "Sorcery",
        text = """
            Distribute two +1/+1 counters among one or two target creatures.
            Beam me up {2}{W} (You may cast this card from your graveyard for {2}{W} if you also return a creature you control to its owner's hand. Then exile this spell.)
        """,
    ) parsesTo abilities(
        spell(doing(Effect.PutCounters(target("creatures"), p1p1, Value.DividedShare)),
            targets = listOf(slot("creatures", creature, min = 1, max = 2).copy(divided = n(2)))),
        beamMeUp("{2}{W}"),
    )

    // CR 702.196a, 701.20 (reveal), 401.1 ("on the bottom" is the bottom of the same library), 110.4b (permanent cards); the mana
    // value limit belongs to the nonland permanent card alone
    @Test
    fun brewInTheCloud() = card(
        name = "Brew in the Cloud",
        cost = "{1}{W}",
        type = "Sorcery",
        text = """
            Look at the top five cards of your library. You may reveal a Plains card or nonland permanent card with mana value 3 or less from among them and put it into your hand. Put the rest on the bottom in a random order.
            Beam me up {2}{W} (You may cast this card from your graveyard for {2}{W} if you also return a creature you control to its owner's hand. Then exile this spell.)
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(or(sub("Plains"), and(nonland, permanentCard, manaValue(Comparison.AtMost, 3))), n(0), n(1),
                from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))),
            move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
        beamMeUp("{2}{W}"),
    )

    // CR 702.196a, 120.3 (damage to any target)
    @Test
    fun beamScramble() = card(
        name = "Beam Scramble",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            Beam Scramble deals 3 damage to any target.
            Beam me up {3}{R}{R}{R} (You may cast this card from your graveyard for {3}{R}{R}{R} if you also return a creature you control to its owner's hand. Then exile this spell.)
        """,
    ) parsesTo abilities(
        spell(damage(3, toAny), targets = listOf(anyTarget)),
        beamMeUp("{3}{R}{R}{R}"),
    )
}
