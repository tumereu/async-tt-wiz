package arcanum.engine.sets.snc

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.pay
import arcanum.engine.sets.fin.resolveAll
import arcanum.engine.sets.fin.targets
import arcanum.engine.sets.fin.yes
import arcanum.engine.sets.ltr.alternativeOf
import kotlin.test.Test

/** Streets of New Capenna chunk 01 in play: blitz from the graveyard with a life payment, a copy that draws when it dies, a card looked at after combat damage. */
class SncChunk01BlitzScenariosTest {
    private val underdog = Cards.invent("Snc01 Stubborn Underhound", "{1}{B}", "Creature — Human Warrior", """
        Blitz—{2}{B}{B}, Pay 2 life. (If you cast this spell for its blitz cost, it gains haste and "When this creature dies, draw a card." Sacrifice it at the beginning of the next end step.)
        You may cast this card from your graveyard using its blitz ability.
    """.trimIndent(), "3/2")
    private val jaxis = Cards.invent("Snc01 Kazra, the Rabblerouser", "{3}{R}", "Legendary Creature — Human Warrior", """
        {R}, {T}, Discard a card: Create a token that's a copy of another target creature you control. It gains haste and "When this token dies, draw a card." Sacrifice it at the beginning of the next end step. Activate only as a sorcery.
        Blitz {1}{R} (If you cast this spell for its blitz cost, it gains haste and "When this creature dies, draw a card." Sacrifice it at the beginning of the next end step.)
    """.trimIndent(), "2/3")
    private val envoy = Cards.invent("Snc01 Ziator's Messenger", "{1}{B}{R}{G}", "Creature — Lizard Warrior", """
        Trample
        Whenever this creature deals combat damage to a player, look at the top card of your library. You may play a land from the top of your library or cast a spell with mana value less than or equal to the damage dealt from the top of your library without paying its mana cost. If you don't, put that card into your hand.
        Blitz {2}{B}{R}{G}
    """.trimIndent(), "5/4")
    private val bear = Cards.invent("Snc01 Alley Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val zap = Cards.invent("Snc01 Gutter Zap", "{1}{R}", "Instant", "Snc01 Gutter Zap deals 3 damage to any target.")
    private val colossus = Cards.invent("Snc01 Gutter Colossus", "{5}{G}{G}", "Creature — Giant", "", "7/7")

    private fun hitWithEnvoy(s: arcanum.engine.scenario.Scenario) = with(s) {
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(envoy)
        resolveAll()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
    }

    // CR 702.152a, 118.8 (the additional cost, 2 life), 400.7 / 603.10a (dies: draw), 603.7 (sacrifice at the next end step)
    @Test
    fun blitzFromTheGraveyardPaysLifeThenIsSacrificedAndDraws() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(underdog)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        alice.casts(underdog, way = alternativeOf(underdog, Keyword.Blitz.Kind))
        allPass()
        expect {
            alice.life shouldBe 18
            alice.battlefield has underdog
            underdog hasKeyword Keyword.Haste
            alice.hand.size shouldBe 0
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.graveyard has underdog
            alice.hand.size shouldBe 1
        }
    }

    // CR 707.2 (a token copy), 603.7, 700.4: the copy has haste, draws when it dies and is sacrificed at the next end step
    @Test
    fun copyDrawsWhenSacrificed() = scenario {
        alice.battlefield(jaxis, bear, "Mountain")
        alice.hand("Island")
        alice.library("Forest", "Forest")
        bob.library("Island", "Island")
        alice.activates(jaxis, target = bear)
        resolveAll()
        expect {
            alice.hand.size shouldBe 0
            bear.power shouldBe 2
        }
        advanceTo(TurnStep.End)
        allPass()
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 510.2 (damage dealt = 5), 305.1 (a land is played from the top), 608.2c
    @Test
    fun envoyPlaysALandFromTheTop() = scenario {
        alice.battlefield(envoy)
        alice.library("Forest", "Island")
        bob.library("Island", "Island")
        hitWithEnvoy(this)
        yes()
        expect {
            alice.battlefield has "Forest"
            alice.hand.size shouldBe 0
        }
    }

    // CR 601.2, 608.2g: a spell with mana value up to the damage (2 <= 5) is cast for free
    @Test
    fun envoyCastsASpellUpToTheDamage() = scenario {
        alice.battlefield(envoy)
        alice.library(zap, "Island")
        bob.library("Island", "Island")
        hitWithEnvoy(this)
        yes()
        targets("bob")
        expect {
            bob.life shouldBe 12
            alice.hand.size shouldBe 0
        }
    }

    // CR 608.2c: a spell with mana value over the damage (7 > 5) can't be cast even when the player says yes, so it goes into the hand
    @Test
    fun envoyPutsATooExpensiveSpellIntoHand() = scenario {
        alice.battlefield(envoy)
        alice.library(colossus, "Island")
        bob.library("Island", "Island")
        hitWithEnvoy(this)
        yes()
        expect {
            alice.hand has colossus
            alice.battlefield lacks colossus
        }
    }

    // CR 608.2c, 3 players: declining puts the card into hand
    @Test
    fun envoyDecliningPutsTheCardIntoHand() = scenario(players = 3) {
        alice.battlefield(envoy)
        alice.library(zap, "Island")
        bob.library("Island", "Island")
        carol.library("Island", "Island")
        hitWithEnvoy(this)
        yes(false)
        expect {
            alice.hand has zap
            bob.life shouldBe 15
        }
    }
}
