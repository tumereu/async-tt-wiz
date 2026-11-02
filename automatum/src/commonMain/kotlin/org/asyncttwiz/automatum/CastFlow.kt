package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.change.Change
import arcanum.engine.change.Meaning
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.LoggedChange
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.PriorityState
import arcanum.engine.state.TargetRef
import arcanum.engine.view.AbilityOrigin
import org.asyncttwiz.protocol.GameCommand
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.PlayState

/**
 * Casting a spell or playing a land through the engine as a **private flow** (integration spec I-D11, §3 I2): the
 * acting client keeps its engine calls on the device and sends everything as one batch ([plan]) when the cast is
 * complete; cancelling before that only drops these local steps, and nothing reaches the other player.
 *
 * A flow is immutable: [answer] and [change] give a new flow, replayed from [play] with [begin] and the answers so far
 * (the engine names its decisions after the state, so a replay meets the same decisions again; random results are kept
 * by [Seeds] and repeat). Changing an earlier answer ([change]) keeps the later answers that still fit and drops the
 * rest, so a choice made before (a target) survives a toggle above it (kicker) whenever the engine still accepts it.
 *
 * The engine state is [player]'s view of the game (§2 step 1, D60); [EngineCall] answers the host's decisions on the way.
 */
class CastFlow private constructor(
    val play: PlayState,
    val definitions: Map<String, CardDefinition>,
    val player: String,
    val log: List<LoggedChange>,
    /** [Input.Cast] or [Input.PlayLand] of [player]. */
    val begin: Input,
    /** The answers given, in order. */
    val answers: List<Answer>,
    private val seeds: Seeds,
    /** The engine call holding every change so far. */
    val call: EngineCall,
    /** Each answered decision with its answer, in order. */
    val steps: List<Pair<Decision, Answer>>,
) {
    /** The open question, or null when the cast or land play is complete and can be sent ([plan]). */
    val pending: Decision? get() = call.pending?.decision

    /** True when nothing is left to decide. */
    val complete: Boolean get() = pending == null

    /** The ATW card being cast or played. */
    val cardId: String get() = call.ids.atw(((begin as? Input.Cast)?.card ?: (begin as Input.PlayLand).card).let { (it as ObjectId.Card).card })

    /** The engine state the cast is at now (after every step so far). */
    val state: GameState get() = call.working

    /**
     * Answers the open decision with [answer].
     * @throws IllegalArgumentException when the engine refuses it.
     */
    fun answer(answer: Answer): CastFlow {
        val open = requireNotNull(pending) { "Nothing to answer" }
        // Checked on the current state first, so a refusal doesn't cost a replay
        Engine.perform(call.working, Input.Answer(open.id, answer))
        return replayOn(play, begin, answers + answer, strict = true)
    }

    /**
     * Changes the answer of step [index] to [answer]: the flow is replayed with it; the later answers are kept while the
     * engine accepts them in order, the rest are dropped.
     * @throws IllegalArgumentException when the engine refuses [answer] there.
     */
    fun change(index: Int, answer: Answer): CastFlow {
        require(index in answers.indices) { "No step $index" }
        val next = replayOn(play, begin, answers.take(index) + answer + answers.drop(index + 1), strict = false)
        require(next.answers.size > index) { "The engine refuses that answer" }
        return next
    }

    /** The flow back at step [index]: that decision is open again, its answer and the later ones dropped. */
    fun back(index: Int): CastFlow = replayOn(play, begin, answers.take(index.coerceIn(0, answers.size)), strict = true)

    /** The same cast begun another way ([ways]: another face or cost), keeping the answers that still fit. */
    fun withBegin(input: Input): CastFlow = replayOn(play, input, answers, strict = false)

    /** The flow replayed on [newPlay] (the board changed underneath, D32): the same answers while they still fit.
     * @throws IllegalArgumentException when the cast can't be begun there any more. */
    fun on(newPlay: PlayState, newLog: List<LoggedChange> = log): CastFlow =
        replay(Context(newPlay, definitions, player, newLog, seeds), begin, answers, strict = false)

    /** Whether paying the open [Decision.ManaPayment] with [uses] completes it (the engine accepts the payment). */
    fun pays(uses: List<arcanum.engine.decision.ManaUse>): Boolean {
        val open = pending as? Decision.ManaPayment ?: return false
        return try {
            Engine.perform(call.working, Input.Answer(open.id, Answer.ManaPayment(uses)))
            true
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    /** The answers the engine lists for the open decision (a working sample for choices of several, §7.0). */
    fun listedAnswers(): List<Answer> {
        val open = pending ?: return emptyList()
        return Engine.options(call.working, open.player).filterIsInstance<Input.Answer>()
            .filter { it.decision == open.id }.map { it.answer }
    }

    /** The batch that sends the whole flow (I-D11), with [command]'s request id, as [actor]. Only once [complete]. */
    fun plan(actor: String, command: GameCommand): BatchPlan {
        check(complete) { "The cast isn't complete" }
        return EngineBatch.plan(call, actor, command)
    }

    /** The ATW reference of an engine object or player (target glow, labels). */
    fun ref(target: TargetRef): MtgRef = call.ids.ref(target)

    /** The ATW card of an engine object, when it is a card. */
    fun atwCard(id: ObjectId): String? = (id as? ObjectId.Card)?.let { call.ids.atw(it.card) }

    private fun replayOn(on: PlayState, begin: Input, answers: List<Answer>, strict: Boolean): CastFlow =
        replay(Context(on, definitions, player, log, seeds), begin, answers, strict)

    /** Random results of a flow, kept so a replay repeats them. */
    class Seeds(private val fresh: () -> String) {
        private val used = mutableListOf<String>()
        internal fun reader(): () -> String {
            var next = 0
            return { (used.getOrNull(next) ?: fresh().also { used += it }).also { next++ } }
        }
    }

    companion object {
        /**
         * Begins [begin] ([Input.Cast] or [Input.PlayLand]) for [player] on [play], then gives [answers] in order.
         * @throws IllegalArgumentException when the engine refuses to begin it, or one of [answers].
         * @throws Unsupported when the board holds something the engine can't take over.
         */
        fun start(play: PlayState, definitions: Map<String, CardDefinition>, player: String, log: List<LoggedChange>,
            begin: Input, seeds: Seeds, answers: List<Answer> = emptyList()): CastFlow =
            replay(Context(play, definitions, player, log, seeds), begin, answers, strict = true)

        internal class Context(val play: PlayState, val definitions: Map<String, CardDefinition>, val player: String,
            val log: List<LoggedChange>, val seeds: Seeds)

        internal fun replay(c: Context, begin: Input, answers: List<Answer>, strict: Boolean): CastFlow {
            val call = EngineCall(c.play, c.definitions, c.player, c.log, c.seeds.reader())
            HostPriority.settle(call)
            call.perform(begin)
            val given = mutableListOf<Answer>()
            val steps = mutableListOf<Pair<Decision, Answer>>()
            for (answer in answers) {
                val open = call.pending?.decision ?: break
                try {
                    // Checked before anything changes: a refused answer leaves the call as it was
                    Engine.perform(call.working, Input.Answer(open.id, answer))
                } catch (problem: IllegalArgumentException) {
                    if (strict) throw problem else break
                }
                call.perform(Input.Answer(open.id, answer))
                given += answer
                steps += open to answer
            }
            return CastFlow(c.play, c.definitions, c.player, c.log, begin, given, c.seeds, call, steps)
        }

        /** What [Input.Cast] [way] says beside the plain cast: its face when the card has several to cast, its
         * alternative cost ("Flashback", "Evoke") and where it is cast from; null for the plain cast of the front face. */
        fun wayLabel(definition: CardDefinition?, way: Input.Cast): String? {
            val parts = mutableListOf<String>()
            if (way.way.face != 0 || (definition?.faces?.size ?: 1) > 1) definition?.faces?.getOrNull(way.way.face)?.name?.let { parts += it }
            way.way.alternative?.let { parts += abilityText(definition, it) ?: "Alternative cost" }
            if (way.way.permission != null) parts += "With permission"
            return parts.joinToString(" · ").ifEmpty { null }
        }

        /** The printed text (or keyword) of the ability at [origin] of [definition]. */
        fun abilityText(definition: CardDefinition?, origin: AbilityOrigin): String? = when (origin) {
            is AbilityOrigin.Printed -> definition?.faces?.getOrNull(origin.face)?.abilities?.getOrNull(origin.index)?.let(::text)
            is AbilityOrigin.Expansion -> abilityText(definition, origin.keyword)
            else -> null
        }

        private fun text(ability: Ability): String? = when (ability) {
            is Keyword -> ability.text
            is Ability.Static -> ability.text
            is Ability.Activated -> ability.text
            is Ability.Triggered -> ability.text
            is Ability.Spell -> ability.text
            is Ability.SpecialAction -> ability.text
            is Ability.Unparsed -> ability.text
            else -> null
        }
    }
}

/**
 * What a player may cast or play from their hand now (§3 I2, I-D28): the engine's [Input.Cast] and [Input.PlayLand]
 * options by ATW card id, in the engine's order (the plain way first). A card without any is not castable now.
 */
class HandPlays private constructor(val byCard: Map<String, List<Input>>, val unsupported: List<String>) {
    fun of(cardId: String): List<Input> = byCard[cardId].orEmpty()

    companion object {
        val NONE = HandPlays(emptyMap(), emptyList())

        /** [viewer]'s plays from their hand on [play] (blocking: one engine `options`). Never throws. */
        fun of(play: PlayState, definitions: Map<String, CardDefinition>, viewer: String, log: List<LoggedChange>): HandPlays {
            val call = try {
                EngineCall(play, definitions, viewer, log) { error("No randomness while listing plays") }
            } catch (problem: Unsupported) {
                return HandPlays(emptyMap(), problem.reasons)
            }
            HostPriority.settle(call)
            val state = call.working
            val hand = state.players.firstOrNull { it.id.value == viewer }?.hand.orEmpty().toSet()
            val byCard = LinkedHashMap<String, MutableList<Input>>()
            for (input in Engine.options(state, PlayerId(viewer))) {
                val card = when (input) {
                    is Input.Cast -> input.card
                    is Input.PlayLand -> input.card
                    else -> continue
                }
                if (card !in hand) continue
                byCard.getOrPut(call.ids.atw((card as ObjectId.Card).card)) { mutableListOf() } += input
            }
            return HandPlays(byCard, emptyList())
        }
    }
}

/**
 * Priority while Automatum doesn't run the steps yet (integration phases I2–I3): ATW's steps and turns still change by
 * hand, which doesn't give priority (CR 117.3a), so before an engine call the host settles it as the rules would have:
 * with an empty Stack in a step with priority, the active player holds it, nobody having passed; with spells or
 * abilities on the Stack, the player who holds it keeps it (nobody: the active player). In the untap and cleanup steps
 * nobody gets priority (CR 502.4, 514.3). The change goes out with the call's batch.
 */
internal object HostPriority {
    fun settle(call: EngineCall) {
        val s = call.working
        if (s.pending != null || s.turn.step == TurnStep.Untap || s.turn.step == TurnStep.Cleanup) return
        val active = s.turn.active
        val wanted = when {
            s.stack.isEmpty() -> PriorityState(active, 0)
            s.priority.holder == null -> PriorityState(active, 0)
            else -> return
        }
        if (s.priority != wanted) call.host(Change.Priority(s.priority, wanted, listOf(Meaning.PriorityGranted)))
    }
}
