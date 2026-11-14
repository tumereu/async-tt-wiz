package arcanum.engine.sets.dmr

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.input.Input
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Dominaria Remastered chunk 02 in play: Auras (CR 303.4). The Mirage-era "as though it had flash" Aura sacrificed at the next cleanup
 * step only when cast at a time a sorcery couldn't have been cast, a threshold lure on the enchanted creature, a damage trigger whose "you"
 * is the Aura's controller, abilities granted to the enchanted creature whose "you" is that creature's controller, and an Aura that
 * returns to its owner's hand.
 */
class DmrChunk02EnchantScenariosTest {
    private val bear = Cards.invent("Dmr Aura Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Dmr Aura Ox", "{G}", "Creature — Ox", "", "1/1")
    private val wolf = Cards.invent("Dmr Aura Wolf", "{1}{G}", "Creature — Wolf", "", "2/2")
    private val tonic = Cards.invent("Dmr Aura Tonic", "{R}", "Sorcery", "You gain 1 life.")
    private val slay = Cards.invent("Dmr Aura Slay", "{B}", "Instant", "Destroy target creature.")

    private val sparkstep = Cards.invent("Sparkstep Instinct", "{1}{R}", "Enchantment — Aura", """
        You may cast this spell as though it had flash. If you cast it any time a sorcery couldn't have been cast, the controller of the permanent it becomes sacrifices it at the beginning of the next cleanup step.
        Enchant creature
        Enchanted creature gets +1/+0 and has first strike.
    """)
    private val beckoning = Cards.invent("Hunter's Beckoning", "{2}{G}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+2.
        Threshold — As long as there are seven or more cards in your graveyard, all creatures able to block enchanted creature do so.
    """)
    private val lifebond = Cards.invent("Kindred Lifebond", "{W}", "Enchantment — Aura", """
        Enchant creature (Target a creature as you cast this. This card enters attached to that creature.)
        Whenever enchanted creature deals damage, you gain that much life.
    """)
    private val tutorship = Cards.invent("Runic Tutorship", "{1}{U}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature has "{T}: This creature deals 1 damage to any target."
    """)
    private val shiv = Cards.invent("Mercury Shiv", "{1}{U}{R}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature has "{T}: This creature deals 1 damage to target player or planeswalker. You draw a card."
    """)
    private val fury = Cards.invent("Unquenched Fury", "{2}{R}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+2 and can't block.
        When this Aura is put into a graveyard from the battlefield, return it to its owner's hand.
    """)

    // CR 702.8a, 307.1, 514.3a: cast in an opponent's upkeep, it is sacrificed at that turn's cleanup step, where its trigger gets priority
    @Test
    fun sparkstepCastOnAnOpponentsTurnIsSacrificedAtCleanup() = scenario {
        turn(number = 1, active = bob, step = TurnStep.Upkeep)
        alice.battlefield("Mountain", "Mountain", bear)
        alice.hand(sparkstep)
        alice.library("Mountain")
        bob.library("Forest", "Forest")
        bob.passes()
        alice.casts(sparkstep, target = bear)
        allPass()
        expect {
            card(sparkstep).zone shouldBe Zone.Battlefield
            card(bear).power shouldBe 3
        }
        advanceTo(TurnStep.Cleanup)
        expect {
            card(sparkstep).zone shouldBe Zone.Battlefield
            stack.size shouldBe 1
        }
        allPass()
        expect {
            card(sparkstep).zone shouldBe Zone.Graveyard
            card(bear).power shouldBe 2
        }
    }

    // CR 307.1: cast in your own main phase with an empty Stack, a sorcery could have been cast: it stays past the cleanup step
    @Test
    fun sparkstepCastAtSorceryTimingStays() = scenario {
        alice.battlefield("Mountain", "Mountain", bear)
        alice.hand(sparkstep)
        alice.library("Mountain")
        bob.library("Forest", "Forest")
        alice.casts(sparkstep, target = bear)
        allPass()
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect {
            card(sparkstep).zone shouldBe Zone.Battlefield
            card(bear).power shouldBe 3
        }
    }

    // CR 307.1: in your own main phase but with a spell on the Stack, a sorcery couldn't have been cast
    @Test
    fun sparkstepCastInResponseInYourMainPhaseIsSacrificed() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", bear)
        alice.hand(tonic, sparkstep)
        alice.library("Mountain")
        bob.library("Forest", "Forest")
        alice.casts(tonic)
        alice.casts(sparkstep, target = bear)
        allPass()
        allPass()
        expect { card(sparkstep).zone shouldBe Zone.Battlefield }
        advanceTo(TurnStep.Cleanup)
        allPass()
        expect { card(sparkstep).zone shouldBe Zone.Graveyard }
    }

    // CR 509.1c, 207.2c: with threshold every creature able to block the enchanted creature must; without it, none has to
    @Test
    fun beckoningLuresBlockersOnlyWithThreshold() {
        for (cards in listOf(6, 7)) scenario {
            alice.battlefield(bear)
            alice.graveyard(*Array(cards) { "Mountain" })
            alice.battlefield(beckoning)
            bob.battlefield(ox, wolf)
            attach(beckoning, bear)
            advanceTo(TurnStep.DeclareAttackers)
            alice.attacks(bear)
            allPass()
            val options = Engine.options(state, bob.id).filterIsInstance<Input.DeclareBlockers>()
            assertTrue(options.isNotEmpty())
            if (cards == 7) assertTrue(options.all { it.blocks.map { b -> b.blocker }.toSet() == setOf(card(ox), card(wolf)) })
            else assertTrue(options.any { it.blocks.isEmpty() })
        }
    }

    // CR 120.1, 109.5: on an opponent's creature, the Aura's controller gains the combat damage it deals
    @Test
    fun lifebondOnAnOpponentsAttackerGainsItsController() = scenario {
        turn(number = 1, active = bob)
        bob.battlefield(bear)
        alice.battlefield(lifebond)
        attach(lifebond, bear)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(bear)
        advanceTo(TurnStep.CombatDamage)
        allPass()
        expect {
            alice.life shouldBe 20
            bob.life shouldBe 20
        }
    }

    // CR 113.10, 120.1: noncombat damage by an ability granted to the enchanted creature counts too
    @Test
    fun lifebondCountsNoncombatDamageOfAGrantedAbility() = scenario {
        alice.battlefield(bear, lifebond, tutorship)
        attach(lifebond, bear)
        attach(tutorship, bear)
        alice.activates(bear, target = "bob")
        allPass()
        allPass()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    // CR 113.10, 109.5: the granted ability is the enchanted creature's, so "you" is that creature's controller, not the Aura's
    @Test
    fun shivOnAnOpponentsCreatureDrawsForThatPlayer() = scenario {
        turn(number = 1, active = bob)
        bob.battlefield(bear)
        alice.library("Mountain")
        bob.library("Forest")
        alice.battlefield(shiv)
        attach(shiv, bear)
        bob.activates(bear, target = "alice")
        allPass()
        expect {
            alice.life shouldBe 19
            bob.hand has "Forest"
            alice.hand lacks "Mountain"
        }
    }

    // CR 704.5m, 603.6c, 400.7e: the creature dies, the Aura goes to the graveyard and its own trigger returns it to its owner's hand
    @Test
    fun furyReturnsToItsOwnersHand() = scenario {
        alice.battlefield("Swamp", bear, fury)
        alice.hand(slay)
        attach(fury, bear)
        expect { card(bear).power shouldBe 4 }
        alice.casts(slay, target = bear)
        allPass()
        expect { card(fury).zone shouldBe Zone.Graveyard }
        allPass()
        expect {
            alice.hand has fury
            card(bear).zone shouldBe Zone.Graveyard
        }
    }
}
