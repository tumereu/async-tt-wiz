package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.thisEnters
import kotlin.test.Test

/*
 * OTJ chunk 18: audit findings on cards whose models were read but doubted.
 */
class OtjChunk18AuditTest {
    // CR 608.2d (a "you may" step is the player's choice) and 117.12 (an optional cost): Step.Pay is optional unless mandatory = true,
    // so the audit's doubt is a false alarm; "If you do" draws only when the discard was paid.
    @Test
    fun discerningPeddlerDiscardIsOptional() = card(
        name = "Discerning Haggler", cost = "{1}{R}", type = "Creature — Human Rogue", pt = "2/2",
        text = "When this creature enters, you may discard a card. If you do, draw a card.",
    ) parsesTo abilities(
        triggered(thisEnters,
            Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1))))), payer = Players.You, mandatory = false, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(1))),
        ),
    )

    // CR 113.8 (a granted ability is controlled by its permanent's controller), 111.10b: "you" in the quoted trigger is the creature's controller
    @Test
    fun fakeYourOwnDeathQuotedYouIsTheCreaturesController() = card(
        name = "Fake Your Own Funeral", cost = "{1}{B}", type = "Instant",
        text = """
            Until end of turn, target creature gets +2/+0 and gains "When this creature dies, return it to the battlefield tapped under its owner's control and you create a Treasure token." (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(target("creature"), pt(2, 0), has(
                triggered(diesSelf, move(Objects.EventObject(EventRole.After), Zone.Battlefield, tapped = true, controller = Players.OwnerOf(Objects.EventObject(EventRole.After))), createTreasure()))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 106.1b, 605.1a: "one mana of any color" is a chosen color, a tap mana ability; CR 119.1 gain life
    @Test
    fun oasisGardenerTapsForAnyColor() = card(
        name = "Oasis Tender", cost = "{3}", type = "Artifact Creature — Scarecrow", pt = "2/2",
        text = """
            When this creature enters, you gain 2 life.
            {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        triggered(thisEnters,gainLife(2)),
        tapForAnyColor,
    )
}
