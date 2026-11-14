package arcanum.engine.sets.otj

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** OTJ chunk 17: sacrifice-three put-from-hand, a cast-only return, the creature-spell discount, looting, "it" after a counter on this creature. */
class OtjChunk17MoveCountersSearchTapScenariosTest {
    private val akur = Cards.invent("Akur the Unyielding", "{B}{B}{R}{R}", "Legendary Creature — Scorpion Dragon Rogue", """
        Flying, trample
        Sacrifice three other creatures: You may put a creature card from your hand onto the battlefield. Activate only as a sorcery and only once each turn.
    """, "5/5")
    private val annie = Cards.invent("Annie Flare, the Veteran", "{3}{R}{G}{W}", "Legendary Creature — Human Rogue", """
        Flash
        When Annie Flare enters, if you cast it, return target permanent card with mana value 3 or less from your graveyard to the battlefield tapped.
        Whenever Annie Flare becomes tapped, exile the top two cards of your library. You may play those cards this turn.
    """, "4/5")
    private val honest = Cards.invent("Honest Rutherford", "{1}{B}{G}", "Legendary Creature — Human Warlock", """
        When Honest Rutherford enters, return target creature card from your graveyard to your hand.
        Creature spells you cast cost {1} less to cast.
    """, "3/2")
    private val strix = Cards.invent("Harrier Strike", "{U}", "Creature — Bird", """
        Flying
        When this creature enters, tap target permanent.
        {2}{U}: Draw a card, then discard a card.
    """, "1/1")
    private val dazzler = Cards.invent("Rascal Dazzler", "{1}{U}", "Creature — Human Wizard", "Whenever you cast your second spell each turn, put a +1/+1 counter on this creature. It can't be blocked this turn.", "1/2")
    private val hare = Cards.invent("Dune Hare", "{G}", "Creature — Rabbit", "", "1/1")
    private val jackal = Cards.invent("Salt Jackal", "{1}{R}", "Creature — Jackal", "", "2/1")
    private val wyrm = Cards.invent("Cliff Wyrm", "{3}{R}", "Creature — Dragon", "", "3/3")
    private val boar = Cards.invent("Mesa Boar", "{2}{G}", "Creature — Boar", "", "3/2")
    private val rat = Cards.invent("Gully Rat", "{1}{B}", "Creature — Rat", "", "1/1")
    private val toad = Cards.invent("Bog Toad", "{B}", "Creature — Frog", "", "1/1")
    private val rock = Cards.invent("Mesa Rock", "{5}", "Artifact", "")
    private val tapper = Cards.invent("Frost Tap", "{U}", "Instant", "Tap target creature.")
    private val quiet = Cards.invent("Soft Cantrip", "{U}", "Instant", "You gain 1 life.")
    private val loud = Cards.invent("Bright Cantrip", "{R}", "Instant", "You gain 1 life.")
    private val blocker = Cards.invent("Reed Sentry", "{1}{G}", "Creature — Human Scout", "", "2/2")

    // CR 118.12 (sacrifice as a cost), 701.21a, 602.5d (sorcery speed), 602.5b (only once each turn)
    @Test
    fun akurPutsACreatureFromHandOncePerTurn() = scenario {
        alice.battlefield(akur, hare, jackal, wyrm, boar, rat, toad)
        alice.hand(blocker, rat)
        alice.activates(akur, choosing = listOf(hare, jackal, wyrm))
        resolveAll()
        alice.chooses(blocker)
        resolveAll()
        expect {
            alice.battlefield has blocker
            alice.graveyard has hare
            alice.graveyard has jackal
            alice.graveyard has wyrm
        }
        assertFailsWith<IllegalArgumentException> { alice.activates(akur, choosing = listOf(boar, rat, toad)) }
    }

    // CR 603.4 (intervening if), 110.5b, 603.2 ("becomes tapped"): only a cast Annie returns a card, tapped; tapping her exiles two cards
    @Test
    fun annieReturnsATappedCardWhenCastAndExilesWhenTapped() = scenario {
        alice.battlefield("Mountain", "Forest", "Plains", "Island", "Island", "Island", "Island")
        alice.hand(annie, tapper)
        alice.graveyard(rat, rock)
        alice.library("Swamp", "Swamp", "Swamp")
        alice.casts(annie)
        resolveAll()
        expect {
            alice.battlefield has rat
            card(rat).tapped shouldBe true
            alice.graveyard has rock
        }
        alice.casts(tapper, target = annie)
        resolveAll()
        expect { alice.exile.names.size shouldBe 2 }
    }

    // CR 601.2f: creature spells you cast cost {1} less
    @Test
    fun honestDiscountsCreatureSpells() = scenario {
        alice.battlefield(honest, "Forest")
        alice.hand(blocker)
        alice.casts(blocker)
        resolveAll()
        expect { alice.battlefield has blocker }
    }

    // CR 701.9a (discard), 121.1: draw a card, then discard a card
    @Test
    fun harrierLoots() = scenario {
        alice.battlefield(strix, "Island", "Island", "Island")
        alice.hand(hare)
        alice.library("Swamp")
        alice.activates(strix)
        resolveAll()
        alice.chooses(hare)
        resolveAll()
        expect {
            alice.graveyard has hare
            alice.hand has "Swamp"
        }
    }

    // CR 603.2, 509.1b: the second spell puts a counter on the Dazzler and makes the Dazzler (not the spell) unblockable
    @Test
    fun secondSpellMakesTheDazzlerUnblockable() = scenario {
        alice.battlefield(dazzler, "Island", "Island", "Mountain")
        bob.battlefield(blocker)
        alice.hand(quiet, loud)
        alice.casts(quiet)
        resolveAll()
        expect { dazzler.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0 }
        alice.casts(loud)
        resolveAll()
        expect {
            dazzler.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            card(dazzler).power shouldBe 2
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(dazzler)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(blocker to dazzler) }
    }
}
