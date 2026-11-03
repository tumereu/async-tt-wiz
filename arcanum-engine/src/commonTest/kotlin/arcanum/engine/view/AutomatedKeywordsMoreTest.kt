package arcanum.engine.view

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.ObjectMark
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** More keywords and keyword actions that used to be done by hand (D61): plot, disguise, cloak, manifest, backup, entwine,
 * awaken, tribute, support, doubling mana, discover, endure, learn, time travel. */
class AutomatedKeywordsMoreTest {
    private val bear = Cards.invent("Auto2 Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Auto Plain Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val plotter = Cards.invent("Auto2 Plotting Outlaw", "{3}{R}", "Creature — Human Rogue", "Plot {1}{R} (You may pay {1}{R} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)", "4/3")
    private val plotSpell = Cards.invent("Auto2 Planned Heist", "{2}{U}", "Instant", "Draw two cards.\nPlot {U} (You may pay {U} and exile this card from your hand. Cast it as a sorcery on a later turn without paying its mana cost. Plot only as a sorcery.)")
    private val disguised = Cards.invent("Auto2 Masked Witness", "{3}{W}", "Creature — Human Detective", "Disguise {1}{W} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)", "3/4")
    private val backer = Cards.invent("Auto2 Backup Trooper", "{2}{R}", "Creature — Human Warrior", "Backup 1 (When this creature enters, put a +1/+1 counter on target creature. If that's another creature, it gains the following ability until end of turn.)\nTrample", "2/2")
    private val entwined = Cards.invent("Auto2 Twin Strike", "{1}{R}", "Instant", "Choose one —\n• Auto2 Twin Strike deals 2 damage to target creature.\n• Auto2 Twin Strike deals 2 damage to target player.\nEntwine {2} (Choose both if you pay the entwine cost.)")
    private val awakener = Cards.invent("Auto2 Rousing Tide", "{1}{U}", "Sorcery", "Draw a card.\nAwaken 2—{3}{U} (If you cast this spell for {3}{U}, also put two +1/+1 counters on target land you control and it becomes a 0/0 Elemental creature with haste. It's still a land.)")
    private val tributer = Cards.invent("Auto2 Tribute Taker", "{2}{R}", "Creature — Minotaur", "Tribute 2 (As this creature enters, an opponent of your choice may put two +1/+1 counters on it.)", "2/2")
    private val lesson = Cards.invent("Auto2 Small Lesson", "{U}", "Sorcery — Lesson", "Draw a card.")
    private val sliver = Cards.invent("Auto2 Waiting Sprite", "{U}", "Creature — Faerie", "Suspend 2—{U} (Rather than cast this card from your hand, you may pay {U} and exile it with two time counters on it. At the beginning of your upkeep, remove a time counter. When the last is removed, you may cast it without paying its mana cost. It has haste.)", "1/1")

    private fun Scenario.alternativeWay(name: String): CastWay =
        Engine.options(state, alice.id).filterIsInstance<Input.Cast>().first { it.card == card(name) && it.way.alternative != null }.way

    private fun Scenario.pay() {
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
    }

    // CR 702.170a, b, d: plotting exiles it face up as a sorcery; on a later turn it is cast without paying its mana cost
    @Test
    fun plot() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(plotter)
        players.forEach { it.library("Forest", "Forest", "Forest") }
        val action = Engine.options(state, alice.id).filterIsInstance<Input.Activate>().single { it.source == card(plotter) }
        perform(action)
        pay()
        val o = state.objOrThrow(card(plotter))
        assertEquals(Zone.Exile, o.zone)
        assertTrue(ObjectMark.Plotted in o.marks && !o.faceDown)
        assertFailsWith<IllegalArgumentException> { alice.casts(plotter) }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.casts(plotter)
        allPass()
        expect {
            alice.battlefield has plotter
            "Mountain".tapped shouldBe false
        }
    }

    // CR 702.170d: a plotted instant is cast only as a sorcery
    @Test
    fun plottedInstantAsASorcery() = scenario {
        alice.battlefield("Island")
        alice.hand(plotSpell)
        players.forEach { it.library("Forest", "Forest", "Forest", "Forest") }
        perform(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().single { it.source == card(plotSpell) })
        pay()
        advanceTo(TurnStep.Upkeep, turn = 3)
        assertFailsWith<IllegalArgumentException> { alice.casts(plotSpell) }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.casts(plotSpell)
        allPass()
        expect { alice.graveyard has plotSpell }
    }

    // CR 702.168a, d: cast face down as a 2/2 with ward {2} for {3}, turned face up for its disguise cost
    @Test
    fun disguise() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(disguised)
        alice.casts(disguised, way = alternativeWay(disguised))
        allPass()
        val id = card(disguised)
        assertTrue(state.objOrThrow(id).faceDown)
        expect {
            card(disguised).power shouldBe 2
            card(disguised) hasKeyword Keyword.Ward.Kind
        }
        val turnUp = Engine.options(state, alice.id).filterIsInstance<Input.Activate>().single { it.source == id }
        perform(turnUp)
        pay()
        expect {
            disguised.power shouldBe 3
            disguised lacksKeyword Keyword.Ward.Kind
        }
    }

    // CR 701.58a, b: cloaked, the top card is a face-down 2/2 with ward {2}; a creature card turns face up for its mana cost
    @Test
    fun cloak() = scenario {
        alice.battlefield("Plains", "Forest", "Forest", "Forest")
        alice.hand("Shrouded Arrival")
        alice.library(bear)
        alice.casts("Shrouded Arrival")
        allPass()
        val id = card(bear)
        assertTrue(state.objOrThrow(id).faceDown && ObjectMark.Cloaked in state.objOrThrow(id).marks)
        expect { card(bear) hasKeyword Keyword.Ward.Kind }
        perform(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().single { it.source == id })
        pay()
        expect { bear.power shouldBe 2 }
        assertTrue(!state.objOrThrow(card(bear)).faceDown)
    }

    // CR 701.40a, c: manifest the top two cards, one at a time
    @Test
    fun manifest() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand("Twofold Manifestation")
        alice.library(bear, "Island", "Island")
        alice.casts("Twofold Manifestation")
        allPass()
        expect { alice.battlefield.size shouldBe 5 }
        assertEquals(2, state.battlefield.count { state.objOrThrow(it).faceDown && ObjectMark.Manifested in state.objOrThrow(it).marks })
    }

    // CR 702.165a: on another creature, a +1/+1 counter and the abilities printed below backup until end of turn
    @Test
    fun backup() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", bear)
        alice.hand(backer)
        alice.casts(backer)
        allPass()
        if (pending is Decision.Targets) answer(Answer.Targets(listOf(target(bear))))
        allPass()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Trample
        }
    }

    // CR 702.42a: paying the entwine cost, both modes
    @Test
    fun entwine() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        bob.battlefield(bear)
        alice.hand(entwined)
        alice.casts(entwined, modes = listOf(0, 1), targets = listOf(bear, "bob"))
        pay()
        allPass()
        expect {
            bob.graveyard has bear
            bob.life shouldBe 18
            "Mountain".tapped shouldBe true
        }
        assertEquals(4, state.battlefield.count { state.objOrThrow(it).tapped })
    }

    // CR 702.113a, b: cast for its awaken cost, a land you control becomes a 0/0 Elemental creature with haste and two counters
    @Test
    fun awaken() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(awakener)
        alice.library("Forest")
        alice.casts(awakener, way = alternativeWay(awakener), target = "Island")
        pay()
        allPass()
        expect {
            alice.hand.size shouldBe 1
            view.objects.values.count { it.characteristics?.name == "Island" && it.characteristics?.power == 2 } shouldBe 1
            view.objects.values.single { it.characteristics?.name == "Island" && it.characteristics?.power == 2 }.characteristics!!.hasKeyword(Keyword.Haste) shouldBe true
        }
    }

    // CR 702.113b: cast normally, no target
    @Test
    fun notAwakened() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(awakener)
        alice.library("Forest")
        alice.casts(awakener)
        allPass()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 702.104a: the chosen opponent may put the counters on it
    @Test
    fun tribute() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(tributer)
        alice.casts(tributer)
        allPass()
        if (pending is Decision.ChoosePlayer) alice.answers(Answer.Player(bob.id))
        assertEquals(bob.id, decision<Decision.May>().player)
        bob.answers(Answer.YesNo(true))
        expect { tributer.power shouldBe 4 }
    }

    // CR 701.39b: a +1/+1 counter on each of up to two target creatures
    @Test
    fun support() = scenario {
        alice.battlefield("Plains", "Plains", bear, "Auto Plain Ox")
        alice.hand("Rallying Support")
        alice.casts("Rallying Support", targets = listOf(bear, "Auto Plain Ox"))
        allPass()
        expect {
            bear.power shouldBe 3
            "Auto Plain Ox".power shouldBe 4
        }
    }

    // CR 701.10e: doubling the mana in a pool
    @Test
    fun doubleMana() = scenario {
        alice.battlefield("Forest")
        alice.hand("Mana Doubling")
        change(arcanum.engine.change.Changes.manaPool(state, alice.id, listOf(arcanum.engine.state.ManaUnit(arcanum.api.Color.Red),
            arcanum.engine.state.ManaUnit(arcanum.api.Color.Red)), emptyList()))
        alice.casts("Mana Doubling", paying = listOf("Forest"))
        allPass()
        assertEquals(4, state.player(alice.id).manaPool.count { it.color == arcanum.api.Color.Red })
    }

    // CR 701.57a: exile until a nonland card with mana value 3 or less; cast it free, or put it into your hand
    @Test
    fun discover() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand("Hidden Discovery")
        alice.library("Island", "Forest", bear, "Island")
        alice.casts("Hidden Discovery")
        allPass()
        alice.answers(Answer.YesNo(true))
        allPass()
        expect {
            alice.battlefield has bear
            alice.exile.size shouldBe 0
        }
        assertEquals(state.player(alice.id).library.size, 3)
    }

    // CR 701.63a: counters on it, or an N/N Spirit token
    @Test
    fun endure() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand("Enduring Sentinel")
        alice.casts("Enduring Sentinel")
        allPass()
        allPass()
        alice.answers(Answer.Option("Spirit token"))
        expect { alice.battlefield.size shouldBe 4 }
        val spirit = state.battlefield.last()
        assertEquals(2, view()[spirit]!!.characteristics!!.power)
    }

    // CR 701.48a: a Lesson card from outside the game into your hand
    @Test
    fun learn() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Curious Study")
        put(alice, listOf(lesson), Zone.Outside)
        alice.casts("Curious Study")
        allPass()
        alice.answers(Answer.Option("a Lesson card from outside the game"))
        if (pending is Decision.ChooseObjects) alice.chooses(lesson)
        expect { alice.hand has lesson }
    }

    // CR 701.56a: add or remove a time counter on each suspended card
    @Test
    fun timeTravel() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(sliver, "Temporal Nudge")
        perform(Engine.options(state, alice.id).filterIsInstance<Input.Activate>().single { it.source == card(sliver) })
        pay()
        alice.casts("Temporal Nudge")
        allPass()
        alice.answers(Answer.Option("remove a time counter"))
        expect { sliver.counters(CounterKind.Time) shouldBe 1 }
    }
}
