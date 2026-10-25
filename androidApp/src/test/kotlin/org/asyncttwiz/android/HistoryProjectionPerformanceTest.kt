package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class HistoryProjectionPerformanceTest {
    @Test fun incrementalTextProjectionReusesPrefixAndInvalidatesReparentedSuffix() {
        val f = DownloadedTreeFixture
        var tree = f.root()
        repeat(360) { n -> tree = f.tap(tree, 2000 + n, tapped = n % 2 == 0) }
        tree = TreeSync.decode(TreeSync.records(TreeSync.generation(tree)), tree.initial.gameId, tree.initial.id).tree
        val texts = historyTexts(tree, f.a, emptyMap())
        val next = f.tap(tree, 3000)
        val start = System.nanoTime()
        val projected = historyTexts(next, f.a, emptyMap(), tree, texts)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 50)
        assertSame(texts.getValue(tree.currentNodeId), projected.getValue(tree.currentNodeId))
        assertEquals(historyTexts(next, f.a, emptyMap()), projected)
        val undone = f.undo(next)
        assertEquals(historyTexts(undone, f.a, emptyMap()), historyTexts(undone, f.a, emptyMap(), next, projected))
    }
}
