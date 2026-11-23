package arcanum.engine.sets.snc

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/** Streets of New Capenna chunk 12 in play: a cycling land that enters tapped, a pump with first strike and a scry, an uncounterable
 * spell, a spell that costs less against a coloured target, and an enters trigger that hits each opponent (three players). */
class SncChunk12CyclingScryManaCostsScenariosTest {
    private val garden = Cards.invent("Snc12 Jetsam Garden", null, "Land — Mountain Forest Plains", """
        ({T}: Add {R}, {G}, or {W}.)
        This land enters tapped.
        Cycling {3} ({3}, Discard this card: Draw a card.)
    """)
    private val escape = Cards.invent("Snc12 Daring Escape", "{R}", "Instant", "Target creature gets +1/+0 and gains first strike until end of turn. Scry 1.")
    private val rend = Cards.invent("Snc12 Void Rend", "{W}{U}{B}", "Instant", """
        This spell can't be countered.
        Destroy target nonland permanent.
    """)
    private val whack = Cards.invent("Snc12 Whack", "{3}{B}", "Sorcery", """
        This spell costs {3} less to cast if it targets a white creature.
        Target creature gets -4/-4 until end of turn.
    """)
    private val outlaw = Cards.invent("Snc12 Glamorous Outlaw", "{3}{U}{B}{R}", "Creature — Vampire Rogue", """
        When this creature enters, it deals 2 damage to each opponent and you scry 2.
    """, "4/5")
    private val veto = Cards.invent("Snc12 Muffling Veto", "{U}", "Instant", "Counter target spell.")
    private val ox = Cards.invent("Snc12 Plodding Ox", "{1}{G}", "Creature — Ox", "", "2/2")
    private val knight = Cards.invent("Snc12 Pale Knight", "{1}{W}", "Creature — Human Knight", "", "2/2")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 614.1c (enters tapped)
    @Test
    fun cyclingLandEntersTapped() = scenario {
        alice.hand(garden)
        alice.plays(garden)
        expect { card(garden).tapped shouldBe true }
    }

    // CR 702.29a: cycling discards the card and draws a card
    @Test
    fun cyclingLandCyclesForThree() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(garden)
        alice.library("Island")
        alice.activates(garden)
        resolveAll()
        expect {
            alice.graveyard has garden
            alice.hand has "Island"
        }
    }

    // CR 611.2a, 702.7a, 701.22a (scry 1: the card goes to the bottom)
    @Test
    fun escapePumpsGivesFirstStrikeAndScries() = scenario {
        alice.battlefield("Mountain", ox)
        alice.hand(escape)
        alice.library("Island", "Forest")
        alice.casts(escape, target = ox)
        allPass()
        answer(Answer.Objects(listOf(card("Island"))))
        expect {
            ox.power shouldBe 3
            ox hasKeyword Keyword.FirstStrike.kind
            state.player(alice.id).library.map { nameOf(state.obj(it)!!) } shouldBe listOf("Forest", "Island")
        }
    }

    // CR 101.5 (can't be countered): the counterspell does nothing
    @Test
    fun rendIsNotCountered() = scenario {
        alice.battlefield("Plains", "Island", "Swamp")
        alice.hand(rend)
        bob.battlefield("Island", ox)
        bob.hand(veto)
        alice.casts(rend, target = ox)
        alice.passes()
        bob.casts(veto, target = rend)
        resolveAll()
        expect { card(ox).zone shouldBe Zone.Graveyard }
    }

    // CR 601.2f: a white creature target makes it cost {B}
    @Test
    fun whackCostsLessAgainstAWhiteCreature() = scenario {
        alice.battlefield("Swamp")
        alice.hand(whack)
        bob.battlefield(knight)
        alice.casts(whack, target = knight)
        resolveAll()
        expect { card(knight).zone shouldBe Zone.Graveyard }
    }

    // CR 601.2f: against a green creature the full cost applies
    @Test
    fun whackCostsFullAgainstAGreenCreature() = scenario {
        alice.battlefield("Swamp")
        alice.hand(whack)
        bob.battlefield(ox)
        alice.casts(whack, target = ox)
        kotlin.test.assertEquals(emptyList(), decision<Decision.ManaPayment>().suggestion)
    }

    // CR 603.3, 120.3 (each opponent), 701.22a: both opponents take 2, then alice scries 2
    @Test
    fun outlawHitsEachOpponentAndScries() = scenario(players = 3) {
        alice.battlefield("Island", "Swamp", "Mountain", "Island", "Swamp", "Mountain")
        alice.hand(outlaw)
        alice.library("Plains", "Forest", "Island")
        alice.casts(outlaw)
        allPass()
        allPass()
        answer(Answer.Objects(listOf(card("Plains"))))
        resolveAll()
        expect {
            bob.life shouldBe 18
            carol.life shouldBe 18
            alice.life shouldBe 20
        }
    }
}
