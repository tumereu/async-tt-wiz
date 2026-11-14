package arcanum.engine.sets.bro

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Brothers' War chunk 07 in play: triggers repeated for each pair of one colour spent, counters for each {G}{G}, an activated
 * ability cheaper while one opponent controls four nonbasic lands (three players), Commands choosing as they resolve, an opponent
 * returning one permanent and shuffling the rest away, every permanent but six lands each destroyed, and a creature spell copied as
 * an artifact.
 */
class BroChunk07ChoicesAndCopiesScenariosTest {
    private val coilspringWyrm = Cards.invent("Coilspring Wyrm", "{X}{6}", "Artifact Creature — Serpent", """
        When this creature enters, for each {U}{U} spent to cast it, draw a card.
        When this creature enters, for each {B}{B} spent to cast it, each opponent discards a card.
        When this creature enters, for each {R}{R} spent to cast it, it gets +1/+0 and gains trample and haste until end of turn.
    """, "5/4")
    private val loamParagon = Cards.invent("Loam Paragon", "{X}{4}", "Artifact Creature — Construct", """
        This creature enters with three +1/+1 counters on it for each {G}{G} spent to cast it.
        When this creature enters, choose up to two other target creatures you control. For each {W}{W} spent to cast this creature, put a +1/+1 counter on each of them.
    """, "2/2")
    private val gravegrinReassembler = Cards.invent("Gravegrin Reassembler", "{2}", "Artifact Creature — Zombie", """
        This creature can't block.
        {4}{B}{B}: Return this card from your graveyard to the battlefield with a +1/+1 counter on it. This ability costs {4} less to activate if an opponent controls four or more nonbasic lands.
    """, "3/1")
    private val vornsCommand = Cards.invent("Vorn's Command", "{3}{B}{B}", "Sorcery", """
        Choose two —
        • Put two +1/+1 counters on up to one creature. It gains lifelink until end of turn.
        • Destroy each creature with power 2 or less.
        • Return up to two creature cards from your graveyard to your hand.
        • Each opponent sacrifices a creature with the greatest power among creatures they control.
    """)
    private val keldasCommand = Cards.invent("Kelda's Command", "{X}{R}", "Sorcery", """
        Choose two —
        • Choose target player. They may discard up to X cards. Then they draw a card for each card discarded this way.
        • This spell deals X damage to target creature.
        • This spell deals X damage to target planeswalker.
        • Target creature gets +X/+0 and gains haste until end of turn.
    """)
    private val ilsabet = Cards.invent("Ilsabet, Time Drifter", "{3}{U}{U}", "Legendary Planeswalker — Teferi", """
        Whenever you draw a card, put a loyalty counter on Ilsabet.
        [0]: Draw a card.
        [−2]: Create a 2/2 blue Spirit creature token with vigilance and "Whenever you draw a card, put a +1/+1 counter on this token."
        [−12]: Target opponent chooses a permanent they control and returns it to its owner's hand. Then they shuffle each nonland permanent they control into its owner's library.
    """, loyalty = "4")
    private val halvardsReliquary = Cards.invent("Halvard's Reliquary", "{3}", "Legendary Artifact", """
        {2}{W}{W}, {T}, Exile Halvard's Reliquary: Each player chooses six lands they control. Destroy all other permanents. Activate only as a sorcery.
        When Halvard's Reliquary is put into exile from the battlefield, you may pay {2}. If you do, search your library for a planeswalker card, reveal it, put it into your hand, then shuffle.
    """)
    private val pell = Cards.invent("Pell, the Toycrafter", "{3}{G}{U}", "Legendary Creature — Human Artificer",
        "Whenever you cast a Beast or Bird creature spell, you may copy it, except the copy is an artifact in addition to its other types. (The copy becomes a token.)", "3/5")

    private val brevik = Cards.invent("Brevik, Master Arcanist", "{1}{U}{U}", "Legendary Creature — Human Wizard Advisor",
        "At the beginning of your end step, if you've cast a noncreature spell this turn, reveal the top five cards of your library. For each card type among noncreature spells you've cast this turn, you may put a card of that type from among the revealed cards into your hand. Put the rest on the bottom of your library in a random order.",
        "2/4")
    private val staticPrick = Cards.invent("Static Prick", "{R}", "Instant", "Static Prick deals 1 damage to any target.")
    private val emberShard = Cards.invent("Ember Shard", "{R}", "Instant", "Ember Shard deals 2 damage to any target.")
    private val ironLattice = Cards.invent("Iron Lattice", "{1}", "Artifact", "{T}: You gain 1 life.")
    private val grizzly = Cards.invent("Burrow Grizzly", "{1}{G}", "Creature — Beast", "", "2/2")
    private val ogre = Cards.invent("Hillside Ogre", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val hound = Cards.invent("Cinder Hound", "{2}{R}", "Creature — Dog", "", "3/3")
    private val totem = Cards.invent("Brass Totem", "{2}", "Artifact", "{T}: You gain 1 life.")
    private val wasteland = Cards.invent("Rubble Expanse", null, "Land", "{T}: Add {C}.")

    // CR 601.2h: four blue mana spent is {U}{U} twice: two cards; two red mana: +1/+0, trample and haste; no black pair, no discard
    @Test
    fun wyrmRepeatsEachTriggerForEachPairSpent() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Mountain", "Mountain")
        alice.hand(coilspringWyrm)
        alice.library("Plains", "Plains", "Plains")
        bob.hand("Forest")
        alice.casts(coilspringWyrm, x = 0)
        playOut()
        expect {
            alice.hand.size shouldBe 2
            bob.hand has "Forest"
            coilspringWyrm.power shouldBe 6
            coilspringWyrm hasKeyword Keyword.Trample.kind
            coilspringWyrm hasKeyword Keyword.Haste.kind
        }
    }

    // CR 107.3, 601.2h: the mana paid for X counts too; three green and three blue: one pair of each, an odd one left over
    @Test
    fun wyrmCountsManaPaidForX() = scenario {
        alice.battlefield("Island", "Island", "Island", "Forest", "Forest", "Forest", "Plains")
        alice.hand(coilspringWyrm)
        alice.library("Plains", "Plains", "Plains")
        alice.casts(coilspringWyrm, x = 1)
        playOut()
        expect {
            alice.hand.size shouldBe 1
            coilspringWyrm.power shouldBe 5
        }
    }

    // CR 614.1c: four green mana spent: three +1/+1 counters twice; no white pair, so the other creature gets nothing
    @Test
    fun paragonEntersWithThreeCountersForEachGreenPair() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", grizzly)
        alice.hand(loamParagon)
        alice.casts(loamParagon, x = 0)
        playOut(grizzly)
        expect {
            loamParagon.counters(CounterKind.PowerToughness(1, 1)) shouldBe 6
            grizzly.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 601.2h, 400.7d: four white mana spent on the spell: each target gets a +1/+1 counter twice
    @Test
    fun paragonPutsACounterOnEachTargetForEachWhitePair() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", grizzly, hound)
        alice.hand(loamParagon)
        alice.casts(loamParagon, x = 0)
        playOut(grizzly, hound)
        expect {
            loamParagon.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
            grizzly.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            hound.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
        }
    }

    // CR 601.2f, 102.3: one opponent controls four nonbasic lands: the ability costs {B}{B}
    @Test
    fun reassemblerCostsLessWhileOneOpponentHasFourNonbasicLands() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard(gravegrinReassembler)
        bob.battlefield(wasteland, wasteland, wasteland, wasteland)
        alice.activates(gravegrinReassembler)
        allPass()
        expect {
            alice.battlefield has gravegrinReassembler
            gravegrinReassembler.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // CR 102.3: "an opponent controls" is one opponent: two each among two opponents don't reduce it
    @Test
    fun reassemblerNotCheaperWhenNonbasicLandsAreSplitAmongOpponents() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard(gravegrinReassembler)
        bob.battlefield(wasteland, wasteland)
        carol.battlefield(wasteland, wasteland)
        alice.activates(gravegrinReassembler)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 101.4, 701.21a: each opponent sacrifices one of their creatures with the greatest power; CR 115.1: the returned cards are chosen
    // as it resolves
    @Test
    fun vornsCommandEachOpponentSacrificesItsBiggestAndReturnsTwo() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(grizzly, hound)
        alice.hand(vornsCommand)
        bob.battlefield(grizzly, ogre)
        carol.battlefield(hound)
        alice.casts(vornsCommand, modes = listOf(2, 3))
        playOut(grizzly, hound)
        expect {
            alice.hand.size shouldBe 2
            bob.battlefield has grizzly
            bob.battlefield lacks ogre
            carol.battlefield lacks hound
        }
    }

    // CR 115.1: "up to one creature" isn't a target: chosen as it resolves, any creature
    @Test
    fun vornsCommandPutsCountersOnAChosenCreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", ogre)
        alice.hand(vornsCommand)
        bob.battlefield(grizzly)
        alice.casts(vornsCommand, modes = listOf(0, 1))
        playOut(ogre)
        expect {
            ogre.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            ogre hasKeyword Keyword.Lifelink.kind
            bob.graveyard has grizzly
        }
    }

    // CR 701.9a, 107.3a: the target player discards up to X cards they choose, then draws that many
    @Test
    fun keldasCommandDiscardsThenDrawsThatMany() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(keldasCommand)
        bob.battlefield(hound)
        bob.hand(grizzly, ogre, "Forest")
        bob.library("Island", "Island", "Island")
        alice.casts(keldasCommand, targets = listOf("bob", hound), x = 2, modes = listOf(0, 1))
        playOut(grizzly, ogre)
        expect {
            bob.graveyard has grizzly
            bob.graveyard has ogre
            bob.hand.size shouldBe 3
            hound.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
            hound.view.obj?.damage shouldBe 2
        }
    }

    // CR 608.2c: the target opponent chooses which permanent returns; CR 701.24a: their other nonland permanents are shuffled away, the
    // third player's stay
    @Test
    fun ilsabetUltimateReturnsOneAndShufflesTheRest() = scenario(players = 3) {
        alice.battlefield(ilsabet, counters = listOf(CounterKind.Loyalty to 12))
        bob.battlefield(ogre, hound, totem, "Forest")
        carol.battlefield(grizzly)
        alice.activates(ilsabet, ability = 2, target = "bob")
        playOut(ogre)
        expect {
            bob.hand has ogre
            bob.library has hound
            bob.library has totem
            bob.battlefield has "Forest"
            carol.battlefield has grizzly
        }
    }

    // CR 101.4: each player keeps six lands (all of them if fewer), everything else is destroyed at once; CR 603.10a: exiling it as a
    // cost triggers its leaves-the-battlefield ability, which resolves first
    @Test
    fun reliquaryKeepsSixLandsEachAndFindsAPlaneswalker() = scenario {
        alice.battlefield(halvardsReliquary, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", ogre)
        alice.library(ilsabet, "Island")
        bob.battlefield("Forest", "Forest", grizzly)
        alice.activates(halvardsReliquary, paying = listOf("Plains", "Plains#1", "Plains#2", "Plains#3"))
        playOut(ilsabet, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        expect {
            alice.exile has halvardsReliquary
            alice.hand has ilsabet
            alice.graveyard has ogre
            alice.battlefield.size shouldBe 6
            bob.graveyard has grizzly
            bob.battlefield.size shouldBe 2
        }
    }

    // CR 707.10c: the copy of a creature spell resolves as a token; CR 707.9b: it is an artifact in addition to its other types
    @Test
    fun pellCopiesABeastSpellAsAnArtifact() = scenario {
        alice.battlefield(pell, "Forest", "Forest")
        alice.hand(grizzly)
        alice.casts(grizzly)
        playOut()
        expect {
            alice.battlefield.names.count { it == grizzly } shouldBe 2
            val types = (0..1).map { card(grizzly, it).types }
            assertTrue(types.any { CardType.Artifact in it && CardType.Creature in it })
            assertTrue(types.any { CardType.Artifact !in it })
            card(grizzly, 0).view.characteristics!!.hasSubtype(Subtype("Beast")) shouldBe true
        }
    }

    // CR 205.2a: an instant and an artifact cast this turn: one revealed instant and one revealed artifact may go to the hand, never a
    // creature (no creature spell counts); CR 701.20a: the rest to the bottom
    @Test
    fun brevikTakesOneCardOfEachTypeCastThisTurn() = scenario {
        alice.battlefield(brevik, "Mountain", "Mountain", "Mountain")
        alice.hand(staticPrick, totem)
        alice.library(emberShard, ironLattice, ogre, "Island", "Plains", "Swamp")
        alice.casts(staticPrick, target = "bob")
        allPass()
        alice.casts(totem)
        allPass()
        advanceTo(arcanum.api.TurnStep.End)
        playOut(emberShard, ironLattice, ogre)
        expect {
            alice.hand has emberShard
            alice.hand has ironLattice
            alice.library has ogre
            alice.library.size shouldBe 4
        }
    }
}
