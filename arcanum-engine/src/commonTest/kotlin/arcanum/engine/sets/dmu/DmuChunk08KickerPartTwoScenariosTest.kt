package arcanum.engine.sets.dmu

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The DMU kicker cards of chunk 08, played: the invented names carry the oracle text of Fires of Victory, Ghitu Amplifier, Hurloon Battle
 * Hymn, Joint Exploration, Juniper Order Rootweaver, Keldon Strike Team, Phyrexian Espionage, Phyrexian Missionary, Phyrexian Warhorse,
 * Runic Shot, Scout the Wilderness, Shalai's Acolyte, Sprouting Goblin, Stall for Time, Strength of the Coalition, Tear Asunder, Tolarian
 * Geyser, Urborg Repossession, Viashino Branchrider, Vineshaper Prodigy, Warhost's Frenzy and Yavimaya Iconoclast.
 */
class DmuChunk08KickerPartTwoScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val hulk = Cards.invent("Quillbrook Hulk", "{4}{G}", "Creature — Beast", "", "5/5")
    private val runt = Cards.invent("Quillbrook Runt", "{1}{G}", "Creature — Beast", "", "2/2")
    private val sentry = Cards.invent("Quillbrook Sentry", "{1}{W}", "Creature — Soldier", "", "2/2")
    private val spark = Cards.invent("Quillbrook Spark", "{R}", "Instant", "Draw a card.")
    private val zap = Cards.invent("Quillbrook Zap", "{R}", "Instant", "Quillbrook Zap deals 5 damage to target creature.")
    private val relic = Cards.invent("Quillbrook Relic", "{2}", "Artifact", "")
    private val boon = Cards.invent("Quillbrook Boon", "{1}{W}", "Enchantment", "")

    private val flames = Cards.invent("Flames of Triumph", "{1}{R}", "Instant", """
        Kicker {2}{U} (You may pay an additional {2}{U} as you cast this spell.)
        If this spell was kicked, draw a card. Flames of Triumph deals damage to target creature or planeswalker equal to the number of cards in your hand.
    """)
    private val amplifier = Cards.invent("Emberite Amplifier", "{1}{R}", "Creature — Human Wizard", """
        Kicker {2}{U} (You may pay an additional {2}{U} as you cast this spell.)
        When this creature enters, if it was kicked, return target creature an opponent controls to its owner's hand.
        Whenever you cast an instant or sorcery spell, this creature gets +2/+0 until end of turn.
    """, "1/2")
    private val chant = Cards.invent("Minotaur War Chant", "{2}{R}", "Instant", """
        Kicker {W} (You may pay an additional {W} as you cast this spell.)
        Minotaur War Chant deals 4 damage to target creature or planeswalker. If this spell was kicked, you gain 4 life.
    """)
    private val foray = Cards.invent("Shared Foray", "{1}{U}", "Instant", """
        Kicker {G} (You may pay an additional {G} as you cast this spell.)
        Scry 2, then draw a card. If this spell was kicked, you may put a land card from your hand onto the battlefield.
    """)
    private val weaver = Cards.invent("Cedar Guild Weaver", "{1}{W}", "Creature — Human Druid", """
        Kicker {G} (You may pay an additional {G} as you cast this spell.)
        When this creature enters, if it was kicked, put a +1/+1 counter on target creature you control.
    """, "2/2")
    private val squad = Cards.invent("Cinder Raid Squad", "{2}{R}", "Creature — Human Warrior", """
        Kicker {1}{W} (You may pay an additional {1}{W} as you cast this spell.)
        When this creature enters, if it was kicked, create two 1/1 white Soldier creature tokens.
        As long as this creature entered this turn, creatures you control have haste.
    """, "3/1")
    private val spying = Cards.invent("Praetor Spying", "{2}{U}", "Sorcery", """
        Kicker {1}{B} (You may pay an additional {1}{B} as you cast this spell.)
        Draw two cards. If this spell was kicked, each opponent discards a card.
    """)
    private val envoy = Cards.invent("Praetor Envoy", "{1}{W}", "Creature — Phyrexian Human Cleric", """
        Kicker {1}{B} (You may pay an additional {1}{B} as you cast this spell.)
        Lifelink
        When this creature enters, if it was kicked, return target creature card from your graveyard to your hand.
    """, "2/3")
    private val charger = Cards.invent("Praetor Charger", "{3}{B}", "Creature — Phyrexian Horse", """
        Kicker {W} (You may pay an additional {W} as you cast this spell.)
        When this creature enters, if it was kicked, create a 1/1 white Soldier creature token.
        {1}, Sacrifice another creature: This creature gets +2/+1 until end of turn.
    """, "3/3")
    private val glyph = Cards.invent("Glyph Bolt", "{W}", "Sorcery", """
        Kicker {U} (You may pay an additional {U} as you cast this spell.)
        Destroy target tapped creature. If this spell was kicked, scry 2.
    """)
    private val survey = Cards.invent("Survey the Wilds", "{2}{G}", "Sorcery", """
        Kicker {1}{W} (You may pay an additional {1}{W} as you cast this spell.)
        Search your library for a basic land card, put it onto the battlefield tapped, then shuffle. If this spell was kicked, create two 1/1 white Soldier creature tokens.
    """)
    private val novice = Cards.invent("Halo's Novice", "{4}{W}", "Creature — Angel", """
        Kicker {1}{G} (You may pay an additional {1}{G} as you cast this spell.)
        Flying
        If this creature was kicked, it enters with two +1/+1 counters on it.
    """, "3/4")
    private val imp = Cards.invent("Budding Imp", "{1}{R}", "Creature — Goblin Druid", """
        Kicker {G} (You may pay an additional {G} as you cast this spell.)
        When this creature enters, if it was kicked, search your library for a land card with a basic land type, reveal it, put it into your hand, then shuffle.
        {R}, {T}, Sacrifice a land: Draw a card.
    """, "2/2")
    private val delay = Cards.invent("Delay the Hour", "{2}{W}", "Instant", """
        Kicker {1}{U} (You may pay an additional {1}{U} as you cast this spell.)
        Tap up to two target creatures. If this spell was kicked, put a stun counter on each of those creatures. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        Draw a card.
    """)
    private val might = Cards.invent("Might of the Alliance", "{G}", "Instant", """
        Kicker {2}{W} (You may pay an additional {2}{W} as you cast this spell.)
        Target creature you control gets +2/+2 until end of turn. If this spell was kicked, put a +1/+1 counter on each creature you control.
    """)
    private val rend = Cards.invent("Rend Apart", "{1}{G}", "Instant", """
        Kicker {1}{B} (You may pay an additional {1}{B} as you cast this spell.)
        Exile target artifact or enchantment. If this spell was kicked, exile target nonland permanent instead.
    """)
    private val fountain = Cards.invent("Arcane Fountain", "{2}{U}", "Sorcery", """
        Kicker {W} (You may pay an additional {W} as you cast this spell.)
        Return target creature to its owner's hand. Draw a card. If this spell was kicked, you gain 3 life.
    """)
    private val reclamation = Cards.invent("Swamp Reclamation", "{B}", "Sorcery", """
        Kicker {1}{G} (You may pay an additional {1}{G} as you cast this spell.)
        Return target creature card from your graveyard to your hand. You gain 2 life. If this spell was kicked, return another target permanent card from your graveyard to your hand.
    """)
    private val hillrider = Cards.invent("Skink Hillrider", "{R}", "Creature — Lizard Warrior", """
        Kicker {2}{G} (You may pay an additional {2}{G} as you cast this spell.)
        Haste
        If this creature was kicked, it enters with two +1/+1 counters on it.
        {2}{R}: This creature gets +2/+0 until end of turn.
    """, "1/1")
    private val prodigy = Cards.invent("Grovecraft Prodigy", "{1}{G}", "Creature — Elf Druid", """
        Kicker {1}{U} (You may pay an additional {1}{U} as you cast this spell.)
        When this creature enters, if it was kicked, look at the top three cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order.
    """, "2/2")
    private val rage = Cards.invent("Horde's Rage", "{2}{R}", "Instant", """
        Kicker {B} (You may pay an additional {B} as you cast this spell.)
        Creatures you control get +2/+0 until end of turn. If this spell was kicked, whenever a creature you control dies this turn, draw a card.
    """)
    private val heretic = Cards.invent("Jungle Heretic", "{1}{G}", "Creature — Elf", """
        Kicker {R} (You may pay an additional {R} as you cast this spell.)
        Trample
        When this creature enters, if it was kicked, it gets +1/+1 and gains haste until end of turn.
    """, "3/2")

    /** Resolves the Stack and answers what is asked: [prefer]red objects when offered (else the first the minimum allows), targets
     * [target]ed, yes to a "you may", until nothing is open. */
    private fun arcanum.engine.scenario.Scenario.answerAll(prefer: List<String> = emptyList(), target: String? = null) {
        resolveAll()
        var guard = 12
        while (guard-- > 0) {
            when (val d = pending ?: break) {
                is Decision.ChooseObjects -> {
                    val ids = prefer.mapNotNull { n -> cards(n).map { it.id }.firstOrNull { it in d.options } }
                    val ans = if (ids.size >= d.min) ids.take(d.max ?: ids.size) else d.options.take(d.min)
                    answer(Answer.Objects(ans))
                }
                is Decision.Targets -> answer(Answer.Targets(listOfNotNull(target?.let { target(it) })))
                is Decision.May -> answer(Answer.YesNo(true))
                else -> error("unhandled $d")
            }
            resolveAll()
        }
    }

    // CR 702.33d, 601.2c: kicked, the draw comes first, so the damage counts the card drawn too
    @Test
    fun flamesKickedCountsTheDrawnCard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Island", "Island", "Island")
        alice.hand(flames, runt, sentry)
        alice.library("Forest")
        bob.battlefield(hulk)
        alice.casts(flames, target = hulk, kicked = 1)
        resolveAll()
        expect {
            alice.hand.size shouldBe 3
            hulk.view.obj?.damage shouldBe 3
        }
    }

    // CR 702.33d: unkicked, nothing is drawn and the damage counts the two cards left in hand
    @Test
    fun flamesUnkickedCountsTheHand() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(flames, runt, sentry)
        alice.library("Forest")
        bob.battlefield(hulk)
        alice.casts(flames, target = hulk, kicked = 0)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            hulk.view.obj?.damage shouldBe 2
        }
    }

    // CR 702.33d, 603.4: kicked, the enter trigger returns an opponent's creature
    @Test
    fun amplifierKickedReturnsACreature() = scenario {
        alice.battlefield("Mountain", "Mountain", "Island", "Island", "Island")
        alice.hand(amplifier)
        bob.battlefield(hulk)
        alice.casts(amplifier, kicked = 1)
        answerAll(target = hulk)
        expect {
            alice.battlefield has amplifier
            bob.hand has hulk
        }
    }

    // CR 603.4: unkicked, the trigger doesn't happen, so no target is asked and the creature stays
    @Test
    fun amplifierUnkickedLeavesTheCreature() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(amplifier)
        bob.battlefield(hulk)
        alice.casts(amplifier, kicked = 0)
        resolveAll()
        expect {
            alice.battlefield has amplifier
            bob.battlefield has hulk
        }
    }

    // CR 603.2: casting an instant gives it +2/+0 until end of turn
    @Test
    fun amplifierGrowsWhenYouCastAnInstant() = scenario {
        alice.battlefield(amplifier, "Mountain")
        alice.hand(spark)
        alice.library("Forest")
        alice.casts(spark)
        resolveAll()
        expect { amplifier.power shouldBe 3 }
    }

    // CR 702.33d: kicked, you also gain 4 life
    @Test
    fun chantKickedGainsLife() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Plains")
        alice.hand(chant)
        bob.battlefield(runt)
        alice.casts(chant, target = runt, kicked = 1)
        resolveAll()
        expect {
            runt.zone shouldBe Zone.Graveyard
            alice.life shouldBe 24
        }
    }

    @Test
    fun chantUnkickedGainsNothing() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Plains")
        alice.hand(chant)
        bob.battlefield(runt)
        alice.casts(chant, target = runt, kicked = 0)
        resolveAll()
        expect {
            runt.zone shouldBe Zone.Graveyard
            alice.life shouldBe 20
        }
    }

    // CR 702.33d: the land from your hand comes onto the battlefield only when kicked
    @Test
    fun forayKickedPutsALandOntoTheBattlefield() = scenario {
        alice.battlefield("Island", "Island", "Forest")
        alice.hand(foray, "Swamp")
        alice.library("Plains", "Plains", "Plains")
        alice.casts(foray, kicked = 1)
        answerAll(prefer = listOf("Swamp"))
        expect { alice.battlefield has "Swamp" }
    }

    @Test
    fun forayUnkickedKeepsTheLandInHand() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(foray, "Swamp")
        alice.library("Plains", "Plains", "Plains")
        alice.casts(foray, kicked = 0)
        answerAll()
        expect {
            alice.hand has "Swamp"
            alice.hand has "Plains"
        }
    }

    // CR 702.33d, 603.4: kicked, a +1/+1 counter on a creature you control
    @Test
    fun weaverKickedPutsACounter() = scenario {
        alice.battlefield("Plains", "Plains", "Forest", runt)
        alice.hand(weaver)
        alice.casts(weaver, kicked = 1)
        answerAll(target = runt)
        expect { runt.counters(p1p1) shouldBe 1 }
    }

    // CR 603.4: unkicked, there is no trigger and so nothing to target
    @Test
    fun weaverUnkickedPutsNoCounter() = scenario {
        alice.battlefield("Plains", "Plains", runt)
        alice.hand(weaver)
        alice.casts(weaver, kicked = 0)
        resolveAll()
        expect {
            alice.battlefield has weaver
            runt.counters(p1p1) shouldBe 0
        }
    }

    // CR 702.33d, 611.3a: kicked, two Soldiers come, and while this creature entered this turn the creatures have haste
    @Test
    fun squadKickedCreatesSoldiersWithHaste() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Plains", "Plains", runt)
        alice.hand(squad)
        alice.casts(squad, kicked = 1)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Soldier Token" } shouldBe 2
            runt hasKeyword Keyword.Haste.kind
            squad hasKeyword Keyword.Haste.kind
        }
    }

    // CR 603.4, 611.3a: unkicked, no Soldiers; the haste ends with the turn it entered
    @Test
    fun squadUnkickedHasteEndsNextTurn() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", runt)
        alice.hand(squad)
        alice.library("Forest", "Forest")
        bob.library("Forest", "Forest")
        alice.casts(squad, kicked = 0)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Soldier Token" } shouldBe 0
            runt hasKeyword Keyword.Haste.kind
        }
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect { assertTrue(!runt.view.characteristics!!.hasKeyword(Keyword.Haste.kind), "no haste any more") }
    }

    // CR 702.33d, 101.4: kicked, each opponent (both of them) discards
    @Test
    fun spyingKickedMakesEachOpponentDiscard() = scenario(players = 3) {
        alice.battlefield("Island", "Island", "Island", "Swamp", "Swamp")
        alice.hand(spying)
        alice.library("Forest", "Forest")
        bob.hand(runt)
        carol.hand(sentry)
        alice.casts(spying, kicked = 1)
        answerAll(prefer = listOf(runt, sentry))
        expect {
            alice.hand.size shouldBe 2
            bob.graveyard has runt
            carol.graveyard has sentry
        }
    }

    @Test
    fun spyingUnkickedOnlyDraws() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(spying)
        alice.library("Forest", "Forest")
        bob.hand(runt)
        alice.casts(spying, kicked = 0)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            bob.hand has runt
        }
    }

    // CR 702.33d, 603.4: kicked, a creature card comes back from the graveyard to hand
    @Test
    fun envoyKickedReturnsACreatureCard() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp")
        alice.hand(envoy)
        alice.graveyard(hulk)
        alice.casts(envoy, kicked = 1)
        answerAll(target = hulk)
        expect { alice.hand has hulk }
    }

    // CR 603.4: unkicked, the graveyard is left alone
    @Test
    fun envoyUnkickedLeavesTheGraveyard() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(envoy)
        alice.graveyard(hulk)
        alice.casts(envoy, kicked = 0)
        resolveAll()
        expect {
            alice.battlefield has envoy
            alice.graveyard has hulk
        }
    }

    // CR 702.33d, 603.4, 602.2: kicked, a Soldier; sacrificing it pumps the Horse
    @Test
    fun chargerKickedMakesASoldierToSacrifice() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Plains", "Plains", "Swamp")
        alice.hand(charger)
        alice.casts(charger, kicked = 1)
        resolveAll()
        expect { alice.battlefield has "Soldier Token" }
        alice.activates(charger, choosing = listOf("Soldier Token"))
        resolveAll()
        expect {
            charger.power shouldBe 5
            charger.toughness shouldBe 4
        }
    }

    // CR 702.33d, 601.2c: the spell destroys a tapped creature, kicked it also scries
    @Test
    fun glyphKickedDestroysAndScries() = scenario {
        alice.battlefield("Plains", "Island")
        alice.hand(glyph)
        alice.library("Forest", "Plains")
        bob.battlefield(hulk, tapped = true)
        alice.casts(glyph, target = hulk, kicked = 1)
        answerAll()
        expect { hulk.zone shouldBe Zone.Graveyard }
    }

    // CR 601.2c: an untapped creature isn't a legal target
    @Test
    fun glyphNeedsATappedCreature() = scenario {
        alice.battlefield("Plains")
        alice.hand(glyph)
        bob.battlefield(hulk)
        kotlin.test.assertFailsWith<IllegalArgumentException> { alice.casts(glyph, target = hulk, kicked = 0) }
    }

    // CR 702.33d: kicked, two Soldiers besides the tapped basic land from the library
    @Test
    fun surveyKickedAlsoCreatesSoldiers() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Plains", "Plains")
        alice.hand(survey)
        alice.library("Mountain")
        alice.casts(survey, kicked = 1)
        answerAll(prefer = listOf("Mountain"))
        expect {
            card("Mountain").zone shouldBe Zone.Battlefield
            card("Mountain").tapped shouldBe true
            alice.battlefield.names.count { it == "Soldier Token" } shouldBe 2
        }
    }

    @Test
    fun surveyUnkickedCreatesNoSoldiers() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(survey)
        alice.library("Mountain")
        alice.casts(survey, kicked = 0)
        answerAll(prefer = listOf("Mountain"))
        expect {
            card("Mountain").zone shouldBe Zone.Battlefield
            alice.battlefield.names.count { it == "Soldier Token" } shouldBe 0
        }
    }

    // CR 702.33d, 614.1c: kicked, two counters as it enters; unkicked, none
    @Test
    fun noviceKickedEntersWithCounters() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Forest", "Forest")
        alice.hand(novice)
        alice.casts(novice, kicked = 1)
        resolveAll()
        expect {
            novice.counters(p1p1) shouldBe 2
            novice.power shouldBe 5
        }
    }

    @Test
    fun noviceUnkickedEntersPlain() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(novice)
        alice.casts(novice, kicked = 0)
        resolveAll()
        expect {
            novice.counters(p1p1) shouldBe 0
            novice.power shouldBe 3
        }
    }

    // CR 702.33d, 603.4: kicked, a land with a basic land type (a nonbasic Forest-typed land counts, a plain one doesn't) goes to hand
    @Test
    fun impKickedSearchesForALandWithABasicType() = scenario {
        val dual = Cards.invent("Quillbrook Hollow", null, "Land — Forest Swamp", "")
        alice.battlefield("Mountain", "Mountain", "Forest")
        alice.hand(imp)
        alice.library(relic, dual)
        alice.casts(imp, kicked = 1)
        answerAll(prefer = listOf(dual))
        expect {
            alice.hand has dual
            assertTrue(alice.hand.names.none { it == relic }, "the artifact is not a land")
        }
    }

    // CR 702.33d: unkicked, nothing is searched for
    @Test
    fun impUnkickedSearchesNothing() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(imp)
        alice.library("Forest")
        alice.casts(imp, kicked = 0)
        resolveAll()
        expect {
            alice.battlefield has imp
            alice.library has "Forest"
        }
    }

    // CR 602.2, 701.21: sacrificing a land draws a card
    @Test
    fun impSacrificesALandToDraw() = scenario {
        alice.battlefield(imp, "Mountain", "Forest")
        alice.library("Plains")
        alice.activates(imp, choosing = listOf("Forest"))
        resolveAll()
        expect {
            alice.hand has "Plains"
            alice.graveyard has "Forest"
        }
    }

    // CR 702.33d, 702.171: kicked, both tapped creatures get a stun counter and stay tapped through the untap step (CR 122.1g)
    @Test
    fun delayKickedStunsBothCreatures() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Island", "Island")
        alice.hand(delay)
        alice.library("Forest")
        bob.battlefield(hulk, runt)
        alice.casts(delay, targets = listOf(hulk, runt), kicked = 1)
        resolveAll()
        expect {
            hulk.tapped shouldBe true
            runt.tapped shouldBe true
            hulk.counters(CounterKind.Stun) shouldBe 1
            runt.counters(CounterKind.Stun) shouldBe 1
            alice.hand has "Forest"
        }
    }

    @Test
    fun delayUnkickedOnlyTaps() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(delay)
        alice.library("Forest")
        bob.battlefield(hulk)
        alice.casts(delay, target = hulk, kicked = 0)
        resolveAll()
        expect {
            hulk.tapped shouldBe true
            hulk.counters(CounterKind.Stun) shouldBe 0
        }
    }

    // CR 702.33d, 122.1: kicked, every creature you control gets a counter
    @Test
    fun mightKickedPutsCountersOnEveryCreature() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Plains", runt, sentry)
        alice.hand(might)
        alice.casts(might, target = runt, kicked = 1)
        resolveAll()
        expect {
            runt.power shouldBe 5
            runt.counters(p1p1) shouldBe 1
            sentry.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun mightUnkickedOnlyPumpsTheTarget() = scenario {
        alice.battlefield("Forest", runt, sentry)
        alice.hand(might)
        alice.casts(might, target = runt, kicked = 0)
        resolveAll()
        expect {
            runt.power shouldBe 4
            sentry.counters(p1p1) shouldBe 0
        }
    }

    // CR 601.2c, 702.33d: unkicked, only the artifact or enchantment is chosen; a creature isn't a legal target
    @Test
    fun rendUnkickedExilesAnArtifactOrEnchantment() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(rend)
        bob.battlefield(relic, hulk)
        alice.casts(rend, target = relic, kicked = 0)
        resolveAll()
        expect {
            relic.zone shouldBe Zone.Exile
            hulk.zone shouldBe Zone.Battlefield
        }
    }

    // CR 601.2c, 702.33d (the audit's case): kicked, only the nonland permanent is chosen, so no artifact or enchantment is needed
    @Test
    fun rendKickedExilesAnyNonlandPermanent() = scenario {
        alice.battlefield("Forest", "Forest", "Swamp", "Swamp")
        alice.hand(rend)
        bob.battlefield(hulk)
        alice.casts(rend, target = hulk, kicked = 1)
        resolveAll()
        expect { hulk.zone shouldBe Zone.Exile }
    }

    // CR 601.2c: unkicked with only a creature to hit there is no legal target
    @Test
    fun rendUnkickedNeedsAnArtifactOrEnchantment() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(rend)
        bob.battlefield(hulk)
        kotlin.test.assertFailsWith<IllegalArgumentException> { alice.casts(rend, target = hulk, kicked = 0) }
    }

    // CR 702.33d: kicked, 3 life besides the bounce and the draw
    @Test
    fun fountainKickedGainsLife() = scenario {
        alice.battlefield("Island", "Island", "Island", "Plains")
        alice.hand(fountain)
        alice.library("Forest")
        bob.battlefield(hulk)
        alice.casts(fountain, target = hulk, kicked = 1)
        resolveAll()
        expect {
            bob.hand has hulk
            alice.hand has "Forest"
            alice.life shouldBe 23
        }
    }

    @Test
    fun fountainUnkickedGainsNoLife() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(fountain)
        alice.library("Forest")
        bob.battlefield(hulk)
        alice.casts(fountain, target = hulk, kicked = 0)
        resolveAll()
        expect {
            bob.hand has hulk
            alice.life shouldBe 20
        }
    }

    // CR 702.33d, 115.3: kicked, a second, different permanent card returns too
    @Test
    fun reclamationKickedReturnsASecondCard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Forest")
        alice.hand(reclamation)
        alice.graveyard(hulk, relic)
        alice.casts(reclamation, targets = listOf(hulk, relic), kicked = 1)
        resolveAll()
        expect {
            alice.hand has hulk
            alice.hand has relic
            alice.life shouldBe 22
        }
    }

    // CR 115.3: the second target can't be the first target again; it needs another card
    @Test
    fun reclamationKickedNeedsADifferentSecondCard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Forest")
        alice.hand(reclamation)
        alice.graveyard(hulk)
        kotlin.test.assertFailsWith<IllegalArgumentException> { alice.casts(reclamation, targets = listOf(hulk, hulk), kicked = 1) }
    }

    // CR 702.33d, 601.2c: unkicked, one target and the second card stays
    @Test
    fun reclamationUnkickedReturnsOne() = scenario {
        alice.battlefield("Swamp")
        alice.hand(reclamation)
        alice.graveyard(hulk, relic)
        alice.casts(reclamation, target = hulk, kicked = 0)
        resolveAll()
        expect {
            alice.hand has hulk
            alice.graveyard has relic
            alice.life shouldBe 22
        }
    }

    // CR 702.33d, 614.1c, 702.10: kicked, two counters and then haste lets it attack at once; the pump costs {2}{R}
    @Test
    fun hillriderKickedEntersWithCountersAndPumps() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Forest", "Forest", "Mountain", "Mountain")
        alice.hand(hillrider)
        alice.casts(hillrider, kicked = 1)
        resolveAll()
        expect {
            hillrider.counters(p1p1) shouldBe 2
            hillrider.power shouldBe 3
        }
        alice.activates(hillrider)
        resolveAll()
        expect { hillrider.power shouldBe 5 }
    }

    @Test
    fun hillriderUnkickedEntersPlain() = scenario {
        alice.battlefield("Mountain")
        alice.hand(hillrider)
        alice.casts(hillrider, kicked = 0)
        resolveAll()
        expect { hillrider.counters(p1p1) shouldBe 0 }
    }

    // CR 702.33d, 603.4: kicked, look at three, one to hand and the others to the bottom
    @Test
    fun prodigyKickedTakesOneOfTheTopThree() = scenario {
        alice.battlefield("Forest", "Forest", "Island", "Island")
        alice.hand(prodigy)
        alice.library(runt, sentry, relic, "Swamp")
        alice.casts(prodigy, kicked = 1)
        answerAll(prefer = listOf(sentry))
        expect {
            alice.hand has sentry
            alice.library.size shouldBe 3
            alice.library.names.first() shouldBe "Swamp"
        }
    }

    @Test
    fun prodigyUnkickedLooksAtNothing() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(prodigy)
        alice.library(runt, sentry, relic)
        alice.casts(prodigy, kicked = 0)
        resolveAll()
        expect {
            alice.library.size shouldBe 3
            alice.battlefield has prodigy
        }
    }

    // CR 702.33d, 603.7: kicked, each creature of yours that dies this turn draws a card
    @Test
    fun rageKickedDrawsWhenACreatureDies() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Swamp", runt)
        alice.hand(rage, zap)
        alice.library("Forest", "Plains")
        alice.casts(rage, kicked = 1)
        resolveAll()
        expect { runt.power shouldBe 4 }
        alice.casts(zap, target = runt)
        resolveAll()
        expect {
            runt.zone shouldBe Zone.Graveyard
            alice.hand has "Forest"
        }
    }

    // CR 603.7: unkicked, no delayed trigger exists, so a death draws nothing
    @Test
    fun rageUnkickedDrawsNothing() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", runt)
        alice.library("Forest", "Plains")
        alice.hand(rage, zap)
        alice.casts(rage, kicked = 0)
        resolveAll()
        alice.casts(zap, target = runt)
        resolveAll()
        expect {
            runt.zone shouldBe Zone.Graveyard
            alice.hand.size shouldBe 0
        }
    }

    // CR 702.33d, 603.4: kicked, it gets +1/+1 and haste until end of turn
    @Test
    fun hereticKickedGetsPumpedAndHasHaste() = scenario {
        alice.battlefield("Forest", "Forest", "Mountain")
        alice.hand(heretic)
        alice.casts(heretic, kicked = 1)
        resolveAll()
        expect {
            heretic.power shouldBe 4
            heretic.toughness shouldBe 3
            heretic hasKeyword Keyword.Haste.kind
        }
    }

    @Test
    fun hereticUnkickedIsPlain() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(heretic)
        alice.casts(heretic, kicked = 0)
        resolveAll()
        expect {
            heretic.power shouldBe 3
            assertTrue(!heretic.view.characteristics!!.hasKeyword(Keyword.Haste.kind), "no haste")
        }
    }
}
