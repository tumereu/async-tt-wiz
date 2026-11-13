package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arcanum.engine.change.Change
import kotlinx.coroutines.delay
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent

/** The test tag of the turn banner (TRN-074). */
const val TURN_BANNER_TAG = "turn-banner"

/** TRN-074: how long the banner stays whole, and its fade after that: about 1.2 s in all. */
const val TURN_BANNER_WHOLE_MILLIS = 300L
const val TURN_BANNER_FADE_MILLIS = 900

/**
 * TRN-074: the banner to show on this table (main thread, snapshot state): it and the plays shown (HIS-112) follow one
 * another and never overlap, holding nothing: a banner whose play came first waits for it ([Banner.afterPlays]), and a play
 * waits for the banner showing ([holdsPlays]).
 */
class TurnBannerQueue {
    class Banner(val key: Long, val player: String, val afterPlays: Boolean)

    var current by mutableStateOf<Banner?>(null)
        private set

    /** Whether a play shown waits for the banner. */
    val holdsPlays: Boolean get() = current?.afterPlays == false

    /** The table shows the turn change [change], with [show] the play shown now: it is the banner now. */
    fun post(change: TurnChange, show: GameDevice.PlayShow?) {
        current = Banner(change.serial, change.player, TurnBanners.afterPlay(change, show))
    }

    /** The banner [banner] starts showing: no longer after a play, the next play waits for it. */
    fun started(banner: Banner) {
        if (current?.key == banner.key && current?.afterPlays == true) current = Banner(banner.key, banner.player, false)
    }

    fun done(banner: Banner) { if (current?.key == banner.key) current = null }

    /** Timeline mode entered, or what it told taken back: it ends. */
    fun drop() { current = null }
}

/**
 * TRN-074: "Your turn" / "<name>'s turn" at the screen's centre in the turn player's colour, fading out in about 1.2 s. No
 * pointer handler and no semantics action: touches go through it to what lies under it. [playOpen]: a play shown holds a
 * banner that comes after it.
 */
@Composable
fun TurnBannerOverlay(queue: TurnBannerQueue, playOpen: Boolean, viewer: String, names: Map<String, String>, modifier: Modifier = Modifier) {
    val banner = queue.current ?: return
    if (banner.afterPlays && playOpen) return
    key(banner.key) {
        val own = banner.player == viewer
        val text = if (own) "Your turn" else "${names[banner.player] ?: "The other player"}'s turn"
        val color = if (own) OwnTableColor else OpponentTableColor
        val alpha = remember { Animatable(1f) }
        RecordShown(text, banner.key)
        LaunchedEffect(Unit) {
            queue.started(banner)
            delay(TURN_BANNER_WHOLE_MILLIS)
            alpha.animateTo(0f, tween(TURN_BANNER_FADE_MILLIS))
            queue.done(banner)
        }
        val shape = RoundedCornerShape(14.dp)
        Text(text, modifier.graphicsLayer { this.alpha = alpha.value }.testTag(TURN_BANNER_TAG).clearAndSetSemantics { contentDescription = text }
            .background(Color.White.copy(alpha = .94f), shape).border(2.dp, color, shape).padding(horizontal = 24.dp, vertical = 12.dp),
            color = color, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
    }
}
