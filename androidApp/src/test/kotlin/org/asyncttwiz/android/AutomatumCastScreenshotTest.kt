package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.api.Layout
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.automatum.EngineState
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

/** Automatum I2a (integration spec §3 I2, I-D6, I-D11): casting from the hand through the engine, the cast panel's
 * sections, targets on the table, the mana section, and what the engine doesn't allow. Invented cards: Storm Spark
 * (instant, 3 damage to any target), Flash Surge (kicker) and Mountains, parsed from their oracle text as on a device. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AutomatumCastScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    private class Invented(val face: DefinitionFace, val oracle: OracleCard) {
        val submitted = CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val definition = arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(oracle)))
    }
    private val spark = Invented(DefinitionFace("spark", "Storm Spark", typeLine = "Instant", manaCost = "{R}",
        rulesText = "Storm Spark deals 3 damage to any target."),
        OracleCard("Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target."))
    private val surge = Invented(DefinitionFace("surge", "Flash Surge", typeLine = "Instant", manaCost = "{R}",
        rulesText = "Kicker {4}\nFlash Surge deals 2 damage to any target. If this spell was kicked, it deals 4 damage instead."),
        OracleCard("Flash Surge", "{R}", "Instant", "Kicker {4} (You may pay an additional {4} as you cast this spell.)\n" +
            "Flash Surge deals 2 damage to any target. If this spell was kicked, it deals 4 damage instead."))
    private val mountain = Invented(DefinitionFace("mountain", "Mountain", typeLine = "Basic Land — Mountain"),
        OracleCard("Mountain", null, "Basic Land — Mountain"))
    private val invented = listOf(spark, surge, mountain)

    private val hand = f.initial.board.containers.single { it.id == f.id("a/HAND") }.cards
    private val sparkId = hand[1]
    private val surgeId = hand[2]
    private val landId = hand[0]
    private val field = f.id("a/BATTLEFIELD")
    private val extraMountains = (0 until 3).map { f.id("extra-mountain-$it") }

    @Before fun definitions() {
        val byDigest = invented.associate { it.submitted.digest to it.definition }
        AutomatumBoards.definitionSource = { submitted -> submitted.mapNotNull { s ->
            byDigest[s.digest]?.let { s.digest to ParsedCard.Read(it, 0) } }.toMap() }
    }
    @After fun forget() { AutomatumBoards.reset(); AutomatumBoards.definitionSource = null }

    /** Alice's main phase with Automatum on: Storm Spark, Flash Surge and a Mountain in hand; five Mountains on her
     * battlefield ([tapped]: all tapped). */
    private fun board(tapped: Boolean = false): PlayState {
        val initial = f.initial
        val lands = initial.board.cards.filter { it.containerId == field }.take(2).map { it.id }
        val extra = extraMountains.mapIndexed { n, id -> PlayCard(id, mountain.submitted.digest, f.a, field, "mountain",
            covered = false, tapped = tapped, placement = PlayPlacement(BoardPosition(2 + n, 0), id)) }
        return initial.copy(definitions = initial.definitions + invented.map { it.submitted },
            board = initial.board.copy(automatum = true, priority = MtgPriority(f.a),
                cards = initial.board.cards.map { when (it.id) {
                    sparkId -> it.copy(definition = spark.submitted.digest, faceId = "spark")
                    surgeId -> it.copy(definition = surge.submitted.digest, faceId = "surge")
                    landId -> it.copy(definition = mountain.submitted.digest, faceId = "mountain")
                    in lands -> it.copy(definition = mountain.submitted.digest, faceId = "mountain", tapped = tapped, corner = "")
                    else -> it
                } } + extra,
                containers = initial.board.containers.map { if (it.id == field) it.copy(cards = it.cards + extra.map(PlayCard::id)) else it }))
    }

    private val tappedSource = hasContentDescription("Mana source", substring = true) and hasContentDescription(", tapped", substring = true)
    private val sent = mutableListOf<Pair<GameAction.PlayBatch, String>>()
    private fun render(play: PlayState): TableToolkitController {
        AutomatumBoards.compute(play, f.a)
        val toolkit = TableToolkitController(f.a, { play }, { true }, { _, _ -> true },
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

    private fun doubleTap(toolkit: TableToolkitController, card: String): Boolean = toolkit.handle(TableIntent.Move(
        TableScope(card, listOf(card)), TableDestination.Container(f.id("stack"), PlayZone.STACK), handDoubleTap = true))

    private fun lifeSquare(play: PlayState, player: String): TableItem =
        TablePresentation.scene(f.game, play, f.a, 412f, 892f, false).items.single { it.id == "life/$player" }

    /** The batch the server would make of [batch] (its own checks, SYS-057), as the new board. */
    private fun accepted(play: PlayState, batch: GameAction.PlayBatch, request: String): PlayState {
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), batch, f.id("client"), "1")
        val change = PlayBatches.change(GameplayTree(play), f.a, command)
        assertNull(change.reason, "the server takes the cast's batch")
        val tree = assertNotNull(change.tree)
        return assertNotNull(tree.stateAt(tree.currentNodeId))
    }

    // ZON-004, STK-023, STK-024, I-D6, I-D11: a double-tap casts; the panel shrinks to a bar while Bob is picked by his
    // life square, then shows the suggested tapping; "Cast" sends everything as one batch, which resolves it (STK-026)
    @Test fun doubleTapCastsWithTargetBarAndManaSection() {
        val play = board()
        val toolkit = render(play)
        compose.runOnIdle { assertTrue(doubleTap(toolkit, sparkId)) }
        compose.waitForIdle()
        assertTrue(toolkit.cast.targeting)
        assertTrue("life/${f.b}" in toolkit.cast.glow(play), "Bob's life square glows")
        compose.onNodeWithText("Storm Spark — choose any target").assertExists()
        assertTrue(sent.isEmpty(), "nothing is sent while the cast is open (I-D11)")
        compose.onRoot().captureShot("$SCREENSHOTS/automatum/cast-target-bar.png",
            "Double-tapping Storm Spark with Automatum on: the cast panel is a slim bar by the Stack (\"Storm Spark — choose any target\") while every legal target, cards and both life squares, glows violet.")
        compose.runOnIdle { toolkit.open(TableIntent.Actions("life/${f.b}", null), lifeSquare(play, f.b), TableSelection()) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Cast panel").assertExists()
        compose.onNodeWithText("Mana · {R}").assertExists()
        compose.onAllNodes(tappedSource).assertCountEquals(1)
        compose.onRoot().captureShot("$SCREENSHOTS/automatum/cast-mana-section.png",
            "After picking Bob's life square the cast panel returns by the Stack: the card's text, the target Bob, and the mana section with the suggested Mountain shown tapped beside the other untapped Mountains; Cast is enabled.")
        // D32: nothing else changes while the cast is open
        compose.runOnIdle { assertFalse(toolkit.submit(PlayAction.AdjustLife(f.a, "-1"))); assertEquals(CastController.BUSY, toolkit.error) }
        compose.onNode(hasText("Cast") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertNull(toolkit.cast.flow)
        val (batch, request) = sent.single()
        assertTrue(batch.tags.isNotEmpty(), "an automated batch")
        val after = accepted(play, batch, request)
        // STK-023, STK-026: the caster passes and Bob has nothing to respond with, so Storm Spark resolves in the cast's
        // own batch (its spell entry, STK-001, is gone again); the active player holds priority after it (CR 117.3b)
        assertTrue(sparkId in after.board.containers.single { it.id == f.id("a/GRAVEYARD") }.cards)
        assertTrue(after.board.stackEffects.isEmpty())
        assertEquals((play.board.players.single { it.accountId == f.b }.life.toInt() - 3).toString(),
            after.board.players.single { it.accountId == f.b }.life)
        assertEquals(MtgPriority(f.a), after.board.priority)
        assertEquals(1, after.board.cards.count { it.tapped && it.definition == mountain.submitted.digest })
        assertTrue(EngineState.unsupported(after, AutomatumBoards.definitions(after)).isEmpty())
    }

    // STK-023, I-D6: choices first (kicker with the card's text), then targets, then mana; the kicker stays a toggle and
    // the mana follows it
    @Test fun kickerChoiceThenToggle() {
        val play = board()
        val toolkit = render(play)
        compose.runOnIdle { assertTrue(toolkit.cast.start(surgeId)) }
        compose.waitForIdle()
        compose.onNodeWithText("Pay the Kicker cost?").assertExists()
        captureShot("$SCREENSHOTS/automatum/cast-choices.png",
            "Casting Flash Surge opens the cast panel by the Stack with its card text and the optional cost first: \"Pay the Kicker cost?\" with Yes and No, Accept automatically, Cancel and a greyed Cast.")
        compose.onNodeWithText("Yes").performClick()
        compose.waitForIdle()
        compose.runOnIdle { toolkit.open(TableIntent.Actions("life/${f.b}", null), lifeSquare(play, f.b), TableSelection()) }
        compose.waitForIdle()
        compose.onNodeWithText("Mana · {4}{R}").assertExists()
        compose.onAllNodes(tappedSource).assertCountEquals(5)
        captureShot("$SCREENSHOTS/automatum/cast-kicked-mana.png",
            "Flash Surge kicked and aimed at Bob: the Kicker toggle is on, and the mana section suggests all five Mountains for {4}{R}.")
        compose.onNodeWithContentDescription("Pay the Kicker cost?").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Mana · {R}").assertExists()
        assertEquals(2, toolkit.cast.flow?.answers?.size, "the target chosen after the kicker stays")
        // Untapping the suggested Mountain leaves the payment short: Cast greys out
        compose.onNode(tappedSource).performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Cast") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Cancel cast").performClick()
        compose.waitForIdle()
        assertNull(toolkit.cast.flow)
        assertTrue(sent.isEmpty(), "a cancelled cast sends nothing (I-D11)")
    }

    // ZON-007, TBL-115, I-D28: the hand menu's Cast is greyed while the engine doesn't allow the cast; a double-tap says why
    @Test fun castNotAllowedIsGreyed() {
        val play = board(tapped = true)
        val toolkit = render(play)
        compose.runOnIdle { toolkit.openCard(TableScope(sparkId, listOf(sparkId))) }
        compose.onNodeWithContentDescription("Inspect").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cast").assertIsNotEnabled()
        captureShot("$SCREENSHOTS/automatum/cast-greyed.png",
            "With every Mountain tapped, the hand menu of Storm Spark shows Automatum's Cast row greyed out above Inspect and a greyed \"Manual…\": the engine doesn't allow casting it now.")
        compose.runOnIdle {
            toolkit.dismiss()
            assertFalse(doubleTap(toolkit, sparkId))
            assertEquals("Storm Spark can't be cast now.", toolkit.error)
        }
        assertNull(toolkit.cast.flow)
    }

    // ZON-004, CR 305.1: a land double-tapped with Automatum on is played at once through the engine, no panel
    @Test fun doubleTapPlaysALand() {
        val play = board()
        val toolkit = render(play)
        compose.runOnIdle { assertTrue(doubleTap(toolkit, landId)) }
        assertNull(toolkit.cast.flow)
        val (batch, request) = sent.single()
        val after = accepted(play, batch, request)
        assertTrue(landId in after.board.containers.single { it.id == field }.cards)
    }

    // STK-024, D3: with Accept automatically on, a payment the suggestion makes is skipped: the cast goes out once the
    // target is picked
    @Test fun acceptAutomaticallySkipsTheMana() {
        org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("automatum-cast", 0).edit()
            .putBoolean("accept/${f.a}", true).commit()
        val play = board()
        val toolkit = render(play)
        compose.runOnIdle { assertTrue(doubleTap(toolkit, sparkId)) }
        compose.waitForIdle()
        compose.runOnIdle { toolkit.open(TableIntent.Actions("life/${f.b}", null), lifeSquare(play, f.b), TableSelection()) }
        compose.waitForIdle()
        assertNull(toolkit.cast.flow)
        assertEquals(1, sent.size)
    }
}
