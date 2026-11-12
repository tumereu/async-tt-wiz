package arcanum.engine.casting

import arcanum.engine.state.castOrNone
import arcanum.api.Ability
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.NamedValue
import arcanum.engine.state.PaidCost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Total costs (CR 601.2f, 118): additional and alternative costs of the cube's keywords, cost changes, special mana. */
class CostsTest {
    private fun Scenario.alternative(name: String): CastWay = CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first { inst ->
        (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AlternativeCost } == true
    }.origin)

    private fun Scenario.facts(name: String): arcanum.engine.state.CastFacts = state.objOrThrow(card(name)).castOrNone

    // CR 702.33a, 601.2b: kicker is an optional additional cost; kicked, it's paid on top
    @Test
    fun kickerPaid() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Flash Surge")
        alice.casts("Flash Surge", target = "bob", kicked = 1)
        expect { (0..4).all { card("Mountain", it).tapped } shouldBe true }
        assertEquals(listOf(PaidCost(Keyword.Kicker.Kind, 1, 0)), facts("Flash Surge").paid)
    }

    // CR 702.33a: not kicked, only the mana cost
    @Test
    fun kickerDeclined() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Flash Surge")
        alice.casts("Flash Surge", target = "bob", kicked = 0)
        expect { card("Mountain", 1).tapped shouldBe false }
        assertTrue(facts("Flash Surge").paid.isEmpty())
    }

    // CR 601.2b: the kicker decision names its cost
    @Test
    fun kickerIsAsked() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Flash Surge")
        perform(Input.Cast(alice.id, card("Flash Surge")))
        val d = assertIs<Decision.May>(pending)
        assertEquals("{4}", (d.cost!!.parts.single() as arcanum.api.CostPart.Mana).cost.toString())
    }

    // CR 702.33a, 107.3: kicker {X}: X is announced only when kicked, and "X can't be 0"
    @Test
    fun kickerWithX() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand("Pilfering Skydiver")
        perform(Input.Cast(alice.id, card("Pilfering Skydiver")))
        answer(Answer.YesNo(true))
        val d = assertIs<Decision.Announce>(pending)
        assertEquals(1, d.min)
        assertFailsWith<IllegalArgumentException> { answer(Answer.Number(0)) }
        answer(Answer.Number(2))
        val pay = assertIs<Decision.ManaPayment>(pending)
        assertEquals("{3}{U}", pay.cost.toString())
    }

    // CR 601.2f, 118.8: a mandatory additional cost "A or B": choose, then the objects it uses
    @Test
    fun sacrificeOrDiscard() = scenario {
        alice.battlefield("Swamp", "Elvish Seer", "Thicketwood Elves")
        bob.battlefield("Wall of Portents")
        alice.hand("Bone Splinters", "Forest")
        alice.casts("Bone Splinters", target = "Wall of Portents", option = "Sacrifice", choosing = listOf("Thicketwood Elves"))
        expect {
            alice.graveyard has "Thicketwood Elves"
            alice.battlefield has "Elvish Seer"
            alice.hand has "Forest"
            log mentions "Sacrificed"
        }
        // The sacrificed creature's last known information stays with the spell (CR 608.2h)
        val item = state.stackItems[card("Bone Splinters")]!!
        assertTrue(item.snapshots.any { it.characteristics?.name == "Thicketwood Elves" })
        assertEquals(listOf(PaidCost(null, 1)), facts("Bone Splinters").paid)
    }

    // CR 602.2b, 601.2h: a cost's choice of objects names the printed part it pays, so a host can show its phrase: the activated
    // ability's own cost, part 0 ("Sacrifice a creature")
    @Test
    fun anActivatedCostsChoiceNamesItsPart() = scenario {
        alice.battlefield("Goblin Volley Pit", "Elvish Seer", "Thicketwood Elves")
        val origin = view()[card("Goblin Volley Pit")]!!.characteristics!!.abilities.first { it.ability is Ability.Activated }.origin
        perform(Input.Activate(alice.id, card("Goblin Volley Pit"), origin))
        answer(Answer.Targets(listOf(arcanum.engine.state.TargetRef.Player(bob.id))))
        val choice = assertIs<Decision.ChooseObjects>(pending)
        assertEquals(arcanum.engine.decision.CostPartAt(null, 0), choice.cost)
        val ability = view()[card("Goblin Volley Pit")]!!.characteristics!!.abilities.first { it.origin == origin }.ability as Ability.Activated
        assertEquals("a creature", ability.cost.span(0)?.of(ability.text!!))
    }

    // CR 601.2f: a spell's additional cost names the static ability stating it
    @Test
    fun anAdditionalCostsChoiceNamesItsAbility() = scenario {
        alice.battlefield("Swamp", "Elvish Seer", "Thicketwood Elves")
        bob.battlefield("Wall of Portents")
        alice.hand("Bone Splinters")
        perform(Input.Cast(alice.id, card("Bone Splinters")))
        answer(Answer.Targets(listOf(arcanum.engine.state.TargetRef.Object(card("Wall of Portents")))))
        val statics = view()[card("Bone Splinters")]!!.characteristics!!.abilities.filter { inst ->
            (inst.ability as? Ability.Static)?.mechanics?.any { it is Mechanic.AdditionalCost } == true }
        if (pending is Decision.ChooseOption) answer(Answer.Option("Sacrifice"))
        assertEquals(statics.single().origin, assertIs<Decision.ChooseObjects>(pending).cost?.ability)
    }

    // P5: when only one option of "A or B" can be paid, it is chosen without asking
    @Test
    fun onlyPayableOptionIsChosen() = scenario {
        alice.battlefield("Swamp")
        bob.battlefield("Wall of Portents")
        alice.hand("Bone Splinters", "Forest")
        alice.casts("Bone Splinters", target = "Wall of Portents")
        expect {
            alice.graveyard has "Forest"
            stack.names shouldBe listOf("Bone Splinters")
        }
    }

    // CR 119.4, 118.3: pay 3 life as the additional cost
    @Test
    fun payLifeAsAdditionalCost() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Hollow Victory", "Forest")
        bob.battlefield("Wall of Portents")
        alice.casts("Hollow Victory", target = "Wall of Portents", option = "Pay life")
        expect {
            alice.life shouldBe 17
            alice.hand has "Forest"
        }
    }

    // CR 119.4: life can't be paid beyond the life total: the other option is the only one
    @Test
    fun notEnoughLifeLeavesTheOtherOption() = scenario {
        alice.life = 2
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Hollow Victory", "Forest")
        bob.battlefield("Wall of Portents")
        alice.casts("Hollow Victory", target = "Wall of Portents")
        expect {
            alice.life shouldBe 2
            alice.graveyard has "Forest"
        }
    }

    // CR 118.9: an alternative cost without a keyword, with its condition (controls a Swamp)
    @Test
    fun alternativeCostPayingLife() = scenario {
        alice.battlefield("Swamp")
        bob.battlefield("Thicketwood Elves")
        alice.hand("Wink Out")
        alice.casts("Wink Out", way = alternative("Wink Out"), target = "Thicketwood Elves")
        expect {
            alice.life shouldBe 16
            "Swamp".tapped shouldBe false
        }
        assertEquals(PaidCost(null, 1), facts("Wink Out").alternative)
    }

    // CR 118.9, 601.3: the alternative cost's condition must hold
    @Test
    fun alternativeCostConditionRefused() = scenario {
        alice.battlefield("Mountain")
        bob.battlefield("Thicketwood Elves")
        alice.hand("Wink Out")
        val way = alternative("Wink Out")
        assertFailsWith<IllegalArgumentException> { alice.casts("Wink Out", way = way, target = "Thicketwood Elves") }
    }

    // CR 118.9: "if it's not your turn": usable on the opponent's turn only
    @Test
    fun alternativeCostOnlyOnTheOpponentsTurn() = scenario {
        alice.hand("Force of Rebuttal", "Denial")
        val way = alternative("Force of Rebuttal")
        assertFailsWith<IllegalArgumentException> { alice.casts("Force of Rebuttal", way = way) }
    }

    // CR 118.9: return an Island to its owner's hand instead of paying mana
    @Test
    fun returnAnIslandInstead() = scenario {
        alice.battlefield("Forest")
        alice.hand("Elvish Seer")
        bob.battlefield("Island", "Island")
        bob.hand("Lapse of Focus")
        alice.casts("Elvish Seer")
        alice.passes()
        bob.casts("Lapse of Focus", way = alternative("Lapse of Focus"), target = "Elvish Seer", choosing = listOf("Island"))
        expect {
            bob.hand has "Island"
            bob.battlefield.size shouldBe 1
            stack.names shouldBe listOf("Elvish Seer", "Lapse of Focus")
        }
    }

    // CR 702.74a: evoke is an alternative cost; the card remembers it was evoked
    @Test
    fun evokeExilingAGreenCard() = scenario {
        alice.hand("Persistence", "Elvish Seer")
        alice.casts("Persistence", way = alternative("Persistence"))
        expect {
            alice.exile has "Elvish Seer"
            stack.names shouldBe listOf("Persistence")
        }
        assertEquals(PaidCost(Keyword.Evoke.Kind, 1), facts("Persistence").alternative)
    }

    // CR 702.34a: flashback: cast from the graveyard for its flashback cost; it's exiled when it leaves the Stack
    @Test
    fun flashbackFromTheGraveyard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard("Cinder Bolt")
        alice.casts("Cinder Bolt", target = "bob")
        expect {
            stack.names shouldBe listOf("Cinder Bolt")
            (0..4).all { card("Mountain", it).tapped } shouldBe true
        }
        assertEquals(Zone.Graveyard, facts("Cinder Bolt").castFrom)
        assertEquals(PaidCost(Keyword.Flashback.Kind, 1), facts("Cinder Bolt").alternative)
        assertTrue(state.effects.values.any { e -> e.mechanics.any { it is Mechanic.Replacement } })
    }

    // CR 702.34a: from the hand it costs its mana cost
    @Test
    fun flashbackCardFromTheHand() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Cinder Bolt")
        alice.casts("Cinder Bolt", target = "bob")
        assertEquals(Zone.Hand, facts("Cinder Bolt").castFrom)
        assertEquals(null, facts("Cinder Bolt").alternative)
    }

    // CR 702.34a: a flashback cost that sacrifices a Mountain
    @Test
    fun flashbackSacrificingAMountain() = scenario {
        alice.battlefield("Mountain", "Forest")
        alice.graveyard("Magma Needle")
        alice.casts("Magma Needle", target = "bob")
        expect {
            alice.graveyard has "Mountain"
            "Forest".tapped shouldBe false
        }
    }

    // CR 702.34a, 400.7: flashback granted until end of turn still exiles the spell, though it's a new object on the Stack
    @Test
    fun grantedFlashbackExilesTheSpell() = scenario {
        alice.battlefield("Mountain")
        alice.graveyard("Storm Spark")
        effect(alice, Mechanic.GainAbilities(listOf(Keyword.Flashback(arcanum.api.Cost(listOf(arcanum.api.CostPart.ItsManaCost))))),
            on = listOf("Storm Spark"))
        alice.casts("Storm Spark", target = "bob")
        expect { "Mountain".tapped shouldBe true }
        allPass()
        expect {
            bob.life shouldBe 17
            alice.exile has "Storm Spark"
        }
    }

    // CR 601.2b, 601.2f: a cost that counts the spell's X uses the X announced while casting it
    @Test
    fun costCountingX() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand("Forest", "Island", "Swamp")
        alice.graveyard("Wildfire Spread")
        alice.casts("Wildfire Spread", x = 2, target = "bob", choosing = listOf("Forest", "Island"))
        expect {
            alice.graveyard.names shouldBe listOf("Forest", "Island")
            alice.hand.names shouldBe listOf("Swamp")
        }
    }

    // CR 601.3: without a permission, nothing is cast from the graveyard
    @Test
    fun noCastingFromTheGraveyardWithoutPermission() = scenario {
        alice.battlefield("Mountain")
        alice.graveyard("Storm Spark")
        assertFailsWith<IllegalArgumentException> { alice.casts("Storm Spark", target = "bob") }
    }

    // CR 601.3a: a permission with a condition: "as long as you control a Zombie"
    @Test
    fun castFromTheGraveyardWithAPermission() = scenario {
        alice.battlefield("Swamp", "Corpse Gnawer")
        alice.graveyard("Tombscuttler")
        alice.casts("Tombscuttler")
        assertEquals(Zone.Graveyard, facts("Tombscuttler").castFrom)
        allPass()
        expect { alice.battlefield has "Tombscuttler" }
    }

    // CR 601.3a: the permission's condition doesn't hold: no casting
    @Test
    fun permissionConditionMustHold() = scenario {
        alice.battlefield("Swamp")
        alice.graveyard("Tombscuttler")
        assertFailsWith<IllegalArgumentException> { alice.casts("Tombscuttler") }
    }

    // CR 702.138a: escape: {R}{R} and exiling eight other cards from the graveyard
    @Test
    fun escape() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.graveyard("Bull of the Pyre", "Forest", "Island", "Swamp", "Plains", "Storm Spark", "Denial", "Cinder Bolt", "Click Back")
        alice.casts("Bull of the Pyre")
        expect {
            alice.exile.size shouldBe 8
            alice.graveyard.size shouldBe 0
            stack.names shouldBe listOf("Bull of the Pyre")
        }
        assertEquals(PaidCost(Keyword.Escape.Kind, 1), facts("Bull of the Pyre").alternative)
    }

    // CR 702.96a–b: overloaded, "target" becomes "each": no targets
    @Test
    fun overloadHasNoTargets() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp")
        bob.battlefield("Thicketwood Elves")
        alice.hand("Curse Word")
        alice.casts("Curse Word", way = alternative("Curse Word"))
        val item = state.stackItems[card("Curse Word")]!!
        assertTrue(item.targets.isEmpty())
        assertEquals(PaidCost(Keyword.Overload.Kind, 1), facts("Curse Word").alternative)
        expect { (0..1).all { card("Plains", it).tapped } shouldBe true }
    }

    // CR 702.81a: retrace: from the graveyard, discarding a land card in addition
    @Test
    fun retrace() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Forest")
        alice.graveyard("Crow's Larceny")
        alice.casts("Crow's Larceny", target = "bob")
        expect {
            alice.graveyard has "Forest"
            stack.names shouldBe listOf("Crow's Larceny")
        }
    }

    // CR 702.51a: convoke: creatures tapped pay for generic or their colour
    @Test
    fun convoke() = scenario {
        alice.battlefield("Swamp", "Corpse Gnawer", "Wall of Portents", "Tinkered Mite")
        bob.battlefield("Thicketwood Elves")
        alice.hand("Deadly Plotting")
        alice.casts("Deadly Plotting", target = "Thicketwood Elves",
            paying = listOf("Swamp", "Corpse Gnawer", "Wall of Portents", "Tinkered Mite"))
        expect {
            "Corpse Gnawer".tapped shouldBe true
            "Wall of Portents".tapped shouldBe true
            stack.names shouldBe listOf("Deadly Plotting")
        }
        val paidWith = facts("Deadly Plotting").paidWith[arcanum.api.ManaPaymentWay.TapCreatures.toString()]
        assertEquals(3, (paidWith as NamedValue.Objects).ids.size)
    }

    // CR 702.51a: a green creature can't pay black mana
    @Test
    fun convokeColourMustMatch() = scenario {
        alice.battlefield("Swamp", "Wall of Portents", "Tinkered Mite", "Gearwing Glider")
        bob.battlefield("Thicketwood Elves")
        alice.hand("Deadly Plotting")
        assertFailsWith<IllegalArgumentException> {
            alice.casts("Deadly Plotting", target = "Thicketwood Elves", paying = listOf("Swamp", "Wall of Portents", "Tinkered Mite", "Gearwing Glider"))
        }
    }

    // CR 702.66a: delve: cards exiled from the graveyard pay for generic mana
    @Test
    fun delve() = scenario {
        alice.battlefield("Island")
        alice.graveyard("Storm Spark", "Denial", "Forest", "Mountain", "Swamp", "Plains", "Cinder Bolt")
        alice.hand("Hoard Voyage")
        alice.casts("Hoard Voyage", paying = listOf("Island", "Storm Spark", "Denial", "Forest", "Mountain", "Swamp", "Plains", "Cinder Bolt"))
        expect {
            alice.exile.size shouldBe 7
            stack.names shouldBe listOf("Hoard Voyage")
        }
    }

    // CR 702.41a, 601.2f: affinity for artifacts: {1} less for each artifact
    @Test
    fun affinityReducesTheCost() = scenario {
        alice.battlefield("Island", "Lotus Frond", "Verdant Bauble", "Onyx Bauble")
        alice.hand("Cogitating Sentry")
        perform(Input.Cast(alice.id, card("Cogitating Sentry")))
        assertEquals("{3}{U}", assertIs<Decision.ManaPayment>(pending).cost.toString())
    }

    // CR 118.7a: a generic reduction never reduces coloured mana
    @Test
    fun reductionStopsAtColouredMana() = scenario {
        alice.battlefield("Swamp", "Lotus Frond", "Verdant Bauble", "Onyx Bauble", "Nacre Bauble", "Garnet Bauble")
        alice.hand("Rebuilt Ratling")
        perform(Input.Cast(alice.id, card("Rebuilt Ratling")))
        assertEquals("{B}", assertIs<Decision.ManaPayment>(pending).cost.toString())
    }

    // CR 601.2f: "Noncreature spells cost {1} more" applies to noncreature spells only
    @Test
    fun costIncrease() = scenario {
        bob.battlefield("Seraphine, Keeper of the Gate")
        alice.battlefield("Mountain", "Mountain", "Forest")
        alice.hand("Storm Spark", "Elvish Seer")
        perform(Input.Cast(alice.id, card("Storm Spark")))
        answer(Answer.Targets(listOf(target("bob"))))
        assertEquals("{1}{R}", assertIs<Decision.ManaPayment>(pending).cost.toString())
        perform(Input.Cancel(alice.id))
        perform(Input.Cast(alice.id, card("Elvish Seer")))
        assertEquals("{G}", assertIs<Decision.ManaPayment>(pending).cost.toString())
    }

    // CR 702.120a: escalate: an additional cost for each mode beyond the first
    @Test
    fun escalate() = scenario {
        alice.battlefield("Swamp", "Swamp")
        bob.battlefield("Thicketwood Elves")
        alice.hand("Communal Cruelty", "Forest")
        alice.casts("Communal Cruelty", modes = listOf(0, 1), targets = listOf("bob", "Thicketwood Elves"))
        expect { alice.graveyard has "Forest" }
        assertEquals(listOf(PaidCost(Keyword.Escalate.Kind, 1)), facts("Communal Cruelty").paid)
    }

    // CR 702.120a: one mode: no escalate cost
    @Test
    fun escalateOneMode() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand("Communal Cruelty", "Forest")
        alice.casts("Communal Cruelty", modes = listOf(0), targets = listOf("bob"))
        expect { alice.hand has "Forest" }
    }

    // CR 601.2b, 601.2f: a reduction "for each card exiled this way" counts the cards chosen for the additional cost
    @Test
    fun reductionCountsTheCardsChosenForTheAdditionalCost() = scenario {
        alice.battlefield("Plains")
        alice.hand("Procession of Pale Light", "Wall of Portents", "Loyal Hearthhound")
        bob.battlefield("Elvish Hierophant")
        perform(Input.Cast(alice.id, card("Procession of Pale Light")))
        answer(Answer.Number(3))
        answer(Answer.Targets(listOf(target("Elvish Hierophant"))))
        answer(Answer.Objects(listOf(card("Wall of Portents"), card("Loyal Hearthhound"))))
        // {3}{W} less {2} twice: the generic part can't go below nothing (CR 118.7a)
        assertEquals("{W}", assertIs<Decision.ManaPayment>(pending).cost.toString())
    }

    // CR 702.37c, 708.4: morph: cast face down as a 2/2 creature for {3}
    @Test
    fun morphFaceDown() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand("Barkwood Pathfinders")
        alice.casts("Barkwood Pathfinders", way = alternative("Barkwood Pathfinders"))
        expect { (0..2).all { card("Forest", it).tapped } shouldBe true }
        assertTrue(state.objOrThrow(card("Barkwood Pathfinders")).faceDown)
        assertEquals(PaidCost(Keyword.Morph.Kind, 1), facts("Barkwood Pathfinders").alternative)
        allPass()
        expect {
            alice.battlefield.size shouldBe 4
            "Barkwood Pathfinders".power shouldBe 2
        }
        assertTrue(state.objOrThrow(card("Barkwood Pathfinders")).faceDown)
    }

    // CR 107.4f: Phyrexian mana paid with 2 life when there's no mana of its colour
    @Test
    fun phyrexianPaidWithLife() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Crypt Flitter")
        alice.casts("Crypt Flitter")
        expect {
            alice.life shouldBe 18
            "Mountain".tapped shouldBe true
            stack.names shouldBe listOf("Crypt Flitter")
        }
    }

    // CR 107.4f, §7.8: with mana of its colour, the suggestion pays mana, not life
    @Test
    fun phyrexianPaidWithMana() = scenario {
        alice.battlefield("Mountain", "Swamp")
        alice.hand("Crypt Flitter")
        alice.casts("Crypt Flitter")
        expect {
            alice.life shouldBe 20
            "Swamp".tapped shouldBe true
        }
    }

    // CR 107.4e: hybrid mana paid with either colour
    @Test
    fun hybridPaidWithEitherColour() = scenario {
        alice.battlefield("Plains", "Mountain")
        alice.hand("Haven Warden")
        alice.casts("Haven Warden")
        expect {
            "Plains".tapped shouldBe true
            "Mountain".tapped shouldBe true
            stack.names shouldBe listOf("Haven Warden")
        }
    }

    // CR 107.4h: {S} needs mana from a snow source
    @Test
    fun snowNeedsASnowSource() = scenario {
        alice.battlefield("Forest", "Rimefrost Druid")
        alice.hand("Rimewatcher's Orrery")
        alice.casts("Rimewatcher's Orrery")
        expect {
            "Rimefrost Druid".tapped shouldBe true
            "Forest".tapped shouldBe false
        }
    }

    // CR 107.4h: a non-snow land can't pay {S}
    @Test
    fun nonSnowCantPaySnow() = scenario {
        alice.battlefield("Forest")
        alice.hand("Rimewatcher's Orrery")
        perform(Input.Cast(alice.id, card("Rimewatcher's Orrery")))
        val d = assertIs<Decision.ManaPayment>(pending)
        assertTrue(d.suggestion.isEmpty())
        assertFailsWith<IllegalArgumentException> { alice.answers(Answer.ManaPayment(d.options)) }
    }

    // CR 702.34a: flashback an effect granted to the card in the graveyard: still cast from there and exiled afterwards,
    // though the card on the Stack is a new object (CR 400.7)
    @Test
    fun grantedFlashbackExiles() = scenario {
        alice.battlefield("Mountain")
        alice.graveyard("Storm Spark")
        alice.hand("Echoflash Adept")
        alice.castsWithoutCosts("Echoflash Adept")
        resolve()
        resolve()
        alice.casts("Storm Spark", target = "bob")
        assertEquals(Zone.Graveyard, facts("Storm Spark").castFrom)
        allPass()
        expect {
            bob.life shouldBe 17
            alice.exile has "Storm Spark"
        }
    }
}
