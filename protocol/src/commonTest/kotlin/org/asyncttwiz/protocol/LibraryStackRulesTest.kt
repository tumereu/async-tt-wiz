package org.asyncttwiz.protocol

import kotlin.test.*

class LibraryStackRulesTest {
    private val f = UndoFixture
    private fun state(zone: PlayZone): PlayState = f.root().let { root -> root.copy(board = root.board.copy(
        cards = root.board.cards.map { it.copy(placement = null, covered = true) },
        containers = listOf(root.board.containers.single().copy(zone = zone), PlayContainer(f.id(50), PlayZone.STACK)))) }
    private fun command(state: PlayState) = f.command(GameAction.Play(PlayAction.Stack(
        StackAction.AddEffect(f.id(9), GameplayRules.stackDigest(state)))), 100)

    @Test fun libraryStackUsesV21ExistingEventAndExactRecordedReplayUndo() {
        val original = state(PlayZone.LIBRARY)
        assertFalse(CombatStackRules.canProject(original.board, original.board.cards.first(), 20))
        assertTrue(CombatStackRules.canProject(original.board, original.board.cards.first(), 21))
        assertEquals("inapplicable", CombatStackRules.change(original, f.a, command(original), rulesVersion = 20).reason)
        val change = GameplayRules.change(original, f.a, command(original))
        val next = assertNotNull(change.state, change.reason)
        val event = assertNotNull(change.mutation)
        assertEquals(CURRENT_GAMEPLAY_RULES_VERSION, event.transition.rulesVersion)
        assertEquals(original.board.cards, next.board.cards)
        assertEquals(f.id(9), next.board.stackEffects.single().sourceId)
        assertTrue(next.board.stackEffects.single().revealsLibrarySource)
        assertTrue(CardVisibility.isVisibleFaceUp(next.board, next.board.cards.first(), f.b))
        assertEquals(original.board.containers.first(), next.board.containers.first())
        assertTrue(event.transition.concerns!!.fields.any { it.field == ConcernField.Entity("card", f.id(9), listOf("containerId")) })
        val decoded = GameWire.json.decodeFromString<GameplayMutation>(GameWire.json.encodeToString(event))
        assertEquals(next, GameplayHistory.apply(original, decoded))
        assertEquals(original, GameplayHistory.unapply(next, decoded))
        assertEquals(change, GameplayHistory.replay(original, decoded))
        val movedSource = original.copy(board = original.board.copy(cards = original.board.cards.map {
            if (it.id == f.id(9)) it.copy(containerId = f.id(50)) else it }))
        assertNull(GameplayHistory.apply(movedSource, decoded))
    }

    @Test fun retainedV20AndV21EventsDoNotUpgradeOnReplay() {
        for ((zone, version) in listOf(PlayZone.GRAVEYARD to 20, PlayZone.EXILE to 20, PlayZone.LIBRARY to 21)) {
            val original = state(zone)
            val change = CombatStackRules.change(original, f.a, command(original), rulesVersion = version)
            val next = assertNotNull(change.state, change.reason)
            val raw = change.mutation!!
            val delta = GameplayDeltas.capture(original, next, raw.id)
            val recorded = GameplayHistory.recontextualize(original, raw.copy(schemaVersion = 2,
                transition = raw.transition.copy(delta = delta, concerns = GameplayConcerns.capture(original, next, raw, delta)),
                bookkeeping = GameplayBookkeeping(original.currentGroup, original.currentBranchId, next.currentGroup)))
            val sealed = recorded.copy(transition = recorded.transition.copy(delta = delta.copy(seal = GameplayDeltas.seal(recorded))))
            val event = GameWire.json.decodeFromString<GameplayMutation>(GameWire.json.encodeToString(sealed))
            assertEquals(version, event.transition.rulesVersion)
            assertEquals(version >= 21, next.board.stackEffects.single().revealsLibrarySource)
            assertEquals(version >= 21, "revealsLibrarySource" in GameWire.json.encodeToString(event))
            assertEquals(next, GameplayHistory.apply(original, event))
            assertEquals(original, GameplayHistory.unapply(next, event))
            assertEquals(event.transition, GameplayHistory.replay(original, event).mutation!!.transition)
        }
    }

    @Test fun copyingLibraryEntryRetainsDisclosureUntilLastEntryIsRemovedAndUndoRestoresIt() {
        val original = state(PlayZone.LIBRARY)
        val first = GameplayRules.change(original, f.a, command(original)).state!!
        val copied = GameplayRules.change(first, f.a, f.command(GameAction.Play(PlayAction.Stack(
            StackAction.CopyEffect(first.board.stackEffects.single().id, GameplayRules.stackDigest(first)))), 101))
        val next = assertNotNull(copied.state, copied.reason)
        assertTrue(next.board.stackEffects.all { it.revealsLibrarySource })
        assertEquals(copied, GameplayHistory.replay(first, copied.mutation!!))
        var current = next
        for ((index, effect) in next.board.stackEffects.withIndex()) {
            val removed = GameplayRules.change(current, f.a, f.command(GameAction.Play(PlayAction.Stack(
                StackAction.RemoveEffect(effect.id))), 102 + index))
            val after = assertNotNull(removed.state, removed.reason)
            assertEquals(index == 0, CardVisibility.isVisibleFaceUp(after.board, after.board.cards.first(), f.b))
            assertEquals(current, GameplayHistory.unapply(after, removed.mutation!!))
            current = after
        }
    }

    // TRN-029
    @Test fun nonTurnPlayerCanMulliganOwnHandAtTurnOneUntap() {
        val original = state(PlayZone.LIBRARY).let { root -> root.copy(board = root.board.copy(
            turn = TurnState(f.b), containers = root.board.containers + PlayContainer(f.id(51), PlayZone.HAND, f.a))) }
        val action = PlayAction.Mulligan(f.id(10), f.id(51), emptyList(), 1, RandomV1.encodeSeed(ByteArray(32)))
        val change = GameplayRules.change(original, f.a, f.command(GameAction.Play(action), 101))
        val next = assertNotNull(change.state, change.reason)
        assertEquals(1, next.board.containers.single { it.id == f.id(51) }.cards.size)
        assertEquals(original.board.turn, next.board.turn)
        assertEquals(original, GameplayHistory.unapply(next, change.mutation!!))
    }
}
