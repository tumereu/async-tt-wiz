package arcanum.engine.sets.woe

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.abilityIndex
import arcanum.engine.sets.ltr.aliceUpkeep
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.tokens
import arcanum.engine.sets.ltr.unblocked
import arcanum.engine.sets.rtr.resolveAll
import arcanum.engine.sets.spm.loreChapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** WOE chunk 03, Food: the invented names carry the oracle text of the cards that need something the engine tests haven't played. */
class WoeChunk03FoodScenariosTest {
    private val trinket = Cards.invent("Brass Trinket", "{1}", "Artifact")
    private val candymaw = Cards.invent("Ravenous Candymaw", "{2}{B}{B}", "Creature — Horror", """
        Menace, trample
        At the beginning of your upkeep, you may sacrifice an artifact, enchantment, or token. If you don't, tap this creature.
    """.trimIndent(), "6/6")
    private val sip = Cards.invent("Sip of Syrup", "{B}", "Instant", "You gain 3 life.")
    private val brute = Cards.invent("Mudback Brute", "{3}{R}", "Creature — Beast", "", "4/4")
    private val alchemist = Cards.invent("Syrup Alchemist", "{2}{B}", "Creature — Human Warlock", """
        Lifelink
        When this creature enters, up to one target creature gets -X/-X until end of turn, where X is the amount of life you gained this turn.
    """.trimIndent(), "3/2")
    private val matron = Cards.invent("The Gander Matron", "{X}{G}{U}", "Legendary Creature — Bird Hydra", """
        Flying
        The Gander Matron enters with X +1/+1 counters on it.
        When The Gander Matron enters, create half X Food tokens, rounded up.
        Whenever The Gander Matron attacks, you may sacrifice a Food. If you do, draw a card.
    """.trimIndent(), "2/2")
    private val dusk = Cards.invent("Dusk of the Pantry's Wrath", "{3}{G}", "Enchantment", """
        When this enchantment enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        Foods you control have "{T}: Add {G}."
    """.trimIndent())
    private val biscuit = Cards.invent("Hard Biscuit", "{1}{G}", "Artifact Creature — Food Golem", """
        {2}{G}: Until end of turn, target noncreature artifact you control becomes a 4/4 artifact creature.
    """.trimIndent(), "2/2")
    private val hut = Cards.invent("Fidgety Hut", null, "Land", """
        This land enters tapped.
        {T}: Add {B} or {G}.
        {2}{B}{G}: This land becomes a 4/4 black and green Horror creature until end of turn. It's still a land.
        Whenever this land attacks, create a Food token and exile up to one target card from a graveyard.
    """.trimIndent())
    private val saga = Cards.invent("Greetings to Candyville", "{1}{G}", "Enchantment — Saga", """
        I — Create a 1/1 white Human creature token.
        II — Create a Food token.
        III — Put X +1/+1 counters on target creature you control, where X is one plus the number of Foods you control.
    """.trimIndent())
    private val loaf = Cards.invent("Stale Loaf", "{0}", "Artifact — Food", "{2}, {T}, Sacrifice this artifact: You gain 3 life.")
    private val pie = Cards.invent("Old Pie", "{0}", "Artifact — Food", "{2}, {T}, Sacrifice this artifact: You gain 3 life.")
    private val bear = Cards.invent("Hedge Bear", "{1}{G}", "Creature — Bear", "", "2/2")

    // CR 118.12: the sacrificed permanent is the payment; "If you don't" then doesn't apply
    @Test
    fun candymawStaysUntappedWhenSomethingIsSacrificed() = scenario {
        alice.battlefield(candymaw, trinket)
        aliceUpkeep()
        playOut("Brass Trinket")
        expect {
            trinket.zone shouldBe Zone.Graveyard
            candymaw.tapped shouldBe false
        }
    }

    // CR 118.12: declining the optional sacrifice is "not paid"
    @Test
    fun candymawTapsWhenTheSacrificeIsDeclined() = scenario {
        alice.battlefield(candymaw, trinket)
        aliceUpkeep()
        playOut(may = false)
        expect {
            trinket.zone shouldBe Zone.Battlefield
            candymaw.tapped shouldBe true
        }
    }

    // CR 118.12: with nothing to sacrifice the payment can't be made, so it is "not paid" (the Candymaw is a creature only)
    @Test
    fun candymawTapsWithNothingToSacrifice() = scenario {
        alice.battlefield(candymaw)
        aliceUpkeep()
        playOut()
        expect { candymaw.tapped shouldBe true }
    }

    // CR 119.3 (life gained this turn), 107.3a: X is read as the ability resolves
    @Test
    fun syrupAlchemistShrinksByTheLifeGainedThisTurn() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(sip, alchemist)
        bob.battlefield(brute)
        alice.casts(sip)
        resolveAll()
        alice.casts(alchemist)
        playOut("Mudback Brute")
        expect {
            alice.life shouldBe 23
            brute.power shouldBe 1
            brute.toughness shouldBe 1
        }
    }

    @Test
    fun syrupAlchemistDoesNothingWithoutLifeGained() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(alchemist)
        bob.battlefield(brute)
        alice.casts(alchemist)
        playOut("Mudback Brute")
        expect {
            brute.power shouldBe 4
            brute.toughness shouldBe 4
        }
    }

    // CR 107.3m (X as it was cast), 107.1a (half rounded up: 3 -> 2); counters as it enters (CR 122.6)
    @Test
    fun gooseMotherMakesHalfXFoodsRoundedUp() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Island")
        alice.hand(matron)
        alice.casts(matron, x = 3)
        playOut()
        expect {
            matron.counters(CounterKind.PowerToughness(1, 1)) shouldBe 3
            matron.power shouldBe 5
        }
        assertEquals(2, tokens("Food Token").size)
    }

    // CR 107.1a: half of 1 rounded up is 1
    @Test
    fun gooseMotherRoundsOneHalfUp() = scenario {
        alice.battlefield("Forest", "Forest", "Island")
        alice.hand(matron)
        alice.casts(matron, x = 1)
        playOut()
        assertEquals(1, tokens("Food Token").size)
    }

    // CR 613.1f, 605.1a: the Food gets a mana ability from the enchantment; CR 111.10b
    @Test
    fun foodsTapForMana() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(dusk)
        alice.casts(dusk)
        playOut()
        val food = tokens("Food Token").single()
        val ability = 1 // after its own "{2}, {T}, Sacrifice" ability
        alice.activates("Food Token", ability = ability)
        expect { alice.pool.size shouldBe 1 }
        assertTrue(state.obj(food)!!.tapped)
    }

    // CR 613.1d, 611.2a: a noncreature artifact becomes an artifact creature until end of turn
    @Test
    fun hardBiscuitAnimatesAnArtifact() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", biscuit, trinket)
        alice.activates(biscuit, ability = 0, target = "Brass Trinket")
        resolveAll()
        expect {
            trinket.types.contains(CardType.Creature) shouldBe true
            trinket.power shouldBe 4
            trinket.toughness shouldBe 4
        }
    }

    // CR 614.12: a land that enters tapped
    @Test
    fun fidgetyHutEntersTapped() = scenario {
        alice.hand(hut)
        alice.plays(hut)
        expect { hut.tapped shouldBe true }
    }

    // CR 508.1, 701.5? no: its attack trigger makes a Food and exiles a card from a graveyard
    @Test
    fun fidgetyHutAttackMakesFoodAndExilesFromAGraveyard() = scenario {
        alice.battlefield(hut, "Swamp", "Forest", "Swamp", "Forest")
        bob.graveyard(trinket)
        val animate = abilityIndex("Fidgety Hut", "becomes a 4/4")
        alice.activates(hut, ability = animate)
        resolveAll()
        unblocked("Fidgety Hut", picks = listOf("Brass Trinket"))
        expect { trinket.zone shouldBe Zone.Exile }
        assertEquals(1, tokens("Food Token").size)
    }

    // CR 107.1: one plus the number of Foods you control
    @Test
    fun greetingsPutsOnePlusFoodsCounters() = scenario {
        alice.battlefield(bear, loaf, pie)
        loreChapter(saga, 3)
        playOut("Hedge Bear")
        expect { bear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 3 }
    }
}
