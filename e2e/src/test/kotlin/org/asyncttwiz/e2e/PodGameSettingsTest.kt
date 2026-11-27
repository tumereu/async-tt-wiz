package org.asyncttwiz.e2e

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.asyncttwiz.android.ShellDestination
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals

/** Owner 2026-11-26 (playtest F): a pod's Game settings, set by its creator in pod setup, are what its games begin with. */
class PodGameSettingsTest : E2e() {
    // DRF-319, LOB-282, LOB-135: in her pod's setup Alice turns "Reshuffle libraries on undo" off in its Game settings card on her
    // phone and it is saved with the pod; after the draft she challenges Bob: the game begins with it off, and Bob's phone shows it
    // off (greyed: Alice, the game's creator, changes it) in the game's lobby
    @Test fun aPodsGamesBeginWithItsGameSettings() {
        launchPlayers()
        val settings = DraftSettings(packsPerPlayer = 3, cardsPerPack = 2)
        val name = "E2E game settings ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.setupPod(alice.account, name, settings = settings)
        var read = 0L
        var last: PodDetail? = null
        fun server(): PodDetail {
            if (last == null || System.nanoTime() - read > 500_000_000L) { last = ServerSetup.pod(alice.account, pod); read = System.nanoTime() }
            return last!!
        }
        alice.auth.navigate(ShellDestination.PODS)
        alice.pods.open(pod)
        waitUntil("Alice's pod setup with its Game settings") { alice.pods.state.detail?.id == pod && alice.shows(hasTestTag("pod-game-settings")) }
        alice.tagged("reshuffle-undo").performScrollTo().performClick()
        waitUntil("the pod's game settings saved") {
            alice.pods.state.setup == null && alice.pods.state.pending == 0 && !server().settings.gameSettings.reshuffleOnUndo
        }
        assertEquals(AssumeReveals(reshuffleOnUndo = false), server().settings.gameSettings)
        alice.pods.showList()
        alice.auth.navigate(ShellDestination.GAMES)

        ServerSetup.joinAndStart(alice.account, bob.account, pod)
        ServerSetup.finishDraft(alice.account, bob.account, pod, settings)
        for (who in listOf(alice.account, bob.account)) ServerSetup.submitPodDeck(who, pod)
        val game = ServerSetup.challenge(alice.account, bob.account, pod, "$name game")
        assertEquals(AssumeReveals(reshuffleOnUndo = false), game.assumeReveals, "the game begins with the pod's settings")

        assertEquals(listOf("accepted"), ServerSetup.attempt(bob.account, game, GameAction.AcceptInvitation).map { it.reason ?: it.outcome })
        bob.auth.navigate(ShellDestination.GAMES)
        waitUntil("the game in Bob's Games list") { bob.showsText(game.gameName) }
        bob.nodes(hasText(game.gameName)).onLast().performScrollTo().performClick()
        waitUntil("Bob in the game's lobby with its Game settings") { bob.shows(hasTestTag("lobby-history-tab")) && bob.shows(hasTestTag("reshuffle-undo")) }
        bob.tagged("reshuffle-undo").performScrollTo().assertIsOff().assertIsNotEnabled()
    }
}
