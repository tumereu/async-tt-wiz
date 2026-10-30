package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PodStatsViewTest {
    @get:Rule val compose = createComposeRule()

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
        compose.onNodeWithText("Land sources").assertIsDisplayed()
        compose.onNodeWithTag("pod-stats-mana-W").assert(hasText("½")).assert(hasText("39"))
        compose.onNodeWithTag("pod-stats-mana-B").assert(hasText("1")).assert(hasText("0"))
        compose.onNodeWithTag("pod-stats-mana-R").assert(hasText("0"))
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

    @Test
    @Config(qualifiers = "w412dp-h892dp-port-xhdpi")
    fun typeCountsWrapBelowTheLegendAndAboveTheManaTable() {
        fun card(name: String, type: String, cost: String) = CardDefinition(name, listOf(DefinitionFace("front", name, typeLine = type, manaCost = cost)))
        val definitions = mapOf(
            "bear" to card("Bear", "Creature — Bear", "{1}{G}"), "golem" to card("Golem", "Artifact Creature — Golem", "{4}"),
            "hero" to card("Hero", "Legendary Creature — Human Knight", "{1}{W}{W}"), "bolt" to card("Bolt", "Instant", "{R}"),
            "growth" to card("Growth", "Instant", "{G}"), "rampant" to card("Rampant", "Sorcery", "{2}{G}"),
            "aura" to card("Aura", "Enchantment — Aura", "{1}{W}"), "ring" to card("Ring", "Artifact", "{2}"),
            "walker" to card("Walker", "Legendary Planeswalker — Ajani", "{2}{G}{W}"),
            "giant" to card("Giant", "Creature — Giant // Sorcery — Adventure", "{4}{R}"),
            "gate" to card("Gate", "Land — Gate", ""),
        )
        val counts = mapOf("bear" to 5, "golem" to 2, "hero" to 3, "bolt" to 2, "growth" to 2, "rampant" to 1, "aura" to 2,
            "ring" to 1, "walker" to 1, "giant" to 2, "gate" to 2)
        val copies = counts.flatMap { (digest, n) -> List(n) { DraftCopy("$digest-$it", digest) } }.associateBy { it.id }
        compose.setContent { MaterialTheme {
            PodStatsView(PodDeckBuild(copies.keys.map { PodDeckCopy(it) }, PodDeckBasics(plains = 7, forest = 8)), copies, definitions)
        } }
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
        val output = java.io.File("$SCREENSHOTS/pod-stats-types.png").also { it.parentFile!!.mkdirs() }
        compose.onRoot().captureRoboImage(output.path)
    }

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
        for (color in DraftColor.entries) compose.onNodeWithTag("pod-stats-mana-${color.name}").assert(hasText("0"))
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
        compose.onNodeWithTag("pod-stats-mana-G").assert(hasText("17")).assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("pod-stats-mana").getUnclippedBoundsInRoot())
    }

    @Test fun pipListEndsWithCardsWithoutColoredPipsAndColorlessLandsAreNamed() {
        val spell = CardDefinition("Spell", listOf(DefinitionFace("front", "Spell", typeLine = "Instant", manaCost = "{1}{U}")))
        val rock = CardDefinition("Rock", listOf(DefinitionFace("front", "Rock", typeLine = "Artifact", manaCost = "{2}")))
        val waste = CardDefinition("Waste", listOf(DefinitionFace("front", "Waste", typeLine = "Land", rulesText = "{T}: Add {C}.")))
        compose.setContent { MaterialTheme {
            PodStatsView(PodDeckBuild(listOf(PodDeckCopy("a"), PodDeckCopy("b"), PodDeckCopy("c"), PodDeckCopy("d"))),
                mapOf("a" to DraftCopy("a", "spell"), "b" to DraftCopy("b", "spell"), "c" to DraftCopy("c", "rock"),
                    "d" to DraftCopy("d", "waste")), mapOf("spell" to spell, "rock" to rock, "waste" to waste))
        } }
        compose.onNodeWithTag("pod-stats-mana-U").assert(hasText("2")).assert(hasText("0"))
        compose.onNodeWithTag("pod-stats-pattern-0").assertTextEquals("2× {U}")
        compose.onNodeWithTag("pod-stats-no-pips").assertTextEquals("1× ∅").assertContentDescriptionEquals("1× no colored pips")
        compose.onNodeWithText("Lands without a color: 1").assertIsDisplayed()
    }

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
