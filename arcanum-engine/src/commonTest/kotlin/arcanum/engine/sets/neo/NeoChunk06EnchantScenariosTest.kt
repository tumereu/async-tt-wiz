package arcanum.engine.sets.neo

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** Chunk 6 of NEO: Auras that animate a land and save it from destruction, draw only on the strongest creature, stop crewing, unattach an
 * Equipment, set a crewed Vehicle's base power, grant an upkeep life loss to an opponent's creature, leave a token behind, and destroy on entering
 * with three players. */
class NeoChunk06EnchantScenariosTest {
    private val sparkvein = Cards.invent("Sparkvein Awakening", "{1}{R}", "Enchantment — Aura", """
        Enchant land you control
        Enchanted land is a 3/3 red Spirit creature with haste. It's still a land.
        If enchanted land would be destroyed, instead sacrifice this Aura and that land gains indestructible until end of turn.
    """)
    private val chronicler = Cards.invent("Chronicler's Insight", "{2}{G}", "Enchantment — Aura", """
        Enchant artifact or creature
        When this Aura enters, if enchanted permanent is a creature with the greatest power among creatures on the battlefield, draw a card.
        As long as enchanted permanent is a creature, it gets +2/+1.
    """)
    private val detention = Cards.invent("Mediator's Detention", "{2}{W}", "Enchantment — Aura", """
        Enchant permanent
        Enchanted permanent can't attack, block, or crew Vehicles. Its activated abilities can't be activated unless they're mana abilities.
    """)
    private val phyresis = Cards.invent("Scholar's Phyresis", "{3}{U}", "Enchantment — Aura", """
        Flash
        Enchant artifact, creature, or planeswalker
        When this Aura enters, tap enchanted permanent. If it's an Equipment, unattach it.
        Enchanted permanent loses all abilities and doesn't untap during its controller's untap step.
    """)
    private val perception = Cards.invent("Roused Perception", "{X}{U}{U}", "Enchantment — Aura", """
        Enchant artifact or creature
        When this Aura enters, put X +1/+1 counters on enchanted permanent.
        As long as enchanted permanent is a creature, it has base power and toughness 1/1.
    """)
    private val anguish = Cards.invent("Raking Anguish", "{B}", "Enchantment — Aura", """
        Enchant artifact or creature
        As long as enchanted permanent is a creature, it gets -1/-1 and can't block.
        Enchanted permanent has "At the beginning of your upkeep, you lose 1 life."
    """)
    private val boon = Cards.invent("Boon of Fury", "{3}{R}", "Enchantment — Aura", """
        Enchant artifact or creature
        As long as enchanted permanent is a creature, it gets +2/+2 and has menace. (It can't be blocked except by two or more creatures.)
        When this Aura leaves the battlefield, create a 2/2 red Spirit creature token with menace.
    """)
    private val clasp = Cards.invent("Warped Clasp", "{2}{B}{B}", "Enchantment — Aura", """
        Enchant artifact or creature you control
        When this Aura enters, destroy target creature or planeswalker an opponent controls.
        As long as enchanted permanent is a creature, it gets +1/+1.
    """)

    private val bear = Cards.invent("Neo Aura Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Neo Aura Ogre", "{2}{R}", "Creature — Ogre", "", "4/4")
    private val giant = Cards.invent("Neo Aura Giant", "{4}{R}", "Creature — Giant", "", "5/5")
    private val trinket = Cards.invent("Neo Aura Trinket", "{1}", "Artifact", "")
    private val cart = Cards.invent("Neo Aura Cart", "{2}", "Artifact — Vehicle",
        "Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)", "5/5")
    private val blade = Cards.invent("Neo Aura Blade", "{1}", "Artifact — Equipment", "Equipped creature gets +2/+0.\nEquip {1}")
    private val elf = Cards.invent("Neo Aura Mana Elf", "{G}", "Creature — Elf", "{T}: Add {G}.", "1/1")
    private val razeLand = Cards.invent("Neo Aura Land Razing", "{0}", "Instant", "Destroy target land.")
    private val bolt = Cards.invent("Neo Aura Jolt", "{0}", "Instant", "Neo Aura Jolt deals 3 damage to target creature.")
    private val shatter = Cards.invent("Neo Aura Shatter", "{0}", "Instant", "Destroy target enchantment.")

    private fun Scenario.islands(n: Int) = alice.battlefield(*Array(n) { "Island" })

    // CR 205.1b, 613.1d-f, 613.4b: a 3/3 red Spirit creature with haste that is still a Mountain
    @Test
    fun sparkveinAwakeningAnimatesTheLand() = scenario {
        alice.battlefield("Mountain", sparkvein)
        attach(sparkvein, "Mountain")
        expect {
            "Mountain".types shouldBe listOf(CardType.Land, CardType.Creature)
            "Mountain".subtypes.toSet() shouldBe setOf(Subtype("Mountain"), Subtype("Spirit"))
            "Mountain".colors shouldBe listOf(Color.Red)
            "Mountain".power shouldBe 3
            "Mountain".toughness shouldBe 3
            "Mountain" hasKeyword Keyword.Haste.kind
        }
    }

    // CR 614.1a, 614.6: destroying the land sacrifices the Aura instead; the land stays and is indestructible until end of turn
    @Test
    fun sparkveinAwakeningSavesTheLandFromDestruction() = scenario {
        alice.battlefield("Mountain", sparkvein)
        attach(sparkvein, "Mountain")
        bob.hand(razeLand)
        turn(priority = bob)
        bob.casts(razeLand, target = "Mountain")
        allPass()
        expect {
            "Mountain".zone shouldBe Zone.Battlefield
            sparkvein.zone shouldBe Zone.Graveyard
            "Mountain" hasKeyword Keyword.Indestructible.kind
            "Mountain".types shouldBe listOf(CardType.Land)
        }
    }

    // CR 704.5g, 614.1a: lethal damage destroys it as a state-based action, and that destruction is replaced too
    @Test
    fun sparkveinAwakeningSavesTheLandFromLethalDamage() = scenario {
        alice.battlefield("Mountain", sparkvein)
        attach(sparkvein, "Mountain")
        bob.hand(bolt)
        turn(priority = bob)
        bob.casts(bolt, target = "Mountain")
        allPass()
        expect {
            "Mountain".zone shouldBe Zone.Battlefield
            sparkvein.zone shouldBe Zone.Graveyard
            "Mountain" hasKeyword Keyword.Indestructible.kind
        }
    }

    // CR 603.4, 208.1: with its own +2/+1 the Bear is a 4/3, tied for the greatest power with the 4/4: it draws
    @Test
    fun chroniclersInsightDrawsWhenTiedForTheGreatestPower() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", bear)
        bob.battlefield(ogre)
        alice.hand(chronicler)
        alice.library("Island")
        alice.casts(chronicler, target = bear)
        playOut()
        expect {
            alice.hand has "Island"
            bear.power shouldBe 4
        }
    }

    // CR 603.4: a greater creature elsewhere (with three players) stops the draw
    @Test
    fun chroniclersInsightDoesNothingUnderAStrongerCreature() = scenario(players = 3) {
        alice.battlefield("Forest", "Forest", "Forest", bear)
        bob.battlefield(ogre)
        carol.battlefield(giant)
        alice.hand(chronicler)
        alice.library("Island")
        alice.casts(chronicler, target = bear)
        playOut()
        expect { alice.hand lacks "Island" }
    }

    // CR 603.4: on a noncreature artifact the condition is false
    @Test
    fun chroniclersInsightOnAnArtifactDoesNothing() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", trinket)
        alice.hand(chronicler)
        alice.library("Island")
        alice.casts(chronicler, target = trinket)
        playOut()
        expect { alice.hand lacks "Island" }
    }

    // CR 702.122a: the enchanted creature can't be tapped to crew; another creature still can
    @Test
    fun mediatorsDetentionStopsCrewing() = scenario {
        alice.battlefield(cart, ogre, giant, detention)
        attach(detention, ogre)
        alice.activates(cart)
        val crew = pending as Decision.ChooseObjects
        assertEquals(listOf(card(giant)), crew.options)
        answer(Answer.Objects(listOf(card(giant))))
        allPass()
        expect {
            cart.types shouldBe listOf(CardType.Artifact, CardType.Creature)
            ogre.tapped shouldBe false
        }
    }

    // CR 508.1c: the enchanted creature can't attack; CR 605.1a: its mana abilities can still be activated
    @Test
    fun mediatorsDetentionStopsAttacksButNotManaAbilities() = scenario {
        alice.battlefield(elf, detention)
        attach(detention, elf)
        turn(step = TurnStep.PrecombatMain)
        alice.activates(elf)
        expect { elf.tapped shouldBe true }
        advanceTo(TurnStep.DeclareAttackers)
        assertFails { alice.attacks(elf) }
    }

    // CR 701.3d: tapped and unattached, the Equipment stays on the battlefield; CR 613.1f: it loses its abilities
    @Test
    fun scholarsPhyresisUnattachesAnEquipment() = scenario {
        islands(4)
        bob.battlefield(bear, blade)
        attach(blade, bear)
        alice.hand(phyresis)
        alice.casts(phyresis, target = blade)
        playOut()
        expect {
            blade.tapped shouldBe true
            blade.zone shouldBe Zone.Battlefield
            bear.power shouldBe 2
            phyresis.zone shouldBe Zone.Battlefield
        }
    }

    // CR 608.2h: a creature isn't an Equipment: it is only tapped
    @Test
    fun scholarsPhyresisTapsACreature() = scenario {
        islands(4)
        bob.battlefield(ogre)
        alice.hand(phyresis)
        alice.casts(phyresis, target = ogre)
        playOut()
        expect {
            ogre.tapped shouldBe true
            phyresis.zone shouldBe Zone.Battlefield
        }
    }

    // CR 107.3m, 613.4b-c: X = 3 counters on a Vehicle; once crewed it's a 1/1 base with three +1/+1 counters (4/4)
    @Test
    fun rousedPerceptionOnAVehicleSetsItsBaseWhenCrewed() = scenario {
        islands(5)
        alice.battlefield(cart, ogre)
        alice.hand(perception)
        alice.casts(perception, target = cart, x = 3)
        playOut()
        expect { cart.counters(CounterKind.PowerToughness(1, 1)) shouldBe 3 }
        alice.activates(cart, choosing = listOf(ogre))
        allPass()
        expect {
            cart.power shouldBe 4
            cart.toughness shouldBe 4
        }
    }

    // CR 113.10, 503.1a: the granted ability is the enchanted creature's; its controller (Bob) loses the life in Bob's upkeep
    @Test
    fun rakingAnguishMakesTheEnchantedCreaturesControllerLoseLife() = scenario {
        alice.battlefield(anguish)
        bob.battlefield(ogre)
        attach(anguish, ogre)
        players.forEach { it.library("Island", "Island") }
        advanceTo(TurnStep.Upkeep, turn = 2)
        while (state.stack.isNotEmpty() && pending == null) allPass()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 20
            ogre.power shouldBe 3
        }
    }

    // CR 603.6c, 603.10a: the Aura leaving makes a 2/2 red Spirit with menace for its controller
    @Test
    fun boonOfFuryLeavesASpiritBehind() = scenario {
        alice.battlefield(bear, boon)
        attach(boon, bear)
        bob.hand(shatter)
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Menace.kind
        }
        turn(priority = bob)
        bob.casts(shatter, target = boon)
        allPass()
        while (state.stack.isNotEmpty() && pending == null) allPass()
        expect {
            "Spirit Token".controller shouldBe alice.id
            "Spirit Token".power shouldBe 2
            "Spirit Token" hasKeyword Keyword.Menace.kind
            bear.power shouldBe 2
        }
    }

    // CR 303.4a (only on your own artifact or creature), 603.3d: destroy a creature of any opponent (three players)
    @Test
    fun warpedClaspDestroysAnyOpponentsCreature() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", bear)
        bob.battlefield(ogre)
        carol.battlefield(giant)
        alice.hand(clasp)
        alice.casts(clasp, target = bear)
        playOut(giant)
        expect {
            giant.zone shouldBe Zone.Graveyard
            ogre.zone shouldBe Zone.Battlefield
            bear.power shouldBe 3
        }
    }

    // CR 303.4a: "you control": an opponent's creature can't be enchanted
    @Test
    fun warpedClaspCantEnchantAnOpponentsCreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        bob.battlefield(ogre)
        alice.hand(clasp)
        assertTrue(runCatching { alice.casts(clasp, target = ogre) }.isFailure)
    }
}
