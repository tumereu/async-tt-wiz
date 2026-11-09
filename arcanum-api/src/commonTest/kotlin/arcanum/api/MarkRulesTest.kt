package arcanum.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The table beside [KeywordExpansions]: what the rules say of each mark. */
class MarkRulesTest {
    private val marks: List<ObjectMark> = listOf(ObjectMark.Monstrous, ObjectMark.Renowned, ObjectMark.Suspected, ObjectMark.Saddled, ObjectMark.Solved,
        ObjectMark.Unlocked(Door.Left), ObjectMark.Unlocked(Door.Right), ObjectMark.Manifested, ObjectMark.Prepared, ObjectMark.Harnessed, ObjectMark.Warped,
        ObjectMark.Foretold, ObjectMark.Plotted, ObjectMark.Cloaked, ObjectMark.RingBearer, ObjectMark.Named("tribute paid"))

    // A rule's name is the mark's stored name, and the table holds each rule once
    @Test
    fun aRulesNameIsItsMarksStoredName() {
        marks.forEach { mark ->
            val stored = Json.encodeToJsonElement(ObjectMark.serializer(), mark).jsonObject.getValue("type").jsonPrimitive.content
            assertEquals(stored, MarkRules.of(mark).name)
            assertEquals(MarkRules.of(mark), MarkRules.named(stored))
        }
        assertEquals(MarkRules.all, MarkRules.all.distinct())
        assertEquals(MarkRules.all.toSet(), marks.map(MarkRules::of).toSet(), "the list above has every rule")
    }

    // CR 701.37b, 702.112b, 701.60b, 702.171b, 719.3b, 709.5c, 701.40a, 722.3a, 701.64b, 701.58a, 701.54b: permanents only
    // CR 702.185b, 702.143a, d, 702.170c: cards in exile only
    @Test
    fun whereEachMarkLives() {
        val exile = listOf(ObjectMark.Warped, ObjectMark.Foretold, ObjectMark.Plotted)
        exile.forEach { assertEquals(setOf(Zone.Exile), MarkRules.of(it).zones, "$it") }
        (marks - exile.toSet()).forEach { assertEquals(setOf(Zone.Battlefield), MarkRules.of(it).zones, "$it") }
    }

    // CR 701.60c: only suspected gives abilities: menace and "This creature can't block"
    @Test
    fun whatAMarkGives() {
        val suspected = MarkRules.of(ObjectMark.Suspected)
        assertEquals(listOf(Keyword.Menace, Ability.Static(listOf(Mechanic.CantBlock(ObjectFilter.Any)), affects = Affected.Objects(Objects.Self))), suspected.abilities)
        (marks - ObjectMark.Suspected).forEach { assertTrue(MarkRules.of(it).abilities.isEmpty(), "$it") }
    }

    // CR 702.171b; 701.60a; 701.40a; 701.58a; 722.3b, c; 709.5g; 701.54a; 701.37b, 702.112b, 719.3b, 701.64b: only leaving the battlefield
    @Test
    fun whenEachMarkClears() {
        assertEquals(setOf<MarkClear>(MarkClear.EndOfTurn), MarkRules.of(ObjectMark.Saddled).clears)
        assertEquals(setOf<MarkClear>(MarkClear.ByEffect), MarkRules.of(ObjectMark.Suspected).clears)
        assertEquals(setOf<MarkClear>(MarkClear.ByEffect), MarkRules.of(ObjectMark.Unlocked(Door.Left)).clears)
        assertEquals(setOf<MarkClear>(MarkClear.TurnedFaceUp), MarkRules.of(ObjectMark.Manifested).clears)
        assertEquals(setOf<MarkClear>(MarkClear.TurnedFaceUp), MarkRules.of(ObjectMark.Cloaked).clears)
        assertEquals(setOf(MarkClear.ByEffect, MarkClear.ItsCopyCast), MarkRules.of(ObjectMark.Prepared).clears)
        assertEquals(setOf(MarkClear.ReplacedByAnother, MarkClear.ControlChanges), MarkRules.of(ObjectMark.RingBearer).clears)
        listOf(ObjectMark.Monstrous, ObjectMark.Renowned, ObjectMark.Solved, ObjectMark.Harnessed).forEach {
            assertTrue(MarkRules.of(it).clears.isEmpty(), "$it")
        }
    }

    // CR 702.143a: a foretell cost; 702.170d: free, as a sorcery; 722.3c: the copy; 702.185a, b: warped gives none, the warp ability does
    @Test
    fun whatEachMarkLetsBeCast() {
        assertEquals(MarkPermission.CastCard(MarkCost.OfKeyword(Keyword.Foretell.Kind)), MarkRules.of(ObjectMark.Foretold).permission)
        assertEquals(MarkPermission.CastCard(MarkCost.Free, Timing.Sorcery), MarkRules.of(ObjectMark.Plotted).permission)
        assertEquals(MarkPermission.CastCopy, MarkRules.of(ObjectMark.Prepared).permission)
        assertNull(MarkRules.of(ObjectMark.Warped).permission)
        (marks - listOf(ObjectMark.Foretold, ObjectMark.Plotted, ObjectMark.Prepared)).forEach { assertNull(MarkRules.of(it).permission, "$it") }
    }

    // CR 702.143a, 702.170d: "after the turn": a permission that waits for a later turn needs the turn the mark was gained
    @Test
    fun whichMarksKeepTheirTurn() {
        marks.forEach { mark ->
            val waits = MarkRules.of(mark).permission is MarkPermission.CastCard
            assertEquals(waits, MarkRules.of(mark).recordsTurn, "$mark")
        }
    }
}
