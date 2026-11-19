package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import arcanum.engine.state.ObjectId
import arcanum.engine.state.StackItemKind
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.android.PLAY_SHOW_TAG
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.Fault
import org.asyncttwiz.protocol.Faults
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Problem report MDLPM7 (owner playtest 2026-11-19, Kirol, Attentive First-Year copying Bre of Clan Stoutarm's trigger): the copy came
 * in silently, looked exactly like its original, and copies on the Stack had "no art, no text"; the copied trigger's choice showed its
 * source face down. A copy is the card its values came from (CR 707.2, 707.10): shown arriving to both players, read as a copy with its
 * marker, its choice's source its token with the source's face, and a spell's copy drawn with the copied card's picture.
 */
class CopiesShownTest : E2e() {
    private val faults = Collections.synchronizedList(ArrayList<Fault>())
    private var sink: ((Fault) -> Unit)? = null

    @Before fun watchFaults() { sink = Faults.sink; Faults.sink = { faults += it; sink?.invoke(it) } }
    @After fun stopWatching() { Faults.sink = sink }

    private fun showPlays() {
        for (player in listOf(alice, bob)) HistoryPlaybackPreferences(AppPlatform.current, player.id).showPlaysMillis = 3_000
    }

    /** Whether [player]'s table shows a copy arriving now (HIS-231). */
    private fun showsCopy(player: E2ePlayer): Boolean = player.shown.show?.kind == GameDevice.PlayKind.Copied

    /** Alice taps Resolve (her Control mode keeps priority, STK-036) until [done]; Bob plays along. */
    private fun aliceResolvesUntil(what: String, also: () -> Unit = {}, done: () -> Boolean) = waitUntil(what, along = listOf(bob)) {
        also()
        if (!done() && alice.state.pending == null && alice.state.priority.holder == alice.me && alice.state.stack.isNotEmpty() &&
            alice.status == GameDevice.Status.YourMove && alice.stepArrow() == "Resolve") alice.once { alice.tap("Resolve") }
        done()
    }

    private fun topKind(): StackItemKind? = alice.state.let { s -> s.stack.lastOrNull()?.let { s.stackItems[it]?.kind } }

    // STK-212, HIS-231, STK-178, STK-030, STK-046: Alice's Ember Forager enters; she copies its trigger with her Ember Kindler. The copy
    // shows arriving on both tables, reads "Copy of Ember Forager's triggered ability" with the copy marker, points at the Forager; its
    // "you may cast that card" choice has the Forager's ability token (its face, the copy marker, never a card back) at its right and
    // the exiled card in "About:"; nothing is reported missing
    @Test fun aCopiedTriggerShowsArrivingReadsAsACopyAndItsChoiceShowsItsSource() {
        startGame(TestDeck("Foragers", listOf(TestCards.mountain to 13, TestCards.kindler to 9, TestCards.forager to 9, TestCards.glimmer to 9)),
            TestDeck.glimmers(), aliceSettings = PlaySettings(mode = ResponseMode.FullControl), deal = { a, _ ->
                "Ember Kindler" in hand(a) && "Ember Forager" in hand(a) &&
                    library(a).filter { it != "Mountain" }.take(2).all { it == "Idle Glimmer" }
            })
        showPlays()
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        // Her Control mode stops at each step (STK-036): the main button steps on to her Main 1
        waitUntil("Alice's Main 1", along = listOf(bob)) {
            if (!alice.inOwnMain() && alice.status == GameDevice.Status.YourMove && alice.engine { device.session.pending.isEmpty() })
                alice.stepArrow()?.takeIf { it in listOf("Pass", "Next", "Resolve") }?.let { arrow -> alice.once { alice.tap(arrow) } }
            alice.inOwnMain()
        }
        alice.playFromHand("Ember Kindler", this)
        aliceResolvesUntil("the Kindler on Alice's battlefield") { alice.field("Ember Kindler").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Forager", this)
        aliceResolvesUntil("the Forager's trigger on the Stack, Alice holding priority") {
            topKind() == StackItemKind.Triggered && alice.state.priority.holder == alice.me && alice.state.pending == null && alice.current()
        }
        // Its play shown (HIS-112) lands on the Stack first
        waitUntil("the trigger's play landed on Alice's Stack") { alice.shown.show == null && alice.shows(hasContentDescription("Stack, 1 entries")) }
        val trigger = alice.state.stack.last()
        val forager = atw(alice.field("Ember Forager").single())
        // The Kindler's one ability, its one target the trigger (picked on the Stack when asked)
        alice.battlefieldCard("Ember Kindler").performClick()
        waitUntil("the Kindler's ability on the Stack") {
            // STK-047: the trigger is picked as its Stack entry on the table
            if (alice.shows(hasContentDescription("Choose targets"))) alice.once {
                if (alice.showsText("Top · Ember Forager's ability")) alice.text("Top · Ember Forager's ability").performClick()
                else alice.nodes(hasContentDescription("Triggered ability, Ember Forager", substring = true)).onFirst().performClick()
            }
            topKind() == StackItemKind.Activated
        }
        var aliceSaw = false
        var bobSaw = false
        aliceResolvesUntil("the copy on the Stack, shown arriving on both tables", also = {
            if (showsCopy(alice)) aliceSaw = true
            if (showsCopy(bob)) bobSaw = true
        }) { aliceSaw && bobSaw }
        // HIS-231: Alice's table shows it as the Forager's ability token, its line naming it a copy
        waitUntil("Alice's play shown naming the copy") {
            alice.shows(hasTestTag(PLAY_SHOW_TAG) and hasContentDescription("Copy of Ember Forager's triggered ability", substring = true)) ||
                !showsCopy(alice)
        }
        val copy = alice.state.stack.last()
        assertEquals(trigger, alice.state.stackItems.getValue(copy).copyOf, "the copy copies the trigger")
        for (player in listOf(alice, bob)) {
            val entry = player.shown.board.board.stackEffects.single { it.item?.id == org.asyncttwiz.protocol.MtgRef.StackItem((copy as ObjectId.StackItem).id) }
            assertTrue(entry.isCopy, "${player.name}: the copy is marked")
            assertEquals(forager, entry.sourceId, "${player.name}: CR 707.10, it points at the Forager")
        }
        // STK-212: on the Stack its token reads as a copy
        waitUntil("the copy's token on Alice's Stack") { !showsCopy(alice) && alice.shows(hasContentDescription("Copy of a triggered ability", substring = true)) }

        // The copy resolves: it exiles the Idle Glimmer and offers to cast it
        aliceResolvesUntil("the copy's choice to cast the exiled card") { alice.state.pending?.decision is Decision.May && alice.showsText("About:") }
        assertTrue(alice.shows(hasContentDescription("Idle Glimmer")), "STK-030: the exiled card about the choice: ${alice.screen()}")
        assertTrue(alice.shows(hasContentDescription("Copy")), "STK-178: the source's token with the copy marker: ${alice.screen()}")
        assertTrue(alice.shows(hasContentDescription("Ember Forager")), "STK-178: the source named by its face: ${alice.screen()}")
        assertTrue(!alice.shows(hasContentDescription("Hidden card")), "never a card back: ${alice.screen()}")
        alice.text("No").performClick()
        aliceResolvesUntil("the original trigger's choice") {
            alice.state.pending?.decision is Decision.May && alice.state.stack.none { it == copy } && alice.showsText("About:")
        }
        assertTrue(!alice.shows(hasContentDescription("Copy")), "the original isn't marked a copy: ${alice.screen()}")
        alice.text("No").performClick()
        aliceResolvesUntil("the Stack empty") { alice.state.stack.isEmpty() && alice.inOwnMain() }
        assertEquals(emptyList(), faults.filter { it.area == "ShownObjects" || "placeholder" in it.what || it.details["shown"] != null }, "nothing missing")
    }

    // STK-121, HIS-231, SYS-151: Alice's Ember Echoer copies her Ember Bauble spell: on both tables the copy is shown arriving as the
    // Bauble's card with its picture (never a blank "Ability" frame), and the token it becomes has the Bauble's picture too
    @Test fun aCopiedPermanentSpellShowsItsPicture() {
        startGame(TestDeck("Echoes", listOf(TestCards.mountain to 14, TestCards.echoer to 13, TestCards.bauble to 13)), TestDeck.glimmers(),
            deal = { a, _ -> "Ember Echoer" in hand(a) && "Ember Bauble" in hand(a) })
        showPlays()
        bothOpen()
        bothKeep(bobAway = false)
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
        alice.playFromHand("Ember Echoer", this)
        waitUntil("the Echoer on Alice's battlefield", along = listOf(bob)) { alice.field("Ember Echoer").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Bauble", this)
        val art = assertNotNull(TestCards.bauble.definition.faces.single().scryfallId)
        val seen = mutableSetOf<String>()
        waitUntil("the Bauble's copy shown arriving on both tables", along = listOf(bob)) {
            for (player in listOf(alice, bob)) if (showsCopy(player) && player.name !in seen) {
                val play = player.shown.board
                val entry = play.board.stackEffects.single { it.id == "entry:${player.shown.show?.entry}" }
                val frozen = assertNotNull(entry.frozen, "${player.name}: the copy is a card of its own")
                val face = play.definitions.single { it.digest == frozen.definition }.definition.faces.first()
                assertEquals("Ember Bauble", face.name, player.name)
                assertEquals("Artifact", face.typeLine, player.name)
                assertEquals("{1}, {T}: You gain 1 life.", face.rulesText, player.name)
                assertEquals(art, face.scryfallId, "${player.name}: the copy is drawn with the Bauble's picture")
                assertTrue(entry.isCopy, player.name)
                seen += player.name
            }
            seen.size == 2
        }
        waitUntil("the Bauble and its token copy on both tables", along = listOf(bob)) {
            alice.field("Ember Bauble").size == 2 && bob.field("Ember Bauble", alice.me).size == 2 && alice.inOwnMain()
        }
        for (player in listOf(alice, bob)) {
            val play = player.shown.board
            val token = play.board.cards.single { c -> c.token && play.definitions.firstOrNull { it.digest == c.definition }?.definition?.name == "Ember Bauble" }
            assertEquals(art, play.definitions.single { it.digest == token.definition }.definition.faces.single().scryfallId,
                "${player.name}: the token copy has the Bauble's picture")
        }
        assertEquals(emptyList(), faults.filter { it.area == "ShownObjects" }, "nothing missing")
    }
}
