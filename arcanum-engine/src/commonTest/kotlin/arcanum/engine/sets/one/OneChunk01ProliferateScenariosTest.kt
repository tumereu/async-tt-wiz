package arcanum.engine.sets.one

import arcanum.api.Ability
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.option
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.dft.payYes
import arcanum.engine.sets.ltr.unblocked
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Phyrexia: All Will Be One chunk 01 in play: proliferate after a condition on a destroyed, countered or bounced target as it last existed
 * (CR 608.2h), as many times as fewer lands were put onto the battlefield, twice for a payment, doubled by a replacement (CR 614.1a, 614.5);
 * planeswalkers granted a proliferate loyalty ability; one more counter of a chosen kind (CR 122.1); a reanimation from an opponent's graveyard
 * (CR 110.2a); a sacrifice that can no longer be made (CR 701.21a).
 */
class OneChunk01ProliferateScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val flyingCounter = CounterKind.Keyword(Keyword.Flying.kind)

    private val bower = Cards.invent("Devouring Bower", "{2}{G}", "Sorcery",
        "Destroy target artifact, enchantment, or creature with flying. If that permanent's mana value was 3 or less, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)")
    private val orb = Cards.invent("Widen the Orb", "{3}{G}", "Sorcery",
        "Look at the top six cards of your library. Put up to two land cards from among them onto the battlefield tapped and the rest on the bottom of your library in a random order. If you put fewer than two lands onto the battlefield this way, proliferate a number of times equal to the difference. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)")
    private val vexil = Cards.invent("Vexil, Seeker of Orbs", "{2}{G}{U}", "Legendary Creature — Phyrexian Elf Warrior", """
        When Vexil enters, you may pay {3}. If you do, proliferate twice.
        Whenever you proliferate, draw a card.
    """, "3/3")
    private val bracer = Cards.invent("Oilmoon Bracer", "{2}{U}", "Artifact", """
        Planeswalkers you control have "[0]: Proliferate" and "[−12]: Take an extra turn after this one."
        Whenever you cast a noncreature spell, choose a counter on target permanent. Put an additional counter of that kind on that permanent.
    """)
    private val refuse = Cards.invent("Refuse the Flawed", "{1}{U}{U}", "Instant",
        "Counter target spell. If that spell's mana value was 3 or less, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)")
    private val noose = Cards.invent("Ichor Noose", "{1}{U}", "Instant",
        "Return target nonland permanent to its owner's hand. If that permanent had mana value 3 or less, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)")
    private val quorvath = Cards.invent("Quorvath, Query Sovereign", "{2}{U}{U}", "Legendary Creature — Phyrexian Horror", """
        Flying
        If you would proliferate, proliferate twice instead.
        {1}{U/P}{U/P}, Remove three counters from among other artifacts, creatures, and planeswalkers you control: Put an indestructible counter on Quorvath. ({U/P} can be paid with either {U} or 2 life.)
    """, "3/5")
    private val echo = Cards.invent("Quorvath's Echo", "{2}{U}{U}", "Creature — Phyrexian Horror", "If you would proliferate, proliferate twice instead.", "3/5")
    private val tank = Cards.invent("Tank Rebirth", "{4}{B}", "Sorcery",
        "Put target creature card from a graveyard onto the battlefield under your control. Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)")
    private val imp = Cards.invent("Clamor Imp", "{R}", "Creature — Phyrexian Goblin Warrior", """
        Whenever this creature deals combat damage to a player, you may sacrifice it. If you do, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
        When this creature dies, it deals damage equal to its power to any target.
    """, "1/1")
    private val stiltwalker = Cards.invent("Bronze Stiltwalker", "{1}{G}", "Creature — Phyrexian Spider", """
        Reach
        {1}{G}, Sacrifice this creature: Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
    """, "1/3")

    private val grunt = Cards.invent("Ichorvat Grunt", "{1}{G}", "Creature — Phyrexian Warrior", "", "2/2")
    private val brute = Cards.invent("Glistening Brute", "{4}{G}", "Creature — Phyrexian Beast", "", "4/4")
    private val drake = Cards.invent("Oilwing Drake", "{1}{U}", "Creature — Phyrexian Drake", "Flying", "1/1")
    private val wyrm = Cards.invent("Oil Sip", "{3}{U}{U}", "Creature — Phyrexian Drake", "Flying", "4/4")
    private val orla = Cards.invent("Orla, Oil Seer", "{2}{G}", "Legendary Planeswalker — Orla", "[+1]: You gain 1 life.", loyalty = "3")
    private val spark = Cards.invent("Ichor Spark", "{R}", "Instant", "Ichor Spark deals 2 damage to any target.")
    private val recall = Cards.invent("Recall Draft", "{U}", "Instant", "Return target creature to its owner's hand.")

    /** Answers one proliferate with [names] (permanents, or players by name). */
    private fun Scenario.proliferating(vararg names: String) {
        assertEquals("proliferate", decision<Decision.Targets>().slot)
        targets(*names)
    }

    // CR 608.2h, 701.34a: the destroyed permanent's mana value as it last existed; players with counters can be chosen, each opponent apart
    @Test
    fun bowerProliferatesAfterDestroyingACheapPermanent() = scenario(players = 3) {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.battlefield(grunt, counters = listOf(p1p1 to 1))
        bob.battlefield(drake)
        bob.counters(CounterKind.Poison, 2)
        carol.counters(CounterKind.Poison, 3)
        alice.hand(bower)
        alice.casts(bower, target = drake)
        resolveAll()
        proliferating(grunt, "bob")
        resolveAll()
        expect {
            drake.zone shouldBe Zone.Graveyard
            grunt.counters(p1p1) shouldBe 2
            bob.counters(CounterKind.Poison) shouldBe 3
            carol.counters(CounterKind.Poison) shouldBe 3
        }
    }

    // CR 608.2h: mana value 5 isn't 3 or less: no proliferate
    @Test
    fun bowerDoesntProliferateAfterAnExpensivePermanent() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.battlefield(grunt, counters = listOf(p1p1 to 1))
        bob.battlefield(wyrm)
        alice.hand(bower)
        alice.casts(bower, target = wyrm)
        resolveAll()
        assertNull(pending)
        expect {
            wyrm.zone shouldBe Zone.Graveyard
            grunt.counters(p1p1) shouldBe 1
        }
    }

    // CR 608.2h: the returned permanent's mana value (it is a new object in the hand, CR 400.7)
    @Test
    fun nooseProliferatesAfterReturningACheapPermanent() = scenario {
        alice.battlefield("Island", "Island")
        alice.battlefield(grunt, counters = listOf(p1p1 to 1))
        bob.battlefield(drake)
        alice.hand(noose)
        alice.casts(noose, target = drake)
        resolveAll()
        proliferating(grunt)
        resolveAll()
        expect {
            drake.zone shouldBe Zone.Hand
            grunt.counters(p1p1) shouldBe 2
        }
    }

    // CR 608.2h: a returned permanent of mana value 5: no proliferate
    @Test
    fun nooseDoesntProliferateAfterAnExpensivePermanent() = scenario {
        alice.battlefield("Island", "Island")
        alice.battlefield(grunt, counters = listOf(p1p1 to 1))
        bob.battlefield(wyrm)
        alice.hand(noose)
        alice.casts(noose, target = wyrm)
        resolveAll()
        assertNull(pending)
        expect { grunt.counters(p1p1) shouldBe 1 }
    }

    // CR 608.2h: the countered spell's mana value as it last existed on the Stack
    @Test
    fun refuseProliferatesAfterCounteringACheapSpell() = scenario {
        turn(number = 2, active = bob)
        bob.battlefield("Island", "Island")
        bob.hand(drake)
        alice.battlefield("Island", "Island", "Island")
        alice.battlefield(grunt, counters = listOf(p1p1 to 1))
        alice.hand(refuse)
        bob.casts(drake)
        bob.passes()
        alice.casts(refuse, target = drake)
        resolveAll()
        proliferating(grunt)
        resolveAll()
        expect {
            drake.zone shouldBe Zone.Graveyard
            grunt.counters(p1p1) shouldBe 2
        }
    }

    // CR 701.34a, 400.7: one land put onto the battlefield tapped, one fewer than two: proliferate once
    @Test
    fun orbProliferatesForEachLandFewerThanTwo() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.battlefield(grunt, counters = listOf(p1p1 to 1))
        alice.library("Forest", brute, brute, brute, brute, brute, "Island")
        alice.hand(orb)
        alice.casts(orb)
        resolveAll()
        alice.chooses("Forest")
        resolveAll()
        proliferating(grunt)
        resolveAll()
        assertNull(pending)
        expect {
            grunt.counters(p1p1) shouldBe 2
            alice.battlefield.names.count { it == "Forest" } shouldBe 5
            card("Forest", 4).tapped shouldBe true
        }
    }

    // CR 701.34a: no land among them: proliferate twice
    @Test
    fun orbProliferatesTwiceWithNoLand() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.battlefield(grunt, counters = listOf(p1p1 to 1))
        alice.library(brute, brute, brute, brute, brute, brute, "Island")
        alice.hand(orb)
        alice.casts(orb)
        resolveAll()
        if (pending is Decision.ChooseObjects) answer(Answer.Objects(emptyList()))
        resolveAll()
        proliferating(grunt)
        resolveAll()
        proliferating(grunt)
        resolveAll()
        expect { grunt.counters(p1p1) shouldBe 3 }
    }

    // CR 701.34a, 603.2: "proliferate twice" is two proliferates: "whenever you proliferate" triggers for each
    @Test
    fun vexilPaysToProliferateTwiceAndDrawsForEach() = scenario {
        alice.battlefield("Forest", "Forest", "Island", "Island", "Island", "Island", "Island")
        alice.battlefield(grunt, counters = listOf(p1p1 to 1))
        alice.library("Island", "Island", "Island")
        alice.hand(vexil)
        alice.casts(vexil)
        resolveAll()
        payYes()
        proliferating(grunt)
        resolveAll()
        proliferating(grunt)
        resolveAll()
        expect {
            grunt.counters(p1p1) shouldBe 3
            alice.hand.size shouldBe 2
        }
    }

    // CR 606.3, 613.1f: a planeswalker granted "[0]: Proliferate" activates it as a loyalty ability
    @Test
    fun bracerGivesPlaneswalkersProliferate() = scenario {
        alice.battlefield(bracer)
        alice.battlefield(orla, counters = listOf(CounterKind.Loyalty to 3))
        alice.battlefield(grunt, counters = listOf(p1p1 to 1))
        alice.activates(orla, ability = loyaltyAbility(orla, 0))
        resolveAll()
        proliferating(orla, grunt)
        resolveAll()
        expect {
            orla.counters(CounterKind.Loyalty) shouldBe 4
            grunt.counters(p1p1) shouldBe 2
        }
    }

    // CR 122.1, 122.6: one more counter of the kind chosen among those on the permanent, not the others
    @Test
    fun bracerAddsOneCounterOfTheChosenKind() = scenario {
        alice.battlefield(bracer, "Mountain")
        alice.battlefield(grunt, counters = listOf(p1p1 to 1, flyingCounter to 1))
        alice.hand(spark)
        alice.casts(spark, target = "bob")
        resolveAll()
        targets(grunt)
        resolveAll()
        option("+1/+1")
        resolveAll()
        expect {
            grunt.counters(p1p1) shouldBe 2
            grunt.counters(flyingCounter) shouldBe 1
        }
    }

    // CR 614.1a: "If you would proliferate, proliferate twice instead"
    @Test
    fun quorvathProliferatesTwiceInstead() = scenario {
        alice.battlefield(quorvath, "Forest", "Forest")
        alice.battlefield(stiltwalker)
        alice.battlefield(grunt, counters = listOf(p1p1 to 1))
        alice.activates(stiltwalker)
        resolveAll()
        proliferating(grunt)
        resolveAll()
        proliferating(grunt)
        resolveAll()
        assertNull(pending)
        expect { grunt.counters(p1p1) shouldBe 3 }
    }

    // CR 614.5, 616.1: two such replacements each apply once: proliferate four times
    @Test
    fun twoDoublersProliferateFourTimes() = scenario {
        alice.battlefield(quorvath, echo, "Forest", "Forest")
        alice.battlefield(stiltwalker)
        alice.battlefield(grunt, counters = listOf(p1p1 to 1))
        alice.activates(stiltwalker)
        resolveAll()
        repeat(4) {
            (pending as? Decision.ChooseReplacement)?.let { answer(Answer.Replacement(it.options.first().index)) }
            proliferating(grunt)
            resolveAll()
        }
        assertNull(pending)
        expect { grunt.counters(p1p1) shouldBe 5 }
    }

    // CR 614.1a: the replacement is of proliferating only by its controller
    @Test
    fun quorvathDoesntDoubleAnOpponentsProliferate() = scenario {
        turn(number = 2, active = bob)
        bob.battlefield(stiltwalker, "Forest", "Forest")
        bob.battlefield(grunt, counters = listOf(p1p1 to 1))
        alice.battlefield(quorvath)
        bob.activates(stiltwalker)
        resolveAll()
        proliferating(grunt)
        resolveAll()
        assertNull(pending)
        expect { grunt.counters(p1p1) shouldBe 2 }
    }

    // CR 110.2a: a creature card from another player's graveyard enters under your control
    @Test
    fun tankRebirthTakesFromAnyGraveyard() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        carol.graveyard(brute)
        bob.counters(CounterKind.Poison, 1)
        alice.hand(tank)
        alice.casts(tank, target = brute)
        resolveAll()
        proliferating("bob")
        resolveAll()
        expect {
            brute.zone shouldBe Zone.Battlefield
            brute.controller shouldBe alice.id
            bob.counters(CounterKind.Poison) shouldBe 2
        }
    }

    // CR 701.21a, 608.2h: sacrificed after combat damage, it proliferates; its dies trigger deals its last known power
    @Test
    fun impSacrificesToProliferateAndDealsItsPower() = scenario {
        alice.battlefield(imp, counters = listOf(p1p1 to 1))
        alice.battlefield(grunt, counters = listOf(p1p1 to 1))
        alice.library("Mountain")
        unblocked(imp)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        may(true)
        resolveAll()
        proliferating(grunt)
        resolveAll()
        targets("bob")
        resolveAll()
        expect {
            imp.zone shouldBe Zone.Graveyard
            grunt.counters(p1p1) shouldBe 2
            bob.life shouldBe 20 - 2 - 2
        }
    }

    // CR 701.21a: returned to hand in response, it can't be sacrificed: "if you do" is false, no proliferate
    @Test
    fun impThatLeftCantBeSacrificed() = scenario {
        alice.battlefield(imp)
        alice.battlefield("Island")
        alice.battlefield(grunt, counters = listOf(p1p1 to 1))
        alice.hand(recall)
        unblocked(imp)
        advanceTo(TurnStep.CombatDamage)
        if (pending is Decision.OrderTriggers) resolveAll()
        alice.casts(recall, target = imp)
        resolve()
        resolveAll()
        if (pending is Decision.May) may(true)
        resolveAll()
        assertNull(pending)
        expect {
            imp.zone shouldBe Zone.Hand
            grunt.counters(p1p1) shouldBe 1
        }
    }

    /** The index among [name]'s activated abilities of the loyalty ability costing [amount] loyalty. */
    private fun Scenario.loyaltyAbility(name: String, amount: Int): Int =
        view()[card(name)]!!.characteristics!!.abilities.filter { it.ability is Ability.Activated }.indexOfFirst {
            (it.ability as Ability.Activated).cost.parts.any { p -> p is CostPart.Loyalty && p.amount == Value.Fixed(amount) }
        }.also { check(it >= 0) { "$name has no loyalty ability [$amount]" } }
}
