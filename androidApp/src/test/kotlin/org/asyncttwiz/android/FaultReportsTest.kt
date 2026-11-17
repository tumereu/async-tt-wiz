package org.asyncttwiz.android

import org.asyncttwiz.protocol.Faults
import org.asyncttwiz.protocol.ProblemReport
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FaultReportsTest {
    private val sent = mutableListOf<ProblemReport>()

    @After fun restore() { FaultReports.reset(); FaultReports.install(send = {}) }

    @Test fun `a fault is logged at error level and sent once per run as a fault report`() {
        FaultReports.reset()
        FaultReports.install(send = { sent += it })
        repeat(50) { Faults.report("Table", "card without a view", mapOf("card" to "c$it")) }
        Faults.report("Table", "chip without a label", mapOf("chip" to "x"))
        assertEquals(2, sent.size)
        assertTrue(sent.all { it.kind == ProblemReport.FAULT })
        assertEquals("Automatic fault report: Table: card without a view; card=c0", sent[0].description)
        val log = AppLog.text()
        assertTrue(log.contains("E/fault: Table: card without a view; card=c0"))
        assertFalse(log.contains("card=c1"))
    }

    // Owner rule 2026-11-11: a stand-in shown because something couldn't be named is a fault; the player still sees it
    @Test fun `a placeholder is reported as a fault and returned for the player to see`() {
        FaultReports.reset()
        FaultReports.install(send = { sent += it })
        assertEquals("Trigger 1", Faults.placeholder("OrderTriggersSection", "trigger without a source", "Trigger 1", mapOf("index" to "0")))
        assertEquals("Automatic fault report: OrderTriggersSection: trigger without a source; index=0; shown=Trigger 1", sent.single().description)
    }

    // LOB-241, LOB-090, TBL-123, TBL-160 (owner, 2026-11-17): a catch-all refusal reason still shows, on a chip and in the card menu, and
    // is reported as placeholder text is, with the card, the ability and the engine's own refusal; once per card and ability
    // however often it is shown (a repeated tap); a specific reason is no fault
    @Test fun `a catch-all refusal reason is shown and reported once per card and ability`() {
        FaultReports.reset()
        FaultReports.install(send = { sent += it })
        val other = org.asyncttwiz.automatum.CardPlay.Refusal.Other("Not enough mana")
        val ability = org.asyncttwiz.automatum.CardPlay.Kind.Ability
        val gain = "{5}: You gain 1 life."
        repeat(3) { assertEquals(CHIP_CATCH_ALL, chipRefusal(other, ability, "Ember Cairn", emptyMap(), "c7", gain)) }
        assertEquals("Ember Cairn: Automatum doesn't allow that now (a cost it can't pay, or nothing to target).",
            engineRefusal(other, ability, "Ember Cairn", emptyMap(), "c7", gain))
        assertEquals(1, sent.size, "one report for Ember Cairn's ability: ${sent.map { it.description }}")
        val report = sent.single().description
        for (part in listOf("Ember Cairn (c7)", gain, "engine=Not enough mana", "where=chip")) assertTrue(part in report, report)
        // Another card's catch-all is its own report; the engine giving no refusal is said so
        chipRefusal(org.asyncttwiz.automatum.CardPlay.Refusal.Other(), ability, "Ember Relic", emptyMap(), "c8", "{T}: Draw a card.")
        assertEquals(2, sent.size)
        assertTrue("engine=none" in sent[1].description, sent[1].description)
        // A specific reason is none
        assertEquals("Already used as often as allowed this turn.",
            chipRefusal(org.asyncttwiz.automatum.CardPlay.Refusal.Used, ability, "Ember Cairn", emptyMap(), "c7", gain))
        assertEquals(2, sent.size)
        assertTrue(AppLog.text().contains("E/fault: Refusal: catch-all reason for Ember Cairn"))
    }

    @Test fun `no more than the run's cap is sent and a failing sink never reaches the caller`() {
        FaultReports.reset()
        FaultReports.install(send = { sent += it })
        repeat(FaultReports.MAX_SENT + 3) { Faults.report("Table", "fault $it") }
        assertEquals(FaultReports.MAX_SENT, sent.size)
        Faults.sink = { error("broken sink") }
        Faults.report("Table", "another")
    }
}
