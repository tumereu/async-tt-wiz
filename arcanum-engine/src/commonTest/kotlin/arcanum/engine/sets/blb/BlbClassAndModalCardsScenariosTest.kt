package arcanum.engine.sets.blb

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.Blb.bear
import arcanum.engine.sets.blb.Blb.charm
import arcanum.engine.sets.blb.Blb.giant
import arcanum.engine.sets.blb.Blb.idol
import arcanum.engine.sets.blb.Blb.mouse
import arcanum.engine.sets.blb.Blb.ogre
import arcanum.engine.sets.blb.Blb.p1p1
import arcanum.engine.sets.blb.Blb.ponder
import arcanum.engine.sets.blb.Blb.rabbit
import arcanum.engine.sets.blb.Blb.shock
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Bloomburrow's Classes, Seasons and other modal cards in play (parser chunks 05–06), beyond the levels and pawprints played in [BlbClassesScenariosTest] and [BlbSeasonsScenariosTest]. */
class BlbClassAndModalCardsScenariosTest {
    private val masonsKnack = Cards.invent("Mason's Knack", "{1}{W}", "Enchantment — Class", """
        (Gain the next level as a sorcery to add its ability.)
        When this Class enters, create a 0/4 white Wall creature token with defender.
        {W}: Level 2
        Whenever one or more noncreature, nonland permanents you control enter, put a +1/+1 counter on target creature you control.
        {4}{W}: Level 3
        When this Class becomes level 3, return target noncreature, nonland permanent card from your graveyard to the battlefield.
    """)
    private val rumormongersKnack = Cards.invent("Rumormonger's Knack", "{1}{U}", "Enchantment — Class", """
        (Gain the next level as a sorcery to add its ability.)
        Whenever a creature you control enters, surveil 1.
        {1}{U}: Level 2
        Whenever you attack, target attacking creature with power 3 or less can't be blocked this turn.
        {3}{U}: Level 3
        Whenever a creature you control deals combat damage to a player, you may exile it, then return it to the battlefield under its owner's control.
    """)
    private val squallchasersKnack = Cards.invent("Squallchaser's Knack", "{U}", "Enchantment — Class", """
        (Gain the next level as a sorcery to add its ability.)
        When this Class enters, create a 1/1 blue and red Otter creature token with prowess.
        {3}{U}: Level 2
        When this Class becomes level 2, return target instant or sorcery card from your graveyard to your hand.
        {5}{U}: Level 3
        Whenever you cast an instant or sorcery spell, create a 1/1 blue and red Otter creature token with prowess.
    """)
    private val brigandsKnack = Cards.invent("Brigand's Knack", "{1}{B}", "Enchantment — Class", """
        (Gain the next level as a sorcery to add its ability.)
        When this Class enters, each opponent discards two cards unless they discard a nonland card.
        {B}: Level 2
        At the beginning of each opponent's upkeep, if that player has one or fewer cards in hand, they lose 2 life.
        {3}{B}: Level 3
        At the beginning of your draw step, draw an additional card for each opponent who has one or fewer cards in hand.
    """)
    private val gleanersKnack = Cards.invent("Gleaner's Knack", "{B}", "Enchantment — Class", """
        (Gain the next level as a sorcery to add its ability.)
        Whenever one or more creatures you control die, create a Food token. This ability triggers only once each turn.
        {1}{B}: Level 2
        Whenever you sacrifice a permanent, target player mills two cards.
        {2}{B}: Level 3
        At the beginning of your end step, you may sacrifice three other nonland permanents. If you do, return a creature card from your graveyard to the battlefield with a finality counter on it.
    """)
    private val paintersKnack = Cards.invent("Painter's Knack", "{1}{R}", "Enchantment — Class", """
        (Gain the next level as a sorcery to add its ability.)
        Whenever you cast a noncreature spell, you may discard a card. If you do, draw a card.
        {2}{R}: Level 2
        Noncreature spells you cast cost {1} less to cast.
        {2}{R}: Level 3
        If a source you control would deal noncombat damage to an opponent or a permanent an opponent controls, it deals that much damage plus 2 instead.
    """)
    private val armorersKnack = Cards.invent("Armorer's Knack", "{R}", "Enchantment — Class", """
        (Gain the next level as a sorcery to add its ability.)
        When this Class enters, create a colorless Equipment artifact token named Sword with "Equipped creature gets +1/+1" and equip {2}.
        {2}{R}: Level 2
        At the beginning of combat on your turn, attach target Equipment you control to up to one target creature you control.
        {3}{R}: Level 3
        During your turn, equipped creatures you control have double strike and haste.
    """)
    private val trackersKnack = Cards.invent("Tracker's Knack", "{1}{G}", "Enchantment — Class", """
        (Gain the next level as a sorcery to add its ability.)
        When this Class enters, target creature you control deals damage equal to its power to target creature you don't control.
        {1}{G}: Level 2
        Whenever you attack, target attacking creature gets +1/+0 and gains trample until end of turn.
        {3}{G}: Level 3
        At the beginning of your end step, if you control a creature with power 4 or greater, draw a card.
    """)
    private val timeOfTangles = Cards.invent("Time of Tangles", "{4}{U}{U}", "Sorcery", """
        Choose up to five {P} worth of modes. You may choose the same mode more than once.
        {P} — Draw a card.
        {P}{P} — Choose an artifact or creature you control. Create a token that's a copy of it.
        {P}{P}{P} — Return each nonland, nontoken permanent to its owner's hand.
    """)
    private val timeOfMourning = Cards.invent("Time of Mourning", "{3}{B}{B}", "Sorcery", """
        Choose up to five {P} worth of modes. You may choose the same mode more than once.
        {P} — Each player sacrifices a creature of their choice.
        {P}{P} — Draw a card for each creature that died under your control this turn.
        {P}{P}{P} — Each opponent loses X life, where X is the number of creature cards in your graveyard.
    """)
    private val timeOfTheDaring = Cards.invent("Time of the Daring", "{3}{R}{R}", "Sorcery", """
        Choose up to five {P} worth of modes. You may choose the same mode more than once.
        {P} — Create a tapped Treasure token.
        {P}{P} — Exile the top two cards of your library. Until the end of your next turn, you may play them.
        {P}{P}{P} — Until the end of your next turn, whenever you cast a spell, Time of the Daring deals 2 damage to up to one target creature.
    """)
    private val timeOfReaping = Cards.invent("Time of Reaping", "{4}{G}{G}", "Sorcery", """
        Choose up to five {P} worth of modes. You may choose the same mode more than once.
        {P} — Put a +1/+1 counter on a creature you control. It gains vigilance and trample until end of turn.
        {P}{P} — Choose artifact or enchantment. Destroy all permanents of the chosen type.
        {P}{P}{P} — Draw cards equal to the greatest power among creatures you control.
    """)
    private val leewardLurker = Cards.invent("Leeward Lurker", "{3}{B}", "Creature — Skunk Assassin", """
        Flash
        When this creature enters, choose one —
        • Target creature an opponent controls gets -1/-1 until end of turn.
        • Destroy target creature an opponent controls that was dealt damage this turn.
    """, "4/2")
    private val firstFrost = Cards.invent("First Frost", "{4}{B}", "Instant", """
        Choose one —
        • Exile target creature.
        • Target opponent exiles an enchantment they control.
    """)
    private val jasperBarrage = Cards.invent("Jasper Barrage", "{2}{R}", "Sorcery", """
        Choose one —
        • Jasper Barrage deals 4 damage to target creature. If that creature would die this turn, exile it instead.
        • Exile target artifact.
    """)
    private val quillbackGlutton = Cards.invent("Quillback Glutton", "{3}{G}{G}", "Creature — Elemental Wolverine", """
        When this creature enters, choose one —
        • Put a +1/+1 counter on target creature you control.
        • This creature fights target creature token.
        • Destroy target artifact or enchantment.
    """, "5/4")
    private val spellwhirl = Cards.invent("Spellwhirl", "{2}{U}{U}", "Instant", """
        Choose one —
        • Counter target spell.
        • Surveil 2, then draw two cards. (To surveil 2, look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
    """)

    // ---- Chunk 05: Classes (levels themselves: BlbClassesScenariosTest)

    // CR 716.2a: a level is gained as a sorcery, one at a time
    @Test
    fun masonsKnackRewardsNoncreaturePermanents() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(masonsKnack, idol)
        alice.casts(masonsKnack)
        resolveAll()
        expect {
            alice.battlefield has "Wall Token"
            "Wall Token".toughness shouldBe 4
            "Wall Token" hasKeyword Keyword.Defender
        }
        alice.activates(masonsKnack)
        resolveAll()
        alice.casts(idol)
        resolveAll()
        targetsIfAsked("Wall Token")
        resolveAll()
        expect { "Wall Token".counters(p1p1) shouldBe 1 }
    }

    // CR 716.2c: "when this Class becomes level 3"
    @Test
    fun masonsKnackReturnsAPermanentAtLevelThree() = scenario {
        alice.battlefield(masonsKnack, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.graveyard(charm)
        alice.activates(masonsKnack, ability = 0)
        resolveAll()
        alice.activates(masonsKnack, ability = 1)
        resolveAll()
        targetsIfAsked(charm)
        resolveAll()
        expect { alice.battlefield has charm }
    }

    @Test
    fun rumormongersKnackSurveilsOnACreature() = scenario {
        alice.battlefield(rumormongersKnack, "Forest", "Forest")
        alice.hand(bear)
        alice.library(ogre, rabbit)
        alice.casts(bear)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { alice.graveyard has ogre }
    }

    // CR 509.1b: an unblockable attacker
    @Test
    fun rumormongersKnackMakesAnAttackerUnblockable() = scenario {
        alice.battlefield(rumormongersKnack, "Island", "Island", bear)
        bob.battlefield(giant)
        alice.activates(rumormongersKnack)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        targetsIfAsked(bear)
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(giant to bear) }
    }

    @Test
    fun squallchasersKnackMakesAnOtterAndReturnsASpell() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(squallchasersKnack)
        alice.graveyard(ponder)
        alice.casts(squallchasersKnack)
        resolveAll()
        expect {
            "Otter Token" hasKeyword Keyword.Prowess
            "Otter Token".colors shouldBe listOf(Color.Blue, Color.Red)
        }
        alice.activates(squallchasersKnack)
        resolveAll()
        targetsIfAsked(ponder)
        resolveAll()
        expect { alice.hand has ponder }
    }

    // CR 118.12: "unless they discard a nonland card"
    @Test
    fun brigandsKnackMakesTheOpponentDiscard() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(brigandsKnack)
        bob.hand(ogre, "Forest", "Forest")
        alice.casts(brigandsKnack)
        resolveAll()
        if (pending is Decision.May) may(true)
        if (pending is Decision.ChooseOption) option("Discard a nonland")
        if (pending is Decision.ChooseObjects) bob.chooses(ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            bob.hand.names shouldBe listOf("Forest", "Forest")
        }
    }

    // CR 603.4: the upkeep trigger checks the hand size
    @Test
    fun brigandsKnackDrainsAnEmptyHandedOpponent() = scenario {
        alice.battlefield(brigandsKnack, "Swamp")
        bob.hand("Forest")
        alice.activates(brigandsKnack)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun gleanersKnackMakesFoodAndMills() = scenario {
        alice.battlefield(gleanersKnack, bear, "Swamp", "Swamp", "Mountain", "Plains", "Plains")
        alice.hand(shock)
        bob.library(ogre, rabbit, giant)
        alice.activates(gleanersKnack, paying = listOf("Swamp", "Swamp#1"))
        resolveAll()
        alice.casts(shock, target = bear)
        resolveAll()
        expect { alice.battlefield has "Food Token" }
        alice.activates("Food Token", paying = listOf("Plains", "Plains#1"))
        targets("bob")
        resolveAll()
        expect { bob.graveyard.names shouldBe listOf(ogre, rabbit) }
    }

    @Test
    fun paintersKnackRummagesOnANoncreatureSpell() = scenario {
        alice.battlefield(paintersKnack, "Island")
        alice.hand(ponder, ogre)
        alice.library(bear, rabbit)
        alice.casts(ponder)
        resolveAll()
        may(true)
        // the only card in hand is discarded without asking
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.hand.names shouldBe listOf(bear, rabbit)
        }
    }

    // CR 601.2f: cost reduction; CR 614.1a: the damage is increased
    @Test
    fun paintersKnackAddsTwoDamageAtLevelThree() = scenario {
        alice.battlefield(paintersKnack, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(shock)
        alice.activates(paintersKnack, ability = 0)
        resolveAll()
        alice.activates(paintersKnack, ability = 1)
        resolveAll()
        alice.casts(shock, target = "bob")
        // no card to discard: the "you may" is skipped
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    @Test
    fun armorersKnackMakesASwordAndAttachesIt() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", bear)
        alice.hand(armorersKnack)
        alice.casts(armorersKnack)
        resolveAll()
        expect { alice.battlefield has "Sword" }
        alice.activates(armorersKnack)
        resolveAll()
        advanceTo(TurnStep.BeginningOfCombat)
        // the only Equipment is chosen without asking
        if (target("Sword") in decision<Decision.Targets>().options) targets("Sword")
        targets(bear)
        resolveAll()
        expect { bear.power shouldBe 3 }
    }

    @Test
    fun trackersKnackBitesOnEntering() = scenario {
        alice.battlefield("Forest", "Forest", bear)
        alice.hand(trackersKnack)
        bob.battlefield(rabbit)
        alice.casts(trackersKnack)
        resolveAll()
        targetsIfAsked(bear, rabbit)
        resolveAll()
        expect { bob.graveyard has rabbit }
    }

    @Test
    fun trackersKnackPumpsAnAttackerAtLevelTwo() = scenario {
        alice.battlefield(trackersKnack, "Forest", "Forest", bear)
        alice.activates(trackersKnack)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Trample
        }
    }

    // ---- Chunk 06: Seasons and other modal cards (pawprints: BlbSeasonsScenariosTest)

    // CR 700.2i: pawprint modes, the same mode more than once
    @Test
    fun timeOfTanglesDrawsAndCopies() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", bear)
        alice.hand(timeOfTangles)
        alice.library(ogre, rabbit)
        alice.casts(timeOfTangles, modes = listOf(0, 0, 1))
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(ogre, rabbit)
            alice.battlefield.names.count { it == bear } shouldBe 2
        }
    }

    @Test
    fun timeOfTanglesReturnsNontokenPermanents() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", bear)
        alice.hand(timeOfTangles)
        bob.battlefield(ogre)
        alice.casts(timeOfTangles, modes = listOf(1, 2))
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect {
            alice.hand has bear
            bob.hand has ogre
            // the copy is a token and stays
            alice.battlefield has bear
        }
    }

    @Test
    fun timeOfMourningSacrificesAndDrains() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(timeOfMourning)
        alice.graveyard(ogre)
        bob.battlefield(rabbit)
        alice.casts(timeOfMourning, modes = listOf(0, 2))
        resolveAll()
        expect {
            alice.graveyard has bear
            bob.graveyard has rabbit
            bob.life shouldBe 18
        }
    }

    @Test
    fun timeOfMourningDrawsForTheDead() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(timeOfMourning)
        alice.library(ogre, rabbit, giant)
        bob.battlefield(rabbit)
        alice.casts(timeOfMourning, modes = listOf(0, 1, 1))
        resolveAll()
        expect { alice.hand.names shouldBe listOf(ogre, rabbit) }
    }

    @Test
    fun timeOfTheDaringMakesTreasuresAndExiles() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(timeOfTheDaring)
        alice.library("Forest", bear)
        alice.casts(timeOfTheDaring, modes = listOf(0, 0, 1))
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Treasure Token" } shouldBe 2
            card("Treasure Token").tapped shouldBe true
            alice.exile has bear
        }
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }

    // CR 603.7: a delayed "whenever you cast a spell" until the end of your next turn
    @Test
    fun timeOfTheDaringBurnsOnEachSpell() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(timeOfTheDaring, shock)
        bob.battlefield(rabbit)
        alice.casts(timeOfTheDaring, modes = listOf(2))
        resolveAll()
        alice.casts(shock, target = "bob")
        targetsIfAsked(rabbit)
        resolveAll()
        expect {
            bob.graveyard has rabbit
            bob.life shouldBe 18
        }
    }

    @Test
    fun timeOfReapingGrowsAndDraws() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", bear)
        alice.hand(timeOfReaping)
        alice.library(ogre, rabbit, giant, mouse, "Forest")
        alice.casts(timeOfReaping, modes = listOf(0, 0, 2))
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 2
            bear hasKeyword Keyword.Trample
            alice.hand.size shouldBe 4
        }
    }

    @Test
    fun timeOfReapingDestroysTheChosenType() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", charm)
        alice.hand(timeOfReaping)
        bob.battlefield(idol)
        alice.casts(timeOfReaping, modes = listOf(1))
        resolveAll()
        if (pending is Decision.ChooseCardType) answer(Answer.CardTypeChoice(CardType.Artifact))
        if (pending is Decision.ChooseOption) option("artifact")
        resolveAll()
        expect {
            bob.graveyard has idol
            alice.battlefield has charm
        }
    }

    // CR 700.2b: the mode is chosen as the trigger is put on the Stack
    @Test
    fun leewardLurkerFinishesADamagedCreature() = scenario {
        alice.battlefield("Mountain", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(shock, leewardLurker)
        bob.battlefield(ogre)
        alice.casts(shock, target = ogre)
        resolveAll()
        alice.casts(leewardLurker)
        resolveAll()
        modes(1)
        targetsIfAsked(ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun firstFrostMakesTheOpponentExileAnEnchantment() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(firstFrost)
        bob.battlefield(charm, bear)
        alice.casts(firstFrost, modes = listOf(1), target = "bob")
        resolveAll()
        expect {
            bob.exile has charm
            bob.battlefield has bear
        }
    }

    // CR 614.1a: exiled instead of dying
    @Test
    fun jasperBarrageExilesTheCreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(jasperBarrage)
        bob.battlefield(ogre)
        alice.casts(jasperBarrage, modes = listOf(0), target = ogre)
        resolveAll()
        expect { bob.exile has ogre }
    }

    @Test
    fun quillbackGluttonDestroysAnArtifact() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(quillbackGlutton)
        bob.battlefield(idol)
        alice.casts(quillbackGlutton)
        resolveAll()
        modes(2)
        targetsIfAsked(idol)
        resolveAll()
        expect { bob.graveyard has idol }
    }

    @Test
    fun quillbackGluttonGivesACounter() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", bear)
        alice.hand(quillbackGlutton)
        alice.casts(quillbackGlutton)
        resolveAll()
        modes(0)
        targets(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun spellwhirlCountersASpell() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(ogre)
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(spellwhirl)
        turn(active = bob)
        bob.casts(ogre)
        bob.passes()
        alice.casts(spellwhirl, modes = listOf(0), target = ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun spellwhirlSurveilsAndDraws() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(spellwhirl)
        alice.library(ogre, rabbit, bear, mouse)
        alice.casts(spellwhirl, modes = listOf(1))
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.hand.names shouldBe listOf(rabbit, bear)
        }
    }
}
