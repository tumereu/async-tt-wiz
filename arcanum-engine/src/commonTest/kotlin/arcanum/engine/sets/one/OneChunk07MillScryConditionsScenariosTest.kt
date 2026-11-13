package arcanum.engine.sets.one

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.CardRef
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.chooseNone
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.ltr.aliceUpkeep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Phyrexia: All Will Be One chunk 07 in play: each opponent may scry (CR 701.22c), a copy token with exceptions (CR 707.9b), a cast
 * while an ability resolves and "can't cast additional spells" (CR 608.2g, 101.2), "if you controlled" (CR 608.2h), statics and an upkeep
 * sacrifice by oil counters, damage prevention with a reflexive trigger (CR 615.5, 603.12), reveal until a lesser mana value (CR 701.20),
 * dying triggers doubled (CR 603.2d) and a poison replacement with a delayed return (CR 614.1a, 603.7c).
 */
class OneChunk07MillScryConditionsScenariosTest {
    private val oil = CounterKind.Named("oil")

    private val archkeeper = Cards.invent("Myr Archkeeper", "{3}", "Artifact Creature — Myr",
        "When this creature enters, scry 2. Then each opponent may scry 1. (To scry X, a player looks at the top X cards of their library, then puts any number of them on the bottom and the rest on top in any order.)", "2/3")
    private val seraphine = Cards.invent("Seraphine, Veiled Reaper", "{3}{W}{W}{B}{B}", "Legendary Planeswalker — Seraphine", """
        Hexproof
        [+2]: Each opponent loses 3 life and you gain 3 life.
        [0]: You draw two cards. Then each opponent may scry 1.
        [−3]: Exile target creature or enchantment. If it wasn't an Aura, create a token that's a copy of it, except it's a 1/1 white Spirit creature with flying in addition to its other types.
    """, loyalty = "6")
    private val gateway = Cards.invent("Gateway of Strata", "{2}{G}{G}", "Artifact", """
        You may play lands from your graveyard.
        {T}: Choose target nonland permanent card in your graveyard. If you haven't cast a spell this turn, you may cast that card. If you do, you can't cast additional spells this turn. Activate only as a sorcery.
    """)
    private val wrecking = Cards.invent("Joyous Wrecking", "{R}", "Sorcery",
        "Destroy target artifact. If you controlled that artifact, create three 1/1 red Phyrexian Goblin creature tokens.")
    private val quillback = Cards.invent("Mutated Quillback", "{2}{G}{G}", "Creature — Phyrexian Beast", """
        This creature enters with four oil counters on it.
        This creature has trample as long as it has two or fewer oil counters on it. Otherwise, it has hexproof.
        At the beginning of your upkeep, remove an oil counter from this creature. Then if it has no oil counters on it, sacrifice it.
    """, "5/5")
    private val retaliator = Cards.invent("Phyrexian Retaliator", "{W}{W}{W}{W}", "Creature — Phyrexian Horror", """
        Flying
        If damage would be dealt to this creature, prevent that damage. When damage is prevented this way, this creature deals that much damage to any other target.
    """, "5/5")
    private val morvath = Cards.invent("Morvath, Crucible Brute", "{2}{B}{R}", "Legendary Creature — Phyrexian Beast",
        "At the beginning of your end step, you may sacrifice another creature. If you do, reveal cards from the top of your library until you reveal a nonlegendary creature card with lesser mana value, put it onto the battlefield, then put the rest on the bottom of your library in a random order.", "4/4")
    private val skelvar = Cards.invent("Skelvar, Slaughter Lord", "{3}{B}{B}", "Legendary Creature — Phyrexian Horror", """
        If a creature dying causes a triggered ability of a permanent you control to trigger, that ability triggers an additional time.
        {B/P}{B/P}, Exile three creature cards from your graveyard: Put an indestructible counter on Skelvar. ({B/P} can be paid with either {B} or 2 life.)
    """, "8/3")
    private val ysolde = Cards.invent("Ysolde, the Mending Hand", "{G}{W}", "Legendary Creature — Human Scout", """
        If you would get one or more poison counters, instead you get one poison counter and you can't get additional poison counters this turn.
        Exile Ysolde: Choose another target creature or artifact. When it's put into a graveyard this turn, return that card to the battlefield under its owner's control.
    """, "3/3")

    private val brassworkMyr = Cards.invent("Brasswork Myr", "{2}", "Artifact Creature — Myr",
        "{2}: This creature becomes a 4/4 Golem artifact creature until end of turn.", "1/1")
    private val hound = Cards.invent("Quarry Hound", "{2}{G}", "Creature — Dog", "Trample", "3/3")
    private val ward = Cards.invent("Shrouding Veil", "{1}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +1/+1.
    """)
    private val mauler = Cards.invent("Gravel Mauler", "{1}{G}", "Creature — Beast", "", "2/2")
    private val grub = Cards.invent("Ashpit Grub", "{G}", "Creature — Insect", "", "1/1")
    private val chieftain = Cards.invent("Ashpit Chieftain", "{B}", "Legendary Creature — Insect", "", "1/1")
    private val brute = Cards.invent("Ashpit Hulk", "{2}{G}", "Creature — Beast", "", "3/3")
    private val trinket = Cards.invent("Brass Trinket", "{1}", "Artifact")
    private val spark = Cards.invent("Cinder Lash", "{R}", "Instant", "Cinder Lash deals 3 damage to any target.")
    private val chanter = Cards.invent("Carrion Chanter", "{1}{B}", "Creature — Zombie", "Whenever another creature dies, you gain 1 life.", "1/1")
    private val drip = Cards.invent("Venom Drip", "{B}", "Sorcery", "Target player gets two poison counters.")
    private val lesson = Cards.invent("Strata Lesson", "{U}", "Sorcery", "Draw a card.")

    // CR 701.22c, 101.4: alice scries 2; each opponent then decides in turn order whether to scry 1, and those who do scry
    @Test
    fun archkeeperLetsEachOpponentChooseToScry() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(archkeeper)
        alice.library("Plains", "Plains")
        bob.library("Island", "Forest")
        carol.library("Swamp", "Mountain")
        alice.casts(archkeeper)
        resolveAll()
        assertEquals(alice.id, decision<Decision.ChooseObjects>().player)
        chooseNone()
        // alice orders the two cards she keeps on top
        decision<Decision.ChooseObjects>().let { assertEquals(alice.id, it.player); answer(Answer.Objects(it.options)) }
        assertEquals(bob.id, decision<Decision.May>().player)
        answer(Answer.YesNo(true))
        assertEquals(carol.id, decision<Decision.May>().player)
        answer(Answer.YesNo(false))
        assertEquals(bob.id, decision<Decision.ChooseObjects>().player)
        answer(Answer.Objects(listOf(card("Island"))))
        resolveAll()
        expect {
            bob.library.names shouldBe listOf("Forest", "Island")
            carol.library.names shouldBe listOf("Swamp", "Mountain")
        }
    }

    // CR 707.9b: the token copies the exiled creature except that it is a 1/1 white Spirit with flying in addition to its other types
    @Test
    fun seraphineCopiesTheExiledCreatureAsASpirit() = scenario {
        alice.battlefield(seraphine, counters = listOf(CounterKind.Loyalty to 6))
        bob.battlefield(hound)
        alice.activates(seraphine, ability = 2, target = hound)
        resolveAll()
        expect {
            bob.exile.names shouldBe listOf(hound)
            val token = CardRef(this@scenario.card(hound, zone = Zone.Battlefield), "token")
            token.controller shouldBe alice.id
            token.power shouldBe 1
            token.toughness shouldBe 1
            token.colors shouldBe listOf(Color.White)
            token.types shouldBe listOf(CardType.Creature)
            token.subtypes.toSet() shouldBe setOf(Subtype("Dog"), Subtype("Spirit"))
            token hasKeyword Keyword.Flying.kind
            token hasKeyword Keyword.Trample.kind
        }
    }

    // CR 608.2h: an Aura exiled this way makes no token
    @Test
    fun seraphineMakesNoTokenOfAnAura() = scenario {
        alice.battlefield(seraphine, counters = listOf(CounterKind.Loyalty to 6))
        bob.battlefield(hound, ward)
        attach(ward, hound)
        alice.activates(seraphine, ability = 2, target = ward)
        resolveAll()
        expect {
            ward.zone shouldBe Zone.Exile
            alice.battlefield.names shouldBe listOf(seraphine)
        }
    }

    // CR 608.2g: the card is cast while the ability resolves; CR 101.2: then no more spells this turn
    @Test
    fun gatewayCastsFromTheGraveyardThenNoMoreSpells() = scenario {
        alice.battlefield(gateway, "Forest", "Forest", "Island")
        alice.graveyard(mauler)
        alice.hand(lesson)
        alice.library("Island")
        alice.activates(gateway, target = mauler)
        resolveAll()
        may(true)
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        expect { mauler.zone shouldBe Zone.Battlefield }
        assertFailsWith<IllegalArgumentException> { alice.casts(lesson) }
    }

    // CR 608.2c: a spell already cast this turn: the card stays in the graveyard and other spells may still be cast
    @Test
    fun gatewayDoesNothingAfterASpell() = scenario {
        alice.battlefield(gateway, "Forest", "Forest", "Island", "Island")
        alice.graveyard(mauler)
        alice.hand(lesson, lesson)
        alice.library("Island", "Island")
        alice.casts(lesson)
        resolveAll()
        alice.activates(gateway, target = mauler)
        resolveAll()
        assertTrue(pending !is Decision.May, "no offer to cast: $pending")
        expect { mauler.zone shouldBe Zone.Graveyard }
        alice.casts(lesson)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    // CR 608.2h: whether you controlled the destroyed artifact, as it last existed
    @Test
    fun wreckingMakesGoblinsOnlyForYourOwnArtifact() = scenario {
        alice.battlefield("Mountain", trinket)
        alice.hand(wrecking)
        alice.casts(wrecking, target = trinket)
        resolveAll()
        expect {
            trinket.zone shouldBe Zone.Graveyard
            alice.battlefield.names.count { it == "Phyrexian Goblin Token" } shouldBe 3
        }
    }

    @Test
    fun wreckingMakesNothingForAnOpponentsArtifact() = scenario {
        alice.battlefield("Mountain")
        bob.battlefield(trinket)
        alice.hand(wrecking)
        alice.casts(wrecking, target = trinket)
        resolveAll()
        expect {
            trinket.zone shouldBe Zone.Graveyard
            alice.battlefield.names shouldBe listOf("Mountain")
        }
    }

    // CR 205.1b: becoming an "artifact creature" keeps the prior card types and subtypes
    @Test
    fun becomingAnArtifactCreatureKeepsItsSubtypes() = scenario {
        alice.battlefield(brassworkMyr, "Plains", "Plains")
        alice.activates(brassworkMyr)
        resolveAll()
        expect {
            brassworkMyr.power shouldBe 4
            brassworkMyr.subtypes.toSet() shouldBe setOf(Subtype("Myr"), Subtype("Golem"))
        }
    }

    // CR 611.3a: hexproof with three or more oil counters, trample with two or fewer
    @Test
    fun quillbackKeywordsFollowItsOilCounters() = scenario {
        alice.battlefield(quillback, counters = listOf(oil to 3))
        expect {
            quillback hasKeyword Keyword.Hexproof.kind
            quillback lacksKeyword Keyword.Trample.kind
        }
    }

    // CR 603.2, 608.2c: the upkeep removal leaves it with two: trample, no hexproof; it stays
    @Test
    fun quillbackLosesACounterEachUpkeep() = scenario {
        alice.battlefield(quillback, counters = listOf(oil to 3))
        aliceUpkeep()
        resolveAll()
        expect {
            quillback.counters(oil) shouldBe 2
            quillback.zone shouldBe Zone.Battlefield
            quillback hasKeyword Keyword.Trample.kind
            quillback lacksKeyword Keyword.Hexproof.kind
        }
    }

    // CR 608.2c: its last counter removed, it is sacrificed
    @Test
    fun quillbackIsSacrificedWithoutCounters() = scenario {
        alice.battlefield(quillback, counters = listOf(oil to 1))
        aliceUpkeep()
        resolveAll()
        expect { quillback.zone shouldBe Zone.Graveyard }
    }

    // CR 615.5: the damage is prevented; CR 603.12: the reflexive trigger deals that much to another target chosen as it is put on the stack
    @Test
    fun retaliatorReflectsPreventedDamage() = scenario {
        alice.battlefield(retaliator)
        bob.battlefield("Mountain")
        bob.hand(spark)
        turn(number = 2, active = bob)
        bob.casts(spark, target = retaliator)
        resolveAll()
        val targets = decision<Decision.Targets>()
        assertEquals(alice.id, targets.player)
        targets("bob")
        resolveAll()
        expect {
            retaliator.view.obj?.damage shouldBe 0
            bob.life shouldBe 17
        }
    }

    // CR 608.2h: lesser than the sacrificed creature's mana value (2); CR 701.20: the legendary, the land and the mana value 3 card go to the bottom
    @Test
    fun morvathRevealsALesserCreature() = scenario {
        alice.battlefield(morvath, mauler)
        alice.library(chieftain, "Forest", brute, grub, "Swamp")
        bob.library("Island")
        advanceTo(TurnStep.End)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            mauler.zone shouldBe Zone.Graveyard
            grub.zone shouldBe Zone.Battlefield
            alice.library.names.first() shouldBe "Swamp"
            alice.library.names.drop(1).toSet() shouldBe setOf(chieftain, "Forest", brute)
        }
    }

    // CR 603.2d: a creature dying makes a triggered ability of alice's permanent trigger twice
    @Test
    fun skelvarDoublesDyingTriggers() = scenario {
        alice.battlefield(skelvar, chanter, "Mountain")
        bob.battlefield(mauler)
        alice.hand(spark)
        alice.casts(spark, target = mauler)
        resolveAll()
        expect {
            mauler.zone shouldBe Zone.Graveyard
            alice.life shouldBe 22
        }
    }

    // CR 614.1a: two poison counters become one; CR 101.2: no more this turn even after it leaves; CR 603.7c, 400.7: the chosen creature
    // put into the graveyard returns
    @Test
    fun ysoldeCapsPoisonAndReturnsTheChosenCreature() = scenario {
        alice.battlefield(ysolde, mauler)
        bob.battlefield("Swamp", "Swamp", "Mountain")
        bob.hand(drip, drip, spark)
        turn(number = 2, active = bob)
        bob.casts(drip, target = "alice")
        resolveAll()
        expect { alice.counters(CounterKind.Poison) shouldBe 1 }
        bob.passes()
        alice.activates(ysolde, target = mauler)
        resolveAll()
        bob.casts(spark, target = mauler)
        resolveAll()
        expect {
            ysolde.zone shouldBe Zone.Exile
            mauler.zone shouldBe Zone.Battlefield
        }
        bob.casts(drip, target = "alice")
        resolveAll()
        expect { alice.counters(CounterKind.Poison) shouldBe 1 }
    }
}
