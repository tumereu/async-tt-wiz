package arcanum.engine.sets.tla

import arcanum.engine.cards.Cards
import arcanum.engine.cards.face

/**
 * The invented Avatar: The Last Airbender (tla) cards of the parser tests (`arcanum.parser.sets.tla`), the same names and texts, for
 * the scenarios: each property registers its card and holds its name (the front face's for a double-faced card).
 */
internal object TlaSet {
    // chunk 01 (Airbend)
    val kiroTheLastWindwalker = Cards.invent("Kiro, the Last Windwalker", "{3}{W}", "Legendary Creature \u2014 Human Avatar Ally", """
            Flying
            When Kiro enters, airbend up to one other target nonland permanent. (Exile it. While it's exiled, its owner may cast it for {2} rather than its mana cost.)
            Whenever you cast a Lesson spell, Kiro gains lifelink until end of turn.
        """.trimIndent(), "3/2")
    val windwalkerAscension = Cards.invent("Windwalker Ascension", "{1}{W}", "Enchantment", """
            When this enchantment enters, airbend up to one target creature.
            Whenever a creature you control enters, put a quest counter on this enchantment.
            At the beginning of your end step, if this enchantment has four or more quest counters on it, exile up to one target creature you control, then return it to the battlefield under its owner's control.
        """.trimIndent(), null)
    val windwalkersReversal = Cards.invent("Windwalker's Reversal", "{1}{W}", "Instant \u2014 Lesson", """
            Choose one —
            • Destroy target attacking creature.
            • Airbend target creature you control. (Exile it. While it's exiled, its owner may cast it for {2} rather than its mana cost.)
        """.trimIndent(), null)
    val windweavingLesson = Cards.invent("Windweaving Lesson", "{2}{W}", "Instant \u2014 Lesson", """
            Airbend target nonland permanent. (Exile it. While it's exiled, its owner may cast it for {2} rather than its mana cost.)
            Draw a card.
        """.trimIndent(), null)
    val brumbleLoyalSkyYak = Cards.invent("Brumble, Loyal Sky Yak", "{4}{W}{W}", "Legendary Creature \u2014 Bison Ally", """
            Flying
            Whenever Brumble enters or attacks, choose one —
            • Target creature you control gains flying until end of turn.
            • Airbend another target nonland permanent you control. (Exile it. While it's exiled, its owner may cast it for {2} rather than its mana cost.)
        """.trimIndent(), "4/4")
    val brumbleSteadfastGuardian = Cards.invent("Brumble, Steadfast Guardian", "{2}{W}{W}", "Legendary Creature \u2014 Bison Ally", """
            Flash
            Flying
            When Brumble enters, airbend any number of other target nonland permanents you control. (Exile them. While each one is exiled, its owner may cast it for {2} rather than its mana cost.)
            Whenever you cast a spell from exile, create a 1/1 white Ally creature token.
        """.trimIndent(), "3/4")
    val heraldsWrath = Cards.invent("Herald's Wrath", "{2}{W}{W}", "Sorcery", """
            Choose up to one target creature, then airbend all other creatures. (Exile them. While each one is exiled, its owner may cast it for {2} rather than its mana cost.)
            Until your next turn, your opponents can't cast spells from anywhere other than their hands.
            Exile Herald's Wrath.
        """.trimIndent(), null)
    val kiteStaff = Cards.invent("Kite Staff", "{2}{W}", "Artifact \u2014 Equipment", """
            When this Equipment enters, airbend up to one target creature. (Exile it. While it's exiled, its owner may cast it for {2} rather than its mana cost.)
            Equipped creature gets +1/+1 and has flying.
            Equip {2}
        """.trimIndent(), null)
    val theTaleOfWynne = Cards.inventFaces("Transform",
        face("The Tale of Wynne", "{3}{W}{W}", "Enchantment \u2014 Saga", """
            (As this Saga enters and after your draw step, add a lore counter.)
            I — Starting with you, each player chooses up to one permanent with mana value 3 or greater from among permanents your opponents control. Exile those permanents.
            II — You may have target opponent draw three cards. If you do, draw three cards.
            III — Exile this Saga, then return it to the battlefield transformed under your control.
        """.trimIndent(), null, null, null),
        face("Herald Wynne", null, "Legendary Creature \u2014 Avatar", """
            Flying
            Whenever you cast your second spell each turn, airbend up to one other target nonland permanent. (Exile it. While it's exiled, its owner may cast it for {2} rather than its mana cost.)
        """.trimIndent(), "4/5", null, null),
    )
    val kiroSwiftSavior = Cards.inventFaces("Transform",
        face("Kiro, Swift Savior", "{1}{W}{U}", "Legendary Creature \u2014 Human Avatar Ally", """
            Flash
            Flying
            When Kiro enters, airbend up to one other target creature or spell. (Exile it. While it's exiled, its owner may cast it for {2} rather than its mana cost.)
            Waterbend {8}: Transform Kiro.
        """.trimIndent(), "2/3", null, null),
        face("Kiro and Tide, Ocean's Fury", null, "Legendary Creature \u2014 Avatar Spirit Ally", """
            Reach, trample
            Whenever Kiro and Tide attack, put a +1/+1 counter on each tapped creature you control.
        """.trimIndent(), "5/5", null, null),
    )
    val kirosGlacier = Cards.invent("Kiro's Glacier", "{2}{W}", "Enchantment", """
            Flash
            When this enchantment enters, exile up to one other target nonland permanent until this enchantment leaves the battlefield.
            Waterbend {3}: Sacrifice this enchantment. If you do, scry 2. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """.trimIndent(), null)
    // chunk 02 (EarthbendCreatures)
    val burrowmole = Cards.invent("Burrowmole", "{4}{G}", "Creature \u2014 Badger Mole", """
            When this creature enters, earthbend 2. (Target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
            Creatures you control with +1/+1 counters on them have trample.
        """.trimIndent(), "4/4")
    val burrowmoleCub = Cards.invent("Burrowmole Cub", "{1}{G}", "Creature \u2014 Badger Mole", """
            When this creature enters, earthbend 1. (Target land you control becomes a 0/0 creature with haste that's still a land. Put a +1/+1 counter on it. When it dies or is exiled, return it to the battlefield tapped.)
            Whenever you tap a creature for mana, add an additional {G}.
        """.trimIndent(), "2/2")
    val theCragReadyToBrawl = Cards.invent("The Crag, Ready to Brawl", "{3}{G}", "Legendary Creature \u2014 Human Warrior Performer", "Whenever The Crag attacks, earthbend X, where X is the number of creatures you control with power 4 or greater. (Target land you control becomes a 0/0 creature with haste that's still a land. Put X +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)", "4/4")
    val odricKingOfThreeTrials = Cards.invent("Odric, King of Three Trials", "{5}{G}", "Legendary Creature \u2014 Human Noble Ally", """
            When Odric enters, choose up to X, where X is the number of Lesson cards in your graveyard —
            • Put three +1/+1 counters on Odric.
            • Target player scries 3.
            • Earthbend 3. (Target land you control becomes a 0/0 creature with haste that's still a land. Put three +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
        """.trimIndent(), "4/4")
    val stoneRealmGeneral = Cards.invent("Stone Realm General", "{3}{G}", "Creature \u2014 Human Soldier Ally", """
            When this creature enters, earthbend 2. (Target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
            Whenever you put one or more +1/+1 counters on a creature, you may gain that much life. Do this only once each turn.
        """.trimIndent(), "2/2")
    val stoneBrawl = Cards.invent("Stone Brawl", "{3}{G}", "Sorcery", "Earthbend 2. When you do, up to one target creature you control fights target creature an opponent controls. (To earthbend 2, target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped. Creatures that fight each deal damage equal to their power to the other.)", null)
    val stoneshaperAscension = Cards.invent("Stoneshaper Ascension", "{2}{G}", "Enchantment", """
            When this enchantment enters, earthbend 2. Then search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
            Landfall — Whenever a land you control enters, put a quest counter on this enchantment. When you do, if it has four or more quest counters on it, put a +1/+1 counter on target creature you control. It gains trample until end of turn.
        """.trimIndent(), null)
    val stoneshapingLesson = Cards.invent("Stoneshaping Lesson", "{3}{G}", "Sorcery \u2014 Lesson", "Earthbend 4. (Target land you control becomes a 0/0 creature with haste that's still a land. Put four +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)", null)
    val earthboundComrade = Cards.invent("Earthbound Comrade", "{G}", "Creature \u2014 Human Soldier Ally", """
            This creature gets +1/+0 for each color among Allies you control.
            {2}{W}{U}{B}{R}{G}: Earthbend 5. (Target land you control becomes a 0/0 creature with haste that's still a land. Put five +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
        """.trimIndent(), "0/2")
    val tobinHiddenTalent = Cards.invent("Tobin, Hidden Talent", "{1}{G}", "Legendary Creature \u2014 Human Peasant Ally", "Whenever another Ally you control enters, earthbend 1. (Target land you control becomes a 0/0 creature with haste that's still a land. Put a +1/+1 counter on it. When it dies or is exiled, return it to the battlefield tapped.)", "1/1")
    val mutinousPrisoners = Cards.invent("Mutinous Prisoners", "{1}{G}", "Creature \u2014 Human Peasant Ally", "Exhaust \u2014 {6}: Put two +1/+1 counters on this creature, then earthbend 2. (Target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped. Activate each exhaust ability only once.)", "2/2")
    val stoneslide = Cards.invent("Stoneslide", "{2}{G}", "Sorcery \u2014 Lesson", """
            Earthbend X, where X is the number of Forests you control. (Target land you control becomes a 0/0 creature with haste that's still a land. Put X +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
            Flashback {5}{G} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """.trimIndent(), null)
    val miraTheSightlessBrawler = Cards.invent("Mira, the Sightless Brawler", "{2}{G}", "Legendary Creature \u2014 Human Warrior Ally", """
            When Mira enters, earthbend 2. (Target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
            Mira's power is equal to the number of +1/+1 counters on lands you control.
        """.trimIndent(), "*/3")
    val stoneholm = Cards.invent("Stoneholm", null, "Land", """
            This land enters tapped unless you control a basic land.
            {T}: Add {G}.
            {2}{G}, {T}: Earthbend 2. Activate only as a sorcery. (Target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
        """.trimIndent(), null)
    // chunk 03 (EarthbendMore)
    val dunecallersStorm = Cards.invent("Dunecallers' Storm", "{3}{W}", "Instant", """
            Choose one —
            • Destroy target creature with power 4 or greater.
            • Earthbend 3. (Target land you control becomes a 0/0 creature with haste that's still a land. Put three +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
        """.trimIndent(), null)
    val stoneGuardConditioning = Cards.invent("Stone Guard Conditioning", "{1}{B}", "Sorcery \u2014 Lesson", """
            Choose one —
            • Target opponent reveals their hand. You choose a nonland permanent card from it. That player discards that card.
            • Earthbend 2. (Target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
        """.trimIndent(), null)
    val deadlyCrevasse = Cards.invent("Deadly Crevasse", "{1}{B}", "Instant", "Choose target creature. When that creature dies this turn, you earthbend 4. (Target land you control becomes a 0/0 creature with haste that's still a land. Put four +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)", null)
    val theGrottoOfTwoSweethearts = Cards.invent("The Grotto of Two Sweethearts", "{3}{R}", "Enchantment \u2014 Saga", """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Create two 1/1 white Ally creature tokens.
            II — Search your library for a Mountain or Cave card, reveal it, put it into your hand, then shuffle.
            III — Earthbend 3. (Target land you control becomes a 0/0 creature with haste that's still a land. Put three +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
        """.trimIndent(), null)
    val theTaleOfMaelis = Cards.inventFaces("Transform",
        face("The Tale of Maelis", "{4}{G}{G}", "Enchantment \u2014 Saga", """
            (As this Saga enters and after your draw step, add a lore counter.)
            I — Draw cards equal to the greatest power among creatures you control.
            II — Earthbend X, where X is the number of cards in your hand. That land becomes an Island in addition to its other types.
            III — Exile this Saga, then return it to the battlefield transformed under your control.
        """.trimIndent(), null, null, null),
        face("Herald Maelis", null, "Legendary Creature \u2014 Avatar", """
            Lands you control have trample and hexproof.
            {T}: Add X mana of any one color, where X is the greatest power among creatures you control.
        """.trimIndent(), "5/4", null, null),
    )
    val kiroAtTheFork = Cards.inventFaces("Transform",
        face("Kiro, at the Fork", "{2}{G}{W}{U}", "Legendary Creature \u2014 Human Avatar Ally", """
            Flying
            When Kiro enters, look at the top five cards of your library. You may put a creature card with mana value 4 or less from among them onto the battlefield. Put the rest on the bottom of your library in a random order.
            When another creature you control leaves the battlefield, transform Kiro at the beginning of the next upkeep.
        """.trimIndent(), "3/3", null, null),
        face("Kiro, Fated Savior", null, "Legendary Creature \u2014 Avatar Ally", """
            Flying
            Land creatures you control have vigilance.
            At the beginning of combat on your turn, earthbend 2. (Target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
        """.trimIndent(), "4/4", null, null),
    )
    val ironclanBountyHunters = Cards.invent("Ironclan Bounty Hunters", "{2}{B}{G}", "Creature \u2014 Human Mercenary", "Whenever a nonland creature you control dies, earthbend X, where X is that creature's power. (Target land you control becomes a 0/0 creature with haste that's still a land. Put X +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)", "4/4")
    val gruelingToil = Cards.invent("Grueling Toil", "{1}{R}{G}", "Enchantment", """
            Whenever you attack a player with one or more creatures with power 4 or greater, draw a card.
            Exhaust — {4}: Earthbend 4. Activate only during your turn. (Target land you control becomes a 0/0 creature with haste that's still a land. Put four +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped. Activate each exhaust ability only once.)
        """.trimIndent(), null)
    val odricUnleashed = Cards.invent("Odric, Unleashed", "{3}{R}{G}", "Legendary Creature \u2014 Human Noble Ally", """
            Trample
            When Odric enters, earthbend 4.
            Whenever Odric deals combat damage to a player, untap all lands you control. After this phase, there is an additional combat phase. Only land creatures can attack during that combat phase.
        """.trimIndent(), "5/4")
    val stoneGuardAgents = Cards.invent("Stone Guard Agents", "{3}{B}{G}", "Creature \u2014 Human Soldier", """
            When this creature enters, earthbend 1, then earthbend 1. (To earthbend 1, target land you control becomes a 0/0 creature with haste that's still a land. Put a +1/+1 counter on it. When it dies or is exiled, return it to the battlefield tapped.)
            Whenever this creature attacks, each opponent loses X life and you gain X life, where X is the number of creatures you control with +1/+1 counters on them.
        """.trimIndent(), "3/4")
    val stoneVillageRuffians = Cards.invent("Stone Village Ruffians", "{2}{B/G}", "Creature \u2014 Human Soldier Rogue", "When this creature dies, earthbend 2. (Target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)", "3/1")
    val miraHardheadedTeacher = Cards.invent("Mira, Hardheaded Teacher", "{2}{R}{G}", "Legendary Creature \u2014 Human Warrior Ally", """
            When Mira enters, you may discard a card. If you do, return target instant or sorcery card from your graveyard to your hand.
            Whenever you cast a spell, earthbend 1. If that spell is a Lesson, put an additional +1/+1 counter on that land. (Target land you control becomes a 0/0 creature with haste that's still a land. Put a +1/+1 counter on it. When it dies or is exiled, return it to the battlefield tapped.)
        """.trimIndent(), "3/4")
    val miraTheFirstIronshaper = Cards.invent("Mira, the First Ironshaper", "{1}{R}{G}{W}", "Legendary Creature \u2014 Human Warrior Ally", """
            Nontoken artifacts you control are lands in addition to their other types. (They don't gain the ability to {T} for mana.)
            At the beginning of your end step, earthbend 2. (Target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
        """.trimIndent(), "3/3")
    // chunk 04 (WaterbendCosts)
    val tideclanRallier = Cards.invent("Tideclan Rallier", "{1}{W}", "Creature \u2014 Human Soldier Ally", "Waterbend {5}: Look at the top four cards of your library. You may reveal a creature card with power 3 or less from among them and put it into your hand. Put the rest on the bottom of your library in a random order. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)", "2/2")
    val kindlyBrookSpirit = Cards.invent("Kindly Brook Spirit", "{U}{U}", "Creature \u2014 Spirit", """
            As an additional cost to cast this spell, waterbend {5}. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
            Flying, ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
            When this creature enters, scry 2.
        """.trimIndent(), "4/5")
    val breakingSurge = Cards.invent("Breaking Surge", "{U}{U}", "Sorcery", """
            As an additional cost to cast this spell, waterbend {X}. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
            Tap up to X target creatures, then distribute three stun counters among any number of tapped creatures your opponents control. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """.trimIndent(), null)
    val suppleTideshaper = Cards.invent("Supple Tideshaper", "{3}{U}", "Creature \u2014 Human Warrior Ally", """
            Vigilance
            Waterbend {3}: This creature has base power and toughness 5/2 until end of turn. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """.trimIndent(), "2/5")
    val spoutJumper = Cards.invent("Spout Jumper", "{4}{U}", "Creature \u2014 Human Warrior Ally", """
            Flying
            Waterbend {4}: Draw a card, then discard a card. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """.trimIndent(), "4/3")
    val colossalCarp = Cards.invent("Colossal Carp", "{4}{U}{U}", "Creature \u2014 Fish", """
            Waterbend {3}: This creature can't be blocked this turn. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
            Islandcycling {2} ({2}, Discard this card: Search your library for an Island card, reveal it, put it into your hand, then shuffle.)
        """.trimIndent(), "5/7")
    val siegeDivingBell = Cards.invent("Siege Diving Bell", "{2}{U}", "Artifact \u2014 Vehicle", """
            When this Vehicle enters, return up to one other target nonland permanent to its owner's hand.
            Exhaust — Waterbend {3}: This Vehicle becomes an artifact creature. Put three +1/+1 counters on it. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}. Activate each exhaust ability only once.)
        """.trimIndent(), "0/0")
    val nerysShapingProdigy = Cards.invent("Nerys, Shaping Prodigy", "{2}{U}", "Legendary Creature \u2014 Human Warrior Ally", """
            At the beginning of your end step, if Nerys is tapped, put a +1/+1 counter on her.
            Waterbend {6}: Draw a card. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """.trimIndent(), "2/3")
    val theTaleOfOsric = Cards.inventFaces("Transform",
        face("The Tale of Osric", "{2}{U}{U}", "Enchantment \u2014 Saga", """
            (As this Saga enters and after your draw step, add a lore counter.)
            I, II — Scry 2, then draw a card.
            III — Exile this Saga, then return it to the battlefield transformed under your control.
        """.trimIndent(), null, null, null),
        face("Herald Osric", null, "Legendary Creature \u2014 Avatar", """
            Whenever you cast a spell, create a 1/1 colorless Spirit creature token with "This token can't block or be blocked by non-Spirit creatures."
            Exhaust — Waterbend {20}: Take an extra turn after this one. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}. Activate each exhaust ability only once.)
        """.trimIndent(), "4/3", null, null),
    )
    val northernIcePatrol = Cards.invent("Northern Ice Patrol", "{2}{U}", "Creature \u2014 Human Soldier Ally", """
            {T}: Untap another target permanent you control.
            Waterbend {3}, {T}: Tap target creature an opponent controls. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """.trimIndent(), "2/3")
    val secretOfVeinshaping = Cards.invent("Secret of Veinshaping", "{U}{U}{U}{U}", "Sorcery \u2014 Lesson", """
            As an additional cost to cast this spell, you may waterbend {10}.
            You control target opponent during their next combat phase. If this spell's additional cost was paid, you control that player during their next turn instead. (You see all cards that player could see and make all decisions for them.)
            Exile Secret of Veinshaping.
        """.trimIndent(), null)
    val holySpringRevival = Cards.invent("Holy Spring Revival", "{1}{U}{U}", "Sorcery", """
            As an additional cost to cast this spell, you may waterbend {6}. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
            Draw two cards. If this spell's additional cost was paid, instead shuffle your graveyard into your library, draw seven cards, and you have no maximum hand size for the rest of the game.
            Exile Holy Spring Revival.
        """.trimIndent(), null)
    // chunk 05 (WaterbendMore)
    val theLeviathanOfReedIsle = Cards.invent("The Leviathan of Reed Isle", "{3}{U}{U}", "Legendary Creature \u2014 Serpent", """
            Flash
            Ward—Waterbend {4}. (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {4}. They can tap their artifacts and creatures to help. Each one pays for {1}.)
            Whenever an opponent draws their second card each turn, you draw two cards.
        """.trimIndent(), "5/5")
    val tideshaperAscension = Cards.invent("Tideshaper Ascension", "{1}{U}", "Enchantment", """
            Whenever a creature you control deals combat damage to a player, put a quest counter on this enchantment. Then if it has four or more quest counters on it, draw a card.
            Waterbend {4}: Target creature can't be blocked this turn. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """.trimIndent(), null)
    val tideshapingLesson = Cards.invent("Tideshaping Lesson", "{3}{U}", "Sorcery \u2014 Lesson", "Draw three cards. Then discard a card unless you waterbend {2}. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)", null)
    val drowningGrip = Cards.invent("Drowning Grip", "{U}", "Enchantment \u2014 Aura", """
            Enchant creature
            Enchanted creature doesn't untap during its controller's untap step.
            Waterbend {5}: Enchanted creature's owner shuffles it into their library. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """.trimIndent(), null)
    val selvaTheMoonSpirit = Cards.invent("Selva, the Moon Spirit", "{3}{U}", "Legendary Creature \u2014 Spirit Ally", """
            Flying, vigilance
            Waterbend {5}, {T}: You may cast a noncreature spell from your hand without paying its mana cost. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """.trimIndent(), "3/3")
    val mistyBogVisions = Cards.invent("Misty Bog Visions", "{1}{B}{B}", "Sorcery", """
            As an additional cost to cast this spell, waterbend {X}. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
            Exile X target creature cards from graveyards. For each creature card exiled this way, create a token that's a copy of it. At the beginning of your next end step, sacrifice those tokens.
        """.trimIndent(), null)
    val ruinousTideshaping = Cards.invent("Ruinous Tideshaping", "{1}{B}{B}", "Sorcery \u2014 Lesson", """
            As an additional cost to cast this spell, you may waterbend {4}. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
            All creatures get -2/-2 until end of turn. If this spell's additional cost was paid, whenever a creature dies this turn, you gain 1 life.
        """.trimIndent(), null)
    val mistyBogVineshaper = Cards.invent("Misty Bog Vineshaper", "{3}{G}", "Creature \u2014 Human Plant Ally", """
            This creature can't be blocked by creatures with power 2 or less.
            Waterbend {5}: Put a +1/+1 counter on this creature. Activate only during your turn. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """.trimIndent(), "4/3")
    val griselTheVeinshaper = Cards.invent("Grisel, the Veinshaper", "{2}{U/B}{U/B}{U/B}", "Legendary Creature \u2014 Human Warlock", "When Grisel enters, target opponent mills three cards. Exile up to one noncreature, nonland card from that player's graveyard. For as long as you control Grisel, you may cast the exiled card during your turn by waterbending {X} rather than paying its mana cost, where X is its mana value. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)", "3/3")
    val nerysTideclansHope = Cards.invent("Nerys, Tideclan's Hope", "{2}{W}{U}{U}", "Legendary Creature \u2014 Human Warrior Ally", """
            Vigilance
            When Nerys enters, create a 1/1 white Ally creature token.
            Waterbend {X}: Creatures you control have base power and toughness X/X until end of turn. X can't be 0. Activate only during your turn. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """.trimIndent(), "3/3")
    val heraldKiro = Cards.inventFaces("Transform",
        face("Herald Kiro", "{R}{G}{W}{U}", "Legendary Creature \u2014 Human Avatar Ally", """
            Flying, firebending 2
            Whenever you waterbend, earthbend, firebend, or airbend, draw a card. Then if you've done all four this turn, transform Herald Kiro.
        """.trimIndent(), "4/4", null, null),
        face("Kiro, Master of the Four", null, "Legendary Creature \u2014 Avatar Ally", """
            Flying
            Spells you cast cost {W}{U}{B}{R}{G} less to cast. (This can reduce generic costs.)
            At the beginning of each upkeep, you may transform Kiro, Master of the Four. If you do, you gain 4 life, draw four cards, put four +1/+1 counters on him, and he deals 4 damage to each opponent.
        """.trimIndent(), "6/6", null, null),
    )
    // chunk 06 (Firebending)
    val vexaOnTheHunt = Cards.invent("Vexa, On the Hunt", "{3}{B}", "Legendary Creature \u2014 Human Noble", """
            Firebending 2 (Whenever this creature attacks, add {R}{R}. This mana lasts until end of combat.)
            Whenever Vexa attacks, you lose 1 life and create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """.trimIndent(), "4/3")
    val scaldingPitRioter = Cards.invent("Scalding Pit Rioter", "{2}{B}", "Creature \u2014 Human Rogue Ally", """
            Firebending 1 (Whenever this creature attacks, add {R}. This mana lasts until end of combat.)
            Tap an untapped Ally you control: Exile target card from a graveyard.
            Whenever this creature attacks, you may cast an Ally spell from among cards you own exiled with this creature.
        """.trimIndent(), "3/3")
    val permafrostTank = Cards.invent("Permafrost Tank", "{2}{B}", "Artifact \u2014 Vehicle", """
            Firebending 1 (Whenever this creature attacks, add {R}. This mana lasts until end of combat.)
            When this Vehicle enters, target creature you control gains indestructible until end of turn.
            Crew 1 (Tap any number of creatures you control with total power 1 or more: This Vehicle becomes an artifact creature until end of turn.)
        """.trimIndent(), "4/4")
    val theEmberLegionStrikes = Cards.invent("The Ember Legion Strikes", "{4}{R}", "Instant", """
            Create two 2/2 red Soldier creature tokens with firebending 1. (Whenever a creature with firebending 1 attacks, add {R}. This mana lasts until end of combat.)
            Flashback {8}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """.trimIndent(), null)
    val emberLegionCadets = Cards.invent("Ember Legion Cadets", "{R}", "Creature \u2014 Human Soldier", """
            This creature has firebending 2 as long as there's a Lesson card in your graveyard. (Whenever this creature attacks, add {R}{R}. This mana lasts until end of combat.)
            {2}: This creature gets +1/+0 until end of turn.
        """.trimIndent(), "1/2")
    val flameElders = Cards.invent("Flame Elders", "{1}{R}", "Creature \u2014 Human Cleric", """
            Firebending 1 (Whenever this creature attacks, add {R}. This mana lasts until end of combat.)
            {1}{R}{R}: Put a +1/+1 counter on this creature.
        """.trimIndent(), "2/2")
    val flamecallerAscension = Cards.invent("Flamecaller Ascension", "{1}{R}", "Enchantment", """
            When this enchantment enters, create a 2/2 red Soldier creature token with firebending 1.
            Whenever a creature you control attacking causes a triggered ability of that creature to trigger, put a quest counter on this enchantment. Then if it has four or more quest counters on it, you may copy that ability. You may choose new targets for the copy.
        """.trimIndent(), null)
    val flamecallingStudent = Cards.invent("Flamecalling Student", "{1}{R}", "Creature \u2014 Human Monk", """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Firebending X, where X is this creature's power. (Whenever this creature attacks, add X {R}. This mana lasts until end of combat.)
        """.trimIndent(), "1/2")
    val corvaneTheDeserter = Cards.invent("Corvane, the Deserter", "{2}{R}", "Legendary Creature \u2014 Human Rebel Ally", """
            Firebending 1 (Whenever this creature attacks, add {R}. This mana lasts until end of combat.)
            Exhaust — {3}: Put a +1/+1 counter on Corvane. When you next cast a Lesson spell this turn, copy it and you may choose new targets for the copy. (Activate each exhaust ability only once.)
        """.trimIndent(), "2/3")
    val theTaleOfArdan = Cards.inventFaces("Transform",
        face("The Tale of Ardan", "{2}{R}{R}", "Enchantment \u2014 Saga", """
            (As this Saga enters and after your draw step, add a lore counter.)
            I — Exile the top three cards of your library. Until the end of your next turn, you may play those cards.
            II — Add one mana of any color.
            III — Exile this Saga, then return it to the battlefield transformed under your control.
        """.trimIndent(), null, null, null),
        face("Herald Ardan", null, "Legendary Creature \u2014 Avatar", """
            Firebending 4 (Whenever this creature attacks, add {R}{R}{R}{R}. This mana lasts until end of combat.)
            {8}: Create a 4/4 red Dragon creature token with flying and firebending 4.
        """.trimIndent(), "4/4", null, null),
    )
    val velAndSorr = Cards.invent("Vel and Sorr", "{3}{R}{R}", "Legendary Creature \u2014 Dragon", """
            Flying, firebending 2
            When Vel and Sorr enter, if you cast them and there are three or more Dragon and/or Lesson cards in your graveyard, create a token that's a copy of Vel and Sorr, except it's not legendary.
            {3}{R}: Dragons you control get +2/+0 until end of turn.
        """.trimIndent(), "4/4")
    val ruggedRhinoLancers = Cards.invent("Rugged Rhino Lancers", "{4}{R}", "Creature \u2014 Human Mercenary", """
            Firebending 2 (Whenever this creature attacks, add {R}{R}. This mana lasts until end of combat.)
            Exhaust — {8}: Put two +1/+1 counters on this creature. It gains trample until end of turn. (Activate each exhaust ability only once.)
        """.trimIndent(), "5/5")
    val vardansComet = Cards.invent("Vardan's Comet", "{3}{R}{R}", "Sorcery", """
            Each creature you control gains firebending 5 until end of turn. (Whenever it attacks, add {R}{R}{R}{R}{R}. This mana lasts until end of combat.)
            Foretell {2}{R} (During your turn, you may pay {2} and exile this card from your hand face down. Cast it on a later turn for its foretell cost.)
        """.trimIndent(), null)
    val darrowExiledPrince = Cards.invent("Darrow, Exiled Prince", "{3}{R}", "Legendary Creature \u2014 Human Noble", """
            Firebending 3 (Whenever this creature attacks, add {R}{R}{R}. This mana lasts until end of combat.)
            {3}: Exile the top card of your library. You may play that card this turn.
        """.trimIndent(), "4/3")
    val emberLegionPalace = Cards.invent("Ember Legion Palace", null, "Land", """
            This land enters tapped unless you control a basic land.
            {T}: Add {R}.
            {1}{R}, {T}: Target creature you control gains firebending 4 until end of turn. (Whenever it attacks, add {R}{R}{R}{R}. This mana lasts until end of combat.)
        """.trimIndent(), null)
    // chunk 07 (FirebendingLegends)
    val theRiseOfVardan = Cards.inventFaces("Transform",
        face("The Rise of Vardan", "{4}{B}{B}", "Enchantment \u2014 Saga", """
            (As this Saga enters and after your draw step, add a lore counter.)
            I — Destroy all creatures.
            II — Choose a card name. Search target opponent's graveyard, hand, and library for up to four cards with that name and exile them. Then that player shuffles.
            III — Exile this Saga, then return it to the battlefield transformed under your control.
        """.trimIndent(), null, null, null),
        face("Flame Sovereign Vardan", null, "Legendary Creature \u2014 Human Noble", """
            Menace, firebending 3 (Whenever this creature attacks, add {R}{R}{R}. This mana lasts until end of combat.)
            Whenever Flame Sovereign Vardan deals combat damage to a player, you may pay {X}. When you do, put any number of target creature cards with total mana value X or less from that player's graveyard onto the battlefield under your control.
        """.trimIndent(), "5/5", null, "B"),
    )
    val theFinalFlameDuel = Cards.invent("The Final Flame Duel", "{1}{R}", "Instant", """
            Target creature you control fights target creature an opponent controls. If the creature the opponent controls is dealt excess damage this way, add that much {R}.
            Until end of turn, you don't lose unspent red mana as steps and phases end.
        """.trimIndent(), null)
    val vexaCunningUsurper = Cards.invent("Vexa, Cunning Usurper", "{2}{U}{B}{B}", "Legendary Creature \u2014 Human Noble Rogue", """
            Firebending 2 (Whenever this creature attacks, add {R}{R}. This mana lasts until end of combat.)
            When Vexa enters, target opponent exiles a nontoken creature they control, then they exile a nonland card from their graveyard.
            During your turn, you may cast cards exiled with Vexa and you may cast them as though they had flash. Mana of any type can be spent to cast those spells.
        """.trimIndent(), "4/4")
    val ruthlessOverseer = Cards.invent("Ruthless Overseer", "{3}{B}{R}", "Creature \u2014 Human Soldier", """
            Raid — This creature enters with a +1/+1 counter on it if you attacked this turn.
            Whenever this creature attacks, create a 2/2 red Soldier creature token with firebending 1. (Whenever it attacks, add {R}. This mana lasts until end of combat.)
        """.trimIndent(), "5/4")
    val flameSovereignVexa = Cards.invent("Flame Sovereign Vexa", "{1}{U}{B}{R}", "Legendary Creature \u2014 Human Noble", """
            Firebending 2 (Whenever this creature attacks, add {R}{R}. This mana lasts until end of combat.)
            Whenever you cast a spell while Flame Sovereign Vexa is attacking, copy that spell. You may choose new targets for the copy. (A copy of a permanent spell becomes a token.)
        """.trimIndent(), "4/4")
    val flameSovereignDarrow = Cards.invent("Flame Sovereign Darrow", "{R}{W}{B}", "Legendary Creature \u2014 Human Noble Ally", """
            Firebending X, where X is Flame Sovereign Darrow's power. (Whenever this creature attacks, add X {R}. This mana lasts until end of combat.)
            Whenever you cast a spell from exile and whenever a permanent you control enters from exile, put a +1/+1 counter on each creature you control.
        """.trimIndent(), "2/4")
    val baldricGrandBlossom = Cards.invent("Baldric, Grand Blossom", "{3}{G}{U}{R}", "Legendary Creature \u2014 Human Noble Ally", """
            Firebending 2
            During your turn, each non-Lesson instant and sorcery card in your graveyard has flashback. The flashback cost is equal to that card's mana cost. (You may cast a card from your graveyard for its flashback cost. Then exile it.)
            During your turn, each Lesson card in your graveyard has flashback {1}.
        """.trimIndent(), "5/5")
    val malgorTheCinderbirdKing = Cards.invent("Malgor, the Cinderbird King", "{2}{B}{B}{R}{R}", "Legendary Creature \u2014 Human Noble", """
            Trample, firebending 4, haste
            If you would lose unspent mana, that mana becomes red instead.
            Malgor has flying and indestructible as long as you have six or more unspent mana.
        """.trimIndent(), "7/7")
    val solarGuardians = Cards.invent("Solar Guardians", "{2}{R}{W}", "Creature \u2014 Human Warrior Ally", """
            Firebending X, where X is the number of creatures you control. (Whenever this creature attacks, add X {R}. This mana lasts until end of combat.)
            {5}: Create a 1/1 white Ally creature token.
        """.trimIndent(), "3/5")
    val elderBaldric = Cards.invent("Elder Baldric", "{1}{R/G}{R/G}", "Legendary Creature \u2014 Human Noble Ally", """
            Firebending 1 (Whenever this creature attacks, add {R}. This mana lasts until end of combat.)
            Lesson spells you cast cost {1} less to cast.
        """.trimIndent(), "4/2")
    val spitefulWarden = Cards.invent("Spiteful Warden", "{2}{B/R}", "Creature \u2014 Human Soldier", """
            Menace (This creature can't be blocked except by two or more creatures.)
            Firebending 1 (Whenever this creature attacks, add {R}. This mana lasts until end of combat.)
            {3}: This creature deals 1 damage to each opponent.
        """.trimIndent(), "2/3")
    val krellRuthlessAdmiral = Cards.invent("Krell, Ruthless Admiral", "{2}{B/R}{B/R}", "Legendary Creature \u2014 Human Soldier", """
            Firebending 2 (Whenever this creature attacks, add {R}{R}. This mana lasts until end of combat.)
            Whenever you sacrifice another permanent, creatures you control get +1/+0 until end of turn.
        """.trimIndent(), "3/4")
    val krellTheMoonSlayer = Cards.invent("Krell, the Moon Slayer", "{1}{R}", "Legendary Creature \u2014 Human Soldier", """
            Menace
            Nonbasic lands enter tapped.
            {7}: Put a conqueror counter on Krell.
            As long as Krell has a conqueror counter on him, nonbasic lands are Mountains. (They lose all other land types and abilities and have "{T}: Add {R}.")
        """.trimIndent(), "2/2")
    // chunk 08 (LessonsWhiteBlueBlack)
    val wayfarersPilgrimage = Cards.invent("Wayfarer's Pilgrimage", "{2}", "Sorcery \u2014 Lesson", """
            Kicker {2} (You may pay an additional {2} as you cast this spell.)
            Search your library for a basic land card. If this spell was kicked, instead search your library for a basic land card and a Shrine card. Reveal those cards, put them into your hand, then shuffle.
            You gain 2 life.
        """.trimIndent(), null)
    val vitalweaving = Cards.invent("Vitalweaving", "{2}", "Instant \u2014 Lesson", """
            Lands you control gain all basic land types until end of turn.
            Draw a card.
        """.trimIndent(), null)
    val banishedHeirsExile = Cards.invent("Banished Heir's Exile", "{5}", "Instant \u2014 Lesson", """
            Exile target artifact, creature, or enchantment. Its controller creates a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """.trimIndent(), null)
    val enterTheHeraldsTrance = Cards.invent("Enter the Herald's Trance", "{W}", "Instant \u2014 Lesson", "Until end of turn, target creature you control becomes an Avatar in addition to its other types and gains flying, first strike, lifelink, and hexproof. (A creature with hexproof can't be the target of spells or abilities your opponents control.)", null)
    val nimbleSteps = Cards.invent("Nimble Steps", "{2}{W}", "Instant \u2014 Lesson", "Untap one or two target creatures. They each get +2/+2 until end of turn.", null)
    val hupHup = Cards.invent("Hup Hup!", "{W}", "Instant \u2014 Lesson", "Target creature you control gets +2/+2 until end of turn. If that creature is an Ally, it also gains flying until end of turn.", null)
    val gatherInsight = Cards.invent("Gather Insight", "{1}{U}", "Instant \u2014 Lesson", "Look at the top three cards of your library. Put one of those cards into your hand and the rest on the bottom of your library in any order. Put each of those cards into your hand instead if there are three or more Lesson cards in your graveyard.", null)
    val returningBladeBasics = Cards.invent("Returning Blade Basics", "{U}", "Sorcery \u2014 Lesson", "Return target nonland permanent to its owner's hand. If you controlled that permanent, draw a card.", null)
    val thatllDouseYa = Cards.invent("That'll Douse Ya!", "{1}{U}", "Instant \u2014 Lesson", "Counter target spell unless its controller pays {2}.", null)
    val vanishedHours = Cards.invent("Vanished Hours", "{4}{U}", "Instant \u2014 Lesson", """
            The owner of target creature or enchantment puts it into their library second from the top or on the bottom. You create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """.trimIndent(), null)
    val squidStance = Cards.invent("Squid Stance", "{U}", "Instant \u2014 Lesson", "Target creature you control gets +1/+1 and gains hexproof until end of turn. Untap it. (It can't be the target of spells or abilities your opponents control.)", null)
    val wisecrackersVerse = Cards.invent("Wisecracker's Verse", "{3}{U}{U}", "Instant \u2014 Lesson", """
            Counter target spell.
            Draw a card, then mill three cards.
            Untap target land.
        """.trimIndent(), null)
    val thePrincessAlwaysLies = Cards.invent("The Princess Always Lies", "{1}{B}", "Instant \u2014 Lesson", """
            Choose one or both —
            • Target creature gets -1/-1 until end of turn.
            • Put a +1/+1 counter on target creature.
        """.trimIndent(), null)
    val tyrantsCruelty = Cards.invent("Tyrant's Cruelty", "{2}{B}", "Sorcery \u2014 Lesson", "Tyrant's Cruelty deals 2 damage to target player. That player discards two cards.", null)
    // chunk 09 (LessonsRedGreen)
    val detonationTechnique = Cards.invent("Detonation Technique", "{1}{R}", "Instant \u2014 Lesson", "Detonation Technique deals damage equal to 2 plus the number of Lesson cards in your graveyard to target creature. If that creature would die this turn, exile it instead.", null)
    val flamecallingLesson = Cards.invent("Flamecalling Lesson", "{R}", "Instant \u2014 Lesson", """
            Kicker {4} (You may pay an additional {4} as you cast this spell.)
            Flamecalling Lesson deals 2 damage to target creature. If this spell was kicked, it deals 5 damage to that creature instead.
        """.trimIndent(), null)
    val howToSparkAnUprising = Cards.invent("How to Spark an Uprising", "{2}{R}", "Instant \u2014 Lesson", """
            Target creature gains menace until end of turn. (It can't be blocked except by two or more creatures.)
            Creatures target player controls get +2/+0 until end of turn.
        """.trimIndent(), null)
    val theOldGeneralsDemonstration = Cards.invent("The Old General's Demonstration", "{1}{R}", "Sorcery \u2014 Lesson", """
            Choose one —
            • The Old General's Demonstration deals 1 damage to each creature your opponents control.
            • The Old General's Demonstration deals 4 damage to target creature.
        """.trimIndent(), null)
    val costOfLiberty = Cards.invent("Cost of Liberty", "{1}{R}", "Sorcery \u2014 Lesson", """
            Destroy target artifact or land an opponent controls. Its controller may search their library for a basic land card, put it onto the battlefield tapped, then shuffle.
            Draw a card.
        """.trimIndent(), null)
    val rerouteThunderbolt = Cards.invent("Reroute Thunderbolt", "{R}", "Instant \u2014 Lesson", """
            As an additional cost to cast this spell, pay 5 life or pay {2}.
            Change the target of target spell or ability with a single target.
        """.trimIndent(), null)
    val equinoxRevelations = Cards.invent("Equinox Revelations", "{2}{R}", "Instant \u2014 Lesson", """
            Exile cards from the top of your library until you exile a nonland card. You may cast that card without paying its mana cost if the spell's mana value is less than the number of Mountains you control. If you don't cast that card this way, put it into your hand.
            Flashback {6}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """.trimIndent(), null)
    val roundOfRenewal = Cards.invent("Round of Renewal", "{2}{G}", "Instant \u2014 Lesson", "Sacrifice a land. Search your library for up to two basic land cards, put them onto the battlefield tapped, then shuffle.", null)
    val primalTeachings = Cards.invent("Primal Teachings", "{4}{G}", "Instant \u2014 Lesson", "Search your library for up to four land cards with different names and reveal them. An opponent chooses two of those cards. Put the chosen cards into your graveyard and the rest onto the battlefield tapped, then shuffle.", null)
    val dawnOfIronshaping = Cards.invent("Dawn of Ironshaping", "{1}{G}", "Instant \u2014 Lesson", """
            Choose one —
            • Destroy target artifact or enchantment.
            • Put a +1/+1 counter on target creature you control. It gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
        """.trimIndent(), null)
    val tremorSight = Cards.invent("Tremor Sight", "{G}", "Sorcery \u2014 Lesson", "Look at the top X cards of your library, where X is the number of lands you control. You may reveal a creature or land card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.", null)
    val commonRoots = Cards.invent("Common Roots", "{1}{G}", "Sorcery \u2014 Lesson", "Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.", null)
    val genuineLineage = Cards.invent("Genuine Lineage", "{1}{G}", "Sorcery \u2014 Lesson", """
            Return up to one target permanent card from your graveyard to your hand.
            Create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """.trimIndent(), null)
    val releaseAttachments = Cards.invent("Release Attachments", "{1}{U/R}", "Instant \u2014 Lesson", "You may discard a card. If you do, draw two cards.", null)
    // chunk 10 (LessonMatters)
    val fledglingGlider = Cards.invent("Fledgling Glider", "{1}{U}", "Creature \u2014 Human Pilot Ally", """
            Flying
            This creature gets +1/+1 as long as there's a Lesson card in your graveyard.
        """.trimIndent(), "1/2")
    val nanaUlla = Cards.invent("Nana-Ulla", "{U}", "Legendary Creature \u2014 Human Peasant Ally", """
            Whenever Nana-Ulla becomes tapped, draw a card, then discard a card.
            Noncreature spells you cast cost {1} less to cast as long as there are three or more Lesson cards in your graveyard.
        """.trimIndent(), "1/2")
    val tidemasterOskar = Cards.invent("Tidemaster Oskar", "{1}{U}", "Legendary Creature \u2014 Human Advisor Ally", """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Whenever Tidemaster Oskar becomes tapped, target player mills X cards, where X is the number of Lesson cards in your graveyard. (They put the top X cards of their library into their graveyard.)
        """.trimIndent(), "1/3")
    val wyrmOfTheStrait = Cards.invent("Wyrm of the Strait", "{5}{U}{U}", "Creature \u2014 Serpent", """
            If there are three or more Lesson cards in your graveyard, you may cast this spell as though it had flash.
            This spell costs {1} less to cast for each noncreature, nonland card in your graveyard.
        """.trimIndent(), "6/5")
    val renAndReiTwinMentors = Cards.invent("Ren and Rei, Twin Mentors", "{4}{B}", "Legendary Creature \u2014 Human Advisor", """
            When Ren and Rei enter, search your library for a Lesson or Noble card, reveal it, put it into your hand, then shuffle.
            Noble creatures you control and Lesson spells you control have lifelink.
        """.trimIndent(), "2/2")
    val petalsFromTheBough = Cards.invent("Petals from the Bough", "{1}{G}", "Enchantment \u2014 Saga", """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Mill three cards, then create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            II — Put a +1/+1 counter on each of up to two target creatures you control.
            III — Draw a card if there's a creature or Lesson card in your graveyard.
        """.trimIndent(), null)
    val trainingScarecrow = Cards.invent("Training Scarecrow", "{1}{G}", "Artifact Creature \u2014 Scarecrow", """
            Defender
            {T}: Mill a card. You may put a land card milled this way into your hand. You gain 2 life if a Lesson card is milled this way. (To mill a card, put the top card of your library into your graveyard.)
        """.trimIndent(), "1/3")
    val rampartSentries = Cards.invent("Rampart Sentries", "{2}{G}", "Creature \u2014 Human Soldier Ally", """
            Reach, deathtouch
            When this creature dies, if there's a Lesson card in your graveyard, you gain 2 life.
        """.trimIndent(), "2/3")
    val damselflyCloud = Cards.invent("Damselfly Cloud", "{1}{U}{R}", "Creature \u2014 Dragon Insect", """
            Flying, ward {1} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {1}.)
            This creature's power is equal to the number of noncreature, nonland cards in your graveyard.
            When this creature dies, if there's a Lesson card in your graveyard, draw a card.
        """.trimIndent(), "*/3")
    val sageOmmar = Cards.invent("Sage Ommar", "{2}{G/U}{G/U}", "Legendary Creature \u2014 Human Monk Ally", """
            When Sage Ommar enters, look at the top five cards of your library. You may reveal a Lesson, Saga, or Shrine card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
            Whenever you cast a Lesson, Saga, or Shrine spell, put a +1/+1 counter on another target creature you control.
        """.trimIndent(), "2/4")
    val reclusiveHerbwife = Cards.invent("Reclusive Herbwife", "{G}{U}", "Creature \u2014 Human Druid Ally", """
            {T}: Add one mana of any color.
            {T}: Add two mana in any combination of colors. Spend this mana only to cast Lesson spells.
        """.trimIndent(), "2/3")
    val theElderShellbeast = Cards.invent("The Elder Shellbeast", "{1}{G}{U}", "Legendary Creature \u2014 Elder Cat Turtle", """
            Reach, vigilance
            When The Elder Shellbeast enters, you gain 3 life.
            The Elder Shellbeast can't attack or block unless there are three or more Lesson cards in your graveyard.
            {T}: Add one mana of any color.
        """.trimIndent(), "3/6")
    val beaverBear = Cards.invent("Beaver-Bear", "{1}{G/U}", "Creature \u2014 Platypus Bear", """
            Defender
            When this creature enters, mill two cards. (Put the top two cards of your library into your graveyard.)
            As long as there is a Lesson card in your graveyard, this creature can attack as though it didn't have defender.
        """.trimIndent(), "2/3")
    val paleBlossomRefuge = Cards.invent("Pale Blossom Refuge", null, "Land", """
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast a Lesson or Shrine spell.
            {1}, {T}: Add one mana of any color.
        """.trimIndent(), null)
    val swordwrightHalloran = Cards.invent("Swordwright Halloran", "{4}{W}", "Legendary Creature \u2014 Human Warrior Ally", """
            First strike
            Whenever Swordwright Halloran attacks, look at the top four cards of your library. You may reveal an Ally, Equipment, or Lesson card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        """.trimIndent(), "4/4")
    // chunk 11 (Allies)
    val heraldEnthusiasts = Cards.invent("Herald Enthusiasts", "{2}{W}", "Creature \u2014 Human Peasant Ally", "Whenever another Ally you control enters, put a +1/+1 counter on this creature.", "2/2")
    val stoneRealmProtectors = Cards.invent("Stone Realm Protectors", "{W}", "Creature \u2014 Human Soldier Ally", """
            Vigilance
            Sacrifice this creature: Another target Ally you control gains indestructible until end of turn. (Damage and effects that say "destroy" don't destroy it.)
        """.trimIndent(), "1/1")
    val gatherThePaleBlossom = Cards.invent("Gather the Pale Blossom", "{4}{W}", "Sorcery", "Create a 1/1 white Ally creature token for each Plains you control. Scry 2. (Look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)", null)
    val torvaldSelflessCommander = Cards.invent("Torvald, Selfless Commander", "{3}{W}", "Legendary Creature \u2014 Human Warrior Ally", """
            Vigilance
            You may look at the top card of your library any time.
            You may cast Ally spells from the top of your library.
            Sacrifice Torvald: Creatures you control get +0/+5 and gain indestructible until end of turn.
        """.trimIndent(), "3/5")
    val siegeReinforcements = Cards.invent("Siege Reinforcements", "{1}{W}", "Creature \u2014 Human Warrior Ally", """
            Flash
            When this creature enters, create a 1/1 white Ally creature token.
        """.trimIndent(), "1/1")
    val fanbladeWarriors = Cards.invent("Fanblade Warriors", "{3}{W}", "Creature \u2014 Human Warrior Ally", "When this creature enters, create a 1/1 white Ally creature token.", "3/3")
    val southernIceVoyager = Cards.invent("Southern Ice Voyager", "{1}{W}", "Creature \u2014 Human Scout Ally", "Whenever this creature or another Ally you control enters, you gain 1 life. If this is the second time this ability has resolved this turn, draw a card.", "2/2")
    val yarraCourageousRescuer = Cards.invent("Yarra, Courageous Rescuer", "{1}{W}{W}", "Legendary Creature \u2014 Human Warrior Ally", """
            Other creatures you control get +1/+0.
            Whenever another permanent you control leaves the battlefield during your turn, create a 1/1 white Ally creature token. This ability triggers only once each turn.
        """.trimIndent(), "2/4")
    val alliedVanguard = Cards.invent("Allied Vanguard", "{X}{W}{W}", "Sorcery", "Create X 1/1 white Ally creature tokens, then put a +1/+1 counter on each creature you control.", null)
    val tideclanCaptain = Cards.invent("Tideclan Captain", "{2}{W}", "Creature \u2014 Human Soldier Ally", "{5}: Creatures you control get +1/+1 until end of turn.", "3/3")
    val canopyLiberators = Cards.invent("Canopy Liberators", "{2}{R}", "Creature \u2014 Human Rebel Ally", """
            Haste
            When this creature enters, create a 1/1 white Ally creature token.
        """.trimIndent(), "2/1")
    val wartimeDissenters = Cards.invent("Wartime Dissenters", "{3}{R}", "Creature \u2014 Human Rebel Ally", """
            Haste
            Whenever another Ally you control enters, put a +1/+1 counter on that creature and it gains haste until end of turn.
        """.trimIndent(), "4/4")
    val comradesAtLast = Cards.invent("Comrades at Last", "{2}{G}", "Instant", """
            Affinity for Allies (This spell costs {1} less to cast for each Ally you control.)
            Up to two target creatures you control each deal damage equal to their power to target creature an opponent controls.
        """.trimIndent(), null)
    val grandChasmGuide = Cards.invent("Grand Chasm Guide", "{1}{G}", "Creature \u2014 Human Scout Ally", """
            Each land and Ally you control has "{T}: Add one mana of any color."
        """.trimIndent(), "2/3")
    val siegeTactics = Cards.invent("Siege Tactics", "{4}{G}", "Enchantment", """
            When this enchantment enters, creatures you control get +2/+2 until end of turn.
            Whenever one or more Allies you control deal combat damage to a player, draw a card.
        """.trimIndent(), null)
    val stoneSovereignsLieutenant = Cards.invent("Stone Sovereign's Lieutenant", "{G}{W}", "Creature \u2014 Human Soldier Ally", """
            Trample
            When this creature enters, put a +1/+1 counter on each other Ally creature you control.
            Whenever another Ally you control enters, put a +1/+1 counter on this creature.
        """.trimIndent(), "1/1")
    val nerysTheFearless = Cards.invent("Nerys, the Fearless", "{G}{W}{U}", "Legendary Creature \u2014 Human Warrior Ally", "If a triggered ability of an Ally you control triggers, that ability triggers an additional time.", "3/3")
    val tammTenaciousTactician = Cards.invent("Tamm, Tenacious Tactician", "{1}{U}{R}{W}", "Legendary Creature \u2014 Human Warrior Ally", """
            Menace, prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Other Allies you control have menace and prowess.
            Whenever you cast a noncreature spell, create a 1/1 white Ally creature token.
        """.trimIndent(), "3/3")
    val paleBlossomReinforcements = Cards.invent("Pale Blossom Reinforcements", "{1}{G}{W}", "Creature \u2014 Human Soldier Ally", """
            Vigilance
            Other Allies you control get +1/+1.
        """.trimIndent(), "2/3")
    val baldricBrewMaster = Cards.invent("Baldric, Brew Master", "{1}{R}{W}", "Legendary Creature \u2014 Human Citizen Ally", """
            When Baldric enters, create a Food token.
            At the beginning of combat on your turn, you may have target opponent gain control of target permanent you control. When you do, create a 1/1 white Ally creature token. Put a +1/+1 counter on that token for each permanent you own that your opponents control.
        """.trimIndent(), "2/2")
    val fanbladeWarFan = Cards.invent("Fanblade War Fan", "{2}", "Artifact \u2014 Equipment", """
            When this Equipment enters, create a 1/1 white Ally creature token, then attach this Equipment to it.
            Equipped creature gets +1/+0.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """.trimIndent(), null)
    val goldenWyrmTeahouse = Cards.invent("Golden Wyrm Teahouse", null, "Land", """
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast an Ally spell or activate an ability of an Ally source.
            {5}, {T}: Create a 1/1 white Ally creature token.
        """.trimIndent(), null)
    // chunk 12 (ShrinesAndWhite)
    val southernWindShrine = Cards.invent("Southern Wind Shrine", "{3}{W}", "Legendary Enchantment \u2014 Shrine", """
            When Southern Wind Shrine enters, put X +1/+1 counters on each creature you control, where X is the number of Shrines you control.
            Whenever another Shrine you control enters, put a +1/+1 counter on each creature you control.
        """.trimIndent(), null)
    val theMoonlitOasis = Cards.invent("The Moonlit Oasis", "{2}{U}", "Legendary Enchantment \u2014 Shrine", """
            When The Moonlit Oasis enters, draw a card for each Shrine you control.
            Whenever another Shrine you control enters, draw a card.
        """.trimIndent(), null)
    val northernWindShrine = Cards.invent("Northern Wind Shrine", "{B}", "Legendary Enchantment \u2014 Shrine", """
            When Northern Wind Shrine enters, each opponent loses X life and you gain X life, where X is the number of Shrines you control.
            Whenever another Shrine you control enters, each opponent loses 1 life and you gain 1 life.
        """.trimIndent(), null)
    val crescentIsleShrine = Cards.invent("Crescent Isle Shrine", "{3}{R}", "Legendary Enchantment \u2014 Shrine", """
            When Crescent Isle Shrine enters, for each Shrine you control, create a 1/1 red Monk creature token with prowess. (Whenever you cast a noncreature spell, it gets +1/+1 until end of turn.)
            Whenever another Shrine you control enters, create a 1/1 red Monk creature token with prowess.
        """.trimIndent(), null)
    val reedIslePlaza = Cards.invent("Reed Isle Plaza", "{3}{G}", "Legendary Enchantment \u2014 Shrine", """
            When Reed Isle Plaza enters, search your library for up to X basic land cards, where X is the number of Shrines you control. Put those cards onto the battlefield tapped, then shuffle.
            Whenever another Shrine you control enters, search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
        """.trimIndent(), null)
    val gentleMender = Cards.invent("Gentle Mender", "{1}{W}", "Creature \u2014 Human Cleric Ally", "Whenever this creature becomes tapped, you gain 1 life and scry 1. (Look at the top card of your library. You may put it on the bottom.)", "2/2")
    val inquisitiveBarnyard = Cards.invent("Inquisitive Barnyard", "{W}", "Creature \u2014 Boar Elk Bird Ox", """
            When this creature dies, you gain 3 life.
            {2}, Sacrifice this creature: Destroy up to one target artifact or enchantment.
        """.trimIndent(), "1/1")
    val fatedShowdown = Cards.invent("Fated Showdown", "{2}{W}{W}", "Sorcery", "Each player chooses any number of creatures they control with total power 4 or less, then sacrifices all other creatures they control.", null)
    val stoneRealmJailer = Cards.invent("Stone Realm Jailer", "{2}{W}", "Creature \u2014 Human Soldier Ally", "When this creature enters, exile up to one target artifact, creature, or enchantment an opponent controls with mana value 3 or greater until this creature leaves the battlefield.", "3/3")
    val kiteYoungsters = Cards.invent("Kite Youngsters", "{2}{W}", "Creature \u2014 Human Pilot Ally", """
            Flying
            When this creature enters, scry 1. (Look at the top card of your library. You may put it on the bottom.)
        """.trimIndent(), "2/3")
    val oldMastersDeserters = Cards.invent("Old Master's Deserters", "{1}{W}", "Creature \u2014 Human Rebel Ally", "When this creature enters, put a +1/+1 counter on target creature.", "1/2")
    val pipkinFriendlyFlier = Cards.invent("Pipkin, Friendly Flier", "{W}", "Legendary Creature \u2014 Lemur Bat Ally", """
            Flying
            The first non-Lemur creature spell with flying you cast during each of your turns costs {1} less to cast.
            Whenever another creature you control with flying enters, Pipkin gets +1/+1 until end of turn.
        """.trimIndent(), "1/1")
    val pipkinPlayfulPet = Cards.invent("Pipkin, Playful Pet", "{W}", "Legendary Creature \u2014 Lemur Bat Ally", """
            Flying, vigilance
            When Pipkin leaves the battlefield, choose one —
            • Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            • Put a +1/+1 counter on target creature you control.
            • Scry 2.
        """.trimIndent(), "1/1")
    val roadToAtonement = Cards.invent("Road to Atonement", "{1}{W}", "Enchantment \u2014 Aura", """
            Enchant creature
            Enchanted creature can't attack or block.
            {5}, Sacrifice this Aura: Exile enchanted creature. Create a 1/1 white Ally creature token. Activate only during your turn.
        """.trimIndent(), null)
    val hopperooTroop = Cards.invent("Hopperoo Troop", "{3}{W}{W}", "Creature \u2014 Rabbit Kangaroo", """
            Landfall — Whenever a land you control enters, this creature gains flying until end of turn and you gain 1 life.
            Plainscycling {2} ({2}, Discard this card: Search your library for a Plains card, reveal it, put it into your hand, then shuffle.)
        """.trimIndent(), "3/5")
    val edgedHoops = Cards.invent("Edged Hoops", "{1}{W}", "Instant", "Edged Hoops deals 4 damage to target attacking or blocking creature. You gain life equal to the excess damage dealt this way.", null)
    val heraldsBand = Cards.invent("Herald's Band", "{2}{W}", "Enchantment", """
            Whenever a creature you control attacks alone, it gets +X/+X until end of turn, where X is the number of creatures you control.
            {2}{W}, Discard this card: It deals damage equal to the number of creatures you control to target creature.
        """.trimIndent(), null)
    val wrathfulTownsfolk = Cards.invent("Wrathful Townsfolk", "{3}{W}", "Creature \u2014 Human Citizen", "Whenever this creature attacks, choose target creature an opponent controls. Tap it, then you may sacrifice an artifact or creature. If you do, put a stun counter on the chosen creature. (If a permanent with a stun counter would become untapped, remove one from it instead.)", "3/3")
    // chunk 13 (Blue)
    val cinderIslePageant = Cards.invent("Cinder Isle Pageant", "{3}{U}{U}", "Sorcery", """
            Choose one —
            • Create a token that's a copy of target creature you control, except it's not legendary and it's a 4/4 Hero in addition to its other types.
            • Create a token that's a copy of target creature an opponent controls, except it's not legendary and it's a 2/2 Coward in addition to its other types.
        """.trimIndent(), null)
    val omenReadingSeer = Cards.invent("Omen-Reading Seer", "{1}{U}", "Creature \u2014 Human Advisor Ally", """
            When this creature enters, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """.trimIndent(), "1/3")
    val humbleLabor = Cards.invent("Humble Labor", "{U}", "Enchantment \u2014 Aura", """
            Enchant creature an opponent controls
            When this Aura enters, tap enchanted creature and remove all counters from it.
            Enchanted creature loses all abilities and is a Citizen with base power and toughness 1/1 and "{T}: Add {C}" named Humble Merchant. (It loses all other creature types and names.)
        """.trimIndent(), null)
    val geckoMacaw = Cards.invent("Gecko Macaw", "{2}{U}", "Creature \u2014 Lizard Bird Pirate", """
            Flying, vigilance
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        """.trimIndent(), "2/2")
    val loreHunter = Cards.invent("Lore Hunter", "{1}{U}", "Creature \u2014 Fox Spirit", """
            Vigilance
            Whenever you draw your second card each turn, put a +1/+1 counter on this creature.
            When this creature dies, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """.trimIndent(), "2/1")
    val theTinkererSkyArtisan = Cards.invent("The Tinkerer, Sky Artisan", "{2}{U}", "Legendary Creature \u2014 Human Artificer Ally", """
            Whenever you cast a noncreature spell, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            {T}: Until end of turn, target artifact token you control becomes a 3/1 Construct artifact creature with flying.
        """.trimIndent(), "1/3")
    val ferretPuffin = Cards.invent("Ferret-Puffin", "{1}{U}", "Creature \u2014 Otter Bird", "Whenever you draw your second card each turn, this creature gets +1/+2 until end of turn and can't be blocked this turn.", "2/1")
    val rambunctiousSnowpelters = Cards.invent("Rambunctious Snowpelters", "{2}{U}", "Creature \u2014 Human Peasant Ally", "When this creature enters, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)", "2/2")
    val pellSpiritedGlider = Cards.invent("Pell, Spirited Glider", "{3}{U}", "Legendary Creature \u2014 Human Pilot Ally", """
            Flying
            Whenever one or more creatures you control with flying attack, draw a card, then discard a card. When you discard a nonland card this way, put a +1/+1 counter on target creature you control.
        """.trimIndent(), "1/4")
    val lynxWalrus = Cards.invent("Lynx-Walrus", "{U}", "Creature \u2014 Cat Seal", """
            Vigilance
            At the beginning of your upkeep, tap this creature.
            Whenever you draw your second card each turn, untap this creature.
        """.trimIndent(), "3/3")
    val junaNerveStriker = Cards.invent("Juna, Nerve Striker", "{2}{U}", "Legendary Creature \u2014 Human Performer Ally", """
            Flash
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            When Juna enters, tap up to one target creature. It doesn't untap during its controller's untap step for as long as you control Juna.
        """.trimIndent(), "2/1")
    val orvathArchivist = Cards.invent("Orvath, Archivist", "{X}{U}{U}", "Legendary Creature \u2014 Bird Spirit", """
            Flash
            Flying, vigilance
            When Orvath enters, put X +1/+1 counters on him. Then draw half X cards, rounded down.
            Whenever an opponent searches their library, put a +1/+1 counter on Orvath and draw a card.
        """.trimIndent(), "1/1")
    val tideshapingScroll = Cards.invent("Tideshaping Scroll", "{1}{U}", "Artifact", "{6}, {T}: Draw a card. This ability costs {1} less to activate for each Island you control.", null)
    // chunk 14 (Black)
    val mantisMaskedTraders = Cards.invent("Mantis-Masked Traders", "{4}{B}", "Creature \u2014 Human Citizen", "Whenever this creature attacks, you may sacrifice another creature or artifact. If you do, draw a card and put a +1/+1 counter on this creature.", "5/4")
    val vultureHornetSwarm = Cards.invent("Vulture-Hornet Swarm", "{3}{B}", "Creature \u2014 Bird Insect", """
            Flying
            When this creature enters, you may sacrifice an artifact or creature. If you do, draw a card.
            Whenever another creature you control dies, if it had counters on it, put its counters on this creature.
        """.trimIndent(), "2/2")
    val heartlessExaminer = Cards.invent("Heartless Examiner", "{B}", "Creature \u2014 Human Soldier", """
            Menace (This creature can't be blocked except by two or more creatures.)
            When this creature dies, it deals 1 damage to you. Create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """.trimIndent(), "1/1")
    val ravineCreeper = Cards.invent("Ravine Creeper", "{4}{B}{B}", "Creature \u2014 Spider Beast", """
            Deathtouch
            When this creature enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Swampcycling {2} ({2}, Discard this card: Search your library for a Swamp card, reveal it, put it into your hand, then shuffle.)
        """.trimIndent(), "6/6")
    val lynxCaiman = Cards.invent("Lynx-Caiman", "{6}{B}", "Creature \u2014 Fish Crocodile", """
            Lifelink
            When this creature enters, it deals damage equal to the number of Swamps you control to any target.
        """.trimIndent(), "3/2")
    val crookedPalaceClerk = Cards.invent("Crooked Palace Clerk", "{1}{B}", "Creature \u2014 Human Advisor", "When this creature enters, target opponent discards a card.", "1/1")
    val dayOfDarkenedSun = Cards.invent("Day of Darkened Sun", "{X}{B}{B}", "Sorcery", "Each creature with mana value X or less loses all abilities until end of turn. Destroy those creatures.", null)
    val lethalExactness = Cards.invent("Lethal Exactness", "{B}", "Sorcery", """
            As an additional cost to cast this spell, pay {4} or sacrifice an artifact or creature.
            Destroy target creature.
        """.trimIndent(), null)
    val grandCollapse = Cards.invent("Grand Collapse", "{1}{B}", "Sorcery", "Exile target creature with mana value 3 or greater.", null)
    val theEmberLegionDrill = Cards.invent("The Ember Legion Drill", "{2}{B}{B}", "Legendary Artifact \u2014 Vehicle", """
            Trample
            When The Ember Legion Drill enters, you may tap it. When you do, destroy target creature with power 4 or less.
            {1}: Permanents your opponents control lose hexproof and indestructible until end of turn.
            Crew 2
        """.trimIndent(), "6/3")
    val emberLegionEngineer = Cards.invent("Ember Legion Engineer", "{2}{B}", "Creature \u2014 Human Artificer", "Raid \u2014 At the beginning of your end step, if you attacked this turn, put a +1/+1 counter on another target creature or Vehicle you control.", "2/3")
    val emberFleetCatapult = Cards.invent("Ember Fleet Catapult", "{2}{B}", "Artifact Creature \u2014 Wall", """
            Defender, reach
            Whenever you attack, create a 2/1 colorless Construct artifact creature token with flying named Ballistic Boulder that's tapped and attacking. Sacrifice that token at the beginning of the next end step.
        """.trimIndent(), "0/4")
    val mistyBogHunters = Cards.invent("Misty Bog Hunters", "{3}{B}", "Creature \u2014 Human Ranger Ally", "As long as you've drawn two or more cards this turn, this creature has lifelink and menace. (It can't be blocked except by two or more creatures.)", "3/4")
    val coldHeartedDeed = Cards.invent("Cold-Hearted Deed", "{1}{B}", "Instant", """
            Choose one —
            • Destroy target creature with no counters on it.
            • Remove up to three counters from target creature.
        """.trimIndent(), null)
    val boarBaboon = Cards.invent("Boar-Baboon", "{2}{B}", "Creature \u2014 Boar Monkey", """
            At the beginning of combat on your turn, target creature you control with a +1/+1 counter on it gains menace until end of turn. (It can't be blocked except by two or more creatures.)
            Exhaust — {5}: Put two +1/+1 counters on this creature. (Activate each exhaust ability only once.)
        """.trimIndent(), "3/2")
    val smilingClerkOneOfMany = Cards.invent("Smiling Clerk, One of Many", "{1}{B}", "Creature \u2014 Human Advisor", "{B}, {T}: Surveil 1. Create a token that's a copy of this creature, then sacrifice an artifact or creature. Activate only as a sorcery. (To surveil 1, look at the top card of your library. You may put it into your graveyard.)", "2/2")
    val saskaBountyHunter = Cards.invent("Saska, Bounty Hunter", "{1}{B}", "Legendary Creature \u2014 Human Mercenary", """
            Saska can't be blocked as long as you've drawn two or more cards this turn.
            {1}, Sacrifice another creature: Create a Clue token. Activate only during your turn. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """.trimIndent(), "2/2")
    val vyrrTheMaskTaker = Cards.invent("Vyrr, the Mask Taker", "{4}{B}{B}", "Legendary Creature \u2014 Shapeshifter Spirit", """
            When Vyrr enters, exile up to one other target creature.
            Whenever another nontoken creature dies, you may exile it.
            Pay 1 life: Choose a creature card exiled with Vyrr.
            Vyrr has all activated and triggered abilities of the last chosen card.
        """.trimIndent(), "6/6")
    val ilsaScornfulStriker = Cards.invent("Ilsa, Scornful Striker", "{1}{B}", "Legendary Creature \u2014 Human Noble Ally", """
            First strike
            Whenever a player casts a noncreature spell, they lose 2 life.
        """.trimIndent(), "2/2")
    val peddlerOfManyMasks = Cards.invent("Peddler of Many Masks", "{1}{B}", "Creature \u2014 Human Peasant Ally", "{2}{B}: Return this card from your graveyard to your hand.", "2/2")
    // chunk 15 (BlackAndRed)
    val relentlessHunt = Cards.invent("Relentless Hunt", "{1}{B}", "Enchantment", """
            When this enchantment enters and at the beginning of your upkeep, you lose 1 life and create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            Whenever you attack, put X +1/+1 counters on target attacking creature, where X is the number of permanents you've sacrificed this turn. If X is three or more, that creature gains lifelink until end of turn.
        """.trimIndent(), null)
    val cinderbirdFleetAirship = Cards.invent("Cinderbird Fleet Airship", "{2}{B}{B}", "Artifact \u2014 Vehicle", """
            Flying
            At the beginning of your end step, if you sacrificed a permanent this turn, create a token that's a copy of this Vehicle.
            As long as you control eight or more permanents named Cinderbird Fleet Airship, this Vehicle is an artifact creature.
            Crew 1
        """.trimIndent(), "4/4")
    val corsairHawkers = Cards.invent("Corsair Hawkers", "{2}{B}", "Creature \u2014 Human Pirate", """
            Deathtouch
            Whenever you sacrifice another permanent, put a +1/+1 counter on this creature.
        """.trimIndent(), "2/2")
    val crowHawk = Cards.invent("Crow Hawk", "{2}{B}", "Creature \u2014 Bird Assassin", """
            Flying
            Whenever this creature enters or attacks, exile up to one target card from a graveyard. If a creature card is exiled this way, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            Whenever you draw your second card each turn, each opponent loses 1 life and you gain 1 life.
        """.trimIndent(), "2/3")
    val betrayedForCoin = Cards.invent("Betrayed for Coin", "{3}{B}", "Instant", """
            Exile target creature. If it was dealt damage this turn, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """.trimIndent(), null)
    val bogsnareTrap = Cards.invent("Bogsnare Trap", "{2}{B}", "Enchantment \u2014 Aura", """
            This spell costs {1} less to cast if it targets a creature with flying.
            Enchant creature
            Enchanted creature gets -5/-3.
        """.trimIndent(), null)
    val houndbat = Cards.invent("Houndbat", "{2}{B}", "Creature \u2014 Wolf Bat", """
            Flying
            Whenever you draw your second card each turn, you may pay {B}. If you do, return this card from your graveyard to the battlefield with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)
        """.trimIndent(), "2/2")
    val banishedHeirsResolve = Cards.invent("Banished Heir's Resolve", "{B}", "Instant", """
            Kicker {4} (You may pay an additional {4} as you cast this spell.)
            Return target creature card from your graveyard to your hand. If this spell was kicked, instead put that card onto the battlefield tapped.
        """.trimIndent(), null)
    val hogUQuill = Cards.invent("Hog-u-quill", "{2}{R}", "Creature \u2014 Boar Porcupine", "Whenever you cast a noncreature spell, put a +1/+1 counter on this creature.", "2/2")
    val oldKingsBash = Cards.invent("Old King's Bash", "{3}{R}", "Sorcery", """
            Choose one —
            • Old King's Bash deals damage equal to the number of lands you control to target creature.
            • Destroy target land creature or nonbasic land.
        """.trimIndent(), null)
    val blastmindAssassin = Cards.invent("Blastmind Assassin", "{3}{R}{R}", "Legendary Creature \u2014 Human Assassin", "Whenever Blastmind Assassin attacks, destroy target permanent unless its controller has Blastmind Assassin deal damage to them equal to his power.", "4/6")
    val craftyGambit = Cards.invent("Crafty Gambit", "{1}{R}", "Instant", """
            Target creature gets +3/+1 until end of turn.
            Create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """.trimIndent(), null)
    val defectorsPupil = Cards.invent("Defector's Pupil", "{1}{R}", "Creature \u2014 Human Rebel Ally", "{T}: Another target creature you control with power 2 or less can't be blocked this turn.", "2/2")
    val destinedBlaze = Cards.invent("Destined Blaze", "{X}{R}{R}{R}", "Enchantment", """
            Flash
            This enchantment enters with X fire counters on it.
            If a source you control would deal damage to an opponent or a permanent an opponent controls, it deals that much damage plus an amount of damage equal to the number of fire counters on this enchantment instead.
        """.trimIndent(), null)
    val emberLegionRaider = Cards.invent("Ember Legion Raider", "{3}{R}", "Creature \u2014 Human Soldier", """
            Raid — When this creature enters, if you attacked this turn, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """.trimIndent(), "4/2")
    val rebelsMindBending = Cards.invent("Rebel's Mind Bending", "{R}", "Sorcery", """
            Kicker {3} (You may pay an additional {3} as you cast this spell.)
            Target creature can't block this turn. If this spell was kicked, gain control of that creature until end of turn, untap it, and it gains haste until end of turn.
            Create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """.trimIndent(), null)
    val thunderboltJab = Cards.invent("Thunderbolt Jab", "{1}{R}", "Instant", "Thunderbolt Jab deals 3 damage to any target.", null)
    val ilsaJadedEdge = Cards.invent("Ilsa, Jaded Edge", "{1}{R}", "Legendary Creature \u2014 Human Noble", """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Exhaust — {3}: Put a double strike counter on Ilsa. (Activate each exhaust ability only once.)
        """.trimIndent(), "1/3")
    val weaselGecko = Cards.invent("Weasel Gecko", "{4}{R}{R}", "Creature \u2014 Mongoose Lizard", """
            Menace (This creature can't be blocked except by two or more creatures.)
            When this creature enters, it deals 1 damage to any target.
            Mountaincycling {2} ({2}, Discard this card: Search your library for a Mountain card, reveal it, put it into your hand, then shuffle.)
        """.trimIndent(), "5/6")
    // chunk 16 (RedAndGreen)
    val pantherPangolin = Cards.invent("Panther-Pangolin", "{1}{R}", "Creature \u2014 Cat Armadillo", "This creature can't attack or block unless you control another creature with power 4 or greater.", "4/3")
    val pairedSabers = Cards.invent("Paired Sabers", "{2}{R}", "Artifact \u2014 Equipment", """
            Flash
            When this Equipment enters, attach it to target creature you control. That creature gains double strike until end of turn.
            Equipped creature gets +1/+1.
            Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
        """.trimIndent(), null)
    val junaArtfulTumbler = Cards.invent("Juna, Artful Tumbler", "{2}{R}", "Legendary Creature \u2014 Human Performer", """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Whenever Juna attacks, you may pay {1}. When you do, target creature can't block this turn.
        """.trimIndent(), "3/2")
    val siegeBalloon = Cards.invent("Siege Balloon", "{2}{R}", "Artifact \u2014 Vehicle", """
            Flying
            {1}: Put a fire counter on this Vehicle.
            As long as this Vehicle has three or more fire counters on it, it's an artifact creature.
            Crew 3 (Tap any number of creatures you control with total power 3 or more: This Vehicle becomes an artifact creature until end of turn.)
        """.trimIndent(), "4/3")
    val silentBowArchers = Cards.invent("Silent Bow Archers", "{1}{R}", "Creature \u2014 Human Archer", """
            Reach
            When this creature enters, you may discard a card. If you do, draw a card.
        """.trimIndent(), "3/1")
    val heraldsDestiny = Cards.invent("Herald's Destiny", "{2}{G}{G}", "Enchantment \u2014 Aura", """
            Enchant creature you control
            Enchanted creature gets +1/+1 for each creature card in your graveyard and is an Avatar in addition to its other types.
            When enchanted creature dies, mill cards equal to its power. Return this card to its owner's hand and up to one creature card milled this way to the battlefield under your control.
        """.trimIndent(), null)
    val dutifulMenagerist = Cards.invent("Dutiful Menagerist", "{3}{G}", "Creature \u2014 Human Citizen Ally", "Each non-Human creature you control gets +1/+1 for each of its creature types, to a maximum of 10.", "4/4")
    val theStoneSovereign = Cards.invent("The Stone Sovereign", "{3}{G}", "Legendary Creature \u2014 Human Noble Ally", """
            When The Stone Sovereign enters, create a 4/4 green Bear creature token.
            Whenever one or more creatures you control with power 4 or greater attack, search your library for up to that many basic land cards, put them onto the battlefield tapped, then shuffle.
        """.trimIndent(), "2/2")
    val gumboTheOldKingsPal = Cards.invent("Gumbo, the Old King's Pal", "{4}{G}{G}", "Legendary Creature \u2014 Ape Goat", """
            When Gumbo enters, put a +1/+1 counter on each creature you control.
            Each creature you control with power 4 or greater can't be blocked by more than one creature.
        """.trimIndent(), "4/4")
    val emuPony = Cards.invent("Emu-Pony", "{2}{G}", "Creature \u2014 Bird Horse", "When this creature enters, mill three cards. You may put a land card from among them into your hand. If you don't, put a +1/+1 counter on this creature. (To mill three cards, put the top three cards of your library into your graveyard.)", "3/1")
    val columnVault = Cards.invent("Column Vault", "{G}", "Instant", "Target creature gets +2/+2 and gains reach until end of turn. Untap it.", null)
    val rowdySpectators = Cards.invent("Rowdy Spectators", "{1}{G}", "Creature \u2014 Human Citizen", "{T}: Add {G}. If you control a creature with power 4 or greater, add {G}{G} instead.", "2/1")
    val stonyRetort = Cards.invent("Stony Retort", "{1}{G}", "Instant", "Target creature you control deals damage equal to its power to target creature an opponent controls.", null)
    val fangTuskElkPanther = Cards.invent("Fang-Tusk Elk-Panther", "{4}{G}{G}", "Creature \u2014 Elk Cat", """
            Reach
            Forestcycling {2} ({2}, Discard this card: Search your library for a Forest card, reveal it, put it into your hand, then shuffle.)
        """.trimIndent(), "7/7")
    val tortoiseGoose = Cards.invent("Tortoise-Goose", "{G}", "Creature \u2014 Turtle Bird", "{3}: Until end of turn, this creature has base power 4 and gains trample.", "0/4")
    val haplessTurnipVendor = Cards.invent("Hapless Turnip Vendor", "{1}{G}", "Creature \u2014 Human Citizen", """
            When this creature enters, create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Whenever you sacrifice a Food, you may search your library for a basic land card and put it onto the battlefield tapped. If you search your library this way, put this creature on the bottom of its owner's library, then shuffle.
        """.trimIndent(), "2/2")
    // chunk 17 (Multicolored)
    val windWandererLegacy = Cards.invent("Wind Wanderer Legacy", "{W}{U}", "Enchantment", """
            When this enchantment enters, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            Creatures you control with flying get +1/+1.
        """.trimIndent(), null)
    val lynxHooter = Cards.invent("Lynx-Hooter", "{3}{W/U}", "Creature \u2014 Cat Bird", """
            Flying
            Whenever this creature attacks, untap target artifact or creature.
        """.trimIndent(), "3/3")
    val stoneRealmSoldier = Cards.invent("Stone Realm Soldier", "{4}{G/W}", "Creature \u2014 Human Soldier", """
            Vigilance
            When this creature enters, put a +1/+1 counter on each of up to two target creatures you control.
        """.trimIndent(), "3/4")
    val stoneBrawlGrapplers = Cards.invent("Stone Brawl Grapplers", "{3}{R/G}", "Creature \u2014 Human Warrior Performer", """
            Reach
            This creature gets +1/+0 and has trample as long as you control a land creature or a land entered the battlefield under your control this turn.
        """.trimIndent(), "3/4")
    val mistyBogSpiritWarden = Cards.invent("Misty Bog Spirit Warden", "{1}{U}{B}", "Creature \u2014 Human Druid Ally", """
            Lifelink
            Whenever you draw your second card each turn, create a 1/1 colorless Spirit creature token with "This token can't block or be blocked by non-Spirit creatures."
        """.trimIndent(), "2/4")
    val moroLunSpiritOfBalance = Cards.invent("Moro Lun, Spirit of Balance", "{2}{W/B}{W/B}", "Legendary Creature \u2014 Bear Spirit", """
            Whenever Moro Lun enters or attacks, you may sacrifice another creature or artifact. If you do, put two +1/+1 counters on Moro Lun.
            When Moro Lun leaves the battlefield, put its counters on target creature you control.
        """.trimIndent(), "3/3")
    val rookLibertyFighter = Cards.invent("Rook, Liberty Fighter", "{2}{R/W}{R/W}{R/W}", "Legendary Creature \u2014 Human Rebel Ally", """
            When Rook enters, he deals damage equal to the number of creatures you control to target creature an opponent controls.
            When Rook dies, put a +1/+1 counter on each of up to two target creatures.
        """.trimIndent(), "3/1")
    val vosskGrandChancellor = Cards.invent("Vossk, Grand Chancellor", "{1}{B/G}{B/G}", "Legendary Creature \u2014 Human Advisor", "Whenever another creature you control or a land you control is put into a graveyard from the battlefield, put a +1/+1 counter on target creature you control.", "2/3")
    val courierFalcon = Cards.invent("Courier Falcon", "{2}{U/B}", "Creature \u2014 Bird Scout", """
            Flying
            When this creature enters, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            This creature gets +2/+0 as long as you've drawn two or more cards this turn.
        """.trimIndent(), "1/2")
    val feigningPlaguebearers = Cards.invent("Feigning Plaguebearers", "{1}{W/B}", "Creature \u2014 Human Citizen Ally", "When this creature dies, create a 1/1 white Ally creature token.", "2/1")
    val scholarQuillAntiquarian = Cards.invent("Scholar Quill, Antiquarian", "{U/R}{U/R}", "Legendary Creature \u2014 Human Advisor Ally", """
            {T}, Discard a card: Draw a card.
            {1}, {T}, Sacrifice Scholar Quill: Return target instant or sorcery card from your graveyard to your hand. Activate only during your turn.
        """.trimIndent(), "0/3")
    val dunecallerScavengers = Cards.invent("Dunecaller Scavengers", "{W}{B}", "Creature \u2014 Human Rogue", """
            Whenever you sacrifice another permanent, put a +1/+1 counter on this creature.
            When this creature dies, you may exile it. When you do, return target creature card with mana value less than or equal to this creature's power from your graveyard to the battlefield.
        """.trimIndent(), "1/1")
    val tammBoldBladeThrower = Cards.invent("Tamm, Bold Blade-Thrower", "{U}{R}", "Legendary Creature \u2014 Human Warrior Ally", """
            When Tamm enters, discard up to two cards, then draw that many cards.
            Whenever you cast an artifact or Lesson spell, put a +1/+1 counter on Tamm.
        """.trimIndent(), "1/1")
    val tammLateralStrategist = Cards.invent("Tamm, Lateral Strategist", "{1}{W/U}{W/U}", "Legendary Creature \u2014 Human Warrior Ally", """
            Vigilance
            Whenever Tamm and at least one other creature attack, draw a card.
        """.trimIndent(), "2/4")
    val yarraFanbladeWarrior = Cards.invent("Yarra, Fanblade Warrior", "{2}{G/W}{G/W}", "Legendary Creature \u2014 Human Warrior Ally", """
            Yarra's power is equal to the number of creatures you control.
            Whenever Yarra attacks, create a 1/1 white Ally creature token that's tapped and attacking.
        """.trimIndent(), "*/4")
    val costsOfConflict = Cards.invent("Costs of Conflict", "{W}{B}", "Enchantment", """
            When this enchantment enters, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            Whenever you sacrifice a permanent during your turn, create a 1/1 white Ally creature token. This ability triggers only once each turn.
        """.trimIndent(), null)
    val roamingMinstrels = Cards.invent("Roaming Minstrels", "{3}{R/W}", "Creature \u2014 Human Bard Ally", "Whenever this creature attacks, creatures you control get +1/+0 until end of turn.", "2/5")
    val darrowTorn = Cards.invent("Darrow, Torn", "{B}{R}", "Legendary Creature \u2014 Human Rogue", """
            At the beginning of your first main phase, choose one that hasn't been chosen and you lose 2 life —
            • Draw a card.
            • Put a +1/+1 counter on Darrow.
            • Add {R}.
            • Exile Darrow, then return him to the battlefield under an opponent's control.
        """.trimIndent(), "2/3")
    // chunk 18 (ArtifactsAndLands)
    val casksOfVolatileJelly = Cards.invent("Casks of Volatile Jelly", "{1}", "Artifact", """
            {1}: Add one mana of any color. Activate only once each turn.
            {5}, {T}, Sacrifice this artifact: It deals 5 damage to target creature.
        """.trimIndent(), null)
    val shapersWaterFlask = Cards.invent("Shaper's Water Flask", "{3}", "Artifact", """
            Untap this artifact during each other player's untap step.
            {T}: Add one mana of any color.
        """.trimIndent(), null)
    val emberLegionWarship = Cards.invent("Ember Legion Warship", "{3}", "Artifact \u2014 Vehicle", """
            Reach
            When this Vehicle dies, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
        """.trimIndent(), "4/4")
    val starfallSaber = Cards.invent("Starfall Saber", "{7}", "Artifact \u2014 Equipment", """
            When this Equipment enters, destroy target permanent.
            Equipped creature gets +3/+3.
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """.trimIndent(), null)
    val orreryOfOrvath = Cards.invent("Orrery of Orvath", "{6}", "Legendary Artifact", """
            {1}, {T}: Scry 2.
            Whenever you scry or surveil, look at the top card of your library. You may cast that card without paying its mana cost. Do this only once each turn. (Look at the card after you scry or surveil.)
        """.trimIndent(), null)
    val faithfulReturningBlade = Cards.invent("Faithful Returning Blade", "{1}", "Artifact \u2014 Equipment", """
            Equipped creature has "{1}, {T}: Tap target creature. Return Faithful Returning Blade to its owner's hand."
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """.trimIndent(), null)
    val theWallsOfStoneholm = Cards.invent("The Walls of Stoneholm", "{8}", "Legendary Artifact Creature \u2014 Wall", """
            Defender
            Other permanents you control have indestructible.
        """.trimIndent(), "0/30")
    val paleBlossomTile = Cards.invent("Pale Blossom Tile", "{4}", "Artifact", """
            This artifact enters tapped.
            {T}: Add X mana of any one color, where X is the greatest number of creatures you control that have a creature type in common.
        """.trimIndent(), null)
    val forsakenWindShrine = Cards.invent("Forsaken Wind Shrine", null, "Land", """
            This land enters tapped unless you control a basic land.
            {T}: Add {W}.
            {3}{W}, {T}: Put a +1/+1 counter on each creature you control.
        """.trimIndent(), null)
    val frostwallCitadel = Cards.invent("Frostwall Citadel", null, "Land", """
            This land enters tapped unless you control a basic land.
            {T}: Add {U}.
            {2}{U}, {T}: Draw a card, then discard a card.
        """.trimIndent(), null)
    val airshipBoilerRoom = Cards.invent("Airship Boiler Room", null, "Land", """
            This land enters tapped.
            {T}: Add {U} or {R}.
            {4}, {T}, Sacrifice this land: Draw a card.
        """.trimIndent(), null)
    val scaldingPitPrison = Cards.invent("Scalding Pit Prison", null, "Land", """
            This land enters tapped.
            {T}: Add {B} or {R}.
            {4}, {T}, Sacrifice this land: Draw a card.
        """.trimIndent(), null)
    val mistyHollowBog = Cards.invent("Misty Hollow Bog", null, "Land", """
            This land enters tapped.
            {T}: Add {B} or {G}.
            {4}, {T}, Sacrifice this land: Draw a card.
        """.trimIndent(), null)
    val reedIsleVillage = Cards.invent("Reed Isle Village", null, "Land", """
            This land enters tapped.
            {T}: Add {G} or {W}.
            {4}, {T}, Sacrifice this land: Draw a card.
        """.trimIndent(), null)
    val contemplationPools = Cards.invent("Contemplation Pools", null, "Land", """
            This land enters tapped.
            {T}: Add {G} or {U}.
            {4}, {T}, Sacrifice this land: Draw a card.
        """.trimIndent(), null)
    val hazyPalmsOasis = Cards.invent("Hazy Palms Oasis", null, "Land", """
            This land enters tapped.
            {T}: Add {W} or {B}.
            {4}, {T}, Sacrifice this land: Draw a card.
        """.trimIndent(), null)
    val northernIceGates = Cards.invent("Northern Ice Gates", null, "Land", """
            This land enters tapped.
            {T}: Add {W} or {U}.
            {4}, {T}, Sacrifice this land: Draw a card.
        """.trimIndent(), null)
    val cliffsideCapital = Cards.invent("Cliffside Capital", null, "Land", """
            This land enters tapped.
            {T}: Add {R} or {G}.
            {4}, {T}, Sacrifice this land: Draw a card.
        """.trimIndent(), null)
    val domainOfVyrr = Cards.invent("Domain of Vyrr", null, "Land", """
            This land enters tapped unless you control a basic land.
            {T}: Add {B}.
            {3}{B}, {T}: Create a 1/1 colorless Spirit creature token with "This token can't block or be blocked by non-Spirit creatures."
        """.trimIndent(), null)
    val brawlArena = Cards.invent("Brawl Arena", null, "Land", """
            Vigilance
            When this land enters, scry 1. (Look at the top card of your library. You may put it on the bottom.)
            {T}: Add {C}.
            {1}, {T}: Add one mana of any color.
        """.trimIndent(), null)
    val hiddenPassage = Cards.invent("Hidden Passage", null, "Land \u2014 Cave", """
            This land can't be blocked.
            {T}: Add {C}.
            {4}, {T}: Two target creatures you control that share a creature type can't be blocked this turn.
        """.trimIndent(), null)
    val wyrmsStrait = Cards.invent("Wyrm's Strait", null, "Land", """
            This land enters tapped.
            {T}: Add {U} or {B}.
            {4}, {T}, Sacrifice this land: Draw a card.
        """.trimIndent(), null)
    val sunKissedSummit = Cards.invent("Sun-Kissed Summit", null, "Land", """
            This land enters tapped.
            {T}: Add {R} or {W}.
            {4}, {T}, Sacrifice this land: Draw a card.
        """.trimIndent(), null)
}
