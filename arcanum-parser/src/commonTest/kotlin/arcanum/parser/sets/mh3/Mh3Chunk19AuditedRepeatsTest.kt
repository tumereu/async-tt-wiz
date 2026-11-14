package arcanum.parser.sets.mh3

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * MH3 chunk 19: cards whose model an audit doubted. Sacrifice costs (CR 118.3, 701.21a): a player can only sacrifice a
 * permanent they control, so the cost needs no controlled-by filter; the owner of the cards arranges them (CR 401.4).
 */
class Mh3Chunk19AuditedRepeatsTest {
    // CR 401.4: the owner arranges the cards put on top at once; the same model as the other draw-then-put-back cards
    @Test
    fun brainsurgeLike() = card(
        name = "Mindflood Surge",
        cost = "{2}{U}",
        type = "Instant",
        text = "Draw four cards, then put two cards from your hand on top of your library in any order.",
    ) parsesTo abilities(
        spell(
            draw(4),
            Step.Choose(Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), n(2), n(2)), bind = "put", chooser = Players.You),
            move(Objects.Bound("put"), Zone.Library, position = 0),
        ),
    )

    // CR 702.18a (shroud), 118.3: "Sacrifice a land" is a land its controller controls (audit: false alarm)
    @Test
    fun sylvanSafekeeperLike() = card(
        name = "Grove Warden Initiate",
        cost = "{G}",
        type = "Creature — Human Wizard",
        pt = "1/1",
        text = "Sacrifice a land: Target creature you control gains shroud until end of turn. (It can't be the target of spells or abilities.)",
    ) parsesTo abilities(
        activated(cost(sacrificeA(land)), gains(target("creature"), Keyword.Shroud),
            targets = listOf(slot("creature", and(creature, you)))),
    )

    // CR 118.3, 701.21a: "another creature" in a cost (audit: false alarm); CR 119.4 paying life
    @Test
    fun warrenSoultraderLike() = card(
        name = "Pit Broker Initiate",
        cost = "{2}{B}",
        type = "Creature — Zombie Goblin Wizard",
        pt = "3/3",
        text = "Pay 1 life, Sacrifice another creature: Create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")",
    ) parsesTo abilities(
        activated(cost(CostPart.PayLife(n(1)), sacrificeA(and(another, creature))), createTreasure()),
    )
}
