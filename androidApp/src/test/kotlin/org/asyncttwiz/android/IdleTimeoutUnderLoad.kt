package org.asyncttwiz.android

import android.os.Handler
import android.os.Looper
import androidx.test.espresso.AppNotIdleException
import androidx.test.espresso.IdlingPolicies
import org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Makes Compose's waits for an idle screen fail ("Compose did not get idle", AppNotIdleException) when the screen keeps
 * working, not when the machine stops the test. Set up before every Robolectric test (registered in META-INF/services).
 *
 * Compose limits each wait by the wall clock (Espresso's master idling policy). A machine stalled at the build slice's
 * memory cap (swapping, load in the hundreds) froze whole test JVMs for minutes, and the wait that was running then failed
 * though its screen was idle: a different test each time, even with a 4-minute limit (NonGameplayScreenshotTest,
 * PodBuildViewTest, QuestionPanelsTest, MultisetDraftScreenshotTest, MatchHistoryScreenshotTest in the merge gates of
 * 2026-11-21 to 2026-11-24; on a quiet machine each of those waits takes under 3 s). A frozen JVM uses no CPU, so the limit
 * that matters here counts CPU time instead: once the test's main thread (where Robolectric runs the test, Compose and
 * its waits) has used [MainThreadCpuBudget.limitNanos] of CPU in one test, the test fails at the main looper's next
 * pass, which every attempt of a wait makes. The wall-clock limit stays only as a backstop, at an hour.
 */
class IdleTimeoutUnderLoad : TestEnvironmentLifecyclePlugin {
    override fun onSetupApplicationState() {
        IdlingPolicies.setMasterPolicyTimeout(WALL_CLOCK_MINUTES, TimeUnit.MINUTES)
        MainThreadCpuBudget.startTest()
    }

    internal companion object {
        const val WALL_CLOCK_MINUTES = 60L
    }
}

/**
 * The main thread's CPU time limit per test (see [IdleTimeoutUnderLoad]). The longest tests use about a minute of wall
 * time on a quiet machine; a screen that never gets idle spends a CPU-second each second, so it fails after about
 * [limitNanos].
 */
internal object MainThreadCpuBudget {
    val DEFAULT_LIMIT_NANOS: Long = TimeUnit.MINUTES.toNanos(4)

    /** Tests of this budget lower it for one test; [startTest] restores the default. */
    @Volatile var limitNanos: Long = DEFAULT_LIMIT_NANOS

    private class Watched(val thread: Thread, val startCpu: Long) {
        @Volatile var failed = false
    }

    @Volatile private var test: Watched? = null
    private val threads = ManagementFactory.getThreadMXBean()

    private val watchdog: Thread by lazy {
        thread(isDaemon = true, name = "main thread CPU budget") {
            while (true) {
                Thread.sleep(250)
                checkBudget()
            }
        }
    }

    /** Called on the test's main thread before each test (its main Looper isn't set up yet then). */
    fun startTest() {
        val main = Thread.currentThread()
        limitNanos = DEFAULT_LIMIT_NANOS
        test = Watched(main, threads.getThreadCpuTime(main.threadId()))
        watchdog
    }

    /** The CPU time the main thread has used in this test so far. */
    fun usedNanos(): Long = test?.let(::used) ?: 0

    private fun used(watched: Watched) = threads.getThreadCpuTime(watched.thread.threadId()) - watched.startCpu

    private fun checkBudget() {
        val watched = test ?: return
        if (watched.failed || used(watched) < limitNanos) return
        watched.failed = true
        val limit = "%.1f s".format(limitNanos / 1e9)
        Handler(Looper.getMainLooper()).post {
            throw AppNotIdleException.create(emptyList(),
                "Compose did not get idle: this test's main thread has used over $limit of CPU time (an effect, " +
                    "animation or layout that changes state on every frame?). The limit counts CPU time, " +
                    "not the wall clock, so a busy machine doesn't trip it (IdleTimeoutUnderLoad).")
        }
    }
}
