package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Input
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner decisions of 2026-11-19, their overall flows on two phones: the other player's phone answering your one-answer questions
 * while you are on Quick (SYS-206), your phone staying in a step you acted in (STK-213), and choosing the cards a mana ability's
 * cost uses while paying (STK-214).
 */
class Decisions1119Test : E2e() {
    private val prod = TestCards.prod
    private val urn = TestCards.urn
    private val note = TestCards.blueNote

    // SYS-206, SYS-063 (owner 2026-11-19, item a819b9ffdd): Bob, on Quick, is away; Alice's Ember Prod meets his question "discard a
    // nonland card?", which has one answer (he holds only Mountains: no), and then his discard of two Mountains (any two are the same
    // answer). Alice's phone answers both for him instead of waiting, the server takes them, and she plays on
    @Test fun theOtherPlayersPhoneAnswersYourOneAnswerQuestionWhileYouAreOnQuick() {
        startGame(TestDeck("Prods", listOf(TestCards.mountain to 20, prod to 20)), TestDeck("Mountains", listOf(TestCards.mountain to 40)),
            aliceSettings = PlaySettings(assume = AssumeResolves.Always), bobSettings = PlaySettings(mode = ResponseMode.AutoSkip),
            deal = { a, _ -> "Ember Prod" in hand(a) })
        bothOpen()
        bothKeep(bobAway = true)
        waitUntil("Alice's phone knowing Bob is on Quick") { alice.engine { device.opponentMode } == ResponseMode.AutoSkip && alice.inOwnMain() }
        // LOB-135: his "no" is shown with his hand (he can't pay), so her assumption of his pass needs "Allow assumes that reveal hands"
        alice.tap("Table actions")
        waitUntil("Alice's table actions") { alice.showsText("Game settings") }
        alice.text("Game settings").performClick()
        waitUntil("the game settings") { alice.shows(androidx.compose.ui.test.hasTestTag("assume-hands")) }
        alice.tagged("assume-hands").performClick()
        waitUntil("hands allowed on her phone") { alice.engine { device.assumeReveals.hands } }
        alice.tap("Close")
        run()
        alice.playFromHand("Ember Prod", this)
        waitUntil("Alice's phone answered Bob's questions for him, Bob's two Mountains discarded, Alice playing on") {
            alice.engine { device.session.events.any { e -> e.author == alice.id &&
                e.engine?.steps.orEmpty().any { s -> (s.input as? Input.Answer)?.answer == Answer.YesNo(false) } } } &&
                alice.state.player(bob.me).graveyard.size == 2 && alice.state.stack.isEmpty() && alice.status == GameDevice.Status.YourMove
        }
        waitUntil("all of it on the server") { serverGame()?.headId == alice.engine { device.session.head } }
        assertTrue(alice.leaks.leaks.isEmpty())
    }

    // STK-213, STK-171 (owner 2026-11-19, item dea2ae3ac4): both in Normal at the table; Bob casts a Drowsy Pup in his main phase and Alice
    // answers it with an Idle Glimmer. Once the Pup has resolved Bob moves on from his main phase: there, where Normal passes for her,
    // Alice's phone stops for her own press because she acted in that step; her press moves the game on to his combat
    @Test fun afterActingInAStepYourPhonePassesNothingThereUntilYouPress() {
        startGame(TestDeck("Glimmers", listOf(TestCards.mountain to 20, TestCards.glimmer to 20)),
            TestDeck("Pups", listOf(TestCards.mountain to 20, TestCards.drowsy to 20)), bobStarts = true,
            aliceSettings = PlaySettings(mode = ResponseMode.Normal, assume = AssumeResolves.Never),
            bobSettings = PlaySettings(mode = ResponseMode.Normal, assume = AssumeResolves.Never),
            deal = { a, b -> "Idle Glimmer" in hand(a) && "Drowsy Pup" in hand(b) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1") { bob.inOwnMain() && bob.settled(this) }
        bob.playFromHand("Drowsy Pup", this)
        waitUntil("Alice stopped at Bob's Pup on the Stack") {
            alice.settled(this) && alice.status == GameDevice.Status.YourMove && alice.state.stack.isNotEmpty() && alice.state.priority.holder == alice.me
        }
        alice.playFromHand("Idle Glimmer", this)
        waitUntil("the Glimmer and the Pup resolved, Bob in his main phase again") {
            if (bob.settled(this) && bob.status == GameDevice.Status.YourMove && bob.state.stack.isNotEmpty() && bob.state.priority.holder == bob.me)
                bob.stepArrow()?.takeIf { it == "Resolve" }?.let(bob::tap)
            if (alice.settled(this) && alice.status == GameDevice.Status.YourMove && alice.state.stack.isNotEmpty() && alice.state.priority.holder == alice.me)
                alice.stepArrow()?.takeIf { it == "Resolve" }?.let(alice::tap)
            bob.field("Drowsy Pup").isNotEmpty() && bob.inOwnMain() && bob.settled(this) && bob.state.stack.isEmpty()
        }
        bob.tap("To combat")
        waitUntil("Alice's phone stops for her press in Bob's main phase") {
            alice.settled(this) && alice.status == GameDevice.Status.YourMove && alice.state.turn.step == TurnStep.PrecombatMain &&
                alice.state.turn.active == bob.me && alice.state.priority.holder == alice.me && alice.state.stack.isEmpty()
        }
        run(skip = Duration.ofSeconds(2))
        assertEquals(TurnStep.PrecombatMain, alice.state.turn.step, "nothing passed for her")
        alice.tap(requireNotNull(alice.stepArrow()))
        waitUntil("on past Bob's main phase") { bob.state.turn.step != TurnStep.PrecombatMain && bob.settled(this) }
    }

    // STK-214, STK-211, STK-191, CR 601.2h (owner 2026-11-19): Autotap off, Alice casts her Blue Note with her Ash Urn out and two cards in her
    // graveyard: a tap on the Urn on the table opens the panel's choice of the card it exiles; she picks one, OK, and the payment line names
    // it with Change; Cast casts it, that card exiled
    @Test fun thePayerChoosesTheCardsAManaAbilitysCostUses() {
        startGame(TestDeck("Urn", listOf(urn to 10, TestCards.study to 20, note to 10)), TestDeck.thirds(),
            aliceSettings = PlaySettings(autotap = false),
            deal = { a, _ -> "Ash Urn" in hand(a) && "Blue Note" in hand(a) && hand(a).count { it == "Ember Reading" } >= 2 })
        bothOpen()
        bothKeep(bobAway = true)
        alice.playFromHand("Ash Urn", this)
        waitUntil("the Urn on the battlefield") { alice.field("Ash Urn").isNotEmpty() && alice.inOwnMain() && alice.settled(this) }
        repeat(2) { n ->
            alice.playFromHand("Ember Reading", this)
            waitUntil("Reading ${n + 1} in the graveyard") { alice.state.player(alice.me).graveyard.size == n + 1 && alice.inOwnMain() && alice.settled(this) }
        }
        val graveyard = alice.state.player(alice.me).graveyard
        alice.playFromHand("Blue Note", this)
        waitUntil("the panel, nothing paying") { alice.shows(hasContentDescription("Not paid: Still owed {U}", substring = true)) }
        alice.tap("Hide cast panel")
        waitUntil("the panel hidden") { alice.showsControl("Show cast panel") }
        alice.battlefieldCard("Ash Urn").performClick()
        run()
        alice.tap("Show cast panel")
        val inPick = hasAnyAncestor(hasContentDescription("Cards for Ash Urn's cost", substring = true))
        waitUntil("the choice of the Urn's card") { alice.shows(hasContentDescription("Ember Reading", substring = true) and inPick) }
        alice.nodes(hasContentDescription("Ember Reading", substring = true) and inPick).fetchSemanticsNodes().let { cards ->
            alice.node(androidx.compose.ui.test.SemanticsMatcher("the last card") { it.id == cards.last().id }).performClick()
        }
        val ok = (hasContentDescription("OK") or androidx.compose.ui.test.hasText("OK")) and inPick and
            !androidx.compose.ui.test.hasTestTag("main-button")
        waitUntil("the choice's own OK") { alice.shows(ok) }
        alice.node(ok).performClick()
        waitUntil("the Urn paying with her card, Cast enabled") {
            !alice.shows(inPick) && alice.shows(hasContentDescription("Change the cards", substring = true))
        }
        alice.tap("Cast")
        waitUntil("the Blue Note cast, the chosen card exiled") {
            alice.state.objects.values.count { it.zone == arcanum.api.Zone.Exile && it.owner == alice.me } == 1 && alice.settled(this)
        }
        val exiled = alice.state.objects.values.single { it.zone == arcanum.api.Zone.Exile && it.owner == alice.me }
        assertTrue(exiled.card in graveyard.map { (it as arcanum.engine.state.ObjectId.Card).card }, "a card of her graveyard")
        val cast = alice.engine { device.session.events.last { e -> e.engine?.steps.orEmpty().any { (it.input as? Input.Cast) != null } } }
        val used = cast.engine!!.steps.mapNotNull { (it.input as? Input.Answer)?.answer as? Answer.ManaPayment }.single().uses
            .filterIsInstance<ManaUse.Ability>().single()
        assertEquals(1, used.answers.filterIsInstance<Answer.Objects>().single().chosen.size)
    }
}
