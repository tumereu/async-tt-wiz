package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.test.*

/** DRF-039 (owner, 2026-11-23): a game a cancelled draft orphaned from its pod belongs to the pod no more. */
class PodOrphanedGamesTest {
    private val alice = AccountIdentity("a", "Alice"); private val bob = AccountIdentity("b", "Bob")
    private fun game(id: String, pod: String? = "pod", orphaned: Boolean = false) = GameSummary(id, "Pod #$id", "", alice, bob, "round",
        "invitation", InvitationState.ACCEPTED, podId = pod, podName = pod?.let { "Pod" }, podOrphaned = orphaned)

    // DRF-121, DRF-039: the pod's Games tab lists its own games once each, not an orphaned one nor another pod's
    @Test fun thePodsGamesTabLeavesOutOrphanedGames() {
        val games = listOf(game("1"), game("2", orphaned = true), game("3", pod = "other"), game("4", pod = null), game("1"))
        assertEquals(listOf("1"), podTabGames(games, "pod").map { it.id })
    }

    // LOB-145, DRF-039: an orphaned game's chat has no Pod tab beside the game's own
    @Test fun anOrphanedGamesChatHasNoPodChat() {
        assertEquals("pod", game("1").chatPodId)
        assertNull(game("2", orphaned = true).chatPodId)
        assertNull(game("3", pod = null).chatPodId)
    }
}
