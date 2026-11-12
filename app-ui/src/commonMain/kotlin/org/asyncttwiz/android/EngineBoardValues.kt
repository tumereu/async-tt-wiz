package org.asyncttwiz.android

import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.view.View
import org.asyncttwiz.automatum.CardPlays
import org.asyncttwiz.automatum.CombatFlow
import org.asyncttwiz.automatum.CombatShown
import org.asyncttwiz.automatum.ComputedBoard
import org.asyncttwiz.automatum.EngineProjection
import org.asyncttwiz.automatum.HandPlays
import org.asyncttwiz.automatum.SharedFlow
import org.asyncttwiz.automatum.boundary.EngineAnswer
import org.asyncttwiz.automatum.boundary.EngineRequest
import org.asyncttwiz.automatum.boundary.PlanningBoard
import org.asyncttwiz.automatum.boundary.BoardTransfer
import org.asyncttwiz.automatum.boundary.EngineChannel
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.TableState
import kotlin.concurrent.Volatile
import org.asyncttwiz.appui.CountDownLatch
import org.asyncttwiz.appui.currentThread
import org.asyncttwiz.appui.simpleClassName

/**
 * Engine-responsiveness R3, R4a: what the engine thread works out for a board of a game on engine state as it builds it
 * ([EngineGame]), from its one view of the state (R2), carried by the board ([TableBoard.values]):
 * - [computed]: the computed card values ([ComputedBoard]: P/T, keywords, type and colour changes, until-end-of-turn
 *   marks), made with the board: the table never shows the previous board's with it;
 * - the player's hand plays ([HandPlays]): worked out (through the engine boundary in-process, [EngineBoundary]) on the engine thread with the board it publishes as the result of
 *   an action ([EngineGame.Shown]), or on the engine thread when asked first. An own action's board (its early board, or
 *   its whole outcome's) gets them and its card menus' plays right after it is published, before the engine thread's
 *   follow-up work (the rest of the action's event, the device's own steps): they are promised ([promisePlays]) before
 *   the board goes out, so a reader on another thread (the table's) waits for them alone, not for that work;
 * - the card menus' plays ([CardPlays]), the shared flow ([SharedFlow]: priority, the open decision) and what the table
 *   shows of combat ([CombatShown]): lazy, on the engine thread when first asked, once per board (a board published again
 *   unchanged keeps them).
 *
 * In the browser the plays are asked of the engine's Web Worker instead ([askPlays], [EngineBoundary.remote]): they
 * arrive later on the page's thread, and a reader that can't wait for them works them out on the page.
 *
 * [state] is the game's own state the board shows (full information, never changed); [board] the board itself; [table]
 * the game's table state beside it (what the worker gets with the board: null, nothing goes to it).
 */
class EngineBoardValues constructor(
    val viewer: String,
    val computed: ComputedBoard,
    private val board: EngineProjection.Board,
    private val gameId: String,
    private val state: GameState,
    private val view: View?,
    private val visible: Set<ObjectId>,
    private val engine: EngineWorker,
    private val table: TableState? = null,
) {
    /** TRN-062 (owner problem report 5DDSKK): the cards the viewer puts on the bottom of their library now, right after a
     * mulligan ([AutomatumOpening.bottomOwed]); worked out with the board, on the engine thread. */
    val openingBottom: Int = try { AutomatumOpening.bottomOwed(state, viewer) } catch (problem: Exception) {
        AppLog.warn("automatum", "Couldn't read the opening's bottom: ${simpleClassName(problem)}")
        0
    }

    /** This board's number on the engine boundary ([EngineRequest.board]). */
    private val number: Long = numbers.incrementAndGet()

    /** Engine thread: the planning request [request] on this board, through the engine boundary in-process ([EngineBoundary]). */
    private fun plan(request: (seq: Long) -> EngineRequest): EngineAnswer =
        EngineBoundary.inProcess.call(request(EngineBoundary.inProcess.next()), PlanningBoard(state, view, visible))

    /** The hand plays once worked out (any thread reads; the engine thread writes). */
    @Volatile var handPlays: HandPlays? = null
        private set

    /** The card menus' plays once worked out. */
    @Volatile var cardPlays: CardPlays? = null
        private set

    /** The hand plays, worked out on the engine thread now when not yet (waits for it; inline on it), or, when promised
     * ([promisePlays]), as soon as the engine thread has them. */
    fun handPlays(): HandPlays = handPlays ?: promised()?.handPlays ?: engine.call { workedHandPlays() }

    /** Engine thread: the hand plays, worked out now when not yet. Never throws (as the table's did: none on failure). */
    fun workedHandPlays(): HandPlays = handPlays ?: when (val answer = plan { EngineRequest.HandPlaysOf(it, gameId, number, viewer) }) {
        is EngineAnswer.HandPlaysAnswer -> answer.plays.also(::logOffZone)
        // A failure is logged by the boundary
        else -> HandPlays.NONE
    }.also { handPlays = it }

    /** ZON-079: the cards from other zones the hand row shows on this board. */
    private fun logOffZone(plays: HandPlays) {
        if (plays.offZone.isNotEmpty()) AppLog.info("automatum", "Hand row from other zones on ${board.board.turn.number}: " +
            plays.offZone.joinToString { id -> "$id (${board.board.cards.singleOrNull { it.id == id }?.containerId})" })
    }

    /**
     * Engine thread, as the board goes out (the published board's hand plays, an own action's plays): the browser asks
     * the engine's Web Worker for this board's hand plays and card menus' plays ([askPlays]); Android works the hand plays
     * out now, in-process ([workedHandPlays]).
     */
    fun boardPlays() {
        if (!askPlays()) workedHandPlays()
    }

    /** The worker the plays were asked of and what waits for their answers (the page's one thread: the browser only). */
    private var askedOf: EngineChannel? = null
    private var handAsked = false
    private var cardAsked = false
    private val waiters = ArrayList<() -> Unit>()
    private val wake: () -> Unit = { handAsked = false; cardAsked = false; woken() }

    /**
     * The browser (web client step 4): asks the engine's Web Worker ([EngineBoundary.remote]) for this board's hand plays
     * and card menus' plays not yet worked out or asked; the answers set them later, on the page's thread, and wake what
     * waits for them ([awaitingPlays]). A failed answer leaves them to be worked out on the page when read. False when
     * there is no worker (Android: in-process, [workedHandPlays]) or nothing to send it.
     */
    fun askPlays(): Boolean {
        val channel = EngineBoundary.remote ?: return false
        val table = table ?: return false
        if (handPlays != null && cardPlays != null) return true
        askedOf = channel
        val transfer = transfer(table)
        if (handPlays == null && !handAsked) {
            handAsked = true
            handSeq = channel.next()
            EngineBoundary.waiting(gameId, wake)
            channel.ask(EngineRequest.HandPlaysOf(handSeq, gameId, number, viewer), null, transfer) { answered(it) }
        }
        if (cardPlays == null && !cardAsked) {
            cardAsked = true
            cardSeq = channel.next()
            EngineBoundary.waiting(gameId, wake)
            channel.ask(EngineRequest.CardPlaysOf(cardSeq, gameId, number, viewer), null, transfer) { answered(it) }
        }
        return true
    }

    /** An answer from the worker (the page's thread). */
    private fun answered(answer: EngineAnswer) {
        when (answer) {
            is EngineAnswer.HandPlaysAnswer -> { handAsked = false; if (handPlays == null) { handPlays = answer.plays; logOffZone(answer.plays) } }
            is EngineAnswer.CardPlaysAnswer -> { cardAsked = false; if (cardPlays == null) cardPlays = answer.plays }
            // Failed or missing (logged by the boundary): worked out on the page when read
            else -> when (answer.seq) {
                handSeq -> handAsked = false
                cardSeq -> cardAsked = false
                else -> { handAsked = false; cardAsked = false }
            }
        }
        if (!handAsked && !cardAsked) EngineBoundary.stopWaiting(gameId, wake)
        woken()
    }

    /** The numbers of the requests asked of the worker. */
    private var handSeq = -1L
    private var cardSeq = -1L

    private fun woken() {
        val woken = waiters.toList()
        waiters.clear()
        woken.forEach { it() }
    }

    /**
     * The browser: whether the worker is still working out this board's hand plays ([hand]) or card menus' plays for
     * it; [wake] then runs once an answer has arrived (or the worker is lost). False on Android, once they are known, and
     * when they weren't asked of the worker or its answer failed (a reader then works them out on the page).
     */
    fun awaitingPlays(hand: Boolean, wake: () -> Unit): Boolean {
        if (askedOf == null || askedOf !== EngineBoundary.remote) return false
        val waiting = if (hand) handPlays == null && handAsked else cardPlays == null && cardAsked
        if (waiting && wake !in waiters) waiters += wake
        return waiting
    }

    /** How the worker gets this board when it doesn't hold it: a snapshot (the definitions written once per game). */
    private fun transfer(table: TableState): BoardTransfer = object : BoardTransfer {
        override fun snapshot(): BoardTransfer.Snapshot {
            val definitions = state.objects.values.mapNotNull { it.definition }.distinct()
            val known = codecs[gameId]?.takeIf { it.definitions == definitions }
                ?: GameCodec(definitions, EngineJson(GameDefinitions(definitions))).also { codecs[gameId] = it }
            return BoardTransfer.Snapshot(known.encoded, known.codec.encodeModel(GameModel(state, table)))
        }
    }

    /** A game's definitions and their codec, encoded once ([transfer]). */
    private class GameCodec(val definitions: List<arcanum.api.CardDefinition>, val codec: EngineJson) {
        val encoded: String by lazy { codec.encodeDefinitions() }
    }

    /** The card menus' plays, worked out on the engine thread when not yet (waits for it; inline on it), or, when
     * promised ([promisePlays]), as soon as the engine thread has them. */
    fun cardPlays(): CardPlays = cardPlays ?: promised()?.cardPlays ?: engine.call { workedCardPlays() }

    /** Engine thread: the card menus' plays, worked out now when not yet. Never throws (none on failure). */
    private fun workedCardPlays(): CardPlays = cardPlays ?: when (val answer = plan { EngineRequest.CardPlaysOf(it, gameId, number, viewer) }) {
        is EngineAnswer.CardPlaysAnswer -> answer.plays
        // A failure is logged by the boundary
        else -> CardPlays.NONE
    }.also { cardPlays = it }

    /** An open promise of the plays ([promisePlays]) and the thread that made it (the engine thread). */
    private class Promise(val kept: CountDownLatch, val by: Any)
    @Volatile private var promise: Promise? = null

    /**
     * Engine thread, before an own action's board goes out: its hand plays and card menus' plays are worked out next
     * ([workPlays]), before any other engine work, so a reader on another thread waits for them alone instead of
     * queueing behind the engine thread's follow-up work. Nothing when both are already worked out.
     */
    fun promisePlays() {
        if (handPlays == null || cardPlays == null) promise = Promise(CountDownLatch(1), currentThread())
    }

    /** Engine thread: the hand plays and card menus' plays worked out now (when not yet; the browser asks its worker,
     * [askPlays]); keeps [promisePlays]. */
    fun workPlays() {
        try { if (!askPlays()) { workedHandPlays(); workedCardPlays() } } finally { promise?.kept?.countDown(); promise = null }
    }

    /** Waits for an open promise of the plays (never on the thread that made it): this, or null when none is open. */
    private fun promised(): EngineBoardValues? {
        val open = promise ?: return null
        if (open.by === currentThread()) return null
        open.kept.await()
        return this
    }

    /** The shared flow once worked out ([sharedFlow]); null until then (and when there is none). */
    @Volatile var sharedFlow: SharedFlow? = null
        private set
    @Volatile private var flowWorked = false

    /** The board's shared flow (priority, the open decision; I2), worked out on the engine thread when not yet (waits for
     * it; inline on it), with what its panel reads ([SharedFlow.prepared], SYS-025). Null when it can't be read. */
    fun sharedFlow(): SharedFlow? = if (flowWorked) sharedFlow else engine.call {
        if (!flowWorked) {
            sharedFlow = try { SharedFlow.of(state, board.board, gameId, DeviceRandom::seed).prepared() } catch (problem: Exception) {
                AppLog.warn("automatum", "Couldn't read the shared flow: ${simpleClassName(problem)}")
                null
            }
            flowWorked = true
        }
        sharedFlow
    }

    /** What the table shows of combat once worked out ([combat]); null until then. */
    @Volatile var combat: CombatShown? = null
        private set

    /** What the table shows of combat on the board (I3: declarations, the damage preview, early divisions), worked out on
     * the engine thread when not yet (waits for it; inline on it); [CombatShown.NONE] outside combat. */
    fun combat(): CombatShown = combat ?: engine.call {
        combat ?: try { CombatFlow.shown(state, board.board, gameId) } catch (problem: Exception) {
            AppLog.warn("automatum", "Couldn't work combat out: ${simpleClassName(problem)}")
            CombatShown.NONE
        }.also { combat = it }
    }

    /** CMB-040 (owner, 2026-11-05): these values for [next], the board with more drawn on its lanes (the blocks the
     * attacker places to assume): its combat worked out anew on it, the rest shared. */
    fun onLanes(next: org.asyncttwiz.protocol.BoardState): EngineBoardValues =
        EngineBoardValues(viewer, computed, board.copy(board = next), gameId, state, view, visible, engine, table).also {
            it.handPlays = handPlays; it.cardPlays = cardPlays
        }

    companion object {
        /** The boards' numbers on the engine boundary. */
        private val numbers = org.asyncttwiz.appui.AtomicLong()

        /** The browser: each game's definitions as last sent to the worker ([transfer]; the page's one thread). */
        private val codecs = HashMap<String, GameCodec>()

        /** Tests and measurements: how many computed boards the engine thread made with its boards. */
        @Volatile var made = 0
            private set

        /**
         * Engine thread: the values of [board], just projected from the game [gameId]'s [state] for [viewer] ([view] and
         * [visible]: the model's own, R2).
         */
        fun make(board: EngineProjection.Board, gameId: String, state: GameState, viewer: String, view: View?,
            visible: Set<ObjectId>, engine: EngineWorker, table: TableState? = null): EngineBoardValues {
            val shown = try { view ?: arcanum.engine.Engine.view(state) } catch (problem: Exception) { null }
            val computed = try {
                ComputedBoard.of(board.board, board.definitions, AutomatumBoards.definitions(board.definitions), viewer, state,
                    shown ?: arcanum.engine.Engine.view(state))
            } catch (problem: Exception) {
                AppLog.warn("automatum", "Couldn't work the board out: ${simpleClassName(problem)}")
                ComputedBoard.failed("Automatum couldn't work this board out (${problem.message?.take(120) ?: simpleClassName(problem)})")
            }
            made++
            return EngineBoardValues(viewer, computed, board, gameId, state, shown, visible, engine, table)
        }

        /** For tests: forget the count. */
        fun reset() { made = 0 }
    }
}
