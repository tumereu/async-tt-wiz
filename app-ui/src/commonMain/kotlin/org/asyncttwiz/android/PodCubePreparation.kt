package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID
import java.util.concurrent.ExecutorService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*

/** One Prepare cube move: [quantity] copies of [definition] from [from] to the other section. */
data class PodCubeMove(val definition: String, val from: SavedDeckSection, val quantity: Int) {
    val to get() = if (from == SavedDeckSection.MAINBOARD) SavedDeckSection.MAYBEBOARD else SavedDeckSection.MAINBOARD
    fun inverse() = PodCubeMove(definition, to, quantity)
}

/** [definitions]: the prepared cube's cards by digest, built from the card records by printing (owner 2026-11-10). */
data class PodCubePreparationState(val pod: String? = null, val cube: PodCube? = null,
    val definitions: Map<String, CardDefinition> = emptyMap(), val loading: Boolean = false, val error: String? = null,
    val feedback: TransferFeedback? = null, val undo: List<PodCubeMove> = emptyList(),
    /** False while the server can't be reached after a move: the screen is read-only (LOB-050). */
    val connected: Boolean = true) {
    val open get() = pod != null
    fun count(section: SavedDeckSection) = cube?.entries.orEmpty().filter { it.section == section }.sumOf { it.quantity.toLong() }
}

/** The prepared cube with [move] applied; copies join the target section's entry of the same card. */
fun PodCube.moved(move: PodCubeMove): PodCube? {
    val source = entries.firstOrNull { it.definition == move.definition && it.section == move.from } ?: return null
    if (source.quantity < move.quantity) return null
    val result = entries.mapNotNull {
        if (it !== source) it else it.copy(quantity = it.quantity - move.quantity).takeIf { left -> left.quantity > 0 }
    }.toMutableList()
    val target = result.indexOfFirst { it.definition == move.definition && it.section == move.to }
    if (target < 0) result += source.copy(quantity = move.quantity, section = move.to)
    else result[target] = result[target].copy(quantity = result[target].quantity + move.quantity)
    return copy(entries = result)
}

/**
 * Prepare cube (DRF-141): the pod creator moves cards between the pod's Main and Maybe before drafting. The server's
 * prepared cube is the truth; moves show at once and are sent in order, and the accepted cube is adopted when none is
 * left in flight. Undo sends the inverse move. [changed] refreshes the pod (its source counts and supply). A move that
 * can't reach the server makes the screen read-only ("Disconnected", LOB-050) until the cube reloads; it is tried
 * again every [RECONNECT_MILLIS], each attempt [wait]ing first.
 */
class PodCubePreparation(private val api: Pods, private val credential: () -> String?,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit,
    private val wait: suspend (Long) -> Unit = { delay(it) }, private val changed: () -> Unit) {
    companion object { const val RECONNECT_MILLIS = 5_000L }
    var state by mutableStateOf(PodCubePreparationState())
        private set
    /** The phone's card records, which the prepared cube's cards are built from (owner 2026-11-10). */
    var cardRecords: SavedCardRecords = SavedCardRecords.NONE
    @Volatile private var epoch = 0L
    private var inFlight = 0
    private var serial = 0L
    private var reconnecting = false

    fun open(pod: String) {
        val token = credential() ?: return
        val generation = ++epoch
        inFlight = 0
        state = PodCubePreparationState(pod = pod, loading = true)
        worker.execute {
            if (generation != epoch) return@execute
            val outcome = runCatching { runBlocking {
                val cube = serviceRetry(current = { generation == epoch }) { api.cube(token, pod) }
                val faces = cardRecords.read(cube.entries.mapNotNull { it.printingId })
                cube to cube.entries.distinctBy { it.definition }.associate { entry ->
                    entry.definition to faces.definition(SavedCard(entry.section, entry.printingId, entry.name, entry.quantity)) }
            } }
            post {
                if (generation != epoch) return@post
                state = outcome.fold({ (cube, definitions) -> state.copy(cube = cube, definitions = definitions, loading = false) },
                    { state.copy(loading = false, error = "The cube could not be loaded. Go back and try again.") })
            }
        }
    }

    fun close() {
        if (!state.open) return
        reset()
        changed()
    }

    /** Forgets the screen without refreshing the pod (sign-out, pod change). */
    fun reset() { epoch++; inFlight = 0; reconnecting = false; state = PodCubePreparationState() }

    fun dismissError() { state = state.copy(error = null) }


    /** Moves one copy, or all copies, of a card to the other section. */
    fun move(digest: String, from: SavedDeckSection, all: Boolean): Boolean {
        val quantity = state.cube?.entries?.firstOrNull { it.definition == digest && it.section == from }?.quantity ?: return false
        return send(PodCubeMove(digest, from, if (all) quantity else 1), undoable = true)
    }

    fun undo() { state.undo.lastOrNull()?.let { last -> if (send(last.inverse(), undoable = false)) state = state.copy(undo = state.undo.dropLast(1)) } }

    private fun send(move: PodCubeMove, undoable: Boolean): Boolean {
        val pod = state.pod ?: return false
        if (!state.connected) return false
        val token = credential() ?: return false
        val cube = state.cube?.moved(move) ?: return false
        val generation = epoch
        val name = state.definitions[move.definition]?.name ?: "Card"
        state = state.copy(cube = cube, error = null, feedback = TransferFeedback(++serial, "${move.quantity}× $name → ${move.to.sectionLabel()}"),
            undo = if (undoable) (state.undo + move).takeLast(PreparationUndo.LIMIT) else state.undo)
        inFlight++
        val request = MovePodCubeCards(UUID.randomUUID().toString(), move.definition, move.from, move.quantity)
        worker.execute {
            if (generation != epoch) return@execute
            val outcome = runCatching { runBlocking { serviceRetry(wait, current = { generation == epoch }) { api.moveCube(token, pod, request) } } }
            post {
                if (generation != epoch) return@post
                inFlight--
                outcome.onSuccess { accepted -> if (inFlight == 0) state = state.copy(cube = accepted) }
                outcome.onFailure { failure ->
                    if (transientServiceFailure(failure)) {
                        // Unreachable: read-only until the server's cube can be read again, then that one is shown.
                        state = state.copy(undo = emptyList(), connected = false)
                        reconnect(pod, token, generation)
                    } else {
                        // The shown cube no longer matches the server's: start again from the accepted one.
                        state = state.copy(undo = emptyList(), error = "The move was not saved. The cube was reloaded.")
                        reload(pod, token, generation)
                    }
                }
            }
        }
        return true
    }

    /** One reload attempt per worker task, so other pod work runs between attempts. */
    private fun reconnect(pod: String, token: String, generation: Long) {
        if (reconnecting) return
        reconnecting = true
        worker.execute {
            if (generation != epoch) return@execute
            val outcome = runCatching { runBlocking { wait(RECONNECT_MILLIS); api.cube(token, pod) } }
            post {
                if (generation != epoch) return@post
                reconnecting = false
                outcome.onSuccess { if (inFlight == 0) state = state.copy(cube = it, connected = true) else reconnect(pod, token, generation) }
                outcome.onFailure { failure ->
                    if (transientServiceFailure(failure)) reconnect(pod, token, generation)
                    else state = state.copy(connected = true, error = "The cube could not be loaded. Go back and try again.")
                }
            }
        }
    }

    private fun reload(pod: String, token: String, generation: Long) {
        worker.execute {
            if (generation != epoch) return@execute
            val outcome = runCatching { runBlocking { serviceRetry(current = { generation == epoch }) { api.cube(token, pod) } } }
            post { if (generation == epoch && inFlight == 0) outcome.onSuccess { state = state.copy(cube = it) } }
        }
    }
}
