package arcanum.engine.sets.sos

import arcanum.api.CounterKind
import arcanum.api.ObjectMark
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Secrets of Strixhaven cards whose rules are new to the engine: skipped turns (CR 614.10), 2ˣ cards, "can't be copied" (CR 707.10),
 * casualty and miracle given by Elder Dragons (CR 702.153a, 702.94a), exiling until a total mana value, "until the end of their next turn",
 * an additional cost of X life, entering with three minus X stun counters, a mode chosen twice (CR 700.2d), and becoming prepared or
 * unprepared by other cards (CR 722.3a, b).
 */
class SosCardsScenariosTest {
    private val varek = Cards.invent("Varek Thorn, Visiting Lecturer", "{1}{B}{B}", "Legendary Planeswalker — Ral", """
        [+1]: Surveil 2.
        [−1]: Any number of target players each discard a card.
        [−2]: Return target creature card with mana value 3 or less from your graveyard to the battlefield.
        [−7]: Flip five coins. Target opponent skips their next X turns, where X is the number of coins that came up heads.
    """, loyalty = "7")
    private val wonder = Cards.invent("Calculated Wonder", "{X}{X}{U}{U}", "Sorcery",
        "Target player draws 2ˣ cards. (2⁰ = 1, 2¹ = 2, 2² = 4, 2³ = 8, 2⁴ = 16, 2⁵ = 32, and so on.)")
    private val embers = Cards.invent("Staged Embers", "{R}{R}", "Instant", """
        This spell can't be copied.
        Choose one or both —
        • Copy target instant or sorcery spell you control. You may choose new targets for the copy.
        • Copy target creature spell you control. The copy gains haste and "At the beginning of the end step, sacrifice this token."
    """)
    private val inkwing = Cards.invent("Inkwing, the Polemicist", "{2}{W}{B}", "Legendary Creature — Elder Dragon", """
        Flying, vigilance
        Each instant and sorcery spell you cast has casualty 1. (As you cast that spell, you may sacrifice a creature with power 1 or greater. When you do, copy the spell and you may choose new targets for the copy.)
    """, "4/4")
    private val embervault = Cards.invent("Embervault, the Chronicler", "{3}{R}{W}", "Legendary Creature — Elder Dragon", """
        Flying, haste
        Each instant and sorcery card in your hand has miracle {2}. (You may cast a card for its miracle cost when you draw it if it's the first card you drew this turn.)
        At the beginning of each opponent's upkeep, you may discard a card. If you do, draw a card.
    """, "5/5")
    private val finale = Cards.invent("Extemporal Finale", "{5}{R}{R}", "Sorcery — Lesson", """
        Exile cards from the top of your library until you exile cards with total mana value 4 or greater. You may cast any number of spells from among them without paying their mana costs.
        Paradigm (Then exile this spell. After you first resolve a spell with this name, you may cast a copy of it from exile without paying its mana cost at the beginning of each of your first main phases.)
    """)
    private val hostility = Cards.invent("Stay the Hostility", "{1}{R}{W}", "Instant",
        "Exile target nonland permanent and the top card of your library. For each of those cards, its owner may play it until the end of their next turn.")
    private val contest = Cards.invent("Cutthroat Contest", "{2}{B}{G}", "Sorcery", """
        As an additional cost to cast this spell, pay X life.
        Destroy all artifacts and creatures with mana value X or less.
    """)
    private val plodder = Cards.invent("Dozing Plodder", "{X}{G}", "Creature — Plant Beast",
        "This creature enters with a number of stun counters on it equal to three minus X. If X is 2 or less, it enters tapped. (If a permanent with a stun counter would become untapped, remove one from it instead.)", "6/6")
    private val judgment = Cards.invent("Hour of Judgment Rendered", "{3}{W}{W}{B}{B}", "Sorcery", """
        Choose up to four. You may choose the same mode more than once.
        • Destroy target nonland permanent.
        • Return target nonland permanent card from your graveyard to the battlefield.
    """)
    private val devotee = Cards.inventFaces("Prepare",
        face("Ghostcall Devotee", "{2}{W}", "Creature — Cat Cleric", "Whenever one or more tokens you control enter, this creature becomes prepared.", "3/3", null, null),
        face("Scroll Surge", "{1}{W}", "Sorcery", "One or two target creatures each get +2/+2 until end of turn.", null, null, null))
    private val bookwarden = Cards.invent("Athenaeum Bookwarden", "{4}", "Artifact Creature — Construct", """
        When this creature enters, choose up to one —
        • Target creature becomes prepared. (Only creatures with prepare spells can become prepared.)
        • Target creature becomes unprepared.
    """, "3/4")
    private val shock = Cards.invent("Sparkling Jolt", "{R}", "Instant", "Sparkling Jolt deals 2 damage to any target.")
    private val pump = Cards.invent("Quick Lift", "{W}", "Instant", "Target creature gets +1/+1 until end of turn.")
    private val tokens = Cards.invent("Spirit Call", "{W}", "Sorcery", "Create a 1/1 white Spirit creature token with flying.")
    private val draw = Cards.invent("Light Study", "{U}", "Sorcery", "Draw a card.")
    private val bear = Cards.invent("Plaza Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Plaza Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val giant = Cards.invent("Lecture Hall Giant", "{4}{G}", "Creature — Giant", "", "5/5")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    private fun Scenario.prepared(name: String): Boolean = state.obj(card(name, zone = Zone.Battlefield))!!.marks.contains(ObjectMark.Prepared)

    /** Three heads: the opponent's next three turns don't happen, so the planeswalker's controller takes the following turns. */
    // CR 614.10, 705.1
    @Test
    fun skippedTurnsPassToTheNextPlayer() = scenario {
        alice.battlefield(varek, counters = listOf(CounterKind.Loyalty to 7))
        alice.library(bear, bear, bear, bear, bear, bear)
        bob.library(bear, bear, bear, bear, bear, bear)
        random(Answer.CoinFlip(true), Answer.CoinFlip(true), Answer.CoinFlip(false), Answer.CoinFlip(true), Answer.CoinFlip(false))
        alice.activates("Varek Thorn, Visiting Lecturer", ability = 3, target = "bob")
        resolveAll()
        assertEquals(listOf(bob.id, bob.id, bob.id), state.turn.skippedTurns)
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertEquals(alice.id, state.turn.active)
        advanceTo(TurnStep.Upkeep, turn = 4)
        assertEquals(alice.id, state.turn.active)
        advanceTo(TurnStep.Upkeep, turn = 5)
        assertEquals(bob.id, state.turn.active)
        assertTrue(state.turn.skippedTurns.isEmpty())
    }

    /** 2ˣ: X 0 draws one card, X 2 draws four. */
    // CR 107.3a
    @Test
    fun drawsTwoToTheX() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(wonder)
        alice.library(bear, bear, bear, bear, bear)
        alice.casts(wonder, target = "alice", x = 2)
        resolveAll()
        expect { alice.hand.names.count { it == "Plaza Bear" } shouldBe 4 }
    }

    /** A spell that can't be copied isn't copied by another copy effect; the other mode copies the creature spell as a token with haste. */
    // CR 707.10, 707.10f
    @Test
    fun cantBeCopiedAndACreatureSpellCopy() = scenario {
        alice.battlefield("Mountain", "Mountain", "Forest", "Forest")
        alice.hand(embers, bear)
        alice.casts(bear, paying = listOf("Forest", "Forest"))
        alice.casts(embers, modes = listOf(1), targets = listOf("Plaza Bear"))
        resolveAll()
        expect { alice.battlefield.names.count { it == "Plaza Bear" } shouldBe 2 }
    }

    /** Casualty given by the Elder Dragon: sacrificing a creature as the spell is cast copies it. */
    // CR 702.153a
    @Test
    fun grantedCasualtyCopiesTheSpell() = scenario {
        alice.battlefield(inkwing, bear, "Mountain")
        alice.hand(shock)
        alice.casts(shock, target = "bob", kicked = 1, choosing = listOf("Plaza Bear"))
        expect { alice.graveyard has "Plaza Bear" }
        assertEquals(2, state.stack.size, "the spell and its casualty trigger")
        allPass()
        if (pending is Decision.May) answer(Answer.YesNo(false))
        resolveAll()
        assertEquals(16, bob.life)
    }

    /** Miracle given to a card in hand: the first card drawn this turn may be cast for {2}. */
    // CR 702.94a
    @Test
    fun grantedMiracleOnTheFirstDraw() = scenario {
        alice.battlefield(embervault, "Mountain", "Mountain")
        alice.library(draw, bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.Draw, turn = 3)
        assertEquals(1, state.stack.size, "the miracle trigger")
        allPass()
        answer(Answer.YesNo(true))
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
        resolveAll()
        expect {
            alice.graveyard has "Light Study"
            alice.hand has "Plaza Bear"
        }
        assertEquals(2, state.battlefield.count { state.obj(it)!!.tapped })
    }

    /** Exiling until the exiled cards' total mana value is 4 or more: lands count 0. */
    // CR 202.3
    @Test
    fun exileUntilATotalManaValue() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(finale)
        alice.library("Mountain", bear, "Forest", bear, ogre, giant)
        alice.casts(finale)
        allPass()
        repeat(6) { if (pending is Decision.May) answer(Answer.YesNo(false)) }
        resolveAll()
        expect { alice.exile.names.count { it == "Plaza Bear" } shouldBe 2 }
        assertEquals(listOf("Plaza Ogre", "Lecture Hall Giant"), state.player(alice.id).library.map { state.obj(it)!!.definition!!.faces[0].name })
    }

    /** Each owner may play their own card until the end of their own next turn. */
    // CR 611.2c
    @Test
    fun eachOwnerPlaysUntilTheirNextTurnEnds() = scenario {
        alice.battlefield("Mountain", "Plains", "Plains")
        alice.hand(hostility)
        alice.library(bear, bear, bear)
        bob.battlefield(ogre)
        bob.library(bear, bear, bear)
        alice.casts(hostility, target = "Plaza Ogre")
        resolveAll()
        expect { bob.exile has "Plaza Ogre" }
        assertEquals(2, state.effects.size)
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertEquals(1, state.effects.size, "bob's permission ended with his turn")
        advanceTo(TurnStep.Upkeep, turn = 4)
        assertEquals(0, state.effects.size)
    }

    /** X life as an additional cost: everything with mana value X or less is destroyed. */
    // CR 601.2b, 107.3
    @Test
    fun payXLifeDestroysUpToX() = scenario {
        alice.battlefield("Swamp", "Swamp", "Forest", "Forest")
        alice.hand(contest)
        bob.battlefield(bear, ogre, giant)
        alice.casts(contest, x = 3)
        resolveAll()
        assertEquals(17, alice.life)
        expect {
            bob.graveyard has "Plaza Bear"
            bob.graveyard has "Plaza Ogre"
            bob.battlefield has "Lecture Hall Giant"
        }
    }

    /** X 1: two stun counters and tapped; X 3: none, untapped. */
    // CR 107.3m, 122.1d
    @Test
    fun stunCountersThreeMinusX() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(plodder)
        alice.casts(plodder, x = 1, paying = listOf("Forest", "Forest"))
        resolveAll()
        expect {
            "Dozing Plodder".counters(CounterKind.Stun) shouldBe 2
            "Dozing Plodder".tapped shouldBe true
        }
    }

    /** The same mode twice: two permanents destroyed. */
    // CR 700.2d
    @Test
    fun aModeChosenTwice() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(judgment)
        bob.battlefield(bear, ogre)
        alice.casts(judgment, modes = listOf(0, 0), targets = listOf("Plaza Bear", "Plaza Ogre"))
        resolveAll()
        expect {
            bob.graveyard has "Plaza Bear"
            bob.graveyard has "Plaza Ogre"
        }
    }

    /** Tokens entering prepare the creature; another card can unprepare it. */
    // CR 722.3a, 722.3b
    @Test
    fun preparedByTokensAndUnpreparedByAnotherCard() = scenario {
        alice.battlefield(devotee, "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(tokens, bookwarden)
        alice.casts(tokens)
        resolveAll()
        assertTrue(prepared("Ghostcall Devotee"))
        expect { alice.exile has "Scroll Surge" }
        alice.casts(bookwarden)
        resolveAll()
        if (pending is Decision.Modes) answer(Answer.Modes(listOf(1)))
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target("Ghostcall Devotee"))))
        resolveAll()
        assertTrue(!prepared("Ghostcall Devotee"))
        expect { alice.exile lacks "Scroll Surge" }
    }
}
