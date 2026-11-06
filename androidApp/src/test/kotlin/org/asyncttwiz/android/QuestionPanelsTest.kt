package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.api.CardType
import arcanum.api.Subtype
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.DecisionId
import arcanum.engine.decision.ManaUse
import arcanum.engine.decision.ReplacementOption
import arcanum.engine.decision.Share
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.TargetRef
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * STK-030 (owner playtest 2026-11-06): every question panel shows, without holding, the sentence it asks about and any
 * cost in its symbols. One picture per decision kind the app shows in an open choice's panel ([DecisionPanelContent])
 * that no game screenshot covers: the panel as Alice sees it above her hand, its source a Storm Spark of hers, asking
 * the question of an invented sentence. The kinds a game reaches are pictured elsewhere: May, ChooseOption,
 * ChooseObjects and OrderTriggers in OffspringForageWalkthroughTest, Targets and Modes in ChoiceSourceTest, Announce in
 * CastPanelChoicesTest, a combat Divide in EngineChoiceScreenTest. Random, HostFill and OtherView are never shown: the
 * devices answer them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class QuestionPanelsTest {
    @get:Rule val compose = createComposeRule()
    private val kit = EngineTableKit()

    @After fun forget() = AutomatumBoards.reset()

    /** The questions of a panel: the engine's state, the answers given, the listed answers and the mana picked. */
    private class Questions(override val engine: GameState, private val listed: List<Answer> = emptyList()) : FlowQuestions {
        val answers = mutableListOf<Answer>()
        override val mana = mutableStateListOf<ManaUse>()
        override fun atwCard(id: ObjectId): String? = (id as? ObjectId.Card)?.card?.value
        override fun answer(answer: Answer): Boolean { answers += answer; return true }
        override fun listedAnswers(): List<Answer> = listed
        override val paid: Boolean get() = mana.isNotEmpty()
        override fun toggleMana(group: List<ManaUse>) { if (mana.any { it in group }) mana.removeAll(group) else mana += group.first() }
    }

    private fun spark(): ObjectId = kit.hand("Storm Spark").first()

    /** Shows [decision]'s panel above an empty table, its source the Storm Spark, the sentence [line] under its title. */
    private fun show(decision: Decision, line: String?, listed: List<Answer> = emptyList(), withSource: Boolean = true): Questions {
        val questions = Questions(kit.state(), listed)
        val play = kit.alice.play()
        val source = if (withSource) choiceCardOf(questions, play, kit.f.a, spark()) else null
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalChoiceFrame provides ChoiceFrame(kit.f.a)) {
                        Box(Modifier.fillMaxSize().background(Color(0xffe6ece9)), contentAlignment = Alignment.BottomCenter) {
                            DecisionPanelContent(questions, false, {}, play, mapOf(kit.f.a to "Alice", kit.f.b to "Bob"), decision, source, line)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        if (line != null) compose.onNodeWithContentDescription("Question: $line").assertExists()
        return questions
    }

    private fun id(name: String) = DecisionId("question/$name")

    // STK-030, STK-049, CR 118.12: a cost paid on resolution: "Pay {R}" with its symbol, the sentence, the sources to tap
    @Test fun aManaPayment() {
        kit.opening()
        kit.playLand()
        val mountain = kit.field("Mountain").first()
        val line = "Counter target spell unless its controller pays {R}."
        val q = show(Decision.ManaPayment(id("pay"), kit.me, spark(), arcanum.api.ManaCost.parse("{R}")!!,
            listOf(ManaUse.Ability(mountain, arcanum.engine.view.AbilityOrigin.Printed(0, 0), arcanum.api.Color.Red)), emptyList()), line)
        compose.onNode(hasText("Pay {R}") and isHeading()).assertExists()
        compose.onNodeWithContentDescription("Mana source Mountain R", substring = true).performClick()
        compose.waitForIdle()
        compose.onRoot().captureShot("$SCREENSHOTS/questions/mana-payment.png",
            "A cost to pay as something resolves: the panel is titled \"Pay\" with the red mana symbol, the sentence asking for it under the title, and Alice's Mountain tapped sideways to pay, \"Paid\" under it and Pay in the bottom row.")
        compose.onNodeWithText("Pay").performClick()
        assertEquals(1, q.answers.size)
    }

    // STK-030: choosing a colour: the sentence, then a swatch button per colour
    @Test fun aColour() {
        kit.opening()
        val line = "Choose a color. Creatures you control of the chosen color get +1/+1 until end of turn."
        val q = show(Decision.ChooseColor(id("color"), kit.me, spark(), arcanum.api.Color.all), line)
        compose.onRoot().captureShot("$SCREENSHOTS/questions/color.png",
            "Choosing a colour: the panel is titled \"Choose a colour\" with the sentence under it, \"Tap one.\", and a button with a swatch for each of the five colours.")
        compose.onNodeWithText("Green").performClick()
        assertEquals(listOf<Answer>(Answer.ColorChoice(arcanum.api.Color.Green)), q.answers)
    }

    // STK-030, STK-041: a creature type: the sentence, a field narrowing every creature type of the rules
    @Test fun aCreatureType() {
        kit.opening()
        val line = "As this creature enters, choose a creature type."
        show(Decision.ChooseSubtype(id("creature-type"), kit.me, spark(), null), line)
        compose.onRoot().captureShot("$SCREENSHOTS/questions/creature-type.png",
            "Choosing a creature type: the panel is titled \"Choose a creature type\" with the sentence under it, a field to narrow the list and the rules' creature types to tap.")
    }

    // STK-030: one of listed types (a basic land type): the sentence, a button per type
    @Test fun aListedType() {
        kit.opening()
        val line = "Choose a basic land type. This land becomes that type until end of turn."
        val types = listOf("Plains", "Island", "Swamp", "Mountain", "Forest").map(::Subtype)
        show(Decision.ChooseSubtype(id("land-type"), kit.me, spark(), types), line, listed = types.map { Answer.SubtypeChoice(it) })
        compose.onNodeWithText("Forest").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/questions/land-type.png",
            "Choosing a basic land type: the panel is titled \"Choose a type\" with the sentence under it and a button for each of the five basic land types.")
    }

    // STK-030: a card type: the sentence, a button per type
    @Test fun aCardType() {
        kit.opening()
        val line = "Choose a card type. Reveal the top card of your library; if it's the chosen type, put it into your hand."
        val types = listOf(CardType.Artifact, CardType.Creature, CardType.Enchantment, CardType.Instant, CardType.Land, CardType.Sorcery)
        show(Decision.ChooseCardType(id("card-type"), kit.me, spark(), types), line, listed = types.map { Answer.CardTypeChoice(it) })
        compose.onRoot().captureShot("$SCREENSHOTS/questions/card-type.png",
            "Choosing a card type: the panel is titled \"Choose a card type\" with the sentence under it and a button for each type offered.")
    }

    // STK-030, STK-041: naming a card: the sentence, the name field
    @Test fun aCardName() {
        kit.opening()
        val line = "Choose a card name. Spells with the chosen name cost {2} more to cast."
        show(Decision.ChooseCardName(id("card-name"), kit.me, spark(), arcanum.api.ObjectFilter.Any), line)
        compose.onRoot().captureShot("$SCREENSHOTS/questions/card-name.png",
            "Naming a card: the panel is titled \"Name a card\" with the sentence under it (its {2} drawn as a symbol), the field to write any card name and OK.")
    }

    // STK-030: a number: the sentence, − value +, OK
    @Test fun aNumber() {
        kit.opening()
        val line = "Choose a number from 0 to 5. Each player mills that many cards."
        show(Decision.ChooseNumber(id("number"), kit.me, spark(), 0, 5), line)
        compose.onRoot().captureShot("$SCREENSHOTS/questions/number.png",
            "Choosing a number: the panel is titled \"Choose a number\" with the sentence under it, − 0 + and OK.")
    }

    // STK-030: a player: the sentence, a button per player
    @Test fun aPlayer() {
        kit.opening()
        val line = "Choose a player. That player draws two cards."
        val q = show(Decision.ChoosePlayer(id("player"), kit.me, spark(), listOf(kit.me, kit.other)), line)
        compose.onRoot().captureShot("$SCREENSHOTS/questions/player.png",
            "Choosing a player: the panel is titled \"Choose a player\" with the sentence under it and a button for Alice and one for Bob.")
        compose.onNodeWithText("Bob").performClick()
        assertEquals(listOf<Answer>(Answer.Player(kit.other)), q.answers)
    }

    // STK-030, CR 601.2d: dividing a spell's damage among its targets: the sentence, a row per target with − share +
    @Test fun aDivision() {
        kit.opening()
        val line = "Storm Spark deals 3 damage divided as you choose among one or two targets."
        show(Decision.Divide(id("divide"), kit.me, spark(), 3, listOf(TargetRef.Player(kit.other), TargetRef.Player(kit.me)), 1,
            suggested = listOf(Share(TargetRef.Player(kit.other), 2), Share(TargetRef.Player(kit.me), 1))), line)
        compose.onRoot().captureShot("$SCREENSHOTS/questions/divide.png",
            "Dividing a spell's damage: the panel is titled \"Divide 3\" with the sentence under it, a row for Bob with 2 and one for Alice with 1, each with − and +, and OK.")
    }

    // STK-030, CR 616.1: which replacement effect applies first: a full-width row per effect, its card and its whole text
    @Test fun aReplacementOrder() {
        kit.opening()
        val (a, b) = kit.hand("Storm Spark").take(2).let { it[0] to (it.getOrNull(1) ?: it[0]) }
        show(Decision.ChooseReplacement(id("replacement"), kit.me, null, listOf(
            ReplacementOption(0, a, "If a source would deal damage to you, prevent 1 of that damage."),
            ReplacementOption(1, b, "If a source would deal damage to you, it deals double that damage to you instead."))), null)
        compose.onRoot().captureShot("$SCREENSHOTS/questions/replacement.png",
            "Which replacement effect applies first: the panel is titled \"Which applies first\" with its instruction and a full-width row per effect, its card at the left and its whole text beside it, then OK.")
    }

    // STK-030, CR 704.5j: the legend rule: no source, its sentence its instruction, the cards to keep one of
    @Test fun theLegendRule() {
        kit.opening()
        val cards = kit.hand("Mountain").take(2)
        show(Decision.LegendRule(id("legend"), kit.me, null, "Mountain", cards), null, withSource = false)
        compose.onNodeWithText("Tap the Mountain to keep; the others go to the graveyard.").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/questions/legend-rule.png",
            "The legend rule: the panel is titled \"Legend rule\", says \"Tap the Mountain to keep; the others go to the graveyard.\", shows the two cards and Keep.")
    }

    // STK-030, STK-023, STK-047: a spell's question shows its sentences naming what is chosen, else its whole text
    @Test fun aSpellsQuestionShowsTheSentenceItAsksAbout() {
        val text = "Ember Burst deals X damage to any target.\nYou gain 1 life."
        val x = Decision.Announce(id("x"), kit.me, null, "X", 0)
        assertEquals("Ember Burst deals X damage to any target.", askedSentence(text, x))
        val targets = Decision.Targets(id("t"), kit.me, null, "any", emptyList(), 1, 1)
        assertEquals("Ember Burst deals X damage to any target.", askedSentence(text, targets))
        val color = Decision.ChooseColor(id("c"), kit.me, null, arcanum.api.Color.all)
        assertEquals("Ember Burst deals X damage to any target. You gain 1 life.", askedSentence(text, color))
        assertEquals(null, askedSentence(null, color))
    }

    // STK-030, SYS-064: a step Automatum can't do: its text, skipped
    @Test fun aSkippedStep() {
        kit.opening()
        show(Decision.Manual(id("manual"), kit.me, spark(), "Exchange control of two target creatures."), null)
        compose.onNodeWithText("Exchange control of two target creatures.").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/questions/skipped.png",
            "A step Automatum can't do yet: the panel is titled \"Skipped\", says \"Automatum can't do this part yet: it is skipped.\" and shows the step's own text, with Done.")
    }
}
