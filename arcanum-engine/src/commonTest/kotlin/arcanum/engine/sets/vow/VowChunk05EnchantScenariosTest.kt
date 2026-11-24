package arcanum.engine.sets.vow

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.state.TargetRef
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** VOW chunk 05: Auras whose abilities give the engine something new: trample for creatures attacking the enchanted player and a
 * permission for the damaging creature's controller, an Aura returning transformed attached to an opponent, flash while you control a
 * Spirit, a land granted "two mana of any one color", a type condition with "Otherwise", a granted enters trigger, and an activated exile. */
class VowChunk05EnchantScenariosTest {
    private val curse = Cards.invent("Curse of Open Doors", "{2}{R}", "Enchantment — Aura Curse", """
        Enchant player
        Creatures attacking enchanted player have trample.
        Whenever a creature deals combat damage to enchanted player, that player exiles the top card of their library. Until end of turn, that creature's controller may play that card and they may spend mana as though it were mana of any color to cast that spell.
    """.trimIndent())
    private val favor = Cards.inventFaces("Transform",
        face("Gleaming Favor", "{W}", "Enchantment — Aura", """
            Enchant creature
            Enchanted creature gets +1/+0 and has vigilance.
            When enchanted creature dies, return this card to the battlefield transformed under your control attached to target opponent.
        """.trimIndent(), null, null, null),
        face("Gleaming Fetters", null, "Enchantment — Aura Curse", """
            Enchant player
            Creatures enchanted player controls enter tapped.
        """.trimIndent(), null, null, "W"))
    private val rescue = Cards.invent("Spectral Deliverance", "{3}{W}", "Enchantment — Aura", """
        This spell has flash as long as you control a Spirit.
        When you cast this spell, tap up to two target creatures you don't control.
        Enchant creature you control
        Enchanted creature gets +1/+2.
    """.trimIndent())
    private val clasp = Cards.invent("Wildwood Clasp", "{2}{G}", "Enchantment — Aura", """
        Enchant creature or land
        As long as enchanted permanent is a creature, it gets +2/+2.
        As long as enchanted permanent is a land, it has "{T}: Add two mana of any one color."
    """.trimIndent())
    private val boon = Cards.invent("Boon of the Bite", "{B}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+2 as long as it's a Vampire. Otherwise, it gets -2/-2.
    """.trimIndent())
    private val presence = Cards.invent("Nursing Presence", "{1}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature has "Whenever a creature you control enters, this creature gets +1/+1 until end of turn."
        When this Aura enters, create a 1/1 white Spirit creature token with flying.
    """.trimIndent())
    private val confinement = Cards.invent("Angelic Confinement", "{2}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature can't attack or block.
        {4}{W}: Exile enchanted creature. Create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
    """.trimIndent())
    private val dread = Cards.invent("Dread of the Grave", "{1}{U}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, mill two cards. (Put the top two cards of your library into your graveyard.)
        Enchanted creature gets -X/-0, where X is the number of cards in your graveyard.
    """.trimIndent())

    private val ogre = Cards.invent("Doorway Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val brute = Cards.invent("Doorway Brute", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val pup = Cards.invent("Doorway Pup", "{G}", "Creature — Dog", "", "1/1")
    private val sprout = Cards.invent("Doorway Sprout", "{1}{G}", "Creature — Plant", "", "2/2")
    private val bear = Cards.invent("Clasp Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val elk = Cards.invent("Clasp Elk", "{1}{G}", "Creature — Elk", "", "2/2")
    private val vampire = Cards.invent("Clasp Vampire", "{1}{B}", "Creature — Vampire", "", "2/2")
    private val ox = Cards.invent("Clasp Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val shade = Cards.invent("Clasp Shade", "{1}{W}", "Creature — Spirit", "", "1/1")
    private val sentry = Cards.invent("Clasp Sentry", "{1}{W}", "Creature — Human", "", "1/1")
    private val tidecaller = Cards.invent("Clasp Tidecaller", "{U}{U}", "Creature — Merfolk", "", "2/2")
    private val growth = Cards.invent("Doorway Growth", "{1}{G}", "Instant", "Target creature gets +1/+1 until end of turn.")
    private val seizure = Cards.invent("Clasp Seizure", "{U}", "Instant", "Gain control of target creature until end of turn.")
    private val spark = Cards.invent("Clasp Spark", "{R}", "Instant", "Clasp Spark deals 3 damage to target creature.")

    // CR 506.3, 702.19c: only creatures attacking the enchanted player have trample; CR 510.3a, 611.2a, 609.4b: the card bob exiles may be
    // played by carol (the damaging creature's controller, not the Curse's) until end of turn, paying green with red mana
    @Test
    fun attackersOfTheEnchantedPlayerTrampleAndTheirControllerPlaysTheCard() = scenario(players = 3) {
        alice.battlefield(curse)
        attach(curse, "bob")
        bob.battlefield(pup)
        bob.library(sprout, "Plains")
        carol.battlefield(ogre, brute, "Mountain", "Mountain")
        turn(number = 3, active = carol)
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(carol.id, listOf(Attack(card(ogre), TargetRef.Player(bob.id)), Attack(card(brute), TargetRef.Player(alice.id)))))
        expect {
            ogre hasKeyword Keyword.Trample.kind
            brute lacksKeyword Keyword.Trample.kind
        }
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(pup to ogre)
        advanceTo(TurnStep.CombatDamage)
        if (pending is Decision.Divide) divideAsSuggested()
        resolveAll()
        expect {
            bob.life shouldBe 18
            sprout.zone shouldBe Zone.Exile
        }
        advanceTo(TurnStep.PostcombatMain)
        carol.casts(sprout)
        resolveAll()
        expect { carol.battlefield has sprout }
    }

    // CR 611.2a: the permission is the damaging creature's controller's only; the Curse's controller gets none (an instant, so either
    // could cast it now)
    @Test
    fun theCursesControllerCantPlayTheCard() = scenario(players = 3) {
        alice.battlefield(curse, "Forest", "Forest")
        attach(curse, "bob")
        bob.library(growth, "Plains")
        carol.battlefield(ogre, "Mountain", "Mountain")
        turn(number = 3, active = carol)
        advanceTo(TurnStep.DeclareAttackers)
        carol.attacks(ogre, defender = "bob")
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { growth.zone shouldBe Zone.Exile }
        advanceTo(TurnStep.PostcombatMain)
        assertEquals(1, Engine.options(state, carol.id).filterIsInstance<Input.Cast>().count { it.card == card(growth) })
        carol.passes()
        assertEquals(alice.id, state.priority.holder)
        assertEquals(0, Engine.options(state, alice.id).filterIsInstance<Input.Cast>().count { it.card == card(growth) })
    }

    // CR 608.2h, 611.2c: "that creature's controller" is found as the trigger resolves; taking control of the creature afterwards doesn't
    // move the permission
    @Test
    fun takingTheCreatureLaterDoesntTakeThePermission() = scenario(players = 3) {
        alice.battlefield(curse, "Forest", "Forest", "Island")
        alice.hand(seizure)
        attach(curse, "bob")
        bob.library(growth, "Plains")
        carol.battlefield(ogre, "Mountain", "Mountain")
        turn(number = 3, active = carol)
        advanceTo(TurnStep.DeclareAttackers)
        carol.attacks(ogre, defender = "bob")
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        carol.passes()
        alice.casts(seizure, target = ogre)
        resolveAll()
        expect { ogre.controller shouldBe alice.id }
        // CR 117.3b: the active player receives priority after the spell resolves
        carol.passes()
        assertEquals(alice.id, state.priority.holder)
        assertEquals(0, Engine.options(state, alice.id).filterIsInstance<Input.Cast>().count { it.card == card(growth) })
    }

    // CR 400.7, 603.10a, 712.14a, 303.4f: the Aura returns back face up, under its controller, attached to the opponent chosen as the target;
    // CR 614.1c, 303.4e: then only that player's creatures enter tapped
    @Test
    fun theAuraReturnsAsACurseOnTheChosenOpponent() = scenario(players = 3) {
        alice.battlefield(bear, favor, "Mountain")
        attach(favor, bear)
        alice.hand(spark)
        alice.library("Plains", "Plains", "Plains")
        bob.library("Plains", "Plains", "Plains")
        bob.battlefield("Forest", "Forest")
        bob.hand(elk)
        carol.library("Plains", "Plains", "Plains")
        carol.battlefield("Forest", "Forest", "Forest")
        carol.hand(ox)
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Vigilance.kind
        }
        alice.casts(spark, target = bear)
        resolveAll()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target("carol"))))
        resolveAll()
        expect { bear.zone shouldBe Zone.Graveyard }
        val returned = state.obj(card(favor))!!
        assertEquals(Zone.Battlefield, returned.zone)
        assertEquals(1, returned.face)
        assertEquals(alice.id, returned.controller)
        assertEquals(TargetRef.Player(carol.id), returned.attachedTo)
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.casts(elk)
        resolveAll()
        expect { card(elk).tapped shouldBe false }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        carol.casts(ox)
        resolveAll()
        expect { card(ox).tapped shouldBe true }
    }

    // CR 702.8a, 604.1: with a Spirit it can be cast on an opponent's turn; CR 601.2i, 603.2: its cast trigger taps creatures of both
    // opponents (CR 115.6: up to two)
    @Test
    fun withASpiritItHasFlashAndTapsTwoOpponentsCreatures() = scenario(players = 3) {
        alice.battlefield(shade, "Plains", "Plains", "Plains", "Plains")
        alice.hand(rescue)
        bob.battlefield(bear)
        carol.battlefield(elk)
        turn(number = 2, active = bob, priority = alice)
        alice.casts(rescue, target = shade)
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(bear), target(elk))))
        resolveAll()
        expect {
            card(bear).tapped shouldBe true
            card(elk).tapped shouldBe true
            alice.battlefield has rescue
            shade.power shouldBe 2
            shade.toughness shouldBe 3
        }
    }

    // CR 307.1, 604.1: without a Spirit it can't be cast on an opponent's turn
    @Test
    fun withoutASpiritItHasNoFlash() = scenario(players = 3) {
        alice.battlefield(sentry, "Plains", "Plains", "Plains", "Plains")
        alice.hand(rescue)
        bob.battlefield(bear)
        turn(number = 2, active = bob, priority = alice)
        assertFailsWith<IllegalArgumentException> { alice.casts(rescue, target = sentry) }
    }

    // CR 106.1b, 605.1a: the enchanted land taps for two mana of one chosen colour, here {U}{U} from a Forest
    @Test
    fun anEnchantedLandAddsTwoManaOfOneColor() = scenario {
        alice.battlefield("Forest", clasp)
        attach(clasp, "Forest")
        alice.hand(tidecaller)
        alice.casts(tidecaller, paying = listOf("Forest:U"))
        resolveAll()
        expect { alice.battlefield has tidecaller }
    }

    // CR 611.3a: on a creature it gets +2/+2 and grants no mana ability
    @Test
    fun anEnchantedCreatureGetsPlusTwo() = scenario {
        alice.battlefield(bear, clasp)
        attach(clasp, bear)
        expect {
            bear.power shouldBe 4
            bear.toughness shouldBe 4
        }
    }

    // CR 611.3a: +2/+2 on a Vampire
    @Test
    fun theBoonPumpsAVampire() = scenario {
        alice.battlefield(vampire, boon)
        attach(boon, vampire)
        expect {
            vampire.power shouldBe 4
            vampire.toughness shouldBe 4
        }
    }

    // CR 611.3a: "Otherwise": -2/-2 on a creature that isn't a Vampire
    @Test
    fun theBoonShrinksANonVampire() = scenario {
        alice.battlefield(ox, boon)
        attach(boon, ox)
        expect {
            ox.power shouldBe 1
            ox.toughness shouldBe 1
        }
    }

    // CR 603.2, 113.6: the token the Aura makes enters under the enchanted creature's controller, so the granted trigger pumps it
    @Test
    fun theSpiritTokenPumpsTheEnchantedCreature() = scenario {
        alice.battlefield(bear, "Plains", "Plains")
        alice.hand(presence)
        alice.casts(presence, target = bear)
        resolveAll()
        expect {
            alice.battlefield has "Spirit Token"
            bear.power shouldBe 3
            bear.toughness shouldBe 3
        }
    }

    // CR 109.5: on an opponent's creature "a creature you control" is that opponent's; the Aura controller's token pumps nothing
    @Test
    fun onAnOpponentsCreatureTheirCreaturesPumpIt() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains")
        alice.hand(presence)
        bob.battlefield(bear)
        alice.casts(presence, target = bear)
        resolveAll()
        expect {
            alice.battlefield has "Spirit Token"
            bear.power shouldBe 2
        }
    }

    // CR 602.1, 406.1, 111.10o: the activation exiles the enchanted creature and makes a Blood token
    @Test
    fun theConfinementExilesTheCreatureAndMakesBlood() = scenario {
        alice.battlefield(confinement, "Plains", "Plains", "Plains", "Plains", "Plains")
        bob.battlefield(ox)
        attach(confinement, ox)
        alice.activates(confinement)
        resolveAll()
        expect {
            ox.zone shouldBe Zone.Exile
            alice.battlefield has "Blood Token"
        }
        assertEquals(1, tokens("Blood Token").size)
    }

    // CR 701.17a, 611.3a: X counts the Aura controller's graveyard, the milled cards included
    @Test
    fun theDreadCountsTheMilledCards() = scenario {
        alice.battlefield("Island", "Island")
        alice.graveyard(spark)
        alice.library("Plains", "Plains", "Plains")
        alice.hand(dread)
        bob.battlefield(ox)
        alice.casts(dread, target = ox)
        resolveAll()
        expect {
            alice.graveyard.size shouldBe 3
            ox.power shouldBe 0
            ox.toughness shouldBe 3
        }
    }
}
