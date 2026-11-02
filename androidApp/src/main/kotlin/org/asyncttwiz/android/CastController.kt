package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.automatum.CastFlow
import org.asyncttwiz.automatum.Unmappable
import org.asyncttwiz.automatum.Unsupported
import org.asyncttwiz.protocol.*
import java.security.SecureRandom
import java.util.UUID

/**
 * Casting from the hand through Automatum (integration spec §3 I2a): one private flow at a time (I-D11). Its engine
 * calls stay on this device ([CastFlow]) and go out as one batch ([send]) when the cast is complete; "Cancel" or Back
 * drops it, sending nothing. While it is open nothing else changes the game ([open] blocks the toolkit's actions, D32);
 * looking stays free (I-D10).
 *
 * [current]: the board the player acts on; [send]: one automated batch with its request id; [tags]: the history's
 * tags up to [current] (the engine's event log, D33); [fail]: a message for the player.
 */
internal class CastController(
    private val viewer: String,
    private val current: () -> PlayState?,
    private val send: ((GameAction.PlayBatch, String) -> Boolean)?,
    private val tags: () -> List<MtgTag?>,
    private val fail: (String?) -> Unit,
) : FlowQuestions {
    /** The open cast, null when none. */
    var flow by mutableStateOf<CastFlow?>(null); private set
    /** The plays the engine offers for the card being cast (its ways: faces, alternative costs). */
    var ways by mutableStateOf<List<Input>>(emptyList()); private set
    /** The mana uses picked in the mana section (starting from the engine's suggestion). */
    override var mana by mutableStateOf<List<ManaUse>>(emptyList()); private set
    /** Targets picked so far for a slot that takes several. */
    var picked by mutableStateOf<List<TargetRef>>(emptyList()); private set
    /** The eye icon (I-D10): the panel is hidden so the board can be looked at. */
    var hidden by mutableStateOf(false); private set
    /** "Accept automatically" (D3, this player's own setting on this device): the mana section is skipped when the
     * engine's suggestion pays. */
    var acceptAutomatically by mutableStateOf(false)

    val open: Boolean get() = flow != null
    /** The open question is targets, chosen on the table: the panel is a slim bar. */
    val targeting: Boolean get() = flow?.pending is Decision.Targets
    /** Whether the picked mana pays the open payment. */
    override val paid: Boolean get() = flow?.let { f -> f.pending is Decision.ManaPayment && f.pays(mana) } == true

    /**
     * Casts (or plays, a land) the hand card [cardId] the first way the engine offers (ZON-004, ZON-007). False with a
     * message when the engine doesn't allow it now (I-D28) or a cast is already open.
     */
    fun start(cardId: String): Boolean {
        val play = current() ?: return false
        if (!play.board.automatum) return false
        if (open) { fail(BUSY); return false }
        // A shared flow waits for an answer (D32): nothing is cast meanwhile
        if (Automatum.flowOpen(play.board)) { fail(SharedFlowController.BUSY); return false }
        val definitions = AutomatumBoards.definitions(play)
        val history = tags()
        val plays = AutomatumBoards.handPlays(play, viewer, history)
        val ways = plays.of(cardId)
        val name = cardName(play, cardId)
        if (ways.isEmpty()) {
            fail(if (plays.unsupported.isNotEmpty()) "Automatum can't take over this board: ${plays.unsupported.first()}"
                else if (land(play, cardId)) "$name can't be played now." else "$name can't be cast now.")
            return false
        }
        val flow = try {
            CastFlow.start(play, definitions, viewer, AutomatumBoards.engineLog(play, definitions, history), ways.first(), seeds())
        } catch (problem: IllegalArgumentException) {
            fail("$name can't be cast now."); return false
        } catch (problem: Unsupported) {
            fail("Automatum can't take over this board: ${problem.reasons.firstOrNull()}"); return false
        }
        fail(null)
        this.ways = ways
        hidden = false
        return show(flow)
    }

    /** Answers the open question. */
    override fun answer(answer: Answer): Boolean = update { it.answer(answer) }
    override val engine get() = flow!!.state
    override fun atwCard(id: ObjectId): String? = flow?.atwCard(id)
    override fun listedAnswers(): List<Answer> = flow?.listedAnswers().orEmpty()
    private var visible: Pair<CastFlow, Set<ObjectId>>? = null
    /** I-D27: the cast runs on full information; the panel names only what the caster may see. */
    override fun shown(id: ObjectId): Boolean {
        val f = flow ?: return false
        val known = visible?.takeIf { it.first === f }?.second
            ?: arcanum.engine.Engine.visible(f.state, PlayerId(viewer)).also { visible = f to it }
        return id is ObjectId.StackItem || id in known
    }

    /** Changes the answer of an earlier section (a toggle above the current one). */
    fun change(index: Int, answer: Answer): Boolean = update { it.change(index, answer) }

    /** Opens the decision of an earlier section again (its answer and the later ones are dropped): "Change" on a
     * section whose answer is chosen on the table (targets). */
    fun back(index: Int): Boolean = update { it.back(index) }

    /** Another way of casting the card (face, alternative cost), keeping the choices that still fit. */
    fun way(input: Input): Boolean = update { it.withBegin(input) }

    /** A tap on a table item while targets are chosen (I-D5: players by their life squares). True when it was taken. */
    fun tapTarget(item: TableItem): Boolean {
        val f = flow ?: return false
        val decision = f.pending as? Decision.Targets ?: return false
        val target = targetOf(f, decision, item) ?: return true.also { fail("That isn't a legal target.") }
        fail(null)
        val max = decision.max
        if (max == 1 && decision.min <= 1) return answer(Answer.Targets(listOf(target)))
        picked = if (target in picked) picked - target else picked + target
        if (max != null && picked.size >= max) return confirmTargets()
        return true
    }

    /** Confirms the targets picked for a slot that takes several. */
    fun confirmTargets(): Boolean {
        val decision = flow?.pending as? Decision.Targets ?: return false
        if (picked.size < decision.min) return false
        return answer(Answer.Targets(picked))
    }

    /** Taps or untaps a mana source in the mana section: untapped, the first of its uses that pays (else its first). */
    override fun toggleMana(group: List<ManaUse>) {
        val f = flow ?: return
        if (mana.any { it in group }) { mana = mana.filterNot { it in group }; return }
        mana = group.firstOrNull { f.pays(mana + it) }?.let { mana + it } ?: (mana + group.first())
    }

    /** "Cast": pays with the picked mana (when the cast is at its payment) and sends the whole flow. */
    fun cast(): Boolean {
        val f = flow ?: return false
        if (f.pending is Decision.ManaPayment) return update { it.answer(Answer.ManaPayment(mana)) }
        return if (f.complete) finish(f) else false
    }

    /** "Cancel" or Back: the whole cast is dropped; nothing was sent (I-D11). */
    fun cancel() { flow = null; ways = emptyList(); mana = emptyList(); picked = emptyList(); hidden = false }

    fun toggleHidden() { hidden = !hidden }

    /** The table items that glow as legal targets of the open question: cards by id, players by their life square. */
    fun glow(play: PlayState): Set<String> {
        val f = flow ?: return emptySet()
        val decision = f.pending as? Decision.Targets ?: return emptySet()
        return decision.options.mapNotNull { target -> when (val ref = f.ref(target)) {
            is MtgRef.Player -> "life/${ref.accountId}"
            is MtgRef.Card -> ref.cardId
            is MtgRef.StackItem -> play.board.stackEffects.firstOrNull { it.item?.id == ref }?.id
        } }.toSet()
    }

    /** The board changed under the open cast (D32: the other player acted, an Undo): the cast is worked out again on it,
     * keeping the choices that still fit; dropped with a message when it can't be cast there any more. */
    fun follow(play: PlayState) {
        val f = flow ?: return
        if (f.play.board === play.board || f.play.id != play.id) return
        try {
            val definitions = AutomatumBoards.definitions(play)
            show(f.on(play, AutomatumBoards.engineLog(play, definitions, tags())), auto = false)
        } catch (problem: Exception) {
            cancel()
            fail("The table changed: ${cardName(play, f.cardId)} can't be cast now.")
        }
    }

    private fun update(step: (CastFlow) -> CastFlow): Boolean {
        val f = flow ?: return false
        val next = try { step(f) } catch (problem: IllegalArgumentException) {
            fail("Automatum doesn't allow that: ${problem.message?.take(80)}"); return false
        }
        fail(null)
        return show(next)
    }

    /** Shows [next]: sent at once when nothing is left to decide (or only a payment its suggestion makes, with
     * "Accept automatically" on); else the panel shows its open question. */
    private fun show(next: CastFlow, auto: Boolean = true): Boolean {
        picked = emptyList()
        val payment = next.pending as? Decision.ManaPayment
        mana = payment?.suggestion.orEmpty()
        if (auto && next.complete && next.steps.isEmpty()) return finish(next)
        if (auto && payment != null && acceptAutomatically && payment.suggestion.isNotEmpty() && next.pays(payment.suggestion))
            return try { finish(next.answer(Answer.ManaPayment(payment.suggestion))) } catch (_: IllegalArgumentException) { flow = next; true }
        if (next.complete && auto) return finish(next)
        flow = next
        return true
    }

    /** Sends the complete flow as one batch (I-D11, SYS-010) on the board it was worked out on. */
    private fun finish(done: CastFlow): Boolean {
        val play = current()
        val ready = if (play != null && play.board !== done.play.board) {
            try { done.on(play).takeIf { it.complete } } catch (_: Exception) { null }
                ?: run { cancel(); fail("The table changed: cast it again."); return false }
        } else done
        val route = send ?: run { fail("Casting isn't available here."); return false }
        val requestId = UUID.randomUUID().toString()
        val command = GameCommand(requestId, GameScope(ready.play.gameId, playId = ready.play.id),
            GameAction.PlayBatch(listOf(PlayAction.PassAttention)), UUID.randomUUID().toString(), "1")
        val plan = try { ready.plan(viewer, command) } catch (problem: Unmappable) {
            cancel(); fail("Automatum couldn't send that cast (${problem.message?.take(80)})."); return false
        }
        cancel()
        if (plan.actions.isEmpty()) return true
        if (!route(plan.action, requestId)) { fail("Action could not be queued. Reconnect and try again."); return false }
        return true
    }

    private fun targetOf(f: CastFlow, decision: Decision.Targets, item: TableItem): TargetRef? {
        if (item.kind == TableItemKind.LIFE) return decision.options.firstOrNull { it == TargetRef.Player(PlayerId(item.owner ?: return null)) }
        val play = f.play
        val ids = listOfNotNull(item.sourceId, item.cardId)
        return decision.options.firstOrNull { target ->
            when (val ref = f.ref(target)) {
                is MtgRef.Card -> ref.cardId in ids
                is MtgRef.StackItem -> play.board.stackEffects.any { it.id in ids && it.item?.id == ref }
                is MtgRef.Player -> false
            }
        }
    }

    private val random = SecureRandom()
    private fun seeds() = CastFlow.Seeds { RandomV1.encodeSeed(ByteArray(32).also(random::nextBytes)) }

    companion object {
        const val BUSY = "Finish or cancel the cast first."

        fun cardName(play: PlayState, cardId: String): String {
            val card = play.board.cards.singleOrNull { it.id == cardId } ?: return "That card"
            return play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces
                ?.let { faces -> faces.singleOrNull { it.faceId == card.faceId } ?: faces.firstOrNull() }?.name ?: "That card"
        }

        fun land(play: PlayState, cardId: String): Boolean {
            val card = play.board.cards.singleOrNull { it.id == cardId } ?: return false
            val front = play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces?.firstOrNull()
            return "Land" in StackContexts.types(front?.typeLine.orEmpty())
        }
    }
}
