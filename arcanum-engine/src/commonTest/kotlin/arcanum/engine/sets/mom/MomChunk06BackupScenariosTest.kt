package arcanum.engine.sets.mom

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fin.resolveAll
import arcanum.engine.sets.rtr.targetsIfAsked
import kotlin.test.Test
import kotlin.test.assertEquals

/** MOM chunk 06: backup (CR 702.165) granting a triggered ability to another creature, combat-damage triggers of "a player or battle", a
 * combat-damage look sized by the damage dealt, damage assigned by toughness, and typecycling on a backup card. */
class MomChunk06BackupScenariosTest {
    private val backup1 = "Backup 1 (When this creature enters, put a +1/+1 counter on target creature. If that's another creature, it gains the following abilities until end of turn.)"
    private val prelate = Cards.invent("Tombwalk Prelate", "{3}{B}{B}", "Creature — Human Warlock", """
        $backup1
        Deathtouch
        Whenever this creature deals combat damage to a player or battle, return target creature card from your graveyard to the battlefield.
    """.trimIndent(), "4/4")
    private val vanguard = Cards.invent("Wildtrail Vanguard", "{2}{G}{G}", "Creature — Human Warrior", """
        Backup 1 (When this creature enters, put a +1/+1 counter on target creature. If that's another creature, it gains the following abilities until end of turn.)
        Trample
        Whenever this creature deals combat damage to a player or battle, look at that many cards from the top of your library. You may reveal a creature or land card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
    """.trimIndent(), "4/3")
    private val wallkeeper = Cards.invent("Bulwark Wallkeeper", "{1}{G}", "Creature — Cat Citizen", """
        Backup 1 (When this creature enters, put a +1/+1 counter on target creature. If that's another creature, it gains the following ability until end of turn.)
        This creature assigns combat damage equal to its toughness rather than its power.
    """.trimIndent(), "0/2")
    private val yeti = Cards.invent("Avalanche Yeti", "{4}{R}{R}", "Creature — Yeti", """
        Mountaincycling {2} ({2}, Discard this card: Search your library for a Mountain card, reveal it, put it into your hand, then shuffle.)
        Backup 2 (When this creature enters, put two +1/+1 counters on target creature. If that's another creature, it gains the following ability until end of turn.)
        Trample
    """.trimIndent(), "4/2")
    private val squire = Cards.invent("Hollow Squire", "{1}{W}", "Creature — Human Knight", "", "2/2")
    private val fallen = Cards.invent("Fallen Footman", "{1}{B}", "Creature — Human Soldier", "", "2/2")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    // CR 702.165a, 613.1f: backup on another creature gives it the deathtouch and the trigger until end of turn; CR 510.2, 603.6a: its combat
    // damage returns a creature card; it keeps the counter after the turn
    @Test
    fun backupGrantsTheCombatTriggerToAnotherCreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", squire)
        alice.graveyard("Fallen Footman")
        alice.hand(prelate)
        alice.casts(prelate)
        resolveAll()
        targetsIfAsked("Hollow Squire")
        resolveAll()
        expect {
            squire.counters(p1p1) shouldBe 1
            squire hasKeyword Keyword.Deathtouch.kind
            prelate.counters(p1p1) shouldBe 0
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(squire)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        targetsIfAsked("Fallen Footman")
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.battlefield has fallen
        }
    }

    // CR 702.165a: the creature itself gets the counter, and the abilities it printed were its own all along
    @Test
    fun backupOnItselfPutsTheCounterOnIt() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard("Fallen Footman")
        alice.hand(prelate)
        alice.casts(prelate)
        resolveAll()
        targetsIfAsked("Tombwalk Prelate")
        resolveAll()
        expect { prelate.counters(p1p1) shouldBe 1 }
    }

    // CR 510.2, 701.20a: with 4 power the look is at four cards; the creature card goes to hand, the other three to the bottom
    @Test
    fun lookIsAsManyCardsAsTheDamageDealt() = scenario {
        alice.battlefield(vanguard)
        alice.library("Swamp", "Island", "Forest", fallen, "Plains", "Mountain")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(vanguard)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        alice.chooses("Fallen Footman")
        resolveAll()
        expect {
            bob.life shouldBe 16
            alice.hand has "Fallen Footman"
        }
        assertEquals(listOf("Plains", "Mountain"), alice.library.names.take(2))
        assertEquals(setOf("Swamp", "Island", "Forest"), alice.library.names.takeLast(3).toSet())
    }

    // CR 510.1c: a 0/2 deals its 2 toughness as combat damage
    @Test
    fun assignsDamageEqualToToughness() = scenario {
        alice.battlefield("Forest", "Forest", wallkeeper)
        alice.battlefield(squire)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wallkeeper)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    // CR 702.29a: the typecycling ability works from the hand
    @Test
    fun typecyclingFindsAMountain() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(yeti)
        alice.library("Island", "Mountain", "Forest")
        alice.activates(yeti)
        resolveAll()
        alice.chooses("Mountain")
        resolveAll()
        expect {
            alice.hand has "Mountain"
            alice.graveyard has yeti
        }
    }
}
