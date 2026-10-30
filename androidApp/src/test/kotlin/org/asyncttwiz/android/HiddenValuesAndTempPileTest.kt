package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

/** Round 19: hidden values never leak, face-down Battlefield values are public, Temp pile dock. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class HiddenValuesAndTempPileTest {
    @get:Rule val compose = createComposeRule()

    private val playId = "00000000-0000-4000-8000-000000000123"
    private val game = LiveTableFixture.game.copy(playId = playId)

    private fun play(): PlayState {
        val base = LiveTableFixture.play()
        // A face-down creature on b's Battlefield with public marks, and a's own face-down Exile card.
        return base.copy(id = playId, board = base.board.copy(cards = base.board.cards.map { card -> when (card.id) {
            "b/BATTLEFIELD/3" -> card.copy(covered = true, explicitlyCovered = true, corner = "2/2",
                counters = mapOf(CounterColor.BLUE to "1"), damage = "1", note = "morph")
            else -> card
        } }))
    }

    private fun card(play: PlayState, id: String) = play.board.cards.single { it.id == id }

    @Test fun hiddenCardsExposeNoValuesButFaceDownBattlefieldCardsDo() {
        val play = play()
        val board = play.board
        // The opponent's hand, any library card and own hand.
        assertTrue(cardValuesHidden(board, card(play, "b/HAND/1"), "a"))
        assertTrue(cardValuesHidden(board, card(play, "a/LIBRARY/1"), "a"))
        assertFalse(cardValuesHidden(board, card(play, "a/HAND/1"), "a"))
        assertFalse(cardValuesHidden(board, card(play, "b/BATTLEFIELD/3"), "a"))
        for (id in listOf("b/HAND/1", "a/LIBRARY/1")) {
            val scope = ManualScope(play.gameId, play.id, id, listOf(id))
            for (kind in listOf(ManualValueKind.CORNER, ManualValueKind.DAMAGE, ManualValueKind.EOT_CORNER, ManualValueKind.NOTE))
                assertNull(scope.value(play, ManualValue(kind), "a"), "$id $kind")
            assertNull(scope.value(play, ManualValue(ManualValueKind.COUNTER, CounterColor.BLUE), "a"))
            // Hidden values are edited blind: the editor starts empty and never shows the stored value.
            assertEquals(listOf<PlayAction>(PlayAction.SetNote(id, "x")), scope.actions(play, ManualValue(ManualValueKind.NOTE), "x", "a"))
            val actions = ManualActions().apply { cards(play, id, listOf(id)) }
            actions.edit(ManualValue(ManualValueKind.CORNER), play, "a")
            assertEquals("", assertNotNull(actions.editor, id).text)
        }
        val faceDown = ManualScope(play.gameId, play.id, "b/BATTLEFIELD/3", listOf("b/BATTLEFIELD/3"))
        assertEquals("2/2", faceDown.value(play, ManualValue(ManualValueKind.CORNER), "a"))
        assertEquals("1", faceDown.value(play, ManualValue(ManualValueKind.COUNTER, CounterColor.BLUE), "a"))

        val scene = TablePresentation.scene(game, play, "a", 412f, 892f, false)
        val down = scene.items.single { it.cardId == "b/BATTLEFIELD/3" }
        assertTrue(down.covered)
        assertEquals("", down.value)
        assertEquals("2/2", down.corner)
        assertEquals("1", down.damage)
        assertEquals("morph", down.note)
        assertEquals(mapOf(CounterColor.BLUE to "1"), down.counters)
        assertFalse(down.changedPowerToughness)
        val opponentHand = scene.items.single { it.cardId == "b/HAND/1" }
        assertEquals("", opponentHand.corner)
        assertTrue(opponentHand.counters.isEmpty())
    }

    @Test fun faceDownStackCardMenuEditsValuesBlind() {
        val initial = play()
        val stack = initial.board.containers.single { it.zone == PlayZone.STACK }
        val id = "00000000-0000-4000-8000-000000000555"
        val play = initial.copy(board = initial.board.copy(
            cards = initial.board.cards.map { if (it.id == "b/HAND/1") it.copy(id = id, containerId = stack.id, covered = true, explicitlyCovered = true) else it },
            containers = initial.board.containers.map { when (it.id) {
                stack.id -> it.copy(cards = listOf(id))
                "b/HAND" -> it.copy(cards = it.cards - "b/HAND/1")
                else -> it
            } }))
        val toolkit = TableToolkitController("a", { play }, { true }, { _, _ -> true })
        toolkit.openCard(TableScope(id, listOf(id)))
        compose.setContent { MaterialTheme {
            TableSurface(game, play, "a", false, Modifier.fillMaxSize(), connected = true, onIntent = toolkit::handle, toolkit = toolkit)
        } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Inspect").assertIsEnabled()
        // Every action stays available (rules v30 policy); the values themselves stay hidden.
        compose.onNodeWithContentDescription("Set damage").assertIsEnabled()
        compose.onNodeWithContentDescription("Increase damage").assertIsEnabled()
        compose.onNodeWithContentDescription("Edit card note").assertIsEnabled()
        // The note editor starts empty: a blind edit never shows the stored note.
        toolkit.manual.edit(ManualValue(ManualValueKind.NOTE), play, "a")
        assertEquals("", assertNotNull(toolkit.manual.editor).text)
    }

    private fun withTemp(play: PlayState, ids: List<String>): PlayState {
        val temp = TempPiles.containerId(play.id, "a")
        return play.copy(board = play.board.copy(
            cards = play.board.cards.map { if (it.id in ids) it.copy(containerId = temp, covered = true) else it },
            containers = play.board.containers.map { if (it.id == "a/GRAVEYARD") it.copy(cards = it.cards - ids.toSet()) else it } +
                PlayContainer(temp, PlayZone.LIBRARY, "a", ids, temp = true)))
    }

    @Test fun tempPileOneIsAlwaysShownAndNoOtherPileMoves() {
        val base = play()
        val temp = TempPiles.containerId(playId, "a")
        for (landscape in listOf(false, true)) for (size in listOf(1f, 1.5f, 2f)) {
            val width = if (landscape) 892f else 412f
            val height = if (landscape) 412f else 892f
            fun scene(play: PlayState) = TablePresentation.scene(game, play, "a", width, height, landscape,
                appearance = CardAppearance(cardSize = size))
            val before = scene(base)
            val empty = scene(withTemp(base, emptyList()))
            val shown = scene(withTemp(base, listOf("a/GRAVEYARD/0")))
            // Pile 1 is shown (as a numbered placeholder) before it exists and while it is empty.
            val placeholder = before.items.single { it.id == temp }
            assertTrue(placeholder.temp && placeholder.cards.isEmpty() && placeholder.tempNumber == 1)
            assertEquals(placeholder.bounds, empty.items.single { it.id == temp }.bounds)
            val item = shown.items.single { it.id == temp }
            assertTrue(item.temp && item.covered && item.zone == PlayZone.LIBRARY && item.drawHandId == null)
            assertEquals("1", item.value)
            assertEquals(1, item.tempNumber)
            assertEquals(placeholder.bounds, item.bounds)
            // Landscape keeps it beside Exile in the (scrollable) sidebar counter row, like the counters.
            if (!landscape || size == 1f) assertTrue(item.visible.isNotEmpty(), "$landscape/$size: Temp pile must be visible")
            if (landscape) assertTrue(item.bounds.right <= TablePresentation.sidebarWidth(width, CardAppearance(cardSize = size)))
            // Library, Graveyard and Exile keep their exact place and stay visible.
            for (id in listOf("a/LIBRARY", "a/GRAVEYARD", "a/EXILE", "b/LIBRARY", "b/GRAVEYARD", "b/EXILE")) {
                val old = before.items.single { it.id == id }
                val new = shown.items.single { it.id == id }
                assertEquals(old.bounds, new.bounds, "$landscape/$size $id")
                if (old.visible.isNotEmpty()) assertTrue(new.visible.isNotEmpty(), "$landscape/$size $id visible")
            }
            val exile = shown.items.single { it.id == "a/EXILE" }
            if (landscape) assertTrue(item.bounds.center.x >= exile.bounds.x && item.bounds.center.x <= exile.bounds.right)
            else assertTrue(item.bounds.x >= exile.bounds.right, "$size: Temp sits after Exile")
            // Filling pile 1 moves nothing else at all.
            for (other in before.items.filter { it.id != temp && it.cardId == null }) {
                assertEquals(other.bounds, shown.items.single { it.id == other.id }.bounds, "$landscape/$size ${other.id}")
            }
            val counters = shown.viewports.single { it.id == "counters/a" }
            val dock = shown.viewports.single { it.id == "dock/a" }
            if (!landscape) assertEquals(width, counters.bounds.right, .01f)
            assertTrue(counters.bounds.x >= dock.bounds.x)
        }
    }

    private val tempMenuMoves = listOf("Shuffle", "Look", "Shuffle into library", "All to library top", "All to library bottom",
        "All to graveyard", "All to exile")

    @Test fun tempPileMenuShowsEveryButtonAndDisablesOnlyWhatCannotApply() {
        // a's Temp 2 holds a card; Temp 3 does not exist yet; Temp 4 exists and is empty.
        val base = withTemp(play(), emptyList())
        val two = TempPiles.containerId(playId, "a", 2)
        val four = TempPiles.containerId(playId, "a", 4)
        val play = base.copy(board = base.board.copy(
            cards = base.board.cards.map { if (it.id == "a/GRAVEYARD/0") it.copy(containerId = two, covered = true) else it },
            containers = base.board.containers.map { if (it.id == "a/GRAVEYARD") it.copy(cards = it.cards - "a/GRAVEYARD/0") else it } +
                PlayContainer(two, PlayZone.LIBRARY, "a", listOf("a/GRAVEYARD/0"), temp = true, tempNumber = 2) +
                PlayContainer(four, PlayZone.LIBRARY, "a", temp = true, tempNumber = 4)))
        val ref = androidx.compose.runtime.mutableStateOf(TempPiles.Ref("a", 2))
        val calls = mutableListOf<String>()
        compose.setContent { MaterialTheme {
            val controller = LibraryController(playId, TempPiles.containerId(playId, "a", ref.value.number), "a/HAND", { play }, { true }, { true })
            TempPileActions(controller, play.board, ref.value, onShow = { _, _, _ -> calls += "look" }, toLibrary = { calls += "library $it" },
                toZone = { calls += "zone $it" }, shuffleIntoLibrary = { calls += "shuffle in" },
                toPile = { calls += "all $it" }, topToPile = { calls += "top $it" }, close = { calls += "close" }, onComplete = {})
        } }
        compose.waitForIdle()
        for (name in tempMenuMoves + listOf("All to Temp 1", "All to Temp 3", "All to Temp 4", "Top to Temp 1", "Top to Temp 3", "Top to Temp 4"))
            compose.onNodeWithContentDescription(name).assertIsEnabled()
        // No create toggles; a pile holding cards cannot be closed.
        compose.onNodeWithContentDescription("Temp 3 on/off").assertDoesNotExist()
        compose.onNodeWithContentDescription("All to Temp 2").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close pile").assertIsNotEnabled()
        for (name in listOf("All to Temp 3", "Top to Temp 3", "Shuffle into library", "All to graveyard", "All to exile", "All to library bottom"))
            compose.onNodeWithContentDescription(name).performClick()
        assertEquals(listOf("all 3", "top 3", "shuffle in", "zone GRAVEYARD", "zone EXILE", "library true"), calls)

        // The empty pile 4: every move is shown but disabled; it can be closed.
        ref.value = TempPiles.Ref("a", 4); compose.waitForIdle()
        for (name in tempMenuMoves + listOf("All to Temp 1", "Top to Temp 2"))
            compose.onNodeWithContentDescription(name).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Close pile").assertIsEnabled().performClick()
        assertEquals("close", calls.last())
        // Pile 1 is never closed, even when empty.
        ref.value = TempPiles.Ref("a", 1); compose.waitForIdle()
        compose.onNodeWithContentDescription("Close pile").assertIsNotEnabled()
    }

    @Test fun tempPileMenuStateFollowsPanelsAndAvailability() {
        val base = withTemp(play(), listOf("a/GRAVEYARD/0"))
        val one = TempPiles.Ref("a", 1)
        assertTrue(tempPileMenuState(base.board, one, true).moves)
        assertFalse(tempPileMenuState(base.board, one, false).moves)
        val three = TempPiles.containerId(playId, "a", 3)
        val empty = base.copy(board = base.board.copy(containers = base.board.containers +
            PlayContainer(three, PlayZone.LIBRARY, "a", temp = true, tempNumber = 3)))
        assertTrue(tempPileMenuState(empty.board, TempPiles.Ref("a", 3), true).mayClose)
        assertFalse(tempPileMenuState(empty.board, TempPiles.Ref("a", 3), false).mayClose)
        val shown = empty.copy(board = empty.board.copy(contentsDisplays = listOf(ContentsDisplay("panel", "a", three,
            ContentsSelection.ALL, 1, false, emptyList()))))
        assertFalse(tempPileMenuState(shown.board, TempPiles.Ref("a", 3), true).mayClose, "something is shown from it")
        assertFalse(tempPileMenuState(shown.board, one, true).mayLook, "another panel is open")
    }

    @Test fun ownerRedirectKeepsTempAndLibraryApart() {
        val play = withTemp(play(), listOf("a/GRAVEYARD/0"))
        val temp = TempPiles.containerId(playId, "a")
        val scene = TablePresentation.scene(game, play, "a", 412f, 892f, false)
        // b's card dropped on a's Temp pile goes to b's (not yet created) Temp pile, never to a Library.
        val redirected = scene.ownerMoveDestination("b/BATTLEFIELD/0", TableDestination.Container(temp, PlayZone.LIBRARY))
        assertEquals(TempPiles.containerId(playId, "b"), (redirected as TableDestination.Container).id)
        val library = scene.ownerMoveDestination("b/BATTLEFIELD/0", TableDestination.Container("a/LIBRARY", PlayZone.LIBRARY))
        assertEquals("b/LIBRARY", (library as TableDestination.Container).id)
        assertEquals("a/LIBRARY", play.board.zoneContainer("a", PlayZone.LIBRARY)!!.id)
        assertTrue(libraryDestinations(play.board, sourceContainerId = "a/LIBRARY").none { it.second.containerId == temp })
    }
}
