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
import org.asyncttwiz.protocol.AccountIdentity
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width

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
    private class Questions(override val engine: GameState, private val listed: List<Answer> = emptyList(),
        /** The payment a "you may pay" goes on to (STK-049), its suggestion picked as with Autotap on. */
        private val payment: Decision.ManaPayment? = null) : FlowQuestions {
        val answers = mutableListOf<Answer>()
        var preference: List<ReplacementOption>? = null
        override val mana = mutableStateListOf<ManaUse>().apply { payment?.let { addAll(it.suggestion) } }
        override fun atwCard(id: ObjectId): String? = (id as? ObjectId.Card)?.card?.value
        override fun answer(answer: Answer): Boolean { answers += answer; return true }
        override fun listedAnswers(): List<Answer> = listed
        override val paid: Boolean get() = mana.isNotEmpty()
        override fun toggleMana(group: List<ManaUse>) { if (mana.any { it in group }) mana.removeAll(group) else mana += group.first() }
        override fun paymentAfter(decision: Decision): Decision.ManaPayment? = payment.takeIf { decision is Decision.May }
        override fun payMana(): Boolean { answers += Answer.ManaPayment(mana.toList()); return true }
        override fun answerReplacements(decision: Decision.ChooseReplacement, preference: List<ReplacementOption>): Boolean {
            this.preference = preference; return answer(Answer.Replacement(preference.first().index))
        }
    }

    private fun spark(): ObjectId = kit.hand("Storm Spark").first()

    /** Shows [decision]'s panel above an empty table, its source the Storm Spark, the sentence [line] under its title. */
    private fun show(decision: Decision, line: String?, listed: List<Answer> = emptyList(), withSource: Boolean = true,
        payment: Decision.ManaPayment? = null, catalog: CardNameSuggestions? = null): Questions {
        val questions = Questions(kit.state(), listed, payment)
        val play = kit.alice.play()
        val source = if (withSource) choiceCardOf(questions, play, kit.f.a, spark()) else null
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalChoiceFrame provides ChoiceFrame(kit.f.a),
                        LocalPlayerAccounts provides mapOf(kit.f.a to AccountIdentity(kit.f.a, "Alice"), kit.f.b to AccountIdentity(kit.f.b, "Bob")),
                        LocalCardNameSuggestions provides (catalog ?: CardNameSuggestions { _, _ -> emptyList() })) {
                        // STK-044: in landscape a panel is at most 420 dp wide from the table's left edge
                        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xffe6ece9)), contentAlignment = Alignment.BottomStart) {
                            Box(Modifier.width(if (maxWidth > maxHeight) 420.dp else maxWidth)) {
                                DecisionPanelContent(questions, false, {}, play, mapOf(kit.f.a to "Alice", kit.f.b to "Bob"), decision, source, line)
                            }
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

    private fun redPayment(mountain: ObjectId) = Decision.ManaPayment(id("pay"), kit.me, spark(), arcanum.api.ManaCost.parse("{R}")!!,
        listOf(ManaUse.Ability(mountain, arcanum.engine.view.AbilityOrigin.Printed(0, 0), arcanum.api.Color.Red)),
        listOf(ManaUse.Ability(mountain, arcanum.engine.view.AbilityOrigin.Printed(0, 0), arcanum.api.Color.Red)))

    // STK-030, STK-049, CR 118.12 (owner gallery review, 2026-11-06, bug: no decline): "unless its controller pays {R}" is one
    // panel "Pay {R}" with the sources Autotap picks already tapped (changeable) and "Don't pay" beside Pay
    @Test fun aCostYouMayPayOrDecline() {
        kit.opening()
        kit.playLand()
        val mountain = kit.field("Mountain").first()
        val text = "Counter target spell unless its controller pays {R}."
        val may = Decision.May(id("may-pay"), kit.me, spark(), text, arcanum.api.Cost(listOf(arcanum.api.CostPart.Mana(arcanum.api.ManaCost.parse("{R}")!!))))
        val q = show(may, null, payment = redPayment(mountain))
        compose.onNodeWithContentDescription("Question: $text").assertExists()
        compose.onNode(hasText("Pay {R}") and isHeading()).assertExists()
        compose.onNodeWithContentDescription("Mana source Mountain R, tapped", substring = true).assertExists()
        compose.onNodeWithText("Don't pay").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/questions/mana-payment.png",
            "A cost to pay as something resolves: the panel is titled \"Pay\" with the red mana symbol, the sentence asking for it under the title, Alice's Mountain already tapped sideways by Autotap, \"Paid\" under it, and Don't pay beside Pay in the bottom row.")
        compose.onNodeWithText("Don't pay").performClick()
        assertEquals(listOf<Answer>(Answer.YesNo(false)), q.answers)
        compose.onNodeWithText("Pay").performClick()
        assertEquals(Answer.ManaPayment(redPayment(mountain).suggestion), q.answers.last())
    }

    // STK-030, STK-049, CR 508.1h: a cost the rules make one pay (no "you may") has no decline: only Pay
    @Test fun aCostYouMustPayHasNoDecline() {
        kit.opening()
        kit.playLand()
        val mountain = kit.field("Mountain").first()
        val q = show(redPayment(mountain).copy(suggestion = emptyList()), null)
        compose.onNode(hasText("Pay {R}") and isHeading()).assertExists()
        compose.onNodeWithText("Don't pay").assertDoesNotExist()
        compose.onNodeWithContentDescription("Mana source Mountain R", substring = true).performClick()
        compose.waitForIdle()
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

    /** Every card's name as the card records would offer them (invented). */
    private val catalog = CardNameSuggestions { text, _ ->
        listOf("Spark Elemental", "Spark Lantern", "Sparkwing Drake", "Storm Spark", "Mountain Sprite").filter { it.contains(text, ignoreCase = true) }
    }

    // STK-030, STK-041, CR 201.4 (owner gallery review, 2026-11-06): naming a card is a search field autocompleting card
    // names, never free text: Alice's own library first (a card-stack icon), then cards she sees (an eye), then every card;
    // the effect's "nonland" leaves out the Mountains; OK only once a name is chosen
    @Test fun aCardName() {
        kit.opening()
        val line = "Choose a nonland card name. Spells with the chosen name cost {2} more to cast."
        val q = show(Decision.ChooseCardName(id("card-name"), kit.me, spark(),
            arcanum.api.ObjectFilter.Not(arcanum.api.ObjectFilter.Type(arcanum.api.CardType.Land))), line, catalog = catalog)
        compose.onNodeWithText("OK").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Storm Spark, in your library").assertExists()
        compose.onNodeWithContentDescription("Mountain, in your library").assertDoesNotExist()
        compose.onNodeWithContentDescription("Mountain, seen this game").assertDoesNotExist()
        compose.onNodeWithContentDescription("Card name").performTextInput("spar")
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Spark Lantern, a card").fetchSemanticsNodes().isNotEmpty() }
        // Each name once, in its first group
        compose.onAllNodesWithContentDescription("Storm Spark, ", substring = true).assertCountEquals(1)
        compose.onNodeWithContentDescription("Storm Spark, in your library").performClick()
        // The tap writes the name in the field; the card records' search for it runs off the main thread: wait until its
        // result is shown (only Storm Spark left, the other names gone) before the picture
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(", a card", substring = true).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithContentDescription("Storm Spark, in your library, chosen").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("OK").assertIsEnabled()
        compose.onRoot().captureShot("$SCREENSHOTS/questions/card-name.png",
            "Naming a card: the panel is titled \"Name a card\" with the sentence under it, the search field holding Storm Spark, and the names containing it: Storm Spark from Alice's library with a card-stack icon, chosen; OK is enabled.")
        compose.onNodeWithText("OK").performClick()
        assertEquals(listOf<Answer>(Answer.CardName("Storm Spark")), q.answers)
    }

    // STK-041, CR 201.4 (owner gallery review, 2026-11-06): a name only written, not one of the cards, isn't taken
    @Test fun aWrittenNameMustBeACard() {
        kit.opening()
        show(Decision.ChooseCardName(id("card-name"), kit.me, spark(), arcanum.api.ObjectFilter.Any), null, catalog = catalog)
        compose.onNodeWithContentDescription("Card name").performTextInput("Sparkly Nonsense")
        compose.waitForIdle()
        compose.onNodeWithText("OK").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Card name").performTextReplacement("storm spark")
        compose.waitForIdle()
        compose.onNodeWithText("OK").assertIsEnabled()
    }

    // STK-041 (owner gallery review, 2026-11-06): the name search while typing, in landscape (the panel 420 dp wide at the
    // table's left): Alice's own library's names first, then every card's
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun aCardNameInLandscape() {
        kit.opening()
        show(Decision.ChooseCardName(id("card-name"), kit.me, spark(),
            arcanum.api.ObjectFilter.Not(arcanum.api.ObjectFilter.Type(arcanum.api.CardType.Land))), "Choose a nonland card name.", catalog = catalog)
        compose.onNodeWithContentDescription("Card name").performTextInput("sp")
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Spark Lantern, a card").fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureShot("$SCREENSHOTS/questions/card-name-landscape.png",
            "Naming a card in landscape while typing: the panel at the table's left holds the search field with \"sp\" and the names to tap: Storm Spark from Alice's library with a card-stack icon first, then every card's names; OK waits for a tap.")
    }

    // STK-030: a number: the sentence, − value +, OK; the value's area keeps its width for up to 4 digits (owner gallery
    // review, 2026-11-06)
    @Test fun aNumber() {
        kit.opening()
        val line = "Choose a number from 0 to 5. Each player mills that many cards."
        show(Decision.ChooseNumber(id("number"), kit.me, spark(), 0, 5), line)
        compose.onRoot().captureShot("$SCREENSHOTS/questions/number.png",
            "Choosing a number: the panel is titled \"Choose a number\" with the sentence under it, − 0 + with the 0 in a box wide enough for four digits, and OK.")
    }

    // STK-023, STK-030 (owner gallery review, 2026-11-06): a tap on the number opens a numeric input in its place for any
    // value; − and + stay where they are up to 4 digits
    @Test fun aNumberIsTyped() {
        kit.opening()
        val q = show(Decision.ChooseNumber(id("number"), kit.me, spark(), 0, null), "Choose a number. Each player mills that many cards.")
        val plus = compose.onNodeWithContentDescription("One more").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Value 0").performClick()
        compose.onNodeWithContentDescription("Number").performTextReplacement("1234")
        compose.waitForIdle()
        compose.onRoot().captureShot("$SCREENSHOTS/questions/number-input.png",
            "A tap on the number of \"Choose a number\" opens the numeric input in its place: 1234 written in its field, a check and a cross beside it and \"0 or more\" under it.")
        compose.onNodeWithContentDescription("Use this number").performClick()
        compose.onNodeWithContentDescription("Value 1234").assertExists()
        assertEquals(plus, compose.onNodeWithContentDescription("One more").fetchSemanticsNode().boundsInRoot, "+ stays put at 4 digits")
        compose.onNodeWithText("OK").performClick()
        assertEquals(listOf<Answer>(Answer.Number(1234)), q.answers)
    }

    // STK-023, STK-030: a written number outside the range isn't taken: the range says why
    @Test fun aNumberOutOfRangeIsRefused() {
        assertEquals("At most 5.", numberProblem("7", 0, 5))
        assertEquals("At least 1.", numberProblem("0", 1, null))
        assertEquals(null, numberProblem("3", 0, 5))
        assertEquals("Write a number.", numberProblem(" ", 0, 5))
        assertEquals("From 0 to 5", numberRange(0, 5))
        assertEquals("0 or more", numberRange(0, null))
    }

    // STK-030: a player: the sentence, a button per player with their avatar
    @Test fun aPlayer() {
        kit.opening()
        val line = "Choose a player. That player draws two cards."
        val q = show(Decision.ChoosePlayer(id("player"), kit.me, spark(), listOf(kit.me, kit.other)), line)
        // Owner gallery review (2026-11-06, bug): each player's avatar, not a generic icon
        compose.onNodeWithContentDescription("Alice's avatar", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Bob's avatar", useUnmergedTree = true).assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/questions/player.png",
            "Choosing a player: the panel is titled \"Choose a player\" with the sentence under it and a button for Alice and one for Bob, each with their round avatar.")
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

    // STK-030, CR 616.1 (owner gallery review, 2026-11-06): which replacement effects apply first is a drag-to-reorder
    // preference list, each row its card and whole text with a handle, in the engine's order at first; dragging the second
    // above the first and OK gives that order
    @Test fun aReplacementOrder() {
        kit.opening()
        val (a, b) = kit.hand("Storm Spark").take(2).let { it[0] to (it.getOrNull(1) ?: it[0]) }
        val prevent = ReplacementOption(0, a, "If a source would deal damage to you, prevent 1 of that damage.")
        val double = ReplacementOption(1, b, "If a source would deal damage to you, it deals double that damage to you instead.")
        val q = show(Decision.ChooseReplacement(id("replacement"), kit.me, null, listOf(prevent, double)), null)
        compose.onAllNodesWithContentDescription("Drag to reorder").assertCountEquals(2)
        compose.onRoot().captureShot("$SCREENSHOTS/questions/replacement.png",
            "Which replacement effect applies first: the panel is titled \"Which applies first\" with its instruction and a full-width row per effect, numbered 1 and 2, each with a drag handle at its left, its card and its whole text, then OK.")
        val height = compose.onNodeWithContentDescription("Replacement: ${double.text}").fetchSemanticsNode().size.height.toFloat()
        compose.onAllNodesWithContentDescription("Drag to reorder")[1].performTouchInput {
            down(center)
            repeat(8) { moveBy(androidx.compose.ui.geometry.Offset(0f, -height / 8)) }
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithText("OK").performClick()
        assertEquals(listOf(double, prevent), q.preference)
        assertEquals(listOf<Answer>(Answer.Replacement(1)), q.answers)
    }

    // CR 616.1, 616.1f (owner gallery review, 2026-11-06): the preference list answers later questions among the same
    // effects (matched by source and text, numbered anew), skipping those no longer there; a new effect is asked
    @Test fun aReplacementPreferenceAnswersLaterQuestions() {
        val x = ObjectId.Card(arcanum.engine.state.CardId("x"), 0); val y = ObjectId.Card(arcanum.engine.state.CardId("y"), 0)
        val z = ObjectId.Card(arcanum.engine.state.CardId("z"), 0)
        val preference = listOf(ReplacementOption(1, y, "Y"), ReplacementOption(0, x, "X"), ReplacementOption(2, z, "Z"))
        val preferred = org.asyncttwiz.automatum.ReplacementPreference
        assertEquals(ReplacementOption(0, x, "X"), preferred.preferred(listOf(ReplacementOption(0, x, "X"), ReplacementOption(1, z, "Z")), preference))
        assertEquals(ReplacementOption(1, z, "Z"), preferred.preferred(listOf(ReplacementOption(1, z, "Z")), preference))
        assertEquals(null, preferred.preferred(listOf(ReplacementOption(0, x, "X"), ReplacementOption(1, z, "W")), preference))
    }

    // STK-030, CR 704.5j: the legend rule: no source, its sentence its instruction, the cards to keep one of
    @Test fun theLegendRule() {
        kit.opening()
        // Owner gallery review (2026-11-06, bug): the picture showed one card; its fixture took two Mountains of a hand
        // holding one
        val cards = kit.scenario.cards(kit.state(), kit.me, kit.state().player(kit.me).library, "Mountain").take(2)
        assertEquals(2, cards.size)
        show(Decision.LegendRule(id("legend"), kit.me, null, "Mountain", cards), null, withSource = false)
        compose.onNodeWithText("Tap the Mountain to keep; the others go to the graveyard.").assertExists()
        compose.onAllNodesWithContentDescription("Mountain").assertCountEquals(2)
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
