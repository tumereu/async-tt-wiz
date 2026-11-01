package arcanum.engine.view

import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Subtype
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/** Timestamps (CR 613.7) and dependencies (CR 613.8) within a layer, and effects that started applying (CR 613.6). */
class DependencyTest {
    // CR 613.8a, 613.8b: "Each land is a Swamp" depends on "Nonbasic lands are Mountains", which removes the Spire's
    // own ability (CR 305.7): the Eclipse applies first whatever the timestamps, so no land is a Swamp
    @Test
    fun landTypeSettingRemovesTheAbilityThatWouldAddSwamp() {
        listOf(true, false).forEach { spireFirst ->
            scenario {
                if (spireFirst) alice.battlefield("Mirefen Spire", "Crimson Eclipse", "Forest")
                else alice.battlefield("Crimson Eclipse", "Mirefen Spire", "Forest")
                expect {
                    "Forest".subtypes shouldBe listOf(Subtype("Forest"))
                    "Mirefen Spire".subtypes shouldBe listOf(Subtype("Mountain"))
                }
            }
        }
    }

    // CR 613.7: without a dependency, "Each land is a Swamp" adds to a basic land's types
    @Test
    fun independentLandTypeEffectAddsSwamp() = scenario {
        alice.battlefield("Mirefen Spire", "Forest")
        expect {
            "Forest".subtypes shouldBe listOf(Subtype("Forest"), Subtype("Swamp"))
            // CR 305.6: and its mana ability
            "Forest".view.characteristics!!.abilities.map { it.origin } shouldBe
                listOf(AbilityOrigin.LandType(Subtype("Forest")), AbilityOrigin.LandType(Subtype("Swamp")))
        }
    }

    // CR 613.1f, 613.6: all creatures lose all abilities in layer 6, so the older anthem (a layer 7c effect) never
    // starts applying; CR 613.4b: the 1/1 base applies
    @Test
    fun losingAllAbilitiesStopsTheAnthemOnTheLosingCreature() = scenario {
        alice.battlefield("Elvish Hierophant", "Elvish Seer")
        alice.battlefield("Hush of the Meek")
        expect {
            "Elvish Seer".power shouldBe 1
            "Elvish Hierophant".power shouldBe 1
            "Elvish Hierophant".view.characteristics!!.abilities shouldBe emptyList()
        }
    }

    // CR 613.6: an effect that started applying in an earlier layer keeps applying in later layers, even after its
    // ability is removed in between: the Herald's black (layer 5) and +1/+1 (layer 7c) both still apply
    @Test
    fun anEffectThatStartedKeepsApplying() = scenario {
        alice.battlefield("Gloomcrest Herald", "Elvish Seer")
        effect(alice, Mechanic.LoseAbilities(), on = listOf("Gloomcrest Herald"))
        expect {
            "Elvish Seer".colors shouldBe listOf(arcanum.api.Color.Black)
            "Elvish Seer".power shouldBe 2
            "Gloomcrest Herald".view.characteristics!!.abilities shouldBe emptyList()
        }
    }

    // CR 613.8a, 613.6 (contrast): an ability whose only effects are in layers 6 and 7 never starts when "loses all
    // abilities" removes it first: Delirium's +2/+2 and flying are gone
    @Test
    fun conditionalAbilityRemovedBeforeItStarts() = scenario {
        alice.battlefield("Wyrm's Fury Conduit")
        alice.graveyard("Storm Spark", "Elvish Seer", "Forest", "Fused Battleplate")
        effect(alice, Mechanic.LoseAbilities(), on = listOf("Wyrm's Fury Conduit"))
        expect {
            "Wyrm's Fury Conduit".power shouldBe 1
            "Wyrm's Fury Conduit" lacksKeyword Keyword.Flying
        }
    }

    // CR 207.2c, 604.1 (contrast): with four card types in the graveyard the Delirium ability applies
    @Test
    fun deliriumAppliesWithFourCardTypes() = scenario {
        alice.battlefield("Wyrm's Fury Conduit")
        alice.graveyard("Storm Spark", "Elvish Seer", "Forest", "Fused Battleplate")
        expect {
            "Wyrm's Fury Conduit".power shouldBe 3
            "Wyrm's Fury Conduit" hasKeyword Keyword.Flying
        }
    }
}
