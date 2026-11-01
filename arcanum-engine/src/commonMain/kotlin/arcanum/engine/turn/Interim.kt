package arcanum.engine.turn

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.Zone
import arcanum.engine.Progress
import arcanum.engine.casting.Snapshot
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.change.Meaning
import arcanum.engine.change.Recorder
import arcanum.engine.state.StackItemKind

/**
 * Stand-ins used only while the resolution and state-based action parts of the engine throw "not implemented yet"
 * (they are built in parallel): a spell resolves by its card going where it goes (CR 608.3: a permanent spell enters
 * the battlefield, an instant or sorcery goes to the graveyard, CR 608.2n) without doing anything else; an ability just
 * leaves the Stack; the state-based actions are the basic ones about life, poison, drawing from an empty library,
 * toughness, damage and loyalty (CR 704.5a–c, f–i). Once the real parts exist these are never reached.
 */
internal object Interim {
    fun resolveTop(recorder: Recorder): Progress {
        val s = recorder.state
        val top = s.topOfStack() ?: return Progress.Done
        val item = s.stackItems[top]
        val o = s.obj(top)
        if (item != null) recorder.record(Change.StackItemRemoved(item, if (o == null) s.stack.indexOf(top) else null, listOf(Meaning.Resolved)))
        if (o != null && (item == null || item.kind == StackItemKind.Spell)) {
            val types = Snapshot(recorder.state).view[top]?.characteristics?.types.orEmpty()
            val permanent = types.any { it == CardType.Creature || it == CardType.Artifact || it == CardType.Enchantment ||
                it == CardType.Planeswalker || it == CardType.Battle || it == CardType.Land }
            val loyalty = Snapshot(recorder.state).view[top]?.characteristics?.loyalty
            recorder.record(Changes.move(recorder.state, listOf(top), if (permanent) Zone.Battlefield else Zone.Graveyard,
                controller = item?.controller, faceDown = o.faceDown,
                // CR 306.5b: a planeswalker enters with its printed loyalty
                counters = if (loyalty != null && loyalty > 0) listOf(CounterKind.Loyalty to loyalty) else emptyList()))
        }
        return Progress.Done
    }

    fun stateBased(recorder: Recorder): Progress {
        val s = recorder.state
        val snap = Snapshot(s)
        // CR 704.5a–c: life 0 or less, a draw from an empty library, ten poison counters
        s.players.filterNot { it.lost }.forEach { p ->
            val drewFromEmpty = (p.ledger[Steps.EMPTY_LIBRARY_DRAW] ?: 0) > 0
            if (drewFromEmpty) recorder.record(Change.Ledger(p.id, Steps.EMPTY_LIBRARY_DRAW, p.ledger[Steps.EMPTY_LIBRARY_DRAW]!!, 0))
            if (p.life <= 0 || drewFromEmpty || p.counters(CounterKind.Poison) >= 10) {
                recorder.record(Change.Lost(p.id, false, true, ))
            }
        }
        val dying = s.battlefield.filter { id ->
            val o = s.obj(id) ?: return@filter false
            val c = snap.view[id]?.characteristics ?: return@filter false
            val indestructible = snap.view[id]?.has { it == Mechanic.Indestructible } == true
            val creature = c.isType(CardType.Creature)
            // CR 704.5f, g, h
            (creature && (c.toughness ?: 0) <= 0) ||
                (creature && !indestructible && (c.toughness ?: 0) > 0 && (o.damage >= (c.toughness ?: 0) || o.deathtouchDamage)) ||
                // CR 704.5i
                (c.isType(CardType.Planeswalker) && o.counters(CounterKind.Loyalty) <= 0)
        }
        if (dying.isNotEmpty()) {
            recorder.record(Changes.move(recorder.state, dying, Zone.Graveyard, meanings = listOf(Meaning.StateBased)))
        }
        return Progress.Done
    }
}
