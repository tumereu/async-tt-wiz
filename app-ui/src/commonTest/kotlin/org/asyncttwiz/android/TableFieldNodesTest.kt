package org.asyncttwiz.android

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Found 2026-11-24 by the desktop battlefield worker: which battlefields have a semantics node is worked out from the scene as
 * laid out with the camera ([TableFieldNode.shown]), so the nodes follow a pan, a zoom and Fit table, which only redraw the
 * table; and it stays equal while the camera keeps the same battlefields in view, so those steps compose nothing again.
 */
class TableFieldNodesTest {
    private val play = TableFixture.play(board = 3, hand = 2)

    private fun nodes(landscape: Boolean, camera: TableCamera) =
        TableFieldNode.shown(TableFixture.scene(play, landscape, TableLayoutState(camera = camera)).fields)

    // TBL-010, TBL-011: a node for each battlefield in view, both as the table opens; equal for a camera step that keeps both in
    // view (nothing composes again for it)
    @Test fun aStepKeepingBothBattlefieldsInViewChangesNoNode() {
        for (landscape in listOf(false, true)) {
            val opened = nodes(landscape, TableCamera())
            assertEquals(listOf("b", "a"), opened.map { it.owner }, "landscape=$landscape: both battlefields as the table opens")
            assertEquals(opened, nodes(landscape, TableCamera(12f, 0f, 1.1f)), "landscape=$landscape: a small step keeps both, unchanged")
        }
    }

    // TBL-010, TBL-011, TBL-012: in landscape, zoomed in to the most and panned down as far as the table goes, Bob's battlefield
    // is out of view and has no node; Fit table brings both back (in portrait the battlefields always both stay in view)
    @Test fun aBattlefieldOutOfViewHasNoNodeUntilItIsBack() {
        val world = requireNotNull(TableFixture.scene(play, true).world)
        val away = world.clamp(TableCamera(0f, 2_000f, TablePresentation.MAX_ZOOM))
        val panned = nodes(true, away)
        assertEquals(listOf("a"), panned.map { it.owner }, "only Alice's battlefield in view at $away: $panned")
        assertEquals(listOf("b", "a"), nodes(true, world.fit()).map { it.owner }, "both again after Fit table")
    }
}
