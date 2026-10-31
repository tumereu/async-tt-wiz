package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.math.floor
import kotlin.test.*

/** Product rule: the battlefield feels infinite; panning is limited only by distance from placed cards.
 * Within the pannable area no battlefield edge is ever visible and drags may place cards anywhere,
 * including negative columns. Automatic placement still starts at column 0. */
class InfiniteBattlefieldTest {
    private val sizes = listOf(Triple(412f, 892f, false), Triple(892f, 412f, true))

    private fun moved(play: PlayState, column: (Int, BoardPosition) -> Int?): PlayState = play.copy(board = play.board.copy(
        cards = play.board.cards.map { card ->
            val p = card.placement ?: return@map card
            val n = card.id.substringAfterLast('/').toIntOrNull() ?: return@map card
            column(n, p.position)?.let { card.copy(placement = p.copy(position = p.position.copy(x = it))) } ?: card
        }))

    private fun empty(play: PlayState): PlayState = play.copy(board = play.board.copy(
        cards = play.board.cards.filter { it.placement == null },
        containers = play.board.containers.map { if (it.zone == PlayZone.BATTLEFIELD) it.copy(cards = emptyList()) else it }))

    /** Thirty attackers: wider than either screen, so the world extends left of column 0. */
    private fun wideCombat(play: PlayState): PlayState {
        val sources = play.board.cards.filter { it.placement != null }.map { it.id }
        return play.copy(board = play.board.copy(combat = CombatArea("combat", (0 until 30).map { n ->
            CombatPair("pair$n", attackers = listOf(CombatOccurrence("attacker$n", sources[n % sources.size])))
        })))
    }

    private val cases: Map<String, PlayState> get() {
        val base = LiveTableFixture.play()
        return mapOf("default" to base, "empty board" to empty(base), "wide combat area" to wideCombat(base),
            "cards at negative columns" to moved(base) { n, p -> if (n == 0) -6 else p.x - 2 },
            "cards far right" to moved(base) { n, _ -> if (n == 4) 30 else null })
    }

    private fun scene(play: PlayState, width: Float, height: Float, landscape: Boolean, camera: TableCamera = TableCamera()) =
        TablePresentation.scene(LiveTableFixture.game, play, "a", width, height, landscape, TableLayoutState(camera))

    // SYS-041, TBL-005
    @Test fun battlefieldFeelsInfiniteFieldsCoverTheWholePanAreaOnBothSides() {
        // Rule: battlefield feels infinite; panning is limited only by distance from placed cards.
        for ((name, play) in cases) for ((width, height, landscape) in sizes) {
            val home = scene(play, width, height, landscape)
            val world = assertNotNull(home.world)
            if (name == "wide combat area") assertTrue(world.bounds.x < 0f, "combat must widen the world left of column 0")
            val cameras = listOf(TableCamera(), world.clamp(TableCamera(x = -1e5f)), world.clamp(TableCamera(x = 1e5f)),
                world.resist(TableCamera(x = -1e5f)), world.resist(TableCamera(x = 1e5f)),
                world.clamp(TableCamera(zoom = TablePresentation.MIN_ZOOM)), world.clamp(TableCamera(x = -1e5f, zoom = TablePresentation.MAX_ZOOM)))
            for (camera in cameras) {
                val scene = scene(play, width, height, landscape, camera)
                val pan = scene.world!!.screen(scene.world.panBounds, scene.camera)
                val viewport = scene.world.viewport
                for (field in scene.fields) {
                    val label = "$name, landscape=$landscape, $camera, ${field.owner}"
                    assertTrue(field.gridBounds.x <= pan.x + .001f && field.gridBounds.right >= pan.right - .001f, label)
                    assertTrue(field.gridBounds.x <= viewport.x + .001f && field.gridBounds.right >= viewport.right - .001f, label)
                    // Drop areas, roll chips and location pings use the visible field: always the full viewport width.
                    if (field.bounds.height > 0f) {
                        assertEquals(viewport.x, field.bounds.x, .001f, label)
                        assertEquals(viewport.width, field.bounds.width, .001f, label)
                    }
                    // Column 0 is still the world origin under every camera.
                    assertEquals(scene.world.screen(TablePoint(0f, 0f), scene.camera).x, field.originX, .001f, label)
                }
            }
        }
    }

    // SYS-041, TBL-013
    @Test fun battlefieldFeelsInfinitePanBoundsFollowPlacedCardsPlusMargin() {
        // Rule: battlefield feels infinite; panning is limited only by distance from placed cards.
        val base = LiveTableFixture.play()
        for ((width, height, landscape) in sizes) {
            val pitch = TablePresentation.COLUMN_PITCH
            val left = scene(moved(base) { n, _ -> if (n == 0) -6 else null }, width, height, landscape).world!!
            assertEquals(-6 * pitch - 3 * pitch, left.panBounds.x, .001f)
            assertEquals(-6 * pitch, left.bounds.x, .001f, "Fit table includes cards at negative columns")
            val right = scene(moved(base) { n, _ -> if (n == 4) 30 else null }, width, height, landscape).world!!
            assertEquals(31 * pitch + 32f + 3 * pitch, right.panBounds.right, .001f)
            assertTrue(right.panBounds.right > left.panBounds.right)
        }
    }

    // SYS-041, TBL-006
    @Test fun battlefieldFeelsInfiniteDragsLandAtNegativeColumnsAndShowThere() {
        // Rule: battlefield feels infinite; cards may be dragged anywhere in the pan area, including negative columns.
        val play = moved(LiveTableFixture.play()) { n, _ -> if (n == 0) -6 else null }
        for ((width, height, landscape) in sizes) {
            val world = scene(play, width, height, landscape).world!!
            var camera = world.clamp(TableCamera(x = -1e5f))
            var scene = scene(play, width, height, landscape, camera)
            fun target(s: TableScene): TablePoint {
                val field = s.fields.single { it.owner == "a" }
                return TablePoint(field.originX + -2.5f * s.columnPitch, field.gridBounds.bottom - .5f * field.rowHeight)
            }
            // Bring the own main row (row 0, also holding the card at column -6) to the middle of the view.
            camera = world.clamp(camera.copy(y = camera.y + (target(scene).y - scene.world!!.viewport.center.y) / camera.zoom))
            scene = scene(play, width, height, landscape, camera)
            val end = target(scene)
            val field = scene.fields.single { it.owner == "a" }
            assertTrue(scene.worldInput(end), "landscape=$landscape $end")
            assertTrue(end.x < field.originX)
            // The card left at column -6 is drawn, not clipped away by a field that starts at column 0.
            val leftCard = scene.items.single { it.cardId == "a/BATTLEFIELD/0" }
            assertTrue(leftCard.visible.isNotEmpty(), "landscape=$landscape ${leftCard.bounds} in ${scene.world!!.viewport}, field ${field.gridBounds} origin ${field.originX}, camera ${scene.camera}")

            val events = mutableListOf<TableIntent>()
            val gesture = TableGestureCoordinator(events::add)
            gesture.update(scene, TableSelection(), true)
            val start = scene.items.single { it.cardId == "a/HAND/2" }.visible.first().center
            gesture.down(start, 0); gesture.move(end, 80)
            val landing = tableDragLanding(scene, gesture.preview)
            assertTrue(landing.outlines.isNotEmpty(), "the landing preview shows at a negative column")
            gesture.up(end, 100)
            val destination = assertIs<TableDestination.Grid>(events.filterIsInstance<TableIntent.Move>().single().destination)
            assertEquals(-3, destination.column)
            assertEquals(0, destination.row)
            assertEquals(field.containerId, destination.containerId)
            val ping = assertNotNull(TableAnnotationGeometry(scene, "a").location(end)).position!!
            assertEquals(floor((end.x - field.originX) / scene.footprint).toInt(), ping.x)
            assertTrue(ping.x < 0)
        }
    }

    // SYS-041, TBL-006
    @Test fun automaticPlacementStillUsesTheFirstFreeColumnFromZero() {
        val play = moved(LiveTableFixture.play()) { n, _ -> if (n == 0) -6 else null }
        val scene = scene(play, 412f, 892f, false)
        val land = scene.items.single { it.cardId == "a/HAND/0" }.copy(frontTypeLine = "Basic Land — Forest")
        val destination = handDoubleTapDestination(scene, land)
        // Row 0 holds column 1 (and column -6); column 0 is free again.
        assertEquals(TableDestination.Grid("a/BATTLEFIELD", 0, 0), destination)
    }
}
