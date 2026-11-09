package arcanum.engine.sets.fdn

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Chunk 25 played: the invented names carry the oracle text of Essence Scatter, Flashfreeze, Negate, Refute, Voracious Greatshark,
 * Deadly Plot, Deathmark, Make Your Move, Meteor Golem, Mortify, Stroke of Midnight, Trygon Predator, Bloodtithe Collector,
 * Dragon Mage and Seeker's Folly.
 */
class FdnChunk25CounterDestroyDiscardScenariosTest {
    private val dissolve = Cards.invent("Essence Dissolve", "{1}{U}", "Instant", "Counter target creature spell.")
    private val flashchill = Cards.invent("Flashchill", "{1}{U}", "Instant", "Counter target red or green spell.")
    private val nullify = Cards.invent("Nullify Spell", "{1}{U}", "Instant", "Counter target noncreature spell.")
    private val rebuff = Cards.invent("Rebuff", "{1}{U}{U}", "Instant", "Counter target spell. Draw a card, then discard a card.")
    private val greatshark = Cards.invent("Hungry Greatshark", "{3}{U}{U}", "Creature — Shark", """
        Flash (You may cast this spell any time you could cast an instant.)
        When this creature enters, counter target artifact or creature spell.
    """, "5/4")
    private val scheme = Cards.invent("Deadly Scheme", "{3}{B}", "Instant", """
        Choose one —
        • Destroy target creature or planeswalker.
        • Return target Zombie creature card from your graveyard to the battlefield tapped.
    """)
    private val deathbrand = Cards.invent("Deathbrand", "{B}", "Sorcery", "Destroy target green or white creature.")
    private val stand = Cards.invent("Make Your Stand", "{2}{W}", "Instant", "Destroy target artifact, enchantment, or creature with power 4 or greater.")
    private val giant = Cards.invent("Meteor Giant", "{7}", "Artifact Creature — Golem", "When this creature enters, destroy target nonland permanent an opponent controls.", "3/3")
    private val mortify = Cards.invent("Mortify Pair", "{1}{W}{B}", "Instant", "Destroy target creature or enchantment.")
    private val stroke = Cards.invent("Stroke of Dusk", "{2}{W}", "Instant", "Destroy target nonland permanent. Its controller creates a 1/1 white Human creature token.")
    private val hunter = Cards.invent("Trygon Hunter", "{1}{G}{U}", "Creature — Beast", """
        Flying
        Whenever this creature deals combat damage to a player, you may destroy target artifact or enchantment that player controls.
    """, "2/3")
    private val gatherer = Cards.invent("Bloodtithe Gatherer", "{4}{B}", "Creature — Vampire Noble", """
        Flying
        When this creature enters, if an opponent lost life this turn, each opponent discards a card.
    """, "3/4")
    private val dragonWizard = Cards.invent("Dragon Wizard", "{5}{R}{R}", "Creature — Dragon Wizard", """
        Flying
        Whenever this creature deals combat damage to a player, each player discards their hand, then draws seven cards.
    """, "5/5")
    private val seekersError = Cards.invent("Seeker's Error", "{2}{B}", "Sorcery", """
        Choose one —
        • Target opponent discards two cards.
        • Creatures your opponents control get -1/-1 until end of turn.
    """)

    private val redRaider = Cards.invent("Red Raider", "{1}{R}", "Creature — Goblin", "", "2/2")
    private val greenGrub = Cards.invent("Green Grub", "{1}{G}", "Creature — Insect", "", "2/2")
    private val blueDrifter = Cards.invent("Blue Drifter", "{1}{U}", "Creature — Illusion", "", "2/2")
    private val whiteKnight = Cards.invent("White Knight", "{1}{W}", "Creature — Human Knight", "", "2/2")
    private val blackBat = Cards.invent("Black Bat", "{1}{B}", "Creature — Bat", "", "2/2")
    private val bigWolf = Cards.invent("Lone Wolf", "{3}{G}", "Creature — Wolf", "", "4/4")
    private val zombieHusk = Cards.invent("Zombie Husk", "{1}{B}", "Creature — Zombie", "", "2/2")
    private val goldIdol = Cards.invent("Tin Trinket", "{2}", "Artifact", "")
    private val paleAura = Cards.invent("Pale Aura", "{1}{W}", "Enchantment", "")
    private val quickStudy = Cards.invent("Quick Study", "{1}{R}", "Sorcery", "Draw a card.")
    private val sparkJab = Cards.invent("Spark Jab", "{R}", "Instant", "Spark Jab deals 1 damage to target player.")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    private fun Scenario.stock() {
        alice.library("Island", "Forest", "Plains", "Swamp", "Mountain", "Island", "Forest", "Plains", "Swamp", "Mountain", "Island", "Forest")
        bob.library("Island", "Forest", "Plains", "Swamp", "Mountain", "Island", "Forest", "Plains", "Swamp", "Mountain", "Island", "Forest")
    }

    // CR 701.6a: the creature spell is countered and goes to its owner's graveyard
    @Test
    fun dissolveCountersACreatureSpell() = scenario {
        bob.battlefield("Mountain", "Mountain")
        bob.hand(redRaider)
        alice.battlefield("Island", "Island")
        alice.hand(dissolve)
        turn(active = bob)
        bob.casts(redRaider)
        bob.passes()
        alice.casts(dissolve, target = redRaider)
        resolveAll()
        expect { bob.graveyard has redRaider }
    }

    // CR 114.1, 601.2c: a noncreature spell is no legal target
    @Test
    fun dissolveCantTargetANoncreatureSpell() = scenario {
        bob.battlefield("Mountain", "Mountain")
        bob.hand(quickStudy)
        alice.battlefield("Island", "Island")
        alice.hand(dissolve)
        stock()
        turn(active = bob)
        bob.casts(quickStudy)
        bob.passes()
        assertFailsWith<IllegalArgumentException> { alice.casts(dissolve) }
    }

    // CR 105.1: a red spell is countered
    @Test
    fun flashchillCountersARedSpell() = scenario {
        bob.battlefield("Mountain", "Mountain")
        bob.hand(redRaider)
        alice.battlefield("Island", "Island")
        alice.hand(flashchill)
        turn(active = bob)
        bob.casts(redRaider)
        bob.passes()
        alice.casts(flashchill, target = redRaider)
        resolveAll()
        expect { bob.graveyard has redRaider }
    }

    // CR 105.1: a green spell is countered too
    @Test
    fun flashchillCountersAGreenSpell() = scenario {
        bob.battlefield("Forest", "Forest")
        bob.hand(greenGrub)
        alice.battlefield("Island", "Island")
        alice.hand(flashchill)
        turn(active = bob)
        bob.casts(greenGrub)
        bob.passes()
        alice.casts(flashchill, target = greenGrub)
        resolveAll()
        expect { bob.graveyard has greenGrub }
    }

    // CR 114.1: a blue spell is no legal target
    @Test
    fun flashchillCantTargetABlueSpell() = scenario {
        bob.battlefield("Island", "Island")
        bob.hand(blueDrifter)
        alice.battlefield("Island", "Island")
        alice.hand(flashchill)
        turn(active = bob)
        bob.casts(blueDrifter)
        bob.passes()
        assertFailsWith<IllegalArgumentException> { alice.casts(flashchill) }
    }

    // CR 701.6a: a noncreature spell is countered
    @Test
    fun nullifyCountersANoncreatureSpell() = scenario {
        bob.battlefield("Mountain", "Mountain")
        bob.hand(quickStudy)
        alice.battlefield("Island", "Island")
        alice.hand(nullify)
        stock()
        turn(active = bob)
        bob.casts(quickStudy)
        bob.passes()
        alice.casts(nullify, target = quickStudy)
        resolveAll()
        expect {
            bob.graveyard has quickStudy
            bob.hand.size shouldBe 0
        }
    }

    // CR 114.1: a creature spell is no legal target
    @Test
    fun nullifyCantTargetACreatureSpell() = scenario {
        bob.battlefield("Mountain", "Mountain")
        bob.hand(redRaider)
        alice.battlefield("Island", "Island")
        alice.hand(nullify)
        turn(active = bob)
        bob.casts(redRaider)
        bob.passes()
        assertFailsWith<IllegalArgumentException> { alice.casts(nullify) }
    }

    // CR 608.2c: counter first, then draw a card and discard a card, in that order
    @Test
    fun rebuffCountersThenLoots() = scenario {
        bob.battlefield("Mountain", "Mountain")
        bob.hand(redRaider)
        alice.battlefield("Island", "Island", "Island")
        alice.hand(rebuff)
        alice.library("Swamp", "Forest")
        turn(active = bob)
        bob.casts(redRaider)
        bob.passes()
        alice.casts(rebuff, target = redRaider)
        resolveAll()
        expect {
            bob.graveyard has redRaider
            alice.graveyard has rebuff
            alice.graveyard has "Swamp"
            alice.hand.size shouldBe 0
        }
    }

    // CR 603.1, 701.6a: the enters trigger counters an artifact spell, found on the Stack after the shark resolved
    @Test
    fun greatsharkCountersAnArtifactSpell() = scenario {
        bob.battlefield("Mountain", "Mountain")
        bob.hand(goldIdol)
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(greatshark)
        turn(active = bob)
        bob.casts(goldIdol)
        bob.passes()
        alice.casts(greatshark)
        resolveAll()
        expect {
            greatshark.zone shouldBe Zone.Battlefield
            bob.graveyard has goldIdol
        }
    }

    // CR 115.1: with only an enchantment spell on the Stack the trigger has no target and does nothing
    @Test
    fun greatsharkLeavesAnEnchantmentSpell() = scenario {
        bob.battlefield("Plains", "Plains")
        bob.hand(paleAura)
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(greatshark)
        turn(active = bob)
        bob.casts(paleAura)
        bob.passes()
        alice.casts(greatshark)
        resolveAll()
        expect {
            greatshark.zone shouldBe Zone.Battlefield
            paleAura.zone shouldBe Zone.Battlefield
        }
    }

    // CR 700.2, 701.8a: the destroy mode
    @Test
    fun schemeDestroysACreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(scheme)
        bob.battlefield(redRaider)
        alice.casts(scheme, target = redRaider, modes = listOf(0))
        resolveAll()
        expect { bob.graveyard has redRaider }
    }

    // CR 700.2, 110.5b: the return mode puts the Zombie card onto the battlefield tapped
    @Test
    fun schemeReturnsAZombieTapped() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(scheme)
        alice.graveyard(zombieHusk, blackBat)
        alice.casts(scheme, target = zombieHusk, modes = listOf(1))
        resolveAll()
        expect {
            zombieHusk.zone shouldBe Zone.Battlefield
            zombieHusk.tapped shouldBe true
            blackBat.zone shouldBe Zone.Graveyard
        }
    }

    // CR 701.8a: a white creature dies
    @Test
    fun deathbrandDestroysAWhiteCreature() = scenario {
        alice.battlefield("Swamp")
        alice.hand(deathbrand)
        bob.battlefield(whiteKnight)
        alice.casts(deathbrand, target = whiteKnight)
        resolveAll()
        expect { bob.graveyard has whiteKnight }
    }

    // CR 114.1: a black creature is no legal target
    @Test
    fun deathbrandCantTargetABlackCreature() = scenario {
        alice.battlefield("Swamp")
        alice.hand(deathbrand)
        bob.battlefield(blackBat)
        assertFailsWith<IllegalArgumentException> { alice.casts(deathbrand) }
    }

    // CR 701.8a: an artifact, an enchantment or a creature with power 4 or greater
    @Test
    fun standDestroysAnArtifact() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(stand)
        bob.battlefield(goldIdol)
        alice.casts(stand, target = goldIdol)
        resolveAll()
        expect { bob.graveyard has goldIdol }
    }

    @Test
    fun standDestroysABigCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(stand)
        bob.battlefield(bigWolf)
        alice.casts(stand, target = bigWolf)
        resolveAll()
        expect { bob.graveyard has bigWolf }
    }

    // CR 114.1: the power restriction applies to creatures; a 2/2 is no legal target, while a second target exists
    @Test
    fun standCantTargetASmallCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(stand)
        bob.battlefield(redRaider)
        assertFailsWith<IllegalArgumentException> { alice.casts(stand) }
    }

    // CR 603.1, 701.8a: only a nonland permanent an opponent controls; the land and own permanent are no targets
    @Test
    fun giantDestroysANonlandPermanentOfAnOpponent() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", goldIdol)
        alice.hand(giant)
        bob.battlefield(blackBat, "Swamp")
        alice.casts(giant)
        resolveAll()
        expect {
            giant.zone shouldBe Zone.Battlefield
            bob.graveyard has blackBat
            bob.battlefield has "Swamp"
            goldIdol.zone shouldBe Zone.Battlefield
        }
    }

    // CR 701.8a: either a creature or an enchantment
    @Test
    fun mortifyDestroysAnEnchantment() = scenario {
        alice.battlefield("Plains", "Swamp", "Plains")
        alice.hand(mortify)
        bob.battlefield(paleAura, goldIdol)
        alice.casts(mortify, target = paleAura)
        resolveAll()
        expect {
            bob.graveyard has paleAura
            goldIdol.zone shouldBe Zone.Battlefield
        }
    }

    @Test
    fun mortifyDestroysACreature() = scenario {
        alice.battlefield("Plains", "Swamp", "Plains")
        alice.hand(mortify)
        bob.battlefield(redRaider, paleAura)
        alice.casts(mortify, target = redRaider)
        resolveAll()
        expect {
            bob.graveyard has redRaider
            paleAura.zone shouldBe Zone.Battlefield
        }
    }

    // CR 608.2h, 111.2: "its controller" is Bob, the destroyed permanent's controller, and Bob gets the token
    @Test
    fun strokeGivesTheTokenToTheControllerOfTheDestroyedPermanent() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(stroke)
        bob.battlefield(goldIdol)
        alice.casts(stroke, target = goldIdol)
        resolveAll()
        expect {
            bob.graveyard has goldIdol
            bob.battlefield has "Human Token"
        }
        kotlin.test.assertEquals(0, alice.battlefield.names.count { it == "Human Token" })
    }

    // CR 510.2, 603.1: unblocked, it deals combat damage and its controller may destroy the player's artifact
    @Test
    fun hunterDestroysTheDamagedPlayersArtifact() = scenario {
        alice.battlefield(hunter)
        bob.battlefield(goldIdol, paleAura)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hunter)
        advanceTo(TurnStep.CombatDamage)
        alice.answers(Answer.Targets(listOf(target(goldIdol))))
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.life shouldBe 18
            bob.graveyard has goldIdol
            paleAura.zone shouldBe Zone.Battlefield
        }
    }

    // CR 603.4: an opponent lost life this turn, so the intervening "if" holds and the opponent discards
    @Test
    fun gathererMakesTheOpponentDiscardAfterLifeLoss() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Mountain", "Mountain")
        alice.hand(gatherer, sparkJab)
        bob.hand("Forest", "Island")
        alice.casts(sparkJab, target = "bob")
        resolveAll()
        alice.casts(gatherer)
        resolveAll()
        bob.chooses("Forest")
        resolveAll()
        expect {
            bob.life shouldBe 19
            bob.graveyard has "Forest"
            bob.hand.size shouldBe 1
        }
    }

    // CR 603.4: no opponent lost life, so the trigger does nothing
    @Test
    fun gathererDoesNothingWithoutLifeLoss() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand(gatherer)
        bob.hand("Forest", "Island")
        alice.casts(gatherer)
        resolveAll()
        expect {
            gatherer.zone shouldBe Zone.Battlefield
            bob.hand.size shouldBe 2
        }
    }

    // CR 701.9a, 121.1: every card of both hands is discarded, then each player draws seven
    @Test
    fun dragonWizardDiscardsEveryHandThenDrawsSeven() = scenario {
        alice.battlefield(dragonWizard)
        alice.hand("Forest", "Island", "Plains")
        bob.hand("Swamp", "Mountain")
        stock()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(dragonWizard)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            alice.graveyard has "Forest"
            alice.graveyard has "Island"
            alice.graveyard has "Plains"
            bob.graveyard has "Swamp"
            bob.graveyard has "Mountain"
            alice.hand.size shouldBe 7
            bob.hand.size shouldBe 7
        }
    }

    // CR 700.2, 701.9a: the first mode makes the opponent discard two cards of their choice
    @Test
    fun seekersErrorMakesTheOpponentDiscardTwo() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(seekersError)
        bob.hand("Forest", "Island", "Plains")
        alice.casts(seekersError, target = "bob", modes = listOf(0))
        resolveAll()
        bob.chooses("Forest", "Island")
        resolveAll()
        expect {
            bob.graveyard has "Forest"
            bob.graveyard has "Island"
            bob.hand.size shouldBe 1
        }
    }

    // CR 700.2, 613.4c: the second mode shrinks only the opponents' creatures until end of turn
    @Test
    fun seekersErrorShrinksOnlyOpposingCreatures() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", blackBat)
        alice.hand(seekersError)
        bob.battlefield(redRaider)
        alice.casts(seekersError, modes = listOf(1))
        resolveAll()
        expect {
            redRaider.power shouldBe 1
            blackBat.power shouldBe 2
        }
    }
}
