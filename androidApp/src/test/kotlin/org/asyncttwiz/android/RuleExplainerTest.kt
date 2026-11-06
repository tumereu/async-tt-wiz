package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.KeywordAction
import arcanum.api.RulesGlossary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TBL-132 (owner gallery review 2026-11-06): holding a chip that names a keyword or keyword action explains it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class RuleExplainerTest {
    @get:Rule val compose = createComposeRule()

    // TBL-132: a question's cost chip "Forage", held, shows what foraging is in plain words
    @Test fun holdingACostChipExplainsTheKeywordAction() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(Color(0xffe6ece9)), contentAlignment = Alignment.Center) {
                    Column(Modifier.width(360.dp).background(Color.White).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("You may pay", fontSize = 15.sp)
                        Text("You may forage. If you do, put two +1/+1 counters on it.", fontSize = 12.sp)
                        CostLine(Cost(listOf(CostPart.Action(KeywordAction.Forage))))
                    }
                }
            }
        }
        val forage = RulesGlossary.action("forage")!!
        assertTrue(compose.onAllNodesWithContentDescription("Rule explanation").fetchSemanticsNodes().isEmpty(), "nothing before the hold")
        compose.onNodeWithText("Forage", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText(forage.text, useUnmergedTree = true).assertExists()
        assertEquals(1, compose.onAllNodesWithContentDescription("Rule explanation").fetchSemanticsNodes().size)
        captureShot("$SCREENSHOTS/chips/rule-explainer.png",
            "Holding the Forage cost chip of a may-pay question: a dark popup over it explains in plain words that foraging " +
                "means exiling three cards from your graveyard or sacrificing a Food.")
    }

    // TBL-132: text naming no keyword holds no explanation
    @Test fun aChipNamingNoKeywordExplainsNothing() {
        compose.setContent { MaterialTheme { RuleTip("Pays {2}") { Text("{2}") } } }
        compose.onNodeWithText("{2}").performTouchInput { longClick() }
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithContentDescription("Rule explanation").fetchSemanticsNodes().isEmpty())
    }
}
