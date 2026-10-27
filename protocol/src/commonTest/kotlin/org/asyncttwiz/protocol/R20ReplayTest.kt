package org.asyncttwiz.protocol

import kotlin.test.*

class R20ReplayTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(7)
    private val b = id(8)
    private fun state(): PlayState = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
        BoardState(listOf(PlayPlayer(a), PlayPlayer(b)),
            listOf(PlayCard(id(20), "definition", b, id(10), id(40))),
            listOf(PlayContainer(id(10), PlayZone.HAND, a, listOf(id(20))),
                PlayContainer(id(11), PlayZone.GRAVEYARD, a), PlayContainer(id(12), PlayZone.GRAVEYARD, b)), TurnState(a)),
        emptyList(), emptyList(), InitialStarter(StarterChoice(a), a))
    private fun command(state: PlayState, action: PlayAction) = GameCommand(id(100),
        GameScope(state.gameId, playId = state.id), GameAction.Play(action), id(90), "1")

    /** Seal the old acceptance path without upgrading its behavior or concerned fields. */
    private fun retained(original: PlayState, change: GameplayChange): GameplayMutation {
        val next = assertNotNull(change.state, change.reason)
        val raw = assertNotNull(change.mutation)
        assertEquals(19, raw.transition.rulesVersion)
        val delta = GameplayDeltas.capture(original, next, raw.id)
        val recorded = GameplayHistory.recontextualize(original, raw.copy(schemaVersion = 2,
            transition = raw.transition.copy(delta = delta, concerns = GameplayConcerns.capture(original, next, raw, delta)),
            bookkeeping = GameplayBookkeeping(original.currentGroup, original.currentBranchId, next.currentGroup)))
        val sealed = recorded.copy(transition = recorded.transition.copy(delta = delta.copy(seal = GameplayDeltas.seal(recorded))))
        val bytes = GameWire.json.encodeToString(sealed)
        val decoded = GameWire.json.decodeFromString<GameplayMutation>(bytes)
        assertEquals(sealed, decoded)
        assertEquals(sealed.transition.delta!!.seal, GameplayDeltas.seal(decoded))
        assertEquals(next, GameplayHistory.apply(original, decoded))
        assertEquals(original, GameplayHistory.unapply(next, decoded))
        assertEquals(decoded.transition, GameplayHistory.replay(original, decoded).mutation!!.transition)
        return decoded
    }

    @Test fun retainedRandomMoveStillUsesHandHoldersZoneWithoutNewOwnerDependency() {
        val original = state()
        val action = HandActions.random(original.board, id(10), emptyList(), RandomV1.encodeSeed(ByteArray(32)), HandRandomResult.DISCARD)
        val change = HandRules.change(original, a, command(original, action), action, rulesVersion = 19)
        val event = retained(original, change)
        assertEquals(id(11), change.state!!.board.cards.single().containerId)
        assertFalse(event.transition.concerns!!.fields.any {
            it.field == ConcernField.Entity("card", id(20), listOf("owner")) })
    }

    @Test fun retainedTokenRemovalKeepsItsOldTrayAndDoesNotGainCleanup() {
        val original = state().let { s -> s.copy(board = s.board.copy(cards = s.board.cards.map { it.copy(token = true) },
            contentsDisplays = listOf(ContentsDisplay(id(50), a, id(10), ContentsSelection.ALL, 1, true, listOf(id(20)), panelVersion = 4)))) }
        val action = PlayAction.RemoveToken(id(20))
        val card = original.board.cards.single()
        val change = GameplayRules.record(original, a, command(original, action), original.board.copy(cards = emptyList(),
            containers = original.board.containers.map { it.copy(cards = emptyList()) }),
            GameplayTransition(tokens = listOf(TokenChange(TokenPresence(card, CardLocation(id(10))), null))), rulesVersion = 19)
        val event = retained(original, change)
        assertNull(event.transition.contents)
        assertEquals(original.board.contentsDisplays, change.state!!.board.contentsDisplays)
    }

    @Test fun retainedCombatRemovalKeepsAbsentBlockedFlagAndNewRemovalRemembersLegacyBlockers() {
        val original = state().let { s -> s.copy(board = s.board.copy(
            cards = listOf(20, 21).map { PlayCard(id(it), "definition", a, id(10), id(40),
                placement = PlayPlacement(BoardPosition(it, 0), id(it + 100))) },
            containers = listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, a, listOf(id(20), id(21)))),
            combat = CombatArea(id(50), listOf(CombatPair(id(51), listOf(CombatOccurrence(id(52), id(20))),
                listOf(CombatOccurrence(id(53), id(21)))))))) }
        val area = original.board.combat!!
        val action = PlayAction.Combat(CombatAction.Remove(area.id, id(53)))
        val candidate = original.board.copy(combat = area.copy(pairs = area.pairs.map { it.copy(blockers = emptyList()) }))
        val old = GameplayRules.record(original, a, command(original, action), candidate, GameplayTransition(), rulesVersion = 19)
        val event = retained(original, old)
        assertFalse(old.state!!.board.combat!!.pairs.single().blocked)
        assertFalse("blocked" in GameWire.json.encodeToString(event))
        assertFalse(GameWire.json.decodeFromString<CombatPair>(GameWire.json.encodeToString(area.pairs.single())).blocked)
        val current = GameplayRules.change(original, a, command(original, action))
        assertTrue(current.state!!.board.combat!!.pairs.single().blocked)
        assertEquals(listOf(CombatBlockedChange(id(51), false, true)), current.mutation!!.transition.combatStack!!.blocked)
        assertEquals(original, GameplayHistory.unapply(current.state, current.mutation))
    }
}
