package arcanum.engine.sets.rvr

import arcanum.api.CounterKind
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test

/** Ravnica Remastered chunk 09 in play: convoke on a spell with X, and an Adapt ability whose cost drops with the graveyard.
 * Afterlife, battalion, dredge, enchant and the other cards of the chunk are mechanically the same as tested ones. */
class RvrChunk09AdaptConvokeScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val chord = Cards.invent("Chant of Summoning", "{X}{G}{G}{G}", "Instant", """
        Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
        Search your library for a creature card with mana value X or less, put it onto the battlefield, then shuffle.
    """)
    private val salamander = Cards.invent("Pterasalamander", "{U}", "Creature — Salamander Drake", """
        Flying
        {7}{U}: Adapt 4. This ability costs {1} less to activate for each instant and sorcery card in your graveyard. (If this creature has no +1/+1 counters on it, put four +1/+1 counters on it.)
    """, "1/1")
    private val sprout = Cards.invent("Rvr9 Sprout", "{G}", "Creature — Elf", "", "1/1")
    private val scout = Cards.invent("Rvr9 Scout", "{W}", "Creature — Human", "", "1/1")
    private val fetched = Cards.invent("Rvr9 Wolf", "{1}{G}", "Creature — Wolf", "", "2/2")
    private val tooBig = Cards.invent("Rvr9 Bear", "{2}{G}", "Creature — Bear", "", "3/3")
    private val bolt = Cards.invent("Rvr9 Spark", "{R}", "Instant", "Rvr9 Spark deals 1 damage to any target.")
    private val ritual = Cards.invent("Rvr9 Study", "{U}", "Sorcery", "Draw a card.")

    /** X = 2 paid with three Forests, one green creature (its colour pays {G}) and a white one (pays generic). The searched
     * creature has mana value 2; the mana value 3 one is not offered. */
    // CR 702.51a, 107.3
    @Test
    fun chordConvokesWithTwoCreaturesAndFetchesWithinX() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", sprout, scout)
        alice.hand(chord)
        alice.library(fetched, tooBig)
        alice.casts(chord, x = 2, paying = listOf("Forest", "Forest", "Forest", sprout, scout))
        playOut(fetched)
        expect {
            fetched.zone shouldBe Zone.Battlefield
            sprout.tapped shouldBe true
            scout.tapped shouldBe true
        }
    }

    /** Two instants and sorceries in the graveyard make the {7}{U} Adapt cost {5}{U}; it puts four counters, and a second
     * activation with counters on it adds none (CR 701.46a). */
    // CR 701.46a, 602.2b
    @Test
    fun adaptCostsLessPerInstantAndSorceryAndOnlyWithoutCounters() = scenario {
        alice.battlefield(salamander, *Array(12) { "Island" })
        alice.graveyard(bolt, ritual)
        alice.activates(salamander)
        resolveAll()
        alice.activates(salamander)
        resolveAll()
        expect { salamander.counters(p1p1) shouldBe 4 }
    }
}
