package org.asyncttwiz.android

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap

import org.asyncttwiz.protocol.*

data class HistoryText(val description: String, val label: String, val move: HistoryMove? = null)
/** A card move: [subjects] are the moved cards' names, null for each card the viewer could not see at the time.
 * [template] is the move's sentence with [HISTORY_CARDS] where the cards go ("Drew …"), or null when a
 * zone is unknown (no board context); it then reads "X moved". Moves with the same template merge in a group. */
data class HistoryMove(val subjects: List<String?>, val template: String?)
/** Where a move sentence names its cards; a private-use character no name contains. */
internal const val HISTORY_CARDS = "\uE000"

/** Only visible timeline entries are projected. Texts the client made while it held an event's
 * before-board come from [known]; otherwise never materialize a distant board for a label: absent
 * nearby context uses the same privacy-safe generic wording as historyText. */
internal class TimelineTexts(private val tree: GameplayTree, private val viewer: String?,
    private val usernames: Map<String, String>, private val known: HistoryTextCache? = null) : AbstractMap<String, HistoryText>() {
    private val cache = object : LinkedHashMap<String, HistoryText>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HistoryText>?) = size > 64
    }
    override val entries: Set<Map.Entry<String, HistoryText>> get() = cache.entries
    override fun get(key: String): HistoryText? {
        if (key != tree.initial.root.id && key !in tree.nodes) return null
        return cache.getOrPut(key) {
            known?.takeIf { it.viewer == viewer && it.usernames == usernames }?.text(tree, key) ?: run {
                val event = tree.nodes[key]
                historyText(event, event?.parentId?.let(tree::preparedState), viewer, usernames)
            }
        }
    }
}

/** Most event texts one table keeps (oldest made first leave first): a few hundred bytes each. */
internal const val HISTORY_TEXT_CACHE_LIMIT = 8_192

/** One event's text, made while the client held the event's before-board. [identity] is the event's
 * ancestry identity and [event] its record's hash, so a re-parented or rewritten event no longer matches. */
internal class HistoryTextEntry(val identity: String, val event: Int, val text: HistoryText)

/** Event texts made when the client already had each event's before-board: the live end, catch-up and
 * playback steps, the reviewed point, local predictions. Immutable; [viewer] and [usernames] are what
 * the texts were written for. Never a reason to build a board: a missing text is simply missing. */
internal class HistoryTextCache private constructor(val viewer: String?, val usernames: Map<String, String>,
    private val entries: PersistentMap<String, HistoryTextEntry>, private val limit: Int) {
    constructor(viewer: String?, usernames: Map<String, String>, limit: Int = HISTORY_TEXT_CACHE_LIMIT) :
        this(viewer, usernames, persistentMapOf(), limit)
    val size: Int get() = entries.size
    fun text(tree: GameplayTree, id: String): HistoryText? {
        val entry = entries[id] ?: return null
        val node = tree.nodes[id] ?: return null
        return entry.text.takeIf { entry.event == node.hashCode() && tree.materializationId(id) == entry.identity }
    }
    fun forContext(viewer: String?, usernames: Map<String, String>) =
        if (viewer == this.viewer && usernames == this.usernames) this else HistoryTextCache(viewer, usernames, limit)
    fun with(added: Map<String, HistoryTextEntry>, dropped: Collection<String> = emptyList()): HistoryTextCache {
        if (added.isEmpty() && dropped.none(entries::containsKey)) return this
        var next = entries.mutate { map -> dropped.forEach { map.remove(it) }; map.putAll(added) }
        while (next.size > limit) next = next.remove(next.keys.first())
        return HistoryTextCache(viewer, usernames, next, limit)
    }
}

/** Texts of the events next to [state] (the board at its own event) that [cache] does not hold yet:
 * that event and up to [back] earlier ones, each stepped back once from [state]; its children; and up to
 * [ahead] later events along [forward] (the events after [state] on one line), each stepped forward once.
 * Each walk stops at the first event that already has a text. Only single-event deltas from the given
 * board are used: this never asks [tree] for a board, so it can never rebuild history from the root. */
internal fun historyTextsNear(tree: GameplayTree, state: PlayState, cache: HistoryTextCache,
    back: Int, forward: List<String> = emptyList(), ahead: Int = 0, dropped: Set<String> = emptySet(),
    current: () -> Boolean = { true }): Map<String, HistoryTextEntry> {
    val viewer = cache.viewer; val usernames = cache.usernames
    val nodes = tree.nodes
    val result = LinkedHashMap<String, HistoryTextEntry>()
    fun known(id: String) = id in result || (id !in dropped && cache.text(tree, id) != null)
    fun add(node: GameplayMutation, before: PlayState?) {
        val identity = tree.materializationId(node.id) ?: return
        result[node.id] = HistoryTextEntry(identity, node.hashCode(), historyText(node, before, viewer, usernames))
    }
    if (state.id != tree.initial.id || (state.currentNodeId != tree.initial.root.id && state.currentNodeId !in nodes)) return result
    for (child in tree.endpointIndex().children[state.currentNodeId].orEmpty()) {
        if (!current()) return result
        nodes[child]?.takeIf { !known(it.id) }?.let { add(it, state) }
    }
    var after = state
    var node = nodes[state.currentNodeId]
    var steps = 0
    while (node != null && steps++ < back && !known(node.id) && current()) {
        val before = GameplayHistory.unapply(after, node) ?: break
        add(node, before)
        after = before
        node = nodes[node.parentId]
    }
    var at = state
    for (id in forward.take(ahead)) {
        if (!current()) break
        val next = nodes[id]?.takeIf { it.parentId == at.currentNodeId } ?: break
        if (id !in result) { if (known(id)) break; add(next, at) }
        at = GameplayHistory.apply(at, next) ?: break
    }
    return result
}

private val historyUuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
internal fun historyPlainText(value: String, limit: Int = 180): String = historyUuid.replace(value, "[reference]")
    .replace(Regex("\\s+"), " ").trim().take(limit)
/** A status (up to 512 characters) as one short line: the first [limit] characters and an ellipsis when longer. */
internal fun historyStatusText(value: String, limit: Int = HISTORY_STATUS_LIMIT): String {
    val plain = historyPlainText(value, PlayerStatus.MAX_LENGTH)
    if (plain.length <= limit) return plain
    var end = limit - 1
    if (plain[end].isLowSurrogate()) end--
    return plain.substring(0, end).trimEnd() + "…"
}
internal const val HISTORY_STATUS_LIMIT = 48
internal fun historyPlayerName(id: String, usernames: Map<String, String>): String =
    usernames[id]?.let(::historyPlainText)?.takeIf { it.isNotBlank() } ?: "A player"

/** Ordinary log text never grants inspection permission. [before] must be the state at this
 * event's parent, not the current board: future reveals must not rename earlier hidden events.
 * Missing context deliberately loses detail rather than guessing an identity from downloaded data. */
fun historyText(event: GameplayMutation?, before: PlayState? = null, viewer: String? = null,
    usernames: Map<String, String> = emptyMap()): HistoryText {
    if (event == null) return HistoryText("Initial state", "Start")
    val transition = event.transition
    fun text(description: String, label: String) = HistoryText(historyPlainText(description), label)
    fun playerPrefix(id: String) = if (id == event.author) "" else "${historyPlayerName(id, usernames)}'s "
    fun container(id: String) = before?.board?.containers?.find { it.id == id }
    // A Temp pile may be created by this very event, so it is recognized by its deterministic ID too.
    val tempRefs by lazy { before?.let(TempPiles::refs).orEmpty() }
    fun tempNumber(id: String): Int? = container(id).let { c -> if (c != null) c.tempNumber.takeIf { c.temp } else tempRefs[id]?.number }
    fun temp(id: String) = tempNumber(id) != null
    fun known(id: String) = temp(id) || container(id) != null
    fun searchWaits(libraryId: String) = transition.rulesVersion >= SearchQueue.RULES_VERSION &&
        before?.board?.let(SearchQueue::search)?.containerId == libraryId
    fun zone(id: String) = tempNumber(id)?.let { "Temp $it" } ?: container(id)?.zone?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Another zone"
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
    fun damage(id: String, value: String?, delta: String?) = text(if (shownCard(id) == null) "A card damage changed"
        else "${card(id)} damage ${change(id, GameplayField.DAMAGE, inspection(id)?.damage?.ifEmpty { "0" }, value?.ifEmpty { "0" }, delta)}", "Damage")
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
    fun move(subjects: List<String?>, template: String?, label: String) = HistoryText(
        historyPlainText(historyMoveText(historyMoveSubjects(subjects), template)), label, HistoryMove(subjects, template))
    /** A zone as a move sentence names it: "graveyard", "the battlefield", "Temp 2"; a player's name only
     * when it isn't [owner]'s (the moved cards' owner), e.g. "bob's battlefield" for a stolen creature. */
    fun holder(id: String, owner: String?) = (container(id)?.accountId ?: tempRefs[id]?.accountId)?.takeIf { owner != null && it != owner }
    fun place(id: String, owner: String?): String {
        val area = container(id)
        val name = tempNumber(id)?.let { "Temp $it" } ?: when (area?.zone) {
            PlayZone.HAND -> "hand"; PlayZone.LIBRARY -> "library"; PlayZone.GRAVEYARD -> "graveyard"
            PlayZone.EXILE -> "exile"; PlayZone.BATTLEFIELD -> "battlefield"; PlayZone.STACK -> "stack"; null -> "another zone"
        }
        val holder = holder(id, owner)
        return when {
            holder != null -> "${historyPlayerName(holder, usernames)}'s $name"
            area?.zone == PlayZone.BATTLEFIELD || area?.zone == PlayZone.STACK -> "the $name"
            else -> name
        }
    }
    /** The sentence and label of a card move between two known places. Neutral verbs only: the table can't
     * know why a card moved, so never "died", "destroyed" or "resolved"; never the acting player's name. */
    fun moveSentence(action: PlayAction.MoveCard, source: String, target: String, owner: String?): Pair<String, String> {
        val x = HISTORY_CARDS
        val from = if (temp(source)) null else container(source)?.zone
        val to = if (temp(target)) null else container(target)?.zone
        val top = from == PlayZone.LIBRARY && action.source.edge == MoveSourceEdge.TOP
        fun origin(named: Boolean) = if (named || temp(source)) " from ${place(source, owner)}" else ""
        // Only a zone that isn't the owner's is named after the main verb ("Drew X into bob's hand").
        val into = if (holder(target, owner) != null) " into ${place(target, owner)}" else ""
        if (source == target) when (to) {
            PlayZone.BATTLEFIELD -> return "$x moved on ${place(target, owner)}" to "Move"
            PlayZone.LIBRARY -> if ((action.destination.libraryInsertion ?: action.destination.insertion) !in
                setOf(MoveInsertion.TOP, MoveInsertion.BOTTOM)) return "$x moved within ${place(target, owner)}" to "Move"
            else -> return "$x moved within ${place(target, owner)}" to "Move"
        }
        return when (to) {
            PlayZone.HAND -> when {
                top -> "Drew $x$into" to "Draw"
                from == PlayZone.LIBRARY -> "Took $x from ${place(source, owner)}$into" to "Take"
                else -> "Returned $x to ${place(target, owner)}${origin(false)}" to "Return"
            }
            PlayZone.BATTLEFIELD -> if (from == PlayZone.BATTLEFIELD)
                "${historyPlayerName(container(target)?.accountId.orEmpty(), usernames)} gains control of $x" to "Gain control"
                else "Put $x onto ${place(target, owner)}${origin(from != PlayZone.HAND)}" to "Put onto battlefield"
            PlayZone.STACK -> "Cast $x${if (action.faceDown == true) " face down" else ""}${origin(from != PlayZone.HAND)}" to "Cast"
            PlayZone.GRAVEYARD -> when {
                from == PlayZone.HAND -> "Discarded $x$into" to "Discard"
                top -> "Milled $x$into" to "Mill"
                else -> "Put $x into ${place(target, owner)}${origin(from != PlayZone.BATTLEFIELD)}" to "Put into graveyard"
            }
            PlayZone.EXILE -> "Exiled $x$into${origin(from != PlayZone.BATTLEFIELD)}" to "Exile"
            PlayZone.LIBRARY -> {
                val insertion = action.destination.libraryInsertion ?: action.destination.insertion
                // Rules v30: a top/bottom put onto a searched Library waits for the search to close.
                val waits = if (insertion in setOf(MoveInsertion.TOP, MoveInsertion.BOTTOM) && searchWaits(target)) " (after search)" else ""
                val library = place(target, owner)
                when (insertion) {
                    MoveInsertion.TOP -> "Put $x on top of $library${origin(false)}$waits"
                    MoveInsertion.BOTTOM -> "Put $x on the bottom of $library${origin(false)}$waits"
                    else -> "Put $x into $library${origin(false)}"
                } to "Put into library"
            }
            null -> "Put $x into ${place(target, owner)}${origin(false)}" to "Put into ${place(target, null)}"
        }
    }
    val result = when (val action = event.action) {
        PlayAction.PassAttention -> text("Move passed → ${transition.attention?.to?.let { historyPlayerName(it, usernames) } ?: "the other player"}", "Pass move")
        is PlayAction.SetTapped -> text("${card(action.cardId)} ${if (action.tapped) "tapped" else "untapped"}", if (action.tapped) "Tap" else "Untap")
        is PlayAction.SetLife -> life(action.accountId, action.value, false)
        is PlayAction.AdjustLife -> life(action.accountId, action.delta, true)
        is PlayAction.SetPlayerCounter -> counter(CounterTarget.PLAYER, action.accountId, action.color, action.value, false)
        is PlayAction.AdjustPlayerCounter -> counter(CounterTarget.PLAYER, action.accountId, action.color, action.delta, true)
        is PlayAction.SetStatus -> text(if (action.text.isEmpty()) "Status cleared" else "Status → ${historyStatusText(action.text)}", "Status")
        PlayAction.NextStep -> turn("Next step")
        PlayAction.EndTurn -> turn("End turn")
        is PlayAction.ChooseStep -> turn("Choose step", action.position)
        is PlayAction.RollDie -> text("d${historyPlainText(action.sides)} → ${action.value}", "Roll d${historyPlainText(action.sides)}")
        is PlayAction.FlipCoin -> text(coinResultText(action.value), "Flip coin")
        is PlayAction.MoveCard -> {
            val source = action.source.containerId
            val target = action.destination.containerId
            if (source == target && container(source)?.zone == PlayZone.HAND) text("Hand reordered", "Reorder hand")
            else {
                val subject = card(action.cardId).takeUnless { shownCard(action.cardId) == null }
                // Without board context a zone is unknown: say only that the card moved, never a made-up zone.
                val (template, label) = if (!known(source) || !known(target)) null to "Move" else moveSentence(action, source, target,
                    (shownCard(action.cardId) ?: before?.board?.cards?.find { it.id == action.cardId })?.owner)
                move(listOf(subject), template, label)
            }
        }
        is PlayAction.MoveToLibraryRandom -> {
            // Names only cards the viewer could see where they came from; the new order is never shown.
            val owners = action.cardIds.mapNotNull { id -> before?.board?.cards?.find { it.id == id }?.owner }.distinct()
            val library = if (container(action.libraryId) == null) "library" else place(action.libraryId, owners.singleOrNull())
            val waits = if (searchWaits(action.libraryId)) " (after search)" else ""
            val template = "Put $HISTORY_CARDS ${if (action.bottom) "on the bottom of" else "on top of"} $library in random order$waits"
            move(action.cardIds.map { id -> card(id).takeUnless { shownCard(id) == null } }, template, "Put into library")
        }
        is PlayAction.ShuffleLibrary -> text("${if (temp(action.containerId)) zone(action.containerId) else "Library"} shuffled", "Shuffle")
        is PlayAction.SetTempPile -> text("${playerPrefix(action.accountId)}Temp ${action.number} → ${if (action.on) "on" else "off"}",
            "Temp ${action.number} ${if (action.on) "on" else "off"}")
        is PlayAction.SetKeepTopRevealed -> text("Library top reveal → ${if (action.enabled) "on" else "off"}", "Top reveal ${if (action.enabled) "on" else "off"}")
        is PlayAction.Mulligan -> text("Mulligan to ${action.drawCount}", "Mulligan")
        is PlayAction.RevealHand -> text("Hand revealed → ${action.cardIds.size} cards", "Reveal hand")
        is PlayAction.RandomHand -> {
            val subject = card(action.cardId).takeUnless { shownCard(action.cardId) == null }
            when (action.result) {
                HandRandomResult.DISCARD -> move(listOf(subject), "Discarded $HISTORY_CARDS at random", "Discard")
                HandRandomResult.EXILE -> move(listOf(subject), "Exiled $HISTORY_CARDS from hand at random", "Exile")
                HandRandomResult.LIBRARY_BOTTOM -> move(listOf(subject), "Put $HISTORY_CARDS from hand on the bottom of library at random", "Put into library")
                HandRandomResult.REVEAL -> text(historyMoveText(historyMoveSubjects(listOf(subject)), "Revealed $HISTORY_CARDS from hand at random"), "Reveal")
            }
        }
        is PlayAction.ShowContents -> {
            val size = transition.contents?.to?.cardIds?.size
            val quantity = size?.let { "$it ${if (it == 1) "card" else "cards"}" } ?: "cards"
            text("${zone(action.containerId)} ${if (action.reveal) "revealed" else if (action.selection == ContentsSelection.ALL) "searched" else "looked at"} → $quantity",
                if (action.reveal) "Reveal" else if (action.selection == ContentsSelection.ALL) "Search" else "Look")
        }
        is PlayAction.DismissContents -> text(if (transition.contents?.from?.let { container(it.containerId)?.zone == PlayZone.HAND && it.reveal } == true) "Hand reveal done"
            else transition.contents?.from?.takeIf { it.search && temp(it.containerId) }?.let { search ->
                // Rules v31: a Temp pile Search closes without shuffling it.
                if (transition.rulesVersion >= TempPiles.SEARCH_RULES_VERSION) "Search closed → ${zone(search.containerId)} unchanged"
                else "Search closed → ${zone(search.containerId)} shuffled" }
            ?: transition.contents?.from?.takeIf { it.search }?.let { search -> "Search closed → Library shuffled" +
                listOfNotNull(searchQueueCount(SearchQueue.topCount(search), "top"),
                    searchQueueCount(SearchQueue.bottomCount(search), "bottom")).joinToString("") { " · $it" } }
            ?: "Card panel closed → ${action.rest.name.lowercase().replace('_', ' ')}", "Close panel")
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
        is PlayAction.SetDamage -> damage(action.cardId, action.value, null)
        is PlayAction.AdjustDamage -> damage(action.cardId, null, action.delta)
        is PlayAction.SetEotCorner -> text(if (shownCard(action.cardId) == null) "A card EOT P/T changed"
            else "${card(action.cardId)} EOT P/T ${change(action.cardId, GameplayField.EOT_CORNER, inspection(action.cardId)?.eotCorner, action.value)}", "EOT P/T")
        is PlayAction.AdjustEotCorner -> text(if (shownCard(action.cardId) == null) "A card EOT P/T changed"
            else "${card(action.cardId)} EOT P/T ${change(action.cardId, GameplayField.EOT_CORNER, inspection(action.cardId)?.eotCorner, null)}", "EOT P/T")
        is PlayAction.SetEotNote -> text(if (shownCard(action.cardId) == null) "A card EOT note edited"
            else "${card(action.cardId)} EOT note: ${historyPlainText(action.value)}", "EOT note")
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
            is CombatAction.SetAttackTarget -> text("Attack → ${combat.cardId?.let { card(it) } ?: "defending player"}", "Attack target")
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
        is PlayAction.DeclineResult -> text("Result declined", "Decline result")
        is PlayAction.WithdrawResult -> text("Result withdrawn", "Withdraw result")
        is PlayAction.SuggestResult -> text(when (action.outcome) {
            SuggestedResult.WIN -> "Result suggested → Win"
            SuggestedResult.LOSS -> "Result suggested → Loss"
            SuggestedResult.DRAW -> "Result suggested → Draw"
        } + action.note.takeIf { it.isNotEmpty() }?.let { ": “$it”" }.orEmpty(), "Suggest result")
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

/** A move sentence for [subjects] (see [historyMoveSubjects]): [template] filled in, or "X moved" without one. */
private fun historyMoveText(subjects: String, template: String?): String {
    val text = template?.replace(HISTORY_CARDS, subjects) ?: "$subjects moved"
    return if (template == null || template.startsWith(HISTORY_CARDS)) text.replaceFirstChar { it.uppercase() } else text
}

/** Longest group text; anything longer is cut and ends with "…". */
internal const val HISTORY_RANGE_TEXT_MAX = 180

/** A group's text. A group of only card moves merges moves of the same kind, in order of first appearance
 * ("Drew Shock, Opt, 2 cards; Discarded a card"); unseen cards collapse into "N cards". Any other group reads
 * as its representative event. */
internal fun historyRangeText(range: HistoryRange, texts: Map<String, HistoryText>,
    fallback: (String) -> HistoryText? = { null }): HistoryText =
    historyGroupText(range.nodeIds, range.representativeId ?: range.targetId, texts, fallback)

/** [historyRangeText] for a group given as its event ids (oldest first) and representative event. */
internal fun historyGroupText(nodeIds: List<String>, representativeId: String, texts: Map<String, HistoryText>,
    fallback: (String) -> HistoryText? = { null }): HistoryText {
    fun text(id: String) = texts[id] ?: fallback(id)
    val representative = text(representativeId) ?: HistoryText("Table changed", "Change")
    if (nodeIds.size < 2) return representative
    val moves = ArrayList<HistoryText>(nodeIds.size)
    for (id in nodeIds) moves += text(id)?.takeIf { it.move != null } ?: return representative
    val description = moves.groupBy { it.move!!.template }.entries.joinToString("; ") { (template, group) ->
        historyMoveText(historyMoveSubjects(group.flatMap { it.move!!.subjects }), template)
    }
    val plain = historyPlainText(description, Int.MAX_VALUE)
    return HistoryText(if (plain.length <= HISTORY_RANGE_TEXT_MAX) plain
        else plain.take(HISTORY_RANGE_TEXT_MAX - 1).trimEnd() + "…",
        moves.first().label.takeIf { label -> moves.all { it.label == label } }?.let { "$it ×${moves.size}" } ?: representative.label)
}

/** The cards of a move sentence: every name the viewer could see, then the unseen ones as "a card" / "N cards". */
internal fun historyMoveSubjects(subjects: List<String?>): String {
    val hidden = subjects.count { it == null }
    val names = subjects.filterNotNull()
    return (names + listOfNotNull(when {
        hidden == 0 -> null
        hidden == 1 -> if (names.isEmpty()) "a card" else "1 card"
        else -> "$hidden cards"
    })).joinToString(", ")
}
