package arcanum.engine.sets.fra

import arcanum.api.CardType
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fra.Fra.bear
import arcanum.engine.sets.fra.Fra.doom
import arcanum.engine.sets.fra.Fra.elf
import arcanum.engine.sets.fra.Fra.flyer
import arcanum.engine.sets.fra.Fra.giant
import arcanum.engine.sets.fra.Fra.heal
import arcanum.engine.sets.fra.Fra.idol
import arcanum.engine.sets.fra.Fra.ogre
import arcanum.engine.sets.fra.Fra.p1p1
import arcanum.engine.sets.fra.Fra.ponder
import arcanum.engine.sets.fra.Fra.shock
import arcanum.engine.sets.fra.Fra.walker
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Reality Fracture's white, blue, black, red and green cards in play (parser chunks 06–10). */
class FraMonoColorCardsScenariosTest {
    private val pathOfTheTutor = Cards.invent("Path of the Tutor", "{2}{W}", "Legendary Enchantment", """
        When Path of the Tutor enters, empower Jace 5. (Put five loyalty counters on a Jace token you control. If you don't control one, first create a blue Jace planeswalker token with "[−1]: Surveil 1" and "[−3]: Draw a card.")
        Whenever you gain life, put a loyalty counter on each planeswalker you control.
    """)
    private val destinyforgeHopeful = Cards.invent("Destinyforge Hopeful", "{4}{W}", "Creature — Rhino Cleric", """
        When this creature enters, choose one —
        • Return target legendary card from your graveyard to your hand.
        • Put a +1/+1 counter on target creature. It gains vigilance and indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
    """, "3/4")
    private val flickerfurHound = Cards.invent("Flickerfur Hound", "{3}{W}", "Creature — Dog", "Whenever you cast a creature spell, exile up to one other target creature you control, then return that card to the battlefield under its owner's control.", "2/2")
    private val bountifulRevivification = Cards.invent("Bountiful Revivification", "{2}{W}", "Sorcery", """
        Return target creature card with mana value 3 or less from your graveyard to the battlefield with an additional +1/+1 counter on it.
        Flashback {4}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val sproutTheConscripts = Cards.invent("Sprout the Conscripts", "{2}{W}", "Instant", "Create X 2/2 colorless Wizard Soldier creature tokens named Cadet, where X is the amount of life you gained this turn.")
    private val spliceSurgeon = Cards.invent("Splice Surgeon", "{2}{W}", "Creature — Human Cleric", """
        This creature enters with a +1/+1 counter on it.
        When this creature dies, put its counters on up to one target creature you control.
    """, "2/2")
    private val pathfinderHydra = Cards.invent("Pathfinder Hydra", "{X}{W}", "Creature — Hydra Horror", """
        This creature enters with X +1/+1 counters on it.
        At the beginning of combat on your turn, you may remove a +1/+1 counter from this creature. If you do, put a +1/+1 counter on each other creature you control.
    """, "1/0")
    private val kinshipVerdict = Cards.invent("Kinship Verdict", "{5}{W}{W}", "Sorcery", "Choose a creature type. Destroy all creatures that aren't of the chosen type.")
    private val recollectionSnare = Cards.invent("Recollection Snare", "{2}{W}", "Enchantment", "When this enchantment enters, exile target nonland permanent an opponent controls until this enchantment leaves the battlefield.")
    private val foresightedReadiness = Cards.invent("Foresighted Readiness", "{1}{W}", "Sorcery", """
        Put a +1/+1 counter on each of one or two target creatures.
        Flashback {3}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val denyTheProphecy = Cards.invent("Deny the Prophecy", "{1}{W}", "Sorcery", "Exile target creature or planeswalker that's green or blue. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)")
    private val homecomingToTheBrightReaches = Cards.invent("Homecoming to the Bright Reaches", "{7}{W}{W}", "Sorcery", "Return all nonland permanent cards from your graveyard to the battlefield.")
    private val shardwingSteed = Cards.invent("Shardwing Steed", "{2}{W}", "Creature — Pegasus", """
        Flying
        {4}{W}: Creatures you control get +1/+1 until end of turn.
    """, "2/3")
    private val exactingIncision = Cards.invent("Exacting Incision", "{1}{W}", "Sorcery", """
        Choose one —
        • Destroy target creature with toughness 4 or greater. You gain 1 life.
        • You draw a card and gain 2 life.
    """)
    private val undauntedHortiseer = Cards.invent("Undaunted Hortiseer", "{1}{W}", "Creature — Human Cleric", """
        Ward {1} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {1}.)
        Whenever you gain life, put a +1/+1 counter on this creature.
    """, "2/1")
    private val thyDestinyHalts = Cards.invent("Thy Destiny Halts", "{2}{W}", "Instant", "Destroy target creature or planeswalker with mana value 3 or greater. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)")
    private val mercilessComputations = Cards.invent("Merciless Computations", "{2}{U}", "Sorcery", "Draw X cards, where X is the number of cards that were put into target player's graveyard from their library this turn.")
    private val frostlogicAdept = Cards.invent("Frostlogic Adept", "{1}{U}", "Creature — Human Wizard", """
        Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        {3}{U}, Exile this card from your graveyard: Tap target creature and put a stun counter on it. Activate only as a sorcery. (If a permanent with a stun counter would become untapped, remove one from it instead.)
    """, "2/1")
    private val scryingFencer = Cards.invent("Scrying Fencer", "{2}{U}", "Creature — Merfolk Wizard", """
        Flash
        When this creature enters, choose one —
        • Tap target creature.
        • Untap target creature.
        • Draw a card, then discard a card.
    """, "3/2")
    private val frigidWelcome = Cards.invent("Frigid Welcome", "{1}{U}", "Instant", """
        Choose one —
        • Counter target creature or legendary spell unless its controller pays {3}.
        • Target creature gets -5/-0 until end of turn.
    """)
    private val polishedHypothesis = Cards.invent("Polished Hypothesis", "{U}", "Instant", """
        Choose one —
        • Target creature has base power and toughness 1/1 until end of turn.
        • Target creature has base power and toughness 4/5 until end of turn.
    """)
    private val exactExcision = Cards.invent("Exact Excision", "{1}{U}", "Instant", "Counter target white or black spell.")
    private val sphinxOfMistakenProofs = Cards.invent("Sphinx of Mistaken Proofs", "{2}{U}{U}", "Creature — Sphinx Illusion", """
        Flash
        Flying
        Whenever this creature attacks, draw a card, then discard a card.
        When this creature dies, if it isn't a token, create a token that's a copy of it.
    """, "4/2")
    private val watchfulPhantasm = Cards.invent("Watchful Phantasm", "{1}{U}", "Creature — Bird Illusion", """
        Defender, flying, vigilance
        As long as you've scried or surveilled this turn, this creature can attack as though it didn't have defender.
        {3}{U}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
    """, "2/3")
    private val ripplingObserver = Cards.invent("Rippling Observer", "{4}{U}", "Creature — Serpent", """
        Flying
        {2}: This creature gets +1/-1 until end of turn.
        Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
    """, "3/5")
    private val pinnacleHexstalker = Cards.invent("Pinnacle Hexstalker", "{4}{B}{B}", "Creature — Wolf", """
        Menace (This creature can't be blocked except by two or more creatures.)
        When this creature enters or dies, you gain 2 life.
        Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
    """, "6/4")
    private val crackUnderStrain = Cards.invent("Crack Under Strain", "{2}{B}", "Instant", "Target opponent sacrifices a creature or planeswalker with the greatest mana value among creatures and planeswalkers they control. You gain 2 life.")
    private val shedAllHesitation = Cards.invent("Shed All Hesitation", "{2}{B}", "Sorcery", "Draw two cards. Shed All Hesitation deals 2 damage to each player.")
    private val duskflarePhoenix = Cards.invent("Duskflare Phoenix", "{3}{B}", "Creature — Phoenix", """
        Flying, haste
        At the beginning of combat on your turn, if two or more creatures died this turn, return this card from your graveyard to the battlefield.
    """, "3/2")
    private val prolongedDeparture = Cards.invent("Prolonged Departure", "{3}{B}", "Instant", "Exile target creature or planeswalker. Prolonged Departure deals 1 damage to each opponent and you gain 1 life.")
    private val timesNothing = Cards.invent("Times Nothing", "{1}{B}", "Instant", "Target creature has base power and toughness 0/0 until end of turn.")
    private val bulwarkStalker = Cards.invent("Bulwark Stalker", "{3}{B}", "Creature — Horror", """
        Deathtouch
        When this creature enters, target creature gets +2/+2 and gains deathtouch until end of turn.
    """, "3/3")
    private val foulVermin = Cards.invent("Foul Vermin", "{1}{B}", "Creature — Zombie Rat", "When this creature enters, each opponent discards a card.", "1/1")
    private val ascentOfTheDoombringer = Cards.invent("Ascent of the Doombringer", "{4}{B}", "Instant", """
        Choose one —
        • Draw cards equal to the greatest power among creatures you control. You lose life equal to the number of cards drawn this way.
        • All creatures get -3/-3 until end of turn.
    """)
    private val shriekingSpiritcrusher = Cards.invent("Shrieking Spiritcrusher", "{2}{B}", "Creature — Siren Bard", """
        Flying
        Whenever this creature attacks, it deals 1 damage to each opponent and you gain 1 life.
    """, "1/4")
    private val hushTheReverberation = Cards.invent("Hush the Reverberation", "{1}{B}", "Sorcery", """
        As an additional cost to cast this spell, sacrifice a creature or planeswalker or pay {3}.
        Destroy target creature or planeswalker.
    """)
    private val fatalCritique = Cards.invent("Fatal Critique", "{1}{B}", "Instant", "Destroy target creature or planeswalker that's blue or red. You gain 1 life.")
    private val hypotheticalGravecaller = Cards.invent("Hypothetical Gravecaller", "{2}{B}", "Creature — Vampire Warlock", "{3}{B}, Exile this card from your graveyard: Return another target creature card from your graveyard to your hand.", "4/1")
    private val lionsTorment = Cards.invent("Lion's Torment", "{X}{R}", "Enchantment", """
        When this enchantment enters, it deals X damage to any target.
        Creatures you control have trample.
    """)
    private val artificersInsight = Cards.invent("Artificer's Insight", "{R}", "Sorcery", """
        Creatures you control gain first strike until end of turn.
        Draw a card.
    """)
    private val rouseTheBlaze = Cards.invent("Rouse the Blaze", "{4}{R}", "Sorcery", """
        Rouse the Blaze deals 6 damage to target creature or planeswalker an opponent controls. Put a +1/+1 counter on up to one target creature you control.
        Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
    """)
    private val fieryClimax = Cards.invent("Fiery Climax", "{1}{R}", "Instant", """
        Target creature gets +3/+1 until end of turn.
        Exile the top card of your library. Until the end of your next turn, you may play that card.
    """)
    private val pyromancersSparkling = Cards.invent("Pyromancer's Sparkling", "{2}{R}", "Creature — Gremlin Elemental", """
        Haste
        Whenever you cast a noncreature spell, put a +1/+1 counter on this creature.
    """, "2/2")
    private val cragmawBehemoth = Cards.invent("Cragmaw Behemoth", "{4}{R}{R}{R}", "Artifact Creature — Beast Construct", """
        Haste
        When this creature enters, creatures you control gain trample and get +X/+0 until end of turn, where X is the number of artifacts you control.
    """, "5/5")
    private val hexscarredFiend = Cards.invent("Hexscarred Fiend", "{2}{R}{R}", "Creature — Demon", """
        Flying, trample
        When this creature enters, search your library for a card, put it into your hand, shuffle, then discard a card at random.
    """, "4/4")
    private val eardrumSplitter = Cards.invent("Eardrum Splitter", "{1}{R}", "Creature — Human Bard", "{1}, {T}: Another target creature you control with power 2 or less can't be blocked this turn.", "2/2")
    private val thunderingFortissimo = Cards.invent("Thundering Fortissimo", "{2}{R}", "Instant", """
        Choose one —
        • Thundering Fortissimo deals 1 damage to each creature and planeswalker your opponents control.
        • Thundering Fortissimo deals 5 damage to target creature or planeswalker.
    """)
    private val heartcordTugger = Cards.invent("Heartcord Tugger", "{3}{R}", "Creature — Elf Sorcerer", """
        Trample
        When this creature enters, create a 2/2 colorless Wizard Soldier creature token named Cadet.
    """, "3/1")
    private val personaReverberation = Cards.invent("Persona Reverberation", "{2}{R}", "Enchantment", "{3}{R}: Exile target creature or planeswalker you control. Reveal cards from the top of your library until you reveal a creature or planeswalker card. Put that card onto the battlefield and the rest on the bottom of your library in a random order. Activate only as a sorcery.")
    private val sovereignOfSpines = Cards.invent("Sovereign of Spines", "{1}{R}", "Creature — Lizard Bard", """
        Menace
        Whenever one or more opponents are dealt noncombat damage, creatures you control get +1/+0 until end of turn.
    """, "2/1")
    private val deftBladeshaper = Cards.invent("Deft Bladeshaper", "{1}{R}", "Creature — Human Warrior", """
        During your turn, this creature has first strike.
        {1}{R}: This creature gets +1/+0 until end of turn.
    """, "2/1")
    private val barbcasterAdept = Cards.invent("Barbcaster Adept", "{1}{R}", "Creature — Human Wizard", """
        Haste
        When this creature enters, target instant or sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal to its mana cost. (You may cast that card from your graveyard for its flashback cost. Then exile it.)
    """, "2/1")
    private val tetherlineEngineer = Cards.invent("Tetherline Engineer", "{4}{R}", "Creature — Minotaur Artificer", """
        Reach
        When this creature enters, you may discard a card. When you do, this creature deals 2 damage to any target.
    """, "4/5")
    private val furyOfTheCrimsonmane = Cards.invent("Fury of the Crimsonmane", "{2}{R}", "Instant", """
        This spell costs {1} less to cast if you control a legendary creature.
        Fury of the Crimsonmane deals 4 damage to target creature or planeswalker.
    """)
    private val blossomingRebel = Cards.invent("Blossoming Rebel", "{2}{G}", "Creature — Dryad Scout", """
        Vigilance
        Sacrifice this creature: Destroy target artifact or enchantment. If that permanent was a legendary enchantment, draw a card. Activate only as a sorcery.
    """, "3/3")
    private val compulsorySavagery = Cards.invent("Compulsory Savagery", "{1}{G}", "Instant", """
        Choose one —
        • Target creature you control deals damage equal to its power to target creature or planeswalker an opponent controls.
        • Target planeswalker you control deals damage equal to its loyalty to target creature or planeswalker an opponent controls.
    """)
    private val thrivingClinch = Cards.invent("Thriving Clinch", "{G}", "Instant", "Target creature or planeswalker an opponent controls that's red or white loses all abilities until end of turn. Target creature you control deals damage equal to its power to that permanent.")
    private val gardenwildBloom = Cards.invent("Gardenwild Bloom", "{1}{G}{G}", "Enchantment", """
        Whenever a creature you control dies, put a charge counter on this enchantment.
        At the beginning of your first main phase, add {G} for each charge counter on this enchantment.
    """)
    private val hothouseCultivator = Cards.invent("Hothouse Cultivator", "{2}{G}", "Creature — Cat Druid", """
        Whenever another creature you control enters, you gain 1 life.
        {T}: Add {G}.
    """, "2/3")
    private val ravenousMarionette = Cards.invent("Ravenous Marionette", "{3}{G}{G}", "Artifact Creature — Beast Construct", """
        When this creature enters, create a Heartwood token. (It's a red and green artifact with "{T}: Add {R} or {G}.")
        {1}, Sacrifice another artifact: Put a +1/+1 counter on this creature. It gains your choice of trample, hexproof, or haste until end of turn.
    """, "5/5")
    private val stalkersHatchet = Cards.invent("Stalker's Hatchet", "{G}", "Artifact — Equipment", """
        Equipped creature gets +2/+0 and has "Whenever this creature attacks, it gains your choice of trample or deathtouch until end of turn."
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val marionetteMaking = Cards.invent("Marionette Making", "{1}{G}", "Enchantment — Aura", """
        Enchant artifact or non-Aura enchantment
        Enchanted permanent is a Construct creature with base power and toughness 5/5 in addition to its other types.
        {4}{G}: Return this card from your graveyard to your hand.
    """)
    private val mendWithCompassion = Cards.invent("Mend with Compassion", "{2}{G}", "Instant", "Return target permanent card from your graveyard to your hand. You gain 4 life.")
    private val facsimileShaper = Cards.invent("Facsimile Shaper", "{1}{G}{G}", "Creature — Elf Druid", """
        When this creature enters, you may search your library for a basic land card, put that card onto the battlefield tapped, then shuffle.
        When this creature dies, draw a card.
    """, "2/2")
    private val worthPreserving = Cards.invent("Worth Preserving", "{1}{G}", "Instant", "Mill four cards. You may put a permanent card from among them into your hand. You gain 1 life. (To mill four cards, put the top four cards of your library into your graveyard.)")
    private val trueshotSeeder = Cards.invent("Trueshot Seeder", "{1}{G}", "Creature — Human Archer", """
        Reach
        {3}{G}, Discard this card: Destroy target creature with flying.
    """, "3/1")
    private val marlgoyf = Cards.invent("Marlgoyf", "{1}{G}", "Creature — Lhurgoyf", "Marlgoyf's power is equal to the number of card types among cards in all graveyards and its toughness is equal to that number plus 1.", "*/1+*")
    private val tetherersEdge = Cards.invent("Tetherer's Edge", "{G}", "Instant", "Target creature gets +2/+2 and gains reach until end of turn. Untap it.")
    private val verdureKraken = Cards.invent("Verdure Kraken", "{4}{G}{G}{G}", "Creature — Plant Kraken", "At the beginning of each player's upkeep, you create a 3/3 green Forest Tentacle land creature token. (It has \"{T}: Add {G}.\" It's affected by summoning sickness until your next turn.)", "6/6")
    private val vinewhipAdept = Cards.invent("Vinewhip Adept", "{4}{G}{G}", "Creature — Rhino Soldier", """
        Reach
        When this creature enters, put three +1/+1 counters on target creature.
        Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
    """, "2/4")
    private val demolitionGecko = Cards.invent("Demolition Gecko", "{4}{G}", "Artifact Creature — Lizard Construct", """
        Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
        {6}{G}{G}: This creature gets +4/+4 and gains trample until end of turn.
    """, "5/5")

    private val mill = Cards.invent("Campus Archive Spill", "{U}", "Sorcery", "Target player mills three cards.")
    private val disenchant = Cards.invent("Campus Cleansing", "{W}", "Instant", "Destroy target enchantment.")
    private val legend = Cards.invent("Campus Champion", "{1}{R}", "Legendary Creature — Human Warrior", "", "2/2")

    // ---- Chunk 06: white

    @Test
    fun destinyforgeHopefulReturnsALegend() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(destinyforgeHopeful)
        alice.graveyard(legend)
        alice.casts(destinyforgeHopeful)
        resolveAll()
        modes(0)
        targetsIfAsked(legend)
        resolveAll()
        expect { alice.hand has legend }
    }

    @Test
    fun destinyforgeHopefulShieldsACreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(destinyforgeHopeful)
        alice.casts(destinyforgeHopeful)
        resolveAll()
        modes(1)
        targets(bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Indestructible
            bear hasKeyword Keyword.Vigilance
        }
    }

    // CR 400.7: the returned creature is a new object, without its counters
    @Test
    fun flickerfurHoundBlinksOnCreatureSpells() = scenario {
        alice.battlefield(flickerfurHound, "Forest")
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.hand(elf)
        alice.casts(elf)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 0
            alice.battlefield has elf
        }
    }

    @Test
    fun bountifulRevivificationReturnsWithACounter() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(bountifulRevivification)
        alice.graveyard(bear)
        alice.casts(bountifulRevivification, target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun sproutTheConscriptsCountsLifeGained() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(heal, sproutTheConscripts)
        alice.casts(heal)
        resolveAll()
        alice.casts(sproutTheConscripts)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Cadet" } shouldBe 2 }
    }

    // CR 603.10a: its counters as it last existed
    @Test
    fun spliceSurgeonPassesItsCountersOn() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Swamp", bear)
        alice.hand(spliceSurgeon, doom)
        alice.casts(spliceSurgeon, paying = listOf("Plains", "Plains", "Plains"))
        resolveAll()
        expect { spliceSurgeon.counters(p1p1) shouldBe 1 }
        alice.casts(doom, target = spliceSurgeon)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun pathfinderHydraSharesACounter() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear)
        alice.hand(pathfinderHydra)
        alice.casts(pathfinderHydra, x = 2)
        resolveAll()
        expect { pathfinderHydra.counters(p1p1) shouldBe 2 }
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            pathfinderHydra.counters(p1p1) shouldBe 1
            bear.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun kinshipVerdictSparesTheChosenType() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(kinshipVerdict)
        bob.battlefield(ogre, bear)
        alice.casts(kinshipVerdict)
        resolveAll()
        answer(Answer.SubtypeChoice(Subtype("Bear")))
        resolveAll()
        expect {
            bob.graveyard has ogre
            bob.battlefield has bear
            alice.battlefield has bear
        }
    }

    // CR 610.3: exiled until this leaves the battlefield
    @Test
    fun recollectionSnareExilesUntilItLeaves() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(recollectionSnare, disenchant)
        bob.battlefield(ogre)
        alice.casts(recollectionSnare)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { bob.exile has ogre }
        alice.casts(disenchant, target = recollectionSnare)
        resolveAll()
        expect { bob.battlefield has ogre }
    }

    @Test
    fun foresightedReadinessCountersTwo() = scenario {
        alice.battlefield("Plains", "Plains", bear, elf)
        alice.hand(foresightedReadiness)
        alice.casts(foresightedReadiness, targets = listOf(bear, elf))
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            elf.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun denyTheProphecyExilesGreenOrBlue() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(denyTheProphecy)
        alice.library(ogre)
        bob.battlefield(bear, ogre)
        assertFailsWith<IllegalArgumentException> { alice.casts(denyTheProphecy, target = ogre) }
    }

    @Test
    fun denyTheProphecyExilesAGreenCreature() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(denyTheProphecy)
        alice.library(ogre)
        bob.battlefield(bear)
        alice.casts(denyTheProphecy, target = bear)
        resolveAll()
        chooseNone()
        resolveAll()
        expect { bob.exile has bear }
    }

    @Test
    fun homecomingToTheBrightReachesReturnsPermanents() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(homecomingToTheBrightReaches)
        alice.graveyard(bear, idol, "Forest", shock)
        alice.casts(homecomingToTheBrightReaches)
        resolveAll()
        expect {
            alice.battlefield has bear
            alice.battlefield has idol
            alice.graveyard has "Forest"
            alice.graveyard has shock
        }
    }

    @Test
    fun shardwingSteedPumpsTheTeam() = scenario {
        alice.battlefield(shardwingSteed, bear, "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.activates(shardwingSteed)
        resolveAll()
        expect {
            bear.power shouldBe 3
            shardwingSteed.power shouldBe 3
        }
    }

    @Test
    fun exactingIncisionDestroysOnlyTheTough() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(exactingIncision)
        bob.battlefield(bear, giant)
        assertFailsWith<IllegalArgumentException> { alice.casts(exactingIncision, modes = listOf(0), target = bear) }
        targets(giant)
        payIfAsked()
        resolveAll()
        expect {
            bob.graveyard has giant
            alice.life shouldBe 21
        }
    }

    @Test
    fun exactingIncisionDrawsAndHeals() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(exactingIncision)
        alice.library(bear)
        alice.casts(exactingIncision, modes = listOf(1))
        resolveAll()
        expect {
            alice.hand has bear
            alice.life shouldBe 22
        }
    }

    @Test
    fun undauntedHortiseerGrowsOnLifeGain() = scenario {
        alice.battlefield(undauntedHortiseer, "Plains")
        alice.hand(heal)
        alice.casts(heal)
        resolveAll()
        expect { undauntedHortiseer.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun thyDestinyHaltsDestroysTheCostly() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(thyDestinyHalts)
        alice.library(bear)
        bob.battlefield(bear, ogre)
        assertFailsWith<IllegalArgumentException> { alice.casts(thyDestinyHalts, target = bear) }
        targets(ogre)
        payIfAsked()
        resolveAll()
        chooseNone()
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    // ---- Chunk 07: blue

    @Test
    fun mercilessComputationsDrawsByMilledCards() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(mill, mercilessComputations)
        alice.library(elf, elf, elf, elf)
        bob.library(bear, bear, bear, bear)
        alice.casts(mill, target = "bob")
        resolveAll()
        alice.casts(mercilessComputations, target = "bob")
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
    }

    // CR 122.1d: stun counters
    @Test
    fun frostlogicAdeptStunsFromTheGraveyard() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.graveyard(frostlogicAdept)
        bob.battlefield(ogre)
        alice.activates(frostlogicAdept, target = ogre)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.Stun) shouldBe 1
            alice.exile has frostlogicAdept
        }
    }

    @Test
    fun scryingFencerTapsACreature() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(scryingFencer)
        bob.battlefield(ogre)
        alice.casts(scryingFencer)
        resolveAll()
        modes(0)
        targets(ogre)
        resolveAll()
        expect { ogre.tapped shouldBe true }
    }

    @Test
    fun scryingFencerLoots() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(scryingFencer, bear)
        alice.library(ogre)
        alice.casts(scryingFencer)
        resolveAll()
        modes(2)
        resolveAll()
        alice.chooses(bear)
        resolveAll()
        expect {
            alice.hand has ogre
            alice.graveyard has bear
        }
    }

    @Test
    fun frigidWelcomeShrinksPower() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(frigidWelcome)
        bob.battlefield(giant)
        alice.casts(frigidWelcome, modes = listOf(1), target = giant)
        resolveAll()
        expect { giant.power shouldBe 0 }
    }

    // CR 118.12: "unless its controller pays"
    @Test
    fun frigidWelcomeTaxesACreatureSpell() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(frigidWelcome)
        bob.battlefield("Forest", "Forest")
        bob.hand(bear)
        turn(active = bob)
        bob.casts(bear)
        bob.passes()
        alice.casts(frigidWelcome, modes = listOf(0), target = bear)
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect { bob.graveyard has bear }
    }

    @Test
    fun polishedHypothesisSetsBaseFourFive() = scenario {
        alice.battlefield("Island", bear)
        alice.hand(polishedHypothesis)
        alice.casts(polishedHypothesis, modes = listOf(1), target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear.toughness shouldBe 5
        }
    }

    @Test
    fun exactExcisionCountersABlackSpell() = scenario {
        alice.battlefield("Island", "Island", bear)
        alice.hand(exactExcision)
        bob.battlefield("Swamp")
        bob.hand(doom)
        turn(active = bob)
        bob.casts(doom, target = bear)
        bob.passes()
        alice.casts(exactExcision, target = doom)
        resolveAll()
        expect {
            alice.battlefield has bear
            bob.graveyard has doom
        }
    }

    // CR 707.2: a token copy of the card it was
    @Test
    fun sphinxOfMistakenProofsComesBackAsAToken() = scenario {
        alice.battlefield(sphinxOfMistakenProofs, "Swamp")
        alice.hand(doom)
        alice.casts(doom, target = sphinxOfMistakenProofs)
        resolveAll()
        expect {
            alice.graveyard has sphinxOfMistakenProofs
            alice.battlefield has sphinxOfMistakenProofs
        }
    }

    @Test
    fun sphinxOfMistakenProofsLootsOnAttack() = scenario {
        alice.battlefield(sphinxOfMistakenProofs)
        alice.hand(bear)
        alice.library(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(sphinxOfMistakenProofs)
        resolveAll()
        alice.chooses(bear)
        resolveAll()
        expect {
            alice.hand has ogre
            alice.graveyard has bear
        }
    }

    // CR 702.3b: defender, unless an effect lets it attack
    @Test
    fun watchfulPhantasmAttacksAfterASurveil() = scenario {
        alice.battlefield(watchfulPhantasm, "Island", "Island", "Island", "Island")
        alice.library(bear, bear)
        alice.activates(watchfulPhantasm)
        resolveAll()
        chooseNone()
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(watchfulPhantasm)
        expect { watchfulPhantasm.tapped shouldBe false }
    }

    @Test
    fun watchfulPhantasmCantAttackWithoutASurveil() = scenario {
        alice.battlefield(watchfulPhantasm)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(watchfulPhantasm) }
    }

    @Test
    fun ripplingObserverTradesToughnessForPower() = scenario {
        alice.battlefield(ripplingObserver, "Island", "Island")
        alice.activates(ripplingObserver)
        resolveAll()
        expect {
            ripplingObserver.power shouldBe 4
            ripplingObserver.toughness shouldBe 4
        }
    }

    // ---- Chunk 08: black

    @Test
    fun pinnacleHexstalkerGainsOnEnteringAndDying() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(pinnacleHexstalker, doom)
        alice.casts(pinnacleHexstalker, paying = listOf("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp"))
        resolveAll()
        alice.casts(doom, target = pinnacleHexstalker)
        resolveAll()
        expect { alice.life shouldBe 24 }
    }

    @Test
    fun crackUnderStrainTakesTheCostliest() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(crackUnderStrain)
        bob.battlefield(bear, giant)
        alice.casts(crackUnderStrain, target = "bob")
        resolveAll()
        if (pending is Decision.ChooseObjects) bob.chooses(giant)
        resolveAll()
        expect {
            bob.graveyard has giant
            bob.battlefield has bear
            alice.life shouldBe 22
        }
    }

    @Test
    fun shedAllHesitationDrawsAndHurtsEveryone() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(shedAllHesitation)
        alice.library(bear, bear)
        alice.casts(shedAllHesitation)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.life shouldBe 18
            bob.life shouldBe 18
        }
    }

    // CR 603.4: the intervening "if" on two deaths this turn
    @Test
    fun duskflarePhoenixReturnsAfterTwoDeaths() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard(duskflarePhoenix)
        alice.hand(doom, doom)
        bob.battlefield(bear, elf)
        alice.casts(doom, target = bear)
        resolveAll()
        alice.casts(doom, target = elf)
        resolveAll()
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { alice.battlefield has duskflarePhoenix }
    }

    @Test
    fun prolongedDepartureExilesAndDrains() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(prolongedDeparture)
        bob.battlefield(ogre)
        alice.casts(prolongedDeparture, target = ogre)
        resolveAll()
        expect {
            bob.exile has ogre
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    // CR 704.5f: base 0/0 dies
    @Test
    fun timesNothingZeroesACreature() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(timesNothing)
        bob.battlefield(giant)
        alice.casts(timesNothing, target = giant)
        resolveAll()
        expect { bob.graveyard has giant }
    }

    @Test
    fun bulwarkStalkerGivesDeathtouch() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(bulwarkStalker)
        alice.casts(bulwarkStalker)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Deathtouch
        }
    }

    @Test
    fun foulVerminMakesThemDiscard() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(foulVermin)
        bob.hand(bear)
        alice.casts(foulVermin)
        resolveAll()
        if (pending is Decision.ChooseObjects) bob.chooses(bear)
        resolveAll()
        expect { bob.graveyard has bear }
    }

    @Test
    fun ascentOfTheDoombringerDrawsByPower() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", ogre, bear)
        alice.hand(ascentOfTheDoombringer)
        alice.library(elf, elf, elf, elf)
        alice.casts(ascentOfTheDoombringer, modes = listOf(0))
        resolveAll()
        expect {
            alice.hand.size shouldBe 3
            alice.life shouldBe 17
        }
    }

    @Test
    fun ascentOfTheDoombringerShrinksAll() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(ascentOfTheDoombringer)
        bob.battlefield(ogre, giant)
        alice.casts(ascentOfTheDoombringer, modes = listOf(1))
        resolveAll()
        expect {
            alice.graveyard has bear
            bob.graveyard has ogre
            giant.power shouldBe 2
        }
    }

    @Test
    fun shriekingSpiritcrusherDrainsOnAttack() = scenario {
        alice.battlefield(shriekingSpiritcrusher)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(shriekingSpiritcrusher)
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    // CR 601.2b, 601.2h: an additional cost chosen among two
    @Test
    fun hushTheReverberationBySacrifice() = scenario {
        alice.battlefield("Swamp", "Swamp", bear)
        alice.hand(hushTheReverberation)
        bob.battlefield(ogre)
        alice.casts(hushTheReverberation, target = ogre, option = "Sacrifice", choosing = listOf(bear))
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.graveyard has bear
        }
    }

    @Test
    fun hushTheReverberationByMana() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(hushTheReverberation)
        bob.battlefield(ogre)
        alice.casts(hushTheReverberation, target = ogre, option = "Pay {3}")
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.battlefield has bear
        }
    }

    @Test
    fun fatalCritiqueDestroysBlueOrRed() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(fatalCritique)
        bob.battlefield(ogre, bear)
        assertFailsWith<IllegalArgumentException> { alice.casts(fatalCritique, target = bear) }
        targets(ogre)
        payIfAsked()
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.life shouldBe 21
        }
    }

    @Test
    fun hypotheticalGravecallerReturnsAnother() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(hypotheticalGravecaller, ogre)
        alice.activates(hypotheticalGravecaller, target = ogre)
        resolveAll()
        expect {
            alice.hand has ogre
            alice.exile has hypotheticalGravecaller
        }
    }

    // ---- Chunk 09: red

    @Test
    fun lionsTormentBurnsAndGrantsTrample() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", bear)
        alice.hand(lionsTorment)
        alice.casts(lionsTorment, x = 2)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect {
            bob.life shouldBe 18
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun artificersInsightFirstStrikeAndADraw() = scenario {
        alice.battlefield("Mountain", bear)
        alice.hand(artificersInsight)
        alice.library(ogre)
        alice.casts(artificersInsight)
        resolveAll()
        expect {
            bear hasKeyword Keyword.FirstStrike
            alice.hand has ogre
        }
    }

    @Test
    fun rouseTheBlazeBurnsAndCounters() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", bear)
        alice.hand(rouseTheBlaze)
        bob.battlefield(giant)
        alice.casts(rouseTheBlaze, targets = listOf(giant, bear))
        resolveAll()
        expect {
            bob.graveyard has giant
            bear.counters(p1p1) shouldBe 1
        }
    }

    // CR 611.2: "until the end of your next turn, you may play that card"
    @Test
    fun fieryClimaxPumpsAndExilesAPlayableCard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", bear)
        alice.hand(fieryClimax)
        alice.library(ogre)
        alice.casts(fieryClimax, target = bear, paying = listOf("Mountain", "Mountain"))
        resolveAll()
        expect {
            bear.power shouldBe 5
            alice.exile has ogre
        }
        alice.casts(ogre)
        resolveAll()
        expect { alice.battlefield has ogre }
    }

    @Test
    fun pyromancersSparklingGrowsOnSpells() = scenario {
        alice.battlefield(pyromancersSparkling, "Mountain")
        alice.hand(shock)
        alice.casts(shock, target = "bob")
        resolveAll()
        expect { pyromancersSparkling.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun cragmawBehemothCountsArtifacts() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", idol, bear)
        alice.hand(cragmawBehemoth)
        alice.casts(cragmawBehemoth)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun hexscarredFiendTutorsThenDiscardsAtRandom() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(hexscarredFiend)
        alice.library(bear, ogre)
        alice.casts(hexscarredFiend)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.hand.size shouldBe 0
        }
    }

    // CR 509.1b: can't be blocked
    @Test
    fun eardrumSplitterMakesASmallCreatureUnblockable() = scenario {
        alice.battlefield(eardrumSplitter, bear, "Mountain")
        bob.battlefield(ogre)
        alice.activates(eardrumSplitter, target = bear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to bear) }
    }

    @Test
    fun thunderingFortissimoPingsTheirSide() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", elf)
        alice.hand(thunderingFortissimo)
        bob.battlefield(elf, bear)
        alice.casts(thunderingFortissimo, modes = listOf(0))
        resolveAll()
        expect {
            bob.graveyard has elf
            alice.battlefield has elf
            bob.battlefield has bear
        }
    }

    @Test
    fun heartcordTuggerMakesACadet() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(heartcordTugger)
        alice.casts(heartcordTugger)
        resolveAll()
        expect { alice.battlefield has "Cadet" }
    }

    // CR 701.20 (reveal), 401.4: the rest to the bottom in a random order
    @Test
    fun personaReverberationSwapsACreature() = scenario {
        alice.battlefield(personaReverberation, bear, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.library("Forest", ogre, elf)
        alice.activates(personaReverberation, target = bear)
        resolveAll()
        expect {
            alice.exile has bear
            alice.battlefield has ogre
            alice.library.names.first() shouldBe elf
        }
    }

    @Test
    fun sovereignOfSpinesPumpsOnNoncombatDamage() = scenario {
        alice.battlefield(sovereignOfSpines, bear, "Mountain")
        alice.hand(shock)
        alice.casts(shock, target = "bob")
        resolveAll()
        expect {
            sovereignOfSpines.power shouldBe 3
            bear.power shouldBe 3
        }
    }

    @Test
    fun deftBladeshaperFirstStrikeOnItsTurn() = scenario {
        alice.battlefield(deftBladeshaper, "Mountain", "Mountain")
        alice.library(bear)
        bob.library(bear)
        expect { deftBladeshaper hasKeyword Keyword.FirstStrike }
        alice.activates(deftBladeshaper)
        resolveAll()
        expect { deftBladeshaper.power shouldBe 3 }
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { deftBladeshaper lacksKeyword Keyword.FirstStrike }
    }

    // CR 702.34a: flashback granted with a cost equal to the card's mana cost
    @Test
    fun barbcasterAdeptGrantsFlashback() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(barbcasterAdept)
        alice.graveyard(shock)
        alice.casts(barbcasterAdept)
        resolveAll()
        targetsIfAsked(shock)
        resolveAll()
        alice.casts(shock, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.exile has shock
        }
    }

    // CR 603.12: a reflexive trigger
    @Test
    fun tetherlineEngineerBurnsForADiscard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(tetherlineEngineer, bear)
        alice.casts(tetherlineEngineer)
        resolveAll()
        may(true)
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect {
            alice.graveyard has bear
            bob.life shouldBe 18
        }
    }

    // CR 601.2f: a cost reduction while you control a legendary creature
    @Test
    fun furyOfTheCrimsonmaneCostsLessWithALegend() = scenario {
        alice.battlefield(legend, "Mountain", "Mountain")
        alice.hand(furyOfTheCrimsonmane)
        bob.battlefield(giant)
        alice.casts(furyOfTheCrimsonmane, target = giant)
        resolveAll()
        expect { card(giant).view.obj!!.damage shouldBe 4 }
    }

    // ---- Chunk 10: green

    @Test
    fun blossomingRebelDestroysALegendaryEnchantmentAndDraws() = scenario {
        alice.battlefield(blossomingRebel)
        alice.library(bear)
        bob.battlefield(pathOfTheTutor)
        alice.activates(blossomingRebel, target = pathOfTheTutor)
        resolveAll()
        expect {
            bob.graveyard has pathOfTheTutor
            alice.hand has bear
        }
    }

    @Test
    fun compulsorySavageryBites() = scenario {
        alice.battlefield("Forest", "Forest", giant)
        alice.hand(compulsorySavagery)
        bob.battlefield(ogre)
        alice.casts(compulsorySavagery, modes = listOf(0), targets = listOf(giant, ogre))
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun compulsorySavageryPlaneswalkerBites() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.battlefield(walker, counters = listOf(CounterKind.Loyalty to 3))
        alice.hand(compulsorySavagery)
        bob.battlefield(ogre)
        alice.casts(compulsorySavagery, modes = listOf(1), targets = listOf(walker, ogre))
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun thrivingClinchStripsAndBites() = scenario {
        alice.battlefield("Forest", ogre)
        alice.hand(thrivingClinch)
        bob.battlefield(sphinxOfMistakenProofs)
        assertFailsWith<IllegalArgumentException> { alice.casts(thrivingClinch, targets = listOf(sphinxOfMistakenProofs, ogre)) }
    }

    @Test
    fun thrivingClinchOnARedCreature() = scenario {
        alice.battlefield("Forest", giant)
        alice.hand(thrivingClinch)
        bob.battlefield(ogre)
        alice.casts(thrivingClinch, targets = listOf(ogre, giant))
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun gardenwildBloomStoresChargeForMana() = scenario {
        alice.battlefield(gardenwildBloom, bear, "Swamp")
        alice.hand(doom)
        alice.library(elf, elf)
        bob.library(elf, elf)
        alice.casts(doom, target = bear)
        resolveAll()
        expect { gardenwildBloom.counters(CounterKind.Named("charge")) shouldBe 1 }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        expect { alice.pool.size shouldBe 1 }
    }

    @Test
    fun hothouseCultivatorGainsOnArrivals() = scenario {
        alice.battlefield(hothouseCultivator, "Forest")
        alice.hand(elf)
        alice.casts(elf)
        resolveAll()
        expect { alice.life shouldBe 21 }
    }

    @Test
    fun ravenousMarionetteEatsArtifacts() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(ravenousMarionette)
        alice.casts(ravenousMarionette, paying = listOf("Forest", "Forest", "Forest", "Forest", "Forest"))
        resolveAll()
        expect { alice.battlefield has "Heartwood Token" }
        alice.activates(ravenousMarionette, choosing = listOf("Heartwood Token"))
        resolveAll()
        if (pending is Decision.ChooseOption) option("trample")
        resolveAll()
        expect {
            ravenousMarionette.counters(p1p1) shouldBe 1
            ravenousMarionette hasKeyword Keyword.Trample
            alice.battlefield lacks "Heartwood Token"
        }
    }

    @Test
    fun stalkersHatchetGivesAChoiceOnAttack() = scenario {
        alice.battlefield(stalkersHatchet, bear, "Forest", "Forest")
        alice.activates(stalkersHatchet, target = bear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        option("deathtouch")
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Deathtouch
        }
    }

    @Test
    fun marionetteMakingAnimatesAnArtifact() = scenario {
        alice.battlefield("Forest", "Forest", idol)
        alice.hand(marionetteMaking)
        alice.casts(marionetteMaking, target = idol)
        resolveAll()
        expect {
            idol.power shouldBe 5
            idol.types.contains(CardType.Creature) shouldBe true
        }
    }

    @Test
    fun marionetteMakingReturnsFromTheGraveyard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.graveyard(marionetteMaking)
        alice.activates(marionetteMaking)
        resolveAll()
        expect { alice.hand has marionetteMaking }
    }

    @Test
    fun mendWithCompassionReturnsAPermanentCard() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(mendWithCompassion)
        alice.graveyard(idol)
        alice.casts(mendWithCompassion, target = idol)
        resolveAll()
        expect {
            alice.hand has idol
            alice.life shouldBe 24
        }
    }

    @Test
    fun facsimileShaperRampsAndDrawsOnDying() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Swamp")
        alice.hand(facsimileShaper, doom)
        alice.library(bear, "Forest")
        alice.casts(facsimileShaper, paying = listOf("Forest", "Forest", "Forest"))
        resolveAll()
        may(true)
        if (pending is Decision.ChooseObjects) alice.chooses("Forest")
        resolveAll()
        expect { alice.battlefield.names.count { it == "Forest" } shouldBe 4 }
        alice.casts(doom, target = facsimileShaper)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun worthPreservingKeepsAPermanentCard() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(worthPreserving)
        alice.library(shock, ogre, ponder, elf)
        alice.casts(worthPreserving)
        resolveAll()
        if (pending is Decision.May) may(true)
        alice.chooses(ogre)
        resolveAll()
        expect {
            alice.hand has ogre
            alice.graveyard has shock
            alice.life shouldBe 21
        }
    }

    @Test
    fun trueshotSeederShootsAFlyer() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(trueshotSeeder)
        bob.battlefield(flyer, bear)
        alice.activates(trueshotSeeder, target = flyer)
        resolveAll()
        expect {
            bob.graveyard has flyer
            alice.graveyard has trueshotSeeder
        }
    }

    // CR 604.3: a characteristic-defining ability
    @Test
    fun marlgoyfCountsCardTypes() = scenario {
        alice.battlefield(marlgoyf)
        alice.graveyard(bear, shock)
        bob.graveyard("Forest")
        expect {
            marlgoyf.power shouldBe 3
            marlgoyf.toughness shouldBe 4
        }
    }

    @Test
    fun tetherersEdgeUntapsAndPumps() = scenario {
        alice.battlefield("Forest")
        alice.battlefield(bear, tapped = true)
        alice.hand(tetherersEdge)
        alice.casts(tetherersEdge, target = bear)
        resolveAll()
        expect {
            bear.tapped shouldBe false
            bear.power shouldBe 4
            bear hasKeyword Keyword.Reach
        }
    }

    @Test
    fun verdureKrakenGrowsTentaclesEachUpkeep() = scenario {
        alice.battlefield(verdureKraken)
        alice.library(bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        expect {
            alice.battlefield has "Forest Tentacle Token"
            "Forest Tentacle Token".power shouldBe 3
        }
    }

    @Test
    fun vinewhipAdeptGivesThreeCounters() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", bear)
        alice.hand(vinewhipAdept)
        alice.casts(vinewhipAdept)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 3 }
    }

    @Test
    fun demolitionGeckoGrowsBig() = scenario {
        alice.battlefield(demolitionGecko, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.activates(demolitionGecko)
        resolveAll()
        expect {
            demolitionGecko.power shouldBe 9
            demolitionGecko hasKeyword Keyword.Trample
        }
    }
}
