package arcanum.parser.sets.tmt

import arcanum.api.Ability
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
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
 * Teenage Mutant Ninja Turtles chunk 10: black cards. Up to three targets, a type of creature given menace, swampcycling, paying life equal
 * to the entering creature's power for that many counters (CR 119.4), a granted dies trigger that returns the card tapped (CR 113.2c,
 * 400.7), exiling the dying card for a reflexive trigger (CR 603.12), draws and life loss by the counters on it, modes with a target player
 * each, life equal to the toughness of a creature that died (CR 603.10a), kicker with a sacrifice (CR 702.33a), any permanent leaving,
 * and an ability that works from the graveyard (CR 113.6m).
 */
class TmtChunk10BlackTest {
    private val gainedLife = on(EventPattern.GainLife(PlayerFilter.You))

    @Test
    fun abyssalMawFutureShark() = card(
        name = "Abyssal Maw, Future Shark",
        cost = "{6}{B}{B}",
        type = "Legendary Creature — Shark Horror Mutant",
        text = """
            Flash
            When Abyssal Maw enters, destroy up to three target creatures.
        """,
        pt = "9/6",
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, destroy(target("creatures")), targets = listOf(slot("creatures", creature, min = 0, max = 3))),
    )

    @Test
    fun tuskerWarthogWarrior() = card(
        name = "Tusker, Warthog Warrior",
        cost = "{4}{B}",
        type = "Legendary Creature — Boar Mutant Warrior",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Rhinos you control have menace.
            Swampcycling {2} ({2}, Discard this card: Search your library for a Swamp card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "5/4",
    ) parsesTo abilities(
        Keyword.Menace,
        static(has(Keyword.Menace), affects = Objects.Each(and(sub("Rhino"), you))),
        landcycling("Swamp"),
    )

    @Test
    fun familyTragedy() = card(
        name = "Family Tragedy",
        cost = "{1}{B}",
        type = "Instant",
        text = "Exile target creature with mana value 3 or less.",
    ) parsesTo abilities(
        spell(move(target("creature"), Zone.Exile), targets = listOf(slot("creature", and(creature, manaValue(Comparison.AtMost, 3))))),
    )

    @Test
    fun nightmareBeavers() = card(
        name = "Nightmare Beavers",
        cost = "{B}",
        type = "Creature — Beaver Nightmare",
        text = """
            Flying
            When this creature enters, each opponent loses 1 life and you gain 1 life. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, loseLife(1, Players.Each(PlayerFilter.Opponent)), gainLife(1), scry(1)),
    )

    // CR 119.4: paying life; the power as the ability resolves
    @Test
    fun madameVoidPowerBroker() = card(
        name = "Madame Void, Power Broker",
        cost = "{2}{B}",
        type = "Legendary Creature — Demon Advisor",
        text = """
            Deathtouch
            Whenever another creature you control enters, you may pay life equal to its power. If you do, put that many +1/+1 counters on it.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(anotherCreatureYouControlEnters,
            Step.Pay(cost(CostPart.PayLife(power(enteredOne))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(counters(enteredOne, power(enteredOne))))),
    )

    // CR 113.2c: the granted trigger returns the card that died (CR 400.7) under its owner's control
    @Test
    fun hardLessons101() = card(
        name = "Hard Lessons 101",
        cost = "{1}{B}",
        type = "Instant",
        text = "Until end of turn, target creature gains deathtouch and \"When this creature dies, return it to the battlefield tapped under its owner's control.\"",
    ) parsesTo abilities(
        spell(untilEndOfTurn(target("creature"), Mechanic.GainAbilities(listOf(Keyword.Deathtouch,
            triggered(diesSelf, move(enteredOne, Zone.Battlefield, tapped = true, controller = Players.OwnerOf(enteredOne)))))),
            targets = listOf(slot("creature", creature))),
    )

    // CR 603.12: the reflexive trigger exists only if the card was exiled
    @Test
    fun protozoanColony() = card(
        name = "Protozoan Colony",
        cost = "{1}{B}",
        type = "Creature — Zombie Worm",
        text = """
            When this creature enters, mill three cards. (Put the top three cards of your library into your graveyard.)
            When this creature dies, you may exile it. When you do, put target creature card from your graveyard on top of your library.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, mill(3)),
        triggered(diesSelf,
            Step.Pay(cost(CostPart.Exile(CostObjects.These(enteredOne), from = Zone.Graveyard)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(move(target("card"), Zone.Library, position = 0),
                targets = listOf(slot("card", inYourGraveyard(creature))))))),
    )

    // CR 107.3c: X counted as the ability resolves, after the counter is put
    @Test
    fun vantariTimesExile() = card(
        name = "Vantari, Time's Exile",
        cost = "{3}{B}{B}",
        type = "Legendary Creature — Demon Wizard",
        text = """
            Trample
            At the beginning of combat on your turn, put a +1/+1 counter on Vantari. Then you draw X cards and lose X life, where X is the number of counters on Vantari.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(beginningOfCombatOnYourTurn,
            counters(Objects.Self),
            doing(Effect.Draw(Players.You, Value.Of(NumericProperty.Counters(null), Objects.Self, Combine.Single))),
            doing(Effect.LoseLife(Players.You, Value.Of(NumericProperty.Counters(null), Objects.Self, Combine.Single)))),
    )

    @Test
    fun warlordsVengeance() = card(
        name = "Warlord's Vengeance",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            Choose one —
            • Target player discards two cards.
            • Target player draws two cards and loses 2 life.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(discard(2, Players.Target("player")), targets = listOf(targetPlayer)),
            mode(draw(2, Players.Target("player")), loseLife(2, Players.Target("player")), targets = listOf(targetPlayer))))),
    )

    // CR 603.10a: the toughness of the creature as it last existed on the battlefield
    @Test
    fun southerlyGaleAvatar() = card(
        name = "Southerly Gale Avatar",
        cost = "{3}{B}",
        type = "Creature — Snake Spirit Avatar",
        text = """
            Deathtouch
            Whenever another creature you control dies, you gain life equal to its toughness.
            Whenever you gain life, each opponent loses 1 life.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(dies(and(another, creature, you)), doing(Effect.GainLife(Players.You, toughness(leftOne)))),
        triggered(gainedLife, loseLife(1, Players.Each(PlayerFilter.Opponent))),
    )

    // CR 702.33a: a kicker cost that is a sacrifice; CR 702.33d: "if this spell was kicked"
    @Test
    fun crushedByTheClan() = card(
        name = "Crushed by the Clan",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Kicker—Sacrifice an artifact or creature. (You may sacrifice an artifact or creature in addition to any other costs as you cast this spell.)
            Target creature gets -2/-2 until end of turn. If this spell was kicked, that creature gets -5/-5 until end of turn instead.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(cost(CostPart.Sacrifice(CostObjects.Choose(or(artifact, creature), count = n(1)))))),
        spell(Step.If(Condition.Paid(Keyword.Kicker.Kind, times = 1, of = Objects.Self),
                then = listOf(pump(target("creature"), -5, -5)),
                otherwise = listOf(pump(target("creature"), -2, -2))),
            targets = listOf(slot("creature", creature))),
    )

    @Test
    fun ultraBladefist() = card(
        name = "Ultra Bladefist",
        cost = "{1}{B}",
        type = "Legendary Creature — Mutant Ninja Human",
        text = """
            Menace
            Whenever another permanent leaves the battlefield, put a +1/+1 counter on Ultra Bladefist.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(on(EventPattern.ZoneChange(and(another, permanentFilter), from = Zone.Battlefield)), counters(Objects.Self)),
    )

    // CR 113.6m: the ability works only while the card is in the graveyard (as it moves the card from there)
    @Test
    fun sewerTunnelRats() = card(
        name = "Sewer Tunnel Rats",
        cost = "{1}{B}",
        type = "Creature — Rat",
        text = "{4}{B}: Return this card from your graveyard to the battlefield tapped.",
        pt = "2/2",
    ) parsesTo abilities(
        activated(manaCost("{4}{B}"), move(Objects.Self, Zone.Battlefield, tapped = true, postBind = "returned")),
    )
}
