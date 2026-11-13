package arcanum.parser.sets.rvr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Ravnica chunk 15: sacrifice with a power condition, searches (basic land types, Gates), untapping all, Forecast. Borborygmos,
 * Pack Rat, Ral Zarek and Turnabout read exactly like tested cards of earlier sets (reveal-and-split, power equal to a count,
 * flip coins, choose-a-type tap-or-untap) and get engine scenarios only. */
class RvrChunk15RevealSacrificeSearchTapTest {
    // CR 701.21a (sacrifice, the player chooses), 119.3 (gain life), 608.2c (condition checked on resolution)
    @Test
    fun undercityLookalike() = card(
        name = "Undertow Embrace", cost = "{2}{B}", type = "Instant",
        text = "Target opponent sacrifices a creature of their choice. If you control a creature with power 4 or greater, you gain 4 life.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))), n(1), n(1)),
                bind = "sacrificed", chooser = Players.Target("opponent")),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            ifThen(youControl(and(creature, power(Comparison.AtLeast, 4)), 1), gainLife(4)),
            targets = listOf(targetOpponent),
        ),
    )

    // CR 701.23a (search), 205.3i (land types): only lands have those subtypes; the untapped creatures of a cost choice are implicit
    @Test
    fun farseekLookalike() = card(
        name = "Lowland Seek", cost = "{1}{G}", type = "Sorcery",
        text = "Search your library for a Plains, Island, Swamp, or Mountain card, put it onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        spell(
            search(or(sub("Plains"), sub("Island"), sub("Swamp"), sub("Mountain"))),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
            shuffle,
        ),
    )

    // CR 701.23a, 701.20a (reveal), 205.3 (Gate is a land type)
    @Test
    fun openTheGatesLookalike() = card(
        name = "Unbar the Gates", cost = "{G}", type = "Sorcery",
        text = "Search your library for a basic land card or Gate card, reveal it, put it into your hand, then shuffle.",
    ) parsesTo abilities(
        spell(
            search(or(basicLand(), sub("Gate")), reveal = true),
            move(Objects.Bound("found"), Zone.Hand),
            shuffle,
        ),
    )

    // CR 118.12 (costs), 701.21a (sacrifice as a cost), 305.7
    @Test
    fun perilousForaysLookalike() = card(
        name = "Risky Foraging", cost = "{3}{G}{G}", type = "Enchantment",
        text = "{1}, Sacrifice a creature: Search your library for a land card with a basic land type, put it onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), sacrificeA(creature)),
            search(and(land, or(sub("Plains"), sub("Island"), sub("Swamp"), sub("Mountain"), sub("Forest")))),
            move(Objects.Bound("found"), Zone.Battlefield, tapped = true),
            shuffle),
    )

    // CR 701.26b (untap all), 702.57 (forecast: only during your upkeep, once each turn), 602.5
    @Test
    fun skyHussarLookalike() = card(
        name = "Dawn Hussar", cost = "{3}{W}{U}", type = "Creature — Human Knight", pt = "4/3",
        text = """
            Flying
            When this creature enters, untap all creatures you control.
            Forecast — Tap two untapped white and/or blue creatures you control, Reveal this card from your hand: Draw a card. (Activate only during your upkeep and only once each turn.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, untapIt(Objects.Each(creatureYouControl))),
        Ability.Activated(cost(CostPart.TapObjects(CostObjects.Choose(and(or(ObjectFilter.OfColor(Color.White), ObjectFilter.OfColor(Color.Blue)), creature),
            count = n(2))), CostPart.Reveal(CostObjects.Self)), Instructions(steps = listOf(draw(1))), anyTime, label = AbilityLabel.Forecast),
    )

    // CR 701.26b (untap all), 121.1 (draw)
    @Test
    fun toArmsLookalike() = card(
        name = "Sound the Muster", cost = "{1}{W}", type = "Instant",
        text = """
            Untap all creatures you control.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(untapIt(Objects.Each(creatureYouControl)), draw(1)),
    )

    // CR 513.1 (end step triggers), 701.26b
    @Test
    fun wildernessReclamationLookalike() = card(
        name = "Frontier Reclamation", cost = "{3}{G}", type = "Enchantment",
        text = "At the beginning of your end step, untap all lands you control.",
    ) parsesTo abilities(
        triggered(yourEndStep, untapIt(Objects.Each(and(land, you)))),
    )
}
