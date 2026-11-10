package arcanum.engine.sets.fdn

import arcanum.api.CounterKind
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The FDN cards whose first unread clause was an effect, played: the invented names carry the oracle text of Sorcerous Spyglass,
 * Mild-Mannered Librarian, Ballyrush Banneret, Harbinger of the Tides, Omniscience, Quilled Greatwurm, Secluded Courtyard, Vizier of the
 * Menagerie, Chandra, Flameshaper, Liliana, Dreadhorde General, Thousand-Year Storm, Giada, Font of Hope, Nine-Lives Familiar, Goblin
 * Negotiation, Hare Apparent and Lathril, Blade of the Elves.
 */
class FdnChunk08EffectsScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val revival = CounterKind.Named("revival")
    private val bear = Cards.invent("Eightfold Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Eightfold Ox", "{2}{G}", "Creature — Ox", "", "3/4")
    private val footman = Cards.invent("Eightfold Footman", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val elf = Cards.invent("Eightfold Elf", "{G}", "Creature — Elf", "", "1/1")
    private val squirrel = Cards.invent("Eightfold Squirrel", "{G}", "Creature — Squirrel", "", "1/1")
    private val angel = Cards.invent("Eightfold Angel", "{1}{W}", "Creature — Angel", "", "2/2")
    private val growth = Cards.invent("Eightfold Growth", "{G}", "Sorcery", "Put a +1/+1 counter on target creature.")
    private val shock = Cards.invent("Eightfold Shock", "{R}", "Instant", "Eightfold Shock deals 2 damage to any target.")
    private val idea = Cards.invent("Eightfold Idea", "{U}", "Sorcery", "Draw a card.")
    private val tinker = Cards.invent("Eightfold Tinker", "{1}", "Artifact", "{T}: You gain 1 life.")

    private val monocle = Cards.invent("Prying Monocle", "{2}", "Artifact", """
        As this artifact enters, look at an opponent's hand, then choose any card name.
        Activated abilities of sources with the chosen name can't be activated unless they're mana abilities.
    """)
    private val archivist = Cards.invent("Bashful Archivist", "{G}", "Creature — Human",
        "{3}{G}: This creature becomes a Werewolf. Put two +1/+1 counters on it and you draw a card. Activate only once.", "1/1")
    private val captain = Cards.invent("Hillbanner Captain", "{1}{W}", "Creature — Kithkin Soldier",
        "Kithkin spells and Soldier spells you cast cost {1} less to cast.", "2/1")
    private val herald = Cards.invent("Herald of the Ebb", "{U}{U}", "Creature — Merfolk Wizard", """
        You may cast this spell as though it had flash if you pay {2} more to cast it. (You may cast it any time you could cast an instant.)
        When this creature enters, you may return target tapped creature an opponent controls to its owner's hand.
    """, "2/2")
    private val insight = Cards.invent("Boundless Insight", "{7}{U}{U}{U}", "Enchantment",
        "You may cast spells from your hand without paying their mana costs.")
    private val wurm = Cards.invent("Spinebristle Wurm", "{4}{G}{G}", "Creature — Wurm", """
        Trample
        Whenever a creature you control deals combat damage during your turn, put that many +1/+1 counters on it. (It must survive to get the counters.)
        You may cast this card from your graveyard by removing six counters from among creatures you control in addition to paying its other costs.
    """, "7/7")
    private val cloister = Cards.invent("Hidden Cloister", null, "Land", """
        As this land enters, choose a creature type.
        {T}: Add {C}.
        {T}: Add one mana of any color. Spend this mana only to cast a creature spell of the chosen type or activate an ability of a creature source of the chosen type.
    """)
    private val steward = Cards.invent("Steward of the Pens", "{3}{G}", "Creature — Snake Cleric", """
        You may look at the top card of your library any time.
        You may cast creature spells from the top of your library.
        You can spend mana of any type to cast creature spells.
    """, "3/4")
    private val pyrra = Cards.invent("Pyrra, Blazeshaper", "{5}{R}{R}", "Legendary Planeswalker — Chandra", """
        [+2]: Add {R}{R}{R}. Exile the top three cards of your library. Choose one. You may play that card this turn.
        [+1]: Create a token that's a copy of target creature you control, except it has haste and "At the beginning of the end step, sacrifice this token."
        [−4]: Pyrra deals 8 damage divided as you choose among any number of target creatures and/or planeswalkers.
    """, loyalty = "6")
    private val tempest = Cards.invent("Endless Tempest", "{4}{U}{R}", "Enchantment",
        "Whenever you cast an instant or sorcery spell, copy it for each other instant and sorcery spell you've cast before it this turn. You may choose new targets for the copies.")
    private val seraphel = Cards.invent("Seraphel, Wellspring of Faith", "{1}{W}", "Legendary Creature — Angel", """
        Flying, vigilance
        Each other Angel you control enters with an additional +1/+1 counter on it for each Angel you already control.
        {T}: Add {W}. Spend this mana only to cast an Angel spell.
    """, "2/2")
    private val tabby = Cards.invent("Ninefold Tabby", "{1}{B}{B}", "Creature — Cat", """
        This creature enters with eight revival counters on it if you cast it.
        When this creature dies, if it had a revival counter on it, return it to the battlefield with one fewer revival counter on it at the beginning of the next end step.
    """, "1/1")
    private val haggling = Cards.invent("Goblin Haggling", "{X}{R}{R}", "Sorcery",
        "Goblin Haggling deals X damage to target creature. Create a number of 1/1 red Goblin creature tokens equal to the amount of excess damage dealt to that creature this way.")
    private val heir = Cards.invent("Heir of the Warren", "{1}{W}", "Creature — Rabbit Noble", """
        When this creature enters, create a number of 1/1 white Rabbit creature tokens equal to the number of other creatures you control named Heir of the Warren.
        A deck can have any number of cards named Heir of the Warren.
    """, "2/2")
    private val vaelith = Cards.invent("Vaelith, Edge of the Elves", "{2}{B}{G}", "Legendary Creature — Elf Noble", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Whenever Vaelith deals combat damage to a player, create that many 1/1 green Elf Warrior creature tokens.
        {T}, Tap ten untapped Elves you control: Each opponent loses 10 life and you gain 10 life.
    """, "2/3")

    /** Whether the engine offers Alice the cast of [name] from where it is (a permission to cast it there, CR 601.2). */
    private fun arcanum.engine.scenario.Scenario.canCast(name: String): Boolean =
        Engine.options(state, alice.id).any { it is Input.Cast && it.card == card(name) }

    private val golem = Cards.invent("Eightfold Golem", "{3}", "Artifact Creature — Golem", "", "3/3")
    private val charm = Cards.invent("Eightfold Charm", "{W}", "Enchantment", "")
    private val morwen = Cards.invent("Morwen, Gravetide Sovereign", "{4}{B}{B}", "Legendary Planeswalker — Liliana", """
        Whenever a creature you control dies, draw a card.
        [+1]: Create a 2/2 black Zombie creature token.
        [−4]: Each player sacrifices two creatures of their choice.
        [−9]: Each opponent chooses a permanent they control of each permanent type and sacrifices the rest.
    """, loyalty = "6")

    // CR 110.4, 701.21a: Bob keeps the Golem (as his artifact and his creature), one Forest and the Charm; the Ox and the other Forest go
    @Test
    fun morwenUltimateKeepsOneOfEachPermanentType() = scenario {
        alice.battlefield(morwen, counters = listOf(CounterKind.Loyalty to 10))
        bob.battlefield(golem, ox, charm, "Forest", "Forest")
        alice.activates(morwen, ability = 2)
        resolve()
        var guard = 10
        while (pending is Decision.ChooseObjects && guard-- > 0) {
            val options = decision<Decision.ChooseObjects>().options
            val pick = options.firstOrNull { it == card(golem) } ?: options.first()
            answer(Answer.Objects(listOf(pick)))
        }
        resolveAll()
        expect {
            bob.battlefield has golem
            bob.battlefield has charm
            bob.battlefield.names.count { it == "Forest" } shouldBe 1
            bob.graveyard has ox
            bob.graveyard has "Forest"
            alice.battlefield has morwen
        }
    }

    // CR 614.12, 201.3: the chosen name's activated abilities can't be activated
    @Test
    fun pryingMonocleStopsTheNamedCard() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(monocle)
        bob.hand("Island")
        bob.battlefield(tinker)
        alice.casts(monocle)
        resolve()
        if (pending is Decision.ChoosePlayer) answer(Answer.Player(bob.id))
        answer(Answer.CardName(tinker))
        expect { alice.battlefield has monocle }
        assertTrue(Engine.options(state, bob.id).none { it is Input.Activate && it.source == card(tinker) })
    }

    // CR 205.1a: a Werewolf and no longer a Human; CR 602.5b: only once
    @Test
    fun bashfulArchivistBecomesAWerewolfOnce() = scenario {
        alice.battlefield(archivist, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.library("Island", "Island")
        alice.activates(archivist)
        resolveAll()
        expect {
            archivist.subtypes shouldBe listOf(Subtype("Werewolf"))
            archivist.counters(p1p1) shouldBe 2
            alice.hand has "Island"
        }
        assertFailsWith<Throwable> { alice.activates(archivist) }
    }

    // CR 601.2f: a Soldier spell costs {1} less; a Bear doesn't
    @Test
    fun hillbannerCaptainMakesSoldiersCheaper() = scenario {
        alice.battlefield(captain, "Plains", "Forest")
        alice.hand(footman, bear)
        alice.casts(footman, paying = listOf("Plains"))
        resolveAll()
        expect { alice.battlefield has footman }
        // the Bear's {1}{G} isn't reduced: one Forest can't pay it
        alice.casts(bear)
        expect { decision<Decision.ManaPayment>().suggestion shouldBe emptyList() }
    }

    // CR 702.8a, 601.2f: cast in combat by paying {2} more; its trigger returns Bob's tapped creature
    @Test
    fun heraldOfTheEbbFlashedInBouncesATappedCreature() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(herald)
        bob.battlefield(ox, tapped = true)
        advanceTo(TurnStep.BeginningOfCombat)
        alice.casts(herald, kicked = 1)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.battlefield has herald
            bob.hand has ox
        }
    }

    // CR 307.1, 702.8a: without the extra {2}, not in combat
    @Test
    fun heraldOfTheEbbNeedsTheExtraCostOutsideItsTiming() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(herald)
        advanceTo(TurnStep.BeginningOfCombat)
        assertFailsWith<IllegalArgumentException> { alice.casts(herald, kicked = 0) }
    }

    // CR 118.9: cast from the hand with no mana
    @Test
    fun boundlessInsightCastsForFree() = scenario {
        alice.battlefield(insight)
        alice.hand(ox)
        // the permission's way: without paying the mana cost
        perform(Engine.options(state, alice.id).filterIsInstance<Input.Cast>().first { it.card == card(ox) && it.way.permission != null })
        resolveAll()
        expect { alice.battlefield has ox }
    }

    // CR 601.2b, 601.2h: cast from the graveyard, removing six counters from among Alice's creatures
    @Test
    fun spinebristleWurmCastFromTheGraveyardByRemovingCounters() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.battlefield(bear, counters = listOf(p1p1 to 4))
        alice.battlefield(ox, counters = listOf(p1p1 to 2))
        alice.graveyard(wurm)
        alice.casts(wurm, choosing = listOf(bear, ox))
        resolveAll()
        expect {
            alice.battlefield has wurm
            bear.counters(p1p1) shouldBe 0
            ox.counters(p1p1) shouldBe 0
        }
    }

    // CR 601.2h: with five counters, it can't be cast from the graveyard
    @Test
    fun spinebristleWurmNeedsSixCounters() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.battlefield(bear, counters = listOf(p1p1 to 5))
        alice.graveyard(wurm)
        assertFailsWith<IllegalArgumentException> { alice.casts(wurm, choosing = listOf(bear)) }
    }

    // CR 510.2, 120.3: combat damage on Alice's turn becomes that many counters on the creature that dealt it
    @Test
    fun spinebristleWurmGrowsCreaturesThatDealCombatDamage() = scenario {
        alice.battlefield(wurm, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            bob.life shouldBe 18
            bear.counters(p1p1) shouldBe 2
        }
    }

    // CR 106.6: not a Squirrel, another type
    @Test
    fun hiddenCloisterManaOnlyForTheChosenType() = scenario {
        alice.hand(cloister, elf, squirrel)
        alice.plays(cloister)
        answer(Answer.SubtypeChoice(Subtype("Elf")))
        alice.casts(squirrel)
        expect { decision<Decision.ManaPayment>().suggestion shouldBe emptyList() }
    }

    // CR 106.6: the coloured mana casts an Elf, the chosen type
    @Test
    fun hiddenCloisterManaCastsTheChosenType() = scenario {
        alice.hand(cloister, elf)
        alice.plays(cloister)
        answer(Answer.SubtypeChoice(Subtype("Elf")))
        alice.casts(elf)
        resolveAll()
        expect { alice.battlefield has elf }
    }

    // CR 118.14, 609.4b: Islands' mana casts a green creature spell, but not a green sorcery
    @Test
    fun stewardOfThePensSpendsAnyTypeOnCreatureSpells() = scenario {
        alice.battlefield(steward, bear, "Island", "Island", "Island", "Island")
        alice.hand(ox, growth)
        alice.casts(ox)
        resolveAll()
        expect { alice.battlefield has ox }
        alice.casts(growth, target = bear)
        expect { decision<Decision.ManaPayment>().suggestion shouldBe emptyList() }
    }

    // "Choose one" of the three exiled cards; only that one may be played this turn (CR 305.1, 601.2)
    @Test
    fun pyrraExilesThreeAndPlaysTheChosenOne() = scenario {
        alice.battlefield(pyrra, counters = listOf(CounterKind.Loyalty to 6))
        alice.library(bear, shock, "Forest")
        alice.activates(pyrra, ability = 0)
        resolve()
        alice.chooses(shock)
        resolveAll()
        expect {
            pyrra.counters(CounterKind.Loyalty) shouldBe 8
            alice.exile has shock
            alice.exile has bear
            canCast(bear) shouldBe false
        }
        alice.casts(shock, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    // CR 702.40a, 707.10: the second instant or sorcery Alice casts this turn is copied once
    @Test
    fun endlessTempestCopiesForEachEarlierSpell() = scenario {
        alice.battlefield(tempest, "Island", "Mountain")
        alice.hand(idea, shock)
        alice.library("Island", "Island", "Island")
        alice.casts(idea)
        resolveAll()
        expect { alice.hand.names.size shouldBe 2 }
        alice.casts(shock, target = "bob")
        resolve()
        if (pending is Decision.May) answer(Answer.YesNo(false))
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    // CR 614.1c: one Angel already (Seraphel), so another Angel gets one counter; with two, two counters
    @Test
    fun seraphelGivesAnAngelACounterPerAngel() = scenario {
        alice.battlefield(seraphel, "Plains")
        alice.hand(angel)
        alice.casts(angel)
        resolveAll()
        expect { angel.counters(p1p1) shouldBe 1 }
    }

    // CR 603.10a, 603.7: dies with eight revival counters; at the next end step it returns with seven
    @Test
    fun ninefoldTabbyReturnsWithOneFewerCounter() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand(tabby, shock)
        alice.casts(tabby)
        resolveAll()
        expect { tabby.counters(revival) shouldBe 8 }
        alice.casts(shock, target = tabby)
        resolveAll()
        expect { alice.graveyard has tabby }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield has tabby
            tabby.counters(revival) shouldBe 7
        }
    }

    // CR 603.4: with no revival counter it stays in the graveyard
    @Test
    fun ninefoldTabbyWithoutCountersStaysDead() = scenario {
        alice.battlefield(tabby, "Mountain")
        alice.hand(shock)
        alice.casts(shock, target = tabby)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.graveyard has tabby }
    }

    // CR 120.4a: 6 damage to a 3/4 is 2 excess, so two Goblins
    @Test
    fun goblinHagglingMakesGoblinsForExcessDamage() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(haggling)
        bob.battlefield(ox)
        alice.casts(haggling, target = ox, x = 6)
        resolveAll()
        expect {
            bob.graveyard has ox
            alice.battlefield.names.count { it == "Goblin Token" } shouldBe 2
        }
    }

    // CR 201.2: two other Heirs, so two Rabbits
    @Test
    fun heirOfTheWarrenCountsTheOtherHeirs() = scenario {
        alice.battlefield(heir, heir, "Plains", "Plains")
        alice.hand(heir)
        alice.casts(heir)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Rabbit Token" } shouldBe 2 }
    }

    // CR 120.3: 2 combat damage, two Elf Warriors
    @Test
    fun vaelithMakesElvesForCombatDamage() = scenario {
        alice.battlefield(vaelith)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(vaelith)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.battlefield.names.count { it == "Elf Warrior Token" } shouldBe 2
        }
    }

    // CR 602.2b, 701.26a: tapping ten untapped Elves (other than Vaelith) drains 10
    @Test
    fun vaelithTapsTenElvesToDrain() = scenario {
        alice.battlefield(vaelith, elf, elf, elf, elf, elf, elf, elf, elf, elf, elf)
        alice.activates(vaelith, choosing = List(10) { elf })
        resolveAll()
        expect {
            bob.life shouldBe 10
            alice.life shouldBe 30
        }
    }
}
