package arcanum.engine.sets.mkm

import arcanum.api.Keyword
import arcanum.api.ObjectMark
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.tdm.Plain
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** MKM chunk 05: suspect (CR 701.60): a creature suspecting itself and then growing, a target exiled if suspected and suspected otherwise,
 * the mark handed on to a creature that entered, an optional attack trigger, a sacrifice cost of a suspected creature, a returned card
 * suspected, and all suspected creatures cleared. */
class MkmChunk05SuspectScenariosTest {
    private val sphinx = Cards.invent("Pardoning Sphinx", "{4}{W}", "Creature — Sphinx", """
        Flying
        When this creature enters, all suspected creatures are no longer suspected.
        When this creature dies, you gain 3 life and suspect up to one target creature an opponent controls. (A suspected creature has menace and can't block.)
    """, "4/3")
    private val grevik = Cards.invent("Grevik Dorn, Spirit of Verdict", "{2}{R}{W}", "Legendary Creature — Spirit Detective", """
        Double strike, vigilance
        Whenever Grevik Dorn enters or attacks, choose up to one target creature. If it's suspected, exile it. Otherwise, suspect it. (A suspected creature has menace and can't block.)
    """, "2/4")
    private val patsy = Cards.invent("Panicked Patsy", "{R}", "Creature — Goat", """
        Haste
        When this creature enters, suspect it. (It has menace and can't block.)
        Whenever one or more other creatures you control enter, if this creature is suspected, you may suspect one of the other creatures. If you do, this creature is no longer suspected.
    """, "1/1")
    private val tally = Cards.invent("Doesn't Tally", "{3}{B}{B}", "Instant",
        "Return target creature card from your graveyard to the battlefield. Suspect it. (It has menace and can't block.)")
    private val party = Cards.invent("Party of Concern", "{3}{R}", "Creature — Human Rogue",
        "When this creature enters, suspect it. Create a 2/2 white and blue Detective creature token. (A suspected creature has menace and can't block.)", "2/2")
    private val departed = Cards.invent("Presumed Departed", "{1}{B}", "Instant",
        "Until end of turn, target creature gets +2/+0 and gains \"When this creature dies, return it to the battlefield under its owner's control and suspect it.\" (A suspected creature has menace and can't block.)")
    private val felon = Cards.invent("Habitual Felon", "{1}{B}", "Creature — Human Assassin",
        "{2}{B}: If this creature is suspected, put a +1/+1 counter on it. Otherwise, suspect it. (A suspected creature has menace and can't block.)", "2/1")
    private val brute = Cards.invent("Boastful Brute", "{4}{R}", "Creature — Lizard Warrior",
        "Whenever this creature attacks, if it's not suspected, you may suspect it. (A suspected creature has menace and can't block.)", "5/5")
    private val trickster = Cards.invent("Brand-Rune Trickster", "{B}{R}", "Creature — Human Shaman", """
        When this creature enters, suspect up to one target creature you control. (A suspected creature has menace and can't block.)
        {3}{B}{R}, Sacrifice a suspected creature: Target creature gets -5/-5 until end of turn.
    """, "2/2")

    private fun Scenario.suspected(name: String): Boolean = state.obj(card(name))?.marks?.contains(ObjectMark.Suspected) == true

    // CR 701.60a: the first activation suspects it, the second (it is suspected) puts a counter on it; CR 701.60c: a suspected creature has
    // menace and can't block (the audit of the suspect mark: the mark itself carries both)
    @Test
    fun habitualFelonSuspectsItselfThenGrows() = scenario {
        alice.battlefield(felon, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        bob.battlefield(Plain.bear)
        alice.library("Swamp", "Swamp")
        bob.library("Swamp", "Swamp")
        alice.activates(felon)
        playOut()
        assertTrue(suspected(felon))
        expect { felon hasKeyword Keyword.Menace.kind }
        alice.activates(felon)
        playOut()
        expect {
            felon.power shouldBe 3
            felon hasKeyword Keyword.Menace.kind
        }
        assertTrue(suspected(felon))
        // on Bob's turn, the suspected creature can't block
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(Plain.bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { alice.blocks(felon to Plain.bear) }
    }

    // CR 701.60a: a target that isn't suspected becomes suspected as it enters
    @Test
    fun grevikSuspectsAnUnsuspectedTarget() = scenario {
        alice.battlefield("Mountain", "Mountain", "Plains", "Plains")
        alice.hand(grevik)
        bob.battlefield(Plain.bear)
        alice.casts(grevik)
        playOut(Plain.bear)
        assertTrue(suspected(Plain.bear))
        expect { Plain.bear.zone shouldBe Zone.Battlefield }
    }

    // CR 608.2b: as the attack trigger resolves, the target is suspected, so it is exiled
    @Test
    fun grevikExilesASuspectedTarget() = scenario {
        alice.battlefield(grevik, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(party)
        alice.casts(party)
        playOut()
        assertTrue(suspected(party))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(grevik)
        playOut(party)
        expect { alice.exile has party }
    }

    // CR 603.2c, 603.4: the creature that entered becomes suspected and this one stops being suspected
    @Test
    fun panickedPatsyHandsTheMarkOn() = scenario {
        alice.battlefield("Mountain", "Forest", "Forest")
        alice.hand(patsy, Plain.bear)
        alice.casts(patsy)
        playOut()
        assertTrue(suspected(patsy))
        alice.casts(Plain.bear)
        playOut(Plain.bear)
        assertTrue(suspected(Plain.bear))
        assertFalse(suspected(patsy))
        expect { patsy lacksKeyword Keyword.Menace.kind }
    }

    // CR 608.2d: declining keeps the mark where it was
    @Test
    fun panickedPatsyMayKeepTheMark() = scenario {
        alice.battlefield("Mountain", "Forest", "Forest")
        alice.hand(patsy, Plain.bear)
        alice.casts(patsy)
        playOut()
        alice.casts(Plain.bear)
        playOut()
        assertTrue(suspected(patsy))
        assertFalse(suspected(Plain.bear))
    }

    // CR 603.4: not suspected, so it triggers; you may suspect it
    @Test
    fun boastfulBruteMaySuspectItselfAsItAttacks() = scenario {
        alice.battlefield(brute)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(brute)
        playOut()
        assertTrue(suspected(brute))
        expect { brute hasKeyword Keyword.Menace.kind }
    }

    // CR 602.2b, 118.3: only a suspected creature can be sacrificed for the cost
    @Test
    fun brandRuneTricksterSacrificesTheSuspectedCreature() = scenario {
        alice.battlefield(Plain.bear, Plain.elf, "Swamp", "Swamp", "Swamp", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(trickster)
        bob.battlefield(Plain.giant)
        alice.casts(trickster, paying = listOf("Swamp", "Mountain"))
        playOut(Plain.bear)
        assertTrue(suspected(Plain.bear))
        alice.activates(trickster, target = Plain.giant)
        playOut()
        expect {
            alice.graveyard has Plain.bear
            Plain.elf.zone shouldBe Zone.Battlefield
            trickster.zone shouldBe Zone.Battlefield
            bob.graveyard has Plain.giant
        }
    }

    // CR 400.7, 608.2c: the card returned is suspected on the battlefield
    @Test
    fun doesntTallyReturnsTheCardSuspected() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(tally)
        alice.graveyard(Plain.bear)
        alice.casts(tally, target = Plain.bear)
        playOut()
        expect {
            Plain.bear.zone shouldBe Zone.Battlefield
            Plain.bear hasKeyword Keyword.Menace.kind
        }
        assertTrue(suspected(Plain.bear))
    }

    // CR 603.10a, 400.7: the granted dies trigger returns the card, and the permanent it became is suspected
    @Test
    fun presumedDepartedReturnsTheCreatureSuspected() = scenario {
        alice.battlefield(Plain.bear, "Swamp", "Swamp", "Mountain")
        alice.hand(departed, Plain.shock)
        alice.casts(departed, target = Plain.bear)
        playOut()
        expect { Plain.bear.power shouldBe 4 }
        alice.casts(Plain.shock, target = Plain.bear)
        playOut()
        expect {
            Plain.bear.zone shouldBe Zone.Battlefield
            Plain.bear.power shouldBe 2
            Plain.bear hasKeyword Keyword.Menace.kind
        }
        assertTrue(suspected(Plain.bear))
    }

    // CR 701.60a: every suspected creature, whoever controls it, stops being suspected
    @Test
    fun pardoningSphinxClearsEverySuspectedCreature() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(party, sphinx)
        alice.casts(party)
        playOut()
        assertTrue(suspected(party))
        alice.casts(sphinx)
        playOut()
        assertFalse(suspected(party))
        expect { party lacksKeyword Keyword.Menace.kind }
    }

    // CR 603.10a, 601.2c: as it dies you gain 3 life and suspect a creature of one of the opponents
    @Test
    fun pardoningSphinxDyingSuspectsAnOpponentsCreature() = scenario(players = 3) {
        alice.battlefield(sphinx, "Swamp")
        alice.hand(Plain.doom)
        bob.battlefield(Plain.bear)
        carol.battlefield(Plain.elf)
        alice.casts(Plain.doom, target = sphinx)
        playOut(Plain.elf)
        expect {
            alice.graveyard has sphinx
            alice.life shouldBe 23
        }
        assertTrue(suspected(Plain.elf))
        assertFalse(suspected(Plain.bear))
    }
}
