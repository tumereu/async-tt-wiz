package arcanum.engine.sets.inr

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** INR chunk 03: flashback cards played from the graveyard (CR 702.34a: cast for the flashback cost, then exiled instead of going
 * anywhere else, CR 118.9, 608.2n). Invented names carry the oracle text of the real cards. */
class InrFlashbackScenariosTest {
    private val tail = "(You may cast this card from your graveyard for its flashback cost. Then exile it.)"

    private val lure = Cards.invent("Deadly Lure", "{B}", "Sorcery", """
        Target creature gains deathtouch until end of turn and must be blocked this turn if able.
        Flashback {G} $tail
    """)
    private val flood = Cards.invent("Memory Flood", "{2}{U}{U}", "Instant", """
        Look at the top X cards of your library, where X is the amount of mana spent to cast this spell. Put two of them into your hand and the rest on the bottom of your library in a random order.
        Flashback {5}{U}{U} $tail
    """)
    private val gale = Cards.invent("Seize the Gale", "{4}{R}", "Sorcery", """
        Create a red Elemental creature token with trample and "This token's power and toughness are each equal to the number of instant and sorcery cards in your graveyard plus the number of cards with flashback you own in exile."
        Flashback {6}{R} $tail
    """)
    private val duplicate = Cards.invent("Cackling Duplicate", "{1}{U}{U}", "Instant", """
        Create a token that's a copy of target creature you control.
        Flashback {5}{U}{U} $tail
    """)
    private val scrounging = Cards.invent("Faithless Scrounging", "{R}", "Sorcery", """
        Draw two cards, then discard two cards.
        Flashback {2}{R} $tail
    """)
    private val alchemy = Cards.invent("Illicit Alchemy", "{2}{U}", "Instant", """
        Look at the top four cards of your library. Put one of them into your hand and the rest into your graveyard.
        Flashback {6}{B} $tail
    """)
    private val repetition = Cards.invent("Galvanic Repetition", "{U}{R}", "Instant", """
        When you next cast an instant or sorcery spell this turn, copy that spell. You may choose new targets for the copy.
        Flashback {1}{U}{R} $tail
    """)
    private val spirits = Cards.invent("Lingering Spirits", "{2}{W}", "Sorcery", """
        Create two 1/1 white Spirit creature tokens with flying.
        Flashback {1}{B} $tail
    """)
    private val recovery = Cards.invent("Mystic Recovery", "{3}{U}", "Sorcery", """
        Return target instant or sorcery card from your graveyard to your hand.
        Flashback {2}{R} $tail
    """)
    private val retreat = Cards.invent("Silent Retreat", "{U}", "Sorcery", """
        Return target creature to its owner's hand.
        Flashback {4}{U} $tail
    """)
    private val thrice = Cards.invent("Think Thrice", "{1}{U}", "Instant", """
        Draw a card.
        Flashback {2}{U} $tail
    """)
    private val ox = Cards.invent("Tarnished Ox", "{2}", "Creature — Ox", "", "2/2")
    private val whisper = Cards.invent("Quiet Whisper", "{U}", "Instant", "You gain 1 life.")
    private val spark = Cards.invent("Ember Jolt", "{R}", "Instant", "Ember Jolt deals 1 damage to each opponent.")
    private val libs = (1..9).map { Cards.invent("Library Card $it", "{1}", "Sorcery", "") }

    private fun Scenario.count(name: String) = state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) } == name }

    // CR 702.34a (the flashback cost is {G}, not the {B} of the card), 509.1c (must be blocked), 702.2 (deathtouch), 611.2c (this turn)
    @Test
    fun lureFlashbackForGreenGivesDeathtouchAndForcesABlock() = scenario {
        alice.battlefield(ox, "Forest")
        bob.battlefield("Elvish Seer")
        alice.graveyard(lure)
        alice.casts(lure, target = ox)
        resolveAll()
        expect {
            ox hasKeyword Keyword.Deathtouch.kind
            lure.zone shouldBe Zone.Exile
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ox)
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks() }
        bob.blocks("Elvish Seer" to ox)
    }

    // CR 601.2h, 107.3: X is the mana spent, so the flashback cost of seven looks at seven cards; two go to hand, the other five to the bottom
    @Test
    fun floodFlashbackLooksAtSevenCardsAndKeepsTwo() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.graveyard(flood)
        alice.library(*libs.toTypedArray())
        alice.casts(flood)
        resolveAll()
        alice.chooses("Library Card 2", "Library Card 5")
        resolveAll()
        expect {
            alice.hand.names.toSet() shouldBe setOf("Library Card 2", "Library Card 5")
            alice.library.size shouldBe 7
            alice.library.names.take(2).toSet() shouldBe setOf("Library Card 8", "Library Card 9")
            flood.zone shouldBe Zone.Exile
        }
    }

    @Test
    fun floodFromHandLooksAtFourCards() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(flood)
        alice.library(*libs.toTypedArray())
        alice.casts(flood)
        resolveAll()
        alice.chooses("Library Card 1", "Library Card 4")
        resolveAll()
        expect {
            alice.hand.names.toSet() shouldBe setOf("Library Card 1", "Library Card 4")
            alice.library.size shouldBe 7
            flood.zone shouldBe Zone.Graveyard
        }
    }

    // CR 604.3, 111.4, 702.34a: instants and sorceries in your graveyard (not the opponent's) plus your cards with flashback in exile
    @Test
    fun galeTokenCountsYourSpellsAndYourFlashbackCardsInExile() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(gale)
        alice.graveyard(whisper, spark, ox)
        bob.graveyard(Cards.invent("Bob's Jolt", "{R}", "Instant", "Bob's Jolt deals 1 damage to each opponent."))
        alice.exile(recovery, ox)
        alice.casts(gale)
        resolveAll()
        expect {
            "Elemental Token".power shouldBe 4 // the resolved Seize the Gale is a sorcery card in the graveyard too (CR 608.2n), continuously counted
            "Elemental Token" hasKeyword Keyword.Trample.kind
        }
    }

    @Test
    fun galeFlashbackIsExiledAndThenCountsItself() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard(gale)
        alice.exile(recovery, retreat)
        alice.casts(gale)
        resolveAll()
        expect {
            gale.zone shouldBe Zone.Exile
            "Elemental Token".power shouldBe 3
        }
    }

    // CR 707.2, 702.34a
    @Test
    fun duplicateFlashbackCopiesAndIsExiled() = scenario {
        alice.battlefield(ox, "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.graveyard(duplicate)
        alice.casts(duplicate, target = ox)
        resolveAll()
        expect {
            count("Tarnished Ox") shouldBe 2
            duplicate.zone shouldBe Zone.Exile
        }
    }

    // CR 121.1, 701.9 (discard)
    @Test
    fun scroungingFlashbackDrawsTwoAndDiscardsTwo() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.graveyard(scrounging)
        alice.library(libs[0], libs[1], libs[2])
        alice.casts(scrounging)
        resolveAll()
        expect {
            alice.hand.size shouldBe 0
            alice.graveyard has "Library Card 1"
            scrounging.zone shouldBe Zone.Exile
        }
    }

    // CR 701.18 (look), 702.34a
    @Test
    fun alchemyFlashbackPutsOneInHandAndTheRestInTheGraveyard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(alchemy)
        alice.library(libs[0], libs[1], libs[2], libs[3], libs[4])
        alice.casts(alchemy)
        resolveAll()
        alice.chooses("Library Card 3")
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf("Library Card 3")
            alice.graveyard has "Library Card 1"
            alice.graveyard has "Library Card 4"
            alice.library.size shouldBe 1
            alchemy.zone shouldBe Zone.Exile
        }
    }

    // CR 603.7 (delayed trigger), 707.10 (copy of a spell), 702.34a: the copy happens for the next instant or sorcery cast this turn only
    @Test
    fun repetitionFlashbackCopiesTheNextSpell() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Mountain", "Mountain")
        alice.graveyard(repetition)
        alice.hand(whisper)
        alice.casts(repetition)
        resolveAll()
        alice.casts(whisper)
        resolveAll()
        expect {
            alice.life shouldBe 22
            repetition.zone shouldBe Zone.Exile
        }
    }

    // CR 111.1, 702.34a, 702.9 (flying): the white card is flashed back with {1}{B}
    @Test
    fun spiritsFlashbackForBlackMakesTwoFlyers() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard(spirits)
        alice.casts(spirits)
        resolveAll()
        expect {
            count("Spirit Token") shouldBe 2
            "Spirit Token" hasKeyword Keyword.Flying.kind
            spirits.zone shouldBe Zone.Exile
        }
    }

    // CR 701.18, 702.34a: a red flashback cost for a blue card
    @Test
    fun recoveryFlashbackReturnsAnInstant() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.graveyard(recovery, whisper)
        alice.casts(recovery, target = "Quiet Whisper")
        resolveAll()
        expect {
            alice.hand has "Quiet Whisper"
            recovery.zone shouldBe Zone.Exile
        }
    }

    // CR 701.18, 400.7 (a bounced creature is a new object)
    @Test
    fun retreatFlashbackBouncesAndIsExiled() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        bob.battlefield(ox)
        alice.graveyard(retreat)
        alice.casts(retreat, target = ox)
        resolveAll()
        expect {
            bob.hand has "Tarnished Ox"
            retreat.zone shouldBe Zone.Exile
        }
    }

    // CR 121.1, 702.34a
    @Test
    fun thriceFlashbackDrawsOneAndIsExiled() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.graveyard(thrice)
        alice.library(libs[0])
        alice.casts(thrice)
        resolveAll()
        expect {
            alice.hand has "Library Card 1"
            thrice.zone shouldBe Zone.Exile
        }
    }
}
