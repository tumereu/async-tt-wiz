package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.asyncttwiz.android.PodTab
import org.asyncttwiz.android.ShellDestination
import org.junit.Test
import java.util.UUID
import kotlin.test.assertFalse

/**
 * Game history (spec game-history, owner 2026-11-07), the overall flow: a match that ends is recorded, and both players
 * find it in the game's lobby History tab and on Stats; a pod's match shows in the pod's Games tab, without decks, and in
 * its standings.
 */
class MatchHistoryTest : E2e() {
    /** [player] concedes from Table actions (TRN-064), confirming the dialog. */
    private fun concede(player: E2ePlayer) {
        player.tap("Table actions")
        waitUntil("${player.name}'s table actions") { player.showsText("Concede") }
        player.text("Concede").performClick()
        waitUntil("the Concede dialog") { player.showsText("You lose this game at once.") }
        // The menu closed; the dialog's button is the only "Concede" left
        player.text("Concede").performClick()
    }

    /** [player]'s game lobby History tab shows one match with [result] and "T<turns> · Conceded". */
    private fun lobbyHistory(player: E2ePlayer, result: String, turns: Int) {
        waitUntil("${player.name}'s lobby after the match") { player.shows(hasTestTag("lobby-history-tab")) }
        player.tagged("lobby-history-tab").performClick()
        waitUntil("the match in ${player.name}'s History tab") {
            player.showsText(result) && player.showsText("T$turns · Conceded", substring = true)
        }
    }

    /** [player]'s Stats screen shows the match with [result] and its turns. */
    private fun stats(player: E2ePlayer, result: String, turns: Int) {
        player.auth.navigate(ShellDestination.MENU)
        waitUntil("${player.name}'s menu") { player.shows(hasTestTag("menu-STATS")) }
        player.tagged("menu-STATS").performClick()
        waitUntil("the match on ${player.name}'s Stats") {
            player.showsText(result) && player.showsText("T$turns · Conceded", substring = true)
        }
    }

    // LOB-126, LOB-129, LOB-130 (TRN-064): Alice concedes in turn 1; both see the match in the lobby's History tab and on Stats,
    // Alice a loss, Bob a win
    @Test fun aConcededMatchShowsInTheLobbyHistoryAndOnStats() {
        startGame()
        bothOpen()
        bothKeep(bobAway = false)
        concede(alice)
        lobbyHistory(alice, "Loss", 1)
        lobbyHistory(bob, "Win", 1)
        stats(alice, "Loss", 1)
        stats(bob, "Win", 1)
    }

    // DRF-121, LOB-126: a pod match ends by Alice's concession; the pod's Games tab shows it in its matches, without decks,
    // and in the standings (Bob 1–0–0 first, Alice 0–1–0)
    @Test fun aPodMatchShowsInThePodGamesTabWithoutDecks() {
        launchPlayers()
        val name = "E2E history ${UUID.randomUUID().toString().take(6)}"
        val game = ServerSetup.readyBoth(alice.account, bob.account, ServerSetup.podGame(alice.account, bob.account, name))
        alice.openGame(game, this)
        concede(alice)
        waitUntil("Alice's lobby after the match") { alice.shows(hasTestTag("lobby-history-tab")) }
        alice.auth.navigate(ShellDestination.MENU)
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        alice.tagged("menu-PODS").performClick()
        waitUntil("the pod in Alice's list") { alice.showsText("$name pod") }
        alice.text("$name pod").performScrollTo().performClick()
        waitUntil("the pod open") { alice.pods.state.detail?.name == "$name pod" }
        alice.pods.tab(PodTab.GAMES)
        waitUntil("the pod's standings and match") {
            alice.shows(hasTestTag("pod-standings")) && alice.showsText("${bob.name} won")
        }
        alice.text("${bob.name} won").performScrollTo()
        assertFalse(alice.showsText("$name pod deck", substring = true), "no deck names in the pod's matches")
        waitUntil("the standings") { alice.showsText("1–0–0") && alice.showsText("0–1–0") }
    }
}
