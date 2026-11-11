package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

/** Product rule: the battlefield feels infinite; panning is limited only by distance from the cards. Within the pannable
 * area no battlefield edge is ever visible, and a row wider than the screen is panned to, never shrunk to fit. */
class InfiniteBattlefieldTest {
    private val sizes = listOf(Triple(412f, 892f, false), Triple(892f, 412f, true))

    private fun empty(play: PlayState): PlayState = play.copy(board = play.board.copy(
        cards = play.board.cards.filter { card -> play.board.containers.none { it.zone == PlayZone.BATTLEFIELD && card.id in it.cards } },
        containers = play.board.containers.map { if (it.zone == PlayZone.BATTLEFIELD) it.copy(cards = emptyList()) else it }))

    /** Thirty attackers: wider than either screen, so the world extends left of the rows' left end. */
    private fun wideCombat(play: PlayState): PlayState {
        val sources = play.board.containers.filter { it.zone == PlayZone.BATTLEFIELD }.flatMap { it.cards }
        return play.copy(board = play.board.copy(combat = CombatArea("combat", (0 until 30).map { n ->
            CombatPair("pair$n", attackers = listOf(CombatOccurrence("attacker$n", sources[n % sources.size])))
        })))
    }

    /** [count] more creatures of Alice's, each different (their own P/T), so each is a group of its own. */
    private fun wideRow(play: PlayState, count: Int): PlayState {
        val sentinel = play.definitions.first { it.definition.name == "Meadow Sentinel" }
        val extra = (0 until count).map { n -> PlayCard("a/BATTLEFIELD/x$n", sentinel.digest, "a", "a/BATTLEFIELD",
            sentinel.definition.faces.single().faceId, covered = false, corner = "$n/$n") }
        return play.copy(board = play.board.copy(cards = play.board.cards + extra, containers = play.board.containers.map {
            if (it.id == "a/BATTLEFIELD") it.copy(cards = it.cards + extra.map(PlayCard::id)) else it }))
    }

    private val cases: Map<String, PlayState> get() {
        val base = LiveTableFixture.play()
        return mapOf("default" to base, "empty board" to empty(base), "wide combat area" to wideCombat(base),
            "a row wider than the screen" to wideRow(base, 61))
    }

    private fun scene(play: PlayState, width: Float, height: Float, landscape: Boolean, camera: TableCamera = TableCamera()) =
        TablePresentation.scene(LiveTableFixture.game, tableBoard(play), "a", width, height, landscape, TableLayoutState(camera))

    // SYS-041, TBL-005
    @Test fun battlefieldFeelsInfiniteFieldsCoverTheWholePanAreaOnBothSides() {
        // Rule: battlefield feels infinite; panning is limited only by distance from the cards.
        for ((name, play) in cases) for ((width, height, landscape) in sizes) {
            val home = scene(play, width, height, landscape)
            val world = assertNotNull(home.world)
            if (name == "wide combat area") assertTrue(world.bounds.x < 0f, "combat must widen the world left of the rows")
            val cameras = listOf(TableCamera(), world.clamp(TableCamera(x = -1e5f)), world.clamp(TableCamera(x = 1e5f)),
                world.resist(TableCamera(x = -1e5f)), world.resist(TableCamera(x = 1e5f)),
                world.clamp(TableCamera(zoom = TablePresentation.MIN_ZOOM)), world.clamp(TableCamera(x = -1e5f, zoom = TablePresentation.MAX_ZOOM)))
            for (camera in cameras) {
                val scene = scene(play, width, height, landscape, camera)
                val pan = scene.world!!.screen(scene.world!!.panBounds, scene.camera)
                val viewport = scene.world!!.viewport
                for (field in scene.fields) {
                    val label = "$name, landscape=$landscape, $camera, ${field.owner}"
                    assertTrue(field.gridBounds.x <= pan.x + .001f && field.gridBounds.right >= pan.right - .001f, label)
                    assertTrue(field.gridBounds.x <= viewport.x + .001f && field.gridBounds.right >= viewport.right - .001f, label)
                    // Drop areas, roll chips and location pings use the visible field: always the full viewport width.
                    if (field.bounds.height > 0f) {
                        assertEquals(viewport.x, field.bounds.x, .001f, label)
                        assertEquals(viewport.width, field.bounds.width, .001f, label)
                    }
                    // The rows' left end is still the world origin under every camera.
                    assertEquals(scene.world!!.screen(TablePoint(0f, 0f), scene.camera).x, field.originX, .001f, label)
                }
            }
        }
    }

    // SYS-041, TBL-013, TBL-004: a row wider than the screen keeps every card at its size and the world reaches 3 slots
    // past its last card; the rows' left end stays where it is
    @Test fun battlefieldFeelsInfinitePanBoundsFollowTheCardsPlusMargin() {
        val base = LiveTableFixture.play()
        for ((width, height, landscape) in sizes) {
            val pitch = TablePresentation.COLUMN_PITCH
            val plain = scene(base, width, height, landscape)
            val wide = scene(wideRow(base, 61), width, height, landscape)
            assertEquals(-3 * pitch, wide.world!!.panBounds.x, .001f)
            // Three creatures and 61 more, each in a slot of its own, from the left in two-slot columns (TBL-004)
            assertEquals(32 * pitch + 32f + 3 * pitch, wide.world!!.panBounds.right, .001f)
            assertTrue(wide.world!!.panBounds.right > plain.world!!.panBounds.right)
            val last = wide.items.single { it.cardId == "a/BATTLEFIELD/x60" }
            assertEquals(plain.items.single { it.cardId == "a/BATTLEFIELD/2" }.bounds.width, last.bounds.width, "nothing shrinks to fit")
        }
    }
}
