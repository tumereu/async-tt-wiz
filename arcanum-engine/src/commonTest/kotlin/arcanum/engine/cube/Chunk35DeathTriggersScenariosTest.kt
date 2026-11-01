package arcanum.engine.cube

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

/** The cube's chunk 35 (abilities that trigger when creatures die), played as a player would. */
class Chunk35DeathTriggersScenariosTest {
    private val bear = Cards.invent("Mossback Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Hulking Mudogre", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val corpse = Cards.invent("Shambling Corpse", "{1}{B}", "Creature — Zombie", "", "2/2")
    private val idol = Cards.invent("Brass Idol", "{2}", "Artifact")
    private val dart = Cards.invent("Grave Dart", "{1}{B}", "Instant", "Destroy target creature.")
    private val shatter = Cards.invent("Shatter Shot", "{R}", "Instant", "Destroy target artifact.")

    private val p1p1 = CounterKind.PowerToughness(1, 1)

    /** Chooses targets when asked; a slot with only as many legal targets as it needs is filled without asking (P5). */
    private fun Scenario.targets(vararg names: String) {
        if (pending is Decision.Targets) answer(Answer.Targets(names.map { target(it) }))
    }

    private fun Scenario.yes() = answer(Answer.YesNo(true))

    /** Whenever this or another creature dies, target player loses 1 life and you gain 1 life. */
    @Test
    fun crimsonMourner() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain", "Mountain")
        alice.hand("Crimson Mourner", dart, "Storm Spark")
        bob.battlefield(bear)

        alice.casts("Crimson Mourner")
        allPass()
        alice.casts(dart, target = bear)
        allPass()
        // CR 603.2: another creature died, any player's
        targets("bob")
        allPass()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }

        // CR 603.10a: it sees its own death
        alice.casts("Storm Spark", target = "Crimson Mourner")
        allPass()
        targets("bob")
        allPass()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    /** Whenever this or another creature you control dies, each opponent loses 1 life and you gain 1 life. */
    @Test
    fun harborsideKnifer() = scenario {
        alice.battlefield("Swamp", "Swamp", "Mountain", "Mountain", "Harborside Knifer", ogre)
        alice.hand(dart, "Storm Spark")
        bob.battlefield(bear)

        // An opponent's creature dying doesn't trigger it
        alice.casts("Storm Spark", target = bear)
        allPass()
        expect {
            bob.graveyard has bear
            stack.isEmpty()
        }
        alice.casts(dart, target = ogre)
        allPass()
        expect { stack.names shouldBe listOf("ability of Harborside Knifer") }
        allPass()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    /** Whenever this or another creature or planeswalker you control dies, each opponent loses 1 and you gain 1. */
    @Test
    fun ghoulishReveler() = scenario {
        alice.battlefield("Swamp", "Swamp", "Mountain", "Mountain", "Ghoulish Reveler", bear)
        alice.battlefield("Renji Kagehara", counters = listOf(CounterKind.Loyalty to 3))
        alice.hand(dart, "Storm Spark")

        alice.casts(dart, target = bear)
        allPass()
        allPass()
        // CR 704.5i: a planeswalker with no loyalty is put into the graveyard; "dies" (CR 700.4)
        alice.casts("Storm Spark", target = "Renji Kagehara")
        allPass()
        expect { alice.graveyard has "Renji Kagehara" }
        allPass()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    /** Whenever this or another Zombie you control dies, you draw a card and lose 1 life. */
    @Test
    fun undeadSoothsayer() = scenario {
        alice.battlefield("Swamp", "Swamp", "Mountain", "Mountain", "Undead Soothsayer", corpse, bear)
        alice.hand(dart, "Storm Spark")
        alice.library("Island", "Forest")

        alice.casts("Storm Spark", target = bear)
        allPass()
        expect { stack.isEmpty() }
        alice.casts(dart, target = corpse)
        allPass()
        allPass()
        expect {
            alice.hand has "Island"
            alice.life shouldBe 19
        }
    }

    /** Whenever a nontoken creature you control dies, it deals 1 damage to you and you draw a card. */
    @Test
    fun duskHarvester() = scenario {
        alice.battlefield("Swamp", "Swamp", "Dusk Harvester", bear)
        alice.hand(dart)
        alice.library("Island")

        alice.casts(dart, target = bear)
        allPass()
        allPass()
        expect {
            alice.life shouldBe 19
            alice.hand has "Island"
        }
    }

    /** Whenever this or another nontoken Zombie you control dies, create a 2/2 black Zombie token. */
    @Test
    fun necklessHorseman() = scenario {
        alice.battlefield("Swamp", "Swamp", "Mountain", "Mountain", "Neckless Horseman", corpse)
        alice.hand(dart, "Storm Spark")

        alice.casts(dart, target = corpse)
        allPass()
        allPass()
        expect {
            alice.battlefield has "Zombie Token"
            "Zombie Token".power shouldBe 2
            "Zombie Token".colors shouldBe listOf(arcanum.api.Color.Black)
        }

        // The token dying doesn't trigger it
        alice.casts("Storm Spark", target = "Zombie Token")
        allPass()
        expect {
            stack.isEmpty()
            alice.battlefield lacks "Zombie Token"
        }
    }

    /** Power equal to devotion to red; a nontoken creature of yours dying makes a 1/1 Satyr that can't block, two for
     * power 4 or greater. */
    @Test
    fun pyrrhusTemperedInAsh() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Swamp", "Swamp", bear, ogre)
        alice.hand("Pyrrhus, Tempered in Ash", dart, "Storm Spark")

        alice.casts("Pyrrhus, Tempered in Ash")
        allPass()
        // CR 700.5: {1}{R}{R} and the Ogre's {R}
        expect { "Pyrrhus, Tempered in Ash".power shouldBe 3 }

        alice.casts("Storm Spark", target = bear)
        allPass()
        allPass()
        expect {
            alice.battlefield.names.count { it == "Satyr Token" } shouldBe 1
            "Satyr Token" hasMechanic { it is arcanum.api.Mechanic.CantBlock }
        }

        alice.casts(dart, target = ogre)
        allPass()
        allPass()
        expect {
            alice.battlefield.names.count { it == "Satyr Token" } shouldBe 3
            "Pyrrhus, Tempered in Ash".power shouldBe 2
        }
    }

    /** Enters with X +1/+1 counters; {1}, {T}: another counter; dies: a 1/1 flying Thopter for each counter. */
    @Test
    fun hangarStrider() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Swamp", "Swamp")
        alice.hand("Hangar Strider", dart)

        alice.casts("Hangar Strider", x = 2)
        allPass()
        expect {
            "Hangar Strider".counters(p1p1) shouldBe 2
            "Hangar Strider".power shouldBe 2
        }

        alice.casts(dart, target = "Hangar Strider")
        allPass()
        allPass()
        expect {
            alice.battlefield.names.count { it == "Thopter Token" } shouldBe 2
            "Thopter Token" hasKeyword Keyword.Flying
            "Thopter Token".types shouldBe listOf(arcanum.api.CardType.Artifact, arcanum.api.CardType.Creature)
        }
    }

    @Test
    fun hangarStriderGrows() = scenario {
        alice.battlefield("Island")
        alice.battlefield("Hangar Strider", counters = listOf(p1p1 to 1))

        alice.activates("Hangar Strider")
        allPass()
        expect {
            "Hangar Strider".counters(p1p1) shouldBe 2
            "Hangar Strider".tapped shouldBe true
        }
    }

    /** Dies: create X tapped 1/1 green Squirrels, X its power. */
    @Test
    fun burrowedShambler() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand("Burrowed Shambler", dart)

        alice.casts("Burrowed Shambler")
        allPass()
        alice.casts(dart, target = "Burrowed Shambler")
        allPass()
        allPass()
        expect {
            alice.battlefield has "Squirrel Token"
            "Squirrel Token".tapped shouldBe true
            alice.battlefield.names.count { it == "Squirrel Token" } shouldBe 1
        }
    }

    /** Sacrifice an artifact: a +1/+1 counter; modular 1: enters with one, and dying moves them to an artifact creature. */
    @Test
    fun scrapfedDevourer() = scenario {
        alice.battlefield("Island", "Island", "Swamp", "Swamp", "Volatile Myr", idol)
        alice.hand("Scrapfed Devourer", dart)

        alice.casts("Scrapfed Devourer")
        allPass()
        expect { "Scrapfed Devourer".counters(p1p1) shouldBe 1 }

        alice.activates("Scrapfed Devourer", choosing = listOf(idol))
        allPass()
        expect {
            alice.graveyard has idol
            "Scrapfed Devourer".counters(p1p1) shouldBe 2
        }

        // CR 702.43a: modular
        alice.casts(dart, target = "Scrapfed Devourer")
        allPass()
        targets("Volatile Myr")
        allPass()
        yes()
        expect {
            "Volatile Myr".counters(p1p1) shouldBe 2
            "Volatile Myr".power shouldBe 3
        }
    }

    /** Enters with X counters; {4}: another; dies: draw a card for each. */
    @Test
    fun bazaarbackStrider() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Swamp", "Swamp")
        alice.hand("Bazaarback Strider", dart)
        alice.library("Island", "Forest", "Plains")

        alice.casts("Bazaarback Strider", x = 1)
        allPass()
        alice.activates("Bazaarback Strider")
        allPass()
        expect { "Bazaarback Strider".counters(p1p1) shouldBe 2 }

        alice.casts(dart, target = "Bazaarback Strider")
        allPass()
        allPass()
        expect { alice.hand.size shouldBe 2 }
    }

    /** Whenever you sacrifice a permanent, a +1/+1 counter on Velo; dies: damage equal to its power to any target. */
    @Test
    fun veloMasterOfCeremonies() = scenario {
        alice.battlefield("Swamp", "Swamp", "Velo, Master of Ceremonies", "Goblin Volley Pit", bear)
        alice.hand(dart)

        alice.activates("Goblin Volley Pit", target = "bob", choosing = listOf(bear))
        allPass()
        allPass()
        expect {
            "Velo, Master of Ceremonies".counters(p1p1) shouldBe 1
            bob.life shouldBe 19
        }

        alice.casts(dart, target = "Velo, Master of Ceremonies")
        allPass()
        targets("bob")
        allPass()
        // CR 608.2h: its power as it last existed
        expect { bob.life shouldBe 17 }
    }

    /** Dies: 2 damage to any target. */
    @Test
    fun volatileMyr() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand("Volatile Myr", dart)

        alice.casts("Volatile Myr")
        allPass()
        alice.casts(dart, target = "Volatile Myr")
        allPass()
        targets("bob")
        allPass()
        expect { bob.life shouldBe 18 }
    }

    /** Flying; dies: return another artifact card from your graveyard to your hand. */
    @Test
    fun salvageGull() = scenario {
        returnsAnArtifact("Salvage Gull")
        expect { "Salvage Gull".zone shouldBe arcanum.api.Zone.Graveyard }
    }

    /** Dies: return another artifact card from your graveyard to your hand. */
    @Test
    fun myrGleaner() = scenario { returnsAnArtifact("Myr Gleaner") }

    /** Dies: return another artifact card from your graveyard to your hand. */
    @Test
    fun foundryApprentice() = scenario { returnsAnArtifact("Foundry Apprentice") }

    private fun Scenario.returnsAnArtifact(name: String) {
        alice.battlefield("Island", "Island", "Island", "Swamp", "Swamp", "Swamp")
        alice.hand(name, dart)
        alice.graveyard(idol)

        alice.casts(name)
        allPass()
        alice.casts(dart, target = name)
        allPass()
        // CR 115.1: "another": itself (an artifact card in the graveyard now) isn't a choice, so Brass Idol is chosen
        allPass()
        expect {
            alice.hand has idol
            alice.graveyard has name
        }
    }

    /** It dying, or another artifact of yours going to the graveyard, returns an artifact card with lesser mana value. */
    @Test
    fun scrapDredger() = scenario {
        alice.battlefield("Mountain", "Swamp", "Swamp", "Scrap Dredger", idol)
        alice.hand(shatter, dart)
        alice.graveyard("Lotus Frond")

        alice.casts(shatter, target = idol)
        allPass()
        allPass()
        expect { alice.hand has "Lotus Frond" }

        // Brass Idol (2) is less than its own mana value 3
        alice.casts(dart, target = "Scrap Dredger")
        allPass()
        allPass()
        expect { alice.hand has idol }
    }

    /** Menace; dies: pay {B} to return it to hand; pay {X} to return another Zombie card with mana value X. */
    @Test
    fun unyieldingDead() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand("Unyielding Dead", dart)
        alice.graveyard(corpse)

        alice.casts("Unyielding Dead")
        allPass()
        expect { "Unyielding Dead" hasKeyword Keyword.Menace }

        alice.casts(dart, target = "Unyielding Dead")
        allPass()
        // Two triggers of one source: Alice orders them, the return-to-hand one first on the Stack; the Corpse is the
        // only other Zombie card, so it is targeted without asking
        answer(Answer.Order(listOf(0, 1)))
        expect { stack.size shouldBe 2 }
        allPass()
        // The top one returns the Corpse for {X}, X its mana value; mana on resolution is paid by hand (D61)
        yes()
        assertEquals("Pay {X} (X = 2)", decision<Decision.Manual>().text)
        answer(Answer.Done)
        expect { alice.battlefield has corpse }
        allPass()
        yes()
        assertEquals("Pay {B}", decision<Decision.Manual>().text)
        answer(Answer.Done)
        expect { alice.hand has "Unyielding Dead" }
    }

    /** Not paying, it stays in the graveyard. */
    @Test
    fun unyieldingDeadUnpaid() = scenario {
        alice.battlefield("Swamp", "Swamp", "Unyielding Dead")
        alice.hand(dart)

        alice.casts(dart, target = "Unyielding Dead")
        allPass()
        answer(Answer.Order(listOf(0, 1)))
        // Without another Zombie card the second ability has no target and is removed (CR 603.3d)
        expect { stack.size shouldBe 1 }
        allPass()
        answer(Answer.YesNo(false))
        expect { alice.graveyard has "Unyielding Dead" }
    }

    /** Modular 2; {T}, sacrifice an artifact: search for a Construct card. */
    @Test
    fun junkyardReassembler() = scenario {
        alice.battlefield("Junkyard Reassembler", counters = listOf(p1p1 to 2))
        alice.battlefield(idol)
        alice.library("Island", "Hangar Strider", "Forest")

        alice.activates("Junkyard Reassembler", choosing = listOf(idol))
        allPass()
        alice.chooses("Hangar Strider")
        expect {
            alice.hand has "Hangar Strider"
            alice.graveyard has idol
            alice.library.size shouldBe 2
        }
    }

    /** Cast, it enters with two +1/+1 counters; dying moves them to another artifact creature. */
    @Test
    fun junkyardReassemblerModular() = scenario {
        alice.battlefield("Island", "Island", "Island", "Swamp", "Swamp", "Volatile Myr")
        alice.hand("Junkyard Reassembler", dart)

        alice.casts("Junkyard Reassembler")
        allPass()
        expect { "Junkyard Reassembler".power shouldBe 2 }
        alice.casts(dart, target = "Junkyard Reassembler")
        allPass()
        allPass()
        yes()
        expect { "Volatile Myr".counters(p1p1) shouldBe 2 }
    }

    /** Dies: each player may search for up to two basic lands and put them onto the battlefield. */
    @Test
    fun seasonedPathfinder() = scenario {
        alice.library("Forest", "Island", "Hangar Strider")
        alice.battlefield("Forest", "Swamp", "Swamp")
        alice.hand("Seasoned Pathfinder", dart)
        bob.library("Plains", "Mountain")

        alice.casts("Seasoned Pathfinder")
        allPass()
        alice.casts(dart, target = "Seasoned Pathfinder")
        allPass()
        allPass()
        // Each decides in turn order (CR 101.4): Alice searches, Bob declines; then the lands enter together
        yes()
        answer(Answer.YesNo(false))
        alice.chooses("Forest", "Island")
        expect {
            alice.battlefield.names.count { it == "Forest" } shouldBe 2
            alice.battlefield has "Island"
            alice.library.names shouldBe listOf("Hangar Strider")
            bob.library.size shouldBe 2
        }
    }
}
