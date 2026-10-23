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

    fun cards(state: PlayState, contextId: String, selectedSourceIds: List<String>) {
        val ids = if (contextId in selectedSourceIds) selectedSourceIds.distinct() else listOf(contextId)
        scope = ManualScope(state.gameId, state.id, contextId, ids)
        editor = null
    }
    fun player(state: PlayState, accountId: String) {
        scope = ManualScope(state.gameId, state.id, accountId, listOf(accountId), player = true)
        editor = null
    }
    fun edit(field: ManualValue, state: PlayState, viewer: String) {
        val bound = scope ?: return
        if (!bound.usable(state)) return
        editor = ValueDraft(bound, field, bound.value(state, field, viewer).orEmpty())
    }
    fun cancelEditor() { editor = null }
    fun close() { scope = null; editor = null }
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

    fun submitValue(state: PlayState, viewer: String, submit: (PlayAction) -> Boolean): Boolean {
        val draft = editor ?: return false
        if (!draft.scope.usable(state) || !draft.valid) return false
        val actions = draft.scope.actions(state, draft.field, draft.text, viewer)
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
    val provisional: Boolean = false)

data class ManualScope(val gameId: String, val playId: String, val contextId: String,
    val targetIds: List<String>, val player: Boolean = false) {
    fun usable(state: PlayState): Boolean = gameId == state.gameId && playId == state.id &&
        if (player) state.board.players.any { it.accountId == contextId }
        else state.board.cards.any { it.id == contextId }

    fun value(state: PlayState, field: ManualValue, viewer: String): String? {
        if (!usable(state)) return null
        if (player) {
            val target = state.board.players.single { it.accountId == contextId }
            return if (field.kind == ManualValueKind.LIFE) target.life else target.counters[field.color]
        }
        val card = state.board.cards.single { it.id == contextId }
        return when (field.kind) {
            ManualValueKind.CORNER -> card.corner
            ManualValueKind.NOTE -> card.note
            ManualValueKind.COUNTER -> card.counters[field.color]
            ManualValueKind.LIFE -> null
        }
    }

    fun actions(state: PlayState, field: ManualValue, text: String, viewer: String): List<PlayAction> {
        if (!usable(state)) return emptyList()
        return targetIds.mapNotNull { id ->
            if (player) {
                if (state.board.players.none { it.accountId == id }) null
                else if (field.kind == ManualValueKind.LIFE) PlayAction.SetLife(id, text)
                else PlayAction.SetCounter(CounterTarget.PLAYER, id, field.color!!, text)
            } else {
                if (state.board.cards.none { it.id == id }) null else when (field.kind) {
                    ManualValueKind.CORNER -> PlayAction.SetCorner(id, text)
                    ManualValueKind.NOTE -> PlayAction.SetNote(id, text)
                    ManualValueKind.COUNTER -> PlayAction.SetCounter(CounterTarget.CARD, id, field.color!!, text)
                    ManualValueKind.LIFE -> null
                }
            }
        }
    }

    /** Each captured source emits one ordinary request; inapplicable numeric targets are skipped. */
    fun cardActions(state: PlayState, action: (PlayCard) -> PlayAction?): List<PlayAction> =
        if (!usable(state) || player) emptyList() else targetIds.mapNotNull { id ->
            state.board.cards.singleOrNull { it.id == id }?.let(action)
        }
}

enum class ManualValueKind { LIFE, CORNER, COUNTER, NOTE }
data class ManualValue(val kind: ManualValueKind, val color: CounterColor? = null) {
    init { require((kind == ManualValueKind.COUNTER) == (color != null)) }
    val label: String get() = if (color != null) "${color.name.lowercase()} counter" else kind.name.lowercase()
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
    landscape: Boolean, names: Map<String, String>, extras: @Composable () -> Unit = {},
    submitAll: ((List<PlayAction>) -> Unit)? = null, completeValue: (() -> Boolean)? = null, submit: (PlayAction) -> Boolean) {
    val scope = actions.scope
    if (scope != null && scope.usable(state)) {
        if (scope.player) PlayerActions(state, scope, actions, canEdit, names, submit)
        else CardActions(state, viewer, scope, actions, canEdit, landscape, names, extras, submitAll, submit)
        actions.editor?.let { draft ->
            ValueEditor(draft, scope.value(state, draft.field, viewer), canEdit && draft.scope.usable(state),
                onCancel = actions::cancelEditor, onSubmit = completeValue ?: { actions.submitValue(state, viewer, submit) },
                preview = { if (!scope.player) ManualCardPreview(state, viewer, scope.contextId, scope.targetIds.size) })
        }
    }
    actions.inspection?.let { inspection -> GameplayCardInspection(state, viewer, inspection, actions::closeInspection) }
}
