package arcanum.engine.sets.fdn

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Chunk 22 played: the invented names carry the oracle text of Taurean Mauler, Three Tree Mascot, Electroduplicate, Inspiration from
 * Beyond, Mystical Teachings, Revenge of the Rats, Self-Reflection, Think Twice, Elfsworn Giant, Felidar Retreat, Grappling Kraken,
 * Primeval Bounty, Rampaging Baloths, Tatyova, Drake Hatcher, Elementalist Adept, Heartfire Immolator, Lightshell Duo and Shipwreck Dowser.
 */
class FdnChunk22ChangelingFlashbackLandfallProwessScenariosTest {
    private val prowessText = "Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)"
    private val flashbackTail = "(You may cast this card from your graveyard for its flashback cost. Then exile it.)"

    private val mangler = Cards.invent("Tauric Mangler", "{2}{R}", "Creature — Shapeshifter", """
        Changeling (This card is every creature type.)
        Whenever an opponent casts a spell, you may put a +1/+1 counter on this creature.
    """, "2/2")
    private val mascot = Cards.invent("Three Branch Mascot", "{2}", "Artifact Creature — Shapeshifter", """
        Changeling (This card is every creature type.)
        {1}: Add one mana of any color. Activate only once each turn.
    """, "2/1")
    private val electrocopy = Cards.invent("Electrocopy", "{2}{R}", "Sorcery", """
        Create a token that's a copy of target creature you control, except it has haste and "At the beginning of the end step, sacrifice this token."
        Flashback {2}{R}{R} $flashbackTail
    """)
    private val insight = Cards.invent("Insight from Afar", "{2}{U}", "Sorcery", """
        Mill three cards, then return an instant or sorcery card from your graveyard to your hand.
        Flashback {5}{U}{U} $flashbackTail
    """)
    private val tutelage = Cards.invent("Arcane Tutelage", "{3}{U}", "Instant", """
        Search your library for an instant card or a card with flash, reveal it, put it into your hand, then shuffle.
        Flashback {5}{B} $flashbackTail
    """)
    private val mice = Cards.invent("Vengeance of Mice", "{2}{B}{B}", "Sorcery", """
        Create a tapped 1/1 black Rat creature token for each creature card in your graveyard.
        Flashback {2}{B}{B} $flashbackTail
    """)
    private val duplication = Cards.invent("Self-Duplication", "{4}{U}{U}", "Sorcery", """
        Create a token that's a copy of target creature you control.
        Flashback {3}{U} $flashbackTail
    """)
    private val ponder = Cards.invent("Ponder Twice", "{1}{U}", "Instant", """
        Draw a card.
        Flashback {2}{U} $flashbackTail
    """)
    private val giant = Cards.invent("Elfbound Giant", "{3}{G}{G}", "Creature — Giant", """
        Reach (This creature can block creatures with flying.)
        Landfall — Whenever a land you control enters, create a 1/1 green Elf Warrior creature token.
    """, "5/3")
    private val haven = Cards.invent("Felid Haven", "{3}{W}", "Enchantment", """
        Landfall — Whenever a land you control enters, choose one —
        • Create a 2/2 white Cat Beast creature token.
        • Put a +1/+1 counter on each creature you control. Those creatures gain vigilance until end of turn.
    """)
    private val leviathan = Cards.invent("Gripping Leviathan", "{4}{U}{U}", "Creature — Kraken", """
        Landfall — Whenever a land you control enters, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
    """, "5/6")
    private val largesse = Cards.invent("Primal Largesse", "{5}{G}", "Enchantment", """
        Whenever you cast a creature spell, create a 3/3 green Beast creature token.
        Whenever you cast a noncreature spell, put three +1/+1 counters on target creature you control.
        Landfall — Whenever a land you control enters, you gain 3 life.
    """)
    private val behemoths = Cards.invent("Charging Behemoths", "{4}{G}{G}", "Creature — Beast", """
        Trample
        Landfall — Whenever a land you control enters, create a 4/4 green Beast creature token.
    """, "6/6")
    private val tatiana = Cards.invent("Tatiana, Pond Druid", "{3}{G}{U}", "Legendary Creature — Merfolk Druid", """
        Landfall — Whenever a land you control enters, you gain 1 life and draw a card.
    """, "3/3")
    private val brooder = Cards.invent("Wyrmling Brooder", "{1}{U}", "Creature — Human Wizard", """
        Vigilance, prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        Whenever this creature deals combat damage to a player, put that many incubation counters on it.
        Remove three incubation counters from this creature: Create a 2/2 blue Drake creature token with flying.
    """, "1/3")
    private val novice = Cards.invent("Sparkcaller Novice", "{1}{U}", "Creature — Human Wizard", """
        Flash (You may cast this spell any time you could cast an instant.)
        $prowessText
    """, "2/1")
    private val burner = Cards.invent("Hearthflame Burner", "{1}{R}", "Creature — Human Wizard", """
        $prowessText
        {R}, Sacrifice this creature: It deals damage equal to its power to target creature or planeswalker.
    """, "2/2")
    private val pair = Cards.invent("Shellglow Pair", "{3}{U}", "Creature — Rat Otter", """
        $prowessText
        When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
    """, "3/4")
    private val diviner = Cards.invent("Wreckage Diviner", "{3}{U}{U}", "Creature — Merfolk Wizard", """
        $prowessText
        When this creature enters, return target instant or sorcery card from your graveyard to your hand.
    """, "3/3")

    private val ox = Cards.invent("Tarnished Ox", "{2}", "Creature — Ox", "", "2/2")
    private val brute = Cards.invent("Mossback Brute", "{2}{G}", "Creature — Beast", "", "3/3")
    private val whisper = Cards.invent("Quiet Whisper", "{U}", "Instant", "You gain 1 life.")
    private val hearth = Cards.invent("Hearthside Sorcery", "{R}", "Sorcery", "You gain 1 life.")
    private val tinker = Cards.invent("Idle Tinker", "{1}", "Creature — Human", "", "1/1")
    private val trophy = Cards.invent("Dull Trophy", "{1}", "Artifact", "")
    private val flashSprite = Cards.invent("Flicker Sprite", "{1}{U}", "Creature — Faerie", "Flash", "1/1")
    private val farmland = Cards.invent("Quiet Farmland", null, "Land", "{T}: Add {G}.")

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    private fun Scenario.count(name: String) = state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) } == name }

    private val p1p1 = CounterKind.PowerToughness(1, 1)

    // CR 702.73a: a changeling is every creature type
    @Test
    fun manglerIsEveryCreatureType() = scenario {
        alice.battlefield(mangler)
        expect {
            mangler hasKeyword Keyword.Changeling.kind
            check(mangler.view.characteristics!!.hasSubtype(Subtype("Elf")) && mangler.view.characteristics!!.hasSubtype(Subtype("Goblin"))) { "not every type" }
        }
    }

    // CR 603.1, 601.2a: an opponent's spell triggers it and the controller may take the counter
    @Test
    fun manglerGrowsWhenAnOpponentCastsASpell() = scenario {
        alice.battlefield(mangler)
        bob.battlefield("Island")
        bob.hand(whisper)
        alice.passes()
        bob.casts(whisper)
        resolveAll()
        alice.answers(Answer.YesNo(true))
        resolveAll()
        expect { mangler.counters(p1p1) shouldBe 1 }
    }

    // CR 603.1: its controller's own spells don't trigger it
    @Test
    fun manglerIgnoresItsControllersSpells() = scenario {
        alice.battlefield(mangler, "Island")
        alice.hand(whisper)
        alice.casts(whisper)
        resolveAll()
        expect { mangler.counters(p1p1) shouldBe 0 }
    }

    // CR 602.5b: the mana ability makes one mana of the chosen colour, once each turn
    @Test
    fun mascotMakesAnyColorOnceEachTurn() = scenario {
        alice.battlefield(mascot, "Island", "Island")
        alice.activates(mascot, color = arcanum.api.Color.Red)
        expect { check(state.player(alice.id).manaPool.map { it.color } == listOf(arcanum.api.Color.Red)) { "pool" } }
        assertFailsWith<Throwable> { alice.activates(mascot, color = arcanum.api.Color.Red) }
    }

    // CR 707.2, 707.9b, 702.34a: the copy has haste and is sacrificed at the end step; flashback exiles the card
    @Test
    fun electrocopyCopiesWithHasteAndSacrificesItAtEnd() = scenario {
        alice.battlefield(ox, "Mountain", "Mountain", "Mountain")
        alice.hand(electrocopy)
        alice.casts(electrocopy, target = ox)
        resolveAll()
        expect {
            count("Tarnished Ox") shouldBe 2
            alice.graveyard has "Electrocopy"
        }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { count("Tarnished Ox") shouldBe 1 }
    }

    @Test
    fun electrocopyCopyHasHaste() = scenario {
        alice.battlefield(ox, "Mountain", "Mountain", "Mountain")
        alice.hand(electrocopy)
        alice.casts(electrocopy, target = ox)
        resolveAll()
        val tokens = state.battlefield.filter { state.obj(it)?.let { o -> nameOf(o) } == "Tarnished Ox" && state.obj(it)?.token == true }
        check(tokens.size == 1) { "one copy token expected" }
        expect {
            arcanum.engine.scenario.CardRef(tokens.single(), "the copy") hasKeyword Keyword.Haste.kind
            ox lacksKeyword Keyword.Haste.kind
        }
    }

    @Test
    fun electrocopyFlashbackCastsFromGraveyardAndExilesIt() = scenario {
        alice.battlefield(ox, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard(electrocopy)
        alice.casts(electrocopy, target = ox)
        resolveAll()
        expect {
            count("Tarnished Ox") shouldBe 2
            electrocopy.zone shouldBe Zone.Exile
        }
    }

    // CR 701.17a, 400.3: the three milled cards can include the card returned; an instant or sorcery comes back
    @Test
    fun insightMillsThenReturnsAnInstant() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(insight)
        alice.graveyard(hearth)
        alice.library(whisper, "Forest", "Forest", "Forest")
        alice.casts(insight)
        resolveAll()
        alice.chooses("Quiet Whisper")
        resolveAll()
        expect {
            alice.hand has "Quiet Whisper"
            alice.graveyard has "Hearthside Sorcery"
            alice.graveyard.size shouldBe 4
        }
    }

    // CR 702.34a: from the graveyard it costs {5}{U}{U} and is exiled afterwards
    @Test
    fun insightFlashbackIsExiled() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.graveyard(insight, hearth)
        alice.library(whisper, "Forest", "Forest", "Forest")
        alice.casts(insight)
        resolveAll()
        alice.chooses("Quiet Whisper")
        resolveAll()
        expect {
            insight.zone shouldBe Zone.Exile
            alice.hand has "Quiet Whisper"
        }
    }

    // CR 701.23a: an instant card OR a card with flash, either one is findable
    @Test
    fun tutelageFindsAnInstantWithoutFlash() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(tutelage)
        alice.library(whisper, flashSprite, ox)
        alice.casts(tutelage)
        resolveAll()
        alice.chooses("Quiet Whisper")
        expect {
            alice.hand has "Quiet Whisper"
            alice.hand lacks "Tarnished Ox"
        }
    }

    @Test
    fun tutelageFindsAFlashCreature() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(tutelage)
        alice.library(whisper, flashSprite, ox)
        alice.casts(tutelage)
        resolveAll()
        alice.chooses("Flicker Sprite")
        expect { alice.hand has "Flicker Sprite" }
    }

    @Test
    fun tutelageCannotFindAPlainCreature() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(tutelage)
        alice.library(whisper, ox)
        alice.casts(tutelage)
        resolveAll()
        assertFailsWith<Throwable> { alice.chooses("Tarnished Ox") }
    }

    // CR 111.1, 110.5b, 702.34a: one tapped Rat for each creature card in the graveyard; flashback exiles
    @Test
    fun miceMakeTappedRatsPerCreatureCard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(mice)
        alice.graveyard(ox, tinker, whisper)
        alice.casts(mice)
        resolveAll()
        expect {
            count("Rat Token") shouldBe 2
            alice.graveyard has "Vengeance of Mice"
        }
    }

    @Test
    fun miceFlashbackExilesItself() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(mice, ox)
        alice.casts(mice)
        resolveAll()
        expect {
            count("Rat Token") shouldBe 1
            mice.zone shouldBe Zone.Exile
        }
    }

    // CR 707.2: a token copy of the targeted creature, then flashback copies again
    @Test
    fun duplicationCopiesACreature() = scenario {
        alice.battlefield(ox, "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(duplication)
        alice.casts(duplication, target = ox)
        resolveAll()
        expect { count("Tarnished Ox") shouldBe 2 }
    }

    @Test
    fun duplicationFlashbackCopiesAndExiles() = scenario {
        alice.battlefield(ox, "Island", "Island", "Island", "Island")
        alice.graveyard(duplication)
        alice.casts(duplication, target = ox)
        resolveAll()
        expect {
            count("Tarnished Ox") shouldBe 2
            duplication.zone shouldBe Zone.Exile
        }
    }

    // CR 702.34a, 121.1: cast from the hand it stays in the graveyard; flashback draws again and exiles it
    @Test
    fun ponderDrawsAndFlashesBack() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(ponder)
        alice.library("Forest", "Plains", "Swamp")
        alice.casts(ponder)
        resolveAll()
        expect { alice.graveyard has "Ponder Twice" }
        alice.casts(ponder)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            ponder.zone shouldBe Zone.Exile
        }
    }

    // CR 207.2c, 603.2: each land that enters under your control makes a token; an opponent's land doesn't
    @Test
    fun giantMakesAnElfPerLand() = scenario {
        alice.battlefield(giant)
        alice.hand(farmland)
        alice.plays(farmland)
        resolveAll()
        expect { count("Elf Warrior Token") shouldBe 1 }
    }

    @Test
    fun giantIgnoresOpponentLands() = scenario {
        alice.battlefield(giant)
        bob.hand(farmland)
        turn(active = bob)
        bob.plays(farmland)
        resolveAll()
        expect { count("Elf Warrior Token") shouldBe 0 }
    }

    // CR 700.2: the first mode makes a 2/2 Cat Beast
    @Test
    fun havenCanMakeACat() = scenario {
        alice.battlefield(haven)
        alice.hand(farmland)
        alice.plays(farmland)
        alice.answers(Answer.Modes(listOf(0)))
        resolveAll()
        expect { count("Cat Beast Token") shouldBe 1 }
    }

    // CR 700.2, 122.1, 702.20a: the second mode counters every creature and gives vigilance
    @Test
    fun havenCanGrowAndGiveVigilance() = scenario {
        alice.battlefield(haven, ox, tinker)
        alice.hand(farmland)
        alice.plays(farmland)
        alice.answers(Answer.Modes(listOf(1)))
        resolveAll()
        expect {
            ox.counters(p1p1) shouldBe 1
            tinker.counters(p1p1) shouldBe 1
            ox hasKeyword Keyword.Vigilance.kind
            tinker hasKeyword Keyword.Vigilance.kind
        }
    }

    // CR 701.26a, 122.1g: the opponent's creature is tapped and gets a stun counter
    @Test
    fun leviathanTapsAndStunsAnOpposingCreature() = scenario {
        alice.battlefield(leviathan)
        bob.battlefield(brute)
        alice.hand(farmland)
        alice.plays(farmland)
        resolveAll()
        expect {
            brute.tapped shouldBe true
            brute.counters(CounterKind.Stun) shouldBe 1
        }
    }

    // CR 601.2, 603.2: creature spells and noncreature spells trigger separately; the land gains 3 life
    @Test
    fun largesseMakesABeastForACreatureSpell() = scenario {
        alice.battlefield(largesse, "Plains")
        alice.hand(tinker)
        alice.casts(tinker)
        resolveAll()
        expect { count("Beast Token") shouldBe 1 }
    }

    @Test
    fun largessePutsThreeCountersForANoncreatureSpell() = scenario {
        alice.battlefield(largesse, ox, "Island")
        alice.hand(whisper)
        alice.casts(whisper)
        resolveAll()
        expect {
            ox.counters(p1p1) shouldBe 3
            count("Beast Token") shouldBe 0
        }
    }

    @Test
    fun largesseGainsThreeLifeOnLandfall() = scenario {
        alice.battlefield(largesse)
        alice.hand(farmland)
        alice.plays(farmland)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    @Test
    fun behemothsMakeAFourFourBeast() = scenario {
        alice.battlefield(behemoths)
        alice.hand(farmland)
        alice.plays(farmland)
        resolveAll()
        expect {
            count("Beast Token") shouldBe 1
            behemoths hasKeyword Keyword.Trample.kind
        }
    }

    @Test
    fun tatianaGainsLifeAndDraws() = scenario {
        alice.battlefield(tatiana)
        alice.hand(farmland)
        alice.library("Forest", "Plains")
        alice.plays(farmland)
        resolveAll()
        expect {
            alice.life shouldBe 21
            alice.hand has "Forest"
        }
    }

    // CR 510.2, 122.1: damage dealt to a player becomes that many incubation counters; three become a 2/2 flier
    @Test
    fun brooderCountsDamageAsIncubationCounters() = scenario {
        alice.battlefield(brooder)
        bob.library("Island", "Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(brooder)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect {
            bob.life shouldBe 19
            brooder.counters(CounterKind.Named("incubation")) shouldBe 1
        }
    }

    @Test
    fun brooderRemovesThreeCountersForADrake() = scenario {
        alice.battlefield(brooder, counters = listOf(CounterKind.Named("incubation") to 3))
        alice.activates(brooder)
        resolveAll()
        expect {
            brooder.counters(CounterKind.Named("incubation")) shouldBe 0
            count("Drake Token") shouldBe 1
        }
    }

    // CR 702.108a: a noncreature spell gives +1/+1 until end of turn; a creature spell doesn't
    @Test
    fun noviceGrowsForANoncreatureSpell() = scenario {
        alice.battlefield(novice, "Island")
        alice.hand(whisper)
        alice.casts(whisper)
        resolveAll()
        expect { novice.power shouldBe 3; novice.toughness shouldBe 2 }
    }

    @Test
    fun noviceDoesNotGrowForACreatureSpell() = scenario {
        alice.battlefield(novice, "Island")
        alice.hand(tinker)
        alice.casts(tinker)
        resolveAll()
        expect { novice.power shouldBe 2 }
    }

    // CR 702.8a: flash lets it be cast on the opponent's turn
    @Test
    fun noviceHasFlash() = scenario {
        bob.hand(novice)
        bob.battlefield("Island", "Island")
        turn(active = alice, priority = bob)
        bob.casts(novice)
        resolveAll()
        expect { novice.zone shouldBe Zone.Battlefield }
    }

    // CR 702.108a, 608.2h: prowess's bonus counts in the damage the sacrificed creature deals
    @Test
    fun burnerDealsItsPowerAfterProwess() = scenario {
        alice.battlefield(burner, "Mountain", "Mountain", "Mountain")
        bob.battlefield(brute)
        alice.hand(hearth)
        alice.casts(hearth)
        resolveAll()
        alice.activates(burner, target = brute)
        resolveAll()
        expect {
            burner.zone shouldBe Zone.Graveyard
            brute.zone shouldBe Zone.Graveyard
        }
    }

    @Test
    fun burnerDealsTwoWithoutProwess() = scenario {
        alice.battlefield(burner, "Mountain")
        bob.battlefield(brute)
        alice.activates(burner, target = brute)
        resolveAll()
        expect {
            burner.zone shouldBe Zone.Graveyard
            brute.zone shouldBe Zone.Battlefield
        }
    }

    // CR 701.25a, 603.6a: surveil 2 on entering, prowess watches the cast of other spells
    @Test
    fun pairSurveilsTwoOnEntering() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(pair)
        alice.library("Forest", "Plains", "Swamp")
        alice.casts(pair)
        resolveAll()
        alice.chooses("Forest")
        expect { alice.graveyard has "Forest" }
    }

    @Test
    fun pairGrowsForANoncreatureSpell() = scenario {
        alice.battlefield(pair, "Island")
        alice.hand(whisper)
        alice.casts(whisper)
        resolveAll()
        expect { pair.power shouldBe 4 }
    }

    // CR 115.1, 400.3: the entering Diviner returns a target instant or sorcery card, not a creature
    @Test
    fun divinerReturnsAnInstantFromTheGraveyard() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(diviner)
        alice.graveyard(whisper, ox)
        alice.casts(diviner)
        resolveAll()
        expect {
            alice.hand has "Quiet Whisper"
            alice.graveyard has "Tarnished Ox"
        }
    }

    @Test
    fun divinerGrowsForANoncreatureSpell() = scenario {
        alice.battlefield(diviner, "Island")
        alice.hand(whisper)
        alice.casts(whisper)
        resolveAll()
        expect { diviner.power shouldBe 4 }
    }

    @Test
    fun trophyIsNoncreatureForProwess() = scenario {
        alice.battlefield(novice, "Plains")
        alice.hand(trophy)
        alice.casts(trophy)
        resolveAll()
        expect { novice.power shouldBe 3 }
    }
}
