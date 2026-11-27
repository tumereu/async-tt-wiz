package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.turn.LoopKey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The first turn during the other player's mulligans (HIS-115, HIS-116): the starting player (the assumer) keeps and
 * plays on an assumed keep of the other player's opening; when that player's real opening move at the base differs (a
 * mulligan, a different bottom choice, an action "any time you could mulligan", CR 103.5b), the assumer's line is made
 * again on top of it, input by input, and stands as far as it comes out the same as the assumer could tell
 * ([sameAs]); from the first event that doesn't, it is taken back as a branch. One rule for the server (the re-made
 * events it stores, [Acceptance]) and the devices (which follow the server's events, [GameSession.reapplied]).
 *
 * An input comes out the same when the engine accepts it on the new state, the position after it is equal as the assumer
 * could tell ([Host.loopKey] with them as the viewer: the other player's hidden cards don't count, nor do object ids and
 * timestamps) and it shows the assumer the same cards ([Host.revealed], by card). Random answers are worked out again
 * from each player's own stream ([LineRandom]), never copied. An input names the object or decision in the same place of
 * the new run ([LoopKey.place], [LoopKey.objectAt]). An event is made again whole or not at all.
 */
object Reapply {
    /**
     * What [run] made: [events] are the re-made events after the new base, oldest first (the assumed keep made again for the
     * other player's opening as it is now, then the old line's events that came out the same, each with
     * [GameEvent.carriedFrom]); [stoppedAt] the first old event that didn't come out the same (null: all did), [why] it
     * didn't; [record] says, event by event, what was made from what, for the logs.
     */
    class Result(val events: List<GameEvent>, val stoppedAt: GameEvent?, val why: String?, val record: List<String>)

    /** Whether [event], made on [before], is an assumed opening answer (an assumed keep or its stand-in bottom choice, turn 0)
     * about another player than its assumer: their real opening answer differing from it re-applies instead of branching. */
    fun opening(event: GameEvent, before: GameState): Boolean = before.turn.number == 0 && event.assumed?.assumedBy != null

    /**
     * The assumed keep (HIS-096): for the other player's open opening decision on [state], made by [assumer], the
     * assumed input: their Keep, or, where their last mulligan still owes cards to the bottom, the stand-in bottom choice
     * (the last cards of their hand in hand order), the Keep following it as its own event. Null where no such decision of
     * theirs is open (their opening is done, or it is a real decision of theirs, such as an opening-hand action).
     */
    fun assumedKeep(state: GameState, assumer: PlayerId): Input? {
        if (state.turn.number != 0) return null
        val pending = state.pending ?: return null
        val d = pending.decision
        if (d.player == assumer || pending.continuation !is Continuation.Setup) return null
        return when (d) {
            is Decision.ChooseObjects -> standIn(state, d)?.let { Input.Answer(d.id, it) }
            is Decision.ChooseOption -> Input.Answer(d.id, Answer.Option(KEEP)).takeIf { KEEP in d.options }
            else -> null
        }
    }

    /** The stand-in bottom choice for the other player's open bottom decision [d] on [state]: the last cards of their hand
     * in hand order, as many as it asks; null when [d] isn't such a choice. Never shown to the assumer (their hand). */
    fun standIn(state: GameState, d: Decision): Answer.Objects? {
        if (d !is Decision.ChooseObjects || d.min != d.max || state.turn.number != 0) return null
        val hand = state.player(d.player).hand
        if (d.options.toSet() != hand.toSet()) return null
        return Answer.Objects(hand.takeLast(d.min))
    }

    /**
     * Makes the assumer's [old] line again: [oldBase] is the state the old line was made on (the base's parent P, used
     * up), [model] the state at the new base [baseId] (the other player's real opening event made on P, used up: it ends
     * as the state after the last re-made event). The old line's leading assumed opening answers are made again for the
     * opening as it now is ([assumedKeep]), then each later event input by input while it comes out the same. [ids] names
     * the new events. [line]: the line the first event made starts (a rewind of the other player's to before the base,
     * [Rewind.reapply]: every rewind starts a new line, HIS-042); null for a re-apply at the other player's opening move,
     * which starts none; [reshuffle]: that line reshuffles the libraries (the game's "Reshuffle libraries on undo"). An
     * old event that started a line of its own starts it again as it did, with its reshuffle or without one (HIS-042).
     */
    fun run(oldBase: GameModel, model: GameModel, baseId: String, old: List<GameEvent>, assumer: PlayerId, ids: () -> String,
        line: Int? = null, reshuffle: Boolean = true): Result {
        var lineDue = line
        val record = mutableListOf<String>()
        val events = mutableListOf<GameEvent>()
        var parent = baseId
        val was = oldBase.engine
        var i = 0
        var keepMode: ResponseMode? = null
        val openingOld = mutableListOf<GameEvent>()
        while (i < old.size && opening(old[i], was)) {
            keepMode = old[i].mode
            old[i].engine?.changes.orEmpty().forEach(was::apply)
            record += "old assumed opening answer ${old[i].id.take(8)}: ${old[i].assumed?.input} (made again below)"
            openingOld += old[i]
            i++
        }
        // The assumed keep again, for the other player's opening as it is now (a mulligan asks again; a bottom owed first),
        // each marked as made from the old one it replaces (the keep from the old keep, a stand-in bottom from the old one)
        val inputs = mutableListOf<Input>()
        val probe = model.copy()
        while (inputs.size < 3) {
            val input = assumedKeep(probe.engine, assumer) ?: break
            inputs += input
            val b = EventBuilder(probe, "probe", parent, assumer.value)
            try { b.perform(input, assumedBy = assumer); foldAssumed(b, probe, assumer) } catch (_: RuntimeException) { break }
            b.build()
        }
        val replaced = openingOld.takeLast(inputs.size).let { List(inputs.size - it.size) { null } + it }
        for ((n, input) in inputs.withIndex()) {
            val builder = EventBuilder(model, ids(), parent, assumer.value)
            builder.mode = keepMode
            builder.carriedFrom = replaced[n]?.id
            try {
                lineDue?.let { builder.startLine(it, reshuffle) }
                builder.perform(input, assumedBy = assumer)
                foldAssumed(builder, model, assumer)
            } catch (e: RuntimeException) {
                builder.abandon()
                record += "the assumed keep wasn't made again: ${e.message}"
                return Result(events, old.getOrNull(i), "the assumed keep wasn't made again: ${e.message}", record)
            }
            val event = builder.build()
            lineDue?.let { record += "${event.id.take(8)} starts line $it (the rewind's)" }
            lineDue = null
            record += "assumed again ${event.id.take(8)}${event.carriedFrom?.let { " (for ${it.take(8)})" }.orEmpty()}: $input"
            events += event
            parent = event.id
        }
        var keyOld = Host.loopKey(was, assumer)
        var keyNew = Host.loopKey(model.engine, assumer)
        // The old line's events made again, with what each was made from
        val carried = mutableListOf<Pair<GameEvent, GameEvent>>()
        for (event in old.drop(i)) {
            val builder = EventBuilder(model, ids(), parent, event.author)
            builder.mode = event.mode
            builder.press = event.press
            builder.carriedFrom = event.id
            val why: String? = try {
                var failed: String? = null
                // After a rewind the first event made starts its new line; an old event's own line start can't follow it
                if (line != null && event.startsLine != null) failed = "it starts a line of its own, which the rewind's new line replaces"
                else lineDue?.let { builder.startLine(it, reshuffle) }
                // HIS-042: an old line start with no reshuffle step (the game's switch off for it, or nothing to reorder):
                // the same line again, unshuffled (with a reshuffle step, the step below starts it)
                val ownStart = event.startsLine
                val reshuffled = event.engine?.steps?.firstOrNull()?.let { it.input == null } == true
                if (failed == null && ownStart != null && !reshuffled) builder.startLine(ownStart, reshuffle = false)
                if (failed == null) for ((j, step) in event.engine?.steps.orEmpty().withIndex()) {
                    val input = step.input
                    if (input == null) {
                        // A line's start (the old event started one, after an undo of the assumer's): the same line's reshuffle
                        val line = event.startsLine
                        if (line == null || j != 0) { failed = "a host step that isn't a line's start"; break }
                        builder.startLine(line)
                        step.changes.forEach(was::apply)
                    } else {
                        val before = was.pending?.decision
                        val random = before is Decision.Random && input is Input.Answer && input.decision == before.id
                        val shownOld = Host.revealed(was, step.changes, assumer)
                        val now = model.engine.pending?.decision
                        val made = if (random) (now as? Decision.Random)?.let { Input.Answer(it.id, builder.randomAnswer(it)) }
                            else carry(input, keyOld, before?.id?.value, keyNew, now?.id?.value)
                        if (made == null) { failed = "$input names what has no place on the new state (or no random decision is open)"; break }
                        step.changes.forEach(was::apply)
                        val copy = model.engine.deepCopy()
                        val outcome = try { builder.perform(made, assumedBy = step.assumedBy) }
                            catch (e: IllegalArgumentException) { failed = "$made refused: ${e.message}"; break }
                            catch (e: IllegalStateException) { failed = "$made refused: ${e.message}"; break }
                        val shownNew = Host.revealed(copy, outcome.changes, assumer)
                        keyOld = Host.loopKey(was, assumer)
                        keyNew = Host.loopKey(model.engine, assumer)
                        if (keyOld != keyNew) { failed = "the position after $made differs as $assumer could tell"; break }
                        if (shownOld != shownNew) { failed = "$made shows other cards ($shownNew, before $shownOld)"; break }
                    }
                }
                if (failed == null) for (change in event.table) {
                    if (change is TableChange.LineMoved) continue
                    try { builder.table(change) } catch (e: IllegalStateException) { failed = "its table change doesn't fit: ${e.message}"; break }
                }
                failed
            } catch (e: RuntimeException) {
                "failed: ${e.message}"
            }
            if (why != null) {
                builder.abandon()
                record += "${event.id.take(8)} by ${event.author.take(8)} not the same: $why"
                // What doesn't come out the same is taken back from the assumer's own action that led to it (the cast whose
                // resolution, on the other player's assumed pass, shows other cards): as Undo counts actions (HIS-084)
                val from = carried.indexOfLast { (old, _) -> action(old, assumer) }.takeIf { it >= 0 && !action(event, assumer) }
                if (from != null) {
                    for ((_, made) in carried.subList(from, carried.size).asReversed()) {
                        model.revert(made)
                        events.removeAt(events.lastIndex)
                    }
                    val back = carried[from].first
                    record += "taken back from ${back.id.take(8)}, the action of ${assumer.value.take(8)} that led to it"
                    return Result(events, back, why, record)
                }
                return Result(events, event, why, record)
            }
            val made = builder.build()
            lineDue?.let { record += "${made.id.take(8)} starts line $it (the rewind's)" }
            lineDue = null
            record += "${event.id.take(8)} → ${made.id.take(8)}: ${made.engine?.steps.orEmpty().mapNotNull { it.input }.joinToString()}"
            events += made
            carried += event to made
            parent = made.id
        }
        return Result(events, null, null, record)
    }

    /** Whether [event] is one of [player]'s own actions (a real input of theirs, not a pass unless a press of the main
     * button, HIS-101; never an assumption): what a take-back goes back to (HIS-097). */
    private fun action(event: GameEvent, player: PlayerId): Boolean {
        if (event.author != player.value || event.assumed != null) return false
        val input = event.engine?.steps?.firstNotNullOfOrNull { it.input } ?: return event.table.any { it !is TableChange.LineMoved }
        return input !is Input.PassPriority || event.press != null
    }

    /** What follows an assumed input by itself, as the assumer's device folds it ([GameDevice]): random answers,
     * engine-automatic answers and public forced passes; never an input of the assumer's own. */
    private fun foldAssumed(builder: EventBuilder, model: GameModel, assumer: PlayerId) {
        repeat(MAX_FOLDED) {
            builder.answerRandoms()
            val s = model.engine
            if (Engine.result(s) != null) return
            val d = s.pending?.decision
            if (d != null) {
                builder.perform(WhoActs.automaticAnswer(model, d) ?: return)
                return@repeat
            }
            val holder = s.priority.holder ?: return
            if (Host.decider(s, holder) == assumer || !Stops.forcedPass(model, holder)) return
            builder.perform(Input.PassPriority(holder))
        }
    }

    /** [input], made where [keyOld] was worked out with [decisionOld] open, as the same input where [keyNew] was worked out
     * with [decisionNew] open: each object it names by the one in the same place, the open decision by the open decision;
     * null when something it names has no place there. */
    fun carry(input: Input, keyOld: LoopKey, decisionOld: String?, keyNew: LoopKey, decisionNew: String?): Input? {
        var missing = false
        fun walk(e: JsonElement): JsonElement = when (e) {
            is JsonObject -> JsonObject(e.mapValues { walk(it.value) })
            is JsonArray -> JsonArray(e.map(::walk))
            is JsonPrimitive -> when {
                !e.isString -> e
                decisionOld != null && e.content == decisionOld -> if (decisionNew == null) { missing = true; e } else JsonPrimitive(decisionNew)
                isObjectId(e.content) -> {
                    val id = EngineJson.json.decodeFromJsonElement(ObjectId.serializer(), e)
                    val there = keyOld.place(id)?.let(keyNew::objectAt)
                    if (there == null) { missing = true; e } else EngineJson.json.encodeToJsonElement(ObjectId.serializer(), there)
                }
                else -> e
            }
        }
        val json = EngineJson.json
        val carried = json.decodeFromJsonElement(Input.serializer(), walk(json.encodeToJsonElement(Input.serializer(), input)))
        return carried.takeIf { !missing }
    }

    private fun isObjectId(s: String) = (s.startsWith("c:") && s.lastIndexOf('#') > 2) || s.startsWith("s:")

    private const val KEEP = "Keep"
    private const val MAX_FOLDED = 64
}
