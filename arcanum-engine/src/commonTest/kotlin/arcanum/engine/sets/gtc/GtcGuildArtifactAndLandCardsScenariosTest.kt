package arcanum.engine.sets.gtc

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.gtc.Gtc.bear
import arcanum.engine.sets.gtc.Gtc.charm
import arcanum.engine.sets.gtc.Gtc.doom
import arcanum.engine.sets.gtc.Gtc.flyer
import arcanum.engine.sets.gtc.Gtc.giant
import arcanum.engine.sets.gtc.Gtc.heal
import arcanum.engine.sets.gtc.Gtc.human
import arcanum.engine.sets.gtc.Gtc.idol
import arcanum.engine.sets.gtc.Gtc.ogre
import arcanum.engine.sets.gtc.Gtc.p1p1
import arcanum.engine.sets.gtc.Gtc.ponder
import arcanum.engine.sets.gtc.Gtc.shock
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Gatecrash's Orzhov, Dimir, Boros, Gruul and Simic cards, artifacts and lands in play (parser chunks 11–16). */
class GtcGuildArtifactAndLandCardsScenariosTest {
    private val syndicateAristocrat = Cards.invent("Syndicate Aristocrat", "{W}{B}", "Creature — Human Advisor", "Sacrifice another creature: This creature gains protection from the color of your choice until end of turn.", "2/2")
    private val oathpactAngel = Cards.invent("Oathpact Angel", "{3}{W}{B}{B}", "Creature — Angel", """
        Flying
        When this creature dies, create a 1/1 white and black Cleric creature token. It has "{3}{W}{B}{B}, {T}, Sacrifice this token: Return a card named Oathpact Angel from your graveyard to the battlefield."
    """, "5/5")
    private val highPriestOfAtonement = Cards.invent("High Priest of Atonement", "{W}{B}", "Creature — Human Cleric", "Whenever this creature is dealt damage, you may destroy target nonland permanent.", "1/1")
    private val ruthlessEviction = Cards.invent("Ruthless Eviction", "{4}{W}{B}", "Sorcery", """
        Choose one —
        • Exile all artifacts.
        • Exile all creatures.
        • Exile all enchantments.
        • Exile all planeswalkers.
    """)
    private val obzeratPhantomCouncil = Cards.invent("Obzerat, Phantom Council", "{1}{W}{W}{B}{B}", "Legendary Creature — Spirit Advisor", """
        When Obzerat enters, target opponent loses 2 life and you gain 2 life.
        At the beginning of your end step, you may exile Obzerat. If you do, return it to the battlefield under its owner's control at the beginning of your next upkeep. It gains haste.
    """, "5/5")
    private val thousandLashShackle = Cards.invent("Thousand Lash Shackle", "{2}{W}{B}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature can't attack or block, and its activated abilities can't be activated.
        At the beginning of the upkeep of enchanted creature's controller, that player loses 1 life.
    """)
    private val syndicateCharm = Cards.invent("Syndicate Charm", "{W}{B}", "Instant", """
        Choose one —
        • Return target creature you control and all Auras you control attached to it to their owner's hand.
        • Destroy target creature and you lose life equal to its toughness.
        • Return target creature card with mana value 1 or less from your graveyard to the battlefield.
    """)
    private val cleanseTheProfane = Cards.invent("Cleanse the Profane", "{2}{W}{B}", "Sorcery", "Target opponent discards two cards and you gain 2 life.")
    private val tithingGuildmage = Cards.invent("Tithing Guildmage", "{W}{B}", "Creature — Human Wizard", """
        {1}{W}{B}: Target creature gains lifelink until end of turn.
        {1}{W}{B}: Whenever you gain life this turn, each opponent loses that much life.
    """, "2/2")
    private val summonApparition = Cards.invent("Summon Apparition", "{W/B}", "Instant", "Exile target card from a graveyard. Create a 1/1 white and black Spirit creature token with flying.")
    private val blessingOfOrzhova = Cards.invent("Blessing of Orzhova", "{1}{W/B}{W/B}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +1/+1 and has flying and lifelink.
    """)
    private val eternalServitude = Cards.invent("Eternal Servitude", "{X}{W/B}{W/B}{W/B}", "Sorcery", "Return each creature card with mana value X from your graveyard to the battlefield.")
    private val whisperAlleyBroker = Cards.invent("Whisper Alley Broker", "{1}{U}{B}", "Creature — Human Rogue", """
        {T}: Draw a card, then exile a card from your hand face down.
        You may look at cards exiled with this creature.
        {U}{B}, {T}: Return a card exiled with this creature to its owner's hand.
    """, "0/3")
    private val devouringAberration = Cards.invent("Devouring Aberration", "{3}{U}{B}", "Creature — Horror", """
        Devouring Aberration's power and toughness are each equal to the number of cards in your opponents' graveyards.
        Whenever you cast a spell, each opponent reveals cards from the top of their library until they reveal a land card, then puts those cards into their graveyard.
    """, "*/*")
    private val shadowCharm = Cards.invent("Shadow Charm", "{U}{B}", "Instant", """
        Choose one —
        • Counter target sorcery spell.
        • Destroy target creature with power 2 or less.
        • Look at the top three cards of target player's library. Put one back and the rest into that player's graveyard.
    """)
    private val dreadHorror = Cards.invent("Dread Horror", "{4}{U}{B}", "Creature — Horror", "When this creature enters, return target permanent to its owner's hand, then that player discards a card.", "4/4")
    private val shadowcloakGuildmage = Cards.invent("Shadowcloak Guildmage", "{U}{B}", "Creature — Human Wizard", """
        {1}{U}{B}: Whenever a card is put into an opponent's graveyard from anywhere this turn, that player loses 1 life.
        {2}{U}{B}: Target player mills two cards.
    """, "2/2")
    private val shadowcloakSeer = Cards.invent("Shadowcloak Seer", "{2}{U}{B}", "Creature — Vampire Wizard", """
        Flying
        At the beginning of your upkeep, each player reveals the top card of their library, loses life equal to that card's mana value, then puts it into their hand.
    """, "4/4")
    private val vazalShadowMastermind = Cards.invent("Vazal, Shadow Mastermind", "{U}{U}{B}{B}", "Legendary Creature — Shapeshifter", """
        Hexproof
        Whenever a creature card is put into an opponent's graveyard from anywhere, you may have Vazal become a copy of that card, except its name is Vazal, Shadow Mastermind, it's legendary in addition to its other types, and it has hexproof and this ability.
    """, "3/3")
    private val mindgrinder = Cards.invent("Mindgrinder", "{X}{U}{B}", "Sorcery", "Each opponent reveals cards from the top of their library until they reveal X land cards, then puts all cards revealed this way into their graveyard. X can't be 0.")
    private val boneStrider = Cards.invent("Bone Strider", "{1}{U}{B}", "Creature — Skeleton", "When this creature dies, return it to its owner's hand.", "1/1")
    private val psychicJolt = Cards.invent("Psychic Jolt", "{1}{U}{B}", "Instant", "Counter target spell. Its controller mills two cards.")
    private val soulHostage = Cards.invent("Soul Hostage", "{2}{U}{B}", "Enchantment — Aura", """
        Enchant creature
        You control enchanted creature.
        Discard two cards: This Aura's controller sacrifices it, then draws two cards. Only your opponents may activate this ability.
    """)
    private val forcedConfession = Cards.invent("Forced Confession", "{4}{U/B}", "Sorcery", "Target player mills four cards. You draw a card for each creature card put into their graveyard this way.")
    private val shadowcultRogue = Cards.invent("Shadowcult Rogue", "{1}{U/B}{U/B}", "Creature — Human Rogue", "This creature can't be blocked except by Rogues.", "2/2")
    private val veilstalkerSpecter = Cards.invent("Veilstalker Specter", "{U/B}{U/B}{U/B}", "Creature — Specter", """
        Flying
        Whenever this creature deals combat damage to a player, that player exiles the top card of their library.
        You may play lands and cast spells from among cards exiled with this creature.
    """, "2/3")
    private val musterTheLegion = Cards.invent("Muster the Legion", "{3}{R}{W}", "Enchantment", "At the beginning of your upkeep, put a muster counter on this enchantment. Then create a 1/1 red and white Soldier creature token with haste for each muster counter on this enchantment.")
    private val aurelissWrath = Cards.invent("Aurelis's Wrath", "{X}{R}{W}", "Instant", "Aurelis's Wrath deals X damage divided as you choose among any number of targets. Tap each creature dealt damage this way. Players dealt damage this way can't cast noncreature spells this turn.")
    private val legionCharm = Cards.invent("Legion Charm", "{R}{W}", "Instant", """
        Choose one —
        • Legion Charm deals 4 damage to target player or planeswalker.
        • Permanents you control gain indestructible until end of turn.
        • Target creature gains double strike until end of turn.
    """)
    private val bastionCyclops = Cards.invent("Bastion Cyclops", "{3}{R}{W}", "Creature — Cyclops Soldier", """
        Whenever this creature attacks, it gets +3/+0 until end of turn.
        Whenever this creature blocks, it gets +0/+3 until end of turn.
    """, "3/3")
    private val forgeChampion = Cards.invent("Forge Champion", "{4}{R}{W}", "Creature — Elemental Soldier", """
        When this creature enters, it deals damage to any target equal to the number of creatures you control.
        {R}: This creature gets +1/+0 until end of turn.
        {W}: This creature gets +0/+1 until end of turn.
    """, "4/4")
    private val battleGlory = Cards.invent("Battle Glory", "{R}{W}", "Instant", """
        Target creature gets +3/+0 until end of turn.
        Target creature gets +0/+3 until end of turn.
    """)
    private val sparkLegionnaire = Cards.invent("Spark Legionnaire", "{1}{R}{R}{W}", "Creature — Elemental Soldier", """
        Trample, lifelink, haste
        At the beginning of the end step, sacrifice this creature.
    """, "6/1")
    private val sunforgeGuildmage = Cards.invent("Sunforge Guildmage", "{R}{W}", "Creature — Human Wizard", """
        {1}{R}{W}: Creatures you control get +1/+0 until end of turn.
        {2}{R}{W}: Create a 1/1 red and white Soldier creature token with haste.
    """, "2/2")
    private val brightfirePaladin = Cards.invent("Brightfire Paladin", "{R}{W}", "Creature — Human Knight", """
        Vigilance
        {R}{W}: This creature gets +2/+0 until end of turn.
        {R}{W}: This creature gains first strike until end of turn.
    """, "2/2")
    private val arrowsOfRetribution = Cards.invent("Arrows of Retribution", "{2}{R/W}", "Instant", "Arrows of Retribution deals 4 damage to target attacking or blocking creature.")
    private val legionReckoner = Cards.invent("Legion Reckoner", "{R/W}{R/W}{R/W}", "Creature — Minotaur Wizard", """
        Whenever this creature is dealt damage, it deals that much damage to any target.
        {R/W}: This creature gains first strike until end of turn.
    """, "3/3")
    private val shatteringStrike = Cards.invent("Shattering Strike", "{1}{R/W}", "Instant", "Exile target artifact.")
    private val borgorathUnleashed = Cards.invent("Borgorath Unleashed", "{4}{R}{R}{G}{G}", "Legendary Creature — Cyclops", """
        Trample
        Whenever Borgorath Unleashed deals combat damage to a player, reveal the top three cards of your library. Put all land cards revealed this way into your hand and the rest into your graveyard.
        Discard a land card: Borgorath Unleashed deals 3 damage to any target.
    """, "7/6")
    private val clanInsurrection = Cards.invent("Clan Insurrection", "{X}{R}{G}", "Sorcery", """
        Choose one or more —
        • Clan Insurrection deals X damage to target creature with flying.
        • Clan Insurrection deals X damage to target creature without flying.
        • Clan Insurrection deals X damage to target player or planeswalker.
    """)
    private val domrakWildheart = Cards.invent("Domrak Wildheart", "{1}{R}{G}", "Legendary Planeswalker — Domri", """
        [+1]: Look at the top card of your library. If it's a creature card, you may reveal it and put it into your hand.
        [−2]: Target creature you control fights another target creature.
        [−7]: You get an emblem with "Creatures you control have double strike, trample, hexproof, and haste."
    """, loyalty = "3")
    private val frenziedPlowing = Cards.invent("Frenzied Plowing", "{3}{R}{G}", "Sorcery", "Destroy target land. Search your library for a basic land card, put that card onto the battlefield tapped, then shuffle.")
    private val groundPound = Cards.invent("Ground Pound", "{R}{G}", "Sorcery", "Ground Pound deals damage to target creature equal to the number of lands you control.")
    private val ragingCharm = Cards.invent("Raging Charm", "{R}{G}", "Instant", """
        Choose one —
        • Creatures without flying can't block this turn.
        • Gain control of all permanents you own.
        • Raging Charm deals 3 damage to each creature with flying.
    """)
    private val ragebornBeast = Cards.invent("Rageborn Beast", "{5}{R}{G}", "Creature — Beast", "Whenever this creature or another creature you control enters, that creature fights target creature an opponent controls.", "6/6")
    private val primalVisit = Cards.invent("Primal Visit", "{3}{R}{G}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +3/+3 and has haste.
    """)
    private val signalTheTribes = Cards.invent("Signal the Tribes", "{R}{G}", "Instant", "Search your library for three creature cards and reveal them. If you reveal three cards with different names, choose one of them at random and put that card into your hand. Shuffle the rest into your library.")
    private val rubblebeltGuildmage = Cards.invent("Rubblebelt Guildmage", "{R}{G}", "Creature — Human Shaman", """
        {R}{G}: Creatures you control gain trample until end of turn.
        {1}{R}{G}: Target land you control becomes a 4/4 Elemental creature until end of turn. It's still a land.
    """, "2/2")
    private val kindlingEmissary = Cards.invent("Kindling Emissary", "{R/G}{R/G}", "Creature — Human Shaman", "When this creature enters, add {R}{G}.", "2/2")
    private val arenaFight = Cards.invent("Arena Fight", "{1}{R/G}", "Instant", "Target creature you control fights another target creature. (Each deals damage equal to its power to the other.)")
    private val rubbleRaiders = Cards.invent("Rubble Raiders", "{1}{R/G}{R/G}{R/G}", "Creature — Human Warrior", "Whenever this creature attacks, put a +1/+1 counter on it for each attacking creature you control.", "3/3")
    private val geneVisionary = Cards.invent("Gene Visionary", "{1}{G}{U}", "Creature — Human Wizard", "At the beginning of the end step, if you control four or more creatures named Gene Visionary, you win the game.", "2/3")
    private val hydroshape = Cards.invent("Hydroshape", "{G}{U}", "Instant", "Target land becomes a 3/3 Elemental creature with flying until end of turn. It's still a land.")
    private val arcaneGenesis = Cards.invent("Arcane Genesis", "{2}{G}{U}{U}", "Instant", "Counter target spell. Create an X/X green Ooze creature token, where X is that spell's mana value.")
    private val cloudSwimmer = Cards.invent("Cloud Swimmer", "{X}{G}{U}", "Creature — Leviathan", """
        Flying
        This creature enters with X +1/+1 counters on it.
    """, "0/0")
    private val firstSpeakerZelana = Cards.invent("First Speaker Zelana", "{2}{G}{G}{U}{U}", "Legendary Creature — Merfolk Wizard", """
        First Speaker Zelana enters with X +1/+1 counters on it, where X is the greatest power among other creatures you control.
        When First Speaker Zelana enters, draw cards equal to its power.
    """, "1/1")
    private val hybridCharm = Cards.invent("Hybrid Charm", "{G}{U}", "Instant", """
        Choose one —
        • Target creature gets +3/+3 until end of turn.
        • Permanents you control gain hexproof until end of turn.
        • Return target creature to its owner's hand.
    """)
    private val startlingResults = Cards.invent("Startling Results", "{2}{G}{U}", "Sorcery", "Shuffle your library, then reveal the top card. If it's a nonland card, you may cast it without paying its mana cost. If it's a land card, you may put it onto the battlefield and return Startling Results to its owner's hand.")
    private val civicEvolution = Cards.invent("Civic Evolution", "{3}{G}{U}", "Sorcery", "Draw three cards. You may play an additional land this turn.")
    private val biomassShift = Cards.invent("Biomass Shift", "{X}{G/U}{G/U}", "Instant", "Creatures you control have base power and toughness X/X until end of turn.")
    private val legionKeyrune = Cards.invent("Legion Keyrune", "{3}", "Artifact", """
        {T}: Add {R} or {W}.
        {R}{W}: This artifact becomes a 1/1 red and white Soldier artifact creature with double strike until end of turn. (It deals both first-strike and regular combat damage.)
    """)
    private val shadowKeyrune = Cards.invent("Shadow Keyrune", "{3}", "Artifact", """
        {T}: Add {U} or {B}.
        {U}{B}: This artifact becomes a 2/2 blue and black Horror artifact creature until end of turn and can't be blocked this turn.
    """)
    private val clanKeyrune = Cards.invent("Clan Keyrune", "{3}", "Artifact", """
        {T}: Add {R} or {G}.
        {R}{G}: This artifact becomes a 3/2 red and green Beast artifact creature with trample until end of turn.
    """)
    private val syndicateKeyrune = Cards.invent("Syndicate Keyrune", "{3}", "Artifact", """
        {T}: Add {W} or {B}.
        {W}{B}: This artifact becomes a 1/4 white and black Thrull artifact creature with lifelink until end of turn.
    """)
    private val hybridKeyrune = Cards.invent("Hybrid Keyrune", "{3}", "Artifact", """
        {T}: Add {G} or {U}.
        {G}{U}: This artifact becomes a 2/3 green and blue Crab artifact creature with hexproof until end of turn. (It can't be the target of spells or abilities your opponents control.)
    """)
    private val mirrorImageBracers = Cards.invent("Mirror Image Bracers", "{2}", "Artifact — Equipment", """
        Whenever an ability of equipped creature is activated, if it isn't a mana ability, copy that ability. You may choose new targets for the copy.
        Equip {3}
    """)
    private val augursPrism = Cards.invent("Augur's Prism", "{2}", "Artifact", """
        When this artifact enters, draw a card.
        {1}, {T}: Add one mana of any color.
    """)
    private val bladetipWhip = Cards.invent("Bladetip Whip", "{2}", "Artifact", "{1}, {T}: This artifact deals 1 damage to target opponent or planeswalker.")
    private val riotersGear = Cards.invent("Rioter's Gear", "{2}", "Artifact — Equipment", """
        Equipped creature gets +1/+2.
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val skyblindStaff = Cards.invent("Skyblind Staff", "{1}", "Artifact — Equipment", """
        Equipped creature gets +1/+0 and can't be blocked by creatures with flying.
        Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val legionGatehouse = Cards.invent("Legion Gatehouse", null, "Land — Gate", """
        This land enters tapped.
        {T}: Add {R} or {W}.
    """)
    private val sanctifiedForge = Cards.invent("Sanctified Forge", null, "Land — Mountain Plains", """
        ({T}: Add {R} or {W}.)
        As this land enters, you may pay 2 life. If you don't, it enters tapped.
    """)
    private val rogue = Cards.invent("Ninth District Footpad", "{1}{B}", "Creature — Human Rogue", "", "2/2")
    private val aura = Cards.invent("Ninth District Favor", "{W}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +1/+1.
    """)
    private val pinger = Cards.invent("Ninth District Pinger", "{1}{R}", "Creature — Human Wizard", "{T}: This creature deals 1 damage to any target.", "1/1")

    // ---- Chunk 11: Orzhov

    @Test
    fun syndicateAristocratGainsProtection() = scenario {
        alice.battlefield(syndicateAristocrat, bear)
        alice.activates(syndicateAristocrat, choosing = listOf(bear))
        resolveAll()
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Red))
        resolveAll()
        expect { syndicateAristocrat hasKeyword Keyword.Protection }
    }

    @Test
    fun oathpactAngelLeavesAClericThatBringsItBack() = scenario {
        alice.battlefield(oathpactAngel, "Swamp", "Plains", "Swamp", "Swamp", "Plains", "Swamp", "Swamp")
        alice.hand(doom)
        alice.library(bear)
        bob.library(bear)
        alice.casts(doom, target = oathpactAngel, paying = listOf("Swamp"))
        resolveAll()
        expect { alice.battlefield has "Cleric Token" }
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        alice.activates("Cleric Token")
        resolveAll()
        expect { alice.battlefield has oathpactAngel }
    }

    @Test
    fun highPriestOfAtonementDestroysWhenHurt() = scenario {
        alice.battlefield("Mountain", highPriestOfAtonement)
        alice.hand(shock)
        bob.battlefield(idol)
        alice.casts(shock, target = highPriestOfAtonement)
        resolveAll()
        targetsIfAsked(idol)
        resolveAll()
        may(true)
        resolveAll()
        expect { bob.graveyard has idol }
    }

    @Test
    fun ruthlessEvictionExilesAllCreatures() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Swamp", "Swamp", bear, idol)
        alice.hand(ruthlessEviction)
        bob.battlefield(ogre)
        alice.casts(ruthlessEviction, modes = listOf(1))
        resolveAll()
        expect {
            alice.exile has bear
            bob.exile has ogre
            alice.battlefield has idol
        }
    }

    // CR 603.7: returns at the beginning of your next upkeep with haste
    @Test
    fun obzeratDrainsAndBlinksToDodge() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp", "Swamp")
        alice.hand(obzeratPhantomCouncil)
        alice.library(bear)
        bob.library(bear)
        alice.casts(obzeratPhantomCouncil)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
        advanceTo(TurnStep.End)
        resolveAll()
        may(true)
        resolveAll()
        expect { alice.exile has obzeratPhantomCouncil }
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect {
            alice.battlefield has obzeratPhantomCouncil
            bob.life shouldBe 16
        }
    }

    @Test
    fun thousandLashShackleLocksAndDrains() = scenario {
        alice.battlefield("Plains", "Plains", "Swamp", "Swamp")
        alice.hand(thousandLashShackle)
        bob.battlefield(ogre)
        bob.library(bear)
        alice.casts(thousandLashShackle, target = ogre)
        resolveAll()
        expect { ogre hasMechanic { it is Mechanic.CantAttack } }
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun syndicateCharmDestroysForLife() = scenario {
        alice.battlefield("Plains", "Swamp")
        alice.hand(syndicateCharm)
        bob.battlefield(ogre)
        alice.casts(syndicateCharm, modes = listOf(1), target = ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            alice.life shouldBe 17
        }
    }

    @Test
    fun syndicateCharmReturnsAnAuraedCreature() = scenario {
        alice.battlefield("Plains", "Swamp", "Plains", bear)
        alice.hand(aura, syndicateCharm)
        alice.casts(aura, target = bear, paying = listOf("Plains"))
        resolveAll()
        alice.casts(syndicateCharm, modes = listOf(0), target = bear)
        resolveAll()
        expect {
            alice.hand has bear
            alice.hand has aura
        }
    }

    @Test
    fun cleanseTheProfaneDiscardsAndGains() = scenario {
        alice.battlefield("Plains", "Swamp", "Plains", "Swamp")
        alice.hand(cleanseTheProfane)
        bob.hand(ogre, giant)
        alice.casts(cleanseTheProfane, target = "bob")
        resolveAll()
        expect {
            bob.hand.isEmpty()
            alice.life shouldBe 22
        }
    }

    // CR 603.7: a delayed "whenever you gain life this turn"
    @Test
    fun tithingGuildmageTurnsGainsIntoDrains() = scenario {
        alice.battlefield(tithingGuildmage, "Plains", "Swamp", "Plains", "Plains")
        alice.hand(heal)
        alice.activates(tithingGuildmage, ability = 1, paying = listOf("Plains", "Swamp", "Plains#1"))
        resolveAll()
        alice.casts(heal)
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun summonApparitionExilesAndMakesASpirit() = scenario {
        alice.battlefield("Plains")
        alice.hand(summonApparition)
        bob.graveyard(ogre)
        alice.casts(summonApparition, target = ogre)
        resolveAll()
        expect {
            bob.exile has ogre
            "Spirit Token" hasKeyword Keyword.Flying
        }
    }

    @Test
    fun blessingOfOrzhovaPumpsWithFlyingAndLifelink() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", bear)
        alice.hand(blessingOfOrzhova)
        alice.casts(blessingOfOrzhova, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear hasKeyword Keyword.Flying
            bear hasKeyword Keyword.Lifelink
        }
    }

    @Test
    fun eternalServitudeReturnsByManaValue() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Swamp", "Swamp")
        alice.hand(eternalServitude)
        alice.graveyard(bear, rogue, ogre)
        alice.casts(eternalServitude, x = 2)
        resolveAll()
        expect {
            alice.battlefield has bear
            alice.battlefield has rogue
            alice.graveyard has ogre
        }
    }

    // ---- Chunk 12: Dimir

    // CR 406.3: face-down exile; CR 607.2a: cards exiled with it
    @Test
    fun whisperAlleyBrokerHidesAndReturnsACard() = scenario {
        alice.battlefield(whisperAlleyBroker, "Island", "Swamp")
        alice.hand(ogre)
        alice.library(bear)
        alice.activates(whisperAlleyBroker, ability = 0)
        resolveAll()
        alice.chooses(ogre)
        resolveAll()
        expect {
            alice.hand.names shouldBe listOf(bear)
            alice.exile has ogre
        }
    }

    @Test
    fun devouringAberrationMillsOnEachSpell() = scenario {
        alice.battlefield(devouringAberration, "Island")
        alice.hand(ponder)
        alice.library(ogre)
        bob.library(bear, "Forest", giant)
        alice.casts(ponder)
        resolveAll()
        expect {
            bob.library.names shouldBe listOf(giant)
            devouringAberration.power shouldBe 2
        }
    }

    @Test
    fun shadowCharmDestroysASmallCreature() = scenario {
        alice.battlefield("Island", "Swamp")
        alice.hand(shadowCharm)
        bob.battlefield(bear)
        alice.casts(shadowCharm, modes = listOf(1), target = bear)
        resolveAll()
        expect { bob.graveyard has bear }
    }

    @Test
    fun shadowCharmPutsOneBack() = scenario {
        alice.battlefield("Island", "Swamp")
        alice.hand(shadowCharm)
        bob.library(bear, ogre, giant, human)
        alice.casts(shadowCharm, modes = listOf(2), target = "bob")
        resolveAll()
        alice.chooses(giant)
        resolveAll()
        expect { bob.library.names shouldBe listOf(giant, human) }
    }

    @Test
    fun dreadHorrorBouncesAndDiscards() = scenario {
        alice.battlefield("Island", "Island", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(dreadHorror)
        bob.battlefield(ogre)
        alice.casts(dreadHorror)
        resolveAll()
        targetsIfAsked(ogre)
        resolveAll()
        if (pending is Decision.ChooseObjects) bob.chooses(ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun shadowcloakGuildmageMillsAndDrains() = scenario {
        alice.battlefield(shadowcloakGuildmage, "Island", "Swamp", "Island", "Swamp", "Island", "Swamp", "Island")
        bob.library(bear, ogre, giant)
        alice.activates(shadowcloakGuildmage, ability = 0, paying = listOf("Island", "Swamp", "Island#1"))
        resolveAll()
        alice.activates(shadowcloakGuildmage, ability = 1, target = "bob")
        resolveAll()
        expect {
            bob.library.names shouldBe listOf(giant)
            bob.life shouldBe 18
        }
    }

    @Test
    fun shadowcloakSeerRevealsForEveryone() = scenario {
        alice.battlefield(shadowcloakSeer)
        alice.library(bear, ogre)
        bob.library(giant, ogre, bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect {
            alice.hand has bear
            alice.life shouldBe 18
            bob.life shouldBe 17
        }
    }

    // CR 707.9b: a copy with exceptions
    @Test
    fun vazalCopiesAFallenCreature() = scenario {
        alice.battlefield("Swamp", vazalShadowMastermind)
        alice.hand(doom)
        bob.battlefield(giant)
        alice.casts(doom, target = giant)
        resolveAll()
        may(true)
        resolveAll()
        expect {
            vazalShadowMastermind.power shouldBe 5
            vazalShadowMastermind hasKeyword Keyword.Hexproof
        }
    }

    @Test
    fun mindgrinderMillsToXLands() = scenario {
        alice.battlefield("Island", "Swamp", "Island", "Swamp")
        alice.hand(mindgrinder)
        bob.library(bear, "Forest", ogre, "Plains", giant)
        alice.casts(mindgrinder, x = 2)
        resolveAll()
        expect { bob.library.names shouldBe listOf(giant) }
    }

    @Test
    fun boneStriderReturnsToTheHand() = scenario {
        alice.battlefield("Swamp", boneStrider)
        alice.hand(doom)
        alice.casts(doom, target = boneStrider)
        resolveAll()
        expect { alice.hand has boneStrider }
    }

    @Test
    fun psychicJoltCountersAndMills() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(ogre)
        bob.library(bear, giant, human)
        alice.battlefield("Island", "Island", "Swamp")
        alice.hand(psychicJolt)
        turn(active = bob)
        bob.casts(ogre)
        bob.passes()
        alice.casts(psychicJolt, target = ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            bob.library.names shouldBe listOf(human)
        }
    }

    // CR 602.2: "Only your opponents may activate this ability"
    @Test
    fun soulHostageStealsUntilTheyPay() = scenario {
        alice.battlefield("Island", "Island", "Swamp", "Swamp")
        alice.hand(soulHostage)
        bob.battlefield(giant)
        bob.hand(ogre, bear)
        bob.library(human, idol)
        alice.casts(soulHostage, target = giant)
        resolveAll()
        expect { giant.controller shouldBe alice.id }
        alice.passes()
        bob.activates(soulHostage, choosing = listOf(ogre, bear))
        resolveAll()
        expect {
            giant.controller shouldBe bob.id
            alice.graveyard has soulHostage
        }
    }

    @Test
    fun forcedConfessionDrawsPerCreature() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Swamp")
        alice.hand(forcedConfession)
        alice.library(idol, charm)
        bob.library(bear, "Forest", ogre, ponder)
        alice.casts(forcedConfession, target = "bob")
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun shadowcultRogueOnlyBlockedByRogues() = scenario {
        alice.battlefield(shadowcultRogue)
        bob.battlefield(ogre, rogue)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(shadowcultRogue)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to shadowcultRogue) }
        bob.blocks(rogue to shadowcultRogue)
    }

    @Test
    fun veilstalkerSpecterPlaysTheirExiledCard() = scenario {
        alice.battlefield(veilstalkerSpecter)
        bob.library("Forest", bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(veilstalkerSpecter)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { bob.exile has "Forest" }
        advanceTo(TurnStep.PostcombatMain)
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }

    // ---- Chunk 13: Boros

    @Test
    fun musterTheLegionGrowsEachUpkeep() = scenario {
        alice.battlefield(musterTheLegion)
        alice.library(bear)
        bob.library(bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        expect {
            musterTheLegion.counters(CounterKind.Named("muster")) shouldBe 1
            "Soldier Token" hasKeyword Keyword.Haste
        }
    }

    @Test
    fun aurelissWrathDividesAndSilences() = scenario {
        alice.battlefield("Mountain", "Plains", "Mountain", "Mountain")
        alice.hand(aurelissWrath)
        bob.battlefield(ogre)
        alice.casts(aurelissWrath, x = 2, targets = listOf(ogre))
        resolveAll()
        expect {
            ogre.tapped shouldBe true
            card(ogre).view.obj?.damage shouldBe 2
        }
    }

    @Test
    fun legionCharmBurnsAPlayer() = scenario {
        alice.battlefield("Mountain", "Plains")
        alice.hand(legionCharm)
        alice.casts(legionCharm, modes = listOf(0), target = "bob")
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    @Test
    fun bastionCyclopsGrowsAttackingAndBlocking() = scenario {
        alice.battlefield(bastionCyclops)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bastionCyclops)
        resolveAll()
        expect { bastionCyclops.power shouldBe 6 }
    }

    @Test
    fun forgeChampionBurnsByCreatures() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Plains", "Plains", bear)
        alice.hand(forgeChampion)
        alice.casts(forgeChampion)
        resolveAll()
        targets("bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun battleGloryPumpsTwo() = scenario {
        alice.battlefield("Mountain", "Plains", bear, ogre)
        alice.hand(battleGlory)
        alice.casts(battleGlory, targets = listOf(bear, ogre))
        resolveAll()
        expect {
            bear.power shouldBe 5
            ogre.toughness shouldBe 6
        }
    }

    @Test
    fun sparkLegionnaireIsSacrificedAtTheEnd() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Plains")
        alice.hand(sparkLegionnaire)
        alice.casts(sparkLegionnaire)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { alice.graveyard has sparkLegionnaire }
    }

    @Test
    fun sunforgeGuildmageMakesHastySoldiers() = scenario {
        alice.battlefield(sunforgeGuildmage, "Mountain", "Plains", "Mountain", "Plains")
        alice.activates(sunforgeGuildmage, ability = 1)
        resolveAll()
        expect { "Soldier Token" hasKeyword Keyword.Haste }
    }

    @Test
    fun brightfirePaladinPumpsAndStrikesFirst() = scenario {
        alice.battlefield(brightfirePaladin, "Mountain", "Plains", "Mountain", "Plains")
        alice.activates(brightfirePaladin, ability = 0, paying = listOf("Mountain", "Plains"))
        resolveAll()
        alice.activates(brightfirePaladin, ability = 1, paying = listOf("Mountain#1", "Plains#1"))
        resolveAll()
        expect {
            brightfirePaladin.power shouldBe 4
            brightfirePaladin hasKeyword Keyword.FirstStrike
        }
    }

    @Test
    fun arrowsOfRetributionHitsAnAttacker() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(arrowsOfRetribution)
        bob.battlefield(ogre)
        bob.library(bear)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(ogre)
        bob.passes()
        alice.casts(arrowsOfRetribution, target = ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun legionReckonerPassesDamageOn() = scenario {
        alice.battlefield("Mountain", legionReckoner)
        alice.hand(shock)
        alice.casts(shock, target = legionReckoner)
        resolveAll()
        targets("bob")
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun shatteringStrikeExilesAnArtifact() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(shatteringStrike)
        bob.battlefield(idol)
        alice.casts(shatteringStrike, target = idol)
        resolveAll()
        expect { bob.exile has idol }
    }

    // ---- Chunk 14: Gruul

    @Test
    fun borgorathDiscardsLandsToBurn() = scenario {
        alice.battlefield(borgorathUnleashed)
        alice.hand("Forest")
        alice.activates(borgorathUnleashed, target = "bob", choosing = listOf("Forest"))
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun clanInsurrectionHitsSeveral() = scenario {
        alice.battlefield("Mountain", "Forest", "Mountain", "Forest")
        alice.hand(clanInsurrection)
        bob.battlefield(flyer, ogre)
        alice.casts(clanInsurrection, x = 2, modes = listOf(0, 2), targets = listOf(flyer, "bob"))
        resolveAll()
        expect {
            bob.graveyard has flyer
            bob.life shouldBe 18
        }
    }

    @Test
    fun domrakFightsWithACreature() = scenario {
        alice.battlefield(domrakWildheart, counters = listOf(CounterKind.Loyalty to 3))
        alice.battlefield(giant)
        bob.battlefield(ogre)
        alice.activates(domrakWildheart, ability = 1, targets = listOf(giant, ogre))
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun frenziedPlowingReplacesALand() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Forest", "Forest")
        alice.hand(frenziedPlowing)
        alice.library(bear, "Plains")
        bob.battlefield("Island")
        alice.casts(frenziedPlowing, target = "Island")
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses("Plains")
        resolveAll()
        expect {
            bob.graveyard has "Island"
            alice.battlefield has "Plains"
        }
    }

    @Test
    fun groundPoundByLands() = scenario {
        alice.battlefield("Mountain", "Forest", "Forest")
        alice.hand(groundPound)
        bob.battlefield(ogre)
        alice.casts(groundPound, target = ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun ragingCharmBurnsFlyers() = scenario {
        alice.battlefield("Mountain", "Forest")
        alice.hand(ragingCharm)
        bob.battlefield(flyer, ogre)
        alice.casts(ragingCharm, modes = listOf(2))
        resolveAll()
        expect {
            bob.graveyard has flyer
            bob.battlefield has ogre
        }
    }

    @Test
    fun ragebornBeastFightsAsItEnters() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Forest", "Forest", "Forest", "Forest")
        alice.hand(ragebornBeast)
        bob.battlefield(giant)
        alice.casts(ragebornBeast)
        resolveAll()
        targetsIfAsked(giant)
        resolveAll()
        expect { bob.graveyard has giant }
    }

    @Test
    fun primalVisitPumpsWithHaste() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Forest", "Forest")
        alice.battlefield(bear, sick = true)
        alice.hand(primalVisit)
        alice.casts(primalVisit, target = bear)
        resolveAll()
        expect {
            bear.power shouldBe 5
            bear hasKeyword Keyword.Haste
        }
    }

    @Test
    fun signalTheTribesKeepsOneAtRandom() = scenario {
        alice.battlefield("Mountain", "Forest")
        alice.hand(signalTheTribes)
        alice.library(bear, ogre, giant, "Island")
        alice.casts(signalTheTribes)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear, ogre, giant)
        resolveAll()
        expect {
            alice.hand.size shouldBe 1
            alice.library.size shouldBe 3
        }
    }

    @Test
    fun rubblebeltGuildmageAnimatesALand() = scenario {
        alice.battlefield(rubblebeltGuildmage, "Mountain", "Forest", "Mountain", "Plains")
        alice.activates(rubblebeltGuildmage, ability = 1, target = "Plains", paying = listOf("Mountain", "Forest", "Mountain#1"))
        resolveAll()
        expect { "Plains".power shouldBe 4 }
    }

    @Test
    fun kindlingEmissaryAddsMana() = scenario {
        alice.battlefield("Mountain", "Forest")
        alice.hand(kindlingEmissary)
        alice.casts(kindlingEmissary)
        resolveAll()
        expect { alice.pool.size shouldBe 2 }
    }

    @Test
    fun arenaFightFights() = scenario {
        alice.battlefield("Forest", "Forest", giant)
        alice.hand(arenaFight)
        bob.battlefield(ogre)
        alice.casts(arenaFight, targets = listOf(giant, ogre))
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun rubbleRaidersGrowsPerAttacker() = scenario {
        alice.battlefield(rubbleRaiders, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rubbleRaiders, bear)
        resolveAll()
        expect { rubbleRaiders.counters(p1p1) shouldBe 2 }
    }

    // ---- Chunk 15: Simic

    // CR 104.2b: an effect that says a player wins
    @Test
    fun geneVisionaryWinsWithFour() = scenario {
        alice.battlefield(geneVisionary, geneVisionary, geneVisionary, geneVisionary)
        advanceTo(TurnStep.End)
        resolveAll()
        assertTrue(state.player(bob.id).lost)
    }

    @Test
    fun hydroshapeAnimatesALand() = scenario {
        alice.battlefield("Forest", "Island", "Plains")
        alice.hand(hydroshape)
        alice.casts(hydroshape, target = "Plains")
        resolveAll()
        expect {
            "Plains".power shouldBe 3
            "Plains" hasKeyword Keyword.Flying
        }
    }

    @Test
    fun arcaneGenesisCountersAndMakesAnOoze() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain")
        bob.hand(ogre)
        alice.battlefield("Forest", "Forest", "Island", "Island", "Island")
        alice.hand(arcaneGenesis)
        turn(active = bob)
        bob.casts(ogre)
        bob.passes()
        alice.casts(arcaneGenesis, target = ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            "Ooze Token".power shouldBe 3
        }
    }

    @Test
    fun cloudSwimmerEntersWithXCounters() = scenario {
        alice.battlefield("Forest", "Island", "Forest", "Island")
        alice.hand(cloudSwimmer)
        alice.casts(cloudSwimmer, x = 2)
        resolveAll()
        expect { cloudSwimmer.power shouldBe 2 }
    }

    @Test
    fun firstSpeakerZelanaEntersByTheGreatestPower() = scenario {
        alice.battlefield("Forest", "Forest", "Island", "Island", "Island", "Forest", ogre)
        alice.hand(firstSpeakerZelana)
        alice.library(bear, giant, human, idol)
        alice.casts(firstSpeakerZelana)
        resolveAll()
        expect {
            firstSpeakerZelana.power shouldBe 4
            alice.hand.size shouldBe 4
        }
    }

    @Test
    fun hybridCharmPumps() = scenario {
        alice.battlefield("Forest", "Island", bear)
        alice.hand(hybridCharm)
        alice.casts(hybridCharm, modes = listOf(0), target = bear)
        resolveAll()
        expect { bear.power shouldBe 5 }
    }

    @Test
    fun startlingResultsCastsTheRevealedCard() = scenario {
        alice.battlefield("Forest", "Forest", "Island", "Island")
        alice.hand(startlingResults)
        alice.library(bear)
        alice.casts(startlingResults)
        resolveAll()
        if (pending is Decision.May) may(true)
        resolveAll()
        expect { alice.battlefield has bear }
    }

    @Test
    fun civicEvolutionDrawsAndAllowsALand() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Island", "Island")
        alice.hand(civicEvolution, "Plains")
        alice.library("Swamp", bear, ogre)
        alice.plays("Plains")
        alice.casts(civicEvolution)
        resolveAll()
        alice.plays("Swamp")
        expect { alice.battlefield has "Swamp" }
    }

    // CR 613.4b: base power and toughness X/X
    @Test
    fun biomassShiftSetsBaseSizes() = scenario {
        alice.battlefield("Forest", "Island", "Forest", "Island", "Forest", bear, giant)
        alice.hand(biomassShift)
        alice.casts(biomassShift, x = 3)
        resolveAll()
        expect {
            bear.power shouldBe 3
            giant.toughness shouldBe 3
        }
    }

    // ---- Chunk 16: artifacts and lands

    /** Each Keyrune becomes its creature until end of turn. */
    // CR 613.1d
    @Test
    fun eachKeyruneBecomesACreature() {
        listOf(legionKeyrune to 1, shadowKeyrune to 2, clanKeyrune to 3, syndicateKeyrune to 1, hybridKeyrune to 2).forEach { (keyrune, power) ->
            scenario {
                alice.battlefield(keyrune, "Plains", "Island", "Swamp", "Mountain", "Forest")
                alice.activates(keyrune, ability = 1)
                resolveAll()
                expect {
                    keyrune.power shouldBe power
                    keyrune.types.toSet() shouldBe setOf(CardType.Artifact, CardType.Creature)
                }
            }
        }
    }

    // CR 707.10: copying an activated ability
    @Test
    fun mirrorImageBracersCopiesAnAbility() = scenario {
        alice.battlefield(mirrorImageBracers, pinger, "Plains", "Plains", "Plains")
        alice.activates(mirrorImageBracers, target = pinger)
        resolveAll()
        alice.activates(pinger, target = "bob")
        resolveAll()
        if (pending is Decision.May) may(false)
        resolveAll()
        expect { bob.life shouldBe 18 }
    }

    @Test
    fun augursPrismDrawsAndFixes() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(augursPrism)
        alice.library(bear)
        alice.casts(augursPrism, paying = listOf("Plains", "Plains#1"))
        resolveAll()
        expect { alice.hand has bear }
        alice.activates(augursPrism, color = Color.Black)
        expect { alice.pool.size shouldBe 1 }
    }

    @Test
    fun bladetipWhipPings() = scenario {
        alice.battlefield(bladetipWhip, "Plains")
        alice.activates(bladetipWhip, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    @Test
    fun riotersGearEquips() = scenario {
        alice.battlefield(riotersGear, bear, "Plains", "Plains")
        alice.activates(riotersGear, target = bear)
        resolveAll()
        expect { bear.toughness shouldBe 4 }
    }

    @Test
    fun skyblindStaffEvadesFlyers() = scenario {
        alice.battlefield(skyblindStaff, bear, "Plains", "Plains", "Plains")
        bob.battlefield(flyer)
        alice.activates(skyblindStaff, target = bear)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(flyer to bear) }
    }

    @Test
    fun legionGatehouseEntersTapped() = scenario {
        alice.hand(legionGatehouse)
        alice.plays(legionGatehouse)
        expect { legionGatehouse.tapped shouldBe true }
    }

    @Test
    fun sanctifiedForgeEntersUntappedForLife() = scenario {
        alice.hand(sanctifiedForge)
        alice.plays(sanctifiedForge)
        may(true)
        expect {
            sanctifiedForge.tapped shouldBe false
            alice.life shouldBe 18
        }
    }
}
