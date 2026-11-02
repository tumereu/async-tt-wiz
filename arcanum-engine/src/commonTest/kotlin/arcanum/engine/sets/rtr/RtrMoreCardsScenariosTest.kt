package arcanum.engine.sets.rtr

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertTrue

/** More Return to Ravnica cards in play: life lost this turn, half a player's life, a lure, hexproof on other turns, a land
 * that untaps in every untap step, a dead creature on top of its library, revealing until a land, damage by the damage
 * dealt, a planeswalker's protection trigger, a target's controller discarding. */
class RtrMoreCardsScenariosTest {
    private val zarkhul = Cards.invent("Zarkhul, Lord of Revels", "{B}{B}{R}{R}", "Legendary Creature — Demon", """
        You can't cast Zarkhul unless an opponent lost life this turn.
        Flying, trample
        Creature spells you cast cost {1} less to cast for each 1 life your opponents have lost this turn.
    """, "6/6")
    private val cryptspawn = Cards.invent("Cryptspawn Terror", "{1}{B/R}{B/R}", "Creature — Horror", """
        Trample
        This creature enters with X +1/+1 counters on it, where X is the total life lost by your opponents this turn.
    """, "0/0")
    private val jubilee = Cards.invent("Mayhem Jubilee", "{4}{B}{R}", "Enchantment", """
        Players can't gain life.
        At the beginning of each player's upkeep, that player loses half their life, rounded up.
    """)
    private val lure = Cards.invent("Rot Farm Lure", "{3}{G}", "Creature — Elf Rogue", """
        All creatures able to block this creature do so.
        Scavenge {3}{G}{G} ({3}{G}{G}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)
    """, "2/2")
    private val innkeeper = Cards.invent("Elm Lane Innkeeper", "{2}{G}", "Creature — Elf",
        "During turns other than yours, tapped creatures you control have hexproof.", "2/2")
    private val overgrowth = Cards.invent("Civic Overgrowth", "{G}", "Enchantment — Aura", """
        Enchant land
        Enchanted land has "Untap this land during each other player's untap step."
    """)
    private val wraith = Cards.invent("Restless Wraith", "{3}{B}", "Creature — Spirit", """
        Flying
        When this creature dies, put it on top of its owner's library.
    """, "2/2")
    private val bury = Cards.invent("Bury the Clues", "{4}{B}", "Sorcery",
        "Destroy target land. Its controller reveals cards from the top of their library until they reveal a land card, then puts those cards into their graveyard.")
    private val overlord = Cards.invent("Necropolis Overlord", "{3}{B}{B}{B}", "Creature — Vampire", """
        Flying
        Whenever a creature you control deals combat damage to a player, put that many +1/+1 counters on it.
    """, "6/5")
    private val ilsevra = Cards.invent("Ilsevra the Veiled", "{3}{B}{G}", "Legendary Planeswalker — Vraska", """
        [+1]: Until your next turn, whenever a creature deals combat damage to Ilsevra, destroy that creature.
        [−3]: Destroy target nonland permanent.
        [−7]: Create three 1/1 black Assassin creature tokens with "Whenever this token deals combat damage to a player, that player loses the game."
    """, loyalty = "5")
    private val zReturn = Cards.invent("Zarkhul's Return", "{X}{B}{R}", "Sorcery",
        "Zarkhul's Return deals X damage to target opponent or planeswalker. That player or that planeswalker's controller discards X cards.")
    private val pretender = Cards.invent("Fae Pretender", "{U}", "Creature — Faerie Rogue", """
        Flying
        When this creature enters, sacrifice it unless you return another creature you control to its owner's hand.
    """, "2/1")
    private val mindwhirl = Cards.invent("Mindwhirl", "{4}{U}", "Instant",
        "Shuffle all cards from your graveyard into your library. Target player mills that many cards.")

    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val wolf = Cards.invent("Alley Wolf", "{1}{G}", "Creature — Wolf", "", "3/3")
    private val shock = Cards.invent("Street Spark", "{R}", "Instant", "Street Spark deals 2 damage to any target.")
    private val insight = Cards.invent("Quick Insight", "{1}{U}", "Instant", "Draw a card.")
    private val murder = Cards.invent("Quick Execution", "{1}{B}{B}", "Instant", "Destroy target creature.")

    private fun Scenario.yes() = answer(Answer.YesNo(true))

    /** Can't be cast before an opponent lost life this turn; then creature spells cost {1} less per life lost. */
    // CR 601.3, 601.2f
    @Test
    fun zarkhulNeedsLifeLost() = scenario {
        alice.battlefield("Swamp", "Swamp", "Mountain", "Mountain")
        alice.hand(zarkhul, shock)
        assertTrue(Engine.options(state, alice.id).none { it is Input.Cast && it.card == card(zarkhul) })
        alice.castsWithoutCosts(shock, target = "bob")
        resolve()
        assertTrue(Engine.options(state, alice.id).any { it is Input.Cast && it.card == card(zarkhul) })
    }

    /** The life opponents lost this turn, counted as it enters (CR 614.1c). */
    @Test
    fun cryptspawnCountsLifeLost() = scenario {
        alice.hand(shock, cryptspawn)
        alice.castsWithoutCosts(shock, target = "bob")
        resolve()
        alice.castsWithoutCosts(cryptspawn)
        resolve()
        expect { cryptspawn.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2 }
    }

    /** Half of 15 rounded up is 8 (CR 107.1a). */
    @Test
    fun halfTheirLifeRoundedUp() = scenario {
        alice.battlefield(jubilee)
        alice.life = 15
        beginStep(TurnStep.Upkeep)
        resolve()
        expect { alice.life shouldBe 7 }
    }

    /** Every creature that could block it must (CR 509.1c): a declaration without them is illegal. */
    @Test
    fun lureMustBeBlocked() = scenario {
        alice.battlefield(lure)
        bob.battlefield(bear, wolf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(lure)
        allPass()
        val options = Engine.options(state, bob.id).filterIsInstance<Input.DeclareBlockers>()
        assertTrue(options.isNotEmpty())
        assertTrue(options.all { it.blocks.map { b -> b.blocker }.toSet() == setOf(card(bear), card(wolf)) })
    }

    /** Hexproof only while tapped and during another player's turn. */
    @Test
    fun innkeeperHexproofOnOtherTurns() = scenario {
        turn(1, active = bob)
        alice.battlefield(innkeeper, bear)
        change(arcanum.engine.change.Changes.tap(state, card(bear)))
        assertTrue(view()[card(bear)]!!.characteristics!!.abilities.any { it.ability == arcanum.api.Keyword.Hexproof })
    }

    /** The land untaps in the other player's untap step too (CR 502.3). */
    @Test
    fun landUntapsInEveryUntapStep() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(overgrowth)
        alice.library("Forest", "Forest")
        bob.library("Swamp", "Swamp")
        alice.castsWithoutCosts(overgrowth, target = "Forest")
        resolve()
        change(arcanum.engine.change.Changes.tap(state, card("Forest", 0)))
        change(arcanum.engine.change.Changes.tap(state, card("Forest", 1)))
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            card("Forest", 0).tapped shouldBe false
            card("Forest", 1).tapped shouldBe true
        }
    }

    /** The card in the graveyard goes on top of its owner's library (CR 400.7). */
    @Test
    fun wraithBackOnTop() = scenario {
        alice.battlefield(wraith)
        alice.library("Island")
        bob.hand(murder)
        bob.castsWithoutCosts(murder, target = wraith)
        resolve()
        resolve()
        expect { alice.library.names shouldBe listOf(wraith, "Island") }
    }

    /** The land's controller reveals until a land; all those cards go to their graveyard. */
    // CR 701.20
    @Test
    fun revealUntilALand() = scenario {
        alice.hand(bury)
        bob.battlefield("Swamp")
        bob.library(insight, shock, "Swamp", "Island")
        alice.castsWithoutCosts(bury, target = "Swamp")
        resolve()
        expect {
            bob.graveyard.names.toSet() shouldBe setOf("Swamp", insight, shock)
            bob.graveyard.size shouldBe 4
            bob.library.names shouldBe listOf("Island")
        }
    }

    /** Counters equal to the combat damage dealt, on the creature that dealt it. */
    @Test
    fun countersByDamageDealt() = scenario {
        alice.battlefield(overlord, wolf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wolf)
        advanceTo(TurnStep.CombatDamage)
        resolveStack()
        expect {
            bob.life shouldBe 17
            wolf.counters(CounterKind.PowerToughness(1, 1)) shouldBe 3
        }
    }

    /** Combat damage to the planeswalker destroys the creature that dealt it, until its controller's next turn. */
    // CR 603.7b
    @Test
    fun ilsevraDestroysWhatDamagesHer() = scenario {
        alice.battlefield(ilsevra)
        bob.battlefield(wolf)
        alice.library("Island", "Island")
        bob.library("Swamp", "Swamp")
        change(arcanum.engine.change.Changes.counters(state, TargetRef.Object(card(ilsevra)), CounterKind.Loyalty, 5))
        alice.activates(ilsevra, ability = 0)
        allPass()
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(wolf, defender = ilsevra)
        advanceTo(TurnStep.CombatDamage, turn = 2)
        resolveStack()
        expect {
            bob.graveyard has wolf
            ilsevra.counters(CounterKind.Loyalty) shouldBe 3
        }
    }

    /** Aimed at a planeswalker, its controller discards. */
    @Test
    fun planeswalkersControllerDiscards() = scenario {
        alice.hand(zReturn)
        bob.battlefield(ilsevra)
        bob.hand(insight, shock)
        change(arcanum.engine.change.Changes.counters(state, TargetRef.Object(card(ilsevra)), CounterKind.Loyalty, 5))
        alice.castsWithoutCosts(zReturn, target = ilsevra, x = 2)
        resolve()
        expect {
            ilsevra.counters(CounterKind.Loyalty) shouldBe 3
            bob.graveyard.names.toSet() shouldBe setOf(insight, shock)
        }
    }

    /** Returning another creature keeps it; with none to return it is sacrificed (CR 118.12a). */
    @Test
    fun pretenderUnlessReturn() = scenario {
        alice.hand(pretender)
        alice.battlefield(bear)
        alice.castsWithoutCosts(pretender)
        resolve()
        resolve()
        yes()
        if (pending != null) alice.chooses(bear)
        expect {
            alice.hand has bear
            alice.battlefield has pretender
        }
    }

    /** "That many": as many as were shuffled in. */
    @Test
    fun millThatMany() = scenario {
        alice.hand(mindwhirl)
        alice.graveyard(insight, shock, bear)
        bob.library("Swamp", "Swamp", "Swamp", "Swamp")
        alice.castsWithoutCosts(mindwhirl, target = "bob")
        resolve()
        expect {
            bob.graveyard.size shouldBe 3
            alice.library.size shouldBe 3
        }
    }
}
