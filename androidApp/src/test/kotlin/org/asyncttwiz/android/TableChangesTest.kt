package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.concurrent.Executor
import kotlin.test.*

class TableChangesTest {
    @Test fun tagsHoldOpaqueForOneSecondThenFadeForHalfASecond() {
        for (elapsed in listOf(0L, 300L, 999L, TableChanges.HOLD_MS))
            assertEquals(1f, tableChangeTagAlpha(elapsed.toFloat() / TableChanges.WINDOW_MS), .0001f)
        assertEquals(.5f, tableChangeTagAlpha(1_250f / TableChanges.WINDOW_MS), .0001f)
        assertEquals(0f, tableChangeTagAlpha(1f))
    }

    private val f = GameplayFixture()
    private fun event(vararg fields: GameplayFieldChange, transition: GameplayTransition = GameplayTransition()) =
        TableAppliedEvent(GameplayMutation("event", "root", "branch", "request", f.actor, null,
            PlayAction.PassAttention, transition.copy(fields = fields.toList())))
    private fun field(kind: GameplayField, from: String?, to: String?, color: CounterColor? = null) =
        GameplayFieldChange("target", kind, from, to, color)

    @Test fun extractsEveryRequestedTransitionAndIgnoresExistingMotionAndPileCounts() {
        val link = PlayLink("link", LinkEndpoint(LinkEndpointKind.CARD, "card"), LinkEndpoint(LinkEndpointKind.PLAYER, "player"))
        val changes = TableChangeExtraction.extract(event(
            field(GameplayField.LIFE, "20", "17"), field(GameplayField.PLAYER_COUNTER, null, "2", CounterColor.RED),
            field(GameplayField.CORNER, "3/3", "1/3"), field(GameplayField.COUNTER, "1", "3", CounterColor.BLUE),
            field(GameplayField.NOTE, "old", "new"), field(GameplayField.OWNER, "a", "b"),
            field(GameplayField.FACE, "front", "back"), field(GameplayField.COVERED, "false", "true"),
            field(GameplayField.PHASE, "MAIN1", "COMBAT"), field(GameplayField.STEP, "", "BEGIN_COMBAT"),
            field(GameplayField.TAPPED, "false", "true"), field(GameplayField.CONTAINER, "hand", "battlefield"),
            transition = GameplayTransition(links = listOf(LinkChange(null, link)), combatStack = CombatStackTransition(
                textMarks = listOf(StackTextMarkChange("stack", setOf(1), setOf(2)))))))
        assertEquals(TableChangeKind.entries.toSet(), changes.map { it.key.kind }.toSet())
        assertEquals("-3", ActiveTableChange(changes.single { it.key.kind == TableChangeKind.LIFE }, 0, 1).tag)
        assertEquals("-2/-0", ActiveTableChange(changes.single { it.key.kind == TableChangeKind.CORNER }, 0, 1).tag)
        assertEquals("b", changes.single { it.key.kind == TableChangeKind.OWNER }.owner)
        assertEquals(setOf(1, 2), changes.filter { it.key.kind == TableChangeKind.TEXT_MARK }.map { it.key.line }.toSet())
        assertEquals(1, changes.count { it.key.kind == TableChangeKind.FACE })
        assertEquals(1, changes.count { it.key.kind == TableChangeKind.STEP })
    }

    @Test fun accumulatesPerValueRestartsWindowAndExpiresWithoutRemovingANewerTag() {
        var time = 0L
        val effects = TableChanges { time }
        val one = event(field(GameplayField.LIFE, "20", "19"))
        repeat(3) { effects.apply(TableChangeBatch(listOf(one))); time += 400 }
        val value = effects.active.values.single()
        assertEquals("-3", value.tag)
        assertEquals(800, value.started)
        time = 2_299; effects.apply(TableChangeBatch(listOf(one)))
        assertEquals("-4", effects.active.values.single().tag)
        effects.expire(value.change.key, value.serial)
        assertEquals(1, effects.active.size)
        time += 1_500; effects.apply(TableChangeBatch(listOf(one)))
        assertEquals("-1", effects.active.values.single().tag)
        effects.apply(TableChangeBatch(jump = true)); assertTrue(effects.active.isEmpty())
    }

    @Test fun invertsDeltasAndNewLinksButDoesNotTagNonNumericEdits() {
        val value = event(field(GameplayField.LIFE, "20", "17"), field(GameplayField.CORNER, "X/3", "4/3"))
        val changes = TableChangeExtraction.extract(value.copy(reverse = true))
        assertEquals("+3", ActiveTableChange(changes.first(), 0, 0).tag)
        assertTrue(changes.last().delta.isEmpty())
    }

    @Test fun projectionNotesControllersAndLinksUseTheirOwnRecordedIdentity() {
        val effect = StackEffect("effect", "source", controller = "a", note = "old")
        val link = ProjectionLink("link", "effect", "occurrence")
        val changes = TableChangeExtraction.extract(event(transition = GameplayTransition(combatStack = CombatStackTransition(
            effects = listOf(StackEffectChange("effect", effect, effect.copy(controller = "b", note = "new"))),
            addedLinks = listOf(link)))))
        assertEquals("b", changes.single { it.key.kind == TableChangeKind.OWNER }.owner)
        assertEquals("effect", changes.single { it.key.kind == TableChangeKind.NOTE }.key.target)
        assertEquals(link, changes.single { it.key.kind == TableChangeKind.LINK }.projectionLink)
    }

    @Test fun recordedFacesFlipInPlaceWithoutAddingMotionToUnchangedCardsOrJumps() {
        val b = TableBounds(0f, 0f, 28f, 40f)
        val initial = TableScene(listOf(TableItem("card/card", b, "front", "Front", kind = TableItemKind.CARD,
            cardId = "card", faceId = "front")), emptyList())
        for (covered in listOf(false, true)) {
            val target = initial.copy(items = initial.items.map { it.copy(faceId = "back", covered = covered) })
            val animation = HistorySceneAnimation(initial)
            assertTrue(animation.retarget(target, 1f, setOf("card")))
            assertEquals(350, animation.durationMillis)
            assertEquals(.02f, animation.frame(.5f).single().item.flipScale)
            assertEquals("front", animation.frame(.25f).single().item.faceId)
            assertEquals("back", animation.frame(.75f).single().item.faceId)
            assertFalse(animation.retarget(target, .75f), "Acceptance must not restart the same flip")
            val jump = HistorySceneAnimation(initial)
            jump.retarget(target, 1f)
            assertEquals(1f, jump.frame(.5f).single().item.flipScale)
        }
    }

    @Test fun predictionAndAcceptanceAnimateOnceIncludingPredictedUndoAndRedoWhileSnapshotsNeverAnimate() {
        val batches = mutableListOf<TableChangeBatch>()
        val replica = GameplayReplica { batches += it }
        assertTrue(replica.root(f.head())); assertTrue(batches.single().jump); batches.clear()
        var tree = GameplayTree(f.initial)
        var rev = 1
        for (action in listOf(GameAction.Play(PlayAction.AdjustLife(f.actor, "-1")), GameAction.Undo, GameAction.Redo)) {
            val command = f.command(PlayAction.PassAttention).copy(action = action)
            assertTrue(replica.predict(command, f.actor))
            assertEquals(1, batches.flatMap { it.events }.size)
            val updated = assertNotNull(HistoryContinuation.change(tree, f.actor, command).tree)
            val state = assertNotNull(updated.stateAt(updated.currentNodeId))
            val revision = GameRevision(f.game.id, rev.toString(), (++rev).toString(), command.requestId, f.actor,
                command.scope, f.game.copy(revision = rev.toString()), play = state)
            assertTrue(replica.published(f.head(state, rev.toString()), TreeSync.generation(updated), rev.toString(),
                emptyList(), listOf(revision), HistoryTree.ingest(updated, rev.toString()), animate = true))
            assertEquals(1, batches.flatMap { it.events }.size)
            tree = updated; batches.clear()
        }
        val remote = f.command(PlayAction.AdjustLife(f.actor, "2"))
        val updated = assertNotNull(tree.play(f.other, remote).tree)
        val state = assertNotNull(updated.stateAt(updated.currentNodeId))
        assertTrue(replica.published(f.head(state, (++rev).toString()), TreeSync.generation(updated), rev.toString(), emptyList(), emptyList(), HistoryTree.ingest(updated, rev.toString()), animate = true))
        assertEquals("+2", ActiveTableChange(TableChangeExtraction.extract(batches.single().events.single()).single(), 0, 0).tag)
        batches.clear()
        assertTrue(replica.published(f.head(state, rev.toString()), TreeSync.generation(updated), rev.toString(), emptyList(), emptyList(), HistoryTree.ingest(updated, rev.toString())))
        assertTrue(batches.single().jump)
    }

    @Test fun jumpsDoNotWalkHistoryAndForwardWorkIsBounded() {
        var reads = 0
        val nodes = object : Map<String, GameplayMutation> by emptyMap() {
            override fun get(key: String): GameplayMutation? {
                reads++
                val n = key.toIntOrNull() ?: return null
                return event().mutation.copy(id = key, parentId = (n - 1).toString())
            }
        }
        assertTrue(TableChangeExtraction.between(null, "100000", nodes).jump)
        assertEquals(0, reads)
        assertTrue(TableChangeExtraction.between("0", "100000", nodes).jump)
        assertTrue(reads <= 257)
        assertTrue(TableChangeExtraction.between("100000", "0", nodes).jump)
        val single = TableChangeExtraction.between("100000", "99999", nodes)
        assertTrue(single.events.single().reverse)
    }

    @Test fun playbackStepAndAutoEmitOnlyAfterTheMaterializedEndpointAndScrubsClearEffects() {
        var tree = GameplayTree(f.initial)
        repeat(3) { tree = assertNotNull(tree.play(f.actor, f.command(PlayAction.AdjustLife(f.actor, "-1"))).tree) }
        val history = TableHistoryController(Executor { it.run() }, { it() }, { node, done ->
            val cursor = node ?: tree.currentNodeId
            done(DownloadedHistoryState(1, cursor, state = tree.stateAt(cursor))); true
        }, { _, _, _ -> error("UI effects must never submit") })
        history.use {
            history.update(tree, "v")
            history.select(f.root.id); history.displayPosition()
            assertTrue(history.changes.active.isEmpty())
            history.playback!!.next(); assertTrue(history.changes.active.isEmpty())
            history.displayPosition()
            assertEquals("-3", history.changes.active.values.single().tag)
            history.playback!!.scrub(f.root.id); assertTrue(history.changes.active.isEmpty())
            history.playback!!.play(); history.playback!!.next(automatic = true); history.displayPosition()
            assertEquals("-3", history.changes.active.values.single().tag)
            history.leaving(); assertTrue(history.changes.active.isEmpty())
        }
    }

    @Test fun coalescedLivePublicationsKeepShuffleFeedbackAndCatchupDoesNotWiggle() {
        val history = TableHistoryController(Executor { it.run() }, { it() }, { _, _ -> true }, { _, _, _ -> false })
        history.use {
            history.update(GameplayTree(f.initial), "v")
            val shuffle = event().copy(mutation = event().mutation.copy(id = "shuffle",
                action = PlayAction.ShuffleLibrary("library", "seed")))
            val following = event().copy(mutation = event().mutation.copy(id = "following", parentId = "shuffle"))
            history.liveChanges(TableChangeBatch(listOf(shuffle), cursor = "shuffle"))
            history.liveChanges(TableChangeBatch(listOf(following), cursor = "following"))
            history.showChanges("following")
            assertEquals(setOf("library"), history.changes.shuffles("following"))
            history.liveChanges(TableChangeBatch(listOf(shuffle), cursor = "shuffle"))
            history.liveChanges(TableChangeBatch(jump = true))
            history.showChanges("shuffle")
            assertTrue(history.changes.shuffles("shuffle").isEmpty())
        }
    }

    @Test fun liveEffectsWaitForTheirDisplayedEndpointAndJumpsDiscardPendingEffects() {
        val tree = GameplayTree(f.initial)
        val history = TableHistoryController(Executor { it.run() }, { it() }, { _, _ -> true }, { _, _, _ -> false })
        history.use {
            history.update(tree, "v")
            val batch = TableChangeBatch(listOf(event(field(GameplayField.LIFE, "20", "19"))), cursor = "predicted")
            history.liveChanges(batch)
            history.showChanges("old-indexed-view")
            assertTrue(history.changes.active.isEmpty())
            history.showChanges("predicted")
            assertEquals("-1", history.changes.active.values.single().tag)
            history.liveChanges(batch.copy(cursor = "catchup"))
            history.liveChanges(TableChangeBatch(jump = true))
            history.showChanges("catchup")
            assertTrue(history.changes.active.isEmpty())
            history.updateForeground(false)
            history.liveChanges(batch); history.showChanges("predicted")
            assertTrue(history.changes.active.isEmpty())
        }
    }
}
