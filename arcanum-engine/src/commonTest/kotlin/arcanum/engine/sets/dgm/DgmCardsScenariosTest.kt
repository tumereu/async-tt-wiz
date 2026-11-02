package arcanum.engine.sets.dgm

import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.ObjectFilter
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dragon's Maze cards whose rules needed new engine support: attacking alone and an unblocked attack setting a life
 * total, a draw replacement except the draw step's first draw, targets chosen at random, text changes, counters removed
 * then counted, a triggered mana ability, a trigger only during your turn, a chosen card name, and ten Gates. */
class DgmCardsScenariosTest {
    private val mistress = Cards.invent("Mistress of Cruelties", "{3}{B}{R}", "Creature — Demon", """
        First strike, deathtouch
        This creature can only attack alone.
        Whenever this creature attacks a player and isn't blocked, that player's life total becomes 1. This creature assigns no combat damage this combat.
    """, "1/4")
    private val pilferer = Cards.invent("Notion Pilferer", "{2}{U}{B}", "Creature — Human Rogue", """
        Flash
        If an opponent would draw a card except the first one they draw in each of their draw steps, instead that player skips that draw and you draw a card.
    """, "3/1")
    private val study = Cards.invent("Deep Study", "{2}{U}", "Sorcery", "Draw two cards.")
    private val pilot = Cards.invent("Goblin Trial Pilot", "{1}{U}{R}", "Creature — Goblin Pilot Wizard", """
        Flying
        {T}: This creature deals 2 damage to any target chosen at random.
    """, "0/2")
    private val brute = Cards.invent("Scab-Clan Brute", "{4}{R}{G}", "Creature — Giant Warrior",
        "When this creature enters, it fights target creature an opponent controls chosen at random.", "4/5")
    private val surgery = Cards.invent("Trait Surgery", "{U}", "Sorcery", """
        Change the text of target permanent by replacing all instances of one color word with another or one basic land type with another until end of turn.
        Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
    """)
    private val knight = Cards.invent("Red Ward Knight", "{1}{W}", "Creature — Human Knight", "Protection from red", "2/2")
    private val fuse = "Fuse (You may cast one or both halves of this card from your hand.)"
    private val grantCollect = Cards.inventFaces("Split",
        face("Grant", "{2}{G}", "Sorcery", "Put three +1/+1 counters on target creature.\n$fuse", null, null, null),
        face("Collect", "{2}{U}", "Sorcery", "Remove all +1/+1 counters from target creature you control. Draw that many cards.\n$fuse", null, null, null),
    )
    private val elder = Cards.invent("Zhur-Taa Elder", "{3}{R}{G}", "Creature — Beast",
        "Whenever a player taps a land for mana, that player adds one mana of any type that land produced.", "7/5")
    private val voice = Cards.invent("Voice of Reawakening", "{G}{W}", "Creature — Elemental",
        "Whenever an opponent casts a spell during your turn and when this creature dies, create a green and white Elemental creature token with \"This token's power and toughness are each equal to the number of creatures you control.\"", "2/2")
    private val council = Cards.invent("Council of the Supreme", "{2}{W}{U}", "Creature — Human Advisor", """
        As this creature enters, choose a noncreature, nonland card name.
        Your opponents can't cast spells with the chosen name.
        Spells with the chosen name you cast cost {2} less to cast.
    """, "2/4")
    private val labyrinth = Cards.invent("Labyrinth's End", null, "Land", """
        This land enters tapped.
        {T}: Add {C}.
        {3}, {T}, Return this land to its owner's hand: Search your library for a Gate card, put it onto the battlefield, then shuffle. If you control ten or more Gates with different names, you win the game.
    """)
    private val gates = listOf("Azorius", "Boros", "Dimir", "Golgari", "Gruul", "Izzet", "Orzhov", "Rakdos", "Selesnya", "Simic").map { guild ->
        Cards.invent("$guild Gatehouse", null, "Land — Gate", "This land enters tapped.\n{T}: Add {W} or {U}.")
    }

    private val storm = Cards.invent("Probability Storm", "{3}{R}{R}", "Enchantment",
        "Whenever a player casts a spell from their hand, that player exiles it, then exiles cards from the top of their library until they exile a card that shares a card type with it. That player may cast that card without paying its mana cost. Then they put all cards exiled with this enchantment on the bottom of their library in a random order.")
    private val saprolings = Cards.invent("Spore Call", "{G}", "Sorcery", "Create a 1/1 green Saproling creature token.")
    private val mime = Cards.invent("Progenitor Mime", "{4}{G}{U}", "Creature — Shapeshifter",
        "You may have this creature enter as a copy of any creature on the battlefield, except it has \"At the beginning of your upkeep, if this creature isn't a token, create a token that's a copy of this creature.\"", "0/0")

    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Wide Ox", "{2}{G}", "Creature — Ox", "", "1/4")
    private val murder = Cards.invent("Quick Execution", "{1}{B}{B}", "Instant", "Destroy target creature.")
    private val counter = CounterKind.PowerToughness(1, 1)

    private fun Scenario.resolveAll(yes: Boolean = true) {
        var guard = 40
        while (guard-- > 0) {
            when {
                pending is Decision.May -> answer(Answer.YesNo(yes))
                pending is Decision.OrderTriggers -> answer(Answer.Order(decision<Decision.OrderTriggers>().triggers.map { it.index }))
                pending != null -> return
                state.stack.isNotEmpty() -> resolve()
                else -> return
            }
        }
    }

    // ---- Mistress of Cruelties

    /** It can't attack with another creature. */
    // CR 508.1c
    @Test
    fun onlyAttacksAlone() = scenario {
        alice.battlefield(mistress, bear)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(mistress, bear) }
        assertTrue(Engine.options(state, alice.id).none { it is Input.DeclareAttackers && it.attacks.size == 2 && it.attacks.any { a -> a.attacker == card(mistress) } })
    }

    /** Unblocked, the defending player's life total becomes 1 and it deals no combat damage. */
    // CR 509.1h, 119.5, 510.1
    @Test
    fun unblockedLifeBecomesOne() = scenario {
        alice.battlefield(mistress)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(mistress)
        advanceTo(TurnStep.PostcombatMain)
        expect { bob.life shouldBe 1 }
    }

    /** Blocked, nothing happens. */
    // CR 509.1h
    @Test
    fun blockedNoTrigger() = scenario {
        alice.battlefield(mistress)
        bob.battlefield(ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(mistress)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(ox to mistress)
        advanceTo(TurnStep.PostcombatMain)
        expect {
            bob.life shouldBe 20
            bob.graveyard.names shouldBe listOf(ox)
        }
    }

    // ---- Notion Pilferer (CR 614.11, 504.1)

    /** The draw step's own draw happens; the opponent's other draws are yours instead. */
    // CR 614.11, 504.1
    @Test
    fun pilfererTakesOtherDraws() = scenario {
        turn(2, active = bob, step = TurnStep.Upkeep)
        alice.battlefield(pilferer)
        alice.library("Island", "Island", "Island")
        bob.library("Swamp", "Swamp", "Swamp", "Swamp")
        bob.hand(study)
        advanceTo(TurnStep.PrecombatMain)
        expect { bob.hand.names shouldBe listOf(study, "Swamp") }
        bob.castsWithoutCosts(study)
        resolve()
        expect {
            bob.hand.names shouldBe listOf("Swamp")
            alice.hand.names shouldBe listOf("Island", "Island")
        }
    }

    // ---- Targets chosen at random

    /** The target is chosen by the host, not the controller: no target decision, and 2 damage goes somewhere. */
    // CR 115, 608.2b
    @Test
    fun randomTargetDamage() = scenario {
        alice.battlefield(pilot)
        bob.battlefield(bear)
        alice.activates(pilot)
        assertNull(pending)
        allPass()
        val damaged = (40 - alice.life - bob.life) + alice.graveyard.names.size + bob.graveyard.names.size
        assertEquals(if (alice.life + bob.life < 40) 2 else 1, damaged)
    }

    /** It fights one of the opponent's creatures chosen at random. */
    // CR 701.14a
    @Test
    fun bruteFightsARandomCreature() = scenario {
        alice.hand(brute)
        bob.battlefield(bear, ox)
        alice.castsWithoutCosts(brute)
        resolve()
        resolveAll()
        expect {
            bob.graveyard.names.size shouldBe 1
            alice.battlefield has brute
        }
    }

    // ---- Text changes (CR 612)

    /** "Protection from red" becomes protection from blue until end of turn. */
    // CR 612.2, 613.1c
    @Test
    fun colorWordChanged() = scenario {
        alice.hand(surgery)
        bob.battlefield(knight)
        alice.castsWithoutCosts(surgery, target = knight)
        resolve()
        answer(Answer.Option("color word"))
        answer(Answer.ColorChoice(Color.Red))
        answer(Answer.ColorChoice(Color.Blue))
        val abilities = view()[card(knight)]!!.characteristics!!.abilities.map { it.ability }
        assertTrue(Keyword.Protection(ObjectFilter.OfColor(ColorRef.Literal(Color.Blue))) in abilities)
        assertTrue(Keyword.Protection(ObjectFilter.OfColor(ColorRef.Literal(Color.Red))) !in abilities)
        advanceTo(TurnStep.Upkeep, turn = 2)
        assertTrue(Keyword.Protection(ObjectFilter.OfColor(ColorRef.Literal(Color.Red))) in view()[card(knight)]!!.characteristics!!.abilities.map { it.ability })
    }

    /** A Forest becomes an Island: its type and the mana it makes. */
    // CR 612.2, 305.6
    @Test
    fun landTypeChanged() = scenario {
        alice.hand(surgery)
        bob.battlefield("Forest")
        alice.castsWithoutCosts(surgery, target = "Forest")
        resolve()
        answer(Answer.Option("basic land type"))
        answer(Answer.SubtypeChoice(Subtype("Forest")))
        answer(Answer.SubtypeChoice(Subtype("Island")))
        expect {
            assertTrue(Subtype("Island") in "Forest".subtypes)
            assertTrue(Subtype("Forest") !in "Forest".subtypes)
        }
    }

    // ---- Counters removed, then counted

    /** Fused: three counters, then all removed and as many cards drawn. */
    // CR 702.102d, 608.2h
    @Test
    fun grantThenCollect() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Island", "Island", "Island", bear)
        alice.library("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(grantCollect)
        alice.casts(grantCollect, targets = listOf(bear, bear), way = CastWay(fused = true))
        allPass()
        expect {
            bear.counters(counter) shouldBe 0
            alice.hand.names shouldBe listOf("Swamp", "Swamp", "Swamp")
        }
    }

    // ---- A triggered mana ability (CR 605.1b)

    /** Tapping a land for mana adds one more mana at once, without the Stack. */
    // CR 605.1b, 605.4a
    @Test
    fun elderAddsMana() = scenario {
        alice.battlefield(elder, "Forest")
        alice.activates("Forest")
        assertNull(pending)
        expect { stack.isEmpty() }
        assertEquals(listOf(Color.Green, Color.Green), state.player(alice.id).manaPool.map { it.color })
    }

    // ---- Voice of Reawakening

    /** An opponent's spell during your turn makes a token; during their turn it doesn't. The token counts creatures. */
    // CR 603.2, 604.3
    @Test
    fun voiceOnlyDuringYourTurn() = scenario {
        alice.battlefield(voice, bear)
        bob.hand(study, study)
        bob.library("Swamp", "Swamp", "Swamp", "Swamp")
        bob.castsWithoutCosts(study)
        resolveAll()
        expect { "Elemental Token".power shouldBe 3 }
    }

    // ---- A chosen card name (CR 201.4)

    /** The opponent can't cast it; yours costs {2} less (of its generic mana only). */
    // CR 201.4, 601.2f
    @Test
    fun councilNamesACard() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(council, murder)
        bob.battlefield("Swamp", "Swamp", "Swamp", ox)
        bob.hand(murder)
        alice.castsWithoutCosts(council)
        resolve()
        answer(Answer.CardName(murder))
        assertTrue(Engine.options(state, bob.id).none { it is Input.Cast })
        alice.casts(murder, target = ox, paying = listOf("Swamp", "Swamp#1"))
        allPass()
        expect { bob.graveyard.names shouldBe listOf(ox) }
    }

    // ---- Probability Storm

    /** The spell cast from the hand is exiled; the first card sharing a card type with it may be cast without paying
     * its mana cost; the exiled cards go to the bottom of the library. */
    // CR 118.9, 701.20
    @Test
    fun stormReplacesTheSpell() = scenario {
        alice.battlefield(storm)
        alice.hand(study)
        alice.library("Island", saprolings, "Swamp")
        alice.castsWithoutCosts(study)
        resolve()
        resolveAll()
        expect {
            alice.battlefield has "Saproling Token"
            alice.hand.isEmpty()
            alice.exile.isEmpty()
            alice.library.names.first() shouldBe "Swamp"
            alice.library.names.toSet() shouldBe setOf("Swamp", "Island", study)
        }
    }

    // ---- Progenitor Mime

    /** It enters as a copy with the upkeep ability; each upkeep the original (not a token) makes a token copy. */
    // CR 707.9b, 603.4
    @Test
    fun mimeCopiesAndMakesTokens() = scenario {
        alice.battlefield(bear)
        alice.hand(mime)
        alice.library("Island", "Island", "Island")
        bob.library("Island", "Island", "Island")
        alice.castsWithoutCosts(mime)
        resolve()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        fun bears() = state.battlefield.count { id -> state.obj(id)?.controller == alice.id && view()[id]?.characteristics?.name == bear }
        assertEquals(2, bears())
        advanceTo(TurnStep.Draw, turn = 3)
        assertEquals(3, bears())
    }

    // ---- Ten Gates (CR 104.2b)

    /** The tenth Gate with a different name wins the game. */
    // CR 104.2b, 201.2
    @Test
    fun labyrinthWinsWithTenGates() = scenario {
        alice.battlefield(labyrinth, *gates.take(9).toTypedArray())
        alice.library(gates[9])
        alice.activates(labyrinth, ability = 1, paying = gates.take(3))
        allPass()
        if (pending is Decision.ChooseObjects) alice.chooses(gates[9])
        assertTrue(state.player(bob.id).lost)
    }
}
