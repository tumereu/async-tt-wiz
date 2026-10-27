package org.asyncttwiz.android

import kotlinx.collections.immutable.toPersistentMap

import org.asyncttwiz.protocol.*

data class HistoryText(val description: String, val label: String)

/** Only visible timeline entries are projected. Never materialize a distant board for a label;
 * absent nearby context uses the same privacy-safe generic wording as historyText. */
internal class TimelineTexts(private val tree: GameplayTree, private val viewer: String?,
    private val usernames: Map<String, String>) : AbstractMap<String, HistoryText>() {
    private val cache = object : LinkedHashMap<String, HistoryText>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HistoryText>?) = size > 64
    }
    override val entries: Set<Map.Entry<String, HistoryText>> get() = cache.entries
    override fun get(key: String): HistoryText? {
        if (key != tree.initial.root.id && key !in tree.nodes) return null
        return cache.getOrPut(key) {
            val event = tree.nodes[key]
            historyText(event, event?.parentId?.let(tree::preparedState), viewer, usernames)
        }
    }
}

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
    val transition = event.transition
    fun text(description: String, label: String) = HistoryText(historyPlainText(description), label)
    fun playerPrefix(id: String) = if (id == event.author) "" else "${historyPlayerName(id, usernames)}'s "
    fun container(id: String) = before?.board?.containers?.find { it.id == id }
    fun zone(id: String) = container(id)?.zone?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Another zone"
    fun visible(card: PlayCard): Boolean {
        val area = container(card.containerId) ?: return false
        return card.knownToAll || before?.board?.contentsDisplays?.any {
            card.id in it.cardIds && it.faceVisibleTo(card.id, viewer.orEmpty()) } == true ||
            area.zone == PlayZone.LIBRARY && area.keepTopRevealed && area.cards.firstOrNull() == card.id ||
            CardVisibility.isVisibleFaceUp(card, area.zone, area.accountId, viewer.orEmpty()) ||
            before?.board?.let { CardVisibility.isVisibleFaceUp(it, card, viewer.orEmpty()) } == true
    }
    fun shownCard(id: String): PlayCard? {
        val old = before?.let { CombatStackRules.inspection(it.board, id) }
            ?: transition.tokens.firstNotNullOfOrNull { it.from?.card?.takeIf { c -> c.id == id } ?: it.to?.card?.takeIf { c -> c.id == id } }
            ?: return null
        val fields = transition.fields.filter { it.targetId == old.id }
        val next = old.copy(containerId = transition.movement?.takeIf { it.cardId == old.id }?.to?.containerId ?: old.containerId,
            faceId = fields.find { it.field == GameplayField.FACE }?.to ?: old.faceId,
            covered = fields.find { it.field == GameplayField.COVERED }?.to?.toBooleanStrictOrNull() ?: old.covered,
            explicitlyCovered = fields.find { it.field == GameplayField.EXPLICITLY_COVERED }?.to?.toBooleanStrictOrNull() ?: old.explicitlyCovered)
        val exposure = transition.disclosures.firstOrNull { it.cardId == old.id && viewer in it.viewers }
        return if (exposure != null) next.copy(faceId = exposure.faceId) else if (visible(next)) next else old.takeIf(::visible)
    }
    fun card(id: String): String {
        val shown = shownCard(id) ?: return "A card"
        val definition = before?.definitions?.find { it.digest == shown.definition }
            ?: (event.action as? PlayAction.CreateToken)?.definition?.takeIf { it.digest == shown.definition }
        return definition?.definition?.faces
            ?.find { it.faceId == shown.faceId }?.name?.let(::historyPlainText)?.takeIf { it.isNotBlank() } ?: "A card"
    }
    fun inspection(id: String) = before?.let { CombatStackRules.inspection(it.board, id) }
    fun change(id: String, field: GameplayField, old: String?, fallback: String?, delta: String? = null): String {
        val recorded = transition.fields.find { it.targetId == id && it.field == field }
        val from = if (recorded != null) recorded.from else old
        val to = if (recorded != null) recorded.to else delta?.let { d -> from?.let { TableActions.addNumber(it, d) } } ?: fallback
        return "${historyPlainText(from ?: "?").ifEmpty { "—" }} → ${historyPlainText(to ?: "?").ifEmpty { "—" }}"
    }
    fun life(id: String, amount: String, adjust: Boolean) = text(
        "${playerPrefix(id)}${if (id == event.author) "Life" else "life"} ${change(id, GameplayField.LIFE,
            before?.board?.players?.find { it.accountId == id }?.life, if (adjust) null else amount, amount.takeIf { adjust })}", "Life")
    fun counter(kind: CounterTarget, id: String, color: CounterColor, amount: String, adjust: Boolean,
        label: String = "Counter"): HistoryText {
        if (kind == CounterTarget.CARD && shownCard(id) == null)
            return text("A card ${color.name.lowercase()} counter changed", label)
        val fields = if (kind == CounterTarget.PLAYER) setOf(GameplayField.COUNTER, GameplayField.PLAYER_COUNTER) else setOf(GameplayField.COUNTER)
        val recorded = transition.fields.find { it.targetId == id && it.field in fields && it.color == color }
        val old = if (kind == CounterTarget.PLAYER) before?.board?.players?.find { it.accountId == id }?.counters?.get(color)
            else inspection(id)?.counters?.get(color)
        val from = (if (recorded != null) recorded.from else old) ?: "0"
        val to = (if (recorded != null) recorded.to else if (adjust) TableActions.addNumber(from, amount) else amount) ?: "0"
        val subject = if (kind == CounterTarget.PLAYER)
            "${playerPrefix(id)}${if (id == event.author) "Counter" else "counter"} (${color.name.lowercase()})"
            else "${card(id)} ${color.name.lowercase()} counter"
        return text("$subject $from → ${to.ifBlank { "0" }}", label)
    }
    fun corner(id: String, fallback: String?, label: String = "Corner") = text(if (shownCard(id) == null) "A card P/T changed"
        else "${card(id)} P/T ${change(id, GameplayField.CORNER, inspection(id)?.corner, fallback)}", label)
    fun note(id: String, value: String, projection: Boolean = false): HistoryText {
        val subject = "${card(id)} ${if (projection) "projection note" else "note"}"
        return text(if (shownCard(id) == null) "$subject edited" else "$subject: $value", "Note")
    }
    fun turn(label: String, fallback: TurnPosition? = null): HistoryText {
        val number = transition.fields.find { it.field == GameplayField.TURN_NUMBER }?.to
        val holder = transition.fields.find { it.field == GameplayField.TURN_PLAYER }?.to
        if (number != null && holder != null) return text("Turn $number → ${historyPlayerName(holder, usernames)}", label)
        val phase = transition.fields.find { it.field == GameplayField.PHASE }?.to
        val step = transition.fields.find { it.field == GameplayField.STEP }?.to
        val position = TurnPosition.entries.find { it.phase == phase && it.step == step } ?: fallback
        return text(position?.let { "Moved to ${it.label}" } ?: if (label == "End turn") "Turn ended" else "Turn advanced", label)
    }
    val result = when (val action = event.action) {
        PlayAction.PassAttention -> text("Move passed → ${transition.attention?.to?.let { historyPlayerName(it, usernames) } ?: "the other player"}", "Pass move")
        is PlayAction.SetTapped -> text("${card(action.cardId)} ${if (action.tapped) "tapped" else "untapped"}", if (action.tapped) "Tap" else "Untap")
        is PlayAction.SetLife -> life(action.accountId, action.value, false)
        is PlayAction.AdjustLife -> life(action.accountId, action.delta, true)
        is PlayAction.SetPlayerCounter -> counter(CounterTarget.PLAYER, action.accountId, action.color, action.value, false)
        is PlayAction.AdjustPlayerCounter -> counter(CounterTarget.PLAYER, action.accountId, action.color, action.delta, true)
        PlayAction.NextStep -> turn("Next step")
        PlayAction.EndTurn -> turn("End turn")
        is PlayAction.ChooseStep -> turn("Choose step", action.position)
        is PlayAction.RollDie -> text("d${historyPlainText(action.sides)} → ${action.value}", "Roll d${historyPlainText(action.sides)}")
        is PlayAction.FlipCoin -> text(action.value.name.lowercase().replaceFirstChar { it.uppercase() }, "Flip coin")
        is PlayAction.MoveCard -> {
            if (container(action.source.containerId)?.zone == PlayZone.LIBRARY &&
                container(action.destination.containerId)?.zone == PlayZone.HAND && action.source.edge == MoveSourceEdge.TOP)
                text("Drew $count", if (count > 1) "Draw ×$count" else "Draw")
            else if (action.source.containerId == action.destination.containerId && container(action.source.containerId)?.zone == PlayZone.HAND)
                text("Hand reordered", "Reorder hand")
            else {
                val destination = zone(action.destination.containerId)
                val insertion = action.destination.libraryInsertion ?: action.destination.insertion
                val edge = if (container(action.destination.containerId)?.zone == PlayZone.LIBRARY) when (insertion) {
                    MoveInsertion.TOP -> " top"
                    MoveInsertion.BOTTOM -> " bottom"
                    else -> ""
                } else ""
                text("${card(action.cardId)} → $destination$edge", "Move")
            }
        }
        is PlayAction.ShuffleLibrary -> text("Library shuffled", "Shuffle")
        is PlayAction.SetKeepTopRevealed -> text("Library top reveal → ${if (action.enabled) "on" else "off"}", "Top reveal ${if (action.enabled) "on" else "off"}")
        is PlayAction.Mulligan -> text("Mulligan to ${action.drawCount}", "Mulligan")
        is PlayAction.ShowContents -> {
            val size = transition.contents?.to?.cardIds?.size
            val quantity = size?.let { "$it ${if (it == 1) "card" else "cards"}" } ?: "cards"
            text("${zone(action.containerId)} ${if (action.reveal) "revealed" else if (action.selection == ContentsSelection.ALL) "searched" else "looked at"} → $quantity",
                if (action.reveal) "Reveal" else if (action.selection == ContentsSelection.ALL) "Search" else "Look")
        }
        is PlayAction.DismissContents -> text(if (transition.contents?.from?.search == true) "Search closed → Library shuffled"
            else "Card panel closed → ${action.rest.name.lowercase().replace('_', ' ')}", "Close panel")
        is PlayAction.RevealContentsCard -> text("${card(action.cardId)} revealed in panel", "Reveal card")
        is PlayAction.ReorderContents -> text("Card panel reordered", "Reorder panel")
        is PlayAction.EventComment -> text("Comment: ${historyPlainText(action.text)}", "Comment")
        is PlayAction.Ping -> text("${when (action.target.kind) {
            PingTargetKind.PLAYER -> action.target.id?.let { historyPlayerName(it, usernames) } ?: "A player"
            PingTargetKind.CARD, PingTargetKind.OCCURRENCE -> action.target.id?.let(::card) ?: "A card"
            PingTargetKind.AREA -> "Area"
            PingTargetKind.LOCATION -> "Location"
        }} pinged", "Ping")
        is PlayAction.Unping -> text("Ping removed", "Unping")
        is PlayAction.SetCorner -> corner(action.cardId, action.value)
        is PlayAction.AdjustCorner -> corner(action.cardId, inspection(action.cardId)?.corner?.let { ManualText.corner(it, action.part, action.delta) })
        is PlayAction.ResetCorner -> corner(action.cardId, null, "Reset corner")
        is PlayAction.SetCounter -> counter(action.target, action.targetId, action.color, action.value, false)
        is PlayAction.AdjustCounter -> counter(action.target, action.targetId, action.color, action.delta, true)
        is PlayAction.ClearCardCounter -> counter(CounterTarget.CARD, action.cardId, action.color, "0", false, "Clear counter")
        is PlayAction.SetNote -> note(action.cardId, action.value)
        is PlayAction.SetProjectionNote -> note(action.occurrenceId, action.value, true)
        is PlayAction.SetOwner -> text("${card(action.cardId)} owner → ${historyPlayerName(action.accountId, usernames)}", "Owner")
        is PlayAction.SetFace -> text("${card(action.cardId)} face changed", "Change face")
        is PlayAction.SetCovered -> text("${card(action.cardId)} face ${if (action.covered) "down" else "up"}", if (action.covered) "Cover" else "Uncover")
        is PlayAction.InspectFace -> text("${card(action.cardId)} inspected", "Inspect")
        is PlayAction.CreateToken -> {
            val id = transition.tokens.firstNotNullOfOrNull { it.to?.card?.id }
            val name = id?.let(::card)?.takeUnless { it == "A card" }
            text(if (name == null) "Token created" else "$name token created", "Create token")
        }
        is PlayAction.CloneToken -> text("${card(action.cardId)} cloned", "Clone")
        is PlayAction.RemoveToken -> text("${card(action.cardId).takeUnless { it == "A card" } ?: "Token"} removed", "Remove token")
        is PlayAction.Link -> text("${if (action.first.kind == LinkEndpointKind.CARD) card(action.first.id) else historyPlayerName(action.first.id, usernames)} linked → ${if (action.second.kind == LinkEndpointKind.CARD) card(action.second.id) else historyPlayerName(action.second.id, usernames)}", "Link")
        is PlayAction.RemoveLink -> text("Link removed", "Unlink")
        is PlayAction.LinkProjections -> text("${card(action.first.occurrenceId)} linked → ${card(action.second.occurrenceId)}", "Link entries")
        is PlayAction.RemoveProjectionLink -> text("Combat/Stack link removed", "Unlink entries")
        is PlayAction.Combat -> when (val combat = action.action) {
            CombatAction.Open -> text("Combat opened", "Open combat")
            is CombatAction.Close -> text("Combat closed", "Close combat")
            is CombatAction.Declare -> text("${card(combat.sourceId)} → Attacker", "Attack")
            is CombatAction.Place -> text("${card(combat.sourceId)} → ${if (combat.destination.role == CombatRole.ATTACKER) "Attacker" else "Blocker"}", if (combat.destination.role == CombatRole.ATTACKER) "Attack" else "Block")
            is CombatAction.Remove -> text("${card(combat.occurrenceId)} removed from combat", "Remove combat")
        }
        is PlayAction.Stack -> when (val stack = action.action) {
            is StackAction.MarkText -> {
                val index = stack.optionIndex
                val source = before?.let { CombatStackRules.inspection(it.board, stack.entryId) }
                val option = if (source != null && visible(source)) before?.let { StackText.options(it, stack.entryId) }
                    ?.getOrNull(stack.optionIndex ?: -1)?.text?.take(40) else null
                text(if (index == null) "${card(stack.entryId)} text mark cleared"
                    else "${card(stack.entryId)} marked: ${index + 1}${option?.let { " — $it" }.orEmpty()}", "Mark text")
            }
            is StackAction.AddEffect -> text("${card(stack.sourceId)} effect → Stack", "Add effect")
            is StackAction.CopyEffect -> text("${card(stack.entryId)} copied → Stack top", "Copy effect")
            is StackAction.SetController -> text("${card(stack.occurrenceId)} Stack controller → ${historyPlayerName(stack.accountId, usernames)}", "Controller")
            is StackAction.ToggleTextMark -> {
                val removing = stack.optionIndex in before?.board?.stackTextMarks?.get(stack.entryId).orEmpty()
                text("${card(stack.entryId)} ${if (removing) "unmarked" else "marked"}: ${stack.optionIndex + 1}", "Mark text")
            }
            is StackAction.RemoveEffect -> text("${card(stack.occurrenceId)} effect removed from Stack", "Remove effect")
            is StackAction.ReorderEffect -> text("${card(stack.occurrenceId)} effect reordered on Stack", "Reorder effect")
        }
        is PlayAction.SuggestResult -> text(when (action.outcome) {
            SuggestedResult.WIN -> "Result suggested → Win"
            SuggestedResult.LOSS -> "Result suggested → Loss"
            SuggestedResult.DRAW -> "Result suggested → Draw"
        }, "Suggest result")
    }
    return if (event.schemaVersion !in 1..2) text("Table changed", "Change") else result
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
        return HistoryText("Drew ${events.size}", "Draw ×${events.size}")
    return texts[range.representativeId ?: range.targetId] ?: HistoryText("Table changed", "Change")
}
