package arcanum.engine.sets.neo

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Chunk 4 of NEO: reconfigure (CR 702.151) and the "this creature or equipped creature" triggers of its Equipment. */
class NeoChunk04ReconfigureScenariosTest {
    private fun reconfigureLine(cost: String) =
        "Reconfigure $cost ($cost: Attach to target creature you control; or unattach from a creature. Reconfigure only as a sorcery. While attached, this isn't a creature.)"

    private val bear = Cards.invent("Neo Sash Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val leech = Cards.invent("Neo Sash Leech", "{1}{B}", "Creature — Leech", "Lifelink", "2/2")
    private val wall = Cards.invent("Neo Sash Wall", "{1}{W}", "Creature — Wall", "Defender", "0/4")

    private val octopus = Cards.invent("Filching Circuit Octopus", "{2}{U}", "Artifact Creature — Equipment Octopus",
        "Whenever this creature or equipped creature deals combat damage to a player, draw a card.\n" + reconfigureLine("{2}"), "2/2")
    private val mask = Cards.invent("Mask of the Akuma", "{1}{B}", "Artifact Creature — Equipment Demon", "Menace\n" +
        "Equipped creature has base power and toughness 5/5, has menace, and is a black Demon in addition to its other colors and types.\n" +
        reconfigureLine("{2}{B}{B}"), "3/1")
    private val centipede = Cards.invent("Whipchain Centipede", "{2}{B}", "Artifact Creature — Equipment Insect",
        "Whenever this creature or equipped creature attacks, it gets +2/+0 until end of turn.\n" + reconfigureLine("{2}"), "2/2")
    private val helm = Cards.invent("Troll-Visage Helm", "{1}{R}", "Artifact Creature — Equipment Ogre", "Equipped creature gets +2/+2.\n" +
        "Whenever this creature or equipped creature deals combat damage to a player, you may sacrifice it. If you do, discard your hand, then draw three cards.\n" +
        reconfigureLine("{3}"), "2/2")
    private val slingshot = Cards.invent("Primate Slingshot", "{R}", "Artifact Creature — Equipment Monkey", "Equipped creature gets +1/+1.\n" +
        "Whenever this creature or equipped creature becomes blocked, it deals 1 damage to defending player.\n" + reconfigureLine("{2}"), "1/1")
    private val shard = Cards.invent("The Dream Shard", "{1}{U}", "Legendary Artifact Creature — Equipment Jellyfish",
        "You may look at the top card of your library any time.\n" +
        "As long as The Dream Shard is attached to a creature, you may play lands and cast spells from the top of your library.\n" +
        reconfigureLine("{2}{U}"), "0/4")
    private val kirin = Cards.invent("Skysilver Kirin", "{2}{W}", "Artifact Creature — Equipment Kirin", "Flying\n" +
        "Equipped creature has flying and \"You can't lose the game and your opponents can't win the game.\"\n" + reconfigureLine("{5}"), "3/2")
    private val capacitor = Cards.invent("Hare Capacitor", "{R}", "Artifact Creature — Equipment Rabbit", "Haste\n" +
        "Equipped creature gets +1/+1 and has haste.\n" + reconfigureLine("{R}"), "1/1")
    private val victory = Cards.invent("Neo Sash Triumph", "{U}", "Instant", "You win the game.")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    /** Alice attacks Bob with [attacker], nobody blocks, and combat damage is dealt; its trigger waits on the Stack. */
    private fun Scenario.hitsUnblocked(attacker: String) {
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(attacker, defender = "bob")
        advanceTo(TurnStep.CombatDamage)
    }

    // CR 510.3a, 603.2: the Octopus as a creature triggers on its own combat damage
    @Test
    fun octopusDrawsWhenItHitsAsACreature() = scenario {
        alice.battlefield(octopus)
        alice.library("Island", "Plains")
        hitsUnblocked(octopus)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.hand has "Island"
        }
    }

    // CR 702.151b: attached, it isn't a creature; CR 510.3a: the equipped creature's combat damage triggers it
    @Test
    fun octopusDrawsWhenTheEquippedCreatureHits() = scenario {
        alice.battlefield(octopus, bear)
        alice.library("Island", "Plains")
        attach(octopus, bear)
        hitsUnblocked(bear)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.hand has "Island"
            octopus.types shouldBe listOf(CardType.Artifact)
        }
    }

    // CR 613.4b (base 5/5, layer 7b), 613.1d-e (black Demon in addition, layers 4 and 5), 702.111 (menace)
    @Test
    fun maskMakesTheEquippedCreatureABlackDemonWithBase55AndMenace() = scenario {
        alice.battlefield(mask, bear)
        attach(mask, bear)
        expect {
            bear.power shouldBe 5
            bear.toughness shouldBe 5
            bear hasKeyword Keyword.Menace.kind
            bear.colors.toSet() shouldBe setOf(Color.Green, Color.Black)
            bear.subtypes.toSet() shouldBe setOf(Subtype("Bear"), Subtype("Demon"))
            mask.types shouldBe listOf(CardType.Artifact)
        }
    }

    // CR 508.1m, 603.2: "it" is the creature that attacked, so the equipped creature gets +2/+0
    @Test
    fun centipedePumpsTheEquippedAttacker() = scenario {
        alice.battlefield(centipede, bear)
        attach(centipede, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear, defender = "bob")
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear.toughness shouldBe 2
        }
    }

    // CR 701.21a: "it" is the creature that dealt the damage; CR 704.5n, 702.151b: the Helm then falls off and is a creature again
    @Test
    fun helmSacrificesTheEquippedCreatureToRefillTheHand() = scenario {
        alice.battlefield(helm, bear)
        alice.hand("Forest", "Mountain")
        alice.library("Island", "Plains", "Swamp", "Mountain")
        attach(helm, bear)
        hitsUnblocked(bear)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        expect {
            bob.life shouldBe 16
            alice.graveyard has bear
            alice.graveyard has "Forest"
            alice.graveyard has "Mountain"
            alice.hand.names.toSet() shouldBe setOf("Island", "Plains", "Swamp")
            alice.battlefield has helm
            helm.types shouldBe listOf(CardType.Artifact, CardType.Creature)
        }
    }

    // CR 509.1h, 506.2: the blocked creature deals the damage (lifelink, CR 702.15b) to the player it attacked, not another opponent
    @Test
    fun slingshotDamagesTheDefendingPlayerFromTheBlockedCreature() = scenario(players = 3) {
        alice.battlefield(slingshot, leech)
        carol.battlefield(wall)
        attach(slingshot, leech)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(leech, defender = "carol")
        advanceTo(TurnStep.DeclareBlockers)
        carol.blocks(wall to leech)
        resolveAll()
        expect {
            carol.life shouldBe 19
            bob.life shouldBe 20
            alice.life shouldBe 21
        }
    }

    // CR 702.151b, 601.2a, 305.1: the top card is playable only while the Shard is attached to a creature
    @Test
    fun shardLetsTheTopLandBePlayedOnlyWhileAttached() = scenario {
        alice.battlefield(shard, bear)
        alice.library("Island", "Plains")
        assertFalse(Engine.options(state, alice.id).any { it is Input.PlayLand }, "unattached, the top card can't be played")
        attach(shard, bear)
        assertTrue(Engine.options(state, alice.id).any { it is Input.PlayLand }, "attached, the top land can be played")
        alice.plays("Island")
        expect { alice.battlefield has "Island" }
    }

    // CR 104.3b, 104.2b: the granted ability keeps the equipped creature's controller in the game
    @Test
    fun kirinKeepsTheEquippedCreaturesControllerInTheGame() = scenario {
        alice.battlefield(kirin, bear)
        attach(kirin, bear)
        alice.life = 0
        bob.battlefield("Island")
        bob.hand(victory)
        alice.passes()
        bob.casts(victory)
        resolveAll()
        expect {
            bear hasKeyword Keyword.Flying.kind
            state.player(alice.id).lost shouldBe false
            state.player(bob.id).lost shouldBe false
        }
    }

    // CR 702.151a: attach as a sorcery to a creature that just came under Alice's control (haste lets it attack), then unattach;
    // CR 702.151b: unattached, it is a creature again
    @Test
    fun capacitorReconfiguresOntoANewCreatureAndBack() = scenario {
        alice.battlefield(capacitor, "Mountain", "Mountain")
        alice.battlefield(bear, sick = true)
        alice.activates(capacitor, ability = 0, target = bear)
        resolveAll()
        expect {
            capacitor.types shouldBe listOf(CardType.Artifact)
            bear.power shouldBe 3
            bear hasKeyword Keyword.Haste.kind
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear, defender = "bob")
        advanceTo(TurnStep.PostcombatMain)
        alice.activates(capacitor, ability = 1)
        resolveAll()
        expect {
            bob.life shouldBe 17
            capacitor.types shouldBe listOf(CardType.Artifact, CardType.Creature)
            bear.power shouldBe 2
            bear lacksKeyword Keyword.Haste.kind
        }
    }
}
