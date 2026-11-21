package org.asyncttwiz.android

import kotlin.test.Test
import kotlin.test.assertEquals

/** TBL-233, TBL-235 (owner, 2026-11-21): which type icons and subtype tags a battlefield card draws. */
class CardTypeMarksTest {
    // TBL-233: one icon per type, in type-line order, as many as fit; the creature icon goes first, then the last ones
    @Test fun typeIconsFitTheirRoom() {
        val types = listOf("Artifact", "Creature")
        assertEquals(types, CardTypeIcons.fit(types, room = 12f, step = 6f))
        assertEquals(listOf("Artifact"), CardTypeIcons.fit(types, room = 6f, step = 6f), "the P/T says it is a creature")
        assertEquals(listOf("Creature"), CardTypeIcons.fit(listOf("Creature"), room = 6f, step = 6f))
        assertEquals(emptyList(), CardTypeIcons.fit(listOf("Creature"), room = 5f, step = 6f))
        assertEquals(listOf("Enchantment"), CardTypeIcons.fit(listOf("Enchantment", "Land"), room = 6f, step = 6f))
        assertEquals(listOf("Land"), CardTypeIcons.fit(listOf("Kindred", "Land"), room = 12f, step = 6f), "kindred has no icon")
    }

    // TBL-233: every type a permanent can have but kindred has its icon (drawn in the screenshot tests)
    @Test fun everyPermanentTypeHasAnIcon() {
        assertEquals(setOf("Artifact", "Battle", "Creature", "Enchantment", "Land", "Planeswalker"), CardTypeIcons.drawn)
    }

    // TBL-235: at most two tags; with more, the first and "+N"; with room for one only, the first with the count
    @Test fun atMostTwoTagsShow() {
        assertEquals(emptyList(), SubtypeTags.shown(emptyList()))
        assertEquals(listOf("Lizard", "Mount"), SubtypeTags.shown(listOf("Lizard", "Mount")))
        assertEquals(listOf("Lizard", "+2"), SubtypeTags.shown(listOf("Lizard", "Mount", "Vehicle")))
        assertEquals(listOf("Lizard", "+2"), SubtypeTags.shown(listOf("Lizard", "Mount", "Vehicle"), rows = 3))
        assertEquals(listOf("Lizard +1"), SubtypeTags.shown(listOf("Lizard", "Mount"), rows = 1))
        assertEquals(listOf("Lizard"), SubtypeTags.shown(listOf("Lizard"), rows = 1))
        assertEquals(emptyList(), SubtypeTags.shown(listOf("Lizard"), rows = 0))
    }
}
