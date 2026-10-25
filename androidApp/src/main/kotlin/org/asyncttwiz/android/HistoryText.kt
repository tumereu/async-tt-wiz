package org.asyncttwiz.android

import kotlinx.collections.immutable.toPersistentMap

import org.asyncttwiz.protocol.*

data class HistoryText(val description: String, val label: String)

private val historyUuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
internal fun historyPlainText(value: String): String = historyUuid.replace(value, "[reference]")
    .replace(Regex("\\s+"), " ").trim().take(180)
internal fun historyPlayerName(id: String, usernames: Map<String, String>): String =
    usernames[id]?.let(::historyPlainText)?.takeIf { it.isNotBlank() } ?: "A player"

/** Ordinary log text never grants inspection permission. [before] must be the state at this
 * event's parent, not the current board: future reveals must not rename earlier hidden events.
 * Missing context deliberately loses detail rather than guessing an identity from downloaded data. */
fun historyText(event: GameplayMutation?, before: PlayState? = null, viewer: String? = null,
    usernames: Map<String, String> = emptyMap(), count: Int = 1): HistoryText {
    if (event == null) return HistoryText("Initial state", "Start")
    val author = historyPlayerName(event.author, usernames)
    val transition = event.transition
    fun text(verb: String, label: String) = HistoryText(historyPlainText("$author $verb"), label)
    fun player(id: String) = if (id == event.author) "their" else "${historyPlayerName(id, usernames)}'s"
    fun container(id: String) = before?.board?.containers?.find { it.id == id }
    fun zone(id: String) = container(id)?.zone?.name?.lowercase() ?: "another zone"
    fun visible(card: PlayCard): Boolean {
        val area = container(card.containerId) ?: return false
        return CardVisibility.isVisibleFaceUp(card, area.zone, area.accountId, viewer.orEmpty())
    }
    fun card(id: String): String {
        val old = before?.let { CombatStackRules.inspection(it.board, id) }
            ?: transition.tokens.firstNotNullOfOrNull { it.from?.card?.takeIf { c -> c.id == id } ?: it.to?.card?.takeIf { c -> c.id == id } }
            ?: return "a card"
        val fields = transition.fields.filter { it.targetId == old.id }
        val next = old.copy(containerId = transition.movement?.takeIf { it.cardId == old.id }?.to?.containerId ?: old.containerId,
            faceId = fields.find { it.field == GameplayField.FACE }?.to ?: old.faceId,
            covered = fields.find { it.field == GameplayField.COVERED }?.to?.toBooleanStrictOrNull() ?: old.covered,
            explicitlyCovered = fields.find { it.field == GameplayField.EXPLICITLY_COVERED }?.to?.toBooleanStrictOrNull() ?: old.explicitlyCovered)
        val shown = if (visible(next)) next else old.takeIf(::visible) ?: return "a card"
        val definition = before?.definitions?.find { it.digest == shown.definition }
            ?: (event.action as? PlayAction.CreateToken)?.definition?.takeIf { it.digest == shown.definition }
        return definition?.definition?.faces
            ?.find { it.faceId == shown.faceId }?.name?.let(::historyPlainText)?.takeIf { it.isNotBlank() } ?: "a card"
    }
    fun value(id: String, field: GameplayField, fallback: String) = historyPlainText(
        transition.fields.find { it.targetId == id && it.field == field }?.to ?: fallback)
    fun counter(target: String, color: CounterColor, amount: String, adjust: Boolean): HistoryText =
        text(if (adjust) "changed the ${color.name.lowercase()} counter on $target by ${historyPlainText(amount)}"
            else "set the ${color.name.lowercase()} counter on $target to ${historyPlainText(amount).ifEmpty { "0" }}", "Counter")
    fun counterTarget(kind: CounterTarget, id: String) = if (kind == CounterTarget.PLAYER)
        historyPlayerName(id, usernames) else card(id)
    fun step(position: TurnPosition?) = position?.let {
        if (it.step == "MAIN") "${it.label} phase" else if (it == TurnPosition.END_STEP) it.label else "${it.label} step"
    }
    val result = when (val action = event.action) {
        PlayAction.PassAttention -> text("passed the move to ${transition.attention?.to?.let { historyPlayerName(it, usernames) } ?: "the other player"}", "Pass move")
        is PlayAction.SetTapped -> text("${if (action.tapped) "tapped" else "untapped"} ${card(action.cardId)}", if (action.tapped) "Tap" else "Untap")
        is PlayAction.SetLife -> text("set ${if (action.accountId == event.author) "life" else "${player(action.accountId)} life"} to ${value(action.accountId, GameplayField.LIFE, action.value)}", "Life")
        is PlayAction.AdjustLife -> {
            val to = transition.fields.find { it.targetId == action.accountId && it.field == GameplayField.LIFE }?.to
            text(if (to != null) "set ${player(action.accountId)} life to ${historyPlainText(to)}"
                else "changed ${player(action.accountId)} life by ${historyPlainText(action.delta)}", "Life")
        }
        is PlayAction.SetPlayerCounter -> counter(historyPlayerName(action.accountId, usernames), action.color, action.value, false)
        is PlayAction.AdjustPlayerCounter -> counter(historyPlayerName(action.accountId, usernames), action.color, action.delta, true)
        PlayAction.NextStep -> {
            val phase = transition.fields.find { it.field == GameplayField.PHASE }?.to
            val nextStep = transition.fields.find { it.field == GameplayField.STEP }?.to
            val position = TurnPosition.entries.find { it.phase == phase && it.step == nextStep }
            text(step(position)?.let { "advanced to $it" } ?: "advanced the turn", "Next step")
        }
        PlayAction.EndTurn -> text("ended the turn", "End turn")
        is PlayAction.ChooseStep -> text("advanced to ${step(action.position)}", "Choose step")
        is PlayAction.RollDie -> text("rolled a d${historyPlainText(action.sides)}: ${action.value}", "Roll d${historyPlainText(action.sides)}")
        is PlayAction.FlipCoin -> text("flipped a coin: ${action.value.name.lowercase()}", "Flip coin")
        is PlayAction.MoveCard -> {
            if (container(action.source.containerId)?.zone == PlayZone.LIBRARY &&
                container(action.destination.containerId)?.zone == PlayZone.HAND && action.source.edge == MoveSourceEdge.TOP)
                text(if (count > 1) "drew $count cards" else "drew a card", if (count > 1) "Draw ×$count" else "Draw")
            else if (action.source.containerId == action.destination.containerId && container(action.source.containerId)?.zone == PlayZone.HAND)
                text("reordered their hand", "Reorder hand")
            else if (action.source.containerId == action.destination.containerId)
                text("repositioned ${card(action.cardId)} in ${zone(action.source.containerId)}", "Move")
            else text("moved ${card(action.cardId)} from ${zone(action.source.containerId)} to ${zone(action.destination.containerId)}", "Move")
        }
        is PlayAction.ShuffleLibrary -> text("shuffled ${container(action.containerId)?.accountId?.let(::player) ?: "their"} library", "Shuffle")
        is PlayAction.ShowContents -> {
            val size = transition.contents?.to?.cardIds?.size
            val quantity = size?.let { "$it ${if (it == 1) "card" else "cards"}" } ?: "cards"
            text("${if (action.reveal) "revealed" else "viewed"} $quantity from ${zone(action.containerId)}", if (action.reveal) "Reveal" else "View")
        }
        is PlayAction.DismissContents -> text("dismissed the card display", "Dismiss")
        is PlayAction.EventComment -> text("commented: ${historyPlainText(action.text)}", "Comment")
        is PlayAction.Ping -> text("pinged ${when (action.target.kind) {
            PingTargetKind.PLAYER -> action.target.id?.let { historyPlayerName(it, usernames) } ?: "a player"
            PingTargetKind.CARD, PingTargetKind.OCCURRENCE -> action.target.id?.let(::card) ?: "a card"
            PingTargetKind.AREA -> "an area"
            PingTargetKind.LOCATION -> "a location"
        }}", "Ping")
        is PlayAction.Unping -> text("removed a ping", "Unping")
        is PlayAction.SetCorner -> text("set the corner value on ${card(action.cardId)} to ${historyPlainText(action.value)}", "Corner")
        is PlayAction.AdjustCorner -> text("changed the ${action.part.name.lowercase()} value on ${card(action.cardId)} by ${action.delta}", "Corner")
        is PlayAction.ResetCorner -> text("reset the corner value on ${card(action.cardId)}", "Reset corner")
        is PlayAction.SetCounter -> counter(counterTarget(action.target, action.targetId), action.color, action.value, false)
        is PlayAction.AdjustCounter -> counter(counterTarget(action.target, action.targetId), action.color, action.delta, true)
        is PlayAction.ClearCardCounter -> text("cleared the ${action.color.name.lowercase()} counter on ${card(action.cardId)}", "Clear counter")
        is PlayAction.SetNote -> text("edited the note on ${card(action.cardId)}", "Note")
        is PlayAction.SetOwner -> text("changed the owner of ${card(action.cardId)} to ${historyPlayerName(action.accountId, usernames)}", "Owner")
        is PlayAction.SetFace -> text("changed the face of ${card(action.cardId)}", "Change face")
        is PlayAction.SetCovered -> text("turned ${card(action.cardId)} face ${if (action.covered) "down" else "up"}", if (action.covered) "Cover" else "Uncover")
        is PlayAction.InspectFace -> text("inspected ${card(action.cardId).takeUnless { it == "a card" } ?: "a card face"}", "Inspect")
        is PlayAction.CreateToken -> {
            val id = transition.tokens.firstNotNullOfOrNull { it.to?.card?.id }
            val name = id?.let(::card)?.takeUnless { it == "a card" }
            text(if (name == null) "created a token" else "created a $name token", "Create token")
        }
        is PlayAction.CloneToken -> text("cloned ${card(action.cardId)}", "Clone")
        is PlayAction.RemoveToken -> text("removed ${card(action.cardId).takeUnless { it == "a card" } ?: "a token"}", "Remove token")
        is PlayAction.Link -> text("linked ${if (action.first.kind == LinkEndpointKind.CARD) card(action.first.id) else historyPlayerName(action.first.id, usernames)} and ${if (action.second.kind == LinkEndpointKind.CARD) card(action.second.id) else historyPlayerName(action.second.id, usernames)}", "Link")
        is PlayAction.RemoveLink -> text("removed a link", "Unlink")
        is PlayAction.LinkProjections -> text("linked ${card(action.first.occurrenceId)} and ${card(action.second.occurrenceId)}", "Link entries")
        is PlayAction.RemoveProjectionLink -> text("removed a combat or stack link", "Unlink entries")
        is PlayAction.Combat -> when (val combat = action.action) {
            CombatAction.Open -> text("opened combat", "Open combat")
            is CombatAction.Close -> text("closed combat", "Close combat")
            is CombatAction.Declare -> text("declared ${card(combat.sourceId)} as an attacker", "Attack")
            is CombatAction.Place -> text("placed ${card(combat.sourceId)} as ${if (combat.destination.role == CombatRole.ATTACKER) "an attacker" else "a blocker"}", if (combat.destination.role == CombatRole.ATTACKER) "Attack" else "Block")
            is CombatAction.Remove -> text("removed ${card(combat.occurrenceId)} from combat", "Remove combat")
        }
        is PlayAction.Stack -> when (val stack = action.action) {
            is StackAction.MarkText -> {
                val index = stack.optionIndex
                val source = before?.let { CombatStackRules.inspection(it.board, stack.entryId) }
                val option = if (source != null && visible(source)) before?.let { StackText.options(it, stack.entryId) }
                    ?.getOrNull(stack.optionIndex ?: -1)?.text?.take(40) else null
                text(if (index == null) "cleared the text mark on ${card(stack.entryId)}"
                    else "marked ${card(stack.entryId)}: ${index + 1}${option?.let { " — $it" }.orEmpty()}", "Mark text")
            }
            is StackAction.AddEffect -> text("added an effect from ${card(stack.sourceId)} to the stack", "Add effect")
            is StackAction.ToggleTextMark -> {
                val removing = stack.optionIndex in before?.board?.stackTextMarks?.get(stack.entryId).orEmpty()
                text("${if (removing) "unmarked" else "marked"} ${card(stack.entryId)}: ${stack.optionIndex + 1}", "Mark text")
            }
            is StackAction.RemoveEffect -> text("removed an effect from the stack", "Remove effect")
            is StackAction.ReorderEffect -> text("reordered an effect on the stack", "Reorder effect")
        }
        is PlayAction.SuggestResult -> text(when (action.outcome) {
            SuggestedResult.WIN -> "suggested their win"
            SuggestedResult.LOSS -> "suggested their loss"
            SuggestedResult.DRAW -> "suggested a draw"
        }, "Suggest result")
    }
    return if (event.schemaVersion !in 1..2) text("changed the table", "Change") else result
}

/** Worker-only text projection. Replay each retained edge once with the shared reducer; keep
 * boards only along open fork paths, never a second per-event board cache. Annotations do not
 * change card visibility, so their board projection needs no reference resolution. */
internal class HistoryTextWork { var projectedEvents = 0; var scannedEvents = 0 }

internal fun historyTexts(tree: GameplayTree, viewer: String?, usernames: Map<String, String>,
    previous: GameplayTree? = null, previousTexts: Map<String, HistoryText> = emptyMap(),
    changed: Set<String>? = previous?.let { tree.changedNodeIdsSince(it) },
    work: HistoryTextWork = HistoryTextWork(),
    current: () -> Boolean = { true }): Map<String, HistoryText> {
    if (previous != null && changed != null) {
        var result = previousTexts.toPersistentMap()
        for (id in changed) {
            if (!current()) return emptyMap()
            val event = tree.nodes[id]
            if (event != null) work.projectedEvents++
            result = if (event == null) result.remove(id) else result.put(id,
                historyText(event, tree.stateAt(event.parentId), viewer, usernames))
        }
        return result
    }
    val children = tree.nodes.values.groupBy { it.parentId }
    work.scannedEvents += tree.nodes.size
    val texts = mutableMapOf(tree.initial.root.id to historyText(null))
    val pending = ArrayDeque<Pair<GameplayMutation, PlayState?>>()
    val unchanged = mutableSetOf<String>()
    if (previous?.initial == tree.initial) unchanged.add(tree.initial.root.id)
    children[tree.initial.root.id].orEmpty().forEach { pending.addLast(it to tree.initial) }
    while (pending.isNotEmpty()) {
        if (!current()) return emptyMap()
        val (event, parent) = pending.removeLast()
        val same = event.parentId in unchanged && previous?.nodes?.get(event.id) == event && event.id in previousTexts
        if (same) unchanged.add(event.id)
        val before = if (same) null else parent ?: tree.stateAt(event.parentId)
        texts[event.id] = if (same) previousTexts.getValue(event.id) else historyText(event, before, viewer, usernames)
        val state = before?.takeIf { children[event.id].orEmpty().isNotEmpty() }?.let { before ->
            if (event.transition.annotation != null) before.copy(currentNodeId = event.id,
                currentBranchId = event.branchId, currentGroup = GameplayHistory.append(before.currentGroup, event))
            else GameplayHistory.replay(before, event).state
        }
        children[event.id].orEmpty().forEach { pending.addLast(it to state) }
    }
    return texts.toPersistentMap()
}

internal fun historyRangeText(range: HistoryRange, texts: Map<String, HistoryText>): HistoryText {
    val events = range.nodeIds.mapNotNull(texts::get)
    if (events.size > 1 && events.all { it.label == "Draw" })
        return HistoryText(events.first().description.removeSuffix("a card") + "${events.size} cards", "Draw ×${events.size}")
    return texts[range.representativeId ?: range.targetId] ?: HistoryText("Table changed", "Change")
}
