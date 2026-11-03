package arcanum.engine.sets.tdm

import arcanum.api.CardType
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tarkir: Dragonstorm cards of parser chunks 09–10 in play: the white and blue cards. Horde Devotee and Frontier Devotee's mana abilities are the template of the five devotees (Sultai, Houselord and Wayfarer Devotee repeat it).
 */
class TdmWhiteAndBlueCardsScenariosTest {
    private val dawnwallSunshield = Cards.invent("Dawnwall Sunshield", "{3}{W}", "Creature — Human Warrior", """
        When this creature enters, exile up to two target cards from a single graveyard.
        {W}, {T}: Tap target creature.
    """, "3/4")
    private val bearerOfHonor = Cards.invent("Bearer of Honor", "{1}{W}", "Creature — Human Soldier", """
        During your turn, this creature has first strike.
        {4}{W}: Creatures you control get +1/+1 until end of turn.
    """, "2/1")
    private val concertedManeuver = Cards.invent("Concerted Maneuver", "{1}{W}", "Instant", """
        Choose one —
        • Concerted Maneuver deals damage equal to the number of creatures you control to target creature or planeswalker.
        • Destroy target enchantment.
    """)
    private val oathPastDeath = Cards.invent("Oath Past Death", "{1}{W}", "Instant", """
        As an additional cost to cast this spell, sacrifice a creature.
        Creatures you control gain indestructible until end of turn. Put a +1/+1 counter on each creature you control. (Damage and effects that say "destroy" don't destroy those creatures.)
    """)
    private val aurelleStormBreaker = Cards.invent("Aurelle, Storm Breaker", "{3}{W}{W}", "Legendary Planeswalker — Elspeth", """
        If one or more tokens would be created under your control, twice that many of those tokens are created instead.
        [+1]: Create a 1/1 white Soldier creature token.
        [0]: Put a +1/+1 counter on each creature you control. Those creatures gain flying until your next turn.
        [−3]: Destroy target creature an opponent controls with mana value 3 or greater.
    """, loyalty = "5")
    private val wrathfulForebear = Cards.invent("Wrathful Forebear", "{1}{W}", "Creature — Spirit Warrior", "Whenever a creature you control dies while this card is in your graveyard, you may pay {1}{W}. If you do, return this card from your graveyard to your hand.", "3/1")
    private val swiftstepTechnique = Cards.invent("Swiftstep Technique", "{1}{W}", "Instant", "Put a +1/+1 counter on target creature. It gains flying and indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)")
    private val pachydermWarPriest = Cards.invent("Pachyderm War Priest", "{4}{W}", "Creature — Elephant Cleric", "At the beginning of combat on your turn, put a +1/+1 counter on another target creature you control.", "3/5")
    private val hordeDevotee = Cards.invent("Horde Devotee", "{W}", "Creature — Human Scout", """
        When this creature enters, scry 2. (Look at the top two cards of your library, then put any number of them on the bottom and the rest on top in any order.)
        {1}: Add {R}, {W}, or {B}. Activate only once each turn.
    """, "1/2")
    private val rebelsStrike = Cards.invent("Rebel's Strike", "{1}{W}", "Instant", """
        Target creature gets +3/+0 until end of turn.
        Draw a card.
    """)
    private val caravanPackbeast = Cards.invent("Caravan Packbeast", "{5}{W}", "Creature — Beast", """
        Affinity for creatures (This spell costs {1} less to cast for each creature you control.)
        When this creature enters, draw a card.
    """, "4/3")
    private val laughAtTheGrave = Cards.invent("Laugh at the Grave", "{3}{W}{W}", "Enchantment", "At the beginning of your upkeep, return up to two target creature cards with power 2 or less from your graveyard to the battlefield. Put a +1/+1 counter on each of those creatures.")
    private val wideEyedSkyrider = Cards.invent("Wide-Eyed Skyrider", "{2}{W}", "Creature — Human Scout", """
        Flying
        Whenever this creature attacks, another target creature you control gains flying until end of turn.
        Attacking tokens you control have flying.
    """, "1/3")
    private val cracklingSnare = Cards.invent("Crackling Snare", "{4}{W}", "Enchantment", """
        Flash
        This spell costs {1} less to cast for each attacking creature.
        When this enchantment enters, exile target artifact or creature an opponent controls until this enchantment leaves the battlefield.
    """)
    private val stormsignalBlade = Cards.invent("Stormsignal Blade", "{1}{W}", "Artifact — Equipment", """
        Equipped creature gets +3/+0.
        Whenever equipped creature attacks, draw a card if you control three or more attacking creatures.
        Equip {2} ({2}: Attach to target creature you control. Equip only as a sorcery.)
    """)
    private val dawnpearlKirin = Cards.invent("Dawnpearl Kirin", "{1}{W}", "Creature — Kirin", """
        Flash
        Flying
        When this creature enters, return up to one other target nonland permanent you control to its owner's hand. If it was a token, draw a card.
    """, "2/1")
    private val galeHawk = Cards.invent("Gale Hawk", "{2}{W}", "Creature — Bird", """
        Flying
        Whenever this creature deals combat damage to a player, you may search your library for a card named Gale Hawk, reveal it, put it into your hand, then shuffle.
        A deck can have any number of cards named Gale Hawk.
    """, "2/2")
    private val joinedBattlefront = Cards.invent("Joined Battlefront", "{3}{W}", "Sorcery", "Look at the top seven cards of your library. Put up to two noncreature, nonland permanent cards with mana value 3 or less from among them onto the battlefield. Put the rest on the bottom of your library in a random order.")
    private val wardSculptor = Cards.invent("Ward Sculptor", "{3}{U}", "Creature — Bird Wizard", """
        Flying
        Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
        At the beginning of your upkeep, you may exile two cards from your graveyard. If you do, put a +1/+1 counter on this creature.
    """, "2/3")
    private val lumberingStormshell = Cards.invent("Lumbering Stormshell", "{3}{U}{U}", "Creature — Turtle", """
        Ward {2}
        Whenever this creature attacks, put three stun counters on it and draw three cards. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        Whenever you cast a Turtle spell, untap this creature.
    """, "5/9")
    private val bafflingBlizzard = Cards.invent("Baffling Blizzard", "{4}{U}{U}", "Instant", "Draw three cards. Creatures your opponents control get -3/-0 until end of turn.")
    private val essenceTether = Cards.invent("Essence Tether", "{2}{U}", "Artifact", """
        At the beginning of your upkeep, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
        {T}: Create a 2/2 black Zombie Druid creature token. Activate only during your turn and only if a card left your graveyard this turn.
    """)
    private val cleanSlateHex = Cards.invent("Clean Slate Hex", "{1}{U}", "Enchantment — Aura", """
        Flash
        Enchant creature
        Enchanted creature gets -5/-0 and loses all abilities.
    """)
    private val chasteningElder = Cards.invent("Chastening Elder", "{U}", "Creature — Human Monk", """
        Flash
        When this creature enters, target creature an opponent controls gets -2/-0 until end of turn.
    """, "1/2")
    private val frostridgeSerpent = Cards.invent("Frostridge Serpent", "{4}{U}", "Creature — Serpent", "When this creature enters, return target creature an opponent controls to its owner's hand.", "3/3")
    private val riverbankTrawlers = Cards.invent("Riverbank Trawlers", "{2}{U}", "Creature — Human Citizen", "When this creature enters, you may exile a creature card from your graveyard. When you do, return target instant or sorcery card from your graveyard to your hand.", "3/2")
    private val resoundingStrikeMastery = Cards.invent("Resounding Strike Mastery", "{U}", "Enchantment — Aura", """
        Enchant creature
        When this Aura enters, tap enchanted creature.
        Enchanted creature doesn't untap during its controller's untap step.
        Enchanted creature has "{5}: Untap this creature."
    """)
    private val streamstepTechnique = Cards.invent("Streamstep Technique", "{3}{U}", "Instant", """
        Choose one —
        • The owner of target nonland permanent puts it on their choice of the top or bottom of their library.
        • Counter target noncreature spell.
    """)
    private val gravecourtAppraiser = Cards.invent("Gravecourt Appraiser", "{2}{U}", "Creature — Zombie Advisor", "When this creature enters, look at the top two cards of your library. Put one of them into your hand and the other into your graveyard.", "2/1")
    private val thawwaterStag = Cards.invent("Thawwater Stag", "{3}{U}", "Creature — Elemental Elk", """
        Vigilance
        During your turn, this creature has base power and toughness 5/2.
        {5}{U}{U}: This creature can't be blocked this turn.
    """, "2/5")
    private val phantomRefusal = Cards.invent("Phantom Refusal", "{X}{U}", "Instant", """
        This spell costs {1} less to cast for each creature you control with power 4 or greater.
        Counter target spell unless its controller pays {X}.
    """)
    private val calmWithinMotion = Cards.invent("Calm Within Motion", "{1}{U}", "Enchantment", "At the beginning of your upkeep, mill three cards. Then if your library has no cards in it, exile this enchantment and put five cards from your graveyard on top of your library in any order.")
    private val frontierDevotee = Cards.invent("Frontier Devotee", "{1}{U}", "Creature — Human Druid", """
        Defender
        {1}: Add {G}, {U}, or {R}. Activate only once each turn.
    """, "3/3")
    private val seasonedIceScaler = Cards.invent("Seasoned Ice Scaler", "{1}{U}", "Creature — Human Scout", """
        Vigilance
        This creature can't be blocked.
        Whenever this creature attacks, up to one target player mills cards equal to this creature's power. (They put that many cards from the top of their library into their graveyard.)
    """, "1/3")
    private val broadwingStride = Cards.invent("Broadwing Stride", "{U}", "Enchantment — Aura", """
        Enchant creature
        Enchanted creature gets +1/+1 and has flying.
        {2}{U}: Return this Aura to its owner's hand.
    """)

    private val bear = Plain.bear
    private val ogre = Plain.ogre
    private val giant = Plain.giant
    private val dragon = Plain.dragon
    private val p1p1 = Plain.p1p1

    private val muster = Cards.invent("Steppe Muster", "{W}", "Sorcery", "Create a 1/1 white Soldier creature token.")

    // ---- Chunk 09: white

    /** As it enters, two cards from one graveyard exiled; {W}, {T}: tap a creature. */
    // CR 115.1
    @Test
    fun dawnwallSunshieldExilesAndTaps() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(dawnwallSunshield)
        bob.graveyard(ogre, giant)
        alice.casts(dawnwallSunshield, paying = listOf("Plains", "Plains#1", "Plains#2", "Plains#3"))
        playOut(ogre, giant)
        expect { bob.exile.size shouldBe 2 }
    }

    @Test
    fun dawnwallSunshieldTaps() = scenario {
        alice.battlefield(dawnwallSunshield, "Plains")
        bob.battlefield(giant)
        alice.activates(dawnwallSunshield, target = giant)
        playOut()
        expect { giant.tapped shouldBe true }
    }

    /** First strike on its own turn; {4}{W}: the team gets +1/+1. */
    @Test
    fun bearerOfHonorRallies() = scenario {
        alice.battlefield(bearerOfHonor, bear, *Array(5) { "Plains" })
        expect { bearerOfHonor hasKeyword Keyword.FirstStrike }
        alice.activates(bearerOfHonor)
        playOut()
        expect { bear.power shouldBe 3 }
    }

    /** Damage equal to the creatures you control; or an enchantment destroyed. */
    @Test
    fun concertedManeuverHits() = scenario {
        alice.battlefield("Plains", "Plains", bear, Plain.flyer, Plain.elf)
        alice.hand(concertedManeuver)
        bob.battlefield(ogre)
        alice.casts(concertedManeuver, modes = listOf(0), target = ogre)
        playOut()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun concertedManeuverDestroysAnEnchantment() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(concertedManeuver)
        bob.battlefield(Plain.charm)
        alice.casts(concertedManeuver, modes = listOf(1), target = Plain.charm)
        playOut()
        expect { bob.graveyard has Plain.charm }
    }

    /** A creature sacrificed: the rest indestructible and a counter on each. */
    // CR 601.2b
    @Test
    fun oathPastDeathFortifies() = scenario {
        alice.battlefield("Plains", "Plains", bear, ogre)
        alice.hand(oathPastDeath)
        alice.casts(oathPastDeath, choosing = listOf(bear))
        playOut()
        expect {
            alice.graveyard has bear
            ogre.counters(p1p1) shouldBe 1
            ogre hasKeyword Keyword.Indestructible
        }
    }

    /** Tokens doubled: +1 makes two Soldiers; 0: counters and flying; −3: a big creature destroyed. */
    // CR 614.1a, 606.3
    @Test
    fun aurelleDoublesTokens() = scenario {
        alice.battlefield(aurelleStormBreaker, counters = listOf(arcanum.api.CounterKind.Loyalty to 5))
        alice.activates(aurelleStormBreaker, ability = 0)
        playOut()
        expect { card("Soldier Token", 1).zone shouldBe Zone.Battlefield }
    }

    @Test
    fun aurelleCountersAndWings() = scenario {
        alice.battlefield(aurelleStormBreaker, counters = listOf(arcanum.api.CounterKind.Loyalty to 5))
        alice.battlefield(bear)
        alice.activates(aurelleStormBreaker, ability = 1)
        playOut()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Flying
        }
    }

    @Test
    fun aurelleDestroysABigCreature() = scenario {
        alice.battlefield(aurelleStormBreaker, counters = listOf(arcanum.api.CounterKind.Loyalty to 5))
        bob.battlefield(giant)
        alice.activates(aurelleStormBreaker, ability = 2, target = giant)
        playOut()
        expect { bob.graveyard has giant }
    }

    /** In the graveyard: a creature dying lets it be paid back to the hand. */
    // CR 113.6b
    @Test
    fun wrathfulForebearReturns() = scenario {
        alice.battlefield(bear, "Plains", "Plains", "Swamp")
        alice.graveyard(wrathfulForebear)
        alice.hand(Plain.doom)
        alice.casts(Plain.doom, target = bear, paying = listOf("Swamp"))
        playOut()
        expect { alice.hand has wrathfulForebear }
    }

    /** A counter, flying and indestructible. */
    @Test
    fun swiftstepTechniqueLifts() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(swiftstepTechnique)
        alice.casts(swiftstepTechnique, target = bear)
        playOut()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Flying
            bear hasKeyword Keyword.Indestructible
        }
    }

    /** At combat on its controller's turn, a counter on another creature. */
    // CR 507.1
    @Test
    fun pachydermWarPriestBlesses() = scenario {
        alice.battlefield(pachydermWarPriest, bear)
        advanceTo(TurnStep.BeginningOfCombat)
        playOut(bear)
        expect { bear.counters(p1p1) shouldBe 1 }
    }

    /** Scry 2 as it enters; {1}: one mana of three colours. */
    // CR 701.22a
    @Test
    fun hordeDevoteeScries() = scenario {
        alice.battlefield("Plains")
        alice.hand(hordeDevotee)
        alice.library(ogre, giant)
        alice.casts(hordeDevotee)
        playOut()
        expect { alice.library.size shouldBe 2 }
    }

    /** +3/+0 and a card. */
    @Test
    fun rebelsStrikeDraws() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(rebelsStrike)
        alice.library(ogre)
        alice.casts(rebelsStrike, target = bear)
        playOut()
        expect {
            bear.power shouldBe 5
            alice.hand has ogre
        }
    }

    /** Affinity for creatures: three creatures make it cost {2}{W}; a card as it enters. */
    // CR 702.41a
    @Test
    fun caravanPackbeastAffinity() = scenario {
        alice.battlefield(bear, ogre, Plain.elf, "Plains", "Plains", "Plains")
        alice.hand(caravanPackbeast)
        alice.library(giant)
        alice.casts(caravanPackbeast)
        playOut()
        expect {
            caravanPackbeast.zone shouldBe Zone.Battlefield
            alice.hand has giant
        }
    }

    /** Each upkeep, up to two small creature cards return with a counter each. */
    // CR 400.7
    @Test
    fun laughAtTheGraveReturnsSmallCreatures() = scenario {
        alice.battlefield(laughAtTheGrave)
        alice.graveyard(bear, Plain.elf, giant)
        alice.library(ogre, ogre)
        bob.library(ogre, ogre)
        advanceTo(TurnStep.Upkeep, turn = 3)
        playOut(bear, Plain.elf)
        expect {
            bear.counters(p1p1) shouldBe 1
            Plain.elf.counters(p1p1) shouldBe 1
            alice.graveyard has giant
        }
    }

    /** Attacking, another creature flies; attacking tokens fly. */
    @Test
    fun wideEyedSkyriderLendsWings() = scenario {
        alice.battlefield(wideEyedSkyrider, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wideEyedSkyrider)
        playOut(bear)
        expect { bear hasKeyword Keyword.Flying }
    }

    /** Flash; {1} less per attacker; an attacker exiled until it leaves. */
    // CR 610.3
    @Test
    fun cracklingSnareExilesAnAttacker() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(cracklingSnare)
        bob.battlefield(giant)
        turn(active = bob)
        advanceTo(TurnStep.DeclareAttackers)
        bob.attacks(giant)
        bob.passes()
        alice.casts(cracklingSnare)
        playOut(giant)
        expect { bob.exile has giant }
    }

    /** +3/+0; attacking with three, a card. */
    @Test
    fun stormsignalBladeDraws() = scenario {
        alice.battlefield(stormsignalBlade, bear, ogre, Plain.elf, "Plains", "Plains")
        alice.library(giant)
        alice.activates(stormsignalBlade, target = bear)
        playOut()
        expect { bear.power shouldBe 5 }
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear, ogre, Plain.elf)
        playOut()
        expect { alice.hand has giant }
    }

    /** A token bounced: a card. */
    // CR 704.5d
    @Test
    fun dawnpearlKirinBouncesAToken() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(muster, dawnpearlKirin)
        alice.library(ogre)
        alice.casts(muster, paying = listOf("Plains"))
        playOut()
        alice.casts(dawnpearlKirin)
        playOut("Soldier Token")
        expect {
            alice.hand has ogre
            alice.battlefield lacks "Soldier Token"
        }
    }

    @Test
    fun dawnpearlKirinBouncesAPermanent() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(dawnpearlKirin)
        alice.library(ogre)
        alice.casts(dawnpearlKirin)
        playOut(bear)
        expect {
            alice.hand has bear
            alice.library has ogre
        }
    }

    /** A hit fetches another card of its name. */
    // CR 201.2
    @Test
    fun galeHawkFindsItsKin() = scenario {
        alice.battlefield(galeHawk)
        alice.library(ogre, galeHawk)
        hits(galeHawk, picks = listOf(galeHawk))
        expect { card(galeHawk, 1).zone shouldBe Zone.Hand }
    }

    /** Two cheap noncreature permanents from the top seven onto the battlefield. */
    @Test
    fun joinedBattlefrontPutsPermanents() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(joinedBattlefront)
        alice.library(Plain.idol, ogre, Plain.charm, giant, bear, Plain.study, Plain.heal)
        alice.casts(joinedBattlefront)
        playOut(Plain.idol, Plain.charm)
        expect {
            Plain.idol.zone shouldBe Zone.Battlefield
            Plain.charm.zone shouldBe Zone.Battlefield
        }
    }

    // ---- Chunk 10: blue

    /** Each upkeep, two graveyard cards exiled for a counter. */
    @Test
    fun wardSculptorGrows() = scenario {
        alice.battlefield(wardSculptor)
        alice.graveyard(ogre, giant)
        alice.library(bear, bear)
        bob.library(bear, bear)
        advanceTo(TurnStep.Upkeep, turn = 3)
        playOut(ogre, giant)
        expect { wardSculptor.counters(p1p1) shouldBe 1 }
    }

    /** Attacking: three stun counters and three cards; a Turtle spell untaps it. */
    // CR 122.1d
    @Test
    fun lumberingStormshellDrawsThree() = scenario {
        alice.battlefield(lumberingStormshell, "Island", "Island")
        alice.hand(Plain.turtle)
        alice.library(ogre, giant, bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(lumberingStormshell)
        playOut()
        expect {
            alice.hand.size shouldBe 4
            lumberingStormshell.counters(arcanum.api.CounterKind.Stun) shouldBe 3
        }
    }

    @Test
    fun lumberingStormshellUntapsForTurtles() = scenario {
        alice.battlefield("Island", "Island")
        alice.battlefield(lumberingStormshell, tapped = true)
        alice.hand(Plain.turtle)
        alice.casts(Plain.turtle)
        playOut()
        expect { lumberingStormshell.tapped shouldBe false }
    }

    /** Three cards and the opponents' creatures get -3/-0. */
    @Test
    fun bafflingBlizzardDraws() = scenario {
        alice.battlefield(*Array(6) { "Island" })
        alice.hand(bafflingBlizzard)
        alice.library(ogre, Plain.elf, bear)
        bob.battlefield(giant)
        alice.casts(bafflingBlizzard)
        playOut()
        expect {
            alice.hand.size shouldBe 3
            giant.power shouldBe 2
        }
    }

    /** Surveil 1 each upkeep; a card left the graveyard on its controller's turn: a Zombie Druid. */
    @Test
    fun essenceTetherMakesZombies() = scenario {
        alice.battlefield(essenceTether, "Island", "Island", "Island")
        alice.graveyard(ogre, Plain.study)
        alice.hand(riverbankTrawlers)
        alice.casts(riverbankTrawlers)
        playOut(ogre, Plain.study)
        alice.activates(essenceTether)
        playOut()
        expect { alice.battlefield has "Zombie Druid Token" }
    }

    @Test
    fun essenceTetherNeedsACardToLeave() = scenario {
        alice.battlefield(essenceTether)
        assertFailsWith<Throwable> { alice.activates(essenceTether) }
    }

    /** -5/-0 and no abilities. */
    // CR 613.1f
    @Test
    fun cleanSlateHexStrips() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand(cleanSlateHex)
        bob.battlefield(Plain.flyer)
        alice.casts(cleanSlateHex, target = Plain.flyer)
        playOut()
        expect { Plain.flyer lacksKeyword Keyword.Flying }
    }

    /** As it enters, an opposing creature gets -2/-0. */
    @Test
    fun chasteningElderWeakens() = scenario {
        alice.battlefield("Island")
        alice.hand(chasteningElder)
        bob.battlefield(giant)
        alice.casts(chasteningElder)
        playOut(giant)
        expect { giant.power shouldBe 3 }
    }

    /** As it enters, an opposing creature bounced. */
    @Test
    fun frostridgeSerpentBounces() = scenario {
        alice.battlefield(*Array(5) { "Island" })
        alice.hand(frostridgeSerpent)
        bob.battlefield(giant)
        alice.casts(frostridgeSerpent)
        playOut(giant)
        expect { bob.hand has giant }
    }

    /** A creature card exiled from the graveyard: an instant or sorcery card back. */
    // CR 603.12
    @Test
    fun riverbankTrawlersReturnASpell() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(riverbankTrawlers)
        alice.graveyard(ogre, Plain.study)
        alice.casts(riverbankTrawlers)
        playOut(ogre, Plain.study)
        expect {
            alice.exile has ogre
            alice.hand has Plain.study
        }
    }

    /** As it enters, the enchanted creature taps and stays tapped. */
    // CR 502.3
    @Test
    fun resoundingStrikeMasteryLocks() = scenario {
        alice.battlefield("Island")
        alice.hand(resoundingStrikeMastery)
        bob.battlefield(giant)
        alice.casts(resoundingStrikeMastery, target = giant)
        playOut()
        expect {
            giant.tapped shouldBe true
            giant hasMechanic { it is arcanum.api.Mechanic.DoesntUntap }
        }
    }

    /** The owner puts the permanent on the top or bottom; or a noncreature spell countered. */
    @Test
    fun streamstepTechniqueTucks() = scenario {
        alice.battlefield(*Array(4) { "Island" })
        alice.hand(streamstepTechnique)
        bob.battlefield(giant)
        alice.casts(streamstepTechnique, modes = listOf(0), target = giant)
        playOut(options = listOf("bottom"))
        expect { bob.library has giant }
    }

    /** One of the top two to the hand, the other to the graveyard. */
    @Test
    fun gravecourtAppraiserSorts() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(gravecourtAppraiser)
        alice.library(ogre, giant)
        alice.casts(gravecourtAppraiser)
        playOut(giant)
        expect {
            alice.hand has giant
            alice.graveyard has ogre
        }
    }

    /** 5/2 on its controller's turn; 2/5 on the opponent's. */
    // CR 613.4b
    @Test
    fun thawwaterStagShifts() = scenario {
        alice.battlefield(thawwaterStag)
        expect { thawwaterStag.power shouldBe 5 }
    }

    @Test
    fun thawwaterStagOnTheOpponentsTurn() = scenario {
        alice.battlefield(thawwaterStag)
        turn(active = bob)
        expect { thawwaterStag.toughness shouldBe 5 }
    }

    /** {1} less per big creature; a spell countered unless X is paid. */
    @Test
    fun phantomRefusalCounters() = scenario {
        alice.battlefield("Island", "Island", "Island", giant)
        alice.hand(phantomRefusal)
        bob.battlefield("Mountain")
        bob.hand(Plain.shock)
        turn(active = bob)
        bob.casts(Plain.shock, target = "alice")
        bob.passes()
        alice.casts(phantomRefusal, target = Plain.shock, x = 2)
        playOut(may = false)
        expect { alice.life shouldBe 20 }
    }

    /** Each upkeep, three cards milled; with the library empty, it's exiled and five cards go back on top. */
    // CR 701.17a
    @Test
    fun calmWithinMotionRefills() = scenario {
        alice.battlefield(calmWithinMotion)
        alice.graveyard(ogre, giant, bear, Plain.study, Plain.heal)
        alice.library(Plain.elf, Plain.elf, Plain.elf)
        bob.library(ogre)
        advanceTo(TurnStep.Upkeep, turn = 3)
        playOut(ogre, giant, bear, Plain.study, Plain.heal)
        expect {
            alice.exile has calmWithinMotion
            alice.library.size shouldBe 5
        }
    }

    /** Defender; {1}: mana once a turn. */
    @Test
    fun frontierDevoteeMakesMana() = scenario {
        alice.battlefield(frontierDevotee, "Island", "Island")
        alice.hand(Plain.bear)
        alice.activates(frontierDevotee, color = arcanum.api.Color.Green, paying = listOf("Island"))
        alice.casts(Plain.bear)
        playOut()
        expect { bear.zone shouldBe Zone.Battlefield }
    }

    /** Unblockable; attacking, a player mills equal to its power. */
    @Test
    fun seasonedIceScalerMills() = scenario {
        alice.battlefield(seasonedIceScaler)
        bob.library(ogre, giant)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(seasonedIceScaler)
        playOut("bob")
        expect { bob.graveyard has ogre }
    }

    /** +1/+1 and flying; {2}{U}: back to the hand. */
    @Test
    fun broadwingStrideLifts() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", bear)
        alice.hand(broadwingStride)
        alice.casts(broadwingStride, target = bear, paying = listOf("Island"))
        playOut()
        expect { bear hasKeyword Keyword.Flying }
        alice.activates(broadwingStride)
        playOut()
        expect { alice.hand has broadwingStride }
    }
}
