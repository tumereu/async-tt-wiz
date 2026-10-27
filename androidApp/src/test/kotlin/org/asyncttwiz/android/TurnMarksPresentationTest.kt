package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TurnMarksPresentationTest {
    private val card = PlayCard("card", "definition", "owner", "field", "face", corner = "1/1", eotCorner = "+2/+2")

    @Test fun effectivePowerToughnessAndInspectKeepDeltaSeparateFromPermanentValue() {
        assertEquals("3/3", effectiveCorner(card))
        assertEquals("3/3 (+2/+2 until end of turn)", inspectedCorner(card))
        assertEquals("0/1", effectiveCorner(card.copy(eotCorner = "-1/-0")))
        assertEquals("*/3", effectiveCorner(card.copy(corner = "*/1")))
        assertEquals("1/1", inspectedCorner(card.copy(eotCorner = "")))
        assertNotEquals(ChangedPowerToughnessColor, EotPowerToughnessColor)
    }

    @Test fun damageFillIsOnlyANumericEffectiveToughnessHint() {
        assertFalse(damageIsLethal("2", effectiveCorner(card)))
        assertTrue(damageIsLethal("3", effectiveCorner(card)))
        assertTrue(damageIsLethal("4", effectiveCorner(card)))
        assertFalse(damageIsLethal("3", "2/*"))
        assertFalse(damageIsLethal("", "2/3"))
        assertFalse(damageIsLethal("x", "2/3"))
        assertTrue(damageIsLethal("9999999999999999999999", "2/9999999999999999999998"))
    }

    @Test fun eotAndPermanentNotesHaveSeparateChipsAndEditorsValidateMarks() {
        val layout = cardAnnotationLayout(9f, 29f, 9f, 10f, true, 6, hasEotNote = true)
        assertNotNull(layout.note); assertNotNull(layout.eotNote)
        assertNull(layout.note.intersection(layout.eotNote))
        val scope = ManualScope("game", "play", "card", listOf("card"))
        assertTrue(ValueDraft(scope, ManualValue(ManualValueKind.EOT_CORNER), "+3/+3").valid)
        assertFalse(ValueDraft(scope, ManualValue(ManualValueKind.EOT_CORNER), "3").valid)
        assertFalse(ValueDraft(scope, ManualValue(ManualValueKind.DAMAGE), "oops").valid)
        assertTrue(ValueDraft(scope, ManualValue(ManualValueKind.EOT_NOTE), "can't block\nthis turn").valid)
        assertTrue(ManualValue(ManualValueKind.EOT_NOTE).multiline)
    }

    @Test fun tablePresentationCarriesEffectiveValuesAndDoesNotLeakCoveredMarks() {
        val f = TableStatesFixture
        val initial = f.combat()
        val source = f.source(f.a, "1/3")
        val state = initial.copy(board = initial.board.copy(cards = initial.board.cards.map {
            if (it.id == source.id) it.copy(damage = "2", eotCorner = "+2/+2", eotNote = "can't block") else it
        }))
        val item = TablePresentation.scene(f.game, state, f.a, 412f, 892f, false).items.single { it.cardId == source.id }
        assertEquals("3/5", item.corner); assertEquals("2", item.damage); assertEquals("+2/+2", item.eotCorner)
        assertEquals("can't block", item.eotNote)
        val covered = state.copy(board = state.board.copy(cards = state.board.cards.map {
            if (it.id == source.id) it.copy(covered = true, explicitlyCovered = true) else it
        }))
        val hidden = TablePresentation.scene(f.game, covered, f.a, 412f, 892f, false).items.single { it.cardId == source.id }
        assertEquals("", hidden.damage); assertEquals("", hidden.eotCorner); assertEquals("", hidden.eotNote)
    }
}
