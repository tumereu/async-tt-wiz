package arcanum.engine.sets.one

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.dft.hits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Phyrexia: All Will Be One chunk 02 in play: toxic (CR 702.164): a creature's total toxic value with toxic granted on top of its own
 * (CR 702.164b), with double strike (CR 510.4), an extra poison counter for each toxic hit (CR 122.1f), a trigger on becoming the target of
 * a spell but not an ability (CR 115.10), a chosen colour locked into hexproof and an evasion (CR 702.11d, 509.1b), Rats revealed without an
 * upper limit, a creature for as long as another remains on the battlefield (CR 611.2b), and a spell that can't be countered (CR 101.2).
 */
class OneChunk02ToxicScenariosTest {
    private val nurse = Cards.invent("Seepwarden Nurse", "{3}{G}", "Creature — Phyrexian Cleric", """
        Toxic 2
        {2}{G}: Each other creature you control with toxic gains toxic 1 until end of turn. Activate only once each turn. (A player dealt combat damage by a creature with toxic also gets poison counters equal to that creature's total toxic value.)
    """, "3/4")
    private val prowler = Cards.invent("Rotbark Prowler", "{1}{G}", "Creature — Phyrexian Elf Scout",
        "Toxic 2 (Players dealt combat damage by this creature also get two poison counters.)", "3/1")
    private val augur = Cards.invent("Blightmender Augur", "{2}{B}{G}", "Creature — Phyrexian Zombie Cleric", """
        Toxic 2 (Players dealt combat damage by this creature also get two poison counters.)
        Whenever a creature you control with toxic deals combat damage to a player, that player gets an additional poison counter.
        {1}{B}{G}: Target creature you control with toxic gains deathtouch until end of turn.
    """, "1/5")
    private val gnat = Cards.invent("Venom Gnat", "{1}{B}", "Creature — Phyrexian Insect", """
        Flying
        Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
    """, "1/1")
    private val acolyte = Cards.invent("Ichorbless Acolyte", "{G}", "Creature — Phyrexian Druid", """
        Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
        Whenever a creature you control becomes the target of a spell, target opponent gets a poison counter.
    """, "1/2")
    private val surge = Cards.invent("Ichor Surge", "{G}", "Instant", "Target creature gets +2/+2 until end of turn.")
    private val jolt = Cards.invent("Bilefire Jolt", "{R}", "Instant", "Bilefire Jolt deals 3 damage to target creature.")
    private val thrisk = Cards.invent("Thrisk, Turncoat Mite", "{W}", "Legendary Artifact Creature — Phyrexian Mite", """
        Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
        Thrisk can't block.
        {W/P}, {T}: Choose a color. Another target creature you control gains toxic 1 and hexproof from that color until end of turn. It can't be blocked by creatures of that color this turn. ({W/P} can be paid with either {W} or 2 life.)
    """, "1/1")
    private val basilisk = Cards.invent("Bilegaze Basilisk", "{2}{G}", "Creature — Phyrexian Basilisk", """
        Deathtouch
        Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
    """, "1/3")
    private val duelist = Cards.invent("Glintfaith Duelist", "{1}{W}", "Creature — Phyrexian Soldier", """
        Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
        During your turn, this creature has first strike.
    """, "2/2")
    private val oskrel = Cards.invent("Oskrel, the Vermin Thane", "{1}{B}{B}", "Legendary Creature — Phyrexian Rat", """
        Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
        Other Rats you control have toxic 1.
        When Oskrel enters, look at the top five cards of your library. You may reveal any number of Rat cards from among them and put the revealed cards into your hand. Put the rest on the bottom of your library in a random order.
    """, "3/3")
    private val gnawer = Cards.invent("Sewer Gnawer", "{B}", "Creature — Phyrexian Rat",
        "Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)", "1/1")
    private val scurrier = Cards.invent("Gutter Scurrier", "{1}{B}", "Creature — Rat", "", "2/1")
    private val fencer = Cards.invent("Mandible Fencer", "{1}{W}", "Creature — Phyrexian Soldier", """
        Double strike
        Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
    """, "1/1")
    private val retooler = Cards.invent("Gearwright Retooler", "{2}{U}", "Creature — Phyrexian Artificer", """
        Toxic 1 (Players dealt combat damage by this creature also get a poison counter.)
        When this creature enters, up to one target artifact you control becomes an artifact creature with base power and toughness 4/4 for as long as this creature remains on the battlefield.
    """, "2/3")
    private val idol = Cards.invent("Rusted Idol", "{1}", "Artifact", "")
    private val tyrant = Cards.invent("Tyrranax Tyrant", "{4}{G}{G}{G}", "Creature — Phyrexian Dinosaur", """
        This spell can't be countered.
        Trample, ward {4}, haste
        Toxic 4 (Players dealt combat damage by this creature also get four poison counters.)
    """, "8/8")
    private val halt = Cards.invent("Halt the Spell", "{1}{U}", "Instant", "Counter target spell.")

    private fun Scenario.toxicOf(name: String): List<Int> =
        view()[card(name)]!!.characteristics!!.abilities.mapNotNull { (it.ability as? Keyword.Toxic)?.amount }

    // CR 702.164b: toxic 1 gained on top of toxic 2 is a total toxic value of 3; "each other": the Nurse keeps its own
    @Test
    fun nurseAddsToxicToEachOtherToxicCreature() = scenario {
        alice.battlefield(nurse, prowler, "Forest", "Forest", "Forest")
        alice.activates(nurse)
        resolveAll()
        assertEquals(listOf(2, 1), toxicOf(prowler).sorted().reversed())
        assertEquals(listOf(2), toxicOf(nurse))
        hits(prowler)
        expect { bob.counters(CounterKind.Poison) shouldBe 3 }
    }

    // CR 122.1f, 702.164c: the toxic poison counter and one more for the player dealt damage; the other opponent gets none
    @Test
    fun augurAddsAPoisonCounterToEachToxicHit() = scenario(players = 3) {
        alice.battlefield(augur, gnat)
        hits(gnat)
        expect {
            bob.counters(CounterKind.Poison) shouldBe 2
            carol.counters(CounterKind.Poison) shouldBe 0
            bob.life shouldBe 19
        }
    }

    // CR 115.10: a spell targeting a creature you control triggers it; the chosen opponent gets the poison counter
    @Test
    fun acolyteGivesPoisonWhenYourCreatureIsTargetedBySpell() = scenario(players = 3) {
        alice.battlefield(acolyte, "Forest")
        alice.hand(surge)
        alice.casts(surge, target = acolyte)
        resolveAll()
        targets("carol")
        resolveAll()
        expect {
            carol.counters(CounterKind.Poison) shouldBe 1
            bob.counters(CounterKind.Poison) shouldBe 0
            acolyte.power shouldBe 3
        }
    }

    // CR 115.10: an opponent's spell targeting your creature triggers it too
    @Test
    fun acolyteTriggersOnAnOpponentsSpell() = scenario(players = 3) {
        turn(number = 2, active = bob)
        alice.battlefield(acolyte, gnat)
        bob.battlefield("Mountain")
        bob.hand(jolt)
        bob.casts(jolt, target = gnat)
        resolveAll()
        targets("bob")
        resolveAll()
        expect {
            bob.counters(CounterKind.Poison) shouldBe 1
            gnat.zone shouldBe Zone.Graveyard
        }
    }

    // CR 115.10: an activated ability is not a spell: no trigger
    @Test
    fun acolyteIgnoresAnAbilityTargeting() = scenario {
        alice.battlefield(acolyte, augur, "Swamp", "Forest", "Forest")
        alice.activates(augur, target = acolyte)
        resolveAll()
        assertEquals(null, pending, "no trigger asks for a target opponent")
        expect {
            bob.counters(CounterKind.Poison) shouldBe 0
            acolyte hasKeyword Keyword.Deathtouch.kind
        }
    }

    // CR 702.11d, 509.1b: the colour chosen as the ability resolves stays fixed: no green blocker, and toxic 1 more (CR 702.164b)
    @Test
    fun thriskMakesItUnblockableByTheChosenColor() = scenario {
        alice.battlefield(thrisk, prowler, "Plains")
        bob.battlefield(basilisk, duelist)
        alice.activates(thrisk, target = prowler)
        resolveAll()
        decision<Decision.ChooseColor>()
        answer(Answer.ColorChoice(Color.Green))
        resolveAll()
        expect { prowler hasKeyword Keyword.Hexproof.kind }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(prowler)
        advanceTo(TurnStep.DeclareBlockers)
        assertFails("a green creature can't block it") { bob.blocks(basilisk to prowler) }
        bob.blocks()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { bob.counters(CounterKind.Poison) shouldBe 3 }
    }

    // CR 509.1b: a creature of another colour may still block it
    @Test
    fun thriskLetsOtherColorsBlock() = scenario {
        alice.battlefield(thrisk, prowler, "Plains")
        bob.battlefield(basilisk, duelist)
        alice.activates(thrisk, target = prowler)
        resolveAll()
        answer(Answer.ColorChoice(Color.Green))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(prowler)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(duelist to prowler)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            bob.counters(CounterKind.Poison) shouldBe 0
            duelist.zone shouldBe Zone.Graveyard
        }
    }

    // "any number of Rat cards": both Rats to the hand, the other three to the bottom (CR 401.4); the sixth card stays on top
    @Test
    fun oskrelRevealsAnyNumberOfRats() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.library(gnawer, "Island", scurrier, "Plains", "Forest", "Mountain")
        alice.hand(oskrel)
        alice.casts(oskrel)
        resolveAll()
        val choice = decision<Decision.ChooseObjects>()
        assertTrue(choice.options.size == 2, "only the Rats can be chosen")
        alice.chooses(gnawer, scurrier)
        resolveAll()
        expect {
            alice.hand has gnawer
            alice.hand has scurrier
            alice.library.size shouldBe 4
            alice.library.names.first() shouldBe "Mountain"
        }
    }

    // CR 702.164b: another Rat's own toxic 1 and the granted toxic 1 total 2; Oskrel itself has only its own
    @Test
    fun oskrelsRatsAddToxic() = scenario {
        alice.battlefield(oskrel, gnawer)
        assertEquals(listOf(1), toxicOf(oskrel))
        hits(gnawer)
        expect { bob.counters(CounterKind.Poison) shouldBe 2 }
    }

    // CR 510.4, 702.164c: combat damage twice, poison counters twice
    @Test
    fun fencerPoisonsTwiceWithDoubleStrike() = scenario {
        alice.battlefield(fencer)
        hits(fencer)
        advanceTo(TurnStep.EndOfCombat)
        expect {
            bob.counters(CounterKind.Poison) shouldBe 2
            bob.life shouldBe 18
        }
    }

    // CR 611.2b, 613.1d: a 4/4 artifact creature until the Retooler leaves the battlefield
    @Test
    fun retoolerAnimatesAnArtifactWhileItRemains() = scenario {
        alice.battlefield(idol, "Island", "Island", "Island")
        bob.battlefield("Mountain")
        alice.hand(retooler)
        bob.hand(jolt)
        alice.casts(retooler)
        resolveAll()
        if (pending is Decision.Targets) targets(idol)
        resolveAll()
        expect {
            idol.power shouldBe 4
            idol.toughness shouldBe 4
            assertTrue(CardType.Creature in idol.types)
        }
        alice.passes()
        bob.casts(jolt, target = retooler)
        resolveAll()
        expect {
            retooler.zone shouldBe Zone.Graveyard
            assertTrue(CardType.Creature !in idol.types)
        }
    }

    // CR 101.2: the counterspell resolves but can't counter it
    @Test
    fun tyrantCantBeCountered() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        bob.battlefield("Island", "Island")
        alice.hand(tyrant)
        bob.hand(halt)
        alice.casts(tyrant)
        alice.passes()
        bob.casts(halt, target = tyrant)
        resolveAll()
        expect {
            tyrant.zone shouldBe Zone.Battlefield
            halt.zone shouldBe Zone.Graveyard
        }
    }
}
