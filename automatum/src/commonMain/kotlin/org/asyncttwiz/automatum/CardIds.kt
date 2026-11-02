package org.asyncttwiz.automatum

import arcanum.engine.state.CardId
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.MtgRef

/**
 * ATW's card ids and the engine's (D21, D35). They are the same card id except where the engine names a card itself:
 * a token, whose id the engine derives (ATW's token keeps it in its ledger, [CardLedger.card]), and, inside one call on
 * a viewer's state, the new ids a shuffle gives the cards hidden from the viewer ([RandomKind.Objects.newIds], D60),
 * whose zone-change counts start again at 0 ([offset]).
 */
class CardIds internal constructor() {
    private val toEngine = mutableMapOf<String, CardId>()
    private val toAtw = mutableMapOf<CardId, String>()
    private val offsets = mutableMapOf<CardId, Int>()

    internal fun alias(atw: String, engine: CardId, offset: Int = 0) {
        toEngine[atw] = engine
        toAtw[engine] = atw
        if (offset != 0) offsets[engine] = offset else offsets.remove(engine)
    }

    /** Whether [engine] is a card id only this call knows (a viewer's shuffle): not kept in ATW. */
    internal fun transient(engine: CardId): Boolean = engine in offsets

    fun engine(atw: String): CardId = toEngine[atw] ?: CardId(atw)
    fun atw(engine: CardId): String = toAtw[engine] ?: engine.value

    /** ATW's zone-change count for the engine object [id]. */
    fun zoneChanges(id: ObjectId.Card): Int = id.zoneChanges + (offsets[id.card] ?: 0)

    fun ref(id: ObjectId): MtgRef = when (id) {
        is ObjectId.Card -> MtgRef.Card(atw(id.card), zoneChanges(id))
        is ObjectId.StackItem -> MtgRef.StackItem(id.id)
    }

    fun ref(target: TargetRef): MtgRef = when (target) {
        is TargetRef.Object -> ref(target.id)
        is TargetRef.Player -> MtgRef.Player(target.id.value)
    }

    fun obj(ref: MtgRef): ObjectId? = when (ref) {
        is MtgRef.Card -> engine(ref.cardId).let { ObjectId.Card(it, ref.zoneChanges - (offsets[it] ?: 0)) }
        is MtgRef.StackItem -> ObjectId.StackItem(ref.id)
        is MtgRef.Player -> null
    }

    fun target(ref: MtgRef): TargetRef = when (ref) {
        is MtgRef.Player -> TargetRef.Player(PlayerId(ref.accountId))
        else -> TargetRef.Object(obj(ref)!!)
    }

    companion object {
        /** The ids of [board]: tokens by the engine id their ledger keeps. */
        fun of(board: BoardState): CardIds = CardIds().also { ids ->
            board.cards.forEach { card -> CardLedger.of(card.ledger).card?.let { ids.alias(card.id, CardId(it)) } }
        }
    }
}
