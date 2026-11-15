package arcanum.parser.sets.trk

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * TRK chunk 12: triggers: an until-end-of-turn counter replacement made by a tap trigger (CR 614.1a), a number and a name chosen as it enters
 * (CR 607.2d), counters that remove themselves (CR 122.8), combat on each player's turn (CR 507.1), a delayed trigger at the next combat
 * (CR 603.7), "choose one or more instead" (CR 700.2d), explore (CR 701.44), a cast or an activation (CR 602.1) and a plural name dying (CR 700.4).
 */
class TrkChunk12TriggersTest {
    private val isSelf = ObjectFilter.Is(Objects.Self)
    private val chosenNumber = Value.Remembered("number")
    private val chosenName = ObjectFilter.RememberedName("name")
    private fun countersOnSelf(kind: CounterKind): Value = Value.Of(NumericProperty.Counters(kind), Objects.Self, Combine.Single)

    // CR 701.26 (becomes tapped), 614.1a (counters any player would put on a permanent you control, plus one of each kind), 611.2a (until end of turn)
    @Test
    fun teoGarrowEagerCadet() = card(
        name = "Teo Garrow, Eager Cadet",
        cost = "{1}{W}",
        type = "Legendary Creature — Human Officer",
        pt = "2/2",
        text = """
            Lifelink
            Whenever Teo Garrow becomes tapped, until end of turn, if one or more counters would be put on a permanent you control, that many plus one of each of those kinds of counters are put on that permanent instead.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        triggered(on(EventPattern.Tap(isSelf)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(EventPattern.CountersPut(and(permanentFilter, you)), modify = listOf(Modification.AddAmount(n(1))))),
                null, Duration.EndOfTurn))),
    )

    // CR 614.12, 607.2d (the number chosen as it enters), 202.3 (a spell's mana value), 603.2 (an opponent's cast)
    @Test
    fun liselVarnSignals() = card(
        name = "Lisel Varn, Signals",
        cost = "{2}{U}",
        type = "Legendary Creature — Human Officer",
        pt = "2/4",
        text = """
            Hailing Frequencies — As Lisel Varn enters, choose a number between 0 and 10.
            Open a Channel — Whenever an opponent casts a spell with mana value equal to the chosen number, you draw a card.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.Number(n(0), n(10)), bind = "number", chooser = Players.You, remember = true)))))).copy(abilityWord = "Hailing Frequencies"),
        triggered(on(EventPattern.Cast(and(spellFilter, ObjectFilter.ManaValue(Comparison.Equal, chosenNumber)), PlayerFilter.Opponent)), draw(1),
            abilityWord = "Open a Channel"),
    )

    // CR 701.26 (becomes tapped), 122.1 (named counters), 122.8 (removing them); "each opponent" with any number of players (CR 102.3)
    @Test
    fun rikaDorneRecklessCadet() = card(
        name = "Rika Dorne, Reckless Cadet",
        cost = "{1}{R}",
        type = "Legendary Creature — Human Officer",
        pt = "2/1",
        text = """
            First strike
            Reckless Behavior — Whenever Rika Dorne becomes tapped, put a promotion counter on her. Then if there are three or more promotion counters on her, remove those counters and she deals 2 damage to each opponent.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        triggered(on(EventPattern.Tap(isSelf)),
            // "her": the creature that became tapped, this one
            counters(Objects.EventObject(EventRole.Object), 1, CounterKind.Named("promotion")),
            ifThen(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("promotion"), atLeast = 3)),
                doing(Effect.RemoveCounters(Objects.Self, CounterKind.Named("promotion"), countersOnSelf(CounterKind.Named("promotion")))),
                damage(2, eachOpponent)),
            abilityWord = "Reckless Behavior"),
    )

    // CR 507.1 (beginning of combat on each player's turn), 608.2d (that player decides), 508.1 (no creature they control can attack this turn,
    // 611.2c: a rule change, so creatures they gain later this turn can't either), 104.2b (twelve peace counters: you win)
    @Test
    fun truceOfTheLongNight() = card(
        name = "Truce of the Long Night",
        cost = "{3}{W}",
        type = "Enchantment",
        text = """
            At the beginning of combat on each player's turn, that player may draw a card. If they do, they can't attack this turn and you put a peace counter on this enchantment.
            At the beginning of your upkeep, if this enchantment has twelve or more peace counters on it, you win the game.
        """,
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.BeginningOfCombat, PlayerFilter.Any),
            Step.Pay(steps = listOf(draw(1, Players.EventPlayer)), payer = Players.EventPlayer, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(
                doing(Effect.ApplyMechanics(listOf(Mechanic.CantAttack()),
                    Affected.Objects(Objects.Each(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer))))), Duration.EndOfTurn)),
                counters(Objects.Self, 1, CounterKind.Named("peace")),
            ))),
        triggered(yourUpkeep, doing(Effect.WinGame(Players.You)),
            interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("peace"), atLeast = 12))),
    )

    // CR 714.2 (chapters), 603.7 (a delayed trigger at the next combat this turn; it names the creature now, CR 603.7c), 613.1b (control),
    // 701.21a (each opponent sacrifices, any number of players)
    @Test
    fun theVengeanceOfMoraq() = card(
        name = "The Vengeance of Moraq",
        cost = "{2}{R}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — This Saga deals 4 damage to target creature an opponent controls. At the beginning of the next combat this turn, gain control of that creature until end of turn. Untap it. It gains haste until end of turn.
            II — You may exile a card from your hand. If you do, draw a card.
            III — Each opponent sacrifices a nontoken permanent of their choice.
        """,
    ) parsesTo abilities(
        chapter(1,
            damage(4, toObjects(target("creature"))),
            Step.Bind(target("creature"), "creature"),
            doing(Effect.CreateDelayedTrigger(stepBegins(TurnStep.BeginningOfCombat, PlayerFilter.Any), Instructions(steps = listOf(
                untilEndOfTurn(Objects.Bound("creature"), Mechanic.ControlledBy(Players.You)),
                untapIt(Objects.Bound("creature")),
                gains(Objects.Bound("creature"), Keyword.Haste),
            )), duration = Duration.EndOfTurn)),
            targets = listOf(slot("creature", creatureOpponentControls))),
        chapter(2,
            Step.Pay(Cost(listOf(CostPart.Exile(CostObjects.Choose(cardFilter, count = n(1)), from = Zone.Hand))), payer = Players.You, mandatory = false, bind = "paid"),
            ifThen(Condition.Did("paid"), draw(1))),
        chapter(3, Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", together = true, steps = listOf(
            Step.Choose(Choice.Objects(and(not(token), permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))), n(1), n(1)),
                bind = "sacrificed", chooser = Players.Bound("opponent")),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
        ))),
    )

    // CR 603.2 (enters or attacks), 700.2d (one or more modes when you have no cards in hand as the modes are chosen, CR 603.3c)
    @Test
    fun captainArlenVDace() = card(
        name = "Captain Arlen V. Dace",
        cost = "{2}{R}",
        type = "Legendary Creature — Human Officer",
        pt = "2/2",
        text = """
            Whenever Captain Dace enters or attacks, choose one. If you have no cards in hand, choose one or more instead.
            • Discard a card, then draw a card.
            • Create a 1/1 red Officer creature token.
            • Creatures you control get +1/+0 until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Triggered(entersOrAttacksSelf, Instructions(modes = Modes(listOf(
            Mode(Instructions(steps = listOf(discard(1), draw(1)))),
            Mode(Instructions(steps = listOf(create(creatureToken("Officer", listOf(Color.Red), 1, 1))))),
            Mode(Instructions(steps = listOf(pump(Objects.Each(creatureYouControl), 1, 0)))),
        ), min = 1, max = 1, maxIf = Condition.Not(Condition.Exists(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), 1)), maxIfCount = 3, upToMaxIf = true, chooser = Players.You))),
    )

    // CR 305.2 (an additional land), 603.2 (this or another creature you control enters), 701.44a (that creature explores)
    @Test
    fun captainMirelaOkonji() = card(
        name = "Captain Mirela Okonji",
        cost = "{3}{G}",
        type = "Legendary Creature — Human Officer",
        pt = "2/3",
        text = """
            You may play an additional land on each of your turns.
            Whenever Captain Okonji or another creature you control enters, that creature explores. (Reveal the top card of your library. Put that card into your hand if it's a land. Otherwise, put a +1/+1 counter on the creature, then put the card back or put it into your graveyard.)
        """,
    ) parsesTo abilities(
        playerStatic(Mechanic.ExtraLandPlays(1)),
        triggered(on(EventPattern.ZoneChange(or(isSelf, and(another, creature, you)), to = Zone.Battlefield)),
            doing(Effect.Action(KeywordAction.Explore(Objects.EventObject(EventRole.After))))),
    )

    // CR 614.12, 607.2d (the name chosen as it enters), 602.1 (activating an ability; mana abilities are activated abilities too, CR 605.1a),
    // 603.2 (an opponent's cast or activation)
    @Test
    fun vedekSorraFaithlessElder() = card(
        name = "Vedek Sorra, Faithless Elder",
        cost = "{1}{B}",
        type = "Legendary Creature — Bajoran Cleric",
        pt = "3/1",
        text = """
            As Vedek Sorra enters, choose a nonland card name.
            Whenever an opponent casts a spell with the chosen name or activates an ability of a source with the chosen name, that player loses 3 life.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.CardName(nonland), bind = "name", remember = true, chooser = Players.You)))))),
        Ability.Triggered(Trigger.On(listOf(
            EventPattern.Cast(and(spellFilter, chosenName), PlayerFilter.Opponent),
            EventPattern.Activate(chosenName, PlayerFilter.Opponent, manaAbilities = true),
        )), Instructions(steps = listOf(loseLife(3, Players.EventPlayer)))),
    )

    // CR 700.4 (dies), 201.5 (a plural name of this object), 702.10 (a token with haste)
    @Test
    fun theVarekBrothers() = card(
        name = "The Varek Brothers",
        cost = "{B/R}{B/R}",
        type = "Legendary Creature — Klingon Warrior",
        pt = "3/2",
        text = "When The Varek Brothers die, create a 2/1 red Klingon Warrior creature token with haste.",
    ) parsesTo abilities(
        triggered(diesSelf, create(creatureToken("Klingon Warrior", listOf(Color.Red), 2, 1, abilities = listOf(Keyword.Haste)))),
    )
}
