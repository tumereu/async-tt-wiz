package arcanum.engine.cube

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test

/** The cube's chunk 30 (enters: draw and life) played in the engine. */
class Chunk30EntersDrawAndLifeScenariosTest {
    /** Casts [name], resolves it and its enters trigger (all players pass twice). */
    private fun Scenario.castAndResolve(name: String) {
        alice.casts(name)
        allPass()
        expect { stack.names shouldBe listOf("ability of $name") }
        allPass()
    }

    /** Flying, deathtouch; enters: draw a card. */
    @Test
    fun venomousOwlet() = scenario {
        alice.battlefield("Island", "Swamp")
        alice.hand("Venomous Owlet")
        alice.library("Forest")

        castAndResolve("Venomous Owlet")

        expect {
            alice.hand.names shouldBe listOf("Forest")
            "Venomous Owlet" hasKeyword Keyword.Flying
            "Venomous Owlet" hasKeyword Keyword.Deathtouch
            "Venomous Owlet".types shouldBe listOf(CardType.Artifact, CardType.Creature)
        }
    }

    /** Enters: draw a card. */
    @Test
    fun elvishSeerling() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand("Elvish Seerling")
        alice.library("Island")

        castAndResolve("Elvish Seerling")

        expect { alice.hand.names shouldBe listOf("Island") }
    }

    /** An enchantment creature; enters: draw a card. */
    @Test
    fun loyalHearthhound() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand("Loyal Hearthhound")
        alice.library("Island")

        castAndResolve("Loyal Hearthhound")

        expect {
            alice.hand.names shouldBe listOf("Island")
            "Loyal Hearthhound".types shouldBe listOf(CardType.Enchantment, CardType.Creature)
        }
    }

    /** Defender; enters: draw a card. */
    @Test
    fun wallOfPortents() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand("Wall of Portents")
        alice.library("Island")

        castAndResolve("Wall of Portents")

        expect {
            alice.hand.names shouldBe listOf("Island")
            "Wall of Portents" hasKeyword Keyword.Defender
        }
    }

    /** Flash, flying; enters: draw a card; deathtouch with three other snow permanents. */
    @Test
    fun frostScaleSerpent() = scenario {
        alice.battlefield("Forest", "Island", "Rimefrost Druid", "Rimefrost Druid")
        alice.hand("Frost-Scale Serpent", "Rimefrost Druid")
        alice.library("Swamp")
        turn(number = 2, active = bob, priority = alice)

        // CR 702.8a: flash: cast on Bob's turn
        castAndResolve("Frost-Scale Serpent")
        expect {
            alice.hand.names shouldBe listOf("Rimefrost Druid", "Swamp")
            "Frost-Scale Serpent" hasKeyword Keyword.Flying
            "Frost-Scale Serpent" lacksKeyword Keyword.Deathtouch
        }
    }

    /** With three other snow permanents it has deathtouch. */
    @Test
    fun frostScaleSerpentWithThreeSnowPermanents() = scenario {
        alice.battlefield("Frost-Scale Serpent", "Rimefrost Druid", "Rimefrost Druid", "Rimefrost Druid")
        expect { "Frost-Scale Serpent" hasKeyword Keyword.Deathtouch }
    }

    /** Affinity for artifacts; flying; enters: draw two cards. */
    @Test
    fun cogitatingSentry() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Tinkered Mite", "Tinkered Mite", "Tinkered Mite")
        alice.hand("Cogitating Sentry")
        alice.library("Forest", "Swamp")

        // CR 702.41a: {6}{U} costs {3}{U} with three artifacts
        castAndResolve("Cogitating Sentry")

        expect {
            (0..3).all { card("Island", it).tapped } shouldBe true
            alice.hand.names shouldBe listOf("Forest", "Swamp")
            "Cogitating Sentry" hasKeyword Keyword.Flying
        }
    }

    /** Flash; enters: draw a card for each creature that died under your control this turn. */
    @Test
    fun gravecallerBanneret() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Elvish Seer", "Tinkered Mite")
        alice.hand("Gravecaller Banneret", "Storm Spark", "Storm Spark")
        alice.library("Forest", "Island", "Plains")

        alice.castsWithoutCosts("Storm Spark", target = "Elvish Seer")
        resolve()
        alice.castsWithoutCosts("Storm Spark", target = "Tinkered Mite")
        resolve()
        castAndResolve("Gravecaller Banneret")

        expect { alice.hand.names shouldBe listOf("Forest", "Island") }
    }

    /** Flying; enters: scry 2. */
    @Test
    fun faerieAugur() = scenario {
        alice.battlefield("Island")
        alice.hand("Faerie Augur")
        alice.library("Forest", "Island", "Swamp")

        castAndResolve("Faerie Augur")
        alice.chooses("Forest")

        expect {
            alice.library.names shouldBe listOf("Island", "Swamp", "Forest")
            "Faerie Augur" hasKeyword Keyword.Flying
        }
    }

    /** Enters: surveil 2; in the graveyard at your upkeep you may pay 1 life to return it to your hand. */
    @Test
    fun deathlessSavant() = scenario {
        alice.battlefield("Island", "Swamp", "Swamp")
        alice.hand("Deathless Savant", "Storm Spark")
        alice.library("Forest", "Island", "Swamp")
        bob.library("Plains", "Plains")

        castAndResolve("Deathless Savant")
        alice.chooses("Forest")
        expect {
            alice.graveyard has "Forest"
            alice.library.names shouldBe listOf("Island", "Swamp")
        }
        alice.castsWithoutCosts("Storm Spark", target = "Deathless Savant")
        resolve()
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect { stack.names shouldBe listOf("ability of Deathless Savant") }
        allPass()
        answer(Answer.YesNo(true))

        expect {
            alice.hand has "Deathless Savant"
            alice.life shouldBe 19
        }
    }

    /** Enters: investigate; the Clue draws a card for {2}. */
    @Test
    fun hollowellInvestigator() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand("Hollowell Investigator")
        alice.library("Forest")

        castAndResolve("Hollowell Investigator")
        expect { card("Clue Token").types shouldBe listOf(CardType.Artifact) }
        alice.activates("Clue Token")
        allPass()

        expect {
            alice.hand.names shouldBe listOf("Forest")
            alice.battlefield lacks "Clue Token"
        }
    }

    /** Enters: you may discard a card to draw one; unearth {1}{R}. */
    @Test
    fun junkyardCur() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Junkyard Cur", "Forest", "Island")
        alice.library("Swamp", "Plains")

        castAndResolve("Junkyard Cur")
        answer(Answer.YesNo(true))
        alice.chooses("Forest")
        expect {
            alice.graveyard has "Forest"
            alice.hand.names shouldBe listOf("Island", "Swamp")
        }
    }

    /** Unearth {1}{R}: back with haste, exiled at the beginning of the next end step. */
    @Test
    fun junkyardCurUnearth() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.graveyard("Junkyard Cur")
        alice.hand("Forest")
        alice.library("Swamp")

        alice.activates("Junkyard Cur")
        allPass()
        expect {
            alice.battlefield has "Junkyard Cur"
            "Junkyard Cur" hasKeyword Keyword.Haste
            stack.names shouldBe listOf("ability of Junkyard Cur")
        }
        allPass()
        answer(Answer.YesNo(false))
        advanceTo(TurnStep.End)
        allPass()

        expect { alice.exile has "Junkyard Cur" }
    }

    /** Enters: discard your hand, then draw three; escape {R}{R} and eight other cards, with a +1/+1 counter. */
    @Test
    fun bullOfThePyre() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand("Bull of the Pyre", "Forest", "Island")
        alice.library("Swamp", "Plains", "Swamp", "Plains")

        castAndResolve("Bull of the Pyre")

        expect {
            alice.graveyard.names.toSet() shouldBe setOf("Forest", "Island")
            alice.hand.names shouldBe listOf("Swamp", "Plains", "Swamp")
        }
    }

    /** Escaped, it enters with a +1/+1 counter (CR 702.138c). */
    @Test
    fun bullOfThePyreEscapes() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.graveyard("Bull of the Pyre", "Forest", "Island", "Swamp", "Plains", "Storm Spark", "Forest", "Island", "Swamp")
        alice.library("Plains", "Plains", "Plains")

        alice.casts("Bull of the Pyre")
        allPass()
        expect {
            "Bull of the Pyre".counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
            "Bull of the Pyre".power shouldBe 5
            alice.exile.size shouldBe 8
        }
        allPass()

        expect { alice.hand.size shouldBe 3 }
    }

    /** Affinity, flying; enters: each opponent discards; for each who can't, you draw. */
    @Test
    fun rebuiltRatling() = scenario {
        alice.battlefield("Swamp", "Swamp", "Tinkered Mite", "Tinkered Mite")
        alice.hand("Rebuilt Ratling")
        alice.library("Forest")
        bob.hand("Island")

        castAndResolve("Rebuilt Ratling")

        expect {
            bob.graveyard has "Island"
            alice.hand.isEmpty()
            "Rebuilt Ratling" hasKeyword Keyword.Flying
        }
    }

    /** An opponent with an empty hand can't discard: you draw. */
    @Test
    fun rebuiltRatlingDrawsWhenTheyCant() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand("Rebuilt Ratling")
        alice.library("Forest")

        castAndResolve("Rebuilt Ratling")

        expect { alice.hand.names shouldBe listOf("Forest") }
    }

    /** Enters: each player discards a card; unearth {1}{B}. */
    @Test
    fun festeringVermin() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand("Festering Vermin", "Forest", "Island")
        bob.hand("Plains")

        castAndResolve("Festering Vermin")
        alice.chooses("Island")

        expect {
            alice.graveyard has "Island"
            bob.graveyard has "Plains"
            alice.hand.names shouldBe listOf("Forest")
        }
    }

    /** Unearth {1}{B}: back with haste, and its enters trigger again. */
    @Test
    fun festeringVerminUnearth() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard("Festering Vermin")
        bob.hand("Plains")

        alice.activates("Festering Vermin")
        allPass()
        allPass()

        expect {
            "Festering Vermin" hasKeyword Keyword.Haste
            bob.graveyard has "Plains"
        }
    }

    /** Enters: gain 2 life; persist returns it once with a -1/-1 counter (and it gains 2 again). */
    @Test
    fun larderOuphes() = scenario {
        alice.battlefield("Forest", "Plains", "Mountain")
        alice.hand("Larder Ouphes", "Storm Spark")

        // CR 107.4e: each hybrid {G/W} paid with either colour
        alice.casts("Larder Ouphes", paying = listOf("Forest", "Plains", "Mountain"))
        allPass()
        allPass()
        expect { alice.life shouldBe 22 }
        alice.castsWithoutCosts("Storm Spark", target = "Larder Ouphes")
        resolve()
        expect { stack.names shouldBe listOf("ability of Larder Ouphes") }
        resolveStack()

        expect {
            alice.battlefield has "Larder Ouphes"
            "Larder Ouphes".counters(CounterKind.PowerToughness(-1, -1)) shouldBe 1
            "Larder Ouphes".power shouldBe 2
            alice.life shouldBe 24
        }
    }

    /** Enters: each opponent loses 1, you gain 1; sacrifice a Food: return it from the graveyard to the battlefield. */
    @Test
    fun kettleKitten() = scenario {
        alice.battlefield("Swamp", "Cookie Brawler")
        alice.hand("Kettle Kitten", "Storm Spark")

        castAndResolve("Kettle Kitten")
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
        alice.castsWithoutCosts("Storm Spark", target = "Kettle Kitten")
        resolve()
        alice.activates("Kettle Kitten", choosing = listOf("Cookie Brawler"))
        allPass()
        expect { "Kettle Kitten".zone shouldBe Zone.Battlefield }
        allPass()

        expect {
            alice.graveyard has "Cookie Brawler"
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }
}
