package arcanum.engine.sets.bro

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test

/**
 * The Brothers' War chunk 03 in play: prototype creatures cast prototyped (CR 702.160, 718) and the abilities that read their size:
 * token copies of a prototyped permanent, entering as a copy with its own power and toughness, a land becoming a creature of its size,
 * an X/X token three times its power, and a graveyard target limited by its power.
 */
class BroChunk03PrototypeScenariosTest {
    private val reminder = "(You may cast this spell with different mana cost, color, and size. It keeps its abilities and types.)"

    private val scuttlelightBattalion = Cards.invent("Scuttlelight Battalion", "{9}", "Artifact Creature — Construct", """
        Prototype {3}{R}{R} — 2/2 $reminder
        Trample, haste
        When this creature enters, if you cast it, create two tokens that are copies of it.
    """, "4/4")
    private val bulkyShapecaster = Cards.invent("Bulky Shapecaster", "{9}", "Artifact Creature — Shapeshifter", """
        Prototype {2}{U}{U} — 3/3 $reminder
        You may have this creature enter as a copy of an artifact or creature you control, except it's an artifact creature in addition to its other types, and its power and toughness are equal to this creature's power and toughness.
    """, "7/7")
    private val treecallAutomaton = Cards.invent("Treecall Automaton", "{10}", "Artifact Creature — Construct", """
        Prototype {2}{G}{G} — 3/3 $reminder
        When this creature enters, if you cast it, untap target land you control. It becomes a Treefolk creature with haste and base power and toughness equal to this creature's power and toughness. It's still a land.
    """, "8/8")
    private val tanglewireAmalgam = Cards.invent("Tanglewire Amalgam", "{5}", "Artifact Creature — Golem", """
        Prototype {1}{G} — 2/3 $reminder
        {3}{G}{G}, Sacrifice this creature: Create an X/X colorless Golem artifact creature token, where X is three times this creature's power. It gains haste until end of turn. Activate only as a sorcery.
    """, "5/5")
    private val spellmirrorAutomaton = Cards.invent("Spellmirror Automaton", "{7}", "Artifact Creature — Wizard", """
        Prototype {1}{U}{U} — 2/1 $reminder
        When this creature enters, if you cast it, exile target instant or sorcery card with mana value less than or equal to this creature's power from your graveyard. Copy that card. You may cast the copy without paying its mana cost.
    """, "4/3")
    private val mossrockGolem = Cards.invent("Mossrock Golem", "{7}", "Artifact Creature — Golem", """
        Prototype {3}{G} — 3/3 $reminder
        When this creature enters, you gain life equal to its power.
    """, "6/5")
    private val bogBoar = Cards.invent("Rustbristle Boar", "{1}{G}", "Creature — Boar", "", "2/2")
    private val quickStudy = Cards.invent("Gearwise Lesson", "{1}{U}", "Sorcery", "Draw two cards.")
    private val deepStudy = Cards.invent("Gearwise Treatise", "{2}{U}", "Sorcery", "Draw three cards.")

    private fun Scenario.prototyped(name: String): CastWay =
        Engine.options(state, alice.id).filterIsInstance<Input.Cast>().first { it.card == card(name) && it.way.alternative != null }.way

    // CR 718.3b: cast prototyped it is a red 2/2; CR 718.3d: the token copies of it are prototyped too (red 2/2s with mana value 5)
    @Test
    fun scuttlelightBattalionPrototypedMakesTwoPrototypedCopies() = scenario {
        alice.battlefield(*Array(5) { "Mountain" })
        alice.hand(scuttlelightBattalion)
        alice.casts(scuttlelightBattalion, way = prototyped(scuttlelightBattalion))
        playOut()
        expect {
            alice.battlefield.names.count { it == scuttlelightBattalion } shouldBe 3
            for (i in 0..2) {
                card(scuttlelightBattalion, i).power shouldBe 2
                card(scuttlelightBattalion, i).toughness shouldBe 2
                card(scuttlelightBattalion, i).colors shouldBe listOf(Color.Red)
                card(scuttlelightBattalion, i).view.characteristics!!.manaValue() shouldBe 5
                card(scuttlelightBattalion, i) hasKeyword Keyword.Haste.kind
            }
        }
    }

    // CR 707.9b, 718.3b: cast prototyped, it enters as a copy of the Boar that is also an artifact and is 3/3; the copy is green, not blue
    @Test
    fun bulkyShapecasterPrototypedCopiesAtThreeThree() = scenario {
        alice.battlefield(*Array(4) { "Island" }, bogBoar)
        alice.hand(bulkyShapecaster)
        alice.casts(bulkyShapecaster, way = prototyped(bulkyShapecaster))
        playOut(bogBoar)
        expect {
            card(bulkyShapecaster).view.characteristics!!.name shouldBe bogBoar
            card(bulkyShapecaster).power shouldBe 3
            card(bulkyShapecaster).toughness shouldBe 3
            card(bulkyShapecaster).types.toSet() shouldBe setOf(CardType.Artifact, CardType.Creature)
            card(bulkyShapecaster).colors shouldBe listOf(Color.Green)
            card(bulkyShapecaster).subtypes shouldBe listOf(Subtype("Boar"))
        }
    }

    // CR 707.9b, 718.4: cast normally, the copy is 7/7
    @Test
    fun bulkyShapecasterCastNormallyCopiesAtSevenSeven() = scenario {
        alice.battlefield(*Array(9) { "Island" }, bogBoar)
        alice.hand(bulkyShapecaster)
        alice.casts(bulkyShapecaster)
        playOut(bogBoar)
        expect {
            card(bulkyShapecaster).view.characteristics!!.name shouldBe bogBoar
            card(bulkyShapecaster).power shouldBe 7
            card(bulkyShapecaster).toughness shouldBe 7
            card(bulkyShapecaster).types.toSet() shouldBe setOf(CardType.Artifact, CardType.Creature)
        }
    }

    // CR 603.4, 613.1d, 613.4b, 205.1b: the tapped land untaps and becomes a 3/3 Treefolk land creature with haste for good; CR 608.2h: its size
    // was fixed as the effect began, so it stays 3/3 after the Automaton leaves
    @Test
    fun treecallAutomatonPrototypedAnimatesALandAtItsSize() = scenario {
        alice.battlefield(*Array(4) { "Forest" })
        alice.battlefield("Plains", tapped = true)
        alice.hand(treecallAutomaton)
        alice.casts(treecallAutomaton, way = prototyped(treecallAutomaton), paying = listOf("Forest", "Forest#1", "Forest#2", "Forest#3"))
        playOut("Plains")
        move(treecallAutomaton, Zone.Graveyard)
        expect {
            "Plains".tapped shouldBe false
            "Plains".power shouldBe 3
            "Plains".toughness shouldBe 3
            "Plains".types shouldBe listOf(CardType.Land, CardType.Creature)
            "Plains".subtypes shouldBe listOf(Subtype("Plains"), Subtype("Treefolk"))
            "Plains" hasKeyword Keyword.Haste.kind
        }
    }

    // CR 107.3c, 608.2h: sacrificed as a prototyped 2/3, its last known power makes a 6/6 colorless Golem with haste (CR 111.4)
    @Test
    fun tanglewireAmalgamPrototypedMakesASixSixGolem() = scenario {
        alice.battlefield(*Array(7) { "Forest" })
        alice.hand(tanglewireAmalgam)
        alice.casts(tanglewireAmalgam, way = prototyped(tanglewireAmalgam), paying = listOf("Forest", "Forest#1"))
        playOut()
        alice.activates(tanglewireAmalgam, paying = listOf("Forest#2", "Forest#3", "Forest#4", "Forest#5", "Forest#6"))
        playOut()
        expect {
            alice.graveyard has tanglewireAmalgam
            "Golem Token".power shouldBe 6
            "Golem Token".toughness shouldBe 6
            "Golem Token".colors shouldBe emptyList()
            "Golem Token" hasKeyword Keyword.Haste.kind
        }
    }

    // CR 718.3b, 115.1: cast prototyped it is a 2/1, so only a card with mana value 2 or less can be its target (the lone legal target is
    // chosen without asking); CR 707.12: the copy is cast without paying its mana cost
    @Test
    fun spellmirrorAutomatonPrototypedTargetsUpToItsPower() = scenario {
        alice.battlefield(*Array(3) { "Island" })
        alice.graveyard(quickStudy, deepStudy)
        alice.library("Island", "Island", "Island")
        alice.hand(spellmirrorAutomaton)
        alice.casts(spellmirrorAutomaton, way = prototyped(spellmirrorAutomaton))
        playOut()
        expect {
            alice.exile has quickStudy
            alice.graveyard has deepStudy
            alice.hand.size shouldBe 2
        }
    }

    // CR 718.3b: prototyped it gains 3 life; CR 718.4: back in the hand it is again a {7} 6/5 card
    @Test
    fun mossrockGolemPrototypedGainsThreeAndIsWholeAgainInTheHand() = scenario {
        alice.battlefield(*Array(4) { "Forest" })
        alice.hand(mossrockGolem)
        alice.casts(mossrockGolem, way = prototyped(mossrockGolem))
        playOut()
        expect { alice.life shouldBe 23 }
        move(mossrockGolem, Zone.Hand)
        expect {
            mossrockGolem.power shouldBe 6
            mossrockGolem.view.characteristics!!.manaValue() shouldBe 7
            mossrockGolem.colors shouldBe emptyList()
        }
    }
}
