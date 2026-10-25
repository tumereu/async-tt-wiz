package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlin.test.*

class EventAnnotationsTest {
    @Test fun actualRetainedPingRegistryDistinguishesSourceCombatOccurrenceStackEffectAndArea() {
        val before = state().let { root -> root.copy(board = root.board.copy(
            containers = listOf(root.board.containers.single().copy(zone = PlayZone.BATTLEFIELD),
                PlayContainer(id(60), PlayZone.STACK, cards = listOf(id(61)))),
            stackEffects = listOf(StackEffect(id(61), id(9))),
            combat = CombatArea(id(62), listOf(CombatPair(id(63), attackers = listOf(CombatOccurrence(id(64), id(9)))))))) }
        val targets = listOf(PingTarget(PingTargetKind.CARD, id(9)), PingTarget(PingTargetKind.OCCURRENCE, id(64)),
            PingTarget(PingTargetKind.OCCURRENCE, id(61)), PingTarget(PingTargetKind.AREA, id(62)))
        assertTrue(RetainedAnnotationContext.from(before, "retained").targets.containsAll(targets))
        var current = before
        targets.forEachIndexed { index, target ->
            val result = change(current, PlayAction.Ping(rootRef, target), 200 + index, references = resolver(before))
            assertEquals("accepted", result.outcome, result.reason)
            assertEquals(target, result.mutation!!.transition.annotation!!.target)
            current = result.state!!
        }
        assertEquals("missingReference", change(current,
            PlayAction.Ping(rootRef, PingTarget(PingTargetKind.OCCURRENCE, id(9))), 210, references = resolver(before)).reason)
    }
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun state() = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
        BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))),
            listOf(PlayCard(id(9), "private-definition", id(7), id(10), id(11),
                placement = PlayPlacement(BoardPosition(0, 0), id(40)))),
            listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, id(7), listOf(id(9)))), TurnState(id(7))),
        emptyList(), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
    private val rootRef = EventReference(id(2), id(5))
    private val x = PingTarget(PingTargetKind.CARD, id(9))
    private val y = PingTarget(PingTargetKind.PLAYER, id(8))
    private fun command(action: PlayAction, n: Int) = GameCommand(id(n), GameScope(id(1), playId = id(2)), GameAction.Play(action), id(90), "1")
    private fun resolver(vararg states: PlayState) = EventReferenceResolver { _, _, reference ->
        states.singleOrNull { reference == EventReference(it.id, it.currentNodeId) }?.let { RetainedAnnotationContext.from(it, "original-context-${it.currentNodeId}") }
    }
    private fun change(state: PlayState, action: PlayAction, n: Int, actor: String = id(7), references: EventReferenceResolver = resolver(state())) =
        GameplayRules.change(state, actor, command(action, n), references)

    @Test fun gm20NumbersPresenceRemovalReaddAndDeliveryRetry() {
        var current = state()
        val first = change(current, PlayAction.Ping(rootRef, x), 20); current = first.state!!
        val group = current.currentGroup!!.id
        val second = change(current, PlayAction.Ping(rootRef, y), 21); current = second.state!!
        current = change(current, PlayAction.Unping(rootRef, x), 22).state!!
        assertEquals(listOf("2"), EventAnnotations.activePings(current.annotations, group).map { it.to!!.ordinal })
        current = change(current, PlayAction.Ping(rootRef, x), 23).state!!
        assertEquals(listOf("2", "3"), EventAnnotations.activePings(current.annotations, group).map { it.to!!.ordinal })
        assertEquals("noOp", change(current, PlayAction.Ping(rootRef, x), 23).outcome)
        assertEquals("noOp", change(current, PlayAction.Ping(rootRef, x), 24).outcome)
        assertEquals("noOp", change(current, PlayAction.Unping(rootRef, PingTarget(PingTargetKind.PLAYER, id(7))), 25).outcome)
        assertEquals(4, current.annotations.pings.size)
        assertEquals(first, GameplayHistory.replay(state(), first.mutation!!, resolver(state())))
    }

    @Test fun gm03And05NewTapStartsFreshOwningContextWithoutBackwardUnping() {
        val edit = change(state(), PlayAction.SetLife(id(7), "21"), 20).state!!
        val first = change(edit, PlayAction.Ping(rootRef, x), 21).state!!
        val firstGroup = first.currentGroup!!.id
        val tapped = change(first, PlayAction.SetTapped(id(9), true), 22).state!!
        val newGroup = tapped.currentGroup!!.id
        assertNotEquals(firstGroup, newGroup)
        val absent = change(tapped, PlayAction.Unping(rootRef, x), 23)
        assertEquals("noOp", absent.outcome)
        assertEquals(tapped, absent.state)
        val second = change(tapped, PlayAction.Ping(rootRef, x), 24).state!!
        assertEquals(newGroup, second.currentGroup!!.id)
        assertEquals("1", EventAnnotations.activePings(second.annotations, newGroup).single().to!!.ordinal)
        assertEquals("1", EventAnnotations.activePings(second.annotations, firstGroup).single().to!!.ordinal)
        val anchor = change(second, PlayAction.SetLife(id(7), "22"), 25).state!!
        assertEquals(newGroup, anchor.currentGroup!!.id)
        assertEquals("noOp", change(state(), PlayAction.Unping(rootRef, x), 26).outcome)
    }

    @Test fun gm21HistoricalInactiveReferenceRecordsHereNeverInsertsOrExposesThere() {
        val root = state()
        val inactive = root.copy(currentNodeId = id(50), board = root.board.copy(cards = root.board.cards.map { it.copy(note = "inactive private text") }))
        val current = change(root, PlayAction.SetLife(id(7), "22"), 20).state!!
        val reference = EventReference(root.id, inactive.currentNodeId)
        val commentAction = PlayAction.EventComment(reference, "  hello\nthere  ")
        val result = change(current, commentAction, 21, references = resolver(root, inactive))
        assertEquals("accepted", result.outcome)
        assertEquals(current.currentNodeId, result.mutation!!.parentId)
        assertEquals(current.root.branchId, result.mutation.branchId)
        assertEquals(current.currentGroup!!.id, result.state!!.currentGroup!!.id)
        assertEquals(current.board, result.state.board)
        val comment = result.state.annotations.comments.single()
        assertEquals(reference, comment.reference.reference)
        assertEquals(commentAction.text, comment.text)
        assertEquals(current.currentNodeId, comment.recording.parentId)
        assertTrue(root.annotations.comments.isEmpty())
        val ping = change(result.state, PlayAction.Ping(reference, x), 22, references = resolver(root, inactive))
        assertEquals("accepted", ping.outcome)
        assertTrue(current.annotations.pings.isEmpty()) // No retroactive overlay at G or E.
        val encoded = GameWire.json.encodeToString(ping.mutation)
        assertFalse("private-definition" in encoded)
        assertFalse("inactive private text" in encoded)
        assertEquals("missingReference", change(current, PlayAction.Ping(reference, x), 23).reason)
        assertEquals("missingReference", change(current, PlayAction.Ping(reference.copy(playId = id(99)), x), 23,
            references = resolver(inactive)).reason)
        val other = change(current, commentAction, 24, id(8), resolver(inactive)).state!!
        assertNotEquals(current.currentGroup!!.id, other.currentGroup!!.id)
    }

    @Test fun gm22RelevantTuplesBlockChangedReferencesOwningContextsAndOrdinalReinterpretation() {
        val root = state()
        val tapped = change(root, PlayAction.SetTapped(id(9), true), 20).state!!
        val ping = change(tapped, PlayAction.Ping(rootRef, x), 21)
        // Target identity survives, but the recording/ordinal cannot be left in the removed tap's group.
        assertEquals("replayBlocked", GameplayHistory.replay(root, ping.mutation!!, resolver(root)).reason)
        val changedRoot = root.copy(board = root.board.copy(turn = root.board.turn.copy(number = "2")))
        val revisedLookup = EventReferenceResolver { _, _, _ -> RetainedAnnotationContext.from(changedRoot, "revised-materialization") }
        assertNotNull(GameplayHistory.replay(tapped, ping.mutation, revisedLookup).state)
        val competing = change(tapped, PlayAction.Ping(rootRef, y), 22).state!!
        assertEquals("replayBlocked", GameplayHistory.replay(competing, ping.mutation, resolver(root)).reason) // Ordinal 1 -> 2.
        val comment = change(tapped, PlayAction.EventComment(rootRef, "reply"), 23)
        val unrelated = tapped.copy(board = tapped.board.copy(players = tapped.board.players.map { it.copy(life = "30") }))
        assertEquals(comment.mutation!!.transition, GameplayHistory.replay(unrelated, comment.mutation, resolver(root)).mutation!!.transition)
        assertEquals("replayBlocked", GameplayHistory.replay(tapped, comment.mutation, resolver()).reason)
    }

    @Test fun requestCodecValidationAndNoDraftOrNoopMutation() {
        for (action in listOf<PlayAction>(PlayAction.EventComment(rootRef, "  body\n "), PlayAction.Ping(rootRef, x), PlayAction.Unping(rootRef, y))) {
            val command = command(action, 20)
            assertTrue(GameWire.valid(command))
            assertEquals(command, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(command)))
        }
        assertFalse(GameplayRules.valid(PlayAction.EventComment(rootRef, "\n ")))
        assertFalse(GameplayRules.valid(PlayAction.EventComment(rootRef, "a\u0000b")))
        assertFalse(GameplayRules.valid(PlayAction.Ping(rootRef, PingTarget(PingTargetKind.CARD, id(9), BoardPosition(1,2)))))
        assertEquals("accessDenied", change(state(), PlayAction.Ping(rootRef, x), 20, id(99)).reason)
        assertEquals("missingReference", change(state(), PlayAction.Ping(rootRef, x.copy(id = id(99))), 20).reason)
        val location = change(state(), PlayAction.Ping(rootRef, PingTarget(PingTargetKind.LOCATION, position = BoardPosition(2,3))), 21)
        assertEquals("accepted", location.outcome)
        val root = state(); val context = RetainedAnnotationContext.from(root, "original")
        val wrongResolver = EventReferenceResolver { _, _, _ -> context.copy(context = context.context.copy(reference = rootRef.copy(nodeId = id(99)))) }
        assertEquals("missingReference", change(root, PlayAction.Ping(rootRef, x), 20, references = wrongResolver).reason)
    }

    @Test fun exactVersionedContextIsNeverSilentlySubstitutedWithRevisedSameNode() {
        val original = state(); val reference = rootRef.copy(contextVersion = "7")
        val action = PlayAction.EventComment(reference, "about original version")
        assertTrue(GameplayRules.valid(action))
        assertFalse(GameplayRules.valid(action.copy(reference = reference.copy(contextVersion = "-1"))))
        assertEquals("missingReference", change(original, action, 20, references = EventReferenceResolver.UNAVAILABLE).reason)
        val retained = RetainedAnnotationContext.from(original, "original", "7")
        val lookup = EventReferenceResolver { _, _, requested -> if (requested == reference) retained else null }
        val revised = original.copy(board = original.board.copy(turn = original.board.turn.copy(number = "2")))
        val change = change(revised, action, 20, references = lookup)
        assertEquals(retained.context, change.state!!.annotations.comments.single().reference)
        assertNotEquals(RetainedAnnotationContext.from(revised, "revised", "7").context, retained.context)
    }
}
