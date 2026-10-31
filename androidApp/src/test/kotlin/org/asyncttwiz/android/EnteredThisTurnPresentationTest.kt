package org.asyncttwiz.android

import androidx.compose.ui.graphics.toArgb
import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class EnteredThisTurnPresentationTest {
    private val f = TableStatesFixture

    private fun withEntered(state: PlayState, cardId: String, value: String, covered: Boolean = false) =
        state.copy(board = state.board.copy(cards = state.board.cards.map {
            if (it.id == cardId) it.copy(enteredTurn = value, covered = covered || it.covered, explicitlyCovered = covered) else it
        }))

    private fun items(state: PlayState, cardId: String) =
        TablePresentation.scene(f.game, state, f.a, 412f, 892f, false).items.filter { it.kind == TableItemKind.CARD && it.sourceId == cardId }

    // TBL-027
    @Test fun markedBattlefieldCardAndItsCombatCopyHaveABlueTitleAndSayItInTheirDescription() {
        val source = f.source(f.a, "1/3")
        val state = withEntered(f.combat(), source.id, f.combat().board.turn.number)
        val drawn = items(state, source.id)
        assertEquals(setOf("card/${source.id}", "card/${f.id("attacker1")}"), drawn.map { it.id }.toSet())
        for (item in drawn) {
            assertTrue(item.enteredThisTurn, item.id)
            assertEquals(EnteredThisTurnTitleColor, item.titleColor)
            assertTrue(item.label.endsWith(", entered this turn"), item.label)
        }
    }

    // TBL-027
    @Test fun earlierTurnAndUnsetCardsKeepTheDefaultTitle() {
        val source = f.source(f.a, "1/3")
        val turn = f.combat().board.turn.number
        for (state in listOf(f.combat(), withEntered(f.combat(), source.id, "${turn.toInt() + 5}"),
                withEntered(f.combat(), source.id, "${turn.toInt() + 5}", covered = true))) {
            val item = items(state, source.id).single { it.id == "card/${source.id}" }
            assertFalse(item.enteredThisTurn)
            assertEquals(android.graphics.Color.DKGRAY, item.titleColor)
            assertFalse(item.label.contains("entered this turn"), item.label)
        }
    }

    // TBL-028
    @Test fun faceDownBattlefieldCardShowsAQuestionMarkTitleThatIsBlueWhenItEnteredThisTurn() {
        val source = f.source(f.a, "1/3")
        val turn = f.combat().board.turn.number
        fun item(state: PlayState, viewer: String) = TablePresentation.scene(f.game, state, viewer, 412f, 892f, false)
            .items.single { it.id == "card/${source.id}" }
        for (viewer in listOf(f.a, f.b)) {
            val fresh = item(withEntered(f.combat(), source.id, turn, covered = true), viewer)
            assertTrue(fresh.covered)
            assertEquals("", fresh.value)
            assertEquals(FACE_DOWN_TITLE, fresh.coveredTitle)
            assertTrue(fresh.enteredThisTurn)
            assertEquals(EnteredThisTurnTitleColor, fresh.titleColor)
            assertTrue(fresh.label.contains("entered this turn"), fresh.label)
            val old = item(withEntered(f.combat(), source.id, "", covered = true), viewer)
            assertEquals(FACE_DOWN_TITLE, old.coveredTitle)
            assertFalse(old.enteredThisTurn)
            assertEquals(android.graphics.Color.DKGRAY, old.titleColor)
            // A face-up card keeps its own title.
            assertEquals("", item(f.combat(), viewer).coveredTitle)
        }
    }

    @Test fun blueIsDistinctFromTableAndUntilEndOfTurnColours() {
        val blue = EnteredThisTurnTitleColor
        assertNotEquals(OwnTableColor.toArgb(), blue)
        assertNotEquals(OpponentTableColor.toArgb(), blue)
        assertNotEquals(EotPowerToughnessColor, blue)
        assertNotEquals(ChangedPowerToughnessColor, blue)
        // Blue dominates and the title stays readable on a white card frame (contrast >= 4.5).
        val r = blue shr 16 and 0xff; val g = blue shr 8 and 0xff; val b = blue and 0xff
        assertTrue(b > r + 100 && b > g + 80)
        fun linear(c: Int) = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        val luminance = 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
        assertTrue(1.05 / (luminance + 0.05) >= 4.5)
    }
}
