package org.asyncttwiz.android

/** Retained by AuthViewModel, not a screen effect. Rotation must neither cancel reconnect
 * maintenance nor own the socket; only sign-out/session disposal ends that ownership. */
class GameConnectionMaintenance(private val active: () -> Boolean, private val sync: () -> Unit,
    private val delayed: MainDelayed = mainLooperDelayed, private val quickTick: () -> Unit = {}) : AutoCloseable {
    private var closed = false
    private var ticks = 0
    private var cancel: () -> Unit = {}
    private fun tick() {
        if (closed) return
        if (active()) { quickTick(); if (++ticks % 5 == 0) sync() }
        cancel = delayed(1_000, ::tick)
    }
    init { cancel = delayed(1_000, ::tick) }
    override fun close() { closed = true; cancel() }
}
