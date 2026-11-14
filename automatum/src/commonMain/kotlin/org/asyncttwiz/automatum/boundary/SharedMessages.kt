package org.asyncttwiz.automatum.boundary

import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.decision.ReplacementOption
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.TargetRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.asyncttwiz.automatum.DamageDivision
import org.asyncttwiz.automatum.SharedFlow

/*
 * Web client step 4e (docs/specs/web-client.md §1 item 3): a board's shared flow (priority, resolution, triggers, the open
 * decision of a resolution and its payments, combat's declarations and divisions; integration spec I-D11) as a session where
 * the device is (the browser's engine Web Worker; Android's in-process engine side), as casts are ([CastMessages]). The page
 * asks for the flow of the board it shows ([SharedOp.Show]: a new version of its session) and draws the shared panels from the
 * plain data it gets back ([SharedFlowValue]), never the live flow; each step names the board it is made on and the version
 * the page shows, so a step for a flow the engine side no longer keeps is refused ([FlowSessions]). Its engine work (the
 * answer's run, Autotap's suggestion with TBL-130's topmost sources, the payment around the player's taps, castability's
 * picks, the declarations of combat) is done there; the own inputs come back to be sent.
 */

/** A step of shared-flow session [session] on the flow version [version] the page shows (null: no panel shows a flow, the
 * step reads the board's own), made on the board numbered [board] ([EngineRequest.board]'s numbers). [stacks]: the members of
 * each stack of identical battlefield cards on the board acted on, topmost last (TBL-130), when the step goes across. */
@Serializable
data class SharedRequest(val session: Long, val version: Long? = null, val board: Long? = null, val op: SharedOp,
    val stacks: List<List<String>>? = null)

/** What a [SharedRequest] asks. */
@Serializable
sealed interface SharedOp {
    /** The flow of the board named, as the panel draws it, as the session's newest version. A payment opens with Autotap's
     * suggestion ([autotap]), except while the panel already shows the decision [decision]: then the picks [keep] stay. */
    @Serializable @SerialName("show") data class Show(val autotap: Boolean, val decision: String? = null,
        val keep: List<ManaUse> = emptyList()) : SharedOp
    /** [player] passes priority ("Let it resolve", CR 117.3d). */
    @Serializable @SerialName("pass") data class Pass(val player: String) : SharedOp
    /** The step arrow (TRN-057): the holder passes, or the lanes' declaration is made ([autotap]: its costs' mana paid by itself). */
    @Serializable @SerialName("step") data class Step(val autotap: Boolean) : SharedOp
    /** Who declares attackers or blockers on the board named now ([SharedAnswer.Declaring]). */
    @Serializable @SerialName("declaring") data object Declaring : SharedOp
    /** The open decision [decision] answered with [answer] and the orders after it ([then], I-D48). */
    @Serializable @SerialName("answer") data class Give(val decision: String, val answer: Answer,
        val then: List<Answer.Objects> = emptyList()) : SharedOp
    /** CR 616.1: the open decision [decision]'s replacement preference list. */
    @Serializable @SerialName("replacements") data class Replacements(val decision: String, val preference: List<ReplacementOption>) : SharedOp
    /** STK-049: the open payment (or "yes" to the open "you may pay" and its payment) made with [mana]. */
    @Serializable @SerialName("pay") data class Pay(val decision: String, val mana: List<ManaUse>) : SharedOp
    /** I-D33: the open decision answered with its early answer. */
    @Serializable @SerialName("early") data object Early : SharedOp
    /** STK-024: a source tapped (or a chip) whose uses are [ways] in the open payment; [belongs] tells which uses are its. */
    @Serializable @SerialName("tap") data class Tap(val ways: List<ManaUse>, val belongs: ManaBelongs, val what: String,
        val current: List<ManaUse>, val taps: ManaTapsWire, val autotap: Boolean) : SharedOp
    /** STK-024: [way] chosen with the corner chip of the table card [cardId], whose uses are [mine]. */
    @Serializable @SerialName("choose-way") data class ChooseWay(val cardId: String, val way: ManaUse, val mine: List<ManaUse>,
        val current: List<ManaUse>, val taps: ManaTapsWire, val autotap: Boolean) : SharedOp
    /** Whether [mana] pays the open payment (picks kept on a new board asking the same decision). */
    @Serializable @SerialName("pays") data class Pays(val mana: List<ManaUse>) : SharedOp
    /** Castability §3: what may be picked in the open choice with [picked] (nothing changes). */
    @Serializable @SerialName("selection") data class Selection(val picked: List<TargetRef>) : SharedOp
    /** The session ends ([how]); nothing is answered. */
    @Serializable @SerialName("close") data class Close(val how: String) : SharedOp
}

/**
 * A board's shared flow as the shared panels draw it: [version] names it in its session's later steps, [board] the board's
 * number. [decision]: the open decision (a division given early as its suggestion, I-D33); [priority]: who holds priority with
 * something on the Stack and nothing to decide; [holder]: who holds it now with nothing to decide (an empty Stack too, the
 * active player in an untap step); [declaring]: who declares attackers or blockers now; [early]: an early answer for the open
 * decision is given; [division]: the open division of combat damage; [listed]: the engine's listed answers (names only of what
 * the decider may see); [payment]: the payment an open "you may pay" goes on to (STK-049); [visible]: the objects the viewer may
 * be shown (I-D27); [left]: the open choice's options that have left but were face up (shown by what they were); [mana]: the
 * mana the open payment is made with now (Autotap's suggestion, or the picks kept) and [paid] whether it pays.
 */
@Serializable
data class SharedFlowValue(
    val version: Long = 0,
    val board: Long = 0,
    val decision: Decision? = null,
    val priority: String? = null,
    val holder: String? = null,
    val declaring: String? = null,
    val early: Boolean = false,
    val division: DamageDivision? = null,
    val listed: List<Answer> = emptyList(),
    val payment: Decision.ManaPayment? = null,
    val visible: Set<ObjectId> = emptySet(),
    val left: Set<ObjectId> = emptySet(),
    val mana: List<ManaUse> = emptyList(),
    val paid: Boolean = false,
) {
    /** In-process only: the live flow it was read from (never sent, never changed). */
    @Transient var flow: SharedFlow? = null

    companion object {
        /** [flow]'s plain data for [viewer] ([flow] worked out with what its panel reads, [SharedFlow.prepared]), on board
         * [board]; version, mana and paid are the session's. */
        fun of(flow: SharedFlow, viewer: String, board: Long): SharedFlowValue {
            val decision = flow.decision
            val left = (decision as? Decision.ChooseObjects)?.options.orEmpty().filterTo(HashSet()) { flow.offeredAfterLeaving(it) }
            return SharedFlowValue(0, board, decision, flow.priority, flow.holder, flow.declaring, flow.early != null, flow.division,
                flow.listedAnswers(), flow.payment, flow.visible(viewer), left).also { it.flow = flow }
        }
    }
}

/** What a [SharedRequest] came to. */
@Serializable
sealed interface SharedAnswer {
    /** The board's flow as the panel draws it. */
    @Serializable @SerialName("show") data class Show(val value: SharedFlowValue) : SharedAnswer
    /** The step's own inputs to send (I-D56 step C). */
    @Serializable @SerialName("send") data class Send(val inputs: List<Input>) : SharedAnswer
    /** The step makes no input of the viewer's own. */
    @Serializable @SerialName("empty") data object Empty : SharedAnswer
    /** The step is refused, with [message] for the player (null: none). */
    @Serializable @SerialName("refuse") data class Refuse(val message: String?) : SharedAnswer
    /** STK-024: the payment of flow version [version] after a tap ([mana], [paid]), the player's taps after it, and what it did. */
    @Serializable @SerialName("mana") data class Mana(val version: Long, val mana: List<ManaUse>, val paid: Boolean,
        val taps: ManaTapsWire = ManaTapsWire(), val said: String = "") : SharedAnswer
    /** Castability §3 for flow version [version]; null when it couldn't be worked out. */
    @Serializable @SerialName("selection") data class Selected(val version: Long, val selection: SelectionWire?) : SharedAnswer
    /** Who declares attackers or blockers now ([SharedOp.Declaring]); null: nobody. */
    @Serializable @SerialName("declaring") data class Declaring(val player: String?) : SharedAnswer
    /** Nothing to answer ([SharedOp.Close]). */
    @Serializable @SerialName("none") data object None : SharedAnswer
}
