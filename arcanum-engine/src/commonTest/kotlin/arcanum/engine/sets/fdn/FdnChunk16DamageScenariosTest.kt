package arcanum.engine.sets.fdn

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Chunk 16 played: the invented names carry the oracle text of Abrade, Bite Down, Boltwave, Boros Charm, Deadly Riposte, Demonic Pact, Elspeth's Smite, Fanatical Firebrand, Firespitter Whelp, Impact Tremors, Incinerating Blast, Joust Through, Mindsparker, Ravenous Giant, Skeleton Archer, Slagstorm, Viashino Pyromancer and Volley Veteran. */
class FdnChunk16DamageScenariosTest {
    private val abrade = Cards.invent("Scorch and Smash", "{1}{R}", "Instant", """
        Choose one —
        • Scorch and Smash deals 3 damage to target creature.
        • Destroy target artifact.
    """)
    private val gnash = Cards.invent("Gnash Down", "{1}{G}", "Instant",
        "Target creature you control deals damage equal to its power to target creature or planeswalker you don't control.")
    private val boltsurge = Cards.invent("Boltsurge", "{R}", "Sorcery", "Boltsurge deals 3 damage to each opponent.")
    private val charm = Cards.invent("Scarlet Charm", "{R}{W}", "Instant", """
        Choose one —
        • Scarlet Charm deals 4 damage to target player or planeswalker.
        • Permanents you control gain indestructible until end of turn.
        • Target creature gains double strike until end of turn.
    """)
    private val penalty = Cards.invent("Grave Penalty", "{1}{W}", "Instant", "Grave Penalty deals 3 damage to target tapped creature and you gain 2 life.")
    private val pact = Cards.invent("Infernal Pact", "{2}{B}{B}", "Enchantment", """
        At the beginning of your upkeep, choose one that hasn't been chosen —
        • This enchantment deals 4 damage to any target and you gain 4 life.
        • Target opponent discards two cards.
        • Draw two cards.
        • You lose the game.
    """)
    private val smite = Cards.invent("Selene's Smite", "{W}", "Instant",
        "Selene's Smite deals 3 damage to target attacking or blocking creature. If that creature would die this turn, exile it instead.")
    private val firebrand = Cards.invent("Frantic Firebrand", "{R}", "Creature — Goblin Pirate", """
        Haste (This creature can attack and {T} as soon as it comes under your control.)
        {T}, Sacrifice this creature: It deals 1 damage to any target.
    """, "1/1")
    private val whelp = Cards.invent("Cinder Whelp", "{2}{R}", "Creature — Dragon", """
        Flying
        Whenever you cast a noncreature or Dragon spell, this creature deals 1 damage to each opponent.
    """, "2/2")
    private val tremors = Cards.invent("Quake Tremors", "{1}{R}", "Enchantment",
        "Whenever a creature you control enters, this enchantment deals 1 damage to each opponent.")
    private val blast = Cards.invent("Blazing Blast", "{4}{R}", "Sorcery", """
        Blazing Blast deals 6 damage to target creature.
        You may discard a card. If you do, draw a card.
    """)
    private val thrust = Cards.invent("Thrust Through", "{W}", "Instant",
        "Thrust Through deals 3 damage to target attacking or blocking creature. You gain 1 life.")
    private val sparker = Cards.invent("Spite Sparker", "{1}{R}{R}", "Creature — Elemental", """
        First strike (This creature deals combat damage before creatures without first strike.)
        Whenever an opponent casts a white or blue instant or sorcery spell, this creature deals 2 damage to that player.
    """, "3/2")
    private val giant = Cards.invent("Painful Giant", "{2}{R}{R}", "Creature — Giant",
        "At the beginning of your upkeep, this creature deals 1 damage to you.", "5/5")
    private val bowman = Cards.invent("Bone Bowman", "{3}{B}", "Creature — Skeleton Archer",
        "When this creature enters, it deals 1 damage to any target.", "3/3")
    private val slagfall = Cards.invent("Slagfall", "{1}{R}{R}", "Sorcery", """
        Choose one —
        • Slagfall deals 3 damage to each creature.
        • Slagfall deals 3 damage to each player.
    """)
    private val pyromancer = Cards.invent("Lizard Pyromancer", "{1}{R}", "Creature — Lizard Wizard",
        "When this creature enters, it deals 2 damage to target player or planeswalker.", "2/1")
    private val volleyer = Cards.invent("Goblin Volleyer", "{3}{R}", "Creature — Goblin Warrior",
        "When this creature enters, it deals damage to target creature an opponent controls equal to the number of Goblins you control.", "4/2")
    private val ox = Cards.invent("Plodding Ox", "{1}{G}", "Creature — Ox", "", "2/2")
    private val brute = Cards.invent("Hulking Brute", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val bobBear = Cards.invent("Marsh Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val bobBrute = Cards.invent("Fenwood Brute", "{1}{G}", "Creature — Ogre", "", "3/3")
    private val relic = Cards.invent("Dusty Relic", "{1}", "Artifact", "")
    private val goblin = Cards.invent("Cave Goblin", "{R}", "Creature — Goblin", "", "1/1")
    private val whiteBolt = Cards.invent("Pale Bolt", "{W}", "Instant", "Pale Bolt deals 1 damage to any target.")
    private val blueSorcery = Cards.invent("Tide Study", "{U}", "Sorcery", "Draw a card.")
    private val redBolt = Cards.invent("Pinprick Zap", "{R}", "Instant", "Pinprick Zap deals 1 damage to any target.")
    private val whiteCreature = Cards.invent("Pale Squire", "{W}", "Creature — Human Knight", "", "1/1")
    private val draconic = Cards.invent("Tiny Drake", "{R}", "Creature — Dragon", "", "1/1")

    /** Answers the mana payment a cast asks for after its targets, if it asks one. */
    private fun Scenario.pay() {
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
    }

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 700.2, 120.3, 704.5g: the first mode's 3 damage kills a 3-toughness creature but not a 4/4
    @Test
    fun abradeFirstModeDamagesACreature() = scenario {
        alice.battlefield("Mountain", "Mountain")
        bob.battlefield(bobBrute, brute)
        alice.hand(abrade)
        alice.casts(abrade, modes = listOf(0), target = bobBrute)
        resolveAll()
        expect {
            bobBrute.zone shouldBe Zone.Graveyard
            brute.zone shouldBe Zone.Battlefield
        }
    }

    // CR 700.2, 701.8: the second mode destroys an artifact; the first mode can't target it
    @Test
    fun abradeSecondModeDestroysAnArtifact() = scenario {
        alice.battlefield("Mountain", "Mountain")
        bob.battlefield(relic, brute)
        alice.hand(abrade)
        alice.casts(abrade, modes = listOf(1), target = relic)
        resolveAll()
        expect {
            relic.zone shouldBe Zone.Graveyard
            brute.zone shouldBe Zone.Battlefield
        }
    }

    // CR 120.3: the creature I control deals the damage (its power, 4); the opponent's 3/3 dies and mine is not hurt
    @Test
    fun biteDownUsesMyCreaturesPower() = scenario {
        alice.battlefield("Forest", "Forest", brute)
        bob.battlefield(bobBrute)
        alice.hand(gnash)
        alice.casts(gnash, targets = listOf(brute, bobBrute))
        resolveAll()
        expect {
            bobBrute.zone shouldBe Zone.Graveyard
            brute.zone shouldBe Zone.Battlefield
        }
    }

    // CR 115.4, 120.3: only a creature I don't control is a legal second target
    @Test
    fun biteDownCannotTargetMyOwnSecondCreature() = scenario {
        alice.battlefield("Forest", "Forest", brute, ox)
        bob.battlefield(bobBear)
        alice.hand(gnash)
        alice.casts(gnash)
        alice.answers(Answer.Targets(listOf(target(brute))))
        expect { decision<Decision.Targets>().options.toSet() shouldBe setOf(target(bobBear)) }
    }

    // CR 120.3, 102.2: only opponents lose life
    @Test
    fun boltwaveHitsTheOpponentOnly() = scenario {
        alice.battlefield("Mountain")
        alice.hand(boltsurge)
        alice.casts(boltsurge)
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.life shouldBe 20
        }
    }

    // CR 700.2, 120.3: mode one deals 4 to a chosen player
    @Test
    fun charmDamagesAPlayer() = scenario {
        alice.battlefield("Mountain", "Plains")
        alice.hand(charm)
        alice.casts(charm, modes = listOf(0), target = "bob")
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    // CR 700.2, 702.12a, 611.2a: mode two makes only my permanents indestructible
    @Test
    fun charmMakesMyPermanentsIndestructible() = scenario {
        alice.battlefield("Mountain", "Plains", ox)
        bob.battlefield(bobBear)
        alice.hand(charm)
        alice.casts(charm, modes = listOf(1))
        resolveAll()
        expect {
            ox hasKeyword Keyword.Indestructible.kind
            "Mountain" hasKeyword Keyword.Indestructible.kind
            bobBear lacksKeyword Keyword.Indestructible.kind
        }
    }

    // CR 700.2, 702.4a: mode three gives the targeted creature double strike
    @Test
    fun charmGivesDoubleStrike() = scenario {
        alice.battlefield("Mountain", "Plains", ox)
        bob.battlefield(bobBear)
        alice.hand(charm)
        alice.casts(charm, modes = listOf(2), target = ox)
        resolveAll()
        expect {
            ox hasKeyword Keyword.DoubleStrike.kind
            bobBear lacksKeyword Keyword.DoubleStrike.kind
        }
    }

    // CR 115.4: untapped creatures are not legal targets, so there is nothing to choose
    @Test
    fun riposteHasNoTargetAmongUntappedCreatures() = scenario {
        alice.battlefield("Plains", "Plains")
        bob.battlefield(bobBear, bobBrute)
        alice.hand(penalty)
        assertFailsWith<IllegalArgumentException> { alice.casts(penalty) }
    }

    // CR 120.3, 119.1
    @Test
    fun riposteDamagesAndGainsLife() = scenario {
        alice.battlefield("Plains", "Plains")
        bob.battlefield(bobBear)
        bob.battlefield(bobBrute, tapped = true)
        alice.hand(penalty)
        alice.casts(penalty, target = bobBrute)
        resolveAll()
        expect {
            bobBrute.zone shouldBe Zone.Graveyard
            bobBear.zone shouldBe Zone.Battlefield
            alice.life shouldBe 22
        }
    }

    // CR 700.2d, 603.2, 120.3, 119.1: the first upkeep's mode is chosen; it can't be chosen again later
    @Test
    fun pactChoosesEachModeOnlyOnce() = scenario {
        alice.battlefield(pact)
        alice.library(ox, ox, ox, ox, ox, ox)
        bob.library(bobBear, bobBear, bobBear, bobBear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        alice.answers(Answer.Modes(listOf(2)))
        resolveAll()
        val handAfterFirst = alice.hand.size
        advanceTo(TurnStep.Upkeep, turn = 5)
        expect { decision<Decision.Modes>().options.single { it.index == 2 }.available shouldBe false }
        alice.answers(Answer.Modes(listOf(0)))
        alice.answers(Answer.Targets(listOf(target("bob"))))
        resolveAll()
        expect {
            bob.life shouldBe 16
            alice.life shouldBe 24
            alice.hand.size shouldBe handAfterFirst + 1
        }
    }

    // CR 700.2d, 104.3a: choosing the last mode loses the game
    @Test
    fun pactCanLoseTheGame() = scenario {
        alice.battlefield(pact)
        alice.library(ox, ox, ox, ox)
        bob.library(bobBear, bobBear, bobBear, bobBear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        alice.answers(Answer.Modes(listOf(3)))
        resolveAll()
        expect { state.player(alice.id).lost shouldBe true }
    }

    // CR 120.3, 614.6, 506.4: the attacker is exiled, not put into the graveyard; a creature that isn't attacking is no target
    @Test
    fun smiteExilesAnAttackerThatWouldDie() = scenario {
        alice.battlefield("Plains", ox, brute)
        alice.hand(smite)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox)
        alice.casts(smite)
        expect { decision<Decision.Targets>().options.toSet() shouldBe setOf(target(ox)) }
        alice.answers(Answer.Targets(listOf(target(ox))))
        pay()
        resolveAll()
        expect { ox.zone shouldBe Zone.Exile }
    }

    // CR 120.3, 506.4: a blocking creature is a legal target too
    @Test
    fun smiteTargetsABlocker() = scenario {
        alice.battlefield("Plains", ox)
        bob.battlefield(bobBear)
        alice.hand(smite)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(bobBear to ox)
        alice.casts(smite, target = bobBear)
        resolveAll()
        expect { bobBear.zone shouldBe Zone.Exile }
    }

    // CR 602.2, 118.12, 120.3: sacrificing is a cost; the creature deals the damage
    @Test
    fun firebrandSacrificesItselfToDealOneDamage() = scenario {
        alice.battlefield(firebrand)
        advanceTo(TurnStep.PrecombatMain)
        alice.activates(firebrand, target = "bob")
        resolveAll()
        expect {
            firebrand.zone shouldBe Zone.Graveyard
            bob.life shouldBe 19
        }
    }

    // CR 603.2, 601.2: a noncreature spell or a Dragon creature spell triggers, an ordinary creature spell doesn't
    @Test
    fun whelpTriggersOnNoncreatureAndDragonSpells() = scenario {
        alice.battlefield("Forest", "Mountain", "Mountain", "Mountain", "Mountain", whelp)
        alice.hand(redBolt, ox, draconic)
        alice.casts(ox)
        resolveAll()
        expect { bob.life shouldBe 20 }
        alice.casts(draconic)
        resolveAll()
        expect { bob.life shouldBe 19 }
        alice.casts(redBolt, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    // CR 603.6a, 120.3: only a creature of mine entering counts
    @Test
    fun tremorsCountsMyCreaturesEntering() = scenario {
        alice.battlefield("Mountain", "Mountain", "Forest", tremors)
        alice.hand(ox)
        alice.casts(ox)
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    // CR 120.3, 701.9, 608.2c: the creature dies; discarding is optional and drawing follows only if I did
    @Test
    fun blastDiscardThenDraws() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        bob.battlefield(bobBrute)
        alice.hand(blast, ox, goblin)
        alice.library(brute, brute)
        alice.casts(blast, target = bobBrute)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        alice.chooses(ox)
        resolveAll()
        expect {
            bobBrute.zone shouldBe Zone.Graveyard
            ox.zone shouldBe Zone.Graveyard
            goblin.zone shouldBe Zone.Hand
            alice.hand.size shouldBe 2
        }
    }

    // CR 608.2c: declining the discard draws nothing
    @Test
    fun blastDeclinedDiscardDoesNotDraw() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        bob.battlefield(bobBrute)
        alice.hand(blast, ox, goblin)
        alice.library(brute, brute)
        alice.casts(blast, target = bobBrute)
        resolveAll()
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            ox.zone shouldBe Zone.Hand
            alice.hand.size shouldBe 2
        }
    }

    // CR 120.3, 119.1, 506.4
    @Test
    fun joustDamagesAnAttackerAndGainsLife() = scenario {
        alice.battlefield("Plains", ox)
        alice.hand(thrust)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox)
        alice.casts(thrust, target = ox)
        resolveAll()
        expect {
            ox.zone shouldBe Zone.Graveyard
            alice.life shouldBe 21
        }
    }

    // CR 603.2, 105.1, 120.3: a white instant triggers; my own spell, a red instant and a white creature don't
    @Test
    fun sparkerPunishesOpponentsWhiteOrBlueSpells() = scenario {
        alice.battlefield(sparker, "Plains")
        bob.battlefield("Plains", "Plains", "Island", "Mountain")
        bob.hand(whiteBolt, redBolt, blueSorcery, whiteCreature)
        bob.library(bobBear, bobBear)
        alice.library(ox, ox, ox)
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.casts(whiteCreature)
        resolveAll()
        expect { bob.life shouldBe 20 }
        bob.casts(redBolt, target = "alice")
        resolveAll()
        expect { bob.life shouldBe 20 }
        bob.casts(whiteBolt, target = "alice")
        resolveAll()
        expect { bob.life shouldBe 18 }
        bob.casts(blueSorcery)
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    // CR 603.2, 120.3: the giant hurts its controller at the upkeep
    @Test
    fun giantDamagesItsControllerEachUpkeep() = scenario {
        alice.battlefield(giant)
        alice.library(ox, ox, ox)
        bob.library(bobBear, bobBear, bobBear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect { alice.life shouldBe 19 }
    }

    // CR 603.6a, 120.3: the archer's trigger hits a chosen target
    @Test
    fun archerShootsAChosenTarget() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        bob.battlefield(bobBear)
        alice.hand(bowman)
        alice.casts(bowman)
        resolveAll()
        alice.answers(Answer.Targets(listOf(target("bob"))))
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    // CR 700.2, 120.3, 704.5g: mode one hits every creature, mine included
    @Test
    fun slagstormFirstModeHitsEveryCreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", ox, brute)
        bob.battlefield(bobBear, bobBrute)
        alice.hand(slagfall)
        alice.casts(slagfall, modes = listOf(0))
        resolveAll()
        expect {
            ox.zone shouldBe Zone.Graveyard
            bobBear.zone shouldBe Zone.Graveyard
            bobBrute.zone shouldBe Zone.Graveyard
            brute.zone shouldBe Zone.Battlefield
            alice.life shouldBe 20
        }
    }

    // CR 700.2, 120.3: mode two hits both players
    @Test
    fun slagstormSecondModeHitsEachPlayer() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", ox)
        alice.hand(slagfall)
        alice.casts(slagfall, modes = listOf(1))
        resolveAll()
        expect {
            alice.life shouldBe 17
            bob.life shouldBe 17
            ox.zone shouldBe Zone.Battlefield
        }
    }

    // CR 603.6a, 120.3: a player is a legal target, a creature is not
    @Test
    fun pyromancerHitsAPlayer() = scenario {
        alice.battlefield("Mountain", "Mountain")
        bob.battlefield(bobBear)
        alice.hand(pyromancer)
        alice.casts(pyromancer)
        resolveAll()
        expect { decision<Decision.Targets>().options.contains(target(bobBear)) shouldBe false }
        alice.answers(Answer.Targets(listOf(target("bob"))))
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    // CR 603.6a, 120.3: the number of Goblins is counted on resolution and includes the Volleyer itself
    @Test
    fun volleyerCountsGoblins() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", goblin)
        bob.battlefield(bobBear, bobBrute)
        alice.hand(volleyer)
        alice.casts(volleyer)
        resolveAll()
        alice.answers(Answer.Targets(listOf(target(bobBear))))
        resolveAll()
        expect {
            bobBear.zone shouldBe Zone.Graveyard
            bobBrute.zone shouldBe Zone.Battlefield
        }
    }

    // CR 120.3: with no other Goblin the damage is only 1
    @Test
    fun volleyerAloneDealsOne() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        bob.battlefield(bobBear, bobBrute)
        alice.hand(volleyer)
        alice.casts(volleyer)
        resolveAll()
        alice.answers(Answer.Targets(listOf(target(bobBear))))
        resolveAll()
        expect { bobBear.zone shouldBe Zone.Battlefield }
    }
}
