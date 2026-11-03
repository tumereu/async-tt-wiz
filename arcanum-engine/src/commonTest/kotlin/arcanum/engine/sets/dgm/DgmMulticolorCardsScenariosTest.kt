package arcanum.engine.sets.dgm

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dgm.Dgm.bear
import arcanum.engine.sets.dgm.Dgm.doom
import arcanum.engine.sets.dgm.Dgm.giant
import arcanum.engine.sets.dgm.Dgm.human
import arcanum.engine.sets.dgm.Dgm.idol
import arcanum.engine.sets.dgm.Dgm.ogre
import arcanum.engine.sets.dgm.Dgm.p1p1
import arcanum.engine.sets.dgm.Dgm.ponder
import arcanum.engine.sets.dgm.Dgm.shock
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Dragon's Maze's multicolored cards in play (parser chunks 05–07). */
class DgmMulticolorCardsScenariosTest {
    private val arrivalOfTheWurm = Cards.invent("Arrival of the Wurm", "{1}{G}{G}{W}", "Instant", "Create a 5/5 green Wurm creature token with trample.")
    private val beetlewingMage = Cards.invent("Beetlewing Mage", "{1}{G}{U}", "Creature — Human Insect Wizard", "{G}{U}: This creature gets +2/+2 and gains flying until end of turn. Activate only once each turn.", "2/2")
    private val burstOfGenius = Cards.invent("Burst of Genius", "{4}{U}{R}", "Sorcery", "Choose any target. Draw three cards, then discard a card. Burst of Genius deals damage equal to the discarded card's mana value to that permanent or player.")
    private val blazeVanguard = Cards.invent("Blaze Vanguard", "{3}{R}{W}", "Creature — Minotaur Soldier", "Whenever an instant or sorcery spell you control deals damage, create two 1/1 red and white Soldier creature tokens with haste.", "5/3")
    private val bloodCountOfVizkopa = Cards.invent("Blood Count of Vizkopa", "{3}{W}{B}", "Creature — Vampire", """
        Lifelink, protection from white and from black
        As long as you have 30 or more life and an opponent has 10 or less life, this creature gets +6/+6 and has flying.
    """, "4/4")
    private val borosWarshaper = Cards.invent("Boros Warshaper", "{5}{R}{W}", "Creature — Minotaur Soldier", "At the beginning of each combat, up to one target creature attacks or blocks this combat if able and up to one target creature can't attack or block this combat.", "5/5")
    private val bredForTheChase = Cards.invent("Bred for the Chase", "{1}{G}{U}", "Enchantment", "Whenever a creature you control with a +1/+1 counter on it deals combat damage to a player, you may draw a card.")
    private val bronzeplumeMoa = Cards.invent("Bronzeplume Moa", "{2}{G}{W}", "Creature — Bird", "Whenever another creature you control enters, this creature gets +3/+3 until end of turn.", "2/2")
    private val carnageDuelist = Cards.invent("Carnage Duelist", "{2}{B}{R}", "Creature — Skeleton Warrior", """
        Whenever a creature blocks, that creature's controller loses 1 life.
        {1}{B}{R}: Regenerate this creature.
    """, "4/2")
    private val deadbridgeHymn = Cards.invent("Deadbridge Hymn", "{4}{B}{G}", "Enchantment", """
        When this enchantment enters, mill ten cards.
        At the beginning of your upkeep, choose a card at random in your graveyard. If it's a creature card, put it onto the battlefield. Otherwise, put it into your hand.
    """)
    private val debtOfTheDeathless = Cards.invent("Debt of the Deathless", "{X}{W}{W}{B}{B}", "Sorcery", "Each opponent loses two times X life. You gain life equal to the life lost this way.")
    private val marshalOfAcquittals = Cards.invent("Marshal of Acquittals", "{W}{U}", "Creature — Human Wizard", """
        Flash (You may cast this spell any time you could cast an instant.)
        When this creature enters, you may return another target creature you control to its owner's hand.
    """, "2/2")
    private val drakeshift = Cards.invent("Drakeshift", "{1}{U}{R}", "Instant", """
        Until end of turn, target creature you control becomes a blue and red Dragon with base power and toughness 4/4, loses all abilities, and gains flying.
        Overload {3}{U}{U}{R}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val drownedInFilth = Cards.invent("Drowned in Filth", "{B}{G}", "Sorcery", "Choose target creature. Mill four cards, then that creature gets -1/-1 until end of turn for each land card in your graveyard.")
    private val emmaraTandril = Cards.invent("Emmara Tandril", "{5}{G}{W}", "Legendary Creature — Elf Shaman", "Prevent all damage that would be dealt to creature tokens you control.", "5/7")
    private val feralShaman = Cards.invent("Feral Shaman", "{1}{R}{G}", "Creature — Goblin Shaman", "{3}: This creature gets +X/+0 until end of turn, where X is its power.", "2/1")
    private val fluxInverter = Cards.invent("Flux Inverter", "{2}{U}{R}", "Creature — Weird", """
        Flying
        Whenever you cast an instant or sorcery spell, you may switch this creature's power and toughness until end of turn.
    """, "1/5")
    private val glareOfGranite = Cards.invent("Glare of Granite", "{X}{B}{B}{G}", "Sorcery", "Destroy each nonland permanent with mana value X or less.")
    private val glintOfBattle = Cards.invent("Glint of Battle", "{4}{R}{W}", "Enchantment", "Whenever a creature you control attacks, put a +1/+1 counter on it.")
    private val gruulBattleChant = Cards.invent("Gruul Battle Chant", "{2}{R}{G}", "Enchantment", "Attacking creatures you control get +1/+0 and have menace.")
    private val haunterOfNightmist = Cards.invent("Haunter of Nightmist", "{3}{U}{B}", "Creature — Spirit", "Creatures your opponents control get -1/-0.", "3/4")
    private val jelennSentinel = Cards.invent("Jelenn Sentinel", "{3}{W}{U}", "Creature — Sphinx", """
        Flying, vigilance
        Whenever this creature attacks, other attacking creatures get +1/+1 until end of turn.
    """, "1/5")
    private val korozdaBasilisk = Cards.invent("Korozda Basilisk", "{3}{B}{G}", "Creature — Gorgon", """
        Deathtouch
        {2}, Remove a +1/+1 counter from a creature you control: Target creature gets -1/-1 until end of turn.
    """, "2/5")
    private val krasisIncubator = Cards.invent("Krasis Incubator", "{2}{G}{U}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature can't attack or block, and its activated abilities can't be activated.
        {1}{G}{U}, Return this Aura to its owner's hand: Put two +1/+1 counters on enchanted creature.
    """)
    private val legionsResolve = Cards.invent("Legion's Resolve", "{R}{W}", "Enchantment", """
        Red creatures you control get +1/+0.
        White creatures you control get +0/+1.
        {R}{W}, Exile this enchantment: Exile all creatures you control. At the beginning of the next combat, return those cards to the battlefield under their owner's control and those creatures gain haste until end of turn.
    """)
    private val maulOfTheObzedat = Cards.invent("Maul of the Obzedat", "{3}{W}{B}", "Creature — Thrull", "Sacrifice a creature: Creatures you control get +1/+1 until end of turn.", "3/3")
    private val melokIzzetProdigy = Cards.invent("Melok, Izzet Prodigy", "{4}{U}{R}", "Legendary Creature — Weird Wizard", """
        Play with the top card of your library revealed.
        You may cast instant and sorcery spells from the top of your library.
        Whenever you cast an instant or sorcery spell from your library, copy it. You may choose new targets for the copy.
    """, "2/4")
    private val mirkoVaskMindSiphon = Cards.invent("Mirko Vask, Mind Siphon", "{3}{U}{B}", "Legendary Creature — Vampire", """
        Flying
        Whenever Mirko Vask deals combat damage to a player, that player reveals cards from the top of their library until they reveal four land cards, then puts those cards into their graveyard.
    """, "2/4")
    private val morgueBlast = Cards.invent("Morgue Blast", "{4}{B}{R}", "Sorcery", "Return target creature card from your graveyard to your hand. Morgue Blast deals damage to any target equal to the power of the card returned this way.")
    private val nivixBrute = Cards.invent("Nivix Brute", "{1}{U}{R}", "Creature — Cyclops", """
        Defender
        Whenever you cast an instant or sorcery spell, this creature gets +3/+0 until end of turn and can attack this turn as though it didn't have defender.
    """, "1/4")
    private val obzedatsGift = Cards.invent("Obzedat's Gift", "{3}{W}{B}", "Sorcery", "Return target permanent card from your graveyard to the battlefield.")
    private val pilferedSchemes = Cards.invent("Pilfered Schemes", "{1}{U}{B}", "Sorcery", "Target player mills two cards. Draw two cards.")
    private val plasmSeizure = Cards.invent("Plasm Seizure", "{G}{G}{U}{U}", "Instant", "Counter target spell. At the beginning of your next first main phase, add X mana in any combination of colors, where X is that spell's mana value.")
    private val putridRot = Cards.invent("Putrid Rot", "{1}{B}{G}", "Instant", "Destroy target artifact or creature. It can't be regenerated.")
    private val ralZarov = Cards.invent("Ral Zarov", "{2}{U}{R}", "Legendary Planeswalker — Ral", """
        [+1]: Tap target permanent, then untap another target permanent.
        [−2]: Ral Zarov deals 3 damage to any target.
        [−7]: Flip five coins. Take an extra turn after this one for each coin that comes up heads.
    """, loyalty = "4")
    private val reapWisdom = Cards.invent("Reap Wisdom", "{X}{2}{U}{B}", "Sorcery", "Target opponent reveals their hand. You choose up to X nonland cards from it and exile them. For each card exiled this way, search that player's graveyard, hand, and library for any number of cards with the same name as that card and exile them. Then that player shuffles.")
    private val renderMute = Cards.invent("Render Mute", "{W}{U}{U}", "Instant", "Counter target spell. Its controller can't cast spells this turn.")
    private val restoreTheCalm = Cards.invent("Restore the Calm", "{1}{W}{U}", "Instant", "Return each creature that dealt damage this turn to its owner's hand.")
    private val rotFarmCorpse = Cards.invent("Rot Farm Corpse", "{2}{B}{G}", "Creature — Plant Skeleton", """
        This creature can't block.
        {2}{B}{G}, Mill four cards: Return this card from your graveyard to the battlefield. Activate only as a sorcery.
    """, "4/1")
    private val ruricThalTheUnyielding = Cards.invent("Ruric Thal, the Unyielding", "{4}{R}{G}", "Legendary Creature — Ogre Warrior", """
        Reach, vigilance
        Ruric Thal attacks each combat if able.
        Whenever a player casts a noncreature spell, Ruric Thal deals 6 damage to that player.
    """, "6/6")
    private val savagebredHydra = Cards.invent("Savagebred Hydra", "{X}{R}{G}", "Creature — Hydra", """
        Double strike
        This creature enters with X +1/+1 counters on it.
        {1}{R/G}: Put a +1/+1 counter on this creature. Activate only as a sorcery.
    """, "0/0")
    private val showstealer = Cards.invent("Showstealer", "{1}{B}{R}", "Instant", "Until end of turn, creatures you control gain \"When this creature dies, it deals 2 damage to target creature an opponent controls.\"")
    private val sinGatherer = Cards.invent("Sin Gatherer", "{1}{W}{B}", "Creature — Human Cleric", "When this creature enters, target opponent reveals their hand. You choose an instant or sorcery card from it and exile that card.", "2/1")
    private val sireOfMadness = Cards.invent("Sire of Madness", "{4}{B}{R}", "Creature — Demon", "At the beginning of each end step, each player discards their hand.", "6/4")
    private val speciesDevourer = Cards.invent("Species Devourer", "{3}{G}{U}", "Creature — Frog Beast", "At the beginning of your upkeep, return a creature you control to its owner's hand.", "6/6")
    private val teyraEnvoyOfGhosts = Cards.invent("Teyra, Envoy of Ghosts", "{5}{W}{B}", "Legendary Creature — Human Advisor", """
        Vigilance, protection from creatures
        Whenever a creature deals combat damage to you, destroy that creature. Create a 1/1 white and black Spirit creature token with flying.
    """, "4/4")
    private val titheSipper = Cards.invent("Tithe Sipper", "{W}{B}", "Creature — Vampire", """
        Lifelink (Damage dealt by this creature also causes you to gain that much life.)
        Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
    """, "2/1")
    private val trostanisCaller = Cards.invent("Trostani's Caller", "{5}{G}{W}", "Creature — Elf Shaman", "When this creature enters, create a 2/2 white Knight creature token with vigilance, a 3/3 green Centaur creature token, and a 4/4 green Rhino creature token with trample.", "1/1")
    private val unflinchingValor = Cards.invent("Unflinching Valor", "{1}{G}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+2 and has trample and lifelink. (Damage dealt by the creature also causes its controller to gain that much life.)
    """)
    private val viashinoFirstblood = Cards.invent("Viashino Firstblood", "{1}{R}{W}", "Creature — Lizard Soldier", """
        Haste
        When this creature enters, it gets +2/+2 until end of turn.
    """, "2/2")
    private val vorelOfTheHullClan = Cards.invent("Vorel of the Hull Clan", "{1}{G}{U}", "Legendary Creature — Human Merfolk", "{G}{U}, {T}: Double the number of each kind of counter on target artifact, creature, or land.", "1/4")
    private val warleadersSpiral = Cards.invent("Warleader's Spiral", "{2}{R}{W}", "Instant", "Warleader's Spiral deals 4 damage to any target and you gain 4 life.")
    private val warpedForm = Cards.invent("Warped Form", "{U}{B}", "Instant", "Target creature gets +X/-X until end of turn, where X is the number of cards in your hand.")
    private val zhurTaaShaman = Cards.invent("Zhur-Taa Shaman", "{R}{G}", "Creature — Human Druid", """
        {T}: Add {G}.
        Whenever you tap this creature for mana, it deals 1 damage to each opponent.
    """, "1/1")
    private val redWhite = Cards.invent("Maze Runner Legionnaire", "{R}{W}", "Creature — Human Soldier", "", "2/2")
    private val whiteBear = Cards.invent("Maze Runner Lancer", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val tokenCall = Cards.invent("Maze Runner Muster", "{W}", "Sorcery", "Create two 1/1 white Soldier creature tokens.")

    // ---- Chunks 05–07: multicolored

    @Test
    fun arrivalOfTheWurmMakesAWurm() = scenario {
        alice.battlefield("Forest", "Forest", "Plains", "Plains")
        alice.hand(arrivalOfTheWurm)
        alice.casts(arrivalOfTheWurm)
        resolveAll()
        expect { "Wurm Token" hasKeyword Keyword.Trample }
    }

    @Test
    fun beetlewingMageGrowsOnce() = scenario {
        alice.battlefield(beetlewingMage, "Forest", "Island", "Forest", "Island")
        alice.activates(beetlewingMage, paying = listOf("Forest", "Island"))
        resolveAll()
        expect {
            beetlewingMage.power shouldBe 4
            beetlewingMage hasKeyword Keyword.Flying
        }
        assertFailsWith<IllegalArgumentException> { alice.activates(beetlewingMage, paying = listOf("Forest#1", "Island#1")) }
    }

    @Test
    fun burstOfGeniusBurnsByTheDiscard() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Mountain", "Mountain")
        alice.hand(burstOfGenius)
        alice.library(giant, bear, human)
        alice.casts(burstOfGenius, target = "bob")
        resolveAll()
        alice.chooses(giant)
        resolveAll()
        expect { bob.life shouldBe 15 }
    }

    @Test
    fun blazeVanguardMakesSoldiersForSpellDamage() = scenario {
        alice.battlefield("Mountain", blazeVanguard)
        alice.hand(shock)
        alice.casts(shock, target = "bob")
        resolveAll()
        expect { alice.battlefield.names.count { it == "Soldier Token" } shouldBe 2 }
    }

    @Test
    fun bloodCountOfVizkopaGrowsAtThirtyLife() = scenario {
        alice.battlefield(bloodCountOfVizkopa)
        alice.life = 30
        bob.life = 10
        expect {
            bloodCountOfVizkopa.power shouldBe 10
            bloodCountOfVizkopa hasKeyword Keyword.Flying
        }
    }

    // CR 508.1d, 509.1c: requirements and restrictions for this combat
    @Test
    fun borosWarshaperForcesAndStops() = scenario {
        alice.battlefield(borosWarshaper, bear)
        bob.battlefield(ogre)
        advanceTo(TurnStep.BeginningOfCombat)
        targets(bear)
        if (pending is Decision.Targets) targets(ogre)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(borosWarshaper) }
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to bear) }
    }

    @Test
    fun bredForTheChaseDrawsForCounteredAttackers() = scenario {
        alice.battlefield(bredForTheChase)
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.library(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.hand has ogre }
    }

    @Test
    fun bronzeplumeMoaGrowsOnArrivals() = scenario {
        alice.battlefield("Forest", "Forest", bronzeplumeMoa)
        alice.hand(bear)
        alice.casts(bear)
        resolveAll()
        expect { bronzeplumeMoa.power shouldBe 5 }
    }

    @Test
    fun carnageDuelistPunishesBlockers() = scenario {
        alice.battlefield(carnageDuelist)
        bob.battlefield(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(carnageDuelist)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(bear to carnageDuelist)
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun deadbridgeHymnMillsTenAndRecurs() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Forest", "Forest")
        alice.hand(deadbridgeHymn)
        alice.library(bear, bear, bear, bear, bear, bear, bear, bear, bear, bear, ogre)
        alice.casts(deadbridgeHymn)
        resolveAll()
        expect { alice.graveyard.names.count { it == bear } shouldBe 10 }
    }

    @Test
    fun debtOfTheDeathlessDrainsTwiceX() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(debtOfTheDeathless)
        alice.casts(debtOfTheDeathless, x = 2)
        resolveAll()
        expect {
            bob.life shouldBe 16
            alice.life shouldBe 24
        }
    }

    @Test
    fun marshalOfAcquittalsSavesACreature() = scenario {
        alice.battlefield("Plains", "Island", bear)
        alice.hand(marshalOfAcquittals)
        alice.casts(marshalOfAcquittals)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun drakeshiftMakesADragon() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain", bear)
        alice.hand(drakeshift)
        alice.casts(drakeshift, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Flying
            bear.colors.toSet() shouldBe setOf(Color.Blue, Color.Red)
        }
    }

    @Test
    fun drownedInFilthShrinksByLands() = scenario {
        alice.battlefield("Swamp", "Forest")
        alice.hand(drownedInFilth)
        alice.library("Island", "Island", bear, ogre)
        bob.battlefield(bear)
        alice.casts(drownedInFilth, target = bear)
        resolveAll()
        expect { bob.graveyard has bear }
    }

    @Test
    fun emmaraTandrilProtectsTokens() = scenario {
        alice.battlefield("Plains", "Mountain", emmaraTandril)
        alice.hand(tokenCall, shock)
        alice.casts(tokenCall)
        resolveAll()
        alice.casts(shock, target = "Soldier Token")
        resolveAll()
        expect { alice.battlefield.names.count { it == "Soldier Token" } shouldBe 2 }
    }

    @Test
    fun feralShamanDoublesItsPower() = scenario {
        alice.battlefield(feralShaman, "Forest", "Forest", "Forest")
        alice.activates(feralShaman)
        resolveAll()
        expect { feralShaman.power shouldBe 4 }
    }

    // CR 613.4d: switching power and toughness
    @Test
    fun fluxInverterSwitches() = scenario {
        alice.battlefield(fluxInverter, "Island")
        alice.hand(ponder)
        alice.library(bear)
        alice.casts(ponder)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            fluxInverter.power shouldBe 5
            fluxInverter.toughness shouldBe 1
        }
    }

    @Test
    fun glareOfGraniteDestroysByManaValue() = scenario {
        alice.battlefield("Swamp", "Swamp", "Forest", "Forest", "Forest")
        alice.hand(glareOfGranite)
        bob.battlefield(bear, idol, giant)
        alice.casts(glareOfGranite, x = 2)
        resolveAll()
        expect {
            bob.graveyard has bear
            bob.graveyard has idol
            bob.battlefield has giant
        }
    }

    @Test
    fun glintOfBattleCountersAttackers() = scenario {
        alice.battlefield(glintOfBattle, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun gruulBattleChantMenacesAttackers() = scenario {
        alice.battlefield(gruulBattleChant, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Menace
        }
    }

    @Test
    fun haunterOfNightmistWeakensThem() = scenario {
        alice.battlefield(haunterOfNightmist)
        bob.battlefield(ogre)
        expect { ogre.power shouldBe 2 }
    }

    @Test
    fun jelennSentinelPumpsOtherAttackers() = scenario {
        alice.battlefield(jelennSentinel, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(jelennSentinel, bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            jelennSentinel.power shouldBe 1
        }
    }

    @Test
    fun korozdaBasiliskSpendsCounters() = scenario {
        alice.battlefield(korozdaBasilisk, "Swamp", "Swamp")
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        bob.battlefield(human)
        alice.activates(korozdaBasilisk, target = human, choosing = listOf(bear))
        resolveAll()
        expect {
            bob.graveyard has human
            bear.counters(p1p1) shouldBe 0
        }
    }

    @Test
    fun krasisIncubatorLocksAndReturns() = scenario {
        alice.battlefield("Forest", "Island", "Forest", "Island", "Forest", "Island", "Forest", bear)
        alice.hand(krasisIncubator)
        alice.casts(krasisIncubator, target = bear, paying = listOf("Forest", "Forest#1", "Island", "Island#1"))
        resolveAll()
        expect { bear hasMechanic { it is Mechanic.CantAttack } }
        alice.activates(krasisIncubator)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 2
            alice.hand has krasisIncubator
        }
    }

    @Test
    fun legionsResolvePumpsByColor() = scenario {
        alice.battlefield(legionsResolve, redWhite)
        expect {
            redWhite.power shouldBe 3
            redWhite.toughness shouldBe 3
        }
    }

    @Test
    fun legionsResolveBlinksUntilCombat() = scenario {
        alice.battlefield(legionsResolve, bear, "Mountain", "Plains")
        alice.activates(legionsResolve)
        resolveAll()
        expect { alice.exile has bear }
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect {
            alice.battlefield has bear
            bear hasKeyword Keyword.Haste
        }
    }

    @Test
    fun maulOfTheObzedatSacrificesToPump() = scenario {
        alice.battlefield(maulOfTheObzedat, bear, human)
        alice.activates(maulOfTheObzedat, choosing = listOf(human))
        resolveAll()
        expect { bear.power shouldBe 3 }
    }

    @Test
    fun melokCastsAndCopiesFromTheLibrary() = scenario {
        alice.battlefield(melokIzzetProdigy, "Mountain")
        alice.library(shock)
        alice.casts(shock, target = "bob")
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    @Test
    fun mirkoVaskMillsToFourLands() = scenario {
        alice.battlefield(mirkoVaskMindSiphon)
        bob.library("Forest", bear, "Forest", "Forest", "Forest", giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(mirkoVaskMindSiphon)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { bob.library.names shouldBe listOf(giant) }
    }

    @Test
    fun morgueBlastReturnsAndBurnsByPower() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Mountain", "Mountain")
        alice.hand(morgueBlast)
        alice.graveyard(giant)
        alice.casts(morgueBlast, targets = listOf(giant, "bob"))
        resolveAll()
        expect {
            alice.hand has giant
            bob.life shouldBe 15
        }
    }

    @Test
    fun nivixBruteAttacksAfterASpell() = scenario {
        alice.battlefield(nivixBrute, "Island")
        alice.hand(ponder)
        alice.library(bear)
        alice.casts(ponder)
        resolveAll()
        expect { nivixBrute.power shouldBe 4 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(nivixBrute)
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 16 }
    }

    @Test
    fun obzedatsGiftReturnsAPermanent() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Swamp", "Swamp")
        alice.hand(obzedatsGift)
        alice.graveyard(idol)
        alice.casts(obzedatsGift, target = idol)
        resolveAll()
        expect { alice.battlefield has idol }
    }

    @Test
    fun pilferedSchemesMillsAndDraws() = scenario {
        alice.battlefield("Island", "Island", "Swamp")
        alice.hand(pilferedSchemes)
        alice.library(bear, ogre)
        bob.library(giant, human, idol)
        alice.casts(pilferedSchemes, target = "bob")
        resolveAll()
        expect {
            bob.library.names shouldBe listOf(idol)
            alice.hand.size shouldBe 2
        }
    }

    // CR 603.7: mana at the beginning of your next first main phase
    @Test
    fun plasmSeizureCountersAndRefunds() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(ogre)
        bob.library(bear)
        alice.battlefield("Forest", "Forest", "Island", "Island")
        alice.hand(plasmSeizure)
        alice.library(bear)
        turn(active = bob)
        bob.casts(ogre)
        bob.passes()
        alice.casts(plasmSeizure, target = ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
        // bob took turn 1: turn 2 is alice's next turn
        advanceTo(TurnStep.Draw, turn = 2)
        allPass()
        resolveAll()
        repeat(3) { if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green)) }
        expect { alice.pool.size shouldBe 3 }
    }

    @Test
    fun putridRotDestroys() = scenario {
        alice.battlefield("Swamp", "Forest", "Swamp")
        alice.hand(putridRot)
        bob.battlefield(idol)
        alice.casts(putridRot, target = idol)
        resolveAll()
        expect { bob.graveyard has idol }
    }

    @Test
    fun ralZarovBurns() = scenario {
        alice.battlefield(ralZarov, counters = listOf(CounterKind.Loyalty to 4))
        alice.activates(ralZarov, ability = 1, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 17
            ralZarov.counters(CounterKind.Loyalty) shouldBe 2
        }
    }

    @Test
    fun ralZarovTapsAndUntaps() = scenario {
        alice.battlefield(ralZarov, counters = listOf(CounterKind.Loyalty to 4))
        alice.battlefield(bear, tapped = true)
        bob.battlefield(ogre)
        alice.activates(ralZarov, ability = 0, targets = listOf(ogre, bear))
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            bear.tapped shouldBe false
        }
    }

    @Test
    fun reapWisdomExilesEveryCopy() = scenario {
        alice.battlefield("Island", "Island", "Island", "Swamp", "Swamp")
        alice.hand(reapWisdom)
        bob.hand(ogre, "Forest")
        bob.graveyard(ogre)
        bob.library(bear, ogre)
        alice.casts(reapWisdom, target = "bob", x = 1)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        if (pending is Decision.ChooseObjects) answer(Answer.Objects(decision<Decision.ChooseObjects>().options))
        resolveAll()
        expect { bob.exile.names.count { it == ogre } shouldBe 3 }
    }

    @Test
    fun renderMuteCountersAndSilences() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        bob.hand(ogre, shock)
        alice.battlefield("Plains", "Island", "Island")
        alice.hand(renderMute)
        turn(active = bob)
        bob.casts(ogre, paying = listOf("Mountain", "Mountain#1", "Mountain#2"))
        bob.passes()
        alice.casts(renderMute, target = ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
        assertFailsWith<IllegalArgumentException> { bob.casts(shock, target = "alice") }
    }

    @Test
    fun restoreTheCalmBouncesDamageDealers() = scenario {
        alice.battlefield("Plains", "Island", "Island", bear)
        alice.hand(restoreTheCalm)
        bob.battlefield(ogre, giant)
        bob.library(human)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(ogre)
        advanceTo(TurnStep.EndOfCombat)
        bob.passes()
        alice.casts(restoreTheCalm)
        resolveAll()
        expect {
            bob.hand has ogre
            bob.battlefield has giant
        }
    }

    @Test
    fun rotFarmCorpseReturnsByMilling() = scenario {
        alice.battlefield("Swamp", "Swamp", "Forest", "Forest")
        alice.graveyard(rotFarmCorpse)
        alice.library(bear, ogre, giant, human, idol)
        alice.activates(rotFarmCorpse)
        resolveAll()
        expect {
            alice.battlefield has rotFarmCorpse
            alice.library.names shouldBe listOf(idol)
        }
    }

    @Test
    fun ruricThalPunishesNoncreatureSpells() = scenario {
        alice.battlefield(ruricThalTheUnyielding)
        bob.battlefield("Island")
        bob.hand(ponder)
        bob.library(bear, ogre)
        turn(active = bob)
        bob.casts(ponder)
        resolveAll()
        expect { bob.life shouldBe 14 }
    }

    @Test
    fun savagebredHydraEntersWithXAndGrows() = scenario {
        alice.battlefield("Mountain", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(savagebredHydra)
        alice.casts(savagebredHydra, x = 2, paying = listOf("Mountain", "Forest", "Forest#1", "Forest#2"))
        resolveAll()
        expect { savagebredHydra.power shouldBe 2 }
        alice.activates(savagebredHydra)
        resolveAll()
        expect {
            savagebredHydra.power shouldBe 3
            savagebredHydra hasKeyword Keyword.DoubleStrike
        }
    }

    @Test
    fun showstealerMakesDeathsHurt() = scenario {
        alice.battlefield("Swamp", "Mountain", "Mountain", "Swamp", human)
        alice.hand(showstealer, doom)
        bob.battlefield(bear)
        alice.casts(showstealer, paying = listOf("Swamp", "Mountain", "Mountain#1"))
        resolveAll()
        alice.casts(doom, target = human)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bob.graveyard has bear }
    }

    @Test
    fun sinGathererExilesAnInstant() = scenario {
        alice.battlefield("Plains", "Swamp", "Swamp")
        alice.hand(sinGatherer)
        bob.hand(shock, ogre)
        alice.casts(sinGatherer)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(shock)
        resolveAll()
        expect { bob.exile has shock }
    }

    @Test
    fun sireOfMadnessEmptiesHands() = scenario {
        alice.battlefield(sireOfMadness)
        alice.hand(bear)
        bob.hand(ogre, giant)
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.hand.isEmpty()
            bob.hand.isEmpty()
        }
    }

    @Test
    fun speciesDevourerReturnsACreature() = scenario {
        alice.battlefield(speciesDevourer, bear)
        alice.library(ogre)
        bob.library(ogre)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun teyraDestroysWhatHitsYou() = scenario {
        alice.battlefield(teyraEnvoyOfGhosts)
        bob.battlefield(bear)
        bob.library(ogre)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(bear)
        advanceTo(TurnStep.EndOfCombat)
        resolveAll()
        expect {
            bob.graveyard has bear
            alice.battlefield has "Spirit Token"
        }
    }

    @Test
    fun titheSipperExtorts() = scenario {
        alice.battlefield(titheSipper, "Forest", "Forest", "Plains")
        alice.hand(bear)
        alice.casts(bear, paying = listOf("Forest", "Forest#1"))
        resolveAll()
        may(true)
        payIfAsked()
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun trostanisCallerMakesThreeTokens() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Plains", "Plains")
        alice.hand(trostanisCaller)
        alice.casts(trostanisCaller)
        resolveAll()
        expect {
            alice.battlefield has "Knight Token"
            alice.battlefield has "Centaur Token"
            "Rhino Token" hasKeyword Keyword.Trample
        }
    }

    @Test
    fun unflinchingValorPumps() = scenario {
        alice.battlefield("Forest", "Plains", "Plains", bear)
        alice.hand(unflinchingValor)
        alice.casts(unflinchingValor, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Lifelink
        }
    }

    @Test
    fun viashinoFirstbloodHitsHardOnEntering() = scenario {
        alice.battlefield("Mountain", "Plains", "Mountain")
        alice.hand(viashinoFirstblood)
        alice.casts(viashinoFirstblood)
        resolveAll()
        expect { viashinoFirstblood.power shouldBe 4 }
    }

    // CR 701.10e: doubling counters
    @Test
    fun vorelDoublesCounters() = scenario {
        alice.battlefield(vorelOfTheHullClan, "Forest", "Island")
        alice.battlefield(bear, counters = listOf(p1p1 to 2))
        alice.activates(vorelOfTheHullClan, target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 4 }
    }

    @Test
    fun warleadersSpiralBurnsAndGains() = scenario {
        alice.battlefield("Mountain", "Mountain", "Plains", "Plains")
        alice.hand(warleadersSpiral)
        alice.casts(warleadersSpiral, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 16
            alice.life shouldBe 24
        }
    }

    @Test
    fun warpedFormByHandSize() = scenario {
        alice.battlefield("Island", "Swamp")
        alice.hand(warpedForm, bear, ogre)
        bob.battlefield(bear)
        alice.casts(warpedForm, target = bear)
        resolveAll()
        expect { bob.graveyard has bear }
    }

    @Test
    fun zhurTaaShamanPingsWhenTapped() = scenario {
        alice.battlefield(zhurTaaShaman)
        alice.activates(zhurTaaShaman)
        resolveAll()
        expect {
            alice.pool.size shouldBe 1
            bob.life shouldBe 19
        }
    }
}
