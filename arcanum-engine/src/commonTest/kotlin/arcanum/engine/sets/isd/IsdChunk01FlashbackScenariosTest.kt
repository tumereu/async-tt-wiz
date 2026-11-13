package arcanum.engine.sets.isd

import arcanum.api.CardType
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** ISD chunk 01: flashback cards whose effects the engine hadn't played (CR 702.34a: cast from the graveyard for the flashback cost, then
 * exiled, CR 118.9). Invented names carry the oracle text of the real cards. */
class IsdChunk01FlashbackScenariosTest {
    private val tail = "(You may cast this card from your graveyard for its flashback cost. Then exile it.)"

    private val resurgence = Cards.invent("Slow Resurgence", "{3}{G}{G}", "Sorcery", """
        Choose a permanent type. Return all cards of the chosen type from your graveyard to your hand.
        Flashback {5}{G}{G} $tail
    """)
    private val babbling = Cards.invent("Frantic Babbling", "{1}{R}", "Instant", """
        Draw two cards, then discard a card at random.
        Flashback {2}{U} $tail
    """)
    private val culling = Cards.invent("Heavenly Culling", "{2}{W}{W}", "Sorcery", """
        Each player chooses a creature they control. Destroy the rest.
        Flashback {5}{W}{W} $tail
    """)
    private val path = Cards.invent("Recollection's Path", "{1}{U}", "Instant", """
        Target player shuffles up to three target cards from their graveyard into their library.
        Flashback {G} $tail
    """)
    private val gambit = Cards.invent("Fiend's Gambit", "{X}{R}", "Sorcery", """
        Fiend's Gambit deals X damage to any target.
        Flashback {X}{R}{R}{R} $tail
    """)

    private val hound = Cards.invent("Hollowmoor Hound", "{1}{G}", "Creature — Dog", "", "2/2")
    private val crow = Cards.invent("Hollowmoor Crow", "{1}{B}", "Creature — Bird", "", "1/1")
    private val ghoul = Cards.invent("Hollowmoor Ghoul", "{2}{B}", "Creature — Zombie", "", "2/2")
    private val bat = Cards.invent("Hollowmoor Bat", "{B}", "Creature — Bat", "", "1/1")
    private val lantern = Cards.invent("Hollowmoor Lantern", "{2}", "Artifact Creature — Construct", "", "1/1")
    private val totem = Cards.invent("Hollowmoor Totem", "{2}", "Artifact", "")
    private val charm = Cards.invent("Hollowmoor Charm", "{U}", "Instant", "")

    /** Only permanent types are offered; an artifact creature card is a creature card too, a land stays behind. */
    // CR 110.4 (the six permanent types), 205.2a, 608.2d (chosen as it resolves)
    @Test
    fun resurgenceReturnsEveryCardOfTheChosenPermanentType() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(resurgence)
        alice.graveyard(hound, lantern, totem, charm, "Forest")
        alice.casts(resurgence)
        resolveAll()
        val d = decision<Decision.ChooseCardType>()
        assertEquals(listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Land, CardType.Planeswalker).toSet(), d.among.toSet())
        answer(Answer.CardTypeChoice(CardType.Creature))
        resolveAll()
        expect {
            alice.hand has hound
            alice.hand has lantern
            alice.graveyard has totem
            alice.graveyard has charm
            alice.graveyard has "Forest"
            resurgence.zone shouldBe Zone.Graveyard
        }
    }

    /** Cast again from the graveyard for {5}{G}{G}: it brings back the lands this time and is exiled. */
    // CR 702.34a, 608.2n
    @Test
    fun resurgenceFlashbackReturnsLandsAndIsExiled() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.graveyard(resurgence, hound, "Forest")
        alice.casts(resurgence)
        resolveAll()
        answer(Answer.CardTypeChoice(CardType.Land))
        resolveAll()
        expect {
            alice.hand has "Forest"
            alice.graveyard has hound
            resurgence.zone shouldBe Zone.Exile
        }
    }

    /** The discarded card is picked at random: the caster isn't asked which. */
    // CR 701.9b
    @Test
    fun babblingDrawsTwoThenDiscardsOneAtRandom() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(babbling, charm)
        alice.library("Island", "Plains")
        alice.casts(babbling)
        resolveAll()
        assertNull(pending)
        expect {
            alice.hand.size shouldBe 2
            alice.graveyard.size shouldBe 2
            alice.graveyard has babbling
        }
    }

    /** Three players each keep the creature they choose, in turn order; every other creature is destroyed at once. */
    // CR 101.4 (choices in turn order), 608.2e, 701.8a
    @Test
    fun cullingLeavesEachPlayerTheCreatureTheyChose() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", hound, crow)
        bob.battlefield(ghoul, bat)
        carol.battlefield(lantern)
        alice.hand(culling)
        alice.casts(culling)
        resolveAll()
        assertEquals(alice.id, decision<Decision.ChooseObjects>().player)
        alice.chooses(crow)
        assertEquals(bob.id, decision<Decision.ChooseObjects>().player)
        bob.chooses(ghoul)
        // Carol's only creature needs no choice.
        resolveAll()
        expect {
            alice.battlefield has crow
            alice.graveyard has hound
            bob.battlefield has ghoul
            bob.graveyard has bat
            carol.battlefield has lantern
        }
    }

    /** The cards go back into the target player's library, which is shuffled; a card left out stays. */
    // CR 115.1a, 400.3, 701.24a
    @Test
    fun pathShufflesTargetCardsIntoTheirOwnersLibrary() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(path)
        bob.graveyard(ghoul, bat, totem, charm)
        bob.library("Swamp")
        alice.casts(path, targets = listOf("bob", ghoul, bat, totem))
        resolveAll()
        expect {
            bob.library.size shouldBe 4
            bob.library has ghoul
            bob.library has bat
            bob.library has totem
            bob.graveyard has charm
        }
    }

    /** X is chosen for the flashback cost {X}{R}{R}{R} too: X=2 costs five mana and deals 2. */
    // CR 107.3a (X in an alternative cost), 118.9, 702.34a
    @Test
    fun gambitFlashbackPaysXInItsOwnCost() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard(gambit)
        alice.casts(gambit, target = "bob", x = 2)
        resolveAll()
        expect {
            bob.life shouldBe 18
            gambit.zone shouldBe Zone.Exile
        }
        assertEquals(5, state.battlefield.count { state.obj(it)?.tapped == true })
    }
}
