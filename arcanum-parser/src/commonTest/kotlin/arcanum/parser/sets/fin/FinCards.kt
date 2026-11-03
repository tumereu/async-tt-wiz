package arcanum.parser.sets.fin

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
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
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone

/*
 * Shared shapes of the Final Fantasy (FIN) parser tests. The test cards are invented (D15): new names, the real cards'
 * exact oracle text with the new name.
 */

// ---- Filters

val creature: ObjectFilter = ObjectFilter.Type(CardType.Creature)
val land: ObjectFilter = ObjectFilter.Type(CardType.Land)
val artifact: ObjectFilter = ObjectFilter.Type(CardType.Artifact)
val enchantment: ObjectFilter = ObjectFilter.Type(CardType.Enchantment)
val instant: ObjectFilter = ObjectFilter.Type(CardType.Instant)
val sorcery: ObjectFilter = ObjectFilter.Type(CardType.Sorcery)
val nonland: ObjectFilter = ObjectFilter.Not(land)
val noncreature: ObjectFilter = ObjectFilter.Not(creature)
val basic: ObjectFilter = ObjectFilter.Super(Supertype.Basic)
val spellFilter: ObjectFilter = ObjectFilter.Category(ObjectCategory.Spell)
val cardFilter: ObjectFilter = ObjectFilter.Category(ObjectCategory.Card)
val token: ObjectFilter = ObjectFilter.Category(ObjectCategory.Token)
val permanentFilter: ObjectFilter = ObjectFilter.Category(ObjectCategory.Permanent)
val you: ObjectFilter = ObjectFilter.ControlledBy(PlayerFilter.You)
val opponents: ObjectFilter = ObjectFilter.ControlledBy(PlayerFilter.Opponent)
val another: ObjectFilter = ObjectFilter.Not(ObjectFilter.Is(Objects.Self))
val attacking: ObjectFilter = ObjectFilter.HasStatus(arcanum.api.Status.Attacking)
val tapped: ObjectFilter = ObjectFilter.HasStatus(arcanum.api.Status.Tapped)
/** All of [filters], nested "and"s flattened (the parser's one canonical form). */
fun and(vararg filters: ObjectFilter): ObjectFilter = ObjectFilter.And(filters.flatMap { if (it is ObjectFilter.And) it.all else listOf(it) })
fun or(vararg filters: ObjectFilter): ObjectFilter = ObjectFilter.Or(filters.toList())
fun not(filter: ObjectFilter): ObjectFilter = ObjectFilter.Not(filter)
fun sub(name: String): ObjectFilter = ObjectFilter.Sub(Subtype(name))
fun kw(kind: arcanum.api.KeywordKind): ObjectFilter = ObjectFilter.HasKeyword(kind)
val creatureYouControl: ObjectFilter = and(creature, you)
val creatureOpponentControls: ObjectFilter = and(creature, opponents)
val nonlandPermanent: ObjectFilter = and(nonland, permanentFilter)
fun inZone(zone: Zone): ObjectFilter = ObjectFilter.InZone(zone)
val yourGraveyard: List<ObjectFilter> = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
fun inYourGraveyard(vararg filters: ObjectFilter): ObjectFilter = ObjectFilter.And(filters.toList() + yourGraveyard)
fun manaValue(comparison: arcanum.api.Comparison, n: Int): ObjectFilter = ObjectFilter.ManaValue(comparison, Value.Fixed(n))
fun power(comparison: arcanum.api.Comparison, n: Int): ObjectFilter = ObjectFilter.Power(comparison, Value.Fixed(n))
// ---- Targets

fun target(name: String): Objects = Objects.Target(name)
/** A target slot of objects [min]..[max] (null: any number). */
fun slot(name: String, filter: ObjectFilter, min: Int = 1, max: Int? = 1) =
    TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(min), max = max?.let { Value.Fixed(it) })
val targetPlayer = TargetSlot("player", TargetKind.Players(PlayerFilter.Any), min = Value.Fixed(1), max = Value.Fixed(1))
val targetOpponent = TargetSlot("opponent", TargetKind.Players(PlayerFilter.Opponent), min = Value.Fixed(1), max = Value.Fixed(1))
val anyTarget = TargetSlot("any", TargetKind.Either(or(creature, ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.Type(CardType.Battle)),
    PlayerFilter.Any), min = Value.Fixed(1), max = Value.Fixed(1))
val toAny = Recipients(target = "any")
fun toObjects(objects: Objects) = Recipients(objects = objects)
val eachOpponent: Recipients = Recipients(players = Players.Each(PlayerFilter.Opponent))

// ---- Abilities

fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList(), announced: List<String> = emptyList()) =
    Ability.Spell(Instructions(targets = targets, steps = steps.toList(), announced = announced))
fun triggered(trigger: Trigger, vararg steps: Step, targets: List<TargetSlot> = emptyList(), interveningIf: Condition? = null, perTurn: Int? = null,
              abilityWord: String? = null, triggersOnlyIf: Condition? = null, actionOncePerTurn: Boolean = false) =
    Ability.Triggered(trigger, Instructions(targets = targets, steps = steps.toList()), interveningIf = interveningIf, perTurn = perTurn,
        abilityWord = abilityWord, triggersOnlyIf = triggersOnlyIf, actionOncePerTurn = actionOncePerTurn)
fun activated(cost: Cost, vararg steps: Step, targets: List<TargetSlot> = emptyList(), rules: ActivationRules = anyTime, label: arcanum.api.AbilityLabel? = null) =
    Ability.Activated(cost, Instructions(targets = targets, steps = steps.toList()), rules, label = label)
fun static(vararg mechanics: Mechanic, affects: Objects = Objects.Self, condition: Condition? = null, characteristicDefining: Boolean = false) =
    Ability.Static(mechanics.toList(), affects = Affected.Objects(affects), condition = condition, characteristicDefining = characteristicDefining)
fun playerStatic(vararg mechanics: Mechanic, players: Players = Players.You, condition: Condition? = null) =
    Ability.Static(mechanics.toList(), affects = Affected.Players(players), condition = condition)
fun modes(vararg options: Mode, min: Int = 1, max: Int = 1) = Modes(options.toList(), min = min, max = max, chooser = Players.You)
fun mode(vararg steps: Step, targets: List<TargetSlot> = emptyList()) = Mode(Instructions(targets = targets, steps = steps.toList()))
fun modal(vararg options: Mode, min: Int = 1, max: Int = 1) = Ability.Spell(Instructions(modes = modes(*options, min = min, max = max)))

val anyTime = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)
val asSorcery = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)
val duringYourTurn = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = Condition.YourTurn)
fun cost(vararg parts: CostPart): Cost = Cost(parts.toList())
fun mana(text: String): CostPart = CostPart.Mana(ManaCost.parse(text)!!)
fun manaCost(text: String): Cost = Cost.mana(ManaCost.parse(text)!!)
val tap: CostPart = CostPart.TapSelf
val sacrificeSelf: CostPart = CostPart.Sacrifice(CostObjects.Self)
fun sacrificeA(filter: ObjectFilter): CostPart = CostPart.Sacrifice(CostObjects.Choose(filter, count = Value.Fixed(1)))

// ---- Triggers

fun on(event: EventPattern): Trigger = Trigger.On(event)
val entersSelf: Trigger = on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield))
val diesSelf: Trigger = on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard))
val attacksSelf: Trigger = on(EventPattern.Attack(ObjectFilter.Is(Objects.Self)))
/** "Whenever this creature enters or attacks" (one ability with two events). */
val entersOrAttacksSelf: Trigger = Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
    EventPattern.Attack(ObjectFilter.Is(Objects.Self))))
/** "Whenever you attack" (CR 508.3d: once per declaration). */
val youAttack: Trigger = Trigger.On(EventPattern.Attack(you), oneOrMore = true)
fun enters(filter: ObjectFilter): Trigger = on(EventPattern.ZoneChange(filter, to = Zone.Battlefield))
fun dies(filter: ObjectFilter): Trigger = on(EventPattern.ZoneChange(filter, from = Zone.Battlefield, to = Zone.Graveyard))
fun stepBegins(step: TurnStep, whose: PlayerFilter): Trigger = on(EventPattern.StepBegins(step, whose))
val beginningOfCombatOnYourTurn: Trigger = stepBegins(TurnStep.BeginningOfCombat, PlayerFilter.You)
val yourEndStep: Trigger = stepBegins(TurnStep.End, PlayerFilter.You)
val yourUpkeep: Trigger = stepBegins(TurnStep.Upkeep, PlayerFilter.You)
/** "Landfall — Whenever a land you control enters" (an ability word, CR 207.2c). */
val landfall: Trigger = enters(and(land, you))
fun youCast(spells: ObjectFilter): Trigger = on(EventPattern.Cast(spells, PlayerFilter.You))
val combatDamageToAPlayer: Trigger = on(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Any), combat = true))
/** "Whenever you draw your second card each turn" */
val drawSecondCard: Trigger = Trigger.On(listOf(EventPattern.Draw(PlayerFilter.You)), nthThisTurn = 2)
/** "Whenever you sacrifice another permanent" (CR 701.21a) */
val youSacrificeAnother: Trigger = on(EventPattern.Sacrifice(and(another, you)))

// ---- Effects

fun n(value: Int): Value = Value.Fixed(value)
val x: Value = Value.Announced(name = "X")
fun doing(effect: Effect): Step = Step.Do(effect)
fun draw(count: Int, players: Players = Players.You): Step = Step.Do(Effect.Draw(players, n(count)))
fun discard(count: Int, players: Players = Players.You): Step = Step.Do(Effect.Discard(players, n(count), ObjectFilter.Any))
fun gainLife(amount: Int, players: Players = Players.You): Step = Step.Do(Effect.GainLife(players, n(amount)))
fun loseLife(amount: Int, players: Players = Players.You): Step = Step.Do(Effect.LoseLife(players, n(amount)))
fun mill(count: Int, players: Players = Players.You, postBind: String? = null): Step = Step.Do(Effect.Mill(players, n(count), postBind = postBind))
fun scry(count: Int, players: Players = Players.You): Step = Step.Do(Effect.Scry(players, n(count)))
fun surveil(count: Int): Step = Step.Do(Effect.Surveil(Players.You, n(count)))
fun damage(amount: Value, to: Recipients, source: Objects = Objects.Self): Step = Step.Do(Effect.DealDamage(to, amount, source))
fun damage(amount: Int, to: Recipients, source: Objects = Objects.Self): Step = damage(n(amount), to, source)
val p1p1: CounterKind = CounterKind.PowerToughness(1, 1)
val quest: CounterKind = CounterKind.Named("quest")
fun counters(objects: Objects, amount: Value, kind: CounterKind = p1p1): Step = Step.Do(Effect.PutCounters(objects, kind, amount))
fun counters(objects: Objects, amount: Int = 1, kind: CounterKind = p1p1): Step = counters(objects, n(amount), kind)
fun untilEndOfTurn(objects: Objects, vararg mechanics: Mechanic): Step =
    Step.Do(Effect.ApplyMechanics(mechanics.toList(), Affected.Objects(objects), Duration.EndOfTurn))
fun pt(p: Int, t: Int): Mechanic = Mechanic.ModifyPowerToughness(n(p), n(t))
fun pump(objects: Objects, p: Int, t: Int, vararg also: Mechanic): Step = untilEndOfTurn(objects, pt(p, t), *also)
fun gains(objects: Objects, vararg keywords: Ability): Step = untilEndOfTurn(objects, Mechanic.GainAbilities(keywords.toList()))
fun has(vararg keywords: Ability): Mechanic = Mechanic.GainAbilities(keywords.toList())
fun move(objects: Objects, zone: Zone, tapped: Boolean = false, position: Int? = null, postBind: String? = null, controller: Players? = null): Step =
    Step.Do(Effect.Move(objects, Destination(zone, libraryPosition = position, tapped = tapped, controller = controller), postBind = postBind))
fun destroy(objects: Objects): Step = Step.Do(Effect.Destroy(objects))
fun tapIt(objects: Objects): Step = Step.Do(Effect.Tap(objects))
fun untapIt(objects: Objects): Step = Step.Do(Effect.Untap(objects))
fun powerOf(of: Objects): Value = Value.Of(NumericProperty.Power, of, Combine.Single)
fun count(filter: ObjectFilter): Value = Value.Count(Objects.Each(filter))
fun search(filter: ObjectFilter, max: Value = n(1), reveal: Boolean = false, bind: String = "found"): Step =
    Step.Do(Effect.Search(Players.You, filter, max = max, reveal = reveal, zones = listOf(Zone.Library), bind = bind, searcher = Players.You))
val shuffle: Step = Step.Do(Effect.Shuffle(Players.You))
val basicLand: ObjectFilter = and(basic, land)
/** "search your library for a basic land card, put it onto the battlefield tapped, then shuffle" */
val rampTapped: List<Step> = listOf(search(basicLand), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle)
fun reflexive(vararg steps: Step, targets: List<TargetSlot> = emptyList()): Step =
    Step.Do(Effect.ReflexiveTrigger(Instructions(targets = targets, steps = steps.toList())))
fun delayed(trigger: Trigger, vararg steps: Step): Step = Step.Do(Effect.CreateDelayedTrigger(trigger, Instructions(steps = steps.toList())))
val kicked: Condition = Condition.Paid(Keyword.Kicker.Kind, times = 1, of = Objects.Self)
/** "If this spell's additional cost was paid" (CR 601.2b) */
val additionalPaid: Condition = Condition.Paid(null, times = 1, of = Objects.Self)
/** "This land enters tapped." */
val entersTapped: Ability = Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
    modify = listOf(arcanum.api.Modification.EntersTapped))), affects = Affected.Objects(Objects.Self))
/** "{T}: Add {A} or {B}." */
fun addEither(a: String, b: String): Ability = activated(cost(tap), Step.Do(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Choose(n(1),
    colors = listOf(Color.of(a[0])!!, Color.of(b[0])!!).map { arcanum.api.ColorRef.Literal(it) })), Players.You)))
/** "{T}: Add one mana of any color." */
val addAnyColor: Ability = activated(cost(tap), Step.Do(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Choose(n(1))), Players.You)))
/** "{T}: Add {C}." */
val addColorless: Ability = activated(cost(tap), Step.Do(Effect.AddMana(arcanum.api.ManaProduction(arcanum.api.ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You)))
/** A Saga's chapter ability (CR 714.2b). */
fun chapter(number: Int, vararg steps: Step, targets: List<TargetSlot> = emptyList()) =
    Ability.Triggered(Trigger.Chapter(listOf(number)), Instructions(targets = targets, steps = steps.toList()))
fun chapters(numbers: List<Int>, vararg steps: Step, targets: List<TargetSlot> = emptyList()) =
    Ability.Triggered(Trigger.Chapter(numbers), Instructions(targets = targets, steps = steps.toList()))
/** "III — Exile this Saga, then return it to the battlefield transformed under your control." (CR 714.2b, 701.27) */
val exileAndReturnTransformed: Ability = chapter(3, move(Objects.Self, Zone.Exile, postBind = "exiled"),
    Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, controller = Players.You, transformed = true))))
val enteredOne: Objects = Objects.EventObject(EventRole.After)
val leftOne: Objects = Objects.EventObject(EventRole.Before)

// ---- Tokens

fun creatureToken(subtypes: String, colors: List<Color>, power: Int, toughness: Int, abilities: List<Ability> = emptyList(),
                  types: List<CardType> = listOf(CardType.Creature), name: String = "$subtypes Token") =
    TokenSpec.Defined(FaceDefinition(name, colorIndicator = colors, types = types, subtypes = subtypes.split(' ').map(::Subtype),
        power = PrintedNumber.Fixed(power), toughness = PrintedNumber.Fixed(toughness), abilities = abilities))
fun create(token: TokenSpec, count: Value = n(1), bind: String? = null, tapped: Boolean = false, attacking: arcanum.api.AttackTarget? = null): Step =
    Step.Do(Effect.CreateToken(token, count, Players.You, tapped = tapped, attacking = attacking, bind = bind))
/** 'a 1/1 colorless Spirit creature token with "This token can't block or be blocked by non-Spirit creatures."' */
val spiritToken: TokenSpec = creatureToken("Spirit", emptyList(), 1, 1, abilities = listOf(Ability.Static(listOf(
    Mechanic.CantBlock(and(not(sub("Spirit")), creature)), Mechanic.CantBeBlockedBy(and(not(sub("Spirit")), creature))), affects = Affected.Objects(Objects.Self))))
val clue: TokenSpec = TokenSpec.Predefined("Clue")
val food: TokenSpec = TokenSpec.Predefined("Food")
val createClue: Step = create(clue)
val createFood: Step = create(food)

// ---- Final Fantasy shapes

val equipment: ObjectFilter = sub("Equipment")
val town: ObjectFilter = sub("Town")
val saga: ObjectFilter = sub("Saga")
val legendary: ObjectFilter = ObjectFilter.Super(Supertype.Legendary)
val equipped: Objects = Objects.AttachedTo(Objects.Self)
val stun: CounterKind = CounterKind.Stun
val lore: CounterKind = CounterKind.Lore
val finality: CounterKind = CounterKind.Finality
val indestructibleCounter: CounterKind = CounterKind.Keyword(Keyword.Indestructible)
/** "permanent card" (CR 110.4b) */
val permanentCard: ObjectFilter = or(*listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Land,
    CardType.Planeswalker).map { ObjectFilter.Type(it) }.toTypedArray())
fun power(of: Objects): Value = Value.Of(NumericProperty.Power, of, Combine.Single)
fun toughness(of: Objects): Value = Value.Of(NumericProperty.Toughness, of, Combine.Single)
/** A tiered mode: its cost is an additional cost of the spell (CR 702.183a). */
fun tier(costText: String, vararg steps: Step, targets: List<TargetSlot> = emptyList()) =
    Mode(Instructions(targets = targets, steps = steps.toList()), extraCost = manaCost(costText))
fun tiered(vararg options: Mode) = Ability.Spell(Instructions(modes = Modes(options.toList(), min = 1, max = 1, chooser = Players.You)))
fun knightToken(): TokenSpec = creatureToken("Knight", listOf(Color.White), 2, 2)
/** "a 1/1 colorless Hero creature token" */
val heroToken: TokenSpec = creatureToken("Hero", emptyList(), 1, 1)
/** 'a 2/2 green Bird creature token with "Whenever a land you control enters, this token gets +1/+0 until end of turn."' */
val birdToken: TokenSpec = creatureToken("Bird", listOf(Color.Green), 2, 2, abilities = listOf(triggered(landfall, pump(Objects.Self, 1, 0))))
/** 'a 0/1 black Wizard creature token with "Whenever you cast a noncreature spell, this token deals 1 damage to each opponent."' */
val wizardToken: TokenSpec = creatureToken("Wizard", listOf(Color.Black), 0, 1, abilities = listOf(triggered(youCast(noncreature), damage(1, eachOpponent))))
val treasure: TokenSpec = TokenSpec.Predefined("Treasure")
val createTreasure: Step = create(treasure)
/** "is a [subtype] in addition to its other types" (layer 4) */
fun alsoA(vararg subtypes: String): Mechanic = Mechanic.SetTypes(addSubtypes = subtypes.map { arcanum.api.SubtypeRef.Literal(Subtype(it)) })
/** "if at least [n] mana was spent to cast it" in a cast trigger: the spell cast (CR 601.2h) */
val thatSpell: Objects = Objects.EventObject(EventRole.Object)
fun manaSpentAtLeast(n: Int): Condition = Condition.Compare(Value.ManaSpent(of = thatSpell), arcanum.api.Comparison.AtLeast, n(n))
fun ifThen(condition: Condition, vararg then: Step, otherwise: List<Step> = emptyList()): Step = Step.If(condition, then.toList(), otherwise)
/** "Exile [this], then return it to the battlefield (front face up)." (a back face's Saga chapter, CR 712.14) */
fun exileAndReturnFrontFaceUp(): List<Step> = listOf(move(Objects.Self, Zone.Exile, postBind = "exiled"),
    Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield))))
