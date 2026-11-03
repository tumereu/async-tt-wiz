package arcanum.engine.sets.dgm

import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.dgm.Dgm.bear
import arcanum.engine.sets.dgm.Dgm.charm
import arcanum.engine.sets.dgm.Dgm.curse
import arcanum.engine.sets.dgm.Dgm.doom
import arcanum.engine.sets.dgm.Dgm.giant
import arcanum.engine.sets.dgm.Dgm.human
import arcanum.engine.sets.dgm.Dgm.idol
import arcanum.engine.sets.dgm.Dgm.ogre
import arcanum.engine.sets.dgm.Dgm.p1p1
import arcanum.engine.sets.dgm.Dgm.ponder
import arcanum.engine.sets.dgm.Dgm.shock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Dragon's Maze's fuse cards, guild-mechanic cards, Gate cards and monocolored cards in play (parser chunks 01–04), beyond fuse played in [DgmFuseScenariosTest]. */
class DgmFuseGuildAndMonoColorCardsScenariosTest {
    private val fuse = "Fuse (You may cast one or both halves of this card from your hand.)"
    private val livingThriving = Cards.inventFaces("Split",
        face("Living", "{3}{G}", "Sorcery", "Create a 3/3 green Centaur creature token.\n$fuse", null, null, null),
        face("Thriving", "{W}", "Sorcery", "You gain 2 life for each creature you control.\n$fuse", null, null, null),
    )
    private val armoredHazardous = Cards.inventFaces("Split",
        face("Armored", "{1}{R}", "Sorcery", "Target creature gets +1/+1 and gains double strike until end of turn.\n$fuse", null, null, null),
        face("Hazardous", "{3}{G}", "Sorcery", "All creatures able to block target creature this turn do so.\n$fuse", null, null, null),
    )
    private val beckonSummon = Cards.inventFaces("Split",
        face("Beckon", "{G}{U}", "Sorcery", "Whenever a creature enters this turn, you may draw a card.\n$fuse", null, null, null),
        face("Summon", "{4}{W}{U}", "Sorcery", "Create four 1/1 white Bird creature tokens with flying.\n$fuse", null, null, null),
    )
    private val shatteringInvading = Cards.inventFaces("Split",
        face("Shattering", "{U}{B}", "Sorcery", "Target player mills eight cards.\n$fuse", null, null, null),
        face("Invading", "{4}{B}{R}", "Sorcery", "Put a creature card from a graveyard onto the battlefield under your control. It gains haste until end of turn.\n$fuse", null, null, null),
    )
    private val seizeDiscard = Cards.inventFaces("Split",
        face("Seize", "{1}{U}{R}", "Sorcery", "Gain control of target permanent until end of turn. Untap it. It gains haste until end of turn.\n$fuse", null, null, null),
        face("Discard", "{4}{R}{W}", "Sorcery", "Each player sacrifices an artifact, a creature, an enchantment, a land, and a planeswalker of their choice.\n$fuse", null, null, null),
    )
    private val lowMuddy = Cards.inventFaces("Split",
        face("Low", "{3}{B}", "Sorcery", "Target player discards two cards.\n$fuse", null, null, null),
        face("Muddy", "{2}{G}", "Sorcery", "Return target card from your graveyard to your hand.\n$fuse", null, null, null),
    )
    private val gainBane = Cards.inventFaces("Split",
        face("Gain", "{1}{W}", "Instant", "Creatures you control get +1/+1 until end of turn.\n$fuse", null, null, null),
        face("Bane", "{2}{B}", "Instant", "Creatures your opponents control get -1/-1 until end of turn.\n$fuse", null, null, null),
    )
    private val guardAssist = Cards.inventFaces("Split",
        face("Guard", "{2}{W}", "Instant", "Target creature gets +2/+4 until end of turn.\n$fuse", null, null, null),
        face("Assist", "{1}{U}", "Instant", "Target creature gets -6/-0 until end of turn.\n$fuse", null, null, null),
    )
    private val steadyEager = Cards.inventFaces("Split",
        face("Steady", "{1}{G}{W}", "Instant", "Creatures you control gain indestructible until end of turn. Untap each creature you control.\n$fuse", null, null, null),
        face("Eager", "{1}{W}{B}", "Instant", "Creatures you control gain deathtouch and lifelink until end of turn.\n$fuse", null, null, null),
    )
    private val twistScorch = Cards.inventFaces("Split",
        face("Twist", "{2}{U}", "Instant", "Until end of turn, target creature loses all abilities and becomes a red Weird with base power and toughness 0/1.\n$fuse", null, null, null),
        face("Scorch", "{1}{R}", "Instant", "Scorch deals 2 damage to any target.\n$fuse", null, null, null),
    )
    private val rendRip = Cards.inventFaces("Split",
        face("Rend", "{1}{R}", "Instant", "Destroy target artifact.\n$fuse", null, null, null),
        face("Rip", "{W}", "Instant", "Destroy target enchantment.\n$fuse", null, null, null),
    )
    private val legionMastiff = Cards.invent("Legion Mastiff", "{1}{W}", "Creature — Dog", "Battalion — Whenever this creature and at least two other creatures attack, this creature gains lifelink until end of turn. (Damage dealt by a creature with lifelink also causes its controller to gain that much life.)", "2/2")
    private val tavicEdgeOfTheLegion = Cards.invent("Tavic, Edge of the Legion", "{2}{R}{W}", "Legendary Creature — Human Soldier", """
        Indestructible
        Battalion — Whenever Tavic and at least two other creatures attack, Tavic gets +5/+5 until end of turn.
    """, "2/2")
    private val pontiffOfTithes = Cards.invent("Pontiff of Tithes", "{4}{B}{B}", "Creature — Zombie Cleric", """
        Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)
        Other creatures you control have extort. (If a creature has multiple instances of extort, each triggers separately.)
    """, "2/7")
    private val pyrewildFirecaller = Cards.invent("Pyrewild Firecaller", "{2}{R}", "Creature — Goblin Shaman", """
        Bloodrush — {1}{R}, Discard this card: Target attacking creature gets +3/+1 until end of turn.
        Whenever one or more creatures you control deal combat damage to a player, if this card is in your graveyard, you may pay {3}. If you do, return this card to your hand.
    """, "3/1")
    private val rubblebeltMauler = Cards.invent("Rubblebelt Mauler", "{3}{R}", "Creature — Cat", "Bloodrush — {R}, Discard this card: Target attacking creature gets +3/+3 until end of turn.", "3/3")
    private val batteringShark = Cards.invent("Battering Shark", "{2}{G}", "Creature — Shark Beast", """
        Trample
        Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
    """, "2/1")
    private val rakdosWyrm = Cards.invent("Rakdos Wyrm", "{2}{B}", "Creature — Drake", """
        Flying
        Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)
    """, "1/2")
    private val ezavaBloodWitchOfRakdos = Cards.invent("Ezava, Blood Witch of Rakdos", "{2}{B}{R}", "Legendary Creature — Human Cleric", """
        First strike, haste
        Unleash (You may have this creature enter with a +1/+1 counter on it. It can't block as long as it has a +1/+1 counter on it.)
        Each other creature you control with a +1/+1 counter on it has haste.
    """, "3/3")
    private val thrashingVinedog = Cards.invent("Thrashing Vinedog", "{3}{G}", "Creature — Plant Dog", """
        Reach (This creature can block creatures with flying.)
        Scavenge {4}{G}{G} ({4}{G}{G}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)
    """, "3/3")
    private val varrokTheScarMarked = Cards.invent("Varrok, the Scar-Marked", "{1}{B}{G}", "Legendary Creature — Troll Warrior", """
        Each creature card in your graveyard has scavenge. The scavenge cost is equal to its mana cost. (Exile a creature card from your graveyard and pay its mana cost: Put a number of +1/+1 counters equal to that card's power on target creature. Scavenge only as a sorcery.)
        Sacrifice another creature: Regenerate Varrok.
    """, "2/2")
    private val lyevEdict = Cards.invent("Lyev Edict", "{1}{W}", "Sorcery", "Detain up to two target creatures your opponents control. (Until your next turn, those creatures can't attack or block and their activated abilities can't be activated.)")
    private val lavinnaOfTheTenth = Cards.invent("Lavinna of the Tenth", "{3}{W}{U}", "Legendary Creature — Human Soldier", """
        Protection from red
        When Lavinna enters, detain each nonland permanent your opponents control with mana value 4 or less. (Until your next turn, those permanents can't attack or block and their activated abilities can't be activated.)
    """, "4/4")
    private val heirOfVituGhazi = Cards.invent("Heir of Vitu-Ghazi", "{3}{W}{W}", "Creature — Elemental", "When this creature enters, if you cast it from your hand, create a 1/1 white Bird creature token with flying, then populate. (Create a token that's a copy of a creature token you control.)", "4/4")
    private val wakenTheReflections = Cards.invent("Waken the Reflections", "{W}", "Sorcery", "Populate. (Create a token that's a copy of a creature token you control.)")
    private val haazdaSnarePatrol = Cards.invent("Haazda Snare Patrol", "{2}{W}", "Creature — Human Soldier", "Whenever this creature attacks, you may pay {W}. If you do, tap target creature an opponent controls.", "1/4")
    private val sunspireWardens = Cards.invent("Sunspire Wardens", "{3}{W}", "Creature — Human Soldier", "When this creature enters, if you control two or more Gates, create a 2/2 white Knight creature token with vigilance.", "2/4")
    private val opalLakeWardens = Cards.invent("Opal Lake Wardens", "{3}{U}", "Creature — Vedalken Soldier", "When this creature enters, if you control two or more Gates, you may draw a card.", "2/4")
    private val ubulSarWardens = Cards.invent("Ubul Sar Wardens", "{3}{B}", "Creature — Zombie Soldier", "When this creature enters, if you control two or more Gates, target creature an opponent controls gets -2/-2 until end of turn.", "2/4")
    private val smeltWardWardens = Cards.invent("Smelt-Ward Wardens", "{3}{R}", "Creature — Human Warrior", "When this creature enters, if you control two or more Gates, gain control of target creature an opponent controls until end of turn. Untap that creature. It gains haste until end of turn.", "2/4")
    private val saruliWardens = Cards.invent("Saruli Wardens", "{3}{G}", "Creature — Elf Warrior", "When this creature enters, if you control two or more Gates, you gain 7 life.", "2/4")
    private val mazeWatcher = Cards.invent("Maze Watcher", "{5}{W}", "Creature — Elemental", """
        Vigilance
        Multicolored creatures you control have vigilance.
    """, "3/6")
    private val mazeSkimmer = Cards.invent("Maze Skimmer", "{5}{U}", "Creature — Elemental", """
        Flying
        Multicolored creatures you control have flying.
    """, "3/5")
    private val mazeHorror = Cards.invent("Maze Horror", "{5}{B}", "Creature — Elemental", """
        Deathtouch (Any amount of damage this deals to a creature is enough to destroy it.)
        Multicolored creatures you control have deathtouch.
    """, "4/5")
    private val mazeCharger = Cards.invent("Maze Charger", "{5}{R}", "Creature — Elemental", """
        Haste
        Multicolored creatures you control have haste.
    """, "6/3")
    private val mazeColossus = Cards.invent("Maze Colossus", "{5}{G}", "Creature — Elemental", """
        Trample
        Multicolored creatures you control have trample.
    """, "5/4")
    private val azoriusCluegem = Cards.invent("Azorius Cluegem", "{3}", "Artifact", """
        {T}: Add {W} or {U}.
        {W}{U}, {T}, Sacrifice this artifact: Draw a card.
    """)
    private val azoriusGatehouse = Cards.invent("Azorius Gatehouse", null, "Land — Gate", """
        This land enters tapped.
        {T}: Add {W} or {U}.
    """)
    private val forsakeTheGuilds = Cards.invent("Forsake the Guilds", "{1}{W}", "Instant", "Each player sacrifices a multicolored permanent of their choice.")
    private val riotQuelling = Cards.invent("Riot Quelling", "{2}{W}", "Instant", "You gain 1 life for each creature your opponents control. Prevent all damage that would be dealt to you this turn.")
    private val aethershifter = Cards.invent("Aethershifter", "{4}{U}{U}", "Creature — Shapeshifter", """
        {U}: Exile this creature. Return it to the battlefield under its owner's control at the beginning of the next end step.
        {U}: This creature can't be blocked this turn.
        {1}: This creature gets +1/-1 until end of turn.
        {1}: This creature gets -1/+1 until end of turn.
    """, "4/5")
    private val unseenStrings = Cards.invent("Unseen Strings", "{1}{U}", "Sorcery", """
        You may tap or untap target permanent, then you may tap or untap another target permanent.
        Cipher (Then you may exile this spell card encoded on a creature you control. Whenever that creature deals combat damage to a player, its controller may cast a copy of the encoded card without paying its mana cost.)
    """)
    private val staticMind = Cards.invent("Static Mind", "{3}{U}", "Instant", "Counter target spell unless its controller pays {6}.")
    private val sprintersBane = Cards.invent("Sprinter's Bane", "{1}{U}", "Enchantment — Aura", """
        Enchant creature with power 3 or less
        When this Aura enters, tap enchanted creature.
        Enchanted creature doesn't untap during its controller's untap step.
    """)
    private val uncoveredSecrets = Cards.invent("Uncovered Secrets", "{2}{U}", "Sorcery", "Look at the top four cards of your library. You may reveal up to two instant and/or sorcery cards from among them and put the revealed cards into your hand. Put the rest on the bottom of your library in any order.")
    private val bloodScribe = Cards.invent("Blood Scribe", "{1}{B}", "Creature — Zombie Wizard", "If you would draw a card while you have no cards in hand, instead you draw two cards and you lose 1 life.", "2/1")
    private val cryptRaid = Cards.invent("Crypt Raid", "{2}{B}", "Instant", "Exile all creature cards from target player's graveyard. You gain 3 life for each card exiled this way.")
    private val fatalVapours = Cards.invent("Fatal Vapours", "{3}{B}", "Instant", "Target creature gets -4/-2 until end of turn.")
    private val hiredTormentor = Cards.invent("Hired Tormentor", "{2}{B}", "Creature — Human Rogue", """
        Defender
        {3}{B}, {T}: Target opponent loses 2 life, then reveals a card at random from their hand.
    """, "2/3")
    private val sinisterHaunting = Cards.invent("Sinister Haunting", "{B}", "Enchantment — Aura", """
        Enchant creature
        Whenever enchanted creature attacks or blocks, its controller loses 2 life.
    """)
    private val aweOfTheGuilds = Cards.invent("Awe of the Guilds", "{2}{R}", "Sorcery", "Monocolored creatures can't block this turn.")
    private val clearTheWay = Cards.invent("Clear the Way", "{R}", "Sorcery", "Destroy target creature with defender.")
    private val punishTheFoe = Cards.invent("Punish the Foe", "{4}{R}", "Instant", "Punish the Foe deals 3 damage to target player or planeswalker and 3 damage to target creature.")
    private val riotSkirmisher = Cards.invent("Riot Skirmisher", "{1}{R}", "Creature — Goblin Berserker", """
        First strike
        This creature attacks each combat if able.
    """, "2/1")
    private val weaponSwell = Cards.invent("Weapon Swell", "{R}", "Instant", """
        Target creature you control gets +1/+0 and gains first strike until end of turn.
        Overload {1}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
    """)
    private val kraulBrawler = Cards.invent("Kraul Brawler", "{1}{G}", "Creature — Insect Warrior", "{5}{G}: This creature gets +3/+3 until end of turn.", "2/2")
    private val mendingGraft = Cards.invent("Mending Graft", "{G}", "Instant", "Regenerate target creature.")
    private val mutantsQuarry = Cards.invent("Mutant's Quarry", "{G}", "Instant", "Target creature you control with a +1/+1 counter on it fights target creature an opponent controls. (Each deals damage equal to its power to the other.)")
    private val phytosurge = Cards.invent("Phytosurge", "{1}{G}", "Sorcery", "Target creature gets +5/+5 until end of turn.")
    private val skyswatter = Cards.invent("Skyswatter", "{1}{G}", "Creature — Insect", """
        Flash
        This spell can't be countered.
        Reach, protection from blue
    """, "2/2")
    private val gate = Cards.invent("Maze Runner Gate", null, "Land — Gate", "{T}: Add {W}.")
    private val multicolored = Cards.invent("Maze Runner Envoy", "{W}{U}", "Creature — Human Advisor", "", "2/2")
    private val wall = Cards.invent("Maze Runner Wall", "{1}{W}", "Creature — Wall", "Defender", "0/4")
    private val tokenCall = Cards.invent("Maze Runner Muster", "{W}", "Sorcery", "Create two 1/1 white Soldier creature tokens.")

    // ---- Chunk 01: fuse (casting fused: DgmFuseScenariosTest)

    // CR 702.102: both halves cast as one fused spell, left half first
    @Test
    fun livingThrivingFused() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Plains", bear)
        alice.hand(livingThriving)
        alice.casts(livingThriving, way = CastWay(fused = true))
        resolveAll()
        expect {
            alice.battlefield has "Centaur Token"
            alice.life shouldBe 24
        }
    }

    // CR 509.1c: every creature able to block it does so
    @Test
    fun armoredHazardousFused() = scenario {
        alice.battlefield("Mountain", "Mountain", "Forest", "Forest", "Forest", "Forest", bear)
        alice.hand(armoredHazardous)
        bob.battlefield(ogre, human)
        alice.casts(armoredHazardous, targets = listOf(bear, bear), way = CastWay(fused = true))
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.DoubleStrike
        }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to bear) }
    }

    @Test
    fun beckonSummonDrawsForArrivals() = scenario {
        alice.battlefield("Forest", "Island", "Forest", "Island")
        alice.hand(beckonSummon, bear)
        alice.library(ogre)
        alice.casts(beckonSummon, way = CastWay(face = 0), paying = listOf("Forest", "Island"))
        resolveAll()
        alice.casts(bear)
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.hand has ogre }
    }

    @Test
    fun summonMakesFourBirds() = scenario {
        alice.battlefield("Plains", "Island", "Plains", "Island", "Plains", "Island")
        alice.hand(beckonSummon)
        alice.casts(beckonSummon, way = CastWay(face = 1))
        resolveAll()
        expect { alice.battlefield.names.count { it == "Bird Token" } shouldBe 4 }
    }

    @Test
    fun shatteringInvadingMillsThenReanimates() = scenario {
        alice.battlefield("Island", "Swamp", "Swamp", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(shatteringInvading)
        bob.library(giant, ogre, human, bear, idol, charm, curse, ponder)
        alice.casts(shatteringInvading, target = "bob", way = CastWay(fused = true))
        resolveAll()
        alice.chooses(giant)
        resolveAll()
        expect {
            giant.controller shouldBe alice.id
            giant hasKeyword Keyword.Haste
        }
    }

    @Test
    fun seizeStealsForATurn() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain")
        alice.hand(seizeDiscard)
        bob.battlefield(giant, tapped = true)
        alice.casts(seizeDiscard, target = giant, way = CastWay(face = 0))
        resolveAll()
        expect {
            giant.controller shouldBe alice.id
            giant.tapped shouldBe false
        }
    }

    // CR 101.4: each player sacrifices one permanent of each type
    @Test
    fun discardMakesEachPlayerSacrificeOfEachType() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Plains", "Plains")
        alice.hand(seizeDiscard)
        bob.battlefield(idol, ogre, charm, "Forest")
        alice.casts(seizeDiscard, way = CastWay(face = 1))
        resolveAll()
        repeat(8) { if (pending is Decision.ChooseObjects) answer(Answer.Objects(listOf(decision<Decision.ChooseObjects>().options.first()))) }
        resolveAll()
        expect {
            bob.graveyard has idol
            bob.graveyard has ogre
            bob.graveyard has charm
            bob.graveyard has "Forest"
        }
    }

    @Test
    fun lowMuddyFused() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Forest", "Forest", "Forest")
        alice.hand(lowMuddy)
        alice.graveyard(giant)
        bob.hand(ogre, human)
        alice.casts(lowMuddy, targets = listOf("bob", giant), way = CastWay(fused = true))
        resolveAll()
        expect {
            bob.hand.isEmpty()
            alice.hand has giant
        }
    }

    @Test
    fun gainBaneFused() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(gainBane)
        bob.battlefield(human)
        alice.casts(gainBane, way = CastWay(fused = true))
        resolveAll()
        expect {
            bear.power shouldBe 3
            bob.graveyard has human
        }
    }

    @Test
    fun guardAssistFused() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Island", "Island", bear)
        alice.hand(guardAssist)
        bob.battlefield(giant)
        alice.casts(guardAssist, targets = listOf(bear, giant), way = CastWay(fused = true))
        resolveAll()
        expect {
            bear.toughness shouldBe 6
            giant.power shouldBe -1
        }
    }

    @Test
    fun steadyEagerFused() = scenario {
        alice.battlefield("Forest", "Plains", "Plains", "Plains", "Swamp", "Swamp")
        alice.battlefield(bear, tapped = true)
        alice.hand(steadyEager)
        alice.casts(steadyEager, way = CastWay(fused = true))
        resolveAll()
        expect {
            bear.tapped shouldBe false
            bear hasKeyword Keyword.Indestructible
            bear hasKeyword Keyword.Lifelink
        }
    }

    @Test
    fun twistScorchFused() = scenario {
        alice.battlefield("Island", "Island", "Island", "Mountain", "Mountain")
        alice.hand(twistScorch)
        bob.battlefield(giant)
        alice.casts(twistScorch, targets = listOf(giant, giant), way = CastWay(fused = true))
        resolveAll()
        expect { bob.graveyard has giant }
    }

    @Test
    fun rendRipFused() = scenario {
        alice.battlefield("Mountain", "Mountain", "Plains")
        alice.hand(rendRip)
        bob.battlefield(idol, charm)
        alice.casts(rendRip, targets = listOf(idol, charm), way = CastWay(fused = true))
        resolveAll()
        expect {
            bob.graveyard has idol
            bob.graveyard has charm
        }
    }

    // ---- Chunk 02: guild mechanics again

    @Test
    fun legionMastiffGainsLifelinkInBattalion() = scenario {
        alice.battlefield(legionMastiff, bear, human)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(legionMastiff, bear, human)
        resolveAll()
        expect { legionMastiff hasKeyword Keyword.Lifelink }
    }

    @Test
    fun tavicGrowsInBattalion() = scenario {
        alice.battlefield(tavicEdgeOfTheLegion, bear, human)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(tavicEdgeOfTheLegion, bear, human)
        resolveAll()
        expect { tavicEdgeOfTheLegion.power shouldBe 7 }
    }

    // CR 702.101b: each instance of extort triggers
    @Test
    fun pontiffOfTithesGivesExtort() = scenario {
        alice.battlefield(pontiffOfTithes, bear)
        expect { bear hasKeyword Keyword.Extort }
    }

    @Test
    fun pyrewildFirecallerReturnsFromTheGraveyard() = scenario {
        alice.battlefield(bear, "Mountain", "Mountain", "Mountain")
        alice.graveyard(pyrewildFirecaller)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        may(true)
        payIfAsked()
        resolveAll()
        expect { alice.hand has pyrewildFirecaller }
    }

    @Test
    fun rubblebeltMaulerBloodrushes() = scenario {
        alice.battlefield(bear, "Mountain")
        alice.hand(rubblebeltMauler)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        alice.activates(rubblebeltMauler, target = bear)
        resolveAll()
        expect { bear.power shouldBe 5 }
    }

    @Test
    fun batteringSharkEvolves() = scenario {
        alice.battlefield(batteringShark, "Forest", "Forest")
        alice.hand(bear)
        alice.casts(bear)
        resolveAll()
        expect { batteringShark.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun rakdosWyrmUnleashed() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(rakdosWyrm)
        alice.casts(rakdosWyrm)
        resolveAll()
        may(true)
        resolveAll()
        expect { rakdosWyrm.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun ezavaHastesCounteredCreatures() = scenario {
        alice.battlefield(ezavaBloodWitchOfRakdos)
        alice.battlefield(bear, sick = true, counters = listOf(p1p1 to 1))
        expect { bear hasKeyword Keyword.Haste }
    }

    @Test
    fun thrashingVinedogScavenges() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", bear)
        alice.graveyard(thrashingVinedog)
        alice.activates(thrashingVinedog, target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 3 }
    }

    // CR 702.97a: scavenge granted, for the card's mana cost
    @Test
    fun varrokGivesScavenge() = scenario {
        alice.battlefield(varrokTheScarMarked, bear, "Mountain", "Mountain", "Mountain")
        alice.graveyard(ogre)
        alice.activates(ogre, target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 3 }
    }

    @Test
    fun varrokRegeneratesBySacrifice() = scenario {
        alice.battlefield(varrokTheScarMarked, bear, "Swamp")
        alice.hand(doom)
        alice.activates(varrokTheScarMarked, choosing = listOf(bear))
        resolveAll()
        alice.casts(doom, target = varrokTheScarMarked)
        resolveAll()
        expect { alice.battlefield has varrokTheScarMarked }
    }

    @Test
    fun lyevEdictDetainsTwo() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(lyevEdict)
        bob.battlefield(ogre, giant)
        alice.casts(lyevEdict, targets = listOf(ogre, giant))
        resolveAll()
        expect { giant hasMechanic { it is Mechanic.CantAttack } }
    }

    @Test
    fun lavinnaDetainsCheapPermanents() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Island", "Island")
        alice.hand(lavinnaOfTheTenth)
        bob.battlefield(ogre, giant)
        alice.casts(lavinnaOfTheTenth)
        resolveAll()
        expect {
            ogre hasMechanic { it is Mechanic.CantAttack }
            giant lacksMechanic { it is Mechanic.CantAttack }
        }
    }

    @Test
    fun heirOfVituGhaziMakesTwoBirds() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(heirOfVituGhazi)
        alice.casts(heirOfVituGhazi)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Bird Token" } shouldBe 2 }
    }

    @Test
    fun wakenTheReflectionsPopulates() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(tokenCall, wakenTheReflections)
        alice.casts(tokenCall)
        resolveAll()
        alice.casts(wakenTheReflections)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Soldier Token")
        resolveAll()
        expect { alice.battlefield.names.count { it == "Soldier Token" } shouldBe 3 }
    }

    // ---- Chunk 03: Gates and the Maze

    @Test
    fun haazdaSnarePatrolPaysToTap() = scenario {
        alice.battlefield(haazdaSnarePatrol, "Plains")
        bob.battlefield(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(haazdaSnarePatrol)
        targetsIfAsked(ogre)
        resolveAll()
        may(true)
        payIfAsked()
        resolveAll()
        expect { ogre.tapped shouldBe true }
    }

    /** Each Warden checks for two Gates as it enters (CR 603.4). */
    @Test
    fun sunspireWardensWithTwoGates() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", gate, gate)
        alice.hand(sunspireWardens)
        alice.casts(sunspireWardens, paying = listOf("Plains", "Plains#1", "Plains#2", "Plains#3"))
        resolveAll()
        expect { alice.battlefield has "Knight Token" }
    }

    @Test
    fun sunspireWardensWithoutGates() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(sunspireWardens)
        alice.casts(sunspireWardens)
        resolveAll()
        expect { alice.battlefield lacks "Knight Token" }
    }

    @Test
    fun opalLakeWardensDraws() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", gate, gate)
        alice.hand(opalLakeWardens)
        alice.library(bear)
        alice.casts(opalLakeWardens, paying = listOf("Island", "Island#1", "Island#2", "Island#3"))
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun ubulSarWardensShrinks() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", gate, gate)
        alice.hand(ubulSarWardens)
        bob.battlefield(bear)
        alice.casts(ubulSarWardens, paying = listOf("Swamp", "Swamp#1", "Swamp#2", "Swamp#3"))
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bob.graveyard has bear }
    }

    @Test
    fun smeltWardWardensSteals() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", gate, gate)
        alice.hand(smeltWardWardens)
        bob.battlefield(ogre, tapped = true)
        alice.casts(smeltWardWardens, paying = listOf("Mountain", "Mountain#1", "Mountain#2", "Mountain#3"))
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            ogre.controller shouldBe alice.id
            ogre hasKeyword Keyword.Haste
        }
    }

    @Test
    fun saruliWardensGainsSeven() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", gate, gate)
        alice.hand(saruliWardens)
        alice.casts(saruliWardens, paying = listOf("Forest", "Forest#1", "Forest#2", "Forest#3"))
        resolveAll()
        expect { alice.life shouldBe 27 }
    }

    /** Each Maze Elemental gives its keyword to multicolored creatures you control. */
    @Test
    fun eachMazeElementalLendsItsKeyword() {
        listOf(mazeWatcher to Keyword.Vigilance, mazeSkimmer to Keyword.Flying, mazeHorror to Keyword.Deathtouch,
            mazeCharger to Keyword.Haste, mazeColossus to Keyword.Trample).forEach { (elemental, keyword) ->
            scenario {
                alice.battlefield(elemental, multicolored, bear)
                expect {
                    multicolored hasKeyword keyword
                    bear lacksKeyword keyword
                }
            }
        }
    }

    @Test
    fun azoriusCluegemCashesInForACard() = scenario {
        alice.battlefield(azoriusCluegem, "Plains", "Island")
        alice.library(bear)
        alice.activates(azoriusCluegem, ability = 1)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun azoriusGatehouseEntersTapped() = scenario {
        alice.hand(azoriusGatehouse)
        alice.plays(azoriusGatehouse)
        expect { azoriusGatehouse.tapped shouldBe true }
    }

    // ---- Chunk 04: monocolored

    @Test
    fun forsakeTheGuildsTakesMulticoloredPermanents() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(forsakeTheGuilds)
        bob.battlefield(multicolored, ogre)
        alice.casts(forsakeTheGuilds)
        resolveAll()
        expect {
            bob.graveyard has multicolored
            alice.battlefield has bear
        }
    }

    @Test
    fun riotQuellingGainsAndPrevents() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(riotQuelling)
        bob.battlefield(ogre, bear)
        bob.library(human)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(ogre)
        bob.passes()
        alice.casts(riotQuelling)
        resolveAll()
        advanceTo(TurnStep.EndOfCombat)
        expect { alice.life shouldBe 22 }
    }

    @Test
    fun aethershifterSlipsAndShifts() = scenario {
        alice.battlefield(aethershifter, "Island", "Island")
        bob.battlefield(giant)
        alice.activates(aethershifter, ability = 2)
        resolveAll()
        expect { aethershifter.power shouldBe 5 }
        alice.activates(aethershifter, ability = 1)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(aethershifter)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(giant to aethershifter) }
    }

    @Test
    fun aethershifterBlinksUntilTheEndStep() = scenario {
        alice.battlefield(aethershifter, "Island")
        alice.activates(aethershifter, ability = 0)
        resolveAll()
        expect { alice.exile has aethershifter }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield has aethershifter }
    }

    @Test
    fun unseenStringsTapsAndUntaps() = scenario {
        alice.battlefield("Island", "Island")
        alice.battlefield(bear, tapped = true)
        alice.hand(unseenStrings)
        bob.battlefield(ogre)
        alice.casts(unseenStrings, targets = listOf(ogre, bear))
        resolveAll()
        if (pending is Decision.May) may(true)
        option("tap")
        resolveAll()
        if (pending is Decision.May) may(true)
        option("untap")
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            bear.tapped shouldBe false
        }
    }

    @Test
    fun staticMindAsksForSix() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        bob.hand(ogre)
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(staticMind)
        turn(active = bob)
        bob.casts(ogre)
        bob.passes()
        alice.casts(staticMind, target = ogre)
        resolveAll()
        if (pending is Decision.May) bob.answers(Answer.YesNo(false))
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun sprintersBaneLocksASmallCreature() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(sprintersBane)
        bob.battlefield(ogre, giant)
        bob.library(bear)
        alice.casts(sprintersBane, target = ogre)
        resolveAll()
        advanceTo(TurnStep.Upkeep, turn = 2)
        expect { ogre.tapped shouldBe true }
    }

    @Test
    fun uncoveredSecretsFindsSpells() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(uncoveredSecrets)
        alice.library(ponder, bear, shock, ogre, giant)
        alice.casts(uncoveredSecrets)
        resolveAll()
        alice.chooses(ponder, shock)
        // the rest go to the bottom in an order of the player's choice
        if (pending is Decision.ChooseObjects) answer(Answer.Objects(decision<Decision.ChooseObjects>().options))
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(ponder, shock)
            alice.library.names.first() shouldBe giant
        }
    }

    // CR 614.11: a replacement of a draw
    @Test
    fun bloodScribeDrawsTwoFromAnEmptyHand() = scenario {
        alice.battlefield("Island", bloodScribe)
        alice.hand(ponder)
        alice.library(bear, ogre, giant)
        alice.casts(ponder)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.life shouldBe 19
        }
    }

    @Test
    fun cryptRaidExilesCreaturesForLife() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(cryptRaid)
        bob.graveyard(bear, ogre, idol)
        alice.casts(cryptRaid, target = "bob")
        resolveAll()
        expect {
            alice.life shouldBe 26
            bob.graveyard has idol
        }
    }

    @Test
    fun fatalVapoursKills() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(fatalVapours)
        bob.battlefield(bear)
        alice.casts(fatalVapours, target = bear)
        resolveAll()
        expect { bob.graveyard has bear }
    }

    @Test
    fun hiredTormentorDrainsAndReveals() = scenario {
        alice.battlefield(hiredTormentor, "Swamp", "Swamp", "Swamp", "Swamp")
        bob.hand(ogre)
        alice.activates(hiredTormentor, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun sinisterHauntingHurtsTheAttacker() = scenario {
        alice.battlefield("Swamp")
        alice.hand(sinisterHaunting)
        bob.battlefield(ogre)
        bob.library(bear)
        alice.casts(sinisterHaunting, target = ogre)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(ogre)
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun aweOfTheGuildsStopsMonocoloredBlockers() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", bear)
        alice.hand(aweOfTheGuilds)
        bob.battlefield(giant)
        alice.casts(aweOfTheGuilds)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(giant to bear) }
    }

    @Test
    fun clearTheWayDestroysADefender() = scenario {
        alice.battlefield("Mountain")
        alice.hand(clearTheWay)
        bob.battlefield(wall, ogre)
        alice.casts(clearTheWay)
        assertEquals(listOf(target(wall)), decision<Decision.Targets>().options)
        targets(wall)
        payIfAsked()
        resolveAll()
        expect { bob.graveyard has wall }
    }

    @Test
    fun punishTheFoeBurnsBoth() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(punishTheFoe)
        bob.battlefield(ogre)
        alice.casts(punishTheFoe, targets = listOf("bob", ogre))
        resolveAll()
        expect {
            bob.life shouldBe 17
            bob.graveyard has ogre
        }
    }

    @Test
    fun riotSkirmisherMustAttack() = scenario {
        alice.battlefield(riotSkirmisher, bear)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(bear) }
        alice.attacks(riotSkirmisher)
    }

    @Test
    fun weaponSwellOverloaded() = scenario {
        alice.battlefield("Mountain", "Mountain", bear, human)
        alice.hand(weaponSwell)
        alice.casts(weaponSwell, way = ownAlternative(weaponSwell))
        resolveAll()
        expect {
            bear hasKeyword Keyword.FirstStrike
            human.power shouldBe 2
        }
    }

    @Test
    fun kraulBrawlerPumps() = scenario {
        alice.battlefield(kraulBrawler, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.activates(kraulBrawler)
        resolveAll()
        expect { kraulBrawler.power shouldBe 5 }
    }

    @Test
    fun mendingGraftRegenerates() = scenario {
        alice.battlefield("Forest", "Swamp", bear)
        alice.hand(mendingGraft, doom)
        alice.casts(mendingGraft, target = bear)
        resolveAll()
        alice.casts(doom, target = bear)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    @Test
    fun mutantsQuarryFightsWithACounteredCreature() = scenario {
        alice.battlefield("Forest")
        alice.battlefield(ogre, counters = listOf(p1p1 to 1))
        alice.hand(mutantsQuarry)
        bob.battlefield(bear)
        alice.casts(mutantsQuarry, targets = listOf(ogre, bear))
        resolveAll()
        expect { bob.graveyard has bear }
    }

    @Test
    fun phytosurgePumps() = scenario {
        alice.battlefield("Forest", "Forest", bear)
        alice.hand(phytosurge)
        alice.casts(phytosurge, target = bear)
        resolveAll()
        expect { bear.power shouldBe 7 }
    }

    // CR 702.16: protection from blue
    @Test
    fun skyswatterHasProtectionFromBlue() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(skyswatter)
        alice.casts(skyswatter)
        resolveAll()
        expect {
            skyswatter hasKeyword Keyword.Reach
            skyswatter hasKeyword Keyword.Protection
        }
    }
}
