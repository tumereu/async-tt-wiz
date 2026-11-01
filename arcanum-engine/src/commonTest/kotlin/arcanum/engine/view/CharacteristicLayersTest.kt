package arcanum.engine.view

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.CopyException
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertTrue

/** Layers 1–6 (CR 613.1a–f): copy, control, type, colour and abilities. */
class CharacteristicLayersTest {
    // CR 613.1b: control-changing effects apply in layer 2; "creatures you control" then follows the new controller
    @Test
    fun gainedControlMovesTheCreatureOutOfItsOwnersAnthem() = scenario {
        alice.battlefield("Elvish Hierophant", "Elvish Seer")
        effect(bob, Mechanic.ControlledBy(Players.You), on = listOf("Elvish Seer"))
        expect {
            "Elvish Seer".controller shouldBe bob.id
            "Elvish Seer".power shouldBe 1
        }
    }

    // CR 702.73a, 613.3: changeling is a characteristic-defining ability: every creature type, so "Other Elf creatures"
    // includes it
    @Test
    fun changelingIsEveryCreatureType() = scenario {
        alice.battlefield("Elvish Hierophant", "Veiled Wrecker")
        expect {
            "Veiled Wrecker".power shouldBe 2
            assertTrue("Veiled Wrecker".view.characteristics!!.hasSubtype(Subtype("Elf")))
            assertTrue(!"Veiled Wrecker".view.characteristics!!.hasSubtype(Subtype("Forest")))
        }
    }

    // CR 613.1d, 205.1b: becoming an artifact in addition to its other types; it then counts as an artifact you control
    @Test
    fun addedCardTypeCounts() = scenario {
        alice.battlefield("Lord of the Aetherforge", "Elvish Seer")
        effect(alice, Mechanic.SetTypes(add = listOf(CardType.Artifact)), on = listOf("Elvish Seer"))
        expect {
            "Elvish Seer".types shouldBe listOf(CardType.Creature, CardType.Artifact)
            "Lord of the Aetherforge".power shouldBe 2
            // "Other artifact creatures you control get +1/+1"
            "Elvish Seer".power shouldBe 2
        }
    }

    // CR 205.1a: new creature types replace only the creature types
    @Test
    fun replacingCreatureTypesKeepsOtherSubtypes() = scenario {
        alice.battlefield("Leopard Sash")
        effect(alice, Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Dinosaur"))), replaceSubtypes = true), on = listOf("Leopard Sash"))
        expect {
            "Leopard Sash".subtypes shouldBe listOf(Subtype("Equipment"), Subtype("Dinosaur"))
        }
    }

    // CR 205.3d: an object can't have a subtype that doesn't correspond to one of its types
    @Test
    fun losingTheCreatureTypeDropsCreatureTypes() = scenario {
        alice.battlefield("Gemhide Lion")
        effect(alice, Mechanic.SetTypes(remove = listOf(CardType.Creature)), on = listOf("Gemhide Lion"))
        expect {
            "Gemhide Lion".types shouldBe listOf(CardType.Artifact)
            "Gemhide Lion".subtypes shouldBe emptyList()
            "Gemhide Lion".power shouldBe null
        }
    }

    // CR 305.6: a land with a basic land type has its mana ability
    @Test
    fun basicLandTypesGiveManaAbilities() = scenario {
        alice.battlefield("Forest", "Ashen Wastes")
        expect {
            "Forest".view.characteristics!!.abilities.map { it.origin } shouldBe listOf(AbilityOrigin.LandType(Subtype("Forest")))
            "Ashen Wastes".view.characteristics!!.abilities.map { it.origin } shouldBe
                listOf(AbilityOrigin.LandType(Subtype("Swamp")), AbilityOrigin.LandType(Subtype("Mountain")))
        }
    }

    // CR 305.7: a nonbasic land whose subtype is set to Mountain loses its old land types and its rules-text abilities
    // and gets only the Mountain's mana ability; basic lands are untouched
    @Test
    fun settingLandTypesReplacesRulesTextAbilities() = scenario {
        alice.battlefield("Crimson Eclipse", "Forest", "Hallowed Mire Triad")
        expect {
            "Hallowed Mire Triad".subtypes shouldBe listOf(Subtype("Mountain"))
            "Hallowed Mire Triad".view.characteristics!!.abilities.map { it.origin } shouldBe
                listOf(AbilityOrigin.LandType(Subtype("Mountain")))
            "Forest".subtypes shouldBe listOf(Subtype("Forest"))
        }
    }

    // CR 613.1e: colour-changing effects; "in addition to its other colours" adds
    @Test
    fun colourEffectsSetOrAdd() = scenario {
        alice.battlefield("Elvish Seer", "Lineage Druid")
        effect(alice, Mechanic.SetColors(listOf(ColorRef.Literal(Color.Blue))), on = listOf("Elvish Seer"))
        effect(alice, Mechanic.SetColors(listOf(ColorRef.Literal(Color.White)), add = true), on = listOf("Lineage Druid"))
        expect {
            "Elvish Seer".colors shouldBe listOf(Color.Blue)
            "Lineage Druid".colors shouldBe listOf(Color.White, Color.Green)
        }
    }

    // CR 202.2: colours come from the mana cost, hybrid symbols counting for both
    @Test
    fun coloursFromTheManaCost() = scenario {
        alice.hand("Fiend Sculptor", "Gemhide Lion")
        expect {
            "Fiend Sculptor".colors shouldBe listOf(Color.Black, Color.Green)
            "Gemhide Lion".colors shouldBe emptyList()
        }
    }

    // CR 613.1f, 301.5: "Enchanted creature … has flying and haste" grants keyword abilities in layer 6, and they expand
    // into their rules (D38)
    @Test
    fun grantedKeywordsExpand() = scenario {
        alice.battlefield("Elvish Seer", "Inspector's Firebird")
        attach("Inspector's Firebird", to = "Elvish Seer")
        expect {
            "Elvish Seer" hasKeyword Keyword.Flying
            "Elvish Seer" hasKeyword Keyword.Haste
            "Elvish Seer".power shouldBe 3
            // CR 702.9b
            "Elvish Seer" hasMechanic { it is Mechanic.CanBeBlockedOnlyBy }
            // CR 702.10b
            "Elvish Seer" hasMechanic { it == Mechanic.IgnoresSummoningSickness }
        }
    }

    // CR 613.1f: an ability granted by a static ability keeps where it came from (D42: ability origin)
    @Test
    fun grantedAbilityOriginNamesItsSource() = scenario {
        alice.battlefield("Elvish Seer", "Eden Cloak")
        attach("Eden Cloak", to = "Elvish Seer")
        expect {
            val granted = "Elvish Seer".view.characteristics!!.abilities.single { it.origin is AbilityOrigin.FromAbility }
            (granted.origin as AbilityOrigin.FromAbility).source shouldBe card("Eden Cloak").id
        }
    }

    // CR 613.1f: "loses flying" removes the keyword and what it means (D24)
    @Test
    fun losingAKeywordRemovesItsMeaning() = scenario {
        alice.battlefield("Beacon Pest", "Inspector's Firebird")
        effect(alice, Mechanic.LoseAbilities(Keyword.Flying), on = listOf("Inspector's Firebird"))
        expect {
            "Inspector's Firebird" lacksKeyword Keyword.Flying
            "Inspector's Firebird" hasKeyword Keyword.Haste
            "Inspector's Firebird" lacksMechanic { it is Mechanic.CanBeBlockedOnlyBy }
        }
    }

    // CR 122.1b, 613.1f: a keyword counter gives its keyword in layer 6
    @Test
    fun keywordCounterGivesItsKeyword() = scenario {
        alice.battlefield("Elvish Seer", counters = listOf(CounterKind.Keyword(Keyword.Flying) to 1))
        expect {
            "Elvish Seer" hasKeyword Keyword.Flying
        }
    }

    // CR 613.1f, 613.10: "can't have" keeps a keyword from being gained later
    @Test
    fun cantHaveBeatsALaterGrant() = scenario {
        alice.battlefield("Elvish Seer")
        effect(alice, Mechanic.CantHaveAbility(Keyword.Flying), on = listOf("Elvish Seer"))
        effect(alice, Mechanic.GainAbilities(listOf(Keyword.Flying)), on = listOf("Elvish Seer"))
        expect {
            "Elvish Seer" lacksKeyword Keyword.Flying
        }
    }

    // CR 707.2, 613.2a: a copy effect makes it a copy of the original's copiable values; CR 707.9b: an exception
    // ("except it's 4/4") becomes part of them
    @Test
    fun copyEffectWithAnException() = scenario {
        alice.battlefield("Elvish Seer", "Elvish Hierophant")
        effect(
            alice,
            Mechanic.CopyOf(Objects.Bound("original"), except = listOf(CopyException.PowerToughness(4, 4))),
            on = listOf("Elvish Seer"),
            names = mapOf("original" to "Elvish Hierophant"),
        )
        expect {
            card("Elvish Seer").view.name shouldBe "Elvish Hierophant"
            // 4/4 copiable values, +1/+1 from the real Hierophant; the copy's own anthem pumps the real one
            card("Elvish Seer").view.power shouldBe 5
            "Elvish Hierophant".power shouldBe 3
        }
    }

    // CR 708.2a, 613.2b: face down it is a 2/2 creature with no name, types other than creature, or abilities
    @Test
    fun faceDownIsANamelessTwoTwo() = scenario {
        alice.battlefield("Elvish Hierophant")
        alice.battlefield("Elvish Seer", faceDown = true)
        expect {
            card("Elvish Seer").view.name shouldBe null
            card("Elvish Seer").view.power shouldBe 2
            card("Elvish Seer").view.characteristics!!.abilities shouldBe emptyList()
        }
    }

    // CR 709.4: a split card outside the Stack has both halves' characteristics; its mana value is their total
    @Test
    fun splitCardHasBothHalves() = scenario {
        alice.hand("Expanse // Eruption")
        expect {
            "Expanse".view.name shouldBe "Expanse // Eruption"
            "Expanse".colors shouldBe listOf(Color.Blue, Color.Red)
            "Expanse".view.characteristics!!.manaValue() shouldBe 6
        }
    }
}
