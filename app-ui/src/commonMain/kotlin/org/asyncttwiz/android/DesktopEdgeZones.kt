package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The edge zones of a card in Build, Prepare and the deck and cube editor on the desktop layout (docs/specs/web-client.md §5,
// wireframe D8; WEB-045, WEB-046): the mouse can't swipe, so a card's left and right quarters move one copy where a swipe that
// way would (SYS-083), a click in its middle opens its menu as a tap does, and ← / → / ↓ do the same for the card under the
// pointer. Holding Alt (or Shift) over it previews it as holding a finger on it does. Nothing of this exists on the phone layout.

/** WEB-045: the share of a card's width each edge zone takes (a quarter, §5). */
const val EDGE_ZONE_FRACTION = .25f

/** WEB-045: one edge zone's destination: the section's [label] ("Side"), its [color], and moving one copy there ([move]). */
class EdgeTarget(val label: String, val color: Color, val move: () -> Unit)

/** Where on a card the pointer is: its left or right edge zone, or the middle. */
sealed interface EdgeZone {
    data object Left : EdgeZone
    data object Right : EdgeZone
    data object Middle : EdgeZone

    companion object {
        /** The zone at [fraction] of the card's width (0 its left edge, 1 its right): a side's quarter when that side has a
         * destination ([left], [right]), else the middle. */
        fun at(fraction: Float, left: Boolean, right: Boolean): EdgeZone = when {
            left && fraction < EDGE_ZONE_FRACTION -> Left
            right && fraction > 1f - EDGE_ZONE_FRACTION -> Right
            else -> Middle
        }
    }
}

/**
 * WEB-045, WEB-046: one card's edge zones on the desktop layout: where the pointer is on it ([at], a share of its width; null
 * off it), its two destinations, what a click in its middle does ([menu]) and the preview while Alt is held ([hold]). Made by
 * [rememberEdgeZones]; main thread.
 */
@Stable
class EdgeZones internal constructor() {
    var at by mutableStateOf<Float?>(null); internal set
    internal var name = ""
    internal var left: EdgeTarget? = null
    internal var right: EdgeTarget? = null
    internal var menu: (() -> Unit)? = null
    internal var hold: (Boolean) -> Unit = {}
    private var holding = false

    /** The zone under the pointer, null when it is off the card. */
    val zone: EdgeZone? get() = at?.let { EdgeZone.at(it, left != null, right != null) }

    /** A click at [fraction] of the card's width: moves one copy to that edge zone's section, or opens the menu. */
    fun click(fraction: Float) = act(EdgeZone.at(fraction, left != null, right != null), "Click")

    private fun act(zone: EdgeZone, how: String): Boolean {
        val target = when (zone) {
            EdgeZone.Left -> left
            EdgeZone.Right -> right
            EdgeZone.Middle -> { val open = menu ?: return false; AppLog.info("cards", "$how on $name: its menu"); open(); return true }
        } ?: return false
        AppLog.info("cards", "$how on $name's ${if (zone == EdgeZone.Left) "left" else "right"} edge zone: one copy to ${target.label}")
        target.move()
        return true
    }

    /** WEB-046: a key while the pointer is on the card: ← / → move one copy to that edge zone's section, ↓ opens the menu;
     * Alt (Shift too) held previews the card. Whether the card took it. Never while a text field has the keyboard. */
    internal fun key(key: DesktopKey): Boolean {
        if (key.textField) return false
        if (key.key == "Alt" || key.key == "Shift") {
            if (key.repeat) return true
            if (key.down != holding) {
                holding = key.down
                AppLog.info("cards", "${key.key} ${if (key.down) "held" else "released"} over $name: preview ${if (key.down) "shown" else "closed"}")
                hold(key.down)
            }
            return true
        }
        // Another key with Shift held (Shift+Tab, "?") is that key's: the preview Shift opened closes
        if (key.down && holding) { holding = false; AppLog.info("cards", "${key.key} pressed over $name: the preview closed"); hold(false) }
        if (!key.down || key.alt || key.ctrl || key.meta || key.shift) return false
        val zone = when (key.key) {
            "ArrowLeft" -> EdgeZone.Left
            "ArrowRight" -> EdgeZone.Right
            "ArrowDown" -> EdgeZone.Middle
            else -> return false
        }
        if (key.repeat) return true
        if (!act(zone, "Key ${KeyChord(key.key).label}")) AppLog.info("cards", "Key ${KeyChord(key.key).label} on $name: no edge zone there")
        return true
    }

    /** The pointer left the card: a preview Alt opened closes. */
    internal fun pointerLeft() {
        if (holding) { holding = false; hold(false) }
    }
}

/**
 * WEB-045, WEB-046: the edge zones of the card [name] on the desktop layout (null on the phone layout, where nothing changes):
 * [left] and [right] its destinations (null: no zone on that side, as for a card that can't move now), [menu] what a click in
 * its middle and ↓ do, [hold] the preview Alt shows while held. The card puts [edgeZoneHover] on itself, gives its gesture's
 * mouse clicks to [EdgeZones.click] and draws [EdgeZoneOverlay] over its picture.
 */
@Composable
fun rememberEdgeZones(name: String, left: EdgeTarget?, right: EdgeTarget?, menu: (() -> Unit)?, hold: (Boolean) -> Unit): EdgeZones? {
    if (!desktopLayout()) return null
    val zones = remember { EdgeZones() }
    zones.name = name
    zones.left = left
    zones.right = right
    zones.menu = menu
    zones.hold = hold
    val over = zones.at != null
    DesktopKeyboard.Listen(over) { zones.key(it) }
    LaunchedEffect(over) { if (!over) zones.pointerLeft() }
    return zones
}

/** WEB-045: follows the mouse over the card for [zones] (nothing without them; touches are left alone). */
fun Modifier.edgeZoneHover(zones: EdgeZones?): Modifier = if (zones == null) this else pointerInput(zones) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.type == PointerType.Mouse } ?: continue
            when (event.type) {
                PointerEventType.Enter, PointerEventType.Move ->
                    zones.at = if (size.width > 0) (change.position.x / size.width).coerceIn(0f, 1f) else .5f
                PointerEventType.Exit -> zones.at = null
            }
        }
    }
}

/** WEB-045 (wireframe D8): while the pointer is on the card, its edge zones tinted in their sections' colours with their names
 * ("◂ Side", "Maybe ▸"), the one under the pointer strongly. */
@Composable
fun BoxScope.EdgeZoneOverlay(zones: EdgeZones?) {
    val zone = zones?.zone ?: return
    zones.left?.let { EdgeZoneBand(it, "◂ ${it.label}", Alignment.CenterStart, zone == EdgeZone.Left, "edge-zone-left") }
    zones.right?.let { EdgeZoneBand(it, "${it.label} ▸", Alignment.CenterEnd, zone == EdgeZone.Right, "edge-zone-right") }
}

@Composable
private fun BoxScope.EdgeZoneBand(target: EdgeTarget, text: String, alignment: Alignment, strong: Boolean, tag: String) {
    Box(Modifier.align(alignment).fillMaxHeight().fillMaxWidth(EDGE_ZONE_FRACTION)
        .background(target.color.copy(alpha = if (strong) .62f else .16f)).testTag(if (strong) "$tag-active" else tag),
        contentAlignment = Alignment.BottomCenter) {
        Text(text, Modifier.padding(bottom = 10.dp).wrapContentWidth(unbounded = true), color = if (strong) Color.White else target.color,
            fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}
