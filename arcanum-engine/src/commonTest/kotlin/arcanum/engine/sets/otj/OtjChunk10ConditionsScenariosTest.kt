package arcanum.engine.sets.otj

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** OTJ chunk 10: conditions on "it", crimes this turn, what was returned this way, "two or fewer other lands", "Until end of turn, …". */
class OtjChunk10ConditionsScenariosTest {
    private val crimeNote = "(Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)"
    private val toss = Cards.invent("Canyon Pebble Toss", "{R}", "Instant", "Canyon Pebble Toss deals 1 damage to any target.")
    private val brute = Cards.invent("Ridgeline Brute", "{1}{G}", "Creature — Ogre", "", "2/2")
    private val lookout = Cards.invent("Scrubland Lookout", "{1}{W}", "Creature — Human Scout", "", "1/4")
    private val pony = Cards.invent("Mesa Pony", "{1}{W}", "Creature — Horse Mount", "", "2/2")
    private val heist = Cards.invent("Borrowed Steed Heist", "{2}{R}", "Sorcery",
        "Borrowed Steed Heist has flash as long as you've committed a crime this turn. $crimeNote\n" +
            "Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn.")
    private val safecracker = Cards.invent("Quickdraw Safecracker", "{2}{U}", "Creature — Human Rogue",
        "Vigilance\nThis creature gets +2/+0 as long as you've committed a crime this turn. $crimeNote", "1/4")
    private val hurler = Cards.invent("Manacle Hurler", "{2}{U}", "Creature — Human Soldier",
        "Whenever you cast your second spell each turn, choose target creature an opponent controls. If it's tapped, put a stun counter on it. " +
            "Otherwise, tap it. (If a permanent with a stun counter would become untapped, remove one from it instead.)", "3/2")
    private val corral = Cards.invent("Pastoral Corral", null, "Land — Desert", """
        {T}: Add {C}.
        {T}: Add one mana of any color. Spend this mana only to cast a Mount spell.
        {3}, {T}: Look at the top card of your library. If it's a Mount card, you may reveal it and put it into your hand. If you don't put it into your hand, you may put it on the bottom of your library.
    """.trimIndent())
    private val sprite = Cards.invent("Tending Sprite", "{W}", "Creature — Faerie Rogue",
        "Flying\nWhen this creature enters, return up to one target non-Faerie, nonland permanent you control to its owner's hand. " +
            "If a permanent was returned this way, put a +1/+1 counter on this creature.", "1/1")
    private val thicket = Cards.invent("Mire Thicket", null, "Land", "This land enters tapped unless you control two or fewer other lands.\n{T}: Add {B} or {G}.")
    private val mirage = Cards.invent("Passing Mirage", "{1}{U}", "Instant",
        "Target creature you control gains hexproof until end of turn. Untap that creature. Until end of turn, it becomes a copy of up to one other target creature.")
    private val onrush = Cards.invent("Thundering Onrush", "{3}{G}{G}", "Sorcery",
        "Until end of turn, each creature you control gets +2/+2 and gains trample and \"This creature can't be blocked by more than one creature.\"")

    // CR 702.8a, 601.3d, 700.13: after a crime the sorcery can be cast on an opponent's turn
    @Test
    fun heistHasFlashAfterACrime() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(toss, heist)
        bob.battlefield(brute, tapped = true)
        turn(number = 2, active = bob, priority = alice)
        alice.castsWithoutCosts(toss, target = "bob")
        resolveAll()
        bob.passes()
        alice.casts(heist, target = brute)
        resolveAll()
        expect {
            brute.controller shouldBe alice.id
            brute.tapped shouldBe false
            brute hasKeyword Keyword.Haste.kind
        }
    }

    // CR 307.1: without a crime it's cast only as a sorcery
    @Test
    fun heistIsASorceryWithoutACrime() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(heist)
        bob.battlefield(brute)
        turn(number = 2, active = bob, priority = alice)
        assertFailsWith<IllegalArgumentException> { alice.casts(heist, target = brute) }
    }

    // CR 700.13: targeting an opponent this turn turns the bonus on
    @Test
    fun safecrackerGetsTwoAfterACrime() = scenario {
        alice.battlefield(safecracker)
        alice.hand(toss)
        expect { safecracker.power shouldBe 1 }
        alice.castsWithoutCosts(toss, target = "bob")
        resolveAll()
        expect { safecracker.power shouldBe 3 }
    }

    // CR 122.1d: a tapped target gets a stun counter; with three players the target may be any opponent's
    @Test
    fun hurlerStunsATappedCreature() = scenario(players = 3) {
        alice.battlefield(hurler)
        alice.hand(toss, toss)
        bob.battlefield(lookout)
        carol.battlefield(brute, tapped = true)
        alice.castsWithoutCosts(toss, target = "bob")
        resolveAll()
        alice.castsWithoutCosts(toss, target = "bob")
        playOut(brute)
        expect {
            brute.counters(CounterKind.Stun) shouldBe 1
            brute.tapped shouldBe true
            lookout.tapped shouldBe false
        }
    }

    // "Otherwise, tap it." (no stun counter on an untapped creature)
    @Test
    fun hurlerTapsAnUntappedCreature() = scenario {
        alice.battlefield(hurler)
        alice.hand(toss, toss)
        bob.battlefield(lookout)
        alice.castsWithoutCosts(toss, target = "bob")
        resolveAll()
        alice.castsWithoutCosts(toss, target = "bob")
        playOut(lookout)
        expect {
            lookout.tapped shouldBe true
            lookout.counters(CounterKind.Stun) shouldBe 0
        }
    }

    // CR 701.20a: a Mount card may be revealed and put into the hand
    @Test
    fun corralFindsAMount() = scenario {
        alice.battlefield(corral, "Plains", "Plains", "Plains")
        alice.library(pony, "Island")
        alice.activates(corral, ability = 2)
        playOut(may = true)
        expect {
            alice.hand has pony
            alice.library.names shouldBe listOf("Island")
        }
    }

    // CR 401.4: a card not put into the hand may go to the bottom
    @Test
    fun corralPutsAnythingElseOnTheBottom() = scenario {
        alice.battlefield(corral, "Plains", "Plains", "Plains")
        alice.library("Island", "Swamp")
        alice.activates(corral, ability = 2)
        playOut(may = true)
        expect { alice.library.names shouldBe listOf("Swamp", "Island") }
    }

    // CR 608.2c: the counter only if a permanent was returned
    @Test
    fun spriteGrowsOnlyWhenSomethingReturned() = scenario {
        alice.battlefield(brute)
        alice.hand(sprite)
        alice.castsWithoutCosts(sprite)
        playOut(brute)
        expect {
            alice.hand has brute
            sprite.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    @Test
    fun spriteWithNothingToReturnStaysSmall() = scenario {
        alice.hand(sprite)
        alice.castsWithoutCosts(sprite)
        playOut()
        expect { sprite.counters(CounterKind.PowerToughness(1, 1)) shouldBe 0 }
    }

    // CR 614.1c: untapped with two other lands, tapped with three
    @Test
    fun thicketUntappedWithTwoOtherLands() = scenario {
        alice.battlefield("Swamp", "Forest")
        alice.hand(thicket)
        alice.plays(thicket)
        expect { thicket.tapped shouldBe false }
    }

    @Test
    fun thicketTappedWithThreeOtherLands() = scenario {
        alice.battlefield("Swamp", "Forest", "Forest")
        alice.hand(thicket)
        alice.plays(thicket)
        expect { thicket.tapped shouldBe true }
    }

    // CR 707.2, 613.1a: the untapped creature becomes a copy of the other target until end of turn, keeping hexproof (layer 6)
    @Test
    fun mirageCopiesAnotherCreature() = scenario {
        alice.battlefield(brute, tapped = true)
        bob.battlefield(lookout)
        bob.library("Island")
        alice.hand(mirage)
        alice.castsWithoutCosts(mirage, targets = listOf(brute, lookout))
        resolveAll()
        expect {
            card(brute).tapped shouldBe false
            card(brute).power shouldBe 1
            card(brute).toughness shouldBe 4
            card(brute).view.characteristics!!.hasSubtype(Subtype("Scout")) shouldBe true
            card(brute) hasKeyword Keyword.Hexproof.kind
        }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { card(brute).power shouldBe 2 }
    }

    // CR 611.2c, 509.1b: each creature you control gets the bonus and can't be blocked by more than one creature
    @Test
    fun onrushStopsDoubleBlocks() = scenario {
        alice.battlefield(brute)
        bob.battlefield(lookout, pony)
        alice.hand(onrush)
        alice.castsWithoutCosts(onrush)
        resolveAll()
        expect {
            brute.power shouldBe 4
            brute hasKeyword Keyword.Trample.kind
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(brute)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(lookout to brute, pony to brute) }
        bob.blocks(lookout to brute)
    }
}
