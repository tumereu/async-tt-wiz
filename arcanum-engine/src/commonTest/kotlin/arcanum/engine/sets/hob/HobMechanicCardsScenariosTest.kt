package arcanum.engine.sets.hob

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.Subtype
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.hob.Hob.bear
import arcanum.engine.sets.hob.Hob.doom
import arcanum.engine.sets.hob.Hob.dwarf
import arcanum.engine.sets.hob.Hob.elf
import arcanum.engine.sets.hob.Hob.giant
import arcanum.engine.sets.hob.Hob.goblin
import arcanum.engine.sets.hob.Hob.human
import arcanum.engine.sets.hob.Hob.idol
import arcanum.engine.sets.hob.Hob.ogre
import arcanum.engine.sets.hob.Hob.p1p1
import arcanum.engine.sets.hob.Hob.ponder
import arcanum.engine.sets.hob.Hob.shock
import arcanum.engine.sets.hob.Hob.sword
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The Hobbit's storied, recruit, amass, Saga, Adventure, Equipment and drawing/landfall cards in play (parser chunks
 * 01–07), each card's own ability on top of the mechanics [HobMechanicsScenariosTest] covers. Wide Mere Pest repeats
 * the "when this creature enters, recruit" of Fenmere Garrison. */
class HobMechanicCardsScenariosTest {
    private val darviLordOfTheCopperFells = Cards.invent("Darvi, Lord of the Copper Fells", "{1}{W}", "Legendary Creature — Dwarf Noble", """
        Vigilance
        Storied (If you control three or more artifacts, legendaries, and/or Sagas, you have an enduring story for the rest of the game.)
        As long as you have an enduring story, creatures can't attack you unless their controller pays {1} for each of those creatures.
    """, "2/2")
    private val braliTheTrailfinder = Cards.invent("Brali the Trailfinder", "{3}{W}", "Legendary Creature — Dwarf Scout", """
        Storied (If you control three or more artifacts, legendaries, and/or Sagas, you have an enduring story for the rest of the game.)
        As long as you have an enduring story, creatures you control get +1/+1.
        Whenever Brali or another nontoken Dwarf you control enters, create a 2/2 red Dwarf creature token.
    """, "2/2")
    private val borinSagaKeeper = Cards.invent("Borin, Saga-Keeper", "{3}{R}{R}", "Legendary Creature — Dwarf Bard", """
        Storied (If you control three or more artifacts, legendaries, and/or Sagas, you have an enduring story for the rest of the game.)
        Whenever Borin or another Dwarf you control enters, you may discard your hand. Draw X cards, where X is the number of cards discarded this way. If you have an enduring story, Borin deals X damage to each opponent.
    """, "4/4")
    private val ulfiTheBrave = Cards.invent("Ulfi the Brave", "{1}{R}", "Legendary Creature — Dwarf Warrior", """
        Storied (If you control three or more artifacts, legendaries, and/or Sagas, you have an enduring story for the rest of the game.)
        As long as you have an enduring story, Ulfi gets +1/+0 and has haste.
        {1}, {T}, Discard a card: Draw a card.
    """, "1/3")
    private val bofrinTunefulRider = Cards.invent("Bofrin, Tuneful Rider", "{4}{R/W}{R/W}", "Legendary Creature — Dwarf Bard", """
        Storied (If you control three or more artifacts, legendaries, and/or Sagas, you have an enduring story for the rest of the game.)
        Whenever Bofrin enters or attacks, put a +1/+1 counter on target creature.
        As long as you have an enduring story, if a triggered ability of a Dwarf you control triggers, that ability triggers an additional time.
    """, "4/5")
    private val tharnIronbuckler = Cards.invent("Tharn Ironbuckler", "{R}{W}", "Legendary Creature — Dwarf Noble", """
        Trample
        Storied (If you control three or more artifacts, legendaries, and/or Sagas, you have an enduring story for the rest of the game.)
        As long as you have an enduring story, artifacts and creatures you control have ward {1}.
    """, "3/2")
    private val fenmereGarrison = Cards.invent("Fenmere Garrison", "{4}{W}", "Creature — Human Soldier", """
        Fenmere Garrison's power is equal to the number of creatures you control.
        When this creature enters, recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)
    """, "*/5")
    private val marshTownLookout = Cards.invent("Marsh-town Lookout", "{W}", "Creature — Human Scout", "When this creature dies, recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)", "1/1")
    private val theDuchessOfVale = Cards.invent("The Duchess of Vale", "{1}{W}", "Legendary Creature — Human Noble", "Whenever an opponent casts their first noncreature spell each turn, you recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)", "2/1")
    private val grandPaintedBarge = Cards.invent("Grand Painted Barge", "{2}{U}", "Artifact — Vehicle", """
        Whenever you attack, recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)
        Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
    """, "4/4")
    private val blowTheClarions = Cards.invent("Blow the Clarions", "{1}{U}{U}", "Instant", "Counter target spell. If that spell's mana value was 2 or less, recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)")
    private val archersMuster = Cards.invent("Archer's Muster", "{2}{W}{U}", "Creature — Human Citizen", """
        You may cast this spell as though it had flash if you control a Human.
        Other creatures you control get +1/+1.
        Whenever this creature enters or attacks, recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)
    """, "2/3")
    private val downTheTwistedPath = Cards.invent("Down the Twisted Path", "{2}{B}", "Enchantment", """
        When this enchantment enters, return target creature card from your graveyard to your hand.
        Whenever a creature card leaves your graveyard, amass Goblins 1.
        {1}{B}: Goblins and Orcs you control gain menace until end of turn.
    """)
    private val musteringOfShadows = Cards.invent("Mustering of Shadows", "{3}{B}", "Sorcery", """
        Return up to one target creature card from your graveyard to your hand.
        Amass Goblins 3. (Put three +1/+1 counters on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)
    """)
    private val stormIntoTheDale = Cards.invent("Storm into the Dale", "{2}{B}", "Sorcery", """
        You draw a card and lose 1 life.
        Amass Goblins 2. (Put two +1/+1 counters on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)
    """)
    private val wilderfellRampager = Cards.invent("Wilderfell Rampager", "{2}{B}", "Creature — Wolf", """
        Whenever this creature attacks, you may sacrifice another creature. If you do, put a number of +1/+1 counters on this creature equal to the sacrificed creature's power.
        When this creature dies, amass Goblins X, where X is this creature's power. (Put X +1/+1 counters on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)
    """, "3/2")
    private val peskyDrumbanger = Cards.invent("Pesky Drumbanger", "{1}{R}", "Creature — Goblin Bard", "Whenever you cast a noncreature spell, amass Goblins 1. (Put a +1/+1 counter on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)", "2/2")
    private val foggyPeaksRaider = Cards.invent("Foggy Peaks Raider", "{4}{R}", "Creature — Goblin Soldier", "Whenever you attack, amass Goblins 2. (Put two +1/+1 counters on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)", "4/4")
    private val rumorsOfBattle = Cards.invent("Rumors of Battle", "{R}", "Sorcery", """
        Amass Goblins 1. If this spell was cast from a graveyard, amass Goblins 3 instead. (To amass Goblins X, put X +1/+1 counters on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)
        Flashback {3}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val skarnOfTheNorth = Cards.invent("Skarn of the North", "{3}{B}{R}", "Legendary Creature — Goblin Soldier", "When Skarn enters, you may sacrifice another creature. When you do, Skarn deals damage equal to that creature's power to another target creature. If excess damage was dealt this way, amass Goblins X, where X is that excess damage. (Put X +1/+1 counters on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)", "5/5")
    private val dreadfulGoblinDuo = Cards.invent("Dreadful Goblin Duo", "{2}{B/R}", "Creature — Goblin Soldier", "When this creature dies, amass Goblins 4. (Put four +1/+1 counters on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)", "1/1")
    private val goblinScaleHauberk = Cards.invent("Goblin Scale Hauberk", "{1}{B/R}", "Artifact — Equipment", """
        When this Equipment enters, amass Goblins 1, then attach this Equipment to the amassed Army. (To amass Goblins 1, put a +1/+1 counter on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)
        Equipped creature gets +1/+0 and has menace.
        Equip {4}
    """)
    private val skarnsWarband = Cards.invent("Skarn's Warband", "{B}{R}", "Creature — Goblin Soldier", """
        This creature has haste as long as you control another Goblin.
        {T}, Sacrifice another Goblin: Add {B}{R}.
    """, "2/2")
    private val theHillLordComesHome = Cards.invent("The Hill-Lord Comes Home", "{2}{W}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Recruit. (Draw a card, then discard a card. If you discarded a nonland card, create a 1/1 white Human Soldier creature token.)
        II — Return target creature card with mana value 3 or less from your graveyard to the battlefield.
        III — Put a +1/+1 counter on up to one target creature.
    """)
    private val pathsWindOnAndOn = Cards.invent("Paths Wind On and On", "{1}{W}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I — Search your library for up to two basic Plains cards, exile them, then shuffle. You gain 2 life.
        II, III — Put a card exiled with this Saga into its owner's hand.
        IV — Whenever you attack this turn, target creature you control gets +1/+1 until end of turn for each Plains you control.
    """)
    private val lazyLobCantCatchMe = Cards.invent("Lazy Lob Can't Catch Me", "{2}{U}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I — Target creature you control gains hexproof for as long as this Saga remains on the battlefield.
        II — Prevent all damage that would be dealt by up to one target creature for as long as this Saga remains on the battlefield.
        III, IV — Draw a card.
    """)
    private val bumpBumpBumpBump = Cards.invent("Bump-Bump-Bump-Bump", "{2}{U}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I, II, III, IV — Exile up to one target creature or land you control. If you do, return it to the battlefield under its owner's control at the beginning of the next end step.
    """)
    private val deepDeepToTheGoblinHalls = Cards.invent("Deep, Deep to the Goblin Halls", "{2}{B}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I — Target opponent reveals their hand. You choose a nonland card from it. That player discards that card.
        II — Amass Goblins 1. (Put a +1/+1 counter on an Army you control. It's also a Goblin. If you don't control an Army, create a 0/0 black Goblin Army creature token first.)
        III, IV — Target opponent loses 1 life and you gain 1 life.
    """)
    private val blazeBlazePineAndMoss = Cards.invent("Blaze, Blaze, Pine and Moss", "{3}{R}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I — This Saga deals 6 damage to target creature an opponent controls.
        II — Destroy target artifact an opponent controls.
        III, IV — Add {R}.
    """)
    private val theFrozenPeaksFar = Cards.invent("The Frozen Peaks Far", "{2}{R}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I, II, III, IV — Create a Treasure token. Then if you control four or more Treasures, sacrifice this Saga. If you do, create a 6/6 red Dragon creature token with flying. (A Treasure token is an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """)
    private val deepInTheDell = Cards.invent("Deep in the Dell", "{2}{G}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I — Search your library for a basic land card, reveal it, put it into your hand, then shuffle.
        II — This Saga gains "Landfall — Whenever a land you control enters, create a 1/1 green Elf creature token."
        III, IV — Elves you control get +1/+0 and gain vigilance until end of turn.
    """)
    private val bolfinSteadyWarden = Cards.inventFaces("Adventure",
        face("Bolfin, Steady Warden", "{W}", "Legendary Creature — Dwarf Scout", "Lifelink", "1/1", null, null),
        face("United Care", "{1}{W}", "Instant — Adventure", "Target artifact or creature you control gains hexproof and indestructible until end of turn. (Then exile this card. You may cast the creature later from exile.)", null, null, null),
    )
    private val aSurpriseFeast = Cards.inventFaces("Adventure",
        face("A Surprise Feast", "{2}{W}{W}", "Enchantment", """
            As this enchantment enters, choose a creature type.
            Creatures you control of the chosen type get +2/+2.
        """, null, null, null),
        face("Knock Knock", "{X}{2}{W}", "Sorcery — Adventure", "Create X 2/2 red Dwarf creature tokens. (Then exile this card. You may cast the enchantment later from exile.)", null, null, null),
    )
    private val silkwingMoths = Cards.inventFaces("Adventure",
        face("Silkwing Moths", "{2}{W}", "Creature — Insect", "Flying", "2/2", null, null),
        face("Stare in Awe", "{1}{W}", "Instant — Adventure", "Tap one or two target creatures. (Then exile this card. You may cast the creature later from exile.)", null, null, null),
    )
    private val tobboUnderhillSneak = Cards.inventFaces("Adventure",
        face("Tobbo Underhill, Sneak", "{2}{U}", "Legendary Creature — Halfling Rogue", "When Tobbo Underhill enters, draw a card.", "2/1", null, null),
        face("Peek Ahead", "{U}", "Sorcery — Adventure", "Scry 2. (Then exile this card. You may cast the creature later from exile.)", null, null, null),
    )
    private val mereTownBoatmen = Cards.inventFaces("Adventure",
        face("Mere-town Boatmen", "{4}{U}{U}", "Creature — Human Citizen", """
            Vigilance
            Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
        """, "6/5", null, null),
        face("Off Angling", "{3}{U}", "Instant — Adventure", "Exile two target creatures and/or lands you control, then return them to the battlefield under their owner's control.", null, null, null),
    )
    private val mostAncientRaven = Cards.inventFaces("Adventure",
        face("Most Ancient Raven", "{U}", "Creature — Bird", """
            Flying
            Threshold — This creature gets +1/+1 as long as there are seven or more cards in your graveyard.
        """, "1/1", null, null),
        face("Whisper Riddles", "{1}{U}", "Sorcery — Adventure", "Mill four cards, then put an instant or sorcery card from among them into your hand.", null, null, null),
    )
    private val smeggleQuietCreeper = Cards.inventFaces("Adventure",
        face("Smeggle, Quiet Creeper", "{3}{B}", "Legendary Creature — Halfling Horror", "Menace (This creature can't be blocked except by two or more creatures.)", "4/3", null, null),
        face("Scanty Supper", "{B}", "Sorcery — Adventure", "Put a +1/+1 counter on up to one target creature. Target player gains 2 life. (Then exile this card. You may cast the creature later from exile.)", null, null, null),
    )
    private val bigWartyGoblin = Cards.inventFaces("Adventure",
        face("Big Warty Goblin", "{5}{B}", "Creature — Goblin Soldier", "Each creature you control with a +1/+1 counter on it has menace. (It can't be blocked except by two or more creatures.)", "4/4", null, null),
        face("Smash! Crash!", "{1}{B}", "Sorcery — Adventure", "Amass Goblins 2. (Then exile this card. You may cast the creature later from exile.)", null, null, null),
    )
    private val gruniTheStrong = Cards.inventFaces("Adventure",
        face("Gruni the Strong", "{3}{R}", "Legendary Creature — Dwarf Warrior", "At the beginning of your first main phase, add {R}{R}.", "4/3", null, null),
        face("Simple Spoils", "{2}{R}", "Sorcery — Adventure", "Simple Spoils deals 1 damage to each creature your opponents control. (Then exile this card. You may cast the creature later from exile.)", null, null, null),
    )
    private val skargTheGreatRuin = Cards.inventFaces("Adventure",
        face("Skarg, the Great Ruin", "{5}{R}{R}", "Legendary Creature — Dragon", "Flying", "5/5", null, null),
        face("Belch Fire", "{4}{R}", "Sorcery — Adventure", "Belch Fire deals 5 damage to target creature. (Then exile this card. You may cast the creature later from exile.)", null, null, null),
    )
    private val barnulfGrudgingHost = Cards.inventFaces("Adventure",
        face("Barnulf, Grudging Host", "{4}{G}", "Legendary Creature — Human Bear Shapeshifter", "Trample", "5/5", null, null),
        face("Plow and Sow", "{1}{G}", "Sorcery — Adventure", "You may play an additional land this turn. (Then exile this card. You may cast the creature later from exile.)", null, null, null),
    )
    private val elrianWoodlandLiege = Cards.inventFaces("Adventure",
        face("Elrian, Woodland Liege", "{2}{G/U}{G/U}", "Legendary Creature — Elf Noble", """
            Other Elves you control get +1/+1.
            Landfall — Whenever a land you control enters, create a 1/1 green Elf creature token.
        """, "2/3", null, null),
        face("Sylvan Muster", "{1}{G/U}{G/U}", "Sorcery — Adventure", "Mill four cards, then put up to two land cards from among them into your hand. (Then exile this card. You may cast the creature later from exile.)", null, null, null),
    )
    private val theHeartgem = Cards.inventFaces("Adventure",
        face("The Heartgem", "{5}", "Legendary Artifact", """
            Creatures you control get +1/+1.
            At the beginning of your end step, draw a card.
        """, null, null, null),
        face("Seek the Core", "{2}{W}", "Sorcery — Adventure", "Search your library for a legendary creature card, reveal it, put it into your hand, then shuffle. (Then exile this card. You may cast the artifact later from exile.)", null, null, null),
    )
    private val skarnbladeFoeCleaver = Cards.inventFaces("Adventure",
        face("Skarnblade, Foe-cleaver", "{2}", "Legendary Artifact — Equipment", """
            Instant and sorcery spells you cast cost {X} less to cast, where X is equipped creature's power.
            Equip {2}
        """, null, null, null),
        face("Glint of Doom", "{3}{U}", "Sorcery — Adventure", "Mill six cards, then put all instant and sorcery cards from among them into your hand. (Then exile this card. You may cast the artifact later from exile.)", null, null, null),
    )
    private val myTreasure = Cards.inventFaces("Adventure",
        face("My Treasure", "{3}", "Legendary Artifact — Equipment", """
            Equipped creature has hexproof and can't be blocked.
            Equip—{2}, Pay 2 life.
        """, null, null, null),
        face("Lure of Might", "{1}{B}", "Instant — Adventure", """
            As an additional cost to cast this spell, sacrifice a creature.
            Draw two cards.
        """, null, null, null),
    )
    private val dwarvenStabbingSword = Cards.invent("Dwarven Stabbing-Sword", "{3}{W}", "Artifact — Equipment", """
        When this Equipment enters, create a 2/2 red Dwarf creature token, then attach this Equipment to it.
        Equipped creature gets +1/+2.
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val copperFellsSmith = Cards.invent("Copper Fells Smith", "{1}{W}", "Creature — Dwarf Artificer", """
        Double strike
        When this creature enters, create a colorless Equipment artifact token named Axe with "Equipped creature gets +1/+0" and equip {2}.
    """, "1/1")
    private val roughTwistedKnife = Cards.invent("Rough Twisted Knife", "{2}{B}", "Artifact — Equipment", """
        When this Equipment enters, target opponent sacrifices a creature of their choice.
        Equipped creature gets +2/+1.
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val darviSteelboot = Cards.invent("Darvi Steelboot", "{2}{R}", "Legendary Creature — Dwarf Warrior", """
        When Darvi enters, create a colorless Equipment artifact token named Axe with "Equipped creature gets +1/+0" and equip {2}. When you do, attach it to target creature you control.
        Whenever Darvi attacks, each equipped attacking creature gains double strike until end of turn.
    """, "1/4")
    private val copperFellsStalwart = Cards.invent("Copper Fells Stalwart", "{4}{R}", "Creature — Dwarf Warrior", """
        Reach, trample
        When this creature enters, attach target Equipment you control to up to one target creature you control.
    """, "4/5")
    private val tharnHillKing = Cards.invent("Tharn, Hill-King", "{3}{R}", "Legendary Creature — Dwarf Noble", """
        Trample
        When Tharn enters, attach any number of target Equipment you control to target creature you control. When one or more Equipment become attached to that creature this way, that creature deals damage equal to its power to up to one target creature.
    """, "3/4")
    private val theDarkBolt = Cards.invent("The Dark Bolt", "{3}", "Legendary Artifact — Equipment", """
        Flash
        When The Dark Bolt enters, it deals 1 damage to any target. If a Dragon is dealt damage this way, destroy it.
        Equipped creature gets +1/+1 and has reach.
        Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val dwarvenPick = Cards.invent("Dwarven Pick", "{2}", "Artifact — Equipment", """
        When this Equipment enters, attach it to target Dwarf you control.
        Equipped creature gets +2/+2 and has ward {1}. (Whenever equipped creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {1}.)
        Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val grimcleaveGoblinSplitter = Cards.invent("Grimcleave, Goblin-splitter", "{3}", "Legendary Artifact — Equipment", """
        Equipped creature gets +2/+2 and has trample.
        Whenever equipped creature deals combat damage to a player, choose a creature type. Create a Treasure token for each creature you control of that type.
        Equip {3}
    """)
    private val prickTobbosBlade = Cards.invent("Prick, Tobbo's Blade", "{2}", "Legendary Artifact — Equipment", """
        Flash
        When Prick enters, put a hone counter on Prick for each creature target opponent controls. Attach Prick to up to one target creature you control. (Each hone counter on an Equipment grants +1/+0 to equipped creature.)
        Equip {3}
    """)
    private val pledgeToKhazrund = Cards.invent("Pledge to Khazrund", "{1}{W}", "Instant", "Untap target creature you control. It gets +2/+2 until end of turn. If it's a Dwarf, you may attach an Equipment you control to it.")
    private val theSolitaryPeak = Cards.invent("The Solitary Peak", null, "Land — Mountain", """
        ({T}: Add {R}.)
        This land enters tapped unless you control an Equipment.
        {4}{R}, {T}: Create a 2/2 red Dwarf creature token. This ability costs {1} less to activate for each Equipment you control. Activate only as a sorcery.
    """)
    private val meresideHerbalist = Cards.invent("Mereside Herbalist", "{1}{U}", "Creature — Human Cleric", """
        Vigilance
        Whenever you draw your second card each turn, put a +1/+1 counter on this creature.
    """, "1/2")
    private val mayorsAdvisers = Cards.invent("Mayor's Advisers", "{1}{U}", "Creature — Human Advisor", """
        Vigilance
        This creature gets +2/+0 for each graveyard with seven or more cards in it.
        Whenever you draw your second card each turn, target player mills three cards. (They put the top three cards of their library into their graveyard.)
    """, "1/3")
    private val brandTheArcher = Cards.invent("Brand the Archer", "{1}{W}{U}", "Legendary Creature — Human Archer", """
        Reach
        Whenever you draw your second card each turn, put a +1/+1 counter on target creature. It gains lifelink until end of turn.
    """, "1/3")
    private val crowhillFlock = Cards.invent("Crowhill Flock", "{3}{U}", "Creature — Bird", """
        Flying
        Whenever you draw a card, put a +1/+1 counter on this creature.
    """, "1/2")
    private val elvenBargePilot = Cards.invent("Elven Barge-Pilot", "{2}{U}", "Creature — Elf Pilot", """
        Landfall — Whenever a land you control enters, choose one —
        • Tap target creature an opponent controls.
        • Untap target creature you control.
    """, "3/2")
    private val gloomwoodContemplator = Cards.invent("Gloomwood Contemplator", "{2}{U}", "Creature — Elf Druid", "Landfall — Whenever a land you control enters, you may have this creature's base power and toughness become 4/2 until end of turn.", "2/4")
    private val cobwebLurker = Cards.invent("Cobweb Lurker", "{1}{G}", "Creature — Spider", """
        Reach, deathtouch
        Landfall — Whenever a land you control enters, this creature gets +1/+1 until end of turn.
    """, "2/1")
    private val branchsideRovers = Cards.invent("Branchside Rovers", "{4}{G}{G}", "Creature — Elf Scout", """
        When this creature enters, look at the top four cards of your library. You may reveal a permanent card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        Landfall — Whenever a land you control enters, this creature gets +2/+2 until end of turn.
    """, "4/4")
    private val revelsFromDuskToDawn = Cards.invent("Revels from Dusk to Dawn", "{3}{G}{G}", "Enchantment", """
        Whenever you cast a creature spell, put X +1/+1 counters on target creature you control, where X is that spell's mana value.
        Landfall — Whenever a land you control enters, create a 2/2 green Bear creature token.
    """)

    private val hoard = Cards.invent("Lonely Hill Hoard", "{R}", "Sorcery", "Create three Treasure tokens.")
    private val dragon = Cards.invent("Lonely Hill Drake", "{3}{R}", "Creature — Dragon", "Flying", "4/4")

    // ---- Chunk 01: storied (CR 702.195)

    // CR 508.1h: an attack cost for each creature
    @Test
    fun darviTaxesAttackersWithAStory() = scenario {
        alice.battlefield(darviLordOfTheCopperFells, idol, "Plains", "Plains")
        alice.hand(idol)
        alice.library(bear)
        bob.battlefield(bear)
        bob.library(bear)
        alice.casts(idol)
        resolveAll()
        assertTrue(hasStory(alice))
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        assertFailsWith<IllegalArgumentException> { bob.attacks(bear) }
    }

    @Test
    fun braliMakesDwarvesAndPumpsWithAStory() = scenario {
        alice.battlefield(idol, idol, "Plains", "Plains", "Plains", "Plains")
        alice.hand(braliTheTrailfinder)
        alice.casts(braliTheTrailfinder)
        resolveAll()
        assertTrue(hasStory(alice))
        expect {
            "Dwarf Token".power shouldBe 3
            braliTheTrailfinder.power shouldBe 3
        }
    }

    @Test
    fun borinRedrawsAndBurnsWithAStory() = scenario {
        alice.battlefield(idol, idol, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(borinSagaKeeper, bear, ogre)
        alice.library(elf, elf, elf)
        alice.casts(borinSagaKeeper)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.graveyard has ogre
            bob.life shouldBe 18
        }
    }

    @Test
    fun ulfiLootsAndGetsHasteWithAStory() = scenario {
        alice.battlefield(ulfiTheBrave, idol, "Mountain", "Mountain", "Mountain")
        alice.hand(idol, bear)
        alice.library(ogre)
        expect { ulfiTheBrave lacksKeyword Keyword.Haste }
        alice.casts(idol, paying = listOf("Mountain", "Mountain"))
        resolveAll()
        expect {
            ulfiTheBrave hasKeyword Keyword.Haste
            ulfiTheBrave.power shouldBe 2
        }
        alice.activates(ulfiTheBrave, choosing = listOf(bear))
        resolveAll()
        expect { alice.hand has ogre }
    }

    // CR 603.2d: an additional trigger of a Dwarf's ability
    @Test
    fun bofrinDoublesDwarfTriggersWithAStory() = scenario {
        alice.battlefield(idol, idol, bear, "Mountain", "Mountain", "Mountain", "Plains", "Plains", "Plains")
        alice.hand(bofrinTunefulRider)
        alice.casts(bofrinTunefulRider)
        repeat(3) {
            resolveAll()
            targetsIfAsked(bear)
        }
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun tharnIronbucklerGivesWardWithAStory() = scenario {
        alice.battlefield(tharnIronbuckler, idol, bear, "Plains", "Plains")
        alice.hand(idol)
        bob.battlefield("Mountain")
        bob.hand(shock)
        alice.casts(idol)
        resolveAll()
        alice.passes()
        bob.casts(shock, target = bear)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    // ---- Chunk 02: recruit (CR 701.70)

    @Test
    fun fenmereGarrisonRecruitsAndCounts() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(fenmereGarrison)
        alice.library(bear)
        alice.casts(fenmereGarrison)
        resolveAll()
        chooseIfOffered(bear)
        resolveAll()
        expect {
            alice.battlefield has "Human Soldier Token"
            fenmereGarrison.power shouldBe 2
        }
    }

    @Test
    fun marshTownLookoutRecruitsOnDeath() = scenario {
        alice.battlefield(marshTownLookout, "Swamp")
        alice.hand(doom)
        alice.library(bear)
        alice.casts(doom, target = marshTownLookout)
        resolveAll()
        chooseIfOffered(bear)
        resolveAll()
        expect { alice.battlefield has "Human Soldier Token" }
    }

    @Test
    fun theDuchessOfValeRecruitsOnTheirFirstNoncreatureSpell() = scenario {
        alice.battlefield(theDuchessOfVale)
        alice.library(bear, ogre)
        bob.battlefield("Mountain", "Mountain")
        bob.hand(shock, shock)
        turn(active = bob)
        bob.casts(shock, target = "alice")
        resolveAll()
        chooseIfOffered(bear)
        resolveAll()
        bob.casts(shock, target = "alice")
        resolveAll()
        expect {
            alice.graveyard has bear
            alice.hand.size shouldBe 0
        }
    }

    @Test
    fun grandPaintedBargeRecruitsOnAttack() = scenario {
        alice.battlefield(grandPaintedBarge, bear)
        alice.library("Island")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        chooseIfOffered("Island")
        resolveAll()
        expect {
            alice.graveyard has "Island"
            alice.battlefield lacks "Human Soldier Token"
        }
    }

    // CR 702.122a: crew
    @Test
    fun grandPaintedBargeCrews() = scenario {
        alice.battlefield(grandPaintedBarge, bear)
        alice.activates(grandPaintedBarge, choosing = listOf(bear))
        resolveAll()
        expect { grandPaintedBarge.types.contains(CardType.Creature) shouldBe true }
    }

    @Test
    fun blowTheClarionsRecruitsForACheapSpell() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(blowTheClarions)
        alice.library(ogre)
        bob.battlefield("Mountain")
        bob.hand(shock)
        turn(active = bob)
        bob.casts(shock, target = "alice")
        bob.passes()
        alice.casts(blowTheClarions, target = shock)
        resolveAll()
        chooseIfOffered(ogre)
        resolveAll()
        expect {
            alice.life shouldBe 20
            alice.battlefield has "Human Soldier Token"
        }
    }

    // CR 702.8a (flash), 601.3e
    @Test
    fun archersMusterFlashesWithAHuman() = scenario {
        alice.battlefield(human, "Plains", "Plains", "Island", "Island")
        alice.hand(archersMuster)
        alice.library(bear, bear)
        bob.library(bear)
        advanceTo(TurnStep.Upkeep, turn = 2)
        passPriority()
        alice.casts(archersMuster)
        resolveAll()
        chooseIfOffered(bear)
        resolveAll()
        expect {
            human.power shouldBe 2
            alice.battlefield.names.count { it == "Human Soldier Token" } shouldBe 1
        }
    }

    // ---- Chunk 03: amass Goblins (CR 701.47)

    @Test
    fun downTheTwistedPathRegrowsAndAmasses() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(downTheTwistedPath)
        alice.graveyard(ogre)
        alice.casts(downTheTwistedPath, paying = listOf("Swamp", "Swamp", "Swamp"))
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect {
            alice.hand has ogre
            "Goblin Army Token".power shouldBe 1
        }
        alice.activates(downTheTwistedPath)
        resolveAll()
        expect { "Goblin Army Token" hasKeyword Keyword.Menace }
    }

    @Test
    fun musteringOfShadowsRegrowsAndAmassesThree() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(musteringOfShadows)
        alice.graveyard(ogre)
        alice.casts(musteringOfShadows, target = ogre)
        resolveAll()
        expect {
            alice.hand has ogre
            "Goblin Army Token".power shouldBe 3
        }
    }

    @Test
    fun stormIntoTheDaleDrawsAndAmasses() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(stormIntoTheDale)
        alice.library(bear)
        alice.casts(stormIntoTheDale)
        resolveAll()
        expect {
            alice.hand has bear
            alice.life shouldBe 19
            "Goblin Army Token".power shouldBe 2
        }
    }

    @Test
    fun wilderfellRampagerEatsAndAmassesOnDeath() = scenario {
        alice.battlefield(wilderfellRampager, ogre, "Swamp")
        alice.hand(doom)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wilderfellRampager)
        resolveAll()
        may(true)
        chooseIfOffered(ogre)
        resolveAll()
        expect { wilderfellRampager.counters(p1p1) shouldBe 3 }
        alice.casts(doom, target = wilderfellRampager)
        resolveAll()
        expect { "Goblin Army Token".power shouldBe 6 }
    }

    @Test
    fun peskyDrumbangerAmassesOnSpells() = scenario {
        alice.battlefield(peskyDrumbanger, "Mountain")
        alice.hand(shock)
        alice.casts(shock, target = "bob")
        resolveAll()
        expect { "Goblin Army Token".power shouldBe 1 }
    }

    @Test
    fun foggyPeaksRaiderAmassesOnAttack() = scenario {
        alice.battlefield(foggyPeaksRaider)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(foggyPeaksRaider)
        resolveAll()
        expect { "Goblin Army Token".power shouldBe 2 }
    }

    @Test
    fun rumorsOfBattleAmassesMoreFromTheGraveyard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(rumorsOfBattle)
        alice.casts(rumorsOfBattle, paying = listOf("Mountain"))
        resolveAll()
        expect { "Goblin Army Token".power shouldBe 1 }
        alice.casts(rumorsOfBattle)
        resolveAll()
        expect { "Goblin Army Token".power shouldBe 4 }
    }

    // CR 120.4a: excess damage
    @Test
    fun skarnOfTheNorthAmassesTheExcess() = scenario {
        alice.battlefield(giant, "Swamp", "Swamp", "Swamp", "Mountain", "Mountain")
        alice.hand(skarnOfTheNorth)
        bob.battlefield(bear)
        alice.casts(skarnOfTheNorth)
        resolveAll()
        may(true)
        chooseIfOffered(giant)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bob.graveyard has bear
            "Goblin Army Token".power shouldBe 3
        }
    }

    @Test
    fun dreadfulGoblinDuoAmassesFourOnDeath() = scenario {
        alice.battlefield(dreadfulGoblinDuo, "Swamp")
        alice.hand(doom)
        alice.casts(doom, target = dreadfulGoblinDuo)
        resolveAll()
        expect { "Goblin Army Token".power shouldBe 4 }
    }

    @Test
    fun goblinScaleHauberkSuitsUpTheArmy() = scenario {
        alice.battlefield("Swamp", "Mountain")
        alice.hand(goblinScaleHauberk)
        alice.casts(goblinScaleHauberk)
        resolveAll()
        expect {
            "Goblin Army Token".power shouldBe 2
            "Goblin Army Token" hasKeyword Keyword.Menace
        }
    }

    @Test
    fun skarnsWarbandHastyWithAGoblinAndSacrificesForMana() = scenario {
        alice.battlefield(goblin)
        alice.battlefield(skarnsWarband, sick = true)
        expect { skarnsWarband hasKeyword Keyword.Haste }
        alice.activates(skarnsWarband, choosing = listOf(goblin))
        expect {
            alice.pool.size shouldBe 2
            alice.graveyard has goblin
        }
    }

    // ---- Chunk 04: Sagas (CR 714)

    @Test
    fun theHillLordComesHomeRecruitsThenReanimates() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(theHillLordComesHome)
        alice.graveyard(ogre)
        alice.library(bear, elf, elf)
        bob.library(elf, elf)
        alice.casts(theHillLordComesHome)
        resolveAll()
        chooseIfOffered(bear)
        resolveAll()
        expect { alice.battlefield has "Human Soldier Token" }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { alice.battlefield has ogre }
    }

    @Test
    fun pathsWindOnAndOnStoresPlains() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(pathsWindOnAndOn)
        alice.library("Plains", bear, "Plains", elf, elf)
        bob.library(elf, elf)
        alice.casts(pathsWindOnAndOn)
        resolveAll()
        alice.chooses("Plains", "Plains")
        resolveAll()
        expect {
            alice.life shouldBe 22
            alice.exile.names.count { it == "Plains" } shouldBe 2
        }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        chooseIfOffered("Plains")
        resolveAll()
        expect { alice.hand has "Plains" }
    }

    @Test
    fun lazyLobCantCatchMeShieldsAndDraws() = scenario {
        alice.battlefield("Island", "Island", "Island", bear)
        alice.hand(lazyLobCantCatchMe)
        alice.casts(lazyLobCantCatchMe)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bear hasKeyword Keyword.Hexproof }
    }

    @Test
    fun bumpBumpBlinksAtTheNextEndStep() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.hand(bumpBumpBumpBump)
        alice.casts(bumpBumpBumpBump)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { alice.exile has bear }
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield has bear
            bear.counters(p1p1) shouldBe 0
        }
    }

    @Test
    fun deepDeepToTheGoblinHallsStripsAHand() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(deepDeepToTheGoblinHalls)
        bob.hand(bear, "Forest")
        alice.casts(deepDeepToTheGoblinHalls)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        chooseIfOffered(bear)
        resolveAll()
        expect { bob.graveyard has bear }
    }

    @Test
    fun blazeBlazePineAndMossBurns() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(blazeBlazePineAndMoss)
        bob.battlefield(giant)
        alice.casts(blazeBlazePineAndMoss)
        resolveAll()
        targetsIfAsked(giant)
        resolveAll()
        expect { bob.graveyard has giant }
    }

    @Test
    fun theFrozenPeaksFarMakesATreasure() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(theFrozenPeaksFar)
        alice.casts(theFrozenPeaksFar)
        resolveAll()
        expect { alice.battlefield has "Treasure Token" }
    }

    // CR 714.2: four Treasures turn the Saga into a Dragon
    @Test
    fun theFrozenPeaksFarBecomesADragon() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(theFrozenPeaksFar, hoard)
        alice.casts(hoard, paying = listOf("Mountain"))
        resolveAll()
        alice.casts(theFrozenPeaksFar, paying = listOf("Mountain#1", "Mountain#2", "Mountain#3"))
        resolveAll()
        expect {
            alice.graveyard has theFrozenPeaksFar
            "Dragon Token".power shouldBe 6
        }
    }

    @Test
    fun deepInTheDellFetchesABasic() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(deepInTheDell)
        alice.library(bear, "Forest")
        alice.casts(deepInTheDell)
        resolveAll()
        alice.chooses("Forest")
        resolveAll()
        expect { alice.hand has "Forest" }
    }

    // ---- Chunk 05: Adventures (CR 715)

    @Test
    fun bolfinUnitedCareShieldsThenBolfinFromExile() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear)
        alice.hand(bolfinSteadyWarden)
        alice.casts("United Care", way = CastWay(face = 1), target = bear)
        resolveAll()
        expect {
            bear hasKeyword Keyword.Indestructible
            alice.exile has bolfinSteadyWarden
        }
        alice.casts(bolfinSteadyWarden)
        resolveAll()
        expect { alice.battlefield has bolfinSteadyWarden }
    }

    @Test
    fun aSurpriseFeastKnocksForDwarvesAndPumpsAType() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(aSurpriseFeast)
        alice.casts("Knock Knock", way = CastWay(face = 1), x = 2)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Dwarf Token" } shouldBe 2 }
        alice.casts(aSurpriseFeast)
        resolveAll()
        answer(Answer.SubtypeChoice(Subtype("Dwarf")))
        resolveAll()
        expect { "Dwarf Token".power shouldBe 4 }
    }

    @Test
    fun silkwingMothsStareTapsTwo() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(silkwingMoths)
        bob.battlefield(bear, ogre)
        alice.casts("Stare in Awe", way = CastWay(face = 1), targets = listOf(bear, ogre))
        resolveAll()
        expect {
            bear.tapped shouldBe true
            ogre.tapped shouldBe true
        }
    }

    @Test
    fun tobboPeeksAheadThenDraws() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island")
        alice.hand(tobboUnderhillSneak)
        alice.library(bear, ogre, elf)
        alice.casts("Peek Ahead", way = CastWay(face = 1), paying = listOf("Island"))
        resolveAll()
        chooseNone()
        // the two kept on top, in the order offered
        (pending as? Decision.ChooseObjects)?.let { answer(Answer.Objects(it.options)) }
        resolveAll()
        alice.casts(tobboUnderhillSneak)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun mereTownBoatmenOffAnglingBlinksTwo() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Forest")
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.hand(mereTownBoatmen)
        alice.casts("Off Angling", way = CastWay(face = 1), targets = listOf(bear, "Forest"))
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 0
            alice.exile has mereTownBoatmen
        }
    }

    @Test
    fun mostAncientRavenWhispersForASpell() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(mostAncientRaven)
        alice.library(bear, shock, ogre, elf)
        alice.casts("Whisper Riddles", way = CastWay(face = 1))
        resolveAll()
        chooseIfOffered(shock)
        resolveAll()
        expect {
            alice.hand has shock
            alice.graveyard has bear
        }
    }

    @Test
    fun mostAncientRavenGrowsAtThreshold() = scenario {
        alice.battlefield(mostAncientRaven)
        alice.graveyard(elf, elf, elf, elf, elf, elf, elf)
        expect { mostAncientRaven.power shouldBe 2 }
    }

    @Test
    fun smeggleScantySupperFeeds() = scenario {
        alice.battlefield("Swamp", bear)
        alice.hand(smeggleQuietCreeper)
        alice.casts("Scanty Supper", way = CastWay(face = 1), targets = listOf(bear, "alice"))
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            alice.life shouldBe 22
        }
    }

    @Test
    fun bigWartyGoblinSmashesAndMenacesCounteredCreatures() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(bigWartyGoblin)
        alice.casts("Smash! Crash!", way = CastWay(face = 1), paying = listOf("Swamp", "Swamp"))
        resolveAll()
        alice.casts(bigWartyGoblin)
        resolveAll()
        expect { "Goblin Army Token" hasKeyword Keyword.Menace }
    }

    @Test
    fun gruniSimpleSpoilsThenMana() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(gruniTheStrong)
        alice.library(bear, bear)
        bob.battlefield(elf, bear)
        bob.library(bear, bear)
        alice.casts("Simple Spoils", way = CastWay(face = 1))
        resolveAll()
        expect {
            bob.graveyard has elf
            bob.battlefield has bear
        }
        alice.casts(gruniTheStrong)
        resolveAll()
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        expect { alice.pool.size shouldBe 2 }
    }

    @Test
    fun skargBelchFire() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(skargTheGreatRuin)
        bob.battlefield(giant)
        alice.casts("Belch Fire", way = CastWay(face = 1), target = giant)
        resolveAll()
        expect {
            bob.graveyard has giant
            alice.exile has skargTheGreatRuin
        }
    }

    @Test
    fun barnulfPlowAndSowGivesALandPlay() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(barnulfGrudgingHost, "Forest", "Island")
        alice.casts("Plow and Sow", way = CastWay(face = 1))
        resolveAll()
        alice.plays("Forest")
        alice.plays("Island")
        expect { alice.battlefield has "Island" }
    }

    @Test
    fun elrianSylvanMusterThenElvesOnLandfall() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Island")
        alice.hand(elrianWoodlandLiege)
        alice.library("Island", bear, "Forest", elf)
        alice.casts("Sylvan Muster", way = CastWay(face = 1), paying = listOf("Forest", "Forest", "Forest"))
        resolveAll()
        alice.chooses("Island", "Forest")
        resolveAll()
        expect {
            alice.hand has "Island"
            alice.hand has "Forest"
        }
    }

    @Test
    fun elrianMakesElvesOnLandfall() = scenario {
        alice.battlefield(elrianWoodlandLiege)
        alice.hand("Forest")
        alice.plays("Forest")
        resolveAll()
        expect { "Elf Token".power shouldBe 2 }
    }

    @Test
    fun theHeartgemSeeksALegendThenPumpsAndDraws() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(theHeartgem)
        alice.library(ogre, gruniTheStrong, elf)
        alice.casts("Seek the Core", way = CastWay(face = 1), paying = listOf("Plains", "Plains", "Plains"))
        resolveAll()
        alice.chooses(gruniTheStrong)
        resolveAll()
        expect { alice.hand has gruniTheStrong }
        alice.casts(theHeartgem)
        resolveAll()
        expect { bear.power shouldBe 3 }
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun skarnbladeGlintOfDoomThenCheaperSpells() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Mountain", ogre)
        alice.hand(skarnbladeFoeCleaver)
        alice.library(shock, bear, ponder, elf, elf, elf)
        alice.casts("Glint of Doom", way = CastWay(face = 1), paying = listOf("Island", "Island", "Island", "Island"))
        resolveAll()
        expect {
            alice.hand has shock
            alice.hand has ponder
            alice.graveyard has bear
        }
        alice.casts(skarnbladeFoeCleaver, paying = listOf("Island#4", "Island#5"))
        resolveAll()
        expect { alice.battlefield has skarnbladeFoeCleaver }
    }

    @Test
    fun myTreasureLureOfMightAndAnUnblockableBearer() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", elf, bear)
        alice.hand(myTreasure)
        alice.library(ogre, ogre)
        alice.casts("Lure of Might", way = CastWay(face = 1), choosing = listOf(elf), paying = listOf("Swamp", "Swamp"))
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
        alice.casts(myTreasure, paying = listOf("Swamp#2", "Swamp#3", "Swamp#4"))
        resolveAll()
        alice.activates(myTreasure, target = bear)
        resolveAll()
        expect {
            bear hasKeyword Keyword.Hexproof
            alice.life shouldBe 18
        }
    }

    // ---- Chunk 06: Equipment

    @Test
    fun dwarvenStabbingSwordArmsANewDwarf() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(dwarvenStabbingSword)
        alice.casts(dwarvenStabbingSword)
        resolveAll()
        expect {
            "Dwarf Token".power shouldBe 3
            "Dwarf Token".toughness shouldBe 4
        }
    }

    @Test
    fun copperFellsSmithForgesAnAxe() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(copperFellsSmith)
        alice.casts(copperFellsSmith, paying = listOf("Plains", "Plains"))
        resolveAll()
        alice.activates("Axe", target = bear)
        resolveAll()
        expect { bear.power shouldBe 3 }
    }

    @Test
    fun roughTwistedKnifeMakesThemSacrifice() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(roughTwistedKnife)
        bob.battlefield(bear)
        alice.casts(roughTwistedKnife)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        chooseIfOffered(bear)
        resolveAll()
        expect { bob.graveyard has bear }
    }

    @Test
    fun darviSteelbootAxesAndDoubleStrikes() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", bear)
        alice.hand(darviSteelboot)
        alice.casts(darviSteelboot)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.power shouldBe 3 }
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(darviSteelboot, bear) }
    }

    @Test
    fun darviSteelbootGivesEquippedAttackersDoubleStrike() = scenario {
        alice.battlefield(darviSteelboot, bear, sword, "Plains")
        alice.activates(sword, target = bear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(darviSteelboot, bear)
        resolveAll()
        expect { bear hasKeyword Keyword.DoubleStrike }
    }

    @Test
    fun copperFellsStalwartAttachesEquipment() = scenario {
        alice.battlefield(sword, bear, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(copperFellsStalwart)
        alice.casts(copperFellsStalwart)
        resolveAll()
        pickTargets(sword, bear)
        resolveAll()
        expect { bear.power shouldBe 3 }
    }

    @Test
    fun tharnHillKingArmsAndStrikes() = scenario {
        alice.battlefield(sword, bear, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(tharnHillKing)
        bob.battlefield(ogre)
        alice.casts(tharnHillKing)
        resolveAll()
        pickTargets(sword, bear)
        resolveAll()
        pickTargets(ogre)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bob.graveyard has ogre
        }
    }

    @Test
    fun theDarkBoltSlaysADragon() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(theDarkBolt)
        bob.battlefield(dragon)
        alice.casts(theDarkBolt)
        resolveAll()
        targetsIfAsked(dragon)
        resolveAll()
        expect { bob.graveyard has dragon }
    }

    @Test
    fun dwarvenPickAttachesToADwarf() = scenario {
        alice.battlefield(dwarf, "Plains", "Plains")
        alice.hand(dwarvenPick)
        alice.casts(dwarvenPick)
        resolveAll()
        targetsIfAsked(dwarf)
        resolveAll()
        expect { dwarf.power shouldBe 4 }
    }

    @Test
    fun grimcleaveMakesTreasuresByType() = scenario {
        alice.battlefield(grimcleaveGoblinSplitter, dwarf, dwarf, "Plains", "Plains", "Plains")
        alice.activates(grimcleaveGoblinSplitter, target = dwarf)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(dwarf)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        answer(Answer.SubtypeChoice(Subtype("Dwarf")))
        resolveAll()
        expect {
            bob.life shouldBe 16
            alice.battlefield.names.count { it == "Treasure Token" } shouldBe 2
        }
    }

    @Test
    fun prickHonesPerOpposingCreature() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(prickTobbosBlade)
        bob.battlefield(elf, elf, elf)
        alice.casts(prickTobbosBlade)
        resolveAll()
        pickTargets("bob", bear)
        resolveAll()
        expect { bear.power shouldBe 5 }
    }

    @Test
    fun pledgeToKhazrundUntapsAndArmsADwarf() = scenario {
        alice.battlefield(sword, "Plains", "Plains")
        alice.battlefield(dwarf, tapped = true)
        alice.hand(pledgeToKhazrund)
        alice.casts(pledgeToKhazrund, target = dwarf)
        resolveAll()
        chooseIfOffered(sword)
        resolveAll()
        expect {
            dwarf.tapped shouldBe false
            dwarf.power shouldBe 5
        }
    }

    @Test
    fun theSolitaryPeakMakesDwarvesCheaperWithEquipment() = scenario {
        alice.battlefield(theSolitaryPeak, sword, "Mountain", "Mountain", "Mountain", "Mountain")
        alice.activates(theSolitaryPeak)
        resolveAll()
        expect { alice.battlefield has "Dwarf Token" }
    }

    @Test
    fun theSolitaryPeakEntersTappedWithoutEquipment() = scenario {
        alice.hand(theSolitaryPeak)
        alice.plays(theSolitaryPeak)
        expect { theSolitaryPeak.tapped shouldBe true }
    }

    // ---- Chunk 07: drawing and landfall

    @Test
    fun meresideHerbalistGrowsOnTheSecondDraw() = scenario {
        alice.battlefield(meresideHerbalist, "Island", "Island")
        alice.hand(ponder, ponder)
        alice.library(bear, bear)
        alice.casts(ponder)
        resolveAll()
        expect { meresideHerbalist.counters(p1p1) shouldBe 0 }
        alice.casts(ponder)
        resolveAll()
        expect { meresideHerbalist.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun mayorsAdvisersMillOnTheSecondDraw() = scenario {
        alice.battlefield(mayorsAdvisers, "Island", "Island")
        alice.hand(ponder, ponder)
        alice.library(bear, bear)
        bob.library(elf, elf, elf, elf)
        bob.graveyard(elf, elf, elf, elf)
        alice.casts(ponder)
        resolveAll()
        alice.casts(ponder)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect {
            bob.graveyard.size shouldBe 7
            mayorsAdvisers.power shouldBe 3
        }
    }

    @Test
    fun brandTheArcherBuffsOnTheSecondDraw() = scenario {
        alice.battlefield(brandTheArcher, bear, "Island", "Island")
        alice.hand(ponder, ponder)
        alice.library(elf, elf)
        alice.casts(ponder)
        resolveAll()
        alice.casts(ponder)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Lifelink
        }
    }

    @Test
    fun crowhillFlockGrowsPerDraw() = scenario {
        alice.battlefield(crowhillFlock, "Island")
        alice.hand(ponder)
        alice.library(bear)
        alice.casts(ponder)
        resolveAll()
        expect { crowhillFlock.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun elvenBargePilotTapsOnLandfall() = scenario {
        alice.battlefield(elvenBargePilot)
        alice.hand("Island")
        bob.battlefield(ogre)
        alice.plays("Island")
        resolveAll()
        modes(0)
        targetsIfAsked(ogre)
        resolveAll()
        expect { ogre.tapped shouldBe true }
    }

    @Test
    fun gloomwoodContemplatorBecomesFourTwo() = scenario {
        alice.battlefield(gloomwoodContemplator)
        alice.hand("Island")
        alice.plays("Island")
        resolveAll()
        may(true)
        resolveAll()
        expect {
            gloomwoodContemplator.power shouldBe 4
            gloomwoodContemplator.toughness shouldBe 2
        }
    }

    @Test
    fun cobwebLurkerGrowsOnLandfall() = scenario {
        alice.battlefield(cobwebLurker)
        alice.hand("Forest")
        alice.plays("Forest")
        resolveAll()
        expect { cobwebLurker.power shouldBe 3 }
    }

    @Test
    fun branchsideRoversFindsAPermanent() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(branchsideRovers)
        alice.library(shock, ogre, ponder, elf)
        alice.casts(branchsideRovers)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(ogre)
        resolveAll()
        expect { alice.hand has ogre }
    }

    @Test
    fun revelsFromDuskToDawnCountersAndBears() = scenario {
        alice.battlefield(revelsFromDuskToDawn, bear, "Mountain", "Mountain", "Mountain")
        alice.hand(ogre, "Forest")
        alice.casts(ogre)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 3 }
        alice.plays("Forest")
        resolveAll()
        expect { alice.battlefield has "Bear Token" }
    }
}
