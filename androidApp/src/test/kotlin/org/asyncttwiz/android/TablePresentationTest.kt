package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

internal object TableFixture {
    val game = GameSummary("game", "Canvas Spark", "2026-10-23T00:00:00Z", AccountIdentity("a", "Alice"),
        AccountIdentity("b", "Bob"), "round", "1", InvitationState.ACCEPTED, lifecycle = GameLifecycle.ACTIVE, playId = "play")
    val definition = CardDefinition("Secret name", listOf(DefinitionFace("face", "Secret face", rulesText = "Secret rules")))
    val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
    fun container(owner: String, zone: PlayZone) = "$owner/$zone"
    fun play(hand: Int = 0, board: Int = 0, library: Int = 4): PlayState {
        val cards = mutableListOf<PlayCard>()
        val containers = mutableListOf<PlayContainer>()
        for (owner in listOf("a", "b")) for (zone in PlayZone.entries.filter { it != PlayZone.STACK }) {
            val count = when (zone) { PlayZone.HAND -> hand; PlayZone.BATTLEFIELD -> board; PlayZone.LIBRARY -> library; else -> 0 }
            val id = container(owner, zone)
            val members = (0 until count).map { n -> "$owner/$zone/$n" }
            cards += members.map { PlayCard(it, submitted.digest, owner, id, "face", covered = zone != PlayZone.BATTLEFIELD) }
            containers += PlayContainer(id, zone, owner, members)
        }
        containers += PlayContainer("stack", PlayZone.STACK)
        return PlayState("game", "play", "round", "request", PlayRoot("root", "branch"), "root",
            BoardState(listOf(PlayPlayer("a"), PlayPlayer("b")), cards, containers, TurnState("a")), listOf(submitted),
            emptyList(), InitialStarter(StarterChoice("a"), "a"))
    }
    fun scene(play: PlayState = play(board = 3, hand = 2), landscape: Boolean = false,
        ui: TableLayoutState = TableLayoutState(), placements: Map<String, TablePlacement> = emptyMap()) =
        TablePresentation.scene(game, play, "a", if (landscape) 900f else 412f, if (landscape) 412f else 820f,
            landscape, ui, placements)
}

class TablePresentationTest {
    @Test fun landscapeKeepsBothOuterLandsRowsSeparateFromCombatAndReview() {
        for ((w, h) in listOf(892f to 412f, 640f to 360f, 892f to 364f)) {
            for (reviewing in listOf(false, true)) for (combat in listOf(false, true)) {
                val initial = LiveTableFixture.play()
                val play = if (combat) initial.copy(board = initial.board.copy(combat = CombatArea("combat"))) else initial
                val scene = TablePresentation.scene(LiveTableFixture.game, play, "a", w, h, true, reviewing = reviewing)
                val lane = scene.viewports.single { it.id == "combat" }.bounds
                for (field in scene.fields) {
                    val lands = field.landsBounds(field.owner == "a")
                    assertEquals(field.bounds.height / 3, lands.height, .001f)
                    assertEquals(lands, lands.intersection(field.bounds))
                    assertNull(lands.intersection(lane))
                    assertNull(field.bounds.intersection(lane))
                    val land = scene.items.single { it.cardId == "${field.owner}/BATTLEFIELD/0" }
                    assertTrue(land.visible.any { it.intersection(lands) != null })
                    assertTrue(scene.items.single { it.cardId == "${field.owner}/BATTLEFIELD/2" }.visible.isNotEmpty())
                }
            }
        }
    }

    @Test fun bothFieldsAndOwnHandRemainUsableAtMockAndCompactViewportsIncludingReview() {
        for ((w, h, landscape) in listOf(Triple(412f, 892f, false), Triple(892f, 412f, true),
            Triple(360f, 640f, false), Triple(640f, 360f, true))) for (reviewing in listOf(false, true)) {
            val scene = TablePresentation.scene(LiveTableFixture.game, LiveTableFixture.play(), "a", w, h, landscape, reviewing = reviewing)
            val hand = scene.items.single { it.id == "a/HAND" }
            for (field in scene.fields) {
                assertTrue(field.bounds.height > 0)
                assertNull(field.bounds.intersection(hand.bounds))
                assertTrue(scene.items.single { it.cardId == "${field.owner}/BATTLEFIELD/2" }.visible.isNotEmpty())
            }
            scene.items.filter { it.cardId != null && it.viewport == "a/HAND" }.forEach {
                assertFalse(it.covered); assertTrue(it.value.isNotEmpty())
            }
            scene.items.filter { it.cardId != null && it.viewport == "b/HAND" }.forEach {
                assertTrue(it.covered); assertEquals("", it.value); assertNull(it.artwork)
            }
        }
    }

    @Test fun portraitHidesEmptyCounterRailsAndReclaimsTheirHeight() {
        val play = TableFixture.play().let { it.copy(board = it.board.copy(players = listOf(
            PlayPlayer("a", counters = mapOf(CounterColor.BLUE to "1")), PlayPlayer("b")))) }
        val before = TableFixture.scene(play)
        val after = TableFixture.scene(TableFixture.play())
        assertEquals(0, after.items.count { it.kind == TableItemKind.COUNTER })
        assertEquals(6, before.items.count { it.kind == TableItemKind.COUNTER })
        assertTrue(after.viewports.none { it.id.startsWith("counters/") })
        assertTrue(after.fields.first().bounds.height > before.fields.first().bounds.height)
        val landscape = TableFixture.scene(TableFixture.play(), true)
        assertEquals(12, landscape.items.count { it.kind == TableItemKind.COUNTER })
        assertTrue(landscape.items.filter { it.kind == TableItemKind.COUNTER }.all { it.value.isEmpty() })
    }

    @Test fun commonScaleAndEqualFootprintsAcrossOrientationOwnerRotationAndPiles() {
        for (landscape in listOf(false, true)) for (zoom in listOf(.65f, 1f, 2f)) {
            val p = TableFixture.play(board = 3, hand = 3)
            val state = p.copy(board = p.board.copy(cards = p.board.cards.mapIndexed { n, c -> c.copy(tapped = n % 2 == 0) }))
            val positions = state.board.cards.filter { it.containerId.endsWith("BATTLEFIELD") }.associate { c ->
                c.id to TablePlacement(0, 0, c.id.last().digitToInt()) }
            val scene = TableFixture.scene(state, landscape, TableLayoutState(TableCamera(zoom = zoom)), positions)
            val cards = scene.items.filter { it.kind == TableItemKind.CARD }
            assertTrue(cards.isNotEmpty())
            cards.forEach { c ->
                val scale = if (c.zone == PlayZone.BATTLEFIELD) zoom else 1f
                assertEquals(28 * scale, minOf(c.bounds.width, c.bounds.height), .001f)
                assertEquals(40 * scale, maxOf(c.bounds.width, c.bounds.height), .001f)
                if (c.zone == PlayZone.BATTLEFIELD) {
                    assertEquals(TablePresentation.GRID * zoom, c.footprint.width, .001f)
                    assertTrue(c.bounds.x >= c.footprint.x && c.bounds.right <= c.footprint.right)
                    assertTrue(c.bounds.y >= c.footprint.y && c.bounds.bottom <= c.footprint.bottom)
                }
            }
            scene.items.filter { it.kind == TableItemKind.PILE }.forEach { assertTrue(it.bounds.width >= 28f) }
        }
    }

    @Test fun groupsPeekTopLeftAndLandingPreviewsMatchDrawnCardsAndDropTargets() {
        for (landscape in listOf(false, true)) for (zoom in listOf(.65f, 1f, 2f)) for (tapped in listOf(false, true)) {
            val p = TableFixture.play(board = 3)
            val state = p.copy(board = p.board.copy(cards = p.board.cards.map { it.copy(tapped = tapped) }))
            val ids = (0..2).map { "a/BATTLEFIELD/$it" }
            val placements = ids.mapIndexed { index, id -> id to TablePlacement(1, 0, index) }.toMap()
            val home = TableFixture.scene(state, landscape, placements = placements)
            val world = home.world!!
            val rowCenter = home.items.single { it.cardId == ids.first() }.footprint.center
            val camera = TableCamera(x = rowCenter.x - world.viewport.center.x, y = rowCenter.y - world.viewport.center.y, zoom = zoom)
            val scene = TableFixture.scene(state, landscape, TableLayoutState(camera), placements)
            val cards = ids.map { id -> scene.items.single { it.cardId == id } }
            cards.zipWithNext().forEach { (top, lower) ->
                assertEquals(top.bounds.x - 4 * zoom, lower.bounds.x, .001f)
                assertEquals(top.bounds.y - 4 * zoom, lower.bounds.y, .001f)
                assertEquals(top.footprint, lower.footprint)
                assertEquals(lower.id, scene.hit(TablePoint(lower.bounds.x + zoom, lower.bounds.y + zoom))?.id)
            }
            val scope = TableScope(ids.first(), ids)
            val landing = tableDragLanding(scene, TableGesturePreview(scope = scope, destination = TableDestination.CardPile(ids.first())))
            assertEquals(cards.map { it.bounds }, landing.outlines)
            val preview = tableDragLanding(scene, TableGesturePreview(scope = scope, destination = TableDestination.Grid("a/BATTLEFIELD", 1, 0)))
            assertEquals(cards.mapNotNull { it.bounds.intersection(scene.fields.last().bounds) }, preview.outlines)
        }
    }

    @Test fun ownerIsNotFieldAndCoveredMetadataNeverLeaksThroughTableSemantics() {
        val p = TableFixture.play(board = 1, hand = 1)
        val changed = p.copy(board = p.board.copy(cards = p.board.cards.map { c ->
            if (c.id == "a/BATTLEFIELD/0") c.copy(owner = "b") else c
        }))
        val scene = TableFixture.scene(changed)
        val moved = scene.items.single { it.cardId == "a/BATTLEFIELD/0" }
        assertEquals("b", moved.owner); assertTrue(moved.label.contains("owner Bob"))
        scene.items.filter { it.zone == PlayZone.HAND && it.cardId != null && it.owner == "b" }.forEach {
            assertEquals("", it.value); assertEquals("", it.corner)
            assertFalse(it.label.contains("Secret")); assertTrue(it.label.startsWith("Covered card"))
        }
        val ownHand = scene.items.single { it.cardId == "a/HAND/0" }
        assertEquals("Secret face", ownHand.value)
        assertFalse(ownHand.covered)
        assertTrue(changed.board.cards.single { it.id == "a/HAND/0" }.covered)
    }

    @Test fun bothHandsCenterWhenFittingAndReachBothEndsWithoutChangingOrderOrScale() {
        for (landscape in listOf(false, true)) for (count in listOf(2, 40)) {
            val p = TableFixture.play(hand = count)
            val scene = TableFixture.scene(p, landscape)
            for (owner in listOf("a", "b")) {
                val id = TableFixture.container(owner, PlayZone.HAND)
                val port = scene.viewports.single { it.id == id }
                val first = scene.items.single { it.cardId == "$owner/HAND/0" }
                if (count == 2) {
                    assertEquals(0f, port.maxScroll)
                    val last = scene.items.single { it.cardId == "$owner/HAND/1" }
                    assertEquals(port.bounds.center.x, (first.bounds.x + last.bounds.right) / 2, .01f)
                } else {
                    assertTrue(first.visible.isNotEmpty()); assertTrue(port.maxScroll > 0)
                    val end = TableFixture.scene(p, landscape, TableLayoutState(scroll = mapOf(id to Float.MAX_VALUE)))
                    val last = end.items.single { it.cardId == "$owner/HAND/${count - 1}" }
                    assertTrue(last.visible.isNotEmpty()); assertTrue(last.bounds.right <= port.bounds.right + .01f)
                    assertEquals(scene.cardWidth, end.cardWidth)
                    assertEquals(p.board.containers.single { it.id == id }.cards,
                        end.items.filter { it.viewport == id && it.cardId != null }.map { it.cardId })
                }
            }
        }
    }

    @Test fun docksAndStackUseCurrentOrientationAndZoomOverflowIsNavigable() {
        for (landscape in listOf(false, true)) {
            val p = TableFixture.play()
            val scene = TableFixture.scene(p, landscape, TableLayoutState(TableCamera(zoom = 2f)))
            val stack = scene.items.single { it.zone == PlayZone.STACK }
            val dock = scene.viewports.single { it.id == "dock/a" }
            assertEquals(TableFixture.scene(p, landscape).viewports.single { it.id == dock.id }.maxScroll, dock.maxScroll)
            if (landscape) assertTrue(stack.bounds.right < scene.fields.first().bounds.x)
            else assertEquals(410f, stack.bounds.center.y)
            val sidebar = scene.viewports.firstOrNull { it.id == "sidebar" }
            val end = TableFixture.scene(p, landscape, TableLayoutState(TableCamera(zoom = 2f),
                mapOf("dock/a" to dock.maxScroll), mapOf("sidebar" to (sidebar?.maxScrollY ?: 0f))))
            assertTrue(end.items.single { it.id == "a/EXILE" }.visible.isNotEmpty())
            assertTrue(scene.items.single { it.id == "a/LIBRARY" }.label.contains("4 cards"))
        }
    }

    @Test fun clippingAndVisiblePeeksDriveHitRectangleAndSemanticsTogether() {
        val p = TableFixture.play(board = 3)
        val positions = p.board.cards.filter { it.containerId.endsWith("BATTLEFIELD") }.associate { it.id to TablePlacement(0, 0, it.id.last().digitToInt()) }
        val scene = TableFixture.scene(p, placements = positions)
        val under = scene.items.single { it.cardId == "a/BATTLEFIELD/2" }
        val top = scene.items.single { it.cardId == "a/BATTLEFIELD/0" }
        assertTrue(under.visible.isNotEmpty())
        under.visible.forEach { b -> assertEquals(under.id, scene.hit(b.center)?.id); assertTrue(under.cardId in scene.rectangle(b)) }
        val insideTop = TableBounds(top.bounds.right - 4, top.bounds.bottom - 4, 2f, 2f)
        assertEquals(setOf(top.cardId), scene.rectangle(insideTop))
        val fullyHidden = TableFixture.scene(p, placements = positions.mapValues { TablePlacement(0, 0, 0) })
        val hidden = fullyHidden.items.single { it.cardId == top.cardId }
        assertTrue(hidden.visible.isEmpty())
        assertFalse(hidden.cardId in fullyHidden.rectangle(hidden.bounds))
    }

    @Test fun animatedDrawnBoundsNotDestinationBoundsOwnTargets() {
        val scene = TableFixture.scene(TableFixture.play(board = 1))
        val old = scene.items.single { it.cardId == "a/BATTLEFIELD/0" }
        val shifted = old.copy(bounds = old.bounds.copy(x = old.bounds.x + 120))
        val animated = TablePresentation.clip(scene.copy(items = scene.items.map { if (it.id == old.id) shifted else it }), 412f, 820f)
        assertNotEquals(old.id, animated.hit(old.bounds.center)?.id)
        assertEquals(old.id, animated.hit(shifted.bounds.center)?.id)
        assertTrue(animated.items.single { it.id == old.id }.visible.all { it.x >= shifted.bounds.x })
    }

    @Test fun portraitCounterPredicateAndBlankValuesPreserveState() {
        listOf("", " ", "0", "-000", "+0").forEach { assertFalse(TablePresentation.nonzero(it)) }
        listOf("-1", "flying", "0.0", "0001").forEach { assertTrue(TablePresentation.nonzero(it)) }
        val p = TableFixture.play().let { it.copy(board = it.board.copy(players = listOf(
            PlayPlayer("a", counters = mapOf(CounterColor.RED to "0")), PlayPlayer("b", counters = mapOf(CounterColor.BLUE to "-1"))))) }
        val portrait = TableFixture.scene(p)
        assertTrue(portrait.items.none { it.kind == TableItemKind.COUNTER && it.owner == "a" })
        val shown = portrait.items.filter { it.kind == TableItemKind.COUNTER && it.owner == "b" }
        assertEquals(6, shown.size); assertEquals("", shown.first { it.counter == CounterColor.RED }.value)
        assertTrue(shown.first { it.counter == CounterColor.RED }.label.contains("no value"))
        assertEquals(12, TableFixture.scene(p, true).items.count { it.kind == TableItemKind.COUNTER })
        assertEquals("0", p.board.players.first().counters[CounterColor.RED])
    }

    @Test fun portraitCounterVisibilityIncludesTextAndSignedNonzeroValuesPerPlayer() {
        for (value in listOf("", " ", "0", "-000", "+0", "1", "-2", "poisoned")) {
            val initial = TableFixture.play()
            val play = initial.copy(board = initial.board.copy(players = initial.board.players.map {
                if (it.accountId == "a") it.copy(counters = mapOf(CounterColor.GREEN to value, CounterColor.RED to "0")) else it
            }))
            val counters = TableFixture.scene(play).items.filter { it.kind == TableItemKind.COUNTER }
            assertEquals(if (TablePresentation.nonzero(value)) 6 else 0, counters.size, value)
            assertTrue(counters.all { it.owner == "a" })
            if (counters.isNotEmpty()) assertEquals("0", counters.single { it.counter == CounterColor.RED }.value)
        }
    }

    @Test fun stackStaysBetweenPlayerDocksWithoutCounterOrFieldOverlap() {
        for (landscape in listOf(false, true)) for (zoom in listOf(.65f, 1f, 2f)) {
            val scene = TableFixture.scene(LiveTableFixture.play(), landscape, TableLayoutState(TableCamera(zoom = zoom)))
            val stack = scene.items.single { it.zone == PlayZone.STACK }
            val opponent = scene.items.single { it.id == "life/b" }
            val own = scene.items.single { it.id == "life/a" }
            assertTrue(stack.bounds.center.y > opponent.bounds.center.y)
            assertTrue(stack.bounds.center.y < own.bounds.center.y)
            assertNotNull(scene.stackRegion)
            if (landscape) assertTrue(scene.fields.none { it.bounds.intersection(scene.stackRegion!!) != null })
            else assertEquals(410f, stack.bounds.center.y)
            assertTrue(scene.items.filter { it.kind == TableItemKind.COUNTER }.none { it.bounds.intersection(stack.bounds) != null })
        }
    }

    @Test fun portraitStackIsScreenPinnedAndDocksAreOutsideHandsRegardlessOfCountersZoomOrReview() {
        for (counterOwners in listOf(emptySet(), setOf("a"), setOf("b"), setOf("a", "b"))) {
            val initial = TableFixture.play(hand = 2)
            val empty = initial.copy(board = initial.board.copy(players = initial.board.players.map {
                it.copy(counters = if (it.accountId in counterOwners) mapOf(CounterColor.BLUE to "3") else emptyMap())
            }))
            val stackIds = empty.board.cards.filter { it.containerId.endsWith("HAND") }.map { it.id }
            val populated = empty.copy(board = empty.board.copy(
                cards = empty.board.cards.map { if (it.id in stackIds) it.copy(containerId = "stack", covered = false) else it },
                containers = empty.board.containers.map { it.copy(cards = if (it.id == "stack") stackIds else it.cards - stackIds.toSet()) }))
            for (width in listOf(320f, 360f, 412f)) for (zoom in listOf(.65f, 1f, 2f)) for (reviewing in listOf(false, true)) {
                var emptyFields: List<TableField>? = null
                for (play in listOf(empty, populated)) for (scroll in listOf(0f, Float.MAX_VALUE)) {
                    val scene = TablePresentation.scene(TableFixture.game, play, "a", width, 892f, false,
                        TableLayoutState(TableCamera(zoom = zoom), mapOf("dock/a" to scroll, "dock/b" to scroll,
                            "counters/a" to scroll, "counters/b" to scroll)), reviewing = reviewing)
                    val topDock = scene.viewports.single { it.id == "dock/b" }.bounds
                    val bottomDock = scene.viewports.single { it.id == "dock/a" }.bounds
                    val topCounters = scene.viewports.singleOrNull { it.id == "counters/b" }?.bounds
                    val topHand = scene.items.single { it.id == "b/HAND" }.bounds
                    val bottomHand = scene.items.single { it.id == "a/HAND" }.bounds
                    val stack = scene.items.single { it.id == "stack" }
                    val region = assertNotNull(scene.stackRegion)
                    assertEquals(topCounters?.bottom ?: topDock.bottom, topHand.y, .001f)
                    assertEquals(bottomHand.bottom, bottomDock.y, .001f)
                    assertEquals(446f, stack.bounds.center.y, .001f)
                    assertEquals(446f, region.center.y, .001f)
                    assertEquals(width, region.right, .001f)
                    assertEquals(56f, stack.bounds.width, .001f)
                    assertEquals(stack.bounds.width + 8f, region.width, .001f)
                    assertEquals(stack.bounds.height + 8f, region.height, .001f)
                    assertEquals(12f * zoom, scene.fields.last().gridBounds.y - scene.fields.first().gridBounds.bottom, .001f)
                    assertEquals(topHand.bottom, scene.world!!.viewport.y, .001f)
                    assertEquals(bottomHand.y, scene.world.viewport.bottom, .001f)
                    for (port in scene.viewports.filter { it.id.startsWith("dock/") || it.id.startsWith("counters/") }) {
                        assertNull(port.bounds.intersection(region))
                    }
                    for (item in scene.items.filter { it.kind == TableItemKind.COUNTER || it.kind == TableItemKind.LIFE ||
                        it.zone in listOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE) }) {
                        assertTrue(item.visible.all { it.intersection(region) == null }, item.id)
                    }
                    assertEquals(stack.id, scene.hit(stack.stackBadge.center)?.id)
                    assertEquals("Stack, ${stack.cards.size} entries", stack.label)
                    if (play == empty) emptyFields = scene.fields else {
                        assertEquals(emptyFields, scene.fields)
                        val top = scene.items.last { it.zone == PlayZone.STACK && it.kind == TableItemKind.CARD }
                         // Stack retains its card face and count-badge interaction routes at every world zoom.
                        assertEquals(top.id, scene.hit(TablePoint(top.bounds.right - zoom, top.bounds.bottom - zoom))?.id)
                        assertEquals(stack.bounds, TablePresentation.stackBubbles(scene, play, top.cardId, width, 892f)?.anchor)
                    }
                }
            }
        }
    }

    @Test fun portraitOnlyReservesMiddleHeightForAnOpenCombatLane() {
        val initial = TableFixture.play()
        val play = initial.copy(board = initial.board.copy(combat = CombatArea("combat")))
        val scene = TableFixture.scene(play)
        val lane = scene.viewports.single { it.id == "combat" }.bounds
        assertEquals(CombatStackPresentation.combat(play.board, scene.cardWidth, scene.cardHeight).height, lane.height)
        val closed = TableFixture.scene(initial)
        assertEquals(closed.fields.first().gridBounds.height, scene.fields.first().gridBounds.height)
        assertEquals(closed.fields.first().rowHeight, scene.fields.first().rowHeight)
        assertTrue(scene.fields.first().gridBounds.bottom < lane.y)
        assertTrue(scene.fields.last().gridBounds.y > lane.bottom)
        assertTrue(scene.fields.first().gridBounds.y < closed.fields.first().gridBounds.y)
        assertTrue(scene.fields.last().gridBounds.y > closed.fields.last().gridBounds.y)
        assertEquals(closed.stackRegion, scene.stackRegion)
        assertEquals(closed.viewports.filter { it.id.startsWith("dock/") }, scene.viewports.filter { it.id.startsWith("dock/") })
    }

    @Test fun portraitWorldViewportFillsBetweenTheHands() {
        for (review in listOf(false, true)) {
            val scene = TablePresentation.scene(LiveTableFixture.game, LiveTableFixture.play(), "a", 412f, 892f, false, reviewing = review)
            assertEquals(scene.items.single { it.id == "a/HAND" }.bounds.y, scene.world!!.viewport.bottom, .01f)
            assertEquals(scene.items.single { it.id == "b/HAND" }.bounds.bottom, scene.world.viewport.y, .01f)
            assertTrue(scene.fields.first().bounds.height > 100f)
        }
    }

    @Test fun automaticInspectionUsesCurrentRegionNotOwnerAndNeverGrantsOpponentHiddenSide() {
        val p = TableFixture.play(hand = 1)
        val changed = p.copy(board = p.board.copy(cards = p.board.cards.map { c -> c.copy(owner = if (c.owner == "a") "b" else "a") }))
        assertEquals(TableFixture.definition, TablePresentation.inspection(changed, "a", "a/HAND/0"))
        val hidden = TablePresentation.inspection(changed, "a", "b/HAND/0")!!
        assertEquals("Covered card", hidden.name); assertEquals("", hidden.faces.single().rulesText)
        assertFalse(hidden.toString().contains("Secret")); assertNull(TablePresentation.inspection(changed, "a", "gone"))
        val uncovered = changed.copy(board = changed.board.copy(cards = changed.board.cards.map { it.copy(covered = false) }))
        assertEquals("Covered card", TablePresentation.inspection(uncovered, "a", "b/HAND/0")!!.name)
    }

    @Test fun handStripMenuPreviewAndInspectionAgreeForHolderAndOpponentRegardlessOfCoverFlag() {
        for (covered in listOf(false, true)) {
            val p = TableFixture.play(hand = 1).let { it.copy(board = it.board.copy(cards = it.board.cards.map { card ->
                card.copy(covered = covered, owner = if (card.owner == "a") "b" else "a")
            })) }
            val card = p.board.cards.single { it.id == "a/HAND/0" }
            for (viewer in listOf("a", "b")) {
                val scene = TablePresentation.scene(TableFixture.game, p, viewer, 412f, 820f, false)
                val item = scene.items.single { it.cardId == card.id }
                val preview = ManualDisclosure.permittedFaces(p, card, viewer, inspecting = false)
                if (viewer == "a") {
                    assertFalse(item.covered)
                    assertEquals("Secret face", item.value)
                    assertEquals(listOf("Secret face"), preview.map { it.name })
                    assertEquals(TableFixture.definition, TablePresentation.inspection(p, viewer, card.id))
                } else {
                    assertTrue(item.covered)
                    assertFalse(item.label.contains("Secret"))
                    assertTrue(preview.isEmpty())
                    assertEquals("Covered card", TablePresentation.inspection(p, viewer, card.id)!!.name)
                }
            }
        }
    }

    @Test fun legacyDefaultCoveredPublicCardsAreVisibleButExplicitFaceDownCardsStayHidden() {
        for (explicit in listOf(false, true)) {
            val p = TableFixture.play(board = 1).let { it.copy(board = it.board.copy(cards = it.board.cards.map { card ->
                card.copy(covered = true, explicitlyCovered = explicit)
            })) }
            for (viewer in listOf("a", "b")) {
                val scene = TablePresentation.scene(TableFixture.game, p, viewer, 412f, 820f, false)
                val card = p.board.cards.single { it.id == "a/BATTLEFIELD/0" }
                assertEquals(explicit, scene.items.single { it.cardId == card.id }.covered)
                assertEquals(explicit, TablePresentation.cardForActions(p, card.id).covered)
                assertEquals(explicit, ManualDisclosure.permittedFaces(p, card, viewer, inspecting = false).isEmpty())
                assertEquals(if (explicit && viewer != "a") "Covered card" else "Secret face",
                    TablePresentation.inspection(p, viewer, card.id)!!.faces.single().name)
            }
        }
    }

    @Test fun libraryIsHiddenInOrdinaryPresentationForBothPlayersEvenWhenUncovered() {
        val p = TableFixture.play(library = 1).let { it.copy(board = it.board.copy(cards = it.board.cards.map { card ->
            card.copy(covered = false)
        })) }
        val card = p.board.cards.single { it.id == "a/LIBRARY/0" }
        for (viewer in listOf("a", "b")) {
            val scene = TablePresentation.scene(TableFixture.game, p, viewer, 412f, 820f, false)
            assertTrue(scene.items.single { it.id == "a/LIBRARY" }.covered)
            assertTrue(ManualDisclosure.permittedFaces(p, card, viewer, inspecting = false).isEmpty())
            if (viewer != "a") assertEquals("Covered card", TablePresentation.inspection(p, viewer, card.id)!!.name)
        }
    }

    @Test fun compactLandscapeSidebarCanReachLastCounterWithoutOverlappingStackOrHands() {
        val p = TableFixture.play()
        fun scene(ui: TableLayoutState) = TablePresentation.scene(TableFixture.game, p, "a", 640f, 300f, true, ui)
        val first = scene(TableLayoutState(TableCamera(zoom = 2f)))
        val sidebar = first.viewports.single { it.id == "sidebar" }
        assertTrue(sidebar.maxScrollY > 0)
        val end = scene(TableLayoutState(TableCamera(zoom = 2f), scrollY = mapOf("sidebar" to sidebar.maxScrollY)))
        val counters = end.items.filter { it.kind == TableItemKind.COUNTER && it.owner == "a" }
        assertTrue(counters.first().visible.isNotEmpty())
        val rail = end.viewports.single { it.id == "counters/a" }
        val farEnd = scene(TableLayoutState(TableCamera(zoom = 2f),
            scroll = mapOf("counters/a" to rail.maxScroll), scrollY = mapOf("sidebar" to sidebar.maxScrollY)))
        assertTrue(farEnd.items.single { it.id == counters.last().id }.visible.isNotEmpty())
        val ownHand = end.viewports.single { it.id == "a/HAND" }.bounds
        assertTrue(counters.flatMap { it.visible }.all { it.bottom <= ownHand.y })
        val stack = end.items.single { it.zone == PlayZone.STACK }.bounds
        assertTrue(end.items.filter { it.kind == TableItemKind.COUNTER }.none { it.bounds.intersection(stack) != null })
    }
}
