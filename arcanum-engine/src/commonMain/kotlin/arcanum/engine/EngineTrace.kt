package arcanum.engine

import arcanum.engine.decision.Continuation
import arcanum.engine.decision.DecisionId
import arcanum.engine.decision.LoopStop
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where [Engine.perform] reports what each call did, for a host's logs and bug reports: one [TraceEntry] per call, made
 * after it ends (accepted or refused). [None], the default, costs nothing: no clock is read and no entry is made. The
 * engine calls [record] on the caller's thread and never keeps the sink.
 */
fun interface EngineTrace {
    fun record(entry: TraceEntry)

    companion object {
        /** Records nothing. */
        val None: EngineTrace = EngineTrace { }
    }
}

/**
 * One [Engine.perform] call as an [EngineTrace] sees it: the [input] as given and how long the call took in
 * [micros] (monotonic). Serializable, so a host can keep entries in a report and replay their inputs.
 */
@Serializable
sealed interface TraceEntry {
    val input: Input
    val micros: Long

    /**
     * The call was accepted: it made [changes] changes in [rounds] rounds of state-based actions and triggers (CR 117.5)
     * and now waits on [pending] (null: nothing to decide). [loopStop] is set when the call stopped for a possible or
     * reported loop ([Continuation.Loop]); [over] when the game ended in it (CR 104).
     */
    @Serializable @SerialName("performed")
    data class Performed(
        override val input: Input,
        val changes: Int,
        val rounds: Int,
        val pending: PendingShape?,
        val loopStop: LoopStop? = null,
        val over: Boolean = false,
        override val micros: Long,
    ) : TraceEntry

    /** The engine refused [input] ([reason] its message; [error] the exception's class): the state is unchanged. */
    @Serializable @SerialName("refused")
    data class Refused(override val input: Input, val reason: String, val error: String, override val micros: Long) : TraceEntry
}

/** The shape of a [Pending]: the [decision]'s kind and [id], who decides it, and the kind of [continuation] waiting
 * on it (the full decision stays in the state). */
@Serializable
data class PendingShape(val decision: String, val id: DecisionId, val player: PlayerId, val continuation: String) {
    companion object {
        fun of(pending: Pending): PendingShape = PendingShape(pending.decision.kind,
            pending.decision.id, pending.decision.player, pending.continuation::class.simpleName ?: "Continuation")
    }
}
