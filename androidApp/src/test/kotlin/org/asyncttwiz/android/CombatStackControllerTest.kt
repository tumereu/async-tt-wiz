package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

internal fun combatId(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
internal fun combatState(): PlayState {
    val cards = (20..25).map { n -> PlayCard(combatId(n), "definition", combatId(7), combatId(10), combatId(11),
        covered = n == 25, corner = "1/2", note = "source", token = n == 25,
        placement = PlayPlacement(BoardPosition((n - 20) % 2, (n - 20) / 2), combatId(n + 100))) }
    return PlayState(combatId(1), combatId(2), combatId(3), combatId(4), PlayRoot(combatId(5), combatId(6)),
        BoardState(listOf(PlayPlayer(combatId(7)), PlayPlayer(combatId(8))), cards,
            listOf(PlayContainer(combatId(10), PlayZone.BATTLEFIELD, combatId(7), cards.map { it.id }),
                PlayContainer(combatId(12), PlayZone.STACK), PlayContainer(combatId(13), PlayZone.GRAVEYARD, combatId(7))),
            TurnState(combatId(7), phase = "COMBAT", step = "DECLARE_ATTACKERS")),
        listOf(SubmittedDefinition("definition", CardDefinition("Sample", listOf(DefinitionFace(combatId(11), "Sample", typeLine = "Creature — Example"))))),
        emptyList(), InitialStarter(StarterChoice(combatId(7)), combatId(7)))
}

/** The table's combat selection helpers ([CombatStackController]) on lanes staged as the table stages them ([CombatBoards]). */
class CombatStackControllerTest {
    private val actor = combatId(8)
    private fun targets(state: PlayState, vararg source: Int) = source.map { CombatStackController.bind(state.board, combatId(it)) }

    // CMB-004
    @Test fun removeFromCombatLeavesTheRealCardAndGraveyardMovesTheSourceToItsOwner() {
        val initial = combatState().let { it.copy(board = it.board.copy(
            cards = it.board.cards.map { card -> if (card.id == combatId(20)) card.copy(tapped = true) else card })) }
        val boards = CombatBoards(actor)
        val declared = boards.declare(initial, targets(initial, 20), false)
        val occurrence = CombatStackRules.occurrences(declared.board.combat).single()
        val removed = boards.remove(declared, listOf(CombatStackController.bind(declared.board, occurrence.id)))
        assertEquals(declared.board.cards, removed.board.cards)
        assertTrue(removed.board.cards.single { it.id == combatId(20) }.tapped)
        assertTrue(CombatStackRules.occurrences(removed.board.combat).isEmpty())
        val action = CombatStackController.graveyard(declared.board, occurrence.id)
        assertEquals(combatId(20), action.cardId)
        assertEquals(combatId(13), action.destination.containerId) // Owner, not the acting player.
    }

    @Test fun aliasesBindToTheirSourceAndUnknownTargetsAreRefused() {
        val state = combatState()
        val effect = StackEffect(combatId(600), combatId(20))
        val stacked = state.copy(board = state.board.copy(stackEffects = listOf(effect),
            containers = state.board.containers.map { if (it.zone == PlayZone.STACK) it.copy(cards = listOf(effect.id)) else it }))
        assertEquals(combatId(20), CombatStackController.bind(stacked.board, effect.id).sourceId)
        assertEquals(listOf(combatId(20)), CombatStackController.sources(listOf(CombatStackController.bind(stacked.board, combatId(20)),
            CombatStackController.bind(stacked.board, effect.id))))
        assertFailsWith<IllegalArgumentException> { CombatStackController.bind(state.board, combatId(9999)) }
    }

    @Test fun collectionPlacementUsesVisualOrderOneTargetAndPrefixAnchors() {
        val boards = CombatBoards(actor)
        var state = boards.declare(combatState(), targets(combatState(), 20, 21, 22), false)
        val original = state
        val area = state.board.combat!!
        val target = area.pairs.last()
        state = boards.place(state, listOf(combatId(24), combatId(21), combatId(20)), CombatDestination(area.id, CombatRole.BLOCKER, target.id))
        assertEquals(listOf(combatId(20), combatId(21), combatId(24)), state.board.combat!!.pairs.last().blockers.map { it.sourceId })
        assertEquals(original.board.cards, state.board.cards)
        assertEquals(1, state.board.combat!!.pairs.size)
    }

    @Test fun withinSlotSubsetInsertionDoesNotReverse() {
        val boards = CombatBoards(actor)
        var state = boards.apply(combatState(), PlayAction.Combat(CombatAction.Open))
        val areaId = state.board.combat!!.id
        state = boards.place(state, (20..23).map(::combatId), CombatDestination(areaId, CombatRole.ATTACKER))
        val pair = state.board.combat!!.pairs.single()
        val a = pair.attackers[0]; val c = pair.attackers[2]
        state = boards.place(state, listOf(pair.attackers[3].id, pair.attackers[1].id),
            CombatDestination(areaId, CombatRole.ATTACKER, pair.id, insertion = CombatInsertion.GAP, cardGap = IdentityGap(a.id, c.id)))
        assertEquals(listOf(combatId(20), combatId(21), combatId(23), combatId(22)), state.board.combat!!.pairs.single().attackers.map { it.sourceId })
        val unchanged = CombatBoards(actor)
        assertEquals(state, unchanged.place(state, listOf(combatId(21), combatId(23)),
            CombatDestination(areaId, CombatRole.ATTACKER, pair.id, insertion = CombatInsertion.GAP, cardGap = IdentityGap(a.id, c.id))))
        assertTrue(unchanged.applied.isEmpty())
    }

    @Test fun crossSlotOrderBlockersBeforeAttackersNotSelectionOrPairOrder() {
        val boards = CombatBoards(actor)
        var state = boards.declare(combatState(), targets(combatState(), 20, 21), false)
        val area = state.board.combat!!
        state = boards.place(state, listOf(combatId(22)), CombatDestination(area.id, CombatRole.BLOCKER, area.pairs.last().id))
        val occurrences = CombatStackRules.occurrences(state.board.combat)
        val ordered = CombatStackController.orderedTargets(state.board, occurrences.map { it.id }.reversed())
        assertEquals(listOf(combatId(22), combatId(20), combatId(21)), ordered.map { it.sourceId })
        val target = CombatDestination(area.id, CombatRole.ATTACKER, state.board.combat!!.pairs.first().id)
        val accepted = boards.place(state, occurrences.map { it.id }.reversed(), target)
        assertEquals(listOf(combatId(22), combatId(20), combatId(21)), accepted.board.combat!!.pairs.single().attackers.map { it.sourceId })
    }

    @Test fun invalidWholeDropIsRefused() {
        val boards = CombatBoards(actor)
        val state = boards.declare(combatState(), targets(combatState(), 20), false)
        assertFailsWith<IllegalArgumentException> {
            boards.place(state, listOf(combatId(21), combatId(999)),
                CombatDestination(state.board.combat!!.id, CombatRole.BLOCKER, state.board.combat!!.pairs.single().id))
        }
    }

    @Test fun frozenEffectsHaveNoSourceButKeepInspection() {
        val state = combatState()
        val token = state.board.cards.single { it.id == combatId(25) }.copy(corner = "5/2")
        val effect = StackEffect(combatId(600), token.id, token)
        val frozen = state.copy(board = state.board.copy(cards = state.board.cards.filterNot { it.id == token.id },
            stackEffects = listOf(effect), containers = state.board.containers.map { when (it.zone) {
                PlayZone.STACK -> it.copy(cards = listOf(effect.id))
                else -> it.copy(cards = it.cards - token.id)
            } }))
        val target = CombatStackController.bind(frozen.board, effect.id)
        assertNull(target.sourceId)
        assertEquals(token, CombatStackRules.inspection(frozen.board, effect.id))
        assertTrue(CombatStackController.sources(listOf(target)).isEmpty())
    }
}
