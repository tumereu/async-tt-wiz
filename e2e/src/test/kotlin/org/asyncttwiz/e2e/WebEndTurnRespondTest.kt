package org.asyncttwiz.e2e

import arcanum.api.TurnStep
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner problem report UJP7DE (2026-11-26): the other player, on the desktop web client, couldn't respond to the owner's spell
 * cast at their end step. They had ended their turn (End turn, TRN-057), and their device went on passing their priority
 * over the spell, and over it again each time it was taken back and cast anew. Now the other player's spell ends the End
 * turn (CR 733.2b, 733.2c) and the browser waits for its player's answer. Owner 2026-11-27: so does anything else of the other
 * player's but a pass, their land and their attack included, whoever's turn it is.
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
                // Bob holds priority at her end step (he could respond): at his playback's stop at her device's assumption of his
                // pass there, or as his own move when his phone got there before her assumption did. Nobody presses for him
                // meanwhile (playing along would press To next turn there: gate run 2026-11-26)
                waitUntil("Bob holding priority at Alice's end step") {
                    bob.state.let { s -> s.turn.active == alice && s.turn.step == TurnStep.End && s.priority.holder == bob.me } &&
                        (bob.status is GameDevice.Status.Stopped || bob.status == GameDevice.Status.YourMove)
                }
                bob.playFromHand("Idle Glimmer", this)
                waitUntil("Bob's Glimmer on the Stack at Alice's end step, on the server") {
                    bob.state.let { s -> s.stack.size == 1 && s.turn.step == TurnStep.End && s.turn.active == alice } &&
                        bob.engine { device.session.pending.isEmpty() }
                }
                // HIS-097, HIS-190: when his cast took back her assumption of his pass, her table waits at the branch point for
                // her click on the centre text, which plays his cast back (in the report her device then passed over it at once)
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
                waitUntil("Bob's Glimmer resolved on his phone") {
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

    // TRN-168, TRN-057, HIS-058, WEB-031 (owner 2026-11-27: "if an opponent does anything else except pass at their priority, your
    // held End turn cancels and you resume play, no matter whose turn it is"): Alice, in her browser on Control, ends the turn in
    // Bob's turn. Bob attacks with his Ember Dasher: her End turn is over, and her browser stops for her priority after the attack,
    // then after the blocks. She ends the turn again at the end of combat; Bob plays a Mountain in his second main phase: over
    // again, so when Bob ends his turn her browser stops for her priority there. Bob's phone waits for her real passes (his Assume
    // Never), and nothing passes for her at those stops
    @Test fun theOtherPlayersAttackAndLandEndYourEndTurnInTheBrowser() {
        startGame(TestDeck.glimmers(), TestDeck("Dashers", listOf(TestCards.mountain to 20, TestCards.dasher to 20)), bobStarts = true,
            alicePhone = false, aliceSettings = PlaySettings(mode = ResponseMode.FullControl),
            bobSettings = PlaySettings().assuming(AssumeResolves.Never),
            deal = { _, b -> "Mountain" in hand(b) && "Ember Dasher" in hand(b) })
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
                /** Whether Alice's browser stands at her priority at [step] of Bob's turn, Bob's phone waiting for her. */
                fun aliceAt(step: TurnStep) = bob.state.let { s -> s.turn.active == bob.me && s.turn.step == step && s.priority.holder == alice } &&
                    bob.status is GameDevice.Status.Waiting && bob.settled(this) && web.mainWords() != null
                var pressed = 0L
                /** Alice passes at each of her stops (Space) until [done]. */
                fun passUntil(what: String, done: () -> Boolean) = waitUntil(what, timeoutSeconds = 120) {
                    if (done()) true else {
                        val s = bob.state
                        if (s.priority.holder == alice && bob.status is GameDevice.Status.Waiting && bob.settled(this) && web.mainWords() != null &&
                            System.nanoTime() - pressed > 2_000_000_000L) { web.page.keyboard().press("Space"); pressed = System.nanoTime() }
                        false
                    }
                }
                /** Alice's stop at [step] holds: nothing passes for her for a few seconds. */
                fun holds(step: TurnStep) {
                    repeat(3) { run(skip = Duration.ofSeconds(1)) }
                    web.page.waitForTimeout(1_000.0)
                    assertTrue(aliceAt(step), "still Alice's priority at $step: ${bob.state.turn} ${bob.state.priority} ${bob.status}")
                }
                passUntil("Bob's Main 1") { bob.inOwnMain() }
                bob.playFromHand("Ember Dasher", this)
                passUntil("Bob's Dasher resolved, his Main 1 again") { bob.field("Ember Dasher").isNotEmpty() && bob.inOwnMain() }
                bob.tap("To combat")
                waitUntil("Alice's priority in Bob's Main 1") { aliceAt(TurnStep.PrecombatMain) }
                // WEB-031: Shift+Space is End turn
                web.page.keyboard().press("Shift+Space")
                waitUntil("Bob declaring attackers") { bob.state.turn.step == TurnStep.DeclareAttackers && bob.settled(this) }
                bob.stageAttacker("Ember Dasher", this)
                waitUntil("the Dasher staged") { bob.stepArrow() == "Attack (1)" }
                bob.tap("Attack (1)")
                waitUntil("Alice's priority after Bob's attack", timeoutSeconds = 90) {
                    aliceAt(TurnStep.DeclareAttackers) && bob.state.combat?.attackers?.isNotEmpty() == true
                }
                holds(TurnStep.DeclareAttackers)
                // The report-logging rule: her log says why her device stopped passing for her
                assertTrue(web.console.any { "End turn ends: the other player attacked" in it },
                    "Alice's log tells her End turn ended: ${web.console.filter { "End turn" in it }}")
                // She has no creatures, so no blocks to declare: her priority after the blocks, as usual
                passUntil("Alice's priority after the blocks") { aliceAt(TurnStep.DeclareBlockers) }
                holds(TurnStep.DeclareBlockers)
                passUntil("Alice's priority at the end of combat") { aliceAt(TurnStep.EndOfCombat) }
                web.page.keyboard().press("Shift+Space")
                waitUntil("Bob's Main 2") { bob.inOwnMain(TurnStep.PostcombatMain) && bob.settled(this) }
                bob.playFromHand("Mountain", this)
                waitUntil("Bob's Mountain, his Main 2 again") { bob.field("Mountain").isNotEmpty() && bob.inOwnMain(TurnStep.PostcombatMain) }
                assertTrue(web.console.any { "End turn ends: the other player played the land" in it },
                    "Alice's log tells her End turn ended: ${web.console.filter { "End turn" in it }}")
                bob.tap("End turn")
                waitUntil("Alice's priority in Bob's Main 2", timeoutSeconds = 90) { aliceAt(TurnStep.PostcombatMain) }
                holds(TurnStep.PostcombatMain)
                val faults = web.faults()
                assertTrue(faults.isEmpty(), "The page reported faults:\n${faults.joinToString("\n")}")
            } catch (failure: Throwable) {
                web.evidence("WebEndTurnRespondTest-attack-land")
                throw failure
            }
        }
    }
}
