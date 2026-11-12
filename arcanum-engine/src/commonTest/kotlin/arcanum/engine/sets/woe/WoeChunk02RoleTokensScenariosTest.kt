package arcanum.engine.sets.woe

import arcanum.api.Keyword
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.rtr.resolveAll
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * WOE chunk 02, Role tokens part 2 (CR 111.10j–r, 303.7a): the invented names carry the oracle text of Not Dead After All,
 * Return Triumphant, Twisted Sewer-Witch, Syr Armont, the Redeemer and Unassuming Sage.
 */
class WoeChunk02RoleTokensScenariosTest {
    private val hound = Cards.invent("Mossback Hound", "{1}{G}", "Creature — Dog", "", "2/2")
    private val rat = Cards.invent("Culvert Scurrier", "{B}", "Creature — Rat", "", "1/1")
    private val bobsRat = Cards.invent("Drainpipe Rat", "{B}", "Creature — Rat", "", "1/1")
    private val shepherd = Cards.invent("Hedgerow Shepherd", "{1}{W}", "Creature — Human", "", "2/2")
    private val jolt = Cards.invent("Hedgefire Jolt", "{R}", "Instant", "Hedgefire Jolt deals 3 damage to target creature.")
    /** Not a predefined Role: an Aura Role card, to enchant an opponent's creature. */
    private val shroud = Cards.invent("Hedgerow Shroud", "{W}", "Enchantment — Aura Role", "Enchant creature\nEnchanted creature gets +1/+1.")

    private val hex = Cards.invent("Second Wind Hex", "{B}", "Instant",
        "Until end of turn, target creature you control gains \"When this creature dies, return it to the battlefield tapped under its owner's control, then create a Wicked Role token attached to it.\" (Enchanted creature gets +1/+1. When this token is put into a graveyard, each opponent loses 1 life.)")
    private val homecoming = Cards.invent("Triumphal Homecoming", "{1}{W}", "Sorcery",
        "Return target creature card with mana value 3 or less from your graveyard to the battlefield. Create a Young Hero Role token attached to it. (Enchanted creature has \"Whenever this creature attacks, if its toughness is 3 or less, put a +1/+1 counter on it.\" If you put another Role on the creature later, put this one into the graveyard.)")
    private val crone = Cards.invent("Gutterwitch Crone", "{3}{B}{B}", "Creature — Human Warlock",
        "When this creature enters, create a 1/1 black Rat creature token with \"This creature can't block.\" Then for each Rat you control, create a Wicked Role token attached to that Rat. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1. When this token is put into a graveyard, each opponent loses 1 life.)",
        "3/4")
    private val orlith = Cards.invent("Dame Orlith, the Steadfast", "{3}{G}{W}", "Legendary Creature — Human Knight", """
        When Dame Orlith enters, create a Monster Role token attached to another target creature you control. (If you control another Role on it, put that one into the graveyard. Enchanted creature gets +1/+1 and has trample.)
        Enchanted creatures you control get +1/+1.
    """, "4/4")
    private val lorekeeper = Cards.invent("Humble Lorekeeper", "{1}{W}", "Creature — Human Peasant Wizard",
        "When this creature enters, you may pay {2}. If you do, create a Sorcerer Role token attached to it. (Enchanted creature gets +1/+1 and has \"Whenever this creature attacks, scry 1.\")",
        "2/2")

    // CR 603.6c, 400.7: the granted dies trigger returns the creature tapped as a new object and the Wicked Role goes on it (+1/+1)
    @Test
    fun secondWindHexReturnsTheCreatureWithAWickedRole() = scenario {
        alice.battlefield("Swamp", hound)
        alice.hand(hex)
        bob.battlefield("Mountain")
        bob.hand(jolt)
        alice.casts(hex, target = hound)
        resolveAll()
        alice.passes()
        bob.casts(jolt, target = hound)
        resolveAll()
        expect {
            hound.zone shouldBe Zone.Battlefield
            hound.tapped shouldBe true
            alice.battlefield has "Wicked"
            hound.power shouldBe 3
            hound.toughness shouldBe 3
        }
        assertEquals(TargetRef.Object(card(hound)), state.objOrThrow(card("Wicked")).attachedTo)
    }

    // CR 400.7, 111.10r: the Young Hero Role goes on the creature that returned, not the card that left the graveyard
    @Test
    fun triumphalHomecomingPutsTheRoleOnTheReturnedCreature() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.graveyard(hound)
        alice.hand(homecoming)
        alice.casts(homecoming, target = hound)
        resolveAll()
        expect {
            hound.zone shouldBe Zone.Battlefield
            alice.battlefield has "Young Hero"
        }
        assertEquals(TargetRef.Object(card(hound)), state.objOrThrow(card("Young Hero")).attachedTo)
    }

    // CR 111.10q, 608.2c: the new Rat and every other Rat Alice controls get a Wicked Role, Bob's Rat doesn't; CR 303.7a, 704.5z: the
    // older Role Alice controls on her Rat goes to the graveyard (it isn't a Wicked Role: no one loses life)
    @Test
    fun gutterwitchCroneGivesEachOfAlicesRatsAWickedRole() = scenario(players = 3) {
        alice.battlefield(rat, shroud, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        attach(shroud, rat)
        bob.battlefield(bobsRat)
        alice.hand(crone)
        alice.casts(crone)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Wicked" } shouldBe 2
            alice.graveyard has shroud
            rat.power shouldBe 2
            "Rat Token".power shouldBe 2
            bobsRat.power shouldBe 1
            alice.life shouldBe 20
            bob.life shouldBe 20
            carol.life shouldBe 20
        }
    }

    // CR 111.10k; CR 613.4c: "Enchanted creatures you control" counts only Alice's creatures with an Aura attached (CR 303.4)
    @Test
    fun dameOrlithBoostsAlicesEnchantedCreatures() = scenario {
        alice.battlefield(hound, shepherd, "Forest", "Forest", "Forest", "Plains", "Plains")
        bob.battlefield(bobsRat, shroud)
        attach(shroud, bobsRat)
        alice.hand(orlith)
        alice.casts(orlith)
        resolveAll()
        answer(Answer.Targets(listOf(target(hound))))
        resolveAll()
        expect {
            hound.power shouldBe 4
            hound.toughness shouldBe 4
            hound hasKeyword Keyword.Trample.kind
            shepherd.power shouldBe 2
            orlith.power shouldBe 4
            bobsRat.power shouldBe 2
        }
    }

    // CR 118.12, 603.5: paying {2} puts a Sorcerer Role on the Lorekeeper itself ("it" is the creature that entered)
    @Test
    fun humbleLorekeeperPaysForASorcererRoleOnItself() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(lorekeeper)
        alice.casts(lorekeeper, paying = listOf("Plains", "Plains#1"))
        resolveAll()
        may(true)
        answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        expect {
            alice.battlefield has "Sorcerer"
            lorekeeper.power shouldBe 3
            lorekeeper.toughness shouldBe 3
        }
    }

    // CR 118.12: declining to pay creates no Role
    @Test
    fun humbleLorekeeperDeclinedMakesNoRole() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(lorekeeper)
        alice.casts(lorekeeper, paying = listOf("Plains", "Plains#1"))
        resolveAll()
        may(false)
        resolveAll()
        expect {
            alice.battlefield lacks "Sorcerer"
            lorekeeper.power shouldBe 2
        }
    }
}
