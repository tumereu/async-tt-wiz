package arcanum.engine.sets.mkm

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.ScenarioPlayer
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.hits
import arcanum.engine.sets.ltr.permitted
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.tdm.Plain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** MKM chunk 09: collect evidence X (CR 701.59a), "counter up to one target spell unless its controller pays mana equal to …" (CR 118.12a),
 * colour pairs (CR 105.2), a count of artifacts put into graveyards this turn, exiling and playing cards (CR 406.3, 609.4b), all colours and
 * every basic land type (CR 105.2c, 305.7), casting from the top of the library once each turn, a choice for each player (CR 608.2e), X copies
 * of X targets (CR 707.2) and permanents that can't be turned face up (CR 708.8). */
class MkmChunk09EffectsScenariosTest {
    private val wyrm = Cards.invent("Cinderjudge Wyrm", "{4}{R}{R}", "Creature — Dragon", """
        Flying, trample
        Whenever this creature deals combat damage to a player, you may collect evidence X. When you do, this creature deals X damage to each creature and planeswalker that player controls.
    """.trimIndent(), "6/6")
    private val graft = Cards.invent("Grotesque Graft", "{X}{G}{U}", "Instant",
        "Put X +1/+1 counters on target creature you control. Then counter up to one target spell unless its controller pays mana equal to the greatest power among creatures you control.")
    private val zarvex = Cards.invent("Zarvex, Concord of Pairs", "{W}{U}{B}{R}{G}", "Legendary Creature — Dragon Avatar", """
        Flying, hexproof from multicolored
        Whenever Zarvex deals combat damage to a player, it deals X damage to any target, target player draws X cards, and you gain X life, where X is the number of different color pairs among permanents you control that are exactly two colors.
    """.trimIndent(), "6/6")
    private val sigil = Cards.invent("Twin Hue Sigil", "{W}{U}", "Enchantment", "")
    private val adept = Cards.invent("Twin Hue Adept", "{U}{W}", "Creature — Human Wizard", "", "1/1")
    private val pairIdol = Cards.invent("Ember Pair Idol", "{B}{R}", "Artifact", "")
    private val banner = Cards.invent("Triad Banner", "{W}{U}{B}", "Enchantment", "")
    private val tantrum = Cards.invent("Quakehammer Tantrum", "{3}{R}{R}", "Sorcery",
        "Destroy all artifacts you don't control, then exile the top X cards of your library, where X is the number of artifacts that were put into " +
            "graveyards from the battlefield this turn. You may put a creature card exiled this way onto the battlefield. It gains haste. Return it to " +
            "your hand at the beginning of the next end step.")
    private val bequest = Cards.invent("Hastened Bequest", "{R}{R}", "Enchantment",
        "Whenever a creature is dealt damage, its controller may exile that many cards from the top of their library. They may play those cards until the end of their next turn.")
    private val pilfering = Cards.invent("Brazen Pilfering", "{X}{B}{B}", "Instant",
        "Target opponent exiles the top X cards of their library face down. You may look at and play those cards for as long as they remain exiled. " +
            "If you cast a spell this way, you may spend mana as though it were mana of any type to cast it.")
    private val leyline = Cards.invent("Leyline of the Concord", "{G/W}{G/U}{B/G}{R/G}", "Enchantment", """
        If this card is in your opening hand, you may begin the game with it on the battlefield.
        Each nonland permanent you control is all colors.
        Lands you control are every basic land type in addition to their other types.
    """.trimIndent())
    private val troupe = Cards.invent("Gather the Troupe", "{1}{W}", "Enchantment", """
        You may look at the top card of your library any time.
        Once each turn, you may cast a creature spell with power 2 or less from the top of your library.
    """.trimIndent())
    private val landgrinder = Cards.invent("Grumbit's Landgrinder", "{2}{R}{R}", "Artifact Creature — Insect Thopter", """
        Flying, trample
        When this creature enters, for each player, destroy up to one nonbasic land that player controls. For each land destroyed this way, its controller may search their library for a basic land card, put it onto the battlefield tapped, then shuffle.
    """.trimIndent(), "4/4")
    private val crossroads = Cards.invent("Gilded Crossroads", null, "Land", "{T}: Add {C}.")
    private val multitude = Cards.invent("Mirrorfold Multitude", "{X}{X}{X}{G}{U}", "Sorcery",
        "For each of X target permanents, create X tokens that are copies of that permanent.")
    private val sentry = Cards.invent("Manor Hound Sentry", "{3}{W}", "Creature — Dog", """
        Vigilance
        Permanents your opponents control can't be turned face up during your turn.
        Whenever you attack with three or more creatures, creatures you control get +1/+1 until end of turn.
    """.trimIndent(), "3/2")
    private val lurker = Cards.invent("Hooded Lurker Spy", "{1}{G}", "Creature — Human Rogue", "Disguise {G}", "2/2")

    private fun Scenario.pay() {
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
    }

    // CR 701.59a: X is chosen as evidence is collected (here 3, exiling the 4-mana-value card); CR 603.12: then 3 damage to each creature and
    // planeswalker of the player dealt damage, not of the third player
    @Test
    fun cinderjudgeWyrmCollectsEvidenceXAndBurnsThatPlayersBoard() = scenario(players = 3) {
        alice.battlefield(wyrm)
        alice.graveyard(Plain.dragon, Plain.elf)
        bob.battlefield(Plain.turtle, Plain.ogre)
        bob.battlefield(Plain.walker, counters = listOf(CounterKind.Loyalty to 3))
        carol.battlefield(Plain.bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wyrm, defender = "bob")
        advanceTo(TurnStep.DeclareBlockers)
        if (state.priority.holder == null) bob.blocks()
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        // at most the graveyard's total mana value (4 + 1)
        assertEquals(5, decision<Decision.ChooseNumber>().max)
        answer(Answer.Number(3))
        if (pending is Decision.May) answer(Answer.YesNo(true))
        alice.chooses(Plain.dragon)
        resolveAll()
        expect {
            bob.life shouldBe 14
            alice.exile has Plain.dragon
            alice.graveyard has Plain.elf
            Plain.turtle.view.obj?.damage shouldBe 3
            bob.graveyard has Plain.ogre
            bob.graveyard has Plain.walker
            carol.battlefield has Plain.bear
        }
    }

    // CR 701.59a: X can be at most what the graveyard can pay (1 here); declining collects nothing and deals nothing
    @Test
    fun cinderjudgeWyrmMayDecline() = scenario {
        alice.battlefield(wyrm)
        alice.graveyard(Plain.elf)
        bob.battlefield(Plain.turtle)
        hits(wyrm)
        assertEquals(1, decision<Decision.ChooseNumber>().max)
        answer(Answer.Number(1))
        playOut(may = false)
        assertEquals(null, pending)
        assertTrue(state.stack.isEmpty())
        expect {
            bob.life shouldBe 14
            alice.graveyard has Plain.elf
            Plain.turtle.view.obj?.damage shouldBe 0
        }
    }

    // CR 118.12a: mana equal to the greatest power after the counters (4); with three lands left the spell is countered
    @Test
    fun grotesqueGraftCountersWhenThePowerCantBePaid() = scenario {
        turn(number = 2, active = bob)
        alice.battlefield(Plain.ogre, "Forest", "Island", "Island")
        alice.hand(graft)
        bob.battlefield("Island", "Island", "Island", "Island")
        bob.hand(Plain.study)
        bob.library("Plains")
        bob.casts(Plain.study)
        bob.passes()
        alice.casts(graft, targets = listOf(Plain.ogre, Plain.study), x = 1)
        playOut(may = true)
        expect {
            Plain.ogre.counters(Plain.p1p1) shouldBe 1
            bob.graveyard has Plain.study
            bob.hand lacks "Plains"
        }
    }

    // CR 118.12a: with four lands left Bob pays the 4 and his spell resolves
    @Test
    fun grotesqueGraftLetsTheSpellResolveWhenPaid() = scenario {
        turn(number = 2, active = bob)
        alice.battlefield(Plain.ogre, "Forest", "Island", "Island")
        alice.hand(graft)
        bob.battlefield("Island", "Island", "Island", "Island", "Island")
        bob.hand(Plain.study)
        bob.library("Plains")
        bob.casts(Plain.study)
        bob.passes()
        alice.casts(graft, targets = listOf(Plain.ogre, Plain.study), x = 1)
        playOut(may = true)
        expect {
            bob.hand has "Plains"
            bob.graveyard has Plain.study
        }
    }

    // CR 115.1: "up to one target spell": with no spell to counter it still puts the counters
    @Test
    fun grotesqueGraftNeedsNoSpell() = scenario {
        alice.battlefield(Plain.ogre, "Forest", "Island", "Island", "Island")
        alice.hand(graft)
        alice.casts(graft, targets = listOf(Plain.ogre), x = 2)
        playOut()
        expect { Plain.ogre.counters(Plain.p1p1) shouldBe 2 }
    }

    // CR 105.2: white-blue (twice) and black-red are two colour pairs; the three-colour and five-colour permanents have none: X is 2
    @Test
    fun zarvexCountsDifferentColorPairs() = scenario {
        alice.battlefield(zarvex, sigil, adept, pairIdol, banner)
        alice.library("Plains", "Island", "Swamp")
        bob.battlefield(Plain.turtle)
        hits(zarvex, picks = listOf(Plain.turtle, "alice"))
        expect {
            Plain.turtle.view.obj?.damage shouldBe 2
            alice.hand has "Plains"
            alice.hand has "Island"
            alice.hand lacks "Swamp"
            alice.life shouldBe 22
            bob.life shouldBe 14
        }
    }

    // CR 608.2c: X counts the two artifacts it destroyed; the creature card exiled comes in with haste and returns at the end step (CR 603.7)
    @Test
    fun quakehammerTantrumExilesOneCardPerDestroyedArtifact() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", pairIdol)
        alice.hand(tantrum)
        alice.library(Plain.bear, "Island", "Forest")
        bob.battlefield(Plain.idol, Plain.golem)
        alice.casts(tantrum)
        resolveAll()
        alice.chooses(Plain.bear)
        resolveAll()
        expect {
            bob.graveyard has Plain.idol
            bob.graveyard has Plain.golem
            alice.battlefield has pairIdol
            alice.exile has "Island"
            alice.library has "Forest"
            alice.battlefield has Plain.bear
            Plain.bear hasKeyword Keyword.Haste.kind
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.hand has Plain.bear }
    }

    // CR 120.3, 608.2h: the destroyed creature's controller (as it last existed) exiles as many cards as the damage and may play them on their
    // next turn (CR 611.2c)
    @Test
    fun hastenedBequestLetsTheDamagedCreaturesControllerPlayTheCards() = scenario {
        alice.battlefield(bequest, "Mountain")
        alice.hand(Plain.shock)
        bob.battlefield(Plain.bear, "Forest")
        bob.library(Plain.elf, "Island", "Swamp", "Plains")
        alice.casts(Plain.shock, target = Plain.bear)
        resolveAll()
        assertEquals(bob.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.graveyard has Plain.bear
            bob.exile has Plain.elf
            bob.exile has "Island"
            bob.library has "Swamp"
        }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.casts(Plain.elf, way = permitted(Plain.elf, bob))
        pay()
        resolveAll()
        expect { bob.battlefield has Plain.elf }
    }

    // CR 406.3: the face-down exiled card of the opponent is Alice's to cast; CR 609.4b: her Swamps pay its red mana
    @Test
    fun brazenPilferingCastsTheOpponentsExiledCardWithAnyMana() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(pilfering)
        bob.library(Plain.ogre, "Island", "Plains")
        alice.casts(pilfering, target = "bob", x = 2)
        resolveAll()
        expect {
            bob.exile has Plain.ogre
            bob.exile has "Island"
            bob.library has "Plains"
        }
        assertTrue(state.objOrThrow(card(Plain.ogre)).faceDown)
        alice.casts(Plain.ogre, way = permitted(Plain.ogre))
        pay()
        resolveAll()
        expect {
            alice.battlefield has Plain.ogre
            Plain.ogre.controller shouldBe alice.id
        }
    }

    // CR 105.2c: Alice's nonland permanents are all five colours, not her lands or Bob's; CR 305.7: her Forests are Islands too and make blue
    @Test
    fun leylineOfTheConcordColorsPermanentsAndGivesLandsEveryType() = scenario {
        alice.battlefield(leyline, Plain.bear, "Forest", "Forest")
        alice.hand(Plain.turtle)
        bob.battlefield(Plain.ogre)
        expect {
            Plain.bear.colors.toSet() shouldBe Color.all.toSet()
            "Forest".colors shouldBe emptyList()
            Plain.ogre.colors shouldBe listOf(Color.Red)
            assertTrue(card("Forest").subtypes.containsAll(listOf("Plains", "Island", "Swamp", "Mountain", "Forest").map { Subtype(it) }))
        }
        alice.casts(Plain.turtle)
        pay()
        resolveAll()
        expect { alice.battlefield has Plain.turtle }
    }

    // CR 601.3: once each turn a creature with power 2 or less from the top; the next one that turn isn't allowed
    @Test
    fun gatherTheTroupeCastsOnceEachTurn() = scenario {
        alice.battlefield(troupe, "Plains", "Plains", "Plains", "Plains")
        alice.library(Plain.flyer, Plain.wall, "Island")
        alice.casts(Plain.flyer, way = permitted(Plain.flyer))
        pay()
        resolveAll()
        expect { alice.battlefield has Plain.flyer }
        assertTrue(castable(Plain.wall).isEmpty())
    }

    // CR 601.3: a power 3 creature isn't allowed
    @Test
    fun gatherTheTroupeNeedsPowerTwoOrLess() = scenario {
        alice.battlefield(troupe, "Plains", "Plains", "Plains", "Plains")
        alice.library(Plain.whiteDragon, "Island")
        assertTrue(castable(Plain.whiteDragon).isEmpty())
    }

    private fun Scenario.castable(name: String, player: ScenarioPlayer = alice) =
        Engine.options(state, player.id).filterIsInstance<Input.Cast>().filter { it.card == card(name) }

    // CR 608.2e: Alice chooses for each player (none of her own), the lands are destroyed; CR 608.2h: each land's controller may search
    @Test
    fun grumbitsLandgrinderDestroysANonbasicLandOfEachChosenPlayer() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", crossroads)
        alice.hand(landgrinder)
        bob.battlefield(crossroads)
        bob.library("Plains", "Island")
        carol.battlefield(crossroads)
        carol.library("Swamp")
        alice.casts(landgrinder)
        resolveAll()
        var guard = 12
        while (guard-- > 0) {
            when (val d = pending) {
                // a land of each player (none of Alice's), or Bob's search
                is Decision.ChooseObjects -> when {
                    d.options.any { state.obj(it)?.zone != Zone.Battlefield } -> bob.chooses("Plains")
                    state.objOrThrow(d.options.first()).controller == alice.id -> answer(Answer.Objects(emptyList()))
                    else -> answer(Answer.Objects(listOf(d.options.first())))
                }
                is Decision.May -> answer(Answer.YesNo(d.player == bob.id))
                null -> if (state.stack.isEmpty()) break else allPass()
                else -> error("unexpected $d")
            }
        }
        expect {
            alice.battlefield has crossroads
            bob.graveyard has crossroads
            carol.graveyard has crossroads
            bob.battlefield has "Plains"
            "Plains".tapped shouldBe true
            carol.library has "Swamp"
        }
    }

    // CR 601.2c, 707.2: two targets, two copies of each
    @Test
    fun mirrorfoldMultitudeCopiesEachTargetXTimes() = scenario {
        alice.battlefield(Plain.bear, "Forest", "Forest", "Forest", "Forest", "Island", "Island", "Island", "Island")
        alice.hand(multitude)
        bob.battlefield(Plain.ogre)
        alice.casts(multitude, targets = listOf(Plain.bear, Plain.ogre), x = 2)
        pay()
        resolveAll()
        expect {
            alice.battlefield.names.count { it == Plain.bear } shouldBe 3
            alice.battlefield.names.count { it == Plain.ogre } shouldBe 2
            bob.battlefield.names.count { it == Plain.ogre } shouldBe 1
        }
    }

    private fun Scenario.canTurnUp(name: String, player: ScenarioPlayer) =
        Engine.options(state, player.id).filterIsInstance<Input.Activate>().any { it.source == card(name) }

    // CR 708.8: during Alice's turn Bob can't turn his face-down creature up
    @Test
    fun manorHoundSentryStopsTurningFaceUpDuringYourTurn() = scenario {
        turn(number = 1, active = alice, priority = bob)
        alice.battlefield(sentry)
        bob.battlefield(lurker, faceDown = true)
        bob.battlefield("Forest", "Forest", "Forest")
        assertTrue(!canTurnUp(lurker, bob))
    }

    // CR 708.8: during his own turn he can
    @Test
    fun manorHoundSentryAllowsItDuringTheirTurn() = scenario {
        turn(number = 2, active = bob)
        alice.battlefield(sentry)
        bob.battlefield(lurker, faceDown = true)
        bob.battlefield("Forest", "Forest", "Forest")
        assertTrue(canTurnUp(lurker, bob))
    }
}
