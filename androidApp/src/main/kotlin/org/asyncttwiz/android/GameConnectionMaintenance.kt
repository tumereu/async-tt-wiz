package org.asyncttwiz.android

import android.os.Handler
import android.os.Looper

/** Retained by AuthViewModel, not a screen effect. Rotation must neither cancel reconnect
 * maintenance nor own the socket; only sign-out/session disposal ends that ownership. */
internal class GameConnectionMaintenance(private val active: () -> Boolean, private val sync: () -> Unit,
    private val handler: Handler = Handler(Looper.getMainLooper())) : AutoCloseable {
    private var closed = false
    private val tick = object : Runnable {
        override fun run() {
            if (closed) return
            if (active()) sync()
            handler.postDelayed(this, 5_000)
        }
    }
    init { handler.postDelayed(tick, 5_000) }
    override fun close() { closed = true; handler.removeCallbacks(tick) }
}
