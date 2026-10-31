package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TableAnimationTest {
    @Test fun drawnCardsStayAirborneWhenAnotherDrawOrBattlefieldChangeRetargetsTheirFlight() {
        for (landscape in listOf(false, true)) for (owner in listOf("a", "b")) for (drawAgain in listOf(false, true)) {
            val initial = TableFixture.play(hand = 2, board = 1)
            val id = "$owner/LIBRARY/0"
            val drawn = moved(initial, listOf(id), "$owner/HAND")
            val animation = HistorySceneAnimation(TableFixture.scene(initial, landscape))
            assertTrue(animation.retarget(TableFixture.scene(drawn, landscape), 1f))
            val flying = animation.frame(.1f).single { it.item.cardId == id }
            assertTrue(flying.item.inTransit)
            assertEquals(PlayZone.HAND, flying.item.zone, "Flight samples already carry destination metadata")
            val changed = if (drawAgain) moved(drawn, listOf("$owner/LIBRARY/1"), "$owner/HAND")
                else drawn.copy(board = drawn.board.copy(cards = drawn.board.cards.map {
                    if (it.id == "$owner/BATTLEFIELD/0") it.copy(tapped = true) else it
                }))
            val target = TableFixture.scene(changed, landscape)
            assertTrue(animation.retarget(target, .1f))
            assertEquals(flying.bounds, animation.frame(0f).single { it.item.cardId == id }.bounds)
            for (progress in listOf(0f, .1f, .25f, .5f)) {
                val frame = animation.frame(progress)
                val card = frame.single { it.item.cardId == id }
                val landed = target.items.single { it.cardId == id }.bounds
                if (card.bounds != landed) assertTrue(card.item.inTransit, "Retargeted draws must not become hand reflow")
                // Isolate viewport clipping: two simultaneous draws can legitimately cover one another.
                val rendered = TablePresentation.clip(target.copy(items = listOf(card.item.copy(bounds = card.bounds))),
                    if (landscape) 900f else 412f, if (landscape) 412f else 820f)
                assertEquals(listOf(card.bounds), rendered.items.single { it.cardId == id }.visible,
                    "The hand rail must not clip the flying card")
                assertEquals(card.item.id, rendered.hit(card.bounds.center)?.id)
            }
            val landed = animation.frame(1f).single { it.item.cardId == id }
            assertFalse(landed.item.inTransit)
            assertEquals(target.items.single { it.cardId == id }.bounds, landed.bounds)

            val next = TableFixture.scene(moved(changed, listOf("$owner/HAND/0"), "$owner/GRAVEYARD"), landscape)
            assertTrue(animation.retarget(next, 1f))
            assertFalse(animation.frame(.1f).single { it.item.cardId == id }.item.inTransit,
                "After landing the drawn card participates in ordinary hand reflow")
        }
    }

    // TBL-040, ZON-002
    @Test fun handSurvivorsSlideForDrawPlayDiscardAndUndoForBothPlayersAndOrientations() {
        for (landscape in listOf(false, true)) for (owner in listOf("a", "b")) {
            val initial = TableFixture.play(hand = 3)
            val survivor = "$owner/HAND/0"
            val changes = listOf(moved(initial, listOf("$owner/LIBRARY/0"), "$owner/HAND"),
                moved(initial, listOf("$owner/HAND/1"), "$owner/BATTLEFIELD"),
                moved(initial, listOf("$owner/HAND/1"), "$owner/GRAVEYARD"))
            for (changed in changes) for ((from, to) in listOf(initial to changed, changed to initial)) {
                val source = TableFixture.scene(from, landscape)
                val target = TableFixture.scene(to, landscape)
                val start = source.items.single { it.cardId == survivor }.bounds
                val end = target.items.single { it.cardId == survivor }.bounds
                assertNotEquals(start, end)
                val animation = HistorySceneAnimation(source)
                assertTrue(animation.retarget(target, 1f))
                fun at(millis: Float) = animation.frame(millis / animation.durationMillis).single { it.item.cardId == survivor }
                assertEquals(start, at(0f).bounds)
                assertEquals(start.x + (end.x - start.x) * .75f, at(110f).bounds.x, .001f)
                assertNotEquals(end, at(200f).bounds)
                assertEquals(end, at(220f).bounds)
                assertFalse(at(110f).item.inTransit, "Reflow stays clipped to its hand rail")
                assertEquals(owner != "a", at(110f).item.covered)
                assertEquals(end, animation.frame(1f).single { it.item.cardId == survivor }.bounds)
            }
        }
    }

    @Test fun handReflowRespectsMoveSpeedAndRetargetsFromCurrentVisiblePosition() {
        val initial = TableFixture.play(hand = 3)
        val source = TableFixture.scene(initial)
        val target = TableFixture.scene(moved(initial, listOf("a/HAND/1"), "a/GRAVEYARD"))
        for ((multiplier, duration) in listOf(.25f to 880, 1f to 220, 2f to 110)) {
            val animation = HistorySceneAnimation(source)
            animation.cardMoveSpeedDpPerMs = DEFAULT_CARD_MOVE_SPEED * multiplier
            animation.retarget(target, 1f)
            fun card(progress: Float) = animation.frame(progress).single { it.item.cardId == "a/HAND/0" }.bounds
            val halfway = duration / 2f / animation.durationMillis
            val current = card(halfway)
            assertNotEquals(source.items.single { it.cardId == "a/HAND/0" }.bounds, current)
            assertNotEquals(target.items.single { it.cardId == "a/HAND/0" }.bounds, current)
            assertEquals(target.items.single { it.cardId == "a/HAND/0" }.bounds, card(duration.toFloat() / animation.durationMillis))
            animation.cardMoveSpeedDpPerMs = DEFAULT_CARD_MOVE_SPEED * 3f
            assertEquals(current, card(halfway), "A setting change must not alter an already-running scene")
            assertTrue(animation.retarget(source, halfway))
            assertEquals(current, card(0f))
            assertEquals(source.items.single { it.cardId == "a/HAND/0" }.bounds, card(1f))
        }
    }

    // TBL-039
    @Test fun rotationFinishesAtNinetyMillisBesideALongMoveAndFlipAndFadeUseTheirOwnClocks() {
        fun card(id: String, x: Float, tapped: Boolean = false) = TableItem(id, TableBounds(x, 0f, 28f, 40f), id, id,
            kind = TableItemKind.CARD, cardId = id, tapped = tapped,
            cardTransform = TableCardTransform(28f, 40f, if (tapped) 90f else 0f))
        val tap = card("tap", 0f)
        val moving = card("move", 0f)
        val flip = card("flip", 50f).copy(faceId = "front")
        val base = TableScene(listOf(tap, moving, flip), emptyList())
        val target = base.copy(items = listOf(tap.copy(tapped = true, cardTransform = TableCardTransform(28f, 40f, 90f)),
            moving.copy(bounds = moving.bounds.copy(x = 1000f)), flip.copy(faceId = "back"), card("fade", 90f)))
        val animation = HistorySceneAnimation(base)
        animation.retarget(target, 1f, setOf("flip"))
        assertEquals(350, animation.durationMillis)
        assertEquals(305, animation.remainingDurationMillis(45f / 350f))
        val frame = animation.frame(90f / animation.durationMillis)
        assertEquals(90f, frame.single { it.item.cardId == "tap" }.item.cardTransform!!.rotation)
        assertTrue(frame.single { it.item.cardId == "move" }.bounds.x < 1000f)
        assertEquals(90f / 350f, frame.single { it.item.cardId == "fade" }.alpha, .001f)
        assertEquals(kotlin.math.abs(1f - 2f * 90f / 350f), frame.single { it.item.cardId == "flip" }.item.flipScale, .001f)
        assertEquals(90f, animation.frame(.8f).single { it.item.cardId == "tap" }.item.cardTransform!!.rotation)
    }

    // TBL-039
    @Test fun sceneEndsWithItsLongestNaturalChannelAndShortMovesHoldTheirEndpoint() {
        val bounds = TableBounds(0f, 0f, 28f, 40f)
        val card = TableItem("card", bounds, "card", "", cardId = "card", kind = TableItemKind.CARD)
        val base = TableScene(listOf(card), emptyList())
        val target = base.copy(items = listOf(card.copy(bounds = bounds.copy(x = 120f))))
        val animation = HistorySceneAnimation(base)
        animation.retarget(target, 1f)
        assertEquals(100, animation.durationMillis)
        assertEquals(120f, animation.frame(1f).single().bounds.x)
    }

    @Test fun shuffleWigglesOnlyRecordedForwardStepsIncludingSearchCloseAndExplicitShuffle() {
        val f = BasicTableFixture()
        var tree = GameplayTree(f.root)
        var request = 5000
        fun step(action: PlayAction): GameplayMutation {
            val command = GameCommand(f.id(request++), GameScope(f.root.gameId, playId = f.root.id),
                GameAction.Play(action), f.id(9000), "1")
            tree = assertNotNull(HistoryContinuation.change(tree, f.actor, command).tree)
            return tree.nodes.getValue(tree.currentNodeId)
        }
        step(PlayAction.ShowContents(f.container(PlayZone.LIBRARY), ContentsSelection.ALL))
        val display = tree.stateAt(tree.currentNodeId)!!.board.contentsDisplays.single()
        val closed = step(PlayAction.DismissContents(display.id))
        val shuffled = step(PlayAction.ShuffleLibrary(display.containerId, RandomV1.encodeSeed(ByteArray(32))))
        for (node in listOf(closed, shuffled)) {
            val changes = TableChanges()
            changes.apply(TableChangeBatch(listOf(TableAppliedEvent(node))))
            assertEquals(setOf(display.containerId), changes.shuffles(node.id))
            for (viewer in listOf(f.actor, f.other)) {
                val scene = TablePresentation.scene(f.game, f.root, viewer, 412f, 892f, false)
                val pile = scene.items.single { it.id == display.containerId }
                val animation = HistorySceneAnimation(scene)
                assertTrue(animation.retarget(scene, 1f, shuffles = changes.shuffles(node.id), shuffleCursor = node.id))
                assertNotEquals(pile.bounds, animation.frame(.1f).single { it.item.id == pile.id }.bounds)
                assertEquals(pile.bounds, animation.frame(1f).single { it.item.id == pile.id }.bounds)
                assertFalse(animation.retarget(scene, .5f, shuffles = changes.shuffles(node.id), shuffleCursor = node.id))
            }
            changes.apply(TableChangeBatch(listOf(TableAppliedEvent(node, reverse = true))))
            assertTrue(changes.shuffles(node.parentId).isEmpty())
            changes.apply(TableChangeBatch(jump = true))
            assertTrue(changes.shuffles(node.id).isEmpty())
        }
    }

    @Test fun libraryArrivalsAndDeparturesStayFaceDownUntilClearOfPile() {
        val inLibrary = TableFixture.play(library = 1)
        val id = "a/LIBRARY/0"
        val card = inLibrary.board.cards.single { it.id == id }
        val inHand = inLibrary.copy(board = inLibrary.board.copy(
            cards = inLibrary.board.cards.map { if (it.id == id) it.copy(containerId = "a/HAND", knownToAll = true) else it },
            containers = inLibrary.board.containers.map { when (it.id) {
                "a/LIBRARY" -> it.copy(cards = emptyList())
                "a/HAND" -> it.copy(cards = listOf(card.id))
                else -> it
            } }))
        for ((from, to) in listOf(inLibrary to inHand, inHand to inLibrary)) {
            val animation = HistorySceneAnimation(TableFixture.scene(from))
            animation.retarget(TableFixture.scene(to), 1f)
            for (progress in listOf(0f, .01f, .5f, .99f)) {
                val moving = animation.frame(progress).single { it.item.cardId == id }.item
                if (moving.inTransit || progress == 0f) {
                    assertTrue(moving.covered)
                    assertFalse(moving.panelRevealed)
                    assertNull(moving.artwork)
                    assertEquals("", moving.value)
                }
            }
        }
    }
    private fun halfwayX(from: TableBounds, to: TableBounds, duration: Int): Float = from.center.x +
        (to.center.x - from.center.x) * cardMoveProgress(from, to, .5f, duration, DEFAULT_CARD_MOVE_SPEED) -
        (from.width + (to.width - from.width) * cardMoveProgress(from, to, .5f, duration, DEFAULT_CARD_MOVE_SPEED)) / 2
    // TBL-037
    @Test fun tapAndUntapUseNinetyMillisButMovesDrawsAndMixedChangesUseTheirNaturalDurations() {
        val initial = TableFixture.play(board = 2, hand = 1)
        fun tapped(play: PlayState, id: String) = play.copy(board = play.board.copy(cards = play.board.cards.map {
            if (it.id == id) it.copy(tapped = !it.tapped) else it
        }))
        for (id in listOf("a/BATTLEFIELD/0")) {
            val base = TableFixture.scene(initial)
            val tap = TableFixture.scene(tapped(initial, id))
            val animation = HistorySceneAnimation(base)
            animation.retarget(tap, 1f)
            assertEquals(90, animation.durationMillis)
            animation.retarget(base, 1f)
            assertEquals(90, animation.durationMillis)
            animation.retarget(tap, .5f)
            assertEquals(90, animation.durationMillis, "Interrupted untap still rotates")
        }
        val handScene = TableFixture.scene(tapped(initial, "a/HAND/0"))
        assertEquals(TableFixture.scene(initial), handScene, "Legacy tapped Hand cards render upright without a rotation")
        val base = TableFixture.scene(initial)
        val move = moved(initial, listOf("a/HAND/0"), "a/BATTLEFIELD")
        for (target in listOf(move, tapped(move, "a/BATTLEFIELD/0"), tapped(move, "a/HAND/0"),
            moved(initial, listOf("a/LIBRARY/0"), "a/HAND"))) {
            val animation = HistorySceneAnimation(base)
            animation.retarget(TableFixture.scene(target), 1f)
            assertTrue(animation.durationMillis in 90..350)
            assertEquals(TableFixture.scene(target).items.single { it.cardId == "a/HAND/0" }.bounds,
                animation.frame(1f).single { it.item.cardId == "a/HAND/0" }.bounds)
        }
    }

    // TBL-002
    @Test fun adjacentColumnsKeepFixedCentersThroughoutRotationsAndEndpointsDoNotIntersect() {
        // Null placement is the old-game fallback; retained integer and fractional presentation
        // anchors must behave identically. All columns remain one grid unit apart.
        for (landscape in listOf(false, true)) for (zoom in listOf(.65f, 1f, 1.37f, 2f))
            for (placementKind in 0..2) for (pileIndex in 0..2) {
                val initial = TableFixture.play(board = 3)
                fun scene(mask: Int): TableScene {
                    val play = initial.copy(board = initial.board.copy(cards = initial.board.cards.map { card ->
                        if (!card.containerId.endsWith("BATTLEFIELD")) card else {
                            val column = card.id.last().digitToInt()
                            card.copy(tapped = mask and (1 shl column) != 0,
                                placement = if (placementKind == 1) PlayPlacement(BoardPosition(column, 0), card.id) else null)
                        }
                    }))
                    val placements = if (placementKind == 1 && pileIndex == 0) emptyMap() else
                        play.board.cards.filter { it.containerId.endsWith("BATTLEFIELD") }.associate { card ->
                            card.id to TablePlacement(card.id.last().digitToInt() + if (placementKind == 2) .35f else 0f,
                                0f, pileIndex)
                        }
                    return TableFixture.scene(play, landscape, TableLayoutState(TableCamera(zoom = zoom)), placements)
                }
                val scenes = (0..7).map(::scene)
                for (from in 0..7) for (to in 0..7) {
                    val animation = HistorySceneAnimation(scenes[from])
                    animation.retarget(scenes[to], 1f)
                    for (step in 0..20) {
                        val frame = animation.frame(step / 20f)
                        for (owner in listOf("a", "b")) {
                            val cards = (0..2).map { col -> frame.single { it.item.cardId == "$owner/BATTLEFIELD/$col" } }
                            val drawn = cards.map { card ->
                                val b = card.item.faceTransform(card.bounds).bounds(card.bounds.center)
                                val footprint = card.item.footprint
                                assertEquals(card.bounds.width, b.width, .001f)
                                assertEquals(card.bounds.height, b.height, .001f)
                                val endpoint = scenes[to].items.single { it.cardId == card.item.cardId }.bounds
                                assertEquals(endpoint.center.x, b.center.x, .001f, "Tap rotates in place")
                                assertEquals(endpoint.center.y, b.center.y, .001f, "Tap rotates in place")
                                assertEquals(42f * zoom, footprint.width, .001f)
                                // A rigid 28x40 face sweeps wider than the 42dp pitch mid-rotation.
                                assertTrue(b.width <= 49f * zoom && b.height <= 49f * zoom)
                                b
                            }
                            drawn.zipWithNext().forEach { (a, b) -> assertEquals(42f * zoom, b.center.x - a.center.x, .001f) }
                            if (step == 0 || step == 20) for (a in drawn.indices) for (b in a + 1 until drawn.size)
                                assertNull(drawn[a].intersection(drawn[b]), "Overlapping endpoints: $from->$to frame $step zoom $zoom")
                        }
                    }
                }
            }
    }

    private fun projected(state: PlayState, ids: List<String>, source: String): PlayState = state.copy(board = state.board.copy(
        stackEffects = ids.map { StackEffect(it, source) }, containers = state.board.containers.map {
            if (it.zone == PlayZone.STACK) it.copy(cards = ids) else it }))

    // STK-006
    @Test fun stackGhostFliesFromRealCardAndBackWithUndoRedoWithoutMovingRealDrawing() {
        for (landscape in listOf(false, true)) for (zone in listOf(PlayZone.HAND, PlayZone.GRAVEYARD, PlayZone.BATTLEFIELD)) {
            val sourceId = "a/HAND/0"
            val initial = if (zone == PlayZone.HAND) TableFixture.play(hand = 1)
                else moved(TableFixture.play(hand = 1), listOf(sourceId), "a/$zone")
            val projected = projected(initial, listOf("effect"), sourceId)
            val base = TableFixture.scene(initial, landscape)
            val stack = TableFixture.scene(projected, landscape)
            val real = base.items.firstOrNull { it.cardId == sourceId }?.bounds ?: base.items.single { it.id == "a/$zone" }.bounds
            val slot = stack.items.single { it.cardId == "effect" }.bounds
            val animation = HistorySceneAnimation(base)
            repeat(2) { // Add/remove, then undo removal / redo removal.
                assertTrue(animation.retarget(stack, 1f))
                assertEquals(real, animation.frame(0f).single { it.item.cardId == "effect" }.bounds)
                val half = animation.frame(.5f).single { it.item.cardId == "effect" }
                assertEquals(cardMoveProgress(real, slot, .5f, animation.durationMillis, DEFAULT_CARD_MOVE_SPEED) < 1f, half.item.inTransit)
                assertEquals(halfwayX(real, slot, animation.durationMillis), half.bounds.x, .001f)
                assertEquals(slot, animation.frame(1f).single { it.item.cardId == "effect" }.bounds)
                stack.items.firstOrNull { it.cardId == sourceId }?.let { card ->
                    assertEquals(card.bounds, animation.frame(.5f).single { it.item.cardId == sourceId }.bounds)
                }
                assertTrue(animation.retarget(base, 1f))
                assertEquals(slot, animation.frame(0f).single { it.item.cardId == "effect" }.bounds)
                assertEquals(halfwayX(slot, real, animation.durationMillis), animation.frame(.5f).single { it.item.cardId == "effect" }.bounds.x, .001f)
                val returned = animation.frame(1f).single { it.item.cardId == "effect" }
                assertEquals(real, returned.bounds)
                assertEquals(0f, returned.alpha)
            }
        }
    }

    @Test fun buriedTopDoesNotFlyBackAndUncoveredTopDoesNotFlyFromSource() {
        val initial = TableFixture.play(hand = 1)
        val three = TableFixture.scene(projected(initial, listOf("one", "two", "three"), "a/HAND/0"))
        val four = TableFixture.scene(projected(initial, listOf("new", "one", "two", "three"), "a/HAND/0"))
        val animation = HistorySceneAnimation(three)
        animation.retarget(four, 1f)
        val before = three.items.single { it.cardId == "one" }.bounds
        assertEquals(before, animation.frame(.5f).single { it.item.cardId == "one" }.bounds)
        animation.retarget(three, 1f)
        val revealed = animation.frame(0f).single { it.item.cardId == "one" }
        assertNotEquals(three.items.single { it.cardId == "a/HAND/0" }.bounds, revealed.bounds)
    }
    @Test fun buriedRemovalFliesHomeAndUndoSendsItBackEvenWithoutAVisiblePeek() {
        val initial = TableFixture.play(hand = 1)
        val before = TableFixture.scene(projected(initial, listOf("one", "two", "three", "buried"), "a/HAND/0"))
        val after = TableFixture.scene(projected(initial, listOf("one", "two", "three"), "a/HAND/0"))
        assertTrue(before.items.none { it.cardId == "buried" })
        val slot = before.stackProjections.single { it.cardId == "buried" }.bounds
        val real = after.items.single { it.cardId == "a/HAND/0" }.bounds
        val animation = HistorySceneAnimation(before)
        assertTrue(animation.retarget(after, 1f))
        assertEquals(slot, animation.frame(0f).single { it.item.cardId == "buried" }.bounds)
        assertEquals(halfwayX(slot, real, animation.durationMillis), animation.frame(.5f).single { it.item.cardId == "buried" }.bounds.x, .001f)
        assertTrue(animation.retarget(before, 1f))
        assertEquals(real, animation.frame(0f).single { it.item.cardId == "buried" }.bounds)
        assertTrue(animation.frame(.01f).single { it.item.cardId == "buried" }.alpha > 0)
        assertEquals(slot, animation.frame(1f).single { it.item.cardId == "buried" }.bounds)
    }

    private fun moved(play: PlayState, ids: List<String>, destination: String): PlayState = play.copy(board = play.board.copy(
        cards = play.board.cards.map { if (it.id in ids) it.copy(containerId = destination,
            placement = if (destination.endsWith("BATTLEFIELD")) PlayPlacement(BoardPosition(2, 2), it.id) else null) else it },
        containers = play.board.containers.map { c -> c.copy(cards = c.cards.filterNot { it in ids } + if (c.id == destination) ids else emptyList()) }))

    @Test fun confirmationBeforeHistoryIndexNeverPublishesTheOlderBoardAgain() {
        val initial = TableFixture.play(hand = 1)
        val predicted = moved(initial, listOf("a/HAND/0"), "a/BATTLEFIELD").copy(currentNodeId = "predicted-node")
        val endpoint = TableEndpointPresentation()
        endpoint.presented(initial)
        assertEquals(predicted, endpoint.live(predicted, initial, awaitingIndex = false))
        endpoint.presented(predicted)
        val animation = HistorySceneAnimation(TableFixture.scene(initial))
        animation.retarget(TableFixture.scene(predicted), 1f)
        val confirmed = predicted.copy()
        val duringIndexing = endpoint.live(confirmed, initial, awaitingIndex = true)!!
        assertEquals(confirmed, duringIndexing)
        assertFalse(animation.retarget(TableFixture.scene(duringIndexing), .8f))
        assertFalse(animation.retarget(TableFixture.scene(confirmed), 1f))

        // A correction to that same predicted node also goes straight to the accepted endpoint,
        // not via the old indexed board and then forward for a second time.
        val correction = confirmed.copy(board = confirmed.board.copy(cards = confirmed.board.cards.map {
            if (it.id == "a/HAND/0") it.copy(placement = PlayPlacement(BoardPosition(3, 2), it.id)) else it
        }))
        val current = animation.frame(.8f).single { it.item.cardId == "a/HAND/0" }.bounds
        assertEquals(correction, endpoint.live(correction, initial, awaitingIndex = true))
        assertTrue(animation.retarget(TableFixture.scene(correction), .8f))
        assertEquals(current, animation.frame(0f).single { it.item.cardId == "a/HAND/0" }.bounds)

        // Unseen remote nodes still wait for history's foreground/catch-up classification.
        assertEquals(initial, endpoint.live(correction.copy(currentNodeId = "remote-node"), initial, awaitingIndex = true))
    }

    // SYS-009
    @Test fun matchingConfirmationDoesNotRestartEvenMidFlightOrChangeCardIdentity() {
        val initial = TableFixture.play(hand = 1)
        val predicted = TableFixture.scene(moved(initial, listOf("a/HAND/0"), "a/BATTLEFIELD"))
        val animation = HistorySceneAnimation(TableFixture.scene(initial))
        assertTrue(animation.retarget(predicted, 1f))
        val before = animation.frame(.8f)
        // Simulate a receipt changing labels/disclosure metadata and iteration order, not geometry.
        val confirmed = predicted.copy(items = predicted.items.reversed().map { it.copy(label = "Accepted ${it.label}") })
        assertEquals(predicted.animationTargets, confirmed.animationTargets)
        assertFalse(animation.retarget(confirmed, .8f))
        assertEquals(before.map { it.bounds }, animation.frame(.8f).map { it.bounds })
        assertTrue(animation.frame(.8f).all { it.item.label.startsWith("Accepted") })
        assertFalse(animation.retarget(confirmed, 1f))
        assertEquals(predicted.items.single { it.cardId == "a/HAND/0" }.bounds,
            animation.frame(1f).single { it.item.cardId == "a/HAND/0" }.bounds)
    }

    // SYS-009
    @Test fun correctionStartsAtCurrentDrawnBoundsAndRemainsVisibleAcrossViewports() {
        val initial = TableFixture.play(hand = 1)
        val target = TableFixture.scene(moved(initial, listOf("a/HAND/0"), "a/BATTLEFIELD"))
        val animation = HistorySceneAnimation(TableFixture.scene(initial))
        animation.retarget(target, 1f)
        val current = animation.frame(.8f).single { it.item.cardId == "a/HAND/0" }.bounds
        val corrected = target.copy(items = target.items.map { if (it.cardId == "a/HAND/0")
            it.copy(bounds = it.bounds.copy(x = it.bounds.x + 56f)) else it })
        assertTrue(animation.retarget(corrected, .8f))
        assertEquals(current, animation.frame(0f).single { it.item.cardId == "a/HAND/0" }.bounds)
        val half = animation.frame(.5f)
        val card = half.single { it.item.cardId == "a/HAND/0" }
        assertEquals(halfwayX(current, corrected.items.single { it.cardId == "a/HAND/0" }.bounds, animation.durationMillis), card.bounds.x, .001f)
        val rendered = TablePresentation.clip(corrected.copy(items = half.map { it.item.copy(bounds = it.bounds) }), 412f, 820f)
        assertTrue(rendered.items.single { it.cardId == "a/HAND/0" }.visible.isNotEmpty())
    }

    // TBL-040
    @Test fun drawsStartAtLibraryAndReachEachHandSlotForBothPlayersAndOrientations() {
        for (landscape in listOf(false, true)) for (owner in listOf("a", "b")) for (count in listOf(1, 3)) {
            val initial = TableFixture.play(hand = 2)
            val ids = initial.board.containers.single { it.id == "$owner/LIBRARY" }.cards.take(count)
            val source = TableFixture.scene(initial, landscape)
            val target = TableFixture.scene(moved(initial, ids, "$owner/HAND"), landscape)
            val animation = HistorySceneAnimation(source)
            assertTrue(animation.retarget(target, 1f))
            val library = source.items.single { it.id == "$owner/LIBRARY" }
            for (id in ids) {
                val start = animation.frame(0f).single { it.item.cardId == id }
                assertEquals(library.bounds.x, start.bounds.x)
                assertEquals(library.bounds.y, start.bounds.y)
                assertEquals(1f, start.alpha)
                assertTrue(start.item.covered) // Even the viewer's draw leaves a face-down Library pile.
                val half = animation.frame(.5f).single { it.item.cardId == id }
                val endBounds = target.items.single { it.cardId == id }.bounds
                assertEquals(cardMoveProgress(start.bounds, endBounds, .5f, animation.durationMillis, DEFAULT_CARD_MOVE_SPEED) < 1f, half.item.inTransit)
                assertNotEquals(start.bounds, half.bounds)
                val rendered = TablePresentation.clip(target.copy(items = animation.frame(.5f).map { it.item.copy(bounds = it.bounds) }),
                    if (landscape) 900f else 412f, if (landscape) 412f else 820f)
                assertTrue(rendered.items.single { it.cardId == id }.visible.isNotEmpty())
                val end = animation.frame(1f).single { it.item.cardId == id }
                assertEquals(target.items.single { it.cardId == id }.bounds, end.bounds)
                assertFalse(end.item.inTransit)
            }
        }
    }

    @Test fun realStackCardUsesSameAnimationIdentityAsItsHandDrawing() {
        val initial = TableFixture.play(hand = 1)
        val id = "a/HAND/0"
        val source = TableFixture.scene(initial)
        val target = TableFixture.scene(moved(initial, listOf(id), "stack"))
        val animation = HistorySceneAnimation(source)
        animation.retarget(target, 1f)
        val cards = animation.frame(0f).filter { it.item.cardId == id }
        assertEquals(1, cards.size)
        assertEquals(source.items.single { it.cardId == id }.bounds, cards.single().bounds)
        assertEquals(1f, cards.single().alpha)
    }

    // HIS-030, TBL-040, ZON-046
    @Test fun cardsFlyFaceDownBetweenTempPilesAndIntoAndOutOfThem() {
        // a's Temp piles 1 and 2 exist; pile 1 holds one card. Moves: Temp 1 -> Temp 2 (and back, as an undo),
        // Temp 2 -> Library, Graveyard -> Temp 1.
        val base = TableFixture.play(library = 2)
        val one = "a/TEMP/1"; val two = "a/TEMP/2"
        val id = "a/LIBRARY/0"
        val start = moved(base.copy(board = base.board.copy(containers = base.board.containers +
            PlayContainer(one, PlayZone.LIBRARY, "a", temp = true) +
            PlayContainer(two, PlayZone.LIBRARY, "a", temp = true, tempNumber = 2))), listOf(id), one)
        val inTwo = moved(start, listOf(id), two)
        val inLibrary = moved(inTwo, listOf(id), "a/LIBRARY")
        val inGraveyard = moved(start, listOf(id), "a/GRAVEYARD")
        for (landscape in listOf(false, true)) for ((from, to) in listOf(start to inTwo, inTwo to start, inTwo to inLibrary,
                inLibrary to inTwo, inGraveyard to start)) {
            val before = TableFixture.scene(from, landscape); val after = TableFixture.scene(to, landscape)
            val source = before.items.single { it.kind == TableItemKind.PILE && id in it.cards }
            val target = after.items.single { it.kind == TableItemKind.PILE && id in it.cards }
            assertTrue(before.items.none { it.kind == TableItemKind.CARD && it.cardId == id }, "no own drawing in a pile")
            val animation = HistorySceneAnimation(before)
            assertTrue(animation.retarget(after, 1f))
            val departing = animation.frame(0f).single { it.item.cardId == id }
            assertEquals(source.bounds, departing.bounds)
            // Neighbouring piles are close: sample a few milliseconds into the (speed-bound) flight.
            val flying = animation.frame(5f / animation.durationMillis).single { it.item.cardId == id }
            assertTrue(flying.item.inTransit, "$landscape ${source.id} -> ${target.id}")
            assertTrue(flying.item.covered)
            assertNotEquals(source.bounds, flying.bounds)
            assertTrue(animation.durationMillis > 1)
            // Landed: nothing is left drawn over the pile.
            assertTrue(animation.frame(1f).none { it.item.cardId == id && it.alpha > 0f })
        }
    }

    /** Every flight of [ids] from [from] to [to]: present, in transit early on, and gone once landed. */
    private fun pileFlights(from: PlayState, to: PlayState, ids: List<String>, landscape: Boolean = false): List<HistoryAnimatedItem> {
        val before = TableFixture.scene(from, landscape); val after = TableFixture.scene(to, landscape)
        val animation = HistorySceneAnimation(before)
        assertTrue(animation.retarget(after, 1f), "a pile-only change restarts the animation")
        val early = animation.frame(2f / animation.durationMillis)
        val flights = ids.map { id -> early.single { it.item.cardId == id } }
        for ((id, flight) in ids.zip(flights)) {
            assertTrue(flight.item.inTransit, "$id ${landscape}")
            assertEquals(before.items.single { it.kind == TableItemKind.PILE && id in it.cards }.bounds,
                animation.frame(0f).single { it.item.cardId == id }.bounds)
        }
        assertTrue(animation.frame(1f).none { it.item.cardId in ids && it.alpha > 0f })
        return flights
    }

    // HIS-030, TBL-040, ZON-046
    @Test fun millGraveyardExileAndBatchMovesFlyBetweenEitherPlayersPiles() {
        for (landscape in listOf(false, true)) for (owner in listOf("a", "b")) {
            val base = TableFixture.play(library = 4)
            val top = "$owner/LIBRARY/0"
            // Mill: a Library card flies face down (like a draw) and lands face up on the Graveyard.
            val milled = moved(base, listOf(top), "$owner/GRAVEYARD")
            assertTrue(pileFlights(base, milled, listOf(top), landscape).single().item.covered)
            // Graveyard -> Exile and back: public both ways, so the flying card shows its face.
            val exiled = moved(milled, listOf(top), "$owner/EXILE")
            for ((from, to) in listOf(milled to exiled, exiled to milled)) {
                val flight = pileFlights(from, to, listOf(top), landscape).single()
                assertFalse(flight.item.covered)
                assertEquals("Secret face", flight.item.value)
            }
            // Library <-> Exile stays face down in flight.
            val second = "$owner/LIBRARY/1"
            val libraryExile = moved(milled, listOf(second), "$owner/EXILE")
            assertTrue(pileFlights(milled, libraryExile, listOf(second), landscape).single().item.covered)
            assertTrue(pileFlights(libraryExile, milled, listOf(second), landscape).single().item.covered)
            // A multi-card batch: every card flies.
            val batch = (0..3).map { "$owner/LIBRARY/$it" }
            val all = moved(base, batch, "$owner/GRAVEYARD")
            assertEquals(4, pileFlights(base, all, batch, landscape).size)
            val gone = moved(all, batch, "$owner/EXILE")
            assertTrue(pileFlights(all, gone, batch, landscape).none { it.item.covered })
        }
    }

    @Test fun faceDownPileCardsNeverShowTheirFaceInFlight() {
        val base = TableFixture.play(library = 2)
        val id = "a/LIBRARY/0"
        // a's card lies face down in Exile; moving it to the Graveyard turns it face up only on arrival.
        val faceDown = moved(base, listOf(id), "a/EXILE").let { play -> play.copy(board = play.board.copy(cards = play.board.cards.map {
            if (it.id == id) it.copy(covered = true, explicitlyCovered = true) else it })) }
        val withTemp = faceDown.copy(board = faceDown.board.copy(containers = faceDown.board.containers +
            PlayContainer("a/TEMP/1", PlayZone.LIBRARY, "a", temp = true)))
        val toTemp = moved(withTemp, listOf(id), "a/TEMP/1")
        for (flight in pileFlights(withTemp, toTemp, listOf(id)) + pileFlights(toTemp, withTemp, listOf(id))) {
            assertTrue(flight.item.covered)
            assertEquals("", flight.item.value)
            assertNull(flight.item.artwork)
        }
        // A move within one pile adds no flight.
        val reordered = base.copy(board = base.board.copy(containers = base.board.containers.map {
            if (it.id == "a/LIBRARY") it.copy(cards = it.cards.reversed()) else it }))
        val animation = HistorySceneAnimation(TableFixture.scene(base))
        animation.retarget(TableFixture.scene(reordered), 1f)
        assertTrue(animation.frame(.1f).none { it.item.cardId == id })
    }

    @Test fun pileDrawingsAreBuiltOnlyForCardsThatChangedPile() {
        // 80 cards in each player's Graveyard and Exile, plus a Library and a Battlefield card.
        val base = TableFixture.play(board = 1, library = 2)
        fun filled(owner: String): List<PlayCard> = listOf(PlayZone.GRAVEYARD, PlayZone.EXILE).flatMap { zone ->
            (0 until 80).map { PlayCard("$owner/$zone/$it", TableFixture.submitted.digest, owner, "$owner/$zone", "face") } }
        val extra = filled("a") + filled("b")
        val big = base.copy(board = base.board.copy(cards = base.board.cards + extra, containers = base.board.containers.map { c ->
            c.copy(cards = c.cards + extra.filter { it.containerId == c.id }.map { it.id }) }))
        val before = TableFixture.scene(big)
        // An unrelated update (a tap) builds no pile drawing at all.
        val tapped = TableFixture.scene(big.copy(board = big.board.copy(cards = big.board.cards.map {
            if (it.id == "a/BATTLEFIELD/0") it.copy(tapped = true) else it })))
        val animation = HistorySceneAnimation(before)
        val started = System.nanoTime()
        assertTrue(animation.retarget(tapped, 1f))
        val tapMillis = (System.nanoTime() - started) / 1e6
        assertEquals(0, before.pileCards.built); assertEquals(0, tapped.pileCards.built)
        // A mill builds exactly the milled card's drawing; Graveyard -> Exile builds only the moved ones.
        val milledPlay = moved(big, listOf("b/LIBRARY/0"), "b/GRAVEYARD")
        val milled = TableFixture.scene(milledPlay)
        assertTrue(animation.retarget(milled, 1f))
        assertEquals(1, milled.pileCards.built)
        val exiled = TableFixture.scene(moved(milledPlay, listOf("b/GRAVEYARD/3", "b/GRAVEYARD/4"), "b/EXILE"))
        assertTrue(animation.retarget(exiled, 1f))
        assertEquals(3, milled.pileCards.built, "the milled card plus the two departing Graveyard cards")
        assertEquals(2, exiled.pileCards.built)
        println("pile drawings benchmark: tap retarget ${"%.2f".format(tapMillis)} ms with 320 public pile cards")
    }
}
