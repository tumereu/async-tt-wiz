package org.asyncttwiz.android

import arcanum.api.CardDefinition
import org.asyncttwiz.automatum.CardPlays
import org.asyncttwiz.automatum.CombatShown
import org.asyncttwiz.automatum.ComputedBoard
import org.asyncttwiz.automatum.HandPlays
import org.asyncttwiz.automatum.SharedFlow
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.storage.ParsedCard
import org.asyncttwiz.appui.newSingleThreadExecutor
import kotlin.concurrent.Volatile
import org.asyncttwiz.appui.simpleClassName
import org.asyncttwiz.appui.concurrentMapOf
import org.asyncttwiz.appui.newConcurrentKeySet
import org.asyncttwiz.appui.asCoroutineDispatcher

/**
 * Automatum (integration spec §3 I1): the engine's view of the table. A board of a game on engine state ([TableBoard]
 * with its [TableBoard.values]) brings its computed values, plays, shared flow and combat from the engine thread
 * ([EngineBoardValues], engine-responsiveness R3): read from there, nothing is rebuilt from the board here. Card
 * definitions are parsed on the device from the game's own card data (I-D2) by [definitionSource], which the app
 * installs (its [org.asyncttwiz.storage.CardDefinitions] on the account's storage), and kept in memory by digest.
 */
object AutomatumBoards {
    /** [play]'s computed board as [viewer] sees it when already worked out (the engine thread's). */
    fun cached(play: TableBoard, viewer: String): ComputedBoard? = play.values?.takeIf { it.viewer == viewer }?.computed

    /** [play]'s computed board (the engine thread's), [ComputedBoard.NONE] without one. */
    fun compute(play: TableBoard, viewer: String): ComputedBoard = cached(play, viewer) ?: ComputedBoard.NONE

    /** The game's card definitions the device could read, by digest. */
    fun definitions(play: TableBoard): Map<String, CardDefinition> = definitions(play.definitions)

    /** What [viewer] may cast or play from their hand on [play], when already worked out. */
    fun cachedHandPlays(play: TableBoard, viewer: String): HandPlays? = play.values?.takeIf { it.viewer == viewer }?.handPlays

    /** What [viewer] may cast or play from their hand on [play] (worked out on the engine thread). */
    fun handPlays(play: TableBoard, viewer: String): HandPlays =
        play.values?.takeIf { it.viewer == viewer }?.handPlays() ?: HandPlays.NONE

    /** What the engine offers [viewer] for each card on [play], when already worked out. */
    fun cachedCardPlays(play: TableBoard, viewer: String): CardPlays? = play.values?.takeIf { it.viewer == viewer }?.cardPlays

    /** What the engine offers [viewer] for each card on [play] (worked out on the engine thread). */
    fun cardPlays(play: TableBoard, viewer: String): CardPlays =
        play.values?.takeIf { it.viewer == viewer }?.cardPlays() ?: CardPlays.NONE

    /** Whether [play]'s board has a shared flow to show (I2): an open decision, or something on the Stack. */
    fun sharedFlowShown(play: TableBoard): Boolean = play.flowOpen || play.board.stackEffects.any { it.item != null }

    /** The shared flow of [play]'s board already worked out, if any (the engine thread's). */
    fun cachedFlow(play: TableBoard): SharedFlow? = play.values?.sharedFlow

    /** [play]'s board as a shared flow (priority, the open decision; I2), worked out now when not yet (blocking, on the
     * engine thread, R4a). */
    fun sharedFlow(play: TableBoard): SharedFlow? = play.values?.sharedFlow()

    /** Web client step 4e: the plain data of [play]'s shared flow already worked out, if any (the engine thread's). */
    fun cachedSharedValue(play: TableBoard): org.asyncttwiz.automatum.boundary.SharedFlowValue? = play.values?.sharedValue

    /** [play]'s shared flow's plain data, worked out now when not yet (blocking, on the engine thread). */
    fun sharedValue(play: TableBoard): org.asyncttwiz.automatum.boundary.SharedFlowValue? = play.values?.sharedValue()

    /** What the table shows of combat on [play]'s board already worked out, if any (the engine thread's). */
    fun cachedCombat(play: TableBoard): CombatShown? = play.values?.combat

    /** What the table shows of combat on [play]'s board, worked out now when not yet (blocking, on the engine thread,
     * R4a); [CombatShown.NONE] outside combat. */
    fun combat(play: TableBoard): CombatShown {
        if (TurnPosition.of(play.board.turn) !in COMBAT_STEPS) return CombatShown.NONE
        return play.values?.combat() ?: CombatShown.NONE
    }

    /** Web client step 4e: whether [play]'s combat is asked of the device in the browser's worker ([askCombat]), never worked
     * out on the page. */
    fun remoteCombat(play: TableBoard): Boolean = play.values?.workerBoard == true

    /** What the table shows of combat on [play]'s board, given to [done]: [CombatShown.NONE] outside combat; in-process worked
     * out now when not yet (blocking, on the engine thread); a board the worker's device published asks it, [done] later. */
    fun askCombat(play: TableBoard, done: (CombatShown) -> Unit) {
        if (TurnPosition.of(play.board.turn) !in COMBAT_STEPS) return done(CombatShown.NONE)
        val values = play.values ?: return done(CombatShown.NONE)
        values.askCombat(done)
    }

    /** Parses (or reads from the device cache) the definitions of the given card data. Blocking. */
    @Volatile var definitionSource: ((Collection<SubmittedDefinition>) -> Map<String, ParsedCard>)? = null

    private val parsed = concurrentMapOf<String, CardDefinition>()
    private val unreadable = newConcurrentKeySet<String>()

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

    private val COMBAT_STEPS = setOf(TurnPosition.DECLARE_ATTACKERS, TurnPosition.DECLARE_BLOCKERS, TurnPosition.FIRST_STRIKE_DAMAGE, TurnPosition.COMBAT_DAMAGE)

    fun definitions(submitted: List<SubmittedDefinition>): Map<String, CardDefinition> {
        val missing = submitted.filter { !parsed.containsKey(it.digest) && it.digest !in unreadable }
        val source = definitionSource
        if (missing.isNotEmpty() && source != null) {
            val read = try { source(missing) } catch (problem: Exception) {
                AppLog.warn("automatum", "Card definitions unavailable: ${simpleClassName(problem)}")
                emptyMap()
            }
            for ((digest, card) in read) when (card) {
                is ParsedCard.Read -> parsed[digest] = card.definition
                // I-D21: such a card plays with its printed values
                is ParsedCard.Unreadable -> unreadable += digest
            }
        }
        return submitted.mapNotNull { s -> parsed[s.digest]?.let { s.digest to it } }.toMap()
    }

    /** For tests: forget what was parsed and worked out. */
    fun reset() {
        parsed.clear(); unreadable.clear()
        EngineBoardValues.reset()
        lastCombatRecord = null
    }

    /** One worker for the shown boards' engine views, so a board shown only in passing (catch-up playback) is skipped
     * when a newer one replaced it before the worker got to it (SYS-025). */
    val boardWorker = newSingleThreadExecutor("automatum-board", daemon = true)
        .asCoroutineDispatcher()
}
