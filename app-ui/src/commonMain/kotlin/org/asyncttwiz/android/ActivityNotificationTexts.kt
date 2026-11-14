package org.asyncttwiz.android

import org.asyncttwiz.protocol.ActivityTexts
import org.asyncttwiz.protocol.GameNoticeContent
import org.asyncttwiz.protocol.ResumePoint
import org.asyncttwiz.protocol.SuggestedResult

// The notifications' words are the protocol's (ActivityTexts), which the server's browser notifications use too (WEB-052).

/** [subText]: the recipient's own deck in the game (I-D57), shown in the notification's header. */
data class GameNotificationText(val title: String, val text: String, val whenMillis: Long, val subText: String? = null)
fun gameNotificationText(content: GameNoticeContent) = GameNotificationText(ActivityTexts.gameTitle(content),
    ActivityTexts.gameText(content), offsetDateTimeMillis(content.lastEventAt), content.deckName)

/** I-D57: the point a game notification says you would resume at ([ActivityTexts.resumePoint]). */
fun resumePointText(point: ResumePoint, opponentName: String): String = ActivityTexts.resumePoint(point, opponentName)

/** Game history §4.5: the cleanup's warning 3 days before a game in play is deleted ([ActivityTexts.gameRemoval]). */
fun gameRemovalText(hint: org.asyncttwiz.protocol.ActivityHint): Pair<String, String> = ActivityTexts.gameRemoval(hint)

/** I-D57: a pod notification's text: the set or cube the pod drafts. */
fun podNotificationDetail(hint: org.asyncttwiz.protocol.ActivityHint): String? = ActivityTexts.podDetail(hint)

/** I-D53: the rules' result, from the notified player's side. */
fun byRulesResult(outcome: SuggestedResult) = ActivityTexts.byRulesResult(outcome)
