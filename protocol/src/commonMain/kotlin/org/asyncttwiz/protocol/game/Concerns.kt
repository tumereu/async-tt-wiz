package org.asyncttwiz.protocol.game

import arcanum.engine.change.Change
import arcanum.engine.state.CardId
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef

/** A part of a game's state an event can write or read, for the undo rule ([UndoRule]). */
sealed interface StateKey {
    /** Whether [card]'s object exists, and in which zone: zone changes, tokens made and gone, cards filled in. */
    data class Presence(val card: CardId) : StateKey
    /** The rest of [card]'s object: status, counters, damage, face, attachment, memory, … */
    data class Object(val card: CardId) : StateKey
    /** A player's engine state: life, counters, mana pool, zones' order, settings kept by the engine. */
    data class Player(val player: PlayerId) : StateKey
    /** Everything else the engine keeps: turn, priority, the Stack, combat, effects, the log, the open decision. */
    data object Game : StateKey
    /** One part of the table state ([TableState]). */
    data class Table(val part: String) : StateKey
}

/**
 * What an event concerns (owner's undo rule): what it [writes], what it [reads], and whether it read the whole engine
 * state ([readsEngine]: everything [arcanum.engine.Engine.perform] did, since its result may depend on any part of it).
 */
data class Concerns(val writes: Set<StateKey>, val reads: Set<StateKey>, val readsEngine: Boolean) {
    /** Whether an event concerning [later] after this one stops this one from being undone. */
    fun blockedBy(later: Concerns): Boolean =
        writes.any { it in later.writes || it in later.reads || (later.readsEngine && it !is StateKey.Table) }

    companion object {
        fun of(event: GameEvent): Concerns {
            val writes = mutableSetOf<StateKey>()
            val reads = mutableSetOf<StateKey>()
            var readsEngine = false
            event.engine?.steps?.forEach { step ->
                if (step.input != null) readsEngine = true
                step.changes.forEach { writes(it, writes) }
            }
            event.table.forEach { table(it, writes, reads) }
            return Concerns(writes, reads, readsEngine)
        }

        private fun table(change: TableChange, writes: MutableSet<StateKey>, reads: MutableSet<StateKey>) {
            fun obj(id: ObjectId) { if (id is ObjectId.Card) reads += StateKey.Presence(id.card) }
            when (change) {
                is TableChange.Placed -> { writes += StateKey.Table("placement:${change.obj}"); obj(change.obj) }
                is TableChange.LineMoved -> writes += StateKey.Table("line")
                is TableChange.EarlyAnswered -> writes += StateKey.Table("early:${change.key}")
                is TableChange.AttackDraft -> {
                    writes += StateKey.Table("attack-draft")
                    (change.before.orEmpty() + change.after.orEmpty()).forEach { obj(it.attacker); (it.target as? TargetRef.Object)?.let { t -> obj(t.id) } }
                }
                is TableChange.BlockDraft -> {
                    writes += StateKey.Table("block-draft")
                    (change.before.orEmpty() + change.after.orEmpty()).forEach { obj(it.blocker); obj(it.attacker) }
                }
                is TableChange.Suggestion -> writes += StateKey.Table("suggestion")
                // Confirming confirms the result the engine reports
                is TableChange.ResultConfirmed -> { writes += StateKey.Table("result-confirmed"); reads += StateKey.Game }
            }
        }

        /** What [change] writes: the objects and players it names, and the game for anything else it does. */
        private fun writes(change: Change, into: MutableSet<StateKey>) {
            fun state(id: ObjectId) { into += if (id is ObjectId.Card) StateKey.Object(id.card) else StateKey.Game }
            fun presence(id: ObjectId) { if (id is ObjectId.Card) { into += StateKey.Presence(id.card); into += StateKey.Object(id.card) } else into += StateKey.Game }
            fun target(ref: TargetRef?) { when (ref) { is TargetRef.Object -> state(ref.id); is TargetRef.Player -> into += StateKey.Player(ref.id); null -> {} } }
            // Logged changes add to the log, and zone lists live in players or the game
            into += StateKey.Game
            when (change) {
                is Change.ZoneChange -> change.moves.forEach { presence(it.before.id); presence(it.after.id); into += StateKey.Player(it.before.owner) }
                is Change.Created -> change.objects.forEach { presence(it.obj.id); into += StateKey.Player(it.obj.owner) }
                is Change.Removed -> change.objects.forEach { presence(it.obj.id); into += StateKey.Player(it.obj.owner) }
                is Change.Filled -> presence(change.before.id)
                is Change.Forgotten -> presence(change.before.id)
                is Change.Shuffle -> { into += StateKey.Player(change.player); (change.before + change.after).forEach { presence(it.id) } }
                is Change.Reorder -> { change.owner?.let { into += StateKey.Player(it) }; (change.before + change.after).forEach { state(it) } }
                is Change.Status -> state(change.id)
                is Change.Face -> state(change.id)
                is Change.Counters -> target(change.holder)
                is Change.DamageMarked -> state(change.id)
                is Change.Attached -> { state(change.id); target(change.before); target(change.after) }
                is Change.Restamped -> state(change.id)
                is Change.Memory -> state(change.id)
                is Change.Facts -> state(change.id)
                is Change.ControlStart -> state(change.id)
                is Change.ObjectUpdated -> state(change.before.id)
                is Change.Life -> into += StateKey.Player(change.player)
                is Change.ManaPool -> into += StateKey.Player(change.player)
                is Change.Ledger -> change.player?.let { into += StateKey.Player(it) }
                is Change.Lost -> into += StateKey.Player(change.player)
                is Change.SkipWhenNoChoice -> into += StateKey.Player(change.player)
                is Change.LibraryPlaces -> into += StateKey.Player(change.player)
                is Change.PlayerDesignation -> into += StateKey.Player(change.player)
                is Change.Reverted -> writes(change.change, into)
                else -> {}
            }
        }
    }
}

/**
 * The owner's undo rule for events (I-D54 §2): an event can be undone when no later event changed or relied on what it
 * changed, so every later event keeps the same values before and after. Information players saw never matters.
 * Errs on the side of refusing (a refused undo can be done by hand; a wrong one can't): an engine input read the whole
 * engine state, so an earlier event that changed anything in the engine is undone only past table-only events that
 * don't touch what it changed.
 */
object UndoRule {
    /** The first of [later] (the events after [event] on its line, in order) that stops [event] from being undone, or
     * null when it can be undone. */
    fun blocking(event: GameEvent, later: List<GameEvent>): GameEvent? {
        val concerns = Concerns.of(event)
        return later.firstOrNull { concerns.blockedBy(Concerns.of(it)) }
    }

    /**
     * Undoes [event] on [model], whose last applied events are [event] and then [later]: takes [later] and [event] back
     * and applies [later] again, returning the model without [event]. Refused ([IllegalStateException], the model
     * unchanged) when [blocking] names an event.
     */
    fun undo(model: GameModel, event: GameEvent, later: List<GameEvent>) {
        blocking(event, later)?.let { error("Event ${event.id} can't be undone: ${it.id} concerns what it changed") }
        for (e in later.asReversed()) model.revert(e)
        model.revert(event)
        for (e in later) model.apply(e)
    }
}
