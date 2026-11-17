package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.state.NamedValue
import arcanum.engine.state.Secrets
import arcanum.engine.state.TargetRef
import org.asyncttwiz.android.EngineEventText
import org.asyncttwiz.protocol.game.EnginePreferences
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-16 (burn run, A Killer Among Us): a secret choice was shown to the other player, and its reveal said nothing in
 * the history. Alice plays an Ember Lineup ("… Then secretly choose Human, Merfolk, or Goblin.") and chooses Goblin: her Inspect
 * of it says so, Bob's says only "Secret choice", and nothing on his table names the type. On her next turn she attacks with
 * her Goblin token and sacrifices the Lineup, revealing the type as its cost: Bob's table names what was revealed.
 */
class SecretChoiceTest : E2e() {
    private val lineups = TestDeck("Lineups", listOf(TestCards.mountain to 20, TestCards.lineup to 20))
    private val pups = TestDeck("Pups", listOf(TestCards.mountain to 20, TestCards.pup to 20))

    /** Every text Bob's table showed or would show of the events he was shown so far (the event chip's, the timeline's). */
    private fun bobsTexts(): List<String> = bob.shownTexts.toList() + bob.engine { device.session.events.toList() }
        .map { EngineEventText.of(it, bob.shown.engine, bob.id, names()) }

    // TBL-133, HIS-091, HIS-124, HIS-062, SYS-039 (owner 2026-11-16; CR 607.2d, CR 118.3): a secret choice shows only to its chooser until the rules reveal
    // it; the event that reveals it names it on both tables
    @Test fun aSecretChoiceStaysHiddenFromTheOtherPlayerUntilItIsRevealed() {
        // The deal follows each run's random account ids (per-player random streams since 336be95d5): Bob's Mountain is asked for,
        // as playing along through his turn he plays one if he holds it, else his cleanup's discard waits for him
        startGame(lineups, pups, bobSettings = PlaySettings(), deal = { a, b -> "Ember Lineup" in hand(a) && "Mountain" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Lineup", this)
        val goblin = hasClickAction() and hasAnyDescendant(hasText("Goblin"))
        waitUntil("Alice choosing a type", along = listOf(bob)) {
            if (alice.stepArrow() == "Resolve") alice.once { alice.tap("Resolve") }
            alice.state.pending?.decision is Decision.ChooseSubtype && alice.shows(goblin)
        }
        alice.node(goblin).performClick()
        waitUntil("the Lineup kept Goblin, secretly", along = listOf(bob)) {
            val lineup = alice.field("Ember Lineup").firstOrNull()?.let { alice.state.obj(it) }
            lineup != null && alice.state.pending == null && alice.state.stack.isEmpty() && alice.inOwnMain() &&
                lineup.linked["type"] == NamedValue.SubtypeValue(arcanum.api.Subtype("Goblin")) && Secrets.isSecret(lineup, "type")
        }
        // Alice's Inspect names her choice
        holding(alice.battlefieldCard("Ember Lineup")) {
            assertTrue(alice.showsText("Chosen: Goblin (secret)"), alice.screen())
        }
        // Bob's says one was made, never what: nor does anything else on his table
        waitUntil("the Lineup and its tokens on Bob's table") {
            bob.showsOnBattlefield("Ember Lineup", alice) && bob.state.battlefield.size >= 4 && bob.state.stack.isEmpty()
        }
        holding(bob.battlefieldCard("Ember Lineup", owner = alice)) {
            assertTrue(bob.showsText("Secret choice"), bob.screen())
            assertFalse(bob.showsText("Chosen: Goblin", substring = true), bob.screen())
        }
        assertTrue(bobsTexts().none { "revealed" in it || "Goblin" in it }, "${bobsTexts()}")

        // Alice's next turn: she attacks with the Goblin token
        alice.hold("To combat")
        waitUntil("Alice's next Main 1", along = listOf(bob)) {
            if (alice.status is GameDevice.Status.Stopped) alice.once { alice.stepArrow()?.let(alice::tap) }
            alice.inOwnMain() && alice.state.turn.number >= 3
        }
        assertTrue(bobsTexts().none { "revealed" in it || "Goblin" in it }, "still nothing of it: ${bobsTexts()}")
        alice.tap("To combat")
        waitUntil("Alice declaring attackers") {
            alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack" && alice.settled(this)
        }
        // Her attack; then, with her priority in combat, the Lineup's ability with its target, the Goblin
        // token, as her phone's panels make them (an event of its own)
        val token = alice.field("Goblin Token").single()
        assertFalse(alice.game!!.send(listOf(Input.DeclareAttackers(alice.me, listOf(Attack(token, TargetRef.Player(bob.me)))))) is GameDevice.Acted.Refused)
        waitUntil("Alice's priority with her Goblin attacking") {
            // (Bob has no creatures: the game may go on to the blockers step, where she has priority again)
            alice.settled(this) && alice.state.let { s -> s.turn.step in listOf(TurnStep.DeclareAttackers, TurnStep.DeclareBlockers) && s.priority.holder == alice.me &&
                s.pending == null && s.combat?.attackers.orEmpty().isNotEmpty() }
        }
        val copy = alice.state.deepCopy()
        var input: Input = Engine.options(copy, alice.me, EnginePreferences.of(copy)).filterIsInstance<Input.Activate>()
            .first { it.source == alice.field("Ember Lineup").single() }
        val inputs = mutableListOf(input)
        for (i in 0 until 10) {
            Engine.perform(copy, input, EnginePreferences.of(copy)).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: break
            if (d is Decision.Random || Host.decider(copy, d.player) != alice.me) break
            val answers = Engine.options(copy, alice.me, EnginePreferences.of(copy)).filterIsInstance<Input.Answer>().filter { it.decision == d.id }
            input = if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
                else answers.firstOrNull { a -> (a.answer as? Answer.Targets)?.chosen == listOf(TargetRef.Object(token)) } ?: answers.first()
            inputs += input
        }
        assertFalse(alice.game!!.send(inputs) is GameDevice.Acted.Refused)

        // Bob's timeline names what was revealed, at the event that did it (live, his event chip shows only the newest of the
        // events shown together: here the ability resolving, as Alice's phone assumed his pass, HIS-076)
        val text = "${alice.name} activated Ember Lineup's ability · ${alice.name} revealed: Goblin"
        fun bobsTimeline() = bob.engine { device.session.events.toList() }.filter { EngineEventText.onTimeline(it, bob.id) }
            .map { EngineEventText.timelineText(it, bob.shown.engine, bob.id, names()) }
        try {
            waitUntil("the reveal on Bob's timeline", along = listOf(alice)) {
                text in bobsTimeline() && bob.state.player(alice.me).graveyard.any { cardName(bob.state, it) == "Ember Lineup" }
            }
        } catch (e: AssertionError) { throw AssertionError("Bob's timeline: ${bobsTimeline()}\n${e.message}", e) }
        assertEquals(1, bobsTexts().filter { "revealed" in it }.distinct().size, "named once: ${bobsTexts()}")
        // And on Alice's, in her words
        assertTrue(alice.engine { device.session.events.toList() }.map { EngineEventText.of(it, alice.shown.engine, alice.id, names()) }
            .contains("You activated Ember Lineup's ability · You revealed: Goblin"))
        assertFalse(alice.refusalSeen || bob.refusalSeen, "no refusal")
    }
}
