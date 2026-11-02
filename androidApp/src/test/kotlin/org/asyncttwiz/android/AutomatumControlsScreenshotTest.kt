package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.api.Layout
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Automatum I4 (integration spec §3 I4, I-D28, I-D37): with Automatum on, menus show the engine's actions for the card
 * and keep the manual ones behind "Manual…"; manual changes are refused with the reason. Invented cards: Grovewatch
 * Druid (a mana ability and a draw ability), Storm Spark, Forests and Mountains, parsed from their oracle text. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AutomatumControlsScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    private class Invented(val face: DefinitionFace, val oracle: OracleCard) {
        val submitted = CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val definition = arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(oracle)))
    }
    private val druidText = "{T}: Add {G}.\n{2}{G}, {T}: Draw a card."
    private val druid = Invented(DefinitionFace("druid", "Grovewatch Druid", typeLine = "Creature — Elf Druid", manaCost = "{1}{G}",
        rulesText = druidText, power = "1", toughness = "2"),
        OracleCard("Grovewatch Druid", "{1}{G}", "Creature — Elf Druid", druidText, "1", "2"))
    private val spark = Invented(DefinitionFace("spark", "Storm Spark", typeLine = "Instant", manaCost = "{R}",
        rulesText = "Storm Spark deals 3 damage to any target."),
        OracleCard("Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target."))
    private val forest = Invented(DefinitionFace("forest", "Forest", typeLine = "Basic Land — Forest"),
        OracleCard("Forest", null, "Basic Land — Forest"))
    private val mountain = Invented(DefinitionFace("mountain", "Mountain", typeLine = "Basic Land — Mountain"),
        OracleCard("Mountain", null, "Basic Land — Mountain"))
    private val invented = listOf(druid, spark, forest, mountain)

    private val field = f.id("a/BATTLEFIELD")
    private val hand = f.initial.board.containers.single { it.id == f.id("a/HAND") }.cards
    private val sparkId = hand[1]
    private val druidId = f.id("druid")
    private val forests = (0 until 3).map { f.id("forest-$it") }

    @Before fun definitions() {
        val byDigest = invented.associate { it.submitted.digest to it.definition }
        AutomatumBoards.definitionSource = { submitted -> submitted.mapNotNull { s ->
            byDigest[s.digest]?.let { s.digest to ParsedCard.Read(it, 0) } }.toMap() }
    }
    @After fun forget() { AutomatumBoards.reset(); AutomatumBoards.definitionSource = null }

    /** Alice's main phase, holding priority: Grovewatch Druid and [lands] Forests on her battlefield (her other lands
     * Mountains), Storm Spark in hand. */
    private fun board(lands: Int, automatum: Boolean = true): PlayState {
        val initial = f.initial
        val others = initial.board.cards.filter { it.containerId == field }.map { it.id }
        val added = listOf(PlayCard(druidId, druid.submitted.digest, f.a, field, "druid", covered = false, corner = "1/2",
            placement = PlayPlacement(BoardPosition(6, 1), druidId))) +
            forests.take(lands).mapIndexed { n, id -> PlayCard(id, forest.submitted.digest, f.a, field, "forest", covered = false,
                placement = PlayPlacement(BoardPosition(3 + n, 0), id)) }
        return initial.copy(definitions = initial.definitions + invented.map { it.submitted },
            board = initial.board.copy(automatum = automatum, priority = MtgPriority(f.a),
                cards = initial.board.cards.map { when (it.id) {
                    sparkId -> it.copy(definition = spark.submitted.digest, faceId = "spark")
                    in others -> it.copy(definition = mountain.submitted.digest, faceId = "mountain", tapped = true, corner = "")
                    else -> it
                } } + added,
                containers = initial.board.containers.map { if (it.id == field) it.copy(cards = it.cards + added.map(PlayCard::id)) else it }))
    }

    private val sent = mutableListOf<Pair<GameAction.PlayBatch, String>>()
    private val manual = mutableListOf<PlayAction>()
    private fun render(play: PlayState): TableToolkitController {
        AutomatumBoards.compute(play, f.a)
        val toolkit = TableToolkitController(f.a, { play }, { true }, { action, _ -> manual += action; true },
            automatedBatch = { batch, request -> sent += batch to request; true })
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    Box(Modifier.fillMaxSize()) {
                        TableSurface(f.game, play, f.a, false, Modifier.fillMaxSize(), connected = true,
                            onIntent = toolkit::handle, toolkit = toolkit,
                            runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, redo = { true }, controlsConnected = true))
                    }
                }
            }
        }
        compose.waitForIdle()
        return toolkit
    }

    private fun accepted(play: PlayState, batch: GameAction.PlayBatch, request: String): PlayState {
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), batch, f.id("client"), "1")
        val change = PlayBatches.change(GameplayTree(play), f.a, command)
        assertNull(change.reason, "the server takes the batch")
        val tree = assertNotNull(change.tree)
        return assertNotNull(tree.stateAt(tree.currentNodeId))
    }

    // TBL-115, TBL-116, I-D28: a battlefield card's menu lists its abilities with their text, the one Automatum doesn't
    // allow greyed with the reason on tap; today's actions are behind "Manual…", greyed while Automatum is on
    @Test fun battlefieldMenuShowsAbilitiesAndManual() {
        val play = board(lands = 2)
        val toolkit = render(play)
        compose.runOnIdle { toolkit.openCard(TableScope(druidId, listOf(druidId))) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("{T}: Add {G}.").assertIsEnabled()
        compose.onNodeWithContentDescription("{2}{G}, {T}: Draw a card.").assertIsNotEnabled()
        compose.onNodeWithContentDescription(CardRingPages.MANUAL).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Graveyard").assertDoesNotExist()
        captureShot("$SCREENSHOTS/automatum/menu-abilities.png",
            "With Automatum on, tapping Grovewatch Druid with two untapped Forests opens its menu with its abilities as rows: \"{T}: Add {G}.\" enabled and \"{2}{G}, {T}: Draw a card.\" greyed, then Inspect, Other side, Note, Ping, Declare, Link, Unlink and a greyed \"Manual…\".")
        compose.ringClick("{2}{G}, {T}: Draw a card.")
        compose.runOnIdle {
            assertEquals("Grovewatch Druid: Automatum doesn't allow that now (a cost it can't pay, or nothing to target).", toolkit.error)
        }
        compose.ringClick(CardRingPages.MANUAL)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(AutomatumDrags.MANUAL, toolkit.error) }
        captureShot("$SCREENSHOTS/automatum/menu-manual-refused.png",
            "Tapping the greyed \"Manual…\" in Grovewatch Druid's menu shows why at the bottom of the table: with Automatum on the game changes only by the rules; switch Automatum off to change it by hand.")
        assertTrue(sent.isEmpty() && manual.isEmpty())
    }

    // TBL-115, CR 602.2, 605.3a: activating from the menu goes through the cast panel ("Activate") and out as one batch
    // that the server takes; a mana ability adds its mana at once
    @Test fun activatingFromTheMenu() {
        val play = board(lands = 3)
        val toolkit = render(play)
        compose.runOnIdle { toolkit.openCard(TableScope(druidId, listOf(druidId))) }
        compose.waitForIdle()
        compose.ringClick("{2}{G}, {T}: Draw a card.")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Cast panel").assertExists()
        compose.onNodeWithText("Mana · {2}{G}").assertExists()
        compose.onNode(hasText("Activate") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertNull(toolkit.cast.flow)
        val (batch, request) = sent.single()
        val after = accepted(play, batch, request)
        assertTrue(after.board.cards.single { it.id == druidId }.tapped)
        assertEquals(hand.size + 1, after.board.containers.single { it.id == f.id("a/HAND") }.cards.size, "a card was drawn")
        sent.clear()
        compose.runOnIdle { toolkit.openCard(TableScope(druidId, listOf(druidId))) }
        compose.waitForIdle()
        compose.ringClick("{T}: Add {G}.")
        compose.waitForIdle()
        val (mana, manaRequest) = sent.single()
        assertEquals(listOf("G"), accepted(play, mana, manaRequest).board.players.single { it.accountId == f.a }.manaPool.map { it.color })
    }

    // ZON-007, TBL-115: a hand card's menu is its casts as rows, Inspect and "Manual…"
    @Test fun handMenuShowsCast() {
        val play = board(lands = 0).let { p -> p.copy(board = p.board.copy(cards = p.board.cards.map {
            if (it.definition == mountain.submitted.digest) it.copy(tapped = false) else it })) }
        val toolkit = render(play)
        compose.runOnIdle { toolkit.openCard(TableScope(sparkId, listOf(sparkId))) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Cast").assertIsEnabled()
        compose.onNodeWithContentDescription(CardRingPages.MANUAL).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Discard").assertDoesNotExist()
        captureShot("$SCREENSHOTS/automatum/menu-hand.png",
            "With Automatum on and Mountains untapped, Storm Spark's hand menu shows Automatum's \"Cast\" as a row above Inspect and a greyed \"Manual…\".")
    }

    // SYS-069, TRN-031, I-D28: manual changes are refused with the reason; what has no game effect still goes through
    @Test fun manualChangesRefused() {
        val play = board(lands = 2)
        val toolkit = render(play)
        compose.runOnIdle {
            assertFalse(toolkit.submit(PlayAction.AdjustLife(f.a, "-1")))
            assertEquals(AutomatumDrags.LIFE, toolkit.error)
            assertFalse(toolkit.submit(PlayAction.SetTapped(druidId, true)))
            assertEquals(AutomatumDrags.MANUAL, toolkit.error)
            assertFalse(toolkit.submit(PlayAction.CreateToken(forest.submitted)))
            assertTrue(manual.isEmpty())
            assertTrue(toolkit.submit(PlayAction.SetNote(druidId, "watch it")))
            assertTrue(toolkit.submit(PlayAction.AdjustCounter(CounterTarget.CARD, druidId, CounterColor.RED, "1")))
            assertTrue(toolkit.submit(PlayAction.RollDie("6", 4)))
        }
    }

    // TRN-059: quick actions that change the game by hand stay in place, greyed with the reason; moving the turn stays
    @Test fun manualQuickActionsGreyed() {
        val drawStep = board(lands = 2).let { p -> p.copy(board = p.board.copy(turn = p.board.turn.copy(
            phase = TurnPosition.DRAW.phase, step = TurnPosition.DRAW.step))) }
        val toolkit = TableToolkitController(f.a, { drawStep }, { true }, { _, _ -> true })
        val actions = QuickActions.contextual(drawStep, QuickContext(viewer = f.a), toolkit.table)
        val draw = actions.single { it.name == "Draw one" }
        assertFalse(draw.enabled)
        assertEquals(AutomatumDrags.MANUAL, draw.refused)
        val main = board(lands = 2)
        val offered = QuickActions.contextual(main, QuickContext(viewer = f.a), TableToolkitController(f.a, { main }, { true }, { _, _ -> true }).table)
        assertTrue(offered.single { it.name == "Skip combat" }.enabled, "moving the turn goes through Automatum")
    }

    // TBL-116: with Automatum off the menus are exactly as before: no "Manual…", today's pages
    @Test fun automatumOffKeepsTodaysMenu() {
        val play = board(lands = 2, automatum = false)
        val toolkit = render(play)
        compose.runOnIdle { toolkit.openCard(TableScope(druidId, listOf(druidId))) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(CardRingPages.MANUAL).assertDoesNotExist()
        compose.onNodeWithContentDescription("{T}: Add {G}.").assertDoesNotExist()
    }
}
