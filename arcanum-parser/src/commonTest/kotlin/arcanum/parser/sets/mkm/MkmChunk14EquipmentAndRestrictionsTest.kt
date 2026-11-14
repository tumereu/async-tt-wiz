package arcanum.parser.sets.mkm

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Measure
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Zone
import arcanum.api.Status
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Murders at Karlov Manor chunk 14: Equipment (and Clue Equipment), additional costs, evasion limits, can't be countered, cost changes. */
class MkmChunk14EquipmentAndRestrictionsTest {
    private val drawACard = draw(1)
    private val clueSacrifice = activated(cost(mana("{2}"), sacrificeSelf), drawACard)

    // CR 301.5, 702.6, 113.3d: the granted triggered ability belongs to the equipped creature
    @Test
    fun candlestickLike() = card(
        name = "Brass Candlestick Test",
        cost = "{U}",
        type = "Artifact — Clue Equipment",
        text = """
            Equipped creature gets +1/+1 and has "Whenever this creature attacks, surveil 2." (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
            {2}, Sacrifice this Equipment: Draw a card.
            Equip {2}
        """,
    ) parsesTo abilities(
        static(pt(1, 1), has(triggered(attacksSelf, doing(Effect.Surveil(Players.You, n(2))))), affects = equipped),
        clueSacrifice,
        equip("{2}"),
    )

    // CR 611.3a: only during its controller's turn
    @Test
    fun knifeLike() = card(
        name = "Paring Knife Test",
        cost = "{R}",
        type = "Artifact — Clue Equipment",
        text = """
            During your turn, equipped creature gets +1/+0 and has first strike.
            {2}, Sacrifice this Equipment: Draw a card.
            Equip {2}
        """,
    ) parsesTo abilities(
        static(pt(1, 0), has(Keyword.FirstStrike), affects = equipped, condition = Condition.YourTurn),
        clueSacrifice,
        equip("{2}"),
    )

    // CR 603.10a: a leaves-the-battlefield trigger of the Equipment itself; "you may pay ... If you do"
    @Test
    fun haunchLike() = card(
        name = "Ham Haunch Test",
        cost = "{W}",
        type = "Artifact — Food Equipment",
        text = """
            Equipped creature gets +2/+0.
            {2}, {T}, Sacrifice this Equipment: You gain 3 life.
            When this Equipment is put into a graveyard from the battlefield, you may pay {1}{W}. If you do, create two 1/1 white Dog creature tokens.
            Equip {2}
        """,
    ) parsesTo abilities(
        static(pt(2, 0), affects = equipped),
        activated(cost(mana("{2}"), tap, sacrificeSelf), gainLife(3)),
        triggered(diesSelf,
            Step.Pay(manaCost("{1}{W}"), bind = "paid", payer = Players.You),
            ifThen(Condition.Did("paid"), create(creatureToken("Dog", listOf(arcanum.api.Color.White), 1, 1), count = n(2)))),
        equip("{2}"),
    )

    // CR 603.10a: "equipped creature dies" looks back at the attachment
    @Test
    fun leadPipeLike() = card(
        name = "Iron Pipe Test",
        cost = "{B}",
        type = "Artifact — Clue Equipment",
        text = """
            Equipped creature gets +2/+0.
            Whenever equipped creature dies, each opponent loses 1 life.
            {2}, Sacrifice this Equipment: Draw a card.
            Equip {2}
        """,
    ) parsesTo abilities(
        static(pt(2, 0), affects = equipped),
        triggered(dies(ObjectFilter.Is(equipped)), loseLife(1, Players.Each(PlayerFilter.Opponent))),
        clueSacrifice,
        equip("{2}"),
    )

    // CR 702.19b: "can't be blocked by more than one creature" is a maximum-blockers limit
    @Test
    fun ropeLike() = card(
        name = "Hemp Rope Test",
        cost = "{G}",
        type = "Artifact — Clue Equipment",
        text = """
            Equipped creature gets +1/+2, has reach, and can't be blocked by more than one creature.
            {2}, Sacrifice this Equipment: Draw a card.
            Equip {3}
        """,
    ) parsesTo abilities(
        static(pt(1, 2), has(Keyword.Reach), Mechanic.MaximumBlockers(1), affects = equipped),
        clueSacrifice,
        equip("{3}"),
    )

    // CR 702.6a: a second Equip ability restricted to Detectives; both are separate abilities
    @Test
    fun thinkingCapLike() = card(
        name = "Deerstalker Cap Test",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+2.
            Equip Detective {1}
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(1, 2), affects = equipped),
        Keyword.Equip(manaCost("{1}"), onto = sub("Detective")),
        equip("{3}"),
    )

    // CR 113.3d: the granted activated ability is the equipped creature's
    @Test
    fun wrenchLike() = card(
        name = "Spanner Test",
        cost = "{W}",
        type = "Artifact — Clue Equipment",
        text = """
            Equipped creature gets +1/+1 and has vigilance and "{3}, {T}: Tap target creature."
            {2}, Sacrifice this Equipment: Draw a card.
            Equip {2}
        """,
    ) parsesTo abilities(
        static(pt(1, 1), has(Keyword.Vigilance, activated(cost(mana("{3}"), tap), tapIt(target("creature")), targets = listOf(slot("creature", creature)))),
            affects = equipped),
        clueSacrifice,
        equip("{2}"),
    )

    // CR 605.1a, 602.5b: once each turn; CR 702.xx exiling itself from the graveyard is a cost that works there
    @Test
    fun gravestoneStriderLike() = card(
        name = "Tombstone Golem Test",
        cost = "{2}",
        type = "Artifact Creature — Golem",
        pt = "1/3",
        text = """
            {1}: Add one mana of any color. Activate only once each turn.
            {2}, Exile this card from your graveyard: Exile target card from a graveyard.
        """,
    ) parsesTo abilities(
        activated(manaCost("{1}"),
            doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You)),
            rules = anyTime.copy(perTurn = 1)),
        activated(cost(mana("{2}"), CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)),
            move(target("card"), Zone.Exile, postBind = "exiled"),
            targets = listOf(slot("card", and(cardFilter, inZone(Zone.Graveyard))))),
    )

    // CR 118.8, 601.2b: one of two additional costs, chosen as the spell is cast
    @Test
    fun demandAnswersLike() = card(
        name = "Press for Facts Test",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice an artifact or discard a card.
            Draw two cards.
        """,
    ) parsesTo abilities(
        additionalCostOneOf(
            cost(sacrificeA(artifact)),
            cost(discardACard),
        ),
        spell(draw(2)),
    )

    // CR 118.8: the sacrificed creature must have dealt damage this turn
    @Test
    fun treacherousGreedLike() = card(
        name = "Spiteful Avarice Test",
        cost = "{1}{W}{B}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice a creature that dealt damage this turn.
            Draw three cards. Each opponent loses 3 life and you gain 3 life.
        """,
    ) parsesTo abilities(
        additionalCost(sacrificeA(and(creature, ObjectFilter.ThisTurn(EventPattern.Damage(ObjectFilter.Any, arcanum.api.RecipientFilter()), role = arcanum.api.EventRole.Source)))),
        spell(draw(3), loseLife(3, Players.Each(PlayerFilter.Opponent)), gainLife(3)),
    )

    // CR 509.1b: a conditional "can't be blocked" is a static ability that applies only while the condition holds
    @Test
    fun furtiveCourierLike() = card(
        name = "Sneaking Ferryman Test",
        cost = "{2}{U}",
        type = "Creature — Merfolk Advisor",
        pt = "3/2",
        text = """
            This creature can't be blocked as long as you've sacrificed an artifact this turn.
            Whenever this creature attacks, draw a card, then discard a card.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeBlocked, condition = Condition.HappenedThisTurn(
            EventPattern.Sacrifice(and(artifact, you)), atLeast = 1, measure = Measure.Events)),
        triggered(attacksSelf, draw(1), discard(1)),
    )

    // CR 509.1b, 613.1f: applies to each creature you control with power 2 or less
    @Test
    fun delneyLike() = card(
        name = "Doorstep Lookout Test",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Scout",
        pt = "2/2",
        text = """
            Creatures you control with power 2 or less can't be blocked by creatures with power 3 or greater.
        """,
    ) parsesTo abilities(
        static(Mechanic.CantBeBlockedBy(and(creature, power(Comparison.AtLeast, 3))),
            affects = Objects.Each(and(creature, you, power(Comparison.AtMost, 2)))),
    )

    // CR 101.2, 701.5: "can't be countered" is a static ability of the spell
    @Test
    fun longGoodbyeLike() = card(
        name = "Curt Farewell Test",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            This spell can't be countered.
            Destroy target creature or planeswalker with mana value 3 or less.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantBeCountered), affects = Affected.Objects(Objects.Self)),
        spell(destroy(target("permanent")), targets = listOf(slot("permanent",
            and(or(creature, ObjectFilter.Type(CardType.Planeswalker)), manaValue(Comparison.AtMost, 3))))),
    )

    // CR 107.3a: -X/-X, with X announced
    @Test
    fun sliceFromTheShadowsLike() = card(
        name = "Cut from the Dark Test",
        cost = "{X}{B}",
        type = "Instant",
        text = """
            This spell can't be countered.
            Target creature gets -X/-X until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantBeCountered), affects = Affected.Objects(Objects.Self)),
        spell(untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(arcanum.api.Value.Times(arcanum.api.Value.Announced("X"), -1), arcanum.api.Value.Times(arcanum.api.Value.Announced("X"), -1))),
            targets = listOf(slot("creature", creature)), announced = listOf("X")),
    )

    // CR 601.2f: the reduction applies when the condition holds as the cost is determined
    @Test
    fun suspiciousDetonationLike() = card(
        name = "Doubtful Blast Test",
        cost = "{4}{R}",
        type = "Sorcery",
        text = """
            This spell costs {3} less to cast if you've sacrificed an artifact this turn.
            This spell can't be countered.
            Doubtful Blast Test deals 4 damage to target creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.CostChange(what = isSelf, change = CostChange.Less(mana = arcanum.api.ManaCost.parse("{3}")!!, times = n(1))),
            condition = Condition.HappenedThisTurn(EventPattern.Sacrifice(and(artifact, you)), atLeast = 1, measure = Measure.Events)),
        Ability.Static(listOf(Mechanic.CantBeCountered), affects = Affected.Objects(Objects.Self)),
        spell(damage(4, toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
    )
}
