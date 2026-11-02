package arcanum.engine.cube

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Chunk 50 in play: creatures that come back from the graveyard, are cast or played from it, or care about cards
 * going there. */
class Chunk50GraveyardCreaturesScenariosTest {
    private val plusOne = CounterKind.PowerToughness(1, 1)

    /** Says yes to a "you may pay" and pays with the suggested mana. */
    private fun Scenario.payYes() {
        assertTrue(decision<Decision.May>().cost != null)
        answer(Answer.YesNo(true))
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
    }

    /** Netherbound Turncoat: haste, shadow; when another creature of yours dies, pay {B} to return it from the
     * graveyard. */
    // CR 113.6m
    @Test
    fun netherboundTurncoatReturns() = scenario {
        alice.battlefield("Corpse Gnawer", "Elvish Seer", "Swamp")
        alice.graveyard("Netherbound Turncoat")
        alice.activates("Corpse Gnawer", choosing = listOf("Elvish Seer"))
        expect { stack.size shouldBe 2 }
        allPass()
        payYes()
        expect {
            alice.battlefield has "Netherbound Turncoat"
            "Swamp".tapped shouldBe true
        }
    }

    // CR 702.28b: shadow: only creatures with shadow block it; haste: it attacks at once
    @Test
    fun netherboundTurncoatShadow() = scenario {
        alice.battlefield("Netherbound Turncoat", sick = true)
        bob.battlefield("Elvish Seer")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks("Netherbound Turncoat")
        allPass()
        assertFailsWith<IllegalArgumentException> { bob.blocks("Elvish Seer" to "Netherbound Turncoat") }
        allPass()
        expect { bob.life shouldBe 19 }
    }

    /** Pilfering Nibbler: when you draw your third card in a turn, it returns from the graveyard tapped. */
    @Test
    fun pilferingNibbler() = scenario {
        alice.battlefield("Island")
        alice.hand("Forebear's Insight")
        alice.graveyard("Pilfering Nibbler")
        alice.library("Forest", "Swamp", "Plains")
        alice.casts("Forebear's Insight", target = "alice")
        allPass()
        expect { stack.names shouldBe listOf("ability of Pilfering Nibbler") }
        allPass()
        expect {
            alice.battlefield has "Pilfering Nibbler"
            "Pilfering Nibbler".tapped shouldBe true
            alice.hand.size shouldBe 3
        }
    }

    /** Plague Shamblers: deathtouch; casting a spell from anywhere but your hand returns it from the graveyard tapped. */
    @Test
    fun plagueShamblers() = scenario {
        alice.battlefield("Corpse Gnawer", "Swamp")
        alice.graveyard("Plague Shamblers", "Tombscuttler")
        alice.casts("Tombscuttler")
        expect { stack.names shouldBe listOf("Tombscuttler", "ability of Plague Shamblers") }
        allPass()
        expect {
            alice.battlefield has "Plague Shamblers"
            "Plague Shamblers".tapped shouldBe true
            "Plague Shamblers" hasKeyword Keyword.Deathtouch
        }
    }

    @Test
    fun plagueShamblersNotFromTheHand() = scenario {
        alice.battlefield("Swamp")
        alice.hand("Tombscuttler")
        alice.graveyard("Plague Shamblers")
        alice.casts("Tombscuttler")
        expect { stack.names shouldBe listOf("Tombscuttler") }
    }

    /** Skrit: at your upkeep, you may return it from your graveyard to your hand. */
    @Test
    fun skritGoblinDawdler() = scenario {
        alice.graveyard("Skrit, Goblin Dawdler")
        beginStep(TurnStep.Upkeep)
        resolve()
        alice.answers(Answer.YesNo(true))
        expect { alice.hand has "Skrit, Goblin Dawdler" }
    }

    /** Vengeroot: your second creature spell this turn may bring it back from the graveyard; haste. */
    // CR 603.4
    @Test
    fun vengeroot() = scenario {
        alice.hand("Tinkered Mite", "Gearwing Glider")
        alice.graveyard("Vengeroot")
        alice.casts("Tinkered Mite")
        expect { stack.size shouldBe 1 }
        allPass()
        alice.casts("Gearwing Glider")
        expect { stack.size shouldBe 2 }
        allPass()
        alice.answers(Answer.YesNo(true))
        expect {
            alice.battlefield has "Vengeroot"
            "Vengeroot" hasKeyword Keyword.Haste
        }
    }

    /** Wyrm of Ashen Graves: cast from the graveyard for {B}{B} and two creatures sacrificed. */
    // CR 118.9, 601.2h
    @Test
    fun wyrmOfAshenGraves() = scenario {
        alice.battlefield("Swamp", "Swamp", "Elvish Seer", "Thicketwood Elves")
        alice.graveyard("Wyrm of Ashen Graves")
        alice.casts("Wyrm of Ashen Graves", choosing = listOf("Elvish Seer", "Thicketwood Elves"))
        expect {
            stack.names shouldBe listOf("Wyrm of Ashen Graves")
            alice.graveyard has "Elvish Seer"
            alice.graveyard has "Thicketwood Elves"
            "Swamp".tapped shouldBe true
        }
        allPass()
        expect {
            alice.battlefield has "Wyrm of Ashen Graves"
            "Wyrm of Ashen Graves" hasKeyword Keyword.Flying
        }
    }

    /** Dunescour Digger: you may play lands from your graveyard. */
    // CR 305.1
    @Test
    fun dunescourDigger() = scenario {
        alice.battlefield("Dunescour Digger")
        alice.graveyard("Forest", "Island")
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
        assertFailsWith<IllegalArgumentException> { alice.plays("Island") }
    }

    /** Ossira: casting a historic spell returns a creature card with mana value 3 or less from your graveyard. */
    // CR 700.6
    @Test
    fun ossiraAncestralHerald() = scenario {
        alice.battlefield("Ossira, Ancestral Herald", "Mountain")
        alice.hand("Sun Band")
        alice.graveyard("Elvish Seer")
        alice.casts("Sun Band")
        // The only legal target, chosen at once
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("Elvish Seer"))))
        allPass()
        expect { alice.battlefield has "Elvish Seer" }
    }

    /** Mirebloat: grows when land cards hit your graveyard; remove three counters to return it to hand; leaving, it
     * returns up to three land cards from your graveyard to your hand. */
    @Test
    fun mirebloatGrows() = scenario {
        alice.battlefield("Mirebloat, the Undermuck", "Rancid Imp")
        alice.hand("Forest")
        alice.activates("Rancid Imp", choosing = listOf("Forest"))
        allPass()
        expect {
            "Mirebloat, the Undermuck".counters(plusOne) shouldBe 1
            "Mirebloat, the Undermuck".power shouldBe 4
            "Mirebloat, the Undermuck" hasKeyword Keyword.Trample
        }
    }

    @Test
    fun mirebloatReturnsToHand() = scenario {
        alice.battlefield("Mirebloat, the Undermuck", counters = listOf(plusOne to 3))
        alice.graveyard("Forest", "Island")
        alice.activates("Mirebloat, the Undermuck")
        allPass()
        expect { alice.hand has "Mirebloat, the Undermuck" }
        // The leaves-the-battlefield trigger
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("Forest"), target("Island"))))
        allPass()
        expect {
            alice.hand has "Forest"
            alice.hand has "Island"
        }
    }

    /** The Bogmaw Horror: upkeep, sacrifice it unless you sacrifice a land; an extra land each turn; land cards to your
     * graveyard draw a card. */
    // CR 118.12a
    @Test
    fun bogmawHorrorUpkeepSacrificingALand() = scenario {
        alice.battlefield("The Bogmaw Horror", "Forest")
        alice.library("Island")
        beginStep(TurnStep.Upkeep)
        resolve()
        alice.answers(Answer.YesNo(true))
        expect {
            alice.graveyard has "Forest"
            alice.battlefield has "The Bogmaw Horror"
            stack.names shouldBe listOf("ability of The Bogmaw Horror")
        }
        resolve()
        expect { alice.hand.names shouldBe listOf("Island") }
    }

    @Test
    fun bogmawHorrorUpkeepDeclined() = scenario {
        alice.battlefield("The Bogmaw Horror", "Forest")
        beginStep(TurnStep.Upkeep)
        resolve()
        alice.answers(Answer.YesNo(false))
        expect {
            alice.graveyard has "The Bogmaw Horror"
            alice.battlefield has "Forest"
        }
    }

    // CR 305.2
    @Test
    fun bogmawHorrorExtraLand() = scenario {
        alice.battlefield("The Bogmaw Horror")
        alice.hand("Forest", "Swamp", "Island")
        alice.plays("Forest")
        alice.plays("Swamp")
        assertFailsWith<IllegalArgumentException> { alice.plays("Island") }
    }

    /** Cinder Gatewarden: another creature of yours entering deals 1 damage to each opponent; unearth {R}. */
    @Test
    fun cinderGatewardenPings() = scenario {
        alice.battlefield("Cinder Gatewarden", "Forest")
        alice.hand("Elvish Seer")
        alice.casts("Elvish Seer")
        allPass()
        allPass()
        expect { bob.life shouldBe 19 }
    }

    // CR 702.84a
    @Test
    fun cinderGatewardenUnearth() = scenario {
        alice.battlefield("Mountain")
        alice.graveyard("Cinder Gatewarden")
        alice.activates("Cinder Gatewarden")
        allPass()
        expect {
            alice.battlefield has "Cinder Gatewarden"
            "Cinder Gatewarden" hasKeyword Keyword.Haste
        }
        advanceTo(TurnStep.End)
        allPass()
        expect { alice.exile has "Cinder Gatewarden" }
    }

    // CR 702.84a: if it would leave the battlefield, it's exiled instead
    @Test
    fun cinderGatewardenUnearthedIsExiledInsteadOfDying() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.graveyard("Cinder Gatewarden")
        alice.hand("Storm Spark")
        alice.activates("Cinder Gatewarden")
        allPass()
        alice.casts("Storm Spark", target = "Cinder Gatewarden")
        allPass()
        expect {
            alice.exile has "Cinder Gatewarden"
            alice.graveyard lacks "Cinder Gatewarden"
        }
    }

    // CR 702.84a, 614.1: sacrificed as a cost, it's exiled instead too
    @Test
    fun cinderGatewardenUnearthedSacrificedAsACost() = scenario {
        alice.battlefield("Mountain", "Corpse Gnawer")
        alice.graveyard("Cinder Gatewarden")
        alice.activates("Cinder Gatewarden")
        allPass()
        alice.activates("Corpse Gnawer", choosing = listOf("Cinder Gatewarden"))
        expect {
            alice.exile has "Cinder Gatewarden"
            alice.graveyard lacks "Cinder Gatewarden"
        }
    }
}
