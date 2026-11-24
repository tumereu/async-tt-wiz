package arcanum.engine.sets.neo

import arcanum.api.CounterKind
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dft.yes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

/** Chunk 8 of NEO: "you control an artifact and an enchantment", an "If it's a … instead" on the target, a creature that is something else while
 * tapped, loyalty abilities at instant speed the turn a planeswalker entered, a repeated sacrifice process with three players, an ability granted
 * while modified, an activation restriction on an attacking modified creature, a cost reduced per creature tapped, costs changed per target, two
 * spells chosen from hand and graveyard under a total mana value, an Aura searched for and put onto the battlefield attached, and "the next spell
 * you cast this turn costs {1} less". */
class NeoChunk08ConditionsAndCostsScenariosTest {
    private val kami = Cards.invent("Kami of Hidden Truths", "{3}{B}", "Creature — Spirit",
        "When this creature enters, if you control an artifact and an enchantment, you draw a card and you gain 1 life.", "3/4")
    private val mandate = Cards.invent("Regent's Mandate", "{W}", "Instant",
        "Target creature gets +2/+2 until end of turn. If it's an enchantment creature or legendary creature, instead put a +1/+1 counter on it and it gets +1/+1 until end of turn.")
    private val operative = Cards.invent("Neon Operative", "{3}{U}", "Creature — Human Ninja", """
        As long as this creature is tapped, it's a Human Citizen with base power and toughness 1/1 and can't be blocked.
        {2}{U}: Untap this creature.
    """, "3/4")
    private val empress = Cards.invent("The Roaming Empress", "{2}{W}{W}", "Legendary Planeswalker", """
        Flash
        As long as The Roaming Empress entered this turn, you may activate her loyalty abilities any time you could cast an instant.
        [+1]: Put a +1/+1 counter on up to one target creature. It gains first strike until end of turn.
        [−1]: Create a 2/2 white Samurai creature token with vigilance.
        [−2]: Exile target tapped creature. You gain 2 life.
    """, loyalty = "3")
    private val dread = Cards.invent("Summon Dread", "{1}{B}{B}{B}{B}", "Sorcery",
        "Target opponent sacrifices a creature of their choice. If they can't, they lose 2 life and you draw a card. Then repeat this process for an enchantment and a planeswalker.")
    private val graftTender = Cards.invent("Orochi Graft-Tender", "{1}{G}", "Creature — Snake Druid", """
        {T}: Add {G}.
        As long as this creature is modified, it has "{T}: Add {G}{G}." (Equipment, Auras you control, and counters are modifications.)
    """, "1/1")
    private val rasa = Cards.invent("Rasa-Rasa, Pupil of Ryusei", "{1}{R}", "Legendary Creature — Goblin Samurai", """
        {R}: Creatures you control gain haste until end of turn.
        {3}{R}{R}: Create a 5/5 red Dragon Spirit creature token with flying. Activate only if you control an attacking modified creature. (Equipment, Auras you control, and counters are modifications.)
    """, "2/2")
    private val singularity = Cards.invent("Volatile Singularity", "{8}{R}{R}", "Sorcery", """
        As an additional cost to cast this spell, you may tap any number of untapped creatures you control. This spell costs {1} less to cast for each creature tapped this way.
        Volatile Singularity deals 10 damage to any target.
    """)
    private val akari = Cards.invent("Akari, Dusk-Crowned", "{1}{U}{R}{W}", "Legendary Creature — Kirin Spirit", """
        Flying, trample
        Spells you cast cost {1} less to cast for each target.
        Spells your opponents cast cost {1} more to cast for each target.
    """, "4/4")
    private val catastrophe = Cards.invent("Summon Catastrophe", "{1}{R}{R}{R}{R}", "Instant",
        "You may cast up to two instant and/or sorcery spells with total mana value 6 or less from your graveyard and/or hand without paying their mana costs. If those spells would be put into your graveyard, exile them instead. Exile Summon Catastrophe.")
    private val brightPaws = Cards.invent("Bright-Paws, Empress's Voice", "{1}{W}", "Legendary Creature — Fox Advisor",
        "Whenever an Aura you control enters, if you cast it, you may search your library for an Aura card with mana value less than or equal to that Aura and with a different name than each Aura you control, put that card onto the battlefield attached to Bright-Paws, then shuffle.", "2/2")
    private val matchless = Cards.invent("Matchless Samurai", "{2}{R}", "Creature — Human Samurai", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Whenever a Samurai or Warrior you control attacks alone, the next spell you cast this turn costs {1} less to cast.
    """, "2/3")

    private val relic = Cards.invent("Neo Cond Relic", "{2}", "Artifact Enchantment", "")
    private val trinket = Cards.invent("Neo Cond Trinket", "{1}", "Artifact", "")
    private val ward = Cards.invent("Neo Cond Ward", "{1}{W}", "Enchantment", "")
    private val bear = Cards.invent("Neo Cond Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val hero = Cards.invent("Neo Cond Hero", "{1}{W}", "Legendary Creature — Human Soldier", "", "2/2")
    private val bolt = Cards.invent("Neo Cond Bolt", "{1}{R}", "Instant", "Neo Cond Bolt deals 2 damage to any target.")
    private val doublePump = Cards.invent("Neo Cond Double Pump", "{2}{W}", "Instant", "Up to two target creatures each get +2/+2 until end of turn.")
    private val musing = Cards.invent("Neo Cond Musing", "{1}{U}", "Sorcery", "Draw two cards.")
    private val drain = Cards.invent("Neo Cond Drain", "{2}{B}", "Sorcery", "Each opponent loses 3 life.")
    private val tome = Cards.invent("Neo Cond Tome", "{4}{U}", "Sorcery", "Draw three cards.")
    private val growth = Cards.invent("Neo Cond Growth", "{1}{G}", "Instant", "Target creature gets +3/+3 until end of turn.")
    private val sprout = Cards.invent("Neo Cond Sprout", "{1}{G}", "Instant", "Target creature gets +1/+1 until end of turn.")
    private val auraA = Cards.invent("Neo Paws Aura A", "{1}{W}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets +1/+1.")
    private val auraB = Cards.invent("Neo Paws Aura B", "{W}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets +0/+2.")
    private val auraBig = Cards.invent("Neo Paws Aura Big", "{2}{W}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets +3/+3.")

    // CR 603.4, 205.2b: one artifact enchantment is both an artifact and an enchantment you control
    @Test
    fun kamiCountsAnArtifactEnchantmentAsBoth() = scenario {
        alice.battlefield(relic, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(kami)
        alice.library("Island")
        alice.casts(kami)
        resolveAll()
        expect {
            alice.hand has "Island"
            alice.life shouldBe 21
        }
    }

    // CR 603.4: an artifact alone isn't enough; the trigger doesn't even go on the Stack
    @Test
    fun kamiNeedsAnEnchantmentToo() = scenario {
        alice.battlefield(trinket, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(kami)
        alice.library("Island")
        alice.casts(kami)
        resolveAll()
        expect {
            alice.hand lacks "Island"
            alice.life shouldBe 20
        }
    }

    // CR 608.2h: a legendary target gets the counter and +1/+1 instead of +2/+2
    @Test
    fun mandateGivesALegendaryCreatureACounterInstead() = scenario {
        alice.battlefield(hero, "Plains")
        alice.hand(mandate)
        alice.casts(mandate, target = hero)
        resolveAll()
        expect {
            hero.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            hero.power shouldBe 4
        }
    }

    // CR 608.2h: any other creature gets +2/+2 and no counter
    @Test
    fun mandatePumpsAnOrdinaryCreature() = scenario {
        alice.battlefield(bear, "Plains")
        alice.hand(mandate)
        alice.casts(mandate, target = bear)
        resolveAll()
        expect {
            bear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
            bear.power shouldBe 4
            bear.toughness shouldBe 4
        }
    }

    // CR 205.1b, 613.4b, 509.1b: attacking taps it; then it's a 1/1 Human Citizen (no longer a Ninja) and can't be blocked
    @Test
    fun operativeIsAnUnblockableCitizenWhileTapped() = scenario {
        alice.battlefield(operative)
        bob.battlefield(bear)
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(operative)
        expect {
            operative.power shouldBe 1
            operative.toughness shouldBe 1
            assertEquals(setOf(Subtype("Human"), Subtype("Citizen")), operative.subtypes.toSet())
        }
        advanceTo(TurnStep.DeclareBlockers)
        assertFails { bob.blocks(bear to operative) }
    }

    // CR 611.3a: untapped, it's a 3/4 Human Ninja again
    @Test
    fun operativeIsItselfUntapped() = scenario {
        alice.battlefield(operative, "Island", "Island", "Island")
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(operative)
        alice.activates(operative)
        resolveAll()
        expect {
            operative.power shouldBe 3
            assertEquals(setOf(Subtype("Human"), Subtype("Ninja")), operative.subtypes.toSet())
        }
    }

    // CR 606.3: cast with flash on an opponent's turn, she can use a loyalty ability at once, at instant speed
    @Test
    fun empressActivatesOnAnOpponentsTurnTheTurnSheEnters() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(empress)
        turn(number = 2, active = bob, priority = alice)
        alice.casts(empress)
        resolveAll()
        // CR 117.3b: after it resolves, Bob (the active player) gets priority first
        bob.passes()
        alice.activates(empress, ability = 1)
        resolveAll()
        expect {
            alice.battlefield has "Samurai Token"
            empress.loyalty shouldBe 2
        }
    }

    // CR 606.3: on a turn she didn't enter, her loyalty abilities are sorcery-speed again
    @Test
    fun empressIsSorcerySpeedOnALaterTurn() = scenario {
        alice.battlefield(empress, counters = listOf(CounterKind.Loyalty to 3))
        turn(number = 2, active = bob, priority = alice)
        val refused = assertFails { alice.activates(empress, ability = 1) }
        assertFalse(refused.message.orEmpty().contains("priority"), refused.message)
    }

    // CR 701.21a, 609.3, 115.1: Bob sacrifices his creature and his enchantment, has no planeswalker, so he loses 2 life and Alice draws one card;
    // Carol is untouched
    @Test
    fun dreadRepeatsTheProcessForEachType() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(dread)
        alice.library("Island", "Island", "Island")
        bob.battlefield(bear, ward)
        carol.battlefield(hero)
        alice.casts(dread, target = "bob")
        resolveAll()
        expect {
            bob.graveyard has bear
            bob.graveyard has ward
            bob.life shouldBe 18
            alice.hand.size shouldBe 1
            carol.battlefield has hero
            carol.life shouldBe 20
        }
    }

    // CR 700.9: with a +1/+1 counter it's modified and has "{T}: Add {G}{G}." besides its own mana ability
    @Test
    fun graftTenderMakesTwoGreenWhenModified() = scenario {
        alice.battlefield(graftTender, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.activates(graftTender, ability = 1)
        expect { alice.pool.size shouldBe 2 }
    }

    // CR 700.9: unmodified, it has only its own ability
    @Test
    fun graftTenderHasOneManaAbilityUnmodified() = scenario {
        alice.battlefield(graftTender)
        assertFails { alice.activates(graftTender, ability = 1) }
    }

    // CR 602.5b, 700.9: an attacking creature with a counter lets the Dragon ability be activated
    @Test
    fun rasaMakesADragonWithAnAttackingModifiedCreature() = scenario {
        alice.battlefield(rasa, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.battlefield(bear, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(bear)
        alice.activates(rasa, ability = 1)
        resolveAll()
        expect { alice.battlefield has "Dragon Spirit Token" }
    }

    // CR 602.5b: an attacking creature that isn't modified doesn't
    @Test
    fun rasaNeedsTheAttackerModified() = scenario {
        alice.battlefield(rasa, bear, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(bear)
        assertFails { alice.activates(rasa, ability = 1) }
    }

    // CR 601.2b, 601.2f: tapping two creatures takes {2} off: eight lands pay {6}{R}{R}
    @Test
    fun singularityCostsLessForEachCreatureTapped() = scenario {
        alice.battlefield(bear, hero, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(singularity)
        alice.casts(singularity, target = "bob", choosing = listOf(bear, hero))
        resolveAll()
        expect {
            bob.life shouldBe 10
            bear.tapped shouldBe true
            hero.tapped shouldBe true
        }
    }

    // CR 601.2c, 601.2f: two targets take {2} off Alice's {2}{W} spell: one Plains pays it
    @Test
    fun akariReducesYourSpellPerTarget() = scenario {
        alice.battlefield(akari, bear, hero, "Plains")
        alice.hand(doublePump)
        alice.casts(doublePump, targets = listOf(bear, hero))
        resolveAll()
        expect {
            bear.power shouldBe 4
            hero.power shouldBe 4
        }
    }

    // CR 601.2f: an opponent's one-target {1}{R} spell costs {2}{R}: three of Bob's four lands are tapped for it
    @Test
    fun akariTaxesAnOpponentsSpellPerTarget() = scenario {
        alice.battlefield(akari)
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        bob.hand(bolt)
        turn(number = 2, active = bob)
        bob.casts(bolt, target = "alice")
        payAsSuggested()
        resolveAll()
        expect { alice.life shouldBe 18 }
        assertEquals(3, tappedOf(bob))
    }

    // CR 608.2g, 614.1a: a sorcery from the hand and one from the graveyard (total mana value 5) are cast free, then exiled, and so is Catastrophe
    @Test
    fun catastropheCastsTwoSpellsFromHandAndGraveyard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(catastrophe, musing)
        alice.graveyard(drain)
        alice.library("Island", "Island", "Island")
        alice.casts(catastrophe)
        allPass()
        val options = (pending as Decision.ChooseObjects).options
        assertEquals(2, options.size)
        alice.chooses(musing, drain)
        if (pending is Decision.ChooseObjects) alice.chooses(musing, drain)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            bob.life shouldBe 17
            alice.exile has musing
            alice.exile has drain
            alice.exile has catastrophe
        }
    }

    // CR 608.2g: the total mana value limits the pair (2 + 5 = 7 is over 6)
    @Test
    fun catastropheKeepsToTheTotalManaValue() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(catastrophe, musing, tome)
        alice.casts(catastrophe)
        allPass()
        assertFails { alice.chooses(musing, tome) }
    }

    // CR 603.4, 201.2, 303.4f: casting a two-mana Aura finds the one-mana Aura with another name and puts it onto Bright-Paws; the same-named
    // Aura and the three-mana one aren't offered
    @Test
    fun brightPawsFetchesASmallerDifferentAura() = scenario {
        alice.battlefield(brightPaws, bear, "Plains", "Plains")
        alice.hand(auraA)
        alice.library(auraA, auraBig, auraB)
        alice.casts(auraA, target = bear)
        resolveAll()
        yes()
        assertEquals(1, (pending as Decision.ChooseObjects).options.size)
        alice.chooses(auraB)
        resolveAll()
        expect {
            brightPaws.toughness shouldBe 4
            bear.power shouldBe 3
        }
    }

    // CR 506.5, 611.2f: attacking alone makes Alice's next spell {1} cheaper ({G} for the {1}{G} spell), but only that one: the second costs {1}{G}
    @Test
    fun matchlessReducesOnlyTheNextSpell() = scenario {
        alice.battlefield(matchless, "Forest", "Forest", "Forest")
        alice.hand(growth, sprout)
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(matchless)
        resolveAll()
        alice.casts(growth, target = matchless)
        payAsSuggested()
        resolveAll()
        expect { matchless.power shouldBe 5 }
        assertEquals(2, tappedOf(alice))
        alice.casts(sprout, target = matchless)
        payAsSuggested()
        resolveAll()
        expect { matchless.power shouldBe 6 }
        assertEquals(4, tappedOf(alice))
    }

    private fun Scenario.payAsSuggested() {
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
    }

    private fun Scenario.tappedOf(player: ScenarioPlayer): Int =
        state.battlefield.count { state.obj(it)?.tapped == true && state.obj(it)?.controller == player.id }
}
