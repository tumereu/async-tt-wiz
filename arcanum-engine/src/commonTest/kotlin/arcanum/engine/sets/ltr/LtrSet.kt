package arcanum.engine.sets.ltr

import arcanum.engine.cards.Cards

/**
 * The invented The Lord of the Rings: Tales of Middle-earth (ltr) cards of the parser tests (`arcanum.parser.sets.ltr`), the same
 * names and texts, for the scenarios: each property registers its card and holds its name.
 */
internal object LtrSet {
    // chunk 01
    val banishFromHalmere = Cards.invent("Banish from Halmere", "{4}{W}", "Sorcery", "This spell costs {2} less to cast if it targets a tapped creature.\nExile target creature.")
    // chunk 01
    val theSkirmishAtReedbrook = Cards.invent("The Skirmish at Reedbrook", "{1}{W}{W}", "Sorcery", "Destroy all creatures with power 3 or greater. Then create a Food token for each creature you control. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")")
    // chunk 01
    val tamThePackhorse = Cards.invent("Tam the Packhorse", "{3}{W}", "Legendary Creature — Horse", "When Tam the Packhorse enters, create two Food tokens. (They're artifacts with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")\nSacrifice a Food: Until end of turn, target creature you control assigns combat damage equal to its toughness rather than its power.", "1/4")
    // chunk 01
    val hadrionWardenOfTheGate = Cards.invent("Hadrion, Warden of the Gate", "{2}{W}", "Legendary Creature — Human Soldier", "Vigilance\nWhenever an opponent casts a spell, if no mana was spent to cast it, counter that spell.\nSacrifice Hadrion: Creatures you control gain indestructible until end of turn. The Ring tempts you.", "3/3")
    // chunk 01
    val morningOfNewHope = Cards.invent("Morning of New Hope", "{1}{W}", "Enchantment", "This enchantment enters with a hope counter on it for each creature you control.\nAt the beginning of your end step, remove a hope counter from this enchantment. If you do, draw a card. Then if this enchantment has no hope counters on it, sacrifice it and you gain 4 life.")
    // chunk 01
    val waywardRangersBlade = Cards.invent("Wayward Ranger's Blade", "{1}{W}", "Artifact — Equipment", "Equipped creature gets +2/+1.\nEquip Human {1}\nEquip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)")
    // chunk 01
    val eaglesOfTheFarPeaks = Cards.invent("Eagles of the Far Peaks", "{5}{W}", "Creature — Bird Soldier", "Flying\nWhen this creature enters, creatures you control get +1/+0 and gain first strike until end of turn.\nPlainscycling {1} ({1}, Discard this card: Search your library for a Plains card, reveal it, put it into your hand, then shuffle.)", "3/3")
    // chunk 01
    val westmeadowFarmer = Cards.invent("Westmeadow Farmer", "{2}{W}", "Creature — Halfling Peasant", "When this creature enters, create a Food token. When you do, target creature you control gets +1/+1 until end of turn for each Food you control. (A Food token is an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")", "2/3")
    // chunk 01
    val northMarkCavalier = Cards.invent("North-Mark Cavalier", "{1}{W}", "Creature — Human Knight", "Vigilance\nWhenever this creature deals damage to a Goblin or Orc, destroy that creature.", "2/2")
    // chunk 01
    val aldrethLadyOfThePlains = Cards.invent("Aldreth, Lady of the Plains", "{2}{W}", "Legendary Creature — Human Noble", "At the beginning of combat on your turn, target creature gains your choice of first strike or vigilance until end of turn. If that creature is equipped, it gains first strike and vigilance until end of turn instead.\nEquip abilities you activate cost {1} less to activate.", "2/4")
    // chunk 01
    val errandRiderOfTheWhiteCity = Cards.invent("Errand-Rider of the White City", "{2}{W}", "Creature — Human Soldier", "When this creature enters, draw a card. Then if you don't control a legendary creature, put a card from your hand on the bottom of your library.", "3/2")
    // chunk 01
    val escapeFromTheTower = Cards.invent("Escape from the Tower", "{W}", "Instant", "Target creature gets +1/+3 and gains flying until end of turn. Untap it.")
    // chunk 01
    val esquireOfTheSteward = Cards.invent("Esquire of the Steward", "{W}", "Creature — Human Soldier", "{4}{W}, {T}: Creatures you control get +1/+1 until end of turn. This ability costs {2} less to activate if you control a legendary creature.", "1/1")
    // chunk 01
    val haldorFieldCaptain = Cards.invent("Haldor, Field Captain", "{3}{W}", "Legendary Creature — Human Soldier", "At the beginning of your end step, if a creature died under your control this turn, draw a card.\nWhenever the Ring tempts you, if you chose a creature other than Haldor as your Ring-bearer, create a 1/1 white Human Soldier creature token.", "3/3")
    // chunk 01
    val blossomingOfTheSilverTree = Cards.invent("Blossoming of the Silver Tree", "{W}{W}", "Legendary Enchantment", "Legendary creatures you control get +2/+1 and have ward {1}.\nNonlegendary creatures you control get +1/+1.")
    // chunk 01
    val mistOnTheBarrowHills = Cards.invent("Mist on the Barrow Hills", "{2}{W}", "Enchantment — Aura", "Enchant creature\nEnchanted creature is a Spirit and can't attack or block. (It loses all other creature types.)")
    // chunk 01
    val smithAnew = Cards.invent("Smith Anew", "{2}{W}", "Enchantment", "When this enchantment enters, return target Equipment card from your graveyard to the battlefield.\nDuring your turn, you may activate equip abilities any time you could cast an instant.\nYou may pay {0} rather than pay the equip cost of the first equip ability you activate during each of your turns.")
    // chunk 01
    val pellShadowsBane = Cards.invent("Pell, Shadow's Bane", "{W}", "Legendary Creature — Halfling Citizen", "{W/B}{W/B}: If Pell is a Citizen, it becomes a Halfling Scout with base power and toughness 2/3 and lifelink.\n{B}{B}{B}: If Pell is a Scout, it becomes a Halfling Rogue with \"Whenever this creature deals combat damage to a player, that player loses the game if the Ring has tempted you four or more times this game. Otherwise, the Ring tempts you.\"", "1/2")
    // chunk 01
    val mithrandelTheRadiant = Cards.invent("Mithrandel the Radiant", "{3}{W}{W}", "Legendary Creature — Avatar Wizard", "Flash\nYou may cast legendary spells and artifact spells as though they had flash.\nIf a legendary permanent or an artifact entering or leaving the battlefield causes a triggered ability of a permanent you control to trigger, that ability triggers an additional time.", "4/5")
    // chunk 01
    val halflingsPrick = Cards.invent("Halfling's Prick", "{1}{W}", "Instant", "Halfling's Prick deals X damage to target creature, where X is the number of creatures you control plus the number of Foods you control.")
    // chunk 01
    val windrovarHorizonWatcher = Cards.invent("Windrovar, Horizon Watcher", "{4}{W}", "Legendary Creature — Bird Noble", "Flying\nWhenever two or more creatures you control attack a player, target attacking creature without flying gains flying until end of turn.", "3/4")
    // chunk 02
    val lostToOldSongs = Cards.invent("Lost to Old Songs", "{W}{W}", "Instant", "Put target nonland historic permanent into its owner's library fourth from the top. (Artifacts, legendaries, and Sagas are historic.)")
    // chunk 02
    val quickfootHalfling = Cards.invent("Quickfoot Halfling", "{1}{W}", "Creature — Halfling Peasant", "Whenever this creature attacks, you may sacrifice a Food or pay {2}{W}. When you do, tap target creature an opponent controls.", "1/3")
    // chunk 02
    val rideForWrathRideForRuin = Cards.invent("Ride for Wrath, Ride for Ruin!", "{3}{W}", "Sorcery", "Put a +1/+1 counter on each creature you control. They gain vigilance until end of turn. The Ring tempts you.")
    // chunk 02
    val protectorOfTheWhiteCity = Cards.invent("Protector of the White City", "{3}{W}", "Creature — Human Soldier", "When this creature enters, create a 1/1 white Human Soldier creature token.", "3/3")
    // chunk 02
    val stayedHand = Cards.invent("Stayed Hand", "{1}{W}", "Instant", "Return target spell to its owner's hand.\nDraw a card.")
    // chunk 02
    val daisyOfNorthLane = Cards.invent("Daisy of North Lane", "{2}{W}", "Legendary Creature — Halfling Peasant", "When Daisy enters, create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")\nWhenever you create a token, put a +1/+1 counter on target creature you control other than Daisy.", "1/1")
    // chunk 02
    val hamfastTheSteadfast = Cards.invent("Hamfast the Steadfast", "{1}{W}", "Legendary Creature — Halfling Peasant", "Flash\nWhen Hamfast enters, choose up to one target permanent card in your graveyard that was put there from the battlefield this turn. Return it to your hand. Then the Ring tempts you.", "2/1")
    // chunk 02
    val elevensesFeast = Cards.invent("Elevenses Feast", "{2}{W}", "Instant", "Up to two target creatures each get +2/+1 until end of turn. Create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")")
    // chunk 02
    val hamletBounder = Cards.invent("Hamlet Bounder", "{1}{W}", "Creature — Halfling Soldier", "Vigilance\nWhen this creature enters, you may sacrifice a token. When you do, exile target creature an opponent controls until this creature leaves the battlefield.", "2/2")
    // chunk 02
    val slipIntoShadow = Cards.invent("Slip Into Shadow", "{1}{W}", "Instant", "Exile target creature you own, then return it to the battlefield under your control. The Ring tempts you.")
    // chunk 02
    val soldierOfThePaleHost = Cards.invent("Soldier of the Pale Host", "{3}{W}", "Creature — Spirit Soldier", "Flash\nFlying\nWhen this creature enters, target creature gets +2/+0 until end of turn.", "2/2")
    // chunk 02
    val stalwartsOfTheRiverFort = Cards.invent("Stalwarts of the River Fort", "{4}{W}", "Creature — Human Soldier", "When this creature enters, the Ring tempts you.\nWhenever you draw your second card each turn, put a +1/+1 counter on this creature.", "4/3")
    // chunk 02
    val layOfLuthiel = Cards.invent("Lay of Lúthiel", "{3}{W}{W}", "Enchantment — Saga", "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)\nI — Target creature you control gains indestructible for as long as you control this Saga.\nII — Return target creature card from your graveyard to the battlefield.\nIII — Up to two target creatures you control each gain lifelink until end of turn.")
    // chunk 02
    val tookGleaner = Cards.invent("Took Gleaner", "{1}{W}", "Creature — Halfling Peasant", "When this creature dies, the Ring tempts you.", "2/1")
    // chunk 02
    val warOfTheAncientAlliance = Cards.invent("War of the Ancient Alliance", "{3}{W}", "Enchantment — Saga", "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)\nI, II — Search your library for a legendary creature card, reveal it, put it into your hand, then shuffle.\nIII — Creatures you control gain double strike until end of turn. The Ring tempts you.")
    // chunk 02
    val eastfoldRider = Cards.invent("Eastfold Rider", "{1}{W}", "Creature — Human Knight", "Sacrifice this creature: Destroy target artifact or enchantment. Activate only as a sorcery.", "3/1")
    // chunk 02
    val noneShallCross = Cards.invent("None Shall Cross!", "{W}", "Instant", "Destroy target creature that blocked or was blocked by a legendary creature this turn.")
    // chunk 02
    val marmadasMasterOfTheMarish = Cards.invent("Marmadas, Master of the Marish", "{3}{W}", "Legendary Creature — Halfling Citizen", "Whenever Marmadas or another nontoken creature you control with power 2 or less enters, create a 1/1 white Halfling creature token.\nTap two other untapped Halflings you control: Marmadas gets +2/+0 and gains lifelink until end of turn.", "2/4")
    // chunk 02
    val bingoSteadfastHero = Cards.invent("Bingo, Steadfast Hero", "{1}{W}", "Legendary Creature — Halfling Warrior", "Whenever Bingo enters or attacks, you may attach target Equipment you control with mana value 2 or 3 to Bingo.\nDuring your turn, prevent all damage that would be dealt to Bingo.", "2/2")
    // chunk 02
    val olorinPaleRider = Cards.invent("Olórin, Pale Rider", "{3}{W}", "Legendary Creature — Avatar Wizard", "Vigilance\nWhenever you cast a spell, creatures you control get +1/+0 until end of turn. Scry 1.\nWhen Olórin dies, you may put it into its owner's library fifth from the top.", "3/3")
    // chunk 02
    val eagleOfRescue = Cards.invent("Eagle of Rescue", "{4}{W}{W}", "Creature — Bird Soldier", "Flying\nWhen this creature enters, put an indestructible counter on another target creature you control. Draw a card if that creature's power is 2 or less.", "5/5")
    // chunk 03
    val evenstarsGift = Cards.invent("Evenstar's Gift", "{3}{U}", "Sorcery", "This spell costs {1} less to cast if you control two or more legendary creatures.\nScry 2, then draw two cards.")
    // chunk 03
    val theWashingSong = Cards.invent("The Washing Song", "{3}{U}", "Enchantment — Saga", "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)\nI, II — Draw two cards, then discard a card.\nIII — Shuffle any number of target cards from your graveyard into your library. Add {U}{U}.")
    // chunk 03
    val bewitchingHerbcraft = Cards.invent("Bewitching Herbcraft", "{1}{U}", "Enchantment — Aura", "Enchant creature\nWhen this Aura enters, tap enchanted creature.\nEnchanted creature has \"If this creature would untap during your untap step, remove a +1/+1 counter from it instead. If you do, untap it.\" (Otherwise, it doesn't untap.)")
    // chunk 03
    val hobFerrowInnSwindler = Cards.invent("Hob Ferrow, Inn Swindler", "{1}{U}", "Legendary Creature — Human Rogue", "Whenever Hob Ferrow becomes blocked, choose one —\n• Create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")\n• Target opponent gains control of target Horse you control. If they do, remove Hob Ferrow from combat and create three Treasure tokens.", "2/1")
    // chunk 03
    val partyVanishing = Cards.invent("Party Vanishing", "{U}", "Sorcery", "Draw a card. The Ring tempts you.")
    // chunk 03
    val carriedOnTheBreeze = Cards.invent("Carried on the Breeze", "{1}{U}", "Instant", "You may cast spells this turn as though they had flash.\nDraw a card.")
    // chunk 03
    val captainOfTheCorsairs = Cards.invent("Captain of the Corsairs", "{2}{U}", "Creature — Human Pirate", "{1}, {T}: Draw a card, then discard a card.", "2/3")
    // chunk 03
    val councilsMusing = Cards.invent("Council's Musing", "{1}{U}", "Instant", "Draw a card.\nWhenever you scry, if you control an Island, you may exile this card from your graveyard. If you do, draw a card.")
    // chunk 03
    val misleadTheHerald = Cards.invent("Mislead the Herald", "{U}", "Instant", "Target creature gets -3/-0 until end of turn.\nAmass Orcs 1. (Put a +1/+1 counter on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)")
    // chunk 03
    val terribleAsTheTempest = Cards.invent("Terrible as the Tempest", "{2}{U}", "Instant", "Target creature has base power and toughness 5/5 until end of turn. The Ring tempts you.")
    // chunk 03
    val elrohirLordOfTheValley = Cards.invent("Elrohir, Lord of the Valley", "{2}{U}", "Legendary Creature — Elf Noble", "Whenever Elrohir or another creature you control enters, scry 1. If this is the second time this ability has resolved this turn, the Ring tempts you.", "3/2")
    // chunk 03
    val tharkunFriendOfTheHills = Cards.invent("Tharkun, Friend of the Hills", "{3}{U}", "Legendary Creature — Avatar Wizard", "Flash\nYou may cast sorcery spells as though they had flash.\nWhenever the Ring tempts you, if you chose a creature other than Tharkun as your Ring-bearer, draw a card.", "2/4")
    // chunk 03
    val gloriousGust = Cards.invent("Glorious Gust", "{1}{U}", "Instant", "Counter target creature spell. If it was a legendary spell, the Ring tempts you.")
    // chunk 03
    val lilywenRiverDaughter = Cards.invent("Lilywen, River-Daughter", "{1}{U}", "Legendary Creature — Nymph", "{T}: Move a counter of each kind not on Lilywen from another target permanent you control onto Lilywen.\n{U}, {T}: Move one or more counters from Lilywen onto another target permanent you control. If you do, draw a card.", "1/3")
    // chunk 03
    val harborNavigatorOfTheWest = Cards.invent("Harbor Navigator of the West", "{2}{U}", "Creature — Elf Pilot", "Flash\nWhen this creature enters, scry 1.", "3/2")
    // chunk 03
    val elvenRopeKnots = Cards.invent("Elven Rope Knots", "{1}{U}", "Instant", "Tap target creature. Scry 1.\nDraw a card.")
    // chunk 03
    val horsesOfTheLoudwater = Cards.invent("Horses of the Loudwater", "{3}{U}{U}", "Sorcery", "Return up to two target creatures to their owners' hands. Scry 1. The Ring tempts you.")
    // chunk 03
    val berethielOfTheHealingHall = Cards.invent("Berethiel of the Healing Hall", "{2}{U}", "Legendary Creature — Human Cleric", "{T}: Untap another target permanent.\n{T}: Untap two other target legendary creatures.", "1/4")
    // chunk 03
    val isolationAtTheTower = Cards.invent("Isolation at the Tower", "{3}{U}", "Instant", "Put target creature into its owner's library second from the top.")
    // chunk 03
    val riverKingfisherOfTheSouth = Cards.invent("River Kingfisher of the South", "{2}{U}", "Creature — Bird", "Flying\nWhen this creature dies, draw a card.", "2/1")
    // chunk 04
    val knightsOfTheSwanCoast = Cards.invent("Knights of the Swan Coast", "{3}{U}", "Creature — Human Knight", "Whenever you draw your second card each turn, put a +1/+1 counter on this creature.", "3/3")
    // chunk 04
    val goldenWoodRevealed = Cards.invent("Golden Wood Revealed", "{3}{U}{U}", "Sorcery", "Draw three cards.\nIslandcycling {1} ({1}, Discard this card: Search your library for an Island card, reveal it, put it into your hand, then shuffle.)")
    // chunk 04
    val farIsleCalling = Cards.invent("Far Isle Calling", "{1}{U}", "Enchantment", "Whenever you scry, put a verse counter on this enchantment.\n{4}{U}{U}, Exile this enchantment: Draw a card for each verse counter on this enchantment. If it had seven or more verse counters on it, take an extra turn after this one. Activate only as a sorcery.")
    // chunk 04
    val thorondilSwiftSavior = Cards.invent("Thorondil, Swift Savior", "{3}{U}", "Legendary Creature — Bird Soldier", "Flying\nWhenever Thorondil deals combat damage to a player, exile up to one target creature you own, then return it to the battlefield under your control.", "3/3")
    // chunk 04
    val silverlodeWatcher = Cards.invent("Silverlode Watcher", "{1}{U}", "Creature — Elf Scout", "Whenever you scry, this creature gets +1/+0 until end of turn and can't be blocked this turn. This ability triggers only once each turn.", "2/1")
    // chunk 04
    val harborTownSurvivor = Cards.invent("Harbor Town Survivor", "{1}{U}", "Creature — Human Peasant", "{T}: Add one mana of any color. Spend this mana only to cast an instant or sorcery spell.\n{5}{U}, {T}: Target player mills three cards. (They put the top three cards of their library into their graveyard.)", "1/3")
    // chunk 04
    val driveBackTheFoe = Cards.invent("Drive Back the Foe", "{2}{U}{U}", "Instant", "Return target spell or nonland permanent an opponent controls to its owner's hand. You may cast an instant or sorcery spell with equal or lesser mana value from your hand without paying its mana cost.")
    // chunk 04
    val rangersOfTheHiddenVale = Cards.invent("Rangers of the Hidden Vale", "{2}{U}{U}", "Creature — Human Ranger", "Vigilance\nWhen this creature enters, gain control of up to one target creature with lesser power for as long as you control this creature. Then the Ring tempts you.", "3/3")
    // chunk 04
    val curunilTheWhite = Cards.invent("Curunil the White", "{4}{U}", "Legendary Creature — Avatar Wizard", "Ward {2}\nWhenever you cast your second spell each turn, amass Orcs 2. (Put two +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)", "4/4")
    // chunk 04
    val wizardsTrickery = Cards.invent("Wizard's Trickery", "{1}{U}{U}", "Instant", "Counter target spell.\nAmass Orcs 1. (Put a +1/+1 counter on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)")
    // chunk 04
    val scrollOfTheLastKing = Cards.invent("Scroll of the Last King", "{2}{U}", "Enchantment — Saga", "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)\nI — Gain control of up to one target artifact for as long as you control this Saga. The Ring tempts you.\nII — Tap up to two target creatures. Put a stun counter on each of them.\nIII — Draw a card for each tapped creature target opponent controls.")
    // chunk 04
    val soothingOfTheWretch = Cards.invent("Soothing of the Wretch", "{1}{U}", "Instant", "Return target nontoken creature to its owner's hand. The Ring tempts you.")
    // chunk 04
    val stormOfTheWhiteWizard = Cards.invent("Storm of the White Wizard", "{4}{U}{U}", "Enchantment", "Ward {3}\nWhenever you cast your second spell each turn, copy it, except the copy isn't legendary. You may choose new targets for the copy. (A copy of a permanent spell becomes a token.)")
    // chunk 04
    val hemmedInByOrcs = Cards.invent("Hemmed In by Orcs", "{3}{U}", "Sorcery", "Amass Orcs 3, then target player mills X cards, where X is the amassed Army's power. (To amass Orcs 3, put three +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)")
    // chunk 04
    val treasonOfTheTower = Cards.invent("Treason of the Tower", "{2}{U}", "Sorcery", "Put up to one target instant or sorcery card from your graveyard on top of your library.\nAmass Orcs 2. (To amass Orcs 2, put two +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)")
    // chunk 04
    val theLurkerInTheLake = Cards.invent("The Lurker in the Lake", "{3}{U}{U}", "Legendary Creature — Kraken", "The Lurker in the Lake enters tapped with nine stun counters on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)\nWhenever you draw a card during an opponent's turn, create a 1/1 blue Tentacle creature token.\nWhenever a Tentacle you control dies, untap up to one target Kraken and put a stun counter on up to one target nonland permanent.", "9/9")
    // chunk 04
    val reedWind = Cards.invent("Reed-Wind", "{4}{U}", "Creature — Elemental", "Flying\nWhen this creature enters, scry 2.", "3/4")
    // chunk 04
    val elvishSailwright = Cards.invent("Elvish Sailwright", "{2}{U}", "Creature — Elf Pilot", "Whenever this creature attacks, scry 1.\nWhenever you scry, tap up to X target nonland permanents, where X is the number of cards looked at while scrying this way.", "3/2")
    // chunk 04
    val whiteCityGarrison = Cards.invent("White City Garrison", "{3}{U}", "Creature — Human Soldier", "White City Garrison's power is equal to the number of cards in your hand.\nWhenever this creature attacks, you may tap any number of untapped Humans you control. Draw a card for each Human tapped this way.", "*/5")
    // chunk 05
    val bitterUndoing = Cards.invent("Bitter Undoing", "{3}{B}", "Instant", "This spell costs {3} less to cast if it targets a creature that was dealt damage this turn.\nDestroy target creature. Its controller loses 2 life.")
    // chunk 05
    val theDarkBreath = Cards.invent("The Dark Breath", "{2}{B}", "Sorcery", "Creatures your opponents control get -1/-1 until end of turn. The Ring tempts you.")
    // chunk 05
    val lureOfTheRing = Cards.invent("Lure of the Ring", "{1}{B}", "Enchantment", "At the beginning of your upkeep, the Ring tempts you.\nWhenever you choose a creature as your Ring-bearer, you may pay 2 life. If you do, draw a card.")
    // chunk 05
    val spiderPassPatrol = Cards.invent("Spider Pass Patrol", "{4}{B}", "Creature — Orc Soldier", "{1}, {T}, Sacrifice another creature: Draw a card, then create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")", "4/5")
    // chunk 05
    val graspThePrecious = Cards.invent("Grasp the Precious", "{1}{B}{B}", "Sorcery", "Destroy target creature. The Ring tempts you.")
    // chunk 05
    val fenlandCrebain = Cards.invent("Fenland Crebain", "{2}{B}", "Creature — Bird Horror", "Flying\nWhen this creature enters, amass Orcs 2. (Put two +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)", "1/1")
    // chunk 05
    val southronVanguard = Cards.invent("Southron Vanguard", "{1}{B}", "Creature — Human Warrior", "When this creature dies, amass Orcs 1. (Put a +1/+1 counter on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)", "2/1")
    // chunk 05
    val smeagorPatientPlotter = Cards.invent("Smeagor, Patient Plotter", "{1}{B}", "Legendary Creature — Halfling Horror", "When Smeagor leaves the battlefield, the Ring tempts you.\n{B}, Sacrifice a creature: Return this card from your graveyard to your hand. Activate only as a sorcery.", "3/1")
    // chunk 05
    val wretchsBite = Cards.invent("Wretch's Bite", "{B}", "Instant", "Target creature gets -2/-2 until end of turn.\n{3}{B}, Exile this card from your graveyard: The Ring tempts you. Activate only as a sorcery.")
    // chunk 05
    val shagulOfTheDeadTower = Cards.invent("Shagul of the Dead Tower", "{1}{B}", "Legendary Creature — Orc Soldier", "Whenever a Goblin or Orc you control deals combat damage to a player, you may sacrifice it. When you do, choose one —\n• Draw a card.\n• Create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")", "2/2")
    // chunk 05
    val gorthakMorgulLieutenant = Cards.invent("Gorthak, Morgul Lieutenant", "{3}{B}", "Legendary Creature — Human Soldier", "When Gorthak enters, amass Orcs 1. (Put a +1/+1 counter on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)\nCreature tokens you control have deathtouch.", "3/3")
    // chunk 05
    val wormwoodTheWhisperer = Cards.invent("Wormwood the Whisperer", "{2}{B}", "Legendary Creature — Human Advisor", "Your opponents can't gain life.\n{T}, Sacrifice another creature: Target player loses 1 life. If the sacrificed creature was legendary, amass Orcs 2.", "1/4")
    // chunk 05
    val grundorTheGatebreaker = Cards.invent("Grundor, the Gatebreaker", "{3}{B}", "Legendary Artifact — Vehicle", "Trample\nAs long as it's your turn and you control an Army, Grundor is an artifact creature.\nCrew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)", "5/5")
    // chunk 05
    val hauntOfTheMereOfGhosts = Cards.invent("Haunt of the Mere of Ghosts", "{B}", "Creature — Nightmare Elf", "When this creature enters, scry 1.\n{2}{B}: Return this card from your graveyard to the battlefield tapped. Activate only if you control a legendary creature.", "1/1")
    // chunk 05
    val theKingsFatefulStrike = Cards.invent("The King's Fateful Strike", "{2}{B}{B}", "Legendary Instant", "(You may cast a legendary instant only if you control a legendary creature or planeswalker.)\nDestroy target creature. If its controller has more than four cards in hand, they exile cards from their hand equal to the difference.")
    // chunk 05
    val lashOfTheFireDemon = Cards.invent("Lash of the Fire Demon", "{B}", "Sorcery", "As an additional cost to cast this spell, sacrifice a creature or pay {4}.\nDestroy target creature.")
    // chunk 05
    val hildaBracegirdlePott = Cards.invent("Hilda Bracegirdle-Pott", "{2}{B}", "Legendary Creature — Halfling Citizen", "Flash\nMenace\nWhen Hilda enters, exile target creature card from an opponent's graveyard that was put there from the battlefield this turn, then create X Treasure tokens, where X is the exiled card's power.", "2/3")
    // chunk 05
    val marchFromTheDarkGate = Cards.invent("March from the Dark Gate", "{1}{B}", "Enchantment", "When this enchantment enters and whenever an Army you control attacks, amass Orcs 1. (Put a +1/+1 counter on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)")
    // chunk 05
    val gloomwoodBats = Cards.invent("Gloomwood Bats", "{3}{B}", "Creature — Bat", "Flying\nWhenever you create or sacrifice a token, each opponent loses 1 life.", "2/3")
    // chunk 05
    val darkLandMuster = Cards.invent("Dark Land Muster", "{1}{B}", "Sorcery", "You draw a card and you lose 1 life.\nAmass Orcs 1. (Put a +1/+1 counter on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)")
    // chunk 05
    val darkLandTrebuchet = Cards.invent("Dark Land Trebuchet", "{2}{B}", "Artifact Creature — Wall", "Defender\nWhenever you attack with one or more Goblins and/or Orcs, create a 2/1 colorless Construct artifact creature token with flying named Ballistic Boulder that's tapped and attacking. Sacrifice that token at end of combat.", "1/4")
    // chunk 06
    val witchBladeWound = Cards.invent("Witch-Blade Wound", "{1}{B}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets -3/-0 and has \"At the beginning of your upkeep, exile this creature unless you pay 2 life.\"")
    // chunk 06
    val wretchedEnd = Cards.invent("Wretched End", "{1}{B}", "Instant", "As an additional cost to cast this spell, sacrifice a creature.\nDraw two cards. If the sacrificed creature was legendary, draw three cards instead.")
    // chunk 06
    val ringwight = Cards.invent("Ringwight", "{2}{B}", "Creature — Wraith Knight", "Deathtouch\nWhen this creature enters, the Ring tempts you.\nWhenever the Ring tempts you, put a +1/+1 counter on each Wraith you control.\nA deck can have up to nine cards named Ringwight.", "1/2")
    // chunk 06
    val oathOfTheSilentHost = Cards.invent("Oath of the Silent Host", "{3}{B}", "Enchantment — Saga", "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)\nI — You and target opponent each create a Food token.\nII — Each opponent loses 3 life. Create a Treasure token.\nIII — Create three tapped 1/1 white Spirit creature tokens with flying.")
    // chunk 06
    val oneRingToBindThemAll = Cards.invent("One Ring to Bind Them All", "{2}{B}{B}", "Enchantment — Saga", "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)\nI — The Ring tempts you, then each player mills cards equal to your Ring-bearer's power.\nII — Destroy all nonlegendary creatures.\nIII — Each opponent loses 1 life for each creature card in that player's graveyard.")
    // chunk 06
    val orcishArrowmasters = Cards.invent("Orcish Arrowmasters", "{1}{B}", "Creature — Orc Archer", "Flash\nWhen this creature enters and whenever an opponent draws a card except the first one they draw in each of their draw steps, this creature deals 1 damage to any target. Then amass Orcs 1.", "1/1")
    // chunk 06
    val orcishPoultice = Cards.invent("Orcish Poultice", "{1}{B}", "Instant", "Target creature gains your choice of lifelink or indestructible until end of turn.\nAmass Orcs 1. (Put a +1/+1 counter on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)")
    // chunk 06
    val hamfastsDesperateRescue = Cards.invent("Hamfast's Desperate Rescue", "{B}", "Sorcery", "Return target creature card from your graveyard to your hand. The Ring tempts you.")
    // chunk 06
    val gorthaurTheNecromancer = Cards.invent("Gorthaur, the Necromancer", "{3}{B}{B}", "Legendary Creature — Avatar Horror", "Menace\nWhenever Gorthaur attacks, exile target creature card from your graveyard. Create a tapped and attacking token that's a copy of that card, except it's a 3/3 black Wraith with menace. At the beginning of the next end step, exile that token unless Gorthaur is your Ring-bearer.", "4/4")
    // chunk 06
    val shadowOfTheFoe = Cards.invent("Shadow of the Foe", "{3}{B}{B}{B}", "Sorcery", "Exile all creature cards from target player's graveyard. You may cast spells from among those cards for as long as they remain exiled, and mana of any type can be spent to cast them.")
    // chunk 06
    val spidersAmbush = Cards.invent("Spider's Ambush", "{B}", "Instant", "Target creature gets +1/+2 and gains deathtouch until end of turn. Create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")")
    // chunk 06
    val snarlingWolfHound = Cards.invent("Snarling Wolf-Hound", "{3}{B}", "Creature — Wolf", "Menace (This creature can't be blocked except by two or more creatures.)\nAs long as you control a Goblin or Orc, this creature gets +1/+0.", "3/4")
    // chunk 06
    val theTormentOfTheWretch = Cards.invent("The Torment of the Wretch", "{3}{B}", "Sorcery", "Target opponent reveals their hand. You choose a nonland card from it. That player discards that card.\nAmass Orcs 2. (Put two +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)")
    // chunk 06
    val trollOfTheDeepHalls = Cards.invent("Troll of the Deep Halls", "{5}{B}", "Creature — Troll", "This creature can't be blocked except by three or more creatures.\nSwampcycling {1} ({1}, Discard this card: Search your library for a Swamp card, reveal it, put it into your hand, then shuffle.)", "6/5")
    // chunk 06
    val urukBerserker = Cards.invent("Uruk Berserker", "{2}{B}", "Creature — Orc Berserker", "When this creature enters, the Ring tempts you.", "3/2")
    // chunk 06
    val ravenousFellSteed = Cards.invent("Ravenous Fell Steed", "{4}{B}{B}", "Creature — Drake Beast", "Flying\nWhen this creature enters, each opponent sacrifices a creature of their choice. Create a Food token for each creature sacrificed this way. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")", "4/4")
    // chunk 06
    val witchLordOfTheNorth = Cards.invent("Witch-lord of the North", "{3}{B}{B}", "Legendary Creature — Wraith Noble", "Flying\nWhenever one or more creatures deal combat damage to you, each opponent sacrifices a creature of their choice that dealt combat damage to you this turn. The Ring tempts you.\nDiscard a card: Witch-lord of the North gains indestructible until end of turn. Tap him.", "5/3")
    // chunk 06
    val ringboundWraiths = Cards.invent("Ringbound Wraiths", "{4}{B}{B}", "Creature — Wraith Knight", "When this creature enters, target creature an opponent controls gets -3/-3 until end of turn. If that creature is legendary, its controller loses 3 life.\nWhen the Ring tempts you, return this card from your graveyard to your hand.", "5/5")
    // chunk 06
    val smeagorSchemingGuide = Cards.invent("Smeagor, Scheming Guide", "{1}{B}", "Legendary Creature — Halfling Horror", "Whenever Smeagor attacks, look at the top two cards of your library, put them back in any order, then choose land or nonland. An opponent guesses whether the top card of your library is the chosen kind. Reveal that card. If they guessed right, remove Smeagor from combat. Otherwise, you draw a card and Smeagor can't be blocked this turn.", "2/1")
    // chunk 06
    val witchLordBringerOfRuin = Cards.invent("Witch-lord, Bringer of Ruin", "{4}{B}{B}", "Legendary Creature — Wraith Noble", "Flying\nWhenever Witch-lord attacks, defending player sacrifices a creature with the least power among creatures they control.", "5/3")
    // chunk 06
    val wolfRider = Cards.invent("Wolf Rider", "{4}{B}", "Creature — Orc Warrior", "Menace\nOther Orcs and Goblins you control have menace.\nAt the beginning of combat on your turn, amass Orcs 2. (Put two +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)", "4/3")
    // chunk 07
    val battleWornGoblin = Cards.invent("Battle-Worn Goblin", "{1}{R}", "Creature — Goblin Warrior", "Whenever this creature becomes blocked, it deals 1 damage to each creature blocking it.", "2/2")
    // chunk 07
    val bookOfTheDeepRecords = Cards.invent("Book of the Deep Records", "{2}{R}", "Enchantment — Saga", "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)\nI — Amass Orcs 1. (Put a +1/+1 counter on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)\nII — Amass Orcs 2.\nIII — Creatures you control get +1/+0 and gain menace until end of turn.")
    // chunk 07
    val sunderingOfTheCompany = Cards.invent("Sundering of the Company", "{1}{R}", "Sorcery", "Target creature an opponent controls deals damage equal to its power to another target creature that player controls. The Ring tempts you.")
    // chunk 07
    val thrownIntoTheFlame = Cards.invent("Thrown into the Flame", "{1}{R}", "Instant", "Choose one —\n• Thrown into the Flame deals 1 damage to each of up to two target creatures.\n• Exile target artifact.")
    // chunk 07
    val showOfPower = Cards.invent("Show of Power", "{1}{R}{R}", "Instant", "This spell can't be copied.\nCopy any number of target instant and/or sorcery spells. You may choose new targets for the copies.")
    // chunk 07
    val eothainMarshalOfThePlains = Cards.invent("Eothain, Marshal of the Plains", "{2}{R}{R}", "Legendary Creature — Human Knight", "Haste\nWhenever one or more other attacking legendary creatures you control die, untap all creatures you control. After this phase, there is an additional combat phase. This ability triggers only once each turn.", "4/4")
    // chunk 07
    val elfhelmOfTheRiddermark = Cards.invent("Elfhelm of the Riddermark", "{4}{R}", "Legendary Creature — Human Knight", "Haste\nWhenever Elfhelm attacks, if you control a creature with the greatest power among creatures on the battlefield, create a 1/1 white Human Soldier creature token.", "5/4")
    // chunk 07
    val ironHillsFlamesmith = Cards.invent("Iron Hills Flamesmith", "{1}{R}", "Creature — Dwarf Artificer", "Whenever you cast an instant or sorcery spell, this creature deals 1 damage to each opponent.", "2/1")
    // chunk 07
    val grimboldLordOfWestfold = Cards.invent("Grimbold, Lord of Westfold", "{3}{R}", "Legendary Creature — Human Soldier", "Whenever Grimbold or another Human you control enters, creatures you control get +1/+0 until end of turn.", "3/3")
    // chunk 07
    val fallOfTheRiverIsle = Cards.invent("Fall of the River Isle", "{2}{R}", "Enchantment", "Whenever a creature an opponent controls is dealt excess noncombat damage, amass Orcs X, where X is that excess damage. (Put X +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)\n{7}{R}: This enchantment deals 7 damage to target creature.")
    // chunk 07
    val flameFuryFoes = Cards.invent("Flame, Fury, Foes!", "{X}{R}", "Sorcery", "Damage can't be prevented this turn. Flame, Fury, Foes! deals X damage to target creature and 1 damage to each other creature with the same controller.")
    // chunk 07
    val burningInscription = Cards.invent("Burning Inscription", "{2}{R}", "Enchantment", "When this enchantment enters, the Ring tempts you.\nWhenever you cast an instant or sorcery spell, this enchantment deals 2 damage to each opponent.")
    // chunk 07
    val fireOfTheWhiteTower = Cards.invent("Fire of the White Tower", "{3}{R}", "Sorcery", "Destroy target artifact or land. Creatures without flying can't block this turn.")
    // chunk 07
    val raidOfOrcs = Cards.invent("Raid of Orcs", "{3}{R}", "Sorcery", "Amass Orcs 2. When you do, Raid of Orcs deals X damage to target creature an opponent controls, where X is the amassed Army's power. (To amass Orcs 2, put two +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)")
    // chunk 07
    val doriCounterOfKills = Cards.invent("Dori, Counter of Kills", "{3}{R}", "Legendary Creature — Dwarf Warrior", "Trample\nWhenever a creature an opponent controls dies, Dori deals 1 damage to that creature's controller.", "4/3")
    // chunk 07
    val dorisAxe = Cards.invent("Dori's Axe", "{2}{R}", "Artifact — Equipment", "Equipped creature gets +3/+0.\nAs long as equipped creature is legendary, it has menace. (It can't be blocked except by two or more creatures.)\nEquip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)")
    // chunk 07
    val dorisFury = Cards.invent("Dori's Fury", "{1}{R}", "Instant", "Target creature gets +3/+2 until end of turn. If it's legendary, it also gains trample until end of turn.")
    // chunk 07
    val noriDwarfEmissary = Cards.invent("Nori, Dwarf Emissary", "{2}{R}", "Legendary Creature — Dwarf Advisor", "Whenever you cast a historic spell, create a Treasure token. This ability triggers only once each turn. (Artifacts, legendaries, and Sagas are historic.)\n{T}, Sacrifice a Treasure: Goad target creature. (Until your next turn, that creature attacks each combat if able and attacks a player other than you if able.)", "3/3")
    // chunk 07
    val goblinFlameleaper = Cards.invent("Goblin Flameleaper", "{1}{R}", "Creature — Goblin Warrior", "{1}{R}: This creature gets +1/+0 until end of turn.\nWhen this creature dies, it deals damage equal to its power to target creature an opponent controls.", "1/1")
    // chunk 07
    val ugrakBrashInstigator = Cards.invent("Ugrak, Brash Instigator", "{2}{R}", "Legendary Creature — Goblin Soldier", "When Ugrak enters, amass Orcs 2. When you do, until end of turn, gain control of target nonlegendary creature an opponent controls with power less than or equal to the amassed Army's power. Untap that creature. It gains haste until end of turn.", "1/1")
    // chunk 07
    val southronSpearmaster = Cards.invent("Southron Spearmaster", "{2}{R}", "Creature — Human Warrior", "Reach\nAt the beginning of combat on your turn, another target creature you control gets +1/+0 until end of turn.", "2/3")
    // chunk 08
    val fellTheOldForest = Cards.invent("Fell the Old Forest", "{3}{R}{R}", "Sorcery", "Sacrifice any number of lands. Reveal the top X cards of your library, where X is the number of lands sacrificed this way. Choose any number of artifact and/or land cards revealed this way. Put all nonland cards chosen this way onto the battlefield, then put all land cards chosen this way onto the battlefield tapped, then put the rest on the bottom of your library in a random order.")
    // chunk 08
    val makeshiftClub = Cards.invent("Makeshift Club", "{1}{R}", "Instant", "As an additional cost to cast this spell, sacrifice an artifact or creature.\nMakeshift Club deals 4 damage to any target.")
    // chunk 08
    val deepMineMarauder = Cards.invent("Deep Mine Marauder", "{R}{R}", "Creature — Goblin Warrior", "Double strike\nWhenever a Goblin or Orc you control deals combat damage to a player, exile the top card of your library. You may play that card this turn.", "1/1")
    // chunk 08
    val southernMumak = Cards.invent("Southern Mumak", "{5}{R}", "Creature — Elephant", "Trample\nWhenever this creature attacks, another target creature you control gets +2/+0 and gains trample until end of turn.\nMountaincycling {1} ({1}, Discard this card: Search your library for a Mountain card, reveal it, put it into your hand, then shuffle.)", "6/4")
    // chunk 08
    val hillTrollCrusher = Cards.invent("Hill-troll Crusher", "{3}{R}", "Creature — Troll Soldier", "Trample\nThis creature can't block unless you control a Goblin or Orc.", "4/4")
    // chunk 08
    val feudsEnd = Cards.invent("Feud's End", "{2}{R}", "Sorcery", "As an additional cost to cast this spell, discard a card.\nDraw two cards and create a 1/1 white Human Soldier creature token.")
    // chunk 08
    val rallyAtTheDeepKeep = Cards.invent("Rally at the Deep Keep", "{1}{R}", "Sorcery", "Create two 1/1 white Human Soldier creature tokens. Humans you control gain haste until end of turn.")
    // chunk 08
    val wanderersFirebrand = Cards.invent("Wanderer's Firebrand", "{R}", "Sorcery", "Wanderer's Firebrand deals 2 damage to any target. The Ring tempts you.")
    // chunk 08
    val relentlessHorselords = Cards.invent("Relentless Horselords", "{3}{R}", "Creature — Human Knight", "When this creature enters, the Ring tempts you.", "4/3")
    // chunk 08
    val risingOfTheDawn = Cards.invent("Rising of the Dawn", "{2}{R}", "Enchantment", "Creatures you control have haste.\nLegendary creatures you control get +1/+0.")
    // chunk 08
    val horselordLancer = Cards.invent("Horselord Lancer", "{R}", "Creature — Human Knight", "Menace (This creature can't be blocked except by two or more creatures.)\nWhen this creature dies, the Ring tempts you.", "1/1")
    // chunk 08
    val stormTheHall = Cards.invent("Storm the Hall", "{R}", "Instant", "Target creature gets +1/+0 and gains first strike until end of turn. If it's a Goblin or Orc, it also gains haste until end of turn.")
    // chunk 08
    val smiteTheUndying = Cards.invent("Smite the Undying", "{1}{R}", "Instant", "Smite the Undying deals 3 damage to target creature. That creature loses indestructible until end of turn. If that creature would die this turn, exile it instead.")
    // chunk 08
    val spitefulThievery = Cards.invent("Spiteful Thievery", "{X}{R}{R}", "Enchantment", "When this enchantment enters, it deals X damage to each creature.\nWhenever one or more creatures your opponents control die, you create a Treasure token. This ability triggers only once each turn.")
    // chunk 08
    val swarmingOfTheDeepMines = Cards.invent("Swarming of the Deep Mines", "{2}{R}", "Sorcery", "Create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")\nAmass Orcs 2. (Put two +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)")
    // chunk 08
    val outAndHomeAgain = Cards.invent("Out and Home Again", "{3}{R}{R}", "Enchantment — Saga", "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)\nI — Up to one target creature can't block for as long as you control this Saga. The Ring tempts you.\nII — Search your library for a Mountain card, put it onto the battlefield, then shuffle.\nIII — Create Smaug, a legendary 6/6 red Dragon creature token with flying, haste, and \"When Smaug dies, create fourteen Treasure tokens.\"")
    // chunk 08
    val warbeastOfTheAshPlain = Cards.invent("Warbeast of the Ash Plain", "{4}{R}", "Creature — Beast", "Whenever this creature or another creature you control with power 4 or greater dies, amass Orcs 2. (Put two +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)", "5/4")
    // chunk 08
    val assaultOnTheRiverCity = Cards.invent("Assault on the River City", "{X}{R}{R}{R}", "Sorcery", "Amass Orcs X, then Goblins and Orcs you control gain double strike and haste until end of turn. (To amass Orcs X, put X +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)")
    // chunk 08
    val firesOfTheBurningPeak = Cards.invent("Fires of the Burning Peak", "{2}{R}", "Legendary Enchantment", "When Fires of the Burning Peak enters, it deals 2 damage to target creature an opponent controls. Destroy all Equipment attached to that creature.\n{2}{R}: Exile the top card of your library. You may play that card this turn. When you play a card this way, Fires of the Burning Peak deals 2 damage to each player.")
    // chunk 08
    val ridersOfThePlains = Cards.invent("Riders of the Plains", "{6}{R}", "Creature — Human Knight", "Affinity for Humans (This spell costs {1} less to cast for each Human you control.)\nTrample, haste\nAt the beginning of your end step, if this creature attacked this turn, return it to its owner's hand. If you do, create a number of 1/1 white Human Soldier creature tokens equal to its toughness.", "7/4")
    // chunk 09
    val hillHolePorter = Cards.invent("Hill Hole Porter", "{3}{G}", "Creature — Dwarf", "Whenever this creature attacks, it gets +X/+X until end of turn, where X is the number of legendary creatures you control.", "4/4")
    // chunk 09
    val oldSingersSong = Cards.invent("Old Singer's Song", "{1}{G}", "Instant", "Target creature you control gets +1/+1 and gains hexproof until end of turn. The Ring tempts you. (A creature with hexproof can't be the target of spells or abilities your opponents control.)")
    // chunk 09
    val riverbankFarmer = Cards.invent("Riverbank Farmer", "{2}{G}", "Creature — Halfling Peasant", "When this creature enters or leaves the battlefield, create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")", "1/1")
    // chunk 09
    val amrothTheWise = Cards.invent("Amroth the Wise", "{3}{G}", "Legendary Creature — Elf Noble", "Whenever you attack with one or more Elves, scry 1.\nWhenever you scry, Amroth gets +1/+1 until end of turn for each card looked at while scrying this way.", "3/3")
    // chunk 09
    val wanderingCompanyElves = Cards.invent("Wandering Company Elves", "{2}{G}", "Creature — Elf Warrior", "Whenever you scry, put a +1/+1 counter on this creature. This ability triggers only once each turn.", "3/2")
    // chunk 09
    val merryHeartedHalfling = Cards.invent("Merry-Hearted Halfling", "{G}", "Creature — Halfling Citizen", "{T}: Add {C}.\n{T}: Add one mana of any color. Spend this mana only to cast a legendary spell, and that spell can't be countered.", "1/2")
    // chunk 09
    val northernRangers = Cards.invent("Northern Rangers", "{3}{G}", "Creature — Human Ranger", "Landfall — Whenever a land you control enters, if you don't control a Ring-bearer, the Ring tempts you.", "4/4")
    // chunk 09
    val elvenChoir = Cards.invent("Elven Choir", "{3}{G}", "Enchantment", "You may look at the top card of your library any time.\nYou may cast creature spells from the top of your library.\nCreatures you control have \"{T}: Add one mana of any color.\"")
    // chunk 09
    val elvenForesight = Cards.invent("Elven Foresight", "{G}", "Sorcery", "Scry 3, then you may reveal the top card of your library. If a creature card is revealed this way, draw a card.")
    // chunk 09
    val wrathfulHuorn = Cards.invent("Wrathful Huorn", "{4}{G}", "Creature — Treefolk", "Trample\nWhen this creature enters, the Ring tempts you.", "4/5")
    // chunk 09
    val entishRenewal = Cards.invent("Entish Renewal", "{2}{G}", "Instant", "Sacrifice a land. Search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle. If you control a creature with power 4 or greater, instead search your library for up to three basic land cards, put them onto the battlefield tapped, then shuffle.")
    // chunk 09
    val entsWrath = Cards.invent("Ent's Wrath", "{1}{G}", "Sorcery", "Put a +1/+1 counter on target creature you control if its power is 4 or greater. Then that creature gets +1/+1 until end of turn and fights target creature you don't control.")
    // chunk 09
    val fallOfTheElvenKing = Cards.invent("Fall of the Elven King", "{1}{G}", "Enchantment — Saga", "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)\nI — Scry 2.\nII — Put two +1/+1 counters on target creature you control.\nIII — Until end of turn, target creature you control gains \"When this creature dies, draw two cards.\" Then that creature fights up to one other target creature.")
    // chunk 09
    val bregaladTreeShepherd = Cards.invent("Bregalad, Tree Shepherd", "{4}{G}{G}{G}", "Legendary Creature — Treefolk", "Treefolk you control have vigilance.\nWhenever one or more Treefolk you control attack, add twice that much {G}.\nYou don't lose unspent green mana as steps and phases end.", "4/10")
    // chunk 09
    val woodlandElfBow = Cards.invent("Woodland Elf Bow", "{2}{G}", "Artifact — Equipment", "Flash\nWhen this Equipment enters, attach it to target creature you control. Untap that creature.\nEquipped creature gets +1/+2 and has reach.\nEquip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)")
    // chunk 09
    val woodlandElfGuide = Cards.invent("Woodland Elf Guide", "{3}{G}", "Creature — Elf Scout", "When this creature enters, scry 2.", "3/4")
    // chunk 09
    val kindlyEnt = Cards.invent("Kindly Ent", "{5}{G}", "Creature — Treefolk", "Reach\nWhen this creature enters, create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")\nForestcycling {1} ({1}, Discard this card: Search your library for a Forest card, reveal it, put it into your hand, then shuffle.)", "5/7")
    // chunk 09
    val giftOfGoldenStrands = Cards.invent("Gift of Golden Strands", "{3}{G}", "Enchantment — Aura", "Flash\nEnchant creature\nWhen this Aura enters, scry 2.\nEnchanted creature gets +3/+3.")
    // chunk 09
    val erestorDauntlessRescuer = Cards.invent("Erestor, Dauntless Rescuer", "{2}{G}", "Legendary Creature — Elf Noble", "Whenever you scry, choose one and Erestor gets +1/+1 until end of turn.\n• Erestor must be blocked this turn if able.\n• Erestor can't be blocked by more than one creature each combat this turn.", "3/2")
    // chunk 09
    val finalMarchOfTheEnts = Cards.invent("Final March of the Ents", "{6}{G}{G}", "Sorcery", "This spell can't be countered.\nDraw cards equal to the greatest toughness among creatures you control, then put any number of creature cards from your hand onto the battlefield.")
    // chunk 09
    val haldirMasterArcher = Cards.invent("Haldir, Master Archer", "{1}{G}{G}", "Legendary Creature — Elf Archer", "Reach\nWhenever you cast a spell that targets Haldir, put a +1/+1 counter on Haldir.\nWhenever you cast a spell that targets a creature you don't control, Haldir deals damage equal to its power to up to one target creature.", "1/4")
    // chunk 10
    val longRollOfTheEnts = Cards.invent("Long Roll of the Ents", "{G}", "Enchantment — Saga", "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after VI.)\nI, II, III, IV, V, VI — Note a creature type that hasn't been noted for this Saga. When you next cast a creature spell of that type this turn, that creature enters with an additional +1/+1 counter on it.")
    // chunk 10
    val goldenWoodLookout = Cards.invent("Golden Wood Lookout", "{1}{G}", "Creature — Elf Scout", "Whenever this creature attacks, scry 1.", "1/3")
    // chunk 10
    val manyFarewells = Cards.invent("Many Farewells", "{G}", "Sorcery", "Search your library for a basic land card, reveal it, put it into your hand, then shuffle. Create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")")
    // chunk 10
    val fredegarTheSteady = Cards.invent("Fredegar the Steady", "{1}{G}", "Legendary Creature — Halfling Citizen", "Whenever one or more Halflings you control attack a player, create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")", "2/2")
    // chunk 10
    val gloomwoodSpider = Cards.invent("Gloomwood Spider", "{G}", "Creature — Spider", "Deathtouch\nWhenever this creature attacks, target legendary creature you control gains deathtouch until end of turn.", "1/1")
    // chunk 10
    val stillLakeGuardian = Cards.invent("Still-Lake Guardian", "{2}{G}", "Creature — Dwarf Soldier", "When this creature dies, the Ring tempts you.", "4/2")
    // chunk 10
    val mushroomHounds = Cards.invent("Mushroom Hounds", "{1}{G}", "Creature — Dog", "Sacrifice a Food: Put a +1/+1 counter on this creature. It gains vigilance until end of turn. Activate only as a sorcery.", "2/2")
    // chunk 10
    val paladinTook = Cards.invent("Paladin Took", "{2}{G}", "Legendary Creature — Halfling Citizen", "If one or more tokens would be created under your control, those tokens plus an additional Food token are created instead. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")\nSacrifice three Foods: Draw a card.", "2/3")
    // chunk 10
    val halflingsBravery = Cards.invent("Halfling's Bravery", "{G}", "Instant", "You may sacrifice a Food. If you do, target creature gets +4/+4 until end of turn. Otherwise, that creature gets +2/+2 until end of turn.")
    // chunk 10
    val leaflockUpstartEnt = Cards.invent("Leaflock, Upstart Ent", "{4}{G}{G}", "Legendary Creature — Treefolk", "Whenever Leaflock or another Treefolk you control enters, up to two target creatures each get +2/+2 and gain trample until end of turn.", "5/6")
    // chunk 10
    val aiwendilTheBrown = Cards.invent("Aiwendil the Brown", "{2}{G}{G}", "Legendary Creature — Avatar Wizard", "Whenever Aiwendil or another nontoken creature you control enters, look at the top X cards of your library, where X is that creature's mana value. You may reveal a creature card that doesn't share a creature type with a creature you control from among those cards and put it into your hand. Put the rest on the bottom of your library in a random order.", "2/5")
    // chunk 10
    val restoreTheShire = Cards.invent("Restore the Shire", "{1}{G}", "Sorcery", "Return target permanent card from your graveyard to your hand. Create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")")
    // chunk 10
    val theRingGoesEast = Cards.invent("The Ring Goes East", "{3}{G}", "Sorcery", "The Ring tempts you. Then reveal cards from the top of your library until you reveal X land cards, where X is the number of legendary creatures you control. Put those land cards onto the battlefield tapped and the rest on the bottom of your library in a random order.")
    // chunk 10
    val shortcutToTheFerry = Cards.invent("Shortcut to the Ferry", "{1}{G}", "Enchantment", "When this enchantment enters, the Ring tempts you.\nAt the beginning of your end step, if a permanent you controlled left the battlefield this turn, put a +1/+1 counter on target creature you control.")
    // chunk 10
    val volleyOfArrows = Cards.invent("Volley of Arrows", "{2}{G}", "Instant", "Destroy target artifact, enchantment, or creature with flying. Scry 1.")
    // chunk 10
    val roastTheConeys = Cards.invent("Roast the Coneys", "{2}{G}", "Instant", "Target creature you control deals damage equal to its power to target creature you don't control. Create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")")
    // chunk 10
    val woodwosePathfinder = Cards.invent("Woodwose Pathfinder", "{1}{G}", "Creature — Human Shaman", "{T}: Add one mana of any color.\n{6}{G}, {T}: Another target creature gets +3/+3 and gains trample until end of turn.", "1/1")
    // chunk 10
    val rosamundGardner = Cards.invent("Rosamund Gardner", "{3}{G}", "Legendary Creature — Halfling Scout", "When Rosamund enters, create a Food token.\nAt the beginning of your end step, if you sacrificed a Food this turn, you may search your library for a basic land card, put that card onto the battlefield tapped, then shuffle.", "2/4")
    // chunk 10
    val nimrodelGiftGiver = Cards.invent("Nimrodel, Gift-Giver", "{3}{G}{G}", "Legendary Creature — Elf Noble", "Whenever Nimrodel enters or attacks, choose one —\n• Put a +1/+1 counter on another target creature.\n• Create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")\n• Create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")", "4/4")
    // chunk 10
    val greenwoodChanneler = Cards.invent("Greenwood Channeler", "{3}{G}", "Creature — Elf Druid", "At the beginning of combat on your turn, target Elf you control gains trample and gets +X/+X until end of turn, where X is the number of Forests you control.", "3/3")
}
