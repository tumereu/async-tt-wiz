package arcanum.parser.sets.dft

import arcanum.api.AbilityKind
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Aetherdrift chunk 02: more Vehicles: exhaust abilities that make the Vehicle an artifact creature for good (CR 702.177a, 611.2a), triggers
 * on activating an exhaust ability, mana only for abilities (CR 106.6), entering creatures with extra counters by mana value (CR 614.1c),
 * a power defined by lands (CR 604.3), counters divided among targets (CR 601.2d), a chosen creature type for Vehicle creatures (CR 607.2d),
 * an Equipment Vehicle (CR 301.5), and becoming a creature by exiling a card from the graveyard.
 */
class DftChunk02VehiclesMoreTest {
    @Test
    fun rangersRefitter() = card(
        name = "Rangers' Refitter",
        cost = "{1}{U}",
        type = "Artifact — Vehicle",
        text = """
            Whenever you activate an exhaust ability, draw a card.
            Exhaust — {4}: This Vehicle becomes an artifact creature. Put a +1/+1 counter on it. (Activate each exhaust ability only once.)
            Crew 2
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(youActivateExhaust, draw(1)),
        exhaust(cost(mana("{4}")), becomesArtifactCreatureForGood, counters(Objects.Self)),
        Keyword.Crew(2),
    )

    // CR 106.6: the mana can be spent only on activated abilities
    @Test
    fun boomcruiser() = card(
        name = "Boomcruiser",
        cost = "{2}{R}{R}",
        type = "Artifact — Vehicle",
        text = """
            When this Vehicle enters, add four mana of any one color. Spend this mana only to activate abilities.
            Exhaust — {X}{2}{R}: This Vehicle deals X damage to any target. Put a +1/+1 counter on this Vehicle. (Activate each exhaust ability only once.)
            Crew 2
        """,
        pt = "5/5",
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(4)), restriction = ObjectFilter.AbilityOf(kind = AbilityKind.Activated)),
            Players.You))),
        exhaust(cost(mana("{X}{2}{R}")), damage(x, toAny), counters(Objects.Self), targets = listOf(anyTarget)).let {
            it.copy(instructions = it.instructions.copy(announced = listOf("X")))
        },
        Keyword.Crew(2),
    )

    @Test
    fun burnerMissile() = card(
        name = "Burner Missile",
        cost = "{1}{R}",
        type = "Artifact — Vehicle",
        text = """
            Flash
            When this Vehicle enters, target creature you control gets +2/+0 and gains trample until end of turn.
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "3/1",
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, pump(target("creature"), 2, 0, has(Keyword.Trample)), targets = listOf(slot("creature", creatureYouControl))),
        Keyword.Crew(1),
    )

    @Test
    fun clamorousIronhull() = card(
        name = "Clamorous Ironhull",
        cost = "{3}{R}",
        type = "Artifact — Vehicle",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)
            Cycling {R} ({R}, Discard this card: Draw a card.)
        """,
        pt = "6/3",
    ) parsesTo abilities(Keyword.Menace, Keyword.Crew(3), cycling("{R}"))

    // CR 400.7: it returns from the graveyard as a new object, with a finality counter (CR 122.1)
    @Test
    fun gastalThrillrider() = card(
        name = "Gastal Thrillrider",
        cost = "{2}{R}",
        type = "Artifact — Vehicle",
        text = """
            Trample, haste
            When this Vehicle enters, it becomes an artifact creature until end of turn.
            Crew 2
            {2}{R}, Discard a card: Return this card from your graveyard to the battlefield with a finality counter on it. Activate only as a sorcery.
        """,
        pt = "4/2",
    ) parsesTo abilities(
        Keyword.Trample,
        Keyword.Haste,
        triggered(entersSelf, becomesArtifactCreature(Objects.Self)),
        Keyword.Crew(2),
        activated(cost(mana("{2}{R}"), CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1)))),
            Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield, counters = listOf(arcanum.api.CountersToPut(arcanum.api.CounterKind.Finality, n(1)))))),
            rules = asSorcery),
    )

    @Test
    fun spireSpeedcycle() = card(
        name = "Spire Speedcycle",
        cost = "{4}{R}",
        type = "Artifact — Vehicle",
        text = """
            Haste
            Exhaust — Tap another untapped Mount or Vehicle you control: This Vehicle becomes an artifact creature. Put a +1/+1 counter on it for each Mount and/or Vehicle you control other than this Vehicle. (Activate each exhaust ability only once.)
            Crew 2
        """,
        pt = "5/4",
    ) parsesTo abilities(
        Keyword.Haste,
        exhaust(cost(CostPart.TapObjects(CostObjects.Choose(and(another, mountOrVehicle), count = n(1)))), becomesArtifactCreatureForGood,
            counters(Objects.Self, count(and(mountOrVehicle, you, another)))),
        Keyword.Crew(2),
    )

    // CR 604.3: its power is defined in every zone
    @Test
    fun trundlingWorldwagon() = card(
        name = "Trundling Worldwagon",
        cost = "{2}{G}",
        type = "Artifact — Vehicle",
        text = """
            This Vehicle's power is equal to the number of lands you control.
            Whenever this Vehicle enters or attacks, you may search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
            Crew 4
        """,
        pt = "*/4",
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(count(and(land, you)), null), characteristicDefining = true),
        triggered(entersOrAttacksSelf, Step.May(rampTapped, player = Players.You)),
        Keyword.Crew(4),
    )

    // CR 614.1c: replacement effects on the entering permanent, the number of counters by its mana value
    @Test
    fun thunderousVelocycle() = card(
        name = "Thunderous Velocycle",
        cost = "{1}{G}{G}",
        type = "Artifact — Vehicle",
        text = """
            Trample
            Each other Vehicle and creature you control enters with an additional +1/+1 counter on it if its mana value is 4 or less. Otherwise, it enters with three additional +1/+1 counters on it.
            Crew 3
        """,
        pt = "5/5",
    ) parsesTo abilities(
        Keyword.Trample,
        run {
            val entering = and(another, or(vehicle, creature), you)
            static(
                Mechanic.Replacement(EventPattern.ZoneChange(and(entering, manaValue(Comparison.AtMost, 4)), to = Zone.Battlefield),
                    modify = listOf(Modification.EntersWithCounters(p1p1, n(1)))),
                Mechanic.Replacement(EventPattern.ZoneChange(and(entering, manaValue(Comparison.Greater, 4)), to = Zone.Battlefield),
                    modify = listOf(Modification.EntersWithCounters(p1p1, n(3)))),
            )
        },
        Keyword.Crew(3),
    )

    @Test
    fun veloheartCycle() = card(
        name = "Veloheart Cycle",
        cost = "{2}{G}",
        type = "Artifact — Vehicle",
        text = """
            When this Vehicle enters, you gain 2 life.
            {T}: Add one mana of any color.
            Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "4/2",
    ) parsesTo abilities(triggered(entersSelf, gainLife(2)), addAnyColor, Keyword.Crew(2))

    @Test
    fun apocalypseRider() = card(
        name = "Apocalypse Rider",
        cost = "{2}{B}{R}",
        type = "Artifact — Vehicle",
        text = """
            {T}: Target creature you control with power 2 or less gains lifelink until end of turn and can't be blocked this turn.
            Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "6/5",
    ) parsesTo abilities(
        activated(cost(tap), untilEndOfTurn(target("creature"), has(Keyword.Lifelink), Mechanic.CantBeBlocked),
            targets = listOf(slot("creature", and(creature, you, power(Comparison.AtMost, 2))))),
        Keyword.Crew(3),
    )

    @Test
    fun boostedSchooner() = card(
        name = "Boosted Schooner",
        cost = "{1}{U}{R}",
        type = "Artifact — Vehicle",
        text = """
            Menace
            Whenever you attack, draw a card, then discard a card.
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "3/3",
    ) parsesTo abilities(Keyword.Menace, triggered(youAttack, draw(1), discard(1)), Keyword.Crew(1))

    // CR 601.2d: the two counters are divided as the targets are chosen, at least one each
    @Test
    fun cloudtrackSkycycle() = card(
        name = "Cloudtrack Skycycle",
        cost = "{2}{R}{W}",
        type = "Artifact — Vehicle",
        text = """
            Flying
            When this Vehicle enters, distribute two +1/+1 counters among one or two other target Vehicles and/or creatures you control.
            Crew 1
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, doing(Effect.PutCounters(target("permanents"), p1p1, Value.DividedShare)),
            targets = listOf(slot("permanents", and(another, or(vehicle, creature), you), min = 1, max = 2).copy(divided = n(2)))),
        Keyword.Crew(1),
    )

    @Test
    fun debrisScarab() = card(
        name = "Debris Scarab",
        cost = "{2}{B}{G}",
        type = "Artifact — Vehicle",
        text = """
            Trample
            When this Vehicle enters, each opponent loses 3 life and you gain 3 life.
            Crew 2
        """,
        pt = "6/6",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf, loseLife(3, Players.Each(PlayerFilter.Opponent)), gainLife(3)),
        Keyword.Crew(2),
    )

    // CR 107.3m: X in its enters trigger is the X it was cast with
    @Test
    fun duneWanderer() = card(
        name = "Dune Wanderer",
        cost = "{X}{W}{B}",
        type = "Artifact — Vehicle",
        text = """
            When this Vehicle enters, return target artifact or creature card with mana value X or less from your graveyard to the battlefield.
            Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Battlefield), targets = listOf(slot("card",
            inYourGraveyard(or(artifact, creature), ObjectFilter.ManaValue(Comparison.AtMost, Value.CastX(Objects.Self)))))),
        Keyword.Crew(2),
    )

    // CR 701.23: a search that may not happen; the shuffle only if it did
    @Test
    fun pathlightPathmaker() = card(
        name = "Pathlight Pathmaker",
        cost = "{4}{W}{U}",
        type = "Artifact — Vehicle",
        text = """
            Vigilance
            When this Vehicle enters, you may search your library for an artifact card and reveal it. Put it onto the battlefield if its mana value is 2 or less. Otherwise, put it into your hand. If you search your library this way, shuffle.
            Crew 2
        """,
        pt = "6/5",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(entersSelf, Step.May(listOf(
            search(artifact, reveal = true),
            Step.If(Condition.ObjectMatches(Objects.Bound("found"), manaValue(Comparison.AtMost, 2)),
                then = listOf(move(Objects.Bound("found"), Zone.Battlefield)), otherwise = listOf(move(Objects.Bound("found"), Zone.Hand))),
            shuffle,
        ), player = Players.You)),
        Keyword.Crew(2),
    )

    @Test
    fun hauntedHotrod() = card(
        name = "Haunted Hotrod",
        cost = "{1}{U}{B}",
        type = "Artifact — Vehicle",
        text = """
            Whenever you attack, target creature you control gets +1/+0 and gains deathtouch until end of turn. Untap it.
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(youAttack, pump(target("creature"), 1, 0, has(Keyword.Deathtouch)), untapIt(target("creature")),
            targets = listOf(slot("creature", creatureYouControl))),
        Keyword.Crew(1),
    )

    @Test
    fun rangersAetherhub() = card(
        name = "Rangers' Aetherhub",
        cost = "{1}{G}{U}",
        type = "Artifact — Vehicle",
        text = """
            Vigilance
            Whenever you activate an exhaust ability, create a 1/1 colorless Thopter artifact creature token with flying.
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "3/5",
    ) parsesTo abilities(Keyword.Vigilance, triggered(youActivateExhaust, create(thopter)), Keyword.Crew(1))

    @Test
    fun rocketeerBoostcart() = card(
        name = "Rocketeer Boostcart",
        cost = "{R}{G}",
        type = "Artifact — Vehicle",
        text = """
            Whenever this Vehicle attacks, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            Exhaust — {3}: This Vehicle becomes an artifact creature. Put a +1/+1 counter on it. (Activate each exhaust ability only once.)
            Crew 1
        """,
        pt = "3/2",
    ) parsesTo abilities(
        triggered(attacksSelf, createTreasure),
        exhaust(cost(mana("{3}")), becomesArtifactCreatureForGood, counters(Objects.Self)),
        Keyword.Crew(1),
    )

    @Test
    fun thunderingBroodcart() = card(
        name = "Thundering Broodcart",
        cost = "{2}{B}{B}{G}{G}",
        type = "Artifact — Vehicle",
        text = """
            Reach, menace
            When this Vehicle enters, destroy target nonland permanent an opponent controls with mana value 4 or less.
            Crew 3
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
        pt = "6/5",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Menace,
        triggered(entersSelf, destroy(target("permanent")), targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents, manaValue(Comparison.AtMost, 4))))),
        Keyword.Crew(3),
        cycling("{2}"),
    )

    // CR 607.2d: the type chosen as it entered; CR 613.1d: added in layer 4
    @Test
    fun lifeforgeEngine() = card(
        name = "Lifeforge Engine",
        cost = "{3}",
        type = "Artifact — Vehicle",
        text = """
            As this Vehicle enters, choose a creature type.
            Vehicle creatures you control are the chosen creature type in addition to their other types.
            Each creature you control of the chosen type other than this Vehicle gets +1/+1.
            Crew 3
        """,
        pt = "4/4",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.CreatureType, bind = "type", chooser = Players.You, remember = true)))))),
        static(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Remembered("type"))), affects = Objects.Each(and(vehicle, creature, you))),
        static(pt(1, 1), affects = Objects.Each(and(creature, you, ObjectFilter.Sub(SubtypeRef.Remembered("type")), another))),
        Keyword.Crew(3),
    )

    @Test
    fun marshalsPathrunner() = card(
        name = "Marshals' Pathrunner",
        cost = "{3}",
        type = "Artifact — Vehicle",
        text = """
            When this Vehicle enters, search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            Exhaust — {W}{U}{B}{R}{G}: This Vehicle becomes an artifact creature. Put two +1/+1 counters on it. (Activate each exhaust ability only once.)
            Crew 5
        """,
        pt = "6/5",
    ) parsesTo abilities(
        triggered(entersSelf, search(basicLand, reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        exhaust(cost(mana("{W}{U}{B}{R}{G}")), becomesArtifactCreatureForGood, counters(Objects.Self, 2)),
        Keyword.Crew(5),
    )

    // CR 301.5c: an Equipment that is a creature can't equip a creature (the reminder text says so)
    @Test
    fun roverEdges() = card(
        name = "Rover Edges",
        cost = "{3}",
        type = "Artifact — Equipment Vehicle",
        text = """
            Double strike
            Equipped creature has double strike.
            Equip {4}
            Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn. Creatures can't be attached to other permanents.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        static(has(Keyword.DoubleStrike), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{4}")),
        Keyword.Crew(2),
    )

    @Test
    fun skyboxBarge() = card(
        name = "Skybox Barge",
        cost = "{5}",
        type = "Artifact — Vehicle",
        text = """
            Flying
            Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
        pt = "4/4",
    ) parsesTo abilities(Keyword.Flying, Keyword.Crew(2), cycling("{2}"))

    @Test
    fun grumblerumbler() = card(
        name = "Grumblerumbler",
        cost = "{4}{G}",
        type = "Artifact — Vehicle",
        text = """
            Vigilance, trample
            Exile an artifact or creature card from your graveyard: This Vehicle becomes an artifact creature until end of turn.
            Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "7/6",
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Trample,
        activated(cost(CostPart.Exile(CostObjects.Choose(or(artifact, creature), count = n(1)), from = Zone.Graveyard)), becomesArtifactCreature(Objects.Self)),
        Keyword.Crew(3),
    )
}
