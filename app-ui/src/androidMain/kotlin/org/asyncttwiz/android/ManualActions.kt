package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

data class ManualInspection(val gameId: String, val playId: String, val cardId: String,
    val faceIds: List<String>, val explicit: Boolean = false, val snapshot: PlayCard? = null,
    val provisional: Boolean = false, val stackEntryId: String? = null,
    /** TBL-111: the other members of the battlefield group held with [cardId], shown after it in this order. */
    val group: List<String> = emptyList())

/** Shared gesture decision used by the production square; a recognized hold always consumes click. */
internal class ManualActionPress {
    var consumed = false
        private set
    var tooltip = false
        private set
    fun start() { consumed = false; tooltip = false }
    fun hold(assigned: (() -> Unit)?) {
        if (consumed) return
        consumed = true
        if (assigned != null) assigned() else tooltip = true
    }
    fun cancel() { consumed = true; tooltip = false }
    fun release(): Boolean {
        val click = !consumed
        tooltip = false
        consumed = true
        return click
    }
}

/** Source-compatible no-argument factory for the original manual-control callers/tests.
 * The quick/turn ActionPress(Boolean) retains its distinct terminal-result API. */
internal fun ActionPress(): ManualActionPress = ManualActionPress()

