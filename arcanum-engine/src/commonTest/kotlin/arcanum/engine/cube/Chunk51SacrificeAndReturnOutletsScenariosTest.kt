package arcanum.engine.cube

import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Chunk 51 in play: creatures with costs that return, sacrifice or discard: untappers, sacrifice outlets, Food. */
class Chunk51SacrificeAndReturnOutletsScenariosTest {
    /** Thicket Ranger: return a Forest you control to hand: untap target creature, once each turn. */
    // CR 602.5b
    @Test
    fun thicketRanger() = scenario {
        alice.battlefield("Thicket Ranger", "Forest", "Forest")
        alice.battlefield("Elvish Seer", tapped = true)
        alice.activates("Thicket Ranger", target = "Elvish Seer", choosing = listOf("Forest"))
        expect { alice.hand has "Forest" }
        allPass()
        expect { "Elvish Seer".tapped shouldBe false }
        assertFailsWith<IllegalArgumentException> { alice.activates("Thicket Ranger", target = "Elvish Seer", choosing = listOf("Forest")) }
    }

    /** Glade Sprite Ranger: flash, flying, protection from blue; the same untap ability. */
    @Test
    fun gladeSpriteRanger() = scenario {
        alice.battlefield("Glade Sprite Ranger", "Forest")
        alice.battlefield("Elvish Seer", tapped = true)
        expect { "Glade Sprite Ranger" hasKeyword Keyword.Flying }
        alice.activates("Glade Sprite Ranger", target = "Elvish Seer", choosing = listOf("Forest"))
        allPass()
        expect { "Elvish Seer".tapped shouldBe false }
    }

    // CR 702.16b: protection from blue: blue spells can't target it
    @Test
    fun gladeSpriteRangerProtection() = scenario {
        alice.battlefield("Glade Sprite Ranger", "Elvish Seer")
        bob.battlefield("Island", "Island")
        bob.hand("Click Back")
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.casts("Click Back", target = "Glade Sprite Ranger") }
    }

    // CR 702.8a
    @Test
    fun gladeSpriteRangerFlash() = scenario {
        turn(number = 2, active = bob, priority = alice)
        alice.battlefield("Forest", "Forest")
        alice.hand("Glade Sprite Ranger")
        alice.casts("Glade Sprite Ranger")
        allPass()
        expect { alice.battlefield has "Glade Sprite Ranger" }
    }

    /** Barkweave Symbiote: return an Elf you control to hand: untap target creature, once each turn. */
    @Test
    fun barkweaveSymbiote() = scenario {
        alice.battlefield("Barkweave Symbiote", "Elvish Seer")
        alice.battlefield("Thicketwood Elves", tapped = true)
        alice.activates("Barkweave Symbiote", target = "Thicketwood Elves", choosing = listOf("Elvish Seer"))
        allPass()
        expect {
            alice.hand has "Elvish Seer"
            "Thicketwood Elves".tapped shouldBe false
        }
    }

    /** Tibbet Gearwhistle: {W}, {T}, sacrifice an artifact: find an artifact with mana value one more, onto the
     * battlefield. */
    @Test
    fun tibbetGearwhistle() = scenario {
        alice.battlefield("Tibbet Gearwhistle", "Plains", "Tinkered Mite")
        alice.library("Lotus Frond", "Sun Band", "Forest")
        alice.activates("Tibbet Gearwhistle", choosing = listOf("Tinkered Mite"))
        expect {
            alice.graveyard has "Tinkered Mite"
            "Tibbet Gearwhistle".tapped shouldBe true
        }
        allPass()
        // Only Sun Band has mana value 1
        val search = decision<Decision.ChooseObjects>()
        search.options shouldBeOnly card("Sun Band")
        alice.chooses("Sun Band")
        expect {
            alice.battlefield has "Sun Band"
            alice.library.size shouldBe 2
        }
    }

    private infix fun List<arcanum.engine.state.ObjectId>.shouldBeOnly(id: arcanum.engine.state.ObjectId) =
        kotlin.test.assertEquals(listOf(id), this)

    /** Gloomwing Skulker: flying; any player may discard a card to give it -2/-0 until end of turn. */
    // CR 602.2
    @Test
    fun gloomwingSkulker() = scenario {
        alice.battlefield("Gloomwing Skulker")
        bob.hand("Forest")
        expect { "Gloomwing Skulker" hasKeyword Keyword.Flying }
        alice.passes()
        bob.activates("Gloomwing Skulker", choosing = listOf("Forest"))
        allPass()
        expect {
            bob.graveyard has "Forest"
            "Gloomwing Skulker".power shouldBe 1
        }
    }

    /** Rancid Imp: discard a card: flying until end of turn; with threshold +1/+1 and can't block. */
    @Test
    fun rancidImpFlies() = scenario {
        alice.battlefield("Rancid Imp")
        alice.hand("Forest")
        alice.activates("Rancid Imp", choosing = listOf("Forest"))
        allPass()
        expect { "Rancid Imp" hasKeyword Keyword.Flying }
    }

    @Test
    fun rancidImpThreshold() = scenario {
        alice.battlefield("Rancid Imp")
        alice.graveyard("Forest", "Forest", "Forest", "Island", "Island", "Island", "Swamp")
        expect {
            "Rancid Imp".power shouldBe 2
            "Rancid Imp" hasMechanic { it is Mechanic.CantBlock }
        }
    }

    @Test
    fun rancidImpWithoutThreshold() = scenario {
        alice.battlefield("Rancid Imp")
        alice.graveyard("Forest", "Forest", "Forest", "Island", "Island", "Island")
        expect {
            "Rancid Imp".power shouldBe 1
            "Rancid Imp" lacksMechanic { it is Mechanic.CantBlock }
        }
    }

    /** Entrail Augur: sacrifice a creature: scry 1. */
    // CR 701.22a
    @Test
    fun entrailAugur() = scenario {
        alice.battlefield("Entrail Augur", "Elvish Seer")
        alice.library("Forest", "Island")
        alice.activates("Entrail Augur", choosing = listOf("Elvish Seer"))
        allPass()
        alice.chooses("Forest")
        expect {
            alice.graveyard has "Elvish Seer"
            alice.library.names shouldBe listOf("Island", "Forest")
        }
    }

    /** Shadowcollar Devotee: sacrifice another creature or artifact: surveil 1. */
    // CR 701.25a
    @Test
    fun shadowcollarDevotee() = scenario {
        alice.battlefield("Shadowcollar Devotee", "Sun Band")
        alice.library("Forest", "Island")
        alice.activates("Shadowcollar Devotee", choosing = listOf("Sun Band"))
        allPass()
        alice.chooses("Forest")
        expect {
            alice.graveyard has "Sun Band"
            alice.graveyard has "Forest"
            alice.library.names shouldBe listOf("Island")
        }
    }

    // "another": it can't sacrifice itself
    @Test
    fun shadowcollarDevoteeNotItself() = scenario {
        alice.battlefield("Shadowcollar Devotee")
        assertFailsWith<IllegalArgumentException> { alice.activates("Shadowcollar Devotee", choosing = listOf("Shadowcollar Devotee")) }
    }

    /** Mossbound Keeper: sacrifice a land: target creature you control gains shroud until end of turn. */
    // CR 702.18a
    @Test
    fun mossboundKeeper() = scenario {
        alice.battlefield("Mossbound Keeper", "Forest", "Elvish Seer")
        bob.battlefield("Mountain")
        bob.hand("Storm Spark")
        alice.activates("Mossbound Keeper", target = "Elvish Seer", choosing = listOf("Forest"))
        allPass()
        expect {
            "Elvish Seer" hasKeyword Keyword.Shroud
            alice.graveyard has "Forest"
        }
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.casts("Storm Spark", target = "Elvish Seer") }
    }

    /** Podrick Burrowfoot: another nontoken creature of yours entering makes a Food; sacrifice three Foods: return a
     * historic card from your graveyard to your hand. */
    @Test
    fun podrickMakesFood() = scenario {
        alice.battlefield("Podrick Burrowfoot", "Forest")
        alice.hand("Elvish Seer")
        alice.casts("Elvish Seer")
        allPass()
        allPass()
        expect { card("Food Token").types shouldBe listOf(arcanum.api.CardType.Artifact) }
    }

    @Test
    fun podrickReturnsAHistoricCard() = scenario {
        alice.battlefield("Podrick Burrowfoot", "Forest", "Forest", "Forest")
        alice.hand("Elvish Seer", "Thicketwood Elves", "Frosthorn Elves")
        alice.graveyard("Sun Band")
        listOf("Elvish Seer", "Thicketwood Elves", "Frosthorn Elves").forEach {
            alice.casts(it)
            allPass()
            allPass()
        }
        expect { alice.battlefield.names.count { it == "Food Token" } shouldBe 3 }
        // Exactly three Foods: they are the ones sacrificed, without asking
        alice.activates("Podrick Burrowfoot", target = "Sun Band")
        allPass()
        expect {
            alice.hand has "Sun Band"
            alice.battlefield.names.count { it == "Food Token" } shouldBe 0
        }
    }
}
