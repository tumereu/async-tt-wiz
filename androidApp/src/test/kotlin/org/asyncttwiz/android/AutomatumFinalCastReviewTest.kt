package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.DecisionId
import arcanum.engine.decision.ManaUse
import arcanum.engine.decision.ModeOption
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** Final adversarial review of casting and resolving (report `arcanum-final-cast-review.md`, outside the repo): each
 * test pins a problem of the cast and decision panels, ignored while it isn't fixed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AutomatumFinalCastReviewTest {
    @get:Rule val compose = createComposeRule()

    /** The panel's questions with nothing behind them but the answers given. */
    private class Answers : FlowQuestions {
        val given = mutableListOf<Answer>()
        override val engine: GameState get() = error("no engine state")
        override fun atwCard(id: ObjectId): String? = null
        override fun answer(answer: Answer): Boolean { given += answer; return true }
        override fun listedAnswers(): List<Answer> = emptyList()
        override val mana: List<ManaUse> = emptyList()
        override val paid: Boolean = false
        override fun toggleMana(group: List<ManaUse>) = Unit
    }

    // A Season (BLB, CR 700.2i): up to five {P} worth of modes, the same mode more than once
    private val season = Decision.Modes(DecisionId("season"), PlayerId("alice"), null, listOf(
        ModeOption(0, "Create a 1/1 white Rabbit creature token.", pawprints = 1),
        ModeOption(1, "Exile target nonland permanent. Its controller draws a card.", pawprints = 2),
        ModeOption(2, "Return target permanent card with mana value 3 or less from your graveyard to the battlefield.", pawprints = 3),
    ), min = 1, max = 5, repeatable = true, pawprintLimit = 5)

    // finalreview-cast #5 (CR 700.2i, I-D6, I-D28): in the mode section of a repeatable modal spell a mode tapped by mistake
    // can't be taken back (each tap adds it again), and the pawprints are neither shown nor counted, so OK sends a choice
    // over the limit that the engine refuses ("Automatum doesn't allow that choice.") and the player can only cancel the
    // whole cast
    @Ignore("finalreview-cast #5: repeatable modes can't be untapped and pawprints aren't counted")
    @Test fun repeatableModesCanBeTakenBackAndPawprintsAreCounted() {
        val answers = Answers()
        compose.setContent { MaterialTheme { OpenSection(answers, answers, TableStatesFixture.initial, emptyMap(), season) } }
        val returning = season.options[2].text!!
        compose.onNodeWithText(returning).performClick()
        compose.onNodeWithText(returning).performClick()
        // Six pawprints chosen: OK must not send it
        compose.onNodeWithText("OK").assertIsNotEnabled()
    }

    // finalreview-cast #5 (the same section): the pawprint cost of each mode is part of what the player chooses by
    @Ignore("finalreview-cast #5: repeatable modes can't be untapped and pawprints aren't counted")
    @Test fun pawprintCostsAreShown() {
        val answers = Answers()
        compose.setContent { MaterialTheme { OpenSection(answers, answers, TableStatesFixture.initial, emptyMap(), season) } }
        assertEquals(1, compose.onAllNodesWithText("{P}{P}{P}", substring = true).fetchSemanticsNodes().size)
    }
}
