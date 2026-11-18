package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.*
import kotlin.test.Test



class QuickActionsTest {
    @Test fun tapHoldTooltipAndCancellationAreMutuallyExclusive() {
        val tap = ActionPress(true)
        assertEquals(PressAction.TAP, tap.release())
        assertNull(tap.release())
        val hold = ActionPress(true)
        assertEquals(PressAction.HOLD, hold.hold())
        assertNull(hold.release()) // no Next after End
        assertNull(hold.hold())
        val tooltip = ActionPress(false)
        assertEquals(PressAction.TOOLTIP, tooltip.hold())
        assertNull(tooltip.release())
        val cancelled = ActionPress(true)
        cancelled.cancel()
        assertNull(cancelled.hold())
        assertNull(cancelled.release())
    }

}
