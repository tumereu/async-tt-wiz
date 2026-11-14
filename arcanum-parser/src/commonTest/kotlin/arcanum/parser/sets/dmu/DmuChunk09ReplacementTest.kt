package arcanum.parser.sets.dmu

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Timing
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
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
 * Dominaria United chunk 09: replacement effects. "Enters tapped" (CR 614.1c, 614.12), "enters with X +1/+1 counters"
 * (CR 614.1c), "you gain that much life plus 1 instead" (CR 614.1) and "if a creature an opponent controls would die, exile it
 * instead. When you do, ..." (CR 614.6, 603.7).
 */
class DmuChunk09ReplacementTest {
    private val entersTapped = static(Mechanic.Replacement(
        EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)))

    private fun dualLand(name: String, type: String, a: String, b: String) = card(
        name = name,
        cost = null,
        type = "Land — $type",
        text = """
            ({T}: Add {$a} or {$b}.)
            This land enters tapped.
        """,
    ) parsesTo abilities(entersTapped)

    @Test fun contaminatedAquifer() = dualLand("Contaminated Aquifer", "Island Swamp", "U", "B")
    @Test fun geothermalBog() = dualLand("Geothermal Bog", "Swamp Mountain", "B", "R")
    @Test fun hauntedMire() = dualLand("Haunted Mire", "Swamp Forest", "B", "G")
    @Test fun idyllicBeachfront() = dualLand("Idyllic Beachfront", "Plains Island", "W", "U")
    @Test fun moltenTributary() = dualLand("Molten Tributary", "Island Mountain", "U", "R")
    @Test fun radiantGrove() = dualLand("Radiant Grove", "Forest Plains", "G", "W")
    @Test fun sacredPeaks() = dualLand("Sacred Peaks", "Mountain Plains", "R", "W")
    @Test fun sunlitMarsh() = dualLand("Sunlit Marsh", "Plains Swamp", "W", "B")
    @Test fun tangledIslet() = dualLand("Tangled Islet", "Forest Island", "G", "U")
    @Test fun woodedRidgeline() = dualLand("Wooded Ridgeline", "Mountain Forest", "R", "G")

    // CR 614.1c, 603.4: a non-Skeleton creature died under your control (events, CR 700.4)
    @Test
    fun cultConscript() = card(
        name = "Cult Conscript",
        cost = "{B}",
        type = "Creature — Skeleton Warrior",
        pt = "2/1",
        text = """
            This creature enters tapped.
            {1}{B}: Return this card from your graveyard to the battlefield. Activate only if a non-Skeleton creature died under your control this turn.
        """,
    ) parsesTo abilities(
        entersTapped,
        Ability.Activated(
            cost = manaCost("{1}{B}"),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Battlefield), postBind = "returned")))),
            rules = ActivationRules(
                condition = Condition.HappenedThisTurn(
                    EventPattern.ZoneChange(and(not(sub("Skeleton")), creature, you), from = Zone.Battlefield, to = Zone.Graveyard),
                    atLeast = 1, measure = Measure.Events),
                timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )

    // CR 614.1: life gain is modified before it happens
    @Test
    fun knightOfDawnsLight() = card(
        name = "Knight of Dawn's Light",
        cost = "{1}{W}",
        type = "Creature — Human Knight",
        pt = "2/2",
        text = """
            First strike
            If you would gain life, you gain that much life plus 1 instead.
            {1}{W}: This creature gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        static(Mechanic.Replacement(EventPattern.GainLife(PlayerFilter.You), modify = listOf(Modification.AddAmount(Value.Fixed(1))))),
        activated(manaCost("{1}{W}"), pump(Objects.Self, 1, 1)),
    )

    // CR 614.6, 603.7: "When you do" follows the replaced event; the exile replaces dying
    @Test
    fun nemataPrimevalWarden() = card(
        name = "Nemata, Primeval Warden",
        cost = "{2}{B}{G}",
        type = "Legendary Creature — Treefolk",
        pt = "3/4",
        text = """
            Reach
            If a creature an opponent controls would die, exile it instead. When you do, create a 1/1 green Saproling creature token.
            {G}, Sacrifice a Saproling: Nemata gets +2/+2 until end of turn.
            {1}{B}, Sacrifice two Saprolings: Draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        static(Mechanic.Replacement(EventPattern.ZoneChange(creatureOpponentControls, from = Zone.Battlefield, to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))),
            whenApplied = Instructions(steps = listOf(create(creatureToken("Saproling", listOf(Color.Green), 1, 1)))))),
        activated(cost(mana("{G}"), sacrificeA(sub("Saproling"))), pump(Objects.Self, 2, 2)),
        activated(cost(mana("{1}{B}"), CostPart.Sacrifice(CostObjects.Choose(sub("Saproling"), count = Value.Fixed(2)))), draw(1)),
    )

    // CR 614.1c, 107.3: X is the value announced for the spell
    @Test
    fun shivanDevastator() = card(
        name = "Shivan Devastator",
        cost = "{X}{R}",
        type = "Creature — Dragon Hydra",
        pt = "0/0",
        text = """
            Flying, haste
            This creature enters with X +1/+1 counters on it.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Haste,
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, Value.CastX(of = Objects.Self))))),
    )

    // CR 614.1c: a tapped artifact makes no mana the turn it enters
    @Test
    fun timelessLotus() = card(
        name = "Timeless Lotus",
        cost = "{5}",
        type = "Legendary Artifact",
        text = """
            Timeless Lotus enters tapped.
            {T}: Add {W}{U}{B}{R}{G}.
        """,
    ) parsesTo abilities(
        entersTapped,
        activated(cost(tap), Step.Do(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Fixed(arcanum.api.ManaCost.parse("{W}{U}{B}{R}{G}")!!)), Players.You))),
    )
}
