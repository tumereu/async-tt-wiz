package org.asyncttwiz.protocol

/** One event changes a few cards of a board that may hold hundreds: the reducer's per-event checks visit only those,
 * so a 1000-member Automatum batch (I-D14) on a big board doesn't hash every card a dozen times per member. */
internal object CardChanges {
    /**
     * Calls [each] with the index in [to], the card of the same id in [from] (null: a new card) and the card, for every
     * card of [to] that isn't the very same object at the same index of [from], in [to]'s order. A card left untouched by
     * an event is the same object in its board after it (boards are immutable and copied only where they change), so
     * every card that differs is visited; [from] is hashed only once the two lists stop lining up.
     */
    inline fun each(from: List<PlayCard>, to: List<PlayCard>, each: (index: Int, previous: PlayCard?, card: PlayCard) -> Unit) {
        if (from === to) return
        var byId: Map<String, PlayCard>? = null
        val before = from.iterator()
        var index = 0
        for (card in to) {
            val aligned = if (before.hasNext()) before.next() else null
            if (aligned !== card) {
                val previous = if (aligned != null && aligned.id == card.id) aligned
                    else (byId ?: from.associateBy { it.id }.also { byId = it })[card.id]
                each(index, previous, card)
            }
            index++
        }
    }
}
