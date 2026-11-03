package arcanum.engine.sets.sos

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.sos.Sos.bear
import arcanum.engine.sets.sos.Sos.doom
import arcanum.engine.sets.sos.Sos.elf
import arcanum.engine.sets.sos.Sos.giant
import arcanum.engine.sets.sos.Sos.heal
import arcanum.engine.sets.sos.Sos.idol
import arcanum.engine.sets.sos.Sos.ogre
import arcanum.engine.sets.sos.Sos.p1p1
import arcanum.engine.sets.sos.Sos.ponder
import arcanum.engine.sets.sos.Sos.shock
import kotlin.test.Test
import kotlin.test.assertEquals

/** Secrets of Strixhaven's converge, opus, mana-spent, paradigm, flashback, increment, stun, Fractal, infusion and
 * life cards in play (parser chunks 01–05), each card's own ability on top of the mechanics [SosMechanicsScenariosTest]
 * covers. Ravenous Longneck and Cubic Swarm are increment with keywords only. */
class SosMechanicCardsScenariosTest {
    private val theRousingAntiquity = Cards.invent("The Rousing Antiquity", "{10}", "Legendary Creature — Avatar", """
        This spell costs {1} less to cast for each instant and sorcery card in your graveyard.
        Reach
        Whenever The Rousing Antiquity attacks, you may cast target instant or sorcery card from your graveyard without paying its mana cost. If that spell would be put into your graveyard, exile it instead.
    """, "7/7")
    private val cleavingAntiquity = Cards.invent("Cleaving Antiquity", "{6}", "Creature — Avatar", """
        Converge — When this creature enters, exile target nonland permanent an opponent controls with mana value less than or equal to the number of colors of mana spent to cast this creature.
        {2}: Put target card from a graveyard on the bottom of its owner's library.
    """, "3/3")
    private val allHandsConvened = Cards.invent("All Hands Convened", "{6}", "Sorcery", "Converge — Target player draws X cards, All Hands Convened deals X damage to any target, and you gain X life, where X is the number of colors of mana spent to cast this spell.")
    private val ascendantAntiquity = Cards.invent("Ascendant Antiquity", "{7}", "Creature — Avatar", """
        Vigilance
        Converge — When this creature enters, you may draw X cards, where X is the number of colors of mana spent to cast this spell. If you draw one or more cards this way, discard two cards.
    """, "6/6")
    private val arcanePortentsRetold = Cards.invent("Arcane Portents Retold", "{4}{B}", "Sorcery", "Converge — Target player discards X cards, where X is the number of colors of mana spent to cast this spell.")
    private val antiquitysTorment = Cards.invent("Antiquity's Torment", "{4}{R}", "Sorcery", "Converge — Antiquity's Torment deals X damage to target creature, where X is the number of colors of mana spent to cast this spell. Exile cards from the top of your library equal to the excess damage dealt to that creature this way. You may play those cards until the end of your next turn.")
    private val cinderbloodAntiquity = Cards.invent("Cinderblood Antiquity", "{2/R}{2/R}{2/R}", "Creature — Avatar", """
        Reach, trample
        Converge — This creature enters with a +1/+1 counter on it for each color of mana spent to cast it.
        Whenever you cast an instant or sorcery spell, creatures you control get +1/+0 until end of turn for each color of mana spent to cast that spell.
    """, "2/2")
    private val brambleChorus = Cards.invent("Bramble Chorus", "{5}{G}", "Sorcery", "Converge — Create two 0/0 green and blue Fractal creature tokens. Put X +1/+1 counters on each of them and you gain X life, where X is the number of colors of mana spent to cast this spell.")
    private val tidebreakVirtuoso = Cards.invent("Tidebreak Virtuoso", "{2}{U}", "Creature — Human Wizard", """
        When this creature enters, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        Opus — Whenever you cast an instant or sorcery spell, this creature gets +1/+1 until end of turn. If five or more mana was spent to cast that spell, this creature gets +2/+2 until end of turn instead.
    """, "2/2")
    private val showcaseTidewhisperer = Cards.invent("Showcase Tidewhisperer", "{U}", "Creature — Djinn Wizard", "Opus — Whenever you cast an instant or sorcery spell, target player mills three cards. If five or more mana was spent to cast that spell, that player mills ten cards instead.", "0/2")
    private val inspirationHunter = Cards.invent("Inspiration Hunter", "{1}{U}", "Creature — Elf Wizard", "Opus — Whenever you cast an instant or sorcery spell, draw a card. Then discard a card unless five or more mana was spent to cast that spell.", "1/2")
    private val flourishingFlamedancer = Cards.invent("Flourishing Flamedancer", "{1}{R}", "Creature — Human Sorcerer", "Opus — Whenever you cast an instant or sorcery spell, this creature gets +1/+1 until end of turn. If five or more mana was spent to cast that spell, this creature also gains double strike until end of turn.", "2/2")
    private val emberHeartConductor = Cards.invent("Ember-Heart Conductor", "{1}{R}", "Creature — Goblin Bard", """
        Menace
        Opus — Whenever you cast an instant or sorcery spell, put a +1/+1 counter on this creature. If five or more mana was spent to cast that spell, add an amount of {R} equal to this creature's power.
    """, "2/2")
    private val grappleShowman = Cards.invent("Grapple Showman", "{3}{R}", "Creature — Orc Sorcerer", """
        Trample
        Opus — Whenever you cast an instant or sorcery spell, put a +1/+1 counter on this creature. If five or more mana was spent to cast that spell, put two +1/+1 counters on this creature instead.
    """, "4/3")
    private val prismgaleCharger = Cards.invent("Prismgale Charger", "{1}{U}{R}", "Creature — Elemental Horse", """
        Ward {1}, haste
        Opus — Whenever you cast an instant or sorcery spell, this creature gets +1/+1 until end of turn. If five or more mana was spent to cast that spell, create a token that's a copy of this creature.
    """, "3/3")
    private val elementalPennantBearer = Cards.invent("Elemental Pennant-Bearer", "{1}{U}{R}", "Creature — Elemental Bird", """
        Flying, vigilance
        Opus — Whenever you cast an instant or sorcery spell, this creature gets +1/+0 until end of turn. If five or more mana was spent to cast that spell, exile the top card of your library. You may play that card until the end of your next turn.
    """, "1/4")
    private val dazzlingCloudwhale = Cards.invent("Dazzling Cloudwhale", "{2}{U}{R}", "Creature — Elemental Whale", """
        Flying
        Opus — Whenever you cast an instant or sorcery spell, this creature gets +3/+0 until end of turn. If five or more mana was spent to cast that spell, put three +1/+1 counters on this creature instead.
    """, "1/4")
    private val waywardSpellwurm = Cards.invent("Wayward Spellwurm", "{3}{G}", "Creature — Wurm", """
        Trample
        Whenever you cast an instant or sorcery spell, this creature gets +X/+0 until end of turn, where X is the amount of mana spent to cast that spell.
    """, "2/5")
    private val streamChanneler = Cards.invent("Stream-Channeler", "{1}{U}", "Creature — Merfolk Wizard", """
        {T}: Add {U}. Spend this mana only to cast an instant or sorcery spell.
        {1}, {T}: Add one mana of any color. Spend this mana only to cast an instant or sorcery spell.
    """, "1/3")
    private val shapewarpingMage = Cards.invent("Shapewarping Mage", "{2}{U}", "Creature — Human Wizard", """
        When this creature enters, return up to one other target creature to its owner's hand.
        Whenever you cast a spell with {X} in its mana cost, this creature can't be blocked this turn.
    """, "2/2")
    private val surveyorsArthropod = Cards.invent("Surveyor's Arthropod", "{G}{U}", "Creature — Fractal Crab", "Whenever you cast a spell with {X} in its mana cost, look at the top X cards of your library. Put one of them into your hand and the rest on the bottom of your library in a random order.", "1/4")
    private val searingRefrain = Cards.invent("Searing Refrain", "{X}{R}{W}", "Sorcery", """
        Searing Refrain deals damage to target creature equal to the amount of mana spent to cast this spell. Untap all creatures you control.
        Flashback {6}{R}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val renewalColloquium = Cards.invent("Renewal Colloquium", "{5}{W}{W}", "Sorcery — Lesson", """
        Return target nonland permanent card from your graveyard to the battlefield.
        Paradigm (Then exile this spell. After you first resolve a spell with this name, you may cast a copy of it from exile without paying its mana cost at the beginning of each of your first main phases.)
    """)
    private val mirrorcastColloquy = Cards.invent("Mirrorcast Colloquy", "{4}{U}{U}", "Sorcery — Lesson", """
        Target player creates a token that's a copy of target creature you control.
        Paradigm (Then exile this spell. After you first resolve a spell with this name, you may cast a copy of it from exile without paying its mana cost at the beginning of each of your first main phases.)
    """)
    private val sproutingWorkshop = Cards.invent("Sprouting Workshop", "{3}{G}{G}", "Sorcery — Lesson", """
        Put two +1/+1 counters on each creature you control.
        Paradigm (Then exile this spell. After you first resolve a spell with this name, you may cast a copy of it from exile without paying its mana cost at the beginning of each of your first main phases.)
    """)
    private val branchingFormula = Cards.invent("Branching Formula", "{X}{X}{U}", "Instant", """
        Return up to X target instant and/or sorcery cards from your graveyard to your hand.
        Exile Branching Formula.
    """)
    private val loreOfTheEons = Cards.invent("Lore of the Eons", "{4}{U}{U}{U}", "Sorcery", """
        Return all instant and sorcery cards from your graveyard to your hand. You have no maximum hand size for the rest of the game.
        Exile Lore of the Eons.
    """)
    private val secondRecital = Cards.invent("Second Recital", "{R}", "Instant", "Target instant or sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal to its mana cost. (You may cast that card from your graveyard for its flashback cost. Then exile it.)")
    private val relicsRunAmok = Cards.invent("Relics Run Amok", "{1}{W}{W}", "Sorcery", """
        Create two 2/2 red and white Spirit creature tokens. Then if this spell was cast from anywhere other than your hand, put a +1/+1 counter on each Spirit you control.
        Flashback {4}{W}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val wistfulReverie = Cards.invent("Wistful Reverie", "{W}", "Sorcery", """
        Exile target creature you control, then return that card to the battlefield under its owner's control with a +1/+1 counter on it.
        Flashback {2}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val excavationLedger = Cards.invent("Excavation Ledger", "{W}", "Sorcery", """
        Put a +1/+1 counter on target creature you control. It gains vigilance until end of turn.
        Flashback {W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val sharedAssignment = Cards.invent("Shared Assignment", "{1}{W}", "Sorcery", """
        Create a 2/2 red and white Spirit creature token.
        Flashback—Tap three untapped creatures you control. (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val rehearsedAssault = Cards.invent("Rehearsed Assault", "{2}{W}", "Sorcery", """
        Put a +1/+1 counter on each creature target player controls. Target creature gains your choice of double strike or lifelink until end of turn.
        Flashback {1}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val sparringStratagem = Cards.invent("Sparring Stratagem", "{R}", "Sorcery", """
        Sparring Stratagem deals 1 damage to target creature. It can't block this turn.
        Flashback {1}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val codexBurst = Cards.invent("Codex Burst", "{1}{R}", "Sorcery", """
        Codex Burst deals 2 damage to any target.
        Flashback {4}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val chaseBygoneDays = Cards.invent("Chase Bygone Days", "{R}{W}", "Sorcery", """
        You gain 2 life. You may discard a card. If you do, draw two cards.
        Flashback {2}{R}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val mendWhatShattered = Cards.invent("Mend What Shattered", "{2}{W}{B}", "Sorcery", """
        As an additional cost to cast this spell, pay X life.
        Return each artifact and creature card with mana value X from your graveyard to the battlefield.
    """)
    private val primerTallykeeper = Cards.invent("Primer Tallykeeper", "{2}{U}", "Creature — Frog Wizard", """
        Increment (Whenever you cast a spell, if the amount of mana you spent is greater than this creature's power or toughness, put a +1/+1 counter on this creature.)
        When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
    """, "0/3")
    private val eagerAmplifier = Cards.invent("Eager Amplifier", "{G}", "Creature — Turtle Wizard", """
        Increment (Whenever you cast a spell, if the amount of mana you spent is greater than this creature's power or toughness, put a +1/+1 counter on this creature.)
        When this creature dies, if it had one or more counters on it, create a 0/0 green and blue Fractal creature token, then put this creature's counters on that token.
    """, "1/1")
    private val wennaKeenProjector = Cards.invent("Wenna, Keen Projector", "{2}{G}{U}", "Legendary Creature — Frog Druid", """
        Increment (Whenever you cast a spell, if the amount of mana you spent is greater than this creature's power or toughness, put a +1/+1 counter on this creature.)
        Whenever one or more +1/+1 counters are put on Wenna, add one mana of any color.
        {X}, {T}: Create a 0/0 green and blue Fractal creature token and put X +1/+1 counters on it.
    """, "1/4")
    private val fractalWarden = Cards.invent("Fractal Warden", "{3}{G}{U}", "Creature — Elf Wizard", """
        Ward {2}
        Increment (Whenever you cast a spell, if the amount of mana you spent is greater than this creature's power or toughness, put a +1/+1 counter on this creature.)
        At the beginning of each end step, if you put a counter on this creature this turn, create a 0/0 green and blue Fractal creature token and put three +1/+1 counters on it.
    """, "3/3")
    private val putItOffAgain = Cards.invent("Put It Off Again", "{X}{U}", "Sorcery", "Tap target creature. Put twice X stun counters on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)")
    private val longingForHearth = Cards.invent("Longing for Hearth", "{4}{U}{U}", "Instant", "Target player draws two cards. Tap up to two target creatures. Put a stun counter on each of them. (If a permanent with a stun counter would become untapped, remove one from it instead.)")
    private val fractalStandardBearer = Cards.invent("Fractal Standard-Bearer", "{4}{G}{U}", "Creature — Fractal Elk", """
        Trample
        When this creature enters, tap target creature an opponent controls. Put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
    """, "6/6")
    private val fractalOddity = Cards.invent("Fractal Oddity", "{U}", "Instant", "Create a 0/0 green and blue Fractal creature token and put X +1/+1 counters on it, where X is the number of cards you've drawn this turn.")
    private val geometrify = Cards.invent("Geometrify", "{X}{U}", "Instant", "Until end of turn, target creature becomes a green and blue Fractal with base power and toughness each equal to X plus 1. (It loses all other colors and creature types.)")
    private val expansionGradient = Cards.invent("Expansion Gradient", "{G}{U}", "Sorcery", "Put a +1/+1 counter on target creature you control, then double the number of +1/+1 counters on that creature.")
    private val practicalTopology = Cards.invent("Practical Topology", "{2}{G}{U}", "Sorcery", "Create a token that's a copy of target non-Aura permanent you control, except it's a 0/0 Fractal creature in addition to its other types. Put six +1/+1 counters on it.")
    private val untamedConjecture = Cards.invent("Untamed Conjecture", "{X}{G}", "Sorcery", """
        Create a 0/0 green and blue Fractal creature token. Put X +1/+1 counters on it.
        Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
    """)
    private val ostraWildlandsRambler = Cards.invent("Ostra, Wildlands Rambler", "{2}{G}", "Legendary Creature — Elf Druid", """
        Creatures you control with +1/+1 counters on them have trample.
        {4}{G}, {T}: Create a 0/0 green and blue Fractal creature token. Put X +1/+1 counters on it, where X is the number of differently named lands you control.
    """, "3/3")
    private val pteroctahedron = Cards.invent("Pteroctahedron", "{X}{G}{U}", "Creature — Dinosaur Fractal", """
        Flying
        This creature enters with X +1/+1 counters on it.
        When this creature enters, you gain 2 life.
    """, "1/0")
    private val cumulativeProgression = Cards.invent("Cumulative Progression", "{3}{G}{G}", "Enchantment", """
        When this enchantment enters, create a 0/0 green and blue Fractal creature token. Put three +1/+1 counters on it.
        At the beginning of combat on your turn, put a +1/+1 counter on target creature you control. It gains vigilance until end of turn.
    """)
    private val soothingAdvisement = Cards.invent("Soothing Advisement", "{1}{G}", "Enchantment", """
        Whenever you gain life, put a growth counter on this enchantment.
        As long as there are five or more growth counters on this enchantment, creatures you control get +3/+3.
    """)
    private val spireDuelAdjudicator = Cards.invent("Spire Duel Adjudicator", "{2}", "Artifact Creature — Construct", "Whenever you cast a multicolored spell, put a +1/+1 counter on this creature.", "2/1")
    private val recklessDestiny = Cards.invent("Reckless Destiny", "{2}{B}", "Instant", """
        Destroy target creature.
        Infusion — If you gained life this turn, that creature's controller loses 3 life.
    """)
    private val corvinVeinsFreshProvost = Cards.invent("Corvin, Vein's Fresh Provost", "{2}{B}", "Legendary Creature — Bird Skeleton Warlock", """
        Flying
        When Corvin enters, create a 1/1 black and green Pest creature token with "Whenever this token attacks, you gain 1 life."
        Infusion — At the beginning of your end step, if you gained life this turn, return up to one target creature card with mana value X or less from your graveyard to the battlefield, where X is the amount of life you gained this turn.
    """, "2/1")
    private val venomwrightsPupil = Cards.invent("Venomwright's Pupil", "{2}{B}", "Creature — Orc Warlock", "Infusion — When this creature enters, target creature an opponent controls gets -4/-4 until end of turn if you gained life this turn.", "2/2")
    private val sorrowgorgeFiend = Cards.invent("Sorrowgorge Fiend", "{2}{B}{B}", "Creature — Demon", """
        Trample
        Ward—Discard a card.
        Infusion — At the beginning of your end step, sacrifice a permanent unless you gained life this turn.
    """, "7/6")
    private val wiltingMalediction = Cards.invent("Wilting Malediction", "{1}{B}{B}", "Sorcery", """
        All creatures get -2/-2 until end of turn.
        Infusion — If you gained life this turn, destroy all creatures instead.
    """)
    private val burstingBloomtide = Cards.invent("Bursting Bloomtide", "{2}{G}", "Instant", """
        Put two +1/+1 counters on target creature.
        Infusion — If you gained life this turn, that creature also gains trample and indestructible until end of turn.
    """)
    private val trailTheGlowmoths = Cards.invent("Trail the Glowmoths", "{1}{G}", "Sorcery", "Infusion — Look at the top four cards of your library. You may reveal a creature or land card from among them and put it into your hand. If you gained life this turn, you may instead reveal two creature and/or land cards from among them and put them into your hand. Put the rest on the bottom of your library in a random order.")
    private val glowmothsBlessing = Cards.invent("Glowmoth's Blessing", "{1}{G}", "Instant", """
        Infusion — When you cast this spell, copy it if you gained life this turn. You may choose new targets for the copy.
        Target creature gets +2/+4 until end of turn.
    """)
    private val emeritusBrewmaster = Cards.invent("Emeritus Brewmaster", "{4}{G}", "Creature — Troll Druid", """
        Vigilance
        Whenever this creature becomes the target of a spell or ability an opponent controls, you may draw a card.
        Infusion — This creature gets +2/+0 as long as you gained life this turn.
    """, "4/5")
    private val briarknuckleBrawler = Cards.invent("Briarknuckle Brawler", "{2}{G}", "Creature — Elf Druid", """
        Ward {1} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {1}.)
        Infusion — Creatures you control get +1/+0 and have trample as long as you gained life this turn.
    """, "3/3")
    private val elderwoodInstructor = Cards.invent("Elderwood Instructor", "{2}{B}{G}", "Creature — Treefolk Druid", """
        Reach, vigilance
        Infusion — When this creature enters, put two +1/+1 counters on it if you gained life this turn.
    """, "4/4")
    private val grubbIdleVermin = Cards.invent("Grubb, Idle Vermin", "{1}{B}{G}", "Legendary Creature — Pest", "Whenever you gain life, put a +1/+1 counter on each Pest, Bat, Insect, Snake, and Spider you control.", "3/4")
    private val mirewaterGlowmoth = Cards.invent("Mirewater Glowmoth", "{B}{G}", "Creature — Spirit Frog", "Whenever this creature or another creature you control enters, you gain 1 life.", "2/2")
    private val verminStandardBearer = Cards.invent("Vermin Standard-Bearer", "{1}{B}{G}", "Creature — Pest Ape", """
        Trample
        Whenever you gain life, put a +1/+1 counter on this creature.
    """, "2/3")
    private val tutorsNuisance = Cards.invent("Tutor's Nuisance", "{B}{G}", "Creature — Skeleton Pest", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Whenever this creature attacks, you gain 1 life.
        {B}{G}: Return this card from your graveyard to the battlefield tapped.
    """, "1/1")
    private val merchantsBane = Cards.invent("Merchant's Bane", "{2}{G}", "Creature — Badger Pest", """
        Trample
        Whenever this creature attacks, you gain 2 life.
    """, "4/2")
    private val attentiveLifeshaper = Cards.invent("Attentive Lifeshaper", "{1}{G}", "Creature — Dryad Druid", """
        When this creature enters, you gain 1 life.
        {2}{G}: This creature gets +2/+2 until end of turn. Activate only once each turn.
    """, "2/2")
    private val professorVashkaDren = Cards.invent("Professor Vashka Dren", "{2}{B}{G}", "Legendary Planeswalker — Dellian", """
        [+2]: You gain 3 life.
        [0]: You draw a card and lose 1 life.
        [−3]: Destroy target creature.
        [−6]: You get an emblem with "Whenever you gain life, target opponent loses that much life."
    """, loyalty = "5")
    private val seylaGravebloomHerbalist = Cards.invent("Seyla, Gravebloom Herbalist", "{2}{B}", "Legendary Creature — Vampire Druid", """
        Deathtouch
        Whenever a creature you control with power or toughness 1 or less dies, target opponent loses 2 life and you gain 2 life.
    """, "2/2")
    private val crucibleOfVitae = Cards.invent("Crucible of Vitae", "{1}{B}{G}", "Artifact", """
        Whenever a creature you control dies, each opponent loses 1 life and you gain 1 life.
        {1}{B}{G}, {T}, Sacrifice a creature: Return target creature card from your graveyard to the battlefield. Activate only as a sorcery.
    """)
    private val lifestitchPupil = Cards.invent("Lifestitch Pupil", "{B}{B/G}{G}", "Creature — Dryad Warlock", """
        When this creature enters, create a 1/1 black and green Pest creature token with "Whenever this token attacks, you gain 1 life."
        At the beginning of your end step, if a creature died under your control this turn, draw a card.
    """, "3/1")

    private val big = Cards.invent("Quad Mega Lecture", "{3}{U}{U}", "Sorcery", "Draw a card.")
    private val xSpell = Cards.invent("Quad Variable Spark", "{X}{R}", "Sorcery", "Quad Variable Spark deals X damage to any target.")
    private val pest = Cards.invent("Quad Pest", "{B}", "Creature — Pest", "", "1/1")
    private val spirit = Cards.invent("Quad Spirit", "{W}", "Creature — Spirit", "", "1/1")

    // ---- Chunk 01: converge (CR 702.? ability word; colors of mana spent, CR 601.2h)

    @Test
    fun theRousingAntiquityCheaperAndRecasts() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(theRousingAntiquity)
        alice.graveyard(shock, ponder)
        alice.casts(theRousingAntiquity)
        resolveAll()
        expect { alice.battlefield has theRousingAntiquity }
    }

    @Test
    fun theRousingAntiquityCastsFromTheGraveyardOnAttack() = scenario {
        alice.battlefield(theRousingAntiquity)
        alice.graveyard(shock)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(theRousingAntiquity)
        resolveAll()
        targetsIfAsked(shock)
        resolveAll()
        if (pending is Decision.May) may(true)
        targetsIfAsked("bob")
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.exile has shock
        }
    }

    @Test
    fun cleavingAntiquityExilesByColors() = scenario {
        alice.battlefield("Plains", "Island", "Swamp", "Mountain", "Forest", "Forest")
        alice.hand(cleavingAntiquity)
        bob.battlefield(giant)
        alice.casts(cleavingAntiquity)
        resolveAll()
        targetsIfAsked(giant)
        resolveAll()
        expect { bob.exile has giant }
    }

    @Test
    fun allHandsConvenedByThreeColors() = scenario {
        alice.battlefield("Plains", "Island", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(allHandsConvened)
        alice.library(bear, bear, bear)
        alice.casts(allHandsConvened, targets = listOf("alice", "bob"))
        resolveAll()
        expect {
            alice.hand.size shouldBe 3
            bob.life shouldBe 17
            alice.life shouldBe 23
        }
    }

    @Test
    fun ascendantAntiquityDrawsByColors() = scenario {
        alice.battlefield("Island", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(ascendantAntiquity)
        alice.library(bear, ogre, elf)
        alice.casts(ascendantAntiquity)
        resolveAll()
        may(true)
        resolveAll()
        // two colors: two cards drawn, then the only two in hand discarded
        if (pending is Decision.ChooseObjects) alice.chooses(bear, ogre)
        resolveAll()
        expect { alice.graveyard.size shouldBe 2 }
    }

    @Test
    fun arcanePortentsRetoldDiscardsByColors() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Island", "Swamp")
        alice.hand(arcanePortentsRetold)
        bob.hand(bear, ogre, elf)
        alice.casts(arcanePortentsRetold, target = "bob")
        resolveAll()
        if (pending is Decision.ChooseObjects) bob.chooses(bear, ogre)
        resolveAll()
        expect { bob.hand.size shouldBe 1 }
    }

    @Test
    fun antiquitysTormentExilesTheExcess() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Forest", "Island")
        alice.hand(antiquitysTorment)
        alice.library(bear, ogre)
        bob.battlefield(elf)
        alice.casts(antiquitysTorment, target = elf)
        resolveAll()
        expect {
            bob.graveyard has elf
            alice.exile has bear
            alice.exile has ogre
        }
    }

    @Test
    fun cinderbloodAntiquityPumpsOnSpells() = scenario {
        alice.battlefield("Mountain", "Mountain", "Island", "Island", "Island")
        alice.hand(cinderbloodAntiquity, ponder)
        alice.library(bear)
        alice.casts(cinderbloodAntiquity, paying = listOf("Mountain", "Mountain", "Island", "Island"))
        resolveAll()
        expect { cinderbloodAntiquity.counters(p1p1) shouldBe 2 }
        alice.casts(ponder)
        resolveAll()
        expect { cinderbloodAntiquity.power shouldBe 5 }
    }

    @Test
    fun brambleChorusMakesFractals() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Island", "Swamp")
        alice.hand(brambleChorus)
        alice.casts(brambleChorus)
        resolveAll()
        expect {
            alice.battlefield.names.count { it == "Fractal Token" } shouldBe 2
            "Fractal Token".power shouldBe 3
            alice.life shouldBe 23
        }
    }

    // ---- Chunk 02: opus and mana spent

    @Test
    fun tidebreakVirtuosoStunsAndGrows() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(tidebreakVirtuoso, big)
        alice.library(bear)
        bob.battlefield(ogre)
        alice.casts(tidebreakVirtuoso)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { ogre.counters(CounterKind.Stun) shouldBe 1 }
        alice.casts(big)
        resolveAll()
        expect { tidebreakVirtuoso.power shouldBe 4 }
    }

    @Test
    fun showcaseTidewhispererMillsTenForABigSpell() = scenario {
        alice.battlefield(showcaseTidewhisperer, "Island", "Island", "Island", "Island", "Island")
        alice.hand(big)
        alice.library(bear)
        bob.library(elf, elf, elf, elf, elf, elf, elf, elf, elf, elf, elf)
        alice.casts(big)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect { bob.graveyard.size shouldBe 10 }
    }

    @Test
    fun inspirationHunterLootsOrDraws() = scenario {
        alice.battlefield(inspirationHunter, "Island")
        alice.hand(ponder, ogre)
        alice.library(bear, elf)
        alice.casts(ponder)
        resolveAll()
        chooseIfOffered(ogre)
        resolveAll()
        expect {
            alice.graveyard has ogre
            alice.hand.size shouldBe 2
        }
    }

    @Test
    fun flourishingFlamedancerDoubleStrikesForBigSpells() = scenario {
        alice.battlefield(flourishingFlamedancer, "Island", "Island", "Island", "Island", "Island")
        alice.hand(big)
        alice.library(bear)
        alice.casts(big)
        resolveAll()
        expect {
            flourishingFlamedancer.power shouldBe 3
            flourishingFlamedancer hasKeyword Keyword.DoubleStrike
        }
    }

    @Test
    fun emberHeartConductorAddsRedForBigSpells() = scenario {
        alice.battlefield(emberHeartConductor, "Island", "Island", "Island", "Island", "Island")
        alice.hand(big)
        alice.library(bear)
        alice.casts(big)
        resolveAll()
        expect {
            emberHeartConductor.counters(p1p1) shouldBe 1
            alice.pool.size shouldBe 3
        }
    }

    @Test
    fun grappleShowmanTwoCountersForBigSpells() = scenario {
        alice.battlefield(grappleShowman, "Island", "Island", "Island", "Island", "Island")
        alice.hand(big)
        alice.library(bear)
        alice.casts(big)
        resolveAll()
        expect { grappleShowman.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun prismgaleChargerCopiesItselfForBigSpells() = scenario {
        alice.battlefield(prismgaleCharger, "Island", "Island", "Island", "Island", "Island")
        alice.hand(big)
        alice.library(bear)
        alice.casts(big)
        resolveAll()
        expect { alice.battlefield.names.count { it == prismgaleCharger } shouldBe 2 }
    }

    @Test
    fun elementalPennantBearerImpulsesForBigSpells() = scenario {
        alice.battlefield(elementalPennantBearer, "Island", "Island", "Island", "Island", "Island")
        alice.hand(big)
        alice.library(bear, ogre)
        alice.casts(big)
        resolveAll()
        expect {
            elementalPennantBearer.power shouldBe 2
            alice.exile.size shouldBe 1
        }
    }

    @Test
    fun dazzlingCloudwhaleCountersForBigSpells() = scenario {
        alice.battlefield(dazzlingCloudwhale, "Island", "Island", "Island", "Island", "Island", "Mountain")
        alice.hand(big, shock)
        alice.library(bear)
        alice.casts(shock, target = "bob", paying = listOf("Mountain"))
        resolveAll()
        expect { dazzlingCloudwhale.power shouldBe 4 }
        alice.casts(big)
        resolveAll()
        expect { dazzlingCloudwhale.counters(p1p1) shouldBe 3 }
    }

    @Test
    fun waywardSpellwurmGrowsByManaSpent() = scenario {
        alice.battlefield(waywardSpellwurm, "Island", "Island", "Island", "Island", "Island")
        alice.hand(big)
        alice.library(bear)
        alice.casts(big)
        resolveAll()
        expect { waywardSpellwurm.power shouldBe 7 }
    }

    @Test
    fun streamChannelerPaysForSpells() = scenario {
        alice.battlefield(streamChanneler)
        alice.hand(ponder)
        alice.library(bear)
        alice.casts(ponder, paying = listOf(streamChanneler))
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun shapewarpingMageBouncesAndSneaksOnXSpells() = scenario {
        alice.battlefield("Island", "Island", "Island", "Mountain", "Mountain", bear)
        alice.hand(shapewarpingMage, xSpell)
        bob.battlefield(ogre)
        alice.casts(shapewarpingMage, paying = listOf("Island", "Island", "Island"))
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { bob.hand has ogre }
        alice.casts(xSpell, x = 1, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun surveyorsArthropodDigsByX() = scenario {
        alice.battlefield(surveyorsArthropod, "Mountain", "Mountain", "Mountain")
        alice.hand(xSpell)
        alice.library(bear, ogre, elf)
        alice.casts(xSpell, x = 2, target = "bob")
        resolveAll()
        chooseIfOffered(ogre)
        resolveAll()
        expect { alice.hand has ogre }
    }

    @Test
    fun searingRefrainBurnsByManaSpentAndUntaps() = scenario {
        alice.battlefield("Mountain", "Plains", "Mountain", "Mountain")
        alice.battlefield(bear, tapped = true)
        alice.hand(searingRefrain)
        bob.battlefield(giant)
        alice.casts(searingRefrain, x = 2, target = giant)
        resolveAll()
        expect {
            card(giant).view.obj!!.damage shouldBe 4
            bear.tapped shouldBe false
        }
    }

    // ---- Chunk 03: paradigm (CR 702.192) and flashback

    @Test
    fun renewalColloquiumReturnsAPermanent() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(renewalColloquium)
        alice.graveyard(giant)
        alice.casts(renewalColloquium, target = giant)
        resolveAll()
        expect {
            alice.battlefield has giant
            alice.exile has renewalColloquium
        }
    }

    @Test
    fun mirrorcastColloquyCopiesACreature() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", giant)
        alice.hand(mirrorcastColloquy)
        alice.casts(mirrorcastColloquy, targets = listOf("alice", giant))
        resolveAll()
        expect { alice.battlefield.names.count { it == giant } shouldBe 2 }
    }

    @Test
    fun sproutingWorkshopCountersAll() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", bear, elf)
        alice.hand(sproutingWorkshop)
        alice.casts(sproutingWorkshop)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 2
            elf.counters(p1p1) shouldBe 2
        }
    }

    @Test
    fun branchingFormulaReturnsXSpells() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand(branchingFormula)
        alice.graveyard(shock, ponder)
        alice.casts(branchingFormula, x = 2, targets = listOf(shock, ponder))
        resolveAll()
        expect {
            alice.hand has shock
            alice.hand has ponder
            alice.exile has branchingFormula
        }
    }

    @Test
    fun loreOfTheEonsReturnsEverySpell() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(loreOfTheEons)
        alice.graveyard(shock, ponder, bear)
        alice.casts(loreOfTheEons)
        resolveAll()
        expect {
            alice.hand has shock
            alice.hand has ponder
            alice.graveyard has bear
        }
    }

    @Test
    fun secondRecitalGrantsFlashback() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(secondRecital)
        alice.graveyard(shock)
        alice.casts(secondRecital, target = shock)
        resolveAll()
        alice.casts(shock, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun relicsRunAmokFromTheGraveyardCounters() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.graveyard(relicsRunAmok)
        alice.casts(relicsRunAmok)
        resolveAll()
        expect { "Spirit Token".counters(p1p1) shouldBe 1 }
    }

    @Test
    fun wistfulReverieBlinksWithACounter() = scenario {
        alice.battlefield("Plains", bear)
        alice.hand(wistfulReverie)
        alice.casts(wistfulReverie, target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun excavationLedgerCountersAndFlashesBack() = scenario {
        alice.battlefield("Plains", bear)
        alice.graveyard(excavationLedger)
        alice.casts(excavationLedger, target = bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Vigilance
        }
    }

    @Test
    fun sharedAssignmentFlashbackByTapping() = scenario {
        alice.battlefield(bear, elf, ogre)
        alice.graveyard(sharedAssignment)
        alice.casts(sharedAssignment, choosing = listOf(bear, elf, ogre))
        resolveAll()
        expect {
            alice.battlefield has "Spirit Token"
            bear.tapped shouldBe true
        }
    }

    @Test
    fun rehearsedAssaultCountersAndDoubleStrike() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear)
        alice.hand(rehearsedAssault)
        alice.casts(rehearsedAssault, targets = listOf("alice", bear))
        resolveAll()
        if (pending is Decision.ChooseOption) option("double strike")
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.DoubleStrike
        }
    }

    @Test
    fun sparringStratagemPingsAndGrounds() = scenario {
        alice.battlefield("Mountain")
        alice.hand(sparringStratagem)
        bob.battlefield(elf, ogre)
        alice.casts(sparringStratagem, target = elf)
        resolveAll()
        expect { bob.graveyard has elf }
    }

    @Test
    fun codexBurstFlashesBack() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.graveyard(codexBurst)
        alice.casts(codexBurst, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun chaseBygoneDaysRummagesTwo() = scenario {
        alice.battlefield("Mountain", "Plains")
        alice.hand(chaseBygoneDays, ogre)
        alice.library(bear, bear)
        alice.casts(chaseBygoneDays)
        resolveAll()
        may(true)
        chooseIfOffered(ogre)
        resolveAll()
        expect {
            alice.life shouldBe 22
            alice.hand.size shouldBe 2
        }
    }

    @Test
    fun mendWhatShatteredReturnsByLifePaid() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp")
        alice.hand(mendWhatShattered)
        alice.graveyard(bear, idol, ogre)
        alice.casts(mendWhatShattered, x = 2)
        resolveAll()
        expect {
            alice.life shouldBe 18
            alice.battlefield has bear
            alice.battlefield has idol
            alice.graveyard has ogre
        }
    }

    // ---- Chunk 04: increment (CR 702.191), stun and Fractals

    @Test
    fun primerTallykeeperSurveilsAndIncrements() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(primerTallykeeper, big)
        alice.library(bear, ogre, elf)
        alice.casts(primerTallykeeper)
        resolveAll()
        chooseNone()
        (pending as? Decision.ChooseObjects)?.let { answer(Answer.Objects(it.options)) }
        resolveAll()
        expect { primerTallykeeper.counters(p1p1) shouldBe 0 }
    }

    @Test
    fun eagerAmplifierPassesItsCountersToAFractal() = scenario {
        alice.battlefield("Swamp")
        alice.battlefield(eagerAmplifier, counters = listOf(p1p1 to 2))
        alice.hand(doom)
        alice.casts(doom, target = eagerAmplifier)
        resolveAll()
        expect { "Fractal Token".counters(p1p1) shouldBe 2 }
    }

    @Test
    fun wennaMakesFractalsAndMana() = scenario {
        alice.battlefield(wennaKeenProjector, "Island", "Island", "Island")
        alice.activates(wennaKeenProjector, x = 3)
        resolveAll()
        expect { "Fractal Token".counters(p1p1) shouldBe 3 }
    }

    @Test
    fun wennaAddsManaForCounters() = scenario {
        alice.battlefield(wennaKeenProjector, "Island", "Island", "Island", "Island", "Island")
        alice.hand(big)
        alice.library(bear)
        alice.casts(big)
        resolveAll()
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        resolveAll()
        expect {
            wennaKeenProjector.counters(p1p1) shouldBe 1
            alice.pool.size shouldBe 1
        }
    }

    @Test
    fun fractalWardenMakesAFractalAfterACounter() = scenario {
        alice.battlefield(fractalWarden, "Island", "Island", "Island", "Island", "Island")
        alice.hand(big)
        alice.library(bear)
        alice.casts(big)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { "Fractal Token".counters(p1p1) shouldBe 3 }
    }

    @Test
    fun putItOffAgainStunsTwiceX() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(putItOffAgain)
        bob.battlefield(ogre)
        alice.casts(putItOffAgain, x = 2, target = ogre)
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            ogre.counters(CounterKind.Stun) shouldBe 4
        }
    }

    @Test
    fun longingForHearthDrawsAndStuns() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(longingForHearth)
        alice.library(bear, bear)
        bob.battlefield(ogre, giant)
        alice.casts(longingForHearth, targets = listOf("alice", ogre, giant))
        resolveAll()
        expect {
            alice.hand.size shouldBe 2
            giant.counters(CounterKind.Stun) shouldBe 1
        }
    }

    @Test
    fun fractalStandardBearerStunsOnEntering() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Island", "Island")
        alice.hand(fractalStandardBearer)
        bob.battlefield(ogre)
        alice.casts(fractalStandardBearer)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        expect { ogre.counters(CounterKind.Stun) shouldBe 1 }
    }

    @Test
    fun fractalOddityCountsCardsDrawn() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(ponder, fractalOddity)
        alice.library(bear)
        alice.casts(ponder)
        resolveAll()
        alice.casts(fractalOddity)
        resolveAll()
        expect { "Fractal Token".counters(p1p1) shouldBe 1 }
    }

    @Test
    fun geometrifyMakesAFractal() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", bear)
        alice.hand(geometrify)
        alice.casts(geometrify, x = 3, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear.subtypes.map { it.name } shouldBe listOf("Fractal")
        }
    }

    @Test
    fun expansionGradientDoublesCounters() = scenario {
        alice.battlefield("Forest", "Island")
        alice.battlefield(bear, counters = listOf(p1p1 to 2))
        alice.hand(expansionGradient)
        alice.casts(expansionGradient, target = bear)
        resolveAll()
        expect { bear.counters(p1p1) shouldBe 6 }
    }

    @Test
    fun practicalTopologyCopiesAsAFractal() = scenario {
        alice.battlefield("Forest", "Forest", "Island", "Island", idol)
        alice.hand(practicalTopology)
        alice.casts(practicalTopology, target = idol)
        resolveAll()
        expect { alice.battlefield.names.count { it == idol } shouldBe 2 }
    }

    @Test
    fun untamedConjectureMakesAFractalAndSurveils() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(untamedConjecture)
        alice.library(bear, ogre)
        alice.casts(untamedConjecture, x = 2)
        resolveAll()
        chooseNone()
        (pending as? Decision.ChooseObjects)?.let { answer(Answer.Objects(it.options)) }
        resolveAll()
        expect { "Fractal Token".counters(p1p1) shouldBe 2 }
    }

    @Test
    fun ostraMakesFractalsByLandNames() = scenario {
        alice.battlefield(ostraWildlandsRambler, "Forest", "Forest", "Forest", "Island", "Swamp")
        alice.activates(ostraWildlandsRambler)
        resolveAll()
        expect {
            "Fractal Token".counters(p1p1) shouldBe 3
            "Fractal Token" hasKeyword Keyword.Trample
        }
    }

    @Test
    fun pteroctahedronEntersWithXAndHeals() = scenario {
        alice.battlefield("Forest", "Island", "Island", "Island")
        alice.hand(pteroctahedron)
        alice.casts(pteroctahedron, x = 2)
        resolveAll()
        expect {
            pteroctahedron.counters(p1p1) shouldBe 2
            alice.life shouldBe 22
        }
    }

    @Test
    fun cumulativeProgressionGrowsAtCombat() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest", bear)
        alice.hand(cumulativeProgression)
        alice.casts(cumulativeProgression)
        resolveAll()
        expect { "Fractal Token".counters(p1p1) shouldBe 3 }
        advanceTo(TurnStep.BeginningOfCombat)
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Vigilance
        }
    }

    @Test
    fun soothingAdvisementPumpsAtFiveGrowth() = scenario {
        alice.battlefield(soothingAdvisement, counters = listOf(CounterKind.Named("growth") to 4))
        alice.battlefield(bear, "Plains")
        alice.hand(heal)
        expect { bear.power shouldBe 2 }
        alice.casts(heal)
        resolveAll()
        expect { bear.power shouldBe 5 }
    }

    @Test
    fun spireDuelAdjudicatorGrowsOnMulticoloredSpells() = scenario {
        alice.battlefield(spireDuelAdjudicator, "Forest", "Island")
        alice.hand(expansionGradient)
        alice.casts(expansionGradient, target = spireDuelAdjudicator)
        resolveAll()
        expect { spireDuelAdjudicator.counters(p1p1) shouldBe 4 }
    }

    // ---- Chunk 05: infusion (CR 119.9) and life

    @Test
    fun recklessDestinyDrainsWithInfusion() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Plains")
        alice.hand(heal, recklessDestiny)
        bob.battlefield(ogre)
        alice.casts(heal)
        resolveAll()
        alice.casts(recklessDestiny, target = ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            bob.life shouldBe 17
        }
    }

    @Test
    fun corvinVeinsFreshProvostRaisesByLifeGained() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Plains")
        alice.hand(corvinVeinsFreshProvost, heal)
        alice.graveyard(bear)
        alice.casts(corvinVeinsFreshProvost, paying = listOf("Swamp", "Swamp", "Swamp"))
        resolveAll()
        expect { alice.battlefield has "Pest Token" }
        alice.casts(heal)
        resolveAll()
        advanceTo(TurnStep.End)
        targetsIfAsked(bear)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    @Test
    fun venomwrightsPupilShrinksWithInfusion() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Plains")
        alice.hand(heal, venomwrightsPupil)
        bob.battlefield(giant)
        alice.casts(heal)
        resolveAll()
        alice.casts(venomwrightsPupil)
        resolveAll()
        targetsIfAsked(giant)
        resolveAll()
        expect { giant.power shouldBe 1 }
    }

    @Test
    fun sorrowgorgeFiendDemandsLife() = scenario {
        alice.battlefield(sorrowgorgeFiend, idol)
        advanceTo(TurnStep.End)
        resolveAll()
        chooseIfOffered(idol)
        resolveAll()
        expect { alice.graveyard.size shouldBe 1 }
    }

    @Test
    fun wiltingMaledictionDestroysAllWithInfusion() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Plains")
        alice.hand(heal, wiltingMalediction)
        bob.battlefield(giant)
        alice.casts(heal)
        resolveAll()
        alice.casts(wiltingMalediction)
        resolveAll()
        expect { bob.graveyard has giant }
    }

    @Test
    fun wiltingMaledictionShrinksWithout() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(wiltingMalediction)
        bob.battlefield(giant, bear)
        alice.casts(wiltingMalediction)
        resolveAll()
        expect {
            bob.graveyard has bear
            giant.power shouldBe 3
        }
    }

    @Test
    fun burstingBloomtideWithInfusion() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Plains", bear)
        alice.hand(heal, burstingBloomtide)
        alice.casts(heal)
        resolveAll()
        alice.casts(burstingBloomtide, target = bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 2
            bear hasKeyword Keyword.Indestructible
        }
    }

    @Test
    fun trailTheGlowmothsTakesTwoWithInfusion() = scenario {
        alice.battlefield("Forest", "Forest", "Plains")
        alice.hand(heal, trailTheGlowmoths)
        alice.library(bear, shock, "Forest", ogre)
        alice.casts(heal)
        resolveAll()
        alice.casts(trailTheGlowmoths)
        resolveAll()
        if (pending is Decision.May) may(true)
        if (pending is Decision.ChooseObjects) alice.chooses(bear, "Forest")
        resolveAll()
        expect {
            alice.hand has bear
            alice.hand has "Forest"
        }
    }

    @Test
    fun glowmothsBlessingCopiedWithInfusion() = scenario {
        alice.battlefield("Forest", "Forest", "Plains", bear)
        alice.hand(heal, glowmothsBlessing)
        alice.casts(heal)
        resolveAll()
        alice.casts(glowmothsBlessing, target = bear)
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect { bear.power shouldBe 6 }
    }

    @Test
    fun emeritusBrewmasterDrawsWhenTargetedAndGrows() = scenario {
        alice.battlefield(emeritusBrewmaster, "Plains")
        alice.hand(heal)
        alice.library(bear)
        alice.casts(heal)
        resolveAll()
        expect { emeritusBrewmaster.power shouldBe 6 }
    }

    @Test
    fun briarknuckleBrawlerTramplesWithInfusion() = scenario {
        alice.battlefield(briarknuckleBrawler, bear, "Plains")
        alice.hand(heal)
        alice.casts(heal)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Trample
        }
    }

    @Test
    fun elderwoodInstructorCountersWithInfusion() = scenario {
        alice.battlefield("Swamp", "Swamp", "Forest", "Forest", "Plains")
        alice.hand(heal, elderwoodInstructor)
        alice.casts(heal)
        resolveAll()
        alice.casts(elderwoodInstructor)
        resolveAll()
        expect { elderwoodInstructor.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun grubbCountersPestsOnLifeGain() = scenario {
        alice.battlefield(grubbIdleVermin, pest, bear, "Plains")
        alice.hand(heal)
        alice.casts(heal)
        resolveAll()
        expect {
            grubbIdleVermin.counters(p1p1) shouldBe 1
            pest.counters(p1p1) shouldBe 1
            bear.counters(p1p1) shouldBe 0
        }
    }

    @Test
    fun mirewaterGlowmothGainsOnArrivals() = scenario {
        alice.battlefield("Swamp", "Forest")
        alice.hand(mirewaterGlowmoth)
        alice.casts(mirewaterGlowmoth)
        resolveAll()
        expect { alice.life shouldBe 21 }
    }

    @Test
    fun verminStandardBearerGrowsOnLifeGain() = scenario {
        alice.battlefield(verminStandardBearer, "Plains")
        alice.hand(heal)
        alice.casts(heal)
        resolveAll()
        expect { verminStandardBearer.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun tutorsNuisanceGainsAndReturns() = scenario {
        alice.battlefield("Swamp", "Forest")
        alice.graveyard(tutorsNuisance)
        alice.activates(tutorsNuisance)
        resolveAll()
        expect { tutorsNuisance.tapped shouldBe true }
    }

    @Test
    fun merchantsBaneGainsOnAttack() = scenario {
        alice.battlefield(merchantsBane)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(merchantsBane)
        resolveAll()
        expect { alice.life shouldBe 22 }
    }

    @Test
    fun attentiveLifeshaperGainsAndPumpsOnce() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", "Forest")
        alice.hand(attentiveLifeshaper)
        alice.casts(attentiveLifeshaper, paying = listOf("Forest", "Forest"))
        resolveAll()
        alice.activates(attentiveLifeshaper)
        resolveAll()
        expect {
            alice.life shouldBe 21
            attentiveLifeshaper.power shouldBe 4
        }
    }

    @Test
    fun professorVashkaDrenGainsAndDestroys() = scenario {
        alice.battlefield(professorVashkaDren, counters = listOf(CounterKind.Loyalty to 5))
        bob.battlefield(giant)
        alice.activates(professorVashkaDren, ability = 2, target = giant)
        resolveAll()
        expect { bob.graveyard has giant }
        assertEquals(2, loyalty(professorVashkaDren))
    }

    @Test
    fun professorVashkaDrenEmblemDrains() = scenario {
        alice.battlefield(professorVashkaDren, counters = listOf(CounterKind.Loyalty to 6))
        alice.battlefield("Plains")
        alice.hand(heal)
        alice.activates(professorVashkaDren, ability = 3)
        resolveAll()
        alice.casts(heal)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun seylaDrainsForSmallDeaths() = scenario {
        alice.battlefield(seylaGravebloomHerbalist, elf, "Swamp")
        alice.hand(doom)
        alice.casts(doom, target = elf)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.life shouldBe 22
        }
    }

    @Test
    fun crucibleOfVitaeDrainsAndRecurs() = scenario {
        alice.battlefield(crucibleOfVitae, bear, elf, "Swamp", "Swamp", "Forest")
        alice.graveyard(giant)
        alice.activates(crucibleOfVitae, target = giant, choosing = listOf(elf))
        resolveAll()
        expect {
            alice.battlefield has giant
            bob.life shouldBe 19
        }
    }

    @Test
    fun lifestitchPupilDrawsAfterADeath() = scenario {
        alice.battlefield("Swamp", "Swamp", "Forest", "Swamp", elf)
        alice.hand(lifestitchPupil, doom)
        alice.library(bear)
        alice.casts(lifestitchPupil, paying = listOf("Swamp", "Swamp", "Forest"))
        resolveAll()
        alice.casts(doom, target = elf)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect {
            alice.battlefield has "Pest Token"
            alice.hand has bear
        }
    }
}
