package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Rules v2 restricts TapEligible to the Battlefield; v1 retains off-Stack eligibility.
 * The sealed delta supplies written tuples; these selectors also record reads,
 * unchanged assignments and negative witnesses. No selector reads retained history. */
@Serializable data class ConcernSet(val fields: List<ConcernTuple>, val rulesVersion: Int = 1)
@Serializable data class ConcernTuple(val field: ConcernField, val before: JsonElement, val after: JsonElement)
@Serializable sealed interface ConcernField {
    @Serializable data class Written(val entity: Int, val value: Int) : ConcernField
    @Serializable data class Entity(val kind: String, val id: String, val path: List<String> = emptyList()) : ConcernField
    @Serializable data class Cell(val container: String, val position: BoardPosition) : ConcernField
    @Serializable data class Incident(val card: String) : ConcernField
    @Serializable data class LinkPair(val first: LinkEndpoint, val second: LinkEndpoint) : ConcernField
    @Serializable data class ProjectionPair(val first: String, val second: String) : ConcernField
    @Serializable data class Projection(val id: String) : ConcernField
    @Serializable data class ProjectionIncident(val id: String) : ConcernField
    @Serializable data class Controller(val id: String) : ConcernField
    @Serializable data class Live(val id: String) : ConcernField
    @Serializable data class TapEligible(val id: String) : ConcernField
    @Serializable data class EditEligible(val id: String, val stackAllowed: Boolean = false) : ConcernField
    @Serializable data class Slice(val container: String, val selection: ContentsSelection, val count: Int) : ConcernField
    @Serializable data class ShuffleSet(val container: String) : ConcernField
    @Serializable data class Top(val container: String, val card: String) : ConcernField
    @Serializable data class Options(val id: String) : ConcernField
    @Serializable data class StackMember(val id: String) : ConcernField
    @Serializable data class Exists(val endpoint: LinkEndpoint) : ConcernField
    @Serializable data class Occupied(val id: String) : ConcernField
    @Serializable data class DefinitionAvailable(val digest: String) : ConcernField
    @Serializable data object PanelSlot : ConcernField
    @Serializable data object Attention : ConcernField
}

internal object GameplayConcerns {
    private inline fun <reified T> encoded(value: T): JsonElement = GameWire.json.encodeToJsonElement(value)
    private fun at(value: JsonElement?, path: List<String>): JsonElement? {
        var result = value
        for (key in path) result = if (key.startsWith("@")) (result as? JsonArray)?.singleOrNull {
            (it as? JsonObject)?.get("id")?.jsonPrimitive?.content == key.drop(1)
        } else (result as? JsonObject)?.get(key)
        return result
    }

    fun knownTopDraw(mutation: GameplayMutation): Boolean = mutation.transition.concerns?.fields.orEmpty()
        .any { it.field is ConcernField.Top }

    private fun independentOwner(mutation: GameplayMutation, entity: GameplayEntityDelta): Boolean =
        mutation.transition.concerns != null && mutation.action is PlayAction.MoveCard && entity.kind == "card" &&
            mutation.transition.tokens.any { it.from?.card?.id == entity.id && it.to == null }

    private fun written(state: PlayState, mutation: GameplayMutation, field: ConcernField.Written, after: Boolean): JsonElement {
        val entity = mutation.transition.delta!!.entities[field.entity]
        val edit = entity.values[field.value]
        var value = at(GameplayDeltas.entityValue(state, entity.kind, entity.id), edit.path)
        if (knownTopDraw(mutation) && entity.kind == "container" &&
            entity.id == (mutation.action as PlayAction.MoveCard).source.containerId && edit.path == listOf("knownTop"))
            return JsonPrimitive((value as? JsonArray)?.firstOrNull()?.jsonPrimitive?.content == mutation.action.cardId)
        if (independentOwner(mutation, entity) && value is JsonObject) value = JsonObject(value - "owner")
        if (edit.unordered) {
            val old = (edit.from as JsonArray).toSet(); val next = (edit.to as JsonArray).toSet()
            val ids = (old - next) + (next - old)
            return JsonArray((value as? JsonArray).orEmpty().filter { it in ids }.sortedBy { it.toString() })
        }
        if (!edit.sequence) return value ?: JsonNull
        val array = value as? JsonArray ?: return JsonNull
        val count = ((if (after) edit.to else edit.from) as JsonArray).size
        val start = if (edit.after == null) 0 else array.indexOf(edit.after).takeIf { it >= 0 }?.plus(1) ?: return JsonNull
        if (start + count > array.size) return JsonNull
        val relaxed = knownTopDraw(mutation) && entity.kind == "container" &&
            entity.id == (mutation.action as PlayAction.MoveCard).source.containerId && edit.path in listOf(listOf("cards"), listOf("knownTop"))
        return buildJsonObject {
            put("members", JsonArray(array.subList(start, start + count)))
            put("after", array.getOrNull(start - 1) ?: JsonNull)
            if (!relaxed) put("before", array.getOrNull(start + count) ?: JsonNull)
        }
    }

    private fun read(state: PlayState, mutation: GameplayMutation, field: ConcernField, after: Boolean): JsonElement {
        val b = state.board
        fun container(id: String) = b.containers.single { it.id == id }
        return when (field) {
            is ConcernField.Written -> written(state, mutation, field, after)
            is ConcernField.Entity -> {
                val value = at(GameplayDeltas.entityValue(state, field.kind, field.id), field.path)
                // A known-top draw concerns only the taken card's knowledge edge, not the
                // untouched known cards beneath it. Still witness its removal after the draw.
                if (knownTopDraw(mutation) && field.kind == "container" &&
                    field.id == (mutation.action as PlayAction.MoveCard).source.containerId && field.path == listOf("knownTop")) {
                    val ids = (value as? JsonArray).orEmpty()
                    val card = JsonPrimitive(mutation.action.cardId)
                    encoded(if (after) card !in ids else ids.firstOrNull() == card)
                } else value ?: JsonNull
            }
            is ConcernField.Cell -> encoded(b.cards.filter { it.containerId == field.container && it.placement?.position == field.position }
                .map { it.id to it.placement!!.pileId }.sortedBy { it.first })
            is ConcernField.Incident -> buildJsonObject {
                put("links", encoded(b.links.filter { it.first.id == field.card || it.second.id == field.card }.sortedBy { it.id }))
                put("combat", encoded(CombatStackRules.occurrences(b.combat).filter { it.sourceId == field.card }))
                put("effects", encoded(b.stackEffects.filter { it.sourceId == field.card }.sortedBy { it.id }))
                put("panels", encoded(b.contentsDisplays.filter { field.card in it.cardIds }.sortedBy { it.id }))
                put("marks", encoded(b.stackTextMarks[field.card]))
            }
            is ConcernField.LinkPair -> encoded(b.links.filter { TokenLinkRules.pair(it.first, it.second) == TokenLinkRules.pair(field.first, field.second) }.sortedBy { it.id })
            is ConcernField.ProjectionPair -> encoded(b.projectionLinks.filter { CombatStackRules.projectionPair(it.first, it.second) == CombatStackRules.projectionPair(field.first, field.second) }.sortedBy { it.id })
            is ConcernField.Projection -> buildJsonObject {
                put("effect", encoded(b.stackEffects.singleOrNull { it.id == field.id }))
                put("occurrence", encoded(CombatStackRules.occurrences(b.combat).singleOrNull { it.id == field.id }))
                put("location", encoded(b.combat?.let { CombatStackRules.location(it, field.id) }))
            }
            is ConcernField.ProjectionIncident -> buildJsonObject {
                put("links", encoded(b.projectionLinks.filter { it.first == field.id || it.second == field.id }.sortedBy { it.id }))
                put("marks", encoded(b.stackTextMarks[field.id]))
            }
            is ConcernField.Controller -> encoded(CombatStackRules.controller(b, field.id))
            is ConcernField.Live -> encoded(CombatStackRules.liveSource(b, field.id)?.id)
            is ConcernField.TapEligible -> encoded(b.cards.singleOrNull { it.id == field.id }?.let {
                val zone = container(it.containerId).zone
                if (mutation.transition.concerns!!.rulesVersion >= 2) zone == PlayZone.BATTLEFIELD else zone != PlayZone.STACK
            } == true)
            is ConcernField.EditEligible -> encoded(b.cards.singleOrNull { it.id == field.id }?.let {
                val zone = container(it.containerId).zone
                zone == PlayZone.BATTLEFIELD || field.stackAllowed && zone == PlayZone.STACK
            } == true)
            is ConcernField.Slice -> encoded(LibraryRules.slice(container(field.container).cards, field.selection, field.count).let {
                if (field.selection == ContentsSelection.ALL) it.sorted() else it })
            is ConcernField.ShuffleSet -> encoded(container(field.container).unknownMiddle.sorted())
            is ConcernField.Top -> encoded(container(field.container).cards.firstOrNull() == field.card)
            is ConcernField.Options -> encoded(StackText.options(state, field.id).map { listOf(it.text, it.faceId, it.paragraph?.toString()) })
            is ConcernField.StackMember -> encoded(b.containers.singleOrNull { it.zone == PlayZone.STACK }?.cards?.contains(field.id) == true)
            is ConcernField.Exists -> encoded(TokenLinkRules.exists(b, field.endpoint))
            is ConcernField.Occupied -> encoded(TokenLinkRules.occupied(b, field.id))
            is ConcernField.DefinitionAvailable -> encoded(state.definitions.any { it.digest == field.digest })
            ConcernField.PanelSlot -> encoded(b.contentsDisplays.map { it.id }.sorted())
            ConcernField.Attention -> encoded(listOf(b.attentionHolder, b.effectiveAttentionHolder))
        }
    }

    fun matches(state: PlayState, mutation: GameplayMutation, after: Boolean): Boolean {
        val concerns = mutation.transition.concerns ?: return true
        if (concerns.rulesVersion !in 1..3) return false
        val endpoint = if (after) state else LibraryKnowledge.applyReseeds(state, mutation.transition.delta!!.libraryReseeds, false) ?: return false
        return runCatching { concerns.fields.all { read(endpoint, mutation, it.field, after) == if (after) it.after else it.before } }.getOrDefault(false)
    }

    fun capture(from: PlayState, to: PlayState, mutation: GameplayMutation, delta: GameplayDelta): ConcernSet {
        val fields = linkedSetOf<ConcernField>()
        fun entity(kind: String, id: String, vararg path: String) { fields += ConcernField.Entity(kind, id, path.toList()) }
        fun card(id: String) = from.board.cards.single { it.id == id }
        fun definition(id: String) { fields += ConcernField.DefinitionAvailable(id) }
        fun knowledge(id: String) {
            entity("container", id, "knownTop"); entity("container", id, "knownBottom")
            from.board.cards.filter { it.containerId == id }.forEach { entity("card", it.id, "knownToAll") }
        }
        fun projection(id: String) { fields += ConcernField.Projection(id) }
        fun target(target: PingTarget) {
            when (target.kind) {
                PingTargetKind.CARD -> fields += ConcernField.Exists(LinkEndpoint(LinkEndpointKind.CARD, target.id!!))
                PingTargetKind.PLAYER -> fields += ConcernField.Exists(LinkEndpoint(LinkEndpointKind.PLAYER, target.id!!))
                PingTargetKind.OCCURRENCE -> projection(target.id!!)
                PingTargetKind.AREA -> if (from.board.combat?.id == target.id) entity("combat", "") else entity("container", target.id!!)
                PingTargetKind.LOCATION -> Unit
            }
        }
        when (val a = mutation.action) {
            is PlayAction.SetTapped -> fields += ConcernField.TapEligible(a.cardId)
            is PlayAction.MoveCard -> {
                val c = card(a.cardId)
                fields += ConcernField.Incident(c.id)
                // Entry defaults write these flags even when their values happen not to change.
                for (key in listOf("covered", "explicitlyCovered", "knownToAll", "tapped")) entity("card", c.id, key)
                val source = from.board.containers.single { it.id == a.source.containerId }
                if (mutation.transition.rulesVersion >= 13 && to.board.cards.any { it.id == c.id }) {
                    val target = from.board.containers.single { it.id == a.destination.containerId }
                    if (source.id != target.id && (source.zone != PlayZone.BATTLEFIELD || target.zone != PlayZone.BATTLEFIELD))
                        entity("card", c.id, "note")
                    if (source.zone == PlayZone.BATTLEFIELD && target.zone != PlayZone.BATTLEFIELD) {
                        for (key in listOf("corner", "counters", "faceId", "owner")) entity("card", c.id, key)
                        definition(c.definition)
                    }
                }
                if (source.zone == PlayZone.LIBRARY) {
                    // Removal/repositioning normalizes both edges even when they stay empty.
                    // Retained replay must not skip cleanup made necessary by an earlier undo.
                    entity("container", source.id, "knownTop"); entity("container", source.id, "knownBottom")
                    if (a.source.edge == MoveSourceEdge.TOP && c.id in source.knownTop && a.destination.containerId != source.id)
                        fields += ConcernField.Top(source.id, c.id)
                    else if (c.id in source.unknownMiddle && (a.source.edge != MoveSourceEdge.MEMBER || a.source.bottomSlice.isNotEmpty()))
                        fields += ConcernField.ShuffleSet(source.id)
                    if (a.source.bottomSlice.isNotEmpty()) fields += ConcernField.Slice(source.id, ContentsSelection.BOTTOM, a.source.bottomSlice.size)
                    if (a.source.edge == MoveSourceEdge.BOTTOM) fields += ConcernField.Slice(source.id, ContentsSelection.BOTTOM, 1)
                }
                if (a.destination.insertion == MoveInsertion.EMPTY) fields += ConcernField.Cell(a.destination.containerId, a.destination.position!!)
                a.removeStackEntryId?.let { projection(it); fields += ConcernField.Live(it) }
            }
            is PlayAction.CreateToken, is PlayAction.CloneToken -> {
                val token = mutation.transition.tokens.single().to!!.card
                definition(token.definition)
                fields += ConcernField.Cell(token.containerId, token.placement!!.position)
                fields += ConcernField.Occupied(token.id); fields += ConcernField.Occupied(token.placement.pileId)
            }
            is PlayAction.RemoveToken -> fields += ConcernField.Incident(a.cardId)
            is PlayAction.Link -> {
                fields += ConcernField.LinkPair(a.first, a.second)
                fields += ConcernField.Exists(a.first); fields += ConcernField.Exists(a.second)
            }
            is PlayAction.RemoveLink -> Unit
            is PlayAction.LinkProjections -> {
                fields += ConcernField.ProjectionPair(a.first.occurrenceId, a.second.occurrenceId)
                projection(a.first.occurrenceId); projection(a.second.occurrenceId)
            }
            is PlayAction.RemoveProjectionLink -> Unit
            is PlayAction.ShuffleLibrary -> knowledge(a.containerId)
            is PlayAction.Mulligan -> {
                knowledge(a.libraryId)
                entity("container", a.libraryId, "cards"); entity("container", a.handId, "cards")
                val ids = from.board.containers.single { it.id == a.libraryId }.cards + a.displayedHand
                val returned = a.displayedHand.toSet()
                val drawn = to.board.containers.single { it.id == a.handId }.cards.toSet()
                ids.forEach { id ->
                    for (key in listOf("containerId", "covered", "explicitlyCovered", "tapped")) entity("card", id, key)
                    if (id in returned || id in drawn)
                        entity("card", id, "note")
                }
            }
            is PlayAction.SetProjectionNote -> projection(a.occurrenceId)
            is PlayAction.ShowContents -> {
                fields += ConcernField.PanelSlot
                fields += ConcernField.Slice(a.containerId, a.selection, a.count)
                mutation.transition.contents!!.to!!.cardIds.forEach { entity("card", it, "knownToAll") }
            }
            is PlayAction.DismissContents -> {
                val display = from.board.contentsDisplays.single { it.id == a.displayId }
                entity("contents", a.displayId); knowledge(display.containerId)
                display.cardIds.forEach { id ->
                    fields += ConcernField.Incident(id)
                    for (key in listOf("containerId", "faceId", "corner", "counters", "covered", "explicitlyCovered")) entity("card", id, key)
                    definition(card(id).definition)
                }
            }
            is PlayAction.RevealContentsCard -> { entity("contents", a.displayId); entity("card", a.cardId, "faceId") }
            is PlayAction.ReorderContents -> entity("contents", a.displayId)
            PlayAction.NextStep, PlayAction.EndTurn, is PlayAction.ChooseStep -> {
                fields += ConcernField.Attention; entity("turn", "")
            }
            PlayAction.PassAttention -> fields += ConcernField.Attention
            is PlayAction.Ping -> target(a.target)
            is PlayAction.Unping -> target(a.target)
            is PlayAction.EventComment -> Unit // retained text, not a dependency on ancestry identity
            is PlayAction.Combat -> {
                val source = when (val combat = a.action) {
                    is CombatAction.Declare -> combat.sourceId
                    is CombatAction.Place -> combat.sourceId
                    is CombatAction.Remove, is CombatAction.Close, CombatAction.Open -> null
                }
                source?.let { entity("card", it, "containerId"); fields += ConcernField.Incident(it) }
                // Combat edits depend on occurrence uniqueness, area and pair gaps.
                entity("combat", "")
            }
            is PlayAction.Stack -> when (val stack = a.action) {
                is StackAction.AddEffect -> {
                    fields += ConcernField.Live(stack.sourceId)
                    entity("card", stack.sourceId, "containerId"); entity("card", stack.sourceId, "owner")
                }
                is StackAction.CopyEffect -> {
                    projection(stack.entryId); fields += ConcernField.Live(stack.entryId)
                    fields += ConcernField.Controller(stack.entryId)
                }
                is StackAction.SetController -> { projection(stack.occurrenceId); fields += ConcernField.Controller(stack.occurrenceId) }
                is StackAction.MarkText -> { fields += ConcernField.Options(stack.entryId); fields += ConcernField.StackMember(stack.entryId) }
                is StackAction.ToggleTextMark -> { fields += ConcernField.Options(stack.entryId); fields += ConcernField.StackMember(stack.entryId) }
                is StackAction.RemoveEffect -> projection(stack.occurrenceId)
                is StackAction.ReorderEffect -> projection(stack.occurrenceId)
            }
            is PlayAction.SetFace -> definition(card(a.cardId).definition)
            is PlayAction.ResetCorner -> { definition(card(a.cardId).definition); entity("card", a.cardId, "faceId") }
            is PlayAction.InspectFace -> definition(card(a.cardId).definition)
            is PlayAction.SetCovered -> {
                entity("card", a.cardId, "covered"); entity("card", a.cardId, "explicitlyCovered")
            }
            is PlayAction.SetOwner, is PlayAction.SetNote, is PlayAction.SetCorner, is PlayAction.AdjustCorner,
            is PlayAction.SetCounter, is PlayAction.AdjustCounter, is PlayAction.ClearCardCounter,
            is PlayAction.SetLife, is PlayAction.AdjustLife, is PlayAction.SetPlayerCounter, is PlayAction.AdjustPlayerCounter,
            is PlayAction.RollDie, is PlayAction.FlipCoin, is PlayAction.SuggestResult -> Unit
        }
        if (mutation.transition.rulesVersion >= 13) {
            val edit = when (val a = mutation.action) {
                is PlayAction.SetNote -> ConcernField.EditEligible(a.cardId, stackAllowed = true)
                is PlayAction.SetOwner -> ConcernField.EditEligible(a.cardId)
                is PlayAction.SetCorner -> ConcernField.EditEligible(a.cardId)
                is PlayAction.AdjustCorner -> ConcernField.EditEligible(a.cardId)
                is PlayAction.ResetCorner -> ConcernField.EditEligible(a.cardId)
                is PlayAction.ClearCardCounter -> ConcernField.EditEligible(a.cardId)
                is PlayAction.SetCounter -> if (a.target == CounterTarget.CARD) ConcernField.EditEligible(a.targetId) else null
                is PlayAction.AdjustCounter -> if (a.target == CounterTarget.CARD) ConcernField.EditEligible(a.targetId) else null
                else -> null
            }
            edit?.let { fields += it }
        }
        for (read in mutation.transition.reads) when (read.field) {
            GameplayField.FACE -> entity("card", read.targetId, "faceId")
            GameplayField.COVERED -> entity("card", read.targetId, "covered")
            GameplayField.EXPLICITLY_COVERED -> entity("card", read.targetId, "explicitlyCovered")
            GameplayField.CONTAINER -> entity("card", read.targetId, "containerId")
            GameplayField.TURN_PLAYER -> entity("turn", "", "accountId")
            else -> error("Unrecorded read field: ${read.field}")
        }
        mutation.transition.combatStack?.let { effects ->
            (effects.effects.filter { it.to == null }.map { it.id } + effects.occurrences.filter { it.to == null }.map { it.id })
                .forEach { fields += ConcernField.ProjectionIncident(it) }
        }
        delta.entities.forEachIndexed { i, e -> e.values.indices.forEach { fields += ConcernField.Written(i, it) } }
        // Install selectors first: known-order source edits deliberately do not bind the exposed next card.
        val version = if (mutation.transition.rulesVersion >= 13) 3 else if (mutation.action is PlayAction.SetTapped) 2 else 1
        val selectors = ConcernSet(fields.map { ConcernTuple(it, JsonNull, JsonNull) }, rulesVersion = version)
        val recorded = mutation.copy(transition = mutation.transition.copy(delta = delta, concerns = selectors))
        return ConcernSet(fields.map { ConcernTuple(it, read(from, recorded, it, false), read(to, recorded, it, true)) }, rulesVersion = version)
    }
}
