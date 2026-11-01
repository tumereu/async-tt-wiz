package arcanum.engine.cube

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** The cube's chunk 42 (sacrifice engines and creatures that search), played as a player would. */
class Chunk42SacrificeEnginesAndTutorCreaturesScenariosTest {
    private val bear = Cards.invent("Mossback Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Hulking Mudogre", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val corpse = Cards.invent("Shambling Corpse", "{1}{B}", "Creature — Zombie", "", "2/2")
    private val idol = Cards.invent("Brass Idol", "{2}", "Artifact")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    /** Chooses targets when asked; a slot with only as many legal targets as it needs is filled without asking (P5). */
    private fun Scenario.targets(vararg names: String) {
        if (pending is Decision.Targets) answer(Answer.Targets(names.map { target(it) }))
    }

    private fun Scenario.yes() = answer(Answer.YesNo(true))

    /** {G}, {T}, discard a creature card: search your library for a creature card to your hand. */
    @Test
    fun wildlifeShaman() = scenario {
        alice.library("Forest", ogre, "Island")
        alice.battlefield("Wildlife Shaman", "Forest")
        alice.hand(bear)

        alice.activates("Wildlife Shaman", choosing = listOf(bear))
        allPass()
        alice.chooses(ogre)
        expect {
            alice.graveyard has bear
            alice.hand.names shouldBe listOf(ogre)
            alice.library.size shouldBe 2
        }
    }

    /** +2/+2 with three or more land cards in your graveyard; {2}, {T}, sacrifice a land: search a land onto the
     * battlefield tapped. */
    @Test
    fun elvishSalvager() = scenario {
        alice.library("Island", "Plains")
        alice.battlefield("Elvish Salvager", "Swamp", "Swamp", "Swamp")
        alice.graveyard("Forest", "Forest")

        expect { "Elvish Salvager".power shouldBe 1 }
        alice.activates("Elvish Salvager", choosing = listOf("Swamp"))
        allPass()
        alice.chooses("Plains")
        expect {
            alice.battlefield has "Plains"
            "Plains".tapped shouldBe true
            // CR 613.4c: three land cards now
            "Elvish Salvager".power shouldBe 3
            "Elvish Salvager".toughness shouldBe 4
        }
    }

    /** +1/+1 for each land card in your graveyard; {T}, sacrifice a Forest or Plains: search a land onto the
     * battlefield. */
    @Test
    fun knightOfTheRelicVault() = scenario {
        alice.library("Island", "Swamp")
        alice.battlefield("Knight of the Relic Vault", "Plains", "Mountain")
        alice.graveyard("Forest")

        expect { "Knight of the Relic Vault".power shouldBe 3 }
        // The Plains is the only Forest or Plains: sacrificed without a choice
        alice.activates("Knight of the Relic Vault")
        allPass()
        alice.chooses("Island")
        expect {
            alice.battlefield has "Island"
            "Island".tapped shouldBe false
            "Knight of the Relic Vault".power shouldBe 4
        }
    }

    @Test
    fun knightOfTheRelicVaultNeedsAForestOrPlains() = scenario {
        alice.battlefield("Knight of the Relic Vault", "Mountain", "Swamp")
        assertFailsWith<IllegalArgumentException> { alice.activates("Knight of the Relic Vault") }
    }

    /** Vigilance; +1/+1 for each creature card in your graveyard; {T}, sacrifice another creature: a land onto the
     * battlefield tapped. */
    @Test
    fun wightOfTheRelicVault() = scenario {
        alice.library("Swamp")
        alice.battlefield("Wight of the Relic Vault", bear)

        alice.activates("Wight of the Relic Vault", choosing = listOf(bear))
        allPass()
        alice.chooses("Swamp")
        expect {
            alice.battlefield has "Swamp"
            "Swamp".tapped shouldBe true
            "Wight of the Relic Vault".power shouldBe 3
            "Wight of the Relic Vault" hasKeyword Keyword.Vigilance
        }
    }

    /** +1/+1 for each creature card in your graveyard; {X}{B/G}, {T}, sacrifice another creature: a creature card with
     * mana value X or less from your library onto the battlefield, as a sorcery. */
    @Test
    fun fiendSculptor() = scenario {
        alice.library(ogre, corpse, "Forest")
        alice.battlefield("Fiend Sculptor", bear, "Swamp", "Swamp", "Swamp")

        alice.activates("Fiend Sculptor", x = 2, choosing = listOf(bear))
        expect { "Fiend Sculptor".power shouldBe 2 }
        allPass()
        // The Ogre's mana value 4 is too much
        alice.chooses(corpse)
        expect {
            alice.battlefield has corpse
            alice.library.names.contains(ogre) shouldBe true
        }
    }

    @Test
    fun fiendSculptorOnlyAsASorcery() = scenario {
        alice.battlefield("Fiend Sculptor", bear, "Swamp", "Swamp")
        turn(2, active = bob)
        bob.passes()
        assertFailsWith<IllegalArgumentException> { alice.activates("Fiend Sculptor", x = 1, choosing = listOf(bear)) }
    }

    /** {1}{B}, {T}, discard a card: a 2/2 Zombie; tap three untapped Zombies you control: draw a card and lose 1 life. */
    @Test
    fun tombcracker() = scenario {
        alice.battlefield("Tombcracker", "Swamp", "Swamp", corpse, "Neckless Horseman")
        alice.hand("Forest")
        alice.library("Island")

        alice.activates("Tombcracker", choosing = listOf("Forest"))
        allPass()
        expect { alice.battlefield has "Zombie Token" }

        // CR 302.6: tapping Zombies isn't their {T} ability: the new token may help
        alice.activates("Tombcracker", ability = 1, choosing = listOf("Neckless Horseman", corpse, "Zombie Token"))
        allPass()
        expect {
            alice.hand has "Island"
            alice.life shouldBe 19
            "Zombie Token".tapped shouldBe true
        }
    }

    /** Enters tapped with six stun counters; sacrificing a land makes a tapped 4/4 Beast and untaps it; {4}, sacrifice a
     * land: gain 2 life. */
    @Test
    fun balothSovereign() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand("Baloth Sovereign")

        alice.casts("Baloth Sovereign")
        allPass()
        expect {
            "Baloth Sovereign".tapped shouldBe true
            "Baloth Sovereign".counters(CounterKind.Stun) shouldBe 6
        }

        alice.activates("Baloth Sovereign", choosing = listOf("Forest"))
        expect { stack.size shouldBe 2 }
        allPass()
        expect {
            alice.battlefield has "Beast Token"
            "Beast Token".tapped shouldBe true
            "Beast Token".power shouldBe 4
            // CR 122.1d: a stun counter is removed instead of untapping
            "Baloth Sovereign".tapped shouldBe true
            "Baloth Sovereign".counters(CounterKind.Stun) shouldBe 5
        }
        allPass()
        expect { alice.life shouldBe 22 }
    }

    /** Sacrificing an artifact or creature: a +1/+1 counter; {1}{B}{G}, sacrifice an artifact or creature: gain 1 life
     * and draw a card. */
    @Test
    fun famishedSquirrel() = scenario {
        alice.battlefield("Famished Squirrel", idol, "Swamp", "Forest", "Forest")
        alice.library("Island")

        alice.activates("Famished Squirrel", choosing = listOf(idol))
        allPass()
        expect { "Famished Squirrel".counters(p1p1) shouldBe 1 }
        allPass()
        expect {
            alice.life shouldBe 21
            alice.hand has "Island"
        }
    }

    /** {T}, sacrifice two other creatures: target players each lose 2 life and sacrifice a creature; you add {B}{B} and
     * draw a card. */
    @Test
    fun priestOfNamelessGods() = scenario(players = 3) {
        alice.battlefield("Priest of Nameless Gods", bear, corpse)
        alice.library("Island")
        bob.battlefield(ogre)
        carol.battlefield("Elvish Seer")

        alice.activates("Priest of Nameless Gods", targets = listOf("bob", "carol"), choosing = listOf(bear, corpse))
        allPass()
        expect {
            bob.life shouldBe 18
            carol.life shouldBe 18
            bob.graveyard has ogre
            carol.graveyard has "Elvish Seer"
            alice.hand has "Island"
            alice.graveyard has bear
        }
        kotlin.test.assertEquals(2, alice.pool.size)
    }

    /** Another nontoken creature of yours entering: investigate; {T}, sacrifice X Clues: an opponent reveals the top X
     * cards, you may put a nonland permanent card with mana value X or less onto the battlefield under your control. */
    @Test
    fun thessaBeastwatcher() = scenario {
        alice.battlefield("Thessa, Beastwatcher", "Forest", "Forest")
        alice.hand(bear)

        alice.casts(bear)
        allPass()
        allPass()
        expect { alice.battlefield has "Clue Token" }
    }

    @Test
    fun thessaRevealsAndSteals() = scenario {
        alice.battlefield("Thessa, Beastwatcher", "Forest", "Forest", "Swamp", "Swamp")
        alice.hand(bear, corpse)
        bob.library(corpse, "Forest", ogre)

        alice.casts(bear)
        allPass()
        allPass()
        alice.casts(corpse)
        allPass()
        allPass()
        expect { alice.battlefield.names.count { it == "Clue Token" } shouldBe 2 }

        alice.activates("Thessa, Beastwatcher", x = 2, target = "bob")
        // X = 2 and two Clues: both are sacrificed without a choice
        allPass()
        // The Ogre's mana value is too much and the Forest is a land: the Corpse is Alice's choice
        answer(Answer.Objects(listOf(card(corpse, 1))))
        expect {
            card(corpse, 1).controller shouldBe alice.id
            bob.library.size shouldBe 2
            alice.battlefield lacks "Clue Token"
        }
    }
}
