package arcanum.parser.sets.dft

import arcanum.api.AbilityKind
import arcanum.api.AbilityLabel
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Aetherdrift chunk 08: exhaust (CR 702.177) and the white cards around Vehicles: exhaust abilities with counters, tokens, haste and
 * double strike, triggers on activating exhaust abilities (from the graveyard too, CR 113.6c), exhaust abilities that cost less, a copy of the
 * next exhaust ability (CR 603.7, 707.10), exhaust abilities activated as though they hadn't been (CR 702.177b), spells costing less with a
 * tapped target, and power counted from artifacts.
 */
class DftChunk08ExhaustTest {
    @Test
    fun pathlightSynergist() = card(
        name = "Pathlight Synergist",
        cost = "{3}{W}",
        type = "Artifact Creature — Robot Artificer",
        text = """
            Flying
            This creature gets +1/+0 for each artifact you control.
        """,
        pt = "0/4",
    ) parsesTo abilities(Keyword.Flying, static(Mechanic.ModifyPowerToughness(count(and(artifact, you)), n(0))))

    @Test
    fun lotuswingDisciple() = card(
        name = "Lotuswing Disciple",
        cost = "{2}{W}",
        type = "Creature — Bird Cleric",
        text = """
            Flying
            When this creature enters, target creature or Vehicle gains lifelink and indestructible until end of turn.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, gains(target("permanent"), Keyword.Lifelink, Keyword.Indestructible), targets = listOf(slot("permanent", creatureOrVehicle))),
    )

    // CR 601.2f: the reduction applies once the target is chosen
    @Test
    fun journeysHalt() = card(
        name = "Journey's Halt",
        cost = "{4}{W}",
        type = "Instant",
        text = """
            This spell costs {3} less to cast if it targets a tapped permanent.
            Exile target creature or Vehicle.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(isSelf, CostChange.Less(ManaCost.parse("{3}")!!, n(1))), condition = Condition.ObjectMatches(target("permanent"), tapped)),
        spell(move(target("permanent"), Zone.Exile), targets = listOf(slot("permanent", creatureOrVehicle))),
    )

    @Test
    fun sunglassDawnTyrant() = card(
        name = "Sunglass, Dawn Tyrant",
        cost = "{1}{W}",
        type = "Legendary Artifact Creature — Construct",
        pt = "3/3",
    ) parsesTo abilities()

    @Test
    fun voyagerSteelwelder() = card(
        name = "Voyager Steelwelder",
        cost = "{2}{W}",
        type = "Artifact Creature — Robot Artificer",
        text = "Artifact spells you cast cost {1} less to cast.",
        pt = "2/4",
    ) parsesTo abilities(static(Mechanic.CostChange(and(artifact, spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))))

    @Test
    fun sharpEyedBuccaneer() = card(
        name = "Sharp-Eyed Buccaneer",
        cost = "{2}{U}",
        type = "Creature — Octopus Pirate",
        text = """
            Vigilance
            Exhaust — {1}{U}: Draw a card, then discard a card. Put a +1/+1 counter on this creature. (Activate each exhaust ability only once.)
        """,
        pt = "2/3",
    ) parsesTo abilities(Keyword.Vigilance, exhaust(cost(mana("{1}{U}")), draw(1), discard(1), counters(Objects.Self)))

    // CR 107.3a: X announced with the cost
    @Test
    fun mindbrookMerfolk() = card(
        name = "Mindbrook Merfolk",
        cost = "{U}",
        type = "Creature — Merfolk Wizard",
        text = "Exhaust — {X}{U}{U}, {T}: Draw X cards. Put a +1/+1 counter on each Merfolk creature you control. (Activate each exhaust ability only once.)",
        pt = "1/1",
    ) parsesTo abilities(
        exhaust(cost(mana("{X}{U}{U}"), tap), doing(Effect.Draw(Players.You, x)), counters(Objects.Each(and(sub("Merfolk"), creature, you)))).let {
            it.copy(instructions = it.instructions.copy(announced = listOf("X")))
        },
    )

    // CR 115.1: any number of target players, each milling from their own count
    @Test
    fun riverwashMonument() = card(
        name = "Riverwash Monument",
        cost = "{1}{U}",
        type = "Artifact",
        text = """
            {1}, {T}: Any number of target players each mill two cards. (Each of them puts the top two cards of their library into their graveyard.)
            Exhaust — {2}{U}{U}, {T}: Any number of target players each mill cards equal to the number of cards in their graveyard. (Activate each exhaust ability only once.)
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap), mill(2, Players.Target("players")), targets = listOf(anyNumberOfPlayers)),
        exhaust(cost(mana("{2}{U}{U}"), tap), Step.ForEach(Each.Players(Players.Target("players")), bind = "player", together = true, steps = listOf(
            doing(Effect.Mill(Players.Bound("player"), count(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("player"))))))))),
            targets = listOf(anyNumberOfPlayers)),
    )

    @Test
    fun sabotageTactician() = card(
        name = "Sabotage Tactician",
        cost = "{2}{U}{U}",
        type = "Creature — Vedalken Ranger",
        text = """
            Flying, vigilance
            Whenever one or more creatures attack you, those creatures get -1/-0 until end of turn.
            Exhaust — {5}{U}{U}: Put three +1/+1 counters on this creature. (Activate each exhaust ability only once.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        triggered(Trigger.On(EventPattern.Attack(creature, target = RecipientFilter(players = PlayerFilter.You)), oneOrMore = true),
            pump(Objects.EventObject(arcanum.api.EventRole.Object), -1, 0)),
        exhaust(cost(mana("{5}{U}{U}")), counters(Objects.Self, 3)),
    )

    @Test
    fun skydashEngineer() = card(
        name = "Skydash Engineer",
        cost = "{1}{U}",
        type = "Creature — Human Pilot",
        text = """
            Flying
            Exhaust — {4}{U}: Put two +1/+1 counters on this creature. (Activate each exhaust ability only once.)
        """,
        pt = "1/3",
    ) parsesTo abilities(Keyword.Flying, exhaust(cost(mana("{4}{U}")), counters(Objects.Self, 2)))

    // CR 603.4: "if it's not their turn" is checked as it triggers and resolves
    @Test
    fun adrenalineRider() = card(
        name = "Adrenaline Rider",
        cost = "{2}{R}",
        type = "Creature — Minotaur Pilot",
        text = """
            Whenever a player casts a spell, if it's not their turn, this creature deals 4 damage to them.
            Whenever you activate an exhaust ability, put a +1/+1 counter on this creature.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        triggered(on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Any)), damage(4, Recipients(players = Players.EventPlayer)),
            interveningIf = Condition.PlayerMatches(Players.EventPlayer, PlayerFilter.Not(PlayerFilter.Is(Players.Active)))),
        triggered(youActivateExhaust, counters(Objects.Self)),
    )

    @Test
    fun draconauticEngineer() = card(
        name = "Draconautic Engineer",
        cost = "{1}{R}",
        type = "Creature — Goblin Artificer",
        text = """
            Exhaust — {R}: Other creatures you control gain haste until end of turn. Put a +1/+1 counter on this creature. (Activate each exhaust ability only once.)
            Exhaust — {3}{R}: Create a 4/4 red Dinosaur Dragon creature token with flying.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        exhaust(cost(mana("{R}")), gains(Objects.Each(and(another, creature, you)), Keyword.Haste), counters(Objects.Self)),
        exhaust(cost(mana("{3}{R}")), create(creatureToken("Dinosaur Dragon", listOf(Color.Red), 4, 4, abilities = listOf(Keyword.Flying)))),
    )

    @Test
    fun pacesetterChampion() = card(
        name = "Pacesetter Champion",
        cost = "{2}{R}",
        type = "Creature — Human Pilot",
        text = "Exhaust — {2}{R}: Put a +1/+1 counter on this creature. It gains double strike until end of turn. (Activate each exhaust ability only once.)",
        pt = "2/3",
    ) parsesTo abilities(exhaust(cost(mana("{2}{R}")), counters(Objects.Self), gains(Objects.Self, Keyword.DoubleStrike)))

    @Test
    fun prowcatcherExpert() = card(
        name = "Prowcatcher Expert",
        cost = "{1}{R}",
        type = "Creature — Goblin Warrior",
        text = """
            Haste
            Exhaust — {3}{R}: Put two +1/+1 counters on this creature. (Activate each exhaust ability only once.)
        """,
        pt = "2/1",
    ) parsesTo abilities(Keyword.Haste, exhaust(cost(mana("{3}{R}")), counters(Objects.Self, 2)))

    // CR 113.6c: the return works from the graveyard
    @Test
    fun afterburnerAdept() = card(
        name = "Afterburner Adept",
        cost = "{2}{G}",
        type = "Creature — Goblin Artificer",
        text = """
            Exhaust — {2}{G}{G}: Put two +1/+1 counters on this creature. (Activate each exhaust ability only once.)
            Whenever you activate an exhaust ability, return this card from your graveyard to the battlefield.
        """,
        pt = "4/2",
    ) parsesTo abilities(
        exhaust(cost(mana("{2}{G}{G}")), counters(Objects.Self, 2)),
        triggered(youActivateExhaust, move(Objects.Self, Zone.Battlefield, postBind = "returned")).copy(functionsIn = listOf(Zone.Graveyard)),
    )

    @Test
    fun greenbeltWarden() = card(
        name = "Greenbelt Warden",
        cost = "{1}{G}",
        type = "Creature — Elf Ranger",
        text = """
            {G}: Target creature gains trample until end of turn.
            Exhaust — {3}{G}: Put three +1/+1 counters on this creature. (Activate each exhaust ability only once.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(mana("{G}")), gains(target("creature"), Keyword.Trample), targets = listOf(slot("creature", creature))),
        exhaust(cost(mana("{3}{G}")), counters(Objects.Self, 3)),
    )

    @Test
    fun perilOfTheDunes() = card(
        name = "Peril of the Dunes",
        cost = "{3}{G}",
        type = "Creature — Wurm",
        text = """
            Reach, trample
            Exhaust — {6}{G}: Put three +1/+1 counters on this creature. (Activate each exhaust ability only once.)
        """,
        pt = "4/4",
    ) parsesTo abilities(Keyword.Reach, Keyword.Trample, exhaust(cost(mana("{6}{G}")), counters(Objects.Self, 3)))

    @Test
    fun stampedingScurrier() = card(
        name = "Stampeding Scurrier",
        cost = "{G}",
        type = "Creature — Mouse",
        text = "Exhaust — {3}{G}: Put a +1/+1 counter on this creature. Create a 3/3 green Elephant creature token. (Activate each exhaust ability only once.)",
        pt = "1/1",
    ) parsesTo abilities(exhaust(cost(mana("{3}{G}")), counters(Objects.Self), create(elephant)))

    // CR 601.2f, 602.2b: the reduction for exhaust abilities of other permanents
    @Test
    fun boomProfessor() = card(
        name = "Boom Professor",
        cost = "{1}{R}{G}",
        type = "Creature — Goblin Advisor",
        text = """
            Exhaust abilities of other permanents you control cost {2} less to activate.
            Exhaust — {4}{R}{G}: Creatures and Vehicles you control gain trample until end of turn. Put two +1/+1 counters on this creature. (Activate each exhaust ability only once.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.AbilityOf(source = and(another, permanentFilter, you), label = AbilityLabel.Exhaust),
            CostChange.Less(ManaCost.parse("{2}")!!, n(1)))),
        exhaust(cost(mana("{4}{R}{G}")), gains(Objects.Each(and(creatureOrVehicle, you)), Keyword.Trample), counters(Objects.Self, 2)),
    )

    @Test
    fun shutterLauncher() = card(
        name = "Shutter Launcher",
        cost = "{3}",
        type = "Artifact Creature — Construct",
        text = "Exhaust — {3}: Put a +1/+1 counter on this creature. Create a 1/1 colorless Thopter artifact creature token with flying. (Activate each exhaust ability only once.)",
        pt = "2/2",
    ) parsesTo abilities(exhaust(cost(mana("{3}")), counters(Objects.Self), create(thopter)))

    // CR 603.7: a delayed trigger for the next such activation this turn; CR 707.10: the copy, with new targets
    @Test
    fun pitAutomata() = card(
        name = "Pit Automata",
        cost = "{2}",
        type = "Artifact Creature — Construct",
        text = """
            Defender
            {T}: Add {C}{C}. Spend this mana only to activate abilities.
            {2}, {T}: When you next activate an exhaust ability that isn't a mana ability this turn, copy it. You may choose new targets for the copy.
        """,
        pt = "0/4",
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}{C}")!!), restriction = ObjectFilter.AbilityOf(kind = AbilityKind.Activated)),
            Players.You))),
        activated(cost(mana("{2}"), tap), doing(Effect.CreateDelayedTrigger(
            on(EventPattern.Activate(ObjectFilter.Any, PlayerFilter.You, label = AbilityLabel.Exhaust)),
            Instructions(steps = listOf(doing(Effect.CopySpell(Objects.EventObject(arcanum.api.EventRole.Object), Players.You, newTargets = true)))),
            duration = Duration.EndOfTurn))),
    )

    // CR 702.177b: only while you haven't begun to activate an exhaust ability this turn
    @Test
    fun elvishRefitter() = card(
        name = "Elvish Refitter",
        cost = "{2}{G}",
        type = "Creature — Elf Druid",
        text = """
            During your turn, as long as you haven't activated an exhaust ability this turn, you may activate exhaust abilities as though they haven't been activated.
            Exhaust — {1}{G}: Put a +1/+1 counter on this creature. (Activate each exhaust ability only once.)
        """,
        pt = "2/3",
    ) parsesTo abilities(
        static(Mechanic.AsThoughNotActivated(AbilityLabel.Exhaust), affects = Objects.Each(and(permanentFilter, you)), condition = Condition.And(listOf(Condition.YourTurn,
            Condition.Not(Condition.HappenedThisTurn(EventPattern.Activate(ObjectFilter.Any, PlayerFilter.You, manaAbilities = true, label = AbilityLabel.Exhaust),
                atLeast = 1, measure = Measure.Events))))),
        exhaust(cost(mana("{1}{G}")), counters(Objects.Self)),
    )

    @Test
    fun greasegripGoblin() = card(
        name = "Greasegrip Goblin",
        cost = "{R}",
        type = "Creature — Goblin Artificer",
        text = "Exhaust — {2}{R}: Discard up to two cards, then draw that many cards. Put a +1/+1 counter on this creature. (Activate each exhaust ability only once.)",
        pt = "2/1",
    ) parsesTo abilities(
        exhaust(cost(mana("{2}{R}")),
            Step.Choose(arcanum.api.Choice.Objects(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), min = n(0), max = n(2)), bind = "chosen", chooser = Players.You),
            doing(Effect.Discard(Players.You, n(2), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
            doing(Effect.Draw(Players.You, Value.Count(Objects.Bound("chosen")))),
            counters(Objects.Self)),
    )

    private val anyNumberOfPlayers = TargetSlot("players", TargetKind.Players(PlayerFilter.Any), min = n(0), max = null)
}
