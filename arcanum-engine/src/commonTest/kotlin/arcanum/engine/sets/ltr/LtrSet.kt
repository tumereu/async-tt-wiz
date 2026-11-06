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
    // chunk 11
    val arathornCompanyLeader = Cards.invent("Arathorn, Company Leader", "{1}{G}{W}", "Legendary Creature — Human Ranger", "Whenever the Ring tempts you, if you chose a creature other than Arathorn as your Ring-bearer, put your choice of a counter from among first strike, vigilance, deathtouch, and lifelink on Arathorn.\nWhenever you put one or more counters on Arathorn, put one of each of those kinds of counters on up to one other target creature.", "3/3")
    // chunk 11
    val elessarTheUniter = Cards.invent("Elessar, the Uniter", "{R}{G}{W}{U}", "Legendary Creature — Human Noble", "Whenever you cast a white spell, create a 1/1 white Human Soldier creature token.\nWhenever you cast a blue spell, scry 2.\nWhenever you cast a red spell, Elessar deals 3 damage to target opponent.\nWhenever you cast a green spell, target creature gets +4/+4 until end of turn.", "5/5")
    // chunk 11
    val luthienMortalQueen = Cards.invent("Lúthien, Mortal Queen", "{1}{G}{W}", "Legendary Creature — Elf Noble", "Lúthien enters with an indestructible counter on her.\n{1}, Remove an indestructible counter from Lúthien: Another target creature gains indestructible until end of turn. Put a +1/+1 counter and a lifelink counter on that creature and a +1/+1 counter and a lifelink counter on Lúthien.", "2/2")
    // chunk 11
    val idrilCelebrindal = Cards.invent("Idril Celebrindal", "{G}{U}", "Legendary Creature — Elf Noble", "Whenever you scry, put a +1/+1 counter on target creature.\n{4}{G}{U}: Scry 2.", "2/2")
    // chunk 11
    val theFireDemonDurinsBane = Cards.invent("The Fire Demon, Durin's Bane", "{5}{B}{R}", "Legendary Creature — Avatar Demon", "This spell costs {1} less to cast for each permanent sacrificed this turn.\nHaste\nThe Fire Demon can't be blocked except by legendary creatures.\nWhen The Fire Demon dies, destroy target artifact or creature an opponent controls.", "7/5")
    // chunk 11
    val bungoRetiredBurglar = Cards.invent("Bungo, Retired Burglar", "{1}{U}{R}", "Legendary Creature — Halfling Rogue", "When Bungo enters or leaves the battlefield, the Ring tempts you.\nWhenever Bungo deals combat damage to a player, create a Treasure token.", "1/3")
    // chunk 11
    val nobBreeInnkeeper = Cards.invent("Nob, Bree Innkeeper", "{2}{G}{W}", "Legendary Creature — Human Peasant", "At the beginning of your end step, if you don't control a Food, create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")", "3/3")
    // chunk 11
    val ecthelionRulingSteward = Cards.invent("Ecthelion, Ruling Steward", "{1}{W}{B}", "Legendary Creature — Human Noble", "At the beginning of your end step, if a creature died under your control this turn, create a 1/1 white Human Soldier creature token.\n{2}, Sacrifice another creature: Each opponent loses 1 life and you gain 1 life.", "2/4")
    // chunk 11
    val gatesOfTheDwarfLord = Cards.invent("Gates of the Dwarf Lord", "{3}{R}{G}", "Legendary Artifact", "Whenever you attack, scry 2, then you may reveal the top card of your library. If a creature card is revealed this way, put it onto the battlefield tapped and attacking. Until your next turn, it gains trample if you control a Dwarf and hexproof if you control an Elf.")
    // chunk 11
    val elrosMasterOfHealing = Cards.invent("Elros, Master of Healing", "{2}{G}{U}", "Legendary Creature — Elf Noble", "Whenever you scry, put a +1/+1 counter on each of up to X target creatures, where X is the number of cards looked at while scrying this way.\nWhenever a creature you control with a +1/+1 counter on it becomes the target of a spell or ability an opponent controls, you may draw a card.", "4/4")
    // chunk 11
    val theodwynFearlessKnight = Cards.invent("Théodwyn, Fearless Knight", "{2}{R}{W}", "Legendary Creature — Human Knight", "Haste\nWhen Théodwyn enters, exile target creature an opponent controls with greater power. Legendary creatures you control gain protection from each of that creature's colors until end of turn.", "3/4")
    // chunk 11
    val damrodPrinceOfIthilien = Cards.invent("Damrod, Prince of Ithilien", "{2}{W}{U}", "Legendary Creature — Human Noble", "At the beginning of your end step, choose an opponent. At the beginning of that player's next end step, you draw a card if they didn't attack you that turn. Otherwise, create three 1/1 white Human Soldier creature tokens.", "3/3")
    // chunk 11
    val flameOfTheSecretFire = Cards.invent("Flame of the Secret Fire", "{1}{U}{R}", "Instant", "Choose one. If you control a Wizard as you cast this spell, you may choose two instead.\n• Target player draws two cards.\n• Destroy target artifact.\n• Flame of the Secret Fire deals 5 damage to target creature.")
    // chunk 11
    val friendlyContest = Cards.invent("Friendly Contest", "{R}{G}", "Instant", "Target creature you control and up to one other target legendary creature you control each deal damage equal to their power to target creature you don't control.")
    // chunk 11
    val drogoBaggins = Cards.invent("Drogo Baggins", "{G}{W}", "Legendary Creature — Halfling Scout", "Whenever Drogo Baggins or another legendary creature you control enters, the Ring tempts you.\nAs long as Drogo Baggins is your Ring-bearer, it must be blocked if able.", "1/3")
    // chunk 11
    val celebrianOfLothlorien = Cards.invent("Celebrían of Lothlórien", "{1}{G}{U}", "Legendary Creature — Elf Noble", "Whenever the Ring tempts you, if you chose a creature other than Celebrían as your Ring-bearer, scry 3.\nWhenever you scry, you may reveal the top card of your library. If a land card is revealed this way, put it onto the battlefield tapped.", "3/3")
    // chunk 12
    val mithrandirTheGrey = Cards.invent("Mithrandir the Grey", "{3}{U}{R}", "Legendary Creature — Avatar Wizard", "Whenever you cast an instant or sorcery spell, choose one that hasn't been chosen —\n• You may tap or untap target permanent.\n• Mithrandir deals 3 damage to each opponent.\n• Copy target instant or sorcery spell you control. You may choose new targets for the copy.\n• Put Mithrandir on top of its owner's library.", "3/4")
    // chunk 12
    val wizardsSanction = Cards.invent("Wizard's Sanction", "{1}{U}{R}", "Sorcery", "Wizard's Sanction deals X damage to target creature, where X is the number of instant and sorcery cards in your graveyard. Excess damage is dealt to that creature's controller instead.")
    // chunk 12
    val thrainMournfulAvenger = Cards.invent("Thráin, Mournful Avenger", "{1}{R}{G}", "Legendary Creature — Dwarf Warrior", "Thráin has indestructible as long as two or more creatures died under your control this turn.\nWhenever another creature you control dies, put a +1/+1 counter on Thráin. When this ability resolves for the third time this turn, Thráin fights up to one target creature you don't control.", "3/2")
    // chunk 12
    val thorondorTheWindlord = Cards.invent("Thorondor the Windlord", "{4}{W}{U}", "Legendary Creature — Bird Noble", "This spell costs {2} less to cast as long as you've drawn two or more cards this turn.\nFlying, vigilance\nOther Birds you control have vigilance.", "4/4")
    // chunk 12
    val kingOfTheForsworn = Cards.invent("King of the Forsworn", "{2}{W}{B}", "Legendary Creature — Spirit Noble", "Flying\nWhenever King of the Forsworn or another Spirit you control becomes the target of a spell, it phases out. (Treat it and anything attached to it as though they don't exist until your next turn.)\nWhenever King of the Forsworn or another Spirit you control phases in, create a tapped 1/1 white Spirit creature token with flying.", "3/3")
    // chunk 12
    val thranduilCounterOfKills = Cards.invent("Thranduil, Counter of Kills", "{2}{G}{U}", "Legendary Creature — Elf Archer", "Reach\nWhenever you scry, if Thranduil is tapped, you may untap it. Do this only once each turn.\nWhenever a creature an opponent controls dies, put a +1/+1 counter on Thranduil.", "2/3")
    // chunk 12
    val tedCrookedShirriff = Cards.invent("Ted, Crooked Shirriff", "{W}{B}", "Legendary Creature — Halfling Rogue", "Whenever a player casts their second spell each turn, you lose 1 life and create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")", "2/1")
    // chunk 12
    val lugdushUrukHaiCaptain = Cards.invent("Lugdush, Uruk-hai Captain", "{B}{R}", "Legendary Creature — Orc Soldier", "Menace\nIf one or more +1/+1 counters would be put on an Army, Goblin, or Orc you control, that many plus one +1/+1 counters are put on it instead.", "2/2")
    // chunk 12
    val berilacEsquireOfRohan = Cards.invent("Berilac, Esquire of Rohan", "{R}{W}", "Legendary Creature — Halfling Knight", "Haste\nBerilac has first strike as long as it's equipped.\nWhenever you attack with Berilac and another legendary creature, draw a card.", "2/2")
    // chunk 12
    val theVoiceOfTheDarkTower = Cards.invent("The Voice of the Dark Tower", "{3}{U}{B}", "Legendary Creature — Human Advisor", "When The Voice of the Dark Tower enters, target player mills three cards. Then amass Orcs X, where X is the number of instant and sorcery cards in that player's graveyard. (Put X +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)", "3/4")
    // chunk 12
    val oldGreyWillow = Cards.invent("Old Grey Willow", "{2}{B}{G}", "Legendary Creature — Treefolk", "Old Grey Willow's power and toughness are each equal to the number of lands you control.\nWhenever Old Grey Willow attacks, you may sacrifice another creature or a token. When you do, target creature an opponent controls gets -2/-2 until end of turn.", "*/*")
    // chunk 12
    val bolgerGuardOfTheCitadel = Cards.invent("Bolger, Guard of the Citadel", "{W}{U}", "Legendary Creature — Halfling Soldier", "Vigilance, ward {1}\n{T}: Another target creature you control gains protection from the card type of your choice until end of turn. (It can't be blocked, targeted, dealt damage, enchanted, or equipped by anything of that type.)", "2/2")
    // chunk 12
    val princeAdrahilTheFair = Cards.invent("Prince Adrahil the Fair", "{W}{U}", "Legendary Creature — Human Noble", "Whenever you draw your second card each turn, create a 1/1 white Human Soldier creature token.", "2/2")
    // chunk 12
    val ringVision = Cards.invent("Ring-vision", "{1}{U}{B}", "Sorcery", "The Ring tempts you. Search your library for a card that shares a color with a legendary creature you control, reveal it, put it into your hand, then shuffle.")
    // chunk 12
    val riseOfTheDarkLord = Cards.invent("Rise of the Dark Lord", "{2}{B}{G}", "Sorcery", "Each player sacrifices a creature of their choice. If you sacrificed a creature this way, you may return another permanent card from your graveyard to the battlefield.")
    // chunk 13
    val curumoOfManyColors = Cards.invent("Curumo of Many Colors", "{3}{W}{U}{B}", "Legendary Creature — Avatar Wizard", "Ward—Discard an enchantment, instant, or sorcery card.\nWhenever you cast your second spell each turn, each opponent mills two cards. When one or more cards are milled this way, exile target enchantment, instant, or sorcery card with equal or lesser mana value than that spell from an opponent's graveyard. Copy the exiled card. You may cast the copy without paying its mana cost.", "5/4")
    // chunk 13
    val maironTheDarkLord = Cards.invent("Mairon, the Dark Lord", "{3}{U}{B}{R}", "Legendary Creature — Avatar Horror", "Ward—Sacrifice a legendary artifact or legendary creature.\nWhenever an opponent casts a spell, amass Orcs 1.\nWhenever an Army you control deals combat damage to a player, the Ring tempts you.\nWhenever the Ring tempts you, you may discard your hand. If you do, draw four cards.", "7/6")
    // chunk 13
    val theDarkLordsRansom = Cards.invent("The Dark Lord's Ransom", "{1}{U}{B}", "Instant", "Choose an opponent. They look at the top four cards of your library and separate them into a face-down pile and a face-up pile. Put one pile into your hand and the other into your graveyard. The Ring tempts you.")
    // chunk 13
    val shadeSummoning = Cards.invent("Shade Summoning", "{W}{B}", "Sorcery", "Create two tapped 1/1 white Spirit creature tokens with flying.")
    // chunk 13
    val felarofLordOfHorses = Cards.invent("Felaróf, Lord of Horses", "{3}{R}{W}", "Legendary Creature — Horse", "Horses you control have haste. (They can attack and {T} as soon as they come under your control.)\nWhenever Felaróf attacks, you may put a creature card with lesser power from your hand onto the battlefield tapped and attacking.", "4/4")
    // chunk 13
    val muzgashLootBearer = Cards.invent("Muzgash, Loot Bearer", "{2}{B}{R}", "Legendary Creature — Orc Soldier", "Whenever Muzgash attacks, attach up to one target Equipment to it. Then amass Orcs X, where X is the number of Equipment attached to Muzgash. (Control of the Equipment doesn't change. To amass Orcs X, put X +1/+1 counters on an Army you control. It's also an Orc. If you don't control an Army, create a 0/0 black Orc Army creature token first.)", "4/4")
    // chunk 13
    val sandymanTyrantOfTheShire = Cards.invent("Sandyman, Tyrant of the Shire", "{2}{U}{B}", "Legendary Creature — Avatar Rogue", "Activated abilities of lands your opponents control can't be activated unless they're mana abilities.\nSandyman has all activated abilities of lands your opponents control except mana abilities.\nMana of any type can be spent to activate Sandyman's abilities.", "2/4")
    // chunk 13
    val mirkspawnChildOfUngoliant = Cards.invent("Mirkspawn, Child of Ungoliant", "{4}{B}{G}", "Legendary Creature — Spider Demon", "Deathtouch, ward {2}\nOther Spiders you control have deathtouch and ward {2}.\nWhenever another creature dealt damage this turn by a Spider you controlled dies, create a token that's a copy of that creature, except it's a Food artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life,\" and it loses all other card types.", "8/8")
    // chunk 13
    val deagolHelpfulGuide = Cards.invent("Déagol, Helpful Guide", "{1}{B}{G}", "Legendary Creature — Halfling Horror", "At the beginning of your end step, if a creature died under your control this turn, the Ring tempts you.\nWhenever the Ring tempts you, target opponent reveals cards from the top of their library until they reveal a land card. Put that card onto the battlefield tapped under your control and the rest into their graveyard.", "4/2")
    // chunk 13
    val halbaradRangerOfTheNorth = Cards.invent("Halbarad, Ranger of the North", "{2}{R}{G}", "Legendary Creature — Human Ranger", "Landfall — Whenever a land you control enters, target creature gets +1/+1 until end of turn. Then if that creature has power 4 or greater, it gains first strike until end of turn.", "4/4")
    // chunk 13
    val thengelKingOfRohan = Cards.invent("Thengel, King of Rohan", "{1}{R}{W}", "Legendary Creature — Human Noble", "Whenever Thengel or another Human you control enters, target creature gains double strike until end of turn.", "2/3")
    // chunk 13
    val iarwainBenAdar = Cards.invent("Iarwain Ben-adar", "{W}{U}{B}{R}{G}", "Legendary Creature — God Bard", "As long as there are four or more lore counters among Sagas you control, Iarwain Ben-adar has hexproof and indestructible.\nWhenever the final chapter ability of a Saga you control resolves, reveal cards from the top of your library until you reveal a Saga card. Put that card onto the battlefield and the rest on the bottom of your library in a random order. This ability triggers only once each turn.", "4/4")
    // chunk 13
    val lurtzOfTheWhiteHand = Cards.invent("Lurtz of the White Hand", "{2}{B}{R}", "Legendary Creature — Orc Soldier", "Whenever another creature you control dies, put a +1/+1 counter on Lurtz. If that creature was a Goblin or Orc, put two +1/+1 counters on Lurtz instead.", "3/3")
    // chunk 13
    val berenAndLuthienWed = Cards.invent("Beren and Lúthien, Wed", "{4}{G}{W}", "Legendary Creature — Human Elf Noble", "Vigilance\nWhenever Beren and Lúthien enters or attacks, put a +1/+1 counter on each other creature you control. You gain 1 life for each other creature you control.", "3/6")
    // chunk 13
    val gorthaurTheLidlessEye = Cards.invent("Gorthaur, the Lidless Eye", "{3}{B}{R}", "Legendary Creature — Avatar Horror", "When Gorthaur enters, gain control of target creature an opponent controls until end of turn. Untap it. It gains haste until end of turn.\n{1}{B}{R}: Creatures you control get +2/+0 until end of turn. Each opponent loses 2 life.", "4/4")
    // chunk 13
    val theFireDemonFlameOfUdun = Cards.invent("The Fire Demon, Flame of Udûn", "{3}{B}{R}", "Legendary Creature — Avatar Demon", "Trample\nWhen a legendary creature an opponent controls dies, put The Fire Demon on the bottom of its owner's library.", "7/7")
    // chunk 14
    val theRulingRing = Cards.invent("The Ruling Ring", "{4}", "Legendary Artifact", "Indestructible\nWhen The Ruling Ring enters, if you cast it, you gain protection from everything until your next turn.\nAt the beginning of your upkeep, you lose 1 life for each burden counter on The Ruling Ring.\n{T}: Put a burden counter on The Ruling Ring, then draw a card for each burden counter on The Ruling Ring.")
    // chunk 14
    val narsilFlameOfTheWest = Cards.invent("Narsil, Flame of the West", "{3}", "Legendary Artifact — Equipment", "Equipped creature gets +3/+1.\nWhenever equipped creature attacks, create two tapped 1/1 white Spirit creature tokens with flying. If that creature is legendary, instead create two of those tokens that are tapped and attacking.\nEquip {2}")
    // chunk 14
    val barrowDagger = Cards.invent("Barrow-Dagger", "{1}", "Artifact — Equipment", "Equipped creature gets +1/+1.\nWhenever equipped creature blocks or becomes blocked by a creature, that creature loses all abilities until end of turn.\nEquip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)")
    // chunk 14
    val entWaterBasin = Cards.invent("Ent-Water Basin", "{2}", "Artifact", "{X}, {T}: Put a +1/+1 counter on target creature with power X. Activate only as a sorcery.")
    // chunk 14
    val orcrist = Cards.invent("Orcrist", "{2}", "Legendary Artifact — Equipment", "Equipped creature has first strike and gets +1/+0 for each instant and sorcery card in your graveyard.\nWhenever equipped creature deals combat damage to a player, you may cast an instant or sorcery spell from your hand with mana value less than or equal to that damage without paying its mana cost.\nEquip {3}")
    // chunk 14
    val hornOfTheSteward = Cards.invent("Horn of the Steward", "{3}", "Legendary Artifact", "When Horn of the Steward enters, create a 1/1 white Human Soldier creature token.\n{3}, {T}: Create X 1/1 white Human Soldier creature tokens, where X is the number of Humans you control.")
    // chunk 14
    val hornOfTheRiders = Cards.invent("Horn of the Riders", "{2}", "Legendary Artifact", "Whenever two or more creatures you control attack a player, look at the top five cards of your library. You may reveal a creature card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.")
    // chunk 14
    val inheritedParcel = Cards.invent("Inherited Parcel", "{3}", "Artifact", "When this artifact enters, the Ring tempts you.\n{T}: Add one mana of any color.")
    // chunk 14
    val waybread = Cards.invent("Waybread", "{2}", "Artifact — Food", "When this artifact enters, scry 1, then draw a card.\n{2}, {T}, Sacrifice this artifact: You gain 3 life.\nWhen this artifact is put into a graveyard from the battlefield, its owner shuffles it into their library.")
    // chunk 14
    val mirrorOfTheLady = Cards.invent("Mirror of the Lady", "{2}", "Legendary Artifact", "{5}, {T}: Scry 1, then draw a card. This ability costs {1} less to activate for each legendary creature you control.")
    // chunk 14
    val mithrilShirt = Cards.invent("Mithril Shirt", "{3}", "Legendary Artifact — Equipment", "Flash\nIndestructible\nWhen Mithril Shirt enters, attach it to target legendary creature you control.\nEquipped creature has indestructible.\nEquip {3}")
    // chunk 14
    val seeingStoneOfTheTower = Cards.invent("Seeing-Stone of the Tower", "{3}", "Legendary Artifact", "At the beginning of your end step, put an influence counter on Seeing-Stone of the Tower and scry 2. Then target opponent may have you draw a card. If that player doesn't, you mill X cards, where X is the number of influence counters on Seeing-Stone of the Tower, and that player loses life equal to the total mana value of those cards.")
    // chunk 14
    val phialOfTheLady = Cards.invent("Phial of the Lady", "{3}", "Legendary Artifact", "If you would draw a card while you have no cards in hand, draw two cards instead.\nIf you would gain life while you have 5 or less life, you gain twice that much life instead.\n{T}: Add one mana of any color.")
    // chunk 14
    val farmsteadScarecrow = Cards.invent("Farmstead Scarecrow", "{2}", "Artifact Creature — Scarecrow", "Defender\n{1}: Add one mana of any color. Activate only once each turn.", "0/3")
    // chunk 14
    val dartbladeTheGlintingDagger = Cards.invent("Dartblade, the Glinting Dagger", "{2}", "Legendary Artifact — Equipment", "Equipped creature gets +1/+1 and has haste.\nAt the beginning of each combat, untap equipped creature.\nEquipped creature has first strike as long as it's blocking or blocked by a Goblin or Orc.\nEquip {2}")
    // chunk 14
    val stoneOfTheOath = Cards.invent("Stone of the Oath", "{1}", "Legendary Artifact", "If a creature an opponent controls would die, exile it instead.\n{2}, {T}, Sacrifice Stone of the Oath: Exile target player's graveyard. Draw a card.")
    // chunk 14
    val bungosRing = Cards.invent("Bungo's Ring", "{3}", "Legendary Artifact — Equipment", "During your turn, equipped creature has hexproof and can't be blocked.\nWhenever equipped creature attacks alone, you draw a card and you lose 1 life.\nEquip Halfling {1} ({1}: Attach to target Halfling you control. Equip only as a sorcery.)\nEquip {4} ({4}: Attach to target creature you control. Equip only as a sorcery.)")
    // chunk 14
    val pathfindersBoots = Cards.invent("Pathfinder's Boots", "{2}", "Artifact — Equipment", "Equipped creature has nonbasic landwalk. (It can't be blocked as long as defending player controls a nonbasic land.)\nEquip {2}")
    // chunk 14
    val darkTowerKeep = Cards.invent("Dark Tower Keep", null, "Legendary Land", "Dark Tower Keep enters tapped unless you control a legendary creature.\n{T}: Add {B}.\n{X}{X}{B}, {T}: Amass Orcs X. Activate only if a creature died this turn.")
    // chunk 14
    val greatHallOfTheTower = Cards.invent("Great Hall of the Tower", null, "Land", "{T}: Add {C}.\n{1}, {T}: Add two mana in any combination of colors. Spend this mana only to cast legendary spells.")
    // chunk 14
    val theWesternHavens = Cards.invent("The Western Havens", null, "Legendary Land", "When The Western Havens enters, scry 1.\n{T}: Add {C}.\n{T}: Add one mana of any color among legendary creature cards in your graveyard.")
    // chunk 14
    val whiteTowerCity = Cards.invent("White Tower City", null, "Legendary Land", "White Tower City enters tapped unless you control a legendary creature.\n{T}: Add {W}.\n{1}{W}, {T}: Draw a card. Activate only if you attacked with two or more creatures this turn.")
    // chunk 14
    val minesOfTheDeep = Cards.invent("Mines of the Deep", null, "Legendary Land", "Mines of the Deep enters tapped unless you control a legendary creature.\n{T}: Add {R}.\n{3}{R}, {T}, Exile three cards from your graveyard: Create two Treasure tokens.")
    // chunk 14
    val mountainOfFire = Cards.invent("Mountain of Fire", null, "Legendary Land", "{T}, Pay 1 life: Add {B} or {R}.\n{1}{B}{R}, {T}: Mountain of Fire deals 1 damage to each opponent.\n{5}{B}{R}, {T}, Sacrifice Mountain of Fire and a legendary artifact: Choose up to two creatures, then destroy the rest. Activate only as a sorcery.")
    // chunk 14
    val hiddenValleyHaven = Cards.invent("Hidden Valley Haven", null, "Legendary Land", "Hidden Valley Haven enters tapped unless you control a legendary creature.\n{T}: Add {U}.\n{1}{U}, {T}: Scry 2. Activate only if you control a legendary creature.")
    // chunk 14
    val theGreenShire = Cards.invent("The Green Shire", null, "Legendary Land", "The Green Shire enters tapped unless you control a legendary creature.\n{T}: Add {G}.\n{1}{G}, {T}, Tap an untapped creature you control: Create a Food token.")
    // chunk 14
    val hillsideTerrace = Cards.invent("Hillside Terrace", null, "Land", "{T}: Add {C}.\n{1}, {T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
}
