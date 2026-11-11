package org.asyncttwiz.automatum

import arcanum.engine.state.CardId
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.MtgRef

/**
 * ATW's card ids and the engine's (D21, D35): a game on engine state plays the prepared cards by their own ids, and the
 * table names the engine's tokens by the engine's ids ([EngineProjection]), so the two are the same card id.
 */
class CardIds internal constructor() {
    fun engine(atw: String): CardId = CardId(atw)
    fun atw(engine: CardId): String = engine.value

    /** ATW's zone-change count for the engine object [id]. */
    fun zoneChanges(id: ObjectId.Card): Int = id.zoneChanges

    fun ref(id: ObjectId): MtgRef = when (id) {
        is ObjectId.Card -> MtgRef.Card(atw(id.card), zoneChanges(id))
        is ObjectId.StackItem -> MtgRef.StackItem(id.id)
    }

    fun ref(target: TargetRef): MtgRef = when (target) {
        is TargetRef.Object -> ref(target.id)
        is TargetRef.Player -> MtgRef.Player(target.id.value)
    }

    fun obj(ref: MtgRef): ObjectId? = when (ref) {
        is MtgRef.Card -> ObjectId.Card(engine(ref.cardId), ref.zoneChanges)
        is MtgRef.StackItem -> ObjectId.StackItem(ref.id)
        is MtgRef.Player -> null
    }

    fun target(ref: MtgRef): TargetRef = when (ref) {
        is MtgRef.Player -> TargetRef.Player(PlayerId(ref.accountId))
        is MtgRef.Card -> TargetRef.Object(ObjectId.Card(engine(ref.cardId), ref.zoneChanges))
        is MtgRef.StackItem -> TargetRef.Object(ObjectId.StackItem(ref.id))
    }
}
