package org.asyncttwiz.protocol

import kotlin.test.*

/** Rules v35: switching a card's face also sets its corner to the new face's printed value, in the same event. */
class R35FaceCornerTest {
    private val f = UndoFixture
    private val card = f.id(9)
    private val other = f.id(12)

    private fun face(n: Int, typeLine: String, power: String = "", toughness: String = "", loyalty: String = "",
        defense: String = "") = DefinitionFace(f.id(n), "Face $n", typeLine = typeLine, power = power, toughness = toughness,
        loyalty = loyalty, defense = defense)
    private val creature = face(20, "Creature — Human", "2", "3")
    private val werewolf = face(21, "Creature — Werewolf", "4", "5")
    private val planeswalker = face(22, "Legendary Planeswalker — Nissa", loyalty = "3")
    private val battle = face(23, "Battle — Siege", defense = "5")
    private val saga = face(24, "Enchantment — Saga")
    private val land = face(25, "Land")

    /** Card 9 shows [front] on a's Battlefield; card 12 is an unrelated one-faced card. */
    private fun root(front: DefinitionFace, back: DefinitionFace, corner: String = front.defaultCorner(),
        faceDown: Boolean = false): PlayState {
        val definition = CardDefinition("Two faces", listOf(front.copy(otherFaceIds = listOf(back.faceId)),
            back.copy(otherFaceIds = listOf(front.faceId))))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val root = f.root()
        return root.copy(definitions = listOf(submitted), board = root.board.copy(cards = root.board.cards.map {
            if (it.id == card) it.copy(definition = submitted.digest, faceId = front.faceId, corner = corner,
                covered = faceDown, explicitlyCovered = faceDown) else it.copy(covered = false)
        }))
    }
    private fun board(tree: GameplayTree) = assertNotNull(tree.stateAt(tree.currentNodeId)).board
    private fun corner(tree: GameplayTree) = board(tree).cards.single { it.id == card }.corner
    private fun switch(tree: GameplayTree, face: DefinitionFace, n: Int = 100, actor: String = f.a) =
        f.play(tree, actor, PlayAction.SetFace(card, face.faceId), n)

    @Test fun faceCornerRulesAreVersionThirtyFive() {
        assertEquals(35, ManualEditRules.FACE_CORNER_RULES_VERSION)
        assertTrue(ManualEditRules.FACE_CORNER_RULES_VERSION <= CURRENT_GAMEPLAY_RULES_VERSION)
    }

    // TBL-076, TBL-021
    @Test fun switchingEitherWaySetsTheNewFacesPrintedCornerForEachKindOfCard() {
        for ((front, back, printed) in listOf(Triple(creature, werewolf, "4/5"), Triple(creature, planeswalker, "3"),
            Triple(battle, creature, "2/3"), Triple(saga, creature, "2/3"), Triple(creature, saga, "1"),
            Triple(creature, land, ""))) {
            // The corner was edited during play; switching replaces the edit with the printed value.
            val start = GameplayTree(root(front, back, corner = "9/9"))
            val switched = switch(start, back)
            assertEquals(printed, corner(switched), "${front.typeLine} -> ${back.typeLine}")
            val event = switched.nodes.getValue(switched.currentNodeId)
            assertEquals(1, switched.nodes.size, "One event")
            assertEquals(CURRENT_GAMEPLAY_RULES_VERSION, event.transition.rulesVersion)
            assertEquals(listOf(GameplayFieldChange(card, GameplayField.FACE, front.faceId, back.faceId),
                GameplayFieldChange(card, GameplayField.CORNER, "9/9", printed)), event.transition.fields)
            assertEquals(HistoryIcon.FLIP_CAMERA_ANDROID, event.grouping!!.icon)
            val back2 = switch(switched, front, 101)
            assertEquals(front.defaultCorner(), corner(back2), "${back.typeLine} -> ${front.typeLine}")
            // The recorded event replays exactly from its decoded form.
            val decoded = GameWire.json.decodeFromString<GameplayMutation>(GameWire.json.encodeToString(event))
            assertEquals(switched.stateAt(switched.currentNodeId), GameplayHistory.apply(start.initial, decoded))
        }
        assertEquals(listOf("4/5", "3", "5", "1", ""), listOf(werewolf, planeswalker, battle, saga, land).map { it.defaultCorner() })
    }

    // TBL-076
    @Test fun oneUndoTakesBackBothTheFaceAndTheCorner() {
        val start = GameplayTree(root(creature, werewolf, corner = "7/7"))
        val switched = switch(start, werewolf)
        val undone = assertNotNull(f.undo(switched).tree)
        assertEquals(board(start), board(undone))
        val redone = assertNotNull(f.redo(undone).tree)
        assertEquals(board(switched), board(redone))
    }

    // TBL-076: the corner is assigned even when it already shows the printed value, so an earlier corner edit
    // underneath cannot be taken back while the switch stands on it.
    @Test fun anUnchangedCornerIsStillTheSwitchsOwnValue() {
        val start = GameplayTree(root(creature, werewolf))
        val edited = f.play(start, f.a, PlayAction.SetCorner(card, "4/5"), 100)
        val switched = switch(edited, werewolf, 101, actor = f.b)
        assertEquals(listOf(GameplayField.FACE), switched.nodes.getValue(switched.currentNodeId).transition.fields.map { it.field })
        assertNull(f.undo(switched).tree)
        assertEquals("4/5", corner(switched))
        // An independent earlier edit still undoes beneath the switch.
        val tapped = f.play(start, f.a, PlayAction.SetTapped(other, true), 100)
        val independent = switch(tapped, werewolf, 101, actor = f.b)
        val undone = assertNotNull(f.undo(independent).tree, "Unrelated edit undoes")
        assertEquals("4/5", corner(undone))
        assertEquals(werewolf.faceId, board(undone).cards.single { it.id == card }.faceId)
    }

    // TBL-076: a face-down card keeps its corner (the public face-down 2/2 on the Battlefield).
    @Test fun faceDownCardsKeepTheirCorner() {
        val start = GameplayTree(root(creature, werewolf, corner = "2/2", faceDown = true))
        val switched = switch(start, werewolf)
        assertEquals("2/2", corner(switched))
        assertEquals(listOf(GameplayField.FACE), switched.nodes.getValue(switched.currentNodeId).transition.fields.map { it.field })
        // Turning it up still shows the shown face's printed value (rules v23).
        val up = f.play(switched, f.a, PlayAction.SetCovered(card, false), 101)
        assertEquals("4/5", corner(up))
    }

    // SYS-031
    @Test fun olderRulesSwitchOnlyTheFaceAndTheirEventsReplayUnchanged() {
        val start = root(creature, werewolf, corner = "9/9")
        val action = PlayAction.SetFace(card, werewolf.faceId)
        val command = f.command(GameAction.Play(action), 100)
        val old = ManualEditRules.change(start, f.a, command, action, rulesVersion = ManualEditRules.FACE_CORNER_RULES_VERSION - 1)
        val mutation = assertNotNull(old.mutation, old.reason)
        assertEquals(ManualEditRules.FACE_CORNER_RULES_VERSION - 1, mutation.transition.rulesVersion)
        assertEquals(listOf(GameplayFieldChange(card, GameplayField.FACE, creature.faceId, werewolf.faceId)), mutation.transition.fields)
        val next = assertNotNull(old.state)
        assertEquals("9/9", next.board.cards.single { it.id == card }.corner)
        // Sealed as the older app recorded it: it applies, reverses and replays exactly, corner untouched.
        val delta = GameplayDeltas.capture(start, next, mutation.id)
        val recorded = GameplayHistory.recontextualize(start, mutation.copy(schemaVersion = 2,
            transition = mutation.transition.copy(delta = delta, concerns = GameplayConcerns.capture(start, next, mutation, delta)),
            bookkeeping = GameplayBookkeeping(start.currentGroup, start.currentBranchId, next.currentGroup)))
        val sealed = recorded.copy(transition = recorded.transition.copy(delta = delta.copy(seal = GameplayDeltas.seal(recorded))))
        val decoded = GameWire.json.decodeFromString<GameplayMutation>(GameWire.json.encodeToString(sealed))
        assertEquals(next, GameplayHistory.apply(start, decoded))
        assertEquals(start, GameplayHistory.unapply(next, decoded))
        val replayed = GameplayHistory.replay(start, decoded)
        assertEquals(decoded.transition, replayed.mutation!!.transition)
        assertEquals("9/9", replayed.state!!.board.cards.single { it.id == card }.corner)
        // The same switch under the current rules sets the printed corner.
        assertEquals("4/5", ManualEditRules.change(start, f.a, command, action).state!!.board.cards.single { it.id == card }.corner)
    }
}
