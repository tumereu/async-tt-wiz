package arcanum.engine.sets.sos

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.sos.Sos.bear
import arcanum.engine.sets.sos.Sos.doom
import arcanum.engine.sets.sos.Sos.elf
import arcanum.engine.sets.sos.Sos.giant
import arcanum.engine.sets.sos.Sos.idol
import arcanum.engine.sets.sos.Sos.ogre
import arcanum.engine.sets.sos.Sos.p1p1
import arcanum.engine.sets.sos.Sos.ponder
import arcanum.engine.sets.sos.Sos.shock
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Secrets of Strixhaven's graveyard, casting-and-copying, library-and-hand, removal, trick, charm, Vehicle and land
 * cards in play (parser chunks 09–14). Final Breath Drawn (-3/-3), Stray Away (exile), Speed Reading (draw two), Spirit
 * Dispersal (counter a creature spell), Hold Your Ground and Mentor's Reply's plain destroy, Sharp Interruption (a pump)
 * and Bucking Cindermare (keywords) repeat templates tested before. */
class SosSpellAndLandCardsScenariosTest {
    private val putItOffAgain = Cards.invent("Put It Off Again", "{X}{U}", "Sorcery", "Tap target creature. Put twice X stun counters on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)")
    private val pellHalfDoneProdigy = Cards.inventFaces("Prepare",
        face("Pell, Half-Done Prodigy", "{U}{R}", "Legendary Creature — Goblin Sorcerer", """
            Pell enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            {T}: Create a Treasure token. Activate only if you've cast an instant or sorcery spell this turn.
        """, "0/4", null, null),
        face("Unruly Notion", "{3}{U}{R}", "Sorcery", "Search your library for an instant or sorcery card, reveal it, put it into your hand, then shuffle.", null, null, null),
    )
    private val orrinWatchfulSequencer = Cards.inventFaces("Prepare",
        face("Orrin, Watchful Sequencer", "{2}{G}{U}", "Legendary Creature — Gorgon Wizard", "Landfall — Whenever a land you control enters, Orrin becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)", "4/3", null, null),
        face("Deep Gaze", "{G}{U}", "Sorcery", "You draw a card and gain 1 life.", null, null, null),
    )
    private val aircoachLanding = Cards.invent("Aircoach Landing", null, "Land", """
        {T}: Add {C}.
        {3}, {T}: Target creature becomes prepared. (Only creatures with prepare spells can become prepared.)
    """)
    private val owlfolkChronicler = Cards.invent("Owlfolk Chronicler", "{2}{W}", "Creature — Bird Cleric", """
        Flying
        When this creature enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        Whenever one or more cards leave your graveyard, this creature gets +1/+1 until end of turn.
    """, "2/3")
    private val foundationalInquiry = Cards.invent("Foundational Inquiry", "{4}{W}", "Enchantment", """
        When this enchantment enters, return target nonland permanent card with mana value 3 or less from your graveyard to the battlefield.
        At the beginning of your end step, if a card left your graveyard this turn, draw a card.
    """)
    private val barracksDigger = Cards.invent("Barracks Digger", "{3}{R}", "Creature — Orc Sorcerer", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Whenever one or more cards leave your graveyard, create a 2/2 red and white Spirit creature token.
    """, "3/4")
    private val breathingChronicle = Cards.invent("Breathing Chronicle", "{1}{R}", "Enchantment", """
        When this enchantment enters, create a 2/2 red and white Spirit creature token.
        Whenever you attack, if a card left your graveyard this turn, target attacking creature gets +2/+0 until end of turn.
    """)
    private val vesselOfCraving = Cards.invent("Vessel of Craving", "{2}{R}{W}", "Artifact", """
        Whenever one or more cards leave your graveyard, this artifact deals 1 damage to each opponent and you gain 1 life.
        {T}: Mill a card. You may play that card this turn.
    """)
    private val weatheredAcademic = Cards.invent("Weathered Academic", "{R}{W}", "Creature — Bird Cleric", """
        Flying, haste
        Discard a card: This creature gains lifelink until end of turn.
        Whenever one or more cards leave your graveyard, put a +1/+1 counter on target creature you control.
    """, "2/1")
    private val spiritPennantBearer = Cards.invent("Spirit Pennant-Bearer", "{R}{W}", "Creature — Spirit Ox", "Whenever one or more cards leave your graveyard, put a +1/+1 counter on this creature.", "2/2")
    private val witherInTheSwelter = Cards.invent("Wither in the Swelter", "{2}{R}{W}", "Instant", """
        This spell costs {2} less to cast if one or more cards left your graveyard this turn.
        Wither in the Swelter deals 5 damage to target creature. If that creature would die this turn, exile it instead.
    """)
    private val jumpyRelicSloth = Cards.invent("Jumpy Relic Sloth", "{2}{R}{W}", "Creature — Sloth Beast", """
        Trample, lifelink
        At the beginning of combat on your turn, exile up to one target card from a graveyard.
    """, "4/4")
    private val risingDustcaller = Cards.invent("Rising Dustcaller", "{4}{W}", "Creature — Orc Cleric", """
        Flying
        When this creature enters, put a +1/+1 counter on another target creature you control.
        At the beginning of combat on your turn, exile up to one target card from a graveyard.
    """, "3/4")
    private val brannocDebateSteward = Cards.invent("Brannoc, Debate Steward", "{1}{W}", "Legendary Creature — Human Cleric", """
        When Brannoc enters, exile up to one other target creature you control. Return that card to the battlefield under its owner's control at the beginning of the next end step.
        At the beginning of your end step, if one or more cards were put into exile this turn, put a +1/+1 counter on Brannoc.
    """, "1/1")
    private val statuaryGuide = Cards.invent("Statuary Guide", "{1}{W}", "Creature — Spirit Chimera", "{W}, Exile this card from your graveyard: You gain 2 life. Surveil 1. Activate only as a sorcery. (Look at the top card of your library. You may put it into your graveyard.)", "3/1")
    private val conjuredDromedary = Cards.invent("Conjured Dromedary", "{3}{W}", "Creature — Spirit Camel", """
        Vigilance
        {1}{W}: Return this card from your graveyard to your hand. Activate only as a sorcery.
    """, "4/3")
    private val perpetualPupil = Cards.invent("Perpetual Pupil", "{3}{B}", "Creature — Zombie Warlock", "{1}{B}, Exile this card from your graveyard: Create two 1/1 white and black Inkling creature tokens with flying.", "4/2")
    private val postMortemLecturer = Cards.invent("Post-Mortem Lecturer", "{1}{B}", "Creature — Zombie Warlock", """
        This creature can't block.
        Whenever this creature attacks, each opponent loses 1 life and you gain 1 life.
        {1}{B}, Exile an instant or sorcery card from your graveyard: Return this card from your graveyard to the battlefield.
    """, "2/2")
    private val seasonedScrollwright = Cards.invent("Seasoned Scrollwright", "{R}{R/W}{W}", "Creature — Dwarf Cleric", """
        First strike
        When this creature enters, exile target noncreature, nonland card from your graveyard. Until the end of your next turn, you may cast that card.
    """, "3/2")
    private val ferventLorechanter = Cards.invent("Fervent Lorechanter", "{5}{R}", "Creature — Giant Sorcerer", "When this creature enters, return target instant or sorcery card from your graveyard to your hand.", "4/4")
    private val dragFromTheCrypt = Cards.invent("Drag from the Crypt", "{2}{B}", "Sorcery", "Return up to two target creature cards from your graveyard to your hand. You gain 2 life.")
    private val glidingStoneflier = Cards.invent("Gliding Stoneflier", "{2}{W}", "Creature — Elephant Cleric", """
        As an additional cost to cast this spell, exile two cards from your graveyard or pay {1}{W}.
        Flying, vigilance
    """, "4/3")
    private val debrisAgitator = Cards.invent("Debris Agitator", "{2}{R}", "Creature — Dwarf Sorcerer", """
        When this creature enters, you may discard a card. If you do, draw a card.
        {T}, Exile a card from your graveyard: Add {R}. When you do, this creature deals 1 damage to each opponent.
    """, "1/4")
    private val fieryQuarrel = Cards.invent("Fiery Quarrel", "{4}{R}", "Instant", "Fiery Quarrel deals 6 damage to target creature. You may exile a card from your graveyard. If you do, Fiery Quarrel also deals 2 damage to that creature's controller.")
    private val slabOfFinding = Cards.invent("Slab of Finding", "{2}{R}", "Artifact", """
        When this artifact enters, mill a card. You may play that card this turn. (To mill a card, put the top card of your library into your graveyard.)
        {T}: Add {R}.
        {T}: Add {R}{R}. Spend this mana only to cast instant and sorcery spells.
    """)
    private val teskaReaderOfWreckage = Cards.invent("Teska, Reader of Wreckage", "{3}{R}", "Legendary Creature — Human Artificer", """
        Ward—Pay 3 life. (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays 3 life.)
        Whenever you cast an instant or sorcery spell, you may sacrifice an artifact. If you do, copy that spell and you may choose new targets for the copy.
    """, "4/4")
    private val marisolSpireDuelCaptain = Cards.invent("Marisol, Spire Duel Captain", "{R}{W}", "Legendary Creature — Djinn Sorcerer", "Whenever you cast an instant or sorcery spell, you may tap three untapped creatures you control. If you do, copy that spell. You may choose new targets for the copy.", "2/2")
    private val coldShoulder = Cards.invent("Cold Shoulder", "{1}{W}{B}", "Sorcery", """
        When you cast this spell while you control a creature, you may copy this spell.
        Each player sacrifices a creature of their choice. Each opponent loses 1 life and you gain 1 life.
    """)
    private val odalysAgoraPeacemaker = Cards.invent("Odalys, Agora Peacemaker", "{1}{W}{B}", "Legendary Creature — Human Advisor", """
        Whenever you cast a spell you don't own, put a +1/+1 counter on each creature you control.
        {2}, Sacrifice another creature: Exile target instant or sorcery card from an opponent's graveyard. You may cast it this turn, and mana of any type can be spent to cast that spell. If that spell would be put into a graveyard, exile it instead. Activate only as a sorcery.
    """, "2/3")
    private val hueflareTheMuse = Cards.invent("Hueflare, the Muse", "{5}{U}{R}", "Legendary Creature — Elder Dragon", """
        Flying
        Ward—Pay 5 life.
        Instant and sorcery spells you cast have storm. (Whenever you cast an instant or sorcery spell, copy it for each spell cast before it this turn. You may choose new targets for the copies.)
    """, "7/7")
    private val tesseractTheTheorem = Cards.invent("Tesseract, the Theorem", "{4}{G}{U}", "Legendary Creature — Elder Dragon", """
        Flying, trample
        Cascade (When you cast this spell, exile cards from the top of your library until you exile a nonland card that costs less. You may cast it without paying its mana cost. Put the exiled cards on the bottom in a random order.)
        Instant and sorcery spells you cast from your hand have cascade.
    """, "6/6")
    private val rotbloomTheEquilibrist = Cards.invent("Rotbloom, the Equilibrist", "{6}{B}{G}", "Legendary Creature — Elder Dragon", """
        Affinity for creatures (This spell costs {1} less to cast for each creature you control.)
        Flying, deathtouch
        Instant and sorcery spells you cast have affinity for creatures.
    """, "5/5")
    private val abstractedColormage = Cards.invent("Abstracted Colormage", "{U}{U/R}{R}", "Creature — Djinn Sorcerer", "At the beginning of your first main phase, add {U}{R}. Spend this mana only to cast instant and sorcery spells.", "2/2")
    private val echoingCittern = Cards.invent("Echoing Cittern", "{2}{U}{R}", "Artifact", """
        Lands you control have "{T}: Add two mana of any one color. Spend this mana only to cast instant and sorcery spells."
        {T}: Draw a card. Activate only if you have seven or more cards in your hand.
    """)
    private val brewersHoard = Cards.invent("Brewer's Hoard", "{3}", "Artifact", """
        {T}: Add one mana of any color.
        {T}: You gain 2 life. Activate only if you've cast an instant or sorcery spell this turn.
    """)
    private val journalOfReveries = Cards.invent("Journal of Reveries", "{2}", "Artifact — Book", """
        Whenever you cast an instant or sorcery spell, put a page counter on this artifact.
        {5}, {T}: Draw a card. This ability costs {1} less to activate for each page counter on this artifact.
    """)
    private val folioStraySheet = Cards.invent("Folio, Stray Sheet", "{2}", "Legendary Artifact Creature — Construct", """
        {T}: Add {C}.
        Grandeur — Discard another card named Folio, Stray Sheet: Reveal cards from the top of your library until you reveal an instant or sorcery card. Put that card into your hand and the rest on the bottom of your library in a random order.
    """, "0/2")
    private val wardensAssurance = Cards.invent("Warden's Assurance", "{W}{B}", "Sorcery", """
        Target creature gets +1/+1 until end of turn. Draw a card.
        Whenever one or more creatures you control deal combat damage to a player, you may pay {W/B}. If you do, return this card from your graveyard to your hand.
    """)
    private val merrinSurfChoreographer = Cards.invent("Merrin, Surf Choreographer", "{4}{U}", "Legendary Creature — Merfolk Bard", """
        This spell costs {3} less to cast if creatures you control have total toughness 10 or greater.
        When Merrin enters, draw two cards.
    """, "2/2")
    private val steadyCurrent = Cards.invent("Steady Current", "{1}{U}", "Sorcery", "Look at the top three cards of your library. Put one of them into your hand and the rest on the bottom of your library in any order. If there is an instant card and a sorcery card in your graveyard, instead put two of them into your hand and the rest on the bottom of your library in any order.")
    private val veskasTrialRun = Cards.invent("Veska's Trial Run", "{3}{G}", "Sorcery", "Look at the top five cards of your library. You may reveal up to two creature and/or land cards from among them, then put the rest on the bottom of your library in a random order. Put all land cards revealed this way onto the battlefield tapped and put all creature cards revealed this way into your hand.")
    private val paradoxCartographer = Cards.invent("Paradox Cartographer", "{G}{G/U}{U}", "Creature — Elf Druid", """
        Reach
        When this creature enters, look at the top five cards of your library. You may reveal a land card or a card with {X} in its mana cost from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
    """, "3/3")
    private val rousingHonorchanter = Cards.invent("Rousing Honorchanter", "{2}{W}{W/B}{B}", "Creature — Rhino Bard", "When this creature enters, look at the top X cards of your library, where X is the number of creatures you control. Put one of those cards into your hand and the rest into your graveyard.", "4/5")
    private val anxiousSlumber = Cards.invent("Anxious Slumber", "{3}{U}{R}", "Instant", "Anxious Slumber deals 5 damage to up to one target creature. Look at the top two cards of your library. Put one of those cards into your hand and the other on the bottom of your library.")
    private val seersWaltz = Cards.invent("Seer's Waltz", "{5}{U}{R}", "Sorcery", """
        Create two 3/3 blue and red Elemental creature tokens with flying.
        {2}, Discard this card: Look at the top two cards of your library. Put one of them into your hand and the other into your graveyard.
    """)
    private val mothersCounsel = Cards.invent("Mother's Counsel", "{1}{B}{G}", "Instant", "Search your library for a creature card, reveal it, put it into your hand or graveyard, then shuffle.")
    private val thoughtTaproot = Cards.invent("Thought Taproot", "{1}{B}{G}", "Sorcery", "Target player discards two cards. Put up to one land card discarded this way onto the battlefield tapped under your control.")
    private val notionIntoSubstance = Cards.invent("Notion into Substance", "{X}{G}{U}", "Sorcery", "Draw X cards. Then you may put a permanent card with mana value X or less from your hand onto the battlefield tapped.")
    private val lentWisdom = Cards.invent("Lent Wisdom", "{2}{R}{W}", "Sorcery", """
        Choose one —
        • Discard your hand, then draw cards equal to the number of cards in target opponent's hand.
        • Discard your hand, then draw cards equal to the number of cards discarded this way.
    """)
    private val colossusOfTheGoreEra = Cards.invent("Colossus of the Gore Era", "{4}{R}{W}", "Artifact Creature — Construct", """
        When this creature enters, it deals 3 damage to each opponent and you gain 3 life.
        When this creature dies, discard any number of cards, then draw that many cards plus one.
    """, "6/6")
    private val upstageTheLecture = Cards.invent("Upstage the Lecture", "{2}{R}", "Sorcery", """
        Choose one or both —
        • Target player discards any number of cards, then draws that many cards.
        • Upstage the Lecture deals damage equal to the number of instant and sorcery cards in your graveyard to target creature or planeswalker.
    """)
    private val strikeDumb = Cards.invent("Strike Dumb", "{2}{W}{B}", "Sorcery", """
        Target opponent reveals their hand. You choose a nonland card from it. That player discards that card.
        Put two +1/+1 counters on up to one target creature.
    """)
    private val grabTheLoot = Cards.invent("Grab the Loot", "{2}{R}", "Sorcery", """
        As an additional cost to cast this spell, discard a card.
        Draw two cards and create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """)
    private val ecstaticInstant = Cards.invent("Ecstatic Instant", "{4}{U}{R}", "Sorcery", "Draw three cards, then discard two cards. Add {U}{U}{R}{R}{R}.")
    private val acceptTheContradiction = Cards.invent("Accept the Contradiction", "{3}{G}{U}", "Instant", "Draw three cards. You may put a land card from your hand onto the battlefield tapped.")
    private val arenaTidecaller = Cards.invent("Arena Tidecaller", "{2}{U}{R}", "Creature — Djinn Sorcerer", "Whenever this creature enters or attacks, you may draw a card. If you do, discard a card.", "4/4")
    private val rushingFeudknight = Cards.invent("Rushing Feudknight", "{2}{R}", "Creature — Spirit Knight", """
        Haste
        {T}, Discard a card: Draw a card.
    """, "3/3")
    private val musesUrging = Cards.invent("Muse's Urging", "{4}{U}", "Instant", """
        Create a 3/3 blue and red Elemental creature token with flying.
        Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
    """)
    private val scarringReview = Cards.invent("Scarring Review", "{X}{U}{R}", "Instant", "Scarring Review deals X damage to any target. Draw two cards, then discard a card.")
    private val landscapeReworking = Cards.invent("Landscape Reworking", "{3}{G}", "Sorcery", "Sacrifice two lands. Search your library for four basic land cards, put them onto the battlefield tapped, then shuffle.")
    private val ecologyResearcher = Cards.invent("Ecology Researcher", "{1}{G}", "Creature — Human Druid", "When this creature enters, you may search your library for a basic land card, reveal it, put it into your hand, then shuffle.", "2/2")
    private val mentorsReply = Cards.invent("Mentor's Reply", "{4}{W}", "Instant", """
        This spell costs {3} less to cast if it targets a tapped creature.
        Destroy target creature.
    """)
    private val wearAwayToSand = Cards.invent("Wear Away to Sand", "{W}", "Instant", "Destroy target creature or planeswalker. Its controller may search their library for a basic land card, put it onto the battlefield tapped, then shuffle.")
    private val holdYourGround = Cards.invent("Hold Your Ground", "{2}{W}", "Instant", "Destroy target creature with power 3 or greater.")
    private val splinteredNovice = Cards.invent("Splintered Novice", "{1}{W}", "Creature — Dwarf Warlock", """
        Lifelink
        {1}, Sacrifice this creature: Destroy target artifact or enchantment.
    """, "2/2")
    private val exilingTreachery = Cards.invent("Exiling Treachery", "{1}{U}", "Instant", "Return target nonland permanent to its owner's hand. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)")
    private val waveAside = Cards.invent("Wave Aside", "{2}{U}{U}", "Instant", """
        This spell costs {1}{U} less to cast if it targets an instant or sorcery spell.
        Counter target spell.
    """)
    private val pursueTheMuse = Cards.invent("Pursue the Muse", "{U}", "Instant", "Target creature you control gets +0/+3 and gains hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)")
    private val fallBehindSchedule = Cards.invent("Fall Behind Schedule", "{3}{U}", "Instant", """
        This spell costs {1} less to cast if it targets an attacking creature.
        Target creature's owner puts it on their choice of the top or bottom of their library.
    """)
    private val priceOfGenius = Cards.invent("Price of Genius", "{2}{B}", "Sorcery", "Target player draws two cards and loses 2 life. Put a +1/+1 counter on up to one target creature.")
    private val anatomyDrill = Cards.invent("Anatomy Drill", "{B}", "Instant", """
        Target opponent loses 1 life and you gain 1 life.
        Up to one target creature gets +1/+1 until end of turn.
        Up to one target creature gets -1/-1 until end of turn.
    """)
    private val virtuosoFlourish = Cards.invent("Virtuoso Flourish", "{B}", "Instant", "Target creature you control gets +1/+0 and gains indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)")
    private val burrogDoomcrafter = Cards.invent("Burrog Doomcrafter", "{B}", "Creature — Frog Warlock", """
        Deathtouch
        {1}{B}: This creature gets +1/+1 until end of turn.
    """, "1/1")
    private val forebearsWrath = Cards.invent("Forebears' Wrath", "{R}", "Sorcery", """
        Target creature gains trample and gets +X/+0 until end of turn, where X is 1 plus the number of cards named Forebears' Wrath in your graveyard.
        Draw a card.
    """)
    private val creativeMethod = Cards.invent("Creative Method", "{3}{R}{R}", "Sorcery", """
        Choose one —
        • Creative Method deals 6 damage to target creature.
        • Creative Method deals 2 damage to each creature you don't control.
        • Create a 3/3 blue and red Elemental creature token with flying. It gains haste until end of turn.
    """)
    private val illTimedJest = Cards.invent("Ill-Timed Jest", "{R}", "Sorcery", "Damage can't be prevented this turn. Ill-Timed Jest deals 3 damage to up to one target creature or planeswalker.")
    private val blatantRidicule = Cards.invent("Blatant Ridicule", "{2}{R}", "Instant", "Blatant Ridicule deals 4 damage to target creature. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)")
    private val burrogBombardment = Cards.invent("Burrog Bombardment", "{1}{G}", "Instant", "Target creature you control gets +1/+0 until end of turn if you've cast another instant or sorcery spell this turn. Then it deals damage equal to its power to up to one target creature an opponent controls.")
    private val tortoiseTakedown = Cards.invent("Tortoise Takedown", "{2}{G}", "Sorcery", "Target creature you control gets +0/+10 until end of turn. Then it fights up to one target creature an opponent controls. (Each deals damage equal to its power to the other.)")
    private val splendidRot = Cards.invent("Splendid Rot", "{1}{G}", "Instant", """
        Choose one —
        • Destroy target artifact.
        • Splendid Rot deals 4 damage to target creature with flying.
        • Exile target card from a graveyard. Draw a card.
    """)
    private val seersRenewal = Cards.invent("Seer's Renewal", "{G}", "Sorcery", "Target creature you control gets +1/+1 until end of turn. You draw a card and gain 1 life.")
    private val frenziedAssault = Cards.invent("Frenzied Assault", "{1}{B}", "Instant", "Until end of turn, any number of target creatures you control each get +1/+0 and gain \"When this creature dies, draw a card.\"")
    private val rootbindingCraft = Cards.invent("Rootbinding Craft", "{3}{B}{G}", "Sorcery", "Until end of turn, creatures you control get +2/+2 and gain menace and \"Whenever this creature attacks, you gain 1 life.\" (A creature with menace can't be blocked except by two or more creatures.)")
    private val emberloreTrinket = Cards.invent("Emberlore Trinket", "{R}{W}", "Instant", """
        Choose one —
        • Each opponent sacrifices a nontoken artifact of their choice.
        • Return target artifact or creature card with mana value 2 or less from your graveyard to the battlefield.
        • Creatures you control get +1/+1 and gain trample until end of turn.
    """)
    private val huecraftTrinket = Cards.invent("Huecraft Trinket", "{U}{R}", "Instant", """
        Choose one —
        • Surveil 2, then draw a card.
        • Huecraft Trinket deals 1 damage to each of one or two targets.
        • Return target nonland permanent to its owner's hand.
    """)
    private val tessellateTrinket = Cards.invent("Tessellate Trinket", "{G}{U}", "Instant", """
        Choose one —
        • Counter target spell unless its controller pays {2}.
        • Destroy target enchantment.
        • Target creature has base power and toughness 5/5 until end of turn.
    """)
    private val inkquillTrinket = Cards.invent("Inkquill Trinket", "{W}{B}", "Instant", """
        Choose one —
        • Put two +1/+1 counters on target creature.
        • Exile target creature with power 2 or less.
        • Each opponent loses 3 life and you gain 3 life.
    """)
    private val rotbloomTrinket = Cards.invent("Rotbloom Trinket", "{B}{G}", "Instant", """
        Choose one —
        • You may sacrifice a permanent. If you do, draw two cards.
        • You gain 5 life.
        • Destroy target nonland permanent with mana value 2 or less.
    """)
    private val wrestleWithMortality = Cards.invent("Wrestle with Mortality", "{1}{B}{G}", "Sorcery", "Destroy target artifact or creature. You gain 1 life.")
    private val examinersGlance = Cards.invent("Examiner's Glance", "{2}{G}{U}", "Instant", "Return up to one target nonland permanent to its owner's hand. Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.")
    private val spatterMethod = Cards.invent("Spatter Method", "{1}{U}{U}{R}{R}", "Sorcery", """
        Choose one —
        • Draw four cards.
        • Spatter Method deals 4 damage to each creature and planeswalker.
    """)
    private val vividEruption = Cards.invent("Vivid Eruption", "{U}{R}", "Instant", "Vivid Eruption deals 3 damage to any target. Tap up to one target creature.")
    private val academyAircoach = Cards.invent("Academy Aircoach", "{3}", "Artifact — Vehicle", """
        Flying
        When this Vehicle enters, you may search your library for a basic land card, reveal it, put it into your hand, then shuffle.
        Crew 2 (Tap any number of creatures you control with total power 2 or more: This Vehicle becomes an artifact creature until end of turn.)
    """, "3/2")
    private val ossifiedVillage = Cards.invent("Ossified Village", null, "Land", """
        When this land enters, choose a land card name.
        Activated abilities of sources with the chosen name can't be activated unless they're mana abilities.
        Lands with the chosen name have "{T}: Add {C}."
        {T}: Add {C}.
    """)
    private val mushroomHollow = Cards.invent("Mushroom Hollow", null, "Land", """
        This land enters tapped unless you control two or more other lands.
        {T}: Add {B} or {G}.
    """)
    private val meadowsOfContention = Cards.invent("Meadows of Contention", null, "Land", """
        This land enters tapped.
        {T}: Add {R} or {W}.
        {2}{R}{W}, {T}: Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
    """)

    private val purge = Cards.invent("Quad Purge", "{R}", "Instant", "Exile target card from a graveyard.")
    private val counterspell = Cards.invent("Quad Refusal", "{U}{U}", "Instant", "Counter target spell.")
    private val lesson = Cards.invent("Quad Lesson", "{1}{U}", "Sorcery", "Draw a card.")
    private val pump = Cards.invent("Quad Vigor", "{G}", "Instant", "Target creature gets +1/+1 until end of turn.")

    // ---- Chunk 09: preparation, the rest

    @Test
    fun pellMakesTreasureAfterASpell() = scenario {
        alice.battlefield(pellHalfDoneProdigy, "Island")
        alice.hand(ponder)
        alice.library(bear)
        assertFailsWith<IllegalArgumentException> { alice.activates(pellHalfDoneProdigy) }
        alice.casts(ponder)
        resolveAll()
        alice.activates(pellHalfDoneProdigy)
        resolveAll()
        expect { alice.battlefield has "Treasure Token" }
    }

    @Test
    fun pellSeeksASpell() = scenario {
        alice.battlefield("Island", "Mountain", "Island", "Island", "Island", "Mountain", "Mountain")
        alice.hand(pellHalfDoneProdigy)
        alice.library(bear, shock)
        alice.casts("Pell, Half-Done Prodigy", paying = listOf("Island", "Mountain"))
        resolveAll()
        alice.casts("Unruly Notion")
        resolveAll()
        chooseIfOffered(shock)
        resolveAll()
        expect { alice.hand has shock }
    }

    @Test
    fun orrinPreparesOnLandfall() = scenario {
        alice.battlefield(orrinWatchfulSequencer, "Forest", "Island")
        alice.hand("Forest")
        alice.library(bear)
        alice.plays("Forest")
        resolveAll()
        alice.casts("Deep Gaze")
        resolveAll()
        expect {
            alice.hand has bear
            alice.life shouldBe 21
        }
    }

    @Test
    fun aircoachLandingPreparesACreature() = scenario {
        alice.battlefield(aircoachLanding, orrinWatchfulSequencer, "Plains", "Plains", "Plains")
        alice.activates(aircoachLanding, ability = 1, target = orrinWatchfulSequencer)
        resolveAll()
        assertTrue(prepared(orrinWatchfulSequencer))
    }

    // ---- Chunk 10: graveyards

    @Test
    fun owlfolkChroniclerGrowsWhenCardsLeave() = scenario {
        alice.battlefield(owlfolkChronicler, "Mountain")
        alice.hand(purge)
        alice.graveyard(bear)
        alice.casts(purge, target = bear)
        resolveAll()
        expect { owlfolkChronicler.power shouldBe 3 }
    }

    @Test
    fun foundationalInquiryReturnsAndDraws() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(foundationalInquiry)
        alice.graveyard(bear)
        alice.library(ogre)
        alice.casts(foundationalInquiry)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield has bear
            alice.hand has ogre
        }
    }

    @Test
    fun barracksDiggerMakesSpirits() = scenario {
        alice.battlefield(barracksDigger, "Mountain")
        alice.hand(purge)
        alice.graveyard(bear)
        alice.casts(purge, target = bear)
        resolveAll()
        expect { alice.battlefield has "Spirit Token" }
    }

    @Test
    fun breathingChronicleSpiritAndAttackBonus() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", bear)
        alice.hand(breathingChronicle, purge)
        alice.graveyard(elf)
        alice.casts(breathingChronicle, paying = listOf("Mountain", "Mountain"))
        resolveAll()
        alice.casts(purge, target = elf)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            alice.battlefield has "Spirit Token"
            bear.power shouldBe 4
        }
    }

    @Test
    fun vesselOfCravingDrainsAndMills() = scenario {
        alice.battlefield(vesselOfCraving, "Mountain")
        alice.hand(purge)
        alice.graveyard(bear)
        alice.library(ogre)
        alice.casts(purge, target = bear)
        resolveAll()
        expect { bob.life shouldBe 19 }
        alice.activates(vesselOfCraving)
        resolveAll()
        expect { alice.graveyard has ogre }
    }

    @Test
    fun weatheredAcademicCountersWhenCardsLeave() = scenario {
        alice.battlefield(weatheredAcademic, "Mountain", bear)
        alice.hand(purge, ogre)
        alice.graveyard(elf)
        alice.casts(purge, target = elf)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
        alice.activates(weatheredAcademic, choosing = listOf(ogre))
        resolveAll()
        expect { weatheredAcademic hasKeyword Keyword.Lifelink }
    }

    @Test
    fun spiritPennantBearerGrows() = scenario {
        alice.battlefield(spiritPennantBearer, "Mountain")
        alice.hand(purge)
        alice.graveyard(bear)
        alice.casts(purge, target = bear)
        resolveAll()
        expect { spiritPennantBearer.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun witherInTheSwelterCheapAfterAGraveyardLeave() = scenario {
        alice.battlefield("Mountain", "Plains", "Mountain")
        alice.hand(purge, witherInTheSwelter)
        alice.graveyard(bear)
        bob.battlefield(giant)
        alice.casts(purge, target = bear, paying = listOf("Mountain"))
        resolveAll()
        alice.casts(witherInTheSwelter, target = giant)
        resolveAll()
        expect { bob.exile has giant }
    }

    @Test
    fun jumpyRelicSlothExilesAtCombat() = scenario {
        alice.battlefield(jumpyRelicSloth)
        bob.graveyard(bear)
        advanceTo(TurnStep.BeginningOfCombat)
        targetsIfAsked(bear)
        resolveAll()
        expect { bob.exile has bear }
    }

    @Test
    fun risingDustcallerCountersAndExiles() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", bear)
        alice.hand(risingDustcaller)
        bob.graveyard(ogre)
        alice.casts(risingDustcaller)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
        advanceTo(TurnStep.BeginningOfCombat)
        targetsIfAsked(ogre)
        resolveAll()
        expect { bob.exile has ogre }
    }

    @Test
    fun brannocBlinksAndGrows() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(brannocDebateSteward)
        alice.casts(brannocDebateSteward)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield has bear
            brannocDebateSteward.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun statuaryGuideFromTheGraveyard() = scenario {
        alice.battlefield("Plains")
        alice.graveyard(statuaryGuide)
        alice.library(bear)
        alice.activates(statuaryGuide)
        resolveAll()
        chooseNone()
        resolveAll()
        expect {
            alice.life shouldBe 22
            alice.exile has statuaryGuide
        }
    }

    @Test
    fun conjuredDromedaryReturns() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.graveyard(conjuredDromedary)
        alice.activates(conjuredDromedary)
        resolveAll()
        expect { alice.hand has conjuredDromedary }
    }

    @Test
    fun perpetualPupilLeavesInklings() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard(perpetualPupil)
        alice.activates(perpetualPupil)
        resolveAll()
        expect { alice.battlefield.names.count { it == "Inkling Token" } shouldBe 2 }
    }

    @Test
    fun postMortemLecturerDrainsAndReturns() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard(postMortemLecturer, shock)
        alice.activates(postMortemLecturer, choosing = listOf(shock))
        resolveAll()
        expect { alice.battlefield has postMortemLecturer }
    }

    @Test
    fun seasonedScrollwrightReplaysASpell() = scenario {
        alice.battlefield("Mountain", "Mountain", "Plains", "Mountain")
        alice.hand(seasonedScrollwright)
        alice.graveyard(shock)
        alice.casts(seasonedScrollwright, paying = listOf("Mountain", "Mountain", "Plains"))
        resolveAll()
        targetsIfAsked(shock)
        resolveAll()
        alice.casts(shock, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun ferventLorechanterRegrowsASpell() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(ferventLorechanter)
        alice.graveyard(shock)
        alice.casts(ferventLorechanter)
        resolveAll()
        targetsIfAsked(shock)
        resolveAll()
        expect { alice.hand has shock }
    }

    @Test
    fun dragFromTheCryptReturnsTwo() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(dragFromTheCrypt)
        alice.graveyard(bear, ogre)
        alice.casts(dragFromTheCrypt, targets = listOf(bear, ogre))
        resolveAll()
        expect {
            alice.hand has bear
            alice.hand has ogre
            alice.life shouldBe 22
        }
    }

    @Test
    fun glidingStonefliersExtraCostByExile() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(glidingStoneflier)
        alice.graveyard(bear, elf)
        alice.casts(glidingStoneflier, option = "Exile", choosing = listOf(bear, elf))
        resolveAll()
        expect {
            alice.battlefield has glidingStoneflier
            alice.exile has bear
        }
    }

    @Test
    fun debrisAgitatorRummagesAndPings() = scenario {
        alice.battlefield(debrisAgitator)
        alice.graveyard(bear)
        alice.activates(debrisAgitator, ability = 0, choosing = listOf(bear))
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.pool.size shouldBe 1
        }
    }

    @Test
    fun fieryQuarrelBurnsAndPunishes() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(fieryQuarrel)
        alice.graveyard(bear)
        bob.battlefield(giant)
        alice.casts(fieryQuarrel, target = giant)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(bear)
        resolveAll()
        expect {
            bob.graveyard has giant
            bob.life shouldBe 18
        }
    }

    @Test
    fun slabOfFindingMillsAPlayableCard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Forest", "Forest")
        alice.hand(slabOfFinding)
        alice.library(bear)
        alice.casts(slabOfFinding, paying = listOf("Mountain", "Mountain", "Mountain"))
        resolveAll()
        alice.casts(bear)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    // ---- Chunk 11: casting and copying

    @Test
    fun teskaCopiesForAnArtifact() = scenario {
        alice.battlefield(teskaReaderOfWreckage, idol, "Mountain")
        alice.hand(shock)
        alice.casts(shock, target = "bob")
        resolveAll()
        may(true)
        chooseIfOffered(idol)
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    @Test
    fun marisolCopiesForThreeTapped() = scenario {
        alice.battlefield(marisolSpireDuelCaptain, bear, elf, ogre, "Mountain")
        alice.hand(shock)
        alice.casts(shock, target = "bob")
        resolveAll()
        may(true)
        if (pending is Decision.ChooseObjects) alice.chooses(bear, elf, ogre)
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    @Test
    fun coldShoulderCopiedWithACreature() = scenario {
        alice.battlefield("Plains", "Swamp", "Swamp", elf, bear)
        alice.hand(coldShoulder)
        bob.battlefield(ogre, giant)
        alice.casts(coldShoulder)
        resolveAll()
        if (pending is Decision.May) may(true)
        repeat(6) {
            resolveAll()
            (pending as? Decision.ChooseObjects)?.let { d -> answer(Answer.Objects(listOf(d.options.first()))) }
        }
        resolveAll()
        expect {
            bob.life shouldBe 18
            bob.battlefield.size shouldBe 0
        }
    }

    @Test
    fun odalysCastsTheirSpell() = scenario {
        alice.battlefield(odalysAgoraPeacemaker, elf, bear, "Plains", "Plains", "Plains")
        bob.graveyard(shock)
        alice.activates(odalysAgoraPeacemaker, target = shock, choosing = listOf(elf))
        resolveAll()
        alice.casts(shock, target = "bob")
        resolveAll()
        expect {
            bob.life shouldBe 18
            bear.counters(p1p1) shouldBe 1
            bob.exile has shock
        }
    }

    // CR 702.40a: storm
    @Test
    fun hueflareGivesStorm() = scenario {
        alice.battlefield(hueflareTheMuse, "Island", "Mountain")
        alice.hand(ponder, shock)
        alice.library(bear)
        alice.casts(ponder)
        resolveAll()
        alice.casts(shock, target = "bob")
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    // CR 702.85a: cascade
    @Test
    fun tesseractCascades() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Island", "Island")
        alice.hand(tesseractTheTheorem)
        alice.library("Forest", bear, ogre)
        alice.casts(tesseractTheTheorem)
        resolveAll()
        if (pending is Decision.May) may(true)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    @Test
    fun rotbloomCheaperByCreatures() = scenario {
        alice.battlefield(bear, elf, ogre, giant, "Swamp", "Forest", "Swamp", "Forest")
        alice.hand(rotbloomTheEquilibrist)
        alice.casts(rotbloomTheEquilibrist)
        resolveAll()
        expect { alice.battlefield has rotbloomTheEquilibrist }
    }

    @Test
    fun abstractedColormageAddsSpellMana() = scenario {
        alice.battlefield(abstractedColormage)
        alice.library(bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        resolveAll()
        expect { alice.pool.size shouldBe 2 }
    }

    @Test
    fun echoingCitternDoublesLandsForSpells() = scenario {
        alice.battlefield(echoingCittern, "Island")
        alice.hand(lesson)
        alice.library(bear)
        alice.casts(lesson)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun brewersHoardGainsAfterASpell() = scenario {
        alice.battlefield(brewersHoard, "Island")
        alice.hand(ponder)
        alice.library(bear)
        alice.casts(ponder)
        resolveAll()
        alice.activates(brewersHoard, ability = 1)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    @Test
    fun journalOfReveriesCheaperByPages() = scenario {
        alice.battlefield(journalOfReveries, counters = listOf(CounterKind.Named("page") to 4))
        alice.battlefield("Island")
        alice.library(bear)
        alice.activates(journalOfReveries)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun journalOfReveriesAddsPages() = scenario {
        alice.battlefield(journalOfReveries, "Island")
        alice.hand(ponder)
        alice.library(bear)
        alice.casts(ponder)
        resolveAll()
        expect { journalOfReveries.counters(CounterKind.Named("page")) shouldBe 1 }
    }

    @Test
    fun folioGrandeurFindsASpell() = scenario {
        alice.battlefield(folioStraySheet)
        alice.hand(folioStraySheet)
        alice.library(bear, shock)
        alice.activates(folioStraySheet, ability = 1, choosing = listOf(folioStraySheet))
        resolveAll()
        expect { alice.hand has shock }
    }

    @Test
    fun wardensAssuranceReturnsOnCombatDamage() = scenario {
        alice.battlefield("Plains", "Swamp", "Plains", bear)
        alice.hand(wardensAssurance)
        alice.library(elf)
        alice.casts(wardensAssurance, target = bear, paying = listOf("Plains", "Swamp"))
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        may(true)
        payIfAsked()
        resolveAll()
        expect { alice.hand has wardensAssurance }
    }

    // ---- Chunk 12: libraries and hands

    @Test
    fun merrinCheapWithToughCreatures() = scenario {
        alice.battlefield(giant, giant, "Island", "Island")
        alice.hand(merrinSurfChoreographer)
        alice.library(bear, bear)
        alice.casts(merrinSurfChoreographer)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun steadyCurrentTakesTwoWithBothKinds() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(steadyCurrent)
        alice.graveyard(shock, ponder)
        alice.library(bear, ogre, elf)
        alice.casts(steadyCurrent)
        resolveAll()
        alice.chooses(bear, ogre)
        resolveAll()
        (pending as? Decision.ChooseObjects)?.let { answer(Answer.Objects(it.options)) }
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun veskasTrialRunSplitsLandsAndCreatures() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(veskasTrialRun)
        alice.library("Island", bear, shock, ogre, elf)
        alice.casts(veskasTrialRun)
        resolveAll()
        alice.chooses("Island", bear)
        resolveAll()
        expect {
            alice.battlefield has "Island"
            alice.hand has bear
        }
    }

    @Test
    fun paradoxCartographerFindsAnXCard() = scenario {
        alice.battlefield("Forest", "Island", "Island")
        alice.hand(paradoxCartographer)
        alice.library(bear, ogre, putItOffAgain)
        alice.casts(paradoxCartographer)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(putItOffAgain)
        resolveAll()
        expect { alice.hand has putItOffAgain }
    }

    @Test
    fun rousingHonorchanterDigsByCreatures() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Swamp", "Swamp", bear)
        alice.hand(rousingHonorchanter)
        alice.library(ogre, elf, giant)
        alice.casts(rousingHonorchanter)
        resolveAll()
        chooseIfOffered(elf)
        resolveAll()
        expect {
            alice.hand has elf
            alice.graveyard has ogre
        }
    }

    @Test
    fun anxiousSlumberBurnsAndSelects() = scenario {
        alice.battlefield("Island", "Island", "Island", "Mountain", "Mountain")
        alice.hand(anxiousSlumber)
        alice.library(bear, ogre)
        bob.battlefield(giant)
        alice.casts(anxiousSlumber, target = giant)
        resolveAll()
        chooseIfOffered(ogre)
        resolveAll()
        expect {
            bob.graveyard has giant
            alice.hand has ogre
        }
    }

    @Test
    fun seersWaltzDiscardedSelects() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(seersWaltz)
        alice.library(bear, ogre)
        alice.activates(seersWaltz)
        resolveAll()
        chooseIfOffered(bear)
        resolveAll()
        expect {
            alice.hand has bear
            alice.graveyard has ogre
        }
    }

    @Test
    fun mothersCounselBinsACreature() = scenario {
        alice.battlefield("Swamp", "Forest", "Forest")
        alice.hand(mothersCounsel)
        alice.library(bear, giant)
        alice.casts(mothersCounsel)
        resolveAll()
        alice.chooses(giant)
        resolveAll()
        if (pending is Decision.ChooseOption) option("graveyard")
        resolveAll()
        expect { alice.graveyard has giant }
    }

    @Test
    fun thoughtTaprootStealsALand() = scenario {
        alice.battlefield("Swamp", "Forest", "Forest")
        alice.hand(thoughtTaproot)
        bob.hand("Island", bear)
        alice.casts(thoughtTaproot, target = "bob")
        resolveAll()
        chooseIfOffered("Island")
        resolveAll()
        expect { alice.battlefield has "Island" }
    }

    @Test
    fun notionIntoSubstanceDrawsAndDrops() = scenario {
        alice.battlefield("Forest", "Island", "Island", "Island")
        alice.hand(notionIntoSubstance)
        alice.library(bear, elf)
        alice.casts(notionIntoSubstance, x = 2)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered(bear)
        resolveAll()
        expect { bear.tapped shouldBe true }
    }

    @Test
    fun lentWisdomRedrawsTheirCount() = scenario {
        alice.battlefield("Mountain", "Plains", "Mountain", "Mountain")
        alice.hand(lentWisdom, bear)
        alice.library(elf, elf, elf)
        bob.hand(ogre, ogre, ogre)
        alice.casts(lentWisdom, modes = listOf(0), target = "bob")
        resolveAll()
        expect {
            alice.hand.size shouldBe 3
            alice.graveyard has bear
        }
    }

    @Test
    fun colossusOfTheGoreEraDrainsAndRedraws() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Plains", "Plains")
        alice.hand(colossusOfTheGoreEra)
        alice.casts(colossusOfTheGoreEra)
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    @Test
    fun upstageTheLectureBurnsBySpells() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(upstageTheLecture)
        alice.graveyard(shock, ponder, shock)
        bob.battlefield(ogre)
        alice.casts(upstageTheLecture, modes = listOf(1), target = ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun strikeDumbStripsAndCounters() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp", bear)
        alice.hand(strikeDumb)
        bob.hand(ogre, "Forest")
        alice.casts(strikeDumb, targets = listOf("bob", bear))
        resolveAll()
        chooseIfOffered(ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            bear.counters(p1p1) shouldBe 2
        }
    }

    @Test
    fun grabTheLootDrawsAndTreasures() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(grabTheLoot, ogre)
        alice.library(bear, bear)
        alice.casts(grabTheLoot, choosing = listOf(ogre))
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            alice.battlefield has "Treasure Token"
        }
    }

    @Test
    fun ecstaticInstantRitualDraws() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Mountain", "Mountain")
        alice.hand(ecstaticInstant)
        alice.library(bear, ogre, elf)
        alice.casts(ecstaticInstant)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear, ogre)
        resolveAll()
        expect {
            alice.pool.size shouldBe 5
            alice.hand.size shouldBe 1
        }
    }

    @Test
    fun acceptTheContradictionDrawsAndDrops() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Island", "Island")
        alice.hand(acceptTheContradiction)
        alice.library("Island", bear, elf)
        alice.casts(acceptTheContradiction)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered("Island")
        resolveAll()
        expect { alice.battlefield.names.count { it == "Island" } shouldBe 3 }
    }

    @Test
    fun arenaTidecallerLootsOnEntering() = scenario {
        alice.battlefield("Island", "Island", "Mountain", "Mountain")
        alice.hand(arenaTidecaller, ogre)
        alice.library(bear)
        alice.casts(arenaTidecaller)
        resolveAll()
        may(true)
        chooseIfOffered(ogre)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun rushingFeudknightRummages() = scenario {
        alice.battlefield(rushingFeudknight)
        alice.hand(ogre)
        alice.library(bear)
        alice.activates(rushingFeudknight, choosing = listOf(ogre))
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun musesUrgingMakesAnElemental() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(musesUrging)
        alice.library(bear, ogre)
        alice.casts(musesUrging)
        resolveAll()
        chooseNone()
        (pending as? Decision.ChooseObjects)?.let { answer(Answer.Objects(it.options)) }
        resolveAll()
        expect { "Elemental Token" hasKeyword Keyword.Flying }
    }

    @Test
    fun scarringReviewBurnsAndLoots() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain", "Mountain")
        alice.hand(scarringReview, ogre)
        alice.library(bear, elf)
        alice.casts(scarringReview, x = 2, target = "bob")
        resolveAll()
        chooseIfOffered(ogre)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.hand.size shouldBe 2
        }
    }

    @Test
    fun landscapeReworkingTradesTwoForFour() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Island", "Island")
        alice.hand(landscapeReworking)
        alice.library("Plains", "Swamp", "Mountain", "Plains")
        alice.casts(landscapeReworking, paying = listOf("Forest", "Forest", "Forest", "Forest"))
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Island", "Island")
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Plains", "Swamp", "Mountain", "Plains")
        resolveAll()
        expect { alice.battlefield has "Mountain" }
    }

    @Test
    fun ecologyResearcherFindsABasic() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(ecologyResearcher)
        alice.library(bear, "Island")
        alice.casts(ecologyResearcher)
        resolveAll()
        may(true)
        chooseIfOffered("Island")
        resolveAll()
        expect { alice.hand has "Island" }
    }

    // ---- Chunk 13: removal and tricks

    @Test
    fun mentorsReplyCheapOnATappedCreature() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(mentorsReply)
        bob.battlefield(giant, tapped = true)
        alice.casts(mentorsReply, target = giant)
        resolveAll()
        expect { bob.graveyard has giant }
    }

    @Test
    fun wearAwayToSandGivesThemALand() = scenario {
        alice.battlefield("Plains")
        alice.hand(wearAwayToSand)
        bob.battlefield(giant)
        bob.library(bear, "Forest")
        alice.casts(wearAwayToSand, target = giant)
        resolveAll()
        if (pending is Decision.May) may(true)
        chooseIfOffered("Forest")
        resolveAll()
        expect {
            bob.graveyard has giant
            bob.battlefield has "Forest"
        }
    }

    @Test
    fun holdYourGroundDestroysTheStrong() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(holdYourGround)
        bob.battlefield(bear, ogre)
        assertFailsWith<IllegalArgumentException> { alice.casts(holdYourGround, target = bear) }
        targets(ogre)
        payIfAsked()
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun splinteredNoviceShatters() = scenario {
        alice.battlefield(splinteredNovice, "Plains")
        bob.battlefield(idol)
        alice.activates(splinteredNovice, target = idol)
        resolveAll()
        expect { bob.graveyard has idol }
    }

    @Test
    fun exilingTreacheryBouncesAndSurveils() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(exilingTreachery)
        alice.library(bear)
        bob.battlefield(idol)
        alice.casts(exilingTreachery, target = idol)
        resolveAll()
        chooseNone()
        resolveAll()
        expect { bob.hand has idol }
    }

    @Test
    fun waveAsideCheapAgainstSpells() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(waveAside)
        bob.battlefield("Mountain")
        bob.hand(shock)
        turn(active = bob)
        bob.casts(shock, target = "alice")
        bob.passes()
        alice.casts(waveAside, target = shock)
        resolveAll()
        expect { alice.life shouldBe 20 }
    }

    @Test
    fun pursueTheMuseShields() = scenario {
        alice.battlefield("Island", bear)
        alice.hand(pursueTheMuse)
        alice.casts(pursueTheMuse, target = bear)
        resolveAll()
        expect {
            bear.toughness shouldBe 5
            bear hasKeyword Keyword.Hexproof
        }
    }

    @Test
    fun fallBehindScheduleTucksAnAttacker() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(fallBehindSchedule)
        alice.library(elf)
        bob.battlefield(ogre)
        bob.library(bear, bear)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(ogre)
        bob.passes()
        alice.casts(fallBehindSchedule, target = ogre)
        resolveAll()
        if (pending is Decision.ChooseOption) option("top")
        resolveAll()
        expect { bob.library.names.first() shouldBe ogre }
    }

    @Test
    fun priceOfGeniusDrawsAndCounters() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", bear)
        alice.hand(priceOfGenius)
        alice.library(elf, elf)
        alice.casts(priceOfGenius, targets = listOf("alice", bear))
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            bear.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun anatomyDrillThreeWays() = scenario {
        alice.battlefield("Swamp", bear)
        alice.hand(anatomyDrill)
        bob.battlefield(elf)
        alice.casts(anatomyDrill, targets = listOf("bob", bear, elf))
        resolveAll()
        expect {
            bob.life shouldBe 19
            bear.power shouldBe 3
            bob.graveyard has elf
        }
    }

    @Test
    fun virtuosoFlourishProtects() = scenario {
        alice.battlefield("Swamp", bear)
        alice.hand(virtuosoFlourish)
        alice.casts(virtuosoFlourish, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun burrogDoomcrafterPumps() = scenario {
        alice.battlefield(burrogDoomcrafter, "Swamp", "Swamp")
        alice.activates(burrogDoomcrafter)
        resolveAll()
        expect { burrogDoomcrafter.power shouldBe 2 }
    }

    @Test
    fun forebearsWrathCountsItsNamesakes() = scenario {
        alice.battlefield("Mountain", bear)
        alice.hand(forebearsWrath)
        alice.graveyard(forebearsWrath)
        alice.library(elf)
        alice.casts(forebearsWrath, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Trample
            alice.hand has elf
        }
    }

    @Test
    fun creativeMethodSweepsTheirSide() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", bear)
        alice.hand(creativeMethod)
        bob.battlefield(elf, ogre)
        alice.casts(creativeMethod, modes = listOf(1))
        resolveAll()
        expect {
            bob.graveyard has elf
            alice.battlefield has bear
        }
    }

    @Test
    fun illTimedJestBurns() = scenario {
        alice.battlefield("Mountain")
        alice.hand(illTimedJest)
        bob.battlefield(ogre)
        alice.casts(illTimedJest, target = ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun blatantRidiculeBurnsAndSurveils() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(blatantRidicule)
        alice.library(bear)
        bob.battlefield(giant)
        alice.casts(blatantRidicule, target = giant)
        resolveAll()
        chooseNone()
        resolveAll()
        expect { card(giant).view.obj!!.damage shouldBe 4 }
    }

    @Test
    fun burrogBombardmentBitesHarderAfterASpell() = scenario {
        alice.battlefield("Forest", "Island", "Forest", bear)
        alice.hand(ponder, burrogBombardment)
        alice.library(elf)
        bob.battlefield(ogre)
        alice.casts(ponder, paying = listOf("Island"))
        resolveAll()
        alice.casts(burrogBombardment, targets = listOf(bear, ogre))
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun tortoiseTakedownFightsSafely() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", bear)
        alice.hand(tortoiseTakedown)
        bob.battlefield(elf)
        alice.casts(tortoiseTakedown, targets = listOf(bear, elf))
        resolveAll()
        expect {
            bob.graveyard has elf
            bear.toughness shouldBe 12
        }
    }

    @Test
    fun splendidRotExilesAndDraws() = scenario {
        alice.battlefield("Forest", "Forest")
        alice.hand(splendidRot)
        alice.library(bear)
        bob.graveyard(ogre)
        alice.casts(splendidRot, modes = listOf(2), target = ogre)
        resolveAll()
        expect {
            bob.exile has ogre
            alice.hand has bear
        }
    }

    @Test
    fun seersRenewalPumpsDrawsAndHeals() = scenario {
        alice.battlefield("Forest", bear)
        alice.hand(seersRenewal)
        alice.library(elf)
        alice.casts(seersRenewal, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            alice.hand has elf
            alice.life shouldBe 21
        }
    }

    @Test
    fun frenziedAssaultDrawsOnDeaths() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", bear)
        alice.hand(frenziedAssault, doom)
        alice.library(elf)
        alice.casts(frenziedAssault, target = bear, paying = listOf("Swamp", "Swamp"))
        resolveAll()
        alice.casts(doom, target = bear)
        resolveAll()
        expect { alice.hand has elf }
    }

    @Test
    fun rootbindingCraftEmpowers() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Forest", "Forest", bear)
        alice.hand(rootbindingCraft)
        alice.casts(rootbindingCraft)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear hasKeyword Keyword.Menace
            alice.life shouldBe 21
        }
    }

    // ---- Chunk 14: charms, Vehicles and lands

    @Test
    fun emberloreTrinketRaisesASmallCreature() = scenario {
        alice.battlefield("Mountain", "Plains")
        alice.hand(emberloreTrinket)
        alice.graveyard(bear)
        alice.casts(emberloreTrinket, modes = listOf(1), target = bear)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    @Test
    fun huecraftTrinketPingsTwo() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.hand(huecraftTrinket)
        bob.battlefield(elf)
        alice.casts(huecraftTrinket, modes = listOf(1), targets = listOf(elf, "bob"))
        resolveAll()
        expect {
            bob.graveyard has elf
            bob.life shouldBe 19
        }
    }

    @Test
    fun tessellateTrinketMakesAFiveFive() = scenario {
        alice.battlefield("Forest", "Island", bear)
        alice.hand(tessellateTrinket)
        alice.casts(tessellateTrinket, modes = listOf(2), target = bear)
        resolveAll()
        expect { bear.power shouldBe 5 }
    }

    @Test
    fun inkquillTrinketDrains() = scenario {
        alice.battlefield("Plains", "Swamp")
        alice.hand(inkquillTrinket)
        alice.casts(inkquillTrinket, modes = listOf(2))
        resolveAll()
        expect {
            bob.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    @Test
    fun rotbloomTrinketDestroysACheapPermanent() = scenario {
        alice.battlefield("Swamp", "Forest")
        alice.hand(rotbloomTrinket)
        bob.battlefield(idol)
        alice.casts(rotbloomTrinket, modes = listOf(2), target = idol)
        resolveAll()
        expect { bob.graveyard has idol }
    }

    @Test
    fun wrestleWithMortalityDestroysAndHeals() = scenario {
        alice.battlefield("Swamp", "Forest", "Forest")
        alice.hand(wrestleWithMortality)
        bob.battlefield(idol)
        alice.casts(wrestleWithMortality, target = idol)
        resolveAll()
        expect {
            bob.graveyard has idol
            alice.life shouldBe 21
        }
    }

    @Test
    fun examinersGlanceBouncesAndRamps() = scenario {
        alice.battlefield("Forest", "Forest", "Island", "Island")
        alice.hand(examinersGlance)
        alice.library(bear, "Island")
        bob.battlefield(ogre)
        alice.casts(examinersGlance, target = ogre)
        resolveAll()
        chooseIfOffered("Island")
        resolveAll()
        expect {
            bob.hand has ogre
            alice.battlefield.names.count { it == "Island" } shouldBe 3
        }
    }

    @Test
    fun spatterMethodSweeps() = scenario {
        alice.battlefield("Island", "Island", "Mountain", "Mountain", "Mountain")
        alice.hand(spatterMethod)
        bob.battlefield(ogre, giant)
        alice.casts(spatterMethod, modes = listOf(1))
        resolveAll()
        expect {
            bob.graveyard has ogre
            bob.battlefield has giant
        }
    }

    @Test
    fun vividEruptionBurnsAndTaps() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.hand(vividEruption)
        bob.battlefield(ogre, giant)
        alice.casts(vividEruption, targets = listOf(ogre, giant))
        resolveAll()
        expect {
            bob.graveyard has ogre
            giant.tapped shouldBe true
        }
    }

    @Test
    fun academyAircoachFindsALand() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(academyAircoach)
        alice.library(bear, "Island")
        alice.casts(academyAircoach)
        resolveAll()
        may(true)
        chooseIfOffered("Island")
        resolveAll()
        expect { alice.hand has "Island" }
    }

    // CR 201.3: a chosen land card name
    @Test
    fun ossifiedVillageShutsDownANamedLand() = scenario {
        alice.hand(ossifiedVillage)
        bob.battlefield(aircoachLanding, "Plains", "Plains", "Plains")
        alice.plays(ossifiedVillage)
        resolveAll()
        answer(Answer.CardName(aircoachLanding))
        resolveAll()
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.activates(aircoachLanding, ability = 1, target = ossifiedVillage) }
    }

    @Test
    fun meadowsOfContentionSurveils() = scenario {
        alice.battlefield(meadowsOfContention, "Mountain", "Mountain", "Plains", "Plains")
        alice.library(bear)
        alice.activates(meadowsOfContention, ability = 1)
        resolveAll()
        alice.chooses(bear)
        resolveAll()
        expect { alice.graveyard has bear }
    }

    @Test
    fun mushroomHollowNeedsTwoOtherLands() = scenario {
        alice.battlefield("Swamp")
        alice.hand(mushroomHollow)
        alice.plays(mushroomHollow)
        expect { mushroomHollow.tapped shouldBe true }
    }
}
