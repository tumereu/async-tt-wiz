package arcanum.engine.sets.tmt

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.change.Changes
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.tmt.Tmt.bear
import arcanum.engine.sets.tmt.Tmt.doom
import arcanum.engine.sets.tmt.Tmt.elf
import arcanum.engine.sets.tmt.Tmt.giant
import arcanum.engine.sets.tmt.Tmt.idol
import arcanum.engine.sets.tmt.Tmt.ogre
import arcanum.engine.sets.tmt.Tmt.p1p1
import arcanum.engine.sets.tmt.Tmt.shatter
import arcanum.engine.sets.tmt.Tmt.shock
import arcanum.engine.sets.tmt.Tmt.sphinx
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** TMT's Class, Food, Equipment and mono- and multicolored, artifact and land cards in play (parser chunks 07–14).
 * Grounded Forever and Decisive Takedown (destroy), Guardian Crane Stance (a pump), Family Tragedy and Robot Smashing Hour
 * (exile, burn), Pound (a bite), Ancient Pachyderm and Gizmo (entering gain or draw) and Tusker (keywords and
 * landcycling) repeat templates tested before; Northerly Gale Avatar's card from outside the game is left to the host. */
class TmtColorArtifactAndLandCardsScenariosTest {
    private val snarkyAttitude = Cards.invent("Snarky Attitude", "{1}{R}", "Enchantment — Class", """
        (Gain the next level as a sorcery to add its ability.)
        Whenever you attack, you may discard a card. If you do, draw a card.
        {1}{R}: Level 2
        Whenever you discard a card, this Class deals 2 damage to each opponent.
        {1}{R}: Level 3
        When this Class becomes level 3, search your library for a card, put it into your hand, shuffle, then discard a card at random.
    """)
    private val partyAnimalLifestyle = Cards.invent("Party Animal Lifestyle", "{G}", "Enchantment — Class", """
        (Gain the next level as a sorcery to add its ability.)
        When this Class enters, each player creates a Food token.
        {1}{G}: Level 2
        Whenever an artifact an opponent controls is put into a graveyard from the battlefield, draw a card.
        {4}{G}: Level 3
        Whenever one or more of your opponents are attacked, up to one target attacking creature gets +X/+X until end of turn, where X is the number of cards in your hand.
    """)
    private val scatterbrainedPigeon = Cards.invent("Scatterbrained Pigeon", "{W}", "Creature — Bird Mutant", """
        Flying
        When this creature leaves the battlefield, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
    """, "0/2")
    private val sardinePineapplePie = Cards.invent("Sardine & Pineapple Pie", "{2}{B}{B}", "Artifact — Food", """
        When this artifact enters, destroy target creature.
        {2}, {T}, Sacrifice this artifact: You gain 3 life.
    """)
    private val fieryPorridgePie = Cards.invent("Fiery Porridge Pie", "{2}{R}", "Artifact — Food", """
        When this artifact enters, it deals 4 damage to any target and 3 damage to you.
        {2}, {T}, Sacrifice this artifact: You gain 3 life.
    """)
    private val avocadoCocoaPie = Cards.invent("Avocado & Cocoa Pie", "{G}", "Artifact — Food", """
        Flash
        When this artifact enters, target creature gets +2/+2 until end of turn. Untap it.
        {2}, {T}, Sacrifice this artifact: You gain 3 life.
    """)
    private val kitchenSinkPie = Cards.invent("Kitchen Sink Pie", "{2}", "Artifact — Food", """
        When this artifact enters, search your library for a basic land card, reveal it, put it into your hand, then shuffle.
        {2}{W}{U}{B}{R}{G}, {T}, Sacrifice this artifact: Target player gains 3 life and draws a card. Each of your opponents discards a card. This artifact deals 3 damage to any target. Put three +1/+1 counters on up to one target creature.
    """)
    private val everyCheesePie = Cards.invent("Every-Cheese Pie", "{2}", "Artifact — Food", """
        When this artifact enters, draw a card.
        {1}, {T}, Sacrifice this artifact: Add one mana of any color.
        {2}, {T}, Sacrifice this artifact: You gain 3 life.
    """)
    private val snackCourier = Cards.invent("Snack Courier", "{1}{G}", "Creature — Human Citizen", "When this creature enters, you may search your library for a Food card, reveal it, put it into your hand, then shuffle. If you don't put a card into your hand this way, create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")", "1/2")
    private val spoiledSnacks = Cards.invent("Spoiled Snacks", "{1}{B}{G}", "Instant", "Destroy target artifact or creature. If its mana value was 4 or less, create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")")
    private val gelatoKitten = Cards.invent("Gelato Kitten", "{1}{B/G}", "Artifact Creature — Food Cat Mutant", """
        {2}, Sacrifice another creature or token: Draw a card. Activate only as a sorcery.
        {2}, {T}, Sacrifice this creature: You gain 3 life.
    """, "1/3")
    private val peerlessKatana = Cards.invent("Peerless Katana", "{W}", "Artifact — Equipment", """
        Equipped creature gets +1/+1 and has "Whenever this creature deals combat damage, untap it and you gain 2 life."
        Whenever a Ninja you control enters, you may attach this Equipment to it.
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val tailoredBoStaff = Cards.invent("Tailored Bo Staff", "{2}{U}", "Artifact — Equipment", """
        When this Equipment enters, return up to one other target nonland permanent to its owner's hand.
        Equipped creature gets +2/+1 and has vigilance.
        Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val hardFoughtSai = Cards.invent("Hard-Fought Sai", "{1}{R}", "Artifact — Equipment", """
        Equipped creature has double strike.
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val scrapheapArmory = Cards.invent("Scrapheap Armory", "{1}{R}", "Artifact — Equipment", """
        Equipped creature gets +1/+0 for each artifact you control.
        {4}{R}: Create a token that's a copy of this Equipment.
        Equip {R}
    """)
    private val neonNunchaku = Cards.invent("Neon Nunchaku", "{2}{G}", "Artifact — Equipment", """
        When this Equipment enters, attach it to target creature you control. When you do, equipped creature fights up to one target creature an opponent controls. (Each deals damage equal to its power to the other.)
        Equipped creature gets +1/+1 and has trample.
        Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val sewerSkateboard = Cards.invent("Sewer Skateboard", "{1}", "Artifact — Equipment", """
        When this Equipment enters, tap target permanent.
        Equipped creature gets +1/+0 and has haste.
        Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val eveningNewsCrew = Cards.invent("Evening News Crew", "{1}{W}", "Creature — Human Citizen", """
        Vigilance
        Channel — {6}, Discard this card: Put a +1/+1 counter on each creature you control. Draw a card.
    """, "2/2")
    private val directorHalberdSuitAndShades = Cards.invent("Director Halberd, Suit and Shades", "{2}{W}", "Legendary Creature — Human Soldier", "At the beginning of combat on your turn, put a +1/+1 counter on each of up to two target creatures.", "1/2")
    private val juniperKunoichiTrainee = Cards.invent("Juniper, Kunoichi Trainee", "{1}{W}", "Legendary Creature — Human Ninja", """
        When Juniper enters, scry 2. (Look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)
        Juniper can't be blocked by creatures with power 3 or greater.
    """, "2/2")
    private val interdimensionalBanishment = Cards.invent("Interdimensional Banishment", "{1}{W}", "Enchantment — Aura", """
        Enchant basic land you control
        When this Aura enters, exile target creature an opponent controls until this Aura leaves the battlefield.
    """)
    private val rooftopFlyingAce = Cards.invent("Rooftop Flying Ace", "{2}{W}", "Creature — Bird Mutant", """
        Flying
        {3}{W}: Target creature without flying gains flying until end of turn. Activate only as a sorcery.
    """, "2/3")
    private val gutsyBadAppleSister = Cards.invent("Gutsy, Bad Apple Sister", "{4}{W}", "Legendary Creature — Mutant Ninja Turtle", """
        When Gutsy enters, create a 2/2 red Mutant creature token.
        Plainscycling {2} ({2}, Discard this card: Search your library for a Plains card, reveal it, put it into your hand, then shuffle.)
    """, "3/3")
    private val primevalHousepet = Cards.invent("Primeval Housepet", "{W}", "Creature — Dinosaur Ninja", """
        This creature can't be blocked by creatures with greater power.
        {1}{W}, {T}: Return another target creature you control to its owner's hand. Activate only during your turn.
    """, "1/2")
    private val manePrideLionessLeader = Cards.invent("Mane Pride, Lioness Leader", "{3}{W}{W}", "Legendary Creature — Cat Mutant Rebel", """
        When Mane Pride enters, create X 2/2 red Mutant creature tokens, where X is the number of nontoken creatures you control.
        Whenever Mane Pride attacks, put a +1/+1 counter on each creature you control.
    """, "2/4")
    private val hornedInvasionCommander = Cards.invent("Horned Invasion Commander", "{X}{X}{W}{W}", "Creature — Dinosaur Soldier", """
        Flying
        When this creature enters, create X 2/2 white Dinosaur Soldier creature tokens.
        Whenever this creature attacks, Dinosaurs you control other than this creature get +1/+1 and gain flying until end of turn.
    """, "2/2")
    private val uneasyTrucePact = Cards.invent("Uneasy Truce Pact", "{1}{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature can't attack or block.
        {5}, Sacrifice this Aura: Exile enchanted creature. You create a 1/1 black Ninja creature token. Activate only as a sorcery.
    """)
    private val wrenReporterOfTheStrange = Cards.invent("Wren, Reporter of the Strange", "{2}{U}", "Legendary Creature — Human Detective", "Whenever Wren deals combat damage to a player, draw that many cards, then discard a card.", "2/2")
    private val droneSwarmlings = Cards.invent("Drone Swarmlings", "{1}{U}", "Artifact Creature — Robot Insect", """
        Flying, vigilance
        When this creature dies, draw a card.
    """, "1/1")
    private val boltMachineWhisperer = Cards.invent("Bolt, Machine Whisperer", "{2}{U}", "Legendary Creature — Mutant Ninja Turtle", """
        Flying
        Whenever an artifact you control enters, put a +1/+1 counter on Bolt.
    """, "1/2")
    private val runawayDroid = Cards.invent("Runaway Droid", "{U}", "Artifact Creature — Robot Scientist", """
        This creature can't be blocked if an artifact entered the battlefield under your control this turn.
        {U}, Sacrifice this creature: Counter target spell that targets an artifact or creature you control.
    """, "1/1")
    private val cerebrusMasterMind = Cards.invent("Cerebrus, Master Mind", "{6}{U}{U}", "Legendary Artifact Creature — Utrom Warrior", """
        Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
        When Cerebrus enters, if you have fewer than four cards in hand, draw cards equal to the difference.
        Cerebrus gets +1/+0 for each other artifact you control.
    """, "1/4")
    private val chromeshell = Cards.invent("Chromeshell", "{4}{U}", "Legendary Artifact Creature — Robot Turtle", """
        When Chromeshell enters, return up to one other target artifact or creature to its owner's hand.
        {R}, Sacrifice another artifact: Put a +1/+1 counter on Chromeshell. He gains menace and haste until end of turn.
    """, "4/4")
    private val consciousnessUpload = Cards.invent("Consciousness Upload", "{2}{U}", "Instant", """
        Until end of turn, target artifact or creature becomes an artifact creature with base power and toughness 4/5.
        Draw a card.
    """)
    private val radicalGeckoSkateLegend = Cards.invent("Radical Gecko, Skate Legend", "{1}{U}{U}", "Legendary Creature — Lizard Mutant", """
        {1}, Discard a card: Until end of turn, Radical Gecko becomes the color of your choice and gains hexproof from that color.
        Whenever Radical Gecko deals combat damage to a player, draw a card for each color among permanents you control.
    """, "2/3")
    private val chroniaTemporalApprentice = Cards.invent("Chronia, Temporal Apprentice", "{3}{U}{U}", "Legendary Creature — Human Wizard", """
        Flash
        When Chronia enters, return each other nonland permanent that entered this turn to its owner's hand.
    """, "4/3")
    private val devolutionRay = Cards.invent("Devolution Ray", "{2}{U}", "Enchantment — Aura", """
        Flash
        Enchant creature
        Enchanted creature is a Turtle with base power and toughness 0/1. It can't attack and loses all abilities. (It also loses all other creature types.)
    """)
    private val gutterSurveillanceCam = Cards.invent("Gutter Surveillance Cam", "{U}", "Artifact", """
        Flash
        When this artifact enters or leaves the battlefield, you may tap or untap target creature.
        {3}{U}, Sacrifice this artifact: Draw two cards.
    """)
    private val buzzwingMadFlyentist = Cards.invent("Buzzwing, Mad Flyentist", "{4}{U}", "Legendary Creature — Insect Mutant Scientist", """
        Flying
        When Buzzwing enters, draw a card, then discard a card.
        Islandcycling {2} ({2}, Discard this card: Search your library for an Island card, reveal it, put it into your hand, then shuffle.)
    """, "3/4")
    private val brainpodScientists = Cards.invent("Brainpod Scientists", "{2}{U}", "Artifact Creature — Utrom Robot Scientist", "When this creature enters, tap up to one target creature and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)", "2/2")
    private val abyssalMawFutureShark = Cards.invent("Abyssal Maw, Future Shark", "{6}{B}{B}", "Legendary Creature — Shark Horror Mutant", """
        Flash
        When Abyssal Maw enters, destroy up to three target creatures.
    """, "9/6")
    private val nightmareBeavers = Cards.invent("Nightmare Beavers", "{B}", "Creature — Beaver Nightmare", """
        Flying
        When this creature enters, each opponent loses 1 life and you gain 1 life. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)
    """, "1/1")
    private val madameVoidPowerBroker = Cards.invent("Madame Void, Power Broker", "{2}{B}", "Legendary Creature — Demon Advisor", """
        Deathtouch
        Whenever another creature you control enters, you may pay life equal to its power. If you do, put that many +1/+1 counters on it.
    """, "1/3")
    private val hardLessons101 = Cards.invent("Hard Lessons 101", "{1}{B}", "Instant", "Until end of turn, target creature gains deathtouch and \"When this creature dies, return it to the battlefield tapped under its owner's control.\"")
    private val protozoanColony = Cards.invent("Protozoan Colony", "{1}{B}", "Creature — Zombie Worm", """
        When this creature enters, mill three cards. (Put the top three cards of your library into your graveyard.)
        When this creature dies, you may exile it. When you do, put target creature card from your graveyard on top of your library.
    """, "2/2")
    private val vantariTimesExile = Cards.invent("Vantari, Time's Exile", "{3}{B}{B}", "Legendary Creature — Demon Wizard", """
        Trample
        At the beginning of combat on your turn, put a +1/+1 counter on Vantari. Then you draw X cards and lose X life, where X is the number of counters on Vantari.
    """, "4/4")
    private val warlordsVengeance = Cards.invent("Warlord's Vengeance", "{2}{B}", "Sorcery", """
        Choose one —
        • Target player discards two cards.
        • Target player draws two cards and loses 2 life.
    """)
    private val southerlyGaleAvatar = Cards.invent("Southerly Gale Avatar", "{3}{B}", "Creature — Snake Spirit Avatar", """
        Deathtouch
        Whenever another creature you control dies, you gain life equal to its toughness.
        Whenever you gain life, each opponent loses 1 life.
    """, "3/4")
    private val ultraBladefist = Cards.invent("Ultra Bladefist", "{1}{B}", "Legendary Creature — Mutant Ninja Human", """
        Menace
        Whenever another permanent leaves the battlefield, put a +1/+1 counter on Ultra Bladefist.
    """, "1/1")
    private val sewerTunnelRats = Cards.invent("Sewer Tunnel Rats", "{1}{B}", "Creature — Rat", "{4}{B}: Return this card from your graveyard to the battlefield tapped.", "2/2")
    private val airwaveHijacking = Cards.invent("Airwave Hijacking", "{2}{R}{R}{R}", "Sorcery", "Gain control of all artifacts your opponents control until end of turn. Untap them. They gain haste until end of turn.")
    private val mackHardyJuryRigJusticiar = Cards.invent("Mack Hardy, Jury-Rig Justiciar", "{1}{R}", "Legendary Creature — Human Berserker", """
        Haste
        When Mack Hardy enters, look at the top four cards of your library. You may reveal an artifact card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
    """, "2/1")
    private val mackHardyStreetVigilante = Cards.invent("Mack Hardy, Street Vigilante", "{1}{R}{R}", "Legendary Creature — Human Berserker", "When Mack Hardy enters, draw three cards. At the beginning of your next upkeep, discard three cards at random.", "4/3")
    private val generalGraniteHeartOfStone = Cards.invent("General Granite, Heart of Stone", "{3}{R}{R}", "Legendary Artifact Creature — Elemental Soldier", """
        Trample
        When General Granite enters, you may sacrifice another artifact. When you do, General Granite deals 4 damage to target creature.
    """, "4/3")
    private val manholeCoverToss = Cards.invent("Manhole Cover Toss", "{1}{R}", "Instant", "Manhole Cover Toss deals 3 damage to target creature. You may put a card from your hand on the bottom of your library. If you do, draw a card.")
    private val roboRodentAttack = Cards.invent("Robo-Rodent Attack!", "{1}{R}", "Instant", """
        Choose one —
        • Create a 1/1 colorless Robot artifact creature token.
        • Target creature gets +3/+0 and gains first strike until end of turn.
    """)
    private val roboRodentFoundry = Cards.invent("Robo-Rodent Foundry", "{1}{R}", "Artifact", """
        When this artifact enters or leaves the battlefield, create a 1/1 colorless Robot artifact creature token.
        {4}{R}, Sacrifice this artifact: It deals 3 damage to target creature.
    """)
    private val blackOpsBioAssets = Cards.invent("Black Ops Bio-Assets", "{2}{R}", "Creature — Mutant Mercenary", """
        During your turn, this creature has first strike.
        Whenever this creature attacks, you may discard a card. If you do, draw a card.
    """, "3/1")
    private val violetSerpentPunks = Cards.invent("Violet Serpent Punks", "{1}{R}", "Creature — Human Rogue", "{T}: Add {R}. Spend this mana only to cast an artifact spell or to activate an ability.", "2/2")
    private val voraciousRobots = Cards.invent("Voracious Robots", "{1}{R}", "Artifact Creature — Robot", """
        Whenever you cast an artifact spell, create a 1/1 colorless Robot artifact creature token.
        {R}, {T}: Creature tokens you control gain haste until end of turn.
    """, "2/1")
    private val boulderSoldiers = Cards.invent("Boulder Soldiers", "{3}{R}", "Artifact Creature — Elemental Soldier", "When this creature enters, destroy up to one target noncreature artifact.", "4/3")
    private val grogHornedCastaway = Cards.invent("Grog, Horned Castaway", "{4}{R}", "Legendary Creature — Dinosaur Soldier", """
        Reach, trample
        When Grog enters, target creature can't block this turn.
        Mountaincycling {2} ({2}, Discard this card: Search your library for a Mountain card, reveal it, put it into your hand, then shuffle.)
    """, "5/4")
    private val radicalDiscovery = Cards.invent("Radical Discovery!", "{G}", "Sorcery", "Look at the top four cards of your library. You may reveal a Mutant, Ninja, Turtle, or land card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.")
    private val toadButler = Cards.invent("Toad Butler", "{1}{G}", "Creature — Frog Spirit", """
        Deathtouch
        {T}: Add one mana of any color.
        {2}: This creature gains reach until end of turn.
    """, "1/1")
    private val mudskipperBurrow = Cards.invent("Mudskipper & Burrow", "{4}{G}{G}", "Legendary Creature — Ox Mole Mutant", """
        Trample
        Whenever you tap a land for mana, add {G}.
    """, "8/8")
    private val hidebackSwampStalker = Cards.invent("Hideback, Swamp Stalker", "{2}{G}{G}", "Legendary Creature — Crocodile Mutant Rogue", """
        Trample
        Hideback enters with a hexproof counter on her.
        Whenever Hideback deals combat damage to a player, you may remove a counter from her. When you do, destroy target artifact or enchantment that player controls.
    """, "5/4")
    private val monaVerdeScienceGeek = Cards.invent("Mona Verde, Science Geek", "{2}{G}", "Legendary Creature — Lizard Mutant", """
        Reach
        {T}: Add X mana of any one color, where X is Mona Verde's power.
    """, "1/3")
    private val scruffyRaptor = Cards.invent("Scruffy Raptor", "{4}{G}", "Creature — Dinosaur", "When this creature enters, return up to one target creature or Food card from your graveyard to your hand.", "4/3")
    private val hornbackCrashCourser = Cards.invent("Hornback, Crash Courser", "{4}{G}{G}", "Legendary Creature — Rhino Mutant", """
        Hornback can't be blocked by more than one creature.
        Boars you control can't be blocked by more than one creature.
        Forestcycling {2} ({2}, Discard this card: Search your library for a Forest card, reveal it, put it into your hand, then shuffle.)
    """, "7/7")
    private val hiddenInTheShell = Cards.invent("Hidden in the Shell", "{1}{G}", "Instant", """
        This spell costs {1} less to cast if you control a Turtle.
        Put a +1/+1 counter on target creature you control. It gains trample, hexproof, and indestructible until end of turn.
    """)
    private val interdimensionalBovine = Cards.invent("Interdimensional Bovine", "{2}{G}", "Creature — Ox Avatar", """
        Flying
        {T}: Add two mana of any one color.
    """, "0/4")
    private val shellPower = Cards.invent("Shell Power!", "{2}{G}", "Enchantment", """
        Flash
        Turtles you control get +2/+2.
    """)
    private val froggaTornBetweenWorlds = Cards.invent("Frogga, Torn Between Worlds", "{4}{G}", "Legendary Creature — Mutant Frog Turtle", """
        Whenever Frogga is dealt damage, put that many +1/+1 counters on her. (She must survive the damage to get the counters.)
        Whenever a creature you control with a counter on it deals combat damage to a player, you may pay {U}. If you do, draw a card.
    """, "5/5")
    private val professorSprocketeer = Cards.invent("Professor Sprocketeer", "{3}{U}{R}", "Legendary Creature — Human Scientist", """
        When Professor Sprocketeer enters, create a 1/1 colorless Robot artifact creature token.
        At the beginning of combat on your turn, target artifact creature you control gets +3/+0 and gains first strike and vigilance until end of turn.
    """, "3/3")
    private val geniusUnbound = Cards.invent("Genius Unbound", "{4}{U}{R}", "Sorcery", """
        Choose one or both —
        • Genius Unbound deals 5 damage to target creature.
        • Choose target artifact card in your graveyard. Return it to the battlefield if it's an artifact creature card. Otherwise, return it to the battlefield and it's a 3/3 Robot artifact creature with flying.
    """)
    private val gearsBladesProblemSolvers = Cards.invent("Gears & Blades, Problem Solvers", "{3}{W/U}{W/U}", "Legendary Creature — Mutant Ninja Turtle", """
        Vigilance
        At the beginning of your end step, exile up to one target artifact you control and up to one target creature you control. Then return them to the battlefield under their owners' control.
    """, "4/6")
    private val gearsFistsHardScience = Cards.invent("Gears & Fists, Hard Science", "{1}{U/R}{U/R}", "Legendary Creature — Mutant Ninja Turtle", """
        Menace
        Whenever Gears & Fists attack, the next noncreature spell you cast this turn has affinity for artifacts. (It costs {1} less to cast for each artifact you control.)
    """, "2/4")
    private val clanEliteGuard = Cards.invent("Clan Elite Guard", "{2}{W/B}", "Creature — Human Ninja", "Whenever this creature attacks, another target creature you control gets +1/+0 and gains indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)", "2/4")
    private val goShinobiGo = Cards.invent("Go Shinobi Go", "{R}{W}", "Sorcery", """
        Choose one or both —
        • Exile target creature you control, then return it to the battlefield under its owner's control.
        • Go Shinobi Go deals damage equal to the greatest power among creatures you control to target creature an opponent controls.
    """)
    private val lessonsFromTheSwamp = Cards.invent("Lessons from the Swamp", "{2}{G}{U}", "Sorcery", "Draw three cards. You may put a land card from your hand onto the battlefield tapped.")
    private val prankGearsPartyPlanners = Cards.invent("Prank & Gears, Party Planners", "{2}{G/U}{G/U}", "Legendary Creature — Mutant Ninja Turtle", """
        Ward {2}
        You may look at the top card of your library any time.
        You may play lands and cast Mutant, Ninja, or Turtle spells from the top of your library. If you cast a creature spell this way, that creature enters with an additional +1/+1 counter on it.
    """, "3/3")
    private val prankBladesChaosOrder = Cards.invent("Prank & Blades, Chaos & Order", "{G/W}{G/W}", "Legendary Creature — Mutant Ninja Turtle", "Whenever you put a counter on a creature you control, draw a card. This ability triggers only once each turn.", "2/2")
    private val roboRodentMarkIV = Cards.invent("Robo-Rodent Mark IV", "{1}{U/R}", "Artifact Creature — Robot", "This creature can't attack unless you control another artifact.", "2/3")
    private val maskedNobody = Cards.invent("Masked Nobody", "{1}{U/R}{U/R}", "Artifact Creature — Human Hero", "When this creature enters, return up to one other target artifact you control to its owner's hand. Scry 1. (Look at the top card of your library. You may put that card on the bottom.)", "3/2")
    private val fistsPrankTroublemakers = Cards.invent("Fists & Prank, Troublemakers", "{5}{R/G}{R/G}", "Legendary Creature — Mutant Ninja Turtle", """
        Trample, haste
        Whenever Fists & Prank attack, reveal cards from the top of your library until you reveal a creature card. Put that card onto the battlefield tapped and attacking and the rest on the bottom of your library in a random order.
    """, "7/7")
    private val oldWhiskerRadicalRat = Cards.invent("Old Whisker, Radical Rat", "{1}{W/B}{W/B}", "Legendary Creature — Mutant Ninja Rat", """
        If a triggered ability of a Ninja creature you control triggers, that ability triggers an additional time.
        {1}{U}: Target Ninja can't be blocked this turn.
    """, "2/4")
    private val gleamingDomeSentinel = Cards.invent("Gleaming Dome Sentinel", "{2}", "Artifact Creature — Robot Ninja", """
        Other artifact creatures you control get +1/+0.
        {5}: Create a token that's a copy of another target artifact you control. That token gains haste. Sacrifice it at the beginning of the next end step.
    """, "1/3")
    private val henchbotSquad = Cards.invent("Henchbot Squad", "{4}", "Artifact Creature — Robot", "When this creature enters, exile target tapped creature an opponent controls until this creature leaves the battlefield.", "2/3")
    private val overlordBrainWarlord = Cards.invent("Overlord Brain, Warlord", "{9}", "Legendary Artifact Creature — Utrom Robot", """
        Flying, trample, indestructible, haste
        Other artifact creatures you control have flying, trample, indestructible, and haste.
    """, "9/9")
    private val rollingFortress = Cards.invent("Rolling Fortress", "{2}", "Artifact Creature — Construct", """
        Reach, trample
        This creature can't attack or block unless its power is 6 or greater.
        {T}, Sacrifice another artifact: Draw a card. Put a +1/+1 counter on this creature.
    """, "3/3")
    private val shellBlimp = Cards.invent("Shell Blimp", "{5}", "Artifact — Vehicle", """
        Flying
        When this Vehicle enters, create a 2/2 red Mutant creature token.
        Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
    """, "3/4")
    private val stormMachine = Cards.invent("Storm Machine", "{3}", "Artifact", """
        Landfall — Whenever a land you control enters, put a charge counter on this artifact.
        {T}: Add one mana of any color.
        {T}, Remove two charge counters from this artifact: Add {C}{C}.
        {T}, Remove three charge counters from this artifact: It deals 3 damage to any target.
    """)
    private val portalDimension = Cards.invent("Portal Dimension", null, "Land", """
        This land enters tapped.
        When this land enters, you gain 1 life.
        {T}: Add {R} or {W}.
    """)
    private val drainageTunnel = Cards.invent("Drainage Tunnel", null, "Land", """
        {T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
        {T}, Sacrifice this land: Target creature with power 2 or less can't be blocked this turn.
    """)
    private val farmhouseRetreat = Cards.invent("Farmhouse Retreat", null, "Land", """
        {T}: Add {C}.
        {1}, {T}: Exile target creature you own.
        {2}, {T}, Sacrifice this land: Return a creature card exiled with this land to the battlefield under your control. Return each other card exiled with this land to its owner's hand.
    """)
    private val shellLair = Cards.invent("Shell Lair", null, "Land", """
        {T}: Add {C}.
        {T}: Add one mana of any color. Spend this mana only to cast a Ninja or Turtle spell.
        {3}, {T}: Target Ninja or Turtle can't be blocked this turn.
    """)

    private val ninja = Cards.invent("Sewer Ninja", "{1}{B}", "Creature — Human Ninja", "", "2/2")
    private val turtle = Cards.invent("Sewer Shellback", "{1}{G}", "Creature — Mutant Ninja Turtle", "", "2/2")
    private val counterspell = Cards.invent("Sewer Refusal", "{U}{U}", "Instant", "Counter target spell.")
    private val bolt = Cards.invent("Sewer Bolt", "{R}", "Instant", "Sewer Bolt deals 1 damage to any target.")
    private val growth = Cards.invent("Sewer Growth", "{G}", "Instant", "Put a +1/+1 counter on target creature.")
    private val lifeNinja = Cards.invent("Sewer Shadow", "{1}{B}", "Creature — Human Ninja", "When this creature enters, you gain 1 life.", "2/2")
    private val insight = Cards.invent("Sewer Insight", "{3}{U}", "Instant", "Draw a card.")
    private val drone = Cards.invent("Sewer Drone", "{2}", "Artifact Creature — Robot", "", "1/1")

    // ---- Chunk 07: Classes

    @Test
    fun snarkyAttitudeRummagesAndBurns() = scenario {
        alice.battlefield(snarkyAttitude, bear, "Mountain", "Mountain")
        alice.hand(ogre)
        alice.library(elf)
        alice.activates(snarkyAttitude, ability = 0)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        may(true)
        chooseIfOffered(ogre)
        resolveAll()
        expect {
            alice.hand has elf
            bob.life shouldBe 18
        }
    }

    @Test
    fun partyAnimalLifestyleFeedsEveryone() = scenario {
        alice.battlefield("Forest")
        alice.hand(partyAnimalLifestyle)
        alice.casts(partyAnimalLifestyle)
        resolveAll()
        expect {
            alice.battlefield has "Food Token"
            bob.battlefield has "Food Token"
        }
    }

    @Test
    fun partyAnimalLifestyleDrawsForTheirArtifacts() = scenario {
        alice.battlefield(partyAnimalLifestyle, "Forest", "Forest", "Mountain")
        alice.hand(shatter)
        alice.library(bear)
        bob.battlefield(idol)
        alice.activates(partyAnimalLifestyle, ability = 0, paying = listOf("Forest", "Forest"))
        resolveAll()
        alice.casts(shatter, target = idol)
        resolveAll()
        expect { alice.hand has bear }
    }

    // ---- Chunk 08: Food

    @Test
    fun scatterbrainedPigeonLeavesFood() = scenario {
        alice.battlefield(scatterbrainedPigeon, "Swamp")
        alice.hand(doom)
        alice.casts(doom, target = scatterbrainedPigeon)
        resolveAll()
        expect { alice.battlefield has "Food Token" }
    }

    @Test
    fun sardinePineapplePieDestroysAndFeeds() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Plains", "Plains")
        alice.hand(sardinePineapplePie)
        bob.battlefield(giant)
        alice.casts(sardinePineapplePie, paying = listOf("Swamp", "Swamp", "Swamp", "Swamp"))
        resolveAll()
        targetsIfAsked(giant)
        resolveAll()
        expect { bob.graveyard has giant }
        alice.activates(sardinePineapplePie)
        resolveAll()
        expect { alice.life shouldBe 23 }
    }

    @Test
    fun fieryPorridgePieBurnsBothWays() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(fieryPorridgePie)
        alice.casts(fieryPorridgePie)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect {
            bob.life shouldBe 16
            alice.life shouldBe 17
        }
    }

    @Test
    fun avocadoCocoaPieUntapsAndPumps() = scenario {
        alice.battlefield("Forest")
        alice.battlefield(bear, tapped = true)
        alice.hand(avocadoCocoaPie)
        alice.casts(avocadoCocoaPie)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.tapped shouldBe false
            bear.power shouldBe 4
        }
    }

    @Test
    fun kitchenSinkPieFindsABasic() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(kitchenSinkPie)
        alice.library(bear, "Island")
        alice.casts(kitchenSinkPie)
        resolveAll()
        chooseIfOffered("Island")
        resolveAll()
        expect { alice.hand has "Island" }
    }

    @Test
    fun everyCheesePieDrawsAndMakesMana() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(everyCheesePie)
        alice.library(bear)
        alice.casts(everyCheesePie, paying = listOf("Forest", "Forest"))
        resolveAll()
        expect { alice.hand has bear }
        alice.activates(everyCheesePie, ability = 0, color = Color.Blue)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Blue))
        expect { alice.pool.size shouldBe 1 }
    }

    @Test
    fun snackCourierFindsAFood() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(snackCourier)
        alice.library(bear, everyCheesePie)
        alice.casts(snackCourier)
        resolveAll()
        may(true)
        chooseIfOffered(everyCheesePie)
        resolveAll()
        expect { alice.hand has everyCheesePie }
    }

    @Test
    fun snackCourierMakesFoodWithoutOne() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(snackCourier)
        alice.library(bear)
        alice.casts(snackCourier)
        resolveAll()
        may(false)
        resolveAll()
        expect { alice.battlefield has "Food Token" }
    }

    @Test
    fun spoiledSnacksDestroysAndFeeds() = scenario {
        alice.battlefield("Swamp", "Forest", "Forest")
        alice.hand(spoiledSnacks)
        bob.battlefield(ogre)
        alice.casts(spoiledSnacks, target = ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.battlefield has "Food Token"
        }
    }

    @Test
    fun gelatoKittenEatsForCards() = scenario {
        alice.battlefield(gelatoKitten, bear, "Swamp", "Swamp")
        alice.library(ogre)
        alice.activates(gelatoKitten, choosing = listOf(bear))
        resolveAll()
        expect { alice.hand has ogre }
    }

    // ---- Chunk 09: Equipment

    @Test
    fun peerlessKatanaJumpsToNinjas() = scenario {
        alice.battlefield(peerlessKatana, "Swamp", "Swamp")
        alice.hand(ninja)
        alice.casts(ninja)
        resolveAll()
        may(true)
        resolveAll()
        expect { ninja.power shouldBe 3 }
    }

    @Test
    fun peerlessKatanaUntapsAndHeals() = scenario {
        alice.battlefield(peerlessKatana, bear, "Plains", "Plains")
        alice.activates(peerlessKatana, target = bear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect {
            bear.tapped shouldBe false
            alice.life shouldBe 22
        }
    }

    @Test
    fun tailoredBoStaffBouncesOnEntering() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(tailoredBoStaff)
        bob.battlefield(ogre)
        alice.casts(tailoredBoStaff)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { bob.hand has ogre }
    }

    @Test
    fun hardFoughtSaiGivesDoubleStrike() = scenario {
        alice.battlefield(hardFoughtSai, bear, "Mountain", "Mountain")
        alice.activates(hardFoughtSai, target = bear)
        resolveAll()
        expect { bear hasKeyword Keyword.DoubleStrike }
    }

    @Test
    fun scrapheapArmoryCountsArtifactsAndCopies() = scenario {
        alice.battlefield(scrapheapArmory, idol, bear, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.activates(scrapheapArmory, ability = 1, target = bear, paying = listOf("Mountain"))
        resolveAll()
        expect { bear.power shouldBe 4 }
        alice.activates(scrapheapArmory, ability = 0)
        resolveAll()
        expect { alice.battlefield.names.count { it == scrapheapArmory } shouldBe 2 }
    }

    @Test
    fun neonNunchakuAttachesAndFights() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", ogre)
        alice.hand(neonNunchaku)
        bob.battlefield(bear)
        alice.casts(neonNunchaku)
        resolveAll()
        // the Ogre is the only creature to attach to (chosen without asking); then the reflexive fight's target
        pickTargets(bear)
        resolveAll()
        expect {
            ogre.power shouldBe 4
            bob.graveyard has bear
        }
    }

    @Test
    fun sewerSkateboardTapsAndHastes() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.battlefield(bear, sick = true)
        alice.hand(sewerSkateboard)
        bob.battlefield(ogre)
        alice.casts(sewerSkateboard, paying = listOf("Plains"))
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        alice.activates(sewerSkateboard, target = bear)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            bear hasKeyword Keyword.Haste
        }
    }

    // ---- Chunks 09–10: white

    @Test
    fun eveningNewsCrewChannels() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(eveningNewsCrew)
        alice.library(ogre)
        alice.activates(eveningNewsCrew)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            alice.hand has ogre
        }
    }

    @Test
    fun directorHalberdCountersTwo() = scenario {
        alice.battlefield(directorHalberdSuitAndShades, bear)
        advanceTo(TurnStep.BeginningOfCombat)
        pickTargets(bear, directorHalberdSuitAndShades)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            directorHalberdSuitAndShades.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun juniperScriesAndSlipsBigBlockers() = scenario {
        alice.battlefield(juniperKunoichiTrainee)
        bob.battlefield(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(juniperKunoichiTrainee)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to juniperKunoichiTrainee) }
    }

    @Test
    fun interdimensionalBanishmentExilesFromALand() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(interdimensionalBanishment)
        bob.battlefield(giant)
        alice.casts(interdimensionalBanishment, target = "Plains")
        resolveAll()
        targetsIfAsked(giant)
        resolveAll()
        expect { bob.exile has giant }
    }

    @Test
    fun rooftopFlyingAceLendsWings() = scenario {
        alice.battlefield(rooftopFlyingAce, bear, "Plains", "Plains", "Plains", "Plains")
        alice.activates(rooftopFlyingAce, target = bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Flying }
    }

    @Test
    fun gutsyBringsAMutant() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(gutsyBadAppleSister)
        alice.casts(gutsyBadAppleSister)
        resolveAll()
        expect { alice.battlefield has "Mutant Token" }
    }

    @Test
    fun primevalHousepetSlipsBigBlockersAndSaves() = scenario {
        alice.battlefield(primevalHousepet, bear, "Plains", "Plains")
        alice.activates(primevalHousepet, target = bear)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun manePrideRalliesMutants() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(manePrideLionessLeader)
        alice.casts(manePrideLionessLeader)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Mutant Token" } shouldBe 2 }
    }

    @Test
    fun hornedInvasionCommanderMakesDinosaurs() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(hornedInvasionCommander)
        alice.casts(hornedInvasionCommander, x = 2)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Dinosaur Soldier Token" } shouldBe 2 }
    }

    @Test
    fun uneasyTrucePactPacifiesThenExiles() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(uneasyTrucePact)
        bob.battlefield(giant)
        alice.casts(uneasyTrucePact, target = giant, paying = listOf("Plains", "Plains"))
        resolveAll()
        alice.activates(uneasyTrucePact)
        resolveAll()
        expect {
            bob.exile has giant
            alice.battlefield has "Ninja Token"
        }
    }

    // ---- Chunk 10: blue

    @Test
    fun wrenDrawsByDamage() = scenario {
        alice.battlefield(wrenReporterOfTheStrange)
        alice.hand(ogre)
        alice.library(bear, elf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wrenReporterOfTheStrange)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        chooseIfOffered(ogre)
        resolveAll()
        expect {
            alice.hand has bear
            alice.hand has elf
        }
    }

    @Test
    fun droneSwarmlingsDrawOnDeath() = scenario {
        alice.battlefield(droneSwarmlings, "Swamp")
        alice.hand(doom)
        alice.library(bear)
        alice.casts(doom, target = droneSwarmlings)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun boltGrowsOnArtifacts() = scenario {
        alice.battlefield(boltMachineWhisperer, "Plains", "Plains")
        alice.hand(idol)
        alice.casts(idol)
        resolveAll()
        expect { boltMachineWhisperer.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun runawayDroidCountersAHostileSpell() = scenario {
        alice.battlefield(runawayDroid, bear, "Island")
        bob.battlefield("Swamp")
        bob.hand(doom)
        alice.passes()
        bob.casts(doom, target = bear)
        bob.passes()
        alice.activates(runawayDroid, target = doom)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    @Test
    fun cerebrusDrawsUpToFour() = scenario {
        alice.battlefield(idol, idol, idol, "Island", "Island", "Island", "Island", "Island")
        alice.hand(cerebrusMasterMind)
        alice.library(bear, bear, bear, bear)
        alice.casts(cerebrusMasterMind)
        resolveAll()
        expect {
            alice.hand.size shouldBe 4
            cerebrusMasterMind.power shouldBe 4
        }
    }

    @Test
    fun chromeshellBouncesAndEats() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Mountain", idol)
        alice.hand(chromeshell)
        bob.battlefield(ogre)
        alice.casts(chromeshell, paying = listOf("Island", "Island", "Island", "Island", "Island"))
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        alice.activates(chromeshell, choosing = listOf(idol))
        resolveAll()
        expect {
            bob.hand has ogre
            chromeshell.counters(p1p1) shouldBe 1
            chromeshell hasKeyword Keyword.Haste
        }
    }

    @Test
    fun consciousnessUploadAnimatesAndDraws() = scenario {
        alice.battlefield("Island", "Island", "Island", idol)
        alice.hand(consciousnessUpload)
        alice.library(bear)
        alice.casts(consciousnessUpload, target = idol)
        resolveAll()
        expect {
            idol.power shouldBe 4
            alice.hand has bear
        }
    }

    @Test
    fun radicalGeckoDrawsByColors() = scenario {
        alice.battlefield(radicalGeckoSkateLegend, ogre)
        alice.library(bear, bear, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(radicalGeckoSkateLegend)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun chroniaRewindsTheTurn() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(chroniaTemporalApprentice)
        bob.battlefield("Forest", "Forest")
        bob.hand(bear)
        turn(active = bob)
        bob.casts(bear)
        resolveAll()
        bob.passes()
        alice.casts(chroniaTemporalApprentice)
        resolveAll()
        expect { bob.hand has bear }
    }

    @Test
    fun devolutionRayMakesATurtle() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(devolutionRay)
        bob.battlefield(sphinx)
        alice.casts(devolutionRay, target = sphinx)
        resolveAll()
        expect {
            sphinx.power shouldBe 0
            sphinx lacksKeyword Keyword.Flying
            sphinx.subtypes.map { it.name } shouldBe listOf("Turtle")
        }
    }

    @Test
    fun gutterSurveillanceCamTapsAndDraws() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(gutterSurveillanceCam)
        alice.library(bear, bear)
        bob.battlefield(ogre)
        alice.casts(gutterSurveillanceCam, paying = listOf("Island"))
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        if (pending is Decision.May) may(true)
        if (pending is Decision.ChooseOption) option("tap")
        resolveAll()
        expect { ogre.tapped shouldBe true }
        alice.activates(gutterSurveillanceCam)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun buzzwingLoots() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(buzzwingMadFlyentist, ogre)
        alice.library(bear)
        alice.casts(buzzwingMadFlyentist)
        resolveAll()
        chooseIfOffered(ogre)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun brainpodScientistsStun() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(brainpodScientists)
        bob.battlefield(ogre)
        alice.casts(brainpodScientists)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { ogre.counters(CounterKind.Stun) shouldBe 1 }
    }

    // ---- Chunk 11: black

    @Test
    fun abyssalMawDestroysThree() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(abyssalMawFutureShark)
        bob.battlefield(bear, ogre, giant)
        alice.casts(abyssalMawFutureShark)
        resolveAll()
        pickTargets(bear, ogre, giant)
        resolveAll()
        expect { bob.graveyard.size shouldBe 3 }
    }

    @Test
    fun nightmareBeaversDrainAndScry() = scenario {
        alice.battlefield("Swamp")
        alice.hand(nightmareBeavers)
        alice.library(bear)
        alice.casts(nightmareBeavers)
        resolveAll()
        chooseNone()
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    @Test
    fun madameVoidSellsCounters() = scenario {
        alice.battlefield(madameVoidPowerBroker, "Mountain", "Mountain", "Mountain")
        alice.hand(ogre)
        alice.casts(ogre)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            ogre.counters(p1p1) shouldBe 3
            alice.life shouldBe 17
        }
    }

    @Test
    fun hardLessons101BringsItBack() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", bear)
        alice.hand(hardLessons101, doom)
        alice.casts(hardLessons101, target = bear, paying = listOf("Swamp", "Swamp"))
        resolveAll()
        alice.casts(doom, target = bear)
        resolveAll()
        expect {
            alice.battlefield has bear
            bear.tapped shouldBe true
        }
    }

    @Test
    fun protozoanColonyMillsAndStacks() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(protozoanColony, doom)
        alice.library(giant, elf, elf, bear)
        alice.casts(protozoanColony, paying = listOf("Swamp", "Swamp"))
        resolveAll()
        expect { alice.graveyard has giant }
        alice.casts(doom, target = protozoanColony)
        resolveAll()
        may(true)
        resolveAll()
        targetsIfAsked(giant)
        resolveAll()
        expect { alice.library.names.first() shouldBe giant }
    }

    @Test
    fun vantariGrowsAndDraws() = scenario {
        alice.battlefield(vantariTimesExile)
        alice.library(bear, bear)
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        expect {
            vantariTimesExile.counters(p1p1) shouldBe 1
            alice.hand.size shouldBe 1
            alice.life shouldBe 19
        }
    }

    @Test
    fun warlordsVengeanceDrawsTwo() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(warlordsVengeance)
        alice.library(bear, bear)
        alice.casts(warlordsVengeance, modes = listOf(1), target = "alice")
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.life shouldBe 18
        }
    }

    @Test
    fun southerlyGaleAvatarHealsAndDrains() = scenario {
        alice.battlefield(southerlyGaleAvatar, ogre, "Swamp")
        alice.hand(doom)
        alice.casts(doom, target = ogre)
        resolveAll()
        expect {
            alice.life shouldBe 23
            bob.life shouldBe 19
        }
    }

    @Test
    fun ultraBladefistGrowsOnDepartures() = scenario {
        alice.battlefield(ultraBladefist, "Swamp")
        alice.hand(doom)
        bob.battlefield(bear)
        alice.casts(doom, target = bear)
        resolveAll()
        expect { ultraBladefist.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun sewerTunnelRatsReturn() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(sewerTunnelRats)
        alice.activates(sewerTunnelRats)
        resolveAll()
        expect { sewerTunnelRats.tapped shouldBe true }
    }

    // ---- Chunk 12: red

    @Test
    fun airwaveHijackingTakesTheirArtifacts() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(airwaveHijacking)
        bob.battlefield(drone)
        alice.casts(airwaveHijacking)
        resolveAll()
        expect {
            drone.controller shouldBe alice.id
            drone hasKeyword Keyword.Haste
        }
    }

    @Test
    fun mackHardyJuryRigFindsAnArtifact() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(mackHardyJuryRigJusticiar)
        alice.library(bear, idol)
        alice.casts(mackHardyJuryRigJusticiar)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(idol)
        resolveAll()
        expect { alice.hand has idol }
    }

    @Test
    fun mackHardyStreetDrawsThreeThenPays() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(mackHardyStreetVigilante)
        alice.library(bear, bear, bear, elf, elf)
        bob.library(elf, elf)
        alice.casts(mackHardyStreetVigilante)
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect { alice.graveyard.size shouldBe 3 }
    }

    @Test
    fun generalGraniteSmashesForAnArtifact() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", idol)
        alice.hand(generalGraniteHeartOfStone)
        bob.battlefield(ogre)
        alice.casts(generalGraniteHeartOfStone)
        resolveAll()
        may(true)
        chooseIfOffered(idol)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun manholeCoverTossBurnsAndCycles() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(manholeCoverToss, giant)
        alice.library(bear)
        bob.battlefield(ogre)
        alice.casts(manholeCoverToss, target = ogre)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(giant)
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.hand has bear
        }
    }

    @Test
    fun roboRodentAttackMakesARobot() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(roboRodentAttack)
        alice.casts(roboRodentAttack, modes = listOf(0))
        resolveAll()
        expect { alice.battlefield has "Robot Token" }
    }

    @Test
    fun roboRodentFoundryRobotsComingAndGoing() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(roboRodentFoundry)
        bob.battlefield(ogre)
        alice.casts(roboRodentFoundry, paying = listOf("Mountain", "Mountain"))
        resolveAll()
        alice.activates(roboRodentFoundry, target = ogre)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Robot Token" } shouldBe 2
            bob.graveyard has ogre
        }
    }

    @Test
    fun blackOpsBioAssetsFirstStrikeAndRummage() = scenario {
        alice.battlefield(blackOpsBioAssets)
        alice.hand(ogre)
        alice.library(bear)
        expect { blackOpsBioAssets hasKeyword Keyword.FirstStrike }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(blackOpsBioAssets)
        resolveAll()
        may(true)
        chooseIfOffered(ogre)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun violetSerpentPunksPayForArtifacts() = scenario {
        alice.battlefield(violetSerpentPunks, "Mountain")
        alice.hand(idol)
        alice.casts(idol, paying = listOf(violetSerpentPunks, "Mountain"))
        resolveAll()
        expect { alice.battlefield has idol }
    }

    @Test
    fun voraciousRobotsBuildRobots() = scenario {
        alice.battlefield(voraciousRobots, "Mountain", "Mountain", "Mountain")
        alice.hand(idol)
        alice.casts(idol, paying = listOf("Mountain", "Mountain"))
        resolveAll()
        alice.activates(voraciousRobots)
        resolveAll()
        expect { "Robot Token" hasKeyword Keyword.Haste }
    }

    @Test
    fun boulderSoldiersShatter() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(boulderSoldiers)
        bob.battlefield(idol)
        alice.casts(boulderSoldiers)
        resolveAll()
        targetsIfAsked(idol)
        resolveAll()
        expect { bob.graveyard has idol }
    }

    @Test
    fun grogGroundsABlocker() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(grogHornedCastaway)
        bob.battlefield(ogre)
        alice.casts(grogHornedCastaway)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { alice.battlefield has grogHornedCastaway }
    }

    // ---- Chunk 13: green

    @Test
    fun radicalDiscoveryFindsATurtle() = scenario {
        alice.battlefield("Forest")
        alice.hand(radicalDiscovery)
        alice.library(bear, turtle, ogre)
        alice.casts(radicalDiscovery)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(turtle)
        resolveAll()
        expect { alice.hand has turtle }
    }

    @Test
    fun toadButlerReaches() = scenario {
        alice.battlefield(toadButler, "Forest", "Forest")
        alice.activates(toadButler, ability = 1)
        resolveAll()
        expect { toadButler hasKeyword Keyword.Reach }
    }

    @Test
    fun mudskipperDoublesLandMana() = scenario {
        alice.battlefield(mudskipperBurrow, "Forest")
        alice.activates("Forest")
        expect { alice.pool.size shouldBe 2 }
    }

    @Test
    fun hidebackShatterOnDamage() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(hidebackSwampStalker)
        bob.battlefield(idol)
        alice.casts(hidebackSwampStalker)
        resolveAll()
        expect { hidebackSwampStalker hasKeyword Keyword.Hexproof }
    }

    @Test
    fun monaVerdeTapsForPower() = scenario {
        alice.battlefield(monaVerdeScienceGeek, counters = listOf(p1p1 to 2))
        alice.activates(monaVerdeScienceGeek, color = Color.Green)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        expect { alice.pool.size shouldBe 3 }
    }

    @Test
    fun scruffyRaptorRegrows() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(scruffyRaptor)
        alice.graveyard(ogre)
        alice.casts(scruffyRaptor)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { alice.hand has ogre }
    }

    @Test
    fun hornbackCantBeDoubleBlocked() = scenario {
        alice.battlefield(hornbackCrashCourser)
        bob.battlefield(bear, ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hornbackCrashCourser)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(bear to hornbackCrashCourser, ogre to hornbackCrashCourser) }
    }

    @Test
    fun hiddenInTheShellCheapWithATurtle() = scenario {
        alice.battlefield(turtle, "Forest")
        alice.hand(hiddenInTheShell)
        alice.casts(hiddenInTheShell, target = turtle)
        resolveAll()
        expect {
            turtle.counters(p1p1) shouldBe 1
            turtle hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun interdimensionalBovineMakesTwo() = scenario {
        alice.battlefield(interdimensionalBovine)
        alice.activates(interdimensionalBovine, color = Color.Green)
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        expect { alice.pool.size shouldBe 2 }
    }

    @Test
    fun shellPowerPumpsTurtles() = scenario {
        alice.battlefield(shellPower, turtle, bear)
        expect {
            turtle.power shouldBe 4
            bear.power shouldBe 2
        }
    }

    @Test
    fun froggaGrowsFromDamage() = scenario {
        alice.battlefield(froggaTornBetweenWorlds)
        bob.battlefield("Mountain")
        bob.hand(shock)
        alice.passes()
        bob.casts(shock, target = froggaTornBetweenWorlds)
        resolveAll()
        expect { froggaTornBetweenWorlds.counters(p1p1) shouldBe 2 }
    }

    // ---- Chunk 14: multicolored, artifacts and lands

    @Test
    fun professorSprocketeerArmsRobots() = scenario {
        alice.battlefield("Island", "Island", "Island", "Mountain", "Mountain")
        alice.hand(professorSprocketeer)
        alice.library(bear, bear)
        bob.library(bear, bear)
        alice.casts(professorSprocketeer)
        resolveAll()
        advanceTo(TurnStep.BeginningOfCombat, turn = 3)
        targetsIfAsked("Robot Token")
        resolveAll()
        expect {
            "Robot Token".power shouldBe 4
            "Robot Token" hasKeyword Keyword.FirstStrike
        }
    }

    @Test
    fun geniusUnboundRaisesAnArtifactAsARobot() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Mountain", "Mountain")
        alice.hand(geniusUnbound)
        alice.graveyard(idol)
        alice.casts(geniusUnbound, modes = listOf(1), target = idol)
        resolveAll()
        expect {
            idol.power shouldBe 3
            idol hasKeyword Keyword.Flying
        }
    }

    @Test
    fun gearsBladesBlinkAtEndStep() = scenario {
        alice.battlefield(gearsBladesProblemSolvers, idol)
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        advanceTo(TurnStep.End)
        pickTargets(idol, bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 0 }
    }

    @Test
    fun clanEliteGuardShieldsAnAttacker() = scenario {
        alice.battlefield(clanEliteGuard, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(clanEliteGuard)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun goShinobiGoBlinksAndBites() = scenario {
        alice.battlefield("Mountain", "Plains", giant)
        alice.hand(goShinobiGo)
        bob.battlefield(ogre)
        alice.casts(goShinobiGo, modes = listOf(1), target = ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun lessonsFromTheSwampDrawsAndDrops() = scenario {
        alice.battlefield("Forest", "Forest", "Island", "Island")
        alice.hand(lessonsFromTheSwamp)
        alice.library("Island", bear, elf)
        alice.casts(lessonsFromTheSwamp)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered("Island")
        resolveAll()
        expect { alice.battlefield.names.count { it == "Island" } shouldBe 3 }
    }

    @Test
    fun prankGearsCastsFromTheTop() = scenario {
        alice.battlefield(prankGearsPartyPlanners, "Forest", "Forest")
        alice.library(turtle)
        alice.casts(turtle)
        resolveAll()
        expect { turtle.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun prankBladesDrawsOnCounters() = scenario {
        alice.battlefield(prankBladesChaosOrder, bear, "Forest")
        alice.hand(growth)
        alice.library(elf)
        alice.casts(growth, target = bear)
        resolveAll()
        expect { alice.hand has elf }
    }

    @Test
    fun roboRodentMarkIVNeedsAnotherArtifact() = scenario {
        alice.battlefield(roboRodentMarkIV)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(roboRodentMarkIV) }
    }

    @Test
    fun maskedNobodyBouncesAnArtifact() = scenario {
        alice.battlefield("Island", "Island", "Island", idol)
        alice.hand(maskedNobody)
        alice.library(bear)
        alice.casts(maskedNobody)
        resolveAll()
        targetsIfAsked(idol)
        resolveAll()
        chooseNone()
        resolveAll()
        expect { alice.hand has idol }
    }

    @Test
    fun fistsPrankBringAnAttacker() = scenario {
        alice.battlefield(fistsPrankTroublemakers)
        alice.library("Forest", ogre, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(fistsPrankTroublemakers)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            alice.battlefield has ogre
        }
    }

    @Test
    fun oldWhiskerRadicalDoublesNinjaTriggers() = scenario {
        alice.battlefield(oldWhiskerRadicalRat, "Swamp", "Swamp")
        alice.hand(lifeNinja)
        alice.casts(lifeNinja)
        resolveAll()
        // CR 603.2d: the Ninja's enters trigger triggers an additional time
        expect { alice.life shouldBe 22 }
    }

    @Test
    fun gleamingDomeSentinelPumpsAndCopies() = scenario {
        alice.battlefield(gleamingDomeSentinel, drone, "Plains", "Plains", "Plains", "Plains", "Plains")
        expect { drone.power shouldBe 2 }
        alice.activates(gleamingDomeSentinel, target = drone)
        resolveAll()
        expect { alice.battlefield.names.count { it == drone } shouldBe 2 }
    }

    @Test
    fun henchbotSquadExilesATappedCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(henchbotSquad)
        bob.battlefield(ogre, tapped = true)
        alice.casts(henchbotSquad)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { bob.exile has ogre }
    }

    @Test
    fun overlordBrainEmpowersArtifactCreatures() = scenario {
        alice.battlefield(overlordBrainWarlord, drone)
        expect {
            drone hasKeyword Keyword.Flying
            drone hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun rollingFortressNeedsSixPower() = scenario {
        alice.battlefield(rollingFortress, idol)
        alice.library(bear)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(rollingFortress) }
    }

    @Test
    fun rollingFortressEatsArtifacts() = scenario {
        alice.battlefield(rollingFortress, idol)
        alice.library(bear)
        alice.activates(rollingFortress, choosing = listOf(idol))
        resolveAll()
        expect {
            alice.hand has bear
            rollingFortress.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun shellBlimpBringsAMutant() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(shellBlimp)
        alice.casts(shellBlimp)
        resolveAll()
        expect { alice.battlefield has "Mutant Token" }
    }

    @Test
    fun stormMachineChargesAndZaps() = scenario {
        alice.battlefield(stormMachine, counters = listOf(CounterKind.Named("charge") to 2))
        alice.hand("Mountain")
        alice.plays("Mountain")
        resolveAll()
        expect { stormMachine.counters(CounterKind.Named("charge")) shouldBe 3 }
        alice.activates(stormMachine, ability = 2, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun portalDimensionGainsOnEntering() = scenario {
        alice.hand(portalDimension)
        alice.plays(portalDimension)
        resolveAll()
        expect {
            portalDimension.tapped shouldBe true
            alice.life shouldBe 21
        }
    }

    @Test
    fun drainageTunnelSneaksASmallCreature() = scenario {
        alice.battlefield(drainageTunnel, bear)
        bob.battlefield(ogre)
        alice.activates(drainageTunnel, ability = 1, target = bear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to bear) }
    }

    @Test
    fun farmhouseRetreatHidesAndReturns() = scenario {
        alice.battlefield(farmhouseRetreat, bear, "Plains", "Plains", "Plains")
        alice.activates(farmhouseRetreat, ability = 1, target = bear)
        resolveAll()
        expect { alice.exile has bear }
        change(Changes.tap(state, card(farmhouseRetreat), false))
        alice.activates(farmhouseRetreat, ability = 2)
        resolveAll()
        chooseIfOffered(bear)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    @Test
    fun shellLairSneaksATurtle() = scenario {
        alice.battlefield(shellLair, turtle, "Plains", "Plains", "Plains")
        bob.battlefield(ogre)
        alice.activates(shellLair, ability = 2, target = turtle)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(turtle)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to turtle) }
    }

    // CR 702.41a: affinity for artifacts
    @Test
    fun gearsFistsGiveTheNextSpellAffinity() = scenario {
        alice.battlefield(gearsFistsHardScience, idol, "Island", "Island", "Island", "Island")
        alice.hand(insight)
        alice.library(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(gearsFistsHardScience)
        resolveAll()
        alice.casts(insight)
        resolveAll()
        // {3}{U} less {1} for the one artifact: three Islands
        expect { alice.hand has bear }
        assertEquals(1, state.battlefield.count { id -> state.obj(id)?.let { o -> nameOf(o) == "Island" && !o.tapped } == true })
    }
}
