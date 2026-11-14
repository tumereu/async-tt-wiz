package arcanum.engine.sets.woe

import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** WOE chunk 06, Celebration (CR 207.2c): "two or more nonland permanents entered the battlefield under your control this turn". */
class WoeChunk06CelebrationScenariosTest {
    private val mice = Cards.invent("Larder Mice", "{1}{W}", "Creature — Mouse",
        "Celebration — This creature gets +0/+2 as long as two or more nonland permanents entered the battlefield under your control this turn.", "3/1")
    private val guest = Cards.invent("Gala Night Guest", "{1}{R}", "Creature — Human Peasant",
        "Celebration — This creature gets +1/+1 and has trample as long as two or more nonland permanents entered the battlefield under your control this turn.", "2/2")
    private val brannoc = Cards.invent("Brannoc, Masked Reveler", "{1}{R}{R}", "Legendary Creature — Human Noble", """
        Haste
        Celebration — As long as two or more nonland permanents entered the battlefield under your control this turn, Brannoc is a Dragon with base power and toughness 4/4, flying, and "{R}: Dragons you control get +1/+0 until end of turn." (He loses all other creature types.)
    """.trimIndent(), "3/3")
    private val warcoat = Cards.invent("Tailored Warcoat", "{1}{R}", "Artifact — Equipment", """
        Equipped creature gets +2/+0.
        Celebration — At the beginning of combat on your turn, if two or more nonland permanents entered the battlefield under your control this turn, attach this Equipment to up to one target creature you control.
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """.trimIndent())
    private val usher = Cards.invent("Gala Usher", "{W}", "Creature — Human", "", "1/1")
    private val banner = Cards.invent("Gala Banner", "{W}", "Artifact", "")
    private val streamer = Cards.invent("Gala Streamer", "{W}", "Artifact", "Sacrifice this artifact: You gain 1 life.")

    // CR 207.2c, 611.3a: a land entering doesn't count, so a land and one creature aren't enough
    @Test
    fun aLandAndOneCreatureAreNotACelebration() = scenario {
        alice.battlefield(mice, "Plains")
        alice.hand("Plains", usher)
        alice.plays("Plains")
        alice.casts(usher)
        resolveAll()
        expect { mice.toughness shouldBe 1 }
    }

    // CR 611.3a, 613.4c: two nonland permanents (a creature and an artifact) entered under Alice's control; Bob's own creature, whose
    // controller had none enter, doesn't celebrate
    @Test
    fun twoNonlandPermanentsCelebrateOnlyForTheirController() = scenario(players = 3) {
        alice.battlefield(mice, "Plains", "Plains")
        alice.hand(usher, banner)
        bob.battlefield(guest)
        alice.casts(usher)
        resolveAll()
        expect { mice.toughness shouldBe 1 }
        alice.casts(banner)
        resolveAll()
        expect {
            mice.toughness shouldBe 3
            guest.power shouldBe 2
            guest lacksKeyword Keyword.Trample.kind
        }
    }

    // CR 611.3a: the condition looks back at what entered this turn, so a permanent that entered and has since left still counts
    @Test
    fun aPermanentThatAlreadyLeftStillCounts() = scenario {
        alice.battlefield(mice, "Plains", "Plains")
        alice.hand(streamer, usher)
        alice.casts(streamer)
        resolveAll()
        alice.activates(streamer, ability = 0)
        resolveAll()
        expect { alice.graveyard has streamer }
        alice.casts(usher)
        resolveAll()
        expect { mice.toughness shouldBe 3 }
    }

    // CR 205.1b, 613.1d, 613.4b, 613.1f: while celebrating, Brannoc is a 4/4 Dragon (no other creature types) with flying and a pump ability
    @Test
    fun brannocBecomesAFlyingDragon() = scenario {
        alice.battlefield(brannoc, "Plains", "Plains", "Mountain")
        alice.hand(usher, banner)
        expect {
            brannoc.power shouldBe 3
            brannoc.subtypes shouldBe listOf(Subtype("Human"), Subtype("Noble"))
            brannoc lacksKeyword Keyword.Flying.kind
        }
        alice.casts(usher, paying = listOf("Plains"))
        resolveAll()
        alice.casts(banner, paying = listOf("Plains#1"))
        resolveAll()
        expect {
            brannoc.power shouldBe 4
            brannoc.toughness shouldBe 4
            brannoc.subtypes shouldBe listOf(Subtype("Dragon"))
            brannoc hasKeyword Keyword.Flying.kind
        }
        alice.activates(brannoc, ability = 0)
        resolveAll()
        expect {
            brannoc.power shouldBe 5
            usher.power shouldBe 1
        }
    }

    // CR 603.4, 301.5c: celebrating at the beginning of combat, the Equipment attaches itself to Alice's creature
    @Test
    fun warcoatAttachesItselfWhenCelebrating() = scenario {
        alice.battlefield(warcoat, "Plains", "Plains")
        alice.hand(usher, banner)
        alice.casts(usher)
        resolveAll()
        alice.casts(banner)
        resolveAll()
        advanceTo(TurnStep.BeginningOfCombat)
        playOut(usher)
        assertEquals(TargetRef.Object(card(usher)), state.objOrThrow(card(warcoat)).attachedTo)
        expect { usher.power shouldBe 3 }
    }

    // CR 603.4: with only one nonland permanent entered, the intervening "if" is false and nothing triggers
    @Test
    fun warcoatStaysUnattachedWithoutACelebration() = scenario {
        alice.battlefield(warcoat, "Plains")
        alice.hand(usher)
        alice.casts(usher)
        resolveAll()
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        assertEquals(null, state.objOrThrow(card(warcoat)).attachedTo)
        expect { usher.power shouldBe 1 }
    }
}
