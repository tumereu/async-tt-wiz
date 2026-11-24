package org.asyncttwiz.e2e

import org.junit.Test
import org.robolectric.annotation.Config

/**
 * The table's semantics nodes following its camera on the phone (found 2026-11-24 by the desktop battlefield worker): a pinch,
 * a pan and Fit table only redraw the table, and its nodes (what screen readers and the tests read) still say what shows. In
 * landscape: in portrait both battlefields always stay in view.
 */
@Config(qualifiers = "w892dp-h412dp-land-mdpi")
class TableCameraNodesTest : E2e() {
    // TBL-010, TBL-011, TBL-012: on Alice's phone in landscape, zoomed in and panned until Bob's battlefield leaves the view, its
    // node goes too, and Fit table brings it back at once
    @Test fun theBattlefieldNodesFollowTheCameraOnThePhone() {
        startGame()
        bothOpen()
        battlefieldNodesFollowTheCamera(alice, bob)
    }
}
