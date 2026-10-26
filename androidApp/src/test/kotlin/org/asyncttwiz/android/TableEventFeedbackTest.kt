package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class TableEventFeedbackTest {
    private val f = GameplayFixture()
    private fun roll(id: String = "roll", parent: String = f.root.id, author: String = f.actor,
        action: PlayAction = PlayAction.RollDie("20", 14)) = GameplayMutation(id, parent, "branch", "request", author, null,
        action, GameplayTransition())
    private fun group(id: String, first: String = id, last: String = id) = GameplayGroup(id, f.actor, first, last, "random", null, "1")
    private fun state(group: GameplayGroup?, annotations: EventAnnotationState = EventAnnotationState()) =
        f.initial.copy(currentNodeId = group?.lastNodeId ?: f.root.id, currentGroup = group, annotations = annotations)
    private fun ping(groupId: String, target: PingTarget = PingTarget(PingTargetKind.PLAYER, f.actor)): RecordedPingChange {
        val reference = ReferencedEventContext(EventReference(f.initial.id, "roll"), "version")
        return RecordedPingChange(AnnotationRecording(f.initial.id, "branch", "roll", "ping", groupId, "request", f.actor),
            reference, target, null, PingPresence(target, "9", "ping"))
    }

    @Test fun liveResultsHoldThenFadeAndSeveralRollsAndBothAuthorsRemainSeparate() {
        var time = 0L
        val feedback = TableEventFeedback { time }
        feedback.apply(TableChangeBatch(listOf(TableAppliedEvent(roll()))))
        time = 400
        feedback.apply(TableChangeBatch(listOf(TableAppliedEvent(roll("second", author = f.other)),
            TableAppliedEvent(roll("coin", action = PlayAction.FlipCoin(CoinFace.HEADS))))))
        assertEquals(listOf("d20 → 14", "d20 → 14", "Heads"), feedback.rolls.values.sortedBy { it.serial }.map { it.result.label })
        assertEquals(setOf(f.actor, f.other), feedback.rolls.values.map { it.result.author }.toSet())
        assertEquals(1f, TableEventFeedback.alpha(2_500f))
        assertEquals(.5f, TableEventFeedback.alpha(2_750f))
        assertEquals(0f, TableEventFeedback.alpha(3_000f))
        time = 3_000
        feedback.apply(TableChangeBatch())
        assertEquals(2, feedback.rolls.size)
        time = 3_400
        feedback.apply(TableChangeBatch())
        assertTrue(feedback.rolls.isEmpty())
    }

    @Test fun stoppedGroupPersistsAfterExpiryAndJumpButDisappearsOnAnotherGroup() {
        var time = 0L
        val feedback = TableEventFeedback { time }
        val first = roll()
        val second = roll("second", first.id, action = PlayAction.FlipCoin(CoinFace.TAILS))
        val nodes = mapOf(first.id to first, second.id to second)
        val play = state(group(first.id, first.id, second.id))
        feedback.apply(TableChangeBatch(listOf(TableAppliedEvent(first), TableAppliedEvent(second))))
        feedback.present(play, true, nodes)
        assertEquals(listOf("d20 → 14", "Tails"), feedback.stoppedRolls.map { it.label })
        time = 100_000
        feedback.present(play, true, nodes)
        assertTrue(feedback.rolls.isEmpty())
        assertEquals(2, feedback.stoppedRolls.size)
        feedback.apply(TableChangeBatch(jump = true))
        feedback.present(play, true, nodes)
        assertTrue(feedback.rolls.isEmpty(), "Jump must not create transient feedback")
        assertEquals(2, feedback.stoppedRolls.size, "A stopped jump target still shows its results")
        feedback.present(state(null), true, nodes)
        assertTrue(feedback.stoppedRolls.isEmpty())
        assertTrue(feedback.isStopped)
        feedback.apply(TableChangeBatch(jump = true))
        feedback.present(play, false, nodes)
        assertTrue(feedback.rolls.isEmpty())
        assertTrue(feedback.stoppedRolls.isEmpty())
    }

    @Test fun reverseDoesNotShowTheResultBeingUnapplied() {
        val feedback = TableEventFeedback { 0L }
        feedback.apply(TableChangeBatch(listOf(TableAppliedEvent(roll(), reverse = true))))
        assertTrue(feedback.rolls.isEmpty())
    }

    @Test fun backwardsGroupTransportUsesBoundedUnapplyInsteadOfJumping() {
        var tree = GameplayTree(f.initial)
        tree = assertNotNull(tree.play(f.actor, f.command(PlayAction.RollDie("20", 14))).tree)
        val first = tree.currentNodeId
        tree = assertNotNull(tree.play(f.actor, f.command(PlayAction.RollDie("6", 2))).tree)
        val second = tree.currentNodeId
        val index = HistoryTree.ingest(tree, "v")
        val batch = tablePlaybackChanges(second, f.root.id, index)
        assertFalse(batch.jump)
        assertEquals(listOf(second, first), batch.events.map { it.mutation.id })
        assertTrue(batch.events.all { it.reverse })
    }

    @Test fun departingPingsLingerInEitherDirectionButNeverBecomeInputOrNewGroupNumbers() {
        for (forward in listOf(true, false)) {
            var time = 0L
            val feedback = TableEventFeedback { time }
            val projection = ActivePingProjection()
            val record = ping("roll")
            val annotations = EventAnnotationState(pings = listOf(record))
            val before = state(group("roll"), annotations)
            val after = state(if (forward) group("next") else null, annotations)
            feedback.present(before, false, emptyMap())
            feedback.present(after, false, emptyMap())
            assertEquals(record.target, feedback.pings.values.single().record.target)
            assertEquals("1", feedback.pings.values.single().record.to!!.ordinal)
            assertTrue(projection.records(after).isEmpty())
            val intent = tablePingIntent(after, EventReference(after.id, after.currentNodeId), record.target, projection)
            assertTrue(intent.add, "A lingering ring must not turn Ping into Unping")
            assertIs<PlayAction.Ping>(intent.actions().single())
            time = 2_500
            feedback.present(after, false, emptyMap())
            assertEquals(1f, TableEventFeedback.alpha(feedback.elapsed(feedback.pings.values.single().started).toFloat()))
            time = 2_750
            assertEquals(.5f, TableEventFeedback.alpha(feedback.elapsed(feedback.pings.values.single().started).toFloat()))
            time = 3_000
            feedback.present(after, false, emptyMap())
            assertTrue(feedback.pings.isEmpty())
            // A jump/catch-up resets both existing ghosts and the departure snapshot.
            feedback.present(before, false, emptyMap())
            feedback.apply(TableChangeBatch(jump = true))
            feedback.present(after, false, emptyMap())
            assertTrue(feedback.pings.isEmpty())
            // The pre-jump endpoint may be composed again while the jump target loads.
            feedback.present(before, false, emptyMap())
            feedback.apply(TableChangeBatch(jump = true))
            feedback.present(before, false, emptyMap())
            feedback.present(after, false, emptyMap())
            assertTrue(feedback.pings.isEmpty(), "A stale pre-jump frame must not create a lingering ring")
            // Ordinary departures linger again afterwards.
            feedback.present(before, false, emptyMap())
            feedback.present(after, false, emptyMap())
            assertEquals(1, feedback.pings.size)
        }
    }

    @Test fun returningToAPingGroupRemovesItsGhostAndRestoresOnlyRealInputMarkers() {
        val feedback = TableEventFeedback { 0L }
        val before = state(group("roll"), EventAnnotationState(pings = listOf(ping("roll"))))
        feedback.present(before, true, emptyMap())
        feedback.present(state(group("next")), true, emptyMap())
        assertEquals(1, feedback.pings.size)
        feedback.present(before, true, emptyMap())
        assertTrue(feedback.pings.isEmpty())
        val intent = tablePingIntent(before, EventReference(before.id, before.currentNodeId), ping("roll").target)
        assertFalse(intent.add)
    }

    @Test fun stoppedLookupAndDenseTransientWindowsAreFixedBounded() {
        var reads = 0
        val nodes = object : Map<String, GameplayMutation> by emptyMap() {
            override fun get(key: String): GameplayMutation {
                reads++
                return roll(key, (key.toInt() - 1).toString(), action = PlayAction.PassAttention)
            }
        }
        val feedback = TableEventFeedback { 0L }
        feedback.present(state(group("0", "0", "100000")), true, nodes)
        assertTrue(reads <= TableEventFeedback.MAX_GROUP_READS + 1)
        repeat(10) { feedback.apply(TableChangeBatch(List(100) { TableAppliedEvent(roll("$it")) })) }
        assertEquals(TableEventFeedback.MAX_RESULTS, feedback.rolls.size)
    }
}
