package arcanum.engine.query

import arcanum.api.ObjectFilter
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.casting.Snapshot
import arcanum.engine.scenario.scenario
import arcanum.engine.state.Bindings
import arcanum.engine.state.NamedValue
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The names of a split card (CR 709.4a), for effects that name a card. */
class SplitCardNamesTest {
    private val split = Cards.inventFaces("Split",
        face("Mirth", "{1}{R}", "Instant", "You gain 1 life.", null, null, null),
        face("Malice", "{W}", "Instant", "You gain 2 life.", null, null, null),
    )

    // CR 709.4a: "An object has the chosen name if one of its names is the chosen name."
    @Test
    fun aSplitCardHasTheChosenNameIfEitherHalfHasIt() = scenario {
        bob.hand(split)
        val id = card(split)
        fun chosen(name: String) = Snapshot(state).evaluator(alice.id, names = Bindings(mapOf("chosen" to NamedValue.Name(name))))
            .matches(ObjectFilter.ChosenName("chosen"), id)
        assertTrue(chosen("Mirth"))
        assertTrue(chosen("Malice"))
        assertFalse(chosen("Sorrow"))
    }

    // CR 709.4a: the same for a name a permanent remembers
    @Test
    fun aSplitCardHasTheRememberedNameIfEitherHalfHasIt() = scenario {
        val rememberer = Cards.invent("Keeper of Words", "{1}", "Artifact", "")
        alice.battlefield(rememberer)
        bob.hand(split)
        val id = card(split)
        val self = card(rememberer) as arcanum.engine.state.ObjectId.Card
        fun remembered(name: String): Boolean {
            state._objects[self] = state.objOrThrow(self).copy(memory = Bindings(mapOf("word" to NamedValue.Name(name))))
            return Snapshot(state).evaluator(alice.id, self = self).matches(ObjectFilter.RememberedName("word"), id)
        }
        assertTrue(remembered("Mirth"))
        assertTrue(remembered("Malice"))
        assertFalse(remembered("Sorrow"))
    }

    // CR 709.4a: half of a card's one name isn't a name of it
    @Test
    fun aOneNamedCardHasOnlyItsName() = scenario {
        val plain = Cards.invent("Mirthful Malice", "{1}", "Artifact", "")
        bob.hand(plain)
        val id = card(plain)
        fun chosen(name: String) = Snapshot(state).evaluator(alice.id, names = Bindings(mapOf("chosen" to NamedValue.Name(name))))
            .matches(ObjectFilter.ChosenName("chosen"), id)
        assertTrue(chosen("Mirthful Malice"))
        assertFalse(chosen("Mirth"))
    }
}
