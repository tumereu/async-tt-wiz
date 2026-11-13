package arcanum.parser.sets.isd

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * ISD chunk 13: discard, draw, exchange life with a stat, coin flips, conditional keywords, life gain and power/toughness lords.
 */
class IsdChunk13DiscardDrawLifeAndPtTest {
    private val targetPlayerPlayers = Players.Target("player")
    private val human = sub("Human")

    // CR 701.9a, 602.5d: a sorcery-speed sacrifice ability makes the target player discard two cards
    @Test
    fun marrowWeevil() = card(
        name = "Marrow Weevil", cost = "{3}{B}", type = "Creature — Insect", pt = "1/1",
        text = """
            Intimidate (This creature can't be blocked except by artifact creatures and/or creatures that share a color with it.)
            Sacrifice this creature: Target player discards two cards. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Intimidate,
        activated(cost(sacrificeSelf), discard(2, targetPlayerPlayers), targets = listOf(targetPlayer), rules = asSorcery),
    )

    // CR 504.1, 121.1, 119.3: the chosen player draws and loses 1 life
    @Test
    fun cellarFiend() = card(
        name = "Cellar Fiend", cost = "{3}{B}{B}", type = "Creature — Demon", pt = "5/4",
        text = """
            Flying
            At the beginning of your upkeep, target player draws a card and loses 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(yourUpkeep, draw(1, targetPlayerPlayers), loseLife(1, targetPlayerPlayers), targets = listOf(targetPlayer)),
    )

    // CR 700.4, 603.5: "if you do" discards only after the optional draw
    @Test
    fun muddledRaven() = card(
        name = "Muddled Raven", cost = "{3}{U}{U}", type = "Creature — Bird", pt = "4/4",
        text = """
            Flying
            Whenever another creature dies, you may draw a card. If you do, discard a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(dies(and(another, creature)),
            Step.Pay(null, steps = listOf(draw(1)), payer = Players.You, mandatory = false, bind = "paid"), Step.If(Condition.Did("paid"), then = listOf(discard(1)))),
    )

    // CR 701.12a: exchange life total with a creature's toughness (CR 119.7, 119.8)
    @Test
    fun rootOfRecovery() = card(
        name = "Root of Recovery", cost = "{3}{G}", type = "Creature — Plant", pt = "0/13",
        text = """
            Defender
            {T}: Exchange your life total with this creature's toughness.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(tap), doing(Effect.ExchangeLifeWithStat(Players.You, Objects.Self, NumericProperty.Toughness))),
    )

    // CR 705.1, 120.2: "that creature" is the creature this one dealt combat damage to
    @Test
    fun grinningPuppet() = card(
        name = "Grinning Puppet", cost = "{5}", type = "Artifact Creature — Construct", pt = "1/1",
        text = """
            Indestructible
            Whenever this creature deals combat damage to a creature, flip a coin. If you win the flip, destroy that creature.
        """,
    ) parsesTo abilities(
        Keyword.Indestructible,
        triggered(on(EventPattern.Damage(isSelf, RecipientFilter(objects = creature), combat = true)),
            doing(Effect.FlipCoin(Players.You, bind = "flip")),
            ifThen(Condition.WonFlip("flip"), destroy(Objects.EventObject(EventRole.Object)))),
    )

    // CR 613.1f, 611.3a: the keywords are granted while the condition holds
    @Test
    fun seraphicWarden() = card(
        name = "Seraphic Warden", cost = "{3}{W}{W}", type = "Creature — Angel", pt = "5/3",
        text = """
            Flying
            As long as you control a Human, this creature has hexproof and indestructible.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(has(Keyword.Hexproof, Keyword.Indestructible), condition = youControl(human, 1)),
    )

    // CR 613.1f: an opponent's Human
    @Test
    fun dawnStalker() = card(
        name = "Dawn Stalker", cost = "{4}{R}", type = "Creature — Vampire", pt = "4/4",
        text = "This creature has haste as long as an opponent controls a Human.",
    ) parsesTo abilities(
        static(has(Keyword.Haste), condition = Condition.Exists(and(human, opponents), atLeast = 1)),
    )

    // CR 700.4, 608.2h: creatures this one dealt damage to this turn; its toughness is last known information
    @Test
    fun gravelGhoul() = card(
        name = "Gravel Ghoul", cost = "{3}{B}", type = "Creature — Zombie", pt = "3/2",
        text = """
            First strike
            Whenever a creature dealt damage by this creature this turn dies, you gain life equal to that creature's toughness.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        triggered(dies(and(creature, ObjectFilter.ThisTurn(EventPattern.Damage(isSelf, RecipientFilter()), role = EventRole.Object))), doing(Effect.GainLife(Players.You, toughness(Objects.EventObject(EventRole.Before))))),
    )

    // CR 701.21a, 118.3: the sacrificed creature's toughness is used
    @Test
    fun graveTithe() = card(
        name = "Grave Tithe", cost = "{1}{B}", type = "Creature — Human Cleric", pt = "1/1",
        text = "{1}, Sacrifice a creature: You gain life equal to the sacrificed creature's toughness.",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), sacrificeA(creature, preBind = "sacrificed")), doing(Effect.GainLife(Players.You, toughness(Objects.Bound("sacrificed"))))),
    )

    // CR 613.4c: other Spirits you control get +0/+1
    @Test
    fun hollowSpiritGuard() = card(
        name = "Hollow Spirit Guard", cost = "{4}{W}", type = "Creature — Spirit", pt = "3/3",
        text = """
            Flying
            Other Spirit creatures you control get +0/+1.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        static(pt(0, 1), affects = Objects.Each(and(another, sub("Spirit"), creature, you))),
    )

    // CR 613.4c: creatures with flying that opponents control get -1/-0
    @Test
    fun strawSentinel() = card(
        name = "Straw Sentinel", cost = "{3}", type = "Artifact Creature — Scarecrow", pt = "2/3",
        text = """
            Defender
            Creatures with flying your opponents control get -1/-0.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        static(pt(-1, 0), affects = Objects.Each(and(creature, kw(Keyword.Flying.kind), opponents))),
    )

    // CR 613.4c, 604.3: counts the creatures opponents control
    @Test
    fun moorHowler() = card(
        name = "Moor Howler", cost = "{3}{R}{R}", type = "Creature — Elemental", pt = "3/3",
        text = "This creature gets +1/+1 for each creature your opponents control.",
    ) parsesTo abilities(
        static(Mechanic.ModifyPowerToughness(count(and(creature, opponents)), count(and(creature, opponents)))),
    )
}
