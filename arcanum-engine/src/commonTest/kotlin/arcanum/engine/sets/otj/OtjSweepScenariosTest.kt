package arcanum.engine.sets.otj

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.resolveAll
import arcanum.engine.sets.ltr.tokens
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertTrue

/** OTJ sweep: siblings of the set's engine and parser fixes, probed in play (each a regression test once it passes). */
class OtjSweepScenariosTest {
    private val gunsmoke = Cards.invent("Gunsmoke Creed", "{2}{R}", "Enchantment",
        "Whenever a creature you control attacks, it deals damage equal to its power to any target.")
    private val bandolier = Cards.invent("Ricochet Bandolier", "{2}", "Artifact — Equipment",
        "Whenever equipped creature attacks, it deals damage equal to its power to target creature defending player controls.\nEquip {1}")
    private val volley = Cards.invent("Welcome Volley", "{1}{R}", "Enchantment", "Whenever another creature you control enters, it deals 1 damage to any target.")
    private val boothill = Cards.invent("Boothill Reckoning", "{2}{B}", "Enchantment",
        "Whenever another creature you control dies, draw X cards, where X is its power.")
    private val covenant = Cards.invent("Sapling Covenant", "{1}{G}", "Enchantment",
        "Whenever a creature you control enters, draw a card if its power is 3 or greater. Otherwise, put two +1/+1 counters on it.")
    private val tally = Cards.invent("Thunderhoof Tally", "{1}{G}", "Enchantment",
        "Whenever another creature you control enters, if its power is 3 or greater, you gain 3 life.")
    private val ledger = Cards.invent("Vanishing Ledger", "{1}{W}", "Enchantment",
        "Whenever a creature is exiled from the battlefield, you gain life equal to its power.")
    private val prideleader = Cards.invent("Prideleader of the Mesa", "{1}{G}", "Creature — Cat Warrior",
        "Whenever another Cat you control attacks, it gets +X/+X until end of turn, where X is its power.", "1/1")
    private val tabby = Cards.invent("Badlands Tabby", "{2}{G}", "Creature — Cat", "", "3/3")
    private val ox = Cards.invent("Dustbowl Ox", "{2}{W}", "Creature — Ox", "Lifelink", "3/3")
    private val steer = Cards.invent("Ironclad Steer", "{1}{G}", "Creature — Ox", "", "2/2")
    private val sharpshooter = Cards.invent("Canyon Sharpshooter", "{3}{G}", "Creature — Human Archer", "", "4/4")
    private val noose = Cards.invent("Rustler's Noose", "{1}{B}", "Instant", "Destroy target creature.")
    private val ruse = Cards.invent("Faro Dealer's Ruse", "{1}{W}", "Instant", "Exile target creature.")
    private val bounty = Cards.invent("Lawbreaker's Bounty", "{1}{B}", "Enchantment", "Whenever you commit a crime, you gain 1 life.")
    private val snuff = Cards.invent("Snuff the Fuse", "{U}{U}", "Instant", "Counter target spell.")
    private val recruiter = Cards.invent("Posse Recruiter", "{1}{W}", "Enchantment", "Whenever one or more tokens you control enter, you gain 1 life.")
    private val muster = Cards.invent("Trailhand Muster", "{2}{W}", "Sorcery", "Create two 1/1 white Soldier creature tokens.")
    private val mirrorband = Cards.invent("Mirrorband Echo", "{2}{G}", "Sorcery", "For each token you control, create a token that's a copy of that permanent.")
    private val altar = Cards.invent("Charmwright Altar", "{1}{W}", "Enchantment", "Whenever an Aura you control becomes attached to a creature, you gain 2 life.")
    private val recall = Cards.invent("Hexrecall Rite", "{1}{W}", "Sorcery",
        "Return target Aura card from your graveyard to the battlefield attached to a creature you control.")
    private val blessing = Cards.invent("Mesa Blessing", "{W}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets +1/+1.")

    // CR 608.2c, 702.15b: "Whenever a creature you control attacks, it deals damage equal to its power": "it" and "its" are the attacking
    // creature (the enchantment never names itself), so the damage is its power and its lifelink applies
    @Test
    fun attackingCreatureDealsDamageEqualToItsPower() = scenario {
        alice.battlefield(gunsmoke, ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox)
        assertTrue(pending is Decision.Targets, pending.toString())
        alice.answers(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    // CR 608.2c, 702.15b: "Whenever equipped creature attacks, it deals damage equal to its power …": the equipped creature deals it (its
    // lifelink applies), not the Equipment
    @Test
    fun equippedAttackerDealsTheDamage() = scenario {
        alice.battlefield(bandolier, ox, "Plains")
        bob.battlefield(sharpshooter)
        alice.activates(bandolier, target = ox)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox)
        // the only legal target is chosen for alice
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(TargetRef.Object(card(sharpshooter)))))
        resolveAll()
        expect {
            sharpshooter.view.obj?.damage shouldBe 3
            alice.life shouldBe 23
        }
    }

    // CR 608.2c, 702.15b: "Whenever another creature you control enters, it deals 1 damage to any target": the creature that entered
    @Test
    fun enteringCreatureDealsTheDamage() = scenario {
        alice.battlefield(volley, "Plains", "Plains", "Plains")
        alice.hand(ox)
        alice.casts(ox)
        resolveAll()
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    // CR 603.10a, 608.2c: "Whenever another creature you control dies, draw X cards, where X is its power": the creature that died, as it
    // last existed, not the enchantment
    @Test
    fun drawsCardsEqualToTheDeadCreaturesPower() = scenario {
        alice.battlefield(boothill, sharpshooter, "Swamp", "Swamp")
        alice.hand(noose)
        alice.library("Island", "Island", "Island", "Island", "Island")
        alice.casts(noose, target = sharpshooter)
        resolveAll()
        expect { alice.hand.names.size shouldBe 4 }
    }

    // CR 608.2c: "draw a card if its power is 3 or greater. Otherwise, put two +1/+1 counters on it": both are the creature that entered
    @Test
    fun theEnteringCreaturesPowerDecides() = scenario {
        alice.battlefield(covenant, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(tabby, steer)
        alice.library("Island", "Island")
        alice.casts(tabby)
        resolveAll()
        expect { alice.hand.names.size shouldBe 2 }
        alice.casts(steer)
        resolveAll()
        expect {
            alice.hand.names.size shouldBe 1
            steer.power shouldBe 4
        }
    }

    // CR 603.4, 608.2c: "Whenever another creature you control enters, if its power is 3 or greater": the entering creature's power
    @Test
    fun interveningIfTestsTheEnteringCreaturesPower() = scenario {
        alice.battlefield(tally, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(tabby, steer)
        alice.casts(tabby)
        resolveAll()
        expect { alice.life shouldBe 23 }
        alice.casts(steer)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    // CR 603.10a, 608.2c: "Whenever a creature is exiled from the battlefield, you gain life equal to its power": the exiled creature's power
    // as it last existed on the battlefield
    @Test
    fun gainsLifeEqualToTheExiledCreaturesPower() = scenario {
        alice.battlefield(ledger, "Plains", "Plains")
        bob.battlefield(sharpshooter)
        alice.hand(ruse)
        alice.casts(ruse, target = sharpshooter)
        resolveAll()
        expect { alice.life shouldBe 24 }
    }

    // CR 608.2c, 608.2h: "Whenever another Cat you control attacks, it gets +X/+X until end of turn, where X is its power": the attacking
    // Cat's power as the ability resolves, not this creature's
    @Test
    fun attackingCatGetsItsOwnPower() = scenario {
        alice.battlefield(prideleader, tabby)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(tabby)
        resolveAll()
        expect {
            tabby.power shouldBe 6
            tabby.toughness shouldBe 6
            prideleader.power shouldBe 1
        }
    }
    // CR 700.13: casting a spell that targets a spell an opponent controls is a crime (the target on the Stack, with three players)
    @Test
    fun counteringAnOpponentsSpellIsACrime() = scenario(players = 3) {
        alice.battlefield(bounty, "Island", "Island")
        alice.hand(snuff)
        carol.battlefield("Forest", "Forest", "Forest")
        carol.hand(tabby)
        turn(number = 3, active = carol)
        carol.casts(tabby)
        carol.passes()
        alice.casts(snuff, target = tabby)
        resolveAll()
        expect {
            alice.life shouldBe 21
            carol.graveyard has tabby
        }
    }

    // CR 608.2f, 603.2c: "for each token you control, create a token that's a copy of that permanent" creates the copies at once: "one or
    // more tokens you control enter" triggers once
    @Test
    fun copiesOfEachTokenEnterTogether() = scenario {
        alice.battlefield(recruiter, "Plains", "Plains", "Plains", "Forest", "Forest", "Forest")
        alice.hand(muster, mirrorband)
        alice.casts(muster)
        resolveAll()
        alice.casts(mirrorband)
        resolveAll()
        kotlin.test.assertEquals(4, tokens("Soldier Token").size)
        expect { alice.life shouldBe 22 }
    }

    // CR 303.4a, 303.4f, 603.2e: an Aura card returned to the battlefield attached to a creature becomes attached to it as it enters
    @Test
    fun auraReturnedAttachedBecomesAttached() = scenario {
        alice.battlefield(altar, steer, "Plains", "Plains")
        alice.graveyard(blessing)
        alice.hand(recall)
        alice.casts(recall, target = blessing)
        resolveAll()
        expect {
            alice.life shouldBe 22
            steer.power shouldBe 3
        }
    }
}
