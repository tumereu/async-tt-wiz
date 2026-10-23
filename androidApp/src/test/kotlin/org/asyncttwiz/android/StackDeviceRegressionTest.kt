package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class StackDeviceRegressionTest {
    @Test fun productionLayoutDragsSendRequestsAndOpenNewTopBubblesThenRouteFaceBadgeDoubleAndHold() {
        for ((width, height) in listOf(412f to 892f, 892f to 412f)) for (populated in listOf(false, true)) {
            val f = BasicTableFixture()
            f.connect()
            try {
                val ui = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay)
                assertTrue(f.move(40, TableDestination.Container(f.container(PlayZone.HAND), PlayZone.HAND), true))
                if (populated) assertTrue(f.move(41, TableDestination.Container(f.container(PlayZone.STACK), PlayZone.STACK), true))
                val events = mutableListOf<TableIntent>()
                lateinit var gestures: TableGestureCoordinator
                gestures = TableGestureCoordinator({ intent ->
                    events += intent
                    when (intent) {
                        is TableIntent.Actions -> ui.open(gestures.capturedActions!!, gestures.actionItem!!, ui.selectionState.value)
                        is TableIntent.Selection -> ui.selectionState.value = intent.selection
                        is TableIntent.Inspect -> ui.inspect(intent.cardId)
                        else -> ui.handle(intent)
                    }
                })
                fun scene(): TableScene {
                    val base = TablePresentation.scene(f.game, f.play, f.actor, width, height, width > height)
                    return base.copy(stackBubbles = TablePresentation.stackBubbles(base, f.play, ui.stackBubbleEntry, width, height))
                        .also { gestures.update(it, ui.selectionState.value, true) }
                }
                val initial = scene()
                val hand = initial.items.single { it.cardId == f.id(40) }
                val pile = initial.items.single { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }
                val before = f.commands().size
                gestures.down(hand.bounds.center, 0)
                gestures.move(pile.bounds.center, 80)
                assertEquals(TableDestination.Container(pile.id, PlayZone.STACK), gestures.preview.destination)
                gestures.up(pile.bounds.center, 100)
                assertEquals(listOf(f.id(40)), events.filterIsInstance<TableIntent.Move>().single().scope.cardIds)
                assertEquals(f.container(PlayZone.STACK), f.card(40).containerId)
                assertTrue(f.commands().drop(before).any { (it.action as? GameAction.Play)?.action is PlayAction.MoveCard })
                assertEquals(f.id(40), ui.stackBubbleEntry)
                val after = scene()
                val bubble = assertNotNull(after.stackBubbles)
                assertEquals(f.id(40), bubble.entryId)
                assertEquals(pile.bounds, bubble.anchor)
                bubble.controls.forEach { control ->
                    assertEquals(control.bounds, control.bounds.intersection(TableBounds(0f, 0f, width, height)))
                    assertTrue(bubble.controls.filter { it != control }.none { it.bounds.intersection(control.bounds) != null })
                }
                // A tap elsewhere dismisses the real gesture-generated bubbles.
                val outside = TablePoint(width - 4, height / 2)
                gestures.down(outside, 200); gestures.up(outside, 220)
                assertNull(ui.stackBubbleEntry)
                val current = scene()
                val top = current.items.single { it.cardId == f.id(40) }
                events.clear()
                gestures.down(top.bounds.center, 400); gestures.up(top.bounds.center, 420); gestures.tick(720)
                assertEquals(f.id(40), ui.stackBubbleEntry)
                assertNull(ui.publicContents())
                assertEquals(listOf<TableIntent>(TableIntent.StackBubbles(f.id(40))), events)
                scene()
                gestures.down(pile.stackBadge.center, 800); gestures.up(pile.stackBadge.center, 820)
                assertEquals(pile.id, assertNotNull(ui.publicContents()).containerId)
                assertNull(ui.stackBubbleEntry)
                ui.dismiss(); scene(); events.clear()
                gestures.down(top.bounds.center, 1000); gestures.up(top.bounds.center, 1020)
                gestures.down(top.bounds.center, 1100); gestures.up(top.bounds.center, 1120); gestures.tick(1500)
                assertEquals(f.id(40), events.filterIsInstance<TableIntent.Actions>().single().scope!!.contextId)
                assertEquals(f.id(40), ui.capture!!.contextViewId)
                assertTrue(events.none { it is TableIntent.Rotate || it is TableIntent.StackBubbles })
                ui.dismiss(); scene(); events.clear()
                gestures.down(top.bounds.center, 1600); gestures.tick(2150); gestures.up(top.bounds.center, 2160)
                assertEquals(listOf<TableIntent>(TableIntent.Inspect(f.id(40))), events)
                assertEquals(f.id(40), ui.inspection!!.stackEntryId)
            } finally { f.lobby.close(); f.worker.drain() }
        }
    }

    @Test fun populatedStackAcceptsEveryPublicSourceAndCombatProjectionAtPileAndBadgeCenters() {
        val f = TableStatesFixture
        val play = f.combat().let { p ->
            val exile = p.board.containers.single { it.zone == PlayZone.EXILE && it.accountId == f.a }
            val card = p.board.cards.first { it.containerId == f.id("a/LIBRARY") }
            p.copy(board = p.board.copy(cards = p.board.cards.map { if (it.id == card.id) it.copy(containerId = exile.id, covered = false) else it },
                containers = p.board.containers.map { if (it.id == exile.id) it.copy(cards = listOf(card.id)) else it.copy(cards = it.cards - card.id) }))
        }
        for ((width, height) in listOf(412f to 892f, 892f to 412f)) {
            val scene = TablePresentation.scene(f.game, play, f.a, width, height, width > height)
            val pile = scene.items.single { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }
            val sources = listOf(PlayZone.HAND, PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD, PlayZone.EXILE).map { zone ->
                scene.items.first { it.zone == zone && it.owner == f.a && it.visible.isNotEmpty() &&
                    it.visible.any { bounds -> bounds.contains(it.bounds.center) } &&
                    it.kind == if (zone == PlayZone.HAND || zone == PlayZone.BATTLEFIELD) TableItemKind.CARD else TableItemKind.PILE }
            } + scene.items.first { it.cardId == f.id("attacker1") }
            for (source in sources) for (end in listOf(pile.bounds.center, pile.stackBadge.center)) {
                val events = mutableListOf<TableIntent>()
                val gestures = TableGestureCoordinator(events::add)
                gestures.update(scene, TableSelection(), true)
                assertEquals(source.id, scene.hit(source.bounds.center)?.id)
                gestures.down(source.bounds.center, 0); gestures.move(end, 80); gestures.up(end, 100)
                val move = assertNotNull(events.filterIsInstance<TableIntent.Move>().singleOrNull(), "$width: ${source.id}: $events")
                assertEquals(source.cardId ?: source.cards.first(), move.scope.contextId)
                assertEquals(TableDestination.Container(pile.id, PlayZone.STACK), move.destination)
                assertTrue(events.none { it is TableIntent.Actions || it is TableIntent.Rotate })
            }
        }
    }
}
