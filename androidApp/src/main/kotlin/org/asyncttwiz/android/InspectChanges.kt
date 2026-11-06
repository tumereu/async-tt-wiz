package org.asyncttwiz.android

import org.asyncttwiz.automatum.ComputedBoard
import org.asyncttwiz.protocol.EnteredTurn
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.PlayCard
import org.asyncttwiz.protocol.PlayZone

/**
 * TBL-104 (owner, 2026-11-06): what Inspect lists under a battlefield card, only what is changed on it or added to it, in
 * this order: its counters ("+1/+1 counter ×2"; a planeswalker's loyalty and a battle's defense are its corner, not
 * changes), damage ("3 damage"), each continuous effect without its source ("+3/+3 until end of turn", "Flying", "Becomes
 * blue": [ComputedBoard.effects], the source kept for pinned mode), what it is attached to and "Entered this turn". A card
 * with none of them has no lines; cards off the battlefield have none.
 */
internal object InspectChanges {
    fun of(play: TableBoard, viewer: String, cards: List<PlayCard>, names: Map<String, String>): Map<String, List<InspectChange>> {
        val battlefield = play.board.containers.filter { it.zone == PlayZone.BATTLEFIELD }.flatMap { it.cards }.toSet()
        val shown = cards.filter { it.id in battlefield }.distinctBy { it.id }
        if (shown.isEmpty()) return emptyMap()
        val effects = AutomatumBoards.compute(play, viewer).effects(shown.map { it.id }, viewer, names)
        return shown.associate { card ->
            card.id to buildList {
                card.typedCounters.filter { it.count != 0 && !corner(it.kind) }.forEach { counter ->
                    val name = ComputedBoard.counterName(counter.kind).let { if (it.endsWith(" counter")) it else "$it counter" }
                    add(InspectChange(if (counter.count == 1) name else "$name ×${counter.count}"))
                }
                card.damage.toIntOrNull()?.takeIf { it > 0 }?.let { add(InspectChange("$it damage")) }
                effects[card.id].orEmpty().forEach { add(InspectChange(it.text, it.source)) }
                when (val to = card.attachedTo) {
                    is MtgRef.Card -> add(InspectChange("Attached to " +
                        if (play.board.cards.firstOrNull { it.id == to.cardId }?.isFaceDown == true) "a face-down card"
                        else StackAbilities.visibleName(play, to.cardId, viewer)))
                    is MtgRef.Player -> add(InspectChange("Attached to ${if (to.accountId == viewer) "you" else names[to.accountId] ?: "the opponent"}"))
                    else -> Unit
                }
                if (EnteredTurn.marked(play.board, card)) add(InspectChange("Entered this turn"))
            }
        }.filterValues { it.isNotEmpty() }
    }

    /** A planeswalker's loyalty or a battle's defense: what its corner shows, not a change. */
    private fun corner(kind: String): Boolean = kind == "loyalty" || kind == "defense"
}
