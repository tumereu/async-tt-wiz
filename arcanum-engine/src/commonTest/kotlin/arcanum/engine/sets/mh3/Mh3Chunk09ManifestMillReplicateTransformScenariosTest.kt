package arcanum.engine.sets.mh3

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.dft.payYes
import arcanum.engine.sets.fin.backFaceUp
import arcanum.engine.sets.fra.loyalty
import arcanum.engine.state.ObjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The MH3 cards of chunk 09, played: the invented names carry the oracle text of Kozilek, the Broken Reality, Guardian of the Forgotten,
 * Grist, Voracious Larva, Ripples of Undeath, Reiterating Bolt, Kozilek's Command, Ajani, Nacatl Pariah, Ral, Monsoon Mage and Sorin of House
 * Markov.
 */
class Mh3Chunk09ManifestMillReplicateTransformScenariosTest {
    private val vorzhak = Cards.invent("Vorzhak, the Shattered Truth", "{9}", "Legendary Creature — Eldrazi", """
        When you cast this spell, up to two target players each manifest two cards from their hands. For each card manifested this way, you draw a card. (To manifest a card, put it onto the battlefield face down as a 2/2 creature. Turn it face up any time for its mana cost if it's a creature card.)
        Other colorless creatures you control get +3/+2.
    """, "9/9")
    private val warden = Cards.invent("Warden of the Unremembered", "{3}{W}", "Creature — Elephant Warrior", """
        Vigilance
        Whenever a modified creature you control dies, manifest the top card of your library. (Equipment, Auras you control, and counters are modifications. To manifest a card, put it onto the battlefield face down as a 2/2 creature. Turn it face up any time for its mana cost if it's a creature card.)
    """, "4/4")
    private val grindle = Cards.inventFaces("Transform",
        face("Grindle, Ravenous Grub", "{G}", "Legendary Creature — Insect", """
            Deathtouch
            Whenever Grindle or another creature you control enters, if it entered from your graveyard or you cast it from your graveyard, you may pay {G}. If you do, exile Grindle, then return it to the battlefield transformed under its owner's control.
        """, "1/2", null, null),
        face("Grindle, the Plague Host", null, "Legendary Planeswalker — Grist", """
            [+1]: Create a 1/1 black and green Insect creature token, then mill two cards. Put a deathtouch counter on the token if a black card was milled this way.
            [−2]: Destroy target artifact or enchantment.
            [−6]: For each creature card in your graveyard, create a token that's a copy of it, except it's a 1/1 black and green Insect.
        """, null, "3", "BG"))
    private val ripples = Cards.invent("Ripples of the Grave", "{1}{B}", "Enchantment",
        "At the beginning of your first main phase, mill three cards. Then you may pay {1} and 3 life. If you do, put a card from among those cards into your hand.")
    private val bolt = Cards.invent("Recurring Bolt", "{1}{R}", "Sorcery", """
        Replicate—Pay {E}{E}{E}. (When you cast this spell, copy it for each time you paid its replicate cost. You may choose new targets for the copies.)
        Recurring Bolt deals 3 damage to target creature or planeswalker.
    """)
    private val decree = Cards.invent("Vorzhak's Decree", "{X}{C}{C}", "Kindred Instant — Eldrazi", """
        Choose two —
        • Target player creates X 0/1 colorless Eldrazi Spawn creature tokens with "Sacrifice this token: Add {C}."
        • Target player scries X, then draws a card.
        • Exile target creature with mana value X or less.
        • Exile up to X target cards from graveyards.
    """)
    private val avani = Cards.inventFaces("Transform",
        face("Avani, Pridemane Exile", "{1}{W}", "Legendary Creature — Cat Warrior", """
            When Avani enters, create a 2/1 white Cat Warrior creature token.
            Whenever one or more other Cats you control die, you may exile Avani, then return him to the battlefield transformed under his owner's control.
        """, "1/2", null, null),
        face("Avani, Pridemane Avenger", null, "Legendary Planeswalker — Ajani", """
            [+2]: Put a +1/+1 counter on each Cat you control.
            [0]: Create a 2/1 white Cat Warrior creature token. When you do, if you control a red permanent other than Avani, he deals damage equal to the number of creatures you control to any target.
            [−4]: Each opponent chooses an artifact, a creature, an enchantment, and a planeswalker from among the nonland permanents they control, then sacrifices the rest.
        """, null, "3", "RW"))
    private val rhal = Cards.inventFaces("Transform",
        face("Rhal, Storm Acolyte", "{1}{R}", "Legendary Creature — Human Wizard", """
            Instant and sorcery spells you cast cost {1} less to cast.
            Whenever you cast an instant or sorcery spell during your turn, flip a coin. If you lose the flip, Rhal deals 1 damage to you. If you win the flip, you may exile Rhal. If you do, return him to the battlefield transformed under his owner's control.
        """, "1/3", null, null),
        face("Rhal, Leyline Savant", null, "Legendary Planeswalker — Ral", """
            Rhal enters with an additional loyalty counter on him for each instant and sorcery spell you've cast this turn.
            [+1]: Until your next turn, instant and sorcery spells you cast cost {1} less to cast.
            [−2]: Rhal deals 2 damage divided as you choose among one or two targets. Draw a card if you control a blue permanent other than Rhal.
            [−8]: Exile the top eight cards of your library. You may cast instant and sorcery spells from among them this turn without paying their mana costs.
        """, null, "2", "UR"))
    private val sorvin = Cards.inventFaces("Transform",
        face("Sorvin of House Markas", "{1}{B}", "Legendary Creature — Human Noble", """
            Lifelink
            Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
            At the beginning of each of your postcombat main phases, if you gained 3 or more life this turn, exile Sorvin, then return him to the battlefield transformed under his owner's control.
        """, "1/4", null, null),
        face("Sorvin, Ravenous Fledgling", null, "Legendary Planeswalker — Sorin", """
            Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
            [+2]: Create a Food token.
            [−1]: Sorvin deals damage equal to the amount of life you gained this turn to any target.
            [−6]: Gain control of target creature. It becomes a Vampire in addition to its other types. Put a lifelink counter on it if you control a white permanent other than that creature or Sorvin.
        """, null, "3", "WB"))

    private val reanimate = Cards.invent("Rise From Cinders", "{1}{B}", "Sorcery", "Return target creature card from your graveyard to the battlefield.")
    private val pilfer = Cards.invent("Grave Pilfer", "{1}{B}", "Sorcery", "Put target creature card from a graveyard onto the battlefield under your control.")
    private val rat = Cards.invent("Murk Rat", "{B}", "Creature — Rat", "", "1/1")
    private val ward = Cards.invent("Quiet Ward", "{1}{W}", "Enchantment", "")
    private val draught = Cards.invent("Healing Draught", "{W}", "Instant", "You gain 3 life.")
    private val squire = Cards.invent("Dawn Squire", "{W}", "Creature — Human Soldier", "", "1/1")
    private val lance = Cards.invent("Pyre Lance", "{1}{R}", "Instant", "Pyre Lance deals 1 damage to any target.")
    private val flats = Cards.invent("Hollowstone Flats", null, "Land", "{T}: Add {C}.")
    private val cinder = Cards.invent("Stray Cinder", "{R}", "Instant", "Stray Cinder deals 2 damage to any target.")
    private val bear = Cards.invent("Reaping Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val pest = Cards.invent("Ember Pest", "{1}{R}", "Creature — Lizard", "", "2/1")
    private val ogre = Cards.invent("Big Ogre", "{4}{R}", "Creature — Ogre", "", "4/4")
    private val anvil = Cards.invent("Rust Anvil", "{3}", "Artifact", "")

    private fun Scenario.faceName(name: String): String? = view()[card(name, zone = Zone.Battlefield)]?.characteristics?.name
    private fun Scenario.faceDown(): List<ObjectId> = state.battlefield.filter { state.obj(it)!!.faceDown }
    private fun Scenario.controllerOf(id: ObjectId) = view()[id]?.controller
    /** Answers open "may" and yes/no decisions with yes and target decisions with [picks] in turn, resolving on. */
    private fun Scenario.yesAndTarget(vararg picks: String) {
        var next = 0
        var guard = 20
        while (guard-- > 0) {
            when (pending) {
                is Decision.May -> answer(Answer.YesNo(true))
                is Decision.Targets -> targets(picks[next++])
                null -> if (state.stack.isEmpty()) return else resolveAll()
                else -> return
            }
        }
    }

    // CR 701.40a, 101.4: two target players (of three) each choose two cards in their hand and manifest them, under their own control; you draw
    // one card for each (CR 603.6: it triggers as it is cast)
    @Test
    fun manifestsFromTwoPlayersHands() = scenario(players = 3) {
        alice.battlefield(*Array(9) { flats })
        alice.hand(vorzhak)
        alice.library("Island", "Island", "Island", "Island", "Island")
        bob.hand(bear, pest, ogre)
        carol.hand(rat, anvil)
        alice.casts(vorzhak)
        if (pending !is Decision.Targets) resolveAll()
        targets("bob", "carol")
        resolveAll()
        while (pending is Decision.ChooseObjects) {
            val d = pending as Decision.ChooseObjects
            if (d.player == bob.id) bob.chooses(bear, ogre) else carol.chooses(rat, anvil)
        }
        resolveAll()
        expect {
            bob.hand.names shouldBe listOf(pest)
            carol.hand.size shouldBe 0
            alice.hand.size shouldBe 4
            alice.battlefield has vorzhak
        }
        val down = faceDown()
        assertEquals(4, down.size)
        assertEquals(2, down.count { controllerOf(it) == bob.id })
        assertEquals(2, down.count { controllerOf(it) == carol.id })
        assertTrue(down.all { view()[it]?.characteristics?.power == 2 }, "face-down 2/2s of other players don't get Vorzhak's +3/+2")
    }

    // CR 700.9, 603.10a: the creature with a +1/+1 counter was modified as it died; CR 701.40a: the top card of the library is manifested
    @Test
    fun modifiedCreatureDiesManifest() = scenario {
        alice.battlefield(warden, "Mountain")
        alice.battlefield(pest, counters = listOf(CounterKind.PowerToughness(1, 1) to 1))
        alice.hand(cinder)
        alice.library(ogre)
        alice.casts(cinder, target = pest)
        resolveAll()
        expect {
            alice.graveyard has pest
            alice.library.size shouldBe 0
        }
        assertEquals(listOf(alice.id), faceDown().map { controllerOf(it) })
    }

    // CR 603.2, 404.2: a creature card returned from your own graveyard: you may pay {G} and Grindle returns transformed (CR 701.27a)
    @Test
    fun grindleTransformsWhenACreatureReturnsFromYourGraveyard() = scenario {
        alice.battlefield(grindle, "Swamp", "Swamp", "Forest")
        alice.graveyard(bear)
        alice.hand(reanimate)
        alice.casts(reanimate, target = bear)
        resolveAll()
        payYes()
        assertEquals("Grindle, the Plague Host", faceName(grindle))
        assertEquals(3, loyalty(grindle))
        expect { alice.battlefield has bear }
    }

    // CR 404.2: a creature card from another player's graveyard didn't come from your graveyard: no trigger
    @Test
    fun grindleIgnoresCreaturesFromOtherGraveyards() = scenario {
        alice.battlefield(grindle, "Swamp", "Swamp", "Forest")
        bob.graveyard(bear)
        alice.hand(pilfer)
        alice.casts(pilfer, target = bear)
        resolveAll()
        expect {
            bear.controller shouldBe alice.id
            state.stack.size shouldBe 0
        }
        assertEquals("Grindle, Ravenous Grub", faceName(grindle))
    }

    // CR 701.17a: of the two milled cards one is black: the token gets a deathtouch counter (CR 122.1b)
    @Test
    fun grindleInsectGetsDeathtouchWhenABlackCardIsMilled() = scenario {
        alice.battlefield(grindle, counters = listOf(CounterKind.Loyalty to 3))
        backFaceUp(grindle)
        alice.library("Island", rat, "Island")
        alice.activates(grindle, ability = 0)
        resolveAll()
        expect {
            alice.graveyard has rat
            "Insect Token".counters(CounterKind.Keyword(Keyword.Deathtouch)) shouldBe 1
            "Insect Token" hasKeyword Keyword.Deathtouch.kind
        }
    }

    @Test
    fun grindleInsectWithoutCounterWhenNoBlackCardIsMilled() = scenario {
        alice.battlefield(grindle, counters = listOf(CounterKind.Loyalty to 3))
        backFaceUp(grindle)
        alice.library("Island", "Swamp", rat)
        alice.activates(grindle, ability = 0)
        resolveAll()
        expect { "Insect Token".counters(CounterKind.Keyword(Keyword.Deathtouch)) shouldBe 0 }
    }

    // CR 707.9b: each copy is a 1/1 black and green Insect, keeping the copied card's name; CR 704.5i: with no loyalty left Grindle itself is
    // in the graveyard as the ability resolves, a creature card (its front face, CR 712.8a), and is copied too
    @Test
    fun grindleCopiesEachCreatureCardAsInsects() = scenario {
        alice.battlefield(grindle, counters = listOf(CounterKind.Loyalty to 6))
        backFaceUp(grindle)
        alice.graveyard(bear, ogre, "Island")
        alice.activates(grindle, ability = 2)
        resolveAll()
        val tokens = state.battlefield.filter { state.obj(it)!!.isToken }.map { view()[it]!!.characteristics!! }
        assertEquals(setOf(bear, ogre, "Grindle, Ravenous Grub"), tokens.map { it.name }.toSet())
        assertTrue(tokens.all { it.power == 1 && it.toughness == 1 && it.colors.toSet() == setOf(Color.Black, Color.Green) && it.subtypes == listOf(Subtype("Insect")) },
            "tokens: $tokens")
        expect { alice.graveyard.size shouldBe 4 }
    }

    // CR 118.12, 701.17a: pay {1} and 3 life, then one of the three milled cards goes to your hand
    @Test
    fun ripplesPaysForACardFromTheMilledOnes() = scenario {
        alice.battlefield(ripples, "Swamp")
        alice.library("Island", bear, ogre, pest, "Island")
        bob.library("Island", "Island")
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        payYes()
        alice.chooses(ogre)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf("Island", ogre)
            alice.life shouldBe 17
            alice.graveyard.names.toSet() shouldBe setOf(bear, pest)
            alice.library.names shouldBe listOf("Island")
        }
    }

    // CR 702.56a, 107.14: replicate paid twice with six energy: three bolts, each its own target
    @Test
    fun replicatePaidWithEnergy() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.counters(CounterKind.Energy, 6)
        alice.hand(bolt)
        bob.battlefield(bear, pest, ogre)
        alice.casts(bolt, target = ogre, kicked = 2)
        yesAndTarget(bear, pest)
        resolveAll()
        expect {
            alice.counters(CounterKind.Energy) shouldBe 0
            bob.graveyard has bear
            bob.graveyard has pest
            ogre.zone shouldBe Zone.Battlefield
        }
    }

    // CR 107.3a, 700.2: with X = 2 the chosen player creates two Spawn and up to two cards in graveyards are exiled
    @Test
    fun decreeModesUseX() = scenario(players = 3) {
        alice.battlefield(flats, flats, flats, flats)
        alice.hand(decree)
        carol.graveyard(bear, ogre)
        alice.casts(decree, x = 2, modes = listOf(0, 3), targets = listOf("bob", bear, ogre))
        resolveAll()
        expect {
            bob.battlefield.names shouldBe listOf("Eldrazi Spawn Token", "Eldrazi Spawn Token")
            bear.zone shouldBe Zone.Exile
            ogre.zone shouldBe Zone.Exile
        }
    }

    // CR 603.4, 603.12: with a red permanent the reflexive trigger deals damage equal to the creatures you control (with the new token: 3)
    @Test
    fun avaniZeroDealsDamageWithARedPermanent() = scenario {
        alice.battlefield(avani, counters = listOf(CounterKind.Loyalty to 3))
        backFaceUp(avani)
        alice.battlefield(pest, bear)
        alice.activates(avani, ability = 1)
        resolveAll()
        targets("bob")
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.battlefield has "Cat Warrior Token"
        }
    }

    // CR 603.4: without another red permanent it doesn't trigger at all
    @Test
    fun avaniZeroWithoutRedPermanentDoesNotTrigger() = scenario {
        alice.battlefield(avani, counters = listOf(CounterKind.Loyalty to 3))
        backFaceUp(avani)
        alice.battlefield(bear)
        alice.activates(avani, ability = 1)
        resolveAll()
        expect {
            bob.life shouldBe 20
            alice.battlefield has "Cat Warrior Token"
            state.stack.size shouldBe 0
        }
    }

    // CR 701.21a, 101.4: each opponent (two) keeps one artifact, creature, enchantment and planeswalker among their nonland permanents and
    // sacrifices the rest; lands and Avani's controller's permanents stay
    @Test
    fun avaniMinusFourEachOpponentKeepsOneOfEachType() = scenario(players = 3) {
        alice.battlefield(avani, counters = listOf(CounterKind.Loyalty to 5))
        backFaceUp(avani)
        alice.battlefield(ogre)
        bob.battlefield(anvil, bear, ogre, ward, "Island")
        carol.battlefield(pest, rat, anvil)
        alice.activates(avani, ability = 2)
        resolveAll()
        while (pending is Decision.ChooseObjects) {
            val d = pending as Decision.ChooseObjects
            if (d.player == bob.id) bob.chooses(bear) else carol.chooses(rat)
        }
        resolveAll()
        expect {
            bob.battlefield.names.toSet() shouldBe setOf(anvil, bear, ward, "Island")
            carol.battlefield.names.toSet() shouldBe setOf(rat, anvil)
            alice.battlefield has ogre
        }
    }

    // CR 306.5b, 614.1c: returned transformed after one instant this turn, it enters with 2 + 1 loyalty
    @Test
    fun rhalEntersWithLoyaltyForSpellsCast() = scenario(seed = 2) {
        alice.battlefield(rhal, "Mountain")
        alice.hand(cinder)
        alice.casts(cinder, target = "bob")
        resolveAll()
        decision<Decision.May>()
        yesAndTarget()
        resolveAll()
        assertEquals("Rhal, Leyline Savant", faceName(rhal))
        assertEquals(3, loyalty(rhal))
        expect { bob.life shouldBe 18 }
    }

    // CR 611.2a, 601.2f: after [+1] a {1}{R} instant costs {R}
    @Test
    fun rhalPlusOneReducesCosts() = scenario {
        alice.battlefield(rhal, counters = listOf(CounterKind.Loyalty to 2))
        backFaceUp(rhal)
        alice.battlefield("Mountain")
        alice.hand(lance)
        alice.activates(rhal, ability = 0)
        resolveAll()
        alice.casts(lance, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 19
            "Mountain".tapped shouldBe true
        }
        assertEquals(3, loyalty(rhal))
    }

    // CR 603.4, 505.1a: having gained 3 life, at the beginning of the postcombat main phase Sorvin returns transformed
    @Test
    fun sorvinTransformsAfterGainingThreeLife() = scenario {
        alice.battlefield(sorvin, "Plains")
        alice.hand(draught)
        alice.casts(draught)
        if (pending is Decision.May) answer(Answer.YesNo(false))
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        resolveAll()
        assertEquals("Sorvin, Ravenous Fledgling", faceName(sorvin))
        expect { alice.life shouldBe 23 }
    }

    @Test
    fun sorvinStaysWithoutLifeGained() = scenario {
        alice.battlefield(sorvin)
        advanceTo(TurnStep.PostcombatMain)
        resolveAll()
        assertEquals("Sorvin of House Markas", faceName(sorvin))
    }

    // CR 109.4: the lifelink counter only with a white permanent other than the stolen creature and Sorvin
    @Test
    fun sorvinMinusSixWithAnotherWhitePermanent() = scenario {
        alice.battlefield(sorvin, counters = listOf(CounterKind.Loyalty to 6))
        backFaceUp(sorvin)
        alice.battlefield(squire)
        bob.battlefield(ogre)
        alice.activates(sorvin, ability = 2, target = ogre)
        resolveAll()
        expect {
            ogre.controller shouldBe alice.id
            ogre.counters(CounterKind.Keyword(Keyword.Lifelink)) shouldBe 1
            ogre.subtypes.contains(Subtype("Vampire")) shouldBe true
            ogre.types.contains(CardType.Creature) shouldBe true
        }
    }

    @Test
    fun sorvinMinusSixTheStolenWhiteCreatureDoesNotCount() = scenario {
        alice.battlefield(sorvin, counters = listOf(CounterKind.Loyalty to 6))
        backFaceUp(sorvin)
        bob.battlefield(squire)
        alice.activates(sorvin, ability = 2, target = squire)
        resolveAll()
        expect {
            squire.controller shouldBe alice.id
            squire.counters(CounterKind.Keyword(Keyword.Lifelink)) shouldBe 0
        }
    }
}
