package org.asyncttwiz.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import arcanum.api.CardDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.asyncttwiz.automatum.ComputedBoard
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.SubmittedDefinition
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
    }
}

/** The engine's view of the shown board while Automatum is on; [ComputedBoard.NONE] while it is off. */
internal val LocalComputedBoard = staticCompositionLocalOf { ComputedBoard.NONE }

/**
 * [play]'s board as Automatum computes it for [viewer] while the game's switch is on: at once when already worked out,
 * otherwise the last worked-out board until the worker has this one (cards keep their manual display meanwhile only
 * the first time).
 */
@Composable
internal fun rememberComputedBoard(play: PlayState, viewer: String): ComputedBoard {
    val on = play.board.automatum
    var last by remember(play.id, viewer) { mutableStateOf(ComputedBoard.NONE) }
    val ready = if (on) AutomatumBoards.cached(play, viewer) else null
    LaunchedEffect(IdentityKey(play.board), viewer, on) {
        if (!on) { last = ComputedBoard.NONE; return@LaunchedEffect }
        last = ready ?: withContext(Dispatchers.Default) { AutomatumBoards.compute(play, viewer) }
    }
    return if (!on) ComputedBoard.NONE else ready ?: last
}
