package arcanum.parser.sets.dmr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** DMR chunk 15: cards whose audit doubted the model: any-colour mana, a graveyard-exile cost, look-and-bottom, a mill cost. */
class DmrChunk15AuditFollowUpTest {
    // CR 106.1b, 605.1a: "one mana of any color" is Choose with no colour list, which means all five colours (ManaKind.Choose KDoc)
    @Test
    fun anyColorMana() = card(
        name = "Sunfeather Sprite", cost = "{G}", type = "Creature — Bird", pt = "0/1", text = """
            Flying
            {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(Keyword.Flying, activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))))

    // CR 118.3, 602.2: the cost's "your graveyard" is the Zone.Graveyard of the cost payer, a card of any kind, two of them
    @Test
    fun exileTwoFromYourGraveyard() = card(
        name = "Ember Scrapper", cost = "{R}", type = "Creature — Human Wizard", pt = "1/1",
        text = "{R}, {T}, Exile two cards from your graveyard: This creature deals 2 damage to any target.",
    ) parsesTo abilities(activated(
        cost(mana("{R}"), tap, CostPart.Exile(CostObjects.Choose(cardFilter, count = n(2)), from = Zone.Graveyard)),
        Step.Do(Effect.DealDamage(Recipients(target = "any"), amount = n(2), source = Objects.Self)),
        targets = listOf(anyTarget),
    ))

    // CR 701.20a, 401.4: look at four, one to hand, the rest to the bottom; their order is the owner's choice (CR 401.4, engine side)
    @Test
    fun lookFourOneToHandRestOnBottom() = card(
        name = "Quickglance", cost = "{1}{U}", type = "Instant",
        text = "Look at the top four cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order.",
    ) parsesTo abilities(spell(
        Step.Do(Effect.LookAt(Objects.LibraryCards(of = Players.You, count = n(4), fromTop = true), players = Players.You, bind = "seen")),
        Step.Choose(Choice.Objects(filter = ObjectFilter.Any, min = n(1), max = n(1), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
        Step.Do(Effect.Move(Objects.Bound("kept"), Destination(Zone.Hand))),
        Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Destination(Zone.Library, libraryPosition = -1))),
    ))

    // CR 602.1, 605.1a: the reminder "(Activate only as an instant.)" restates the timing every activated ability has (no sorcery-speed restriction); the model is the plain ability with a mill cost
    @Test
    fun millCostManaAbility() = card(
        name = "Gravelet", cost = "{2}", type = "Artifact Creature — Construct", pt = "0/1",
        text = "{T}, Mill a card: Add {C}. (Activate only as an instant. To mill a card, put the top card of your library into your graveyard.)",
    ) parsesTo abilities(activated(cost(tap, CostPart.Mill(n(1))), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))))
}
