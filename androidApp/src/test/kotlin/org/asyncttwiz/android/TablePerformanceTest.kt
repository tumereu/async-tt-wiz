package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.random.Random
import kotlin.test.*

class TablePerformanceTest {
    @Test fun placementMembershipReadsCardsLinearlyAndUsesContainerOrder() {
        for (count in listOf(100, 200, 400)) {
            val initial = TableFixture.play(board = count, library = 0)
            val cards = initial.board.cards.map { card ->
                card.copy(placement = PlayPlacement(BoardPosition(2, 1), "pile"))
            }
            var reads = 0
            val counted = object : AbstractList<PlayCard>() {
                override val size get() = cards.size
                override fun get(index: Int): PlayCard { reads++; return cards[index] }
            }
            val play = initial.copy(board = initial.board.copy(cards = counted,
                containers = initial.board.containers.map { it.copy(cards = it.cards.reversed()) }))
            val placements = TablePresentation.logicalPlacements(play)
            for (container in play.board.containers) container.cards.forEachIndexed { index, id ->
                assertEquals(TablePlacement(2, 1, index, "pile"), placements[id])
            }
            assertTrue(reads <= cards.size * 3, "$count cards per field required $reads card reads")
        }
    }

    @Test fun occlusionCandidatesExcludeOffscreenAndSpatiallySeparateDrawings() {
        for (count in listOf(1_000, 10_000)) {
            val screen = TableBounds(0f, 0f, 1_000f, 1_000f)
            val items = (0 until count).map { n ->
                val bounds = TableBounds(n * 60f, 10f, 28f, 40f)
                TableItem("$n", bounds, "", "", kind = TableItemKind.CARD,
                    visible = if (n < 10) listOf(bounds) else emptyList())
            }
            val index = TableOcclusionIndex(items)
            val checks = items.indices.sumOf { index.candidates(it).size }
            assertTrue(checks <= 40, "$count drawings (10 visible) required $checks candidate checks")
            val port = TableViewport("field", screen.copy(width = 600f), 600f, 0f)
            val scene = TableScene(items.map { it.copy(viewport = port.id, zone = PlayZone.BATTLEFIELD) },
                emptyList(), listOf(port))
            val clipped = TablePresentation.clip(scene, 600f, 1_000f)
            assertEquals(10, clipped.items.count { it.visible.isNotEmpty() })
            assertTrue(clipped.items.take(10).all { it.visible == listOf(it.bounds) })

            // Many visible but disjoint drawings must not fall back to all visible pairs either.
            val separated = items.take(1_000).mapIndexed { n, item ->
                val bounds = TableBounds((n % 40) * 24f, (n / 40) * 24f, 8f, 8f)
                item.copy(bounds = bounds, visible = listOf(bounds.intersection(screen)!!))
            }
            val visibleIndex = TableOcclusionIndex(separated)
            assertTrue(separated.indices.sumOf { visibleIndex.candidates(it).size } <= 4_000)
        }
    }

    @Test fun placementGroupsSeparatePilesAndContainersEvenAtTheSamePosition() {
        val initial = TableFixture.play(board = 3, library = 0)
        val play = initial.copy(board = initial.board.copy(cards = initial.board.cards.map { card ->
            card.copy(placement = if (card.id == "b/BATTLEFIELD/0") null else
                PlayPlacement(BoardPosition(2, 1), if (card.id.endsWith("/1")) "other" else "pile"))
        }, containers = initial.board.containers.map {
            it.copy(cards = if (it.zone == PlayZone.STACK) listOf("effect") else it.cards.reversed())
        }, stackEffects = listOf(StackEffect("effect", "a/BATTLEFIELD/0"))))
        val positions = TablePresentation.logicalPlacements(play)
        for (owner in listOf("a", "b")) {
            assertEquals(TablePlacement(2, 1, 0, "pile"), positions["$owner/BATTLEFIELD/2"])
            assertEquals(TablePlacement(2, 1, 0, "other"), positions["$owner/BATTLEFIELD/1"])
        }
        assertEquals(TablePlacement(2, 1, 1, "pile"), positions["a/BATTLEFIELD/0"])
        assertFalse("b/BATTLEFIELD/0" in positions)
    }

    @Test fun indexedClippingKeepsExactPainterOrderFragmentsAndHitTargets() {
        val random = Random(15)
        val screen = TableBounds(0f, 0f, 412f, 820f)
        val ports = listOf(TableViewport("field", screen.copy(y = 100f, height = 450f), 412f, 0f),
            TableViewport("stack", screen.copy(x = 300f, width = 100f), 100f, 0f),
            TableViewport("hand", screen.copy(y = 600f, height = 100f), 412f, 0f),
            TableViewport("cardPanel/library", screen.copy(x = 20f, y = 200f, width = 300f, height = 200f), 300f, 0f))
        repeat(30) {
            val items = (0 until 120).map { n ->
                val port = ports.random(random)
                val bounds = TableBounds(random.nextInt(-100, 500).toFloat(), random.nextInt(-100, 900).toFloat(),
                    random.nextInt(1, 150).toFloat(), random.nextInt(1, 180).toFloat())
                TableItem("$n", bounds, "", "", zone = when (port.id) {
                    "field" -> PlayZone.BATTLEFIELD; "stack" -> PlayZone.STACK; else -> PlayZone.HAND
                }, kind = TableItemKind.entries.random(random), viewport = port.id, inTransit = n % 7 == 0)
            }
            val scene = TableScene(items, emptyList(), ports, stackRegion = TableBounds(300f, 350f, 70f, 100f),
                world = TableWorld(ports.first().bounds, screen))
            val expected = fullScanClip(scene, screen)
            val actual = TablePresentation.clip(scene, screen.width, screen.height)
            assertEquals(expected.items, actual.items)
            repeat(50) {
                val point = TablePoint(random.nextFloat() * screen.width, random.nextFloat() * screen.height)
                assertEquals(expected.hit(point), actual.hit(point))
            }
        }
    }

    @Test fun preparedPlacementsCanBeReusedAcrossCameraChangesWithoutChangingLayout() {
        val initial = TableFixture.play(board = 12, hand = 2)
        val play = initial.copy(board = initial.board.copy(cards = initial.board.cards.mapIndexed { n, card ->
            if (!card.containerId.endsWith("BATTLEFIELD")) card else card.copy(
                placement = if (n % 5 == 0) null else PlayPlacement(BoardPosition(n % 3, n % 2), "pile${n % 4}"))
        }))
        val positions = TablePresentation.logicalPlacements(play)
        for (camera in listOf(TableCamera(), TableCamera(20f, -30f, .65f), TableCamera(-50f, 40f, 2f))) {
            val ui = TableLayoutState(camera)
            val scene = TablePresentation.scene(TableFixture.game, play, "a", 412f, 820f, false, ui,
                logicalPositions = positions)
            assertEquals(TableFixture.scene(play, ui = ui), scene)
        }
    }

    /** Original all-pairs algorithm is the visual/targeting oracle, not a timing threshold. */
    private fun fullScanClip(scene: TableScene, screen: TableBounds): TableScene {
        val ordered = scene.items.filter { it.zone == PlayZone.BATTLEFIELD && !it.viewport.startsWith("cardPanel/") } +
            scene.items.filter { it.zone != PlayZone.BATTLEFIELD && !it.viewport.startsWith("cardPanel/") } +
            scene.items.filter { it.viewport.startsWith("cardPanel/") }
        val ports = scene.viewports.associateBy { it.id }
        val clipped = ordered.map { item ->
            val viewport = if (item.inTransit) {
                if (item.zone == PlayZone.BATTLEFIELD) scene.world?.visibleViewport ?: screen else screen
            } else ports[item.viewport]?.bounds
            val regions = listOfNotNull(viewport?.let(item.bounds::intersection)?.intersection(screen))
            val parts = if (item.viewport.startsWith("cardPanel/") || item.inTransit) regions else
                scene.cardPanels.fold(regions) { parts, panel -> parts.flatMap { it.subtract(panel.bounds) } }
            item.copy(visible = if (item.zone == PlayZone.BATTLEFIELD && scene.stackRegion != null)
                parts.flatMap { it.subtract(scene.stackRegion) } else parts)
        }
        return scene.copy(items = clipped.mapIndexed { index, item ->
            var parts = item.visible
            if (item.kind != TableItemKind.HAND) for (next in index + 1 until clipped.size) {
                val cover = clipped[next]
                if (cover.kind == TableItemKind.HAND ||
                    item.zone == PlayZone.STACK && item.kind == TableItemKind.PILE && cover.viewport == item.viewport ||
                    item.bounds.intersection(cover.bounds) == null) continue
                for (part in cover.visible) if (parts.any { it.intersection(part) != null })
                    parts = parts.flatMap { it.subtract(part) }
                if (parts.isEmpty()) break
            }
            item.copy(visible = parts)
        })
    }
}
