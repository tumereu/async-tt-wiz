package org.asyncttwiz.e2e

import android.app.NotificationManager
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.android.ActivityApi
import org.asyncttwiz.android.ActivityDelivery
import org.asyncttwiz.android.ActivityNotifications
import org.asyncttwiz.android.ActivityOwner
import org.asyncttwiz.android.ActivityWatermark
import org.asyncttwiz.android.ActivityWindowStore
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Notifications clear everywhere (owner, 2026-11-19): a notification clears on every device of its player once nothing waits
 * for them, however they dealt with it. Bob has a second phone: the app's notification code and its feed poll (the path
 * without push), and an installation with a push token, which the server sends a clear hint.
 */
class NotificationsClearEverywhereTest : E2e() {
    /** Bob's second phone: its notifications, its delivery state, and the feed poll the app's worker makes. */
    private inner class SecondPhone(val account: TestAccount) {
        val owner = ActivityOwner(account.id, UUID.randomUUID().toString())
        val notifications = ActivityNotifications(bob.activity).also {
            // The phone allows notifications (Android 13+ asks, LOB-188)
            org.robolectric.Shadows.shadowOf(bob.activity.application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
            it.channels()
        }
        private var cursor: String? = null
        private val marks = mutableMapOf<String, ActivityWatermark>()
        private var pending = mapOf<String, PendingActivity>()
        val delivery = ActivityDelivery(object : ActivityWindowStore {
            override fun cursor() = cursor
            override fun watermark(key: String) = marks[key]
            override fun save(cursor: String, marks: Map<String, ActivityWatermark>): Boolean {
                this@SecondPhone.cursor = cursor; this@SecondPhone.marks += marks; return true
            }
            override fun pending() = pending
            override fun saveWindow(cursor: String, marks: Map<String, ActivityWatermark>, pending: Map<String, PendingActivity>): Boolean {
                this@SecondPhone.pending = pending; return save(cursor, marks)
            }
        }) { notice -> notifications.post(owner, notice) }.also { it.bind(owner) }

        /** One poll, as the app's worker makes it (ActivityWorker): pages applied, then what no longer waits cleared. */
        fun poll() = runBlocking {
            ActivityApi(E2eStack.origin).use { api ->
                do {
                    val page = api.page(account.token, cursor)
                    check(delivery.apply(owner, page) { true })
                    notifications.settle(owner, page, delivery)
                } while (page.hasMore)
            }
        }

        private var polled = 0L
        /** [poll] at most once a second, as a check in a wait (the server limits the feed's requests per minute). */
        fun pollNowAndThen() { if (System.nanoTime() - polled > 1_000_000_000L) { polled = System.nanoTime(); poll() } }

        fun shown(): Set<String> = bob.activity.getSystemService(NotificationManager::class.java).activeNotifications
            .mapNotNull { it.tag?.removePrefix("activity:${owner.binding}:")?.takeIf { key -> key != it.tag } }.toSet()

        /** The title of the notification of [key] showing on this phone, null when none shows. */
        fun title(key: String): String? = bob.activity.getSystemService(NotificationManager::class.java).activeNotifications
            .singleOrNull { it.tag == "activity:${owner.binding}:$key" }?.notification?.extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()

        /** Registers an installation with a push token for [account], which the server sends clear hints; its generation. */
        fun registerPush(): String = runBlocking {
            ActivityApi(E2eStack.origin).use { api ->
                api.register(account.token, RegisterInstallation(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                    "e2e-push-token-${UUID.randomUUID()}")).generation
            }
        }
    }

    // LOB-243, DRF-138 (owner, 2026-11-20): Bob's second phone shows his pod's "Decks ready"; he opens the pod in the app on his
    // first phone; the server queues a clear for his push installation, and the second phone's next poll clears the notification
    @Test fun podNewsOpenedOnOnePhoneClearsOnTheOther() {
        launchPlayers()
        val phone = SecondPhone(bob.account)
        val generation = phone.registerPush()
        phone.poll() // the feed's first page: nothing shown from it
        val name = "E2E pod news ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftedPod(alice.account, bob.account, name)
        ServerSetup.submitPodDeck(bob.account, pod)
        ServerSetup.submitPodDeck(alice.account, pod) // the last submitter: "Decks ready" goes to Bob
        val key = "pod:$pod"
        waitUntil("Bob's second phone shows Decks ready") {
            phone.pollNowAndThen(); phone.title(key)?.startsWith("Decks ready") == true
        }

        // Bob opens the pod on his first phone
        bob.auth.navigate(org.asyncttwiz.android.ShellDestination.PODS)
        bob.pods.open(pod)
        waitUntil("a clear queued for Bob's push installation") {
            E2eStack.query("SELECT kind FROM activity_push WHERE generation = '$generation' AND game_id = '$pod'").contains("CLEAR_POD")
        }
        assertTrue(key in phone.shown(), "still shown until the phone hears of it")
        phone.poll()
        assertEquals(emptySet(), phone.shown().filter { it == key }.toSet(), "the pod's news cleared")
    }

    // DRF-135, DRF-122 (owner, 2026-11-20): one notification per pod: Bob's phone shows "Draft complete: build your deck";
    // Alice challenges him while he still builds; the challenge replaces it in the pod's one notification, no second one
    @Test fun aPodChallengeReplacesDraftCompleteInThePodsOneNotification() {
        launchPlayers()
        val phone = SecondPhone(bob.account)
        phone.poll()
        val name = "E2E one per pod ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftedPod(alice.account, bob.account, name)
        val key = "pod:$pod"
        waitUntil("Bob's phone shows Draft complete") {
            phone.pollNowAndThen(); phone.title(key)?.startsWith("Draft complete") == true
        }
        ServerSetup.submitPodDeck(alice.account, pod)
        val game = ServerSetup.challenge(alice.account, bob.account, pod, "E2E challenge while building")
        waitUntil("the challenge in the pod's notification") {
            phone.pollNowAndThen(); phone.title(key)?.contains("challenged you") == true
        }
        assertEquals(setOf(key), phone.shown(), "one notification for the pod, none of the invitation's own")
        assertTrue(game.invitationHeld)
    }

    // LOB-242, LOB-243, LOB-078: Alice invites Bob; his second phone shows the invitation's notification; Bob accepts it in the
    // app on his first phone; the server queues a clear for his installation with a push token, and the second phone's next
    // poll clears the notification
    @Test fun anInvitationAnsweredOnOnePhoneClearsOnTheOther() {
        launchPlayers()
        val phone = SecondPhone(bob.account)
        val generation = runBlocking {
            ActivityApi(E2eStack.origin).use { api ->
                api.register(bob.account.token, RegisterInstallation(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                    "e2e-push-token-${UUID.randomUUID()}")).generation
            }
        }
        phone.poll() // the feed's first page: nothing shown from it
        val name = "E2E notified ${UUID.randomUUID().toString().take(6)}"
        val game = runBlocking {
            org.asyncttwiz.android.LobbyApi(E2eStack.origin).use { api ->
                api.add(alice.account.token, AddContact(UUID.randomUUID().toString(), bob.account.id))
                api.create(alice.account.token, CreateInvitation(UUID.randomUUID().toString(), bob.account.id, name))
            }
        }
        val key = "invite:${game.invitationId}"
        waitUntil("the invitation's notification on Bob's second phone") { phone.pollNowAndThen(); key in phone.shown() }

        // Bob accepts it on his first phone, from the Games list
        waitUntil("Bob's menu") { bob.shows(hasTestTag("menu-GAMES")) }
        bob.tagged("menu-GAMES").performClick()
        val accept = hasAnyChild(hasContentDescription("Accept invitation")) and isEnabled()
        waitUntil("the invitation in Bob's list") { bob.showsText(name) && bob.shows(accept) }
        bob.node(accept).performClick()
        waitUntil("the invitation accepted") {
            ServerSetup.head(bob.account, game.id).summary.invitationState == InvitationState.ACCEPTED
        }

        // The server's sweep queues the clear hint for the installation with a push token (no push service in this stack)
        waitUntil("a clear queued for Bob's push installation") {
            E2eStack.query("SELECT kind FROM activity_push WHERE generation = '$generation' AND game_id = '${game.invitationId}'")
                .contains("CLEAR_INVITE")
        }
        // The second phone's next poll (the path without push, and what a clear hint starts) removes the notification
        assertTrue(key in phone.shown(), "still shown until the phone hears of it")
        phone.poll()
        assertEquals(emptySet(), phone.shown().filter { it == key }.toSet(), "the answered invitation's notification cleared")
    }
}
