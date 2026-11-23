package org.asyncttwiz.e2e

import android.app.NotificationManager
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.android.ActivityApi
import org.asyncttwiz.android.ActivityDelivery
import org.asyncttwiz.android.ActivityNotifications
import org.asyncttwiz.android.ActivityOwner
import org.asyncttwiz.android.ActivityWatermark
import org.asyncttwiz.android.ActivityWindowStore
import org.asyncttwiz.android.ShellDestination
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pod setup's bots and members (owner, 2026-11-22): Add bot, and the creator's ✕ on bots, invitations and members. */
class PodSetupMembersTest : E2e() {
    /** Bob's phone's notifications as the app posts them from its feed poll (ActivityWorker), which the e2e app doesn't run. */
    private inner class Notifications(account: TestAccount) {
        private val token = account.token
        val owner = ActivityOwner(account.id, UUID.randomUUID().toString())
        private val notifications = ActivityNotifications(bob.activity).also {
            org.robolectric.Shadows.shadowOf(bob.activity.application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
            it.channels()
        }
        private var cursor: String? = null
        private val marks = mutableMapOf<String, ActivityWatermark>()
        private var pending = mapOf<String, PendingActivity>()
        private val delivery = ActivityDelivery(object : ActivityWindowStore {
            override fun cursor() = cursor
            override fun watermark(key: String) = marks[key]
            override fun save(cursor: String, marks: Map<String, ActivityWatermark>): Boolean {
                this@Notifications.cursor = cursor; this@Notifications.marks += marks; return true
            }
            override fun pending() = pending
            override fun saveWindow(cursor: String, marks: Map<String, ActivityWatermark>, pending: Map<String, PendingActivity>): Boolean {
                this@Notifications.pending = pending; return save(cursor, marks)
            }
        }) { notice -> notifications.post(owner, notice) }.also { it.bind(owner) }
        private var polled = 0L
        /** One poll of the feed at most once a second (the server limits its requests per minute). */
        fun poll() {
            if (System.nanoTime() - polled < 1_000_000_000L) return
            polled = System.nanoTime()
            runBlocking {
                ActivityApi(E2eStack.origin).use { api ->
                    do {
                        val page = api.page(token, cursor)
                        check(delivery.apply(owner, page) { true })
                        notifications.settle(owner, page, delivery)
                    } while (page.hasMore)
                }
            }
        }
        /** The title of the notification of [key] showing, null when none shows. */
        fun title(key: String): String? = bob.activity.getSystemService(NotificationManager::class.java).activeNotifications
            .singleOrNull { it.tag == "activity:${owner.binding}:$key" }?.notification?.extras
            ?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
    }

    // DRF-296, DRF-030, DRF-307, DRF-308, DRF-137, LOB-243 (owner, 2026-11-22): in her pod's setup Alice adds two bots with Add bot and
    // removes one with its ✕; she cancels Carol's pending invitation with its ✕ (it leaves Carol's Pods list); she removes Bob,
    // a joined member, confirming it: his phone shows "Removed from <pod>", the pod has left his Pods list, and showing that
    // list clears the notice (LOB-243); she invites him again from the invite picker and he joins
    @Test fun theCreatorAddsAndRemovesBotsCancelsAnInvitationAndRemovesAMember() {
        launchPlayers()
        fun id() = UUID.randomUUID().toString()
        val carol = ServerSetup.third()
        val name = "E2E members ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.setupPod(alice.account, name, settings = DraftSettings(packsPerPlayer = 1, cardsPerPack = 3))
        // The pod on the server, read at most every half second in a wait (the server limits each account's pod requests)
        var read = 0L
        var last: PodDetail? = null
        fun server(): PodDetail {
            if (last == null || System.nanoTime() - read > 500_000_000L) { last = ServerSetup.pod(alice.account, pod); read = System.nanoTime() }
            return last!!
        }
        ServerSetup.invitePod(alice.account, bob.account, pod)
        runBlocking { org.asyncttwiz.android.PodApi(E2eStack.origin).use { it.accept(bob.account.token, pod, PodRequest(id())) } }
        ServerSetup.invitePod(alice.account, carol, pod)
        val phone = Notifications(bob.account)
        phone.poll() // the feed's first page: nothing shown from it

        bob.auth.navigate(ShellDestination.PODS)
        bob.pods.open(pod)
        waitUntil("Bob's pod setup, without ✕s") { bob.pods.state.detail?.id == pod && bob.showsText("Members (2/8)") }
        assertTrue(!bob.shows(hasContentDescription("Remove", substring = true)) && !bob.shows(hasTestTag("pod-add-bot")), bob.screen())
        bob.pods.showList()
        bob.auth.navigate(ShellDestination.GAMES)

        alice.auth.navigate(ShellDestination.PODS)
        alice.pods.open(pod)
        waitUntil("Alice's pod setup with Add bot") { alice.shows(hasTestTag("pod-add-bot")) }
        // DRF-296: Add bot twice, then the ✕ on Bot 2
        for (bots in 1..2) {
            alice.tagged("pod-add-bot").performScrollTo().performClick()
            waitUntil("$bots bots shown") { alice.shows(hasContentDescription("Remove Bot $bots")) }
        }
        waitUntil("two bots saved") { alice.pods.state.pending == 0 && server().settings.bots == 2 }
        alice.tagged("pod-remove-bot-2").performScrollTo().performClick()
        waitUntil("one bot left, saved") {
            !alice.shows(hasContentDescription("Remove Bot 2")) && alice.pods.state.setup == null && alice.pods.state.pending == 0 &&
                server().settings.bots == 1 && alice.showsText("Members (3/8)")
        }

        // DRF-307: the ✕ on Carol's invitation cancels it
        alice.tagged("pod-cancel-invitation-${carol.id}").performScrollTo().performClick()
        waitUntil("Carol's invitation cancelled") {
            server().pendingInvitations.isEmpty() && !alice.showsText(carol.identity.username)
        }
        runBlocking { org.asyncttwiz.android.PodApi(E2eStack.origin).use { api ->
            assertTrue(api.list(carol.token).invitations.none { it.podId == pod }, "the invitation left Carol's Pods list")
        } }

        // DRF-308: the ✕ on Bob asks first; confirmed, Bob is removed
        alice.tagged("pod-remove-member-${bob.id}").performScrollTo().performClick()
        waitUntil("the removal's confirmation") { alice.showsText("Remove ${bob.account.identity.username} from $name?") }
        alice.tagged("pod-remove-confirm").performClick()
        waitUntil("Bob removed") {
            server().members.map { it.account.accountId } == listOf(alice.id) && alice.showsText("Members (2/8)")
        }
        // Bob, away from the Pods screen, gets the notice on his phone; the pod has left his Pods list, and showing it clears the
        // notice (LOB-243)
        waitUntil("Bob's phone shows Removed from the pod") { phone.poll(); phone.title("pod:$pod") == "Removed from $name" }
        bob.auth.navigate(ShellDestination.PODS)
        bob.pods.refresh()
        waitUntil("the pod gone from Bob's list") { bob.pods.state.loaded && bob.pods.state.pods.none { it.id == pod } && bob.showsText("MY PODS (0)") }
        waitUntil("the notice cleared once Bob showed the Pods list") { phone.poll(); phone.title("pod:$pod") == null }

        // Re-invited from the picker, Bob joins again
        alice.described("Invite player").performScrollTo().performClick()
        waitUntil("the invite picker with Bob") { alice.pods.state.choosingMember && alice.shows(hasTestTag("pod-invite-${bob.id}")) }
        alice.tagged("pod-invite-${bob.id}").performClick()
        waitUntil("Bob invited again") {
            server().pendingInvitations.any { it.accountId == bob.id } &&
                alice.shows(hasTestTag("pod-invite-status-${bob.id}") and hasText("invited"))
        }
        alice.activity.onBackPressedDispatcher.onBackPressed()
        runBlocking { org.asyncttwiz.android.PodApi(E2eStack.origin).use { it.accept(bob.account.token, pod, PodRequest(id())) } }
        bob.pods.refresh()
        waitUntil("the pod back in Bob's list") { bob.pods.state.pods.any { it.id == pod } }
        assertEquals(setOf(alice.id, bob.id), ServerSetup.pod(alice.account, pod).members.map { it.account.accountId }.toSet())
    }
}
