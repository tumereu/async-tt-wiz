package arcanum.engine.sets.gtc

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.gtc.Gtc.bear
import arcanum.engine.sets.gtc.Gtc.giant
import arcanum.engine.sets.gtc.Gtc.human
import arcanum.engine.sets.gtc.Gtc.idol
import arcanum.engine.sets.gtc.Gtc.ogre
import arcanum.engine.sets.gtc.Gtc.p1p1
import arcanum.engine.sets.gtc.Gtc.pump
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Gatecrash's battalion, bloodrush, extort, evolve and cipher cards in play (parser chunks 01–05), beyond the mechanics played in [GtcMechanicsScenariosTest]. */
class GtcMechanicCardsScenariosTest {
    private val skyjekDaredevil = Cards.invent("Skyjek Daredevil", "{1}{W}", "Creature — Human Knight", "Battalion — Whenever this creature and at least two other creatures attack, this creature gains flying until end of turn.", "3/1")
    private val battlefieldMender = Cards.invent("Battlefield Mender", "{2}{W}", "Creature — Human Cleric", """
        Battalion — Whenever this creature and at least two other creatures attack, creatures you control gain indestructible until end of turn.
        Sacrifice this creature: Counter target spell with {X} in its mana cost unless its controller pays {3}.
    """, "3/3")
    private val squadronBreaker = Cards.invent("Squadron Breaker", "{4}{W}", "Creature — Human Soldier", "Battalion — Whenever this creature and at least two other creatures attack, this creature gets +1/+1 until end of turn. Untap it.", "3/5")
    private val sapperBrigade = Cards.invent("Sapper Brigade", "{1}{R}", "Creature — Human Soldier", "Battalion — Whenever this creature and at least two other creatures attack, this creature deals 1 damage to any target.", "1/2")
    private val fistfireSkirmisher = Cards.invent("Fistfire Skirmisher", "{1}{R}", "Creature — Human Soldier", "Battalion — Whenever this creature and at least two other creatures attack, target creature can't block this turn.", "2/1")
    private val rallyGoblin = Cards.invent("Rally Goblin", "{R}", "Creature — Goblin Soldier", """
        Haste
        Battalion — Whenever this creature and at least two other creatures attack, creatures you control gain first strike and trample until end of turn and can't be blocked by creature tokens this turn.
    """, "1/1")
    private val warmindFootman = Cards.invent("Warmind Footman", "{2}{R}", "Creature — Elemental Soldier", "Battalion — Whenever this creature and at least two other creatures attack, this creature gets +2/+0 until end of turn.", "2/3")
    private val pyreheartAvenger = Cards.invent("Pyreheart Avenger", "{2}{R}{W}", "Creature — Angel", """
        Flying
        Battalion — Whenever this creature and at least two other creatures attack, this creature deals 3 damage to any target and you gain 3 life.
    """, "3/3")
    private val hornlineVeteran = Cards.invent("Hornline Veteran", "{2}{R}{W}", "Creature — Minotaur Soldier", "Battalion — Whenever this creature and at least two other creatures attack, this creature gains double strike until end of turn. (It deals both first-strike and regular combat damage.)", "3/1")
    private val wojekPikemen = Cards.invent("Wojek Pikemen", "{R}{W}", "Creature — Human Soldier", "Battalion — Whenever this creature and at least two other creatures attack, this creature gains first strike until end of turn.", "3/2")
    private val cinderstrideElemental = Cards.invent("Cinderstride Elemental", "{3}{R}", "Creature — Elemental", "Bloodrush — {1}{R}{R}, Discard this card: Target attacking creature gets +5/+1 until end of turn.", "5/1")
    private val shankfangViashino = Cards.invent("Shankfang Viashino", "{3}{R}", "Creature — Lizard Warrior", """
        First strike
        Bloodrush — {2}{R}, Discard this card: Target attacking creature gets +3/+1 and gains first strike until end of turn.
    """, "3/1")
    private val ruinousOgre = Cards.invent("Ruinous Ogre", "{4}{R}", "Creature — Ogre Warrior", """
        Double strike
        Bloodrush — {3}{R}{R}, Discard this card: Target attacking creature gets +3/+3 and gains double strike until end of turn.
    """, "3/3")
    private val clanbruteCharger = Cards.invent("Clanbrute Charger", "{3}{G}", "Creature — Centaur Warrior", "Bloodrush — {1}{G}, Discard this card: Target attacking creature gets +2/+4 until end of turn.", "2/4")
    private val rubblemawGoliath = Cards.invent("Rubblemaw Goliath", "{6}{G}{G}", "Creature — Beast", """
        Trample
        Bloodrush — {5}{G}{G}, Discard this card: Target attacking creature gets +9/+9 and gains trample until end of turn.
    """, "9/9")
    private val gorehornBeast = Cards.invent("Gorehorn Beast", "{2}{G}", "Creature — Beast", "Bloodrush — {G}, Discard this card: Target attacking creature gets +3/+2 until end of turn.", "3/2")
    private val blightfangViper = Cards.invent("Blightfang Viper", "{G}", "Creature — Snake", """
        Deathtouch
        Bloodrush — {G}, Discard this card: Target attacking creature gets +1/+2 and gains deathtouch until end of turn.
    """, "1/2")
    private val ghorClanBruiser = Cards.invent("Ghor-Clan Bruiser", "{2}{R}{G}", "Creature — Beast", """
        Trample
        Bloodrush — {R}{G}, Discard this card: Target attacking creature gets +4/+4 and gains trample until end of turn.
    """, "4/4")
    private val quarryhulk = Cards.invent("Quarryhulk", "{4}{R}{G}", "Creature — Elemental", """
        Quarryhulk's power and toughness are each equal to the number of lands you control.
        Bloodrush — {1}{R}{G}, Discard this card: Target attacking creature gets +X/+X until end of turn, where X is the number of lands you control.
    """, "*/*")
    private val zhurTaaHog = Cards.invent("Zhur-Taa Hog", "{3}{R}{G}", "Creature — Boar", "Bloodrush — {1}{R}{G}, Discard this card: Target attacking creature gets +5/+4 until end of turn.", "5/4")
    private val bastionWarden = Cards.invent("Bastion Warden", "{2}{W}", "Creature — Human Soldier", """
        Defender
        Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
    """, "1/4")
    private val edictOfSubmission = Cards.invent("Edict of Submission", "{1}{W}", "Enchantment", """
        Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
        Artifacts and creatures your opponents control enter tapped.
    """)
    private val oathboundKnight = Cards.invent("Oathbound Knight", "{3}{W}", "Creature — Human Knight", """
        Vigilance
        Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
    """, "2/4")
    private val vaultwingBat = Cards.invent("Vaultwing Bat", "{1}{B}", "Creature — Bat", """
        Flying
        Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
    """, "1/2")
    private val ledgerWraith = Cards.invent("Ledger Wraith", "{3}{B}", "Creature — Spirit", """
        Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
        Whenever you tap a Swamp for mana, add an additional {B}.
    """, "2/2")
    private val collectionsThug = Cards.invent("Collections Thug", "{3}{B}", "Creature — Human Rogue", "Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)", "3/2")
    private val leechThrull = Cards.invent("Leech Thrull", "{B}", "Creature — Thrull", """
        Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
        {T}, Pay 2 life: Remove a counter from target nonland permanent.
    """, "1/1")
    private val tithingHound = Cards.invent("Tithing Hound", "{1}{W}{B}", "Creature — Thrull", """
        Flying
        Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
    """, "2/2")
    private val vaultThrull = Cards.invent("Vault Thrull", "{4}{W}{B}", "Creature — Thrull", """
        Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
        Whenever this creature attacks, you may return target artifact, creature, or enchantment card from your graveyard to your hand.
    """, "4/4")
    private val confessionExtractor = Cards.invent("Confession Extractor", "{3}{W}{B}", "Creature — Human Cleric", """
        Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
        When this creature enters, pay any amount of life. Target opponent reveals that many cards from their hand. You choose one of them and exile it.
    """, "1/3")
    private val clingingPolyps = Cards.invent("Clinging Polyps", "{3}{U}", "Creature — Jellyfish", """
        Defender
        Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
    """, "1/4")
    private val finnedRaptor = Cards.invent("Finned Raptor", "{U}", "Creature — Bird Mutant", """
        Flying
        Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
    """, "0/1")
    private val fluxmancerAdept = Cards.invent("Fluxmancer Adept", "{2}{U}", "Creature — Merfolk Wizard", """
        Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
        {1}{U}, {T}: Move a +1/+1 counter from this creature onto target creature.
    """, "1/2")
    private val mindgraftManipulator = Cards.invent("Mindgraft Manipulator", "{1}{U}{U}", "Creature — Mutant Wizard", """
        Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
        {T}, Remove one or more +1/+1 counters from this creature: Gain control of target creature with power less than or equal to the number of +1/+1 counters removed this way.
    """, "0/1")
    private val snappingMutant = Cards.invent("Snapping Mutant", "{4}{G}", "Creature — Lizard Beast", "Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)", "6/2")
    private val reedmawCroc = Cards.invent("Reedmaw Croc", "{2}{G}", "Creature — Crocodile Frog", """
        Reach (This creature can block creatures with flying.)
        Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
    """, "1/3")
    private val spiralSage = Cards.invent("Spiral Sage", "{1}{G}", "Creature — Elf Druid", """
        Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
        {T}: Add {G} for each +1/+1 counter on this creature.
    """, "1/2")
    private val slipstreamKrasis = Cards.invent("Slipstream Krasis", "{1}{G}{U}", "Creature — Fish Mutant", """
        This creature can't be blocked.
        Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
    """, "0/4")
    private val lurkingSharkcrab = Cards.invent("Lurking Sharkcrab", "{G}{U}", "Creature — Shark Crab", """
        Flash (You may cast this spell any time you could cast an instant.)
        Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
    """, "2/1")
    private val hybridGuildmage = Cards.invent("Hybrid Guildmage", "{G}{U}", "Creature — Elf Wizard", """
        {G}{U}: This turn, each creature you control enters with an additional +1/+1 counter on it.
        {G}{U}, Remove a +1/+1 counter from a creature you control: Draw a card.
    """, "2/2")
    private val adaptiveShift = Cards.invent("Adaptive Shift", "{G/U}", "Instant", "Move any number of +1/+1 counters from target creature onto another target creature with the same controller.")
    private val saphirDrake = Cards.invent("Saphir Drake", "{5}{U}", "Creature — Drake", """
        Flying
        Each creature you control with a +1/+1 counter on it has flying.
    """, "4/4")
    private val crestedCeratok = Cards.invent("Crested Ceratok", "{3}{G}", "Creature — Rhino", """
        Trample
        Each creature you control with a +1/+1 counter on it has trample.
    """, "4/3")
    private val coercedGrowth = Cards.invent("Coerced Growth", "{G}", "Enchantment — Aura", """
        Enchant creature
        At the beginning of your upkeep, put a +1/+1 counter on enchanted creature.
    """)
    private val sproutingSurge = Cards.invent("Sprouting Surge", "{G}", "Instant", "Put a +1/+1 counter on target creature and untap it.")
    private val thornvineWard = Cards.invent("Thornvine Ward", "{2}{G}", "Instant", "Prevent all combat damage that would be dealt this turn by creatures with no +1/+1 counters on them.")
    private val shacklingCipher = Cards.invent("Shackling Cipher", "{1}{U}", "Sorcery", """
        Tap target creature an opponent controls. That creature doesn't untap during its controller's next untap step.
        Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
    """)
    private val finalWhisper = Cards.invent("Final Whisper", "{3}{U}", "Sorcery", """
        Draw a card.
        Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
    """)
    private val borrowedVisage = Cards.invent("Borrowed Visage", "{4}{U}{U}", "Sorcery", """
        Create a token that's a copy of target artifact or creature.
        Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
    """)
    private val phaseStride = Cards.invent("Phase Stride", "{3}{U}", "Sorcery", """
        Exile target creature. Return it to the battlefield under its owner's control at the beginning of the next end step.
        Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
    """)
    private val encodedTorment = Cards.invent("Encoded Torment", "{3}{B}", "Sorcery", """
        Target player discards a card.
        Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
    """)
    private val midnightExhumation = Cards.invent("Midnight Exhumation", "{3}{B}", "Sorcery", """
        Return target creature card from your graveyard to your hand.
        Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
    """)
    private val cellarPlague = Cards.invent("Cellar Plague", "{4}{B}{B}", "Sorcery", """
        Target player loses 1 life, discards a card, then sacrifices a permanent of their choice.
        Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
    """)
    private val nightwingSummons = Cards.invent("Nightwing Summons", "{2}{U}{B}", "Sorcery", """
        Create a 1/1 blue and black Horror creature token with flying.
        Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
    """)
    private val paranoidVisions = Cards.invent("Paranoid Visions", "{U}{B}", "Sorcery", """
        Target player mills three cards.
        Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
    """)
    private val xBurn = Cards.invent("Ninth District Blast", "{X}{R}", "Sorcery", "Ninth District Blast deals X damage to any target.")

    /** [name] attacks with two others: battalion triggers (CR 207.2c, 508.1m). */
    private fun Scenario.battalion(name: String) {
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(name, bear, human)
    }

    // ---- Chunk 01: battalion (the ability word itself: GtcMechanicsScenariosTest)

    @Test
    fun skyjekDaredevilFlies() = scenario {
        alice.battlefield(skyjekDaredevil, bear, human)
        battalion(skyjekDaredevil)
        resolveAll()
        expect { skyjekDaredevil hasKeyword Keyword.Flying }
    }

    @Test
    fun battlefieldMenderMakesTheTeamIndestructible() = scenario {
        alice.battlefield(battlefieldMender, bear, human)
        battalion(battlefieldMender)
        resolveAll()
        expect { bear hasKeyword Keyword.Indestructible }
    }

    // CR 202.1, 107.3: a spell with {X} in its mana cost
    @Test
    fun battlefieldMenderCountersAnXSpell() = scenario {
        alice.battlefield(battlefieldMender)
        bob.battlefield("Mountain", "Mountain")
        bob.hand(xBurn)
        turn(active = bob)
        bob.casts(xBurn, target = "alice", x = 1)
        bob.passes()
        alice.activates(battlefieldMender, target = xBurn)
        resolveAll()
        if (pending is Decision.May) bob.answers(Answer.YesNo(false))
        resolveAll()
        expect {
            bob.graveyard has xBurn
            alice.life shouldBe 20
        }
    }

    @Test
    fun squadronBreakerUntaps() = scenario {
        alice.battlefield(squadronBreaker, bear, human)
        battalion(squadronBreaker)
        resolveAll()
        expect {
            squadronBreaker.power shouldBe 4
            squadronBreaker.tapped shouldBe false
        }
    }

    @Test
    fun sapperBrigadePings() = scenario {
        alice.battlefield(sapperBrigade, bear, human)
        battalion(sapperBrigade)
        targets("bob")
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun fistfireSkirmisherStopsABlocker() = scenario {
        alice.battlefield(fistfireSkirmisher, bear, human)
        bob.battlefield(giant)
        battalion(fistfireSkirmisher)
        targetsIfAsked(giant)
        resolveAll()
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(giant to bear) }
    }

    @Test
    fun rallyGoblinGivesFirstStrikeAndTrample() = scenario {
        alice.battlefield(rallyGoblin, bear, human)
        battalion(rallyGoblin)
        resolveAll()
        expect {
            bear hasKeyword Keyword.FirstStrike
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun warmindFootmanPumps() = scenario {
        alice.battlefield(warmindFootman, bear, human)
        battalion(warmindFootman)
        resolveAll()
        expect { warmindFootman.power shouldBe 4 }
    }

    @Test
    fun pyreheartAvengerBurnsAndGains() = scenario {
        alice.battlefield(pyreheartAvenger, bear, human)
        battalion(pyreheartAvenger)
        targets("bob")
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    @Test
    fun hornlineVeteranGainsDoubleStrike() = scenario {
        alice.battlefield(hornlineVeteran, bear, human)
        battalion(hornlineVeteran)
        resolveAll()
        expect { hornlineVeteran hasKeyword Keyword.DoubleStrike }
    }

    @Test
    fun wojekPikemenGainsFirstStrike() = scenario {
        alice.battlefield(wojekPikemen, bear, human)
        battalion(wojekPikemen)
        resolveAll()
        expect { wojekPikemen hasKeyword Keyword.FirstStrike }
    }

    // ---- Chunk 02: bloodrush (the ability word itself: GtcMechanicsScenariosTest)

    /** Each bloodrush card, discarded from the hand, pumps the attacking giant by its printed amounts. */
    // CR 602.2, 601.2h
    @Test
    fun eachBloodrushPumpsAnAttacker() {
        listOf(cinderstrideElemental to (5 to 1), shankfangViashino to (3 to 1), ruinousOgre to (3 to 3), clanbruteCharger to (2 to 4),
            rubblemawGoliath to (9 to 9), gorehornBeast to (3 to 2), blightfangViper to (1 to 2), ghorClanBruiser to (4 to 4),
            zhurTaaHog to (5 to 4)).forEach { (card, pump) ->
            scenario {
                alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Forest", "Forest", "Forest", giant)
                alice.hand(card)
                advanceTo(TurnStep.DeclareAttackers)
                alice.attacks(giant)
                alice.activates(card, target = giant)
                resolveAll()
                expect {
                    giant.power shouldBe 5 + pump.first
                    giant.toughness shouldBe 5 + pump.second
                    alice.graveyard has card
                }
            }
        }
    }

    @Test
    fun quarryhulkCountsLands() = scenario {
        alice.battlefield("Mountain", "Forest", "Forest", "Forest", quarryhulk)
        expect { quarryhulk.power shouldBe 4 }
    }

    @Test
    fun quarryhulkBloodrushesByLands() = scenario {
        alice.battlefield("Mountain", "Forest", "Forest", "Forest", giant)
        alice.hand(quarryhulk)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(giant)
        alice.activates(quarryhulk, target = giant)
        resolveAll()
        expect { giant.power shouldBe 9 }
    }

    // ---- Chunk 03: extort (the keyword itself: GtcMechanicsScenariosTest)

    /** Each extort permanent drains 1 when its controller casts a spell and pays {W/B}. */
    // CR 702.101a
    @Test
    fun eachExtortPermanentDrains() {
        listOf(bastionWarden, edictOfSubmission, oathboundKnight, vaultwingBat, collectionsThug, tithingHound, vaultThrull).forEach { card ->
            scenario {
                alice.battlefield(card, "Forest", "Forest", "Plains")
                alice.hand(bear)
                alice.casts(bear, paying = listOf("Forest", "Forest#1"))
                resolveAll()
                may(true)
                payIfAsked()
                resolveAll()
                expect {
                    bob.life shouldBe 19
                    alice.life shouldBe 21
                }
            }
        }
    }

    // CR 614.1c: their artifacts and creatures enter tapped
    @Test
    fun edictOfSubmissionTapsTheirArrivals() = scenario {
        alice.battlefield(edictOfSubmission)
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(ogre)
        turn(active = bob)
        bob.casts(ogre)
        resolveAll()
        expect { ogre.tapped shouldBe true }
    }

    // CR 106.12a: an additional mana when a Swamp is tapped
    @Test
    fun ledgerWraithDoublesSwamps() = scenario {
        alice.battlefield(ledgerWraith, "Swamp")
        alice.activates("Swamp")
        expect { alice.pool.size shouldBe 2 }
    }

    @Test
    fun leechThrullRemovesACounter() = scenario {
        alice.battlefield(leechThrull)
        bob.battlefield(ogre, counters = listOf(p1p1 to 2))
        alice.activates(leechThrull, target = ogre)
        resolveAll()
        expect {
            ogre.counters(p1p1) shouldBe 1
            alice.life shouldBe 18
        }
    }

    @Test
    fun vaultThrullReturnsACardWhenAttacking() = scenario {
        alice.battlefield(vaultThrull)
        alice.graveyard(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(vaultThrull)
        targetsIfAsked(bear)
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.hand has bear }
    }

    // CR 107.1b: "pay any amount of life"
    @Test
    fun confessionExtractorExilesARevealedCard() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Swamp", "Swamp")
        alice.hand(confessionExtractor)
        bob.hand(ogre, giant)
        alice.casts(confessionExtractor)
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        if (pending is Decision.ChooseNumber) answer(Answer.Number(2))
        if (pending is Decision.Announce) answer(Answer.Number(2))
        targetsIfAsked("bob")
        resolveAll()
        if (pending is Decision.ChooseNumber) answer(Answer.Number(2))
        if (pending is Decision.Announce) answer(Answer.Number(2))
        resolveAll()
        alice.chooses(giant)
        resolveAll()
        expect {
            bob.exile has giant
            alice.life shouldBe 18
        }
    }

    // ---- Chunk 04: evolve and +1/+1 counters (evolve itself: GtcMechanicsScenariosTest)

    /** Each evolve creature gets a counter when a bigger creature enters under its controller's control. */
    // CR 702.100a
    @Test
    fun eachEvolveCreatureEvolves() {
        listOf(clingingPolyps, finnedRaptor, fluxmancerAdept, mindgraftManipulator, reedmawCroc, spiralSage, slipstreamKrasis,
            lurkingSharkcrab, snappingMutant).forEach { card ->
            scenario {
                alice.battlefield(card, "Forest", "Forest", "Forest", "Forest", "Forest")
                alice.hand(Gtc.giant)
                alice.casts(Gtc.giant)
                resolveAll()
                expect { card.counters(p1p1) shouldBe 1 }
            }
        }
    }

    @Test
    fun fluxmancerAdeptMovesACounter() = scenario {
        alice.battlefield(fluxmancerAdept, counters = listOf(p1p1 to 1))
        alice.battlefield(bear, "Island", "Island")
        alice.activates(fluxmancerAdept, target = bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            fluxmancerAdept.counters(p1p1) shouldBe 0
        }
    }

    // CR 107.1b-like: "the number of counters removed this way"
    @Test
    fun mindgraftManipulatorStealsBySpentCounters() = scenario {
        alice.battlefield(mindgraftManipulator, counters = listOf(p1p1 to 2))
        bob.battlefield(bear)
        alice.activates(mindgraftManipulator, target = bear, x = 2)
        if (pending is Decision.ChooseNumber) answer(Answer.Number(2))
        resolveAll()
        expect { bear.controller shouldBe alice.id }
    }

    @Test
    fun spiralSageMakesManaPerCounter() = scenario {
        alice.battlefield(spiralSage, counters = listOf(p1p1 to 2))
        alice.activates(spiralSage)
        expect { alice.pool.size shouldBe 2 }
    }

    @Test
    fun hybridGuildmageAddsCountersAndDraws() = scenario {
        alice.battlefield(hybridGuildmage, "Forest", "Island", "Forest", "Island", "Forest", "Island")
        alice.hand(bear)
        alice.library(ogre)
        alice.activates(hybridGuildmage, ability = 0, paying = listOf("Forest", "Island"))
        resolveAll()
        alice.casts(bear, paying = listOf("Forest#1", "Island#1"))
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
        alice.activates(hybridGuildmage, ability = 1, choosing = listOf(bear))
        resolveAll()
        expect {
            alice.hand has ogre
            bear.counters(p1p1) shouldBe 0
        }
    }

    @Test
    fun adaptiveShiftMovesCounters() = scenario {
        alice.battlefield("Forest", bear)
        alice.battlefield(ogre, counters = listOf(p1p1 to 3))
        alice.hand(adaptiveShift)
        alice.casts(adaptiveShift, targets = listOf(ogre, bear))
        resolveAll()
        if (pending is Decision.ChooseNumber) answer(Answer.Number(3))
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 3 }
    }

    @Test
    fun saphirDrakeGivesCounteredCreaturesFlying() = scenario {
        alice.battlefield(saphirDrake, bear)
        alice.battlefield(ogre, counters = listOf(p1p1 to 1))
        expect {
            ogre hasKeyword Keyword.Flying
            bear lacksKeyword Keyword.Flying
        }
    }

    @Test
    fun crestedCeratokGivesCounteredCreaturesTrample() = scenario {
        alice.battlefield(crestedCeratok, bear)
        alice.battlefield(ogre, counters = listOf(p1p1 to 1))
        expect {
            ogre hasKeyword Keyword.Trample
            bear lacksKeyword Keyword.Trample
        }
    }

    @Test
    fun coercedGrowthGrowsEachUpkeep() = scenario {
        alice.battlefield("Forest", bear)
        alice.hand(coercedGrowth)
        alice.library(ogre)
        bob.library(ogre)
        alice.casts(coercedGrowth, target = bear)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun sproutingSurgeUntapsWithACounter() = scenario {
        alice.battlefield("Forest")
        alice.battlefield(bear, tapped = true)
        alice.hand(sproutingSurge)
        alice.casts(sproutingSurge, target = bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear.tapped shouldBe false
        }
    }

    // CR 615.1: prevention for creatures without counters
    @Test
    fun thornvineWardPreventsUncounteredDamage() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(thornvineWard)
        bob.battlefield(ogre)
        bob.library(bear)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(ogre)
        bob.passes()
        alice.casts(thornvineWard)
        resolveAll()
        advanceTo(TurnStep.EndOfCombat)
        expect { alice.life shouldBe 20 }
    }

    // ---- Chunk 05: cipher (the keyword itself: GtcMechanicsScenariosTest)

    /** Casts [spell] (no cipher) and declines to encode it. */
    private fun Scenario.noCipher() {
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
    }

    // CR 502.3: doesn't untap during the next untap step
    @Test
    fun shacklingCipherTapsAndHolds() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(shacklingCipher)
        bob.battlefield(ogre)
        bob.library(bear)
        alice.casts(shacklingCipher, target = ogre)
        noCipher()
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { ogre.tapped shouldBe true }
    }

    @Test
    fun finalWhisperDraws() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(finalWhisper)
        alice.library(bear)
        alice.casts(finalWhisper)
        noCipher()
        expect { alice.hand has bear }
    }

    @Test
    fun borrowedVisageCopiesACreature() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(borrowedVisage)
        bob.battlefield(giant)
        alice.casts(borrowedVisage, target = giant)
        noCipher()
        expect { alice.battlefield has giant }
    }

    @Test
    fun phaseStrideFlickersUntilTheEndStep() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(phaseStride)
        bob.battlefield(ogre)
        alice.casts(phaseStride, target = ogre)
        noCipher()
        expect { bob.exile has ogre }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { bob.battlefield has ogre }
    }

    @Test
    fun encodedTormentMakesThemDiscard() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(encodedTorment)
        bob.hand(ogre)
        alice.casts(encodedTorment, target = "bob")
        noCipher()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun midnightExhumationReturnsACreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(midnightExhumation)
        alice.graveyard(giant)
        alice.casts(midnightExhumation, target = giant)
        noCipher()
        expect { alice.hand has giant }
    }

    @Test
    fun cellarPlagueTakesLifeACardAndAPermanent() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(cellarPlague)
        bob.hand(ogre)
        bob.battlefield(idol)
        alice.casts(cellarPlague, target = "bob")
        resolveAll()
        if (pending is Decision.ChooseObjects) bob.chooses(idol)
        noCipher()
        expect {
            bob.life shouldBe 19
            bob.graveyard has ogre
            bob.graveyard has idol
        }
    }

    @Test
    fun nightwingSummonsEncodedMakesHorrorsOnDamage() = scenario {
        alice.battlefield("Island", "Island", "Swamp", "Swamp", bear)
        alice.hand(nightwingSummons)
        alice.casts(nightwingSummons)
        resolveAll()
        may(true)
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect { "Horror Token" hasKeyword Keyword.Flying }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Horror Token" } shouldBe 2 }
    }

    @Test
    fun paranoidVisionsMills() = scenario {
        alice.battlefield("Island", "Swamp")
        alice.hand(paranoidVisions)
        bob.library(bear, ogre, giant, human)
        alice.casts(paranoidVisions, target = "bob")
        noCipher()
        expect { bob.library.names shouldBe listOf(human) }
    }
}
