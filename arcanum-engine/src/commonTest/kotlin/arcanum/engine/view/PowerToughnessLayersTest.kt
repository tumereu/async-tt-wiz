package arcanum.engine.view

import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.Value
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/** Layer 7 (CR 613.4): characteristic-defining, set, modify (counters too), switch; timestamps within a sublayer. */
class PowerToughnessLayersTest {
    private fun set(power: Int, toughness: Int) = Mechanic.SetPowerToughness(Value.Fixed(power), Value.Fixed(toughness))
    private fun modify(power: Int, toughness: Int) = Mechanic.ModifyPowerToughness(Value.Fixed(power), Value.Fixed(toughness))

    // CR 208.1: printed power and toughness
    @Test
    fun printedPowerAndToughness() = scenario {
        alice.battlefield("Elvish Seer", "Gloomcrest Herald")
        expect {
            "Gloomcrest Herald".power shouldBe 2
            "Gloomcrest Herald".toughness shouldBe 2
        }
    }

    // CR 613.4c: "Other Elf creatures you control get +1/+1" applies to the other Elves of its controller only
    @Test
    fun anthemModifiesOtherElvesYouControl() = scenario {
        alice.battlefield("Elvish Hierophant", "Elvish Seer", "Lineage Druid")
        bob.battlefield("Umbral Elves")
        expect {
            "Elvish Seer".power shouldBe 2
            "Lineage Druid".toughness shouldBe 2
            "Elvish Hierophant".power shouldBe 2
            "Umbral Elves".power shouldBe 1
        }
    }

    // CR 613.4c: anthems add up, each in timestamp order
    @Test
    fun twoAnthemsAddUp() = scenario {
        alice.battlefield("Elvish Hierophant", "Elvish Hierophant", "Elvish Seer")
        expect {
            "Elvish Seer".power shouldBe 3
            card("Elvish Hierophant", 1).power shouldBe 3
        }
    }

    // CR 613.4a, 604.3: a characteristic-defining power and toughness ("equal to the number of artifacts you control")
    @Test
    fun characteristicDefiningPowerAndToughness() = scenario {
        alice.battlefield("Lord of the Aetherforge", "Gemhide Lion")
        expect {
            "Lord of the Aetherforge".power shouldBe 2
            "Lord of the Aetherforge".toughness shouldBe 2
            // "Other artifact creatures you control get +1/+1" (CR 613.4c)
            "Gemhide Lion".power shouldBe 3
        }
    }

    // CR 604.3, 113.6a: a characteristic-defining ability works in every zone
    @Test
    fun characteristicDefiningAbilityWorksInTheHand() = scenario {
        alice.hand("Lord of the Aetherforge")
        alice.battlefield("Gemhide Lion", "Gemhide Lion")
        expect {
            "Lord of the Aetherforge".power shouldBe 2
        }
    }

    // CR 613.4b, 613.4c: setting base power and toughness comes before modifications, whatever the timestamps
    @Test
    fun setAppliesBeforeModifyAndCounters() = scenario {
        alice.battlefield("Elvish Hierophant")
        alice.battlefield("Elvish Seer", counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        effect(alice, set(3, 3), on = listOf("Elvish Seer"))
        expect {
            // 3/3 base, +1/+1 counter, +1/+1 from Elvish Hierophant
            "Elvish Seer".power shouldBe 5
            "Elvish Seer".toughness shouldBe 5
        }
    }

    // CR 613.4c, 122.1a: +1/+1 and -1/-1 counters modify power and toughness in layer 7c
    @Test
    fun countersModifyPowerAndToughness() = scenario {
        alice.battlefield("Elvish Seer", counters = listOf(CounterKind.PowerToughness(1, 1) to 2, CounterKind.PowerToughness(-1, -1) to 1))
        expect {
            "Elvish Seer".power shouldBe 2
            "Elvish Seer".toughness shouldBe 2
        }
    }

    // CR 613.4d: switching comes after every modification, even one with a later timestamp
    @Test
    fun switchAppliesLast() = scenario {
        alice.battlefield("Elvish Seer")
        effect(alice, Mechanic.SwitchPowerToughness, on = listOf("Elvish Seer"))
        effect(alice, modify(2, 0), on = listOf("Elvish Seer"))
        expect {
            // 1/1 +2/+0 = 3/1, switched: 1/3
            "Elvish Seer".power shouldBe 1
            "Elvish Seer".toughness shouldBe 3
        }
    }

    // CR 613.7, 613.7b: within layer 7b the later effect wins
    @Test
    fun laterSetEffectWins() = scenario {
        alice.battlefield("Elvish Seer", "Lineage Druid")
        effect(alice, set(4, 4), on = listOf("Elvish Seer"))
        effect(alice, set(1, 2), on = listOf("Elvish Seer"))
        effect(alice, set(1, 2), on = listOf("Lineage Druid"))
        effect(alice, set(4, 4), on = listOf("Lineage Druid"))
        expect {
            "Elvish Seer".toughness shouldBe 2
            "Lineage Druid".toughness shouldBe 4
        }
    }

    // CR 613.4c: "+1/+1 for each creature card in your graveyard" counts cards in that zone
    @Test
    fun valueCountsCardsInAGraveyard() = scenario {
        alice.battlefield("Wight of the Relic Vault")
        alice.graveyard("Elvish Seer", "Lineage Druid", "Storm Spark")
        bob.graveyard("Umbral Elves")
        expect {
            "Wight of the Relic Vault".power shouldBe 4
        }
    }

    // CR 613.4c, 301.5: "Equipped creature gets +3/+2" applies to the creature the Equipment is attached to
    @Test
    fun equipmentModifiesTheEquippedCreature() = scenario {
        alice.battlefield("Elvish Seer", "Lineage Druid", "Fused Battleplate")
        attach("Fused Battleplate", to = "Elvish Seer")
        expect {
            "Elvish Seer".power shouldBe 4
            "Elvish Seer".toughness shouldBe 3
            "Lineage Druid".power shouldBe 1
        }
    }

    // CR 208.3: a noncreature permanent has no power or toughness; CR 306.5c: a planeswalker's loyalty is its counters
    @Test
    fun noncreaturePermanentHasNoPowerAndLoyaltyIsCounters() = scenario {
        alice.battlefield("Fused Battleplate")
        alice.battlefield("Orla, Gilded Artificer", counters = listOf(CounterKind.Loyalty to 2))
        alice.hand("Orla, Gilded Artificer")
        expect {
            "Fused Battleplate".power shouldBe null
            card("Orla, Gilded Artificer", 0).view.characteristics?.loyalty shouldBe 2
            card("Orla, Gilded Artificer", 1).view.characteristics?.loyalty shouldBe 5
        }
    }
}
