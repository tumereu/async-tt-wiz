package arcanum.engine.sets.mh3

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.dft.yes
import arcanum.engine.sets.ltr.alternativeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The MH3 cards of chunk 07, played: the invented names carry the oracle text of Spymaster's Vault, Arna Kennerüd, Skycaptain, Fangs of
 * Kalonia, Crabomination, Herigast, Erupting Nullkite, Dog Umbra, Shadow of the Second Sun and Amphibian Downpour.
 */
class Mh3Chunk07ConniveEmergeAurasScenariosTest {
    private val cellar = Cards.invent("Informant's Cellar", null, "Land", """
        This land enters tapped unless you control a Swamp.
        {T}: Add {B}.
        {B}, {T}: Target creature you control connives X, where X is the number of creatures that died this turn. (Draw X cards, then discard X cards. Put a +1/+1 counter on that creature for each nonland card discarded this way.)
    """)
    private val velka = Cards.invent("Velka Strandmoor, Skywarden", "{2}{W}{U}{B}", "Legendary Creature — Human Knight", """
        Flying, lifelink
        Ward—Discard a card.
        Whenever a modified creature you control attacks, double the number of each kind of counter on it. Then for each nontoken permanent attached to it, create a token that's a copy of that permanent attached to that creature.
    """, "4/4")
    private val tusks = Cards.invent("Tusks of the Grove", "{1}{G}", "Sorcery", """
        Put a +1/+1 counter on target creature you control, then double the number of +1/+1 counters on each creature that had a +1/+1 counter put on it this way.
        Overload {4}{G}{G} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val claws = Cards.invent("Clawmonstrosity", "{4}{B}{B}", "Creature — Crab Demon", """
        Emerge from artifact {5}{B}{B} (You may cast this spell by sacrificing an artifact and paying the emerge cost reduced by that artifact's mana value.)
        When this creature enters, target opponent exiles the top card of their library, a card at random from their graveyard, and a card at random from their hand. You may cast a spell from among cards exiled this way without paying its mana cost.
    """, "5/5")
    private val vorlath = Cards.invent("Vorlath, Rising Nullwyrm", "{9}", "Legendary Creature — Eldrazi Dragon", """
        Emerge {6}{R}{R} (You may cast this spell by sacrificing a creature and paying the emerge cost reduced by that creature's mana value.)
        When you cast this spell, you may exile your hand. If you do, draw three cards.
        Flying
        Each creature spell you cast has emerge. The emerge cost is equal to its mana cost.
    """, "6/6")
    private val houndShade = Cards.invent("Hound Shade", "{1}{W}", "Enchantment — Aura", """
        Flash
        Enchant creature
        As long as another player controls enchanted creature, it can't attack or block. Otherwise, this Aura has umbra armor. (If enchanted creature would be destroyed, instead remove all damage from it and destroy this Aura.)
    """)
    private val echo = Cards.invent("Echo of the Twin Dawn", "{4}{U}{U}", "Enchantment — Aura", """
        Enchant player
        At the beginning of each of enchanted player's postcombat main phases, there is an additional beginning phase after this phase. (The end step happens after the added untap, upkeep, and draw steps.)
    """)

    private val toadfall = Cards.invent("Toadfall Torrent", "{2}{U}", "Enchantment — Aura", """
        Flash
        Storm (When you cast this spell, copy it for each spell cast before it this turn. You may choose new targets for the copies. Copies become tokens.)
        Enchant creature
        Enchanted creature loses all abilities and is a blue Frog creature with base power and toughness 1/1.
    """)

    private val cinder = Cards.invent("Stray Cinder", "{R}", "Instant", "Stray Cinder deals 2 damage to any target.")
    private val lance = Cards.invent("Doom Lance", "{1}{B}", "Instant", "Destroy target creature.")
    private val bear = Cards.invent("Reaping Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val pest = Cards.invent("Ember Pest", "{1}{R}", "Creature — Lizard", "", "2/1")
    private val rat = Cards.invent("Cinder Rat", "{B}", "Creature — Rat", "", "1/1")
    private val ogre = Cards.invent("Big Ogre", "{4}{R}", "Creature — Ogre", "", "4/4")
    private val anvil = Cards.invent("Rust Anvil", "{3}", "Artifact", "")
    private val mantle = Cards.invent("Gilded Mantle", "{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +1/+1.
    """)
    private val spur = Cards.invent("Brass Spur", "{1}", "Artifact — Equipment", """
        Equipped creature gets +1/+0.
        Equip {1}
    """)

    // CR 701.50d: connive 2, as two creatures died this turn (an opponent's count too, CR 700.4): two cards drawn and discarded, a counter for
    // the one nonland card
    @Test
    fun connivesForEachCreatureThatDied() = scenario {
        alice.battlefield(cellar, "Swamp", "Mountain", "Mountain", bear)
        alice.hand(cinder, cinder)
        alice.library(lance, "Forest")
        bob.battlefield(pest, rat)
        alice.casts(cinder, target = pest)
        allPass()
        alice.casts(cinder, target = rat)
        allPass()
        alice.activates(cellar, ability = 1, target = bear)
        resolveAll()
        expect {
            bob.graveyard.size shouldBe 2
            alice.hand.size shouldBe 0
            alice.graveyard has lance
            alice.graveyard has "Forest"
            bear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // CR 701.10e: the attacking modified creature's two +1/+1 counters become four; CR 707.2, 303.4f, 301.5c: a token copy of its Aura and of
    // its Equipment is created attached to it
    @Test
    fun attackDoublesCountersAndCopiesAttachments() = scenario {
        alice.battlefield(velka, mantle, spur)
        alice.battlefield(bear, counters = listOf(CounterKind.PowerToughness(1, 1) to 2))
        attach(mantle, bear)
        attach(spur, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == mantle } shouldBe 2
            alice.battlefield.names.count { it == spur } shouldBe 2
            card(bear).power shouldBe 2 + 4 + 2 + 2
            card(bear).toughness shouldBe 2 + 4 + 2
        }
    }

    // CR 702.96b: overloaded, each creature you control gets a counter, then each of them has its +1/+1 counters doubled (1 + 1 → 4, 0 + 1 → 2);
    // the opponent's creature is untouched
    @Test
    fun overloadedDoublesEachCreatureGivenACounter() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", pest)
        alice.battlefield(bear, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.hand(tusks)
        bob.battlefield(ogre, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.casts(tusks, way = alternativeOf(tusks, Keyword.Overload.Kind))
        allPass()
        expect {
            bear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 4
            pest.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            ogre.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // CR 702.119b: emerge from artifact: the artifact (mana value 3) is sacrificed and {5}{B}{B} is reduced to {2}{B}{B}; CR 608.2g: the
    // opponent's exiled creature card is cast for free and comes under the caster's control
    @Test
    fun emergesFromAnArtifactAndCastsAnExiledCard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", anvil)
        alice.hand(claws)
        bob.library("Forest")
        bob.graveyard("Island")
        bob.hand(pest)
        alice.casts(claws, way = alternativeOf(claws, Keyword.Emerge.Kind), choosing = listOf(anvil))
        allPass()
        resolveAll()
        alice.chooses(pest)
        resolveAll()
        expect {
            alice.battlefield has claws
            alice.graveyard has anvil
            bob.exile has "Forest"
            bob.exile has "Island"
            pest.zone shouldBe Zone.Battlefield
            pest.controller shouldBe alice.id
        }
    }

    // CR 702.119a: emerge, sacrificing a mana value 5 creature ({6}{R}{R} less 5); CR 118.12: the cast trigger exiles the hand and draws three
    @Test
    fun emergesAndTradesTheHandForThreeCards() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", ogre)
        alice.hand(vorlath, "Island")
        alice.library("Forest", "Forest", "Forest")
        alice.casts(vorlath, way = alternativeOf(vorlath, Keyword.Emerge.Kind), choosing = listOf(ogre))
        resolveAll()
        yes()
        resolveAll()
        expect {
            alice.battlefield has vorlath
            alice.graveyard has ogre
            alice.exile has "Island"
            alice.hand.size shouldBe 3
        }
    }

    // CR 702.119a: the creature spell has emerge for its own mana cost: {4}{R} less the sacrificed creature's mana value 2
    @Test
    fun givesCreatureSpellsEmergeForTheirManaCost() = scenario {
        alice.battlefield(vorlath, "Mountain", "Mountain", "Mountain", bear)
        alice.hand(ogre)
        alice.casts(ogre, way = alternativeOf(ogre, Keyword.Emerge.Kind), choosing = listOf(bear))
        allPass()
        expect {
            alice.battlefield has ogre
            alice.graveyard has bear
        }
    }

    // CR 702.89a: while its controller controls the creature, the Aura has umbra armor: the destruction destroys the Aura instead
    @Test
    fun umbraArmorOnYourOwnCreature() = scenario {
        alice.battlefield(bear, houndShade)
        attach(houndShade, bear)
        bob.battlefield("Swamp", "Swamp")
        bob.hand(lance)
        turn(priority = bob)
        bob.casts(lance, target = bear)
        allPass()
        expect {
            alice.battlefield has bear
            alice.graveyard has houndShade
        }
    }

    // CR 611.3a: on another player's creature, that creature can't attack
    @Test
    fun anotherPlayersCreatureCantAttack() = scenario {
        alice.battlefield(houndShade)
        bob.battlefield(bear)
        attach(houndShade, bear)
        bob.library("Forest", "Forest")
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { bob.attacks(bear) }
    }

    // CR 500.8, 501.1: after the postcombat main phase, an additional beginning phase: an untap step untaps the lands, a draw step draws a
    // card; then the end step of the same turn
    @Test
    fun additionalBeginningPhaseAfterThePostcombatMainPhase() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(echo)
        alice.library("Forest", "Forest", "Forest")
        bob.library("Forest", "Forest")
        turn(number = 3)
        alice.casts(echo, target = "alice")
        allPass()
        advanceTo(TurnStep.End)
        expect {
            alice.hand.size shouldBe 1
            (0 until 6).count { card("Island", it).tapped } shouldBe 0
        }
        assertEquals(3, state.turn.number)
    }

    // CR 702.40a: storm copies the Aura spell for the one spell cast before it, the copy given a new target (CR 707.10c); the copy resolves as
    // a token Aura (CR 707.10f, 303.4f): each enchanted creature is a 1/1 blue Frog (layers 4, 5, 7b)
    @Test
    fun stormCopiesOfAnAuraSpellBecomeTokenAuras() = scenario {
        alice.battlefield("Island", "Island", "Island", "Mountain")
        alice.hand(cinder, toadfall)
        bob.battlefield(ogre, bear)
        alice.casts(cinder, target = "bob")
        allPass()
        alice.casts(toadfall, target = ogre)
        resolveAll()
        yes()
        targets(bear)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == toadfall } shouldBe 2
            card(ogre).power shouldBe 1
            card(ogre).toughness shouldBe 1
            card(bear).power shouldBe 1
            card(bear).colors shouldBe listOf(Color.Blue)
            card(bear).subtypes shouldBe listOf(Subtype("Frog"))
            card(bear).types shouldBe listOf(CardType.Creature)
        }
    }
}
