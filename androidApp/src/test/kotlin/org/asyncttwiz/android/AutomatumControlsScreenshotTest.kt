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
    private fun board(lands: Int): PlayState {
        val initial = f.initial
        val others = initial.board.cards.filter { it.containerId == field }.map { it.id }
        val added = listOf(PlayCard(druidId, druid.submitted.digest, f.a, field, "druid", covered = false, corner = "1/2",
            placement = PlayPlacement(BoardPosition(6, 1), druidId))) +
            forests.take(lands).mapIndexed { n, id -> PlayCard(id, forest.submitted.digest, f.a, field, "forest", covered = false,
                placement = PlayPlacement(BoardPosition(3 + n, 0), id)) }
        return initial.copy(definitions = initial.definitions + invented.map { it.submitted },
            board = initial.board.copy(priority = MtgPriority(f.a),
                cards = initial.board.cards.map { when (it.id) {
                    sparkId -> it.copy(definition = spark.submitted.digest, faceId = "spark")
                    in others -> it.copy(definition = mountain.submitted.digest, faceId = "mountain", tapped = true, corner = "")
                    else -> it
                } } + added,
                containers = initial.board.containers.map { if (it.id == field) it.copy(cards = it.cards + added.map(PlayCard::id)) else it }))
    }

    private val sent = mutableListOf<Pair<GameAction.PlayBatch, String>>()
    private val manual = mutableListOf<PlayAction>()
    /** [batches]: ordinary batches are sent too (recorded in [manual]). */
    private fun render(play: PlayState, batches: Boolean = false): TableToolkitController {
        AutomatumBoards.compute(play, f.a)
        val toolkit = TableToolkitController(f.a, { play }, { true }, { action, _ -> manual += action; true },
            playBatch = if (batches) { actions, _ -> manual += actions; true } else null,
            automatedBatch = { batch, request -> sent += batch to request; true })
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    Box(Modifier.fillMaxSize()) {
                        TableSurface(f.game, play, f.a, false, Modifier.fillMaxSize(), connected = true,
                            onIntent = toolkit::handle, toolkit = toolkit,
                            runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, controlsConnected = true))
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

    // TBL-115, TBL-116, TBL-119, I-D28, I-D46: a battlefield card's menu lists its abilities with their text, the one Automatum
    // doesn't allow greyed: a tap on it shakes it without a message, holding it says why; no "Manual…", no manual cells and
    // no virtual-tabletop cells
    @Test fun battlefieldMenuShowsAbilitiesOnly() {
        val play = board(lands = 2)
        val toolkit = render(play)
        compose.runOnIdle { toolkit.openCard(TableScope(druidId, listOf(druidId))) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("{T}: Add {G}.").assertIsEnabled()
        compose.onNodeWithContentDescription("{2}{G}, {T}: Draw a card.").assertIsNotEnabled()
        compose.onNodeWithContentDescription(MANUAL).assertDoesNotExist()
        compose.onNodeWithContentDescription("Graveyard").assertDoesNotExist()
        compose.onNodeWithContentDescription("Ping / Unping").assertDoesNotExist()
        captureShot("$SCREENSHOTS/automatum/menu-abilities.png",
            "With Automatum on, tapping Grovewatch Druid with two untapped Forests opens its menu with its abilities as rows: \"{T}: Add {G}.\" enabled and \"{2}{G}, {T}: Draw a card.\" greyed, then only Inspect, Other side and Attack →.")
        compose.ringClick("{2}{G}, {T}: Draw a card.")
        compose.runOnIdle { assertNull(toolkit.error, "a refused row only shakes") }
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription("{2}{G}, {T}: Draw a card.").performTouchInput { advanceEventTime(350); longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Grovewatch Druid: Automatum doesn't allow that now (a cost it can't pay, or nothing to target).").assertExists()
        captureShot("$SCREENSHOTS/automatum/menu-refused-reason.png",
            "Holding the greyed \"{2}{G}, {T}: Draw a card.\" row in Grovewatch Druid's menu shows why above it: Automatum doesn't allow that now (a cost it can't pay, or nothing to target).")
        assertTrue(sent.isEmpty() && manual.isEmpty())
    }

    // TBL-115, STK-024, CR 602.2, 605.3a: activating from the menu goes out as one batch that the server takes, paid by
    // Autotap without a panel (nothing to choose, I-D47); a mana ability adds its mana at once
    @Test fun activatingFromTheMenu() {
        val play = board(lands = 3)
        val toolkit = render(play)
        compose.runOnIdle { toolkit.openCard(TableScope(druidId, listOf(druidId))) }
        compose.waitForIdle()
        compose.ringClick("{2}{G}, {T}: Draw a card.")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Cast panel").assertDoesNotExist()
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

    // SYS-069, I-D28: manual changes are refused with the reason, also what the tabletop used to allow (notes, colour
    // counters, dice: the server's own check, AutomatumOnly, pivot review #7); passing the move still goes through
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
            for (gone in listOf(PlayAction.SetNote(druidId, "watch it"), PlayAction.AdjustCounter(CounterTarget.CARD, druidId,
                    CounterColor.RED, "1"), PlayAction.RollDie("6", 4))) {
                assertFalse(toolkit.submit(gone), "$gone")
                assertEquals(AutomatumDrags.MANUAL, toolkit.error)
            }
            assertTrue(manual.isEmpty())
            assertTrue(toolkit.submit(PlayAction.PassAttention))
        }
    }

    // TRN-059, I-D46: with Automatum on the quick-actions row is the viewer's Automatum toggles, on either player's turn;
    // no manual quick action (the draws, Skip combat) is there
    @Test fun quickActionsAreAutomatumToggles() {
        val drawStep = board(lands = 2).let { p -> p.copy(board = p.board.copy(turn = p.board.turn.copy(
            phase = TurnPosition.DRAW.phase, step = TurnPosition.DRAW.step))) }
        assertTrue(AutomatumToggles.shown(drawStep, QuickContext(viewer = f.a)))
        assertTrue(AutomatumToggles.shown(drawStep, QuickContext(viewer = f.b)), "also on the other player's turn")
        render(drawStep)
        compose.onNodeWithContentDescription(AutomatumToggles.FULL_CONTROL).assertIsDisplayed()
        compose.onNodeWithContentDescription(AutomatumToggles.SKIP).assertIsDisplayed()
        compose.onNodeWithContentDescription(AutomatumToggles.AUTOTAP).assertIsDisplayed()
        compose.onNodeWithContentDescription("Draw one").assertDoesNotExist()
        captureShot("$SCREENSHOTS/automatum/quick-toggles.png",
            "With Automatum on, Alice's quick-actions row in her draw step is three toggles, Full control (off), Skip if no choice (on) and Autotap mana (on), beside the step arrow; there are no manual quick actions.")
    }

    // I-D46 (c), SYS-063, SYS-071, SYS-003, I-D47: the toggles change Skip when I have no choice in the game (sent at once,
    // no confirmation or Send), Full control and
    // Autotap mana on the device; holding one says what it does
    @Test fun togglesChangeTheSettings() {
        val play = board(lands = 2)
        render(play)
        compose.onNodeWithContentDescription(AutomatumToggles.SKIP).performClick()
        compose.waitForIdle()
        assertEquals(listOf<PlayAction>(PlayAction.Mtg(MtgAction.SetSkipWhenNoChoice(false))), manual)
        compose.onNodeWithContentDescription(AutomatumToggles.AUTOTAP).performClick()
        compose.onNodeWithContentDescription(AutomatumToggles.FULL_CONTROL).performClick()
        compose.waitForIdle()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        assertFalse(PausePreferences(context, f.a).settings.autotap, "the cast's own setting")
        assertTrue(PausePreferences(context, f.a).settings.fullControl)
        compose.onNodeWithContentDescription(AutomatumToggles.AUTOTAP).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Autotap mana sources: casting pays by itself, with mana in your pool first").assertExists()
    }

    // ZON-078, CR 305.1, 401.5 (final cast review #8): with a Seer of Harvestide-like card, the Library menu offers the
    // engine's land play of the top card above "Manual…", and playing it goes out as one batch the server takes
    @Test fun libraryMenuPlaysTheTopCard() {
        val seerText = "You may look at the top card of your library any time.\nYou may play lands from the top of your library."
        val seer = Invented(DefinitionFace("seer", "Furrow Seer", typeLine = "Creature — Human Druid", manaCost = "{1}{G}{G}",
            rulesText = seerText, power = "2", toughness = "3"),
            OracleCard("Furrow Seer", "{1}{G}{G}", "Creature — Human Druid", seerText, "2", "3"))
        val byDigest = (invented + seer).associate { it.submitted.digest to it.definition }
        AutomatumBoards.definitionSource = { submitted -> submitted.mapNotNull { s ->
            byDigest[s.digest]?.let { s.digest to ParsedCard.Read(it, 0) } }.toMap() }
        val library = f.id("a/LIBRARY")
        val base = board(lands = 0)
        val top = base.board.containers.single { it.id == library }.cards.first()
        val play = base.copy(definitions = base.definitions + seer.submitted, board = base.board.copy(cards = base.board.cards.map {
            when (it.id) {
                druidId -> it.copy(definition = seer.submitted.digest, faceId = "seer", corner = "2/3")
                top -> it.copy(definition = forest.submitted.digest, faceId = "forest")
                else -> it
            }
        }))
        val toolkit = render(play)
        val pile = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false).items
            .first { it.zone == PlayZone.LIBRARY && it.id == library }
        compose.runOnIdle { toolkit.open(TableIntent.Actions(pile.id, null), pile) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Play · Forest").assertIsEnabled()
        compose.onNodeWithContentDescription(MANUAL).assertDoesNotExist()
        captureShot("$SCREENSHOTS/automatum/menu-library-top.png",
            "With Automatum on and a Furrow Seer (\"You may play lands from the top of your library\") on Alice's battlefield, tapping her Library opens its menu with only Automatum's \"Play · Forest\" row for the top card.")
        compose.ringClick("Play · Forest")
        compose.waitForIdle()
        val (batch, request) = sent.single()
        val after = accepted(play, batch, request)
        assertEquals(field, after.board.cards.single { it.id == top }.containerId, "the Forest was played from the top")
    }

    // ZON-078, I-D46: without anything the engine offers for the top card, tapping the Library opens no menu: it shakes
    @Test fun libraryWithoutTopCardPlaysShakes() {
        val play = board(lands = 0)
        val toolkit = render(play)
        val library = f.id("a/LIBRARY")
        val pile = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false).items.first { it.zone == PlayZone.LIBRARY && it.id == library }
        compose.runOnIdle { toolkit.open(TableIntent.Actions(pile.id, null), pile) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertNull(toolkit.panel)
            assertEquals(library, toolkit.shaking?.id)
            assertNull(toolkit.error)
        }
    }


    // TBL-117, I-D46 (a): the viewer's cards Automatum lets them use now glow: Storm Spark with Mountains untapped and the
    // Druid (its draw ability); lands with only their mana abilities don't; nothing of the other player's
    @Test fun playableCardsGlow() {
        val play = board(lands = 1).let { p -> p.copy(board = p.board.copy(cards = p.board.cards.map {
            if (it.definition == mountain.submitted.digest) it.copy(tapped = false) else it })) }
        val toolkit = render(play)
        val playable = compose.runOnIdle { toolkit.playable(play) }
        assertTrue(druidId in playable && sparkId in playable, playable.toString())
        assertFalse(forests[0] in playable, "a mana ability alone doesn't glow")
        assertTrue(playable.none { id -> play.board.cards.single { it.id == id }.definition == mountain.submitted.digest })
        assertTrue(playable.none { id -> play.board.cards.single { it.id == id }.owner != f.a }, "only the viewer's own cards")
        captureShot("$SCREENSHOTS/automatum/playable-glow.png",
            "With Automatum on in Alice's main phase, her cards she can use now glow softly in cyan from behind: Storm Spark in her hand and Grovewatch Druid (its draw ability); her Forest and untapped Mountains, which offer only mana, don't.")
    }

    // I-D46 (b), ZON-004, ZON-077: a double-tap or drag Automatum doesn't allow shakes the card instead of a message, and
    // Inspect says why; a battlefield card's double-tap (tapping by hand) and a life drag shake too
    @Test fun refusedGesturesShake() {
        val play = board(lands = 2)
        val toolkit = render(play)
        compose.runOnIdle {
            toolkit.playable(play)
            val hand = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false).items
                .first { it.kind == TableItemKind.CARD && it.cardId == sparkId }
            assertFalse(toolkit.handle(TableIntent.Move(TableScope(sparkId, listOf(sparkId)),
                assertNotNull(handDoubleTapDestination(TablePresentation.scene(f.game, play, f.a, 412f, 892f, false), hand)),
                handDoubleTap = true)))
            assertEquals(sparkId, toolkit.shaking?.id)
            assertNull(toolkit.error)
            // A tapped Mountain (no mana ability Automatum allows now, TBL-043): untapping it by hand shakes it
            val tappedMountain = play.board.cards.first { it.definition == mountain.submitted.digest && it.tapped }.id
            assertFalse(toolkit.handle(TableIntent.Rotate(TableScope(tappedMountain, listOf(tappedMountain)), false, toggle = true)))
            assertEquals(tappedMountain, toolkit.shaking?.id)
            assertFalse(toolkit.handle(TableIntent.Move(TableScope(druidId, listOf(druidId)),
                TableDestination.Container(f.id("a/GRAVEYARD"), PlayZone.GRAVEYARD), drag = true)))
            assertEquals(druidId, toolkit.shaking?.id)
            assertNull(toolkit.error)
            assertTrue(manual.isEmpty() && sent.isEmpty())
            assertEquals(listOf("Cast — Storm Spark can't be cast now."), automatumReasons(play, f.a, sparkId, emptyMap()))
        }
        compose.runOnIdle { toolkit.inspect(sparkId) }
        compose.waitForIdle()
        compose.onNodeWithText("Cast — Storm Spark can't be cast now.", substring = true).assertExists()
        captureShot("$SCREENSHOTS/automatum/inspect-reason.png",
            "With every Mountain tapped, double-tapping Storm Spark only shakes it; inspecting it shows why under the card: \"Cast — Storm Spark can't be cast now.\"")
    }

    // TRN-010, TBL-119, I-D46, I-D50: with Automatum on the Table actions have no combat area, token, die or coin entries
    @Test fun tableActionsWithoutManualEntries() {
        val toolkit = render(board(lands = 2))
        compose.runOnIdle { toolkit.tableMenu() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Create token").assertDoesNotExist()
        compose.onNodeWithContentDescription("Create combat area").assertDoesNotExist()
        compose.onNodeWithContentDescription("Flip coin").assertDoesNotExist()
        compose.onNodeWithContentDescription("Timeline").assertExists()
    }

    // TRN-064, TRN-010, TRN-046 (I-D52, CR 104.3a): Table actions offer Concede beside Suggest game end (My win or Draw
    // game); Concede asks first, and only its confirmation sends the concession
    @Test fun concedeAsksFirst() {
        val play = board(lands = 2)
        val intents = mutableListOf<TableActionIntent>()
        val toolkit = TableToolkitController(f.a, { play }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme { TableActionMenu(play, toolkit.table, {}, perform = { intents += it; true }) } }
        compose.onNodeWithContentDescription("Suggest game end").assertExists()
        compose.onNodeWithContentDescription("Concede").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("You lose this game at once.").assertExists()
        assertTrue(intents.isEmpty(), "nothing is sent before the confirmation")
        captureShot("$SCREENSHOTS/automatum/concede-ask.png",
            "Concede in Table actions asks first: \"Concede?\", \"You lose this game at once.\", with Cancel and a red Concede.")
        compose.onNodeWithText("Concede").performClick()
        compose.waitForIdle()
        assertEquals(listOf(EndingTool.CONCEDE), intents.map { (it as TableActionIntent.Ending).tool })
    }


    // TRN-010 (problem report N7FU6Y): Table actions has Send problem report, which opens the same report as Settings'
    // "Report a problem" without leaving the game
    @Test fun tableActionsSendAProblemReport() {
        val play = board(lands = 2)
        val toolkit = TableToolkitController(f.a, { play }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme { TableActionMenu(play, toolkit.table, {}, perform = { true }, reportToken = { null }) } }
        captureShot("$SCREENSHOTS/automatum/table-actions-report.png",
            "Table actions with Timeline, Suggest game end, Concede and Send problem report, each an icon and its name.")
        compose.onNodeWithContentDescription("Send problem report").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Report a problem").assertExists()
        compose.onNodeWithTag("report-description").assertExists()
    }

    // TRN-062, TRN-059, I-D49, I-D48: during Alice's opening with Automatum on her quick actions are Mulligan and Keep (icon
    // and text) and her hand cards' menu has no bottom; after two mulligans Keep opens the Keep / Bottom screen: OK only
    // with one card in Bottom, then that card goes to the bottom of her library and she is Ready, in one batch
    @Test fun openingMulliganKeepAndBottom() {
        val base = board(lands = 2)
        val play = base.copy(board = base.board.copy(turn = base.board.turn.copy(number = "1"), priority = null,
            players = base.board.players.map { it.copy(status = PlayerStatus.OPENING, mulligans = if (it.accountId == f.a) 2 else 0) }))
        val toolkit = render(play, batches = true)
        compose.onNodeWithContentDescription(QuickActions.MULLIGAN).assertIsEnabled()
        compose.onNodeWithContentDescription(QuickActions.KEEP).assertIsEnabled()
        compose.onNodeWithContentDescription("Mull N").assertDoesNotExist()
        compose.onNodeWithContentDescription(AutomatumToggles.FULL_CONTROL).assertDoesNotExist()
        captureShot("$SCREENSHOTS/automatum/opening-quick-actions.png",
            "During Alice's opening in a game with Automatum on, her quick-actions row is Mulligan and Keep, each an icon with its name, beside the step arrow, greyed until both players are Ready.")
        compose.runOnIdle { toolkit.openCard(TableScope(sparkId, listOf(sparkId))) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Library bottom").assertDoesNotExist()
        compose.runOnIdle { toolkit.dismiss() }
        compose.onNodeWithContentDescription(QuickActions.KEEP).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(AutomatumOpening.title(1)).assertIsDisplayed()
        val ok = hasText("OK") and hasClickAction()
        compose.onNode(ok).assertIsNotEnabled()
        compose.onNode(hasContentDescription("Storm Spark") and hasAnyAncestor(hasContentDescription("Keep area"))).performClick()
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Storm Spark") and hasAnyAncestor(hasContentDescription("Bottom area"))).assertExists()
        compose.onNode(ok).assertIsEnabled()
        captureShot("$SCREENSHOTS/automatum/opening-keep-bottom.png",
            "After two mulligans Alice tapped Keep: the panel above her hand reads \"Mulligan · put 1 on the bottom\", her other hand cards are in Keep, Storm Spark (tapped once) is in Bottom, and OK is enabled.")
        assertTrue(manual.isEmpty(), "nothing is sent before OK")
        compose.onNode(ok).performClick()
        compose.waitForIdle()
        val move = manual.filterIsInstance<PlayAction.MoveCard>().single()
        assertEquals(sparkId, move.cardId)
        assertEquals(MoveInsertion.BOTTOM, move.destination.insertion)
        assertEquals(PlayAction.SetStatus(""), manual.last())
        compose.runOnIdle { assertFalse(toolkit.keeping) }
    }

    private companion object { const val MANUAL = "Manual…" }
}
