package arcanum.engine.sets.lci

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** LCI chunk 13 played: typecycling, Auras (-2/-2, a returning dies trigger, can't attack/block/activate, a mill and -X/-0), Caves and restricted
 * mana. Invented names carry the oracle text. */
class LciChunk13CyclingEnchantManaScenariosTest {
    private val hulkasaur = Cards.invent("Tidal Hulkasaur", "{5}{U}{U}", "Creature — Dinosaur", """
        Ward {3}
        Islandcycling {2} ({2}, Discard this card: Search your library for an Island card, reveal it, put it into your hand, then shuffle.)
    """, "6/7")
    private val burden = Cards.invent("Withering Burden", "{B}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets -2/-2.
    """)
    private val resilience = Cards.invent("Grave Resilience", "{1}{B}", "Enchantment — Aura", """
        Flash
        Enchant creature
        Enchanted creature gets +2/+0.
        When enchanted creature dies, return it to the battlefield tapped under its owner's control.
    """)
    private val stonebind = Cards.invent("Stonebind", "{1}{W}", "Enchantment — Aura", """
        Enchant artifact or creature
        Enchanted permanent can't attack or block, and its activated abilities can't be activated.
    """)
    private val lullaby = Cards.invent("Lullaby of Silence", "{1}{U}", "Enchantment — Aura", """
        Enchant creature or Vehicle
        When this Aura enters, you may mill two cards. (You may put the top two cards of your library into your graveyard.)
        Fathomless descent — Enchanted permanent gets -X/-0, where X is the number of permanent cards in your graveyard.
    """)
    private val hollowCave = Cards.invent("Hidden Hollow Cave", null, "Land — Cave", """
        {T}: Add {C}.
        {1}, {T}: Add one mana of any color.
        {4}, {T}, Sacrifice this land: Put two +1/+1 counters on target creature. Activate only as a sorcery.
    """)
    private val shrine = Cards.invent("Shrine of Hollows", null, "Land — Cave", """
        {T}: Add {C}.
        Other Caves you control have "{T}, Pay 1 life: Add one mana of any color."
    """)
    private val prospectorsCave = Cards.invent("Prospector's Cave", null, "Land — Cave", """
        {T}: Add {C}.
        {1}, {T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
    """)
    private val herbalist = Cards.invent("Dinosaur Herbalist", "{G}", "Creature — Human Druid",
        "{T}: Add one mana of any color. Spend this mana only to cast a Dinosaur spell or activate an ability of a Dinosaur source.", "1/1")
    private val siren = Cards.invent("Clockwork Siren", "{1}{U}", "Artifact Creature — Siren Pirate", """
        Flying, vigilance
        {T}: Add {U}. Spend this mana only to cast an artifact spell or activate an ability of an artifact source.
    """, "1/2")
    private val frog = Cards.invent("Bog Frog", "{1}{G}", "Creature — Frog", """
        Reach
        {T}: Add one mana of any color.
        {2}: This creature gains deathtouch until end of turn.
    """, "1/1")
    private val bolt = Cards.invent("Scorch Bolt", "{R}", "Instant", "Scorch Bolt deals 5 damage to target creature.")
    private val bear = Cards.invent("Lowland Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val bruiser = Cards.invent("Hollow Bruiser", "{2}{G}", "Creature — Beast", "", "3/3")
    private val whelp = Cards.invent("Verdant Whelp", "{R}", "Creature — Dinosaur", "", "1/1")
    private val goblin = Cards.invent("Scrap Goblin", "{R}", "Creature — Goblin", "", "1/1")
    private val idol = Cards.invent("Idle Totem", "{1}", "Artifact", "{T}: You gain 1 life.")
    private val gadget = Cards.invent("Plain Trinket", "{1}", "Artifact", "")

    // CR 702.29e: Islandcycling pays {2}, discards the card and searches for an Island card
    @Test
    fun islandcyclingFindsAnIsland() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(hulkasaur)
        alice.library(bear, "Island", bear)
        alice.activates(hulkasaur)
        resolveAll()
        alice.chooses("Island")
        resolveAll()
        expect {
            alice.hand has "Island"
            alice.graveyard has hulkasaur
        }
    }

    // CR 704.5f, 704.5m: -2/-2 kills a 2/2; the Aura then goes to the graveyard
    @Test
    fun burdenKillsATwoTwo() = scenario {
        alice.battlefield(bear, "Swamp")
        alice.hand(burden)
        alice.casts(burden, target = bear)
        resolveAll()
        expect {
            alice.graveyard has bear
            alice.graveyard has burden
        }
    }

    // CR 613.4c: a 3/3 survives as a 1/1
    @Test
    fun burdenShrinksABiggerCreature() = scenario {
        alice.battlefield(bruiser, "Swamp")
        alice.hand(burden)
        alice.casts(burden, target = bruiser)
        resolveAll()
        expect {
            bruiser.power shouldBe 1
            bruiser.toughness shouldBe 1
        }
    }

    // CR 700.4, 603.10a, 110.2: it dies with the Aura, returns tapped under its owner's control without the Aura; the Aura stays in the graveyard (CR 303.4g, 704.5m)
    @Test
    fun resilienceReturnsTheCreatureTapped() = scenario {
        alice.battlefield(bear, resilience, "Mountain")
        attach(resilience, bear)
        alice.hand(bolt)
        alice.casts(bolt, target = bear)
        resolveAll()
        expect {
            bear.tapped shouldBe true
            bear.power shouldBe 2
            alice.graveyard has resilience
        }
    }

    // CR 508.1c: it can't be declared as an attacker; CR 602.5: its activated abilities can't be activated
    @Test
    fun stonebindStopsAttackingAndActivating() = scenario {
        alice.battlefield(bear, idol, stonebind)
        attach(stonebind, bear)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(bear) }
    }

    @Test
    fun stonebindOnAnArtifactStopsItsAbility() = scenario {
        alice.battlefield(idol, stonebind)
        attach(stonebind, idol)
        assertFailsWith<IllegalArgumentException> { alice.activates(idol) }
    }

    // CR 509.1b: it can't block
    @Test
    fun stonebindStopsBlocking() = scenario {
        alice.battlefield(bear)
        bob.battlefield(bruiser, stonebind)
        attach(stonebind, bruiser)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        allPass()
        assertFailsWith<IllegalArgumentException> { bob.blocks(bruiser to bear) }
    }

    // CR 110.4a, 404.1: milled permanent cards (two Islands) are counted by -X/-0, as it resolves
    @Test
    fun lullabyMillsThenCountsPermanentCards() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(lullaby)
        alice.library("Island", "Island", "Island")
        bob.battlefield(bruiser)
        alice.casts(lullaby, target = bruiser)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect { bruiser.power shouldBe 1 }
    }

    // CR 110.4a: an instant is no permanent card, so it isn't counted
    @Test
    fun lullabyDoesNotCountSpellCards() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(lullaby)
        alice.library(bolt, bolt, "Island")
        bob.battlefield(bruiser)
        alice.casts(lullaby, target = bruiser)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect { bruiser.power shouldBe 3 }
    }

    // CR 602.5: the Cave's abilities all need {T}; once it is tapped for {C} it can't be tapped for another
    @Test
    fun caveTappedForColorlessCannotBeTappedAgain() = scenario {
        alice.battlefield(hollowCave, "Mountain")
        alice.activates(hollowCave, ability = 0)
        assertFailsWith<IllegalArgumentException> { alice.activates(hollowCave, ability = 1, paying = listOf("Mountain")) }
    }

    // CR 701.21a, 602.5d: tap, pay {4} from other lands, sacrifice, put two counters
    @Test
    fun caveSacrificesForTwoCounters() = scenario {
        alice.battlefield(hollowCave, bear, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.activates(hollowCave, ability = 2, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            alice.graveyard has hollowCave
        }
    }

    // CR 613.1f, 106.1b: another Cave has the granted mana ability: it pays for a green spell and costs 1 life
    @Test
    fun shrineLetsAnotherCaveMakeColoredManaForALife() = scenario {
        alice.battlefield(shrine, prospectorsCave)
        alice.hand(bear)
        alice.casts(bear)
        resolveAll()
        expect {
            alice.life shouldBe 19
            alice.battlefield has bear
        }
    }

    // CR 613.1f, 106.1b: the granted mana ability is chosen by name and colour ("paying"): the Shrine's {C} and the Cave's {G} for a life
    @Test
    fun shrineGrantedAbilityIsChosenByPaying() = scenario {
        alice.battlefield(shrine, prospectorsCave)
        alice.hand(bear)
        alice.casts(bear, paying = listOf("Shrine of Hollows", "Prospector's Cave:G"))
        resolveAll()
        expect {
            alice.life shouldBe 19
            alice.battlefield has bear
        }
    }

    // CR 613.1f: "other": the Shrine itself makes only {C}, so a lone Shrine can't pay for a green spell
    @Test
    fun shrineDoesNotGrantItselfTheAbility() = scenario {
        alice.battlefield(shrine)
        alice.hand(whelp)
        alice.casts(whelp)
        expect { decision<Decision.ManaPayment>().suggestion.isEmpty() shouldBe true }
    }

    // CR 106.6: the Herbalist's mana pays for a Dinosaur spell, not for a Goblin
    @Test
    fun herbalistManaPaysForADinosaur() = scenario {
        alice.battlefield(herbalist)
        alice.hand(whelp)
        alice.casts(whelp)
        resolveAll()
        expect { alice.battlefield has whelp }
    }

    @Test
    fun herbalistManaCannotPayForAGoblin() = scenario {
        alice.battlefield(herbalist)
        alice.hand(goblin)
        alice.casts(goblin)
        expect { decision<Decision.ManaPayment>().suggestion.isEmpty() shouldBe true }
    }

    // CR 106.6: an artifact spell can use the Siren's {U}; a creature spell that is not an artifact can't
    @Test
    fun sirenManaPaysForAnArtifactSpell() = scenario {
        alice.battlefield(siren)
        alice.hand(gadget)
        alice.casts(gadget)
        resolveAll()
        expect { alice.battlefield has gadget }
    }

    @Test
    fun sirenManaCannotPayForAnInstant() = scenario {
        alice.battlefield(siren, bear)
        alice.hand(bolt)
        alice.casts(bolt, target = bear)
        expect { decision<Decision.ManaPayment>().suggestion.isEmpty() shouldBe true }
    }

    // CR 701.23a, 305.7: sacrifice for {1}: a basic land comes from the library tapped, then shuffle
    @Test
    fun prospectorsCaveFetchesATappedBasic() = scenario {
        alice.battlefield(prospectorsCave, "Mountain")
        alice.library(bear, "Forest", bear)
        alice.activates(prospectorsCave, ability = 1)
        resolveAll()
        alice.chooses("Forest")
        resolveAll()
        expect {
            "Forest".tapped shouldBe true
            alice.graveyard has prospectorsCave
        }
    }

    // CR 611.2a: {2} gives deathtouch until end of turn
    @Test
    fun frogGainsDeathtouch() = scenario {
        alice.battlefield(frog, "Forest", "Forest")
        alice.activates(frog, ability = 1)
        resolveAll()
        expect { frog hasKeyword Keyword.Deathtouch.kind }
    }
}
