package arcanum.engine.sets.one

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.CardRef
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.dft.hits
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test

/**
 * ONE sweep: siblings of the bugs set work found, played on other paths (combat damage beside effects, triggers beside spells, other
 * phrases of one rule). Invented names carry real oracle wordings.
 */
class OneSweepScenariosTest {
    private val warden = Cards.invent("Ysmira, Venom Warden", "{G}{W}", "Legendary Creature — Human Cleric",
        "If you would get one or more poison counters, instead you get one poison counter and you can't get additional poison counters this turn.", "2/3")
    private val stinger = Cards.invent("Glasswing Stinger", "{1}{B}", "Creature — Phyrexian Insect", "Toxic 2", "2/1")
    private val blighter = Cards.invent("Bilecoil Blighter", "{1}{B}", "Creature — Phyrexian Horror", "Infect", "2/2")
    private val dose = Cards.invent("Bilecoil Dose", "{B}", "Instant", "Target player gets two poison counters.")

    private val echoes = Cards.invent("Hall of Lingering Echoes", "{2}{U}", "Enchantment",
        "Whenever another creature you control dies, create a token that's a copy of it.")
    private val husk = Cards.invent("Husk of Lingering Echoes", "{2}{U}", "Enchantment",
        "Whenever another nontoken creature you control dies, create a token that's a copy of that creature, except it's 1/1.")
    private val ooze = Cards.invent("Sweepmimic Ooze", "{0}", "Creature — Ooze", "You may have this creature enter as a copy of any creature on the battlefield.", "0/0")
    private val boar = Cards.invent("Sweepthorn Boar", "{2}{G}", "Creature — Boar", "Trample", "2/2")
    private val jolt = Cards.invent("Sweep Jolt", "{R}", "Instant", "Sweep Jolt deals 2 damage to target creature.")

    private val tally = Cards.invent("Graveward Tally", "{1}{B}", "Enchantment",
        "Whenever a creature you control dies, if it had a +1/+1 counter on it, draw a card.")
    private val cub = Cards.invent("Sweeppath Cub", "{1}{G}", "Creature — Bear", "", "1/1")
    private val pup = Cards.invent("Sweeppath Pup", "{1}{G}", "Creature — Wolf", "", "1/1")

    private val reaper = Cards.invent("Ledger of Fallen Foes", "{1}{B}", "Enchantment",
        "Whenever a creature an opponent controls dies, that creature's controller loses 1 life.")
    private val gift = Cards.invent("Sweep Bequest", "{U}", "Sorcery", "Target opponent gains control of target permanent you control.")
    private val beacon = Cards.invent("Corsair's Beacon", "{1}{R}", "Enchantment",
        "At the beginning of combat on your turn, create a 1/1 red Pirate creature token with menace and haste. Exile it at the beginning of the next end step.")
    private val usurper = Cards.invent("Tidecrest Usurper", "{3}{U}{U}", "Creature — Elemental",
        "Landfall — Whenever a land you control enters, you may gain control of target creature for as long as you control this creature.", "3/2")
    private val spur = Cards.invent("Sweepspur Lance", "{1}", "Artifact — Equipment",
        "Whenever equipped creature attacks, it gets +2/+0 until end of turn.\nEquip {1}")
    private val spurThat = Cards.invent("Sweepspur Halberd", "{1}", "Artifact — Equipment",
        "Whenever equipped creature attacks, that creature gets +2/+0 until end of turn.\nEquip {1}")
    private val valet = Cards.invent("Sweepbrass Valet", "{3}", "Artifact Creature — Golem", "{T}: Attach target Equipment you control to target creature you control.", "1/3")

    private val pyre = Cards.invent("Pyre of Fleeting Kin", "{2}{R}", "Enchantment",
        "Whenever another creature you control dies, create a 3/1 red Elemental creature token with haste. Exile it at the beginning of the next end step.")
    private val pyreThat = Cards.invent("Brazier of Fleeting Kin", "{2}{R}", "Enchantment",
        "Whenever another creature you control dies, create a 3/1 red Elemental creature token with haste. Exile that token at the beginning of the next end step.")
    private val drill = Cards.invent("Veteran's Drill", "{1}{W}", "Enchantment",
        "Whenever a creature you control attacks, if it has a +1/+1 counter on it, draw a card.")
    private val spread = Cards.invent("Sweep Spread", "{U}", "Instant", "Proliferate.")
    private val hydra = Cards.invent("Sweepscale Hydra", "{2}{G}", "Creature — Hydra",
        "Remove X +1/+1 counters from this creature: It deals X damage to any target.", "0/0")

    /** Alice casts [clone] (in her hand) copying [copied]. */
    private fun Scenario.castsCopy(clone: String, copied: String) {
        alice.casts(clone)
        playOut(copied)
    }

    // CR 702.164c, 614.1a: toxic gives poison counters; "if you would get one or more poison counters" replaces that too: one, not two
    @Test
    fun toxicPoisonCountersAreReplaced() = scenario {
        alice.battlefield(stinger)
        bob.battlefield(warden)
        hits(stinger)
        expect { bob.counters(CounterKind.Poison) shouldBe 1 }
    }

    // CR 120.3b, 614.1a, 101.2: infect damage gives poison counters through the replacement (one), and then none more this turn
    @Test
    fun infectPoisonCountersAreReplacedThenCantBeGot() = scenario {
        alice.battlefield(blighter, "Swamp")
        bob.battlefield(warden)
        alice.hand(dose)
        hits(blighter)
        expect { bob.counters(CounterKind.Poison) shouldBe 1 }
        alice.casts(dose, target = "bob")
        resolveAll()
        expect { bob.counters(CounterKind.Poison) shouldBe 1 }
    }

    // CR 707.2, 608.2h: "it" in a dies trigger is the creature that died; it died a copy of the Boar, so the token is a Boar, not the Ooze card
    @Test
    fun diesTriggerCopiesADeadCopyAsItLastExisted() = scenario {
        alice.battlefield(echoes, boar, "Mountain")
        alice.hand(ooze, jolt)
        castsCopy(ooze, boar)
        alice.casts(jolt, target = ooze)
        playOut()
        val token = CardRef(tokens(boar).single(), "token")
        expect {
            alice.graveyard has ooze
            token.power shouldBe 2
            token hasKeyword Keyword.Trample.kind
        }
    }

    // CR 707.2, 707.9b, 608.2h: "that creature" died a copy of the Boar; the 1/1 token copies it as it last existed
    @Test
    fun diesTriggerCopyWithExceptionCopiesADeadCopyAsItLastExisted() = scenario {
        alice.battlefield(husk, boar, "Mountain")
        alice.hand(ooze, jolt)
        castsCopy(ooze, boar)
        alice.casts(jolt, target = ooze)
        playOut()
        val token = CardRef(tokens(boar).single(), "token")
        expect {
            token.power shouldBe 1
            token hasKeyword Keyword.Trample.kind
        }
    }

    // CR 603.4, 603.10a: "if it had a +1/+1 counter on it" is about the creature that died, as it last existed, not the enchantment
    @Test
    fun diesInterveningIfLooksAtTheDeadCreature() = scenario {
        alice.battlefield(tally, cub, "Mountain", "Mountain")
        alice.battlefield(pup, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.library("Island", "Island")
        alice.hand(jolt, jolt)
        alice.casts(jolt, target = cub)
        playOut()
        expect { alice.hand.size shouldBe 1 }
        alice.casts(jolt, target = pup)
        playOut()
        expect {
            alice.graveyard has pup
            alice.hand.size shouldBe 1
        }
    }

    // CR 603.10a, 608.2h: "that creature's controller" is who controlled it as it died (Bob), not its owner (Alice)
    @Test
    fun thatCreaturesControllerIsItsLastController() = scenario(players = 3) {
        carol.battlefield(reaper)
        alice.battlefield(cub, "Island", "Mountain")
        alice.hand(gift, jolt)
        alice.casts(gift, targets = listOf("bob", cub))
        resolveAll()
        expect { bob.battlefield has cub }
        alice.casts(jolt, target = cub)
        playOut()
        expect {
            alice.graveyard has cub
            bob.life shouldBe 19
            alice.life shouldBe 20
        }
    }

    // CR 603.7, 608.2c: "it" after creating a token is the token; the creature that died stays in the graveyard at the end step
    @Test
    fun diesTriggerExilesTheTokenItCreatedNotTheDeadCreature() = scenario {
        alice.battlefield(pyre, cub, "Mountain")
        alice.hand(jolt)
        alice.casts(jolt, target = cub)
        playOut()
        expect { alice.battlefield has "Elemental Token" }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.graveyard has cub
            alice.battlefield lacks "Elemental Token"
        }
    }

    // CR 603.7, 608.2c: the same with "that token"
    @Test
    fun diesTriggerExilesThatTokenNotTheDeadCreature() = scenario {
        alice.battlefield(pyreThat, cub, "Mountain")
        alice.hand(jolt)
        alice.casts(jolt, target = cub)
        playOut()
        expect { alice.battlefield has "Elemental Token" }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.graveyard has cub
            alice.battlefield lacks "Elemental Token"
        }
    }

    // CR 603.4: in an attack trigger, "if it has a +1/+1 counter on it" is about the attacking creature, not the enchantment
    @Test
    fun attackInterveningIfLooksAtTheAttacker() = scenario {
        alice.battlefield(drill)
        alice.battlefield(pup, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.library("Island", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(pup)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 701.34a, 614.1a, 101.2: proliferating gives a poison counter through the replacement; the second time this turn, none
    @Test
    fun proliferatedPoisonIsReplacedThenCantBeGot() = scenario {
        alice.battlefield("Island", "Island")
        bob.battlefield(warden)
        bob.counters(CounterKind.Poison, 1)
        alice.hand(spread, spread)
        alice.casts(spread)
        resolveAll()
        targets("bob")
        resolveAll()
        expect { bob.counters(CounterKind.Poison) shouldBe 2 }
        alice.casts(spread)
        resolveAll()
        targets("bob")
        resolveAll()
        expect { bob.counters(CounterKind.Poison) shouldBe 2 }
    }

    // CR 107.3a, 602.2b: X in an activated ability's cost is announced; two counters removed, two damage
    @Test
    fun xInARemoveCountersCostIsAnnounced() = scenario {
        alice.battlefield(hydra, counters = listOf(CounterKind.PowerToughness(1, 1) to 3))
        alice.activates(hydra, target = "bob", x = 2)
        resolveAll()
        expect {
            bob.life shouldBe 18
            hydra.power shouldBe 1
        }
    }

    // CR 603.2, 608.2c: "it" in "whenever equipped creature attacks" is the attacking creature; moving the Equipment before the trigger
    // resolves doesn't move the bonus to the newly equipped creature
    @Test
    fun equippedAttackTriggerPumpsTheAttackerAfterTheEquipmentMoves() = scenario {
        alice.battlefield(spur, valet, cub, "Plains", "Plains")
        alice.activates(spur, target = cub)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cub)
        alice.activates(valet, targets = listOf(spur, valet))
        resolveAll()
        expect {
            cub.power shouldBe 3
            valet.power shouldBe 1
        }
    }

    // CR 603.2, 608.2c: the same with "that creature"
    @Test
    fun equippedAttackTriggerPumpsThatCreatureAfterTheEquipmentMoves() = scenario {
        alice.battlefield(spurThat, valet, cub, "Plains", "Plains")
        alice.activates(spurThat, target = cub)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cub)
        alice.activates(valet, targets = listOf(spurThat, valet))
        resolveAll()
        expect {
            cub.power shouldBe 3
            valet.power shouldBe 1
        }
    }

    // CR 115.1, 611.2b, 613.1b: one target; the control change lasts past the end of turn, for as long as Alice controls the Usurper
    @Test
    fun landfallControlLastsForAsLongAsYouControlIt() = scenario(players = 3) {
        alice.battlefield(usurper, "Island")
        alice.hand("Island", gift)
        bob.battlefield(boar)
        alice.plays("Island")
        resolveAll()
        targets(boar)
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.battlefield has boar }
        alice.casts(gift, targets = listOf("bob", usurper))
        resolveAll()
        expect {
            bob.battlefield has usurper
            bob.battlefield has boar
        }
    }

    // CR 603.7, 608.2c: with no event object, "it" after creating a token is still the token, not the enchantment
    @Test
    fun combatTriggerExilesTheTokenNotItsSource() = scenario {
        alice.battlefield(beacon)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { alice.battlefield has "Pirate Token" }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield has beacon
            alice.battlefield lacks "Pirate Token"
        }
    }
}
