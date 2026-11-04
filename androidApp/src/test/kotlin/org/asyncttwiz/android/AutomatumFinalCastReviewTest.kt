package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** Final adversarial review of casting and resolving (report `arcanum-final-cast-review.md`, outside the repo): each
 * test pins a problem of the cast and decision panels, now fixed. */
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

    // STK-040, finalreview-cast #5 (CR 700.2i, I-D6, I-D28): in the mode section of a repeatable modal spell a mode tapped by mistake
    // can't be taken back (each tap adds it again), and the pawprints are neither shown nor counted, so OK sends a choice
    // over the limit that the engine refuses ("Automatum doesn't allow that choice.") and the player can only cancel the
    // whole cast
    @Test fun repeatableModesCanBeTakenBackAndPawprintsAreCounted() {
        val answers = Answers()
        compose.setContent { MaterialTheme { OpenSection(answers, answers, tableBoard(TableStatesFixture.initial), emptyMap(), season) } }
        val returning = season.options[2].text!!
        compose.onNodeWithText(returning).performClick()
        compose.onNodeWithText(returning).performClick()
        // Six pawprints chosen: OK must not send it
        compose.onNodeWithText("OK").assertIsNotEnabled()
    }

    // STK-040, finalreview-cast #5 (the same section): the pawprint cost of each mode is part of what the player chooses by
    @Test fun pawprintCostsAreShown() {
        val answers = Answers()
        compose.setContent { MaterialTheme { OpenSection(answers, answers, tableBoard(TableStatesFixture.initial), emptyMap(), season) } }
        assertEquals(1, compose.onAllNodesWithText("{P}{P}{P}", substring = true).fetchSemanticsNodes().size)
    }

    // STK-040, finalreview-cast #5 (CR 700.2d, 700.2i): a mode chosen once too often is taken back with "−", and the choice goes out
    // once it is within the limit; "Clear" takes back every mode
    @Test fun repeatableModeTakenBackThenSent() {
        val answers = Answers()
        compose.setContent { MaterialTheme { OpenSection(answers, answers, tableBoard(TableStatesFixture.initial), emptyMap(), season) } }
        val rabbit = season.options[0].text!!
        val returning = season.options[2].text!!
        compose.onNodeWithText(returning).performClick()
        compose.onNodeWithText(returning).performClick()
        compose.onNodeWithText("×2").assertExists()
        compose.onNodeWithText("Pawprints: 6 of 5").assertExists()
        compose.onNodeWithContentDescription("Choose one fewer: $returning").performClick()
        compose.onNodeWithText(rabbit).performClick()
        compose.onNodeWithText(rabbit).performClick()
        compose.onNodeWithText("Pawprints: 5 of 5").assertExists()
        compose.onNodeWithText("OK").assertIsEnabled().performClick()
        assertEquals(listOf<Answer>(Answer.Modes(listOf(2, 0, 0))), answers.given)
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("OK").assertIsNotEnabled()
    }

    // STK-041, finalreview-cast #1 (I-D27, CR 201.3): "choose a card name" is a name field with suggestions from the device's card
    // catalog; the engine's listed names (the game's cards, the other player's hidden hand among them) are never shown
    @Test fun cardNameIsTypedWithCatalogSuggestions() {
        val answers = object : FlowQuestions by Answers() {
            val given = mutableListOf<Answer>()
            override fun answer(answer: Answer): Boolean { given += answer; return true }
            override fun listedAnswers(): List<Answer> = listOf(Answer.CardName("Velvet Secret"))
        }
        val catalog = CardNameSuggestions { text -> listOf("Velvet Fern", "Velvet Hollow").filter { it.contains(text, ignoreCase = true) } }
        val naming = Decision.ChooseCardName(DecisionId("name"), PlayerId("alice"), null, arcanum.api.ObjectFilter.Any)
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalCardNameSuggestions provides catalog) {
            OpenSection(answers, answers, tableBoard(TableStatesFixture.initial), emptyMap(), naming)
        } } }
        compose.onNodeWithText("OK").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("velv")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Velvet Fern").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Velvet Secret").assertDoesNotExist()
        compose.onNodeWithText("Velvet Fern").performClick()
        compose.onNodeWithText("OK").assertIsEnabled().performClick()
        assertEquals(listOf<Answer>(Answer.CardName("Velvet Fern")), answers.given)
    }

    // STK-041, finalreview-cast #1 (I-D27, CR 205.3m): "choose a creature type" offers every creature type of the rules, found by
    // typing, never only the types of the game's cards
    @Test fun creatureTypeFromEveryType() {
        val answers = Answers()
        val choosing = Decision.ChooseSubtype(DecisionId("type"), PlayerId("alice"), null)
        compose.setContent { MaterialTheme { OpenSection(answers, answers, tableBoard(TableStatesFixture.initial), emptyMap(), choosing) } }
        compose.onNodeWithText("Zombie").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("ell")
        compose.onNodeWithText("Zombie").assertDoesNotExist()
        compose.onNodeWithText("Elf").assertDoesNotExist()
        compose.onNodeWithText("Wizard").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextReplacement("elf")
        compose.onNodeWithText("Elf").performClick()
        assertEquals(listOf<Answer>(Answer.SubtypeChoice(arcanum.api.Subtype("Elf"))), answers.given)
    }
}
