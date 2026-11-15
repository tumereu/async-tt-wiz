@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

/**
 * WEB-057: the frame times of a game's table and the draft in the app log, so a problem report says how smoothly they drew
 * and what was on screen when they didn't. While one of them shows (the web shell's [sessionShown]) and the tab is visible,
 * every display refresh of the browser is timed ([jsFrameMeter]) with what the page did in it: whether it drew and how long
 * its drawing took, and its other work seen meanwhile (the engine worker's messages, input handling). [DrawnFrames] makes
 * frames of them (a page with nothing to draw adds none: owner, 2026-11-15, the idle seconds counted as slow frames) for
 * a [FrameTimeLog], labelled with what the screens report ([state], [animation], [action]); recorded whether or not reports
 * are on, since it only writes the app log. Stutters aren't sent apart as on Android (JankStats).
 */
actual object DebugJank {
    private const val TAG = "frames"
    private val log = FrameTimeLog({ AppLog.info(TAG, it) })
    private val frames = DrawnFrames(log)
    private val tags = linkedMapOf("table_mode" to "play", "popup" to "none", "dragging" to "false", "visible_cards" to "0")
    private val animations = linkedSetOf<String>()
    private val catchUps = mutableSetOf<String>()
    private var lastAction = "none"
    private var changed = true
    private var group = ""
    private var labels = ""
    private var moving = false
    private val meter: JsAny by lazy { jsFrameMeter(::refresh, frames::reset) }

    actual val labelling: Boolean get() = true

    actual fun catchUp(game: String, running: Boolean) {
        val before = catchUps.isNotEmpty()
        if (running) catchUps.add(game) else catchUps.remove(game)
        if (before != catchUps.isNotEmpty()) changed = true
    }
    actual fun catchingUp(): Boolean = Diagnostics.enabled && catchUps.isNotEmpty()
    actual fun treeInFlight(): Boolean = false
    actual fun animation(owner: String, running: Boolean) {
        if (if (running) animations.add(owner.take(40)) else animations.remove(owner.take(40))) changed = true
    }
    actual fun state(vararg values: Pair<String, String>) {
        for ((key, value) in values) {
            val text = value.take(60)
            if (tags[key] == text) continue
            tags[key] = text
            changed = true
        }
    }
    actual fun action(name: String) {
        val text = name.take(60)
        if (text != lastAction) { lastAction = text; changed = true }
    }
    actual fun beginWork(kind: String): Long = 0
    actual fun endWork(kind: String, start: Long) {}
    actual fun track(owner: Any, tracking: JankTracking) {}
    actual fun untrack(owner: Any) {}
    actual fun frame(startNanos: Long, durationNanos: Long, isJank: Boolean, refreshRate: Float, states: Map<String, String>) {}
    actual fun authenticated(value: String?) {}
    actual fun discard() {}
    actual fun flushFrames() = log.flush()

    /** The page starts: the refreshes' drawing is timed from its first frame on (the meter wraps the browser's frame requests,
     * which Compose asks its frames with), whether or not a table shows yet. */
    internal fun install() { meter }

    /** A game's table or the draft ([screen]) shows: its frames are timed from now. */
    internal fun sessionShown(screen: String) {
        log.begin(screen, jsDisplayFacts())
        frames.reset()
        jsFrameMeterRun(meter, true)
    }

    /** No table or draft shows any more: the session's summary goes to the log. */
    internal fun sessionHidden(reason: String) {
        jsFrameMeterRun(meter, false)
        log.end(reason)
    }

    private fun refresh(atMs: Double, intervalMs: Double, drawMs: Double, otherMs: Double) {
        if (changed) {
            changed = false
            val parts = ArrayList<String>(3)
            if (animations.isNotEmpty()) parts += animations.joinToString("+") + " animating"
            if (tags["dragging"] == "true") parts += "dragging"
            if (catchUps.isNotEmpty()) parts += "catching up"
            moving = animations.isNotEmpty() || tags["dragging"] == "true"
            group = if (parts.isEmpty()) "nothing animating" else parts.joinToString(", ")
            labels = "anim=" + (animations.joinToString("+").ifEmpty { "none" }) + " " +
                tags.entries.joinToString(" ") { (key, value) -> "$key=$value" } + " catch_up=${catchUps.isNotEmpty()} action=$lastAction"
        }
        frames.refresh(atMs, intervalMs, drawMs, otherMs, moving, group, labels)
    }
}

/**
 * The browser's display refreshes, timed: installed once, it wraps `requestAnimationFrame` (Compose and the page ask their
 * frames with it) to time each frame callback, and while running it has a frame of its own at every refresh, which calls
 * [refresh] for the refresh before it: its start, its length (to this one), how long the page's frame callbacks took in it
 * (negative: none ran, the page drew nothing) and the page's other work seen during it (`atwFrameMeter.other`, added to
 * by the engine worker's messages, and input handling as the browser's event timing reports it). A hidden tab (Firefox still
 * runs its frames, every second or more) is no refresh: [reset] is called and the next visible one starts afresh.
 */
private fun jsFrameMeter(refresh: (Double, Double, Double, Double) -> Unit, reset: () -> Unit): JsAny = js("""(() => {
    const now = () => performance.now();
    const meter = { on: false, last: -1, a: -1, aMs: 0, b: -1, bMs: 0, other: 0, id: 0 };
    globalThis.atwFrameMeter = meter;
    const request = window.requestAnimationFrame.bind(window);
    // For frame callbacks that draw nothing (the hover cursor's, WEB-058): they don't make a refresh a drawn one
    globalThis.atwFrameRequest = request;
    window.requestAnimationFrame = (callback) => request((time) => {
        if (!meter.on) return callback(time);
        const start = now();
        try { return callback(time); } finally {
            const took = now() - start;
            if (meter.a === time) meter.aMs += took;
            else if (meter.b === time) meter.bMs += took;
            else if (meter.a < meter.b) { meter.a = time; meter.aMs = took; } else { meter.b = time; meter.bMs = took; }
        }
    });
    const tick = (time) => {
        if (!meter.on) return;
        meter.id = request(tick);
        if (document.visibilityState !== 'visible') { if (meter.last >= 0) reset(); meter.last = -1; meter.other = 0; return; }
        const last = meter.last;
        meter.last = time;
        if (last < 0) { meter.other = 0; return; }
        const drew = meter.a === last ? meter.aMs : meter.b === last ? meter.bMs : -1;
        const other = meter.other;
        meter.other = 0;
        refresh(last, time - last, drew, other);
    };
    meter.run = (on) => {
        if (on === meter.on) return;
        meter.on = on;
        meter.last = -1;
        if (on) meter.id = request(tick); else cancelAnimationFrame(meter.id);
    };
    try {
        new PerformanceObserver((list) => {
            if (!meter.on) return;
            for (const entry of list.getEntries()) if (entry.processingEnd > entry.processingStart) meter.other += entry.processingEnd - entry.processingStart;
        }).observe({ type: 'event', durationThreshold: 16, buffered: false });
    } catch (e) {}
    return meter;
})()""")

private fun jsFrameMeterRun(meter: JsAny, on: Boolean): Unit = js("meter.run(on)")

/** The window and display, and the graphics the browser draws with (WebGL's renderer, as far as the browser tells it). */
private fun jsDisplayFacts(): String = js("""(() => {
    let graphics = 'unknown';
    try {
        if (globalThis.atwGraphics === undefined) {
            const gl = document.createElement('canvas').getContext('webgl2') || document.createElement('canvas').getContext('webgl');
            const info = gl && gl.getExtension('WEBGL_debug_renderer_info');
            globalThis.atwGraphics = gl ? String(gl.getParameter(info ? info.UNMASKED_RENDERER_WEBGL : gl.RENDERER)) : 'no WebGL';
            const lose = gl && gl.getExtension('WEBGL_lose_context');
            if (lose) lose.loseContext();
        }
        graphics = globalThis.atwGraphics;
    } catch (e) {}
    return 'window ' + innerWidth + 'x' + innerHeight + ' at ' + devicePixelRatio + 'x; graphics ' + graphics;
})()""")
