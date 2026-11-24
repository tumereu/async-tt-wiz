package arcanum.engine.sets.snc

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.cards.face
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertFails

/**
 * SNC bug-class sweep: siblings of the engine fixes SNC made one card at a time (a copy that loses what was decided for the
 * original, an entering permanent's printed counters on one entry path only, counters put at once as one event on one path only,
 * shield counters on one kind of damage only, a limited permission used up by one way of playing only, "your step" read without
 * whose turn it is). Invented cards with oracle wordings.
 */
class SncSweepScenariosTest {
    private val loyalty = CounterKind.Loyalty
    private val shield = CounterKind.Shield

    private val promise = Cards.invent("SncSweep Promised Respite", "{1}{W}", "Instant", """
        Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
        You gain 3 life.
    """)
    private val mirror = Cards.invent("SncSweep Ledger Mirror", "{U}{U}", "Instant", "Copy target instant or sorcery spell.")
    private val fuse = "Fuse (You may cast one or both halves of this card from your hand.)"
    private val takeGive = Cards.inventFaces("Split",
        face("SncSweep Take", "{W}", "Instant", "You gain 3 life.\n$fuse", null, null, null),
        face("SncSweep Give", "{U}", "Instant", "Draw a card.\n$fuse", null, null, null),
    )
    private val echoEngine = Cards.invent("SncSweep Echo Engine", "{3}", "Artifact",
        "{2}: Copy target activated or triggered ability you control. You may choose new targets for the copy.")
    private val disciple = Cards.invent("SncSweep Bloodline Disciple", "{1}{B}", "Creature — Human Cleric",
        "{1}, Sacrifice a creature: You gain life equal to the sacrificed creature's toughness.", "1/1")
    private val leech = Cards.invent("SncSweep Ledger Leech", "{2}{B}", "Artifact", "{X}: Target player loses X life.")
    private val fixer = Cards.invent("SncSweep Vexa, Uptown Fixer", "{2}{B}{R}", "Legendary Planeswalker — Vexa", "[+1]: You gain 2 life.", loyalty = "3")
    private val forgery = Cards.invent("SncSweep Perfect Forgery", "{2}{U}", "Sorcery", "Create a token that's a copy of target planeswalker you control.")
    private val matron = Cards.invent("SncSweep Family Matron", "{2}{G}", "Creature — Elf Citizen",
        "Each other creature you control enters with an additional +1/+1 counter on it.", "2/2")
    private val bookie = Cards.invent("SncSweep Counting Bookie", "{1}{U}", "Creature — Human Rogue",
        "Whenever you put one or more +1/+1 counters on one or more other creatures you control, draw a card.", "1/1")
    private val muster = Cards.invent("SncSweep Muster the Crew", "{2}{W}", "Sorcery", "Create two 1/1 white Soldier creature tokens.")
    private val ogre = Cards.invent("SncSweep Dockside Ogre", "{3}{G}", "Creature — Ogre", "", "4/4")
    private val reckless = Cards.invent("SncSweep Reckless Blast", "{R}", "Instant",
        "Damage can't be prevented this turn. SncSweep Reckless Blast deals 2 damage to target creature.")
    private val zap = Cards.invent("SncSweep Alley Zap", "{R}", "Instant", "SncSweep Alley Zap deals 2 damage to target creature or planeswalker.")
    private val heist = Cards.invent("SncSweep Two-Card Heist", "{1}{R}", "Sorcery",
        "Exile the top two cards of your library. Until end of turn, you may play one of those cards.")
    private val spark = Cards.invent("SncSweep Street Spark", "{R}", "Sorcery", "SncSweep Street Spark deals 1 damage to each opponent.")
    private val censor = Cards.invent("SncSweep Wiretap Censor", "{3}{U}", "Enchantment",
        "If an opponent would draw a card except the first one they draw in each of their draw steps, instead that player skips that draw and you draw a card.")
    private val tip = Cards.invent("SncSweep Anonymous Tip", "{U}", "Instant", "Target player draws a card.")

    // CR 707.10, 702.174a: the gift is an additional cost: a copy of the spell has it promised too, to the same opponent
    @Test
    fun copyOfAPromisedGiftSpellGivesTheSameOpponentTheGift() = scenario {
        players.forEach { it.library("Island", "Island", "Island") }
        alice.battlefield("Plains", "Plains", "Island", "Island")
        alice.hand(promise, mirror)
        alice.casts(promise, kicked = 1)
        alice.casts(mirror, target = promise)
        resolveAll()
        expect {
            bob.hand.size shouldBe 2
            alice.life shouldBe 26
        }
    }

    /** Copies the ability on top of the Stack with the Echo Engine (paid with two Islands, no new targets) and resolves everything. */
    private fun Scenario.copyTopAbility() {
        alice.activates(echoEngine)
        (pending as? Decision.Targets)?.let { d -> answer(Answer.Targets(listOf(d.options.first()))) }
        (pending as? Decision.ManaPayment)?.let { d -> answer(Answer.ManaPayment(d.suggestion)) }
        resolve()
        (pending as? Decision.May)?.let { answer(Answer.YesNo(false)) }
        resolveAll()
    }

    // CR 707.10: a copy of an activated ability uses the objects used to pay the original's costs (the creature sacrificed)
    @Test
    fun copyOfAnActivatedAbilityUsesTheOriginalsSacrificedCreature() = scenario {
        alice.battlefield(echoEngine, disciple, ogre, "Swamp", "Island", "Island")
        alice.activates(disciple, choosing = listOf(ogre), paying = listOf("Swamp"))
        copyTopAbility()
        expect { alice.life shouldBe 28 }
    }

    // CR 707.10: a copy of an activated ability copies the value of X
    @Test
    fun copyOfAnActivatedAbilityKeepsX() = scenario {
        alice.battlefield(echoEngine, leech, "Swamp", "Swamp", "Swamp", "Island", "Island")
        alice.activates(leech, target = "bob", x = 3, paying = listOf("Swamp", "Swamp#1", "Swamp#2"))
        copyTopAbility()
        expect { bob.life shouldBe 14 }
    }

    // CR 707.10, 702.102a, d: casting both halves is a decision made for the spell: a copy of a fused split spell does both halves too
    @Test
    fun copyOfAFusedSplitSpellIsFused() = scenario {
        alice.library("Island", "Island", "Island")
        alice.battlefield("Plains", "Island", "Island", "Island")
        alice.hand(takeGive, mirror)
        alice.casts(takeGive, way = CastWay(fused = true))
        alice.casts(mirror, target = takeGive)
        resolveAll()
        expect {
            alice.life shouldBe 26
            alice.hand.size shouldBe 2
        }
    }

    // CR 707.2, 306.5b, 111.10: a token copy of a planeswalker enters with loyalty counters equal to the copied printed loyalty
    @Test
    fun tokenCopyOfAPlaneswalkerEntersWithItsPrintedLoyalty() = scenario {
        alice.battlefield(fixer, counters = listOf(loyalty to 5))
        alice.hand(forgery)
        alice.castsWithoutCosts(forgery, target = fixer)
        resolveAll()
        expect { card(fixer, 1).counters(loyalty) shouldBe 3 }
    }

    // CR 122.6, 122.6a, 603.2c: counters given to two tokens as they enter at once are put by their controller in one event: "one or
    // more counters on one or more creatures" triggers once
    @Test
    fun countersOnCreaturesEnteringAtOnceAreOneEvent() = scenario {
        alice.library("Island", "Island", "Island")
        alice.battlefield(matron, bookie)
        alice.hand(muster)
        alice.castsWithoutCosts(muster)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            alice.battlefield.names.count { it == "Soldier Token" } shouldBe 2
        }
    }

    // CR 615.12, 122.1c: damage that can't be prevented is dealt; the shield counter's prevention still applies, so its additional
    // effect (a shield counter removed) happens
    @Test
    fun unpreventableDamageIsDealtAndStillRemovesAShieldCounter() = scenario {
        bob.battlefield(ogre, counters = listOf(shield to 1))
        alice.hand(reckless)
        alice.castsWithoutCosts(reckless, target = ogre)
        resolveAll()
        expect {
            ogre.view.obj?.damage shouldBe 2
            card(ogre).counters(shield) shouldBe 0
        }
    }

    // CR 122.1c, 120.3c: a shield counter on a planeswalker prevents the damage, so no loyalty is lost
    @Test
    fun shieldCounterOnAPlaneswalkerPreventsDamage() = scenario {
        bob.battlefield(fixer, counters = listOf(loyalty to 3, shield to 1))
        alice.hand(zap)
        alice.castsWithoutCosts(zap, target = fixer)
        resolveAll()
        expect {
            card(fixer).counters(loyalty) shouldBe 3
            card(fixer).counters(shield) shouldBe 0
        }
    }

    // CR 611.2a, 305.1: "you may play one of those cards": casting one of them uses the permission up, so the other (a land) can't be played
    @Test
    fun spellCastWithAOneCardPermissionLeavesNoLandPlay() = scenario {
        alice.library(spark, "Mountain", "Island")
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(heist)
        alice.casts(heist)
        resolveAll()
        alice.casts(spark)
        resolveAll()
        assertFails { alice.plays("Mountain") }
        expect { bob.life shouldBe 19 }
    }

    // CR 504.1, 614.1a: "the first one they draw in each of their draw steps" is in that player's own draw step: a card Bob draws in
    // Carol's draw step is replaced
    @Test
    fun drawInAnotherOpponentsDrawStepIsNotThatPlayersFirstDraw() = scenario(players = 3) {
        players.forEach { it.library("Island", "Island", "Island") }
        alice.battlefield(censor, "Island")
        alice.hand(tip)
        turn(active = carol, step = TurnStep.Draw, priority = alice)
        alice.casts(tip, target = "bob")
        resolveAll()
        expect {
            bob.hand.size shouldBe 0
            alice.hand.size shouldBe 1
        }
    }
}
