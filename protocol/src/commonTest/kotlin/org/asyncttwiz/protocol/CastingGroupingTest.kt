package org.asyncttwiz.protocol

import kotlin.test.*

class CastingGroupingTest {
    private val author = gameplayId(7)
    private val entry = gameplayId(10)
    private fun classify(action: PlayAction, version: Int = 15) = HistoryGrouping.classify(action,
        listOf(PlayContainer(gameplayId(21), PlayZone.HAND), PlayContainer(gameplayId(25), PlayZone.STACK)),
        GameplayTransition(rulesVersion = version))
    private fun cast(version: Int = 15) = classify(PlayAction.MoveCard(entry, MoveSource(gameplayId(21)),
        MoveDestination(gameplayId(25), MoveInsertion.TOP)), version)
    private fun append(group: GameplayGroup?, semantic: HistorySemantic, n: Int, actor: String = author) =
        HistoryGrouping.append(group, gameplayId(n), gameplayId(n + 1000), actor, semantic)

    @Test fun castWithTapPrefixMarksBothNoteFormsAndManaTapsKeepsItsEntryRepresentative() {
        var group = append(null, classify(PlayAction.SetTapped(gameplayId(14), true)), 100)
        val first = group
        group = append(group, cast(), 101)
        assertEquals(first.id, group.id)
        assertEquals(entry, group.stackEntryId)
        val actions = listOf(
            PlayAction.Stack(StackAction.MarkText(entry, 0)),
            PlayAction.Stack(StackAction.ToggleTextMark(entry, 1)),
            PlayAction.SetProjectionNote(entry, "X=3"), PlayAction.SetNote(entry, "target: opponent"),
            PlayAction.SetTapped(gameplayId(12), true), PlayAction.SetTapped(gameplayId(13), true),
            PlayAction.EventComment(EventReference(gameplayId(2), gameplayId(101)), "paying"),
            PlayAction.Ping(EventReference(gameplayId(2), gameplayId(101)), PingTarget(PingTargetKind.PLAYER, author)))
        actions.forEachIndexed { i, action ->
            group = append(group, classify(action), 102 + i)
            assertEquals(first.id, group.id)
            assertEquals(gameplayId(101), group.representative?.nodeId)
            assertEquals(HistorySignificance.STACK_ENTRY, group.representative?.semantic?.significance)
            assertEquals(HistoryCategory.I.key, group.category)
        }
        assertEquals("10", group.count)
        assertEquals(group, GameWire.json.decodeFromString<GameplayGroup>(GameWire.json.encodeToString(group)))
    }

    @Test fun untapOtherProjectionOpponentAndAnyOtherActionCloseCastingContext() {
        val group = append(null, cast(), 100)
        val different = gameplayId(11)
        val boundaries = listOf(
            PlayAction.SetTapped(gameplayId(14), false),
            PlayAction.Stack(StackAction.MarkText(different, 0)),
            PlayAction.Stack(StackAction.ToggleTextMark(different, 0)),
            PlayAction.SetProjectionNote(different, "different"), PlayAction.SetNote(different, "different"),
            PlayAction.SetLife(author, "19"), PlayAction.NextStep, PlayAction.PassAttention,
            PlayAction.Stack(StackAction.RemoveEffect(entry)))
        for ((i, action) in boundaries.withIndex()) {
            val next = append(group, classify(action), 101 + i)
            assertNotEquals(group.id, next.id, "$action")
            assertNull(next.stackEntryId, "$action must not leak the old cast context")
            assertNotEquals(group.id, append(next, classify(PlayAction.SetTapped(gameplayId(13), true)), 200).id)
        }
        for (action in listOf(PlayAction.SetTapped(gameplayId(14), true), PlayAction.SetNote(entry, "other player"),
            PlayAction.EventComment(EventReference(gameplayId(2), gameplayId(100)), "opponent"), PlayAction.PassAttention))
            assertNotEquals(group.id, append(group, classify(action), 101, gameplayId(8)).id)
        val second = append(group, cast(), 102)
        assertNotEquals(group.id, second.id)
        assertEquals(gameplayId(102), second.representative?.nodeId)
        assertEquals(second.id, append(second, classify(PlayAction.SetTapped(gameplayId(14), true)), 103).id)
    }

    @Test fun retainedRulesDoNotGainCastingSuffixGroupsOrSerializedContext() {
        for (version in listOf(1, 12, 13, 14)) {
            val semantic = cast(version)
            val group = append(null, semantic, 100)
            assertNull(group.stackEntryId)
            assertFalse("stackEntryId" in GameWire.json.encodeToString(group))
            assertFalse("stackEntryId" in GameWire.json.encodeToString(semantic))
            for (action in listOf(PlayAction.Stack(StackAction.MarkText(entry, 0)), PlayAction.SetNote(entry, "note"),
                PlayAction.SetTapped(gameplayId(14), true)))
                assertNotEquals(group.id, append(group, classify(action, version), 101).id)
            // An old event uses its recorded old rule even following a new casting anchor.
            assertNotEquals(group.id, append(append(null, cast(), 100),
                classify(PlayAction.SetTapped(gameplayId(14), true), version), 101).id)
        }
    }

    @Test fun acceptedEffectCastAndCopyCarryTheirOwnOccurrenceContextThroughSealedApplyAndUnapply() {
        var state = gameplayFixture().withBattlefieldCards(gameplayId(10), gameplayId(14))
        var request = 300
        fun step(action: PlayAction): GameplayMutation {
            val before = state
            val result = gameplayChange(before, action, request++)
            val mutation = assertNotNull(result.mutation, "${result.outcome}: ${result.reason}")
            assertEquals(16, mutation.transition.rulesVersion)
            assertEquals(3, mutation.grouping?.rulesVersion)
            state = assertNotNull(result.state)
            assertEquals(state, GameplayHistory.apply(before, mutation))
            assertEquals(before, GameplayHistory.unapply(state, mutation))
            return mutation
        }
        step(PlayAction.Stack(StackAction.AddEffect(entry, GameplayRules.stackDigest(state))))
        val effect = state.board.stackEffects.single().id
        val castGroup = state.currentGroup!!
        assertEquals(effect, castGroup.stackEntryId)
        step(PlayAction.SetProjectionNote(effect, "mode chosen"))
        step(PlayAction.SetTapped(gameplayId(14), true))
        assertEquals(castGroup.id, state.currentGroup!!.id)
        assertEquals(castGroup.representative, state.currentGroup!!.representative)
        val copy = step(PlayAction.Stack(StackAction.CopyEffect(effect, GameplayRules.stackDigest(state))))
        val copyId = state.board.stackEffects.single { it.id != effect }.id
        assertNotEquals(castGroup.id, state.currentGroup!!.id)
        assertEquals(copyId, state.currentGroup!!.stackEntryId)
        assertEquals(copy.id, state.currentGroup!!.representative?.nodeId)
    }
}
