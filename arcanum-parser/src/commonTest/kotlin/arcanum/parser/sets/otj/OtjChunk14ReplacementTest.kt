package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * OTJ chunk 14: "enters tapped" replacements: ten Desert lands that also ping an opponent, a land that also chooses a color as it
 * enters, and a flash creature that enters tapped.
 */
class OtjChunk14ReplacementTest {
    // CR 614.1d, 614.12: "enters tapped" is a replacement of the zone change
    private val entersTapped = Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersTapped))), affects = Affected.Objects(Objects.Self))

    private fun twoColorDesert(name: String, first: Color, second: Color) {
        val a = first.symbol()
        val b = second.symbol()
        card(
            name = name,
            cost = null,
            type = "Land — Desert",
            text = """
                This land enters tapped.
                When this land enters, it deals 1 damage to target opponent.
                {T}: Add {$a} or {$b}.
            """,
        ) parsesTo abilities(
            entersTapped,
            triggered(entersSelf, damage(1, Recipients(players = Players.Target("opponent"))), targets = listOf(targetOpponent)),
            activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), colors = listOf(ColorRef.Literal(first), ColorRef.Literal(second)))), Players.You)),
                rules = anyTime),
        )
    }

    private fun Color.symbol() = when (this) {
        Color.White -> "W"; Color.Blue -> "U"; Color.Black -> "B"; Color.Red -> "R"; Color.Green -> "G"
    }

    @Test
    fun desertLandsThatPingAnOpponent() {
        val w = Color.White; val u = Color.Blue; val b = Color.Black; val r = Color.Red; val g = Color.Green
        // names are invented; the colour pairs are the ten two-colour combinations, each in the order the oracle text lists them
        twoColorDesert("Scorched Dunes", r, w)
        twoColorDesert("Dustbowl Hollow", r, g)
        twoColorDesert("Sunbaked Wash", g, w)
        twoColorDesert("Cracked Arroyo", u, r)
        twoColorDesert("Gritty Spring", b, g)
        twoColorDesert("Parched Gulch", w, b)
        twoColorDesert("Wind-Scoured Flats", b, r)
        twoColorDesert("Dry Wash", w, u)
        twoColorDesert("Verdant Mirage", g, u)
        twoColorDesert("Sour Wells", u, b)
    }

    // CR 614.12: a colour chosen as the land enters
    @Test
    fun miragePlateau() = card(
        name = "Mirage Plateau",
        cost = null,
        type = "Land — Desert",
        text = """
            This land enters tapped. As it enters, choose a color.
            {T}: Add one mana of the chosen color.
        """,
    ) parsesTo abilities(
        entersTapped,
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Color(), bind = "color", chooser = Players.You, remember = true)))))), affects = Affected.Objects(Objects.Self)),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), colors = listOf(ColorRef.Remembered("color")))), Players.You)), rules = anyTime),
    )

    // CR 614.1d
    @Test
    fun dozingThunderThief() = card(
        name = "Dozing Thunder-Thief",
        cost = "{3}{U}",
        type = "Creature — Turtle Rogue",
        pt = "4/4",
        text = """
            Flash
            This creature enters tapped.
        """,
    ) parsesTo abilities(Keyword.Flash, entersTapped)
}
