package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The side menu's one list of games and pods (owner, 2026-11-25): the portrait drawer, the landscape side area and the desktop's
 * permanent side menu, with every kind of row, the open game's, a short list and an empty one. Shown from seeded state;
 * nothing is sent.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-xhdpi")
class SideMenuListScreenTest {
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

    @Before fun pinClock() { AppClock.pinned = Clock.fixed(Instant.parse("2026-11-25T18:00:00Z"), ZoneOffset.UTC) }
    @After fun close() { AppClock.pinned = null; pods.close() }

    private fun at(hour: Int) = "2026-11-25T${hour.toString().padStart(2, '0')}:00:00Z"
    private fun game(id: String, name: String, holder: String?, hour: Int, opponent: AccountIdentity = riley) = GameSummary(id, name,
        at(0), alex, opponent, "round", "invite-$id", InvitationState.ACCEPTED, GameLifecycle.ACTIVE, playId = "play-$id",
        attentionHolder = holder, updatedAt = at(hour))
    // Red: Alex's move, Riley's invitation; no dot: Morgan's move, an older game of Riley's move
    private val yourMove = game("g-move", "Azure Spark", alex.accountId, 9)
    private val invitation = GameSummary("g-invite", "Ember Road", at(0), riley, alex, "round", "invite-g-invite",
        InvitationState.PENDING, updatedAt = at(11))
    private val theirMove = game("g-wait", "Crackling Mist", morgan.accountId, 15, morgan)
    private val olderGame = game("g-old", "Old Quarry", riley.accountId, 2)
    // Last: a challenge held until Alex submits his deck (LOB-025)
    private val heldChallenge = GameSummary("g-held", "Sealed league #2", at(0), morgan, alex, "round", "invite-g-held",
        InvitationState.PENDING, updatedAt = at(14), podId = "p-build", podName = "Sealed league", invitationHeld = true)
    private fun pod(id: String, name: String, phase: PodPhase, attention: PodAttention, hour: Int) = PodSummary(id, name, phase, riley,
        listOf(alex, riley, morgan), attention, if (attention == PodAttention.YOUR_PICK) 2 else 0, at(hour))
    private val pick = pod("p-pick", "Cube night", PodPhase.DRAFTING, PodAttention.YOUR_PICK, 8)
    private val build = pod("p-build", "Sealed league", PodPhase.DRAFTED, PodAttention.BUILD, 6)
    private val podInvitation = PodInvitation("p-invited", "Friday sealed", morgan, at(10), DraftMode.SEALED)

    private fun shot(name: String, caption: String) = captureShot("$SCREENSHOTS/side-menu/$name.png", caption)
    private fun settle() { compose.mainClock.advanceTimeBy(500); compose.waitForIdle() }
    private fun rowTags(): List<String> = compose.onAllNodes(SemanticsMatcher("a side-menu row") { node ->
        node.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.let { it.startsWith("navigation-game-") || it.startsWith("navigation-pod-") } == true
    }).fetchSemanticsNodes().sortedBy { it.boundsInRoot.top }.map { it.config[SemanticsProperties.TestTag] }
    private fun dotIn(row: String, dot: WaitingDot?) {
        for (each in listOf(WaitingDot.Move, WaitingDot.Pick, WaitingDot.Build))
            compose.onAllNodes(hasTestTag("waiting-dot-${each.tag}") and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true)
                .assertCountEquals(if (each == dot) 1 else 0)
    }

    /** LOB-269 (owner gallery review 2026-11-25): each of [rows] (tag to its name) has its dot's slot, reserved with or without a dot,
     * at one x between the avatar column and the name, its dot (if any) inside it. */
    private fun dotSlotsAligned(rows: Map<String, String>) {
        fun bounds(matcher: SemanticsMatcher, row: String) =
            compose.onNode(matcher and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val slots = rows.mapValues { (row, _) -> bounds(hasTestTag(WAITING_DOT_SLOT_TAG), row) }
        val x = slots.values.first().left
        for ((row, name) in rows) {
            val slot = slots.getValue(row)
            assertTrue(kotlin.math.abs(slot.left - x) < 0.5f, "$row's dot slot at ${slot.left}, the first row's at $x")
            val text = bounds(hasText(name), row)
            assertTrue(slot.right <= text.left, "$row's dot slot ($slot) before its name ($text)")
            for (each in listOf(WaitingDot.Move, WaitingDot.Pick, WaitingDot.Build)) {
                val dot = compose.onAllNodes(hasTestTag("waiting-dot-${each.tag}") and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true)
                    .fetchSemanticsNodes().singleOrNull()?.boundsInRoot ?: continue
                assertTrue(dot.left >= slot.left - 0.5f && dot.right <= slot.right + 0.5f, "$row's dot ($dot) in its slot ($slot)")
                assertTrue(dot.top >= text.top && dot.bottom <= text.bottom, "$row's dot ($dot) on its name's line ($text)")
            }
        }
    }
    /** LOB-018 (owner 2026-11-26): each of [rows] (tag to whether it is a pod) has one badge, a pod's or a game's, on its first
     * avatar's bottom-left corner (choice). */
    private fun badges(rows: Map<String, Boolean>) {
        for ((row, pod) in rows) {
            compose.onAllNodes(hasTestTag("navigation-badge-pod") and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true)
                .assertCountEquals(if (pod) 1 else 0)
            compose.onAllNodes(hasTestTag("navigation-badge-game") and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true)
                .assertCountEquals(if (pod) 0 else 1)
            val badge = compose.onNode(hasTestTag(if (pod) "navigation-badge-pod" else "navigation-badge-game") and hasAnyAncestor(hasTestTag(row)),
                useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val avatar = compose.onAllNodes(hasContentDescription("'s avatar", substring = true) and hasAnyAncestor(hasTestTag(row)),
                useUnmergedTree = true).fetchSemanticsNodes().minBy { it.boundsInRoot.left }.boundsInRoot
            assertTrue(badge.left < avatar.left + avatar.width / 2 && badge.bottom > avatar.bottom - avatar.height / 2,
                "$row's badge ($badge) on its first avatar's bottom-left corner ($avatar)")
        }
    }
    private val mixKinds = mapOf("navigation-game-g-move" to false, "navigation-pod-invitation-p-invited" to true,
        "navigation-pod-p-build" to true, "navigation-game-g-wait" to false, "navigation-game-g-held" to false)

    private val mixNames = mapOf("navigation-game-g-move" to "Azure Spark", "navigation-pod-invitation-p-invited" to "Friday sealed",
        "navigation-pod-p-build" to "Sealed league", "navigation-game-g-wait" to "Crackling Mist", "navigation-game-g-held" to "Sealed league #2")

    private fun seed(games: List<GameSummary>, invitations: List<GameSummary>, podList: List<PodSummary>, podInvitations: List<PodInvitation>,
        destination: ShellDestination = ShellDestination.MENU, selectedGame: String? = null, drawer: Boolean = true) {
        fixtures.lobby.seed(LobbyState(loaded = true, games = games, invitations = invitations, selected = selectedGame,
            connectedGames = (games + invitations).map { it.id }.toSet()))
        pods.seed(PodState(loaded = true, pods = podList, invitations = podInvitations))
        fixtures.auth.seed(AuthState(restoring = false, account = alex, destination = destination, drawerOpen = drawer))
    }

    /** The portrait drawer over the Menu screen (or [behind]), as the shell draws it. */
    private fun showDrawer(behind: @Composable () -> Unit = {
        ShellContent(ShellDestination.MENU, true, {}, {}, null, null, null, fixtures.lobby, alex.accountId, pods = pods)
    }) {
        compose.setContent { ScreenTheme { NoRipples { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            Box(Modifier.fillMaxSize()) {
                behind()
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .2f)))
                Surface(Modifier.width(280.dp).fillMaxHeight(), color = Color.White) { Navigation(fixtures.auth, true, fixtures.lobby, pods) }
            }
        } } } }
        settle()
    }

    private fun seedMix() = seed(listOf(yourMove, theirMove), listOf(heldChallenge), listOf(build), listOf(podInvitation))

    // LOB-018, LOB-269, LOB-025: one of each: red (Alex's move), blue (an invitation to a pod), green (his deck to build), no dot
    // (Morgan's move), and last the challenge he can't answer yet; every dot before its name at one x, the names lined up
    @Test fun thePortraitDrawerListsEveryKindOfRow() {
        seedMix()
        showDrawer()
        assertEquals(listOf("navigation-game-g-move", "navigation-pod-invitation-p-invited", "navigation-pod-p-build",
            "navigation-game-g-wait", "navigation-game-g-held"), rowTags())
        dotIn("navigation-game-g-move", WaitingDot.Move)
        dotIn("navigation-pod-invitation-p-invited", WaitingDot.Pick)
        dotIn("navigation-pod-p-build", WaitingDot.Build)
        dotIn("navigation-game-g-wait", null)
        dotIn("navigation-game-g-held", null)
        dotSlotsAligned(mixNames)
        // LOB-018 (owner 2026-11-26): a game's crossed swords or a pod's table on each row's first avatar
        badges(mixKinds)
        shot("drawer-mix", "The side menu opened over the Menu screen in portrait, one list above the buttons Menu, Games, Pods, " +
            "Manage decks, Contacts, Stats and Settings, each row's dot in its own slot between the avatars and the name: Azure Spark " +
            "(Alex's move) red, Morgan's invitation to the pod Friday sealed blue, Sealed league (his deck to build) green, Crackling " +
            "Mist (Morgan's move) with an empty slot, and last Morgan's challenge he can't accept before submitting his deck; each " +
            "game's first avatar carries a small crossed-swords badge and each pod's a table badge (choice: a white 16 dp disc on the " +
            "avatar's bottom-left corner).")
    }

    // LOB-018: always five rows: with seven, Cube night (blue) comes in, and the oldest game without a dot and the held challenge
    // drop out
    @Test fun theDrawerListsFiveOfMany() {
        seed(listOf(yourMove, theirMove, olderGame), listOf(invitation, heldChallenge), listOf(build, pick), listOf(podInvitation))
        showDrawer()
        assertEquals(listOf("navigation-game-g-move", "navigation-game-g-invite", "navigation-pod-p-pick",
            "navigation-pod-invitation-p-invited", "navigation-pod-p-build"), rowTags())
        shot("drawer-five-of-many", "The side menu with eight games and pods waiting: only the five most important show, Alex's move " +
            "and Riley's invitation red, Cube night's pick and Friday sealed's invitation blue (longest waiting first), Sealed league " +
            "green; Crackling Mist, Old Quarry and the held challenge are left out.")
    }

    // LOB-018, LOB-269: in Azure Spark's table its row keeps its place, highlighted and without its red dot
    @Test fun theOpenGamesRowKeepsItsPlace() {
        val open = LiveTableFixture.game.copy(gameName = "Azure Spark", attentionHolder = alex.accountId, updatedAt = at(9), recipient = riley)
        seed(listOf(open, theirMove), listOf(invitation, heldChallenge), listOf(build), listOf(podInvitation),
            destination = ShellDestination.GAMES, selectedGame = open.id)
        showDrawer {
            TableSurface(open, tableBoard(LiveTableFixture.play()), "a", false, Modifier.fillMaxSize(), connected = true,
                runtime = TableRuntimeHooks(navigation = {}))
        }
        assertEquals("navigation-game-${open.id}", rowTags().first())
        compose.onNodeWithTag("navigation-game-${open.id}").assertIsSelected()
        dotIn("navigation-game-${open.id}", null)
        dotIn("navigation-game-g-invite", WaitingDot.Move)
        shot("drawer-open-game", "The side menu opened at Azure Spark's table, where it is Alex's move: Azure Spark stays first in the " +
            "list, highlighted and without a dot; Ember Road's invitation is still red.")
    }

    // LOB-018: fewer than five only when there are fewer
    @Test fun aShortListHasFewerRows() {
        seed(listOf(theirMove), emptyList(), listOf(pick), emptyList())
        showDrawer()
        assertEquals(listOf("navigation-pod-p-pick", "navigation-game-g-wait"), rowTags())
        shot("drawer-two", "The side menu when Alex has one game and one pod: two rows, Cube night (his pick, blue) above Crackling " +
            "Mist (Morgan's move), then the buttons.")
    }

    // LOB-018: with no games or pods only the buttons show
    @Test fun anEmptyListShowsOnlyTheButtons() {
        seed(emptyList(), emptyList(), emptyList(), emptyList())
        showDrawer()
        assertEquals(emptyList(), rowTags())
        val menu = compose.onNodeWithTag("navigation-Menu").getUnclippedBoundsInRoot()
        val close = compose.onNodeWithContentDescription("Close navigation").getUnclippedBoundsInRoot()
        assertTrue(menu.top - close.bottom <= 16.dp, "the buttons right under the close button: $menu")
        shot("drawer-empty", "The side menu of a new player with no games or pods: no list, only the buttons Menu, Games, Pods, " +
            "Manage decks, Contacts, Stats and Settings.")
    }

    // LOB-268 (owner, 2026-11-25), LOB-017, LOB-269: an invitation to a pod lights the hamburger blue, and its menu-panel row too
    @Test fun aPodInvitationLightsTheHamburgerAndItsPanelRowBlue() {
        seed(listOf(theirMove), emptyList(), emptyList(), listOf(podInvitation), drawer = false)
        compose.setContent { ScreenTheme { NoRipples { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            ShellContent(ShellDestination.MENU, true, {}, {}, null, null, null, fixtures.lobby, alex.accountId, pods = pods)
        } } } }
        settle()
        compose.onNodeWithTag("navigation-dot-pick", useUnmergedTree = true).assertExists()
        dotIn("quick-pod-p-invited", WaitingDot.Pick)
        // LOB-269 (owner 2026-11-25): the panels' rows have the dot's slot before the name too, empty on Crackling Mist's
        dotIn("quick-continue-g-wait", null)
        dotSlotsAligned(mapOf("quick-pod-p-invited" to "Friday sealed"))
        dotSlotsAligned(mapOf("quick-continue-g-wait" to "Crackling Mist"))
        shot("menu-pod-invitation", "The Menu screen in portrait when only Morgan's invitation to the pod Friday sealed waits for Alex: " +
            "the hamburger's dot is blue, and so is the invitation's row in the Pods panel, its dot before the name; Crackling Mist's " +
            "row in the Games panel keeps the dot's place empty.")
    }

    // LOB-018, WEB-037: landscape's permanent side area holds the same list
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun theLandscapeSideAreaListsTheSame() {
        seedMix()
        compose.setContent { ScreenTheme { NoRipples { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            Row(Modifier.fillMaxSize()) {
                Surface(Modifier.width(220.dp).fillMaxHeight(), color = Color.White) { Navigation(fixtures.auth, false, fixtures.lobby, pods) }
                Box(Modifier.weight(1f)) {
                    ShellContent(ShellDestination.MENU, false, {}, {}, null, null, null, fixtures.lobby, alex.accountId, pods = pods)
                }
            }
        } } } }
        settle()
        assertEquals("navigation-game-g-move", rowTags().first())
        // LOB-269 (owner 2026-11-25)
        dotSlotsAligned(mixNames)
        badges(mixKinds)
        shot("landscape", "The Menu screen in landscape with the side area always open at its left: the same list (Azure Spark " +
            "red, Friday sealed blue, Sealed league green, Crackling Mist, the held challenge), each row's game or pod badge on its " +
            "first avatar, above the buttons.")
    }

    // LOB-018, WEB-037: the desktop's permanent side menu holds the same list
    @Test @Config(qualifiers = "w1440dp-h900dp-land-mdpi")
    fun theDesktopSideMenuListsTheSame() {
        seedMix()
        compose.setContent { ScreenTheme { CompositionLocalProvider(LocalAppLayout provides AppLayout.Desktop) { NoRipples {
            Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
                Row(Modifier.fillMaxSize()) {
                    Surface(Modifier.width(DesktopScreens.SideMenuWidth).fillMaxHeight(), color = Color.White) {
                        Navigation(fixtures.auth, false, fixtures.lobby, pods)
                    }
                    Box(Modifier.weight(1f)) {
                        ShellContent(ShellDestination.MENU, false, {}, {}, null, null, null, fixtures.lobby, alex.accountId, pods = pods)
                    }
                }
            }
        } } } }
        settle()
        assertEquals(5, rowTags().size)
        // LOB-269 (owner 2026-11-25)
        dotSlotsAligned(mixNames)
        badges(mixKinds)
        shot("desktop", "The desktop layout's Menu screen with its side menu always open at the left: the same list (Azure Spark " +
            "red, Friday sealed blue, Sealed league green, Crackling Mist, the held challenge), each row's game or pod badge on its " +
            "first avatar, above the buttons.")
    }
}
