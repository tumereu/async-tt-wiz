package arcanum.parser.sets.ecl

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.AbilityLabel
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Designation
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
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
 * Shared shapes of the Lorwyn Eclipsed (ECL) parser tests. The test cards are invented (D15): new names, the real cards' exact oracle
 * text with the new name.
 */

// ---- Filters

val creature: ObjectFilter = ObjectFilter.Type(CardType.Creature)
val land: ObjectFilter = ObjectFilter.Type(CardType.Land)
val artifact: ObjectFilter = ObjectFilter.Type(CardType.Artifact)
val enchantment: ObjectFilter = ObjectFilter.Type(CardType.Enchantment)
val instant: ObjectFilter = ObjectFilter.Type(CardType.Instant)
val sorcery: ObjectFilter = ObjectFilter.Type(CardType.Sorcery)
val nonland: ObjectFilter = ObjectFilter.Not(land)
val legendary: ObjectFilter = ObjectFilter.Super(Supertype.Legendary)
val permanentCard: ObjectFilter = ObjectFilter.Or(listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment,
    CardType.Land, CardType.Planeswalker).map { ObjectFilter.Type(it) })
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
val equipment: ObjectFilter = sub("Equipment")
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

fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList(), replacements: List<Mechanic.Replacement> = emptyList(), announced: List<String> = emptyList()) =
    Ability.Spell(Instructions(targets = targets, steps = steps.toList(), replacements = replacements, announced = announced))
fun triggered(trigger: Trigger, vararg steps: Step, targets: List<TargetSlot> = emptyList(), interveningIf: Condition? = null, perTurn: Int? = null,
              abilityWord: String? = null, triggersOnlyIf: Condition? = null, functionsIn: List<Zone>? = null) =
    Ability.Triggered(trigger, Instructions(targets = targets, steps = steps.toList()), interveningIf = interveningIf, perTurn = perTurn,
        abilityWord = abilityWord, triggersOnlyIf = triggersOnlyIf, functionsIn = functionsIn)
fun activated(cost: Cost, vararg steps: Step, targets: List<TargetSlot> = emptyList(), rules: ActivationRules = anyTime, functionsIn: List<Zone>? = null) =
    Ability.Activated(cost, Instructions(targets = targets, steps = steps.toList()), rules, functionsIn = functionsIn)
fun static(vararg mechanics: Mechanic, affects: Objects = Objects.Self, condition: Condition? = null, characteristicDefining: Boolean = false) =
    Ability.Static(mechanics.toList(), affects = Affected.Objects(affects), condition = condition, characteristicDefining = characteristicDefining)
fun playerStatic(vararg mechanics: Mechanic, players: Players = Players.You, condition: Condition? = null) =
    Ability.Static(mechanics.toList(), affects = Affected.Players(players), condition = condition)
fun modes(vararg options: Mode, min: Int = 1, max: Int = 1, onlyUnchosen: Boolean = false) =
    Modes(options.toList(), min = min, max = max, onlyUnchosen = onlyUnchosen, chooser = Players.You)
fun mode(vararg steps: Step, targets: List<TargetSlot> = emptyList()) = Mode(Instructions(targets = targets, steps = steps.toList()))

val anyTime = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)
val asSorcery = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)
fun cost(vararg parts: CostPart): Cost = Cost(parts.toList())
fun mana(text: String): CostPart = CostPart.Mana(ManaCost.parse(text)!!)
fun manaCost(text: String): Cost = Cost.mana(ManaCost.parse(text)!!)
val tap: CostPart = CostPart.TapSelf
val sacrificeSelf: CostPart = CostPart.Sacrifice(CostObjects.Self)
fun sacrificeA(filter: ObjectFilter, preBind: String? = null): CostPart = CostPart.Sacrifice(CostObjects.Choose(filter, count = Value.Fixed(1)), preBind = preBind)
val discardACard: CostPart = CostPart.Discard(CostObjects.Choose(cardFilter, count = Value.Fixed(1)))

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
fun stepBegins(step: TurnStep, whose: PlayerFilter): Trigger = on(EventPattern.StepBegins(step, whose))
val beginningOfCombatOnYourTurn: Trigger = stepBegins(TurnStep.BeginningOfCombat, PlayerFilter.You)
/** "Landfall — Whenever a land you control enters" (an ability word, CR 207.2c). */
val landfall: Trigger = enters(and(land, you))
fun youCast(spells: ObjectFilter): Trigger = on(EventPattern.Cast(spells, PlayerFilter.You))
val noncreatureSpell: ObjectFilter = not(creature)
val combatDamageToAPlayer: Trigger = on(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Any), combat = true))
/** "Whenever you draw your second card each turn" */
val drawSecondCard: Trigger = Trigger.On(listOf(EventPattern.Draw(PlayerFilter.You)), nthThisTurn = 2)

// ---- Effects

fun n(value: Int): Value = Value.Fixed(value)
fun doing(effect: Effect): Step = Step.Do(effect)
fun draw(count: Int, players: Players = Players.You): Step = Step.Do(Effect.Draw(players, n(count)))
fun discard(count: Int, players: Players = Players.You): Step = Step.Do(Effect.Discard(players, n(count), ObjectFilter.Any))
fun gainLife(amount: Int, players: Players = Players.You): Step = Step.Do(Effect.GainLife(players, n(amount)))
fun loseLife(amount: Int, players: Players = Players.You): Step = Step.Do(Effect.LoseLife(players, n(amount)))
fun mill(count: Int, players: Players = Players.You, postBind: String? = null): Step = Step.Do(Effect.Mill(players, n(count), postBind = postBind))
fun scry(count: Int): Step = Step.Do(Effect.Scry(Players.You, n(count)))
fun damage(amount: Value, to: Recipients, source: Objects = Objects.Self): Step = Step.Do(Effect.DealDamage(to, amount, source))
fun damage(amount: Int, to: Recipients, source: Objects = Objects.Self): Step = damage(n(amount), to, source)
val p1p1: CounterKind = CounterKind.PowerToughness(1, 1)
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
fun power(of: Objects): Value = Value.Of(NumericProperty.Power, of, Combine.Single)
fun toughness(of: Objects): Value = Value.Of(NumericProperty.Toughness, of, Combine.Single)
fun count(filter: ObjectFilter): Value = Value.Count(Objects.Each(filter))
fun search(filter: ObjectFilter, max: Int = 1, reveal: Boolean = false, bind: String = "found", zones: List<Zone> = listOf(Zone.Library)): Step =
    Step.Do(Effect.Search(Players.You, filter, max = n(max), reveal = reveal, zones = zones, bind = bind, searcher = Players.You))
val shuffle: Step = Step.Do(Effect.Shuffle(Players.You))
fun basicLand(): ObjectFilter = and(ObjectFilter.Super(Supertype.Basic), land)

// ---- Tokens

val treasure: TokenSpec = TokenSpec.Predefined("Treasure")
fun createTreasure(count: Value = n(1), tapped: Boolean = false): Step = Step.Do(Effect.CreateToken(treasure, count, Players.You, tapped = tapped))
fun creatureToken(subtypes: String, colors: List<Color>, power: Int, toughness: Int, abilities: List<Ability> = emptyList(),
                  types: List<CardType> = listOf(CardType.Creature), name: String = "$subtypes Token") =
    TokenSpec.Defined(FaceDefinition(name, colorIndicator = colors, types = types, subtypes = subtypes.split(' ').map(::Subtype),
        power = PrintedNumber.Fixed(power), toughness = PrintedNumber.Fixed(toughness), abilities = abilities))
fun create(token: TokenSpec, count: Value = n(1), bind: String? = null): Step = Step.Do(Effect.CreateToken(token, count, Players.You, bind = bind))
val soldier: TokenSpec = creatureToken("Soldier", listOf(Color.White), 1, 1)
val clue: TokenSpec = TokenSpec.Predefined("Clue")
val food: TokenSpec = TokenSpec.Predefined("Food")


// ---- Lorwyn Eclipsed mechanics

val m1m1: CounterKind = CounterKind.PowerToughness(-1, -1)
/** "blight N" as a cost (CR 701.68a); "the blighted creature" afterwards (701.68c). */
fun blight(count: Int): CostPart = CostPart.Action(KeywordAction.Blight(Players.You, n(count)), bind = "blighted")
/** "[players] blight(s) N" as an effect (CR 701.68a). */
fun blights(players: Players, count: Int): Step = Step.Do(Effect.Action(KeywordAction.Blight(players, n(count)), bind = "blighted"))
/** "As an additional cost to cast this spell, [cost]" (CR 601.2f). */
fun additionalCost(vararg parts: CostPart, optional: Boolean = false): Ability =
    Ability.Static(listOf(Mechanic.AdditionalCost(cost = cost(*parts), optional = optional)), affects = Affected.Objects(Objects.Self))
/** "As an additional cost to cast this spell, [a] or [b]". */
fun additionalCostOneOf(vararg options: Cost): Ability =
    Ability.Static(listOf(Mechanic.AdditionalCost(cost = cost(CostPart.OneOf(options.toList())))), affects = Affected.Objects(Objects.Self))
/** "if this spell's additional cost was paid" (CR 601.2f). */
val additionalCostPaid: Condition = Condition.Paid(null, times = 1, of = Objects.Self)
/** "behold a [filter]" (CR 701.4a). */
fun behold(filter: ObjectFilter, count: Int = 1): CostPart = CostPart.Action(KeywordAction.Behold(filter, n(count)), bind = "beheld")
/** "behold a [filter] and exile it": a matching permanent you control or card from your hand, exiled and kept as "the exiled card"
 * (CR 701.4a, 607.2a). */
fun beholdAndExile(filter: ObjectFilter): Ability = additionalCostOneOf(
    cost(CostPart.Exile(CostObjects.Choose(filter, count = n(1)), from = Zone.Battlefield, remember = "exiled")),
    cost(CostPart.Exile(CostObjects.Choose(filter, count = n(1)), from = Zone.Hand, remember = "exiled")))
/** "When this creature leaves the battlefield, return the exiled card to its owner's hand." */
val returnTheExiledCard: Ability = triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)),
    move(Objects.Remembered("exiled"), Zone.Hand))
/** Vivid: "the number of colors among permanents you control" (CR 105.4). */
val colorsAmongPermanents: Value = Value.CountDistinct(Objects.Each(and(permanentFilter, you)), arcanum.api.SharedQuality.Color)
/** "This creature enters with N -1/-1 counters on it." (CR 614.1c) */
fun entersWithM1M1(count: Int): Ability = Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
    modify = listOf(arcanum.api.Modification.EntersWithCounters(m1m1, n(count))))), affects = Affected.Objects(Objects.Self))
/** "Remove a counter from this creature", "Remove two counters from this creature" (any kinds, CR 122.8). */
fun removeCounters(count: Int): CostPart = CostPart.RemoveCounters(null, n(count), from = CostObjects.Self)
/** "you may [cost]. If you do, [steps]" (CR 118.12). */
fun mayPay(vararg parts: CostPart, then: List<Step>): List<Step> =
    listOf(Step.Pay(cost(*parts), payer = Players.You, bind = "paid"), Step.If(Condition.Did("paid"), then = then))
/** "[a 1/1 green and white Kithkin creature token]" */
val kithkin: TokenSpec = creatureToken("Kithkin", listOf(Color.Green, Color.White), 1, 1)
val goblin: TokenSpec = creatureToken("Goblin", listOf(Color.Black, Color.Red), 1, 1)
val faerie: TokenSpec = creatureToken("Faerie", listOf(Color.Blue, Color.Black), 1, 1, abilities = listOf(Keyword.Flying))
val merfolk: TokenSpec = creatureToken("Merfolk", listOf(Color.White, Color.Blue), 1, 1)
val elf: TokenSpec = creatureToken("Elf", listOf(Color.Black, Color.Green), 2, 2)
/** "a 1/1 colorless Shapeshifter creature token with changeling" */
val shapeshifter: TokenSpec = creatureToken("Shapeshifter", emptyList(), 1, 1, abilities = listOf(Keyword.Changeling))
/** "At the beginning of your first main phase, you may pay {G}. If you do, transform [this]." (CR 505.1a, 701.27a) */
fun mayPayToTransform(mana: String): Ability = triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You),
    *mayPay(mana(mana), then = listOf(Step.Do(Effect.Transform(Objects.Self)))).toTypedArray())
/** "Whenever this creature enters or transforms into [name]" (CR 701.27e) */
fun entersOrTransformsInto(name: String): Trigger = Trigger.On(listOf(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
    EventPattern.Transform(and(ObjectFilter.Is(Objects.Self), ObjectFilter.Named(name)))))
fun transformsInto(name: String): Trigger = on(EventPattern.Transform(and(ObjectFilter.Is(Objects.Self), ObjectFilter.Named(name))))
/** "Whenever this creature becomes tapped" */
val becomesTapped: Trigger = on(EventPattern.Tap(ObjectFilter.Is(Objects.Self)))
/** "Whenever another creature you control enters" */
val anotherCreatureYouControlEnters: Trigger = enters(and(another, creature, you))
/** "the chosen type" (a creature type chosen as it entered, kept as card memory) */
val ofChosenType: ObjectFilter = ObjectFilter.Sub(arcanum.api.SubtypeRef.Remembered("type"))
/** "that type" (a creature type chosen on resolution) */
val ofThatType: ObjectFilter = ObjectFilter.Sub(arcanum.api.SubtypeRef.Chosen("type"))
val chooseCreatureType: Step = Step.Choose(arcanum.api.Choice.CreatureType, bind = "type", chooser = Players.You)
/** "this spell costs {N} less to cast …" */
fun costsLess(mana: String, times: Value, condition: Condition? = null): Ability = Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self),
    arcanum.api.CostChange.Less(ManaCost.parse(mana)!!, times))), affects = Affected.Objects(Objects.Self), condition = condition)
fun gainAbilitiesUntilEndOfTurn(objects: Objects, vararg keywords: Ability): Step = untilEndOfTurn(objects, Mechanic.GainAbilities(keywords.toList()))
/** "When you do, …" (CR 603.12) */
fun reflexive(vararg steps: Step, targets: List<TargetSlot> = emptyList()): Step =
    Step.Do(Effect.ReflexiveTrigger(Instructions(targets = targets, steps = steps.toList())))
/** "Vivid — [ability]": an ability word (CR 207.2c), no rules meaning. */
fun vivid(ability: Ability): Ability = when (ability) {
    is Ability.Triggered -> ability.copy(abilityWord = "Vivid")
    is Ability.Activated -> ability.copy(abilityWord = "Vivid")
    is Ability.Static -> ability.copy(abilityWord = "Vivid")
    is Ability.Spell -> ability.copy(abilityWord = "Vivid")
    else -> error("no ability word on $ability")
}
/** "[objects] get +[power]/+[toughness] until end of turn" for computed values. */
fun pumpBy(objects: Objects, power: Value, toughness: Value): Step = untilEndOfTurn(objects, Mechanic.ModifyPowerToughness(power, toughness))
/** "tap [it] and put a stun counter on it" (CR 122.1d) */
fun tapAndStun(objects: Objects): List<Step> = listOf(tapIt(objects), counters(objects, 1, CounterKind.Stun))
/** A static ability of this object's own ("This spell costs …"). */
val selfOnly: Affected = Affected.Objects(Objects.Self)
/** "sacrifice this token/creature" */
val sacrificeIt: Step = Step.Do(Effect.Sacrifice(Objects.Self))
/** "the beginning of the end step" (each player's) */
val eachEndStep: Trigger = stepBegins(TurnStep.End, PlayerFilter.Any)
