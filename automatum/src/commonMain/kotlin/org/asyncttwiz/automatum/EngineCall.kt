package org.asyncttwiz.automatum

import arcanum.api.CardDefinition
import arcanum.engine.Engine
import arcanum.engine.Pending
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.FilledObject
import arcanum.engine.decision.RandomKind
import arcanum.engine.input.Input
import arcanum.engine.state.CardId
import arcanum.engine.state.GameState
import arcanum.engine.state.LoggedChange
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.CommandIds
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.RandomV1

/**
 * One engine call as ATW hosts it (integration spec §2): the engine state built from [state] ([EngineState.from]),
 * [perform] with a player's input, and every host decision answered inside the same call (D25, D60):
 *
 * - randomness with ATW's own seeded randomness ([RandomV1], fresh seeds from [seeds]); a shuffle's order is the one
 *   ATW's `ShuffleLibrary` gives for the same seed, so the batch can say it with that event;
 * - host fills from ATW's card data (every client has the game's cards and their definitions);
 * - a step another player decides in their own view ([Decision.OtherView], D31): its identities are filled in here;
 *   when that player has to decide something there, the call stops and the open flow is handed to them;
 * - a player's decision answered early (a pre-event, I-D33, [PreAnswers]) with that answer, when the engine takes it.
 *
 * The call ends when the engine waits for a player ([pending]) or is done. [changes] are everything it did, in order,
 * for [EngineBatch.plan] to turn into one ATW batch.
 */
class EngineCall(
    val state: PlayState,
    val definitions: Map<String, CardDefinition>,
    val viewer: String? = null,
    val log: List<LoggedChange> = emptyList(),
    private val seeds: () -> String,
) {
    /** The engine state the call starts from. */
    val start: GameState = EngineState.from(state, definitions, viewer, log)
    /** The state after [changes]. */
    val working: GameState = start.deepCopy()
    val changes = mutableListOf<Change>()
    internal val ids = CardIds.of(state.board)
    internal val shuffles = mutableListOf<ShuffleAnswer>()
    /** Card ids a viewer's shuffle made (D60), by the ATW card each one is. */
    internal val hidden = mutableMapOf<CardId, String>()

    /** What the engine waits for now: a player's decision, or another player's view ([Decision.OtherView]). */
    val pending: Pending? get() = working.pending

    /** The [changes] (by index) that are a player's own priority pass: the history shows them; other priority changes
     * (a pass the host made, priority given) are folded into the visible event of their batch (I-D22). */
    val ownPasses: Set<Int> get() = passes
    private val passes = mutableSetOf<Int>()

    /**
     * Performs [input] and answers the host's decisions that follow; returns what the engine then waits for.
     * [automatic]: the host makes the input for a player, as a priority pass when `Engine.shouldPassFor` says so
     * (I-D3, I-D18); the history then folds it into its batch's visible event (I-D22).
     */
    fun perform(input: Input, automatic: Boolean = false): Pending? {
        run(input, automatic)
        while (true) {
            val decision = working.pending?.decision ?: return null
            val answer = when (decision) {
                is Decision.Random -> random(decision)
                is Decision.HostFill -> Answer.Filled(decision.objects.map(::filled))
                is Decision.OtherView -> {
                    val filled = decision.objects.map(::filled)
                    // D31: the identities alone carry on while that player has nothing to decide there; random results
                    // there are the host's too
                    when (val next = Engine.otherViewDecision(working, filled)) {
                        null -> Answer.OtherView(filled, emptyList())
                        is Decision.Random -> Answer.OtherView(filled, listOf(random(next)))
                        else -> return working.pending
                    }
                }
                // I-D33: a decision a player answered early is answered now, when the engine takes the early answer
                else -> PreAnswers.answer(state.board, working, ids, decision) ?: return working.pending
            }
            run(Input.Answer(decision.id, answer))
        }
    }

    /** A change the host makes itself before the input (priority settled while ATW's steps change by hand,
     * [HostPriority]); it goes out with the call's batch. */
    internal fun host(change: Change) {
        working.apply(change)
        changes += change
    }

    /** The identities of [decision]'s objects, for the player who decides in their own view (D31). */
    fun otherView(decision: Decision.OtherView): List<FilledObject> = decision.objects.map(::filled)

    private fun run(input: Input, automatic: Boolean = true) {
        val outcome = Engine.perform(working, input)
        if (input is Input.PassPriority && !automatic)
            outcome.changes.indexOfFirst { it is Change.Priority }.takeIf { it >= 0 }?.let { passes += changes.size + it }
        outcome.changes.forEach { working.apply(it); changes += it }
    }

    /** The identity of [id] from ATW's card data. */
    private fun filled(id: ObjectId): FilledObject {
        val card = (id as? ObjectId.Card)?.card ?: error("Not a card: $id")
        val atw = hidden[card] ?: ids.atw(card)
        val played = state.board.cards.singleOrNull { it.id == atw } ?: error("No card $atw to fill $id")
        // I-D21: a card the device couldn't read plays with its printed values and manual abilities
        val definition = CardLedger.of(played.ledger).definition ?: definitions[played.definition]
            ?: PrintedCards.definition(state, played) ?: throw Unsupported(listOf("A card has no card data"))
        return FilledObject(id, definition)
    }

    private fun random(decision: Decision.Random): Answer = when (val what = decision.what) {
        RandomKind.CoinFlip -> Answer.CoinFlip(RandomV1.sample(2, RandomV1.Words(seeds())::next) == 0)
        is RandomKind.Die -> RandomV1.Words(seeds()).let { words -> Answer.DieRolls(List(what.count) { RandomV1.sample(what.sides, words::next) + 1 }) }
        is RandomKind.Objects -> objects(decision.player, what)
    }

    /** Random objects: a shuffle ([shuffled]) in the order ATW's `ShuffleLibrary` gives; else a random pick. */
    private fun objects(player: PlayerId, what: RandomKind.Objects): Answer.RandomObjects {
        val seed = seeds()
        val atw = what.among.map { (it as? ObjectId.Card)?.let { o -> hidden[o.card] ?: ids.atw(o.card) } ?: error("Not a card: $it") }
        val owner = shuffled(what)
        val order = if (owner == null) RandomV1.shuffle(atw, seed).take(what.count) else {
            // ATW shuffles the whole library with the seed; cards the shuffle leaves out (CR 701.24b) go back on top
            val library = working.player(owner).library.map { (it as ObjectId.Card).card.let { c -> hidden[c] ?: ids.atw(c) } }
            RandomV1.shuffle(library, seed).filter { it in atw }
        }
        val byAtw = atw.zip(what.among).toMap()
        val chosen = if (!what.newIds) order.map { byAtw.getValue(it) } else order.mapIndexed { i, real ->
            // D60: new ids for cards whose order the viewer doesn't know; this host knows which card each one is
            CardId(CommandIds.uuidV5(HIDDEN_NAMESPACE, "$seed/$i")).also { hidden[it] = real }.let { ObjectId.Card(it, 0) }
        }
        if (owner != null) shuffles += ShuffleAnswer(owner, seed, order)
        return Answer.RandomObjects(chosen)
    }

    /** The player whose library [what] shuffles: all of it (but cards the shuffle leaves out) in a random order. */
    private fun shuffled(what: RandomKind.Objects): PlayerId? {
        if (what.count != what.among.size || what.among.isEmpty()) return null
        return working.players.firstOrNull { p -> what.among.all { it in p.library } }?.id
    }

    private companion object {
        /** A fixed namespace for the card ids of a viewer's shuffle. */
        const val HIDDEN_NAMESPACE = "8a3d6f0e-5b1c-4e7a-9d2f-6c4b8e1a7f30"
    }
}

/** A shuffle the host answered: [player]'s library in [order] (ATW card ids, top first) from [seed], as ATW's
 * `ShuffleLibrary` with that seed orders it. */
internal data class ShuffleAnswer(val player: PlayerId, val seed: String, val order: List<String>)
