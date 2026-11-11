package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * TRN-063, TRN-070 (owner playtest 2026-11-05 evening): the main button has room for two lines, and every name it shows
 * reads whole on at most two lines under its icon, at the width it has on a phone in portrait (and a narrow one), also with
 * large system text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class MainButtonLabelsTest {
    @get:Rule val compose = createComposeRule()

    /** Every kind of step arrow with what it says (TRN-063, STK-029): the step moves naming where they go. */
    private val steps = listOf(AutomatumStep.ToCombat to "To combat", AutomatumStep.EndTurn to "End turn",
        AutomatumStep.ToEndStep to "To end step", AutomatumStep.Next to "To upkeep", AutomatumStep.Next to "To draw step",
        AutomatumStep.Next to "To Main 1", AutomatumStep.Next to "To attackers", AutomatumStep.Next to "To blockers",
        AutomatumStep.Next to "To damage", AutomatumStep.Next to "To end of combat", AutomatumStep.Next to "To Main 2",
        AutomatumStep.Pass to "To end step", AutomatumStep.Pass to "To next turn", AutomatumStep.Attack(2) to "Attack (2)",
        AutomatumStep.Attack(0) to "No attack", AutomatumStep.Block(1) to "Block (1)", AutomatumStep.Block(0) to "No blocks",
        AutomatumStep.Resolve to "Resolve")

    /** Every name the main button shows: the step arrow's, a combat division's OK, the waits' (TRN-063, TRN-070). */
    private val labels = steps.map { it.second }.distinct() + listOf("OK", "Assume resolves", "Assume no response", "Assume no blockers",
        "Assume blockers", "Cancel request")

    @OptIn(ExperimentalLayoutApi::class)
    private fun render(width: Int, fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    FlowRow(Modifier.testTag("buttons").width(412.dp).background(Color(0xfff3f5f5)).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((step, words) in steps) Column(Modifier.width(width.dp)) {
                            AutomatumStepFace(step, words, true, Modifier.width(width.dp))
                        }
                        for (label in labels.drop(steps.map { it.second }.distinct().size)) Column(Modifier.width(width.dp)) {
                            if (label.startsWith("Assume")) EngineWaitArrow(EngineTableRoutes.WaitAction(label, AssumeIcon) {}, Modifier.width(width.dp))
                            else if (label == "Cancel request")
                                EngineWaitArrow(EngineTableRoutes.WaitAction(label, Icons.Outlined.Close, assumption = false) {}, Modifier.width(width.dp))
                            // A choice's OK (a combat division's): the tick
                            else MainActionArrow(MainAction(label, Icons.Outlined.Check) {}, true, Modifier.width(width.dp))
                        }
                    }
                }
            }
        }
    }

    /** The label [text]'s layout: on at most two lines, none of it cut off. */
    private fun readsWhole(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onAllNodes(hasText(text) and !hasContentDescription(text), useUnmergedTree = true).fetchSemanticsNodes().first()
            .config[SemanticsActions.GetTextLayoutResult].action!!(results)
        val layout = results.single()
        assertTrue(layout.lineCount <= 2 && !layout.hasVisualOverflow, "\"$text\" reads whole: ${layout.lineCount} lines, overflow ${layout.hasVisualOverflow}")
        // Wrapped only between words
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertTrue(text[end - 1] == ' ' || text.getOrNull(end) == ' ', "\"$text\" breaks between words")
        }
    }

    // TRN-063, STK-029, TRN-070: at the portrait phone's main button width every name reads whole, under its kind's icon (owner
    // gallery note 22, 2026-11-11: play for Resolve, crossed swords for To combat and Attack, a shield for Block, a turn arrow
    // for End turn, a tick for OK; the step moves name where they go)
    @Test fun everyNameReadsOnTwoLines() {
        render(width = 128, fontScale = 1f)
        labels.forEach(::readsWhole)
        assertTrue(AutomatumStep.Resolve.icon == Icons.Outlined.PlayArrow && AutomatumStep.Block(1).icon == Icons.Outlined.Shield &&
            AutomatumStep.EndTurn.icon == Icons.AutoMirrored.Outlined.RotateRight && AutomatumStep.ToCombat.swords && AutomatumStep.Attack(2).swords,
            "an icon per kind")
        compose.onNodeWithTag("buttons").captureShot("$SCREENSHOTS/main-button/names.png",
            "Every name the main button can show, each on a button as wide as on a phone in portrait under its kind's icon (crossed swords for To combat and Attack, a turn arrow for End turn, an arrow for the step moves naming where they go, skip for Pass, a shield for Block, play for Resolve, a tick for OK), its words whole on up to two lines; the Assumes are violet with white words.")
    }

    // TRN-063: on a narrow phone with large system text the names still read whole, drawn smaller where two lines don't fit
    @Test fun namesReadWholeWhenNarrowWithLargeText() {
        render(width = 108, fontScale = 1.3f)
        labels.forEach(::readsWhole)
        compose.onNodeWithTag("buttons").captureShot("$SCREENSHOTS/main-button/names-narrow-large-text.png",
            "The main button's names on a narrow phone with large system text: each reads whole on at most two lines (Assume no response and Assume no blockers on two), drawn a little smaller where needed.")
    }

    // TRN-063, TRN-070 (owner, 2026-11-10): the main button tells an assumption from your own action at a glance: your own
    // actions (Resolve, Cast, Cancel request) are white with the dark teal words; an Assume is filled violet with white words
    // and the Assume icon; a greyed Assume stays greyed
    @Test fun anAssumptionReadsApartFromYourOwnAction() {
        val assume = EngineTableRoutes.WaitAction("Assume resolves", AssumeIcon) {}
        val greyed = EngineTableRoutes.WaitAction("Assume resolves", AssumeIcon, refusal = "Assuming this would show you hidden cards") {}
        val cancel = EngineTableRoutes.WaitAction("Cancel request", Icons.Outlined.Close, assumption = false) {}
        assertTrue(assumeLook(assume))
        kotlin.test.assertFalse(assumeLook(greyed), "a greyed Assume stays greyed")
        kotlin.test.assertFalse(assumeLook(cancel), "Cancel request is your own action")
        compose.setContent {
            MaterialTheme {
                FlowRow(Modifier.testTag("sides").width(412.dp).background(Color(0xfff3f5f5)).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MainActionArrow(MainAction("Resolve", Icons.Outlined.PlayArrow) {}, true, Modifier.width(128.dp))
                    EngineWaitArrow(assume, Modifier.width(128.dp))
                    MainActionArrow(MainAction("Cast", Icons.Outlined.DoneAll) {}, true, Modifier.width(128.dp))
                    EngineWaitArrow(EngineTableRoutes.WaitAction("Assume blockers", AssumeIcon) {}, Modifier.width(128.dp))
                    EngineWaitArrow(cancel, Modifier.width(128.dp))
                    EngineWaitArrow(greyed, Modifier.width(128.dp))
                }
            }
        }
        compose.onAllNodes(hasTestTag("engine-wait-action") and
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Assumption"))
            .assertCountEquals(2)
        compose.onNodeWithTag("sides").captureShot("$SCREENSHOTS/main-button/assume-or-own.png",
            "The main button's two sides drawn as in play: Resolve, Cast and Cancel request, your own actions, white with dark " +
                "teal words; Assume resolves and Assume blockers filled violet with white words and the Assume icon; a greyed " +
                "Assume (it would show hidden cards) stays greyed.")
    }
}
