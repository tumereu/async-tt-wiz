package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import org.asyncttwiz.protocol.*

/**
 * WEB-117: the quick look of the desktop draft's cards (§5, wireframe D9): the card the mouse rests on ([resting]) shows
 * larger with every face ([shown]) after [QUICK_LOOK_MILLIS], as the table's does (WEB-026); a button pressed hides it
 * until it is let go. Main thread.
 */
@Stable
class PodQuickLook {
    var resting by mutableStateOf<DraftCopy?>(null); private set
    var pressed by mutableStateOf(false); private set
    var shown by mutableStateOf<DraftCopy?>(null); internal set
    /** Counts the releases, so a press and release within one frame still restarts the rest timer. */
    var releases by mutableIntStateOf(0); private set
    /** The mouse came onto [copy] ([inside]) or left it. */
    fun over(copy: DraftCopy, inside: Boolean) {
        if (inside) { if (resting != copy) { resting = copy; shown = null } }
        else if (resting == copy) { resting = null; shown = null }
    }
    /** A mouse button went down ([down]) or all are up again. */
    fun press(down: Boolean) { pressed = down; if (down) shown = null else releases++ }
}

/** WEB-117: runs [look]'s rest timer: its card shows after [QUICK_LOOK_MILLIS] unless [blocked] (a hold's Inspect or a
 * card's menu is open) or a button is down. */
@Composable
fun PodQuickLookTimer(look: PodQuickLook, blocked: Boolean) {
    LaunchedEffect(look, look.resting, look.pressed, look.releases, blocked) {
        look.shown = null
        val copy = look.resting ?: return@LaunchedEffect
        if (blocked || look.pressed) return@LaunchedEffect
        delay(QUICK_LOOK_MILLIS)
        look.shown = copy
        AppLog.info("pod", "Quick look of ${copy.id} (${copy.definition})")
    }
}

/** WEB-117: tells [look] when the mouse is on [copy]'s card and when a button is pressed there; nothing without a [look]
 * (the phone layout) or for a finger. Never consumes the events: the card's own gestures still get them. */
fun Modifier.podQuickLook(look: PodQuickLook?, copy: DraftCopy): Modifier = if (look == null) this else pointerInput(look, copy) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.none { it.type == PointerType.Mouse }) continue
            when (event.type) {
                PointerEventType.Enter -> look.over(copy, true)
                PointerEventType.Exit -> look.over(copy, false)
                PointerEventType.Press -> look.press(true)
                // By the pointers, not [PointerEvent.buttons], which a release may still report pressed
                PointerEventType.Release -> if (event.changes.none { it.pressed }) look.press(false)
            }
        }
    }
}

/** WEB-117: the quick look's largest height (dp), as the table's (WEB-026). */
private const val POD_QUICK_LOOK_HEIGHT = 420f

/**
 * WEB-117: [copy]'s quick look while [look] shows it, beside the card it is composed in (to its right where it fits, else
 * to its left; level with it, inside the window): every face of the card as Inspect shows them (TBL-104), large, with no
 * dimming and nothing to press.
 */
@Composable
fun PodQuickLookPopup(look: PodQuickLook?, copy: DraftCopy, definition: CardDefinition?) {
    if (look == null || look.shown != copy) return
    val card = remember(copy.definition, definition) { definition?.takeIf { it.faces.isNotEmpty() }?.let { InspectedCard.retained(it) } }
    val tiles = remember(card) { inspectTiles(card, definition?.shownName ?: cardDataUnavailable("PodQuickLook", copy.definition)) }
    if (tiles.isEmpty()) return
    val margin = with(LocalDensity.current) { 12.dp.roundToPx() }
    val position = remember(margin) { PodQuickLookPosition(margin) }
    Popup(position, properties = PopupProperties(focusable = false)) {
        BoxWithConstraints {
            val height = minOf(POD_QUICK_LOOK_HEIGHT.dp, maxHeight - 16.dp).coerceAtLeast(120.dp)
            val room = DpSize(height / 1.4f, height)
            Row(Modifier.testTag(QUICK_LOOK_TAG).semantics { contentDescription = "Quick look" },
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (tile in tiles) Box { InspectTileView(tile, room) }
            }
        }
    }
}

/** Beside the anchor card: to its right where it fits, else to its left, else against the window's edge; level with it. */
private class PodQuickLookPosition(private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
        popupContentSize: IntSize): IntOffset {
        val width = popupContentSize.width
        val x = when {
            anchorBounds.right + margin + width <= windowSize.width - margin -> anchorBounds.right + margin
            anchorBounds.left - margin - width >= margin -> anchorBounds.left - margin - width
            else -> (windowSize.width - width - margin).coerceAtLeast(margin)
        }
        val y = (anchorBounds.center.y - popupContentSize.height / 2)
            .coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin))
        return IntOffset(x, y)
    }
}
