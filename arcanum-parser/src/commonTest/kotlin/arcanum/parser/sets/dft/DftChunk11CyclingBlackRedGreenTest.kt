package arcanum.parser.sets.dft

import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.CostChange
import arcanum.api.Effect
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
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
 * Aetherdrift chunk 11: cycling (CR 702.29) on black, red, green and colourless cards: a spell costing less for cards in the graveyard
 * (CR 601.2f), hands revealed and a card chosen from them (CR 701.20), damage for each card discarded, cycle triggers with targets and with
 * the X of cycling {X} (CR 107.3a, 702.29c), and exiling a card from a graveyard on entering or attacking.
 */
class DftChunk11CyclingBlackRedGreenTest {
    @Test
    fun chitinTombstalker() = card(
        name = "Chitin Tombstalker",
        cost = "{5}{B}",
        type = "Creature — Insect Warrior",
        text = """
            This spell costs {1} less to cast for each artifact and/or creature card in your graveyard.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
        pt = "5/4",
    ) parsesTo abilities(
        static(Mechanic.CostChange(isSelf, CostChange.Less(ManaCost.parse("{1}")!!, count(inYourGraveyard(or(artifact, creature)))))),
        cycling("{2}"),
    )

    @Test
    fun intimidatingTactics() = card(
        name = "Intimidating Tactics",
        cost = "{B}",
        type = "Sorcery",
        text = """
            Target opponent reveals their hand. You choose an artifact or creature card from it. Exile that card.
            Cycling {3} ({3}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
            Step.Choose(Choice.Objects(or(artifact, creature), n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
            targets = listOf(targetOpponent)),
        cycling("{3}"),
    )

    @Test
    fun locustMist() = card(
        name = "Locust Mist",
        cost = "{B}",
        type = "Instant",
        text = """
            Target creature gets -1/-1 until end of turn.
            Cycling {B} ({B}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(spell(pump(target("creature"), -1, -1), targets = listOf(slot("creature", creature))), cycling("{B}"))

    @Test
    fun shefetHellfiend() = card(
        name = "Shefet Hellfiend",
        cost = "{5}{B}{B}",
        type = "Creature — Demon",
        text = """
            Flying
            When this creature enters, all other creatures get -2/-2 until end of turn.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
        pt = "5/5",
    ) parsesTo abilities(Keyword.Flying, triggered(entersSelf, pump(Objects.Each(and(another, creature)), -2, -2)), cycling("{2}"))

    @Test
    fun stokeTheFlamesAnew() = card(
        name = "Stoke the Flames Anew",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            Stoke the Flames Anew deals 2 damage to each creature.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(spell(damage(2, toObjects(Objects.Each(creature)))), cycling("{2}"))

    // CR 702.29c: the cycle trigger works from the graveyard the card went to
    @Test
    fun magmakinGunner() = card(
        name = "Magmakin Gunner",
        cost = "{2}{R}",
        type = "Creature — Elemental Pirate",
        text = """
            Whenever you discard one or more cards, this creature deals that much damage to each opponent.
            Cycling {1}{R} ({1}{R}, Discard this card: Draw a card.)
            When you cycle this card, it deals 1 damage to each opponent.
        """,
        pt = "1/4",
    ) parsesTo abilities(
        triggered(youDiscardCards, damage(thatManyDiscarded, eachOpponent)),
        cycling("{1}{R}"),
        triggered(youCycleThis, damage(1, eachOpponent)),
    )

    @Test
    fun maraudingShark() = card(
        name = "Marauding Shark",
        cost = "{R}",
        type = "Creature — Shark Pirate",
        text = """
            Whenever you discard one or more cards, put that many +1/+1 counters on this creature.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
        pt = "1/1",
    ) parsesTo abilities(youDiscardPutThatMany, cycling("{2}"))

    @Test
    fun skyshatter() = card(
        name = "Skyshatter",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Destroy target artifact.
            Cycling {R} ({R}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(spell(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))), cycling("{R}"))

    @Test
    fun agonodonRex() = card(
        name = "Agonodon Rex",
        cost = "{3}{G}{G}",
        type = "Creature — Dinosaur",
        text = """
            Trample
            Cycling {2}{G} ({2}{G}, Discard this card: Draw a card.)
            When you cycle this card, put two +1/+1 counters on up to one target creature or Vehicle. It gains trample and indestructible until end of turn.
        """,
        pt = "8/8",
    ) parsesTo abilities(
        Keyword.Trample,
        cycling("{2}{G}"),
        triggered(youCycleThis, counters(target("permanent"), 2), gains(target("permanent"), Keyword.Trample, Keyword.Indestructible),
            targets = listOf(slot("permanent", creatureOrVehicle, min = 0))),
    )

    @Test
    fun wanderingKetradon() = card(
        name = "Wandering Ketradon",
        cost = "{4}{G}{G}",
        type = "Creature — Dinosaur",
        text = """
            Reach
            When this creature enters, you gain 4 life.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
        pt = "6/6",
    ) parsesTo abilities(Keyword.Reach, triggered(entersSelf, gainLife(4)), cycling("{2}"))

    // CR 107.3a: X is the X paid for the cycling
    @Test
    fun webshotElite() = card(
        name = "Webshot Elite",
        cost = "{G}{G}",
        type = "Creature — Insect Archer",
        text = """
            Reach
            Cycling {X}{G}{G} ({X}{G}{G}, Discard this card: Draw a card.)
            When you cycle this card, destroy up to one target artifact or enchantment with mana value X.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Reach,
        cycling("{X}{G}{G}"),
        triggered(youCycleThis, destroy(target("permanent")), targets = listOf(slot("permanent", and(or(artifact, enchantment),
            ObjectFilter.ManaValue(Comparison.Equal, Value.EventX)), min = 0))),
    )

    @Test
    fun wreckCollector() = card(
        name = "Wreck Collector",
        cost = "{4}",
        type = "Artifact Creature — Construct",
        text = """
            Whenever this creature enters or attacks, exile up to one target card from a graveyard. You gain 1 life.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
        pt = "3/4",
    ) parsesTo abilities(
        triggered(entersOrAttacksSelf, move(target("card"), Zone.Exile, postBind = "exiled"), gainLife(1), targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard)), min = 0))),
        cycling("{2}"),
    )
}
