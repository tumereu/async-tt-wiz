package arcanum.engine.sets.ecl

import arcanum.api.Ability
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ecl.Ecl.bear
import arcanum.engine.sets.ecl.Ecl.elf
import arcanum.engine.sets.ecl.Ecl.goblin
import arcanum.engine.sets.ecl.Ecl.jolt
import arcanum.engine.sets.ecl.Ecl.kithkin
import arcanum.engine.sets.ecl.Ecl.m1m1
import arcanum.engine.sets.ecl.Ecl.ogre
import arcanum.engine.sets.ecl.Ecl.redBear
import arcanum.engine.sets.ecl.Ecl.totem
import kotlin.test.Test
import kotlin.test.assertEquals

/** Lorwyn Eclipsed's transforming legends, Decrees and Incarnations in play (chunks 7–8): a convoke given to creature spells, a
 * transforming planeswalker, triggers on entering or transforming, choose-two Kindred spells, and enters triggers that depend on the
 * mana spent (evoke). */
class EclLegendsAndDecreesScenariosTest {
    private val solvanne = Cards.inventFaces("Transform",
        face("Solvanne, Bearer of Morning", "{3}{W}{W}", "Legendary Creature — Elemental God", """
            Flying, lifelink
            Creature spells you cast have convoke. (Your creatures can help cast those spells. Each creature you tap while casting a creature spell pays for {1} or one mana of that creature's color.)
            At the beginning of your first main phase, you may pay {B}. If you do, transform Solvanne.
        """.trimIndent(), "5/5", null, null),
        face("Nocthra, Bearer of Dusk", null, "Legendary Creature — Elemental God", """
            Flying, lifelink
            Each other nontoken creature you control has persist. (When it dies, if it had no -1/-1 counters on it, return it to the battlefield under its owner's control with a -1/-1 counter on it.)
            At the beginning of your first main phase, you may pay {W}. If you do, transform Nocthra.
        """.trimIndent(), "5/5", null, "B"))
    private val fenn = Cards.inventFaces("Transform",
        face("Fenn, Meadow Sovereign", "{2}{U}", "Legendary Planeswalker — Oko", """
            At the beginning of your first main phase, you may pay {G}. If you do, transform Fenn.
            [+2]: Up to one target creature gains all creature types. (This effect doesn't end.)
            [+1]: Target creature gets -2/-0 until your next turn.
        """.trimIndent(), null, "3", null),
        face("Fenn, Gloomvale Heir", null, "Legendary Planeswalker — Oko", """
            At the beginning of your first main phase, you may pay {U}. If you do, transform Fenn.
            [−1]: Mill three cards. You may put a permanent card from among them into your hand.
            [−3]: Create two 3/3 green Elk creature tokens.
            [−6]: Choose a creature type. You get an emblem with "Creatures you control of the chosen type get +3/+3 and have vigilance and hexproof."
        """.trimIndent(), null, "3", "G"))
    private val tellis = Cards.inventFaces("Transform",
        face("Tellis, Riverwine Sage", "{1}{U}", "Legendary Creature — Merfolk Wizard", """
            Tellis can't be blocked.
            Whenever this creature enters or transforms into Tellis, Riverwine Sage, target creature gains "Whenever this creature deals combat damage to a player or planeswalker, draw a card" until end of turn.
            At the beginning of your first main phase, you may pay {W}. If you do, transform Tellis.
        """.trimIndent(), "2/2", null, null),
        face("Tellis, Brinewalk Guard", null, "Legendary Creature — Merfolk Rogue", """
            Tellis can't be blocked.
            Whenever this creature transforms into Tellis, Brinewalk Guard, target creature you control gains protection from each color until your next turn.
            At the beginning of your first main phase, you may pay {U}. If you do, transform Tellis.
        """.trimIndent(), "2/2", null, "W"))
    private val nettle = Cards.inventFaces("Transform",
        face("Nettle, Fabled Elder", "{2}{B}", "Legendary Creature — Goblin Warlock", """
            Menace
            Whenever this creature enters or transforms into Nettle, Fabled Elder, return up to one target Goblin card from your graveyard to your hand.
            At the beginning of your first main phase, you may pay {R}. If you do, transform Nettle.
        """.trimIndent(), "2/1", null, null),
        face("Nettle, Infamous Granny", null, "Legendary Creature — Goblin Warrior", """
            Menace
            Whenever Nettle attacks, you may blight 1. If you do, create a tapped and attacking token that's a copy of the blighted creature, except it has "At the beginning of the end step, sacrifice this token."
            At the beginning of your first main phase, you may pay {B}. If you do, transform Nettle.
        """.trimIndent(), "2/1", null, "R"))
    private val cinna = Cards.inventFaces("Transform",
        face("Cinna, Relit", "{1}{R}", "Legendary Creature — Elemental Sorcerer", """
            Whenever this creature enters or transforms into Cinna, Relit, you may discard a card. If you do, draw a card.
            At the beginning of your first main phase, you may pay {U}. If you do, transform Cinna.
        """.trimIndent(), "1/3", null, null),
        face("Cinna, Frostbound", null, "Legendary Creature — Elemental Wizard", """
            Whenever this creature transforms into Cinna, Frostbound and at the beginning of your first main phase, add two mana of any one color. Spend this mana only to cast spells with mana value 4 or greater.
            At the beginning of your first main phase, you may pay {R}. If you do, transform Cinna.
        """.trimIndent(), "1/3", null, "U"))
    private val orwen = Cards.inventFaces("Transform",
        face("Orwen, Heartless Grower", "{2}{G}", "Legendary Creature — Elf Druid", """
            Deathtouch
            Whenever this creature enters or transforms into Orwen, Heartless Grower, mill three cards. Then if there is an Elf card in your graveyard, you gain 2 life.
            At the beginning of your first main phase, you may pay {B}. If you do, transform Orwen.
        """.trimIndent(), "3/4", null, null),
        face("Orwen, Remorseful Reaper", null, "Legendary Creature — Elf Warlock", """
            Deathtouch
            Whenever this creature transforms into Orwen, Remorseful Reaper, mill three cards, then you may exile an Elf card from your graveyard. If you do, each opponent loses 2 life.
            At the beginning of your first main phase, you may pay {G}. If you do, transform Orwen.
        """.trimIndent(), "3/4", null, "B"))
    private val cinnasDecree = Cards.invent("Cinna's Decree", "{3}{U}{R}", "Kindred Instant — Elemental", """
        Choose two —
        • Create a token that's a copy of target Elemental you control.
        • Target player draws two cards.
        • Cinna's Decree deals 2 damage to each creature target player controls.
        • Target player creates two Treasure tokens.
    """)
    private val maewensDecree = Cards.invent("Maewen's Decree", "{1}{G}{W}", "Kindred Sorcery — Kithkin", """
        Choose two —
        • Create a token that's a copy of target Kithkin you control.
        • Target player creates a 1/1 green and white Kithkin creature token.
        • Target creature you control gets +3/+3 until end of turn.
        • Target creature you control fights target creature an opponent controls.
    """)
    private val unburdening = Cards.invent("Unburdening", "{4}{R/W}{R/W}", "Creature — Elemental Incarnation", """
        When this creature enters, if {W}{W} was spent to cast it, create two 1/1 green and white Kithkin creature tokens.
        When this creature enters, if {R}{R} was spent to cast it, creatures you control get +1/+1 and gain haste until end of turn.
        Evoke {R/W}{R/W} (You may cast this spell for its evoke cost. If you do, it's sacrificed when it enters.)
    """, "3/4")
    private val chicanery = Cards.invent("Chicanery", "{4}{U/B}{U/B}", "Creature — Elemental Incarnation", """
        When this creature enters, if {U}{U} was spent to cast it, return up to one other target nonland permanent to its owner's hand.
        When this creature enters, if {B}{B} was spent to cast it, target opponent reveals their hand. You choose a nonland card from it. That player discards that card.
        Evoke {U/B}{U/B}
    """, "5/5")
    private val hollowness = Cards.invent("Hollowness", "{4}{W/B}{W/B}", "Creature — Elemental Incarnation", """
        When this creature enters, if {W}{W} was spent to cast it, return target creature card with mana value 3 or less from your graveyard to the battlefield.
        When this creature enters, if {B}{B} was spent to cast it, put three -1/-1 counters on up to one target creature.
        Evoke {W/B}{W/B}
    """, "3/5")
    private val nettlesDecree = Cards.invent("Nettle's Decree", "{3}{B}{R}", "Kindred Sorcery — Goblin", """
        Choose two —
        • Create a token that's a copy of target Goblin you control.
        • Creatures target player controls get +1/+1 and gain haste until end of turn.
        • Destroy target artifact or creature.
        • Target player mills five cards, then puts each Goblin card milled this way into their hand.
    """)
    private val tellissDecree = Cards.invent("Tellis's Decree", "{1}{W}{U}", "Kindred Sorcery — Merfolk", """
        Choose two —
        • Create a token that's a copy of target Merfolk you control.
        • Creatures target player controls gain lifelink until end of turn.
        • Target player draws a card.
        • Tap target creature. Put a stun counter on it.
    """)
    private val orwensDecree = Cards.invent("Orwen's Decree", "{4}{B}{G}", "Kindred Sorcery — Elf", """
        Choose two —
        • Create a token that's a copy of target Elf you control.
        • Return one or two target permanent cards from your graveyard to your hand.
        • Destroy target creature or enchantment.
        • Creatures target player controls get +3/+3 until end of turn. Untap them.
    """)
    private val yearning = Cards.invent("Yearning", "{3}{G/U}{G/U}", "Creature — Elemental Incarnation", """
        When this creature enters, if {G}{G} was spent to cast it, exile target artifact or enchantment an opponent controls.
        When this creature enters, if {U}{U} was spent to cast it, draw two cards, then discard a card.
        Evoke {G/U}{G/U} (You may cast this spell for its evoke cost. If you do, it's sacrificed when it enters.)
    """, "6/5")

    private fun Scenario.targets(vararg names: String) {
        if (pending is Decision.Targets) answer(Answer.Targets(names.map { target(it) }))
    }

    private fun Scenario.evoke(name: String) = CastWay(alternative = view()[card(name)]!!.characteristics!!.abilities.first {
        (it.ability as? Ability.Static)?.mechanics?.any { m -> m is Mechanic.AlternativeCost } == true
    }.origin)

    /** Advances to turn 3's first main phase and pays for the transform. */
    private fun Scenario.transformAtFirstMain() {
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        say(true)
        paySuggested()
        resolveAll()
    }

    // CR 702.51a: convoke given to creature spells: two creatures pay {1}{G}
    @Test
    fun solvanneGivesConvoke() = scenario {
        alice.battlefield(solvanne, ogre, elf)
        alice.hand(bear)
        alice.casts(bear, paying = listOf(ogre, elf))
        resolveAll()
        expect {
            alice.battlefield has bear
            ogre.tapped shouldBe true
        }
    }

    // CR 701.27a: paying {B} at the first main phase transforms it
    @Test
    fun solvanneTransforms() = scenario {
        alice.library(bear, bear)
        bob.library(bear, bear)
        alice.battlefield("Swamp", solvanne)
        transformAtFirstMain()
        assertEquals(1, state.obj(card(solvanne))?.face)
    }

    // CR 606.4: +1: -2/-0 on a creature; loyalty 4
    @Test
    fun fennShrinksACreature() = scenario {
        alice.battlefield(fenn, counters = listOf(CounterKind.Loyalty to 3))
        bob.battlefield(ogre)
        alice.activates(fenn, ability = 1, target = ogre)
        resolveAll()
        expect {
            ogre.power shouldBe 1
            card(fenn).view.characteristics?.loyalty shouldBe 4
        }
    }

    // the back face's −3 makes two Elks
    @Test
    fun fennTransformedMakesElks() = scenario {
        alice.library(bear, bear)
        bob.library(bear, bear)
        alice.battlefield("Forest")
        alice.battlefield(fenn, counters = listOf(CounterKind.Loyalty to 3))
        transformAtFirstMain()
        alice.activates(fenn, ability = 1)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Elk Token" } shouldBe 2 }
    }

    // CR 613.1f: an ability given until end of turn: combat damage to a player draws a card
    @Test
    fun tellisGivesACardDrawingAbility() = scenario {
        alice.library(elf, elf)
        bob.library(bear)
        alice.battlefield("Island", "Island", bear)
        alice.hand(tellis)
        alice.casts(tellis)
        resolveAll()
        targets(bear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect { alice.hand has elf }
        expect { tellis hasMechanic { it is Mechanic.CantBeBlocked } }
    }

    @Test
    fun nettleReturnsAGoblin() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(nettle)
        alice.graveyard(goblin)
        alice.casts(nettle)
        resolveAll()
        targets(goblin)
        resolveAll()
        expect { alice.hand has goblin }
    }

    // CR 508.4: the copy enters tapped and attacking, sacrificed at the end step
    @Test
    fun nettleTransformedCopiesTheBlightedCreature() = scenario {
        alice.library(bear, bear)
        bob.library(bear, bear)
        alice.battlefield("Mountain", nettle, ogre)
        transformAtFirstMain()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(nettle)
        resolveAll()
        say(true)
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Plaza Ogre" } shouldBe 2
            card("Plaza Ogre", 1).tapped shouldBe true
            ogre.counters(m1m1) shouldBe 1
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Plaza Ogre" } shouldBe 1 }
    }

    @Test
    fun cinnaRummages() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(cinna, ogre)
        alice.library(bear)
        alice.casts(cinna)
        resolveAll()
        say(true)
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.hand has bear
        }
    }

    // CR 106.6: two mana of one colour as it transforms
    @Test
    fun cinnaTransformedAddsMana() = scenario {
        alice.library(bear, bear)
        bob.library(bear, bear)
        alice.battlefield("Island", cinna)
        transformAtFirstMain()
        if (pending is Decision.ChooseColor) alice.answers(Answer.ColorChoice(arcanum.api.Color.Red))
        resolveAll()
        expect { alice.pool.size shouldBe 2 }
    }

    // CR 701.17a: three milled; an Elf card among them gains 2 life
    @Test
    fun orwenMillsAndGains() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(orwen)
        alice.library(bear, elf, ogre, redBear)
        alice.casts(orwen)
        resolveAll()
        expect {
            alice.graveyard has elf
            alice.library has redBear
        }
        assertEquals(22, alice.life)
    }

    // CR 700.2: two modes, each with its target player
    @Test
    fun cinnasDecreeDrawsAndBurns() = scenario {
        alice.battlefield("Island", "Island", "Island", "Mountain", "Mountain")
        alice.hand(cinnasDecree)
        alice.library(elf, elf)
        bob.battlefield(bear, redBear, Ecl.giant)
        alice.casts(cinnasDecree, modes = listOf(1, 2), targets = listOf("alice", "bob"))
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            bob.graveyard has bear
            bob.graveyard has redBear
            bob.battlefield has Ecl.giant
        }
    }

    // CR 701.14a: the fight after a token for the target player
    @Test
    fun maewensDecreeMakesAKithkinAndFights() = scenario {
        alice.battlefield("Forest", "Plains", "Plains", ogre)
        alice.hand(maewensDecree)
        bob.battlefield(redBear)
        alice.casts(maewensDecree, modes = listOf(1, 3), targets = listOf("alice", ogre, redBear))
        resolveAll()
        expect {
            alice.battlefield has "Kithkin Token"
            bob.graveyard has redBear
        }
    }

    // CR 601.2h, 702.74a: {W}{W} spent: the Kithkin; no {R}{R}: no pump
    @Test
    fun unburdeningWithWhiteMana() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(unburdening)
        alice.casts(unburdening)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Kithkin Token" } shouldBe 2
            unburdening.power shouldBe 3
        }
    }

    // evoked with {R}{R}: creatures get +1/+1 and haste; it's sacrificed
    @Test
    fun unburdeningEvokedWithRedMana() = scenario {
        alice.battlefield("Mountain", "Mountain", bear)
        alice.hand(unburdening)
        alice.casts(unburdening, way = evoke(unburdening))
        resolveAll()
        expect {
            alice.graveyard has unburdening
            bear.power shouldBe 3
            bear hasKeyword Keyword.Haste
        }
    }

    // CR 701.9b: evoked with {B}{B}: a nonland card chosen from the revealed hand
    @Test
    fun chicaneryEvokedMakesADiscard() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(chicanery)
        bob.hand(ogre, "Forest")
        alice.casts(chicanery, way = evoke(chicanery))
        resolveAll()
        targets("bob")
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            bob.hand has "Forest"
            alice.graveyard has chicanery
        }
    }

    // both colours spent: a creature returns and three -1/-1 counters go on a creature
    @Test
    fun hollownessWithBothColors() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(hollowness)
        alice.graveyard(ogre)
        bob.battlefield(Ecl.giant)
        alice.casts(hollowness)
        resolveAll()
        var guard = 4
        while (pending is Decision.Targets && guard-- > 0) {
            val d = pending as Decision.Targets
            answer(Answer.Targets(listOf(if (target(ogre) in d.options) target(ogre) else target(Ecl.giant))))
            resolveAll()
        }
        resolveAll()
        expect {
            alice.battlefield has ogre
            bob.graveyard has Ecl.giant
        }
    }

    // CR 701.17a: milled Goblin cards go to the hand; an artifact destroyed
    @Test
    fun nettlesDecreeMillsGoblinsAndDestroys() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain", "Mountain")
        alice.hand(nettlesDecree)
        alice.library(goblin, bear, goblin, "Swamp", ogre, elf)
        bob.battlefield(totem)
        alice.casts(nettlesDecree, modes = listOf(2, 3), targets = listOf(totem, "alice"))
        resolveAll()
        expect {
            bob.graveyard has totem
            alice.hand.names.count { it == goblin } shouldBe 2
            alice.graveyard has ogre
            alice.library has elf
        }
    }

    @Test
    fun tellissDecreeDrawsAndStuns() = scenario {
        alice.battlefield("Plains", "Island", "Island")
        alice.hand(tellissDecree)
        alice.library(elf)
        bob.battlefield(ogre)
        alice.casts(tellissDecree, modes = listOf(2, 3), targets = listOf("alice", ogre))
        resolveAll()
        expect {
            alice.hand has elf
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.Stun) shouldBe 1
        }
    }

    // CR 701.26b: the creatures get +3/+3 and untap
    @Test
    fun orwensDecreeReturnsAndPumps() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Forest", "Forest")
        alice.battlefield(bear, tapped = true)
        alice.hand(orwensDecree)
        alice.graveyard(ogre, totem)
        alice.casts(orwensDecree, modes = listOf(1, 3), targets = listOf(ogre, totem, "alice"))
        resolveAll()
        expect {
            alice.hand has ogre
            alice.hand has totem
            bear.power shouldBe 5
            bear.tapped shouldBe false
        }
    }

    // evoked with {U}{U}: draw two, discard one
    @Test
    fun yearningEvokedLoots() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(yearning, ogre)
        alice.library(bear, elf)
        alice.casts(yearning, way = evoke(yearning))
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.graveyard has yearning
            alice.hand has bear
            alice.hand has elf
        }
    }

    // {G}{G} spent: an artifact of the opponent exiled
    @Test
    fun yearningWithGreenExiles() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(yearning)
        bob.battlefield(totem)
        alice.casts(yearning)
        resolveAll()
        targets(totem)
        resolveAll()
        expect { bob.exile has totem }
    }
}
