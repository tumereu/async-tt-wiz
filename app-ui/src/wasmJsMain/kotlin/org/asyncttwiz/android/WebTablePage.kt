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
 * pinch or Ctrl+wheel and Ctrl with +, - or 0 (page zoom), Alt+wheel and Shift+wheel (history, sideways scroll), and a second
 * finger's or a double tap's default (zoom).
 * Compose still gets every event: only the browser's default actions are cancelled.
 */
internal fun jsGuardTablePage(): Unit = js("""{
    const on = () => document.documentElement.classList.contains('atw-table');
    const stop = (event) => { if (on() && event.cancelable) event.preventDefault(); };
    window.addEventListener('contextmenu', stop, { capture: true });
    for (const kind of ['gesturestart', 'gesturechange', 'gestureend']) document.addEventListener(kind, stop, { capture: true, passive: false });
    // WEB-024 (owner playtest 2026-11-15: Alt with the wheel went back a page in Firefox): the wheel with Alt or Shift held
    // (scrolling a held Inspect) never moves the browser through its history or scrolls the page
    window.addEventListener('wheel', (event) => { if (event.ctrlKey || event.altKey || event.shiftKey) stop(event); }, { capture: true, passive: false });
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
 * [key] (its name, down or up, time, a held key's repeat, the modifiers), which says whether the app took it (and asks
 * [jsTyping] whether a text field has the keyboard); a taken Alt keeps the browser's own Alt handling (its menu bar) off.
 * WEB-025 (prod report 96PB2X, 2026-11-16: Alt's key-up never came, the table stayed paused): a key down is let go when its
 * key-up can't come any more ([lost], the reason): the window loses the focus, the page is hidden, a text field takes the
 * keyboard ([jsTyping] after a focus move; the app lets only the finger keys go then). A pointer event whose modifiers say
 * a modifier the page saw go down isn't held any more goes to [modifiers] (only then: not every move crosses into the app).
 */
internal fun jsListenKeys(key: (String, Boolean, Double, Boolean, Boolean, Boolean, Boolean, Boolean) -> Boolean,
    lost: (String, Boolean) -> Unit, modifiers: (Boolean, Boolean, Boolean, Boolean, String) -> Unit): Unit = js("""{
    // The modifiers seen going down and not up, for the pointer events' check
    const mods = new Set();
    // WEB-031: the keys whose press a hotkey took: their release is the app's too
    const taken = new Set();
    const modifierNames = ['Alt', 'Shift', 'Control', 'Meta'];
    const send = (event, down) => {
        if (typeof event.key !== 'string') return;
        if (modifierNames.includes(event.key)) { if (down) mods.add(event.key); else mods.delete(event.key); }
        const took = key(event.key, down, event.timeStamp, event.repeat === true, event.altKey, event.shiftKey, event.ctrlKey,
            event.metaKey);
        if (took && event.cancelable && event.key === 'Alt') event.preventDefault();
        if (event.key === 'Alt' || event.key === 'Shift') return;
        // WEB-031: a hotkey the app took is the app's only, its press and its release: no browser default (Tab's focus move,
        // Space's scroll) and no second handling by the focused control
        const code = event.code || event.key;
        if (down ? took : taken.has(code)) {
            if (down) taken.add(code); else taken.delete(code);
            if (event.cancelable) event.preventDefault();
            event.stopPropagation();
        }
    };
    window.addEventListener('keydown', (event) => send(event, true), true);
    window.addEventListener('keyup', (event) => send(event, false), true);
    window.addEventListener('blur', () => { mods.clear(); lost('the window lost the focus', true); });
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState !== 'visible') { mods.clear(); lost('the page was hidden', true); }
    });
    // A text field took the keyboard (Compose's input lives in a shadow root: its focus moves come out retargeted)
    window.addEventListener('focusin', () => {
        let element = document.activeElement;
        while (element && element.shadowRoot && element.shadowRoot.activeElement) element = element.shadowRoot.activeElement;
        const typing = !!element && (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA' || element.isContentEditable === true);
        if (typing && (mods.has('Alt') || mods.has('Shift'))) {
            mods.delete('Alt'); mods.delete('Shift');
            lost('a text field took the keyboard', false);
        }
    }, true);
    const pointer = (what) => (event) => {
        if (mods.size === 0) return;
        const flags = { Alt: event.altKey, Shift: event.shiftKey, Control: event.ctrlKey, Meta: event.metaKey };
        let stale = false;
        for (const name of Array.from(mods)) if (!flags[name]) { mods.delete(name); stale = true; }
        if (stale) modifiers(event.altKey, event.shiftKey, event.ctrlKey, event.metaKey, what);
    };
    window.addEventListener('pointerdown', pointer('a pointer press'), true);
    window.addEventListener('pointermove', pointer('a pointer move'), true);
    window.addEventListener('pointerup', pointer('a pointer release'), true);
}""")
