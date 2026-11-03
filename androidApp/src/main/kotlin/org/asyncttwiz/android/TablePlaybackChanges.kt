package org.asyncttwiz.android

import org.asyncttwiz.protocol.*


/** Previous-group transport is a normal step, not a jump, even when it unapplies several nodes. */
internal fun tablePlaybackChanges(from: String, to: String, index: HistoryTree): TableChangeBatch {
    if (index.depth(from) <= index.depth(to)) return TableChangeExtraction.between(from, to, index.nodes)
    val events = ArrayList<TableAppliedEvent>()
    var cursor = from
    repeat(256) {
        val node = index.nodes[cursor] ?: return TableChangeBatch(jump = true)
        events.add(TableAppliedEvent(node, reverse = true))
        cursor = node.parentId
        if (cursor == to) return TableChangeBatch(events)
    }
    return TableChangeBatch(jump = true)
}

/** The coin result everywhere it is shown: which face, and what it means for the flipper. */
internal fun coinResultText(face: CoinFace) = when (face) {
    CoinFace.HEADS -> "Heads / win"
    CoinFace.TAILS -> "Tails / loss"
}
