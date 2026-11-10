package org.asyncttwiz.e2e

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The e2e harness itself (no server): the phones' composition, its coroutines and its frames run on the main thread
 * only, as on a phone. A composition coroutine that comes back from other work (`withContext(Dispatchers.IO)` in a
 * `produceState`, a flow collected from a worker's updates) resumes on the main thread, so the state it writes is applied,
 * recomposed, measured and laid out there; never on that worker while the main thread measures the other phone's window
 * ("performMeasureAndLayout called during measure layout", found 2026-11-09/10 in four e2e tests).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class ComposeThreadsTest {
    @get:Rule val compose = phonesComposeRule()

    @Test fun compositionCoroutinesAndFramesStayOnTheMainThread() {
        val main = Thread.currentThread()
        check(Looper.getMainLooper().thread === main)
        val elsewhere = ConcurrentLinkedQueue<String>()
        fun onMain(what: String) { if (Thread.currentThread() !== main) elsewhere += "$what on ${Thread.currentThread().name}" }
        val rounds = AtomicInteger()
        // Two phones, as in every e2e test: each writes state each time its work on another thread comes back
        repeat(2) { phone ->
            Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
                var count by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) {
                    repeat(ROUNDS) {
                        withContext(Dispatchers.Default) { Thread.sleep(2) }
                        onMain("phone $phone's coroutine resumed")
                        count++
                        rounds.incrementAndGet()
                    }
                }
                onMain("phone $phone composed")
                Text("Phone $phone: $count", Modifier.layout { measurable, constraints ->
                    onMain("phone $phone measured")
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                })
            }
        }
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (rounds.get() < 2 * ROUNDS && System.nanoTime() < end) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            compose.waitForIdle()
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1))
        }
        assertEquals(emptyList<String>(), elsewhere.toList().distinct())
        assertTrue("both phones' work came back every time (${rounds.get()})", rounds.get() == 2 * ROUNDS)
    }

    private companion object {
        const val ROUNDS = 100
    }
}
