package arcanum.parser.sets.trk

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.dmu.addEither
import kotlin.test.Test

/**
 * TRK chunk 13: ten Planet lands (enter tapped, gain 1 life, tap for one of two colors), the five shock lands (pay 2 life as they enter, CR 614.12;
 * their mana comes from the basic land types, CR 305.6) and a draw replacement (CR 614.11).
 */
class TrkChunk13LandsTest {
    private val entersTapped = static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersTapped)))

    private fun planet(name: String, a: String, b: String) = card(
        name = name,
        cost = null,
        type = "Land — Planet",
        text = """
            This land enters tapped.
            When this land enters, you gain 1 life.
            {T}: Add {$a} or {$b}.
        """,
    ) parsesTo abilities(entersTapped, triggered(entersSelf, gainLife(1)), addEither(a, b))

    // CR 614.1c, 305.6 (basic land types give the mana ability, so the reminder text adds nothing)
    private fun shock(name: String, type: String, a: String, b: String) = card(
        name = name,
        cost = null,
        type = type,
        text = """
            ({T}: Add {$a} or {$b}.)
            As this land enters, you may pay 2 life. If you don't, it enters tapped.
        """,
    ) parsesTo abilities(static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.AsEnters(listOf(
            Step.Pay(cost = cost(CostPart.PayLife(n(2))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Modify(Modification.EntersTapped))),
        ))))))

    @Test fun andoria() = planet("Andoria", "U", "R")
    @Test fun bajor() = planet("Bajor", "R", "W")
    @Test fun cardassiaPrime() = planet("Cardassia Prime", "W", "B")
    @Test fun earthSector001() = planet("Earth, Sector 001", "G", "U")
    @Test fun ferenginar() = planet("Ferenginar", "B", "G")
    @Test fun orion() = planet("Orion", "R", "G")
    @Test fun qonos() = planet("Qo'noS", "B", "R")
    @Test fun risa() = planet("Risa", "G", "W")
    @Test fun romulus() = planet("Romulus", "U", "B")
    @Test fun vulcan() = planet("Vulcan", "W", "U")

    @Test fun bloodCrypt() = shock("Blood Crypt", "Land — Swamp Mountain", "B", "R")
    @Test fun breedingPool() = shock("Breeding Pool", "Land — Forest Island", "G", "U")
    @Test fun godlessShrine() = shock("Godless Shrine", "Land — Plains Swamp", "W", "B")
    @Test fun hallowedFountain() = shock("Hallowed Fountain", "Land — Plains Island", "W", "U")
    @Test fun overgrownTomb() = shock("Overgrown Tomb", "Land — Swamp Forest", "B", "G")
    @Test fun sacredFoundry() = shock("Sacred Foundry", "Land — Mountain Plains", "R", "W")
    @Test fun steamVents() = shock("Steam Vents", "Land — Island Mountain", "U", "R")
    @Test fun stompingGround() = shock("Stomping Ground", "Land — Mountain Forest", "R", "G")
    @Test fun templeGarden() = shock("Temple Garden", "Land — Forest Plains", "G", "W")
    @Test fun wateryGrave() = shock("Watery Grave", "Land — Island Swamp", "U", "B")

    // CR 614.11 (a draw replaced by drawing two)
    @Test
    fun mrSpockScienceOfficer() = card(
        name = "Mr. Soval, Science Officer",
        cost = "{2}{U}{U}",
        type = "Legendary Creature — Vulcan Human Scientist",
        pt = "2/4",
        text = "If you would draw a card, draw two cards instead.",
    ) parsesTo abilities(static(Mechanic.Replacement(EventPattern.Draw(PlayerFilter.You), instead = Instructions(steps = listOf(draw(2))))))
}
