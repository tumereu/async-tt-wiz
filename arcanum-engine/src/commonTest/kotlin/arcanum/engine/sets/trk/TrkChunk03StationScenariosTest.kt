package arcanum.engine.sets.trk

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.KeywordExpansions
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** Star Trek chunk 03 in play: Spacecraft whose triggers count on themselves while not creatures, hexproof while not a creature,
 * "haven't begun an extra turn this game", exiling cards and putting the permanents onto the battlefield, revealing until two
 * lands. Station itself is played in the Edge of Eternities scenarios; the other cards repeat shapes tested before. */
class TrkChunk03StationScenariosTest {
    private val charge: CounterKind = KeywordExpansions.CHARGE
    private val station = "Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at"
    private val barge = Cards.invent("Bone Barge", "{2}{R}", "Legendary Artifact — Spacecraft", """
        Other creatures you control get +1/+0.
        Whenever a creature dies, if Bone Barge isn't a creature, put a charge counter on it.
        $station 10+.)
        10+ | Flying
    """, "4/4")
    private val skiff = Cards.invent("Mist Skiff", "{1}{U}", "Artifact — Spacecraft", """
        Cloaking Device — As long as this Spacecraft isn't a creature, it has hexproof.
        Whenever you cast a noncreature spell, if this Spacecraft isn't a creature, put two charge counters on it.
        $station 8+.)
        8+ | Flying
    """, "4/4")
    private val hub = Cards.invent("Farspace Hub", "{9}", "Legendary Artifact — Spacecraft", """
        When Farspace Hub enters and at the beginning of your upkeep, exile the top four cards of your library. Put all permanent cards exiled this way onto the battlefield.
        Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery.)
        9+ | Creatures you control get +3/+3.
    """)
    private val ark = Cards.invent("Prospector Ark", "{3}{G}", "Legendary Artifact — Spacecraft", """
        When Prospector Ark enters, reveal cards from the top of your library until you reveal two land cards. Put those land cards onto the battlefield tapped and the rest on the bottom of your library in a random order.
        $station 8+.)
        8+ | Flying, vigilance
    """, "3/3")
    private val cutter = Cards.invent("Skirmish Cutter", "{1}{R}", "Artifact — Spacecraft", """
        $station 8+.)
        3+ | At the beginning of combat on your turn, create a 2/1 red Klingon Warrior creature token with haste. Sacrifice it at the beginning of the next end step.
        8+ | Flying
    """, "3/3")
    private val whale = Cards.invent("Beached Whale", "{2}{U}", "Creature — Whale", "", "3/3")
    private val bear = Cards.invent("Trk3 Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val spark = Cards.invent("Trk3 Free Spark", "{R}", "Instant", "This spell deals 3 damage to any target.")
    private val study = Cards.invent("Trk3 Quiet Study", "{U}", "Sorcery", "Draw a card.")
    private val warp = Cards.invent("Trk3 Time Hop", "{U}", "Sorcery", "If you control a Whale and haven't begun an extra turn this game, take an extra turn after this one.")
    private val warp2 = Cards.invent("Trk3 Time Skip", "{U}", "Sorcery", "If you control a Whale and haven't begun an extra turn this game, take an extra turn after this one.")

    // CR 603.4, 122.1: the counter goes on the Spacecraft, not on the creature that died; CR 613.4c: "other" creatures only
    @Test
    fun deathPutsAChargeCounterOnTheSpacecraftNotTheCreature() = scenario(players = 3) {
        alice.battlefield(barge, bear, "Mountain")
        alice.hand(spark)
        alice.casts(spark, target = bear)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            barge.counters(charge) shouldBe 1
        }
    }

    // CR 603.4: once it has become a creature (enough counters), the intervening if fails
    @Test
    fun noCounterOnceTheSpacecraftIsACreature() = scenario {
        alice.battlefield(barge, bear, "Mountain", counters = listOf(charge to 10))
        alice.hand(spark)
        alice.casts(spark, target = bear)
        resolveAll()
        expect { barge.counters(charge) shouldBe 10 }
    }

    // CR 603.4: "put two charge counters on it" is the Spacecraft (named by the condition), not the spell cast
    @Test
    fun castingANoncreatureSpellPutsTwoCountersOnTheSpacecraft() = scenario {
        alice.battlefield(skiff, "Island")
        alice.hand(study, bear)
        alice.library("Island", "Island")
        alice.casts(study)
        resolveAll()
        expect { skiff.counters(charge) shouldBe 2 }
    }

    // CR 604.1, 700.4: hexproof only while it isn't a creature
    @Test
    fun hexproofWhileNotACreature() = scenario {
        alice.battlefield(skiff)
        expect { skiff hasKeyword Keyword.Hexproof }
    }

    @Test
    fun noHexproofOnceItIsACreature() = scenario {
        alice.battlefield(skiff, counters = listOf(charge to 8))
        expect { skiff lacksKeyword Keyword.Hexproof }
    }

    // CR 500.7: the second extra-turn card does nothing once its controller has begun an extra turn this game
    @Test
    fun onlyTheFirstExtraTurnIsTaken() = scenario(players = 3) {
        alice.battlefield(whale, "Island", "Island")
        alice.hand(warp, warp2)
        alice.library("Island", "Island", "Island", "Island")
        bob.library("Island", "Island", "Island", "Island")
        carol.library("Island", "Island", "Island", "Island")
        alice.casts(warp)
        resolveAll()
        assertEquals(listOf(alice.id), state.turn.extraTurns)
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        assertEquals(alice.id, state.turn.active)
        alice.casts(warp2)
        resolveAll()
        assertEquals(emptyList(), state.turn.extraTurns)
    }

    // CR 701.13, 400.7: the top four cards are exiled; the permanent cards enter, the sorcery stays exiled
    @Test
    fun exilesFourAndPutsThePermanentCardsOntoTheBattlefield() = scenario {
        alice.hand(hub)
        alice.library("Island", bear, study, "Forest", "Mountain")
        alice.castsWithoutCosts(hub)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Battlefield
            alice.exile.names shouldBe listOf("Trk3 Quiet Study")
            alice.library.names shouldBe listOf("Mountain")
        }
        assertEquals(setOf("Island", "Forest", "Trk3 Bear", "Farspace Hub"), alice.battlefield.names.toSet())
    }

    // CR 701.20: the cards revealed before the second land go to the bottom with nothing else
    @Test
    fun revealsUntilTwoLandsAndPutsThemTapped() = scenario {
        alice.hand(ark)
        alice.library(bear, "Island", study, "Forest", "Mountain")
        alice.castsWithoutCosts(ark)
        resolveAll()
        expect {
            "Island".tapped shouldBe true
            "Forest".tapped shouldBe true
        }
        // the rest go to the bottom in a random order (CR 701.20)
        assertEquals("Mountain", alice.library.names.first())
        assertEquals(setOf("Trk3 Bear", "Trk3 Quiet Study"), alice.library.names.drop(1).toSet())
    }

    // CR 603.7a, 701.21: the token lives until the next end step
    @Test
    fun cutterMakesAHastyTokenEachCombatThatIsSacrificed() = scenario {
        alice.battlefield(cutter, counters = listOf(charge to 3))
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        resolveAll()
        expect { "Klingon Warrior Token".zone shouldBe Zone.Battlefield }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield.names shouldBe listOf("Skirmish Cutter") }
    }
}
