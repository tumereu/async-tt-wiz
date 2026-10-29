package org.asyncttwiz.android

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

    @Test fun cardsPrintedSidewaysTurnAQuarterClockwise() {
        for (definition in listOf(card("split", "Instant"), card("split", "Enchantment — Room", "Enchantment — Room"),
            card("room", "Enchantment — Room"), card("planar", "Plane — Zendikar"), card("planar", "Phenomenon"),
            card("normal", "Plane — Dominaria"), card("normal", "Battle — Control Point"))) {
            assertEquals(InspectionOrientation(90), orientation(definition), definition.toString())
            assertTrue(orientation(definition).sideways)
        }
    }

    @Test fun onlyTheBattleFrontOfATransformingSiegeIsSideways() {
        val siege = card("transform", "Battle — Siege", "Creature — Elemental")
        assertEquals(90, orientation(siege, 0).degrees)
        assertEquals(InspectionOrientation.UPRIGHT, orientation(siege, 1))
    }

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
}
