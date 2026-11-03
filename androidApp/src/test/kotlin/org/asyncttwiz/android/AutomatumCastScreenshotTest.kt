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
    private val cub = Invented(DefinitionFace("cub", "Ember Cub", typeLine = "Creature — Elemental Cat", manaCost = "{R}",
        power = "1", toughness = "1"), OracleCard("Ember Cub", "{R}", "Creature — Elemental Cat", "", "1", "1"))
    private val invented = listOf(spark, surge, mountain, cub)

    private val hand = f.initial.board.containers.single { it.id == f.id("a/HAND") }.cards
    private val sparkId = hand[1]
    private val surgeId = hand[2]
    private val landId = hand[0]
    private val cubId = f.id("ember-cub")
    private val handId = f.id("a/HAND")
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
        val cubCard = initial.board.cards.single { it.id == landId }.copy(id = cubId, definition = cub.submitted.digest, faceId = "cub")
        return initial.copy(definitions = initial.definitions + invented.map { it.submitted },
            board = initial.board.copy(automatum = true, priority = MtgPriority(f.a),
                cards = initial.board.cards.map { when (it.id) {
                    sparkId -> it.copy(definition = spark.submitted.digest, faceId = "spark")
                    surgeId -> it.copy(definition = surge.submitted.digest, faceId = "surge")
                    landId -> it.copy(definition = mountain.submitted.digest, faceId = "mountain")
                    in lands -> it.copy(definition = mountain.submitted.digest, faceId = "mountain", tapped = tapped, corner = "")
                    else -> it
                } } + extra + cubCard,
                containers = initial.board.containers.map { when (it.id) {
                    field -> it.copy(cards = it.cards + extra.map(PlayCard::id))
                    handId -> it.copy(cards = it.cards + cubId)
                    else -> it
                } }))
    }

    private val tappedSource = hasContentDescription("Mana source", substring = true) and hasContentDescription(", tapped", substring = true)
    private val sent = mutableListOf<Pair<GameAction.PlayBatch, String>>()
    private fun render(play: PlayState, autotap: Boolean = true): TableToolkitController {
        AutomatumBoards.compute(play, f.a)
        val toolkit = TableToolkitController(f.a, { play }, { true }, { _, _ -> true },
            automatedBatch = { batch, request -> sent += batch to request; true })
        toolkit.cast.autotap = autotap
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

    // ZON-004, STK-023, STK-024, I-D6, I-D11, I-D47: a double-tap casts; the panel shrinks to a bar while Bob is picked by
    // his life square; Autotap (on by default) pays at once, so the cast goes out as one batch, which resolves it (STK-026)
    @Test fun doubleTapCastsWithTargetBarThenAutotap() {
        val play = board()
        val toolkit = render(play)
        compose.runOnIdle { assertTrue(doubleTap(toolkit, sparkId)) }
        compose.waitForIdle()
        assertTrue(toolkit.cast.targeting)
        assertTrue("life/${f.b}" in toolkit.cast.glow(play), "Bob's life square glows")
        compose.onNodeWithText("Storm Spark — choose any target").assertExists()
        assertTrue(sent.isEmpty(), "nothing is sent while the cast is open (I-D11)")
        compose.onRoot().captureShot("$SCREENSHOTS/automatum/cast-target-bar.png",
            "Double-tapping Storm Spark with Automatum on: the cast panel is a slim bar just above the hand (\"Storm Spark — choose any target\" with Cancel and its icon) while the legal targets glow: the creatures on both battlefields and Bob's life square are outlined in violet; the step arrow is greyed while the cast is open.")
        // D32, STK-025, UI review round 2 #4: nothing else changes while the cast is open; the refusal shakes, no message
        compose.runOnIdle {
            assertFalse(toolkit.submit(PlayAction.AdjustLife(f.a, "-1")))
            assertNull(toolkit.error)
            assertEquals(sparkId, toolkit.shaking?.id, "the card being cast shakes")
            assertEquals(CastController.BUSY, stepBlocked(play, toolkit.cast.open, null), "the step arrow is greyed and says why on hold")
        }
        compose.runOnIdle { toolkit.open(TableIntent.Actions("life/${f.b}", null), lifeSquare(play, f.b), TableSelection()) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Cast panel").assertDoesNotExist()
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

    // STK-023, STK-024, I-D47: a cast with a real choice (kicker) opens the panel docked above the hand, full width, with
    // the card small (no rules text, no mana selection); the eye stays where it is while the panel fades around it;
    // after the choice and the target Autotap pays and the cast goes out at once
    @Test fun kickerPanelDockedAboveTheHand() {
        val play = board()
        val toolkit = render(play)
        compose.runOnIdle { assertTrue(toolkit.cast.start(surgeId)) }
        compose.waitForIdle()
        compose.onNodeWithText("Pay the Kicker cost?").assertExists()
        compose.onNodeWithContentDescription("Card Flash Surge").assertExists()
        compose.onNodeWithText("Accept automatically").assertDoesNotExist()
        compose.onNodeWithText("Kicker {4}", substring = true).assertDoesNotExist()
        val panel = compose.onNodeWithContentDescription("Cast panel").fetchSemanticsNode().boundsInRoot
        val hand = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false).items
            .first { it.kind == TableItemKind.HAND && it.owner == f.a }.bounds
        assertEquals(hand.y, panel.bottom / compose.density.density, 1f, "docked exactly above the hand")
        assertEquals(0f, panel.left, 1f); assertEquals(412f, panel.right / compose.density.density, 1f)
        val eye = compose.onNodeWithContentDescription("Hide cast panel").fetchSemanticsNode().boundsInRoot
        captureShot("$SCREENSHOTS/automatum/cast-choices.png",
            "Casting Flash Surge (a kicker choice) opens the cast panel full width exactly above the hand: the card small beside its name, \"Pay the Kicker cost?\" with No and Yes at its right, and a bottom row with the eye at the left and Cancel and Cast with their icons at the right; the step arrow is greyed while the cast is open.")
        compose.onNodeWithContentDescription("Hide cast panel").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Pay the Kicker cost?").assertDoesNotExist()
        assertEquals(eye, compose.onNodeWithContentDescription("Show cast panel").fetchSemanticsNode().boundsInRoot, "the eye stays in place")
        captureShot("$SCREENSHOTS/automatum/cast-panel-hidden.png",
            "The same cast after tapping the eye: the panel has faded away and only the eye button stays, in the very same place above the hand.")
        compose.onNodeWithContentDescription("Show cast panel").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Yes").performClick()
        compose.waitForIdle()
        assertTrue(sent.isEmpty())
        compose.runOnIdle { toolkit.open(TableIntent.Actions("life/${f.b}", null), lifeSquare(play, f.b), TableSelection()) }
        compose.waitForIdle()
        assertNull(toolkit.cast.flow)
        val (batch, request) = sent.single()
        val after = accepted(play, batch, request)
        assertEquals(5, after.board.cards.count { it.tapped && it.definition == mountain.submitted.digest }, "kicked: {4}{R}")
    }

    // STK-024, I-D47: Autotap off keeps the mana section, the panel's way to choose the payment
    @Test fun autotapOffShowsTheManaSection() {
        val play = board()
        val toolkit = render(play, autotap = false)
        compose.runOnIdle { assertTrue(doubleTap(toolkit, sparkId)) }
        compose.waitForIdle()
        compose.runOnIdle { toolkit.open(TableIntent.Actions("life/${f.b}", null), lifeSquare(play, f.b), TableSelection()) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Cast panel").assertExists()
        compose.onNodeWithText("Mana · {R}").assertExists()
        compose.onAllNodes(tappedSource).assertCountEquals(1)
        captureShot("$SCREENSHOTS/automatum/cast-mana-section.png",
            "With Autotap mana sources off, after picking Bob's life square the cast panel above the hand shows the target Bob and the mana section with the suggested Mountain tapped beside the other untapped Mountains; Cast is enabled.")
        compose.onNode(hasText("Cast") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertNull(toolkit.cast.flow)
        assertEquals(1, accepted(play, sent.single().first, sent.single().second).board.cards
            .count { it.tapped && it.definition == mountain.submitted.digest })
    }

    // ZON-004, STK-023, I-D47: a spell with nothing to choose casts at once: no panel at all, one batch
    @Test fun doubleTapWithoutChoicesCastsAtOnce() {
        val play = board()
        val toolkit = render(play)
        compose.runOnIdle { assertTrue(doubleTap(toolkit, cubId)) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Cast panel").assertDoesNotExist()
        assertFalse(toolkit.cast.open)
        val (batch, request) = sent.single()
        val after = accepted(play, batch, request)
        assertTrue(cubId in after.board.containers.single { it.id == field }.cards)
        assertEquals(1, after.board.cards.count { it.tapped && it.definition == mountain.submitted.digest })
    }

    // STK-023, I-D6: choices first (kicker), then targets, then mana (Autotap off); the kicker stays a toggle and the mana
    // follows it
    @Test fun kickerChoiceThenToggle() {
        val play = board()
        val toolkit = render(play, autotap = false)
        compose.runOnIdle { assertTrue(toolkit.cast.start(surgeId)) }
        compose.waitForIdle()
        compose.onNodeWithText("Pay the Kicker cost?").assertExists()
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

    // ZON-007, TBL-115, I-D28, I-D46: the hand menu's Cast is greyed while the engine doesn't allow the cast; a
    // double-tap shakes the card instead of saying why
    @Test fun castNotAllowedIsGreyed() {
        val play = board(tapped = true)
        val toolkit = render(play)
        compose.runOnIdle { toolkit.openCard(TableScope(sparkId, listOf(sparkId))) }
        compose.onNodeWithContentDescription("Inspect").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cast").assertIsNotEnabled()
        captureShot("$SCREENSHOTS/automatum/cast-greyed.png",
            "With every Mountain tapped, the hand menu of Storm Spark shows Automatum's Cast row greyed out above Inspect: the engine doesn't allow casting it now.")
        compose.runOnIdle {
            toolkit.dismiss()
            assertFalse(doubleTap(toolkit, sparkId))
            assertNull(toolkit.error)
            assertEquals(sparkId, toolkit.shaking?.id)
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

    /** [player]'s device on the board [tree] is at (no screen): its casts and taps go to [sent]. */
    private fun device(tree: GameplayTree, player: String = f.a): TableToolkitController {
        val state = tree.stateAt(tree.currentNodeId)!!
        AutomatumBoards.compute(state, player)
        return TableToolkitController(player, { state }, { true }, { _, _ -> true }, playTree = { tree },
            automatedBatch = { batch, request -> sent += batch to request; true })
    }

    /** The tree after the server took the batch [player]'s device sent last. */
    private fun take(tree: GameplayTree, player: String = f.a): GameplayTree {
        val (batch, request) = sent.removeAt(sent.lastIndex)
        val change = PlayBatches.change(tree, player, GameCommand(request, GameScope(tree.initial.gameId, playId = tree.initial.id), batch, f.id("client"), "1"))
        return assertNotNull(change.tree, change.reason)
    }

    private fun state(tree: GameplayTree) = tree.stateAt(tree.currentNodeId)!!
    private fun tapped(tree: GameplayTree) = state(tree).board.cards.filter { it.tapped && it.definition == mountain.submitted.digest }.map { it.id }.toSet()
    private fun rotate(card: String) = TableIntent.Rotate(TableScope(card, listOf(card)), tapped = true, toggle = true)

    // TBL-043, STK-024, I-D47 (owner's flow): autotap tapped a Mountain the player didn't want: Undo, double-tap the wanted
    // Mountain (its mana goes to the pool), cast again: the pool pays and the first Mountain stays untapped. Both devices
    // get the same board: the server's.
    @Test fun undoThenTapTheWantedSourceThenCast() {
        var tree = GameplayTree(board())
        assertTrue(device(tree).handle(TableIntent.Move(TableScope(cubId, listOf(cubId)),
            TableDestination.Container(f.id("stack"), PlayZone.STACK), handDoubleTap = true)))
        tree = take(tree)
        val suggested = tapped(tree).single()
        val wanted = extraMountains.last()
        assertTrue(wanted != suggested)
        tree = assertNotNull(HistoryContinuation.change(tree, f.a, GameCommand(f.id("undo"), GameScope(tree.initial.gameId,
            playId = tree.initial.id), GameAction.Undo, f.id("client"), "1")).tree)
        assertTrue(tapped(tree).isEmpty(), "the cast was undone")
        // A double-tap on the wanted Mountain with Automatum on taps it for mana (not refused, no panel)
        val tapping = device(tree)
        assertTrue(tapping.handle(rotate(wanted)))
        assertFalse(tapping.cast.open)
        tree = take(tree)
        assertEquals(setOf(wanted), tapped(tree))
        assertEquals(1, state(tree).board.players.single { it.accountId == f.a }.manaPool.size, "{R} in the pool")
        assertEquals(MtgPriority(f.a), state(tree).board.priority, "a mana ability keeps priority")
        // Cast again: autotap pays from the pool and taps nothing more
        assertTrue(device(tree).handle(TableIntent.Move(TableScope(cubId, listOf(cubId)),
            TableDestination.Container(f.id("stack"), PlayZone.STACK), handDoubleTap = true)))
        tree = take(tree)
        assertEquals(setOf(wanted), tapped(tree))
        assertTrue(cubId in state(tree).board.containers.single { it.id == field }.cards)
        assertTrue(state(tree).board.players.single { it.accountId == f.a }.manaPool.isEmpty())
        // Bob's device reads the same board as Automatum (nothing it can't take over, the same mana and creature)
        val bob = EngineState.from(state(tree), AutomatumBoards.definitions(state(tree)), f.b)
        val alice = EngineState.from(state(tree), AutomatumBoards.definitions(state(tree)), f.a)
        assertEquals(alice.battlefield.size, bob.battlefield.size)
    }

    /** [AutomatumWork] whose staged second part waits for [release] (the worker still busy with the rest of a cast). */
    private class HeldWork : AutomatumWork {
        var held: (() -> Unit)? = null
        override fun <T> run(work: () -> T, done: (T) -> Unit) = done(work())
        override fun <A, B> staged(first: () -> A, shown: (A) -> Unit, next: (A) -> (() -> B)?, done: (B) -> Unit) {
            val a = first(); shown(a); next(a)?.let { second -> held = { done(second()) } }
        }
        fun release() { held?.invoke().also { held = null } }
    }

    /** A device's preview through its own [GameplayReplica], as the app's session does it. */
    private class ReplicaPreview(val replica: GameplayReplica, val actor: String) : CastPreview {
        val sent = mutableListOf<GameCommand>()
        private fun command(batch: GameAction.PlayBatch, requestId: String) = replica.display!!.let { play ->
            GameCommand(requestId, GameScope(play.gameId, playId = play.id), batch, TableStatesFixture.id("client"), "1") }
        override fun show(batch: GameAction.PlayBatch, requestId: String) = replica.predict(command(batch, requestId), actor)
        override fun shownOn(requestId: String) = replica.shownOn(requestId)
        override fun drop(requestId: String) = replica.retire(requestId)
        override fun send(batch: GameAction.PlayBatch, requestId: String, replaces: String): Boolean {
            val command = command(batch, requestId)
            sent += command
            return replica.replace(replaces, command, actor)
        }
    }

    private fun replicaOf(play: PlayState) = GameplayReplica().also {
        assertTrue(it.root(GameHead(play.gameId, "1", f.game.copy(revision = "1", lifecycle = GameLifecycle.ACTIVE, playId = play.id, preparationRound = play.preparationRound), play = play)))
    }

    // STK-042, I-D47: a cast shows at once on the caster's device (the card's Stack entry, its Mountain tapped) while its
    // batch is still being worked out; the batch then takes the preview's place, and the board shown is the server's
    @Test fun castShowsAtOnceThenItsBatchTakesItsPlace() {
        val play = board()
        AutomatumBoards.compute(play, f.a)
        val replica = replicaOf(play)
        val preview = ReplicaPreview(replica, f.a)
        val work = HeldWork()
        val toolkit = TableToolkitController(f.a, { replica.display }, { true }, { _, _ -> true }, playTree = { replica.displayTree },
            automatedBatch = { batch, request -> sent += batch to request; true }, automatumWork = work, castPreview = preview)
        assertTrue(doubleTap(toolkit, cubId))
        // The preview: on the Stack, the Mountain tapped, nothing sent, the cast still open (nothing else changes meanwhile)
        val shown = replica.display!!.board
        assertEquals(cubId, shown.stackEffects.single().sourceId)
        assertEquals(1, shown.cards.count { it.tapped && it.definition == mountain.submitted.digest })
        assertTrue(toolkit.cast.open)
        assertNull(toolkit.cast.flow, "no panel")
        assertTrue(preview.sent.isEmpty() && sent.isEmpty())
        work.release()
        val command = preview.sent.single()
        assertFalse(toolkit.cast.open)
        assertEquals(listOf(command.requestId), replica.pendingIds, "the preview is gone, the batch predicted")
        val server = PlayBatches.change(GameplayTree(play), f.a, command)
        val accepted = assertNotNull(server.tree, server.reason).let { it.stateAt(it.currentNodeId)!! }
        assertEquals(accepted.board, replica.display!!.board, "the caster shows what the server (and Bob) will have")
        assertTrue(cubId in accepted.board.containers.single { it.id == field }.cards)
    }

    // STK-042, I-D47: a cast dropped while its preview shows (here: Cancel) takes the preview back and sends nothing
    @Test fun previewTakenBackWhenTheCastIsDropped() {
        val play = board()
        AutomatumBoards.compute(play, f.a)
        val replica = replicaOf(play)
        val preview = ReplicaPreview(replica, f.a)
        val work = HeldWork()
        val toolkit = TableToolkitController(f.a, { replica.display }, { true }, { _, _ -> true }, playTree = { replica.displayTree },
            automatedBatch = { batch, request -> sent += batch to request; true }, automatumWork = work, castPreview = preview)
        assertTrue(doubleTap(toolkit, cubId))
        assertTrue(replica.display!!.board.stackEffects.isNotEmpty())
        toolkit.cast.cancel()
        assertEquals(play.board, replica.display!!.board)
        work.release()
        assertTrue(preview.sent.isEmpty() && sent.isEmpty())
        assertTrue(replica.pendingIds.isEmpty())
    }
}
