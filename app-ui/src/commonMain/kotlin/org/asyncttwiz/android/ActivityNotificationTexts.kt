package org.asyncttwiz.android

import org.asyncttwiz.protocol.GameNoticeContent
import org.asyncttwiz.protocol.ResumePoint
import org.asyncttwiz.protocol.SuggestedResult

/** [subText]: the recipient's own deck in the game (I-D57), shown in the notification's header. */
data class GameNotificationText(val title: String, val text: String, val whenMillis: Long, val subText: String? = null)
fun gameNotificationText(content: GameNoticeContent) = GameNotificationText(
    "${content.gameName} · vs ${content.opponentName}",
    (content.gameOver?.let { if (content.byRules) "Game over · ${byRulesResult(it)}" // I-D53
        else if (content.conceded) "Game over · ${content.opponentName} conceded"
        else "Game over · ${content.opponentName} accepted ${gameOverResult(it)}" }
        ?: ((if (content.reminder) "${content.opponentName} is waiting for you"
        else if (content.opponentReady) "${content.opponentName} is ready" // LOB-114
        else if (content.branched) "${content.opponentName} responded differently · your later actions were taken back" // LOB-116
        else if (content.yourMove) "Your move"
        else "${content.opponentName}'s move") +
            // I-D57: where you would resume (the point you last saw, never further)
            content.resumeAt?.let { " · ${resumePointText(it, content.opponentName)}" }.orEmpty())) +
        (if (content.newMessage) " · new message" else ""),
    offsetDateTimeMillis(content.lastEventAt), content.deckName)

/** I-D57: the point a game notification says you would resume at, in the table's own words (the turn square, TRN-007):
 * "Turn 5, your turn, Upkeep" or "Turn 5, <opponent>'s turn, End step"; the opening is "Mulliganing" (TRN-027). */
fun resumePointText(point: ResumePoint, opponentName: String): String =
    if (point.turn == 0) "Mulliganing"
    else "Turn ${point.turn}, ${if (point.yours) "your" else "$opponentName's"} turn" + point.step?.let { ", ${it.label}" }.orEmpty()

/** Game history §4.5: the cleanup's warning 3 days before a game in play is deleted: "<game> · vs <opponent>" and
 * "Removed in 3 days if nobody plays". */
fun gameRemovalText(hint: org.asyncttwiz.protocol.ActivityHint): Pair<String, String> =
    "${hint.resourceName ?: "Game"} · vs ${hint.opponentName ?: "your opponent"}" to "Removed in 3 days if nobody plays"

/** I-D57: a pod notification's text: the set or cube the pod drafts. */
fun podNotificationDetail(hint: org.asyncttwiz.protocol.ActivityHint): String? =
    hint.sourceName?.takeIf { hint.kind.isPodActivity() }

/** I-D53: the rules' result, from the notified player's side. */
fun byRulesResult(outcome: SuggestedResult) = when (outcome) {
    SuggestedResult.WIN -> "You won"
    SuggestedResult.LOSS -> "You lost"
    SuggestedResult.DRAW -> "Draw"
}

/** LOB-111: the accepted suggestion, from its author's (the notified player's) side. */
private fun gameOverResult(outcome: SuggestedResult) = when (outcome) {
    SuggestedResult.WIN -> "your win"
    SuggestedResult.LOSS -> "your loss"
    SuggestedResult.DRAW -> "the draw"
}
