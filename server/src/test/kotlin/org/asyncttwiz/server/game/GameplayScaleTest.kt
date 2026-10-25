package org.asyncttwiz.server.game

import org.asyncttwiz.fixtures.ScaleGames as F
import org.asyncttwiz.protocol.*
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameplayScaleTest {
    @Test fun warmServerHeadAndReducerDoNotScanTenThousandStoredActions() {
        val tree = F.build(10000)
        val store = GameplayStore()
        store.committed(10000, tree)
        // A SQL-less context deliberately fails if the revision-matched head cache queries history.
        val warm = store.read(DSL.using(SQLDialect.POSTGRES), tree.preparedState(tree.currentNodeId)!!, 10000)
        warm.work.reset()
        val next = F.play(warm, PlayAction.AdjustLife(F.a, "1"), 800001)
        val delta = TreeSync.delta(warm, next)
        assertEquals(1, delta.upserts.count { it.kind == "node" })
        assertEquals(0, warm.work.fullHistoryScans)
        assertEquals(0, warm.work.rootMaterializations)
        assertTrue(warm.work.indexedNodes <= 2)
    }
}
