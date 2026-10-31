package org.asyncttwiz.android

import org.asyncttwiz.fixtures.ScaleGames as F
import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class GameplayScaleTest {
    // SYS-026
    @Test fun acceptedTenThousandActionTreeKeepsIncrementalSyncAndReviewEndpoints() {
        val accepted = F.build(10000)
        val baseline = TreeSync.generation(accepted)
        val predicted = F.play(accepted, PlayAction.AdjustLife(F.a, "1"), 800001)
        accepted.work.reset()
        val published = TreeSync.apply(baseline, TreeSync.delta(accepted, predicted)).tree
        assertEquals(predicted.preparedState(predicted.currentNodeId), published.preparedState(published.currentNodeId))
        val parent = published.nodes.getValue(published.currentNodeId).parentId
        assertNotNull(published.prepareReview(parent))
        assertEquals(0, accepted.work.fullHistoryScans)
        assertEquals(0, accepted.work.rootMaterializations)
        assertTrue(accepted.work.replayedNodes + accepted.work.unappliedNodes <= 3)
    }
}
