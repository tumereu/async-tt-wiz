package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

internal data class AttentionPresentation(val label: String, val name: String, val own: Boolean, val enabled: Boolean) {
    val color get() = if (own) OwnTableColor else OpponentTableColor
}

internal fun attentionPresentation(game: GameSummary, play: PlayState, viewer: String,
    reviewing: Boolean, connected: Boolean): AttentionPresentation {
    val holder = play.board.effectiveAttentionHolder
    val own = holder == viewer
    val name = if (game.inviter.accountId == holder) game.inviter.username else game.recipient.username
    return AttentionPresentation(if (own) "Your move" else "$name's move", name, own, !reviewing && connected)
}
