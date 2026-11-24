package arcanum.engine.sets.neo

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.CardRef
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dgm.payIfAsked
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Chunk 10 of NEO: a count taken as the spell was cast, a bonus per creature sharing its name, the first activated ability of an artifact each turn
 * costing less (mana abilities included), an artifact made a creature unless it's a Vehicle, a ninjutsu activation trigger, "up to two of those
 * cards" played, a keyword counter chosen for each token, a face-down exile cast only as a cheap instant, an exile-and-search by name with one card
 * playable for mana of any colour, and a dies trigger of a modified creature. */
class NeoChunk10EffectsAndTriggersScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private val overreach = Cards.invent("Fatal Overreach", "{1}{B}", "Instant",
        "Target creature gets -2/-2 until end of turn. It gets an additional -1/-1 until end of turn for each modified creature you controlled as you cast this spell.")
    private val ox = Cards.invent("Neon Bulwark Ox", "{3}{G}", "Creature — Ox", "", "5/5")
    private val sentry = Cards.invent("Cloned Sentry", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val crate = Cards.invent("Looking-Glass Crate", "{3}", "Artifact", """
        The "legend rule" doesn't apply to permanents you control.
        Each legendary creature you control gets +1/+1.
        Each nontoken creature you control gets +1/+1 for each other creature you control with the same name as that creature.
    """)
    private val hoshi = Cards.invent("Hoshi Twinblade", "{1}{R}", "Legendary Creature — Human Samurai", "", "2/2")
    private val vexxeret = Cards.invent("Vexxeret, Metal Betrayer", "{2}{U}{U}", "Legendary Planeswalker — Tezzeret", """
        The first activated ability of an artifact you activate each turn costs {2} less to activate.
        [+1]: Draw two cards. Then discard two cards unless you discard an artifact card.
        [−2]: Target artifact becomes an artifact creature. If it isn't a Vehicle, it has base power and toughness 4/4.
        [−6]: You get an emblem with "Whenever an artifact you control becomes tapped, draw a card."
    """, loyalty = "4")
    private val relic = Cards.invent("Humming Relic", "{1}", "Artifact", "{3}: You gain 1 life.")
    private val pebble = Cards.invent("Ember Pebble", "{1}", "Artifact", "{T}: Add {R}.")
    private val wagon = Cards.invent("Rust Rider Wagon", "{2}", "Artifact — Vehicle", "Crew 1", "3/2")
    private val sakuro = Cards.invent("Sakuro Umeda", "{1}{U}{B}", "Legendary Creature — Human Ninja", """
        Whenever you activate a ninjutsu ability, look at the top three cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order. This ability triggers only once each turn.
        Each creature card in your hand has ninjutsu {2}{U}{B}.
    """, "2/4")
    private val parade = Cards.invent("Parade of Wild Mirth", "{X}{R}", "Instant", """
        As an additional cost to cast this spell, you may exile any number of red cards from your hand. This spell costs {2} less to cast for each card exiled this way.
        Exile the top X cards of your library. You may play up to two of those cards until the end of your next turn.
    """)
    private val echo = Cards.invent("Gilded Echo", "{0}", "Instant", "You gain 1 life.")
    private val costlyEcho = Cards.invent("Tri-Gilded Echo", "{2}{U}", "Instant", "You gain 3 life.")
    private val insight = Cards.invent("Shard of Insight", "{0}", "Instant", "You gain 2 life.")
    private val elders = Cards.invent("Summon the Elders", "{1}{G}{G}{G}{G}", "Sorcery",
        "Create two 4/5 green Spirit creature tokens. For each of them, put your choice of a reach counter, a vigilance counter, or a trample counter on it.")
    private val uncover = Cards.invent("Uncover the Unthinkable", "{2}{U}", "Instant",
        "Look at the top five cards of your library. Exile one of them face down and put the rest on the bottom of your library in a random order. You may cast the exiled card without paying its mana cost if it's an instant spell with mana value 2 or less. If you don't, put that card into your hand.")
    private val kotomi = Cards.invent("Kotomi, the Quiet Weaver", "{3}{U}{B}", "Legendary Creature — Human Ninja",
        "When Kotomi enters, exile target card other than a basic land card from an opponent's graveyard. Search that player's graveyard, hand, and library for any number of cards with the same name as that card and exile them. Then that player shuffles. For as long as you control Kotomi, you may play one of the exiled cards, and you may spend mana as though it were mana of any color to cast it.", "4/4")
    private val flare = Cards.invent("Cinder Flare Rite", "{R}", "Sorcery", "You gain 2 life.")
    private val warden = Cards.invent("Akki Cinder-Warden", "{1}{R}", "Enchantment Creature — Goblin Warrior",
        "Whenever a nontoken modified creature you control dies, create a 1/1 colorless Spirit creature token.", "2/1")

    // CR 601.2i: the modified creature is counted as the spell is cast; it leaving before resolution changes nothing (-2/-2 and -1/-1)
    @Test
    fun overreachCountsModifiedCreaturesAsCast() = scenario {
        alice.battlefield(sentry, counters = listOf(p1p1 to 1))
        alice.battlefield("Swamp", "Swamp")
        alice.hand(overreach)
        bob.battlefield(ox)
        alice.casts(overreach, target = ox)
        move(sentry, Zone.Graveyard)
        resolveAll()
        expect {
            ox.power shouldBe 2
            ox.toughness shouldBe 2
        }
    }

    // CR 700.9: an unmodified creature counts for nothing
    @Test
    fun overreachWithoutModifiedCreatures() = scenario {
        alice.battlefield(sentry, "Swamp", "Swamp")
        alice.hand(overreach)
        bob.battlefield(ox)
        alice.casts(overreach, target = ox)
        resolveAll()
        expect { ox.power shouldBe 3 }
    }

    // CR 704.5j (no legend rule for them), 201.2, 613.4c: each Sentry counts the other; each Hoshi gets +1/+1 twice; the Ox shares no name
    @Test
    fun crateCountsCreaturesSharingTheName() = scenario {
        alice.battlefield(crate, sentry, sentry, hoshi, hoshi, ox)
        settle()
        expect {
            card(sentry, 0).power shouldBe 3
            card(sentry, 1).toughness shouldBe 3
            card(hoshi, 0).power shouldBe 4
            card(hoshi, 1).power shouldBe 4
            ox.power shouldBe 5
        }
    }

    // CR 601.2f, 602.2b: the first activation costs {1}, the second the full {3}
    @Test
    fun vexxeretReducesOnlyTheFirstArtifactAbility() = scenario {
        alice.battlefield(vexxeret, counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield(relic, "Island", "Island", "Island", "Island")
        alice.activates(relic)
        payIfAsked()
        resolveAll()
        expect { alice.life shouldBe 21 }
        assertEquals(3, untappedIslands())
        alice.activates(relic)
        payIfAsked()
        resolveAll()
        expect { alice.life shouldBe 22 }
        assertEquals(0, untappedIslands())
    }

    // CR 118.12: "unless you discard an artifact card" asks for that action in plain words, not "Pay discard?"
    @Test
    fun vexxeretUnlessPromptNamesTheAction() = scenario {
        alice.battlefield(vexxeret, counters = listOf(CounterKind.Loyalty to 4))
        alice.hand(relic)
        alice.library("Island", "Island", "Island")
        advanceTo(TurnStep.PrecombatMain, turn = 1)
        alice.activates(vexxeret, ability = 0)
        resolveAll()
        assertEquals("Discard an artifact card?", decision<Decision.May>().text)
    }

    // CR 605.1a: a mana ability of an artifact is an activated ability too, so activating it first uses the reduction up ({3} paid in full)
    @Test
    fun vexxeretReductionIsUsedUpByAManaAbility() = scenario {
        alice.battlefield(vexxeret, counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield(pebble, relic, "Island", "Island")
        alice.activates(pebble)
        alice.activates(relic)
        payIfAsked()
        resolveAll()
        expect { alice.life shouldBe 21 }
        assertEquals(0, untappedIslands())
    }

    // CR 205.1b, 613.4b: a non-Vehicle artifact becomes a 4/4 artifact creature
    @Test
    fun vexxeretMakesAnArtifactAFourFour() = scenario {
        alice.battlefield(vexxeret, counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield(relic)
        alice.activates(vexxeret, ability = 1, target = relic)
        resolveAll()
        expect {
            assertTrue(CardType.Creature in relic.types && CardType.Artifact in relic.types)
            relic.power shouldBe 4
            relic.toughness shouldBe 4
        }
    }

    // CR 301.7: a Vehicle that becomes a creature keeps its printed power and toughness
    @Test
    fun vexxeretLeavesAVehiclesPowerAndToughness() = scenario {
        alice.battlefield(vexxeret, counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield(wagon)
        alice.activates(vexxeret, ability = 1, target = wagon)
        resolveAll()
        expect {
            assertTrue(CardType.Creature in wagon.types)
            wagon.power shouldBe 3
            wagon.toughness shouldBe 2
        }
    }

    // CR 702.49a: activating the granted ninjutsu triggers; one of the top three goes to the hand
    @Test
    fun sakuroLooksWhenNinjutsuIsActivated() = scenario {
        alice.battlefield(sakuro, sentry, "Island", "Island", "Swamp", "Swamp")
        alice.hand(ox)
        alice.library("Plains", "Forest", "Mountain", "Plains")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(sentry)
        advanceTo(TurnStep.DeclareBlockers)
        alice.activates(ox)
        payIfAsked()
        resolve()
        alice.chooses("Forest")
        while (pending is Decision.ChooseObjects) answer(Answer.Objects((pending as Decision.ChooseObjects).options))
        resolveAll()
        expect {
            alice.hand has "Forest"
            alice.hand has sentry
            alice.battlefield has ox
        }
    }

    // CR 611.2a: two of the three exiled cards may be played, then no more
    @Test
    fun paradeLetsYouPlayTwoOfTheExiledCards() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(parade)
        alice.library(echo, insight, "Forest", "Plains")
        alice.casts(parade, x = 3)
        answer(Answer.Objects(emptyList()))
        payIfAsked()
        resolveAll()
        alice.casts(echo)
        resolveAll()
        alice.casts(insight)
        resolveAll()
        expect {
            alice.life shouldBe 23
            alice.exile has "Forest"
        }
        assertTrue(Engine.options(state, alice.id).none { it is Input.PlayLand && it.card == card("Forest") })
    }

    // CR 122.1b, 608.2c: each token gets the counter chosen for it
    @Test
    fun eldersPutsAChosenCounterOnEachToken() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(elders)
        alice.casts(elders)
        resolveAll()
        answer(Answer.Option("reach"))
        answer(Answer.Option("trample"))
        resolveAll()
        val spirits = tokens("Spirit Token")
        assertEquals(2, spirits.size)
        expect {
            CardRef(spirits[0], "first").counters(CounterKind.Keyword(Keyword.Reach.kind)) shouldBe 1
            CardRef(spirits[1], "second").counters(CounterKind.Keyword(Keyword.Trample.kind)) shouldBe 1
            CardRef(spirits[1], "second") hasKeyword Keyword.Trample.kind
        }
    }

    // CR 406.3a-b: the face-down card is turned face up and cast free as an instant with mana value 2 or less
    @Test
    fun uncoverCastsACheapInstantFromFaceDownExile() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(uncover)
        alice.library(echo, "Plains", "Forest", "Mountain", "Swamp")
        alice.casts(uncover)
        resolveAll()
        alice.chooses(echo)
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.life shouldBe 21
            alice.graveyard has echo
            alice.hand lacks echo
        }
    }

    // CR 406.3b: an instant of mana value 3 isn't cast; it goes to the hand
    @Test
    fun uncoverPutsACostlierInstantIntoTheHand() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(uncover)
        alice.library(costlyEcho, "Plains", "Forest", "Mountain", "Swamp")
        alice.casts(uncover)
        resolveAll()
        alice.chooses(costlyEcho)
        if (pending is Decision.May) answer(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.life shouldBe 20
            alice.hand has costlyEcho
            alice.exile.size shouldBe 0
        }
    }

    // CR 305.1: a land can't be cast, so it goes to the hand
    @Test
    fun uncoverPutsALandIntoTheHand() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(uncover)
        alice.library(echo, "Plains", "Forest", "Mountain", "Swamp")
        alice.casts(uncover)
        resolveAll()
        alice.chooses("Plains")
        if (pending is Decision.May) answer(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.hand has "Plains"
            alice.exile.size shouldBe 0
        }
    }

    // CR 701.23, 611.2a, 609.4b: every copy of the card is exiled, one of them can be cast with blue mana, then no other
    @Test
    fun kotomiExilesEveryCopyAndLetsOneBeCast() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Swamp")
        alice.hand(kotomi)
        bob.graveyard(flare)
        bob.hand(flare)
        bob.library(flare, "Plains")
        alice.casts(kotomi, paying = listOf("Island", "Island#1", "Island#2", "Island#3", "Swamp"))
        resolveAll()
        while (pending is Decision.ChooseObjects) answer(Answer.Objects((pending as Decision.ChooseObjects).options))
        resolveAll()
        assertEquals(3, cards(flare, zone = Zone.Exile).size)
        expect { bob.hand.size shouldBe 0 }
        val ways = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().filter { state.obj(it.card)?.zone == Zone.Exile }
        assertEquals(3, ways.size)
        perform(ways.first())
        payIfAsked()
        resolveAll()
        expect { alice.life shouldBe 22 }
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Cast>().none { state.obj(it.card)?.zone == Zone.Exile })
    }

    // CR 700.9, 603.10a: a creature with a +1/+1 counter that dies was modified
    @Test
    fun wardenMakesASpiritWhenAModifiedCreatureDies() = scenario {
        alice.battlefield(warden)
        alice.battlefield(sentry, counters = listOf(p1p1 to 1))
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = sentry)
        resolveAll()
        assertEquals(1, tokens("Spirit Token").size)
    }

    // CR 700.9: an unmodified creature dying makes nothing
    @Test
    fun wardenIgnoresAnUnmodifiedCreature() = scenario {
        alice.battlefield(warden, sentry, "Mountain")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = sentry)
        resolveAll()
        assertEquals(0, tokens("Spirit Token").size)
    }

    private fun Scenario.untappedIslands() = cards("Island", zone = Zone.Battlefield).count { !it.tapped }
}
