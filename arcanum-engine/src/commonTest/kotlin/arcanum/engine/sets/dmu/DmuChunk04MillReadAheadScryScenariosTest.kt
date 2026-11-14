package arcanum.engine.sets.dmu

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.spm.loreChapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The DMU mill, read ahead and scry cards of chunk 04 that give the engine something new, played: the invented names carry the oracle
 * text of Founding the Third Path, Braids's Frightful Return, The Phasing of Zhalfir, The World Spell, Urza Assembles the Titans, The Elder
 * Dragon War, Yotia Declares War, Weatherlight Compleated and Eerie Soultender.
 */
class DmuChunk04MillReadAheadScryScenariosTest {
    private val readAhead = "Read ahead (Choose a chapter and start with that many lore counters. Add one after your draw step. Skipped chapters don't trigger. Sacrifice after III.)"
    private val one = Cards.invent("Read Ahead Test Spark", "{U}", "Instant", "Draw a card.")
    private val two = Cards.invent("Read Ahead Test Insight", "{1}{U}", "Instant", "Draw a card.")
    private val three = Cards.invent("Read Ahead Test Study", "{2}{U}", "Sorcery", "Draw a card.")
    private val ogre = Cards.invent("Read Ahead Test Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val bear = Cards.invent("Read Ahead Test Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val elk = Cards.invent("Read Ahead Test Elk", "{1}{G}", "Creature — Elk", "", "2/2")
    private val idol = Cards.invent("Read Ahead Test Idol", "{2}", "Artifact", "")
    private val otherSaga = Cards.invent("Read Ahead Test Chronicle", "{1}{W}", "Enchantment — Saga", "I — Draw a card.\nII — Draw a card.")
    private val zap = Cards.invent("Read Ahead Test Zap", "{R}", "Instant", "Read Ahead Test Zap deals 2 damage to target creature.")
    private val walker = Cards.invent("Read Ahead Test Walker", "{2}", "Legendary Planeswalker — Testa", "[+1]: You gain 1 life.", loyalty = "3")
    private val secondWalker = Cards.invent("Read Ahead Test Sage", "{2}", "Legendary Planeswalker — Testa", "[+1]: You gain 1 life.", loyalty = "3")

    private val founding = Cards.invent("Charting the Fourth Road", "{1}{U}", "Enchantment — Saga", """
        $readAhead
        I — You may cast an instant or sorcery spell with mana value 1 or 2 from your hand without paying its mana cost.
        II — Target player mills four cards.
        III — Exile target instant or sorcery card from your graveyard. Copy it. You may cast the copy.
    """)
    private val braids = Cards.invent("Mirela's Dread Homecoming", "{2}{B}", "Enchantment — Saga", """
        $readAhead
        I — You may sacrifice a creature. If you do, each opponent discards a card.
        II — Return target creature card from your graveyard to your hand.
        III — Target opponent may sacrifice a nonland, nontoken permanent of their choice. If they don't, they lose 2 life and you draw a card.
    """)
    private val phasing = Cards.invent("The Fading of Ostmere", "{2}{U}{U}", "Enchantment — Saga", """
        $readAhead
        I, II — Another target nonland permanent phases out. It can't phase in for as long as you control this Saga.
        III — Destroy all creatures. For each creature destroyed this way, its controller creates a 2/2 black Phyrexian creature token.
    """)
    private val worldSpell = Cards.invent("The Primal Litany", "{5}{G}{G}", "Enchantment — Saga", """
        $readAhead
        I, II — Look at the top seven cards of your library. You may reveal a non-Saga permanent card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        III — Put up to two non-Saga permanent cards from your hand onto the battlefield.
    """)
    private val urza = Cards.invent("Odric Gathers the Colossi", "{3}{W}{W}", "Enchantment — Saga", """
        $readAhead
        I — Scry 4, then you may reveal the top card of your library. If a planeswalker card is revealed this way, put it into your hand.
        II — You may put a planeswalker card with mana value 6 or less from your hand onto the battlefield.
        III — You may activate the loyalty abilities of planeswalkers you control twice this turn rather than only once.
    """)
    private val dragonWar = Cards.invent("The Wyrmfire Conflict", "{2}{R}{R}", "Enchantment — Saga", """
        $readAhead
        I — This Saga deals 2 damage to each creature and each opponent.
        II — Discard any number of cards, then draw that many cards.
        III — Create a 4/4 red Dragon creature token with flying.
    """)
    private val weatherlight = Cards.invent("Skyreaver Corrupted", "{2}", "Legendary Artifact — Vehicle", """
        Flying
        As long as Skyreaver Corrupted has four or more phyresis counters on it, it's a Phyrexian creature in addition to its other types.
        Whenever a creature you control dies, put a phyresis counter on Skyreaver Corrupted. Then draw a card if it has seven or more phyresis counters on it. If it doesn't, scry 1.
    """, "5/5")
    private val soultender = Cards.invent("Hollow Gravetender", "{2}{B}", "Creature — Spirit Cleric", """
        When this creature enters, mill three cards. (Put the top three cards of your library into your graveyard.)
        {4}{B}, Exile this card from your graveyard: Return another target creature card from your graveyard to your hand.
    """, "3/1")

    private val yotia = Cards.invent("Kessia Marches to Battle", "{1}{R}", "Enchantment — Saga", """
        $readAhead
        I — Create a 0/2 colorless Thopter artifact creature token with flying named Ornithopter.
        II — Tap any number of untapped artifacts you control. When you do, this Saga deals that much damage to target creature or planeswalker.
        III — Up to one target artifact you control becomes an artifact creature with base power and toughness 4/4 until end of turn.
    """)
    private val idols = (1..3).map { Cards.invent("Read Ahead Test Relic $it", "{1}", "Artifact", "") }

    private val phyresis = CounterKind.Named("phyresis")

    // CR 702.155b (read ahead: chapter I chosen), CR 608.2g, 601.2 (cast while the ability resolves, without paying, CR 118.9): only a spell of
    // mana value 1 or 2
    @Test
    fun foundingCastsOnlyAManaValueOneOrTwoSpellFree() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(founding, one, two, three)
        alice.library("Plains", "Plains")
        alice.casts(founding)
        allPass()
        alice.answers(Answer.Number(1))
        allPass()
        assertEquals(setOf(card(one), card(two)), decision<Decision.ChooseObjects>().options.toSet())
        alice.chooses(two)
        resolveAll()
        expect {
            alice.graveyard has two
            alice.hand has "Plains"
            alice.hand has three
        }
    }

    /** Alice's Saga at chapter III against Bob and Carol: Bob is targeted; Bob has [bobs] and a land. */
    private fun Scenario.braidsChapterThree(vararg bobs: String) {
        bob.battlefield("Swamp", *bobs)
        carol.battlefield(elk)
        loreChapter(braids, 3, three)
        resolveAll()
        answer(Answer.Targets(listOf(target("bob"))))
        resolveAll()
    }

    // CR 118.12, 608.2d: the targeted opponent may sacrifice; if they don't, they lose 2 life and you draw (3 players: Carol isn't asked)
    @Test
    fun braidsOpponentDeclinesToSacrifice() = scenario(players = 3) {
        braidsChapterThree(ogre)
        assertEquals(bob.id, decision<Decision.May>().player)
        answer(Answer.YesNo(false))
        resolveAll()
        expect {
            bob.life shouldBe 18
            carol.life shouldBe 20
            bob.battlefield has ogre
            alice.hand has three
        }
    }

    // CR 118.12, 701.21a: when they sacrifice, nothing else happens
    @Test
    fun braidsOpponentSacrifices() = scenario(players = 3) {
        braidsChapterThree(ogre)
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.life shouldBe 20
            bob.graveyard has ogre
            bob.battlefield has "Swamp"
            carol.battlefield has elk
        }
    }

    // CR 702.26a, b (a phased-out permanent phases in during its controller's untap step), CR 611.2b (not while Alice controls the Saga); CR 702.26d
    // (phased out, it isn't destroyed); CR 608.2h (each destroyed creature's controller creates a token); CR 714.4 (sacrificed after III)
    @Test
    fun phasingHoldsItOutWhileTheSagaStaysThenTokensForEachController() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Island", bear)
        bob.battlefield(ogre)
        carol.battlefield(elk)
        alice.library("Plains", "Plains", "Plains")
        bob.library("Plains", "Plains", "Plains")
        carol.library("Plains", "Plains", "Plains")
        alice.hand(phasing)
        alice.casts(phasing)
        allPass()
        alice.answers(Answer.Number(2))
        allPass()
        answer(Answer.Targets(listOf(target(ogre))))
        resolveAll()
        expect { assertTrue(card(ogre).view.obj?.phasedOut == true, "phased out") }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { assertTrue(card(ogre).view.obj?.phasedOut == true, "held out during Bob's untap step") }
        advanceTo(TurnStep.PrecombatMain, turn = 4)
        resolveAll()
        expect {
            alice.graveyard has phasing
            alice.graveyard has bear
            carol.graveyard has elk
            alice.battlefield has "Phyrexian Token"
            carol.battlefield has "Phyrexian Token"
            bob.battlefield lacks "Phyrexian Token"
            assertTrue(card(ogre).view.obj?.phasedOut == true, "still out until Bob's next untap step")
        }
        advanceTo(TurnStep.Upkeep, turn = 5)
        expect { assertTrue(card(ogre).view.obj?.phasedOut == false, "phased in") }
    }

    // CR 611.2b: once Alice no longer controls the Saga (here it left the battlefield), the permanent phases in during its controller's next untap step
    @Test
    fun phasingEndsWhenTheSagaLeaves() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        bob.battlefield(ogre)
        alice.library("Plains", "Plains")
        bob.library("Plains", "Plains")
        alice.hand(phasing)
        alice.casts(phasing)
        allPass()
        alice.answers(Answer.Number(1))
        resolveAll()
        move(phasing, arcanum.api.Zone.Graveyard)
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { assertTrue(card(ogre).view.obj?.phasedOut == false, "phased in") }
    }

    // CR 305.1-like: putting permanent cards onto the battlefield, up to two, not Sagas (CR 714.1) nor instants
    @Test
    fun worldSpellPutsUpToTwoNonSagaPermanents() = scenario {
        alice.hand(ogre, idol, otherSaga, three)
        loreChapter(worldSpell, 3)
        resolveAll()
        val d = decision<Decision.ChooseObjects>()
        assertEquals(setOf(card(ogre), card(idol)), d.options.toSet())
        assertEquals(2, d.max)
        alice.chooses(ogre, idol)
        resolveAll()
        expect {
            alice.battlefield has ogre
            alice.battlefield has idol
            alice.hand has otherSaga
        }
    }

    // CR 606.3 (a loyalty ability once a turn, unless an effect allows more), CR 611.2c (not a characteristic: a planeswalker that comes under your
    // control later in the turn may also activate twice)
    @Test
    fun urzaLetsEachPlaneswalkerActivateTwice() = scenario {
        alice.battlefield("Plains", "Plains", walker, counters = listOf(CounterKind.Loyalty to 3))
        alice.hand(secondWalker)
        loreChapter(urza, 3)
        resolveAll()
        alice.casts(secondWalker)
        resolveAll()
        repeat(2) {
            alice.activates(walker)
            resolveAll()
            alice.activates(secondWalker)
            resolveAll()
        }
        expect {
            walker.counters(CounterKind.Loyalty) shouldBe 5
            secondWalker.counters(CounterKind.Loyalty) shouldBe 5
            alice.life shouldBe 24
        }
    }

    // Audit: CR 701.9a (each chosen card is discarded), CR 608.2c ("that many": the number discarded)
    @Test
    fun elderDragonWarDiscardsEveryChosenCardAndDrawsThatMany() = scenario {
        alice.hand(ogre, idol, three)
        loreChapter(dragonWar, 2, "Mountain", "Mountain", "Mountain")
        resolveAll()
        alice.chooses(ogre, idol)
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.graveyard has idol
            alice.hand has three
            alice.hand.size shouldBe 3
        }
    }

    // Audit: CR 603.12 (a reflexive trigger), CR 608.2c: "that much" is the number of artifacts tapped (one here, not the chapter number)
    @Test
    fun yotiaDealsDamageEqualToTheArtifactsTapped() = scenario {
        alice.battlefield(*idols.toTypedArray())
        bob.battlefield(ogre)
        loreChapter(yotia, 2)
        resolveAll()
        alice.chooses(idols[0])
        resolveAll()
        expect {
            card(ogre).view.obj?.damage shouldBe 1
            idols[0].tapped shouldBe true
            idols[2].tapped shouldBe false
        }
    }

    // CR 603.12: with nothing tapped, "when you do" doesn't trigger
    @Test
    fun yotiaTappingNothingDoesNotTrigger() = scenario {
        alice.battlefield(*idols.toTypedArray())
        bob.battlefield(ogre)
        loreChapter(yotia, 2)
        resolveAll()
        alice.chooses()
        resolveAll()
        expect {
            card(ogre).view.obj?.damage shouldBe 0
            stack.isEmpty()
        }
    }

    /** Alice's Skyreaver with [counters] phyresis counters; Bear dies to a damage spell. */
    private fun Scenario.bearDiesBeside(counters: Int) {
        alice.battlefield("Mountain", bear)
        alice.battlefield(weatherlight, counters = listOf(phyresis to counters))
        alice.hand(zap)
        alice.library("Plains")
        alice.casts(zap, target = bear)
        resolveAll()
    }

    // CR 613.1d, 205.1b (a Phyrexian creature in addition to its other types, from four counters); CR 608.2c: six then seven counters: no scry
    // but a draw
    @Test
    fun weatherlightBecomesACreatureThenScries() = scenario {
        bearDiesBeside(3)
        expect {
            weatherlight.counters(phyresis) shouldBe 4
            assertTrue(CardType.Creature in weatherlight.types && CardType.Artifact in weatherlight.types, "an artifact creature")
            assertTrue(Subtype("Phyrexian") in weatherlight.subtypes && Subtype("Vehicle") in weatherlight.subtypes, "a Phyrexian Vehicle")
            alice.library has "Plains"
        }
    }

    @Test
    fun weatherlightDrawsAtSevenCounters() = scenario {
        bearDiesBeside(6)
        expect {
            weatherlight.counters(phyresis) shouldBe 7
            alice.hand has "Plains"
        }
    }

    // CR 109.2, 115.1: "another target creature card" can't be the card itself
    @Test
    fun soultenderCantReturnItself() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(soultender)
        assertFailsWith<IllegalArgumentException> { alice.activates(soultender) }
    }

    @Test
    fun soultenderReturnsAnotherCreatureCard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(soultender, ogre)
        alice.activates(soultender, target = ogre)
        resolveAll()
        expect {
            alice.hand has ogre
            alice.exile has soultender
        }
    }
}
