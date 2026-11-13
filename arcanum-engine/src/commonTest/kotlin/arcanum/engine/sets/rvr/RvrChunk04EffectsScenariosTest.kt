package arcanum.engine.sets.rvr

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Supertype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Ravnica Remastered chunk 04 in play: opponents held to sorcery timing and a planeswalker's flash permission, the next sorcery cast as
 * though it had flash, a cost reduction for each chosen colour the spell is, a mana value limit by each opponent's own lands with 3 players,
 * a copy that enters with counters by what it copies (or dies as itself), an Aura moved to another permanent of the same controller, damage
 * to a destroyed permanent's controller, sacrifice-or-discard with 3 players, ten colour-pair choices and a graveyard return after three
 * instants and sorceries. */
class RvrChunk04EffectsScenariosTest {
    private val ombrel = Cards.invent("Ombrel, Tide Bender", "{1}{W}{U}", "Legendary Planeswalker — Teferi", """
        Each opponent can cast spells only any time they could cast a sorcery.
        [+1]: Until your next turn, you may cast sorcery spells as though they had flash.
        [−3]: Return up to one target artifact, creature, or enchantment to its owner's hand. Draw a card.
    """, loyalty = "4")
    private val quickNote = Cards.invent("Quick Note", "{U}", "Instant", "Draw a card.")
    private val ledgerGlimpse = Cards.invent("Ledger Glimpse", "{U}", "Sorcery", "Draw a card.")
    private val mossNote = Cards.invent("Moss Note", "{G}", "Sorcery", "Draw a card.")
    private val hastenThought = Cards.invent("Hasten Thought", "{U}", "Instant", """
        The next sorcery spell you cast this turn can be cast as though it had flash. (It can be cast any time you could cast an instant.)
        Draw a card.
    """)
    private val sigil = Cards.invent("Sigil of the Compact", "{5}", "Artifact", """
        As this artifact enters, choose two colors.
        Each spell you cast costs {1} less to cast for each of the chosen colors it is.
    """)
    private val concordEdict = Cards.invent("Concord Edict", "{2}{W}{U}", "Sorcery", "Draw a card.")
    private val corvessa = Cards.invent("Corvessa, Senate Dissenter", "{W}{U}", "Legendary Creature — Human Soldier", """
        Each opponent can't cast noncreature spells with mana value greater than the number of lands that player controls.
        Whenever an opponent casts a spell, if no mana was spent to cast it, counter that spell.
    """, "2/2")
    private val manaBead = Cards.invent("Mana Bead", "{1}", "Artifact", "{T}: Add {C}.")
    private val tideRebuke = Cards.invent("Tide Rebuke", "{2}{U}", "Instant", "Draw a card.")
    private val sternRecluse = Cards.invent("Stern Recluse", "{2}{U}", "Creature — Human", "", "2/2")
    private val glint = Cards.invent("Mirrored Glint", "{3}{U}", "Creature — Illusion",
        "You may have this creature enter as a copy of a creature or planeswalker you control, except it enters with an additional +1/+1 counter on it if it's a creature, it enters with an additional loyalty counter on it if it's a planeswalker, and it isn't legendary.",
        "0/0")
    private val vorel = Cards.invent("Vorel the Bold", "{1}{W}", "Legendary Creature — Human Knight", "", "2/2")
    private val talia = Cards.invent("Oathsworn Talia", "{2}{W}", "Legendary Planeswalker — Gideon", "[+1]: You gain 1 life.", loyalty = "3")
    private val helixTender = Cards.invent("Helix Tender", "{G/U}{G/U}", "Creature — Elf Wizard", """
        ({G/U} can be paid with either {G} or {U}.)
        {1}{G}: Move a +1/+1 counter from target creature onto another target creature with the same controller.
        {1}{U}: Attach target Aura attached to a permanent to another permanent with the same controller.
    """, "2/2")
    private val brambleMantle = Cards.invent("Bramble Mantle", "{G}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+2.
    """)
    private val gravelHound = Cards.invent("Gravel Hound", "{1}{G}", "Creature — Dog", "", "2/2")
    private val ashthorn = Cards.invent("Ashthorn Creepers", "{R}{G}", "Enchantment", """
        Whenever an opponent casts a noncreature spell, this enchantment deals 1 damage to that player.
        {1}, Sacrifice this enchantment: Destroy target artifact or enchantment. This enchantment deals 2 damage to that permanent's controller.
    """)
    private val copperBauble = Cards.invent("Copper Bauble", "{1}", "Artifact", "")
    private val blightmonger = Cards.invent("Blightmonger", "{2}{B}", "Creature — Human Shaman",
        "When this creature enters, each player sacrifices a creature or planeswalker of their choice. Each player who can't discards a card.", "3/2")
    private val wyrmlord = Cards.invent("Prismatic Wyrmlord", "{W}{U}{B}{R}{G}", "Legendary Creature — Dragon Avatar", """
        Flying
        When Prismatic Wyrmlord enters, reveal the top ten cards of your library. For each color pair, choose a card that's exactly those colors from among them. Put the chosen cards into your hand and the rest on the bottom of your library in a random order.
    """, "6/6")
    private val cinderPact = Cards.invent("Cinder Pact", "{B}{R}", "Sorcery", "Draw a card.")
    private val phoenix = Cards.invent("Embercall Phoenix", "{3}{R}", "Creature — Phoenix", """
        Flying, haste
        At the beginning of combat on your turn, if you've cast three or more instant and sorcery spells this turn, return this card from your graveyard to the battlefield.
    """, "3/2")

    // ---- Sorcery timing

    /** On Alice's turn neither opponent may cast an instant, while Alice may. */
    // CR 307.5: only with priority in their own main phase on an empty Stack
    @Test
    fun opponentsCantCastInstantsOnYourTurn() = scenario(players = 3) {
        alice.battlefield(ombrel, "Island", counters = listOf(CounterKind.Loyalty to 4))
        bob.battlefield("Island")
        carol.battlefield("Island")
        bob.hand(quickNote)
        carol.hand(quickNote)
        alice.hand(quickNote)
        alice.library("Plains")
        turn(number = 1, active = alice, priority = bob)
        assertFailsWith<IllegalArgumentException> { bob.casts(quickNote) }
        bob.passes()
        assertFailsWith<IllegalArgumentException> { carol.casts(quickNote) }
        carol.passes()
        alice.casts(quickNote)
        expect { stack.size shouldBe 1 }
    }

    /** In his own main phase Bob casts an instant, but not a second one while the first is on the Stack. */
    // CR 307.5: the Stack must be empty, flash or instant
    @Test
    fun opponentCastsAnInstantOnlyOnAnEmptyStackInHisMainPhase() = scenario {
        alice.battlefield(ombrel, counters = listOf(CounterKind.Loyalty to 4))
        bob.battlefield("Island", "Island")
        bob.hand(quickNote, ledgerGlimpse)
        turn(number = 2, active = bob)
        bob.casts(quickNote)
        expect { stack.size shouldBe 1 }
        assertFailsWith<IllegalArgumentException> { bob.casts(ledgerGlimpse) }
    }

    /** After the +1 Alice casts a sorcery in Bob's upkeep. */
    // CR 702.8a, 611.2b: until her next turn
    @Test
    fun plusOneLetsYouCastSorceriesOnTheOpponentsTurn() = scenario {
        alice.battlefield(ombrel, "Island", counters = listOf(CounterKind.Loyalty to 4))
        alice.hand(ledgerGlimpse)
        alice.library("Plains", "Plains")
        bob.library("Plains", "Plains")
        alice.activates(ombrel, ability = 0)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 2)
        bob.passes()
        alice.casts(ledgerGlimpse)
        expect { stack.size shouldBe 1 }
    }

    /** Hasten Thought lets the next sorcery be cast on Bob's turn; the one after it still waits for a main phase. */
    // CR 611.2f, 702.8a: one spell, then the effect is used up
    @Test
    fun nextSorceryAsThoughItHadFlash() = scenario {
        alice.battlefield("Island", "Island", "Forest")
        alice.hand(hastenThought, ledgerGlimpse, mossNote)
        alice.library("Plains", "Plains", "Plains")
        turn(number = 2, active = bob, priority = alice)
        alice.casts(hastenThought)
        allPass()
        bob.passes()
        alice.casts(ledgerGlimpse, paying = listOf("Island#1"))
        allPass()
        bob.passes()
        assertFailsWith<IllegalArgumentException> { alice.casts(mossNote) }
    }

    // ---- Costs and limits

    /** White and blue chosen: a white and blue spell costs {2} less, so two lands pay for it. */
    // CR 105.2, 601.2f: {1} less for each chosen colour it is
    @Test
    fun costsLessForEachChosenColorItIs() = scenario {
        alice.battlefield("Plains", "Island")
        alice.hand(sigil, concordEdict)
        alice.library("Plains")
        alice.castsWithoutCosts(sigil)
        resolve()
        answer(Answer.ColorChoice(Color.White))
        answer(Answer.ColorChoice(Color.Blue))
        alice.casts(concordEdict)
        expect { stack has concordEdict }
        resolveAll()
        expect { alice.hand has "Plains" }
    }

    /** Bob has three mana but two lands: no three-mana instant, though a three-mana creature is fine; Carol with three lands casts it. */
    // CR 101.2: each opponent counts their own lands
    @Test
    fun manaValueLimitedByEachOpponentsOwnLands() = scenario(players = 3) {
        alice.battlefield(corvessa)
        bob.battlefield("Island", "Island", manaBead)
        bob.hand(tideRebuke, sternRecluse)
        carol.battlefield("Island", "Island", "Island")
        carol.hand(tideRebuke)
        turn(number = 2, active = bob)
        assertFailsWith<IllegalArgumentException> { bob.casts(tideRebuke) }
        bob.casts(sternRecluse)
        expect { stack has sternRecluse }
        bob.passes()
        carol.casts(tideRebuke)
        expect { stack has tideRebuke }
    }

    // ---- Copies

    /** As a copy of a legendary 2/2 it is a 3/3 that isn't legendary, and both stay. */
    // CR 707.9b, 614.1c; CR 704.5j: no legend rule without two legendary permanents
    @Test
    fun copyOfACreatureGetsACounterAndIsntLegendary() = scenario {
        alice.battlefield(vorel)
        alice.hand(glint)
        alice.castsWithoutCosts(glint)
        resolve()
        alice.chooses(vorel)
        settle()
        expect {
            card(glint).power shouldBe 3
            card(glint).toughness shouldBe 3
            assertTrue(Supertype.Legendary !in card(glint).view.characteristics!!.supertypes)
            alice.battlefield has vorel
        }
    }

    /** As a copy of a planeswalker with loyalty 3 it enters with four loyalty counters and no +1/+1 counter. */
    // CR 306.5b, 707.9b
    @Test
    fun copyOfAPlaneswalkerGetsALoyaltyCounter() = scenario {
        alice.battlefield(talia, counters = listOf(CounterKind.Loyalty to 3))
        alice.hand(glint)
        alice.castsWithoutCosts(glint)
        resolve()
        alice.chooses(talia)
        settle()
        expect {
            card(glint).counters(CounterKind.Loyalty) shouldBe 4
            card(glint).counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
        }
    }

    /** Declining the copy, it enters as a 0/0 with no counter and dies. */
    // CR 707.9b: the counters come only with the copy; CR 704.5f
    @Test
    fun withoutACopyItDies() = scenario {
        alice.battlefield(vorel)
        alice.hand(glint)
        alice.castsWithoutCosts(glint)
        resolve()
        alice.chooses()
        settle()
        expect { alice.graveyard has glint }
    }

    // ---- Attach, damage

    /** The Aura on Bob's Hound moves to Bob's Recluse, chosen as the ability resolves. */
    // CR 701.3a, b
    @Test
    fun auraMovesToAnotherPermanentWithTheSameController() = scenario {
        alice.battlefield(helixTender, "Island", "Island")
        bob.battlefield(gravelHound, sternRecluse, brambleMantle)
        attach(brambleMantle, gravelHound)
        alice.activates(helixTender, ability = 1, target = brambleMantle)
        allPass()
        alice.chooses(sternRecluse)
        settle()
        expect {
            sternRecluse.power shouldBe 4
            gravelHound.power shouldBe 2
        }
    }

    /** The destroyed artifact's controller takes 2. */
    // CR 608.2h: its controller as it last existed
    @Test
    fun damageToTheDestroyedPermanentsController() = scenario {
        alice.battlefield(ashthorn, "Forest")
        bob.battlefield(copperBauble)
        alice.activates(ashthorn, target = copperBauble)
        allPass()
        expect {
            bob.graveyard has copperBauble
            bob.life shouldBe 18
        }
    }

    // ---- Sacrifice or discard

    /** Alice and Bob sacrifice their creatures; Carol, with none, discards her card. */
    // CR 701.21a, 101.4
    @Test
    fun eachPlayerWhoCantSacrificeDiscards() = scenario(players = 3) {
        alice.hand(blightmonger, quickNote)
        bob.battlefield(gravelHound)
        bob.hand(ledgerGlimpse)
        carol.hand(mossNote)
        alice.castsWithoutCosts(blightmonger)
        resolveAll()
        expect {
            alice.graveyard has blightmonger
            bob.graveyard has gravelHound
            carol.graveyard has mossNote
            alice.hand has quickNote
            bob.hand has ledgerGlimpse
        }
    }

    // ---- Colour pairs

    /** Of ten revealed cards, the white-blue and black-red ones go to the hand; the green one and the lands to the bottom. */
    // CR 105.1, 105.2: exactly those two colours; CR 401.4
    @Test
    fun chooseOneCardForEachColorPair() = scenario {
        alice.hand(wyrmlord)
        alice.library(concordEdict, cinderPact, mossNote, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Island")
        alice.castsWithoutCosts(wyrmlord)
        resolveAll()
        expect {
            alice.hand has concordEdict
            alice.hand has cinderPact
            alice.hand lacks mossNote
            alice.library.size shouldBe 9
        }
    }

    // ---- Graveyard return

    /** After three instants and sorceries the Phoenix returns at the beginning of combat. */
    // CR 603.4, 603.6
    @Test
    fun returnsAfterThreeInstantsAndSorceries() = scenario {
        alice.graveyard(phoenix)
        alice.hand(quickNote, ledgerGlimpse, mossNote)
        alice.library("Plains", "Plains", "Plains")
        alice.castsWithoutCosts(quickNote)
        allPass()
        alice.castsWithoutCosts(ledgerGlimpse)
        allPass()
        alice.castsWithoutCosts(mossNote)
        allPass()
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { alice.battlefield has phoenix }
    }

    /** After two it stays in the graveyard. */
    // CR 603.4
    @Test
    fun staysAfterTwo() = scenario {
        alice.graveyard(phoenix)
        alice.hand(quickNote, ledgerGlimpse)
        alice.library("Plains", "Plains")
        alice.castsWithoutCosts(quickNote)
        allPass()
        alice.castsWithoutCosts(ledgerGlimpse)
        allPass()
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { alice.graveyard has phoenix }
    }
}
