package org.asyncttwiz.android

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.DefinitionFace
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InspectionOrientationTest {
    private fun card(layout: String, vararg types: String) = CardDefinition("Card", types.mapIndexed { index, type ->
        DefinitionFace("f$index", "Face $index", typeLine = type) }, layout = layout)

    private fun orientation(definition: CardDefinition, face: Int = 0) = InspectionOrientation.of(definition, definition.faces[face])

    // TBL-108
    @Test fun cardsPrintedSidewaysTurnAQuarterClockwise() {
        for (definition in listOf(card("split", "Instant"), card("split", "Enchantment — Room", "Enchantment — Room"),
            card("room", "Enchantment — Room"), card("planar", "Plane — Zendikar"), card("planar", "Phenomenon"),
            card("normal", "Plane — Dominaria"), card("normal", "Battle — Control Point"))) {
            assertEquals(InspectionOrientation(90), orientation(definition), definition.toString())
            assertTrue(orientation(definition).sideways)
        }
    }

    // TBL-108
    @Test fun onlyTheBattleFrontOfATransformingSiegeIsSideways() {
        val siege = card("transform", "Battle — Siege", "Creature — Elemental")
        assertEquals(90, orientation(siege, 0).degrees)
        assertEquals(InspectionOrientation.UPRIGHT, orientation(siege, 1))
    }

    // TBL-108
    @Test fun aftermathAndFlipStayUprightWithAPersonalTurn() {
        val aftermath = orientation(card("aftermath", "Instant"))
        assertEquals(0, aftermath.shown(turned = false))
        assertEquals(270, aftermath.shown(turned = true))
        val flip = orientation(card("flip", "Creature — Human Soldier", "Legendary Creature — Human Samurai"))
        assertEquals(0, flip.shown(false))
        assertEquals(180, flip.shown(true))
        assertFalse(InspectionOrientation(0, 180).sideways)
        assertTrue(InspectionOrientation(0, 270).let { it.shown(true) % 180 != 0 })
    }

    @Test fun ordinaryCardsAndLookalikeTypeWordsStayUpright() {
        for (definition in listOf(card("normal", "Creature — Human Battlemage"), card("normal", "Legendary Planeswalker — Jace"),
            card("transform", "Creature — Human", "Creature — Werewolf"), card("adventure", "Creature — Giant", "Instant — Adventure"),
            card("modal_dfc", "Sorcery", "Land"), card("saga", "Enchantment — Saga"), card("normal", "Creature — Planewalker Battle"))) {
            definition.faces.indices.forEach { face ->
                val shown = orientation(definition, face)
                assertEquals(InspectionOrientation.UPRIGHT, shown, definition.toString())
                assertNull(shown.alternate)
            }
        }
    }

    @Test fun controllerTurnIsPersonalAndResetsOnFaceChange() {
        val definition = card("flip", "Creature — Human Soldier", "Legendary Creature — Human Samurai")
        val controller = CardInspectionController()
        controller.open(InspectedCard(definition, listOf(null, null)))
        assertEquals(0, controller.state!!.degrees)
        controller.turn()
        assertEquals(180, controller.state!!.degrees)
        controller.face(1)
        assertEquals(0, controller.state!!.degrees)
        controller.open(InspectedCard(card("split", "Instant"), listOf(null)))
        controller.turn()
        assertEquals(90, controller.state!!.degrees)
    }

    private fun assertSize(width: Float, height: Float, actual: DpSize) {
        assertEquals(width, actual.width.value, .01f, "width of $actual")
        assertEquals(height, actual.height.value, .01f, "height of $actual")
    }

    @Test fun uprightArtworkIsCappedAt320Wide() {
        assertSize(300f, 420f, inspectedArtworkSize(false, 300.dp))
        assertSize(320f, 448f, inspectedArtworkSize(false, 900.dp))
        assertSize(200f, 280f, inspectedArtworkSize(false, 900.dp, 280.dp))
    }

    // TBL-108
    @Test fun sidewaysArtworkFillsTheWidthButIsNeverTallerThanACappedUprightCard() {
        val capped = inspectedArtworkSize(false, 900.dp)
        // Narrow room: the full width.
        assertSize(380f, 380f / 1.4f, inspectedArtworkSize(true, 380.dp))
        // Wide room: the same height as the capped upright card.
        val wide = inspectedArtworkSize(true, 1200.dp)
        assertSize(448f * 1.4f, 448f, wide)
        assertEquals(capped.height, wide.height)
        // Short room: the height limit wins.
        assertSize(280f, 200f, inspectedArtworkSize(true, 1200.dp, 200.dp))
    }
}
