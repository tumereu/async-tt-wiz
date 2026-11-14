package arcanum.engine.sets.dmu

import arcanum.api.CounterKind
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targetsIfAsked
import arcanum.engine.sets.msh.chooseIfOffered
import arcanum.engine.sets.msh.may
import arcanum.engine.sets.msh.payIfAsked
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The DMU cards of chunk 13, played: a defender-counting drain, a cast trigger with three players and a graveyard cast with a discard
 * cost, tribal anthems with optional payments, sacrifice costs and tutors, an exile-until-leaves enchantment, characteristic-defining
 * power and cost reduction, counter engines, a once-per-turn "one or more" trigger, a tap ability and an untap spell.
 * The invented names carry the oracle text of the real cards.
 */
class DmuChunk13LifeCountersSearchTapScenariosTest {
    private val husk = Cards.invent("Brindle Husk", "{1}{B}", "Creature — Phyrexian", """
        Defender
        {2}{B}, {T}: Each opponent loses X life, where X is the number of creatures with defender you control.
    """, "3/3")
    private val wyrd = Cards.invent("Brindle Wyrdcaller", "{1}{U}{B}{B}", "Legendary Creature — Human Wizard", """
        Whenever you cast an instant or sorcery spell, each opponent loses 1 life.
        You may cast this card from your graveyard by discarding two cards in addition to paying its other costs.
    """, "3/4")
    private val seer = Cards.invent("Brindle Thornseer", "{G}{G}", "Creature — Elf Druid", """
        Other Elves you control get +1/+1.
        Whenever you cast an Elf spell, you may pay {G}. If you do, draw a card.
    """, "1/1")
    private val acolyte = Cards.invent("Brindle Acolyte", "{1}{B}", "Creature — Human Cleric", """
        Other Clerics you control get +1/+1.
        {3}{B}{B}, {T}, Sacrifice another Cleric: Search your library for a black creature card, put it onto the battlefield, then shuffle.
    """, "2/2")
    private val snare = Cards.invent("Brindle Spellsnare", "{1}{U}", "Creature — Merfolk Wizard", """
        Flash
        Other Merfolk you control get +1/+1.
        Sacrifice a Merfolk: Counter target noncreature spell unless its controller pays {1}.
    """, "1/1")
    private val writ = Cards.invent("Brindle Writ", "{1}{W}{W}", "Enchantment",
        "When this enchantment enters, exile target creature or planeswalker an opponent controls until this enchantment leaves the battlefield.")
    private val corvane = Cards.invent("Brindle Corvane, Warden", "{W}{W}{B}", "Legendary Creature — Phyrexian Human", """
        Menace
        {W}{B}, {T}, Sacrifice another creature: Put a +1/+1 counter on each creature you control.
    """, "3/3")
    private val tinkerer = Cards.invent("Brindle Maelis, Sage", "{U}{R}", "Legendary Creature — Human Artificer",
        "{T}: Put two ingenuity counters on Brindle Maelis, then you may put an artifact card with mana value X or less from your hand onto the battlefield, where X is the number of ingenuity counters on Brindle Maelis.", "2/3")
    private val gourmand = Cards.invent("Brindle Gourmand", "{3}{B}{B}", "Creature — Vampire", """
        Flying
        Whenever one or more other creatures die, put a +1/+1 counter on this creature. This ability triggers only once each turn.
    """, "3/3")
    private val automaton = Cards.invent("Brindle Automaton", "{4}", "Artifact Creature — Golem", """
        Defender
        When this creature enters, you may search your library for a creature card with defender, reveal it, put it into your hand, then shuffle.
    """, "1/3")
    private val zephyr = Cards.invent("Brindle Zephyr", "{1}{U}{U}", "Creature — Djinn", """
        Flying
        Brindle Zephyr's power is equal to the number of instant and sorcery cards in your graveyard.
        Instant and sorcery spells you cast cost {1} less to cast.
    """, "*/4")
    private val portcullis = Cards.invent("Brindle Portcullis", "{W}", "Artifact Creature — Wall", """
        Defender
        {2}{W}, {T}: Tap target creature.
    """, "0/3")
    private val muster = Cards.invent("Brindle Muster", "{2}{W}", "Instant", "Untap up to two target creatures. They each get +2/+2 until end of turn.")

    private val scroll1 = Cards.invent("Brindle Scroll One", "{U}", "Instant", "Draw a card.")
    private val scroll2 = Cards.invent("Brindle Scroll Two", "{U}", "Sorcery", "Draw a card.")
    private val elf = Cards.invent("Brindle Elfling", "{G}", "Creature — Elf", "", "1/1")
    private val elfSpell = Cards.invent("Brindle Elf Scout", "{G}", "Creature — Elf Scout", "", "1/1")
    private val cleric = Cards.invent("Brindle Cleric", "{B}", "Creature — Human Cleric", "", "1/1")
    private val blackBeast = Cards.invent("Brindle Black Beast", "{2}{B}", "Creature — Beast", "", "3/3")
    private val merfolk = Cards.invent("Brindle Merfolk", "{U}", "Creature — Merfolk", "", "1/1")
    private val bear = Cards.invent("Brindle Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Brindle Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val wall = Cards.invent("Brindle Wall", "{2}", "Artifact Creature — Wall", "Defender", "0/4")
    private val trinketSmall = Cards.invent("Brindle Trinket", "{2}", "Artifact", "")
    private val trinketBig = Cards.invent("Brindle Gadget", "{5}", "Artifact", "")
    private val page = (1..6).map { Cards.invent("Brindle Page $it", "{G}", "Creature — Plant", "", "1/1") }
    private val sweep = Cards.invent("Brindle Sweep", "{2}{R}", "Sorcery", "Destroy all creatures with power 2 or less.")
    private val preen = Cards.invent("Brindle Preen", "{1}{U}", "Instant", "Draw a card.")
    private val foePreen = Cards.invent("Brindle Foe Preen", "{1}{U}", "Instant", "Draw a card.")
    private val foeScroll = Cards.invent("Brindle Foe Scroll", "{U}", "Instant", "Draw a card.")
    private val smash = Cards.invent("Brindle Smash", "{1}{W}", "Instant", "Destroy target enchantment.")
    private val doom = Cards.invent("Brindle Doom", "{1}", "Instant", "Destroy target creature.")

    // CR 119.3, 608.2h: X is counted on resolution and includes the Husk itself; three players: each opponent loses it
    @Test
    fun huskDrainsEachOpponentByTheNumberOfDefenders() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", husk, wall, bear)
        bob.battlefield(wall)
        alice.activates(husk)
        resolveAll()
        expect {
            husk.tapped shouldBe true
            bob.life shouldBe 18
            carol.life shouldBe 18
            alice.life shouldBe 20
        }
    }

    // CR 603.2, 119.3: each opponent loses 1 for every instant or sorcery cast, but not for a creature spell
    @Test
    fun wyrdcallerDrainsEveryOpponentWhenYouCastAnInstantOrSorcery() = scenario(players = 3) {
        alice.battlefield("Island", "Island", wyrd)
        alice.hand(scroll1, scroll2, bear)
        alice.library(page[0], page[1], page[2])
        alice.casts(scroll1)
        resolveAll()
        expect {
            bob.life shouldBe 19
            carol.life shouldBe 19
        }
        alice.casts(scroll2)
        resolveAll()
        expect {
            bob.life shouldBe 18
            carol.life shouldBe 18
        }
    }

    // CR 601.2b, 118.8, 701.9a: the discard of two cards from the hand is paid on top of the mana cost
    @Test
    fun wyrdcallerCastFromTheGraveyardDiscardsTwoCardsFromHand() = scenario {
        alice.battlefield("Island", "Swamp", "Swamp", "Swamp")
        alice.graveyard(wyrd)
        alice.hand(bear, ogre, cleric)
        alice.casts(wyrd, choosing = listOf(bear, ogre))
        resolveAll()
        expect {
            wyrd.zone shouldBe Zone.Battlefield
            bear.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Graveyard
            cleric.zone shouldBe Zone.Hand
        }
    }

    @Test
    fun wyrdcallerCannotBeCastFromTheGraveyardWithOnlyOneCardToDiscard() = scenario {
        alice.battlefield("Island", "Swamp", "Swamp", "Swamp")
        alice.graveyard(wyrd)
        alice.hand(bear)
        runCatching { alice.casts(wyrd) }
        expect {
            wyrd.zone shouldBe Zone.Graveyard
            bear.zone shouldBe Zone.Hand
        }
    }

    // CR 613.4c: other Elves only, your own only; CR 601.2 (cast trigger): pay {G} on resolution, then draw
    @Test
    fun thornseerGivesOtherElvesPlusOneAndDrawsForAnElfSpellWhenPaid() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", seer, elf)
        bob.battlefield(Cards.invent("Brindle Foe Elf", "{G}", "Creature — Elf", "", "1/1"))
        alice.hand(elfSpell)
        alice.library(page[0], page[1])
        expect {
            seer.power shouldBe 1
            elf.power shouldBe 2
            "Brindle Foe Elf".power shouldBe 1
        }
        alice.casts(elfSpell)
        resolveAll()
        if (pending is Decision.May) may(true)
        payIfAsked()
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    @Test
    fun thornseerDrawsNothingWhenYouDoNotPay() = scenario {
        alice.battlefield("Forest", "Forest", seer)
        alice.hand(elfSpell)
        alice.library(page[0], page[1])
        alice.casts(elfSpell)
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect { alice.hand.size shouldBe 0 }
    }

    // CR 701.21a: only a permanent you control, and not the Acolyte itself, can be sacrificed to "another Cleric"
    @Test
    fun acolyteSacrificesAnotherClericToPutABlackCreatureOntoTheBattlefield() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", acolyte, cleric)
        alice.library(blackBeast, bear)
        alice.activates(acolyte, choosing = listOf(cleric))
        resolveAll()
        alice.chooses(blackBeast)
        resolveAll()
        expect {
            cleric.zone shouldBe Zone.Graveyard
            acolyte.zone shouldBe Zone.Battlefield
            blackBeast.zone shouldBe Zone.Battlefield
            acolyte.power shouldBe 2
        }
    }

    @Test
    fun acolyteCannotSacrificeItselfOrAnOpponentsCleric() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", acolyte)
        bob.battlefield(Cards.invent("Brindle Foe Cleric", "{B}", "Creature — Human Cleric", "", "1/1"))
        alice.library(blackBeast)
        assertFailsWith<IllegalArgumentException> { alice.activates(acolyte, choosing = listOf("Brindle Foe Cleric")) }
        assertFailsWith<IllegalArgumentException> { alice.activates(acolyte, choosing = listOf(acolyte)) }
        expect { acolyte.zone shouldBe Zone.Battlefield }
    }

    // CR 701.6a, 118.12: the spell is countered unless its controller pays; CR 701.21a: the Merfolk may be the source itself
    @Test
    fun spellsnareCountersANoncreatureSpellUnlessItsControllerPays() = scenario {
        alice.battlefield("Island", snare, merfolk)
        bob.battlefield("Island", "Island", "Island", "Island")
        bob.hand(scroll1, scroll2)
        expect { merfolk.power shouldBe 2 }
        turn(active = bob)
        bob.casts(scroll1)
        bob.passes()
        alice.activates(snare, target = scroll1, choosing = listOf(merfolk))
        resolveAll()
        if (pending is Decision.ManaPayment) payIfAsked() else if (pending is Decision.May) may(false)
        resolveAll()
        expect { merfolk.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun spellsnareCannotSacrificeAnOpponentsMerfolk() = scenario {
        alice.battlefield("Island", snare)
        bob.battlefield("Island", "Island", merfolk)
        bob.hand(scroll1)
        turn(active = bob)
        bob.casts(scroll1)
        bob.passes()
        // only the Spellsnare itself is a Merfolk alice controls, so it is the one sacrificed
        alice.activates(snare, target = scroll1)
        expect {
            merfolk.zone shouldBe Zone.Battlefield
            snare.zone shouldBe Zone.Graveyard
        }
    }

    // CR 610.3: the creature returns when the enchantment leaves the battlefield
    @Test
    fun writExilesAnOpponentsCreatureUntilItLeaves() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(writ, smash)
        bob.battlefield(ogre)
        alice.casts(writ)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Exile
        }
        alice.casts(smash, target = writ)
        resolveAll()
        expect {
            writ.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Battlefield
        }
    }

    // CR 400.7, 608.2b: a counter engine: sacrificing another creature, then +1/+1 on each creature you control
    @Test
    fun corvaneSacrificesAnotherCreatureForACounterOnEachCreatureYouControl() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", corvane, bear, cleric)
        bob.battlefield(ogre)
        alice.activates(corvane, choosing = listOf(cleric))
        resolveAll()
        expect {
            cleric.zone shouldBe Zone.Graveyard
            corvane.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            bear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            ogre.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    // CR 122.1, 202.3: the counters are placed first, then the limit is the new number of counters
    @Test
    fun maelisPutsAnArtifactUpToTheNumberOfCountersOntoTheBattlefield() = scenario {
        alice.battlefield(tinkerer)
        alice.hand(trinketSmall, trinketBig)
        alice.activates(tinkerer)
        resolveAll()
        alice.chooses(trinketSmall)
        resolveAll()
        expect {
            tinkerer.counters(CounterKind.Named("ingenuity")) shouldBe 2
            trinketSmall.zone shouldBe Zone.Battlefield
            trinketBig.zone shouldBe Zone.Hand
        }
    }

    @Test
    fun maelisCannotPutAnArtifactAboveTheNumberOfCounters() = scenario {
        alice.battlefield(tinkerer)
        alice.hand(trinketBig)
        alice.activates(tinkerer)
        resolveAll()
        chooseIfOffered(trinketBig)
        resolveAll()
        expect {
            tinkerer.counters(CounterKind.Named("ingenuity")) shouldBe 2
            trinketBig.zone shouldBe Zone.Hand
        }
    }

    // CR 603.2c, 603.4 ("once each turn"): two creatures dying at once give one counter, a later death in the same turn none
    @Test
    fun gourmandGrowsOncePerTurnWhenOneOrMoreOtherCreaturesDie() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", gourmand, bear, cleric)
        bob.battlefield(ogre)
        alice.hand(sweep, doom)
        alice.casts(sweep)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            cleric.zone shouldBe Zone.Graveyard
            gourmand.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
        alice.casts(doom, target = ogre)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Graveyard
            gourmand.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // CR 701.19, 701.20, 701.24: reveal the found card, put it into the hand, shuffle
    @Test
    fun automatonMaySearchForACreatureWithDefender() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(automaton)
        alice.library(bear, wall, ogre)
        alice.casts(automaton)
        resolveAll()
        if (pending is Decision.May) may(true)
        alice.chooses(wall)
        resolveAll()
        expect {
            wall.zone shouldBe Zone.Hand
            bear.zone shouldBe Zone.Library
        }
    }

    // CR 604.3 (characteristic-defining ability), 601.2f: power counts instants and sorceries in your graveyard; spells cost {1} less
    @Test
    fun zephyrPowerCountsInstantsAndSorceriesAndTheyCostOneLess() = scenario {
        alice.battlefield("Island", zephyr)
        alice.graveyard(scroll1, scroll2, bear)
        bob.battlefield("Island")
        bob.graveyard(foeScroll)
        alice.hand(preen)
        bob.hand(foePreen)
        alice.library(page[0], page[1])
        expect { zephyr.power shouldBe 2 }
        alice.casts(preen)
        resolveAll()
        expect {
            preen.zone shouldBe Zone.Graveyard
            zephyr.power shouldBe 3
        }
    }

    @Test
    fun zephyrDoesNotCheapenAnOpponentsSpells() = scenario {
        alice.battlefield(zephyr)
        bob.battlefield("Island")
        bob.hand(foePreen)
        runCatching { bob.casts(foePreen) }
        expect { foePreen.zone shouldBe Zone.Hand }
    }

    // CR 701.26a, 702.3: a defender may still tap target creature; the cost needs the Portcullis untapped
    @Test
    fun portcullisTapsATargetCreatureAndIsTappedItself() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", portcullis)
        bob.battlefield(ogre)
        alice.activates(portcullis, target = ogre)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            portcullis.tapped shouldBe true
        }
    }

    @Test
    fun portcullisCannotActivateWhileTapped() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.battlefield(portcullis, tapped = true)
        bob.battlefield(ogre)
        assertFailsWith<IllegalArgumentException> { alice.activates(portcullis, target = ogre) }
        expect { ogre.tapped shouldBe false }
    }

    // CR 701.26b, 115.1b: up to two targets, each untapped and given +2/+2 until end of turn
    @Test
    fun musterUntapsUpToTwoCreaturesAndPumpsThem() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.battlefield(bear, tapped = true)
        alice.battlefield(cleric, tapped = true)
        alice.hand(muster)
        alice.casts(muster, targets = listOf(bear, cleric))
        resolveAll()
        expect {
            bear.tapped shouldBe false
            cleric.tapped shouldBe false
            bear.power shouldBe 4
            cleric.power shouldBe 3
        }
    }

    @Test
    fun musterWorksWithOneTargetOnly() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.battlefield(bear, tapped = true)
        alice.hand(muster)
        alice.casts(muster, target = bear)
        resolveAll()
        expect {
            bear.tapped shouldBe false
            bear.toughness shouldBe 4
        }
    }
}
