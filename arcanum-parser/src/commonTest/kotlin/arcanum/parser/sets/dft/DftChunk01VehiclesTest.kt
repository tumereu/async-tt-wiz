package arcanum.parser.sets.dft

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
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
 * Aetherdrift chunk 01: Vehicles with crew (CR 702.122) and what they do as they enter or attack: exile until this Vehicle leaves
 * (CR 610.3), a chosen card name making abilities cost more (CR 601.2f), a Vehicle that is a creature during other players' turns, control
 * for as long as you control it, a second card drawn each turn, a destroyed creature counted by its last known controller (CR 608.2h), and
 * power reduced by your life total.
 */
class DftChunk01VehiclesTest {
    @Test
    fun skywardPatrolUnit() = card(
        name = "Skyward Patrol Unit",
        cost = "{2}{W}",
        type = "Artifact — Vehicle",
        text = """
            Flying, vigilance
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "3/3",
    ) parsesTo abilities(Keyword.Flying, Keyword.Vigilance, Keyword.Crew(1))

    // CR 610.3: the exiled card returns when the Vehicle leaves the battlefield
    @Test
    fun holdingChariot() = card(
        name = "Holding Chariot",
        cost = "{4}{W}{W}",
        type = "Artifact — Vehicle",
        text = """
            When this Vehicle enters, exile target artifact or creature an opponent controls until this Vehicle leaves the battlefield.
            Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)
            Cycling {W} ({W}, Discard this card: Draw a card.)
        """,
        pt = "6/6",
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", and(or(artifact, creature), opponents)))),
        Keyword.Crew(3),
        cycling("{W}"),
    )

    @Test
    fun rescueEngine() = card(
        name = "Rescue Engine",
        cost = "{4}{W}",
        type = "Artifact — Vehicle",
        text = """
            Other artifact creatures you control get +2/+2.
            Whenever this Vehicle attacks, return up to one target artifact card from your graveyard to the battlefield.
            Crew 6
        """,
        pt = "6/10",
    ) parsesTo abilities(
        static(pt(2, 2), affects = Objects.Each(and(another, artifact, creature, you))),
        triggered(attacksSelf, move(target("card"), Zone.Battlefield), targets = listOf(slot("card", inYourGraveyard(artifact), min = 0))),
        Keyword.Crew(6),
    )

    // CR 601.2f: the increase applies to activated abilities of sources with that name wherever they are
    @Test
    fun seerSkyChariot() = card(
        name = "Seer-Sky Chariot",
        cost = "{1}{W}",
        type = "Artifact — Vehicle",
        text = """
            Flying
            As this Vehicle enters, choose a nonland card name.
            Activated abilities of sources with the chosen name cost {2} more to activate.
            Crew 2
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flying,
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.CardName(nonland), bind = "name", chooser = Players.You, remember = true)))))),
        static(Mechanic.CostChange(ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = ObjectFilter.RememberedName("name")),
            arcanum.api.CostChange.More(arcanum.api.ManaCost.parse("{2}")!!, n(1)))),
        Keyword.Crew(2),
    )

    @Test
    fun spotlightScouter() = card(
        name = "Spotlight Scouter",
        cost = "{1}{W}",
        type = "Artifact — Vehicle",
        text = """
            When this Vehicle enters, scry 2. (Look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "3/2",
    ) parsesTo abilities(triggered(entersSelf, scry(2)), Keyword.Crew(1))

    // CR 301.7b: it has its printed power and toughness once it is a creature
    @Test
    fun voyagerSkimcar() = card(
        name = "Voyager Skimcar",
        cost = "{W}",
        type = "Artifact — Vehicle",
        text = """
            When this Vehicle enters, scry 1.
            Tap three other untapped creatures you control: Until end of turn, this Vehicle becomes an artifact creature and gains flying. Put a +1/+1 counter on it.
            Crew 1
        """,
        pt = "2/3",
    ) parsesTo abilities(
        triggered(entersSelf, scry(1)),
        activated(cost(CostPart.TapObjects(CostObjects.Choose(and(another, creature), count = n(3)))),
            untilEndOfTurn(Objects.Self, Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)), has(Keyword.Flying)),
            counters(Objects.Self)),
        Keyword.Crew(1),
    )

    @Test
    fun hullskimmer() = card(
        name = "Hullskimmer",
        cost = "{3}{U}{U}",
        type = "Artifact — Vehicle",
        text = """
            Flying
            When this Vehicle enters, draw two cards.
            Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "3/2",
    ) parsesTo abilities(Keyword.Flying, triggered(entersSelf, draw(2)), Keyword.Crew(3))

    @Test
    fun moonlitMangler() = card(
        name = "Moonlit Mangler",
        cost = "{1}{U}",
        type = "Artifact — Vehicle",
        text = """
            During turns other than yours, this Vehicle is an artifact creature.
            Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        static(Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)), condition = Condition.Not(Condition.YourTurn)),
        Keyword.Crew(2),
    )

    @Test
    fun seizureEngine() = card(
        name = "Seizure Engine",
        cost = "{3}{U}{U}",
        type = "Artifact — Vehicle",
        text = """
            When this Vehicle enters, gain control of target creature an opponent controls for as long as you control this Vehicle. That creature can't attack or block for as long as you control this Vehicle.
            Crew 3
        """,
        pt = "5/5",
    ) parsesTo abilities(
        run {
            val asLong = Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, you))
            triggered(entersSelf,
                doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("creature")), asLong)),
                doing(Effect.ApplyMechanics(listOf(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any)), Affected.Objects(target("creature")), asLong)),
                targets = listOf(slot("creature", creatureOpponentControls)))
        },
        Keyword.Crew(3),
    )

    @Test
    fun thopterAssembler() = card(
        name = "Thopter Assembler",
        cost = "{2}{U}",
        type = "Artifact — Vehicle",
        text = """
            Flying
            Whenever you draw your second card each turn, create a 1/1 colorless Thopter artifact creature token with flying.
            Crew 2
        """,
        pt = "4/4",
    ) parsesTo abilities(Keyword.Flying, triggered(drawSecondCard, create(thopter)), Keyword.Crew(2))

    @Test
    fun carrionHauler() = card(
        name = "Carrion Hauler",
        cost = "{2}{B}",
        type = "Artifact — Vehicle",
        text = """
            When this Vehicle enters, mill two cards. Then return a creature or Vehicle card from your graveyard to your hand. (To mill two cards, put the top two cards of your library into your graveyard.)
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "3/2",
    ) parsesTo abilities(
        triggered(entersSelf, mill(2),
            Step.Choose(Choice.Objects(inYourGraveyard(creatureOrVehicle), min = n(1), max = n(1)), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand)),
        Keyword.Crew(1),
    )

    @Test
    fun cryptcallChariot() = card(
        name = "Cryptcall Chariot",
        cost = "{3}{B}",
        type = "Artifact — Vehicle",
        text = """
            Menace
            Whenever you discard one or more cards, create that many tapped 2/2 black Zombie creature tokens.
            Crew 2
        """,
        pt = "5/5",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(arcanum.api.Trigger.On(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.You), oneOrMore = true),
            create(zombie, count = Value.Count(Objects.EventObject(arcanum.api.EventRole.After)), tapped = true)),
        Keyword.Crew(2),
    )

    // CR 608.2h: "a creature you controlled" is read from the destroyed creature as it last existed
    @Test
    fun infernalJunker() = card(
        name = "Infernal Junker",
        cost = "{6}{B}",
        type = "Artifact — Vehicle",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            When this Vehicle enters, for each player, destroy up to one target creature that player controls. If a creature you controlled was destroyed this way, put two +1/+1 counters on this Vehicle.
            Crew 2
        """,
        pt = "4/3",
    ) parsesTo abilities(
        Keyword.Affinity(artifact, "artifacts"),
        triggered(entersSelf,
            doing(Effect.Destroy(target("creature"), preBind = "destroyed")),
            Step.If(Condition.Compare(Value.Count(Objects.Where(Objects.Bound("destroyed"), and(creature, you))), Comparison.AtLeast, n(1)),
                then = listOf(counters(Objects.Self, 2))),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.SlotPlayer))), min = 0)
                .copy(perPlayer = PlayerFilter.Any))),
        Keyword.Crew(2),
    )

    @Test
    fun theFinalLap() = card(
        name = "The Final Lap",
        cost = "{B}",
        type = "Legendary Artifact — Vehicle",
        text = """
            The Final Lap gets -X/-X, where X is your life total.
            {2}{B}, Pay 2 life: Draw a card.
            Crew 2
        """,
        pt = "13/13",
    ) parsesTo abilities(
        run {
            val minusLife = Value.Times(Value.LifeOf(Players.You), -1)
            static(Mechanic.ModifyPowerToughness(minusLife, minusLife))
        },
        activated(cost(mana("{2}{B}"), CostPart.PayLife(n(2))), draw(1)),
        Keyword.Crew(2),
    )

    @Test
    fun ripclawRustler() = card(
        name = "Ripclaw Rustler",
        cost = "{3}{B}",
        type = "Artifact — Vehicle",
        text = """
            When this Vehicle enters, each opponent discards a card.
            Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "4/3",
    ) parsesTo abilities(triggered(entersSelf, discard(1, Players.Each(PlayerFilter.Opponent))), Keyword.Crew(2))

    @Test
    fun sidewalkRambler() = card(
        name = "Sidewalk Rambler",
        cost = "{4}{W}",
        type = "Artifact — Vehicle",
        text = """
            When this Vehicle enters, create a 1/1 colorless Thopter artifact creature token with flying.
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """,
        pt = "5/4",
    ) parsesTo abilities(triggered(entersSelf, create(thopter)), Keyword.Crew(1))
}
