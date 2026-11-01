package arcanum.engine.cube

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The cube's chunk 36 (attack triggers and combat damage triggers), played as a player would. */
class Chunk36AttackAndCombatDamageScenariosTest {
    private val bear = Cards.invent("Mossback Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Hulking Mudogre", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val idol = Cards.invent("Brass Idol", "{2}", "Artifact")

    private val p1p1 = CounterKind.PowerToughness(1, 1)

    /** Chooses targets when asked; a slot with only as many legal targets as it needs is filled without asking (P5). */
    private fun Scenario.targets(vararg names: String) {
        if (pending is Decision.Targets) answer(Answer.Targets(names.map { target(it) }))
    }

    private fun Scenario.yes() = answer(Answer.YesNo(true))

    private fun Scenario.toAttackers() = advanceTo(TurnStep.DeclareAttackers)

    /** Attacks each combat if able; attacking: discard the hand, then draw three. */
    @Test
    fun kaelsMarauder() = scenario {
        alice.battlefield("Kael's Marauder", bear)
        alice.hand("Forest", "Storm Spark")
        alice.library("Island", "Island", "Island", "Swamp")

        toAttackers()
        // CR 508.1d: it must attack
        assertFailsWith<IllegalArgumentException> { alice.attacks(bear) }
        alice.attacks("Kael's Marauder")
        allPass()
        expect {
            alice.graveyard has "Forest"
            alice.graveyard has "Storm Spark"
            alice.hand.names shouldBe listOf("Island", "Island", "Island")
        }
    }

    /** Madness {1}{R}: discarded by the first one's attack, the second is exiled and may be cast for its madness cost. */
    @Test
    fun kaelsMarauderMadness() = scenario {
        alice.battlefield("Kael's Marauder", "Mountain", "Mountain")
        alice.hand("Kael's Marauder")
        alice.library("Island", "Island", "Island")

        toAttackers()
        alice.attacks("Kael's Marauder")
        allPass()
        // CR 702.35a: discarded into exile; the madness trigger goes on the Stack
        expect {
            card("Kael's Marauder", 1).zone shouldBe arcanum.api.Zone.Exile
            stack.names shouldBe listOf("ability of Kael's Marauder")
        }
        allPass()
        yes()
        // Casting it for a cost from an effect is done by hand for now (D61)
        assertEquals("Cast Kael's Marauder for {1}{R}", decision<Decision.Manual>().text)
        answer(Answer.Done)
        expect {
            card("Kael's Marauder", 1).zone shouldBe arcanum.api.Zone.Exile
            alice.hand.size shouldBe 3
        }
    }

    /** Cast for its madness cost by the engine. */
    @Ignore("engine: casting for a cost from an effect (madness) is a manual step")
    @Test
    fun kaelsMarauderMadnessCast() = scenario {
        alice.battlefield("Kael's Marauder", "Mountain", "Mountain")
        alice.hand("Kael's Marauder")
        alice.library("Island", "Island", "Island")

        toAttackers()
        alice.attacks("Kael's Marauder")
        allPass()
        allPass()
        yes()
        expect { stack.names shouldBe listOf("Kael's Marauder") }
        allPass()
        expect { alice.battlefield.names.count { it == "Kael's Marauder" } shouldBe 2 }
    }

    /** Discarded to pay a cost, it is exiled for madness too. */
    // CR 702.35a, 614.1: a discard to pay a cost is a discard
    @Ignore("engine: zone changes paying a cost (discard, sacrifice, exile) skip replacement effects, so madness misses a discard cost")
    @Test
    fun kaelsMarauderMadnessFromACost() = scenario {
        alice.battlefield("Tombcracker", "Swamp", "Swamp", "Mountain", "Mountain")
        alice.hand("Kael's Marauder")

        alice.activates("Tombcracker", choosing = listOf("Kael's Marauder"))
        expect {
            alice.exile has "Kael's Marauder"
            stack.size shouldBe 2
        }
    }

    /** Haste; attacking: exile the top card face down; {R}, discard the hand, sacrifice it: the exiled cards go to hand. */
    @Test
    fun gearwheelCourier() = scenario {
        alice.battlefield("Gearwheel Courier", "Mountain", sick = true)
        alice.hand("Forest")
        alice.library("Storm Spark", "Island")

        toAttackers()
        // CR 702.10b: haste
        alice.attacks("Gearwheel Courier")
        allPass()
        expect {
            alice.exile has "Storm Spark"
            alice.library.names shouldBe listOf("Island")
        }
        assertEquals(true, state.objOrThrow(card("Storm Spark")).faceDown)

        alice.activates("Gearwheel Courier")
        allPass()
        expect {
            alice.hand.names shouldBe listOf("Storm Spark")
            alice.graveyard has "Forest"
            alice.graveyard has "Gearwheel Courier"
        }
    }

    /** Attacking: you may pay {1} and discard a card to draw one; discarding nonland cards lets you exile one and cast it
     * this turn. */
    @Test
    fun paranoidRanter() = scenario {
        alice.battlefield("Paranoid Ranter", "Mountain", "Mountain")
        alice.hand("Storm Spark")
        alice.library("Island")

        toAttackers()
        alice.attacks("Paranoid Ranter")
        allPass()
        yes()
        // Mana on resolution is paid by hand (D61); the only card in hand is discarded
        answer(Answer.Done)
        expect {
            alice.hand.names shouldBe listOf("Island")
            alice.graveyard has "Storm Spark"
            stack.names shouldBe listOf("ability of Paranoid Ranter")
        }

        allPass()
        yes()
        expect { alice.exile has "Storm Spark" }
        // CR 601.2: cast from exile this turn
        alice.casts("Storm Spark", target = "bob")
        allPass()
        expect { bob.life shouldBe 17 }
    }

    /** Trample; attacking: you may cast an instant or sorcery card with mana value up to its power from your graveyard
     * free, exiled afterwards. */
    @Test
    fun gravehordeSpellcaller() = scenario {
        alice.battlefield("Gravehorde Spellcaller")
        alice.graveyard("Storm Spark", "Cinder Bolt")

        toAttackers()
        alice.attacks("Gravehorde Spellcaller")
        targets("Storm Spark")
        allPass()
        yes()
        targets("bob")
        expect { stack.names shouldBe listOf("Storm Spark") }
        allPass()
        expect {
            bob.life shouldBe 17
            alice.exile has "Storm Spark"
            "Gravehorde Spellcaller" hasKeyword Keyword.Trample
        }
    }

    /** Reach; attacking: mill three, and you may put a land card from among them into your hand. */
    @Test
    fun oakheart() = scenario {
        alice.battlefield("Oakheart")
        alice.library("Forest", "Storm Spark", bear, "Island")

        toAttackers()
        alice.attacks("Oakheart")
        allPass()
        alice.chooses("Forest")
        expect {
            alice.hand.names shouldBe listOf("Forest")
            alice.graveyard has "Storm Spark"
            alice.graveyard has bear
            alice.library.names shouldBe listOf("Island")
            "Oakheart" hasKeyword Keyword.Reach
        }
    }

    /** During your turn, nonland permanent cards in your graveyard have retrace. */
    @Test
    fun oakheartRetrace() = scenario {
        alice.battlefield("Oakheart", "Forest", "Forest")
        alice.graveyard(bear)
        alice.hand("Island")

        // CR 702.81a: cast from the graveyard, discarding a land card in addition
        alice.casts(bear)
        expect {
            alice.graveyard has "Island"
            stack.names shouldBe listOf(bear)
        }
        allPass()
        expect { alice.battlefield has bear }
    }

    /** Flash; attacking: exile up to one other nonland permanent until the next end step; if it comes back under your
     * control, a +1/+1 counter on Brisa. */
    @Test
    fun brisaBoundingShepherd() = scenario {
        alice.battlefield("Brisa, Bounding Shepherd", bear)
        bob.battlefield(ogre)

        toAttackers()
        alice.attacks("Brisa, Bounding Shepherd")
        targets(bear)
        allPass()
        expect { alice.exile has bear }

        advanceTo(TurnStep.End)
        // CR 603.7: the delayed trigger at the beginning of the next end step
        allPass()
        expect {
            alice.battlefield has bear
            "Brisa, Bounding Shepherd".counters(p1p1) shouldBe 1
        }
    }

    /** An opponent's permanent comes back under its owner's control: no counter. */
    @Test
    fun brisaExilesAnOpponentsPermanent() = scenario {
        alice.battlefield("Brisa, Bounding Shepherd")
        bob.battlefield(ogre)

        toAttackers()
        alice.attacks("Brisa, Bounding Shepherd")
        targets(ogre)
        allPass()
        expect { bob.exile has ogre }
        advanceTo(TurnStep.End)
        allPass()
        expect {
            bob.battlefield has ogre
            "Brisa, Bounding Shepherd".counters(p1p1) shouldBe 0
        }
    }

    /** Flying; entering or attacking: sacrifice another permanent; each sacrifice: a +1/+1 counter and a card. */
    @Test
    fun vorgathFeyHexedTyrant() = scenario {
        alice.battlefield("Swamp", "Mountain", "Forest", "Forest", "Forest", idol)
        alice.hand("Vorgath, Fey-Hexed Tyrant")
        alice.library("Island", "Island")

        alice.casts("Vorgath, Fey-Hexed Tyrant")
        allPass()
        allPass()
        alice.chooses(idol)
        expect {
            alice.graveyard has idol
            stack.names shouldBe listOf("ability of Vorgath, Fey-Hexed Tyrant")
        }
        allPass()
        expect {
            "Vorgath, Fey-Hexed Tyrant".counters(p1p1) shouldBe 1
            alice.hand.size shouldBe 1
            "Vorgath, Fey-Hexed Tyrant" hasKeyword Keyword.Flying
        }
    }

    @Test
    fun vorgathAttacks() = scenario {
        alice.battlefield("Vorgath, Fey-Hexed Tyrant", bear)
        alice.library("Island")

        toAttackers()
        alice.attacks("Vorgath, Fey-Hexed Tyrant")
        allPass()
        // The only other permanent is sacrificed without a choice
        expect { alice.graveyard has bear }
        allPass()
        expect {
            "Vorgath, Fey-Hexed Tyrant".power shouldBe 5
            alice.hand.names shouldBe listOf("Island")
        }
    }

    /** Training; whenever you cast a creature spell, a 1/1 Human Soldier token with training. */
    @Test
    fun aldricFistOfTheSeraphs() = scenario {
        alice.battlefield("Aldric, Fist of the Seraphs", ogre, "Forest", "Forest")
        alice.hand(bear)

        alice.casts(bear)
        expect { stack.names shouldBe listOf(bear, "ability of Aldric, Fist of the Seraphs") }
        allPass()
        expect {
            alice.battlefield has "Human Soldier Token"
            "Human Soldier Token" hasKeyword Keyword.Training
        }
        allPass()

        // CR 702.149a: training: attacking with a creature with greater power
        toAttackers()
        alice.attacks("Aldric, Fist of the Seraphs", ogre)
        allPass()
        expect { "Aldric, Fist of the Seraphs".counters(p1p1) shouldBe 1 }
    }

    /** Battle cry; can't be blocked except by creatures with flying or reach. */
    @Test
    fun beaconPest() = scenario {
        alice.battlefield("Beacon Pest", ogre)
        bob.battlefield(bear, "Oakheart")

        toAttackers()
        alice.attacks("Beacon Pest", ogre)
        allPass()
        // CR 702.91a: battle cry
        expect {
            ogre.power shouldBe 5
            "Beacon Pest".power shouldBe 0
        }
        allPass()
        assertFailsWith<IllegalArgumentException> { bob.blocks(bear to "Beacon Pest") }
        bob.blocks("Oakheart" to "Beacon Pest")
    }

    /** Ninjutsu {1}{U}; combat damage to a player: you may draw a card. */
    @Test
    fun ninjaOfTheSilentHours() = scenario {
        ninjutsu("Ninja of the Silent Hours", "Island", "Island")
        expect {
            alice.battlefield has "Ninja of the Silent Hours"
            "Ninja of the Silent Hours".tapped shouldBe true
            alice.hand has bear
        }
        advanceTo(TurnStep.CombatDamage)
        allPass()
        yes()
        expect {
            bob.life shouldBe 18
            alice.hand has "Swamp"
        }
    }

    /** Ninjutsu {U}{B}; whenever a Ninja you control deals combat damage to a player, draw a card. */
    @Test
    fun cunningInfiltrator() = scenario {
        ninjutsu("Cunning Infiltrator", "Island", "Swamp")
        advanceTo(TurnStep.CombatDamage)
        allPass()
        expect {
            bob.life shouldBe 18
            alice.hand has "Swamp"
        }
    }

    /** Ninjutsu {U}; combat damage to a player: you may draw, then discard unless it entered this turn. */
    @Test
    fun neonCircuitHacker() = scenario {
        ninjutsu("Neon-Circuit Hacker", "Island")
        advanceTo(TurnStep.CombatDamage)
        allPass()
        yes()
        // It entered this turn: no discard
        expect {
            bob.life shouldBe 18
            alice.hand.names shouldBe listOf(bear, "Swamp")
        }
    }

    @Test
    fun neonCircuitHackerDiscardsLater() = scenario {
        alice.battlefield("Neon-Circuit Hacker")
        alice.hand("Forest")
        alice.library("Swamp")

        toAttackers()
        alice.attacks("Neon-Circuit Hacker")
        advanceTo(TurnStep.CombatDamage)
        allPass()
        yes()
        alice.chooses("Forest")
        expect {
            alice.hand.names shouldBe listOf("Swamp")
            alice.graveyard has "Forest"
        }
    }

    /** Commander ninjutsu {U}{B}; a Ninja's combat damage to a player reveals the top card into your hand, and each
     * opponent loses life equal to its mana value. */
    @Test
    fun akemiTheFoxsShadow() = scenario {
        ninjutsu("Akemi, the Fox's Shadow", "Island", "Swamp", top = "Hulking Mudogre")
        advanceTo(TurnStep.CombatDamage)
        allPass()
        expect {
            alice.hand has ogre
            // 1 combat damage, then 4 for the Ogre's mana value
            bob.life shouldBe 15
        }
    }

    /** Attacks with Mossback Cub, unblocked, and returns it for [ninja] by ninjutsu paid with [lands]. */
    private fun Scenario.ninjutsu(ninja: String, vararg lands: String, top: String = "Swamp") {
        alice.battlefield(bear, *lands)
        alice.hand(ninja)
        alice.library(top)

        toAttackers()
        alice.attacks(bear)
        allPass()
        assertEquals(TurnStep.DeclareBlockers, state.turn.step)
        // CR 702.49a: after blockers are declared, return an unblocked attacker: the Ninja enters tapped and attacking
        alice.activates(ninja)
        allPass()
        assertEquals(true, state.combat!!.attackers.any { it.id == card(ninja) })
    }

    /** Rage: attacking puts a +1/+1 counter on it; at each end step, if a card left your graveyard this turn, it deals
     * damage equal to its power to each opponent. */
    @Test
    fun brakkaWildWhelp() = scenario {
        alice.battlefield("Brakka, Wild Whelp", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard("Cinder Bolt")

        toAttackers()
        alice.attacks("Brakka, Wild Whelp")
        allPass()
        expect { "Brakka, Wild Whelp".power shouldBe 3 }
        advanceTo(TurnStep.PostcombatMain)
        expect { bob.life shouldBe 17 }

        // Flashback takes the card out of the graveyard
        alice.casts("Cinder Bolt", target = "bob")
        allPass()
        advanceTo(TurnStep.End)
        allPass()
        expect { bob.life shouldBe 12 }
    }

    /** Without a card leaving the graveyard, no damage at the end step. */
    @Test
    fun brakkaWithoutGraveyardUse() = scenario {
        alice.battlefield("Brakka, Wild Whelp")
        advanceTo(TurnStep.End)
        expect {
            stack.isEmpty()
            bob.life shouldBe 20
        }
    }
}
