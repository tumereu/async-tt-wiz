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

    @Test fun `no more than the run's cap is sent and a failing sink never reaches the caller`() {
        FaultReports.reset()
        FaultReports.install(send = { sent += it })
        repeat(FaultReports.MAX_SENT + 3) { Faults.report("Table", "fault $it") }
        assertEquals(FaultReports.MAX_SENT, sent.size)
        Faults.sink = { error("broken sink") }
        Faults.report("Table", "another")
    }
}
