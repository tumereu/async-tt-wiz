package arcanum.engine.cube

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

/** Chunk 20 of the cube in play: Equipment and artifacts that sacrifice. */
class Chunk20EquipmentAndSacrificeArtifactsScenariosTest {
    private fun Scenario.attachedTo(name: String) = state.objOrThrow(card(name)).attachedTo

    /** Skull Bracer: +1/+0 for each artifact you control; equips for {1} or moves for {B}{B}. */
    @Test
    fun skullBracerEquipsAndMoves() = scenario {
        // CR 702.6a, 301.5
        alice.battlefield("Skull Bracer", "Lotus Frond", "Elvish Seer", "Thicketwood Elves", "Mountain", "Swamp", "Swamp")

        alice.activates("Skull Bracer", ability = 1, target = "Elvish Seer", paying = listOf("Mountain"))
        allPass()
        expect { "Elvish Seer".power shouldBe 3 }

        alice.activates("Skull Bracer", ability = 0, target = "Thicketwood Elves", paying = listOf("Swamp", "Swamp#1"))
        allPass()
        expect {
            "Elvish Seer".power shouldBe 1
            "Thicketwood Elves".power shouldBe 3
            "Thicketwood Elves".toughness shouldBe 1
        }
    }

    /** Fused Battleplate: +3/+2; the creature it leaves is sacrificed. */
    @Test
    fun fusedBattleplateSacrificesWhatItLeaves() = scenario {
        alice.battlefield("Fused Battleplate", "Elvish Seer", "Thicketwood Elves")

        alice.activates("Fused Battleplate", target = "Elvish Seer")
        allPass()
        expect {
            "Elvish Seer".power shouldBe 4
            "Elvish Seer".toughness shouldBe 3
        }

        alice.activates("Fused Battleplate", target = "Thicketwood Elves")
        allPass()
        expect { stack.names shouldBe listOf("ability of Fused Battleplate") }
        allPass()
        expect {
            alice.graveyard has "Elvish Seer"
            "Thicketwood Elves".power shouldBe 4
        }
    }

    /** Thornpod Husk: living weapon makes a Germ and equips it; +1/+1 for each artifact and enchantment. */
    @Test
    fun thornpodHuskLivingWeapon() = scenario {
        // CR 702.92a
        alice.battlefield("Forest", "Forest", "Forest", "Frontier Wandering", "Elvish Seer")
        alice.hand("Thornpod Husk")

        alice.casts("Thornpod Husk")
        allPass()
        expect { stack.names shouldBe listOf("ability of Thornpod Husk") }
        allPass()
        expect {
            alice.battlefield has "Phyrexian Germ Token"
            // The Husk and Frontier Wandering
            "Phyrexian Germ Token".power shouldBe 2
            "Phyrexian Germ Token".toughness shouldBe 2
        }
        assertEquals(arcanum.engine.state.TargetRef.Object(card("Phyrexian Germ Token")), attachedTo("Thornpod Husk"))
    }

    /** Thornpod Husk's equip moves it to another creature; the Germ dies as a 0/0. */
    @Test
    fun thornpodHuskEquipsAnotherCreature() = scenario {
        alice.battlefield("Thornpod Husk", "Elvish Seer", "Forest", "Forest")
        alice.activates("Thornpod Husk", target = "Elvish Seer")
        allPass()
        expect {
            "Elvish Seer".power shouldBe 2
            "Elvish Seer".toughness shouldBe 2
        }
    }

    /** Bonevise: +1/-1; a 1/1 wearing it dies, and its controller draws two. */
    @Test
    fun boneviseDrawsTwoWhenTheCreatureDies() = scenario {
        alice.battlefield("Bonevise", "Elvish Seer", "Forest")
        alice.library("Island", "Swamp")

        alice.activates("Bonevise", target = "Elvish Seer")
        allPass()
        // CR 704.5f: 2/0, it dies; the trigger draws two
        expect { alice.graveyard has "Elvish Seer" }
        allPass()
        expect { alice.hand.names shouldBe listOf("Island", "Swamp") }
    }

    /** Blade of the Humble: +1/+2; when a 1/1 enters, it may come back from the graveyard attached to it. */
    @Test
    fun bladeOfTheHumbleReturnsOnAOneOne() = scenario {
        alice.battlefield("Forest")
        alice.hand("Elvish Seer")
        alice.graveyard("Blade of the Humble")

        alice.casts("Elvish Seer")
        allPass()
        expect { stack.names shouldBe listOf("ability of Blade of the Humble") }
        allPass()
        answer(Answer.YesNo(true))

        expect {
            alice.battlefield has "Blade of the Humble"
            "Elvish Seer".power shouldBe 2
            "Elvish Seer".toughness shouldBe 3
        }
    }

    /** Blade of the Humble's equip {2}. */
    @Test
    fun bladeOfTheHumbleEquip() = scenario {
        alice.battlefield("Blade of the Humble", "Thicketwood Elves", "Forest", "Forest")
        alice.activates("Blade of the Humble", target = "Thicketwood Elves")
        allPass()
        expect { "Thicketwood Elves".toughness shouldBe 3 }
    }

    /** Runner's Skiff: crewed by a 1-power creature, it attacks and loots. */
    @Test
    fun runnersSkiffCrewsAttacksAndLoots() = scenario {
        // CR 702.122a
        alice.battlefield("Runner's Skiff", "Elvish Seer")
        alice.library("Island")
        alice.hand("Forest")

        alice.activates("Runner's Skiff", choosing = listOf("Elvish Seer"))
        allPass()
        expect {
            "Elvish Seer".tapped shouldBe true
            "Runner's Skiff".types.contains(arcanum.api.CardType.Creature) shouldBe true
            "Runner's Skiff" hasKeyword Keyword.Flying
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Runner's Skiff")
        allPass()
        answer(Answer.YesNo(true))
        alice.chooses("Forest")

        expect {
            alice.hand.names shouldBe listOf("Island")
            alice.graveyard has "Forest"
        }
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 17 }
    }

    /** Tidal Spellcharge: {U} and sacrifice it to bounce a creature. */
    @Test
    fun tidalSpellchargeBounces() = scenario {
        alice.battlefield("Tidal Spellcharge", "Island")
        bob.battlefield("Elvish Seer")

        alice.activates("Tidal Spellcharge", ability = 0, target = "Elvish Seer")
        expect { alice.graveyard has "Tidal Spellcharge" }
        allPass()
        expect { bob.hand has "Elvish Seer" }
    }

    /** Tidal Spellcharge: {1} and sacrifice it to draw. */
    @Test
    fun tidalSpellchargeDraws() = scenario {
        alice.battlefield("Tidal Spellcharge", "Forest")
        alice.library("Island")
        alice.activates("Tidal Spellcharge", ability = 1)
        allPass()
        expect { alice.hand.names shouldBe listOf("Island") }
    }

    /** Cinder Spellcharge: {R} and sacrifice it: 2 damage; or {1} and sacrifice it: draw. */
    @Test
    fun cinderSpellchargeDamages() = scenario {
        alice.battlefield("Cinder Spellcharge", "Mountain")
        alice.activates("Cinder Spellcharge", ability = 0, target = "bob")
        allPass()
        expect {
            bob.life shouldBe 18
            alice.graveyard has "Cinder Spellcharge"
        }
    }

    @Test
    fun cinderSpellchargeDraws() = scenario {
        alice.battlefield("Cinder Spellcharge", "Forest")
        alice.library("Island")
        alice.activates("Cinder Spellcharge", ability = 1)
        allPass()
        expect { alice.hand.names shouldBe listOf("Island") }
    }

    /** Mender's Trinket: put a graveyard card on the bottom of the library, then draw. */
    @Test
    fun mendersTrinketRecyclesAndDraws() = scenario {
        alice.battlefield("Mender's Trinket")
        alice.graveyard("Storm Spark")
        alice.library("Island", "Forest")

        alice.activates("Mender's Trinket", target = "Storm Spark")
        allPass()

        expect {
            alice.hand.names shouldBe listOf("Island")
            alice.library.names shouldBe listOf("Forest", "Storm Spark")
            alice.graveyard.names shouldBe listOf("Mender's Trinket")
        }
    }

    /** Seer's Trinket: look at the top of bob's library; draw at the beginning of the next turn's upkeep. */
    @Test
    fun seersTrinketDrawsNextUpkeep() = scenario {
        alice.battlefield("Seer's Trinket")
        alice.library("Island", "Forest")
        bob.library("Swamp", hidden = true)

        alice.activates("Seer's Trinket", target = "bob")
        allPass()
        expect { alice.hand.size shouldBe 0 }

        // CR 603.7: the delayed trigger at the next upkeep (bob's)
        beginStep(TurnStep.Upkeep, active = bob)
        resolveStack()
        expect { alice.hand.names shouldBe listOf("Island") }
    }

    /** Spy's Trinket: look at a random card of bob's hand; draw at the next upkeep. */
    @Test
    fun spysTrinketDrawsNextUpkeep() = scenario {
        alice.battlefield("Spy's Trinket")
        alice.library("Island")
        bob.hiddenHand("Storm Spark", "Forest")

        alice.activates("Spy's Trinket", target = "bob")
        allPass()
        beginStep(TurnStep.Upkeep, active = bob)
        resolveStack()
        expect { alice.hand.names shouldBe listOf("Island") }
    }

    /** Oracle's Spinning Top: {1} rearranges the top three; {T} draws and puts the Top on the library. */
    @Test
    fun oraclesSpinningTop() = scenario {
        alice.battlefield("Oracle's Spinning Top", "Forest")
        alice.library("Island", "Swamp", "Mountain", "Plains")

        alice.activates("Oracle's Spinning Top", ability = 0)
        allPass()
        alice.chooses("Mountain", "Island", "Swamp")
        expect { alice.library.names shouldBe listOf("Mountain", "Island", "Swamp", "Plains") }

        alice.activates("Oracle's Spinning Top", ability = 1)
        allPass()
        expect {
            alice.hand.names shouldBe listOf("Mountain")
            alice.library.names shouldBe listOf("Oracle's Spinning Top", "Island", "Swamp", "Plains")
        }
    }

    /** Shrine of Oblivion: sacrifice a creature: bob mills cards equal to its power. */
    @Test
    fun shrineOfOblivionMillsByPower() = scenario {
        alice.battlefield("Shrine of Oblivion", "Elvish Hierophant", "Elvish Seer")
        bob.library("Forest", "Island", "Swamp", "Mountain")

        // Elvish Seer is 2/2 with the Hierophant's anthem: its last known power counts (CR 608.2h)
        alice.activates("Shrine of Oblivion", target = "bob", choosing = listOf("Elvish Seer"))
        allPass()

        expect {
            bob.graveyard.names shouldBe listOf("Forest", "Island")
            alice.graveyard has "Elvish Seer"
        }
    }

    /** Detonation Rig: tap and sacrifice a creature for 1 damage; untaps when a creature enters. */
    @Test
    fun detonationRigPingsAndUntaps() = scenario {
        alice.battlefield("Detonation Rig", "Elvish Seer", "Forest")
        alice.hand("Thicketwood Elves")

        alice.activates("Detonation Rig", target = "bob", choosing = listOf("Elvish Seer"))
        allPass()
        expect {
            bob.life shouldBe 19
            "Detonation Rig".tapped shouldBe true
        }

        alice.casts("Thicketwood Elves")
        allPass()
        allPass()
        answer(Answer.YesNo(true))
        expect { "Detonation Rig".tapped shouldBe false }
    }

    /** Lifeseed Orb: sacrifice a land: gain 2 life. */
    @Test
    fun lifeseedOrbGainsLife() = scenario {
        alice.battlefield("Lifeseed Orb", "Forest")
        alice.activates("Lifeseed Orb", choosing = listOf("Forest"))
        allPass()
        expect {
            alice.life shouldBe 22
            alice.graveyard has "Forest"
        }
    }

    /** Hag's Kiln: a Food for a creature, two for a creature with toughness 4 or more; a Food gains 3 life. */
    @Test
    fun hagsKilnMakesFood() = scenario {
        alice.battlefield("Hag's Kiln", "Wall of Portents", "Forest", "Forest")

        alice.activates("Hag's Kiln", choosing = listOf("Wall of Portents"))
        allPass()
        expect { alice.battlefield.names.count { it == "Food Token" } shouldBe 2 }

        alice.activates("Food Token")
        allPass()
        expect {
            alice.life shouldBe 23
            alice.battlefield.names.count { it == "Food Token" } shouldBe 1
        }
    }

    @Test
    fun hagsKilnOneFoodForASmallCreature() = scenario {
        alice.battlefield("Hag's Kiln", "Elvish Seer")
        alice.activates("Hag's Kiln", choosing = listOf("Elvish Seer"))
        allPass()
        expect { alice.battlefield.names.count { it == "Food Token" } shouldBe 1 }
    }

    /** Wingwright Forge: {1}, sacrifice a nontoken artifact: a 1/1 flying Thopter and 1 life. */
    @Test
    fun wingwrightForgeMakesAThopter() = scenario {
        alice.battlefield("Wingwright Forge", "Lotus Frond", "Forest")
        alice.activates("Wingwright Forge", choosing = listOf("Lotus Frond"), paying = listOf("Forest"))
        allPass()
        expect {
            alice.graveyard has "Lotus Frond"
            alice.battlefield has "Thopter Token"
            "Thopter Token" hasKeyword Keyword.Flying
            alice.life shouldBe 21
        }
    }

    /** The Gravecook's Ledger: discard for a Food; {4}, sacrifice it: a creature card back to hand. */
    @Test
    fun gravecooksLedger() = scenario {
        alice.battlefield("The Gravecook's Ledger", "Forest", "Forest", "Forest", "Forest")
        alice.hand("Island")
        alice.graveyard("Elvish Seer")

        alice.activates("The Gravecook's Ledger", ability = 0, choosing = listOf("Island"))
        allPass()
        expect {
            alice.graveyard has "Island"
            alice.battlefield has "Food Token"
        }
    }

    @Test
    fun gravecooksLedgerReturnsACreatureCard() = scenario {
        alice.battlefield("The Gravecook's Ledger", "Forest", "Forest", "Forest", "Forest")
        alice.graveyard("Elvish Seer")
        alice.activates("The Gravecook's Ledger", ability = 1, target = "Elvish Seer")
        allPass()
        expect {
            alice.hand has "Elvish Seer"
            alice.graveyard has "The Gravecook's Ledger"
        }
    }
}
