@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

/**
 * WEB-057: the frame times of a game's table and the draft in the app log, so a problem report says how smoothly they drew
 * and what was on screen when they didn't. While one of them shows (the web shell's [sessionShown]) and the tab is visible,
 * the browser's animation frames are timed ([jsFrameClock]) into a [FrameTimeLog], labelled with what the screens report
 * ([state], [animation], [action]); recorded whether or not reports are on, since it only writes the app log.
 * Stutters aren't sent apart as on Android (JankStats): the browser has no matching frame timing.
 */
actual object DebugJank {
    private const val TAG = "frames"
    private val log = FrameTimeLog({ AppLog.info(TAG, it) })
    private val tags = linkedMapOf("table_mode" to "play", "popup" to "none", "dragging" to "false", "visible_cards" to "0")
    private val animations = linkedSetOf<String>()
    private val catchUps = mutableSetOf<String>()
    private var lastAction = "none"
    private var changed = true
    private var group = ""
    private var labels = ""
    private var clock: JsAny? = null

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

    /** A game's table or the draft ([screen]) shows: its frames are timed from now. */
    internal fun sessionShown(screen: String) {
        log.begin(screen, jsDisplayFacts())
        val frames = clock ?: jsFrameClock(::frame).also { clock = it }
        jsFrameClockRun(frames, true)
    }

    /** No table or draft shows any more: the session's summary goes to the log. */
    internal fun sessionHidden(reason: String) {
        clock?.let { jsFrameClockRun(it, false) }
        log.end(reason)
    }

    private fun frame(intervalMs: Double) {
        if (changed) {
            changed = false
            val parts = ArrayList<String>(3)
            if (animations.isNotEmpty()) parts += animations.joinToString("+") + " animating"
            if (tags["dragging"] == "true") parts += "dragging"
            if (catchUps.isNotEmpty()) parts += "catching up"
            group = if (parts.isEmpty()) "nothing animating" else parts.joinToString(", ")
            labels = "anim=" + (animations.joinToString("+").ifEmpty { "none" }) + " " +
                tags.entries.joinToString(" ") { (key, value) -> "$key=$value" } + " catch_up=${catchUps.isNotEmpty()} action=$lastAction"
        }
        log.frame(intervalMs, group, labels)
    }
}

/**
 * The browser's frame clock: while running, every animation frame calls [frame] with the time since the one before (the
 * frames' own timestamps, so it measures the drawn rate whatever drew it). A hidden tab draws no frames; the first one after
 * it shows again starts afresh, so the time hidden never counts as a frame.
 */
private fun jsFrameClock(frame: (Double) -> Unit): JsAny = js("""(() => {
    const clock = { on: false, last: -1, id: 0 };
    const tick = (time) => {
        if (!clock.on) return;
        if (clock.last >= 0) frame(time - clock.last);
        clock.last = time;
        clock.id = requestAnimationFrame(tick);
    };
    clock.run = (on) => {
        if (on === clock.on) return;
        clock.on = on;
        clock.last = -1;
        if (on) clock.id = requestAnimationFrame(tick); else cancelAnimationFrame(clock.id);
    };
    document.addEventListener('visibilitychange', () => { clock.last = -1; });
    return clock;
})()""")

private fun jsFrameClockRun(clock: JsAny, on: Boolean): Unit = js("clock.run(on)")

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
