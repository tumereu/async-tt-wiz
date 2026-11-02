package arcanum.engine.query

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.CardType
import arcanum.api.CardTypeRef
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.ManaSymbol
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Status
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.api.isManaAbility
import arcanum.engine.state.Bindings
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PayOutcome
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TriggerEvent
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.view.Characteristics
import arcanum.engine.view.Subtypes
import arcanum.engine.view.SubtypeKind

/**
 * What the evaluator reads about objects: their characteristics as far as they are worked out (part-way through the
 * layers, or the final view) and their controllers. Off the battlefield and the Stack an object has no controller and
 * its owner answers for it (CR 108.4a).
 */
internal interface Board {
    val state: GameState
    fun characteristics(id: ObjectId): Characteristics?
    fun controller(id: ObjectId): PlayerId?

    /** Last known information of an object that has left its zone (CR 608.2h, 603.10): null for a current object. */
    fun snapshot(id: ObjectId): arcanum.engine.state.ObjectSnapshot? = Lki.find(state, id)

    /** The zone of an object that doesn't exist yet but is being tested as it would be (CR 614.12): an entering token. */
    fun proposedZone(id: ObjectId): Zone? = null
}

/**
 * Who "you", "this" and the other references of a definition are while it is evaluated (CR 109.5, D19): [you] the
 * controller, [self] the object the ability is on, [names] the names in scope (a Stack item's targets and choices, an
 * effect object's frozen names, a resolution's binds), [affected] / [affectedPlayer] the object or player a static
 * ability's mechanics are applied to, [tested] / [testedPlayer] what a filter is testing.
 */
internal data class Context(
    val you: PlayerId,
    val self: ObjectId? = null,
    val names: Bindings = Bindings.EMPTY,
    val affected: ObjectId? = null,
    val affectedPlayer: PlayerId? = null,
    val tested: ObjectId? = null,
    val testedPlayer: PlayerId? = null,
    val slotPlayer: PlayerId? = null,
    val holder: PlayerId? = null,
    val event: TriggerEvent? = null,
    val stackItem: StackItem? = null,
    /** The share of a divided amount for the target being affected (CR 601.2d). */
    val dividedShare: Int? = null,
)

/**
 * Works out what the definition building blocks of arcanum-api mean in a game: [Objects], [Players], [ObjectFilter],
 * [PlayerFilter], [Value], [Condition] and the colour/type references. Shared by the view, and by casting,
 * resolution and triggers.
 *
 * "This turn" questions ([ObjectFilter.ThisTurn], [Condition.HappenedThisTurn], [Value.EventsThisTurn],
 * [Value.SpellsCastBefore]) read the tagged events through [History].
 */
internal class Evaluator(val board: Board, val ctx: Context) {
    private val state: GameState get() = board.state

    fun with(ctx: Context) = Evaluator(board, ctx)

    // ---- Objects

    fun objects(objects: Objects): List<ObjectId> = when (objects) {
        Objects.Self -> listOfNotNull(ctx.self)
        is Objects.Target -> boundObjects(ctx.names[objects.slot])
        is Objects.Bound -> boundObjects(ctx.names[objects.name])
        is Objects.Remembered -> boundObjects(selfMemory(objects.key))
        is Objects.EventObject -> ctx.event?.objects.orEmpty().filter { it.role == objects.role }.flatMap { it.ids }
        is Objects.AttachedTo -> objects(objects.of).mapNotNull { (state.obj(it)?.attachedTo as? TargetRef.Object)?.id }
        // The source's new object after a zone change: bound by a flow that knows the move, else found in the log
        Objects.SelfAfter -> ctx.names[SELF_AFTER]?.let { boundObjects(it) } ?: listOfNotNull(ctx.self?.let { Lki.newObject(state, it) })
        Objects.Tested -> listOfNotNull(ctx.tested)
        is Objects.AttackedBy -> {
            val attackers = objects(objects.attackers).toSet()
            state.combat?.attackers.orEmpty().filter { it.id in attackers }.mapNotNull { (it.target as? TargetRef.Object)?.id }
        }
        is Objects.AttachmentsOf -> {
            val hosts = objects(objects.of).toSet()
            state.battlefield.filter { id ->
                val a = state.obj(id)?.attachedTo
                a is TargetRef.Object && a.id in hosts && matches(objects.filter, id)
            }
        }
        is Objects.Each -> each(objects.filter)
        is Objects.LibraryCards -> players(objects.of).flatMap { p ->
            val library = state.player(p).library
            val n = value(objects.count).coerceIn(0, library.size)
            if (objects.fromTop) library.take(n) else library.takeLast(n)
        }
        is Objects.Union -> objects.parts.flatMap { objects(it) }.distinct()
        Objects.Affected -> listOfNotNull(ctx.affected)
        is Objects.LinkedTo -> boundObjects(selfMemory(objects.keyword.id))
        is Objects.PaidWith -> objects(objects.of).flatMap { id ->
            boundObjects(state.obj(id)?.facts?.paidWith?.get(objects.way.toString()))
        }
        // Random picks are asked from the host (D25) by the flow that needs them.
        is Objects.Random -> emptyList()
        is Objects.Where -> objects(objects.objects).filter { matches(objects.filter, it) }
        is Objects.Except -> {
            val minus = objects(objects.minus).toSet()
            objects(objects.base).filterNot { it in minus }
        }
    }

    /** Every object matching [filter] in the zones it names, else on the battlefield (the Stack for spells and
     * abilities); phased-out permanents are treated as though they don't exist (CR 702.26b). */
    fun each(filter: ObjectFilter): List<ObjectId> {
        val zones = zonesIn(filter).ifEmpty { if (filter.mentionsStack()) setOf(Zone.Stack) else setOf(Zone.Battlefield) }
        val candidates = zones.flatMap { zone ->
            when (zone) {
                Zone.Stack -> state.stack
                Zone.Battlefield -> state.battlefield.filter { state.obj(it)?.phasedOut != true }
                Zone.Exile -> state.exile
                Zone.Command -> state.command
                else -> state.players.flatMap { state.zone(zone, it.id) }
            }
        }
        return candidates.filter { matches(filter, it) }
    }

    private fun boundObjects(value: NamedValue?): List<ObjectId> = when (value) {
        is NamedValue.Objects -> value.ids
        is NamedValue.Targets -> value.refs.mapNotNull { (it as? TargetRef.Object)?.id }
        else -> emptyList()
    }

    private fun boundPlayers(value: NamedValue?): List<PlayerId> = when (value) {
        is NamedValue.Players -> value.ids
        is NamedValue.Targets -> value.refs.mapNotNull { (it as? TargetRef.Player)?.id }
        else -> emptyList()
    }

    /** What the source remembers (D41); after it left its zone, what it remembered then (its last known information,
     * kept by the Stack item or the log): a leaves-the-battlefield ability still finds the cards its linked ability
     * exiled (CR 607.2a). */
    private fun selfMemory(key: String): NamedValue? = ctx.self?.let { self ->
        val o = state.obj(self)
        if (o != null) o.memory[key]
        else (board.snapshot(self)?.memory?.takeIf { it.values.isNotEmpty() } ?: Lki.memory(state, self))?.get(key)
    }

    // ---- Players

    fun players(players: Players): List<PlayerId> = when (players) {
        Players.You -> listOf(ctx.you)
        is Players.Target -> boundPlayers(ctx.names[players.slot])
        is Players.Bound -> boundPlayers(ctx.names[players.name])
        is Players.Remembered -> boundPlayers(selfMemory(players.key))
        Players.EventPlayer -> ctx.event?.players.orEmpty()
        is Players.ControllerOf -> objects(players.objects).mapNotNull { controllerOf(it) }.distinct()
        is Players.OwnerOf -> objects(players.objects).mapNotNull { ownerOf(it) }.distinct()
        Players.Active -> listOf(state.turn.active)
        is Players.DefendingPlayerOf -> {
            val attackers = objects(players.attackers).toSet()
            state.combat?.attackers.orEmpty().filter { it.id in attackers }.mapNotNull { attack ->
                when (val target = attack.target) {
                    is TargetRef.Player -> target.id
                    // The controller of the planeswalker, or the protector of the battle (CR 506.2)
                    is TargetRef.Object -> controllerOf(target.id)
                }
            }.distinct()
        }
        Players.Holder -> listOfNotNull(ctx.holder)
        Players.Tested -> listOfNotNull(ctx.testedPlayer)
        Players.SlotPlayer -> listOfNotNull(ctx.slotPlayer)
        is Players.AttachedTo -> objects(players.of).mapNotNull { (state.obj(it)?.attachedTo as? TargetRef.Player)?.id }
        Players.Affected -> listOfNotNull(ctx.affectedPlayer)
        is Players.Each -> state.apnap().filter { matches(players.filter, it) }
    }

    fun matches(filter: PlayerFilter, player: PlayerId): Boolean = when (filter) {
        PlayerFilter.Any -> true
        PlayerFilter.You -> player == ctx.you
        // CR 102.2, 102.3: every other player is an opponent (no teams)
        PlayerFilter.Opponent -> player != ctx.you
        is PlayerFilter.Where -> with(ctx.copy(testedPlayer = player)).holds(filter.condition)
        is PlayerFilter.Is -> player in players(filter.players)
        is PlayerFilter.And -> filter.all.all { matches(it, player) }
        is PlayerFilter.Or -> filter.any.any { matches(it, player) }
        is PlayerFilter.Not -> !matches(filter.filter, player)
        is PlayerFilter.Has -> filter.designation in state.player(player).designations
        is PlayerFilter.Life -> compare(state.player(player).life, filter.comparison, value(filter.value))
        is PlayerFilter.ThisTurn -> History.happened(this, filter.event, player = player)
    }

    // ---- Object filters

    fun controllerOf(id: ObjectId): PlayerId? = board.controller(id)

    fun ownerOf(id: ObjectId): PlayerId? = state.obj(id)?.owner ?: state.stackItems[id]?.controller ?: lki(id)?.owner

    fun characteristics(id: ObjectId): Characteristics? = board.characteristics(id)

    fun matches(filter: ObjectFilter, id: ObjectId): Boolean {
        val o = state.obj(id)
        val c = characteristics(id)
        return when (filter) {
            ObjectFilter.Any -> true
            is ObjectFilter.And -> filter.all.all { matches(it, id) }
            is ObjectFilter.Or -> filter.any.any { matches(it, id) }
            is ObjectFilter.Not -> !matches(filter.filter, id)
            is ObjectFilter.Is -> id in objects(filter.objects)
            is ObjectFilter.Category -> when (filter.category) {
                ObjectCategory.Card -> (o != null && !o.token) || lki(id)?.token == false
                ObjectCategory.Token -> o?.token ?: lki(id)?.token ?: false
                // A permanent that left is read as it last existed (CR 608.2h): "a permanent you controlled left"
                ObjectCategory.Permanent -> zoneOf(id) == Zone.Battlefield || (o == null && lki(id)?.zone == Zone.Battlefield)
                ObjectCategory.Spell -> state.stackItems[id]?.kind.let { it == StackItemKind.Spell || it is StackItemKind.SpellCopy }
                ObjectCategory.StackAbility -> state.stackItems[id]?.kind.let { it == StackItemKind.Activated || it == StackItemKind.Triggered }
            }
            is ObjectFilter.Type -> {
                val types = if (filter.ignoringTypeChanges) o?.printedTypes() else c?.types
                cardTypes(filter.cardType).any { types.orEmpty().contains(it) }
            }
            is ObjectFilter.Super -> c?.supertypes.orEmpty().contains(filter.supertype)
            is ObjectFilter.Sub -> subtypes(filter.subtype).any { c?.hasSubtype(it) == true }
            is ObjectFilter.OfColor -> colors(filter.color).any { c?.colors.orEmpty().contains(it) }
            ObjectFilter.Colorless -> c != null && c.colors.isEmpty()
            ObjectFilter.Multicolored -> c != null && c.colors.size >= 2
            ObjectFilter.Monocolored -> c != null && c.colors.size == 1
            is ObjectFilter.Shares -> objects(filter.objects).any { other -> other != id && shares(filter.quality, id, other) }
            is ObjectFilter.ControlledBy -> controllerOf(id)?.let { matches(filter.players, it) } ?: false
            is ObjectFilter.OwnedBy -> ownerOf(id)?.let { matches(filter.players, it) } ?: false
            is ObjectFilter.InZone -> zoneOf(id) == filter.zone
            is ObjectFilter.Named -> c?.name == filter.name
            is ObjectFilter.SameNameAs -> c?.name != null && objects(filter.objects).any { characteristics(it)?.name == c.name }
            is ObjectFilter.ChosenName -> c?.name != null && (ctx.names[filter.name] as? NamedValue.Name)?.name == c.name
            is ObjectFilter.RememberedName -> c?.name != null && (selfMemory(filter.key) as? NamedValue.Name)?.name == c.name
            is ObjectFilter.HasKeyword -> c?.hasKeyword(filter.keyword) == true
            is ObjectFilter.HasAbilityKind -> c?.abilities.orEmpty().any { it.ability.isKind(filter.kind) }
            is ObjectFilter.AbilityOf -> matchesAbility(filter, id)
            is ObjectFilter.WasCast -> facts(id)?.castFrom?.let { filter.from == null || it == filter.from } ?: false
            is ObjectFilter.WasPaid -> (facts(id)?.paid.orEmpty().filter { it.keyword == filter.keyword }.sumOf { it.times }) >= filter.times
            is ObjectFilter.HasStatus -> hasStatus(id, o, filter.status)
            ObjectFilter.SummoningSick -> o != null && summoningSick(o, controllerOf(id))
            is ObjectFilter.HasCounter -> counterList(id)?.let { counters ->
                (if (filter.kind == null) counters.sumOf { it.count } else counters.filter { it.kind == filter.kind }.sumOf { it.count }) >= filter.atLeast
            } ?: false
            is ObjectFilter.Power -> c?.power?.let { compare(it, filter.comparison, value(filter.value)) } ?: false
            is ObjectFilter.Toughness -> c?.toughness?.let { compare(it, filter.comparison, value(filter.value)) } ?: false
            is ObjectFilter.ManaValue -> c != null && compare(manaValue(id, c), filter.comparison, value(filter.value))
            is ObjectFilter.Targets -> {
                val item = state.stackItems[id]
                val targets = item?.targets?.values?.flatten().orEmpty()
                val objectsTargeted = filter.objects?.let { objects(it).toSet() }.orEmpty()
                val playersTargeted = filter.players?.let { players(it).toSet() }.orEmpty()
                targets.any { (it is TargetRef.Object && it.id in objectsTargeted) || (it is TargetRef.Player && it.id in playersTargeted) }
            }
            is ObjectFilter.Attached -> {
                val toOk = filter.to?.let { to ->
                    val a = o?.attachedTo
                    a is TargetRef.Object && a.id in objects(to)
                } ?: true
                val attachmentsOk = filter.attachments?.let { f ->
                    state.battlefield.any { other ->
                        val a = state.obj(other)?.attachedTo
                        a is TargetRef.Object && a.id == id && matches(f, other)
                    }
                } ?: true
                toOk && attachmentsOk
            }
            is ObjectFilter.ThisTurn -> History.happened(this, filter.event, objectId = id)
        }
    }

    private fun matchesAbility(filter: ObjectFilter.AbilityOf, id: ObjectId): Boolean {
        val item = state.stackItems[id] ?: return false
        if (item.kind != StackItemKind.Activated && item.kind != StackItemKind.Triggered) return false
        val ability = item.ability
        val kindOk = when (filter.kind) {
            null -> true
            AbilityKind.Activated -> item.kind == StackItemKind.Activated
            AbilityKind.Triggered -> item.kind == StackItemKind.Triggered
            AbilityKind.Static -> false
            AbilityKind.Mana -> ability?.isManaAbility() == true
            AbilityKind.Loyalty -> (ability as? Ability.Activated)?.cost?.parts.orEmpty().any { it is CostPart.Loyalty }
        }
        val sourceOk = filter.source?.let { f -> item.source?.let { matches(f, it) } ?: false } ?: true
        val labelOk = filter.label?.let { label ->
            when (ability) {
                is Ability.Activated -> ability.label == label
                is Ability.Triggered -> ability.label == label
                else -> false
            }
        } ?: true
        val keywordOk = filter.keyword?.let { kind ->
            val root = item.origin?.root
            item.origin is AbilityOrigin.Expansion && root != null &&
                item.source?.let { s -> characteristics(s)?.abilities?.any { it.origin == root && (it.ability as? Keyword)?.kind == kind } } == true
        } ?: true
        return kindOk && sourceOk && labelOk && keywordOk
    }

    private fun hasStatus(id: ObjectId, o: GameObject?, status: Status): Boolean = when (status) {
        Status.Tapped -> o?.tapped ?: lki(id)?.tapped ?: false
        Status.FaceDown -> o?.faceDown == true
        Status.Transformed -> o?.transformed == true
        Status.Flipped -> o?.flipped == true
        Status.PhasedOut -> o?.phasedOut == true
        Status.Attacking -> state.combat?.attackers.orEmpty().any { it.id == id }
        Status.Blocking -> state.combat?.blockers.orEmpty().any { it.id == id }
        Status.Blocked -> state.combat?.attackers.orEmpty().any { it.id == id && it.blocked }
        // CR 509.1h, 508.4d: unblocked once blockers are declared
        Status.Unblocked -> state.combat?.let { c -> c.blocksDecided && c.attackers.any { it.id == id && !it.blocked } } == true
        Status.Goaded -> o?.goadedBy?.isNotEmpty() == true
        is Status.Marked -> o?.marks?.contains(status.mark) == true
    }

    /** CR 302.6: [controller] hasn't controlled [o] continuously since their most recent turn began. */
    fun summoningSick(o: GameObject, controller: PlayerId?): Boolean {
        if (o.zone != Zone.Battlefield || controller == null) return false
        val since = o.controlSince ?: return true
        val started = state.turn.turnStarted[controller] ?: return true
        return since.player != controller || since.turn >= started
    }

    /** Where [id] is now. An object that left its zone is in none (CR 400.7): "if this card is in your graveyard" is false
     * for it, whatever its last known information says (CR 603.4); an object about to exist is where it would be. */
    private fun zoneOf(id: ObjectId): Zone? = state.obj(id)?.zone ?: if (id in state.stackItems) Zone.Stack else board.proposedZone(id)

    /** Last known information of [id] when it has left its zone (CR 608.2h). */
    private fun lki(id: ObjectId) = if (state.obj(id) == null) board.snapshot(id) else null

    private fun facts(id: ObjectId) = state.obj(id)?.facts ?: lki(id)?.facts

    private fun counterList(id: ObjectId) = state.obj(id)?.counters ?: lki(id)?.counters

    private fun GameObject.printedTypes(): List<CardType> = definition?.faces?.getOrNull(face)?.types.orEmpty()

    private fun shares(quality: SharedQuality, a: ObjectId, b: ObjectId): Boolean {
        val ca = characteristics(a) ?: return false
        val cb = characteristics(b) ?: return false
        return when (quality) {
            SharedQuality.Color -> ca.colors.any { it in cb.colors }
            SharedQuality.CardType -> ca.types.any { it in cb.types }
            SharedQuality.CreatureType -> creatureTypes(ca).let { ta ->
                (ca.allCreatureTypes && (cb.allCreatureTypes || creatureTypes(cb).isNotEmpty())) ||
                    (cb.allCreatureTypes && ta.isNotEmpty()) || ta.any { it in creatureTypes(cb) }
            }
            SharedQuality.Name -> ca.name != null && ca.name == cb.name
            SharedQuality.BasicLandType -> ca.subtypes.any { it in Subtypes.basicLandTypes && it in cb.subtypes }
            is SharedQuality.Number -> property(quality.property, a) == property(quality.property, b)
        }
    }

    private fun creatureTypes(c: Characteristics) = c.subtypes.filter { Subtypes.kindOf(it) == SubtypeKind.Creature }

    // ---- References to colours and types

    fun colors(ref: ColorRef): List<Color> = when (ref) {
        is ColorRef.Literal -> listOf(ref.color)
        is ColorRef.Chosen -> (ctx.names[ref.name] as? NamedValue.Colors)?.colors.orEmpty()
        is ColorRef.Remembered -> (selfMemory(ref.key) as? NamedValue.Colors)?.colors.orEmpty()
        is ColorRef.Of -> objects(ref.objects).flatMap { characteristics(it)?.colors.orEmpty() }.distinct()
    }

    fun subtypes(ref: SubtypeRef): List<Subtype> = when (ref) {
        is SubtypeRef.Literal -> listOf(ref.subtype)
        is SubtypeRef.Chosen -> listOfNotNull((ctx.names[ref.name] as? NamedValue.SubtypeValue)?.subtype)
        is SubtypeRef.Remembered -> listOfNotNull((selfMemory(ref.key) as? NamedValue.SubtypeValue)?.subtype)
    }

    fun cardTypes(ref: CardTypeRef): List<CardType> = when (ref) {
        is CardTypeRef.Literal -> listOf(ref.cardType)
        is CardTypeRef.Chosen -> listOfNotNull((ctx.names[ref.name] as? NamedValue.CardTypeValue)?.cardType)
        is CardTypeRef.Remembered -> listOfNotNull((selfMemory(ref.key) as? NamedValue.CardTypeValue)?.cardType)
    }

    // ---- Values

    fun value(value: Value): Int = when (value) {
        is Value.Fixed -> value.value
        is Value.Announced -> (ctx.names[value.name] as? NamedValue.Number)?.value ?: ctx.stackItem?.announced?.get(value.name) ?: 0
        is Value.Bound -> boundNumber(ctx.names[value.name])
        is Value.Remembered -> boundNumber(selfMemory(value.key))
        // While it is being cast, the spell's X is the one announced so far (CR 601.2b, 601.2f)
        is Value.CastX -> objects(value.of).firstNotNullOfOrNull { id ->
            facts(id)?.x ?: ctx.stackItem?.takeIf { it.id == id }?.announced?.get("X")
        } ?: 0
        is Value.SpellsCastBefore -> History.spellsCastBefore(this, objects(value.of))
        Value.ModesChosen -> ctx.stackItem?.modes?.size ?: 0
        Value.EventAmount -> ctx.event?.amount ?: 0
        is Value.Count -> objects(value.objects).size
        is Value.CountPlayers -> players(value.players).size
        is Value.CountDistinct -> distinct(value.quality, objects(value.objects))
        is Value.Of -> combine(value.how, objects(value.objects).map { property(value.property, it) })
        is Value.PlayerCounters -> players(value.players).sumOf { state.player(it).counters(value.kind) }
        is Value.LifeOf -> players(value.players).sumOf { state.player(it).life }
        is Value.HandSize -> players(value.players).sumOf { state.player(it).hand.size }
        // CR 700.5: mana symbols of those colours among the mana costs of permanents they control
        is Value.Devotion -> {
            val who = players(value.players).toSet()
            state.battlefield.filter { controllerOf(it) in who }.sumOf { id ->
                characteristics(id)?.manaCost?.symbols.orEmpty().count { symbol -> symbolColors(symbol).any { it in value.colors } }
            }
        }
        is Value.EventsThisTurn -> History.count(this, value.event, value.measure)
        is Value.StartingLife -> players(value.players).sumOf { state.player(it).startingLife }
        is Value.ManaSpent -> objects(value.of).sumOf { id ->
            val spent = facts(id)?.manaSpent.orEmpty()
            when {
                value.distinctColors -> spent.filter { it.color != null && it.amount > 0 }.map { it.color }.distinct().size
                value.color != null -> spent.filter { it.color == value.color }.sumOf { it.amount }
                else -> spent.sumOf { it.amount }
            }
        }
        is Value.TimesPaid -> objects(value.of).sumOf { id ->
            facts(id)?.paid.orEmpty().filter { it.keyword == value.keyword }.sumOf { it.times }
        }
        is Value.AbilityUsesThisTurn -> boundNumber(ctx.names[if (value.resolved) USES_RESOLVED else USES_ACTIVATED])
        is Value.Excess -> boundNumber(ctx.names[EXCESS_PREFIX + value.name])
        Value.DividedShare -> ctx.dividedShare ?: 0
        is Value.Sum -> value.parts.sumOf { value(it) }
        is Value.Difference -> value(value.from) - value(value.minus)
        is Value.Times -> value(value.value) * value.factor
        is Value.Half -> value(value.value).let { if (value.roundUp) (it + 1) / 2 else it / 2 }
        is Value.Max -> value.parts.maxOfOrNull { value(it) } ?: 0
        is Value.Min -> value.parts.minOfOrNull { value(it) } ?: 0
    }

    private fun boundNumber(value: NamedValue?): Int = when (value) {
        is NamedValue.Number -> value.value
        is NamedValue.Rolls -> value.results.sum()
        is NamedValue.Objects -> value.ids.size
        else -> 0
    }

    /** A number of an object (CR 208, 202.3, 306.5, 122): power, toughness, mana value, loyalty, counters. */
    fun property(property: NumericProperty, id: ObjectId): Int {
        val c = characteristics(id)
        val o = state.obj(id)
        return when (property) {
            NumericProperty.Power -> c?.power ?: 0
            NumericProperty.Toughness -> c?.toughness ?: 0
            NumericProperty.ManaValue -> c?.let { manaValue(id, it) } ?: 0
            NumericProperty.Loyalty -> c?.loyalty ?: 0
            is NumericProperty.Counters -> counterList(id).orEmpty().let { counters ->
                if (property.kind == null) counters.sumOf { it.count } else counters.filter { it.kind == property.kind }.sumOf { it.count }
            }
        }
    }

    /** Mana value; X counts its announced value on the Stack (CR 202.3e), also for a spell that has left the Stack and is
     * read as it last existed there (CR 608.2h: "that spell's mana value" of a countered spell). */
    fun manaValue(id: ObjectId, c: Characteristics): Int {
        val x = when {
            zoneOf(id) == Zone.Stack -> state.stackItems[id]?.announced?.get("X") ?: 0
            else -> lki(id)?.takeIf { it.zone == Zone.Stack }?.facts?.x ?: 0
        }
        return (frontFaceCost(id)?.let { c.copy(manaCost = it) } ?: c).manaValue(x)
    }

    /** CR 712.8c, e: a nonmodal double-faced permanent or spell with its back face up has its front face's mana value;
     * one copying something (a back face included) has the copy's (0 for a back face, which has no mana cost). */
    private fun frontFaceCost(id: ObjectId): arcanum.api.ManaCost? {
        val o = state.obj(id) ?: return null
        val definition = o.definition ?: return null
        if (definition.layout != arcanum.api.Layout.Transform || o.face == 0 || o.faceDown) return null
        if (o.zone != Zone.Battlefield && o.zone != Zone.Stack) return null
        if (state.effects.values.any { e -> e.locked?.contains(id) == true && e.mechanics.any { it is arcanum.api.Mechanic.CopyOf } }) return null
        return definition.faces.first().manaCost ?: arcanum.api.ManaCost(emptyList())
    }

    private fun combine(how: Combine, values: List<Int>): Int = when (how) {
        Combine.Single -> values.firstOrNull() ?: 0
        Combine.Total -> values.sum()
        Combine.Greatest -> values.maxOrNull() ?: 0
        Combine.Least -> values.minOrNull() ?: 0
    }

    private fun distinct(quality: SharedQuality, ids: List<ObjectId>): Int {
        val cs = ids.mapNotNull { characteristics(it) }
        return when (quality) {
            SharedQuality.Color -> cs.flatMap { it.colors }.distinct().size
            SharedQuality.CardType -> cs.flatMap { it.types }.distinct().size
            SharedQuality.CreatureType -> cs.flatMap { creatureTypes(it) }.distinct().size
            SharedQuality.Name -> cs.mapNotNull { it.name }.distinct().size
            SharedQuality.BasicLandType -> cs.flatMap { c -> c.subtypes.filter { it in Subtypes.basicLandTypes } }.distinct().size
            is SharedQuality.Number -> ids.map { property(quality.property, it) }.distinct().size
        }
    }

    // ---- Conditions

    fun holds(condition: Condition): Boolean = when (condition) {
        is Condition.And -> condition.all.all { holds(it) }
        is Condition.Or -> condition.any.any { holds(it) }
        is Condition.Not -> !holds(condition.condition)
        is Condition.Compare -> compare(value(condition.left), condition.comparison, value(condition.right))
        is Condition.Exists -> each(condition.filter).size >= condition.atLeast
        is Condition.PlayerMatches -> players(condition.players).let { ps -> ps.isNotEmpty() && ps.all { matches(condition.filter, it) } }
        is Condition.PlayerExists -> state.apnap().count { matches(condition.filter, it) } >= condition.atLeast
        is Condition.ObjectMatches -> objects(condition.objects).let { ids -> ids.isNotEmpty() && ids.all { matches(condition.filter, it) } }
        Condition.YourTurn -> state.turn.active == ctx.you
        is Condition.StepIs -> state.turn.step == condition.step
        is Condition.Did -> (ctx.names[condition.name] as? NamedValue.Payment)?.outcome == PayOutcome.Paid
        is Condition.CouldNot -> (ctx.names[condition.name] as? NamedValue.Payment)?.outcome == PayOutcome.CouldNotPay
        is Condition.WonFlip -> (ctx.names[condition.name] as? NamedValue.Flip)?.won == true
        is Condition.Paid -> objects(condition.of).any { id ->
            val facts = facts(id) ?: return@any false
            val paid = (facts.paid + listOfNotNull(facts.alternative))
                .filter { it.keyword == condition.keyword && (condition.costIndex == null || it.costIndex == condition.costIndex) }
            paid.sumOf { it.times } >= condition.times
        }
        is Condition.CastFrom -> objects(condition.of).any { id ->
            facts(id)?.castFrom?.let { condition.zone == null || it == condition.zone } ?: false
        }
        is Condition.HappenedThisTurn -> History.count(this, condition.event, condition.measure) >= condition.atLeast
        is Condition.NonEmpty -> boundObjects(ctx.names[condition.name]).isNotEmpty()
        // Speed (CR 702.178) isn't modelled yet.
        Condition.MaxSpeed -> false
        // The revealed companion is set up before the game (CR 702.139a); the host marks it in card memory.
        Condition.RevealedCompanion -> selfMemory(REVEALED_COMPANION) != null
        is Condition.Chose -> (ctx.names[condition.name] as? NamedValue.Option)?.option == condition.option
        is Condition.ModeChosen -> ctx.stackItem?.modes?.contains(condition.index) == true
        // Checked by resolution against the Stack item's targets (CR 608.2b).
        is Condition.TargetsLegal ->
            (ctx.names[TARGETS_LEGAL_PREFIX + condition.slots.joinToString(",")] as? NamedValue.Number)?.value != 0
        is Condition.Searched -> ctx.names[SEARCHED_PREFIX + condition.name + ":" + condition.zone] != null
        // A Class's level is card memory (CR 716); a leveler's is its level counters (CR 711.2)
        is Condition.ClassLevelAtLeast -> {
            val o = ctx.self?.let { state.obj(it) }
            val isClass = o?.definition?.faces?.getOrNull(o.face)?.subtypes?.contains(Subtype("Class")) == true
            val level = if (isClass || o == null) boundNumber(selfMemory(CLASS_LEVEL)).coerceAtLeast(1) else o.counters(CounterKind.Level)
            level >= condition.level
        }
        Condition.IsDay -> state.ledger[DAY_NIGHT] == DAY
        Condition.IsNight -> state.ledger[DAY_NIGHT] == NIGHT
    }

    companion object {
        /** Names flows bind for references the evaluator can't work out alone. */
        const val SELF_AFTER = "\$self-after"
        const val USES_ACTIVATED = "\$uses-activated"
        const val USES_RESOLVED = "\$uses-resolved"
        const val EXCESS_PREFIX = "\$excess:"
        const val TARGETS_LEGAL_PREFIX = "\$targets-legal:"
        const val SEARCHED_PREFIX = "\$searched:"
        /** Card memory keys and game ledger keys the engine itself uses. */
        const val REVEALED_COMPANION = "\$revealed-companion"
        const val CLASS_LEVEL = "\$class-level"
        const val DAY_NIGHT = "\$day-night"
        const val DAY = 1
        const val NIGHT = 2

        fun compare(left: Int, comparison: Comparison, right: Int): Boolean = when (comparison) {
            Comparison.Equal -> left == right
            Comparison.NotEqual -> left != right
            Comparison.Less -> left < right
            Comparison.AtMost -> left <= right
            Comparison.Greater -> left > right
            Comparison.AtLeast -> left >= right
        }

        /** The colours a mana symbol counts for (CR 202.2, 700.5). */
        fun symbolColors(symbol: ManaSymbol): List<Color> = when (symbol) {
            is ManaSymbol.Colored -> listOf(symbol.color)
            is ManaSymbol.Hybrid -> listOf(symbol.first, symbol.second)
            is ManaSymbol.GenericHybrid -> listOf(symbol.color)
            is ManaSymbol.ColorlessHybrid -> listOf(symbol.color)
            is ManaSymbol.Phyrexian -> listOf(symbol.color)
            is ManaSymbol.PhyrexianHybrid -> listOf(symbol.first, symbol.second)
            else -> emptyList()
        }

        /** The zones a filter names with [ObjectFilter.InZone], anywhere in it. */
        fun zonesIn(filter: ObjectFilter): Set<Zone> = when (filter) {
            is ObjectFilter.InZone -> setOf(filter.zone)
            is ObjectFilter.And -> filter.all.flatMap { zonesIn(it) }.toSet()
            is ObjectFilter.Or -> filter.any.flatMap { zonesIn(it) }.toSet()
            else -> emptySet()
        }

        private fun ObjectFilter.mentionsStack(): Boolean = when (this) {
            is ObjectFilter.Category -> category == ObjectCategory.Spell || category == ObjectCategory.StackAbility
            is ObjectFilter.AbilityOf, is ObjectFilter.Targets -> true
            is ObjectFilter.And -> all.any { it.mentionsStack() }
            is ObjectFilter.Or -> any.any { it.mentionsStack() }
            else -> false
        }

        private fun Ability.isKind(kind: AbilityKind): Boolean = when (kind) {
            AbilityKind.Activated -> this is Ability.Activated
            AbilityKind.Triggered -> this is Ability.Triggered
            AbilityKind.Static -> this is Ability.Static
            AbilityKind.Mana -> isManaAbility()
            AbilityKind.Loyalty -> this is Ability.Activated && cost.parts.any { it is CostPart.Loyalty }
        }
    }
}

/**
 * "This turn" questions over the tagged events (D33): the events of this turn matched against an
 * [arcanum.api.EventPattern] ([arcanum.engine.trigger.EventMatcher]), as the evaluator's "you" sees them.
 */
internal object History {
    fun happened(
        evaluator: Evaluator, event: arcanum.api.EventPattern, objectId: ObjectId? = null, player: PlayerId? = null,
    ): Boolean = instances(evaluator, event).any { e ->
        (objectId == null || e.objects.any { objectId in it.ids }) && (player == null || player in e.players)
    }

    fun count(evaluator: Evaluator, event: arcanum.api.EventPattern, measure: arcanum.api.Measure): Int {
        val state = evaluator.board.state
        return when (measure) {
            arcanum.api.Measure.Events -> state.eventsOf(state.turn.number).count { change ->
                arcanum.engine.trigger.EventMatcher.instances(evaluator, event, change).isNotEmpty()
            }
            arcanum.api.Measure.Objects -> instances(evaluator, event).flatMap { e ->
                (e.objects.firstOrNull { it.role == arcanum.api.EventRole.Object || it.role == arcanum.api.EventRole.Before }
                    ?: e.objects.firstOrNull())?.ids.orEmpty()
            }.size
            arcanum.api.Measure.Amount -> instances(evaluator, event).sumOf { it.amount ?: 0 }
        }
    }

    /** Spells cast this turn before the cast of [of] (storm, CR 702.40a). */
    fun spellsCastBefore(evaluator: Evaluator, of: List<ObjectId>): Int {
        val state = evaluator.board.state
        val casts = state.eventsOf(state.turn.number).filter { arcanum.engine.trigger.EventMatcher.isCast(it) }
        val index = casts.indexOfFirst { c -> arcanum.engine.trigger.EventMatcher.castObject(c) in of }
        return if (index < 0) casts.size else index
    }

    private fun instances(evaluator: Evaluator, event: arcanum.api.EventPattern): List<TriggerEvent> {
        val state = evaluator.board.state
        return state.eventsOf(state.turn.number).flatMap { arcanum.engine.trigger.EventMatcher.instances(evaluator, event, it) }
    }
}

/** Counter kinds that modify power and toughness (CR 122.1a, 613.4c). */
internal val CounterKind.powerToughness: Pair<Int, Int>?
    get() = (this as? CounterKind.PowerToughness)?.let { it.power to it.toughness }
