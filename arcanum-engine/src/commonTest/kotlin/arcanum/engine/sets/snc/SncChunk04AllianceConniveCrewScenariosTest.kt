package arcanum.engine.sets.snc

import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dft.choose
import arcanum.engine.sets.fin.resolveAll
import arcanum.engine.sets.fin.targets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Streets of New Capenna chunk 04 in play: Alliance's second resolution, connive's reflexive trigger, cards exiled with Vehicles. */
class SncChunk04AllianceConniveCrewScenariosTest {
    private val soloist = Cards.invent("Snc04 Blazing Crooner", "{3}{R}", "Creature — Human Citizen",
        "Alliance — Whenever another creature you control enters, target creature an opponent controls can't block this turn. If this is the second time this ability has resolved this turn, that creature attacks during its controller's next combat phase if able.",
        "3/2")
    private val gatherer = Cards.invent("Snc04 Whisper Collector", "{1}{W}{W}", "Creature — Elf Wizard",
        "Alliance — Whenever another creature you control enters, scry 1. If this is the second time this ability has resolved this turn, draw a card instead.", "2/1")
    private val launderer = Cards.invent("Snc04 Corpse Washer", "{2}{B}{B}", "Creature — Ogre Rogue", """
        Deathtouch
        Whenever another nontoken creature you control dies, this creature connives.
        When this creature dies, return another target non-Rogue creature card with equal or lesser power from your graveyard to the battlefield.
    """, "3/3")
    private val interceptor = Cards.invent("Snc04 Shadowveil Intercepter", "{1}{W}{U}{B}", "Creature — Octopus Wizard", """
        Flash
        Lifelink
        When this creature enters, it connives. When it connives this way, return up to one target spell to its owner's hand. (To have a creature connive, draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)
    """, "3/1")
    private val bypass = Cards.invent("Snc04 Lockpick Charm", "{1}{U}", "Enchantment — Aura", """
        Enchant creature
        As long as enchanted creature is attacking alone, it can't be blocked.
        Enchanted creature has "Whenever this creature deals combat damage to a player, it connives." (Its controller draws a card, then discards a card. If they discarded a nonland card, they put a +1/+1 counter on this creature.)
    """)
    private val toluz = Cards.invent("Snc04 Veska, Sly Porter", "{W/U}{U}{U/B}", "Legendary Creature — Human Rogue", """
        When this creature enters, it connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
        Whenever you discard one or more cards, exile them from your graveyard.
        When this creature dies, put the cards exiled with it into their owner's hand.
    """, "3/1")
    private val silencer = Cards.invent("Snc04 Hushblade Assassin", "{2}{B}", "Creature — Human Assassin", """
        When this creature enters, it connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
        When this creature dies, target creature an opponent controls gets -X/-X until end of turn, where X is this creature's power.
    """, "1/1")
    private val hauler = Cards.invent("Snc04 Trove Wagon", "{3}{R}", "Artifact — Vehicle", """
        Trample
        Whenever this Vehicle deals combat damage to a player, create a Treasure token for each artifact they control.
        Crew 3
    """, "5/5")
    private val limousine = Cards.invent("Snc04 Uncanny Town Car", "{3}{W}{W}", "Artifact — Vehicle", """
        Whenever this Vehicle enters or attacks, exile up to one other target creature until this Vehicle leaves the battlefield. If a creature is put into exile this way, return each other card exiled with this Vehicle to the battlefield under its owner's control.
        Crew 2
    """, "4/4")
    private val hearse = Cards.invent("Snc04 Unbonded Funeral Coach", "{2}", "Artifact — Vehicle", """
        {T}: Exile up to two target cards from a single graveyard.
        Snc04 Unbonded Funeral Coach's power and toughness are each equal to the number of cards exiled with it.
        Crew 2
    """, "*/*")

    private val pup = Cards.invent("Snc04 Alley Pup", "{W}", "Creature — Dog", "", "1/1")
    private val stray = Cards.invent("Snc04 Alley Stray", "{W}", "Creature — Cat", "", "1/1")
    private val brute = Cards.invent("Snc04 Dock Brute", "{1}{G}", "Creature — Ogre", "", "2/2")
    private val thug = Cards.invent("Snc04 Hulking Thug", "{2}{B}", "Creature — Human Warrior", "", "4/4")
    private val bruiser = Cards.invent("Snc04 Towering Bruiser", "{4}{B}", "Creature — Giant", "", "5/5")
    private val alleyRogue = Cards.invent("Snc04 Alley Cutthroat", "{B}", "Creature — Human Rogue", "", "2/2")
    private val jolt = Cards.invent("Snc04 Back Alley Jolt", "{R}", "Instant", "Snc04 Back Alley Jolt deals 5 damage to target creature.")
    private val windfall = Cards.invent("Snc04 Ledger Windfall", "{1}{U}", "Sorcery", "Draw two cards.")
    private val rummage = Cards.invent("Snc04 Rummage the Drawer", "{R}", "Sorcery", "Draw a card, then discard a card.")
    private val spare = Cards.invent("Snc04 Spare Calling Card", "{2}", "Artifact", "")
    private val trinket = Cards.invent("Snc04 Brass Trinket", "{1}", "Artifact", "")
    private val bauble = Cards.invent("Snc04 Glass Bauble", "{1}", "Artifact", "")

    private fun treasures(player: ScenarioPlayer): Int = player.battlefield.names.count { it == "Treasure Token" }

    // CR 508.1d, 603.4, 611.2c: on the second resolution the target must attack in its controller's next combat (Carol's, not Bob's turn
    // between); the requirement ends with that declare attackers step
    @Test
    fun soloistsSecondResolutionMakesTheTargetAttackInItsControllersNextCombat() = scenario(players = 3) {
        alice.battlefield(soloist, "Plains", "Plains")
        alice.hand(pup, stray)
        carol.battlefield(brute)
        bob.library("Island", "Island")
        carol.library("Island", "Island")
        alice.casts(pup)
        resolveAll()
        expect { brute lacksMechanic { it is Mechanic.MustAttack } }
        alice.casts(stray)
        resolveAll()
        expect { brute hasMechanic { it is Mechanic.MustAttack } }
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        assertFailsWith<IllegalArgumentException> { carol.attacks() }
        carol.attacks(brute, defender = "alice")
        advanceTo(TurnStep.EndOfCombat)
        expect { brute lacksMechanic { it is Mechanic.MustAttack } }
    }

    // CR 614.1a, 603.4 (audit: the count includes this resolution and starts over each turn): scry on the first resolution, draw instead of
    // scrying on the second
    @Test
    fun gathererScriesFirstAndDrawsInsteadOnTheSecondResolution() = scenario {
        alice.battlefield(gatherer, "Plains", "Plains")
        alice.hand(pup, stray)
        alice.library("Island", "Swamp")
        alice.casts(pup)
        resolveAll()
        assertEquals(Decision.ChooseObjects::class, pending!!::class)
        answer(Answer.Objects(emptyList()))
        resolveAll()
        expect { alice.hand lacks "Island" }
        alice.casts(stray)
        resolveAll()
        assertNull(pending)
        expect {
            alice.hand has "Island"
            alice.library has "Swamp"
        }
    }

    // CR 603.10a: "equal or lesser power" compares with its power as it last existed (a +1/+1 counter: 4); not a Rogue, not more power
    @Test
    fun laundererReturnsANonRogueWithPowerUpToItsLastPower() = scenario {
        alice.battlefield(launderer, "Mountain", counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.graveyard(thug, bruiser, alleyRogue)
        alice.hand(jolt)
        alice.casts(jolt, target = launderer)
        resolveAll()
        expect {
            launderer.zone shouldBe Zone.Graveyard
            thug.zone shouldBe Zone.Battlefield
            bruiser.zone shouldBe Zone.Graveyard
            alleyRogue.zone shouldBe Zone.Graveyard
        }
    }

    // CR 701.50a, 603.12: flashed in on Bob's turn, it connives, and the reflexive trigger returns Bob's spell to his hand
    @Test
    fun interceptorConnivesAndReturnsTheSpell() = scenario {
        alice.battlefield("Plains", "Island", "Swamp", "Island")
        alice.hand(interceptor)
        alice.library("Forest")
        bob.battlefield("Island", "Island")
        bob.hand(windfall)
        bob.library("Island", "Island", "Island")
        turn(number = 2, active = bob, step = TurnStep.PrecombatMain)
        bob.casts(windfall)
        bob.passes()
        alice.casts(interceptor)
        resolveAll()
        targets(windfall)
        expect {
            bob.hand has windfall
            alice.graveyard has "Forest"
            interceptor.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0
            stack.isEmpty()
        }
    }

    // CR 506.5, 509.1b: attacking alone it can't be blocked; it connives on combat damage (a nonland card discarded: a +1/+1 counter)
    @Test
    fun bypassedCreatureAloneIsUnblockableAndConnives() = scenario {
        alice.battlefield(pup, bypass)
        alice.library(jolt)
        bob.battlefield(brute)
        attach(bypass, pup)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(pup)
        advanceTo(TurnStep.DeclareBlockers)
        // no legal block: the declaration is skipped or refuses the Brute; the damage shows it went unblocked
        assertFailsWith<IllegalArgumentException> { bob.blocks(brute to pup) }
        if (state.priority.holder == null) bob.blocks()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.graveyard has jolt
            pup.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // CR 506.5: attacking with another creature it can be blocked
    @Test
    fun bypassedCreatureWithAnotherAttackerCanBeBlocked() = scenario {
        alice.battlefield(pup, stray, bypass)
        bob.battlefield(brute)
        attach(bypass, pup)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(pup, stray)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(brute to pup)
        expect { pup.zone shouldBe Zone.Battlefield }
    }

    // CR 607.2a, 400.7: the discarded card is exiled from the graveyard and returns to its owner's hand when it dies
    @Test
    fun toluzExilesDiscardedCardsAndReturnsThemWhenItDies() = scenario {
        alice.battlefield(toluz, "Mountain", "Mountain")
        alice.hand(rummage, spare, jolt)
        alice.library("Island")
        alice.casts(rummage)
        resolveAll()
        choose(spare)
        expect { alice.exile has spare }
        alice.casts(jolt, target = toluz)
        resolveAll()
        expect {
            toluz.zone shouldBe Zone.Graveyard
            alice.hand has spare
        }
    }

    // CR 603.10a, 608.2h: X is its power as it last existed, a +1/+1 counter included
    @Test
    fun silencerShrinksByItsLastPower() = scenario {
        alice.battlefield(silencer, "Mountain", counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.hand(jolt)
        bob.battlefield(thug)
        alice.casts(jolt, target = silencer)
        resolveAll()
        expect {
            thug.power shouldBe 2
            thug.toughness shouldBe 2
        }
    }

    // CR 510.1, 111.10a, 608.2c: one Treasure for each artifact the damaged player (Bob) controls, not Carol's
    @Test
    fun haulerMakesATreasureForEachArtifactOfThePlayerItHit() = scenario(players = 3) {
        alice.battlefield(hauler, thug)
        bob.battlefield(trinket, bauble)
        carol.battlefield(spare)
        alice.activates(hauler, choosing = listOf(thug))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hauler, defender = "bob")
        advanceTo(TurnStep.DeclareBlockers)
        if (state.priority.holder == null) bob.blocks()
        if (state.priority.holder == null) carol.blocks()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { bob.life shouldBe 15 }
        assertEquals(2, treasures(alice))
    }

    // CR 610.3, 607.2a: entering it exiles Bob's Brute; attacking it exiles the Thug, and the Brute comes back under Bob's control
    @Test
    fun limousineSwapsTheExiledCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", pup, stray)
        alice.hand(limousine)
        alice.library("Plains")
        bob.battlefield(brute, thug)
        bob.library("Island")
        alice.casts(limousine)
        resolveAll()
        targets(brute)
        expect {
            brute.zone shouldBe Zone.Exile
            thug.zone shouldBe Zone.Battlefield
        }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.activates(limousine, choosing = listOf(pup, stray))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(limousine)
        targets(thug)
        expect {
            thug.zone shouldBe Zone.Exile
            brute.zone shouldBe Zone.Battlefield
            brute.controller shouldBe bob.id
        }
    }

    // CR 604.3, 607.2a: its power and toughness count the cards it exiled
    @Test
    fun hearseCountsTheCardsItExiled() = scenario {
        alice.battlefield(hearse, brute)
        bob.graveyard(thug, bruiser)
        alice.activates(hearse, targets = listOf(thug, bruiser))
        resolveAll()
        alice.activates(hearse, ability = 1, choosing = listOf(brute))
        resolveAll()
        expect {
            bob.exile has thug
            hearse.power shouldBe 2
            hearse.toughness shouldBe 2
        }
    }
}
