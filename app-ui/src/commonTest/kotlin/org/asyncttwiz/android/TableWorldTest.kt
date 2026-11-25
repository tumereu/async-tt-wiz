package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.Test
import kotlin.test.*

class TableWorldTest {
    // TBL-013: the pan area reaches 3 card slots past the outermost cards on the left and right; Fit table (the world's
    // bounds) doesn't count them
    @Test fun panExposesThreeEmptySlotsBeyondBothBattlefieldsWithoutChangingFitBounds() {
        val f = TableStatesFixture
        // Sixty more creatures of Alice's, each different: her creatures (two rows of them, TBL-004, TBL-140) are wider than either
        // screen at every zoom
        val combat = f.combat()
        val creature = f.source(f.a, "1/3")
        val extra = (0 until 60).map { n -> creature.copy(id = f.id("extra$n"), corner = "$n/$n", placement = null) }
        val play = combat.copy(board = combat.board.copy(cards = combat.board.cards + extra, containers = combat.board.containers.map {
            if (it.id == creature.containerId) it.copy(cards = it.cards + extra.map(PlayCard::id)) else it }))
        val pitch = TablePresentation.COLUMN_PITCH
        for (landscape in listOf(false, true)) {
            val scene = TablePresentation.scene(f.game, tableBoard(play), f.a, if (landscape) 892f else 412f,
                if (landscape) 412f else 892f, landscape)
            val world = scene.world!!
            assertEquals(world.bounds.x - 3 * pitch, world.panBounds.x, .001f)
            assertEquals(world.bounds.right + 3 * pitch, world.panBounds.right, .001f)
            assertTrue(scene.items.any { it.battlefieldCard })
            for (zoom in listOf(.65f, 1f, 2f)) {
                val left = world.clamp(TableCamera(-10000f, zoom = zoom))
                val right = world.clamp(TableCamera(10000f, zoom = zoom))
                assertEquals(world.viewport.x, world.screen(world.panBounds, left).x, .001f)
                assertEquals(world.viewport.right, world.screen(world.panBounds, right).right, .001f)
            }
            assertTrue(world.bounds.width < world.panBounds.width)
        }
    }
    // TBL-015
    @Test fun boundsAlignContentEdgesForLargeSmallAndMixedAxesAtEveryZoom() {
        val viewport = TableBounds(20f, 30f, 200f, 100f)
        for (width in listOf(80f, 400f)) for (height in listOf(30f, 300f))
            for (zoom in listOf(.65f, 1f, 2f)) {
                val world = TableWorld(viewport, TableBounds(-30f, -40f, width, height))
                for (sign in listOf(-1f, 1f)) {
                    val camera = world.clamp(TableCamera(sign * 10000, sign * 10000, zoom))
                    val content = world.screen(world.bounds, camera)
                    if (width * zoom >= viewport.width) {
                        assertTrue(content.x <= viewport.x + .001f && content.right >= viewport.right - .001f)
                    } else assertTrue(content.x >= viewport.x - .001f && content.right <= viewport.right + .001f)
                    if (height * zoom >= viewport.height) {
                        assertTrue(content.y <= viewport.y + .001f && content.bottom >= viewport.bottom - .001f)
                    } else assertTrue(content.y >= viewport.y - .001f && content.bottom <= viewport.bottom + .001f)
                    val resisted = world.resist(camera.copy(x = camera.x + sign * 1000, y = camera.y + sign * 1000))
                    assertTrue(kotlin.math.abs(resisted.x - camera.x) * zoom in 0f..96f)
                    assertTrue(kotlin.math.abs(resisted.y - camera.y) * zoom in 0f..96f)
                }
            }
    }

    // SYS-041, SYS-150, TBL-001 (D2): an open panel lets the world pan up by its height at every zoom, the bottom of small content
    // too; holding a camera past the usual bounds extends them just enough to keep it where it is
    @Test fun anOpenPanelExtendsThePanBoundsUpwardByItsHeight() {
        val viewport = TableBounds(0f, 0f, 400f, 400f)
        for (height in listOf(100f, 800f)) for (zoom in listOf(.65f, 1f, 2f)) {
            val world = TableWorld(viewport, TableBounds(0f, 0f, 400f, height))
            val usual = world.clamp(TableCamera(0f, 10000f, zoom))
            val panel = world.withBottomInset(150f)
            val up = panel.clamp(TableCamera(0f, 10000f, zoom))
            // Large content's bottom comes just above the panel; small content may already go higher (its top to the top)
            val bottom = world.screen(world.bounds, up).bottom
            if (height * zoom >= viewport.height) assertEquals(viewport.bottom - 150f, bottom, .01f, "the bottom just above the panel")
            else assertTrue(bottom <= viewport.bottom - 150f + .01f, "small content above the panel: $bottom")
            assertTrue(up.y >= usual.y)
            assertEquals(world.clamp(TableCamera(0f, -10000f, zoom)), panel.clamp(TableCamera(0f, -10000f, zoom)), "top unchanged")
            assertEquals(world.clamp(TableCamera(-10000f, 0f, zoom)).x, panel.clamp(TableCamera(-10000f, 0f, zoom)).x, "sides unchanged")
            assertSame(world, world.holding(usual), "nothing to hold inside the usual bounds")
            assertEquals(up, world.holding(up).clamp(up), "a camera past them is held where it is")
        }
    }

    // TBL-005, TBL-238
    @Test fun backgroundBandsCoverViewportWidthEvenWhenAllocatedWorldIsOffscreen() {
        val scene = TableFixture.scene(ui = TableLayoutState(TableCamera(800f, 0f, .65f)))
        val world = scene.world!!
        val shown = world.visibleViewport
        // The battlefield feels infinite: even overscrolled past the pan bounds, the fields fill the width they are seen in.
        assertTrue(scene.fields.all { it.bounds.x == shown.x && it.bounds.width == shown.width })
        val bands = scene.fields.map { it.gridBounds } + TableBounds(0f, scene.fields.first().gridBounds.bottom,
            0f, scene.fields.last().gridBounds.y - scene.fields.first().gridBounds.bottom)
        for (band in bands) {
            val background = world.horizontalBand(band)
            assertEquals(shown.x, background.x)
            assertEquals(shown.width, background.width)
            assertTrue(background.height > 0f)
        }
        assertEquals(.65f, scene.camera.zoom)
        assertEquals(800f, scene.camera.x, "Presentation must not discard gesture overscroll")
    }

    // TBL-001
    @Test fun worldScalesUniformlyWhileAllHudGeometryStaysFixed() {
        val f = TableStatesFixture
        for (landscape in listOf(false, true)) {
            fun scene(camera: TableCamera) = TablePresentation.scene(f.game, tableBoard(f.combat()), f.a,
                if (landscape) 892f else 412f, if (landscape) 412f else 892f, landscape, TableLayoutState(camera))
            val home = scene(TableCamera())
            for (zoom in listOf(.65f, 1.4f, 2f)) {
                val next = scene(TableCamera(18f, -12f, zoom))
                fun transformed(b: TableBounds): TableBounds {
                    val center = home.world!!.viewport.center
                    return TableBounds(center.x + (b.x - center.x - 18f) * zoom,
                        center.y + (b.y - center.y + 12f) * zoom, b.width * zoom, b.height * zoom)
                }
                for (field in home.fields) {
                    val other = next.fields.single { it.owner == field.owner }
                    val expected = transformed(field.gridBounds)
                    // Fields span the whole pan area; their column-0 origin carries the world transform.
                    assertEquals(transformed(TableBounds(field.originX, 0f, 0f, 0f)).x, other.originX, .001f)
                    assertEquals(expected.y, other.gridBounds.y, .001f)
                    assertEquals(expected.height, other.gridBounds.height, .001f)
                    assertEquals(field.rowHeight * zoom, other.rowHeight, .001f)
                }
                val homeGap = home.fields.last().gridBounds.y - home.fields.first().gridBounds.bottom
                assertEquals(homeGap * zoom, next.fields.last().gridBounds.y - next.fields.first().gridBounds.bottom, .001f)
                // CMB-007, CMB-056, CMB-042 (owner, 2026-11-05): the combat band is world content: its panel and cards zoom with
                // the battlefields
                val panel = transformed(home.combatPanel!!)
                val moved = assertNotNull(next.combatPanel)
                assertEquals(panel.x, moved.x, .001f); assertEquals(panel.y, moved.y, .001f)
                assertEquals(panel.width, moved.width, .001f); assertEquals(panel.height, moved.height, .001f)
                for (item in home.items) {
                    val other = next.items.single { it.id == item.id }
                    if (item.zone == PlayZone.BATTLEFIELD) {
                        val expected = transformed(item.bounds)
                        assertEquals(expected.x, other.bounds.x, .001f, item.id)
                        assertEquals(expected.y, other.bounds.y, .001f, item.id)
                        assertEquals(expected.width, other.bounds.width, .001f, item.id)
                        assertEquals(expected.height, other.bounds.height, .001f, item.id)
                    } else assertEquals(item.bounds, other.bounds, item.id)
                }
                assertEquals(home.stackRegion, next.stackRegion)
                assertEquals(home.viewports.filter { it.hand || it.id.startsWith("dock/") || it.id.startsWith("counters/") },
                    next.viewports.filter { it.hand || it.id.startsWith("dock/") || it.id.startsWith("counters/") })
            }
        }
    }

    // CMB-007, CMB-054 (owner, 2026-11-05): opening the combat band pushes the battlefields apart by its height; no row height or
    // HUD changes
    @Test fun openingCombatPushesTheFieldsApartButMovesNoHud() {
        val initial = TableFixture.play(board = 1)
        val play = initial.copy(board = initial.board.copy(combat = CombatArea("combat", listOf(CombatPair("pair", attackers = listOf(CombatOccurrence("attacker", "a/BATTLEFIELD/0")))))))
        for (landscape in listOf(false, true)) {
            val (w, h) = if (landscape) 892f to 412f else 412f to 892f
            val closed = TablePresentation.scene(TableFixture.game, tableBoard(initial), "a", w, h, landscape)
            val open = TablePresentation.scene(TableFixture.game, tableBoard(play), "a", w, h, landscape)
            val panel = assertNotNull(open.combatPanel)
            assertTrue(open.fields.first().gridBounds.bottom <= panel.y && open.fields.last().gridBounds.y >= panel.bottom)
            for ((a, b) in closed.fields.zip(open.fields)) {
                assertEquals(a.gridBounds.height, b.gridBounds.height, .001f)
                assertEquals(a.rowHeight, b.rowHeight, .001f)
            }
            assertEquals(closed.stackRegion, open.stackRegion)
            for (item in closed.items.filter { it.zone != PlayZone.BATTLEFIELD })
                assertEquals(item.bounds, open.items.single { it.id == item.id }.bounds, item.id)
        }
    }

    @Test fun fitIncludesDistantOccupiedCellsAndCombatAndPanNeverLosesEntireWorld() {
        val positions = mapOf("a/BATTLEFIELD/0" to TablePlacement(8, 4), "b/BATTLEFIELD/0" to TablePlacement(6, 3))
        val scene = TableFixture.scene(TableFixture.play(board = 1), placements = positions)
        val world = scene.world!!
        val camera = world.fit()
        assertTrue(camera.zoom in TablePresentation.MIN_ZOOM..TablePresentation.MAX_ZOOM)
        val fitted = world.screen(world.bounds, camera)
        assertEquals(world.viewport.center.x, fitted.center.x, .001f)
        assertEquals(world.viewport.center.y, fitted.center.y, .001f)
        assertTrue(fitted.x >= world.viewport.x - .001f && fitted.right <= world.viewport.right + .001f)
        assertTrue(fitted.y >= world.viewport.y - .001f && fitted.bottom <= world.viewport.bottom + .001f)
        for (x in listOf(-Float.MAX_VALUE, Float.MAX_VALUE)) for (y in listOf(-Float.MAX_VALUE, Float.MAX_VALUE)) {
            val clamped = world.clamp(TableCamera(x, y, 2f))
            assertNotNull(world.screen(world.bounds, clamped).intersection(world.viewport))
        }
    }

    // WEB-016, WEB-071, TBL-121, TBL-012, SYS-041 (owner notes 2026-11-16): on the desktop table the seat boxes cover the viewport's left
    // [inset]: the opening view puts column 0 just right of them, fitting the table fits it into the room right of them, and
    // both views are within the pan bounds, which still let the table pan under the boxes
    @Test fun aLeftInsetMovesTheOpeningViewAndTheFitRightOfIt() {
        val viewport = TableBounds(6f, 4f, 1100f, 600f)
        val inset = 380f
        for (width in listOf(700f, 1100f, 2600f)) for (height in listOf(300f, 700f)) {
            val bounds = TableBounds(0f, 0f, width, height)
            val world = TableWorld(viewport, bounds, bounds.copy(x = -3 * 84f, width = width + 6 * 84f), leftInset = inset)
            val home = world.landscapeHome()
            assertEquals(viewport.x + inset, world.screen(TablePoint(0f, 0f), home).x, .01f, "column 0 right of the boxes ($width × $height)")
            val fit = world.fit()
            val fitted = world.screen(bounds, fit)
            val room = viewport.width - inset
            // Centred in the room right of the boxes (the pan bounds don't move it), inside it where it fits there at all
            assertEquals(viewport.x + inset + room / 2, fitted.center.x, .01f, "fitted right of the boxes ($width × $height): $fitted")
            if (width * TablePresentation.MIN_ZOOM <= room)
                assertTrue(fitted.x >= viewport.x + inset - .01f && fitted.right <= viewport.right + .01f, "inside the room: $fitted")
            assertEquals(home.x, world.clamp(home).x, .01f, "the opening view within the pan bounds ($width × $height)")
            // Panned left as far as it goes, the table's right end (and its empty columns) still reach the viewport's right edge,
            // its left end under the boxes
            val left = world.clamp(TableCamera(10000f, zoom = fit.zoom))
            val panned = world.screen(world.panBounds, left)
            if (panned.width > viewport.width) assertEquals(viewport.right, panned.right, .01f)
        }
        // Without an inset nothing changes: column 0 at the left edge, the fit centred in the whole viewport
        val bounds = TableBounds(0f, 0f, 2600f, 300f)
        val plain = TableWorld(viewport, bounds, bounds.copy(x = -252f, width = 2600f + 504f))
        assertEquals(viewport.x, plain.screen(TablePoint(0f, 0f), plain.landscapeHome()).x, .01f)
        assertEquals(viewport.center.x, plain.screen(bounds, plain.fit()).center.x, .01f)
    }

    @Test fun portraitStackOccludesWorldDrawingSelectionAndDropsAtEveryZoom() {
        for (zoom in listOf(.65f, 1f, 2f)) {
            val scene = TableFixture.scene(ui = TableLayoutState(TableCamera(zoom = zoom)))
            val region = scene.stackRegion!!
            assertEquals(412f, region.right); assertEquals(410f, region.center.y)
            for (item in scene.items.filter { it.zone == PlayZone.BATTLEFIELD }) {
                assertTrue(item.visible.none { it.intersection(region) != null })
            }
            assertFalse(scene.worldInput(region.center))
            val start = scene.items.single { it.cardId == "a/HAND/0" }.visible.first().center
            val events = mutableListOf<TableIntent>()
            val gesture = TableGestureCoordinator(events::add)
            gesture.update(scene, true)
            gesture.down(start, 0); gesture.move(region.center, 80); gesture.up(region.center, 100)
            assertEquals(TableDestination.Container("stack", PlayZone.STACK), events.filterIsInstance<TableIntent.Move>().single().destination)
        }
    }

    // TBL-238 (owner playtest 2026-11-22): on the phone the battlefields are seen through the table's whole width and, in
    // landscape, up to both bars: fields, the combat band and touches reach the screen's sides, while where the cards lie,
    // the opening view and the pan limits stay those of the room they are laid out in
    @Test fun thePhoneBattlefieldsAreSeenWithoutTheirMargins() {
        val f = TableStatesFixture
        for (landscape in listOf(false, true)) {
            val width = if (landscape) 892f else 412f
            val height = if (landscape) 412f else 892f
            val scene = TablePresentation.scene(f.game, tableBoard(f.combat()), f.a, width, height, landscape,
                TableLayoutState(TableCamera()), landscapeHome = false)
            val world = scene.world!!
            val laid = world.viewport
            val shown = world.visibleViewport
            assertEquals(0f, shown.x, "seen from the screen's left edge")
            assertEquals(width, shown.right, "seen up to the screen's right edge")
            if (landscape) {
                // From the top bar (the header row) to the bottom bar, 4 dp past the room they are laid out in
                assertEquals(TablePresentation.HEADER, shown.y, .001f)
                assertEquals(laid.y - 4f, shown.y, .001f); assertEquals(laid.bottom + 4f, shown.bottom, .001f)
            } else {
                assertEquals(laid.y, shown.y); assertEquals(laid.bottom, shown.bottom)
            }
            // Where the cards lie is unchanged: column 0 is still 6 dp in from the left edge at the untouched camera
            assertEquals(6f, laid.x); assertEquals(width - 12f, laid.width)
            for (field in scene.fields) {
                assertEquals(laid.x, field.originX, .001f)
                assertEquals(0f, field.bounds.x); assertEquals(width, field.bounds.right)
                assertTrue(scene.worldInput(TablePoint(2f, field.bounds.center.y)), "the margin on the left pans the battlefield")
                // (clear of the Stack pinned at the right edge, STK-007)
                val edge = if (field === scene.fields.first()) field.bounds.y + 2f else field.bounds.bottom - 2f
                assertTrue(scene.worldInput(TablePoint(width - 2f, edge)), "and the one on the right")
            }
            val band = assertNotNull(scene.combatBand)
            assertEquals(0f, band.x); assertEquals(width, band.right)
            // The pan limits are the laid-out room's (TBL-013, TBL-015)
            val left = world.clamp(TableCamera(-10000f))
            assertEquals(laid.x, world.screen(world.panBounds, left).x, .001f)
        }
    }

    private fun largeHands(hand: Float, camera: TableCamera = TableCamera()) = TableStatesFixture.let { f ->
        TablePresentation.scene(f.game, tableBoard(f.initial), f.a, 892f, 412f, true, TableLayoutState(camera), landscapeHome = true,
            appearance = CardAppearance(customizeAreas = true, areas = mapOf(CardArea.HAND to hand)))
    }

    // TBL-238 (owner 2026-11-24): in landscape with hand cards taller than the bar the battlefields are seen under them too, from
    // the top bar down to the bottom bar, the hands' cards drawn over them; where the cards lie, the opening view and what is
    // centred on the battlefields stay those of the room between the hand cards, and with 1x hand cards nothing changes
    @Test fun inLandscapeTheBattlefieldsAreSeenUnderLargeHandCards() {
        val f = TableStatesFixture
        val bar = 412f - TableControlsHeight
        val header = TablePresentation.HEADER
        val plain = largeHands(1f).world!!
        assertNull(plain.panViewport); assertNull(plain.clearViewport)
        assertEquals(header, plain.visibleViewport.y, .001f); assertEquals(bar, plain.visibleViewport.bottom, .001f)
        for (size in listOf(1.5f, 3f)) {
            val scene = largeHands(size)
            val world = scene.world!!
            val laid = world.viewport
            val shown = world.visibleViewport
            assertEquals(TableBounds(0f, header, 892f, bar - header), shown, "seen from bar to bar across the whole width")
            // The room the rows are laid out in is still between the hands' cards, 4 dp from the bar or your fan's top (ZON-138)
            val own = scene.items.single { it.kind == TableItemKind.HAND && it.owner == f.a }
            val other = scene.items.single { it.kind == TableItemKind.HAND && it.owner == f.b }
            assertEquals(minOf(bar, own.bounds.y) - 4f, laid.bottom, .001f)
            // ZON-126: the other player's hand is a small fan in their top bar: the room starts 4 dp under the bar
            assertEquals(header + 4f, laid.y, .001f)
            assertEquals(TableBounds(0f, laid.y - 4f, 892f, laid.height + 8f), world.clearOfHands)
            assertEquals(world.clearOfHands, scene.boardArea, "the review dim and centre texts stay clear of the hand cards")
            // TBL-121: the opening view centred on the battlefields in that room, as before
            assertEquals(world.landscapeHome(), scene.camera)
            assertEquals(laid.center.y, world.screen(world.bounds, scene.camera).center.y, .01f)
            for (field in scene.fields) { assertEquals(0f, field.bounds.x); assertEquals(892f, field.bounds.right) }
            // The strips beside the hands are the battlefield's: touching there pans it
            assertTrue(scene.worldInput(TablePoint(4f, bar - 4f)) && scene.worldInput(TablePoint(4f, header + 4f)))
            // Only the other player's hand row's part in its bar is the row; your fan's own part is its part in the bar (ZON-138)
            assertEquals(TableBounds(own.bounds.x, bar, own.bounds.width, 412f - bar), own.handBand)
            assertTrue(other.handFan && other.handBand == null && other.bounds.bottom <= header, "the other player's small fan in their bar")
            // Battlefield cards are drawn and touched only where no hand card lies over them
            val handCards = scene.items.filter { it.kind == TableItemKind.CARD && it.zone == PlayZone.HAND }
            for (card in scene.items.filter { it.battlefieldCard }) for (part in card.visible) for (hand in handCards)
                for (cover in hand.visible) assertNull(part.intersection(cover)?.takeIf { it.width > .01f && it.height > .01f },
                    "${card.label} drawn over ${hand.label}")
        }
        // Panned down to the end at 1.2x, the land rows reach under the hand cards: part of a land shows beside them, below the room
        // the rows lie in (ZON-126: opened, they fit the room now that the other player's hand is a small fan in their bar)
        val scene = largeHands(3f, largeHands(3f).world!!.clamp(TableCamera(-10000f, -10000f, 1.2f)))
        assertTrue(scene.items.any { card -> card.battlefieldCard && card.visible.any { it.bottom > scene.world!!.viewport.bottom + 4f } })
    }

    // TBL-238, TBL-015, SYS-150 (owner 2026-11-24): with large hand cards in landscape the rows may also be panned under the hand
    // cards, up to 4 dp from both bars; content taller than that room keeps its old limits, Fit table is unchanged and an open
    // panel still lets the rows' bottom come up above it
    @Test fun withLargeHandCardsTheRowsPanUnderTheHandCards() {
        val bar = 412f - TableControlsHeight
        val header = TablePresentation.HEADER
        val world = largeHands(2f).world!!
        val laid = world.viewport
        val room = assertNotNull(world.panViewport)
        assertEquals(header + 4f, room.y, .001f); assertEquals(bar - 4f, room.bottom, .001f)
        // At the smallest zoom the rows are shorter than the room between the hand cards: they pan right under either hand
        val small = TablePresentation.MIN_ZOOM
        assertTrue(world.bounds.height * small < laid.height)
        assertEquals(room.y, world.screen(world.panBounds, world.clamp(TableCamera(0f, 10000f, small))).y, .01f)
        assertEquals(room.bottom, world.screen(world.panBounds, world.clamp(TableCamera(0f, -10000f, small))).bottom, .01f)
        // ... with an open panel, its height further up from the room the rows lie in
        val panel = world.withBottomInset(60f)
        assertEquals(laid.bottom - 60f, panel.screen(panel.panBounds, panel.clamp(TableCamera(0f, 10000f, small))).bottom, .01f)
        // Fit table fits them into the room between the hand cards, as before
        assertEquals(laid.center.y, world.screen(world.bounds, world.fit()).center.y, .01f)
        // At 2x the rows are taller than the whole room: they cover the room between the hand cards, as before
        assertTrue(world.bounds.height * 2f > room.height)
        assertEquals(laid.y, world.screen(world.panBounds, world.clamp(TableCamera(0f, -10000f, 2f))).y, .01f)
        assertEquals(laid.bottom, world.screen(world.panBounds, world.clamp(TableCamera(0f, 10000f, 2f))).bottom, .01f)
        // Horizontally the limits are the laid-out room's (TBL-013)
        assertEquals(laid.x, world.screen(world.panBounds, world.clamp(TableCamera(-10000f))).x, .01f)
    }

    // TBL-238, ZON-138 (owner 2026-11-24, 2026-11-25): with large hand cards in landscape a touch on a hand card is the card's, also
    // where it stands over the battlefield; beside the fan above the bar it reaches the battlefield under it; in the bar it is the
    // fan's
    @Test fun touchesAboveTheBarGoToTheHandCardDrawnThereElseToTheBattlefield() {
        val f = TableStatesFixture
        val bar = 412f - TableControlsHeight
        val scene = largeHands(3f)
        val own = scene.items.single { it.kind == TableItemKind.HAND && it.owner == f.a }
        val cards = scene.items.filter { it.kind == TableItemKind.CARD && it.viewport == own.viewport }.sortedBy { it.bounds.x }
        assertTrue(cards.size >= 2)
        assertTrue(cards[0].bounds.y < bar - 8f, "the 3x hand cards stand above the bar")
        val slot = assertNotNull(cards[0].handSlot)
        val above = TablePoint(slot.center.x, (slot.y + bar) / 2)
        assertEquals(cards[0].id, scene.hit(above)?.id)
        assertEquals(own.viewport, scene.panRegion(above), "a drag on the card still turns the fan or drags the card")
        val gap = TablePoint(own.bounds.x - 30f, above.y)
        assertTrue(scene.hit(gap).let { it == null || it.battlefieldCard }, "beside the fan: the battlefield, not the hand")
        assertEquals(WORLD_PAN_REGION, scene.panRegion(gap))
        val inBar = TablePoint(own.bounds.right - 8f, (bar + own.bounds.bottom) / 2)
        assertEquals(own.id, scene.hit(inBar)?.id)
        assertEquals(own.viewport, scene.panRegion(inBar))
        // Gestures: a tap on the card's part above the bar opens it; a sideways drag beside the fan pans the battlefield, one in
        // the bar is the fan's
        val events = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator(events::add)
        gestures.update(scene, true)
        gestures.down(above, 0); gestures.up(above, 20)
        assertEquals(cards[0].id, events.filterIsInstance<TableIntent.Actions>().single().itemId)
        events.clear()
        val end = TablePoint(gap.x + 60f, gap.y)
        gestures.down(gap, 1000); gestures.move(end, 1040); gestures.up(end, 1060)
        assertTrue(events.any { it is TableIntent.Camera }, "pans the battlefield: $events")
        assertTrue(events.none { it is TableIntent.Scroll || it is TableIntent.Overscroll })
        // (the down settles the pan's spring first)
        gestures.down(inBar, 3000); events.clear()
        gestures.move(TablePoint(inBar.x + 60f, inBar.y), 3040); gestures.up(TablePoint(inBar.x + 60f, inBar.y), 3060)
        assertTrue(events.none { it is TableIntent.Camera }, "the hand's, not the battlefield's: $events")
    }
}
