package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.asyncttwiz.appui.AppPlatform
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
import kotlin.test.assertEquals

/**
 * Card ratings in every draft mode's history dialog for the owner's gallery (owner, 2026-11-25; DRF-315, DRF-316, DRF-317):
 * each mode's dialog after Alice's own draft, "By rating" off and on, the rating chips' colour options, and a dialog before
 * her draft is done. Invented cards and ratings; nothing is sent.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-xhdpi")
class DraftHistoryRatingsScreenshotTest {
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
    private val controllers = mutableListOf<PodController>()

    private val preferences get() = AppPlatform.current.preferences("pod-pick")
    @Before @After fun reset() {
        controllers.forEach { it.close() }
        controllers.clear()
        preferences.edit().remove("history-by-rating").apply()
    }

    private val alice = AccountIdentity("alice", "Alice")
    private val bob = AccountIdentity("bob", "Bob")
    private val carol = AccountIdentity("carol", "Carol")

    /** Invented cards: name, mana cost, type line. */
    private val cards = listOf(
        Triple("Dawnward Sentry", "{1}{W}", "Creature — Human Soldier"), Triple("Lantern Oath", "{W}", "Instant"),
        Triple("Skyhall Lancer", "{3}{W}", "Creature — Bird Knight"), Triple("Mercy Vow", "{2}{W}", "Enchantment — Aura"),
        Triple("Gleamfield Warden", "{4}{W}{W}", "Creature — Angel"), Triple("Tidecall Adept", "{1}{U}", "Creature — Merfolk Wizard"),
        Triple("Mistweave", "{U}{U}", "Instant"), Triple("Archive Drake", "{3}{U}", "Creature — Drake"),
        Triple("Second Glance", "{U}", "Sorcery"), Triple("Fogbound Sphinx", "{4}{U}{U}", "Creature — Sphinx"),
        Triple("Gravel Ghoul", "{1}{B}", "Creature — Zombie"), Triple("Bitter Harvest", "{B}", "Instant"),
        Triple("Duskmarket Thief", "{2}{B}", "Creature — Human Rogue"), Triple("Hollow Pact", "{1}{B}{B}", "Sorcery"),
        Triple("Ashen Revenant", "{4}{B}", "Creature — Spirit"), Triple("Cinder Whelp", "{R}", "Creature — Dragon"),
        Triple("Spark Lash", "{R}", "Instant"), Triple("Forgeborn Brute", "{2}{R}", "Creature — Giant"),
        Triple("Molten Verdict", "{X}{R}", "Sorcery"), Triple("Emberpeak Tyrant", "{4}{R}{R}", "Creature — Dragon"),
        Triple("Mossback Cub", "{G}", "Creature — Bear"), Triple("Rootsurge", "{G}", "Instant"),
        Triple("Thornwood Ranger", "{2}{G}", "Creature — Elf Ranger"), Triple("Wildbloom Rite", "{2}{G}", "Sorcery"),
        Triple("Canopy Colossus", "{5}{G}", "Creature — Treefolk"), Triple("Stormtide Pact", "{U}{R}", "Instant"),
        Triple("Verdant Paladin", "{G}{W}", "Creature — Human Knight"), Triple("Gloomroot Witch", "{B}{G}", "Creature — Elf Warlock"),
        Triple("Brass Courier", "{2}", "Artifact Creature — Construct"), Triple("Waystone Idol", "{3}", "Artifact"),
        Triple("Sunken Causeway", "", "Land"), Triple("Ridgeback Crossing", "", "Land"),
    )
    private fun digest(n: Int) = "def-$n"
    private val submitted = cards.mapIndexed { n, (name, cost, type) ->
        SubmittedDefinition(digest(n), CardDefinition(name, listOf(DefinitionFace("face", name, typeLine = type, manaCost = cost,
            scryfallId = placeholderPrinting(name)))))
    }
    private val definitions = submitted.associate { it.digest to it.definition }

    /** Invented GIH WRs (tenths of a percent) of the first 30 cards, 48.0 % to 63.9 %; Brass Courier (28) and the lands have none. */
    private val table = cards.take(30).withIndex().filter { it.index != 28 }.associate { (n, card) -> card.first to 480 + (n * 53) % 160 }
    private val ratings = PodHistoryRatings(DraftBotRatings("TST", table))
    private val ratedSet = PodSource(kind = PodSourceKind.SET, setCode = "TST", setName = "Test Set",
        boosterKind = SetBoosterKind.RECIPE_BASED, boosterName = "play", packSets = listOf("TST"), botRatings = true)

    /** Copy [copy] of card [n]. */
    private fun copy(n: Int, copy: Int = 0) = DraftCopy("c$n-$copy", digest(n))
    private fun copies(vararg n: Int) = n.map { copy(it) }

    private fun settle() {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    private fun shot(name: String, caption: String) = captureShot("$SCREENSHOTS/draft/$name.png", caption)

    private fun showContent(content: @Composable () -> Unit) {
        compose.setContent { ScreenTheme { NoRipples { Surface(Modifier.fillMaxSize(), color = ScreenBackground) { content() } } } }
        settle()
    }

    private fun history(picks: List<DraftHistoryPick>, columns: Int = 5, seating: List<String> = listOf("alice", "bob", "carol"),
        arena: Pair<DraftSettings, PodSourceKind>? = null, shown: PodHistoryRatings? = ratings) = showContent {
        PodHistoryDialog("pod", PodDraftHistory(picks, seating = seating), false, definitions, columns, listOf(alice, bob, carol),
            "alice", {}, arena = arena, ratings = shown) {}
    }

    private fun picksDialog(picks: List<PodOpponentPicks>, palette: PodRatingPalette = PodRatingPalette.GoldToGrey) = showContent {
        CompositionLocalProvider(LocalPodRatingPalette provides palette) { PodPicksDialog(picks, definitions, ratings) {} }
    }

    private fun rating(n: Int) = table.getValue(cards[n].first)
    private fun chipText(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).onChild().fetchSemanticsNode()
        .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }

    /** The picks of a three-player Booster draft: Alice's first two picks of pack 1 and the pack that came back to her. */
    private val boosterPicks = listOf(
        DraftHistoryPick(1, 1, copies(0, 3, 5, 9, 12, 15, 19, 22, 26, 30), kept = listOf("c19-0")),
        DraftHistoryPick(1, 2, copies(1, 6, 10, 14, 17, 21, 25, 31), kept = listOf("c6-0")),
        DraftHistoryPick(1, 4, copies(0, 5, 12, 26, 30), kept = listOf("c26-0"), takenSince = copies(3, 9, 15, 22), returning = true))

    // DRF-315, DRF-317, DRF-316 (owner, 2026-11-25): Booster's draft history after Alice's own draft in a pod of a set with
    // ratings: each rated card's chip, the citation heading the list and "By rating" beside "Returning packs only"
    @Test fun boosterHistoryWithRatings() {
        history(boosterPicks, columns = 8)
        compose.onNodeWithTag("pod-ratings-citation").assertTextEquals("Ratings: 17Lands")
        assertEquals(podRatingText(rating(19)), chipText("pod-history-0-c19-0-rating"))
        compose.onNodeWithTag("pod-history-0-c30-0-rating", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("pod-history-by-rating").assertIsNotSelected()
        shot("history-ratings-booster", "Booster draft history of Alice's Test Set pod after her own draft, eight cards a row (the default): each rated card has " +
            "its win rate on a chip at its bottom edge, gold for the set's best down to grey, the land none; \"Ratings: 17Lands\" " +
            "heads the list and By rating sits beside Returning packs only on a row under the title.")
    }

    // DRF-316 (owner, 2026-11-25): By rating sorts each pick's pack best first, the keep marked wherever it lands, and is
    // remembered on the device
    @Test fun boosterHistoryByRating() {
        history(boosterPicks, columns = 8)
        compose.onNodeWithTag("pod-history-by-rating").performClick()
        settle()
        compose.onNodeWithTag("pod-history-by-rating").assertIsSelected()
        val first = listOf(0, 3, 5, 9, 12, 15, 19, 22, 26, 30)
        val best = first.filter { cards[it].first in table }.sortedByDescending(::rating) + first.filter { cards[it].first !in table }
        val shownOrder = compose.onAllNodes(hasTestTagPrefix("pod-history-0-c"), useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] }
            .filterNot { it.endsWith("-rating") }
        assertEquals(best.map { "pod-history-0-c$it-0" }, shownOrder)
        shot("history-ratings-booster-by-rating", "The same Booster draft history after tapping By rating: each pack lists its " +
            "cards best first, the land without a rating last, and Alice's keep keeps its green border and check wherever it lands.")
        // Remembered on the device, for every pod and dialog
        kotlin.test.assertTrue(preferences.getBoolean("history-by-rating", false))
    }

    // DRF-315, DRF-316: the cards taken since a pack came back get their chips and sort too (choice)
    @Test fun takenSinceCardsHaveRatings() {
        preferences.edit().putBoolean("history-by-rating", true).apply()
        history(boosterPicks, columns = 5)
        compose.onNodeWithTag("pod-history-picks").performScrollToKey("since-2")
        settle()
        assertEquals(podRatingText(rating(22)), chipText("pod-history-2-c22-0-rating"))
        shot("history-ratings-taken-since", "Booster draft history with By rating on, scrolled to the pack that came back to Alice: " +
            "the greyed cards taken since she passed it also carry their chips and are sorted best first (choice).")
    }

    // DRF-315: Glimpse's draft history: keeps and burns marked, every rated card with its chip
    @Test fun glimpseHistoryWithRatings() {
        history(listOf(
            DraftHistoryPick(1, 1, copies(2, 4, 7, 11, 13, 16, 18, 20, 23, 27, 29), kept = listOf("c7-0"), burned = listOf("c4-0", "c18-0")),
            DraftHistoryPick(1, 2, copies(1, 8, 10, 14, 24, 25, 28, 31), kept = listOf("c24-0"), burned = listOf("c25-0", "c1-0"))))
        assertEquals(podRatingText(rating(4)), chipText("pod-history-0-c4-0-rating"))
        shot("history-ratings-glimpse", "Glimpse draft history (keep 1, burn 2) of Alice's Test Set pod after her draft: her keep " +
            "green, her burns red with their flames, and every rated card's win rate chip; Brass Courier and the land have no rating.")
    }

    // DRF-315: Custom booster's draft history, a pass picking two and burning one
    @Test fun customBoosterHistoryWithRatings() {
        history(listOf(
            DraftHistoryPick(1, 1, copies(0, 6, 9, 13, 17, 20, 24, 27, 30), kept = listOf("c9-0", "c17-0"), burned = listOf("c27-0")),
            DraftHistoryPick(1, 2, copies(3, 5, 11, 19, 22, 26, 31), kept = listOf("c5-0", "c22-0"), burned = listOf("c3-0"))))
        compose.onNodeWithTag("pod-ratings-citation").assertExists()
        shot("history-ratings-custom-booster", "Custom booster draft history (pass 1: pick 2, burn 1) of Alice's Test Set pod after " +
            "her draft, with the citation at the top and a win rate chip on every rated card.")
    }

    // DRF-315, DRF-289: Arena's draft history: no seat ring and no Returning packs only, so By rating sits in the title row
    @Test fun arenaHistoryWithRatings() {
        history(listOf(
            DraftHistoryPick(1, 1, copies(4, 9, 14, 19, 24), kept = listOf("c19-0"), round = 1),
            DraftHistoryPick(1, 1, copies(1, 6, 12, 21, 27), kept = listOf("c12-0"), round = 2),
            DraftHistoryPick(1, 1, copies(3, 8, 16, 23, 29), kept = listOf("c3-0"), round = 3)),
            arena = DraftSettings.arena() to PodSourceKind.SET)
        compose.onNodeWithTag("pod-history-returning").assertDoesNotExist()
        compose.onNodeWithTag("pod-history-by-rating").assertIsDisplayed()
        shot("history-ratings-arena", "Arena draft history of Alice's Test Set pod after her last pick: each offer under its pick " +
            "line with her pick marked and a win rate chip on every card, By rating in the title row.")
    }

    private val winstonPicks = listOf(PodOpponentPicks(bob, listOf(copy(7), null, copy(15), copy(28), null, copy(2), copy(21), null)),
        PodOpponentPicks(carol, listOf(copy(9), copy(13))))

    // DRF-315, DRF-227: Winston's Picks: only the cards Alice saw face up have chips, card backs none
    @Test fun winstonPicksWithRatings() {
        picksDialog(winstonPicks)
        compose.onNodeWithTag("pod-ratings-citation").assertExists()
        compose.onNodeWithTag("pod-picks-c7-0-rating", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("pod-picks-c28-0-rating", useUnmergedTree = true).assertDoesNotExist()
        shot("history-ratings-winston", "Winston Picks of Bob after Alice's own draft in a Test Set pod: the cards she saw face up " +
            "carry their win rate chips, the card backs none, under \"Ratings: 17Lands\" with By rating in the title row.")
    }

    // DRF-316: By rating in Picks: best first, unrated cards and card backs after in their order
    @Test fun picksByRating() {
        picksDialog(winstonPicks)
        compose.onNodeWithTag("pod-history-by-rating").performClick()
        settle()
        compose.onNodeWithTag("pod-history-by-rating").assertIsSelected()
        shot("history-ratings-picks-by-rating", "The same Winston Picks after tapping By rating: Bob's cards best first, then " +
            "Brass Courier without a rating and the card backs in their order.")
    }

    // DRF-315, DRF-160: Winchester's Picks opened from the History icon of the pod after the draft, the ratings read from the
    // card records as the bots read them
    @Test fun winchesterPicksFromThePod() {
        showPod(winchester(PodPhase.DRAFTED))
        compose.onNodeWithTag("pod-picks").performClick()
        settle()
        compose.onNodeWithTag("pod-ratings-citation").assertExists()
        compose.onNodeWithTag("pod-picks-c13-0-rating", useUnmergedTree = true).assertExists()
        shot("history-ratings-winchester", "Alice opens History in her drafted Winchester pod of Test Set: Bob's piles' cards " +
            "newest first, each rated card with its win rate chip, the citation above them and By rating in the title row.")
    }

    // DRF-315 (owner, 2026-11-25): before her own draft is done the dialog shows no ratings, no citation and no By rating (choice:
    // the chip shows only with ratings)
    @Test fun noRatingsBeforeYourDraftIsDone() {
        showPod(winchester(PodPhase.DRAFTING))
        compose.onNodeWithTag("pod-picks").performClick()
        settle()
        compose.onNodeWithTag("pod-picks-c13-0").assertExists()
        compose.onNodeWithTag("pod-ratings-citation").assertDoesNotExist()
        compose.onNodeWithTag("pod-picks-c13-0-rating", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("pod-history-by-rating").assertDoesNotExist()
        shot("history-ratings-before-done", "Alice opens History while her Winchester draft of Test Set still runs: Bob's picks " +
            "without win rates, citation or By rating, which show only once her own draft is done (choice: no By rating before).")
    }

    // DRF-315: Grid's Picks: each opponent's lines, all seen face up
    @Test fun gridPicksWithRatings() {
        picksDialog(listOf(PodOpponentPicks(bob, copies(4, 11, 18, 0, 7, 14)), PodOpponentPicks(carol, copies(22, 25, 29))))
        shot("history-ratings-grid", "Grid Picks of Bob after Alice's own draft in a Test Set pod: the two lines he took, newest " +
            "first, every rated card with its win rate chip.")
    }

    // DRF-315: Rochester's Picks
    @Test fun rochesterPicksWithRatings() {
        picksDialog(listOf(PodOpponentPicks(carol, copies(20, 3, 16, 9, 27, 12, 5)), PodOpponentPicks(bob, copies(1, 8))))
        shot("history-ratings-rochester", "Rochester Picks of Carol after Alice's own draft in a Test Set pod: her picks newest " +
            "first with their win rate chips.")
    }

    // DRF-315: Rotisserie's Picks
    @Test fun rotisseriePicksWithRatings() {
        picksDialog(listOf(PodOpponentPicks(bob, copies(17, 6, 23, 10, 29, 2, 19, 13, 26)), PodOpponentPicks(carol, copies(0, 24))))
        shot("history-ratings-rotisserie", "Rotisserie Picks of Bob after Alice's own draft in a Test Set pod: his nine picks newest " +
            "first, each with its win rate chip coloured by its rank in the set.")
    }

    /** Twelve picks covering all five steps, best first: two top, two high, four middle, two low, two bottom. */
    private val stepPicks = listOf(PodOpponentPicks(bob, run {
        val rated = (0..29).filter { cards[it].first in table }.sortedByDescending(::rating)
        val steps = rated.groupBy { ratings.step(rating(it)) }
        listOf(PodRatingStep.Top to 2, PodRatingStep.High to 2, PodRatingStep.Middle to 4, PodRatingStep.Low to 2, PodRatingStep.Bottom to 2)
            .flatMap { (step, count) -> steps.getValue(step).take(count) }.map { copy(it) }
    }))

    // DRF-315 (owner, 2026-11-25): the chips' colour options for the owner: the five steps in each palette, never green or red
    @Test fun colourOptionGoldToGrey() = colourOption(PodRatingPalette.GoldToGrey, "rating-colours-gold-to-grey",
        "the default: gold, pale gold, light grey, grey and dark grey")
    @Test fun colourOptionMedals() = colourOption(PodRatingPalette.Medals, "rating-colours-medals",
        "option 2, medals: gold, silver, bronze, grey and dark grey")
    @Test fun colourOptionGoldVioletGrey() = colourOption(PodRatingPalette.GoldVioletGrey, "rating-colours-gold-violet-grey",
        "option 3: gold, violet, light grey, grey and dark grey")

    private fun colourOption(palette: PodRatingPalette, name: String, words: String) {
        preferences.edit().putBoolean("history-by-rating", true).apply()
        picksDialog(stepPicks, palette)
        shot(name, "Rotisserie Picks sorted By rating with two cards of each step (top 10%, next 15%, four of the middle, " +
            "next 15%, bottom 10%), the chips in $words.")
    }

    // DRF-316: on a wider screen the title, By rating and Returning packs only share one row (choice)
    @Test @Config(qualifiers = "w892dp-h412dp-land-xhdpi") fun landscapeHistoryHeaderInOneRow() {
        history(boosterPicks, columns = 10)
        val title = compose.onNodeWithText("Draft history").fetchSemanticsNode().boundsInRoot
        val chip = compose.onNodeWithTag("pod-history-by-rating").fetchSemanticsNode().boundsInRoot
        kotlin.test.assertTrue(chip.top < title.bottom && chip.bottom > title.top, "By rating in the title's row: $chip, $title")
        shot("history-ratings-landscape", "Booster draft history in landscape after Alice's draft: the title, By rating and " +
            "Returning packs only share one row (choice), the citation heads the list and every rated card has its chip.")
    }

    private fun winchester(phase: PodPhase): PodDetail {
        val members = listOf(PodMember(alice), PodMember(bob))
        return PodDetail("pod", "Winchester night", phase, alice, DraftSettings.winchester(), ratedSet, DraftSupply(0, Long.MAX_VALUE),
            members, emptyList(), draftAttemptId = "attempt", definitions = submitted, updatedAt = "2026-11-25T00:00:00Z",
            draftWait = PodDraftWait.Picks.takeIf { phase == PodPhase.DRAFTING },
            limited = LimitedView(DraftMode.WINCHESTER, "alice", listOf("alice", "bob"), copies(3, 8), phase == PodPhase.DRAFTED,
                picks = listOf(LimitedPickView("alice", copies(3), 1), LimitedPickView("bob", copies(13, 18), 2),
                    LimitedPickView("alice", copies(8), 0), LimitedPickView("bob", copies(5, 22, 27), 1))))
    }

    private fun showPod(detail: PodDetail) {
        val filters = PodCardFilters({ emptyMap() }, readRatings = { code -> if (code == "TST") table else emptyMap() })
        val pods = PodController(unused<Pods>(), unused<Cubes>(), { "token" }, worker, { it() }, filters = filters).also { controllers += it }
        pods.seed(PodState(loaded = true, selected = detail.id, detail = detail, tab = PodTab.BUILD))
        showContent { PodsDestination(pods, alice.accountId, {}, null, {}, mainMenu = {}) }
    }

    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("test tag starts with $prefix") { node ->
        node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.startsWith(prefix) == true
    }
}
