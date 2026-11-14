package arcanum.engine.sets.mh3

import arcanum.api.CounterKind
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dgm.payIfAsked
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The MH3 cycling cards of chunk 03, played: the invented names carry the oracle text of Drownyard Lurker, Jolted Awake, Decree of Justice
 * and Bountiful Landscape (costs cut where they don't change play).
 */
class Mh3Chunk03CyclingScenariosTest {
    private val energy = CounterKind.Energy
    private val spawn = "Eldrazi Spawn Token"
    private val bear = Cards.invent("Rubblefield Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Rubblefield Ox", "{2}{G}", "Creature — Ox", "", "2/4")

    private val lurker = Cards.invent("Tidewreck Lurker", "{3}", "Creature — Eldrazi Trilobite", """
        Vigilance
        When you cast or cycle Tidewreck Lurker, create a 0/1 colorless Eldrazi Spawn creature token with "Sacrifice this token: Add {C}."
        Cycling {2}{U} ({2}{U}, Discard this card: Draw a card.)
    """, "7/7")
    private val revival = Cards.invent("Sparked Revival", "{W}", "Sorcery", """
        Choose up to one target artifact or creature card in your graveyard. You get {E}{E} (two energy counters). Then you may pay an amount of {E} equal to that card's mana value. If you do, return it from your graveyard to the battlefield.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """)
    private val edict = Cards.invent("Edict of Righteousness", "{X}{X}{2}{W}{W}", "Sorcery", """
        Create X 4/4 white Angel creature tokens with flying.
        Cycling {2}{W} ({2}{W}, Discard this card: Draw a card.)
        When you cycle this card, you may pay {X}. If you do, create X 1/1 white Soldier creature tokens.
    """)
    private val vista = Cards.invent("Plentiful Vista", null, "Land", """
        {T}: Add {C}.
        {T}, Sacrifice this land: Search your library for a basic Forest, Island, or Mountain card, put it onto the battlefield tapped, then shuffle.
        Cycling {G}{U}{R} ({G}{U}{R}, Discard this card: Draw a card.)
    """)

    // CR 603.6: "when you cast" triggers as the spell is cast, from the stack, and resolves before it
    @Test
    fun lurkerMakesASpawnWhenCast() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(lurker)
        alice.casts(lurker)
        resolveAll()
        expect {
            alice.battlefield has lurker
            alice.battlefield.names.count { it == spawn } shouldBe 1
        }
    }

    // CR 702.29c: the same ability triggers when the card is cycled, from the graveyard it went to
    @Test
    fun lurkerMakesASpawnWhenCycled() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(lurker)
        alice.library("Forest")
        alice.activates(lurker)
        resolveAll()
        expect {
            alice.graveyard has lurker
            alice.hand.names shouldBe listOf("Forest")
            alice.battlefield.names.count { it == spawn } shouldBe 1
        }
    }

    // CR 122.1, 107.14: the two energy it gives count toward the three the Ox's mana value asks; paid, the Ox returns
    @Test
    fun revivalReturnsTheCardWhenItsManaValueInEnergyIsPaid() = scenario {
        alice.battlefield("Plains")
        alice.hand(revival)
        alice.graveyard(ox, bear)
        alice.counters(energy, 1)
        alice.casts(revival, target = ox)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.battlefield has ox
            alice.graveyard has bear
            alice.counters(energy) shouldBe 0
        }
    }

    // CR 107.14, 118.3: with two energy the three can't be paid; the Ox stays and the energy is kept
    @Test
    fun revivalCantReturnACardItCantPayFor() = scenario {
        alice.battlefield("Plains")
        alice.hand(revival)
        alice.graveyard(ox, bear)
        alice.casts(revival, target = ox)
        resolveAll()
        assertNull(pending)
        expect {
            alice.graveyard has ox
            alice.graveyard has revival
            alice.counters(energy) shouldBe 2
        }
    }

    // CR 107.3f, 118.12: in the cycle trigger X is chosen as {X} is paid on resolution, at most the two Plains left after cycling (the cycling
    // cost has no X); two paid, two Soldiers
    @Test
    fun edictCycledMakesXSoldiersForTheXPaid() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(edict)
        alice.library("Island")
        alice.activates(edict)
        resolveAll()
        decision<Decision.May>()
        answer(Answer.YesNo(true))
        val x = decision<Decision.ChooseNumber>()
        assertEquals(0 to 2, x.min to x.max)
        answer(Answer.Number(2))
        decision<Decision.ManaPayment>()
        payIfAsked()
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Soldier Token" } shouldBe 2
            alice.hand.names shouldBe listOf("Island")
        }
    }

    // CR 701.23a, 205.3i: only a basic Forest, Island or Mountain can be found; it enters tapped
    @Test
    fun vistaFindsOnlyItsThreeBasicTypes() = scenario {
        alice.battlefield(vista)
        alice.library("Plains", "Forest", "Swamp")
        alice.activates(vista, ability = 1)
        resolveAll()
        assertEquals(listOf(card("Forest")), decision<Decision.ChooseObjects>().options)
        alice.chooses("Forest")
        resolveAll()
        expect {
            alice.graveyard has vista
            alice.battlefield has "Forest"
            "Forest".tapped shouldBe true
        }
        assertNull(pending)
    }
}
