package org.asyncttwiz.android

import org.asyncttwiz.android.DesktopFingerKey.Effect
import org.asyncttwiz.protocol.PlayZone
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The desktop table's input mapping (docs/specs/web-client.md §5): the mouse's buttons, the finger key, the left button. */
class DesktopMouseTest {
    // WEB-023, WEB-028: the left button is the finger, the right one a control's hold action, the middle one pans; a chord
    // with the left button down stays a left press
    @Test fun eachMouseButtonHasItsRole() {
        assertEquals(MouseButton.Left, MouseButton.of(primary = true, secondary = false, tertiary = false))
        assertEquals(MouseButton.Right, MouseButton.of(primary = false, secondary = true, tertiary = false))
        assertEquals(MouseButton.Middle, MouseButton.of(primary = false, secondary = false, tertiary = true))
        assertEquals(MouseButton.Left, MouseButton.of(primary = true, secondary = true, tertiary = false))
        assertEquals(null, MouseButton.of(primary = false, secondary = false, tertiary = false))
    }

    // WEB-024 (owner, D4, decision 31; owner playtest 2026-11-15: no pinning by a tap): holding Alt over a card inspects it
    // while held and pauses the view; releasing it, however soon, closes the Inspect and lets the view go on
    @Test fun holdingTheFingerKeyOverACardInspectsItWhileHeld() {
        val key = DesktopFingerKey()
        assertEquals(listOf(Effect.Pause(true), Effect.Inspect("c1")), key.press("Alt", "c1"))
        assertTrue(key.held)
        assertEquals(emptyList(), key.press("Alt", "c1"), "a held key's repeat does nothing")
        assertEquals(listOf(Effect.Pause(false), Effect.Close), key.release("Alt"))
        key.press("Alt", "c1")
        assertEquals(listOf(Effect.Pause(false), Effect.Close), key.release("Alt"), "a quick tap pins nothing")
        assertFalse(key.held)
    }

    // WEB-024: Shift is the fallback finger key; a second finger key while one is held does nothing
    @Test fun shiftIsTheFallbackFingerKey() {
        val key = DesktopFingerKey()
        assertEquals(listOf(Effect.Pause(true), Effect.Inspect("c1")), key.press("Shift", "c1"))
        assertEquals(listOf(Effect.Pause(false), Effect.Close), key.release("Shift"))
        key.press("Alt", "c1")
        assertEquals(emptyList(), key.press("Shift", null), "a second finger key does nothing")
        assertEquals(emptyList(), key.release("Shift"))
        assertEquals(listOf(Effect.Pause(false), Effect.Close), key.release("Alt"))
    }

    // WEB-025 (decision 31): away from cards the finger key only pauses the view while held
    @Test fun theFingerKeyElsewhereOnlyPauses() {
        val key = DesktopFingerKey()
        assertEquals(listOf(Effect.Pause(true)), key.press("Alt", null))
        assertEquals(listOf(Effect.Pause(false)), key.release("Alt"))
    }

    // WEB-025, HIS-021: on the desktop table the left button held still on a card pauses the view but never inspects (§5: no
    // press-and-hold on desktop); the phone's hold still inspects (TBL-104)
    @Test fun theLeftButtonHeldStillPausesWithoutInspecting() {
        val card = TableItem("card/a", TableBounds(0f, 0f, 100f, 100f), "Card", "", kind = TableItemKind.CARD, cardId = "a",
            zone = PlayZone.BATTLEFIELD)
        val events = mutableListOf<TableIntent>()
        val held = mutableListOf<Boolean>()
        val g = TableGestureCoordinator(events::add, onHeld = held::add)
        g.holdInspects = false
        g.update(TableScene(listOf(card), emptyList()), true)
        g.down(card.bounds.center, 0); g.tick(400)
        assertEquals(listOf(true), held)
        assertFalse(g.peeking)
        g.up(card.bounds.center, 900)
        assertEquals(listOf(true, false), held)
        assertTrue(events.none { it is TableIntent.Peek }, "$events")
        // TBL-104: the phone's coordinator inspects as before
        val phone = mutableListOf<TableIntent>()
        val p = TableGestureCoordinator(phone::add)
        p.update(TableScene(listOf(card), emptyList()), true)
        p.down(card.bounds.center, 0); p.tick(400); p.up(card.bounds.center, 900)
        assertEquals(listOf<TableIntent>(TableIntent.Peek("a"), TableIntent.Peek(null, released = true)), phone)
    }

    // WEB-030, HIS-113: a double click on the table during a combat hold (two left clicks, as the phone's double tap) ends
    // the hold, and a single click does nothing more than a tap
    @Test fun aDoubleClickEndsACombatHold() {
        val world = TableWorld(TableBounds(0f, 0f, 300f, 400f), TableBounds(0f, 0f, 800f, 800f))
        val card = TableItem("card/a", TableBounds(20f, 20f, 60f, 80f), "Card", "", kind = TableItemKind.CARD, cardId = "a",
            zone = PlayZone.BATTLEFIELD)
        val g = TableGestureCoordinator({})
        g.holdInspects = false
        var skips = 0
        g.viewHoldSkip = { { skips++ } }
        g.update(TableScene(listOf(card), emptyList(), world = world), true)
        val point = card.bounds.center
        g.down(point, 0); g.up(point, 60)
        assertEquals(0, skips, "one click")
        g.down(point, 200); g.up(point, 260)
        assertEquals(1, skips, "the double click")
    }

    // WEB-029: the wheel zooms around the pointer within 0.65–2×; a notch is a gentle step either way
    @Test fun theWheelZoomsGently() {
        assertTrue(wheelZoom(1f) < 1f && wheelZoom(1f) > .85f, "a notch towards you zooms out a little")
        assertTrue(wheelZoom(-1f) > 1f && wheelZoom(-1f) < 1.15f)
        assertEquals(wheelZoom(4f), wheelZoom(400f), "a page-sized turn is one big step, not a jump")
        val world = TableWorld(TableBounds(0f, 0f, 400f, 400f), TableBounds(0f, 0f, 400f, 400f))
        var camera = TableCamera()
        val g = TableGestureCoordinator({ if (it is TableIntent.Camera) camera = it.camera })
        g.update(TableScene(emptyList(), emptyList(), world = world, camera = camera), true)
        repeat(40) { g.zoomAt(TablePoint(200f, 200f), wheelZoom(-4f)); g.update(TableScene(emptyList(), emptyList(), world = world, camera = camera), true) }
        assertEquals(TablePresentation.MAX_ZOOM, camera.zoom, .001f)
    }
}
