package arcanum.engine.cube

import arcanum.api.Ability
import arcanum.api.Mechanic
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Chunk 14 of the cube in play: copies, storm and casting from the graveyard. */
class Chunk14CopiesStormAndGraveyardCastingScenariosTest {
    private fun Scenario.alternative(name: String): CastWay = CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
        (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost } == true
    }.origin)

    /** The cast [name] offers [player] now (from an effect's permission, a granted flashback, …). */
    private fun Scenario.castOption(player: arcanum.engine.scenario.ScenarioPlayer, name: String): Input.Cast =
        Engine.options(state, player.id).filterIsInstance<Input.Cast>().firstOrNull { it.card == card(name) }
            ?: error("$player can't cast $name now: ${Engine.options(state, player.id)}")


    /** Linked Lightning: 3 damage; the damaged player may pay {R}{R} to copy it back. */
    @Test
    fun linkedLightningIsCopiedBack() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Linked Lightning")
        bob.battlefield("Mountain", "Mountain")

        alice.casts("Linked Lightning", target = "bob")
        allPass()
        val may = decision<Decision.May>()
        assertEquals(bob.id, may.player)
        answer(Answer.YesNo(true))
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        // Bob copies it and chooses a new target: alice
        while (pending is Decision.May) answer(Answer.YesNo(true))
        answer(Answer.Targets(listOf(target("alice"))))
        expect {
            bob.life shouldBe 17
            card("Mountain", 1).tapped shouldBe true
            stack.names shouldBe listOf("copy of Linked Lightning")
        }
        resolveStack()
        // Alice may pay {R}{R} in turn, but has no untapped Mountains: she declines
        while (pending is Decision.May) answer(Answer.YesNo(false))

        expect {
            alice.life shouldBe 17
            alice.graveyard has "Linked Lightning"
        }
    }

    /** Mounting Retribution: copies a spell you control once from the hand, twice by flashback. */
    @Test
    fun mountingRetributionCopiesOnceFromTheHand() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Storm Spark", "Mounting Retribution")

        alice.casts("Storm Spark", target = "bob")
        alice.casts("Mounting Retribution", target = "Storm Spark")
        allPass()
        answer(Answer.YesNo(false))
        expect { stack.names shouldBe listOf("Storm Spark", "copy of Storm Spark") }
        allPass()
        allPass()

        expect {
            bob.life shouldBe 14
            alice.graveyard has "Mounting Retribution"
        }
    }

    /** Mounting Retribution by flashback: cast from a graveyard, it copies the spell twice and is exiled. */
    @Test
    fun mountingRetributionFlashbackCopiesTwice() = scenario {
        // CR 702.34a
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Storm Spark")
        alice.graveyard("Mounting Retribution")

        alice.casts("Storm Spark", target = "bob")
        alice.casts("Mounting Retribution", target = "Storm Spark")
        allPass()
        while (pending is Decision.May) answer(Answer.YesNo(false))
        expect { stack.names shouldBe listOf("Storm Spark", "copy of Storm Spark", "copy of Storm Spark") }
        repeat(3) { allPass() }

        expect {
            bob.life shouldBe 11
            alice.exile has "Mounting Retribution"
        }
    }

    /** Flash of Mirroring cast by sacrificing a red creature: copies the opponent's spell with a new target. */
    @Test
    fun flashOfMirroringForFreeCopiesAnOpponentsSpell() = scenario {
        // CR 118.9, 707.10
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.battlefield("Scorching Mudlizard", "Elvish Seer")
        alice.hand("Flash of Mirroring")

        alice.passes()
        bob.casts("Storm Spark", target = "Elvish Seer")
        bob.passes()
        alice.casts("Flash of Mirroring", way = alternative("Flash of Mirroring"), target = "Storm Spark", choosing = listOf("Scorching Mudlizard"))
        expect { alice.graveyard has "Scorching Mudlizard" }
        allPass()
        answer(Answer.YesNo(true))
        answer(Answer.Targets(listOf(target("bob"))))
        repeat(2) { allPass() }

        expect {
            bob.life shouldBe 17
            alice.graveyard has "Elvish Seer"
        }
    }

    /** Encore Round: for the rest of the turn, every instant or sorcery a player casts is copied. */
    @Test
    fun encoreRoundCopiesTheNextSpells() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Encore Round", "Storm Spark")

        alice.casts("Encore Round", paying = listOf("Mountain", "Mountain#1", "Mountain#2"))
        allPass()
        alice.casts("Storm Spark", target = "bob")
        expect { stack.names shouldBe listOf("Storm Spark", "ability of Encore Round") }
        allPass()
        while (pending is Decision.May) answer(Answer.YesNo(false))
        expect { stack.names shouldBe listOf("Storm Spark", "copy of Storm Spark") }
        repeat(2) { allPass() }

        expect { bob.life shouldBe 14 }
    }

    /** Warden's Reclamation: return a cheap permanent card from your graveyard to the battlefield. */
    @Test
    fun wardensReclamationReturnsAPermanent() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand("Warden's Reclamation")
        alice.graveyard("Elvish Hierophant", "Deadly Plotting")

        alice.casts("Warden's Reclamation", target = "Elvish Hierophant")
        allPass()

        expect {
            alice.battlefield has "Elvish Hierophant"
            alice.graveyard.names shouldBe listOf("Deadly Plotting", "Warden's Reclamation")
        }
    }

    /** Warden's Reclamation by flashback: cast from a graveyard, it may copy itself for a second permanent. */
    @Test
    fun wardensReclamationFlashbackCopiesItself() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.graveyard("Warden's Reclamation", "Elvish Hierophant", "Lotus Frond")

        alice.casts("Warden's Reclamation", target = "Elvish Hierophant")
        allPass()
        // Copy it, with Lotus Frond as the copy's target
        answer(Answer.YesNo(true))
        while (pending is Decision.May) answer(Answer.YesNo(true))
        answer(Answer.Targets(listOf(target("Lotus Frond"))))
        expect {
            alice.battlefield has "Elvish Hierophant"
            stack.names shouldBe listOf("copy of Warden's Reclamation")
        }
        allPass()

        expect {
            alice.battlefield has "Lotus Frond"
            alice.exile has "Warden's Reclamation"
        }
    }

    /** Mind Chill with storm: one spell before it, so it and one copy each mill three. */
    @Test
    fun mindChillStorm() = scenario {
        // CR 702.40a
        alice.battlefield("Island", "Island", "Mountain")
        alice.hand("Storm Spark", "Mind Chill")
        bob.library("Forest", "Forest", "Forest", "Island", "Island", "Island", "Swamp")

        alice.casts("Storm Spark", target = "bob")
        allPass()
        alice.casts("Mind Chill", target = "bob")
        expect { stack.names shouldBe listOf("Mind Chill", "ability of Mind Chill") }
        allPass()
        while (pending is Decision.May) answer(Answer.YesNo(false))
        repeat(2) { allPass() }

        expect {
            bob.graveyard.size shouldBe 6
            bob.library.names shouldBe listOf("Swamp")
        }
    }

    /** Chitterstorm with two spells before it: three Squirrels. */
    @Test
    fun chitterstormStorm() = scenario {
        alice.battlefield("Mountain", "Mountain", "Forest", "Forest")
        alice.hand("Storm Spark", "Cinder Bolt", "Chitterstorm")

        alice.casts("Storm Spark", target = "bob")
        allPass()
        alice.casts("Cinder Bolt", target = "bob")
        allPass()
        alice.casts("Chitterstorm")
        repeat(4) { allPass() }

        expect {
            alice.battlefield.names.count { it == "Squirrel Token" } shouldBe 3
            "Squirrel Token".power shouldBe 1
            stack.isEmpty()
        }
    }

    /** Flood the Burrows with one spell before it: four Goblins. */
    @Test
    fun floodTheBurrowsStorm() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Storm Spark", "Flood the Burrows")

        alice.casts("Storm Spark", target = "bob")
        allPass()
        alice.casts("Flood the Burrows")
        repeat(3) { allPass() }

        expect { alice.battlefield.names.count { it == "Goblin Token" } shouldBe 4 }
    }

    /** Tendrils of Torment with one spell before it: two drains of 2. */
    @Test
    fun tendrilsOfTormentStorm() = scenario {
        alice.battlefield("Mountain", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand("Storm Spark", "Tendrils of Torment")

        alice.casts("Storm Spark", target = "bob")
        allPass()
        alice.casts("Tendrils of Torment", target = "bob")
        allPass()
        while (pending is Decision.May) answer(Answer.YesNo(false))
        repeat(2) { allPass() }

        expect {
            bob.life shouldBe 13
            alice.life shouldBe 24
        }
    }

    /** Mind's Longing: shuffle, exile the top card, play it free this turn; storm makes more. */
    @Test
    fun mindsLongingPlaysTheExiledCardFree() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand("Mind's Longing")
        alice.library("Storm Spark")

        alice.casts("Mind's Longing")
        // The storm trigger, with no spells before it, then the spell
        repeat(2) { allPass() }
        expect { alice.exile has "Storm Spark" }

        perform(castOption(alice, "Storm Spark"))
        answer(Answer.Targets(listOf(target("bob"))))
        expect {
            stack.names shouldBe listOf("Storm Spark")
            (0..5).all { card("Island", it).tapped } shouldBe true
        }
        allPass()
        expect { bob.life shouldBe 17 }
    }

    /** Ashes of Yesterday: instants and sorceries in the graveyard gain flashback for their mana cost this turn. */
    @Test
    fun ashesOfYesterdayGrantsFlashback() = scenario {
        // CR 702.34a
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Ashes of Yesterday")
        alice.graveyard("Storm Spark")

        alice.casts("Ashes of Yesterday", paying = listOf("Mountain", "Mountain#1", "Mountain#2", "Mountain#3"))
        allPass()
        alice.casts("Storm Spark", target = "bob")
        expect { card("Mountain", 4).tapped shouldBe true }
        allPass()

        expect {
            bob.life shouldBe 17
            alice.exile has "Storm Spark"
            // Ashes itself is in the graveyard now, with its own flashback
            alice.graveyard has "Ashes of Yesterday"
        }
    }

    /** Ashes of Yesterday's own flashback. */
    @Test
    fun ashesOfYesterdayFlashback() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard("Ashes of Yesterday", "Storm Spark")

        alice.casts("Ashes of Yesterday", paying = listOf("Mountain", "Mountain#1", "Mountain#2", "Mountain#3", "Mountain#4"))
        allPass()
        alice.casts("Storm Spark", target = "bob")
        allPass()

        expect {
            alice.exile has "Ashes of Yesterday"
            alice.exile has "Storm Spark"
            bob.life shouldBe 17
        }
    }

    /** Necromancer's Will: play lands and cast spells from the graveyard this turn; cards going there are exiled. */
    @Test
    fun necromancersWillPlaysFromTheGraveyard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand("Necromancer's Will")
        alice.graveyard("Mountain", "Storm Spark")

        alice.casts("Necromancer's Will")
        allPass()
        alice.plays("Mountain")
        alice.casts("Storm Spark", target = "bob", paying = listOf("Mountain"))
        allPass()

        expect {
            alice.battlefield has "Mountain"
            bob.life shouldBe 17
            // CR 614.1a: it would go to the graveyard: exiled instead
            alice.exile has "Storm Spark"
        }
    }

    /** Finale of Vows with X=1: cast an instant and a sorcery from the graveyard free; they're exiled afterwards. */
    @Test
    fun finaleOfVowsCastsFromTheGraveyard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Finale of Vows")
        alice.graveyard("Storm Spark", "Cinder Bolt")

        alice.casts("Finale of Vows", x = 1, targets = listOf("Storm Spark", "Cinder Bolt"))
        allPass()
        // Cast both, each at bob
        alice.chooses("Storm Spark", "Cinder Bolt")
        while (pending is Decision.Targets) answer(Answer.Targets(listOf(target("bob"))))
        expect { stack.size shouldBe 2 }
        repeat(2) { allPass() }

        expect {
            bob.life shouldBe 15
            alice.exile has "Storm Spark"
            alice.exile has "Cinder Bolt"
            alice.graveyard.names shouldBe listOf("Finale of Vows")
        }
    }

    /** Finale of Vows with X=10: each spell cast this way is copied twice. */
    @Test
    fun finaleOfVowsWithTenCopiesTwice() = scenario {
        alice.battlefield(*Array(12) { "Mountain" })
        alice.hand("Finale of Vows")
        alice.graveyard("Storm Spark")
        bob.life = 30

        alice.casts("Finale of Vows", x = 10, targets = listOf("Storm Spark"))
        allPass()
        alice.chooses("Storm Spark")
        answer(Answer.Targets(listOf(target("bob"))))
        // The copies keep their target
        while (pending is Decision.May) answer(Answer.YesNo(false))
        expect { stack.names shouldBe listOf("Storm Spark", "copy of Storm Spark", "copy of Storm Spark") }
        repeat(3) { allPass() }

        expect { bob.life shouldBe 21 }
    }

    /** Wildfire Spread with X=2: 2 damage divided between bob and his creature. */
    @Test
    fun wildfireSpreadDividesItsDamage() = scenario {
        // CR 601.2d
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Wildfire Spread")
        bob.battlefield("Elvish Seer")

        perform(Input.Cast(alice.id, card("Wildfire Spread")))
        answer(Answer.Number(2))
        answer(Answer.Targets(listOf(target("bob"), target("Elvish Seer"))))
        assertIs<Decision.Divide>(pending)
        answer(Answer.Division(listOf(Share(target("bob"), 1), Share(target("Elvish Seer"), 1))))
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()

        expect {
            bob.life shouldBe 19
            bob.graveyard has "Elvish Seer"
        }
    }

    /** Wildfire Spread's flashback: {R}{R} and discarding X cards. */
    @Test
    fun wildfireSpreadFlashbackDiscardsX() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Forest", "Island")
        alice.graveyard("Wildfire Spread")

        alice.casts("Wildfire Spread", x = 2, target = "bob", choosing = listOf("Forest", "Island"))
        expect {
            alice.graveyard.names shouldBe listOf("Forest", "Island")
            stack.names shouldBe listOf("Wildfire Spread")
        }
        allPass()

        expect {
            bob.life shouldBe 18
            alice.exile has "Wildfire Spread"
        }
    }
}
