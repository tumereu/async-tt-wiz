package arcanum.engine.sets.one

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Phyrexia: All Will Be One chunk 13 in play: "+X/+X where X" counted as the trigger resolves, the "Then if" power check after the bonus
 * (CR 608.2c, 613.4c), once-each-turn sacrifice pumps (CR 602.5b), -X/-0 on an opponent's creature with 3 players, equipped creatures only
 * (CR 301.5) and a creature that becomes the target of a fight spell (CR 701.14).
 */
class OneChunk13ApplyScenariosTest {
    private val oil = CounterKind.Named("oil")
    private val jarn = Cards.invent("Jarn Kodel, First Warden", "{R}{W}", "Legendary Creature — Human Rebel", """
        Trample
        Whenever Jarn Kodel attacks, it gets +X/+X until end of turn, where X is the number of equipped creatures you control. Then if Jarn Kodel's power is 4 or greater, draw a card.
    """, "2/2")
    private val blade = Cards.invent("Tin Blade", "{1}", "Artifact — Equipment", "Equipped creature gets +0/+0.\nEquip {1}")
    private val bear = Cards.invent("Stonefang Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val legionary = Cards.invent("Ruthless Legionary", "{2}{B}", "Artifact Creature — Phyrexian Warrior", """
        Sacrifice another artifact or creature: This creature gets +2/+2 until end of turn. Activate only once each turn.
    """, "2/2")
    private val fodder = Cards.invent("Scrap Rat", "{B}", "Creature — Rat", "", "1/1")
    private val prototype = Cards.invent("Fled Prototype", "{1}{U}", "Artifact Creature — Phyrexian Beast", """
        Whenever this creature attacks, target creature an opponent controls gets -X/-0 until end of turn, where X is the number of artifacts you control.
    """, "2/1")
    private val trinket = Cards.invent("Brass Trinket", "{1}", "Artifact", "")
    private val cackler = Cards.invent("Kuldor Cackler", "{2}{R}", "Creature — Phyrexian Hyena", """
        Trample
        Whenever this creature attacks, it gets +X/+0 until end of turn, where X is the number of permanents you control with oil counters on them.
    """, "2/3")
    private val rejoined = Cards.invent("Rebels Rejoined", "{1}{W}", "Instant", """
        Target creature gets +2/+2 until end of turn.
        Equipped creatures you control gain indestructible until end of turn.
    """)

    // CR 608.2c: with two equipped creatures Jarn is 4/4 and the draw happens
    @Test
    fun jarnDrawsWhenEquippedCreaturesMakeHimBigEnough() = scenario {
        alice.battlefield(jarn, bear, blade, Cards.invent("Tin Blade Two", "{1}", "Artifact — Equipment", "Equip {1}"))
        attach(blade, jarn)
        attach("Tin Blade Two", bear)
        alice.library("Forest", "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(jarn)
        resolveAll()
        expect {
            jarn.power shouldBe 4
            alice.hand.size shouldBe 1
        }
    }

    // CR 608.2c: one equipped creature (Jarn himself) makes him 3/3: no draw
    @Test
    fun jarnDoesNotDrawBelowFourPower() = scenario {
        alice.battlefield(jarn, bear, blade)
        attach(blade, jarn)
        alice.library("Forest", "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(jarn)
        resolveAll()
        expect {
            jarn.power shouldBe 3
            alice.hand.size shouldBe 0
        }
    }

    // CR 602.5b: the pump can be activated once each turn; the second try fails
    @Test
    fun legionarySacrificesOnceEachTurn() = scenario {
        alice.battlefield(legionary, fodder, Cards.invent("Scrap Rat Two", "{B}", "Creature — Rat", "", "1/1"))
        alice.activates(legionary, choosing = listOf(fodder))
        resolveAll()
        expect {
            legionary.power shouldBe 4
            fodder.zone shouldBe arcanum.api.Zone.Graveyard
        }
        assertFailsWith<IllegalArgumentException> { alice.activates(legionary, choosing = listOf("Scrap Rat Two")) }
    }

    // CR 603.2, 115.1, 3 players: X = 2 (the Prototype itself and my Trinket; the opponent's artifact is not counted); a creature of either opponent can be shrunk
    @Test
    fun prototypeShrinksAnOpponentsCreatureByMyArtifacts() = scenario(players = 3) {
        alice.battlefield(prototype, trinket)
        carol.battlefield(Cards.invent("Cragmaw Ogre", "{3}{R}", "Creature — Ogre", "", "4/4"))
        bob.battlefield(Cards.invent("Bob's Artifact", "{1}", "Artifact", ""))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(prototype)
        resolveAll()
        expect { "Cragmaw Ogre".power shouldBe 2 }
    }

    // CR 613.4c: only permanents with oil counters count, the Cackler itself included
    @Test
    fun cacklerCountsPermanentsWithOilCounters() = scenario {
        alice.battlefield(cackler, counters = listOf(oil to 1))
        alice.battlefield(trinket, counters = listOf(oil to 2))
        alice.battlefield(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(cackler)
        resolveAll()
        expect { cackler.power shouldBe 4 }
    }

    // CR 301.5: the equipped creature gains indestructible, the unequipped one does not; the target gets +2/+2
    @Test
    fun rejoinedGivesIndestructibleToEquippedOnly() = scenario {
        alice.battlefield(bear, jarn, blade, "Plains", "Plains")
        attach(blade, jarn)
        alice.hand(rejoined)
        alice.casts(rejoined, bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            jarn hasKeyword Keyword.Indestructible.kind
            bear lacksKeyword Keyword.Indestructible.kind
        }
    }
}
