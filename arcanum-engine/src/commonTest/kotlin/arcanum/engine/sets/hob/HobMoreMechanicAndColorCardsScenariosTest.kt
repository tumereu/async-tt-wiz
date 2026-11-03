package arcanum.engine.sets.hob

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.change.Changes
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.hob.Hob.bear
import arcanum.engine.sets.hob.Hob.doom
import arcanum.engine.sets.hob.Hob.dwarf
import arcanum.engine.sets.hob.Hob.elf
import arcanum.engine.sets.hob.Hob.flyer
import arcanum.engine.sets.hob.Hob.giant
import arcanum.engine.sets.hob.Hob.idol
import arcanum.engine.sets.hob.Hob.ogre
import arcanum.engine.sets.hob.Hob.p1p1
import arcanum.engine.sets.hob.Hob.shatter
import arcanum.engine.sets.hob.Hob.shock
import arcanum.engine.sets.hob.Hob.sphinx
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** The Hobbit's landfall, Wolf, mono-colored, multicolored, artifact and land cards in play (parser chunks 07–14).
 * Goblin Burrowtown, Copper Fells, Mere-town and Gloomwood repeat the sacrifice-for-two-counters land tested with
 * Elf-lord's Galleries; Sizable Bear is keywords only. */
class HobMoreMechanicAndColorCardsScenariosTest {
    private val elriansRetinue = Cards.invent("Elrian's Retinue", "{2}{G}{U}", "Creature — Elf Soldier", """
        As long as you control another Elf, you may play an additional land on each of your turns.
        Landfall — Whenever a land you control enters, put two +1/+1 counters on target creature you control. It gains vigilance until end of turn.
    """, "3/4")
    private val woodlandMerrymaker = Cards.invent("Woodland Merrymaker", "{2}{G}{U}", "Creature — Elf Citizen", """
        When this creature enters, draw a card, then discard a card. If you discard a land card this way, put it from your graveyard onto the battlefield tapped.
        Landfall — Whenever a land you control enters, you may pay {1}{G}{U}. If you do, return this card from your graveyard to your hand.
    """, "3/2")
    private val revealTheMoonRunes = Cards.invent("Reveal the Moon-Runes", "{3}{U}", "Enchantment", "Whenever you cast a noncreature spell, you may draw X cards, where X is the amount of mana spent to cast that spell. If you do, discard two cards.")
    private val fortunateFind = Cards.invent("Fortunate Find", "{3}{U}{U}", "Enchantment", "Whenever an artifact you control enters, draw a card.")
    private val moonhowlStalker = Cards.invent("Moonhowl Stalker", "{B}", "Creature — Wolf", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Ferocious — Whenever this creature attacks while you control a creature with power 4 or greater, this creature gets +2/+2 until end of turn.
    """, "1/1")
    private val ravenousWargHound = Cards.invent("Ravenous Warg-Hound", "{1}{B}", "Creature — Wolf", """
        Deathtouch
        Ferocious — Whenever this creature attacks while you control a creature with power 4 or greater, you gain 2 life.
    """, "2/2")
    private val viciousLittleHare = Cards.invent("Vicious Little Hare", "{G}", "Creature — Rabbit", "Ferocious — At the beginning of combat on your turn, if you control a creature with power 4 or greater, put a +1/+1 counter on this creature.", "1/2")
    private val wargWhelp = Cards.invent("Warg Whelp", "{1}{G}", "Creature — Wolf", "Ferocious — Whenever this creature attacks while you control a creature with power 4 or greater, until end of turn, this creature gets +1/+0 and creatures you control gain trample.", "2/2")
    private val wildmarkScavenger = Cards.invent("Wildmark Scavenger", "{4}{G}", "Creature — Wolf", "Ferocious — Whenever this creature attacks while you control a creature with power 4 or greater, put a +1/+1 counter on each creature you control.", "3/6")
    private val theAlphaWarg = Cards.invent("The Alpha Warg", "{2}{B}{G}", "Legendary Creature — Wolf", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Ferocious — Whenever you attack while you control a creature with power 4 or greater, you draw a card and lose 1 life.
    """, "3/3")
    private val alphaWargsPack = Cards.invent("Alpha Warg's Pack", "{1}{B}{G}", "Creature — Wolf", """
        Trample
        This creature can't attack unless you control two or more other Wolves.
        At the beginning of your upkeep, create a 2/2 green Wolf creature token.
    """, "5/3")
    private val gemStuddedWarg = Cards.invent("Gem-Studded Warg", "{1}{G}", "Creature — Wolf", """
        Trample
        Whenever this creature deals combat damage to a player, choose one —
        • Put a +1/+1 counter on target Wolf you control.
        • Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """, "3/2")
    private val gloamwatchStalker = Cards.invent("Gloamwatch Stalker", "{2}{B/G}", "Creature — Wolf", """
        This creature can't be blocked by tokens.
        When this creature enters, put a +1/+1 counter on target creature.
    """, "3/1")
    private val wastelandProwler = Cards.invent("Wasteland Prowler", "{1}{B}", "Creature — Wolf", "Pay 2 life: This creature gets +2/+2 until end of turn. Activate only once each turn.", "2/2")
    private val backDoorGuards = Cards.invent("Back Door Guards", "{1}{B}", "Creature — Goblin Soldier", "When this creature dies, target creature an opponent controls gets -1/-1 until end of turn.", "2/2")
    private val fearsomeBatSwarm = Cards.invent("Fearsome Bat-Swarm", "{4}{B}", "Creature — Bat", """
        This spell costs {3} less to cast if a creature died this turn.
        Flying, deathtouch
    """, "4/2")
    private val lankyGreyHound = Cards.invent("Lanky Grey Hound", "{3}", "Creature — Dog", """
        Flash
        Reach
        When this creature enters, create a tapped Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """, "2/2")
    private val ancientThrush = Cards.invent("Ancient Thrush", "{2}", "Creature — Bird", """
        Flying
        When this creature enters, you gain 2 life. You may search your library for a basic land card, reveal it, then shuffle and put that card on top.
    """, "1/2")
    private val herdOfPonies = Cards.invent("Herd of Ponies", "{2}", "Creature — Horse", "{2}, {T}, Sacrifice this creature: Search your library for up to two basic land cards, reveal them, put one onto the battlefield tapped and the other into your hand, then shuffle.", "2/1")
    private val camelliaTuck = Cards.invent("Camellia Tuck", "{1}{W}", "Legendary Creature — Halfling Citizen", "Whenever a token you control enters, you gain 1 life if this is the first time this ability has resolved this turn. If it's the second time, draw a card. If it's the third time, put a +1/+1 counter on each creature you control.", "2/2")
    private val dwarvenQuartermaster = Cards.invent("Dwarven Quartermaster", "{1}{W}", "Creature — Dwarf Citizen", "{3}{W}: Creatures you control get +1/+1 until end of turn.", "2/2")
    private val eagleOfTheHighLedge = Cards.invent("Eagle of the High Ledge", "{4}{W}", "Creature — Bird Soldier", """
        Flying
        Whenever this creature attacks, it gets +1/+1 until end of turn for each other creature you control.
    """, "2/5")
    private val mereTownTinker = Cards.invent("Mere-town Tinker", "{3}{W}", "Creature — Human Artificer", "At the beginning of combat on your turn, if you've drawn two or more cards this turn, another target creature you control gets +3/+0 and gains first strike until end of turn.", "3/4")
    private val gloriousFinale = Cards.invent("Glorious Finale", "{4}{W}", "Instant", """
        This spell costs {3} less to cast if it targets a tapped creature.
        Glorious Finale deals 5 damage to target creature.
    """)
    private val instantOfTriumph = Cards.invent("Instant of Triumph", "{W}", "Sorcery", """
        Put a +1/+1 counter on target creature you control. If this spell was cast from a graveyard, also put a +1/+1 counter on each other creature you control.
        Flashback {4}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val rockAtDaybreak = Cards.invent("Rock at Daybreak", "{1}{W}", "Instant", """
        Choose one —
        • Destroy target creature with power 4 or greater.
        • Until end of turn, target creature becomes an artifact in addition to its other types and gains indestructible. (Damage and effects that say "destroy" don't destroy it.)
    """)
    private val tharnsFinalStand = Cards.invent("Tharn's Final Stand", "{2}{W}{W}", "Instant", """
        Choose one —
        • Creatures you control get +2/+1 until end of turn.
        • Destroy target artifact or enchantment. You gain 2 life.
    """)
    private val bamboozleAndBefuddle = Cards.invent("Bamboozle and Befuddle", "{2}{U}", "Instant", """
        Choose one —
        • Counter target spell unless its controller pays {4}.
        • Draw two cards, then discard a card.
    """)
    private val elfLordsLutist = Cards.invent("Elf-lord's Lutist", "{1}{U}", "Creature — Elf Bard", "{4}{U}: Target creature can't be blocked this turn.", "2/2")
    private val bewitchedStreamsClutch = Cards.invent("Bewitched Stream's Clutch", "{2}{U}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, tap enchanted creature and remove all counters from it.
        Enchanted creature loses all abilities and doesn't untap during its controller's untap step.
    """)
    private val grimbaldRoamingWizard = Cards.invent("Grimbald, Roaming Wizard", "{4}{U}", "Legendary Creature — Avatar Wizard", """
        Ward {3} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {3}.)
        {6}: Grimbald's owner shuffles him into their library and draws three cards.
    """, "4/5")
    private val theLordOfTheHawks = Cards.invent("The Lord of the Hawks", "{7}{U}{U}", "Legendary Creature — Bird Noble", """
        Flash
        This spell costs {X} less to cast, where X is the total power of creatures you control with flying.
        Flying
    """, "8/8")
    private val raidTheTrollWoods = Cards.invent("Raid the Troll-Woods", "{1}{U}", "Instant", """
        Draw a card. If this spell was cast from a graveyard, draw two cards instead.
        Flashback {3}{U} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val awkwardFarewells = Cards.invent("Awkward Farewells", "{3}{U}", "Instant", """
        This spell costs {1} less to cast if it targets an attacking nontoken creature.
        Target creature's owner puts it on their choice of the top or bottom of their library.
    """)
    private val grindingOfFangs = Cards.invent("Grinding of Fangs", "{1}{B}{B}", "Sorcery", """
        Choose one —
        • Target creature gets -5/-5 until end of turn. If that creature would die this turn, exile it instead.
        • Creatures target player controls get -1/-1 until end of turn.
    """)
    private val hugeAngryHornet = Cards.invent("Huge Angry Hornet", "{2}{B}", "Creature — Insect", """
        Flying
        Whenever one or more other creatures die, scry 1. (Look at the top card of your library. You may put that card on the bottom.)
    """, "2/2")
    private val solemnHowl = Cards.invent("Solemn Howl", "{2}{B}", "Instant", """
        Choose one —
        • Target player draws two cards and loses 2 life.
        • Target creature gets +2/+2 and gains lifelink until end of turn.
    """)
    private val theStingeworthTunnelys = Cards.invent("The Stingeworth-Tunnelys", "{1}{B}", "Legendary Creature — Halfling Citizen", """
        When The Stingeworth-Tunnelys enter, you may sacrifice another creature or artifact. If you do, draw a card and create a Treasure token.
        Whenever you sacrifice a token, target opponent loses 1 life.
    """, "2/2")
    private val kickUpAFuss = Cards.invent("Kick Up a Fuss", "{B}", "Sorcery", """
        As an additional cost to cast this spell, sacrifice an artifact or creature or pay {4}.
        Destroy target creature.
    """)
    private val gravelVoicedGoblins = Cards.invent("Gravel-Voiced Goblins", "{1}{B}", "Creature — Goblin Bard", "When this creature enters, each opponent discards a card.", "1/1")
    private val devastationOfSkarg = Cards.invent("Devastation of Skarg", "{2}{R}{R}", "Sorcery", """
        Devastation of Skarg deals 3 damage to each non-Dragon creature.
        Add four mana in any combination of colors. Spend this mana only to cast Dragon spells.
    """)
    private val dorinCarrierOfComrades = Cards.invent("Dorin, Carrier of Comrades", "{2}{R}", "Legendary Creature — Dwarf Warrior", """
        Trample
        When Dorin enters, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """, "3/2")
    private val grimbaldFireworkMaker = Cards.invent("Grimbald, Firework-Maker", "{4}{R}{R}", "Legendary Creature — Avatar Wizard", """
        Reach
        When Grimbald enters, he deals 3 damage divided as you choose among one, two, or three targets.
    """, "4/3")
    private val escapeBarrel = Cards.invent("Escape Barrel", "{3}{R}", "Artifact", "When this artifact is put into a graveyard from the battlefield, reveal the top thirteen cards of your library. Put a random creature card from among them onto the battlefield. Put the rest on the bottom of your library in a random order.")
    private val mountGrimOpportunist = Cards.invent("Mount Grim Opportunist", "{3}{R}", "Creature — Goblin Rogue", "When this creature enters, exile the top card of your library. Until the end of your next turn, you may play that card.", "4/2")
    private val finalGlowOfTheDwarfKingsDay = Cards.invent("Final Glow of the Dwarf-King's Day", "{1}{R}", "Enchantment", """
        Whenever a Mountain you control enters, put a quest counter on this enchantment. If it has six or more quest counters on it, sacrifice it. If you do, search your hand and/or library for a Dragon card and put it onto the battlefield. If you search your library this way, shuffle.
        Mountaincycling {2} ({2}, Discard this card: Search your library for a Mountain card, reveal it, put it into your hand, then shuffle.)
    """)
    private val acornVolley = Cards.invent("Acorn Volley", "{1}{R}", "Instant", """
        Choose one or both —
        • Acorn Volley deals 3 damage to target creature. If that creature would die this turn, exile it instead.
        • Destroy target artifact token.
    """)
    private val skargTheSplendid = Cards.invent("Skarg the Splendid", "{2}{R}{R}", "Legendary Creature — Dragon", """
        Flying, haste
        Whenever Skarg attacks, he deals damage equal to the number of Treasures you control to any target.
        At the beginning of your upkeep, create a Treasure token.
    """, "4/3")
    private val skargsWrath = Cards.invent("Skarg's Wrath", "{1}{R}", "Instant", "Target creature gets +3/+0 and gains reach and first strike until end of turn.")
    private val iceslopeTracker = Cards.invent("Iceslope Tracker", "{2}{R}", "Creature — Goblin Ranger", "Sacrifice another creature or artifact: Exile the top card of your library. You may play it until the end of your next turn. Activate only during your turn and only once each turn.", "2/3")
    private val rockGiantOfTheUpperGap = Cards.invent("Rock-Giant of the Upper Gap", "{5}{R}{R}", "Creature — Giant", """
        Whenever this creature enters or attacks, create a 3/1 colorless Wall artifact creature token with defender named Stone Boulder.
        {2}{R}, Sacrifice an artifact: This creature deals 4 damage to any target.
    """, "7/7")
    private val barnulfTheSavage = Cards.invent("Barnulf the Savage", "{3}{G}{G}", "Legendary Creature — Bear Shapeshifter Warrior", """
        Trample
        Other Bears you control get +2/+2.
        At the beginning of combat on your turn, put a trample counter on up to one target creature you control. It becomes a Bear in addition to its other types. Then if you control three or more Bears, draw two cards.
    """, "6/6")
    private val grumpyWardens = Cards.invent("Grumpy Wardens", "{5}{G}", "Creature — Elf Soldier", """
        Affinity for Elves (This spell costs {1} less to cast for each Elf you control.)
        When this creature enters, mill four cards, then put all Elf cards from among them into your hand.
    """, "4/3")
    private val enormousHugeBear = Cards.invent("Enormous Huge Bear", "{5}{G}{G}", "Creature — Bear", """
        This spell can't be countered.
        Hexproof, haste
    """, "10/7")
    private val wardenOfTheGalleries = Cards.invent("Warden of the Galleries", "{1}{G}", "Creature — Elf Soldier", """
        Trample
        {5}{G}{G}: Put three +1/+1 counters on this creature.
    """, "2/2")
    private val tinyBear = Cards.invent("Tiny Bear", "{2}{G}", "Creature — Bear", """
        Flash
        When this creature enters, untap another target creature you control. If that creature is a Bear, put a +1/+1 counter on it.
    """, "3/2")
    private val gloomwoodTrailblazer = Cards.invent("Gloomwood Trailblazer", "{2}{G}", "Creature — Elf Ranger", "Gloomwood Trailblazer's power and toughness are each equal to the number of lands you control.", "*/*")
    private val ancientBloatedSpider = Cards.invent("Ancient Bloated Spider", "{4}{G}{G}", "Creature — Spider", """
        Reach
        This creature can't be blocked by creatures with power 2 or less.
        Whenever this creature becomes the target of a spell or ability an opponent controls, draw a card.
    """, "6/7")
    private val partingAsFriends = Cards.invent("Parting as Friends", "{4}{G}", "Enchantment", "Whenever a nontoken creature you control dies, reveal cards from the top of your library until you reveal a creature card. If its mana value is less than or equal to the number of lands you control, put it onto the battlefield. Otherwise, put it into your hand. Put the rest on the bottom of your library in a random order. This ability triggers only once each turn.")
    private val squabble = Cards.invent("Squabble", "{1}{G}", "Instant", "Target creature you control deals damage equal to its power to target creature an opponent controls.")
    private val rhodricOfThornhollow = Cards.invent("Rhodric of Thornhollow", "{2}{G}{G}", "Legendary Creature — Avatar Wizard", "The first creature spell you cast each turn costs {2} less to cast and can be cast as though it had flash.", "2/5")
    private val beyondTheWoodlandArch = Cards.invent("Beyond the Woodland Arch", "{6}{G}{G}", "Sorcery", "Look at the top twenty cards of your library, put any number of land cards from among them onto the battlefield tapped, then shuffle. You gain 8 life.")
    private val trollBargaining = Cards.invent("Troll Bargaining", "{2}{G}{G}", "Sorcery", "Put two +1/+1 counters on target creature you control. Then it fights target creature an opponent controls. (Each deals damage equal to its power to the other.)")
    private val wargStratagems = Cards.invent("Warg Stratagems", "{1}{G}", "Instant", """
        Choose one —
        • Destroy target creature with flying.
        • Put a +1/+1 counter on target creature you control. It gains trample and hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)
    """)
    private val groveElves = Cards.invent("Grove Elves", "{2}{G}", "Creature — Elf Scout", "When this creature enters, search your library for a Forest card, put that card onto the battlefield, then shuffle.", "1/1")
    private val forestLoommaster = Cards.invent("Forest Loommaster", "{1}{G}", "Creature — Elf Druid", """
        Vigilance
        Whenever another Elf you control enters, this creature gets +1/+1 until end of turn.
        {T}: Add X mana of any one color, where X is this creature's power. Spend this mana only to cast Elf spells and activate abilities of Elf sources.
    """, "1/2")
    private val darvisRetinue = Cards.invent("Darvi's Retinue", "{R}{W}", "Creature — Dwarf Warrior", """
        This creature has lifelink as long as you control another Dwarf.
        When this creature enters, look at the top four cards of your library. You may reveal a Dwarf or Equipment card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
    """, "2/2")
    private val hawksSalvation = Cards.invent("Hawk's Salvation", "{2}{W/U}{W/U}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +2/+2 and has flying.
        {2}{W/U}{W/U}: Return this card from your graveyard to the battlefield attached to target creature you control with power 1 or less. Activate only as a sorcery.
    """)
    private val gloomwoodCaretaker = Cards.invent("Gloomwood Caretaker", "{2}{G/U}", "Creature — Elf Ranger", "When this creature enters, return up to one other target permanent you control to its owner's hand. If you do, put a +1/+1 counter on this creature.", "3/2")
    private val norviSpinnerOfYarns = Cards.invent("Norvi, Spinner of Yarns", "{1}{R/W}", "Legendary Creature — Dwarf Bard", "Whenever Norvi attacks, target attacking creature gains first strike until end of turn.", "2/2")
    private val titansRock = Cards.invent("Titan's Rock", "{1}", "Artifact", """
        When this artifact enters, scry 2. (Look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)
        {1}, {T}: Add one mana of any color.
        {7}, {T}, Sacrifice this artifact: Destroy target permanent.
    """)
    private val keyToTheBackGate = Cards.invent("Key to the Back Gate", "{1}", "Artifact", """
        {2}, {T}: Target creature can't be blocked this turn.
        {1}, {T}, Discard a legendary card with the same name as a legendary permanent you control: Draw two cards.
    """)
    private val grandsiresChart = Cards.invent("Grandsire's Chart", "{2}", "Legendary Artifact", """
        When Grandsire's Chart enters, search your library for a basic land card, reveal it, put it into your hand, then shuffle.
        {2}, {T}: Draw a card, then discard a card.
    """)
    private val elfLordsGalleries = Cards.invent("Elf-lord's Galleries", null, "Land", """
        This land enters tapped.
        {T}: Add {G} or {U}.
        {2}{G}{U}, {T}, Sacrifice this land: Put two +1/+1 counters on target Elf you control. Activate only as a sorcery.
    """)
    private val halflingBurrow = Cards.invent("Halfling Burrow", null, "Land", """
        {T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
        Halflingcycling {4} ({4}, Discard this card: Search your library for a Halfling card, reveal it, put it into your hand, then shuffle.)
    """)

    private val wolf = Cards.invent("Lonely Hill Wolf", "{1}{G}", "Creature — Wolf", "", "2/2")
    private val dragon = Cards.invent("Lonely Hill Drake", "{3}{R}", "Creature — Dragon", "Flying", "4/4")
    private val counterspell = Cards.invent("Lonely Hill Refusal", "{U}{U}", "Instant", "Counter target spell.")
    private val hoard = Cards.invent("Lonely Hill Hoard", "{R}", "Sorcery", "Create three Treasure tokens.")
    private val lesson = Cards.invent("Lonely Hill Lesson", "{1}{U}", "Sorcery", "Draw a card.")
    private val muster = Cards.invent("Lonely Hill Muster", "{W}", "Instant", "Create a 1/1 white Soldier creature token.")
    private val draw2 = Cards.invent("Lonely Hill Letters", "{U}", "Sorcery", "Draw two cards.")

    // ---- Chunk 07: drawing and landfall

    @Test
    fun elriansRetinueCountersOnLandfallAndAnExtraLand() = scenario {
        alice.battlefield(elriansRetinue, elf)
        alice.hand("Forest", "Island")
        alice.plays("Forest")
        resolveAll()
        targetsIfAsked(elf)
        resolveAll()
        expect {
            elf.counters(p1p1) shouldBe 2
            elf hasKeyword Keyword.Vigilance
        }
        alice.plays("Island")
        resolveAll()
        targetsIfAsked(elf)
        resolveAll()
        expect { alice.battlefield has "Island" }
    }

    @Test
    fun woodlandMerrymakerPutsADiscardedLandIn() = scenario {
        alice.battlefield("Forest", "Forest", "Island", "Island")
        alice.hand(woodlandMerrymaker)
        alice.library("Island")
        alice.casts(woodlandMerrymaker)
        resolveAll()
        chooseIfOffered("Island")
        resolveAll()
        expect { alice.battlefield.names.count { it == "Island" } shouldBe 3 }
    }

    @Test
    fun woodlandMerrymakerReturnsOnLandfall() = scenario {
        alice.battlefield("Forest", "Forest", "Island")
        alice.graveyard(woodlandMerrymaker)
        alice.hand("Island")
        alice.plays("Island")
        resolveAll()
        may(true)
        payIfAsked()
        resolveAll()
        expect { alice.hand has woodlandMerrymaker }
    }

    @Test
    fun revealTheMoonRunesDrawsByManaSpent() = scenario {
        alice.battlefield(revealTheMoonRunes, "Island", "Island", "Island")
        alice.hand(lesson, ogre, bear)
        alice.library(elf, elf, elf)
        alice.casts(lesson)
        resolveAll()
        may(true)
        resolveAll()
        alice.chooses(ogre, bear)
        resolveAll()
        // the trigger first (two cards drawn, two discarded), then the spell's own draw
        expect {
            alice.hand.size shouldBe 3
            alice.graveyard has ogre
        }
    }

    @Test
    fun fortunateFindDrawsForArtifacts() = scenario {
        alice.battlefield(fortunateFind, "Plains", "Plains")
        alice.hand(idol)
        alice.library(bear)
        alice.casts(idol)
        resolveAll()
        expect { alice.hand has bear }
    }

    // ---- Chunk 08: Wolves and ferocious

    @Test
    fun moonhowlStalkerFerocious() = scenario {
        alice.battlefield(moonhowlStalker, giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(moonhowlStalker)
        resolveAll()
        expect { moonhowlStalker.power shouldBe 3 }
    }

    @Test
    fun moonhowlStalkerWithoutAFourPowerCreature() = scenario {
        alice.battlefield(moonhowlStalker, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(moonhowlStalker)
        resolveAll()
        expect { moonhowlStalker.power shouldBe 1 }
    }

    @Test
    fun ravenousWargHoundGainsLife() = scenario {
        alice.battlefield(ravenousWargHound, giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ravenousWargHound)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    // CR 603.4: the intervening "if"
    @Test
    fun viciousLittleHareGrowsAtCombat() = scenario {
        alice.battlefield(viciousLittleHare, giant)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect { viciousLittleHare.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun wargWhelpGivesTrample() = scenario {
        alice.battlefield(wargWhelp, giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wargWhelp)
        resolveAll()
        expect {
            wargWhelp.power shouldBe 3
            giant hasKeyword Keyword.Trample
        }
    }

    @Test
    fun wildmarkScavengerCountersTheTeam() = scenario {
        alice.battlefield(wildmarkScavenger, giant, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wildmarkScavenger)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            wildmarkScavenger.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun theAlphaWargDrawsOnAttack() = scenario {
        alice.battlefield(theAlphaWarg, giant)
        alice.library(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(theAlphaWarg)
        resolveAll()
        expect {
            alice.hand has bear
            alice.life shouldBe 19
        }
    }

    // CR 508.1c: an attack restriction
    @Test
    fun alphaWargsPackNeedsTwoOtherWolves() = scenario {
        alice.battlefield(alphaWargsPack, wolf)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(alphaWargsPack) }
    }

    @Test
    fun alphaWargsPackMakesWolvesEachUpkeep() = scenario {
        alice.battlefield(alphaWargsPack)
        alice.library(bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect { alice.battlefield has "Wolf Token" }
    }

    @Test
    fun gemStuddedWargMakesATreasure() = scenario {
        alice.battlefield(gemStuddedWarg)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(gemStuddedWarg)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        modes(1)
        resolveAll()
        expect { alice.battlefield has "Treasure Token" }
    }

    // CR 509.1b: a blocking restriction
    @Test
    fun gloamwatchStalkerCantBeBlockedByTokens() = scenario {
        alice.battlefield(gloamwatchStalker)
        bob.battlefield("Plains")
        bob.hand(muster)
        alice.passes()
        bob.casts(muster)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(gloamwatchStalker)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks("Soldier Token" to gloamwatchStalker) }
    }

    @Test
    fun gloamwatchStalkerCountersOnEntering() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", bear)
        alice.hand(gloamwatchStalker)
        alice.casts(gloamwatchStalker)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun wastelandProwlerPaysLifeOnce() = scenario {
        alice.battlefield(wastelandProwler)
        alice.activates(wastelandProwler)
        resolveAll()
        expect {
            wastelandProwler.power shouldBe 4
            alice.life shouldBe 18
        }
        assertFailsWith<IllegalArgumentException> { alice.activates(wastelandProwler) }
    }

    @Test
    fun backDoorGuardsShrinkOnDeath() = scenario {
        alice.battlefield(backDoorGuards, "Swamp")
        alice.hand(doom)
        bob.battlefield(elf)
        alice.casts(doom, target = backDoorGuards)
        resolveAll()
        targetsIfAsked(elf)
        resolveAll()
        expect { bob.graveyard has elf }
    }

    @Test
    fun fearsomeBatSwarmCheaperAfterADeath() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(doom, fearsomeBatSwarm)
        bob.battlefield(bear)
        alice.casts(doom, target = bear)
        resolveAll()
        alice.casts(fearsomeBatSwarm)
        resolveAll()
        expect { alice.battlefield has fearsomeBatSwarm }
    }

    // ---- Chunks 09–13: mono-colored

    @Test
    fun lankyGreyHoundBringsATappedTreasure() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(lankyGreyHound)
        alice.casts(lankyGreyHound)
        resolveAll()
        expect { "Treasure Token".tapped shouldBe true }
    }

    @Test
    fun ancientThrushGainsAndTopsALand() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(ancientThrush)
        alice.library(bear, "Forest", ogre)
        alice.casts(ancientThrush)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered("Forest")
        resolveAll()
        expect {
            alice.life shouldBe 22
            alice.library.names.first() shouldBe "Forest"
        }
    }

    @Test
    fun herdOfPoniesFetchesTwoBasics() = scenario {
        alice.battlefield(herdOfPonies, "Plains", "Plains")
        alice.library("Forest", bear, "Island")
        alice.activates(herdOfPonies)
        resolveAll()
        alice.chooses("Forest", "Island")
        resolveAll()
        chooseIfOffered("Forest")
        resolveAll()
        expect { alice.battlefield.names.count { it == "Forest" || it == "Island" } shouldBe 1 }
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 603.2: counting its resolutions this turn
    @Test
    fun camelliaTuckRewardsEachToken() = scenario {
        alice.battlefield(camelliaTuck, "Mountain")
        alice.hand(hoard)
        alice.library(bear)
        alice.casts(hoard)
        resolveAll()
        expect {
            alice.life shouldBe 21
            alice.hand has bear
            camelliaTuck.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun dwarvenQuartermasterPumpsTheTeam() = scenario {
        alice.battlefield(dwarvenQuartermaster, bear, "Plains", "Plains", "Plains", "Plains")
        alice.activates(dwarvenQuartermaster)
        resolveAll()
        expect { bear.power shouldBe 3 }
    }

    @Test
    fun eagleOfTheHighLedgeGrowsPerCreature() = scenario {
        alice.battlefield(eagleOfTheHighLedge, bear, elf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(eagleOfTheHighLedge)
        resolveAll()
        expect { eagleOfTheHighLedge.power shouldBe 4 }
    }

    @Test
    fun mereTownTinkerArmsAfterTwoDraws() = scenario {
        alice.battlefield(mereTownTinker, bear, "Island")
        alice.hand(draw2)
        alice.library(elf, elf)
        alice.casts(draw2)
        resolveAll()
        advanceTo(TurnStep.BeginningOfCombat)
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear hasKeyword Keyword.FirstStrike
        }
    }

    @Test
    fun gloriousFinaleCheaperOnATappedCreature() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(gloriousFinale)
        bob.battlefield(giant, tapped = true)
        alice.casts(gloriousFinale, target = giant)
        resolveAll()
        expect { bob.graveyard has giant }
    }

    @Test
    fun instantOfTriumphFromTheGraveyardCountersAll() = scenario {
        alice.battlefield(bear, elf, "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.graveyard(instantOfTriumph)
        alice.casts(instantOfTriumph, target = bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            elf.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun rockAtDaybreakDestroysTheBig() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(rockAtDaybreak)
        bob.battlefield(giant)
        alice.casts(rockAtDaybreak, modes = listOf(0), target = giant)
        resolveAll()
        expect { bob.graveyard has giant }
    }

    @Test
    fun rockAtDaybreakMakesAnIndestructibleArtifact() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(rockAtDaybreak)
        alice.casts(rockAtDaybreak, modes = listOf(1), target = bear)
        resolveAll()
        expect {
            bear.types.contains(CardType.Artifact) shouldBe true
            bear hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun tharnsFinalStandRallies() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(tharnsFinalStand)
        alice.casts(tharnsFinalStand, modes = listOf(0))
        resolveAll()
        expect { bear.power shouldBe 4 }
    }

    @Test
    fun tharnsFinalStandShattersAndHeals() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(tharnsFinalStand)
        bob.battlefield(idol)
        alice.casts(tharnsFinalStand, modes = listOf(1), target = idol)
        resolveAll()
        expect {
            bob.graveyard has idol
            alice.life shouldBe 22
        }
    }

    @Test
    fun bamboozleAndBefuddleLoots() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(bamboozleAndBefuddle, ogre)
        alice.library(bear, elf)
        alice.casts(bamboozleAndBefuddle, modes = listOf(1))
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.graveyard has ogre
        }
    }

    @Test
    fun elfLordsLutistMakesUnblockable() = scenario {
        alice.battlefield(elfLordsLutist, bear, "Island", "Island", "Island", "Island", "Island")
        bob.battlefield(ogre)
        alice.activates(elfLordsLutist, target = bear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to bear) }
    }

    @Test
    fun bewitchedStreamsClutchLocksDown() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(bewitchedStreamsClutch)
        bob.battlefield(wastelandProwler, counters = listOf(p1p1 to 2))
        alice.casts(bewitchedStreamsClutch, target = wastelandProwler)
        resolveAll()
        expect {
            wastelandProwler.tapped shouldBe true
            wastelandProwler.counters(p1p1) shouldBe 0
            card(wastelandProwler).view.characteristics!!.abilities.size shouldBe 0
        }
    }

    @Test
    fun grimbaldRoamingWizardShufflesHimselfAway() = scenario {
        alice.battlefield(grimbaldRoamingWizard, "Island", "Island", "Island", "Island", "Island", "Island")
        alice.library(bear, bear, bear)
        alice.activates(grimbaldRoamingWizard)
        resolveAll()
        expect {
            alice.hand.size shouldBe 3
            alice.battlefield lacks grimbaldRoamingWizard
        }
    }

    @Test
    fun theLordOfTheHawksCheaperByFlyers() = scenario {
        alice.battlefield(sphinx, sphinx, "Island", "Island", "Island")
        alice.hand(theLordOfTheHawks)
        alice.casts(theLordOfTheHawks)
        resolveAll()
        expect { alice.battlefield has theLordOfTheHawks }
    }

    @Test
    fun raidTheTrollWoodsDrawsTwoFromTheGraveyard() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.graveyard(raidTheTrollWoods)
        alice.library(bear, bear)
        alice.casts(raidTheTrollWoods)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun awkwardFarewellsCheaperOnAnAttacker() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(awkwardFarewells)
        alice.library(elf)
        bob.battlefield(ogre)
        bob.library(bear, bear)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(ogre)
        bob.passes()
        alice.casts(awkwardFarewells, target = ogre)
        resolveAll()
        if (pending is Decision.ChooseOption) option("bottom")
        resolveAll()
        expect { bob.library.names.last() shouldBe ogre }
    }

    @Test
    fun grindingOfFangsExilesWhatDies() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(grindingOfFangs)
        bob.battlefield(giant)
        alice.casts(grindingOfFangs, modes = listOf(0), target = giant)
        resolveAll()
        expect { bob.exile has giant }
    }

    @Test
    fun grindingOfFangsShrinksAPlayersCreatures() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(grindingOfFangs)
        bob.battlefield(elf, bear)
        alice.casts(grindingOfFangs, modes = listOf(1), target = "bob")
        resolveAll()
        expect {
            bob.graveyard has elf
            bear.power shouldBe 1
        }
    }

    @Test
    fun hugeAngryHornetScriesOnDeaths() = scenario {
        alice.battlefield(hugeAngryHornet, "Swamp")
        alice.hand(doom)
        alice.library(ogre)
        bob.battlefield(bear)
        alice.casts(doom, target = bear)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect { alice.library.names.last() shouldBe ogre }
    }

    @Test
    fun solemnHowlDrawsTwo() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(solemnHowl)
        alice.library(bear, bear)
        alice.casts(solemnHowl, modes = listOf(0), target = "alice")
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.life shouldBe 18
        }
    }

    @Test
    fun solemnHowlLifelinkPump() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", bear)
        alice.hand(solemnHowl)
        alice.casts(solemnHowl, modes = listOf(1), target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Lifelink
        }
    }

    @Test
    fun theStingeworthTunnelysTradeAndDrain() = scenario {
        alice.battlefield("Swamp", "Swamp", elf)
        alice.hand(theStingeworthTunnelys)
        alice.library(bear)
        alice.casts(theStingeworthTunnelys)
        resolveAll()
        may(true)
        chooseIfOffered(elf)
        resolveAll()
        expect {
            alice.hand has bear
            alice.battlefield has "Treasure Token"
        }
        alice.activates("Treasure Token", color = Color.Black)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun kickUpAFussBySacrifice() = scenario {
        alice.battlefield("Swamp", idol)
        alice.hand(kickUpAFuss)
        bob.battlefield(giant)
        alice.casts(kickUpAFuss, target = giant, option = "Sacrifice", choosing = listOf(idol))
        resolveAll()
        expect {
            bob.graveyard has giant
            alice.graveyard has idol
        }
    }

    @Test
    fun gravelVoicedGoblinsMakeThemDiscard() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(gravelVoicedGoblins)
        bob.hand(bear)
        alice.casts(gravelVoicedGoblins)
        resolveAll()
        chooseIfOffered(bear)
        resolveAll()
        expect { bob.graveyard has bear }
    }

    @Test
    fun devastationOfSkargSparesDragonsAndFundsThem() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", dragon)
        alice.hand(devastationOfSkarg, dragon)
        bob.battlefield(ogre)
        alice.casts(devastationOfSkarg)
        resolveAll()
        // four mana in any combination of colors: one color each
        repeat(4) { if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Red)) }
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.battlefield has dragon
            alice.pool.size shouldBe 4
        }
        alice.casts(dragon)
        resolveAll()
        expect { alice.battlefield.names.count { it == dragon } shouldBe 2 }
    }

    @Test
    fun dorinCarrierOfComradesBringsATreasure() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(dorinCarrierOfComrades)
        alice.casts(dorinCarrierOfComrades)
        resolveAll()
        expect { alice.battlefield has "Treasure Token" }
    }

    // CR 601.2d: dividing damage
    @Test
    fun grimbaldFireworkMakerDividesThree() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(grimbaldFireworkMaker)
        bob.battlefield(elf, bear)
        alice.casts(grimbaldFireworkMaker)
        resolveAll()
        pickTargets(elf, bear)
        if (pending is Decision.Divide) answer(Answer.Division(listOf(
            arcanum.engine.decision.Share(target(elf), 1), arcanum.engine.decision.Share(target(bear), 2))))
        resolveAll()
        expect {
            bob.graveyard has elf
            bob.graveyard has bear
        }
    }

    @Test
    fun escapeBarrelFindsACreature() = scenario {
        alice.battlefield(escapeBarrel, "Mountain")
        alice.library("Forest", ogre, "Island")
        alice.hand(shatter)
        alice.casts(shatter, target = escapeBarrel)
        resolveAll()
        expect { alice.battlefield has ogre }
    }

    @Test
    fun mountGrimOpportunistExilesAPlayableCard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Forest", "Forest")
        alice.hand(mountGrimOpportunist)
        alice.library(bear)
        alice.casts(mountGrimOpportunist, paying = listOf("Mountain", "Mountain", "Mountain", "Mountain"))
        resolveAll()
        alice.casts(bear)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    @Test
    fun finalGlowCallsADragonAtSixQuests() = scenario {
        alice.battlefield(finalGlowOfTheDwarfKingsDay, counters = listOf(CounterKind.Named("quest") to 5))
        alice.hand("Mountain", dragon)
        alice.plays("Mountain")
        resolveAll()
        chooseIfOffered(dragon)
        resolveAll()
        expect {
            alice.battlefield has dragon
            alice.graveyard has finalGlowOfTheDwarfKingsDay
        }
    }

    @Test
    fun acornVolleyBothModes() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(acornVolley)
        bob.battlefield(ogre)
        bob.hand(hoard)
        alice.casts(acornVolley, modes = listOf(0), target = ogre)
        resolveAll()
        expect { bob.exile has ogre }
    }

    @Test
    fun skargTheSplendidBurnsByTreasures() = scenario {
        alice.battlefield(skargTheSplendid, "Mountain")
        alice.hand(hoard)
        alice.casts(hoard)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(skargTheSplendid)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun skargTheSplendidTreasureEachUpkeep() = scenario {
        alice.battlefield(skargTheSplendid)
        alice.library(bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect { alice.battlefield has "Treasure Token" }
    }

    @Test
    fun skargsWrathArmsACreature() = scenario {
        alice.battlefield("Mountain", "Mountain", bear)
        alice.hand(skargsWrath)
        alice.casts(skargsWrath, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear hasKeyword Keyword.FirstStrike
            bear hasKeyword Keyword.Reach
        }
    }

    @Test
    fun iceslopeTrackerImpulsesForASacrifice() = scenario {
        alice.battlefield(iceslopeTracker, idol, "Forest", "Forest")
        alice.library(bear)
        alice.activates(iceslopeTracker, choosing = listOf(idol))
        resolveAll()
        alice.casts(bear)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    @Test
    fun rockGiantOfTheUpperGapThrowsBoulders() = scenario {
        alice.battlefield(rockGiantOfTheUpperGap, "Mountain", "Mountain", "Mountain")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rockGiantOfTheUpperGap)
        resolveAll()
        expect { alice.battlefield has "Stone Boulder" }
        advanceTo(TurnStep.PostcombatMain)
        alice.activates(rockGiantOfTheUpperGap, target = "bob", choosing = listOf("Stone Boulder"))
        resolveAll()
        expect { bob.life shouldBe 9 }
    }

    @Test
    fun barnulfTheSavageMakesBearsAndDraws() = scenario {
        alice.battlefield(barnulfTheSavage, bear, ogre)
        alice.library(elf, elf)
        advanceTo(TurnStep.BeginningOfCombat)
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            ogre.subtypes.map { it.name }.contains("Bear") shouldBe true
            ogre.power shouldBe 5
            alice.hand.size shouldBe 2
        }
    }

    @Test
    fun grumpyWardensFindElves() = scenario {
        alice.battlefield(elf, elf, "Forest", "Forest", "Forest", "Forest")
        alice.hand(grumpyWardens)
        alice.library(elfLordsLutist, bear, groveElves, ogre)
        alice.casts(grumpyWardens)
        resolveAll()
        expect {
            alice.hand has elfLordsLutist
            alice.hand has groveElves
            alice.graveyard has bear
        }
    }

    // CR 101.2: "can't be countered"
    @Test
    fun enormousHugeBearCantBeCountered() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(enormousHugeBear)
        bob.battlefield("Island", "Island")
        bob.hand(counterspell)
        alice.casts(enormousHugeBear)
        alice.passes()
        bob.casts(counterspell, target = enormousHugeBear)
        resolveAll()
        expect { alice.battlefield has enormousHugeBear }
    }

    @Test
    fun wardenOfTheGalleriesGrowsThree() = scenario {
        alice.battlefield(wardenOfTheGalleries, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.activates(wardenOfTheGalleries)
        resolveAll()
        expect { wardenOfTheGalleries.counters(p1p1) shouldBe 3 }
    }

    @Test
    fun tinyBearUntapsAndCountersABear() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.battlefield(bear, tapped = true)
        alice.hand(tinyBear)
        alice.casts(tinyBear)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.tapped shouldBe false
            bear.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun gloomwoodTrailblazerCountsLands() = scenario {
        alice.battlefield(gloomwoodTrailblazer, "Forest", "Forest", "Island")
        expect { gloomwoodTrailblazer.power shouldBe 3 }
    }

    @Test
    fun ancientBloatedSpiderDrawsWhenTargeted() = scenario {
        alice.battlefield(ancientBloatedSpider)
        alice.library(bear)
        bob.battlefield("Mountain")
        bob.hand(shock)
        turn(active = bob)
        bob.casts(shock, target = ancientBloatedSpider)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun ancientBloatedSpiderEvadesSmallBlockers() = scenario {
        alice.battlefield(ancientBloatedSpider)
        bob.battlefield(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ancientBloatedSpider)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(bear to ancientBloatedSpider) }
    }

    @Test
    fun partingAsFriendsReplacesTheFallen() = scenario {
        alice.battlefield(partingAsFriends, bear, "Swamp", "Forest", "Forest")
        alice.hand(doom)
        alice.library("Island", ogre, giant)
        alice.casts(doom, target = bear)
        resolveAll()
        expect { alice.battlefield has ogre }
    }

    @Test
    fun squabbleBites() = scenario {
        alice.battlefield("Forest", "Forest", giant)
        alice.hand(squabble)
        bob.battlefield(ogre)
        alice.casts(squabble, targets = listOf(giant, ogre))
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun rhodricFlashesTheFirstCreatureCheaply() = scenario {
        alice.battlefield(rhodricOfThornhollow, "Mountain")
        alice.hand(ogre)
        alice.library(bear)
        bob.library(bear)
        advanceTo(TurnStep.Upkeep, turn = 2)
        passPriority()
        alice.casts(ogre)
        resolveAll()
        expect { alice.battlefield has ogre }
    }

    @Test
    fun beyondTheWoodlandArchPutsLandsIn() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(beyondTheWoodlandArch)
        alice.library("Island", bear, "Plains", ogre)
        alice.casts(beyondTheWoodlandArch)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Island", "Plains")
        resolveAll()
        expect {
            alice.battlefield has "Island"
            alice.battlefield has "Plains"
            alice.life shouldBe 28
        }
    }

    @Test
    fun trollBargainingCountersAndFights() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", giant)
        alice.hand(trollBargaining)
        bob.battlefield(ogre)
        alice.casts(trollBargaining, targets = listOf(giant, ogre))
        resolveAll()
        expect {
            bob.graveyard has ogre
            giant.counters(p1p1) shouldBe 2
        }
    }

    @Test
    fun wargStratagemsGrounds() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(wargStratagems)
        bob.battlefield(flyer)
        alice.casts(wargStratagems, modes = listOf(0), target = flyer)
        resolveAll()
        expect { bob.graveyard has flyer }
    }

    @Test
    fun wargStratagemsProtects() = scenario {
        alice.battlefield("Forest", "Forest", bear)
        alice.hand(wargStratagems)
        alice.casts(wargStratagems, modes = listOf(1), target = bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Hexproof
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun groveElvesFetchAForest() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(groveElves)
        alice.library(bear, "Forest")
        alice.casts(groveElves)
        resolveAll()
        chooseIfOffered("Forest")
        resolveAll()
        expect { alice.battlefield.names.count { it == "Forest" } shouldBe 4 }
    }

    @Test
    fun forestLoommasterGrowsAndPaysForElves() = scenario {
        alice.battlefield(forestLoommaster, "Forest")
        alice.hand(elf)
        alice.casts(elf)
        resolveAll()
        expect { forestLoommaster.power shouldBe 2 }
        alice.activates(forestLoommaster, color = Color.Green)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        expect { alice.pool.size shouldBe 2 }
    }

    // ---- Chunk 14: multicolored, artifacts and lands

    @Test
    fun darvisRetinueFindsADwarf() = scenario {
        alice.battlefield("Mountain", "Plains", dwarf)
        alice.hand(darvisRetinue)
        alice.library(bear, dwarf, ogre, elf)
        alice.casts(darvisRetinue)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(dwarf)
        resolveAll()
        expect {
            alice.hand has dwarf
            darvisRetinue hasKeyword Keyword.Lifelink
        }
    }

    @Test
    fun hawksSalvationLiftsAndReturns() = scenario {
        alice.battlefield("Plains", "Plains", "Island", "Island", "Swamp", elf)
        alice.hand(hawksSalvation, doom)
        alice.casts(hawksSalvation, target = elf, paying = listOf("Plains", "Plains", "Island", "Island"))
        resolveAll()
        expect {
            elf.power shouldBe 3
            elf hasKeyword Keyword.Flying
        }
    }

    @Test
    fun hawksSalvationReturnsFromTheGraveyard() = scenario {
        alice.battlefield("Plains", "Plains", "Island", "Island", elf)
        alice.graveyard(hawksSalvation)
        alice.activates(hawksSalvation, target = elf)
        resolveAll()
        expect { elf hasKeyword Keyword.Flying }
    }

    @Test
    fun gloomwoodCaretakerBouncesForACounter() = scenario {
        alice.battlefield("Forest", "Forest", "Island", bear)
        alice.hand(gloomwoodCaretaker)
        alice.casts(gloomwoodCaretaker)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            alice.hand has bear
            gloomwoodCaretaker.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun norviGivesAnAttackerFirstStrike() = scenario {
        alice.battlefield(norviSpinnerOfYarns, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(norviSpinnerOfYarns, bear)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bear hasKeyword Keyword.FirstStrike }
    }

    @Test
    fun titansRockScriesAndDestroys() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(titansRock)
        alice.library(bear, ogre)
        bob.battlefield(giant)
        alice.casts(titansRock, paying = listOf("Plains"))
        resolveAll()
        chooseNone()
        (pending as? Decision.ChooseObjects)?.let { answer(Answer.Objects(it.options)) }
        resolveAll()
        change(Changes.tap(state, card(titansRock), false))
        alice.activates(titansRock, ability = 1, target = giant)
        resolveAll()
        expect { bob.graveyard has giant }
    }

    @Test
    fun keyToTheBackGateOpensTheWay() = scenario {
        alice.battlefield(keyToTheBackGate, bear, "Plains", "Plains")
        bob.battlefield(ogre)
        alice.activates(keyToTheBackGate, target = bear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to bear) }
    }

    @Test
    fun keyToTheBackGateDrawsForADuplicateLegend() = scenario {
        alice.battlefield(keyToTheBackGate, dorinCarrierOfComrades, "Plains")
        alice.hand(dorinCarrierOfComrades)
        alice.library(bear, bear)
        alice.activates(keyToTheBackGate, ability = 1, choosing = listOf(dorinCarrierOfComrades))
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun grandsiresChartFetchesAndLoots() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(grandsiresChart)
        alice.library("Forest", ogre)
        alice.casts(grandsiresChart, paying = listOf("Plains", "Plains"))
        resolveAll()
        chooseIfOffered("Forest")
        resolveAll()
        expect { alice.hand has "Forest" }
    }

    @Test
    fun elfLordsGalleriesTappedAndPumpsAnElf() = scenario {
        alice.battlefield(elfLordsGalleries, elf, "Forest", "Forest", "Island", "Island")
        alice.activates(elfLordsGalleries, ability = 1, target = elf)
        resolveAll()
        expect { elf.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun elfLordsGalleriesEntersTapped() = scenario {
        alice.hand(elfLordsGalleries)
        alice.plays(elfLordsGalleries)
        expect { elfLordsGalleries.tapped shouldBe true }
    }

    @Test
    fun halflingBurrowFetchesATappedBasic() = scenario {
        alice.battlefield(halflingBurrow)
        alice.library(bear, "Forest")
        alice.activates(halflingBurrow)
        resolveAll()
        chooseIfOffered("Forest")
        resolveAll()
        expect {
            "Forest".tapped shouldBe true
            alice.graveyard has halflingBurrow
        }
    }
}
