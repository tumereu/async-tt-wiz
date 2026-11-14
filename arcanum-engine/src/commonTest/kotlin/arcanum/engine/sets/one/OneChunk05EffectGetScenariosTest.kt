package arcanum.engine.sets.one

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Phyrexia: All Will Be One chunk 05 in play: "up to one target creature gets -X/-X" and an untargeted return chosen as the spell
 * resolves when X is 5 or more (CR 115.10a, 608.2d); creatures that get +1/+1 for each oil counter on themselves (layer 7c, CR 613.4c);
 * a delayed trigger giving each blocking creature's controller a poison counter (CR 603.7c, 509.3a, 122.1f); and each opponent who
 * sacrifices and gets a poison counter (CR 701.21a, 122.1f).
 */
class OneChunk05EffectGetScenariosTest {
    private val rite = Cards.invent("Umbral Dusk Rite", "{X}{B}", "Instant",
        "Up to one target creature gets -X/-X until end of turn. If X is 5 or more, return a creature card with mana value X or less from your graveyard to the battlefield tapped.")
    private val hulk = Cards.invent("Murkfen Hulk", "{4}{B}", "Creature — Horror", "", "5/5")
    private val drudge = Cards.invent("Bilecrawl Drudge", "{2}{B}", "Creature — Zombie", "", "3/3")
    private val behemoth = Cards.invent("Gloomhide Behemoth", "{4}{B}{B}", "Creature — Horror", "", "6/6")
    private val adaptor = Cards.invent("Seepwing Adaptor", "{G}", "Creature — Phyrexian Warrior", """
        This creature enters with an oil counter on it.
        This creature gets +1/+1 for each oil counter on it.
        Whenever another creature you control enters, if that creature has greater power or toughness than this creature, put an oil counter on this creature.
    """, "0/0")
    private val grublet = Cards.invent("Sparkoil Grublet", "{R}", "Creature — Phyrexian Goblin Warrior", """
        Trample
        This creature gets +1/+0 for each oil counter on it.
        When this creature enters and whenever another creature or artifact you control is put into a graveyard from the battlefield, put an oil counter on this creature.
    """, "0/1")
    private val gnat = Cards.invent("Oilwing Gnat", "{3}{B}", "Creature — Phyrexian Insect", """
        Flying
        This creature enters with two oil counters on it.
        This creature gets +1/+1 for each oil counter on it.
        Whenever another creature or artifact you control is put into a graveyard from the battlefield, put an oil counter on this creature.
    """, "0/0")
    private val pup = Cards.invent("Rustfang Pup", "{1}{G}", "Creature — Dog", "", "2/1")
    private val ox = Cards.invent("Sludgeback Ox", "{G}", "Creature — Ox", "", "1/1")
    private val bolt = Cards.invent("Filament Bolt", "{R}", "Instant", "Filament Bolt deals 3 damage to target creature.")
    private val onslaught = Cards.invent("Fouling Onslaught", "{3}{G}{G}", "Sorcery",
        "Creatures you control get +2/+2 until end of turn. Whenever a creature blocks this turn, its controller gets a poison counter.")
    private val scout = Cards.invent("Slagstep Scout", "{1}{G}", "Creature — Phyrexian Scout", "", "2/2")
    private val sentry = Cards.invent("Corroded Sentry", "{1}{W}", "Creature — Soldier", "", "1/4")
    private val trooper = Cards.invent("Grimcoil Trooper", "{1}{W}", "Creature — Soldier", "", "2/3")
    private val plunge = Cards.invent("Gorgon's Plunge", "{2}{B}", "Instant",
        "Each opponent sacrifices a creature or planeswalker of their choice and gets a poison counter.")

    // CR 107.3a, 115.10a, 608.2d: X is 5; the target dies (CR 704.5f) and a creature card with mana value 5 or less, chosen as the spell
    // resolves, returns tapped; one with mana value 6 can't be chosen
    @Test
    fun riteWithXFiveShrinksAndReturnsTapped() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(drudge, hulk, behemoth)
        bob.battlefield(scout)
        alice.hand(rite)
        alice.casts(rite, target = scout, x = 5)
        resolveAll()
        val choice = decision<Decision.ChooseObjects>()
        assertEquals(2, choice.options.size, "only the cards with mana value 5 or less: ${choice.options}")
        alice.chooses(hulk)
        resolveAll()
        expect {
            scout.zone shouldBe Zone.Graveyard
            hulk.zone shouldBe Zone.Battlefield
            hulk.tapped shouldBe true
            drudge.zone shouldBe Zone.Graveyard
            behemoth.zone shouldBe Zone.Graveyard
        }
    }

    // CR 608.2c: X is 4, below 5: the creature gets -4/-4 and nothing returns
    @Test
    fun riteWithXFourReturnsNothing() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(drudge)
        bob.battlefield(hulk)
        alice.hand(rite)
        alice.casts(rite, target = hulk, x = 4)
        resolveAll()
        expect {
            hulk.power shouldBe 1
            hulk.toughness shouldBe 1
            drudge.zone shouldBe Zone.Graveyard
        }
    }

    // CR 601.2c, 608.2b: "up to one" with no creature to target: the spell still resolves and returns a card
    @Test
    fun riteWithNoTargetStillReturns() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(drudge)
        alice.hand(rite)
        alice.casts(rite, x = 5)
        resolveAll()
        expect {
            drudge.zone shouldBe Zone.Battlefield
            drudge.tapped shouldBe true
        }
    }

    // CR 614.1c, 613.4c: enters with two oil counters, 2/2; another creature you control dying adds one, 3/3
    @Test
    fun gnatGrowsWithOilCounters() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Mountain", ox)
        alice.hand(gnat, bolt)
        alice.casts(gnat)
        resolveAll()
        expect {
            gnat.counters(CounterKind.Named("oil")) shouldBe 2
            gnat.power shouldBe 2
            gnat.toughness shouldBe 2
        }
        alice.casts(bolt, target = ox)
        resolveAll()
        expect {
            ox.zone shouldBe Zone.Graveyard
            gnat.power shouldBe 3
            gnat.toughness shouldBe 3
        }
    }

    // CR 603.2, 613.4c: its enters trigger puts an oil counter on it: +1/+0, so 1/1
    @Test
    fun grubletGetsPowerOnlyFromOil() = scenario {
        alice.battlefield("Mountain")
        alice.hand(grublet)
        alice.casts(grublet)
        resolveAll()
        expect {
            grublet.counters(CounterKind.Named("oil")) shouldBe 1
            grublet.power shouldBe 1
            grublet.toughness shouldBe 1
        }
    }

    // CR 603.4: a 2/1 entering has greater power than the 1/1: an oil counter, 2/2; a 1/1 entering after that has neither
    @Test
    fun adaptorGrowsOnlyForABiggerCreature() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(adaptor, pup, ox)
        alice.casts(adaptor)
        resolveAll()
        expect { adaptor.power shouldBe 1 }
        alice.casts(pup)
        resolveAll()
        expect {
            adaptor.power shouldBe 2
            adaptor.toughness shouldBe 2
        }
        alice.casts(ox)
        resolveAll()
        expect { adaptor.counters(CounterKind.Named("oil")) shouldBe 2 }
    }

    // CR 603.7c, 509.3a, 122.1f: each creature that blocks this turn gives its controller a poison counter; the other opponent gets none
    @Test
    fun onslaughtPoisonsEachBlockersController() = scenario(players = 3) {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", scout, pup)
        bob.battlefield(sentry, trooper)
        alice.hand(onslaught)
        alice.casts(onslaught)
        resolveAll()
        expect { scout.power shouldBe 4 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(scout, pup, defender = "bob")
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(sentry to scout, trooper to pup)
        resolveAll()
        expect {
            bob.counters(CounterKind.Poison) shouldBe 2
            carol.counters(CounterKind.Poison) shouldBe 0
            alice.counters(CounterKind.Poison) shouldBe 0
        }
    }

    // CR 701.21a, 101.4: each opponent sacrifices one of their choice; CR 122.1f: each gets a poison counter, also one with nothing to sacrifice
    @Test
    fun plungeMakesEachOpponentSacrificeAndPoisons() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", ox)
        bob.battlefield(sentry, trooper)
        alice.hand(plunge)
        alice.casts(plunge)
        resolveAll()
        bob.chooses(trooper)
        resolveAll()
        expect {
            trooper.zone shouldBe Zone.Graveyard
            sentry.zone shouldBe Zone.Battlefield
            ox.zone shouldBe Zone.Battlefield
            bob.counters(CounterKind.Poison) shouldBe 1
            carol.counters(CounterKind.Poison) shouldBe 1
            alice.counters(CounterKind.Poison) shouldBe 0
        }
    }

    private val lesion = Cards.invent("Seeping Lesion", "{B}", "Instant",
        "Put a -1/-1 counter on target creature. When that creature dies this turn, its controller gets a poison counter.")

    // CR 603.7c, 603.10a: the delayed trigger sees the creature die; its last controller gets the poison counter, the others none
    @Test
    fun lesionPoisonsTheDeadCreaturesController() = scenario(players = 3) {
        alice.battlefield("Swamp")
        carol.battlefield(ox)
        alice.hand(lesion)
        alice.casts(lesion, target = ox)
        resolveAll()
        expect {
            ox.zone shouldBe Zone.Graveyard
            carol.counters(CounterKind.Poison) shouldBe 1
            bob.counters(CounterKind.Poison) shouldBe 0
            alice.counters(CounterKind.Poison) shouldBe 0
        }
    }
}
