package arcanum.engine.resolution

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One-shot effects of cube cards as they resolve (CR 608.2, 610). */
class EffectsTest {
    // CR 608.2h: "its power" reads the exiled creature's last known information
    @Test
    fun exileAndGainLifeEqualToItsPower() = scenario {
        alice.hand("Plowshare Decree")
        bob.battlefield("Elvish Hierophant", "Umbral Elves")

        alice.castsWithoutCosts("Plowshare Decree", target = "Umbral Elves")
        resolve()

        expect {
            bob.exile has "Umbral Elves"
            // Umbral Elves was 2/2 with the Hierophant's anthem
            bob.life shouldBe 22
        }
    }

    // CR 400.7: a returned creature is a new object in its owner's hand
    @Test
    fun bounceToOwnersHand() = scenario {
        alice.hand("Click Back")
        alice.battlefield("Forest", "Forest", tapped = true)
        bob.battlefield("Umbral Elves")

        alice.castsWithoutCosts("Click Back", target = "Umbral Elves")
        resolve()
        answer(Answer.Objects(listOf(card("Forest", 0), card("Forest", 1))))

        expect {
            bob.hand has "Umbral Elves"
            card("Forest", 0).tapped shouldBe false
            card("Forest", 1).tapped shouldBe false
        }
    }

    // CR 401.4: "second from the top" counts from the top card
    @Test
    fun putIntoLibrarySecondFromTop() = scenario {
        alice.hand("Banish Below")
        bob.battlefield("Umbral Elves")
        bob.library("Forest", "Island", "Swamp")

        alice.castsWithoutCosts("Banish Below", target = "Umbral Elves")
        resolve()

        expect {
            bob.library.names shouldBe listOf("Forest", "Umbral Elves", "Island", "Swamp")
            bob.life shouldBe 23
        }
    }

    // CR 121.2: a player draws cards one at a time
    @Test
    fun targetPlayerDraws() = scenario {
        alice.hand("Forebear's Insight")
        bob.library("Forest", "Island", "Swamp", "Mountain")

        alice.castsWithoutCosts("Forebear's Insight", target = "bob")
        resolve()

        expect {
            bob.hand.names shouldBe listOf("Forest", "Island", "Swamp")
            bob.library.names shouldBe listOf("Mountain")
        }
    }

    // CR 119.3: losing life
    @Test
    fun drawAndLoseLife() = scenario {
        alice.hand("Midnight Murmur")
        alice.library("Forest", "Island")

        alice.castsWithoutCosts("Midnight Murmur")
        resolve()

        expect {
            alice.hand.size shouldBe 2
            alice.life shouldBe 18
        }
    }

    // CR 701.17a: milling puts the top cards into the graveyard
    @Test
    fun millThenDraw() = scenario {
        alice.hand("Thought Siphon")
        alice.library("Forest")
        bob.library("Island", "Swamp", "Mountain")

        alice.castsWithoutCosts("Thought Siphon", target = "bob")
        resolve()

        expect {
            bob.graveyard.names shouldBe listOf("Island", "Swamp")
            bob.library.names shouldBe listOf("Mountain")
            alice.hand has "Forest"
            log mentions "Milled"
        }
    }

    // CR 701.9a: the discarding player chooses the card
    @Test
    fun targetPlayerDiscardsACardOfTheirChoice() = scenario {
        alice.hand("Crow's Larceny")
        bob.hand("Forest", "Storm Spark")

        alice.castsWithoutCosts("Crow's Larceny", target = "bob")
        resolve()
        assertEquals(bob.id, decision<Decision.ChooseObjects>().player)
        bob.chooses("Storm Spark")

        expect {
            bob.graveyard has "Storm Spark"
            bob.hand.names shouldBe listOf("Forest")
            log mentions "Discarded"
        }
    }

    // CR 701.20, D60: the revealed hand's hidden cards are filled in by the host; the caster chooses
    @Test
    fun revealHandChooseAndDiscard() = scenario {
        alice.hand("Mind Pilfer")
        bob.hiddenHand("Forest", "Storm Spark", "Denial")

        alice.castsWithoutCosts("Mind Pilfer", target = "bob")
        resolve()
        assertEquals(alice.id, decision<Decision.ChooseObjects>().player)
        alice.chooses("Storm Spark")

        expect {
            bob.graveyard has "Storm Spark"
            alice.life shouldBe 18
        }
    }

    // CR 701.9b, D25: a random discard is asked from the host
    @Test
    fun eachPlayerDrawsThenDiscardsAtRandom() = scenario {
        alice.hand("Searing Question")
        alice.library("Forest", "Island", "Swamp")
        bob.library("Plains", "Mountain", "Forest")

        alice.castsWithoutCosts("Searing Question")
        resolve()

        expect {
            alice.hand.size shouldBe 0
            bob.hand.size shouldBe 0
            alice.graveyard.size shouldBe 4
            bob.graveyard.size shouldBe 3
        }
    }

    // CR 701.22a: scry puts any number on the bottom
    @Test
    fun scryToTheBottomThenDraw() = scenario {
        alice.hand("Glance Ahead")
        alice.library("Forest", "Island", "Swamp")

        alice.castsWithoutCosts("Glance Ahead")
        resolve()
        alice.chooses("Forest")

        expect {
            alice.hand.names shouldBe listOf("Island")
            alice.library.names shouldBe listOf("Swamp", "Forest")
        }
    }

    // CR 701.25a: surveil puts any number into the graveyard
    @Test
    fun surveilIntoTheGraveyardThenDraw() = scenario {
        alice.hand("Mull It Over")
        alice.library("Forest", "Island")

        alice.castsWithoutCosts("Mull It Over")
        resolve()
        alice.chooses("Forest")

        expect {
            alice.graveyard has "Forest"
            alice.hand.names shouldBe listOf("Island")
        }
    }

    // CR 701.23a, 701.24: search, then shuffle (the host orders the library, D25)
    @Test
    fun searchForACardThenShuffle() = scenario {
        alice.hand("Fiendish Mentor")
        alice.library("Forest", "Island", "Swamp", hidden = true)

        alice.castsWithoutCosts("Fiendish Mentor")
        resolve()
        alice.chooses("Island")

        expect {
            alice.hand.names shouldBe listOf("Island")
            alice.library.size shouldBe 2
            log mentions "Searched"
        }
    }

    // CR 701.24b: "shuffle and put that card on top" leaves the found card out of the shuffle
    @Test
    fun searchShuffleAndPutOnTop() = scenario {
        alice.hand("Bloodsucker's Lesson")
        alice.library("Forest", "Island", "Swamp")

        alice.castsWithoutCosts("Bloodsucker's Lesson")
        resolve()
        alice.chooses("Swamp")

        expect {
            alice.library.names.first() shouldBe "Swamp"
            alice.library.size shouldBe 3
            alice.life shouldBe 18
        }
    }

    // CR 106.4: mana is added to the pool
    @Test
    fun addThreeBlackMana() = scenario {
        alice.hand("Grim Offering")

        alice.castsWithoutCosts("Grim Offering")
        resolve()

        assertEquals(listOf(Color.Black, Color.Black, Color.Black), state.player(alice.id).manaPool.map { it.color })
    }

    // CR 106.1: "two mana in any combination of colors" asks a colour for each
    @Test
    fun addManaInAnyCombination() = scenario {
        alice.hand("Manashift")
        alice.library("Forest")

        alice.castsWithoutCosts("Manashift")
        resolve()
        answer(Answer.ColorChoice(Color.Red))
        answer(Answer.ColorChoice(Color.Green))

        assertEquals(listOf(Color.Red, Color.Green), state.player(alice.id).manaPool.map { it.color })
        expect { alice.hand has "Forest" }
    }

    // CR 611.2c, 704.5f: -5/-5 until end of turn; toughness 0 puts the creature into the graveyard
    @Test
    fun minusFiveKillsWithoutDestroying() = scenario {
        alice.hand("Sever Limb")
        bob.battlefield("Elvish Hierophant")

        alice.castsWithoutCosts("Sever Limb", target = "Elvish Hierophant")
        resolve()

        expect {
            bob.graveyard has "Elvish Hierophant"
            log mentions "StateBased"
        }
    }

    // CR 111.1, 111.4: tokens are created on the battlefield under their creator's control
    @Test
    fun createTwoFlyingSpirits() = scenario {
        alice.hand("Lingering Shades")

        alice.castsWithoutCosts("Lingering Shades")
        resolve()

        expect {
            alice.battlefield.size shouldBe 2
            alice.battlefield.names.forEach { assertTrue(it!!.contains("Spirit"), it) }
            card("Spirit Token").power shouldBe 1
        }
    }

    // CR 701.16a, 111.10f: investigate creates a Clue
    @Test
    fun crabAndInvestigate() = scenario {
        alice.hand("Solid Proof")

        alice.castsWithoutCosts("Solid Proof")
        resolve()

        expect {
            alice.battlefield.names.toSet() shouldBe setOf("Crab Token", "Clue Token")
            card("Crab Token").toughness shouldBe 3
        }
    }

    // CR 500.7: an extra turn after this one
    @Test
    fun takeAnExtraTurn() = scenario {
        alice.hand("Time Stride")

        alice.castsWithoutCosts("Time Stride")
        resolve()

        assertEquals(listOf(alice.id), state.turn.extraTurns)
    }

    // CR 700.2: only the chosen mode happens
    @Test
    fun modalSpellDoesTheChosenMode() = scenario {
        alice.hand("Grindstone Rasp", "Grindstone Rasp")
        bob.battlefield("Elvish Hierophant", "Tinkered Mite")

        alice.castsWithoutCosts("Grindstone Rasp", target = "Tinkered Mite", modes = listOf(1))
        resolve()

        expect {
            bob.graveyard has "Tinkered Mite"
            bob.battlefield has "Elvish Hierophant"
        }
    }

    // CR 601.2d, 608.2: damage divided as chosen when cast
    @Test
    fun dividedDamage() = scenario {
        alice.hand("Wildfire Spread")
        bob.battlefield("Elvish Hierophant", "Umbral Elves")

        alice.castsWithoutCosts("Wildfire Spread", targets = listOf("Elvish Hierophant", "bob"), x = 4, divided = listOf(2, 2))
        resolve()

        expect {
            bob.graveyard has "Elvish Hierophant"
            bob.life shouldBe 18
            bob.battlefield has "Umbral Elves"
        }
    }

    // CR 119.3: lose and gain life as one resolution
    @Test
    fun drainTwo() = scenario {
        alice.hand("Tendrils of Torment")

        alice.castsWithoutCosts("Tendrils of Torment", target = "bob")
        // Storm's trigger (no spells before it: no copies) resolves first
        resolveStack()

        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    // CR 614.15, 616.1a: the spell's own replacement puts the countered spell on top of its owner's library
    @Test
    fun counteredSpellGoesOnTopOfTheLibraryInstead() = scenario {
        alice.hand("Storm Spark")
        bob.hand("Recall Lapse")
        alice.library("Forest")

        alice.castsWithoutCosts("Storm Spark", target = "bob")
        bob.castsWithoutCosts("Recall Lapse", target = "Storm Spark")
        resolve()

        expect {
            alice.library.names shouldBe listOf("Storm Spark", "Forest")
            alice.graveyard lacks "Storm Spark"
            bob.graveyard has "Recall Lapse"
        }
    }

    // CR 614.15: "into its owner's hand instead", then draw a card
    @Test
    fun counteredSpellGoesToHandInstead() = scenario {
        alice.hand("Storm Spark")
        bob.hand("Send Back")
        bob.library("Island")

        alice.castsWithoutCosts("Storm Spark", target = "bob")
        bob.castsWithoutCosts("Send Back", target = "Storm Spark")
        resolve()

        expect {
            alice.hand has "Storm Spark"
            bob.hand has "Island"
        }
    }

    // CR 118.12a, D52: "unless its controller pays": declining means it is countered
    @Test
    fun counterUnlessPaysAndTheyDecline() = scenario {
        alice.hand("Storm Spark")
        bob.hand("Mana Levy")

        alice.castsWithoutCosts("Storm Spark", target = "bob")
        bob.castsWithoutCosts("Mana Levy", target = "Storm Spark")
        resolve()
        assertEquals(alice.id, decision<Decision.May>().player)
        answer(Answer.YesNo(false))

        expect {
            alice.graveyard has "Storm Spark"
            stack.isEmpty()
        }
    }

    // CR 118.12a: paying (by hand until mana payment exists) keeps the spell
    @Test
    fun counterUnlessPaysAndTheyPay() = scenario {
        alice.hand("Storm Spark")
        bob.hand("Mana Levy")

        alice.castsWithoutCosts("Storm Spark", target = "bob")
        bob.castsWithoutCosts("Mana Levy", target = "Storm Spark")
        resolve()
        answer(Answer.YesNo(true))
        decision<Decision.Manual>()
        answer(Answer.Done)

        expect { stack.names shouldBe listOf("Storm Spark") }
        resolve()
        expect { bob.life shouldBe 17 }
    }

    // CR 118.12a, 608.2g: mana paid on resolution: the payer taps lands for it
    @Test
    fun counterUnlessPaysWithALand() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Storm Spark")
        bob.hand("Mana Levy")

        alice.castsWithoutCosts("Storm Spark", target = "bob")
        bob.castsWithoutCosts("Mana Levy", target = "Storm Spark")
        resolve()
        answer(Answer.YesNo(true))
        val payment = decision<Decision.ManaPayment>()
        assertEquals("{1}", payment.cost.toString())
        answer(Answer.ManaPayment(payment.suggestion))

        expect {
            "Mountain".tapped shouldBe true
            stack.names shouldBe listOf("Storm Spark")
        }
    }

    // CR 707.10c: a copy may change to the only other legal target
    @Test
    fun copyMayTakeTheOnlyOtherTarget() = scenario {
        // Cast from the graveyard, it may copy itself; Lotus Frond is the copy's only legal target
        alice.graveyard("Warden's Reclamation", "Elvish Seer", "Lotus Frond")
        alice.castsWithoutCosts("Warden's Reclamation", target = "Elvish Seer")
        resolve()
        answer(Answer.YesNo(true))
        answer(Answer.YesNo(true))
        assertEquals(listOf(target("Lotus Frond")), decision<Decision.Targets>().options)
        answer(Answer.Targets(listOf(target("Lotus Frond"))))
        resolve()

        expect {
            alice.battlefield has "Elvish Seer"
            alice.battlefield has "Lotus Frond"
        }
    }

    // CR 707.10: a copy of a spell is put on the Stack, and its controller may choose new targets
    @Test
    fun copyASpellWithNewTargets() = scenario {
        alice.hand("Storm Spark", "Expanse")
        bob.battlefield("Umbral Elves")

        alice.castsWithoutCosts("Storm Spark", target = "bob")
        alice.castsWithoutCosts("Expanse", target = "Storm Spark")
        resolve()
        answer(Answer.YesNo(true))
        answer(Answer.Targets(listOf(target("Umbral Elves"))))

        expect { stack.names shouldBe listOf("Storm Spark", "copy of Storm Spark") }
        resolveStack()
        expect {
            bob.graveyard has "Umbral Elves"
            bob.life shouldBe 17
            log mentions "Copied"
        }
    }

    // CR 701.21a, 608.2e: each opponent chooses, then all sacrifice at once
    @Test
    fun eachOpponentSacrificesTheirBiggest() = scenario(players = 3) {
        alice.hand("Flash of Spite")
        bob.battlefield("Elvish Hierophant", "Umbral Elves")
        carol.battlefield("Tinkered Mite")

        alice.castsWithoutCosts("Flash of Spite")
        resolve()

        expect {
            bob.graveyard.names shouldBe listOf("Elvish Hierophant")
            carol.graveyard.names shouldBe listOf("Tinkered Mite")
            log mentions "Sacrificed"
        }
        val sacrifices = applied.filterIsInstance<arcanum.engine.change.Change.ZoneChange>()
            .filter { z -> z.moves.any { arcanum.engine.change.Meaning.Sacrificed in it.meanings } }
        assertEquals(1, sacrifices.size, "One event for both sacrifices (CR 608.2e)")
    }

    // CR 701.20, D60: look at a hidden hand (filled in by the host), then draw
    @Test
    fun lookAtTargetPlayersHand() = scenario {
        alice.hand("Cerebral Scan")
        alice.library("Forest")
        bob.hiddenHand("Storm Spark")

        alice.castsWithoutCosts("Cerebral Scan", target = "bob")
        resolve()

        expect { alice.hand has "Forest" }
        assertTrue(state.objOrThrow(card("Storm Spark")).definition != null)
    }

    // CR 107.3, 202.3: X announced for the spell; mana value matching
    @Test
    fun returnPermanentWithManaValueX() = scenario {
        alice.hand("Rescind")
        alice.library("Forest")
        bob.battlefield("Umbral Elves")

        alice.castsWithoutCosts("Rescind", target = "Umbral Elves", x = 1)
        resolve()

        expect {
            bob.hand has "Umbral Elves"
            alice.hand has "Forest"
        }
    }

    // CR 701.24a: each player shuffles hand and graveyard into the library, then draws seven
    @Test
    fun wheelEachPlayer() = scenario {
        alice.hand("Hourspinner", "Storm Spark")
        alice.graveyard("Forest")
        alice.library("Island", "Island", "Island", "Island", "Island", "Island")
        bob.library("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        bob.graveyard("Mountain")

        alice.castsWithoutCosts("Hourspinner")
        resolve()

        expect {
            alice.hand.size shouldBe 7
            bob.hand.size shouldBe 7
            bob.library.size shouldBe 1
            alice.graveyard.names shouldBe listOf("Hourspinner")
        }
    }

    // CR 603.7a: a delayed trigger created on resolution triggers later (at the beginning of the next main phase)
    @Test
    fun counterAndGetManaLater() = scenario {
        alice.hand("Storm Spark")
        bob.hand("Mana Siphon")

        alice.castsWithoutCosts("Storm Spark", target = "bob")
        bob.castsWithoutCosts("Mana Siphon", target = "Storm Spark")
        resolve()

        expect { alice.graveyard has "Storm Spark" }
        assertEquals(1, state.delayedTriggers.size)
    }

    // CR 701.8a: destroy
    @Test
    fun destroyTargetPermanent() = scenario {
        alice.hand("Sudden Rot")
        bob.battlefield("Elvish Hierophant")

        alice.castsWithoutCosts("Sudden Rot", target = "Elvish Hierophant")
        resolve()

        expect {
            bob.graveyard has "Elvish Hierophant"
            log mentions "Destroyed"
        }
    }

    // CR 701.24: "You may shuffle" after putting cards back in any order
    @Test
    fun putBackInAnyOrder() = scenario {
        alice.hand("Muse Awhile")
        alice.library("Forest", "Island", "Swamp", "Mountain")

        alice.castsWithoutCosts("Muse Awhile")
        resolve()
        while (pending != null) {
            when (val d = pending) {
                is Decision.ChooseObjects -> answer(Answer.Objects(listOf(card("Swamp"), card("Island"), card("Forest"))))
                is Decision.May -> answer(Answer.YesNo(false))
                else -> error("Unexpected $d")
            }
        }

        expect {
            alice.hand.names shouldBe listOf("Swamp")
            alice.library.names shouldBe listOf("Island", "Forest", "Mountain")
        }
    }

    // CR 122.1: counters on a permanent as it enters (CR 122.6)
    @Test
    fun entersWithXCounters() = scenario {
        alice.hand("Striding Arbalest")

        alice.castsWithoutCosts("Striding Arbalest", x = 3)
        resolve()

        expect {
            "Striding Arbalest".counters(CounterKind.PowerToughness(1, 1)) shouldBe 3
            "Striding Arbalest".power shouldBe 3
        }
    }

    @Suppress("unused")
    private val step = TurnStep.PrecombatMain

    @Suppress("unused")
    private val zone = Zone.Battlefield
}
