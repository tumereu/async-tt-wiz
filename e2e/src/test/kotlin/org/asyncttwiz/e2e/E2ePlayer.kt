package org.asyncttwiz.e2e

import android.app.Activity
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.android.AccountApi
import org.asyncttwiz.android.AccountEntry
import org.asyncttwiz.android.ActivityApi
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.android.AuthController
import org.asyncttwiz.android.DeckApi
import org.asyncttwiz.android.EngineGame
import org.asyncttwiz.android.EngineWorker
import org.asyncttwiz.android.GameConnectionMaintenance
import org.asyncttwiz.android.GameHeartbeatController
import org.asyncttwiz.android.LobbyApi
import org.asyncttwiz.android.LocalShownTexts
import org.asyncttwiz.android.LobbyController
import org.asyncttwiz.android.PodApi
import org.asyncttwiz.android.PodController
import org.asyncttwiz.android.ResetController
import org.asyncttwiz.android.SessionStore
import org.asyncttwiz.android.ShellDestination
import org.asyncttwiz.android.SocketGameConnection
import org.asyncttwiz.android.StoredSession
import org.asyncttwiz.protocol.GameSummary
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * One player's phone: the app's own code for a signed-in account, wired as [org.asyncttwiz.android.AuthViewModel] and
 * [org.asyncttwiz.android.MainActivity] wire it (account entry, lobby with its game socket and engine games, pods,
 * presence heartbeats, connection maintenance), its Compose UI ([AccountEntry], the app's whole signed-in screen) in an
 * activity of its own, and its network ([link]) to the real server. Two players run side by side in one Robolectric
 * JVM: they share the main thread and the engine thread, as two apps on one phone would share a CPU. The app's clock is
 * the main thread's (Robolectric's): it moves as the test lets the phones run ([E2e.run]), so a minute's grace passes
 * in a moment. [engineWorker] is where the game's engine work runs ([HeldWorker] holds what it shows).
 *
 * The finders ([node], [nodes]) look only at this player's windows (their activity, its dialogs and popups).
 */
internal class E2ePlayer(val account: TestAccount, private val compose: ComposeTestRule,
    val engineWorker: EngineWorker = EngineWorker.Background) : AutoCloseable {
    val name: String get() = account.identity.username
    val id: String get() = account.id
    /** This phone's network: [NetworkLink.cut] it to drop the connection. */
    val link = NetworkLink(E2eStack.origin)
    private val main = Handler(Looper.getMainLooper())
    private val post: (() -> Unit) -> Unit = { main.post(it) }
    private val workers = mutableListOf<ExecutorService>()
    private fun worker(name: String) = Executors.newSingleThreadExecutor { Thread(it, "${account.name}-$name").apply { isDaemon = true } }
        .also { workers += it }

    private class MemoryStore(token: String) : SessionStore {
        private var token: String? = token
        override fun load(): StoredSession = token?.let { StoredSession.Present(it) } ?: StoredSession.Empty
        override fun save(token: String) { this.token = token }
        override fun clear() { token = null }
    }

    val auth = AuthController(AccountApi(link.origin), MemoryStore(account.token), worker("account"), post = post)
    private fun authenticationRequired(token: String) = auth.authenticationRequired(token)
    val lobby = LobbyController(LobbyApi(link.origin, authenticationRequired = ::authenticationRequired), auth::sessionToken,
        worker("lobby"), post, openGame = { auth.push(ShellDestination.GAMES) }, gameConnection = SocketGameConnection(link.origin),
        accountId = { auth.state.account?.accountId }, authenticationRequired = ::authenticationRequired,
        deckApi = DeckApi(link.origin, authenticationRequired = ::authenticationRequired),
        clock = { android.os.SystemClock.uptimeMillis() }, engineWorker = engineWorker)
    val pods = PodController(PodApi(link.origin, authenticationRequired = ::authenticationRequired),
        DeckApi(link.origin, authenticationRequired = ::authenticationRequired), auth::sessionToken, worker("pods"), post)
    private val reset = ResetController(AccountApi(link.origin), "", worker("reset"), succeeded = {}, post = post)
    internal val heartbeat = GameHeartbeatController({ token, presence ->
        runBlocking { ActivityApi(link.origin).use { it.heartbeat(token, presence) } }
    }) { token, presence -> auth.sessionToken() == token && lobby.heartbeat(presence) }
    private val maintenance = GameConnectionMaintenance({ true }, {
        auth.foreground()
        if (auth.state.account != null) lobby.sync()
    }, main, lobby::tick)

    /** Whether the app is in the foreground (MainActivity resumed): away, it says it left the game, as onPause does. */
    var foreground by mutableStateOf(true)
        private set
    private lateinit var controller: ActivityController<ComponentActivity>
    val activity: ComponentActivity get() = controller.get()

    init { lobby.gameInput = heartbeat::input }

    /** The leak oracle on this phone's device (see [LeakWatch]). */
    val leaks = LeakWatch(this)

    /**
     * Every short-lived text this phone's screen showed, in order, as it appeared (the app's [LocalShownTexts]: table
     * notes, refusals' messages, the event text and its warning flash): a test reads them here, never missing one its
     * skippable clock ran past.
     */
    val shownTexts = mutableListOf<String>()

    /** Whether the screen showed a short-lived text containing [text] ([shownTexts]). */
    fun told(text: String): Boolean = shownTexts.any { text in it }

    /** Whether the screen showed SYS-012's refusal note ("That wasn't taken: ...") or a refused action's error. */
    val refusalSeen: Boolean get() = told("wasn't taken")

    private var onceAt = -1L

    /** Runs [action] once per board shown (a tap inside a wait's loop that mustn't repeat before the board changes), once
     * that board is [current]. */
    fun once(action: () -> Unit) {
        val version = game?.shown?.version ?: return
        if (version != onceAt && current()) { onceAt = version; action() }
    }

    /**
     * Whether the board on the screen is the newest one of the game: the engine thread's queued work done (an own
     * action's follow-up, the server's events the main thread handed it) and every board it published taken in by the
     * main thread, none of them newer. A tap on a board the engine thread has already moved on from is not taken
     * (SYS-011, SYS-012's "That wasn't taken: the game moved on first"), as a person's tap on a board just replaced: a
     * helper that taps by itself waits for this rather than tapping a board on its way out.
     */
    fun current(): Boolean {
        val before = game?.shown?.version ?: return false
        // Twice: the main thread's handing on of a server event queues engine work that publishes in turn
        repeat(2) {
            engine { }
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
        return game?.shown?.version == before
    }

    /** The board version [playAlong] last acted on. */
    internal var alongAt = -1L

    /** Starts the app's screen: signed in (the saved session), on the main menu. */
    fun launch() {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity.setContent {
            MaterialTheme {
                val state = auth.state
                val token = auth.sessionToken()
                val game = lobby.state.selected.takeIf { state.destination == ShellDestination.GAMES }
                val shown = foreground
                SideEffect { heartbeat.display(game?.takeIf { shown }, token) }
                CompositionLocalProvider(LocalShownTexts provides { text: String -> shownTexts += text }) {
                    Surface(Modifier.fillMaxSize()) { AccountEntry(auth, reset, lobby = lobby, pods = pods) }
                }
            }
        }
    }

    // ---- What the player does outside the screen

    /** The player leaves the app (MainActivity.onPause/onStop): the game's presence says they left, the lists pause. */
    fun away() {
        foreground = false
        lobby.background()
        heartbeat.display(null, null)
        controller.pause().stop()
    }

    /** The player comes back to the app (MainActivity.onResume). */
    fun back() {
        link.restore()
        controller.restart().resume()
        foreground = true
        auth.foreground()
        lobby.foreground()
    }

    // ---- The screen

    /** Nodes in this player's windows only. */
    private val mine = SemanticsMatcher("in ${account.name}'s app") { node ->
        var context = (node.root as? ViewRootForTest)?.view?.context
        while (context is ContextWrapper && context !is Activity) context = context.baseContext
        context === activity
    }

    fun node(matcher: SemanticsMatcher): SemanticsNodeInteraction = compose.onNode(matcher and mine, useUnmergedTree = true)
    fun nodes(matcher: SemanticsMatcher): SemanticsNodeInteractionCollection = compose.onAllNodes(matcher and mine, useUnmergedTree = true)
    fun text(text: String, substring: Boolean = false) = node(hasText(text, substring = substring))
    fun described(description: String, substring: Boolean = false) = node(hasContentDescription(description, substring = substring))
    fun tagged(tag: String) = node(hasTestTag(tag))
    fun shows(matcher: SemanticsMatcher) = nodes(matcher).fetchSemanticsNodes().isNotEmpty()
    fun showsText(text: String, substring: Boolean = false) = shows(hasText(text, substring = substring))

    /** What this player's screen shows, as text (for failure messages). */
    fun screen(): String = runCatching { nodes(SemanticsMatcher("any") { true }).fetchSemanticsNodes()
        .mapNotNull { n -> (n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString { it.text }
            ?: n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.joinToString())?.takeIf(String::isNotBlank) }
        .joinToString(" | ") }.getOrElse { "(screen unavailable: $it)" }

    // ---- The game

    /** The game open on this phone's table (its engine game: the app's own state, for assertions). */
    val game: EngineGame? get() = lobby.engineGame()?.also { last = it }

    /** The last game the table had open (still readable after it ended and the table let it go). */
    var last: EngineGame? = null
        private set

    /** Opens [summary] from the main menu's Games list, as the player does. */
    fun openGame(summary: GameSummary, e2e: E2e) {
        e2e.waitUntil("$name's menu") { shows(hasTestTag("menu-GAMES")) }
        tagged("menu-GAMES").performClick()
        e2e.waitUntil("$name's game in the Games list") { shows(hasText(summary.gameName)) }
        text(summary.gameName).performScrollTo().performClick()
        e2e.waitUntil("$name's table") { game?.shown != null }
    }

    /** Failure evidence: this player's screen (picture and semantics tree) and the app's log. */
    fun evidence(dir: File) {
        dir.mkdirs()
        runCatching {
            val view = activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1),
                android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(dir, "${account.name}.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        runCatching { File(dir, "${account.name}-tree.txt").writeText(nodes(SemanticsMatcher("any") { true }).printToString()) }
        runCatching { File(dir, "${account.name}-screen.txt").writeText(screen()) }
        // What the device did (its record, as a problem report's bundle holds it) and every short-lived text it showed
        runCatching {
            val open = game ?: last
            val line = open?.let { g -> engineWorker.call { g.device.session.let { s -> s.events.takeLast(60) + s.pending } } }.orEmpty()
                .joinToString("\n") { e -> "${e.id.take(8)} on ${e.parentId.take(8)} by ${e.author.take(8)}${if (e.assumed != null) " (assumed)" else ""}: " +
                    e.engine?.steps?.mapNotNull { it.input }?.joinToString().orEmpty() + if (e.table.isNotEmpty()) " table ${e.table.map { it::class.simpleName }}" else "" }
            File(dir, "${account.name}-device.txt").writeText(open?.repro()?.log.orEmpty().joinToString("\n") + "\n\n-- line (last 60, then pending)\n" + line)
        }
        runCatching { File(dir, "${account.name}-shown.txt").writeText(shownTexts.joinToString("\n")) }
    }

    override fun close() {
        runCatching { maintenance.close() }
        runCatching { heartbeat.close() }
        runCatching { controller.pause().stop().destroy() }
        runCatching { lobby.close() }
        runCatching { pods.close() }
        workers.forEach { it.shutdownNow() }
        link.close()
    }

    companion object {
        fun log(): String = AppLog.text()
    }
}
