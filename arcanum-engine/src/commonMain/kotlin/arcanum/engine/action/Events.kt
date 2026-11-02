package arcanum.engine.action

import arcanum.api.Zone
import arcanum.engine.Pending
import arcanum.engine.Progress
import arcanum.engine.change.DamageDealt
import arcanum.engine.change.Meaning
import arcanum.engine.change.Recorder
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An event a flow outside resolution performs (combat damage, the draw step's draw, a land played): what was about to
 * happen before replacements, so it can be redone with the answers to its replacement decisions (D29,
 * [Continuation.Event]). [controller] and [source] are its cause ("you" of the replacement effects that care).
 */
@Serializable
sealed interface PlannedEvent {
    val controller: PlayerId
    val source: ObjectId?

    /** One damage event (CR 120, 510.2). */
    @Serializable @SerialName("damage")
    data class Damage(
        val dealt: List<DamageDealt>, override val controller: PlayerId, override val source: ObjectId? = null,
        val cantBePrevented: Boolean = false,
    ) : PlannedEvent

    /** [player] draws [count] cards (CR 121, 504.1). */
    @Serializable @SerialName("draw")
    data class Draw(val player: PlayerId, val count: Int, override val controller: PlayerId = player, override val source: ObjectId? = null) : PlannedEvent

    /** [amount] counters of [kind] are put on each of [on] (CR 122.6): a Saga's lore counter after the draw step. */
    @Serializable @SerialName("counters")
    data class Counters(
        val on: List<ObjectId>, val kind: arcanum.api.CounterKind, val amount: Int,
        override val controller: PlayerId, override val source: ObjectId? = null,
    ) : PlannedEvent

    /** rtr chunk 07: [controller] pays the total cost to attack [cost] (CR 508.1h–j), then [attacks] become attacking creatures
     * (CR 508.1f, k). */
    @Serializable @SerialName("declare-attackers")
    data class DeclareAttackers(
        val attacks: List<arcanum.engine.input.Attack>, val cost: arcanum.api.ManaCost,
        override val controller: PlayerId, override val source: ObjectId? = null,
    ) : PlannedEvent

    /** [ids] move to [to] as one event; onto the battlefield under [under]'s control. */
    @Serializable @SerialName("move")
    data class Move(
        val ids: List<ObjectId>, val to: Zone, val meanings: List<Meaning> = emptyList(), val under: PlayerId? = null,
        val tapped: Boolean = false, override val controller: PlayerId, override val source: ObjectId? = null,
    ) : PlannedEvent
}

/**
 * Performs [PlannedEvent]s for flows outside resolution, through the event pipeline ([Actions]): an event that needs a
 * replacement decision (CR 616.1) or an "as enters" choice (CR 614.12) stops with [Continuation.Event] and is redone from
 * its start with the answers so far (D29); nothing of it is applied while it waits.
 */
internal object Events {
    /** Performs [event]; [then] is what goes on afterwards (D58). */
    fun run(recorder: Recorder, event: PlannedEvent, then: Continuation? = null, answers: List<Answer> = emptyList()): Progress {
        val mark = recorder.changes.size
        val act = Act(recorder, Asker(answers, "event@${recorder.state.nextTimestamp()}"))
        return try {
            perform(act, event)
            Progress.Done
        } catch (n: NeedDecision) {
            recorder.rollback(mark)
            Progress.Waiting(Pending(n.decision, Continuation.Event(emptyList(), answers, then, event)))
        }
    }

    fun resume(recorder: Recorder, continuation: Continuation.Event, answer: Answer): Progress {
        val event = continuation.planned ?: return Progress.Done
        return run(recorder, event, continuation.then, continuation.answers + answer)
    }

    private fun perform(act: Act, event: PlannedEvent) {
        val cause = Cause(event.controller, event.source, key = "event:${act.state.nextTimestamp()}")
        when (event) {
            is PlannedEvent.Damage -> Actions.damage(act, cause, event.dealt, event.cantBePrevented)
            is PlannedEvent.Draw -> Actions.draw(act, cause, event.player, event.count)
            is PlannedEvent.Counters -> event.on.forEach {
                Actions.putCounters(act, cause, arcanum.engine.state.TargetRef.Object(it), event.kind, event.amount)
            }
            is PlannedEvent.DeclareAttackers -> {
                act.payMana(event.controller, null, event.cost)
                arcanum.engine.combat.Combat.attack(act.state, act::record, event.attacks)
            }
            is PlannedEvent.Move -> Actions.move(act, cause, event.ids.map {
                PlannedMove(it, event.to, controller = event.under, tapped = event.tapped, meanings = event.meanings)
            })
        }
    }
}
