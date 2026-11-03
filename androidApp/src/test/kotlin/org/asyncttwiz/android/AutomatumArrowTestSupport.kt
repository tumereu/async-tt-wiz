package org.asyncttwiz.android

import org.asyncttwiz.automatum.CombatShown
import org.asyncttwiz.protocol.PlayState
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The step arrow with Automatum on, as a test taps it (TRN-063, UI review round 2 #1: the one control that declares and
 * passes): it reads [label] when given and isn't greyed ([stepBlocked]); the tap is Next through [toolkit].
 */
internal fun tapArrow(play: PlayState, toolkit: TableToolkitController, combat: CombatShown?, label: String? = null): Boolean {
    if (label != null) assertEquals(label, AutomatumStep.of(play).label)
    val blocked = stepBlocked(play, toolkit.cast.open, combat)
    assertNull(blocked, "the step arrow is greyed: $blocked")
    return toolkit.perform(TableActionIntent.Next(TableActionScope(play.gameId, play.id)))
}
