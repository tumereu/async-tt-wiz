package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

internal fun combatId(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
internal fun combatState(): PlayState {
    val cards = (20..25).map { n -> PlayCard(combatId(n), "definition", combatId(7), combatId(10), combatId(11),
        covered = n == 25, corner = "1/2", note = "source", token = n == 25,
        placement = PlayPlacement(BoardPosition((n - 20) % 2, (n - 20) / 2), combatId(n + 100))) }
    return PlayState(combatId(1), combatId(2), combatId(3), combatId(4), PlayRoot(combatId(5), combatId(6)), combatId(5),
        BoardState(listOf(PlayPlayer(combatId(7)), PlayPlayer(combatId(8))), cards,
            listOf(PlayContainer(combatId(10), PlayZone.BATTLEFIELD, combatId(7), cards.map { it.id }),
                PlayContainer(combatId(12), PlayZone.STACK), PlayContainer(combatId(13), PlayZone.GRAVEYARD, combatId(7))),
            TurnState(combatId(7), phase = "COMBAT", step = "DECLARE_ATTACKERS")),
        listOf(SubmittedDefinition("definition", CardDefinition("Sample", listOf(DefinitionFace(combatId(11), "Sample", typeLine = "Creature — Example"))))),
        emptyList(), InitialStarter(StarterChoice(combatId(7)), combatId(7)))
}
internal class CombatCommands {
    private var next = 1000
    fun command(action: PlayAction) = GameCommand(combatId(next++), GameScope(combatId(1), playId = combatId(2)),
        GameAction.Play(action), combatId(9), "1", invocationId = combatId(900))
}

class CombatStackControllerTest {
    private val actor = combatId(8)
    private fun targets(state: PlayState, vararg source: Int) = source.map { CombatStackController.bind(state.board, combatId(it)) }
    private fun replay(initial: PlayState, plan: CombatStackPlan): PlayState {
        var accepted = initial
        val seen = mutableListOf<String>()
        plan.dispatch { command ->
            seen += command.requestId
            val change = GameplayRules.change(accepted, actor, command)
            assertEquals("accepted", change.outcome)
            assertEquals(change, GameplayHistory.replay(accepted, change.mutation!!))
            accepted = change.state!!
        }
        assertEquals(plan.commands.map { it.requestId }, seen)
        assertEquals(plan.prediction, accepted)
        return accepted
    }

    @Test fun combinedDeclarationHasIndividualNodesAndDoesNotForceUntap() {
        val state = combatState().let { it.copy(board = it.board.copy(cards = it.board.cards.map { card ->
            if (card.id == combatId(21)) card.copy(tapped = true) else card
        })) }
        val commands = CombatCommands()
        val plan = CombatStackController.declare(state, actor, targets(state, 20, 21), true, commands::command)
        // Area, two occurrences, only the source which actually needs tapping.
        assertEquals(4, plan.commands.size)
        assertTrue((plan.commands[0].action as GameAction.Play).action is PlayAction.Combat)
        val accepted = replay(state, plan)
        assertEquals(2, accepted.board.combat!!.pairs.size)
        assertEquals(state.board.cards.map { it.placement }, accepted.board.cards.map { it.placement })
        assertTrue(accepted.board.cards.filter { it.id in setOf(combatId(20), combatId(21)) }.all { it.tapped })
        val repeated = CombatStackController.declare(accepted, actor, targets(accepted, 20, 21), false, commands::command)
        assertTrue(repeated.commands.isEmpty())
        assertEquals(accepted, repeated.prediction)
    }

    @Test fun sourceAliasesBindOnceAndEffectsRemoveIndividually() {
        val initial = combatState()
        val commands = CombatCommands()
        var state = replay(initial, CombatStackController.addEffects(initial, actor, targets(initial, 20), commands::command))
        state = replay(state, CombatStackController.addEffects(state, actor, targets(state, 20), commands::command))
        val targets = listOf(CombatStackController.bind(state.board, combatId(20))) + state.board.stackEffects.map {
            CombatStackController.bind(state.board, it.id)
        }
        val tapped = CombatStackController.sourceActions(state, actor, targets, commands::command) { PlayAction.SetTapped(it, true) }
        assertEquals(1, tapped.commands.size)
        state = replay(state, tapped)
        val removed = CombatStackController.remove(state, actor, targets.drop(1), commands::command)
        assertEquals(2, removed.commands.size)
        val accepted = replay(state, removed)
        assertTrue(accepted.board.stackEffects.isEmpty())
        assertTrue(accepted.board.cards.single { it.id == combatId(20) }.tapped)
        val absentSource = state.copy(board = state.board.copy(cards = state.board.cards.filterNot { it.id == combatId(20) }))
        assertTrue(CombatStackController.sourceActions(absentSource, actor, targets, commands::command) {
            PlayAction.SetTapped(it, false)
        }.commands.isEmpty())
        assertFailsWith<IllegalArgumentException> { CombatStackController.bind(state.board, combatId(9999)) }
    }

    @Test fun queuedEffectsUsePredictedSemanticPredecessorNotAcknowledgementGate() {
        val state = combatState()
        val commands = CombatCommands()
        val plan = CombatStackController.addEffects(state, actor, targets(state, 20, 21, 22), commands::command)
        assertEquals(3, plan.commands.size)
        val expected = plan.commands.map { (((it.action as GameAction.Play).action as PlayAction.Stack).action as StackAction.AddEffect).expectedStackDigest }
        assertEquals(3, expected.distinct().size)
        replay(state, plan)
    }

    @Test fun collectionPlacementUsesVisualOrderOneTargetAndPrefixAnchors() {
        val commands = CombatCommands()
        var state = combatState()
        state = replay(state, CombatStackController.declare(state, actor, targets(state, 20, 21, 22), false, commands::command))
        val original = state
        val area = state.board.combat!!
        val target = area.pairs.last()
        val plan = CombatStackController.place(state, actor, listOf(combatId(24), combatId(21), combatId(20)),
            CombatDestination(area.id, CombatRole.BLOCKER, target.id), commands::command)
        state = replay(state, plan)
        assertEquals(listOf(combatId(20), combatId(21), combatId(24)), state.board.combat!!.pairs.last().blockers.map { it.sourceId })
        assertEquals(original.board.cards, state.board.cards)
        assertEquals(1, state.board.combat!!.pairs.size)
        val second = (((plan.commands[1].action as GameAction.Play).action as PlayAction.Combat).action as CombatAction.Place)
        val prefix = CommandIds.uuidV5(plan.commands[0].requestId, "combat-occurrence/0")
        // First source already had an occurrence: the prefix is that original identity, not a new ID.
        assertNotEquals(prefix, second.destination.cardGap.afterId)
        assertEquals(area.pairs.first().attackers.single().id, second.destination.cardGap.afterId)
    }

    @Test fun withinSlotSubsetInsertionDoesNotReverseAndUnchangedLayoutEmitsNothing() {
        val commands = CombatCommands()
        var state = combatState()
        state = replay(state, CombatStackController.single(state, actor, PlayAction.Combat(CombatAction.Open), commands::command))
        val areaId = state.board.combat!!.id
        state = replay(state, CombatStackController.place(state, actor, (20..23).map(::combatId),
            CombatDestination(areaId, CombatRole.ATTACKER), commands::command))
        val pair = state.board.combat!!.pairs.single()
        val a = pair.attackers[0]; val c = pair.attackers[2]
        val plan = CombatStackController.place(state, actor, listOf(pair.attackers[3].id, pair.attackers[1].id),
            CombatDestination(areaId, CombatRole.ATTACKER, pair.id, insertion = CombatInsertion.GAP,
                cardGap = IdentityGap(a.id, c.id)), commands::command)
        assertEquals(1, plan.commands.size) // B already at the required position anchors the next member.
        state = replay(state, plan)
        assertEquals(listOf(combatId(20), combatId(21), combatId(23), combatId(22)), state.board.combat!!.pairs.single().attackers.map { it.sourceId })
        val same = CombatStackController.place(state, actor, listOf(combatId(21), combatId(23)),
            CombatDestination(areaId, CombatRole.ATTACKER, pair.id, insertion = CombatInsertion.GAP,
                cardGap = IdentityGap(a.id, c.id)), commands::command)
        assertTrue(same.commands.isEmpty())
    }

    @Test fun crossSlotOrderBlockersBeforeAttackersNotSelectionOrPairOrder() {
        val commands = CombatCommands()
        var state = combatState()
        state = replay(state, CombatStackController.declare(state, actor, targets(state, 20, 21), false, commands::command))
        val area = state.board.combat!!
        state = replay(state, CombatStackController.place(state, actor, listOf(combatId(22)),
            CombatDestination(area.id, CombatRole.BLOCKER, area.pairs.last().id), commands::command))
        val occurrences = CombatStackRules.occurrences(state.board.combat)
        val ordered = CombatStackController.orderedTargets(state.board, occurrences.map { it.id }.reversed())
        assertEquals(listOf(combatId(22), combatId(20), combatId(21)), ordered.map { it.sourceId })
        val target = CombatDestination(area.id, CombatRole.ATTACKER, state.board.combat!!.pairs.first().id)
        val plan = CombatStackController.place(state, actor, occurrences.map { it.id }.reversed(), target, commands::command)
        val accepted = replay(state, plan)
        assertEquals(listOf(combatId(22), combatId(20), combatId(21)), accepted.board.combat!!.pairs.single().attackers.map { it.sourceId })
    }

    @Test fun invalidWholeDropNeverDispatchesAndStalePrefixRejectsWithoutRedirect() {
        val commands = CombatCommands()
        var state = combatState()
        state = replay(state, CombatStackController.declare(state, actor, targets(state, 20), false, commands::command))
        assertFailsWith<IllegalArgumentException> {
            CombatStackController.place(state, actor, listOf(combatId(21), combatId(999)),
                CombatDestination(state.board.combat!!.id, CombatRole.BLOCKER, state.board.combat!!.pairs.single().id), commands::command)
        }
        val plan = CombatStackController.place(state, actor, listOf(combatId(21), combatId(22)),
            CombatDestination(state.board.combat!!.id, CombatRole.BLOCKER, state.board.combat!!.pairs.single().id), commands::command)
        val first = GameplayRules.change(state, actor, plan.commands.first()).state!!
        val prefix = first.board.combat!!.pairs.single().blockers.single().id
        val remove = commands.command(PlayAction.Combat(CombatAction.Remove(first.board.combat!!.id, prefix)))
        val opponent = GameplayRules.change(first, combatId(7), remove).state!!
        assertEquals("destinationChanged", GameplayRules.change(opponent, actor, plan.commands.last()).reason)
        assertEquals(listOf(combatId(20)), CombatStackRules.occurrences(opponent.board.combat).map { it.sourceId })
    }

    @Test fun frozenEffectsHaveNoSourceActionsButKeepRemovalAndInspection() {
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
        val commands = CombatCommands()
        assertTrue(CombatStackController.sourceActions(frozen, actor, listOf(target), commands::command) {
            PlayAction.SetTapped(it, false)
        }.commands.isEmpty())
        assertTrue(replay(frozen, CombatStackController.remove(frozen, actor, listOf(target), commands::command)).board.stackEffects.isEmpty())
    }

    @Test fun quickVisibilityIsNotFullActionPhaseRestriction() {
        val turn = combatState().board.turn
        assertTrue(CombatOfferings.declare(turn, true))
        assertFalse(CombatOfferings.declare(turn, false))
        for (step in listOf("BEGINNING_OF_COMBAT", "DECLARE_BLOCKERS", "COMBAT_DAMAGE", "END_OF_COMBAT")) {
            assertFalse(CombatOfferings.declare(turn.copy(step = step), true))
            assertEquals(step == "END_OF_COMBAT", CombatOfferings.advanceAndClose(turn.copy(step = step)))
        }
        val outside = combatState().let { it.copy(board = it.board.copy(turn = turn.copy(phase = "ENDING", step = "CLEANUP"))) }
        val commands = CombatCommands()
        assertEquals(3, CombatStackController.declare(outside, actor, targets(outside, 20), true, commands::command).commands.size)
    }
}
