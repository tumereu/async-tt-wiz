package org.asyncttwiz.android

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class NavigationPodsTest {
    @get:Rule val compose = createComposeRule()
    private val fixtures = ScreenFixtures()
    private val requests = mutableListOf<Runnable>()
    private val worker = object : AbstractExecutorService() {
        override fun execute(command: Runnable) { requests += command } // Leave network work pending.
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader,
        arrayOf(T::class.java)) { _, method, _ -> error("Unexpected network call: ${method.name}") } as T
    private val pods = PodController(unused<Pods>(), unused<Cubes>(), { "token" }, worker, { it() })
    private fun pod(id: String = "pod", phase: PodPhase = PodPhase.DRAFTING) = PodSummary(id, "Draft $id", phase,
        ScreenFixtures.alex, listOf(ScreenFixtures.alex, ScreenFixtures.riley), PodAttention.YOUR_PICK, 2,
        "2026-10-27T00:00:00Z")

    @After fun close() { pods.close() }

    private fun showNavigation(close: Boolean = true) {
        fixtures.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex,
            destination = ShellDestination.DECKS, drawerOpen = close))
        compose.setContent { ScreenTheme { Navigation(fixtures.auth, close, fixtures.lobby, pods) } }
    }

    @Test fun podsIsOneTopLevelSectionDirectlyBelowGamesWithMatchingIndentedRows() {
        val pod = pod()
        val game = fixtures.lobby.state.games.first()
        fixtures.lobby.seed(LobbyState(loaded = true, games = listOf(game)))
        pods.seed(PodState(loaded = true, pods = listOf(pod)))
        showNavigation()

        compose.onAllNodesWithText("Pods").assertCountEquals(1)
        val games = compose.onNodeWithText("Games").getUnclippedBoundsInRoot()
        val podsHeader = compose.onNodeWithText("Pods").getUnclippedBoundsInRoot()
        val gameRow = compose.onNodeWithText(game.gameName).getUnclippedBoundsInRoot()
        val podRow = compose.onNodeWithTag("navigation-pod-${pod.id}").getUnclippedBoundsInRoot()
        val decks = compose.onNodeWithText(ShellDestination.DECKS.title).getUnclippedBoundsInRoot()
        val decksLabel = compose.onNodeWithText(ShellDestination.DECKS.title, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val podsLabel = compose.onNodeWithText("Pods", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(games.left, podsHeader.left)
        assertTrue(decksLabel.left > podsLabel.left)
        assertTrue(decks.top < games.top)
        assertTrue(games.bottom <= gameRow.top)
        assertEquals(gameRow.bottom, podsHeader.top)
        assertEquals(podsHeader.bottom, podRow.top)
        assertEquals(gameRow.left, podRow.left)
        for (heading in listOf("Games", "Pods")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(heading, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(FontWeight.Bold, layouts.single().layoutInput.style.fontWeight)
        }
        compose.onNodeWithTag("navigation-pod-${pod.id}")
            .assert(hasText(pod.name)).assert(hasText("Your pick · 2 packs"))
            .assert(hasContentDescription("alex's avatar")).assert(hasContentDescription("riley's avatar"))
    }

    @Test fun everyMemberPodAppearsWithoutInvitationsOrMainMenuThreeItemLimit() {
        val entries = listOf(pod("setup", PodPhase.SETUP), pod("draft"), pod("build", PodPhase.DRAFTED), pod("fourth"))
        pods.seed(PodState(loaded = true, pods = entries,
            invitations = listOf(PodInvitation("invite", "Invitation only", ScreenFixtures.riley, entries.first().updatedAt))))
        showNavigation(close = false)
        entries.forEach { compose.onNodeWithTag("navigation-pod-${it.id}").performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText("Invitation only").assertDoesNotExist()
    }

    @Test fun podRowImmediatelyOpensPodDestinationClosesDrawerAndMarksSelection() {
        val pod = pod()
        pods.seed(PodState(loaded = true, pods = listOf(pod)))
        showNavigation()
        compose.onNodeWithTag("navigation-pod-${pod.id}").performScrollTo().performClick()
        assertEquals(ShellDestination.PODS, fixtures.auth.state.destination)
        assertEquals(pod.id, pods.state.selected)
        assertTrue(pods.state.loading)
        assertFalse(fixtures.auth.state.drawerOpen)
        compose.onNodeWithTag("navigation-pod-${pod.id}").assertIsSelected()
        compose.onNodeWithText("Pods").assertIsSelected()
    }

    @Test fun emptyPodsHeaderOpensListAndClosesDrawer() {
        pods.seed(PodState(loaded = true, selected = "previous"))
        showNavigation()
        compose.onNodeWithText("Pods").performScrollTo().performClick()
        assertEquals(ShellDestination.PODS, fixtures.auth.state.destination)
        assertNull(pods.state.selected)
        assertFalse(fixtures.auth.state.drawerOpen)
    }

    @Test fun unloadedPodsHaveNoSpinnerAndCachedRowsStayInPlaceDuringRefreshAndFailure() {
        pods.seed(PodState(loading = true))
        showNavigation()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
        compose.onNodeWithText("Pods").assertIsEnabled()
        val pod = pod()
        compose.runOnIdle { pods.seed(PodState(loaded = true, pods = listOf(pod))) }
        val row = compose.onNodeWithTag("navigation-pod-${pod.id}").performScrollTo().getUnclippedBoundsInRoot()
        val header = compose.onNodeWithText("Pods").getUnclippedBoundsInRoot()
        compose.runOnIdle {
            fixtures.lobby.seed(fixtures.lobby.state.copy(busy = true))
            pods.seed(pods.state.copy(loading = true))
        }
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
        assertEquals(row, compose.onNodeWithTag("navigation-pod-${pod.id}").getUnclippedBoundsInRoot())
        assertEquals(header, compose.onNodeWithText("Pods").getUnclippedBoundsInRoot())
        compose.runOnIdle { pods.seed(pods.state.copy(loading = false, error = "Could not refresh")) }
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
        assertEquals(row, compose.onNodeWithTag("navigation-pod-${pod.id}").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("navigation-pod-${pod.id}").assertIsEnabled()
    }

    @Test fun gamesStartupAndReopeningDrawerBothRefreshPodsInBackground() {
        fixtures.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex, destination = ShellDestination.GAMES))
        compose.setContent { ScreenTheme { AuthenticatedShell(fixtures.auth, false, lobby = fixtures.lobby, pods = pods) } }
        compose.runOnIdle {
            assertTrue(pods.state.loading)
            assertEquals(1, requests.size)
            requests.removeAt(0).run() // Complete the failed first load; opening the drawer must retry it.
            assertFalse(pods.state.loading)
            fixtures.auth.drawer(true)
        }
        compose.onNodeWithText("Pods").assertIsDisplayed()
        compose.runOnIdle { assertTrue(pods.state.loading); assertEquals(1, requests.size) }
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
    }

    @Test fun newPodCreateStaysAtTheBottomWhileSettingsScroll() {
        pods.seed(PodState(creating = NewPodDraft("Draft", "cube")))
        compose.setContent { ScreenTheme { PodsDestination(pods, "a", null, null, {}) } }
        val create = compose.onNodeWithText("Create").assertIsDisplayed().getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue(create.right - create.left > (root.right - root.left) * .9f)
        assertTrue(root.bottom - create.bottom <= 8.dp)
        compose.onNodeWithText("Color balancing").performScrollTo().assertIsDisplayed()
        assertEquals(create, compose.onNodeWithText("Create").getUnclippedBoundsInRoot())
    }

    @Test fun postDraftTabsHaveNoMemberStripAboveThemButSetupKeepsIt() {
        val detail = PodDetail("pod", "Pod", PodPhase.DRAFTED, ScreenFixtures.alex, DraftSettings(),
            PodSource("Cube", 540), DraftSupply(90, 540),
            listOf(PodMember(ScreenFixtures.alex), PodMember(ScreenFixtures.riley)), emptyList(), updatedAt = "now")
        pods.seed(PodState(selected = detail.id, detail = detail, tab = PodTab.BUILD))
        compose.setContent { ScreenTheme { PodsDestination(pods, "a", null, null, {}) } }
        for (tab in listOf(PodTab.BUILD, PodTab.STATS, PodTab.GAMES)) {
            compose.runOnIdle { pods.tab(tab) }
            compose.onNodeWithTag("pod-member-strip").assertDoesNotExist()
        }
        compose.runOnIdle { pods.seed(pods.state.copy(detail = detail.copy(phase = PodPhase.SETUP))) }
        compose.onNodeWithTag("pod-member-strip").assertIsDisplayed()
    }

    @Test fun portraitPodPlayHeaderHasModalHamburgerTitleAndAvatarsAboveTabs() = assertPodPlayHeader(false)

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapePodPlayUsesTheModalHamburgerInsteadOfASideArea() = assertPodPlayHeader(true)

    private fun assertPodPlayHeader(landscape: Boolean) {
        val detail = PodDetail("pod", "Pod", PodPhase.DRAFTING, ScreenFixtures.alex, DraftSettings(),
            PodSource("Cube", 540), DraftSupply(90, 540),
            listOf(PodMember(ScreenFixtures.alex), PodMember(ScreenFixtures.riley, packsWaiting = 1)), emptyList(), updatedAt = "now")
        fixtures.auth.seed(AuthState(restoring = false, account = ScreenFixtures.alex, destination = ShellDestination.PODS))
        pods.seed(PodState(selected = detail.id, detail = detail))
        lateinit var back: OnBackPressedDispatcher
        compose.setContent { ScreenTheme {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            AuthenticatedShell(fixtures.auth, landscape, pods = pods)
        } }
        for (tab in listOf(PodTab.DRAFT, PodTab.BUILD, PodTab.STATS)) {
            compose.runOnIdle { pods.tab(tab) }
            val root = compose.onNodeWithTag("authenticated-identity").getUnclippedBoundsInRoot()
            val header = compose.onNodeWithTag("pod-play-header").getUnclippedBoundsInRoot()
            val tabs = compose.onNodeWithTag("pod-tabs").getUnclippedBoundsInRoot()
            assertEquals(root.top, header.top)
            assertEquals(root.left, header.left)
            assertEquals(root.right, header.right)
            assertEquals(PodPlayHeaderHeight, header.bottom - header.top)
            assertEquals(header.bottom, tabs.top)
            assertEquals(root.left, tabs.left)
            val menu = compose.onNodeWithContentDescription("Open navigation").assertIsDisplayed().getUnclippedBoundsInRoot()
            val title = compose.onNodeWithTag("pod-play-title").assertTextEquals("Pod").getUnclippedBoundsInRoot()
            val avatar = compose.onNodeWithContentDescription("${ScreenFixtures.riley.username}'s avatar").getUnclippedBoundsInRoot()
            assertTrue(title.left >= menu.right && title.bottom - title.top <= 16.dp)
            assertTrue(avatar.top >= title.bottom && avatar.left >= menu.right && avatar.bottom <= header.bottom)
            val cog = compose.onNodeWithContentDescription("View settings").assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(cog.left >= avatar.right && cog.right == header.right && cog.bottom <= header.bottom)
            compose.onNodeWithContentDescription("View settings").performClick()
            compose.onNodeWithText("Draft · cards per row").assertIsDisplayed()
            compose.onNodeWithText("Build · columns").assertIsDisplayed()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithText("Build · columns").assertDoesNotExist()
            compose.onNodeWithContentDescription("Pod actions").assertIsDisplayed().performClick()
            compose.onNodeWithText("Cancel draft").assertIsDisplayed()
            compose.onNodeWithText("Destroy").assertIsDisplayed()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithText("Destroy").assertDoesNotExist()
        }
        compose.onNodeWithContentDescription("Open navigation").performClick()
        compose.runOnIdle { assertTrue(fixtures.auth.state.drawerOpen) }
        compose.onNodeWithContentDescription("Close navigation").assertIsDisplayed()
        compose.runOnIdle { back.onBackPressed() }
        compose.runOnIdle { assertFalse(fixtures.auth.state.drawerOpen); assertEquals("pod", pods.state.selected) }
        compose.onNodeWithTag("pod-play-header").assertIsDisplayed()
        compose.runOnIdle { back.onBackPressed(); assertNull(pods.state.selected) }
        compose.onAllNodesWithText("Pods").onFirst().assertIsDisplayed()
    }
}
