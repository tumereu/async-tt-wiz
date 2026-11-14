package arcanum.engine.sets.mom

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.resolveAll
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** MOM sweep: siblings of the engine and parser bugs MOM's set work found, each probed on another path the same gap could take. */
class MomSweepScenariosTest {
    private val pt = CounterKind.PowerToughness(1, 1)
    private val slaying = Cards.invent("Sweepfen Slaying", "{B}", "Instant", "Destroy target creature.")
    private val recall = Cards.invent("Sweepfen Recall", "{U}", "Instant", "Return target creature to its owner's hand.")
    private val beetle = Cards.invent("Sweepfen Wall Beetle", "{1}{G}", "Creature — Insect", "", "2/5")
    private val ox = Cards.invent("Sweepfen Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val pup = Cards.invent("Sweepfen Pup", "{1}{G}", "Creature — Dog", "", "2/2")

    // ---- A permanent's own leaves-the-battlefield trigger reads it as it last existed (siblings of "where X is its power")

    private val emberHound = Cards.invent("Sweepfen Ember Hound", "{1}{R}", "Creature — Dog",
        "When this creature dies, it deals damage equal to its power to any target.", "1/1")
    private val hearthOx = Cards.invent("Sweepfen Hearth Ox", "{1}{W}", "Creature — Ox",
        "When this creature dies, you gain life equal to its toughness.", "1/1")
    private val ledgerRat = Cards.invent("Sweepfen Ledger Rat", "{1}{B}", "Creature — Rat",
        "When this creature dies, draw a card for each +1/+1 counter on it.", "1/1")
    private val tidecaller = Cards.invent("Sweepfen Tidecaller", "{1}{U}", "Creature — Merfolk",
        "When this creature leaves the battlefield, you gain life equal to its power.", "1/1")

    private fun killedWithTwoCounters(creature: String, then: arcanum.engine.scenario.Scenario.() -> Unit) = scenario {
        alice.battlefield(creature, counters = listOf(pt to 2))
        alice.library("Island", "Island", "Island")
        bob.battlefield("Swamp")
        bob.hand(slaying)
        alice.passes()
        bob.casts(slaying, target = creature)
        resolveAll()
        then()
    }

    // CR 603.10a, 608.2h: the damage is its power as it last existed on the battlefield, its two +1/+1 counters included
    @Test
    fun aDyingCreatureDealsItsLastPower() = killedWithTwoCounters(emberHound) {
        answer(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    // CR 603.10a: its toughness as it last existed
    @Test
    fun aDyingCreatureGainsItsLastToughness() = killedWithTwoCounters(hearthOx) {
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    // CR 603.10a, 122.2: the counters it had as it last existed (the card in the graveyard has none)
    @Test
    fun aDyingCreatureCountsItsLastCounters() = killedWithTwoCounters(ledgerRat) {
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    // CR 603.10a: returned to the hand, its power as it last existed on the battlefield
    @Test
    fun aBouncedCreatureGainsItsLastPower() = scenario {
        alice.battlefield(tidecaller, counters = listOf(pt to 2))
        bob.battlefield("Island")
        bob.hand(recall)
        alice.passes()
        bob.casts(recall, target = tidecaller)
        resolveAll()
        resolveAll()
        expect {
            alice.hand has tidecaller
            alice.life shouldBe 23
        }
    }

    // ---- "it" in a dies trigger that moves it is the card in the graveyard (siblings of "you may exile this card. When you do")

    private val barrowWisp = Cards.invent("Sweepfen Barrow Wisp", "{1}{B}", "Creature — Spirit",
        "When this creature dies, you may put it on the bottom of its owner's library. If you do, draw a card.", "1/1")
    private val shuffleWisp = Cards.invent("Sweepfen Drift Wisp", "{1}{U}", "Creature — Spirit",
        "When this creature dies, you may shuffle it into its owner's library. If you do, draw a card.", "1/1")
    private val pyreWisp = Cards.invent("Sweepfen Pyre Wisp", "{1}{W}", "Creature — Spirit",
        "When this creature dies, you may exile it. If you do, create a 1/1 white Spirit creature token with flying.", "1/1")

    private fun killed(creature: String, then: arcanum.engine.scenario.Scenario.() -> Unit) = scenario {
        alice.battlefield(creature)
        alice.library("Island")
        bob.battlefield("Swamp")
        bob.hand(slaying)
        alice.passes()
        bob.casts(slaying, target = creature)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        then()
    }

    // CR 400.7, 603.10a: the card put into the graveyard is what goes to the bottom, so "If you do" draws
    @Test
    fun aDeadCreatureGoesToTheBottomAndDraws() = killed(barrowWisp) {
        expect {
            alice.graveyard lacks barrowWisp
            alice.library has barrowWisp
            alice.hand has "Island"
        }
    }

    // CR 400.7, 701.24a: the card in the graveyard is shuffled in, then a card drawn
    @Test
    fun aDeadCreatureIsShuffledInAndDraws() = killed(shuffleWisp) {
        expect {
            alice.graveyard lacks shuffleWisp
            alice.hand.size shouldBe 1
            (alice.library.size + alice.hand.size) shouldBe 2
        }
    }

    // CR 400.7: the card in the graveyard is exiled, so "If you do" makes the Spirit
    @Test
    fun aDeadCreatureIsExiledAndMakesASpirit() = killed(pyreWisp) {
        expect {
            alice.exile has pyreWisp
            alice.battlefield has "Spirit Token"
        }
    }

    // ---- "it", "its" and "that creature" in a trigger about another object (siblings of Tribute's "its power" and Protocol Knight's stun)

    private val warden = Cards.invent("Sweepfen Warden", "{1}{W}", "Creature — Human Cleric",
        "Whenever another creature you control enters, you gain life equal to its toughness.", "1/1")
    private val scholar = Cards.invent("Sweepfen Scholar", "{1}{U}", "Creature — Human Wizard",
        "Whenever another creature you control enters, draw a card if its toughness is 4 or greater.", "1/1")
    private val banner = Cards.invent("Sweepfen Banner", "{2}{R}", "Enchantment",
        "Whenever a creature you control attacks, if its power is 3 or greater, draw a card.")
    private val jailer = Cards.invent("Sweepfen Jailer", "{2}{B}", "Creature — Human Rogue",
        "When this creature enters, tap target creature an opponent controls. Put two -1/-1 counters on that creature.", "2/2")
    private val scrapper = Cards.invent("Sweepfen Scrapper", "{1}{R}", "Creature — Goblin",
        "When this creature enters, destroy target artifact. Its controller loses 2 life.", "2/1")
    private val trinket = Cards.invent("Sweepfen Trinket", "{1}", "Artifact", "")

    // CR 608.2h: "its" is the creature that entered, not the 1/1 with the ability
    @Test
    fun anotherEnteringCreaturesToughnessIsGained() = scenario {
        alice.battlefield(warden, "Forest", "Forest")
        alice.hand(beetle)
        alice.casts(beetle)
        resolveAll()
        expect { alice.life shouldBe 25 }
    }

    // CR 608.2h: "its toughness" checks the creature that entered
    @Test
    fun anotherEnteringCreaturesToughnessIsChecked() = scenario {
        alice.battlefield(scholar, "Forest", "Forest")
        alice.hand(beetle)
        alice.library("Island")
        alice.casts(beetle)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 603.4, 608.2h: the attacking creature's power, once per attacker (the 3/3 draws, the 2/2 doesn't)
    @Test
    fun anAttackersPowerIsChecked() = scenario {
        alice.battlefield(banner, ox, pup)
        alice.library("Island", "Island")
        bob.library("Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox, pup, defender = "bob")
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 608.2c: "that creature" is the tapped target, not the creature whose entering triggered it
    @Test
    fun thatCreatureIsTheTargetTapped() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(jailer)
        bob.battlefield(ox)
        alice.casts(jailer)
        resolveAll()
        expect {
            ox.tapped shouldBe true
            ox.counters(CounterKind.PowerToughness(-1, -1)) shouldBe 2
            jailer.counters(CounterKind.PowerToughness(-1, -1)) shouldBe 0
        }
    }

    // CR 608.2h: "its controller" is the destroyed artifact's controller, as it last existed
    @Test
    fun itsControllerIsTheTargetsController() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(scrapper)
        bob.battlefield(trinket)
        alice.casts(scrapper)
        resolveAll()
        expect {
            bob.graveyard has trinket
            bob.life shouldBe 18
            alice.life shouldBe 20
        }
    }

    // ---- "Each opponent" through one library at a time (sibling of each player's reveal-until), three players

    private val grindstone = Cards.invent("Sweepfen Grindstone", "{1}{U}", "Sorcery",
        "Each opponent reveals cards from the top of their library until they reveal a land card, then puts those cards into their graveyard.")

    // CR 101.4, 701.20a: every opponent's library is revealed in turn and each one's cards go to its own graveyard
    @Test
    fun eachOpponentRevealsUntilALand() = scenario(players = 3) {
        alice.battlefield("Island", "Island")
        alice.hand(grindstone)
        bob.library(pup, "Island", "Island")
        carol.library(ox, beetle, "Island", "Island")
        alice.casts(grindstone)
        resolveAll()
        expect {
            bob.graveyard.size shouldBe 2
            carol.graveyard.size shouldBe 3
            alice.graveyard.size shouldBe 1
        }
    }

    // ---- A reflexive trigger needs its action to have happened (sibling of tapping none for Teferi)

    private val gravecaller = Cards.invent("Sweepfen Gravecaller", "{2}{B}", "Creature — Human Warlock",
        "When this creature enters, return up to one target creature card from your graveyard to your hand. When you do, you lose 2 life.", "2/2")

    // CR 603.12: no target chosen, nothing returned: the reflexive trigger doesn't trigger
    @Test
    fun returningNoCardTriggersNothing() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(gravecaller)
        alice.graveyard(ox, pup)
        alice.casts(gravecaller)
        resolveAll()
        answer(Answer.Targets(emptyList()))
        resolveAll()
        expect {
            alice.life shouldBe 20
            alice.graveyard.size shouldBe 2
        }
    }

    // CR 603.12: a card returned: "when you do" triggers
    @Test
    fun returningACardLosesTheLife() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(gravecaller)
        alice.graveyard(ox, pup)
        alice.casts(gravecaller)
        resolveAll()
        answer(Answer.Targets(listOf(TargetRef.Object(card(ox)))))
        resolveAll()
        expect {
            alice.hand has ox
            alice.life shouldBe 18
        }
    }

    // ---- A mechanic the parser reads must do something (sibling of "assign damage as though it weren't blocked")

    private val skald = Cards.invent("Sweepfen Skald", "{2}{G}", "Creature — Human Warrior",
        "Creatures you control can boast twice during each of your turns rather than once.", "2/2")
    private val braggart = Cards.invent("Sweepfen Braggart", "{1}{R}", "Creature — Human Berserker",
        "Boast — {1}: Draw a card. (Activate only if this creature attacked this turn and only once each turn.)", "2/1")

    // CR 702.142a, 602.5b: boasting is once each turn; "twice … rather than once" makes the limit two during your turns
    @Test
    fun boastingTwiceEachTurn() = scenario {
        alice.battlefield(skald, braggart, "Island", "Island", "Island")
        alice.library("Island", "Island", "Island")
        bob.library("Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(braggart, defender = "bob")
        alice.activates(braggart)
        resolveAll()
        alice.activates(braggart)
        resolveAll()
        assertFailsWith<IllegalArgumentException> { alice.activates(braggart) }
        expect { alice.hand.size shouldBe 2 }
    }

    // CR 702.142a: without it, once
    @Test
    fun boastingOnceEachTurn() = scenario {
        alice.battlefield(braggart, "Island", "Island")
        alice.library("Island", "Island")
        bob.library("Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(braggart, defender = "bob")
        alice.activates(braggart)
        resolveAll()
        assertFailsWith<IllegalArgumentException> { alice.activates(braggart) }
    }

    // ---- Counters of no named kind: the remover chooses (sibling of "remove up to N counters")

    private val unwinding = Cards.invent("Sweepfen Unwinding", "{U}", "Instant", """
        Remove a counter from target permanent.
        Draw a card.
    """.trimIndent())

    // CR 608.2d: one counter of either kind: its controller chooses which
    @Test
    fun removingACounterAsksWhichKind() = scenario {
        alice.battlefield("Island")
        alice.hand(unwinding)
        alice.library("Island")
        bob.battlefield(ox, counters = listOf(pt to 2, CounterKind.Stun to 1))
        alice.casts(unwinding, target = ox)
        resolveAll()
        val choice = pending as Decision.ChooseOption
        assertEquals(alice.id, choice.player)
        assertEquals(setOf("+1/+1", "stun"), choice.options.toSet())
        answer(Answer.Option("stun"))
        resolveAll()
        expect {
            ox.counters(CounterKind.Stun) shouldBe 0
            ox.counters(pt) shouldBe 2
        }
    }

    // ---- The source or object of another object's trigger is that object (siblings of "Whenever a Dragon you control attacks, it deals")

    private val pyreBanner = Cards.invent("Sweepfen Pyre Banner", "{2}{R}", "Enchantment",
        "Whenever a creature you control enters, it deals damage equal to its power to any target.")
    private val lifelinkPup = Cards.invent("Sweepfen Lifelink Pup", "{1}{W}", "Creature — Dog", "Lifelink", "2/2")
    private val graveHerald = Cards.invent("Sweepfen Grave Herald", "{1}{W}", "Creature — Human Cleric",
        "Whenever another creature you control dies, you gain life equal to its toughness.", "1/1")
    private val lastRites = Cards.invent("Sweepfen Last Rites", "{1}{B}", "Enchantment",
        "Whenever a creature you control dies, it deals 1 damage to each opponent.")
    private val tallyKeeper = Cards.invent("Sweepfen Tally Keeper", "{1}{U}", "Enchantment",
        "Whenever a creature you control leaves the battlefield, if it had counters on it, draw a card.")

    // CR 120.3f, 702.15b: the entering creature deals the damage, so its lifelink gains the life
    @Test
    fun anEnteringCreatureIsTheDamageSource() = scenario {
        alice.battlefield(pyreBanner, "Plains", "Plains")
        alice.hand(lifelinkPup)
        alice.casts(lifelinkPup)
        resolveAll()
        answer(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    // CR 603.10a, 608.2h: the other creature's toughness as it last existed, its counters included
    @Test
    fun anotherDyingCreaturesLastToughnessIsGained() = scenario {
        alice.battlefield(graveHerald)
        alice.battlefield(pup, counters = listOf(pt to 2))
        bob.battlefield("Swamp")
        bob.hand(slaying)
        alice.passes()
        bob.casts(slaying, target = pup)
        resolveAll()
        resolveAll()
        expect { alice.life shouldBe 24 }
    }

    // CR 608.2h, 702.15b: the creature that died deals the damage, as it last existed: its lifelink gains the life; CR 102.2: each opponent
    @Test
    fun aDyingCreatureIsTheDamageSource() = scenario(players = 3) {
        alice.battlefield(lastRites, lifelinkPup)
        bob.battlefield("Swamp")
        bob.hand(slaying)
        alice.passes()
        bob.casts(slaying, target = lifelinkPup)
        resolveAll()
        resolveAll()
        expect {
            bob.life shouldBe 19
            carol.life shouldBe 19
            alice.life shouldBe 22
        }
    }

    // CR 603.10a, 603.4: "if it had counters on it" looks at the creature as it last existed on the battlefield
    @Test
    fun aLeavingCreaturesLastCountersAreChecked() = scenario {
        alice.battlefield(tallyKeeper)
        alice.battlefield(pup, counters = listOf(pt to 1))
        alice.library("Island")
        bob.battlefield("Island")
        bob.hand(recall)
        alice.passes()
        bob.casts(recall, target = pup)
        resolveAll()
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    // ---- A compound sentence keeps both halves, X of an activated ability is not a cast X (siblings of "deals X damage … and" and
    // Malady Invoker's "where X is this creature's power")

    private val sear = Cards.invent("Sweepfen Sear", "{R}", "Instant", "Sweepfen Sear deals 2 damage to any target and you gain 2 life.")
    private val hexer = Cards.invent("Sweepfen Hexer", "{1}{B}", "Creature — Human Warlock",
        "{T}: Target creature gets -X/-0 until end of turn, where X is this creature's power.", "1/1")

    // CR 608.2c: both halves of the sentence happen
    @Test
    fun damageAndLifeBothHappen() = scenario {
        alice.battlefield("Mountain")
        alice.hand(sear)
        alice.casts(sear, targets = listOf("bob"))
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    // CR 107.3a, 608.2h: X is the Hexer's power as the ability resolves (1 plus two counters), not an announced X
    @Test
    fun activatedXIsThisCreaturesPower() = scenario {
        alice.battlefield(hexer, counters = listOf(pt to 2))
        bob.battlefield(ox)
        alice.activates(hexer, target = ox)
        resolveAll()
        expect { ox.power shouldBe 0 }
    }

    // ---- An exile remembers its cards when the card refers to cards "exiled with" it (siblings of "exile target … from your graveyard")

    private fun keeper(name: String, exile: String) = Cards.invent(name, "{2}{B}", "Creature — Horror", """
        When this creature enters, $exile
        As long as there are cards exiled with this creature, it has flying.
    """.trimIndent(), "2/2")
    private val graveKeeper = keeper("Sweepfen Grave Keeper", "exile target card from a graveyard.")
    private val upToKeeper = keeper("Sweepfen Lean Keeper", "exile up to one target card from a graveyard.")
    private val wardKeeper = keeper("Sweepfen Ward Keeper", "exile target nonland permanent an opponent controls.")

    private fun exilesAndFlies(creature: String, target: String, fromGraveyard: Boolean) = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(creature)
        if (fromGraveyard) bob.graveyard(target, ox) else bob.battlefield(target, ox)
        alice.casts(creature)
        resolveAll()
        answer(Answer.Targets(listOf(TargetRef.Object(card(target)))))
        resolveAll()
        expect {
            bob.exile has target
            creature hasKeyword arcanum.api.Keyword.Flying.kind
        }
    }

    // CR 607.2a: the card exiled from a graveyard is one "exiled with" this creature
    @Test
    fun aCardExiledFromAGraveyardIsLinked() = exilesAndFlies(graveKeeper, pup, fromGraveyard = true)

    // CR 607.2a: likewise for "up to one target"
    @Test
    fun anUpToOneExiledCardIsLinked() = exilesAndFlies(upToKeeper, pup, fromGraveyard = true)

    // CR 607.2a: and for an exiled permanent
    @Test
    fun anExiledPermanentIsLinked() = exilesAndFlies(wardKeeper, trinket, fromGraveyard = false)
}
