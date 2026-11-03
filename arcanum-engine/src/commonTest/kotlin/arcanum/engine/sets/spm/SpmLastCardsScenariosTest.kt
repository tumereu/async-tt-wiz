package arcanum.engine.sets.spm

import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

/** The last Marvel's Spider-Man cards in play: a returned creature that must attack and is sacrificed after hitting a player, an
 * end-step draw after two creatures entered, a legend that connives on hitting a player, a Spider entering that gets a counter. */
class SpmLastCardsScenariosTest {
    private val ruinmaw = Cards.invent("Ruinmaw, Scarlet Frenzy", "{2}{B}{R}", "Legendary Creature — Symbiote Villain", """
        Trample
        When Ruinmaw enters, return target creature card with mana value 3 or less from your graveyard to the battlefield. It gains "This creature attacks each combat if able" and "When this creature deals combat damage to a player, sacrifice it."
        Mayhem {B}{R}
    """, "4/3")
    private val warden = Cards.invent("Thamesbridge Warden", "{3}{W}", "Legendary Creature — Spider Human Hero", """
        Web-slinging {2}{W} (You may cast this spell for {2}{W} if you also return a tapped creature you control to its owner's hand.)
        At the beginning of your end step, if two or more creatures entered the battlefield under your control this turn, you draw a card and gain 2 life.
    """, "3/4")
    private val halden = Cards.invent("Halden Grimsby", "{1}{U}", "Legendary Creature — Human Scientist Villain", """
        Halden Grimsby can't be blocked.
        Whenever Halden Grimsby deals combat damage to a player, he connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
    """, "1/1")
    private val odette = Cards.invent("Kindly Aunt Odette", "{W}", "Legendary Creature — Human Citizen",
        "Whenever another creature you control enters, you gain 1 life. If it's a Spider, put a +1/+1 counter on it.", "0/2")
    private val bear = spmBear
    private val ogre = spmOgre
    private val spider = spmSpider

    /** The returned creature must attack, and after hitting a player it is sacrificed. */
    // CR 508.1d, 113.10
    @Test
    fun returnedCreatureAttacksAndIsSacrificed() = scenario {
        alice.battlefield("Swamp", "Swamp", "Mountain", "Mountain")
        alice.graveyard(ogre)
        alice.hand(ruinmaw)
        alice.library(bear, bear)
        bob.library(bear, bear)
        alice.casts(ruinmaw)
        drive({ d: Decision -> d is Decision.Targets } to { targets(ogre) })
        expect { ogre.zone shouldBe Zone.Battlefield }
        expect { ogre hasMechanic { it is Mechanic.MustAttack } }
        advanceTo(TurnStep.DeclareAttackers, turn = 3)
        alice.attacks(ogre)
        untilStep(TurnStep.PostcombatMain)
        resolveAll()
        assertEquals(17, bob.life)
        expect { alice.graveyard has ogre }
    }

    /** Two creatures entered under your control this turn: a card and 2 life at the end step; one isn't enough. */
    // CR 603.4
    @Test
    fun twoCreaturesEnteredDraws() = scenario {
        alice.battlefield(warden, "Forest", "Forest", "Forest", "Forest")
        alice.hand(bear, bear)
        alice.library(ogre)
        alice.casts(bear, paying = listOf("Forest", "Forest"))
        resolveAll()
        alice.casts(bear, paying = listOf("Forest#2", "Forest#3"))
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        assertEquals(22, alice.life)
        expect { alice.hand.size shouldBe 1 }
    }

    @Test
    fun oneCreatureDoesNotDraw() = scenario {
        alice.battlefield(warden, "Forest", "Forest")
        alice.hand(bear)
        alice.library(ogre)
        alice.casts(bear)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        assertEquals(20, alice.life)
    }

    /** Unblockable; its combat damage to a player makes it connive (it, not the player). */
    // CR 701.50a
    @Test
    fun connivesOnDamage() = scenario {
        alice.battlefield(halden)
        alice.hand(ogre)
        alice.library(bear, bear, bear)
        bob.library(bear, bear)
        bob.battlefield(spider)
        turn(number = 3)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(halden)
        untilStep(TurnStep.PostcombatMain, { d: Decision -> d is Decision.ChooseObjects } to { alice.chooses(ogre) })
        expect {
            halden.counters(p1p1) shouldBe 1
            alice.graveyard has ogre
        }
        assertEquals(19, bob.life)
    }

    /** Each other creature entering gains 1 life; a Spider also gets a counter. */
    // CR 603.2
    @Test
    fun lifeAndSpiderCounter() = scenario {
        alice.battlefield(odette, "Forest", "Forest", "Forest", "Forest")
        alice.hand(bear, spider)
        alice.casts(bear, paying = listOf("Forest", "Forest"))
        resolveAll()
        alice.casts(spider, paying = listOf("Forest#2", "Forest#3"))
        resolveAll()
        assertEquals(22, alice.life)
        expect {
            spider.counters(p1p1) shouldBe 1
            bear.counters(p1p1) shouldBe 0
        }
    }
}
