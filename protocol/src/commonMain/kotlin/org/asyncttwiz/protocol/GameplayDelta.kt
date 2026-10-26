package org.asyncttwiz.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Sparse, recorded changes, not commands or per-event board snapshots. Paths address fields within
 * one entity. Sequence edits bind the untouched gap on both sides, including the two edge gaps. */
@Serializable data class GameplayValueDelta(val path: List<String>, val from: JsonElement?, val to: JsonElement?,
    val sequence: Boolean = false, val after: JsonElement? = null, val before: JsonElement? = null,
    val unordered: Boolean = false)
@Serializable data class GameplayEntityDelta(val kind: String, val id: String = "", val index: Int = 0,
    val values: List<GameplayValueDelta>)
@Serializable data class GameplayPileDelta(val containerId: String, val placement: PlayPlacement,
    val from: List<String>, val to: List<String>)
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class GameplayDelta(val entities: List<GameplayEntityDelta>, val seal: String = "",
    val piles: List<GameplayPileDelta> = emptyList(),
    /** Applied before the action delta, reversed after it. Bounded by library size, not history. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val libraryReseeds: List<LibraryReseed> = emptyList())
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class GameplayBookkeeping(val fromGroup: GameplayGroup?, val fromBranchId: String,
    val toGroup: GameplayGroup?,
    /** An owner-independent deletion does not lock owner. Retain its traversal inverse separately
     * from the sealed semantic tuple, just as presentation folds are recomputed after reparenting. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val deletedTokenOwners: Map<String, String> = emptyMap())

/** Encoding is restricted to changed entities. Applying a tap never serializes the decks, history,
 * or definitions. Unchanged immutable entities are shared by the resulting state. */
internal object GameplayDeltas {
    private val json get() = GameWire.json
    // Nullable JsonElement decodes JSON null as Kotlin null. Preserve explicit null separately
    // from an absent object key, including across SQL and tree-sync serialization.
    private val nullValue = JsonObject(mapOf("$" + "null" to JsonPrimitive(true)))
    private fun stored(value: JsonElement?) = if (value == JsonNull) nullValue else value
    private fun restored(value: JsonElement?) = if (value == nullValue) JsonNull else value

    internal fun entityValue(state: PlayState, kind: String, id: String): JsonElement? {
        val b = state.board
        return when (kind) {
            "card" -> b.cards.singleOrNull { it.id == id }?.let { json.encodeToJsonElement(it) }
            "player" -> b.players.singleOrNull { it.accountId == id }?.let { json.encodeToJsonElement(it) }
            "container" -> b.containers.singleOrNull { it.id == id }?.let { json.encodeToJsonElement(it) }
            "contents" -> b.contentsDisplays.singleOrNull { it.id == id }?.let { json.encodeToJsonElement(it) }
            "link" -> b.links.singleOrNull { it.id == id }?.let { json.encodeToJsonElement(it) }
            "effect" -> b.stackEffects.singleOrNull { it.id == id }?.let { json.encodeToJsonElement(it) }
            "occurrence" -> CombatStackRules.occurrences(b.combat).singleOrNull { it.id == id }?.let { json.encodeToJsonElement(it) }
            "projectionLink" -> b.projectionLinks.singleOrNull { it.id == id }?.let { json.encodeToJsonElement(it) }
            "definition" -> state.definitions.singleOrNull { it.digest == id }?.let { json.encodeToJsonElement(it) }
            "turn" -> json.encodeToJsonElement(b.turn)
            "combat" -> b.combat?.let { json.encodeToJsonElement(it) }
            "result" -> b.resultSuggestion?.let { json.encodeToJsonElement(it) }
            "attention" -> JsonPrimitive(b.attentionHolder)
            "marks" -> marks(b)
            "comment" -> state.annotations.commentsById[id]?.let { json.encodeToJsonElement(it) }
            "ping" -> state.annotations.pingsById[id]?.let { json.encodeToJsonElement(it) }
            "ping-ordinal" -> state.annotations.lastOrdinals[id]?.let(::JsonPrimitive)
            else -> error("Unknown entity kind: $kind")
        }
    }

    fun seal(mutation: GameplayMutation): String = DeckJson.intent("gameplay-event-v2", buildJsonObject {
        put("requestId", mutation.requestId)
        put("invocationId", mutation.invocationId?.let(::JsonPrimitive) ?: JsonNull)
        put("author", mutation.author)
        put("action", json.encodeToJsonElement(PlayAction.serializer(), mutation.action))
        // Branch assignment and optional grouping describe retained topology/presentation, not
        // the recorded board effect. Reparenting and legacy grouping fallback must not invalidate it.
        put("transition", json.encodeToJsonElement(GameplayTransition.serializer(), mutation.transition.copy(delta = null)))
        put("delta", mutation.transition.delta?.let { json.encodeToJsonElement(GameplayDelta.serializer(), it.copy(seal = "")) } ?: JsonNull)
    }.let(DeckJson::canonicalElement))

    fun capture(from: PlayState, to: PlayState, nodeId: String, projectionNoteId: String? = null): GameplayDelta {
        val changes = mutableListOf<GameplayEntityDelta>()
        fun <T> entity(kind: String, id: String, index: Int, old: T?, next: T?, serializer: KSerializer<T>) {
            if (old == next) return
            val values = mutableListOf<GameplayValueDelta>()
            diff(emptyList(), old?.let { json.encodeToJsonElement(serializer, it) },
                next?.let { json.encodeToJsonElement(serializer, it) }, values)
            if (old is PlayContainer && next is PlayContainer && old.zone in setOf(PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.BATTLEFIELD) &&
                (old.zone != PlayZone.BATTLEFIELD || old.cards.toSet() != next.cards.toSet())) {
                values.removeAll { it.path == listOf("cards") }
                if (old.cards != next.cards) values += GameplayValueDelta(listOf("cards"),
                    JsonArray(old.cards.map(::JsonPrimitive)), JsonArray(next.cards.map(::JsonPrimitive)), unordered = true)
            }
            changes += GameplayEntityDelta(kind, id, index, values)
        }
        fun <T> members(kind: String, old: List<T>, next: List<T>, serializer: KSerializer<T>, id: (T) -> String) {
            if (old === next || old == next) return
            val left = old.associateBy(id); val right = next.associateBy(id)
            val sorted = old.map(id) == old.map(id).sorted() && next.map(id) == next.map(id).sorted()
            for (key in left.keys + right.keys) entity(kind, key,
                if (sorted) -1 else if (key in right) next.indexOf(right.getValue(key)) else old.indexOf(left.getValue(key)),
                left[key], right[key], serializer)
        }
        val a = from.board; val b = to.board
        members("card", a.cards, b.cards, PlayCard.serializer()) { it.id }
        members("player", a.players, b.players, PlayPlayer.serializer()) { it.accountId }
        members("container", a.containers, b.containers, PlayContainer.serializer()) { it.id }
        members("contents", a.contentsDisplays, b.contentsDisplays, ContentsDisplay.serializer()) { it.id }
        members("link", a.links, b.links, PlayLink.serializer()) { it.id }
        members("effect", a.stackEffects, b.stackEffects, StackEffect.serializer()) { it.id }
        members("projectionLink", a.projectionLinks, b.projectionLinks, ProjectionLink.serializer()) { it.id }
        members("definition", from.definitions, to.definitions, SubmittedDefinition.serializer()) { it.digest }
        entity("turn", "", 0, a.turn, b.turn, TurnState.serializer())
        // Notes address an occurrence, not its current pair/role. Keep their sparse path stable
        // when an unrelated combat placement is selectively undone before replaying the note.
        if (projectionNoteId != null) entity("occurrence", projectionNoteId, 0,
            CombatStackRules.occurrences(a.combat).singleOrNull { it.id == projectionNoteId },
            CombatStackRules.occurrences(b.combat).singleOrNull { it.id == projectionNoteId }, CombatOccurrence.serializer())
        else entity("combat", "", 0, a.combat, b.combat, CombatArea.serializer())
        entity("result", "", 0, a.resultSuggestion, b.resultSuggestion, ResultSuggestion.serializer())
        entity("attention", "", 0, JsonPrimitive(a.attentionHolder), JsonPrimitive(b.attentionHolder), JsonElement.serializer())
        entity("marks", "", 0, marks(a), marks(b), JsonElement.serializer())
        if (from.annotations !== to.annotations) {
            entity("comment", nodeId, 0, from.annotations.commentsById[nodeId], to.annotations.commentsById[nodeId], RecordedEventComment.serializer())
            entity("ping", nodeId, 0, from.annotations.pingsById[nodeId], to.annotations.pingsById[nodeId], RecordedPingChange.serializer())
            to.annotations.pingsById[nodeId]?.recording?.groupId?.let { group ->
                entity("ping-ordinal", group, 0, from.annotations.lastOrdinals[group]?.let(::JsonPrimitive),
                    to.annotations.lastOrdinals[group]?.let(::JsonPrimitive), JsonElement.serializer())
            }
        }
        val piles = mutableListOf<GameplayPileDelta>()
        val relocated = changes.filter { it.kind == "card" && it.values.any { value ->
            value.path.isEmpty() || value.path.first() in setOf("placement", "containerId") } }.map { it.id }.toSet()
        for (container in a.containers.filter { it.zone == PlayZone.BATTLEFIELD }) {
            val nextContainer = b.containers.single { it.id == container.id }
            if (container.cards == nextContainer.cards && (container.cards + nextContainer.cards).none { it in relocated }) continue
            val placements = (a.cards + b.cards).filter { it.containerId == container.id }.mapNotNull { it.placement }.distinct()
            for (placement in placements) {
                val old = GameplayRules.members(a, container, placement)
                val next = GameplayRules.members(b, nextContainer, placement)
                if (old != next) piles += GameplayPileDelta(container.id, placement, old, next)
            }
        }
        return GameplayDelta(changes, piles = piles)
    }

    private fun diff(path: List<String>, from: JsonElement?, to: JsonElement?, out: MutableList<GameplayValueDelta>) {
        if (from == to) return
        if (from is JsonObject && to is JsonObject) {
            // Capture new events independently of map/JSONB key iteration. Never sort retained deltas.
            for (key in (from.keys + to.keys).sorted()) diff(path + key, from[key], to[key], out)
        } else if (from is JsonArray && to is JsonArray) {
            // Stable entity IDs avoid positional reads when another independent entity is removed.
            fun keyed(array: JsonArray) = array.isNotEmpty() && array.all { it is JsonObject && it["id"] is JsonPrimitive }
            if (keyed(from) && keyed(to) && from.map { it.jsonObject["id"] } == to.map { it.jsonObject["id"] }) {
                for (index in from.indices) diff(path + "@${from[index].jsonObject.getValue("id").jsonPrimitive.content}", from[index], to[index], out)
            } else {
                val prefix = from.zip(to).takeWhile { it.first == it.second }.size
                var suffix = 0
                while (suffix < minOf(from.size, to.size) - prefix && from[from.lastIndex - suffix] == to[to.lastIndex - suffix]) suffix++
                out += GameplayValueDelta(path, JsonArray(from.subList(prefix, from.size - suffix)),
                    JsonArray(to.subList(prefix, to.size - suffix)), true,
                    from.getOrNull(prefix - 1), from.getOrNull(from.size - suffix))
            }
        } else out += GameplayValueDelta(path, stored(from), stored(to))
    }

    private fun marks(board: BoardState) = JsonObject(board.stackTextMarks.mapValues {
        json.encodeToJsonElement(StackTextMarksSerializer, it.value)
    })

    private class Mismatch : RuntimeException()
    private fun patch(value: JsonElement?, edit: GameplayValueDelta, reverse: Boolean, depth: Int = 0,
        relaxedNext: Boolean = false): JsonElement? {
        if (depth < edit.path.size) {
            val key = edit.path[depth]
            if (value is JsonArray && key.startsWith("@")) {
                val index = value.indexOfFirst { (it as? JsonObject)?.get("id")?.jsonPrimitive?.content == key.drop(1) }
                if (index < 0) throw Mismatch()
                return JsonArray(value.toMutableList().also { it[index] = patch(it[index], edit, reverse, depth + 1, relaxedNext) ?: throw Mismatch() })
            }
            val obj = value as? JsonObject ?: throw Mismatch()
            val next = patch(obj[key], edit, reverse, depth + 1, relaxedNext)
            return JsonObject(obj.toMutableMap().also { if (next == null) it.remove(key) else it[key] = next })
        }
        val from = restored(if (reverse) edit.to else edit.from)
        val to = restored(if (reverse) edit.from else edit.to)
        if (edit.unordered) {
            val array = value as? JsonArray ?: throw Mismatch()
            val old = from as? JsonArray ?: throw Mismatch()
            val next = to as? JsonArray ?: throw Mismatch()
            val removed = old - next.toSet(); val added = next - old.toSet()
            if (!array.containsAll(removed) || added.any { it in array }) throw Mismatch()
            return if (array == old) next else JsonArray(((array - removed.toSet()) + added).sortedBy { it.jsonPrimitive.content })
        }
        if (!edit.sequence) {
            if (value != from) throw Mismatch()
            return to
        }
        val array = value as? JsonArray ?: throw Mismatch()
        val removed = from as? JsonArray ?: throw Mismatch()
        val added = to as? JsonArray ?: throw Mismatch()
        val start = if (edit.after == null) 0 else array.indexOf(edit.after).takeIf { it >= 0 }?.plus(1) ?: throw Mismatch()
        val end = start + removed.size
        if (end > array.size || array.subList(start, end) != removed || (!relaxedNext && array.getOrNull(end) != edit.before)) throw Mismatch()
        return JsonArray(array.take(start) + added + array.drop(end))
    }

    fun apply(state: PlayState, delta: GameplayDelta, reverse: Boolean, mutation: GameplayMutation? = null,
        work: GameplayWork? = null): PlayState? = try {
        work?.let { it.deltaApplications++ }
        for (pile in delta.piles) {
            val container = state.board.containers.singleOrNull { it.id == pile.containerId } ?: throw Mismatch()
            if (GameplayRules.members(state.board, container, pile.placement) != (if (reverse) pile.to else pile.from)) throw Mismatch()
        }
        var result = if (reverse) state else LibraryKnowledge.applyReseeds(state, delta.libraryReseeds, false) ?: throw Mismatch()
        val rebasedBattlefields = mutableSetOf<String>()
        for (entity in if (reverse) delta.entities.asReversed() else delta.entities) {
            fun <T> value(old: T?, serializer: KSerializer<T>): T? {
                var encoded = old?.let { json.encodeToJsonElement(serializer, it) }
                for (edit in if (reverse) entity.values.asReversed() else entity.values) {
                    val knownDraw = mutation != null && GameplayConcerns.knownTopDraw(mutation) && entity.kind == "container" &&
                        entity.id == (mutation.action as PlayAction.MoveCard).source.containerId
                    if (knownDraw && edit.path == listOf("knownTop")) {
                        val obj = encoded as? JsonObject ?: throw Mismatch()
                        val ids = (obj["knownTop"] as? JsonArray).orEmpty()
                        val card = JsonPrimitive(mutation.action.cardId)
                        if ((!reverse && ids.firstOrNull() != card) || (reverse && card in ids)) throw Mismatch()
                        val next = if (reverse) listOf(card) + ids else ids.drop(1)
                        encoded = JsonObject(if (next.isEmpty()) obj - "knownTop" else obj + ("knownTop" to JsonArray(next)))
                        continue
                    }
                    val ownerIndependent = mutation?.transition?.concerns != null && mutation.action is PlayAction.MoveCard &&
                        entity.kind == "card" && mutation.transition.tokens.any { it.from?.card?.id == entity.id && it.to == null }
                    val adjusted = if (ownerIndependent && !reverse && edit.path.isEmpty() && encoded is JsonObject && edit.from is JsonObject)
                        edit.copy(from = JsonObject(edit.from + ("owner" to encoded.getValue("owner")))) else edit
                    val relaxed = knownDraw && edit.path == listOf("cards")
                    encoded = patch(encoded, adjusted, reverse, relaxedNext = relaxed)
                    if (ownerIndependent && reverse && edit.path.isEmpty() && encoded is JsonObject) {
                        mutation!!.bookkeeping?.deletedTokenOwners?.get(entity.id)?.let { owner ->
                            encoded = JsonObject(encoded + ("owner" to JsonPrimitive(owner)))
                        }
                    }
                }
                return encoded?.let { json.decodeFromJsonElement(serializer, it) }
            }
            fun <T> members(old: List<T>, serializer: KSerializer<T>, id: (T) -> String): List<T> {
                val index = old.indexOfFirst { id(it) == entity.id }
                val next = value(old.getOrNull(index), serializer)
                return old.toMutableList().also {
                    if (index >= 0) { if (next == null) it.removeAt(index) else it[index] = next }
                    else if (next != null) {
                        if (entity.index == -1) { it.add(next); it.sortBy(id) }
                        else it.add(entity.index.coerceIn(0, it.size), next)
                    }
                }
            }
            val b = result.board
            result = when (entity.kind) {
                "card" -> result.copy(board = b.copy(cards = members(b.cards, PlayCard.serializer()) { it.id }))
                "player" -> result.copy(board = b.copy(players = members(b.players, PlayPlayer.serializer()) { it.accountId }))
                "container" -> {
                    val current = b.containers.singleOrNull { it.id == entity.id }
                    val edit = entity.values.singleOrNull { it.path == listOf("cards") && it.unordered }
                    val expected = edit?.let { if (reverse) it.to else it.from }
                    val rebased = edit != null && current != null && JsonArray(current.cards.map(::JsonPrimitive)) != expected
                    if (rebased && current?.zone == PlayZone.BATTLEFIELD) rebasedBattlefields += entity.id
                    val containers = members(b.containers, PlayContainer.serializer()) { it.id }
                    result.copy(board = b.copy(containers = containers))
                }
                "contents" -> result.copy(board = b.copy(contentsDisplays = members(b.contentsDisplays, ContentsDisplay.serializer()) { it.id }))
                "link" -> result.copy(board = b.copy(links = members(b.links, PlayLink.serializer()) { it.id }))
                "effect" -> result.copy(board = b.copy(stackEffects = members(b.stackEffects, StackEffect.serializer()) { it.id }))
                "occurrence" -> {
                    val current = CombatStackRules.occurrences(b.combat).singleOrNull { it.id == entity.id } ?: throw Mismatch()
                    val next = value(current, CombatOccurrence.serializer()) ?: throw Mismatch()
                    result.copy(board = b.copy(combat = b.combat!!.let { area -> area.copy(pairs = area.pairs.map { pair ->
                        pair.copy(attackers = pair.attackers.map { if (it.id == entity.id) next else it },
                            blockers = pair.blockers.map { if (it.id == entity.id) next else it })
                    }) }))
                }
                "projectionLink" -> result.copy(board = b.copy(projectionLinks = members(b.projectionLinks, ProjectionLink.serializer()) { it.id }))
                "definition" -> result.copy(definitions = members(result.definitions, SubmittedDefinition.serializer()) { it.digest })
                "turn" -> result.copy(board = b.copy(turn = value(b.turn, TurnState.serializer()) ?: throw Mismatch()))
                "combat" -> result.copy(board = b.copy(combat = value(b.combat, CombatArea.serializer())))
                "result" -> result.copy(board = b.copy(resultSuggestion = value(b.resultSuggestion, ResultSuggestion.serializer())))
                "attention" -> result.copy(board = b.copy(attentionHolder = value(JsonPrimitive(b.attentionHolder), JsonElement.serializer())?.jsonPrimitive?.contentOrNull))
                "marks" -> result.copy(board = b.copy(stackTextMarks = value(marks(b), JsonElement.serializer())!!.jsonObject
                    .mapValues { json.decodeFromJsonElement(StackTextMarksSerializer, it.value) }))
                "comment" -> result.copy(annotations = result.annotations.comment(entity.id,
                    value(result.annotations.commentsById[entity.id], RecordedEventComment.serializer())))
                "ping" -> result.copy(annotations = result.annotations.ping(entity.id,
                    value(result.annotations.pingsById[entity.id], RecordedPingChange.serializer())))
                "ping-ordinal" -> result.copy(annotations = result.annotations.ordinal(entity.id,
                    value(result.annotations.lastOrdinals[entity.id]?.let(::JsonPrimitive), JsonElement.serializer())?.jsonPrimitive?.long))
                else -> throw Mismatch()
            }
        }
        if (rebasedBattlefields.isNotEmpty()) {
            val board = result.board
            val placements = board.cards.associate { it.id to it.placement }
            val containers = board.containers.map { container ->
                if (container.id !in rebasedBattlefields) container else {
                    val orders = delta.piles.filter { it.containerId == container.id }.associate { it.placement to (if (reverse) it.from else it.to) }
                    val stable = container.cards.withIndex().associate { it.value to it.index }
                    container.copy(cards = container.cards.sortedWith(compareBy<String> { placements[it]?.pileId }.thenBy {
                        orders[placements[it]]?.indexOf(it)?.takeIf { index -> index >= 0 } ?: stable.getValue(it)
                    }))
                }
            }
            result = result.copy(board = board.copy(containers = containers))
        }
        if (reverse) LibraryKnowledge.applyReseeds(result, delta.libraryReseeds, true) else result
    } catch (_: Mismatch) { null } catch (_: IllegalArgumentException) { null }
}
