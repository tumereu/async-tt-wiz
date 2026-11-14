package arcanum.engine.sets.bro

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The Brothers' War chunk 15 in play: a counter only the first time an ability resolves each turn, deathtouch from an artifact
 * count, an opponent's discard that puts a card onto the battlefield, protection from multicolored, a land-entering life
 * trigger for opponents, and an anthem.
 */
class BroChunk15GainAbilitiesLifeLookModifyScenariosTest {
    private val hatchling = Cards.invent("Cogwheel Hatchling", "{G}", "Creature — Wurm", """
        This creature has deathtouch as long as you control three or more artifacts.
        Whenever an artifact you control enters, you gain 1 life. If this is the first time this ability has resolved this turn, put a +1/+1 counter on this creature.
    """, "1/1")
    private val gearOne = Cards.invent("Brass Gear", "{1}", "Artifact", "")
    private val gearTwo = Cards.invent("Iron Gear", "{1}", "Artifact", "")
    private val gearThree = Cards.invent("Tin Gear", "{1}", "Artifact", "")
    private val boar = Cards.invent("Unyielding Boar", "{2}{G}{G}", "Creature — Beast", """
        When this creature enters, you gain 4 life.
        If a spell or ability an opponent controls causes you to discard this card, put it onto the battlefield instead of putting it into your graveyard.
    """, "4/4")
    private val rot = Cards.invent("Forced Discard", "{2}{B}", "Sorcery", "Target player discards a card.")
    private val sentinel = Cards.invent("Mirror Sentinel", "{1}", "Artifact Creature — Soldier", """
        Protection from multicolored (This creature can't be blocked, targeted, dealt damage, enchanted, or equipped by anything multicolored.)
        Whenever a nonbasic land an opponent controls enters, you gain 1 life.
    """, "1/2")
    private val raider = Cards.invent("Prismatic Raider", "{1}{R}{W}", "Creature — Human Soldier", "", "3/3")
    private val mono = Cards.invent("Plain Raider", "{2}{R}", "Creature — Human Soldier", "", "3/3")
    private val cavern = Cards.invent("Ashen Cavern", null, "Land", "{T}: Add {R}.")
    private val hall = Cards.invent("Ironclad Hall", "{1}{W}{W}", "Enchantment", """
        Creatures you control get +1/+1.
        {5}{W}: Exile target nonland permanent you don't control until this enchantment leaves the battlefield. Activate only as a sorcery and only once.
    """)
    private val ally = Cards.invent("Lone Peasant", "{W}", "Creature — Human", "", "1/1")
    private val foe = Cards.invent("Lone Raider", "{R}", "Creature — Human", "", "1/1")

    // CR 603.4: the second resolution in a turn gains life but adds no counter
    @Test
    fun counterOnlyTheFirstTimeTheAbilityResolvesThisTurn() = scenario {
        alice.battlefield(hatchling, "Mountain", "Mountain")
        alice.hand(gearOne, gearTwo)
        alice.casts(gearOne)
        resolveAll()
        expect {
            alice.life shouldBe 21
            hatchling.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
        alice.casts(gearTwo)
        resolveAll()
        expect {
            alice.life shouldBe 22
            hatchling.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // CR 611.3a (a static ability applies only while its condition holds): three artifacts
    @Test
    fun deathtouchOnlyWithThreeArtifacts() = scenario {
        alice.battlefield(hatchling, gearOne, gearTwo)
        expect { hatchling lacksKeyword Keyword.Deathtouch }
        alice.battlefield(gearThree)
        expect { hatchling hasKeyword Keyword.Deathtouch }
    }

    // CR 701.9, 614.1: a spell an opponent controls (here the third player's) puts the discarded card onto the battlefield
    @Test
    fun anOpponentInAThreePlayerGameMakesYouDiscardTheBoarOntoTheBattlefield() = scenario(players = 3) {
        alice.hand(boar)
        carol.hand(rot)
        carol.castsWithoutCosts(rot, target = "alice")
        resolveAll()
        expect {
            alice.battlefield has boar
            alice.life shouldBe 24
        }
    }

    // CR 701.9: discarding it to your own spell sends it to the graveyard
    @Test
    fun yourOwnDiscardSendsTheBoarToTheGraveyard() = scenario {
        alice.hand(boar, rot)
        alice.castsWithoutCosts(rot, target = "alice")
        resolve()
        expect { alice.graveyard has boar }
    }

    // CR 702.16b, 702.16e: damage from a multicolored source is prevented; a mono-colored one still hurts
    @Test
    fun protectionFromMulticoloredPreventsOnlyMulticoloredDamage() = scenario {
        turn(number = 2, active = bob, priority = bob)
        alice.battlefield(sentinel)
        bob.battlefield(raider)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(raider)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        alice.blocks(sentinel to raider)
        advanceTo(TurnStep.CombatDamage)
        playOut()
        expect { sentinel.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun protectionFromMulticoloredDoesNotStopAMonoColoredAttacker() = scenario {
        turn(number = 2, active = bob, priority = bob)
        alice.battlefield(sentinel)
        bob.battlefield(mono)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(mono)
        playOut()
        advanceTo(TurnStep.DeclareBlockers)
        alice.blocks(sentinel to mono)
        advanceTo(TurnStep.CombatDamage)
        playOut()
        expect { sentinel.zone shouldBe Zone.Graveyard }
    }

    // CR 603.2, "an opponent": the land of any opponent in a three-player game gains life
    @Test
    fun nonbasicLandOfAnyOpponentGainsLife() = scenario(players = 3) {
        turn(number = 3, active = carol, priority = carol)
        alice.battlefield(sentinel)
        carol.hand(cavern)
        carol.plays(cavern)
        resolveAll()
        expect { alice.life shouldBe 21 }
    }

    @Test
    fun basicLandGainsNothing() = scenario {
        turn(number = 2, active = bob, priority = bob)
        alice.battlefield(sentinel)
        bob.hand("Mountain")
        bob.plays("Mountain")
        resolveAll()
        expect { alice.life shouldBe 20 }
    }

    // CR 613.4c: +1/+1 for your creatures only
    @Test
    fun anthemBoostsOnlyYourCreatures() = scenario {
        alice.battlefield(hall, ally)
        bob.battlefield(foe)
        expect {
            ally.power shouldBe 2
            ally.toughness shouldBe 2
            foe.power shouldBe 1
        }
    }

    // CR 602.5b, 610.3: exile until the enchantment leaves, only once, only as a sorcery
    @Test
    fun hallExilesAnOpponentsPermanentOnlyOnce() = scenario {
        alice.battlefield(hall, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        bob.battlefield(foe, mono)
        alice.activates(hall, target = foe)
        resolveAll()
        expect { bob.exile has foe }
        assertFailsWith<IllegalArgumentException> { alice.activates(hall, target = mono) }
        expect { bob.battlefield has mono }
    }
}
