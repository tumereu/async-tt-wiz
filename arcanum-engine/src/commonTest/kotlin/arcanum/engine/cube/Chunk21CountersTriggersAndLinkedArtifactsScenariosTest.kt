package arcanum.engine.cube

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Chunk 21 of the cube in play: artifacts with counters, triggers and linked abilities. */
class Chunk21CountersTriggersAndLinkedArtifactsScenariosTest {
    private val charge = CounterKind.Named("charge")

    /** What [player] may play now with a permission (from exile, from the graveyard). */
    private fun Scenario.playable(player: ScenarioPlayer): List<Input> =
        Engine.options(state, player.id).filter { (it is Input.Cast && state.obj(it.card)?.zone != arcanum.api.Zone.Hand) || it is Input.PlayLand }

    /** Ether Flask: a charge counter at your upkeep. */
    @Test
    fun etherFlaskChargesAtUpkeep() = scenario {
        alice.battlefield("Ether Flask")
        beginStep(TurnStep.Upkeep, active = alice)
        resolve()
        answer(Answer.YesNo(true))
        expect { "Ether Flask".counters(charge) shouldBe 1 }
    }

    /** Ether Flask: {T}: put a creature card with mana value equal to its charge counters onto the battlefield. */
    @Test
    fun etherFlaskPutsACreatureOntoTheBattlefield() = scenario {
        alice.battlefield("Ether Flask", counters = listOf(CounterKind.Named("charge") to 1))
        alice.hand("Elvish Seer", "Elvish Hierophant")

        alice.activates("Ether Flask")
        allPass()
        // "You may put": one or none of the cards with mana value 1
        assertEquals(listOf(card("Elvish Seer")), decision<Decision.ChooseObjects>().options)
        alice.chooses("Elvish Seer")

        expect {
            alice.battlefield has "Elvish Seer"
            alice.hand.names shouldBe listOf("Elvish Hierophant")
        }
    }

    /** Clockwork Charge: tap to add a charge counter; sacrifice to destroy every nonland permanent with that mana value. */
    @Test
    fun clockworkChargeCountsUpThenDestroys() = scenario {
        alice.battlefield("Clockwork Charge", "Forest")
        bob.battlefield("Elvish Seer", "Thicketwood Elves", "Elvish Hierophant", "Lotus Frond", "Forest")

        alice.activates("Clockwork Charge", ability = 0)
        allPass()
        expect { "Clockwork Charge".counters(charge) shouldBe 1 }
    }

    @Test
    fun clockworkChargeDestroysByManaValue() = scenario {
        alice.battlefield("Clockwork Charge", counters = listOf(CounterKind.Named("charge") to 1))
        bob.battlefield("Elvish Seer", "Thicketwood Elves", "Elvish Hierophant", "Lotus Frond", "Forest")

        alice.activates("Clockwork Charge", ability = 1)
        allPass()
        expect {
            bob.graveyard.names.toSet() shouldBe setOf("Elvish Seer", "Thicketwood Elves")
            bob.battlefield has "Elvish Hierophant"
            bob.battlefield has "Lotus Frond"
            alice.graveyard has "Clockwork Charge"
        }
    }

    /** Tuned Detonators: sunburst counts the colours spent; its sacrifice destroys by that mana value. */
    @Test
    fun tunedDetonatorsSunburst() = scenario {
        // CR 702.44a
        alice.battlefield("Forest", "Mountain", "Island", "Island")
        alice.hand("Tuned Detonators")
        bob.battlefield("Zealous Druid", "Elvish Seer")

        alice.casts("Tuned Detonators", x = 2, paying = listOf("Forest", "Mountain"))
        allPass()
        expect { "Tuned Detonators".counters(charge) shouldBe 2 }

        alice.activates("Tuned Detonators", paying = listOf("Island", "Island#1"))
        allPass()
        expect {
            bob.graveyard.names shouldBe listOf("Zealous Druid")
            bob.battlefield has "Elvish Seer"
        }
    }

    /** Brood Vat: sacrifice creatures for charge counters; {1} and two counters: a 2/2 Spawn. */
    @Test
    fun broodVatMakesASpawn() = scenario {
        alice.battlefield("Brood Vat", "Elvish Seer", "Thicketwood Elves", "Forest")

        alice.activates("Brood Vat", ability = 0, choosing = listOf("Elvish Seer"))
        allPass()
        alice.activates("Brood Vat", ability = 0, choosing = listOf("Thicketwood Elves"))
        allPass()
        expect { "Brood Vat".counters(charge) shouldBe 2 }

        alice.activates("Brood Vat", ability = 1)
        allPass()
        expect {
            "Brood Vat".counters(charge) shouldBe 0
            alice.battlefield has "Spawn Token"
            "Spawn Token".power shouldBe 2
        }
    }

    /** Rod of the Fabulist: a Spirit as it enters; creating tokens adds a story counter; spend one to draw. */
    @Test
    fun rodOfTheFabulist() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand("Rod of the Fabulist")
        alice.library("Island")

        alice.casts("Rod of the Fabulist", paying = listOf("Plains", "Plains#1"))
        allPass()
        allPass()
        expect { alice.battlefield has "Spirit Token" }
        allPass()
        expect { "Rod of the Fabulist".counters(CounterKind.Named("story")) shouldBe 1 }

        alice.activates("Rod of the Fabulist")
        allPass()
        expect {
            alice.hand.names shouldBe listOf("Island")
            "Rod of the Fabulist".counters(CounterKind.Named("story")) shouldBe 0
        }
    }

    /** Wishfang Amulet: enters with three wish counters; a tutor that gives it to an opponent. */
    @Test
    fun wishfangAmuletTutorsAndChangesHands() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand("Wishfang Amulet")
        alice.library("Forest", "Storm Spark")

        alice.casts("Wishfang Amulet", paying = listOf("Swamp", "Swamp#1"))
        allPass()
        expect { "Wishfang Amulet".counters(CounterKind.Named("wish")) shouldBe 3 }

        alice.activates("Wishfang Amulet", paying = listOf("Swamp#2"))
        allPass()
        alice.chooses("Storm Spark")

        expect {
            alice.hand.names shouldBe listOf("Storm Spark")
            "Wishfang Amulet".counters(CounterKind.Named("wish")) shouldBe 2
            "Wishfang Amulet".controller shouldBe bob.id
        }
    }

    /** Refitting Workshop: Servo, then a Thopter for the Servo, then a Construct for the Thopter, untapping for {3}. */
    @Test
    fun refittingWorkshopChain() = scenario {
        alice.battlefield("Refitting Workshop", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")

        alice.activates("Refitting Workshop", ability = 1)
        allPass()
        expect { alice.battlefield has "Servo Token" }

        alice.activates("Refitting Workshop", ability = 0)
        allPass()
        alice.activates("Refitting Workshop", ability = 2, choosing = listOf("Servo Token"))
        allPass()
        expect {
            alice.battlefield lacks "Servo Token"
            "Thopter Token" hasKeyword Keyword.Flying
        }

        alice.activates("Refitting Workshop", ability = 0)
        allPass()
        alice.activates("Refitting Workshop", ability = 3, choosing = listOf("Thopter Token"))
        allPass()
        expect {
            alice.battlefield has "Construct Token"
            "Construct Token".power shouldBe 4
        }
    }

    /** Prototype Replicator: entering exiles the top card to play this turn; sacrificed (sorcery speed) it makes a
     * Samurai and exiles another as it leaves. */
    @Test
    fun prototypeReplicator() = scenario {
        // A fifth Mountain to pay for the card it exiles (options list a cast only when its mana can be found)
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Prototype Replicator")
        alice.library("Forest", "Storm Spark")

        alice.casts("Prototype Replicator", paying = listOf("Mountain"))
        repeat(2) { allPass() }
        expect { alice.exile has "Forest" }
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }

        alice.activates("Prototype Replicator", paying = listOf("Mountain#1", "Mountain#2", "Mountain#3"))
        expect { alice.graveyard has "Prototype Replicator" }
        // Its leaves trigger goes on the Stack above the ability
        repeat(2) { allPass() }
        expect {
            alice.exile has "Storm Spark"
            alice.battlefield has "Samurai Token"
            "Samurai Token" hasKeyword Keyword.Vigilance
        }
        assertTrue(playable(alice).any { it is Input.Cast && it.card == card("Storm Spark") })
    }

    /** Origin Vault: while untapped, each nontoken creature entering gives its controller a Myr. */
    @Test
    fun originVaultMakesMyr() = scenario {
        alice.battlefield("Origin Vault", "Forest")
        alice.hand("Elvish Seer")

        alice.casts("Elvish Seer")
        allPass()
        expect { stack.names shouldBe listOf("ability of Origin Vault") }
        allPass()
        expect { alice.battlefield has "Myr Token" }
    }

    /** Origin Vault tapped: no Myr (CR 603.4). */
    @Test
    fun originVaultTappedDoesNothing() = scenario {
        alice.battlefield("Origin Vault", tapped = true)
        alice.battlefield("Forest")
        alice.hand("Elvish Seer")
        alice.casts("Elvish Seer")
        allPass()
        expect { stack.isEmpty() }
    }

    /** Demon-Cult Forge: sacrifice an artifact to drain 1; artifacts leaving on your turn make a Construct, once a turn. */
    @Test
    fun demonCultForge() = scenario {
        alice.battlefield("Demon-Cult Forge", "Lotus Frond", "Verdant Bauble")

        alice.activates("Demon-Cult Forge", ability = 0, choosing = listOf("Lotus Frond"))
        expect { stack.names shouldBe listOf("ability of Demon-Cult Forge", "ability of Demon-Cult Forge") }
        repeat(2) { allPass() }
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
            alice.battlefield has "Construct Token"
        }
        // Once each turn: another artifact leaving makes no second Construct
        alice.activates("Verdant Bauble")
        expect { stack.isEmpty() }
    }

    /** Stormflux Cistern: each spell gains life for each spell cast this turn; 50 life for 50 damage. */
    @Test
    fun stormfluxCisternGainsLife() = scenario {
        alice.battlefield("Stormflux Cistern", "Mountain", "Mountain")
        alice.hand("Storm Spark", "Cinder Bolt")

        alice.casts("Storm Spark", target = "bob")
        repeat(2) { allPass() }
        alice.casts("Cinder Bolt", target = "bob")
        repeat(2) { allPass() }

        expect { alice.life shouldBe 23 }
    }

    @Test
    fun stormfluxCisternPaysFiftyLife() = scenario {
        alice.life = 60
        alice.battlefield("Stormflux Cistern")
        alice.activates("Stormflux Cistern", target = "bob")
        allPass()
        expect {
            alice.life shouldBe 10
            bob.life shouldBe -30
        }
    }

    /** Bile Fountain: draw as it enters and as it goes to the graveyard. */
    @Test
    fun bileFountainDrawsTwice() = scenario {
        alice.battlefield("Forest", "Forest", "Mountain")
        alice.hand("Bile Fountain", "Storm Spark")
        alice.library("Island", "Swamp")
        bob.hand("Old Enmity")
        bob.battlefield("Mountain", "Mountain")

        alice.casts("Bile Fountain", paying = listOf("Forest", "Forest#1"))
        repeat(2) { allPass() }
        expect { alice.hand has "Island" }

        alice.passes()
        bob.casts("Old Enmity", target = "Bile Fountain", paying = listOf("Mountain#1", "Mountain#2"))
        allPass()
        allPass()
        expect { alice.hand has "Swamp" }
    }

    /** Memorial to Perseverance: each discard picks a mode not picked this turn. */
    @Test
    fun memorialToPerseverance() = scenario {
        alice.battlefield("Memorial to Perseverance", "Swamp", "Swamp")
        alice.hand("Crow's Larceny", "Forest", "Island", "Plains")
        alice.library("Mountain")

        alice.casts("Crow's Larceny", target = "alice", paying = listOf("Swamp"))
        allPass()
        alice.chooses("Forest")
        // CR 603.3c: the mode is chosen as it goes on the Stack: each opponent loses 3
        answer(Answer.Modes(listOf(2)))
        expect { stack.names shouldBe listOf("ability of Memorial to Perseverance") }
        allPass()
        expect { bob.life shouldBe 17 }

        // Retrace discards the Island: the losing mode isn't offered again this turn; draw
        alice.casts("Crow's Larceny", target = "alice", choosing = listOf("Island"), paying = listOf("Swamp#1"))
        assertEquals(listOf(true, true, false), decision<Decision.Modes>().options.map { it.available })
        answer(Answer.Modes(listOf(0)))
        allPass()
        expect { alice.hand.names shouldBe listOf("Plains", "Mountain") }
        // The spell makes alice discard again: only the Treasure is left
        allPass()
        alice.chooses("Plains")
        assertEquals(listOf(false, true, false), decision<Decision.Modes>().options.map { it.available })
        answer(Answer.Modes(listOf(1)))
        allPass()
        expect {
            alice.battlefield has "Treasure Token"
            alice.hand.names shouldBe listOf("Mountain")
        }
    }

    /** Pocket Rift: exiles a cheap nonland permanent until it leaves the battlefield. */
    @Test
    fun pocketRiftExilesUntilItLeaves() = scenario {
        // CR 610.3
        alice.battlefield("Plains", "Mountain", "Mountain")
        alice.hand("Pocket Rift")
        bob.battlefield("Elvish Seer", "Elvish Hierophant")
        bob.hand("Old Enmity")
        bob.battlefield("Mountain", "Mountain")

        alice.casts("Pocket Rift")
        allPass()
        // Only the Seer has mana value 2 or less: the enters trigger targets it
        expect { stack.names shouldBe listOf("ability of Pocket Rift") }
        assertEquals(listOf(target("Elvish Seer")), state.stackItems.values.single().targets.values.flatten())
        allPass()
        expect { bob.exile has "Elvish Seer" }

        alice.passes()
        bob.casts("Old Enmity", target = "Pocket Rift", paying = listOf("Mountain#2", "Mountain#3"))
        allPass()
        allPass()
        expect { bob.battlefield has "Elvish Seer" }
    }

    /** Cauldron of Realms: lands can be played from your graveyard. */
    @Test
    fun cauldronOfRealmsPlaysLandsFromTheGraveyard() = scenario {
        alice.battlefield("Cauldron of Realms")
        alice.graveyard("Forest")
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }

    /** Morwen's Spirit Kettle: exile a creature card, counter a creature, which then has the exiled card's abilities. */
    @Test
    fun morwensSpiritKettle() = scenario {
        alice.battlefield("Morwen's Spirit Kettle", "Thicketwood Elves", "Forest")
        alice.graveyard("Island", "Swamp")
        bob.graveyard("Bleak Magmacaller")

        alice.activates("Morwen's Spirit Kettle", target = "Bleak Magmacaller")
        allPass()
        // The reflexive trigger targets a creature alice controls
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target("Thicketwood Elves"))))
        allPass()
        expect {
            bob.exile has "Bleak Magmacaller"
            "Thicketwood Elves".counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
        // The Elves have the exiled card's activated ability
        val abilities = view()[card("Thicketwood Elves")]!!.characteristics!!.abilities
        assertEquals(2, abilities.count { it.ability is arcanum.api.Ability.Activated })

        // {R}, {T}, exile two cards from your graveyard: 2 damage; the Forest's green mana spends as red here
        alice.activates("Thicketwood Elves", ability = 1, target = "bob", choosing = listOf("Island", "Swamp"), paying = listOf("Forest"))
        allPass()
        expect {
            bob.life shouldBe 18
            alice.exile.names shouldBe listOf("Island", "Swamp")
        }
    }

    /** Coin Exchanger: discarded cards may be exiled with it; it returns them for a Treasure or a Rogue. */
    @Test
    fun coinExchanger() = scenario {
        alice.battlefield("Coin Exchanger", "Forest", "Forest")
        alice.hand("Storm Spark")
        alice.library("Island")

        alice.activates("Coin Exchanger", ability = 0)
        allPass()
        alice.chooses("Storm Spark")
        allPass()
        answer(Answer.YesNo(true))
        expect { alice.exile has "Storm Spark" }
    }

    @Test
    fun coinExchangerReturnsForARogue() = scenario {
        alice.battlefield("Coin Exchanger", "Forest", "Forest")
        alice.hand("Storm Spark", "Forest")
        alice.library("Island", "Swamp")

        alice.activates("Coin Exchanger", ability = 0)
        allPass()
        alice.chooses("Storm Spark")
        allPass()
        answer(Answer.YesNo(true))
        // The next turn, untapped: put it back for a Rogue
        change(arcanum.engine.change.Changes.tap(state, card("Coin Exchanger"), tapped = false))
        alice.activates("Coin Exchanger", ability = 1)
        allPass()
        expect {
            alice.graveyard has "Storm Spark"
            alice.battlefield has "Rogue Token"
            "Rogue Token".power shouldBe 2
        }
    }
}
