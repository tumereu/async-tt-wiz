package arcanum.engine.sets.rvr

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ravnica Remastered chunk 07 in play: a creature that shuffles itself into its owner's library, a split card whose second half may
 * put counters before a global -4/-4, a spell that checks whether a creature died this turn, a creature that returns itself to hand
 * when targeted, a mana-value-X target, a sacrifice-three-white-creatures cost, a cast trigger returning any permanent, an exile
 * returned at the next end step and a split card putting a creature on top of its owner's library or making a token. Eternal Repose's
 * graveyard replacement (Rest in Peace) is played in the RTR rules scenarios. */
class RvrChunk07MoveScenariosTest {
    private val sphinx = Cards.invent("Brinegate Sphinx", "{4}{U}{U}", "Creature — Sphinx", """
        Flying
        {U}: This creature's owner shuffles it into their library.
    """, "5/5")
    private val findFinality = Cards.inventFaces("Split",
        face("Regrave", "{B/G}{B/G}", "Sorcery", "Return up to two target creature cards from your graveyard to your hand.", null, null, null),
        face("Rotwave", "{4}{B}{G}", "Sorcery", "You may put two +1/+1 counters on a creature you control. Then all creatures get -4/-4 until end of turn.", null, null, null))
    private val snare = Cards.invent("Skyhook Snare", "{1}{G}", "Instant", "Put target creature with flying on the bottom of its owner's library.")
    private val rebirth = Cards.invent("Mossbed Rebirth", "{2}{G}", "Instant",
        "Return target permanent card from your graveyard to your hand. If a creature died this turn, create two 1/1 green Saproling creature tokens.")
    private val weird = Cards.invent("Mirrorhide Weird", "{3}{U/R}", "Creature — Weird", """
        ({U/R} can be paid with either {U} or {R}.)
        When this creature becomes the target of a spell or ability, return this creature to its owner's hand.
    """, "3/3")
    private val surge = Cards.invent("Recall Surge", "{X}{U}", "Instant", "Return target nonland permanent with mana value X to its owner's hand.\nDraw a card.")
    private val council = Cards.invent("Bonetide Council", "{1}{W}{B}", "Legendary Creature — Human Advisor", """
        Sacrifice three white creatures: Exile target creature.
        Whenever another black creature you control dies, create a 1/1 white Spirit creature token with flying.
    """, "2/3")
    private val djinn = Cards.invent("Stormcrown Djinn", "{5}{U}{U}{U}", "Creature — Djinn", """
        Flying
        Whenever you cast a spell, return target permanent to its owner's hand.
    """, "5/5")
    private val staff = Cards.invent("Gleamcoin Staff", "{1}", "Artifact",
        "{2}, Sacrifice this artifact: Exile target creature. Return the exiled card to the battlefield under its owner's control at the beginning of the next end step.")
    private val wardWarden = Cards.inventFaces("Split",
        face("Shelve", "{W/U}{W/U}", "Instant", "Put target attacking or blocking creature on top of its owner's library.", null, null, null),
        face("Sentinel Born", "{3}{W}{U}", "Sorcery", "Create a 4/4 white and blue Sphinx creature token with flying and vigilance.", null, null, null))

    private val giant = Cards.invent("Rvr7 Giant", "{4}{G}", "Creature — Giant", "", "5/5")
    private val brute = Cards.invent("Rvr7 Brute", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val imp = Cards.invent("Rvr7 Imp", "{1}{B}", "Creature — Imp", "", "2/2")
    private val whiteA = Cards.invent("Rvr7 Knight A", "{W}", "Creature — Knight", "", "1/1")
    private val whiteB = Cards.invent("Rvr7 Knight B", "{W}", "Creature — Knight", "", "1/1")
    private val whiteC = Cards.invent("Rvr7 Knight C", "{W}", "Creature — Knight", "", "1/1")
    private val blackImp = Cards.invent("Rvr7 Black Imp", "{B}", "Creature — Imp", "", "1/1")
    private val flier = Cards.invent("Rvr7 Flier", "{1}{U}", "Creature — Bird", "Flying", "1/1")

    /** The shuffle ability moves the Sphinx from the battlefield into its owner's library. */
    // CR 701.24a, 400.7
    @Test
    fun sphinxShufflesItselfIntoItsOwnersLibrary() = scenario {
        alice.battlefield(sphinx, "Island")
        alice.library("Forest")
        alice.activates(sphinx)
        resolveAll()
        expect {
            alice.battlefield lacks sphinx
            alice.library.size shouldBe 2
        }
    }

    /** Rotwave: the 5/5 given two counters survives -4/-4 as a 3/3; the other creatures die (they have no counters). */
    // CR 709.3, 701.3 pattern, 613.4c
    @Test
    fun rotwaveCountersThenMinusFourKillsTheRest() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Forest", "Forest", "Forest", giant, imp)
        bob.battlefield(brute)
        alice.hand(findFinality)
        alice.casts(findFinality, way = CastWay(face = 1))
        resolve()
        alice.chooses(giant)
        resolveAll()
        expect {
            alice.battlefield has giant
            alice.battlefield lacks imp
            bob.battlefield lacks brute
        }
        assertEquals(3, view()[card(giant)]!!.characteristics!!.power)
        assertEquals(3, view()[card(giant)]!!.characteristics!!.toughness)
    }

    /** Declining the counters (choosing no creature) still gives -4/-4: the 5/5 survives as a 1/1 and nothing else lives. */
    // CR 701.3 pattern, 613.4c
    @Test
    fun rotwaveWithNoChosenCreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Forest", "Forest", "Forest", giant, imp)
        alice.hand(findFinality)
        alice.casts(findFinality, way = CastWay(face = 1))
        resolve()
        alice.chooses()
        resolveAll()
        expect {
            alice.battlefield has giant
            alice.battlefield lacks imp
        }
        assertEquals(1, view()[card(giant)]!!.characteristics!!.power)
    }

    /** Regrave returns two creature cards from the graveyard. */
    // CR 709.3
    @Test
    fun regraveReturnsUpToTwoCreatureCards() = scenario {
        alice.battlefield("Swamp", "Forest")
        alice.graveyard(brute, imp, giant)
        alice.hand(findFinality)
        alice.casts(findFinality, targets = listOf(brute, imp))
        resolveAll()
        expect {
            alice.hand has brute
            alice.hand has imp
            alice.graveyard has giant
        }
    }

    /** Only a creature with flying is a legal target (the lone legal target is chosen on its own); the Bird goes to the bottom of Bob's library. */
    // CR 115.1, 701.24
    @Test
    fun snareTargetsOnlyFliers() = scenario {
        alice.battlefield("Forest", "Forest")
        bob.battlefield(flier, brute)
        bob.library("Island", "Swamp")
        alice.hand(snare)
        alice.casts(snare, target = flier)
        resolveAll()
        expect { bob.library.names shouldBe listOf("Island", "Swamp", flier) }
    }

    /** No creature died this turn: only the card returns. */
    // CR 608.2c
    @Test
    fun rebirthWithoutADeathMakesNoTokens() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.graveyard(brute)
        alice.hand(rebirth)
        alice.casts(rebirth, target = brute)
        resolveAll()
        expect {
            alice.hand has brute
            alice.battlefield.names shouldBe listOf("Forest", "Forest", "Forest")
        }
    }

    /** A creature died earlier this turn: two Saprolings too. */
    // CR 700.4, 608.2c
    @Test
    fun rebirthAfterADeathMakesTwoSaprolings() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Mountain")
        alice.graveyard(brute)
        alice.hand(rebirth, shockSpell)
        bob.battlefield(imp)
        alice.casts(shockSpell, target = imp)
        resolveAll()
        alice.casts(rebirth, target = brute)
        resolveAll()
        expect {
            alice.hand has brute
            alice.battlefield.names.count { it == "Saproling Token" } shouldBe 2
        }
    }
    private val shockSpell = Cards.invent("Rvr7 Spark", "{R}", "Instant", "Rvr7 Spark deals 3 damage to target creature.")

    /** Targeting the Weird with Bob's spell returns it to Alice's hand before the spell resolves. */
    // CR 603.2, 115.1
    @Test
    fun weirdReturnsToHandWhenTargeted() = scenario(players = 3) {
        alice.battlefield(weird)
        bob.battlefield("Mountain")
        bob.hand(bobSpark)
        turn(number = 2, active = bob)
        bob.casts(bobSpark, target = weird)
        resolveAll()
        expect {
            alice.hand has weird
            alice.battlefield lacks weird
        }
    }
    private val bobSpark = Cards.invent("Rvr7 Bolt", "{R}", "Instant", "Rvr7 Bolt deals 3 damage to target creature.")

    /** X = 3: the only legal target is the permanent with mana value 3 (the lone legal target is chosen on its own); Alice draws. */
    // CR 107.3, 202.3
    @Test
    fun surgeTargetsManaValueX() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        bob.battlefield(brute, imp)
        alice.library("Forest")
        alice.hand(surge)
        alice.casts(surge, target = brute, x = 3)
        resolveAll()
        expect {
            bob.hand has brute
            bob.battlefield has imp
            alice.hand has "Forest"
        }
    }

    /** Sacrificing three white creatures exiles the target creature. */
    // CR 118.8, 602.2
    @Test
    fun councilSacrificesThreeWhiteCreaturesToExile() = scenario {
        alice.battlefield(council, whiteA, whiteB, whiteC, giant)
        bob.battlefield(brute)
        alice.activates(council, target = brute, choosing = listOf(whiteA, whiteB, whiteC))
        resolveAll()
        expect {
            bob.exile has brute
            alice.graveyard has whiteA
            alice.graveyard has whiteB
            alice.graveyard has whiteC
        }
    }

    /** Another black creature dying makes a 1/1 flying Spirit; a non-black one does not. */
    // CR 700.4, 603.10a
    @Test
    fun councilMakesASpiritWhenABlackCreatureDies() = scenario {
        alice.battlefield(council, blackImp, giant, "Mountain", "Mountain")
        alice.hand(shockSpell, bobSpark)
        alice.casts(shockSpell, target = giant)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Spirit Token" } shouldBe 0 }
        alice.casts(bobSpark, target = blackImp)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Spirit Token" } shouldBe 1
            card("Spirit Token") hasKeyword Keyword.Flying
        }
    }

    /** Casting any spell returns a chosen permanent; Alice returns Bob's creature. */
    // CR 603.2, 601.2i
    @Test
    fun djinnReturnsAPermanentOnEachCast() = scenario {
        alice.battlefield(djinn, "Mountain")
        alice.hand(shockSpell)
        bob.battlefield(brute, imp)
        alice.casts(shockSpell, target = imp)
        resolveAll()
        answer(arcanum.engine.decision.Answer.Targets(listOf(target(brute))))
        resolveAll()
        expect {
            bob.hand has brute
            bob.battlefield lacks imp
        }
    }

    /** The exiled creature comes back at the next end step under its owner's control, not the Staff's controller's. */
    // CR 603.7c, 110.2
    @Test
    fun staffReturnsTheCreatureAtTheNextEndStep() = scenario {
        alice.battlefield(staff, "Island", "Island")
        bob.battlefield(brute)
        alice.activates(staff, target = brute)
        resolveAll()
        expect {
            bob.exile has brute
            alice.battlefield lacks staff
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            bob.battlefield has brute
            alice.battlefield lacks brute
        }
    }

    /** Shelve puts Bob's attacker on top of his library. */
    // CR 506.4, 401.4
    @Test
    fun shelveWithAttackerOnTop() = scenario {
        alice.battlefield("Plains", "Island")
        alice.hand(wardWarden)
        bob.battlefield(brute)
        bob.library("Swamp", "Swamp")
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(brute)
        bob.passes()
        alice.casts(wardWarden, target = brute)
        resolveAll()
        expect { bob.library.names shouldBe listOf(brute, "Swamp", "Swamp") }
    }

    /** Sentinel Born makes a 4/4 white and blue Sphinx with flying and vigilance. */
    // CR 709.3, 111.4
    @Test
    fun sentinelBornMakesASphinx() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Island")
        alice.hand(wardWarden)
        alice.casts(wardWarden, way = CastWay(face = 1))
        resolveAll()
        expect {
            card("Sphinx Token") hasKeyword Keyword.Flying
            card("Sphinx Token") hasKeyword Keyword.Vigilance
        }
    }
}
