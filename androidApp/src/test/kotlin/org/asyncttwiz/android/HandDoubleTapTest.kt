package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class HandDoubleTapTest {
    @Test fun landShortcutUsesFrontFaceAndFirstFreeLandSlotNotTheSelectedBackFace() {
        val field = TableField(TableBounds(0f, 0f, 200f, 300f), "owner", "field")
        val hand = TableItem("hand-card", TableBounds(0f, 0f, 28f, 40f), "Card", "", owner = "owner",
            zone = PlayZone.HAND, kind = TableItemKind.CARD, cardId = "card", typeLine = "Creature",
            frontTypeLine = "Basic Land — Forest")
        val scene = TableScene(listOf(hand), listOf(field))
        assertEquals(TableDestination.Grid("field", 0, 0), handDoubleTapDestination(scene, hand))
        assertNull(handDoubleTapDestination(scene, hand.copy(zone = PlayZone.GRAVEYARD)))
    }

    @Test fun allFacesMustBeClassifiableNonlandPermanents() {
        fun definition(vararg types: String) = CardDefinition("Fixture", types.mapIndexed { index, type ->
            DefinitionFace("face-$index", "Face", typeLine = type) })
        for (types in listOf(listOf("Creature"), listOf("Legendary Artifact Creature — Golem"),
            listOf("Enchantment", "Planeswalker"), listOf("Battle — Siege", "Creature")))
            assertTrue(StackContexts.permanentOnly(definition(*types.toTypedArray())), types.toString())
        for (types in listOf(listOf("Instant"), listOf("Sorcery"), listOf("Creature", "Instant — Adventure"),
            listOf("Sorcery", "Instant"), listOf("Creature", "Land"), listOf("Creature", ""),
            listOf("Mystery Creature"), listOf("")))
            assertFalse(StackContexts.permanentOnly(definition(*types.toTypedArray())), types.toString())
        assertFalse(StackContexts.permanentOnly(null))
    }

    @Test fun doubleTapUsesNormalCastThenSeparateResolveGroupWithPlaybackPauseAndFlights() {
        val f = BasicTableFixture()
        val definition = f.definition.copy(faces = f.definition.faces.map { it.copy(typeLine = "Creature") })
        val digest = DeckJson.digest(definition)
        val field = f.container(PlayZone.BATTLEFIELD)
        val hand = f.container(PlayZone.HAND)
        val initial = f.root.copy(definitions = listOf(SubmittedDefinition(digest, definition)), board = f.root.board.copy(
            cards = f.root.board.cards.map { card -> card.copy(definition = digest,
                containerId = if (card.id in listOf(f.id(40), f.id(41))) field else hand,
                placement = when (card.id) {
                    f.id(40) -> PlayPlacement(BoardPosition(0, 1), f.id(400))
                    f.id(41) -> PlayPlacement(BoardPosition(2, 1), f.id(401))
                    else -> null
                }) },
            containers = f.root.board.containers.map { it.copy(cards = when (it.id) {
                field -> listOf(f.id(40), f.id(41))
                hand -> listOf(f.id(42), f.id(43))
                else -> emptyList()
            }) }))
        f.connect(initial)
        try {
            val toolkit = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)
            val scene = TablePresentation.scene(f.game, f.play, f.actor, 412f, 892f, false)
            val card = scene.items.single { it.cardId == f.id(42) }
            val gestures = TableGestureCoordinator({ toolkit.handle(it) })
            gestures.update(scene, TableSelection(), true)
            val point = card.visible.first().center
            gestures.down(point, 0); gestures.up(point, 20)
            gestures.down(point, 100); gestures.up(point, 120)
            assertEquals(field, f.card(42).containerId)
            assertEquals(BoardPosition(1, 1), f.card(42).placement!!.position)
            assertEquals(1, f.commands().size)
            assertIs<GameAction.PlayBatch>(f.commands().single().action)
            val commands = f.commands().playMembers()
            assertEquals(2, commands.size)
            assertIs<StackAction.AddEffect>(assertIs<PlayAction.Stack>(assertIs<GameAction.Play>(commands[0].action).action).action)
            assertIs<PlayAction.MoveCard>(assertIs<GameAction.Play>(commands[1].action).action)
            assertEquals(1, commands.map { it.invocationId }.distinct().size)

            var tree = GameplayTree(initial)
            val boards = mutableListOf(initial)
            for (command in commands) {
                tree = assertNotNull(HistoryContinuation.change(tree, f.actor, command).tree)
                boards += assertNotNull(tree.stateAt(tree.currentNodeId))
            }
            assertEquals(f.play.board, boards.last().board)
            assertNotEquals(boards[1].currentGroup!!.id, boards[2].currentGroup!!.id)
            val index = HistoryTree.ingest(tree, "accepted")
            val playback = HistoryPlayback(index)
            playback.openFrom(initial.currentNodeId)
            playback.next(true, f.other, board = initial.board, definitions = initial.definitions)
            assertEquals(boards[1].currentNodeId, playback.position.cursorId)
            assertTrue(playback.pauseAfterAnimation())
            val castScene = TablePresentation.scene(f.game, boards[1], f.other, 412f, 892f, false)
            val finalScene = TablePresentation.scene(f.game, boards[2], f.other, 412f, 892f, false)
            val entry = CombatStackRules.stack(boards[1].board).cards.single()
            val opponentStart = TablePresentation.scene(f.game, initial, f.other, 412f, 892f, false)
            val animation = HistorySceneAnimation(opponentStart)
            animation.retarget(castScene, 1f)
            assertEquals(opponentStart.items.single { it.cardId == f.id(42) }.bounds,
                animation.frame(0f).single { it.item.cardId == entry }.bounds)
            animation.retarget(finalScene, 1f)
            assertEquals(castScene.items.single { it.cardId == entry }.bounds,
                animation.frame(0f).single { it.item.cardId == entry }.bounds)
            assertEquals(finalScene.items.single { it.cardId == f.id(42) }.bounds,
                animation.frame(1f).single { it.item.cardId == entry }.bounds)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun faceDownCastAutoResolvesAsASeparatePausedGroupAndOneUndoRestoresTheHand() {
        val f = BasicTableFixture()
        val definition = f.definition.copy(faces = f.definition.faces.map { it.copy(typeLine = "Instant") })
        val digest = DeckJson.digest(definition)
        val field = f.container(PlayZone.BATTLEFIELD)
        val hand = f.container(PlayZone.HAND)
        val initial = f.root.copy(definitions = listOf(SubmittedDefinition(digest, definition)), board = f.root.board.copy(
            cards = f.root.board.cards.map { card -> card.copy(definition = digest,
                containerId = if (card.id == f.id(40)) field else hand,
                placement = if (card.id == f.id(40)) PlayPlacement(BoardPosition(0, 1), f.id(400)) else null) },
            containers = f.root.board.containers.map { it.copy(cards = when (it.id) {
                field -> listOf(f.id(40))
                hand -> listOf(f.id(42), f.id(43))
                else -> emptyList()
            }) }))
        f.connect(initial)
        try {
            val toolkit = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)
            assertTrue(toolkit.castFaceDown(listOf(f.id(42))))
            assertEquals(field, f.card(42).containerId)
            assertEquals(BoardPosition(1, 1), f.card(42).placement!!.position)
            assertTrue(f.card(42).isFaceDown)
            assertEquals("2/2", f.card(42).corner)
            assertIs<GameAction.PlayBatch>(f.commands().single().action)
            val commands = f.commands().playMembers()
            assertEquals(2, commands.size)
            assertEquals(1, commands.map { it.invocationId }.distinct().size)

            var tree = GameplayTree(initial)
            val boards = mutableListOf(initial)
            for (command in commands) {
                tree = assertNotNull(HistoryContinuation.change(tree, f.actor, command).tree)
                boards += assertNotNull(tree.stateAt(tree.currentNodeId))
            }
            assertEquals(f.play.board, boards.last().board)
            val onStack = boards[1].board.cards.single { it.id == f.id(42) }
            assertEquals(CombatStackRules.stack(boards[1].board).id, onStack.containerId)
            assertTrue(ManualDisclosure.permittedFaces(boards[1], onStack, f.other, inspecting = true).isEmpty())
            assertNotEquals(boards[1].currentGroup!!.id, boards[2].currentGroup!!.id)
            val playback = HistoryPlayback(HistoryTree.ingest(tree, "accepted"))
            playback.openFrom(initial.currentNodeId)
            playback.next(true, f.other, board = initial.board, definitions = initial.definitions)
            assertEquals(boards[1].currentNodeId, playback.position.cursorId)
            assertTrue(playback.pauseAfterAnimation())

            // One Undo removes both the resolve and the cast: the card is back in the Hand.
            val undo = GameCommand(f.id(990), GameScope(initial.gameId, playId = initial.id), GameAction.Undo, f.id(991), "1")
            val undone = assertNotNull(SelectiveUndo.change(tree, f.actor, undo).tree)
            assertEquals(initial.board, undone.stateAt(undone.currentNodeId)!!.board)
        } finally { f.lobby.close(); f.worker.drain() }
    }

    @Test fun spellsAndAdventureFacesStayOnStackAndManualPermanentDragStillWaits() {
        for ((types, doubleTap) in listOf(listOf("Instant") to true, listOf("Sorcery") to true,
            listOf("Creature", "Instant — Adventure") to true, listOf("Creature", "Sorcery") to true,
            listOf("") to true, listOf("Creature") to false)) {
            val f = BasicTableFixture()
            val definition = f.definition.copy(faces = types.mapIndexed { i, type ->
                f.definition.faces.first().copy(faceId = f.id(800 + i), typeLine = type) })
            val digest = DeckJson.digest(definition)
            val root = f.root.copy(definitions = listOf(SubmittedDefinition(digest, definition)), board = f.root.board.copy(
                cards = f.root.board.cards.map { it.copy(definition = digest, faceId = definition.faces.first().faceId) }))
            f.connect(root)
            try {
                assertTrue(f.move(40, TableDestination.Container(f.container(PlayZone.HAND), PlayZone.HAND)))
                val id = f.id(40)
                assertTrue(f.basic.handle(TableIntent.Move(TableScope(id, listOf(id)),
                    TableDestination.Container(f.container(PlayZone.STACK), PlayZone.STACK),
                    BasicTableController.sources(f.play, listOf(id)), handDoubleTap = doubleTap)))
                assertEquals(1, CombatStackRules.stack(f.play.board).cards.size)
                assertEquals(f.container(PlayZone.HAND), f.card(40).containerId)
            } finally { f.lobby.close(); f.worker.drain() }
        }
    }

    @Test fun normalizedSpellHalfLayoutsStayOnStackAfterHandDoubleTapButPermanentFlipResolves() {
        for ((layout, types) in listOf(
            "adventure" to listOf("Creature — Giant", "Instant — Adventure"),
            "split" to listOf("Instant", "Sorcery"),
            "aftermath" to listOf("Sorcery", "Sorcery — Aftermath"),
            "flip" to listOf("Creature — Human Monk", "Legendary Creature — Spirit"))) {
            val f = BasicTableFixture()
            val definition = PhysicalCardFaces.normalize(f.definition.copy(provider = "mtgjson", layout = layout,
                faces = types.mapIndexed { index, type -> f.definition.faces.first().copy(
                    faceId = f.id(800 + index), name = "Half $index", typeLine = type) }))
            assertEquals(1, definition.faces.size)
            assertEquals(types.first(), definition.faces.single().typeLine)
            assertTrue(types.last() in definition.faces.single().rulesText)
            assertEquals(layout == "flip", StackContexts.permanentOnly(definition), layout)
            val digest = DeckJson.digest(definition)
            val initial = f.root.copy(definitions = listOf(SubmittedDefinition(digest, definition)),
                board = f.root.board.copy(cards = f.root.board.cards.map {
                    it.copy(definition = digest, faceId = definition.faces.single().faceId) }))
            f.connect(initial)
            try {
                assertTrue(f.move(40, TableDestination.Container(f.container(PlayZone.HAND), PlayZone.HAND)))
                val before = f.commands().size
                val toolkit = TableToolkitController(f.actor, { f.play }, { true }, f.lobby::gameplay, playBatch = f.lobby::gameplayBatch)
                val scene = TablePresentation.scene(f.game, f.play, f.actor, 412f, 892f, false)
                val card = scene.items.single { it.cardId == f.id(40) }
                val gestures = TableGestureCoordinator({ if (it is TableIntent.Move) assertTrue(toolkit.handle(it), layout) })
                gestures.update(scene, TableSelection(), true)
                val point = card.visible.first().center
                gestures.down(point, 0); gestures.up(point, 20)
                gestures.down(point, 100); gestures.up(point, 120)
                assertEquals(1, f.commands().drop(before).size)
                val commands = f.commands().drop(before).playMembers()
                assertEquals(if (layout == "flip") 2 else 1, commands.size, layout)
                assertEquals(f.id(40), assertIs<StackAction.AddEffect>(assertIs<PlayAction.Stack>(
                    assertIs<GameAction.Play>(commands.first().action).action).action).sourceId)
                if (layout == "flip") {
                    assertTrue(CombatStackRules.stack(f.play.board).cards.isEmpty())
                    assertEquals(f.container(PlayZone.BATTLEFIELD), f.card(40).containerId)
                } else {
                    assertEquals(1, CombatStackRules.stack(f.play.board).cards.size, layout)
                    assertEquals(f.id(40), f.play.board.stackEffects.single().sourceId)
                    assertEquals(f.container(PlayZone.HAND), f.card(40).containerId)
                }
            } finally { f.lobby.close(); f.worker.drain() }
        }
    }
}
