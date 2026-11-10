package arcanum.engine.sets.fdn

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Chunk 24 played: the invented names carry the oracle text of Eaten Alive, Thrill of Possibility, Stromkirk Noble, Vampire Interloper,
 * Vampire Soulcaller, Giant Cindermaw, High Fae Trickster, Arcane Epiphany, Archmage of Runes, Bolt Bend, Dragonlord's Servant, Ghalta
 * Primal Hunger, Luminous Rebuke, Mocking Sprite and Sanguine Indulgence.
 */
class FdnChunk24CostsAndRestrictionsScenariosTest {
    private val eaten = Cards.invent("Eaten Dead", "{B}", "Sorcery", """
        As an additional cost to cast this spell, sacrifice a creature or pay {3}{B}.
        Exile target creature or planeswalker.
    """)
    private val thrill = Cards.invent("Thrill of Chance", "{1}{R}", "Instant", """
        As an additional cost to cast this spell, discard a card.
        Draw two cards.
    """)
    private val noble = Cards.invent("Stromkirk Lord", "{R}", "Creature — Vampire Noble", """
        This creature can't be blocked by Humans.
        Whenever this creature deals combat damage to a player, put a +1/+1 counter on it.
    """, "1/1")
    private val intruder = Cards.invent("Vampire Intruder", "{1}{B}", "Creature — Vampire Scout", """
        Flying
        This creature can't block.
    """, "2/1")
    private val spiritcaller = Cards.invent("Vampire Spiritcaller", "{4}{B}", "Creature — Vampire Warlock", """
        Flying
        This creature can't block.
        When this creature enters, return target creature card from your graveyard to your hand.
    """, "3/2")
    private val jaw = Cards.invent("Giant Cinderjaw", "{2}{R}", "Creature — Dinosaur Beast", """
        Trample (This creature can deal excess combat damage to the player or planeswalker it's attacking.)
        Players can't gain life.
    """, "4/3")
    private val swindler = Cards.invent("High Fae Swindler", "{3}{U}", "Creature — Faerie Wizard", """
        Flash (You may cast this spell any time you could cast an instant.)
        Flying
        You may cast spells as though they had flash.
    """, "4/2")
    private val revelation = Cards.invent("Arcane Revelation", "{3}{U}{U}", "Instant", """
        This spell costs {1} less to cast if you control a Wizard.
        Draw three cards.
    """)
    private val glyphs = Cards.invent("Archmage of Glyphs", "{3}{U}{U}", "Creature — Giant Wizard", """
        Instant and sorcery spells you cast cost {1} less to cast.
        Whenever you cast an instant or sorcery spell, draw a card.
    """, "3/6")
    private val twist = Cards.invent("Bolt Twist", "{3}{R}", "Instant", """
        This spell costs {3} less to cast if you control a creature with power 4 or greater.
        Change the target of target spell or ability with a single target.
    """)
    private val servant = Cards.invent("Dragonking's Servant", "{1}{R}", "Creature — Goblin Shaman", "Dragon spells you cast cost {1} less to cast.", "1/3")
    private val ghalta = Cards.invent("Ghalta, Primal Hungerer", "{10}{G}{G}", "Legendary Creature — Elder Dinosaur", """
        This spell costs {X} less to cast, where X is the total power of creatures you control.
        Trample (This creature can deal excess combat damage to the player or planeswalker it's attacking.)
    """, "12/12")
    private val reproach = Cards.invent("Luminous Reproach", "{4}{W}", "Instant", """
        This spell costs {3} less to cast if it targets a tapped creature.
        Destroy target creature.
    """)
    private val sprite = Cards.invent("Mimicking Sprite", "{2}{U}", "Creature — Faerie Rogue", """
        Flying
        Instant and sorcery spells you cast cost {1} less to cast.
    """, "2/1")
    private val excess = Cards.invent("Sanguine Excess", "{3}{B}", "Sorcery", """
        This spell costs {3} less to cast if you've gained 3 or more life this turn.
        Return up to two target creature cards from your graveyard to your hand.
    """)

    private val thought = Cards.invent("Idle Thought", "{1}{U}", "Instant", "Draw a card.")
    private val rite = Cards.invent("Healing Rite", "{W}", "Sorcery", "You gain 3 life.")
    private val balm = Cards.invent("Quiet Gain", "{W}", "Instant", "You gain 3 life.")
    private val wolf = Cards.invent("Hill Wolf", "{2}{R}", "Creature — Wolf", "", "2/2")
    private val wyrmling = Cards.invent("Ember Wyrmling", "{2}{R}", "Creature — Dragon", "", "2/2")
    private val adept = Cards.invent("Wizard Adept", "{1}{U}", "Creature — Human Wizard", "", "1/1")
    private val titan = Cards.invent("Mountain Titan", "{5}{R}", "Creature — Giant", "", "10/10")
    private val brute = Cards.invent("Big Brute", "{5}{R}", "Creature — Giant", "", "9/9")
    private val strongArm = Cards.invent("Hill Giant", "{3}{R}", "Creature — Giant", "", "4/4")
    private val human = Cards.invent("Stonewall Guard", "{1}{W}", "Creature — Human Soldier", "", "1/4")
    private val wall = Cards.invent("Plain Wall", "{1}{W}", "Creature — Wall", "", "0/4")
    private val victim = Cards.invent("Lone Victim", "{1}{G}", "Creature — Elf", "", "2/2")
    private val bear = Cards.invent("Old Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val boltCard = Cards.invent("Sunder Bolt", "{R}", "Instant", "Sunder Bolt deals 3 damage to target creature.")
    private val graveBear = Cards.invent("Grave Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val graveWolf = Cards.invent("Grave Wolf", "{1}{G}", "Creature — Wolf", "", "2/2")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    private fun Scenario.stock() {
        alice.library("Island", "Forest", "Plains", "Swamp", "Mountain", "Island", "Forest", "Plains")
        bob.library("Island", "Forest", "Plains", "Swamp", "Mountain", "Island", "Forest", "Plains")
    }

    /** After the cast the spell can't be paid: the payment is still asked and nothing pays it. */
    private fun Scenario.cantPay() {
        kotlin.test.assertEquals(emptyList(), decision<Decision.ManaPayment>().suggestion)
    }

    // CR 601.2b, 601.2f: the sacrifice alternative of the additional cost
    @Test
    fun eatenSacrificesACreature() = scenario {
        alice.battlefield("Swamp", bear)
        alice.hand(eaten)
        bob.battlefield(victim)
        alice.casts(eaten, target = victim, option = "Sacrifice")
        resolveAll()
        expect {
            victim.zone shouldBe Zone.Exile
            alice.graveyard has bear
        }
    }

    // CR 601.2b: the player may pay instead and keep the creature
    @Test
    fun eatenPaysThreeMoreAndKeepsTheCreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(eaten)
        bob.battlefield(victim)
        alice.casts(eaten, target = victim, option = "Pay")
        resolveAll()
        expect {
            victim.zone shouldBe Zone.Exile
            bear.zone shouldBe Zone.Battlefield
        }
    }

    // CR 601.2b: with no creature to sacrifice the other alternative, {3}{B} more, is paid
    @Test
    fun eatenPaysThreeMoreWithoutACreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(eaten)
        bob.battlefield(victim)
        alice.casts(eaten, target = victim)
        resolveAll()
        expect {
            victim.zone shouldBe Zone.Exile
            alice.battlefield.names.count { it == "Swamp" } shouldBe 5
        }
    }

    // CR 601.2f: neither alternative can be paid with one Swamp and no creature
    @Test
    fun eatenCantBeCastWithOneSwampAndNoCreature() = scenario {
        alice.battlefield("Swamp")
        alice.hand(eaten)
        bob.battlefield(victim)
        alice.casts(eaten, target = victim)
        expect { victim.zone shouldBe Zone.Battlefield }
    }

    // CR 601.2b, 601.2h: the discard is paid as the spell is cast, then two cards are drawn
    @Test
    fun thrillDiscardsThenDraws() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(thrill, "Forest")
        alice.library("Island", "Plains", "Swamp")
        alice.casts(thrill)
        resolveAll()
        expect {
            alice.graveyard has "Forest"
            alice.graveyard has thrill
            alice.hand.size shouldBe 2
        }
    }

    // CR 509.1b: a Human can't block it; a non-Human can
    @Test
    fun nobleCantBeBlockedByAHuman() = scenario {
        alice.battlefield(noble)
        bob.battlefield(human)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(noble)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(human to noble) }
    }

    @Test
    fun nobleCanBeBlockedByANonHuman() = scenario {
        alice.battlefield(noble)
        bob.battlefield(wall)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(noble)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(wall to noble)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 20 }
    }

    // CR 603.2, 122.1: combat damage to a player puts a +1/+1 counter on it
    @Test
    fun nobleGrowsWhenUnblocked() = scenario {
        alice.battlefield(noble)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(noble)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            bob.life shouldBe 19
            noble.power shouldBe 2
        }
    }

    // CR 509.1a: a creature that can't block is not a legal blocker, while an equal creature is
    @Test
    fun intruderCantBlock() = scenario {
        alice.battlefield(strongArm)
        bob.battlefield(intruder)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(strongArm)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(intruder to strongArm) }
    }

    // CR 509.1a, 603.6a: it can't block, and returns a creature card as it enters
    @Test
    fun spiritcallerCantBlockButReturnsACreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(bear)
        alice.hand(spiritcaller)
        bob.battlefield(spiritcaller)
        alice.casts(spiritcaller)
        resolveAll()
        expect {
            alice.hand has bear
            alice.battlefield has spiritcaller
        }
    }

    @Test
    fun spiritcallerOfTheOpponentCantBlock() = scenario {
        alice.battlefield(strongArm)
        bob.battlefield(spiritcaller)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(strongArm)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<Throwable> { bob.blocks(spiritcaller to strongArm) }
    }

    // CR 119.7: no player gains life while it is on the battlefield
    @Test
    fun noPlayerGainsLifeWithTheJaw() = scenario {
        alice.battlefield(jaw, "Plains")
        alice.hand(balm)
        bob.battlefield("Plains")
        bob.hand(balm)
        alice.casts(balm)
        resolveAll()
        alice.passes()
        bob.casts(balm)
        resolveAll()
        expect {
            alice.life shouldBe 20
            bob.life shouldBe 20
        }
    }

    @Test
    fun lifeIsGainedWithoutTheJaw() = scenario {
        alice.battlefield("Plains")
        alice.hand(balm)
        alice.casts(balm)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    // CR 702.8a, 307.1: a sorcery cast in the opponent's turn needs the permission
    @Test
    fun swindlerLetsYouCastASorceryInTheOpponentsTurn() = scenario {
        turn(number = 2, active = bob, step = TurnStep.PrecombatMain, priority = alice)
        alice.battlefield(swindler, "Plains")
        alice.hand(rite)
        alice.casts(rite)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    @Test
    fun withoutTheSwindlerASorceryNeedsYourOwnTurn() = scenario {
        turn(number = 2, active = bob, step = TurnStep.PrecombatMain, priority = alice)
        alice.battlefield("Plains")
        alice.hand(rite)
        assertFailsWith<Throwable> { alice.casts(rite) }
    }

    // CR 601.2f: {1} less if you control a Wizard
    @Test
    fun revelationCostsOneLessWithAWizard() = scenario {
        alice.battlefield(adept, "Island", "Island", "Island", "Island")
        alice.hand(revelation)
        stock()
        alice.casts(revelation)
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
    }

    @Test
    fun revelationCostsFullWithoutAWizard() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(revelation)
        stock()
        alice.casts(revelation)
        cantPay()
    }

    // CR 601.2f, 603.2: spells of those types cost {1} less, and casting one draws a card
    @Test
    fun glyphsReduceAnInstantAndDraw() = scenario {
        alice.battlefield(glyphs, "Island")
        alice.hand(thought)
        stock()
        alice.casts(thought)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    // CR 601.2f: a creature spell is not an instant or sorcery
    @Test
    fun glyphsDoNotReduceACreature() = scenario {
        alice.battlefield(glyphs, "Forest")
        alice.hand(bear)
        alice.casts(bear)
        cantPay()
    }

    @Test
    fun spriteReducesASorcery() = scenario {
        alice.battlefield(sprite, "Plains")
        alice.hand(rite)
        alice.casts(rite)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    @Test
    fun spriteReducesTheGenericPartOfAnInstant() = scenario {
        alice.battlefield(sprite, "Island")
        alice.hand(thought)
        stock()
        alice.casts(thought)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 601.2f: the opponent's spells are not "spells you cast"
    @Test
    fun spriteDoesNotReduceTheOpponentsSpell() = scenario {
        alice.battlefield(sprite)
        bob.battlefield("Island")
        bob.hand(thought)
        alice.passes()
        bob.casts(thought)
        cantPay()
    }

    // CR 601.2f: with a creature of power 4 or greater it costs {R}; the target is a spell on the Stack
    @Test
    fun twistCostsOneRedWithAPowerFourCreature() = scenario {
        alice.battlefield(strongArm, "Mountain", "Mountain")
        alice.hand(boltCard, twist)
        bob.battlefield(victim)
        alice.casts(boltCard, target = victim, paying = listOf("Mountain#0"))
        alice.casts(twist, target = boltCard, paying = listOf("Mountain#1"))
        expect { stack.names.contains(twist) shouldBe true }
    }

    @Test
    fun twistCostsFullWithoutAPowerFourCreature() = scenario {
        alice.battlefield(wolf, "Mountain", "Mountain")
        alice.hand(boltCard, twist)
        bob.battlefield(victim)
        alice.casts(boltCard, target = victim, paying = listOf("Mountain#0"))
        alice.casts(twist, target = boltCard)
        cantPay()
    }

    // CR 601.2f: Dragon spells cost {1} less; other spells do not
    @Test
    fun servantReducesADragon() = scenario {
        alice.battlefield(servant, "Mountain", "Mountain")
        alice.hand(wyrmling)
        alice.casts(wyrmling)
        resolveAll()
        expect { wyrmling.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun servantDoesNotReduceANonDragon() = scenario {
        alice.battlefield(servant, "Mountain", "Mountain")
        alice.hand(wolf)
        alice.casts(wolf)
        cantPay()
    }

    // CR 601.2f, 107.3: X is the total power of the creatures its controller controls
    @Test
    fun ghaltaCostsOnlyGreenWithPowerTen() = scenario {
        alice.battlefield(titan, "Forest", "Forest")
        alice.hand(ghalta)
        alice.casts(ghalta)
        resolveAll()
        expect { ghalta.zone shouldBe Zone.Battlefield }
    }

    @Test
    fun ghaltaCostsOneMoreWithPowerNine() = scenario {
        alice.battlefield(brute, "Forest", "Forest")
        alice.hand(ghalta)
        alice.casts(ghalta)
        cantPay()
    }

    // CR 601.2c, 601.2f: the target is chosen before the cost is determined; a tapped one makes it {1}{W}
    @Test
    fun reproachCostsLessAgainstATappedCreature() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(reproach)
        bob.battlefield(victim, tapped = true)
        alice.casts(reproach, target = victim)
        resolveAll()
        expect { victim.zone shouldBe Zone.Graveyard }
    }

    @Test
    fun reproachCostsFullAgainstAnUntappedCreature() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(reproach)
        bob.battlefield(victim)
        alice.casts(reproach, target = victim)
        cantPay()
    }

    // CR 601.2f, 119.1: gaining 3 life this turn first makes it cost {B}
    @Test
    fun excessCostsOneBlackAfterGaining3Life() = scenario {
        alice.battlefield("Plains", "Swamp")
        alice.hand(balm, excess)
        alice.graveyard(graveBear, graveWolf)
        alice.casts(balm)
        resolveAll()
        alice.casts(excess, targets = listOf(graveBear, graveWolf))
        resolveAll()
        expect {
            alice.hand has graveBear
            alice.hand has graveWolf
        }
    }

    @Test
    fun excessCostsFullWithoutTheLife() = scenario {
        alice.battlefield("Swamp")
        alice.hand(excess)
        alice.graveyard(graveBear, graveWolf)
        alice.casts(excess, targets = listOf(graveBear, graveWolf))
        cantPay()
    }
}
