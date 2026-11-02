package arcanum.engine.sets.fra

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.setup.GameResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reality Fracture cards whose wordings needed new pieces, played: an ability from the hand, the next spell that can't be
 * countered, surveil keeping a card, copies of one card of each type cast within a total mana value, winning with an empty
 * library, "if it wasn't attacking", each player sacrificing, a delayed copy of the next instant, exile-the-top casting,
 * loyalty counters among Jaces, token copies with a sacrifice trigger, temporary theft, exile instead of dying, top or
 * bottom of a library, cards from outside the game, and a spell exiled with four of its name. */
class FraCardsScenariosTest {
    private val ulamorth = Cards.invent("Ulamorth, the Exacting Ruin", "{10}", "Legendary Creature — Eldrazi", """
        When you cast this spell, untap all lands you control.
        Flying, trample
        Ward—Sacrifice three permanents.
        {3}, Exile this card from your hand: Target land gains "{T}: Add {C}{C}" until this card is cast from exile. You may cast this card for as long as it remains exiled.
    """, "12/12")
    private val standIn = Cards.invent("Scholar's Stand-In", "{1}{U}", "Creature — Illusion", """
        Flash
        When this creature enters, empower Jace 3.
        {U}, Sacrifice this creature: The next spell you cast this turn can't be countered.
    """, "0/3")
    private val confessor = Cards.invent("Illumined Confessor", "{1}{W}", "Creature — Kor Cleric", """
        Lifelink
        At the beginning of your end step, if you gained life this turn, surveil 1. If you put a card with mana value less than or equal to the amount of life you gained this turn into your graveyard this way, put that card into your hand.
    """, "2/1")
    private val vexmorth = Cards.invent("Vexmorth Quillon", "{3}{U}{B}{B}", "Legendary Creature — Elder Sphinx", """
        Flying
        When Vexmorth Quillon enters, if you cast him, exile up to one target nonland card of each card type from your graveyard. Copy those cards. You may cast any number of spells with total mana value 6 or less from among the copies without paying their mana costs. (Permanent spells cast this way become tokens.)
    """, "5/5")
    private val plimbo = Cards.invent("Plimbo, Hopelessly Astray", "{1}{U}", "Legendary Creature — Homunculus",
        "When one or more of your opponents are dealt combat damage during your turn, draw two cards. If your library has no cards in it, you win the game. Plimbo's owner shuffles him into their library. (If you draw from an empty library this way, you still win the game.)", "1/1")
    private val demise = Cards.invent("Foretold Demise", "{1}{W}", "Instant", "Destroy target creature. If it wasn't attacking, its controller draws a card.")
    private val morrow = Cards.invent("Morrow, Shrouded Slaughterer", "{3}{B}{B}", "Legendary Planeswalker — Garruk", """
        If a creature an opponent controls would die, exile it instead.
        [+2]: Up to one target creature gets -4/-1 until your next turn.
        [−2]: Each player sacrifices a creature of their choice. If you sacrificed a creature this way, create a 4/4 green Beast creature token with trample.
        [−3]: Each opponent discards two cards. For each opponent who didn't discard two nonland cards this way, you draw a card.
    """, loyalty = "5")
    private val frostcaster = Cards.invent("Path of the Frostcaster", "{2}{U}", "Legendary Enchantment", """
        When Path of the Frostcaster enters, empower Jace 5.
        Planeswalkers you control have "[−3]: When you next cast an instant or sorcery spell this turn, copy that spell. You may choose new targets for the copy."
    """)
    private val velka = Cards.invent("Velka, Brand of Defiance", "{2}{R}{R}", "Legendary Planeswalker — Chandra", """
        [+1]: Exile the top card of your library. You may cast that card. If you don't, Velka deals 2 damage to each opponent.
        [+1]: Add {R}{R}.
        [−3]: Velka deals 4 damage to target creature.
        [−7]: You get an emblem with "Whenever you cast a spell, this emblem deals 5 damage to any target."
    """, loyalty = "4")
    private val confront = Cards.invent("Confront Thyself", "{5}{R}{R}", "Sorcery",
        "For each creature target player controls, create a token that's a copy of that creature, except it has haste and \"At the beginning of the end step, if you don't control a planeswalker, sacrifice this creature.\"")
    private val spiteful = Cards.invent("Spiteful Victory", "{W}{B}{B}", "Instant",
        "Exile target creature or planeswalker. If that permanent's mana value was 3 or less, return it to the battlefield tapped under your control. Exile it at the beginning of the next end step.")
    private val scorch = Cards.invent("Quintessence Scorch", "{1}{R}", "Instant",
        "Quintessence Scorch deals 5 damage to target black or green creature or planeswalker. If that permanent would die this turn, exile it instead.")
    private val collision = Cards.invent("Collision of Forces", "{1}{U}{R}", "Instant",
        "Choose target nonland permanent. Its owner may put it on top of their library. If they do, Collision of Forces deals 2 damage to them. If they didn't put the card on top of their library, they put it on the bottom.")
    private val infer = Cards.invent("Infer the Unthinkable", "{1}{B}", "Sorcery",
        "You may reveal exactly two cards you own with different names from outside the game. An opponent chooses one of them. You put that card into your hand.")
    private val advance = Cards.invent("Sphinx's Advance", "{1}{U}{U}", "Instant", """
        Draw two cards. Then you may exile this spell and four cards named Sphinx's Advance from your graveyard. If you do, search your library for a Sphinx creature card, put it onto the battlefield, then shuffle.
        A deck can have any number of cards named Sphinx's Advance.
    """)
    private val sphinx = Cards.invent("Riddling Sphinx", "{4}{U}{U}", "Creature — Sphinx", "Flying", "5/5")
    private val nefrah = Cards.invent("Nefrah, the Dune Chill", "{3}{U}", "Legendary Creature — Human Wizard", """
        When Nefrah enters, for each opponent, tap up to one target creature that player controls. Put a stun counter on each of those creatures. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        {2}{U}: Untap target creature.
    """, "4/3")
    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Wide Ox", "{2}{G}", "Creature — Ox", "", "1/4")
    private val counterspell = Cards.invent("Plain Negation", "{U}{U}", "Instant", "Counter target spell.")
    private val shock = Cards.invent("Quick Shock", "{R}", "Instant", "Quick Shock deals 2 damage to any target.")
    private val study = Cards.invent("Deep Study", "{2}{U}", "Sorcery", "Draw two cards.")
    private val heal = Cards.invent("Warm Embrace", "{W}", "Instant", "You gain 3 life.")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private fun Scenario.resolveStackFully(yes: Boolean = true) {
        var guard = 40
        while (guard-- > 0) {
            when {
                pending is Decision.May -> answer(Answer.YesNo(yes))
                pending is Decision.OrderTriggers -> answer(Answer.Order(decision<Decision.OrderTriggers>().triggers.map { it.index }))
                pending != null -> return
                state.stack.isNotEmpty() -> allPass()
                else -> return
            }
        }
    }

    /** From the hand: a land gains "{T}: Add {C}{C}" until the exiled card is cast; it can be cast from exile, and casting
     * it untaps your lands. */
    // CR 113.6b, 601.2, 611.2b
    @Test
    fun ulamorthFromTheHand() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(ulamorth)
        alice.activates(ulamorth, target = "Forest", paying = listOf("Forest#1", "Forest#2", "Forest#3"))
        resolveStackFully()
        expect { card(ulamorth).zone shouldBe Zone.Exile }
        assertEquals(2, view()[card("Forest", 0)]!!.characteristics!!.abilities.count { it.ability is arcanum.api.Ability.Activated })
        // the land's {C}{C} pays for it while it is being cast (CR 601.2g); it ends once the spell is cast (CR 601.2i)
        alice.casts(ulamorth, way = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().first { it.card == card(ulamorth) }.way)
        resolveStackFully()
        expect { card(ulamorth).zone shouldBe Zone.Battlefield }
        assertEquals(1, view()[card("Forest", 0)]!!.characteristics!!.abilities.count { it.ability is arcanum.api.Ability.Activated })
    }

    /** "The next spell you cast this turn can't be countered." */
    // CR 611.2f, 101.2
    @Test
    fun nextSpellCantBeCountered() = scenario {
        alice.battlefield(standIn, "Island", "Mountain")
        bob.battlefield("Island", "Island")
        alice.hand(shock)
        bob.hand(counterspell)
        alice.activates(standIn, paying = listOf("Island"))
        resolveStackFully()
        alice.casts(shock, target = "bob")
        alice.passes()
        bob.casts(counterspell, target = "Quick Shock")
        resolveStackFully()
        expect { bob.life shouldBe 18 }
    }

    /** Surveil keeps the card put into the graveyard when its mana value is at most the life gained this turn. */
    // CR 701.25a
    @Test
    fun confessorKeepsCheapCards() = scenario {
        alice.battlefield(confessor, "Plains")
        alice.hand(heal)
        alice.library(bear, ox)
        bob.library(bear)
        alice.casts(heal)
        resolveStackFully()
        advanceTo(TurnStep.End)
        resolveStackFully()
        alice.chooses("Market Bear")
        expect { alice.hand has "Market Bear" }
    }

    /** Up to one card of each card type, copied; the copies cast for free within a total mana value of 6, the permanent
     * spells becoming tokens. */
    // CR 707.12, 707.10f, 601.2c
    @Test
    fun vexmorthCastsCopies() = scenario {
        alice.battlefield("Island", "Island", "Island", "Swamp", "Swamp", "Swamp")
        alice.graveyard(shock, ox, study)
        alice.library(bear, bear, bear)
        alice.hand(vexmorth)
        alice.casts(vexmorth)
        resolveStackFully()
        // targets: one of each type
        answer(Answer.Targets(listOf(target(shock), target(ox), target(study))))
        resolveStackFully()
        alice.chooses("Wide Ox", "Deep Study")
        resolveStackFully()
        expect {
            alice.hand.size shouldBe 2
        }
        assertTrue(state.battlefield.any { state.obj(it)?.token == true && nameOf(state.obj(it)!!) == "Wide Ox" })
    }

    /** With an empty library after drawing, its controller wins. */
    // CR 104.2b, 121.4
    @Test
    fun plimboWinsWithAnEmptyLibrary() = scenario {
        alice.battlefield(plimbo, bear)
        alice.library(ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.CombatDamage)
        resolveStackFully()
        assertEquals(GameResult.Won(alice.id), Engine.result(state))
    }

    /** "If it wasn't attacking, its controller draws a card." */
    // CR 608.2h
    @Test
    fun demiseDrawsForANonattacker() = scenario {
        alice.battlefield("Plains", "Plains")
        bob.battlefield(bear)
        bob.library(ox)
        alice.hand(demise)
        alice.casts(demise, target = "Market Bear")
        resolveStackFully()
        expect {
            bob.graveyard has "Market Bear"
            bob.hand has "Wide Ox"
        }
    }

    /** An attacking creature destroyed: no card. */
    // CR 608.2h
    @Test
    fun demiseNoCardForAnAttacker() = scenario {
        bob.battlefield("Plains", "Plains")
        alice.battlefield(bear)
        alice.library(ox)
        bob.hand(demise)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        alice.passes()
        bob.casts(demise, target = "Market Bear")
        resolveStackFully()
        expect {
            alice.graveyard has "Market Bear"
            alice.hand.isEmpty()
        }
    }

    /** Each player sacrifices a creature; the opponent's is exiled instead; you get a Beast for yours. */
    // CR 101.4, 614.1a
    @Test
    fun morrowEachPlayerSacrifices() = scenario {
        alice.battlefield(bear)
        alice.battlefield(morrow, counters = listOf(CounterKind.Loyalty to 5))
        bob.battlefield(ox)
        alice.activates(morrow, ability = 1)
        resolveStackFully()
        expect {
            alice.graveyard has "Market Bear"
            bob.exile has "Wide Ox"
        }
        assertTrue(state.battlefield.any { state.obj(it)?.token == true })
    }

    /** "When you next cast an instant or sorcery spell this turn, copy that spell." */
    // CR 603.7, 707.10
    @Test
    fun frostcasterCopiesTheNextInstant() = scenario {
        alice.battlefield(frostcaster, "Mountain")
        alice.hand(shock)
        alice.battlefield(velka, counters = listOf(CounterKind.Loyalty to 4))
        alice.activates(velka, ability = 4)
        resolveStackFully()
        alice.casts(shock, target = "bob")
        resolveStackFully()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target("bob"))))
        resolveStackFully()
        expect { bob.life shouldBe 16 }
    }

    /** Exile the top card; not cast, 2 damage to each opponent. */
    // CR 608.2g
    @Test
    fun velkaExilesAndBurns() = scenario {
        alice.battlefield(velka, counters = listOf(CounterKind.Loyalty to 4))
        alice.library(ox)
        alice.activates(velka, ability = 0)
        resolveStackFully(yes = false)
        expect {
            alice.exile has "Wide Ox"
            bob.life shouldBe 18
        }
    }

    /** Token copies of a player's creatures with haste and a trigger that sacrifices them without a planeswalker. */
    // CR 707.9b, 603.4
    @Test
    fun confrontCopiesWithHaste() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        bob.battlefield(bear, ox)
        alice.library(bear)
        bob.library(bear)
        alice.hand(confront)
        alice.casts(confront, target = "bob")
        resolveStackFully()
        val tokens = state.battlefield.filter { state.obj(it)?.token == true }
        assertEquals(2, tokens.size)
        assertTrue(tokens.all { view()[it]!!.characteristics!!.hasKeyword(Keyword.Haste) })
        advanceTo(TurnStep.End)
        resolveStackFully()
        assertTrue(state.battlefield.none { state.obj(it)?.token == true })
    }

    /** A permanent of mana value 3 or less comes back under your control, tapped, and is exiled at the next end step. */
    // CR 608.2h, 603.7
    @Test
    fun spitefulVictoryBorrows() = scenario {
        alice.battlefield("Plains", "Swamp", "Swamp")
        bob.battlefield(bear)
        alice.library(bear)
        bob.library(bear)
        alice.hand(spiteful)
        alice.casts(spiteful, target = "Market Bear")
        resolveStackFully()
        expect {
            "Market Bear".controller shouldBe alice.id
            "Market Bear".tapped shouldBe true
        }
        advanceTo(TurnStep.End)
        resolveStackFully()
        expect { bob.exile has "Market Bear" }
    }

    /** "If that permanent would die this turn, exile it instead." */
    // CR 614.1a
    @Test
    fun scorchExilesWhatDies() = scenario {
        alice.battlefield("Mountain", "Mountain")
        bob.battlefield(bear)
        alice.hand(scorch)
        alice.casts(scorch, target = "Market Bear")
        resolveStackFully()
        expect { bob.exile has "Market Bear" }
    }

    /** The owner puts it on top and is dealt 2 damage, or on the bottom. */
    // CR 118.12, 401.4
    @Test
    fun collisionTopOrBottom() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain")
        bob.battlefield(bear)
        bob.library(ox)
        alice.hand(collision)
        alice.casts(collision, target = "Market Bear")
        resolveStackFully(yes = true)
        expect {
            bob.library.names shouldBe listOf("Market Bear", "Wide Ox")
            bob.life shouldBe 18
        }
    }

    /** Two different cards from outside the game revealed; an opponent chooses which one you get. */
    // CR 400.11
    @Test
    fun inferFromOutsideTheGame() = scenario {
        alice.battlefield("Swamp", "Swamp")
        put(alice, listOf(bear, ox), Zone.Outside)
        alice.hand(infer)
        alice.casts(infer)
        resolveStackFully()
        // the only two cards outside the game: both revealed without a choice
        if ((pending as? Decision.ChooseObjects)?.min == 2) alice.chooses("Market Bear", "Wide Ox")
        resolveStackFully()
        bob.chooses("Wide Ox")
        resolveStackFully()
        expect { alice.hand has "Wide Ox" }
    }

    /** With four others of its name in the graveyard, it exiles them and itself to fetch a Sphinx. */
    // CR 118.12, 100.2a
    @Test
    fun advanceFetchesASphinx() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.graveyard(advance, advance, advance, advance)
        alice.library(bear, ox, sphinx)
        alice.hand(advance)
        alice.casts(advance)
        resolveStackFully()
        // the four other cards are the only ones of its name: exiled with it without a choice; then the search
        alice.chooses("Riddling Sphinx")
        resolveStackFully()
        expect {
            card("Riddling Sphinx").zone shouldBe Zone.Battlefield
            alice.exile.names.count { it == "Sphinx's Advance" } shouldBe 5
        }
    }

    /** For each opponent, up to one creature tapped with a stun counter. */
    // CR 601.2c, 122.1d
    @Test
    fun nefrahStuns() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        bob.battlefield(bear)
        alice.hand(nefrah)
        alice.casts(nefrah)
        resolveStackFully()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target("Market Bear"))))
        resolveStackFully()
        expect {
            "Market Bear".tapped shouldBe true
            "Market Bear".counters(CounterKind.Stun) shouldBe 1
        }
    }
}
