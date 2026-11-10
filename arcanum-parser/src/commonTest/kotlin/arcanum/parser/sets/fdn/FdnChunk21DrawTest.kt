package arcanum.parser.sets.fdn

import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** The FDN cards that draw cards (chunk 21). The invented names carry the oracle text of the real cards. */
class FdnChunk21DrawTest {
    private val paid = Step.If(Condition.Did("paid"), then = listOf(draw(1)))

    // CR 121.1, 602.1: a tap cost; CR 400.3: "owner's hand"
    @Test
    fun voltarisTheAllKnowing() = card(
        name = "Voltaris the All-Knowing",
        cost = "{3}{U}{U}{U}",
        type = "Legendary Creature — Wizard",
        text = """
            {T}: Draw three cards.
            {2}{U}{U}: Return Voltaris to its owner's hand.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        activated(cost(tap), draw(3)),
        activated(manaCost("{2}{U}{U}"), move(Objects.Self, Zone.Hand)),
    )

    // CR 603.2: a trigger on each life gain event; CR 119.9
    @Test
    fun gravewispReaver() = card(
        name = "Gravewisp Reaver",
        cost = "{5}{W}{U}",
        type = "Creature — Spirit",
        text = """
            Flying
            Double strike (This creature deals both first-strike and regular combat damage.)
            Lifelink (Damage dealt by this creature also causes you to gain that much life.)
            Whenever you gain life, draw a card.
        """,
        pt = "3/5",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.DoubleStrike,
        Keyword.Lifelink,
        triggered(on(EventPattern.GainLife(PlayerFilter.You)), draw(1)),
    )

    // CR 603.4: the intervening "if" is checked on trigger and on resolution; CR 208.1: power
    @Test
    fun thornwakeUprising() = card(
        name = "Thornwake Uprising",
        cost = "{2}{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, if you control a creature with power 4 or greater, draw a card.
            Creatures you control have trample. (Each of those creatures can deal excess combat damage to the player or planeswalker it's attacking.)
            Whenever a creature you control with power 4 or greater enters, draw a card.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, draw(1), interveningIf = Condition.Exists(and(creature, power(Comparison.AtLeast, 4), you), 1)),
        static(has(Keyword.Trample), affects = Objects.Each(creatureYouControl)),
        triggered(enters(and(creature, you, power(Comparison.AtLeast, 4))), draw(1)),
    )

    // CR 601.2c, 603.2: a cast trigger on a spell that targets this creature
    @Test
    fun thornbackRhino() = card(
        name = "Thornback Rhino",
        cost = "{2}{G}{G}",
        type = "Creature — Rhino",
        text = """
            Trample (This creature can deal excess combat damage to the player or planeswalker it's attacking.)
            Whenever you cast a spell that targets this creature, draw a card.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(youCast(arcanum.api.ObjectFilter.Targets(objects = Objects.Self)), draw(1)),
    )

    // CR 117.12: "you may pay", then "if you do"; CR 208.1: power 2 or less, another creature
    @Test
    fun menderOfTheMeek() = card(
        name = "Mender of the Meek",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        text = "Whenever another creature you control with power 2 or less enters, you may pay {1}. If you do, draw a card.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(
            enters(and(another, creature, you, power(Comparison.AtMost, 2))),
            Step.Pay(cost = cost(mana("{1}")), payer = Players.You, bind = "paid"),
            paid,
        ),
    )

    // CR 503.1a: the upkeep of its controller; CR 121.1 then CR 119.3 in order
    @Test
    fun phyrexianArcade() = card(
        name = "Phyrexian Arcade",
        cost = "{1}{B}{B}",
        type = "Enchantment",
        text = "At the beginning of your upkeep, you draw a card and lose 1 life.",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), draw(1), loseLife(1)),
    )

    // CR 121.1: each player; CR 603.2: an opponent's draw
    @Test
    fun scribblingCrawler() = card(
        name = "Scribbling Crawler",
        cost = "{3}",
        type = "Artifact Creature — Phyrexian Construct",
        text = """
            At the beginning of your upkeep, each player draws a card.
            Whenever an opponent draws a card, that player loses 1 life.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), draw(1, Players.Each(PlayerFilter.Any))),
        triggered(on(EventPattern.Draw(PlayerFilter.Opponent)), loseLife(1, Players.EventPlayer)),
    )

    // CR 702.36a, 602.1: a mana-only cost
    @Test
    fun spectralMariner() = card(
        name = "Spectral Mariner",
        cost = "{U}",
        type = "Creature — Spirit Pirate",
        text = """
            Flash (You may cast this spell any time you could cast an instant.)
            Flying
            {3}{U}: Draw a card.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        activated(manaCost("{3}{U}"), draw(1)),
    )

    // CR 702.20a: vigilance with a tap cost on the same creature; CR 608.2c: draw, then discard
    @Test
    fun stryxWatcher() = card(
        name = "Stryx Watcher",
        cost = "{1}{U}",
        type = "Creature — Bird",
        text = """
            Flying, vigilance (Attacking doesn't cause this creature to tap.)
            {1}{U}, {T}: Draw a card, then discard a card.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        activated(cost(mana("{1}{U}"), tap), draw(1), discard(1)),
    )

    // CR 117.12, 701.21a: sacrifice another creature, then "if you do"; CR 509.1b: can't be blocked
    @Test
    fun vampireGlutton() = card(
        name = "Vampire Glutton",
        cost = "{1}{B}",
        type = "Creature — Vampire",
        text = "Whenever this creature attacks, you may sacrifice another creature. If you do, draw a card and this creature can't be blocked this turn.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(
            attacksSelf,
            Step.Pay(cost = cost(sacrificeA(and(another, creature))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(1), untilEndOfTurn(Objects.Self, Mechanic.CantBeBlocked))),
        ),
    )
}
