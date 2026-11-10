package arcanum.parser.sets.inr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/** INR chunk 11: "enters tapped (unless ...)" and "enters with an additional counter" replacements. */
class InrChunk11ReplacementTest {
    private val sameTypeYouControl = and(another, creature, you, ObjectFilter.Sub(SubtypeRef.Remembered("type")))
    private val plusOne = CounterKind.PowerToughness(1, 1)
    /** "This land enters tapped unless you control two or more other lands." (CR 614.1c, checked as it would enter) */
    private val entersTappedUnlessTwoOtherLands = static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        modify = listOf(Modification.EntersTapped)), condition = Condition.Not(youControl(and(another, land), 2)))

    private fun checkLand(name: String, a: String, b: String) = card(
        name = name,
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control two or more other lands.
            {T}: Add {${a}} or {${b}}.
        """,
    ) parsesTo abilities(entersTappedUnlessTwoOtherLands, addEither(a, b))

    @Test fun glenOfBlight() = checkLand("Glen of Blight", "B", "G")
    @Test fun shoreOfDawn() = checkLand("Shore of Dawn", "W", "U")
    @Test fun cascadeOfDreams() = checkLand("Cascade of Dreams", "G", "U")
    @Test fun ridgeOfHaunts() = checkLand("Ridge of Haunts", "B", "R")
    @Test fun farmlandOfOvergrowth() = checkLand("Farmland of Overgrowth", "G", "W")
    @Test fun valeOfRockfall() = checkLand("Vale of Rockfall", "R", "G")
    @Test fun sanctumOfShards() = checkLand("Sanctum of Shards", "W", "B")
    @Test fun marshOfWrecks() = checkLand("Marsh of Wrecks", "U", "B")
    @Test fun coastOfStorms() = checkLand("Coast of Storms", "U", "R")
    @Test fun passOfSundown() = checkLand("Pass of Sundown", "R", "W")

    // CR 614.12, 607.2d, 613.4c, 614.1c
    @Test
    fun metallicMimic() = card(
        name = "Shifting Mimic",
        cost = "{2}",
        type = "Artifact Creature — Shapeshifter",
        pt = "2/1",
        text = """
            As this creature enters, choose a creature type.
            This creature is the chosen type in addition to its other types.
            Each other creature you control of the chosen type enters with an additional +1/+1 counter on it.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(
            event = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.AsEnters(listOf(Step.Choose(
                Choice.CreatureType,
                bind = "type",
                remember = true,
                chooser = Players.You,
            )))),
        )), affects = Affected.Objects(Objects.Self)),
        Ability.Static(
            listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Remembered("type")))),
            affects = Affected.Objects(Objects.Self),
        ),
        Ability.Static(listOf(Mechanic.Replacement(
            event = EventPattern.ZoneChange(sameTypeYouControl, to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(plusOne, Value.Fixed(1))),
        )), affects = Affected.Objects(Objects.Self)),
    )

    // CR 614.1d (enters tapped), CR 502.3 (stays tapped through its controller's next untap step)
    @Test
    fun stitchedMangler() = card(
        name = "Sewn Mangler",
        cost = "{2}{U}",
        type = "Creature — Zombie Horror",
        pt = "2/3",
        text = """
            This creature enters tapped.
            When this creature enters, tap target creature an opponent controls. That creature doesn't untap during its controller's next untap step.
        """,
    ) parsesTo abilities(
        entersTapped,
        whenThisEnters(
            Step.Do(Effect.Tap(Objects.Target("creature"))),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.DoesntUntap), Affected.Objects(Objects.Target("creature")),
                Duration.ThroughNextStep(TurnStep.Untap, Players.ControllerOf(Objects.Target("creature"))))),
            targets = listOf(slot("creature", creatureOpponentControls)),
        ),
    )
}
