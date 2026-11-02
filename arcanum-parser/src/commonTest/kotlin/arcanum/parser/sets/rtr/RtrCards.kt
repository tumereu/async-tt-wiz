package arcanum.parser.sets.rtr

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone

/**
 * Shared shapes of the Return to Ravnica (rtr) parser tests. The test cards are invented (D15): new names, the real
 * cards' exact oracle text with the new name.
 */
internal object Rtr {
    val creature: ObjectFilter = ObjectFilter.Type(CardType.Creature)
    val land: ObjectFilter = ObjectFilter.Type(CardType.Land)
    val artifact: ObjectFilter = ObjectFilter.Type(CardType.Artifact)
    val enchantment: ObjectFilter = ObjectFilter.Type(CardType.Enchantment)
    val planeswalker: ObjectFilter = ObjectFilter.Type(CardType.Planeswalker)
    val instant: ObjectFilter = ObjectFilter.Type(CardType.Instant)
    val sorcery: ObjectFilter = ObjectFilter.Type(CardType.Sorcery)
    val permanent: ObjectFilter = ObjectFilter.Category(ObjectCategory.Permanent)
    val card: ObjectFilter = ObjectFilter.Category(ObjectCategory.Card)
    val spell: ObjectFilter = ObjectFilter.Category(ObjectCategory.Spell)
    val token: ObjectFilter = ObjectFilter.Category(ObjectCategory.Token)
    val nonland: ObjectFilter = ObjectFilter.Not(land)
    val nonlandPermanent: ObjectFilter = all(nonland, permanent)
    val you: ObjectFilter = ObjectFilter.ControlledBy(PlayerFilter.You)
    val opponents: ObjectFilter = ObjectFilter.ControlledBy(PlayerFilter.Opponent)
    val notYou: ObjectFilter = ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))
    val another: ObjectFilter = ObjectFilter.Not(ObjectFilter.Is(Objects.Self))
    val p1p1: CounterKind = CounterKind.PowerToughness(1, 1)
    val enchanted: Objects = Objects.AttachedTo(Objects.Self)

    fun all(vararg filters: ObjectFilter): ObjectFilter = ObjectFilter.And(filters.toList())
    fun any(vararg filters: ObjectFilter): ObjectFilter = ObjectFilter.Or(filters.toList())
    fun n(value: Int): Value = Value.Fixed(value)
    fun sub(name: String): ObjectFilter = ObjectFilter.Sub(Subtype(name))
    fun mana(text: String): ManaCost = ManaCost.parse(text)!!
    fun cost(vararg parts: CostPart): Cost = Cost(parts.toList())
    fun manaPart(text: String): CostPart = CostPart.Mana(mana(text))

    /** "target [filter]" named [name]: [min] to [max] of them. */
    fun slot(name: String, filter: ObjectFilter, min: Int = 1, max: Int = 1) =
        TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(min), max = Value.Fixed(max))

    /** "target player" */
    val targetPlayer = TargetSlot("player", TargetKind.Players(PlayerFilter.Any), min = Value.Fixed(1), max = Value.Fixed(1))

    /** "target opponent" */
    val targetOpponent = TargetSlot("opponent", TargetKind.Players(PlayerFilter.Opponent), min = Value.Fixed(1), max = Value.Fixed(1))

    /** "any target" (CR 115.4) */
    val anyTarget = TargetSlot("any", TargetKind.Either(ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Creature),
        ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.Type(CardType.Battle))), PlayerFilter.Any), min = Value.Fixed(1), max = Value.Fixed(1))

    fun target(slot: String): Objects = Objects.Target(slot)

    // ---- Abilities

    fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList()) = Ability.Spell(Instructions(targets = targets, steps = steps.toList()))

    fun triggered(trigger: Trigger, vararg steps: Step, targets: List<TargetSlot> = emptyList(), interveningIf: Condition? = null) =
        Ability.Triggered(trigger, Instructions(targets = targets, steps = steps.toList()), interveningIf = interveningIf)

    fun activated(cost: Cost, vararg steps: Step, targets: List<TargetSlot> = emptyList(), timing: Timing = Timing.Any) =
        Ability.Activated(cost, Instructions(targets = targets, steps = steps.toList()), ActivationRules(timing = timing, activatedBy = PlayerFilter.You))

    fun static(objects: Objects, vararg mechanics: Mechanic, condition: Condition? = null) =
        Ability.Static(mechanics.toList(), affects = Affected.Objects(objects), condition = condition)

    fun enchant(filter: ObjectFilter) = Keyword.Enchant(TargetKind.Objects(filter))

    // ---- Triggers

    val thisEnters = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield))
    val thisDies = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard))
    val thisLeaves = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield))
    val thisAttacks = Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self)))
    val yourUpkeep = Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You))
    fun step(step: TurnStep, whose: PlayerFilter) = Trigger.On(EventPattern.StepBegins(step, whose))

    /** "Whenever [source] deals combat damage to a player" */
    fun combatDamageToAPlayer(source: ObjectFilter = ObjectFilter.Is(Objects.Self)) =
        Trigger.On(EventPattern.Damage(source, RecipientFilter(players = PlayerFilter.Any), combat = true))

    /** "Whenever you cast a [filter] spell" */
    fun youCast(filter: ObjectFilter) = Trigger.On(EventPattern.Cast(filter, PlayerFilter.You))

    // ---- Steps

    fun step(effect: Effect): Step = Step.Do(effect)

    /** "Detain [slot]" (CR 701.35a) */
    fun detain(slot: String): Step = Step.Do(Effect.Action(KeywordAction.Detain(Objects.Target(slot))))

    /** "Populate" (CR 701.36a) */
    val populate: Step = Step.Do(Effect.Action(KeywordAction.Populate))

    /** "Regenerate [objects]" (CR 701.19a) */
    fun regenerate(objects: Objects): Step = Step.Do(Effect.Action(KeywordAction.Regenerate(objects)))

    /** "[objects] get(s) +[power]/+[toughness] [and gains …] until end of turn" (layers 6, 7c). */
    fun pump(objects: Objects, power: Value, toughness: Value, vararg more: Mechanic): Step =
        Step.Do(Effect.ApplyMechanics(listOf<Mechanic>(Mechanic.ModifyPowerToughness(power, toughness)) + more, Affected.Objects(objects), Duration.EndOfTurn))

    fun pump(objects: Objects, power: Int, toughness: Int, vararg more: Mechanic): Step = pump(objects, n(power), n(toughness), *more)

    /** "[objects] gain(s) [keywords] until end of turn" */
    fun gains(objects: Objects, vararg keywords: Ability): Step =
        Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(keywords.toList())), Affected.Objects(objects), Duration.EndOfTurn))

    fun gain(vararg keywords: Ability): Mechanic = Mechanic.GainAbilities(keywords.toList())

    fun draw(count: Int, players: Players = Players.You): Step = Step.Do(Effect.Draw(players, Value.Fixed(count)))
    fun gainLife(amount: Value, players: Players = Players.You): Step = Step.Do(Effect.GainLife(players, amount))
    fun gainLife(amount: Int, players: Players = Players.You): Step = gainLife(Value.Fixed(amount), players)
    fun loseLife(amount: Value, players: Players): Step = Step.Do(Effect.LoseLife(players, amount))
    fun damage(to: Recipients, amount: Value, source: Objects = Objects.Self): Step = Step.Do(Effect.DealDamage(to, amount, source))
    fun damage(to: Recipients, amount: Int, source: Objects = Objects.Self): Step = damage(to, Value.Fixed(amount), source)
    fun destroy(objects: Objects): Step = Step.Do(Effect.Destroy(objects))
    fun counters(objects: Objects, amount: Value, kind: CounterKind = p1p1): Step = Step.Do(Effect.PutCounters(objects, kind, amount))
    fun counters(objects: Objects, amount: Int = 1, kind: CounterKind = p1p1): Step = counters(objects, Value.Fixed(amount), kind)
    fun move(objects: Objects, zone: Zone): Step = Step.Do(Effect.Move(objects, arcanum.api.Destination(zone)))

    /** A creature token ("a 3/3 green Centaur creature token", CR 111.4). */
    fun creatureToken(subtype: String, colors: List<Color>, power: Int, toughness: Int, vararg abilities: Ability) =
        TokenSpec.Defined(FaceDefinition(
            name = "$subtype Token",
            colorIndicator = colors,
            types = listOf(CardType.Creature),
            subtypes = listOf(Subtype(subtype)),
            power = PrintedNumber.Fixed(power),
            toughness = PrintedNumber.Fixed(toughness),
            abilities = abilities.toList(),
        ))

    fun create(token: TokenSpec, count: Int = 1): Step = Step.Do(Effect.CreateToken(token, count = Value.Fixed(count), controller = Players.You))

    /** The two reminder-text-only keywords of a set: overload with its "each" version (CR 702.96b). */
    fun overload(cost: String, vararg steps: Step) = Keyword.Overload(Cost.mana(mana(cost)), Instructions(steps = steps.toList()))

    fun scavenge(cost: String) = Keyword.Scavenge(Cost.mana(mana(cost)))

    /** Players.EventPlayer etc. kept short */
    val eventPlayer: Players = Players.EventPlayer
    val eventObject: Objects = Objects.EventObject(EventRole.Object)
}
