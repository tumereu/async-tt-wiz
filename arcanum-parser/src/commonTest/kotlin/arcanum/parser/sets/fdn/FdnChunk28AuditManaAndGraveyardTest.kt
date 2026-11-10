package arcanum.parser.sets.fdn

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** The FDN audit cards of chunk 28 (invented names, the oracle text of the real cards). */
class FdnChunk28AuditManaAndGraveyardTest {
    // CR 605.1a, 106.1b, 106.4: the Choose amount is the number of mana (three), all of one colour (combination = false)
    @Test
    fun gildedLotus() = card(
        name = "Gilded Blossom",
        cost = "{5}",
        type = "Artifact",
        text = "{T}: Add three mana of any one color.",
    ) parsesTo abilities(
        activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(Value.Fixed(3), colors = null, combination = false)), Players.You))),
    )

    // CR 602.2, 113.6k: an activated ability works only on the battlefield unless it says otherwise; "from your graveyard"
    // is the move's source, and the engine lets an ability whose effect moves this card from there be activated there
    @Test
    fun reassemblingSkeleton() = card(
        name = "Rebuilding Skeleton",
        cost = "{1}{B}",
        type = "Creature — Skeleton Warrior",
        pt = "1/1",
        text = "{1}{B}: Return this card from your graveyard to the battlefield tapped.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{1}{B}")!!))),
            instructions = Instructions(steps = listOf(Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Battlefield, tapped = true), postBind = "returned")))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )
}
