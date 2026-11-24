package arcanum.parser.sets.neo

import arcanum.api.*
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.abilities
import kotlin.test.Test

/** Chunk 5: Vehicles with crew (CR 702.122), "becomes crewed" triggers (CR 702.122e) and Pilot tokens that crew as though stronger. */
class NeoChunk05CrewTest {
    private fun crewLine(n: Int) =
        "Crew $n (Tap any number of creatures you control with total power $n or more: This Vehicle becomes an artifact creature until end of turn.)"
    private val vehicle = sub("Vehicle")
    private fun vehicleCard(name: String, cost: String, pt: String, vararg lines: String) =
        card(name = name, cost = cost, type = "Artifact — Vehicle", pt = pt, text = lines.joinToString("\n"))
    private val allColors = listOf(Color.White, Color.Blue, Color.Black, Color.Red, Color.Green)
    /** This Vehicle's crew ability resolved (CR 702.122e) */
    private val crewedSelf = EventPattern.Crewed(ObjectFilter.Is(Objects.Self))

    // CR 702.122a: crew taps creatures with total power N or more; the Vehicle's other keywords stand beside it
    @Test
    fun bruiserExoframe() = vehicleCard("Bruiser Exoframe", "{3}", "4/3", "Vigilance", crewLine(1)) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Crew(1),
    )

    // CR 702.122a
    @Test
    fun mayflyExoframe() = vehicleCard("Mayfly Exoframe", "{2}{W}", "3/2", "Flying", crewLine(1)) parsesTo abilities(
        Keyword.Flying,
        Keyword.Crew(1),
    )

    // CR 702.122a
    @Test
    fun tomorrowWardenRig() = vehicleCard("Tomorrow Warden Rig", "{3}{U}", "6/6", crewLine(3)) parsesTo abilities(
        Keyword.Crew(3),
    )

    // CR 702.122a, 702.19 (trample), 702.10 (haste)
    @Test
    fun stormironJuggernaut() = vehicleCard("Stormiron Juggernaut", "{7}", "7/7", "Trample, haste", crewLine(2)) parsesTo abilities(
        Keyword.Trample,
        Keyword.Haste,
        Keyword.Crew(2),
    )

    // CR 603.6a, 115.6 (up to one target: none is legal), 701.26a (tap)
    @Test
    fun swiftwindSkimmer() = vehicleCard(
        "Swiftwind Skimmer", "{2}", "2/2",
        "Flash",
        "Flying",
        "When this Vehicle enters, tap up to one target creature.",
        crewLine(1),
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(entersSelf, tapIt(target("creature")), targets = listOf(slot("creature", creature, min = 0))),
        Keyword.Crew(1),
    )

    // CR 508.1m, 603.2: the creature-or-Vehicle card has mana value 2 or less (both nouns share "card with mana value 2 or less")
    @Test
    fun shogunateSalvageRig() = vehicleCard(
        "Shogunate Salvage Rig", "{2}{W}", "3/4",
        "Whenever this Vehicle attacks, return target creature or Vehicle card with mana value 2 or less from your graveyard to your hand.",
        crewLine(2),
    ) parsesTo abilities(
        triggered(attacksSelf, move(target("card"), Zone.Hand),
            targets = listOf(slot("card", inYourGraveyard(or(creature, vehicle), manaValue(Comparison.AtMost, 2))))),
        Keyword.Crew(2),
    )

    // CR 701.21a: a sacrifice is of a permanent you control, so the cost's choice is among your own (control is implicit in the cost);
    // CR 608.2c: "If you do" follows the optional sacrifice
    @Test
    fun soulgrinderWalker() = vehicleCard(
        "Soulgrinder Walker", "{1}{B}", "4/3",
        "Whenever this Vehicle attacks, you may sacrifice another artifact or creature. If you do, put a +1/+1 counter on this Vehicle and it gains menace until end of turn.",
        crewLine(2),
    ) parsesTo abilities(
        triggered(attacksSelf,
            Step.Pay(cost = cost(CostPart.Sacrifice(what = CostObjects.Choose(filter = and(another, or(artifact, creature)), count = n(1)))),
                payer = Players.You, bind = "paid"),
            Step.If(condition = Condition.Did(name = "paid"), then = listOf(counters(Objects.Self), gains(Objects.Self, Keyword.Menace)))),
        Keyword.Crew(2),
    )

    // CR 120.3, 107.1 ("twice the number": counted as the ability resolves, CR 608.2h)
    @Test
    fun overclockRig() = vehicleCard(
        "Overclock Rig", "{4}", "5/5",
        "Menace",
        "When this Vehicle enters, it deals damage equal to twice the number of Vehicles you control to target creature or planeswalker an opponent controls.",
        "Crew 4",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(entersSelf, damage(Value.Times(count(and(vehicle, you)), 2), toObjects(target("permanent"))),
            targets = listOf(slot("permanent", and(or(creature, ObjectFilter.Type(CardType.Planeswalker)), opponents)))),
        Keyword.Crew(4),
    )

    // CR 508.1m, 603.2c ("one or more … attack": once per declaration), 111.4 (the token's ability), 702.122a
    @Test
    fun apprenticesRoadster() = vehicleCard(
        "Apprentice's Roadster", "{1}{W}{U}", "3/4",
        "Whenever one or more Vehicles you control attack, create a 1/1 colorless Pilot creature token with \"This token crews Vehicles as though its power were 2 greater.\"",
        crewLine(2),
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.Attack(and(vehicle, you)), oneOrMore = true), create(pilotToken)),
        Keyword.Crew(2),
    )

    // CR 122.6 (enters with counters), 602.2 (removing a counter is a cost), 608.2c ("Then if": checked after the draw)
    @Test
    fun vaultcrackerHauler() = vehicleCard(
        "Vaultcracker Hauler", "{2}", "4/4",
        "This Vehicle enters with three charge counters on it.",
        "{2}, {T}, Remove a charge counter from this Vehicle: Draw a card. Then if there are no charge counters on this Vehicle, create a Treasure token and a 1/1 colorless Pilot creature token with \"This token crews Vehicles as though its power were 2 greater.\"",
        "Crew 3",
    ) parsesTo abilities(
        static(Mechanic.Replacement(event = EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(kind = CounterKind.Named("charge"), amount = n(3))))),
        activated(cost(mana("{2}"), tap, CostPart.RemoveCounters(kind = CounterKind.Named("charge"), amount = n(1), from = CostObjects.Self)),
            draw(1),
            ifThen(Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("charge"), atLeast = 1))),
                createTreasure(), create(pilotToken))),
        Keyword.Crew(3),
    )

    // CR 702.122e: "becomes crewed" is its crew ability resolving; CR 115.3, 115.6: "up to one other target" (not this Vehicle, none is legal);
    // CR 205.1b: "artifact creature" keeps its other types
    @Test
    fun rallyingRig() = vehicleCard(
        "Rallying Rig", "{1}{U}", "3/4",
        "Flying",
        "Whenever this Vehicle becomes crewed, up to one other target Vehicle you control becomes an artifact creature until end of turn.",
        crewLine(3),
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(on(crewedSelf),
            doing(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature))), Affected.Objects(target("Vehicle")),
                Duration.EndOfTurn)),
            targets = listOf(slot("Vehicle", and(another, vehicle, you), min = 0))),
        Keyword.Crew(3),
    )

    // CR 702.122e, 603.2 ("for the first time each turn": the first crewing only); CR 707.2, 707.9b (the copy's exceptions), 702.122c (the
    // creatures that crewed it this turn), 611.2a (until end of turn)
    @Test
    fun mirrorlinkRig() = vehicleCard(
        "Mirrorlink Rig", "{2}{U}", "4/3",
        "Flying",
        "Whenever this Vehicle becomes crewed for the first time each turn, until end of turn, this Vehicle becomes a copy of target nonlegendary creature that crewed it this turn, except it's 4/3, it's a Vehicle artifact in addition to its other types, and it has flying.",
        crewLine(1),
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(Trigger.On(listOf(crewedSelf), nthThisTurn = 1),
            doing(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(target("creature"), listOf(
                CopyException.PowerToughness(4, 3),
                CopyException.Types(add = listOf(CardType.Artifact), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Vehicle")))),
                CopyException.AddAbilities(listOf(Keyword.Flying)),
            ))), Affected.Objects(Objects.Self), Duration.EndOfTurn)),
            targets = listOf(slot("creature", and(not(legendary), creature, ObjectFilter.CrewedThisTurn(Objects.Self))))),
        Keyword.Crew(1),
    )

    // CR 118.3, 601.2h (exiling this Vehicle and four others is the cost), 607.2a (the cards exiled with it), 111.4, 105.2c (a token that's all
    // colors), 603.7a, 603.7c (a delayed trigger about that token, once), 400.7 ("this card": the card it became in exile), 110.2 (each under
    // its owner's control)
    @Test
    fun titanframeNexus() = vehicleCard(
        "Titanframe Nexus", "{2}", "2/4",
        "{5}, Exile this Vehicle and four other artifact creatures and/or Vehicles you control: Create Titanframe, a legendary 10/10 Construct artifact creature token with flying, vigilance, trample, lifelink, and haste that's all colors. When that token leaves the battlefield, return all cards exiled with this Vehicle except this card to the battlefield tapped under their owners' control.",
        "Crew 2",
    ) parsesTo abilities(
        activated(
            cost(mana("{5}"),
                CostPart.Exile(CostObjects.Self, from = Zone.Battlefield, remember = "exiled"),
                CostPart.Exile(CostObjects.Choose(and(another, or(and(artifact, creature), vehicle)), count = n(4)), from = Zone.Battlefield, remember = "exiled")),
            doing(Effect.CreateToken(TokenSpec.Defined(FaceDefinition(name = "Titanframe", colorIndicator = allColors, supertypes = listOf(Supertype.Legendary),
                types = listOf(CardType.Artifact, CardType.Creature), subtypes = listOf(Subtype("Construct")),
                power = PrintedNumber.Fixed(10), toughness = PrintedNumber.Fixed(10),
                abilities = listOf(Keyword.Flying, Keyword.Vigilance, Keyword.Trample, Keyword.Lifelink, Keyword.Haste))), n(1), Players.You, bind = "token")),
            doing(Effect.CreateDelayedTrigger(
                Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("token")), from = Zone.Battlefield)),
                Instructions(steps = listOf(doing(Effect.Move(Objects.Except(Objects.Bound("exiled"), Objects.SelfAfter),
                    Destination(Zone.Battlefield, tapped = true, controller = Players.OwnerOf(Objects.Except(Objects.Bound("exiled"), Objects.SelfAfter)))))))))),
        Keyword.Crew(2),
    )
}
