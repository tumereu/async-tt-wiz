package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import arcanum.api.Layout
import arcanum.engine.decision.Decision
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ParsedCard
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Automatum's choices and step arrow after the 2026-11-03 playtest (I-D46 d, I-D47, I-D48): titled choice panels, cards
 * drawn as cards, the reusable two-area screen (scry, surveil, discarding) and the step arrow's wording. Invented cards
 * parsed from their oracle text as on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AutomatumChoicesScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    private class Invented(val face: DefinitionFace, val oracle: OracleCard, byHand: Boolean = false) {
        val submitted = CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val definition = arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(oracle).let { parsed ->
            // Something to do by hand (D61), as the engine's own manual steps are
            if (!byHand) parsed else parsed.copy(abilities = listOf(arcanum.api.Ability.Spell(
                arcanum.api.Instructions(steps = listOf(arcanum.api.Step.Manual(oracle.text.orEmpty()))), text = oracle.text)))
        }))
    }
    /** A sorcery whose text Automatum can't read: it is done by hand (D61). */
    private val odd = "Rearrange the permanents on the battlefield however you like.".let { text -> Invented(
        DefinitionFace("odd", "Odd Rearrangement", typeLine = "Sorcery", manaCost = "{R}", rulesText = text),
        OracleCard("Odd Rearrangement", "{R}", "Sorcery", text, null, null), byHand = true) }
    private fun invented(id: String, name: String, cost: String?, type: String, text: String, pt: String? = null) = Invented(
        DefinitionFace(id, name, typeLine = type, manaCost = cost.orEmpty(), rulesText = text, corner = pt.orEmpty()),
        OracleCard(name, cost, type, text, pt?.substringBefore("/"), pt?.substringAfter("/")))
    private val spark = invented("spark", "Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target.")
    private val mountain = invented("mountain", "Mountain", null, "Basic Land — Mountain", "")
    private val mite = invented("mite", "Tinkered Mite", "{0}", "Artifact Creature — Construct", "", "1/1")
    private val divination = invented("divination", "Ember Divination", "{R}", "Sorcery", "Scry 3.")
    private val survey = invented("survey", "Grim Survey", "{R}", "Sorcery", "Surveil 2.")
    private val rummage = invented("rummage", "Ember Rummage", "{R}", "Sorcery", "Draw two cards, then discard a card.")
    private val all = listOf(spark, mountain, mite, divination, survey, rummage, odd)

    private val aHand = f.id("a/HAND")
    private val bHand = f.id("b/HAND")
    private val aField = f.id("a/BATTLEFIELD")
    private val bField = f.id("b/BATTLEFIELD")
    private val aLibrary = f.id("a/LIBRARY")
    private val aGraveyard = f.id("a/GRAVEYARD")
    private val aCard get() = f.initial.board.containers.single { it.id == aHand }.cards[0]
    private val bSpark get() = f.initial.board.containers.single { it.id == bHand }.cards[0]

    @Before fun definitions() {
        val byDigest = all.associate { it.submitted.digest to it.definition }
        AutomatumBoards.definitionSource = { submitted -> submitted.mapNotNull { s ->
            byDigest[s.digest]?.let { s.digest to ParsedCard.Read(it, 0) } }.toMap() }
    }
    @After fun forget() { AutomatumBoards.reset(); AutomatumBoards.definitionSource = null }

    /**
     * Alice's main phase with Automatum on: Alice holds [spell] and has two Mountains, her library's top cards are Storm
     * Spark, Tinkered Mite and Mountain; Bob holds Storm Spark when [bobSpark] and has one Mountain. Every other hand card
     * is out of the game.
     */
    private fun board(spell: Invented = spark, bobSpark: Boolean = false): PlayState {
        val initial = f.initial
        val aLands = initial.board.cards.filter { it.containerId == aField }.take(2).map { it.id }
        val bLands = initial.board.cards.filter { it.containerId == bField }.take(1).map { it.id }
        val library = initial.board.containers.single { it.id == aLibrary }.cards
        val top = library.take(3).zip(listOf(spark, mite, mountain)).toMap()
        val kept = setOf(aCard) + if (bobSpark) setOf(bSpark) else emptySet()
        val dropped = initial.board.cards.filter { (it.containerId == aHand || it.containerId == bHand) && it.id !in kept }.map { it.id }.toSet()
        fun as_(card: PlayCard, what: Invented) = card.copy(definition = what.submitted.digest, faceId = what.face.faceId, corner = what.face.corner,
            tapped = false)
        return initial.copy(definitions = initial.definitions + all.map { it.submitted },
            board = initial.board.copy(automatum = true, priority = MtgPriority(f.a),
                cards = initial.board.cards.filter { it.id !in dropped }.map { card -> when (card.id) {
                    aCard -> as_(card, spell)
                    bSpark -> as_(card, spark)
                    in aLands, in bLands -> as_(card, mountain)
                    in top -> as_(card, top.getValue(card.id))
                    else -> card
                } },
                containers = initial.board.containers.map { it.copy(cards = it.cards.filter { id -> id !in dropped }) }))
    }

    private var tree: GameplayTree? = null

    private fun accepted(play: PlayState, batch: GameAction.PlayBatch, request: String, actor: String): PlayState {
        val history = tree?.takeIf { it.stateAt(it.currentNodeId)?.board == play.board } ?: GameplayTree(play)
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), batch, f.id("client"), "1")
        val change = PlayBatches.change(history, actor, command)
        assertNull(change.reason, "the server takes the batch")
        val next = assertNotNull(change.tree)
        tree = next
        return assertNotNull(next.stateAt(next.currentNodeId))
    }
    private fun tags(play: PlayState) = engineTags(tree?.takeIf { it.stateAt(it.currentNodeId)?.board == play.board }, play.currentNodeId)

    private val sent = mutableListOf<Pair<GameAction.PlayBatch, String>>()
    private fun toolkit(play: PlayState, viewer: String) = TableToolkitController(viewer, { play }, { true }, { _, _ -> true },
        automatedBatch = { batch, request -> sent += batch to request; true }, playTree = { tree })

    /** Alice casts the spell in her hand (no targets) through Automatum, as the app sends it. */
    private fun aliceCasts(play: PlayState): PlayState {
        sent.clear()
        val toolkit = toolkit(play, f.a)
        assertTrue(toolkit.cast.start(aCard), toolkit.error)
        if (toolkit.cast.open) assertTrue(toolkit.cast.cast(), toolkit.error)
        val (batch, request) = sent.single()
        sent.clear()
        return accepted(play, batch, request, f.a)
    }

    private fun render(play: PlayState, viewer: String): TableToolkitController {
        AutomatumBoards.compute(play, viewer)
        AutomatumBoards.sharedFlow(play, tags(play))
        val toolkit = toolkit(play, viewer)
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    Box(Modifier.fillMaxSize()) {
                        TableSurface(f.game, play, viewer, false, Modifier.fillMaxSize(), connected = true,
                            onIntent = toolkit::handle, toolkit = toolkit,
                            runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, redo = { true }, controlsConnected = true))
                    }
                }
            }
        }
        compose.waitForIdle()
        return toolkit
    }

    private fun card(name: String) = compose.onAllNodes(hasContentDescription(name) and hasClickAction()).onFirst()
    private fun cards(play: PlayState, container: String) = play.board.containers.single { it.id == container }.cards
    private fun name(play: PlayState, id: String) = CastController.cardName(play, id)

    // STK-043, STK-030, I-D47, I-D48, CR 701.22: Alice's scry is a titled two-area screen: the three cards start on Top; a tap moves
    // one to Bottom at once; OK puts it on the bottom and the rest on top in their order, one step
    @Test fun scryIsATwoAreaScreen() {
        val open = aliceCasts(board(divination))
        val flow = assertNotNull(AutomatumBoards.sharedFlow(open, tags(open)))
        assertEquals("Put on the bottom", assertIs<Decision.ChooseObjects>(flow.decision).text)
        val library = cards(open, aLibrary)
        render(open, f.a)
        compose.onNodeWithText("Scry 3 · Ember Divination").assertExists()
        compose.onNodeWithText("Top · 3").assertExists()
        card("Tinkered Mite").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Top · 2").assertExists()
        compose.onNodeWithText("Bottom · 1").assertExists()
        captureShot("$SCREENSHOTS/automatum/choice-scry.png",
            "Alice cast Ember Divination (Scry 3) with Automatum on: the panel is titled \"Scry 3 · Ember Divination\" with a short instruction, her top three cards are drawn as cards in a Top area, numbered in order, and a tap on Tinkered Mite moved it into the Bottom area at once; OK sits in the bottom row beside the eye.")
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        val (batch, request) = sent.single()
        val after = accepted(open, batch, request, f.a)
        assertFalse(Automatum.flowOpen(after.board), "the orders went with the answer")
        val now = cards(after, aLibrary)
        assertEquals(library[1], now.last(), "Tinkered Mite on the bottom")
        assertEquals(listOf(library[0], library[2]), now.take(2), "the rest on top in their order")
    }

    // STK-031, I-D27, I-D48: Bob choosing for Alice sees her scried cards only as covered cards
    @Test fun choosingForTheOtherPlayerShowsTheirCardsCovered() {
        val open = aliceCasts(board(divination))
        render(open, f.b)
        compose.onNodeWithText("Choose for them").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Choose for them")[1].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Scry 3 · Ember Divination").assertExists()
        compose.onAllNodes(hasContentDescription("Hidden card") and hasClickAction()).assertCountEquals(3)
        compose.onAllNodes(hasContentDescription("Tinkered Mite") and hasClickAction()).assertCountEquals(0)
        captureShot("$SCREENSHOTS/automatum/choice-scry-for-other.png",
            "Bob chose to answer Alice's Scry 3 for her: the same two-area screen marked \"Choosing for Alice\" shows her library's cards only as covered cards, which he can still move between Top and Bottom.")
    }

    // STK-043, I-D48, CR 701.25: surveil's two areas are Top and Graveyard; a card tapped into Graveyard goes there
    @Test fun surveilIsATwoAreaScreen() {
        val open = aliceCasts(board(survey))
        val library = cards(open, aLibrary)
        render(open, f.a)
        compose.onNodeWithText("Surveil 2 · Grim Survey").assertExists()
        card("Storm Spark").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Graveyard · 1").assertExists()
        captureShot("$SCREENSHOTS/automatum/choice-surveil.png",
            "Alice cast Grim Survey (Surveil 2): \"Surveil 2 · Grim Survey\" with her top two cards as cards, Storm Spark tapped into the Graveyard area and Tinkered Mite left on Top.")
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        val (batch, request) = sent.single()
        val after = accepted(open, batch, request, f.a)
        assertTrue(library[0] in cards(after, aGraveyard))
        assertEquals(library[1], cards(after, aLibrary).first())
    }

    // STK-043, I-D48: discarding is a choice between Hand and Discard; OK waits until exactly the number asked is there
    @Test fun discardIsATwoAreaScreen() {
        val open = aliceCasts(board(rummage))
        val flow = assertNotNull(AutomatumBoards.sharedFlow(open, tags(open)))
        val discard = assertIs<Decision.ChooseObjects>(flow.decision)
        val drawn = assertNotNull(flow.atwCard(discard.options.first()))
        render(open, f.a)
        compose.onNodeWithText("Discard 1 · Ember Rummage").assertExists()
        compose.onNodeWithText("OK").assertIsNotEnabled()
        card(name(open, drawn)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("OK").assertIsEnabled()
        captureShot("$SCREENSHOTS/automatum/choice-discard.png",
            "Alice cast Ember Rummage, drew two and must discard one: \"Discard 1 · Ember Rummage\" shows her hand as cards in a Hand area, one tapped into the Discard area, and OK beside the eye is enabled once exactly one is there.")
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        val (batch, request) = sent.single()
        val after = accepted(open, batch, request, f.a)
        assertTrue(drawn in cards(after, aGraveyard))
    }

    // STK-030, I-D47: a trigger's "may" is titled with what it is and where it comes from
    @Test fun aChoiceSaysWhatItIsAndWhereItComesFrom() {
        assertEquals("You may", choiceWhat(Decision.May(arcanum.engine.decision.DecisionId("d"), arcanum.engine.state.PlayerId(f.a), null, "draw")))
        assertEquals("Scry 2", ObjectsShape.of(objects("Put on the bottom", 2, 0, 2)).title)
    }

    private fun objects(text: String, n: Int, min: Int, max: Int?) = Decision.ChooseObjects(arcanum.engine.decision.DecisionId("d"),
        arcanum.engine.state.PlayerId(f.a), null, List(n) { arcanum.engine.state.ObjectId.Card(arcanum.engine.state.CardId("c$it"), 0) }, min, max, text)

    /** [play] at [position] of Alice's turn with [holder] holding priority. */
    private fun at(play: PlayState, position: TurnPosition, holder: String?) = play.copy(board = play.board.copy(
        turn = play.board.turn.copy(phase = position.phase, step = position.step), priority = MtgPriority(holder)))

    // TRN-063, I-D46 d: with Automatum on the step arrow says what a tap does, in a neutral colour, an icon above its word
    @Test fun stepArrowStates() {
        val play = board()
        val attacker = play.board.cards.first { it.containerId == aField }.id
        val blocker = play.board.cards.first { it.containerId == bField }.id
        val attack = at(play, TurnPosition.DECLARE_ATTACKERS, null).let { it.copy(board = it.board.copy(combat = CombatArea(f.id("area"),
            listOf(CombatPair(f.id("pair"), attackers = listOf(CombatOccurrence(f.id("occ"), attacker))))))) }
        val block = at(attack, TurnPosition.DECLARE_BLOCKERS, null).let { it.copy(board = it.board.copy(combat = it.board.combat!!.copy(
            pairs = it.board.combat!!.pairs.map { p -> p.copy(blockers = listOf(CombatOccurrence(f.id("blk"), blocker))) }))) }
        val stacked = play.copy(board = play.board.copy(priority = MtgPriority(f.b), stackEffects = listOf(StackEffect(f.id("entry"), aCard,
            item = MtgStackItem(MtgRef.Card(aCard, 0), MtgStackKind.Spell, f.a, timestamp = 1))),
            containers = play.board.containers.map { if (it.zone == PlayZone.STACK) it.copy(cards = listOf(f.id("entry"))) else it }))
        val states = listOf(
            "To combat" to play,
            "Pass" to at(play, TurnPosition.PRECOMBAT_MAIN, f.b),
            "Next" to at(play, TurnPosition.UPKEEP, f.a),
            "Attack (1)" to attack,
            "No attack" to at(play, TurnPosition.DECLARE_ATTACKERS, null),
            "Block (1)" to block,
            "No blocks" to at(play, TurnPosition.DECLARE_BLOCKERS, null),
            "Resolve" to stacked,
            "End turn" to at(play, TurnPosition.POSTCOMBAT_MAIN, f.a),
            "End turn" to at(play, TurnPosition.END_STEP, f.a))
        for ((label, state) in states) assertEquals(label, AutomatumStep.of(state).label, "${TurnPosition.of(state.board.turn)}")
        compose.setContent {
            MaterialTheme { NoRipples {
                FlowRowOfArrows(states.map { it.second }.distinctBy { AutomatumStep.of(it).label })
            } }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Attack (1)").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/automatum/step-arrow-states.png",
            "The step arrow with Automatum on in each situation, white with a grey outline and an icon above what a tap does: To combat (crossed swords, own main phase), Pass (the other player's priority), Next (a plain arrow), Attack (1) under crossed swords and No attack (declaring attackers with one or no creature in the lanes), Block (1) and No blocks, Resolve (a spell on the Stack) and End turn (from the second main phase on).")
    }

    @OptIn(ExperimentalLayoutApi::class)
    @androidx.compose.runtime.Composable
    private fun FlowRowOfArrows(boards: List<PlayState>) {
        FlowRow(Modifier.background(Color(0xffeef1f0)).padding(8.dp).width(360.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (state in boards) AutomatumStepArrow(state, enabled = true, compact = false, onHold = {}) {}
        }
    }

    // TRN-063, STK-032, I-D46 d, I-D37: while Automatum waits for something done by hand the step arrow says Done, and a tap
    // is Done: the spell finishes resolving
    @Test fun doneOnTheStepArrowFinishesAStepDoneByHand() {
        val open = aliceCasts(board(odd))
        assertTrue(Automatum.flowOpen(open.board))
        assertEquals(AutomatumStep.Done, AutomatumStep.of(open))
        render(open, f.a)
        compose.onNodeWithText("By hand · Odd Rearrangement").assertExists()
        compose.onNodeWithContentDescription("Done").performClick()
        compose.waitForIdle()
        val (batch, request) = sent.single()
        val after = accepted(open, batch, request, f.a)
        assertFalse(Automatum.flowOpen(after.board))
        assertTrue(after.board.stackEffects.isEmpty())
    }

    // TRN-063, STK-029, I-D46 d: with Alice's spell on the Stack, Bob (who could answer) holds priority: his step arrow says
    // Resolve, and a tap passes as Let it resolve does, so the spell resolves
    @Test fun resolveOnTheStepArrowPasses() {
        val play = board(bobSpark = true)
        val alice = toolkit(play, f.a)
        assertTrue(alice.cast.start(aCard), alice.error)
        val target = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false).items.first { it.id == "life/${f.b}" }
        alice.open(TableIntent.Actions(target.id, null), target, TableSelection())
        // STK-023: with Autotap on the cast goes out once its target is chosen
        if (alice.cast.open) assertTrue(alice.cast.cast(), alice.error)
        val (batch, request) = sent.single()
        sent.clear()
        val cast = accepted(play, batch, request, f.a)
        assertEquals(f.b, cast.board.priority?.holder, "Bob may respond")
        assertEquals(AutomatumStep.Resolve, AutomatumStep.of(cast))
        render(cast, f.b)
        compose.onNodeWithContentDescription("Resolve").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/automatum/step-arrow-resolve.png",
            "Alice cast Storm Spark at Bob, who holds one too: above his hand Bob sees the respond panel with Respond (no Let it resolve of its own), and his step arrow in the bottom-right corner, neutral white, reads \"Resolve\" under a play icon: the one way to let it resolve.")
        compose.onNodeWithContentDescription("Resolve").performClick()
        compose.waitForIdle()
        val (passed, id) = sent.single()
        val after = accepted(cast, passed, id, f.b)
        assertTrue(after.board.stackEffects.isEmpty(), "Storm Spark resolved")
    }
}
