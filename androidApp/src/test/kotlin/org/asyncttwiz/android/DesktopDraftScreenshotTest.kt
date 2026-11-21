package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
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
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

/**
 * A started pod on the desktop layout (WEB-044, wireframe D9) at 1440 × 900: the draft pick with
 * its keep and burn trays and Confirm at the bottom, the header with the tabs in its row, and Build's overlapping columns with a
 * card's edge zones under the pointer. Invented cards.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1440dp-h900dp-land-mdpi")
class DesktopDraftScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val worker = object : AbstractExecutorService() {
        override fun execute(command: Runnable) = Unit // Captures never run requests.
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader,
        arrayOf(T::class.java)) { _, method, _ -> error("Unexpected network call: ${method.name}") } as T
    private val pods = PodController(unused<Pods>(), unused<Cubes>(), { "token" }, worker, { it() })

    @Before fun clearPreferences() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (name in listOf("pod-pick", "pod-build")) context.getSharedPreferences(name, 0).edit().clear().commit()
    }
    @After fun close() { pods.close() }

    private val alice = AccountIdentity("alice", "Alice")
    private val bob = AccountIdentity("bob", "Bob")
    private val carol = AccountIdentity("carol", "Carol")

    /** Invented cards: name, mana cost, type line. */
    private val cards = listOf(
        Triple("Dawnward Sentry", "{1}{W}", "Creature — Human Soldier"), Triple("Lantern Oath", "{W}", "Instant"),
        Triple("Skyhall Lancer", "{3}{W}", "Creature — Bird Knight"), Triple("Mercy Vow", "{2}{W}", "Enchantment — Aura"),
        Triple("Tidecall Adept", "{1}{U}", "Creature — Merfolk Wizard"), Triple("Mistweave", "{U}{U}", "Instant"),
        Triple("Archive Drake", "{3}{U}", "Creature — Drake"), Triple("Gravel Ghoul", "{1}{B}", "Creature — Zombie"),
        Triple("Duskmarket Thief", "{2}{B}", "Creature — Human Rogue"), Triple("Cinder Whelp", "{R}", "Creature — Dragon"),
        Triple("Spark Lash", "{R}", "Instant"), Triple("Forgeborn Brute", "{2}{R}", "Creature — Giant"),
        Triple("Mossback Cub", "{G}", "Creature — Bear"), Triple("Thornwood Ranger", "{2}{G}", "Creature — Elf Ranger"),
        Triple("Canopy Colossus", "{5}{G}", "Creature — Treefolk"), Triple("Brass Courier", "{2}", "Artifact Creature — Construct"),
        Triple("Waystone Idol", "{3}", "Artifact"), Triple("Sunken Causeway", "", "Land"),
    )
    private fun digest(n: Int) = "def-$n"
    private val definitions = cards.mapIndexed { n, (name, cost, type) ->
        SubmittedDefinition(digest(n), CardDefinition(name, listOf(DefinitionFace("face", name, typeLine = type, manaCost = cost,
            scryfallId = placeholderPrinting(name)))))
    }
    private fun copy(n: Int, copy: Int = 0) = DraftCopy("c$n-$copy", digest(n))

    private fun detail(phase: PodPhase, settings: DraftSettings, members: List<PodMember>) = PodDetail("pod", "Cube night", phase,
        alice, settings, PodSource("Runes", 540, maybeCopies = 12), DraftSupply(135, 540), members, emptyList(),
        draftAttemptId = "attempt", definitions = definitions, updatedAt = "2026-11-07T00:00:00Z",
        // While the pod drafts, Alice's own draft runs (DRF-302), so no Games tab (DRF-076), unless she has submitted
        draftWait = PodDraftWait.Picks.takeIf { phase == PodPhase.DRAFTING && members.none { it.account == alice && it.submitted } })

    private fun show(state: PodState) {
        pods.seed(state)
        compose.setContent { ScreenTheme { NoRipples {
            CompositionLocalProvider(LocalAppLayout provides AppLayout.Desktop) {
                Surface(Modifier.fillMaxSize(), color = ScreenBackground) { PodsDestination(pods, alice.accountId, {}, null, {}, mainMenu = {}) }
            }
        } } }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    // WEB-044, WEB-093, WEB-036, WEB-062, DRF-056, DRF-057, DRF-061, DRF-065 (D9): the pick by clicks and keys, the trays at the bottom with
    // Confirm at the end of their row (owner playtest 2026-11-16), the tabs in the header's row between the hamburger and avatars
    @Test fun draftPick() {
        val head = DraftPrivatePack("pack-a", 1, (0..13).map { copy(it) }, 2, "bob")
        val members = listOf(PodMember(alice, packsWaiting = 2), PodMember(bob, packsWaiting = 1), PodMember(carol))
        val detail = detail(PodPhase.DRAFTING, DraftSettings.glimpse(), members).copy(draft = DraftPrivateView("alice", head, 1,
            listOf(copy(16, 1)), queuedPacks = emptyList(), round = 1, rounds = 9))
        show(PodState(loaded = true, selected = detail.id, detail = detail, tab = PodTab.DRAFT))
        // A click keeps Cinder Whelp, a right-click burns Gravel Ghoul, B over Mistweave burns it; the pointer rests on Archive Drake
        fun mark(id: String, mark: String) = compose.onNodeWithTag("pod-pick-card-$id")
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, mark))
        compose.onNodeWithTag("pod-pick-card-c9-0").performMouseInput { click() }
        mark("c9-0", "Keep")
        compose.onNodeWithTag("pod-pick-card-c7-0").performMouseInput {
            moveTo(center); press(androidx.compose.ui.test.MouseButton.Secondary); release(androidx.compose.ui.test.MouseButton.Secondary)
        }
        mark("c7-0", "Burn")
        compose.onNodeWithTag("pod-pick-card-c5-0").performMouseInput { moveTo(center) }
        compose.runOnIdle { DesktopKeyboard.dispatch(DesktopKey("b", true, 1)); DesktopKeyboard.dispatch(DesktopKey("b", false, 2)) }
        mark("c5-0", "Burn")
        compose.onNodeWithTag("pod-pick-card-c6-0").performMouseInput { moveTo(center) }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithTag("pod-pick-more-c6-0").assertIsDisplayed()
        compose.onNodeWithText("Confirm").assertIsEnabled()
        val tray = compose.onNodeWithTag("pod-pick-burn-tray").fetchSemanticsNode().boundsInRoot
        assertTrue(tray.bottom > 780f, "the trays at the bottom: $tray")
        val confirm = compose.onNodeWithTag("pod-pick-confirm").fetchSemanticsNode().boundsInRoot
        assertTrue(confirm.left >= tray.right && confirm.top < tray.bottom && confirm.bottom > tray.top, "Confirm on the trays' row: $confirm, $tray")
        val header = compose.onNodeWithTag("pod-play-header").fetchSemanticsNode().boundsInRoot
        val tabs = compose.onNodeWithTag("pod-tabs").fetchSemanticsNode().boundsInRoot
        val avatars = compose.onNodeWithTag("pod-draft-members").fetchSemanticsNode().boundsInRoot
        assertTrue(tabs.top >= header.top && tabs.bottom <= header.bottom && tabs.right <= avatars.left, "tabs in the header's row, left of the avatars: $tabs, $avatars")
        captureShot("$SCREENSHOTS/desktop/draft-pick.png",
            "Alice's Glimpse pick on the desktop layout, eight cards to a row: a click kept Cinder Whelp, a right-click burned Gravel Ghoul and B over Mistweave burned it, so both trays at the bottom are full with Confirm at the end of their row; the header holds the tabs at its left, the pod's name and avatars in its middle and its icons at the right; the pointer rests on Archive Drake, which shows its ⋯ menu button.")
    }

    // DRF-296, DRF-297, WEB-038 (owner, 2026-11-21): a pod's setup with bot seats on the desktop layout, at its readable width:
    // the bot seats after the members and the "Bots − N +" stepper beside the invite button
    @Test fun botSeatsSetup() {
        val detail = detail(PodPhase.SETUP, DraftSettings(bots = 2), listOf(PodMember(alice), PodMember(bob)))
        show(PodState(loaded = true, selected = detail.id, detail = detail, tab = PodTab.DRAFT))
        compose.onNodeWithTag("pod-bots-value", useUnmergedTree = true).assertTextEquals("2")
        compose.onNodeWithText("Members (4/8)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("pod-bots").performScrollTo().assertIsDisplayed()
        captureShot("$SCREENSHOTS/desktop/setup-bots.png",
            "A Booster pod's setup with two bot seats on the desktop layout at its readable width: the setting tiles end with the sideboard's ∞ (no Bots tile), the members list counts four of eight seats, Alice and Bob, then Bot 1 and Bot 2 marked \"bot\", and under it the invite button with the \"Bots − 2 +\" stepper at the row's right.")
    }

    // DRF-293, DRF-294 (owner, 2026-11-21): on the desktop layout Auto is the tip at the right end of Confirm, at the end of the
    // trays' row; a click turns it on and Confirm yellow with its shine
    @Test fun autoconfirmToggle() {
        val head = DraftPrivatePack("pack-a", 1, (0..13).map { copy(it) }, 2, "bob")
        val members = listOf(PodMember(alice, packsWaiting = 2), PodMember(bob, packsWaiting = 1), PodMember(carol))
        val detail = detail(PodPhase.DRAFTING, DraftSettings.glimpse(), members).copy(draft = DraftPrivateView("alice", head, 1,
            listOf(copy(16, 1)), queuedPacks = emptyList(), round = 1, rounds = 9))
        show(PodState(loaded = true, selected = detail.id, detail = detail, tab = PodTab.DRAFT))
        compose.runOnIdle { pods.mark(head, PodPickMarks("pack-a", keeps = listOf(copy(9).id), burns = listOf(copy(7).id))) }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val toggle = compose.onNodeWithTag("pod-pick-autoconfirm").assertIsOff().fetchSemanticsNode().boundsInRoot
        val confirm = compose.onNodeWithTag("pod-pick-confirm").fetchSemanticsNode().boundsInRoot
        assertTrue(confirm.right <= toggle.left && toggle.left - confirm.right < 4f && toggle.center.y in confirm.top..confirm.bottom,
            "Auto the tip right after Confirm: $confirm, $toggle")
        captureShot("$SCREENSHOTS/desktop/autoconfirm-off.png",
            "Alice's Glimpse pick on the desktop layout, Cinder Whelp kept and Gravel Ghoul burned with one burn to go: Confirm at the end of the trays' row ends in Auto, a narrow tip with a bolt over \"Auto\" behind a thin divider, off.")
        compose.onNodeWithTag("pod-pick-autoconfirm").performMouseInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithTag("pod-pick-autoconfirm").assertIsOn()
        captureShot("$SCREENSHOTS/desktop/autoconfirm-on.png",
            "The same pick after Alice clicks Auto: the tip and Confirm are one yellow shape with a band of light across Confirm; her last burn will confirm the pick at once.")
    }

    // DRF-293, DRF-294 (owner, 2026-11-21): a Grid pick on the desktop layout with Auto clicked on: the yellow shining Confirm
    // and Auto, its tip, fill the row under the grid; a click on a line's arrow then takes that line at once
    @Test fun gridAutoconfirm() {
        val nine = (0..8).map { copy(it) }
        val board = GridPublicBoard("grid-0", 1, nine, alice.accountId, 1, false, nine.map { GridPublicSlot(it) })
        val grid = org.asyncttwiz.protocol.PodGridView(GridPublicView(1, 9, false, listOf("alice", "bob"), listOf(board), emptyList()),
            GridPrivateView(alice.accountId, emptyList(), board.id, queue = listOf(board.id), round = 1, rounds = 18))
        val detail = detail(PodPhase.DRAFTING, DraftSettings.grid(), listOf(PodMember(alice, packsWaiting = 1), PodMember(bob)))
            .copy(grid = grid)
        show(PodState(loaded = true, selected = detail.id, detail = detail, tab = PodTab.DRAFT))
        compose.onNodeWithTag("pod-pick-autoconfirm").assertIsOff().performMouseInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithTag("pod-pick-autoconfirm").assertIsOn()
        val toggle = compose.onNodeWithTag("pod-pick-autoconfirm").fetchSemanticsNode().boundsInRoot
        val confirm = compose.onNodeWithTag("pod-grid-confirm").fetchSemanticsNode().boundsInRoot
        assertTrue(confirm.right <= toggle.left && toggle.center.y in confirm.top..confirm.bottom, "Auto the tip after Confirm: $confirm, $toggle")
        captureShot("$SCREENSHOTS/desktop/grid-autoconfirm-on.png",
            "Alice's pick from grid 1 of a two-player Grid draft on the desktop layout after she clicks Auto, the tip at Confirm's right end: both are yellow, Confirm's label dimmed with a band of light across it; a click on a row's or column's arrow now takes that line at once.")
        compose.onNodeWithContentDescription("Select row 1").performMouseInput { click() }
        compose.runOnIdle { assertTrue(pods.state.picking, "the click sent row 1") }
    }

    // DRF-288, WEB-117: an Arena offer on the desktop layout, the pointer resting on a card for its quick look
    @Test fun arenaPick() {
        val head = DraftPrivatePack("offer-0-9", 10, listOf(2, 6, 11, 14, 16).map { copy(it) }, 1, null)
        val members = listOf(PodMember(alice, packsWaiting = 36), PodMember(bob, packsWaiting = 41), PodMember(carol, packsWaiting = 30))
        val detail = detail(PodPhase.DRAFTING, DraftSettings.arena(), members).copy(draft = DraftPrivateView("alice", head, 0,
            (0..8).map { copy(it, 1) }, round = 10, rounds = 45))
        show(PodState(loaded = true, selected = detail.id, detail = detail, tab = PodTab.DRAFT))
        compose.onNodeWithTag("pod-pick-orientation").assertTextEquals("Pick 10 of 45 · Rare · take 1")
        compose.onNodeWithTag("pod-pick-card-c2-0").performMouseInput { click() }
        compose.onNodeWithTag("pod-pick-card-c14-0").performMouseInput { moveTo(center) }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithTag(QUICK_LOOK_TAG).assertIsDisplayed()
        compose.onNodeWithTag("pod-pick-burn-tray").assertDoesNotExist()
        captureShot("$SCREENSHOTS/desktop/arena-pick.png",
            "Alice's tenth Arena pick, a rare one, on the desktop layout: her own offer of five cards under \"Pick 10 of 45 · Rare · take 1\", a click took Skyhall Lancer into the keep tray with Confirm beside it, no burn tray, and the pointer resting on Canopy Colossus shows its quick look over its neighbour.")
    }

    // WEB-117 (owner 2026-11-19, D9): resting the pointer on a pack card a quarter of a second shows it large with every face beside
    // it; a pressed button hides it until let go, and leaving the card takes it away
    @Test fun draftPickQuickLook() {
        val pilgrim = SubmittedDefinition("def-dfc", CardDefinition("Moonlit Pilgrim // Howling Pilgrim", listOf(
            DefinitionFace("front", "Moonlit Pilgrim", typeLine = "Creature — Human Werewolf", manaCost = "{1}{G}",
                scryfallId = placeholderPrinting("Moonlit Pilgrim")),
            DefinitionFace("back", "Howling Pilgrim", typeLine = "Creature — Werewolf", scryfallId = placeholderPrinting("Howling Pilgrim"))),
            layout = "transform"))
        val head = DraftPrivatePack("pack-a", 1, (0..12).map { copy(it) } + DraftCopy("dfc-0", "def-dfc"), 2, "bob")
        val members = listOf(PodMember(alice, packsWaiting = 2), PodMember(bob, packsWaiting = 1), PodMember(carol))
        val detail = detail(PodPhase.DRAFTING, DraftSettings.glimpse(), members).copy(definitions = definitions + pilgrim,
            draft = DraftPrivateView("alice", head, 1, emptyList(), queuedPacks = emptyList(), round = 1, rounds = 9))
        show(PodState(loaded = true, selected = detail.id, detail = detail, tab = PodTab.DRAFT))
        val card = compose.onNodeWithTag("pod-pick-card-dfc-0")
        card.performMouseInput { moveTo(center) }
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
        compose.onNodeWithTag(QUICK_LOOK_TAG).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        // Both faces, beside the card, inside the window
        val look = compose.onNodeWithTag(QUICK_LOOK_TAG).assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val bounds = card.fetchSemanticsNode().boundsInWindow
        assertTrue(look.left >= bounds.right || look.right <= bounds.left, "beside the card: $look, $bounds")
        assertTrue(look.width > look.height, "two faces side by side: $look")
        captureShot("$SCREENSHOTS/desktop/draft-quick-look.png",
            "Alice's Glimpse pick on the desktop layout with the pointer resting on Moonlit Pilgrim, a double-faced card: after a quarter of a second its quick look shows both faces large beside it, nothing dimmed.")
        card.performMouseInput { moveTo(center); press() }
        compose.waitForIdle()
        compose.onNodeWithTag(QUICK_LOOK_TAG).assertDoesNotExist()
        card.performMouseInput { release() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNodeWithTag(QUICK_LOOK_TAG).assertIsDisplayed()
        card.performMouseInput { moveTo(Offset(-400f, -400f)) }
        compose.waitForIdle()
        compose.onNodeWithTag(QUICK_LOOK_TAG).assertDoesNotExist()
    }

    // WEB-062 (owner playtest 2026-11-16): a desktop window too narrow for the tabs, eight avatars and the icons on one row puts the
    // tabs on their own row under the header, as the phone does
    @Test @Config(qualifiers = "w800dp-h600dp-land-mdpi") fun draftPickNarrowHeader() {
        val head = DraftPrivatePack("pack-a", 1, (0..13).map { copy(it) }, 2, "bob")
        val others = (1..7).map { PodMember(AccountIdentity("player$it", "Player $it"), packsWaiting = it % 3) }
        val detail = detail(PodPhase.DRAFTING, DraftSettings.glimpse(), listOf(PodMember(alice, packsWaiting = 2)) + others)
            .copy(draft = DraftPrivateView("alice", head, 1, emptyList(), queuedPacks = emptyList(), round = 1, rounds = 9))
        show(PodState(loaded = true, selected = detail.id, detail = detail, tab = PodTab.DRAFT))
        val header = compose.onNodeWithTag("pod-play-header").fetchSemanticsNode().boundsInRoot
        val tabs = compose.onNodeWithTag("pod-tabs").fetchSemanticsNode().boundsInRoot
        assertTrue(tabs.top >= header.bottom, "the tabs on their own row: $tabs, $header")
        captureShot("$SCREENSHOTS/desktop/pod-header-narrow.png",
            "Alice's Glimpse pick on the desktop layout in an 800 × 600 window with eight players: the tabs, eight avatars and the header's icons don't fit one row, so the tabs have their own row under the header, as on the phone.")
    }

    // WEB-062: the tabs share the header's row only where they, the avatars and the icons fit
    @Test fun theHeaderRowFitsTheTabsWhereThereIsRoom() {
        assertTrue(podHeaderOneRow(androidx.compose.ui.unit.Dp(1440f), 3, 3, 3, navigation = true))
        assertTrue(podHeaderOneRow(androidx.compose.ui.unit.Dp(1024f), 3, 8, 3, navigation = true))
        assertTrue(!podHeaderOneRow(androidx.compose.ui.unit.Dp(800f), 3, 8, 3, navigation = true))
        assertTrue(!podHeaderOneRow(androidx.compose.ui.unit.Dp(1024f), 3, 16, 3, navigation = true))
    }

    // WEB-044, WEB-094, DRF-061, DRF-065 (owner playtest 2026-11-15): in the owner's 1920 × 919 window the marks on the pack cards (their
    // borders and badges) and the trays' count chips are drawn 1.5 times the phone's size; the ⋯ sits under a marked card's badge
    @Test @Config(qualifiers = "w1920dp-h919dp-land-mdpi") fun draftPickMarks() {
        val head = DraftPrivatePack("pack-a", 1, (0..13).map { copy(it) }, 2, "bob")
        val members = listOf(PodMember(alice, packsWaiting = 2), PodMember(bob, packsWaiting = 1), PodMember(carol))
        val detail = detail(PodPhase.DRAFTING, DraftSettings.glimpse(), members).copy(draft = DraftPrivateView("alice", head, 1,
            listOf(copy(16, 1)), queuedPacks = emptyList(), round = 1, rounds = 9))
        show(PodState(loaded = true, selected = detail.id, detail = detail, tab = PodTab.DRAFT))
        compose.onNodeWithTag("pod-pick-card-c12-0").performMouseInput { click() }
        compose.onNodeWithTag("pod-pick-card-c3-0").performMouseInput {
            moveTo(center); press(androidx.compose.ui.test.MouseButton.Secondary); release(androidx.compose.ui.test.MouseButton.Secondary)
        }
        compose.onNodeWithTag("pod-pick-card-c12-0").performMouseInput { moveTo(center) }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        fun badge(id: String) = compose.onNode(hasTestTag("pod-pick-badge") and hasAnyAncestor(hasTestTag("pod-pick-card-$id")))
            .fetchSemanticsNode().boundsInRoot
        // The phone's badges are about 20 tall, its chips 13 to 15: here half as large again
        val keep = badge("c12-0")
        assertTrue(keep.height >= 28f, "a 1.5× keep badge: $keep")
        val burn = badge("c3-0")
        assertTrue(burn.height >= 28f, "a 1.5× burn badge: $burn")
        val more = compose.onNodeWithTag("pod-pick-more-c12-0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(more.top >= keep.bottom, "the ⋯ under the badge: $more, $keep")
        for (tag in listOf("pod-pick-keep-chip", "pod-pick-burn-chip")) {
            val chip = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(chip.height >= 18f, "a 1.5× $tag: $chip")
        }
        captureShot("$SCREENSHOTS/desktop/draft-pick-marks-1920.png",
            "Alice's Glimpse pick in a 1920 × 919 window, as the owner played it: a click kept Mossback Cub and a right-click burned Mercy Vow, their borders, ✓ and 🔥 badges and the trays' count chips drawn half as large again as the phone's; the pointer rests on Mossback Cub, its ⋯ under the badge and its quick look beside it (WEB-117).")
    }

    private fun statsDetail(): PodDetail {
        val main = (0..15).map { copy(it) } + copy(9, 1)
        val build = PodDeckBuild(main.map { PodDeckCopy(it.id) } + listOf(PodDeckCopy(copy(16).id, PodDeckCategory.SIDE)),
            PodDeckBasics(plains = 6, mountain = 5, forest = 6))
        return detail(PodPhase.DRAFTED, DraftSettings.booster(), listOf(PodMember(alice, building = true),
            PodMember(bob, submitted = true), PodMember(carol, building = true))).copy(
            draft = DraftPrivateView("alice", null, 0, main + copy(16)), build = build)
    }

    // WEB-061, WEB-110, DRF-111, DRF-114, DRF-117, DRF-118 (owner playtest 2026-11-15): the pod's Stats on the desktop layout keeps
    // Settings' readable width, the deck's numbers in the left column and the colours, notes and counters in the right
    @Test fun stats() {
        val detail = statsDetail()
        show(PodState(loaded = true, selected = detail.id, detail = detail, tab = PodTab.STATS))
        compose.runOnIdle { pods.editNotes { PodDeckNotes("White-green go-wide; Carol takes red.", listOf(4, 0, 0, 1, 5, 0, 1, 0)) } }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val stats = compose.onNodeWithTag("pod-stats").fetchSemanticsNode().boundsInRoot
        assertTrue(stats.width <= 1120f, "a readable width: $stats")
        val left = compose.onNodeWithTag("pod-stats-left").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("pod-stats-right").fetchSemanticsNode().boundsInRoot
        assertTrue(right.left > left.right, "two columns: $left, $right")
        fun inside(tag: String, column: androidx.compose.ui.geometry.Rect) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            .let { assertTrue(it.left >= column.left && it.right <= column.right, "$tag in its column: $it, $column") }
        inside("pod-stats-curve", left)
        inside("pod-stats-mana", left)
        inside("pod-stats-colors", right)
        inside("pod-stats-notes", right)
        inside("pod-stats-counter-7", right)
        captureShot("$SCREENSHOTS/desktop/stats.png",
            "Alice's Stats after a Booster draft on the desktop layout at 1440 × 900, with notes and counters she set: at Settings' readable width centred in the window, the curve with its Type | Color toggle, the counts, type rows and pips and sources in the left column, the Colors chart by cast cost, notes and tally counters in the right.")
    }

    // WEB-061: a desktop window too narrow for two columns shows Stats in one, as the phone does
    @Test @Config(qualifiers = "w700dp-h500dp-land-mdpi") fun statsNarrow() {
        val detail = statsDetail()
        show(PodState(loaded = true, selected = detail.id, detail = detail, tab = PodTab.STATS))
        compose.onNodeWithTag("pod-stats").assertIsDisplayed()
        compose.onNodeWithTag("pod-stats-left").assertDoesNotExist()
        compose.onNodeWithTag("pod-stats-curve").assertIsDisplayed()
    }

    // WEB-045, WEB-063 (D8, D9): Build with the pointer on a Main card's right quarter: "Maybe ▸" tinted strongly, "◂ Side" faintly;
    // the columns overlap, only the cards' name strips showing above the last of each column
    @Test fun buildEdgeZones() {
        val main = (0..15).map { copy(it) } + copy(9, 1)
        val side = listOf(16, 17).map { copy(it) }
        val build = PodDeckBuild(main.map { PodDeckCopy(it.id) } + side.map { PodDeckCopy(it.id, PodDeckCategory.SIDE) },
            PodDeckBasics(plains = 6, mountain = 5, forest = 6))
        val detail = detail(PodPhase.DRAFTED, DraftSettings.booster(), listOf(PodMember(alice, building = true),
            PodMember(bob, submitted = true), PodMember(carol, building = true))).copy(draft = DraftPrivateView("alice", null, 0, main + side),
            build = build)
        show(PodState(loaded = true, selected = detail.id, detail = detail, tab = PodTab.BUILD))
        compose.onNodeWithText("Review ›").assertDoesNotExist()
        compose.onNodeWithTag("pod-build-card-c4-0").performMouseInput { moveTo(Offset(width * .88f, height * .6f)) }
        compose.waitForIdle()
        compose.onNodeWithTag("edge-zone-right-active").assertIsDisplayed()
        captureShot("$SCREENSHOTS/desktop/build-edge-zones.png",
            "Alice's Build on the desktop layout, each column's cards overlapping so only their name strips show above its last card, with the pointer on Tidecall Adept's right quarter, the last card of its column: that quarter is tinted Maybe's amber with \"Maybe ▸\" and the left one faintly Side's blue with \"◂ Side\", where a click moves one copy and a Shift+click every copy; the section headers have no Review, as Build has no one-by-one review on the desktop.")
    }

    // DRF-119, DRF-303, DRF-076 (owner, 2026-11-21, MT74XQ): on the desktop layout too, Build says over its bottom bar when Submit
    // opens while Alice's own draft runs (a Booster pick of hers still to come), and the header's tabs have no Games yet
    @Test fun buildSubmitLocked() {
        val main = (0..15).map { copy(it) } + copy(9, 1)
        val build = PodDeckBuild(main.map { PodDeckCopy(it.id) }, PodDeckBasics(plains = 8, mountain = 7, forest = 8))
        val detail = detail(PodPhase.DRAFTING, DraftSettings.booster(), listOf(PodMember(alice), PodMember(bob, packsWaiting = 2),
            PodMember(carol, building = true))).copy(draft = DraftPrivateView("alice", null, 0, main, round = 3, rounds = 3, incoming = 1),
            build = build, draftWait = PodDraftWait.Picks)
        show(PodState(loaded = true, selected = detail.id, detail = detail, tab = PodTab.BUILD))
        compose.onNodeWithTag("pod-build-submit-wait").assertTextEquals("Submit opens after your last pick")
        compose.onNode(hasClickAction() and hasText("Submit deck", substring = true)).assertIsNotEnabled()
        compose.onNodeWithText("Games").assertDoesNotExist()
        captureShot("$SCREENSHOTS/desktop/build-submit-locked.png",
            "Alice's Build on the desktop layout in round 3 of a Booster draft, one pack still to come back to her: her 40-card deck is ready, but over the full-width bottom bar a line says \"Submit opens after your last pick\" and Submit deck (40) is greyed; the header's tabs read Draft · Build · Stats, no Games while her own draft runs.")
    }

    // WEB-120, DRF-250, DRF-251, DRF-252, DRF-254: on the desktop layout Build's filter button sits in the bottom right corner above the bar;
    // a click opens its popup, where the Creature chip and "human" (a word of the type line) leave Main with the two Humans
    @Test fun buildFilterPopup() {
        val detail = statsDetail()
        show(PodState(loaded = true, selected = detail.id, detail = detail, tab = PodTab.BUILD))
        val list = compose.onNodeWithTag("pod-build").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithTag("pod-build-filter").fetchSemanticsNode().boundsInRoot
        assertTrue(button.right <= list.right && list.right - button.right < 40f && button.bottom <= list.bottom && list.bottom - button.bottom < 40f,
            "the button in Build's bottom right corner: $button in $list")
        compose.onNodeWithTag("pod-build-filter").performMouseInput { click() }
        compose.waitForIdle()
        compose.onNodeWithTag("pod-build-filter-type-Creature").performMouseInput { click() }
        compose.onNodeWithTag("pod-build-filter-field").performTextInput("human")
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        fun shown(n: Int) = compose.onAllNodesWithTag("pod-build-card-c$n-0").fetchSemanticsNodes().isNotEmpty()
        assertTrue(shown(0) && shown(8), "Dawnward Sentry and Duskmarket Thief shown")
        assertTrue((1..17).filter { it != 8 }.none(::shown), "no other card shown")
        captureShot("$SCREENSHOTS/desktop/build-filter.png",
            "Alice's Build on the desktop layout after she clicks the filter button in the bottom right corner, chooses Creature and types \"human\" in the popup: Main shows only Dawnward Sentry and Duskmarket Thief, its header still counting the whole deck, and the button is filled with a dot.")
    }

    // WEB-063, WEB-111 (owner playtest 2026-11-16): the pointer on the name strip of a card high in its column brings that card to the front,
    // whole and in place, over the cards below it; its edge zones show
    @Test fun buildHoveredCardInFront() {
        val main = (0..15).map { copy(it) } + copy(9, 1)
        val build = PodDeckBuild(main.map { PodDeckCopy(it.id) }, PodDeckBasics(plains = 6, mountain = 5, forest = 6))
        val detail = detail(PodPhase.DRAFTED, DraftSettings.booster(), listOf(PodMember(alice, building = true),
            PodMember(bob, submitted = true), PodMember(carol, building = true))).copy(draft = DraftPrivateView("alice", null, 0, main),
            build = build)
        show(PodState(loaded = true, selected = detail.id, detail = detail, tab = PodTab.BUILD))
        // Dawnward Sentry lies second in the MV 2 column, covered by the cards after it but for its name strip
        compose.onNodeWithTag("pod-build-strip-c0-0").performMouseInput { moveTo(Offset(width * .12f, height / 2f)) }
        compose.waitForIdle()
        compose.onNodeWithTag("edge-zone-left-active").assertIsDisplayed()
        captureShot("$SCREENSHOTS/desktop/build-hovered-card.png",
            "Alice's Build on the desktop layout with the pointer on Dawnward Sentry's name strip, high in its overlapping column: the card comes to the front whole, in its place, over the cards below it, its left quarter tinted Side's blue with \"◂ Side\" under the pointer.")
    }
}
