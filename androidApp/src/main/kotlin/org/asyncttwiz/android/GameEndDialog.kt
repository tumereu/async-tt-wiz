package org.asyncttwiz.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.LossCause
import org.asyncttwiz.protocol.MatchEnding
import org.asyncttwiz.protocol.ResultNotice

/** TRN-065: why Ready is greyed while the last result waits for your OK. */
internal const val RESULT_FIRST = "Tap OK on the last game's result first."

/** The test tag of the game end dialog (TRN-065). */
internal const val GAME_END_DIALOG_TAG = "game-end-dialog"

/**
 * TRN-065 (owner 2026-11-07): the words of a game's end for [viewer]: [title] "You won", "You lost" or "Draw", and
 * [reason], how it ended in a few plain words ("Bob's life reached 0", "Bob conceded", "Agreed: Infinite combo"), empty
 * when nothing more can be said.
 */
internal data class GameEndText(val title: String, val reason: String) {
    companion object {
        /** [winner] (null: a draw), how it [ending]ed, why each loser lost by the rules ([causes], by account); [names]:
         * the players' names by account. */
        fun of(winner: String?, ending: MatchEnding, causes: Map<String, LossCause>, viewer: String, names: Map<String, String>): GameEndText {
            val title = when (winner) { null -> "Draw"; viewer -> "You won"; else -> "You lost" }
            fun name(account: String) = names[account] ?: "The other player"
            val reason = when (ending) {
                is MatchEnding.Conceded -> if (ending.by == viewer) "You conceded" else "${name(ending.by)} conceded"
                is MatchEnding.Agreed -> if (ending.reason.isBlank()) "Agreed" else "Agreed: ${ending.reason}"
                is MatchEnding.Draw -> if (ending.loop) "An endless loop" else "Both players lost at once"
                MatchEnding.ByRules -> causes.entries.firstOrNull { it.key != winner }?.let { (loser, cause) ->
                    val you = loser == viewer
                    when (cause) {
                        LossCause.ZeroLife -> if (you) "Your life reached 0" else "${name(loser)}'s life reached 0"
                        LossCause.EmptyLibrary -> if (you) "You drew from an empty library" else "${name(loser)} drew from an empty library"
                        LossCause.Poison -> if (you) "You got 10 poison counters" else "${name(loser)} got 10 poison counters"
                        is LossCause.CardEffect -> "${cause.card ?: "A card"}'s effect"
                    }
                }.orEmpty()
                MatchEnding.Unfinished -> ""
            }
            return GameEndText(title, reason)
        }

        /** What the lobby's dialog says of [notice]. */
        fun of(notice: ResultNotice, viewer: String, names: Map<String, String>): GameEndText =
            of(notice.winner, notice.ending, notice.causes, viewer, names)
    }
}

/**
 * TRN-065 (owner 2026-11-07): the result of a game in the middle of the screen: big [text]'s title, its reason, then
 * [waiting] ("Waiting for Bob to catch up", OK greyed) when it can't be confirmed yet, the eye at the bottom left (hides the
 * dialog to look at what is behind it; hidden, only the eye stays, at the same spot, STK-044) and OK at the right.
 */
@Composable
internal fun GameEndDialog(text: GameEndText, waiting: String?, ok: () -> Unit) {
    var hidden by remember { mutableStateOf(false) }
    val shown by animateFloatAsState(if (hidden) 0f else 1f, tween(180), label = "game end")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(16.dp)
        // Hidden, nothing but the eye takes touches: the board behind it stays free
        Column(Modifier.width(300.dp).padding(horizontal = 8.dp).testTag(GAME_END_DIALOG_TAG)
            .then(if (hidden) Modifier else Modifier.shadow(8.dp, shape).clip(shape).background(Color.White)
                .border(2.dp, AutomatumViolet, shape).pointerInput(Unit) {}.semantics { contentDescription = "Game result: ${text.title}" })) {
                // Hidden, the words fade but keep their room: the eye stays at the same spot
                Column(Modifier.fillMaxWidth().alpha(shown).padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MenuInk)
                        if (text.reason.isNotEmpty()) Text(text.reason, style = MaterialTheme.typography.bodyLarge, color = MenuInk,
                            textAlign = TextAlign.Center)
                        if (waiting != null) Text(waiting, style = MaterialTheme.typography.bodyMedium,
                            color = MenuInk.copy(alpha = .7f), textAlign = TextAlign.Center)
                }
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color.White, border = BorderStroke(2.dp, AutomatumViolet.copy(alpha = 1f - shown)),
                        shadowElevation = if (hidden) 6.dp else 0.dp) {
                        HoldTip(if (hidden) "Show the result" else "Hide the result") { EyeButton(hidden, { hidden = !hidden }, "result") }
                    }
                    Spacer(Modifier.weight(1f))
                    AnimatedVisibility(!hidden, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
                        ChoiceButton("OK", Icons.Outlined.Check, Modifier.widthIn(min = 88.dp).padding(end = 6.dp),
                            enabled = waiting == null, primary = true, onClick = ok)
                    }
                }
        }
    }
}
