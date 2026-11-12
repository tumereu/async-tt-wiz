package arcanum.engine.sets.lci

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.backFaceUp
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** LCI chunk 01: discover (CR 701.57) played: the discovered card a later sentence names, X from a spell that left the Stack, another
 * player discovering, "you descended this turn", a cast trigger needing this land's mana, and the cards around them. Invented names
 * carry the oracle text. */
class LciChunk01DiscoverScenariosTest {
    private fun reminder(n: Int) = "(Exile cards from the top of your library until you exile a nonland card with mana value $n or less. Cast it " +
        "without paying its mana cost or put it into your hand. Put the rest on the bottom in a random order.)"

    private val boreEngine = Cards.inventFaces("Transform",
        face("Gearwright's Bore-Engine", "{2}{R}", "Legendary Artifact", """
            When Gearwright's Bore-Engine enters, discard any number of cards, then draw that many cards plus one.
            At the beginning of your end step, if you descended this turn, put a bore counter on Gearwright's Bore-Engine. Then if there are three or more bore counters on it, remove those counters and transform it. (You descended if a permanent card was put into your graveyard from anywhere.)
        """, null, null, null),
        face("Cinderdeep, the Molten Rift", null, "Legendary Land — Cave", """
            (Transforms from Gearwright's Bore-Engine.)
            {T}: Add {R}.
            Whenever you cast a permanent spell using mana produced by Cinderdeep, discover X, where X is that spell's mana value.
        """, null, null, null))
    private val strike = Cards.invent("Strike the Deep Seam", "{4}{R}{R}{R}", "Sorcery",
        "Discover 10. If the discovered card's mana value is less than 10, create a number of tapped Treasure tokens equal to the difference. " +
            "(To discover 10, exile cards from the top of your library until you exile a nonland card with mana value 10 or less. Cast it without paying its mana cost or put it into your hand. Put the rest on the bottom in a random order.)")
    private val fling = Cards.invent("Fling into Antiquity", "{3}{U}{U}", "Instant",
        "Counter target artifact or creature spell. Discover X, where X is that spell's mana value. (Exile cards from the top of your library until you exile a nonland card with that mana value or less. Cast it without paying its mana cost or put it into your hand. Put the rest on the bottom in a random order.)")
    private val verdict = Cards.invent("Matriarch's Verdict", "{1}{R}", "Instant",
        "The owner of target artifact or creature with mana value 1 or greater shuffles it into their library. Then that player discovers X, where X is its mana value. (They exile cards from the top of their library until they exile a nonland card with that mana value or less. They cast it without paying its mana cost or put it into their hand. They put the rest on the bottom in a random order.)")
    private val thask = Cards.invent("Velorian Thask", "{3}{R}{W}", "Legendary Planeswalker — Quintorius", """
        Whenever you cast a spell from exile, Velorian Thask deals 2 damage to each opponent and you gain 2 life.
        [+1]: Create a 3/2 red and white Spirit creature token.
        [−3]: Discover 4.
        [−6]: Exile any number of target cards from your graveyard. Add {R} for each card exiled this way. You may play those cards this turn.
    """, null, "4")
    private val itzaro = Cards.invent("Itzaro Dawnblade", "{2}{R}{W}", "Legendary Creature — Human Soldier",
        "Whenever Itzaro Dawnblade attacks, you may tap two untapped artifacts and/or creatures you control. If you do, discover 3. ${reminder(3)}", "4/4")
    private val tonalli = Cards.invent("Tonalli, the Core Flame", "{6}", "Legendary Artifact", """
        Spells you control can't be countered.
        At the beginning of your end step, discover 5. ${reminder(5)}
    """)
    private val caldera = Cards.invent("Veiled Caldera", null, "Land — Cave", """
        This land enters tapped.
        {T}: Add {R}.
        {4}{R}, {T}, Sacrifice this land: Discover 4. Activate only as a sorcery. ${reminder(4)}
    """)
    private val megasaur = Cards.invent("Bellowing Megasaur", "{4}{R}{R}", "Creature — Dinosaur", """
        Trample
        When this creature enters, discover 5.
        {2}{R}, Discard this card: It deals 3 damage to target creature or planeswalker.
    """, "7/6")
    private val favor = Cards.invent("Ochre Titan's Favor", "{2}{R}", "Enchantment — Aura", """
        Enchant creature you control
        When this Aura enters, discover 3. ${reminder(3)}
        Enchanted creature gets +1/+1 and has trample.
    """)
    private val sigil = Cards.invent("Animating Sigil", "{2}{U}", "Enchantment — Aura", """
        Enchant artifact
        Enchanted artifact is a Golem creature with base power and toughness 5/4 in addition to its other types.
        When this Aura is put into a graveyard from the battlefield, discover 3. ${reminder(3)}
    """)

    private val spark = Cards.invent("Seam Spark", "{R}", "Instant", "Seam Spark deals 3 damage to target creature.")
    private val snuff = Cards.invent("Snuff the Flame", "{U}{U}", "Instant", "Counter target spell.")
    private val mole = Cards.invent("Tunnel Mole", "{1}{R}", "Creature — Mole", "", "2/2")
    private val lizard = Cards.invent("Ruin Lizard", "{1}{R}", "Creature — Lizard", "", "2/1")
    private val moth = Cards.invent("Glyph Moth", "{2}{U}", "Creature — Insect", "", "1/3")
    private val idol = Cards.invent("Lodestone Idol", "{1}", "Artifact", "")
    private val bore = CounterKind.Named("bore")
    private val summons = Cards.invent("Summons to Inquiry", "{W}", "Sorcery",
        "Target player investigates. (To investigate, create a Clue token. It's an artifact with \"{2}, Sacrifice this token: Draw a card.\")")

    // ---- Gearwright's Bore-Engine // Cinderdeep

    // CR 700.11 (a permanent card put into your graveyard), 603.4 (intervening if), 701.27a: the third counter transforms it
    @Test
    fun boreEngineTransformsAtItsThirdCounterAfterYouDescended() = scenario {
        alice.battlefield("Mountain", mole)
        alice.battlefield(boreEngine, counters = listOf(bore to 2))
        alice.hand(spark)
        alice.library("Mountain", "Mountain")
        bob.library("Island", "Island")
        alice.casts(spark, target = mole)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.graveyard has mole
            boreEngine.counters(bore) shouldBe 0
            card(boreEngine).view.characteristics!!.name shouldBe "Cinderdeep, the Molten Rift"
        }
    }

    // CR 700.11: only your own graveyard counts, and only permanent cards (the instant that went there doesn't)
    @Test
    fun boreEngineGetsNoCounterWhenOnlyAnOpponentsCreatureDied() = scenario(players = 3) {
        alice.battlefield("Mountain")
        alice.battlefield(boreEngine, counters = listOf(bore to 2))
        bob.battlefield(mole)
        alice.hand(spark)
        alice.casts(spark, target = mole)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bob.graveyard has mole
            alice.graveyard has spark
            boreEngine.counters(bore) shouldBe 2
        }
    }

    // CR 106.3, 603.2: a permanent spell paid for with Cinderdeep's mana discovers its mana value (CR 701.57a); without it, nothing
    @Test
    fun cinderdeepDiscoversWhenItsManaPaysForAPermanentSpell() = scenario {
        alice.battlefield(boreEngine, "Mountain", "Mountain", "Mountain")
        backFaceUp(boreEngine)
        alice.hand(mole, idol)
        alice.library("Mountain", moth, lizard, "Mountain")
        alice.casts(mole, paying = listOf(boreEngine, "Mountain"))
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.battlefield has mole
            // discover 2: the 3-mana Moth goes to the bottom, the Lizard is cast
            alice.battlefield has lizard
        }
        // a permanent spell paid for with a Mountain alone: no trigger
        alice.casts(idol, paying = listOf("Mountain#1"))
        resolveAll()
        expect {
            alice.battlefield has idol
            stack.isEmpty()
            alice.library.size shouldBe 3
        }
    }

    // ---- Strike the Deep Seam

    // CR 701.57c: the discovered card (put into the hand here) has mana value 2: eight tapped Treasures
    @Test
    fun strikeTheDeepSeamCreatesTheDifferenceInTappedTreasures() = scenario {
        alice.battlefield(*Array(7) { "Mountain" })
        alice.hand(strike)
        alice.library("Mountain", lizard, "Island")
        alice.casts(strike)
        resolveAll()
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect { alice.hand has lizard }
        val treasures = state.battlefield.filter { view()[it]?.characteristics?.subtypes?.any { s -> s.name == "Treasure" } == true }
        assertEquals(8, treasures.size)
        assertTrue(treasures.all { state.objOrThrow(it).tapped })
    }

    // CR 701.57c: with no nonland card exiled there is no discovered card, so no Treasures
    @Test
    fun strikeTheDeepSeamWithNothingDiscoveredCreatesNoTreasures() = scenario {
        alice.battlefield(*Array(7) { "Mountain" })
        alice.hand(strike)
        alice.library("Island", "Island")
        alice.casts(strike)
        resolveAll()
        if (pending is Decision.May) alice.answers(Answer.YesNo(false))
        resolveAll()
        expect { alice.battlefield.size shouldBe 7 }
    }

    // ---- Fling into Antiquity

    // CR 701.6a, 608.2h: X is the countered spell's mana value as it last existed: discover 2 passes over a 3-mana card
    @Test
    fun flingIntoAntiquityDiscoversTheCounteredSpellsManaValue() = scenario {
        turn(number = 2, active = bob)
        bob.battlefield("Mountain", "Mountain")
        bob.hand(mole)
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(fling)
        alice.library(moth, lizard, "Island")
        bob.casts(mole)
        bob.passes()
        alice.casts(fling, target = mole)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.graveyard has mole
            alice.battlefield has lizard
            alice.library has moth
        }
    }

    // ---- Matriarch's Verdict

    // CR 701.57a: the owner shuffles it in and discovers from their own library; X is its mana value as it last existed (CR 608.2h)
    @Test
    fun matriarchsVerdictsOwnerDiscoversFromTheirOwnLibrary() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(verdict)
        alice.library("Mountain", lizard)
        bob.battlefield(moth)
        bob.library("Island", "Island", "Island")
        carol.library("Forest", lizard)
        alice.casts(verdict, target = moth)
        resolveAll()
        assertEquals(bob.id, decision<Decision.May>().player)
        bob.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            // the Moth is the only nonland card in Bob's library: he discovers it again and casts it
            bob.battlefield has moth
            card(moth).controller shouldBe bob.id
            alice.library.size shouldBe 2
            carol.library.size shouldBe 2
        }
    }

    // CR 701.16a: the player who investigates creates the Clue (a keyword action's "you" is the player doing it)
    @Test
    fun anotherPlayerInvestigatingGetsTheClue() = scenario(players = 3) {
        alice.battlefield("Plains")
        alice.hand(summons)
        alice.casts(summons, target = "bob")
        resolveAll()
        expect {
            bob.battlefield has "Clue Token"
            alice.battlefield lacks "Clue Token"
        }
    }

    // ---- Velorian Thask

    // CR 606.3: the −6 adds {R} per card exiled and lets you play them (a land too, CR 305.1); casting from exile triggers the first
    // ability: 2 damage to each opponent (CR 120.3) and 2 life
    @Test
    fun velorianThaskPlaysTheExiledCards() = scenario(players = 3) {
        alice.battlefield(thask, counters = listOf(CounterKind.Loyalty to 7))
        alice.graveyard("Mountain", lizard)
        alice.activates(thask, ability = 2, targets = listOf("Mountain", lizard))
        resolveAll()
        expect { alice.pool.size shouldBe 2 }
        alice.plays("Mountain")
        alice.casts(lizard)
        resolveAll()
        expect {
            alice.battlefield has "Mountain"
            alice.battlefield has lizard
            alice.life shouldBe 22
            bob.life shouldBe 18
            carol.life shouldBe 18
        }
    }

    // ---- Itzaro Dawnblade

    // CR 118.12, 508.1: "you may tap two untapped artifacts and/or creatures you control": only your own untapped ones are offered
    @Test
    fun itzaroTapsTwoOfYourArtifactsOrCreaturesToDiscover() = scenario(players = 3) {
        alice.battlefield(itzaro, mole, idol, lizard)
        bob.battlefield(moth)
        alice.library("Mountain", spark, "Mountain")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(itzaro, defender = "bob")
        resolveAll()
        alice.answers(Answer.YesNo(true))
        val choice = decision<Decision.ChooseObjects>()
        assertEquals(setOf(card(mole), card(idol), card(lizard)), choice.options.toSet())
        alice.chooses(mole, idol)
        resolveAll()
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            mole.tapped shouldBe true
            idol.tapped shouldBe true
            lizard.tapped shouldBe false
            alice.hand has spark
        }
    }

    // ---- Tonalli, the Core Flame

    // CR 101.2, 701.6a: an opponent's counterspell can't counter your spell
    @Test
    fun tonalliKeepsYourSpellsFromBeingCountered() = scenario {
        alice.battlefield(tonalli, "Mountain", "Mountain")
        alice.hand(mole)
        bob.battlefield("Island", "Island")
        bob.hand(snuff)
        alice.casts(mole)
        alice.passes()
        bob.casts(snuff, target = mole)
        resolveAll()
        expect {
            alice.battlefield has mole
            bob.graveyard has snuff
        }
    }

    // ---- Veiled Caldera

    // CR 614.1c: it enters tapped; CR 602.5d: the discover ability only as a sorcery
    @Test
    fun veiledCalderaEntersTappedAndDiscoversOnlyAsASorcery() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(caldera)
        alice.library(lizard, "Mountain")
        alice.plays(caldera)
        expect { caldera.tapped shouldBe true }
    }

    // CR 602.5d: not on an opponent's turn
    @Test
    fun veiledCalderaCantDiscoverOnAnOpponentsTurn() = scenario {
        turn(number = 2, active = bob, priority = alice)
        alice.battlefield(caldera, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.library(lizard, "Mountain")
        assertFailsWith<IllegalArgumentException> { alice.activates(caldera, ability = 1) }
    }

    // CR 602.5d, 701.57a: in your main phase it is sacrificed to discover 4
    @Test
    fun veiledCalderaSacrificesToDiscoverInYourMainPhase() = scenario {
        alice.battlefield(caldera, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.library("Mountain", lizard, "Mountain")
        alice.activates(caldera, ability = 1)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.graveyard has caldera
            alice.battlefield has lizard
        }
    }

    // ---- Bellowing Megasaur

    // CR 113.6m, 602.1: discarding it from the hand as a cost; the card deals the damage (CR 113.7a)
    @Test
    fun megasaurDiscardedFromTheHandDealsThreeDamage() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(megasaur)
        bob.battlefield(moth)
        alice.activates(megasaur, ability = 0, target = moth)
        resolveAll()
        expect {
            alice.graveyard has megasaur
            bob.graveyard has moth
        }
    }

    // ---- Ochre Titan's Favor

    // CR 303.4a: it can enchant only a creature you control; CR 701.57a: discover 3 when it enters; +1/+1 and trample
    @Test
    fun ochreTitansFavorEnchantsYourCreatureAndDiscovers() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", mole)
        bob.battlefield(moth)
        alice.hand(favor)
        alice.library("Mountain", lizard)
        alice.casts(favor, target = mole)
        resolveAll()
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            mole.power shouldBe 3
            mole.toughness shouldBe 3
            mole hasKeyword Keyword.Trample.kind
            alice.hand has lizard
        }
    }

    // ---- Animating Sigil

    // CR 613.1d, 613.4b: a 5/4 Golem artifact creature; when the Aura goes to the graveyard, discover 3
    @Test
    fun animatingSigilMakesAGolemAndDiscoversWhenItLeaves() = scenario {
        alice.battlefield("Island", "Island", "Island", "Mountain", "Mountain", idol)
        alice.hand(sigil, spark, spark)
        alice.library("Island", lizard)
        alice.casts(sigil, target = idol)
        resolveAll()
        expect {
            idol.power shouldBe 5
            idol.toughness shouldBe 4
            assertTrue(card(idol).view.characteristics!!.hasSubtype(arcanum.api.Subtype("Golem")))
        }
        alice.casts(spark, target = idol)
        resolveAll()
        alice.casts(spark, target = idol)
        resolveAll()
        expect { alice.graveyard has sigil }
        alice.answers(Answer.YesNo(false))
        resolveAll()
        expect { alice.hand has lizard }
    }
}
