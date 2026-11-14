package arcanum.engine.sets.lci

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.backFaceUp
import arcanum.engine.sets.rtr.resolveAll
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** LCI chunk 04: descend N (N or more permanent cards in your graveyard, CR 700.11) played, a surveil counting the cards it kept on top, a
 * reflexive mill of the discarded card's mana value and a permanent becoming a copy of a spell. Invented names carry the oracle text. */
class LciChunk04DescendScenariosTest {
    private val marmot = Cards.invent("Sunning Marmot", "{1}{G}", "Creature — Capybara",
        "Descend 4 — This creature gets +3/+0 as long as there are four or more permanent cards in your graveyard.", "1/3")
    private val grubhollow = Cards.invent("Grubhollow, the Fuming Spire", "{1}{B}{G}", "Legendary Creature — Fungus", """
        Descend 4 — As long as there are four or more permanent cards in your graveyard, Grubhollow gets +2/+2 and has trample.
        Descend 8 — As long as there are eight or more permanent cards in your graveyard, Grubhollow gets an additional +2/+2 and can't be blocked by more than one creature.
    """, "3/3")
    private val plunge = Cards.invent("Plunge into Gloom", "{1}{B}{B}", "Instant", """
        Target creature gets -5/-5 until end of turn.
        Descend 4 — That creature gets -10/-10 until end of turn instead if there are four or more permanent cards in your graveyard.
    """)
    private val wraithkin = Cards.invent("Hungering Wraithkin", "{2}{B}{B}", "Creature — Spirit Horror", """
        When this creature enters, surveil 2. Then for each card you put on top of your library, you draw a card and you lose 3 life.
        Descend 8 — Whenever you draw a card, if there are eight or more permanent cards in your graveyard, target opponent loses 1 life and you gain 1 life.
    """, "4/4")
    private val elder = Cards.invent("The Drowned Elder", "{U}{B}", "Legendary Creature — Spirit God", """
        Descend 8 — The Drowned Elder can't attack or block unless there are eight or more permanent cards in your graveyard.
        {2}{U}{B}: Draw a card, then discard a card. When you discard a card this way, target player mills cards equal to its mana value.
    """, "8/8")
    private val cistern = Cards.inventFaces("Transform",
        face("The Brimming Cistern", "{2}{U}", "Legendary Artifact", """
            When The Brimming Cistern enters, mill two cards, then draw two cards.
            Descend 8 — At the beginning of your upkeep, if there are eight or more permanent cards in your graveyard, transform The Brimming Cistern.
        """, null, null, null),
        face("The Thousand Springs", null, "Legendary Artifact Land", """
            (Transforms from The Brimming Cistern.)
            {T}: Add {U}.
            Whenever you cast a permanent spell using mana produced by The Thousand Springs, up to one other target permanent you control becomes a copy of that spell until end of turn.
        """, null, null, null))
    private val ogre = Cards.invent("Riverside Ogre", "{2}{R}", "Creature — Ogre", "", "4/4")
    private val brute = Cards.invent("Cavern Brute", "{1}{R}", "Creature — Ogre", "", "2/2")
    private val beast = Cards.invent("Hulking Grotto Beast", "{5}{G}", "Creature — Beast", "", "7/7")
    private val idol = Cards.invent("Rusted Idol", "{1}", "Artifact", "")

    // CR 700.11, 110.4b: only permanent cards in your own graveyard count (an instant and the opponent's cards don't); CR 613.4c
    @Test
    fun descendCountsPermanentCardsInYourGraveyard() = scenario {
        alice.battlefield(marmot)
        alice.graveyard("Forest", "Forest", ogre, plunge)
        alice.hand("Island")
        bob.graveyard("Plains", "Plains", "Plains", "Plains")
        expect { marmot.power shouldBe 1 }
        move("Island", Zone.Graveyard)
        expect { marmot.power shouldBe 4 }
    }

    // CR 613.4c: both bonuses with eight permanent cards (+4/+4); CR 509.1b: no more than one blocker
    @Test
    fun grubhollowWithEightGetsBothBonuses() = scenario {
        alice.battlefield(grubhollow)
        alice.graveyard("Forest", "Forest", "Forest", "Forest", "Swamp", "Swamp", "Swamp", "Swamp")
        bob.battlefield(ogre, brute)
        expect {
            grubhollow.power shouldBe 7
            grubhollow.toughness shouldBe 7
            grubhollow hasKeyword Keyword.Trample.kind
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(grubhollow)
        allPass()
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to grubhollow, brute to grubhollow) }
    }

    // CR 608.2c: with four permanent cards as it resolves, -10/-10 instead of -5/-5
    @Test
    fun plungeGivesMinusTenWithDescend() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.graveyard("Forest", "Forest", "Forest", "Forest")
        alice.hand(plunge)
        bob.battlefield(beast)
        alice.casts(plunge, target = beast)
        allPass()
        expect { bob.graveyard has beast }
    }

    // CR 608.2c: without descend 4, only -5/-5
    @Test
    fun plungeGivesMinusFiveWithoutDescend() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.graveyard("Forest", "Forest", "Forest", plunge)
        alice.hand(plunge)
        bob.battlefield(beast)
        alice.casts(plunge, target = beast)
        allPass()
        expect {
            beast.power shouldBe 2
            beast.toughness shouldBe 2
        }
    }

    // CR 701.25a: one card into the graveyard, one kept on top: one draw and 3 life; fewer than eight permanent cards, no draw trigger
    @Test
    fun wraithkinDrawsForEachCardKeptOnTop() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(wraithkin)
        alice.library("Island", "Forest", "Plains")
        alice.casts(wraithkin)
        resolveAll()
        alice.chooses("Island")
        resolveAll()
        expect {
            alice.graveyard has "Island"
            alice.hand has "Forest"
            alice.library.size shouldBe 1
            alice.life shouldBe 17
            stack.isEmpty()
        }
    }

    // CR 701.25a, 603.4: both kept, two draws (6 life); with eight permanent cards each draw triggers, each opponent targeted in turn
    @Test
    fun wraithkinWithDescendDrainsForEachDraw() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard("Forest", "Forest", "Forest", "Forest", "Plains", "Plains", "Plains", "Plains")
        alice.hand(wraithkin)
        alice.library("Island", "Mountain", "Island")
        alice.casts(wraithkin)
        resolveAll()
        alice.chooses()
        // the two kept cards' order on top
        alice.chooses("Island", "Mountain")
        resolveAll()
        alice.answers(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        alice.answers(Answer.Targets(listOf(TargetRef.Player(carol.id))))
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.life shouldBe 16
            bob.life shouldBe 19
            carol.life shouldBe 19
        }
    }

    // CR 603.12, 608.2h: the reflexive trigger mills the discarded card's mana value (3); CR 506.5: it can't attack without descend 8
    @Test
    fun elderMillsTheDiscardedCardsManaValue() = scenario {
        alice.battlefield(elder, "Island", "Island", "Swamp", "Swamp")
        alice.hand(ogre)
        alice.library("Forest")
        bob.library("Plains", "Plains", "Plains", "Plains")
        alice.activates(elder)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        alice.answers(Answer.Targets(listOf(TargetRef.Player(bob.id))))
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.hand has "Forest"
            bob.graveyard.size shouldBe 3
            bob.library.size shouldBe 1
        }
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(elder) }
    }

    // CR 106.3, 707.2, 707.2b: paid for with The Thousand Springs' mana, the Idol becomes a copy of the Ogre spell until end of turn (CR 514.2)
    @Test
    fun thousandSpringsMakesACopyOfTheSpell() = scenario {
        alice.battlefield(cistern, "Mountain", "Mountain", idol)
        backFaceUp(cistern)
        alice.hand(ogre)
        alice.casts(ogre, paying = listOf(cistern, "Mountain", "Mountain#1"))
        resolveAll()
        alice.answers(Answer.Targets(listOf(target(idol))))
        resolveAll()
        expect {
            alice.battlefield has ogre
            idol.power shouldBe 4
            (CardType.Creature in idol.types) shouldBe true
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            idol.power shouldBe null
            (CardType.Creature in idol.types) shouldBe false
        }
    }
}
