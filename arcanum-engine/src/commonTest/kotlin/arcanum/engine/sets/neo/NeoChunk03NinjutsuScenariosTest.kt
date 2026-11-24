package arcanum.engine.sets.neo

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Chunk 3 of NEO: ninjutsu (CR 702.49) and the combat damage triggers of its Ninjas that give the engine something new. */
class NeoChunk03NinjutsuScenariosTest {
    private fun ninjutsuLine(cost: String) =
        "Ninjutsu $cost ($cost, Return an unblocked attacker you control to hand: Put this card onto the battlefield from your hand tapped and attacking.)"

    private val bear = Cards.invent("Neo Dojo Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val cub = Cards.invent("Neo Dojo Cub", "{G}", "Creature — Bear", "", "1/1")
    private val ogre = Cards.invent("Neo Dojo Ogre", "{4}{R}", "Creature — Ogre", "", "5/5")
    private val rogue = Cards.invent("Neo Dojo Rogue", "{1}{B}", "Creature — Human Rogue", "", "2/2")
    private val gearTwo = Cards.invent("Neo Dojo Gear", "{2}", "Artifact", "")
    private val gearThree = Cards.invent("Neo Dojo Engine", "{3}", "Artifact", "")
    private val snack = Cards.invent("Neo Dojo Snack", "{2}{B}", "Sorcery", "Draw a card.")
    private val quip = Cards.invent("Neo Dojo Quip", "{U}", "Instant", "Draw a card.")

    private val clenchedFist = Cards.invent("Clenched-Fist Ninja", "{2}{B}", "Creature — Human Ninja", """
        Ninjutsu {2}{B}
        This creature enters with a menace counter on it.
        Whenever this creature deals combat damage to a player, you may remove a menace counter from it. When you do, that player reveals their hand and you choose a nonland card from it. Exile that card.
    """.trimIndent(), "3/3")
    private val prowler = Cards.invent("Twining Prowler", "{1}{G}", "Creature — Snake Ninja", ninjutsuLine("{1}{G}") +
        "\nWhenever this creature deals combat damage to a player, put a +1/+1 counter on target creature you control that doesn't have a +1/+1 counter on it.", "2/1")
    private val machinist = Cards.invent("Hidden Machinist", "{2}{U}", "Artifact Creature — Human Ninja", ninjutsuLine("{1}{U}") +
        "\nWhenever this creature deals combat damage to a player, you may put an artifact card with mana value less than or equal to that damage from your hand onto the battlefield.", "2/4")
    private val kesho = Cards.invent("Kesho, Star Sage's Heir", "{1}{B}{B}", "Legendary Creature — Rat Ninja", ninjutsuLine("{3}{B}") +
        "\nWhenever Kesho deals combat damage to a player, exile the top card of each player's library. Until end of turn, you may play one of those cards. If you cast a spell this way, pay life equal to its mana value rather than paying its mana cost.", "3/2")
    private val sensei = Cards.invent("Ash-Pelt Sensei", "{U}{B}", "Creature — Rat Ninja", ninjutsuLine("{U}{B}") +
        "\nNinjutsu abilities you activate cost {1} less to activate.\nOther Ninja and Rogue creatures you control get +1/+1.", "2/2")
    private val shade = Cards.invent("Neo Dojo Shade", "{2}{U}", "Creature — Human Ninja", ninjutsuLine("{1}{U}"), "1/1")
    private val hushBlade = Cards.invent("Ronsai Hush-Blade", "{1}{B}", "Creature — Human Ninja", ninjutsuLine("{1}{B}") +
        "\nWhenever this creature deals combat damage to a player, you may discard a creature card. When you do, destroy target creature or planeswalker that player controls.", "2/1")
    private val intruder = Cards.invent("Star-Circuit Intruder", "{1}{U}", "Enchantment Creature — Human Ninja", ninjutsuLine("{U}") +
        "\nWhenever this creature deals combat damage to a player, you may draw a card. If you do, discard a card unless this creature entered this turn.", "2/1")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    /** Alice attacks [defender] with [attacker], nobody blocks, and combat damage is dealt; its trigger waits on the Stack. */
    private fun Scenario.hitsUnblocked(attacker: String, defender: String = "bob") {
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(attacker, defender = defender)
        advanceTo(TurnStep.CombatDamage)
    }

    // CR 118.12, 603.12: removing the menace counter is the "you do"; CR 701.20a: the damaged player reveals, not another opponent
    @Test
    fun clenchedFistExilesANonlandCardFromTheDamagedPlayersHand() = scenario(players = 3) {
        alice.battlefield(clenchedFist, counters = listOf(CounterKind.Keyword(Keyword.Menace.kind) to 1))
        bob.hand(snack)
        carol.hand(quip, ogre, "Plains")
        hitsUnblocked(clenchedFist, defender = "carol")
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        assertTrue((pending as Decision.ChooseObjects).options.none { it == card(snack) || it == card("Plains") })
        carol.chooses(ogre)
        resolveAll()
        expect {
            carol.life shouldBe 17
            card(clenchedFist).counters(CounterKind.Keyword(Keyword.Menace.kind)) shouldBe 0
            carol.exile has ogre
            carol.hand has quip
            bob.hand has snack
        }
    }

    // CR 122.1: a creature that has a +1/+1 counter can't be chosen
    @Test
    fun prowlerTargetsOnlyCreaturesWithoutACounter() = scenario {
        alice.battlefield(prowler, cub)
        alice.battlefield(bear, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        hitsUnblocked(prowler)
        resolveAll()
        val options = (pending as Decision.Targets).options
        assertTrue(target(bear) !in options)
        answer(Answer.Targets(listOf(target(cub))))
        resolveAll()
        expect { cub.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }

    // CR 603.2c ("that damage": 2, the damage dealt), 110.2a (put onto the battlefield, not cast)
    @Test
    fun machinistPutsAnArtifactWithManaValueUpToTheDamage() = scenario {
        alice.battlefield(machinist)
        alice.hand(gearTwo, gearThree)
        hitsUnblocked(machinist)
        resolveAll()
        val options = (pending as Decision.ChooseObjects).options
        assertEquals(listOf(card(gearTwo)), options)
        alice.chooses(gearTwo)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.battlefield has gearTwo
            alice.hand has gearThree
        }
    }

    // CR 406.3, 118.9: one of the three exiled cards (one from each player's library) is cast for life; CR 611.2a: then no other
    @Test
    fun keshoPlaysOneExiledCardForLife() = scenario(players = 3) {
        alice.battlefield(kesho)
        alice.library("Island", "Swamp")
        bob.library(snack)
        carol.library("Plains")
        hitsUnblocked(kesho, defender = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 17
            bob.exile has snack
            carol.exile has "Plains"
            alice.exile has "Island"
        }
        advanceTo(TurnStep.PostcombatMain)
        alice.casts(snack)
        resolveAll()
        expect {
            alice.life shouldBe 17
            bob.graveyard has snack
            alice.hand has "Swamp"
        }
        assertFailsWith<IllegalArgumentException> { alice.plays("Plains") }
    }

    // CR 305.1, 611.2a: playing the exiled land uses the one permission up: the exiled spell can't be cast afterwards
    @Test
    fun keshoLandPlayUsesThePermission() = scenario(players = 3) {
        alice.battlefield(kesho)
        alice.library("Island")
        bob.library(snack)
        carol.library("Plains")
        hitsUnblocked(kesho, defender = "bob")
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        alice.plays("Plains")
        expect {
            alice.battlefield has "Plains"
            alice.life shouldBe 20
        }
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Cast>().none { it.card == card(snack) })
    }

    // CR 601.2f, 602.2b: ninjutsu {1}{U} costs {U}; CR 613.4c: the Ninja that enters gets +1/+1, the Sensei doesn't
    @Test
    fun senseiMakesNinjutsuCheaperAndPumpsOtherNinjas() = scenario {
        alice.battlefield(sensei, bear, "Island")
        alice.hand(shade)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        alice.activates(shade, paying = listOf("Island"))
        resolveAll()
        expect {
            alice.battlefield has shade
            alice.hand has bear
            shade.power shouldBe 2
            sensei.power shouldBe 2
        }
    }

    // CR 613.4c: a Rogue gets the bonus too
    @Test
    fun senseiPumpsRogues() = scenario {
        alice.battlefield(sensei, rogue, bear)
        expect {
            rogue.power shouldBe 3
            bear.power shouldBe 2
        }
    }

    // CR 118.12, 603.12; 510.2: "that player" is the one dealt damage: only Carol's creature can be destroyed
    @Test
    fun hushBladeDestroysACreatureOfTheDamagedPlayer() = scenario(players = 3) {
        alice.battlefield(hushBlade)
        alice.hand(ogre, "Swamp")
        bob.battlefield(bear)
        carol.battlefield(cub)
        hitsUnblocked(hushBlade, defender = "carol")
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.graveyard has ogre
            carol.graveyard has cub
            bob.battlefield has bear
        }
    }

    // CR 118.12: the draw is optional (the audit's doubt): declining draws nothing and discards nothing
    @Test
    fun intruderMayDeclineTheDraw() = scenario {
        alice.battlefield(intruder)
        alice.hand("Forest")
        alice.library("Swamp")
        hitsUnblocked(intruder)
        resolveAll()
        answer(Answer.YesNo(false))
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.hand.names shouldBe listOf("Forest")
            alice.library.size shouldBe 1
        }
    }
}
