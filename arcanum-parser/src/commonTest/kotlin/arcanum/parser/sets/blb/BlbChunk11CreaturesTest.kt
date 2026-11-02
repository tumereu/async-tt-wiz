package arcanum.parser.sets.blb

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Doubled
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Bloomburrow chunk 11: creatures and Auras with enters and leaves triggers. Leaving the battlefield without dying
 * (CR 700.4), last known power, returning another creature as an upkeep cost, per-player damage, revealing until a land,
 * stun counters (CR 122.1d), doubling counters (CR 701.10e), flickering with a counter, players who can't gain life and
 * damage that can't be prevented, base toughness (CR 613.4b).
 */
class BlbChunk11CreaturesTest {
    private val anotherCreatureYouControl = ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, you))
    private val enchanted = Objects.AttachedTo(of = Objects.Self)
    private fun mana(text: String) = CostPart.Mana(arcanum.api.ManaCost.parse(text)!!)
    private val yourAbilities = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)

    // CR 610.3: exiled until this creature leaves; CR 608.2h: "had power 2 or less" is its last known power
    @Test
    fun mistgloamCoyote() = card(
        name = "Mistgloam Coyote",
        cost = "{3}{W}{W}",
        type = "Creature — Elemental Coyote",
        pt = "3/4",
        text = "When this creature enters, exile target creature an opponent controls until this creature leaves the battlefield. If that creature had power 2 or less, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        whenThisEnters(
            Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            Step.If(Condition.ObjectMatches(Objects.Target("creature"), ObjectFilter.Power(Comparison.AtMost, one(2))), then = listOf(counters(Objects.Self))),
            targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, opponents)))),
        ),
    )

    // CR 700.4: leaving without dying is any move from the battlefield but to a graveyard; once for the batch
    @Test
    fun glumHarborMage() = card(
        name = "Glum Harbor-Mage",
        cost = "{1}{U}",
        type = "Creature — Frog Wizard",
        pt = "1/3",
        text = """
            Whenever one or more other creatures you control leave the battlefield without dying, draw a card.
            {1}{U}, {T}: Return another target creature you control to its owner's hand.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.ZoneChange(anotherCreatureYouControl, from = Zone.Battlefield, notTo = Zone.Graveyard), oneOrMore = true),
            steps(Step.Do(Effect.Draw(Players.You, one()))),
        ),
        Ability.Activated(
            Cost(listOf(mana("{1}{U}"), CostPart.TapSelf)),
            Instructions(targets = listOf(slot("creature", anotherCreatureYouControl)), steps = listOf(Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Hand))))),
            yourAbilities,
        ),
    )

    // CR 700.4
    @Test
    fun twinOakScribe() = card(
        name = "Twin Oak Scribe",
        cost = "{1}{G}",
        type = "Creature — Frog Druid",
        pt = "2/3",
        text = "Whenever this creature or another creature you control leaves the battlefield without dying, put a +1/+1 counter on target creature you control.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.ZoneChange(ObjectFilter.Or(listOf(ObjectFilter.Is(Objects.Self), anotherCreatureYouControl)), from = Zone.Battlefield,
                notTo = Zone.Graveyard)),
            Instructions(targets = listOf(slot("creature", creatureYouControl)), steps = listOf(counters(Objects.Target("creature")))),
        ),
    )

    // CR 603.4: an intervening "if" looking back over the turn
    @Test
    fun rainspoutSentry() = card(
        name = "Rainspout Sentry",
        cost = "{2}{U}",
        type = "Creature — Frog Soldier",
        pt = "3/2",
        text = "Whenever this creature attacks, if another creature entered the battlefield under your control this turn, this creature gains flying until end of turn.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
            steps(gains(Objects.Self, Keyword.Flying)),
            interveningIf = Condition.HappenedThisTurn(EventPattern.ZoneChange(anotherCreatureYouControl, to = Zone.Battlefield), atLeast = 1, measure = Measure.Events),
        ),
    )

    // CR 707.2: a token copy; CR 603.7: exiled by a delayed trigger
    @Test
    fun stormcleaverOtter() = card(
        name = "Stormcleaver Otter",
        cost = "{3}{R}",
        type = "Creature — Otter Wizard",
        pt = "1/4",
        text = """
            Haste
            Whenever you cast an instant or sorcery spell, create a token that's a copy of this creature. Exile that token at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        Ability.Triggered(
            Trigger.On(EventPattern.Cast(ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))), by = PlayerFilter.You)),
            steps(
                Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Self), count = one(), controller = Players.You, bind = "tokens")),
                Step.Do(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, whose = PlayerFilter.Any)),
                    steps(Step.Do(Effect.Move(Objects.Bound("tokens"), Destination(Zone.Exile)))))),
            ),
        ),
    )

    // CR 119.7, 615.12; the damage to each player counts that player's own nonbasic lands
    @Test
    fun sunbristleLynx() = card(
        name = "Sunbristle Lynx",
        cost = "{2}{R}{R}",
        type = "Creature — Elemental Cat",
        pt = "5/4",
        text = """
            Players can't gain life.
            Damage can't be prevented.
            When this creature enters, it deals damage to each player equal to the number of nonbasic lands that player controls.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantGainLife), affects = Affected.Players(Players.Each(PlayerFilter.Any))),
        Ability.Static(listOf(Mechanic.DamageCantBePrevented(EventPattern.Damage(ObjectFilter.Any, RecipientFilter()))), affects = Affected.Objects(Objects.Self)),
        whenThisEnters(
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
                Step.Do(Effect.DealDamage(Recipients(players = Players.Bound("player")), Value.Count(Objects.Each(ObjectFilter.And(listOf(
                    ObjectFilter.Not(ObjectFilter.Super(Supertype.Basic)), land, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player"))))))),
                    source = Objects.Self)),
            )),
        ),
    )

    @Test
    fun battleSqueal() = card(
        name = "Battle Squeal",
        cost = "{R}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, target creature an opponent controls can't block this turn.
            Enchanted creature gets +1/+1 and has haste.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        whenThisEnters(
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantBlock(ObjectFilter.Any)), Affected.Objects(Objects.Target("creature")), Duration.EndOfTurn)),
            targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, opponents)))),
        ),
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(one(), one()), Mechanic.GainAbilities(listOf(Keyword.Haste))), affects = Affected.Objects(enchanted)),
    )

    // CR 701.20b: reveal until a land card is revealed
    @Test
    fun ridgetopWatcher() = card(
        name = "Ridgetop Watcher",
        cost = "{2}{G}",
        type = "Creature — Frog Scout",
        pt = "1/2",
        text = """
            Reach
            When this creature enters, reveal cards from the top of your library until you reveal a land card. Put that card onto the battlefield tapped and the rest on the bottom of your library in a random order.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        whenThisEnters(
            Step.Do(Effect.RevealUntil(land, library = Players.You, count = one(), reveal = true, foundBind = "found", restBind = "rest")),
            Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Battlefield, tapped = true))),
            Step.Do(Effect.Move(Objects.Bound("rest"), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
        ),
    )

    // CR 608.2c: "If you do" asks whether another creature was returned
    @Test
    fun fogbreathElder() = card(
        name = "Fogbreath Elder",
        cost = "{G}",
        type = "Creature — Frog Warrior",
        pt = "2/2",
        text = "At the beginning of your upkeep, return another creature you control to its owner's hand. If you do, put a +1/+1 counter on this creature. Otherwise, you may return this creature to its owner's hand.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
            steps(
                Step.Pay(Cost(listOf(CostPart.ReturnToHand(CostObjects.Choose(anotherCreatureYouControl, one())))), payer = Players.You, mandatory = true, bind = "returned"),
                Step.If(Condition.Did("returned"), then = listOf(counters(Objects.Self)),
                    otherwise = listOf(Step.May(listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Hand)))), player = Players.You))),
            ),
        ),
    )

    @Test
    fun gluetongueSentry() = card(
        name = "Gluetongue Sentry",
        cost = "{2}{G}",
        type = "Creature — Frog Warrior",
        pt = "3/3",
        text = """
            Reach
            When this creature enters, return up to one other target permanent you control to its owner's hand.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        whenThisEnters(
            Step.Do(Effect.Move(Objects.Target("permanent"), Destination(Zone.Hand))),
            targets = listOf(slot("permanent", ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), ObjectFilter.Category(ObjectCategory.Permanent), you)), min = 0)),
        ),
    )

    // CR 122.6: counters put by your spells and abilities; once for the batch
    @Test
    fun fillingTheLarder() = card(
        name = "Filling the Larder",
        cost = "{G}",
        type = "Enchantment",
        text = """
            Whenever you put one or more +1/+1 counters on a creature you control, put a supply counter on this enchantment.
            {2}, Remove a supply counter from this enchantment: Draw a card.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.CountersPut(creatureYouControl, p1p1, causedBy = you), oneOrMore = true),
            steps(counters(Objects.Self, kind = CounterKind.Named("supply"))),
        ),
        Ability.Activated(
            Cost(listOf(mana("{2}"), CostPart.RemoveCounters(CounterKind.Named("supply"), one(), CostObjects.Self))),
            steps(Step.Do(Effect.Draw(Players.You, one()))),
            yourAbilities,
        ),
    )

    @Test
    fun drizzlelightDruid() = card(
        name = "Drizzlelight Druid",
        cost = "{G}",
        type = "Creature — Frog Druid",
        pt = "0/2",
        text = "When this creature enters, put a +1/+1 counter on target creature and you gain 1 life.",
    ) parsesTo abilities(
        whenThisEnters(counters(Objects.Target("creature")), Step.Do(Effect.GainLife(Players.You, one())), targets = listOf(slot("creature", creature))),
    )

    // CR 107.3c: X is defined by the ability's own words, counted as it resolves
    @Test
    fun grovekeeperPair() = card(
        name = "Grovekeeper Pair",
        cost = "{3}{G}",
        type = "Creature — Frog Rabbit",
        pt = "3/4",
        text = "When this creature enters, until end of turn, target creature you control gains vigilance and gets +X/+X, where X is the number of creatures you control.",
    ) parsesTo abilities(
        run {
            val x = Value.Count(Objects.Each(creatureYouControl))
            whenThisEnters(
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Vigilance)), Mechanic.ModifyPowerToughness(x, x)),
                    Affected.Objects(Objects.Target("creature")), Duration.EndOfTurn)),
                targets = listOf(slot("creature", creatureYouControl)),
            )
        },
    )

    // "that creature" is the one that entered
    @Test
    fun haloedRedeemer() = card(
        name = "Haloed Redeemer",
        cost = "{3}{W}{W}",
        type = "Creature — Angel Soldier",
        pt = "2/4",
        text = """
            Flying
            Whenever another creature you control with power 2 or less enters, put two +1/+1 counters on that creature.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, you,
                ObjectFilter.Power(Comparison.AtMost, one(2)))), to = Zone.Battlefield)),
            steps(counters(Objects.EventObject(EventRole.After), 2)),
        ),
    )

    // CR 502.3: it doesn't untap in its controller's untap step
    @Test
    fun enchantedSlumber() = card(
        name = "Enchanted Slumber",
        cost = "{1}{U}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, tap enchanted creature.
            Enchanted creature doesn't untap during its controller's untap step.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        whenThisEnters(Step.Do(Effect.Tap(enchanted))),
        Ability.Static(listOf(Mechanic.DoesntUntap), affects = Affected.Objects(enchanted)),
    )

    @Test
    fun pilferingOtter() = card(
        name = "Pilfering Otter",
        cost = "{2}{U}",
        type = "Creature — Otter",
        pt = "2/2",
        text = "Whenever this creature deals damage to an opponent, draw a card.",
    ) parsesTo abilities(
        Ability.Triggered(
            Trigger.On(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Opponent))),
            steps(Step.Do(Effect.Draw(Players.You, one()))),
        ),
    )

    // CR 701.10e: doubling counters puts that many more
    @Test
    fun hargenLongEarOfTheWatch() = card(
        name = "Hargen, Long Ear of the Watch",
        cost = "{4}{G}{W}",
        type = "Legendary Creature — Rabbit Soldier",
        pt = "4/4",
        text = """
            Vigilance
            When Hargen enters, put a +1/+1 counter on each of up to two target creatures.
            Whenever a creature you control with a +1/+1 counter on it attacks, double the number of +1/+1 counters on it.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        whenThisEnters(counters(Objects.Target("creatures")), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
        Ability.Triggered(
            Trigger.On(EventPattern.Attack(ObjectFilter.And(listOf(creature, you, ObjectFilter.HasCounter(p1p1, atLeast = 1))))),
            steps(Step.Do(Effect.Action(KeywordAction.Double(Doubled.Counters(Objects.EventObject(EventRole.Object), p1p1))))),
        ),
    )

    // CR 122.1d: stun counters
    @Test
    fun dewdreamMesmerist() = card(
        name = "Dewdream Mesmerist",
        cost = "{2}{G}{U}",
        type = "Creature — Frog Wizard",
        pt = "3/4",
        text = """
            Reach
            When this creature enters, tap up to one target creature and put three stun counters on it. If you control that creature, draw two cards.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        whenThisEnters(
            Step.Do(Effect.Tap(Objects.Target("creature"))),
            counters(Objects.Target("creature"), 3, CounterKind.Stun),
            Step.If(Condition.ObjectMatches(Objects.Target("creature"), you), then = listOf(Step.Do(Effect.Draw(Players.You, one(2))))),
            targets = listOf(slot("creature", creature, min = 0)),
        ),
    )

    // CR 400.7: the returned card is a new object, with the counter as it enters
    @Test
    fun lilypadTutor() = card(
        name = "Lilypad Tutor",
        cost = "{2}{G}{U}",
        type = "Creature — Frog Druid",
        pt = "4/4",
        text = """
            Reach
            {1}{G}{U}: Exile another target creature you control, then return it to the battlefield under its owner's control with a +1/+1 counter on it. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Ability.Activated(
            Cost(listOf(mana("{1}{G}{U}"))),
            Instructions(targets = listOf(slot("creature", anotherCreatureYouControl)), steps = listOf(
                Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled")),
                    counters = listOf(CountersToPut(p1p1, one()))))),
            )),
            ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You),
        ),
    )

    // CR 613.4b: base toughness set in layer 7b
    @Test
    fun noxaItsWingsDusk() = card(
        name = "Noxa, Its Wings Dusk",
        cost = "{3}{B}{B}",
        type = "Legendary Creature — Elemental Bird",
        pt = "6/5",
        text = """
            Flying, trample
            Ward—Discard a card.
            Creatures your opponents control have base toughness 1.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Trample,
        Keyword.Ward(Cost(listOf(CostPart.Discard(CostObjects.Choose(anyCard, count = one()))))),
        Ability.Static(listOf(Mechanic.SetPowerToughness(null, one())), affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(creature, opponents))))),
    )

    // "lesser mana value" than the creature that entered
    @Test
    fun pelleTheFretful() = card(
        name = "Pelle, the Fretful",
        cost = "{1}{G}{U}",
        type = "Legendary Creature — Frog Druid",
        pt = "3/3",
        text = """
            Vigilance
            Whenever Pelle or another creature you control enters, return up to one target creature you control with lesser mana value to its owner's hand.
            Frogs you control have "{T}: Add {G} or {U}. Spend this mana only to cast a creature spell."
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Ability.Triggered(
            Trigger.On(EventPattern.ZoneChange(ObjectFilter.Or(listOf(ObjectFilter.Is(Objects.Self), anotherCreatureYouControl)), to = Zone.Battlefield)),
            Instructions(
                targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, you, ObjectFilter.ManaValue(Comparison.Less,
                    Value.Of(NumericProperty.ManaValue, Objects.EventObject(EventRole.After), Combine.Single)))), min = 0)),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Hand)))),
            ),
        ),
        Ability.Static(
            listOf(Mechanic.GainAbilities(listOf(Ability.Activated(
                Cost(listOf(CostPart.TapSelf)),
                steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(one(), listOf(ColorRef.Literal(Color.Green), ColorRef.Literal(Color.Blue))),
                    restriction = ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Spell), creature))), Players.You))),
                yourAbilities,
            )))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(sub("Frog"), you)))),
        ),
    )

    // CR 701.16: looking at the top card; a land may go onto the battlefield, anything else goes to the hand
    @Test
    fun fertileGreenback() = card(
        name = "Fertile Greenback",
        cost = "{3}{G}{G}",
        type = "Creature — Elemental Turtle",
        pt = "4/6",
        text = """
            Reach
            As long as you control ten or more lands, creatures you control get +2/+2.
            Whenever this creature or another creature you control with toughness greater than its power enters, look at the top card of your library. If it's a land card, you may put it onto the battlefield tapped. Otherwise, put it into your hand.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(one(2), one(2))), affects = Affected.Objects(Objects.Each(creatureYouControl)),
            condition = Condition.Exists(ObjectFilter.And(listOf(land, you)), atLeast = 10)),
        Ability.Triggered(
            Trigger.On(EventPattern.ZoneChange(ObjectFilter.Or(listOf(ObjectFilter.Is(Objects.Self), ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
                creature, you, ObjectFilter.Toughness(Comparison.Greater, Value.Of(NumericProperty.Power, Objects.Tested, Combine.Single)))))), to = Zone.Battlefield)),
            steps(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, one(), fromTop = true), players = Players.You, bind = "seen")),
                Step.If(Condition.ObjectMatches(Objects.Bound("seen"), land),
                    then = listOf(Step.May(listOf(Step.Do(Effect.Move(Objects.Bound("seen"), Destination(Zone.Battlefield, tapped = true)))), player = Players.You)),
                    otherwise = listOf(Step.Do(Effect.Move(Objects.Bound("seen"), Destination(Zone.Hand))))),
            ),
        ),
    )
}
