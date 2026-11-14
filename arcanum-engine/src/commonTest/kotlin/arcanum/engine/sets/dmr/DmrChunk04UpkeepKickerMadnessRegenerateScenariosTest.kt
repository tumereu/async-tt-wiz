package arcanum.engine.sets.dmr

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dft.payYes
import arcanum.engine.sets.dft.payYesOrChoose
import arcanum.engine.sets.dft.yes
import arcanum.engine.sets.ltr.aliceUpkeep
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test

/**
 * Dominaria Remastered chunk 04 in play: cumulative upkeep of mana and of discarding (CR 702.24a), "you may draw unless that player
 * pays" (CR 118.12a), a kicked spell's second target (CR 601.2c), counter unless paying for each card (CR 118.12a), madness on an instant
 * (CR 702.35a), a damage-to-a-creature trigger and a colour choice (CR 120.1, 105.3), an ability's activations counted (CR 602.1), a mana
 * land sacrificed with its last counter and damage prevented with a counter removed (CR 615.5).
 */
class DmrChunk04UpkeepKickerMadnessRegenerateScenariosTest {
    private val age = CounterKind.Named("age")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private val sphinx = Cards.invent("Taunting Sphinx", "{1}{U}{U}", "Creature — Sphinx", """
        Flying
        Cumulative upkeep—Discard a card. (At the beginning of your upkeep, put an age counter on this permanent, then sacrifice it unless you pay its upkeep cost for each age counter on it.)
        When this creature dies, draw a card for each age counter on it.
    """, "4/4")
    private val remora = Cards.invent("Hungering Remora", "{U}", "Enchantment", """
        Cumulative upkeep {1} (At the beginning of your upkeep, put an age counter on this permanent, then sacrifice it unless you pay its upkeep cost for each age counter on it.)
        Whenever an opponent casts a noncreature spell, you may draw a card unless that player pays {4}.
    """)
    private val thunder = Cards.invent("Sister's Thunder", "{2}{W}", "Instant", """
        Kicker {R} (You may pay an additional {R} as you cast this spell.)
        Destroy target artifact or enchantment. If this spell was kicked, it deals damage equal to that permanent's mana value to target creature.
    """)
    private val logic = Cards.invent("Looping Logic", "{2}{U}", "Instant", """
        Counter target spell unless its controller pays {1} for each card in your graveyard.
        Madness {U} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)
    """)
    private val hunt = Cards.invent("Obsessive Hunt", "{U}", "Instant", """
        Draw a card.
        Madness {U} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)
    """)
    private val gorger = Cards.invent("Spiritgorger", "{3}{B}{G}", "Creature — Beast", """
        Whenever this creature deals damage to a creature, put a +1/+1 counter on this creature.
        {B}: Regenerate this creature.
        {G}: This creature becomes the color of your choice until end of turn.
    """, "6/6")
    private val whelp = Cards.invent("Ember Whelp", "{2}{R}{R}", "Creature — Dragon", """
        Flying
        {R}: This creature gets +1/+0 until end of turn. If this ability has been activated four or more times this turn, sacrifice this creature at the beginning of the next end step.
    """, "2/3")
    private val quarry = Cards.invent("Gemstone Quarry", null, "Land", """
        This land enters with three mining counters on it.
        {T}, Remove a mining counter from this land: Add one mana of any color. If there are no mining counters on this land, sacrifice it.
    """)
    private val covey = Cards.invent("Phantom Covey", "{3}{W}{W}", "Creature — Bird Soldier Spirit", """
        Flying
        This creature enters with three +1/+1 counters on it.
        If damage would be dealt to this creature, prevent that damage. Remove a +1/+1 counter from this creature.
    """, "0/0")

    private val trinket = Cards.invent("Dmr Ch4 Trinket", "{3}", "Artifact", "")
    private val bear = Cards.invent("Dmr Ch4 Bear", "{1}{G}", "Creature — Bear", "", "3/3")
    private val wall = Cards.invent("Dmr Ch4 Wall", "{1}{W}", "Creature — Wall", "Defender", "0/4")
    private val spider = Cards.invent("Dmr Ch4 Spider", "{1}{G}", "Creature — Spider", "Reach", "2/3")
    private val archer = Cards.invent("Dmr Ch4 Archer", "{G}", "Creature — Elf Archer", "Reach", "1/1")
    private val ponder = Cards.invent("Dmr Ch4 Ponder", "{U}", "Sorcery", "Draw a card.")
    private val spark = Cards.invent("Dmr Ch4 Spark", "{R}", "Instant", "Dmr Ch4 Spark deals 2 damage to any target.")
    private val looter = Cards.invent("Dmr Ch4 Looter", "{1}{U}", "Creature — Human", "Discard a card: This creature gets +1/+1 until end of turn.", "1/1")

    // CR 702.24a: the second upkeep costs {1} twice; paid, it stays with two age counters
    @Test
    fun remoraPaysItsCumulativeUpkeepForEachAgeCounter() = scenario {
        alice.battlefield("Island", "Island", remora, counters = listOf(age to 1))
        aliceUpkeep()
        resolveAll()
        payYes()
        expect {
            remora.zone shouldBe Zone.Battlefield
            remora.counters(age) shouldBe 2
            "Island".tapped shouldBe true
        }
    }

    // CR 702.24a: not paid, it's sacrificed
    @Test
    fun remoraIsSacrificedWhenTheUpkeepIsNotPaid() = scenario {
        alice.battlefield("Island", "Island", remora, counters = listOf(age to 1))
        aliceUpkeep()
        resolveAll()
        yes(false)
        expect { alice.graveyard has remora }
    }

    // CR 118.12a, 102.1: with three players, the casting opponent declines to pay and the Remora's controller draws
    @Test
    fun remoraDrawsWhenTheCasterDoesNotPay() = scenario(players = 3) {
        alice.battlefield(remora)
        alice.library("Island")
        carol.battlefield("Island", "Island", "Island", "Island", "Island")
        carol.hand(ponder)
        carol.library("Plains", "Plains")
        turn(number = 2, active = carol)
        carol.casts(ponder)
        resolveAll()
        expect { (pending as Decision.May).player shouldBe carol.id }
        answer(Answer.YesNo(false))
        expect { (pending as Decision.May).player shouldBe alice.id }
        answer(Answer.YesNo(true))
        resolveAll()
        expect { alice.hand has "Island" }
    }

    // CR 118.12a: the opponent pays {4} and the Remora's controller doesn't draw
    @Test
    fun remoraDrawsNothingWhenTheCasterPays() = scenario {
        alice.battlefield(remora)
        alice.library("Island")
        bob.battlefield("Island", "Island", "Island", "Island", "Island")
        bob.hand(ponder)
        bob.library("Plains", "Plains")
        turn(number = 2, active = bob)
        bob.casts(ponder)
        resolveAll()
        payYes()
        expect {
            alice.library has "Island"
            bob.hand has "Plains"
        }
    }

    // CR 702.24a: two age counters ask for two discards; paid, it stays
    @Test
    fun sphinxDiscardsACardForEachAgeCounter() = scenario {
        alice.battlefield(sphinx, counters = listOf(age to 1))
        alice.hand("Plains", "Swamp")
        aliceUpkeep()
        resolveAll()
        payYesOrChoose("Plains", "Swamp")
        resolveAll()
        expect {
            sphinx.zone shouldBe Zone.Battlefield
            sphinx.counters(age) shouldBe 2
            alice.graveyard has "Plains"
            alice.graveyard has "Swamp"
        }
    }

    // CR 702.24a, 603.10a: with one card for two age counters the cost can't be paid; the Sphinx dies and draws two, counting the counters
    // it had as it left
    @Test
    fun sphinxThatCantPayDiesAndDrawsForItsAgeCounters() = scenario {
        alice.battlefield(sphinx, counters = listOf(age to 1))
        alice.hand("Plains")
        aliceUpkeep("Forest", "Mountain")
        resolveAll()
        if (pending is Decision.May) yes(false)
        resolveAll()
        expect {
            alice.graveyard has sphinx
            alice.hand has "Plains"
            alice.hand has "Forest"
            alice.hand has "Mountain"
        }
    }

    // CR 601.2c: not kicked, the spell has no creature target and is cast with none on the battlefield
    @Test
    fun thunderUnkickedNeedsNoCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Mountain")
        alice.hand(thunder)
        bob.battlefield(trinket)
        alice.casts(thunder, target = trinket, kicked = 0)
        resolveAll()
        expect { bob.graveyard has trinket }
    }

    // CR 702.33d, 608.2h: kicked, the creature takes damage equal to the destroyed artifact's mana value
    @Test
    fun thunderKickedDealsTheDestroyedPermanentsManaValue() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Mountain")
        alice.hand(thunder)
        bob.battlefield(trinket, bear)
        alice.casts(thunder, targets = listOf(trinket, bear), kicked = 1)
        resolveAll()
        expect {
            bob.graveyard has trinket
            bob.graveyard has bear
        }
    }

    // CR 118.12a: two cards in Alice's graveyard: Bob can't pay {2} with one land, so the spell is countered
    @Test
    fun logicCountersUnlessItsControllerPaysForEachCardInTheGraveyard() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(logic)
        alice.graveyard("Plains", "Swamp")
        bob.battlefield("Island", "Island")
        bob.hand(ponder)
        turn(number = 2, active = bob)
        bob.casts(ponder)
        bob.passes()
        alice.casts(logic, target = ponder)
        resolve()
        if (pending is Decision.May) answer(Answer.YesNo(false))
        resolveAll()
        expect { bob.graveyard has ponder }
    }

    // CR 118.12a: with enough mana Bob pays {2} and his spell resolves
    @Test
    fun logicIsPaidFor() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(logic)
        alice.graveyard("Plains", "Swamp")
        bob.battlefield("Island", "Island", "Island")
        bob.hand(ponder)
        bob.library("Forest")
        turn(number = 2, active = bob)
        bob.casts(ponder)
        bob.passes()
        alice.casts(logic, target = ponder)
        resolve()
        payYes()
        resolveAll()
        expect { bob.hand has "Forest" }
    }

    // CR 702.35a, 608.2g: discarded as a cost, the instant is cast for its madness cost
    @Test
    fun madnessCastsTheInstantWhenDiscarded() = scenario {
        alice.battlefield("Island", looter)
        alice.hand(hunt)
        alice.library("Forest")
        alice.activates(looter, choosing = listOf(hunt))
        playOut()
        expect {
            alice.hand has "Forest"
            alice.graveyard has hunt
        }
    }

    // CR 120.1, 510.2: combat damage to a blocking creature triggers it; damage to a player doesn't
    @Test
    fun gorgerGrowsWhenItDamagesACreature() = scenario {
        alice.battlefield(gorger)
        bob.battlefield(wall)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(gorger)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(wall to gorger)
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        resolveAll()
        expect { gorger.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun gorgerDoesNotGrowFromDamageToAPlayer() = scenario {
        alice.battlefield(gorger)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(gorger)
        advanceTo(TurnStep.PostcombatMain)
        resolveAll()
        expect {
            bob.life shouldBe 14
            gorger.counters(p1p1) shouldBe 0
        }
    }

    // CR 105.3, 613.1e: the chosen colour replaces its colours until end of turn
    @Test
    fun gorgerBecomesTheChosenColor() = scenario {
        alice.battlefield("Forest", gorger)
        bob.library("Plains")
        alice.activates(gorger, ability = 1)
        resolveAll()
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Blue))
        resolveAll()
        expect { gorger.colors shouldBe listOf(Color.Blue) }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { gorger.colors.toSet() shouldBe setOf(Color.Black, Color.Green) }
    }

    // CR 602.1: the fourth activation this turn sets up the sacrifice at the next end step
    @Test
    fun whelpActivatedFourTimesIsSacrificedAtTheEndStep() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", whelp)
        repeat(4) { alice.activates(whelp); resolveAll() }
        expect { whelp.power shouldBe 6 }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.graveyard has whelp }
    }

    @Test
    fun whelpActivatedThreeTimesStays() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", whelp)
        repeat(3) { alice.activates(whelp); resolveAll() }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { whelp.zone shouldBe Zone.Battlefield }
    }

    // CR 605.1a, 614.1c: three counters, three mana; the third use sacrifices it
    @Test
    fun quarryIsSacrificedWithItsLastCounter() = scenario {
        alice.hand(quarry)
        alice.plays(quarry)
        expect { quarry.counters(CounterKind.Named("mining")) shouldBe 3 }
        alice.activates(quarry, color = Color.Red)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Red))
        expect {
            alice.pool.size shouldBe 1
            quarry.counters(CounterKind.Named("mining")) shouldBe 2
            quarry.zone shouldBe Zone.Battlefield
        }
    }

    @Test
    fun quarryWithOneCounterIsSacrificedAfterUse() = scenario {
        alice.battlefield(quarry, counters = listOf(CounterKind.Named("mining") to 1))
        alice.activates(quarry, color = Color.Red)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Red))
        expect {
            alice.pool.size shouldBe 1
            alice.graveyard has quarry
        }
    }

    // CR 615.5: the 2 damage is prevented and one counter removed
    @Test
    fun coveyPreventsDamageAndLosesACounter() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(covey)
        bob.battlefield("Mountain")
        bob.hand(spark)
        alice.casts(covey)
        resolveAll()
        expect { covey.counters(p1p1) shouldBe 3 }
        alice.passes()
        bob.casts(spark, target = covey)
        resolveAll()
        expect {
            covey.counters(p1p1) shouldBe 2
            covey.view.obj?.damage shouldBe 0
        }
    }

    // CR 615.5, 615.13, 510.2: blocked by two creatures, all the simultaneous combat damage is prevented by one application of the effect,
    // so one counter is removed, not one for each blocker
    @Test
    fun coveyBlockedByTwoPreventsAllAndLosesOneCounter() = scenario {
        alice.battlefield(covey, counters = listOf(p1p1 to 3))
        bob.battlefield(spider, archer)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(covey)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(spider to covey, archer to covey)
        allPass()
        divideAsSuggested()
        advanceTo(TurnStep.PostcombatMain)
        expect {
            covey.zone shouldBe Zone.Battlefield
            covey.view.obj?.damage shouldBe 0
            covey.counters(p1p1) shouldBe 2
        }
    }
}
