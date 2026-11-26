package org.asyncttwiz.e2e

import arcanum.api.TurnStep
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner problem report UJP7DE (2026-11-26): the other player, on the desktop web client, couldn't respond to the owner's spell
 * cast at their end step. They had ended their turn (End turn, TRN-057), and their device went on passing their priority
 * over the spell, and over it again each time it was taken back and cast anew. Now the other player's spell ends the End
 * turn (CR 733.2b, 733.2c) and the browser waits for its player's answer.
 */
class WebEndTurnRespondTest : E2e() {
    // TRN-168, TRN-057, HIS-058, WEB-031, CR 733.2b, CR 733.2c (report UJP7DE): Alice, in her browser, ends her turn with Shift+Space; Bob
    // (live on his phone) casts an Idle Glimmer at her end step instead of passing. Her browser stops with his spell on the
    // Stack: her priority, "Resolve" on her main button, nothing passed for her meanwhile, her log saying her End turn ended;
    // her click on Resolve lets it resolve
    @Test fun theOtherPlayersSpellAtYourEndStepWaitsForYourAnswerInTheBrowser() {
        startGame(TestDeck.glimmers(), TestDeck.glimmers(), alicePhone = false,
            deal = { a, b -> "Idle Glimmer" in hand(a) && "Idle Glimmer" in hand(b) })
        bob.openGame(game, this)
        val alice = PlayerId(aliceAccount.id)
        E2eBrowser(width = 1440, height = 900).use { web ->
            try {
                web.signIn(aliceAccount, "/games/${game.id}")
                waitUntil("Keep among Alice's quick actions in her browser", timeoutSeconds = 180) { web.showsControl("Keep") }
                web.tap(web.control("Keep"))
                waitUntil("Bob's phone holding Alice's keep from her browser") {
                    bob.shown.held.any { it.author == aliceAccount.id && it.assumed == null }
                }
                bob.tap("Keep")
                waitUntil("Alice's Main 1 in her browser") { web.mainWords()?.contains("To combat") == true }
                // WEB-031: Shift+Space is End turn
                web.page.waitForTimeout(500.0)
                web.page.keyboard().press("Shift+Space")
                // Bob's playback stops at her end step (her device assumed his pass there; he could respond): he casts there
                waitUntil("Bob stopped at Alice's end step", along = listOf(bob)) {
                    bob.state.let { s -> s.turn.active == alice && s.turn.step == TurnStep.End && s.priority.holder == bob.me } &&
                        bob.status is GameDevice.Status.Stopped
                }
                bob.playFromHand("Idle Glimmer", this)
                waitUntil("Bob's Glimmer on the Stack at Alice's end step, on the server") {
                    bob.state.let { s -> s.stack.size == 1 && s.turn.step == TurnStep.End && s.turn.active == alice } &&
                        bob.engine { device.session.pending.isEmpty() }
                }
                // HIS-097, HIS-190: his cast took back her assumption of his pass: her table waits at the branch point for her
                // click on the centre text, which plays his cast back (in the report her device then passed over it at once)
                val centre = "#centre-action"
                var clicked = 0L
                waitUntil("Resolve on Alice's main button in her browser", timeoutSeconds = 90) {
                    if (web.shows(centre) && System.nanoTime() - clicked > 2_000_000_000L) { web.tap(centre); clicked = System.nanoTime() }
                    !web.shows(centre) && web.mainWords()?.startsWith("Resolve") == true
                }
                // Nothing passes it for her meanwhile
                repeat(5) { run(skip = Duration.ofSeconds(1)) }
                web.page.waitForTimeout(2_000.0)
                bob.state.let { s ->
                    assertEquals(1, s.stack.size, "Bob's Glimmer still on the Stack on his phone")
                    assertEquals(TurnStep.End, s.turn.step)
                    assertEquals(alice, s.priority.holder, "Alice holds priority")
                }
                assertTrue(bob.status is GameDevice.Status.Waiting, "Bob's phone waits for Alice: ${bob.status}")
                assertTrue(web.mainWords()?.startsWith("Resolve") == true, "still Resolve: ${web.mainWords()}")
                // The report-logging rule: her log says why her device stopped passing for her
                assertTrue(web.console.any { "End turn ends: the other player cast" in it },
                    "Alice's log tells her End turn ended: ${web.console.filter { "End turn" in it }}")
                // Her answer: Resolve; the Glimmer resolves (Bob draws)
                val bobHand = bob.state.player(bob.me).hand.size
                web.tap("#main-button")
                web.cursorAt(560.0, 300.0)
                waitUntil("Bob's Glimmer resolved on his phone", along = listOf(bob)) {
                    bob.state.let { s -> s.stack.isEmpty() && s.player(bob.me).hand.size == bobHand + 1 }
                }
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebEndTurnRespondTest")
                throw failure
            }
        }
    }
}
