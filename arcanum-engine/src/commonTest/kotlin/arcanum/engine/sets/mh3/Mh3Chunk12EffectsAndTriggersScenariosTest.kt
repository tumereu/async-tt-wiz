package arcanum.engine.sets.mh3

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.ltr.hits
import arcanum.engine.sets.ltr.unblocked
import arcanum.engine.sets.spm.loreChapter
import kotlin.test.Test

/**
 * The MH3 cards of chunk 12, played: the invented names carry the oracle text of The Creation of Avacyn, Etched Slith, Kaalia of the Vast,
 * Ocelot Pride, Glimpse the Impossible, Planar Genesis, Eladamri, Korvecdal, The Hunger Tide Rises, Suppression Ray, Aether Revolt,
 * Argent Dais, Laelia, the Blade Reforged, Charitable Levy and Grim Servant.
 */
class Mh3Chunk12EffectsAndTriggersScenariosTest {
    private val seraphs = Cards.invent("The Forging of Seraphs", "{1}{B}{B}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Search your library for a card, exile it face down, then shuffle.
        II — Turn the exiled card face up. If it's a creature card, you lose life equal to its mana value.
        III — You may put the exiled card onto the battlefield if it's a creature card. If you don't put it onto the battlefield, put it into its owner's hand.
    """)
    private val slith = Cards.invent("Graven Slith", "{1}{B}", "Artifact Creature — Phyrexian Slith", """
        Menace
        Whenever this creature deals combat damage to a player, put a +1/+1 counter on it. When you do, you may remove a counter from another target permanent or opponent.
    """, "1/1")
    private val kessa = Cards.invent("Kessa of the Broad Sky", "{1}{R}{W}{B}", "Legendary Creature — Human Cleric", """
        Flying
        Whenever Kessa of the Broad Sky attacks an opponent, you may put an Angel, Demon, or Dragon creature card from your hand onto the battlefield tapped and attacking that opponent.
    """, "2/2")
    private val lynx = Cards.invent("Lynx Vanity", "{W}", "Creature — Cat", """
        First strike, lifelink
        Ascend (If you control ten or more permanents, you get the city's blessing for the rest of the game.)
        At the beginning of your end step, if you gained life this turn, create a 1/1 white Cat creature token. Then if you have the city's blessing, for each token you control that entered this turn, create a token that's a copy of it.
    """, "1/1")
    private val peek = Cards.invent("Peek Beyond Reason", "{2}{R}", "Sorcery",
        "Exile the top three cards of your library. You may play those cards this turn. At the beginning of the next end step, if any of those cards remain exiled, put them into your graveyard, then create a 0/1 colorless Eldrazi Spawn creature token for each card put into your graveyard this way. Those tokens have \"Sacrifice this token: Add {C}.\"")
    private val sprouting = Cards.invent("Planewell Sprouting", "{G}{U}", "Instant",
        "Look at the top four cards of your library. You may put a land card from among them onto the battlefield tapped. If you don't, put a card from among them into your hand. Put the rest on the bottom of your library in a random order.")
    private val korvan = Cards.invent("Korvan, Elf Warden", "{1}{G}{G}", "Legendary Creature — Elf Warrior", """
        You may look at the top card of your library any time.
        You may cast creature spells from the top of your library.
        {G}, {T}, Tap two untapped creatures you control: Reveal a card from your hand or the top card of your library. If you reveal a creature card this way, put it onto the battlefield. Activate only during your turn.
    """, "3/3")
    private val swarm = Cards.invent("The Swarm Tide Swells", "{2}{G}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I, II, III — Create a 1/1 black and green Insect creature token.
        IV — Sacrifice any number of creatures. Search your library and/or graveyard for a creature card with mana value less than or equal to the number of creatures sacrificed this way and put it onto the battlefield. If you search your library this way, shuffle.
    """)
    private val beam = Cards.invent("Quelling Beam", "{3}{W}{U}", "Sorcery",
        "Tap all creatures target player controls. You may pay any amount of {E}. If you do, choose up to that many creatures tapped this way. Put a stun counter on each of them. (If a permanent with a stun counter would become untapped, remove one from it instead.)")
    private val uprising = Cards.invent("Charge Uprising", "{2}{R}{R}", "Enchantment", """
        Revolt — As long as a permanent left the battlefield under your control this turn, if a source you control would deal noncombat damage to an opponent or a permanent an opponent controls, it deals that much damage plus 2 instead.
        Whenever you get one or more {E}, this enchantment deals that much damage to any target.
    """)
    private val plinth = Cards.invent("Silver Plinth", "{1}{W}", "Artifact", """
        This artifact enters with two oil counters on it.
        Whenever two or more creatures attack, put an oil counter on this artifact.
        {2}, {T}, Remove two oil counters from this artifact: Exile another target nonland permanent. Its controller draws two cards.
    """)
    private val lyra = Cards.invent("Lyra, the Reforged Edge", "{2}{R}", "Legendary Creature — Spirit Warrior", """
        Haste
        Whenever Lyra attacks, exile the top card of your library. You may play that card this turn.
        Whenever one or more cards are put into exile from your library and/or your graveyard, put a +1/+1 counter on Lyra.
    """, "2/2")
    private val tithe = Cards.invent("Alms Tithe", "{1}{W}", "Enchantment", """
        Noncreature spells cost {1} more to cast.
        Whenever a player casts a noncreature spell, put a collection counter on this enchantment. Then if there are three or more collection counters on it, sacrifice it. If you do, draw a card, then you may search your library for a Plains card, put it onto the battlefield tapped, then shuffle.
    """)
    private val thrall = Cards.invent("Dour Thrall", "{3}{B}", "Creature — Zombie Warlock", """
        Menace
        When this creature enters, search your library for a card with mana value less than or equal to your devotion to black, reveal it, put it into your hand, then shuffle. You lose 3 life. (Each {B} in the mana costs of permanents you control counts toward your devotion to black.)
    """, "3/2")

    private val bear = Cards.invent("Reaping Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Tallowbone Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val titan = Cards.invent("Hollow Titan", "{7}", "Creature — Eldrazi", "", "7/7")
    private val dragon = Cards.invent("Cinderwing Wyrm", "{4}{R}{R}", "Creature — Dragon", "Flying", "5/5")
    private val anthem = Cards.invent("Glowing Anthem", "{2}{W}", "Enchantment", "Creatures you control get +1/+1.")
    private val cinder = Cards.invent("Stray Cinder", "{R}", "Instant", "Stray Cinder deals 2 damage to any target.")
    private val spark = Cards.invent("Charged Spark", "{R}", "Instant", "You get {E}{E}{E}.")
    private val shade = Cards.invent("Gloomhollow Shade", "{B}{B}", "Creature — Shade", "", "2/2")

    // CR 406.3, 607.2a, 708.8: chapter I exiles the found card face down; chapter II turns it face up and costs its mana value in life;
    // chapter III puts it onto the battlefield
    @Test
    fun seraphsExileFaceDownThenFaceUpThenPutIt() = scenario {
        loreChapter(seraphs, 1, "Island", titan)
        resolveAll()
        alice.chooses(titan)
        resolveAll()
        expect {
            alice.exile has titan
            card(titan).view.obj?.faceDown shouldBe true
        }
        advanceTo(TurnStep.PrecombatMain, turn = 5)
        resolveAll()
        expect {
            card(titan).view.obj?.faceDown shouldBe false
            alice.life shouldBe 13
        }
        advanceTo(TurnStep.PrecombatMain, turn = 7)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            alice.battlefield has titan
            alice.graveyard has seraphs
        }
    }

    // CR 607.2a: a noncreature card exiled by chapter I goes to its owner's hand in chapter III
    @Test
    fun seraphsPutANoncreatureCardIntoTheHand() = scenario {
        loreChapter(seraphs, 1, "Island", anthem)
        resolveAll()
        alice.chooses(anthem)
        resolveAll()
        advanceTo(TurnStep.PrecombatMain, turn = 5)
        resolveAll()
        expect { alice.life shouldBe 20 }
        advanceTo(TurnStep.PrecombatMain, turn = 7)
        resolveAll()
        expect { alice.hand has anthem }
    }

    // CR 603.12, 122.1: the reflexive trigger removes a poison counter from the chosen opponent (three players: Carol, not the one hit)
    @Test
    fun slithRemovesACounterFromAnOpponent() = scenario(players = 3) {
        alice.battlefield(slith)
        carol.counters(CounterKind.Poison, 3)
        hits(slith)
        resolveAll()
        targets("carol")
        resolveAll()
        if (pending is Decision.May) may(true)
        resolveAll()
        expect {
            carol.counters(CounterKind.Poison) shouldBe 2
            slith.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1
        }
    }

    // CR 122.1: or a counter from another permanent
    @Test
    fun slithRemovesACounterFromAPermanent() = scenario {
        alice.battlefield(slith)
        bob.battlefield(bear, counters = listOf(CounterKind.PowerToughness(1, 1) to 2))
        hits(slith)
        resolveAll()
        targets(bear)
        resolveAll()
        if (pending is Decision.May) may(true)
        resolveAll()
        expect { bear.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }

    // CR 508.4: the Dragon enters attacking the opponent Kessa attacks (Carol), not Bob
    @Test
    fun kessaPutsADragonAttackingThatOpponent() = scenario(players = 3) {
        alice.battlefield(kessa)
        alice.hand(dragon)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(kessa, defender = "carol")
        resolveAll()
        if (pending is Decision.May) may(true)
        alice.chooses(dragon)
        resolveAll()
        val attacker = state.combat?.attackers.orEmpty().single { it.id == card(dragon) }
        expect {
            alice.battlefield has dragon
            dragon.tapped shouldBe true
            attacker.target shouldBe target("carol")
        }
    }

    // CR 702.131c, 707.2: with the city's blessing, each token that entered this turn (the new Cat, too) is copied
    @Test
    fun lynxCopiesTheTokensThatEnteredThisTurnWithTheBlessing() = scenario {
        alice.battlefield(lynx, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        unblocked(lynx)
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.life shouldBe 21
            alice.battlefield.names.count { it == "Cat Token" } shouldBe 2
        }
    }

    // CR 603.4: no life gained, no Cat
    @Test
    fun lynxNeedsLifeGained() = scenario {
        alice.battlefield(lynx)
        alice.library("Island")
        bob.library("Island")
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield lacks "Cat Token" }
    }

    // CR 603.7c, 400.7: the card played from exile is a new object; the two still exiled go to the graveyard and make two Spawn
    @Test
    fun peekPutsTheRemainingCardsIntoTheGraveyard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(peek)
        alice.library("Forest", ogre, titan, "Island")
        alice.casts(peek)
        resolveAll()
        alice.plays("Forest")
        expect { alice.exile has ogre }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.graveyard has titan
            alice.battlefield has "Forest"
            alice.battlefield.names.count { it == "Eldrazi Spawn Token" } shouldBe 2
        }
    }

    // CR 401.4: a land put onto the battlefield tapped, nothing into the hand
    @Test
    fun sproutingPutsALand() = scenario {
        alice.battlefield("Forest", "Island")
        alice.hand(sprouting)
        alice.library(ogre, "Forest", bear, titan, "Island")
        alice.casts(sprouting)
        resolveAll()
        alice.chooses("Forest")
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Forest" } shouldBe 2
            alice.hand lacks ogre
            alice.hand lacks bear
            alice.library.size shouldBe 4
        }
    }

    // CR 401.4: no land chosen: a card into the hand instead
    @Test
    fun sproutingOtherwisePutsACardIntoTheHand() = scenario {
        alice.battlefield("Forest", "Island")
        alice.hand(sprouting)
        alice.library(ogre, "Forest", bear, titan, "Island")
        alice.casts(sprouting)
        resolveAll()
        answer(Answer.Objects(emptyList()))
        alice.chooses(bear)
        resolveAll()
        expect {
            alice.hand has bear
            alice.battlefield.names.count { it == "Forest" } shouldBe 1
            alice.library.size shouldBe 4
        }
    }

    // CR 701.20a: the top card of the library may be the one revealed; a creature goes onto the battlefield
    @Test
    fun korvanRevealsTheTopCard() = scenario {
        alice.battlefield(korvan, bear, ogre, "Forest")
        alice.hand(anthem)
        alice.library(titan, "Island")
        alice.activates(korvan, choosing = listOf(bear, ogre))
        resolveAll()
        alice.chooses(titan)
        resolveAll()
        expect { alice.battlefield has titan }
    }

    // CR 701.20a: a noncreature card revealed stays where it was
    @Test
    fun korvanRevealingANoncreatureCardDoesNothing() = scenario {
        alice.battlefield(korvan, bear, ogre, "Forest")
        alice.hand(anthem)
        alice.library(titan, "Island")
        alice.activates(korvan, choosing = listOf(bear, ogre))
        resolveAll()
        alice.chooses(anthem)
        resolveAll()
        expect {
            alice.hand has anthem
            alice.library has titan
        }
    }

    // CR 701.21a, 701.23a: three creatures sacrificed: a creature card with mana value 3 or less from the graveyard
    @Test
    fun swarmSacrificesAndFindsFromTheGraveyard() = scenario {
        alice.battlefield(bear, shade, ogre)
        alice.graveyard(ogre)
        loreChapter(swarm, 4, "Island", titan)
        resolveAll()
        alice.chooses(bear, shade, ogre)
        resolveAll()
        // "library and/or graveyard": both searched (CR 701.23a)
        answer(Answer.Option("library and graveyard"))
        alice.chooses(ogre)
        resolveAll()
        expect {
            alice.battlefield has ogre
            alice.graveyard has bear
            alice.graveyard has shade
            alice.library has titan
        }
    }

    // CR 701.26a, 107.14: two energy paid: two of the creatures tapped this way get a stun counter, not the one already tapped
    @Test
    fun beamStunsAsManyAsTheEnergyPaid() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Island", "Island")
        alice.counters(CounterKind.Energy, 3)
        bob.battlefield(bear, ogre, shade)
        bob.battlefield(titan, tapped = true)
        alice.hand(beam)
        alice.casts(beam, target = "bob")
        resolveAll()
        answer(Answer.Number(2))
        resolveAll()
        alice.chooses(bear, ogre)
        resolveAll()
        expect {
            bear.tapped shouldBe true
            shade.tapped shouldBe true
            bear.counters(CounterKind.Stun) shouldBe 1
            ogre.counters(CounterKind.Stun) shouldBe 1
            shade.counters(CounterKind.Stun) shouldBe 0
            alice.counters(CounterKind.Energy) shouldBe 1
        }
    }

    // CR 122.1: three energy got: 3 damage to any target
    @Test
    fun uprisingDealsTheEnergyGot() = scenario {
        alice.battlefield(uprising, "Mountain")
        bob.battlefield(ogre)
        alice.hand(spark)
        alice.casts(spark)
        resolveAll()
        targets(ogre)
        resolveAll()
        expect {
            alice.graveyard lacks ogre
            bob.graveyard has ogre
        }
    }

    // CR 508.1: two attackers, even the opponent's, add an oil counter; one attacker doesn't
    @Test
    fun plinthCountsTwoOrMoreAttackersOfAnyPlayer() = scenario {
        alice.battlefield(plinth, counters = listOf(CounterKind.Named("oil") to 2))
        bob.battlefield(bear, ogre)
        bob.library("Island")
        alice.library("Island")
        turn(number = 2, active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(bear, ogre)
        resolveAll()
        expect { plinth.counters(CounterKind.Named("oil")) shouldBe 3 }
    }

    @Test
    fun plinthIgnoresALoneAttacker() = scenario {
        alice.battlefield(plinth, counters = listOf(CounterKind.Named("oil") to 2))
        alice.battlefield(bear)
        unblocked(bear)
        resolveAll()
        expect { plinth.counters(CounterKind.Named("oil")) shouldBe 2 }
    }

    // CR 603.2c: Lyra's own attack exiles a card from the library: one counter
    @Test
    fun lyraGrowsWhenACardIsExiledFromTheLibrary() = scenario {
        alice.battlefield(lyra)
        alice.library("Island", "Island")
        unblocked(lyra)
        resolveAll()
        expect { lyra.counters(CounterKind.PowerToughness(1, 1)) shouldBe 1 }
    }

    // CR 701.21a: the third noncreature spell makes three counters: sacrificed, a card drawn and a Plains found
    @Test
    fun titheSacrificesAtThreeCounters() = scenario {
        alice.battlefield(tithe, counters = listOf(CounterKind.Named("collection") to 2))
        alice.battlefield("Mountain", "Mountain")
        alice.hand(cinder)
        alice.library("Island", "Plains")
        bob.battlefield(bear)
        alice.casts(cinder, target = bear)
        resolveAll()
        if (pending is Decision.May) may(true)
        alice.chooses("Plains")
        resolveAll()
        expect {
            alice.graveyard has tithe
            alice.hand has "Island"
            alice.battlefield has "Plains"
            "Plains".tapped shouldBe true
        }
    }

    // CR 701.21a: two counters: not yet
    @Test
    fun titheWaitsForThreeCounters() = scenario {
        alice.battlefield(tithe, counters = listOf(CounterKind.Named("collection") to 1))
        alice.battlefield("Mountain", "Mountain")
        alice.hand(cinder)
        bob.battlefield(bear)
        alice.casts(cinder, target = bear)
        resolveAll()
        expect {
            alice.battlefield has tithe
            tithe.counters(CounterKind.Named("collection")) shouldBe 2
        }
    }

    // CR 700.5: devotion to black 3 (the Thrall's {B} and the Shade's {B}{B}, counted as the trigger resolves): a 3-mana card, not a 7-mana one
    @Test
    fun thrallSearchesUpToTheDevotion() = scenario {
        alice.battlefield(shade, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(thrall)
        alice.library(titan, ogre, "Island")
        alice.casts(thrall)
        resolveAll()
        kotlin.test.assertFalse(card(titan) in (pending as Decision.ChooseObjects).options)
        alice.chooses(ogre)
        resolveAll()
        expect {
            alice.hand has ogre
            alice.life shouldBe 17
        }
    }
}
