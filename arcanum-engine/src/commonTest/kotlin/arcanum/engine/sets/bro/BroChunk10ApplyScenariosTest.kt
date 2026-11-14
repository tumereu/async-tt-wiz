package arcanum.engine.sets.bro

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The Brothers' War chunk 10 in play: "until end of turn" effects. A trigger for the creature itself or another entering, X from the
 * creature cards in the graveyard, a pump that reaches only legendary creatures, a type change of a noncreature artifact, a keyword
 * for Soldiers only, protection from everything for a player, and damage to each of two opponents with a pump.
 */
class BroChunk10ApplyScenariosTest {
    private val pikeguard = Cards.invent("Dunes Pikeguard", "{2}{R}{W}", "Creature — Human Soldier", """
        First strike
        Whenever this creature or another creature you control enters, target creature gets +2/+0 until end of turn.
    """, "2/3")
    private val mourner = Cards.invent("Rootbark Mourner", "{4}{G}{G}", "Creature — Treefolk Druid", """
        Trample
        When this creature enters, target creature gets +X/+X until end of turn, where X is the number of creature cards in your graveyard.
    """, "5/5")
    private val halvar = Cards.invent("Halvar, Steadfast Guard", "{R}{G}", "Legendary Creature — Human Soldier",
        "Sacrifice Halvar: Legendary creatures you control get +1/+0 and gain indestructible until end of turn.", "3/3")
    private val whisperer = Cards.invent("Brass Whisperer", "{G}", "Creature — Human Druid",
        "{2}{G}: Until end of turn, target noncreature artifact you control becomes a 4/4 artifact creature.", "1/1")
    private val sergeant = Cards.invent("Cloud Sergeant", "{1}{U}", "Creature — Human Soldier",
        "{3}: Target Soldier gains flying until end of turn.", "2/1")
    private val casket = Cards.invent("The Frozen Casket", "{3}", "Legendary Artifact",
        "{2}, {T}, Exile The Frozen Casket: You gain protection from everything until your next turn.")
    private val ironbull = Cards.invent("Pellor Ironbull", "{2}{R}", "Creature — Minotaur",
        "{1}, Sacrifice an artifact: This creature gets +1/+1 until end of turn and deals 1 damage to each opponent.", "2/3")

    private val hollin = Cards.invent("Hollin, Cloud Pilot", "{W}{U}", "Legendary Creature — Human Soldier", """
        Flying
        Whenever you attack with five or more Soldiers, creatures you control get +1/+1 and gain flying until end of turn.
    """, "3/2")
    private val bear = Cards.invent("Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val soldierBear = Cards.invent("Plain Pikeman", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val lancer = Cards.invent("Plain Lancer", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val archer = Cards.invent("Plain Archer", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val sentry = Cards.invent("Plain Sentry", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val legend = Cards.invent("Plain Legend", "{2}{W}", "Legendary Creature — Human Noble", "", "2/2")
    private val trinket = Cards.invent("Plain Trinket", "{1}", "Artifact", "")
    private val zap = Cards.invent("Plain Zap", "{R}", "Instant", "Plain Zap deals 2 damage to any target.")

    // CR 603.2, 603.6a: one trigger fires for the creature itself entering and for another one entering
    @Test
    fun pikeguardTriggersForItselfAndForAnotherCreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(pikeguard, soldierBear)
        alice.casts(pikeguard)
        playOut(bear)
        expect { bear.power shouldBe 4 }
        alice.casts(soldierBear)
        playOut(soldierBear)
        expect {
            soldierBear.power shouldBe 4
            bear.power shouldBe 4
        }
    }

    // CR 107.3a: X counts the creature cards in the graveyard as the trigger resolves, not lands or others' cards
    @Test
    fun mournerCountsCreatureCardsInItsControllersGraveyard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", bear)
        alice.graveyard(soldierBear, legend, trinket, "Forest")
        bob.graveyard(bear)
        alice.hand(mourner)
        alice.casts(mourner)
        playOut(bear)
        expect { bear.power shouldBe 4 }
        expect { bear.toughness shouldBe 4 }
    }

    @Test
    fun halvarPumpsOnlyLegendaryCreaturesYouControl() = scenario {
        alice.battlefield(halvar, legend, bear)
        bob.battlefield(Cards.invent("Plain Rival", "{1}", "Legendary Creature — Human Noble", "", "2/2"))
        alice.activates(halvar)
        playOut()
        expect {
            legend.power shouldBe 3
            legend hasKeyword Keyword.Indestructible
            bear.power shouldBe 2
            bear lacksKeyword Keyword.Indestructible
            card("Plain Rival").power shouldBe 2
        }
    }

    // CR 205.1b, 613.1d, 613.4b: it becomes an artifact creature 4/4, keeping its artifact type
    @Test
    fun whispererMakesANoncreatureArtifactAFourFourCreature() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", whisperer, trinket)
        alice.activates(whisperer, target = trinket)
        playOut()
        expect {
            trinket.power shouldBe 4
            trinket.toughness shouldBe 4
            trinket.types shouldBe listOf(CardType.Artifact, CardType.Creature)
        }
    }

    @Test
    fun sergeantTargetsOnlySoldiers() = scenario {
        alice.battlefield("Island", "Island", "Island", sergeant, soldierBear, bear)
        alice.activates(sergeant)
        assertTrue(target(bear) !in decision<Decision.Targets>().options, "a Bear isn't a Soldier")
        assertTrue(target(soldierBear) in decision<Decision.Targets>().options)
        alice.answers(Answer.Targets(listOf(target(soldierBear))))
        playOut()
        expect { soldierBear hasKeyword Keyword.Flying }
    }

    // CR 702.16j, 514.2: the player can't be targeted by Bob's spell until Alice's next turn begins
    @Test
    fun casketProtectsItsControllerFromEverythingUntilTheirNextTurn() = scenario {
        alice.battlefield("Island", "Island", casket)
        bob.battlefield("Mountain")
        bob.hand(zap)
        alice.activates(casket)
        playOut()
        alice.passes()
        assertFailsWith<Throwable> { bob.casts(zap, target = "alice") }
        expect { casket.zone shouldBe arcanum.api.Zone.Exile }
    }

    // CR 608.2: each opponent takes the damage, in a three-player game
    @Test
    fun ironbullPumpsAndDamagesEachOpponent() = scenario(players = 3) {
        alice.battlefield("Mountain", ironbull, trinket)
        alice.activates(ironbull)
        playOut(trinket)
        expect {
            ironbull.power shouldBe 3
            ironbull.toughness shouldBe 4
            bob.life shouldBe 19
            carol.life shouldBe 19
            alice.life shouldBe 20
            trinket.zone shouldBe arcanum.api.Zone.Graveyard
        }
    }

    // CR 508.1, 603.2c: the trigger needs five Soldiers among the attackers; four Soldiers or five other creatures don't do
    @Test
    fun hollinNeedsFiveAttackingSoldiers() = scenario {
        alice.battlefield(hollin, soldierBear, lancer, archer, sentry, bear)
        advanceTo(arcanum.api.TurnStep.DeclareAttackers)
        alice.attacks(hollin, soldierBear, lancer, archer)
        playOut()
        expect { bear.power shouldBe 2 }
    }

    @Test
    fun hollinPumpsEveryCreatureWhenFiveSoldiersAttack() = scenario {
        alice.battlefield(hollin, soldierBear, lancer, archer, sentry, bear)
        advanceTo(arcanum.api.TurnStep.DeclareAttackers)
        alice.attacks(hollin, soldierBear, lancer, archer, sentry)
        playOut()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Flying
            hollin.power shouldBe 4
        }
    }
}
