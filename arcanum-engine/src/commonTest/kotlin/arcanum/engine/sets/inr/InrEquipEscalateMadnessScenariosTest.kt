package arcanum.engine.sets.inr

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.mh2.playOut
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** INR chunk 05: an ability an Equipment gives that sacrifices the Equipment and has it deal the damage, a creature returning
 * transformed from a dies trigger, an Equipment transforming when its creature does, an attack trigger keeping the creature tapped
 * through its controller's next untap step, mana escalate with three modes and an opponent's creatures only, and a madness sorcery
 * cast on an opponent's turn. Invented names carry the oracle text of the real cards. */
class InrEquipEscalateMadnessScenariosTest {
    private val pole = Cards.invent("Kindled Lantern Pole", "{1}", "Artifact — Equipment", """
        Equipped creature can't be blocked by Vampires or Zombies.
        Equipped creature has "{T}, Sacrifice Kindled Lantern Pole: Kindled Lantern Pole deals 2 damage to any target."
        Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val scarecrow = Cards.inventFaces("Transform",
        face("Gleaning Scarecrow", "{3}", "Artifact Creature — Scarecrow",
            "When this creature dies, return it to the battlefield transformed under your control.", "2/2", null, null),
        face("Salvaged Sickle", null, "Artifact — Equipment", """
            Equipped creature gets +1/+1.
            As long as equipped creature is a Human, it has menace. (It can't be blocked except by two or more creatures.)
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """.trimIndent(), null, null, null))
    private val keepsake = Cards.inventFaces("Transform",
        face("Forgotten Keepsake", "{1}", "Artifact — Equipment", """
            Equipped creature gets +1/+1.
            When equipped creature transforms, transform this Equipment.
            Equip {1}
        """.trimIndent(), null, null, null),
        face("Gloommouth Edge", null, "Artifact — Equipment", "Equipped creature gets +3/+3 and has first strike.\nEquip {3}", null, null, null))
    private val tinker = Cards.inventFaces("Transform",
        face("Restless Tinker", "{1}{R}", "Creature — Human Werewolf", "{1}: Transform this creature.", "2/2", null, null),
        face("Unbound Tinker", null, "Creature — Werewolf", "", "4/4", null, "R"))
    private val splice = Cards.invent("Tanner's Splice", "{1}", "Artifact — Equipment", """
        Equipped creature gets +3/+3.
        Whenever equipped creature attacks, it doesn't untap during its controller's next untap step.
        Whenever this Equipment becomes unattached from a permanent, sacrifice that permanent.
        Equip {2}
    """)
    private val uprising = Cards.invent("Communal Uprising", "{1}{R}{R}", "Sorcery", """
        Escalate {1} (Pay this cost for each mode chosen beyond the first.)
        Choose one or more —
        • Target player discards all the cards in their hand, then draws that many cards.
        • Communal Uprising deals 4 damage to target creature.
        • Communal Uprising deals 3 damage to target opponent or planeswalker.
    """)
    private val compact = Cards.invent("Feral Compact", "{2}{R}", "Instant", """
        Escalate {1} (Pay this cost for each mode chosen beyond the first.)
        Choose one or more —
        • Creatures target player controls gain trample until end of turn.
        • Feral Compact deals 2 damage to target creature.
        • Feral Compact deals 1 damage to each creature target opponent controls.
    """)
    private val welcome = Cards.invent("Distiller's Welcome", "{4}{R}", "Sorcery", """
        Distiller's Welcome deals 4 damage to target creature.
        Madness {1}{R} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)
    """)
    private val hoarder = Cards.invent("Twitchy Hoarder", "{1}{R}", "Creature — Human", "Discard a card: This creature gets +1/+1 until end of turn.", "1/1")
    private val squire = Cards.invent("Wiry Squire", "{1}", "Creature — Human Soldier", "", "2/2")
    private val pawn = Cards.invent("Spare Pawn", "{1}", "Creature — Human", "", "1/1")
    private val oxen = Cards.invent("Hulking Oxen", "{4}", "Creature — Ox", "", "4/4")

    private fun Scenario.face(name: String): Int? = state.obj(card(name))?.face

    // CR 113.10, 602.2b: the creature's granted ability sacrifices the Equipment as a cost; the Equipment, as it last existed
    // (CR 608.2h), deals the damage
    @Test
    fun theGrantedAbilitySacrificesThePoleAndThePoleDealsTheDamage() = scenario {
        alice.battlefield(pole, squire)
        attach(pole, squire)
        bob.battlefield(pawn)
        alice.activates(squire, target = pawn)
        resolveAll()
        expect {
            alice.graveyard has pole
            bob.graveyard has pawn
            squire.tapped shouldBe true
        }
    }

    // CR 712.14a: the dies trigger returns the card with its back face up, an Equipment, under its controller's control
    @Test
    fun theScarecrowReturnsAsTheSickle() = scenario {
        alice.battlefield("Mountain", scarecrow)
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = scarecrow)
        resolveAll()
        expect {
            alice.battlefield has scarecrow
            face(scarecrow) shouldBe 1
            card(scarecrow).controller shouldBe alice.id
        }
    }

    // CR 701.27a: the creature transforms, so the Equipment's trigger transforms it too: 4/4 back face +3/+3
    @Test
    fun theKeepsakeTransformsWithItsCreature() = scenario {
        alice.battlefield("Mountain", keepsake, tinker)
        attach(keepsake, tinker)
        alice.activates(tinker)
        resolveAll()
        expect {
            face(tinker) shouldBe 1
            face(keepsake) shouldBe 1
            tinker.power shouldBe 7
            tinker.toughness shouldBe 7
        }
    }

    // CR 502.3, 508.1: it attacked, so it stays tapped through Alice's next untap step
    @Test
    fun theSplicedCreatureDoesntUntapNextTurn() = scenario {
        alice.battlefield(splice, squire, pawn)
        attach(splice, squire)
        alice.library("Mountain", "Mountain")
        bob.library("Mountain", "Mountain")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(squire, pawn)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect {
            squire.tapped shouldBe true
            pawn.tapped shouldBe false
        }
    }

    // CR 702.120a: three modes cost {1}{R}{R} plus {1} twice; the player discards two and draws two, the opponent takes 3
    @Test
    fun uprisingWithAllThreeModes() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(uprising)
        bob.hand("Plains", "Island")
        bob.library("Forest", "Swamp")
        bob.battlefield(oxen)
        alice.casts(uprising, modes = listOf(0, 1, 2), targets = listOf("bob", oxen, "carol"))
        // all five Mountains paid: {1}{R}{R} and {1} for each of the two extra modes
        assertEquals(0, state.battlefield.count { id -> state.obj(id)?.let { nameOf(it) == "Mountain" && !it.tapped } == true })
        resolveAll()
        expect {
            bob.graveyard has "Plains"
            bob.graveyard has "Island"
            bob.hand.size shouldBe 2
            bob.graveyard has oxen
            carol.life shouldBe 17
            alice.graveyard has uprising
        }
    }

    // CR 115.1, 702.120a: one mode, no escalate; only the targeted opponent's creatures are dealt damage
    @Test
    fun compactHurtsOnlyTheTargetedOpponentsCreatures() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain", squire)
        alice.hand(compact)
        bob.battlefield(pawn)
        carol.battlefield(hoarder)
        alice.casts(compact, modes = listOf(2), target = "bob")
        resolveAll()
        expect {
            bob.graveyard has pawn
            carol.battlefield has hoarder
            alice.battlefield has squire
        }
    }

    // CR 702.35a, 608.2g: discarded on Bob's turn, the sorcery is cast for its madness cost while the madness trigger resolves
    @Test
    fun madnessCastsTheSorceryOnAnOpponentsTurn() = scenario {
        alice.battlefield("Mountain", "Mountain", hoarder)
        alice.hand(welcome)
        bob.battlefield(oxen)
        turn(number = 2, active = bob, priority = alice)
        alice.activates(hoarder, choosing = listOf(welcome))
        playOut(oxen)
        expect {
            bob.graveyard has oxen
            alice.graveyard has welcome
        }
    }
}
