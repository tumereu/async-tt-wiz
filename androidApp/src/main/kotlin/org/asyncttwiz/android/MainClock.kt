package org.asyncttwiz.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.asyncttwiz.appui.postToMainDelayed

/**
 * Runs the work after the milliseconds on the main thread, by its clock, and gives what cancels it. The app's periodic
 * re-reads and ticks run on it, owned by their controllers, not as delay loops in composition: a composition's frame clock
 * stands still in the e2e harness while nothing on the screen animates, so such a loop stalls on a still screen there.
 */
typealias MainDelayed = (Long, () -> Unit) -> () -> Unit

/** [MainDelayed] on the main looper's handler. */
internal val mainLooperDelayed: MainDelayed = { millis, work -> postToMainDelayed(millis, work) }

/** [work] every [everyMillis] on [delayed]'s clock, the first one interval from now, until the returned closer is called. */
internal fun repeatEvery(everyMillis: Long, delayed: MainDelayed, work: () -> Unit): () -> Unit {
    var active = true
    var cancel: () -> Unit = {}
    fun next() { cancel = delayed(everyMillis) { if (active) { work(); if (active) next() } } }
    next()
    return { if (active) { active = false; cancel() } }
}

/**
 * Runs [start] while this is composed and the app is in front (resumed), and the closer it gives when the app goes to the
 * background or this leaves composition; again on each return to the front. A change of [keys] closes and starts it again.
 */
@Composable
internal fun WhileInFront(vararg keys: Any?, start: () -> () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(*keys, owner) {
        var stop: (() -> Unit)? = null
        val observer = LifecycleEventObserver { _, _ ->
            val front = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (front && stop == null) stop = start()
            else if (!front) { stop?.invoke(); stop = null }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); stop?.invoke(); stop = null }
    }
}
