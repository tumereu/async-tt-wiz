package arcanum.engine.sets.inr

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import arcanum.engine.state.TargetRef
import arcanum.parser.OracleCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** INR chunk 02: transforming double-faced cards in play, part 2: a cost that sacrifices five creatures and untaps the land that
 * transforms, a delayed transform at the next upkeep whose back face damages every other creature and each opponent, an activation
 * that needs five Vampires, a life-total threshold, a tap-another-creature cost, a counter-and-branch end step trigger and a Siege
 * whose back face exiles a graveyard card. */
class InrTransformPart2ScenariosTest {
    private val chapel = Cards.inventFaces("Transform",
        face("Tallow Moor Chapel", null, "Land", """
            {T}: Add {C}.
            {5}, {T}, Sacrifice five creatures: Transform this land, then untap it.
        """.trimIndent(), null, null, null),
        face("Prince of Tallow Moor", null, "Legendary Creature — Demon", "Flying, lifelink, indestructible, haste", "9/7", null, "B"))
    private val seraph = Cards.inventFaces("Transform",
        face("Dawnwatch Seraph", "{3}{W}{W}", "Legendary Creature — Angel", """
            Flash
            Flying, vigilance
            When a non-Angel creature you control dies, transform this creature at the beginning of the next upkeep.
        """.trimIndent(), "4/4", null, null),
        face("Dawnwatch Cleanser", null, "Legendary Creature — Angel", """
            Flying
            When this creature transforms into Dawnwatch Cleanser, it deals 3 damage to each other creature and each opponent.
        """.trimIndent(), "6/5", null, "W"))
    private val matron = Cards.inventFaces("Transform",
        face("Nightcrypt Matron", "{2}{B}{B}", "Creature — Vampire", """
            Flying
            {B}: Transform this creature. Activate only if you control five or more Vampires.
        """.trimIndent(), "3/3", null, null),
        face("Nightcrypt Empress", null, "Creature — Vampire", """
            Flying
            Other Vampire creatures you control get +2/+2.
        """.trimIndent(), "5/5", null, "B"))
    private val grail = Cards.inventFaces("Transform",
        face("Hollow Grail", "{3}", "Artifact",
            "{T}: You gain 1 life. Then if you have at least 10 life more than your starting life total, transform this artifact.", null, null, null),
        face("Hollow Chalice of Ruin", null, "Artifact", "{T}: Target player loses 5 life.", null, null, null))
    private val busybody = Cards.inventFaces("Transform",
        face("Parish Busybody", "{W}", "Creature — Human", "{T}, Tap an untapped creature you control: Transform this creature.", "1/1", null, null),
        face("Parish Mob", null, "Creature — Human", "This creature attacks each combat if able.", "2/3", null, "W"))
    private val banquet = Cards.inventFaces("Transform",
        face("Hollow Banquet", "{2}{W}", "Enchantment", """
            At the beginning of your end step, put an invitation counter on this enchantment. If you attacked with two or more creatures this turn, draw a card. Otherwise, create a 1/1 white Human creature token. Then if this enchantment has three or more invitation counters on it, transform it.
        """.trimIndent(), null, null, null),
        face("Hollow Feast", null, "Enchantment", "Creatures you control get +1/+1.", null, null, "W"))
    private val moors = Cards.inventFaces("Transform",
        OracleCard("Siege of the Hushed Moors", "{2}{B}{B}", "Battle — Siege", """
            (As a Siege enters, choose an opponent to protect it. You and others can attack it. When it's defeated, exile it, then cast it transformed.)
            When this Siege enters, target creature an opponent controls gets -13/-13 until end of turn.
        """.trimIndent(), defense = "5"),
        OracleCard("Hushed Moors Rising", null, "Enchantment", """
            When this enchantment enters, create two 2/2 black Zombie creature tokens.
            {2}{B}: Exile target card from a graveyard. If it was a creature card, create a 2/2 black Zombie creature token.
        """.trimIndent(), colorIndicator = "B"))
    private val zap = Cards.invent("Dawnwatch Zap", "{R}", "Instant", "Dawnwatch Zap deals 1 damage to any target.")
    private val bear = Cards.invent("Dawnwatch Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Dawnwatch Ox", "{3}{G}", "Creature — Ox", "", "2/4")
    private val stag = Cards.invent("Dawnwatch Stag", "{G}", "Creature — Elk", "", "1/1")
    private val bat = Cards.invent("Dawnwatch Vampire", "{B}", "Creature — Vampire", "", "1/1")

    private fun Scenario.face(name: String): Int? = state.obj(card(name))?.face
    private fun Scenario.libraries() = players.forEach { it.library(bear, bear, bear) }
    private fun Scenario.zombies() = state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) } == "Zombie Token" }

    // CR 602.2b (costs paid together), 701.27a, 502.3: it transforms, then untaps; the sacrificed creatures are gone
    @Test
    fun theChapelSacrificesFiveCreaturesTransformsAndUntaps() = scenario {
        alice.battlefield(chapel, "Island", "Island", "Island", "Island", "Island")
        alice.battlefield(bear, bear, bear, bear, bear)
        libraries()
        alice.activates(chapel, ability = 1)
        resolveAll()
        assertEquals(1, face(chapel))
        expect {
            chapel.tapped shouldBe false
            alice.graveyard.size shouldBe 5
            chapel.power shouldBe 9
        }
    }

    // CR 602.2: with four creatures the cost can't be paid
    @Test
    fun theChapelNeedsFiveCreatures() = scenario {
        alice.battlefield(chapel, "Island", "Island", "Island", "Island", "Island")
        alice.battlefield(bear, bear, bear, bear)
        libraries()
        assertFailsWith<IllegalArgumentException> { alice.activates(chapel, ability = 1) }
    }

    // CR 603.7, 701.27a, 701.27f, 120.3: a non-Angel creature dying sets up the transform at the next upkeep; the back face then hits
    // every other creature and each opponent (three players)
    @Test
    fun theSeraphTransformsAtTheNextUpkeepAndHurtsEveryOpponent() = scenario(players = 3) {
        alice.battlefield(seraph, stag, "Mountain")
        bob.battlefield(ox, stag)
        carol.battlefield(ox)
        alice.hand(zap)
        libraries()
        alice.casts(zap, target = stag)
        resolveAll()
        assertEquals(0, face(seraph))
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        assertEquals(1, face(seraph))
        expect {
            bob.life shouldBe 17
            carol.life shouldBe 17
            alice.life shouldBe 20
            seraph.power shouldBe 6
        }
    }

    // CR 602.5b: "Activate only if you control five or more Vampires"; CR 613.4c: the back face's lord bonus
    @Test
    fun theMatronNeedsFiveVampires() = scenario {
        alice.battlefield(matron, bat, bat, bat, "Swamp")
        libraries()
        assertFailsWith<IllegalArgumentException> { alice.activates(matron, ability = 0) }
    }

    @Test
    fun theMatronTransformsWithFiveVampiresAndLordsTheOthers() = scenario {
        alice.battlefield(matron, bat, bat, bat, bat, "Swamp")
        libraries()
        alice.activates(matron, ability = 0)
        resolveAll()
        assertEquals(1, face(matron))
        expect { bat.power shouldBe 3 }
    }

    // CR 119.2, 103.4: 29 life gaining 1 makes 30, ten more than the starting 20
    @Test
    fun theGrailTransformsAtTenMoreThanStartingLife() = scenario {
        alice.battlefield(grail)
        alice.life = 29
        libraries()
        alice.activates(grail, ability = 0)
        resolveAll()
        assertEquals(1, face(grail))
    }

    @Test
    fun theGrailStaysBelowThat() = scenario {
        alice.battlefield(grail)
        alice.life = 28
        libraries()
        alice.activates(grail, ability = 0)
        resolveAll()
        assertEquals(0, face(grail))
        expect { alice.life shouldBe 29 }
    }

    // CR 118.3, 701.26a (tap as a cost); the other creature must be untapped
    @Test
    fun theBusybodyTapsAnotherCreatureAndTransforms() = scenario {
        alice.battlefield(busybody, bear)
        libraries()
        alice.activates(busybody, ability = 0, choosing = listOf(bear))
        resolveAll()
        assertEquals(1, face(busybody))
        expect { bear.tapped shouldBe true }
    }

    @Test
    fun theBusybodyHasNothingToTap() = scenario {
        alice.battlefield(busybody)
        libraries()
        assertFailsWith<IllegalArgumentException> { alice.activates(busybody, ability = 0) }
    }

    // CR 603.4, 608.2c: no attack: a token, and the third counter transforms it
    @Test
    fun theBanquetMakesATokenWhenYouDidntAttackAndTransforms() = scenario {
        alice.battlefield(banquet, counters = listOf(CounterKind.Named("invitation") to 2))
        libraries()
        advanceTo(TurnStep.End, turn = 1)
        resolveAll()
        assertEquals(1, face(banquet))
        expect {
            "Human Token".zone shouldBe Zone.Battlefield
            alice.hand.size shouldBe 0
        }
    }

    // two attackers: a card, no token, and with one counter it stays
    @Test
    fun theBanquetDrawsWhenYouAttackedWithTwo() = scenario {
        alice.battlefield(banquet, bear, ox)
        libraries()
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(bear, ox)
        resolveAll()
        val before = state.player(alice.id).hand.size
        advanceTo(TurnStep.End, turn = 3)
        resolveAll()
        assertEquals(0, face(banquet))
        expect { alice.hand.size shouldBe before + 1 }
    }

    // CR 310.12b: the defeated Siege is cast transformed; CR 701.13 (exile): a creature card exiled from a graveyard makes a Zombie
    @Test
    fun theHushedMoorsMakeZombiesAndExileACreatureCard() = scenario {
        alice.battlefield(moors, counters = listOf(CounterKind.Defense to 2))
        alice.battlefield(ox, "Swamp", "Swamp", "Swamp")
        bob.graveyard(stag)
        libraries()
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(ox), TargetRef.Object(card(moors))))))
        advanceTo(TurnStep.CombatDamage)
        allPass()
        answer(Answer.YesNo(true))
        resolveAll()
        assertEquals(2, zombies())
        alice.activates("Hushed Moors Rising", target = stag)
        resolveAll()
        assertEquals(3, zombies())
        expect { bob.exile has stag }
    }
}
