package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

internal object TableFixture {
    // Real games have UUID ids.
    val gameId = java.util.UUID.nameUUIDFromBytes("game".toByteArray()).toString()
    val playId = java.util.UUID.nameUUIDFromBytes("play".toByteArray()).toString()
    val game = GameSummary(gameId, "Canvas Spark", "2026-10-23T00:00:00Z", AccountIdentity("a", "Alice"),
        AccountIdentity("b", "Bob"), "round", "1", InvitationState.ACCEPTED, lifecycle = GameLifecycle.ACTIVE, playId = playId)
    val definition = CardDefinition("Secret name", listOf(DefinitionFace("face", "Secret face", rulesText = "Secret rules",
        scryfallId = placeholderPrinting("Secret name"))))
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
        return PlayState(gameId, playId, "round", "request", PlayRoot("root", "branch"),
            BoardState(listOf(PlayPlayer("a"), PlayPlayer("b")), cards, containers, TurnState("a")), listOf(submitted),
            emptyList(), InitialStarter(StarterChoice("a"), "a"))
    }
    /** [play] with its battlefield cards told apart (each its own P/T corner), so none stack (TBL-053). */
    fun apart(play: PlayState): PlayState = play.copy(board = play.board.copy(cards = play.board.cards.map { card ->
        if (card.containerId.endsWith("BATTLEFIELD")) card.copy(corner = "${card.id.substringAfterLast('/')}/1") else card }))
    fun scene(play: PlayState = play(board = 3, hand = 2), landscape: Boolean = false,
        ui: TableLayoutState = TableLayoutState(), placements: Map<String, TablePlacement> = emptyMap()) =
        TablePresentation.scene(game, tableBoard(play), "a", if (landscape) 900f else 412f, if (landscape) 412f else 820f,
            landscape, ui, placements)
}

class TablePresentationTest {
    // TBL-121: landscape opens zoomed out so both battlefields' rows fit between the hands (large cards: at 1× they don't),
    // the rows' left ends where they were; portrait, and a camera the player moved, are left alone
    @Test fun landscapeOpensOnBothBattlefieldsWholeHeight() {
        val play = TableFixture.play(board = 3, hand = 2)
        // TBL-009 (owner, 2026-11-08): with your hand in the bottom bar the battlefields are taller: cards a little larger
        // than before don't fit at 1×
        fun scene(landscape: Boolean, ui: TableLayoutState = TableLayoutState(), home: Boolean = true, size: Float = .85f) =
            TablePresentation.scene(TableFixture.game, tableBoard(play), "a", if (landscape) 892f else 412f, if (landscape) 412f else 892f,
                landscape, ui, landscapeHome = home, appearance = CardAppearance(cardSize = size))
        val plain = scene(true, home = false)
        val opened = scene(true)
        val viewport = assertNotNull(opened.world).viewport
        assertTrue(opened.camera.zoom < 1f && opened.camera.zoom >= TablePresentation.MIN_ZOOM, "${opened.camera}")
        assertTrue(plain.fields.any { it.gridBounds.y < viewport.y - .5f || it.gridBounds.bottom > viewport.bottom + .5f },
            "at 1× the rows don't fit")
        for (field in opened.fields) {
            assertTrue(field.gridBounds.y >= viewport.y - .5f && field.gridBounds.bottom <= viewport.bottom + .5f, "$field in $viewport")
            assertEquals(plain.fields.single { it.owner == field.owner }.originX, field.originX, .01f)
        }
        // TBL-121, TBL-010 (B1, three rows each): larger cards don't fit even at the least zoom: it opens there, centred on
        // the battlefields
        val large = scene(true, size = 1.25f)
        assertEquals(TablePresentation.MIN_ZOOM, large.camera.zoom, .001f)
        assertEquals(large.world!!.viewport.center.y, (large.fields.first().gridBounds.y + large.fields.last().gridBounds.bottom) / 2, 1f)
        assertEquals(TableCamera(), scene(false).camera)
        val moved = TableCamera(10f, 0f, 1f)
        assertEquals(moved, scene(true, TableLayoutState(moved)).camera)
    }

    // TBL-009: in landscape every pile size setting fits the bar rows: the piles at most 32 dp tall, each with its own
    // whole 48 dp touch target inside its row, nothing scrolling sideways
    @Test fun landscapeBarRowsFitAllPileSizesAndGlobalCardSizeFallbacksWithoutScrolling() {
        val sizes = (5..30).map { it / 10f }
        val appearances = sizes.flatMap { size ->
            listOf(CardAppearance(cardSize = size),
                CardAppearance(cardSize = size, customizeAreas = true),
                CardAppearance(cardSize = size, customizeAreas = true, areas = mapOf(CardArea.PILES to .5f))) +
                listOf(.5f, 1f, 3f).map { global -> CardAppearance(cardSize = global, customizeAreas = true,
                    areas = mapOf(CardArea.PILES to size)) }
        }
        for ((width, height) in listOf(900f to 412f, 640f to 360f)) for (appearance in appearances) {
            val scene = TablePresentation.scene(TableFixture.game, tableBoard(TableFixture.play()), "a", width, height, true,
                appearance = appearance)
            val pileHeight = minOf(40f * appearance.size(CardArea.PILES), 32f)
            assertEquals(TableBounds(6f, scene.world!!.viewport.y, width - 12f, scene.world!!.viewport.height), scene.world!!.viewport)
            for (owner in listOf("a", "b")) {
                val dock = scene.viewports.single { it.id == "dock/$owner" }
                assertEquals(0f, dock.maxScroll)
                for (zone in listOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE)) {
                    val pile = scene.items.single { it.id == "$owner/$zone" }
                    assertEquals(pileHeight, pile.bounds.height, .001f)
                    assertEquals(pileHeight * .7f, pile.bounds.width, .001f)
                    assertEquals(listOf(pile.bounds), pile.visible, "$width x $height, $appearance, ${pile.id}")
                    assertEquals(pile.pileCountBubble, pile.pileCountBubble.intersection(dock.bounds))
                    val touch = assertNotNull(pile.touchBounds)
                    assertEquals(48f, touch.width, .001f)
                    assertEquals(48f, touch.height, .001f)
                    assertEquals(touch, touch.intersection(dock.bounds))
                    for (point in listOf(TablePoint(touch.x + .1f, touch.center.y), TablePoint(touch.right - .1f, touch.center.y),
                        TablePoint(touch.center.x, touch.y + .1f), TablePoint(touch.center.x, touch.bottom - .1f)))
                        assertEquals(pile.id, scene.hit(point)?.id, "$width x $height, $appearance, $point")
                }
            }
        }
    }

    // TBL-009 (owner, 2026-11-08): in landscape your hand sits in the bottom bar, after your life square and piles, up to
    // the bar's fixed-width right end (the controls); no hand strip above the bar: the battlefields reach the bar. Portrait
    // keeps its hand row above the bottom bar
    @Test fun landscapeHandSitsInTheBottomBarBeforeTheControls() {
        for ((width, height) in listOf(900f to 412f, 640f to 360f)) {
            val scene = TablePresentation.scene(TableFixture.game, tableBoard(TableFixture.play(hand = 12)), "a", width, height, true)
            val bar = height - TableControlsHeight
            val hand = scene.items.single { it.id == "a/HAND" }.bounds
            val dock = scene.viewports.single { it.id == "dock/a" }.bounds
            assertTrue(hand.y >= bar && hand.bottom <= height, "in the bottom bar: $hand")
            assertTrue(hand.x >= dock.right, "after the life square and piles: $hand, $dock")
            assertEquals(width - TablePresentation.Landscape.controls(width), hand.right, .001f)
            assertTrue(TablePresentation.Landscape.controls(width) <= width / 2)
            assertTrue(hand.width >= TablePresentation.Landscape.MIN_OWN_HAND, "$hand")
            val cards = scene.items.filter { it.zone == PlayZone.HAND && it.owner == "a" && it.kind == TableItemKind.CARD }
            assertEquals(12, cards.size)
            assertTrue(cards.all { it.bounds.y >= hand.y && it.bounds.bottom <= hand.bottom }, "cards inside the hand row")
            assertTrue(scene.viewports.single { it.id == "a/HAND" }.maxScroll > 0f, "a long hand scrolls")
            val world = assertNotNull(scene.world).viewport
            assertEquals(bar - 4f, world.bottom, .001f)
        }
        val portrait = TableFixture.scene(TableFixture.play(hand = 2))
        val hand = portrait.items.single { it.id == "a/HAND" }.bounds
        assertEquals(8f, hand.x)
        assertTrue(hand.bottom <= 820f - TableControlsHeight - 64f, "portrait: above the own piles row: $hand")
    }

    // TBL-009, STK-007 (owner, 2026-11-07): in landscape the opponent's life square and piles sit in the top bar after the
    // turn header, yours in the bottom bar left of the controls; the battlefields take the whole width and the Stack is
    // pinned at the right edge, mid-screen, as in portrait
    @Test fun landscapeRowsSitInTheBarsWithTheStackAtTheRightEdge() {
        for ((width, height) in listOf(900f to 412f, 640f to 360f)) for (scale in listOf(.5f, 1f)) {
            val scene = TablePresentation.scene(TableFixture.game, tableBoard(TableFixture.play()), "a", width, height, true,
                appearance = CardAppearance(customizeAreas = true, areas = mapOf(CardArea.PILES to scale)))
            assertNull(scene.viewports.firstOrNull { it.id == "sidebar" })
            assertTrue(scene.viewports.none { it.id.startsWith("counters/") })
            val bottomBar = height - TableControlsHeight
            for (owner in listOf("a", "b")) {
                val own = owner == "a"
                val dock = scene.viewports.single { it.id == "dock/$owner" }.bounds
                assertEquals(if (own) TableBounds(10f, bottomBar, dock.width, TableControlsHeight)
                    else TableBounds(48f + 4f + (if (width >= 892f) 128f else 80f) + 4f, 0f, dock.width, TablePresentation.HEADER), dock)
                if (own) assertTrue(dock.right <= width / 2, "clear of the controls: $dock")
                else assertTrue(dock.right < scene.items.single { it.id == "b/HAND" }.bounds.x, "before the opponent's hand")
                val life = scene.items.single { it.id == "life/$owner" }
                assertTrue(life.bounds.width >= 40f && life.bounds.height >= 40f, "${life.bounds}")
                assertEquals(life.bounds, life.bounds.intersection(dock))
                assertEquals(life.id, scene.hit(life.bounds.center)?.id)
                val piles = listOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE).map { zone ->
                    scene.items.single { it.id == "$owner/$zone" } }
                assertEquals(48f, piles[1].bounds.x - piles[0].bounds.x, .001f)
                assertTrue(piles[0].touchBounds!!.x >= life.bounds.right, "piles after the life square")
                for (pile in piles) assertEquals(dock.center.y, pile.bounds.center.y, .001f)
            }
            val stack = scene.items.single { it.id == "stack" }
            val region = scene.stackRegion!!
            assertEquals(width, region.right, .001f)
            val world = scene.world!!.viewport
            assertEquals((height / 2).coerceIn(world.y + region.height / 2, world.bottom - region.height / 2), stack.bounds.center.y, .001f)
            assertNull(stack.touchBounds)
        }
    }

    @Test fun specialPileCountBubblesAreSmallerAndCenteredExactlyOnTheirCornerInBothLayouts() {
        for (landscape in listOf(false, true)) {
            val scene = TableFixture.scene(landscape = landscape)
            val piles = scene.items.filter { it.zone in setOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE) && !it.temp }
            assertEquals(6, piles.size)
            for (pile in piles) {
                val bubble = pile.pileCountBubble
                assertEquals(TablePoint(pile.bounds.x, pile.bounds.y), bubble.center)
                assertEquals(16f, bubble.width)
                assertEquals(16f, bubble.height)
                val port = scene.viewports.single { it.id == pile.viewport }.bounds
                assertEquals(bubble, bubble.intersection(port), "Bubble must not be clipped in the dock")
            }
        }
    }


    @Test fun landscapeKeepsBothOuterLandsRowsSeparateFromCombatAndReview() {
        for ((w, h) in listOf(892f to 412f, 640f to 360f, 892f to 364f)) {
            for (reviewing in listOf(false, true)) for (combat in listOf(false, true)) {
                val initial = LiveTableFixture.play()
                val play = if (combat) initial.copy(board = initial.board.copy(combat = CombatArea("combat"))) else initial
                val scene = TablePresentation.scene(LiveTableFixture.game, tableBoard(play), "a", w, h, true, reviewing = reviewing)
                val lane = scene.viewports.single { it.id == "combat" }.bounds
                for (field in scene.fields) {
                    val lands = field.landsBounds(field.owner == "a")
                    // TBL-003: three rows, the land row by the player's own edge
                    assertEquals(field.gridBounds.height / 3, lands.height, .001f)
                    assertEquals(lands, lands.intersection(field.gridBounds))
                    // CMB-007: the combat band lies between the battlefields, never over their outer lands rows
                    assertNull(lands.intersection(lane))
                    val camera = scene.world!!.clamp(TableCamera(y = if (field.owner == "a") 10000f else -10000f))
                    val panned = TablePresentation.scene(LiveTableFixture.game, tableBoard(play), "a", w, h, true,
                        TableLayoutState(camera), reviewing = reviewing)
                    val land = panned.items.single { it.cardId == "${field.owner}/BATTLEFIELD/0" }
                    val pannedLands = panned.fields.single { it.owner == field.owner }.landsBounds(field.owner == "a")
                    assertTrue(land.visible.any { it.intersection(pannedLands) != null })
                    // CMB-007 (owner, 2026-11-05): an open band pushes the battlefields apart, so on a low screen they are
                    // panned to (above); without combat their middle shows at once
                    if (!combat) assertTrue(scene.items.single { it.cardId == "${field.owner}/BATTLEFIELD/2" }.visible.isNotEmpty())
                }
            }
        }
    }

    @Test fun bothFieldsAndOwnHandRemainUsableAtMockAndCompactViewportsIncludingReview() {
        for ((w, h, landscape) in listOf(Triple(412f, 892f, false), Triple(892f, 412f, true),
            Triple(360f, 640f, false), Triple(640f, 360f, true))) for (reviewing in listOf(false, true)) {
            val scene = TablePresentation.scene(LiveTableFixture.game, tableBoard(LiveTableFixture.play()), "a", w, h, landscape, reviewing = reviewing)
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


    // TBL-002
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
                    assertEquals(TablePresentation.COLUMN_PITCH * zoom, c.footprint.width, .001f)
                    assertTrue(c.bounds.x >= c.footprint.x - 8 * zoom && c.bounds.right <= c.footprint.right)
                    assertTrue(c.bounds.y >= c.footprint.y && c.bounds.bottom <= c.footprint.bottom)
                }
            }
            // TBL-009: landscape's piles fit their bar row, a little smaller
            scene.items.filter { it.kind == TableItemKind.PILE }.forEach {
                assertTrue(it.bounds.width >= if (landscape && it.zone != PlayZone.STACK) 28f * .8f - .001f else 28f) }
        }
    }

    // TBL-002
    @Test fun battlefieldGroupsHaveFixed42DpSlotsAndRotateAroundTheSameCenter() {
        // The 1st and 3rd cards lie side by side in the upper band's row nearest the middle (the 2nd behind the 1st, TBL-004)
        val play = TableFixture.apart(TableFixture.play(board = 3))
        for (tapped in listOf(false, true)) {
            val state = play.copy(board = play.board.copy(cards = play.board.cards.map { it.copy(tapped = tapped) }))
            val scene = TableFixture.scene(state)
            val cards = listOf(0, 2).map { n -> scene.items.single { it.cardId == "a/BATTLEFIELD/$n" } }.sortedBy { it.bounds.x }
            assertEquals(42f, cards[1].bounds.x - cards[0].bounds.x, .001f)
            assertEquals(if (tapped) 2f else 14f, cards[1].bounds.x - cards[0].bounds.right, .001f)
            val upright = TableFixture.scene(play).items.single { it.cardId == cards[0].cardId }
            assertEquals(upright.bounds.center, cards[0].bounds.center)
            assertEquals(56f, cards[0].footprint.height)
        }
    }

    // ZON-012
    @Test fun knownLibraryTopsStayCoveredWithoutArtworkOrEyeBadgeForEitherViewer() {
        val play = TableFixture.play(library = 2, hand = 1).let { p -> p.copy(board = p.board.copy(
            cards = p.board.cards.map { it.copy(knownToAll = true, covered = false) })) }
        for (viewer in listOf("a", "b")) {
            val scene = TablePresentation.scene(TableFixture.game, tableBoard(play), viewer, 400f, 800f, false)
            for (pile in scene.items.filter { it.zone == PlayZone.LIBRARY && !it.temp }) {
                assertTrue(pile.covered)
                        assertNull(pile.artwork)
                        assertEquals("", pile.corner)
                    }
            }
    }

    // TBL-054, TBL-053: a tight stack's second and third cards show 3 dp each (later ones lie on the third), the top one
    // carrying ×N; a land stack fans 8 dp per land; every member is drawn and the lower ones' strips are hit as
    // themselves; a drop on the battlefield lights up that battlefield, no slot (TBL-004, TBL-052)
    @Test fun stacksLieTightOrFanOutAndShowTheirCount() {
        for (landscape in listOf(false, true)) for (zoom in listOf(.65f, 1f, 2f)) for (tapped in listOf(false, true)) {
            val p = TableFixture.play(board = 4)
            val state = p.copy(board = p.board.copy(cards = p.board.cards.map { it.copy(tapped = tapped) }))
            val ids = (0..3).map { "a/BATTLEFIELD/$it" }
            val home = TableFixture.scene(state, landscape)
            val world = home.world!!
            val rowCenter = home.items.single { it.cardId == ids.first() }.footprint.center
            val camera = TableCamera(x = rowCenter.x - world.viewport.center.x, y = rowCenter.y - world.viewport.center.y, zoom = zoom)
            val scene = TableFixture.scene(state, landscape, TableLayoutState(camera))
            val cards = ids.map { id -> scene.items.single { it.cardId == id } }
            listOf(0f, 3 * zoom, 6 * zoom, 6 * zoom).zip(cards).forEach { (x, card) -> assertEquals(x, card.bounds.x - cards[0].bounds.x, .001f) }
            assertEquals(listOf(1, 1, 1, 4), cards.map { it.copies })
            assertTrue(cards.all { it.group == "stack:${ids.first()}" })
            assertEquals(cards[0].id, scene.hit(TablePoint(cards[0].bounds.x + zoom, cards[0].bounds.center.y))?.id)
            assertTrue(cards[2].visible.isEmpty(), "the fourth lies on the third")
            val scope = TableScope(ids.first(), ids)
            val field = listOf(scene.fields.last().bounds)
            assertEquals(field, tableDragLanding(scene, TableGesturePreview(scope = scope, destination = TableDestination.CardPile(ids.first()))).outlines)
            assertEquals(field, tableDragLanding(scene, TableGesturePreview(scope = scope, destination = TableDestination.Grid("a/BATTLEFIELD", 1, 0))).outlines)
        }
        val lands = TableFixture.play(board = 3).let { p -> p.copy(definitions = p.definitions.map { d ->
            d.copy(definition = d.definition.copy(faces = d.definition.faces.map { it.copy(typeLine = "Basic Land — Forest") })) }) }
        val scene = TableFixture.scene(lands)
        val fanned = (0..2).map { n -> scene.items.single { it.cardId == "a/BATTLEFIELD/$n" } }
        assertEquals(listOf(0f, 8f, 16f), fanned.map { it.bounds.x - fanned[0].bounds.x })
        assertTrue(fanned.all { it.copies == 1 }, "a land stack shows its count by its fan")
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
            // STK-007: pinned at the right edge in both orientations (TBL-009)
            assertEquals(if (landscape) 900f else 412f, scene.stackRegion!!.right, .001f)
            if (!landscape) assertEquals(410f, stack.bounds.center.y)
            val end = TableFixture.scene(p, landscape, TableLayoutState(TableCamera(zoom = 2f),
                mapOf("dock/a" to dock.maxScroll)))
            assertTrue(end.items.single { it.id == "a/EXILE" }.visible.isNotEmpty())
            assertTrue(scene.items.single { it.id == "a/LIBRARY" }.label.contains("4 cards"))
        }
    }

    @Test fun clippingAndVisiblePeeksDriveHitRectangleAndSemanticsTogether() {
        // A tight stack of four: the first card's strip shows, the third lies wholly under the fourth (TBL-054)
        val p = TableFixture.play(board = 4)
        val scene = TableFixture.scene(p)
        val under = scene.items.single { it.cardId == "a/BATTLEFIELD/0" }
        val top = scene.items.single { it.cardId == "a/BATTLEFIELD/3" }
        assertTrue(under.visible.isNotEmpty())
        under.visible.forEach { b -> assertEquals(under.id, scene.hit(b.center)?.id); assertTrue(under.cardId in scene.rectangle(b)) }
        val insideTop = TableBounds(top.bounds.right - 4, top.bounds.bottom - 4, 2f, 2f)
        assertEquals(setOf(top.cardId), scene.rectangle(insideTop))
        val hidden = scene.items.single { it.cardId == "a/BATTLEFIELD/2" }
        assertTrue(hidden.visible.isEmpty())
        assertFalse(hidden.cardId in scene.rectangle(hidden.bounds))
    }

    @Test fun animatedDrawnBoundsNotDestinationBoundsOwnTargets() {
        val scene = TableFixture.scene(TableFixture.play(board = 1))
        val old = scene.items.single { it.cardId == "a/BATTLEFIELD/0" }
        val shifted = old.copy(bounds = old.bounds.copy(x = old.bounds.x - 120))
        val animated = TablePresentation.clip(scene.copy(items = scene.items.map { if (it.id == old.id) shifted else it }), 412f, 820f)
        assertNotEquals(old.id, animated.hit(old.bounds.center)?.id)
        assertEquals(old.id, animated.hit(shifted.bounds.center)?.id)
        assertTrue(animated.items.single { it.id == old.id }.visible.all { it.x >= shifted.bounds.x })
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
            // STK-007, TBL-009: pinned at the right edge in both orientations
            assertEquals(if (landscape) 900f else 412f, scene.stackRegion!!.right, .001f)
            if (!landscape) assertEquals(410f, stack.bounds.center.y)
            }
    }

    // STK-007
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
                    val scene = TablePresentation.scene(TableFixture.game, tableBoard(play), "a", width, 892f, false,
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
                    assertEquals(28f, stack.bounds.width, .001f)
                    assertEquals(stack.bounds.width + 24f, region.width, .001f)
                    assertEquals(64f, region.height, .001f)
                    assertEquals(region.x + 1.5f, stack.stackBadge.center.x, .001f)
                    assertEquals(region.y + 1.5f, stack.stackBadge.center.y, .001f)
                    assertEquals(CombatStackPresentation.CHIP_ROW * zoom, scene.fields.last().gridBounds.y - scene.fields.first().gridBounds.bottom, .001f)
                    assertEquals(topHand.bottom, scene.world!!.viewport.y, .001f)
                    assertEquals(bottomHand.y, scene.world!!.viewport.bottom, .001f)
                    for (port in scene.viewports.filter { it.id.startsWith("dock/") || it.id.startsWith("counters/") }) {
                        assertNull(port.bounds.intersection(region))
                    }
                    for (item in scene.items.filter { it.kind == TableItemKind.LIFE ||
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
                    }
                }
            }
        }
    }

    // CMB-001, CMB-007 (owner playtest 2026-11-06): the band between the battlefields is always there, collapsed to its
    // chip row (also with a combat area nobody attacks in); once an attacker is in the area it opens by itself, pushing
    // the battlefields apart to hold its lanes
    @Test fun theCombatBandIsAlwaysThereAndOpensWithAnAttacker() {
        val initial = TableFixture.play(board = 1)
        val play = initial.copy(board = initial.board.copy(combat = CombatArea("combat", listOf(
            CombatPair("pair", attackers = listOf(CombatOccurrence("attacker", "a/BATTLEFIELD/0")))))))
        val scene = TableFixture.scene(play)
        val lane = scene.viewports.single { it.id == "combat" }.bounds
        assertEquals(CombatStackPresentation.combat(play.board, scene.combatCardWidth, scene.combatCardHeight, play.definitions,
            mapOf(TableFixture.game.inviter.accountId to TableFixture.game.inviter.username,
                TableFixture.game.recipient.accountId to TableFixture.game.recipient.username)).height, lane.height, .001f)
        assertEquals(scene.combatPanel, lane)
        val band = assertNotNull(scene.combatBand)
        assertTrue(band.y <= lane.y && band.bottom >= lane.bottom)
        val closed = TableFixture.scene(initial)
        val unattacked = TableFixture.scene(initial.copy(board = initial.board.copy(combat = CombatArea("combat"))))
        for (collapsed in listOf(closed, unattacked)) {
            assertNull(collapsed.combatPanel)
            assertTrue(collapsed.combatLabels.isEmpty(), "no chips outside the declaration of attackers")
            assertEquals(CombatStackPresentation.CHIP_ROW * collapsed.camera.zoom,
                collapsed.fields.last().gridBounds.y - collapsed.fields.first().gridBounds.bottom, .001f)
        }
        assertTrue(scene.fields.first().gridBounds.bottom <= lane.y)
        assertTrue(scene.fields.last().gridBounds.y >= lane.bottom)
        val pushed = scene.fields.last().gridBounds.y - scene.fields.first().gridBounds.bottom
        val narrow = closed.fields.last().gridBounds.y - closed.fields.first().gridBounds.bottom
        assertTrue(pushed > lane.height && narrow < lane.height, "the band holds the lanes; collapsed it is the chip row")
        assertEquals(closed.fields.first().gridBounds.height, scene.fields.first().gridBounds.height, .001f)
        assertEquals(closed.stackRegion, scene.stackRegion)
        assertEquals(closed.viewports.filter { it.id.startsWith("dock/") }, scene.viewports.filter { it.id.startsWith("dock/") })
    }

    @Test fun portraitWorldViewportFillsBetweenTheHands() {
        for (review in listOf(false, true)) {
            val scene = TablePresentation.scene(LiveTableFixture.game, tableBoard(LiveTableFixture.play()), "a", 412f, 892f, false, reviewing = review)
            assertEquals(scene.items.single { it.id == "a/HAND" }.bounds.y, scene.world!!.viewport.bottom, .01f)
            assertEquals(scene.items.single { it.id == "b/HAND" }.bounds.bottom, scene.world!!.viewport.y, .01f)
            assertTrue(scene.fields.first().bounds.height > 100f)
        }
    }

    // TBL-109
    @Test fun automaticInspectionUsesCurrentRegionNotOwnerAndNeverGrantsOpponentHiddenSide() {
        val p = TableFixture.play(hand = 1)
        val changed = p.copy(board = p.board.copy(cards = p.board.cards.map { c -> c.copy(owner = if (c.owner == "a") "b" else "a") }))
        assertEquals(TableFixture.definition, TablePresentation.inspection(tableBoard(changed), "a", "a/HAND/0"))
        val hidden = TablePresentation.inspection(tableBoard(changed), "a", "b/HAND/0")!!
        assertEquals("Covered card", hidden.name); assertEquals("", hidden.faces.single().rulesText)
        assertFalse(hidden.toString().contains("Secret")); assertNull(TablePresentation.inspection(tableBoard(changed), "a", "gone"))
        val uncovered = changed.copy(board = changed.board.copy(cards = changed.board.cards.map { it.copy(covered = false) }))
        assertEquals("Covered card", TablePresentation.inspection(tableBoard(uncovered), "a", "b/HAND/0")!!.name)
    }

    // ZON-001, ZON-073
    @Test fun handStripMenuPreviewAndInspectionAgreeForHolderAndOpponentRegardlessOfCoverFlag() {
        for (covered in listOf(false, true)) {
            val p = TableFixture.play(hand = 1).let { it.copy(board = it.board.copy(cards = it.board.cards.map { card ->
                card.copy(covered = covered, owner = if (card.owner == "a") "b" else "a")
            })) }
            val card = p.board.cards.single { it.id == "a/HAND/0" }
            for (viewer in listOf("a", "b")) {
                val scene = TablePresentation.scene(TableFixture.game, tableBoard(p), viewer, 412f, 820f, false)
                val item = scene.items.single { it.cardId == card.id }
                val preview = ManualDisclosure.permittedFaces(p, card, viewer, inspecting = false)
                if (viewer == "a") {
                    assertFalse(item.covered)
                    assertEquals("Secret face", item.value)
                    assertEquals(listOf("Secret face"), preview.map { it.name })
                    assertEquals(TableFixture.definition, TablePresentation.inspection(tableBoard(p), viewer, card.id))
                } else {
                    assertTrue(item.covered)
                    assertFalse(item.label.contains("Secret"))
                    assertTrue(preview.isEmpty())
                    assertEquals("Covered card", TablePresentation.inspection(tableBoard(p), viewer, card.id)!!.name)
                }
            }
        }
    }

    // ZON-073
    @Test fun legacyDefaultCoveredPublicCardsAreVisibleButExplicitFaceDownCardsStayHidden() {
        for (explicit in listOf(false, true)) {
            val p = TableFixture.play(board = 1).let { it.copy(board = it.board.copy(cards = it.board.cards.map { card ->
                card.copy(covered = true, explicitlyCovered = explicit)
            })) }
            for (viewer in listOf("a", "b")) {
                val scene = TablePresentation.scene(TableFixture.game, tableBoard(p), viewer, 412f, 820f, false)
                val card = p.board.cards.single { it.id == "a/BATTLEFIELD/0" }
                assertEquals(explicit, scene.items.single { it.cardId == card.id }.covered)
                assertEquals(explicit, TablePresentation.cardForActions(tableBoard(p), card.id).covered)
                assertEquals(explicit, ManualDisclosure.permittedFaces(p, card, viewer, inspecting = false).isEmpty())
                assertEquals(if (explicit && viewer != "a") "Covered card" else "Secret face",
                    TablePresentation.inspection(tableBoard(p), viewer, card.id)!!.faces.single().name)
            }
        }
    }

    // ZON-012, ZON-073
    @Test fun libraryIsHiddenInOrdinaryPresentationForBothPlayersEvenWhenUncovered() {
        val p = TableFixture.play(library = 1).let { it.copy(board = it.board.copy(cards = it.board.cards.map { card ->
            card.copy(covered = false)
        })) }
        val card = p.board.cards.single { it.id == "a/LIBRARY/0" }
        for (viewer in listOf("a", "b")) {
            val scene = TablePresentation.scene(TableFixture.game, tableBoard(p), viewer, 412f, 820f, false)
            assertTrue(scene.items.single { it.id == "a/LIBRARY" }.covered)
            assertTrue(ManualDisclosure.permittedFaces(p, card, viewer, inspecting = false).isEmpty())
            if (viewer != "a") assertEquals("Covered card", TablePresentation.inspection(tableBoard(p), viewer, card.id)!!.name)
        }
    }

}
