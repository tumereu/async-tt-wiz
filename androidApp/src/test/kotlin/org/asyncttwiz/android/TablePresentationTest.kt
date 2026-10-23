package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

internal object TableFixture {
    val game = GameSummary("game", "Canvas Spark", "2026-10-23T00:00:00Z", AccountIdentity("a", "Alice"),
        AccountIdentity("b", "Bob"), "round", "1", InvitationState.ACCEPTED, lifecycle = GameLifecycle.ACTIVE, playId = "play")
    val definition = CardDefinition("Secret name", listOf(DefinitionFace("face", "Secret face", rulesText = "Secret rules")))
    val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
    fun container(owner: String, zone: PlayZone) = "$owner/$zone"
    fun play(hand: Int = 0, board: Int = 0, library: Int = 4): PlayState {
        val cards = mutableListOf<PlayCard>()
        val containers = mutableListOf<PlayContainer>()
        for (owner in listOf("a", "b")) for (zone in PlayZone.entries.filter { it != PlayZone.STACK }) {
            val count = when (zone) { PlayZone.HAND -> hand; PlayZone.BATTLEFIELD -> board; PlayZone.LIBRARY -> library; else -> 0 }
            val id = container(owner, zone)
            val members = (0 until count).map { n -> "$owner/$zone/$n" }
            cards += members.map { PlayCard(it, submitted.digest, owner, id, "face", covered = zone != PlayZone.BATTLEFIELD) }
            containers += PlayContainer(id, zone, owner, members)
        }
        containers += PlayContainer("stack", PlayZone.STACK)
        return PlayState("game", "play", "round", "request", PlayRoot("root", "branch"), "root",
            BoardState(listOf(PlayPlayer("a"), PlayPlayer("b")), cards, containers, TurnState("a")), listOf(submitted),
            emptyList(), InitialStarter(StarterChoice("a"), "a"))
    }
    fun scene(play: PlayState = play(board = 3, hand = 2), landscape: Boolean = false,
        ui: TableLayoutState = TableLayoutState(), placements: Map<String, TablePlacement> = emptyMap()) =
        TablePresentation.scene(game, play, "a", if (landscape) 900f else 412f, if (landscape) 412f else 820f,
            landscape, ui, placements)
}

class TablePresentationTest {
    @Test fun commonScaleAndEqualFootprintsAcrossOrientationOwnerRotationAndPiles() {
        for (landscape in listOf(false, true)) for (zoom in listOf(.65f, 1f, 2f)) {
            val p = TableFixture.play(board = 3, hand = 3)
            val state = p.copy(board = p.board.copy(cards = p.board.cards.mapIndexed { n, c -> c.copy(tapped = n % 2 == 0) }))
            val positions = state.board.cards.filter { it.containerId.endsWith("BATTLEFIELD") }.associate { c ->
                c.id to TablePlacement(0, 0, c.id.last().digitToInt()) }
            val scene = TableFixture.scene(state, landscape, TableLayoutState(TableCamera(zoom = zoom)), positions)
            val cards = scene.items.filter { it.kind == TableItemKind.CARD }
            assertTrue(cards.isNotEmpty())
            cards.forEach { c ->
                assertEquals(48 * zoom, minOf(c.bounds.width, c.bounds.height), .001f)
                assertEquals(68 * zoom, maxOf(c.bounds.width, c.bounds.height), .001f)
                if (c.zone == PlayZone.BATTLEFIELD) {
                    assertEquals(84 * zoom, c.footprint.width, .001f)
                    assertTrue(c.bounds.x >= c.footprint.x && c.bounds.right <= c.footprint.right)
                    assertTrue(c.bounds.y >= c.footprint.y && c.bounds.bottom <= c.footprint.bottom)
                }
            }
            scene.items.filter { it.kind == TableItemKind.PILE }.forEach { assertTrue(it.bounds.width >= scene.cardWidth) }
        }
    }

    @Test fun ownerIsNotFieldAndCoveredMetadataNeverLeaksThroughTableSemantics() {
        val p = TableFixture.play(board = 1, hand = 1)
        val changed = p.copy(board = p.board.copy(cards = p.board.cards.map { c ->
            if (c.id == "a/BATTLEFIELD/0") c.copy(owner = "b") else c
        }))
        val scene = TableFixture.scene(changed)
        val moved = scene.items.single { it.cardId == "a/BATTLEFIELD/0" }
        assertEquals("b", moved.owner); assertTrue(moved.label.contains("owner Bob"))
        scene.items.filter { it.zone == PlayZone.HAND && it.cardId != null }.forEach {
            assertEquals("", it.value); assertEquals("", it.corner)
            assertFalse(it.label.contains("Secret")); assertTrue(it.label.startsWith("Covered card"))
        }
    }

    @Test fun bothHandsCenterWhenFittingAndReachBothEndsWithoutChangingOrderOrScale() {
        for (landscape in listOf(false, true)) for (count in listOf(2, 40)) {
            val p = TableFixture.play(hand = count)
            val scene = TableFixture.scene(p, landscape)
            for (owner in listOf("a", "b")) {
                val id = TableFixture.container(owner, PlayZone.HAND)
                val port = scene.viewports.single { it.id == id }
                val first = scene.items.single { it.cardId == "$owner/HAND/0" }
                if (count == 2) {
                    assertEquals(0f, port.maxScroll)
                    val last = scene.items.single { it.cardId == "$owner/HAND/1" }
                    assertEquals(port.bounds.center.x, (first.bounds.x + last.bounds.right) / 2, .01f)
                } else {
                    assertTrue(first.visible.isNotEmpty()); assertTrue(port.maxScroll > 0)
                    val end = TableFixture.scene(p, landscape, TableLayoutState(scroll = mapOf(id to Float.MAX_VALUE)))
                    val last = end.items.single { it.cardId == "$owner/HAND/${count - 1}" }
                    assertTrue(last.visible.isNotEmpty()); assertTrue(last.bounds.right <= port.bounds.right + .01f)
                    assertEquals(scene.cardWidth, end.cardWidth)
                    assertEquals(p.board.containers.single { it.id == id }.cards,
                        end.items.filter { it.viewport == id && it.cardId != null }.map { it.cardId })
                }
            }
        }
    }

    @Test fun docksAndStackUseCurrentOrientationAndZoomOverflowIsNavigable() {
        for (landscape in listOf(false, true)) {
            val p = TableFixture.play()
            val scene = TableFixture.scene(p, landscape, TableLayoutState(TableCamera(zoom = 2f)))
            val stack = scene.items.single { it.zone == PlayZone.STACK }
            val dock = scene.viewports.single { it.id == "dock/a" }
            assertTrue(dock.maxScroll > 0)
            if (landscape) assertTrue(stack.bounds.right < scene.fields.first().bounds.x)
            else assertTrue(stack.bounds.x >= dock.bounds.right)
            val sidebar = scene.viewports.firstOrNull { it.id == "sidebar" }
            val end = TableFixture.scene(p, landscape, TableLayoutState(TableCamera(zoom = 2f),
                mapOf("dock/a" to dock.maxScroll), mapOf("sidebar" to (sidebar?.maxScrollY ?: 0f))))
            assertTrue(end.items.single { it.id == "a/EXILE" }.visible.isNotEmpty())
            assertTrue(scene.items.single { it.id == "a/LIBRARY" }.label.contains("4 cards"))
        }
    }

    @Test fun clippingAndVisiblePeeksDriveHitRectangleAndSemanticsTogether() {
        val p = TableFixture.play(board = 3)
        val positions = p.board.cards.filter { it.containerId.endsWith("BATTLEFIELD") }.associate { it.id to TablePlacement(0, 0, it.id.last().digitToInt()) }
        val scene = TableFixture.scene(p, placements = positions)
        val under = scene.items.single { it.cardId == "a/BATTLEFIELD/0" }
        val top = scene.items.single { it.cardId == "a/BATTLEFIELD/2" }
        assertTrue(under.visible.isNotEmpty())
        under.visible.forEach { b -> assertEquals(under.id, scene.hit(b.center)?.id); assertTrue(under.cardId in scene.rectangle(b)) }
        val insideTop = TableBounds(top.bounds.right - 4, top.bounds.bottom - 4, 2f, 2f)
        assertEquals(setOf(top.cardId), scene.rectangle(insideTop))
        val fullyHidden = TableFixture.scene(p, placements = positions.mapValues { TablePlacement(0, 0, 0) })
        assertTrue(fullyHidden.items.single { it.cardId == under.cardId }.visible.isEmpty())
        assertFalse(under.cardId in fullyHidden.rectangle(under.bounds))
    }

    @Test fun animatedDrawnBoundsNotDestinationBoundsOwnTargets() {
        val scene = TableFixture.scene(TableFixture.play(board = 1))
        val old = scene.items.single { it.cardId == "a/BATTLEFIELD/0" }
        val shifted = old.copy(bounds = old.bounds.copy(x = old.bounds.x + 120))
        val animated = TablePresentation.clip(scene.copy(items = scene.items.map { if (it.id == old.id) shifted else it }), 412f, 820f)
        assertNotEquals(old.id, animated.hit(old.bounds.center)?.id)
        assertEquals(old.id, animated.hit(shifted.bounds.center)?.id)
        assertTrue(animated.items.single { it.id == old.id }.visible.all { it.x >= shifted.bounds.x })
    }

    @Test fun portraitCounterPredicateAndBlankValuesPreserveState() {
        listOf("", " ", "0", "-000", "+0").forEach { assertFalse(TablePresentation.nonzero(it)) }
        listOf("-1", "flying", "0.0", "0001").forEach { assertTrue(TablePresentation.nonzero(it)) }
        val p = TableFixture.play().let { it.copy(board = it.board.copy(players = listOf(
            PlayPlayer("a", counters = mapOf(CounterColor.RED to "0")), PlayPlayer("b", counters = mapOf(CounterColor.BLUE to "-1"))))) }
        val portrait = TableFixture.scene(p)
        assertTrue(portrait.items.none { it.kind == TableItemKind.COUNTER && it.owner == "a" })
        val shown = portrait.items.filter { it.kind == TableItemKind.COUNTER }
        assertEquals(6, shown.size); assertEquals("", shown.first { it.counter == CounterColor.RED }.value)
        assertTrue(shown.first { it.counter == CounterColor.RED }.label.contains("no value"))
        assertEquals(12, TableFixture.scene(p, true).items.count { it.kind == TableItemKind.COUNTER })
        assertEquals("0", p.board.players.first().counters[CounterColor.RED])
    }

    @Test fun automaticInspectionUsesCurrentRegionNotOwnerAndNeverGrantsOpponentHiddenSide() {
        val p = TableFixture.play(hand = 1)
        val changed = p.copy(board = p.board.copy(cards = p.board.cards.map { c -> c.copy(owner = if (c.owner == "a") "b" else "a") }))
        assertEquals(TableFixture.definition, TablePresentation.inspection(changed, "a", "a/HAND/0"))
        val hidden = TablePresentation.inspection(changed, "a", "b/HAND/0")!!
        assertEquals("Covered card", hidden.name); assertEquals("", hidden.faces.single().rulesText)
        assertFalse(hidden.toString().contains("Secret")); assertNull(TablePresentation.inspection(changed, "a", "gone"))
        val uncovered = changed.copy(board = changed.board.copy(cards = changed.board.cards.map { it.copy(covered = false) }))
        assertEquals(listOf(TableFixture.definition.faces.single()), TablePresentation.inspection(uncovered, "a", "b/HAND/0")!!.faces)
    }

    @Test fun compactLandscapeSidebarCanReachLastCounterWithoutOverlappingStackOrHands() {
        val p = TableFixture.play()
        val first = TableFixture.scene(p, true, TableLayoutState(TableCamera(zoom = 2f)))
        val sidebar = first.viewports.single { it.id == "sidebar" }
        assertTrue(sidebar.maxScrollY > 0)
        val end = TableFixture.scene(p, true, TableLayoutState(TableCamera(zoom = 2f), scrollY = mapOf("sidebar" to sidebar.maxScrollY)))
        val counters = end.items.filter { it.kind == TableItemKind.COUNTER && it.owner == "a" }
        assertTrue(counters.first().visible.isNotEmpty())
        val rail = end.viewports.single { it.id == "counters/a" }
        val farEnd = TableFixture.scene(p, true, TableLayoutState(TableCamera(zoom = 2f),
            scroll = mapOf("counters/a" to rail.maxScroll), scrollY = mapOf("sidebar" to sidebar.maxScrollY)))
        assertTrue(farEnd.items.single { it.id == counters.last().id }.visible.isNotEmpty())
        val ownHand = end.viewports.single { it.id == "a/HAND" }.bounds
        assertTrue(counters.flatMap { it.visible }.all { it.bottom <= ownHand.y })
        val stack = end.items.single { it.zone == PlayZone.STACK }.bounds
        assertTrue(end.items.filter { it.kind == TableItemKind.COUNTER }.none { it.bounds.intersection(stack) != null })
    }
}
