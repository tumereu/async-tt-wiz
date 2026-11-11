package org.asyncttwiz.e2e

import android.os.Looper
import androidx.compose.ui.test.performTouchInput
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.android.AutomatumBoards
import org.asyncttwiz.android.EngineWorker
import org.asyncttwiz.protocol.GameSummary
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import kotlin.test.fail
import org.asyncttwiz.appui.AppPlatform

/**
 * The two phones' Compose test environment (one for both: one recomposer and frame clock, both phones' windows), with
 * the composition on a queuing test dispatcher (Compose's v2 test rule), as a phone's runs on its main thread: a
 * composition coroutine that another thread resumes (the end of a `withContext(Dispatchers.IO)`, a worker's callback)
 * waits for the main thread, where the test runs the composition's work and frames. The older rule's unconfined
 * dispatcher ran it on that worker, frame and layout included, while the main thread measured: "performMeasureAndLayout
 * called during measure layout" (ComposeThreadsTest).
 */
internal fun phonesComposeRule(): androidx.compose.ui.test.junit4.ComposeTestRule =
    androidx.compose.ui.test.junit4.v2.createEmptyComposeRule()

/**
 * The base of every e2e test: two players' phones ([alice], [bob]: the real app, [E2ePlayer]) on the real server
 * ([E2eStack]), a game between them set up as the test chooses ([startGame]: decks, the starting player, each player's
 * play defaults), and [waitUntil] for whatever the test waits for (a server round trip, the other phone's table, a
 * playback): never a fixed sleep, never an elapsed-time assert (owner rule), only a condition and a generous timeout.
 *
 * Every test starts from the same state, whatever ran before, and depends on no other test's leftovers: before each
 * test the server's database goes back to the base dataset (Alice's and Bob's accounts, made once per run:
 * [ServerSetup.fresh]), and each test's phones are new apps (Robolectric's fresh application and storage, new
 * controllers, nothing in memory).
 *
 * When a test fails, each phone's screen (picture, semantics tree, visible texts), its device's record of what it sent
 * and received with the end of its line, every short-lived text it showed, and the app log are written to
 * `e2e/build/e2e/failures/<Test>.<method>/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class E2e {
    private val compose = phonesComposeRule()
    private val players = mutableListOf<E2ePlayer>()
    private val evidence = object : TestWatcher() {
        override fun failed(e: Throwable, description: Description) {
            val dir = File(E2eStack.output, "failures/${description.testClass.simpleName}.${description.methodName}")
            dir.deleteRecursively()
            players.forEach { it.evidence(dir) }
            runCatching { File(dir, "app.log").writeText(AppLog.text()) }
            System.err.println("E2E failure evidence: ${dir.path}")
        }

        // After failed(): the phones stay up until their evidence is taken
        override fun finished(description: Description) {
            // A phone that had to load its game again (it couldn't follow the server) is rare and worth a look even when the
            // test passed: the app log with the device's record goes to e2e/build/e2e/reloads/
            runCatching {
                AppLog.text().takeIf { "before loading again" in it }?.let { log ->
                    File(E2eStack.output, "reloads/${description.testClass.simpleName}.${description.methodName}.log")
                        .apply { parentFile.mkdirs() }.writeText(log)
                }
            }
            players.forEach { runCatching { it.close() } }
            AutomatumBoards.reset()
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(compose).around(evidence)

    internal lateinit var alice: E2ePlayer
    internal lateinit var bob: E2ePlayer
    lateinit var game: GameSummary
    /** The game's deal: each player's opening hand and library. */
    lateinit var deal: Deal
    private lateinit var prepared: ServerSetup.Prepared

    /** The same start for every test: the server's base dataset only, no engine boards of an earlier test's tables, Compose's
     * main dispatcher not waiting for a post the end of an earlier test dropped ([ComposeMainDispatcher]). */
    @Before fun freshStart() {
        ServerSetup.fresh()
        AutomatumBoards.reset()
        org.asyncttwiz.android.MoveSoundRefresh.reset()
        ComposeMainDispatcher.forgetDroppedPost()
    }

    /**
     * Alice and Bob with their phones (the app launched, signed in, on the main menu), each player's play defaults
     * ([aliceSettings], [bobSettings]: response mode, Autotap), and a game between them (Alice invites) with
     * [aliceDeck] and [bobDeck], Bob first when [bobStarts], dealt as [deal] wants (say `{ a, b -> "Mountain" in
     * hand(a) }`: the opening hands and libraries, worked out before the start). [aliceWorker]: where Alice's game does
     * its engine work ([HeldWorker]). With [ready] false the game waits for both players' Ready ([ready]); else it has
     * started.
     */
    internal fun startGame(aliceDeck: TestDeck = TestDeck.thirds(), bobDeck: TestDeck = TestDeck.thirds(), bobStarts: Boolean = false,
        aliceSettings: PlaySettings? = null, bobSettings: PlaySettings? = null, ready: Boolean = true,
        aliceWorker: EngineWorker = EngineWorker.Background,
        /** LOB-113: Alice's account as a new account's is, with nothing saved: the app's own defaults. */
        aliceChoseNothing: Boolean = false,
        /** Alice's phone installs the server's card catalog on start ([E2ePlayer.catalog]), held until let through. */
        aliceCatalog: Boolean = false, holdAliceCatalog: Boolean = false,
        deal: Deal.(TestAccount, TestAccount) -> Boolean = { _, _ -> true }) {
        val (a, b) = ServerSetup.accounts()
        showPlays(a, b)
        if (!aliceChoseNothing) ServerSetup.playDefaults(a, aliceSettings ?: PlaySettings())
        ServerSetup.playDefaults(b, bobSettings ?: PlaySettings())
        prepared = ServerSetup.prepare(a, b, aliceDeck, bobDeck, starter = if (bobStarts) b else a,
            name = "${javaClass.simpleName} ${java.util.UUID.randomUUID().toString().take(6)}", deal = { it.deal(a, b) })
        game = prepared.invitation
        alice = E2ePlayer(a, compose, aliceWorker, withCatalog = aliceCatalog, holdCatalog = holdAliceCatalog).also(players::add)
        bob = E2ePlayer(b, compose).also(players::add)
        alice.launch(); bob.launch()
        if (ready) ready()
    }

    /** Alice and Bob with their phones (the app launched, signed in, on the main menu), and no game set up (say, for a
     * pod: [ServerSetup.draftingPod]). [aliceSets]: the draft sets on Alice's phone (none without card records). */
    /** [aliceCatalog]: Alice's phone installs the server's card catalog on start, as the app does (docs/specs/web-client.md
     * section 3.5); without it the phones have no card records and nothing waits for them. */
    internal fun launchPlayers(aliceSets: org.asyncttwiz.android.PodSetRepository? = null, aliceCatalog: Boolean = false) {
        val (a, b) = ServerSetup.accounts()
        showPlays(a, b)
        alice = E2ePlayer(a, compose, setRepository = aliceSets, withCatalog = aliceCatalog).also(players::add)
        bob = E2ePlayer(b, compose).also(players::add)
        alice.launch(); bob.launch()
    }

    /**
     * HIS-112: the phones show no plays (their "Show plays and combat" time 0), as every flow but the shows' own
     * was written for: a play shown holds a table for a moment and takes the taps on the middle of the board. A test of
     * the shows sets the time itself ([org.asyncttwiz.android.HistoryPlaybackPreferences.showPlaysMillis]).
     */
    private fun showPlays(vararg accounts: TestAccount) {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        for (account in accounts) org.asyncttwiz.android.HistoryPlaybackPreferences(AppPlatform.current, account.id).showPlaysMillis = 0
    }

    /**
     * [player]'s app closes (the process ends: its game says it left) and starts again, signed in on the main menu with
     * nothing of before in memory: opening the game loads it from the server. [engineWorker] for the new app.
     */
    internal fun restart(player: E2ePlayer, engineWorker: EngineWorker = EngineWorker.Background): E2ePlayer {
        player.close()
        players -= player
        return E2ePlayer(player.account, compose, engineWorker).also { players += it; it.launch() }
    }

    /** Both players Ready: the game starts (the server's start revision reaches both phones). */
    fun ready() {
        game = prepared.ready()
        deal = prepared.deal
    }

    /**
     * Both players open the game (Bob first; either order is fine: an opening keep is never assumed, TRN-027,
     * OpeningKeepTest). Each holds the hand the deal worked out and sees the other.
     */
    internal fun bothOpen() {
        for (player in listOf(bob, alice)) {
            player.openGame(game, this)
            val s = player.game!!.shown!!.engine
            val hand = s.player(arcanum.engine.state.PlayerId(player.id)).hand.map { s.obj(it)?.definition?.faces?.first()?.name }
            kotlin.test.assertEquals(deal.hand(player.account).sorted(), hand.sortedBy { it }, "${player.name}'s hand as dealt")
        }
        for (player in listOf(alice, bob)) waitUntil("${player.name} seeing the other player online") {
            player.lobby.state.opponentPresence?.presence?.state == org.asyncttwiz.protocol.PresenceState.ONLINE
        }
        // The tables' next second's tick tells each game the other player is there (the green dot, TRN-042)
        run(skip = Duration.ofMillis(1500))
    }

    /**
     * Lets both phones run (their main thread, the frames and timers on it, their sockets and engine thread) until
     * [condition] holds; fails naming [what] after [timeoutSeconds] with what each screen shows. Meanwhile each player in
     * [along] plays along ([E2ePlayer.playAlong]: keeps, passes, ends their own turn, plays back, resolves).
     */
    internal fun waitUntil(what: String, timeoutSeconds: Long = 60, along: List<E2ePlayer> = emptyList(), condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (true) {
            run()
            if (condition()) return
            along.forEach { it.playAlong() }
            if (System.nanoTime() > end) fail("Timed out waiting for $what.\n" +
                players.joinToString("\n") { "${it.name} (${it.game?.shown?.status}) shows: ${it.screen()}" })
            // Gives the sockets' and the engine's threads a moment before the next round
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2))
        }
    }

    private var ranAt = System.nanoTime()

    /**
     * Runs what is due on the main thread now and moves the phones' clock (timers, animations, frames) on by the real
     * time since the last round (at least a millisecond, at most [MAX_STEP]), so the apps' clock keeps pace with the
     * server's; [skip] moves it on further at once (a minute's grace in a moment).
     */
    fun run(skip: Duration = Duration.ZERO) {
        val now = System.nanoTime()
        val step = Duration.ofNanos(now - ranAt).coerceIn(Duration.ofMillis(1), MAX_STEP)
        ranAt = now
        shadowOf(Looper.getMainLooper()).idleFor(step + skip)
        if (inStep > 0) {
            // [clockInStep]: the frames' clock moves on by the same time, in whole frames, and only by it
            owedMillis += (step + skip).toMillis()
            val frames = owedMillis / FRAME_MILLIS * FRAME_MILLIS
            if (frames > 0) { compose.mainClock.advanceTimeBy(frames); owedMillis -= frames }
        }
        compose.waitForIdle()
        everyRun.forEach { it() }
        players.forEach { it.leaks.install() }
    }

    private var inStep = 0
    private var owedMillis = 0L

    /**
     * While [block] runs the phones' frames' clock (the Compose test clock: animations, effects' delays, a play shown's or a
     * combat's hold, [org.asyncttwiz.android.ViewPause.wait]) moves on only as [run] moves the main thread's: by the real
     * time between rounds (and its skip), never further. Outside it, [run]'s `waitForIdle` advances that clock virtually,
     * frame after frame, until nothing animates: a 3 s hold could end between two rounds, about 100 ms of real time after
     * it began, before the test saw it (CombatHoldTest, 2026-11-11). Tests that watch a hold or a show while it lasts run
     * inside it; its animations then take their real time.
     */
    internal fun <T> clockInStep(block: () -> T): T {
        inStep++
        compose.mainClock.autoAdvance = false
        try { return block() } finally { inStep--; restoreAutoAdvance() }
    }

    /** The frames' clock back to advancing by itself, unless inside [clockInStep]. */
    private fun restoreAutoAdvance() { compose.mainClock.autoAdvance = inStep == 0 }

    /**
     * Whether nothing of the game is on its way between the phones and the server: each phone that is online (its network
     * up, the app in the foreground, the game open) has all its own events on the server, and their lines end at the same
     * event (the other's events have reached it too).
     */
    internal fun quiet(): Boolean {
        val online = players.filter { it.link.up && it.foreground && it.game != null }
        if (online.any { p -> p.engine { device.session.pending.isNotEmpty() } }) return false
        return online.map { p -> p.engine { device.session.head } }.distinct().size <= 1
    }

    /** The game as the server holds it now, read by Alice's account at most every few hundred milliseconds (the
     * server limits how often an address may ask). */
    internal fun serverGame(): org.asyncttwiz.protocol.EngineSnapshot? {
        val now = System.nanoTime()
        if (now - serverReadAt > 300_000_000L || serverRead == null) {
            serverRead = ServerSetup.head(alice.account, game.id).engine
            serverReadAt = now
        }
        return serverRead
    }
    private var serverRead: org.asyncttwiz.protocol.EngineSnapshot? = null
    private var serverReadAt = 0L

    /**
     * A finger held on [node] (TBL-047's hold): down, then [millis] of the phones' time in frame steps, [held] (what the
     * screen shows meanwhile), then up.
     *
     * The phones have two clocks that must agree while a finger is down: the input's (the Compose test clock, which stamps
     * the injected touches and runs the frames) and the main thread's (Robolectric's, which stamps the move Compose
     * resends after a layout under a held finger, and runs the window traversals a popup such as the inspection needs).
     * Before the down the main thread's clock catches up with the input's; during the hold both move on together. Apart, a
     * resent move restarted the hold's timer at an older time (it fired late or never), and a popup's window waited for a
     * traversal that never came (the screen never got idle).
     */
    /** The input's clock moved on to [ahead] ms past the main thread's (see [holding]): a table popup that opened by the main
     * thread's clock lets the next tap through its guard against the opening tap's tail (TBL-067's inline popups). */
    internal fun pastPopupGuard(ahead: Long = 400) {
        val behind = android.os.SystemClock.uptimeMillis() - compose.mainClock.currentTime
        compose.mainClock.advanceTimeBy(maxOf(0L, behind) + ahead)
    }

    internal fun holding(node: androidx.compose.ui.test.SemanticsNodeInteraction, millis: Long = 600,
        moveFirst: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
        at: androidx.compose.ui.test.TouchInjectionScope.() -> androidx.compose.ui.geometry.Offset = { center }, held: () -> Unit) {
        val looper = shadowOf(Looper.getMainLooper())
        val behind = compose.mainClock.currentTime - android.os.SystemClock.uptimeMillis()
        if (behind > 0) looper.idleFor(Duration.ofMillis(behind))
        compose.mainClock.autoAdvance = false
        var fingerDown = false
        try {
            node.performTouchInput {
                down(at())
                fingerDown = true
                // [moveFirst]: the finger moves at once, before the hold's time: a pan or a drag, not a hold
                if (moveFirst != androidx.compose.ui.geometry.Offset.Zero) moveBy(moveFirst)
            }
            passWhileHeld(millis)
            held()
            node.performTouchInput { up() }
            fingerDown = false
        } finally {
            // A look in [held] that failed still lifts the finger: a finger left down keeps the table's gesture recognition
            // asking for a frame every frame, the screens never get idle again, and the failure ended minutes later in an
            // OutOfMemoryError instead of its own message
            if (fingerDown) runCatching { node.performTouchInput { up() } }
            restoreAutoAdvance()
        }
        run()
    }

    /**
     * Problem report QVERHX: while [block] runs the phones' frames (the Compose clock: animations, effects' delays) move on
     * only when [block] asks for one ([FrameByFrame.frame]); the main thread's messages (the server's events, the engine
     * thread's boards) are still handled meanwhile ([FrameByFrame.messages]): what arrives within an animation is drawn at
     * the next frame, before that animation settles, as on a phone whose network answers faster than its animations.
     */
    internal fun <T> frameByFrame(block: FrameByFrame.() -> T): T {
        val looper = shadowOf(Looper.getMainLooper())
        compose.mainClock.autoAdvance = false
        try {
            return object : FrameByFrame {
                override fun frame() { compose.mainClock.advanceTimeByFrame(); looper.idleFor(Duration.ofMillis(FRAME_MILLIS)) }
                override fun messages(what: String, frames: Boolean, condition: () -> Boolean) {
                    val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
                    while (!condition()) {
                        if (frames) frame() else looper.idle()
                        if (System.nanoTime() > end) fail("Timed out (frame by frame) waiting for $what")
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2))
                    }
                }
            }.block()
        } finally { restoreAutoAdvance() }
    }

    /** [frameByFrame]'s controls. */
    internal interface FrameByFrame {
        /** One frame of the phones' clock (and as much of the main thread's). */
        fun frame()
        /** Handles the main thread's messages until [condition] holds, a frame at a time with [frames], else none at all. */
        fun messages(what: String, frames: Boolean, condition: () -> Boolean)
    }

    /** Inside [holding]: [millis] more of the phones' time pass with the finger still down, both clocks moving on together. */
    internal fun passWhileHeld(millis: Long) {
        val looper = shadowOf(Looper.getMainLooper())
        var left = millis
        while (left > 0) {
            val step = minOf(left, FRAME_MILLIS)
            compose.mainClock.advanceTimeBy(step)
            looper.idleFor(Duration.ofMillis(step))
            left -= step
        }
    }

    /**
     * Inside [holding]'s `held`: the held finger moves by [by] (px) over [steps] frames, both clocks moving on together as
     * while it was held still.
     */
    internal fun moveHeldFinger(node: androidx.compose.ui.test.SemanticsNodeInteraction, by: androidx.compose.ui.geometry.Offset,
        steps: Int = 10) {
        val looper = shadowOf(Looper.getMainLooper())
        repeat(steps) {
            node.performTouchInput { moveBy(by / steps.toFloat()) }
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            looper.idleFor(Duration.ofMillis(FRAME_MILLIS))
        }
    }

    /**
     * The input clock (Compose's, which stamps the injected touches) caught up with the main thread's: a panel that opened
     * just now takes no touch for its first moments (its input guard, read on the main thread's clock), and a touch stamped
     * from a clock left behind would fall in them however long the test waited.
     */
    internal fun inputClockCaughtUp() {
        val behind = android.os.SystemClock.uptimeMillis() - compose.mainClock.currentTime
        if (behind > 0) compose.mainClock.advanceTimeBy(behind)
    }

    /** Called after every round of [run] (say, to record each board a table shows). */
    internal val everyRun = mutableListOf<() -> Unit>()

    private companion object {
        val MAX_STEP: Duration = Duration.ofMillis(200)
        /** A frame at 60 Hz, rounded. */
        const val FRAME_MILLIS = 16L
    }

}

/**
 * Compose's main-thread dispatcher ([androidx.compose.ui.platform.AndroidUiDispatcher.Main], one per process) runs the global
 * snapshot manager: what tells compositions and `snapshotFlow`s of state written outside a composition, such as the table's
 * gesture clock that times a held finger (TBL-047). It posts to the main looper once and posts nothing more while it believes
 * that post is still to come. Robolectric empties the main looper after each test, so a post still waiting when a test ended
 * was dropped and the dispatcher waited for it for the rest of the run: in every later test such state reached the screens
 * only at the next idle check, a hold went unrecognised under the held finger, and the tests that hold a card to inspect it
 * (HoldPausesPlaybackTest, InspectChangesTest, StackAbilityTest, CardStatesTest) failed when they ran after a test that left
 * it so, as CombatHoldTest often did. Before each test it forgets a dropped post and posts again.
 */
internal object ComposeMainDispatcher {
    fun forgetDroppedPost() {
        val main = androidx.compose.ui.platform.AndroidUiDispatcher.Main[kotlin.coroutines.ContinuationInterceptor]
            as androidx.compose.ui.platform.AndroidUiDispatcher
        fun field(name: String) = main.javaClass.getDeclaredField(name).apply { isAccessible = true }
        synchronized(field("lock").get(main)!!) {
            field("scheduledTrampolineDispatch").setBoolean(main, false)
            field("scheduledFrameDispatch").setBoolean(main, false)
        }
        // Posts again (to the main looper and the next frame): what waited runs at the test's first round
        main.dispatch(kotlin.coroutines.EmptyCoroutineContext, Runnable {})
    }
}
