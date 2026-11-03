package arcanum.parser.sets.dsk

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
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
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.RecipientFilter
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.thisEnters
import arcanum.parser.sets.dsk.Dsk.create
import arcanum.parser.sets.dsk.Dsk.creature
import arcanum.parser.sets.dsk.Dsk.creatureYouControl
import arcanum.parser.sets.dsk.Dsk.delirium
import arcanum.parser.sets.dsk.Dsk.token
import arcanum.parser.sets.dsk.Dsk.youControl
import kotlin.test.Test

/**
 * Duskmourn chunk 07: delirium ("if there are four or more card types among cards in your graveyard", CR 207.2c ability
 * word): as an intervening "if" (CR 603.4), a "while" checked only as it triggers, a condition of statics, activations,
 * cost reductions and replacement effects, a spell's "instead" and "choose one or more instead", the number of card types
 * itself (X, maximum hand size), distributing and doubling counters (CR 601.2d, 701.10e).
 */
class DskChunk07DeliriumTest {
    private val plusOne = CounterKind.PowerToughness(1, 1)
    private fun slot(name: String, filter: ObjectFilter, min: Int = 1, max: Int = 1) =
        TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(min), max = Value.Fixed(max))
    private val yourGraveyard = ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))
    private val cardTypesInGraveyard = Value.CountDistinct(Objects.Each(yourGraveyard), SharedQuality.CardType)
    private val attacks = Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self)))
    private val basicLand = ObjectFilter.And(listOf(ObjectFilter.Super(Supertype.Basic), ObjectFilter.Type(CardType.Land)))
    private fun untilEndOfTurn(objects: Objects, vararg mechanics: Mechanic) =
        Step.Do(Effect.ApplyMechanics(mechanics.toList(), Affected.Objects(objects), Duration.EndOfTurn))
    private fun search(filter: ObjectFilter, reveal: Boolean) = listOf(
        Step.Do(Effect.Search(Players.You, filter, max = Value.Fixed(1), reveal = reveal, zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
        Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Hand))),
        Step.Do(Effect.Shuffle(Players.You)),
    )

    /** "Return this card from your graveyard to the battlefield with a finality counter on it. Activate only if [delirium]
     * and only as a sorcery." Works from the graveyard by the CR 113.6m default (it moves the card from there); the counter
     * as it enters (CR 122.6, 122.1h). */
    private fun returnWithFinality(cost: String) = Ability.Activated(
        cost = manaCost(cost),
        instructions = Instructions(steps = listOf(Step.Do(Effect.Move(Objects.Self, Destination(Zone.Battlefield,
            counters = listOf(CountersToPut(CounterKind.Finality, Value.Fixed(1)))))))),
        rules = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You, condition = delirium),
        abilityWord = "Delirium",
    )

    /** "If [delirium], instead …": the whole spell is replaced when the condition is true. */
    @Test
    fun infernalCounsel() = card(
        name = "Infernal Counsel",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            Search your library for a Demon card, reveal it, put it into your hand, then shuffle.
            Delirium — If there are four or more card types among cards in your graveyard, instead search your library for any card, put it into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(steps = listOf(Step.If(
                condition = delirium,
                then = search(ObjectFilter.Category(ObjectCategory.Card), reveal = false),
                otherwise = search(ObjectFilter.Sub(Subtype("Demon")), reveal = true),
            ))),
            abilityWord = "Delirium",
        ),
    )

    /** "Choose one. If [delirium], choose one or more instead." (CR 700.2) */
    @Test
    fun shallWePlay() = card(
        name = "Shall We Play",
        cost = "{3}{B}",
        type = "Sorcery",
        text = """
            Delirium — Choose one. If there are four or more card types among cards in your graveyard, choose one or more instead.
            • Creatures your opponents control get -1/-1 until end of turn.
            • Each opponent discards two cards.
            • Each opponent loses 3 life and you gain 3 life.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            instructions = Instructions(modes = Modes(
                options = listOf(
                    Mode(Instructions(steps = listOf(untilEndOfTurn(
                        Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Opponent)))),
                        Mechanic.ModifyPowerToughness(Value.Fixed(-1), Value.Fixed(-1)),
                    )))),
                    Mode(Instructions(steps = listOf(Step.Do(Effect.Discard(Players.Each(PlayerFilter.Opponent), Value.Fixed(2), filter = ObjectFilter.Any))))),
                    Mode(Instructions(steps = listOf(
                        Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), Value.Fixed(3))),
                        Step.Do(Effect.GainLife(Players.You, Value.Fixed(3))),
                    ))),
                ),
                min = 1,
                max = 1,
                maxIf = delirium,
                maxIfCount = 3,
                upToMaxIf = true,
                chooser = Players.You,
            )),
            abilityWord = "Delirium",
        ),
    )

    /** Each opponent decides to pay (sacrifice or discard) in turn order, then the damage to each who didn't (CR 101.4,
     * 118.12). */
    @Test
    fun bonyTwistscare() = card(
        name = "Bony Twistscare",
        cost = "{1}{B}",
        type = "Artifact Creature — Scarecrow",
        pt = "2/2",
        text = """
            Lifelink
            Delirium — At the beginning of your end step, if there are four or more card types among cards in your graveyard, each opponent may sacrifice a nonland permanent of their choice or discard a card. Then this creature deals damage equal to its power to each opponent who didn't sacrifice a permanent or discard a card this way.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.You)),
            instructions = Instructions(steps = listOf(Step.ForEach(
                each = Each.Players(Players.Each(PlayerFilter.Opponent)),
                bind = "opponent",
                together = true,
                steps = listOf(
                    Step.Pay(
                        cost = Cost(listOf(CostPart.OneOf(listOf(
                            Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Type(CardType.Land)),
                                ObjectFilter.Category(ObjectCategory.Permanent))), count = Value.Fixed(1))))),
                            Cost(listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card), count = Value.Fixed(1))))),
                        )))),
                        payer = Players.Bound("opponent"),
                        bind = "paid",
                    ),
                    Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.DealDamage(
                        Recipients(players = Players.Bound("opponent")), Value.Of(NumericProperty.Power, Objects.Self, Combine.Single), source = Objects.Self)))),
                ),
            ))),
            interveningIf = delirium,
            abilityWord = "Delirium",
        ),
    )

    @Test
    fun risenCultist() = card(
        name = "Risen Cultist",
        cost = "{2}{B}",
        type = "Creature — Human Cleric",
        pt = "4/1",
        text = "Delirium — {2}{B}{B}: Return this card from your graveyard to the battlefield with a finality counter on it. Activate only if there are four or more card types among cards in your graveyard and only as a sorcery. (If a creature with a finality counter on it would die, exile it instead.)",
    ) parsesTo abilities(returnWithFinality("{2}{B}{B}"))

    /** "That amount" of noncombat damage, to a creature "that player" (the damaged opponent) controls. */
    @Test
    fun dreadOfBurning() = card(
        name = "Dread of Burning",
        cost = "{4}{R}{R}",
        type = "Enchantment Creature — Nightmare",
        pt = "4/4",
        text = """
            When this creature enters, it deals 4 damage to each opponent.
            Delirium — Whenever a source you control deals noncombat damage to an opponent, if there are four or more card types among cards in your graveyard, this creature deals that amount of damage to target creature that player controls.
        """,
    ) parsesTo abilities(
        Ability.Triggered(thisEnters, Instructions(steps = listOf(Step.Do(Effect.DealDamage(
            Recipients(players = Players.Each(PlayerFilter.Opponent)), Value.Fixed(4), source = Objects.Self))))),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Damage(youControl, RecipientFilter(players = PlayerFilter.Opponent), combat = false)),
            instructions = Instructions(
                targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer)))))),
                steps = listOf(Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), Value.EventAmount, source = Objects.Self))),
            ),
            interveningIf = delirium,
            abilityWord = "Delirium",
        ),
    )

    /** "Attacks for the first time each turn" (the first attack event this turn); an additional combat phase (CR 500.8). */
    @Test
    fun dreadOfBeingLeftOut() = card(
        name = "Dread of Being Left Out",
        cost = "{1}{R}",
        type = "Enchantment Creature — Nightmare",
        pt = "2/3",
        text = """
            When this creature enters, discard a card, then draw a card.
            Delirium — Whenever this creature attacks for the first time each turn, if there are four or more card types among cards in your graveyard, untap target creature. After this phase, there is an additional combat phase.
        """,
    ) parsesTo abilities(
        Ability.Triggered(thisEnters, Instructions(steps = listOf(
            Step.Do(Effect.Discard(Players.You, Value.Fixed(1), filter = ObjectFilter.Any)),
            Step.Do(Effect.Draw(Players.You, Value.Fixed(1))),
        ))),
        Ability.Triggered(
            trigger = Trigger.On(listOf(EventPattern.Attack(ObjectFilter.Is(Objects.Self))), nthThisTurn = 1),
            instructions = Instructions(
                targets = listOf(slot("creature", creature)),
                steps = listOf(Step.Do(Effect.Untap(Objects.Target("creature"))), Step.Do(Effect.ExtraCombat())),
            ),
            interveningIf = delirium,
            abilityWord = "Delirium",
        ),
    )

    /** "Whenever … while …": checked only as it would trigger, not again as it resolves. */
    @Test
    fun feedingHand() = card(
        name = "Feeding Hand",
        cost = "{1}{R}",
        type = "Creature — Mutant",
        pt = "2/2",
        text = "Delirium — Whenever this creature attacks while there are four or more card types among cards in your graveyard, it gets +2/+0 and gains menace until end of turn. (It can't be blocked except by two or more creatures.)",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = attacks,
            instructions = Instructions(steps = listOf(untilEndOfTurn(Objects.Self,
                Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(0)), Mechanic.GainAbilities(listOf(Keyword.Menace))))),
            triggersOnlyIf = delirium,
            abilityWord = "Delirium",
        ),
    )

    /** A spell's ability-word paragraph (CR 207.2c) is its own spell ability; the exiled card may be played (CR 305.1). */
    @Test
    fun inexplicableInferno() = card(
        name = "Inexplicable Inferno",
        cost = "{4}{R}",
        type = "Instant",
        text = """
            Inexplicable Inferno deals 6 damage to target creature.
            Delirium — If there are four or more card types among cards in your graveyard, exile the top card of your library. You may play it until the end of your next turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("creature", creature)),
            steps = listOf(Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), Value.Fixed(6), source = Objects.Self))),
        )),
        Ability.Spell(
            instructions = Instructions(steps = listOf(
                Step.If(delirium, then = listOf(Step.Do(Effect.Move(Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true),
                    Destination(Zone.Exile), postBind = "exiled")))),
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                    Affected.Objects(Objects.Bound("exiled")), Duration.UntilEndOfYourNextTurn)),
            )),
            abilityWord = "Delirium",
        ),
    )

    /** Doubling noncombat damage while delirium holds (CR 614.1a); X damage to each of up to X targets (CR 107.3m: the X
     * the spell was cast with). */
    @Test
    fun theCoasterOfCarnage() = card(
        name = "The Coaster of Carnage",
        cost = "{X}{2}{R}",
        type = "Legendary Enchantment",
        text = """
            Delirium — If a source you control would deal noncombat damage to a permanent or player while there are four or more card types among cards in your graveyard, it deals double that damage instead.
            When The Coaster of Carnage enters, it deals X damage to each of up to X target creatures.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.Replacement(
                EventPattern.Damage(youControl, RecipientFilter(objects = ObjectFilter.Category(ObjectCategory.Permanent), players = PlayerFilter.Any), combat = false),
                modify = listOf(Modification.MultiplyAmount(2)),
            )),
            affects = Affected.Objects(Objects.Self),
            condition = delirium,
            abilityWord = "Delirium",
        ),
        Ability.Triggered(thisEnters, Instructions(
            targets = listOf(TargetSlot("creatures", TargetKind.Objects(creature), min = Value.Fixed(0), max = Value.CastX(Objects.Self))),
            steps = listOf(Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creatures")), Value.CastX(Objects.Self), source = Objects.Self))),
        )),
    )

    /** "That creature" in the delirium paragraph is the first paragraph's target: one spell ability (CR 113.3a). */
    @Test
    fun savageUrge() = card(
        name = "Savage Urge",
        cost = "{R}",
        type = "Instant",
        text = """
            Target creature gets +1/+0 and gains first strike until end of turn.
            Delirium — If there are four or more card types among cards in your graveyard, that creature gains double strike until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("creature", creature)),
            steps = listOf(
                untilEndOfTurn(Objects.Target("creature"), Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(0)),
                    Mechanic.GainAbilities(listOf(Keyword.FirstStrike))),
                Step.If(delirium, then = listOf(untilEndOfTurn(Objects.Target("creature"), Mechanic.GainAbilities(listOf(Keyword.DoubleStrike))))),
            ),
        )),
    )

    @Test
    fun banisterWurm() = card(
        name = "Banister Wurm",
        cost = "{3}{G}{G}",
        type = "Creature — Wurm",
        pt = "5/5",
        text = """
            This spell can't be countered.
            Trample, haste
            Delirium — {2}{G}{G}: Return this card from your graveyard to the battlefield with a finality counter on it. Activate only if there are four or more card types among cards in your graveyard and only as a sorcery.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantBeCountered), affects = Affected.Objects(Objects.Self)),
        Keyword.Trample,
        Keyword.Haste,
        returnWithFinality("{2}{G}{G}"),
    )

    /** Two +1/+1 counters divided among one or two targets as it's put on the Stack (CR 601.2d); doubling counters
     * (CR 701.10e). */
    @Test
    fun ravenousFlytrap() = card(
        name = "Ravenous Flytrap",
        cost = "{2}{G}",
        type = "Creature — Plant",
        pt = "2/4",
        text = "Delirium — Whenever this creature enters or attacks, if there are four or more card types among cards in your graveyard, distribute two +1/+1 counters among one or two target creatures. Then if there are six or more card types among cards in your graveyard, double the number of +1/+1 counters on those creatures.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(listOf(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                EventPattern.Attack(ObjectFilter.Is(Objects.Self)),
            )),
            instructions = Instructions(
                targets = listOf(TargetSlot("creatures", TargetKind.Objects(creature), min = Value.Fixed(1), max = Value.Fixed(2), divided = Value.Fixed(2))),
                steps = listOf(
                    Step.Do(Effect.PutCounters(Objects.Target("creatures"), plusOne, Value.DividedShare)),
                    Step.If(
                        Condition.Compare(cardTypesInGraveyard, Comparison.AtLeast, Value.Fixed(6)),
                        then = listOf(Step.Do(Effect.Action(KeywordAction.Double(Doubled.Counters(Objects.Target("creatures"), plusOne))))),
                    ),
                ),
            ),
            interveningIf = delirium,
            abilityWord = "Delirium",
        ),
    )

    @Test
    fun stitchworkBeastie() = card(
        name = "Stitchwork Beastie",
        cost = "{G}",
        type = "Artifact Creature — Beast",
        pt = "3/3",
        text = """
            Delirium — This creature can't attack or block unless there are four or more card types among cards in your graveyard.
            At the beginning of your upkeep, you may mill a card. (You may put the top card of your library into your graveyard.)
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any)),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.Not(delirium),
            abilityWord = "Delirium",
        ),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
            instructions = Instructions(steps = listOf(Step.May(listOf(Step.Do(Effect.Mill(Players.You, Value.Fixed(1)))), player = Players.You))),
        ),
    )

    @Test
    fun spinebiterCentipede() = card(
        name = "Spinebiter Centipede",
        cost = "{2}{G}",
        type = "Creature — Insect",
        pt = "2/1",
        text = """
            When this creature enters, search your library for a basic land card, reveal it, put it into your hand, then shuffle.
            Delirium — This creature gets +1/+2 and has vigilance as long as there are four or more card types among cards in your graveyard.
        """,
    ) parsesTo abilities(
        Ability.Triggered(thisEnters, Instructions(steps = search(basicLand, reveal = true))),
        Ability.Static(
            mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(2)), Mechanic.GainAbilities(listOf(Keyword.Vigilance))),
            affects = Affected.Objects(Objects.Self),
            condition = delirium,
            abilityWord = "Delirium",
        ),
    )

    /** "If you don't put the card onto the battlefield": whether the "you may" was taken (CR 118.12, D40). */
    @Test
    fun wickerworkReaper() = card(
        name = "Wickerwork Reaper",
        cost = "{3}{G}",
        type = "Artifact Creature — Scarecrow",
        pt = "5/4",
        text = "Delirium — Whenever this creature attacks, if there are four or more card types among cards in your graveyard, look at the top card of your library. If it's a land card, you may put it onto the battlefield. If you don't put the card onto the battlefield, put it into your hand.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = attacks,
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true), players = Players.You, bind = "seen")),
                Step.If(Condition.ObjectMatches(Objects.Bound("seen"), ObjectFilter.Type(CardType.Land)), then = listOf(
                    Step.Pay(steps = listOf(Step.Do(Effect.Move(Objects.Bound("seen"), Destination(Zone.Battlefield)))), payer = Players.You, bind = "put"),
                )),
                Step.If(Condition.Not(Condition.Did("put")), then = listOf(Step.Do(Effect.Move(Objects.Bound("seen"), Destination(Zone.Hand))))),
            )),
            interveningIf = delirium,
            abilityWord = "Delirium",
        ),
    )

    /** Two targets kept apart by their controllers; "the creature you control" and "the creature an opponent controls"
     * are those targets in the later paragraphs of the one spell ability (CR 113.3a). */
    @Test
    fun beastieBrawl() = card(
        name = "Beastie Brawl",
        cost = "{R}{G}",
        type = "Sorcery",
        text = """
            Choose target creature you control and target creature an opponent controls.
            Delirium — If there are four or more card types among cards in your graveyard, put two +1/+1 counters on the creature you control.
            The creature you control deals damage equal to its power to the creature an opponent controls.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(
                slot("creature", creatureYouControl),
                slot("other creature", ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Opponent)))),
            ),
            steps = listOf(
                Step.If(delirium, then = listOf(Step.Do(Effect.PutCounters(Objects.Target("creature"), plusOne, Value.Fixed(2))))),
                Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("other creature")),
                    Value.Of(NumericProperty.Power, Objects.Target("creature"), Combine.Single), source = Objects.Target("creature"))),
            ),
        )),
    )

    @Test
    fun pullToTheRoots() = card(
        name = "Pull to the Roots",
        cost = "{2}{B}{G}",
        type = "Instant",
        text = """
            Delirium — This spell costs {2} less to cast as long as there are four or more card types among cards in your graveyard.
            Destroy target nonland permanent.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{2}")!!, Value.Fixed(1)))),
            affects = Affected.Objects(Objects.Self),
            condition = delirium,
            abilityWord = "Delirium",
        ),
        Ability.Spell(Instructions(
            targets = listOf(slot("permanent", ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Type(CardType.Land)), ObjectFilter.Category(ObjectCategory.Permanent))))),
            steps = listOf(Step.Do(Effect.Destroy(Objects.Target("permanent")))),
        )),
    )

    @Test
    fun theSwarmspinner() = card(
        name = "The Swarmspinner",
        cost = "{2}{B}{G}",
        type = "Legendary Artifact Creature — Scarecrow",
        pt = "2/3",
        text = """
            When The Swarmspinner enters, create two 1/1 black and green Insect creature tokens with flying.
            Delirium — As long as there are four or more card types among cards in your graveyard, Insects and Spiders you control get +1/+1 and have deathtouch.
        """,
    ) parsesTo abilities(
        Ability.Triggered(thisEnters, Instructions(steps = listOf(
            create(token(listOf("Insect"), listOf(Color.Black, Color.Green), 1, 1, abilities = listOf(Keyword.Flying)), count = 2),
        ))),
        Ability.Static(
            mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(1)), Mechanic.GainAbilities(listOf(Keyword.Deathtouch))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(
                ObjectFilter.Or(listOf(ObjectFilter.Sub(Subtype("Insect")), ObjectFilter.Sub(Subtype("Spider")))),
                youControl,
            )))),
            condition = delirium,
            abilityWord = "Delirium",
        ),
    )

    @Test
    fun kindledWickerfolk() = card(
        name = "Kindled Wickerfolk",
        cost = "{R}{G}",
        type = "Artifact Creature — Scarecrow",
        pt = "3/2",
        text = """
            Haste
            Delirium — This creature gets +1/+1 and has trample as long as there are four or more card types among cards in your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        Ability.Static(
            mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(1)), Mechanic.GainAbilities(listOf(Keyword.Trample))),
            affects = Affected.Objects(Objects.Self),
            condition = delirium,
            abilityWord = "Delirium",
        ),
    )

    /** A maximum hand size set by a number (CR 402.2): seven minus the card types counted. */
    @Test
    fun wynneMisanthropicWarden() = card(
        name = "Wynne, Misanthropic Warden",
        cost = "{1}{B}{R}{G}",
        type = "Legendary Creature — Human Warlock",
        pt = "3/4",
        text = """
            Ward {2}
            At the beginning of your upkeep, each player draws two cards.
            Delirium — As long as there are four or more card types among cards in your graveyard, each opponent's maximum hand size is equal to seven minus the number of those card types.
        """,
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{2}")),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)),
            instructions = Instructions(steps = listOf(Step.Do(Effect.Draw(Players.Each(PlayerFilter.Any), Value.Fixed(2))))),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.MaximumHandSize(Value.Difference(Value.Fixed(7), cardTypesInGraveyard))),
            affects = Affected.Players(Players.Each(PlayerFilter.Opponent)),
            condition = delirium,
            abilityWord = "Delirium",
        ),
    )

    @Test
    fun peekPastTheVeil() = card(
        name = "Peek Past the Veil",
        cost = "{2}{R}{G}",
        type = "Instant",
        text = "Discard your hand. Then draw X cards, where X is the number of card types among cards in your graveyard.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.Discard(Players.You, Value.Fixed(1), filter = ObjectFilter.Any, objects = Objects.Each(ObjectFilter.And(listOf(
                ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))))),
            Step.Do(Effect.Draw(Players.You, cardTypesInGraveyard)),
        ))),
    )

    @Test
    fun broodweaver() = card(
        name = "Broodweaver",
        cost = "{B}{G}",
        type = "Creature — Spider",
        pt = "2/3",
        text = """
            Reach
            When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
            {4}{B}{G}, {T}, Sacrifice this creature: Create a number of 1/1 black and green Insect creature tokens with flying equal to the number of card types among cards in your graveyard.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Ability.Triggered(thisEnters, Instructions(steps = listOf(Step.Do(Effect.Surveil(Players.You, Value.Fixed(2)))))),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{4}{B}{G}")!!), CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Self))),
            instructions = Instructions(steps = listOf(Step.Do(Effect.CreateToken(
                token(listOf("Insect"), listOf(Color.Black, Color.Green), 1, 1, abilities = listOf(Keyword.Flying)),
                count = cardTypesInGraveyard,
                controller = Players.You,
            )))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )
}
