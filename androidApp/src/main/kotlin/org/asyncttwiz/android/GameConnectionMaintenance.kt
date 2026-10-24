package org.asyncttwiz.android

import android.os.Handler
import android.os.Looper

/** Retained by AuthViewModel, not a screen effect. Rotation must neither cancel reconnect
 * maintenance nor own the socket; only sign-out/session disposal ends that ownership. */
internal class GameConnectionMaintenance(private val active: () -> Boolean, private val sync: () -> Unit,
    private val handler: Handler = Handler(Looper.getMainLooper()), private val quickTick: () -> Unit = {}) : AutoCloseable {
    private var closed = false
    private var ticks = 0
    private val tick = object : Runnable {
        override fun run() {
            if (closed) return
            if (active()) { quickTick(); if (++ticks % 5 == 0) sync() }
            handler.postDelayed(this, 1_000)
        }
    }
    init { handler.postDelayed(tick, 1_000) }
    override fun close() { closed = true; handler.removeCallbacks(tick) }
}
