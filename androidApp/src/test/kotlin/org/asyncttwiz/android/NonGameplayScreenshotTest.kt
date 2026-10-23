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
            "sign-in", "register", "register-scrolled", "reset-email", "new-password", "home", "navigation-open")
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
        compose.setContent {
            ScreenTheme {
                Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
                    if (screen in listOf("sign-in", "register", "register-scrolled", "reset-email", "new-password")) {
                        AccountEntry(fixtures.auth, fixtures.reset)
                    } else {
                        fixtures.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex))
                        Row(Modifier.fillMaxSize()) {
                            if (landscape || screen == "navigation-open") Surface(Modifier.width(if (landscape) 220.dp else 280.dp).fillMaxHeight(),
                                color = Color.White) { Navigation(fixtures.auth, !landscape, fixtures.lobby) }
                            Box(Modifier.weight(1f)) {
                                ShellContent(ShellDestination.MENU, !landscape, {}, {}, null, null, null, fixtures.lobby, "a")
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
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
    init {
        lobby.seed(LobbyState(loaded = true, games = listOf(
            GameSummary("g1", "Azure Spark", "2026-10-21T12:00:00Z", alex, riley, "round", "invite1",
                InvitationState.ACCEPTED, GameLifecycle.ACTIVE, playId = "play"),
            GameSummary("g2", "Crackling Mist", "2026-10-20T12:00:00Z", alex, riley, "round", "invite2",
                InvitationState.ACCEPTED))))
    }
}
