package arcanum.engine.sets.neo

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dgm.payIfAsked
import kotlin.test.Test

/** Chunk 11 of NEO: a pump by the greatest mana value among your permanents with an untap, a pump per Samurai or Warrior for a creature attacking alone,
 * a graveyard artifact cast after attacking alone, and a replacement exiling every creature that would die this turn. */
class NeoChunk11ApplyScenariosTest {
    private val boon = Cards.invent("Boon of Hakkaido", "{1}{G}", "Instant",
        "Target creature gets +X/+X until end of turn, where X is the greatest mana value among permanents you control. Untap it.")
    private val titan = Cards.invent("Hakkaido Titan", "{4}{G}{G}", "Creature — Giant", "", "6/6")
    private val cub = Cards.invent("Hakkaido Cub", "{G}", "Creature — Bear", "", "1/1")
    private val captain = Cards.invent("Kasumi Captain", "{3}{R}{W}", "Creature — Human Samurai", """
        Haste
        Whenever a Samurai or Warrior you control attacks alone, it gets +1/+0 until end of turn for each Samurai or Warrior you control.
    """, "4/3")
    private val ronin = Cards.invent("Wandering Ronin", "{1}{W}", "Creature — Human Samurai", "", "2/2")
    private val warrior = Cards.invent("Wandering Warrior", "{1}{R}", "Creature — Human Warrior", "", "2/2")
    private val colonel = Cards.invent("Heiko Yamasaki, the Colonel", "{3}{R}", "Legendary Creature — Human Samurai", """
        Trample
        Whenever a Samurai or Warrior you control attacks alone, you may cast target artifact card from your graveyard this turn.
    """, "3/3")
    private val gadget = Cards.invent("Brass Gadget", "{1}", "Artifact", "Flash")
    private val misfire = Cards.invent("Wicked Misfire", "{1}{B}{B}", "Sorcery",
        "All creatures get -2/-2 until end of turn. If a creature would die this turn, exile it instead.")

    // CR 202.3 (mana value), 608.2c (X is worked out as the spell resolves), 701.26 (untap)
    @Test
    fun boonPumpsByTheGreatestManaValueAndUntaps() = scenario {
        alice.battlefield(titan, "Forest", "Forest")
        alice.battlefield(cub, tapped = true)
        alice.hand(boon)
        alice.casts(boon, target = cub)
        payIfAsked()
        resolveAll()
        expect {
            cub.power shouldBe 7
            cub.toughness shouldBe 7
            cub.tapped shouldBe false
        }
    }

    // CR 506.5 (alone), 611.2a: the count includes the attacker itself and every other Samurai or Warrior, and no one else
    @Test
    fun aloneAttackerGetsOneForEachSamuraiOrWarrior() = scenario {
        alice.battlefield(captain, ronin, warrior, cub)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ronin)
        resolveAll()
        expect { ronin.power shouldBe 5 }
    }

    // CR 506.5: two attackers are not attacking alone
    @Test
    fun twoAttackersGetNothing() = scenario {
        alice.battlefield(captain, ronin)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ronin, captain)
        resolveAll()
        expect { ronin.power shouldBe 2 }
    }

    // CR 601.2a, 118.9, 307.1: the artifact card may be cast from the graveyard this turn (during combat only because it has flash)
    @Test
    fun aloneAttackLetsYouCastAnArtifactFromTheGraveyard() = scenario {
        alice.battlefield(colonel, "Mountain")
        alice.graveyard(gadget)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(colonel)
        resolveAll()
        alice.casts(gadget)
        payIfAsked()
        resolveAll()
        expect { alice.battlefield has gadget }
    }

    // CR 614.6 (the replacement applies to every creature that would die this turn), 3+ players: each player's creatures
    @Test
    fun misfireExilesEveryCreatureThatDies() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", ronin)
        bob.battlefield(warrior)
        carol.battlefield(titan)
        alice.hand(misfire)
        alice.casts(misfire)
        payIfAsked()
        resolveAll()
        expect {
            alice.exile has ronin
            bob.exile has warrior
            carol.battlefield has titan
            titan.power shouldBe 4
        }
    }
}
