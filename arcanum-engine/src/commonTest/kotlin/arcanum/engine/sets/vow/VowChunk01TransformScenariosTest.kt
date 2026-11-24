package arcanum.engine.sets.vow

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.fin.backFaceUp
import arcanum.engine.sets.ltr.permitted
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** VOW chunk 01: transforming cards: "exactly two" attackers, a card played from among those exiled with it, face-down cards a player may
 * look at and play once a turn, "if no one does", a toughness check after a counter, tapped and attacking copies, a revealed hand's
 * discard and draw, counters removed before transforming back, and a sacrificed creature's toughness. */
class VowChunk01TransformScenariosTest {
    private val swain = Cards.inventFaces("Transform",
        face("Courting Swain", "{2}{R}", "Creature — Vampire", "When you attack with exactly two creatures, transform this creature.", "2/3", null, null),
        face("Ruinous Partner", null, "Creature — Vampire", """
            Trample
            When this creature transforms into Ruinous Partner, add {R}{R}. Until end of turn, you don't lose this mana as steps and phases end.
            {R}{R}: This creature and another target creature each get +1/+0 until end of turn.
        """.trimIndent(), "3/3", null, "R"))
    private val seer = Cards.inventFaces("Transform",
        face("Sparkwild Seer", "{1}{R}", "Creature — Human Wizard", """
            {T}: This creature deals 2 damage to you. Exile the top card of your library. You may play that card this turn. Activate only as a sorcery.
            When you play a card exiled with this creature, transform this creature.
        """.trimIndent(), "3/1", null, null),
        face("Spark-Charged Raider", null, "Creature — Human Berserker", "This creature can't block.", "4/3", null, "R"))
    private val odric = Cards.inventFaces("Transform",
        face("Odric Vane, Examiner", "{1}{U}", "Legendary Creature — Human Advisor",
            "{T}: Draw a card, then exile a card from your hand face down. You may look at that card for as long as it remains exiled. You may pay {4}{U}{U}. If you do, transform Odric Vane.",
            "0/2", null, null),
        face("Vane's Insight", null, "Legendary Enchantment", """
            At the beginning of your upkeep, exile the top card of your library face down. You may look at that card for as long as it remains exiled.
            Once during each of your turns, you may play a land or cast a spell from among the cards exiled with this permanent without paying its mana cost.
        """.trimIndent(), null, null, "U"))
    private val wayfarer = Cards.inventFaces("Transform",
        face("Guileless Wayfarer", "{2}{B}{B}", "Creature — Human",
            "At the beginning of your upkeep, any opponent may sacrifice a creature of their choice. If no one does, transform this creature.", "1/3", null, null),
        face("Hungering Invader", null, "Creature — Vampire", """
            Flying
            This creature gets +2/+0 as long as an opponent controls a Human.
        """.trimIndent(), "3/3", null, "B"))
    private val copse = Cards.inventFaces("Transform",
        face("Sleeping Copse", "{3}{G}", "Enchantment",
            "At the beginning of combat on your turn, put a +1/+1 counter on target creature you control. Then if that creature has toughness 6 or greater, transform this enchantment.",
            null, null, null),
        face("Gnarled Copsewalker", null, "Creature — Treefolk", "Vigilance\nOther creatures you control have vigilance.", "3/6", null, "G"))
    private val volo = Cards.inventFaces("Transform",
        face("Volo Tidehold", "{1}{U}{B}", "Legendary Creature — Vampire Cleric", """
            Flying
            When Volo enters, put up to one target creature card from your graveyard on top of your library.
            At the beginning of your upkeep, look at the top card of your library. You may reveal that card. If a creature card with mana value 6 or greater is revealed this way, transform Volo.
        """.trimIndent(), "1/4", null, null),
        face("Groth, Lord of the Fathoms", null, "Legendary Creature — Kraken Horror", """
            Flying
            Whenever Groth attacks, create a tapped and attacking token that's a copy of another target attacking creature. If that creature is a Kraken, Leviathan, Octopus, or Serpent, create two of those tokens instead.
        """.trimIndent(), "3/5", null, "UB"))
    private val drapes = Cards.inventFaces("Transform",
        face("Veiling Drapes", "{B}", "Creature — Wall", "Defender\n{2}{B}: Transform this creature. Activate only as a sorcery.", "0/4", null, null),
        face("Unveiled Watcher", null, "Creature — Eye Horror", """
            Menace
            When this creature transforms into Unveiled Watcher, target opponent reveals their hand. You may choose a nonland card from it. If you do, that player discards that card, then draws a card.
        """.trimIndent(), "3/4", null, "B"))
    private val ansel = Cards.inventFaces("Transform",
        face("Ansel, Doting Groom", "{2}{W}{B}", "Legendary Creature — Vampire Noble", """
            Other Vampires you control get +1/+1.
            When Ansel dies, return it to the battlefield transformed under its owner's control.
        """.trimIndent(), "4/4", null, null),
        face("Ansel's Casket", null, "Legendary Artifact",
            "At the beginning of your upkeep, create a 1/1 white and black Vampire creature token with lifelink and put a bloodline counter on Ansel's Casket. Then if there are three or more bloodline counters on it, remove those counters and transform it.",
            null, null, "WB"))
    private val fodder = Cards.inventFaces("Transform",
        face("Siege Fodder", "{2}{B}", "Creature — Zombie",
            "At the beginning of combat on your turn, if you control three or more creatures that each have toughness greater than their power, transform this creature.",
            "1/5", null, null),
        face("Siege Overseer", null, "Creature — Zombie",
            "{2}{B}, {T}, Sacrifice another creature: Target opponent loses life equal to the sacrificed creature's toughness.", "2/6", null, "B"))
    private val bear = Cards.invent("Vowfield Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Vowfield Ox", "{3}{G}", "Creature — Ox", "", "2/4")
    private val treant = Cards.invent("Vowfield Treant", "{2}{G}", "Creature — Treefolk", "", "1/5")
    private val wall = Cards.invent("Vowfield Wall", "{1}{G}", "Creature — Wall", "", "1/4")
    private val kraken = Cards.invent("Vowfield Kraken", "{2}{U}", "Creature — Kraken", "", "2/2")
    private val zap = Cards.invent("Vowfield Zap", "{R}", "Instant", "Vowfield Zap deals 1 damage to any target.")
    private val bolt = Cards.invent("Vowfield Bolt", "{R}", "Instant", "Vowfield Bolt deals 4 damage to target creature.")

    private fun Scenario.face(name: String): Int? = state.obj(card(name))?.face

    // CR 508.1, 603.2c: two attackers make one batch of exactly two; the back face's mana stays until end of turn (CR 106.4)
    @Test
    fun attackingWithExactlyTwoCreaturesTransformsAndKeepsTheMana() = scenario {
        alice.battlefield(swain, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(swain, bear)
        resolveAll()
        assertEquals(1, face(swain))
        advanceTo(TurnStep.EndOfCombat)
        assertEquals(2, alice.pool.size)
    }

    // CR 508.1: three attackers are not exactly two
    @Test
    fun attackingWithThreeCreaturesDoesntTransform() = scenario {
        alice.battlefield(swain, bear, ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(swain, bear, ox)
        resolveAll()
        assertEquals(0, face(swain))
    }

    // CR 607.2a, 305.1: playing the land exiled with it as your land play is playing a card exiled with it (CR 400.7)
    @Test
    fun playingTheExiledLandTransforms() = scenario {
        alice.battlefield(seer)
        alice.library("Forest", "Island")
        alice.activates(seer)
        resolveAll()
        expect {
            "Forest".zone shouldBe Zone.Exile
            alice.life shouldBe 18
        }
        alice.plays("Forest")
        resolveAll()
        assertEquals(1, face(seer))
    }

    // CR 601.2a: casting the exiled spell is playing it; the trigger resolves before the spell
    @Test
    fun castingTheExiledSpellTransforms() = scenario {
        alice.battlefield(seer, "Mountain")
        alice.library(zap, "Island")
        alice.activates(seer)
        resolveAll()
        alice.casts(zap, target = "bob", way = permitted(zap))
        resolveAll()
        assertEquals(1, face(seer))
        expect { bob.life shouldBe 19 }
    }

    // CR 607.2a: a card played from the hand isn't one exiled with it
    @Test
    fun playingAnotherCardDoesntTransform() = scenario {
        alice.battlefield(seer)
        alice.hand("Island")
        alice.library("Forest", "Plains")
        alice.activates(seer)
        resolveAll()
        alice.plays("Island")
        resolveAll()
        assertEquals(0, face(seer))
    }

    // CR 406.3: the card is exiled face down; paying {4}{U}{U} transforms it; CR 607.2a: the card the front face exiled isn't among the cards
    // exiled with the back face's first ability
    @Test
    fun theFrontExilesFaceDownAndTransformsWhenPaid() = scenario {
        alice.battlefield(odric, "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(bear)
        alice.library(ox, ox)
        alice.activates(odric)
        playOut(bear, may = true)
        assertEquals(1, face(odric))
        expect { bear.zone shouldBe Zone.Exile }
        assertTrue(state.obj(card(bear))!!.faceDown)
        assertTrue(Engine.options(state, alice.id).filterIsInstance<Input.Cast>().none { it.card == card(bear) })
    }

    // CR 406.3, 607.2a: the upkeep exiles face down; once during your turn, one of those cards is cast without paying its mana cost
    @Test
    fun theInsightCastsAnExiledCardForFree() = scenario {
        alice.battlefield(odric)
        backFaceUp(odric)
        alice.library(bear, ox, ox, ox)
        bob.library("Island", "Island")
        advanceTo(TurnStep.Upkeep, turn = 3)
        playOut()
        expect { bear.zone shouldBe Zone.Exile }
        assertTrue(state.obj(card(bear))!!.faceDown)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.casts(bear, way = permitted(bear))
        playOut()
        expect { bear.zone shouldBe Zone.Battlefield }
    }

    // CR 101.4: each opponent in turn order declines, so it transforms
    @Test
    fun noOpponentSacrificingTransformsIt() = scenario(players = 3) {
        alice.battlefield(wayfarer)
        bob.battlefield(bear)
        carol.battlefield(ox)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        carol.library("Island", "Island")
        advanceTo(TurnStep.Upkeep, turn = 4)
        playOut(may = false)
        assertEquals(1, face(wayfarer))
        expect {
            bear.zone shouldBe Zone.Battlefield
            ox.zone shouldBe Zone.Battlefield
        }
    }

    // CR 101.4: one opponent sacrificing keeps it from transforming
    @Test
    fun oneOpponentSacrificingKeepsItFromTransforming() = scenario(players = 3) {
        alice.battlefield(wayfarer)
        bob.battlefield(bear)
        carol.battlefield(ox)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        carol.library("Island", "Island")
        advanceTo(TurnStep.Upkeep, turn = 4)
        var guard = 10
        while (guard-- > 0) {
            val d = pending as? Decision.May ?: if (state.stack.isNotEmpty()) { resolve(); continue } else break
            answer(Answer.YesNo(d.player == carol.id))
        }
        playOut(ox)
        assertEquals(0, face(wayfarer))
        expect {
            bear.zone shouldBe Zone.Battlefield
            ox.zone shouldBe Zone.Graveyard
        }
    }

    // CR 608.2h: the toughness is checked after the counter: 1/5 becomes 2/6 and it transforms
    @Test
    fun aCounterReachingToughnessSixTransformsIt() = scenario {
        alice.battlefield(copse, treant)
        advanceTo(TurnStep.BeginningOfCombat)
        playOut(treant)
        assertEquals(1, face(copse))
        expect { treant.toughness shouldBe 6 }
    }

    // CR 608.2h: 1/4 becomes 2/5, not enough
    @Test
    fun aCounterShortOfToughnessSixDoesnt() = scenario {
        alice.battlefield(copse, wall)
        advanceTo(TurnStep.BeginningOfCombat)
        playOut(wall)
        assertEquals(0, face(copse))
    }

    // CR 707.2, 508.4: two tapped and attacking copies of an attacking Kraken
    @Test
    fun aKrakenGetsTwoAttackingCopies() = scenario {
        alice.battlefield(volo, kraken)
        backFaceUp(volo)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(volo, kraken)
        playOut(kraken)
        expect {
            alice.battlefield.size shouldBe 4
        }
        assertEquals(4, state.objects.values.count { it.zone == Zone.Battlefield && it.tapped })
    }

    // CR 707.2: one copy of another creature
    @Test
    fun anotherCreatureGetsOneCopy() = scenario {
        alice.battlefield(volo, bear)
        backFaceUp(volo)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(volo, bear)
        playOut(bear)
        expect { alice.battlefield.size shouldBe 3 }
    }

    // CR 701.27f: transforming into the back face triggers; the chosen nonland card is discarded and its owner draws (CR 608.2c)
    @Test
    fun theWatcherMakesTheOpponentDiscardAndDraw() = scenario {
        alice.battlefield(drapes, "Swamp", "Swamp", "Swamp")
        bob.hand(bear, "Forest")
        bob.library("Island")
        alice.activates(drapes)
        playOut(bear)
        assertEquals(1, face(drapes))
        expect {
            bob.graveyard has bear
            bob.hand has "Forest"
            bob.hand has "Island"
        }
    }

    // CR 608.2d: with no nonland card to choose, nothing is discarded and no card is drawn
    @Test
    fun aHandOfLandsLosesNothingAndDrawsNothing() = scenario {
        alice.battlefield(drapes, "Swamp", "Swamp", "Swamp")
        bob.hand("Forest")
        bob.library("Island")
        alice.activates(drapes)
        playOut(may = true)
        assertEquals(1, face(drapes))
        expect {
            bob.hand has "Forest"
            bob.hand.size shouldBe 1
        }
    }

    // CR 122.1: the third bloodline counter: a Vampire token, the counters removed, and it transforms back
    @Test
    fun theCasketTransformsBackAtThreeCounters() = scenario {
        alice.battlefield(ansel, counters = listOf(CounterKind.Named("bloodline") to 2))
        backFaceUp(ansel)
        alice.library("Plains", "Plains")
        bob.library("Island", "Island")
        advanceTo(TurnStep.Upkeep, turn = 3)
        playOut()
        assertEquals(0, face(ansel))
        expect {
            ansel.counters(CounterKind.Named("bloodline")) shouldBe 0
            alice.battlefield has "Vampire Token"
        }
    }

    // CR 712.14: it returns with its back face up
    @Test
    fun anselReturnsAsTheCasket() = scenario {
        alice.battlefield(ansel, "Mountain")
        alice.hand(bolt)
        alice.casts(bolt, target = ansel)
        playOut()
        expect { ansel.zone shouldBe Zone.Battlefield }
        assertEquals(1, face(ansel))
    }

    // CR 603.4: three creatures with toughness greater than power, itself included
    @Test
    fun threeSturdyCreaturesTransformTheFodder() = scenario {
        alice.battlefield(fodder, ox, treant)
        advanceTo(TurnStep.BeginningOfCombat)
        playOut()
        assertEquals(1, face(fodder))
    }

    // CR 603.4: a 2/2 doesn't count
    @Test
    fun twoSturdyCreaturesDont() = scenario {
        alice.battlefield(fodder, ox, bear)
        advanceTo(TurnStep.BeginningOfCombat)
        playOut()
        assertEquals(0, face(fodder))
    }

    // CR 608.2h: the sacrificed creature's toughness as it last existed; any target opponent
    @Test
    fun theOverseerDrainsTheSacrificedToughness() = scenario(players = 3) {
        alice.battlefield(fodder, ox, bear, "Swamp", "Swamp", "Swamp")
        backFaceUp(fodder)
        alice.activates(fodder, target = "carol", choosing = listOf(ox))
        playOut()
        expect {
            carol.life shouldBe 16
            bob.life shouldBe 20
            ox.zone shouldBe Zone.Graveyard
        }
    }
}
