package arcanum.parser.sets.trk

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** TRK fix step: audit doubts and wordings settled after the chunks. */
class TrkFixesTest {
    // Audit (unsure) was a false alarm: "one mana of any color" is one mana of a colour its controller chooses among all five
    // (ManaKind.Choose with no colour list, CR 106.1a, 106.4)
    @Test
    fun xenobotanist() = card(
        name = "Trk Fix Seedling Botanist",
        cost = "{1}{G}",
        type = "Creature — Vulcan Scientist",
        text = "{T}: Add one mana of any color.",
        pt = "1/3",
    ) parsesTo abilities(activated(cost(tap), doing(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))))

    // CR 702.196a, b: each card in your graveyard has beam me up for its mana cost during your main phase (layer 6); the giver can't be
    // returned for it
    @Test
    fun scottyMiracleWorker() = card(
        name = "Montgomery, Miracle Mechanic",
        cost = "{1}{U}{U}",
        type = "Legendary Creature — Human Artificer",
        text = "During your main phase, each card in your graveyard has beam me up. The beam me up cost is equal to that spell's mana cost. Montgomery can't be beamed up to cast a spell this way. (Only instant and sorcery spells can be cast this way. You may cast a card from your graveyard for its beam me up cost if you also return another creature you control to its owner's hand. Then exile that spell.)",
        pt = "2/3",
    ) parsesTo abilities(Ability.Static(
        listOf(Mechanic.GainAbilities(listOf(Keyword.BeamMeUp(Cost(listOf(CostPart.ItsManaCost)), returnExcept = ObjectFilter.Is(Objects.Granter))))),
        affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.InZone(Zone.Graveyard),
            ObjectFilter.OwnedBy(PlayerFilter.You))))),
        condition = Condition.And(listOf(Condition.YourTurn, Condition.Or(listOf(Condition.StepIs(TurnStep.PrecombatMain), Condition.StepIs(TurnStep.PostcombatMain))))),
    ))
}
