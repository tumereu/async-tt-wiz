package arcanum.engine.sets.one

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.CardRef
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dft.hits
import arcanum.engine.sets.ltr.aliceUpkeep
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Phyrexia: All Will Be One chunk 10 in play: a creature type's attack requirement reaching creatures that gained the type (CR 508.1d, 613.1d),
 * a card returning at its owner's next end step (CR 603.7), choosing a creature for each player and sacrificing the rest (3 players), paying
 * life and exiling a dead creature to copy it (CR 707.9b), cards that can't enter the battlefield and spells that can't be cast from
 * graveyards (CR 101.2), optional counter removal (CR 118.3), loyalty abilities twice (CR 606.3), a choice of a creature and a land card with
 * X 5 or more, a source's controller sacrificing for the damage (CR 120.3), a reflexive trigger on a discard (CR 603.12), Equipment moved from
 * a creature that died (CR 608.2h) and damage for the counters put (CR 603.2c).
 */
class OneChunk10EffectsAndTriggersScenariosTest {
    private val oil = CounterKind.Named("oil")

    private val tusker = Cards.invent("Plodding Tusker", "{1}{G}", "Creature — Boar", "", "2/2")
    private val lumbering = Cards.invent("Lumbering Tusker", "{1}{G}", "Creature — Boar", "", "2/2")
    private val hulk = Cards.invent("Towering Hulk", "{5}", "Artifact Creature — Golem", "", "5/5")
    private val trinket = Cards.invent("Copper Knickknack", "{2}", "Artifact", "")
    private val jolt = Cards.invent("Scorching Jolt", "{R}", "Instant", "Scorching Jolt deals 3 damage to any target.")
    private val brawn = Cards.invent("Surge of Brawn", "{G}", "Instant", "Put two +1/+1 counters on target creature.")
    private val field = Cards.invent("Return to the Field", "{1}{W}", "Sorcery", "Return target creature card from your graveyard to the battlefield.")
    private val spark = Cards.invent("Echo Spark", "{R}", "Instant", """
        Echo Spark deals 1 damage to any target.
        Flashback {R}
    """)
    private val spur = Cards.invent("Iron Spur", "{1}", "Artifact — Equipment", """
        Equipped creature gets +1/+0.
        Equip {1}
    """)

    private val graaz = Cards.invent("Graaz Juggernaut Warden", "{8}", "Legendary Artifact Creature — Juggernaut", """
        Juggernauts you control attack each combat if able.
        Juggernauts you control can't be blocked by Walls.
        Other creatures you control have base power and toughness 5/3 and are Juggernauts in addition to their other creature types.
    """, "7/5")
    private val roamer = Cards.invent("Eternal Roamer", "{4}{W}{W}", "Legendary Planeswalker", """
        No more than one creature can attack Eternal Roamer each combat.
        [+1]: Exile up to one target artifact or creature. Return that card to the battlefield under its owner's control at the beginning of that player's next end step.
        [0]: Create a 2/2 white Samurai creature token with double strike.
        [−4]: For each player, choose a creature that player controls. Each player sacrifices all creatures they control not chosen this way.
    """, loyalty = "5")
    private val kinzar = Cards.invent("Kinzar Bleak Patron", "{4}{B}", "Legendary Creature — Phyrexian Vampire", """
        Flying
        Whenever another nontoken creature you control dies, you may pay 2 life and exile it. If you do, create a token that's a copy of that creature, except it's 1/1 and has toxic 1. (Players dealt combat damage by it also get a poison counter.)
    """, "5/4")
    private val jailkeeper = Cards.invent("Hollow Jailkeeper", "{2}", "Artifact Creature — Phyrexian Golem", """
        Permanent cards in graveyards can't enter the battlefield.
        Players can't cast noncreature spells from graveyards or exile.
    """, "0/4")
    private val centurion = Cards.invent("Oilhammer Centurion", "{2}{R}", "Creature — Phyrexian Warrior", """
        Whenever another creature or artifact you control is put into a graveyard from the battlefield, put an oil counter on this creature.
        Whenever this creature attacks, you may remove two oil counters from it. When you do, target creature can't block this turn.
    """, "3/2")
    private val kaiso = Cards.invent("Kaiso, Shadow Dancer", "{2}{U}{B}", "Legendary Planeswalker — Kaito", """
        Whenever one or more creatures you control deal combat damage to a player, you may return one of them to its owner's hand. If you do, you may activate loyalty abilities of Kaiso twice this turn rather than only once.
        [+1]: Up to one target creature can't attack or block until your next turn.
        [0]: Draw a card.
        [−2]: Create a 2/2 colorless Drone artifact creature token with deathtouch and "When this token leaves the battlefield, each opponent loses 2 life and you gain 2 life."
    """, loyalty = "3")
    private val dusk = Cards.invent("Verdant Sun's Dusk", "{X}{G}", "Sorcery",
        "Reveal the top X plus one cards of your library. Choose a creature card and/or a land card from among them. Put those cards into your hand and the rest on the bottom of your library in a random order. If X is 5 or more, instead put the chosen cards onto the battlefield or into your hand and the rest on the bottom of your library in a random order.")
    private val annihilator = Cards.invent("Phyrexian Annihilator", "{B}{B}{B}{B}", "Creature — Phyrexian Horror", """
        Trample
        Whenever a source deals damage to this creature, that source's controller sacrifices that many permanents of their choice.
    """, "5/5")
    private val masticore = Cards.invent("Argent Masticore", "{5}", "Artifact Creature — Phyrexian Masticore", """
        First strike, protection from multicolored
        At the beginning of your upkeep, sacrifice this creature unless you discard a card. When you discard a card this way, destroy target nonland permanent an opponent controls with mana value less than or equal to the mana value of the discarded card.
    """, "5/5")
    private val ruk = Cards.invent("Ruk, Hexgold Snatcher", "{2}{R}", "Legendary Creature — Goblin Rebel", """
        Trample, haste
        Whenever an equipped creature you control other than Ruk attacks or dies, you may attach all Equipment attached to that creature to Ruk.
    """, "2/2")
    private val allOne = Cards.invent("All Become One", "{3}{R}{R}", "Enchantment",
        "Whenever you put one or more counters on a permanent or player, this enchantment deals that much damage to target opponent, creature an opponent controls, or planeswalker an opponent controls.")

    // CR 613.1d, 613.4b: the other creature is a 5/3 Juggernaut; CR 508.1d: so it must attack too
    @Test
    fun graazMakesOthersJuggernautsThatMustAttack() = scenario {
        alice.battlefield(graaz, tusker)
        advanceTo(TurnStep.DeclareAttackers)
        expect {
            tusker.power shouldBe 5
            tusker.toughness shouldBe 3
            assertTrue(Subtype("Juggernaut") in tusker.subtypes)
        }
        assertFailsWith<IllegalArgumentException> { alice.attacks(graaz) }
        alice.attacks(graaz, tusker)
    }

    // CR 603.7: the card returns at the beginning of its owner's next end step, not at the activating player's
    @Test
    fun roamerReturnsTheCardAtItsOwnersEndStep() = scenario {
        alice.battlefield(roamer, counters = listOf(CounterKind.Loyalty to 5))
        alice.library("Plains", "Plains")
        bob.library("Island", "Island")
        bob.battlefield(tusker)
        alice.activates(roamer, ability = 0, target = tusker)
        resolveAll()
        expect { bob.exile has tusker }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { bob.exile has tusker }
        advanceTo(TurnStep.End, turn = 2)
        resolveAll()
        expect { bob.battlefield has tusker }
    }

    // CR 701.21a, 101.4: Alice chooses one creature for each of three players; every other creature is sacrificed
    @Test
    fun roamerKeepsOneCreatureForEachPlayer() = scenario(players = 3) {
        alice.battlefield(roamer, tusker, hulk, counters = listOf(CounterKind.Loyalty to 5))
        bob.battlefield(lumbering, centurion)
        carol.battlefield(ruk)
        alice.activates(roamer, ability = 2)
        playOut(hulk, centurion, ruk)
        expect {
            alice.battlefield has hulk
            alice.graveyard has tusker
            bob.battlefield has centurion
            bob.graveyard has lumbering
            carol.battlefield has ruk
            alice.battlefield has roamer
        }
    }

    // CR 118.3, 707.9b: 2 life paid and the card exiled; the token is a 1/1 copy with toxic 1
    @Test
    fun kinzarPaysLifeAndCopiesTheDeadCreature() = scenario {
        alice.battlefield(kinzar, tusker, "Mountain")
        alice.hand(jolt)
        alice.casts(jolt, target = tusker)
        playOut()
        val copy = tokens(tusker).single()
        expect {
            alice.life shouldBe 18
            alice.exile has tusker
            CardRef(copy, "copy").power shouldBe 1
            CardRef(copy, "copy").toughness shouldBe 1
            CardRef(copy, "copy") hasKeyword Keyword.Toxic.Kind
        }
    }

    // CR 101.2: the card stays in the graveyard; CR 601.3: a noncreature card can't be cast from the graveyard
    @Test
    fun jailkeeperKeepsCardsInGraveyards() = scenario {
        alice.battlefield(jailkeeper, "Plains", "Plains", "Mountain")
        alice.graveyard(tusker, spark)
        alice.hand(field)
        alice.casts(field, target = tusker)
        resolveAll()
        expect {
            alice.graveyard has tusker
            alice.graveyard has field
        }
        assertFails { alice.casts(spark, target = "bob") }
    }

    // CR 118.3: with one oil counter the two can't be removed, so no reflexive trigger
    @Test
    fun centurionNeedsTwoOilCounters() = scenario {
        alice.battlefield(centurion, counters = listOf(oil to 1))
        bob.battlefield(tusker)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(centurion)
        resolveAll()
        expect { centurion.counters(oil) shouldBe 1 }
    }

    // CR 603.12: two oil counters removed, then the target can't block
    @Test
    fun centurionStopsABlocker() = scenario {
        alice.battlefield(centurion, counters = listOf(oil to 2))
        bob.battlefield(tusker)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(centurion)
        playOut(tusker)
        expect { centurion.counters(oil) shouldBe 0 }
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(tusker to centurion) }
    }

    // CR 606.3: after returning the creature that dealt damage, Kaiso's loyalty abilities may be activated twice this turn, not three times
    @Test
    fun kaisoActivatesTwiceAfterReturningAnAttacker() = scenario {
        alice.battlefield(kaiso, tusker, counters = listOf(CounterKind.Loyalty to 3))
        alice.library("Island", "Island", "Island")
        hits(tusker)
        playOut(tusker)
        expect { alice.hand has tusker }
        advanceTo(TurnStep.PostcombatMain)
        alice.activates(kaiso, ability = 1)
        resolveAll()
        alice.activates(kaiso, ability = 1)
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
        assertFails { alice.activates(kaiso, ability = 1) }
    }

    // CR 608.2c: with X = 5 the creature card goes onto the battlefield and the land card into the hand; the rest go to the bottom
    @Test
    fun duskPutsTheChosenCreatureOntoTheBattlefield() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(dusk)
        alice.library("Island", tusker, "Plains", "Swamp", "Mountain", "Island", "Swamp")
        alice.casts(dusk, x = 5)
        allPass()
        answer(Answer.Objects(listOf(card(tusker, zone = Zone.Library))))
        answer(Answer.Objects(listOf(card("Plains", zone = Zone.Library))))
        answer(Answer.Objects(listOf(card(tusker, zone = Zone.Library))))
        resolveAll()
        expect {
            alice.battlefield has tusker
            alice.hand has "Plains"
            alice.library.size shouldBe 5
        }
    }

    // CR 120.3: 3 damage from Alice's spell, so Alice (the spell's controller as it last existed, CR 608.2h) sacrifices three permanents
    @Test
    fun annihilatorMakesTheSourcesControllerSacrifice() = scenario {
        alice.battlefield("Mountain", "Plains", "Plains", "Plains", tusker)
        alice.hand(jolt)
        bob.battlefield(annihilator)
        alice.casts(jolt, target = annihilator)
        playOut("Plains", "Plains", "Plains")
        expect {
            alice.battlefield.size shouldBe 2
            alice.battlefield has tusker
            bob.battlefield has annihilator
        }
    }

    // CR 603.12: the discarded two-mana card lets the reflexive trigger destroy a permanent with mana value 2 or less
    @Test
    fun masticoreDestroysByTheDiscardedCardsManaValue() = scenario {
        alice.battlefield(masticore)
        alice.hand(tusker)
        bob.battlefield(trinket, hulk)
        aliceUpkeep()
        playOut(tusker, trinket)
        expect {
            alice.battlefield has masticore
            alice.graveyard has tusker
            bob.graveyard has trinket
            bob.battlefield has hulk
        }
    }

    // CR 608.2h: the Equipment that was attached to the creature as it died is attached to Ruk
    @Test
    fun rukTakesTheEquipmentOfACreatureThatDied() = scenario {
        alice.battlefield(ruk, tusker, spur, "Mountain")
        alice.hand(jolt)
        attach(spur, tusker)
        alice.casts(jolt, target = tusker)
        playOut()
        expect {
            alice.graveyard has tusker
            ruk.power shouldBe 3
        }
    }

    // CR 603.2c: two counters put on one creature: 2 damage to the target opponent
    @Test
    fun allBecomeOneDealsDamageForTheCounters() = scenario {
        alice.battlefield(allOne, tusker, "Forest")
        alice.hand(brawn)
        alice.casts(brawn, target = tusker)
        playOut("bob")
        expect {
            tusker.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            bob.life shouldBe 18
        }
    }
}
