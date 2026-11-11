package org.asyncttwiz.android

import org.asyncttwiz.automatum.ComputedBoard
import org.asyncttwiz.protocol.EnteredTurn
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.PlayCard
import org.asyncttwiz.protocol.PlayZone

/**
 * TBL-104 (owner, 2026-11-06): what Inspect lists under a battlefield card, only what is changed on it or added to it, in
 * this order: its states ("Monstrous", "Ring-bearer: ...", TBL-133), its counters ("+1/+1 counter ×2"; a planeswalker's
 * loyalty and a battle's defense are its corner, not changes), damage ("3 damage"), each continuous effect without its
 * source ("+3/+3 until end of turn", "Flying", "Becomes blue": [ComputedBoard.effects], the source kept for pinned mode),
 * what those effects tell it it can't or must do ("Can't block until end of turn", TBL-134), what it is attached to and
 * "Entered this turn". A card with none of them has no lines. A card in exile lists its states and counters (ZON-094, a
 * suspended card's time counters, ZON-091); a spell on the Stack what effects tell it (TBL-134); other cards off the
 * battlefield have none.
 */
object InspectChanges {
    fun of(play: TableBoard, viewer: String, cards: List<PlayCard>, names: Map<String, String>): Map<String, List<InspectChange>> {
        val battlefield = play.board.containers.filter { it.zone == PlayZone.BATTLEFIELD }.flatMap { it.cards }.toSet()
        val exile = play.board.containers.filter { it.zone == PlayZone.EXILE }.flatMap { it.cards }.toSet()
        val computed = AutomatumBoards.compute(play, viewer)
        val exiled = cards.filter { it.id in exile }.distinctBy { it.id }.associate { card ->
            card.id to buildList {
                computed.exileStates[card.id].orEmpty().forEach { add(InspectChange(it)) }
                counters(card)
            }
        }.filterValues { it.isNotEmpty() }
        // A spell on the Stack: its card stays in the zone it was cast from (STK-001); only a card on the Stack has lines here
        val spells = cards.filter { it.id !in exile && it.id !in battlefield }.distinctBy { it.id }.map { it.id }.let { ids ->
            computed.restrictions(ids, viewer, names).mapValues { (_, lines) -> lines.map { InspectChange(it.text, it.source) } } }
        val shown = cards.filter { it.id in battlefield }.distinctBy { it.id }
        if (shown.isEmpty()) return exiled + spells
        val ids = shown.map { it.id }
        val effects = computed.effects(ids, viewer, names)
        val states = computed.states(ids, viewer, names)
        val restrictions = computed.restrictions(ids, viewer, names)
        return exiled + spells + shown.associate { card ->
            card.id to buildList {
                states[card.id].orEmpty().forEach { add(InspectChange(it.text, it.source)) }
                counters(card)
                card.damage.toIntOrNull()?.takeIf { it > 0 }?.let { add(InspectChange("$it damage")) }
                effects[card.id].orEmpty().forEach { add(InspectChange(it.text, it.source)) }
                restrictions[card.id].orEmpty().forEach { add(InspectChange(it.text, it.source)) }
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

    /** The card's counters, one line per kind ("+1/+1 counter ×2", "time counter ×3"). */
    private fun MutableList<InspectChange>.counters(card: PlayCard) {
        card.typedCounters.filter { it.count != 0 && !corner(it.kind) }.forEach { counter ->
            val name = ComputedBoard.counterName(counter.kind).let { if (it.endsWith(" counter")) it else "$it counter" }
            add(InspectChange(if (counter.count == 1) name else "$name ×${counter.count}"))
        }
    }

    /** A planeswalker's loyalty or a battle's defense: what its corner shows, not a change. */
    private fun corner(kind: String): Boolean = kind == "loyalty" || kind == "defense"
}
