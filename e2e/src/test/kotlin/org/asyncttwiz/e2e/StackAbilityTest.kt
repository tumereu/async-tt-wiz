package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import arcanum.engine.change.Change
import arcanum.engine.state.ObjectId
import org.asyncttwiz.android.EngineEventText
import org.asyncttwiz.android.PileContents
import org.asyncttwiz.android.StackAbilities
import org.asyncttwiz.android.StackAbilityShown
import org.asyncttwiz.android.TableBoard
import org.asyncttwiz.android.TablePresentation
import org.asyncttwiz.android.contentsEntries
import org.asyncttwiz.android.waitingText
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.MtgStackKind
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-04 (moved from the interim StackAbilityScenarioTest): a creature's enters trigger on the Stack
 * was drawn as the creature card, so it looked as if the spell were still waiting to resolve. Alice casts an Ember Herald
 * (an enters trigger) while Bob is away and plays on; Bob's playback stops at her spell, then at her trigger. Both on the
 * Stack and in every text the trigger is the Herald's ability, never the Herald itself.
 */
class StackAbilityTest : E2e() {
    private val heraldText = "When this creature enters, create a 1/1 red Elemental creature token."

    private fun top(play: TableBoard): String? = CombatStackRules.stack(play.board).cards.firstOrNull()

    /** Bob's playback goes on (the centre text's tap on the catch-up) until it stops. */
    private fun bobPlaysToAStop(what: String) = waitUntil(what) {
        if (bob.status == GameDevice.Status.CatchUp) bob.once { bob.tapToWatch() }
        bob.status is GameDevice.Status.Stopped
    }

    /** What the hold shows: Inspect with the ability's tile and the Herald's picture (one read of Bob's screen while the finger
     * is down). */
    private fun inspectsTheHeraldsAbility(where: String) {
        val shown = bob.screen()
        for (part in listOf("Card inspection content", "Triggered ability of Ember Herald", "Card image: Ember Herald"))
            assertTrue(part in shown, "$where: $part: $shown")
    }

    // TBL-104, STK-050, STK-046 (owner, 2026-11-05: not yet seen working on the phone): Bob's playback stops at Alice's
    // Herald trigger; holding the ability token on the Stack inspects it (its tile, then the Herald's picture); lifting
    // closes it; the Stack list names it as the Herald's ability
    @Test fun holdingAnAbilityOnTheStackInspectsIt() {
        aliceCastsAHeraldWhileBobIsAway()
        bob.back()
        bobPlaysToAStop("Bob's playback stopping at the spell")
        val spellStop = bob.status
        bob.tap("Resolve")
        waitUntil("Bob's playback stopping at the trigger") {
            if (bob.status == GameDevice.Status.CatchUp) bob.once { bob.tapToWatch() }
            bob.status.let { it is GameDevice.Status.Stopped && it != spellStop }
        }
        val token = bob.node(hasContentDescription("Triggered ability, Ember Herald — $heraldText", substring = true))
        holding(token) { inspectsTheHeraldsAbility("the Stack") }
        assertTrue(!bob.shows(hasContentDescription("Card inspection content")), "closed on release")
        // The Stack list's row is the same hold (EngineTableScreenshotTest.holdingAnAbilityOnTheStackShowsItsTileThenItsSourceCard);
        // here only that the list names it so
        bob.node(hasContentDescription("Stack, 1 entries")).performClick()
        waitUntil("the Stack list") { bob.showsText("Top · Ember Herald's ability") }
    }

    // STK-046, HIS-091, TRN-066 (owner playtest 2026-11-04): an enters trigger on the Stack is drawn and named as its
    // source's ability, for both players; the spell itself stays a card
    @Test fun anEntersTriggerShowsAsTheSourcesAbilityOnBothDevices() {
        val cast = aliceCastsAHeraldWhileBobIsAway()
        bob.back()

        // Bob's playback stops first at the spell: it is the Herald card, no ability
        bobPlaysToAStop("Bob's playback stopping at the spell")
        val spellBoard = bob.shown.board
        val spell = assertNotNull(top(spellBoard))
        assertEquals(MtgStackKind.Spell, spellBoard.board.stackEffects.single { it.id == spell }.item?.kind)
        assertNull(StackAbilities.of(spellBoard, spell, bob.id), "a spell is drawn as its card")
        assertEquals("Ember Herald", contentsEntries(spellBoard, PileContents("stack", "Stack", listOf(spell), true), bob.id).single().name)
        val spellStop = bob.status
        bob.tap("Resolve")

        // Then at the trigger: the Herald's ability, drawn as an ability token with its text, for both players
        waitUntil("Bob's playback stopping at the trigger") {
            if (bob.status == GameDevice.Status.CatchUp) bob.once { bob.tapToWatch() }
            bob.status.let { it is GameDevice.Status.Stopped && it != spellStop }
        }
        assertIs<GameDevice.Status.Stopped>(bob.status)
        val play = bob.shown.board
        val entry = assertNotNull(top(play))
        assertEquals(MtgStackKind.Triggered, play.board.stackEffects.single { it.id == entry }.item?.kind)
        val shown = StackAbilityShown(triggered = true, source = "Ember Herald", text = heraldText)
        assertEquals(shown, StackAbilities.of(play, entry, bob.id))
        assertEquals(shown, StackAbilities.of(play, entry, alice.id))
        val scene = TablePresentation.scene(game, play, bob.id,
            412f, 892f, false)
        val drawing = scene.items.single { it.id == "stackDrawing/$entry" }
        assertEquals(shown, drawing.stackAbility)
        assertTrue("Triggered ability, Ember Herald — $heraldText" in drawing.label, drawing.label)
        // Bob's screen draws it so
        assertTrue(bob.shows(hasContentDescription("Triggered ability, Ember Herald — $heraldText", substring = true)), bob.screen())
        // The Stack list and the target picker name it as an ability
        val listed = contentsEntries(play, PileContents("stack", "Stack", listOf(entry), true), bob.id).single()
        assertEquals("Ember Herald's ability", listed.name)
        assertEquals(shown, listed.ability)
        // The wait's words on Alice's side name the ability, not the card
        val item = play.engine!!.state.stack.last()
        assertIs<ObjectId.StackItem>(item)
        assertEquals("${bob.name} may respond to Ember Herald's ability", waitingText(GameDevice.Wait.Respond(item), play, bob.name))

        // The events where they resolved: the spell as the card, then the trigger as the Herald's ability
        val resolutions = cast.line.filter { e -> e.engine?.changes.orEmpty().any { it is Change.StackItemRemoved } }
            .map { EngineEventText.of(it, bob.shown.engine, bob.id, names()) }
        assertEquals(listOf("Ember Herald resolved", "Ember Herald's ability resolved"), resolutions.take(2), "$resolutions")
    }
}
