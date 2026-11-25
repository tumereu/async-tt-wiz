package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

/**
 * The "Waiting for you" dots (owner, 2026-11-22) where they show: every hamburger (the Menu and Games screens', a game's
 * table, a pod's header in setup or started), the menu panels and the side menu; never on the Games screen's rows. Shown from seeded state; nothing is sent.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-xhdpi")
class WaitingDotsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val worker = object : AbstractExecutorService() {
        override fun execute(command: Runnable) = Unit // Leave network work pending.
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader,
        arrayOf(T::class.java)) { _, method, _ -> error("Unexpected network call: ${method.name}") } as T
    private val pods = PodController(unused<Pods>(), unused<Cubes>(), { "token" }, worker, { it() })
    private val fixtures = ScreenFixtures()
    private val alex = ScreenFixtures.alex
    private val riley = ScreenFixtures.riley
    private val morgan = AccountIdentity("c", "morgan")

    @After fun close() { pods.close() }

    private fun game(id: String, name: String, holder: String?, hour: Int) = GameSummary(id, name, "2026-11-20T12:00:00Z", alex, riley,
        "round", "invite-$id", InvitationState.ACCEPTED, GameLifecycle.ACTIVE, playId = "play", attentionHolder = holder,
        updatedAt = "2026-11-22T${10 + hour}:00:00Z")
    private val yourMove = game("g-move", "Azure Spark", alex.accountId, 0)
    private val theirMove = game("g-wait", "Crackling Mist", riley.accountId, 1)
    private val invitation = GameSummary("g-invite", "Ember Road", "2026-11-22T09:00:00Z", riley, alex, "round", "invite-g-invite",
        InvitationState.PENDING, updatedAt = "2026-11-22T13:00:00Z")
    private val heldChallenge = GameSummary("g-held", "Sealed league #2", "2026-11-22T08:00:00Z", morgan, alex, "round", "invite-g-held",
        InvitationState.PENDING, updatedAt = "2026-11-22T12:00:00Z", podId = "p-build", podName = "Sealed league", invitationHeld = true)
    private fun pod(id: String, name: String, phase: PodPhase, attention: PodAttention, hour: Int) = PodSummary(id, name, phase, riley,
        listOf(alex, riley, morgan), attention, if (attention == PodAttention.YOUR_PICK) 2 else 0, "2026-11-22T0${hour}:00:00Z")
    private val pick = pod("p-pick", "Cube night", PodPhase.DRAFTING, PodAttention.YOUR_PICK, 1)
    private val build = pod("p-build", "Sealed league", PodPhase.DRAFTED, PodAttention.BUILD, 2)
    private val build2 = pod("p-build2", "Rochester Friday", PodPhase.DRAFTED, PodAttention.BUILD, 3)
    private val podInvitation = PodInvitation("p-invited", "Friday sealed", morgan, "2026-11-22T05:00:00Z", DraftMode.SEALED)

    private fun shot(name: String, caption: String) = captureShot("$SCREENSHOTS/waiting-dots/$name.png", caption)
    private fun settle() { compose.mainClock.advanceTimeBy(500); compose.waitForIdle() }
    private fun dotIn(row: String, dot: WaitingDot?) {
        for (each in listOf(WaitingDot.Move, WaitingDot.Pick, WaitingDot.Build)) {
            val found = compose.onAllNodes(hasTestTag("waiting-dot-${each.tag}") and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true)
            found.assertCountEquals(if (each == dot) 1 else 0)
        }
    }

    // LOB-267, LOB-268, LOB-269: the Menu screen in portrait: the hamburger in the highest colour of everything waiting (your
    // move), and each menu panel row in its own colour; a challenge held for your deck has none
    @Test fun theMenuScreenShowsEveryColour() {
        fixtures.lobby.seed(LobbyState(loaded = true, games = listOf(yourMove, theirMove), invitations = listOf(invitation, heldChallenge),
            connectedGames = setOf("g-move", "g-wait", "g-invite", "g-held")))
        pods.seed(PodState(loaded = true, pods = listOf(pick, build), invitations = listOf(podInvitation)))
        fixtures.auth.seed(AuthState(restoring = false, account = alex))
        compose.setContent { ScreenTheme { NoRipples { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            ShellContent(ShellDestination.MENU, true, {}, {}, null, null, null, fixtures.lobby, alex.accountId, pods = pods)
        } } } }
        settle()
        compose.onNodeWithTag("navigation-dot-move", useUnmergedTree = true).assertExists()
        dotIn("quick-continue-g-move", WaitingDot.Move)
        dotIn("quick-continue-g-invite", WaitingDot.Move)
        dotIn("quick-continue-g-held", null)
        dotIn("quick-pod-p-pick", WaitingDot.Pick)
        dotIn("quick-pod-p-build", WaitingDot.Build)
        // Owner, 2026-11-25: a pod invitation's row blue too
        dotIn("quick-pod-p-invited", WaitingDot.Pick)
        shot("menu", "The Menu screen in portrait while Alex's move waits in Azure Spark, Riley's invitation to Ember Road, his pick in " +
            "Cube night, his deck in Sealed league and Morgan's invitation to Friday sealed: the hamburger's dot is red (move first), " +
            "the game rows' red, Cube night's and Friday sealed's blue and Sealed league's green; Morgan's challenge held for his deck has none.")
    }

    // LOB-267, LOB-268: at a game's table the hamburger's dot leaves that game out: with only a pick waiting elsewhere it is blue
    @Test fun theTableShowsWhatWaitsElsewhere() {
        val open = LiveTableFixture.game.copy(attentionHolder = alex.accountId)
        val dot = navigationDot(listOf(open), listOf(pick, build), emptyList(), alex.accountId, openGame = open.id)?.dot
        compose.setContent { ScreenTheme { CompositionLocalProvider(LocalNavigationDot provides dot) {
            TableSurface(open, tableBoard(LiveTableFixture.play()), "a", false, Modifier.fillMaxSize(), connected = true,
                runtime = TableRuntimeHooks(navigation = {}))
        } } }
        settle()
        compose.onNodeWithTag("navigation-dot-pick", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("navigation-dot-move", useUnmergedTree = true).assertDoesNotExist()
        shot("table-pick", "Alex at the table of a game where it is his move: the hamburger's dot is blue, for his pick in Cube night; " +
            "this game's own move gives none.")
    }

    private fun sealedLeague() = PodDetail(build.id, build.name, PodPhase.DRAFTED, riley, DraftSettings(mode = DraftMode.SEALED),
        PodSource("Runes", 540), DraftSupply(135, 540), listOf(PodMember(alex, building = true), PodMember(riley, submitted = true),
            PodMember(morgan, submitted = true)), emptyList(), draftAttemptId = "attempt", updatedAt = "2026-11-22T02:00:00Z")

    private fun showPod(drawer: Boolean) {
        fixtures.lobby.seed(LobbyState(loaded = true, games = listOf(theirMove), connectedGames = setOf("g-wait")))
        pods.seed(PodState(loaded = true, pods = listOf(build, build2), selected = build.id, detail = sealedLeague(), tab = PodTab.GAMES))
        fixtures.auth.seed(AuthState(restoring = false, account = alex, destination = ShellDestination.PODS, drawerOpen = drawer))
        val dot = navigationDot(fixtures.lobby.state.games, pods.state.pods, emptyList(), alex.accountId, openPod = build.id)?.dot
        compose.setContent { ScreenTheme { NoRipples { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            CompositionLocalProvider(LocalNavigationDot provides dot) {
                Box(Modifier.fillMaxSize()) {
                    PodsDestination(pods, alex.accountId, {}, fixtures.lobby, {}, mainMenu = {})
                    if (drawer) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .2f)))
                        Surface(Modifier.width(280.dp).fillMaxHeight(), color = Color.White) {
                            Navigation(fixtures.auth, true, fixtures.lobby, pods)
                        }
                    }
                }
            }
        } } } }
        settle()
    }

    // DRF-306, LOB-268: a started pod's hamburger leaves that pod out: building your deck in another pod gives a green dot
    @Test fun aStartedPodsHamburgerShowsWhatWaitsElsewhere() {
        showPod(drawer = false)
        compose.onNode(hasTestTag("navigation-dot-build") and hasAnyAncestor(hasTestTag("pod-play-header")), useUnmergedTree = true)
            .assertExists()
        shot("pod-build", "Alex in Sealed league, building his deck there: the pod header's hamburger dot is green, for his deck to build " +
            "in Rochester Friday; this pod's own build gives none.")
    }

    // LOB-269: the side menu's rows carry their own colour, never the pod you are looking at
    @Test fun theSideMenuRowsCarryTheirDotsButNotTheOpenPod() {
        showPod(drawer = true)
        dotIn("navigation-pod-p-build", null)
        dotIn("navigation-pod-p-build2", WaitingDot.Build)
        dotIn("navigation-game-g-wait", null)
        shot("side-menu-pod", "The side menu opened over Sealed league: Rochester Friday's row has a green dot for Alex's deck to build " +
            "there, Sealed league (open) and Crackling Mist (Riley's move) have none.")
    }

    // LOB-269: the side menu in a game's table: your move in another game and an invitation red, the open game none; LOB-018:
    // the challenge held for Alex's deck would be the sixth row, so it is not listed
    @Test fun theSideMenuLeavesOutTheOpenGame() {
        fixtures.lobby.seed(LobbyState(loaded = true, games = listOf(yourMove, theirMove), invitations = listOf(invitation, heldChallenge),
            selected = theirMove.id, connectedGames = setOf("g-move", "g-wait", "g-invite", "g-held")))
        pods.seed(PodState(loaded = true, pods = listOf(pick, build)))
        fixtures.auth.seed(AuthState(restoring = false, account = alex, destination = ShellDestination.GAMES, drawerOpen = true))
        compose.setContent { ScreenTheme { Navigation(fixtures.auth, true, fixtures.lobby, pods) } }
        dotIn("navigation-game-g-move", WaitingDot.Move)
        dotIn("navigation-game-g-invite", WaitingDot.Move)
        compose.onNodeWithTag("navigation-game-g-held").assertDoesNotExist()
        dotIn("navigation-pod-p-pick", WaitingDot.Pick)
        dotIn("navigation-pod-p-build", WaitingDot.Build)
        // Your move in the game you are looking at gives its row none
        compose.runOnIdle { fixtures.lobby.seed(fixtures.lobby.state.copy(selected = yourMove.id)) }
        dotIn("navigation-game-g-move", null)
        // LOB-018: it keeps its place, first of the list
        compose.onNodeWithTag("navigation-game-g-move").assertIsSelected()
        assertTrue(compose.onNodeWithTag("navigation-game-g-move").getUnclippedBoundsInRoot().bottom <=
            compose.onNodeWithTag("navigation-game-g-invite").getUnclippedBoundsInRoot().top)
    }

    // LOB-268: a pod still in setup leaves itself out too: its hamburger blue for the pick in Cube night
    @Test fun aPodInSetupsHamburgerShowsWhatWaitsElsewhere() {
        val setup = pod("p-setup", "Friday cube", PodPhase.SETUP, PodAttention.NONE, 4)
        val detail = PodDetail(setup.id, setup.name, PodPhase.SETUP, alex, DraftSettings(mode = DraftMode.SEALED), PodSource("Runes", 540),
            DraftSupply(135, 540), listOf(PodMember(alex), PodMember(riley)), emptyList(), updatedAt = "2026-11-22T04:00:00Z")
        fixtures.lobby.seed(LobbyState(loaded = true, games = listOf(theirMove), connectedGames = setOf("g-wait")))
        pods.seed(PodState(loaded = true, pods = listOf(setup, pick), selected = setup.id, detail = detail))
        val dot = navigationDot(fixtures.lobby.state.games, pods.state.pods, emptyList(), alex.accountId, openPod = setup.id)?.dot
        compose.setContent { ScreenTheme { NoRipples { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            CompositionLocalProvider(LocalNavigationDot provides dot) { PodsDestination(pods, alex.accountId, {}, fixtures.lobby, {}, mainMenu = {}) }
        } } } }
        settle()
        compose.onNodeWithTag("navigation-dot-pick", useUnmergedTree = true).assertExists()
        shot("pod-setup", "Alex setting up Friday cube: the header's hamburger dot is blue, for his pick in Cube night.")
    }

    // LOB-269, LOB-024, LOB-268: the Games screen's rows keep their move chip and get no dot; its hamburger is red for the move
    @Test fun theGamesScreenRowsGetNoDots() {
        fixtures.lobby.seed(LobbyState(loaded = true, games = listOf(yourMove, theirMove), invitations = listOf(invitation),
            connectedGames = setOf("g-move", "g-wait", "g-invite")))
        compose.setContent { ScreenTheme { ShellContent(ShellDestination.GAMES, true, {}, {}, null, null, null, fixtures.lobby, alex.accountId) } }
        compose.onNodeWithText("Your move").assertExists()
        for (dot in listOf(WaitingDot.Move, WaitingDot.Pick, WaitingDot.Build))
            compose.onAllNodes(hasTestTag("waiting-dot-${dot.tag}"), useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodes(hasTestTag("navigation-dot-move"), useUnmergedTree = true).assertCountEquals(1)
    }
}
