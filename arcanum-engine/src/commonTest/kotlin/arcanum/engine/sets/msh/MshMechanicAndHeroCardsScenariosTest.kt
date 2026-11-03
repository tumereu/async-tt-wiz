package arcanum.engine.sets.msh

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.msh.Msh.bear
import arcanum.engine.sets.msh.Msh.doom
import arcanum.engine.sets.msh.Msh.elf
import arcanum.engine.sets.msh.Msh.giant
import arcanum.engine.sets.msh.Msh.idol
import arcanum.engine.sets.msh.Msh.ogre
import arcanum.engine.sets.msh.Msh.p1p1
import arcanum.engine.sets.msh.Msh.ponder
import arcanum.engine.sets.msh.Msh.shatter
import arcanum.engine.sets.msh.Msh.shock
import arcanum.engine.sets.msh.Msh.sword
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Marvel Super Heroes' power-up, teamwork, connive, Villain, Plan, Saga, double-faced, attacking-alone, targeting and
 * Hero cards in play (parser chunks 01–08), each card's own ability on top of the mechanics [MshMechanicsScenariosTest]
 * covers. Magma Raider and Venom-Tongue Adept repeat the counters-only power-up of Hovering Sentry Bot, Syndicate
 * Technicians the entering connive; Forge an Infinite Prism's last chapter (controlling another player's turn) waits for
 * that mechanic, its plan counters are played here. */
class MshMechanicAndHeroCardsScenariosTest {
    private val stellarParagonSkyWarden = Cards.invent("Stellar Paragon, Sky Warden", "{3}{W}{W}", "Legendary Creature — Human Kree Hero", """
        Flash
        Flying, lifelink
        Power-up — {5}{W}{W}: Put a +1/+1 counter and an indestructible counter on Stellar Paragon. (Activate each power-up ability only once. Reduce the cost by her mana cost if she entered this turn.)
    """, "5/4")
    private val directorGrimmSpymaster = Cards.invent("Director Grimm, Spymaster", "{W}", "Legendary Creature — Human Spy Hero", "Power-up — {W}{U}{B}{R}{G}: Put two +1/+1 counters on Director Grimm, then look at the top seven cards of your library. You may put a Hero, Equipment, or Vehicle card from among them onto the battlefield. If it's a double-faced card, you may transform it. Put the rest on the bottom of your library in a random order. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn.)", "2/1")
    private val hoveringSentryBot = Cards.invent("Hovering Sentry Bot", "{U}", "Artifact Creature — Robot Villain", """
        Flying
        Power-up — {5}{U}: Put three +1/+1 counters on this creature. (Activate each power-up ability only once. Reduce the cost by its mana cost if it entered this turn.)
    """, "1/1")
    private val daringLabAssistant = Cards.invent("Daring Lab Assistant", "{1}{U}", "Creature — Human Scientist", "Power-up — {5}{U}: Put a +1/+1 counter on this creature and draw two cards. (Activate each power-up ability only once. Reduce the cost by its mana cost if it entered this turn.)", "1/3")
    private val dwindleSizeShifter = Cards.invent("Dwindle, Size Shifter", "{U}", "Legendary Creature — Human Hero", """
        Dwindle can't be blocked if her power is 1 or less.
        Power-up — {X}{U}{U}: Put X +1/+1 counters on Dwindle. (Activate each power-up ability only once. Reduce the cost by her mana cost if she entered this turn.)
    """, "1/1")
    private val shadowFistAdept = Cards.invent("Shadow Fist Adept", "{2}{B}", "Creature — Human Ninja Villain", """
        Deathtouch
        Power-up — {4}{B}: Each opponent discards a card. Put a +1/+1 counter on this creature. (Activate each power-up ability only once. Reduce the cost by its mana cost if it entered this turn.)
    """, "2/2")
    private val deathlessLegionary = Cards.invent("Deathless Legionary", "{3}{B}", "Creature — Vampire Villain", """
        Flying
        Power-up — {5}{B}{B}: Return up to one target creature card from your graveyard to your hand. Put two +1/+1 counters on this creature. (Activate each power-up ability only once. Reduce the cost by its mana cost if it entered this turn.)
    """, "3/2")
    private val blazingPyrokid = Cards.invent("Blazing Pyrokid", "{2}{R}", "Legendary Creature — Human Hero", """
        Flying
        Whenever you draw a card, if you control another Hero, Blazing Pyrokid deals 1 damage to target opponent.
        Power-up — {6}{R}: Put three +1/+1 counters on Blazing Pyrokid. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn.)
    """, "2/2")
    private val trickstersHeir = Cards.invent("Trickster's Heir", "{1}{R}", "Legendary Creature — God Sorcerer Villain", """
        {1}, {T}: When you next cast an instant or sorcery spell with mana value less than or equal to Trickster's Heir's power this turn, copy that spell. You may choose new targets for the copy.
        Power-up — {4}{R}: Put two +1/+1 counters on Trickster's Heir. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn.)
    """, "2/1")
    private val flickerSprinter = Cards.invent("Flicker, Sprinter", "{R}", "Legendary Creature — Mutant Hero", """
        If Flicker, Sprinter is in your opening hand, you may begin the game with him on the battlefield.
        Haste
        Power-up — {4}{R}: Put a +1/+1 counter and a double strike counter on Flicker. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn.)
    """, "1/1")
    private val demigodOfMight = Cards.invent("Demigod of Might", "{2}{G}", "Legendary Creature — Demigod Warrior Hero", "Power-up — {4}{G}: Put a +1/+1 counter on Demigod of Might. He gains vigilance, indestructible, and haste until end of turn. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn.)", "3/3")
    private val menagerieOfHeroes = Cards.invent("Menagerie of Heroes", "{3}{G}", "Creature — Dragon Cat Dog Bird Frog Hero", """
        Reach
        Power-up — {6}{G}: Put a +1/+1 counter on this creature and create a 3/2 white Hero creature token with vigilance. (Activate each power-up ability only once. Reduce the cost by its mana cost if it entered this turn.)
    """, "4/4")
    private val emeraldJusticar = Cards.invent("Emerald Justicar", "{3}{G}", "Legendary Creature — Gamma Hero", """
        Reach, trample
        Power-up — {4}{G}{G}: Destroy up to one target artifact or enchantment. Put a +1/+1 counter on Emerald Justicar. (Activate each power-up ability only once. Reduce the cost by her mana cost if she entered this turn.)
    """, "4/4")
    private val jadeTigerAvatar = Cards.invent("Jade Tiger Avatar", "{1}{G}", "Legendary Creature — Human Hero", "Power-up — {5}{G}: Put a +1/+1 counter on Jade Tiger Avatar and create The Tiger God, a legendary 4/4 green Cat God creature token with \"The Tiger God can't be blocked by more than one creature.\" (Activate each power-up ability only once. Reduce the cost by her mana cost if she entered this turn.)", "2/2")
    private val hulkingHorrorGammaBrute = Cards.invent("Hulking Horror, Gamma Brute", "{3}{R/G}", "Legendary Creature — Gamma Villain", """
        Trample
        Power-up — {5}{R/G}{R/G}: Put a +1/+1 counter on Hulking Horror. He fights up to one target creature an opponent controls. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn.)
    """, "4/4")
    private val gammaColossus = Cards.invent("Gamma Colossus", "{3}{R}{G}", "Legendary Creature — Gamma Berserker Hero", """
        Reach, trample
        Power-up abilities of other creatures you control cost {3} less to activate.
        Power-up — {6}{R}{G}: Put five +1/+1 counters on Gamma Colossus. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn.)
    """, "6/5")
    private val ravagerOfBalance = Cards.invent("Ravager of Balance", "{R}{W}{B}", "Legendary Creature — Eternal Villain", """
        Deathtouch, lifelink
        Power-up — {C}{W}{U}{B}{R}{G}: Put two +1/+1 counters on Ravager of Balance. Choose odd or even. Destroy each other creature with mana value of the chosen quality. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn. Zero is even.)
    """, "4/4")
    private val assemblyLineDrone = Cards.invent("Assembly-Line Drone", "{3}", "Artifact Creature — Robot Villain", "Power-up — {6}: Put two +1/+1 counters on this creature and create a 2/2 colorless Robot Villain artifact creature token. (Activate each power-up ability only once. Reduce the cost by its mana cost if it entered this turn.)", "2/3")
    private val syntheticScion = Cards.invent("Synthetic Scion", "{3}", "Legendary Artifact Creature — Robot Hero", """
        Flying
        Cybernetic Senses — Whenever Synthetic Scion attacks, draw a card if her power is 4 or greater.
        Power-up — {7}: Put two +1/+1 counters on Synthetic Scion. (Activate each power-up ability only once. Reduce the cost by her mana cost if she entered this turn.)
    """, "2/2")
    private val skyshipBarrage = Cards.invent("Skyship Barrage", "{W}", "Instant", """
        Teamwork 2 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 2 or more.)
        Skyship Barrage deals 2 damage to target attacking or blocking creature. If this spell was cast using teamwork, it deals 4 damage to that creature instead.
    """)
    private val twofoldJustice = Cards.invent("Twofold Justice", "{1}{W}", "Sorcery", """
        Teamwork 4 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 4 or more.)
        Choose one. If this spell was cast using teamwork, choose both instead.
        • Street Justice — Exile target creature with toughness 4 or greater.
        • Legal Justice — Exile target enchantment with mana value 4 or greater.
    """)
    private val sunkenKingdomStrikes = Cards.invent("Sunken Kingdom Strikes", "{5}{U}{U}", "Sorcery", """
        Teamwork 4 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 4 or more.)
        Choose one. If this spell was cast using teamwork, choose both instead.
        • Target player creates a 6/5 blue Leviathan creature token with hexproof.
        • Return one or two target nonland permanents to their owners' hands.
    """)
    private val weForbidThee = Cards.invent("We Forbid Thee!", "{1}{U}", "Instant — Arcane", """
        Teamwork 2 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 2 or more.)
        Counter target spell unless its controller pays {2}. Counter that spell unless its controller pays {4} instead if this spell was cast using teamwork.
    """)
    private val tooWickedToRest = Cards.invent("Too Wicked to Rest", "{2}{B}", "Sorcery", """
        Teamwork 4 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 4 or more.)
        Choose target creature card in your graveyard with mana value 4 or less. If this spell was cast using teamwork, instead choose target creature card in your graveyard. Return the chosen card to the battlefield.
    """)
    private val assassinsBite = Cards.invent("Assassin's Bite", "{1}{B}", "Instant", """
        Teamwork 3 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 3 or more.)
        Choose one. If this spell was cast using teamwork, choose both instead.
        • Target creature gains deathtouch until end of turn.
        • Target creature gets -2/-2 until end of turn.
    """)
    private val bruteBASH = Cards.invent("BRUTE BASH!", "{1}{R}", "Instant", """
        Teamwork 4 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 4 or more.)
        Choose one. If this spell was cast using teamwork, choose both instead.
        • Destroy target noncreature artifact.
        • Target creature you control deals damage equal to its power to target creature an opponent controls.
    """)
    private val squadTactics = Cards.invent("Squad Tactics", "{1}{R}", "Instant", """
        Teamwork 1 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 1 or more.)
        Target creature gains double strike until end of turn. If this spell was cast using teamwork, that creature also gains trample until end of turn.
    """)
    private val earthsBravestChampions = Cards.invent("Earth's Bravest Champions", "{4}{G}{G}", "Sorcery", """
        Teamwork 5 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 5 or more.)
        Reveal the top eight cards of your library. You may put a creature card from among them onto the battlefield. If this spell was cast using teamwork, put any number of creature cards from among them onto the battlefield instead. Put the rest into your graveyard.
    """)
    private val goBananas = Cards.invent("Go Bananas!", "{G}", "Sorcery", """
        Teamwork 3 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 3 or more.)
        Choose one. If this spell was cast using teamwork, choose both instead.
        • Put a +1/+1 counter on target creature.
        • Target creature you control fights target creature an opponent controls.
    """)
    private val deceiversGambit = Cards.invent("Deceiver's Gambit", "{3}{U}", "Sorcery", "The owner of target creature an opponent controls puts it into their library second from the top or on the bottom. Then up to one target creature you control connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)")
    private val baronVexHeirOfMalice = Cards.invent("Baron Vex, Heir of Malice", "{B}{B}{B}", "Legendary Creature — Human Noble Villain", """
        Whenever you cast a black spell from your hand, Baron Vex connives.
        Boast — Exile any number of black cards from your graveyard with fifteen or more black mana symbols among their mana costs: Copy those exiled cards. You may cast up to three of the copies without paying their mana costs. (Activate only if this creature attacked this turn and only once each turn.)
    """, "3/3")
    private val baronStrakeSerpentOverlord = Cards.invent("Baron Strake, Serpent Overlord", "{2}{B}", "Legendary Creature — Human Villain", """
        Villain spells you cast cost {1} less to cast.
        Whenever another Villain you control enters, you may have it connive. Do this only once each turn. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)
    """, "2/2")
    private val ladyVisage = Cards.invent("Lady Visage", "{4}{B}", "Legendary Creature — Human Villain", """
        When Lady Visage enters, she connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
        Whenever you draw your second card each turn, create a 2/1 black Villain creature token with menace. (It can't be blocked except by two or more creatures.)
    """, "3/2")
    private val brain = Cards.invent("B.R.A.I.N.", "{3}{B}{B}", "Legendary Artifact Creature — Villain", """
        Flying, lifelink
        Mental Organism — Pay 3 life: B.R.A.I.N. connives. Activate only during your turn. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
        Designed Only for Killing — Creatures your opponents control get -1/-1.
    """, "2/2")
    private val bladesmanSharpRogue = Cards.invent("Bladesman, Sharp Rogue", "{1}{B}", "Legendary Creature — Human Hero Villain", """
        Whenever another Villain you control enters, attach up to one target Equipment you control to target creature you control.
        Whenever an equipped creature you control attacks, it connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)
    """, "2/2")
    private val chronoTyrant = Cards.invent("Chrono Tyrant", "{2}{U}{B}", "Legendary Creature — Human Villain", """
        Whenever Chrono Tyrant attacks, he connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
        Whenever you draw your second card each turn, each opponent loses 1 life and you gain 1 life.
    """, "3/4")
    private val bonebreakerCruelMercenary = Cards.invent("Bonebreaker, Cruel Mercenary", "{3}{B}", "Legendary Creature — Human Mercenary Villain", """
        Deathtouch
        Whenever another Villain you control enters, put a +1/+1 counter on Bonebreaker. He deals 2 damage to each opponent. This ability triggers only once each turn.
    """, "3/3")
    private val waspstingMarauder = Cards.invent("Waspsting Marauder", "{1}{B}", "Legendary Creature — Human Rogue Villain", """
        Flying
        Whenever another Villain you control enters, Waspsting Marauder gets +1/+0 and gains lifelink until end of turn.
    """, "1/2")
    private val madameSerpentine = Cards.invent("Madame Serpentine", "{2}{B}{R}", "Legendary Creature — Human Villain", "Whenever you cast a Villain spell, create a 2/1 black Villain creature token with menace. (It can't be blocked except by two or more creatures.)", "2/3")
    private val theSinisterCouncil = Cards.invent("The Sinister Council", "{5}{B}", "Legendary Creature — Human Villain", """
        Other Villains you control get +2/+1.
        {1}{B}, Discard this card: Search your library for a Plan card, reveal it, put it into your hand, then shuffle.
    """, "5/6")
    private val reviseTheRecords = Cards.invent("Revise the Records", "{2}{U}", "Enchantment — Plan", """
        Whenever one or more creatures you control become tapped, draw a card, then discard a card and put a plan counter on this enchantment.
        When the fourth plan counter is put on this enchantment, sacrifice it. When you do, return up to two target instant and/or sorcery cards from your graveyard to your hand.
    """)
    private val forgeAnInfinitePrism = Cards.invent("Forge an Infinite Prism", "{2}{B}", "Enchantment — Plan", """
        Whenever you draw your second card each turn, create a 2/1 black Villain creature token with menace and put a plan counter on this enchantment.
        When the seventh plan counter is put on this enchantment, sacrifice it. When you do, you control target opponent during their next turn. (You see all cards that player could see and make all decisions for them.)
    """)
    private val tyrannyReignsEternal = Cards.invent("Tyranny Reigns Eternal", "{1}{B}", "Enchantment — Plan", """
        Whenever a Villain you control enters, each opponent loses 1 life and you gain 1 life. Put a plan counter on this enchantment.
        When the fifth plan counter is put on this enchantment, sacrifice it. When you do, target opponent exiles the top five cards of their library. You may cast up to two spells from among the exiled cards without paying their mana costs.
    """)
    private val riseOfTheAutomatons = Cards.invent("Rise of the Automatons", "{3}{B}", "Enchantment — Plan", """
        Whenever one or more creature cards are put into your graveyard from anywhere, you draw a card, lose 1 life, and put a plan counter on this enchantment.
        When the third plan counter is put on this enchantment, sacrifice it and create three 2/2 colorless Robot Villain artifact creature tokens.
    """)
    private val ruinToOurFoes = Cards.invent("Ruin to Our Foes", "{2}{R}", "Enchantment — Plan", """
        Whenever you cast a noncreature spell, create a tapped Treasure token and put a plan counter on this enchantment.
        When the fourth plan counter is put on this enchantment, sacrifice it. When you do, it deals 7 damage divided as you choose among one or two targets.
    """)
    private val seizeTheRealm = Cards.invent("Seize the Realm", "{1}{G}", "Enchantment — Plan", """
        Landfall — Whenever a land you control enters, put a +1/+1 counter on target creature you control and a plan counter on this enchantment.
        When the fourth plan counter is put on this enchantment, sacrifice it. When you do, put an indestructible counter on target creature you control.
    """)
    private val originOfTheGuardians = Cards.invent("Origin of the Guardians", "{1}{W}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Scry 2.
        II — You may put a Hero creature card with mana value 3 or less from your hand onto the battlefield. If you don't, draw a card.
        III — Put a +1/+1 counter on each creature you control.
    """)
    private val armorFeud = Cards.invent("Armor Feud", "{2}{U}{R}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — You may draw a card for each artifact you control. If you do, each opponent draws a card.
        II — Artifact spells you cast this turn cost {1} less to cast.
        III — This Saga deals X damage to target opponent, where X is the greatest mana value among artifacts you control.
    """)
    private val guardiansUnderSiege = Cards.invent("Guardians: Under Siege", "{2}{B}{R}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
        I — Create two 2/1 black Villain creature tokens with menace.
        II — This Saga deals 2 damage to each non-Villain creature and each opponent.
        III — Create a Treasure token for each Villain you control.
    """)
    private val theComingOfTheDevourer = Cards.invent("The Coming of the Devourer", "{2}{B}{B}{G}", "Enchantment — Saga", """
        (As this Saga enters and after your draw step, add a lore counter. Sacrifice after IV.)
        I — Destroy up to one target nonland permanent.
        II, III — Each opponent loses 2 life.
        IV — Create Worldeater, a legendary 16/16 black Elder Alien creature token with flying, trample, and "Whenever Worldeater attacks, destroy target land."
    """)
    private val operativeNineFieldLiaison = Cards.invent("Operative Nine, Field Liaison", "{2}{W}", "Legendary Creature — Human Spy Hero", "Whenever a creature you control attacks alone, investigate. (Create a Clue token. It's an artifact with \"{2}, Sacrifice this token: Draw a card.\")", "3/2")
    private val crowdOfLoyalFans = Cards.invent("Crowd of Loyal Fans", "{W}", "Creature — Human Citizen", "{T}: Target creature you control that's attacking alone gets +1/+0 until end of turn. You gain 1 life.", "1/2")
    private val bureauSpyKit = Cards.invent("Bureau Spy Kit", "{W}", "Artifact — Equipment", """
        Equipped creature gets +1/+1.
        Whenever equipped creature attacks alone, untap it and scry 1. (Look at the top card of your library. You may put that card on the bottom.)
        Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val serpentCultInfiltration = Cards.invent("Serpent Cult Infiltration", "{3}{B}", "Enchantment", """
        When this enchantment enters, target opponent discards two cards.
        Whenever a creature you control attacks alone, target opponent loses 1 life and you gain 1 life.
    """)
    private val crimsonSpiderDoubleAgent = Cards.invent("Crimson Spider, Double Agent", "{1}{W}{B}", "Legendary Creature — Human Hero Villain", """
        Deathtouch
        Whenever a creature you control attacks alone, it gains first strike and menace until end of turn. (It can't be blocked except by two or more creatures.)
    """, "3/2")
    private val lucasStoneIronskin = Cards.invent("Lucas Stone, Ironskin", "{3}{W}", "Legendary Creature — Human Hero", "Unbreakable Skin — Whenever Lucas Stone attacks alone, he gets +2/+0 and gains indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy him.)", "2/5")
    private val larkspurAceAgent = Cards.invent("Larkspur, Ace Agent", "{3}{W}", "Legendary Creature — Human Spy Hero", """
        Double strike
        Whenever you cast a spell that targets a creature you control, put a +1/+1 counter on Larkspur.
    """, "2/2")
    private val missWondrousKiranShah = Cards.invent("Miss Wondrous, Kiran Shah", "{2}{U}", "Legendary Creature — Mutant Inhuman Hero", """
        Reach, vigilance
        You have no maximum hand size.
        Embiggen Fist — Whenever you cast a spell that targets a creature you control, draw a card. Until end of turn, Miss Wondrous gains "Miss Wondrous's base power is equal to the number of cards in your hand."
    """, "1/4")
    private val jadeFistLivingWeapon = Cards.invent("Jade Fist, Living Weapon", "{2}{R}", "Legendary Creature — Human Warrior Hero", "Whenever you cast a spell that targets a creature you control, Jade Fist gains \"{T}: Jade Fist deals damage equal to his power to any other target\" until end of turn.", "2/3")
    private val bouncebackNewWarrior = Cards.invent("Bounceback, New Warrior", "{2}{U/R}", "Legendary Creature — Human Hero", "Whenever a player casts a spell that targets Bounceback, he gets +2/+2 until end of turn. You may choose new targets for that spell.", "2/2")
    private val grakThulVorr = Cards.invent("Grak Thul Vorr", "{2}{R}{R}", "Legendary Creature — Alien Dragon Villain", """
        Flying
        Whenever you cast an instant or sorcery spell that targets an artifact or land, copy that spell. You may choose new targets for the copy. Put two +1/+1 counters on Grak Thul Vorr.
    """, "3/5")
    private val handlerPhilCorbin = Cards.invent("Handler Phil Corbin", "{1}{W}", "Legendary Creature — Human Spy Hero", """
        Vigilance
        {T}: Put a +1/+1 counter on each other Hero you control.
    """, "2/2")
    private val guardiansRally = Cards.invent("Guardians, Rally!", "{4}{W}", "Enchantment", """
        Flash
        Heroes you control get +2/+2.
        At the beginning of each end step, if you attacked with a Hero this turn or a Hero entered the battlefield under your control this turn, draw a card.
    """)
    private val boroughReinforcements = Cards.invent("Borough Reinforcements", "{4}{W}", "Sorcery", """
        Create two 3/2 white Hero creature tokens with vigilance.
        Basic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)
    """)
    private val commanderLibertySuperSoldier = Cards.invent("Commander Liberty, Super-Soldier", "{1}{W}{W}", "Legendary Creature — Human Soldier Hero", """
        First strike
        Commander Liberty enters with a shield counter on him. (If he would be dealt damage or destroyed, remove a shield counter from him instead.)
        As long as Commander Liberty has a shield counter on him, you and other Heroes you control have hexproof.
    """, "3/2")

    private val hero = Cards.invent("Avenue Hero", "{1}{W}", "Creature — Human Hero", "", "2/2")
    private val villain = Cards.invent("Avenue Thug", "{1}{B}", "Creature — Human Villain", "", "2/2")
    private val gate = Cards.invent("Avenue Gate", null, "Land", "{T}: Add {C}.")
    private val bolt = Cards.invent("Avenue Bolt", "{R}", "Instant", "Avenue Bolt deals 1 damage to any target.")
    private val letters = Cards.invent("Avenue Letters", "{U}", "Sorcery", "Draw two cards.")
    private val boost = Cards.invent("Avenue Boost", "{W}", "Instant", "Target creature gets +1/+1 until end of turn.")
    private val wrenHale = Cards.inventFaces("ModalDoubleFaced",
        face("Counselor Wren Hale", "{1}{W}", "Legendary Creature — Human Advisor Hero", """
            Your opponents can't cast spells during your turn.
            {3}{G}{W}{W}: Transform Counselor Wren Hale. Activate only as a sorcery.
        """, "2/3", null, null),
        face("The Emerald Amazon", "{3}{G}{W}{W}", "Legendary Creature — Gamma Hero", """
            Reach, trample
            Your opponents can't cast spells during your turn.
            Whenever a creature you control is dealt damage, you may have The Emerald Amazon deal that much damage to any target. Do this only once each turn.
        """, "6/6", null, null))
    private val maraBell = Cards.inventFaces("ModalDoubleFaced",
        face("Lieutenant Mara Bell", "{2}{W}", "Legendary Creature — Human Hero", """
            Flying, prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            {2}{R}{W}{W}: Transform Lieutenant Mara Bell. Activate only as a sorcery.
        """, "3/3", null, null),
        face("Radiant, Living Light", "{2}{R}{W}{W}", "Legendary Creature — Elemental Hero", """
            Flying, hexproof, prowess
            Whenever you cast a noncreature spell, put a +1/+1 counter on each other creature you control.
        """, "4/4", null, null))
    private val antonSteele = Cards.inventFaces("ModalDoubleFaced",
        face("Anton Steele", "{1}{U}", "Legendary Creature — Human Artificer Hero", """
            {1}, {T}: Look at the top four cards of your library. You may reveal an artifact card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
            {4}{U}{R}: Transform Anton Steele. Activate only as a sorcery.
        """, "1/3", null, null),
        face("The Invincible Steel Sentinel", "{4}{U}{R}", "Legendary Artifact Creature — Human Hero", """
            Flying, haste
            At the beginning of combat on your turn, you may put an artifact card from your hand onto the battlefield. If it's an Equipment, attach it to The Invincible Steel Sentinel.
        """, "5/5", null, null))
    private val kingTavari = Cards.inventFaces("ModalDoubleFaced",
        face("King Tavari", "{1}{W}{U}", "Legendary Creature — Human Noble Hero", """
            Flash
            Whenever a player draws their second card each turn, you draw a card.
            {4}{W}{U}: Transform King Tavari. Activate only as a sorcery.
        """, "3/2", null, null),
        face("Shadow Panther, Hope Enduring", "{4}{W}{U}", "Legendary Creature — Human Warrior Hero", """
            Flash
            Double strike
            Prevent all damage that would be dealt to Shadow Panther.
            Whenever Shadow Panther deals combat damage to a player, draw a card.
        """, "3/3", null, null))

    // ---- Chunks 01–02: power-up (CR 702.193)

    @Test
    fun stellarParagonGetsAnIndestructibleCounter() = scenario {
        alice.battlefield(stellarParagonSkyWarden, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.activates(stellarParagonSkyWarden)
        resolveAll()
        expect {
            stellarParagonSkyWarden.counters(p1p1) shouldBe 1
            stellarParagonSkyWarden hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun directorGrimmFindsAHero() = scenario {
        alice.battlefield(directorGrimmSpymaster, "Plains", "Island", "Swamp", "Mountain", "Forest")
        alice.library(bear, hero, ogre)
        alice.activates(directorGrimmSpymaster)
        resolveAll()
        chooseIfOffered(hero)
        resolveAll()
        expect {
            alice.battlefield has hero
            directorGrimmSpymaster.counters(p1p1) shouldBe 2
        }
    }

    @Test
    fun hoveringSentryBotPowersUp() = scenario {
        alice.battlefield(hoveringSentryBot, "Island", "Island", "Island", "Island", "Island", "Island")
        alice.activates(hoveringSentryBot)
        resolveAll()
        expect { hoveringSentryBot.counters(p1p1) shouldBe 3 }
    }

    @Test
    fun daringLabAssistantDrawsTwo() = scenario {
        alice.battlefield(daringLabAssistant, "Island", "Island", "Island", "Island", "Island", "Island")
        alice.library(bear, bear)
        alice.activates(daringLabAssistant)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            daringLabAssistant.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun dwindleSlipsPastWhileSmall() = scenario {
        alice.battlefield(dwindleSizeShifter)
        bob.battlefield(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(dwindleSizeShifter)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(bear to dwindleSizeShifter) }
    }

    @Test
    fun dwindlePowersUpByX() = scenario {
        alice.battlefield(dwindleSizeShifter, "Island", "Island", "Island", "Island")
        alice.activates(dwindleSizeShifter, x = 2)
        resolveAll()
        expect { dwindleSizeShifter.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun shadowFistAdeptMakesThemDiscard() = scenario {
        alice.battlefield(shadowFistAdept, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        bob.hand(bear)
        alice.activates(shadowFistAdept)
        resolveAll()
        chooseIfOffered(bear)
        resolveAll()
        expect {
            bob.graveyard has bear
            shadowFistAdept.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun deathlessLegionaryRegrows() = scenario {
        alice.battlefield(deathlessLegionary, "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(ogre)
        alice.activates(deathlessLegionary, target = ogre)
        resolveAll()
        expect {
            alice.hand has ogre
            deathlessLegionary.counters(p1p1) shouldBe 2
        }
    }

    @Test
    fun blazingPyrokidPingsOnDrawsWithAnotherHero() = scenario {
        alice.battlefield(blazingPyrokid, hero, "Island")
        alice.hand(ponder)
        alice.library(bear)
        alice.casts(ponder)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    // CR 707.10: a copy of the next small spell
    @Test
    fun trickstersHeirCopiesTheNextSmallSpell() = scenario {
        alice.battlefield(trickstersHeir, "Mountain", "Mountain")
        alice.hand(shock)
        alice.activates(trickstersHeir, paying = listOf("Mountain"))
        resolveAll()
        alice.casts(shock, target = "bob")
        resolveAll()
        // CR 707.10c: the copy keeps its target
        may(false)
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    @Test
    fun flickerSprinterPowersUpWithDoubleStrike() = scenario {
        alice.battlefield(flickerSprinter, "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.activates(flickerSprinter)
        resolveAll()
        expect {
            flickerSprinter.counters(p1p1) shouldBe 1
            flickerSprinter hasKeyword Keyword.DoubleStrike
        }
    }

    @Test
    fun demigodOfMightPowersUp() = scenario {
        alice.battlefield(demigodOfMight, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.activates(demigodOfMight)
        resolveAll()
        expect {
            demigodOfMight hasKeyword Keyword.Indestructible
            demigodOfMight hasKeyword Keyword.Haste
            demigodOfMight.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun menagerieOfHeroesMakesAHero() = scenario {
        alice.battlefield(menagerieOfHeroes, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.activates(menagerieOfHeroes)
        resolveAll()
        expect { "Hero Token" hasKeyword Keyword.Vigilance }
    }

    @Test
    fun emeraldJusticarDestroysAnArtifact() = scenario {
        alice.battlefield(emeraldJusticar, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        bob.battlefield(idol)
        alice.activates(emeraldJusticar, target = idol)
        resolveAll()
        expect {
            bob.graveyard has idol
            emeraldJusticar.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun jadeTigerAvatarCallsTheTigerGod() = scenario {
        alice.battlefield(jadeTigerAvatar, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.activates(jadeTigerAvatar)
        resolveAll()
        expect { "The Tiger God".power shouldBe 4 }
    }

    @Test
    fun hulkingHorrorFightsOnPowerUp() = scenario {
        alice.battlefield(hulkingHorrorGammaBrute, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        bob.battlefield(ogre)
        alice.activates(hulkingHorrorGammaBrute, target = ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun gammaColossusDiscountsOtherPowerUps() = scenario {
        alice.battlefield(gammaColossus, hoveringSentryBot, "Island", "Island", "Island")
        alice.activates(hoveringSentryBot)
        resolveAll()
        expect { hoveringSentryBot.counters(p1p1) shouldBe 3 }
    }

    @Test
    fun ravagerOfBalanceDestroysTheOdd() = scenario {
        alice.battlefield(ravagerOfBalance, gate, "Plains", "Island", "Swamp", "Mountain", "Forest")
        bob.battlefield(ogre, bear)
        alice.activates(ravagerOfBalance)
        resolveAll()
        option("odd")
        resolveAll()
        expect {
            bob.graveyard has ogre
            bob.battlefield has bear
            alice.battlefield has ravagerOfBalance
        }
    }

    @Test
    fun assemblyLineDroneBuildsARobot() = scenario {
        alice.battlefield(assemblyLineDrone, "Island", "Island", "Island", "Island", "Island", "Island")
        alice.activates(assemblyLineDrone)
        resolveAll()
        expect { alice.battlefield has "Robot Villain Token" }
    }

    @Test
    fun syntheticScionDrawsAtFourPower() = scenario {
        alice.battlefield(syntheticScion, counters = listOf(p1p1 to 2))
        alice.library(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(syntheticScion)
        resolveAll()
        expect { alice.hand has bear }
    }

    // ---- Chunk 03: teamwork (CR 702.194)

    @Test
    fun skyshipBarrageWithTeamwork() = scenario {
        alice.battlefield("Plains", bear)
        alice.hand(skyshipBarrage)
        alice.library(elf)
        bob.battlefield(ogre)
        bob.library(elf, elf)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(ogre)
        bob.passes()
        alice.casts(skyshipBarrage, target = ogre, kicked = 1, choosing = listOf(bear))
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun twofoldJusticeOneModeWithout() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(twofoldJustice)
        bob.battlefield(giant)
        alice.casts(twofoldJustice, kicked = 0, modes = listOf(0), target = giant)
        resolveAll()
        expect { bob.exile has giant }
    }

    @Test
    fun sunkenKingdomStrikesBouncesTwo() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(sunkenKingdomStrikes)
        bob.battlefield(ogre, idol)
        alice.casts(sunkenKingdomStrikes, kicked = 0, modes = listOf(1), targets = listOf(ogre, idol))
        resolveAll()
        expect {
            bob.hand has ogre
            bob.hand has idol
        }
    }

    @Test
    fun weForbidTheeTaxesASpell() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(weForbidThee)
        bob.battlefield("Forest", "Forest")
        bob.hand(bear)
        turn(active = bob)
        bob.casts(bear)
        bob.passes()
        alice.casts(weForbidThee, kicked = 0, target = bear)
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect { bob.graveyard has bear }
    }

    @Test
    fun tooWickedToRestReanimatesASmallCreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(tooWickedToRest)
        alice.graveyard(ogre, giant)
        alice.casts(tooWickedToRest, kicked = 0, target = ogre)
        resolveAll()
        expect { alice.battlefield has ogre }
    }

    @Test
    fun tooWickedToRestWithTeamworkAnyCreature() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", ogre, elf)
        alice.hand(tooWickedToRest)
        alice.graveyard(giant)
        alice.casts(tooWickedToRest, kicked = 1, choosing = listOf(ogre, elf), target = giant)
        resolveAll()
        expect { alice.battlefield has giant }
    }

    @Test
    fun assassinsBiteBothWithTeamwork() = scenario {
        alice.battlefield("Swamp", "Swamp", ogre, bear)
        alice.hand(assassinsBite)
        bob.battlefield(elf)
        alice.casts(assassinsBite, kicked = 1, choosing = listOf(ogre), modes = listOf(0, 1), targets = listOf(bear, elf))
        resolveAll()
        expect {
            bear hasKeyword Keyword.Deathtouch
            bob.graveyard has elf
        }
    }

    @Test
    fun bruteBashBites() = scenario {
        alice.battlefield("Mountain", "Mountain", giant)
        alice.hand(bruteBASH)
        bob.battlefield(ogre)
        alice.casts(bruteBASH, kicked = 0, modes = listOf(1), targets = listOf(giant, ogre))
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun squadTacticsTrampleWithTeamwork() = scenario {
        alice.battlefield("Mountain", "Mountain", bear, elf)
        alice.hand(squadTactics)
        alice.casts(squadTactics, kicked = 1, choosing = listOf(elf), target = bear)
        resolveAll()
        expect {
            bear hasKeyword Keyword.DoubleStrike
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun earthsBravestChampionsPutsACreatureIn() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(earthsBravestChampions)
        alice.library(bear, "Island", giant, shock)
        alice.casts(earthsBravestChampions, kicked = 0)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(giant)
        resolveAll()
        expect {
            alice.battlefield has giant
            alice.graveyard has bear
            alice.graveyard has shock
        }
    }

    @Test
    fun goBananasCounters() = scenario {
        alice.battlefield("Forest", bear)
        alice.hand(goBananas)
        alice.casts(goBananas, kicked = 0, modes = listOf(0), target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    // ---- Chunk 04: connive (CR 701.50) and Villains

    @Test
    fun deceiversGambitTucksAndConnives() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", bear)
        alice.hand(deceiversGambit, ogre)
        alice.library(elf)
        bob.battlefield(giant)
        bob.library(elf, elf)
        alice.casts(deceiversGambit, targets = listOf(giant, bear))
        resolveAll()
        if (pending is Decision.ChooseOption) option("on the bottom")
        resolveAll()
        chooseIfOffered(ogre)
        resolveAll()
        expect {
            bob.library.names.last() shouldBe giant
            bear.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun baronVexConnivesOnBlackSpells() = scenario {
        alice.battlefield(baronVexHeirOfMalice, "Swamp")
        alice.hand(doom, ogre)
        alice.library(elf)
        bob.battlefield(bear)
        alice.casts(doom, target = bear)
        resolveAll()
        chooseIfOffered(ogre)
        resolveAll()
        expect { baronVexHeirOfMalice.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun baronStrakeDiscountsVillainsAndConnives() = scenario {
        alice.battlefield(baronStrakeSerpentOverlord, "Swamp")
        alice.hand(villain, ogre)
        alice.library(elf)
        alice.casts(villain)
        resolveAll()
        may(true)
        resolveAll()
        chooseIfOffered(ogre)
        resolveAll()
        expect { villain.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun ladyVisageMakesAVillainOnTheSecondDraw() = scenario {
        alice.battlefield(ladyVisage, "Island")
        alice.hand(letters)
        alice.library(bear, bear)
        alice.casts(letters)
        resolveAll()
        expect { "Villain Token" hasKeyword Keyword.Menace }
    }

    @Test
    fun brainConnivesForLifeAndWeakensThem() = scenario {
        alice.battlefield(brain)
        alice.hand(ogre)
        alice.library(elf)
        bob.battlefield(elf)
        alice.activates(brain)
        resolveAll()
        chooseIfOffered(ogre)
        resolveAll()
        expect {
            bob.graveyard has elf
            alice.life shouldBe 17
            brain.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun bladesmanArmsAndConnives() = scenario {
        alice.battlefield(bladesmanSharpRogue, sword, bear, "Swamp", "Swamp")
        alice.hand(villain, ogre)
        alice.library(elf)
        alice.casts(villain)
        resolveAll()
        pickTargets(sword, bear)
        resolveAll()
        expect { bear.power shouldBe 3 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        chooseIfOffered(ogre)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun chronoTyrantConnivesAndDrains() = scenario {
        alice.battlefield(chronoTyrant)
        alice.hand(ogre)
        alice.library(elf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(chronoTyrant)
        resolveAll()
        chooseIfOffered(ogre)
        resolveAll()
        expect { chronoTyrant.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun chronoTyrantDrainsOnTheSecondDraw() = scenario {
        alice.battlefield(chronoTyrant, "Island")
        alice.hand(letters)
        alice.library(bear, bear)
        alice.casts(letters)
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
        }
    }

    @Test
    fun bonebreakerGrowsOnceForVillains() = scenario {
        alice.battlefield(bonebreakerCruelMercenary, "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(villain, villain)
        alice.casts(villain)
        resolveAll()
        alice.casts(villain)
        resolveAll()
        expect {
            bonebreakerCruelMercenary.counters(p1p1) shouldBe 1
            bob.life shouldBe 18
        }
    }

    @Test
    fun waspstingMarauderLifelinkOnVillains() = scenario {
        alice.battlefield(waspstingMarauder, "Swamp", "Swamp")
        alice.hand(villain)
        alice.casts(villain)
        resolveAll()
        expect {
            waspstingMarauder.power shouldBe 2
            waspstingMarauder hasKeyword Keyword.Lifelink
        }
    }

    @Test
    fun madameSerpentineMakesVillains() = scenario {
        alice.battlefield(madameSerpentine, "Swamp", "Swamp")
        alice.hand(villain)
        alice.casts(villain)
        resolveAll()
        expect { alice.battlefield has "Villain Token" }
    }

    @Test
    fun theSinisterCouncilPumpsVillainsAndFindsPlans() = scenario {
        alice.battlefield(theSinisterCouncil, villain)
        expect { villain.power shouldBe 4 }
    }

    @Test
    fun theSinisterCouncilSearchesAPlan() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(theSinisterCouncil)
        alice.library(bear, tyrannyReignsEternal)
        alice.activates(theSinisterCouncil)
        resolveAll()
        chooseIfOffered(tyrannyReignsEternal)
        resolveAll()
        expect { alice.hand has tyrannyReignsEternal }
    }

    // ---- Chunk 05: Plans and Sagas

    @Test
    fun reviseTheRecordsLootsWhenCreaturesTap() = scenario {
        alice.battlefield(reviseTheRecords, bear)
        alice.hand(ogre)
        alice.library(elf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        chooseIfOffered(ogre)
        resolveAll()
        expect {
            alice.graveyard has ogre
            reviseTheRecords.counters(CounterKind.Named("plan")) shouldBe 1
        }
    }

    @Test
    fun forgeAnInfinitePrismMakesVillainsOnTheSecondDraw() = scenario {
        alice.battlefield(forgeAnInfinitePrism, "Island")
        alice.hand(letters)
        alice.library(bear, bear)
        alice.casts(letters)
        resolveAll()
        expect {
            alice.battlefield has "Villain Token"
            forgeAnInfinitePrism.counters(CounterKind.Named("plan")) shouldBe 1
        }
    }

    @Test
    fun tyrannyReignsEternalDrainsForVillains() = scenario {
        alice.battlefield(tyrannyReignsEternal, "Swamp", "Swamp")
        alice.hand(villain)
        alice.casts(villain)
        resolveAll()
        expect {
            bob.life shouldBe 19
            tyrannyReignsEternal.counters(CounterKind.Named("plan")) shouldBe 1
        }
    }

    @Test
    fun riseOfTheAutomatonsBuildsRobotsOnTheThirdCounter() = scenario {
        alice.battlefield(riseOfTheAutomatons, bear, elf, ogre, "Swamp", "Swamp", "Swamp")
        alice.hand(doom, doom, doom)
        alice.library(giant, giant, giant)
        listOf(bear, elf, ogre).forEach {
            alice.casts(doom, target = it)
            resolveAll()
        }
        expect { alice.battlefield.names.count { it == "Robot Villain Token" } shouldBe 3 }
    }

    @Test
    fun ruinToOurFoesMakesTappedTreasures() = scenario {
        alice.battlefield(ruinToOurFoes, "Mountain")
        alice.hand(shock)
        alice.casts(shock, target = "bob")
        resolveAll()
        expect {
            "Treasure Token".tapped shouldBe true
            ruinToOurFoes.counters(CounterKind.Named("plan")) shouldBe 1
        }
    }

    @Test
    fun seizeTheRealmCountersOnLandfall() = scenario {
        alice.battlefield(seizeTheRealm, bear)
        alice.hand("Forest")
        alice.plays("Forest")
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            seizeTheRealm.counters(CounterKind.Named("plan")) shouldBe 1
        }
    }

    @Test
    fun originOfTheGuardiansScriesTwo() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(originOfTheGuardians)
        alice.library(bear, ogre)
        alice.casts(originOfTheGuardians)
        resolveAll()
        alice.chooses(bear, ogre)
        resolveAll()
        expect { alice.library.names.last() shouldBe ogre }
    }

    @Test
    fun armorFeudDrawsPerArtifact() = scenario {
        alice.battlefield("Island", "Island", "Mountain", "Mountain", idol, idol)
        alice.hand(armorFeud)
        alice.library(bear, bear)
        bob.library(elf)
        alice.casts(armorFeud)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            bob.hand.size shouldBe 1
        }
    }

    @Test
    fun guardiansUnderSiegeMakesTwoVillains() = scenario {
        alice.battlefield("Swamp", "Swamp", "Mountain", "Mountain")
        alice.hand(guardiansUnderSiege)
        alice.casts(guardiansUnderSiege)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Villain Token" } shouldBe 2 }
    }

    @Test
    fun theComingOfTheDevourerDestroysAPermanent() = scenario {
        alice.battlefield("Swamp", "Swamp", "Forest", "Forest", "Forest")
        alice.hand(theComingOfTheDevourer)
        bob.battlefield(idol)
        alice.casts(theComingOfTheDevourer)
        resolveAll()
        targetsIfAsked(idol)
        resolveAll()
        expect { bob.graveyard has idol }
    }

    // ---- Chunk 06: double-faced (CR 712)

    @Test
    fun counselorWrenHaleSilencesOpponentsOnHerTurn() = scenario {
        alice.battlefield(wrenHale)
        bob.battlefield("Mountain")
        bob.hand(shock)
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.casts(shock, target = "alice") }
    }

    @Test
    fun counselorWrenHaleTransforms() = scenario {
        alice.battlefield(wrenHale, "Forest", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.activates(wrenHale)
        resolveAll()
        expect { card(wrenHale).power shouldBe 6 }
    }

    @Test
    fun theEmeraldAmazonReflectsDamage() = scenario {
        alice.battlefield(wrenHale, bear, "Forest", "Plains", "Plains", "Plains", "Plains", "Plains", "Mountain")
        alice.hand(bolt)
        alice.activates(wrenHale, paying = listOf("Forest", "Plains", "Plains", "Plains", "Plains", "Plains"))
        resolveAll()
        alice.casts(bolt, target = bear)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        if (pending is Decision.May) may(true)
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun lieutenantMaraBellBecomesRadiant() = scenario {
        alice.battlefield(maraBell, bear, "Mountain", "Plains", "Plains", "Plains", "Plains", "Mountain")
        alice.hand(bolt)
        alice.activates(maraBell, paying = listOf("Mountain", "Plains", "Plains", "Plains", "Plains"))
        resolveAll()
        alice.casts(bolt, target = "bob")
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun antonSteeleFindsAnArtifact() = scenario {
        alice.battlefield(antonSteele, "Island")
        alice.library(bear, idol, ogre)
        alice.activates(antonSteele)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(idol)
        resolveAll()
        expect { alice.hand has idol }
    }

    @Test
    fun theInvincibleSteelSentinelDropsInArtifacts() = scenario {
        alice.battlefield(antonSteele, "Island", "Island", "Island", "Island", "Island", "Mountain")
        alice.hand(sword)
        alice.activates(antonSteele, ability = 1)
        resolveAll()
        advanceTo(TurnStep.BeginningOfCombat)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(sword)
        resolveAll()
        expect { card(antonSteele).power shouldBe 6 }
    }

    @Test
    fun kingTavariDrawsOnASecondDraw() = scenario {
        alice.battlefield(kingTavari, "Island")
        alice.hand(letters)
        alice.library(bear, bear, ogre)
        alice.casts(letters)
        resolveAll()
        expect { alice.hand.size shouldBe 3 }
    }

    @Test
    fun shadowPantherPreventsDamageToItself() = scenario {
        alice.battlefield(kingTavari, "Island", "Island", "Island", "Island", "Plains", "Plains", "Mountain")
        alice.hand(bolt)
        alice.activates(kingTavari, paying = listOf("Island", "Island", "Island", "Island", "Plains", "Plains"))
        resolveAll()
        alice.casts(bolt, target = kingTavari)
        resolveAll()
        expect { card(kingTavari).view.obj!!.damage shouldBe 0 }
    }

    // ---- Chunk 07: attacking alone and targeting

    @Test
    fun operativeNineInvestigatesForALoneAttacker() = scenario {
        alice.battlefield(operativeNineFieldLiaison)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(operativeNineFieldLiaison)
        resolveAll()
        expect { alice.battlefield has "Clue Token" }
    }

    @Test
    fun crowdOfLoyalFansCheersALoneAttacker() = scenario {
        alice.battlefield(crowdOfLoyalFans, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        alice.activates(crowdOfLoyalFans, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            alice.life shouldBe 21
        }
    }

    @Test
    fun bureauSpyKitUntapsALoneAttacker() = scenario {
        alice.battlefield(bureauSpyKit, bear, "Plains")
        alice.library(ogre)
        alice.activates(bureauSpyKit, target = bear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        chooseNone()
        resolveAll()
        expect {
            bear.tapped shouldBe false
            bear.power shouldBe 3
        }
    }

    @Test
    fun serpentCultInfiltrationStripsAndDrains() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", bear)
        alice.hand(serpentCultInfiltration)
        bob.hand(ogre, elf)
        alice.casts(serpentCultInfiltration)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        if (pending is Decision.ChooseObjects) bob.chooses(ogre, elf)
        resolveAll()
        expect { bob.hand.size shouldBe 0 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect { alice.life shouldBe 21 }
    }

    @Test
    fun crimsonSpiderArmsALoneAttacker() = scenario {
        alice.battlefield(crimsonSpiderDoubleAgent, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect {
            bear hasKeyword Keyword.FirstStrike
            bear hasKeyword Keyword.Menace
        }
    }

    @Test
    fun lucasStoneAloneIsUnbreakable() = scenario {
        alice.battlefield(lucasStoneIronskin)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(lucasStoneIronskin)
        resolveAll()
        expect {
            lucasStoneIronskin.power shouldBe 4
            lucasStoneIronskin hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun larkspurGrowsOnTargetingSpells() = scenario {
        alice.battlefield(larkspurAceAgent, bear, "Plains")
        alice.hand(boost)
        alice.casts(boost, target = bear)
        resolveAll()
        expect { larkspurAceAgent.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun missWondrousDrawsAndGrows() = scenario {
        alice.battlefield(missWondrousKiranShah, "Plains")
        alice.hand(boost, bear, ogre)
        alice.library(elf)
        alice.casts(boost, target = missWondrousKiranShah)
        resolveAll()
        expect {
            alice.hand.size shouldBe 3
            missWondrousKiranShah.power shouldBe 4
        }
    }

    @Test
    fun jadeFistGainsAPing() = scenario {
        alice.battlefield(jadeFistLivingWeapon, "Plains")
        alice.hand(boost)
        alice.casts(boost, target = jadeFistLivingWeapon)
        resolveAll()
        alice.activates(jadeFistLivingWeapon, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun bouncebackGrowsWhenTargeted() = scenario {
        alice.battlefield(bouncebackNewWarrior)
        bob.battlefield("Mountain")
        bob.hand(shock)
        alice.passes()
        bob.casts(shock, target = bouncebackNewWarrior)
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect {
            alice.battlefield has bouncebackNewWarrior
            bouncebackNewWarrior.power shouldBe 4
        }
    }

    @Test
    fun grakThulVorrCopiesArtifactSpells() = scenario {
        alice.battlefield(grakThulVorr, "Mountain")
        alice.hand(shatter)
        bob.battlefield(idol, idol)
        alice.casts(shatter, target = idol)
        resolveAll()
        may(true)
        // the copy's new target: the other Idol (CR 707.10c)
        answer(Answer.Targets(listOf(arcanum.engine.state.TargetRef.Object(card(idol, 1)))))
        resolveAll()
        expect {
            grakThulVorr.counters(p1p1) shouldBe 2
            bob.graveyard.names.count { it == idol } shouldBe 2
        }
    }

    // ---- Chunk 08: white Heroes

    @Test
    fun handlerPhilCorbinCountersHeroes() = scenario {
        alice.battlefield(handlerPhilCorbin, hero, bear)
        alice.activates(handlerPhilCorbin)
        resolveAll()
        expect {
            hero.counters(p1p1) shouldBe 1
            bear.counters(p1p1) shouldBe 0
        }
    }

    @Test
    fun guardiansRallyPumpsAndDraws() = scenario {
        alice.battlefield(guardiansRally, hero)
        alice.library(bear)
        expect { hero.power shouldBe 4 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(hero)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun boroughReinforcementsMakeTwoHeroes() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(boroughReinforcements)
        alice.casts(boroughReinforcements)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Hero Token" } shouldBe 2 }
    }

    @Test
    fun commanderLibertyShieldsHeroes() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", hero)
        alice.hand(commanderLibertySuperSoldier)
        bob.battlefield("Mountain")
        bob.hand(shock)
        alice.casts(commanderLibertySuperSoldier)
        resolveAll()
        expect {
            commanderLibertySuperSoldier.counters(CounterKind.Shield) shouldBe 1
            hero hasKeyword Keyword.Hexproof
        }
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.casts(shock, target = "alice") }
    }
}
