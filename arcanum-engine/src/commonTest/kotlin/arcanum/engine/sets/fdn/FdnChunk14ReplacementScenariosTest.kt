package arcanum.engine.sets.fdn

import arcanum.api.CounterKind
import arcanum.api.Subtype
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertTrue

/** Chunk 14 played: the invented names carry the oracle text of Adaptive Automaton, Authority of the Consuls, the Guildgates, the gain lands, Diregraf Ghoul, Dryad Militant and Heroes' Bane. */
class FdnChunk14ReplacementScenariosTest {
    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val engine = Cards.invent("Adaptive Engine", "{3}", "Artifact Creature — Construct", """
        As this creature enters, choose a creature type.
        This creature is the chosen type in addition to its other types.
        Other creatures you control of the chosen type get +1/+1.
    """, "2/2")
    private val authority = Cards.invent("Consul's Authority", "{W}", "Enchantment", """
        Creatures your opponents control enter tapped.
        Whenever a creature an opponent controls enters, you gain 1 life.
    """)
    private val gate = Cards.invent("Azure Gate", null, "Land — Gate", """
        This land enters tapped.
        {T}: Add {W} or {U}.
    """)
    private val caves = Cards.invent("Rotfen Caves", null, "Land", """
        This land enters tapped.
        When this land enters, you gain 1 life.
        {T}: Add {B} or {R}.
    """)
    private val ghoul = Cards.invent("Gloomy Ghoul", "{B}", "Creature — Zombie", "This creature enters tapped.", "2/2")
    private val dryad = Cards.invent("Militant Dryad", "{G/W}", "Creature — Dryad Soldier", """
        ({G/W} can be paid with either {G} or {W}.)
        If an instant or sorcery card would be put into a graveyard from anywhere, exile it instead.
    """, "2/1")
    private val bane = Cards.invent("Hydra's Bane", "{3}{G}{G}", "Creature — Hydra", """
        This creature enters with four +1/+1 counters on it.
        {2}{G}{G}: Put X +1/+1 counters on this creature, where X is its power.
    """, "0/0")
    private val elf = Cards.invent("Quiet Elf", "{G}", "Creature — Elf", "", "1/1")
    private val ox = Cards.invent("Plain Ox", "{1}{G}", "Creature — Ox", "", "2/2")
    private val spark = Cards.invent("Brief Spark", "{R}", "Instant", "You gain 1 life.")

    // CR 614.12, 607.2d, 613.4c
    @Test
    fun engineIsTheChosenTypeAndPumpsOthers() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", elf, ox)
        alice.hand(engine)
        alice.casts(engine)
        allPass()
        decision<Decision.ChooseSubtype>()
        alice.answers(Answer.SubtypeChoice(Subtype("Elf")))
        expect {
            assertTrue(Subtype("Elf") in engine.subtypes)
            elf.power shouldBe 2
            ox.power shouldBe 2
            engine.power shouldBe 2
        }
    }

    // CR 614.1c, 603.6a: the opponent's creature enters tapped and Alice gains 1 life; her own does not
    @Test
    fun opponentCreaturesEnterTappedAndGainLife() = scenario {
        turn(1, active = bob)
        alice.battlefield(authority)
        bob.battlefield("Swamp")
        bob.hand(ghoul)
        alice.hand(ox)
        bob.casts(ghoul)
        allPass()
        allPass()
        expect {
            card(ghoul).tapped shouldBe true
            alice.life shouldBe 21
        }
    }

    // CR 614.1c: only the opponent's creatures
    @Test
    fun ownCreaturesEnterUntappedUnderAuthority() = scenario {
        alice.battlefield(authority, "Forest", "Forest")
        alice.hand(ox)
        alice.casts(ox)
        allPass()
        expect {
            card(ox).tapped shouldBe false
            alice.life shouldBe 20
        }
    }

    // CR 614.1c
    @Test
    fun ghoulEntersTapped() = scenario {
        alice.battlefield("Swamp")
        alice.hand(ghoul)
        alice.casts(ghoul)
        allPass()
        expect { card(ghoul).tapped shouldBe true }
    }

    // CR 614.1c, 305.1
    @Test
    fun gateEntersTapped() = scenario {
        alice.hand(gate)
        alice.plays(gate)
        expect { card(gate).tapped shouldBe true }
    }

    // CR 614.1c, 603.6a
    @Test
    fun gainLandEntersTappedAndGainsLife() = scenario {
        alice.hand(caves)
        alice.plays(caves)
        resolveAll()
        expect {
            card(caves).tapped shouldBe true
            alice.life shouldBe 21
        }
    }

    // CR 614.1a, 400.7
    @Test
    fun dryadExilesInstantsInsteadOfTheGraveyard() = scenario {
        alice.battlefield(dryad, "Mountain")
        alice.hand(spark)
        alice.casts(spark)
        allPass()
        expect {
            alice.exile has spark
            alice.graveyard lacks spark
        }
    }

    // CR 614.12, 122.6, 107.3c
    @Test
    fun baneEntersWithFourCountersAndDoublesThem() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(bane)
        alice.casts(bane)
        allPass()
        expect { card(bane).counters(p1p1) shouldBe 4 }
        alice.activates(bane)
        resolveAll()
        expect { card(bane).counters(p1p1) shouldBe 8 }
    }
}
