package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.concurrent.Executor
import kotlin.test.*

class TableChangesTest {
    // HIS-031, TBL-033
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

    // HIS-031, TBL-033
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

    // HIS-031, TBL-033
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




    @Test fun playbackWithoutAnExplicitFenceUsesTheForwardOrReverseEndpoint() {
        val changes = TableChanges()
        val shuffle = event().let { it.copy(mutation = it.mutation.copy(id = "shuffle", parentId = "before",
            action = PlayAction.ShuffleLibrary("library", "seed"))) }
        changes.apply(TableChangeBatch(listOf(shuffle)))
        assertEquals(setOf("library"), changes.shuffles("shuffle"))
        assertFalse(changes.animates("before"))
        changes.apply(TableChangeBatch(listOf(shuffle.copy(reverse = true))))
        assertTrue(changes.animates("before"))
        assertFalse(changes.animates("shuffle"))
        assertTrue(changes.shuffles("before").isEmpty())
    }

}
