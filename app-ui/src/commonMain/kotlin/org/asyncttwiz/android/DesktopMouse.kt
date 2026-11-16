package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

// The mouse and the finger key on the desktop table (docs/specs/web-client.md §5; WEB-023 to WEB-030). The phone layout's
// touch handling is never changed by any of this: everything here is installed only in the desktop layout.

/** WEB-023: which mouse button a press is: the left button acts as a finger does, the right one does a control's hold
 * action (never Inspect), the middle one pans the table. */
sealed interface MouseButton {
    data object Left : MouseButton
    data object Right : MouseButton
    data object Middle : MouseButton

    companion object {
        /** The button a press with these buttons down is: the left wins over the others (a chord stays a left press). */
        fun of(primary: Boolean, secondary: Boolean, tertiary: Boolean): MouseButton? = when {
            primary -> Left
            secondary -> Right
            tertiary -> Middle
            else -> null
        }

        /** The button of [event] (a press of a mouse); null for a touch or a pen, which stay the phone's. */
        fun of(event: PointerEvent): MouseButton? {
            if (event.changes.none { it.type == PointerType.Mouse }) return null
            return of(event.buttons.isPrimaryPressed, event.buttons.isSecondaryPressed, event.buttons.isTertiaryPressed)
        }
    }
}

/**
 * A key press or release on the desktop layout (the web page's keyboard events): [key] as the browser names it ("Alt",
 * "Shift", "a", " "), [time] the event's time (ms, comparable only with other keys'), [repeat] a held key's repeats,
 * [textField] whether a text field has the focus (hotkeys never act then, §6). [lost]: a release the page never sent, made
 * by [DesktopKeyboard.letGo] because the key can't be down any more, and why (the window lost the focus, a text field took
 * the keyboard, a later event said the key isn't down); null for the page's own key-up.
 */
data class DesktopKey(val key: String, val down: Boolean, val time: Long, val repeat: Boolean = false, val alt: Boolean = false,
    val shift: Boolean = false, val ctrl: Boolean = false, val meta: Boolean = false, val textField: Boolean = false,
    val lost: String? = null)

/**
 * The desktop layout's keyboard (§6): the page hands every key press and release here ([dispatch], the web platform's
 * window listeners), and the screens listening ([Listen]) take them, the last listening first. Step 5c's hotkeys listen
 * here too. Nothing dispatches on Android. Main thread.
 *
 * WEB-031 (owner, 2026-11-14): while a text field has the keyboard ([DesktopKey.textField]) every key press goes only to it,
 * as typing: no listener hears it (but one waiting for a key to bind, `whileTyping`), so no hotkey, "?" included, ever acts
 * on a typed key. Releases still reach every listener (each of them, whichever takes it), so a key held before the field
 * took the keyboard is let go.
 *
 * WEB-025 (prod report 96PB2X, 2026-11-16: Alt's key-up never came and the table stayed paused): a key the page said went
 * down is let go ([letGo]) when its key-up can't come any more: the window loses the focus or the page is hidden
 * ([focusLost]), a text field takes the keyboard (the finger keys, Alt and Shift), or a later key or pointer event says the
 * modifier isn't held ([modifiers]). Every listener hears such a release, with its reason ([DesktopKey.lost]).
 */
object DesktopKeyboard {
    private class Entry(val whileTyping: Boolean, val listener: (DesktopKey) -> Boolean)
    private val listeners = mutableListOf<Entry>()
    private val focusListeners = mutableListOf<(String) -> Unit>()
    private val down = LinkedHashSet<String>()
    /** The keys down that were pressed while a text field had the keyboard (typing, not hotkeys or the finger). */
    private val typedDown = HashSet<String>()
    private var lastTime = 0L

    /** The keys the page said are down now (pressed, with no key-up or [letGo] since), oldest first. */
    val keysDown: Set<String> get() = down.toSet()

    /** Hands [event] to the listeners, the newest first, until one takes a press; a release reaches every listener. Whether
     * one took it (the page then keeps the browser's own handling of the key off). A press while a text field has the
     * keyboard reaches only `whileTyping` ones. */
    fun dispatch(event: DesktopKey): Boolean {
        lastTime = event.time
        if (event.lost == null) staleModifiers(event.key, event.alt, event.shift, event.ctrl, event.meta,
            // A typed character is never named (the field may be a password's)
            "${if (event.key.length > 1) event.key else "a key"} ${if (event.down) "pressed" else "let go"}")
        val typing = event.down && event.textField
        if (typing) {
            heldBack(event)
            // A finger key held from before the field had the keyboard (one pressed while typing, Shift for a capital, never
            // was the finger)
            val fingers = down.filterTo(HashSet()) { it in FINGER_KEYS && it != event.key && it !in typedDown }
            if (fingers.isNotEmpty()) letGo("a text field has the keyboard", fingers)
        } else if (event.down) typingNoted = null
        if (event.down) down += event.key else down -= event.key
        if (typing) typedDown += event.key else typedDown -= event.key
        return hand(event, typing)
    }

    private fun hand(event: DesktopKey, typing: Boolean): Boolean {
        val heard = listeners.asReversed().toList()
        if (!event.down) return heard.map { it.listener(event) }.any { it }
        return heard.any { (!typing || it.whileTyping) && it.listener(event) }
    }

    /**
     * Lets go of every key down ([keysDown]; only those in [only] when given) whose key-up the page can no longer send
     * ([why]): every listener hears its release, with [DesktopKey.lost] set. Logged with what was let go.
     */
    fun letGo(why: String, only: Set<String>? = null) {
        val keys = down.filter { only == null || it in only }
        if (keys.isEmpty()) return
        AppLog.info("keys", "Keys let go without their key-up ($why): ${keys.joinToString()}")
        for (key in keys) {
            down -= key
            typedDown -= key
            hand(DesktopKey(key, false, lastTime, lost = why), false)
        }
    }

    /** The window lost the focus or the page was hidden ([why]): every key down is let go ([letGo]), and whatever else is
     * held only while the page has the input (the left button held on the table) hears it ([OnFocusLost]). */
    fun focusLost(why: String) {
        letGo(why)
        focusListeners.toList().forEach { it(why) }
    }

    /** A text field took the keyboard (the focus moved into one): the finger keys down are let go. */
    fun textFieldTookKeyboard() = letGo("a text field took the keyboard", FINGER_KEYS)

    /** A pointer event (or any other event that carries the modifiers) with these modifiers held ([what] names it in the
     * log, "a click"): a modifier the page said is down that isn't any more is let go. */
    fun modifiers(alt: Boolean, shift: Boolean, ctrl: Boolean, meta: Boolean, what: String) =
        staleModifiers(null, alt, shift, ctrl, meta, what)

    private fun staleModifiers(key: String?, alt: Boolean, shift: Boolean, ctrl: Boolean, meta: Boolean, what: String) {
        if (down.isEmpty()) return
        val stale = down.filterTo(HashSet()) { held -> held != key && when (held) {
            "Alt" -> !alt
            "Shift" -> !shift
            "Control" -> !ctrl
            "Meta" -> !meta
            else -> false
        } }
        if (stale.isNotEmpty()) letGo("$what without it: its key-up was lost", stale)
    }

    /** Hears [focusLost] (its reason) while composed and [enabled]. */
    @Composable
    fun OnFocusLost(enabled: Boolean, listener: (String) -> Unit) {
        val current by rememberUpdatedState(listener)
        DisposableEffect(enabled) {
            val entry: (String) -> Unit = { current(it) }
            if (enabled) focusListeners += entry
            onDispose { focusListeners -= entry }
        }
    }

    /** The finger keys (WEB-024), let go when a text field takes the keyboard. */
    private val FINGER_KEYS = setOf("Alt", "Shift")

    /** WEB-045 (owner playtest 2026-11-16): whether Shift is held now, as the keys down say (a Shift+click on an edge zone
     * moves every copy); a lost Shift key-up is let go as every finger key is ([letGo]). */
    val shiftHeld: Boolean get() = "Shift" in down

    /** Whether anything listens (a desktop table is shown). */
    val listening: Boolean get() = listeners.isNotEmpty()

    /** Listens to the keyboard while composed and [enabled]; [listener] returns whether it took the key. [whileTyping]: it
     * also hears presses while a text field has the keyboard (only for waiting on the key to bind, WEB-035). */
    @Composable
    fun Listen(enabled: Boolean, whileTyping: Boolean = false, listener: (DesktopKey) -> Boolean) {
        val current by rememberUpdatedState(listener)
        DisposableEffect(enabled, whileTyping) {
            val entry = Entry(whileTyping) { current(it) }
            if (enabled) listeners += entry
            onDispose { listeners -= entry }
        }
    }

    /** When the current stretch of typing was last noted in the log (its key's time), null outside one. */
    private var typingNoted: Long? = null
    /** Presses held back from the listeners since that note. */
    private var heldBackSince = 0

    /**
     * Logs, once per stretch of typing and then at most every [TYPING_NOTE_MILLIS], that presses go to the text field and
     * not to the hotkeys (with how many since the last note), so a report of a key "doing nothing" explains itself. A typed
     * character is never named (the field may be a password's); keys with names (Enter, Tab, ←) and Ctrl / ⌘ chords are.
     */
    private fun heldBack(event: DesktopKey) {
        if (listeners.isEmpty() || event.key in KeyChord.MODIFIERS) return
        heldBackSince++
        val noted = typingNoted
        if (noted != null && event.time - noted in 0 until TYPING_NOTE_MILLIS) return
        val name = when {
            event.ctrl || event.meta || event.key.length > 1 -> KeyChord.of(event)?.label ?: "a named key"
            else -> "a typed character"
        }
        AppLog.info("keys", "Typing: $name went to the text field, no hotkey acts while one has the keyboard" +
            if (heldBackSince > 1) " ($heldBackSince keys since the last note)" else "")
        typingNoted = event.time
        heldBackSince = 0
    }

    private const val TYPING_NOTE_MILLIS = 30_000L
}

/**
 * WEB-024, WEB-025 (owner, decision 31): the desktop's finger key, Alt (Shift as the fallback where a browser's Alt menu
 * gets in the way): holding it is the phone's finger down. Over a card it opens Inspect while held, closed on release
 * (owner playtest 2026-11-15: no pinned Inspect by a tap of it any more, "If hold-scrolling is possible I would scrap the
 * tap-alt-to-open-with-close-button version completely"); anywhere it pauses what the view waits on (a play shown, a combat
 * hold, playback) while held. Pure: the table applies the [Effect]s.
 */
class DesktopFingerKey {
    sealed interface Effect {
        /** Open Inspect of [cardId], the pictures with their changes beside them, while the key is held. */
        data class Inspect(val cardId: String) : Effect
        /** Close the Inspect. */
        data object Close : Effect
        /** The finger down: the view waits ([on]) or goes on. */
        data class Pause(val on: Boolean) : Effect
    }

    private var down: String? = null
    private var card: String? = null
    /** The Shift held is part of a chord (Shift+Space): no finger any more until it is released. */
    private var chorded = false

    /** Whether the finger key is held. */
    val held: Boolean get() = down != null

    /** [key] pressed with the pointer over [overCard] (its card id, null elsewhere). */
    fun press(key: String, overCard: String?): List<Effect> {
        if (down != null) return emptyList()
        down = key
        card = overCard
        return listOfNotNull(Effect.Pause(true), overCard?.let { Effect.Inspect(it) })
    }

    /** [key] released: the Inspect it opened closes and the view goes on. */
    fun release(key: String): List<Effect> {
        if (key != down) return emptyList()
        down = null
        if (chorded) { chorded = false; card = null; return emptyList() }
        val opened = card
        card = null
        return listOfNotNull(Effect.Pause(false), Effect.Close.takeIf { opened != null })
    }

    /**
     * WEB-031 (§6): another key pressed while the finger key is held. Alt stays the finger. Shift, the fallback, becomes part
     * of a chord (Shift+Space, Shift+G, "?"): the finger is lifted at once, so the Inspect it opened closes and the view goes
     * on, and its release does nothing more.
     */
    fun chord(): List<Effect> {
        if (down == null) return emptyList()
        if (down != "Shift" || chorded) return emptyList()
        chorded = true
        val opened = card != null
        card = null
        return listOfNotNull(Effect.Close.takeIf { opened }, Effect.Pause(false))
    }
}

/** WEB-026: how long the pointer rests on a card before its quick look shows. */
const val QUICK_LOOK_MILLIS = 250L

/** WEB-027: how long a card's hover chips stay after the pointer leaves it, so it can reach them. */
const val HOVER_CHIPS_KEEP_MILLIS = 180L

/** §5: how long the pointer rests on a control before its tooltip shows. */
const val HOVER_TOOLTIP_MILLIS = 500L

/** WEB-029: the zoom of one wheel notch (§5: zoom around the pointer, within the table's 0.65–2×). */
fun wheelZoom(delta: Float): Float = exp(-delta.coerceIn(-4f, 4f) * .1f)

/** WEB-029: how far (dp) a sideways wheel notch (Shift+wheel, or a trackpad's sideways swipe) pans the table. */
const val WHEEL_PAN = 40f

/** What the mouse hovers on the desktop table whose hover chips show (WEB-027): a hand card or a battlefield card. */
sealed interface HoverTarget {
    val cardId: String
    data class Hand(override val cardId: String) : HoverTarget
    data class Field(override val cardId: String) : HoverTarget
}

/**
 * WEB-027: the card whose chips show faded while hovered on the desktop table ([target]); kept [HOVER_CHIPS_KEEP_MILLIS]
 * after the pointer leaves it, and as long as the pointer is on its chips ([chips]), so they can be reached. [apply] shows
 * a target's chips (null: none). Main thread.
 */
@Stable
class DesktopHover(private val scope: CoroutineScope, private val apply: (HoverTarget?) -> Unit) {
    var target by mutableStateOf<HoverTarget?>(null); private set
    private var overChips = false
    private var clearing: Job? = null

    /** The pointer is over [next] (null: over nothing with chips). */
    fun card(next: HoverTarget?) {
        if (next != null) {
            clearing?.cancel(); clearing = null
            if (next != target) { target = next; apply(next) }
        } else if (target != null && clearing == null) clearing = scope.launch {
            delay(HOVER_CHIPS_KEEP_MILLIS)
            clearing = null
            if (!overChips) clear()
        }
    }

    /** The pointer is on the hovered card's chips ([over]) or has left them. */
    fun chips(over: Boolean) {
        overChips = over
        if (over) { clearing?.cancel(); clearing = null } else card(null)
    }

    /** No hover chips now (a press, the pointer off the table). */
    fun clear() {
        clearing?.cancel(); clearing = null
        overChips = false
        if (target != null) { target = null; apply(null) }
    }
}

/** The desktop table's hover chips while one is drawn; null in the phone layout. */
val LocalDesktopHover = compositionLocalOf<DesktopHover?> { null }

/**
 * WEB-023 to WEB-030: the mouse on the desktop table, beside the table's one pointer arbiter ([TableGestureCoordinator],
 * which gets the left button as a finger: a click taps, a double click double-taps, a drag drags or pans, held still it
 * pauses): what the pointer hovers ([hover], for the quick look, the hover chips and the finger key), the right button
 * kept off the table (it never inspects), the middle button panning, the wheel zooming around the pointer (Shift or
 * sideways: panning). Main thread.
 */
@Stable
class DesktopTableMouse(private val coordinator: TableGestureCoordinator, private val hit: (TablePoint) -> TableItem?,
    private val scope: CoroutineScope) {
    /** Where the pointer is over the table (dp), null when off it. */
    var point: TablePoint? = null; private set
    /** The item under the pointer (a card, a pile), null over the empty table or off it. */
    var hovered by mutableStateOf<TableItem?>(null); private set
    /** WEB-026: the card whose quick look shows, null when none. */
    var quickLook by mutableStateOf<TableItem?>(null); private set
    /** The camera was moved by the wheel or the middle button: the table's clock runs for its settling. */
    var moved: () -> Unit = {}
    /** WEB-027: the hover chips of what the pointer is over. */
    var hoverChips: DesktopHover? = null
    /** The card ids of the hover chips' targets, worked out for each hovered item (null: no chips there). */
    var chipTarget: (TableItem) -> HoverTarget? = { null }
    /** Whether a quick look may show (not while an Inspect or a card's menu shows). */
    var quickLookAllowed: () -> Boolean = { true }
    private var looking: Job? = null
    private var pressed = false

    /** The pointer moved to [at] (null: left the table) with no button down. */
    fun hover(at: TablePoint?) {
        point = at
        val item = at?.let(hit)
        if (item?.id != hovered?.id) {
            hovered = item
            looking?.cancel(); looking = null
            quickLook = null
            val card = item?.takeIf { it.kind == TableItemKind.CARD && !it.covered && it.cardId != null }
            if (card != null && !pressed) looking = scope.launch {
                delay(QUICK_LOOK_MILLIS)
                if (quickLookAllowed()) quickLook = card
            }
        }
        hoverChips?.card(item?.let(chipTarget))
    }

    /** A button went down (any): no quick look while it is. */
    fun press() {
        pressed = true
        looking?.cancel(); looking = null
        quickLook = null
    }

    /** The buttons are all up again at [at]. */
    fun release(at: TablePoint?) {
        pressed = false
        hovered = null
        hover(at)
    }

    /** WEB-026: the quick look goes (an Inspect opened over it). */
    fun dismissLook() { looking?.cancel(); looking = null; quickLook = null }

    /** WEB-029: the wheel turned by [delta] at [at]: zooms around the pointer, or pans sideways with Shift or a sideways turn. */
    fun wheel(delta: Offset, at: TablePoint, shift: Boolean) {
        if (coordinator.pressed) return
        dismissLook()
        val sideways = if (shift) (if (delta.x != 0f) delta.x else delta.y) else delta.x
        if (shift || abs(delta.x) > abs(delta.y)) coordinator.panScreen(sideways * WHEEL_PAN, 0f)
        else if (delta.y != 0f) coordinator.zoomAt(at, wheelZoom(delta.y))
        moved()
    }

    /** WEB-029: the middle button dragged by [dx], [dy] dp: the table moves with the pointer. */
    fun drag(dx: Float, dy: Float) { coordinator.panScreen(-dx, -dy); moved() }
}

/**
 * WEB-023 to WEB-029: the desktop table's mouse handling for [mouse], ahead of the table's own pointer arbiter (installed
 * only in the desktop layout): hover moves, the right button's presses kept from the table (they'd tap there otherwise),
 * the middle button's drags, the wheel. The left button passes on to the arbiter untouched; touches are left alone.
 */
fun Modifier.desktopTableMouse(mouse: DesktopTableMouse, density: Float): Modifier = pointerInput(mouse) {
    awaitPointerEventScope {
        // The button of the press under way: its moves and release are this layer's for the right and middle ones
        var button: MouseButton? = null
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull() ?: continue
            if (change.type != PointerType.Mouse) continue
            val at = TablePoint(change.position.x / density, change.position.y / density)
            when (event.type) {
                PointerEventType.Press -> {
                    mouse.press()
                    if (button == null) button = MouseButton.of(event)
                    if (button != MouseButton.Left) event.changes.forEach { it.consume() }
                }
                PointerEventType.Release -> {
                    if (button != MouseButton.Left && button != null) event.changes.forEach { it.consume() }
                    if (!event.buttons.isPrimaryPressed && !event.buttons.isSecondaryPressed && !event.buttons.isTertiaryPressed) {
                        button = null
                        mouse.release(at)
                    }
                }
                PointerEventType.Move -> when (button) {
                    null -> mouse.hover(at)
                    MouseButton.Middle -> {
                        val moved = change.position - change.previousPosition
                        mouse.drag(moved.x / density, moved.y / density)
                        event.changes.forEach { it.consume() }
                    }
                    MouseButton.Right -> event.changes.forEach { it.consume() }
                    MouseButton.Left -> Unit
                }
                PointerEventType.Scroll -> {
                    mouse.wheel(change.scrollDelta, at, event.keyboardModifiers.isShiftPressed)
                    event.changes.forEach { it.consume() }
                }
                PointerEventType.Enter -> if (button == null) mouse.hover(at)
                PointerEventType.Exit -> if (button == null) mouse.hover(null)
            }
        }
    }
}

/**
 * §5, WEB-028: a control on the desktop layout: a right-click does [onRight] (its hold action, or its tooltip without one)
 * instead of a tap, and resting the pointer on it [HOVER_TOOLTIP_MILLIS] shows its tooltip ([onHover] true, false once the
 * pointer leaves or presses). Ahead of the control's own gestures, so the right button never taps it. Touches are left alone.
 */
fun Modifier.desktopControl(key: Any?, onRight: () -> Unit, onHover: ((Boolean) -> Unit)? = null): Modifier = pointerInput(key) {
    coroutineScope {
        val scope = this
        var tip: Job? = null
        awaitPointerEventScope {
            var right = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.none { it.type == PointerType.Mouse }) continue
                when (event.type) {
                    PointerEventType.Press -> {
                        tip?.cancel(); onHover?.invoke(false)
                        if (MouseButton.of(event) == MouseButton.Right) { right = true; event.changes.forEach { it.consume() } }
                    }
                    PointerEventType.Release -> if (right) {
                        right = false
                        event.changes.forEach { it.consume() }
                        onRight()
                    }
                    PointerEventType.Enter -> if (onHover != null) { tip?.cancel(); tip = scope.launch { delay(HOVER_TOOLTIP_MILLIS); onHover(true) } }
                    PointerEventType.Exit -> { tip?.cancel(); onHover?.invoke(false) }
                    else -> if (right) event.changes.forEach { it.consume() }
                }
            }
        }
    }
}

/** WEB-027: whether the mouse is over this (Enter → true, Exit → false); touches are left alone. */
fun Modifier.mouseHover(key: Any?, over: (Boolean) -> Unit): Modifier = pointerInput(key) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.none { it.type == PointerType.Mouse }) continue
            when (event.type) {
                PointerEventType.Enter -> over(true)
                PointerEventType.Exit -> over(false)
            }
        }
    }
}

/** The test tag of the quick look (WEB-026). */
const val QUICK_LOOK_TAG = "quick-look"

/** WEB-026: the quick look's largest height (dp). */
private const val QUICK_LOOK_HEIGHT = 420f

/** WEB-026: the room between a desktop hand card's top and its quick look, which its chips' row takes (dp). */
private const val FAN_QUICK_LOOK_GAP = 72f

/**
 * WEB-026 (owner): the quick look of [item], the card the pointer rests on: its large picture only (each face it may be
 * seen with, as Inspect shows them), beside the card, inside the table [tableWidth] × [tableHeight]; no dimming, nothing
 * to press, gone when the pointer leaves the card.
 */
@Composable
fun DesktopQuickLook(play: TableBoard, viewer: String, item: TableItem, toolkit: TableToolkitController, tableWidth: Float,
    tableHeight: Float) {
    val id = item.cardId ?: return
    val tiles = remember(IdentityKey(play.board), id, viewer) {
        val inspection = toolkit.inspection(id) ?: return@remember emptyList()
        val card = org.asyncttwiz.protocol.CombatStackRules.inspection(play.board, inspection.cardId) ?: return@remember emptyList()
        gameQuickLookTiles(play, viewer, inspection, card)
    }
    if (tiles.isEmpty()) return
    val height = minOf(QUICK_LOOK_HEIGHT, tableHeight - 16f)
    val room = DpSize((height / 1.4f).dp, height.dp)
    val width = tiles.sumOf { inspectTileSize(it, room).width.value.toDouble() }.toFloat() + 8f * (tiles.size - 1)
    val bounds = item.bounds
    val slot = item.handSlot
    // Beside the card: to its right where it fits, else to its left; level with it, inside the table. WEB-015 (owner
    // 2026-11-16: a hovered hand card no longer rises out of the desktop fan): a card of the fan's above it, clear of the fan
    // and of its chips' row (WEB-027), so it covers none of the cards beside it or the chips the mouse goes on to
    val x = if (slot != null) (bounds.center.x - width / 2).coerceIn(8f, (tableWidth - width - 8f).coerceAtLeast(8f))
        else if (bounds.right + 12f + width <= tableWidth - 8f) bounds.right + 12f else (bounds.x - 12f - width).coerceAtLeast(8f)
    val y = if (slot != null) (minOf(slot.y, bounds.y) - FAN_QUICK_LOOK_GAP - height).coerceAtLeast(8f)
        else (bounds.center.y - height / 2).coerceIn(8f, (tableHeight - height - 8f).coerceAtLeast(8f))
    Row(Modifier.offset(x.dp, y.dp).testTag(QUICK_LOOK_TAG).semantics { contentDescription = "Quick look" },
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (tile in tiles) Box { InspectTileView(tile, room) }
    }
}
