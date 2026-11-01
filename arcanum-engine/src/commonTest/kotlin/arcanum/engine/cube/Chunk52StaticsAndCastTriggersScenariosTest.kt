package arcanum.engine.cube

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Chunk 52 in play: static abilities (cost changes, ward, "can't be activated", counters replaced) and cast
 * triggers. */
class Chunk52StaticsAndCastTriggersScenariosTest {
    private val plusOne = CounterKind.PowerToughness(1, 1)
    private val minusOne = CounterKind.PowerToughness(-1, -1)

    /** Briar Sentry: doesn't untap in your untap step; casting a green spell, you may untap it. */
    @Test
    fun briarSentryUntapsOnGreenSpells() = scenario {
        alice.battlefield("Briar Sentry", tapped = true)
        alice.battlefield("Forest")
        alice.hand("Elvish Seer")
        alice.casts("Elvish Seer")
        allPass()
        alice.answers(Answer.YesNo(true))
        expect { "Briar Sentry".tapped shouldBe false }
    }

    // CR 502.3
    @Test
    fun briarSentryStaysTapped() = scenario {
        alice.battlefield("Briar Sentry", tapped = true)
        alice.battlefield("Forest", tapped = true)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect {
            "Briar Sentry".tapped shouldBe true
            "Forest".tapped shouldBe false
        }
    }

    /** Scrapquilt Automaton: ward {2}; casting an artifact spell puts a +1/+1 counter on it. */
    @Test
    fun scrapquiltAutomatonGrows() = scenario {
        alice.battlefield("Scrapquilt Automaton")
        alice.hand("Tinkered Mite")
        alice.casts("Tinkered Mite")
        allPass()
        expect { "Scrapquilt Automaton".counters(plusOne) shouldBe 1 }
    }

    // CR 702.21a: ward: the spell is countered unless its controller pays {2}
    @Test
    fun scrapquiltAutomatonWard() = scenario {
        alice.battlefield("Scrapquilt Automaton")
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = "Scrapquilt Automaton")
        expect { stack.size shouldBe 2 }
        // Bob could pay {2}, and declines
        allPass()
        bob.answers(Answer.YesNo(false))
        expect {
            bob.graveyard has "Storm Spark"
            stack.isEmpty()
            alice.battlefield has "Scrapquilt Automaton"
        }
    }

    @Test
    fun scrapquiltAutomatonWardPaid() = scenario {
        alice.battlefield("Scrapquilt Automaton")
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand("Storm Spark")
        alice.passes()
        bob.casts("Storm Spark", target = "Scrapquilt Automaton", paying = listOf("Mountain"))
        allPass()
        bob.answers(Answer.YesNo(true))
        bob.answers(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        allPass()
        expect { alice.graveyard has "Scrapquilt Automaton" }
    }

    /** Novice Flamecaller: casting an instant or sorcery makes a 1/1 red Elemental. */
    @Test
    fun noviceFlamecaller() = scenario {
        alice.battlefield("Novice Flamecaller", "Mountain")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = "bob")
        allPass()
        expect {
            card("Elemental Token").power shouldBe 1
            card("Elemental Token").colors shouldBe listOf(arcanum.api.Color.Red)
        }
    }

    /** Seraphine: first strike; noncreature spells cost {1} more. */
    @Test
    fun seraphineTaxesNoncreatureSpells() = scenario {
        alice.battlefield("Seraphine, Keeper of the Gate", "Mountain", "Mountain", "Forest")
        alice.hand("Storm Spark", "Elvish Seer")
        expect { "Seraphine, Keeper of the Gate" hasKeyword Keyword.FirstStrike }
        alice.casts("Storm Spark", target = "bob")
        expect {
            "Mountain".tapped shouldBe true
            card("Mountain", 1).tapped shouldBe true
        }
        allPass()
        // A creature spell costs as printed
        alice.casts("Elvish Seer")
        expect { stack.names shouldBe listOf("Elvish Seer") }
    }

    @Test
    fun seraphineNeedsTheExtraMana() = scenario {
        alice.battlefield("Seraphine, Keeper of the Gate", "Mountain")
        alice.hand("Storm Spark")
        assertFailsWith<IllegalArgumentException> { alice.casts("Storm Spark", target = "bob", paying = listOf("Mountain")) }
    }

    /** Mender of Afflictions: one fewer -1/-1 counter is put on your creatures. */
    // CR 614.1a, 702.79a, 122.6: counters a permanent enters with are put on it
    @Ignore("engine: replacement effects on counters a permanent enters with (CR 122.6)")
    @Test
    fun menderOfAfflictionsWithPersist() = scenario {
        alice.battlefield("Mender of Afflictions", "Corpse Gnawer", "Haven Warden")
        alice.activates("Corpse Gnawer", choosing = listOf("Haven Warden"))
        allPass()
        allPass()
        expect {
            alice.battlefield has "Haven Warden"
            "Haven Warden".counters(minusOne) shouldBe 0
        }
    }

    // CR 702.80a: wither damage puts -1/-1 counters, one fewer here
    @Test
    fun menderOfAfflictionsWithWither() = scenario {
        arcanum.engine.cards.Cards.invent("Blight Hound", "{1}{B}", "Creature — Dog", "Wither", "2/2")
        alice.battlefield("Mender of Afflictions", "Seer of Harvestide")
        bob.battlefield("Blight Hound")
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks("Blight Hound")
        allPass()
        alice.blocks("Seer of Harvestide" to "Blight Hound")
        advanceTo(TurnStep.EndOfCombat)
        expect {
            "Seer of Harvestide".counters(minusOne) shouldBe 1
            alice.battlefield has "Seer of Harvestide"
        }
    }

    /** Shalecoil Wyrm: {X}: enters with X +1/+1 counters; reach, trample, protection from multicolored. */
    // CR 107.3m
    @Test
    fun shalecoilWyrm() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand("Shalecoil Wyrm")
        alice.casts("Shalecoil Wyrm", x = 3)
        allPass()
        expect {
            "Shalecoil Wyrm".counters(plusOne) shouldBe 3
            "Shalecoil Wyrm".power shouldBe 3
            "Shalecoil Wyrm" hasKeyword Keyword.Reach
            "Shalecoil Wyrm" hasKeyword Keyword.Trample
        }
    }

    /** Ysolde: +1/+0 for each instant and sorcery card in your graveyard; the first discard each turn draws that many. */
    @Test
    fun ysoldeGrows() = scenario {
        alice.battlefield("Ysolde, the Unbound Scholar")
        alice.graveyard("Storm Spark", "Cinder Bolt", "Forest")
        expect { "Ysolde, the Unbound Scholar".power shouldBe 2 }
    }

    @Test
    fun ysoldeDrawsOnTheFirstDiscard() = scenario {
        alice.battlefield("Ysolde, the Unbound Scholar", "Rancid Imp")
        alice.hand("Forest", "Swamp")
        alice.library("Island", "Plains")
        alice.activates("Rancid Imp", choosing = listOf("Forest"))
        allPass()
        expect { alice.hand.names shouldBe listOf("Swamp", "Island") }
        allPass()
        // The second discard this turn: no trigger
        alice.activates("Rancid Imp", choosing = listOf("Swamp"))
        expect { stack.size shouldBe 1 }
    }

    /** Silencing Automaton: name a nonland card; activated abilities of sources with that name can't be activated. */
    @Test
    fun silencingAutomaton() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand("Silencing Automaton")
        bob.battlefield("Corpse Gnawer", "Elvish Seer")
        alice.casts("Silencing Automaton")
        allPass()
        decision<Decision.ChooseCardName>()
        alice.answers(Answer.CardName("Corpse Gnawer"))
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.activates("Corpse Gnawer", choosing = listOf("Elvish Seer")) }
    }

    /** Crafty Pawnbroker: choose a nonland permanent as it enters: its activated abilities can't be activated, and the
     * Pawnbroker has them. */
    @Test
    fun craftyPawnbroker() = scenario {
        alice.battlefield("Island", "Plains", "Elvish Seer")
        alice.hand("Crafty Pawnbroker")
        bob.battlefield("Corpse Gnawer", "Thicketwood Elves")
        alice.casts("Crafty Pawnbroker")
        allPass()
        alice.chooses("Corpse Gnawer")
        // Pawnbroker's copy of "Sacrifice a creature: Put a +1/+1 counter on this creature."
        alice.activates("Crafty Pawnbroker", choosing = listOf("Elvish Seer"))
        allPass()
        expect { "Crafty Pawnbroker".counters(plusOne) shouldBe 1 }
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.activates("Corpse Gnawer", choosing = listOf("Thicketwood Elves")) }
    }

    /** Spiritwrangler: a creature exiled from the battlefield grows it; at your end step, you may blink another
     * creature of yours. */
    @Test
    fun spiritwrangler() = scenario {
        alice.battlefield("Spiritwrangler", "Elvish Seer")
        beginStep(TurnStep.End)
        // The only other creature: the target is chosen at once
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("Elvish Seer"))))
        resolve()
        alice.answers(Answer.YesNo(true))
        expect {
            alice.battlefield has "Elvish Seer"
            stack.names shouldBe listOf("ability of Spiritwrangler")
        }
        resolve()
        expect { "Spiritwrangler".counters(plusOne) shouldBe 1 }
    }

    /** Gossamer Coterie: flash, flying; entering, look at a player's hand; a nonland card you choose goes to the
     * bottom of their library and they draw. */
    @Test
    fun gossamerCoterie() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Gossamer Coterie")
        bob.hand("Storm Spark", "Cinder Bolt", "Forest")
        bob.library("Swamp")
        alice.casts("Gossamer Coterie")
        allPass()
        alice.answers(Answer.Targets(listOf(target("bob"))))
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(true))
        alice.chooses("Storm Spark")
        expect {
            bob.library.names shouldBe listOf("Storm Spark")
            bob.hand.names shouldBe listOf("Cinder Bolt", "Forest", "Swamp")
            "Gossamer Coterie" hasKeyword Keyword.Flying
        }
    }

    /** Ilsa: landfall adds one mana of any colour; the second time this turn it also finds an Elf or Elemental. */
    @Test
    fun ilsaVerdantSeeker() = scenario {
        // The Bogmaw Horror gives the second land play
        alice.battlefield("Ilsa, Verdant Seeker", "The Bogmaw Horror")
        alice.hand("Forest", "Swamp")
        alice.library("Island", "Elvish Seer", "Plains")
        alice.plays("Forest")
        allPass()
        alice.answers(Answer.ColorChoice(arcanum.api.Color.Green))
        expect {
            alice.hand.names shouldBe listOf("Swamp")
            alice.library.size shouldBe 3
        }
        assertTrue(alice.pool.size == 1)
        alice.plays("Swamp")
        allPass()
        alice.answers(Answer.ColorChoice(arcanum.api.Color.Black))
        expect {
            alice.hand has "Elvish Seer"
            alice.library.names.toSet() shouldBe setOf("Island", "Plains")
        }
    }
}
