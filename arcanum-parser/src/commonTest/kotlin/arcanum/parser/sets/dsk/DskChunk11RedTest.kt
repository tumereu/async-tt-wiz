package arcanum.parser.sets.dsk

import arcanum.api.DamageOccurrence
import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
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
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.thisEnters
import arcanum.parser.sets.dsk.Dsk.creature
import arcanum.parser.sets.dsk.Dsk.creatureYouControl
import arcanum.parser.sets.dsk.Dsk.enchantment
import kotlin.test.Test

/**
 * Duskmourn chunk 11: the red cards. "If that creature would die this turn, exile it instead" (a replacement for the
 * turn, CR 614.1a); counters named on an Equipment for its bonus; a delayed trigger on your next instant or sorcery
 * (CR 603.7); "must be blocked if able" (CR 509.1c); "the discarded card"; "do this only once each turn" (CR 603.2h);
 * damage to a creature's controller after a draw; damage dealt to it redirected as damage to another target; two targets
 * with one controller and that player's choice; changing a single target (CR 115.7); a turn's worth of a dies trigger.
 */
class DskChunk11RedTest {
    private fun slot(name: String, filter: ObjectFilter, min: Int = 1, max: Int = 1) =
        TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(min), max = Value.Fixed(max))
    private fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList()) = Ability.Spell(Instructions(targets = targets, steps = steps.toList()))
    private fun untilEndOfTurn(objects: Objects, vararg mechanics: Mechanic) =
        Step.Do(Effect.ApplyMechanics(mechanics.toList(), Affected.Objects(objects), Duration.EndOfTurn))
    private val creatureOrPlaneswalker = ObjectFilter.Or(listOf(creature, ObjectFilter.Type(CardType.Planeswalker)))
    private val instantOrSorcerySpell = ObjectFilter.And(listOf(
        ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
        ObjectFilter.Category(ObjectCategory.Spell),
    ))
    /** "If that creature would die this turn, exile it instead." */
    private fun exileIfItWouldDie(slot: String) = Step.Do(Effect.ApplyMechanics(
        listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Target(slot)), from = Zone.Battlefield, to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
        affects = null, duration = Duration.EndOfTurn,
    ))
    private fun mayPlayExiled(duration: Duration) = Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
        Affected.Objects(Objects.Bound("exiled")), duration))

    /** An additional cost of two options, one with an "or" of its own (CR 601.2f). */
    @Test
    fun traitorsBargain() = card(
        name = "Traitor's Bargain",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, sacrifice a creature or enchantment or pay {2}.
            Traitor's Bargain deals 5 damage to target creature. If that creature would die this turn, exile it instead.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.OneOf(listOf(
                Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Or(listOf(creature, enchantment)), count = Value.Fixed(1))))),
                manaCost("{2}"),
            )))))),
            affects = Affected.Objects(Objects.Self),
        ),
        spell(
            Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), Value.Fixed(5), source = Objects.Self)),
            exileIfItWouldDie("creature"),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun buzzsaw() = card(
        name = "Buzzsaw",
        cost = "{1}{R}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, it deals 3 damage to up to one target creature.
            Whenever one or more creatures die, put a rev counter on this Equipment.
            Equipped creature gets +X/+0, where X is the number of rev counters on this Equipment.
            Equip {3}
        """,
    ) parsesTo abilities(
        Ability.Triggered(thisEnters, Instructions(
            targets = listOf(slot("creature", creature, min = 0)),
            steps = listOf(Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), Value.Fixed(3), source = Objects.Self))),
        )),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), oneOrMore = true),
            instructions = Instructions(steps = listOf(Step.Do(Effect.PutCounters(Objects.Self, CounterKind.Named("rev"), Value.Fixed(1))))),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Of(NumericProperty.Counters(CounterKind.Named("rev")), Objects.Self, Combine.Single), Value.Fixed(0))),
            affects = Affected.Objects(Objects.AttachedTo(Objects.Self)),
        ),
        Keyword.Equip(manaCost("{3}")),
    )

    /** "Remove those counters": all its time counters; "when you next cast …": a delayed trigger, once, this turn (CR 603.7b). */
    @Test
    fun hexedRecording() = card(
        name = "Hexed Recording",
        cost = "{2}{R}{R}",
        type = "Artifact",
        text = """
            Whenever you cast an instant or sorcery spell, put a time counter on this artifact. Then if there are seven or more time counters on it, remove those counters and it deals 20 damage to you.
            {T}: When you next cast an instant or sorcery spell this turn, copy that spell. You may choose new targets for the copy.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Cast(ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))), by = PlayerFilter.You)),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.PutCounters(Objects.Self, CounterKind.Time, Value.Fixed(1))),
                Step.If(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Time, atLeast = 7)), then = listOf(
                    Step.Do(Effect.RemoveCounters(Objects.Self, CounterKind.Time, Value.Of(NumericProperty.Counters(CounterKind.Time), Objects.Self, Combine.Single))),
                    Step.Do(Effect.DealDamage(Recipients(players = Players.You), Value.Fixed(20), source = Objects.Self)),
                )),
            )),
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = Instructions(steps = listOf(Step.Do(Effect.CreateDelayedTrigger(
                trigger = Trigger.On(EventPattern.Cast(instantOrSorcerySpell, by = PlayerFilter.You)),
                instructions = Instructions(steps = listOf(Step.Do(Effect.CopySpell(Objects.EventObject(EventRole.Object), controller = Players.You, newTargets = true)))),
                duration = Duration.EndOfTurn,
            )))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )

    @Test
    fun dreadOfBeingHunted() = card(
        name = "Dread of Being Hunted",
        cost = "{1}{R}{R}",
        type = "Enchantment Creature — Nightmare",
        pt = "4/2",
        text = """
            Haste
            This creature must be blocked if able.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        Ability.Static(listOf(Mechanic.MustBeBlocked), affects = Affected.Objects(Objects.Self)),
    )

    /** "The discarded card" of the additional cost (its last known information, CR 608.2h). */
    @Test
    fun snatchThePrize() = card(
        name = "Snatch the Prize",
        cost = "{1}{R}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, discard a card.
            Draw two cards. If the discarded card wasn't a land card, Snatch the Prize deals 2 damage to each opponent.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card),
                count = Value.Fixed(1)), preBind = "discarded"))))),
            affects = Affected.Objects(Objects.Self),
        ),
        spell(
            Step.Do(Effect.Draw(Players.You, Value.Fixed(2))),
            Step.If(Condition.Not(Condition.ObjectMatches(Objects.Bound("discarded"), ObjectFilter.Type(CardType.Land))), then = listOf(
                Step.Do(Effect.DealDamage(Recipients(players = Players.Each(PlayerFilter.Opponent)), Value.Fixed(2), source = Objects.Self)),
            )),
        ),
    )

    /** "Do this only once each turn": it triggers only if you haven't discarded this way this turn (CR 603.2h). */
    @Test
    fun impiousGremlin() = card(
        name = "Impious Gremlin",
        cost = "{1}{R}",
        type = "Creature — Gremlin",
        pt = "2/2",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Whenever another creature you control with power 2 or less enters, you may discard a card. If you do, draw a card. Do this only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature,
                ObjectFilter.ControlledBy(PlayerFilter.You), ObjectFilter.Power(Comparison.AtMost, Value.Fixed(2)))), to = Zone.Battlefield)),
            instructions = Instructions(steps = listOf(
                Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card), count = Value.Fixed(1))))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(1))))),
            )),
            actionOncePerTurn = true,
        ),
    )

    /** "You may exile it. You may play that card from exile this turn." */
    @Test
    fun nolanSwiftEscapist() = card(
        name = "Nolan, Swift Escapist",
        cost = "{R}",
        type = "Legendary Creature — Human Coward",
        pt = "2/1",
        text = """
            Nolan can't block.
            Whenever a creature you control becomes blocked, you may exile it. You may play that card from exile this turn.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantBlock(ObjectFilter.Any)), affects = Affected.Objects(Objects.Self)),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.BecomesBlocked(creatureYouControl, ObjectFilter.Any)),
            instructions = Instructions(steps = listOf(
                Step.May(listOf(Step.Do(Effect.Move(Objects.EventObject(EventRole.Object), Destination(Zone.Exile), postBind = "exiled"))), player = Players.You),
                mayPlayExiled(Duration.EndOfTurn),
            )),
        ),
    )

    @Test
    fun tatteredPlaymate() = card(
        name = "Tattered Playmate",
        cost = "{1}{R}",
        type = "Artifact Creature — Toy",
        pt = "2/2",
        text = "{1}, {T}: Target creature with power 2 or less can't be blocked this turn.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{1}")!!), CostPart.TapSelf)),
            instructions = Instructions(
                targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, ObjectFilter.Power(Comparison.AtMost, Value.Fixed(2)))))),
                steps = listOf(untilEndOfTurn(Objects.Target("creature"), Mechanic.CantBeBlocked)),
            ),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )

    @Test
    fun razorkinPinhead() = card(
        name = "Razorkin Pinhead",
        cost = "{R}{R}",
        type = "Creature — Human Assassin",
        pt = "2/2",
        text = """
            This creature has first strike during your turn.
            Whenever an opponent draws a card, this creature deals 1 damage to them.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.FirstStrike))), affects = Affected.Objects(Objects.Self), condition = Condition.YourTurn),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Draw(PlayerFilter.Opponent)),
            instructions = Instructions(steps = listOf(Step.Do(Effect.DealDamage(Recipients(players = Players.EventPlayer), Value.Fixed(1), source = Objects.Self)))),
        ),
    )

    @Test
    fun searingDragonfire() = card(
        name = "Searing Dragonfire",
        cost = "{1}{R}",
        type = "Instant",
        text = "Searing Dragonfire deals 3 damage to target creature or planeswalker. If that creature or planeswalker would die this turn, exile it instead.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("permanent")), Value.Fixed(3), source = Objects.Self)),
            exileIfItWouldDie("permanent"),
            targets = listOf(slot("permanent", creatureOrPlaneswalker)),
        ),
    )

    /** "That much damage" to any other target; "can't gain life for the rest of the game" for a player dealt damage this way
     * (an effect with no end). */
    @Test
    fun shriekingNemesis() = card(
        name = "Shrieking Nemesis",
        cost = "{2}{R}",
        type = "Creature — Spirit",
        pt = "3/3",
        text = """
            Haste
            Whenever this creature is dealt damage, it deals that much damage to any other target. If a player is dealt damage this way, they can't gain life for the rest of the game.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)), per = DamageOccurrence.Recipient)),
            instructions = Instructions(
                targets = listOf(TargetSlot("any", TargetKind.Either(
                    ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
                        ObjectFilter.Or(listOf(creature, ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.Type(CardType.Battle))))),
                    PlayerFilter.Any), min = Value.Fixed(1), max = Value.Fixed(1))),
                steps = listOf(
                    Step.Do(Effect.DealDamage(Recipients(target = "any"), Value.EventAmount, source = Objects.Self, bind = "dealt")),
                    Step.If(Condition.Compare(Value.Bound("dealt"), Comparison.AtLeast, Value.Fixed(1)), then = listOf(
                        Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantGainLife), Affected.Players(Players.Target("any")), Duration.Indefinitely)),
                    )),
                ),
            ),
        ),
    )

    /** Two targets controlled by the same opponent; that player chooses one (CR 608.2d); "the other" can't block. */
    @Test
    fun ordealOfAgony() = card(
        name = "Ordeal of Agony",
        cost = "{R}",
        type = "Sorcery",
        text = "Choose two target creatures controlled by the same opponent. That player chooses one of those creatures. Ordeal of Agony deals 5 damage to that creature, and the other can't block this turn.",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Objects(ObjectFilter.Any, Value.Fixed(1), Value.Fixed(1), from = Objects.Target("creatures")), bind = "chosen",
                chooser = Players.ControllerOf(Objects.Target("creatures"))),
            Step.Do(Effect.DealDamage(Recipients(objects = Objects.Bound("chosen")), Value.Fixed(5), source = Objects.Self)),
            untilEndOfTurn(Objects.Except(Objects.Target("creatures"), Objects.Bound("chosen")), Mechanic.CantBlock(ObjectFilter.Any)),
            targets = listOf(TargetSlot("creatures", TargetKind.Objects(ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Opponent)))),
                min = Value.Fixed(2), max = Value.Fixed(2), sameController = true)),
        ),
    )

    /** "Change the target of target spell or ability with a single target" (CR 115.7a). */
    @Test
    fun untimelyBreakdown() = card(
        name = "Untimely Breakdown",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Destroy target artifact.
            • Change the target of target spell or ability with a single target.
            • One or two target creatures can't block this turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(
            options = listOf(
                Mode(Instructions(targets = listOf(slot("artifact", ObjectFilter.Type(CardType.Artifact))),
                    steps = listOf(Step.Do(Effect.Destroy(Objects.Target("artifact")))))),
                Mode(Instructions(
                    targets = listOf(slot("spell", ObjectFilter.And(listOf(
                        ObjectFilter.Or(listOf(ObjectFilter.Category(ObjectCategory.Spell), ObjectFilter.Category(ObjectCategory.StackAbility))),
                        ObjectFilter.SingleTarget(),
                    )))),
                    steps = listOf(Step.Do(Effect.ChangeTargets(Objects.Target("spell"), chooser = Players.You, optional = false))),
                )),
                Mode(Instructions(
                    targets = listOf(slot("creatures", creature, max = 2)),
                    steps = listOf(untilEndOfTurn(Objects.Target("creatures"), Mechanic.CantBlock(ObjectFilter.Any))),
                )),
            ),
            min = 1,
            max = 1,
            chooser = Players.You,
        ))),
    )

    /** Control until end of turn (layer 2); "untap it", "it gains haste"; "you may discard a card. If you do" (CR 118.12). */
    @Test
    fun vengefulSeizure() = card(
        name = "Vengeful Seizure",
        cost = "{2}{R}",
        type = "Sorcery",
        text = "Gain control of target creature until end of turn. Untap it. It gains haste until end of turn. You may discard a card. If you do, draw a card.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(Objects.Target("creature")), Duration.EndOfTurn)),
            Step.Do(Effect.Untap(Objects.Target("creature"))),
            untilEndOfTurn(Objects.Target("creature"), Mechanic.GainAbilities(listOf(Keyword.Haste))),
            Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card), count = Value.Fixed(1))))), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(1))))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    /** The target deals the damage (CR 120.1); a delayed trigger for the turn that triggers each time (CR 603.7b). */
    @Test
    fun waltzOfFury() = card(
        name = "Waltz of Fury",
        cost = "{3}{R}{R}",
        type = "Sorcery",
        text = "Target creature you control deals damage equal to its power to each other creature. Until end of turn, whenever a creature you control dies, exile the top card of your library. You may play it until the end of your next turn.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.DealDamage(
                Recipients(objects = Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.Not(ObjectFilter.Is(Objects.Target("creature"))))))),
                Value.Of(NumericProperty.Power, Objects.Target("creature"), Combine.Single),
                source = Objects.Target("creature"),
            )),
            Step.Do(Effect.CreateDelayedTrigger(
                trigger = Trigger.On(EventPattern.ZoneChange(creatureYouControl, from = Zone.Battlefield, to = Zone.Graveyard)),
                instructions = Instructions(steps = listOf(
                    Step.Do(Effect.Move(Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true), Destination(Zone.Exile), postBind = "exiled")),
                    mayPlayExiled(Duration.UntilEndOfYourNextTurn),
                )),
                duration = Duration.EndOfTurn,
                repeats = true,
            )),
            targets = listOf(slot("creature", creatureYouControl)),
        ),
    )

    @Test
    fun sleepyheadBeastie() = card(
        name = "Sleepyhead Beastie",
        cost = "{4}{R}{R}",
        type = "Creature — Beast",
        pt = "5/6",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Mountaincycling {2} ({2}, Discard this card: Search your library for a Mountain card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Keyword.Typecycling(ObjectFilter.Sub(Subtype("Mountain")), "Mountaincycling", manaCost("{2}")),
    )
}
