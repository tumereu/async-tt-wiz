package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Keyword
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.view.View
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.MtgRef

/**
 * Casting a spell, playing a land or activating an ability (a special action too) through the engine as a **private flow** (integration spec I-D11, §3 I2): the
 * acting client keeps its engine calls on the device and sends the player's inputs as one submission ([sending]) when the
 * cast is complete; cancelling before that only drops these local steps, and nothing reaches the other player.
 *
 * A flow is immutable: [answer] and [change] give a new flow, replayed from [source] with [begin] and the answers so far
 * (the engine names its decisions after the state, so a replay meets the same decisions again; random results are kept
 * by [Seeds] and repeat). Changing an earlier answer ([change]) keeps the later answers that still fit and drops the
 * rest, so a choice made before (a target) survives a toggle above it (kicker) whenever the engine still accepts it.
 *
 * The engine state holds full information (I-D17, I-D27: the client holds every card; the cast panel shows only what
 * [player] may see); [EngineRun] answers the host's decisions on the way.
 */
class CastFlow private constructor(
    /** Where the flow runs: the board it was begun on and its engine state. */
    val source: Source,
    val player: String,
    /** [Input.Cast], [Input.PlayLand] or [Input.Activate] of [player]. */
    val begin: Input,
    /** The answers given, in order. */
    val answers: List<Answer>,
    private val seeds: Seeds,
    /** The engine run holding every change so far. */
    val call: EngineRun,
    /** Each answered decision with its answer, in order. */
    val steps: List<Pair<Decision, Answer>>,
    /** How many objects the Stack held before [begin]. */
    internal val stackBefore: Int,
    /**
     * I-D50: the times each optional additional cost of the spell is paid (kicker, buyback, offspring, gift, …; in the
     * order of [HandCasts.extras]), chosen before the cast with the hand's cost chips: the flow answers those questions
     * itself, so they are neither [pending] nor [steps]. Null: they are asked like any other question (a cast begun from a
     * card menu).
     */
    val extras: List<Int>? = null,
) {
    /** The open question of the cast or land play itself, or null when it is complete and can be sent ([plan]). A
     * decision after it (the order or targets of triggers it caused, the legend rule) belongs to the shared flow that
     * follows (I-D11) and goes out with the batch as the game's open decision. */
    val pending: Decision? get() = call.pending?.takeIf { private(it.continuation) }?.decision

    /** True when nothing is left to decide in the cast or land play itself. */
    val complete: Boolean get() = pending == null

    /** The ATW card being cast or played, or whose ability is activated. */
    val cardId: String get() = call.ids.atw(when (begin) {
        is Input.Cast -> begin.card
        is Input.PlayLand -> begin.card
        is Input.Activate -> begin.source
        else -> error("Not a cast, land play or activation")
    }.let { (it as ObjectId.Card).card })

    /** Whether this flow activates an ability or takes a special action (not a cast or land play). */
    val activation: Boolean get() = begin is Input.Activate

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
        return replayOn(begin, answers + answer, strict = true)
    }

    /**
     * Changes the answer of step [index] to [answer]: the flow is replayed with it; the later answers are kept while the
     * engine accepts them in order, the rest are dropped.
     * @throws IllegalArgumentException when the engine refuses [answer] there.
     */
    fun change(index: Int, answer: Answer): CastFlow {
        require(index in answers.indices) { "No step $index" }
        val next = replayOn(begin, answers.take(index) + answer + answers.drop(index + 1), strict = false)
        require(next.answers.size > index) { "The engine refuses that answer" }
        return next
    }

    /** The flow back at step [index]: that decision is open again, its answer and the later ones dropped. */
    fun back(index: Int): CastFlow = replayOn(begin, answers.take(index.coerceIn(0, answers.size)), strict = true)

    /** The same cast begun another way ([ways]: another face or cost), keeping the answers that still fit. */
    fun withBegin(input: Input): CastFlow = replayOn(input, answers, strict = false)

    /** The same cast begun [input] with the optional extras [extras] (the cast panel's way and extra chips, ZON-080: times
     * each is paid, in the engine's order), keeping the answers that still fit.
     * @throws IllegalArgumentException when the engine refuses to begin it so. */
    fun withChoice(input: Input, extras: List<Int>): CastFlow = replay(Context(source, player, seeds, extras), input, answers, strict = false)

    /** The flow replayed on [newPlay] (the board changed underneath, D32): the same answers while they still fit.
     * @throws IllegalArgumentException when the cast can't be begun there any more. */
    fun on(newSource: Source): CastFlow = replay(Context(newSource, player, seeds, extras), begin, answers, strict = false)

    /** The board the flow was begun on. */
    val board: BoardState get() = source.board

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

    /** KX8GYS: whether the open question's answers may differ in what the cast costs ([Engine.costMayFall]). */
    val costMayFall: Boolean by lazy { runCatching { Engine.costMayFall(call.working) }.getOrDefault(false) }

    /** KX8GYS: the candidates of the open choice of one (a target, or an object for a cost) that leave the cast payable, when
     * they may differ ([costMayFall]); null when any will do or none is listed. Worked out once per question. */
    val payableChoices: List<arcanum.engine.state.TargetRef>? by lazy {
        val open = pending
        val one = (open is Decision.Targets && open.min <= 1) || (open is Decision.ChooseObjects && open.min <= 1)
        if (!one || !costMayFall) null else listedAnswers().mapNotNull { answer ->
            when (answer) {
                is Answer.Targets -> answer.chosen.singleOrNull()
                is Answer.Objects -> answer.chosen.singleOrNull()?.let { arcanum.engine.state.TargetRef.Object(it) }
                else -> null
            }
        }.ifEmpty { null }
    }

    /** The answers the engine lists for the open decision (a working sample for choices of several, §7.0); card names
     * and creature types only of objects the player may see ([ShownAnswers], I-D27). */
    fun listedAnswers(): List<Answer> {
        val open = pending ?: return emptyList()
        return ShownAnswers.of(call.working, open, Engine.options(call.working, open.player).filterIsInstance<Input.Answer>()
            .filter { it.decision == open.id }.map { it.answer })
    }

    /**
     * The flow completed with [payment] (the open [Decision.ManaPayment] answered with it; null when nothing is open) in
     * one replay, ready to send (I-D47, "Autotap mana sources"): [Sending.cast] is the cast alone.
     * @throws IllegalArgumentException when the engine refuses [payment], or something else is still open.
     */
    fun sending(payment: List<arcanum.engine.decision.ManaUse>?): Sending {
        val all = if (payment != null) {
            require(pending is Decision.ManaPayment) { "No payment is open" }
            answers + Answer.ManaPayment(payment)
        } else answers
        val done = replay(Context(source, player, seeds, extras), begin, all, strict = true)
        require(done.complete) { "The cast isn't complete" }
        return Sending(done)
    }

    /** A complete flow on its way out (I-D47): [cast] is the engine run of the cast alone, whose own inputs go out (the
     * device makes the passes that follow by itself). */
    class Sending internal constructor(val flow: CastFlow) {
        /** The engine run of the cast alone. */
        val cast: EngineRun get() = flow.call
    }

    /** The ATW reference of an engine object or player (target glow, labels). */
    fun ref(target: TargetRef): MtgRef = call.ids.ref(target)

    /** The ATW card of an engine object, when it is a card. */
    fun atwCard(id: ObjectId): String? = (id as? ObjectId.Card)?.let { call.ids.atw(it.card) }

    private fun replayOn(begin: Input, answers: List<Answer>, strict: Boolean): CastFlow =
        replay(Context(source, player, seeds, extras), begin, answers, strict)

    /**
     * Where a flow runs: the [board] of the game [gameId] it is begun on and a fresh engine run on it for each replay
     * ([run], given the flow's seeds).
     */
    sealed class Source(val board: BoardState, val gameId: String) {
        internal abstract fun run(seeds: () -> String): EngineRun

        /** A game on engine state (I-D56, engine-responsiveness R4a): its own [state] (full information, never changed
         * here) shown as [board]. */
        class Engine internal constructor(val state: GameState, board: BoardState, gameId: String) : Source(board, gameId) {
            override fun run(seeds: () -> String): EngineRun = EngineRun.of(state, board, gameId, seeds)
        }

        companion object {
            /** A game on engine state: [state] shown as [board]. */
            fun engine(state: GameState, board: BoardState, gameId: String): Source = Engine(state, board, gameId)
        }
    }

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
         * Begins [begin] ([Input.Cast], [Input.PlayLand] or [Input.Activate]) for [player] on [source] (a game on engine
         * state: [Source.engine]), then gives [answers] in order.
         * @throws IllegalArgumentException when the engine refuses to begin it, or one of [answers].
         */
        fun start(source: Source, player: String, begin: Input, seeds: Seeds, answers: List<Answer> = emptyList(),
            extras: List<Int>? = null): CastFlow = replay(Context(source, player, seeds, extras), begin, answers, strict = true)

        /** Whether [continuation] is part of casting or playing the card (CR 601.2, 305.1), which the caster's device
         * keeps to itself (I-D11), rather than what follows it. */
        internal fun private(continuation: Continuation): Boolean =
            continuation is Continuation.Announcing || continuation is Continuation.Event || continuation is Continuation.Played

        internal class Context(val source: Source, val player: String, val seeds: Seeds, val extras: List<Int>? = null)

        internal fun replay(c: Context, begin: Input, answers: List<Answer>, strict: Boolean): CastFlow {
            val call = c.source.run(c.seeds.reader())
            val stackBefore = call.working.stack.size
            call.perform(begin)
            val given = mutableListOf<Answer>()
            val steps = mutableListOf<Pair<Decision, Answer>>()
            fun chosen() { if (c.extras != null) HandCasts.answerExtras(call, c.extras) }
            chosen()
            for (answer in answers) {
                val open = call.pending?.takeIf { private(it.continuation) }?.decision ?: break
                try {
                    // Checked before anything changes: a refused answer leaves the call as it was
                    Engine.perform(call.working, Input.Answer(open.id, answer))
                } catch (problem: IllegalArgumentException) {
                    if (strict) throw problem else break
                }
                call.perform(Input.Answer(open.id, answer))
                given += answer
                steps += open to answer
                chosen()
            }
            return CastFlow(c.source, c.player, begin, given, c.seeds, call, steps, stackBefore, c.extras)
        }

        /** What [Input.Cast] [way] says beside the plain cast: its face when the card has several to cast ("Reel // Rend
         * (fused)" for both halves of a fuse card, CR 702.102a), its alternative cost ("Flashback", "Evoke") and where it
         * is cast from; null for the plain cast of the front face. */
        fun wayLabel(definition: CardDefinition?, way: Input.Cast): String? {
            val parts = mutableListOf<String>()
            if (way.way.fused) fusedName(definition)?.let { parts += it }
            else if (way.way.face != 0 || (definition?.faces?.size ?: 1) > 1) definition?.faces?.getOrNull(way.way.face)?.name?.let { parts += it }
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

        /** The name of a fused split spell (CR 702.102a): both halves' names, "Reel // Rend (fused)". */
        fun fusedName(definition: CardDefinition?): String? =
            definition?.faces?.takeIf { it.size > 1 }?.joinToString(" // ") { it.name }?.let { "$it (fused)" }

        /** The printed text players read for [ability] (a keyword's name for a keyword). */
        fun text(ability: Ability?): String? = when (ability) {
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
class HandPlays private constructor(val byCard: Map<String, List<Input>>) {
    fun of(cardId: String): List<Input> = byCard[cardId].orEmpty()

    companion object {
        val NONE = HandPlays(emptyMap())

        /**
         * [of] for a game on engine state (I-D56; engine-responsiveness R3, R4a): [state] is its own state (full
         * information, not changed here; the card ids are the game's) and [view] its view when the caller already has it
         * (worked out on the engine thread, nothing copied).
         */
        fun of(state: GameState, viewer: String, view: View?): HandPlays = of(state, CardIds(), viewer, view)

        private fun of(state: GameState, ids: CardIds, viewer: String, view: View?): HandPlays {
            val hand = state.players.firstOrNull { it.id.value == viewer }?.hand.orEmpty().toSet()
            val byCard = LinkedHashMap<String, MutableList<Input>>()
            for (input in Engine.options(state, PlayerId(viewer), view)) {
                val card = when (input) {
                    is Input.Cast -> input.card
                    is Input.PlayLand -> input.card
                    else -> continue
                }
                if (card !in hand) continue
                byCard.getOrPut(ids.atw((card as ObjectId.Card).card)) { mutableListOf() } += input
            }
            return HandPlays(byCard)
        }
    }
}

