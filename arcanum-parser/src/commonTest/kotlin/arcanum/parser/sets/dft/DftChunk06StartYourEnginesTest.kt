package arcanum.parser.sets.dft

import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
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
 * Aetherdrift chunk 06: start your engines! (CR 702.179) and max speed (CR 702.178) on white, blue and black cards: max speed statics,
 * activated abilities (from the graveyard too, CR 702.178b), triggers and a replacement; values read from your speed (CR 702.179f) and a
 * speed reduced (not below 1).
 */
class DftChunk06StartYourEnginesTest {
    private val sye = Keyword.StartYourEngines

    @Test
    fun leoninPathfinder() = card(
        name = "Leonin Pathfinder",
        cost = "{1}{W}",
        type = "Creature — Cat Scout",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            During your turn, this creature has first strike.
            Max speed — {3}, Exile this card from your graveyard: Draw a card.
        """,
        pt = "2/2",
    ) parsesTo abilities(sye, static(has(Keyword.FirstStrike), condition = Condition.YourTurn), surveyorDraw)

    // CR 702.178b: the permission works from the graveyard, so the max speed ability does too
    @Test
    fun gleamwheelUpgrades() = card(
        name = "Gleamwheel Upgrades",
        cost = "{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature or Vehicle
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            Enchanted permanent gets +1/+1 and has vigilance.
            Max speed — You may cast this card from your graveyard.
        """,
    ) parsesTo abilities(
        enchantCreatureOrVehicle,
        sye,
        static(pt(1, 1), has(Keyword.Vigilance), affects = enchanted),
        maxSpeed(static(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, holder = PlayerFilter.You))),
    )

    @Test
    fun roostingBot() = card(
        name = "Roosting Bot",
        cost = "{W}",
        type = "Artifact Creature — Robot",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            When this creature dies, create a 1/1 colorless Servo artifact creature token.
            Max speed — This creature gets +1/+0.
        """,
        pt = "1/1",
    ) parsesTo abilities(sye, triggered(diesSelf, create(servo)), maxSpeed(static(pt(1, 0))))

    @Test
    fun hazardousSnare() = card(
        name = "Hazardous Snare",
        cost = "{2}{W}",
        type = "Artifact",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            When this artifact enters, exile target nonland permanent an opponent controls until this artifact leaves the battlefield.
            Max speed — {T}: Put a +1/+1 counter on target creature or Vehicle you control. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        sye,
        triggered(entersSelf, doing(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", and(nonland, permanentFilter, opponents)))),
        maxSpeed(activated(cost(tap), counters(target("permanent")), targets = listOf(slot("permanent", and(creatureOrVehicle, you))), rules = asSorcery)),
    )

    @Test
    fun honorOfTheHighway() = card(
        name = "Honor of the Highway",
        cost = "{3}{W}",
        type = "Creature — Zombie Cat Warrior",
        text = """
            Vigilance
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            Max speed — At the beginning of combat on your turn, target creature or Vehicle you control gains double strike until end of turn.
        """,
        pt = "2/5",
    ) parsesTo abilities(
        Keyword.Vigilance,
        sye,
        maxSpeed(triggered(beginningOfCombatOnYourTurn, gains(target("permanent"), Keyword.DoubleStrike), targets = listOf(slot("permanent", and(creatureOrVehicle, you))))),
    )

    @Test
    fun swiftfeatherAssailant() = card(
        name = "Swiftfeather Assailant",
        cost = "{3}{W}",
        type = "Creature — Bird Warrior",
        text = """
            Flying
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            Max speed — This creature gets +0/+1 and has vigilance.
        """,
        pt = "3/3",
    ) parsesTo abilities(Keyword.Flying, sye, maxSpeed(static(pt(0, 1), has(Keyword.Vigilance))))

    @Test
    fun aetherSiphoner() = card(
        name = "Aether Siphoner",
        cost = "{1}{U}{U}",
        type = "Artifact",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            {2}, {T}: Draw a card.
            Max speed — Whenever you draw a card, each opponent mills two cards. (Each opponent puts the top two cards of their library into their graveyard.)
        """,
    ) parsesTo abilities(
        sye,
        activated(cost(mana("{2}"), tap), draw(1)),
        maxSpeed(triggered(on(EventPattern.Draw(PlayerFilter.You)), mill(2, Players.Each(PlayerFilter.Opponent)))),
    )

    @Test
    fun glitchwraithPathfinder() = card(
        name = "Glitchwraith Pathfinder",
        cost = "{2}{U}",
        type = "Creature — Spirit Scout",
        text = """
            Flying
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            Max speed — {3}, Exile this card from your graveyard: Draw a card.
        """,
        pt = "2/2",
    ) parsesTo abilities(Keyword.Flying, sye, surveyorDraw)

    // CR 707.10: a copy of a permanent spell becomes a token (CR 707.10c)
    @Test
    fun slickMimic() = card(
        name = "Slick Mimic",
        cost = "{1}{U}",
        type = "Creature — Ooze",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            Max speed — {1}, Sacrifice this creature: Copy target spell you control. You may choose new targets for the copy. (A copy of a permanent spell becomes a token.)
        """,
        pt = "1/3",
    ) parsesTo abilities(
        sye,
        maxSpeed(activated(cost(mana("{1}"), sacrificeSelf), doing(Effect.CopySpell(target("spell"), Players.You, newTargets = true)),
            targets = listOf(slot("spell", and(spellFilter, you))))),
    )

    // CR 702.179: "that opponent" is the controller of the returned permanent; the speed can't go below 1
    @Test
    fun spikebackHarrier() = card(
        name = "Spikeback Harrier",
        cost = "{4}{U}",
        type = "Artifact Creature — Robot Turtle",
        text = "When this creature enters, return target creature or Vehicle an opponent controls to its owner's hand. If that opponent's speed is greater than each other player's speed, reduce that opponent's speed by 1. This effect can't reduce their speed below 1.",
        pt = "4/4",
    ) parsesTo abilities(
        run {
            val that = Players.ControllerOf(target("permanent"))
            triggered(entersSelf,
                move(target("permanent"), Zone.Hand),
                Step.If(Condition.Compare(Value.Speed(that), Comparison.Greater,
                    Value.GreatestFor(Players.Each(PlayerFilter.Not(PlayerFilter.Is(that))), Value.Speed(Players.Tested))),
                    then = listOf(doing(Effect.ReduceSpeed(that, n(1), atLeast = 1)))),
                targets = listOf(slot("permanent", and(creatureOrVehicle, opponents))))
        },
    )

    @Test
    fun wxxnvWordyHost() = card(
        name = "Wxxnv, Wordy Host",
        cost = "{1}{U}",
        type = "Legendary Creature — Homunculus",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            You have no maximum hand size.
            Max speed — If you would draw a card, draw two cards instead.
        """,
        pt = "0/4",
    ) parsesTo abilities(
        sye,
        playerStatic(Mechanic.NoMaximumHandSize),
        maxSpeed(static(Mechanic.Replacement(EventPattern.Draw(PlayerFilter.You), instead = arcanum.api.Instructions(steps = listOf(draw(2)))))),
    )

    @Test
    fun fuelGuzzler() = card(
        name = "Fuel Guzzler",
        cost = "{B}",
        type = "Creature — Vampire Rogue",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            This creature enters tapped.
            Max speed — {B}, Sacrifice another creature or Vehicle: Draw a card.
        """,
        pt = "2/1",
    ) parsesTo abilities(sye, entersTapped, maxSpeed(activated(cost(mana("{B}"), sacrificeA(and(another, creatureOrVehicle))), draw(1))))

    @Test
    fun gastalPlunderer() = card(
        name = "Gastal Plunderer",
        cost = "{2}{B}",
        type = "Creature — Vampire Rogue",
        text = """
            Start your engines!
            When this creature enters, target opponent reveals their hand. You choose an instant or sorcery card from it. That player discards that card.
            Max speed — This creature gets +1/+1 and has menace.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        sye,
        triggered(entersSelf,
            doing(Effect.Reveal(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))))), bind = "revealed")),
            Step.Choose(Choice.Objects(or(instant, sorcery), n(1), n(1), from = Objects.Bound("revealed")), bind = "chosen", chooser = Players.You),
            doing(Effect.Discard(Players.Target("opponent"), n(1), ObjectFilter.Any, objects = Objects.Bound("chosen"))),
            targets = listOf(targetOpponent)),
        maxSpeed(static(pt(1, 1), has(Keyword.Menace))),
    )

    @Test
    fun hourOfTriumph() = card(
        name = "Hour of Triumph",
        cost = "{2}{B}",
        type = "Enchantment",
        text = """
            Start your engines!
            When this enchantment enters, create a 2/2 black Zombie creature token.
            Max speed — {1}{B}, Sacrifice this enchantment: Search your library for a card, put it into your hand, then shuffle. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        sye,
        triggered(entersSelf, create(zombie)),
        maxSpeed(activated(cost(mana("{1}{B}"), sacrificeSelf), search(cardFilter), move(Objects.Bound("found"), Zone.Hand), shuffle, rules = asSorcery)),
    )

    // CR 118.12: each opponent who couldn't sacrifice discards instead
    @Test
    fun momentumCrusher() = card(
        name = "Momentum Crusher",
        cost = "{1}{B}",
        type = "Enchantment",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            When this enchantment enters, each opponent sacrifices a creature or Vehicle of their choice. Each opponent who can't discards a card.
            {2}, Sacrifice this enchantment: You gain life equal to your speed.
        """,
    ) parsesTo abilities(
        sye,
        triggered(entersSelf, Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "opponent", together = true, steps = listOf(
            Step.Pay(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(creatureOrVehicle, count = n(1))))), payer = Players.Bound("opponent"), mandatory = true,
                bind = "sacrificed"),
            Step.If(Condition.CouldNot("sacrificed"), then = listOf(discard(1, Players.Bound("opponent"))))))),
        activated(cost(mana("{2}"), sacrificeSelf), doing(Effect.GainLife(Players.You, yourSpeed))),
    )

    @Test
    fun mutantPathfinder() = card(
        name = "Mutant Pathfinder",
        cost = "{2}{B}",
        type = "Creature — Mutant Scout",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            {2}: This creature gets +1/+1 until end of turn.
            Max speed — {3}, Exile this card from your graveyard: Draw a card.
        """,
        pt = "2/3",
    ) parsesTo abilities(sye, activated(cost(mana("{2}")), pump(Objects.Self, 1, 1)), surveyorDraw)

    @Test
    fun risenNecrolord() = card(
        name = "Risen Necrolord",
        cost = "{4}{B}",
        type = "Creature — Zombie Cat Knight",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            Max speed — At the beginning of your end step, create a 2/2 black Zombie creature token.
        """,
        pt = "5/4",
    ) parsesTo abilities(sye, maxSpeed(triggered(yourEndStep, create(zombie))))

    // CR 702.179f: X is the speed as the ability resolves
    @Test
    fun theVelocityFiend() = card(
        name = "The Velocity Fiend",
        cost = "{3}{B}{B}",
        type = "Legendary Creature — Demon",
        text = """
            Flying, trample
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            At the beginning of your end step, you draw X cards and lose X life, where X is your speed.
        """,
        pt = "5/5",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Trample,
        sye,
        triggered(yourEndStep, doing(Effect.Draw(Players.You, yourSpeed)), doing(Effect.LoseLife(Players.You, yourSpeed))),
    )

    @Test
    fun streakingOilguzzler() = card(
        name = "Streaking Oilguzzler",
        cost = "{4}{B}",
        type = "Creature — Vampire",
        text = """
            Flying, haste
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            Max speed — This creature has lifelink.
        """,
        pt = "3/3",
    ) parsesTo abilities(Keyword.Flying, Keyword.Haste, sye, maxSpeed(static(has(Keyword.Lifelink))))
}
