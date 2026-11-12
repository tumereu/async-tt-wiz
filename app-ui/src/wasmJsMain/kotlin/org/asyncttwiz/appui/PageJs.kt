@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.appui

// The browser APIs the platform pieces use (web client step 3b), as small JavaScript functions: timers and frames, clocks,
// the console, secure random bytes, the local time zone's calendar, identity numbers and the page's error events.

internal fun jsSetTimeout(millis: Double, action: () -> Unit): Int = js("setTimeout(() => action(), millis)")
internal fun jsClearTimeout(id: Int): Unit = js("clearTimeout(id)")
internal fun jsRequestFrame(action: () -> Unit): Unit =
    js("{ if (typeof requestAnimationFrame === 'function') requestAnimationFrame(() => action()); else setTimeout(() => action(), 16); }")

internal fun jsDateNow(): Double = js("Date.now()")
internal fun jsPerformanceNow(): Double = js("(typeof performance !== 'undefined') ? performance.now() : Date.now()")

internal fun jsUserAgent(): String = js("(typeof navigator !== 'undefined' && navigator.userAgent) ? navigator.userAgent : 'unknown'")
internal fun jsPlatform(): String = js("""(() => {
    if (typeof navigator === 'undefined') return 'unknown';
    if (navigator.userAgentData && navigator.userAgentData.platform) return navigator.userAgentData.platform;
    return navigator.platform || 'unknown';
})()""")

internal fun jsConsoleError(text: String): Unit = js("console.error(text)")
internal fun jsConsoleWarn(text: String): Unit = js("console.warn(text)")
internal fun jsConsoleInfo(text: String): Unit = js("console.info(text)")

/** [size] secure random bytes as a Uint8Array (crypto.getRandomValues). */
internal fun jsRandomBytes(size: Int): JsAny = js("crypto.getRandomValues(new Uint8Array(size))")
internal fun jsByteAt(bytes: JsAny, index: Int): Int = js("bytes[index]")

/** The local time zone's calendar fields of [millis] after 1970: [year, month 1-12, day, hour, minute, second,
 * day of week 1 (Monday) - 7, offset minutes east of UTC] joined by commas. */
internal fun jsLocalFields(millis: Double): String = js("""(() => {
    const d = new Date(millis);
    return [d.getFullYear(), d.getMonth() + 1, d.getDate(), d.getHours(), d.getMinutes(), d.getSeconds(),
        d.getDay() === 0 ? 7 : d.getDay(), -d.getTimezoneOffset()].join(',');
})()""")

/** Milliseconds after 1970 of a local date and time in this browser's time zone. */
internal fun jsLocalMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Double =
    js("new Date(year, month - 1, day, hour, minute, second).getTime()")

/** This browser's time zone's name (as Java's ZoneId names it), "" when unknown. */
internal fun jsTimeZone(): String = js("""(() => {
    try { return Intl.DateTimeFormat().resolvedOptions().timeZone || ''; } catch (e) { return ''; }
})()""")

/** A number for [value] that no other live object has, kept as long as it lives (a WeakMap). */
internal fun jsIdentity(value: JsAny): Int = js("""(() => {
    const g = globalThis;
    if (!g.__atwIdentities) { g.__atwIdentities = new WeakMap(); g.__atwNextIdentity = 1; }
    let id = g.__atwIdentities.get(value);
    if (id === undefined) { id = g.__atwNextIdentity++; g.__atwIdentities.set(value, id); }
    return id;
})()""")

/** Calls [failed] with a description of each error nothing caught (window error and unhandledrejection events). */
internal fun jsOnUncaught(failed: (String) -> Unit): Unit = js("""{
    if (typeof window !== 'undefined') {
        window.addEventListener('error', (event) => {
            const e = event.error;
            failed((e && e.stack) ? String(e.stack) : String(event.message) + ' at ' + event.filename + ':' + event.lineno);
        });
        window.addEventListener('unhandledrejection', (event) => {
            const e = event.reason;
            failed('Unhandled rejection: ' + ((e && e.stack) ? String(e.stack) : String(e)));
        });
    }
}""")

/** Calls [hidden] each time the page goes to the background (the tab is hidden). */
internal fun jsOnHidden(hidden: () -> Unit): Unit = js("""{
    if (typeof document !== 'undefined') document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'hidden') hidden();
    });
}""")
