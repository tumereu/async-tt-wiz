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
}
