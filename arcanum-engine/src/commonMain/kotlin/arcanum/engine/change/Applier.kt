package arcanum.engine.change

import arcanum.engine.state.CounterEntry
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.LoggedChange
import arcanum.engine.state.ObjectId
import arcanum.engine.state.StatusFlag
import arcanum.engine.state.TargetRef

/**
 * Applies and reverts [Change]s on a [GameState] (D56, D59). Every change checks that the state holds its
 * before-values, so a change made for another state fails loudly instead of corrupting this one.
 */
internal object Applier {
    fun apply(state: GameState, change: Change) {
        state.viewCache?.changed(change)
        when (change) {
            is Change.Reverted -> revert(state, change.change)
            else -> {
                applyState(state, change)
                if (change.isEvent) state.log.add(LoggedChange(state.turn.number, change))
            }
        }
    }

    fun revert(state: GameState, change: Change) {
        state.viewCache?.changed(change)
        when (change) {
            is Change.Reverted -> apply(state, change.change)
            else -> {
                // Changes are taken back last first, so an event's entry is the log's last. Once the host dropped its
                // turn's entries (GameState.forgetOlderTurns), every later entry has been taken back before it and the
                // log is empty: there is nothing to take off, and the change still reverts.
                if (change.isEvent && state.log.isNotEmpty()) {
                    val last = state.log.last()
                    check(last.change == change) { "Reverting a change that isn't the last logged event: $change" }
                    state.log.removeAt(state.log.lastIndex)
                }
                applyState(state, change.inverse())
            }
        }
    }

    private fun applyState(state: GameState, change: Change) {
        when (change) {
            is Change.ZoneChange -> change.moves.forEach { move ->
                // CR 730.3: a merged permanent's components arrive as their own objects; taken back, they leave again first
                val splits = move.before.merged
                if (!splits) move.parts.reversed().forEach { remove(state, it.obj, it.index) }
                remove(state, move.before, move.fromIndex)
                insert(state, move.after, move.toIndex)
                if (splits) move.parts.forEach { insert(state, it.obj, it.index) }
            }
            is Change.Created -> change.objects.forEach { insert(state, it.obj, it.index) }
            is Change.Removed -> change.objects.forEach { remove(state, it.obj, it.index) }
            is Change.Status -> updateObject(state, change.id) { o ->
                check(o.has(change.flag) == change.before) { "Status ${change.flag} of ${change.id} isn't ${change.before}" }
                o.with(change.flag, change.after)
            }
            is Change.Face -> updateObject(state, change.id) { o ->
                check(o.face == change.before) { "Face of ${change.id} isn't ${change.before}" }
                o.copy(face = change.after)
            }
            is Change.Counters -> {
                change.after?.let { stamp(state, it.timestamp) }
                when (val holder = change.holder) {
                    is TargetRef.Object -> updateObject(state, holder.id) { o ->
                        check(o.counters.entry(change.kind) == change.before) { "Counters ${change.kind} of ${holder.id} aren't ${change.before}" }
                        o.copy(counters = o.counters.replaced(change.kind, change.after))
                    }
                    is TargetRef.Player -> state.updatePlayer(holder.id) { p ->
                        check(p.counters.entry(change.kind) == change.before) { "Counters ${change.kind} of ${holder.id} aren't ${change.before}" }
                        p.copy(counters = p.counters.replaced(change.kind, change.after))
                    }
                }
            }
            is Change.Damage, is Change.Happened -> Unit
            is Change.DamageMarked -> updateObject(state, change.id) { o ->
                check(o.damage == change.before && o.deathtouchDamage == change.deathtouchBefore) { "Damage on ${change.id} isn't ${change.before}" }
                o.copy(damage = change.after, deathtouchDamage = change.deathtouchAfter)
            }
            is Change.Life -> state.updatePlayer(change.player) { p ->
                check(p.life == change.before) { "Life of ${change.player} isn't ${change.before}" }
                p.copy(life = change.after)
            }
            is Change.ManaPool -> state.updatePlayer(change.player) { p ->
                check(p.manaPool == change.before) { "Mana pool of ${change.player} isn't ${change.before}" }
                p.copy(manaPool = change.after)
            }
            is Change.StackItemAdded -> {
                check(change.item.id !in state.stackItems) { "Stack item ${change.item.id} exists already" }
                state.stackItems[change.item.id] = change.item
                change.index?.let { state.stack.add(it, change.item.id) }
                stamp(state, change.item.timestamp)
            }
            is Change.StackItemRemoved -> {
                check(state.stackItems[change.item.id] == change.item) { "Stack item ${change.item.id} isn't as expected" }
                state.stackItems.remove(change.item.id)
                change.index?.let {
                    check(state.stack.getOrNull(it) == change.item.id) { "Stack position $it isn't ${change.item.id}" }
                    state.stack.removeAt(it)
                }
            }
            is Change.StackItemUpdated -> {
                check(state.stackItems[change.before.id] == change.before) { "Stack item ${change.before.id} isn't as expected" }
                state.stackItems.remove(change.before.id)
                state.stackItems[change.after.id] = change.after
            }
            is Change.EffectAdded -> {
                check(change.effect.id !in state.effects) { "Effect ${change.effect.id} exists already" }
                state.effects[change.effect.id] = change.effect
                stamp(state, change.effect.timestamp)
            }
            is Change.EffectRemoved -> {
                check(state.effects[change.effect.id] == change.effect) { "Effect ${change.effect.id} isn't as expected" }
                state.effects.remove(change.effect.id)
            }
            is Change.EffectUpdated -> {
                check(state.effects[change.before.id] == change.before) { "Effect ${change.before.id} isn't as expected" }
                state.effects.remove(change.before.id)
                state.effects[change.after.id] = change.after
            }
            is Change.DelayedTriggerAdded -> {
                check(change.trigger.id !in state.delayedTriggers) { "Delayed trigger ${change.trigger.id} exists already" }
                state.delayedTriggers[change.trigger.id] = change.trigger
                stamp(state, change.trigger.timestamp)
            }
            is Change.DelayedTriggerRemoved -> {
                check(state.delayedTriggers[change.trigger.id] == change.trigger) { "Delayed trigger ${change.trigger.id} isn't as expected" }
                state.delayedTriggers.remove(change.trigger.id)
            }
            is Change.Attached -> updateObject(state, change.id) { o ->
                check(o.attachedTo == change.before && o.timestamp == change.timestampBefore) { "${change.id} isn't attached to ${change.before}" }
                stamp(state, change.timestampAfter)
                o.copy(attachedTo = change.after, timestamp = change.timestampAfter)
            }
            is Change.Restamped -> updateObject(state, change.id) { o ->
                check(o.timestamp == change.before) { "Timestamp of ${change.id} isn't ${change.before}" }
                stamp(state, change.after)
                o.copy(timestamp = change.after)
            }
            is Change.Memory -> updateObject(state, change.id) { o ->
                check(o.memory[change.key] == change.before) { "Memory ${change.key} of ${change.id} isn't ${change.before}" }
                val values = o.memory.values - change.key
                o.copy(memory = arcanum.engine.state.Bindings(if (change.after == null) values else values + (change.key to change.after)))
            }
            is Change.Facts -> updateObject(state, change.id) { o ->
                check(o.facts == change.before) { "Facts of ${change.id} aren't as expected" }
                o.copy(facts = change.after)
            }
            is Change.ControlStart -> updateObject(state, change.id) { o ->
                check(o.controlSince == change.before) { "Control start of ${change.id} isn't ${change.before}" }
                o.copy(controlSince = change.after)
            }
            is Change.ObjectUpdated -> {
                check(change.before.id == change.after.id && change.before.zone == change.after.zone) { "An update can't move an object" }
                updateObject(state, change.before.id) { o ->
                    check(o == change.before) { "${change.before.id} isn't as expected" }
                    change.after
                }
            }
            is Change.Step -> {
                check(state.turn == change.before) { "Turn isn't ${change.before}" }
                state.turn = change.after
            }
            is Change.Priority -> {
                check(state.priority == change.before) { "Priority isn't ${change.before}" }
                state.priority = change.after
            }
            is Change.Combat -> {
                check(state.combat == change.before) { "Combat isn't ${change.before}" }
                state.combat = change.after
            }
            is Change.Ledger -> if (change.player == null) {
                check((state.ledger[change.key] ?: 0) == change.before) { "Ledger ${change.key} isn't ${change.before}" }
                if (change.after == 0) state.ledger.remove(change.key) else state.ledger[change.key] = change.after
            } else state.updatePlayer(change.player) { p ->
                check((p.ledger[change.key] ?: 0) == change.before) { "Ledger ${change.key} of ${change.player} isn't ${change.before}" }
                p.copy(ledger = if (change.after == 0) p.ledger - change.key else p.ledger + (change.key to change.after))
            }
            is Change.Lost -> state.updatePlayer(change.player) { p ->
                check(p.lost == change.before) { "Lost of ${change.player} isn't ${change.before}" }
                p.copy(lost = change.after)
            }
            is Change.SkipWhenNoChoice -> state.updatePlayer(change.player) { p ->
                check(p.skipWhenNoChoice == change.before) { "Skip when no choice of ${change.player} isn't ${change.before}" }
                p.copy(skipWhenNoChoice = change.after)
            }
            is Change.PlayerDesignation -> state.updatePlayer(change.player) { p ->
                check((change.designation in p.designations) == change.before) { "${change.designation} of ${change.player} isn't ${change.before}" }
                p.copy(designations = (if (change.after) p.designations + change.designation else p.designations - change.designation)
                    .sortedBy { it.toString() })
            }
            is Change.Shuffle -> {
                val library = state.player(change.player).library
                check(library == change.before.map { it.id }) { "Library of ${change.player} isn't as expected" }
                change.before.forEach { check(state.objects.remove(it.id) == it) { "${it.id} isn't as expected" } }
                change.after.forEach { state.objects[it.id] = it }
                state.setZone(arcanum.api.Zone.Library, change.player, change.after.map { it.id })
            }
            is Change.Reorder -> {
                check(state.zone(change.zone, change.owner) == change.before) { "${change.zone} isn't in the expected order" }
                state.setZone(change.zone, change.owner, change.after)
            }
            is Change.Forgotten -> applyState(state, Change.Filled(change.before, change.after))
            is Change.Filled -> {
                check(change.before.id == change.after.id) { "Filling can't change an id" }
                updateObject(state, change.before.id) { o ->
                    check(o == change.before) { "${change.before.id} isn't as expected" }
                    change.after
                }
            }
            is Change.TriggersWaiting -> {
                check(state.triggered == change.before) { "Waiting triggers aren't as expected" }
                state.triggered = change.after.toMutableList()
            }
            is Change.PendingSet -> {
                check(state.pending == change.before) { "Pending isn't as expected" }
                state.pending = change.after
            }
            is Change.Reverted -> error("Reverted is handled by apply and revert")
        }
    }

    private fun stamp(state: GameState, timestamp: Long) {
        if (timestamp > state.timestampCounter) state.timestampCounter = timestamp
    }

    private fun insert(state: GameState, obj: GameObject, index: Int) {
        check(obj.id !in state.objects) { "Object ${obj.id} exists already" }
        // One entry per counter kind (CR 122.1): counters of a kind add up
        check(obj.counters.distinctBy { it.kind }.size == obj.counters.size) { "Object ${obj.id} has two entries of one counter kind: ${obj.counters}" }
        val zone = state.zone(obj.zone, obj.owner)
        check(index in 0..zone.size) { "Position $index is outside ${obj.zone} (size ${zone.size})" }
        state.objects[obj.id] = obj
        state.setZone(obj.zone, obj.owner, zone.toMutableList().apply { add(index, obj.id) })
        stamp(state, obj.timestamp)
    }

    private fun remove(state: GameState, obj: GameObject, index: Int) {
        check(state.objects[obj.id] == obj) { "Object ${obj.id} isn't as expected:\n  ${state.objects[obj.id]}\n  $obj" }
        val zone = state.zone(obj.zone, obj.owner)
        check(zone.getOrNull(index) == obj.id) { "Position $index of ${obj.zone} isn't ${obj.id}" }
        state.objects.remove(obj.id)
        state.setZone(obj.zone, obj.owner, zone.toMutableList().apply { removeAt(index) })
    }

    private fun updateObject(state: GameState, id: ObjectId, change: (GameObject) -> GameObject) {
        val o = state.objects[id] ?: error("No object $id")
        state.objects[id] = change(o)
    }

    private fun GameObject.has(flag: StatusFlag): Boolean = when (flag) {
        StatusFlag.Tapped -> tapped
        StatusFlag.FaceDown -> faceDown
        StatusFlag.PhasedOut -> phasedOut
        is StatusFlag.Marked -> flag.mark in marks
    }

    private fun GameObject.with(flag: StatusFlag, on: Boolean): GameObject = when (flag) {
        StatusFlag.Tapped -> copy(tapped = on)
        StatusFlag.FaceDown -> copy(faceDown = on)
        StatusFlag.PhasedOut -> copy(phasedOut = on)
        is StatusFlag.Marked -> copy(marks = (if (on) marks + flag.mark else marks - flag.mark).sortedBy { it.toString() })
    }

    private fun List<CounterEntry>.entry(kind: arcanum.api.CounterKind) = firstOrNull { it.kind == kind }

    /** Sets or removes the entry of [kind]; entries stay in one canonical order (timestamp, then kind), so taking a
     * change back gives the same list. */
    private fun List<CounterEntry>.replaced(kind: arcanum.api.CounterKind, entry: CounterEntry?): List<CounterEntry> =
        (filter { it.kind != kind } + listOfNotNull(entry)).sortedWith(compareBy({ it.timestamp }, { it.kind.toString() }))
}
