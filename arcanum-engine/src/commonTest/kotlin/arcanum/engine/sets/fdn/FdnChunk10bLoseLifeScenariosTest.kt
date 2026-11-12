package arcanum.engine.sets.fdn

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/** Chunk 10b played: the invented names carry the oracle text of Curator of Destinies, Arbiter of Woe, Feed the Swarm, Herald of Eternal Dawn, Ravenous Amulet, Fog Bank and Soulstone Sanctuary. */
class FdnChunk10bLoseLifeScenariosTest {
    private val curator = Cards.invent("Warden of Fates", "{4}{U}{U}", "Creature — Sphinx", """
        This spell can't be countered.
        Flying
        When this creature enters, look at the top five cards of your library and separate them into a face-down pile and a face-up pile. An opponent chooses one of those piles. Put that pile into your hand and the other into your graveyard.
    """, "5/5")
    private val arbiter = Cards.invent("Judge of Sorrow", "{4}{B}{B}", "Creature — Demon", """
        As an additional cost to cast this spell, sacrifice a creature.
        Flying
        When this creature enters, each opponent discards a card and loses 2 life. You draw a card and gain 2 life.
    """, "5/4")
    private val feed = Cards.invent("Feed the Flock", "{1}{B}", "Sorcery",
        "Destroy target creature or enchantment an opponent controls. You lose life equal to that permanent's mana value.")
    private val herald = Cards.invent("Keeper of Endless Dusk", "{4}{W}{W}{W}", "Creature — Angel", """
        Flash (You may cast this spell any time you could cast an instant.)
        Flying
        You can't lose the game and your opponents can't win the game.
    """, "6/6")
    private val amulet = Cards.invent("Hungering Idol", "{2}", "Artifact", """
        {1}, {T}, Sacrifice a creature: Draw a card and put a soul counter on this artifact. Activate only as a sorcery.
        {4}, {T}, Sacrifice this artifact: Each opponent loses life equal to the number of soul counters on this artifact.
    """)
    private val fog = Cards.invent("Mist Barrier", "{1}{U}", "Creature — Wall", """
        Defender (This creature can't attack.)
        Flying
        Prevent all combat damage that would be dealt to and dealt by this creature.
    """, "0/2")
    private val grotto = Cards.invent("Ghostwalk Grotto", null, "Land", """
        {T}: Add {C}.
        {4}: This land becomes a 3/3 creature with vigilance and all creature types. It's still a land.
    """)
    private val victory = Cards.invent("Sudden Triumph", "{U}", "Instant", "You win the game.")
    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val brute = Cards.invent("Tallow Brute", "{2}{G}", "Creature — Ogre", "", "3/3")
    private val lackey = Cards.invent("Pit Lackey", "{B}", "Creature — Imp", "", "1/1")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 701.20: Alice separates the top five; Bob picks the second pile, which goes to her hand, the rest to the graveyard
    @Test
    fun curatorGivesTheChosenPileToHandAndTheRestToGraveyard() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(curator)
        alice.library("Forest", "Swamp", "Plains", "Mountain", "Market Bear")
        alice.casts(curator)
        resolveAll()
        alice.chooses("Forest", "Swamp")
        bob.answers(Answer.Option("second"))
        resolveAll()
        expect {
            alice.hand has "Plains"
            alice.hand has "Mountain"
            alice.hand has bear
            alice.graveyard has "Forest"
            alice.graveyard has "Swamp"
        }
    }

    // CR 119.3, 121.1, 119.1: Bob discards and loses 2 life (the "and" keeps the subject), Alice draws and gains 2
    @Test
    fun arbiterDrainsTheOpponentAndDrawsForItsController() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", lackey)
        alice.hand(arbiter)
        alice.library("Island")
        bob.hand("Mountain")
        alice.casts(arbiter)
        resolveAll()
        expect {
            alice.life shouldBe 22
            bob.life shouldBe 18
            bob.graveyard has "Mountain"
            alice.hand has "Island"
            alice.graveyard has lackey
        }
    }

    // CR 608.2h: the life lost is the destroyed permanent's mana value as it last existed
    @Test
    fun feedTheSwarmLosesLifeEqualToManaValue() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(feed)
        bob.battlefield(brute)
        alice.casts(feed, target = brute)
        resolveAll()
        expect {
            bob.graveyard has brute
            alice.life shouldBe 17
        }
    }

    // CR 104.3b, 104.2b: at 0 life Alice stays in the game, and Bob's "you win the game" does nothing
    @Test
    fun heraldKeepsItsControllerInTheGameAndStopsOpponentWins() = scenario {
        alice.battlefield(herald)
        alice.life = 0
        bob.battlefield("Island")
        bob.hand(victory)
        alice.passes()
        bob.casts(victory)
        resolveAll()
        expect {
            state.player(alice.id).lost shouldBe false
            state.player(bob.id).lost shouldBe false
        }
    }

    // CR 104.2b: without the Herald the same win effect makes Alice lose
    @Test
    fun withoutHeraldTheOpponentWins() = scenario {
        alice.battlefield("Island")
        bob.battlefield("Island")
        bob.hand(victory)
        alice.passes()
        bob.casts(victory)
        resolveAll()
        expect { state.player(alice.id).lost shouldBe true }
    }

    // CR 602.5d, 122.1, 119.3: the first ability draws and puts a soul counter
    @Test
    fun amuletPutsASoulCounterAndDraws() = scenario {
        alice.battlefield(amulet, bear, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.library("Island", "Island")
        alice.activates(amulet, ability = 0, paying = listOf("Forest"))
        resolveAll()
        expect {
            amulet.counters(CounterKind.Named("soul")) shouldBe 1
            alice.hand has "Island"
        }
    }

    // CR 119.3, 122.1: sacrificing it drains one life per soul counter it had
    @Test
    fun amuletDrainsForEachSoulCounter() = scenario {
        alice.battlefield(amulet, counters = listOf(CounterKind.Named("soul") to 3))
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.activates(amulet, ability = 1, paying = listOf("Forest", "Forest", "Forest", "Forest"))
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    // CR 615.1, 510.2: the 2/4 Wall takes no combat damage and deals none, and the Bear lives
    @Test
    fun fogBankPreventsCombatDamageToAndByIt() = scenario {
        alice.battlefield(bear)
        bob.battlefield(counters = listOf(CounterKind.PowerToughness(1, 1) to 2), names = arrayOf(fog))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(fog to bear)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Battlefield
            card(bear).view.obj!!.damage shouldBe 0
            card(fog).view.obj!!.damage shouldBe 0
        }
    }

    // CR 611.2a, 613.4b, 205.1b: for good a 3/3 vigilance creature with every creature type, still a land
    @Test
    fun soulstoneBecomesACreatureForGood() = scenario {
        alice.battlefield(grotto, "Forest", "Forest", "Forest", "Forest")
        alice.activates(grotto, ability = 1, paying = listOf("Forest", "Forest", "Forest", "Forest"))
        resolveAll()
        advanceTo(TurnStep.End)
        expect {
            grotto.power shouldBe 3
            grotto.toughness shouldBe 3
            grotto.types shouldBe listOf(CardType.Land, CardType.Creature)
        }
    }
}
