package arcanum.engine.sets.mh3

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.option
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test

/**
 * The MH3 devoid cards of chunk 01, played: the invented names carry the oracle text of Abstruse Appropriation, Corrupted Shapeshifter,
 * Drowner of Truth, Wumpus Aberration, Thief of Existence, Voidpouncer and Depth Defiler (costs cut where they don't change play).
 */
class Mh3Chunk01DevoidScenariosTest {
    private val flats = Cards.invent("Hollowstone Flats", null, "Land", "{T}: Add {C}.")
    private val cub = Cards.invent("Mirewood Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val aurochs = Cards.invent("Mirewood Aurochs", "{2}{G}", "Creature — Ox", "", "2/4")
    private val idol = Cards.invent("Mirewood Idol", "{2}", "Artifact", "")
    private val cull = Cards.invent("Mirewood Cull", "{B}", "Instant", "Destroy target creature.")
    private val spawn = "Eldrazi Spawn Token"

    private val appropriation = Cards.invent("Hollow Appropriation", "{2}{W}{B}", "Instant", """
        Devoid (This card has no color.)
        Exile target nonland permanent. You may cast that card for as long as it remains exiled, and you may spend colorless mana as though it were mana of any color to cast that spell.
    """)
    private val voidling = Cards.invent("Shifting Voidling", "{3}{U}", "Creature — Eldrazi Shapeshifter", """
        Devoid (This card has no color.)
        As this creature enters, it becomes your choice of a 3/3 creature with flying, a 2/5 creature with vigilance, or a 0/12 creature with defender.
    """, "*/*")
    private val drowner = Cards.inventFaces("ModalDoubleFaced",
        face("Abyssal Drowner", "{1}{G/U}", "Creature — Eldrazi", """
            Devoid (This card has no color.)
            When you cast this spell, if {C} was spent to cast it, create two 0/1 colorless Eldrazi Spawn creature tokens with "Sacrifice this token: Add {C}."
        """.trimIndent(), "7/6", null, null),
        face("Drowned Thicket", null, "Land", "This land enters tapped.\n{T}: Add {G} or {U}.", null, null, null))
    private val wumpus = Cards.invent("Voidborn Wumpus", "{1}{G}", "Creature — Eldrazi Beast", """
        Devoid (This card has no color.)
        When you cast this spell, if {C} wasn't spent to cast it, target opponent may put a creature card from their hand onto the battlefield.
        Trample
    """, "6/6")
    private val thief = Cards.invent("Larcenous Voidling", "{1}{C}{G}", "Creature — Eldrazi", """
        Devoid (This card has no color.)
        When you cast this spell, exile up to one target noncreature, nonland permanent an opponent controls with mana value 4 or less. If you do, Larcenous Voidling gains "When this creature leaves the battlefield, target opponent draws a card."
    """, "3/4")
    private val leaper = Cards.invent("Void Leaper", "{1}{R}", "Creature — Eldrazi", """
        Devoid (This card has no color.)
        Kicker {2}{C} (You may pay an additional {2}{C} as you cast this spell.)
        If this creature was kicked, it enters with two +1/+1 counters and a trample counter on it and with haste.
    """, "3/1")
    private val defiler = Cards.invent("Abyss Defiler", "{3}{U}{U}", "Creature — Eldrazi", """
        Devoid (This card has no color.)
        Kicker {C} (You may pay an additional {C} as you cast this spell.)
        When you cast this spell, choose one. If it was kicked, choose both instead.
        • Return target creature to its owner's hand.
        • Target player draws two cards, then discards a card.
    """, "3/5")

    // CR 609.4b: colorless mana is spent as though it were green; CR 110.2: the caster controls the permanent
    @Test
    fun appropriationCastsTheExiledCardWithColorlessMana() = scenario {
        alice.battlefield("Plains", "Swamp", "Swamp", "Swamp", flats, flats)
        alice.hand(appropriation)
        bob.battlefield(cub)
        alice.casts(appropriation, target = cub, paying = listOf("Plains", "Swamp", "Swamp#1", "Swamp#2"))
        resolveAll()
        expect { bob.exile has cub }
        alice.casts(cub)
        resolveAll()
        expect {
            alice.battlefield has cub
            cub.controller shouldBe alice.id
        }
    }

    // CR 609.4b: only colorless mana is spent as though it were any colour, so white mana can't pay {G}
    @Test
    fun appropriationDoesNotLetColoredManaPayAnotherColor() = scenario {
        alice.battlefield("Plains", "Swamp", "Swamp", "Swamp", "Plains", "Plains")
        alice.hand(appropriation)
        bob.battlefield(cub)
        alice.casts(appropriation, target = cub, paying = listOf("Plains", "Swamp", "Swamp#1", "Swamp#2"))
        resolveAll()
        alice.casts(cub)
        expect { decision<Decision.ManaPayment>().suggestion shouldBe emptyList() }
    }

    // CR 614.12, 614.1c: it enters as the chosen form (P/T layer 7b, defender layer 6)
    @Test
    fun shiftingVoidlingEntersAsAWall() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(voidling)
        alice.casts(voidling)
        resolveAll()
        option("a 0/12")
        resolveAll()
        expect {
            voidling.power shouldBe 0
            voidling.toughness shouldBe 12
            voidling hasKeyword Keyword.Defender
            voidling lacksKeyword Keyword.Flying
        }
    }

    // CR 614.12
    @Test
    fun shiftingVoidlingEntersAsAFlier() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(voidling)
        alice.casts(voidling)
        resolveAll()
        option("a 3/3")
        resolveAll()
        expect {
            voidling.power shouldBe 3
            voidling.toughness shouldBe 3
            voidling hasKeyword Keyword.Flying
            voidling lacksKeyword Keyword.Defender
        }
    }

    // CR 106.1b, 603.4: colorless mana spent to cast it
    @Test
    fun drownerMakesSpawnWhenColorlessWasSpent() = scenario {
        alice.battlefield(flats, "Forest")
        alice.hand(drowner)
        alice.casts(drowner, paying = listOf(flats, "Forest"))
        resolveAll()
        expect {
            alice.battlefield has drowner
            alice.battlefield.names.count { it == spawn } shouldBe 2
        }
    }

    // CR 106.1b: green and blue mana are not colorless
    @Test
    fun drownerMakesNoSpawnWithColoredMana() = scenario {
        alice.battlefield("Island", "Forest")
        alice.hand(drowner)
        alice.casts(drowner)
        resolveAll()
        expect {
            alice.battlefield has drowner
            alice.battlefield lacks spawn
        }
    }

    // CR 110.2: the opponent who puts the creature onto the battlefield controls it; CR 115.1: only the targeted opponent
    @Test
    fun wumpusLetsTheTargetOpponentPutACreatureOntoTheBattlefield() = scenario(players = 3) {
        alice.battlefield("Forest", "Forest")
        alice.hand(wumpus)
        bob.hand(aurochs)
        carol.hand(cub)
        alice.casts(wumpus)
        resolveAll()
        alice.answers(Answer.Targets(listOf(target("bob"))))
        resolveAll()
        bob.chooses(aurochs)
        resolveAll()
        expect {
            bob.battlefield has aurochs
            aurochs.controller shouldBe bob.id
            carol.hand has cub
            alice.battlefield has wumpus
        }
    }

    // CR 603.4: with {C} spent, the ability doesn't trigger
    @Test
    fun wumpusDoesNothingWhenColorlessWasSpent() = scenario(players = 3) {
        alice.battlefield(flats, "Forest")
        alice.hand(wumpus)
        bob.hand(aurochs)
        alice.casts(wumpus, paying = listOf(flats, "Forest"))
        resolveAll()
        expect {
            bob.hand has aurochs
            alice.battlefield has wumpus
        }
    }

    // CR 400.7a: the ability the spell gained stays with the permanent; CR 603.10a: it triggers as the creature leaves
    @Test
    fun thiefGainsTheLeaveTriggerWhenItExiles() = scenario(players = 3) {
        alice.battlefield(flats, "Forest", "Forest", "Swamp")
        alice.hand(thief, cull)
        bob.battlefield(idol)
        bob.library("Island")
        carol.library("Island")
        alice.casts(thief, paying = listOf(flats, "Forest", "Forest#1"))
        resolveAll()
        alice.answers(Answer.Targets(listOf(target(idol))))
        resolveAll()
        expect {
            bob.exile has idol
            alice.battlefield has thief
        }
        alice.casts(cull, target = thief)
        resolveAll()
        alice.answers(Answer.Targets(listOf(target("carol"))))
        resolveAll()
        expect {
            alice.graveyard has thief
            carol.hand has "Island"
            bob.hand lacks "Island"
        }
    }

    // "If you do": nothing was exiled, so it gains nothing
    @Test
    fun thiefGainsNothingWhenItExilesNothing() = scenario(players = 3) {
        alice.battlefield(flats, "Forest", "Forest", "Swamp")
        alice.hand(thief, cull)
        bob.library("Island")
        carol.library("Island")
        alice.casts(thief, paying = listOf(flats, "Forest", "Forest#1"))
        resolveAll()
        alice.casts(cull, target = thief)
        resolveAll()
        expect {
            alice.graveyard has thief
            bob.hand lacks "Island"
            carol.hand lacks "Island"
        }
    }

    // CR 702.33d, 614.1c, 122.1b: kicked, it enters with the counters and haste
    @Test
    fun voidLeaperKickedEntersBigWithTrampleAndHaste() = scenario {
        alice.battlefield(flats, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(leaper)
        alice.casts(leaper, kicked = 1)
        resolveAll()
        expect {
            leaper.counters(CounterKind.PowerToughness(1, 1)) shouldBe 2
            leaper.power shouldBe 5
            leaper.toughness shouldBe 3
            leaper hasKeyword Keyword.Trample
            leaper hasKeyword Keyword.Haste
        }
    }

    // CR 702.33d
    @Test
    fun voidLeaperUnkickedIsPlain() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(leaper)
        alice.casts(leaper, kicked = 0)
        resolveAll()
        expect {
            leaper.power shouldBe 3
            leaper lacksKeyword Keyword.Trample
            leaper lacksKeyword Keyword.Haste
        }
    }

    // CR 700.2, 702.33d: kicked, both modes are chosen (audit: a false alarm; the engine doesn't even ask, both are the only choice)
    @Test
    fun abyssDefilerKickedDoesBoth() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", flats)
        alice.hand(defiler)
        bob.battlefield(cub)
        bob.library("Island", "Forest")
        alice.casts(defiler, kicked = 1)
        resolveAll()
        alice.answers(Answer.Targets(listOf(target("bob"))))
        resolveAll()
        bob.chooses("Forest")
        resolveAll()
        expect {
            bob.hand has cub
            bob.hand has "Island"
            bob.graveyard has "Forest"
            alice.battlefield has defiler
        }
    }

    // CR 700.2: unkicked, exactly one
    @Test
    fun abyssDefilerUnkickedChoosesOne() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(defiler)
        bob.battlefield(cub)
        alice.casts(defiler, kicked = 0)
        resolveAll()
        val d = decision<Decision.Modes>()
        expect {
            d.min shouldBe 1
            d.max shouldBe 1
        }
    }
}
