package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.asyncttwiz.automatum.DamageDivision
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.PlayZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The division of combat damage panel (CMB-033, STK-033; owner playtest 2026-11-06): the dealing creature at the top left
 * as its card with its power and the keywords that change damage; each recipient's damage as a chip, red with ☠ when it
 * is lethal (deathtouch: 1), grey when not; with trample a last row for the attacked player that never moves.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CombatDivisionPanelTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture
    private val play = tableBoard(f.combat())
    private val names = mapOf(f.a to "Alice", f.b to "Bob")

    private fun field(owner: String) = f.combat().board.let { board ->
        val container = board.containers.single { it.accountId == owner && it.zone == PlayZone.BATTLEFIELD }
        container.cards.mapNotNull { id -> board.cards.single { it.id == id }.takeIf { it.corner.isNotEmpty() } }
    }
    private fun ref(id: String): MtgRef = MtgRef.Card(id, 0)
    private fun name(card: org.asyncttwiz.protocol.PlayCard) = CastController.cardName(play, card.id)
    /** [owner]'s creature named unlike any of [others] (both battlefields hold the same cards), so rows read apart. */
    private fun dealer(owner: String, others: List<org.asyncttwiz.protocol.PlayCard>) =
        field(owner).first { card -> others.none { name(it) == name(card) } }

    /** [viewer]'s panel for [division] as the band shows it (white, the table's width), started from its suggestion. */
    private fun render(division: DamageDivision, viewer: String, power: Int?, keywords: List<String>, free: Boolean = false): DivisionDraft {
        val draft = DivisionDraft(division, division.suggested, free)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalChoiceFrame provides ChoiceFrame(viewer)) {
                    Box(Modifier.fillMaxSize().background(CombatBandColor), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(400.dp)) {
                            DivisionDock(draft, play, names, null, power, keywords, hidden = false, toggleHidden = {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        return draft
    }

    // CMB-033, STK-033, CR 702.19b (owner playtest 2026-11-06): Alice's 4-power trampler blocked by two of Bob's
    // creatures: in order each gets lethal damage (red ☠ chips); what tramples over is no row but a chip on the panel's
    // bottom line by the eye, "Tramples 1 → Bob", grey (not lethal to him)
    @Test fun whatTramplesOverShowsOnTheBottomLine() {
        val blockers = field(f.b).take(2)
        val attacker = dealer(f.a, blockers)
        val among = blockers.map { ref(it.id) } + MtgRef.Player(f.b)
        val lethal: Map<MtgRef, Int> = blockers.map { ref(it.id) }.zip(listOf(1, 2)).toMap()
        val division = DamageDivision(f.a, attacker.id, 4, among, lethal,
            mapOf(among[0] to 1, among[1] to 2, among[2] to 1), MtgRef.Player(f.b))
        render(division, f.a, power = 4, keywords = listOf("Trample"))
        compose.onNode(hasText("Divide 4 damage") and isHeading()).assertExists()
        compose.onNodeWithContentDescription("Power 4").assertExists()
        compose.onNode(hasText("trample")).assertExists()
        compose.onAllNodes(hasStateDescription("lethal")).assertCountEquals(2)
        compose.onNodeWithContentDescription("Tramples 1 to Bob").assertExists()
        compose.onAllNodesWithContentDescription("Bob: ", substring = true).assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Drag to reorder").assertCountEquals(2)
        captureShot("$SCREENSHOTS/combat-division/trample.png",
            "Alice divides her 4-power trampler's damage between Bob's two blockers: her creature at the top left with its power 4 and a trample chip, the title Divide 4 damage, in order both blockers get lethal damage as red ☠ chips, and on the bottom line by the eye a grey chip says it tramples 1 over to Bob.")
    }

    // CMB-033 (owner playtest 2026-11-06): trample lethal to the defender: the bottom line's chip is red with ☠
    @Test fun tramplingOverLethalDamageShowsARedChip() {
        val blockers = field(f.b).take(1)
        val attacker = dealer(f.a, blockers)
        val among = blockers.map { ref(it.id) } + MtgRef.Player(f.b)
        val life = play.board.players.single { it.accountId == f.b }.life.toInt()
        val division = DamageDivision(f.a, attacker.id, life + 1, among, mapOf(among[0] to 1),
            mapOf(among[0] to 1, among[1] to life), MtgRef.Player(f.b))
        render(division, f.a, power = life + 1, keywords = listOf("Trample", "Lifelink"))
        compose.onNodeWithContentDescription("Tramples $life to Bob").assert(hasStateDescription("lethal"))
        captureShot("$SCREENSHOTS/combat-division/trample-lethal.png",
            "Alice's trampler with lifelink deals 1 to its one blocker (a red ☠ chip) and tramples over the rest, as much as Bob's life: the bottom line's chip is red with ☠, \"Tramples $life → Bob\".")
    }

    // CMB-033, STK-033, CR 702.2c (owner playtest 2026-11-06): with deathtouch 1 damage is lethal: a 1-power attacker
    // with deathtouch and first strike blocked by two creatures gives the first 1 (red ☠) and the second nothing
    @Test fun withDeathtouchOneIsLethal() {
        val blockers = field(f.b).take(2)
        val attacker = dealer(f.a, blockers)
        val among = blockers.map { ref(it.id) }
        val division = DamageDivision(f.a, attacker.id, 1, among, among.associateWith { 1 },
            mapOf(among[0] to 1, among[1] to 0), null, firstStrike = true)
        render(division, f.a, power = 1, keywords = listOf("Deathtouch", "First strike"))
        compose.onAllNodes(hasStateDescription("lethal")).assertCountEquals(1)
        compose.onNode(hasText("deathtouch")).assertExists()
        compose.onNode(hasText("first strike")).assertExists()
        captureShot("$SCREENSHOTS/combat-division/deathtouch.png",
            "Alice divides the first-strike damage of her 1-power attacker with deathtouch between two blockers: deathtouch and first strike chips under the title, the first blocker gets 1 as a red ☠ chip (1 is lethal with deathtouch), the second 0 as a grey chip.")
    }

    // CMB-033, STK-033, CR 510.1d (owner playtest 2026-11-06): Bob's creature blocking two of Alice's attackers divides
    // its damage among them; in Free the chips sit between − and +, and moving 1 changes which is lethal
    @Test fun aBlockerDividesAmongTheAttackersItBlocksInFree() {
        val attackers = field(f.a).take(2)
        val blocker = dealer(f.b, attackers)
        val among = attackers.map { ref(it.id) }
        val division = DamageDivision(f.b, blocker.id, 3, among, mapOf(among[0] to 3, among[1] to 2),
            mapOf(among[0] to 3, among[1] to 0), null)
        val draft = render(division, f.b, power = 3, keywords = emptyList(), free = true)
        compose.onAllNodes(hasStateDescription("lethal")).assertCountEquals(1)
        compose.onAllNodesWithContentDescription("One more")[1].performClick()
        compose.onAllNodesWithContentDescription("One less")[0].performClick()
        compose.waitForIdle()
        assertEquals(mapOf<MtgRef, Int>(among[0] to 2, among[1] to 1), draft.shares)
        compose.onAllNodes(hasStateDescription("lethal")).assertCountEquals(0)
        draft.set(among[0], 1); draft.set(among[1], 2)
        compose.waitForIdle()
        compose.onAllNodes(hasStateDescription("lethal")).assertCountEquals(1)
        captureShot("$SCREENSHOTS/combat-division/blocker-free.png",
            "Bob's creature blocks two of Alice's attackers and divides its 3 damage in Free: its card at the top left with power 3, each attacker's chip between − and +, 1 to the first (grey, not lethal) and 2 to the second (red ☠, lethal).")
        assertTrue(play.board.cards.any { it.id == blocker.id })
    }
}
