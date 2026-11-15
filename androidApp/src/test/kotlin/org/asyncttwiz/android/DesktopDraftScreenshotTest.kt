package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
 * A started pod on the desktop layout (docs/specs/web-client.md §7.6, wireframe D9; step 6b) at 1440 × 900: the draft pick with
 * its keep and burn trays and Confirm at the bottom, and Build with a card's edge zones under the pointer. Invented cards.
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
        draftAttemptId = "attempt", definitions = definitions, updatedAt = "2026-11-07T00:00:00Z")

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

    // WEB-044, WEB-036, DRF-056, DRF-057, DRF-061, DRF-065 (D9): the pick by clicks and keys, the trays and Confirm at the bottom
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
        captureShot("$SCREENSHOTS/desktop/draft-pick.png",
            "Alice's Glimpse pick on the desktop layout, eight cards to a row: a click kept Cinder Whelp, a right-click burned Gravel Ghoul and B over Mistweave burned it, so both trays at the bottom are full above the full-width Confirm; the pointer rests on Archive Drake, which shows its ⋯ menu button.")
    }

    // WEB-044, DRF-061, DRF-065 (owner playtest 2026-11-15): in the owner's 1920 × 919 window the marks on the pack cards (their
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
            "Alice's Glimpse pick in a 1920 × 919 window, as the owner played it: a click kept Mossback Cub and a right-click burned Mercy Vow, their borders, ✓ and 🔥 badges and the trays' count chips drawn half as large again as the phone's; the pointer rests on Mossback Cub, its ⋯ under the badge.")
    }

    private fun statsDetail(): PodDetail {
        val main = (0..15).map { copy(it) } + copy(9, 1)
        val build = PodDeckBuild(main.map { PodDeckCopy(it.id) } + listOf(PodDeckCopy(copy(16).id, PodDeckCategory.SIDE)),
            PodDeckBasics(plains = 6, mountain = 5, forest = 6))
        return detail(PodPhase.DRAFTED, DraftSettings.booster(), listOf(PodMember(alice, building = true),
            PodMember(bob, submitted = true), PodMember(carol, building = true))).copy(
            draft = DraftPrivateView("alice", null, 0, main + copy(16)), build = build)
    }

    // WEB-060, DRF-111, DRF-114, DRF-117, DRF-118 (owner playtest 2026-11-15): the pod's Stats on the desktop layout keeps
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
        inside("pod-stats-donut-Colors", right)
        inside("pod-stats-notes", right)
        inside("pod-stats-counter-7", right)
        captureShot("$SCREENSHOTS/desktop/stats.png",
            "Alice's Stats after a Booster draft on the desktop layout at 1440 × 900, with notes and counters she set: at Settings' readable width, the curve with its Type | Color toggle, the counts, type rows and pips and sources in the left column, the colours donut, notes and tally counters in the right.")
    }

    // WEB-060: a desktop window too narrow for two columns shows Stats in one, as the phone does
    @Test @Config(qualifiers = "w700dp-h500dp-land-mdpi") fun statsNarrow() {
        val detail = statsDetail()
        show(PodState(loaded = true, selected = detail.id, detail = detail, tab = PodTab.STATS))
        compose.onNodeWithTag("pod-stats").assertIsDisplayed()
        compose.onNodeWithTag("pod-stats-left").assertDoesNotExist()
        compose.onNodeWithTag("pod-stats-curve").assertIsDisplayed()
    }

    // WEB-045 (D8, D9): Build with the pointer on a Main card's right quarter: "Maybe ▸" tinted strongly, "◂ Side" faintly
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
        compose.onNodeWithTag("pod-build-card-c15-0").performMouseInput { moveTo(Offset(width * .88f, height * .6f)) }
        compose.waitForIdle()
        compose.onNodeWithTag("edge-zone-right-active").assertIsDisplayed()
        captureShot("$SCREENSHOTS/desktop/build-edge-zones.png",
            "Alice's Build on the desktop layout with the pointer on Brass Courier's right quarter: that quarter is tinted Maybe's amber with \"Maybe ▸\" and the left one faintly Side's blue with \"◂ Side\", where a click moves one copy; the section headers have no Review, as Build has no one-by-one review on the desktop.")
    }
}
