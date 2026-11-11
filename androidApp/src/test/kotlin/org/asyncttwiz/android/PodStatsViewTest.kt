package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PodStatsViewTest {
    @get:Rule val compose = createComposeRule()

    // DRF-114
    @Test fun manaTableShowsNumbersAndPipPatternsWithTheColorsDonutAndStackedCurve() {
        val card = CardDefinition("Hybrid", listOf(DefinitionFace("front", "Hybrid", typeLine = "Creature", manaCost = "{W/U}{B/P}")))
        compose.setContent { MaterialTheme {
            PodStatsView(PodDeckBuild(listOf(PodDeckCopy("copy")), PodDeckBasics(plains = 39)),
                mapOf("copy" to DraftCopy("copy", "digest")), mapOf("digest" to card))
        } }
        compose.onNodeWithText("Main including basics").assertDoesNotExist()
        compose.onNodeWithTag("pod-stats-curve").assertIsDisplayed()
            .assert(hasContentDescription("2: 1 creature, 0 noncreature", substring = true))
        compose.onNodeWithText("2: 1").assertDoesNotExist()
        compose.onNodeWithText("Creature: 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Noncreature: 0").assertIsDisplayed()
        compose.onNodeWithText("Land: 39").assertIsDisplayed()
        compose.onNodeWithText("Pips and sources").assertIsDisplayed()
        compose.onNodeWithTag("pod-stats-sources").assertTextEquals("Sources: 39 lands, 0 others")
        compose.onNodeWithTag("pod-stats-mana-W").assert(hasText("½ pip · 25%")).assert(hasText("39 sources · 100%"))
            .assert(hasContentDescription("W: 25% of pips, 100% of sources"))
        compose.onNodeWithTag("pod-stats-mana-B").assert(hasText("1 pip · 50%")).assert(hasText("0 sources · 0%"))
            .assert(hasContentDescription("B: 50% of pips, 0% of sources"))
        compose.onNodeWithTag("pod-stats-mana-R").assert(hasText("0 pips · 0%")).assert(hasText("0 sources · 0%"))
        compose.onNodeWithTag("pod-stats-pattern-0").assertTextEquals("1× {W/U}{B/P}")
        compose.onNodeWithTag("pod-stats-no-pips").assertDoesNotExist()
        compose.onNodeWithText("Lands without a color", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("pod-stats-donut-Pips").assertDoesNotExist()
        compose.onNodeWithTag("pod-stats-donut-Sources").assertDoesNotExist()
        compose.onNodeWithTag("pod-stats-donut-Colors").performScrollTo().assertIsDisplayed()
            .assert(hasContentDescription("Multicolor 100%", substring = true))
        compose.onAllNodesWithTag("pod-color-bar").assertCountEquals(0)
        compose.onNodeWithText("Mana sources · approximate").assertDoesNotExist()
    }

    /** The app's check, parsing each card's rules text as the device does (without its cache). */
    private val parsing = CardListSupportCheck({ cards -> cards.associate { card ->
        val parsed = org.asyncttwiz.protocol.game.GameCards.parse(card.definition)
        card.digest to (parsed.definition?.let { org.asyncttwiz.storage.ParsedCard.Read(it, parsed.manualAbilities) }
            ?: org.asyncttwiz.storage.ParsedCard.Unreadable(parsed.problem.orEmpty()))
    } }, { emptySet() })

    private fun card(name: String, type: String, cost: String, text: String = "") =
        CardDefinition(name, listOf(DefinitionFace("front", name, typeLine = type, manaCost = cost, rulesText = text)))

    /** A 23-card build plus 15 basics, as the app reads it: a green-white deck with a little red, a mana rock and two duals. */
    private fun showDeck() {
        val definitions = mapOf(
            "bear" to card("Bear", "Creature — Bear", "{1}{G}"), "golem" to card("Golem", "Artifact Creature — Golem", "{4}"),
            "hero" to card("Hero", "Legendary Creature — Human Knight", "{1}{W}{W}"), "bolt" to card("Bolt", "Instant", "{R}"),
            "growth" to card("Growth", "Instant", "{G}"), "rampant" to card("Rampant", "Sorcery", "{2}{G}"),
            "aura" to card("Aura", "Enchantment — Aura", "{1}{W}"), "ring" to card("Ring", "Artifact", "{2}", "{T}: Add one mana of any color."),
            "walker" to card("Walker", "Legendary Planeswalker — Ajani", "{2}{G}{W}"),
            "giant" to card("Giant", "Creature — Giant // Sorcery — Adventure", "{4}{R}"),
            "gate" to card("Gate", "Land — Gate", "", "{T}: Add {R} or {G}."),
        )
        val counts = mapOf("bear" to 5, "golem" to 2, "hero" to 3, "bolt" to 2, "growth" to 2, "rampant" to 1, "aura" to 2,
            "ring" to 1, "walker" to 1, "giant" to 2, "gate" to 2)
        val copies = counts.flatMap { (digest, n) -> List(n) { DraftCopy("$digest-$it", digest) } }.associateBy { it.id }
        compose.setContent { MaterialTheme { androidx.compose.runtime.CompositionLocalProvider(LocalCardListSupport provides parsing) {
            PodStatsView(PodDeckBuild(copies.keys.map { PodDeckCopy(it) }, PodDeckBasics(plains = 7, forest = 8)), copies, definitions)
        } } }
        // The sources once the cards' mana abilities are known.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Sources: 17 lands, 1 other").fetchSemanticsNodes().isNotEmpty() }
    }

    // DRF-112, DRF-113, DRF-114, DRF-115, DRF-116
    @Test
    @Config(qualifiers = "w412dp-h892dp-port-xhdpi")
    fun typeCountsWrapBelowTheLegendAndAboveTheManaTable() {
        showDeck()
        // Lands and the Ring (any colour) added up, the Gate counting for both its colours.
        compose.onNodeWithTag("pod-stats-mana-W").assert(hasText("9 pips · 41%")).assert(hasText("8 sources · 33%"))
        compose.onNodeWithTag("pod-stats-mana-G").assert(hasText("9 pips · 41%")).assert(hasText("11 sources · 46%"))
        compose.onNodeWithTag("pod-stats-mana-R").assert(hasText("4 pips · 18%")).assert(hasText("3 sources · 13%"))
        compose.onNodeWithTag("pod-stats-type-Creature").assertTextEquals("Creature 12").assertIsDisplayed()
        compose.onNodeWithTag("pod-stats-type-Land").assertTextEquals("Land 17")
        compose.onNodeWithTag("pod-stats-type-Basic").assertTextEquals("Basic 15")
        compose.onNodeWithTag("pod-stats-type-Legendary").assertTextEquals("Legendary 4")
        compose.onNodeWithTag("pod-stats-type-Artifact").assertTextEquals("Artifact 3")
        compose.onNodeWithTag("pod-stats-type-Sorcery").assertTextEquals("Sorcery 1")
        compose.onNodeWithTag("pod-stats-type-Golem").assertDoesNotExist()
        compose.onNodeWithTag("pod-stats-type-Tribal").assertDoesNotExist()
        val chips = compose.onAllNodes(SemanticsMatcher("type chip") { node ->
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("pod-stats-type-") == true
        }).fetchSemanticsNodes()
        assertEquals(listOf("Land", "Basic", "Creature", "Instant", "Legendary", "Artifact", "Enchantment", "Planeswalker", "Sorcery"),
            chips.map { it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].removePrefix("pod-stats-type-") })
        val legend = compose.onNodeWithText("Creature: 12").getUnclippedBoundsInRoot()
        val types = compose.onNodeWithTag("pod-stats-types").getUnclippedBoundsInRoot()
        val mana = compose.onNodeWithTag("pod-stats-mana").getUnclippedBoundsInRoot()
        assertTrue(legend.bottom <= types.top && types.bottom <= mana.top)
        assertTrue(types.bottom - types.top < 80.dp, "Nine types wrap into at most a few rows: ${types.bottom - types.top}")
        // DRF-116: the pip counts flow in rows in their order, the "N× ∅" chip last.
        val first = compose.onNodeWithTag("pod-stats-pattern-0", useUnmergedTree = true).assertTextEquals("8× {G}").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("pod-stats-pattern-1", useUnmergedTree = true).assertTextEquals("4× {R}").getUnclippedBoundsInRoot()
        assertEquals(first.top, second.top)
        assertTrue(first.right < second.left)
        compose.onNodeWithTag("pod-stats-no-pips", useUnmergedTree = true).assertTextEquals("3× ∅")
        val output = java.io.File("$SCREENSHOTS/pod-stats-types.png").also { it.parentFile!!.mkdirs() }
        compose.onRoot().captureShot(output.path, "Draft deck Stats in portrait for a 23-card build plus 15 basics, the curve " +
            "on its Type view, each bar's number reading creatures · noncreatures (\"5 · 3\"): card type counts wrap between the curve legend and the mana table, each colour's pips and sources with their " +
            "shares in percent beside a thin pip bar over a source bar, full width being 100% (lands solid, a mana rock lighter), then the sources' total " +
            "line and the pip counts as chips flowing in rows.")
    }

    // DRF-111
    @Test
    @Config(qualifiers = "w412dp-h892dp-port-xhdpi")
    fun curveToggleStacksByColorKeepingTheChartAndRemembersTheChoice() {
        showDeck()
        val preferences = org.asyncttwiz.appui.AppPlatform.current.preferences("pod-build")
        compose.onNodeWithTag("pod-stats-curve-type").assertIsSelected()
        compose.onNodeWithTag("pod-stats-curve-color").assertIsNotSelected()
        compose.onNodeWithTag("pod-stats-curve-legend").assertDoesNotExist()
        val typeChart = compose.onNodeWithTag("pod-stats-curve").assert(hasContentDescription("2: 5 creature, 3 noncreature", substring = true))
            .getUnclippedBoundsInRoot()
        compose.onNodeWithTag("pod-stats-curve-color").performClick()
        compose.onNodeWithTag("pod-stats-curve-color").assertIsSelected()
        compose.onNodeWithTag("pod-stats-curve-type").assertIsNotSelected()
        assertEquals(typeChart, compose.onNodeWithTag("pod-stats-curve")
            .assert(hasContentDescription("2: 2 W, 5 G, 1 Colorless", substring = true)).assert(hasContentDescription("0: none", substring = true))
            .getUnclippedBoundsInRoot())
        compose.onNodeWithTag("pod-stats-curve").assert(hasContentDescription("4: 1 Gold, 2 Colorless", substring = true))
        for (name in listOf("W", "U", "B", "R", "G", "Gold", "Colorless"))
            compose.onNode(hasText(name) and hasAnyAncestor(hasTestTag("pod-stats-curve-legend"))).assertIsDisplayed()
        // The counts stay; their Type swatches would mean nothing here.
        compose.onNodeWithText("Creature: 12").assertIsDisplayed()
        // Every legend entry on one line at phone width.
        assertTrue(with(compose.onNodeWithTag("pod-stats-curve-legend").getUnclippedBoundsInRoot()) { bottom - top } < 24.dp)
        compose.onRoot().captureShot("$SCREENSHOTS/pod-stats-color.png", "Draft deck Stats after tapping Color above the mana " +
            "curve: the same chart and totals with the bars stacked W, U, B, R, G, gold and colorless in the donut's colours, " +
            "a legend of those colours above the Creature, Noncreature and Land counts.")
        assertTrue(PodStatsCurveChoice(preferences).byColor, "the choice stays on this device")
        compose.onNodeWithTag("pod-stats-curve-type").performClick()
        compose.onNodeWithTag("pod-stats-curve-legend").assertDoesNotExist()
        assertFalse(PodStatsCurveChoice(preferences).byColor)
    }

    // DRF-111 (owner playtest 2026-11-09): in Type view a bar's number reads "creatures · noncreatures"; in Color view its total
    @Test fun aTypeBarReadsCreaturesAndNoncreaturesAColorBarItsTotal() {
        assertEquals("5 · 3", podStatsCurveBarLabel(listOf(5, 3), byColor = false))
        assertEquals("0 · 1", podStatsCurveBarLabel(listOf(0, 1), byColor = false))
        assertEquals("8", podStatsCurveBarLabel(listOf(2, 0, 0, 0, 5, 0, 1), byColor = true))
    }

    // DRF-113
    @Test
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun emptyStatsRenderZeroValuesInLandscape() {
        compose.setContent { MaterialTheme { PodStatsView(PodDeckBuild(), emptyMap(), emptyMap()) } }
        compose.onNodeWithTag("pod-stats-curve").assertIsDisplayed()
        compose.onNodeWithText("0: 0").assertDoesNotExist()
        compose.onNodeWithTag("pod-stats-donut-Colors").performScrollTo().assertIsDisplayed()
        val table = compose.onNodeWithTag("pod-stats-mana").getUnclippedBoundsInRoot()
        val colors = compose.onNodeWithTag("pod-stats-donut-Colors")
            .assert(hasContentDescription("W 0%, U 0%, B 0%, R 0%, G 0%", substring = true)).getUnclippedBoundsInRoot()
        assertEquals(table.top, colors.top)
        assertTrue(table.right < colors.left)
        for (color in DraftColor.entries) compose.onNodeWithTag("pod-stats-mana-${color.name}")
            .assert(hasText("0 pips · 0%")).assert(hasText("0 sources · 0%"))
        compose.onNodeWithTag("pod-stats-pattern-0").assertDoesNotExist()
        compose.onNodeWithTag("pod-stats-no-pips").assertDoesNotExist()
        compose.onNodeWithTag("pod-stats-notes").assertDoesNotExist()
        compose.onNodeWithTag("pod-stats-types").assertDoesNotExist()
    }

    @Test fun chartBoundsStayStableWhenValuesRefreshInLightAndDarkThemes() {
        val dark = mutableStateOf(false)
        // Starts non-empty: the type counts row only appears once the Main deck has a card.
        val build = mutableStateOf(PodDeckBuild(basics = PodDeckBasics(forest = 1)))
        compose.setContent { MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
            PodStatsView(build.value, emptyMap(), emptyMap())
        } }
        val before = compose.onNodeWithTag("pod-stats-mana").getUnclippedBoundsInRoot()
        compose.runOnIdle { build.value = PodDeckBuild(basics = PodDeckBasics(forest = 17)); dark.value = true }
        compose.onNodeWithTag("pod-stats-mana-G").assert(hasText("17 sources · 100%")).assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("pod-stats-mana").getUnclippedBoundsInRoot())
    }

    // DRF-114: shares of the whole, nothing scaled to the largest: 3 Forests and 1 Island read 75% and 25%, and a colour
    // with a share of 0 keeps its row (the bars themselves show in pod-stats-types.png)
    @Test fun sharesAddUpToTheWholeAndAColourWithNoneKeepsItsRow() {
        compose.setContent { MaterialTheme { PodStatsView(PodDeckBuild(basics = PodDeckBasics(forest = 3, island = 1)), emptyMap(), emptyMap()) } }
        compose.onNodeWithTag("pod-stats-mana-G").assert(hasText("3 sources · 75%")).assert(hasContentDescription("G: 0% of pips, 75% of sources"))
        compose.onNodeWithTag("pod-stats-mana-U").assert(hasText("1 source · 25%")).assert(hasContentDescription("U: 0% of pips, 25% of sources"))
        compose.onNodeWithTag("pod-stats-mana-W").assert(hasText("0 pips · 0%")).assert(hasText("0 sources · 0%")).assertIsDisplayed()
    }

    // DRF-114, DRF-116
    @Test fun pipListEndsWithCardsWithoutColoredPipsAndColorlessLandsAreNamed() {
        val spell = CardDefinition("Spell", listOf(DefinitionFace("front", "Spell", typeLine = "Instant", manaCost = "{1}{U}")))
        val rock = CardDefinition("Rock", listOf(DefinitionFace("front", "Rock", typeLine = "Artifact", manaCost = "{2}")))
        val waste = CardDefinition("Waste", listOf(DefinitionFace("front", "Waste", typeLine = "Land", rulesText = "{T}: Add {C}.")))
        compose.setContent { MaterialTheme {
            PodStatsView(PodDeckBuild(listOf(PodDeckCopy("a"), PodDeckCopy("b"), PodDeckCopy("c"), PodDeckCopy("d"))),
                mapOf("a" to DraftCopy("a", "spell"), "b" to DraftCopy("b", "spell"), "c" to DraftCopy("c", "rock"),
                    "d" to DraftCopy("d", "waste")), mapOf("spell" to spell, "rock" to rock, "waste" to waste))
        } }
        compose.onNodeWithTag("pod-stats-mana-U").assert(hasText("2 pips · 100%"))
        compose.onNodeWithTag("pod-stats-pattern-0").assertTextEquals("2× {U}")
        compose.onNodeWithTag("pod-stats-no-pips").assertTextEquals("1× ∅").assertContentDescriptionEquals("1× no colored pips")
        compose.onNodeWithText("Lands without a color: 1").assertIsDisplayed()
    }

    // DRF-118
    @Test fun notesAndCountersEditThroughTheCallbackAndStayInRange() {
        var notes by mutableStateOf(PodDeckNotes(counters = listOf(0, 0, 0, 0, 0, 0, 0, PodDeckNotes.MAX_COUNTER)))
        compose.setContent { MaterialTheme {
            PodStatsView(PodDeckBuild(), emptyMap(), emptyMap(), notes = notes, onNotes = { change -> notes = change(notes) })
        } }
        compose.onNodeWithTag("pod-stats").performScrollToNode(hasTestTag("pod-stats-counter-7"))
        compose.onNodeWithTag("pod-stats-notes").performTextInput("Splash")
        compose.runOnIdle { assertEquals("Splash", notes.text) }
        compose.onNodeWithText("6 / 2000").assertExists()
        compose.onNodeWithContentDescription("Decrease White").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Increase White").performClick().performClick()
        compose.onNodeWithContentDescription("Decrease White").performClick()
        compose.onNodeWithContentDescription("Increase Gold").performClick()
        compose.onNodeWithContentDescription("Increase Neutral").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf(1, 0, 0, 0, 0, 0, 1, PodDeckNotes.MAX_COUNTER), notes.counters) }
        compose.onNodeWithContentDescription("White 1").assertExists()
    }
}
