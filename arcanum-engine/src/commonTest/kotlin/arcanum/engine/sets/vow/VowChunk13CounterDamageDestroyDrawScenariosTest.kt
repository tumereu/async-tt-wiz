package arcanum.engine.sets.vow

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** VOW chunk 13: damage to each non-Vampire creature with a Blood token (three players), excess damage as Blood tokens, discarding
 * the hand then drawing for every card discarded this turn (a cost's discard included), a trigger on a spell you control targeting
 * the creature (an opponent's spell doesn't), the exile cost of "if you do, draw" (the audit's unsure line was a false alarm: only
 * your own graveyard), and a Vampire-only lifelink with an unblockable creature. */
class VowChunk13CounterDamageDestroyDrawScenariosTest {
    private val reprisal = Cards.invent("Night Reprisal", "{2}{R}", "Instant",
        "Night Reprisal deals 2 damage to each non-Vampire creature. Create a Blood token.")
    private val bat = Cards.invent("Dusk Flitter", "{R}", "Creature — Vampire", "", "2/2")
    private val ogre = Cards.invent("Reedbank Ogre", "{R}", "Creature — Ogre", "", "2/2")
    private val boar = Cards.invent("Gorse Boar", "{2}{G}", "Creature — Boar", "", "4/4")
    private val wound = Cards.invent("Gash Wound", "{4}{R}", "Sorcery",
        "Gash Wound deals 4 damage to target creature. Create a number of Blood tokens equal to the amount of excess damage dealt to that creature this way.")
    private val entomber = Cards.invent("Cemetery Entomber", "{1}{U}", "Creature — Zombie", "{T}, Discard a creature card: Draw a card.", "1/3")
    private val reversal = Cards.invent("Fate Reversal", "{3}{R}", "Sorcery", "Discard your hand, then draw a card for each card you've discarded this turn.")
    private val drake = Cards.invent("Gale Drake", "{1}{U}", "Creature — Drake", """
        Flying
        Whenever this creature becomes the target of a spell you control, draw a card.
    """.trimIndent(), "2/1")
    private val boost = Cards.invent("Brisk Boost", "{U}", "Instant", "Target creature gets +1/+1 until end of turn.")
    private val skaab = Cards.invent("Skybound Skaab", "{3}{U}{U}", "Creature — Zombie Drake", """
        Flying
        When this creature enters, you may exile two creature cards from your graveyard. If you do, draw a card.
    """.trimIndent(), "2/5")
    private val invitation = Cards.invent("Wedding Token", "{2}", "Artifact", """
        When this artifact enters, draw a card.
        {T}, Sacrifice this artifact: Target creature can't be blocked this turn. If it's a Vampire, it also gains lifelink until end of turn.
    """.trimIndent())

    private fun Scenario.count(player: ScenarioPlayer, name: String) = player.battlefield.names.count { it == name }

    // CR 120.3 and 702.2 (non-Vampires only), 111.10g; each other player's creatures too (three players)
    @Test
    fun reprisalHurtsEveryNonVampireCreatureOfEveryPlayer() = scenario(players = 3) {
        alice.battlefield(bat, ogre, "Mountain", "Mountain", "Mountain")
        alice.hand(reprisal)
        bob.battlefield(boar, ogre)
        carol.battlefield(bat, ogre)
        alice.casts(reprisal)
        resolveAll()
        expect {
            alice.battlefield has bat
            alice.graveyard has ogre
            bob.battlefield has boar
            bob.graveyard has ogre
            carol.battlefield has bat
            carol.graveyard has ogre
        }
        expect { count(alice, "Blood Token") shouldBe 1 }
    }

    // CR 120.4a, 120.10 (excess damage is beyond lethal damage), 111.10g: a 4/4 takes 4, no excess; a 2/2 takes 2 more than lethal
    @Test
    fun woundMakesABloodTokenForEachDamageBeyondLethal() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(wound)
        bob.battlefield(ogre)
        alice.casts(wound, target = ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            count(alice, "Blood Token") shouldBe 2
        }
    }

    @Test
    fun woundMakesNoBloodTokenWithoutExcess() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(wound)
        bob.battlefield(boar)
        alice.casts(wound, target = boar)
        resolveAll()
        expect {
            bob.graveyard has boar
            count(alice, "Blood Token") shouldBe 0
        }
    }

    // CR 701.9a, 121.1, 608.2c: a discard as a cost counts; the three cards of the hand add: four discarded this turn, four drawn
    @Test
    fun reversalDrawsForTheCostDiscardToo() = scenario {
        alice.battlefield(entomber, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(ogre, reversal, "Mountain", "Island")
        alice.library("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.activates(entomber, choosing = listOf("Reedbank Ogre"))
        resolveAll()
        expect { alice.hand.size shouldBe 4 }
        alice.casts(reversal)
        resolveAll()
        // the Ogre (cost) plus the three cards of the hand left: four discarded this turn, four drawn
        expect {
            alice.hand.size shouldBe 4
            alice.graveyard has ogre
        }
    }

    // CR 603.2, 115.1: only a spell its controller controls; the opponent's spell draws nothing
    @Test
    fun drakeDrawsOnlyForYourOwnSpell() = scenario {
        alice.battlefield(drake, "Island", "Island")
        alice.hand(boost)
        alice.library("Forest", "Forest", "Forest")
        bob.battlefield("Island")
        bob.hand(boost)
        alice.casts(boost, target = drake)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
        alice.passes()
        bob.casts(boost, target = drake)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 117.12, 404.1: it exiles two creature cards from its controller's graveyard to draw; with fewer it can't be paid
    @Test
    fun skaabExilesTwoCreatureCardsOfYourOwnGraveyardToDraw() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(skaab)
        alice.graveyard(ogre, bat)
        bob.graveyard(boar)
        alice.library("Forest", "Forest")
        alice.casts(skaab)
        resolveAll()
        playOut("Reedbank Ogre", "Dusk Flitter")
        expect {
            alice.exile has ogre
            alice.exile has bat
            bob.graveyard has boar
            alice.hand.size shouldBe 1
        }
    }

    // CR 509.1b, 702.15a: an unblockable Vampire with lifelink gains its controller life; a Human doesn't get it
    @Test
    fun invitationGivesLifelinkToAVampireOnly() = scenario {
        alice.battlefield(bat, invitation)
        bob.battlefield(boar)
        alice.activates(invitation, target = bat)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bat)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(boar to bat) }
        advanceTo(TurnStep.End)
        expect { alice.life shouldBe 22 }
    }

    @Test
    fun invitationGivesNoLifelinkToANonVampire() = scenario {
        alice.battlefield(ogre, invitation)
        bob.battlefield(boar)
        alice.activates(invitation, target = ogre)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ogre)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(boar to ogre) }
        advanceTo(TurnStep.End)
        expect { alice.life shouldBe 20 }
    }
}
