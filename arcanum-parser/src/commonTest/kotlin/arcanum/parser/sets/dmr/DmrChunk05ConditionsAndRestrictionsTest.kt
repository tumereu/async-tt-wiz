package arcanum.parser.sets.dmr

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.SubtypeRef
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** DMR chunk 05: replacement and cost statics, conditions and restrictions. */
class DmrChunk05ConditionsAndRestrictionsTest {
    private val endStep: Trigger = stepBegins(TurnStep.End, PlayerFilter.Any)

    // CR 614.1a, 122.6: a discard caused by an opponent's spell or ability puts it onto the battlefield with counters instead
    @Test
    fun duodecipede() = card(
        name = "Duodecipede",
        cost = "{4}",
        type = "Artifact Creature — Golem",
        text = "If a spell or ability an opponent controls causes you to discard this card, put it onto the battlefield with two +1/+1 counters on it " +
            "instead of putting it into your graveyard.",
        pt = "3/3",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(
            EventPattern.Discard(isSelf, PlayerFilter.You, causedBy = opponentsCause),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Battlefield, counters = listOf(CountersToPut(p1p1, n(2)))))))),
            affects = Affected.Objects(Objects.Self), functionsIn = listOf(Zone.Hand)),
    )

    // CR 701.20: reveal until (CR 118.12: "you may exile it. If you do" is an optional cost, unpaid once the card has left the graveyard);
    // that card onto the battlefield, the other revealed cards into the graveyard
    @Test
    fun warrenkeeper() = card(
        name = "Warrenkeeper",
        cost = "{3}{G}",
        type = "Creature — Elf",
        text = "When this creature dies, you may exile it. If you do, reveal cards from the top of your library until you reveal a creature card. " +
            "Put that card onto the battlefield and put all other cards revealed this way into your graveyard.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(diesSelf,
            Step.Pay(Cost(listOf(CostPart.Exile(CostObjects.These(Objects.EventObject(EventRole.After)), from = Zone.Graveyard))), payer = Players.You,
                mandatory = false, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(
                Step.Do(Effect.RevealUntil(creature, Players.You, n(1), reveal = true, foundBind = "found", restBind = "rest")),
                move(Objects.Bound("found"), Zone.Battlefield),
                move(Objects.Bound("rest"), Zone.Graveyard)))),
    )

    // CR 614.1a, 119.10: a life gain replaced by none is no life gain event
    @Test
    fun brimstoneMaelstrom() = card(
        name = "Brimstone Maelstrom",
        cost = "{1}{R}{R}",
        type = "Enchantment",
        text = """
            At the beginning of each player's upkeep, this enchantment deals 2 damage to that player.
            If a player would gain life, that player gains no life instead.
        """,
    ) parsesTo abilities(
        triggered(eachUpkeep, damage(2, Recipients(players = Players.EventPlayer))),
        static(Mechanic.Replacement(EventPattern.GainLife(PlayerFilter.Any), modify = listOf(Modification.Skip))),
    )

    // CR 611.3a: each untapped creature's own "as long as it's not attacking"
    @Test
    fun vesperanElder() = card(
        name = "Vesperan Elder",
        cost = "{2}{G}{G}{W}{W}{U}{U}",
        type = "Legendary Creature — Elder Dragon",
        text = """
            Flying
            At the beginning of your upkeep, sacrifice Vesperan Elder unless you pay {G}{W}{U}.
            Each untapped creature you control gets +0/+2 as long as it's not attacking.
            {W}: Vesperan Elder gets +0/+1 until end of turn.
        """,
        pt = "7/7",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(yourUpkeep, Step.Pay(manaCost("{G}{W}{U}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(doing(Effect.Sacrifice(Objects.Self))))),
        static(pt(0, 2), affects = Objects.Each(and(not(attacking), not(tapped), creature, you))),
        activated(cost(mana("{W}")), pump(Objects.Self, 0, 1)),
    )

    // CR 303.4: enchanted = an Aura is attached to it
    @Test
    fun thranicSentinel() = card(
        name = "Thranic Sentinel",
        cost = "{5}",
        type = "Artifact Creature — Golem",
        text = "As long as this creature is enchanted, it gets +2/+2 and has flying, first strike, and trample.",
        pt = "3/3",
    ) parsesTo abilities(
        static(pt(2, 2), has(Keyword.Flying, Keyword.FirstStrike, Keyword.Trample),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.Attached(attachments = sub("Aura")))),
    )

    // CR 602.5b: activated from the graveyard only during its controller's upkeep
    @Test
    fun gravesavant() = card(
        name = "Gravesavant",
        cost = "{3}{B}{B}{B}",
        type = "Creature — Zombie Giant",
        text = "{3}{B}{B}, Sacrifice a creature: Return this card from your graveyard to the battlefield. Activate only during your upkeep.",
        pt = "5/5",
    ) parsesTo abilities(
        activated(cost(mana("{3}{B}{B}"), sacrificeA(creature)), move(Objects.Self, Zone.Battlefield, postBind = "returned"),
            rules = anyTime.copy(condition = Condition.And(listOf(Condition.YourTurn, Condition.StepIs(TurnStep.Upkeep))))),
    )

    // CR 111.1, 603.7: a token for each Aura and Equipment attached to it, exiled at the next end step
    @Test
    fun orvekWardenOfThePyre() = card(
        name = "Orvek, Warden of the Pyre",
        cost = "{2}{R}",
        type = "Legendary Creature — Human Shaman",
        text = "At the beginning of combat on your turn, for each Aura and Equipment attached to Orvek, create a 3/1 red Elemental creature token " +
            "with trample and haste. Exile those tokens at the beginning of the next end step.",
        pt = "3/2",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn,
            Step.Do(Effect.CreateToken(creatureToken("Elemental", listOf(Color.Red), 3, 1, listOf(Keyword.Trample, Keyword.Haste)),
                Value.Count(Objects.AttachmentsOf(Objects.Self, or(sub("Aura"), sub("Equipment")))), Players.You, bind = "tokens")),
            doing(Effect.CreateDelayedTrigger(endStep, Instructions(steps = listOf(move(Objects.Bound("tokens"), Zone.Exile)))))),
    )

    // CR 601.2f: every player's spells
    @Test
    fun circletOfRousing() = card(
        name = "Circlet of Rousing",
        cost = "{2}",
        type = "Artifact",
        text = "Spells cost {1} less to cast.",
    ) parsesTo abilities(
        static(Mechanic.CostChange(spellFilter, CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
    )

    // CR 611.2f: the next instant or sorcery spell
    @Test
    fun overmastery() = card(
        name = "Overmastery",
        cost = "{R}",
        type = "Sorcery",
        text = """
            The next instant or sorcery spell you cast this turn can't be countered.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(doing(Effect.ApplyMechanics(listOf(Mechanic.CantBeCountered), Affected.NextSpells(and(or(instant, sorcery), spellFilter, you), 1),
            Duration.EndOfTurn)), draw(1)),
    )

    // CR 601.3 (only during combat, CR 506.1), 603.7 (the sacrifice and its "If you do" are the delayed trigger), 608.2h (its toughness)
    @Test
    fun vertebralClasp() = card(
        name = "Vertebral Clasp",
        cost = "{3}{U}{U}{B}",
        type = "Instant",
        text = """
            Cast this spell only during combat.
            Untap target creature you don't control and gain control of it. It gains haste until end of turn. At the beginning of the next end step, sacrifice it. If you do, you gain life equal to its toughness.
        """,
    ) parsesTo abilities(
        static(Mechanic.CastRestriction(condition = Condition.Or(listOf(TurnStep.BeginningOfCombat, TurnStep.DeclareAttackers,
            TurnStep.DeclareBlockers, TurnStep.CombatDamage, TurnStep.EndOfCombat).map { Condition.StepIs(it) }))),
        spell(untapIt(target("creature")),
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("creature")), Duration.Indefinitely)),
            gains(target("creature"), Keyword.Haste),
            doing(Effect.CreateDelayedTrigger(endStep, Instructions(steps = listOf(
                Step.Do(Effect.Sacrifice(target("creature"), postBind = "sacrificed")),
                Step.If(Condition.NonEmpty("sacrificed"), then = listOf(
                    Step.Do(Effect.GainLife(Players.You, toughness(Objects.Bound("sacrificed")))))))))),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))),
    )

    // CR 601.2f: creature spells of the type chosen as it entered (CR 607.2d)
    @Test
    fun broodIncubator() = card(
        name = "Brood Incubator",
        cost = "{3}",
        type = "Artifact",
        text = """
            As this artifact enters, choose a creature type.
            Creature spells of the chosen type cost {2} less to cast.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.CreatureType, bind = "type", remember = true, chooser = Players.You)))))), affects = Affected.Objects(Objects.Self)),
        static(Mechanic.CostChange(and(creature, spellFilter, ObjectFilter.Sub(SubtypeRef.Remembered("type"))),
            CostChange.Less(ManaCost.parse("{2}")!!, n(1)))),
    )

    // CR 601.2d (the division as it is cast), 615.7 (each target a shield of its share)
    @Test
    fun salvingTouch() = card(
        name = "Salving Touch",
        cost = "{1}{W}",
        type = "Instant",
        text = "Prevent the next 5 damage that would be dealt this turn to any number of targets, divided as you choose.",
    ) parsesTo abilities(
        spell(doing(Effect.PreventDamage(toAny, Value.DividedShare, from = ObjectFilter.Any, duration = Duration.EndOfTurn)),
            targets = listOf(anyTarget.copy(min = n(0), max = null, divided = n(5)))),
    )

    // CR 122.5: any number of the counters, onto any of the other creatures
    @Test
    fun rememberedElder() = card(
        name = "Remembered Elder",
        cost = "{3}{G}",
        type = "Creature — Elemental",
        text = """
            Whenever a player casts a spell, you may put a +1/+1 counter on this creature.
            At the beginning of your upkeep, you may move any number of +1/+1 counters from this creature onto other creatures.
        """,
        pt = "0/3",
    ) parsesTo abilities(
        triggered(on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Any)), Step.May(listOf(counters(Objects.Self)), player = Players.You)),
        triggered(yourUpkeep, Step.May(listOf(Step.ForEach(Each.Objects(Objects.Each(and(another, creature))), "creature", listOf(
            Step.Choose(Choice.Number(n(0), Value.Of(NumericProperty.Counters(p1p1), Objects.Self, Combine.Single)), bind = "moved", chooser = Players.You),
            Step.Do(Effect.MoveCounters(Objects.Self, Objects.Bound("creature"), p1p1, Value.Bound("moved")))))), player = Players.You)),
    )

    // CR 614.1c, 400.7: a counter for each spell cast this turn other than the one it was
    @Test
    fun tempestEntity() = card(
        name = "Tempest Entity",
        cost = "{1}{R}",
        type = "Creature — Elemental",
        text = """
            Haste
            This creature enters with a +1/+1 counter on it for each other spell cast this turn.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Haste,
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.EntersWithCounters(p1p1,
            Value.EventsThisTurn(EventPattern.Cast(not(isSelf), PlayerFilter.Any), Measure.Events))))),
    )

    private val opponentsCause: ObjectFilter = ObjectFilter.ControlledBy(PlayerFilter.Opponent)
}
