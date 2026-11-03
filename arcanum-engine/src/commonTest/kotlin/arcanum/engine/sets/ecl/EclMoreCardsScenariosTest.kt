package arcanum.engine.sets.ecl

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** More Lorwyn Eclipsed cards in play: abilities given to others (persist, hexproof from a creature's own colours), a Mutavault token,
 * the first tokens each turn replaced by copies, extra triggers of Elementals, creatures copied when they come back from a graveyard,
 * and casting from among exiled cards by removing counters. */
class EclMoreCardsScenariosTest {
    private val m1m1 = CounterKind.PowerToughness(-1, -1)
    private val nocthra = Cards.invent("Nocthra Test Spirit", "{3}{W}{B}", "Legendary Creature — Elemental God", """
        Flying, lifelink
        Each other nontoken creature you control has persist. (When it dies, if it had no -1/-1 counters on it, return it to the battlefield under its owner's control with a -1/-1 counter on it.)
    """, "5/5")
    private val ressa = Cards.invent("Ressa, Thoughtful Freshling", "{1}{G/U}", "Legendary Creature — Gorgon Wizard", """
        Each other creature you control has hexproof from each of its colors.
        {T}: Target creature you control becomes all colors until end of turn.
    """, "2/2")
    private val wanderer = Cards.invent("Shifting Wanderer", "{2}{G}", "Creature — Shapeshifter", """
        Changeling (This card is every creature type.)
        When this creature enters, create a tapped Mutavault token. (It's a land with "{T}: Add {C}" and "{1}: This token becomes a 2/2 creature with all creature types until end of turn. It's still a land.")
    """, "1/1")
    private val circlet = Cards.invent("Glassthought Circlet", "{4}", "Artifact — Equipment", """
        As long as this Equipment is attached to a creature, the first time you would create one or more tokens each turn, you may instead create that many tokens that are copies of equipped creature.
        Equip {2}
    """)
    private val pairblaze = Cards.invent("Pairblaze Wanderers", "{2}{U}{R}", "Creature — Elemental Sorcerer", """
        Flying
        If a triggered ability of another Elemental you control triggers, it triggers an additional time.
    """, "3/3")
    private val gleamdiver = Cards.invent("Gleamdiver", "{4}{U}{U}", "Creature — Elemental", """
        Flying
        Vivid — When this creature enters, draw cards equal to the number of colors among permanents you control.
    """, "3/3")
    private val augur = Cards.invent("Dusk Augur", "{2}{B}", "Creature — Elf Cleric", """
        When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        Whenever one or more other creatures you control enter, if they entered or were cast from a graveyard, create a token that's a copy of one of them. This ability triggers only once each turn.
    """, "3/3")
    private val reanimate = Cards.invent("Grave Call", "{1}{B}", "Sorcery", "Return target creature card from your graveyard to the battlefield.")
    private val hamletFair = Cards.invent("Hamlet Fair", "{2}{W}", "Kindred Enchantment — Kithkin", """
        When this enchantment enters, create two 1/1 green and white Kithkin creature tokens.
        {4}{W}: Create a 1/1 green and white Kithkin creature token.
    """)
    private val bear = Cards.invent("Plaza Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Plaza Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val jolt = Cards.invent("Spark Jolt", "{R}", "Instant", "Spark Jolt deals 2 damage to any target.")
    private val bolt = Cards.invent("Searing Bolt", "{R}", "Instant", "Searing Bolt deals 3 damage to any target.")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 702.79a: a creature given persist returns with a -1/-1 counter
    @Test
    fun persistGivenToOtherCreatures() = scenario {
        alice.battlefield(nocthra, ogre)
        bob.battlefield("Mountain")
        bob.hand(bolt)
        turn(active = bob)
        bob.casts(bolt, target = ogre)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Battlefield
            ogre.counters(m1m1) shouldBe 1
        }
    }

    // CR 702.11d: hexproof from the creature's own colours: a red spell can't target a red creature
    @Test
    fun hexproofFromItsOwnColors() = scenario {
        alice.battlefield(ressa, ogre, bear)
        bob.battlefield("Mountain")
        bob.hand(jolt)
        turn(active = bob)
        kotlin.test.assertFailsWith<IllegalArgumentException> { bob.casts(jolt, target = ogre) }
    }

    // the green bear has no hexproof from red
    @Test
    fun hexproofFromOtherColorsDoesntStopIt() = scenario {
        alice.battlefield(ressa, ogre, bear)
        bob.battlefield("Mountain")
        bob.hand(jolt)
        turn(active = bob)
        bob.casts(jolt, target = bear)
        resolveAll()
        expect { alice.graveyard has bear }
    }

    // CR 111.4: the Mutavault land token
    @Test
    fun aMutavaultToken() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(wanderer)
        alice.casts(wanderer)
        resolveAll()
        expect {
            alice.battlefield has "Mutavault"
            "Mutavault".tapped shouldBe true
            "Mutavault".types shouldBe listOf(arcanum.api.CardType.Land)
        }
    }

    // CR 614.1a: the first tokens of the turn become copies of the equipped creature
    @Test
    fun theFirstTokensAreCopies() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", ogre, circlet)
        alice.hand(hamletFair)
        attach(circlet, ogre)
        alice.casts(hamletFair)
        resolveAll()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        resolveAll()
        expect { alice.battlefield.names.count { it == "Plaza Ogre" } shouldBe 3 }
    }

    // CR 603.2d: another Elemental's enters trigger triggers twice
    @Test
    fun anElementalsTriggerTwice() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", pairblaze)
        alice.library(bear, bear, bear, bear, bear, bear)
        alice.hand(gleamdiver)
        alice.casts(gleamdiver)
        resolveAll()
        // blue and red: two cards, twice
        assertEquals(4, state.player(alice.id).hand.size)
    }

    // CR 603.4: a creature that came back from a graveyard is copied, once each turn
    @Test
    fun copiedWhenReturnedFromAGraveyard() = scenario {
        alice.battlefield("Swamp", "Swamp", augur)
        alice.graveyard(ogre)
        alice.hand(reanimate)
        alice.casts(reanimate, target = ogre)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Plaza Ogre" } shouldBe 2 }
    }
}
