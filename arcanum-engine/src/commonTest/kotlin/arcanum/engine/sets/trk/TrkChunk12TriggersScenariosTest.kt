package arcanum.engine.sets.trk

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.modes
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.spm.loreChapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Star Trek chunk 12 in play: an until-end-of-turn counter replacement made by a tap trigger, combat on each player's turn with a may-draw that
 * stops that player's attacks, a delayed control change at the next combat, "choose one or more instead" with an empty hand, and a trigger on an
 * opponent's cast or activation of the chosen name. */
class TrkChunk12TriggersScenariosTest {
    private val teo = Cards.invent("Teo Garrow, Eager Cadet", "{1}{W}", "Legendary Creature — Human Officer", """
        Lifelink
        Whenever Teo Garrow becomes tapped, until end of turn, if one or more counters would be put on a permanent you control, that many plus one of each of those kinds of counters are put on that permanent instead.
    """.trimIndent(), "2/2")
    private val truce = Cards.invent("Truce of the Long Night", "{3}{W}", "Enchantment", """
        At the beginning of combat on each player's turn, that player may draw a card. If they do, they can't attack this turn and you put a peace counter on this enchantment.
        At the beginning of your upkeep, if this enchantment has twelve or more peace counters on it, you win the game.
    """.trimIndent())
    private val vengeance = Cards.invent("The Vengeance of Moraq", "{2}{R}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — This Saga deals 4 damage to target creature an opponent controls. At the beginning of the next combat this turn, gain control of that creature until end of turn. Untap it. It gains haste until end of turn.
        II — You may exile a card from your hand. If you do, draw a card.
        III — Each opponent sacrifices a nontoken permanent of their choice.
    """.trimIndent())
    private val dace = Cards.invent("Captain Arlen V. Dace", "{2}{R}", "Legendary Creature — Human Officer", """
        Whenever Captain Dace enters or attacks, choose one. If you have no cards in hand, choose one or more instead.
        • Discard a card, then draw a card.
        • Create a 1/1 red Officer creature token.
        • Creatures you control get +1/+0 until end of turn.
    """.trimIndent(), "2/2")
    private val sorra = Cards.invent("Vedek Sorra, Faithless Elder", "{1}{B}", "Legendary Creature — Bajoran Cleric", """
        As Vedek Sorra enters, choose a nonland card name.
        Whenever an opponent casts a spell with the chosen name or activates an ability of a source with the chosen name, that player loses 3 life.
    """.trimIndent(), "3/1")

    private val cub = Cards.invent("Trk12 Moss Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Trk12 Plains Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val bigOx = Cards.invent("Trk12 Great Ox", "{4}{G}", "Creature — Ox", "", "5/5")
    private val stun = Cards.invent("Trk12 Stun Ray", "{U}", "Instant", "Tap target creature.")
    private val growth = Cards.invent("Trk12 Growth Ray", "{G}", "Instant", "Put a +1/+1 counter on target creature.")
    private val wither = Cards.invent("Trk12 Wither Ray", "{B}", "Instant", "Put a -1/-1 counter on target creature.")
    private val relay = Cards.invent("Trk12 Signal Relay", "{1}", "Artifact", """
        Flash
        {1}: You gain 1 life.
    """.trimIndent())
    private val m1m1 = CounterKind.PowerToughness(-1, -1)
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    // CR 614.1a, 611.2a: once Teo is tapped, counters put on permanents its controller controls this turn are one more of each kind, whoever
    // puts them; another player's permanent gets the plain number (three players)
    @Test
    fun teoAddsOneCounterOnceTapped() = scenario(players = 3) {
        alice.battlefield(teo, cub, ox, "Island", "Forest", "Forest")
        alice.hand(stun, growth, growth)
        bob.battlefield(bigOx, "Swamp", "Swamp")
        bob.hand(wither, wither)
        alice.casts(growth, target = cub)
        resolveAll()
        expect { cub.counters(p1p1) shouldBe 1 }
        alice.casts(stun, target = teo)
        resolveAll()
        alice.casts(growth, target = cub)
        resolveAll()
        alice.passes()
        bob.casts(wither, target = ox)
        resolveAll()
        alice.passes()
        bob.casts(wither, target = bigOx)
        resolveAll()
        expect {
            teo.tapped shouldBe true
            cub.counters(p1p1) shouldBe 3
            ox.counters(m1m1) shouldBe 2
            bigOx.counters(m1m1) shouldBe 1
        }
    }

    // CR 507.1, 608.2d: on another player's turn that player chooses to draw; then no creature they control can attack this turn (CR 508.1),
    // and the enchantment's controller gets the counter (three players)
    @Test
    fun truceStopsTheAttacksOfAPlayerWhoDraws() = scenario(players = 3) {
        alice.battlefield(truce)
        bob.battlefield(ox)
        bob.library("Swamp", "Swamp")
        turn(number = 2, active = bob)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        assertEquals(bob.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.hand has "Swamp"
            truce.counters(CounterKind.Named("peace")) shouldBe 1
        }
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { bob.attacks(ox, defender = "alice") }
    }

    // CR 608.2d: a player who declines draws nothing, may attack, and adds no counter
    @Test
    fun truceLetsAPlayerWhoDeclinesAttack() = scenario(players = 3) {
        alice.battlefield(truce)
        bob.battlefield(ox)
        bob.library("Swamp", "Swamp")
        turn(number = 2, active = bob)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        answer(Answer.YesNo(false))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(ox, defender = "alice")
        expect {
            bob.hand lacks "Swamp"
            truce.counters(CounterKind.Named("peace")) shouldBe 0
            ox.tapped shouldBe true
        }
    }

    // CR 603.7, 603.7c: chapter I damages the creature now and takes it at the next combat this turn, untapped and hasty, so it can attack
    @Test
    fun vengeanceTakesTheDamagedCreatureAtCombat() = scenario {
        bob.battlefield(bigOx, tapped = true)
        loreChapter(vengeance, 1, "Island", "Island")
        resolveAll()
        expect {
            bigOx.view.obj?.damage shouldBe 4
            bigOx.controller shouldBe bob.id
        }
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect {
            bigOx.controller shouldBe alice.id
            bigOx.tapped shouldBe false
            bigOx hasKeyword Keyword.Haste.kind
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bigOx)
        expect { bigOx.tapped shouldBe true }
    }

    // CR 700.2d, 603.3c: with no cards in hand as the modes are chosen, Dace's enters trigger takes more than one mode
    @Test
    fun daceChoosesSeveralModesWithAnEmptyHand() = scenario {
        alice.battlefield(cub)
        alice.hand(dace)
        alice.castsWithoutCosts(dace)
        resolveAll()
        assertEquals(3, decision<Decision.Modes>().max)
        modes(1, 2)
        resolveAll()
        expect {
            alice.battlefield has "Officer Token"
            cub.power shouldBe 3
            dace.power shouldBe 3
        }
    }

    // CR 700.2: with a card in hand, only one mode
    @Test
    fun daceChoosesOneModeWithACardInHand() = scenario {
        alice.hand(dace, "Island")
        alice.castsWithoutCosts(dace)
        resolveAll()
        assertEquals(1, decision<Decision.Modes>().max)
    }

    // CR 602.1, 603.2: an opponent activating an ability of a permanent with the chosen name loses 3 life (three players)
    @Test
    fun sorraPunishesAnActivationOfTheChosenName() = scenario(players = 3) {
        alice.hand(sorra)
        bob.battlefield(relay, "Swamp")
        alice.castsWithoutCosts(sorra)
        resolveAll()
        pending as Decision.ChooseCardName
        answer(Answer.CardName(relay))
        resolveAll()
        alice.passes()
        bob.activates(relay)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 20
        }
    }

    // CR 601.2, 603.2: an opponent casting a spell with the chosen name loses 3 life; another name doesn't count (three players)
    @Test
    fun sorraPunishesACastOfTheChosenName() = scenario(players = 3) {
        alice.hand(sorra)
        bob.hand(stun)
        bob.battlefield("Island")
        carol.hand(relay)
        carol.battlefield("Swamp")
        alice.castsWithoutCosts(sorra)
        resolveAll()
        answer(Answer.CardName(relay))
        resolveAll()
        alice.passes()
        bob.casts(stun, target = sorra)
        resolveAll()
        alice.passes()
        bob.passes()
        carol.casts(relay)
        resolveAll()
        expect {
            bob.life shouldBe 20
            carol.life shouldBe 17
            carol.battlefield has relay
        }
    }
}
