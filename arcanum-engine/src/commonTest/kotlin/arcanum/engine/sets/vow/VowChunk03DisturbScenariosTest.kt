package arcanum.engine.sets.vow

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.fin.backFaceUp
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** VOW chunk 03: disturb cards whose abilities give the engine something new: "becomes the target of an Aura spell" triggers, a Curse
 * that makes the enchanted player lose, a token copy of the enchanted creature, and an Aura counting its controller's permanents. */
class VowChunk03DisturbScenariosTest {
    private val disturb = "(You may cast this card from your graveyard transformed for its disturb cost.)"
    private fun comber(front: String, back: String) = Cards.inventFaces("Transform",
        face(front, "{1}{W}{U}", "Creature — Spirit", """
            Whenever this creature enters or becomes the target of an Aura spell, create a 1/1 white Spirit creature token with flying.
            Disturb {W}{U} $disturb
        """.trimIndent(), "1/1", null, null),
        face(back, null, "Enchantment — Aura", """
            Enchant creature
            Whenever this Aura enters or enchanted creature becomes the target of an Aura spell, create a 1/1 white Spirit creature token with flying.
            If this Aura would be put into a graveyard from anywhere, exile it instead.
        """.trimIndent(), null, null, "WU"))
    private val wisp = comber("Saltspray Wisp", "Saltspray Offering")
    private val otherWisp = comber("Brinemist Wisp", "Brinemist Offering")
    private val charm = Cards.invent("Wispward Charm", "{W}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets +1/+1.")
    private val spark = Cards.invent("Wispward Spark", "{R}", "Instant", "Wispward Spark deals 1 damage to target creature.")
    private val bear = Cards.invent("Wispward Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val judge = Cards.inventFaces("Transform",
        face("Verdict Warden", "{1}{W}{W}", "Creature — Spirit Soldier", """
            Defender, flying, vigilance
            At the beginning of your upkeep, if this creature has two or fewer judgment counters on it, put a judgment counter on it.
            As long as this creature has three or more judgment counters on it, it can attack as though it didn't have defender.
            Disturb {5}{W}{W}
        """.trimIndent(), "4/4", null, null),
        face("Verdict of the Damned", null, "Enchantment — Aura Curse", """
            Enchant player
            At the beginning of your upkeep, put a judgment counter on this Aura. Then if there are three or more judgment counters on it, enchanted player loses the game.
            If Verdict of the Damned would be put into a graveyard from anywhere, exile it instead.
        """.trimIndent(), null, null, "W"))
    private val mimic = Cards.inventFaces("Transform",
        face("Mirrorpane Mimic", "{3}{U}", "Creature — Spirit", """
            You may have this creature enter as a copy of any creature on the battlefield, except it's a Spirit in addition to its other types.
            Disturb {3}{U}{U} $disturb
        """.trimIndent(), "0/0", null, null),
        face("Mirrorpane Haunting", null, "Enchantment — Aura", """
            Enchant creature
            At the beginning of your upkeep, create a token that's a copy of enchanted creature, except it's a Spirit in addition to its other types.
            If Mirrorpane Haunting would be put into a graveyard from anywhere, exile it instead.
        """.trimIndent(), null, null, "U"))
    private val martyr = Cards.inventFaces("Transform",
        face("Ysolde, Dawnward Martyr", "{1}{W}{W}", "Legendary Creature — Spirit Warlock", """
            Flying, lifelink, protection from Vampires
            Ysolde's power and toughness are each equal to the number of permanents you control that are Spirits and/or enchantments.
            Disturb {3}{W}{W} $disturb
        """.trimIndent(), "*/*", null, null),
        face("Ysolde's Rising Dawn", null, "Legendary Enchantment — Aura", """
            Enchant creature
            Enchanted creature has flying, lifelink, and protection from Vampires, and it gets +X/+X, where X is the number of permanents you control that are Spirits and/or enchantments.
            If Ysolde's Rising Dawn would be put into a graveyard from anywhere, exile it instead.
        """.trimIndent(), null, null, "W"))
    private val shade = Cards.invent("Wispward Shade", "{1}{W}", "Creature — Spirit", "", "1/1")
    private val bobShade = Cards.invent("Wispward Haunt", "{1}{W}", "Creature — Spirit", "", "1/1")

    // CR 303.4a: an Aura spell targets the object it will enchant, so the creature becomes its target, whoever casts it (CR 115.10)
    @Test
    fun anAuraSpellTargetingItMakesASpirit() = scenario(players = 3) {
        alice.battlefield(wisp, "Plains")
        bob.battlefield("Plains")
        bob.hand(charm)
        turn(number = 2, active = bob)
        bob.casts(charm, target = wisp)
        resolveAll()
        expect {
            alice.battlefield has "Spirit Token"
            "Spirit Token".controller shouldBe alice.id
            wisp.power shouldBe 2
        }
    }

    // CR 115.10: only an Aura spell counts; a damage spell targeting it, or an Aura spell targeting another creature, makes nothing
    @Test
    fun otherSpellsOrOtherTargetsMakeNothing() = scenario {
        alice.battlefield(wisp, bear, "Plains", "Mountain")
        alice.hand(charm, spark)
        alice.casts(spark, target = wisp)
        resolveAll()
        alice.casts(charm, target = bear)
        resolveAll()
        expect {
            alice.battlefield lacks "Spirit Token"
            bear.power shouldBe 3
        }
    }

    // CR 702.146a, 303.4a: the disturb Aura cast on the front face creature is an Aura spell targeting it, then it enters (two tokens); a
    // later Aura spell targeting the enchanted creature triggers both the creature and the Aura
    @Test
    fun disturbedAuraOnTheCreatureAndALaterAura() = scenario {
        alice.battlefield(wisp, "Plains", "Island", "Plains")
        alice.graveyard(otherWisp)
        alice.hand(charm)
        alice.casts(otherWisp, target = wisp, paying = listOf("Plains", "Island"))
        resolveAll()
        expect { alice.battlefield has otherWisp }
        assertEquals(2, tokens("Spirit Token").size)
        alice.casts(charm, target = wisp)
        resolveAll()
        assertEquals(4, tokens("Spirit Token").size)
    }

    // CR 303.4e, 104.3a: the enchanted player loses on the third counter; with three players the game goes on (CR 800.4a)
    @Test
    fun theCurseMakesTheEnchantedPlayerLose() = scenario(players = 3) {
        alice.battlefield(judge, counters = listOf(CounterKind.Named("judgment") to 2))
        backFaceUp(judge)
        attach(judge, "bob")
        alice.library("Plains", "Plains")
        bob.library("Plains", "Plains")
        carol.library("Plains", "Plains")
        advanceTo(TurnStep.Upkeep, turn = 4)
        resolveAll()
        expect {
            // CR 704.5m, 702.146b: the Curse attached to a player who left the game goes to exile instead of a graveyard
            judge.zone shouldBe Zone.Exile
            state.player(bob.id).lost shouldBe true
            state.player(carol.id).lost shouldBe false
            state.player(alice.id).lost shouldBe false
        }
    }

    // CR 707.2, 707.9b, 111.2: the token copies the enchanted creature (an opponent's) under the Aura's controller, a Spirit too
    @Test
    fun theUpkeepCopiesTheEnchantedCreatureAsASpirit() = scenario {
        alice.battlefield(mimic)
        backFaceUp(mimic)
        bob.battlefield(bear)
        attach(mimic, bear)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        val copies = tokens(bear)
        assertEquals(1, copies.size)
        val copy = state.obj(copies.single())!!
        assertEquals(alice.id, copy.controller)
        expect {
            val c = arcanum.engine.scenario.CardRef(copies.single(), "copy")
            c.types shouldBe listOf(CardType.Creature)
            assertTrue(Subtype("Spirit") in c.subtypes && Subtype("Bear") in c.subtypes)
            c.power shouldBe 2
        }
    }

    // CR 613.4c, 109.5 ("you" is the Aura's controller): X counts the Aura's controller's Spirits and enchantments, the Aura itself included
    @Test
    fun theAuraCountsItsControllersSpiritsAndEnchantments() = scenario(players = 3) {
        alice.battlefield(martyr, shade)
        backFaceUp(martyr)
        bob.battlefield(bear, bobShade)
        attach(martyr, bear)
        expect {
            bear.power shouldBe 4
            bear.toughness shouldBe 4
            bear hasKeyword Keyword.Lifelink.kind
            bear hasKeyword Keyword.Flying.kind
        }
    }
}
