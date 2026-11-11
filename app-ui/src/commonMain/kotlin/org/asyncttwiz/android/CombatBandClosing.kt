package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer

/** The open band last shown ([scene] with its [play]), and how far its fading out has gone once it closed. */
private class ClosingBand(val scene: TableScene, val play: TableBoard,
    val fade: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>)

/** The last open band shown, kept until the band closes. */
private class OpenBand { var shown: Pair<TableScene, TableBoard>? = null }

/**
 * CMB-001 (owner playtest 2026-11-11): when the combat band closes, what sat on it or by it fades out where it was, over
 * [CARD_FADE_DURATION_MILLIS], while the band itself closes with the battlefields (HistorySceneAnimation): its lane separators,
 * the target chips with the damage on them and the life-gain heart, the combat chips and badges (the copies themselves
 * fade out as table items). A band closing to the chip row while its viewer declares attackers keeps its chips, so nothing
 * fades then. Drawn only: touches reach the table beneath. [bounds]: where an item is drawn now.
 */
@Composable
fun ClosingCombatBand(play: TableBoard, scene: TableScene, viewer: String, bounds: (TableItem) -> TableBounds) {
    val last = remember(play.playId) { OpenBand() }
    var closing by remember(play.playId) { mutableStateOf<ClosingBand?>(null) }
    val open = scene.combatPanel != null
    SideEffect { if (open) last.shown = scene to play }
    LaunchedEffect(open) {
        if (open) { closing = null; return@LaunchedEffect }
        val (before, board) = last.shown ?: return@LaunchedEffect
        last.shown = null
        if (scene.combatLabels.isNotEmpty()) return@LaunchedEffect
        AppLog.info("combat", "Combat band closed at ${play.nodeId}: ${before.combatLabels.size} lanes, " +
            "${before.items.count { it.viewport == "combat" }} copies and the damage shown (${before.combatDealt?.targets}) fade out")
        val fade = Animatable(1f)
        closing = ClosingBand(before, board, fade)
        fade.animateTo(0f, tween(CARD_FADE_DURATION_MILLIS))
        closing = null
    }
    val band = closing ?: return
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = band.fade.value }) {
        // The lane separators, where they were in the open band
        band.scene.combatBand?.let { b ->
            Canvas(Modifier.fillMaxSize()) {
                val d = density
                clipRect(b.x * d, b.y * d, b.right * d, b.bottom * d) {
                    for ((from, to) in band.scene.combatLaneDividers) drawLine(Color.Black.copy(alpha = CombatStackPresentation.LANE_DIVIDER_ALPHA),
                        Offset(from.x * d, from.y * d), Offset(to.x * d, to.y * d),
                        CombatStackPresentation.laneDividerStroke(band.scene.combatCardWidth) * d)
                }
            }
        }
        CombatAreaOverlay(null, band.play, band.scene, viewer, writable = false, leaving = true) { item ->
            // A copy fades out where it was; a real card's chip goes with its card
            if (item.viewport == "combat") item.bounds else bounds(item)
        }
    }
}
