package arcanum.parser.sets.mh2

import arcanum.api.CardType
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Modern Horizons 2 chunk 01: white cards, first part: modular (CR 702.43) with a cost removing X of its counters (CR 107.3a), an
 * end-step mode choice, an Equipment attaching to the token it made (CR 701.3), a conditional pump on an artifact creature, player
 * hexproof until your next turn (CR 702.11c) with rebound (CR 702.88), reinforce (CR 702.77), devour artifact (CR 702.82c), renown
 * (CR 702.112) with "one or more +1/+1 counters are put on" (CR 122.6), a "first noncreature spell each turn" tax, an ability from
 * the graveyard, "more life than an opponent", adapt (CR 701.46) and a card put on the bottom of its owner's library.
 */
class Mh2Chunk01WhiteTest {
    @Test
    fun steadfastBenediction() = card(
        name = "Steadfast Benediction",
        cost = "{2}{W}",
        type = "Enchantment",
        text = """
            At the beginning of your end step, choose one —
            • You gain 1 life.
            • Return target creature card with mana value 1 from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        modalTriggered(yourEndStep,
            mode(gainLife(1)),
            mode(move(target("card"), Zone.Battlefield),
                targets = listOf(slot("card", inYourGraveyard(creature, manaValue(Comparison.Equal, 1)))))),
    )

    // CR 107.3a: X is announced as the ability is activated; "It" is this creature
    @Test
    fun arcwiredLancer() = card(
        name = "Arcwired Lancer",
        cost = "{W}",
        type = "Artifact Creature — Soldier",
        text = """
            {T}, Remove X +1/+1 counters from this creature: It deals X damage to target attacking or blocking creature.
            Modular 1 (This creature enters with a +1/+1 counter on it. When it dies, you may put its +1/+1 counters on target artifact creature.)
        """,
        pt = "0/1",
    ) parsesTo abilities(
        arcanum.api.Ability.Activated(
            cost(tap, CostPart.RemoveCounters(p1p1, x, from = CostObjects.Self)),
            arcanum.api.Instructions(
                targets = listOf(slot("creature", attackingOrBlockingCreature)),
                steps = listOf(damage(x, toObjects(target("creature")))),
                announced = listOf("X"),
            ),
            anyTime,
        ),
        Keyword.Modular(1),
    )

    @Test
    fun arcwiredTabby() = card(
        name = "Arcwired Tabby",
        cost = "{W}",
        type = "Artifact Creature — Cat",
        text = """
            Lifelink
            Modular 1 (This creature enters with a +1/+1 counter on it. When it dies, you may put its +1/+1 counters on target artifact creature.)
        """,
        pt = "0/0",
    ) parsesTo abilities(Keyword.Lifelink, Keyword.Modular(1))

    @Test
    fun arcwiredTemplate() = card(
        name = "Arcwired Template",
        cost = "{1}{W}",
        type = "Artifact Creature — Assembly-Worker",
        text = "Modular 2 (This creature enters with two +1/+1 counters on it. When it dies, you may put its +1/+1 counters on target artifact creature.)",
        pt = "0/0",
    ) parsesTo abilities(Keyword.Modular(2))

    // CR 701.3a: the Equipment is attached to the token it created
    @Test
    fun thornedSpur() = card(
        name = "Thorned Spur",
        cost = "{1}{W}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, create a 1/1 colorless Thopter artifact creature token with flying, then attach this Equipment to it.
            Equipped creature gets +1/+0.
            Equip {2}
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(thopter, bind = "token"), doing(Effect.Attach(Objects.Self, Recipients(objects = Objects.Bound("token"))))),
        static(pt(1, 0), affects = equipped),
        equip("{2}"),
    )

    // CR 608.2h: whether it's an artifact creature is checked as the spell resolves
    @Test
    fun smithsWard() = card(
        name = "Smith's Ward",
        cost = "{W}",
        type = "Instant",
        text = "Target permanent gains hexproof and indestructible until end of turn. If it's an artifact creature, it gets +2/+2 until end of turn.",
    ) parsesTo abilities(
        spell(
            gains(target("permanent"), Keyword.Hexproof, Keyword.Indestructible),
            Step.If(Condition.ObjectMatches(target("permanent"), and(artifact, creature)), then = listOf(pump(target("permanent"), 2, 2))),
            targets = listOf(slot("permanent", permanentFilter)),
        ),
    )

    // CR 702.11c: a player with hexproof can't be the target of spells or abilities their opponents control; CR 702.88a rebound
    @Test
    fun bloomingStillness() = card(
        name = "Blooming Stillness",
        cost = "{W}",
        type = "Instant",
        text = """
            You gain hexproof until your next turn. You gain 2 life.
            Rebound (If you cast this spell from your hand, exile it as it resolves. At the beginning of your next upkeep, you may cast this card from exile without paying its mana cost.)
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.ApplyMechanics(listOf(playerHexproof), arcanum.api.Affected.Players(Players.You), arcanum.api.Duration.UntilYourNextTurn)),
            gainLife(2),
        ),
        Keyword.Rebound,
    )

    // CR 702.77a: reinforce is an activated ability from the hand
    @Test
    fun severTheBonds() = card(
        name = "Sever the Bonds",
        cost = "{2}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Destroy target artifact.
            • Destroy target enchantment.
            • Exile target card from a graveyard.
            Reinforce 1—{W} ({W}, Discard this card: Put a +1/+1 counter on target creature.)
        """,
    ) parsesTo abilities(
        modal(
            mode(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
            mode(destroy(target("enchantment")), targets = listOf(slot("enchantment", enchantment))),
            mode(move(target("card"), Zone.Exile, postBind = "exiled"), targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
        ),
        Keyword.Reinforce(1, manaCost("{W}")),
    )

    // CR 702.82c: devour artifact sacrifices artifacts
    @Test
    fun chromegoat() = card(
        name = "Chromegoat",
        cost = "{3}{W}",
        type = "Artifact Creature — Goat",
        text = """
            Flash
            Vigilance
            Devour artifact 1 (As this creature enters, you may sacrifice any number of artifacts. It enters with that many +1/+1 counters on it.)
        """,
        pt = "2/2",
    ) parsesTo abilities(Keyword.Flash, Keyword.Vigilance, Keyword.Devour(1, what = artifact))

    // CR 702.112a renown; CR 610.3 until it leaves the battlefield
    @Test
    fun wardenOfTheShire() = card(
        name = "Warden of the Shire",
        cost = "{4}{W}",
        type = "Creature — Giant Soldier",
        text = """
            Renown 2 (When this creature deals combat damage to a player, if it isn't renowned, put two +1/+1 counters on it and it becomes renowned.)
            Whenever one or more +1/+1 counters are put on this creature, exile up to one other target nonland permanent until this creature leaves the battlefield.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Renown(2),
        triggered(countersPutOnSelf, doing(Effect.Move(target("permanent"), arcanum.api.Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", and(another, nonland, permanentFilter), min = 0))),
    )

    @Test
    fun acolyteOfDawnlight() = card(
        name = "Acolyte of Dawnlight",
        cost = "{4}{W}",
        type = "Creature — Human Cleric",
        text = """
            Lifelink
            When this creature enters, return target permanent card with mana value 3 or less from your graveyard to your hand.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(entersSelf, move(target("card"), Zone.Hand),
            targets = listOf(slot("card", inYourGraveyard(permanentCard, manaValue(Comparison.AtMost, 3))))),
    )

    // CR 118.12a: "unless that player pays {X}"
    @Test
    fun esperLookout() = card(
        name = "Esper Lookout",
        cost = "{W}",
        type = "Artifact Creature — Human Soldier",
        text = "Whenever an opponent casts their first noncreature spell each turn, draw a card unless that player pays {X}, where X is this creature's power.",
        pt = "1/1",
    ) parsesTo abilities(
        arcanum.api.Ability.Triggered(
            Trigger.On(events = listOf(EventPattern.Cast(noncreature, by = PlayerFilter.Opponent)), nthThisTurn = 1),
            arcanum.api.Instructions(steps = listOf(
                Step.Pay(
                    cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{X}")!!, x = Value.Of(NumericProperty.Power, Objects.Self, Combine.Single)))),
                    payer = Players.EventPlayer,
                    bind = "paid",
                ),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(draw(1))),
            )),
        ),
    )

    // CR 113.6k: an ability whose cost exiles the card from the graveyard works there
    @Test
    fun midwayPatrol() = card(
        name = "Midway Patrol",
        cost = "{1}{W}",
        type = "Creature — Human Soldier",
        text = "{1}{W}, Exile this card from your graveyard: Create a 1/1 colorless Thopter artifact creature token with flying. Activate only as a sorcery.",
        pt = "2/1",
    ) parsesTo abilities(
        activated(cost(mana("{1}{W}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)), create(thopter), rules = asSorcery),
    )

    // CR 603.4: "if you have more life than an opponent" is checked when it triggers and as it resolves
    @Test
    fun radiantExecutor() = card(
        name = "Radiant Executor",
        cost = "{5}{W}{W}",
        type = "Creature — Angel",
        text = """
            Flying, lifelink
            At the beginning of each combat, if you have more life than an opponent, this creature gains double strike until end of turn.
        """,
        pt = "5/5",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
        triggered(stepBegins(TurnStep.BeginningOfCombat, PlayerFilter.Any), gains(Objects.Self, Keyword.DoubleStrike),
            interveningIf = Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent,
                PlayerFilter.Life(Comparison.Less, Value.LifeOf(Players.You)))), atLeast = 1)),
    )

    @Test
    fun watchfulKirin() = card(
        name = "Watchful Kirin",
        cost = "{3}{W}",
        type = "Creature — Kirin",
        text = """
            Flying
            Whenever another creature you control dies, put a +1/+1 counter on this creature.
        """,
        pt = "2/3",
    ) parsesTo abilities(Keyword.Flying, triggered(dies(and(another, creature, you)), counters(Objects.Self)))

    @Test
    fun mendersFlight() = card(
        name = "Mender's Flight",
        cost = "{W}{W}{W}",
        type = "Creature — Bird",
        text = "Flying, lifelink",
        pt = "3/3",
    ) parsesTo abilities(Keyword.Flying, Keyword.Lifelink)

    // CR 701.46a adapt; "it" is the creature the counters were put on
    @Test
    fun squireMyr() = card(
        name = "Squire Myr",
        cost = "{2}{W}",
        type = "Artifact Creature — Myr Knight",
        text = """
            {2}{W}: Adapt 1. (If this creature has no +1/+1 counters on it, put a +1/+1 counter on it.)
            Whenever one or more +1/+1 counters are put on this creature, it gains double strike until end of turn.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(mana("{2}{W}")), doing(Effect.Action(KeywordAction.Adapt(n(1))))),
        triggered(countersPutOnSelf, gains(thatCreature, Keyword.DoubleStrike)),
    )

    // CR 401.4: put on the bottom of its owner's library; CR 702.29e basic landcycling
    @Test
    fun groundskeeperColos() = card(
        name = "Groundskeeper Colos",
        cost = "{5}{W}",
        type = "Creature — Goat Beast",
        text = """
            When this creature enters, put target card from an opponent's graveyard on the bottom of their library.
            Basic landcycling {1}{W} ({1}{W}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "4/6",
    ) parsesTo abilities(
        triggered(entersSelf, move(target("card"), Zone.Library, position = -1),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Opponent))))),
        basicLandcycling("{1}{W}"),
    )

    @Test
    fun tardySupper() = card(
        name = "Tardy Supper",
        cost = "{3}{W}",
        type = "Sorcery",
        text = "Return target creature card from your graveyard to the battlefield. Create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")",
    ) parsesTo abilities(
        spell(move(target("card"), Zone.Battlefield), create(arcanum.api.TokenSpec.Predefined("Food")),
            targets = listOf(slot("card", inYourGraveyard(creature)))),
    )
}
