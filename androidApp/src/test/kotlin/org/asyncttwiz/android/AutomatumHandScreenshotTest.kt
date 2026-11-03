package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
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

/** I-D50 (integration spec): the hand with Automatum on: a tap raises a card with its cost chips and outlines the mana
 * Autotap would tap, a second tap casts it the way the chips say, the hand pans instead of reordering, and the
 * virtual-tabletop controls are gone. Invented cards: Storm Spark, Flash Surge (kicker), Ember Cub and Mountains. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AutomatumHandScreenshotTest {
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

    /** Alice's main phase with Automatum on: a Mountain, Storm Spark, Flash Surge and Ember Cub in hand ([more]: that many
     * more Mountains); [lands] Mountains on her battlefield ([tapped]: all tapped); a note and colour counters on one of
     * her cards ([marks]). */
    private fun board(lands: Int = 5, tapped: Boolean = false, more: Int = 0, marks: Boolean = false): PlayState {
        val initial = f.initial
        val own = initial.board.cards.filter { it.containerId == field }.take(2).map { it.id }
        val extra = extraMountains.mapIndexed { n, id -> PlayCard(id, mountain.submitted.digest, f.a, field, "mountain",
            covered = false, tapped = tapped, placement = PlayPlacement(BoardPosition(2 + n, 0), id)) }
        val kept = (own + extra.map { it.id }).take(lands)
        val cubCard = initial.board.cards.single { it.id == landId }.copy(id = cubId, definition = cub.submitted.digest, faceId = "cub")
        val spare = (0 until more).map { n -> initial.board.cards.single { it.id == landId }.copy(id = f.id("spare-$n")) }
        val board = initial.board.copy(automatum = true, priority = MtgPriority(f.a),
            cards = initial.board.cards.map { when (it.id) {
                sparkId -> it.copy(definition = spark.submitted.digest, faceId = "spark")
                surgeId -> it.copy(definition = surge.submitted.digest, faceId = "surge")
                landId -> it.copy(definition = mountain.submitted.digest, faceId = "mountain")
                in own -> it.copy(definition = mountain.submitted.digest, faceId = "mountain", tapped = tapped, corner = "")
                else -> it
            } }.map { c -> if (marks && c.id == own.first()) c.copy(note = "Remember me", counters = mapOf(CounterColor.RED to "2")) else c }
                + extra + cubCard + spare.map { it.copy(definition = mountain.submitted.digest, faceId = "mountain") },
            containers = initial.board.containers.map { when (it.id) {
                field -> it.copy(cards = it.cards + extra.map(PlayCard::id))
                handId -> it.copy(cards = it.cards + cubId + spare.map(PlayCard::id))
                else -> it
            } })
        // Fewer lands: the others leave the battlefield for the graveyard
        val gone = (own + extra.map { it.id }) - kept.toSet()
        val graveyard = f.id("a/GRAVEYARD")
        return initial.copy(definitions = initial.definitions + invented.map { it.submitted }, board = board.copy(
            cards = board.cards.map { if (it.id in gone) it.copy(containerId = graveyard, placement = null, tapped = false) else it },
            containers = board.containers.map { when (it.id) {
                field -> it.copy(cards = it.cards - gone.toSet())
                graveyard -> it.copy(cards = it.cards + gone)
                else -> it
            } }))
    }

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

    private fun scene(play: PlayState, toolkit: TableToolkitController? = null) = TablePresentation.scene(f.game, play, f.a, 412f, 892f,
        false, toolkit?.layoutState?.value ?: TableLayoutState())

    /** A single tap on [card] in a hand, as the table's gestures make it. */
    private fun tap(toolkit: TableToolkitController, play: PlayState, card: String) {
        val item = scene(play, toolkit).items.single { it.cardId == card && it.zone == PlayZone.HAND }
        toolkit.open(TableIntent.Actions(item.id, TableScope(card, listOf(card))), item)
    }

    private fun lifeSquare(play: PlayState, player: String): TableItem = scene(play).items.single { it.id == "life/$player" }

    private fun accepted(play: PlayState, batch: GameAction.PlayBatch, request: String): PlayState {
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), batch, f.id("client"), "1")
        val change = PlayBatches.change(GameplayTree(play), f.a, command)
        assertNull(change.reason, "the server takes the cast's batch")
        val tree = assertNotNull(change.tree)
        return assertNotNull(tree.stateAt(tree.currentNodeId))
    }

    private fun mountainsTapped(play: PlayState) = play.board.cards.count { it.tapped && it.definition == mountain.submitted.digest }

    // ZON-079, ZON-080, I-D50: a tap raises Flash Surge with its chips (the regular cast chosen, the kicker as an extra)
    // and outlines the one Mountain Autotap would tap; turning the kicker on outlines all five
    @Test fun tapRaisesWithChipsAndSources() {
        val play = board()
        val toolkit = render(play)
        compose.runOnIdle { tap(toolkit, play, surgeId) }
        compose.waitForIdle()
        assertEquals(surgeId, toolkit.hand.raised)
        assertTrue(scene(play, toolkit).items.single { it.cardId == surgeId && it.zone == PlayZone.HAND }.raised)
        assertNull(toolkit.capture, "no hand menu")
        assertNull(toolkit.cast.flow, "nothing is cast yet")
        compose.onNodeWithContentDescription("Way Cast {R}").assertExists()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "chosen"))
        compose.onNodeWithContentDescription("Extra Kicker {4}").assertExists()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "off"))
        assertEquals(1, toolkit.hand.sources(play).size)
        captureShot("$SCREENSHOTS/automatum/hand-raised-chips.png",
            "Tapping Flash Surge with Automatum on raises it a little above the hand with its chips in a row above it: the chosen \"Cast {R}\" way filled violet and an amber \"+ Kicker {4}\" extra with an empty check box; the one Mountain Autotap would tap is outlined in amber on Alice's battlefield.")
        compose.onNodeWithContentDescription("Extra Kicker {4}").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Extra Kicker {4}").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "on"))
        assertEquals(5, toolkit.hand.sources(play).size, "the sources follow the kicker")
        assertNull(toolkit.cast.flow, "an extra never casts")
        captureShot("$SCREENSHOTS/automatum/hand-raised-kicker.png",
            "The same raised Flash Surge after tapping its Kicker chip: the extra is checked and tinted amber, and all five Mountains Autotap would tap for {4}{R} are outlined in amber.")
        // ZON-079: a tap anywhere else lowers it
        compose.runOnIdle { toolkit.open(TableIntent.Actions("life/${f.b}", null), lifeSquare(play, f.b)) }
        assertNull(toolkit.hand.raised)
        assertTrue(toolkit.hand.sources(play).isEmpty())
    }

    // ZON-080, STK-023, CR 702.33: the kicker chosen with its chip, a second tap on the card casts it kicked: the cast
    // asks only for the target (no kicker question), Autotap pays {4}{R}, Bob takes 4
    @Test fun kickerCastViaChips() {
        val play = board()
        val toolkit = render(play)
        compose.runOnIdle { tap(toolkit, play, surgeId) }
        compose.onNodeWithContentDescription("Extra Kicker {4}").performClick()
        compose.waitForIdle()
        compose.runOnIdle { tap(toolkit, play, surgeId) }
        compose.waitForIdle()
        assertNull(toolkit.hand.raised, "casting lowers it")
        assertTrue(toolkit.cast.targeting, "straight to the target")
        compose.onNodeWithText("Pay the Kicker cost?").assertDoesNotExist()
        compose.runOnIdle { toolkit.open(TableIntent.Actions("life/${f.b}", null), lifeSquare(play, f.b)) }
        compose.waitForIdle()
        val (batch, request) = sent.single()
        val after = accepted(play, batch, request)
        assertEquals(5, mountainsTapped(after), "kicked: {4}{R}")
        assertEquals((play.board.players.single { it.accountId == f.b }.life.toInt() - 4).toString(),
            after.board.players.single { it.accountId == f.b }.life)
    }

    // ZON-080: tapping the chosen way's chip casts too (here the plain Ember Cub: no choices, out at once)
    @Test fun chosenWayChipCasts() {
        val play = board()
        val toolkit = render(play)
        compose.runOnIdle { tap(toolkit, play, cubId) }
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(hasContentDescription("Extra", substring = true)).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithContentDescription("Way Cast {R}").performClick()
        compose.waitForIdle()
        val (batch, request) = sent.single()
        assertTrue(cubId in accepted(play, batch, request).board.containers.single { it.id == field }.cards)
    }

    // ZON-079: a land's second tap plays it (its chip says Play, with no cost)
    @Test fun landSecondTapPlays() {
        val play = board()
        val toolkit = render(play)
        compose.runOnIdle { tap(toolkit, play, landId) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Way Play").assertExists()
        assertTrue(toolkit.hand.sources(play).isEmpty())
        compose.runOnIdle { tap(toolkit, play, landId) }
        compose.waitForIdle()
        val (batch, request) = sent.single()
        assertTrue(landId in accepted(play, batch, request).board.containers.single { it.id == field }.cards)
    }

    // ZON-080: with two Mountains the kicker's {4} more can't be paid: its chip is greyed and a tap leaves it off
    @Test fun unaffordableChipIsGreyed() {
        val play = board(lands = 2)
        val toolkit = render(play)
        compose.runOnIdle { tap(toolkit, play, surgeId) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Extra Kicker {4}")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "off, can't be paid now"))
        captureShot("$SCREENSHOTS/automatum/hand-chip-unaffordable.png",
            "With only two Mountains, the raised Flash Surge's \"+ Kicker {4}\" chip is greyed (Alice's mana can't pay {4}{R}) beside the chosen \"Cast {R}\"; one Mountain is outlined in amber.")
        compose.onNodeWithContentDescription("Extra Kicker {4}").performClick()
        compose.waitForIdle()
        assertEquals(listOf(0), toolkit.hand.options?.times)
    }

    // ZON-080: holding a chip shows its details, card-like, until released
    @Test fun holdingAChipShowsItsDetails() {
        val play = board()
        val toolkit = render(play)
        compose.runOnIdle { tap(toolkit, play, surgeId) }
        compose.waitForIdle()
        val chip = compose.onNodeWithContentDescription("Extra Kicker {4}")
        chip.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Details + Kicker {4}").assertExists()
        captureShot("$SCREENSHOTS/automatum/hand-chip-details.png",
            "Holding Flash Surge's Kicker chip shows its details in a white card-like popup above the chips: \"+ Kicker {4}\" with the symbol drawn as a mana symbol and the kicker's printed text.")
        chip.performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Details + Kicker {4}").assertDoesNotExist()
        assertEquals(listOf(0), toolkit.hand.options?.times, "holding never toggles")
    }

    // ZON-079, TBL-118: a card Automatum doesn't allow now (every Mountain tapped) rises with a shake, its chip greyed, no
    // sources outlined; its second tap is refused with a shake too
    @Test fun unplayableRisesWithAShake() {
        val play = board(tapped = true)
        val toolkit = render(play)
        compose.runOnIdle { tap(toolkit, play, sparkId) }
        compose.waitForIdle()
        assertEquals(sparkId, toolkit.hand.raised)
        assertEquals(sparkId, toolkit.shaking?.id)
        assertTrue(toolkit.hand.sources(play).isEmpty())
        compose.onNodeWithContentDescription("Way Cast {R}")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "chosen, can't be paid now"))
        val shakes = toolkit.shaking
        compose.runOnIdle { tap(toolkit, play, sparkId) }
        compose.waitForIdle()
        assertTrue(toolkit.shaking != shakes, "shaken again")
        assertTrue(sent.isEmpty())
        assertNull(toolkit.error)
    }

    // ZON-004, ZON-079: a double-tap still casts (the first tap raises, the second casts the raised card)
    @Test fun doubleTapStillCasts() {
        val play = board()
        val toolkit = render(play)
        compose.runOnIdle {
            tap(toolkit, play, cubId)
            assertTrue(toolkit.handle(TableIntent.Move(TableScope(cubId, listOf(cubId)),
                TableDestination.Container(f.id("stack"), PlayZone.STACK), handDoubleTap = true)))
        }
        compose.waitForIdle()
        assertNull(toolkit.hand.raised)
        assertEquals(1, sent.size)
    }

    // ZON-081: a hand wider than its row: a sideways drag starting on a card pans the hand (no card moves), past its end it
    // stretches and springs back on release; a drag that first moves up drags the card instead
    @Test fun sidewaysDragPansTheHand() {
        val play = board(more = 12)
        val intents = mutableListOf<TableIntent>()
        val coordinator = TableGestureCoordinator({ intents += it })
        val scene = scene(play)
        coordinator.update(scene, writable = true)
        val port = scene.viewports.single { it.id == handId }
        assertTrue(port.maxScroll > 0f, "the hand is wider than its row")
        val card = scene.items.first { it.zone == PlayZone.HAND && it.kind == TableItemKind.CARD && it.owner == f.a && it.visible.isNotEmpty() }
        val start = card.bounds.center
        coordinator.down(start, 0)
        coordinator.move(TablePoint(start.x - 60f, start.y + 2f), 20)
        coordinator.up(TablePoint(start.x - 60f, start.y + 2f), 40)
        assertTrue(intents.none { it is TableIntent.Move }, "no reordering, no cast")
        assertEquals(60f, intents.filterIsInstance<TableIntent.Scroll>().last().offset, .5f)
        // Past the start: the pull is resisted and springs back to 0
        intents.clear()
        coordinator.down(start, 1000)
        coordinator.move(TablePoint(start.x + 200f, start.y), 1020)
        val pull = intents.filterIsInstance<TableIntent.Overscroll>().last().offset
        assertTrue(pull < 0f && pull > -200f, "resisted: $pull")
        coordinator.up(TablePoint(start.x + 200f, start.y), 1040)
        coordinator.tick(1100); coordinator.tick(2000)
        assertEquals(0f, intents.filterIsInstance<TableIntent.Overscroll>().last().offset)
        // Up first: the card is dragged (onto the battlefield it casts, ZON-077)
        intents.clear()
        coordinator.down(start, 3000)
        coordinator.move(TablePoint(start.x + 1f, start.y - 200f), 3020)
        coordinator.up(TablePoint(start.x + 1f, start.y - 200f), 3040)
        assertTrue(intents.any { it is TableIntent.Move && it.drag })
    }

    // ZON-081: the panned hand on the screen (scrolled by a real swipe on its cards)
    @Test fun handPanned() {
        val play = board(more = 12)
        val toolkit = render(play)
        val hand = scene(play).items.single { it.id == handId }.bounds
        compose.onRoot().performTouchInput {
            down(androidx.compose.ui.geometry.Offset((hand.center.x + 120f) * density, hand.center.y * density))
            repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(-24f * density, 0f)) }
            up()
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        assertTrue((toolkit.layoutState.value.scroll[handId] ?: 0f) > 100f, "${toolkit.layoutState.value.scroll}")
        assertTrue(sent.isEmpty())
        captureShot("$SCREENSHOTS/automatum/hand-panned.png",
            "Alice's sixteen hand cards are wider than her hand row; after a sideways swipe on them with Automatum on the hand has panned toward its right end, every card still full size, none moved or cast.")
    }

    // ZON-081, ZON-077: a hand card dropped back into its own hand isn't reordered (nothing is sent, nothing shakes)
    @Test fun noReorderingInTheHand() {
        val play = board()
        val toolkit = render(play)
        compose.runOnIdle {
            assertFalse(toolkit.handle(TableIntent.Move(TableScope(sparkId, listOf(sparkId)), TableDestination.Container(handId,
                PlayZone.HAND, handGap = IdentityGap(null, landId)), drag = true)))
            assertNull(toolkit.error)
            assertNull(toolkit.shaking)
        }
        assertTrue(sent.isEmpty())
    }

    // TBL-119 (I-D52): menus offer no notes, pings, links or colour counters: Inspect, Other side and the combat draft
    @Test fun virtualTabletopControlsAreGone() {
        assertEquals(listOf("inspect", "otherSide", "Attack →"), CardRingPages.page(CardMenuLayout.CARD).actions)
        assertEquals(listOf("inspect", "Attack →", "Remove from combat"), CardRingPages.page(CardMenuLayout.COMBAT).actions)
        assertEquals(listOf("inspect", "otherSide"), CardRingPages.page(CardMenuLayout.PANEL).actions)
    }

    // TRN-010, TBL-119: Table actions have no Roll die…, Flip coin, Create token or combat area
    @Test fun noDiceInTableActions() {
        val play = board()
        val toolkit = render(play)
        compose.runOnIdle { toolkit.tableMenu() }
        compose.waitForIdle()
        compose.onNodeWithText("Timeline").assertExists()
        compose.onNodeWithText("Roll die…").assertDoesNotExist()
        compose.onNodeWithText("Flip coin").assertDoesNotExist()
        compose.onNodeWithText("Create token").assertDoesNotExist()
        compose.onNodeWithText("Create combat area").assertDoesNotExist()
    }

    /** [player]'s device on the board [tree] is at (no screen). */
    private fun device(tree: GameplayTree, player: String): TableToolkitController {
        val state = tree.stateAt(tree.currentNodeId)!!
        AutomatumBoards.compute(state, player)
        return TableToolkitController(player, { state }, { true }, { _, _ -> true }, playTree = { tree },
            automatedBatch = { batch, request -> sent += batch to request; true })
    }

    private fun tapOn(device: TableToolkitController, play: PlayState, viewer: String, card: String) {
        val item = TablePresentation.scene(f.game, play, viewer, 412f, 892f, false, device.layoutState.value).items
            .single { it.cardId == card && it.zone == PlayZone.HAND }
        device.open(TableIntent.Actions(item.id, TableScope(card, listOf(card))), item)
    }

    // ZON-079, ZON-080, I-D11, SYS-057: Alice casts Flash Surge kicked through its chips on her device; the server takes the
    // batch and Bob's device reads the same board (the same engine battlefield, Flash Surge in Alice's graveyard, 4 damage).
    // Bob tapping a card in Alice's hand raises nothing.
    @Test fun twoDevicesAgreeOnAKickedCast() {
        var tree = GameplayTree(board())
        val alice = device(tree, f.a)
        val start = tree.stateAt(tree.currentNodeId)!!
        tapOn(alice, start, f.a, surgeId)
        assertTrue(alice.hand.extra(0))
        tapOn(alice, start, f.a, surgeId)
        assertTrue(alice.cast.targeting)
        assertTrue(alice.cast.tapTarget(lifeSquare(start, f.b)))
        val (batch, request) = sent.removeAt(sent.lastIndex)
        val change = PlayBatches.change(tree, f.a, GameCommand(request, GameScope(start.gameId, playId = start.id), batch, f.id("client"), "1"))
        tree = assertNotNull(change.tree, change.reason)
        val after = tree.stateAt(tree.currentNodeId)!!
        assertEquals(5, mountainsTapped(after))
        assertTrue(surgeId in after.board.containers.single { it.id == f.id("a/GRAVEYARD") }.cards)
        assertEquals((start.board.players.single { it.accountId == f.b }.life.toInt() - 4).toString(),
            after.board.players.single { it.accountId == f.b }.life)
        val definitions = AutomatumBoards.definitions(after)
        assertEquals(EngineState.from(after, definitions, f.a).battlefield.size, EngineState.from(after, definitions, f.b).battlefield.size)
        val bob = device(tree, f.b)
        tapOn(bob, after, f.b, sparkId)
        assertNull(bob.hand.raised, "a card in the other player's hand doesn't rise")
        assertNull(bob.capture, "and has no menu")
    }
}
