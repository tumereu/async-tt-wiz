package arcanum.parser.sets.dft

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Aetherdrift chunk 05: Pilots: creatures that saddle and crew as though their power were greater or with their toughness (CR 702.122a,
 * 702.171a), triggers on saddling a Mount or crewing a Vehicle during your main phase (CR 702.171c, 702.122b), the Pilot token, counts of
 * Mounts and Vehicles, and the Roads that enter untapped with a Mount or Vehicle.
 */
class DftChunk05PilotsTest {
    @Test
    fun gorgeLeaper() = card(
        name = "Gorge Leaper",
        cost = "{1}{W}",
        type = "Creature — Kor Pilot",
        text = "Whenever this creature saddles a Mount or crews a Vehicle during your main phase, that Mount or Vehicle gains flying until end of turn.",
        pt = "3/1",
    ) parsesTo abilities(saddlesOrCrewsInMainPhase(gains(thatMountOrVehicle, Keyword.Flying)))

    @Test
    fun cloudtrackCommander() = card(
        name = "Cloudtrack Commander",
        cost = "{2}{W}",
        type = "Creature — Human Pilot",
        text = """
            Mounts and Vehicles you control get +1/+1.
            This creature saddles Mounts and crews Vehicles as though its power were 2 greater.
        """,
        pt = "2/3",
    ) parsesTo abilities(static(pt(1, 1), affects = Objects.Each(and(mountOrVehicle, you))), saddlesAndCrewsAsThoughGreater(2))

    @Test
    fun collisionVerdict() = card(
        name = "Collision Verdict",
        cost = "{1}{W}",
        type = "Sorcery",
        text = """
            Choose one —
            • Collision Verdict deals X damage to target creature, where X is the number of permanents you control that are creatures and/or Vehicles.
            • Destroy target artifact.
        """,
    ) parsesTo abilities(
        modal(
            mode(damage(count(and(you, or(creature, vehicle))), toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
            mode(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
        ),
    )

    @Test
    fun pitlaneMechanic() = card(
        name = "Pitlane Mechanic",
        cost = "{2}{W}",
        type = "Creature — Human Artificer",
        text = "{3}{W}: Put a +1/+1 counter on target Mount or Vehicle.",
        pt = "3/3",
    ) parsesTo abilities(activated(cost(mana("{3}{W}")), counters(target("permanent")), targets = listOf(slot("permanent", mountOrVehicle))))

    @Test
    fun consoleAce() = card(
        name = "Console Ace",
        cost = "{1}{W}",
        type = "Artifact Creature — Robot Pilot",
        text = """
            This creature saddles Mounts and crews Vehicles using its toughness rather than its power.
            Whenever this creature becomes tapped during your turn, untap it. This ability triggers only once each turn.
        """,
        pt = "0/4",
    ) parsesTo abilities(
        static(Mechanic.UsesToughnessFor(Keyword.Saddle.Kind), Mechanic.UsesToughnessFor(Keyword.Crew.Kind)),
        triggered(on(EventPattern.Tap(isSelf)), untapIt(Objects.EventObject(arcanum.api.EventRole.Object)), perTurn = 1, triggersOnlyIf = Condition.YourTurn),
    )

    @Test
    fun waysideAid() = card(
        name = "Wayside Aid",
        cost = "{2}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature or Vehicle
            When this Aura enters, create a 1/1 colorless Pilot creature token with "This token saddles Mounts and crews Vehicles as though its power were 2 greater."
            Enchanted permanent gets +1/+1 and has lifelink.
        """,
    ) parsesTo abilities(
        enchantCreatureOrVehicle,
        triggered(entersSelf, create(pilotToken)),
        static(pt(1, 1), has(Keyword.Lifelink), affects = enchanted),
    )

    @Test
    fun backInTheRace() = card(
        name = "Back in the Race",
        cost = "{4}{B}",
        type = "Sorcery",
        text = """Return target creature or Vehicle card from your graveyard to the battlefield. Create a 1/1 colorless Pilot creature token with "This token saddles Mounts and crews Vehicles as though its power were 2 greater."""",
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Battlefield), create(pilotToken), targets = listOf(slot("card", inYourGraveyard(creatureOrVehicle)))),
    )

    @Test
    fun undyingPilot() = card(
        name = "Undying Pilot",
        cost = "{1}{B}",
        type = "Creature — Zombie Pilot",
        text = """
            This creature saddles Mounts and crews Vehicles as though its power were 2 greater.
            {3}{B}: Return this card from your graveyard to your hand.
        """,
        pt = "2/2",
    ) parsesTo abilities(saddlesAndCrewsAsThoughGreater(2), activated(cost(mana("{3}{B}")), move(Objects.Self, Zone.Hand)).copy(functionsIn = listOf(Zone.Graveyard)))

    @Test
    fun dynamitePlunger() = card(
        name = "Dynamite Plunger",
        cost = "{R}",
        type = "Creature — Goblin Pilot",
        text = """
            This creature saddles Mounts and crews Vehicles as though its power were 2 greater.
            When this creature dies, it deals 1 damage to any target.
        """,
        pt = "1/1",
    ) parsesTo abilities(saddlesAndCrewsAsThoughGreater(2), triggered(diesSelf, damage(1, toAny), targets = listOf(anyTarget)))

    @Test
    fun recklessVelocicorn() = card(
        name = "Reckless Velocicorn",
        cost = "{3}{R}",
        type = "Creature — Minotaur Pilot",
        text = "Whenever this creature saddles a Mount or crews a Vehicle during your main phase, that Mount or Vehicle gets +2/+0 and gains trample until end of turn.",
        pt = "3/3",
    ) parsesTo abilities(saddlesOrCrewsInMainPhase(pump(thatMountOrVehicle, 2, 0, has(Keyword.Trample))))

    @Test
    fun roadFury() = card(
        name = "Road Fury",
        cost = "{R}",
        type = "Instant",
        text = "Road Fury deals X damage to target creature or planeswalker, where X is 2 plus the number of Mounts and Vehicles you control.",
    ) parsesTo abilities(
        spell(damage(Value.Sum(listOf(n(2), count(and(mountOrVehicle, you)))), toObjects(target("permanent"))),
            targets = listOf(slot("permanent", or(creature, planeswalker)))),
    )

    @Test
    fun defendTheRacer() = card(
        name = "Defend the Racer",
        cost = "{G}",
        type = "Instant",
        text = """
            Choose one —
            • Target permanent you control gains hexproof and indestructible until end of turn.
            • Create a 1/1 colorless Pilot creature token with "This token saddles Mounts and crews Vehicles as though its power were 2 greater."
        """,
    ) parsesTo abilities(
        modal(
            mode(gains(target("permanent"), Keyword.Hexproof, Keyword.Indestructible), targets = listOf(slot("permanent", and(permanentFilter, you)))),
            mode(create(pilotToken)),
        ),
    )

    // CR 111.4: the tokens have the ability as part of their definition
    @Test
    fun cloudtrackCoordinator() = card(
        name = "Cloudtrack Coordinator",
        cost = "{R}{W}",
        type = "Creature — Human Pilot",
        text = """
            When this creature enters, scry 2.
            {T}: Create X 1/1 colorless Pilot creature tokens, where X is the number of Mounts and/or Vehicles that entered the battlefield under your control this turn. The tokens have "This token saddles Mounts and crews Vehicles as though its power were 2 greater."
        """,
        pt = "3/1",
    ) parsesTo abilities(
        triggered(entersSelf, scry(2)),
        activated(cost(tap), create(pilotToken, count = Value.EventsThisTurn(EventPattern.ZoneChange(and(mountOrVehicle, you), to = Zone.Battlefield), Measure.Objects))),
    )

    @Test
    fun countryLanes() = lanes("Country Lanes", "W")

    @Test
    fun foulLanes() = lanes("Foul Lanes", "B")

    @Test
    fun reefLanes() = lanes("Reef Lanes", "U")

    @Test
    fun rockyLanes() = lanes("Rocky Lanes", "R")

    @Test
    fun wildLanes() = lanes("Wild Lanes", "G")

    /** The Roads: enter untapped with a Mount or Vehicle (CR 614.1c), one colour, and a Pilot for the land. */
    private fun lanes(name: String, color: String) = card(
        name = name,
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a Mount or Vehicle.
            {T}: Add {$color}.
            {1}{$color}, {T}, Sacrifice this land: Create a 1/1 colorless Pilot creature token with "This token saddles Mounts and crews Vehicles as though its power were 2 greater." Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.EntersTapped))),
            affects = Affected.Objects(Objects.Self), condition = Condition.Not(Condition.Exists(and(mountOrVehicle, you), atLeast = 1))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{$color}")!!)), Players.You))),
        activated(cost(mana("{1}{$color}"), tap, sacrificeSelf), create(pilotToken), rules = asSorcery),
    )
}
