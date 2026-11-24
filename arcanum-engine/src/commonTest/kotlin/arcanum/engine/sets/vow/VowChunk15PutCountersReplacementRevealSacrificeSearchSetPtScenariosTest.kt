package arcanum.engine.sets.vow

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test

/** VOW chunk 15 (and Syncopate): a land that enters tapped unless two other lands, counters that grant abilities, extra life, a creature
 * that enters tapped, revealing four cards, searching by name, power and toughness from lands with a repeating land drop, and a counterspell
 * that exiles only the spell it counters. */
class VowChunk15PutCountersReplacementRevealSacrificeSearchSetPtScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val glade = Cards.invent("Mirewood Glade", null, "Land", """
        This land enters tapped unless you control two or more other lands.
        {T}: Add {B} or {G}.
    """.trimIndent())
    private val voice = Cards.invent("Voice of the Chorus", "{W}{W}", "Creature — Spirit Cleric", """
        Whenever you gain life, put a +1/+1 counter on this creature.
        As long as this creature has four or more +1/+1 counters on it, it has flying and vigilance.
        As long as this creature has ten or more +1/+1 counters on it, it has indestructible.
    """.trimIndent(), "2/2")
    private val draught = Cards.invent("Soothing Draught", "{W}", "Instant", "You gain 3 life.")
    private val heron = Cards.invent("Heron of Dawnlight", "{3}{W}", "Creature — Bird", """
        Flying
        If you would gain life, you gain that much life plus 1 instead.
        {1}{W}: This creature gains lifelink until end of turn.
    """.trimIndent(), "2/3")
    private val phalanx = Cards.invent("Gravebound Phalanx", "{4}{B}", "Creature — Zombie Soldier", "This creature enters tapped.", "1/13")
    private val harvest = Cards.invent("Rootmould Harvest", "{1}{G}", "Sorcery",
        "Reveal the top four cards of your library. Put all land cards revealed this way into your hand and the rest into your graveyard.")
    private val throng = Cards.invent("Sorrowing Throng", "{1}{U}", "Creature — Zombie Horror",
        "When this creature dies, you may search your library for a card named Sorrowing Throng, reveal it, put it into your hand, then shuffle.", "2/1")
    private val doom = Cards.invent("Final Doom of Fifteen", "{B}", "Instant", "Destroy target creature.")
    private val colossus = Cards.invent("Orchard Colossus", "{4}{G}{G}{G}", "Creature — Plant Beast", """
        Trample
        Orchard Colossus's power and toughness are each equal to the number of lands you control.
        When this creature enters, you may put a land card from your hand onto the battlefield tapped. If you do, draw a card and repeat this process.
    """.trimIndent(), "*/*")
    private val bear = Cards.invent("Quiet Moor Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val fadeout = Cards.invent("Fadeout", "{X}{U}", "Instant",
        "Counter target spell unless its controller pays {X}. If that spell is countered this way, exile it instead of putting it into its owner's graveyard.")
    private val bolt = Cards.invent("Fifteen Spark", "{R}", "Instant", "Fifteen Spark deals 1 damage to any target.")

    // CR 614.1c, 614.12: with fewer than two other lands the land enters tapped
    @Test
    fun gladeEntersTappedWithoutTwoOtherLands() = scenario {
        alice.hand(glade)
        alice.battlefield("Forest")
        alice.plays(glade)
        expect { glade.tapped shouldBe true }
    }

    // CR 614.12: two other lands are enough for it to enter untapped
    @Test
    fun gladeEntersUntappedWithTwoOtherLands() = scenario {
        alice.hand(glade)
        alice.battlefield("Forest", "Swamp")
        alice.plays(glade)
        expect { glade.tapped shouldBe false }
    }

    // CR 122.1, 613.1f: the fourth +1/+1 counter, put on by gaining life, grants flying and vigilance
    @Test
    fun voiceGainsFlyingAndVigilanceAtFourCounters() = scenario {
        alice.battlefield(voice, "Plains", counters = listOf(p1p1 to 3))
        alice.hand(draught)
        expect { voice lacksKeyword Keyword.Flying.kind }
        alice.casts(draught)
        resolveAll()
        expect {
            voice.counters(p1p1) shouldBe 4
            voice hasKeyword Keyword.Flying.kind
            voice hasKeyword Keyword.Vigilance.kind
            voice lacksKeyword Keyword.Indestructible.kind
        }
    }

    // CR 122.1, 702.12: the tenth counter grants indestructible
    @Test
    fun voiceBecomesIndestructibleAtTenCounters() = scenario {
        alice.battlefield(voice, "Plains", counters = listOf(p1p1 to 9))
        alice.hand(draught)
        alice.casts(draught)
        resolveAll()
        expect {
            voice.counters(p1p1) shouldBe 10
            voice hasKeyword Keyword.Indestructible.kind
        }
    }

    // CR 614.1a: gaining 3 life with the Heron gains 4
    @Test
    fun heronAddsOneToLifeGained() = scenario {
        alice.battlefield(heron, "Plains")
        alice.hand(draught)
        alice.casts(draught)
        resolveAll()
        expect { alice.life shouldBe 24 }
    }

    // CR 614.1c: the creature enters tapped
    @Test
    fun phalanxEntersTapped() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(phalanx)
        alice.casts(phalanx)
        resolveAll()
        expect {
            phalanx.zone shouldBe Zone.Battlefield
            phalanx.tapped shouldBe true
        }
    }

    // CR 701.20a: the four revealed cards, lands to the hand and the rest to the graveyard; the fifth card stays
    @Test
    fun harvestTakesLandsAndBinsTheRest() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(harvest)
        alice.library("Mountain", bear, "Island", doom, "Plains")
        alice.casts(harvest)
        resolveAll()
        expect {
            alice.hand has "Mountain"
            alice.hand has "Island"
            alice.graveyard has bear
            alice.graveyard has doom
            alice.library.size shouldBe 1
            alice.hand lacks "Plains"
        }
    }

    // CR 701.23, 603.10a: when it dies its controller may fetch another copy of it from the library
    @Test
    fun throngDyingFindsAnotherThrong() = scenario {
        alice.battlefield(throng, "Swamp")
        alice.hand(doom)
        alice.library(bear, throng, "Island")
        alice.casts(doom, target = throng)
        resolveAll()
        answer(Answer.YesNo(true))
        playOut(throng)
        resolveAll()
        expect {
            alice.hand has throng
            alice.graveyard has doom
        }
    }

    // CR 604.3: power and toughness equal the number of lands, changing as lands arrive
    @Test
    fun colossusSizeCountsLands() = scenario {
        alice.battlefield(colossus, "Forest", "Forest")
        expect {
            colossus.power shouldBe 2
            colossus.toughness shouldBe 2
        }
        alice.battlefield("Forest")
        expect { colossus.power shouldBe 3 }
        bob.battlefield("Forest")
        expect { colossus.power shouldBe 3 }
    }

    // CR 608.2c, 614.1: each land put from the hand lets its controller draw and go again; the process ends when no land is put
    @Test
    fun colossusPutsLandsAndDrawsUntilNoneIsPut() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(colossus, "Mountain", "Island")
        alice.library(bear, bear, bear)
        alice.casts(colossus)
        resolveAll()
        playOut("Mountain", "Island")
        expect {
            "Mountain".tapped shouldBe true
            "Island".tapped shouldBe true
            alice.battlefield has "Mountain"
            alice.battlefield has "Island"
            alice.hand.size shouldBe 2
        }
    }

    // CR 118.12, 614.1a: an unpaid {X} counters the spell and it goes to exile instead of the graveyard
    @Test
    fun fadeoutExilesTheSpellItCounters() = scenario {
        bob.battlefield("Mountain")
        bob.hand(bolt)
        alice.battlefield("Island", "Island")
        alice.hand(fadeout)
        turn(active = bob)
        bob.casts(bolt, target = "alice")
        bob.passes()
        alice.casts(fadeout, target = bolt, x = 1)
        resolveAll()
        if (pending is Decision.May) bob.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            bolt.zone shouldBe Zone.Exile
            bob.graveyard lacks bolt
        }
    }

    // CR 118.12, 614.1a (audit): when its controller pays {X} the spell isn't countered, resolves and goes to the graveyard, not exile
    @Test
    fun fadeoutDoesNotExileASpellThatIsPaidFor() = scenario {
        bob.battlefield("Mountain", "Mountain")
        bob.hand(bolt)
        alice.battlefield("Island", "Island")
        alice.hand(fadeout)
        turn(active = bob)
        bob.casts(bolt, target = "alice")
        bob.passes()
        alice.casts(fadeout, target = bolt, x = 1)
        resolveAll()
        if (pending is Decision.May) bob.answers(Answer.YesNo(true))
        if (pending is Decision.ManaPayment) playOut()
        resolveAll()
        expect {
            bolt.zone shouldBe Zone.Graveyard
            bob.graveyard has bolt
            alice.life shouldBe 19
        }
    }
}
