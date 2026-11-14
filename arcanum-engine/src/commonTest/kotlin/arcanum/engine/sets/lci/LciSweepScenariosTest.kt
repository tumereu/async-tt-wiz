package arcanum.engine.sets.lci

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** LCI sweep: siblings of the bugs LCI set work fixed, played with invented cards carrying real oracle wordings. */
class LciSweepScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val squire = Cards.invent("Sweep Bramble Squire", "{G}", "Creature — Human", "", "1/1")
    private val hauler = Cards.invent("Sweep Ridge Hauler", "{3}{G}", "Creature — Beast", "", "4/4")
    private val trinket = Cards.invent("Sweep Brass Trinket", "{1}", "Artifact", "")
    private val jolt = Cards.invent("Sweep Quick Jolt", "{R}", "Instant", "Sweep Quick Jolt deals 1 damage to any target.")
    private val edict = Cards.invent("Sweep Gloom Edict", "{B}", "Instant", "Destroy target creature.")

    // ---- "permanent spell" / "permanent card" read as the battlefield category (lci chunks 01, 13)

    private val watcher = Cards.invent("Sweep Spire Watcher", "{1}{U}", "Creature — Human Wizard",
        "Whenever an opponent casts a permanent spell, draw a card.", "1/2")
    private val tinkerer = Cards.invent("Sweep Gear Tinkerer", "{1}{U}", "Creature — Human Artificer",
        "Whenever you cast a noncreature permanent spell, this creature gets +1/+1 until end of turn.", "1/1")
    private val denial = Cards.invent("Sweep Stone Denial", "{1}{U}", "Instant", "Counter target noncreature permanent spell.")
    private val delver = Cards.invent("Sweep Grave Delver", "{2}{G}", "Creature — Human",
        "When this creature enters, mill three cards. Then return a permanent card from your graveyard to your hand.", "2/2")

    // CR 110.4b: a creature spell is a permanent spell, so the opponent's cast triggers it; an instant isn't one
    @Test
    fun opponentsPermanentSpellTriggersTheWatcher() = scenario(players = 3) {
        alice.battlefield(watcher)
        alice.library("Island", "Island")
        bob.battlefield("Forest", "Mountain")
        bob.hand(squire, jolt)
        turn(number = 2, active = bob)
        bob.casts(squire)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
        bob.casts(jolt, target = "carol")
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 110.4b: an artifact spell is a noncreature permanent spell
    @Test
    fun noncreaturePermanentSpellTriggersTheTinkerer() = scenario {
        alice.battlefield(tinkerer, "Island")
        alice.hand(trinket)
        alice.casts(trinket)
        resolveAll()
        expect { tinkerer.power shouldBe 2 }
    }

    // CR 110.4b, 701.6a: the artifact spell is a legal target and is countered
    @Test
    fun denialCountersAnArtifactSpell() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(trinket, denial)
        alice.casts(trinket)
        alice.casts(denial, target = trinket)
        resolveAll()
        expect { alice.graveyard has trinket }
    }

    // CR 110.4a: a creature card in the graveyard is a permanent card; an instant card isn't
    @Test
    fun delverReturnsAPermanentCardFromTheGraveyard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(delver)
        alice.library(jolt, hauler, "Swamp")
        alice.casts(delver)
        resolveAll()
        val options = decision<Decision.ChooseObjects>().options
        assertTrue(options.size == 2, "the creature and land cards, not the instant: $options")
        alice.chooses(hauler)
        resolveAll()
        expect { alice.hand has hauler }
    }

    // ---- Information from an object that left: last known information (lci fixes: X of a sacrificed or died creature)

    private val banisher = Cards.invent("Sweep Dawn Banishing", "{W}", "Instant", "Exile target creature you control. You gain life equal to its toughness.")
    private val reckoning = Cards.invent("Sweep Fair Reckoning", "{W}", "Instant", "Destroy target creature. Its controller gains life equal to its power.")
    private val altar = Cards.invent("Sweep Bone Altar", "{2}", "Artifact", "Sacrifice another creature: You gain life equal to the sacrificed creature's toughness.")
    private val ember = Cards.invent("Sweep Ember Hound", "{2}{R}", "Creature — Dog", "When this creature dies, it deals damage equal to its power to any target.", "2/2")

    // CR 608.2h: the creature left the battlefield, so its toughness is read as it last existed there (1 + 2 counters), not the exiled card's.
    @Test
    fun banishingReadsTheExiledCreatureAsItLastExisted() = scenario {
        alice.battlefield("Plains")
        alice.battlefield(squire, counters = listOf(p1p1 to 2))
        alice.hand(banisher)
        alice.casts(banisher, target = squire)
        resolveAll()
        expect {
            alice.exile has squire
            alice.life shouldBe 23
        }
    }

    private val condemnation = Cards.invent("Sweep Stern Condemnation", "{W}", "Instant", "Destroy target creature. You gain life equal to its toughness.")

    // CR 608.2h: also after destroying it: the creature as it last existed on the battlefield (1 + 2 counters), not the card in the graveyard
    @Test
    fun condemnationReadsTheDestroyedCreatureAsItLastExisted() = scenario(players = 3) {
        alice.battlefield("Plains")
        alice.hand(condemnation)
        carol.battlefield(squire, counters = listOf(p1p1 to 2))
        alice.casts(condemnation, target = squire)
        resolveAll()
        expect {
            carol.graveyard has squire
            alice.life shouldBe 23
            carol.life shouldBe 20
        }
    }

    private val tithe = Cards.invent("Sweep Carrion Tithe", "{1}{B}", "Enchantment", "Whenever a creature you control dies, you gain life equal to its power.")
    private val grove = Cards.invent("Sweep Welcoming Grove", "{1}{G}", "Enchantment", "Whenever another creature you control enters, you gain life equal to its power.")

    // CR 603.10a, 608.2h: "its power" is the creature that died, as it last existed (1 + 2 counters), not the enchantment's
    @Test
    fun titheGainsTheDeadCreaturesLastPower() = scenario(players = 3) {
        alice.battlefield(tithe, "Swamp")
        alice.battlefield(squire, counters = listOf(p1p1 to 2))
        alice.hand(edict)
        alice.casts(edict, target = squire)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    // CR 603.2, 603.10a: "its power" is the creature that entered
    @Test
    fun groveGainsTheEnteringCreaturesPower() = scenario {
        alice.battlefield(grove, "Forest", "Forest", "Forest", "Forest")
        alice.hand(hauler)
        alice.casts(hauler)
        resolveAll()
        expect { alice.life shouldBe 24 }
    }

    // CR 608.2h: the destroyed creature's power and controller as it last existed (2 + 1 counter), in a three-player game
    @Test
    fun reckoningGivesTheControllerTheDestroyedCreaturesPower() = scenario(players = 3) {
        alice.battlefield("Plains")
        alice.hand(reckoning)
        carol.battlefield("Sweep Ember Hound", counters = listOf(p1p1 to 1))
        alice.casts(reckoning, target = ember)
        resolveAll()
        expect {
            carol.graveyard has ember
            carol.life shouldBe 23
            bob.life shouldBe 20
        }
    }

    // CR 601.2h, 608.2h: the creature sacrificed as a cost is read as it last existed (1 + 2 counters)
    @Test
    fun altarReadsTheSacrificedCreaturesToughness() = scenario {
        alice.battlefield(altar)
        alice.battlefield(squire, counters = listOf(p1p1 to 2))
        alice.activates(altar, choosing = listOf(squire))
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    // CR 603.10a, 608.2h: the dies trigger reads its source's power as it last existed (2 + 1 counter)
    @Test
    fun emberHoundDealsItsLastPower() = scenario {
        alice.battlefield("Swamp")
        alice.battlefield(ember, counters = listOf(p1p1 to 1))
        alice.hand(edict)
        alice.casts(edict, target = ember)
        resolveAll()
        alice.answers(arcanum.engine.decision.Answer.Targets(listOf(target("bob"))))
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    // ---- One object pays one cost part (lci fixes: craft materials, CR 601.2h)

    private val drummer = Cards.invent("Sweep Hollow Drummer", "{1}{G}", "Creature — Elf", "{T}, Tap an untapped creature you control: Draw a card.", "1/1")
    private val scrapper = Cards.invent("Sweep Scrap Offerer", "{1}{B}", "Creature — Human", "Sacrifice an artifact, Sacrifice a creature: Draw a card.", "1/1")
    private val golem = Cards.invent("Sweep Brass Golem", "{3}", "Artifact Creature — Golem", "", "2/2")

    // CR 601.2h, 602.2b: the source tapped for {T} can't also be the untapped creature tapped for the other part
    @Test
    fun drummerAloneCantPayBothTaps() = scenario {
        alice.battlefield(drummer)
        alice.library("Forest")
        assertFailsWith<IllegalArgumentException> { alice.activates(drummer) }
    }

    // CR 601.2h: with another untapped creature it can
    @Test
    fun drummerTapsAnotherCreature() = scenario {
        alice.battlefield(drummer, squire)
        alice.library("Forest")
        alice.activates(drummer, choosing = listOf(squire))
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            squire.tapped shouldBe true
        }
    }

    // CR 601.2h: an artifact creature can't be sacrificed for both the artifact and the creature part (the offerer itself is a creature,
    // so with the golem alone there is still one of each)
    @Test
    fun anArtifactCreatureIsOnlyOneOfTheSacrifices() = scenario {
        alice.battlefield(scrapper, golem)
        alice.library("Swamp")
        alice.activates(scrapper, choosing = listOf(golem, scrapper))
        resolveAll()
        expect {
            alice.graveyard has golem
            alice.graveyard has scrapper
            alice.hand.size shouldBe 1
        }
    }

    // ---- A damage result through the action that applies replacements (lci chunk 09: damage to a player loses life through life-loss
    // replacements)

    private val plaguefang = Cards.invent("Sweep Plague Fang", "{1}{B}", "Creature — Phyrexian Rat", "Infect", "2/2")
    private val multiplier = Cards.invent("Sweep Brood Multiplier", "{3}{G}{G}", "Creature — Phyrexian Beast",
        "If you would put one or more counters on a permanent or player, put twice that many of each of those kinds of counters on that permanent or player instead.", "4/4")

    // CR 120.3b, 614.1a: infect damage makes its controller give the player poison counters, so a replacement doubling the counters you'd
    // put on a player applies (2 damage, 4 poison counters)
    @Test
    fun infectPoisonCountersAreDoubled() = scenario {
        alice.battlefield(plaguefang, multiplier)
        turn(number = 1, active = alice, step = TurnStep.BeginningOfCombat)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(plaguefang, defender = "bob")
        advanceTo(TurnStep.DeclareBlockers)
        if (state.priority.holder == bob.id) bob.blocks()
        advanceTo(TurnStep.CombatDamage)
        expect {
            bob.counters(CounterKind.Poison) shouldBe 4
            bob.life shouldBe 20
        }
    }

    private val stinger = Cards.invent("Sweep Venom Stinger", "{1}{B}", "Creature — Phyrexian Insect", "Toxic 2", "1/1")
    private val witherling = Cards.invent("Sweep Wilting Shade", "{2}{B}", "Creature — Shade", "Wither", "1/3")

    // CR 702.164c, 614.1a: toxic's poison counters are given by the creature's controller, so they double too (2 to 4)
    @Test
    fun toxicPoisonCountersAreDoubled() = scenario {
        alice.battlefield(stinger, multiplier)
        turn(number = 1, active = alice, step = TurnStep.BeginningOfCombat)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(stinger, defender = "bob")
        advanceTo(TurnStep.DeclareBlockers)
        if (state.priority.holder == bob.id) bob.blocks()
        advanceTo(TurnStep.CombatDamage)
        expect {
            bob.counters(CounterKind.Poison) shouldBe 4
            bob.life shouldBe 19
        }
    }

    // CR 120.3d, 614.1a: a blocker with wither puts its -1/-1 counters as its own controller's, so the defending player's doubler applies
    // (1 damage, 2 counters) during another player's turn, with three players
    @Test
    fun blockingWitherCountersAreTheBlockersControllers() = scenario(players = 3) {
        alice.battlefield(hauler)
        bob.battlefield(witherling, multiplier)
        turn(number = 1, active = alice, step = TurnStep.BeginningOfCombat)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hauler, defender = "bob")
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(witherling to hauler)
        advanceTo(TurnStep.CombatDamage)
        expect { hauler.counters(CounterKind.PowerToughness(-1, -1)) shouldBe 2 }
    }

    // ---- A replacement modifies only the parts of an event it names (lci chunk 05: token multipliers)

    private val brand = Cards.invent("Sweep Cinder Brand", "{2}{R}", "Creature — Elemental",
        "If a source you control would deal damage to an opponent or a permanent an opponent controls, it deals that much damage plus 1 instead.", "2/2")
    private val quake = Cards.invent("Sweep Rumbling Quake", "{1}{R}", "Creature — Elemental", "When this creature enters, it deals 1 damage to each creature and each player.", "1/3")

    // CR 614.1a, 616.1: one event damages every creature and player; only the parts dealt to an opponent or an opponent's permanent get 1 more
    @Test
    fun brandAddsOnlyToOpponentsAndTheirPermanents() = scenario(players = 3) {
        alice.battlefield(brand, "Mountain", "Mountain")
        alice.hand(quake)
        bob.battlefield(golem)
        carol.battlefield(hauler)
        alice.casts(quake)
        resolveAll()
        expect {
            alice.life shouldBe 19
            bob.life shouldBe 18
            carol.life shouldBe 18
            alice.battlefield has brand
            bob.graveyard has golem
            card(brand).view.obj!!.damage shouldBe 1
            card(hauler).view.obj!!.damage shouldBe 2
        }
    }
}
