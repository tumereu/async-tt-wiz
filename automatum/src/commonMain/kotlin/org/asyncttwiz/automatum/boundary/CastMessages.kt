package org.asyncttwiz.automatum.boundary

import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.decision.DecisionId
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.TargetRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import org.asyncttwiz.automatum.HandCastChips
import org.asyncttwiz.automatum.HandCastOptions

/*
 * Web client step 4e (docs/specs/web-client.md §1 item 3): a cast, land play or activation as a flow session where the
 * device is (the browser's engine Web Worker; Android's in-process engine side): the page opens a session, answers its
 * steps and cancels or finishes it with [CastRequest]s, and draws the cast panel and its target bar from the [CastAnswer]s
 * it gets back ([CastPanelValue]: plain data, never a live flow). Every step after the opening names the session and the
 * version of the flow the page shows, so a step for a flow the page no longer shows is refused ([FlowSessions]). Autotap's
 * suggestion, the payment's fill-in, the castability checks and the X's most are worked out there; their results come in
 * the value.
 */

/** A step of cast session [session] on the flow version [version] the page shows (null when it opens the session). [board]:
 * the number of the board the step is made on ([org.asyncttwiz.automatum.boundary.EngineRequest.board]'s numbers), for the
 * steps that read the board the player acts on now; null for the others. [stacks]: the members of each stack of identical
 * battlefield cards on that board as the page lays them out, topmost last (TBL-130: Autotap takes the topmost), when the page
 * sends the step across; null in-process (worked out there) or without a board. */
@Serializable
data class CastRequest(val session: Long, val version: Long? = null, val board: Long? = null, val op: CastOp,
    val stacks: List<List<String>>? = null)

/** What a [CastRequest] asks. The page's own choices travel with each step ([CastChoices]): the engine side keeps only the flows. */
@Serializable
sealed interface CastOp {
    /** A hand card cast [way] (the first offered when null) with the extras [extras] (ZON-004, I-D50). */
    @Serializable @SerialName("start") data class Start(val cardId: String, val way: Input? = null, val extras: List<Int> = emptyList(),
        val autotap: Boolean = true) : CastOp
    /** A tap on a raised hand card (ZON-079, ZON-080): cast at once without a choice, else the panel. [known]: its chips. */
    @Serializable @SerialName("from-hand") data class FromHand(val cardId: String, val known: HandCastChips? = null,
        val autotap: Boolean = true) : CastOp
    /** What a card menu offers ([input]: a cast one way, a land play, an ability or special action), §3 I4. */
    @Serializable @SerialName("begin") data class Begin(val cardId: String, val input: Input, val autotap: Boolean = true) : CastOp
    /** A hand card's panel's way [way] with the extras [times] (ZON-080, ZON-082), on the board named. */
    @Serializable @SerialName("hand-choice") data class HandChoice(val way: Int, val times: List<Int>, val choices: CastChoices) : CastOp
    /** The open question answered, an earlier one changed or opened again, or another way ([CastChange]). */
    @Serializable @SerialName("update") data class Update(val change: CastChange, val choices: CastChoices) : CastOp
    /** STK-024: a source tapped (or a chip) whose uses are [ways]; [belongs] tells the payment's uses of it. */
    @Serializable @SerialName("tap") data class Tap(val ways: List<ManaUse>, val belongs: ManaBelongs, val what: String,
        val current: List<ManaUse>, val taps: ManaTapsWire, val autotap: Boolean) : CastOp
    /** STK-024: [way] chosen with the corner chip of the table card [cardId], whose uses are [mine]. */
    @Serializable @SerialName("choose-way") data class ChooseWay(val cardId: String, val way: ManaUse, val mine: List<ManaUse>,
        val taps: ManaTapsWire, val autotap: Boolean) : CastOp
    /** "Cast": pays with [mana] (Autotap's suggestion when it follows and nothing is chosen) and finishes the flow. */
    @Serializable @SerialName("cast") data class Cast(val autotap: Boolean, val custom: Boolean, val mana: List<ManaUse>) : CastOp
    /** D32: the board the player acts on changed under the flow: worked out again on the board named. [hand]: a hand card's
     * panel's way and extras ([HandCastOptions.way], [HandCastOptions.times]) to work out again there. */
    @Serializable @SerialName("follow") data class Follow(val choices: CastChoices, val hand: HandChoiceWire? = null) : CastOp
    /** A complete flow whose send waited while the board moved on: completed again on the board named. */
    @Serializable @SerialName("replan") data class Replan(val autotap: Boolean) : CastOp
    /** Castability §3: what may be picked in the open choice with [picked] so far (nothing changes). */
    @Serializable @SerialName("selection") data class Selection(val picked: List<TargetRef>) : CastOp
    /** The session ends ([how]: sent, cancelled, dropped …); nothing is answered. */
    @Serializable @SerialName("close") data class Close(val how: String) : CastOp
}

/** The page's choices a step carries: Autotap, the mana chosen so far ([keep]), whether the player changed the payment
 * ([custom]) and their own taps ([taps], STK-024). */
@Serializable
data class CastChoices(val autotap: Boolean = true, val keep: List<ManaUse>? = null, val custom: Boolean = false,
    val taps: ManaTapsWire = ManaTapsWire())

/** A hand card's panel's chosen way and extras. */
@Serializable
data class HandChoiceWire(val way: Int, val times: List<Int>)

/** A change to the open flow. */
@Serializable
sealed interface CastChange {
    @Serializable @SerialName("answer") data class Give(val answer: Answer) : CastChange
    @Serializable @SerialName("change") data class Change(val index: Int, val answer: Answer) : CastChange
    @Serializable @SerialName("back") data class Back(val index: Int) : CastChange
    @Serializable @SerialName("way") data class Way(val input: Input) : CastChange
}

/** Which uses of a payment are of the source tapped: those with its pick key ([org.asyncttwiz.automatum.ManaPicks.key]), or
 * those listed. */
@Serializable
sealed interface ManaBelongs {
    @Serializable @SerialName("key") data class Key(val key: String) : ManaBelongs
    @Serializable @SerialName("uses") data class Uses(val uses: List<ManaUse>) : ManaBelongs
}

/** STK-024: the player's own changes to a payment (app-engine's ManaTaps) as they cross. */
@Serializable
data class ManaTapsWire(val tapped: List<ManaUse> = emptyList(), val chosen: List<ManaUse> = emptyList(), val outs: List<ManaUse> = emptyList())

/** Castability §3 ([arcanum.engine.Selection]) as it crosses. */
@Serializable
data class SelectionWire(val candidates: List<TargetRef>, val done: Boolean) {
    fun selection() = arcanum.engine.Selection(candidates, done)
    companion object { fun of(s: arcanum.engine.Selection) = SelectionWire(s.candidates, s.done) }
}

/** STK-023: the most an announced number ([decision]) may be so that the cast can still be paid, and the values known not
 * to be payable ([org.asyncttwiz.automatum.HandCasts.Announceable]). */
@Serializable
data class AnnouncedMost(val decision: DecisionId, val most: Int, val unpayable: Set<Int> = emptySet())

/**
 * The open flow as the page draws it: [version] names it in the session's later steps. [cardId] is the card cast, played
 * or whose ability is activated; [begin] how; [steps] each answered decision with its answer; [pending] the open question
 * of the cast itself (null: complete) and [continuation] the engine's open continuation (the announcement it is part of).
 * The engine state the flow is at is the board's own with [changes] applied straight ([state] in-process, nothing copied):
 * [fresh] says whether the flow runs on the board the step named (else on the one the session's flow ran on).
 */
@Serializable
data class CastFlowValue(
    val version: Long,
    val fresh: Boolean,
    val cardId: String,
    val begin: Input,
    val steps: List<CastStepValue> = emptyList(),
    val pending: Decision? = null,
    val continuation: Continuation? = null,
    /** The flow's changes to the board's engine state, in the game's stored format (the page's copy of the board follows
     * them); null in-process. */
    val changes: JsonElement? = null,
) {
    /** In-process only: the flow's engine state itself (never copied, never changed). */
    @Transient var state: arcanum.engine.state.GameState? = null
}

/** An answered decision of a flow. */
@Serializable
data class CastStepValue(val decision: Decision, val answer: Answer)

/**
 * What the cast panel and its target bar draw of an open cast besides the flow itself: the mana that pays ([mana], [paid]),
 * the payment the cast comes to ([payment], the open one or the one ahead) with the engine objects among its sources that are
 * on the battlefield ([fieldSources]: tapped on the table), the engine's listed answers of the open question ([listed]),
 * the objects the caster may see ([visible], I-D27), the cast's ways when just begun ([ways]), the most of each announced
 * number ([most]), what may be picked in the open choice with nothing picked ([selection]), a hand card's panel's ways and
 * extras ([hand], null: unchanged) and whether it waits for its way ([choosing], null: unchanged).
 */
@Serializable
data class CastPanelValue(
    val flow: CastFlowValue,
    val mana: List<ManaUse> = emptyList(),
    val paid: Boolean = true,
    val listed: List<Answer> = emptyList(),
    val visible: Set<ObjectId> = emptySet(),
    val ways: List<Input>? = null,
    val clear: Boolean = true,
    val most: List<AnnouncedMost> = emptyList(),
    val payment: Decision.ManaPayment? = null,
    val fieldSources: Set<ObjectId> = emptySet(),
    val selection: SelectionWire? = null,
    val hand: HandCastOptions? = null,
    val choosing: Boolean? = null,
)

/** What a [CastRequest] came to. */
@Serializable
sealed interface CastAnswer {
    /** The flow with its open question ([CastPanelValue]). */
    @Serializable @SerialName("show") data class Show(val panel: CastPanelValue) : CastAnswer
    /** STK-024: the payment after a tap ([mana], [paid]) and the player's taps after it, on flow version [version]. */
    @Serializable @SerialName("mana") data class Mana(val version: Long, val mana: List<ManaUse>, val paid: Boolean, val taps: ManaTapsWire) : CastAnswer
    /** The complete flow ([ready]) and its own inputs to send ([inputs], I-D56 step C). */
    @Serializable @SerialName("engine") data class Engine(val ready: CastFlowValue, val inputs: List<Input>) : CastAnswer
    /** The flow is dropped with [message]. */
    @Serializable @SerialName("drop") data class Drop(val message: String) : CastAnswer
    /** The step is refused with [message]; the flow stays as it was. */
    @Serializable @SerialName("refuse") data class Refuse(val message: String) : CastAnswer
    /** Castability §3 for flow version [version]; null when it couldn't be worked out. */
    @Serializable @SerialName("selection") data class Selected(val version: Long, val selection: SelectionWire?) : CastAnswer
    /** Nothing to answer ([CastOp.Close]). */
    @Serializable @SerialName("none") data object None : CastAnswer
}

/**
 * The engine side's open flow sessions of one game, each a few versions of its flow kept ([keep]: the page names the one it
 * shows), every opening, step, refusal and end logged ([log]). Not thread-safe: the engine side's one thread.
 */
class FlowSessions<E>(private val log: EngineLog, private val keep: Int = 4) {
    private class Session<E>(val kind: String, val what: String) {
        val versions = LinkedHashMap<Long, E>()
        var last = 0L
    }

    private val sessions = HashMap<Long, Session<E>>()

    /** Sessions open now (tests and the log). */
    val open: Int get() = sessions.size

    /** Opens session [id] ([kind]: "cast", …; [what]: its card, for the log). An open one of the same id is replaced. */
    fun open(id: Long, kind: String, what: String) {
        if (sessions.put(id, Session(kind, what)) != null) log.log(true, "Flow session $id opened again ($kind $what)")
        else log.log(false, "Flow session $id opened: $kind $what")
    }

    fun isOpen(id: Long): Boolean = id in sessions

    /** [entry] as session [id]'s newest version; its number. Null when the session isn't open (closed meanwhile). */
    fun put(id: Long, entry: E, step: String): Long? {
        val session = sessions[id] ?: run { log.log(true, "Flow session $id: $step after it closed, dropped"); return null }
        val version = ++session.last
        session.versions[version] = entry
        while (session.versions.size > keep) session.versions.remove(session.versions.keys.first())
        log.log(false, "Flow session $id (${session.kind} ${session.what}): $step, version $version")
        return version
    }

    /** Session [id]'s version [version]: the entry, or why the step is stale ([Found.entry] null, [Found.stale] the reason). */
    fun get(id: Long, version: Long?, step: String): Found<E> {
        val session = sessions[id] ?: return Found<E>(null, "the session isn't open").also { stale(id, step, it.stale) }
        val v = version ?: return Found<E>(null, "no version named").also { stale(id, step, it.stale) }
        val entry = session.versions[v] ?: return Found<E>(null,
            "version $v isn't kept (newest ${session.last})").also { stale(id, step, it.stale) }
        if (v != session.last) log.log(false, "Flow session $id: $step on version $v, newer ${session.last} kept")
        return Found(entry, null)
    }

    private fun stale(id: Long, step: String, why: String?) = log.log(true, "Flow session $id: $step refused as stale: $why")

    /** Session [id] ends ([how]). */
    fun close(id: Long, how: String) {
        val session = sessions.remove(id)
        if (session == null) log.log(false, "Flow session $id closed ($how), not open")
        else log.log(false, "Flow session $id (${session.kind} ${session.what}) closed: $how, after ${session.last} versions")
    }

    class Found<E>(val entry: E?, val stale: String?)

    companion object {
        /** What a step on a flow the page no longer shows is refused with. */
        const val STALE = "The table changed: cast it again."
    }
}
