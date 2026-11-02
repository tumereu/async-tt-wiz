package arcanum.engine.view

import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Keyword
import arcanum.api.Layer
import arcanum.api.Mechanic
import arcanum.api.Value
import arcanum.engine.Engine
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [Engine.explain]: where an object's characteristics come from (spec §7.1, a host's Inspect). */
class ExplanationTest {
    private fun modify(power: Int, toughness: Int) = Mechanic.ModifyPowerToughness(Value.Fixed(power), Value.Fixed(toughness))

    // CR 613.4b, 613.4c, 613.7: printed values, then a static ability, counters and an effect object in layer 7c in
    // timestamp order
    @Test
    fun powerToughnessFromPrintedAnthemCountersAndEffect() = scenario {
        alice.battlefield("Elvish Hierophant")
        alice.battlefield("Elvish Seer", counters = listOf(CounterKind.PowerToughness(1, 1) to 2))
        val growth = effect(alice, modify(3, 3), on = listOf("Elvish Seer"), duration = Duration.EndOfTurn)
        val seer = card("Elvish Seer")
        val hierophant = card("Elvish Hierophant")
        val explanation = Engine.explain(state, seer)!!
        assertEquals(1, explanation.base.power)
        assertEquals(1, explanation.base.toughness)
        assertEquals(7, explanation.characteristics.power)
        val sources = explanation.steps.map { it.source }
        assertEquals(3, sources.size)
        assertTrue(sources.any { it is EffectSource.StaticAbility && it.obj == hierophant })
        assertTrue(EffectSource.Counters(seer, CounterKind.PowerToughness(1, 1), 2) in sources)
        assertTrue(EffectSource.EffectObject(growth) in sources)
        assertTrue(explanation.steps.all { it.layer == Layer.PowerToughnessModify })
        // Each step's after is the next one's before
        explanation.steps.zipWithNext().forEach { (a, b) -> assertEquals(a.after, b.before) }
        assertEquals(explanation.characteristics.power, explanation.steps.last().after.power)
    }

    // CR 613.1f: a granted keyword shows as a layer 6 step; an effect that changes nothing on the object isn't listed
    @Test
    fun grantedKeywordIsALayerSixStep() = scenario {
        alice.battlefield("Elvish Seer", "Lineage Druid")
        effect(alice, Mechanic.GainAbilities(listOf(Keyword.Flying)), on = listOf("Elvish Seer"))
        val step = Engine.explain(state, card("Elvish Seer"))!!.steps.single()
        assertEquals(Layer.Ability, step.layer)
        assertTrue(step.after.hasKeyword(Keyword.Flying) && !step.before.hasKeyword(Keyword.Flying))
        assertEquals(emptyList(), Engine.explain(state, card("Lineage Druid"))!!.steps)
    }

    @Test
    fun noObjectNoExplanation() = scenario {
        alice.battlefield("Elvish Seer")
        assertNull(Engine.explain(state, arcanum.engine.state.ObjectId.Card(arcanum.engine.state.CardId("nobody"), 0)))
    }
}
