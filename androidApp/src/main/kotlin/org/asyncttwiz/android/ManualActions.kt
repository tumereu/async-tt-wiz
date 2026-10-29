package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*

/** Retain this personal UI holder with the table controller. It never holds an authoritative board. */
class ManualActions {
    var scope by mutableStateOf<ManualScope?>(null)
        private set
    var editor by mutableStateOf<ValueDraft?>(null)
        private set
    var inspection by mutableStateOf<ManualInspection?>(null)
        private set
    /** Open keyword toggles: false edits the card note, true the EOT note. */
    var keywords by mutableStateOf<Boolean?>(null)
        private set

    fun cards(state: PlayState, contextId: String, selectedSourceIds: List<String>, noteViewId: String? = null) {
        val ids = if (contextId in selectedSourceIds) selectedSourceIds.distinct() else listOf(contextId)
        scope = ManualScope(state.gameId, state.id, contextId, ids, noteViewId = noteViewId)
        editor = null; keywords = null
    }
    fun projectionNote(state: PlayState, id: String, viewer: String, selectedViewIds: List<String> = listOf(id)) {
        scope = ManualScope(state.gameId, state.id, id, emptyList(), noteViewId = id,
            noteViewIds = if (id in selectedViewIds) selectedViewIds.distinct() else listOf(id))
        edit(ManualValue(ManualValueKind.NOTE), state, viewer)
    }
    fun player(state: PlayState, accountId: String, counters: Boolean = false) {
        scope = ManualScope(state.gameId, state.id, accountId, listOf(accountId), player = true, playerCounters = counters)
        editor = null; keywords = null
    }
    fun edit(field: ManualValue, state: PlayState, viewer: String) {
        val bound = scope ?: return
        if (!bound.usable(state)) return
        // A hidden card's stored values (often printed defaults) are never shown: its editor starts empty (blind edit).
        editor = ValueDraft(bound, field, bound.value(state, field, viewer).orEmpty())
        keywords = null
    }
    fun keywords(eot: Boolean, state: PlayState) {
        val bound = scope ?: return
        if (!bound.usable(state) || bound.player) return
        editor = null; keywords = eot
    }
    fun cancelEditor() { editor = null }
    fun close() { scope = null; editor = null; keywords = null }
    fun clear() { close(); inspection = null }
    fun closeInspection() { inspection = null }

    fun inspect(state: PlayState, viewer: String) {
        val bound = scope ?: return
        val card = state.board.cards.singleOrNull { it.id == bound.contextId } ?: return
        val faces = ManualDisclosure.permittedFaces(state, card, viewer, inspecting = true)
        inspection = ManualInspection(state.gameId, state.id, card.id, faces.map { it.faceId })
    }

    /** I14 calls this only for an actual accepted/presented disclosure, NOT submit's Boolean receipt. */
    fun inspected(state: PlayState, viewer: String, disclosure: GameplayDisclosure) {
        if (viewer !in disclosure.viewers) return
        val card = state.board.cards.singleOrNull { it.id == disclosure.cardId } ?: return
        if (card.containerId != disclosure.containerId) return
        if (state.definitions.none { it.digest == card.definition && it.definition.faces.any { face -> face.faceId == disclosure.faceId } }) return
        inspection = ManualInspection(state.gameId, state.id, card.id, listOf(disclosure.faceId), explicit = true)
    }

    fun valueActions(state: PlayState, viewer: String): List<PlayAction> {
        val draft = editor ?: return emptyList()
        if (!draft.scope.usable(state) || !draft.valid) return emptyList()
        return draft.scope.actions(state, draft.field, draft.text, viewer)
    }

    fun submitValue(state: PlayState, viewer: String, submit: (PlayAction) -> Boolean): Boolean {
        val actions = valueActions(state, viewer)
        if (actions.isEmpty()) return false
        var allSubmitted = true
        for (action in actions) if (!submit(action)) allSubmitted = false
        // A failed enqueue keeps the input. Already submitted siblings are not rolled back.
        if (allSubmitted) editor = null
        return allSubmitted
    }
}

data class ManualInspection(val gameId: String, val playId: String, val cardId: String,
    val faceIds: List<String>, val explicit: Boolean = false, val snapshot: PlayCard? = null,
    val provisional: Boolean = false, val stackEntryId: String? = null)

data class ManualScope(val gameId: String, val playId: String, val contextId: String,
    val targetIds: List<String>, val player: Boolean = false, val noteViewId: String? = null,
    val playerCounters: Boolean = false, val noteViewIds: List<String> = emptyList()) {
    fun usable(state: PlayState): Boolean = gameId == state.gameId && playId == state.id &&
        if (player) state.board.players.any { it.accountId == contextId }
        else if (noteViewId != null) CombatStackRules.projectionNote(state.board, noteViewId) != null
        else state.board.cards.any { it.id == contextId }

    fun value(state: PlayState, field: ManualValue, viewer: String): String? {
        if (!usable(state)) return null
        if (field.kind == ManualValueKind.NOTE && noteViewId != null)
            return CombatStackRules.projectionNote(state.board, noteViewId)
        if (player) {
            val target = state.board.players.single { it.accountId == contextId }
            return if (field.kind == ManualValueKind.LIFE) target.life else target.counters[field.color]
        }
        val card = state.board.cards.single { it.id == contextId }
        if (cardValuesHidden(state.board, card, viewer)) return null
        return when (field.kind) {
            ManualValueKind.DAMAGE -> card.damage
            ManualValueKind.EOT_CORNER -> card.eotCorner
            ManualValueKind.EOT_NOTE -> card.eotNote
            ManualValueKind.CORNER -> card.corner
            ManualValueKind.NOTE -> card.note
            ManualValueKind.COUNTER -> card.counters[field.color]
            ManualValueKind.LIFE -> null
        }
    }

    fun actions(state: PlayState, field: ManualValue, text: String, viewer: String): List<PlayAction> {
        if (!usable(state)) return emptyList()
        if (field.kind == ManualValueKind.NOTE && noteViewId != null)
            return noteViewIds.ifEmpty { listOf(noteViewId) }.filter { CombatStackRules.projectionNote(state.board, it) != null }
                .map { PlayAction.SetProjectionNote(it, text) }
        return targetIds.mapNotNull { id ->
            if (field.kind in listOf(ManualValueKind.DAMAGE, ManualValueKind.EOT_CORNER, ManualValueKind.EOT_NOTE) &&
                state.board.cards.singleOrNull { it.id == id }?.let { card ->
                    state.board.containers.any { it.id == card.containerId && it.zone in setOf(PlayZone.BATTLEFIELD, PlayZone.STACK) }
                } != true) return@mapNotNull null
            if (player) {
                if (state.board.players.none { it.accountId == id }) null
                else if (field.kind == ManualValueKind.LIFE) PlayAction.SetLife(id, text)
                else PlayAction.SetCounter(CounterTarget.PLAYER, id, field.color!!, text)
            } else {
                val card = state.board.cards.singleOrNull { it.id == id }
                if (card == null) null else when (field.kind) {
                    ManualValueKind.DAMAGE -> PlayAction.SetDamage(id, text)
                    ManualValueKind.EOT_CORNER -> PlayAction.SetEotCorner(id, text)
                    ManualValueKind.EOT_NOTE -> PlayAction.SetEotNote(id, text)
                    ManualValueKind.CORNER -> PlayAction.SetCorner(id, text)
                    ManualValueKind.NOTE -> PlayAction.SetNote(id, text)
                    ManualValueKind.COUNTER -> PlayAction.SetCounter(CounterTarget.CARD, id, field.color!!, text)
                    ManualValueKind.LIFE -> null
                }
            }
        }
    }

    /** Keyword toggles edit Battlefield/Stack cards only, like the EOT marks. */
    fun keywordCards(state: PlayState): List<PlayCard> =
        if (!usable(state) || player) emptyList() else targetIds.mapNotNull { id -> state.board.cards.singleOrNull { it.id == id } }
            .filter { card -> state.board.containers.any { it.id == card.containerId && it.zone in setOf(PlayZone.BATTLEFIELD, PlayZone.STACK) } }

    /** Each captured source emits one ordinary request; inapplicable numeric targets are skipped. */
    fun cardActions(state: PlayState, action: (PlayCard) -> PlayAction?): List<PlayAction> =
        if (!usable(state) || player) emptyList() else targetIds.mapNotNull { id ->
            state.board.cards.singleOrNull { it.id == id }?.let(action)
        }
}

enum class ManualValueKind { LIFE, CORNER, COUNTER, NOTE, DAMAGE, EOT_CORNER, EOT_NOTE }
data class ManualValue(val kind: ManualValueKind, val color: CounterColor? = null) {
    init { require((kind == ManualValueKind.COUNTER) == (color != null)) }
    val label: String get() = when (kind) {
        ManualValueKind.EOT_CORNER -> "EOT P/T"
        ManualValueKind.EOT_NOTE -> "EOT note"
        else -> if (color != null) "${color.name.lowercase()} counter" else kind.name.lowercase()
    }
    val multiline: Boolean get() = kind == ManualValueKind.NOTE || kind == ManualValueKind.EOT_NOTE
}

/** Shared gesture decision used by the production square; a recognized hold always consumes click. */
internal class ManualActionPress {
    var consumed = false
        private set
    var tooltip = false
        private set
    fun start() { consumed = false; tooltip = false }
    fun hold(assigned: (() -> Unit)?) {
        if (consumed) return
        consumed = true
        if (assigned != null) assigned() else tooltip = true
    }
    fun cancel() { consumed = true; tooltip = false }
    fun release(): Boolean {
        val click = !consumed
        tooltip = false
        consumed = true
        return click
    }
}

/** Source-compatible no-argument factory for the original manual-control callers/tests.
 * The quick/turn ActionPress(Boolean) retains its distinct terminal-result API. */
internal fun ActionPress(): ManualActionPress = ManualActionPress()

/** Actual reusable overlay tree. I16 supplies the same I14 gameplay callback, never a local reducer. */
@Composable
internal fun ManualActionsHost(state: PlayState, viewer: String, actions: ManualActions, canEdit: Boolean,
    landscape: Boolean, names: Map<String, String>,
    submitAll: ((List<PlayAction>) -> Unit)? = null, completeValue: (() -> Boolean)? = null,
    tools: List<MenuControl> = emptyList(), previewViewId: String? = null, inspect: (() -> Unit)? = null,
    editProjectionNote: (() -> Unit)? = null, submit: (PlayAction) -> Boolean) {
    val scope = actions.scope
    if (scope != null && scope.usable(state)) {
        if (scope.player) PlayerActions(state, scope, actions, canEdit, names, viewer, landscape, submit)
        else if (actions.editor == null && actions.keywords == null && state.board.cards.any { it.id == scope.contextId })
            CardActions(state, viewer, scope, actions, canEdit, names, submitAll, tools, previewViewId, inspect, editProjectionNote, submit)
        actions.keywords?.let { eot ->
            if (actions.editor == null) KeywordToggles(state, scope, eot, canEdit, actions::close) { requests ->
                if (requests.isEmpty()) Unit else if (submitAll != null) submitAll(requests) else requests.forEach { submit(it) }
            }
        }
        actions.editor?.let { draft ->
            ValueEditor(draft, scope.value(state, draft.field, viewer), canEdit && draft.scope.usable(state),
                onCancel = actions::cancelEditor, onSubmit = completeValue ?: { actions.submitValue(state, viewer, submit) },
                preview = { if (!scope.player) ManualCardPreview(state, viewer, scope.contextId, scope.targetIds.size, names, previewViewId) })
        }
    }
    actions.inspection?.let { inspection -> GameplayCardInspection(state, viewer, inspection, actions::closeInspection, names) }
}
