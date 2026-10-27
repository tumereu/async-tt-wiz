package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class StackInteractionsTest {
    @Test fun laterPublishedPredictionOpensBubblesAndRequestDerivedEffectIdentitySurvivesAcceptance() {
        val f = BasicTableFixture()
        f.connect()
        try {
            assertTrue(f.move(40, TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 0), true))
            val setup = f.update(f.root, f.commands().single(), 1)
            f.message(setup); f.message(f.result(setup))
            val before = f.play
            var published = before
            val ui = TableToolkitController(f.actor, { published }, { true }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)
            assertTrue(ui.addEffects(listOf(f.id(40))))
            assertNull(ui.stackBubbleEntry) // Main-thread publication has not run yet.
            published = f.play
            ui.refreshStackBubbles()
            val predicted = published.board.stackEffects.single().id
            assertEquals(predicted, ui.stackBubbleEntry)
            val scene = TablePresentation.scene(f.game, published, f.actor, 412f, 892f, false)
            assertEquals(predicted, TablePresentation.stackBubbles(scene, published, ui.stackBubbleEntry, 412f, 892f)?.entryId)
            val revision = f.update(before, f.commands().last(), 2)
            f.message(f.result(revision)); f.message(revision)
            published = f.play
            ui.refreshStackBubbles()
            assertEquals(predicted, published.board.stackEffects.single().id)
            assertEquals(predicted, ui.stackBubbleEntry)
            val acceptedScene = TablePresentation.scene(f.game, published, f.actor, 412f, 892f, false)
            assertEquals(predicted, TablePresentation.stackBubbles(acceptedScene, published, ui.stackBubbleEntry, 412f, 892f)?.entryId)
            ui.showStackBubbles(null)
            ui.refreshStackBubbles()
            assertNull(ui.stackBubbleEntry) // Acceptance must not reopen a dismissed overlay.
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun realSessionAutomaticallyShowsNewEntriesAndBubblesUseOneInvocationWithOrdinaryRequests() {
        val f = BasicTableFixture()
        f.connect()
        val toolkit = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)
        val id = f.id(40)
        val scope = TableScope(id, listOf(id))
        assertTrue(f.move(40, TableDestination.Container(f.container(PlayZone.HAND), PlayZone.HAND), true))
        assertTrue(toolkit.handle(TableIntent.Move(scope, TableDestination.Container(f.container(PlayZone.STACK), PlayZone.STACK),
            BasicTableController.sources(f.play, listOf(id)))))
        val firstEntry = f.play.board.stackEffects.single().id
        assertEquals(firstEntry, toolkit.stackBubbleEntry)
        assertTrue(toolkit.handle(TableIntent.StackDestination(firstEntry, PlayZone.BATTLEFIELD)))
        assertEquals(BoardPosition(0, 1), f.card(40).placement!!.position)
        for ((width, height, landscape) in listOf(Triple(412f, 892f, false), Triple(892f, 412f, true),
            Triple(640f, 360f, true))) for (combat in listOf(false, true)) {
            val play = if (combat) f.play.copy(board = f.play.board.copy(combat = CombatArea("combat"))) else f.play
            val home = TablePresentation.scene(f.game, play, f.actor, width, height, landscape)
            val world = home.world!!
            val point = home.items.single { it.cardId == id }.bounds.center
            val camera = world.clamp(TableCamera(y = point.y - world.viewport.center.y))
            val scene = TablePresentation.scene(f.game, play, f.actor, width, height, landscape, TableLayoutState(camera))
            val field = scene.fields.single { it.owner == f.actor }
            val lands = field.landsBounds(atBottom = true)
            val placed = scene.items.single { it.cardId == id }
            assertTrue(placed.visible.isNotEmpty())
            assertTrue(placed.bounds.center.y < lands.y)
            assertTrue(placed.bounds.center.y >= lands.y - field.rowHeight)
        }
        assertNull(toolkit.stackBubbleEntry)
        assertTrue(toolkit.addEffects(listOf(id)))
        val effect = f.play.board.stackEffects.single()
        assertEquals(effect.id, toolkit.stackBubbleEntry)
        val before = f.commands().size
        assertTrue(toolkit.handle(TableIntent.StackDestination(effect.id, PlayZone.GRAVEYARD)))
        val commands = f.commands().drop(before)
        assertEquals(1, commands.map { it.invocationId }.distinct().size)
        val move = assertIs<PlayAction.MoveCard>((commands.single().action as GameAction.Play).action)
        assertEquals(effect.id, move.removeStackEntryId)
        assertTrue(f.play.board.stackEffects.isEmpty())
        assertEquals(f.container(PlayZone.GRAVEYARD), f.card(40).containerId)
        assertTrue(commands.any { (it.action as GameAction.Play).action is PlayAction.MoveCard })
        assertEquals("2/3", f.card(40).corner)
        assertTrue(f.card(40).counters.isEmpty())
        assertTrue(f.commands().all(GameWire::valid))
    }
    @Test fun markerHistoryHasReadableLiteralTextAndInspectionRetainsOccurrenceNotSourceScope() {
        val f = BasicTableFixture()
        val definition = f.definition.copy(faces = f.definition.faces.map { it.copy(rulesText = "First paragraph\nSecond paragraph") })
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val initial = f.root.copy(definitions = listOf(submitted), board = f.root.board.copy(cards = f.root.board.cards.map { it.copy(definition = submitted.digest) }))
        f.connect(initial)
        val toolkit = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)
        val id = f.id(40)
        toolkit.handle(TableIntent.Move(TableScope(id, listOf(id)), TableDestination.Grid(f.container(PlayZone.BATTLEFIELD), 0, 1),
            BasicTableController.sources(f.play, listOf(id))))
        toolkit.addEffects(listOf(id))
        val entry = f.play.board.stackEffects.single().id
        val before = f.play
        assertTrue(toolkit.handle(TableIntent.Submit(PlayAction.Stack(StackAction.MarkText(entry, 1)), null)))
        assertEquals(mapOf(entry to setOf(1)), f.play.board.stackTextMarks)
        val change = GameplayRules.change(before, f.actor, f.commands().last())
        val text = historyText(change.mutation!!, before, f.actor, mapOf(f.actor to "bob"))
        assertEquals("${definition.faces.single().name} marked: 2 — Second paragraph", text.description)
        toolkit.inspect(entry)
        assertEquals(entry, toolkit.inspection!!.cardId)
        assertEquals(entry, toolkit.inspection!!.stackEntryId)
        toolkit.inspect(id)
        assertNull(toolkit.inspection!!.stackEntryId)
        assertEquals(mapOf(entry to setOf(1)), f.play.board.stackTextMarks)
    }
}
