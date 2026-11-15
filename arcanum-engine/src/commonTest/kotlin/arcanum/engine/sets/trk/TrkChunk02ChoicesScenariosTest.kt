package arcanum.engine.sets.trk

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.CardRef
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.modes
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** Star Trek chunk 02 in play: an opponent's choice to exile their own spell, trigger doubling while a creature stays
 * tapped, copies of opponents' creatures with changeling, a control change whose former controller draws, and a hand card
 * exiled from the opponent's hand. The other modal spells repeat shapes tested before. */
class TrkChunk02ChoicesScenariosTest {
    private val gambit = Cards.invent("Audacious Gambit", "{R}{R}", "Instant",
        "Choose target noncreature spell an opponent controls. That player may exile it. If they don't, you may cast a noncreature spell from your hand without paying its mana cost.")
    private val study = Cards.invent("Trk2 Quiet Study", "{1}{U}", "Sorcery", "Draw two cards.")
    private val spark = Cards.invent("Trk2 Free Spark", "{3}{R}", "Instant", "This spell deals 3 damage to any target.")
    private val velvaya = Cards.invent("Velvaya Moor, Doting Envoy", "{1}{R}", "Legendary Creature — Human Advisor", """
        You may choose not to untap Velvaya Moor during your untap step.
        {2}, {T}: Choose another target creature you control. For as long as Velvaya Moor remains tapped, if a triggered ability of that creature triggers, that ability triggers an additional time.
    """, "2/2")
    private val eager = Cards.invent("Trk2 Eager Squire", "{1}{W}", "Creature — Human Soldier", "Whenever this creature attacks, you gain 1 life.", "2/2")
    private val plain = Cards.invent("Trk2 Plain Squire", "{1}{W}", "Creature — Human Soldier", "Whenever this creature attacks, you gain 1 life.", "2/2")
    private val wake = Cards.invent("Trk2 Rousing Call", "{W}", "Instant", "Untap target permanent.")
    private val incursion = Cards.invent("Shapeshift Incursion", "{X}{R}{R}{R}", "Sorcery",
        "Choose X target creatures your opponents control. For each of them, create a token that's a copy of that creature, except it has changeling. Those tokens gain haste. Exile those tokens at the beginning of the next end step. (They're every creature type.)")
    private val bear = Cards.invent("Trk2 Bob Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val wolf = Cards.invent("Trk2 Carol Wolf", "{2}{G}", "Creature — Wolf", "", "3/3")
    private val edicts = Cards.invent("Edicts of Commerce", "{3}{U}{U}", "Sorcery", """
        Choose one —
        • Follow the 3rd Rule — Gain control of target nonland permanent an opponent controls. That opponent draws three cards.
        • Follow the 98th Rule — Target opponent gains control of target nonland permanent you control. If they do, you draw three cards.
    """)
    private val relic = Cards.invent("Trk2 Carol Relic", "{2}", "Artifact", "")
    private val leech = Cards.invent("Brine Leech", "{1}{B}", "Creature — Vampire Shapeshifter", """
        Lifelink
        When this creature enters, look at target opponent's hand. You may choose a nonland card from it. If you do, that player exiles that card, then draws a card.
    """, "2/2")

    private fun gambitOnBobsStudy(block: arcanum.engine.scenario.Scenario.() -> Unit) = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(gambit, spark)
        bob.battlefield("Island", "Island")
        bob.hand(study)
        bob.library("Island", "Island", "Island")
        turn(number = 2, active = bob)
        bob.casts(study)
        bob.passes()
        carol.passes()
        alice.casts(gambit, target = study)
        allPass()
        assertEquals(bob.id, decision<Decision.May>().player)
        block()
    }

    // CR 608.2c: "that player" is the spell's controller; they decline, so you may cast a noncreature spell free (CR 118.9)
    @Test
    fun gambitDeclinedCastsFree() = gambitOnBobsStudy {
        answer(Answer.YesNo(false))
        alice.chooses(spark)
        answer(Answer.Targets(listOf(target("carol"))))
        resolveAll()
        expect {
            carol.life shouldBe 17
            alice.graveyard has spark
            bob.graveyard has study
            bob.hand.size shouldBe 2
        }
    }

    // CR 608.2c: the opponent exiles their own spell, so nothing is cast
    @Test
    fun gambitExiledNoCast() = gambitOnBobsStudy {
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.exile has study
            alice.hand has spark
            bob.hand.size shouldBe 0
        }
    }

    // CR 603.2d: the chosen creature's attack trigger triggers twice while Velvaya Moor stays tapped; another creature's once
    @Test
    fun velvayaDoublesTheChosenCreaturesTriggers() = scenario {
        alice.battlefield(velvaya, eager, plain, "Mountain", "Mountain")
        alice.activates(velvaya, target = eager)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(eager, plain)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    // CR 611.2b: once Velvaya Moor untaps the effect ends for good
    @Test
    fun velvayaEffectEndsWhenItUntaps() = scenario {
        alice.battlefield(velvaya, eager, "Mountain", "Mountain", "Plains")
        alice.hand(wake)
        alice.activates(velvaya, target = eager, paying = listOf("Mountain", "Mountain"))
        resolveAll()
        alice.casts(wake, target = velvaya)
        resolveAll()
        expect { velvaya.tapped shouldBe false }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(eager)
        resolveAll()
        expect { alice.life shouldBe 21 }
    }

    // CR 707.9b: copies of each opponent's creature, with changeling (CR 702.73a) and haste; exiled at the next end step (CR 603.7)
    @Test
    fun incursionCopiesEachOpponentsCreature() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(incursion)
        bob.battlefield(bear)
        carol.battlefield(wolf)
        alice.casts(incursion, x = 2, targets = listOf(bear, wolf))
        resolveAll()
        val bearCopy = CardRef(card(bear, owner = alice), "bear copy")
        val wolfCopy = CardRef(card(wolf, owner = alice), "wolf copy")
        expect {
            bearCopy.controller shouldBe alice.id
            wolfCopy.power shouldBe 3
            bearCopy hasKeyword Keyword.Changeling.kind
            wolfCopy hasKeyword Keyword.Haste.kind
            card(bear).controller shouldBe bob.id
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield lacks bear
            alice.battlefield lacks wolf
            bob.battlefield has bear
        }
    }

    // CR 608.2c: "that opponent" is the one who controlled the permanent, not you after the control change (3 players: carol draws)
    @Test
    fun edictsFormerControllerDraws() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(edicts)
        carol.battlefield(relic)
        carol.library("Island", "Island", "Island", "Island")
        bob.library("Island", "Island", "Island", "Island")
        alice.casts(edicts, modes = listOf(0), target = relic)
        resolveAll()
        expect {
            relic.controller shouldBe alice.id
            carol.hand.size shouldBe 3
            bob.hand.size shouldBe 0
            alice.hand.size shouldBe 0
        }
    }

    // CR 608.2d: you choose a nonland card; that player exiles it and draws a card
    @Test
    fun leechExilesTheChosenCard() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(leech)
        bob.hand(spark, "Island")
        bob.library("Plains")
        alice.casts(leech)
        resolveAll()
        alice.chooses(spark)
        resolveAll()
        expect {
            bob.exile has spark
            bob.hand has "Island"
            bob.hand has "Plains"
        }
    }
}
