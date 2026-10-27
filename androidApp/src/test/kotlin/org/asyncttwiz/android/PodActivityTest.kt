package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.*

class PodActivityTest {
    private val id = UUID.randomUUID().toString()
    private val identity = UUID.randomUUID().toString()
    @Test fun textAndRoutesArePodSpecificAndUnknownKindsAreIgnored() {
        assertEquals("Your pick — Synergy", podActivityText(ActivityKind.POD_YOUR_PICK, "Synergy"))
        assertEquals("Decks ready — Synergy", podActivityText(ActivityKind.POD_DECKS_READY, "Synergy"))
        assertEquals("Your pick", podActivityText(ActivityKind.POD_YOUR_PICK))
        assertEquals("New pod invitation", podActivityText(ActivityKind.POD_INVITATION))
        assertEquals("Draft complete: build your deck", podActivityText(ActivityKind.POD_DRAFT_COMPLETE))
        assertEquals("Draft cancelled", podActivityText(ActivityKind.POD_DRAFT_CANCELLED))
        assertEquals("Pod destroyed", podActivityText(ActivityKind.POD_DESTROYED))
        ActivityKind.entries.forEach { kind ->
            val route = ActivityRoute.parse(id, id, kind.name, id, identity)!!
            assertEquals(kind.name.startsWith("POD_"), route.kind.isPodActivity())
        }
        assertNull(ActivityRoute.parse(id, id, "POD_FUTURE", id, identity))
        assertNull(ActivityRoute.parse(id, id, "FUTURE", id, identity))
    }
    @Test fun picksReplacePerPodNotPerPackAndNeverCollideWithGamesOrInvitations() {
        val savedMarks = mutableMapOf<String, ActivityWatermark>()
        var savedCursor: String? = null
        val store = object : ActivityWindowStore {
            override fun cursor() = savedCursor
            override fun watermark(key: String) = savedMarks[key]
            override fun save(cursor: String, marks: Map<String, ActivityWatermark>): Boolean {
                savedCursor = cursor; savedMarks.putAll(marks); return true
            }
            override fun pending() = emptyMap<String, PendingActivity>()
            override fun saveWindow(cursor: String, marks: Map<String, ActivityWatermark>, pending: Map<String, PendingActivity>) = save(cursor, marks)
        }
        val notices = mutableListOf<ActivityNotice>()
        val delivery = ActivityDelivery(store, { 0L }, notices::add)
        val owner = ActivityOwner(id, identity); delivery.bind(owner)
        fun apply(number: Int, kind: ActivityKind, identity: String = this.identity) {
            assertTrue(delivery.apply(owner, ActivityPage(listOf(ActivityHint(number.toString(), kind, id, identity)),
                number.toString(), "0", false, false)) { true })
        }
        apply(1, ActivityKind.POD_YOUR_PICK)
        apply(2, ActivityKind.POD_YOUR_PICK)
        apply(2, ActivityKind.POD_YOUR_PICK) // Same event delivered by both push and polling.
        assertEquals(listOf("pod:$id", "pod:$id"), notices.map { it.key })
        assertTrue(notices.none { it.silent })
        apply(3, ActivityKind.POD_INVITATION)
        apply(4, ActivityKind.INVITATION)
        assertEquals("pod-invite:$id", notices[2].key)
        assertEquals("invite:$identity", notices[3].key)
        assertEquals("game:$id", ActivityHint("5", ActivityKind.GAME, id, identity).notificationKey())
        delivery.display(id, true)
        apply(6, ActivityKind.POD_DRAFT_COMPLETE)
        assertEquals(ActivityKind.POD_DRAFT_COMPLETE, notices.last().hint.kind) // not a game-screen hint
        assertFalse(notices.last().silent)
    }
}
