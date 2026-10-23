package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.test.assertEquals

/** Production screen composables, synthetic accepted state, no HTTP/catalog/art dependencies. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class NonGameplayScreenshotTest(private val screen: String) {
    @get:Rule val compose = createComposeRule()

    companion object {
        @JvmStatic @Parameters(name = "{0}") fun screens() = listOf(
            "sign-in", "register", "register-scrolled", "reset-email", "new-password", "home", "navigation-open",
            "play", "contacts", "create-game", "waiting-invite", "game-context", "delete-game", "cancel-lobby",
            "lobby-select-deck", "choose-lobby-deck", "prepare-deck", "prepare-deck-faded", "ready-waiting")
    }

    @Test fun portrait() = capture(false)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun landscape() = capture(true)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(landscape: Boolean) {
        val fixtures = ScreenFixtures()
        fixtures.auth.seed(AuthState(restoring = false, page = if (screen.startsWith("register")) AuthPage.REGISTER else AuthPage.LOGIN,
            username = "player2_tactics", email = "player1@tabletop.local", password = "password123",
            message = if (screen == "sign-in") "Email or password not recognised." else null, error = screen == "sign-in"))
        if (screen == "reset-email") fixtures.reset.seed(ResetState(ResetPage.REQUEST, email = "alex@example.test",
            message = "If this email has an account, a reset link is on its way."))
        if (screen == "new-password") fixtures.reset.seed(ResetState(ResetPage.PASSWORD, password = "password123"))
        fixtures.configure(screen)
        val accountScreen = screen in listOf("sign-in", "register", "register-scrolled", "reset-email", "new-password")
        if (!accountScreen) fixtures.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex))
        if (screen == "prepare-deck") compose.mainClock.autoAdvance = false
        compose.setContent {
            ScreenTheme {
                Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
                    if (accountScreen) {
                        AccountEntry(fixtures.auth, fixtures.reset)
                    } else {
                        Row(Modifier.fillMaxSize()) {
                            if (landscape || screen == "navigation-open") Surface(Modifier.width(if (landscape) 220.dp else 280.dp).fillMaxHeight(),
                                color = Color.White) { Navigation(fixtures.auth, !landscape, fixtures.lobby) }
                            Box(Modifier.weight(1f)) {
                                when (screen) {
                                    "waiting-invite", "lobby-select-deck", "ready-waiting", "choose-lobby-deck", "prepare-deck", "prepare-deck-faded", "cancel-lobby" ->
                                        WaitingLobby(fixtures.lobby, "a", if (landscape) null else ({}), landscape, importDeck = {})
                                    else -> ShellContent(when (screen) {
                                        "play", "game-context", "delete-game" -> ShellDestination.GAMES
                                        "contacts", "create-game" -> ShellDestination.CONTACTS
                                        else -> ShellDestination.MENU
                                    }, !landscape, {}, {}, null, null, null, fixtures.lobby, "a")
                                }
                            }
                        }
                        SetupCancellationDialogs(fixtures.lobby)
                        GameDeletionDialogs(fixtures.lobby)
                    }
                }
            }
        }
        compose.waitForIdle()
        if (screen == "prepare-deck") { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        if (screen == "register-scrolled") compose.onNodeWithText("Sign in").performScrollTo()
        val output = File("build/outputs/roborazzi/non-gameplay/$screen-${if (landscape) "landscape" else "portrait"}.png")
        output.parentFile!!.mkdirs()
        captureScreenRoboImage(output.path)
        val image = ImageIO.read(output)
        assertEquals(if (landscape) 892 else 412, image.width)
        assertEquals(if (landscape) 412 else 892, image.height)
    }
}

/** Seeding accepted presentation avoids turning screenshot capture into a transport integration test. */
internal fun Any.seed(state: Any) {
    val field = javaClass.getDeclaredField("state\$delegate").apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    (field.get(this) as MutableState<Any?>).value = state
}

internal class ScreenFixtures {
    companion object {
        val alex = AccountIdentity("a", "alex")
        val riley = AccountIdentity("b", "riley")
    }
    private val worker = object : AbstractExecutorService() {
        override fun execute(command: Runnable) = Unit // Captures never run requests or polling work.
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        error("Screenshot attempted ${method.name}")
    } as T
    val auth = AuthController(unused<Accounts>(), unused<SessionStore>(), worker, post = { it() })
    val reset = ResetController(unused<PasswordResets>(), "https://example.test", worker, {}, { it() })
    val lobby = LobbyController(unused<Lobbies>(), { "fixture" }, worker, { it() }, accountId = { "a" })
    val definitions = listOf("Meadow Sentinel", "Aegis Ward", "Training Land", "Ember Adept", "Quiet Grove").mapIndexed { n, name ->
        CardDefinition(name, listOf(DefinitionFace("face-$n", name, typeLine = "Creature", corner = "1/3"))).let { SubmittedDefinition(DeckJson.digest(it), it) }
    }
    val decks = listOf("Training deck", "Woodland practice", "Starter list").mapIndexed { n, name ->
        DeckSummary("deck-$n", name, "1", "2026-10-20T12:00:00Z", 60, 15)
    }
    fun configure(screen: String) {
        val game = lobby.state.games.last().copy(invitationState = if (screen == "waiting-invite") InvitationState.DECLINED else InvitationState.ACCEPTED)
        val prepared = PreparedDeck("prepared", "deck-0", "1", "Training deck", null, "00000000-0000-4000-8000-000000000001", listOf(
            PreparedEntry(definitions[0].digest, SavedDeckSection.MAINBOARD, "0", 3),
            PreparedEntry(definitions[1].digest, SavedDeckSection.MAINBOARD, "3", 4),
            PreparedEntry(definitions[2].digest, SavedDeckSection.MAINBOARD, "7", 52),
            PreparedEntry(definitions[0].digest, SavedDeckSection.SIDEBOARD, "59", 1),
            PreparedEntry(definitions[3].digest, SavedDeckSection.SIDEBOARD, "60", 3),
            PreparedEntry(definitions[4].digest, SavedDeckSection.SIDEBOARD, "63", 12)))
        val prep = LobbyPreparation(game.id, game.preparationRound, listOf(
            PlayerPreparation("a", ready = screen == "ready-waiting", deck = prepared.takeUnless { screen == "lobby-select-deck" }),
            PlayerPreparation("b", ready = screen == "lobby-select-deck")), definitions, StarterChoice())
        val inLobby = screen in listOf("waiting-invite", "lobby-select-deck", "ready-waiting", "choose-lobby-deck", "prepare-deck", "prepare-deck-faded", "cancel-lobby")
        lobby.seed(lobby.state.copy(contacts = listOf(riley, AccountIdentity("c", "morgan")), query = "sam", searched = true,
            results = listOf(AccountIdentity("d", "sam")), connectedGames = setOf("g1", "g2", "g3"),
            invitations = listOf(game.copy(id = "g3", inviter = riley, recipient = alex, invitationState = InvitationState.PENDING)),
            selected = if (inLobby) game.id else null, detail = if (inLobby) game else null,
            preparation = if (inLobby) prep else null, preparing = screen.startsWith("prepare-deck"),
            transferFeedback = if (screen == "prepare-deck") TransferFeedback(1, "1× Meadow Sentinel → Sideboard") else null,
            picker = if (screen == "choose-lobby-deck") DeckPickerState(game, decks, decks.first(), loading = false) else null,
            draft = if (screen == "create-game") GameNameDraft(riley, "Crackling Mist") else null))
        if (screen == "game-context") lobby.gameContext(lobby.state.games.first())
        if (screen == "delete-game") lobby.requestGameDeletion(lobby.state.games.first())
        if (screen == "cancel-lobby") lobby.requestCancelSetup(game)
    }
    init {
        lobby.seed(LobbyState(loaded = true, games = listOf(
            GameSummary("g1", "Azure Spark", "2026-10-21T12:00:00Z", alex, riley, "round", "invite1",
                InvitationState.ACCEPTED, GameLifecycle.ACTIVE, playId = "play"),
            GameSummary("g2", "Crackling Mist", "2026-10-20T12:00:00Z", alex, riley, "round", "invite2",
                InvitationState.ACCEPTED))))
    }
}
