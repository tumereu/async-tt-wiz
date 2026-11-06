package arcanum.engine.sets.mh2

import arcanum.engine.cards.Cards
import arcanum.parser.OracleCard

/**
 * The invented Modern Horizons 2 (mh2) cards of the parser tests (`arcanum.parser.sets.mh2`), the same names and texts, for the
 * scenarios: each property registers its card and holds its name.
 */
internal object Mh2Set {
    // chunk 01
    val steadfastBenediction = Cards.invent("Steadfast Benediction", "{2}{W}", "Enchantment", "At the beginning of your end step, choose one —\n• You gain 1 life.\n• Return target creature card with mana value 1 from your graveyard to the battlefield.")
    // chunk 01
    val arcwiredLancer = Cards.invent("Arcwired Lancer", "{W}", "Artifact Creature — Soldier", "{T}, Remove X +1/+1 counters from this creature: It deals X damage to target attacking or blocking creature.\nModular 1 (This creature enters with a +1/+1 counter on it. When it dies, you may put its +1/+1 counters on target artifact creature.)", "0/1")
    // chunk 01
    val arcwiredTabby = Cards.invent("Arcwired Tabby", "{W}", "Artifact Creature — Cat", "Lifelink\nModular 1 (This creature enters with a +1/+1 counter on it. When it dies, you may put its +1/+1 counters on target artifact creature.)", "0/0")
    // chunk 01
    val arcwiredTemplate = Cards.invent("Arcwired Template", "{1}{W}", "Artifact Creature — Assembly-Worker", "Modular 2 (This creature enters with two +1/+1 counters on it. When it dies, you may put its +1/+1 counters on target artifact creature.)", "0/0")
    // chunk 01
    val thornedSpur = Cards.invent("Thorned Spur", "{1}{W}", "Artifact — Equipment", "When this Equipment enters, create a 1/1 colorless Thopter artifact creature token with flying, then attach this Equipment to it.\nEquipped creature gets +1/+0.\nEquip {2}")
    // chunk 01
    val smithsWard = Cards.invent("Smith's Ward", "{W}", "Instant", "Target permanent gains hexproof and indestructible until end of turn. If it's an artifact creature, it gets +2/+2 until end of turn.")
    // chunk 01
    val bloomingStillness = Cards.invent("Blooming Stillness", "{W}", "Instant", "You gain hexproof until your next turn. You gain 2 life.\nRebound (If you cast this spell from your hand, exile it as it resolves. At the beginning of your next upkeep, you may cast this card from exile without paying its mana cost.)")
    // chunk 01
    val severTheBonds = Cards.invent("Sever the Bonds", "{2}{W}", "Instant", "Choose one —\n• Destroy target artifact.\n• Destroy target enchantment.\n• Exile target card from a graveyard.\nReinforce 1—{W} ({W}, Discard this card: Put a +1/+1 counter on target creature.)")
    // chunk 01
    val chromegoat = Cards.invent("Chromegoat", "{3}{W}", "Artifact Creature — Goat", "Flash\nVigilance\nDevour artifact 1 (As this creature enters, you may sacrifice any number of artifacts. It enters with that many +1/+1 counters on it.)", "2/2")
    // chunk 01
    val wardenOfTheShire = Cards.invent("Warden of the Shire", "{4}{W}", "Creature — Giant Soldier", "Renown 2 (When this creature deals combat damage to a player, if it isn't renowned, put two +1/+1 counters on it and it becomes renowned.)\nWhenever one or more +1/+1 counters are put on this creature, exile up to one other target nonland permanent until this creature leaves the battlefield.", "3/3")
    // chunk 01
    val acolyteOfDawnlight = Cards.invent("Acolyte of Dawnlight", "{4}{W}", "Creature — Human Cleric", "Lifelink\nWhen this creature enters, return target permanent card with mana value 3 or less from your graveyard to your hand.", "3/3")
    // chunk 01
    val esperLookout = Cards.invent("Esper Lookout", "{W}", "Artifact Creature — Human Soldier", "Whenever an opponent casts their first noncreature spell each turn, draw a card unless that player pays {X}, where X is this creature's power.", "1/1")
    // chunk 01
    val midwayPatrol = Cards.invent("Midway Patrol", "{1}{W}", "Creature — Human Soldier", "{1}{W}, Exile this card from your graveyard: Create a 1/1 colorless Thopter artifact creature token with flying. Activate only as a sorcery.", "2/1")
    // chunk 01
    val radiantExecutor = Cards.invent("Radiant Executor", "{5}{W}{W}", "Creature — Angel", "Flying, lifelink\nAt the beginning of each combat, if you have more life than an opponent, this creature gains double strike until end of turn.", "5/5")
    // chunk 01
    val watchfulKirin = Cards.invent("Watchful Kirin", "{3}{W}", "Creature — Kirin", "Flying\nWhenever another creature you control dies, put a +1/+1 counter on this creature.", "2/3")
    // chunk 01
    val mendersFlight = Cards.invent("Mender's Flight", "{W}{W}{W}", "Creature — Bird", "Flying, lifelink", "3/3")
    // chunk 01
    val squireMyr = Cards.invent("Squire Myr", "{2}{W}", "Artifact Creature — Myr Knight", "{2}{W}: Adapt 1. (If this creature has no +1/+1 counters on it, put a +1/+1 counter on it.)\nWhenever one or more +1/+1 counters are put on this creature, it gains double strike until end of turn.", "2/2")
    // chunk 01
    val groundskeeperColos = Cards.invent("Groundskeeper Colos", "{5}{W}", "Creature — Goat Beast", "When this creature enters, put target card from an opponent's graveyard on the bottom of their library.\nBasic landcycling {1}{W} ({1}{W}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)", "4/6")
    // chunk 01
    val tardySupper = Cards.invent("Tardy Supper", "{3}{W}", "Sorcery", "Return target creature card from your graveyard to the battlefield. Create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")")
    // chunk 02
    val glintingGlare = Cards.invent("Glinting Glare", "{4}{W}", "Instant", "Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)\nGlinting Glare deals 5 damage to target attacking or blocking creature.")
    // chunk 02
    val alabasterGargoyle = Cards.invent("Alabaster Gargoyle", "{2}{W}", "Artifact Creature — Gargoyle", "Flying\n{W}: This creature gets +0/+1 until end of turn.", "2/2")
    // chunk 02
    val nyxbornExemplar = Cards.invent("Nyxborn Exemplar", "{4}{W}{W}", "Enchantment Creature — Human Soldier", "Whenever you gain life, you may put that many +1/+1 counters on each creature you control. Do this only once each turn.", "4/6")
    // chunk 02
    val untimelyStasis = Cards.invent("Untimely Stasis", "{1}{W}{W}", "Enchantment", "When this enchantment enters, untap all creatures, then those creatures phase out until this enchantment leaves the battlefield. Put a time counter on this enchantment for each creature that phased out this way.\nVanishing (At the beginning of your upkeep, remove a time counter from this enchantment. When the last is removed, sacrifice it.)")
    // chunk 02
    val lancingSunbeams = Cards.invent("Lancing Sunbeams", "{1}{W}", "Sorcery", "Exile target tapped creature.\nForecast — {2}{W}, Reveal this card from your hand: Tap target untapped creature. (Activate this ability only during your upkeep and only once each turn.)")
    // chunk 02
    val chromaticClosure = Cards.invent("Chromatic Closure", "{X}{W}", "Sorcery", "Converge — Exile target nonland permanent if its mana value is less than or equal to the number of colors of mana spent to cast this spell.")
    // chunk 02
    val rekindledConviction = Cards.invent("Rekindled Conviction", null, "Sorcery", "Suspend 2—{1}{W} (Rather than cast this card from your hand, pay {1}{W} and exile it with two time counters on it. At the beginning of your upkeep, remove a time counter. When the last is removed, you may cast it without paying its mana cost.)\nReturn all enchantment cards from your graveyard to the battlefield. (Auras with nothing to enchant remain in your graveyard.)")
    // chunk 02
    val purifierEnDal = Cards.invent("Purifier en-Dal", "{W}{W}", "Creature — Human Cleric", "Protection from black and from red\nWhen this creature enters, exile all cards that are black or red from all graveyards.\nIf a black or red permanent, spell, or card not on the battlefield would be put into a graveyard, exile it instead.", "2/2")
    // chunk 02
    val combTheDunes = Cards.invent("Comb the Dunes", "{3}{W}{W}", "Sorcery", "Exile target creature card from your graveyard. Create X 1/1 white Bird creature tokens with flying, where X is the exiled card's toughness.")
    // chunk 02
    val sweepTheGrounds = Cards.invent("Sweep the Grounds", "{3}{W}", "Enchantment", "Whenever a creature attacks you or a planeswalker you control, investigate. (Create a Clue token. It's an artifact with \"{2}, Sacrifice this token: Draw a card.\")")
    // chunk 02
    val serrasHerald = Cards.invent("Serra's Herald", "{4}{W}{W}{W}", "Creature — Angel", "Flying\nAs this creature enters, choose a card type.\nYou and creatures you control have protection from the chosen card type.", "7/7")
    // chunk 02
    val skyknightsGift = Cards.invent("Skyknight's Gift", "{1}{W}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets +1/+1 and has flying.\n{2}{W}: Return Skyknight's Gift to its owner's hand. Activate only if Skyknight's Gift is on the battlefield or in your graveyard.")
    // chunk 02
    val quietude = Cards.invent("Quietude", "{3}{W}{W}", "Creature — Elemental Incarnation", "Flash\nLifelink\nWhen this creature enters, exile up to one other target creature. That creature's controller gains life equal to its power.\nEvoke—Exile a white card from your hand.", "3/2")
    // chunk 02
    val spiritOfPassage = Cards.invent("Spirit of Passage", "{5}{W}{W}", "Creature — Elemental", "Flying\nWhen this creature enters, create two 1/1 white Bird creature tokens with flying.\nEvoke {3}{W} (You may cast this spell for its evoke cost. If you do, it's sacrificed when it enters.)", "2/4")
    // chunk 02
    val thrabenOverseer = Cards.invent("Thraben Overseer", "{2}{W}{W}", "Creature — Angel", "Flying, vigilance\nOther nontoken creatures you control get +1/+1 and have vigilance.", "2/2")
    // chunk 02
    val agelessWyrm = Cards.invent("Ageless Wyrm", "{3}{W}{W}", "Creature — Dragon", "Flying\nPlainscycling {2} ({2}, Discard this card: Search your library for a Plains card, reveal it, put it into your hand, then shuffle.)\nEternalize {2}{W}{W} ({2}{W}{W}, Exile this card from your graveyard: Create a token that's a copy of it, except it's a 4/4 black Zombie Dragon with no mana cost. Eternalize only as a sorcery.)", "5/5")
    // chunk 02
    val boundlessPromise = Cards.invent("Boundless Promise", "{1}{W}", "Instant", "Choose one —\n• Put a +1/+1 counter on each of up to two target creatures.\n• Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)\nEntwine {3}{W} (Choose both if you pay the entwine cost.)")
    // chunk 03
    val skyshiftProtist = Cards.invent("Skyshift Protist", "{3}{U}", "Creature — Elemental Beast", "Flying\nDiscard a card: Switch this creature's power and toughness until end of turn.", "2/4")
    // chunk 03
    val ladenSkyrigger = Cards.invent("Laden Skyrigger", "{2}{U}", "Creature — Human Pirate", "When this creature enters, create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")\nWhenever you sacrifice a token, this creature gains flying until end of turn.", "3/1")
    // chunk 03
    val strippedBare = Cards.invent("Stripped Bare", "{1}{U}", "Enchantment", "Flash\nWhen this enchantment enters, draw a card.\nCreatures lose all abilities.\nAt the beginning of the end step, sacrifice this enchantment.")
    // chunk 03
    val etheriumWeaver = Cards.invent("Etherium Weaver", "{2}{U}", "Artifact Creature — Human Wizard", "Whenever you cast a spell with mana value 4 or greater, create a 1/1 colorless Thopter artifact creature token with flying.", "2/1")
    // chunk 03
    val filigreeSteward = Cards.invent("Filigree Steward", "{2}{U}{U}", "Artifact Creature — Homunculus", "Flying\nFiligree Steward's power is equal to the number of artifacts you control.", "*/3")
    // chunk 03
    val tidehound = Cards.invent("Tidehound", "{U}", "Creature — Elemental Dog", "{3}, {T}: Investigate. (Create a Clue token. It's an artifact with \"{2}, Sacrifice this token: Draw a card.\")", "1/2")
    // chunk 03
    val fetidSentinel = Cards.invent("Fetid Sentinel", "{1}{U}", "Creature — Nightmare Bird", "Flying\nWhen this creature enters, surveil 1. (Look at the top card of your library. You may put that card into your graveyard.)\nDelirium — This creature gets +1/+0 as long as there are four or more card types among cards in your graveyard.", "1/2")
    // chunk 03
    val shatteredReason = Cards.invent("Shattered Reason", "{U}{U}{U}", "Sorcery", "Each opponent mills fourteen cards.\nCycling {1}{U} ({1}{U}, Discard this card: Draw a card.)\nWhen you cycle this card, each opponent mills four cards.")
    // chunk 03
    val lanternlitDrifter = Cards.invent("Lanternlit Drifter", "{2}{U}", "Creature — Spirit", "Flying\n{2}{U}: Another target creature gains flying until end of turn.\nChannel — {X}{U}, Discard this card: X target creatures gain flying until end of turn.", "2/2")
    // chunk 03
    val firmTestimony = Cards.invent("Firm Testimony", "{U}", "Sorcery", "Create a 0/3 blue Crab creature token.\nInvestigate. (Create a Clue token. It's an artifact with \"{2}, Sacrifice this token: Draw a card.\")")
    // chunk 03
    val foreseenTreachery = Cards.invent("Foreseen Treachery", null, "Sorcery", "Suspend 3—{1}{U}{U} (Rather than cast this card from your hand, pay {1}{U}{U} and exile it with three time counters on it. At the beginning of your upkeep, remove a time counter. When the last is removed, you may cast it without paying its mana cost.)\nSearch target opponent's library for a creature card and put that card onto the battlefield under your control. Then that player shuffles.")
    // chunk 03
    val scrapSerpent = Cards.invent("Scrap Serpent", "{5}{U}{U}", "Creature — Serpent", "Affinity for tokens (This spell costs {1} less to cast for each token you control.)\nWhenever a token you control enters, tap target nonland permanent an opponent controls. It doesn't untap during its controller's next untap step.", "5/6")
    // chunk 03
    val lapseOfAttention = Cards.invent("Lapse of Attention", "{1}{U}", "Instant", "Replicate {U} (When you cast this spell, copy it for each time you paid its replicate cost. You may choose new targets for the copies.)\nCounter target spell unless its controller pays {2}.")
    // chunk 03
    val wakingVisions = Cards.invent("Waking Visions", "{3}{U}{U}", "Sorcery", "Draw X cards, where X is the number of card types among cards in your graveyard.")
    // chunk 03
    val innerVoyage = Cards.invent("Inner Voyage", "{4}{U}{U}", "Instant", "Draw three cards.\nBasic landcycling {1}{U} ({1}{U}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)")
    // chunk 03
    val murkwaterTyrant = Cards.invent("Murkwater Tyrant", "{5}{U}{U}", "Creature — Dragon", "Delve (Each card you exile from your graveyard while casting this spell pays for {1}.)\nFlying\nThis creature enters with a +1/+1 counter on it for each instant and sorcery card exiled with it.\nWhenever an instant or sorcery card leaves your graveyard, put a +1/+1 counter on this creature.", "3/3")
    // chunk 03
    val arcaneErasure = Cards.invent("Arcane Erasure", "{2}{U}", "Enchantment", "At the beginning of your upkeep, scry 1.\nWhenever you discard a card, each opponent mills two cards. (They put the top two cards of their library into their graveyard.)")
    // chunk 03
    val packageMyr = Cards.invent("Package Myr", "{1}{U}", "Artifact Creature — Clue Myr", "{2}, Sacrifice this creature: Draw a card.", "2/1")
    // chunk 03
    val illusoryDreadjaw = Cards.invent("Illusory Dreadjaw", "{2}{U}{U}", "Creature — Dinosaur Illusion", "Trample\nWhen this creature becomes the target of a spell or ability, sacrifice it.", "6/6")
    // chunk 04
    val frenziedSeer = Cards.invent("Frenzied Seer", "{1}{U}", "Creature — Merfolk Wizard", "{U}, {T}: Draw a card, then discard a card.\nDelirium — {2}{U}, {T}: Draw a card. Activate only if there are four or more card types among cards in your graveyard.", "1/1")
    // chunk 04
    val readjust = Cards.invent("Readjust", "{1}{U}", "Instant", "Return target creature to its owner's hand. If you've discarded a card this turn, draw a card.")
    // chunk 04
    val wakeAndStir = Cards.invent("Wake and Stir", "{1}{U}", "Sorcery", "Target noncreature artifact you control becomes a 0/0 artifact creature. Put four +1/+1 counters on each artifact that became a creature this way.\nOverload {4}{U}{U} (You may cast this spell for its overload cost. If you do, change \"target\" in its text to \"each.\")")
    // chunk 04
    val rishadanDeckhand = Cards.invent("Rishadan Deckhand", "{U}", "Creature — Merfolk", "Islandwalk (This creature can't be blocked as long as defending player controls an Island.)\n{1}, {T}: Tap target land.", "1/2")
    // chunk 04
    val scuttlesurge = Cards.invent("Scuttlesurge", "{1}{U}", "Enchantment", "{1}, Discard a card: Create a 0/3 blue Crab creature token.\nDelirium — Crabs you control get +1/+1 as long as there are four or more card types among cards in your graveyard.")
    // chunk 04
    val fracturedPride = Cards.invent("Fractured Pride", "{U}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets -3/-0.\n{3}{U}{U}: Put enchanted creature into its owner's library third from the top.")
    // chunk 04
    val suchSparkle = Cards.invent("Such Sparkle", "{2}{U}", "Enchantment — Aura", "Enchant creature\nWhen this Aura enters, if you control a token, tap enchanted creature, then scry 2.\nEnchanted creature doesn't untap during its controller's untap step.")
    // chunk 04
    val sampleHoarder = Cards.invent("Sample Hoarder", "{4}{U}", "Creature — Vedalken Wizard", "When this creature enters, create a 1/1 green Squirrel creature token and a 0/3 blue Crab creature token.\nWhen this creature dies, create a token that's a copy of target token you control.", "2/1")
    // chunk 04
    val ironfinLeviathan = Cards.invent("Ironfin Leviathan", "{5}{U}", "Creature — Whale", "Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)\nWhenever an artifact you control enters, untap this creature.", "3/4")
    // chunk 04
    val walkBetween = Cards.invent("Walk Between", "{3}{U}{U}", "Sorcery", "Return two target creatures to their owners' hands.\nWizardcycling {2} ({2}, Discard this card: Search your library for a Wizard card, reveal it, put it into your hand, then shuffle.)")
    // chunk 04
    val finesse = Cards.invent("Finesse", "{2}{U}{U}", "Creature — Elemental Incarnation", "Flash\nFlying\nWhen this creature enters, choose up to one target creature spell or planeswalker spell. Its owner puts it on their choice of the top or bottom of their library.\nEvoke—Exile a blue card from your hand.", "3/3")
    // chunk 04
    val holdInAbeyance = Cards.invent("Hold in Abeyance", "{U}", "Instant", "Exile target creature and put two time counters on it. If it doesn't have suspend, it gains suspend. (At the beginning of its owner's upkeep, they remove a time counter. When the last is removed, they may play it without paying its mana cost. If it's a creature, it has haste.)")
    // chunk 04
    val ysvelinOfTideAndSky = Cards.invent("Ysvelin of Tide and Sky", "{1}{U}{U}", "Legendary Creature — Merfolk God", "Ysvelin has indestructible as long as you control at least two other Merfolk.\nWhenever Ysvelin attacks, draw a card.\nOther Merfolk you control have ward {1}. (Whenever another Merfolk you control becomes the target of a spell or ability an opponent controls, counter it unless that player pays {1}.)", "3/4")
    // chunk 04
    val clearTheHeavens = Cards.invent("Clear the Heavens", "{X}{U}{U}", "Sorcery", "Converge — Create a 1/1 colorless Thopter artifact creature token with flying for each color of mana spent to cast this spell.")
    // chunk 04
    val mindOverseer = Cards.invent("Mind Overseer", "{6}{U}", "Artifact Creature — Construct", "Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)\nFlying\nWhen this creature enters, draw two cards.", "2/2")
    // chunk 04
    val currentSculptor = Cards.invent("Current Sculptor", "{U}", "Creature — Merfolk Wizard", "Kicker {1} (You may pay an additional {1} as you cast this spell.)\nWhen this creature enters, if it was kicked, target land becomes an Island for as long as this creature remains on the battlefield.\nThis creature gets +1/+1 as long as an opponent controls an Island.", "1/1")
    // chunk 04
    val vedalkenInterloper = Cards.invent("Vedalken Interloper", "{1}{U}", "Creature — Vedalken Rogue", "This creature can't be blocked.\nMetalcraft — This creature gets +1/+0 as long as you control three or more artifacts.", "1/3")
    // chunk 05
    val archdemonOfWoe = Cards.invent("Archdemon of Woe", "{5}{B}{B}", "Creature — Demon", "Flying\nWhen this creature enters, creatures your opponents control get -2/-2 until end of turn.\nUnearth {3}{B}{B} ({3}{B}{B}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)", "4/5")
    // chunk 05
    val archonOfMalice = Cards.invent("Archon of Malice", "{6}{B}{B}", "Creature — Archon", "Flying\nWhenever this creature enters or attacks, target opponent sacrifices a creature or planeswalker of their choice, discards a card, and loses 3 life. You draw a card and gain 3 life.", "6/6")
    // chunk 05
    val boneSlivers = Cards.invent("Bone Slivers", "{B}", "Sorcery", "As an additional cost to cast this spell, sacrifice a creature or discard a card.\nDestroy target creature or planeswalker.")
    // chunk 05
    val shatterTheFrost = Cards.invent("Shatter the Frost", "{B}{B}", "Sorcery", "Destroy target land that is snow or could produce {C}.\nOverload {4}{B}{B} (You may cast this spell for its overload cost. If you do, change \"target\" in its text to \"each.\")")
    // chunk 05
    val cabalNovice = Cards.invent("Cabal Novice", "{1}{B}", "Creature — Human Warlock", "Discard a card: This creature gains lifelink until end of turn.\nThreshold — This creature gets +1/+2 as long as there are seven or more cards in your graveyard.", "2/1")
    // chunk 05
    val rattlingSoothsayer = Cards.invent("Rattling Soothsayer", "{1}{B}", "Creature — Skeleton Shaman", "This creature can't block.\nWhen this creature enters, you draw a card and you lose 1 life.\n{2}{B}{B}: Return this card from your graveyard to your hand.", "1/1")
    // chunk 05
    val curseItAll = Cards.invent("Curse It All", "{B}{B}", "Sorcery", "Destroy target creature. A creature destroyed this way can't be regenerated.\nOverload {2}{W}{W} (You may cast this spell for its overload cost. If you do, change \"target\" in its text to \"each.\")")
    // chunk 05
    val dauthiNullwalker = Cards.invent("Dauthi Nullwalker", "{B}{B}", "Creature — Dauthi Rogue", "Shadow (This creature can block or be blocked by only creatures with shadow.)\nIf a card would be put into an opponent's graveyard from anywhere, instead exile it with a void counter on it.\n{T}, Sacrifice this creature: Choose an exiled card an opponent owns with a void counter on it. You may play it this turn without paying its mana cost.", "3/2")
    // chunk 05
    val refinedPalate = Cards.invent("Refined Palate", "{2}{B}", "Sorcery", "Look at the top four cards of your library. Put one of them into your hand and the rest into your graveyard. You gain life equal to the greatest power among creature cards put into your graveyard this way.")
    // chunk 05
    val resoundingReturn = Cards.invent("Resounding Return", "{B}", "Sorcery", "Return target creature card and all other cards with the same name as that card from your graveyard to your hand.")
    // chunk 05
    val banquetOfReason = Cards.invent("Banquet of Reason", "{3}{B}", "Enchantment", "Whenever you discard a card, this enchantment deals 1 damage to any target and you gain 1 life.")
    // chunk 05
    val stripTheEssence = Cards.invent("Strip the Essence", "{1}{B}{B}", "Sorcery", "Exile target creature or planeswalker. You gain life equal to the number of counters on it.")
    // chunk 05
    val giltEdgeProwler = Cards.invent("Gilt-Edge Prowler", "{2}{B}", "Creature — Human Rogue", "{1}, {T}, Pay 1 life: Draw a card. Activate only if you've discarded a card this turn.", "2/3")
    // chunk 05
    val lament = Cards.invent("Lament", "{2}{B}{B}", "Creature — Elemental Incarnation", "Menace\nWhen this creature enters, target opponent reveals their hand. You choose a nonland card from it. That player discards that card.\nEvoke—Exile a black card from your hand.", "3/2")
    // chunk 05
    val pitMongrel = Cards.invent("Pit Mongrel", "{3}{B}", "Creature — Nightmare Dog", "Discard a card: This creature gets +1/+1 until end of turn.\nMadness {2}{B} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)", "4/3")
    // chunk 05
    val sculleryImp = Cards.invent("Scullery Imp", "{3}{B}", "Creature — Imp", "Flying, haste\nMadness {B} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)", "2/2")
    // chunk 05
    val legionOutrider = Cards.invent("Legion Outrider", "{1}{B}", "Creature — Vampire Soldier", "{1}, Sacrifice another creature: This creature explores. (Reveal the top card of your library. Put that card into your hand if it's a land. Otherwise, put a +1/+1 counter on this creature, then put the card back or put it into your graveyard.)", "2/2")
    // chunk 05
    val repugnantArchivist = Cards.invent("Repugnant Archivist", "{4}{B}", "Creature — Gorgon Wizard", "Exploit (When this creature enters, you may sacrifice a creature.)\nMenace\nWhen this creature exploits a creature, destroy target creature you don't control with mana value 3 or less.", "5/4")
    // chunk 05
    val magusOfTheSpan = Cards.invent("Magus of the Span", "{B}{B}{B}", "Creature — Human Wizard", "Whenever a nontoken creature is put into your graveyard from the battlefield, create a 2/2 black Zombie creature token.\nWhen a creature is put into an opponent's graveyard from the battlefield, exile this creature.", "4/4")
    // chunk 06
    val mortgoyf = Cards.invent("Mortgoyf", "{3}{B}{B}", "Creature — Lhurgoyf", "Mortgoyf's power is equal to the number of creature cards in all graveyards.\nAt the beginning of each player's upkeep, that player discards a card.\nMadness {1}{B}{B} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)", "*/4")
    // chunk 06
    val gravecallersFamiliar = Cards.invent("Gravecaller's Familiar", "{3}{B}", "Creature — Bird Spirit", "Flying\nHellbent — This creature has lifelink as long as you have no cards in hand.\n{B}, Discard a card: This creature gains indestructible until end of turn. Tap it.", "3/1")
    // chunk 06
    val huddledShambler = Cards.invent("Huddled Shambler", "{B}", "Creature — Zombie", "When this creature dies, create X tapped 1/1 green Squirrel creature tokens, where X is this creature's power.", "1/1")
    // chunk 06
    val endureBeyond = Cards.invent("Endure Beyond", "{1}{B}", "Sorcery", "Return target nonlegendary creature card from your graveyard to the battlefield with a -1/-1 counter on it.")
    // chunk 06
    val unholyTutor = Cards.invent("Unholy Tutor", null, "Sorcery", "Suspend 2—{1}{B} (Rather than cast this card from your hand, pay {1}{B} and exile it with two time counters on it. At the beginning of your upkeep, remove a time counter. When the last is removed, you may cast it without paying its mana cost.)\nSearch your library for a card, put that card into your hand, then shuffle.")
    // chunk 06
    val prismaticGourmand = Cards.invent("Prismatic Gourmand", "{4}{B}", "Creature — Vampire Wizard", "Converge — When this creature enters, each opponent loses X life and you gain X life, where X is the number of colors of mana spent to cast this spell.", "5/5")
    // chunk 06
    val sinisterSeaStar = Cards.invent("Sinister Sea Star", "{1}{B}", "Creature — Starfish", "{T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)", "0/3")
    // chunk 06
    val abruptDecree = Cards.invent("Abrupt Decree", "{1}{B}", "Instant", "Split second (As long as this spell is on the stack, players can't cast spells or activate abilities that aren't mana abilities.)\nTarget player sacrifices a creature of their choice.")
    // chunk 06
    val tizerusCourser = Cards.invent("Tizerus Courser", "{2}{B}", "Creature — Pegasus", "Escape—{4}{B}, Exile five other cards from your graveyard. (You may cast this card from your graveyard for its escape cost.)\nThis creature escapes with your choice of a +1/+1 counter or a flying counter on it.", "3/2")
    // chunk 06
    val vourachDreadChanter = Cards.invent("Vourach, Dread Chanter", "{1}{B}", "Legendary Creature — Human Cleric", "Kicker {B}{B} (You may pay an additional {B}{B} as you cast this spell.)\nProtection from white\nWhenever an opponent discards a card, put a +1/+1 counter on Vourach.\nWhen Vourach enters, if it was kicked, target opponent discards two cards at random.", "2/1")
    // chunk 06
    val vourachsHymnal = Cards.invent("Vourach's Hymnal", "{3}{B}", "Sorcery", "Target opponent reveals their hand. You choose a card from it. That player discards that card, then discards a card at random.")
    // chunk 06
    val woefulPlunge = Cards.invent("Woeful Plunge", "{1}{B}", "Instant", "Target creature gets -3/-3 until end of turn.\nHellbent — That creature gets -13/-13 until end of turn instead if you have no cards in hand.")
    // chunk 06
    val netherworldRecluse = Cards.invent("Netherworld Recluse", "{4}{B}{B}", "Creature — Human Peasant", "When this creature enters, create a number of 1/1 green Squirrel creature tokens equal to your devotion to black. (Each {B} in the mana costs of permanents you control counts toward your devotion to black.)", "3/3")
    // chunk 06
    val namelessGrave = Cards.invent("Nameless Grave", "{1}{B}", "Sorcery", "Search your library for a nonlegendary card, put that card into your graveyard, then shuffle.")
    // chunk 06
    val verminGlutton = Cards.invent("Vermin Glutton", "{1}{B}", "Creature — Vampire", "{T}, Sacrifice another creature: Each opponent loses 2 life and you gain 2 life.", "2/2")
    // chunk 06
    val foulInterrer = Cards.invent("Foul Interrer", "{2}{B}{B}", "Creature — Zombie Warlock", "Deathtouch (Any amount of damage this deals to a creature is enough to destroy it.)\nWhen this creature enters, search your library for a card, put that card into your graveyard, then shuffle.", "2/2")
    // chunk 06
    val wearyOfWorlds = Cards.invent("Weary of Worlds", "{3}{B}{B}", "Enchantment — Aura", "Enchant creature\nEnchanted creature gets -4/-4.\nBasic landcycling {1}{B} ({1}{B}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)")
    // chunk 06
    val fledglingNecromancer = Cards.invent("Fledgling Necromancer", "{4}{B}", "Creature — Human Warlock", "When this creature enters, you may exile two cards from your graveyard. When you do, return target creature card from your graveyard to the battlefield.", "2/3")
    // chunk 07
    val arcwiredSlasher = Cards.invent("Arcwired Slasher", "{4}{R}", "Artifact Creature — Cat", "Modular 4 (This creature enters with four +1/+1 counters on it. When it dies, you may put its +1/+1 counters on target artifact creature.)\nRiot (This creature enters with your choice of an additional +1/+1 counter or haste.)", "0/0")
    // chunk 07
    val arcwiredTracker = Cards.invent("Arcwired Tracker", "{2}{R}", "Artifact Creature — Dog", "Menace\nModular 2 (This creature enters with two +1/+1 counters on it. When it dies, you may put its +1/+1 counters on target artifact creature.)\nWhenever you cast a spell other than your first spell each turn, put a +1/+1 counter on this creature.", "0/0")
    // chunk 07
    val arcwiredWhelp = Cards.invent("Arcwired Whelp", "{3}{R}", "Artifact Creature — Dragon", "Flying\n{R}: This creature gets +1/+0 until end of turn.\nModular 2 (This creature enters with two +1/+1 counters on it. When it dies, you may put its +1/+1 counters on target artifact creature.)", "0/0")
    // chunk 07
    val battleScheme = Cards.invent("Battle Scheme", "{3}{R}", "Enchantment", "At the beginning of combat on your turn, target creature you control gets +2/+0 until end of turn.\nBasic landcycling {1}{R} ({1}{R}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)")
    // chunk 07
    val flaringRootwalla = Cards.invent("Flaring Rootwalla", "{R}", "Creature — Lizard", "{R}: This creature gets +2/+0 until end of turn. Activate only once each turn.\nMadness {0} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)", "1/1")
    // chunk 07
    val bloodbraidRaider = Cards.invent("Bloodbraid Raider", "{1}{R}", "Creature — Human Berserker", "This creature can't block.\nDelirium — This spell has cascade as long as there are four or more card types among cards in your graveyard. (When you cast this spell, exile cards from the top of your library until you exile a nonland card that costs less. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order.)", "3/1")
    // chunk 07
    val breyasPupil = Cards.invent("Breya's Pupil", "{2}{R}", "Artifact Creature — Human Artificer", "When this creature enters, create a 1/1 colorless Thopter artifact creature token with flying.\n{T}, Sacrifice an artifact: Choose one —\n• Exile the top card of your library. Until the end of your next turn, you may play that card.\n• Target creature gets +2/+0 until end of turn.", "2/3")
    // chunk 07
    val measuredBlast = Cards.invent("Measured Blast", "{2}{R}", "Instant", "Reveal cards from the top of your library until you reveal a nonland card. Put the revealed cards on the bottom of your library in a random order. When you reveal a nonland card this way, Measured Blast deals damage equal to that card's mana value to any target.\nFlashback {3}{R}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)")
    // chunk 07
    val captainRillaVane = Cards.invent("Captain Rilla Vane", "{2}{R}", "Legendary Creature — Human Pirate", "Whenever you cast your third spell each turn, put a +1/+1 counter on Captain Rilla Vane, then it deals damage equal to its power to any target.", "3/2")
    // chunk 07
    val cooksKiss = Cards.invent("Cook's Kiss", "{1}{R}{R}", "Instant", "Gain control of target spell that targets only a single permanent or player. Copy it, then reselect the targets at random for the spell and the copy. The new targets can't be you or a permanent you control.")
    // chunk 07
    val dragonsWrathConduit = Cards.invent("Dragon's Wrath Conduit", "{R}", "Creature — Human Shaman", "Whenever you cast a noncreature spell, surveil 1. (Look at the top card of your library. You may put that card into your graveyard.)\nDelirium — As long as there are four or more card types among cards in your graveyard, this creature gets +2/+2, has flying, and attacks each combat if able.", "1/1")
    // chunk 07
    val godlessScavenging = Cards.invent("Godless Scavenging", "{1}{R}", "Instant", "Discard a card, then draw a card.\nRebound (If you cast this spell from your hand, exile it as it resolves. At the beginning of your next upkeep, you may cast this card from exile without paying its mana cost.)")
    // chunk 07
    val fireRush = Cards.invent("Fire Rush", "{R}", "Enchantment", "At the beginning of your end step, this enchantment deals 5 damage to each planeswalker.\nCycling {2} ({2}, Discard this card: Draw a card.)")
    // chunk 07
    val flametongueCub = Cards.invent("Flametongue Cub", "{R}{R}", "Creature — Kavu", "Multikicker {2} (You may pay an additional {2} any number of times as you cast this spell.)\nThis creature enters with a +1/+1 counter on it for each time it was kicked.\nWhen this creature enters, it deals damage equal to its power to target creature.", "2/1")
    // chunk 07
    val choler = Cards.invent("Choler", "{3}{R}{R}", "Creature — Elemental Incarnation", "Double strike\nWhen this creature enters, it deals 4 damage divided as you choose among any number of target creatures and/or planeswalkers.\nEvoke—Exile a red card from your hand.", "3/3")
    // chunk 07
    val galvanicHandoff = Cards.invent("Galvanic Handoff", "{2}{R}", "Sorcery", "Exile the top card of your library. During your next turn, you may play that card.\nStorm (When you cast this spell, copy it for each spell cast before it this turn.)")
    // chunk 07
    val greaterBehemoth = Cards.invent("Greater Behemoth", "{5}{R}{R}", "Creature — Beast", "Trample\nSuspend 4—{1}{R} (Rather than cast this card from your hand, you may pay {1}{R} and exile it with four time counters on it. At the beginning of your upkeep, remove a time counter. When the last is removed, you may cast it without paying its mana cost. It has haste.)", "7/5")
    // chunk 08
    val glanceAtTomorrow = Cards.invent("Glance at Tomorrow", null, "Sorcery", "Suspend 3—{R}{R}\nShuffle all permanents you own into your library, then reveal that many cards from the top of your library. Put all non-Aura permanent cards revealed this way onto the battlefield, then do the same for Aura cards, then put the rest on the bottom of your library in a random order.")
    // chunk 08
    val goblinSnarerunner = Cards.invent("Goblin Snarerunner", "{3}{R}", "Creature — Goblin", "Whenever this creature attacks, flip three coins. For each flip you win, create a 1/1 red Goblin creature token that's tapped and attacking.", "4/2")
    // chunk 08
    val goredFanatic = Cards.invent("Gored Fanatic", "{3}{R}", "Creature — Cyclops Berserker", "Reach\nDelirium — Whenever this creature attacks, if there are four or more card types among cards in your graveyard, this creature deals 1 damage to each creature defending player controls.", "4/3")
    // chunk 08
    val resonantProdigy = Cards.invent("Resonant Prodigy", "{1}{R}", "Creature — Human Wizard", "Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)\nIf a triggered ability of a Shaman or another Wizard you control triggers, that ability triggers an additional time.", "1/3")
    // chunk 08
    val prismscorch = Cards.invent("Prismscorch", "{1}{R}", "Sorcery", "Converge — Prismscorch deals X damage to any target, where X is the number of colors of mana spent to cast this spell.\nFlashback {4}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)")
    // chunk 08
    val thunderLance = Cards.invent("Thunder Lance", "{1}{R}", "Artifact — Equipment", "Equipped creature gets +1/+0 and has trample.\n{2}{R}, Sacrifice this Equipment: It deals 3 damage to any target.\nEquip {1}")
    // chunk 08
    val tunnelCaveIn = Cards.invent("Tunnel Cave-In", "{3}{R}", "Instant", "If it's your turn, you may sacrifice a Mountain rather than pay this spell's mana cost.\nTunnel Cave-In deals 5 damage to target creature or planeswalker.")
    // chunk 08
    val velusPeakManticore = Cards.invent("Velus Peak Manticore", "{2}{R}{R}", "Enchantment Creature — Manticore", "At the beginning of combat on your turn, you may discard a card. When you do, this creature deals X damage to any target, where X is the number of card types the discarded card has.", "3/4")
    // chunk 08
    val basaltCharmaw = Cards.invent("Basalt Charmaw", "{3}{R}{R}", "Creature — Dragon", "This spell costs {1} less to cast for each land your opponents control that could produce {C}.\nFlying\nWhen this creature enters, destroy target nonbasic land an opponent controls.", "4/4")
    // chunk 08
    val rakavanQuickPilferer = Cards.invent("Rakavan, Quick Pilferer", "{R}", "Legendary Creature — Monkey Pirate", "Whenever Rakavan deals combat damage to a player, create a Treasure token and exile the top card of that player's library. Until end of turn, you may cast that card.\nDash {1}{R} (You may cast this spell for its dash cost. If you do, it gains haste, and it's returned from the battlefield to its owner's hand at the beginning of the next end step.)", "2/1")
    // chunk 08
    val insurrectionist = Cards.invent("Insurrectionist", "{5}{R}", "Creature — Human Wizard", "When this creature enters, return target instant or sorcery card from your graveyard to your hand.\nMadness {3}{R} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)", "3/3")
    // chunk 08
    val skophosRavager = Cards.invent("Skophos Ravager", "{2}{R}", "Creature — Minotaur Warrior", "During your turn, this creature gets +2/+0.\nMadness {1}{R} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)", "2/3")
    // chunk 08
    val cinderStrider = Cards.invent("Cinder Strider", "{5}{R}{R}", "Creature — Elemental", "Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)\n{1}, Sacrifice an artifact: This creature deals 1 damage to any target.", "3/3")
    // chunk 08
    val spreadingRevolt = Cards.invent("Spreading Revolt", "{4}{R}", "Sorcery", "Gain control of target creature you don't control until end of turn. Untap that creature. It gains haste until end of turn.\nStorm (When you cast this spell, copy it for each spell cast before it this turn. You may choose new targets for the copies.)")
    // chunk 08
    val hitTheJackpot = Cards.invent("Hit the Jackpot", "{R}", "Sorcery", "Create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")\nFlashback {2}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)")
    // chunk 08
    val taproomScoundrel = Cards.invent("Taproom Scoundrel", "{1}{R}", "Creature — Human Rogue", "Whenever you win a coin flip, create two Treasure tokens. (They're artifacts with \"{T}, Sacrifice this token: Add one mana of any color.\")\n{1}, {T}, Sacrifice another permanent: Flip a coin.", "1/3")
    // chunk 08
    val profaneFever = Cards.invent("Profane Fever", "{R}", "Instant", "Profane Fever deals 2 damage to target creature or planeswalker.\nDelirium — Profane Fever deals 6 damage instead if there are four or more card types among cards in your graveyard.")
    // chunk 08
    val viashinoWhipclaw = Cards.invent("Viashino Whipclaw", "{1}{R}", "Creature — Lizard Warrior", "{T}, Discard a card: Creatures you control gain haste until end of turn.", "2/2")
    // chunk 09
    val bountifulReaping = Cards.invent("Bountiful Reaping", "{G}", "Sorcery", "Choose land or nonland. Reveal cards from the top of your library until you reveal a card of the chosen kind. Put that card into your hand and the rest on the bottom of your library in a random order.")
    // chunk 09
    val aviePrimordialOoze = Cards.invent("Avie, Primordial Ooze", "{2}{G}{G}{G}", "Legendary Creature — Ooze", "Storm (When you cast this spell, copy it for each spell cast before it this turn. Copies become tokens.)\nAvie isn't legendary if it's a token.\nAvie enters with a +1/+1 counter on it for each other Ooze you control.", "2/2")
    // chunk 09
    val flaghideKrushok = Cards.invent("Flaghide Krushok", "{3}{G}", "Creature — Beast", "Trample\nReinforce 2—{1}{G} ({1}{G}, Discard this card: Put two +1/+1 counters on target creature.)\nScavenge {5}{G}{G} ({5}{G}{G}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)", "4/4")
    // chunk 09
    val hallowedReprieve = Cards.invent("Hallowed Reprieve", "{1}{G}", "Instant", "Target player shuffles their graveyard into their library. Prevent all combat damage that would be dealt this turn.")
    // chunk 09
    val chitterjawSquirrelCaptain = Cards.invent("Chitterjaw, Squirrel Captain", "{2}{G}", "Legendary Creature — Squirrel Warrior", "Forestwalk (This creature can't be blocked as long as defending player controls a Forest.)\nIf one or more tokens would be created under your control, those tokens plus that many 1/1 green Squirrel creature tokens are created instead.\n{B}, Sacrifice X Squirrels: Target creature gets +X/-X until end of turn.", "3/3")
    // chunk 09
    val squirrelstorm = Cards.invent("Squirrelstorm", "{1}{G}", "Sorcery", "Create a 1/1 green Squirrel creature token.\nStorm (When you cast this spell, copy it for each spell cast before it this turn.)")
    // chunk 09
    val acornspitter = Cards.invent("Acornspitter", "{2}{G}", "Artifact", "At the beginning of your upkeep, you may sacrifice a token. If you do, put an acorn counter on this artifact.\nSquirrels you control get +1/+1 for each acorn counter on this artifact.\n{G}, {T}: Create a 1/1 green Squirrel creature token.")
    // chunk 09
    val pryOpen = Cards.invent("Pry Open", "{2}{G}", "Sorcery", "Destroy target artifact or enchantment. Create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")")
    // chunk 09
    val deepwoodDweller = Cards.invent("Deepwood Dweller", "{2}{G}", "Creature — Elf Warrior", "Vigilance (Attacking doesn't cause this creature to tap.)\n{5}{G}, {T}: Draw a card. This ability costs {1} less to activate for each +1/+1 counter on creatures you control.", "3/2")
    // chunk 09
    val duskshellCreeper = Cards.invent("Duskshell Creeper", "{1}{G}", "Creature — Insect", "When this creature enters, put a +1/+1 counter on target creature.\nEach creature you control with a +1/+1 counter on it has trample. (It can deal excess combat damage to the player or planeswalker it's attacking.)", "0/3")
    // chunk 09
    val doggedness = Cards.invent("Doggedness", "{1}{G}{G}", "Creature — Elemental Incarnation", "Flash\nReach\nWhen this creature enters, up to one target player puts all the cards from their graveyard on the bottom of their library in a random order.\nEvoke—Exile a green card from your hand.", "3/4")
    // chunk 09
    val fairyTribute = Cards.invent("Fairy Tribute", "{2}{G}", "Enchantment", "At the beginning of each end step, if you've cast both a creature spell and a noncreature spell this turn, create a Clue token, a Food token, and a Treasure token.")
    // chunk 09
    val thrivingBlow = Cards.invent("Thriving Blow", "{1}{G}", "Instant", "Choose one —\n• Thriving Blow deals 5 damage to target creature with flying.\n• Target creature gets +3/+3 until end of turn.\nEntwine {2}{G} (Choose both if you pay the entwine cost.)")
    // chunk 09
    val bedrockBreaker = Cards.invent("Bedrock Breaker", "{3}{G}", "Creature — Elemental", "When this creature enters, you may destroy target artifact or enchantment.\nEvoke {1}{G} (You may cast this spell for its evoke cost. If you do, it's sacrificed when it enters.)", "2/2")
    // chunk 09
    val funnelwebHermit = Cards.invent("Funnelweb Hermit", "{4}{G}", "Creature — Spider", "Reach\nMorbid — When this creature enters, if a creature died this turn, investigate. (Create a Clue token. It's an artifact with \"{2}, Sacrifice this token: Draw a card.\")", "3/5")
    // chunk 09
    val gaeasWish = Cards.invent("Gaea's Wish", null, "Sorcery", "Suspend 4—{G}\nUntil end of turn, you may play lands and cast spells from your graveyard.\nIf a card would be put into your graveyard from anywhere this turn, exile that card instead.")
    // chunk 09
    val shimmerBairn = Cards.invent("Shimmer Bairn", "{G}", "Creature — Ouphe", "Sacrifice a token: This creature gets +2/+2 until end of turn.", "1/2")
    // chunk 09
    val gleamingCreeper = Cards.invent("Gleaming Creeper", "{4}{G}", "Creature — Plant", "Converge — This creature enters with two +1/+1 counters on it for each color of mana spent to cast it.\nThis creature can't be blocked by creatures with power 2 or less.", "0/0")
    // chunk 09
    val droveBaloth = Cards.invent("Drove Baloth", "{3}{G}{G}", "Creature — Beast", "Whenever one or more +1/+1 counters are put on this creature, you may create a 4/4 green Beast creature token.", "4/4")
    // chunk 10
    val lowlyHierarch = Cards.invent("Lowly Hierarch", "{G}", "Creature — Goblin Shaman", "Exalted (Whenever a creature you control attacks alone, that creature gets +1/+1 until end of turn.)\n{T}: Add {B}, {R}, or {G}.", "0/1")
    // chunk 10
    val jadeRetaliator = Cards.invent("Jade Retaliator", "{1}{G}", "Creature — Frog Samurai", "Bushido 2 (Whenever this creature blocks or becomes blocked, it gets +2/+2 until end of turn.)", "2/2")
    // chunk 10
    val gemEyedViper = Cards.invent("Gem-Eyed Viper", "{2}{G}", "Creature — Snake", "Deathtouch\nWhen this creature dies, create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")", "3/1")
    // chunk 10
    val groveStrider = Cards.invent("Grove Strider", "{4}{G}{G}", "Creature — Treefolk", "When this creature enters, create two Food tokens. (They're artifacts with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")\nBasic landcycling {1}{G} ({1}{G}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)", "6/4")
    // chunk 10
    val riftSeeder = Cards.invent("Rift Seeder", "{2}{G}", "Creature — Elf Druid", "{T}: Add one mana of any color.\nSuspend 2—{G} (Rather than cast this card from your hand, you may pay {G} and exile it with two time counters on it. At the beginning of your upkeep, remove a time counter. When the last is removed, you may cast it without paying its mana cost. It has haste.)", "1/3")
    // chunk 10
    val sanctumSpinner = Cards.invent("Sanctum Spinner", "{1}{G}", "Enchantment Creature — Dryad", "{T}: Add X mana of any one color, where X is the number of enchantments you control.", "0/2")
    // chunk 10
    val scurryingOak = Cards.invent("Scurrying Oak", "{2}{G}", "Creature — Treefolk", "Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)\nWhenever one or more +1/+1 counters are put on this creature, you may create a 1/1 green Squirrel creature token.", "1/2")
    // chunk 10
    val scentOfDread = Cards.invent("Scent of Dread", "{1}{G}", "Sorcery", "Proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)\nTarget creature you control fights up to one target creature you don't control.")
    // chunk 10
    val squirrelRefuge = Cards.invent("Squirrel Refuge", "{G}", "Enchantment", "When this enchantment enters, create a 1/1 green Squirrel creature token.\nWhenever a nontoken creature you control dies, you may pay {1}. If you do, return this enchantment to its owner's hand.")
    // chunk 10
    val squirrelMonarch = Cards.invent("Squirrel Monarch", "{1}{G}", "Creature — Squirrel Noble", "Other Squirrels you control get +1/+1.", "2/2")
    // chunk 10
    val sylvanHymn = Cards.invent("Sylvan Hymn", "{G}{G}", "Enchantment", "Green creatures you control get +1/+1.\nWhenever a green creature you control enters, scry 1.")
    // chunk 10
    val groundmorph = Cards.invent("Groundmorph", "{3}{G}", "Sorcery", "Search your library for a basic land card, put it onto the battlefield, then shuffle.\nRebound (If you cast this spell from your hand, exile it as it resolves. At the beginning of your next upkeep, you may cast this card from exile without paying its mana cost.)")
    // chunk 10
    val thravosTempestsHowl = Cards.invent("Thravos, Tempest's Howl", "{10}{G}{G}", "Legendary Creature — Dinosaur", "This spell costs {3} less to cast for each other spell cast this turn.\nTrample, haste\nTrample over planeswalkers (This creature can deal excess combat damage to the controller of the planeswalker it's attacking.)\nThravos has hexproof as long as it entered this turn.", "7/7")
    // chunk 10
    val agelessWitness = Cards.invent("Ageless Witness", "{2}{G}{G}", "Creature — Human Shaman", "When this creature enters, return target card from your graveyard to your hand.\nEternalize {5}{G}{G} ({5}{G}{G}, Exile this card from your graveyard: Create a token that's a copy of it, except it's a 4/4 black Zombie Human Shaman with no mana cost. Eternalize only as a sorcery.)", "2/1")
    // chunk 10
    val untiringProvisioner = Cards.invent("Untiring Provisioner", "{2}{G}", "Creature — Elf Scout", "Landfall — Whenever a land you control enters, create a Food token or a Treasure token. (Food is an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\" Treasure is an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")", "3/2")
    // chunk 10
    val urbanKnifetooth = Cards.invent("Urban Knifetooth", "{2}{G}{G}", "Creature — Dinosaur", "Vigilance\nEnrage — Whenever this creature is dealt damage, proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)", "4/3")
    // chunk 10
    val verdantDecree = Cards.invent("Verdant Decree", "{1}{G}", "Instant", "Choose two —\n• Target player creates two tapped 1/1 green Squirrel creature tokens.\n• Counter target loyalty ability of a planeswalker.\n• Exile target card from a graveyard.\n• Target player gains 3 life.")
    // chunk 10
    val wrenhollowHydra = Cards.invent("Wrenhollow Hydra", "{X}{G}", "Creature — Hydra", "Reach\nThis creature enters with X +1/+1 counters on it.\nReinforce X—{X}{G}{G} ({X}{G}{G}, Discard this card: Put X +1/+1 counters on target creature.)", "0/0")
    // chunk 11
    val arcwiredHunter = Cards.invent("Arcwired Hunter", "{1}{R}{W}", "Artifact Creature — Cat Soldier", "First strike\nWhen this creature enters, put a +1/+1 counter on each other artifact creature you control.\nModular 2 (This creature enters with two +1/+1 counters on it. When it dies, you may put its +1/+1 counters on target artifact creature.)", "0/0")
    // chunk 11
    val arcusDevotee = Cards.invent("Arcus Devotee", "{G}{W}", "Creature — Human Cleric Archer", "Reach, lifelink\nOutlast {G/W} ({G/W}, {T}: Put a +1/+1 counter on this creature. Outlast only as a sorcery.)\nEach other creature you control without a +1/+1 counter on it has outlast {G/W}.", "2/2")
    // chunk 11
    val pollywhistlebrambleton = Cards.invent("Pollywhistlebrambleton", null, "Legendary Creature — Human Wizard", "As long as you've discarded a card this turn, you may pay {B/R} to cast this spell.\nWhen Pollywhistlebrambleton enters, you may search your library for a card named The Underworld Cookbook, reveal it, put it into your hand, then shuffle.\nSacrifice two Foods: Target creature deals 6 damage to itself.", "3/3")
    // chunk 11
    val breathlessPaladin = Cards.invent("Breathless Paladin", "{1}{W}{B}", "Creature — Spirit Knight", "Flying, lifelink\nWhenever this creature or another creature you control enters, if that creature entered from a graveyard or you cast it from a graveyard, put a +1/+1 counter on this creature.", "2/2")
    // chunk 11
    val snaredByLagacs = Cards.invent("Snared by Lagacs", "{1}{G}{W}", "Enchantment — Aura", "Enchant creature\nEnchanted creature can't attack or block.\nWhen this Aura enters, support 2. (Put a +1/+1 counter on each of up to two target creatures.)")
    // chunk 11
    val marthTheLynx = Cards.invent("Marth the Lynx", "{2}{B}{G}", "Legendary Creature — Human Warrior", "Whenever Marth enters or a planeswalker you control dies, look at the top seven cards of your library. You may reveal a planeswalker card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.\nPlaneswalkers' loyalty abilities you activate cost an additional [+1] to activate.", "3/5")
    // chunk 11
    val chromeHerald = Cards.invent("Chrome Herald", "{1}{W}{U}", "Artifact Creature — Thopter", "Flying\nWhen this creature enters, reveal the top two cards of your library. Put one of them into your hand and the other into your graveyard. If you put an artifact card into your hand this way, you gain 3 life.", "1/1")
    // chunk 11
    val mergeChrysalis = Cards.invent("Merge Chrysalis", "{G}{U}", "Artifact", "Creature tokens you control have flying.\n{2}{G}{U}, {T}, Sacrifice a token: Create a 4/4 green Beast creature token. Activate only as a sorcery.")
    // chunk 11
    val varrokShadowSlayer = Cards.invent("Varrok, Shadow Slayer", "{W}{U}{B}", "Legendary Planeswalker — Dakkon", "Varrok enters with a number of loyalty counters on him equal to the number of lands you control.\n[+1]: Surveil 2.\n[−3]: Exile target creature.\n[−6]: You may put an artifact card from your hand or graveyard onto the battlefield.", null, loyalty = "0")
    // chunk 11
    val dihadasGambit = Cards.invent("Dihada's Gambit", "{1}{U}{B}", "Instant", "Draw two cards, then discard a card. You gain life equal to the number of cards you've discarded this turn.\nJump-start (You may cast this card from your graveyard by discarding a card in addition to paying its other costs. Then exile this card.)")
    // chunk 11
    val dreyWarden = Cards.invent("Drey Warden", "{3}{B}{G}", "Creature — Elf Druid", "When this creature enters, create two 1/1 green Squirrel creature tokens.\n{3}{B}: Squirrels you control get +1/+0 and gain menace until end of turn.", "2/2")
    // chunk 11
    val etherforgedSphinx = Cards.invent("Etherforged Sphinx", "{7}{W}{U}", "Artifact Creature — Sphinx", "Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)\nFlying\nCascade (When you cast this spell, exile cards from the top of your library until you exile a nonland card with lesser mana value. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order.)", "4/4")
    // chunk 11
    val smelterHelix = Cards.invent("Smelter Helix", "{1}{R}{W}", "Instant", "As an additional cost to cast this spell, sacrifice a permanent.\nSmelter Helix deals 4 damage to any target. If the sacrificed permanent was an artifact, you gain 4 life.")
    // chunk 11
    val generalFerrusRokard = Cards.invent("General Ferrus Rokard", "{1}{R}{W}", "Legendary Creature — Human Soldier", "Hexproof from monocolored\nWhenever you cast a multicolored spell, create a 4/4 red and white Golem artifact creature token.", "3/1")
    // chunk 11
    val vesadroneDihada = Cards.invent("Vesadrone Dihada", "{1}{U}{B}{R}", "Legendary Planeswalker — Dihada", "Protection from permanents with corruption counters on them\n[+1]: Each opponent loses 2 life and you gain 2 life. Put a corruption counter on up to one other target creature or planeswalker.\n[−3]: Gain control of target creature or planeswalker until end of turn. Untap it and put a corruption counter on it. It gains haste until end of turn.\n[−7]: Gain control of each permanent with a corruption counter on it.", null, loyalty = "4")
    // chunk 11
    val goblinChaosmonger = Cards.invent("Goblin Chaosmonger", "{R}{G}", "Creature — Goblin Shaman", "Each spell you cast that's red or green costs {1} less to cast.", "2/2")
    // chunk 11
    val gentleRestoration = Cards.invent("Gentle Restoration", "{3}{W}{B}", "Sorcery", "Choose one —\n• Return target creature card from your graveyard to the battlefield with an additional +1/+1 counter on it.\n• Return up to two target creature cards with power 2 or less from your graveyard to the battlefield.")
    // chunk 11
    val gorvanHalfEye = Cards.invent("Gorvan Half-Eye", "{W}{U}{B}{R}{G}", "Legendary Creature — Human Wizard", "{T}: Choose a card name that hasn't been chosen from among Horizon Unmaking, Horizon Torrent, Horizon Dread, Horizon Wyrm, Horizon Regrowth, and Horizon Lotus. Create a copy of the card with the chosen name. You may cast the copy. (You still pay its costs.)", "5/5")
    // chunk 12
    val grastTheRavenousTide = Cards.invent("Grast, the Ravenous Tide", "{1}{B}{G}", "Legendary Planeswalker — Grist", "As long as Grast isn't on the battlefield, it's a 1/1 Insect creature in addition to its other types.\n[+1]: Create a 1/1 black and green Insect creature token, then mill a card. If an Insect card was milled this way, put a loyalty counter on Grast and repeat this process.\n[−2]: You may sacrifice a creature. When you do, destroy target creature or planeswalker.\n[−5]: Each opponent loses life equal to the number of creature cards in your graveyard.", null, loyalty = "3")
    // chunk 12
    val lazotepVizier = Cards.invent("Lazotep Vizier", "{U}{B}", "Creature — Zombie Wizard", "Whenever you discard a card, you may pay {1}. If you do, amass Zombies 2. (Put two +1/+1 counters on an Army you control. It's also a Zombie. If you don't control an Army, create a 0/0 black Zombie Army creature token first.)", "1/3")
    // chunk 12
    val lonasCryptobotanist = Cards.invent("Lonas, Cryptobotanist", "{G}{U}", "Legendary Creature — Snake Elf Scout", "Whenever another nontoken creature you control enters, investigate.\n{T}, Sacrifice X Clues: Target opponent reveals the top X cards of their library. You may put a nonland permanent card with mana value X or less from among them onto the battlefield under your control. That player puts the rest on the bottom of their library in a random order.", "1/2")
    // chunk 12
    val lordOfDemise = Cards.invent("Lord of Demise", "{1}{U}{B}", "Creature — Zombie Wizard", "When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)\nAt the beginning of your upkeep, if this card is in your graveyard, you may pay 1 life. If you do, return it to your hand.", "3/1")
    // chunk 12
    val temperance = Cards.invent("Temperance", "{1}{W}{U}", "Enchantment", "You can't cast more than one spell each turn.\nWhenever you cast a spell, draw a card.")
    // chunk 12
    val pirsuTheUnstable = Cards.invent("Pirsu, the Unstable", "{2}{R}{R}{W}{W}{B}{B}", "Legendary Creature — Elder Dragon", "Flying, lifelink\nAt the beginning of your upkeep, sacrifice Pirsu unless you pay {R}{W}{B}.\nWhen Pirsu dies, it deals 7 damage to each nonlegendary creature.", "7/7")
    // chunk 12
    val priestessOfFellRites = Cards.invent("Priestess of Fell Rites", "{W}{B}", "Creature — Human Warlock", "{T}, Pay 3 life, Sacrifice this creature: Return target creature card from your graveyard to the battlefield. Activate only as a sorcery.\nUnearth {3}{W}{B} ({3}{W}{B}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)", "2/2")
    // chunk 12
    val prescientTitan = Cards.invent("Prescient Titan", "{4}{U}{R}", "Creature — Giant Wizard", "Delirium — When this creature enters, choose one. If there are four or more card types among cards in your graveyard, choose both instead.\n• This creature deals 4 damage to any target.\n• Look at the top four cards of your library. Put one of them into your hand and the rest on the bottom of your library in a random order.", "4/4")
    // chunk 12
    val rakdosShowstopper = Cards.invent("Rakdos Showstopper", "{B}{R}", "Creature — Devil", "Haste\nEcho—Discard a card. (At the beginning of your upkeep, if this came under your control since the beginning of your last upkeep, sacrifice it unless you pay its echo cost.)", "3/3")
    // chunk 12
    val voraciousSquirrel = Cards.invent("Voracious Squirrel", "{B/G}", "Creature — Squirrel", "Whenever you sacrifice an artifact or creature, put a +1/+1 counter on this creature.\n{1}{B}{G}, Sacrifice an artifact or creature: You gain 1 life and draw a card.", "1/1")
    // chunk 12
    val stormGodsSeer = Cards.invent("Storm God's Seer", "{1}{U}{R}", "Enchantment Creature — Human Shaman", "{1}: This creature gets +1/-1 until end of turn.\nWhen this creature dies, it deals 3 damage to any target.", "1/3")
    // chunk 12
    val sythriaHarvestsHand = Cards.invent("Sythria, Harvest's Hand", "{G}{W}", "Legendary Enchantment Creature — Nymph", "Whenever you cast an enchantment spell, you gain 1 life and draw a card.", "1/2")
    // chunk 12
    val mortalAgony = Cards.invent("Mortal Agony", "{2}{B}{R}", "Sorcery", "Destroy target creature.\nMadness {B}{R} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)")
    // chunk 12
    val domainKavu = Cards.invent("Domain Kavu", "{R}{G}", "Creature — Kavu", "Domain — Domain Kavu's power and toughness are each equal to the number of basic land types among lands you control.\nWhenever this creature attacks, choose one —\n• Discard a card. If you do, draw a card.\n• Exile up to one target card from a graveyard.", "*/*")
    // chunk 12
    val sievesifter = Cards.invent("Sievesifter", "{3}{G}{U}", "Creature — Elemental", "Flying\nWhen this creature enters, investigate twice. (To investigate, create a Clue token. It's an artifact with \"{2}, Sacrifice this token: Draw a card.\")\nEvoke {G}{U} (You may cast this spell for its evoke cost. If you do, it's sacrificed when it enters.)", "3/2")
    // chunk 12
    val yuzraFortunesBlaze = Cards.invent("Yuzra, Fortune's Blaze", "{1}{U}{R}", "Legendary Creature — Efreet", "Flying\nWhenever Yuzra attacks, choose a number between 1 and 5. Flip that many coins. For each flip you win, draw a card. For each flip you lose, Yuzra deals 2 damage to you. If you won five flips this way, you may cast spells from your hand this turn without paying their mana costs.", "2/3")
    // chunk 13
    val academyFabricator = Cards.invent("Academy Fabricator", "{3}", "Artifact Creature — Assembly-Worker", "If you would create a Clue, Food, or Treasure token, instead create one of each.", "1/3")
    // chunk 13
    val shrineOfTheGoyf = Cards.invent("Shrine of the Goyf", "{5}", "Kindred Artifact — Lhurgoyf", "Whenever a creature you control attacks alone, it gets +X/+X until end of turn, where X is the number of card types among cards in all graveyards.\nLhurgoyf creatures you control have trample.")
    // chunk 13
    val battlemarrow = Cards.invent("Battlemarrow", "{2}", "Artifact — Equipment", "Living weapon (When this Equipment enters, create a 0/0 black Phyrexian Germ creature token, then attach this to it.)\nEquipped creature gets +1/+1 and has vigilance and lifelink.\nEquip {5}")
    // chunk 13
    val jarredGolems = Cards.invent("Jarred Golems", "{4}", "Artifact Creature — Golem", "Trample\nWhen this creature dies, you gain life equal to its power.", "3/3")
    // chunk 13
    val thoughtrock = Cards.invent("Thoughtrock", "{1}", "Artifact", "{2}, {T}, Sacrifice this artifact: Draw three cards, then put two cards from your hand on top of your library in any order.")
    // chunk 13
    val dermocab = Cards.invent("Dermocab", "{2}", "Artifact — Vehicle", "Imprint — As this Vehicle enters, exile a creature card from a graveyard.\nTap two untapped creatures you control: Until end of turn, this Vehicle becomes a copy of the exiled card, except it's a Vehicle artifact in addition to its other types.", "0/0")
    // chunk 13
    val adamantLion = Cards.invent("Adamant Lion", "{2}", "Artifact Creature — Cat", "{T}, Discard your hand, Sacrifice this creature: Add three mana of any one color. Activate only as an instant.", "2/2")
    // chunk 13
    val chaffTosser = Cards.invent("Chaff Tosser", "{3}", "Artifact", "{T}, Discard a card: This artifact deals 2 damage to target player or planeswalker.")
    // chunk 13
    val valdraCompleat = Cards.invent("Valdra Compleat", "{7}", "Legendary Artifact — Equipment", "Living weapon\nIndestructible\nEquipped creature gets +5/+5 and has first strike, trample, indestructible, haste, and \"Whenever this creature deals combat damage to a creature, exile that creature.\"\nEquip {7}")
    // chunk 13
    val liquimetalWrench = Cards.invent("Liquimetal Wrench", "{2}", "Artifact", "{T}: Add {C}.\n{T}: Target nonland permanent becomes an artifact in addition to its other types until end of turn.")
    // chunk 13
    val singleskelion = Cards.invent("Singleskelion", "{2}", "Artifact Creature — Construct", "This creature enters with a +1/+1 counter on it.\n{1}, Remove a +1/+1 counter from this creature: It deals 1 damage to any target.", "1/1")
    // chunk 13
    val myrScrapper = Cards.invent("Myr Scrapper", "{1}", "Artifact Creature — Myr", "Sacrifice this creature: Put a +1/+1 counter on target creature.", "1/1")
    // chunk 13
    val briarcyst = Cards.invent("Briarcyst", "{3}", "Artifact — Equipment", "Living weapon (When this Equipment enters, create a 0/0 black Phyrexian Germ creature token, then attach this to it.)\nEquipped creature gets +1/+1 for each artifact and/or enchantment you control.\nEquip {2}")
    // chunk 13
    val ornithopterOfBliss = Cards.invent("Ornithopter of Bliss", "{2}", "Artifact Creature — Thopter", "Flying\n{T}: Add one mana of any color.", "0/2")
    // chunk 13
    val havenRaptor = Cards.invent("Haven Raptor", "{3}", "Artifact Creature — Bird", "Flying\nWhenever this creature attacks, if you control three or more tokens, this creature gets +2/+0 and gains first strike until end of turn.", "2/1")
    // chunk 13
    val heirOfDraco = Cards.invent("Heir of Draco", "{12}", "Artifact Creature — Dragon", "Domain — This spell costs {2} less to cast for each basic land type among lands you control.\nFlying\nEach creature you control has vigilance if it's white, hexproof if it's blue, lifelink if it's black, first strike if it's red, and trample if it's green.", "4/4")
    // chunk 13
    val wayfarersCompanion = Cards.invent("Wayfarer's Companion", "{7}", "Artifact Creature — Salamander", "Affinity for artifacts\nArtifact landcycling {2} ({2}, Discard this card: Search your library for an artifact land card, reveal it, put it into your hand, then shuffle.)", "4/4")
    // chunk 13
    val sunTalisman = Cards.invent("Sun Talisman", null, "Artifact", "Suspend 3—{1} (Rather than cast this card from your hand, pay {1} and exile it with three time counters on it. At the beginning of your upkeep, remove a time counter. When the last is removed, you may cast it without paying its mana cost.)\n{T}: Add {C}{C}.")
    // chunk 13
    val ironDromedary = Cards.invent("Iron Dromedary", "{3}", "Artifact Creature — Camel", "This creature enters tapped with two +1/+1 counters on it.\nThis creature doesn't untap during your untap step if it has a +1/+1 counter on it.\nAt the beginning of combat on your turn, you may move a +1/+1 counter from this creature onto target creature.", "2/2")
    // chunk 13
    val bladeOfHearthAndField = Cards.invent("Blade of Hearth and Field", "{3}", "Artifact — Equipment", "Equipped creature gets +2/+2 and has protection from green and from white.\nWhenever equipped creature deals combat damage to a player, exile up to one target creature you own, then search your library for a basic land card. Put both cards onto the battlefield under your control, then shuffle.\nEquip {2}")
    // chunk 13
    val tormodsGravekeeper = Cards.invent("Tormod's Gravekeeper", "{3}", "Artifact Creature — Golem", "Vigilance\n{T}, Sacrifice this creature: Exile target player's graveyard.", "3/2")
    // chunk 13
    val theNetherworldCookbook = Cards.invent("The Netherworld Cookbook", "{1}", "Artifact — Book", "{T}, Discard a card: Create a Food token. (It's an artifact with \"{2}, {T}, Sacrifice this token: You gain 3 life.\")\n{4}, {T}, Sacrifice this artifact: Return target creature card from your graveyard to your hand.")
    // chunk 13
    val vectisGauntlets = Cards.invent("Vectis Gauntlets", "{2}", "Artifact — Equipment", "Equipped creature gets +2/+0 and has artifact landwalk. (It can't be blocked as long as defending player controls an artifact land.)\nEquip {2}")
    // chunk 13
    val nullMirror = Cards.invent("Null Mirror", "{2}", "Artifact", "Whenever a player casts a spell, if no colored mana was spent to cast it, counter that spell.")
    // chunk 13
    val zebazTheGlimmerwing = Cards.invent("Zebaz, the Glimmerwing", "{1}", "Legendary Artifact Creature — Insect", "Modular 1\nIf a modular triggered ability would put one or more +1/+1 counters on a creature you control, that many plus one +1/+1 counters are put on it instead.\n{R}: Destroy target artifact you control.\n{W}: Zebaz gains flying until end of turn.", "0/0")
    // chunk 14
    val powerStorehouse = Cards.invent("Power Storehouse", null, "Artifact Land", "This land enters tapped.\n{T}: Add {C}.\n{T}: Add one mana of any color. Spend this mana only to cast artifact spells or activate abilities of artifacts.\nModular 1")
    // chunk 14
    val urzasEpic = Cards.invent("Urza's Epic", null, "Enchantment Land — Urza's Saga", "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)\nI — This Saga gains \"{T}: Add {C}.\"\nII — This Saga gains \"{2}, {T}: Create a 0/0 colorless Construct artifact creature token with 'This token gets +1/+1 for each artifact you control.'\"\nIII — Search your library for an artifact card with mana cost {0} or {1}, put it onto the battlefield, then shuffle.")
    // chunk 14
    val yavimayaCradleOfRoots = Cards.invent("Yavimaya, Cradle of Roots", null, "Legendary Land", "Each land is a Forest in addition to its other land types.")
    // chunk 15
    val seraphicCurator = Cards.invent("Seraphic Curator", "{1}{W}", "Creature — Angel Spirit", "Flying, protection from artifacts", "1/1")
    // chunk 15
    val karmicShepherd = Cards.invent("Karmic Shepherd", "{3}{W}{W}", "Creature — Angel Spirit", "Flying, protection from black\nEcho {3}{W}{W} (At the beginning of your upkeep, if this came under your control since the beginning of your last upkeep, sacrifice it unless you pay its echo cost.)\nWhen this creature enters, return target creature card from your graveyard to the battlefield.", "2/2")
    // chunk 15
    val sigilOfCleansing = Cards.invent("Sigil of Cleansing", "{1}{W}", "Enchantment", "Sacrifice this enchantment: Destroy target artifact or enchantment.")
    // chunk 15
    val lonelyConfinement = Cards.invent("Lonely Confinement", "{2}{W}", "Enchantment", "At the beginning of your upkeep, sacrifice this enchantment unless you discard a card.\nSkip your draw step.\nYou have shroud. (You can't be the target of spells or abilities.)\nPrevent all damage that would be dealt to you.")
    // chunk 15
    val spiritSnare = Cards.invent("Spirit Snare", "{W}", "Enchantment", "{W}, Sacrifice this enchantment: Exile target creature that's attacking you or a planeswalker you control.")
    // chunk 15
    val spellRefusal = Cards.invent("Spell Refusal", "{U}{U}", "Instant", "Counter target spell.")
    // chunk 15
    val brineDrake = Cards.invent("Brine Drake", "{2}{U}", "Creature — Drake", "Flying\nWhen this creature enters, return two target lands you control to their owner's hand.", "4/3")
    // chunk 15
    val sigilOfRemoval = Cards.invent("Sigil of Removal", "{U}", "Enchantment", "Sacrifice this enchantment: Return target creature to its owner's hand.")
    // chunk 15
    val upturning = Cards.invent("Upturning", "{4}{U}{U}", "Sorcery", "Return all permanents to their owners' hands.")
    // chunk 15
    val amazement = Cards.invent("Amazement", "{3}{U}", "Creature — Incarnation", "Flying\nAs long as this card is in your graveyard and you control an Island, creatures you control have flying.", "2/2")
    // chunk 15
    val boneShearer = Cards.invent("Bone Shearer", "{2}{B}", "Creature — Phyrexian Minion", "Flying\nEcho {2}{B} (At the beginning of your upkeep, if this came under your control since the beginning of your last upkeep, sacrifice it unless you pay its echo cost.)\nWhen this creature enters, destroy target nonartifact, nonblack creature.", "1/1")
    // chunk 15
    val braidaCabalServant = Cards.invent("Braida, Cabal Servant", "{2}{B}{B}", "Legendary Creature — Human Minion", "At the beginning of each player's upkeep, that player sacrifices an artifact, creature, or land of their choice.", "2/2")
    // chunk 15
    val covetousness = Cards.invent("Covetousness", "{3}{B}", "Enchantment", "{B}, Pay 2 life: Draw a card.")
    // chunk 15
    val patriarchsSummons = Cards.invent("Patriarch's Summons", "{3}{B}{B}", "Sorcery", "Each player chooses a creature type. Each player returns all creature cards of a type chosen this way from their graveyard to the battlefield.")
    // chunk 15
    val skirgeCompanion = Cards.invent("Skirge Companion", "{4}{B}", "Creature — Phyrexian Imp", "Flying\nDiscard a card: Add {B}.", "3/2")
    // chunk 15
    val luckyEncounter = Cards.invent("Lucky Encounter", "{2}{R}{R}", "Enchantment", "Whenever you win a coin flip, put a luck counter on this enchantment.\nAt the beginning of your upkeep, if this enchantment has ten or more luck counters on it, you win the game.")
    // chunk 15
    val flameFissure = Cards.invent("Flame Fissure", "{1}{R}", "Sorcery", "Flame Fissure deals 4 damage to each player.")
    // chunk 15
    val goblinShellfire = Cards.invent("Goblin Shellfire", "{1}{R}", "Enchantment", "Sacrifice a creature: This enchantment deals 1 damage to any target.")
    // chunk 15
    val gorillaMystic = Cards.invent("Gorilla Mystic", "{R}", "Creature — Ape Shaman", "{X}{X}{1}: Destroy target noncreature artifact with mana value X.", "1/1")
    // chunk 15
    val imperialEnlister = Cards.invent("Imperial Enlister", "{2}{R}", "Creature — Human Advisor", "When this creature enters, search your library for a creature card with power 2 or less, reveal it, put it into your hand, then shuffle.", "1/1")
    // chunk 15
    val moggScavenging = Cards.invent("Mogg Scavenging", "{2}{R}", "Instant", "If an opponent controls an Island and you control a Mountain, you may cast this spell without paying its mana cost.\nDestroy target artifact.")
    // chunk 16
    val enchantresssInfluence = Cards.invent("Enchantress's Influence", "{2}{G}", "Enchantment", "Whenever you cast an enchantment spell, draw a card.")
    // chunk 16
    val prowlingPack = Cards.invent("Prowling Pack", "{5}{G}{G}", "Instant", "Create a 4/4 green Beast creature token.\nStorm (When you cast this spell, copy it for each spell cast before it this turn.)")
    // chunk 16
    val quirionWarden = Cards.invent("Quirion Warden", "{G}", "Creature — Elf Ranger", "Return a Forest you control to its owner's hand: Untap target creature. Activate only once each turn.", "1/1")
    // chunk 16
    val squirrelThrong = Cards.invent("Squirrel Throng", "{1}{G}{G}", "Creature — Squirrel", "This creature gets +1/+1 for each other Squirrel on the battlefield.", "2/2")
    // chunk 16
    val titanyaKeeperOfArgoth = Cards.invent("Titanya, Keeper of Argoth", "{3}{G}{G}", "Legendary Creature — Elemental", "When Titanya enters, return target land card from your graveyard to the battlefield.\nWhenever a land you control is put into a graveyard from the battlefield, create a 5/3 green Elemental creature token.", "5/3")
    // chunk 16
    val yavimayaAncient = Cards.invent("Yavimaya Ancient", "{1}{G}{G}", "Creature — Human Druid", "When this creature dies, you may search your library for up to two basic land cards, reveal them, put them into your hand, then shuffle.\n{2}, Sacrifice this creature: Draw a card.", "2/1")
    // chunk 16
    val charnelNightmareAdept = Cards.invent("Charnel, Nightmare Adept", "{2}{B}{R}", "Legendary Creature — Human Minion", "Discard a card: You may cast a creature spell from your graveyard this turn. Activate only once each turn.\nWhenever a nontoken creature you control enters, if you didn't cast it from your hand, it gains haste until your next turn.", "3/2")
    // chunk 16
    val mirarisEcho = Cards.invent("Mirari's Echo", "{3}{G}{W}", "Enchantment", "Creatures you control get +1/+1.\nWhenever you tap a land for mana, add one mana of any type that land produced.")
    // chunk 16
    val shardboundEnvoy = Cards.invent("Shardbound Envoy", "{1}{G}{U}", "Artifact Creature — Human Rogue", "Cascade (When you cast this spell, exile cards from the top of your library until you exile a nonland card that costs less. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order.)", "2/2")
    // chunk 16
    val silverleafGrove = Cards.invent("Silverleaf Grove", "{G}{W}", "Enchantment", "Other enchantments you control have shroud. (They can't be the targets of spells or abilities.)\n{1}, Sacrifice this enchantment: Search your library for an enchantment card, reveal it, then shuffle and put that card on top.")
    // chunk 16
    val vindication = Cards.invent("Vindication", "{1}{W}{B}", "Sorcery", "Destroy target permanent.")
    // chunk 16
    val cursedIdol = Cards.invent("Cursed Idol", "{2}", "Artifact", "Activated abilities of creatures can't be activated.")
    // chunk 16
    val extrusionGolem = Cards.invent("Extrusion Golem", "{4}", "Artifact Creature — Juggernaut", "Echo {4} (At the beginning of your upkeep, if this came under your control since the beginning of your last upkeep, sacrifice it unless you pay its echo cost.)\nSacrifice an artifact: Put a +1/+1 counter on target creature.", "4/3")
    // chunk 16
    val grindlet = Cards.invent("Grindlet", "{2}", "Artifact Creature — Construct", "{T}, Mill a card: Add {C}. (Activate only as an instant. To mill a card, put the top card of your library into your graveyard.)", "0/1")
    // chunk 16
    val nevinyrralsPlatter = Cards.invent("Nevinyrral's Platter", "{4}", "Artifact", "This artifact enters tapped.\n{1}, {T}: Destroy all artifacts, creatures, and enchantments.")
    // chunk 16
    val quiltworkGnomes = Cards.invent("Quiltwork Gnomes", "{3}", "Artifact Creature — Gnome", "Discard a card: Regenerate this creature. (The next time this creature would be destroyed this turn, instead tap it, remove it from combat, and heal all damage on it.)", "2/1")
    // chunk 16
    val zuranSphere = Cards.invent("Zuran Sphere", "{0}", "Artifact", "Sacrifice a land: You gain 2 life.")
    // chunk 16
    val cabalVaults = Cards.invent("Cabal Vaults", null, "Land", "{2}, {T}: Add {B} for each Swamp you control.")
    // chunk 16
    val mishrasWorkfloor = Cards.invent("Mishra's Workfloor", null, "Land", "{T}: Add {C}.\n{1}: This land becomes a 2/2 Assembly-Worker artifact creature until end of turn. It's still a land.\n{T}: Target Assembly-Worker creature gets +1/+1 until end of turn.")
    // chunk 16
    val riptideWorkshop = Cards.invent("Riptide Workshop", null, "Land", "{T}: Add {C}.\n{1}{U}, {T}: Return target Wizard you control to its owner's hand.")
    // chunk 16
    val sanctumHierarch = Cards.invent("Sanctum Hierarch", "{1}{W}{W}", "Creature — Human Cleric", "As this creature enters, choose a number.\nNoncreature spells with mana value equal to the chosen number can't be cast.", "2/2")
    val spokenFinished = Cards.inventFaces("Split", OracleCard("Spoken", "{2}{U}", "Sorcery", "Return target instant or sorcery card from your graveyard to your hand."), OracleCard("Finished", "{3}{U}", "Instant", "Tap up to two target creatures. Those creatures don't untap during their controller's next untap step."))
    val swiftFeral = Cards.inventFaces("Split", OracleCard("Swift", "{2}{R}", "Instant", "Discard a card, then draw two cards."), OracleCard("Feral", "{3}{R}{R}", "Sorcery", "Feral deals 3 damage to each creature without flying."))
    val wayWreck = Cards.inventFaces("Split", OracleCard("Way", "{2}{G}", "Instant", "Search your library for a basic land card, put it onto the battlefield tapped, then shuffle."), OracleCard("Wreck", "{1}{R}{R}", "Sorcery", "Aftermath (Cast this spell only from your graveyard. Then exile it.)\nWreck deals damage to target creature equal to the number of lands you control."))
    val smolderSleet = Cards.inventFaces("Split", OracleCard("Smolder", "{1}{R}", "Instant", "Smolder deals 2 damage divided as you choose among one or two targets."), OracleCard("Sleet", "{1}{U}", "Instant", "Tap target permanent.\nDraw a card."))
    val parchedButte = Cards.invent("Parched Butte", null, "Land", "{T}, Pay 1 life, Sacrifice this land: Search your library for a Mountain or Plains card, put it onto the battlefield, then shuffle.")
    val bogBarrens = Cards.invent("Bog Barrens", null, "Land", "{T}, Pay 1 life, Sacrifice this land: Search your library for a Plains or Swamp card, put it onto the battlefield, then shuffle.")
    val fogboundJungle = Cards.invent("Fogbound Jungle", null, "Land", "{T}, Pay 1 life, Sacrifice this land: Search your library for a Forest or Island card, put it onto the battlefield, then shuffle.")
    val boilingMere = Cards.invent("Boiling Mere", null, "Land", "{T}, Pay 1 life, Sacrifice this land: Search your library for an Island or Mountain card, put it onto the battlefield, then shuffle.")
    val mossyCrypts = Cards.invent("Mossy Crypts", null, "Land", "{T}, Pay 1 life, Sacrifice this land: Search your library for a Swamp or Forest card, put it onto the battlefield, then shuffle.")
    val duskmossSpan = Cards.invent("Duskmoss Span", null, "Artifact Land", "This land enters tapped.\nIndestructible\n{T}: Add {B} or {G}.")
    val slagforgeSpan = Cards.invent("Slagforge Span", null, "Artifact Land", "This land enters tapped.\nIndestructible\n{T}: Add {B} or {R}.")
    val gildmireSpan = Cards.invent("Gildmire Span", null, "Artifact Land", "This land enters tapped.\nIndestructible\n{T}: Add {W} or {B}.")
    val fogvaultSpan = Cards.invent("Fogvault Span", null, "Artifact Land", "This land enters tapped.\nIndestructible\n{T}: Add {U} or {B}.")
    val bladetideSpan = Cards.invent("Bladetide Span", null, "Artifact Land", "This land enters tapped.\nIndestructible\n{T}: Add {W} or {U}.")
    val rustdaleSpan = Cards.invent("Rustdale Span", null, "Artifact Land", "This land enters tapped.\nIndestructible\n{T}: Add {R} or {W}.")
    val silvercragSpan = Cards.invent("Silvercrag Span", null, "Artifact Land", "This land enters tapped.\nIndestructible\n{T}: Add {U} or {R}.")
    val cinderwoodsSpan = Cards.invent("Cinderwoods Span", null, "Artifact Land", "This land enters tapped.\nIndestructible\n{T}: Add {R} or {G}.")
    val snarlpoolSpan = Cards.invent("Snarlpool Span", null, "Artifact Land", "This land enters tapped.\nIndestructible\n{T}: Add {G} or {U}.")
    val briarglintSpan = Cards.invent("Briarglint Span", null, "Artifact Land", "This land enters tapped.\nIndestructible\n{T}: Add {G} or {W}.")
}
