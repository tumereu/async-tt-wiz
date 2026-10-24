package org.asyncttwiz.android

internal enum class TableBackAction { CLOSE_OVERLAY, EXIT_REVIEW, CLEAR_SELECTION, PROMPT, LEAVE }

/** Personal navigation only. A dismissed layer never also arms leaving the table. */
internal class TableBackStateMachine {
    private var promptedAt: Long? = null
    fun reset() { promptedAt = null }
    fun back(now: Long, overlay: Boolean, reviewing: Boolean, selection: Boolean): TableBackAction {
        val layer = when {
            overlay -> TableBackAction.CLOSE_OVERLAY
            reviewing -> TableBackAction.EXIT_REVIEW
            selection -> TableBackAction.CLEAR_SELECTION
            else -> null
        }
        if (layer != null) { reset(); return layer }
        val previous = promptedAt
        if (previous != null && now - previous in 0..2000) { reset(); return TableBackAction.LEAVE }
        promptedAt = now
        return TableBackAction.PROMPT
    }
}
