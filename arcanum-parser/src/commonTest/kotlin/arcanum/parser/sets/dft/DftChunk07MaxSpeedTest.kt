package arcanum.parser.sets.dft

import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Aetherdrift chunk 07: start your engines! (CR 702.179) and max speed (CR 702.178) on red, green, multicoloured and colourless cards and
 * the Speedways: max speed mana abilities, an upkeep exile you may play this turn, attacking and blocking only at max speed, players without
 * max speed (CR 702.179e), searches and cost reductions by speed, a damage replacement (CR 614.1a) and a copy of a cast artifact spell.
 */
class DftChunk07MaxSpeedTest {
    private val sye = Keyword.StartYourEngines

    @Test
    fun burnoutBashrider() = card(
        name = "Burnout Bashrider",
        cost = "{R}",
        type = "Creature — Goblin Warrior",
        text = """
            Menace
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            {2}: This creature gets +1/+0 until end of turn.
            Max speed — This creature has double strike.
        """,
        pt = "1/1",
    ) parsesTo abilities(Keyword.Menace, sye, activated(cost(mana("{2}")), pump(Objects.Self, 1, 0)), maxSpeed(static(has(Keyword.DoubleStrike))))

    @Test
    fun endriderCatalyst() = card(
        name = "Endrider Catalyst",
        cost = "{1}{R}",
        type = "Creature — Human Warrior",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            Max speed — {T}: Add {R}{R}.
        """,
        pt = "3/1",
    ) parsesTo abilities(sye, maxSpeed(activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}{R}")!!)), Players.You)))))

    @Test
    fun endriderSpikeshooter() = card(
        name = "Endrider Spikeshooter",
        cost = "{3}{R}",
        type = "Creature — Human Mercenary",
        text = """
            Reach
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            Max speed — At the beginning of your upkeep, exile the top card of your library. You may play that card this turn.
        """,
        pt = "3/4",
    ) parsesTo abilities(Keyword.Reach, sye, maxSpeed(triggered(yourUpkeep, *exileTopMayPlayThisTurn)))

    @Test
    fun goblinPathfinder() = card(
        name = "Goblin Pathfinder",
        cost = "{2}{R}",
        type = "Creature — Goblin Scout",
        text = """
            Trample
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            Max speed — {3}, Exile this card from your graveyard: Draw a card.
        """,
        pt = "3/2",
    ) parsesTo abilities(Keyword.Trample, sye, surveyorDraw)

    // CR 702.179e: max speed is speed 4
    @Test
    fun hazoraGodracer() = card(
        name = "Hazora, Godracer",
        cost = "{1}{R}",
        type = "Legendary Creature — God",
        text = """
            Indestructible, haste
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            {1}, {T}: Target creature with power 2 or less can't be blocked this turn.
            Hazora can't attack or block unless you have max speed.
        """,
        pt = "5/3",
    ) parsesTo abilities(
        Keyword.Indestructible,
        Keyword.Haste,
        sye,
        activated(cost(mana("{1}"), tap), untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked), targets = listOf(slot("creature", and(creature, power(Comparison.AtMost, 2))))),
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), condition = Condition.Not(Condition.MaxSpeed)),
    )

    // CR 508.1d: the token must attack this combat if able
    @Test
    fun howlpackHeavy() = card(
        name = "Howlpack Heavy",
        cost = "{2}{R}",
        type = "Creature — Goblin Mercenary",
        text = """
            Start your engines!
            Other Goblins you control have haste.
            At the beginning of combat on your turn, create a 1/1 red Goblin creature token. That token attacks this combat if able.
            Max speed — {T}: Add {R} for each Goblin you control.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        sye,
        static(has(Keyword.Haste), affects = Objects.Each(and(another, sub("Goblin"), you))),
        triggered(beginningOfCombatOnYourTurn, create(creatureToken("Goblin", listOf(arcanum.api.Color.Red), 1, 1), bind = "token"),
            doing(Effect.ApplyMechanics(listOf(Mechanic.MustAttack()), arcanum.api.Affected.Objects(Objects.Bound("token")), Duration.EndOfCombat))),
        maxSpeed(activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!, times = count(and(sub("Goblin"), you)))), Players.You)))),
    )

    @Test
    fun kickoffFestivities() = card(
        name = "Kickoff Festivities",
        cost = "{1}{R}",
        type = "Enchantment",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            When this enchantment enters, you may discard a card. If you do, draw two cards.
            Max speed — Sacrifice this enchantment: Creatures and Vehicles you control gain haste until end of turn.
        """,
    ) parsesTo abilities(
        sye,
        triggered(entersSelf, Step.Pay(cost(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(2)))),
        maxSpeed(activated(cost(sacrificeSelf), gains(Objects.Each(and(creatureOrVehicle, you)), Keyword.Haste))),
    )

    // CR 702.179e, f: a player without speed doesn't have max speed
    @Test
    fun outrunOblivion() = card(
        name = "Outrun Oblivion",
        cost = "{2}{R}",
        type = "Enchantment",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            When this enchantment enters, it deals 5 damage to up to one target creature or planeswalker.
            {2}, Sacrifice this enchantment: It deals 2 damage to each player who doesn't have max speed.
        """,
    ) parsesTo abilities(
        sye,
        triggered(entersSelf, damage(5, toObjects(target("permanent"))), targets = listOf(slot("permanent", or(creature, planeswalker), min = 0))),
        activated(cost(mana("{2}"), sacrificeSelf), damage(2, Recipients(players = Players.Each(PlayerFilter.Where(
            Condition.Compare(Value.Speed(Players.Tested), Comparison.Less, n(4))))))),
    )

    @Test
    fun loxodonPathfinder() = card(
        name = "Loxodon Pathfinder",
        cost = "{2}{G}",
        type = "Creature — Elephant Scout",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            Max speed — {3}, Exile this card from your graveyard: Draw a card.
        """,
        pt = "3/3",
    ) parsesTo abilities(sye, surveyorDraw)

    @Test
    fun markTheWay() = card(
        name = "Mark the Way",
        cost = "{G}",
        type = "Enchantment",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            {3}{G}, Sacrifice this enchantment: Search your library for up to X basic land cards, where X is your speed. Put those cards onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        sye,
        activated(cost(mana("{3}{G}"), sacrificeSelf), search(basicLand, max = yourSpeed), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
    )

    @Test
    fun embalmedAscender() = card(
        name = "Embalmed Ascender",
        cost = "{1}{W}{B}",
        type = "Creature — Zombie",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            When this creature enters, create a 2/2 black Zombie creature token.
            Max speed — Whenever a creature you control dies, each opponent loses 1 life and you gain 1 life.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        sye,
        triggered(entersSelf, create(zombie)),
        maxSpeed(triggered(dies(creatureYouControl), loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1))),
    )

    // CR 614.1a: the damage event is changed (each source, each event)
    @Test
    fun farFortunaFinalBoss() = card(
        name = "Far Fortuna, Final Boss",
        cost = "{2}{B}{R}",
        type = "Legendary Creature — Human Mercenary",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            Whenever you attack, Far Fortuna deals 1 damage to each opponent.
            Max speed — If a source you control would deal damage to an opponent or a permanent an opponent controls, it deals that much damage plus 1 instead.
        """,
        pt = "4/5",
    ) parsesTo abilities(
        sye,
        triggered(youAttack, damage(1, eachOpponent)),
        maxSpeed(static(Mechanic.Replacement(EventPattern.Damage(you, RecipientFilter(objects = and(permanentFilter, opponents), players = PlayerFilter.Opponent)),
            modify = listOf(Modification.AddAmount(n(1)))))),
    )

    @Test
    fun gastalThrillchaser() = card(
        name = "Gastal Thrillchaser",
        cost = "{B}{R}",
        type = "Creature — Lizard Berserker",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            When this creature enters, it deals 1 damage to target opponent and you gain 1 life.
            Max speed — This creature has deathtouch and haste.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        sye,
        triggered(entersSelf, damage(1, Recipients(players = Players.Target("opponent"))), gainLife(1), targets = listOf(targetOpponent)),
        maxSpeed(static(has(Keyword.Deathtouch, Keyword.Haste))),
    )

    // CR 707.10c: the copy of a permanent spell becomes a token
    @Test
    fun mendicantHeartPathlight() = card(
        name = "Mendicant Heart, Pathlight",
        cost = "{W}{U}",
        type = "Legendary Artifact Creature — Robot",
        text = """
            Mendicant Heart's power is equal to the number of artifacts you control.
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            Max speed — Whenever you cast an artifact spell, you may pay {1}. If you do, copy it. (The copy becomes a token.)
        """,
        pt = "*/3",
    ) parsesTo abilities(
        static(Mechanic.SetPowerToughness(count(and(artifact, you)), null), characteristicDefining = true),
        sye,
        maxSpeed(triggered(youCast(artifact), Step.Pay(cost(mana("{1}")), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(doing(Effect.CopySpell(Objects.EventObject(arcanum.api.EventRole.Object), Players.You, newTargets = false)))))),
    )

    @Test
    fun samraTheDrivingForce() = card(
        name = "Samra, the Driving Force",
        cost = "{3}{R}{G}{W}",
        type = "Legendary Creature — Human Warrior Cleric",
        text = """
            First strike, vigilance, haste
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            Other creatures you control get +X/+0, where X is your speed.
            Noncreature spells you cast cost {X} less to cast, where X is your speed.
        """,
        pt = "4/5",
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Keyword.Vigilance,
        Keyword.Haste,
        sye,
        static(Mechanic.ModifyPowerToughness(yourSpeed, n(0)), affects = Objects.Each(and(another, creature, you))),
        static(Mechanic.CostChange(and(noncreature, spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, yourSpeed))),
    )

    @Test
    fun zahraGlorysPast() = card(
        name = "Zahra, Glory's Past",
        cost = "{W}{B}",
        type = "Legendary Creature — Zombie Cat Warrior",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            Sacrifice another creature: Surveil 1. Activate only once each turn.
            Max speed — Whenever a nontoken creature you control dies, create a tapped 2/2 black Zombie creature token.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        sye,
        activated(cost(sacrificeA(and(another, creature))), surveil(1), rules = anyTime.copy(perTurn = 1)),
        maxSpeed(triggered(dies(and(not(token), creature, you)), create(zombie, tapped = true))),
    )

    @Test
    fun racersLeaderboard() = card(
        name = "Racers' Leaderboard",
        cost = "{4}",
        type = "Artifact",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            When this artifact enters, draw two cards, then discard a card.
            Max speed — Spells you cast cost {1} less to cast.
        """,
    ) parsesTo abilities(
        sye,
        triggered(entersSelf, draw(2), discard(1)),
        maxSpeed(static(Mechanic.CostChange(and(spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1))))),
    )

    @Test
    fun startingGrid() = card(
        name = "Starting Grid",
        cost = "{3}",
        type = "Artifact",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            {T}: Add one mana of any color.
            Max speed — {T}, Sacrifice this artifact: Draw two cards, then discard a card.
        """,
    ) parsesTo abilities(sye, addAnyColor, maxSpeed(activated(cost(tap, sacrificeSelf), draw(2), discard(1))))

    @Test
    fun walkingSepulcher() = card(
        name = "Walking Sepulcher",
        cost = "{2}",
        type = "Artifact Creature — Zombie Cat",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            Max speed — This creature gets +1/+2.
        """,
        pt = "2/1",
    ) parsesTo abilities(sye, maxSpeed(static(pt(1, 2))))

    @Test
    fun amonkhetSpeedway() = card(
        name = "Amonkhet Speedway",
        cost = null,
        type = "Land",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            {T}: Add {C}.
            Max speed — {T}: Target creature gains haste until end of turn.
        """,
    ) parsesTo abilities(sye, addColorless, maxSpeed(activated(cost(tap), gains(target("creature"), Keyword.Haste), targets = listOf(slot("creature", creature)))))

    @Test
    fun avishkarSpeedway() = card(
        name = "Avishkar Speedway",
        cost = null,
        type = "Land",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            {T}: Add {C}.
            Max speed — {3}, {T}, Discard a card: Draw a card.
        """,
    ) parsesTo abilities(sye, addColorless, maxSpeed(activated(cost(mana("{3}"), tap, CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1)))), draw(1))))

    @Test
    fun muragandaSpeedway() = card(
        name = "Muraganda Speedway",
        cost = null,
        type = "Land",
        text = """
            Start your engines! (If you have no speed, it starts at 1. It increases once on each of your turns when an opponent loses life. Max speed is 4.)
            {T}: Add {C}.
            Max speed — {T}: Add {C}{C}.
        """,
    ) parsesTo abilities(sye, addColorless, maxSpeed(activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}{C}")!!)), Players.You)))))
}
