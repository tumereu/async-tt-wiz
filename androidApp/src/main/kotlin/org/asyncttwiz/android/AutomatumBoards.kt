package org.asyncttwiz.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import arcanum.api.CardDefinition
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import arcanum.engine.state.LoggedChange
import org.asyncttwiz.automatum.CardPlays
import org.asyncttwiz.automatum.CombatFlow
import org.asyncttwiz.automatum.CombatShown
import org.asyncttwiz.automatum.ComputedBoard
import org.asyncttwiz.automatum.EngineLog
import org.asyncttwiz.automatum.EngineState
import org.asyncttwiz.automatum.HandPlays
import org.asyncttwiz.automatum.SharedFlow
import org.asyncttwiz.protocol.GameplayTree
import org.asyncttwiz.protocol.MtgTag
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.storage.ParsedCard
import java.util.concurrent.ConcurrentHashMap

/**
 * Automatum on (integration spec §3 I1): the engine's view of the table, worked out on a worker thread whenever the
 * board changes (never per frame, never in the way of an action's own result, SYS-025) and kept for the latest boards.
 * Card definitions are parsed on the device from the game's own card data (I-D2) by [definitionSource], which the app
 * installs (its [org.asyncttwiz.storage.CardDefinitions] on the account's storage), and kept in memory by digest.
 */
internal object AutomatumBoards {
    /** Parses (or reads from the device cache) the definitions of the given card data. Blocking. */
    @Volatile var definitionSource: ((Collection<SubmittedDefinition>) -> Map<String, ParsedCard>)? = null

    private val parsed = ConcurrentHashMap<String, CardDefinition>()
    private val unreadable = ConcurrentHashMap.newKeySet<String>()
    private class Key(val board: Any, val viewer: String) {
        override fun equals(other: Any?) = other is Key && board === other.board && viewer == other.viewer
        override fun hashCode() = System.identityHashCode(board) * 31 + viewer.hashCode()
    }
    private val boards = object : LinkedHashMap<Key, ComputedBoard>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, ComputedBoard>?) = size > 8
    }

    /** The board already worked out for [play]'s board as [viewer] sees it, if any. */
    fun cached(play: PlayState, viewer: String): ComputedBoard? = synchronized(boards) { boards[Key(play.board, viewer)] }

    /** Works [play]'s board out (blocking: parsing new card data and one engine view). Never throws: a board the
     * device can't work out keeps every card manual and says why. */
    fun compute(play: PlayState, viewer: String): ComputedBoard {
        cached(play, viewer)?.let { return it }
        val board = try {
            ComputedBoard.of(play, definitions(play.definitions), viewer)
        } catch (problem: Exception) {
            AppLog.warn("automatum", "Couldn't work the board out: ${problem::class.java.simpleName}")
            ComputedBoard.failed("Automatum couldn't work this board out (${problem.message?.take(120) ?: problem::class.java.simpleName})")
        }
        synchronized(boards) { boards[Key(play.board, viewer)] = board }
        return board
    }

    private class PlaysKey(val board: Any, val viewer: String) {
        override fun equals(other: Any?) = other is PlaysKey && board === other.board && viewer == other.viewer
        override fun hashCode() = System.identityHashCode(board) * 31 + viewer.hashCode()
    }
    private val plays = object : LinkedHashMap<PlaysKey, HandPlays>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PlaysKey, HandPlays>?) = size > 8
    }

    /** The game's card definitions the device could read, by digest (blocking: parses card data not read yet). */
    fun definitions(play: PlayState): Map<String, CardDefinition> = definitions(play.definitions)

    /** What [viewer] may cast or play from their hand on [play]'s board, when already worked out. */
    fun cachedHandPlays(play: PlayState, viewer: String): HandPlays? = synchronized(plays) { plays[PlaysKey(play.board, viewer)] }

    /** What [viewer] may cast or play from their hand on [play]'s board (I2, I-D28): kept for the latest boards; worked
     * out now when not yet (blocking: one engine `options`). [tags]: the history's tags up to this board (D33), read
     * only when it has to be worked out. */
    fun handPlays(play: PlayState, viewer: String, tags: () -> List<MtgTag?>): HandPlays {
        cachedHandPlays(play, viewer)?.let { return it }
        val result = try {
            val definitions = definitions(play.definitions)
            HandPlays.of(play, definitions, viewer, engineLog(play, definitions, tags()))
        } catch (problem: Exception) {
            AppLog.warn("automatum", "Couldn't list plays: ${problem::class.java.simpleName}")
            HandPlays.NONE
        }
        synchronized(plays) { plays[PlaysKey(play.board, viewer)] = result }
        return result
    }

    private val cardPlays = object : LinkedHashMap<PlaysKey, CardPlays>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PlaysKey, CardPlays>?) = size > 8
    }

    /** What the engine offers [viewer] for each card on [play]'s board, when already worked out. */
    fun cachedCardPlays(play: PlayState, viewer: String): CardPlays? = synchronized(cardPlays) { cardPlays[PlaysKey(play.board, viewer)] }

    /** What the engine offers [viewer] for each card on [play]'s board (§3 I4: the card menus): kept for the latest
     * boards; worked out now when not yet (blocking: one engine `options` and `view`). */
    fun cardPlays(play: PlayState, viewer: String, tags: () -> List<MtgTag?>): CardPlays {
        cachedCardPlays(play, viewer)?.let { return it }
        val result = try {
            val definitions = definitions(play.definitions)
            CardPlays.of(play, definitions, viewer, engineLog(play, definitions, tags()))
        } catch (problem: Exception) {
            AppLog.warn("automatum", "Couldn't list card plays: ${problem::class.java.simpleName}")
            CardPlays.NONE
        }
        synchronized(cardPlays) { cardPlays[PlaysKey(play.board, viewer)] = result }
        return result
    }

    private val flows = object : LinkedHashMap<IdentityKey, SharedFlow?>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<IdentityKey, SharedFlow?>?) = size > 8
    }
    private fun seed(): String = DeviceRandom.seed()

    /** Whether [play]'s board has a shared flow to show (I2): an open decision, or something on the Stack. */
    fun sharedFlowShown(play: PlayState): Boolean = play.board.automatum &&
        (org.asyncttwiz.protocol.Automatum.flowOpen(play.board) || play.board.stackEffects.any { it.item != null })

    /** Whether Automatum's open choice on [play]'s board is something to do by hand (I-D37): manual edits are allowed
     * until it is answered. Read from the open choice's record, so it is known at once on every board (#5: a second
     * quick edit in a row isn't refused while the new board's flow is worked out); kept for the last record. */
    fun byHand(play: PlayState): Boolean {
        val record = play.board.engineRecords.singleOrNull { it.id == org.asyncttwiz.protocol.Automatum.CONTINUATION_ID } ?: return false
        lastByHand?.takeIf { it.first === record }?.let { return it.second }
        return EngineState.byHand(play.board).also { lastByHand = record to it }
    }
    @Volatile private var lastByHand: Pair<Any, Boolean>? = null

    /** [CombatFlow.throughEngine] and [CombatFlow.hadFirstStrikeStep] of [board], kept for the last board (combat review
     * #12: the table asks on every frame; each reads the combat record's JSON). */
    fun combatRecord(board: org.asyncttwiz.protocol.BoardState): Pair<Boolean, Boolean> {
        lastCombatRecord?.takeIf { it.first === board }?.let { return it.second }
        val read = org.asyncttwiz.automatum.CombatFlow.throughEngine(board) to org.asyncttwiz.automatum.CombatFlow.hadFirstStrikeStep(board)
        lastCombatRecord = board to read
        return read
    }
    @Volatile private var lastCombatRecord: Pair<Any, Pair<Boolean, Boolean>>? = null
    fun throughEngine(board: org.asyncttwiz.protocol.BoardState): Boolean = combatRecord(board).first
    fun hadFirstStrikeStep(board: org.asyncttwiz.protocol.BoardState): Boolean = combatRecord(board).second

    /** Whether manual controls work on [play]'s board: Automatum off, or its open choice is something to do by hand
     * (I-D28, I-D37). */
    fun manualAllowed(play: PlayState): Boolean = !play.board.automatum || byHand(play)

    /** The shared flow of [play]'s board already worked out, if any. */
    fun cachedFlow(play: PlayState): SharedFlow? = synchronized(flows) { flows[IdentityKey(play.board)] }

    /** [play]'s board as a shared flow (priority, the open decision; I2): kept for the latest boards; worked out now when
     * not yet (blocking: one engine state). Null when the board can't be taken over. [tags]: the history's tags (D33). */
    fun sharedFlow(play: PlayState, tags: List<MtgTag?>): SharedFlow? {
        synchronized(flows) { if (flows.containsKey(IdentityKey(play.board))) return flows[IdentityKey(play.board)] }
        val result = try {
            val definitions = definitions(play.definitions)
            // SYS-025 (final cast review #9): what the panel reads is worked out here, off the main thread
            SharedFlow(play, definitions, engineLog(play, definitions, tags), ::seed).prepared()
        } catch (problem: Exception) {
            AppLog.warn("automatum", "Couldn't read the shared flow: ${problem::class.java.simpleName}")
            null
        }
        synchronized(flows) { flows[IdentityKey(play.board)] = result }
        return result
    }

    private val combats = object : LinkedHashMap<IdentityKey, CombatShown>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<IdentityKey, CombatShown>?) = size > 8
    }

    /** What the table shows of combat on [play]'s board already worked out, if any. */
    fun cachedCombat(play: PlayState): CombatShown? = synchronized(combats) { combats[IdentityKey(play.board)] }

    /** What the table shows of combat on [play]'s board (I3: declarations, the damage preview, early divisions): kept
     * for the latest boards; worked out now when not yet (blocking: a few engine calls). [CombatShown.NONE] outside
     * combat or with Automatum off. [tags]: the history's tags (D33), read only when it has to be worked out. */
    fun combat(play: PlayState, tags: () -> List<MtgTag?>): CombatShown {
        if (!play.board.automatum || play.board.turn.let { TurnPosition.of(it) } !in COMBAT_STEPS) return CombatShown.NONE
        cachedCombat(play)?.let { return it }
        val result = try {
            val definitions = definitions(play.definitions)
            CombatFlow.shown(play, definitions, engineLog(play, definitions, tags()))
        } catch (problem: Exception) {
            AppLog.warn("automatum", "Couldn't work combat out: ${problem::class.java.simpleName}")
            CombatShown.NONE
        }
        synchronized(combats) { combats[IdentityKey(play.board)] = result }
        return result
    }

    private val COMBAT_STEPS = setOf(TurnPosition.DECLARE_ATTACKERS, TurnPosition.DECLARE_BLOCKERS, TurnPosition.FIRST_STRIKE_DAMAGE, TurnPosition.COMBAT_DAMAGE)

    /** The game's event log readers (one per play, for the card data read so far): each history tag is read once. */
    private val decoders = object : LinkedHashMap<String, Pair<Set<String>, EngineLog.Decoder>>(4, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Set<String>, EngineLog.Decoder>>?) = size > 4
    }

    /** The game's event logs along its history tree (one per play): each event's log is worked out once (N3). */
    private val chains = object : LinkedHashMap<String, Pair<Set<String>, EngineLog.Chain>>(4, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Set<String>, EngineLog.Chain>>?) = size > 4
    }

    /** The engine's event log from the history's [tags] (D33): this and last turn's events ([EngineLog.recent]), each tag
     * read only once per game ([EngineLog.Decoder], SYS-025); for the history tree's own [HistoryTags], from the log
     * after the nearest event already read ([EngineLog.Chain], finalreview-sync N3: no walk through the whole history).
     * Empty when it can't be read (a cast then misses "this turn" facts rather than failing). */
    fun engineLog(play: PlayState, definitions: Map<String, CardDefinition>, tags: List<MtgTag?>): List<LoggedChange> {
        if (tags is HistoryTags) return chainLog(play, definitions, tags)
        if (tags.all { it?.data.isNullOrEmpty() }) return emptyList()
        val decoder = synchronized(decoders) {
            decoders[play.id]?.takeIf { it.first == definitions.keys }?.second
                ?: EngineLog.Decoder(definitions).also { decoders[play.id] = definitions.keys to it }
        }
        return try {
            EngineLog.recent(synchronized(decoder) { decoder.log(tags, play) }, play.board.turn.number.toIntOrNull())
        } catch (problem: Exception) {
            AppLog.warn("automatum", "Engine log unreadable: ${problem::class.java.simpleName}")
            emptyList()
        }
    }

    private fun chainLog(play: PlayState, definitions: Map<String, CardDefinition>, tags: HistoryTags): List<LoggedChange> {
        val chain = synchronized(chains) {
            chains[play.id]?.takeIf { it.first == definitions.keys }?.second
                ?: EngineLog.Chain(definitions).also { chains[play.id] = definitions.keys to it }
        }
        val tree = tags.tree
        return try {
            synchronized(chain) {
                chain.log(tags.nodeId, play, play.board.turn.number.toIntOrNull()) { id ->
                    val node = tree.nodes[id] ?: return@log null
                    tree.materializationId(id)?.let { EngineLog.Event(it, node.parentId, node.tag) }
                }
            }
        } catch (problem: Exception) {
            AppLog.warn("automatum", "Engine log unreadable: ${problem::class.java.simpleName}")
            emptyList()
        }
    }

    private fun definitions(submitted: List<SubmittedDefinition>): Map<String, CardDefinition> {
        val missing = submitted.filter { !parsed.containsKey(it.digest) && it.digest !in unreadable }
        val source = definitionSource
        if (missing.isNotEmpty() && source != null) {
            val read = try { source(missing) } catch (problem: Exception) {
                AppLog.warn("automatum", "Card definitions unavailable: ${problem::class.java.simpleName}")
                emptyMap()
            }
            for ((digest, card) in read) when (card) {
                is ParsedCard.Read -> parsed[digest] = card.definition
                // I-D21: such a card plays with its printed values (EngineState.from falls back to them)
                is ParsedCard.Unreadable -> unreadable += digest
            }
        }
        return submitted.mapNotNull { s -> parsed[s.digest]?.let { s.digest to it } }.toMap()
    }

    /** For tests: forget what was parsed and worked out. */
    fun reset() {
        parsed.clear(); unreadable.clear()
        synchronized(boards) { boards.clear() }
        synchronized(plays) { plays.clear() }
        synchronized(cardPlays) { cardPlays.clear() }
        synchronized(flows) { flows.clear() }
        synchronized(combats) { combats.clear() }
        synchronized(decoders) { decoders.clear() }
        synchronized(chains) { chains.clear() }
        lastCombatRecord = null
    }

    /** One worker for the shown boards' engine views, so a board shown only in passing (catch-up playback) is skipped
     * when a newer one replaced it before the worker got to it (SYS-025). */
    internal val boardWorker = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "automatum-board").apply { isDaemon = true } }
        .asCoroutineDispatcher()
}

/** The engine's view of the shown board while Automatum is on; [ComputedBoard.NONE] while it is off. */
internal val LocalComputedBoard = staticCompositionLocalOf { ComputedBoard.NONE }

/**
 * [play]'s board as Automatum computes it for [viewer] while the game's switch is on: at once when already worked out,
 * otherwise the last worked-out board until the worker has this one (cards keep their manual display meanwhile only
 * the first time).
 */
@Composable
internal fun rememberComputedBoard(play: PlayState, viewer: String, tags: (() -> List<MtgTag?>)? = null): ComputedBoard {
    val on = play.board.automatum
    var last by remember(play.id, viewer) { mutableStateOf(ComputedBoard.NONE) }
    val ready = if (on) AutomatumBoards.cached(play, viewer) else null
    LaunchedEffect(IdentityKey(play.board), viewer, on) {
        if (!on) { last = ComputedBoard.NONE; return@LaunchedEffect }
        last = ready ?: withContext(AutomatumBoards.boardWorker) { AutomatumBoards.compute(play, viewer) }
        // I2: what the viewer may cast from their hand, ready before their hand menu or a double-tap asks (the history's
        // tags read on the worker too)
        if (tags != null) withContext(AutomatumBoards.boardWorker) { AutomatumBoards.handPlays(play, viewer, tags) }
    }
    return if (!on) ComputedBoard.NONE else ready ?: last
}

/** The tags of the history's events from the start up to [nodeId], in order (D33: the engine's event log rides on them).
 * Read through when listed only: [AutomatumBoards.engineLog] reads the log from the tree itself, from the nearest event
 * already read (finalreview-sync N3). No board is rebuilt. */
internal fun engineTags(tree: GameplayTree?, nodeId: String): List<MtgTag?> = if (tree == null) emptyList() else HistoryTags(tree, nodeId)

/** [engineTags] of [tree] up to [nodeId]: listed (walking the chain of events back once) only when read as a list. */
internal class HistoryTags(val tree: GameplayTree, val nodeId: String) : AbstractList<MtgTag?>() {
    private val tags: List<MtgTag?> by lazy {
        val tags = ArrayList<MtgTag?>()
        var id: String? = nodeId
        while (id != null) {
            val node = tree.nodes[id] ?: break
            tags += node.tag
            id = node.parentId
        }
        tags.asReversed()
    }
    override val size: Int get() = tags.size
    override fun get(index: Int): MtgTag? = tags[index]
}
