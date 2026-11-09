package arcanum.engine.sets.fdn

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The FDN affinity, doubling, fight and Food cards, played: the invented names carry the oracle text of Gate Colossus, Claws Out, Biogenic Upgrade,
 * Bulk Up, Mossborn Hydra, Twinflame Tyrant, Zimone, Paradox Sculptor, Gratuitous Violence, Affectionate Indrik, Bushwhack, Primal Might, Cat Collector,
 * Bake into a Pie, Eager Trufflesnout and Midnight Snack.
 */
class FdnChunk04DoubleFightFoodScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val cub = Cards.invent("Burrow Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val brute = Cards.invent("Burrow Brute", "{1}{G}{G}", "Creature — Ogre", "", "5/5")
    private val tabby = Cards.invent("Burrow Tabby", "{W}", "Creature — Cat", "", "1/1")
    private val gate = Cards.invent("Burrow Gate", null, "Land — Gate", "{T}: Add {C}.")
    private val lockbox = Cards.invent("Pewter Lockbox", "{1}", "Artifact", "")
    private val spark = Cards.invent("Scorching Spark", "{R}", "Instant", "Scorching Spark deals 1 damage to any target.")
    private val cull = Cards.invent("Sudden Cull", "{B}", "Instant", "Destroy target creature.")

    private val colossus = Cards.invent("Gatehold Colossus", "{8}", "Artifact Creature — Construct", """
        Affinity for Gates (This spell costs {1} less to cast for each Gate you control.)
        This creature can't be blocked by creatures with power 2 or less.
        Whenever a Gate you control enters, you may put this card from your graveyard on top of your library.
    """, "8/8")
    private val rally = Cards.invent("Purring Rally", "{3}{W}{W}", "Instant", """
        Affinity for Cats (This spell costs {1} less to cast for each Cat you control.)
        Creatures you control get +2/+2 until end of turn.
    """)
    private val upgrade = Cards.invent("Graftbloom Upgrade", "{4}{G}{G}", "Sorcery",
        "Distribute three +1/+1 counters among one, two, or three target creatures, then double the number of +1/+1 counters on each of those creatures.")
    private val swell = Cards.invent("Swell of Strength", "{1}{R}", "Instant", """
        Double target creature's power until end of turn.
        Flashback {4}{R}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val hydra = Cards.invent("Sprouting Hydra", "{2}{G}", "Creature — Elemental Hydra", """
        Trample (This creature can deal excess combat damage to the player or planeswalker it's attacking.)
        This creature enters with a +1/+1 counter on it.
        Landfall — Whenever a land you control enters, double the number of +1/+1 counters on this creature.
    """, "0/0")
    private val tyrant = Cards.invent("Forkflame Tyrant", "{3}{R}{R}", "Creature — Dragon", """
        Flying
        If a source you control would deal damage to an opponent or a permanent an opponent controls, it deals double that damage instead.
    """, "3/5")
    private val sculptor = Cards.invent("Odessa, Counter Sculptor", "{2}{G}{U}", "Legendary Creature — Human Wizard", """
        At the beginning of combat on your turn, put a +1/+1 counter on each of up to two target creatures you control.
        {G}{U}, {T}: Double the number of each kind of counter on up to two target creatures and/or artifacts you control.
    """, "1/4")
    private val excess = Cards.invent("Savage Excess", "{2}{R}{R}{R}", "Enchantment",
        "If a creature you control would deal damage to a permanent or player, it deals double that damage instead.")
    private val indrik = Cards.invent("Quarrelsome Indrik", "{5}{G}", "Creature — Beast",
        "When this creature enters, you may have it fight target creature you don't control. (Each deals damage equal to its power to the other.)", "4/4")
    private val whack = Cards.invent("Thornwhack", "{G}", "Sorcery", """
        Choose one —
        • Search your library for a basic land card, reveal it, put it into your hand, then shuffle.
        • Target creature you control fights target creature you don't control. (Each deals damage equal to its power to the other.)
    """)
    private val might = Cards.invent("Overgrown Might", "{X}{G}", "Sorcery",
        "Target creature you control gets +X/+X until end of turn. Then it fights up to one target creature you don't control. (Each deals damage equal to its power to the other.)")
    private val keeper = Cards.invent("Tabby Keeper", "{2}{W}", "Creature — Human Citizen", """
        When this creature enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        Whenever you gain life for the first time during each of your turns, create a 1/1 white Cat creature token.
    """, "3/2")
    private val comeuppance = Cards.invent("Baked Comeuppance", "{2}{B}{B}", "Instant",
        """Destroy target creature. Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")""")
    private val hog = Cards.invent("Hungry Trufflehog", "{2}{G}", "Creature — Boar", """
        Trample (This creature can deal excess combat damage to the player or planeswalker it's attacking.)
        Whenever this creature deals combat damage to a player, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
    """, "4/2")
    private val morsel = Cards.invent("Moonlit Morsel", "{2}{B}", "Enchantment", """
        Raid — At the beginning of your end step, if you attacked this turn, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        {2}{B}, Sacrifice this enchantment: Target opponent loses X life, where X is the amount of life you gained this turn.
    """)

    private val foodToken = "Food Token"
    private val catToken = "Cat Token"

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    private fun Scenario.toCombat() {
        turn(step = TurnStep.PrecombatMain)
        advanceTo(TurnStep.DeclareAttackers)
    }

    // CR 702.41a, 601.2f: {1} less for each Gate, so two Gates leave {6}
    @Test
    fun colossusCostsLessForEachGate() = scenario {
        alice.battlefield(gate, gate, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(colossus)
        alice.casts(colossus, paying = listOf("Plains", "Plains#1", "Plains#2", "Plains#3", "Plains#4", "Plains#5"))
        expect {
            stack.names shouldBe listOf(colossus)
            card(gate, 0).tapped shouldBe false
            card(gate, 1).tapped shouldBe false
        }
    }

    // CR 509.1b
    @Test
    fun colossusOnlyBlockedByPowerThreeOrMore() = scenario {
        alice.battlefield(colossus)
        bob.battlefield(cub, brute)
        toCombat()
        alice.attacks(colossus)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(cub to colossus) }
        bob.blocks(brute to colossus)
    }

    // CR 113.6m, 603.3: from the graveyard, on a Gate entering, it may go on top
    @Test
    fun colossusReturnsToTheTopWhenAGateEnters() = scenario {
        alice.graveyard(colossus)
        alice.hand(gate)
        alice.library("Island", "Forest")
        alice.plays(gate)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.graveyard lacks colossus
            alice.library.names.first() shouldBe colossus
        }
    }

    // CR 603.5: it may stay in the graveyard
    @Test
    fun colossusMayStayInTheGraveyard() = scenario {
        alice.graveyard(colossus)
        alice.hand(gate)
        alice.plays(gate)
        resolveAll()
        answer(Answer.YesNo(false))
        resolveAll()
        expect { alice.graveyard has colossus }
    }

    // CR 113.6m: the ability works only from the graveyard
    @Test
    fun colossusDoesNothingFromTheBattlefield() = scenario {
        alice.battlefield(colossus)
        alice.hand(gate)
        alice.plays(gate)
        expect { state.stack.size shouldBe 0 }
    }

    // CR 603.2: a land that isn't a Gate doesn't trigger it
    @Test
    fun colossusIgnoresOtherLands() = scenario {
        alice.graveyard(colossus)
        alice.hand("Plains")
        alice.plays("Plains")
        expect { state.stack.size shouldBe 0 }
    }

    // CR 702.41a, 118.7: the reduction takes generic mana only; every creature you control gets the bonus
    @Test
    fun rallyCostsLessForEachCat() = scenario {
        alice.battlefield(tabby, tabby, cub, "Plains", "Plains", "Plains")
        bob.battlefield(brute)
        alice.hand(rally)
        alice.casts(rally, paying = listOf("Plains", "Plains#1", "Plains#2"))
        resolveAll()
        expect {
            card(tabby, 0).power shouldBe 3
            card(tabby, 1).toughness shouldBe 3
            card(cub).power shouldBe 4
            card(brute).power shouldBe 5
        }
    }

    // CR 601.2d, 701.10e: a +1/+1 counter from before doubles along with those of this spell
    @Test
    fun upgradeDistributesThenDoublesOnEach() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.battlefield(cub, counters = listOf(p1p1 to 1))
        alice.battlefield(brute)
        alice.hand(upgrade)
        alice.casts(upgrade, targets = listOf(cub, brute))
        answer(Answer.Division(listOf(Share(target(cub), 2), Share(target(brute), 1))))
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        expect {
            card(cub).counters(p1p1) shouldBe 6
            card(brute).counters(p1p1) shouldBe 2
        }
    }

    // CR 701.10b: +X/+0 only
    @Test
    fun swellDoublesPower() = scenario {
        alice.battlefield(cub, "Mountain", "Mountain")
        alice.hand(swell)
        alice.casts(swell, target = cub)
        resolveAll()
        expect {
            card(cub).power shouldBe 4
            card(cub).toughness shouldBe 2
            alice.graveyard has swell
        }
    }

    // CR 702.34a: cast again from the graveyard, then exiled
    @Test
    fun swellFlashbackExiles() = scenario {
        alice.battlefield(cub, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard(swell)
        alice.casts(swell, target = cub)
        resolveAll()
        expect {
            card(cub).power shouldBe 4
            alice.exile has swell
        }
    }

    // CR 614.1c, 701.10e, 207.2c
    @Test
    fun hydraDoublesItsCountersOnEachLandYouPlay() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(hydra, "Forest")
        alice.casts(hydra)
        resolveAll()
        expect { card(hydra).counters(p1p1) shouldBe 1 }
        alice.plays("Forest")
        resolveAll()
        expect {
            card(hydra).counters(p1p1) shouldBe 2
            card(hydra).power shouldBe 2
        }
    }

    // CR 701.10e: the land doesn't get any; an opponent's land doesn't trigger it
    @Test
    fun hydraIgnoresAnOpponentsLand() = scenario {
        alice.battlefield(hydra, counters = listOf(p1p1 to 3))
        bob.hand("Forest")
        turn(number = 2, active = bob)
        bob.plays("Forest")
        expect {
            state.stack.size shouldBe 0
            card(hydra).counters(p1p1) shouldBe 3
        }
    }

    // CR 614.1a: combat damage to an opponent, doubled
    @Test
    fun tyrantDoublesCombatDamageToAnOpponent() = scenario {
        alice.battlefield(tyrant)
        toCombat()
        alice.attacks(tyrant)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 14 }
    }

    // CR 614.1a: a noncreature source counts: a 2/2 of the opponent's dies to 1 damage doubled, your own 2/2 survives it
    @Test
    fun tyrantDoublesSpellDamageToAnOpponentsPermanent() = scenario {
        alice.battlefield(tyrant, "Mountain")
        bob.battlefield(cub)
        alice.hand(spark)
        alice.casts(spark, target = cub)
        resolveAll()
        expect { bob.graveyard has cub }
    }

    @Test
    fun tyrantDoesNotDoubleDamageToYourOwnPermanent() = scenario {
        alice.battlefield(tyrant, cub, "Mountain", "Mountain")
        alice.hand(spark)
        alice.casts(spark, target = cub, paying = listOf("Mountain"))
        resolveAll()
        expect { alice.battlefield has cub }
    }

    // CR 614.1a: an opponent's source isn't "a source you control"
    @Test
    fun tyrantDoesNotDoubleAnOpponentsDamage() = scenario {
        alice.battlefield(tyrant)
        bob.battlefield(cub, "Mountain")
        bob.hand(spark)
        turn(number = 2, active = bob)
        bob.casts(spark, target = "alice")
        resolveAll()
        expect { alice.life shouldBe 19 }
    }

    // CR 701.10e, 601.2c
    @Test
    fun sculptorDoublesEachKindOfCounterOnCreatureAndArtifact() = scenario {
        val charge = CounterKind.Named("charge")
        alice.battlefield(sculptor, "Forest", "Island")
        alice.battlefield(cub, counters = listOf(p1p1 to 2, charge to 1))
        alice.battlefield(lockbox, counters = listOf(charge to 3))
        alice.activates(sculptor, targets = listOf(cub, lockbox))
        resolveAll()
        expect {
            card(cub).counters(p1p1) shouldBe 4
            card(cub).counters(charge) shouldBe 2
            card(lockbox).counters(charge) shouldBe 6
        }
    }

    // CR 601.2c: an opponent's creature isn't a legal target
    @Test
    fun sculptorCannotTargetAnOpponentsCreature() = scenario {
        alice.battlefield(sculptor, "Forest", "Island")
        bob.battlefield(brute, counters = listOf(p1p1 to 1))
        assertFailsWith<Throwable> { alice.activates(sculptor, target = brute) }
    }

    // CR 603.3d: at the beginning of combat, up to two get a +1/+1 counter
    @Test
    fun sculptorPutsCountersAtTheBeginningOfCombat() = scenario {
        alice.battlefield(sculptor, cub, brute)
        advanceTo(TurnStep.BeginningOfCombat)
        answer(Answer.Targets(listOf(target(cub), target(brute))))
        resolveAll()
        expect {
            card(cub).counters(p1p1) shouldBe 1
            card(brute).counters(p1p1) shouldBe 1
            card(sculptor).counters(p1p1) shouldBe 0
        }
    }

    // CR 614.1a: a creature's damage is doubled; a spell's isn't
    @Test
    fun excessDoublesACreaturesDamage() = scenario {
        alice.battlefield(excess, cub)
        toCombat()
        alice.attacks(cub)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 16 }
    }

    @Test
    fun excessDoesNotDoubleASpellsDamage() = scenario {
        alice.battlefield(excess, "Mountain")
        bob.battlefield(cub)
        alice.hand(spark)
        alice.casts(spark, target = cub)
        resolveAll()
        expect { bob.battlefield has cub }
    }

    // CR 701.14a: each deals damage equal to its power to the other
    @Test
    fun indrikFightsIfYouChoose() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        bob.battlefield(cub)
        alice.hand(indrik)
        alice.casts(indrik)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.graveyard has cub
            alice.battlefield has indrik
        }
    }

    // CR 701.14a: the 5/5 takes 4 and kills it with 5
    @Test
    fun indrikCanLoseTheFight() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        bob.battlefield(brute)
        alice.hand(indrik)
        alice.casts(indrik)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.graveyard has indrik
            bob.battlefield has brute
        }
    }

    // CR 603.5
    @Test
    fun indrikMayDeclineTheFight() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        bob.battlefield(cub)
        alice.hand(indrik)
        alice.casts(indrik)
        resolveAll()
        answer(Answer.YesNo(false))
        resolveAll()
        expect { bob.battlefield has cub }
    }

    // CR 701.14b: gone before its ability resolves, it doesn't fight
    @Test
    fun indrikGoneBeforeResolutionDoesNotFight() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        bob.battlefield(cub, "Swamp")
        bob.hand(cull)
        alice.hand(indrik)
        alice.casts(indrik)
        allPass()
        expect { state.stack.size shouldBe 1 }
        alice.passes()
        bob.casts(cull, target = indrik)
        allPass()
        expect { alice.graveyard has indrik }
        allPass()
        if (pending != null) answer(Answer.YesNo(true))
        resolveAll()
        expect { bob.battlefield has cub }
    }

    // CR 700.2: the first mode finds a basic land
    @Test
    fun whackSearchesForABasicLand() = scenario {
        alice.battlefield("Forest")
        alice.library(cub, "Plains")
        alice.hand(whack)
        alice.casts(whack, modes = listOf(0))
        resolveAll()
        answer(Answer.Objects(listOf(card("Plains"))))
        resolveAll()
        expect {
            alice.hand has "Plains"
            alice.library lacks "Plains"
        }
    }

    // CR 701.14a
    @Test
    fun whackFights() = scenario {
        alice.battlefield("Forest", brute)
        bob.battlefield(cub)
        alice.hand(whack)
        alice.casts(whack, modes = listOf(1), targets = listOf(brute, cub))
        resolveAll()
        expect {
            bob.graveyard has cub
            alice.battlefield has brute
        }
    }

    // CR 107.3, 701.14a: the pump comes first, so X decides the fight
    @Test
    fun mightPumpsThenFights() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", cub)
        bob.battlefield(brute)
        alice.hand(might)
        alice.casts(might, targets = listOf(cub, brute), x = 4)
        resolveAll()
        expect {
            bob.graveyard has brute
            alice.battlefield has cub
            card(cub).power shouldBe 6
        }
    }

    // CR 601.2c: "up to one": with none chosen there's no fight, the pump still happens
    @Test
    fun mightWithoutASecondTargetOnlyPumps() = scenario {
        alice.battlefield("Forest", "Forest", cub)
        bob.battlefield(brute)
        alice.hand(might)
        alice.casts(might, targets = listOf(cub), x = 1)
        alice.answers(Answer.Targets(emptyList()))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        expect {
            card(cub).power shouldBe 3
            bob.battlefield has brute
        }
    }

    // CR 701.14b, 608.2b: the pumped creature gone: no damage either way
    @Test
    fun mightWithoutItsCreatureDoesNotFight() = scenario {
        alice.battlefield("Forest", "Forest", "Swamp", cub)
        bob.battlefield(tabby)
        alice.hand(might, cull)
        alice.casts(might, targets = listOf(cub, tabby), x = 1, paying = listOf("Forest", "Forest#1"))
        alice.casts(cull, target = cub)
        resolveAll()
        expect {
            alice.graveyard has cub
            bob.battlefield has tabby
        }
    }

    // CR 111.10b, 603.2: the Food's life is the first of the turn, the next isn't
    @Test
    fun keeperMakesOneCatOnTheFirstLifeGainOfYourTurn() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(keeper)
        alice.casts(keeper, paying = listOf("Plains", "Plains#1", "Plains#2"))
        resolveAll()
        expect { alice.battlefield has foodToken }
        alice.activates(foodToken, paying = listOf("Plains#3", "Plains#4"))
        resolveAll()
        expect {
            alice.life shouldBe 23
            alice.battlefield.names.count { it == catToken } shouldBe 1
        }
    }

    @Test
    fun keeperMakesNoSecondCatInATurn() = scenario {
        alice.battlefield(keeper, "Plains", "Plains")
        alice.hand(balm, balm)
        alice.casts(balm, paying = listOf("Plains"))
        resolveAll()
        alice.casts(balm, paying = listOf("Plains#1"))
        resolveAll()
        expect {
            alice.life shouldBe 24
            alice.battlefield.names.count { it == catToken } shouldBe 1
        }
    }

    private val balm = Cards.invent("Burrow Balm", "{W}", "Instant", "You gain 2 life.")

    // CR 603.2: only during your own turns: the life on Bob's turn makes nothing
    @Test
    fun keeperIgnoresLifeOnAnOpponentsTurn() = scenario {
        alice.battlefield(keeper, "Plains")
        alice.hand(balm)
        turn(number = 2, active = bob)
        bob.passes()
        alice.casts(balm)
        resolveAll()
        expect {
            alice.life shouldBe 22
            alice.battlefield lacks catToken
        }
    }

    // CR 603.2: a gain before this entered was the first of the turn, so a later one isn't
    @Test
    fun keeperCountsLifeGainedBeforeItEntered() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(balm, keeper, balm)
        alice.casts(balm, paying = listOf("Plains"))
        resolveAll()
        alice.casts(keeper, paying = listOf("Plains#1", "Plains#2", "Plains#3"))
        resolveAll()
        alice.casts(balm, paying = listOf("Plains#4"))
        resolveAll()
        expect {
            alice.life shouldBe 24
            alice.battlefield lacks catToken
        }
    }

    // CR 111.10b: the destroyed creature and a Food
    @Test
    fun comeuppanceDestroysAndMakesAFood() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        bob.battlefield(brute)
        alice.hand(comeuppance)
        alice.casts(comeuppance, target = brute)
        resolveAll()
        expect {
            bob.graveyard has brute
            alice.battlefield has foodToken
        }
    }

    // CR 510.2: unblocked, the damage reaches the player
    @Test
    fun hogMakesAFoodWhenItConnects() = scenario {
        alice.battlefield(hog)
        toCombat()
        alice.attacks(hog)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect {
            bob.life shouldBe 16
            alice.battlefield has foodToken
        }
    }

    // CR 702.19b: blocked by a 5/5, nothing tramples over, so no trigger
    @Test
    fun hogBlockedWithoutExcessMakesNoFood() = scenario {
        alice.battlefield(hog)
        bob.battlefield(brute)
        toCombat()
        alice.attacks(hog)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(brute to hog)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect {
            bob.life shouldBe 20
            alice.battlefield lacks foodToken
        }
    }

    // CR 702.19b: blocked by a 1/1, 3 trample over and the Food comes
    @Test
    fun hogTramplingOverMakesAFood() = scenario {
        alice.battlefield(hog)
        bob.battlefield(tabby)
        toCombat()
        alice.attacks(hog)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(tabby to hog)
        advanceTo(TurnStep.CombatDamage)
        if (pending is Decision.Divide) divideAsSuggested()
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.battlefield has foodToken
        }
    }

    // CR 603.4: having attacked this turn, a Food at the end step
    @Test
    fun morselMakesAFoodIfYouAttacked() = scenario {
        alice.battlefield(morsel, cub)
        toCombat()
        alice.attacks(cub)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield has foodToken }
    }

    // CR 603.4
    @Test
    fun morselMakesNoFoodIfYouDidNotAttack() = scenario {
        alice.battlefield(morsel)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield lacks foodToken }
    }

    // CR 608.2h, 602.2b: X is the life gained this turn, counted as it resolves; the sacrifice is a cost
    @Test
    fun morselDrainsTheLifeGainedThisTurn() = scenario {
        alice.battlefield(morsel, "Swamp", "Swamp", "Swamp", "Plains")
        alice.hand(balm)
        alice.casts(balm, paying = listOf("Plains"))
        resolveAll()
        alice.activates(morsel, target = "bob", paying = listOf("Swamp", "Swamp#1", "Swamp#2"))
        resolveAll()
        expect {
            alice.life shouldBe 22
            bob.life shouldBe 18
            alice.graveyard has morsel
        }
    }
}
