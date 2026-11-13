package arcanum.engine.sets.isd

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test

/** ISD chunk 13: an each-player discard with three players, a coin-flip destroy of the creature that was damaged, life gain from creatures
 * this one damaged, and static power/toughness and keyword grants to a group. Invented names carry the oracle text of the real cards. */
class IsdChunk13DiscardDrawLifeAndPtScenariosTest {
    private val edict = Cards.invent("Veiled Hexmistress", "{1}{B}{B}", "Legendary Planeswalker — Edict", """
        [+1]: Each player discards a card.
        [−2]: Target player sacrifices a creature.
        [−6]: Separate all permanents target player controls into two piles. That player sacrifices all permanents in the pile of their choice.
    """, null)
    private val puppet = Cards.invent("Grinning Puppet", "{5}", "Artifact Creature — Construct", """
        Indestructible
        Whenever this creature deals combat damage to a creature, flip a coin. If you win the flip, destroy that creature.
    """, "1/1")
    private val ghoul = Cards.invent("Gravel Ghoul", "{3}{B}", "Creature — Zombie", """
        First strike
        Whenever a creature dealt damage by this creature this turn dies, you gain life equal to that creature's toughness.
    """, "3/2")
    private val geist = Cards.invent("Hollow Spirit Guard", "{4}{U}", "Creature — Spirit", """
        Flying
        Other Spirit creatures you control get +1/+0.
    """, "3/3")
    private val virtue = Cards.invent("Hallowed Banner", "{1}{W}", "Enchantment", "Creature tokens you control get +1/+1 and have vigilance.", null)
    private val caller = Cards.invent("Wisp Caller", "{2}{W}", "Creature — Human", "When this creature enters, create a 1/1 white Soldier creature token.", "2/2")
    private val wisp = Cards.invent("Dim Wisp", "{1}{U}", "Creature — Spirit", "", "2/2")
    private val bear = Cards.invent("Brindle Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Fen Ox", "{2}{G}", "Creature — Ox", "", "2/4")
    private val mule = Cards.invent("Dun Mule", "{2}{G}", "Creature — Beast", "", "2/3")
    private val zap = Cards.invent("Cinder Jolt", "{R}", "Instant", "Cinder Jolt deals 2 damage to target creature.")
    private val card1 = Cards.invent("Spare Scroll", "{U}", "Sorcery", "Draw a card.")

    // CR 701.9a, 101.4: every player discards, the third one too; each loses their only card
    @Test
    fun hexmistressPlusOneMakesEveryPlayerDiscard() = scenario(players = 3) {
        alice.battlefield(edict)
        alice.hand(card1)
        bob.hand("Island")
        carol.hand("Swamp")
        alice.activates(edict, ability = 0)
        resolveAll()
        expect {
            alice.graveyard has card1
            bob.graveyard has "Island"
            carol.graveyard has "Swamp"
        }
        expect {
            alice.hand.isEmpty()
            bob.hand.isEmpty()
            carol.hand.isEmpty()
        }
    }

    // CR 705.1, 120.2: a won flip destroys the creature the Puppet damaged, not the indestructible Puppet
    @Test
    fun puppetDestroysTheCreatureItDamagedOnAWonFlip() = scenario {
        alice.battlefield(puppet)
        bob.battlefield(bear)
        random(Answer.CoinFlip(true))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(puppet)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(bear to puppet)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect {
            bob.graveyard has bear
            alice.battlefield has puppet
        }
    }

    // CR 705.1: a lost flip destroys nothing
    @Test
    fun puppetLeavesTheCreatureAfterALostFlip() = scenario {
        alice.battlefield(puppet)
        bob.battlefield(bear)
        random(Answer.CoinFlip(false))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(puppet)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(bear to puppet)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect {
            bob.battlefield has bear
            alice.battlefield has puppet
        }
    }

    // CR 510.2, 700.4, 608.2h: the blocker dies of the first-strike damage; its toughness (3) is gained
    @Test
    fun ghoulGainsTheToughnessOfACreatureItKilled() = scenario {
        alice.battlefield(ghoul)
        bob.battlefield(mule)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ghoul)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(mule to ghoul)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect {
            bob.graveyard has mule
            alice.life shouldBe 23
        }
    }

    // CR 603.2: a creature the Ghoul damaged earlier this turn that dies later (to a spell) still counts; its toughness (4) is gained
    @Test
    fun ghoulCountsACreatureThatDiesLaterThisTurn() = scenario {
        alice.battlefield(ghoul, "Mountain")
        alice.hand(zap)
        bob.battlefield(ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ghoul)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(ox to ghoul)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        alice.casts(zap, target = ox)
        resolveAll()
        expect {
            bob.graveyard has ox
            alice.life shouldBe 24
        }
    }

    // CR 603.2: a creature the Ghoul never damaged dies: no life
    @Test
    fun ghoulIgnoresACreatureItDidNotDamage() = scenario {
        alice.battlefield(ghoul, "Mountain")
        alice.hand(zap)
        bob.battlefield(bear)
        alice.casts(zap, target = bear)
        resolveAll()
        expect {
            bob.graveyard has bear
            alice.life shouldBe 20
        }
    }

    // CR 613.4c, 205.3m: only the other Spirits of the controller get +1/+0
    @Test
    fun spiritLordPumpsOtherSpiritsOnly() = scenario {
        alice.battlefield(geist, wisp, bear)
        bob.battlefield(Cards.invent("Foe Wisp", "{1}{U}", "Creature — Spirit", "", "2/2"))
        expect {
            geist.power shouldBe 3
            wisp.power shouldBe 3
            wisp.toughness shouldBe 2
            bear.power shouldBe 2
            "Foe Wisp".power shouldBe 2
        }
    }

    // CR 111.1, 613.4c, 613.1f: the token gets +1/+1 and vigilance, a nontoken creature neither
    @Test
    fun virtueHelpsTokensOnly() = scenario {
        alice.battlefield(virtue, bear)
        alice.hand(caller)
        alice.battlefield("Plains", "Plains", "Plains")
        alice.casts(caller)
        resolveAll()
        expect {
            "Soldier Token".power shouldBe 2
            "Soldier Token".toughness shouldBe 2
            "Soldier Token" hasKeyword Keyword.Vigilance.kind
            bear.power shouldBe 2
            bear.toughness shouldBe 2
            bear lacksKeyword Keyword.Vigilance.kind
        }
    }
}
