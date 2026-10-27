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
        // Rules v23: a face-down Battlefield card's marks are public; only its face is covered.
        val faceDown = TablePresentation.scene(f.game, covered, f.a, 412f, 892f, false).items.single { it.cardId == source.id }
        assertTrue(faceDown.covered)
        assertEquals("2", faceDown.damage); assertEquals("+2/+2", faceDown.eotCorner); assertEquals("can't block", faceDown.eotNote)
        // A card whose face is truly hidden (here: in the opponent's Hand) still leaks no marks.
        val stack = covered.board.containers.single { it.zone == PlayZone.HAND && it.accountId != f.a }
        val onStack = covered.copy(board = covered.board.copy(cards = covered.board.cards.map {
            if (it.id == source.id) it.copy(containerId = stack.id, placement = null) else it },
            containers = covered.board.containers.map { when {
                it.id == stack.id -> it.copy(cards = it.cards + source.id)
                source.id in it.cards -> it.copy(cards = it.cards - source.id)
                else -> it } }))
        assertTrue(cardValuesHidden(onStack.board, onStack.board.cards.single { it.id == source.id }, f.a))
        val hidden = TablePresentation.scene(f.game, onStack, f.a, 412f, 892f, false).items.single { it.cardId == source.id }
        assertEquals("", hidden.damage); assertEquals("", hidden.eotCorner); assertEquals("", hidden.eotNote)
    }
}
