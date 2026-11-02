package arcanum.parser.sets.fra

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.anyTarget
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureOpponentControls
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.land
import arcanum.parser.sets.gtc.token
import arcanum.parser.sets.gtc.you
import kotlin.test.Test

/**
 * Reality Fracture chunk 05: planeswalkers and the cards around them. Loyalty abilities (CR 606), one with an announced X
 * removing X loyalty counters (CR 107.3a, 606.4); targets one for each opponent (CR 601.2c); emblems (CR 114); delayed
 * triggers until your next turn (CR 603.7b), one once per batch of creatures attacking each opponent; "exile all but the
 * bottom card"; an activation restricted by the loyalty counters among Jaces you control; a static ability granting another
 * static ability ("No more than one creature can attack this planeswalker"); proliferate X times (CR 701.34) counting
 * planeswalker types (CR 205.3j); searching for a planeswalker card.
 */
class FraChunk05PlaneswalkersTest {
    // CR 504.1, 606.3
    @Test
    fun thePostulantIrenValcourt() = card(
        name = "The Postulant, Iren Valcourt",
        cost = "{2}{U}{U}",
        type = "Legendary Planeswalker — Jace",
        loyalty = "3",
        text = """
            At the beginning of each opponent's draw step, you draw a card.
            [+1]: Create a 1/1 blue Illusion creature token.
            [−2]: For each opponent, return up to one target artifact or creature that player controls to its owner's hand.
            [−6]: Draw three cards. Then put X +1/+1 counters on each creature you control, where X is the number of cards in your hand.
        """,
    ) parsesTo abilities(
            triggered(stepBegins(TurnStep.Draw, PlayerFilter.Opponent), draw(1)),
            loyalty(1, Step.Do(Effect.CreateToken(creatureToken("Illusion", listOf(Color.Blue), 1, 1), n(1), Players.You))),
            loyalty(-2, move(target("permanent"), Zone.Hand), targets = listOf(TargetSlot("permanent",
                TargetKind.Objects(and(or(artifact, creature), controlledBy(PlayerFilter.Is(Players.SlotPlayer)))), min = n(0), max = n(1), perPlayer = PlayerFilter.Opponent))),
            loyalty(-6, draw(3), Step.Do(Effect.PutCounters(Objects.Each(creatureYouControl), p1p1,
                Value.Count(Objects.Each(and(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))))))),
        )

    // CR 114.1
    @Test
    fun orvaneSteadfast() = card(
        name = "Orvane Steadfast",
        cost = "{1}{W}",
        type = "Legendary Planeswalker — Ajani",
        loyalty = "2",
        text = """
            Whenever you gain life, put a loyalty counter on Orvane.
            [0]: You gain 1 life.
            [−4]: Create a 2/2 white Cat Soldier creature token named Orvane's Pridemate with "Whenever you gain life, put a +1/+1 counter on this token."
            [−10]: You get an emblem with "Creatures you control get +2/+2."
        """,
    ) parsesTo abilities(
            triggered(youGainLife, counters(Objects.Self, kind = CounterKind.Loyalty)),
            loyalty(0, gainLife(1)),
            loyalty(-4, Step.Do(Effect.CreateToken(creatureToken("Cat Soldier", listOf(Color.White), 2, 2,
                abilities = listOf(triggered(youGainLife, counters(Objects.Self))), name = "Orvane's Pridemate"), n(1), Players.You, bind = "created"))),
            loyalty(-10, Step.Do(Effect.CreateEmblem(listOf(static(Mechanic.ModifyPowerToughness(n(2), n(2)), affects = Objects.Each(creatureYouControl))), Players.You))),
        )

    // CR 107.3a, 606.4: X announced, X loyalty counters removed
    @Test
    fun velkaFrostOfObedience() = card(
        name = "Velka, Frost of Obedience",
        cost = "{1}{U}{U}",
        type = "Legendary Planeswalker — Chandra",
        loyalty = "3",
        text = """
            [+1]: Surveil 1. If you put a noncreature, nonland card into your graveyard this way, put that card into your hand.
            [+1]: Add {U}. Spend this mana only to cast a noncreature spell.
            [−X]: Tap target artifact or creature. Put X stun counters on it.
            [−6]: You get an emblem with "Whenever you cast a spell, draw a card."
        """,
    ) parsesTo abilities(
            loyalty(1, Step.Do(Effect.Surveil(Players.You, n(1), bind = "surveilled")), Step.If(Condition.ObjectMatches(Objects.Bound("surveilled"), and(not(creature), nonland)),
                then = listOf(move(Objects.Bound("surveilled"), Zone.Hand)))),
            loyalty(1, Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{U}")!!), restriction = and(spellFilter, not(creature))), Players.You))),
            Ability.Activated(cost(CostPart.Loyalty(Value.Times(Value.Announced("X"), -1))), Instructions(
                targets = listOf(slot("permanent", or(artifact, creature))),
                steps = listOf(Step.Do(Effect.Tap(target("permanent"))), Step.Do(Effect.PutCounters(target("permanent"), CounterKind.Stun, Value.Announced("X")))),
                announced = listOf("X")), anyTime),
            loyalty(-6, Step.Do(Effect.CreateEmblem(listOf(triggered(youCast(ObjectFilter.Any), draw(1))), Players.You))),
        )

    // CR 602.5b: activate only if
    @Test
    fun irenRealityShaper() = card(
        name = "Iren, Reality Shaper",
        cost = "{3}{U}{U}",
        type = "Legendary Planeswalker — Jace",
        loyalty = "5",
        text = """
            [+1]: Empower Jace X, where X is the number of Islands you control.
            [−3]: Until your next turn, whenever a creature attacks you or a planeswalker you control, it gets -5/-0 until end of turn.
            [0]: Exile all but the bottom card of each opponent's library. Activate only if there are twenty-five or more loyalty counters among Jaces you control.
        """,
    ) parsesTo abilities(
            loyalty(1, empower(Value.Count(Objects.Each(and(sub("Island"), you))))),
            loyalty(-3, Step.Do(Effect.CreateDelayedTrigger(on(EventPattern.Attack(creature, target = RecipientFilter(objects = and(planeswalker, you), players = PlayerFilter.You))),
                Instructions(steps = listOf(pump(Objects.EventObject(EventRole.Object), -5, 0))), duration = Duration.UntilYourNextTurn, repeats = true))),
            Ability.Activated(cost(CostPart.Loyalty(n(0))), Instructions(steps = listOf(Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), "opponent", listOf(
                Step.Do(Effect.Move(Objects.Except(Objects.Each(and(ObjectFilter.InZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Bound("opponent"))))),
                    Objects.LibraryCards(Players.Bound("opponent"), n(1), fromTop = false)), Destination(Zone.Exile)))), together = true))),
                ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = Condition.Compare(
                    Value.Of(NumericProperty.Counters(CounterKind.Loyalty), Objects.Each(and(sub("Jace"), you)), Combine.Total), Comparison.AtLeast, n(25)))),
        )

    // CR 614.1a, 101.4
    @Test
    fun morrowShroudedSlaughterer() = card(
        name = "Morrow, Shrouded Slaughterer",
        cost = "{3}{B}{B}",
        type = "Legendary Planeswalker — Garruk",
        loyalty = "5",
        text = """
            If a creature an opponent controls would die, exile it instead.
            [+2]: Up to one target creature gets -4/-1 until your next turn.
            [−2]: Each player sacrifices a creature of their choice. If you sacrificed a creature this way, create a 4/4 green Beast creature token with trample.
            [−3]: Each opponent discards two cards. For each opponent who didn't discard two nonland cards this way, you draw a card.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.ZoneChange(creatureOpponentControls, from = Zone.Battlefield, to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
            loyalty(2, Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(n(-4), n(-1))), Affected.Objects(target("creature")), Duration.UntilYourNextTurn)),
                targets = listOf(slot("creature", creature, min = 0))),
            loyalty(-2,
                Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), "player", listOf(
                    Step.Choose(Choice.Objects(and(creature, controlledBy(PlayerFilter.Is(Players.Bound("player")))), n(1), n(1)), bind = "sacrificed", chooser = Players.Bound("player")),
                    Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"), preBind = "sacrificed"))), together = true),
                Step.If(Condition.Compare(Value.Count(Objects.Where(Objects.Bound("sacrificed"), and(creature, you))), Comparison.AtLeast, n(1)),
                    then = listOf(Step.Do(Effect.CreateToken(creatureToken("Beast", listOf(Color.Green), 4, 4, abilities = listOf(Keyword.Trample)), n(1), Players.You))))),
            loyalty(-3,
                Step.Do(Effect.Discard(Players.Each(PlayerFilter.Opponent), n(2), ObjectFilter.Any, preBind = "discarded")),
                Step.Do(Effect.Draw(Players.You, Value.CountPlayers(Players.Each(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Where(Condition.Compare(
                    Value.Count(Objects.Where(Objects.Bound("discarded"), and(nonland, ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Tested))))), Comparison.Less, n(2)))))))))),
        )

    @Test
    fun orvaneRelentless() = card(
        name = "Orvane Relentless",
        cost = "{4}{R}{R}",
        type = "Legendary Planeswalker — Ajani",
        loyalty = "5",
        text = """
            Whenever you activate a loyalty ability, create a 2/2 colorless Wizard Soldier creature token named Cadet.
            [+1]: Creatures you control get +1/+0 and gain haste until end of turn.
            [−2]: Discard your hand, then draw a card for each creature you control.
            [−3]: Orvane deals 4 damage to each creature except for tokens you control.
        """,
    ) parsesTo abilities(
            triggered(youActivateLoyalty, createCadets(1)),
            loyalty(1, pump(Objects.Each(creatureYouControl), 1, 0, Mechanic.GainAbilities(listOf(Keyword.Haste)))),
            loyalty(-2, Step.Do(Effect.Discard(Players.You, n(1), ObjectFilter.Any, objects = Objects.Each(and(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))))),
                Step.Do(Effect.Draw(Players.You, Value.Count(Objects.Each(creatureYouControl))))),
            loyalty(-3, Step.Do(Effect.DealDamage(Recipients(objects = Objects.Each(and(creature, not(and(token, you))))), n(4), Objects.Self))),
        )

    // CR 608.2g: cast while it resolves
    @Test
    fun velkaBrandOfDefiance() = card(
        name = "Velka, Brand of Defiance",
        cost = "{2}{R}{R}",
        type = "Legendary Planeswalker — Chandra",
        loyalty = "4",
        text = """
            [+1]: Exile the top card of your library. You may cast that card. If you don't, Velka deals 2 damage to each opponent.
            [+1]: Add {R}{R}.
            [−3]: Velka deals 4 damage to target creature.
            [−7]: You get an emblem with "Whenever you cast a spell, this emblem deals 5 damage to any target."
        """,
    ) parsesTo abilities(
            loyalty(1, Step.Do(Effect.Move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
                Step.Pay(steps = listOf(Step.Do(Effect.Cast(Objects.Bound("exiled"), PlayKind.Cast, caster = Players.You))), payer = Players.You, bind = "cast"),
                Step.If(Condition.Not(Condition.Did("cast")), then = listOf(damage(2, eachOpponent)))),
            loyalty(1, Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}{R}")!!)), Players.You))),
            loyalty(-3, damage(4, Recipients(objects = target("creature"))), targets = listOf(slot("creature", creature))),
            loyalty(-7, Step.Do(Effect.CreateEmblem(listOf(triggered(youCast(ObjectFilter.Any), Step.Do(Effect.DealDamage(Recipients(target = "any"), n(5), Objects.Self)),
                targets = listOf(anyTarget))), Players.You))),
        )

    // CR 603.7b: once for each batch, for each opponent attacked
    @Test
    fun morrowHexSunderer() = card(
        name = "Morrow, Hex Sunderer",
        cost = "{3}{G}{G}",
        type = "Legendary Planeswalker — Garruk",
        loyalty = "5",
        text = """
            Whenever a creature you control with power 4 or greater enters, draw a card.
            [+2]: Untap up to two target lands.
            [−3]: Create a 4/4 green Beast creature token with trample.
            [−4]: Until your next turn, whenever one or more creatures attack one of your opponents, those creatures get +2/+2 and gain trample until end of turn.
        """,
    ) parsesTo abilities(
            triggered(on(EventPattern.ZoneChange(and(creature, you, ObjectFilter.Power(Comparison.AtLeast, n(4))), to = Zone.Battlefield)), draw(1)),
            loyalty(2, Step.Do(Effect.Untap(target("land"))), targets = listOf(slot("land", land, min = 0, max = 2))),
            loyalty(-3, Step.Do(Effect.CreateToken(creatureToken("Beast", listOf(Color.Green), 4, 4, abilities = listOf(Keyword.Trample)), n(1), Players.You))),
            loyalty(-4, Step.Do(Effect.CreateDelayedTrigger(Trigger.On(listOf(EventPattern.Attack(creature, target = RecipientFilter(players = PlayerFilter.Opponent))),
                oneOrMore = true, batchPerPlayer = true), Instructions(steps = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(n(2), n(2)),
                Mechanic.GainAbilities(listOf(Keyword.Trample))), Affected.Objects(Objects.EventObject(EventRole.Object)), Duration.EndOfTurn)))),
                duration = Duration.UntilYourNextTurn, repeats = true))),
        )

    // CR 205.3j, 701.34
    @Test
    fun ilseThePotential() = card(
        name = "Ilse, the Potential",
        cost = "{1}{G}{U}",
        type = "Legendary Creature — Gorgon Wizard",
        pt = "2/4",
        text = """
            Planeswalker spells you cast cost {1} less to cast.
            {W}{U}{B}{R}{G}, {T}: Proliferate X times, where X is the number of planeswalker types among planeswalkers you control. (To proliferate, choose any number of permanents and/or players, then give each another counter of each kind already there.)
        """,
    ) parsesTo abilities(
            static(Mechanic.CostChange(and(planeswalker, spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
            activated(cost(mana("{W}{U}{B}{R}{G}"), tap), Step.Repeat(Value.CountDistinct(Objects.Each(and(planeswalker, you)), SharedQuality.PlaneswalkerType),
                listOf(Step.Do(Effect.Action(KeywordAction.Proliferate))))),
        )

    // CR 506.5, 613.1f
    @Test
    fun dovanSyndicateJurist() = card(
        name = "Dovan, Syndicate Jurist",
        cost = "{1}{W}",
        type = "Legendary Creature — Human Advisor",
        pt = "2/1",
        text = """
            Flying
            Planeswalkers you control have "No more than one creature can attack this planeswalker each combat."
            {T}: Target creature with a +1/+1 counter on it gains flying until end of turn.
        """,
    ) parsesTo abilities(
            Keyword.Flying,
            static(Mechanic.GainAbilities(listOf(Ability.Static(listOf(Mechanic.MaximumAttackers(1, target = Recipients(objects = Objects.Self))),
                affects = Affected.Objects(Objects.Each(creature))))), affects = Objects.Each(and(planeswalker, you))),
            activated(cost(tap), gains(target("creature"), Keyword.Flying), targets = listOf(slot("creature", and(creature, ObjectFilter.HasCounter(p1p1, 1))))),
        )

    // CR 702.21a
    @Test
    fun bastienTheForsworn() = card(
        name = "Bastien the Forsworn",
        cost = "{2}{B}",
        type = "Legendary Creature — Human Mercenary",
        pt = "3/3",
        text = """
            Ward—Discard a card.
            Whenever a creature an opponent controls enters, Bastien deals 1 damage to that player.
            Whenever an opponent activates a loyalty ability, Bastien deals 1 damage to that player.
        """,
    ) parsesTo abilities(
            Keyword.Ward(cost(discardACard)),
            triggered(on(EventPattern.ZoneChange(creatureOpponentControls, to = Zone.Battlefield)),
                damage(1, Recipients(players = Players.EventPlayer))),
            triggered(on(EventPattern.Activate(ObjectFilter.Any, PlayerFilter.Opponent, kind = AbilityKind.Loyalty)), damage(1, Recipients(players = Players.EventPlayer))),
        )

    // CR 508.3d, 603.4
    @Test
    fun seraphineOfBrineAndDune() = card(
        name = "Seraphine of Brine and Dune",
        cost = "{1}{G}{U}",
        type = "Legendary Creature — Merfolk Noble",
        pt = "2/4",
        text = """
            Whenever you attack, if you've activated a loyalty ability this turn, untap target attacking creature. It can't be blocked this turn.
            Planeswalkers you control have "[−8]: Create an 8/8 blue Leviathan creature token with hexproof."
        """,
    ) parsesTo abilities(
            Ability.Triggered(Trigger.On(EventPattern.Attack(controlledBy(PlayerFilter.You)), oneOrMore = true), Instructions(
                targets = listOf(slot("creature", and(ObjectFilter.HasStatus(Status.Attacking), creature))),
                steps = listOf(Step.Do(Effect.Untap(target("creature"))), untilEndOfTurn(target("creature"), Mechanic.CantBeBlocked))),
                interveningIf = thisTurn(EventPattern.Activate(ObjectFilter.Any, PlayerFilter.You, kind = AbilityKind.Loyalty))),
            static(Mechanic.GainAbilities(listOf(loyalty(-8, Step.Do(Effect.CreateToken(creatureToken("Leviathan", listOf(Color.Blue), 8, 8,
                abilities = listOf(Keyword.Hexproof)), n(1), Players.You))))), affects = Objects.Each(and(planeswalker, you))),
        )

    // CR 118.12
    @Test
    fun bequeathTheEmber() = card(
        name = "Bequeath the Ember",
        cost = "{3}{G}{U}",
        type = "Sorcery",
        text = "You may sacrifice a planeswalker. If you do, search your library for a planeswalker card, put it onto the battlefield, then shuffle.",
    ) parsesTo abilities(spell(
            Step.Pay(cost(CostPart.Sacrifice(CostObjects.Choose(planeswalker, n(1)))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(
                Step.Do(Effect.Search(Players.You, planeswalker, n(1), zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
                move(Objects.Bound("found"), Zone.Battlefield),
                Step.Do(Effect.Shuffle(Players.You)))),
        ))

    // CR 701.24b: shuffle, then put it on top
    @Test
    fun faithfulMentorSCall() = card(
        name = "Faithful Mentor's Call",
        cost = "{W}",
        type = "Instant",
        text = "Search your library for a planeswalker card, reveal it, then shuffle and put that card on top.",
    ) parsesTo abilities(spell(
            Step.Do(Effect.Search(Players.You, planeswalker, n(1), reveal = true, zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            Step.Do(Effect.Shuffle(Players.You, except = Objects.Bound("found"))),
            move(Objects.Bound("found"), Zone.Library, position = 0),
        ))
}
