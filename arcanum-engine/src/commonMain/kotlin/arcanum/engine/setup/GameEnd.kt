package arcanum.engine.setup

import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Outcome
import arcanum.engine.Perform
import arcanum.engine.Progress
import arcanum.engine.casting.Casting
import arcanum.engine.casting.Snapshot
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.change.GameEvent
import arcanum.engine.change.Meaning
import arcanum.engine.change.Recorder
import arcanum.engine.decision.Continuation
import arcanum.engine.input.Input
import arcanum.engine.state.CombatState
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.PriorityState
import arcanum.engine.turn.Priority
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How a game ended (CR 104.1). */
@Serializable
sealed interface GameResult {
    /** [winner] is the only player left (CR 104.2a). */
    @Serializable @SerialName("won") data class Won(val winner: PlayerId) : GameResult

    /** Every remaining player lost at once (CR 104.4a). */
    @Serializable @SerialName("draw") data object Draw : GameResult
}

/**
 * How the game ended, or null while it goes on: a game of two or more players ends when one player is left, who wins
 * (CR 104.2a), or when the last players lose at once, a draw (CR 104.4a). Worked out from who has lost, so undoing a
 * loss undoes the end. Once it is set, [arcanum.engine.Engine.options] are empty and [arcanum.engine.Engine.perform]
 * accepts nothing.
 */
val GameState.result: GameResult?
    get() {
        if (players.size < 2) return null
        val left = players.filterNot { it.lost }
        return when (left.size) {
            0 -> GameResult.Draw
            1 -> GameResult.Won(left.single().id)
            else -> null
        }
    }

/**
 * The end of a game (CR 104) and players leaving it (CR 800.4): conceding, a game that ended, and a player who lost in
 * a game that goes on.
 */
internal object GameEnd {
    fun over(s: GameState): Boolean = s.result != null

    /** The game ended (CR 104.1): nobody has priority any more; an open decision is dropped. */
    fun close(recorder: Recorder) {
        val s = recorder.state
        if (s.pending != null) recorder.record(Changes.pending(s, null))
        if (s.priority.holder != null || s.priority.passes != 0) recorder.record(Changes.priority(s, PriorityState(null), emptyList()))
    }

    /**
     * [Input.Concede] (CR 104.3a): the player leaves the game at once and loses, at any time, also while a decision is
     * open. Their own open cast or activation is cancelled first (CR 601.2); a decision of the game's setup is asked
     * again without them; any other open decision stays open. If the game goes on, the player leaves it (CR 800.4a) and
     * the game continues: state-based actions, triggers and priority as after any action (CR 117.5).
     */
    fun concede(state: GameState, input: Input.Concede): Outcome {
        val recorder = Recorder(state.deepCopy())
        val s = recorder.state
        val player = input.player
        require(!s.player(player).lost) { "$player has left the game already" }
        val open = s.pending
        var keep = open
        if (open != null) {
            recorder.record(Changes.pending(s, null))
            val c = open.continuation
            if (c is Continuation.Announcing && c.player == player) {
                Casting.cancel(recorder, c, player)
                keep = null
            }
        }
        recorder.record(Change.Happened(GameEvent.Conceded(player)))
        recorder.record(Change.Lost(player, false, true))
        if (over(s)) {
            close(recorder)
            return Outcome(recorder.changes.toList(), null)
        }
        leave(recorder, player)
        val setup = keep?.continuation as? Continuation.Setup
        val progress: Progress = when {
            setup != null -> Setup.run(recorder, setup.stage, setup.options, emptyList())
            keep != null && stillOpen(s, keep.continuation) -> {
                recorder.record(Changes.pending(s, keep))
                return Outcome(recorder.changes.toList(), keep)
            }
            else -> Progress.Done
        }
        val final = if (progress is Progress.Done) Perform.afterAction(recorder) else progress
        if (over(s)) {
            close(recorder)
            return Outcome(recorder.changes.toList(), null)
        }
        val waiting = (final as? Progress.Waiting)?.pending
        if (waiting != null) recorder.record(Changes.pending(s, waiting))
        return Outcome(recorder.changes.toList(), waiting)
    }

    /** An open flow can go on unless the Stack item it resolves has left the game. */
    private fun stillOpen(s: GameState, c: Continuation): Boolean =
        c !is Continuation.Resolving || c.item in s.stackItems || (c.left != null && !s.player(c.left.controller).lost)

    /** Players who lost in a game that goes on leave it (CR 800.4a): checked with the state-based actions. */
    fun leaveLost(recorder: Recorder) {
        val s = recorder.state
        if (over(s)) return
        s.players.filter { it.lost && inGame(s, it.id) }.forEach { leave(recorder, it.id) }
    }

    private fun inGame(s: GameState, p: PlayerId): Boolean =
        s.objects.values.any { (it.owner == p && it.zone != Zone.Outside) || it.controller == p } ||
            s.stackItems.values.any { it.controller == p } || s.triggered.any { it.controller == p } ||
            s.effects.values.any { it.controller == p && it.mechanics.any { m -> m is Mechanic.ControlledBy } } ||
            s.delayedTriggers.values.any { it.controller == p }

    /**
     * [player] leaves the game (CR 800.4a): effects giving them control end; every object they own leaves the game
     * (to outside the game; their tokens then cease to exist, CR 704.5d); their abilities and copies on the Stack cease to
     * exist; what they still control is exiled. Their waiting and delayed triggers are dropped (CR 800.4d). Priority they
     * held passes to the next player (CR 800.4a); passes made so far keep counting, theirs aside. An active player's turn
     * goes on without them (CR 800.4j).
     */
    fun leave(recorder: Recorder, player: PlayerId) {
        val s = recorder.state
        s.effects.values.filter { it.controller == player && it.mechanics.any { m -> m is Mechanic.ControlledBy } }
            .forEach { recorder.record(Change.EffectRemoved(it)) }
        s.delayedTriggers.values.filter { it.controller == player }
            .forEach { recorder.record(Change.DelayedTriggerRemoved(it, listOf(Meaning.EffectEnded))) }
        // Their abilities and copies of spells on the Stack cease to exist
        s.stack.filter { it is ObjectId.StackItem && s.stackItems[it]?.controller == player }.reversed().forEach { id ->
            recorder.record(Change.StackItemRemoved(s.stackItems.getValue(id), s.stack.indexOf(id)))
        }
        // CR 708.9: their face-down permanents and spells are revealed to all players (in a viewer's state, those the
        // viewer doesn't know stay unknown: the game can't wait for the host here; they leave the game at once)
        val faceDown = s.objects.values.filter { it.owner == player && it.faceDown && (it.zone == Zone.Battlefield || it.zone == Zone.Stack) }
            .map { it.id }
        if (faceDown.isNotEmpty()) recorder.record(Change.Happened(GameEvent.Revealed(player, faceDown)))
        // Every object they own leaves the game
        val owned = s.objects.values.filter { it.owner == player && it.zone != Zone.Outside }.map { it.id }
        removeSpells(recorder, owned)
        if (owned.isNotEmpty()) recorder.record(Changes.move(s, owned, Zone.Outside))
        // What they still control is exiled
        val snap = Snapshot(s)
        val controlled = s.battlefield.filter { snap.board.controller(it) == player } +
            s.stack.filter { it is ObjectId.Card && s.stackItems[it]?.controller == player }
        removeSpells(recorder, controlled)
        if (controlled.isNotEmpty()) recorder.record(Changes.move(s, controlled, Zone.Exile))
        // CR 800.4d: their triggered abilities aren't put on the Stack
        val triggers = s.triggered.filter { it.controller != player }
        if (triggers.size != s.triggered.size) recorder.record(Change.TriggersWaiting(s.triggered.toList(), triggers))
        // Combat goes on without what left
        s.combat?.let { c ->
            val after = c.copy(
                attackers = c.attackers.filter { s.obj(it.id) != null },
                blockers = c.blockers.filter { s.obj(it.id) != null }.map { b -> b.copy(attackers = b.attackers.filter { s.obj(it) != null }) },
                firstStrikers = c.firstStrikers?.filter { s.obj(it) != null },
            )
            if (after != c) recorder.record(Change.Combat(c, after))
        }
        // CR 800.4j: an active player who left declares no attackers
        if (s.turn.active == player && s.turn.step == TurnStep.DeclareAttackers && s.combat == null) {
            recorder.record(Change.Combat(null, CombatState(), listOf(Meaning.AttackersDeclared)))
        }
        val p = s.priority
        val holder = p.holder ?: return
        val after = if (holder == player) {
            // The players who passed before them still passed in succession, short of everyone left
            PriorityState(Priority.nextPlayer(s, player), p.passes.coerceAtMost(s.playersFrom(holder).size - 1))
        } else {
            p.copy(passes = p.passes - if (player in passedBefore(s, holder, p.passes, player)) 1 else 0)
        }
        if (after != p) recorder.record(Changes.priority(s, after, if (after.holder != holder) listOf(Meaning.PriorityGranted) else emptyList()))
    }

    /** The [passes] players who passed in succession before [holder], counted among the players still in the game and
     * [leaving] (CR 117.3d). */
    private fun passedBefore(s: GameState, holder: PlayerId, passes: Int, leaving: PlayerId): List<PlayerId> {
        val ids = s.players.filter { !it.lost || it.id == leaving }.map { it.id }
        val at = ids.indexOf(holder)
        if (at < 0 || ids.isEmpty()) return emptyList()
        return (1..passes.coerceAtMost(ids.size - 1)).map { ids[((at - it) % ids.size + ids.size) % ids.size] }
    }

    /** Spells among [ids] stop being on the Stack as their cards leave it. */
    private fun removeSpells(recorder: Recorder, ids: List<ObjectId>) {
        ids.filter { recorder.state.obj(it)?.zone == Zone.Stack }.forEach { id ->
            recorder.state.stackItems[id]?.let { recorder.record(Change.StackItemRemoved(it, null)) }
        }
    }
}
