package arcanum.engine.sets.fin

import arcanum.engine.cards.Cards
import arcanum.engine.cards.face

/**
 * The invented Final Fantasy (fin) cards of the parser tests (`arcanum.parser.sets.fin`), the same names and texts, for the
 * scenarios: each property registers its card and holds its name.
 */
internal object FinSet {
    // chunk 01
    val shadowcastersWand = Cards.invent("Shadowcaster's Wand", "{1}{B}", "Artifact — Equipment", """
        Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
        Equipped creature gets +1/+0, has "Whenever you cast a noncreature spell, this creature deals 1 damage to each opponent," and is a Wizard in addition to its other types.
        Equip {3}
    """.trimIndent())
    val berserkersGreataxe = Cards.invent("Berserker's Greataxe", "{3}{R}", "Artifact — Equipment", """
        Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
        Equipped creature gets +3/+2 and is a Warrior in addition to its other types.
        Equip {5} ({5}: Attach to target creature you control. Equip only as a sorcery.)
    """.trimIndent())
    val oathkeepersPlate = Cards.invent("Oathkeeper's Plate", "{2}{W}", "Artifact — Equipment", """
        Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
        Equipped creature gets +2/+1, has ward {1}, and is a Knight in addition to its other types.
        Lightbringer and Hero's Shield — Equip {4} ({4}: Attach to target creature you control. Equip only as a sorcery.)
    """.trimIndent())
    val wyrmridersLance = Cards.invent("Wyrmrider's Lance", "{1}{W}", "Artifact — Equipment", """
        Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
        Equipped creature gets +1/+0 and is a Knight in addition to its other types.
        During your turn, equipped creature has flying.
        Gae Bolg — Equip {4}
    """.trimIndent())
    val stargazersOrrery = Cards.invent("Stargazer's Orrery", "{1}{U}", "Artifact — Equipment", """
        Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
        Equipped creature is a Wizard in addition to its other types and has "Whenever you cast a noncreature spell and whenever you draw your third card each turn, put a +1/+1 counter on this creature."
        Diana — Equip {2}
    """.trimIndent())
    val shinobiTwinEdges = Cards.invent("Shinobi Twin Edges", "{2}{B}", "Artifact — Equipment", """
        Job select
        Equipped creature gets +1/+1, is a Ninja in addition to its other types, and has "Whenever this creature deals combat damage to a player, draw a card, then discard a card. That player loses life equal to the discarded card's mana value."
        Mutsunokami — Equip {2}
    """.trimIndent())
    val pickpocketsDirk = Cards.invent("Pickpocket's Dirk", "{2}{U}", "Artifact — Equipment", """
        Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
        Equipped creature gets +1/+1, has "Whenever this creature deals combat damage to a player, draw a card," and is a Rogue in addition to its other types.
        Equip {4}
    """.trimIndent())
    val roninsNodachi = Cards.invent("Ronin's Nodachi", "{2}{R}", "Artifact — Equipment", """
        Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
        Equipped creature gets +2/+2, has trample and haste, and is a Samurai in addition to its other types.
        Murasame — Equip {5}
    """.trimIndent())
    val troubadoursLongbow = Cards.invent("Troubadour's Longbow", "{2}{G}", "Artifact — Equipment", """
        Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
        Equipped creature gets +2/+2, has reach, and is a Bard in addition to its other types.
        Perseus's Bow — Equip {6} ({6}: Attach to target creature you control. Equip only as a sorcery.)
    """.trimIndent())
    val mystikPetalblades = Cards.invent("Mystik Petalblades", "{1}{U}", "Artifact — Equipment", """
        Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
        Equipped creature gets +1/+0, has "Whenever this creature attacks, untap target attacking creature," and is a Cleric in addition to its other types.
        Hagneia — Equip {3}
    """.trimIndent())
    val crimsonDuelistsFoil = Cards.invent("Crimson Duelist's Foil", "{1}{R}", "Artifact — Equipment", """
        Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
        Equipped creature has "Whenever you cast a noncreature spell, this creature gets +2/+0 until end of turn" and is a Wizard in addition to its other types.
        Equip {3}
    """.trimIndent())
    val mendersCrook = Cards.invent("Mender's Crook", "{1}{W}", "Artifact — Equipment", """
        Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
        Equipped creature gets +1/+1, has "Whenever this creature attacks, you gain 1 life," and is a Cleric in addition to its other types.
        Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
    """.trimIndent())
    val abyssKnightsClaymore = Cards.invent("Abyss Knight's Claymore", "{2}{B}", "Artifact — Equipment", """
        Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
        Equipped creature gets +3/+0 and is a Knight in addition to its other types.
        Chaosbringer — Equip—Pay 3 life. Activate only once each turn.
    """.trimIndent())
    val pugilistsCesti = Cards.invent("Pugilist's Cesti", "{2}", "Artifact — Equipment", """
        Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
        Equipped creature gets +1/+0 and is a Monk in addition to its other types.
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """.trimIndent())
    val conjurersTome = Cards.invent("Conjurer's Tome", "{3}{G}", "Artifact — Book Equipment", """
        Job select
        Equipped creature is a Shaman in addition to its other types and has "Whenever this creature attacks, you may put a creature card from your hand onto the battlefield. If that card is an enchantment card, it enters tapped and attacking."
        Abraxas — Equip {3}
    """.trimIndent())
    val tinkerersArsenal = Cards.invent("Tinkerer's Arsenal", "{4}{W}", "Artifact — Equipment", """
        Job select (When this Equipment enters, create a 1/1 colorless Hero creature token, then attach this to it.)
        Equipped creature gets +2/+2 for each artifact you control and is an Artificer in addition to its other types.
        Machina — Equip {4} ({4}: Attach to target creature you control. Equip only as a sorcery.)
    """.trimIndent())
    // chunk 02
    val arenaColossus = Cards.invent("Arena Colossus", "{5}{G}{G}", "Creature — Beast", """
        Trample
        When this creature enters, choose one —
        • Destroy target artifact or enchantment.
        • Draw a card.
    """.trimIndent(), "7/7")
    val frostLitany = Cards.invent("Frost Litany", "{1}{U}", "Instant", """
        Tiered (Choose one additional cost.)
        • Blizzard — {0} — Return target creature to its owner's hand.
        • Blizzara — {2} — Target creature's owner puts it on their choice of the top or bottom of their library.
        • Blizzaga — {5}{U} — Target creature's owner shuffles it into their library.
    """.trimIndent())
    val clashOfTheTitans = Cards.invent("Clash of the Titans", "{G}", "Sorcery", """
        Choose one or more —
        • Target creature you control fights target creature an opponent controls.
        • Remove a lore counter from target Saga you control. (Removing lore counters doesn't cause chapter abilities to trigger.)
        • Put a lore counter on target Saga you control.
    """.trimIndent())
    val emberLitany = Cards.invent("Ember Litany", "{R}", "Instant", """
        Tiered (Choose one additional cost.)
        • Fire — {0} — Ember Litany deals 1 damage to each creature.
        • Fira — {2} — Ember Litany deals 2 damage to each creature.
        • Firaga — {5} — Ember Litany deals 3 damage to each creature.
    """.trimIndent())
    val angelFeather = Cards.invent("Angel Feather", "{W}", "Artifact", """
        {1}{W}, {T}, Exile this artifact: Choose one —
        • Return target creature card with mana value 4 or less from your graveyard to the battlefield tapped.
        • Exile target Skeleton, Spirit, or Zombie.
    """.trimIndent())
    val beastformCrescendo = Cards.invent("Beastform Crescendo", "{1}{B}", "Instant", """
        Tiered (Choose one additional cost.)
        Until end of turn, target creature you control gains "When this creature dies, return it to the battlefield tapped under its owner's control" and has the chosen base power and toughness.
        • Galian Beast — {0} — 3/2.
        • Death Gigas — {1} — 5/2.
        • Hellmasker — {3} — 7/2.
    """.trimIndent())
    val brawlersCrescendo = Cards.invent("Brawler's Crescendo", "{G}", "Instant", """
        Tiered (Choose one additional cost.)
        • Somersault — {0} — Target creature gets +2/+2 until end of turn.
        • Meteor Strikes — {2} — Double target creature's power and toughness until end of turn.
        • Final Heaven — {6}{G} — Triple target creature's power and toughness until end of turn.
    """.trimIndent())
    val tacticsMenu = Cards.invent("Tactics Menu", "{1}{W}", "Instant", """
        Choose one —
        • Attack — Create a 2/2 white Knight creature token.
        • Ability — Target creature gets +0/+4 until end of turn.
        • Magic — Destroy target creature with power 4 or greater.
        • Item — You gain 4 life.
    """.trimIndent())
    val rescueByTheStairs = Cards.invent("Rescue by the Stairs", "{3}{W}", "Sorcery", """
        Choose one —
        • Take the Elevator — Create three 1/1 colorless Hero creature tokens.
        • Take 59 Flights of Stairs — Tap up to three target creatures. Put a stun counter on one of them. (If a permanent with a stun counter would become untapped, remove one from it instead.)
    """.trimIndent())
    val ironfistedJudge = Cards.invent("Ironfisted Judge", "{2}{B}{B}", "Legendary Creature — Human Soldier", """
        When Ironfisted Judge enters, choose one —
        • Each player sacrifices a creature token of their choice.
        • Each player sacrifices a nontoken creature of their choice.
        • Each player sacrifices an enchantment of their choice.
    """.trimIndent(), "3/2")
    val serenadeOfTheStage = Cards.invent("Serenade of the Stage", "{1}{R}", "Instant", """
        Choose one —
        • Exile the top two cards of your library. You may play those cards until your next end step.
        • One or two target creatures each get +2/+0 until end of turn.
    """.trimIndent())
    val ropeSlam = Cards.invent("Rope Slam", "{1}{R}", "Sorcery", """
        Choose one —
        • Rope Slam deals 3 damage to target creature. If that creature would die this turn, exile it instead.
        • Exile target artifact.
    """.trimIndent())
    val callersHomecoming = Cards.invent("Caller's Homecoming", "{3}{G}{G}", "Sorcery", """
        Choose one —
        • Creatures you control get +3/+3 until end of turn.
        • Return up to two target permanent cards from your graveyard to your hand.
    """.trimIndent())
    val graveGnawer = Cards.invent("Grave Gnawer", "{2}{B}", "Creature — Zombie Horror", """
        When this creature enters, choose one —
        • Destroy target creature that was dealt damage this turn.
        • Exile up to two target cards from a single graveyard.
    """.trimIndent(), "3/2")
    val stormLitany = Cards.invent("Storm Litany", "{R}", "Instant", """
        Tiered (Choose one additional cost.)
        • Thunder — {0} — Storm Litany deals 2 damage to target creature.
        • Thundara — {3} — Storm Litany deals 4 damage to target creature.
        • Thundaga — {5}{R} — Storm Litany deals 8 damage to target creature.
    """.trimIndent())
    val mendingLitany = Cards.invent("Mending Litany", "{W}", "Instant", """
        Tiered (Choose one additional cost.)
        • Cure — {0} — Target permanent gains hexproof and indestructible until end of turn.
        • Cura — {1} — Target permanent gains hexproof and indestructible until end of turn. You gain 3 life.
        • Curaga — {3}{W} — Permanents you control gain hexproof and indestructible until end of turn. You gain 6 life.
    """.trimIndent())
    val taintedSpring = Cards.invent("Tainted Spring", "{1}{B}", "Sorcery", """
        Choose one —
        • All creatures get -1/-1 until end of turn.
        • Target player reveals their hand. You choose an artifact or creature card from it. That player discards that card.
    """.trimIndent())
    // chunk 03
    val conjureThreefoldHound = Cards.invent("Conjure: Threefold Hound", "{2}{R}{R}", "Enchantment Creature — Saga Dog", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        II — Double — When you next cast an instant or sorcery spell this turn, copy it. You may choose new targets for the copy.
        III — Triple — When you next cast an instant or sorcery spell this turn, copy it twice. You may choose new targets for the copies.
    """.trimIndent(), "3/3")
    val conjureRotundStrider = Cards.invent("Conjure: Rotund Strider", "{4}{G}", "Enchantment Creature — Saga Bird", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I — Wark — Create a 2/2 green Bird creature token with "Whenever a land you control enters, this token gets +1/+0 until end of turn."
        II, III, IV — Kerplunk — Creatures you control gain trample until end of turn.
    """.trimIndent(), "4/4")
    val conjureMoonhowlWolf = Cards.invent("Conjure: Moonhowl Wolf", "{2}{G}", "Enchantment Creature — Saga Wolf", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Crescent Fang — Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
        II — Heavenward Howl — When you next cast a creature spell this turn, that creature enters with an additional +1/+1 counter on it.
        III — Ecliptic Growl — Draw a card if you control the creature with the greatest power or tied for the greatest power.
    """.trimIndent(), "3/2")
    val conjureGaleHarpy = Cards.invent("Conjure: Gale Harpy", "{3}{W}", "Enchantment Creature — Saga Harpy", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Aerial Blast — This creature deals 4 damage to target tapped creature an opponent controls.
        II, III — Slipstream — Another target creature you control gets +1/+0 and gains flying until end of turn.
        Flying
    """.trimIndent(), "3/3")
    val conjureChainboundValkyrie = Cards.invent("Conjure: Chainbound Valkyrie", "{1}{R}", "Enchantment Creature — Saga Knight", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Chain — Exile the top card of your library. During any turn you put a lore counter on this Saga, you may play that card.
        II, III — Gestalt Mode — When you next cast a creature spell this turn, it gains haste until end of turn.
    """.trimIndent(), "2/1")
    val conjureRoundtablePaladins = Cards.invent("Conjure: Roundtable Paladins", "{6}{W}{W}", "Enchantment Creature — Saga Knight", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after V.)
        I, II, III, IV — Create three 2/2 white Knight creature tokens.
        V — Ultimate End — Other creatures you control get +2/+2 until end of turn. Put an indestructible counter on each of them.
        Indestructible
    """.trimIndent(), "3/3")
    val conjureSorrowShade = Cards.invent("Conjure: Sorrow Shade", "{4}{B}{B}", "Enchantment Creature — Saga Horror", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I, II, III — Pain — You draw a card and you lose 1 life.
        IV — Oblivion — Each opponent sacrifices a creature of their choice and loses 3 life.
        Menace
    """.trimIndent(), "4/4")
    val conjureSkyfireWyrm = Cards.invent("Conjure: Skyfire Wyrm", "{9}", "Enchantment Creature — Saga Dragon", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I, II — Destroy up to one target nonland permanent.
        III — Draw two cards.
        IV — Mega Flare — This creature deals damage equal to the total mana value of other permanents you control to each opponent.
        Flying
    """.trimIndent(), "9/9")
    val conjureSpectralRider = Cards.invent("Conjure: Spectral Rider", "{4}{B}{B}", "Enchantment Creature — Saga Knight", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Gungnir — Destroy target creature an opponent controls.
        II — Zantetsuken — This creature gains "Whenever this creature deals combat damage to a player, that player loses the game."
        III — Hall of Sorrow — Draw two cards. Each player loses 2 life.
    """.trimIndent(), "5/3")
    val conjureRimeMaiden = Cards.invent("Conjure: Rime Maiden", "{3}{U}{U}", "Enchantment Creature — Saga Elemental", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I, II — Heavenly Strike — Tap target creature an opponent controls. Put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        III — Diamond Dust — Draw a card for each tapped creature your opponents control.
    """.trimIndent(), "4/5")
    val conjureBedrockGiant = Cards.invent("Conjure: Bedrock Giant", "{3}{G}{G}", "Enchantment Creature — Saga Giant", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Mill five cards.
        II — Return all land cards from your graveyard to the battlefield tapped.
        III — Until end of turn, another target creature you control gains trample and gets +X/+X, where X is the number of lands you control.
        Reach, trample
    """.trimIndent(), "7/7")
    val conjureFeatherfootStampede = Cards.invent("Conjure: Featherfoot Stampede", "{2}{W}", "Enchantment Creature — Saga Bird Moogle", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I, II, III, IV — Stampede! — Other creatures you control get +1/+0 until end of turn.
    """.trimIndent(), "3/3")
    val conjureStormcallerSage = Cards.invent("Conjure: Stormcaller Sage", "{2}{R}{R}", "Enchantment Creature — Saga Wizard", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Judgment Bolt — This creature deals damage equal to the number of noncreature, nonland cards in your graveyard to target creature an opponent controls.
        II, III — Wizards you control get +1/+0 until end of turn.
    """.trimIndent(), "3/3")
    val conjureAbyssalWyrm = Cards.invent("Conjure: Abyssal Wyrm", "{4}{U}{U}", "Enchantment Creature — Saga Leviathan", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Return each creature that isn't a Kraken, Leviathan, Merfolk, Octopus, or Serpent to its owner's hand.
        II, III — Until end of turn, whenever a Kraken, Leviathan, Merfolk, Octopus, or Serpent attacks, draw a card.
        Ward {2}
    """.trimIndent(), "6/6")
    val conjureCinderImp = Cards.invent("Conjure: Cinder Imp", "{2}{R}", "Enchantment Creature — Saga Demon", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I, II — You may discard a card. If you do, draw a card.
        III, IV — Add {R}.
    """.trimIndent(), "3/2")
    // chunk 04
    val aelthaHeartOfTheCrystal = Cards.inventFaces("Transform",
        face("Aeltha, Heart of the Crystal", "{1}{W}{W}", "Legendary Creature — Elder Wizard", """
            Whenever you cast a legendary spell, draw a card. This ability triggers only once each turn.
            Hero's Sundering — {7}, {T}: Exile target nonland permanent. Transform Aeltha. Activate only as a sorcery.
        """.trimIndent(), "3/3", null, null),
        face("Aeltha, the Mothercrystal", null, "Legendary Creature — God", """
            Indestructible
            Blessing of Light — At the beginning of combat on your turn, put a +1/+1 counter on another target creature you control. Until your next turn, it gains indestructible. If that creature is legendary, draw a card.
        """.trimIndent(), "4/4", null, "W"),
    )
    val maraFrostboundNoble = Cards.inventFaces("Transform",
        face("Mara, Frostbound Noble", "{2}{U}", "Legendary Creature — Human Noble Warrior", """
            When Mara enters, return up to one other target nonland permanent to its owner's hand.
            {3}{U}{U}, {T}: Exile Mara, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery.
        """.trimIndent(), "2/2", null, null),
        face("Rimeheart, Warden of Frost", null, "Legendary Enchantment Creature — Saga Elemental", """
            (As this Saga enters and after your draw step, add a lore counter.)
            I, II — Mesmerize — Target creature can't be blocked this turn.
            III — Cold Snap — Tap all lands your opponents control. Exile Rimeheart, then return it to the battlefield (front face up).
        """.trimIndent(), "4/5", null, "U"),
    )
    val shardOfTheArbiter = Cards.inventFaces("Transform",
        face("Shard of the Arbiter", "{W}", "Artifact — Equipment", """
            Equipped creature gets +1/+1.
            {5}{W}{W}: Exile this Equipment, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery.
            Equip {1}
        """.trimIndent(), null, null, null),
        face("Conjure: The Arbiter", null, "Enchantment Creature — Saga Construct", """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I, II — Prevent all damage that would be dealt to creatures you control this turn.
            III — Tap all creatures your opponents control.
            Flying
        """.trimIndent(), "4/3", null, "W"),
    )
    val corvanEmberboundNoble = Cards.inventFaces("Transform",
        face("Corvan, Emberbound Noble", "{4}{R}{R}", "Legendary Creature — Human Noble Warrior", """
            When Corvan enters, you may discard your hand, then draw cards equal to your devotion to red. (Each {R} in the mana costs of permanents you control counts toward your devotion to red.)
            {4}{R}{R}, {T}: Exile Corvan, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery.
        """.trimIndent(), "5/5", null, null),
        face("Pyreheart, Warden of Cinders", null, "Legendary Enchantment Creature — Saga Demon", """
            (As this Saga enters and after your draw step, add a lore counter.)
            I — Lunge — Pyreheart fights up to one other target creature.
            II, III — Brimstone — Add {R}{R}{R}{R}. If Pyreheart has three or more lore counters on it, exile it, then return it to the battlefield (front face up).
        """.trimIndent(), "9/9", null, "R"),
    )
    val espersAwakening = Cards.inventFaces("Transform",
        face("Esper's Awakening", "{1}{G}", "Sorcery", """
            Surveil 2. You gain 2 life. If this spell was cast from a graveyard, exile it, then put it onto the battlefield transformed under its owner's control with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)
            Flashback {3}{G} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """.trimIndent(), null, null, null),
        face("Conjure: Grove Esper", null, "Enchantment Creature — Saga Elemental", """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Reveal the top card of your library. If it's a permanent card, put it into your hand.
            II — Add {G}{G}.
            III — Other creatures you control get +2/+2 and gain trample until end of turn.
        """.trimIndent(), "4/4", null, "G"),
    )
    val brannoReluctantGuardian = Cards.inventFaces("Transform",
        face("Branno, Reluctant Guardian", "{3}{B}", "Legendary Creature — Human Warrior", """
            Menace
            Whenever Branno deals combat damage to a player, you may exile it, then return it to the battlefield transformed under its owner's control.
        """.trimIndent(), "4/3", null, null),
        face("Branno's Final Aeon", null, "Legendary Enchantment Creature — Saga Nightmare", """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I, II — Jecht Beam — Each opponent discards a card and you draw a card.
            III — Ultimate Jecht Shot — Each opponent sacrifices two creatures of their choice.
            Menace
        """.trimIndent(), "7/7", null, "B"),
    )
    val gerrandFallenKnight = Cards.inventFaces("Transform",
        face("Gerrand, Fallen Knight", "{B}{R}", "Legendary Creature — Human Knight", """
            Whenever you cast a noncreature spell, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            {3}{B}{B}{R}{R}: Return this card from your graveyard to the battlefield transformed. Activate only as a sorcery.
        """.trimIndent(), "3/2", null, null),
        face("Ruin, the Endless", null, "Legendary Creature — Demon", """
            Flying
            When Ruin dies, put it on the bottom of its owner's library.
        """.trimIndent(), "5/5", null, "BR"),
    )
    val cyrenAshbornNoble = Cards.inventFaces("Transform",
        face("Cyren, Ashborn Noble", "{1}{R}{W}", "Legendary Creature — Human Noble Wizard", """
            When Cyren enters, discard up to two cards, then draw that many cards.
            {3}{R}{W}, {T}: Exile Cyren, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery.
        """.trimIndent(), "3/4", null, null),
        face("Ashwing, Warden of Embers", null, "Legendary Enchantment Creature — Saga Phoenix", """
            (As this Saga enters and after your draw step, add a lore counter.)
            I, II — Rising Flames — Ashwing deals 2 damage to each opponent.
            III — Flames of Rebirth — Return any number of target creature cards with total mana value 6 or less from your graveyard to the battlefield. Exile Ashwing, then return it to the battlefield (front face up).
            Flying, lifelink
        """.trimIndent(), "4/4", null, "RW"),
    )
    val aurelWyrmSwornKnight = Cards.inventFaces("Transform",
        face("Aurel, Wyrm-Sworn Knight", "{3}{W}", "Legendary Creature — Human Noble Knight", """
            Dragonfire Dive — During your turn, Aurel and other Knights you control have flying.
            When Aurel enters, create a 2/2 white Knight creature token.
            {4}{W}{W}, {T}: Exile Aurel, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery.
        """.trimIndent(), "3/3", null, null),
        face("Radiant Wyrm, Warden of Dawn", null, "Legendary Enchantment Creature — Saga Dragon", """
            (As this Saga enters and after your draw step, add a lore counter.)
            I, II — Wings of Light — Put a +1/+1 counter on each other creature you control. Those creatures gain flying until end of turn.
            III — Gigaflare — Destroy target permanent. Exile Radiant Wyrm, then return it to the battlefield (front face up).
            Flying
        """.trimIndent(), "5/5", null, "W"),
    )
    val taliaArcaneProdigy = Cards.inventFaces("Transform",
        face("Talia, Arcane Prodigy", "{1}{R}{G}", "Legendary Creature — Human Wizard Warrior", """
            When Talia enters, mill five cards. Put up to one enchantment card milled this way into your hand.
            Trance — {4}{R}{G}, {T}: Exile Talia, then return it to the battlefield transformed under its owner's control. Activate only as a sorcery.
        """.trimIndent(), "4/2", null, null),
        face("Arcane Talia", null, "Legendary Enchantment Creature — Saga Wizard", """
            (As this Saga enters and after your draw step, add a lore counter.)
            I, II, III — Create a token that's a copy of target nonlegendary enchantment you control. It gains haste. If it's a Saga, put up to three lore counters on it. Sacrifice it at the beginning of your next end step.
            IV — Add {W}{W}, {U}{U}, {B}{B}, {R}{R}, and {G}{G}. Exile Arcane Talia, then return it to the battlefield (front face up).
            Flying
        """.trimIndent(), "6/6", null, "GR"),
    )
    // chunk 05
    val theTyrantOfVesh = Cards.inventFaces("Transform",
        face("The Tyrant of Vesh", "{U}{R}", "Legendary Creature — Human Noble Wizard", """
            {T}: Add {U} or {R}. Spend this mana only to cast a noncreature spell.
            Whenever you cast a noncreature spell, if at least four mana was spent to cast it, put a +1/+1 counter on The Tyrant of Vesh. Then if it has three or more +1/+1 counters on it, transform it.
        """.trimIndent(), "2/2", null, null),
        face("The Lord of the Abyss", null, "Legendary Creature — Demon Noble Wizard", """
            Starfall — Whenever The Lord of the Abyss attacks, it deals X damage to each opponent, where X is the number of noncreature, nonland cards in your graveyard.
        """.trimIndent(), "3/3", null, "RU"),
    )
    val zerandTheUnchained = Cards.inventFaces("Transform",
        face("Zerand the Unchained", "{3}{B}{B}", "Legendary Creature — Human Noble Warrior", """
            My First Friend — When Zerand the Unchained enters, choose a creature an opponent controls. Until end of turn, creatures other than Zerand the Unchained and the chosen creature get -2/-2.
            When the chosen creature leaves the battlefield, transform Zerand the Unchained.
        """.trimIndent(), "4/4", null, null),
        face("Shenra, Transcendent Rival", null, "Legendary Creature — Dragon", """
            Flying
            As this creature transforms into Shenra, choose an opponent.
            Burning Chains — When the chosen player loses the game, you win the game.
        """.trimIndent(), "8/8", null, "B"),
    )
    val ravelGenomeSorcerer = Cards.inventFaces("Transform",
        face("Ravel, Genome Sorcerer", "{2}{B}{R}", "Legendary Creature — Human Mutant Wizard", """
            At the beginning of your end step, create a tapped 0/1 black Wizard creature token with "Whenever you cast a noncreature spell, this token deals 1 damage to each opponent." Then if you control four or more Wizards, transform Ravel.
        """.trimIndent(), "3/4", null, null),
        face("Trance Ravel, Fate Defied", null, "Legendary Creature — Avatar Wizard", """
            Flare Star — If a Wizard you control would deal damage to a permanent or player, it deals double that damage instead.
        """.trimIndent(), "4/6", null, "BR"),
    )
    val valenShadegunner = Cards.inventFaces("Transform",
        face("Valen Shadegunner", "{2}{B}{B}", "Legendary Creature — Assassin", """
            Whenever a creature an opponent controls dies, put a number of +1/+1 counters on Valen Shadegunner equal to that creature's power.
            Whenever Valen Shadegunner attacks, you may transform it.
        """.trimIndent(), "2/2", null, null),
        face("Wildborn Beast", null, "Legendary Creature — Werewolf Beast", """
            Trample, lifelink
            When Wildborn Beast dies, return it to the battlefield tapped (front face up).
        """.trimIndent(), "3/2", null, "B"),
    )
    val severusFabledSoldier = Cards.inventFaces("Transform",
        face("Severus, Fabled Soldier", "{2}{B}", "Legendary Creature — Human Avatar Soldier", """
            Whenever Severus enters or attacks, you may sacrifice another creature. If you do, draw a card.
            Whenever another creature dies, target opponent loses 1 life and you gain 1 life. If this is the fourth time this ability has resolved this turn, transform Severus.
        """.trimIndent(), "3/3", null, null),
        face("Severus, One-Winged Seraph", null, "Legendary Creature — Angel Nightmare Avatar", """
            Flying
            Super Nova — As this creature transforms into Severus, One-Winged Seraph, you get an emblem with "Whenever a creature dies, target opponent loses 1 life and you gain 1 life."
            Whenever Severus attacks, you may sacrifice any number of other creatures. If you do, draw that many cards.
        """.trimIndent(), "5/5", null, "B"),
    )
    val questPlayTheMatch = Cards.inventFaces("Transform",
        face("Quest: Play the Match", "{2}{R}", "Enchantment", """
            At the beginning of combat on your turn, target creature you control gets +2/+0 until end of turn.
            At the end of combat on your turn, if a player was dealt 6 or more combat damage this turn, transform this enchantment, then attach it to a creature you control.
        """.trimIndent(), null, null, null),
        face("Champion's Trophy, Celestial Arm", null, "Legendary Artifact — Equipment", """
            Double Overdrive — Equipped creature gets +2/+0 and has double strike.
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """.trimIndent(), null, null, "R"),
    )
    val cedricDarkKnight = Cards.inventFaces("Transform",
        face("Cedric, Dark Knight", "{B}", "Legendary Creature — Human Knight", """
            Deathtouch
            Darkness — Whenever Cedric deals damage, you lose that much life. Then if your life total is less than or equal to half your starting life total, untap Cedric and transform it.
        """.trimIndent(), "2/3", null, null),
        face("Cedric, Redeemed Paladin", null, "Legendary Creature — Human Knight", """
            Lifelink
            Protect — Whenever Cedric attacks, other attacking creatures gain indestructible until end of turn.
        """.trimIndent(), "4/4", null, "W"),
    )
    val questBountyBoard = Cards.inventFaces("Transform",
        face("Quest: Bounty Board", "{3}{B}{B}", "Enchantment", """
            When this enchantment enters, destroy up to one target creature.
            At the beginning of your end step, if a creature died under an opponent's control this turn, create a Treasure token. Then if you control three or more Treasures, transform this enchantment.
        """.trimIndent(), null, null, null),
        face("Varrok, Ultimate Quarry", null, "Legendary Creature — Dragon", """
            {1}{B}, Sacrifice another creature or artifact: Varrok gains indestructible until end of turn. Tap it.
        """.trimIndent(), "5/6", null, "B"),
    )
    val kaelCourtJester = Cards.inventFaces("Transform",
        face("Kael, Court Jester", "{2}{U}{B}{R}", "Legendary Creature — Human Wizard", """
            Whenever Kael enters or attacks, each player discards a card. Then you draw a card for each card type among cards discarded this way.
            {8}: Each opponent sacrifices a permanent of their choice. Transform Kael. Activate only as a sorcery.
        """.trimIndent(), "4/5", null, null),
        face("Kael, Ruler of Ruin", null, "Legendary Creature — Avatar Wizard", """
            Flying
            Whenever an opponent loses life during your turn, you draw that many cards.
        """.trimIndent(), "5/7", null, "BRU"),
    )
    val skyhavenMercenaryAcademy = Cards.inventFaces("Transform",
        face("Skyhaven, Mercenary Academy", null, "Land — Town", """
            This land enters tapped.
            {T}: Add {G} or {U}.
            {5}{G}{U}, {T}: Transform this land. This ability costs {1} less to activate for each other Town you control.
        """.trimIndent(), null, null, null),
        face("Skyhaven, Airborne", null, "Legendary Artifact — Vehicle", """
            Flying
            Whenever Skyhaven attacks, draw a card.
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """.trimIndent(), "5/4", null, null),
    )
    val morvathVoidWarlock = Cards.inventFaces("Transform",
        face("Morvath, Void Warlock", "{1}{B}{G}", "Legendary Creature — Spirit Warlock", """
            When Morvath enters, you gain 3 life.
            At the beginning of your end step, if there are six or more permanent cards in your graveyard, transform Morvath.
        """.trimIndent(), "3/3", null, null),
        face("Neo Morvath, Dimension's End", null, "Legendary Creature — Spirit Avatar", """
            Trample
            Neo Morvath's power is equal to the number of permanent cards in your graveyard.
        """.trimIndent(), "*/3", null, "BG"),
    )
    val sylvaFarron = Cards.inventFaces("Transform",
        face("Sylva Farron", "{1}{G}{W}", "Legendary Creature — Human Citizen", """
            The first legendary creature spell you cast each turn costs {2} less to cast.
            At the beginning of combat on your turn, if you control two or more other legendary creatures, you may transform Sylva Farron.
        """.trimIndent(), "2/2", null, null),
        face("Crystallized Sylva", null, "Legendary Artifact", """
            The first legendary creature spell you cast each turn costs {2} less to cast.
            Legendary creatures you control get +2/+2.
        """.trimIndent(), null, null, "GW"),
    )
    val questCollectorsAlbum = Cards.inventFaces("Transform",
        face("Quest: Collector's Album", "{3}{U}", "Enchantment", """
            When this enchantment enters, draw three cards, then discard two cards.
            At the beginning of your end step, if eight or more cards are in your graveyard, transform this enchantment.
        """.trimIndent(), null, null, null),
        face("Enchanted Playing Card", null, "Artifact — Vehicle", """
            Flying
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """.trimIndent(), "4/4", null, "U"),
    )
    val questRaiseARunner = Cards.inventFaces("Transform",
        face("Quest: Raise a Runner", "{1}{G}", "Enchantment", """
            When this enchantment enters, create a 2/2 green Bird creature token with "Whenever a land you control enters, this token gets +1/+0 until end of turn."
            At the beginning of your first main phase, if you control four or more Birds, transform this enchantment.
        """.trimIndent(), null, null, null),
        face("Ebony Runner", null, "Creature — Bird", """
            When this permanent transforms into Ebony Runner, search your library for a land card, put it onto the battlefield tapped, then shuffle.
            Landfall — Whenever a land you control enters, Birds you control get +1/+0 until end of turn.
        """.trimIndent(), "2/2", null, "G"),
    )
    val ysoldeTimeSorceress = Cards.inventFaces("Transform",
        face("Ysolde, Time Sorceress", "{3}{U}{B}", "Legendary Creature — Human Warlock", """
            Whenever Ysolde enters or attacks, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
            At the beginning of your end step, you may pay {4}{U}{U}{B}{B} and exile eight cards from your graveyard. If you do, transform Ysolde.
        """.trimIndent(), "4/5", null, null),
        face("Ysolde, Omnipotent", null, "Legendary Creature — Nightmare Warlock", """
            Menace (This creature can't be blocked except by two or more creatures.)
            Time Compression — When this creature transforms into Ysolde, Omnipotent, take an extra turn after this one.
        """.trimIndent(), "7/7", null, "BU"),
    )
    val aethelUnsundered = Cards.inventFaces("Transform",
        face("Aethel, Unsundered", "{1}{U}{B}", "Legendary Creature — Elder Wizard", """
            Vigilance
            Whenever Aethel enters or attacks, draw a card, then discard a card.
            At the beginning of your upkeep, if there are fourteen or more cards in your graveyard, you may transform Aethel.
        """.trimIndent(), "2/4", null, null),
        face("Aethel, Sorcerer of the Deep", null, "Legendary Creature — Avatar", """
            Vigilance
            Echo of the Lost — During your turn, you may play cards from your graveyard.
            If a card or token would be put into your graveyard from anywhere, exile it instead.
        """.trimIndent(), "6/6", null, "BU"),
    )
    val questGoneFishing = Cards.inventFaces("Transform",
        face("Quest: Gone Fishing", "{2}{W}", "Enchantment", """
            At the beginning of your upkeep, look at the top card of your library. If it's an artifact or creature card, you may reveal it and put it into your hand. If you put a card into your hand this way, create a Food token and transform this enchantment.
        """.trimIndent(), null, null, null),
        face("Riverside Camp", null, "Land", """
            {T}: Add {W}.
            {3}, {T}, Sacrifice an artifact: Put a +1/+1 counter on each creature you control. Activate only as a sorcery.
        """.trimIndent(), null, null, null),
    )
    // chunk 06
    val matronArchonElder = Cards.invent("Matron, Archon Elder", "{2}{U}", "Legendary Creature — Human Warlock", "Whenever you scry or surveil, draw a card. (Draw after you scry or surveil.)", "1/4")
    val ambushedByMages = Cards.invent("Ambushed by Mages", "{1}{B}{B}", "Sorcery", """
        Target opponent sacrifices a creature of their choice.
        Create a 0/1 black Wizard creature token with "Whenever you cast a noncreature spell, this token deals 1 damage to each opponent."
    """.trimIndent())
    val shanaraTacticianMagician = Cards.invent("Shanara, Tactician Magician", "{1}{U}{R}", "Legendary Creature — Dwarf Wizard", """
        Whenever you cast a noncreature spell, Shanara gets +X/+0 until end of turn, where X is the amount of mana spent to cast that spell. If X is 4 or more, draw a card.
    """.trimIndent(), "0/4")
    val grimWaltzerNo5 = Cards.invent("Grim Waltzer No. 5", "{2}{B}{R}", "Legendary Creature — Wizard", """
        Flying, deathtouch
        Whenever you cast a noncreature spell, Grim Waltzer No. 5 deals 2 damage to each opponent.
    """.trimIndent(), "2/2")
    val telmarGreatSage = Cards.invent("Telmar, Great Sage", "{3}{U}{R}", "Legendary Creature — Human Wizard", """
        Whenever you cast a noncreature spell, create a 1/1 colorless Hero creature token. If four or more mana was spent to cast that spell, draw two cards. If eight or more mana was spent to cast that spell, sacrifice Telmar and it deals that much damage to each opponent.
    """.trimIndent(), "3/3")
    val theGrandStagecraft = Cards.invent("The Grand Stagecraft", "{4}{U}", "Legendary Artifact — Vehicle", """
        Flying
        Whenever you cast a noncreature spell, if at least four mana was spent to cast it, The Grand Stagecraft becomes an artifact creature until end of turn.
        Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "5/3")
    val mysticElder = Cards.invent("Mystic Elder", "{2}{R}", "Creature — Human Wizard", """
        When this creature enters, create a 0/1 black Wizard creature token with "Whenever you cast a noncreature spell, this token deals 1 damage to each opponent."
    """.trimIndent(), "1/3")
    val viraOrnitier = Cards.invent("Vira Ornitier", "{1}{U}{R}", "Legendary Creature — Wizard", """
        {0}: Add X mana in any combination of {U} and/or {R}, where X is Vira Ornitier's power. Activate only during your turn and only once each turn.
        Whenever you cast a noncreature spell, put a +1/+1 counter on Vira Ornitier and it deals 1 damage to each opponent.
    """.trimIndent(), "0/3")
    val queenOfBrahm = Cards.invent("Queen of Brahm", "{2}{R}", "Legendary Creature — Human Noble", """
        Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        Whenever Queen of Brahm attacks, create a 0/1 black Wizard creature token with "Whenever you cast a noncreature spell, this token deals 1 damage to each opponent."
    """.trimIndent(), "2/1")
    val ulrosInkyNuisance = Cards.invent("Ulros, Inky Nuisance", "{1}{U}", "Legendary Creature — Octopus", """
        Whenever you cast a noncreature spell, if at least four mana was spent to cast it, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        Whenever you cast a noncreature spell, if at least eight mana was spent to cast it, put eight +1/+1 counters on Ulros.
    """.trimIndent(), "2/1")
    val xandraDarkMage = Cards.invent("Xandra, Dark Mage", "{2}{U}{B}", "Legendary Creature — Human Wizard", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Xandra gets +1/+1 for each noncreature, nonland card in your graveyard.
    """.trimIndent(), "3/3")
    val ringOfIncantation = Cards.invent("Ring of Incantation", "{3}{B}", "Sorcery", """
        You draw two cards and you lose 2 life. Create a 0/1 black Wizard creature token with "Whenever you cast a noncreature spell, this token deals 1 damage to each opponent."
        Wizards you control get +1/+0 and gain lifelink until end of turn.
    """.trimIndent())
    val cinderBomb = Cards.invent("Cinder Bomb", "{R}", "Creature — Elemental", """
        Whenever you cast a noncreature spell, if at least four mana was spent to cast it, put a +1/+1 counter on this creature.
        Blow Up — {T}, Sacrifice this creature: It deals damage equal to its power to target creature. Activate only as a sorcery.
    """.trimIndent(), "1/1")
    val pellaShutterbug = Cards.invent("Pella, Shutterbug", "{1}{R}", "Legendary Creature — Human Scout", """
        Haste
        Selfie Shot — Whenever you cast a noncreature spell, if at least four mana was spent to cast it, create a Treasure token.
    """.trimIndent(), "2/2")
    val brineclawRaider = Cards.invent("Brineclaw Raider", "{1}{U}", "Creature — Merfolk Warrior", """
        Whenever you cast a noncreature spell, if at least four mana was spent to cast it, put a +1/+1 counter on this creature and it can't be blocked this turn.
    """.trimIndent(), "1/3")
    // chunk 07
    val paleAuracite = Cards.invent("Pale Auracite", "{2}{W}{W}", "Artifact", """
        When this artifact enters, exile target nonland permanent an opponent controls until this artifact leaves the battlefield.
        {T}: Add {W}.
    """.trimIndent())
    val parcelMoogle = Cards.invent("Parcel Moogle", "{3}{W}", "Creature — Moogle", """
        Flying
        When this creature enters, search your library and/or graveyard for an artifact card with mana value 2 or less, reveal it, and put it into your hand. If you search your library this way, shuffle.
    """.trimIndent(), "3/2")
    val aeraFlowerSeller = Cards.invent("Aera, Flower Seller", "{2}{W}", "Legendary Creature — Human Cleric", """
        Lifelink
        Whenever you gain life, put a +1/+1 counter on Aera, Flower Seller.
        When Aera, Flower Seller dies, put X +1/+1 counters on each legendary creature you control, where X is the number of +1/+1 counters on Aera, Flower Seller.
    """.trimIndent(), "2/2")
    val legacyOfTheAirship = Cards.invent("Legacy of the Airship", "{1}{W}", "Sorcery", """
        Search your library for a Vehicle card, reveal it, and put it into your hand. If this spell was cast from a graveyard, put that card onto the battlefield instead. Then shuffle.
        Flashback {4}{W}{W}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """.trimIndent())
    val zaneValecourt = Cards.invent("Zane Valecourt", "{W}", "Legendary Creature — Human Soldier", """
        Zane Valecourt enters with a +1/+1 counter on it.
        {1}, Sacrifice Zane Valecourt: Target creature you control gains indestructible until end of turn. Put Zane Valecourt's counters on that creature and attach an Equipment that was attached to Zane Valecourt to that creature.
    """.trimIndent(), "0/1")
    val armamentPeddler = Cards.invent("Armament Peddler", "{3}{W}", "Creature — Human Artificer", """
        When this creature enters, draw a card.
        At the beginning of combat on your turn, if you control an Equipment, you may pay {1}. When you do, attach target Equipment you control to target creature you control.
    """.trimIndent(), "2/2")
    val rosalindResoluteHealer = Cards.invent("Rosalind, Resolute Healer", "{3}{W}", "Legendary Creature — Human Noble Cleric", """
        Reach (This creature can block creatures with flying.)
        At the beginning of combat on your turn, put a +1/+1 counter on target creature you control. It gains lifelink until end of turn. (Damage dealt by the creature also causes you to gain that much life.)
    """.trimIndent(), "2/3")
    val stilkinMoogleTrader = Cards.invent("Stilkin, Moogle Trader", "{W}", "Legendary Creature — Moogle", """
        Lifelink
        {2}, {T}: Target opponent gains control of another target permanent you control. If they do, you draw a card.
    """.trimIndent(), "1/2")
    val crystalsFate = Cards.invent("Crystal's Fate", "{4}{W}", "Instant", """
        This spell costs {2} less to cast if it targets a tapped creature.
        Destroy target nonland permanent.
    """.trimIndent())
    val gerraTiel = Cards.invent("Gerra Tiel", "{4}{W}", "Legendary Creature — Cat Archer", """
        Reach
        The Allagan Eye — Whenever one or more other creatures and/or artifacts you control die, draw a card. This ability triggers only once each turn.
    """.trimIndent(), "3/5")
    val ambergaleWhiteheart = Cards.invent("Ambergale Whiteheart", "{1}{W}", "Legendary Creature — Bird", """
        Flash
        When Ambergale Whiteheart enters, you may return another permanent you control to its owner's hand.
        Landfall — Whenever a land you control enters, Ambergale Whiteheart gets +1/+0 until end of turn.
    """.trimIndent(), "2/2")
    val theGaleCrystal = Cards.invent("The Gale Crystal", "{2}{W}{W}", "Legendary Artifact", """
        White spells you cast cost {1} less to cast.
        If you would gain life, you gain twice that much life instead.
        {4}{W}{W}, {T}: Creatures you control gain flying and lifelink until end of turn.
    """.trimIndent())
    val claudeSellswordOfTheSlums = Cards.invent("Claude, Sellsword of the Slums", "{W}{W}", "Legendary Creature — Human Soldier Mercenary", """
        When Claude enters, search your library for an Equipment card, reveal it, put it into your hand, then shuffle.
        As long as Claude is equipped, if a triggered ability of Claude or an Equipment attached to it triggers, that ability triggers an additional time.
    """.trimIndent(), "2/1")
    val finalOblivion = Cards.invent("Final Oblivion", "{3}{W}{W}", "Sorcery", """
        Destroy all artifacts and creatures. End the turn. (Exile all spells and abilities from the stack, including this card. The player whose turn it is discards down to their maximum hand size. Damage wears off, and "this turn" and "until end of turn" effects end.)
    """.trimIndent())
    val bellatrixLoyalGeneral = Cards.invent("Bellatrix, Loyal General", "{4}{W}{W}", "Legendary Creature — Human Soldier", """
        Vigilance (Attacking doesn't cause this creature to tap.)
        At the beginning of combat on your turn, you may attach any number of Equipment you control to target creature you control.
    """.trimIndent(), "4/4")
    // chunk 08
    val stoneholdGuard = Cards.invent("Stonehold Guard", "{1}{W}", "Creature — Dwarf Soldier", "When this creature dies, create a 1/1 colorless Hero creature token.", "2/1")
    val edgeOfDawn = Cards.invent("Edge of Dawn", "{1}{W}", "Instant", """
        Edge of Dawn deals damage equal to the number of creatures you control plus the number of Equipment you control to target creature.
    """.trimIndent())
    val adelbaldSteadfast = Cards.invent("Adelbald Steadfast", "{1}{W}", "Legendary Creature — Human Knight", """
        Lifelink
        Adelbald Steadfast gets +1/+1 for each Equipment you control.
    """.trimIndent(), "2/1")
    val hollisEstheim = Cards.invent("Hollis Estheim", "{W}{U}", "Legendary Creature — Human Wizard", """
        Lifelink
        At the beginning of your end step, each opponent mills X cards, where X is the amount of life you gained this turn.
    """.trimIndent(), "2/2")
    val ashaPrincessOfTheSands = Cards.invent("Asha, Princess of the Sands", "{2}{W}", "Legendary Creature — Human Rebel Noble", """
        Whenever Asha attacks, look at the top five cards of your library. You may reveal an artifact card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
    """.trimIndent(), "3/2")
    val theLightsChosen = Cards.invent("The Light's Chosen", "{5}{W}{W}", "Sorcery", """
        Create four 1/1 colorless Hero creature tokens. Then put a +1/+1 counter on each creature you control.
    """.trimIndent())
    val ironcladWalker = Cards.invent("Ironclad Walker", "{3}{W}", "Artifact — Vehicle", """
        When this Vehicle enters, create a 1/1 colorless Hero creature token.
        Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "4/4")
    val clawedProwler = Cards.invent("Clawed Prowler", "{1}{W}", "Creature — Cat Beast", "{1}{W}, {T}: Tap target nonenchantment creature.", "2/2")
    val mooglesStand = Cards.invent("Moogles' Stand", "{3}{W}{W}", "Instant", """
        For each creature you control, create a 1/2 white Moogle creature token with lifelink. Then creatures you control gain indestructible until end of turn.
    """.trimIndent())
    val neverAlone = Cards.invent("Never Alone", "{W}", "Instant", """
        Target creature gets +2/+2 until end of turn. If you control three or more creatures, it gets +4/+4 until end of turn instead.
    """.trimIndent())
    val crestedCat = Cards.invent("Crested Cat", "{2}{W}", "Creature — Cat", """
        Flying, vigilance
        As long as you control two or more artifacts, this creature gets +2/+0.
    """.trimIndent(), "1/3")
    val snoweValiers = Cards.invent("Snowe Valiers", "{2}{W}", "Legendary Creature — Human Rebel Monk", """
        Vigilance
        Snowe Valiers's power is equal to the number of creatures you control.
    """.trimIndent(), "*/3")
    val cidelTimelessTinker = Cards.invent("Cidel, Timeless Tinker", "{2}{W}{U}", "Legendary Creature — Human Artificer", """
        Artifact creatures and Heroes you control get +1/+1 for each Artificer you control and each Artificer card in your graveyard.
        A deck can have any number of cards named Cidel, Timeless Tinker.
        Cycling {W}{U} ({W}{U}, Discard this card: Draw a card.)
    """.trimIndent(), "4/4")
    val ronansInspiration = Cards.invent("Ronan's Inspiration", "{2}{W}", "Instant", """
        Attacking creatures get +2/+0 until end of turn.
        Flashback {2}{W}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """.trimIndent())
    val tydesStarStriker = Cards.invent("Tydes, Star Striker", "{1}{W}{U}", "Legendary Creature — Human Warrior", """
        Whenever an artifact you control enters, put a +1/+1 counter on Tydes.
        Whenever Tydes attacks, tap target creature an opponent controls.
    """.trimIndent(), "2/1")
    val clockworkInfantry = Cards.invent("Clockwork Infantry", "{W}", "Artifact Creature — Robot Soldier", """
        This creature gets +1/+0 as long as you control another artifact.
        {2}{W}: Search your library for a card named Clockwork Infantry, put it onto the battlefield tapped, then shuffle.
    """.trimIndent(), "1/1")
    val skyboundMoogle = Cards.invent("Skybound Moogle", "{3}{W}{W}", "Creature — Moogle", """
        Flying
        When this creature enters, put a +1/+1 counter on target creature.
        Plainscycling {2} ({2}, Discard this card: Search your library for a Plains card, reveal it, put it into your hand, then shuffle.)
    """.trimIndent(), "2/3")
    val minaWhiteMage = Cards.invent("Mina, White Mage", "{3}{W}{W}", "Legendary Creature — Human Cleric", """
        Vigilance, lifelink
        Whenever you gain life, put a +1/+1 counter on each Cleric you control.
    """.trimIndent(), "3/3")
    val virtueAbsolute = Cards.invent("Virtue Absolute", "{6}{W}{U}", "Legendary Creature — Avatar Warrior", """
        This spell can't be countered.
        Flying
        You have protection from each of your opponents. (You can't be dealt damage, enchanted, or targeted by anything controlled by your opponents.)
    """.trimIndent(), "8/8")
    // chunk 09
    val ysraWoodSeer = Cards.invent("Ysra, Wood Seer", "{4}{U}{U}", "Legendary Creature — Cat Druid", """
        At the beginning of your end step, exile target creature you control, then return it to the battlefield under its owner's control. Then if it's the first end step of the turn, there is an additional end step after this step.
    """.trimIndent(), "3/5")
    val mentorsSacrifice = Cards.invent("Mentor's Sacrifice", "{U}", "Instant", """
        As an additional cost to cast this spell, sacrifice a legendary creature or pay {2}.
        Counter target activated ability, triggered ability, or noncreature spell.
    """.trimIndent())
    val theMoonwardWhale = Cards.invent("The Moonward Whale", "{3}{U}", "Legendary Artifact — Vehicle", """
        Flying
        You may look at the top card of your library any time.
        As long as The Moonward Whale attacked this turn, you may play the top card of your library.
        Crew 1
    """.trimIndent(), "3/5")
    val recollectionsReturning = Cards.invent("Recollections Returning", "{2}{U}{U}", "Sorcery", """
        Reveal the top five cards of your library. Put one of them into your hand. Then choose an opponent. They put one on the bottom of your library. Then you put one into your hand. Then they put one on the bottom of your library. Put the other into your hand.
        Flashback {7}{U}{U}
    """.trimIndent())
    val devouredByTheDeep = Cards.invent("Devoured by the Deep", "{2}{U}", "Instant", """
        Choose target spell. Surveil 2, then counter the chosen spell unless its controller pays {1} for each card in your graveyard. (To surveil 2, look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
    """.trimIndent())
    val theTideCrystal = Cards.invent("The Tide Crystal", "{2}{U}{U}", "Legendary Artifact", """
        Blue spells you cast cost {1} less to cast.
        If an opponent would mill one or more cards, they mill that many cards plus four instead.
        {4}{U}{U}, {T}: Each opponent mills cards equal to the number of cards in your hand.
    """.trimIndent())
    val trappedInTheSanctum = Cards.invent("Trapped in the Sanctum", "{2}{U}", "Enchantment — Aura", """
        Flash
        Enchant artifact or creature
        When this Aura enters, tap enchanted permanent.
        Enchanted permanent doesn't untap during its controller's untap step and its activated abilities can't be activated.
    """.trimIndent())
    val recoverTheEsper = Cards.invent("Recover the Esper", "{3}{U}", "Sorcery", """
        Create a 3/3 blue Robot Warrior artifact creature token. Then if this spell was cast from a graveyard, put two +1/+1 counters on that token.
        Flashback {5}{U} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """.trimIndent())
    val edrikKingOfTheSands = Cards.invent("Edrik, King of the Sands", "{4}{U}{U}", "Legendary Creature — Human Artificer Noble", """
        When Edrik enters, draw a card for each artifact you control.
        Two-Headed Coin — The first time you flip one or more coins each turn, those coins come up heads and you win those flips.
    """.trimIndent(), "4/5")
    val quessaTrepid = Cards.invent("Quessa Trepid", "{2}{U}", "Legendary Creature — Human Wizard", """
        Blue Magic — When Quessa Trepid enters, you may cast target instant or sorcery card from a graveyard, and mana of any type can be spent to cast that spell. If that spell would be put into a graveyard, exile it instead.
    """.trimIndent(), "2/2")
    val mimoMasterOfMimicry = Cards.invent("Mimo, Master of Mimicry", "{2}{U}", "Legendary Creature — Wizard", """
        {X}{X}, {T}: Copy target activated or triggered ability you control X times. You may choose new targets for the copies. This ability can't be copied and X can't be 0. (Mana abilities can't be targeted.)
    """.trimIndent(), "2/4")
    val borrowedUniform = Cards.invent("Borrowed Uniform", "{U}", "Instant", """
        Choose target creature you control and target Equipment. Gain control of that Equipment until end of turn. Attach it to the chosen creature. When you lose control of that Equipment this turn, if it's attached to a creature you control, unattach it.
    """.trimIndent())
    // chunk 10
    val syncopatedRhythm = Cards.invent("Syncopated Rhythm", "{X}{U}", "Instant", """
        Counter target spell unless its controller pays {X}. If that spell is countered this way, exile it instead of putting it into its owner's graveyard.
    """.trimIndent())
    val ultaraTemporalThreat = Cards.invent("Ultara, Temporal Threat", "{4}{U}{U}", "Legendary Creature — Human Warlock", """
        When Ultara enters, tap all creatures your opponents control.
        Whenever a creature you control deals combat damage to a player, draw a card.
    """.trimIndent(), "4/4")
    val aetherTincture = Cards.invent("Aether Tincture", "{3}{U}", "Artifact", """
        {T}, Exile this artifact: Add {U}. When you next cast an instant or sorcery spell this turn, copy that spell. You may choose new targets for the copy.
    """.trimIndent())
    val freightSkiff = Cards.invent("Freight Skiff", "{1}{U}", "Artifact — Vehicle", """
        Flying, vigilance
        {T}: Add {C}. Spend this mana only to cast an artifact spell or activate an ability of an artifact source.
        Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
    """.trimIndent(), "2/3")
    val hamletMerchant = Cards.invent("Hamlet Merchant", "{2}{U}", "Creature — Beast Citizen", """
        {1}, {T}: Draw a card, then discard a card.
        {7}, {T}, Sacrifice this creature: Draw three cards. This ability costs {1} less to activate for each Town you control.
    """.trimIndent(), "1/4")
    val dreamOfTheFaraway = Cards.invent("Dream of the Faraway", "{1}{U}", "Instant", """
        Surveil 1, then draw a card. (To surveil 1, look at the top card of your library. You may put it into your graveyard.)
        Flashback {3}{U} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """.trimIndent())
    val spellDamper = Cards.invent("Spell Damper", "{U}", "Instant", "Target creature you control gets +1/+1 and gains hexproof until end of turn. Untap it.")
    val ejectionSeat = Cards.invent("Ejection Seat", "{3}{U}", "Instant", """
        This spell can't be countered.
        Return target nonland permanent to its owner's hand.
        Draw a card.
    """.trimIndent())
    val watchtowerTurret = Cards.invent("Watchtower Turret", "{3}{U}", "Artifact Creature — Construct", """
        Flying
        Whenever another artifact you control enters, you may draw a card. If you do, discard a card.
    """.trimIndent(), "3/3")
    val frostPudding = Cards.invent("Frost Pudding", "{4}{U}{U}", "Creature — Elemental Ooze", """
        When this creature enters, tap target artifact or creature an opponent controls. Put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        Islandcycling {2} ({2}, Discard this card: Search your library for an Island card, reveal it, put it into your hand, then shuffle.)
    """.trimIndent(), "5/4")
    val drillInstruction = Cards.invent("Drill Instruction", "{2}{U}", "Sorcery", """
        Target player draws two cards. Put a +1/+1 counter on up to one target creature you control.
    """.trimIndent())
    val portraitSketch = Cards.invent("Portrait Sketch", "{2}{U}{U}", "Sorcery", "Create a token that's a copy of target artifact, creature, or land.")
    val valkyrieGunship = Cards.invent("Valkyrie Gunship", "{5}{U}{U}", "Artifact Creature — Construct", """
        Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
        Flying
        When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
    """.trimIndent(), "5/4")
    val stingerSentry = Cards.invent("Stinger Sentry", "{1}{U}", "Artifact Creature — Robot Scorpion", "As long as you control seven or more lands, this creature gets +3/+0.", "1/4")
    val slumberCharm = Cards.invent("Slumber Charm", "{U}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, tap enchanted creature.
        Enchanted creature doesn't untap during its controller's untap step.
        When enchanted creature is dealt damage, sacrifice this Aura.
    """.trimIndent())
    val journeyAcrossTheWorld = Cards.invent("Journey Across the World", "{5}{U}{U}", "Sorcery", """
        Affinity for Towns (This spell costs {1} less to cast for each Town you control.)
        Draw four cards.
    """.trimIndent())
    // chunk 11
    val wickedReawakening = Cards.invent("Wicked Reawakening", "{4}{B}", "Sorcery", """
        Return target creature card from your graveyard to the battlefield with two additional +1/+1 counters on it.
    """.trimIndent())
    val kaelTraitorousLancer = Cards.invent("Kael, Traitorous Lancer", "{2}{B}", "Legendary Creature — Human Knight", """
        Jump — During your turn, Kael has flying.
        Whenever Kael deals combat damage to a player, that player gains control of Kael. If they do, you draw that many cards, create that many tapped Treasure tokens, then lose that much life.
    """.trimIndent(), "2/4")
    val ardaanTheUsurper = Cards.invent("Ardaan, the Usurper", "{5}{B}{B}{B}", "Legendary Creature — Elder Human Noble", """
        Demons you control have menace, lifelink, and haste.
        Starscourge — At the beginning of combat on your turn, exile up to one target creature card from a graveyard. If you exiled a card this way, create a token that's a copy of that card, except it's a 5/5 black Demon.
    """.trimIndent(), "4/4")
    val knifeGoblin = Cards.invent("Knife Goblin", "{B}", "Creature — Salamander Horror", """
        This creature enters tapped with a stun counter on it. (If it would become untapped, remove a stun counter from it instead.)
        Chef's Knife — During your turn, this creature has first strike and deathtouch.
    """.trimIndent(), "2/1")
    val wardingWall = Cards.invent("Warding Wall", "{1}{B}", "Artifact Creature — Demon Wall", """
        Defender
        Menace (This creature can't be blocked except by two or more creatures.)
        As long as this creature has a counter on it, it can attack as though it didn't have defender.
        {5}{B}: Put two +1/+1 counters on this creature.
    """.trimIndent(), "3/3")
    val theLastDays = Cards.invent("The Last Days", "{2}{B}{B}", "Sorcery", """
        Create two tapped 2/2 black Horror creature tokens. If this spell was cast from a graveyard, instead create X of those tokens, where X is the number of creature cards in your graveyard.
        Flashback {4}{B}{B} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """.trimIndent())
    val ghostlyLocomotive = Cards.invent("Ghostly Locomotive", "{3}{B}", "Artifact — Vehicle", """
        Trample
        Sacrifice another artifact or creature: Put a +1/+1 counter on this Vehicle. It becomes a Spirit artifact creature in addition to its other types until end of turn.
    """.trimIndent(), "4/4")
    val rotmaw = Cards.invent("Rotmaw", "{4}{B}{B}", "Creature — Plant Horror", """
        Bad Breath — When this creature enters, each opponent discards a card, loses 2 life, and exiles the top three cards of their library.
        Swampcycling {2} ({2}, Discard this card: Search your library for a Swamp card, reveal it, put it into your hand, then shuffle.)
    """.trimIndent(), "4/4")
    val renzAndRook = Cards.invent("Renz and Rook", "{1}{B}", "Legendary Creature — Human Assassin", """
        Menace
        Whenever Renz and Rook deals combat damage to a player, exile the top card of that player's library. Then you may sacrifice another creature or artifact. If you do, you may play the exiled card this turn, and mana of any type can be spent to cast it.
    """.trimIndent(), "2/1")
    val fatalEmbrace = Cards.invent("Fatal Embrace", "{3}{B}{B}", "Sorcery", """
        Destroy target creature an opponent controls. Then draw a card for each creature that died this turn.
    """.trimIndent())
    val underwayRat = Cards.invent("Underway Rat", "{1}{B}", "Creature — Rat", """
        Rat Tail — When this creature dies, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """.trimIndent(), "2/2")
    val theShadowCrystal = Cards.invent("The Shadow Crystal", "{2}{B}{B}", "Legendary Artifact", """
        Black spells you cast cost {1} less to cast.
        If a nontoken creature an opponent controls would die, instead exile it and you gain 2 life.
        {4}{B}{B}, {T}: Put target creature card exiled with The Shadow Crystal onto the battlefield tapped under your control with two additional +1/+1 counters on it.
    """.trimIndent())
    val zodrakUmbralGod = Cards.invent("Zodrak, Umbral God", "{B}{B}{B}{B}{B}", "Legendary Creature — God", """
        Indestructible
        When Zodrak enters, each player sacrifices half the non-God creatures they control of their choice, rounded down.
        Whenever a player sacrifices another creature, put a +1/+1 counter on Zodrak.
    """.trimIndent(), "5/5")
    // chunk 12
    val frejaCrescentia = Cards.invent("Freja Crescentia", "{R}", "Legendary Creature — Rat Knight", """
        Jump — During your turn, Freja Crescentia has flying.
        {T}: Add {R}. Spend this mana only to cast an Equipment spell or activate an equip ability.
    """.trimIndent(), "1/1")
    val gearShopkeeper = Cards.invent("Gear Shopkeeper", "{1}{R}", "Creature — Human Citizen", """
        Whenever you attack, target attacking equipped creature gains menace until end of turn. (It can't be blocked except by two or more creatures.)
    """.trimIndent(), "2/2")
    val selfDetonate = Cards.invent("Self-Detonate", "{1}{R}", "Instant", """
        Target creature you control deals X damage to any other target and X damage to itself, where X is its power.
    """.trimIndent())
    val gildemarMasterAtArms = Cards.invent("Gildemar, Master-at-Arms", "{4}{R}{R}", "Legendary Creature — Human Samurai", """
        Whenever Gildemar enters or attacks, look at the top six cards of your library. You may put any number of Equipment cards from among them onto the battlefield. Put the rest on the bottom of your library in a random order. When you put one or more Equipment onto the battlefield this way, you may attach one of them to a Samurai you control.
    """.trimIndent(), "6/6")
    val quickeningSpell = Cards.invent("Quickening Spell", "{1}{R}", "Instant", """
        Target creature gets +3/+1 and gains haste until end of turn. Exile the top card of your library. You may play it until your next end step.
    """.trimIndent())
    val unexpectedSummons = Cards.invent("Unexpected Summons", "{2}{R}", "Sorcery", """
        Gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn. You may attach an Equipment you control to that creature. If you do, unattach it at the beginning of the next end step.
    """.trimIndent())
    val rauvanBullOfTheHighlands = Cards.invent("Rauvan, Bull of the Highlands", "{1}{R}", "Legendary Creature — Human Warrior", """
        Ward—Pay life equal to Rauvan's power.
        Whenever Rauvan attacks, attach up to one target Equipment you control to target attacking creature.
    """.trimIndent(), "2/2")
    val sorceresssPlots = Cards.invent("Sorceress's Plots", "{3}{R}", "Sorcery", """
        Return target instant or sorcery card from your graveyard or exiled card with flashback you own to your hand. Add {R}.
        Flashback {4}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """.trimIndent())
    val ferrionWildRoseRebel = Cards.invent("Ferrion, Wild Rose Rebel", "{2}{R}", "Legendary Creature — Human Rebel Warrior", """
        Equipped creatures you control have haste.
        Whenever a nontoken Equipment you control enters, create a token that's a copy of it, except it has "This Equipment's equip abilities cost {2} less to activate." Sacrifice that token at the beginning of the next upkeep.
    """.trimIndent(), "3/3")
    val tripleTriumph = Cards.invent("Triple Triumph", "{3}{R}{R}{R}", "Enchantment", """
        At the beginning of your upkeep, each player exiles the top card of their library. Until end of turn, you may play the card you own exiled this way and each other card exiled this way with lesser mana value than it without paying their mana costs.
    """.trimIndent())
    val judgmentStrike = Cards.invent("Judgment Strike", "{3}{R}", "Instant", """
        Judgment Strike deals 5 damage to target creature and X damage to that creature's controller, where X is the number of Equipment you control.
    """.trimIndent())
    val barrickWallace = Cards.invent("Barrick Wallace", "{3}{R}", "Legendary Creature — Human Rebel", """
        Reach
        Whenever Barrick Wallace attacks, it deals damage equal to the number of equipped creatures you control to defending player.
    """.trimIndent(), "4/4")
    val suddenEncounter = Cards.invent("Sudden Encounter", "{4}{R}{R}", "Sorcery", """
        Shuffle your library, then mill four cards. Put each creature card milled this way onto the battlefield. They gain haste. At the beginning of the next end step, return those creatures to their owner's hand.
        Flashback {6}{R}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """.trimIndent())
    val judgmentsLight = Cards.invent("Judgment's Light", "{4}{R}", "Instant", """
        Judgment's Light deals 6 damage to target creature. Destroy up to one Equipment attached to that creature.
    """.trimIndent())
    val vanoStreetThief = Cards.invent("Vano, Street Thief", "{2}{R}", "Legendary Creature — Human Scout", """
        Whenever one or more Scouts, Pirates, and/or Rogues you control deal combat damage to a player, exile the top card of that player's library. You may cast it. If you don't, create a Treasure token.
        Whenever you cast a spell you don't own, put a +1/+1 counter on each Scout, Pirate, and Rogue you control.
    """.trimIndent(), "2/2")
    // chunk 13
    val wanderersPath = Cards.invent("Wanderer's Path", "{2}{G}", "Instant", """
        Search your library for a basic land card or Town card, put it onto the battlefield tapped, then shuffle. When you search your library this way, put a +1/+1 counter on target creature you control.
    """.trimIndent())
    val villageGreeter = Cards.invent("Village Greeter", "{1}{G}", "Creature — Human Citizen", """
        When this creature enters, mill four cards. You may put a land card from among them into your hand. If you put a Town card into your hand this way, you gain 2 life. (To mill four cards, a player puts the top four cards of their library into their graveyard.)
    """.trimIndent(), "1/1")
    val wanderingStrider = Cards.invent("Wandering Strider", "{2}{G}", "Creature — Bird", """
        You may look at the top card of your library any time.
        You may play lands and cast Bird spells from the top of your library.
        If a land or Bird you control entering the battlefield causes a triggered ability of a permanent you control to trigger, that ability triggers an additional time.
    """.trimIndent(), "3/2")
    val rideTheBehemoth = Cards.invent("Ride the Behemoth", "{1}{G}", "Enchantment", """
        Landfall — Whenever a land you control enters, put a +1/+1 counter on target creature you control.
        {5}{G}{G}: This enchantment becomes a 7/7 Beast creature in addition to its other types.
    """.trimIndent())
    val theGroveCrystal = Cards.invent("The Grove Crystal", "{2}{G}{G}", "Legendary Artifact", """
        Green spells you cast cost {1} less to cast.
        If one or more +1/+1 counters would be put on a creature you control, twice that many +1/+1 counters are put on that creature instead.
        {4}{G}{G}, {T}: Distribute two +1/+1 counters among one or two target creatures you control.
    """.trimIndent())
    val gaelsFinalStand = Cards.invent("Gael's Final Stand", "{1}{G}", "Instant", """
        Until end of turn, target creature gets +1/+0 and gains "When this creature dies, put a number of +1/+1 counters equal to its power on up to one target creature."
    """.trimIndent())
    val quennaGourmet = Cards.invent("Quenna, Gourmet", "{2}{G}", "Legendary Creature — Qu", """
        If one or more tokens would be created under your control, those tokens plus a 1/1 green Frog creature token are created instead.
        {2}, Sacrifice a Frog: Put a +1/+1 counter on Quenna.
    """.trimIndent(), "2/3")
    val torvalAFineHound = Cards.invent("Torval, A Fine Hound", "{1}{G}", "Legendary Creature — Wolf", """
        Whenever you cast your first Human creature spell each turn, that creature enters with an additional +1/+1 counter on it for each Dog and/or Wolf you control.
        {T}: Add one mana of any color.
    """.trimIndent(), "2/2")
    val cactusSprinter = Cards.invent("Cactus Sprinter", "{G}", "Creature — Plant", """
        Trample
        At the beginning of your end step, if this creature didn't enter the battlefield this turn, return it to its owner's hand.
    """.trimIndent(), "3/3")
    val primevalTortoise = Cards.invent("Primeval Tortoise", "{5}{G}{G}{G}", "Creature — Turtle", """
        Vigilance, ward {3}
        Damage isn't removed from this creature during cleanup steps.
        All damage that would be dealt to you and other permanents you control is dealt to this creature instead.
        When this creature dies, exile it and create ten tapped Treasure tokens.
    """.trimIndent(), "8/20")
    val crystalWeapon = Cards.invent("Crystal Weapon", "{7}{G}{G}", "Legendary Artifact Creature — Elemental", """
        This spell costs {1} less to cast for each permanent card in your graveyard.
        Reach
        Immune — Prevent all combat damage that would be dealt to Crystal Weapon.
    """.trimIndent(), "8/8")
    val birdKick = Cards.invent("Bird Kick", "{1}{G}", "Sorcery", """
        Kicker—Return a land you control to its owner's hand. (You may return a land you control to its owner's hand in addition to any other costs as you cast this spell.)
        Target creature you control deals damage equal to its power to target creature an opponent controls. If this spell was kicked, the creature you control deals twice that much damage instead.
    """.trimIndent())
    val grovePulseOchu = Cards.invent("Grove Pulse Ochu", "{G}", "Creature — Plant Beast", """
        Deathtouch
        {8}: Until end of turn, this creature gets +1/+1 for each permanent card in your graveyard.
    """.trimIndent(), "1/1")
    val tiraLockheart = Cards.invent("Tira Lockheart", "{1}{G}", "Legendary Creature — Human Monk", """
        Trample
        Landfall — Whenever a land you control enters, double Tira Lockheart's power until end of turn.
    """.trimIndent(), "1/2")
    val bardAndBirdie = Cards.invent("Bard and Birdie", "{3}{G}{G}", "Legendary Creature — Human Bird", """
        Affinity for Birds (This spell costs {1} less to cast for each Bird you control.)
        When Bard and Birdie enters, each other Bird you control deals damage equal to its power to target creature an opponent controls.
    """.trimIndent(), "4/3")
    val beyondTheHorizon = Cards.invent("Beyond the Horizon", "{3}{G}", "Sorcery", """
        Search your library for up to two basic land cards and/or Town cards with different names, put them onto the battlefield tapped, then shuffle.
    """.trimIndent())
    // chunk 14
    val lightfallLegionOfOne = Cards.invent("Lightfall, Legion of One", "{1}{R}{W}", "Legendary Creature — Human Soldier", """
        First strike, trample, lifelink
        Stagger — Whenever Lightfall deals combat damage to a player, until your next turn, if a source would deal damage to that player or a permanent that player controls, it deals double that damage instead.
    """.trimIndent(), "3/2")
    val synnScourgeOfTheSea = Cards.invent("Synn, Scourge of the Sea", "{4}{B}{G}{U}", "Legendary Creature — Leviathan Avatar", """
        Flying
        Whenever Synn enters or attacks, exile a permanent card from your graveyard at random, then create a tapped token that's a copy of that card. If the exiled card is a land card, repeat this process.
    """.trimIndent(), "7/7")
    val garnaPrincessOfTheMist = Cards.invent("Garna, Princess of the Mist", "{G}{W}", "Legendary Creature — Human Noble Cleric", """
        Lifelink
        Whenever Garna attacks, you may remove a lore counter from each of any number of Sagas you control. Put a +1/+1 counter on Garna for each lore counter removed this way.
    """.trimIndent(), "2/2")
    val baltherAndFrin = Cards.invent("Balther and Frin", "{1}{R}{G}", "Legendary Creature — Human Rabbit", """
        Reach
        Vehicles you control get +1/+1 and have reach and vigilance.
        Whenever a Vehicle crewed by Balther and Frin this turn attacks, if it's the first combat phase of the turn, you may pay {1}{R}{G}. If you do, after this phase, there is an additional combat phase.
    """.trimIndent(), "4/3")
    val ignatiusCampChef = Cards.invent("Ignatius, Camp Chef", "{1}{G}{U}", "Legendary Creature — Human Advisor", """
        When Ignatius enters, look at the top six cards of your library. You may put a land card from among them onto the battlefield tapped. Put the rest on the bottom of your library in a random order.
        I've Come Up with a New Recipe! — {1}{G}{U}, {T}: Exile target card from a graveyard. If a creature card was exiled this way, create a Food token.
    """.trimIndent(), "2/2")
    val noctiaPrinceOfLight = Cards.invent("Noctia, Prince of Light", "{1}{W}{U}{B}", "Legendary Creature — Human Noble", """
        Lifelink
        You may cast artifact spells from your graveyard by paying 3 life in addition to paying their other costs. If you cast a spell this way, that artifact enters with a finality counter on it.
    """.trimIndent(), "4/3")
    val golbarCrystalHoarder = Cards.invent("Golbar, Crystal Hoarder", "{U}{B}", "Legendary Creature — Human Wizard", """
        Whenever an artifact you control enters, surveil 1.
        At the beginning of your end step, if you control four or more artifacts, return target creature card from your graveyard to your hand. Then if you control eight or more artifacts, each opponent loses life equal to that card's power.
    """.trimIndent(), "1/4")
    val zidrenTroupeThief = Cards.invent("Zidren, Troupe Thief", "{3}{R}{W}", "Legendary Creature — Human Mutant Scout", """
        When Zidren enters, gain control of target creature an opponent controls until end of turn. Untap it. It gains lifelink and haste until end of turn.
        Whenever an opponent gains control of a permanent from you, you create a Treasure token.
    """.trimIndent(), "3/3")
    val chocoboSeekerOfHavens = Cards.invent("Chocobo, Seeker of Havens", "{1}{G}{W}{U}", "Legendary Creature — Bird", """
        Whenever one or more Birds you control attack, look at that many cards from the top of your library. You may put one of them into your hand. Then put any number of land cards from among them onto the battlefield tapped and the rest into your graveyard.
        Landfall — Whenever a land you control enters, Chocobo gets +1/+0 until end of turn.
    """.trimIndent(), "3/5")
    val jenaraAncientCalamity = Cards.invent("Jenara, Ancient Calamity", "{2}{B}{G}", "Legendary Creature — Alien", """
        At the beginning of combat on your turn, put a number of +1/+1 counters equal to Jenara's power on up to one other target creature. That creature becomes a Mutant in addition to its other types.
        Whenever a Mutant you control dies during your turn, you draw cards equal to its power.
    """.trimIndent(), "1/5")
    val yunelleHopeOfTheIsles = Cards.invent("Yunelle, Hope of the Isles", "{3}{G}{W}", "Legendary Creature — Human Cleric", """
        During your turn, Yunelle and enchantment creatures you control have trample, lifelink, and ward {2}.
        At the beginning of your end step, return up to one target enchantment card from your graveyard to the battlefield with a finality counter on it. (If a permanent with a finality counter on it would be put into a graveyard from the battlefield, exile it instead.)
    """.trimIndent(), "3/5")
    val omicronHeartlessEvolution = Cards.invent("Omicron, Heartless Evolution", "{5}{G}{U}", "Legendary Artifact Creature — Robot", """
        Wave Cannon — When Omicron enters, for each opponent, tap up to one target nonland permanent that opponent controls. Put X stun counters on each of those permanents and you gain X life, where X is the number of nonbasic lands you control. (If a permanent with a stun counter would become untapped, remove one from it instead.)
    """.trimIndent(), "8/8")
    val theWanderingBard = Cards.invent("The Wandering Bard", "{G}{U}", "Legendary Creature — Human Bard", """
        Lands you control enter untapped.
        The Minstrel's Ballad — At the beginning of combat on your turn, if you control five or more Towns, create a 2/2 Elemental creature token that's all colors.
        {3}{W}{U}{B}{R}{G}: Other creatures you control get +X/+X until end of turn, where X is the number of Towns you control.
    """.trimIndent(), "1/3")
    // chunk 15
    val vallaCheerfulPulsebearer = Cards.inventFaces("Meld:Fenna, Fearless Pulsebearer",
        face("Valla, Cheerful Pulsebearer", "{3}{G}", "Legendary Creature — Human Cleric", """
            When Valla enters, mill two cards, then return a permanent card from your graveyard to your hand.
            At the beginning of your first main phase, if you both own and control Valla and a creature named Fenna, Fearless Pulsebearer, you may pay {3}{B}{G}. If you do, exile them, then meld them into Ragnaroth, Divine Deliverance.
        """.trimIndent(), "3/2", null, null),
        face("Ragnaroth, Divine Deliverance", null, "Legendary Creature — Beast Avatar", """
            Reach, vigilance, menace, trample, haste
            When Ragnaroth dies, destroy target permanent and return target nonlegendary permanent card from your graveyard to the battlefield.
        """.trimIndent(), "7/6", null, "BG"),
    )
    val pupuSaucer = Cards.invent("Pupu Saucer", "{2}", "Artifact Creature — Construct Alien", """
        Flying
        {T}: You may put a land card from your hand onto the battlefield.
        {3}: Until end of turn, this creature's base power becomes equal to the number of Towns you control.
    """.trimIndent(), "0/4")
    val aegisAndBlade = Cards.invent("Aegis and Blade", "{6}", "Legendary Artifact — Equipment", """
        Equipped creature has base power and toughness X/X, where X is your life total.
        Equip {5}
    """.trimIndent())
    val theTrueKatana = Cards.invent("The True Katana", "{3}", "Legendary Artifact — Equipment", """
        As long as equipped creature is attacking, it has first strike and must be blocked if able.
        Equipped creature has "If a creature dying causes a triggered ability of this creature or an emblem you own to trigger, that ability triggers an additional time."
        Equip {2}
    """.trimIndent())
    val fennaFearlessPulsebearer = Cards.inventFaces("Meld:Valla, Cheerful Pulsebearer",
        face("Fenna, Fearless Pulsebearer", "{2}{B}", "Legendary Creature — Human Warrior", """
            Whenever one or more cards leave your graveyard, you draw a card and you lose 1 life. This ability triggers only once each turn.
            (Melds with Valla, Cheerful Pulsebearer.)
        """.trimIndent(), "2/3", null, null),
        face("Ragnaroth, Divine Deliverance", null, "Legendary Creature — Beast Avatar", """
            Reach, vigilance, menace, trample, haste
            When Ragnaroth dies, destroy target permanent and return target nonlegendary permanent card from your graveyard to the battlefield.
        """.trimIndent(), "7/6", null, "BG"),
    )
    val broadswordOfTheSoldier = Cards.invent("Broadsword of the Soldier", "{3}", "Artifact — Equipment", """
        Equipped creature gets +3/+2.
        Whenever equipped creature deals combat damage to a player, draw a card, then you may cast a spell from your hand with mana value less than or equal to that damage without paying its mana cost.
        Equip {2}
    """.trimIndent())
    val firstVillage = Cards.invent("First Village", null, "Land — Town", """
        This land enters tapped unless it's your first, second, or third turn of the game.
        {T}: Add {C}.
        {T}, Pay 1 life: Add one mana of any color.
    """.trimIndent())
    val relentlessHunterUnit = Cards.invent("Relentless Hunter Unit", "{6}", "Artifact Creature — Robot Spider", """
        This creature can't be blocked except by three or more creatures.
        {8}: Return this card from your graveyard to the battlefield tapped with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)
    """.trimIndent(), "6/5")
    val cureAll = Cards.invent("Cure-All", "{1}", "Artifact", """
        This artifact enters tapped.
        {5}, {T}, Exile this artifact: Shuffle all nonland cards from your graveyard into your library. You gain life equal to the number of cards shuffled into your library this way.
    """.trimIndent())
    val corvansRetreat = Cards.invent("Corvan's Retreat", null, "Land — Town", """
        Hideaway 4 (When this land enters, look at the top four cards of your library, exile one face down, then put the rest on the bottom in a random order.)
        {T}: Add {C}.
        {2}, {T}: You may play the exiled card without paying its mana cost if you control four or more legendary creatures.
    """.trimIndent())
    val goalBall = Cards.invent("Goal Ball", "{3}", "Artifact", """
        {T}: Add one mana of any color.
        GOOOOAAAALLL! — {T}, Sacrifice this artifact: Draw two cards. Activate only if an opponent was dealt combat damage by a legendary creature this turn.
    """.trimIndent())
    val clockworkScythe = Cards.invent("Clockwork Scythe", "{4}", "Artifact — Equipment", """
        A Test of Your Reflexes! — When this Equipment enters, you may attach it to target creature you control. If you do, that creature gains first strike until end of turn and must be blocked this turn if able.
        Equipped creature gets +2/+1.
        Equip {2}
    """.trimIndent())
    val ultimusOriginOfTheVoid = Cards.invent("Ultimus, Origin of the Void", "{5}", "Legendary Creature — God", """
        Flying
        Whenever Ultimus attacks, put a blight counter on target land. For as long as that land has a blight counter on it, it loses all land types and abilities and has "{T}: Add {C}."
        Whenever you tap a land for {C}, add an additional {C}.
    """.trimIndent(), "4/4")
    // chunk 16
    val catfishTrader = Cards.invent("Catfish Trader", "{3}{B}", "Creature — Fish Citizen", """
        When this creature enters, you lose 1 life and create a Treasure token.
        Whenever this creature attacks, you may sacrifice another creature or artifact. If you do, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
    """.trimIndent(), "3/4")
    val aWorldReborn = Cards.invent("A World Reborn", "{4}{G}{G}", "Enchantment", "Other permanents you control have \"{T}: Add one mana of any color.\"")
    val treacherousEnvoysPlot = Cards.invent("Treacherous Envoy's Plot", "{1}{B}", "Instant", """
        Kicker—Sacrifice an artifact or creature. (You may sacrifice an artifact or creature in addition to any other costs as you cast this spell.)
        Target creature gets -2/-2 until end of turn. If this spell was kicked, that creature gets -6/-6 until end of turn instead.
    """.trimIndent())
    val runnerRacetrack = Cards.invent("Runner Racetrack", "{3}{G}{G}", "Artifact", """
        Landfall — Whenever a land you control enters, create a 2/2 green Bird creature token with "Whenever a land you control enters, this token gets +1/+0 until end of turn."
    """.trimIndent())
    val saszsChick = Cards.invent("Sasz's Chick", "{G}", "Creature — Bird", "Landfall — Whenever a land you control enters, put a +1/+1 counter on this creature.", "0/1")
    val reefSword = Cards.invent("Reef Sword", "{R}", "Artifact — Equipment", """
        Flash
        When this Equipment enters, attach it to target creature you control. That creature gains first strike until end of turn.
        Equipped creature gets +1/+0.
        Equip {1}
    """.trimIndent())
    val salvageCrew = Cards.invent("Salvage Crew", "{2}{B}", "Creature — Human Artificer Warrior", """
        Whenever this creature or another creature or artifact you control dies, target opponent loses 1 life and you gain 1 life.
    """.trimIndent(), "2/3")
    val manicLaughter = Cards.invent("Manic Laughter", "{2}{R}", "Instant", """
        As an additional cost to cast this spell, discard a card.
        Draw two cards.
        Flashback {3}{R} (You may cast this card from your graveyard for its flashback cost and any additional costs. Then exile it.)
    """.trimIndent())
    val burrowScout = Cards.invent("Burrow Scout", "{2}{G}", "Creature — Rabbit Scout", "Whenever another creature you control enters, this creature gets +1/+1 until end of turn.", "3/2")
    val meadowRexaur = Cards.invent("Meadow Rexaur", "{4}{G}{G}", "Creature — Dinosaur", """
        Trample
        When this creature enters, you gain 3 life.
        Forestcycling {2} ({2}, Discard this card: Search your library for a Forest card, reveal it, put it into your hand, then shuffle.)
    """.trimIndent(), "6/6")
    val shamblingHusk = Cards.invent("Shambling Husk", "{2}{B}", "Creature — Mutant Horror", """
        This creature enters tapped.
        Whenever you cast a noncreature spell, you may pay {B}. If you do, return this card from your graveyard to your hand.
    """.trimIndent(), "3/3")
    val callTheRidgeRunner = Cards.invent("Call the Ridge Runner", "{3}{R}", "Sorcery", """
        Search your library for a Mountain card, reveal it, put it into your hand, then shuffle. Create a 2/2 green Bird creature token with "Whenever a land you control enters, this token gets +1/+0 until end of turn."
        Flashback {5}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """.trimIndent())
    val spinyPlant = Cards.invent("Spiny Plant", "{1}{R}", "Creature — Plant", """
        Reach
        Landfall — Whenever a land you control enters, this creature deals 1 damage to each opponent.
    """.trimIndent(), "2/1")
    val dunesWorm = Cards.invent("Dunes Worm", "{4}{R}", "Creature — Worm", """
        Haste
        When this creature enters, destroy target land. Its controller may search their library for a basic land card, put it onto the battlefield tapped, then shuffle.
    """.trimIndent(), "5/4")
    val crushingGravity = Cards.invent("Crushing Gravity", "{2}{B}", "Instant", "Target creature gets -0/-9999 until end of turn.")
    val shadowConfidant = Cards.invent("Shadow Confidant", "{1}{B}", "Creature — Human Wizard", """
        At the beginning of your upkeep, reveal the top card of your library and put that card into your hand. You lose life equal to its mana value.
    """.trimIndent(), "2/1")
    val runnerComet = Cards.invent("Runner Comet", "{X}{R}{R}", "Sorcery", """
        Runner Comet deals X damage to any target.
        Create a 2/2 green Bird creature token with "Whenever a land you control enters, this token gets +1/+0 until end of turn."
    """.trimIndent())
    val keepFighting = Cards.invent("Keep Fighting", "{2}{B}", "Instant", "Return up to two target creature cards from your graveyard to your hand.")
    val strikerShot = Cards.invent("Striker Shot", "{1}{G}", "Instant", "Target creature gets +3/+3 and gains trample until end of turn.")
    val colossalCactus = Cards.invent("Colossal Cactus", "{5}{G}{G}", "Creature — Plant", "10,000 Needles — Whenever this creature attacks, it gets +9999/+0 until end of turn.", "1/7")
    val saiferAlmase = Cards.invent("Saifer Almase", "{3}{R}", "Legendary Creature — Human Knight", """
        Whenever a creature you control attacks alone, it gains double strike until end of turn.
        Fire Cross — Whenever Saifer Almase deals combat damage to a player, you may cast target instant or sorcery card with mana value 3 or less from your graveyard without paying its mana cost. If that spell would be put into your graveyard, exile it instead.
    """.trimIndent(), "3/4")
    val runnerGreens = Cards.invent("Runner Greens", "{1}{G}", "Sorcery", """
        Create a 2/2 green Bird creature token with "Whenever a land you control enters, this token gets +1/+0 until end of turn."
        Flashback {6}{G} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """.trimIndent())
    val rancorousRevelation = Cards.invent("Rancorous Revelation", "{1}{B}", "Sorcery", """
        Look at the top three cards of your library. Put one of them into your hand and the rest into your graveyard.
        Flashback {6}{B} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """.trimIndent())
    val theBlazeCrystal = Cards.invent("The Blaze Crystal", "{2}{R}{R}", "Legendary Artifact", """
        Red spells you cast cost {1} less to cast.
        Creatures you control have haste.
        {4}{R}{R}, {T}: Create a token that's a copy of target creature you control. Sacrifice it at the beginning of the next end step.
    """.trimIndent())
    val villageAflame = Cards.invent("Village Aflame", "{2}{R}{R}", "Sorcery", """
        Choose target creature you control. It deals damage equal to its power to each other creature. If this spell was cast from a graveyard, discard your hand and draw four cards.
        Flashback {5}{R}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """.trimIndent())
    val saszKatroy = Cards.invent("Sasz Katroy", "{3}{G}", "Legendary Creature — Human Pilot", """
        When Sasz Katroy enters, you may search your library for a Bird or basic land card, reveal it, put it into your hand, then shuffle.
        Whenever Sasz Katroy attacks, put a +1/+1 counter on target creature, then double the number of +1/+1 counters on that creature.
    """.trimIndent(), "3/3")
    val sevmarVoid = Cards.invent("Sevmar Void", "{4}{B}", "Legendary Creature — Spirit Avatar", """
        At the beginning of your upkeep, you may pay 1 life. If you do, draw a card and put a +1/+1 counter on Sevmar Void.
    """.trimIndent(), "5/5")
    val zellerDinch = Cards.invent("Zeller Dinch", "{2}{R}", "Legendary Creature — Human Monk", """
        You may play an additional land on each of your turns.
        Zeller Dinch gets +1/+0 for each land you control.
        At the beginning of your end step, return a land you control to its owner's hand.
    """.trimIndent(), "0/3")
    val cohortReinforcements = Cards.invent("Cohort Reinforcements", "{2}{B}", "Creature — Human Soldier", """
        When this creature enters, mill three cards and you gain 3 life. (To mill three cards, put the top three cards of your library into your graveyard.)
    """.trimIndent(), "2/3")
    val skyshipCrash = Cards.invent("Skyship Crash", "{2}{G}", "Instant", """
        Destroy target artifact, enchantment, or creature with flying.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """.trimIndent())
    val communeWithOtters = Cards.invent("Commune with Otters", "{G}", "Sorcery", """
        Look at the top three cards of your library. You may reveal an artifact, creature, or land card from among them and put it into your hand. Put the rest on the bottom of your library in any order.
    """.trimIndent())
    val manyEyedOoze = Cards.invent("Many-Eyed Ooze", "{1}{B}", "Creature — Ooze Horror", "When this creature enters, each opponent discards a card.", "1/1")
    val greatToad = Cards.invent("Great Toad", "{3}{G}", "Creature — Frog", "As long as you control seven or more lands, this creature gets +2/+2.", "4/4")
    val ahrielEye = Cards.invent("Ahriel Eye", "{2}{B}", "Creature — Eye Horror", """
        Flying, deathtouch
        {3}, Sacrifice another creature or artifact: Draw a card.
    """.trimIndent(), "2/2")
    // chunk 17
    val cupNoodles = Cards.invent("Cup Noodles", "{2}", "Artifact — Food", """
        Flash
        When this artifact enters, draw a card.
        {2}, {T}, Sacrifice this artifact: You gain 3 life.
    """.trimIndent())
    val edenSanctumSeat = Cards.invent("Eden, Sanctum Seat", null, "Land — Town", """
        {T}: Add {C}.
        {5}, {T}: Mill two cards. Then you may sacrifice this land. When you do, return another target permanent card from your graveyard to your hand.
    """.trimIndent())
    val rhyaSummonerOfMist = Cards.invent("Rhya, Summoner of Mist", "{R}{G}", "Legendary Creature — Human Shaman", """
        Landfall — Whenever a land you control enters, you may discard a card. If you do, draw a card.
        Summon — {X}, {T}: Return target Saga card with mana value X from your graveyard to the battlefield with a finality counter on it. It gains haste until end of turn. Activate only as a sorcery.
    """.trimIndent(), "1/2")
    val gauntletOfGenshi = Cards.invent("Gauntlet of Genshi", "{5}", "Artifact — Equipment", """
        Equipped creature has double strike.
        Whenever equipped creature attacks, if it's the first combat phase of the turn, untap it. After this phase, there is an additional combat phase.
        Equip {3}
    """.trimIndent())
    val forgeRegency = Cards.inventFaces("Adventure",
        face("Forge Regency", null, "Land — Town", """
            This land enters tapped.
            {T}: Add {R}.
        """.trimIndent(), null, null, null),
        face("Siege of Mages", "{2}{R}", "Instant — Adventure", """
            Create a 0/1 black Wizard creature token with "Whenever you cast a noncreature spell, this token deals 1 damage to each opponent."
        """.trimIndent(), null, null, null),
    )
    val squalSeedMercenary = Cards.invent("Squal, Seed Mercenary", "{2}{W}{B}", "Legendary Creature — Human Knight Mercenary", """
        Rough Divide — Whenever a creature you control attacks alone, it gains double strike until end of turn.
        Whenever Squal deals combat damage to a player, return target permanent card with mana value 3 or less from your graveyard to the battlefield.
    """.trimIndent(), "3/4")
    val harborTown = Cards.invent("Harbor Town", null, "Land — Town", """
        This land enters tapped.
        {T}: Add {U} or {R}.
    """.trimIndent())
    val miracleUrn = Cards.invent("Miracle Urn", "{3}", "Artifact Creature — Goblin Construct", """
        When this creature dies, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        {2}, {T}: Exile target card from a graveyard.
    """.trimIndent(), "1/4")
    val brightbladeIi = Cards.invent("Brightblade II", "{1}", "Legendary Artifact — Equipment", """
        Whenever you gain life, put a charge counter on Brightblade II.
        Equipped creature gets +1/+1 for each charge counter on Brightblade II.
        Equip {3}
    """.trimIndent())
    val lorneColeman = Cards.invent("Lorne Coleman", "{1}{U}{B}", "Legendary Creature — Human Rogue", """
        Deathtouch, lifelink
        Whenever Lorne Coleman deals combat damage to a player, draw a card, then discard a card.
    """.trimIndent(), "2/3")
    val rexShinral = Cards.invent("Rex Shinral", "{1}{W}{B}", "Legendary Creature — Human Noble", """
        Whenever Rex Shinral attacks, if you don't control a creature named Nightshade, create Nightshade, a legendary 2/2 white and black Dog creature token.
    """.trimIndent(), "2/4")
    val boltSecuritySergeant = Cards.invent("Bolt, Security Sergeant", "{2}{R}", "Legendary Creature — Human Soldier", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Whenever Bolt deals combat damage to a player, exile the top card of your library. You may play that card for as long as you control Bolt.
    """.trimIndent(), "2/3")
    val lunarPandora = Cards.invent("Lunar Pandora", "{1}", "Legendary Artifact", """
        {2}, {T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        {6}, {T}, Sacrifice Lunar Pandora: Destroy target nonland permanent.
    """.trimIndent())
    val holySeeOfIshvar = Cards.inventFaces("Adventure",
        face("Holy See of Ishvar", null, "Land — Town", """
            This land enters tapped.
            {T}: Add {W}.
        """.trimIndent(), null, null, null),
        face("Hope and Sorrow", "{3}{W}{W}", "Sorcery — Adventure", """
            Return up to two target artifact and/or enchantment cards from your graveyard to your hand. (Then exile this card. You may play the land later from exile.)
        """.trimIndent(), null, null, null),
    )
    val worldAtlas = Cards.invent("World Atlas", "{1}", "Artifact", """
        {1}, {T}, Sacrifice this artifact: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.
        {3}, {T}, Sacrifice this artifact: Search your library for a land card, reveal it, put it into your hand, then shuffle.
    """.trimIndent())
    val lionmanePendant = Cards.invent("Lionmane Pendant", "{4}", "Artifact — Equipment", """
        When this Equipment enters, it deals 2 damage to any target.
        Equipped creature gets +2/+1.
        Equip {2}
    """.trimIndent())
    val ancientMetropolisOfZan = Cards.inventFaces("Adventure",
        face("Ancient Metropolis of Zan", null, "Land — Town", """
            This land enters tapped.
            {T}: Add {G}.
        """.trimIndent(), null, null, null),
        face("Enduring Faith", "{4}{G}{G}", "Sorcery — Adventure", """
            Create a 1/1 colorless Hero creature token. Put a +1/+1 counter on it for each land you control. (Then exile this card. You may play the land later from exile.)
        """.trimIndent(), null, null, null),
    )
    val ultimateArmament = Cards.invent("Ultimate Armament", "{7}", "Legendary Artifact — Equipment", """
        Whenever equipped creature attacks, destroy target creature an opponent controls.
        Equipped creature gets +7/+7.
        Equip {7}
    """.trimIndent())
    val claudPlanetsChampion = Cards.invent("Claud, Planet's Champion", "{3}{R}{W}", "Legendary Creature — Human Soldier Mercenary", """
        During your turn, as long as Claud is equipped, it has double strike and indestructible. (This creature deals both first-strike and regular combat damage. Damage and effects that say "destroy" don't destroy this creature.)
        Equip abilities you activate that target Claud cost {2} less to activate.
    """.trimIndent(), "4/4")
    val theRoyalRoadster = Cards.invent("The Royal Roadster", "{4}", "Legendary Artifact — Vehicle", """
        Haste
        Whenever The Royal Roadster attacks, reveal cards from the top of your library until you reveal a land card. Put that card onto the battlefield tapped and the rest on the bottom of your library in a random order.
        Crew 1
    """.trimIndent(), "4/4")
    val villainsDemise = Cards.invent("Villain's Demise", "{3}{B}", "Instant", "Destroy target creature. You gain 2 life.")
    val aristocratsCapital = Cards.inventFaces("Adventure",
        face("Aristocrats' Capital", null, "Land — Town", """
            This land enters tapped.
            {T}: Add {U}.
        """.trimIndent(), null, null, null),
        face("Grand Overture", "{4}{U}{U}", "Sorcery — Adventure", """
            Target opponent mills half their library, rounded down. (Then exile this card. You may play the land later from exile.)
        """.trimIndent(), null, null, null),
    )
    val crossingVillage = Cards.invent("Crossing Village", null, "Land — Town", """
        This land enters tapped. As it enters, choose a color.
        {T}: Add one mana of the chosen color.
    """.trimIndent())
    val gerrotKingOfTheDwarves = Cards.invent("Gerrot, King of the Dwarves", "{R}{W}", "Legendary Creature — Dwarf Noble", """
        Double strike
        Whenever Gerrot or another Dwarf you control enters and whenever an Equipment you control enters, you may discard a card. If you do, draw a card.
    """.trimIndent(), "1/1")
    val severinPlanetsHeir = Cards.invent("Severin, Planet's Heir", "{4}{U}{B}", "Legendary Creature — Human Avatar Soldier", """
        Vigilance (Attacking doesn't cause this creature to tap.)
        When Severin enters, creatures your opponents control get -2/-2 until end of turn.
        Whenever a creature an opponent controls dies, put a +1/+1 counter on Severin.
    """.trimIndent(), "4/4")
    val ringOfTheKings = Cards.invent("Ring of the Kings", "{4}", "Legendary Artifact", """
        {T}: Add {C}{C}.
        {2}, {T}, Pay 1 life: Tap target nonland permanent.
    """.trimIndent())
    val judgeMagistrateGavran = Cards.invent("Judge Magistrate Gavran", "{W}{B}", "Legendary Creature — Human Advisor Knight", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Whenever another creature or artifact you control dies, put a +1/+1 counter on Judge Magistrate Gavran.
    """.trimIndent(), "2/2")
    val cityOfGlowingMako = Cards.inventFaces("Adventure",
        face("City of Glowing Mako", null, "Land — Town", """
            This land enters tapped.
            {T}: Add {B}.
        """.trimIndent(), null, null, null),
        face("Reactor Heist", "{2}{B}", "Sorcery — Adventure", """
            You may sacrifice an artifact or creature. If you do, draw two cards. (Then exile this card. You may play the land later from exile.)
        """.trimIndent(), null, null, null),
    )
    val theGoldenArcade = Cards.invent("The Golden Arcade", null, "Land — Town", """
        {T}: Add {C}.
        {2}, {T}: Flip a coin. If you win the flip, create a Treasure token.
        {3}, {T}, Sacrifice two artifacts: Draw a card.
    """.trimIndent())
    val rinaHeartsong = Cards.invent("Rina Heartsong", "{3}{G}{W}", "Legendary Creature — Human Rebel Warlock", """
        When Rina Heartsong enters, create Biscuit, a legendary 1/1 green and white Dog creature token.
        Angelo Cannon — Whenever Rina Heartsong attacks, another target creature you control gets +1/+1 until end of turn for each creature you control.
    """.trimIndent(), "4/4")
    val capitalCitadel = Cards.invent("Capital Citadel", null, "Land — Town", """
        {T}: Add {C}.
        {1}, {T}: Add one mana of any color.
        Cycling {2} ({2}, Discard this card: Draw a card.)
    """.trimIndent())
    val travelersRest = Cards.invent("Traveler's Rest", null, "Land — Town", """
        When this land enters, you gain 2 life.
        {T}: Add {C}.
    """.trimIndent())
}
