package org.asyncttwiz.android

import androidx.compose.ui.graphics.Color
import org.asyncttwiz.protocol.*

internal data class AttentionPresentation(val label: String, val name: String, val own: Boolean, val enabled: Boolean) {
    val color get() = if (own) OwnTableColor else OpponentTableColor
}

internal fun attentionPresentation(game: GameSummary, play: TableBoard, viewer: String,
    reviewing: Boolean, connected: Boolean): AttentionPresentation {
    // TRN-058: the move the game list shows too (on engine state: whom the game really waits for)
    val holder = play.board.moveHolder
    val own = holder == viewer
    val name = if (game.inviter.accountId == holder) game.inviter.username else game.recipient.username
    val label = if (own) "Your move" else "Waiting · $name"
    return AttentionPresentation(label, name, own, !reviewing && connected)
}

internal fun turnHeader(game: GameSummary, turn: TurnState, viewer: String): String {
    val player = if (turn.accountId == game.inviter.accountId) game.inviter else game.recipient
    return "${if (turn.accountId == viewer) "Your turn" else "${player.username}'s turn"} · ${turn.number}"
}

internal fun battlefieldColor(owner: String, viewer: String, turnPlayer: String, lands: Boolean): Color =
    if (owner != turnPlayer) Color(if (lands) 0xffdfe2e2 else 0xffeceeee)
    else if (owner == viewer) Color(if (lands) 0xffc4dcd0 else 0xffe2efe6)
    else Color(if (lands) 0xffe9d1d3 else 0xfff4e8ea)

internal val TimelineBarColor = Color(0xffe5e7e9)
internal fun tableBarColor(board: BoardState, viewer: String, reviewing: Boolean = false) =
    if (reviewing) TimelineBarColor else battlefieldColor(board.moveHolder, viewer, board.moveHolder, lands = false)

