package arcanum.engine.sets.tmt

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.change.Changes
import arcanum.engine.change.Meaning
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.tmt.Tmt.bear
import arcanum.engine.sets.tmt.Tmt.charm
import arcanum.engine.sets.tmt.Tmt.doom
import arcanum.engine.sets.tmt.Tmt.elf
import arcanum.engine.sets.tmt.Tmt.flyer
import arcanum.engine.sets.tmt.Tmt.giant
import arcanum.engine.sets.tmt.Tmt.heal
import arcanum.engine.sets.tmt.Tmt.idol
import arcanum.engine.sets.tmt.Tmt.ogre
import arcanum.engine.sets.tmt.Tmt.p1p1
import arcanum.engine.sets.tmt.Tmt.shatter
import arcanum.engine.sets.tmt.Tmt.shock
import arcanum.engine.sets.tmt.Tmt.sword
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** TMT's Mutagen, sneak, alliance, disappear and Class cards in play (parser chunks 01–08), each card's own ability on
 * top of the mechanics [TmtMechanicsScenariosTest] covers. */
class TmtMechanicCardsScenariosTest {
    private val sludgeSurge = Cards.invent("Sludge Surge", "{1}{U}{U}", "Instant", "Counter target spell. Create a Mutagen token. (It's an artifact with \"{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.\")")
    private val glidefinReefMarauder = Cards.invent("Glidefin, Reef Marauder", "{3}{U}", "Legendary Creature — Fish Mutant", """
        Flying
        When Glidefin enters, create a Mutagen token. (It's an artifact with "{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.")
        {2}, Remove a +1/+1 counter from a creature you control: Draw a card.
    """, "3/3")
    private val downTheDrain = Cards.invent("Down the Drain", "{3}{U}", "Instant", "Target creature's owner puts it on their choice of the top or bottom of their library. You create a Mutagen token. (It's an artifact with \"{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.\")")
    private val pipShellBuddy = Cards.invent("Pip, Shell Buddy", "{2}{G}{G}", "Legendary Creature — Mutant Ninja Turtle", """
        Each creature you control with a counter on it can't be blocked by more than one creature.
        Whenever Pip enters or attacks, create a Mutagen token. (It's an artifact with "{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.")
    """, "4/4")
    private val cascadingMutation = Cards.invent("Cascading Mutation", "{2}{G}", "Sorcery", "Destroy up to one target artifact, enchantment, or creature with flying. Create a Mutagen token. (It's an artifact with \"{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.\")")
    private val menagerieRunaways = Cards.invent("Menagerie Runaways", "{1}{G}", "Creature — Boar Rhino", "When this creature leaves the battlefield, create a Mutagen token. (It's an artifact with \"{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.\")", "2/2")
    private val grandCroakerSwampKhan = Cards.invent("Grand Croaker, Swamp Khan", "{G}{U}", "Legendary Creature — Frog Mutant Rogue", """
        Trample
        Whenever Grand Croaker or another Mutant you control enters, create a Mutagen token. (It's an artifact with "{1}, {T}, Sacrifice this token: Put a +1/+1 counter on target creature. Activate only as a sorcery.")
    """, "1/3")
    private val cogwrightGearheadSavant = Cards.invent("Cogwright, Gearhead Savant", "{3}{U}", "Legendary Creature — Mutant Ninja Turtle", """
        {T}: Put three +1/+1 counters on target artifact you control. If it isn't a creature, it becomes a 0/0 Robot creature in addition to its other types. Activate only as a sorcery.
        Whenever an artifact you control is put into a graveyard from the battlefield, if it had counters on it, put those counters on up to one target artifact or creature you control.
    """, "3/5")
    private val bastionEldestBrother = Cards.invent("Bastion, Eldest Brother", "{2}{W}", "Legendary Creature — Mutant Ninja Turtle", """
        Sneak {W} (You may cast this spell for {W} if you also return an unblocked attacker you control to hand during the declare blockers step. He enters tapped and attacking.)
        Bastion gets +1/+0 for each other creature you control.
    """, "1/3")
    private val keenBladeOfTheSewers = Cards.invent("Keen, Blade of the Sewers", "{1}{W}", "Legendary Creature — Mutant Ninja Turtle", """
        Sneak {W} (You may cast this spell for {W} if you also return an unblocked attacker you control to hand during the declare blockers step. He enters tapped and attacking.)
        Lifelink
        Whenever you gain life, put a +1/+1 counter on Keen.
    """, "1/1")
    private val sprocketGizmoSavant = Cards.invent("Sprocket, Gizmo Savant", "{2}{U}", "Legendary Creature — Mutant Ninja Turtle", """
        Sneak {1}{U} (You may cast this spell for {1}{U} if you also return an unblocked attacker you control to hand during the declare blockers step. He enters tapped and attacking.)
        Whenever Sprocket deals combat damage to a player, create a token that's a copy of target artifact you control.
    """, "3/2")
    private val takedaRisingWarlord = Cards.invent("Takeda, Rising Warlord", "{2}{B}", "Legendary Creature — Human Ninja", """
        Sneak {1}{B} (You may cast this spell for {1}{B} if you also return an unblocked attacker you control to hand during the declare blockers step. He enters tapped and attacking.)
        Whenever Takeda deals combat damage to a player, you draw a card and lose 1 life.
    """, "3/1")
    private val razorfinCloneAssassin = Cards.invent("Razorfin, Clone Assassin", "{2}{B}{B}", "Legendary Creature — Shark Octopus Ninja", """
        Sneak {3}{B}{B}
        First strike
        Whenever Razorfin deals combat damage to a player, put up to one target creature card from that player's graveyard onto the battlefield under your control. It enters tapped and attacking that player.
    """, "4/4")
    private val ironclawRelentless = Cards.invent("Ironclaw, Relentless", "{4}{B}", "Legendary Creature — Human Ninja", """
        Sneak {3}{B} (You may cast this spell for {3}{B} if you also return an unblocked attacker you control to hand during the declare blockers step. He enters tapped and attacking.)
        Deathtouch
        Whenever Ironclaw enters or attacks, another target creature you control gains deathtouch until end of turn.
    """, "6/4")
    private val oldWhiskerSensei = Cards.invent("Old Whisker, Sensei", "{1}{B}", "Legendary Creature — Mutant Ninja Rat", """
        Sneak {B} (You may cast this spell for {B} if you also return an unblocked attacker you control to hand during the declare blockers step. He enters tapped and attacking.)
        Menace (This creature can't be blocked except by two or more creatures.)
        Other Ninjas you control get +1/+1.
    """, "1/3")
    private val brawlerNightProwler = Cards.invent("Brawler, Night Prowler", "{2}{R}{R}", "Legendary Creature — Mutant Ninja Turtle", """
        Sneak {1}{R}{R} (You may cast this spell for {1}{R}{R} if you also return an unblocked attacker you control to hand during the declare blockers step. He enters tapped and attacking.)
        Attacking creatures you control have double strike.
    """, "2/3")
    private val doodleImprovArtist = Cards.invent("Doodle, Improv Artist", "{3}{G}", "Legendary Creature — Mutant Ninja Turtle", """
        Sneak {2}{G}{G} (You may cast this spell for {2}{G}{G} if you also return an unblocked attacker you control to hand during the declare blockers step. He enters tapped and attacking.)
        Whenever Doodle deals combat damage to a player, you may put a creature card and/or a land card from your hand onto the battlefield.
    """, "4/4")
    private val clanFootsoldiers = Cards.invent("Clan Footsoldiers", "{4}{W/B}{W/B}", "Creature — Human Ninja", """
        Sneak {3}{W/B} (You may cast this spell for {3}{W/B} if you also return an unblocked attacker you control to hand during the declare blockers step. It enters tapped and attacking.)
        When this creature enters, you gain 3 life.
    """, "5/5")
    private val grimBladeIronFist = Cards.invent("Grim Blade & Iron Fist", "{W}{B}", "Legendary Creature — Mutant Ninja Turtle Human", """
        Sneak {W}{B}
        Attacking Ninjas you control have deathtouch.
        Whenever Grim Blade & Iron Fist deal combat damage to a player, create a 1/1 black Ninja creature token. Then if you control five or more Ninjas, that player loses half their life, rounded up.
    """, "1/3")
    private val captainsTechnique = Cards.invent("Captain's Technique", "{3}{W}", "Sorcery", """
        Sneak {1}{W} (You may cast this spell for {1}{W} if you also return an unblocked attacker you control to hand during the declare blockers step.)
        Return one or two target creature cards each with mana value 3 or less from your graveyard to the battlefield.
    """)
    private val vixensTechnique = Cards.invent("Vixen's Technique", "{4}{U}{U}", "Instant", """
        Sneak {1}{U} (You may cast this spell for {1}{U} if you also return an unblocked attacker you control to hand during the declare blockers step.)
        Target opponent mills half their library, rounded up.
    """)
    private val warlordsTechnique = Cards.invent("Warlord's Technique", "{2}{B}", "Sorcery", """
        Sneak {B} (You may cast this spell for {B} if you also return an unblocked attacker you control to hand during the declare blockers step.)
        Destroy target creature or enchantment. If an enchantment was destroyed this way, you lose 2 life.
    """)
    private val senseisTechnique = Cards.invent("Sensei's Technique", "{3}{B}", "Sorcery", """
        Sneak {1}{B} (You may cast this spell for {1}{B} if you also return an unblocked attacker you control to hand during the declare blockers step.)
        Search your library for a card, put that card into your hand, then shuffle.
    """)
    private val bigSistersTechnique = Cards.invent("Big Sister's Technique", "{2}{R}", "Instant", """
        Sneak {R} (You may cast this spell for {R} if you also return an unblocked attacker you control to hand during the declare blockers step.)
        Big Sister's Technique deals 2 damage to each creature.
    """)
    private val hotheadsTechnique = Cards.invent("Hothead's Technique", "{4}{R}{R}", "Instant", """
        Sneak {2}{R} (You may cast this spell for {2}{R} if you also return an unblocked attacker you control to hand during the declare blockers step.)
        Each player may discard their hand and draw seven cards.
    """)
    private val prankstersTechnique = Cards.invent("Prankster's Technique", "{4}{G}", "Sorcery", """
        Sneak {3}{G} (You may cast this spell for {3}{G} if you also return an unblocked attacker you control to hand during the declare blockers step.)
        Look at the top eight cards of your library. Put up to two creature cards with total mana value 6 or less from among them onto the battlefield and the rest on the bottom of your library in a random order.
    """)
    private val youngGuardsTechnique = Cards.invent("Young Guard's Technique", "{3}{G}", "Sorcery", """
        Sneak {2}{G} (You may cast this spell for {2}{G} if you also return an unblocked attacker you control to hand during the declare blockers step.)
        Search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle.
    """)
    private val heiresssTechnique = Cards.invent("Heiress's Technique", "{1}{W}{B}", "Sorcery", """
        Sneak {W}{B} (You may cast this spell for {W}{B} if you also return an unblocked attacker you control to hand during the declare blockers step.)
        Choose one or both —
        • Target creature gets +3/+3 until end of turn.
        • Target creature gets -3/-3 until end of turn.
    """)
    private val easterlyGaleAvatar = Cards.invent("Easterly Gale Avatar", "{3}{W}", "Creature — Bird Spirit Avatar", """
        Flying, vigilance
        Alliance — Whenever another creature you control enters, this creature gets +1/+0 until end of turn.
    """, "2/4")
    private val pollywogPondOrphan = Cards.invent("Pollywog, Pond Orphan", "{1}{W}", "Legendary Creature — Mutant Ninja Turtle", """
        Alliance — Whenever another creature you control enters, choose one that hasn't been chosen this turn.
        • Put a +1/+1 counter on Pollywog.
        • Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
        • Scry 1.
    """, "2/1")
    private val motleyMutantBrigade = Cards.invent("Motley Mutant Brigade", "{2}{W}{W}", "Creature — Mutant Rebel", """
        When this creature enters, create a 2/2 red Mutant creature token.
        Alliance — Whenever another creature you control enters, put a +1/+1 counter on target creature you control.
    """, "2/1")
    private val snarlBiggestAttitude = Cards.invent("Snarl, Biggest Attitude", "{3}{R}", "Legendary Creature — Mutant Ninja Turtle", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Alliance — Whenever another creature you control enters, you may exile the top card of your library.
        Whenever Snarl attacks, until end of turn, you may play a card exiled with Snarl.
    """, "4/3")
    private val gritToughShell = Cards.invent("Grit, Tough Shell", "{1}{R}", "Legendary Creature — Mutant Ninja Turtle", "Alliance — Whenever another creature you control enters, Grit deals 1 damage to target opponent.", "1/3")
    private val gnashReptileRampager = Cards.invent("Gnash, Reptile Rampager", "{3}{R}{R}", "Legendary Creature — Mutant Berserker Turtle", """
        Alliance — Whenever another creature you control enters, Gnash deals 2 damage to each opponent.
        Whenever Gnash attacks, create a 2/2 red Mutant creature token.
    """, "7/5")
    private val flittermouseBelfryBat = Cards.invent("Flittermouse, Belfry Bat", "{1}{R}", "Legendary Creature — Bat Mutant", """
        Alliance — Whenever another creature you control enters, Flittermouse gains your choice of flying, menace, or haste until end of turn.
        Whenever Flittermouse attacks, each other attacking creature gets +1/+0 until end of turn.
    """, "1/2")
    private val vanguardStrikeTeam = Cards.invent("Vanguard Strike Team", "{1}{R/W}{R/W}", "Creature — Human Soldier", "Alliance — Whenever another creature you control enters, put a +1/+1 counter on this creature.", "2/1")
    private val theHyperdriveKids = Cards.invent("The Hyperdrive Kids", "{2}{R}{W}", "Legendary Creature — Elf Rebel", """
        Flying
        Alliance — Whenever another creature you control enters, The Hyperdrive Kids get +1/+0 until end of turn.
        Whenever The Hyperdrive Kids attack, exile up to one target creature you own, then return it to the battlefield under your control tapped and attacking.
    """, "2/4")
    private val shadowClanMystic = Cards.invent("Shadow Clan Mystic", "{3}{B}", "Creature — Human Ninja Warlock", """
        Lifelink
        Disappear — When this creature enters, if a permanent left the battlefield under your control this turn, create a 1/1 black Ninja creature token.
    """, "2/4")
    private val bugbaneExterminator = Cards.invent("Bugbane Exterminator", "{2}{B}", "Creature — Insect Mutant", """
        Flying
        Disappear — At the beginning of your end step, if a permanent left the battlefield under your control this turn, scry 1. (Look at the top card of your library. You may put that card on the bottom.)
    """, "2/2")
    private val overlordChitinSwarmInvader = Cards.invent("Overlord Chitin, Swarm Invader", "{3}{B}", "Legendary Creature — Insect Warrior", """
        Flying
        Disappear — At the beginning of your end step, if a permanent left the battlefield under your control this turn, create a 1/1 black Insect Warrior creature token with flying.
        {3}{G}, Sacrifice a token: Draw a card.
    """, "3/2")
    private val squeakVerminMonarch = Cards.invent("Squeak, Vermin Monarch", "{1}{B}", "Legendary Creature — Rat Avatar", """
        Disappear — At the beginning of your end step, if a permanent left the battlefield under your control this turn, create a 1/1 black Rat creature token and put a +1/+1 counter on Squeak.
        {T}, Sacrifice three Rats: Return target creature card and all other cards with the same name as that card from your graveyard to the battlefield tapped.
    """, "1/1")
    private val westerlyGaleAvatar = Cards.invent("Westerly Gale Avatar", "{5}{G}{G}", "Creature — Cat Spirit Avatar", """
        Trample
        Whenever this creature enters or attacks, you may sacrifice a token or a land. If you do, you gain 3 life.
        Disappear — At the beginning of your end step, if a permanent left the battlefield under your control this turn, draw a card.
    """, "7/7")
    private val brainlordBladefist = Cards.invent("Brainlord & Bladefist", "{4}{U/B}{U/B}", "Legendary Creature — Utrom Human Ninja", """
        Whenever Brainlord & Bladefist enter or attack, each opponent exiles cards from the top of their library until they exile a nonland card.
        Disappear — At the beginning of your end step, if a permanent left the battlefield under your control this turn, you may cast a card exiled with Brainlord & Bladefist without paying its mana cost.
    """, "6/7")
    private val crustfaceGastromancer = Cards.invent("Crustface, Gastromancer", "{3}{B}{G}", "Legendary Artifact Creature — Food Mutant", """
        When Crustface enters, create a Food token.
        Disappear — At the beginning of your end step, if a permanent left the battlefield under your control this turn, put three +1/+1 counters on up to one other target artifact or creature. If it isn't a creature, it becomes a 0/0 Mutant creature in addition to its other types.
        {10}, {T}, Sacrifice Crustface: You gain 15 life.
    """, "2/4")
    private val festeringFriends = Cards.invent("Festering Friends", "{2}{B/G}{B/G}", "Creature — Human Ooze Mutant", """
        Deathtouch
        Disappear — This creature enters with two +1/+1 counters on it if a permanent left the battlefield under your control this turn.
    """, "3/3")
    private val commandersKnack = Cards.invent("Commander's Knack", "{1}{W}", "Enchantment — Class", """
        (Gain the next level as a sorcery to add its ability.)
        Whenever you attack, put a +1/+1 counter on target attacking creature.
        {2}{W}: Level 2
        Whenever a creature you control leaves the battlefield, if it had a counter on it, you gain 2 life.
        {3}{W}: Level 3
        Whenever you cast a spell, put a +1/+1 counter on each creature you control.
    """)
    private val gadgeteersCraft = Cards.invent("Gadgeteer's Craft", "{1}{U}", "Enchantment — Class", """
        (Gain the next level as a sorcery to add its ability.)
        When this Class enters, mill two cards, draw two cards, then discard two cards.
        {1}{U}: Level 2
        When this Class becomes level 2, return up to two target artifact cards from your graveyard to your hand.
        {4}{U}: Level 3
        At the beginning of combat on your turn, put three +1/+1 counters on target artifact you control. If it isn't a creature, it becomes a 0/0 Robot creature in addition to its other types.
    """)

    private val mutant = Cards.invent("Sewer Mutant", "{1}{G}", "Creature — Mutant", "", "2/2")
    private val ninja = Cards.invent("Sewer Ninja", "{1}{B}", "Creature — Human Ninja", "", "2/2")
    private val counterspell = Cards.invent("Sewer Refusal", "{U}{U}", "Instant", "Counter target spell.")

    // ---- Chunks 01–02: Mutagen tokens (CR 111.10v)

    @Test
    fun sludgeSurgeCountersAndMutates() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(sludgeSurge)
        bob.battlefield("Mountain")
        bob.hand(shock)
        turn(active = bob)
        bob.casts(shock, target = "alice")
        bob.passes()
        alice.casts(sludgeSurge, target = shock)
        resolveAll()
        expect {
            alice.life shouldBe 20
            alice.battlefield has "Mutagen Token"
        }
    }

    @Test
    fun glidefinMutatesAndDrawsForACounter() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.hand(glidefinReefMarauder)
        alice.library(ogre)
        alice.casts(glidefinReefMarauder)
        resolveAll()
        expect { alice.battlefield has "Mutagen Token" }
        alice.activates(glidefinReefMarauder, choosing = listOf(bear))
        resolveAll()
        expect {
            alice.hand has ogre
            bear.counters(p1p1) shouldBe 0
        }
    }

    @Test
    fun downTheDrainTucksAndMutates() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(downTheDrain)
        bob.battlefield(giant)
        alice.casts(downTheDrain, target = giant)
        resolveAll()
        if (pending is Decision.ChooseOption) option("bottom")
        resolveAll()
        expect {
            bob.library has giant
            alice.battlefield has "Mutagen Token"
        }
    }

    @Test
    fun pipShellBuddyMutatesAndLimitsBlockers() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(pipShellBuddy)
        alice.casts(pipShellBuddy)
        resolveAll()
        expect { alice.battlefield has "Mutagen Token" }
    }

    @Test
    fun pipShellBuddyCounteredCreaturesEvadeDoubleBlocks() = scenario {
        alice.battlefield(pipShellBuddy)
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        bob.battlefield(ogre, elf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to bear, elf to bear) }
    }

    @Test
    fun cascadingMutationDestroysAFlyer() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(cascadingMutation)
        bob.battlefield(flyer)
        alice.casts(cascadingMutation, target = flyer)
        resolveAll()
        expect {
            bob.graveyard has flyer
            alice.battlefield has "Mutagen Token"
        }
    }

    @Test
    fun menagerieRunawaysLeaveAMutagen() = scenario {
        alice.battlefield(menagerieRunaways, "Swamp")
        alice.hand(doom)
        alice.casts(doom, target = menagerieRunaways)
        resolveAll()
        expect { alice.battlefield has "Mutagen Token" }
    }

    @Test
    fun grandCroakerMutatesForMutants() = scenario {
        alice.battlefield(grandCroakerSwampKhan, "Forest", "Forest")
        alice.hand(mutant)
        alice.casts(mutant)
        resolveAll()
        expect { alice.battlefield has "Mutagen Token" }
    }

    @Test
    fun cogwrightAnimatesAndPassesCounters() = scenario {
        alice.battlefield(cogwrightGearheadSavant, idol, sword, "Mountain")
        alice.hand(shatter)
        alice.activates(cogwrightGearheadSavant, target = idol)
        resolveAll()
        expect {
            idol.power shouldBe 3
            idol.types.contains(CardType.Creature) shouldBe true
        }
        alice.casts(shatter, target = idol)
        resolveAll()
        targetsIfAsked(sword)
        resolveAll()
        expect { sword.counters(p1p1) shouldBe 3 }
    }

    // ---- Chunks 02–03: sneak (CR 702.190)

    @Test
    fun bastionSneaksInAndCountsCreatures() = scenario {
        alice.battlefield(bear, ogre, "Plains")
        alice.hand(bastionEldestBrother)
        unblocked(bear)
        alice.casts(bastionEldestBrother, way = sneakOf(bastionEldestBrother), choosing = listOf(bear))
        resolveAll()
        expect {
            bastionEldestBrother.tapped shouldBe true
            bastionEldestBrother.power shouldBe 2
            alice.hand has bear
        }
    }

    @Test
    fun keenGrowsOnLifeGain() = scenario {
        alice.battlefield(keenBladeOfTheSewers, "Plains")
        alice.hand(heal)
        alice.casts(heal)
        resolveAll()
        expect { keenBladeOfTheSewers.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun sprocketCopiesAnArtifactOnDamage() = scenario {
        alice.battlefield(sprocketGizmoSavant, idol)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(sprocketGizmoSavant)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        targetsIfAsked(idol)
        resolveAll()
        expect { alice.battlefield.names.count { it == idol } shouldBe 2 }
    }

    @Test
    fun takedaDrawsOnDamage() = scenario {
        alice.battlefield(takedaRisingWarlord)
        alice.library(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(takedaRisingWarlord)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            alice.hand has bear
            alice.life shouldBe 19
        }
    }

    @Test
    fun razorfinStealsFromTheirGraveyard() = scenario {
        alice.battlefield(razorfinCloneAssassin)
        bob.graveyard(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(razorfinCloneAssassin)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            ogre.controller shouldBe alice.id
            ogre.tapped shouldBe true
        }
    }

    @Test
    fun ironclawLendsDeathtouch() = scenario {
        alice.battlefield(ironclawRelentless, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ironclawRelentless)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Deathtouch }
    }

    @Test
    fun oldWhiskerLeadsNinjas() = scenario {
        alice.battlefield(oldWhiskerSensei, ninja)
        expect { ninja.power shouldBe 3 }
    }

    @Test
    fun brawlerGivesAttackersDoubleStrike() = scenario {
        alice.battlefield(brawlerNightProwler, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        expect { bear hasKeyword Keyword.DoubleStrike }
    }

    @Test
    fun doodleDropsACreatureAndALand() = scenario {
        alice.battlefield(doodleImprovArtist)
        alice.hand(giant, "Forest")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(doodleImprovArtist)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(giant)
        resolveAll()
        chooseIfOffered("Forest")
        resolveAll()
        expect {
            alice.battlefield has giant
            alice.battlefield has "Forest"
        }
    }

    @Test
    fun clanFootsoldiersGainOnEntering() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Swamp", "Swamp")
        alice.hand(clanFootsoldiers)
        alice.casts(clanFootsoldiers)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    @Test
    fun grimBladeMakesNinjasAndDeathtouch() = scenario {
        alice.battlefield(grimBladeIronFist, ninja)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(grimBladeIronFist, ninja)
        expect { ninja hasKeyword Keyword.Deathtouch }
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { alice.battlefield has "Ninja Token" }
    }

    @Test
    fun captainsTechniqueReturnsTwo() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(captainsTechnique)
        alice.graveyard(bear, ogre)
        alice.casts(captainsTechnique, targets = listOf(bear, ogre))
        resolveAll()
        expect {
            alice.battlefield has bear
            alice.battlefield has ogre
        }
    }

    @Test
    fun vixensTechniqueMillsHalf() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(vixensTechnique)
        bob.library(elf, elf, elf)
        alice.casts(vixensTechnique, target = "bob")
        resolveAll()
        expect { bob.graveyard.size shouldBe 2 }
    }

    @Test
    fun warlordsTechniqueCostsLifeForAnEnchantment() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(warlordsTechnique)
        bob.battlefield(charm)
        alice.casts(warlordsTechnique, target = charm)
        resolveAll()
        expect {
            bob.graveyard has charm
            alice.life shouldBe 18
        }
    }

    @Test
    fun senseisTechniqueTutors() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(senseisTechnique)
        alice.library(bear, giant)
        alice.casts(senseisTechnique)
        resolveAll()
        alice.chooses(giant)
        resolveAll()
        expect { alice.hand has giant }
    }

    @Test
    fun bigSistersTechniqueSweeps() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(bigSistersTechnique)
        bob.battlefield(bear, ogre)
        alice.casts(bigSistersTechnique)
        resolveAll()
        expect {
            bob.graveyard has bear
            bob.battlefield has ogre
        }
    }

    @Test
    fun hotheadsTechniqueRefillsWhoWants() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(hotheadsTechnique, ogre)
        alice.library(bear, bear, bear, bear, bear, bear, bear)
        alice.casts(hotheadsTechnique)
        repeat(4) {
            resolveAll()
            (pending as? Decision.May)?.let { answer(Answer.YesNo(it.player == alice.id)) }
        }
        expect { alice.hand.size shouldBe 7 }
    }

    @Test
    fun prankstersTechniqueFindsTwoCreatures() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(prankstersTechnique)
        alice.library(bear, ogre, shock, giant)
        alice.casts(prankstersTechnique)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear, ogre)
        resolveAll()
        expect {
            alice.battlefield has bear
            alice.battlefield has ogre
        }
    }

    @Test
    fun youngGuardsTechniqueRampsTwo() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(youngGuardsTechnique)
        alice.library("Island", bear, "Plains")
        alice.casts(youngGuardsTechnique)
        resolveAll()
        alice.chooses("Island", "Plains")
        resolveAll()
        expect {
            "Island".tapped shouldBe true
            alice.battlefield has "Plains"
        }
    }

    @Test
    fun heiresssTechniqueBothWays() = scenario {
        alice.battlefield("Plains", "Swamp", "Swamp", bear)
        alice.hand(heiresssTechnique)
        bob.battlefield(ogre)
        alice.casts(heiresssTechnique, modes = listOf(0, 1), targets = listOf(bear, ogre))
        resolveAll()
        expect {
            bear.power shouldBe 5
            bob.graveyard has ogre
        }
    }

    // ---- Chunk 04: alliance

    @Test
    fun easterlyGaleAvatarGrowsOnArrivals() = scenario {
        alice.battlefield(easterlyGaleAvatar, "Forest")
        alice.hand(elf)
        alice.casts(elf)
        resolveAll()
        expect { easterlyGaleAvatar.power shouldBe 3 }
    }

    @Test
    fun pollywogChoosesDifferentModes() = scenario {
        alice.battlefield(pollywogPondOrphan, "Forest", "Forest")
        alice.hand(elf, elf)
        alice.casts(elf)
        resolveAll()
        modes(0)
        resolveAll()
        alice.casts(elf)
        resolveAll()
        modes(1)
        resolveAll()
        expect {
            pollywogPondOrphan.counters(p1p1) shouldBe 1
            alice.battlefield has "Food Token"
        }
    }

    @Test
    fun motleyMutantBrigadeCountersOnArrivals() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(motleyMutantBrigade)
        alice.casts(motleyMutantBrigade)
        resolveAll()
        pickTargets(bear)
        resolveAll()
        expect {
            alice.battlefield has "Mutant Token"
            bear.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun snarlExilesAndPlays() = scenario {
        alice.battlefield(snarlBiggestAttitude, "Forest", "Forest", "Forest")
        alice.hand(elf)
        alice.library(bear)
        alice.casts(elf, paying = listOf("Forest"))
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.exile has bear }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(snarlBiggestAttitude)
        resolveAll()
        advanceTo(TurnStep.PostcombatMain)
        alice.casts(bear)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    @Test
    fun gritPingsOnArrivals() = scenario {
        alice.battlefield(gritToughShell, "Forest")
        alice.hand(elf)
        alice.casts(elf)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun gnashBurnsAndMakesMutants() = scenario {
        alice.battlefield(gnashReptileRampager, "Forest")
        alice.hand(elf)
        alice.casts(elf)
        resolveAll()
        expect { bob.life shouldBe 18 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(gnashReptileRampager)
        resolveAll()
        expect { alice.battlefield has "Mutant Token" }
    }

    @Test
    fun flittermouseChoosesFlying() = scenario {
        alice.battlefield(flittermouseBelfryBat, "Forest")
        alice.hand(elf)
        alice.casts(elf)
        resolveAll()
        option("flying")
        resolveAll()
        expect { flittermouseBelfryBat hasKeyword Keyword.Flying }
    }

    @Test
    fun flittermouseLeadsAttackers() = scenario {
        alice.battlefield(flittermouseBelfryBat, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(flittermouseBelfryBat, bear)
        resolveAll()
        expect { bear.power shouldBe 3 }
    }

    @Test
    fun vanguardStrikeTeamGrowsOnArrivals() = scenario {
        alice.battlefield(vanguardStrikeTeam, "Forest")
        alice.hand(elf)
        alice.casts(elf)
        resolveAll()
        expect { vanguardStrikeTeam.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun theHyperdriveKidsBlinkAnAttacker() = scenario {
        alice.battlefield(theHyperdriveKids)
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(theHyperdriveKids)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 0
            bear.tapped shouldBe true
        }
    }

    // ---- Chunk 05: disappear

    @Test
    fun shadowClanMysticMakesANinjaAfterADeparture() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(doom, shadowClanMystic)
        alice.casts(doom, target = bear)
        resolveAll()
        alice.casts(shadowClanMystic)
        resolveAll()
        expect { alice.battlefield has "Ninja Token" }
    }

    @Test
    fun bugbaneExterminatorScriesAfterADeparture() = scenario {
        alice.battlefield(bugbaneExterminator, bear, "Swamp")
        alice.hand(doom)
        alice.library(ogre)
        alice.casts(doom, target = bear)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { alice.library.names.last() shouldBe ogre }
    }

    @Test
    fun overlordChitinBreedsAndDraws() = scenario {
        alice.battlefield(overlordChitinSwarmInvader, bear, "Swamp", "Forest", "Forest", "Forest", "Forest")
        alice.hand(doom)
        alice.library(ogre)
        alice.casts(doom, target = bear)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.battlefield has "Insect Warrior Token" }
        alice.activates(overlordChitinSwarmInvader, choosing = listOf("Insect Warrior Token"))
        resolveAll()
        expect { alice.hand has ogre }
    }

    @Test
    fun squeakBreedsRats() = scenario {
        alice.battlefield(squeakVerminMonarch, bear, "Swamp")
        alice.hand(doom)
        alice.casts(doom, target = bear)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield has "Rat Token"
            squeakVerminMonarch.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun westerlyGaleAvatarTradesALandForLife() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Plains")
        alice.hand(westerlyGaleAvatar)
        alice.library(bear)
        alice.casts(westerlyGaleAvatar, paying = listOf("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest"))
        resolveAll()
        may(true)
        chooseIfOffered("Plains")
        resolveAll()
        expect { alice.life shouldBe 23 }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun brainlordBladefistExilesTheirCards() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Swamp", "Swamp")
        alice.hand(brainlordBladefist)
        bob.library("Forest", ogre)
        alice.casts(brainlordBladefist)
        resolveAll()
        expect { bob.exile has ogre }
    }

    @Test
    fun crustfaceAnimatesAFood() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Forest", "Forest", bear)
        alice.hand(crustfaceGastromancer, doom)
        alice.casts(crustfaceGastromancer)
        resolveAll()
        expect { alice.battlefield has "Food Token" }
        change(Changes.move(state, listOf(card(bear)), Zone.Graveyard, meanings = listOf(Meaning.Destroyed)))
        advanceTo(TurnStep.End)
        resolveAll()
        targetsIfAsked("Food Token")
        resolveAll()
        expect { "Food Token".power shouldBe 3 }
    }

    @Test
    fun festeringFriendsEnterBiggerAfterADeparture() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(doom, festeringFriends)
        alice.casts(doom, target = bear)
        resolveAll()
        alice.casts(festeringFriends)
        resolveAll()
        expect { festeringFriends.counters(p1p1) shouldBe 2 }
    }

    // ---- Chunks 06–07: Classes (CR 716)

    @Test
    fun commandersKnackCountersAttackers() = scenario {
        alice.battlefield(commandersKnack, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun commandersKnackLevelThreeCountersOnSpells() = scenario {
        alice.battlefield(commandersKnack, bear, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(heal)
        alice.activates(commandersKnack, ability = 0)
        resolveAll()
        alice.activates(commandersKnack, ability = 1)
        resolveAll()
        alice.casts(heal)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun gadgeteersCraftLootsAndRegrows() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(gadgeteersCraft, bear, ogre)
        alice.library(idol, sword, elf, elf)
        alice.casts(gadgeteersCraft, paying = listOf("Island", "Island"))
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear, ogre)
        resolveAll()
        expect { alice.graveyard has idol }
        alice.activates(gadgeteersCraft)
        resolveAll()
        pickTargets(idol)
        resolveAll()
        expect { alice.hand has idol }
    }
}
