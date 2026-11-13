@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

// The table in the browser (docs/specs/web-client.md §8.5, §8.7; WEB-011): the browser's own gestures kept off the table
// and the draft (page scroll and bounce, pull-to-refresh, pinch and double-tap zoom, text selection, long-press callouts
// and the context menu), so the table's own pan, pinch, hold and double tap get every touch, mouse and wheel event; and
// haptics through navigator.vibrate where the browser has it.

/**
 * Marks the page as showing the table or the draft ([on]) or another screen: index.html's `atw-table` styles turn the
 * browser's touch handling and selection off on it, and [jsGuardTablePage]'s listeners take its zoom and menus.
 */
internal fun jsTablePage(on: Boolean): Unit = js("document.documentElement.classList.toggle('atw-table', on)")

/**
 * Installs, once, the page's listeners that keep the browser's zoom and menus off the table and the draft (while
 * `atw-table` is set): the context menu (a long press on a phone, a right-click), Safari's pinch gestures, a trackpad's
 * pinch or Ctrl+wheel and Ctrl with +, - or 0 (page zoom), and a second finger's or a double tap's default (zoom).
 * Compose still gets every event: only the browser's default actions are cancelled.
 */
internal fun jsGuardTablePage(): Unit = js("""{
    const on = () => document.documentElement.classList.contains('atw-table');
    const stop = (event) => { if (on() && event.cancelable) event.preventDefault(); };
    window.addEventListener('contextmenu', stop, { capture: true });
    for (const kind of ['gesturestart', 'gesturechange', 'gestureend']) document.addEventListener(kind, stop, { capture: true, passive: false });
    window.addEventListener('wheel', (event) => { if (event.ctrlKey) stop(event); }, { capture: true, passive: false });
    window.addEventListener('touchmove', (event) => { if (event.touches.length > 1) stop(event); }, { capture: true, passive: false });
    window.addEventListener('dblclick', stop, { capture: true });
    // The chat's text field (Compose's hidden input while typing) keeps its own selection
    window.addEventListener('selectstart', (event) => {
        const target = event.target;
        if (!(target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA'))) stop(event);
    }, { capture: true });
    window.addEventListener('keydown', (event) => {
        if ((event.ctrlKey || event.metaKey) && ['+', '=', '-', '_', '0'].includes(event.key)) stop(event);
    }, { capture: true });
}""")

/**
 * The shared UI's haptic ticks in the browser (§8.7): a short vibration where the browser offers it (Android's Chrome), and
 * nothing where it doesn't (iOS, desktops) or before the page has had a tap (browsers refuse it then).
 */
internal object BrowserHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        jsVibrate(if (hapticFeedbackType == HapticFeedbackType.LongPress) 30 else 12)
    }
}

private fun jsVibrate(millis: Int): Unit = js("""{
    try {
        const active = !navigator.userActivation || navigator.userActivation.hasBeenActive;
        if (active && typeof navigator.vibrate === 'function') navigator.vibrate(millis);
    } catch (e) {}
}""")

/**
 * The desktop layout's keyboard (docs/specs/web-client.md §5, §6; WEB-024): every key press and release on the page goes to
 * [key] (its name, down or up, time, a held key's repeat, the modifiers, whether a text field has the keyboard), which says
 * whether the app took it; a taken Alt keeps the browser's own Alt handling (its menu bar) off. When the window loses the
 * focus every key still down is let go, so a held finger key never sticks.
 */
internal fun jsListenKeys(key: (String, Boolean, Double, Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Boolean): Unit = js("""{
    const typing = () => {
        const element = document.activeElement;
        return !!element && (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA' || element.isContentEditable === true);
    };
    const held = new Set();
    const send = (event, down) => {
        if (typeof event.key !== 'string') return;
        if (down) held.add(event.key); else held.delete(event.key);
        const taken = key(event.key, down, event.timeStamp, event.repeat === true, event.altKey, event.shiftKey, event.ctrlKey,
            event.metaKey, typing());
        if (taken && event.cancelable && event.key === 'Alt') event.preventDefault();
    };
    window.addEventListener('keydown', (event) => send(event, true), true);
    window.addEventListener('keyup', (event) => send(event, false), true);
    window.addEventListener('blur', () => {
        for (const name of Array.from(held)) key(name, false, performance.now(), false, false, false, false, false, false);
        held.clear();
    });
}""")
