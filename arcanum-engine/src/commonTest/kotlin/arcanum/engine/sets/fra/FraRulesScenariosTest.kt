package arcanum.engine.sets.fra

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Reality Fracture's rules pieces in play: split second granted to spells, no spells during combat, entering that
 * doesn't trigger, the "legend rule" switched off, negative power assigning damage, hexproof until combat damage,
 * "last turn", "for every seven", proliferate per planeswalker type, equip cheaper by counters, token replacement,
 * one more counter, free casting by mana value. */
class FraRulesScenariosTest {
    private val hekat = Cards.invent("Hekat, Tyrant of the Sands", "{1}{U}", "Legendary Creature — Human Wizard",
        "Instant and sorcery spells you control have split second. (As long as a spell with split second is on the stack, players can't cast spells or activate abilities that aren't mana abilities.)", "2/1")
    private val akemi = Cards.invent("Akemi, Edge of the Mighty", "{3}{W}", "Legendary Creature — Human Samurai", """
        During combat, players can't cast spells or activate abilities that aren't mana abilities.
        Whenever a creature you control attacks a player alone, it gains double strike until end of turn.
    """, "2/3")
    private val golvak = Cards.invent("Golvak, Silver Bulwark", "{2}", "Legendary Artifact Creature — Golem",
        "Artifacts and creatures entering the battlefield don't cause abilities to trigger.", "1/3")
    private val orb = Cards.invent("Thoughtseeker Orb", "{2}{U}", "Creature — Homunculus",
        "When this creature enters, empower Jace 4. (Put four loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with \"[−1]: Surveil 1\" and \"[−3]: Draw a card.\")", "2/1")
    private val hall = Cards.invent("Hall of Reverberations", null, "Land", """
        {T}: Add {C}.
        {5}: This land becomes a copy of target creature you control until end of turn. The "legend rule" doesn't apply to permanents you control this turn.
    """)
    private val snibbet = Cards.invent("Snibbet, the Oddity", "{2}{B}", "Legendary Creature — Beast Horror", """
        If Snibbet's power is negative, he assigns combat damage as though his power were positive.
        Threshold — Sacrifice another creature or planeswalker: Snibbet gets -2/-0 until end of turn. Activate only if there are seven or more cards in your graveyard.
    """, "-2/4")
    private val gorrum = Cards.invent("Gorrum Vax, Spellsmasher", "{5}{G}{G}", "Legendary Creature — Ogre Warrior", """
        This spell can't be countered.
        Reach, vigilance, trample
        Gorrum Vax has hexproof as long as they haven't dealt combat damage yet. (They can't be the target of spells or abilities your opponents control.)
    """, "7/7")
    private val limelight = Cards.invent("Claim the Limelight", "{2}{R}", "Sorcery", """
        Create a 2/2 colorless Wizard Soldier creature token named Cadet, then put a +1/+1 counter on each other Wizard token you control.
        At the beginning of each upkeep, if an opponent was dealt noncombat damage last turn, return this card from your graveyard to your hand.
    """)
    private val voidShaper = Cards.invent("Void Matter Shaper", "{B}", "Creature — Human Warlock", """
        When this creature enters, mill three cards.
        This creature gets +2/+0 for every seven cards in your graveyard.
    """, "1/2")
    private val ilse = Cards.invent("Ilse, the Potential", "{1}{G}{U}", "Legendary Creature — Gorgon Wizard", """
        Planeswalker spells you cast cost {1} less to cast.
        {W}{U}{B}{R}{G}, {T}: Proliferate X times, where X is the number of planeswalker types among planeswalkers you control. (To proliferate, choose any number of permanents and/or players, then give each another counter of each kind already there.)
    """, "2/4")
    private val edges = Cards.invent("Duelist's Edges", "{2}{R}{W}", "Artifact — Equipment", """
        When this Equipment enters, it deals 3 damage to any target and you gain 3 life.
        Equipped creature gets +2/+1.
        Equip {3}. This ability costs {1} less to activate for each +1/+1 counter on the creature it targets.
    """)
    private val wanderer = Cards.invent("Wyrmish Wanderer", "{3}{R}{R}", "Creature — Dragon", """
        Flying
        If one or more artifact tokens would be created under your control, that many 5/5 red Dragon creature tokens with flying are created instead.
    """, "5/5")
    private val treasures = Cards.invent("Hoard Shower", "{1}{R}", "Sorcery", "Create two Treasure tokens.")
    private val kotaro = Cards.invent("Kotaro, Faithful Friend", "{2}{W}", "Legendary Creature — Dog", """
        If one or more +1/+1 counters would be put on a creature you control, that many plus one +1/+1 counters are put on it instead.
        {6}: Put a +1/+1 counter on target legendary creature.
    """, "2/2")
    private val graft = Cards.invent("Graft Mending", "{G/W}", "Sorcery", "Put a +1/+1 counter on target creature. You gain 1 life.")
    private val everywhere = Cards.invent("Everywhere at Once", "{5}{G}{G}{G}", "Enchantment",
        "You may cast spells with mana value less than or equal to the number of creatures you control from your hand without paying their mana costs.")
    private val iren = Cards.invent("Iren, Planeswalker of Proofs", "{1}{U}{U}", "Legendary Planeswalker — Jace", "[+1]: Draw a card.", loyalty = "3")
    private val velka = Cards.invent("Velka, Spark Adept", "{2}{R}", "Legendary Planeswalker — Chandra", "[+1]: Add {R}.", loyalty = "3")
    private val shock = Cards.invent("Quick Shock", "{R}", "Instant", "Quick Shock deals 2 damage to any target.")
    private val bear = Cards.invent("Market Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Wide Ox", "{2}{G}", "Creature — Ox", "", "1/4")
    private val legend = Cards.invent("Aldric the Unrivalled", "{1}{W}", "Legendary Creature — Human Knight", "", "2/2")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private fun Scenario.resolveStackFully() {
        var guard = 30
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    /** "Instant and sorcery spells you control have split second": nobody can respond with a spell. */
    // CR 702.61a
    @Test
    fun grantedSplitSecond() = scenario {
        alice.battlefield(hekat, "Mountain")
        bob.battlefield("Mountain")
        alice.hand(shock)
        bob.hand(shock)
        alice.casts(shock, target = "bob")
        passPriority()
        assertTrue(Engine.options(state, bob.id).none { it is Input.Cast })
        resolveStackFully()
        expect { bob.life shouldBe 18 }
    }

    /** During combat, no spells; after combat they are fine again. */
    // CR 506.1
    @Test
    fun noSpellsDuringCombat() = scenario {
        alice.battlefield(akemi, "Mountain", "Mountain")
        alice.hand(shock, shock)
        alice.library(bear)
        bob.library(bear)
        advanceTo(TurnStep.BeginningOfCombat)
        assertFailsWith<IllegalArgumentException> { alice.casts(shock, target = "bob") }
        advanceTo(TurnStep.PostcombatMain)
        alice.casts(shock, target = "bob")
        resolveStackFully()
        expect { bob.life shouldBe 18 }
    }

    /** A creature attacking a player alone gets double strike. */
    // CR 506.5, 702.4b
    @Test
    fun aloneGetsDoubleStrike() = scenario {
        alice.battlefield(akemi, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveStackFully()
        expect { "Market Bear" hasKeyword Keyword.DoubleStrike }
        advanceTo(TurnStep.PostcombatMain)
        expect { bob.life shouldBe 16 }
    }

    /** Creatures entering don't cause abilities to trigger. */
    // CR 603.2
    @Test
    fun enteringCausesNoTriggers() = scenario {
        alice.battlefield(golvak, "Island", "Island", "Island")
        alice.hand(orb)
        alice.casts(orb)
        allPass()
        expect {
            stack.isEmpty()
            card("Thoughtseeker Orb").zone shouldBe Zone.Battlefield
        }
        assertTrue(state.battlefield.none { state.obj(it)?.token == true })
    }

    /** A land becoming a copy of a legendary creature you control: the legend rule doesn't apply this turn. */
    // CR 707.2, 704.5j
    @Test
    fun hallCopiesALegend() = scenario {
        alice.battlefield(hall, legend, "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.activates(hall, ability = 1, target = "Aldric the Unrivalled")
        resolveStackFully()
        expect {
            card(hall).zone shouldBe Zone.Battlefield
            card("Aldric the Unrivalled").zone shouldBe Zone.Battlefield
        }
        assertEquals(2, state.battlefield.count { view()[it]?.characteristics?.name == "Aldric the Unrivalled" })
    }

    /** With negative power, it assigns as much combat damage as its power is below zero. */
    // CR 510.1a
    @Test
    fun negativePowerDealsDamage() = scenario {
        alice.battlefield(snibbet)
        alice.library(bear)
        bob.library(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(snibbet)
        advanceTo(TurnStep.PostcombatMain)
        expect { bob.life shouldBe 18 }
    }

    /** Hexproof only until it has dealt combat damage. */
    // CR 702.11b
    @Test
    fun hexproofUntilCombatDamage() = scenario {
        alice.battlefield(gorrum)
        bob.battlefield("Mountain", "Mountain")
        bob.hand(shock, shock)
        alice.library(bear)
        bob.library(bear)
        expect { gorrum hasKeyword Keyword.Hexproof }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(gorrum)
        advanceTo(TurnStep.PostcombatMain)
        expect {
            bob.life shouldBe 13
            gorrum lacksKeyword Keyword.Hexproof
        }
    }

    /** "If an opponent was dealt noncombat damage last turn, return this card from your graveyard to your hand." */
    // CR 603.4, 113.6b
    @Test
    fun limelightReturnsAfterNoncombatDamage() = scenario {
        alice.graveyard(limelight)
        alice.battlefield("Mountain")
        alice.hand(shock)
        alice.library(bear, bear)
        bob.library(bear, bear)
        alice.casts(shock, target = "bob")
        resolveStackFully()
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveStackFully()
        expect { alice.hand has limelight }
    }

    /** The upkeep after a turn without noncombat damage to an opponent, it stays. */
    // CR 603.4
    @Test
    fun limelightStaysWithoutDamage() = scenario {
        alice.graveyard(limelight)
        alice.library(bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { alice.graveyard has limelight }
    }

    /** "Create a Cadet, then put a +1/+1 counter on each other Wizard token you control": not on the new one. */
    // CR 111.4
    @Test
    fun limelightCountersOtherWizards() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(limelight)
        alice.casts(limelight)
        resolveStackFully()
        alice.graveyard.names
        change(arcanum.engine.change.Changes.move(state, listOf(card(limelight, zone = Zone.Graveyard)), Zone.Hand))
        alice.casts(limelight)
        resolveStackFully()
        val cadets = state.battlefield.filter { state.obj(it)?.token == true }
        assertEquals(listOf(1, 0), cadets.map { state.obj(it)!!.counters(p1p1) })
    }

    /** "+2/+0 for every seven cards in your graveyard": rounded down. */
    // CR 107.1a
    @Test
    fun forEverySevenCards() = scenario {
        alice.battlefield(voidShaper)
        alice.graveyard(bear, bear, bear, bear, bear, bear)
        expect { voidShaper.power shouldBe 1 }
        move(bear, Zone.Graveyard)
        expect { voidShaper.power shouldBe 1 }
    }

    /** Proliferate as many times as the planeswalker types among planeswalkers you control. */
    // CR 701.34a, 205.3j
    @Test
    fun proliferatesPerPlaneswalkerType() = scenario {
        alice.battlefield(ilse, "Plains", "Island", "Swamp", "Mountain", "Forest")
        alice.battlefield(iren, counters = listOf(CounterKind.Loyalty to 3))
        alice.battlefield(velka, counters = listOf(CounterKind.Loyalty to 3))
        alice.activates(ilse)
        allPass()
        repeat(2) { if (pending != null) alice.answers(arcanum.engine.decision.Answer.Targets(listOf(target("Iren, Planeswalker of Proofs")))) }
        expect { "Iren, Planeswalker of Proofs".counters(CounterKind.Loyalty) shouldBe 5 }
    }

    /** "Equip {3}. This ability costs {1} less to activate for each +1/+1 counter on the creature it targets." */
    // CR 601.2f, 602.2b
    @Test
    fun equipCheaperByCounters() = scenario {
        alice.battlefield(edges, "Plains")
        alice.battlefield(bear, counters = listOf(p1p1 to 2))
        alice.activates(edges, target = "Market Bear", paying = listOf("Plains"))
        resolveStackFully()
        expect { "Market Bear".power shouldBe 6 }
    }

    /** Treasure tokens would be created: Dragons are instead, as many. */
    // CR 614.1a
    @Test
    fun treasuresBecomeDragons() = scenario {
        alice.battlefield(wanderer, "Mountain", "Mountain")
        alice.hand(treasures)
        alice.casts(treasures)
        resolveStackFully()
        val tokens = state.battlefield.filter { state.obj(it)?.token == true }
        assertEquals(2, tokens.size)
        assertTrue(tokens.all { view()[it]?.characteristics?.subtypes?.map { s -> s.name } == listOf("Dragon") })
    }

    /** One more +1/+1 counter on your creatures. */
    // CR 614.1a
    @Test
    fun oneMoreCounter() = scenario {
        alice.battlefield(kotaro, bear, "Forest")
        alice.hand(graft)
        alice.casts(graft, target = "Market Bear")
        resolveStackFully()
        expect { "Market Bear".counters(p1p1) shouldBe 2 }
    }

    /** Spells with mana value at most the number of creatures you control, from the hand, for free. */
    // CR 118.9
    @Test
    fun castsForFreeByManaValue() = scenario {
        alice.battlefield(everywhere, bear, ox)
        alice.hand(bear, ox)
        // the permission's way: without paying the mana cost (CR 118.9)
        val free = Engine.options(state, alice.id).filterIsInstance<Input.Cast>().first { it.way.permission != null && state.obj(it.card)?.zone == Zone.Hand }
        perform(free)
        resolveStackFully()
        expect { state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) } == "Market Bear" } shouldBe 2 }
        // three creatures now: the Ox (mana value 3) can be cast for free too
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Cast>().any { it.way.permission != null })
    }
}
