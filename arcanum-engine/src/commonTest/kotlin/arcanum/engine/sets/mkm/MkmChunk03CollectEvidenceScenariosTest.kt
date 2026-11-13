package arcanum.engine.sets.mkm

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targetsIfAsked
import arcanum.engine.sets.dsk.grantedWay
import arcanum.engine.sets.tdm.Plain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** MKM chunk 03: collect evidence (CR 701.59) as an optional additional cost and what is linked to it (CR 701.59c, 607.2), as a cost of
 * activated, mana and ward abilities, as an effect, and as an alternative cost. Graveyard fillers' mana values: giant 5, dragon 4, ogre 3,
 * idol 2, elf 1, study 1. */
class MkmChunk03CollectEvidenceScenariosTest {
    private val clamp = Cards.invent("Clamp on Guilt", "{3}{G}", "Sorcery", """
        As an additional cost to cast this spell, you may collect evidence 6. This spell costs {2} less to cast if evidence was collected. (To collect evidence 6, exile cards with total mana value 6 or greater from your graveyard.)
        Target creature you control gets +2/+0 until end of turn. It deals damage equal to its power to target creature you don't control.
    """)
    private val untangler = Cards.invent("Plot Untangler", "{5}{U}{U}", "Creature — Sphinx Detective", """
        Flying
        You may collect evidence 10 rather than pay the mana cost for spells you cast. (To collect evidence 10, exile cards with total mana value 10 or greater from your graveyard.)
    """, "6/6")
    private val pixie = Cards.invent("Patrol Pixie", "{2}{U}", "Creature — Faerie Detective", """
        As an additional cost to cast this spell, you may collect evidence 6. (Exile cards with total mana value 6 or greater from your graveyard.)
        Flying
        When this creature enters, tap target creature. If evidence was collected, put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
    """, "2/2")
    private val hushUp = Cards.invent("Grim Hush-Up", "{3}{B}{B}", "Sorcery", """
        As an additional cost to cast this spell, you may collect evidence 6.
        Destroy all creatures. If evidence was collected, exile a card from an opponent's graveyard. Then search its owner's graveyard, hand, and library for any number of cards with that name and exile them. That player shuffles, then draws a card for each card exiled from their hand this way.
    """)
    private val inspector = Cards.invent("Proof Inspector", "{G}{U}", "Creature — Merfolk Detective", """
        At the beginning of combat on your turn, you may collect evidence 4. (Exile cards with total mana value 4 or greater from your graveyard.)
        Whenever you collect evidence, investigate. (Create a Clue token. It's an artifact with "{2}, Sacrifice this token: Draw a card.")
    """, "2/2")
    private val wring = Cards.invent("Wring a Statement", "{1}{B}", "Sorcery", """
        As an additional cost to cast this spell, you may collect evidence 6. (Exile cards with total mana value 6 or greater from your graveyard.)
        Each opponent sacrifices a creature of their choice. If evidence was collected, instead each opponent sacrifices a creature with the greatest power among creatures they control.
    """)
    private val murmurer = Cards.invent("Thicket Murmurer", "{G}", "Creature — Elf Druid Detective", """
        You may choose not to untap this creature during your untap step.
        {3}{G}, {T}, Collect evidence 4: Target land you control becomes a 5/5 green Plant Boar creature with haste for as long as this creature remains tapped. It's still a land. Activate only as a sorcery. (To collect evidence 4, exile cards with total mana value 4 or greater from your graveyard.)
    """, "0/3")
    private val strider = Cards.invent("Sparkcoil Strider", "{1}{U}{R}", "Artifact — Vehicle", """
        Collect evidence 6: This Vehicle becomes an artifact creature until end of turn.
        Whenever this Vehicle attacks, you may cast an instant or sorcery spell from among cards exiled with it. If that spell would be put into a graveyard, put it on the bottom of its owner's library instead.
        Crew 2
    """, "4/4")
    private val phoenix = Cards.invent("Lanternwing Phoenix", "{1}{R}{R}", "Creature — Phoenix", """
        Flying
        When this creature dies, you may exile it and collect evidence 4. If you do, return this card to the battlefield tapped. (To collect evidence 4, exile cards with total mana value 4 or greater from your graveyard.)
    """, "3/3")
    private val champion = Cards.invent("Ninth Ward Champion", "{1}{W}", "Creature — Human", """
        {1}{W}, Collect evidence 2: This creature becomes a Human Detective with base power and toughness 4/4 and gains vigilance.
        {2}{W}, Collect evidence 4: If this creature is a Detective, it becomes a legendary creature named Oreva, the Steadfast, it has base power and toughness 5/5, and it gains "Other creatures you control have indestructible."
    """, "2/3")
    private val brute = Cards.invent("Hatchetback Brute", "{2}{G}{G}", "Creature — Beast", """
        Deathtouch, haste
        Ward—Collect evidence 4. (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player exiles cards with total mana value 4 or greater from their graveyard.)
    """, "4/4")
    private val cipher = Cards.invent("Lockbox Cipher", "{2}", "Artifact", """
        {T}, Collect evidence 3: Add one mana of any color. Put an unlock counter on this artifact. (To collect evidence 3, exile cards with total mana value 3 or greater from your graveyard.)
        Sacrifice this artifact: Surveil 3, then draw three cards. Activate only if this artifact has five or more unlock counters on it.
    """)
    private val analyst = Cards.invent("Clueglass Analyst", "{2}{U}", "Creature — Merfolk Detective", """
        {T}: Untap another target permanent you control.
        {T}, Collect evidence 3: Tap target creature you don't control. (To collect evidence 3, exile cards with total mana value 3 or greater from your graveyard.)
    """, "1/3")
    private val vessa = Cards.invent("Vessa, Heart of the Snare", "{4}{B}{G}", "Legendary Creature — Elf Detective", """
        Menace
        Whenever Vessa enters or attacks, you may collect evidence 4. If you do, create two 2/1 black and green Spider creature tokens with reach and menace.
        Sacrifice four tokens: Surveil 2, then draw two cards. You gain 2 life.
    """, "5/4")

    private val giant = Plain.giant
    private val dragon = Plain.dragon
    private val ogre = Plain.ogre
    private val idol = Plain.idol
    private val elf = Plain.elf
    private val bear = Plain.bear
    private val golem = Plain.golem
    private val study = Plain.study
    private val spark = Plain.shock
    private val doom = Plain.doom

    private fun Scenario.tokens(name: String): Int = state.objects.values.count { o -> o.zone == Zone.Battlefield && nameOf(o) == name }

    // CR 601.2f: the total cost counts the reduction once the optional additional cost is chosen (CR 701.59c)
    @Test
    fun clampCostsTwoLessWhenEvidenceIsCollected() = scenario {
        alice.battlefield("Forest", "Forest", bear)
        alice.graveyard(giant, elf)
        alice.hand(clamp)
        bob.battlefield(ogre)
        alice.casts(clamp, targets = listOf(bear, ogre), kicked = 1, choosing = listOf(giant, elf))
        resolveAll()
        expect {
            alice.exile has giant
            alice.exile has elf
            bob.graveyard has ogre
            bear.power shouldBe 4
        }
    }

    // CR 601.2f: without the evidence, the full {3}{G}
    @Test
    fun clampWithoutEvidenceCostsItsFullCost() = scenario {
        alice.battlefield("Forest", "Forest", bear)
        alice.graveyard(giant, elf)
        alice.hand(clamp)
        bob.battlefield(ogre)
        alice.casts(clamp, targets = listOf(bear, ogre), kicked = 0)
        assertTrue(decision<Decision.ManaPayment>().suggestion.isEmpty())
    }

    // CR 701.59c: the enters trigger refers to how the permanent was cast; CR 122.1d: a stun counter
    @Test
    fun pixieWithEvidenceStunsWhatItTaps() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.graveyard(giant, elf)
        alice.hand(pixie)
        bob.battlefield(ogre)
        alice.casts(pixie, kicked = 1, choosing = listOf(giant, elf))
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.Stun) shouldBe 1
        }
    }

    // CR 607.2, 701.59c: collecting evidence for the alternative cost isn't the linked additional cost: no stun counter
    @Test
    fun evidenceForAnAlternativeCostIsNotTheLinkedOne() = scenario {
        alice.battlefield(untangler)
        alice.graveyard(giant, dragon, elf)
        alice.hand(pixie)
        bob.battlefield(ogre)
        val way = grantedWay(pixie)
        assertNotNull(way)
        alice.casts(pixie, way = way, kicked = 0, choosing = listOf(giant, dragon, elf))
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            alice.battlefield has pixie
            alice.exile has giant
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.Stun) shouldBe 0
        }
    }

    // CR 701.59a: collecting evidence as an effect; CR 603.2: "whenever you collect evidence"
    @Test
    fun inspectorCollectsEvidenceAndInvestigates() = scenario {
        alice.battlefield(inspector)
        alice.graveyard(ogre, elf)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        answer(Answer.YesNo(true))
        alice.chooses(ogre, elf)
        resolveAll()
        expect {
            alice.exile has ogre
            alice.exile has elf
        }
        assertEquals(1, tokens("Clue Token"))
    }

    // CR 701.59c; CR 101.4: each opponent sacrifices one of their creatures with the greatest power (a tie: their choice)
    @Test
    fun wringWithEvidenceTakesEachOpponentsGreatest() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard(dragon, idol)
        alice.hand(wring)
        bob.battlefield(bear, giant)
        carol.battlefield(ogre, golem)
        alice.casts(wring, kicked = 1, choosing = listOf(dragon, idol))
        resolveAll()
        if (pending is Decision.ChooseObjects) carol.chooses(golem)
        resolveAll()
        expect {
            bob.graveyard has giant
            bob.battlefield has bear
            carol.graveyard has golem
            carol.battlefield has ogre
        }
    }

    private fun Scenario.animatePlains() {
        alice.battlefield(murmurer, "Forest", "Forest", "Forest", "Forest", "Plains")
        alice.graveyard(dragon)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        alice.activates(murmurer, target = "Plains", choosing = listOf(dragon))
        resolveAll()
    }

    /** Players pass until the untap step's choice of permanents not to untap is open in turn [turn]. */
    private fun Scenario.toUntapChoice(turn: Int) {
        var guard = 200
        while (pending == null && guard-- > 0) if (state.priority.holder == null) declareNone() else passPriority()
        assertTrue(pending is Decision.ChooseObjects && state.turn.number == turn && state.turn.step == TurnStep.Untap, "asked $pending")
    }

    // CR 502.3: its controller chooses not to untap it; CR 611.2b: the land stays a creature for as long as it remains tapped
    @Test
    fun murmurerKeptTappedKeepsTheLandACreature() = scenario {
        animatePlains()
        expect { "Plains".power shouldBe 5 }
        toUntapChoice(3)
        alice.chooses(murmurer)
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect {
            murmurer.tapped shouldBe true
            "Forest".tapped shouldBe false
            "Plains".power shouldBe 5
            "Plains".types shouldBe listOf(CardType.Land, CardType.Creature)
        }
    }

    // CR 502.3: untapped as usual, the effect ends (CR 611.2b)
    @Test
    fun murmurerUntappedEndsTheEffect() = scenario {
        animatePlains()
        toUntapChoice(3)
        answer(Answer.Objects(emptyList()))
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect {
            murmurer.tapped shouldBe false
            "Plains".types shouldBe listOf(CardType.Land)
        }
    }

    // CR 607.2a: the cards its collect evidence cost exiled are "exiled with it"; CR 608.2g: cast as the trigger resolves; CR 614.1a: to the
    // bottom of the library instead of the graveyard
    @Test
    fun striderCastsACardItsEvidenceExiled() = scenario {
        alice.battlefield(strider, "Island")
        alice.graveyard(giant, study)
        alice.library("Forest", "Forest")
        alice.activates(strider, choosing = listOf(giant, study))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(strider)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(study)
        (pending as? Decision.ManaPayment)?.let { answer(Answer.ManaPayment(it.suggestion)) }
        resolveAll()
        expect {
            alice.hand has "Forest"
            alice.graveyard lacks study
        }
        assertEquals(study, nameOf(state.obj(state.player(alice.id).library.last())!!))
    }

    // CR 118.12: the cost of exiling it and collecting evidence; CR 400.7: the exiled card returns, tapped
    @Test
    fun phoenixReturnsTappedWhenTheCostIsPaid() = scenario {
        alice.battlefield(phoenix, "Swamp")
        alice.graveyard(ogre, elf)
        alice.hand(doom)
        alice.casts(doom, target = phoenix)
        resolveAll()
        answer(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses(ogre, elf)
        resolveAll()
        expect {
            alice.battlefield has phoenix
            phoenix.tapped shouldBe true
            alice.exile has ogre
            alice.exile has elf
        }
    }

    // CR 118.3, 601.2h analog: the phoenix exiled by the cost's first part can't also count toward the evidence
    @Test
    fun phoenixCantCountItselfAsEvidence() = scenario {
        alice.battlefield(phoenix, "Swamp")
        alice.graveyard(ogre)
        alice.hand(doom)
        alice.casts(doom, target = phoenix)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.graveyard has phoenix
            alice.graveyard has ogre
        }
    }

    // CR 701.59c; CR 701.23a: the opponent's graveyard, hand and library searched for the exiled card's name; they draw for those from
    // their hand (any opponent's graveyard, with several)
    @Test
    fun hushUpExilesEveryCopyAndRefillsTheHand() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.graveyard(dragon, idol)
        alice.hand(hushUp)
        bob.battlefield(ogre)
        bob.graveyard(spark)
        bob.hand(spark, "Island")
        bob.library(spark, "Forest", "Forest")
        carol.graveyard(elf)
        alice.casts(hushUp, kicked = 1, choosing = listOf(dragon, idol))
        resolveAll()
        answer(Answer.Objects(listOf(card(spark, owner = bob, zone = Zone.Graveyard))))
        // the search offers the two other copies, in the hand and the library
        val offered = decision<Decision.ChooseObjects>().options
        assertEquals(2, offered.size)
        answer(Answer.Objects(offered))
        resolveAll()
        expect {
            alice.graveyard has bear
            bob.graveyard has ogre
            carol.graveyard has elf
        }
        assertEquals(3, state.exile.count { nameOf(state.obj(it)!!) == spark })
        assertEquals(2, state.player(bob.id).hand.size)
    }

    // CR 611.2a, 612.8: for good, a new name; CR 205.1a: legendary; the granted static gives the others indestructible
    @Test
    fun championBecomesTheLegend() = scenario {
        alice.battlefield(champion, bear, "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.graveyard(idol, dragon)
        alice.activates(champion, ability = 0, choosing = listOf(idol))
        resolveAll()
        expect {
            champion.power shouldBe 4
            champion hasKeyword Keyword.Vigilance.kind
        }
        alice.activates(champion, ability = 1, choosing = listOf(dragon))
        resolveAll()
        val view = champion.let { view()[card(it)]!!.characteristics!! }
        assertEquals("Oreva, the Steadfast", view.name)
        assertTrue(arcanum.api.Supertype.Legendary in view.supertypes)
        expect {
            champion.power shouldBe 5
            bear hasKeyword Keyword.Indestructible.kind
            champion lacksKeyword Keyword.Indestructible.kind
        }
    }

    // CR 702.21a: ward's cost is paid by the spell's controller, from their own graveyard (CR 701.59a)
    @Test
    fun wardIsPaidWithTheOpponentsEvidence() = scenario {
        alice.battlefield(brute)
        bob.battlefield("Mountain")
        bob.graveyard(dragon)
        bob.hand(spark)
        turn(active = bob)
        bob.casts(spark, target = brute)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) bob.chooses(dragon)
        resolveAll()
        expect {
            bob.exile has dragon
            brute.view.obj?.damage shouldBe 2
        }
    }

    // CR 702.21a: unpaid, the spell is countered
    @Test
    fun wardCountersWithoutEvidence() = scenario {
        alice.battlefield(brute)
        bob.battlefield("Mountain")
        bob.hand(spark)
        turn(active = bob)
        bob.casts(spark, target = brute)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.graveyard has spark
            brute.view.obj?.damage shouldBe 0
        }
    }

    // CR 605.1a: a mana ability whose cost collects evidence; the counter comes with the mana
    @Test
    fun cipherMakesManaAndCountsUnlocks() = scenario {
        alice.battlefield(cipher)
        alice.graveyard(ogre)
        alice.activates(cipher, choosing = listOf(ogre), color = arcanum.api.Color.Red)
        expect {
            alice.exile has ogre
            cipher.counters(CounterKind.Named("unlock")) shouldBe 1
        }
        assertEquals(1, alice.pool.size)
    }

    // CR 701.26b: untap another permanent you control
    @Test
    fun analystUntapsAnotherPermanent() = scenario {
        alice.battlefield(analyst, golem)
        alice.battlefield("Forest", tapped = true)
        alice.activates(analyst, ability = 0, target = "Forest")
        resolveAll()
        expect {
            "Forest".tapped shouldBe false
            analyst.tapped shouldBe true
        }
    }

    // CR 701.26a: tap a creature you don't control, collecting evidence for it
    @Test
    fun analystTapsWithEvidence() = scenario {
        alice.battlefield(analyst)
        alice.graveyard(ogre)
        bob.battlefield(bear)
        alice.activates(analyst, ability = 1, target = bear, choosing = listOf(ogre))
        resolveAll()
        expect {
            bear.tapped shouldBe true
            alice.exile has ogre
        }
    }

    // CR 118.12: "you may collect evidence 4. If you do" as it resolves; CR 603.2: the attack trigger
    @Test
    fun vessaMakesSpidersWhenEvidenceIsCollected() = scenario {
        alice.battlefield(vessa)
        alice.graveyard(dragon)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(vessa)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        if (pending is Decision.ChooseObjects) alice.chooses(dragon)
        resolveAll()
        expect { alice.exile has dragon }
        assertEquals(2, tokens("Spider Token"))
    }
}
