package arcanum.parser.sets.lci

import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.EventRole
import arcanum.api.NumericProperty
import arcanum.api.Zone
import arcanum.api.Condition
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
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
import arcanum.api.Value
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** lci fixes: wordings the set's chunks left unread (Unstable Glyphbridge // Sandswirl Wanderglyph). */
class LciFixesTest {
    private val planeswalkerYouControl = and(ObjectFilter.Type(CardType.Planeswalker), you)

    // CR 608.2c, 101.4: you choose one creature for each player (not targeted), then all creatures but the chosen are destroyed at once
    // (CR 608.2e); CR 603.4: "if you cast it"
    @Test
    fun waveringGlyphgate() = card(
        name = "Wavering Glyphgate",
        cost = "{3}{W}{W}",
        type = "Artifact",
        text = """
            When this artifact enters, if you cast it, for each player, choose a creature with power 2 or less that player controls. Then destroy all creatures except creatures chosen this way.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), "player", together = true, steps = listOf(
                Step.Choose(Choice.Objects(and(creature, power(Comparison.AtMost, 2), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player")))),
                    Value.Fixed(1), Value.Fixed(1)), bind = "chosen", chooser = Players.You))),
            Step.Do(Effect.Destroy(Objects.Except(Objects.Each(creature), Objects.Bound("chosen")))),
            interveningIf = Condition.CastFrom(null, Objects.Self)),
    )

    // CR 603.2: "during their turn": only a spell its caster casts during their own turn triggers it; CR 508.1c: that player's creatures can't
    // attack you or your planeswalkers this turn. CR 506.2, 508.1: only the active player attacks, so the opponent who attacked you this turn
    // is the active player once a creature attacked you or a planeswalker you control
    @Test
    fun duneglassGlyphwalker() = card(
        name = "Duneglass Glyphwalker",
        cost = "{3}{W}{W}",
        type = "Artifact Creature — Golem",
        pt = "5/3",
        text = """
            Flying
            Whenever an opponent casts a spell during their turn, they can't attack you or planeswalkers you control this turn.
            Each opponent who attacked you or a planeswalker you control this turn can't cast spells.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Opponent)),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantAttack(Recipients(objects = Objects.Each(planeswalkerYouControl), players = Players.You))),
                Affected.Objects(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer))))), Duration.EndOfTurn)),
            triggersOnlyIf = Condition.PlayerMatches(Players.EventPlayer, PlayerFilter.Is(Players.Active))),
        playerStatic(Mechanic.CantCast(ObjectFilter.Any),
            players = Players.Each(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Is(Players.Active)))),
            condition = Condition.HappenedThisTurn(EventPattern.Attack(ObjectFilter.Any, target = RecipientFilter(objects = planeswalkerYouControl,
                players = PlayerFilter.You)), atLeast = 1, measure = Measure.Events)),
    )

    private fun lifeAndCards(x: Value) = arrayOf(Step.Do(Effect.GainLife(Players.You, x)), Step.Do(Effect.Draw(Players.You, x)))
    private val sacrificeAnother = CostPart.Sacrifice(CostObjects.Choose(and(another, creature), Value.Fixed(1)), preBind = "sacrificed")
    private val sacrificedPower = Value.Of(NumericProperty.Power, Objects.Bound("sacrificed"), Combine.Single)

    // CR 608.2h: "that creature's power" is the sacrificed creature's, as it last existed on the battlefield
    @Test
    fun ashenAcolyte() = card(
        name = "Ashen Acolyte",
        cost = "{3}{B}",
        type = "Creature — Human Wizard",
        pt = "2/1",
        text = "When this creature enters, sacrifice another creature. You gain X life and draw X cards, where X is that creature's power.",
    ) parsesTo abilities(
        triggered(entersSelf,
            Step.Choose(Choice.Objects(and(another, creature, you), Value.Fixed(1), Value.Fixed(1)), bind = "sacrificed", chooser = Players.You),
            Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            *lifeAndCards(sacrificedPower)),
    )

    // CR 608.2c, 608.2h: "you may sacrifice …. If you do, …": the sacrificed creature's power
    @Test
    fun groveVotary() = card(
        name = "Grove Votary",
        cost = "{3}{G}{G}{G}",
        type = "Creature — Elf Druid",
        pt = "3/3",
        text = "When this creature enters, you may sacrifice another creature. If you do, you gain X life and draw X cards, where X is that creature's power.",
    ) parsesTo abilities(
        triggered(entersSelf, Step.Pay(Cost(listOf(sacrificeAnother)), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = lifeAndCards(sacrificedPower).toList())),
    )

    // CR 603.10a, 608.2h: a dies trigger reads the creature's toughness as it last existed on the battlefield
    @Test
    fun mottledSaurianEgg() = card(
        name = "Mottled Saurian Egg",
        cost = "{1}{G}",
        type = "Creature — Dinosaur Egg",
        pt = "0/3",
        text = "When this creature dies, you may discover X, where X is its toughness.",
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.May(listOf(discover(Value.Of(NumericProperty.Toughness, Objects.EventObject(EventRole.Before), Combine.Single))), Players.You)),
    )

    // CR 603.10a: the enchanted creature's toughness as it last existed
    @Test
    fun elderboughFruit() = card(
        name = "Elderbough Fruit",
        cost = "{3}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When enchanted creature dies, you gain X life and draw X cards, where X is its toughness.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)), from = Zone.Battlefield, to = Zone.Graveyard)),
            *lifeAndCards(Value.Of(NumericProperty.Toughness, Objects.EventObject(EventRole.Before), Combine.Single))),
    )
}
