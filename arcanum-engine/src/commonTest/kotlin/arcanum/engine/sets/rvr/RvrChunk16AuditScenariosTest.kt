package arcanum.engine.sets.rvr

import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test

/** Ravnica Remastered chunk 16: the two cards the audit doubted. */
class RvrChunk16AuditScenariosTest {
    private val debt = Cards.invent("Rvr16 Debt Collector", "{X}{W}{W}{B}{B}", "Sorcery",
        "Each opponent loses two times X life. You gain life equal to the life lost this way.")
    private val priest = Cards.invent("Rvr16 Grave Priest", "{1}{B}", "Creature — Human Cleric", """
        {T}, Sacrifice two other creatures: Any number of target players each lose 2 life and sacrifice a creature of their choice. You add {B}{B} and draw a card. (Activate only as an instant.)
    """, "1/2")
    private val bear = Cards.invent("Rvr16 Fodder Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val boar = Cards.invent("Rvr16 Fodder Boar", "{1}{G}", "Creature — Boar", "", "2/2")
    private val sip = Cards.invent("Rvr16 Small Sip", "{W}", "Instant", "You gain 1 life.")
    private val ox = Cards.invent("Rvr16 Victim Ox", "{1}{G}", "Creature — Ox", "", "2/2")
    private val elk = Cards.invent("Rvr16 Victim Elk", "{1}{G}", "Creature — Elk", "", "2/2")

    // CR 119.3, 119.6: each opponent loses 4 life; the life lost this way is the total over all opponents (CR 608.2c), so 8 gained
    @Test
    fun debtDrainsEachOpponentAndGainsTheTotal() = scenario(players = 3) {
        alice.hand(debt)
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp", "Plains", "Plains")
        alice.casts(debt, x = 2)
        resolveAll()
        expect {
            bob.life shouldBe 16
            carol.life shouldBe 16
            alice.life shouldBe 28
        }
    }

    // CR 602.5d (an activated ability can be activated whenever its controller has priority, the reminder adds no restriction),
    // 701.21a: each chosen player loses 2 and sacrifices a creature of their choice
    @Test
    fun priestActivatesOnAnOpponentsTurnAndHitsChosenPlayers() = scenario(players = 3) {
        alice.battlefield(priest, bear, boar)
        alice.library("Swamp", "Swamp")
        bob.battlefield(ox)
        carol.battlefield(elk)
        bob.hand(sip)
        bob.battlefield("Plains")
        turn(active = bob)
        bob.casts(sip)
        bob.passes()
        carol.passes()
        alice.activates(priest, targets = listOf("bob", "carol"), choosing = listOf("Rvr16 Fodder Bear", "Rvr16 Fodder Boar"))
        resolveAll()
        expect {
            bob.life shouldBe 19
            carol.life shouldBe 18
            bob.battlefield.names shouldBe listOf("Plains")
            carol.battlefield.size shouldBe 0
            alice.hand.size shouldBe 1
            alice.battlefield.names shouldBe listOf("Rvr16 Grave Priest")
        }
    }
}
