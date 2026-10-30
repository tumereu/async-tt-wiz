package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class PodPresetsTest {
    private fun preset(name: String, settings: DraftSettings = DraftSettings(), cubeName: String? = null,
        setCode: String? = null, setName: String? = null, missing: Boolean = false) =
        PodPreset(name, name, settings, if (cubeName != null || missing) "cube" else null, cubeName, missing, setCode, setName, "2026-10-30T00:00:00Z")

    private val presets = listOf(
        preset("Friday cube", cubeName = "Vintage cube"),
        preset("Glimpse night", DraftSettings.glimpse(), setCode = "DMU", setName = "Dominaria United"),
        preset("Grid two", DraftSettings.grid().copy(grids = 12), setCode = "MOM", setName = "March of the Machine"),
        preset("Old", missing = true),
        preset("Bare"),
    )

    @Test fun summariesShowModeSourceAndSize() {
        assertEquals(listOf("Booster · Vintage cube · 3×15", "Glimpse · Dominaria United · 9 packs",
            "Grid · March of the Machine · 12 grids", "Booster · Cube missing · 3×15", "Booster · No source · 3×15"),
            presets.map(::podPresetSummary))
        assertEquals("Booster · DMU · 1 pack", podPresetSummary(preset("x", DraftSettings(packsPerPlayer = 1, packsOpenAtOnce = 1), setCode = "DMU")))
    }

    @Test fun filteringMatchesEveryWordInNameSummaryOrSetCodeIgnoringCase() {
        fun names(query: String) = podPresetFilter(presets, query).map { it.name }
        assertEquals(presets.map { it.name }, names(""))
        assertEquals(presets.map { it.name }, names("   "))
        assertEquals(listOf("Friday cube", "Old"), names("CUBE"))
        assertEquals(listOf("Glimpse night"), names("dmu"))
        assertEquals(listOf("Glimpse night"), names("glimpse dominaria"))
        assertEquals(listOf("Grid two"), names("12 grids"))
        assertEquals(listOf("Old"), names("missing"))
        assertEquals(emptyList(), names("glimpse vintage"))
    }

    @Test fun searchAppearsOnceTheListIsLong() {
        val few = PodPresetState(presets = presets.take(PodPresetSearchThreshold))
        assertFalse(few.searchable)
        assertTrue(few.copy(query = "x").searchable)
        val many = PodPresetState(presets = List(30) { preset("Preset $it") }, query = "preset 2")
        assertTrue(many.searchable)
        // Every word on its own: "2" also matches "Preset 12".
        assertEquals(listOf("Preset 2", "Preset 12") + (20..29).map { "Preset $it" }, many.filtered.map { it.name })
    }
}
