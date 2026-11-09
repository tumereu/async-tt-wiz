package org.asyncttwiz.android

import arcanum.api.NumericProperty
import arcanum.api.Total
import arcanum.api.Value
import arcanum.engine.decision.Decision
import arcanum.engine.decision.DecisionId
import arcanum.engine.state.PlayerId
import org.junit.Test
import kotlin.test.assertEquals

/** STK-061: the hint of a choice up to a total says the total; "at least 1" would read as if one card were enough. */
class ObjectsShapeTotalTest {
    private fun hint(total: Total?) = ObjectsShape.of(Decision.ChooseObjects(DecisionId("d"), PlayerId("a"), null, emptyList(), 1, null, "Exile", total)).instruction

    // STK-061, CR 701.59a
    @Test fun aManaValueTotalIsRead() =
        assertEquals("Tap cards with total mana value 8 or more, then OK.", hint(Total(NumericProperty.ManaValue, Value.Fixed(8))))

    // CR 702.122a: crew's power, counted by its own rule, reads as power
    @Test fun aPowerTotalIsRead() {
        assertEquals("Tap cards with total power 3 or more, then OK.", hint(Total(NumericProperty.Power, Value.Fixed(3))))
        assertEquals("Tap cards with total power 3 or more, then OK.", hint(Total(NumericProperty.PowerFor(arcanum.api.Keyword.Crew.Kind), Value.Fixed(3))))
    }

    @Test fun withoutATotalItCountsAsBefore() = assertEquals("Tap at least 1 of the cards, then OK.", hint(null))
}
