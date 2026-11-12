package arcanum.engine.cube

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.EventPattern
import arcanum.api.PlayerFilter
import arcanum.api.RecipientFilter
import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Effect
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.KeywordAction
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Color
import arcanum.api.Mechanic
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertTrue

/** Cube chunk 8, burn and removal, played as a player would: cast with their costs, resolved through priority. */
class Chunk08BurnAndRemovalScenariosTest {
    private fun Scenario.alternative(name: String): CastWay = CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
        (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost } == true
    }.origin)

    /** A vanilla 3/3. */
    private val brute = Cards.invent("Bramblehide Brute", "{2}{G}", "Creature — Beast", "", "3/3")

    /** "Regenerate target creature." ({G} instant, written in the model: the parser doesn't read regeneration yet), a
     * shield in response (CR 701.19a). */
    private val mendingBark = Cards.define("Mending Bark", CardDefinition(Layout.Normal, listOf(FaceDefinition(
        "Mending Bark", ManaCost.parse("{G}"), types = listOf(CardType.Instant),
        abilities = listOf(Ability.Spell(Instructions(
            targets = listOf(TargetSlot("creature", TargetKind.Objects(ObjectFilter.Type(CardType.Creature)), Value.Fixed(1), Value.Fixed(1))),
            steps = listOf(Step.Do(Effect.Action(KeywordAction.Regenerate(Objects.Target("creature"))))),
        ))),
    ))))

    private fun Scenario.damage(name: String) = state.objOrThrow(card(name)).damage

    /** Storm Spark: 3 damage to any target. */
    @Test
    fun stormSpark() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.battlefield("Elvish Hierophant")

        alice.casts("Storm Spark", target = "Elvish Hierophant")
        allPass()

        expect {
            bob.graveyard has "Elvish Hierophant"
            alice.graveyard has "Storm Spark"
            "Mountain".tapped shouldBe true
        }
    }

    /** Cinder Bolt: 2 damage to any target; flashback {4}{R} from the graveyard, then it's exiled. */
    @Test
    fun cinderBoltAndItsFlashback() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Cinder Bolt")

        alice.casts("Cinder Bolt", target = "bob")
        allPass()
        expect {
            bob.life shouldBe 18
            alice.graveyard has "Cinder Bolt"
        }

        // CR 702.34a
        alice.casts("Cinder Bolt", target = "bob")
        allPass()
        expect {
            bob.life shouldBe 16
            alice.exile has "Cinder Bolt"
            (0..5).all { card("Mountain", it).tapped } shouldBe true
        }
    }

    /** Magma Needle: 1 damage; flashback by sacrificing a Mountain. */
    @Test
    fun magmaNeedleAndItsFlashback() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Magma Needle")

        alice.casts("Magma Needle", target = "bob")
        allPass()
        alice.casts("Magma Needle", target = "bob", choosing = listOf("Mountain"))
        expect {
            alice.graveyard has "Mountain"
            alice.battlefield.size shouldBe 1
        }
        allPass()

        expect {
            bob.life shouldBe 18
            alice.exile has "Magma Needle"
        }
    }

    /** Flash Surge kicked: 4 damage instead of 2. */
    @Test
    fun flashSurgeKicked() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Flash Surge")

        // CR 702.33d
        alice.casts("Flash Surge", target = "bob", kicked = 1)
        allPass()

        expect { bob.life shouldBe 16 }
    }

    /** Flash Surge not kicked: 2 damage. */
    @Test
    fun flashSurgeUnkicked() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Flash Surge")

        alice.casts("Flash Surge", target = "bob", kicked = 0)
        allPass()

        expect { bob.life shouldBe 18 }
    }

    /** Voltaic Jolt: 2 damage, or 4 with metalcraft (three artifacts). */
    @Test
    fun voltaicJoltWithMetalcraft() = scenario {
        alice.battlefield("Mountain", "Lotus Frond", "Verdant Bauble", "Onyx Bauble")
        alice.hand("Voltaic Jolt")

        alice.casts("Voltaic Jolt", target = "bob", paying = listOf("Mountain"))
        allPass()

        expect { bob.life shouldBe 16 }
    }

    /** Voltaic Jolt without metalcraft. */
    @Test
    fun voltaicJoltWithoutMetalcraft() = scenario {
        alice.battlefield("Mountain", "Verdant Bauble", "Onyx Bauble")
        alice.hand("Voltaic Jolt")

        alice.casts("Voltaic Jolt", target = "bob", paying = listOf("Mountain"))
        allPass()

        expect { bob.life shouldBe 18 }
    }

    /** Profane Scorch: 2 damage to a creature; it survives as a 3/3. */
    @Test
    fun profaneScorch() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Profane Scorch")
        bob.battlefield(brute)

        alice.casts("Profane Scorch", target = brute)
        allPass()

        expect { bob.battlefield has brute }
        assertTrue(damage(brute) == 2)
    }

    /** Profane Scorch with delirium (land, instant, creature, artifact in the graveyard): 6 damage. */
    @Test
    fun profaneScorchWithDelirium() = scenario {
        alice.battlefield("Mountain")
        alice.graveyard("Forest", "Storm Spark", "Elvish Seer", "Lotus Frond")
        alice.hand("Profane Scorch")
        bob.battlefield(brute)

        alice.casts("Profane Scorch", target = brute)
        allPass()

        expect { bob.graveyard has brute }
    }

    /** Immolation Bolt: 3 damage; the creature dealt damage can't be regenerated this turn. */
    @Test
    fun immolationBoltBeatsRegeneration() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Immolation Bolt")
        bob.battlefield("Elvish Hierophant", "Forest")
        bob.hand(mendingBark)

        alice.casts("Immolation Bolt", target = "Elvish Hierophant")
        alice.passes()
        bob.casts(mendingBark, target = "Elvish Hierophant")
        allPass()
        allPass()

        // CR 701.19c: the shield doesn't apply
        expect {
            bob.graveyard has "Elvish Hierophant"
            alice.graveyard has "Immolation Bolt"
        }
    }

    /** Doomfire: X damage. */
    @Test
    fun doomfire() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Doomfire")

        alice.casts("Doomfire", x = 2, target = "bob")
        allPass()

        expect { bob.life shouldBe 18 }
    }

    /** Doomfire with X 5: it can't be countered. */
    @Test
    fun doomfireCantBeCounteredWithXFive() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Doomfire")
        bob.battlefield("Island", "Island")
        bob.hand("Denial")

        alice.casts("Doomfire", x = 5, target = "bob")
        alice.passes()
        bob.casts("Denial", target = "Doomfire")
        allPass()
        // CR 101.2: Denial resolves, but Doomfire stays on the Stack
        expect {
            stack.names shouldBe listOf("Doomfire")
            bob.graveyard has "Denial"
        }
        allPass()

        expect { bob.life shouldBe 15 }
    }

    /** "Prevent all damage that would be dealt to you." ({2}{W} enchantment, written in the model: the parser doesn't
     * read it yet) */
    private val veil = Cards.define("Hallowed Veil", CardDefinition(Layout.Normal, listOf(FaceDefinition(
        "Hallowed Veil", ManaCost.parse("{2}{W}"), types = listOf(CardType.Enchantment),
        abilities = listOf(Ability.Static(listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any,
            RecipientFilter(players = PlayerFilter.You)))), Affected.Objects(Objects.Self))),
    ))))

    /** Doomfire with a small X: its damage is prevented. */
    @Test
    fun doomfirePrevented() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Doomfire")
        bob.battlefield(veil)

        alice.casts("Doomfire", x = 2, target = "bob")
        allPass()

        expect { bob.life shouldBe 20 }
    }

    /** Doomfire with X 5: the damage can't be prevented (CR 615.12). */
    @Test
    fun doomfireCantBePreventedWithXFive() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Doomfire")
        bob.battlefield(veil)

        alice.casts("Doomfire", x = 5, target = "bob")
        allPass()

        expect { bob.life shouldBe 15 }
    }

    /** Sudden Rot: can't be countered; destroys a nonland permanent with mana value 3 or less. */
    @Test
    fun suddenRot() = scenario {
        alice.battlefield("Swamp", "Forest")
        alice.hand("Sudden Rot")
        bob.battlefield("Elvish Hierophant", "Island", "Island")
        bob.hand("Denial")

        alice.casts("Sudden Rot", target = "Elvish Hierophant")
        alice.passes()
        bob.casts("Denial", target = "Sudden Rot")
        allPass()
        allPass()

        expect {
            bob.graveyard has "Elvish Hierophant"
            alice.graveyard has "Sudden Rot"
        }
    }

    // CR 601.2c: a land isn't a target for Sudden Rot
    @Test
    fun suddenRotDoesntHitLands() = scenario {
        alice.battlefield("Swamp", "Forest")
        alice.hand("Sudden Rot")
        bob.battlefield("Island")

        kotlin.test.assertFailsWith<IllegalArgumentException> { alice.casts("Sudden Rot") }
    }

    /** Old Enmity: destroy target artifact; flashback {G}. */
    @Test
    fun oldEnmityAndItsFlashback() = scenario {
        alice.battlefield("Mountain", "Mountain", "Forest")
        alice.hand("Old Enmity")
        bob.battlefield("Lotus Frond", "Verdant Bauble")

        alice.casts("Old Enmity", target = "Lotus Frond", paying = listOf("Mountain", "Mountain"))
        allPass()
        alice.casts("Old Enmity", target = "Verdant Bauble")
        allPass()

        expect {
            bob.graveyard.size shouldBe 2
            alice.exile has "Old Enmity"
            "Forest".tapped shouldBe true
        }
    }

    /** Hollow Victory: pay 3 life (or discard) as it's cast; destroy target creature. */
    @Test
    fun hollowVictory() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Hollow Victory", "Forest")
        bob.battlefield("Elvish Hierophant")

        alice.casts("Hollow Victory", target = "Elvish Hierophant", option = "Pay life")
        allPass()

        expect {
            alice.life shouldBe 17
            alice.hand has "Forest"
            bob.graveyard has "Elvish Hierophant"
        }
    }

    /** Bone Splinters: sacrifice a creature as it's cast; destroy target creature. */
    @Test
    fun boneSplinters() = scenario {
        alice.battlefield("Swamp", "Elvish Seer")
        alice.hand("Bone Splinters")
        bob.battlefield("Elvish Hierophant")

        alice.casts("Bone Splinters", target = "Elvish Hierophant", option = "Sacrifice", choosing = listOf("Elvish Seer"))
        allPass()

        expect {
            alice.graveyard has "Elvish Seer"
            bob.graveyard has "Elvish Hierophant"
        }
    }

    /** Wink Out: 4 life instead of its mana cost while you control a Swamp; destroys a nonblack creature, no regeneration. */
    @Test
    fun winkOutForLife() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Wink Out")
        bob.battlefield("Elvish Hierophant", "Corpse Gnawer", "Forest")
        bob.hand(mendingBark)

        perform(arcanum.engine.input.Input.Cast(alice.id, card("Wink Out"), alternative("Wink Out")))
        // CR 601.2c: the black creature isn't a target
        kotlin.test.assertEquals(setOf(TargetRef.Object(card("Elvish Hierophant"))), decision<Decision.Targets>().options.toSet())
        answer(Answer.Targets(listOf(target("Elvish Hierophant"))))
        alice.passes()
        bob.casts(mendingBark, target = "Elvish Hierophant")
        allPass()
        allPass()

        expect {
            alice.life shouldBe 16
            "Swamp".tapped shouldBe false
            bob.graveyard has "Elvish Hierophant"
        }
    }

    /** Deadly Shove: destroys a creature with mana value 2 or less. */
    @Test
    fun deadlyShove() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Deadly Shove")
        bob.battlefield("Wall of Portents")

        alice.casts("Deadly Shove", target = "Wall of Portents")
        allPass()

        expect { bob.graveyard has "Wall of Portents" }
    }

    /** Deadly Shove: a mana value 3 creature survives without revolt. */
    @Test
    fun deadlyShoveTooBigWithoutRevolt() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Deadly Shove")
        bob.battlefield("Elvish Hierophant")

        alice.casts("Deadly Shove", target = "Elvish Hierophant")
        allPass()

        expect {
            bob.battlefield has "Elvish Hierophant"
            alice.graveyard has "Deadly Shove"
        }
    }

    /** Deadly Shove with revolt (Lotus Frond sacrificed for mana this turn): mana value 4 or less. */
    @Test
    fun deadlyShoveWithRevolt() = scenario {
        alice.battlefield("Lotus Frond")
        alice.hand("Deadly Shove")
        bob.battlefield("Elvish Hierophant")

        alice.activates("Lotus Frond", color = Color.Black)
        alice.casts("Deadly Shove", target = "Elvish Hierophant")
        allPass()

        expect {
            alice.graveyard has "Lotus Frond"
            bob.graveyard has "Elvish Hierophant"
        }
    }

    /** Curse Word: destroy target creature. */
    @Test
    fun curseWord() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Curse Word")
        bob.battlefield("Elvish Hierophant", "Wall of Portents")

        alice.casts("Curse Word", target = "Elvish Hierophant")
        allPass()

        expect {
            bob.graveyard has "Elvish Hierophant"
            bob.battlefield has "Wall of Portents"
        }
    }

    /** Curse Word overloaded: destroys each creature. */
    @Test
    fun curseWordOverloaded() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp", "Elvish Seer")
        alice.hand("Curse Word")
        bob.battlefield("Elvish Hierophant", "Wall of Portents")

        // CR 702.96a
        alice.casts("Curse Word", way = alternative("Curse Word"), paying = listOf("Plains", "Plains", "Swamp", "Swamp"))
        allPass()

        expect {
            bob.graveyard has "Elvish Hierophant"
            bob.graveyard has "Wall of Portents"
            alice.graveyard has "Elvish Seer"
        }
    }

    /** Plowshare Decree: exile target creature; its controller gains life equal to its power. */
    @Test
    fun plowshareDecree() = scenario {
        alice.battlefield("Plains")
        alice.hand("Plowshare Decree")
        bob.battlefield(brute)

        alice.casts("Plowshare Decree", target = brute)
        allPass()

        expect {
            bob.exile has brute
            bob.life shouldBe 23
        }
    }

    /** Road to Exile: exile target creature; its controller may fetch a basic land tapped. */
    @Test
    fun roadToExile() = scenario {
        alice.battlefield("Plains")
        alice.hand("Road to Exile")
        bob.battlefield(brute)
        bob.library("Storm Spark", "Forest", "Denial")

        alice.casts("Road to Exile", target = brute)
        allPass()
        assertTrue(decision<Decision.May>().player == bob.id)
        answer(Answer.YesNo(true))
        bob.chooses("Forest")

        expect {
            bob.exile has brute
            bob.battlefield has "Forest"
            "Forest".tapped shouldBe true
            bob.library.size shouldBe 2
        }
    }

    /** Spectral Finale: converge: three colours spent exile a mana value 3 permanent. */
    @Test
    fun spectralFinaleWithThreeColours() = scenario {
        alice.battlefield("Plains", "Island", "Mountain")
        alice.hand("Spectral Finale")
        bob.battlefield("Elvish Hierophant")

        alice.casts("Spectral Finale", x = 2, target = "Elvish Hierophant", paying = listOf("Plains", "Island", "Mountain"))
        allPass()

        expect { bob.exile has "Elvish Hierophant" }
    }

    /** Spectral Finale: one colour spent isn't enough for a mana value 3 permanent. */
    @Test
    fun spectralFinaleWithOneColour() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand("Spectral Finale")
        bob.battlefield("Elvish Hierophant")

        alice.casts("Spectral Finale", x = 2, target = "Elvish Hierophant")
        allPass()

        expect {
            bob.battlefield has "Elvish Hierophant"
            alice.graveyard has "Spectral Finale"
        }
    }

    /** Rescind: return target nonland permanent with mana value X; draw a card. */
    @Test
    fun rescind() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand("Rescind")
        alice.library("Forest")
        bob.battlefield("Elvish Hierophant", "Wall of Portents")

        alice.casts("Rescind", x = 3, target = "Elvish Hierophant")
        allPass()

        expect {
            bob.hand has "Elvish Hierophant"
            alice.hand has "Forest"
        }
    }

    // CR 601.2c: with X 3 a mana value 2 permanent isn't a target
    @Test
    fun rescindNeedsTheRightManaValue() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand("Rescind")
        bob.battlefield("Wall of Portents")

        kotlin.test.assertFailsWith<IllegalArgumentException> { alice.casts("Rescind", x = 3, target = "Wall of Portents") }
    }

    /** Click Back: return target creature to its owner's hand; untap up to two lands. */
    @Test
    fun clickBack() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Click Back")
        bob.battlefield("Elvish Hierophant")

        alice.casts("Click Back", target = "Elvish Hierophant")
        allPass()
        answer(Answer.Objects(listOf(card("Island", 0), card("Island", 1))))

        expect {
            bob.hand has "Elvish Hierophant"
            card("Island", 0).tapped shouldBe false
            card("Island", 1).tapped shouldBe false
        }
    }
}
