package arcanum.engine.cube

import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.PredefinedTokens
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Chunk 56 in play: modal double-faced, Adventure, split and transforming cards: casting either face, playing a land
 * face, transforming. */
class Chunk56MultiFacedCardsScenariosTest {
    private val ice = CounterKind.Named("ice")
    private val back = CastWay(face = 1)

    private fun Scenario.face(name: String): Int = state.objOrThrow(card(name)).face

    // ---- Modal double-faced cards (CR 712)

    /** Skalda: casting a spell adds {R} that stays until end of turn. */
    @Test
    fun skaldaAddsLastingRed() = scenario {
        alice.battlefield("Skalda, God of Sagas", "Mountain")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = "bob")
        allPass()
        expect { alice.pool.size shouldBe 1 }
        // The step ends: the mana stays
        advanceTo(TurnStep.BeginningOfCombat)
        expect { alice.pool.size shouldBe 1 }
    }

    // CR 712.8: the front face is cast
    @Test
    fun skaldaCastFront() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand("Skalda, God of Sagas")
        alice.casts("Skalda, God of Sagas")
        allPass()
        expect {
            alice.battlefield has "Skalda, God of Sagas // Brennor, Horn of Plenty"
            card("Skalda, God of Sagas").types shouldBe listOf(CardType.Creature)
        }
    }

    /** Brennor, the back face: discard a card: exile the top two cards; you may play them this turn. */
    // CR 712.11
    @Test
    fun brennorCastBackAndUsed() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Skalda, God of Sagas", "Storm Spark")
        alice.library("Forest", "Cinder Bolt", "Island")
        alice.casts("Skalda, God of Sagas", way = back)
        allPass()
        expect {
            card("Brennor, Horn of Plenty").types shouldBe listOf(CardType.Artifact)
            alice.battlefield has "Skalda, God of Sagas // Brennor, Horn of Plenty"
        }
        assertEquals(1, face("Skalda, God of Sagas"))
        alice.activates("Brennor, Horn of Plenty", choosing = listOf("Storm Spark"))
        allPass()
        expect {
            alice.exile has "Forest"
            alice.exile has "Cinder Bolt"
        }
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }

    /** Summit Ascetic: prowess; entering, return an instant or sorcery card from your graveyard to your hand. */
    @Test
    fun summitAsceticReturnsASpell() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Summit Ascetic", "Storm Spark")
        alice.graveyard("Cinder Bolt")
        alice.casts("Summit Ascetic")
        allPass()
        // The only target, Cinder Bolt
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("Cinder Bolt"))))
        allPass()
        expect { alice.hand has "Cinder Bolt" }
        // CR 702.108a: prowess
        alice.casts("Storm Spark", target = "bob")
        allPass()
        expect { card("Summit Ascetic").power shouldBe 3 }
    }

    /** Hermit Peak, the land face: pay 3 life or it enters tapped. */
    // CR 712.11, 305.1
    @Test
    fun hermitPeakPaid() = scenario {
        alice.hand("Summit Ascetic")
        alice.plays("Summit Ascetic", face = 1)
        alice.answers(Answer.YesNo(true))
        expect {
            alice.life shouldBe 17
            card("Hermit Peak").tapped shouldBe false
        }
        alice.activates("Hermit Peak")
        expect { alice.pool.size shouldBe 1 }
    }

    @Test
    fun hermitPeakTapped() = scenario {
        alice.hand("Summit Ascetic")
        alice.plays("Summit Ascetic", face = 1)
        alice.answers(Answer.YesNo(false))
        expect {
            alice.life shouldBe 20
            card("Hermit Peak").tapped shouldBe true
        }
    }

    // ---- Adventure (CR 715)

    /** Petty Pinch: bounce a nonland permanent an opponent controls; then Brash Pilferer can be cast from exile. */
    // CR 715.4
    @Test
    fun brashPilfererAdventure() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand("Brash Pilferer")
        bob.battlefield("Elvish Seer")
        alice.casts("Brash Pilferer", way = back, target = "Elvish Seer")
        allPass()
        expect {
            bob.hand has "Elvish Seer"
            alice.exile has "Brash Pilferer // Petty Pinch"
        }
        // Not as an Adventure again
        assertFailsWith<IllegalArgumentException> { alice.casts("Brash Pilferer", way = back, target = "Elvish Seer") }
        alice.casts("Brash Pilferer")
        allPass()
        expect {
            alice.battlefield has "Brash Pilferer // Petty Pinch"
            card("Brash Pilferer") hasKeyword Keyword.Flying
        }
    }

    /** Brash Pilferer: flash, flying, blocks only fliers. */
    @Test
    fun brashPilfererBlocksOnlyFliers() = scenario {
        alice.battlefield("Brash Pilferer")
        bob.battlefield("Elvish Seer")
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks("Elvish Seer")
        allPass()
        expect { assertTrue(alice.id in state.combat!!.blockersDeclaredBy) }
        assertFailsWith<IllegalArgumentException> { alice.blocks("Brash Pilferer" to "Elvish Seer") }
    }

    // ---- Split cards (CR 709)

    /** Expanse: copy target instant or sorcery spell with mana value 4 or less. */
    @Test
    fun expanseCopiesASpell() = scenario {
        alice.battlefield("Island", "Island", "Mountain")
        alice.hand("Storm Spark", "Expanse // Eruption")
        alice.casts("Storm Spark", target = "bob")
        alice.casts("Expanse // Eruption", target = "Storm Spark")
        allPass()
        if (pending is Decision.May) alice.answers(Answer.YesNo(false))
        expect { stack.names shouldBe listOf("Storm Spark", "copy of Storm Spark") }
        allPass()
        allPass()
        expect { bob.life shouldBe 14 }
    }

    /** Eruption: X damage to any target; target player draws X. */
    @Test
    fun eruption() = scenario {
        alice.battlefield("Island", "Island", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Expanse // Eruption")
        alice.library("Forest", "Swamp")
        bob.battlefield("Seer of Harvestide")
        alice.casts("Expanse // Eruption", way = back, x = 2, targets = listOf("Seer of Harvestide", "alice"))
        allPass()
        expect {
            "Seer of Harvestide".zone shouldBe Zone.Battlefield
            card("Seer of Harvestide").view.obj!!.damage shouldBe 2
            alice.hand.size shouldBe 2
            alice.graveyard has "Expanse // Eruption"
        }
    }

    // ---- Transforming cards (CR 712, 701.28)

    /** Orrin: {T}: loot; with five or more cards in your graveyard he transforms into a planeswalker. */
    @Test
    fun orrinTransforms() = scenario {
        alice.battlefield("Orrin, Wayward Prodigy")
        alice.graveyard("Forest", "Forest", "Island", "Island")
        alice.library("Swamp", "Plains")
        alice.activates("Orrin, Wayward Prodigy")
        // Draws Swamp and discards it, the only card in hand
        allPass()
        expect {
            alice.graveyard.names shouldBe listOf("Forest", "Forest", "Island", "Island", "Swamp")
            card("Orrin, Mind Unbound").types shouldBe listOf(CardType.Planeswalker)
            "Orrin, Mind Unbound".loyalty shouldBe 5
            card("Orrin, Mind Unbound").counters(CounterKind.Loyalty) shouldBe 5
        }
    }

    @Test
    fun orrinLootsWithoutTransforming() = scenario {
        alice.battlefield("Orrin, Wayward Prodigy")
        alice.library("Swamp", "Plains")
        alice.hand("Forest")
        alice.activates("Orrin, Wayward Prodigy")
        allPass()
        alice.chooses("Forest")
        expect {
            card("Orrin, Wayward Prodigy").types shouldBe listOf(CardType.Creature)
            alice.hand.names shouldBe listOf("Swamp")
        }
    }

    /** Orrin, Mind Unbound: +1 shrinks a creature, -3 lets you cast an instant or sorcery from your graveyard, -9 an
     * emblem. */
    // CR 606.3
    @Test
    fun orrinMindUnboundPlusOne() = scenario {
        alice.battlefield("Orrin, Wayward Prodigy")
        bob.battlefield("Seer of Harvestide")
        transformed("Orrin, Wayward Prodigy", loyalty = 5)
        alice.activates("Orrin, Wayward Prodigy", target = "Seer of Harvestide")
        allPass()
        expect {
            "Seer of Harvestide".power shouldBe 0
            card("Orrin, Wayward Prodigy").counters(CounterKind.Loyalty) shouldBe 6
        }
        // Once each turn
        assertFailsWith<IllegalArgumentException> { alice.activates("Orrin, Wayward Prodigy", ability = 1, target = "Cinder Bolt") }
    }

    @Test
    fun orrinMindUnboundMinusThree() = scenario {
        alice.battlefield("Orrin, Wayward Prodigy", "Mountain")
        alice.graveyard("Storm Spark")
        transformed("Orrin, Wayward Prodigy", loyalty = 5)
        alice.activates("Orrin, Wayward Prodigy", ability = 1, target = "Storm Spark")
        allPass()
        alice.casts("Storm Spark", target = "bob")
        allPass()
        expect {
            bob.life shouldBe 17
            alice.exile has "Storm Spark"
            card("Orrin, Wayward Prodigy").counters(CounterKind.Loyalty) shouldBe 2
        }
    }

    @Test
    fun orrinMindUnboundEmblem() = scenario {
        alice.battlefield("Orrin, Wayward Prodigy", "Mountain")
        alice.hand("Storm Spark")
        bob.library("Forest", "Forest", "Forest", "Forest", "Forest", "Island")
        transformed("Orrin, Wayward Prodigy", loyalty = 9)
        alice.activates("Orrin, Wayward Prodigy", ability = 2)
        allPass()
        expect { alice.graveyard has "Orrin, Wayward Prodigy // Orrin, Mind Unbound" }
        alice.casts("Storm Spark", target = "bob")
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("bob"))))
        allPass()
        expect {
            bob.graveyard.size shouldBe 5
            bob.library.names shouldBe listOf("Island")
        }
    }

    /** Sets [name] up transformed with [loyalty] counters (setup only). */
    private fun Scenario.transformed(name: String, loyalty: Int) {
        val id = card(name)
        state.objects[id] = state.objOrThrow(id).copy(face = 1,
            counters = listOf(arcanum.engine.state.CounterEntry(CounterKind.Loyalty, loyalty, state.objOrThrow(id).timestamp)))
    }

    /** Haruka's Age of Honor: I, II: a creature gets +1/+1 for each artifact and enchantment; III: it returns
     * transformed into Likeness of Haruka. */
    // CR 714.3a, 714.2b
    @Test
    fun harukasAgeOfHonorChapterOne() = scenario {
        alice.battlefield("Plains", "Plains", "Sun Band", "Elvish Seer")
        alice.hand("Haruka's Age of Honor")
        alice.casts("Haruka's Age of Honor")
        allPass()
        // Sun Band and the Saga: +2/+2
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("Elvish Seer"))))
        allPass()
        expect {
            card("Haruka's Age of Honor").counters(CounterKind.Lore) shouldBe 1
            "Elvish Seer".power shouldBe 3
        }
    }

    // CR 714.3b: after the draw step, a lore counter; chapter III transforms it
    @Test
    fun harukasAgeOfHonorChapterThree() = scenario {
        alice.battlefield("Sun Band")
        alice.battlefield("Haruka's Age of Honor", counters = listOf(CounterKind.Lore to 2))
        turn(number = 1, step = TurnStep.Draw)
        allPass()
        expect { stack.names shouldBe listOf("ability of Haruka's Age of Honor") }
        allPass()
        expect {
            alice.battlefield has "Haruka's Age of Honor // Likeness of Haruka"
            card("Likeness of Haruka").types shouldBe listOf(CardType.Enchantment, CardType.Creature)
            // Sun Band and itself
            card("Likeness of Haruka").power shouldBe 2
        }
    }

    /** Shape Beneath the Floe: defender, enters with four ice counters; each instant or sorcery removes one, and with
     * none left it transforms into Risen Leviathan, which bounces all non-Horror creatures. */
    @Test
    fun shapeBeneathTheFloeEntersWithIce() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Shape Beneath the Floe")
        alice.casts("Shape Beneath the Floe")
        allPass()
        expect {
            card("Shape Beneath the Floe").counters(ice) shouldBe 4
            card("Shape Beneath the Floe") hasKeyword Keyword.Defender
        }
    }

    @Test
    fun shapeBeneathTheFloeTransforms() = scenario {
        alice.battlefield("Shape Beneath the Floe", counters = listOf(ice to 1))
        alice.battlefield("Mountain", "Elvish Seer")
        alice.hand("Storm Spark")
        bob.battlefield("Thicketwood Elves")
        alice.casts("Storm Spark", target = "bob")
        allPass()
        expect {
            card("Risen Leviathan").power shouldBe 7
            stack.names shouldBe listOf("Storm Spark", "ability of Risen Leviathan")
        }
        allPass()
        expect {
            alice.hand has "Elvish Seer"
            bob.hand has "Thicketwood Elves"
            alice.battlefield has "Shape Beneath the Floe // Risen Leviathan"
        }
    }

    /** Crimson Hexcaller: flying; your nontoken creatures dying make Blood; with five Blood tokens it transforms into
     * Duskwing Summoner, which turns a Blood token into a 2/2 flying, hasty Bat each combat. */
    @Test
    fun crimsonHexcallerMakesBlood() = scenario {
        alice.battlefield("Crimson Hexcaller", "Corpse Gnawer", "Elvish Seer")
        expect { card("Crimson Hexcaller") hasKeyword Keyword.Flying }
        alice.activates("Corpse Gnawer", choosing = listOf("Elvish Seer"))
        allPass()
        expect { alice.battlefield has "Blood Token" }
    }

    @Test
    fun crimsonHexcallerTransforms() = scenario {
        val blood = Cards.define("Blood Token", CardDefinition(Layout.Normal, listOf(PredefinedTokens.face("Blood")!!)))
        alice.battlefield("Crimson Hexcaller", "Corpse Gnawer", "Elvish Seer")
        alice.battlefield(blood, blood, blood, blood)
        state.battlefield.filter { state.objOrThrow(it).definition?.faces?.first()?.name == blood }.forEach {
            state.objects[it as arcanum.engine.state.ObjectId.Card] = state.objOrThrow(it).copy(token = true)
        }
        alice.activates("Corpse Gnawer", choosing = listOf("Elvish Seer"))
        expect { stack.names shouldBe listOf("ability of Corpse Gnawer", "ability of Crimson Hexcaller") }
        // The fifth Blood token: the transform trigger
        allPass()
        expect {
            alice.battlefield.names.count { it == blood } shouldBe 5
            stack.names shouldBe listOf("ability of Corpse Gnawer", "ability of Crimson Hexcaller")
        }
        allPass()
        expect { card("Duskwing Summoner").power shouldBe 3 }
        // Duskwing Summoner: at the beginning of combat, a Blood token becomes a 2/2 Bat with flying and haste
        advanceTo(TurnStep.BeginningOfCombat)
        alice.answers(Answer.Targets(listOf(target(blood))))
        allPass()
        expect {
            card(blood).power shouldBe 2
            card(blood) hasKeyword Keyword.Haste
            card(blood).types shouldBe listOf(CardType.Artifact, CardType.Creature)
        }
    }
}
