package arcanum.parser.sets.dft

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
 * Shared shapes of the Aetherdrift (DFT) parser tests. The test cards are invented (D15): new names, the real cards'
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
/** "permanent card": a card of a permanent type (CR 110.4b). */
val permanentCard: ObjectFilter = or(artifact, ObjectFilter.Type(CardType.Battle), creature, enchantment, land, ObjectFilter.Type(CardType.Planeswalker))
fun inZone(zone: Zone): ObjectFilter = ObjectFilter.InZone(zone)
val yourGraveyard: List<ObjectFilter> = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
fun inYourGraveyard(vararg filters: ObjectFilter): ObjectFilter = ObjectFilter.And(filters.toList() + yourGraveyard)
fun manaValue(comparison: arcanum.api.Comparison, n: Int): ObjectFilter = ObjectFilter.ManaValue(comparison, Value.Fixed(n))
fun power(comparison: arcanum.api.Comparison, n: Int): ObjectFilter = ObjectFilter.Power(comparison, Value.Fixed(n))
val vehicle: ObjectFilter = sub("Vehicle")
val mount: ObjectFilter = sub("Mount")
val pilot: ObjectFilter = sub("Pilot")
val planeswalker: ObjectFilter = ObjectFilter.Type(CardType.Planeswalker)
/** "creature or Vehicle" */
val creatureOrVehicle: ObjectFilter = or(creature, vehicle)
/** "Mount or Vehicle" */
val mountOrVehicle: ObjectFilter = or(mount, vehicle)
val isSelf: ObjectFilter = ObjectFilter.Is(Objects.Self)

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
val treasure: TokenSpec = TokenSpec.Predefined("Treasure")
val createTreasure: Step = create(treasure)
/** "a 1/1 colorless Thopter artifact creature token with flying" */
val thopter: TokenSpec = creatureToken("Thopter", emptyList(), 1, 1, abilities = listOf(Keyword.Flying), types = listOf(CardType.Artifact, CardType.Creature))
/** "a 2/2 black Zombie creature token" */
val zombie: TokenSpec = creatureToken("Zombie", listOf(Color.Black), 2, 2)
/** "a 3/3 green Elephant creature token" */
val elephant: TokenSpec = creatureToken("Elephant", listOf(Color.Green), 3, 3)
/** "a 1/1 colorless Servo artifact creature token" */
val servo: TokenSpec = creatureToken("Servo", emptyList(), 1, 1, types = listOf(CardType.Artifact, CardType.Creature))
/** "a 3/2 colorless Vehicle artifact token with crew 1" (CR 301.7: an artifact, its P/T printed for when it's a creature) */
val vehicleToken: TokenSpec = TokenSpec.Defined(FaceDefinition("Vehicle Token", colorIndicator = emptyList(), types = listOf(CardType.Artifact),
    subtypes = listOf(Subtype("Vehicle")), power = PrintedNumber.Fixed(3), toughness = PrintedNumber.Fixed(2), abilities = listOf(Keyword.Crew(1))))

// ---- Aetherdrift mechanics

/** A max speed ability (CR 702.178a): the ability it grants, labelled. */
fun maxSpeed(ability: Ability): Ability = when (ability) {
    is Ability.Static -> ability.copy(label = arcanum.api.AbilityLabel.MaxSpeed)
    is Ability.Activated -> ability.copy(label = arcanum.api.AbilityLabel.MaxSpeed)
    is Ability.Triggered -> ability.copy(label = arcanum.api.AbilityLabel.MaxSpeed)
    else -> error("not a max speed ability: $ability")
}
/** An exhaust ability (CR 702.177a). */
fun exhaust(cost: Cost, vararg steps: Step, targets: List<TargetSlot> = emptyList()) =
    activated(cost, *steps, targets = targets, label = arcanum.api.AbilityLabel.Exhaust)
/** "Cycling [cost]" (CR 702.29a). */
fun cycling(text: String): Keyword = Keyword.Cycling(manaCost(text))
/** "When you cycle this card" (CR 702.29c: from whatever zone the card is in). */
val youCycleThis: Trigger = on(EventPattern.Cycle(isSelf, PlayerFilter.You))
/** "[It] becomes an artifact creature until end of turn" (CR 301.7b). */
fun becomesArtifactCreature(objects: Objects): Step = untilEndOfTurn(objects, Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature)))
/** "This Vehicle becomes an artifact creature." with no duration: for as long as it stays on the battlefield (CR 611.2a). */
val becomesArtifactCreatureForGood: Step = Step.Do(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature))),
    Affected.Objects(Objects.Self), Duration.Indefinitely))
/** "Whenever you activate an exhaust ability" (mana abilities too, CR 702.177a, 605.1a). */
val youActivateExhaust: Trigger = on(EventPattern.Activate(ObjectFilter.Any, PlayerFilter.You, manaAbilities = true, label = arcanum.api.AbilityLabel.Exhaust))
/** It is saddled (CR 702.171b). */
val saddledMark: ObjectFilter = ObjectFilter.HasStatus(arcanum.api.Status.Marked(arcanum.api.ObjectMark.Saddled))
/** "Whenever this creature attacks while saddled": it attacks while it is saddled (CR 702.171b). */
val attacksSaddled: EventPattern = EventPattern.Attack(and(isSelf, saddledMark))
fun attacksWhileSaddled(vararg steps: Step, targets: List<TargetSlot> = emptyList()) = triggered(on(attacksSaddled), *steps, targets = targets)
/** "[This creature] saddles Mounts and crews Vehicles as though its power were [n] greater" (CR 702.171a, 702.122a). */
fun saddlesAndCrewsAsThoughGreater(n: Int): Ability = static(Mechanic.PowerGreaterFor(Keyword.Saddle.Kind, n), Mechanic.PowerGreaterFor(Keyword.Crew.Kind, n))
/** 'a 1/1 colorless Pilot creature token with "This token saddles Mounts and crews Vehicles as though its power were 2 greater."' */
val pilotToken: TokenSpec = creatureToken("Pilot", emptyList(), 1, 1, abilities = listOf(saddlesAndCrewsAsThoughGreater(2)))
/** "it's your main phase" (CR 505.1) */
val yourMainPhase: Condition = Condition.And(listOf(Condition.YourTurn,
    Condition.Or(listOf(Condition.StepIs(TurnStep.PrecombatMain), Condition.StepIs(TurnStep.PostcombatMain)))))
/** "Whenever this creature saddles a Mount or crews a Vehicle during your main phase" (CR 702.171c, 702.122b). */
fun saddlesOrCrewsInMainPhase(vararg steps: Step) = triggered(Trigger.On(listOf(
    EventPattern.Tap(isSelf, forCost = Keyword.Saddle.Kind), EventPattern.Tap(isSelf, forCost = Keyword.Crew.Kind))), *steps, triggersOnlyIf = yourMainPhase)
/** "that Mount or Vehicle" of [saddlesOrCrewsInMainPhase] */
val thatMountOrVehicle: Objects = Objects.EventObject(EventRole.Source)
/** "Enchant creature or Vehicle" (CR 702.5a) */
val enchantCreatureOrVehicle: Keyword = Keyword.Enchant(TargetKind.Objects(or(creature, vehicle)))
val enchanted: Objects = Objects.AttachedTo(Objects.Self)
/** Your speed (CR 702.179f). */
val yourSpeed: Value = Value.Speed(Players.You)
/** "Exile this card from your graveyard" as a cost. */
val exileSelfFromGraveyard: CostPart = CostPart.Exile(CostObjects.Self, from = Zone.Graveyard)
/** "Max speed — {3}, Exile this card from your graveyard: Draw a card." (the set's Surveyor cycle) */
val surveyorDraw: Ability = maxSpeed(activated(cost(mana("{3}"), exileSelfFromGraveyard), draw(1)))

