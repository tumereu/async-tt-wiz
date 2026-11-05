package org.asyncttwiz.e2e

import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.android.AutomatumBoards
import org.asyncttwiz.android.EngineWorker
import org.asyncttwiz.protocol.GameSummary
import org.asyncttwiz.protocol.game.PlaySettings
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

/**
 * The base of every e2e test: two players' phones ([alice], [bob]: the real app, [E2ePlayer]) on the real server
 * ([E2eStack]), a game between them set up as the test chooses ([startGame]: decks, the starting player, each player's
 * play defaults), and [waitUntil] for whatever the test waits for (a server round trip, the other phone's table, a
 * playback): never a fixed sleep, never an elapsed-time assert (owner rule), only a condition and a generous timeout.
 *
 * When a test fails, each phone's screen (picture, semantics tree, visible texts) and the app log are written to
 * `e2e/build/e2e/failures/<Test>.<method>/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class E2e {
    private val compose = createEmptyComposeRule()
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
        deal: Deal.(TestAccount, TestAccount) -> Boolean = { _, _ -> true }) {
        val (a, b) = ServerSetup.accounts()
        ServerSetup.playDefaults(a, aliceSettings ?: PlaySettings())
        ServerSetup.playDefaults(b, bobSettings ?: PlaySettings())
        prepared = ServerSetup.prepare(a, b, aliceDeck, bobDeck, starter = if (bobStarts) b else a,
            name = "${javaClass.simpleName} ${java.util.UUID.randomUUID().toString().take(6)}", deal = { it.deal(a, b) })
        game = prepared.invitation
        alice = E2ePlayer(a, compose, aliceWorker).also(players::add)
        bob = E2ePlayer(b, compose).also(players::add)
        alice.launch(); bob.launch()
        if (ready) ready()
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
     * Both players open the game, Bob first: Alice's phone opens it with Bob already there, so it waits for him while he is
     * (a phone that opened the game before the other player was there assumes their opening keep at once: a point first
     * met while they aren't live is never a wait, I-D56 §4). Each holds the hand the deal worked out and sees the other.
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
        compose.waitForIdle()
        everyRun.forEach { it() }
        players.forEach {
            it.leaks.install()
            if (it.game != null && it.showsText("wasn't taken", substring = true)) it.refusalSeen = true
        }
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

    /** Called after every round of [run] (say, to record each board a table shows). */
    internal val everyRun = mutableListOf<() -> Unit>()

    private companion object { val MAX_STEP: Duration = Duration.ofMillis(200) }

}
