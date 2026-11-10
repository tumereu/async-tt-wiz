package arcanum.engine.sets.fdn

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Chunk 18 played: the invented names carry the oracle text of Ajani Caller of the Pride, Apothecary Stomper, Armasaur Guide, Brineborn Cutthroat, Fleeting Flight, Good-Fortune Unicorn, Halana and Alena, High-Society Hunter, Ingenious Leonin, Kaito Cunning Infiltrator, Mold Adder, Nessian Hornbeetle, Stromkirk Bloodthief, Sylvan Scavenging, Treetop Snarespinner and Youthful Valkyrie. */
class FdnChunk18PutCountersScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private val ajani = Cards.invent("Ajani, Herald of the Pride", "{1}{W}{W}", "Legendary Planeswalker — Ajani", """
        [+1]: Put a +1/+1 counter on up to one target creature.
        [−3]: Target creature gains flying and double strike until end of turn.
        [−8]: Create X 2/2 white Cat creature tokens, where X is your life total.
    """, loyalty = "4")
    private val stomper = Cards.invent("Herbalist Stomper", "{4}{G}{G}", "Creature — Elephant", """
        Vigilance (Attacking doesn't cause this creature to tap.)
        When this creature enters, choose one —
        • Put two +1/+1 counters on target creature you control.
        • You gain 4 life.
    """, "4/4")
    private val guide = Cards.invent("Ridgeback Guide", "{4}{W}", "Creature — Dinosaur", """
        Vigilance (Attacking doesn't cause this creature to tap.)
        Whenever you attack with three or more creatures, put a +1/+1 counter on target creature you control.
    """, "4/4")
    private val cutthroat = Cards.invent("Tidebound Cutthroat", "{1}{U}", "Creature — Merfolk Pirate", """
        Flash (You may cast this spell any time you could cast an instant.)
        Whenever you cast a spell during an opponent's turn, put a +1/+1 counter on this creature.
    """, "2/1")
    private val glide = Cards.invent("Fleeting Glide", "{W}", "Instant",
        "Put a +1/+1 counter on target creature. It gains flying until end of turn. Prevent all combat damage that would be dealt to it this turn.")
    private val unicorn = Cards.invent("Fair-Fortune Unicorn", "{1}{G}{W}", "Creature — Unicorn", "Whenever another creature you control enters, put a +1/+1 counter on that creature.", "2/2")
    private val halana = Cards.invent("Halana and Alena, Pals", "{2}{R}{G}", "Legendary Creature — Human Ranger", """
        Reach (This creature can block creatures with flying.)
        First strike (This creature deals combat damage before creatures without first strike.)
        At the beginning of combat on your turn, put X +1/+1 counters on another target creature you control, where X is Halana and Alena's power. That creature gains haste until end of turn.
    """, "2/3")
    private val hunter = Cards.invent("High-Rank Hunter", "{3}{B}{B}", "Creature — Vampire Noble", """
        Flying
        Whenever this creature attacks, you may sacrifice another creature. If you do, put a +1/+1 counter on this creature.
        Whenever another nontoken creature dies, draw a card.
    """, "5/3")
    private val leonin = Cards.invent("Clever Leonin", "{4}{W}", "Creature — Cat Soldier",
        "{3}{W}: Put a +1/+1 counter on another target attacking creature you control. If that creature is a Cat, it gains first strike until end of turn. (It deals combat damage before creatures without first strike.)", "4/4")
    private val kaito = Cards.invent("Kaito, Crafty Infiltrator", "{1}{U}{U}", "Legendary Planeswalker — Kaito", """
        Whenever a creature you control deals combat damage to a player, put a loyalty counter on Kaito.
        [+1]: Up to one target creature you control can't be blocked this turn. Draw a card, then discard a card.
        [−2]: Create a 2/1 blue Ninja creature token.
        [−9]: You get an emblem with "Whenever a player casts a spell, you create a 2/1 blue Ninja creature token."
    """, loyalty = "3")
    private val adder = Cards.invent("Mildew Adder", "{G}", "Creature — Fungus Snake",
        "Whenever an opponent casts a blue or black spell, you may put a +1/+1 counter on this creature.", "1/1")
    private val hornweevil = Cards.invent("Nessian Hornweevil", "{1}{G}", "Creature — Insect",
        "At the beginning of combat on your turn, if you control another creature with power 4 or greater, put a +1/+1 counter on this creature.", "2/2")
    private val thief = Cards.invent("Stormkirk Bloodthief", "{2}{B}", "Creature — Vampire Rogue",
        "At the beginning of your end step, if an opponent lost life this turn, put a +1/+1 counter on target Vampire you control.", "2/2")
    private val foraging = Cards.invent("Sylvan Foraging", "{1}{G}{G}", "Enchantment", """
        At the beginning of your end step, choose one —
        • Put a +1/+1 counter on target creature you control.
        • Create a 3/3 green Raccoon creature token if you control a creature with power 4 or greater.
    """)
    private val webspinner = Cards.invent("Treetop Webspinner", "{3}{G}", "Creature — Spider", """
        Reach (This creature can block creatures with flying.)
        Deathtouch (Any amount of damage this deals to a creature is enough to destroy it.)
        {2}{G}: Put a +1/+1 counter on target creature you control. Activate only as a sorcery.
    """, "1/4")
    private val seraph = Cards.invent("Youthful Seraph", "{1}{W}", "Creature — Angel", """
        Flying
        Whenever another Angel you control enters, put a +1/+1 counter on this creature.
    """, "1/3")

    private val ox = Cards.invent("Stout Ox", "{3}{G}", "Creature — Ox", "", "5/5")
    private val cub = Cards.invent("Rill Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val bobBear = Cards.invent("Bramble Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val tabby = Cards.invent("Dusk Tabby", "{1}{W}", "Creature — Cat", "", "2/2")
    private val angel = Cards.invent("Dawn Angel", "{2}{W}", "Creature — Angel", "", "2/2")
    private val bat = Cards.invent("Cave Bat", "{B}", "Creature — Bat", "", "1/1")
    private val vamp = Cards.invent("Dusk Vampire", "{1}{B}", "Creature — Vampire", "", "2/2")
    private val blueSpell = Cards.invent("Tidal Quip", "{U}", "Instant", "Draw a card.")
    private val greenSpell = Cards.invent("Leaf Quip", "{G}", "Instant", "You gain 1 life.")
    private val blackSpell = Cards.invent("Gloom Quip", "{B}", "Instant", "You gain 1 life.")
    private val drain = Cards.invent("Sip of Gloom", "{B}", "Sorcery", "Each opponent loses 1 life.")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    private fun Scenario.count(name: String) = state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) } == name }

    private fun Scenario.chooseTarget(name: String) {
        alice.answers(Answer.Targets(listOf(target(name))))
        resolveAll()
    }

    // CR 606.4, 122.1: +1 puts the counter on the chosen creature and adds loyalty
    @Test
    fun ajaniPlusOnePutsACounter() = scenario {
        alice.battlefield(ajani, counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield(cub)
        alice.activates(ajani, ability = 0, target = cub)
        resolveAll()
        expect {
            cub.counters(p1p1) shouldBe 1
            ajani.counters(CounterKind.Loyalty) shouldBe 5
        }
    }

    // CR 115.4 ("up to one"): the +1 may have no target and still adds loyalty
    @Test
    fun ajaniPlusOneWithNoTarget() = scenario {
        alice.battlefield(ajani, counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield(cub)
        alice.activates(ajani, ability = 0)
        alice.answers(Answer.Targets(emptyList()))
        resolveAll()
        expect {
            cub.counters(p1p1) shouldBe 0
            ajani.counters(CounterKind.Loyalty) shouldBe 5
        }
    }

    // CR 606.4, 702.9, 702.4: -3 gives flying and double strike
    @Test
    fun ajaniMinusThreeGivesFlyingAndDoubleStrike() = scenario {
        alice.battlefield(ajani, counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield(cub)
        alice.activates(ajani, ability = 1, target = cub)
        resolveAll()
        expect {
            cub hasKeyword Keyword.Flying.kind
            cub hasKeyword Keyword.DoubleStrike.kind
            ajani.counters(CounterKind.Loyalty) shouldBe 1
        }
    }

    // CR 107.3, 111.1: X is her life total as the ability resolves
    @Test
    fun ajaniMinusEightMakesACatPerLife() = scenario {
        alice.battlefield(ajani, counters = listOf(CounterKind.Loyalty to 8))
        alice.activates(ajani, ability = 2)
        resolveAll()
        expect {
            count("Cat Token") shouldBe 20
            "Cat Token".power shouldBe 2
            ajani.zone shouldBe Zone.Graveyard
        }
    }

    // CR 700.2, 122.1: the first mode puts two counters
    @Test
    fun stomperPutsTwoCounters() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", cub, bobBear)
        alice.hand(stomper)
        alice.casts(stomper)
        resolveAll()
        alice.answers(Answer.Modes(listOf(0)))
        resolveAll()
        chooseTarget(cub)
        expect {
            cub.counters(p1p1) shouldBe 2
            bobBear.counters(p1p1) shouldBe 0
        }
    }

    // CR 700.2: the second mode gains 4 life
    @Test
    fun stomperGainsFourLife() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(stomper)
        alice.casts(stomper)
        resolveAll()
        alice.answers(Answer.Modes(listOf(1)))
        resolveAll()
        expect { alice.life shouldBe 24 }
    }

    // CR 508.1, 603.2: three attackers trigger it once, the counter goes on the chosen creature
    @Test
    fun guideTriggersWithThreeAttackers() = scenario {
        alice.battlefield(guide, cub, tabby, ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cub, tabby, ox)
        resolveAll()
        chooseTarget(tabby)
        expect {
            tabby.counters(p1p1) shouldBe 1
            cub.counters(p1p1) shouldBe 0
            guide.counters(p1p1) shouldBe 0
        }
    }

    // CR 508.1: two attackers are not enough
    @Test
    fun guideDoesNotTriggerWithTwoAttackers() = scenario {
        alice.battlefield(guide, cub, tabby)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cub, tabby)
        resolveAll()
        expect {
            cub.counters(p1p1) shouldBe 0
            tabby.counters(p1p1) shouldBe 0
            guide.counters(p1p1) shouldBe 0
        }
    }

    // CR 603.2, 117.1a: a spell cast in the opponent's turn grows it
    @Test
    fun cutthroatGrowsWhenYouCastInTheOpponentsTurn() = scenario {
        alice.battlefield("Island", cutthroat)
        alice.hand(blueSpell)
        alice.library("Island", "Island")
        turn(1, bob, TurnStep.PrecombatMain)
        passPriority()
        alice.casts(blueSpell)
        resolveAll()
        expect { cutthroat.counters(p1p1) shouldBe 1 }
    }

    // CR 603.2: casting in your own turn does not
    @Test
    fun cutthroatDoesNotGrowInYourOwnTurn() = scenario {
        alice.battlefield("Island", cutthroat)
        alice.hand(blueSpell)
        alice.library("Island", "Island")
        alice.casts(blueSpell)
        resolveAll()
        expect { cutthroat.counters(p1p1) shouldBe 0 }
    }

    // CR 122.1, 702.9, 615.1, 510.2: the target gets a counter and flying, and the blocker's combat damage is prevented
    @Test
    fun glidePreventsCombatDamageToTheTarget() = scenario {
        alice.battlefield("Plains", ox)
        bob.battlefield(bobBear)
        alice.hand(glide)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(bobBear to ox)
        alice.casts(glide, target = bobBear)
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        expect {
            bobBear.counters(p1p1) shouldBe 1
            bobBear.zone shouldBe Zone.Battlefield
            state.obj(card(bobBear).id)!!.damage shouldBe 0
            state.obj(card(ox).id)!!.damage shouldBe 3
        }
    }

    // CR 122.1, 702.9: the counter and flying go on the target
    @Test
    fun glideGivesACounterAndFlying() = scenario {
        alice.battlefield("Plains", cub)
        alice.hand(glide)
        alice.casts(glide, target = cub)
        resolveAll()
        expect {
            cub.counters(p1p1) shouldBe 1
            cub hasKeyword Keyword.Flying.kind
        }
    }

    // CR 603.2: the counter goes on the creature that entered, not on the Unicorn or an opponent's creature
    @Test
    fun unicornPutsACounterOnTheEnteringCreature() = scenario {
        alice.battlefield("Plains", "Plains", unicorn)
        alice.hand(tabby)
        alice.casts(tabby)
        resolveAll()
        expect {
            tabby.counters(p1p1) shouldBe 1
            unicorn.counters(p1p1) shouldBe 0
        }
    }

    // CR 603.2: an opponent's creature does not trigger it
    @Test
    fun unicornIgnoresTheOpponentsCreatures() = scenario {
        alice.battlefield(unicorn)
        bob.battlefield("Plains", "Plains", "Plains")
        bob.hand(bobBear)
        turn(1, bob, TurnStep.PrecombatMain)
        bob.casts(bobBear)
        resolveAll()
        expect { bobBear.counters(p1p1) shouldBe 0 }
    }

    // CR 603.2, 608.2h, 122.1, 702.10: X is her power, the chosen creature also gains haste
    @Test
    fun halanaPutsCountersEqualToHerPower() = scenario {
        alice.battlefield(halana, cub)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect {
            cub.counters(p1p1) shouldBe 2
            cub hasKeyword Keyword.Haste.kind
            halana.counters(p1p1) shouldBe 0
        }
    }

    // CR 603.2, 701.21 ("you may sacrifice"), 608.2c ("if you do")
    @Test
    fun hunterSacrificesToGrow() = scenario {
        alice.battlefield(hunter, bat, cub)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hunter)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        alice.chooses(bat)
        resolveAll()
        expect {
            hunter.counters(p1p1) shouldBe 1
            bat.zone shouldBe Zone.Graveyard
        }
    }

    // CR 603.2: another nontoken creature dying draws a card (the sacrificed bat is such a creature)
    @Test
    fun hunterDrawsWhenAnotherNontokenCreatureDies() = scenario {
        alice.battlefield(hunter, bat, cub)
        alice.library("Island", "Island", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hunter)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        alice.chooses(bat)
        resolveAll()
        expect { alice.hand has "Island" }
    }

    // CR 508.1k: the counter goes on another attacker; a Cat also gains first strike
    @Test
    fun leoninPumpsAnAttackingCatAndGivesFirstStrike() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", leonin, tabby)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(tabby)
        alice.activates(leonin, target = tabby)
        resolveAll()
        expect {
            tabby.counters(p1p1) shouldBe 1
            tabby hasKeyword Keyword.FirstStrike.kind
        }
    }

    // CR 608.2c: a non-Cat gets the counter but no first strike
    @Test
    fun leoninGivesNoFirstStrikeToANonCat() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", leonin, cub)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cub)
        alice.activates(leonin, target = cub)
        resolveAll()
        expect {
            cub.counters(p1p1) shouldBe 1
            cub lacksKeyword Keyword.FirstStrike.kind
        }
    }

    // CR 115.1: the target must be attacking
    @Test
    fun leoninNeedsAnAttackingTarget() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", leonin, tabby)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(leonin)
        assertFailsWith<Throwable> { alice.activates(leonin, target = tabby) }
    }

    // CR 603.2, 120.1, 122.1, 306.5b: combat damage to a player adds a loyalty counter
    @Test
    fun kaitoGainsLoyaltyFromCombatDamage() = scenario {
        alice.battlefield(kaito, cub, counters = listOf(CounterKind.Loyalty to 3))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cub)
        advanceTo(TurnStep.PostcombatMain)
        resolveAll()
        expect {
            kaito.counters(CounterKind.Loyalty) shouldBe 4
            bob.life shouldBe 18
        }
    }

    // CR 606.4, 111.1: -2 creates a 2/1 blue Ninja
    @Test
    fun kaitoMinusTwoCreatesANinja() = scenario {
        alice.battlefield(kaito, counters = listOf(CounterKind.Loyalty to 3))
        alice.activates(kaito, ability = 1)
        resolveAll()
        expect {
            count("Ninja Token") shouldBe 1
            "Ninja Token".power shouldBe 2
            "Ninja Token".toughness shouldBe 1
            kaito.counters(CounterKind.Loyalty) shouldBe 1
        }
    }

    // CR 603.2, 105.1: a blue spell lets you grow it
    @Test
    fun adderGrowsOnABlueSpell() = scenario {
        alice.battlefield(adder)
        bob.battlefield("Island")
        bob.hand(blueSpell)
        bob.library("Island", "Island")
        turn(1, bob, TurnStep.PrecombatMain)
        bob.casts(blueSpell)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect { adder.counters(p1p1) shouldBe 1 }
    }

    // CR 603.2: "you may": declining puts no counter
    @Test
    fun adderMayDecline() = scenario {
        alice.battlefield(adder)
        bob.battlefield("Island")
        bob.hand(blueSpell)
        bob.library("Island", "Island")
        turn(1, bob, TurnStep.PrecombatMain)
        bob.casts(blueSpell)
        resolveAll()
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect { adder.counters(p1p1) shouldBe 0 }
    }

    // CR 105.1: a green spell is neither blue nor black
    @Test
    fun adderIgnoresAGreenSpell() = scenario {
        alice.battlefield(adder)
        bob.battlefield("Forest")
        bob.hand(greenSpell)
        turn(1, bob, TurnStep.PrecombatMain)
        bob.casts(greenSpell)
        resolveAll()
        expect { adder.counters(p1p1) shouldBe 0 }
    }

    // CR 105.1, 603.2: a black spell counts as well
    @Test
    fun adderGrowsOnABlackSpell() = scenario {
        alice.battlefield(adder)
        bob.battlefield("Swamp")
        bob.hand(blackSpell)
        turn(1, bob, TurnStep.PrecombatMain)
        bob.casts(blackSpell)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect { adder.counters(p1p1) shouldBe 1 }
    }

    // CR 603.4, 202.3: another creature with power 4 or greater, at the beginning of combat
    @Test
    fun hornweevilGrowsWithABigCreature() = scenario {
        alice.battlefield(hornweevil, ox)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { hornweevil.counters(p1p1) shouldBe 1 }
    }

    // CR 603.4: with no such creature the intervening "if" stops it (it doesn't count itself)
    @Test
    fun hornweevilDoesNotGrowAlone() = scenario {
        alice.battlefield(hornweevil, cub)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { hornweevil.counters(p1p1) shouldBe 0 }
    }

    // CR 603.4, 119.3: an opponent lost life this turn
    @Test
    fun thiefGrowsAVampireWhenAnOpponentLostLife() = scenario {
        alice.battlefield("Swamp", thief, vamp)
        alice.hand(drain)
        alice.casts(drain)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        chooseTarget(vamp)
        expect {
            vamp.counters(p1p1) shouldBe 1
            thief.counters(p1p1) shouldBe 0
        }
    }

    // CR 603.4: no life lost, no trigger
    @Test
    fun thiefDoesNothingWhenNobodyLostLife() = scenario {
        alice.battlefield(thief, vamp)
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            vamp.counters(p1p1) shouldBe 0
            thief.counters(p1p1) shouldBe 0
        }
    }

    // CR 700.2, 122.1: the first mode puts a counter on a creature you control
    @Test
    fun foragingPutsACounter() = scenario {
        alice.battlefield(foraging, cub, bobBear)
        advanceTo(TurnStep.End)
        resolveAll()
        alice.answers(Answer.Modes(listOf(0)))
        resolveAll()
        chooseTarget(cub)
        expect {
            cub.counters(p1p1) shouldBe 1
            bobBear.counters(p1p1) shouldBe 0
        }
    }

    // CR 700.2, 111.1: the second mode makes a 3/3 when you control a creature with power 4 or greater
    @Test
    fun foragingMakesARaccoonWithABigCreature() = scenario {
        alice.battlefield(foraging, ox)
        advanceTo(TurnStep.End)
        resolveAll()
        alice.answers(Answer.Modes(listOf(1)))
        resolveAll()
        expect {
            count("Raccoon Token") shouldBe 1
            "Raccoon Token".power shouldBe 3
        }
    }

    // CR 608.2c: without such a creature the second mode does nothing
    @Test
    fun foragingMakesNoRaccoonWithoutABigCreature() = scenario {
        alice.battlefield(foraging, cub)
        advanceTo(TurnStep.End)
        resolveAll()
        alice.answers(Answer.Modes(listOf(1)))
        resolveAll()
        expect { count("Raccoon Token") shouldBe 0 }
    }

    // CR 602.5d: sorcery timing; the counter goes on the target
    @Test
    fun webspinnerPutsACounterAtSorcerySpeed() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", webspinner, cub)
        alice.activates(webspinner, target = cub)
        resolveAll()
        expect { cub.counters(p1p1) shouldBe 1 }
    }

    // CR 602.5d: not in combat
    @Test
    fun webspinnerCantActivateInCombat() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", webspinner, cub)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<Throwable> { alice.activates(webspinner, target = cub) }
    }

    // CR 603.2, 205.3m: another Angel entering grows her; a non-Angel does not
    @Test
    fun seraphGrowsForAnotherAngel() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", seraph)
        alice.hand(angel, tabby)
        alice.casts(angel)
        resolveAll()
        alice.casts(tabby)
        resolveAll()
        expect {
            seraph.counters(p1p1) shouldBe 1
            angel.counters(p1p1) shouldBe 0
        }
    }
}
