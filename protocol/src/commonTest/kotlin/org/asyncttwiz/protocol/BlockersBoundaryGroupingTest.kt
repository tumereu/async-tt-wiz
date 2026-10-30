package org.asyncttwiz.protocol

import kotlin.test.*

/** Rules v29: a step change into Declare blockers ends a history group, so the defender can continue from it. */
class BlockersBoundaryGroupingTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(7)
    private val b = id(8)
    private fun state(position: TurnPosition): PlayState {
        val definition = CardDefinition("Bear", listOf(DefinitionFace(id(40), "Bear", typeLine = "Creature")))
        val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
        val cards = listOf(PlayCard(id(20), submitted.digest, a, id(10), id(40), covered = false,
            placement = PlayPlacement(BoardPosition(0, 0), id(60))),
            PlayCard(id(21), submitted.digest, b, id(11), id(40), covered = false,
                placement = PlayPlacement(BoardPosition(0, 0), id(61))))
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5), BoardState(
            listOf(PlayPlayer(a, life = "20"), PlayPlayer(b, life = "20")), cards,
            listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, a, listOf(id(20))),
                PlayContainer(id(11), PlayZone.BATTLEFIELD, b, listOf(id(21))), PlayContainer(id(12), PlayZone.STACK)),
            TurnState(a, phase = position.phase, step = position.step)),
            listOf(submitted), emptyList(), InitialStarter(StarterChoice(a), a))
    }
    private fun play(action: PlayAction, n: Int) = GameCommand(id(n), GameScope(id(1), playId = id(2)), GameAction.Play(action), id(9), "1")

    private fun step(from: TurnPosition, to: TurnPosition, version: Int) = GameplayTransition(listOf(
        GameplayFieldChange(id(2), GameplayField.PHASE, from.phase, to.phase),
        GameplayFieldChange(id(2), GameplayField.STEP, from.step, to.step)), rulesVersion = version)
    private fun classify(action: PlayAction, from: TurnPosition, to: TurnPosition, version: Int) =
        HistoryGrouping.classify(action, emptyList(), step(from, to, version))
    private fun append(group: GameplayGroup?, semantic: HistorySemantic, n: Int, actor: String = a) =
        HistoryGrouping.append(group, id(n), id(n + 1000), actor, semantic)
    private fun v(version: Int) = GameplayTransition(rulesVersion = version)
    private val declare = PlayAction.Combat(CombatAction.Declare(id(30), id(20)))
    private fun encode(group: GameplayGroup) = GameWire.json.encodeToString(GameplayGroup.serializer(), group)

    @Test fun rulesVersionIs29() {
        assertEquals(29, HistoryGrouping.BLOCKERS_BOUNDARY_RULES_VERSION)
        assertTrue(CURRENT_GAMEPLAY_RULES_VERSION >= HistoryGrouping.BLOCKERS_BOUNDARY_RULES_VERSION)
    }

    @Test fun v28StepIntoDeclareBlockersGroupsExactlyAsBefore() {
        for (action in listOf(PlayAction.NextStep, PlayAction.ChooseStep(TurnPosition.DECLARE_BLOCKERS))) {
            val old = classify(action, TurnPosition.DECLARE_ATTACKERS, TurnPosition.DECLARE_BLOCKERS, 28)
            assertFalse(old.isolated)
            assertFalse(old.endsGroup)
            val json = GameWire.json.encodeToString(HistorySemantic.serializer(), old)
            assertFalse("isolated" in json || "endsGroup" in json, json)
            for (first in listOf(PlayAction.AdjustLife(a, "1"), declare)) {
                val previous = append(null, HistoryGrouping.classify(first, emptyList(), v(28)), 100)
                // v28: the turn player's step change joins their preceding group, and later events still join it.
                val joined = append(previous, old, 101)
                assertEquals(previous.id, joined.id)
                assertFalse(joined.closed)
                assertFalse("closed" in encode(joined))
                val next = classify(PlayAction.ChooseStep(TurnPosition.COMBAT_DAMAGE), TurnPosition.DECLARE_BLOCKERS,
                    TurnPosition.COMBAT_DAMAGE, 28)
                assertEquals(previous.id, append(joined, next, 102).id)
            }
        }
    }

    @Test fun v29StepIntoDeclareBlockersEndsTheAttackerDeclarationGroup() {
        val tap = HistoryGrouping.classify(PlayAction.SetTapped(id(20), true), emptyList(), v(29))
        val open = HistoryGrouping.classify(PlayAction.Combat(CombatAction.Open), emptyList(), v(29))
        val declared = HistoryGrouping.classify(declare, emptyList(), v(29))
        for (action in listOf(PlayAction.NextStep, PlayAction.ChooseStep(TurnPosition.DECLARE_BLOCKERS))) {
            val semantic = classify(action, TurnPosition.DECLARE_ATTACKERS, TurnPosition.DECLARE_BLOCKERS, 29)
            assertTrue(semantic.endsGroup, "$action")
            assertFalse(semantic.isolated)
            assertEquals(classify(action, TurnPosition.DECLARE_ATTACKERS, TurnPosition.DECLARE_BLOCKERS, 28)
                .copy(endsGroup = true), semantic)
            // Taps first, then the declaration, then Declare blockers: one group that ends at Declare blockers.
            var group = append(null, tap, 100)
            group = append(group, open, 101)
            group = append(group, declared, 102)
            assertEquals(HistoryCategory.K.key, group.category)
            val ended = append(group, semantic, 103)
            assertEquals(group.id, ended.id)
            assertEquals(id(100), ended.firstNodeId)
            assertEquals(id(103), ended.lastNodeId)
            assertTrue(ended.closed)
            assertEquals(HistorySignificance.COMBAT_MEMBERSHIP, ended.representative!!.semantic.significance)
            assertTrue("\"closed\":true" in encode(ended))
            assertEquals(ended, GameWire.json.decodeFromString(GameplayGroup.serializer(), encode(ended)))
            // Nothing joins it: not the author's next step, a status, a ping, a tap, nor the defender's pass.
            val damage = classify(PlayAction.ChooseStep(TurnPosition.COMBAT_DAMAGE), TurnPosition.DECLARE_BLOCKERS,
                TurnPosition.COMBAT_DAMAGE, 29)
            for (later in listOf(damage, HistorySemantic(HistorySignificance.PING, HistoryIcon.ADS_CLICK, rulesVersion = 3),
                tap, declared))
                assertNotEquals(ended.id, append(ended, later, 104).id, "$later")
            assertNotEquals(ended.id, append(ended, HistoryGrouping.classify(PlayAction.PassAttention), 104, b).id)
            assertFalse(append(ended, damage, 104).closed)
        }
    }

    @Test fun v29StepIntoDeclareBlockersAfterAnotherGroupIsItsOwnClosedGroup() {
        val semantic = classify(PlayAction.NextStep, TurnPosition.DECLARE_ATTACKERS, TurnPosition.DECLARE_BLOCKERS, 29)
        val declared = HistoryGrouping.classify(declare, emptyList(), v(29))
        val previousGroups = listOf(
            append(null, HistoryGrouping.classify(PlayAction.AdjustLife(a, "1"), emptyList(), v(29)), 100),
            append(null, HistoryGrouping.classify(PlayAction.SetTapped(id(20), true), emptyList(), v(29)), 100),
            append(null, classify(PlayAction.ChooseStep(TurnPosition.DECLARE_ATTACKERS), TurnPosition.BEGIN_COMBAT,
                TurnPosition.DECLARE_ATTACKERS, 29), 100),
            // The other player's combat group is not this author's attacker declaration.
            append(null, declared, 100, b))
        for (previous in previousGroups) {
            val boundary = append(previous, semantic, 101)
            assertNotEquals(previous.id, boundary.id, previous.category)
            assertEquals(id(101), boundary.firstNodeId)
            assertTrue(boundary.closed)
            assertEquals(HistoryCategory.O.key, boundary.category)
            assertNotEquals(boundary.id, append(boundary, classify(PlayAction.NextStep, TurnPosition.DECLARE_BLOCKERS,
                TurnPosition.COMBAT_DAMAGE, 29), 102).id)
        }
        // Other step targets keep joining in v29.
        for (to in TurnPosition.entries.filter { it != TurnPosition.DECLARE_BLOCKERS && it != TurnPosition.DECLARE_ATTACKERS }) {
            val previous = append(null, declared, 100)
            val other = classify(PlayAction.ChooseStep(to), TurnPosition.DECLARE_ATTACKERS, to, 29)
            assertFalse(other.endsGroup, "$to")
            val joined = append(previous, other, 101)
            assertEquals(previous.id, joined.id)
            assertFalse(joined.closed)
        }
    }

    @Test fun tapDeclareAndAssumeNoBlocksGiveTheDefenderADeclareBlockersEndpoint() {
        var tree = GameplayTree(state(TurnPosition.DECLARE_ATTACKERS))
        tree = assertNotNull(tree.play(a, play(PlayAction.SetTapped(id(20), true), 20)).tree)
        val tapped = tree.currentNodeId
        tree = assertNotNull(tree.play(a, play(PlayAction.Combat(CombatAction.Open), 21)).tree)
        val area = tree.stateAt(tree.currentNodeId)!!.board.combat!!.id
        tree = assertNotNull(tree.play(a, play(PlayAction.Combat(CombatAction.Declare(area, id(20))), 22)).tree)
        val attacks = tree.currentNodeId
        tree = assertNotNull(tree.play(a, play(PlayAction.ChooseStep(TurnPosition.DECLARE_BLOCKERS), 23)).tree)
        val blockers = tree.currentNodeId
        tree = assertNotNull(tree.play(a, play(PlayAction.ChooseStep(TurnPosition.COMBAT_DAMAGE), 24)).tree)
        val damage = tree.currentNodeId
        val groups = listOf(tapped, attacks, blockers, damage).map { assertNotNull(tree.stateAt(it)!!.currentGroup) }
        // Tap, declaration and Declare blockers are one group, a complete timeline endpoint; damage is the next group.
        assertEquals(1, groups.take(3).map { it.id }.distinct().size)
        assertNotEquals(groups[2].id, groups[3].id)
        assertEquals(tapped, groups[2].firstNodeId)
        assertEquals(blockers, groups[2].lastNodeId)
        assertTrue(groups[2].closed)
        assertEquals(blockers, groups[3].firstNodeId.let { tree.nodes.getValue(it).parentId })
        val boundary = tree.stateAt(blockers)!!
        assertEquals(TurnPosition.DECLARE_BLOCKERS, TurnPosition.of(boundary.board.turn))
        assertEquals(b, boundary.board.effectiveAttentionHolder)
        // The defender continues from there; their combat edits start a new group on a new branch.
        val continued = tree.play(b, play(PlayAction.Combat(CombatAction.Declare(area, id(21))), 25)
            .copy(continuation = Continuation(blockers, true)))
        assertNull(continued.reason)
        val branched = assertNotNull(continued.tree)
        assertEquals(blockers, branched.nodes.getValue(branched.currentNodeId).parentId)
        val blocks = branched.stateAt(branched.currentNodeId)!!
        assertEquals(TurnPosition.DECLARE_BLOCKERS, TurnPosition.of(blocks.board.turn))
        assertNotEquals(groups[2].id, blocks.currentGroup!!.id)
        assertEquals(b, blocks.currentGroup!!.author)
        // Cold replay reproduces the same groups.
        assertEquals(groups, listOf(tapped, attacks, blockers, damage).map { tree.copy().stateAt(it)!!.currentGroup })
    }
}
