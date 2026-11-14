package arcanum.engine.sets.mh3

import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.ObjectId
import kotlin.test.Test

/**
 * The MH3 spell-cost cards of chunk 05, played: the invented names carry the oracle text of Archway of Innovation, Bloodsoaked Insight,
 * Deem Inferior, Disruptor Flute, Party Thrasher, Sage of the Unknowable and Urza's Incubator (costs cut where they don't change play).
 */
class Mh3Chunk05SpellCostsScenariosTest {
    private val arch = Cards.invent("Arch of Ingenuity", null, "Land", """
        This land enters tapped unless you control an Island.
        {T}: Add {U}.
        {U}, {T}: The next spell you cast this turn has improvise. (Your artifacts can help cast that spell. Each artifact you tap after you're done activating mana abilities pays for {1}.)
    """)
    private val revelation = Cards.invent("Crimson Revelation", "{5}{B/R}{B/R}", "Sorcery", """
        This spell costs {1} less to cast for each 1 life your opponents have lost this turn.
        Target opponent exiles the top three cards of their library. Until the end of your next turn, you may play those cards. If you cast a spell this way, mana of any type can be spent to cast it.
    """)
    private val lesser = Cards.invent("Rule Lesser", "{3}{U}", "Sorcery", """
        This spell costs {1} less to cast for each card you've drawn this turn.
        The owner of target nonland permanent puts it into their library second from the top or on the bottom.
    """)
    private val pipe = Cards.invent("Silencer Pipe", "{2}", "Artifact", """
        Flash
        As this artifact enters, choose a card name.
        Spells with the chosen name cost {3} more to cast.
        Activated abilities of sources with the chosen name can't be activated unless they're mana abilities.
    """)
    private val basher = Cards.invent("Revel Basher", "{1}{R}", "Creature — Lizard Wizard", """
        Noncreature spells you cast from exile have convoke. (Each creature you tap while casting a noncreature spell from exile pays for {1} or one mana of that creature's color.)
        At the beginning of your first main phase, you may discard a card. If you do, exile the top two cards of your library, then choose one of them. You may play that card this turn.
    """, "1/4")
    private val seer = Cards.invent("Seer of the Unfathomable", "{1}{U}", "Creature — Human Wizard",
        "{T}: Add {C}. Spend this mana only to cast a colorless spell or to activate an ability.", "0/4")
    private val vat = Cards.invent("Tinker's Brood Vat", "{3}", "Artifact", """
        As this artifact enters, choose a creature type.
        Creature spells of the chosen type cost {2} less to cast.
    """)

    private val trinket = Cards.invent("Lattice Trinket", "{1}", "Artifact", "")
    private val ponder = Cards.invent("Lattice Musing", "{2}{U}", "Sorcery", "Draw a card.")
    private val glance = Cards.invent("Lattice Glance", "{1}{U}", "Sorcery", "Draw a card.")
    private val insight = Cards.invent("Lattice Insight", "{U}", "Sorcery", "Draw two cards.")
    private val drain = Cards.invent("Lattice Drain", "{B}", "Sorcery", "Each opponent loses 2 life.")
    private val mend = Cards.invent("Lattice Mending", "{G}", "Sorcery", "You gain 3 life.")
    private val shock = Cards.invent("Lattice Shock", "{R}", "Instant", "Lattice Shock deals 2 damage to any target.")
    private val blast = Cards.invent("Lattice Blast", "{2}{R}", "Sorcery", "Lattice Blast deals 3 damage to target player.")
    private val bear = Cards.invent("Lattice Bear", "{2}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Lattice Ox", "{2}{G}", "Creature — Ox", "", "2/2")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    // CR 702.126a: the activation gives the next spell improvise; two artifacts pay its {2}
    @Test
    fun archGivesTheNextSpellImprovise() = scenario {
        alice.battlefield(arch, "Island", "Island", trinket, trinket)
        alice.hand(ponder)
        alice.library("Island")
        alice.activates(arch, ability = 1, paying = listOf("Island"))
        resolveAll()
        alice.casts(ponder)
        resolveAll()
        expect {
            alice.graveyard has ponder
            alice.hand has "Island"
        }
    }

    // CR 702.126a: without the activation the spell has no improvise, and one Island can't pay {2}{U}
    @Test
    fun withoutTheArchArtifactsDontPay() = scenario {
        alice.battlefield("Island", trinket, trinket)
        alice.hand(ponder)
        alice.casts(ponder)
        expect { decision<Decision.ManaPayment>().suggestion shouldBe emptyList() }
    }

    // CR 601.2f: 4 life lost by two opponents makes it cost {B/R}{B/R}{1}; CR 609.4b: the target opponent's exiled green spell is cast with
    // black mana; the other opponent's library is untouched (3 players)
    @Test
    fun revelationCostsLessAndPlaysTheOpponentsCardsWithAnyMana() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(drain, revelation)
        carol.library(mend, "Forest", "Forest", "Forest")
        bob.library("Mountain", "Mountain", "Mountain")
        alice.casts(drain)
        resolveAll()
        alice.casts(revelation, target = "carol")
        resolveAll()
        expect {
            carol.exile has mend
            carol.library.size shouldBe 1
            bob.library.size shouldBe 3
        }
        alice.casts(mend)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    // CR 601.2f: without life lost it costs all seven
    @Test
    fun revelationCostsSevenWithoutLifeLost() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(revelation)
        alice.casts(revelation, target = "bob")
        expect { decision<Decision.ManaPayment>().suggestion shouldBe emptyList() }
    }

    // CR 601.2f, 121.2: two cards drawn make it cost {1}{U}; its owner puts the creature second from the top (CR 401.4)
    @Test
    fun lesserCostsLessForEachCardDrawn() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(insight, lesser)
        alice.library("Island", "Island")
        bob.battlefield(bear)
        bob.library("Forest", "Forest")
        alice.casts(insight)
        resolveAll()
        alice.casts(lesser, target = bear)
        resolveAll()
        answer(Answer.Option("second from the top"))
        expect { bob.library.names shouldBe listOf("Forest", bear, "Forest") }
    }

    // CR 601.2f: no card drawn, two Islands can't pay {3}{U}
    @Test
    fun lesserCostsFullWithoutDraws() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(lesser)
        bob.battlefield(bear)
        alice.casts(lesser, target = bear)
        expect { decision<Decision.ManaPayment>().suggestion shouldBe emptyList() }
    }

    // CR 702.8a, 201.3, 601.2f: flashed in on the opponent's turn naming their instant; it costs {3}{R} for them
    @Test
    fun pipeTaxesTheNamedSpellOfAnyPlayer() = scenario {
        turn(number = 2, active = bob, priority = alice)
        alice.battlefield("Plains", "Plains")
        alice.hand(pipe)
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(shock)
        alice.casts(pipe)
        resolveAll()
        answer(Answer.CardName(shock))
        bob.casts(shock, target = "alice")
        expect { decision<Decision.ManaPayment>().suggestion shouldBe emptyList() }
    }

    // CR 601.2f: with four Mountains the taxed spell is cast
    @Test
    fun pipeTaxIsPayable() = scenario {
        turn(number = 2, active = bob, priority = alice)
        alice.battlefield("Plains", "Plains")
        alice.hand(pipe)
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        bob.hand(shock)
        alice.casts(pipe)
        resolveAll()
        answer(Answer.CardName(shock))
        bob.casts(shock, target = "alice")
        resolveAll()
        expect { alice.life shouldBe 18 }
    }

    // CR 505.1a, 603.2: the first main phase trigger; discard, exile two, choose one; CR 702.51a, 601.2a: cast from exile it has convoke,
    // two creatures pay its {2}
    @Test
    fun basherConvokesASpellCastFromExile() = scenario {
        turn(number = 2, active = alice, step = TurnStep.Upkeep)
        alice.battlefield(basher, bear, "Mountain")
        alice.hand("Forest")
        alice.library("Island", blast, "Island", "Island")
        advanceTo(TurnStep.PrecombatMain)
        resolveAll()
        answer(Answer.YesNo(true))
        resolveAll()
        alice.chooses("Forest")
        // the exiled card is a new object (CR 400.7), offered before the move is shown
        answer(Answer.Objects(listOf((card(blast) as ObjectId.Card).next())))
        resolveAll()
        alice.casts(blast, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    // CR 702.51a: from the hand the same spell has no convoke
    @Test
    fun basherGivesNoConvokeFromTheHand() = scenario {
        alice.battlefield(basher, bear, "Mountain")
        alice.hand(blast)
        alice.casts(blast, target = "bob")
        expect { decision<Decision.ManaPayment>().suggestion shouldBe emptyList() }
    }

    // CR 106.6: its {C} pays for a colorless spell
    @Test
    fun seerManaCastsAColorlessSpell() = scenario {
        alice.battlefield(seer)
        alice.hand(trinket)
        alice.casts(trinket)
        resolveAll()
        expect { alice.battlefield has trinket }
    }

    // CR 106.6: but not the generic part of a blue spell
    @Test
    fun seerManaCantCastAColoredSpell() = scenario {
        alice.battlefield(seer, "Island")
        alice.hand(glance)
        alice.casts(glance)
        expect { decision<Decision.ManaPayment>().suggestion shouldBe emptyList() }
    }

    // CR 601.2f, 614.12: Bears cost {2} less for every player (here the opponent, 3 players); an Ox still costs {2}{G}
    @Test
    fun vatMakesTheChosenTypeCheaperForEveryone() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(vat)
        bob.battlefield("Forest")
        bob.hand(bear, ox)
        alice.library("Plains")
        bob.library("Forest")
        carol.library("Forest")
        alice.casts(vat)
        resolveAll()
        answer(Answer.SubtypeChoice(Subtype("Bear")))
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.casts(ox)
        expect { decision<Decision.ManaPayment>().suggestion shouldBe emptyList() }
    }

    @Test
    fun vatChosenTypeCostsOnlyItsColoredMana() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(vat)
        bob.battlefield("Forest")
        bob.hand(bear)
        alice.library("Plains")
        bob.library("Forest")
        carol.library("Forest")
        alice.casts(vat)
        resolveAll()
        answer(Answer.SubtypeChoice(Subtype("Bear")))
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        bob.casts(bear)
        resolveAll()
        expect { bob.battlefield has bear }
    }
}
