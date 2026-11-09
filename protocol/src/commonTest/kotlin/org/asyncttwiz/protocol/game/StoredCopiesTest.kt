package org.asyncttwiz.protocol.game

import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Duration
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.engine.state.CopiableValues
import arcanum.engine.state.EffectId
import arcanum.engine.state.EffectObject
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItemKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What stored games hold of a copy: the fields for a copy's whole values only add to the format (EngineJson). */
class StoredCopiesTest {
    private val left = FaceDefinition("Lantern Foyer", types = listOf(CardType.Enchantment))
    private val right = FaceDefinition("Quiet Pantry", types = listOf(CardType.Enchantment))
    private val copy = ObjectId.StackItem("copy")

    private fun effect(copied: FaceDefinition? = null, values: CopiableValues? = null) = EffectObject(
        id = EffectId("E1"), mechanics = listOf(Mechanic.CopyOf(Objects.Self)), affects = Affected.Objects(Objects.Self), source = null,
        controller = PlayerId("alice"), timestamp = 1, createdTurn = 1, duration = Duration.Indefinitely, copied = copied, copiedValues = values)

    @Test
    fun aCopyEffectStoredWithOneFaceWritesAndReadsAsBefore() {
        val text = EngineJson.json.encodeToString(EffectObject.serializer(), effect(copied = left))
        assertFalse("copiedValues" in text, text)
        assertEquals(effect(copied = left), EngineJson.json.decodeFromString(EffectObject.serializer(), text))
    }

    @Test
    fun aCopyEffectWithWholeValuesRoundTrips() {
        val whole = effect(values = CopiableValues(Layout.Split, listOf(left, right), up = 1))
        val text = EngineJson.json.encodeToString(EffectObject.serializer(), whole)
        assertTrue("copiedValues" in text, text)
        assertEquals(whole, EngineJson.json.decodeFromString(EffectObject.serializer(), text))
    }

    @Test
    fun theFlippedStatusIsAStatusFlagOfItsOwn() {
        val text = EngineJson.json.encodeToString(arcanum.engine.state.StatusFlag.serializer(), arcanum.engine.state.StatusFlag.Flipped)
        assertEquals<arcanum.engine.state.StatusFlag>(arcanum.engine.state.StatusFlag.Flipped, EngineJson.json.decodeFromString(arcanum.engine.state.StatusFlag.serializer(), text))
    }

    @Test
    fun whatARoomGainedInCopyingRoundTrips() {
        val whole = effect(values = CopiableValues(Layout.Split, listOf(left, right), gained = listOf(arcanum.api.Keyword.Flying)))
        assertEquals(whole, EngineJson.json.decodeFromString(EffectObject.serializer(), EngineJson.json.encodeToString(EffectObject.serializer(), whole)))
    }

    @Test
    fun aSpellCopyWritesItsValuesOnlyWhenItHasThem() {
        val plain = StackItemKind.SpellCopy(copy)
        val plainText = EngineJson.json.encodeToString(StackItemKind.serializer(), plain)
        assertFalse("values" in plainText, plainText)
        assertEquals<StackItemKind>(plain, EngineJson.json.decodeFromString(StackItemKind.serializer(), plainText))
        val whole = StackItemKind.SpellCopy(copy, values = CopiableValues(Layout.Transform, listOf(left, right)))
        assertEquals<StackItemKind>(whole, EngineJson.json.decodeFromString(StackItemKind.serializer(), EngineJson.json.encodeToString(StackItemKind.serializer(), whole)))
    }
}
