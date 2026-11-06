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

    /** Told before a power or toughness is read: the layer system tracks which effects read them (CR 613.8a). */
    fun readingPowerToughness() {}

    /** Told before the tagged events (D33) are read, or last known information found in them: a view that read none
     * stays the same while only events are added ([arcanum.engine.view.ViewCache]). */
    fun readingLog() {}

    /** eoe: whether a rule matching [mechanic] applies to [id] in the finished view (false part-way through the layers). */
    fun hasMechanic(id: ObjectId, mechanic: (arcanum.api.Mechanic) -> Boolean): Boolean = false

    /** dft: the rules that apply to [id] in the finished view (none part-way through the layers). */
    fun mechanicsOf(id: ObjectId): List<arcanum.api.Mechanic> = emptyList()
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
    // dsk gaps
    /** Cards not on the Stack or the battlefield are tested as the spell they would be if cast from where they are
     * (CR 601.2a): [ObjectFilter.WasCast] from their zone, a spell, controlled by [you]. For alternative costs given to
     * spells cast from a zone and to the next spells cast. */
    val prospectiveCast: Boolean = false,
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
        // A spell's own static ability reads the spell's chosen targets ("costs {1} less if it targets a tapped creature",
        // CR 601.2f: the total cost is determined after the targets are chosen)
        is Objects.Target -> ctx.names[objects.slot]?.let { boundObjects(it) }
            ?: ctx.self?.let { s -> state.stackItems[s]?.targets?.get(objects.slot)?.filterIsInstance<TargetRef.Object>()?.map { it.id } }.orEmpty()
        // spm: a name a keyword's cost made for the spell is kept by the permanent it became ("the returned creature")
        is Objects.Bound -> boundObjects(ctx.names[objects.name]
            ?: selfMemory(objects.name).takeIf { objects.name in arcanum.api.KeywordExpansions.COST_NAMES })
        is Objects.Remembered -> boundObjects(selfMemory(objects.key))
        is Objects.EventObject -> ctx.event?.objects.orEmpty().filter { it.role == objects.role }.flatMap { it.ids }
        // ecl: an Aura sacrificed to pay its own ability's cost still names what it was attached to (CR 608.2h)
        is Objects.AttachedTo -> objects(objects.of).mapNotNull { ((state.obj(it)?.attachedTo ?: lki(it)?.attachedTo) as? TargetRef.Object)?.id }
        // tla: in an ability another object's static ability gives, that object (CR 113.10)
        Objects.Granter -> listOfNotNull((ctx.stackItem?.origin as? arcanum.engine.view.AbilityOrigin.FromAbility)?.source ?: ctx.self)
        // The source's new object after a zone change: bound by a flow that knows the move, else found in the log
        Objects.SelfAfter -> ctx.names[SELF_AFTER]?.let { boundObjects(it) } ?: listOfNotNull(ctx.self?.let { board.readingLog(); Lki.newObject(state, it) })
        Objects.Tested -> listOfNotNull(ctx.tested)
        is Objects.AttackedBy -> {
            val attackers = objects(objects.attackers).toSet()
            state.combat?.attackers.orEmpty().filter { it.id in attackers }.mapNotNull { (it.target as? TargetRef.Object)?.id }
        }
        is Objects.AttachmentsOf -> {
            val hosts = objects(objects.of).toSet()
            // fin: a host that has left the battlefield: what was attached to it as it last existed (CR 608.2h), those still unattached
            val gone = hosts.filter { state.obj(it)?.zone != Zone.Battlefield }.map { TargetRef.Object(it) }.toSet()
            val wereAttached = if (gone.isEmpty()) emptySet() else {
                board.readingLog()
                state.eventsOf(state.turn.number).filterIsInstance<arcanum.engine.change.Change.Attached>().filter { it.before in gone && it.after == null }
                    .map { it.id }.toSet()
            }
            state.battlefield.filter { id ->
                val a = state.obj(id)?.attachedTo
                ((a is TargetRef.Object && a.id in hosts) || (a == null && id in wereAttached)) && matches(objects.filter, id)
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
        // CR 702.95b
        is Objects.PairedWith -> objects(objects.of).mapNotNull { arcanum.engine.soulbond.Soulbond.partner(state, it) }.distinct()
        is Objects.LinkedTo -> boundObjects(selfMemory(objects.keyword.id))
        is Objects.PaidWith -> objects(objects.of).flatMap { id ->
            boundObjects(facts(id)?.paidWith?.get(objects.way.toString()))
        }
        // Random picks are asked from the host (D25) by the resolution that needs them (Scope.objects)
        is Objects.Random -> error("\"At random\" objects are picked by the resolution that uses them (D25), not evaluated")
        is Objects.Where -> objects(objects.objects).filter { matches(objects.filter, it) }
        is Objects.Except -> {
            val minus = objects(objects.minus).toSet()
            objects(objects.base).filterNot { it in minus }
        }
    }

    /** Every object matching [filter] in the zones it names, else on the battlefield (the Stack for spells and
     * abilities); phased-out permanents are treated as though they don't exist (CR 702.26b). */
    fun each(filter: ObjectFilter): List<ObjectId> = inZonesOf(filter).filter { matches(filter, it) }

    /** The objects [each] looks at for [filter]: everything in the zones it names (the battlefield, or the Stack, when
     * it names none; both for "spell or creature", ecl). */
    fun inZonesOf(filter: ObjectFilter): List<ObjectId> {
        val zones = zonesIn(filter).ifEmpty { defaultZones(filter) } + prospectiveZones(filter)
        return zones.flatMap { zone ->
            when (zone) {
                Zone.Stack -> state.stack
                Zone.Battlefield -> state.battlefield.filter { state.obj(it)?.phasedOut != true }
                Zone.Exile -> state.exile
                Zone.Command -> state.command
                else -> state.players.flatMap { state.zone(zone, it.id) }
            }
        }
    }

    /**
     * Whether [id] matching [filter] depends on what card it is (I-D18): with its identity unknown, it could
     * match and could fail to. Parts about what the card is (types, colours, name, abilities, keywords, P/T, mana value,
     * sharing a quality) could go either way; the rest (zone, owner, controller, status, counters, what it targets, a
     * card or a token, events) is read as it is.
     */
    fun identityDecides(filter: ObjectFilter, id: ObjectId): Boolean = could(filter, id, true) && could(filter, id, false)

    /** Whether [filter] could be [result] for [id] whatever card it is. */
    private fun could(filter: ObjectFilter, id: ObjectId, result: Boolean): Boolean = when (filter) {
        is ObjectFilter.And -> if (result) filter.all.all { could(it, id, true) } else filter.all.any { could(it, id, false) }
        is ObjectFilter.Or -> if (result) filter.any.any { could(it, id, true) } else filter.any.all { could(it, id, false) }
        is ObjectFilter.Not -> could(filter.filter, id, !result)
        ObjectFilter.Any, is ObjectFilter.Is, is ObjectFilter.Category, is ObjectFilter.ControlledBy, is ObjectFilter.OwnedBy,
        is ObjectFilter.InZone, is ObjectFilter.AbilityOf, is ObjectFilter.WasCast, is ObjectFilter.WasPaid,
        is ObjectFilter.HasStatus, ObjectFilter.SummoningSick, ObjectFilter.Paired, is ObjectFilter.HasCounter, is ObjectFilter.Targets,
        is ObjectFilter.Blocking, is ObjectFilter.BlockedBy,
        is ObjectFilter.Attached, is ObjectFilter.ThisTurn -> matches(filter, id) == result
        else -> true
    }

    private fun boundObjects(value: NamedValue?): List<ObjectId> = when (value) {
        is NamedValue.Objects -> value.ids
        is NamedValue.Targets -> value.refs.mapNotNull { (it as? TargetRef.Object)?.id }
        is NamedValue.Spell -> listOf(value.item.id)
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
        else {
            board.readingLog()
            (board.snapshot(self)?.memory?.takeIf { it.values.isNotEmpty() } ?: Lki.memory(state, self))?.get(key)
        }
    }

    // ---- Players

    fun players(players: Players): List<PlayerId> = when (players) {
        Players.You -> listOf(ctx.you)
        is Players.Target -> boundPlayers(ctx.names[players.slot])
        is Players.Bound -> boundPlayers(ctx.names[players.name])
        is Players.Remembered -> boundPlayers(selfMemory(players.key))
        // blb: the opponent chosen for a gift, kept by the permanent the spell became (CR 702.174a, 400.7d)
        is Players.ChosenAsCost -> objects(players.of).mapNotNull { facts(it)?.chosenPlayer }.distinct()
        // fin: in an effect a triggered ability created, "that player" is its event's player, kept as the effect began (CR 608.2h)
        Players.EventPlayer -> ctx.event?.players ?: (ctx.names[EVENT_PLAYERS] as? arcanum.engine.state.NamedValue.Players)?.ids.orEmpty()
        is Players.ControllerOf -> objects(players.objects).mapNotNull { controllerOf(it) }.distinct()
        is Players.OwnerOf -> objects(players.objects).mapNotNull { ownerOf(it) }.distinct()
        Players.Active -> listOf(state.turn.active)
        is Players.DefendingPlayerOf -> {
            val attackers = objects(players.attackers).toSet()
            state.combat?.attackers.orEmpty().filter { it.id in attackers }.mapNotNull { attack ->
                when (val target = attack.target) {
                    is TargetRef.Player -> target.id
                    // The controller of the planeswalker, or the protector of the battle (CR 506.2, 310.9d)
                    is TargetRef.Object -> state.obj(target.id)?.protector ?: controllerOf(target.id)
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
                // dsk gaps: a copy of a card isn't a card (CR 109.1)
                ObjectCategory.Card -> (o != null && !o.isToken && !o.copyOfCard) || (o == null && lki(id)?.token == false)
                ObjectCategory.Token -> o?.isToken ?: lki(id)?.token ?: false
                // A permanent that left is read as it last existed (CR 608.2h): "a permanent you controlled left"
                ObjectCategory.Permanent -> zoneOf(id) == Zone.Battlefield || (o == null && lki(id)?.zone == Zone.Battlefield)
                // msh: a card tested as the spell it would be or is becoming as it is cast (CR 601.2a)
                ObjectCategory.Spell -> state.stackItems[id]?.kind.let { it == StackItemKind.Spell || it is StackItemKind.SpellCopy } ||
                    ctx.prospectiveCast && (prospectiveCastFrom(id) != null || state.obj(id)?.zone == Zone.Stack)
                ObjectCategory.StackAbility -> state.stackItems[id]?.kind.let { it == StackItemKind.Activated || it == StackItemKind.Triggered }
            }
            is ObjectFilter.Type -> {
                val types = if (filter.ignoringTypeChanges) o?.printedTypes() else c?.types
                cardTypes(filter.cardType).any { types.orEmpty().contains(it) }
            }
            is ObjectFilter.Super -> c?.supertypes.orEmpty().contains(filter.supertype)
            is ObjectFilter.Sub -> subtypes(filter.subtype).any { c?.hasSubtype(it) == true }
            is ObjectFilter.OfColor -> colors(filter.color).any { c?.colors.orEmpty().contains(it) }
            // fra: a prepare spell (CR 722.3c, d)
            ObjectFilter.PrepareSpell -> o?.prepareSpellOf != null
            ObjectFilter.HasDealtCombatDamage -> o?.facts?.dealtCombatDamage == true
            ObjectFilter.HasDealtDamage -> o?.facts?.dealtDamage == true // tdm
            // msh: the only creature declared as an attacker in this combat (CR 506.5)
            ObjectFilter.AttackingAlone -> state.combat?.attackers.orEmpty().any { it.id == id } && attackedAlone(id)
            // msh: a target of one of these spells or abilities (CR 115.1)
            is ObjectFilter.TargetedBy -> objects(filter.objects).any { item ->
                state.stackItems[item]?.targets?.values.orEmpty().any { refs -> refs.any { it == TargetRef.Object(id) } }
            }
            // hob: the condition read with this object as the tested one
            is ObjectFilter.Where -> with(ctx.copy(tested = id)).holds(filter.condition)
            ObjectFilter.Colorless -> c != null && c.colors.isEmpty()
            ObjectFilter.Multicolored -> c != null && c.colors.size >= 2
            ObjectFilter.Monocolored -> c != null && c.colors.size == 1
            is ObjectFilter.Shares -> objects(filter.objects).any { other -> other != id && shares(filter.quality, id, other) }
            // msh: a card tested as the spell it would be is controlled by the player who would cast it (CR 601.2a)
            is ObjectFilter.ControlledBy -> (controllerOf(id) ?: prospectiveCastFrom(id)?.let { ctx.you })?.let { matches(filter.players, it) } ?: false
            is ObjectFilter.OwnedBy -> ownerOf(id)?.let { matches(filter.players, it) } ?: false
            is ObjectFilter.InZone -> zoneOf(id) == filter.zone
            is ObjectFilter.Named -> filter.name in names(c)
            // CR 709.4a: a split card has each of its halves' names
            is ObjectFilter.SameNameAs -> names(c).let { mine -> mine.isNotEmpty() && objects(filter.objects).any { other -> names(characteristics(other)).any { it in mine } } }
            is ObjectFilter.ChosenName -> c?.name != null && (ctx.names[filter.name] as? NamedValue.Name)?.name == c.name
            // CR 702.106f: double agenda's second name counts as the chosen name too
            is ObjectFilter.RememberedName -> c?.name != null && ((selfMemory(filter.key) as? NamedValue.Name)?.name == c.name ||
                (filter.key == arcanum.engine.conspiracy.Conspiracies.CHOSEN_NAME &&
                    (selfMemory(arcanum.engine.conspiracy.Conspiracies.SECOND_NAME) as? NamedValue.Name)?.name == c.name))
            is ObjectFilter.HasKeyword -> c?.hasKeyword(filter.keyword) == true
            is ObjectFilter.HasAbilityKind -> c?.abilities.orEmpty().any { it.ability.isKind(filter.kind) }
            is ObjectFilter.AbilityOf -> matchesAbility(filter, id)
            is ObjectFilter.WasCast -> (facts(id)?.castFrom ?: prospectiveCastFrom(id))?.let { filter.from == null || it == filter.from } ?: false
            // eoe: an alternative cost counts too ("a spell was warped this turn": cast for its warp cost, CR 702.185c)
            is ObjectFilter.WasPaid -> facts(id).let { f -> (f?.paid.orEmpty() + listOfNotNull(f?.alternative)) }
                .filter { it.keyword == filter.keyword }.sumOf { it.times } >= filter.times
            is ObjectFilter.HasStatus -> hasStatus(id, o, filter.status)
            // gtc chunk 07: the blocking relation of this combat (CR 509.1a, 509.1h)
            is ObjectFilter.Blocking -> objects(filter.objects).let { them -> state.combat?.blockers.orEmpty().any { b -> b.id == id && b.attackers.any { it in them } } }
            is ObjectFilter.BlockedBy -> objects(filter.objects).let { them -> state.combat?.blockers.orEmpty().any { b -> b.id in them && id in b.attackers } }
            ObjectFilter.SummoningSick -> o != null && summoningSick(o, controllerOf(id))
            ObjectFilter.Paired -> arcanum.engine.soulbond.Soulbond.partner(state, id) != null
            is ObjectFilter.HasCounter -> counterList(id)?.let { counters ->
                (if (filter.kind == null) counters.sumOf { it.count } else counters.filter { it.kind == filter.kind }.sumOf { it.count }) >= filter.atLeast
            } ?: false
            is ObjectFilter.Power -> {
                board.readingPowerToughness()
                c?.power?.let { compare(it, filter.comparison, value(filter.value)) } ?: false
            }
            is ObjectFilter.Toughness -> {
                board.readingPowerToughness()
                c?.toughness?.let { compare(it, filter.comparison, value(filter.value)) } ?: false
            }
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
            is ObjectFilter.ThisTurn -> History.happened(this, filter.event, objectId = id, role = filter.role)
            // tmt: tapped this turn to pay a crew cost of one of the Vehicles (CR 702.122c)
            is ObjectFilter.CrewedThisTurn -> History.crewed(this, id, objects(filter.vehicles).toSet())
            // fin: a Vehicle one of [crew] crewed this turn (CR 702.122b)
            is ObjectFilter.CrewedBy -> objects(filter.crew).any { History.crewed(this, it, setOf(id)) }
            // blb gaps 2: "with {X} in its mana cost" (CR 107.3, 202.1); "the first instant spell you've cast this turn"
            ObjectFilter.XInManaCost -> c?.manaCost?.symbols.orEmpty().any { it is arcanum.api.ManaSymbol.Variable && it.name == 'X' }
            is ObjectFilter.NthSpellThisTurn -> History.isNthSpell(this, filter, id)
            // CR 115.7: one target in all ("with a single target"), of [target] when given
            // dft: no abilities at all (CR 113.1)
            ObjectFilter.NoAbilities -> c?.abilities.orEmpty().isEmpty()
            // spm: modified (CR 700.9): a counter on it, an Equipment attached, or an Aura its controller controls attached
            ObjectFilter.Modified -> (counterList(id)?.any { it.count > 0 } == true) || state.battlefield.any { other ->
                val a = state.obj(other)?.attachedTo
                a is TargetRef.Object && a.id == id && characteristics(other)?.let { oc ->
                    oc.hasSubtype(Subtype("Equipment")) || (oc.hasSubtype(Subtype("Aura")) && controllerOf(other) == controllerOf(id))
                } == true
            }
            is ObjectFilter.SingleTarget -> {
                val targets = state.stackItems[id]?.targets?.values?.flatten().orEmpty()
                targets.size == 1 && (filter.target.let { f -> f == null || (targets[0] as? TargetRef.Object)?.id?.let { matches(f, it) } == true } ||
                    // mh2: "a single permanent or player"
                    filter.players?.let { p -> (targets[0] as? TargetRef.Player)?.id?.let { matches(p, it) } } == true)
            }
            // mh2: one of its mana abilities could add mana of that kind (CR 106.7)
            is ObjectFilter.CouldProduce -> c != null && couldProduce(c, filter.mana)
            // mh2: its mana cost is exactly that (CR 202.1)
            is ObjectFilter.HasManaCost -> c?.manaCost == filter.cost
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
            // Special actions don't use the Stack (CR 116.1): only one being taken matches (a provisional item, for its
            // costs and the mana spent on it)
            AbilityKind.Unlock -> arcanum.engine.room.Rooms.isUnlockRule(item.origin)
            AbilityKind.TurnFaceUp -> (ability as? Ability.SpecialAction)?.instructions?.steps.orEmpty()
                .any { (it as? arcanum.api.Step.Do)?.effect is arcanum.api.Effect.TurnFaceUp }
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
            // mh2: a source that left (modular's "when it dies") is read as it last existed (CR 608.2h)
            item.origin is AbilityOrigin.Expansion && root != null &&
                item.source?.let { s -> (characteristics(s) ?: lki(s)?.characteristics)?.abilities?.any { it.origin == root && (it.ability as? Keyword)?.kind == kind } } == true
        } ?: true
        return kindOk && sourceOk && labelOk && keywordOk
    }

    private fun hasStatus(id: ObjectId, o: GameObject?, status: Status): Boolean = when (status) {
        Status.Tapped -> o?.tapped ?: lki(id)?.tapped ?: false
        Status.FaceDown -> o?.faceDown == true
        Status.Transformed -> o?.transformed == true
        Status.Flipped -> o?.flipped == true
        Status.PhasedOut -> o?.phasedOut == true
        // fra: one that left is read as it last existed (CR 608.2h)
        Status.Attacking -> state.combat?.attackers.orEmpty().any { it.id == id } || (o == null && lki(id)?.attacking == true)
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
    // dsk gaps
    /** With [Context.prospectiveCast]: the zones spells matching [filter]'s [ObjectFilter.WasCast] are being or could be
     * cast from, and the Stack. */
    private fun prospectiveZones(filter: ObjectFilter): Set<Zone> {
        if (!ctx.prospectiveCast) return emptySet()
        fun froms(f: ObjectFilter): List<Zone?> = when (f) {
            is ObjectFilter.WasCast -> listOf(f.from)
            is ObjectFilter.And -> f.all.flatMap { froms(it) }
            is ObjectFilter.Or -> f.any.flatMap { froms(it) }
            else -> emptyList()
        }
        val from = froms(filter)
        if (from.isEmpty()) return emptySet()
        return setOf(Zone.Stack) + from.flatMap { z -> z?.let { listOf(it) } ?: CAST_FROM }
    }

    /** Where spells can be cast from ("spells you cast"). */
    private val CAST_FROM = listOf(Zone.Hand, Zone.Library, Zone.Graveyard, Zone.Exile, Zone.Command)

    private fun prospectiveCastFrom(id: ObjectId): Zone? =
        if (!ctx.prospectiveCast) null else zoneOf(id)?.takeIf { it != Zone.Stack && it != Zone.Battlefield }

    private fun zoneOf(id: ObjectId): Zone? = state.obj(id)?.zone ?: if (id in state.stackItems) Zone.Stack else board.proposedZone(id)

    /** Last known information of [id] when it has left its zone (CR 608.2h). */
    private fun lki(id: ObjectId): arcanum.engine.state.ObjectSnapshot? {
        if (state.obj(id) != null) return null
        board.readingLog()
        return board.snapshot(id)
    }

    /** What happened to [id] as an object: a card's facts, a copy of a spell's own (CR 707.10), or as it last existed. */
    private fun facts(id: ObjectId) = state.obj(id)?.facts ?: copyFacts(id) ?: lki(id)?.facts

    private fun copyFacts(id: ObjectId) = (state.stackItems[id] ?: ctx.stackItem?.takeIf { it.id == id })?.facts

    private fun counterList(id: ObjectId) = state.obj(id)?.counters ?: lki(id)?.counters

    /** tdm: the kinds of counters on [id] (CR 122.1). */
    private fun counterKinds(id: ObjectId) = counterList(id).orEmpty().filter { it.count > 0 }.map { it.kind }.distinct()

    private fun GameObject.printedTypes(): List<CardType> = definition?.faces?.getOrNull(face)?.types.orEmpty()

    /** [a] and [b] share [quality] ("share a color", "with the same power"). */
    fun shares(quality: SharedQuality, a: ObjectId, b: ObjectId): Boolean {
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
            SharedQuality.PlaneswalkerType -> planeswalkerTypes(ca).any { it in planeswalkerTypes(cb) }
            is SharedQuality.Number -> property(quality.property, a) == property(quality.property, b)
            SharedQuality.CounterKind -> counterKinds(a).any { it in counterKinds(b) } // tdm
        }
    }

    /** Its names: one, or each half's (CR 709.4a), or none (face down, CR 708.2a). */
    /** blb gaps: whether each of [ids] can stand for a different value of [quality] that it has ("for each card type, … a
     * card of that type": an artifact creature card is the artifact or the creature), found by matching each object to a
     * value of its own, trying again with another for the objects already matched (CR 205.2a). An object with every
     * creature type (changeling, CR 702.73a) can always stand for one more. */
    fun eachStandsForOne(quality: SharedQuality, ids: List<ObjectId>): Boolean {
        val values: List<List<Any>> = ids.mapIndexed { i, id ->
            val c = characteristics(id) ?: return false
            when (quality) {
                SharedQuality.Color -> c.colors
                SharedQuality.CardType -> c.types
                SharedQuality.CreatureType -> if (c.allCreatureTypes) listOf("every creature type $i") else creatureTypes(c)
                SharedQuality.Name -> names(c)
                SharedQuality.BasicLandType -> c.subtypes.filter { it in Subtypes.basicLandTypes }
                SharedQuality.PlaneswalkerType -> planeswalkerTypes(c)
                is SharedQuality.Number -> listOf(property(quality.property, id))
                SharedQuality.CounterKind -> counterKinds(id) // tdm
            }
        }
        val holder = mutableMapOf<Any, Int>()
        fun place(i: Int, tried: MutableSet<Any>): Boolean = values[i].any { v ->
            tried.add(v) && (holder[v]?.let { other -> place(other, tried) } ?: true).also { placed -> if (placed) holder[v] = i }
        }
        return values.indices.all { place(it, mutableSetOf()) }
    }

    private fun names(c: Characteristics?): List<String> = c?.name?.split(" // ").orEmpty()

    private fun creatureTypes(c: Characteristics) = c.subtypes.filter { Subtypes.kindOf(it) == SubtypeKind.Creature }
    // fra: a planeswalker's planeswalker types (CR 205.3j)
    private fun planeswalkerTypes(c: Characteristics) =
        if (c.isType(arcanum.api.CardType.Planeswalker)) c.subtypes.filter { Subtypes.kindOf(it) == SubtypeKind.Planeswalker } else emptyList()

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

    /** mh2: whether one of [c]'s mana abilities (those its basic land types give included, CR 305.6) adds mana of [mana]'s kind, one symbol:
     * a colour or colourless (CR 106.7). */
    private fun couldProduce(c: Characteristics, mana: arcanum.api.ManaCost): Boolean {
        val symbol = mana.symbols.singleOrNull() ?: return false
        val color = (symbol as? arcanum.api.ManaSymbol.Colored)?.color
        val colorless = symbol is arcanum.api.ManaSymbol.Colorless
        return c.abilities.any { inst ->
            val a = inst.ability as? arcanum.api.Ability.Activated ?: return@any false
            if (!a.isManaAbility()) return@any false
            a.instructions.steps.mapNotNull { (it as? arcanum.api.Step.Do)?.effect as? arcanum.api.Effect.AddMana }.any { add ->
                when (val kind = add.mana.kind) {
                    is arcanum.api.ManaKind.Fixed -> kind.mana.symbols.any { s ->
                        (colorless && (s is arcanum.api.ManaSymbol.Colorless || s is arcanum.api.ManaSymbol.Generic)) ||
                            (color != null && s is arcanum.api.ManaSymbol.Colored && s.color == color)
                    }
                    is arcanum.api.ManaKind.Choose -> color != null && (kind.colors == null || kind.colors!!.flatMap { colors(it) }.contains(color))
                    is arcanum.api.ManaKind.EachColorAmong -> color != null
                    is arcanum.api.ManaKind.AsCouldProduce, is arcanum.api.ManaKind.SameAsProduced -> false
                }
            }
        }
    }

    // ---- Values

    fun value(value: Value): Int = when (value) {
        is Value.Fixed -> value.value
        // spm: the turns that player has had this game, this one included: worked out from the number of the latest turn they began,
        // players taking turns in order from turn 1 (extra turns aren't counted apart)
        is Value.TurnsTaken -> players(value.players).firstOrNull()?.let { p ->
            ((state.turn.turnStarted[p] ?: 0) + state.players.size - 1) / state.players.size
        } ?: 0
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
        is Value.Quotient -> if (value.divisor <= 0) 0 else value(value.value) / value.divisor
        // sos: "2ˣ" (2⁰ = 1), held to an Int (a game never needs more)
        is Value.Exponential -> {
            var result = 1L
            repeat(value(value.exponent).coerceIn(0, 62)) { result = (result * value.base).coerceAtMost(Int.MAX_VALUE.toLong()) }
            result.toInt()
        }
        is Value.Count -> objects(value.objects).size
        is Value.CountPlayers -> players(value.players).size
        is Value.CountDistinct -> distinct(value.quality, objects(value.objects))
        // tla: unspent mana (CR 106.4)
        is Value.ManaInPool -> players(value.players).sumOf { state.player(it).manaPool.size }
        // tla: the most objects sharing one value of the quality (a changeling shares every creature type, CR 702.73a)
        is Value.RingTemptations -> players(value.players).sumOf { arcanum.engine.ring.TheRing.temptations(state, it) }
        is Value.DungeonsCompleted -> players(value.players).sumOf { arcanum.engine.dungeon.Dungeons.completed(state, it) }
        is Value.MostSharing -> mostSharing(value.quality, objects(value.objects))
        // dft: a player's speed, 0 without speed (CR 702.179f)
        is Value.Speed -> players(value.players).sumOf { arcanum.engine.turn.Speed.of(state, it) }
        // dft: the X of the cycling that triggered this ability (CR 107.3a, 702.29c)
        Value.EventX -> ctx.event?.x ?: 0
        // mh2: coin flips won and lost (CR 705.2)
        is Value.FlipsWon -> when (val f = ctx.names[value.name]) { is NamedValue.Flip -> if (f.won) 1 else 0; is NamedValue.Flips -> f.won; else -> 0 }
        is Value.FlipsLost -> when (val f = ctx.names[value.name]) { is NamedValue.Flip -> if (f.won) 0 else 1; is NamedValue.Flips -> f.lost; else -> 0 }
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
                // hob: only mana from sources matching (as they last existed, CR 106.3)
                value.source != null -> facts(id)?.manaSources.orEmpty().count { matches(value.source!!, it) }
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
        // rtr chunk 09: how many of the colours (each counted once) the objects have (CR 105.2)
        is Value.SharedColors -> {
            val among = value.colors.flatMap { colors(it) }.toSet()
            objects(value.objects).flatMap { characteristics(it)?.colors.orEmpty() }.distinct().count { it in among }
        }
        is Value.Min -> value.parts.minOfOrNull { value(it) } ?: 0
        // gtc chunk 05: the greatest of a value for each player ("the greatest number of cards a player discarded this way")
        is Value.GreatestFor -> players(value.players).maxOfOrNull { p -> with(ctx.copy(testedPlayer = p)).value(value.value) } ?: 0
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
            NumericProperty.Power, NumericProperty.Toughness -> {
                board.readingPowerToughness()
                (if (property == NumericProperty.Power) c?.power else c?.toughness) ?: 0
            }
            NumericProperty.ManaValue -> c?.let { manaValue(id, it) } ?: 0
            NumericProperty.Loyalty -> c?.loyalty ?: 0
            is NumericProperty.Counters -> counterList(id).orEmpty().let { counters ->
                if (property.kind == null) counters.sumOf { it.count } else counters.filter { it.kind == property.kind }.sumOf { it.count }
            }
            // eoe: what it stations with: its power, or its toughness when a rule says so (CR 702.184a, c)
            NumericProperty.Stationing -> property(if (board.hasMechanic(id) { it == arcanum.api.Mechanic.StationsWithToughness })
                NumericProperty.Toughness else NumericProperty.Power, id)
            // dft: what it crews or saddles with: its power or toughness, and "as though its power were N greater" (CR 702.122a, 702.171a)
            is NumericProperty.PowerFor -> {
                val rules = board.mechanicsOf(id)
                val toughness = rules.any { it is arcanum.api.Mechanic.UsesToughnessFor && it.keyword == property.keyword }
                property(if (toughness) NumericProperty.Toughness else NumericProperty.Power, id) +
                    rules.filterIsInstance<arcanum.api.Mechanic.PowerGreaterFor>().filter { it.keyword == property.keyword }.sumOf { it.amount }
            }
            // tla: how many creature types it has (every one for a changeling, CR 702.73a)
            NumericProperty.CreatureTypes -> c?.let { if (it.allCreatureTypes) Subtypes.creatureTypeCount else creatureTypes(it).distinct().size } ?: 0
            // CR 709.5c
            NumericProperty.UnlockedDoors -> o?.marks.orEmpty().count { it is arcanum.api.ObjectMark.Unlocked }
            // msh: mana symbols of a colour in its mana cost, hybrid ones included (CR 107.4, 202.1)
            is NumericProperty.ManaSymbols -> c?.manaCost?.symbols.orEmpty().count { s ->
                when (s) {
                    is arcanum.api.ManaSymbol.Colored -> s.color == property.color
                    is arcanum.api.ManaSymbol.Hybrid -> s.first == property.color || s.second == property.color
                    is arcanum.api.ManaSymbol.GenericHybrid -> s.color == property.color
                    is arcanum.api.ManaSymbol.ColorlessHybrid -> s.color == property.color
                    is arcanum.api.ManaSymbol.Phyrexian -> s.color == property.color
                    is arcanum.api.ManaSymbol.PhyrexianHybrid -> s.first == property.color || s.second == property.color
                    else -> false
                }
            }
        }
    }

    /** msh: whether [id] was the only creature declared as an attacker in the latest declaration of attackers this turn
     * (CR 506.5: a creature put onto the battlefield attacking doesn't change that). */
    private fun attackedAlone(id: ObjectId): Boolean {
        val declared = state.eventsOf(state.turn.number).lastOrNull { c ->
            c is arcanum.engine.change.Change.Combat && arcanum.engine.change.Meaning.AttackersDeclared in c.meanings
        } as? arcanum.engine.change.Change.Combat ?: return false
        val before = declared.before?.attackers.orEmpty().map { it.id }.toSet()
        val now = declared.after?.attackers.orEmpty().filter { it.id !in before }
        return now.singleOrNull()?.id == id
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
        // fin: CR 202.3c, 712.8g: a melded permanent's mana value is the total of its two front faces'
        if (definition.layout is arcanum.api.Layout.Meld && o.face == 1 && o.zone == Zone.Battlefield)
            return arcanum.api.ManaCost(((definition.faces.first().manaCost?.symbols ?: emptyList()) +
                o.mergedBelow.flatMap { it.definition?.faces?.firstOrNull()?.manaCost?.symbols.orEmpty() }))
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

    /** tla: the most of [ids] that share one value of [quality]. */
    private fun mostSharing(quality: SharedQuality, ids: List<ObjectId>): Int {
        val cs = ids.mapNotNull { characteristics(it) }
        if (quality == SharedQuality.CreatureType) {
            val all = cs.count { it.allCreatureTypes }
            val counts = cs.filter { !it.allCreatureTypes }.flatMap { creatureTypes(it).distinct() }.groupingBy { it }.eachCount()
            return all + (counts.values.maxOrNull() ?: 0)
        }
        val values: List<List<Any>> = when (quality) {
            SharedQuality.Color -> cs.map { it.colors }
            SharedQuality.CardType -> cs.map { it.types }
            SharedQuality.Name -> cs.map { names(it) }
            SharedQuality.BasicLandType -> cs.map { c -> c.subtypes.filter { it in Subtypes.basicLandTypes } }
            SharedQuality.PlaneswalkerType -> cs.map { planeswalkerTypes(it) }
            is SharedQuality.Number -> ids.map { listOf(property(quality.property, it)) }
            SharedQuality.CounterKind -> ids.map { counterKinds(it) } // tdm
            SharedQuality.CreatureType -> emptyList()
        }
        return values.flatMap { it.distinct() }.groupingBy { it }.eachCount().values.maxOrNull() ?: 0
    }

    internal fun distinct(quality: SharedQuality, ids: List<ObjectId>): Int {
        val cs = ids.mapNotNull { characteristics(it) }
        return when (quality) {
            SharedQuality.Color -> cs.flatMap { it.colors }.distinct().size
            SharedQuality.CardType -> cs.flatMap { it.types }.distinct().size
            SharedQuality.CreatureType -> cs.flatMap { creatureTypes(it) }.distinct().size
            SharedQuality.Name -> cs.flatMap { names(it) }.distinct().size
            SharedQuality.BasicLandType -> cs.flatMap { c -> c.subtypes.filter { it in Subtypes.basicLandTypes } }.distinct().size
            SharedQuality.PlaneswalkerType -> cs.flatMap { planeswalkerTypes(it) }.distinct().size
            is SharedQuality.Number -> ids.map { property(quality.property, it) }.distinct().size
            SharedQuality.CounterKind -> ids.flatMap { counterKinds(it) }.distinct().size // tdm
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
        is Condition.WonFlip -> (ctx.names[condition.name] as? NamedValue.Flip)?.won == true || ((ctx.names[condition.name] as? NamedValue.Flips)?.won ?: 0) > 0
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
        is Condition.HappenedLastTurn -> History.count(this, condition.event, condition.measure, turn = state.turn.number - 1) >= condition.atLeast
        is Condition.NonEmpty -> boundObjects(ctx.names[condition.name]).isNotEmpty()
        // sos: paradigm (CR 702.192a): a spell with that name controlled by such a player resolved earlier this game
        is Condition.ResolvedEarlierThisGame -> objects(condition.of).flatMap { names(characteristics(it)) }.any { name ->
            state.players.any { p -> matches(condition.controller, p.id) && (p.ledger[resolvedKey(name)] ?: 0) > 0 }
        }
        // CR 702.179e: max speed is speed 4
        Condition.MaxSpeed -> arcanum.engine.turn.Speed.of(state, ctx.you) >= arcanum.engine.turn.Speed.MAX
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
        is Condition.Prime -> value(condition.value).let { n -> n >= 2 && (2..n / 2).none { n % it == 0 } }
        // hob
        is Condition.Odd -> value(condition.value) % 2 != 0
        is Condition.RememberedOption -> (selfMemory(condition.key) as? NamedValue.Option)?.option == condition.option
        Condition.IsDay -> state.ledger[DAY_NIGHT] == DAY
        Condition.IsNight -> state.ledger[DAY_NIGHT] == NIGHT
    }

    companion object {
        /** Names flows bind for references the evaluator can't work out alone. */
        const val SELF_AFTER = "\$self-after"
        /** fin: the players of the event of the triggered ability that created an effect ([Players.EventPlayer] in it). */
        const val EVENT_PLAYERS = "\$event-players"
        const val USES_ACTIVATED = "\$uses-activated"
        const val USES_RESOLVED = "\$uses-resolved"
        const val EXCESS_PREFIX = "\$excess:"
        const val TARGETS_LEGAL_PREFIX = "\$targets-legal:"
        const val SEARCHED_PREFIX = "\$searched:"
        /** sos: the player ledger key counting resolutions of spells named [name] that player controlled (CR 702.192a). */
        fun resolvedKey(name: String): String = "resolved:$name"

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

        /** Where a filter naming no zone looks: the Stack for spells and abilities, the battlefield otherwise, and both for
         * "either" filters with one of each ("target spell or creature", CR 115.4). */
        private fun defaultZones(filter: ObjectFilter): Set<Zone> = when {
            filter is ObjectFilter.Or && filter.any.any { it.mentionsStack() } && filter.any.any { !it.mentionsStack() && zonesIn(it).isEmpty() } ->
                setOf(Zone.Stack, Zone.Battlefield)
            filter.mentionsStack() -> setOf(Zone.Stack)
            else -> setOf(Zone.Battlefield)
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
            // CR 709.5e: unlocking is a rule of Room permanents, not an ability they have
            AbilityKind.Unlock -> false
            AbilityKind.TurnFaceUp -> this is Ability.SpecialAction && instructions.steps.any { (it as? arcanum.api.Step.Do)?.effect is arcanum.api.Effect.TurnFaceUp }
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
        // rtr chunk 05: only in this role of the event ("that dealt damage this turn": the source)
        role: arcanum.api.EventRole? = null,
    ): Boolean = instances(evaluator, event).any { e ->
        (objectId == null || e.objects.any { (role == null || it.role == role) && objectId in it.ids }) && (player == null || player in e.players)
    }

    /** How many times [event] happened in [turn] (this turn when null; fra: the previous turn for "last turn"). */
    fun count(evaluator: Evaluator, event: arcanum.api.EventPattern, measure: arcanum.api.Measure, turn: Int? = null): Int {
        evaluator.board.readingLog()
        val state = evaluator.board.state
        val number = turn ?: state.turn.number
        return when (measure) {
            arcanum.api.Measure.Events -> state.eventsOf(number).count { change ->
                arcanum.engine.trigger.EventMatcher.instances(evaluator, event, change).isNotEmpty()
            }
            arcanum.api.Measure.Objects -> instances(evaluator, event, number).flatMap { e ->
                (e.objects.firstOrNull { it.role == arcanum.api.EventRole.Object || it.role == arcanum.api.EventRole.Before }
                    ?: e.objects.firstOrNull())?.ids.orEmpty()
            }.size
            arcanum.api.Measure.Amount -> instances(evaluator, event, number).sumOf { it.amount ?: 0 }
            // tmt: the qualities among the events' objects, each as it was then ("card types among spells you've cast this turn")
            is arcanum.api.Measure.Distinct -> evaluator.distinct(measure.quality, instances(evaluator, event, number).flatMap { e ->
                (e.objects.firstOrNull { it.role == arcanum.api.EventRole.Object || it.role == arcanum.api.EventRole.Before }
                    ?: e.objects.firstOrNull())?.ids.orEmpty()
            }.distinct())
        }
    }

    /** tmt: whether [id] was tapped this turn to pay the crew cost of one of [vehicles] (CR 702.122b, c). */
    fun crewed(evaluator: Evaluator, id: ObjectId, vehicles: Set<ObjectId>): Boolean {
        evaluator.board.readingLog()
        val state = evaluator.board.state
        return state.eventsOf(state.turn.number).any { change ->
            change is arcanum.engine.change.Change.Status && change.id == id &&
                change.meanings.any { it is arcanum.engine.change.Meaning.Crewed && it.vehicle in vehicles }
        }
    }

    /** Spells cast this turn before the cast of [of] (storm, CR 702.40a). */
    fun spellsCastBefore(evaluator: Evaluator, of: List<ObjectId>): Int {
        evaluator.board.readingLog()
        val state = evaluator.board.state
        val casts = state.eventsOf(state.turn.number).filter { arcanum.engine.trigger.EventMatcher.isCast(it) }
        val index = casts.indexOfFirst { c -> arcanum.engine.trigger.EventMatcher.castObject(c) in of }
        return if (index < 0) casts.size else index
    }

    /** blb gaps 2: whether [id] is the [ObjectFilter.NthSpellThisTurn.n]th spell matching its filter that its caster cast
     * this turn. A cast spell that became the evaluator's own object (CR 400.7) is judged as that object ("the first Otter
     * spell other than Alania"). */
    fun isNthSpell(evaluator: Evaluator, filter: ObjectFilter.NthSpellThisTurn, id: ObjectId): Boolean {
        evaluator.board.readingLog()
        val state = evaluator.board.state
        val casts = state.eventsOf(state.turn.number).filter { arcanum.engine.trigger.EventMatcher.isCast(it) }
            .map { (it as arcanum.engine.change.Change.StackItemAdded).item }
        val found = casts.indexOfFirst { it.id == id }
        // tla: a spell on the Stack not yet cast (its costs being determined, CR 601.2f) is the next one cast
        val beingCast = found < 0 && state.obj(id)?.zone == Zone.Stack
        if (found < 0 && !beingCast) return false
        val index = if (beingCast) casts.size else found
        val self = evaluator.ctx.self
        fun counts(spell: ObjectId): Boolean {
            val judged = if (self != null && spell != self && Lki.newObject(state, spell) == self) self else spell
            return evaluator.matches(filter.spells, judged)
        }
        if (!counts(id)) return false
        val caster = if (beingCast) evaluator.board.controller(id) else casts[index].controller
        return casts.take(index).count { it.controller == caster && counts(it.id) } + 1 == filter.n
    }

    private fun instances(evaluator: Evaluator, event: arcanum.api.EventPattern, turn: Int? = null): List<TriggerEvent> {
        evaluator.board.readingLog()
        val state = evaluator.board.state
        return state.eventsOf(turn ?: state.turn.number).flatMap { arcanum.engine.trigger.EventMatcher.instances(evaluator, event, it) }
    }
}

/** Counter kinds that modify power and toughness (CR 122.1a, 613.4c). */
internal val CounterKind.powerToughness: Pair<Int, Int>?
    get() = (this as? CounterKind.PowerToughness)?.let { it.power to it.toughness }
