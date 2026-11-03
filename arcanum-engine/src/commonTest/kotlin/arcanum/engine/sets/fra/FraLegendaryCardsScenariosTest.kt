package arcanum.engine.sets.fra

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.change.Changes
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.fra.Fra.bear
import arcanum.engine.sets.fra.Fra.charm
import arcanum.engine.sets.fra.Fra.doom
import arcanum.engine.sets.fra.Fra.elf
import arcanum.engine.sets.fra.Fra.giant
import arcanum.engine.sets.fra.Fra.heal
import arcanum.engine.sets.fra.Fra.idol
import arcanum.engine.sets.fra.Fra.ogre
import arcanum.engine.sets.fra.Fra.p1p1
import arcanum.engine.sets.fra.Fra.ponder
import arcanum.engine.sets.fra.Fra.pump
import arcanum.engine.sets.fra.Fra.shock
import arcanum.engine.sets.fra.Fra.walker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Reality Fracture's legendary creatures in play (parser chunks 14–17). */
class FraLegendaryCardsScenariosTest {
    private val heartcordTugger = Cards.invent("Heartcord Tugger", "{3}{R}", "Creature — Elf Sorcerer", """
        Trample
        When this creature enters, create a 2/2 colorless Wizard Soldier creature token named Cadet.
    """, "3/1")
    private val lieslBladeOfPromise = Cards.invent("Liesl, Blade of Promise", "{2}{W}", "Legendary Creature — Human Knight", """
        First strike
        Whenever you cast an Equipment spell or a spell that targets a creature you control, draw a card. This ability triggers only once each turn.
    """, "2/2")
    private val brontorTheUnbudging = Cards.invent("Brontor the Unbudging", "{8}{W}", "Legendary Creature — Elder Dinosaur", """
        This spell costs {X} less to cast, where X is the greatest toughness among creatures you control.
        Creatures you control can attack as though they didn't have defender.
        Each creature you control with toughness greater than its power assigns combat damage equal to its toughness rather than its power.
    """, "0/7")
    private val oathkeepersMonument = Cards.invent("Oathkeeper's Monument", "{1}{W}", "Legendary Artifact", """
        Creature tokens you control get +1/+0 and have vigilance.
        {T}: Add one mana of any color. Spend this mana only to cast a planeswalker spell.
        {1}{W}, Discard this card: It deals 4 damage to target attacking or blocking creature.
    """)
    private val brannocOfTheHearth = Cards.invent("Brannoc of the Hearth", "{2}{W}", "Legendary Creature — Human Citizen", """
        Landfall — Whenever a land you control enters, you gain 1 life.
        Whenever a Plains you control enters, put a +1/+1 counter on target creature.
    """, "2/3")
    private val selineTheBlameless = Cards.invent("Seline the Blameless", "{W}", "Legendary Creature — Human Cleric", """
        Whenever another creature or planeswalker you control enters, you gain 1 life.
        {1}, {T}, Discard a card: Another target creature or planeswalker you control gains hexproof until end of turn.
    """, "1/1")
    private val aurielleSeraphOfDaybreak = Cards.invent("Aurielle, Seraph of Daybreak", "{2}{W}", "Legendary Creature — Angel Knight", """
        Flying
        Whenever you gain life, put a +1/+1 counter on each Angel you control.
    """, "3/3")
    private val reliefMaidenSwiftRescuer = Cards.invent("Relief Maiden, Swift Rescuer", "{2}{W}", "Legendary Creature — Human Cleric", """
        Flying
        {T}: Return another target permanent you control to its owner's hand. Activate only during your turn.
    """, "1/3")
    private val tamsinEnvoyOfVigilance = Cards.invent("Tamsin, Envoy of Vigilance", "{3}{W}{W}", "Legendary Creature — Human Advisor", """
        Flying
        Whenever you scry or surveil, create a 1/1 colorless Thopter artifact creature token with flying. This ability triggers only once each turn.
    """, "4/4")
    private val ilsaShieldlightAdept = Cards.invent("Ilsa, Shieldlight Adept", "{1}{W}", "Legendary Creature — Human Cleric", """
        Flash
        When Ilsa enters, target permanent you control gains hexproof until end of turn. Put a +1/+1 counter on it if it's a creature. Put a loyalty counter on it if it's a planeswalker. (It can't be the target of spells or abilities your opponents control.)
    """, "1/1")
    private val mirelaTheEnduring = Cards.invent("Mirela, the Enduring", "{3}{W}", "Legendary Creature — Human Soldier", """
        Lifelink
        Noncreature spells your opponents cast cost {1} more to cast.
    """, "3/4")
    private val orrinModestCopyist = Cards.invent("Orrin, Modest Copyist", "{2}{U}", "Legendary Creature — Human Wizard", """
        Whenever another nontoken creature you control enters, untap Orrin.
        {T}: Draw a card, then discard a card.
    """, "3/2")
    private val wraithOfBlessedMirela = Cards.invent("Wraith of Blessed Mirela", "{1}{U}", "Legendary Creature — Spirit Cleric", """
        Flying
        Noncreature spells you cast cost {1} less to cast.
    """, "1/2")
    private val aurielleAcademySeraph = Cards.invent("Aurielle, Academy Seraph", "{1}{U}{U}", "Legendary Creature — Angel Wizard", """
        Flying
        At the beginning of each end step, if you've drawn three or more cards this turn, create a 3/3 blue Angel creature token with flying.
        {3}{U}{U}: Until end of turn, whenever Aurielle deals combat damage to a player, draw two cards.
    """, "3/3")
    private val quillInvestigatingSleuth = Cards.invent("Quill, Investigating Sleuth", "{1}{U}", "Legendary Creature — Human Detective", "Whenever you scry or surveil, you may pay {2}. If you do, put a +1/+1 counter on Quill and draw a card. (Draw after you scry or surveil.)", "2/2")
    private val gorrumVaxBiomancer = Cards.invent("Gorrum Vax, Biomancer", "{4}{U}{U}", "Legendary Creature — Ogre Crab Wizard", """
        Flying
        Prowess, prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn twice.)
        Whenever Gorrum Vax becomes the target of a spell or ability an opponent controls, draw a card.
    """, "4/6")
    private val rinkoTakaharaRunaway = Cards.invent("Rinko Takahara, Runaway", "{1}{U}", "Legendary Creature — Human Rogue", "Creatures you control with power or toughness 1 or less can't be blocked.", "1/3")
    private val vortanScholasticWarden = Cards.invent("Vortan, Scholastic Warden", "{3}{U}", "Legendary Artifact Creature — Dragon Construct", """
        This spell costs {2} less to cast if you've cast a noncreature spell this turn.
        Flying, vigilance
        Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
    """, "1/5")
    private val akemiLightFromTheShadows = Cards.invent("Akemi, Light from the Shadows", "{U}", "Legendary Creature — Human Ninja", """
        Flash
        When Akemi enters, choose one —
        • Target creature gets -X/-0 until end of turn, where X is the number of cards in your graveyard.
        • Surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
    """, "1/1")
    private val lieslLanceOfTorment = Cards.invent("Liesl, Lance of Torment", "{2}{B}", "Legendary Creature — Human Knight", """
        First strike
        Whenever you cast a spell that targets an opponent or a creature an opponent controls, put a +1/+1 counter on Liesl.
    """, "2/2")
    private val mirethMournfulRevenant = Cards.invent("Mireth, Mournful Revenant", "{1}{B}", "Legendary Creature — Zombie Satyr", """
        Menace (This creature can't be blocked except by two or more creatures.)
        {4}{B}, Exile another creature card from your graveyard: Return this card from your graveyard to the battlefield tapped with a +1/+1 counter on her.
    """, "2/1")
    private val selineThePenitent = Cards.invent("Seline the Penitent", "{1}{B}", "Legendary Creature — Human Warlock", """
        Whenever another creature or planeswalker you control enters, mill two cards.
        Exhaust — {5}{B}: Return target creature or planeswalker card from your graveyard to the battlefield. Put a +1/+1 counter on Seline. Activate only as a sorcery. (Activate each exhaust ability only once.)
    """, "2/2")
    private val carnageMaidenMostFeared = Cards.invent("Carnage Maiden, Most Feared", "{4}{B}", "Legendary Creature — Human Assassin", """
        Whenever another creature or planeswalker you control dies, Carnage Maiden deals 1 damage to target opponent and you gain 1 life.
        Whenever an opponent is dealt noncombat damage, put a +1/+1 counter on Carnage Maiden.
    """, "4/4")
    private val quillSinisterSchemer = Cards.invent("Quill, Sinister Schemer", "{2}{B}", "Legendary Creature — Human Rogue", """
        Threshold — You can't cast this spell unless there are seven or more cards in your graveyard.
        Menace
        {B}, Discard this card: Target creature gets -3/-1 until end of turn.
    """, "5/5")
    private val ilsaDiamondedgeMage = Cards.invent("Ilsa, Diamondedge Mage", "{3}{B}", "Legendary Creature — Human Warlock", """
        Flash
        When Ilsa enters, target permanent you control gains deathtouch until end of turn. Put a +1/+1 counter on it if it's a creature. Put a loyalty counter on it if it's a planeswalker.
    """, "3/1")
    private val smallbonesPocketPest = Cards.invent("Smallbones, Pocket Pest", "{2}{B}", "Legendary Creature — Skeleton Rogue", """
        When Smallbones enters, each opponent discards a card.
        Whenever a player discards one or more cards, Smallbones deals 1 damage to each opponent.
    """, "2/1")
    private val hollisHauntedLoner = Cards.invent("Hollis, Haunted Loner", "{2}{B}", "Legendary Creature — Human Warlock", """
        When Hollis enters, you may sacrifice a creature or planeswalker. When you do, each opponent sacrifices a creature of their choice.
        Hollis gets +1/+0 for each creature and planeswalker card in your graveyard.
    """, "0/3")
    private val orrinFamedChampion = Cards.invent("Orrin, Famed Champion", "{3}{R}", "Legendary Creature — Human Berserker", """
        Trample
        Whenever another creature you control enters, Orrin gets +X/+0 until end of turn, where X is that creature's power.
    """, "1/5")
    private val mirethTheReveler = Cards.invent("Mireth, the Reveler", "{1}{R}", "Legendary Creature — Satyr", """
        Haste
        Each other creature you control with a +1/+1 counter on it has haste.
        {1}{R}, {T}: Put a +1/+1 counter on target creature that entered this turn.
    """, "2/1")
    private val weiLanfengSolitary = Cards.invent("Wei Lanfeng, Solitary", "{4}{R}", "Legendary Creature — Human Berserker", """
        Menace (This creature can't be blocked except by two or more creatures.)
        Whenever a creature you control attacks a player alone, discard a card, then draw a card. Then put a +1/+1 counter on that creature for each card you've discarded this turn.
    """, "4/4")
    private val seraphineOfEmberAndSoot = Cards.invent("Seraphine of Ember and Soot", "{4}{R}{R}", "Legendary Creature — Merfolk Noble", """
        When Seraphine enters, create a 5/5 red Dragon creature token with flying.
        {8}: Create a 5/5 red Dragon creature token with flying.
    """, "2/2")
    private val brannocTheEarthshaper = Cards.invent("Brannoc, the Earthshaper", "{2}{R}", "Legendary Creature — Human Warrior", """
        Reach
        Landfall — Whenever a land you control enters, Brannoc deals 1 damage to each opponent. If that land is a Mountain, add {R}.
    """, "3/2")
    private val tindraTheWoodfeller = Cards.invent("Tindra, the Woodfeller", "{R}", "Legendary Creature — Elf Warrior", "{2}, {T}, Sacrifice an artifact or land: Draw a card.", "2/1")
    private val ashaSteadfastRebuilder = Cards.invent("Asha, Steadfast Rebuilder", "{2}{R}", "Legendary Creature — Human Artificer", """
        When Asha enters, create a 1/1 colorless Thopter artifact creature token with flying.
        {5}{R}: Target creature gets +X/+0 until end of turn, where X is the number of artifacts you control.
    """, "2/2")
    private val hekatTheStormGodsChosen = Cards.invent("Hekat, the Storm God's Chosen", "{1}{R}", "Legendary Creature — Human Warrior Cleric", "Creatures you control have haste.", "2/2")
    private val rinkoTakaharaHunter = Cards.invent("Rinko Takahara, Hunter", "{3}{R}", "Legendary Creature — Human Mercenary", """
        Double strike
        Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        Whenever a creature an opponent controls with power or toughness 1 or less blocks, Rinko Takahara deals 1 damage to that creature's controller.
    """, "2/4")
    private val dovanGuildSparkwright = Cards.invent("Dovan, Guild Sparkwright", "{1}{R}", "Legendary Creature — Human Wizard", """
        Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        If a source you control would deal noncombat damage to an opponent or a permanent an opponent controls, it deals that much damage plus 1 instead.
    """, "1/2")
    private val hollisTeamChampion = Cards.invent("Hollis, Team Champion", "{4}{R}", "Legendary Creature — Human Warrior", """
        Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
        Whenever you cast a noncreature spell, creatures you control get +1/+0 until end of turn.
    """, "3/3")
    private val corvinMoonlitMonarch = Cards.invent("Corvin, Moonlit Monarch", "{3}{G}{G}", "Legendary Creature — Werewolf Noble", """
        Flash
        At the beginning of your end step, if you didn't cast a spell this turn, put two +1/+1 counters on Corvin.
        {4}{G}: Put a +1/+1 counter on each creature you control with a +1/+1 counter on it.
    """, "4/4")
    private val plimboFindsThePath = Cards.invent("Plimbo, Finds the Path", "{X}{G}{G}", "Legendary Creature — Homunculus Scout", """
        Domain — Plimbo's power is equal to the number of basic land types among lands you control.
        When Plimbo enters, search your library for up to X basic land cards with different names, reveal them, put them into your hand, then shuffle.
    """, "*/2")
    private val brontorTheUnhaltable = Cards.invent("Brontor the Unhaltable", "{8}{G}", "Legendary Creature — Elder Dinosaur", """
        This spell costs {X} less to cast, where X is the greatest power among creatures you control.
        Trample
        Other creatures you control have trample.
    """, "8/8")
    private val weiLanfengNeverLonely = Cards.invent("Wei Lanfeng, Never Lonely", "{3}{G}", "Legendary Creature — Human Druid", """
        When Wei Lanfeng enters, create Bao, a legendary 3/3 green Dog creature token.
        At the beginning of your end step, untap all tokens you control.
    """, "2/2")
    private val snibbetTheJunction = Cards.invent("Snibbet, the Junction", "{2}{G}", "Legendary Creature — Beast Noble", "{T}: Choose a color. Add one mana of that color for each different power among creatures you control.", "2/1")
    private val tindraTheKeeper = Cards.invent("Tindra, the Keeper", "{1}{G}", "Legendary Creature — Elf Druid", """
        Lands you control have hexproof. (They can't be the targets of spells or abilities your opponents control.)
        {2}: Return target land card from your graveyard to your hand.
    """, "3/2")
    private val ashaAetherHermit = Cards.invent("Asha, Aether Hermit", "{2}{G}", "Legendary Creature — Human Druid", "When Asha enters, you may discard a card. If you do, search your library for an enchantment card, reveal it, put it into your hand, then shuffle.", "2/2")
    private val kotaroScrappyMutt = Cards.invent("Kotaro, Scrappy Mutt", "{1}{G}", "Legendary Creature — Dog", """
        When Kotaro enters, another target creature you control fights up to one target creature an opponent controls. (Each deals damage equal to its power to the other.)
        {6}: Put a +1/+1 counter on target nonlegendary creature.
    """, "1/1")
    private val corvinElderBloodsire = Cards.invent("Corvin, Elder Bloodsire", "{W}{B}", "Legendary Creature — Vampire Noble", """
        Whenever another creature or planeswalker you control dies, you gain 1 life.
        {2}, Sacrifice another creature or planeswalker: Put a +1/+1 counter on Corvin. He gains menace until end of turn. (He can't be blocked except by two or more creatures.)
    """, "2/3")
    private val nefrahTheDuneFang = Cards.invent("Nefrah, the Dune Fang", "{2}{B}{B}{G}", "Legendary Creature — Human Cleric", "When Nefrah enters, for each opponent, put X -1/-1 counters on up to one target creature that player controls, where X is the greatest mana value among cards in your graveyard.", "3/3")
    private val golvakGildedSentinel = Cards.invent("Golvak, Gilded Sentinel", "{2/W}{2/U}{2/B}{2/R}{2/G}", "Legendary Artifact Creature — Golem", """
        Vigilance, trample
        When Golvak enters, draw a card for each color among other artifacts you control.
    """, "5/5")
    private val pipwickValeHero = Cards.invent("Pipwick, Vale Hero", "{1}{R}{W}", "Legendary Creature — Mouse Soldier", "Whenever Pipwick or another creature you control enters, put a +1/+1 counter on target creature that entered this turn.", "1/2")
    private val tamsinGemOfTheFoundry = Cards.invent("Tamsin, Gem of the Foundry", "{2}{U}{R}", "Legendary Creature — Human Artificer", """
        Thopters you control have haste.
        Whenever you cast a noncreature spell, create a 1/1 colorless Thopter artifact creature token with flying.
    """, "2/4")
    private val gorgaHeartOfStone = Cards.invent("Gorga, Heart of Stone", "{U}{R}{W}", "Legendary Creature — Gorgon Wizard", """
        Artifact creatures you control have vigilance.
        Whenever you cast a noncreature spell, create a 1/1 colorless Sculpture Treasure artifact creature token with "{T}, Sacrifice this token: Add one mana of any color."
    """, "3/3")
    private val gorgaTheSeveringGaze = Cards.invent("Gorga, the Severing Gaze", "{B}{B}{G}", "Legendary Creature — Gorgon Assassin", """
        Deathtouch
        When Gorga enters, if you control six or more lands, destroy target permanent an opponent controls. They create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
    """, "4/4")
    private val vortanUndyingScourge = Cards.invent("Vortan, Undying Scourge", "{4}", "Legendary Artifact Creature — Dragon Construct", """
        Trample
        Vortan doesn't untap during your untap step.
        Whenever you cast an artifact or creature spell, untap Vortan.
    """, "5/4")

    private val glimpse = Cards.invent("Campus Glimpse", "{U}", "Instant", "Surveil 1.")
    private val wall = Cards.invent("Campus Wall", "{1}{W}", "Creature — Wall", "Defender", "0/4")
    private val lesson = Cards.invent("Campus Lesson", "{1}{U}", "Sorcery", "Draw a card.")
    private val insight = Cards.invent("Campus Insight", "{U}", "Sorcery", "Draw three cards.")
    private val quake = Cards.invent("Campus Quake", "{R}", "Sorcery", "Destroy target land.")
    private val redRelic = Cards.invent("Campus Red Relic", "{R}", "Artifact", "")
    private val greenRelic = Cards.invent("Campus Green Relic", "{G}", "Artifact", "")

    // ---- Chunk 14: white legends

    // CR 603.2h: once each turn
    @Test
    fun lieslBladeOfPromiseDrawsForATargetingSpell() = scenario {
        alice.battlefield(lieslBladeOfPromise, bear, "Forest", "Forest")
        alice.hand(pump, pump)
        alice.library(ogre, ogre)
        alice.casts(pump, target = bear)
        resolveAll()
        alice.casts(pump, target = bear)
        resolveAll()
        expect { alice.hand.size shouldBe 1 }
    }

    // CR 510.1a: combat damage by toughness
    @Test
    fun brontorTheUnbudgingLetsWallsHitByToughness() = scenario {
        alice.battlefield(brontorTheUnbudging, wall)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(wall)
        advanceTo(TurnStep.CombatDamage)
        resolveAll()
        expect { bob.life shouldBe 16 }
    }

    @Test
    fun brontorTheUnbudgingCostsLessByToughness() = scenario {
        alice.battlefield(wall, "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(brontorTheUnbudging)
        alice.casts(brontorTheUnbudging)
        resolveAll()
        expect { alice.battlefield has brontorTheUnbudging }
    }

    @Test
    fun oathkeepersMonumentStrikesAnAttackerFromTheHand() = scenario {
        alice.battlefield("Plains", "Plains")
        alice.hand(oathkeepersMonument)
        alice.library(bear)
        bob.battlefield(giant)
        bob.library(bear)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        bob.attacks(giant)
        bob.passes()
        alice.activates(oathkeepersMonument, ability = 1, target = giant)
        resolveAll()
        expect { card(giant).view.obj!!.damage shouldBe 4 }
    }

    @Test
    fun oathkeepersMonumentPumpsTokens() = scenario {
        alice.battlefield(oathkeepersMonument, "Plains", "Plains", "Plains", "Mountain")
        alice.hand(heartcordTugger)
        alice.casts(heartcordTugger)
        resolveAll()
        expect {
            "Cadet".power shouldBe 3
            "Cadet" hasKeyword Keyword.Vigilance
        }
    }

    @Test
    fun brannocOfTheHearthRewardsPlains() = scenario {
        alice.battlefield(brannocOfTheHearth, bear)
        alice.hand("Plains")
        alice.plays("Plains")
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            alice.life shouldBe 21
            bear.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun selineTheBlamelessGainsOnArrivals() = scenario {
        alice.battlefield(selineTheBlameless, "Forest")
        alice.hand(elf)
        alice.casts(elf)
        resolveAll()
        expect { alice.life shouldBe 21 }
    }

    @Test
    fun selineTheBlamelessGrantsHexproof() = scenario {
        alice.battlefield(selineTheBlameless, bear, "Plains")
        alice.hand(ogre)
        alice.activates(selineTheBlameless, target = bear, choosing = listOf(ogre))
        resolveAll()
        expect { bear hasKeyword Keyword.Hexproof }
    }

    @Test
    fun aurielleSeraphOfDaybreakGrowsAngels() = scenario {
        alice.battlefield(aurielleSeraphOfDaybreak, bear, "Plains")
        alice.hand(heal)
        alice.casts(heal)
        resolveAll()
        expect {
            aurielleSeraphOfDaybreak.counters(p1p1) shouldBe 1
            bear.counters(p1p1) shouldBe 0
        }
    }

    @Test
    fun reliefMaidenSwiftRescuerBouncesOwnPermanents() = scenario {
        alice.battlefield(reliefMaidenSwiftRescuer, bear)
        alice.activates(reliefMaidenSwiftRescuer, target = bear)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun tamsinEnvoyOfVigilanceMakesAThopterOnce() = scenario {
        alice.battlefield(tamsinEnvoyOfVigilance, "Island", "Island")
        alice.hand(glimpse, glimpse)
        alice.library(bear, bear)
        repeat(2) {
            alice.casts(glimpse)
            resolveAll()
            chooseNone()
            resolveAll()
        }
        expect { alice.battlefield.names.count { it == "Thopter Token" } shouldBe 1 }
    }

    @Test
    fun ilsaShieldlightAdeptProtectsAndCounters() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(ilsaShieldlightAdept)
        alice.casts(ilsaShieldlightAdept)
        resolveAll()
        targetsIfAsked(bear)
        resolveAll()
        expect {
            bear hasKeyword Keyword.Hexproof
            bear.counters(p1p1) shouldBe 1
        }
    }

    // CR 601.2f: a cost increase for the opponent's noncreature spells
    @Test
    fun mirelaTheEnduringTaxesOpponents() = scenario {
        alice.battlefield(mirelaTheEnduring)
        bob.battlefield("Mountain", "Mountain")
        bob.hand(shock)
        turn(active = bob)
        bob.casts(shock, target = "alice")
        resolveAll()
        expect {
            card("Mountain", 0).tapped shouldBe true
            card("Mountain", 1).tapped shouldBe true
            alice.life shouldBe 18
        }
    }

    // ---- Chunk 15: blue legends

    @Test
    fun orrinModestCopyistLootsAndUntaps() = scenario {
        alice.battlefield(orrinModestCopyist, "Forest")
        alice.hand(bear, elf)
        alice.library(ogre, ogre)
        alice.activates(orrinModestCopyist, choosing = listOf(bear))
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(bear)
        resolveAll()
        expect { orrinModestCopyist.tapped shouldBe true }
        alice.casts(elf)
        resolveAll()
        expect { orrinModestCopyist.tapped shouldBe false }
    }

    @Test
    fun wraithOfBlessedMirelaDiscountsSpells() = scenario {
        alice.battlefield(wraithOfBlessedMirela, "Island")
        alice.hand(lesson)
        alice.library(bear)
        alice.casts(lesson)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun aurielleAcademySeraphMakesAnAngelAfterThreeDraws() = scenario {
        alice.battlefield(aurielleAcademySeraph, "Island")
        alice.hand(insight)
        alice.library(bear, bear, bear)
        alice.casts(insight)
        resolveAll()
        advanceTo(TurnStep.End)
        resolveAll()
        expect { "Angel Token" hasKeyword Keyword.Flying }
    }

    @Test
    fun quillInvestigatingSleuthPaysToGrow() = scenario {
        alice.battlefield(quillInvestigatingSleuth, "Island", "Island", "Island")
        alice.hand(glimpse)
        alice.library(bear, ogre)
        alice.casts(glimpse)
        resolveAll()
        chooseNone()
        resolveAll()
        may(true)
        payIfAsked()
        resolveAll()
        expect {
            quillInvestigatingSleuth.counters(p1p1) shouldBe 1
            alice.hand has bear
        }
    }

    @Test
    fun gorrumVaxBiomancerDrawsWhenTargeted() = scenario {
        alice.battlefield(gorrumVaxBiomancer)
        alice.library(bear)
        bob.battlefield("Mountain")
        bob.hand(shock)
        turn(active = bob)
        bob.casts(shock, target = gorrumVaxBiomancer)
        resolveAll()
        expect { alice.hand has bear }
    }

    @Test
    fun gorrumVaxBiomancerDoubleProwess() = scenario {
        alice.battlefield(gorrumVaxBiomancer, "Island")
        alice.hand(ponder)
        alice.library(bear)
        alice.casts(ponder)
        resolveAll()
        expect { gorrumVaxBiomancer.power shouldBe 6 }
    }

    @Test
    fun rinkoTakaharaRunawaySneaksSmallCreatures() = scenario {
        alice.battlefield(rinkoTakaharaRunaway, elf)
        bob.battlefield(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(elf)
        advanceTo(TurnStep.DeclareBlockers)
        assertFailsWith<IllegalArgumentException> { bob.blocks(ogre to elf) }
    }

    @Test
    fun vortanScholasticWardenCheaperAfterASpell() = scenario {
        alice.battlefield("Island", "Island", "Island")
        alice.hand(ponder, vortanScholasticWarden)
        alice.library(bear)
        alice.casts(ponder)
        resolveAll()
        alice.casts(vortanScholasticWarden)
        resolveAll()
        expect { alice.battlefield has vortanScholasticWarden }
    }

    @Test
    fun akemiLightFromTheShadowsShrinksByGraveyard() = scenario {
        alice.battlefield("Island")
        alice.hand(akemiLightFromTheShadows)
        alice.graveyard(elf, elf)
        bob.battlefield(ogre)
        alice.casts(akemiLightFromTheShadows)
        resolveAll()
        modes(0)
        targetsIfAsked(ogre)
        resolveAll()
        expect { ogre.power shouldBe 1 }
    }

    // ---- Chunk 16: black and red legends

    @Test
    fun lieslLanceOfTormentGrowsOnHostileSpells() = scenario {
        alice.battlefield(lieslLanceOfTorment, "Mountain")
        alice.hand(shock)
        alice.casts(shock, target = "bob")
        resolveAll()
        expect { lieslLanceOfTorment.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun mirethMournfulRevenantReturnsTapped() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.graveyard(mirethMournfulRevenant, bear)
        alice.activates(mirethMournfulRevenant, choosing = listOf(bear))
        resolveAll()
        expect {
            mirethMournfulRevenant.tapped shouldBe true
            mirethMournfulRevenant.counters(p1p1) shouldBe 1
            alice.exile has bear
        }
    }

    // CR 702.177a: exhaust, once
    @Test
    fun selineThePenitentMillsAndExhausts() = scenario {
        alice.battlefield(selineThePenitent, "Forest", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(elf)
        alice.library(giant, ogre, bear)
        alice.casts(elf, paying = listOf("Forest"))
        resolveAll()
        expect { alice.graveyard has giant }
        alice.activates(selineThePenitent, target = giant)
        resolveAll()
        expect {
            alice.battlefield has giant
            selineThePenitent.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun carnageMaidenMostFearedDrainsAndGrows() = scenario {
        alice.battlefield(carnageMaidenMostFeared, bear, "Swamp")
        alice.hand(doom)
        alice.casts(doom, target = bear)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.life shouldBe 21
            carnageMaidenMostFeared.counters(p1p1) shouldBe 1
        }
    }

    // CR 601.3: a spell that can't be cast
    @Test
    fun quillSinisterSchemerNeedsThreshold() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(quillSinisterSchemer)
        assertFailsWith<IllegalArgumentException> { alice.casts(quillSinisterSchemer) }
    }

    @Test
    fun quillSinisterSchemerDiscardsToShrink() = scenario {
        alice.battlefield("Swamp")
        alice.hand(quillSinisterSchemer)
        bob.battlefield(ogre)
        alice.activates(quillSinisterSchemer, target = ogre)
        resolveAll()
        expect {
            ogre.power shouldBe 0
            ogre.toughness shouldBe 2
        }
    }

    @Test
    fun ilsaDiamondedgeMageAddsLoyalty() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.battlefield(walker, counters = listOf(CounterKind.Loyalty to 3))
        alice.hand(ilsaDiamondedgeMage)
        alice.casts(ilsaDiamondedgeMage)
        resolveAll()
        targetsIfAsked(walker)
        resolveAll()
        assertEquals(4, loyalty(walker))
    }

    @Test
    fun smallbonesPocketPestPunishesDiscards() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(smallbonesPocketPest)
        bob.hand(bear)
        alice.casts(smallbonesPocketPest)
        resolveAll()
        if (pending is Decision.ChooseObjects) bob.chooses(bear)
        resolveAll()
        expect {
            bob.graveyard has bear
            bob.life shouldBe 19
        }
    }

    // CR 603.12: reflexive "when you do"
    @Test
    fun hollisHauntedLonerTradesASacrifice() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", elf)
        alice.hand(hollisHauntedLoner)
        bob.battlefield(ogre)
        alice.casts(hollisHauntedLoner)
        resolveAll()
        may(true)
        if (pending is Decision.ChooseObjects) alice.chooses(elf)
        resolveAll()
        if (pending is Decision.ChooseObjects) bob.chooses(ogre)
        resolveAll()
        expect {
            bob.graveyard has ogre
            hollisHauntedLoner.power shouldBe 1
        }
    }

    @Test
    fun orrinFamedChampionGainsTheNewcomersPower() = scenario {
        alice.battlefield(orrinFamedChampion, "Mountain", "Mountain", "Mountain")
        alice.hand(ogre)
        alice.casts(ogre)
        resolveAll()
        expect { orrinFamedChampion.power shouldBe 4 }
    }

    @Test
    fun mirethTheRevelerHastesCounteredNewcomers() = scenario {
        alice.battlefield(mirethTheReveler, "Mountain", "Mountain", "Forest", "Forest")
        alice.hand(bear)
        alice.casts(bear, paying = listOf("Forest", "Forest"))
        resolveAll()
        alice.activates(mirethTheReveler, target = bear)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 1
            bear hasKeyword Keyword.Haste
        }
    }

    @Test
    fun weiLanfengSolitaryLootsForALoneAttacker() = scenario {
        alice.battlefield(weiLanfengSolitary, bear)
        alice.hand(ogre)
        alice.library(elf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(bear)
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(ogre)
        resolveAll()
        expect {
            alice.hand has elf
            bear.counters(p1p1) shouldBe 1
        }
    }

    @Test
    fun seraphineOfEmberAndSootMakesDragons() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(seraphineOfEmberAndSoot)
        alice.casts(seraphineOfEmberAndSoot)
        resolveAll()
        expect { "Dragon Token" hasKeyword Keyword.Flying }
    }

    @Test
    fun brannocTheEarthshaperBurnsOnLandfall() = scenario {
        alice.battlefield(brannocTheEarthshaper)
        alice.hand("Mountain")
        alice.plays("Mountain")
        resolveAll()
        expect {
            bob.life shouldBe 19
            alice.pool.size shouldBe 1
        }
    }

    @Test
    fun tindraTheWoodfellerDrawsForALand() = scenario {
        alice.battlefield(tindraTheWoodfeller, "Mountain", "Mountain", "Plains")
        alice.library(bear)
        alice.activates(tindraTheWoodfeller, choosing = listOf("Plains"), paying = listOf("Mountain", "Mountain"))
        resolveAll()
        expect {
            alice.hand has bear
            alice.graveyard has "Plains"
        }
    }

    @Test
    fun ashaSteadfastRebuilderBuildsAThopterAndPumps() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain", bear)
        alice.hand(ashaSteadfastRebuilder)
        alice.casts(ashaSteadfastRebuilder)
        resolveAll()
        expect { alice.battlefield has "Thopter Token" }
        alice.activates(ashaSteadfastRebuilder, target = bear)
        resolveAll()
        expect { bear.power shouldBe 3 }
    }

    @Test
    fun hekatTheStormGodsChosenHastesAll() = scenario {
        alice.battlefield(hekatTheStormGodsChosen)
        alice.battlefield(bear, sick = true)
        expect { bear hasKeyword Keyword.Haste }
    }

    @Test
    fun rinkoTakaharaHunterPunishesSmallBlockers() = scenario {
        alice.battlefield(rinkoTakaharaHunter)
        bob.battlefield(elf)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(rinkoTakaharaHunter)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(elf to rinkoTakaharaHunter)
        resolveAll()
        expect { bob.life shouldBe 19 }
    }

    // CR 614.1a: damage dealt plus 1
    @Test
    fun dovanGuildSparkwrightAddsOne() = scenario {
        alice.battlefield(dovanGuildSparkwright, "Mountain")
        alice.hand(shock)
        alice.casts(shock, target = "bob")
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    @Test
    fun hollisTeamChampionPumpsOnSpells() = scenario {
        alice.battlefield(hollisTeamChampion, bear, "Mountain")
        alice.hand(shock)
        alice.casts(shock, target = "bob")
        resolveAll()
        expect {
            bear.power shouldBe 3
            hollisTeamChampion.power shouldBe 4
        }
    }

    // CR 702.51a: convoke
    @Test
    fun hollisTeamChampionConvokes() = scenario {
        alice.battlefield("Mountain", bear, elf, ogre, giant)
        alice.hand(hollisTeamChampion)
        alice.casts(hollisTeamChampion, paying = listOf("Mountain", bear, elf, ogre, giant))
        resolveAll()
        expect { alice.battlefield has hollisTeamChampion }
    }

    // ---- Chunk 17: green and multicolored legends

    @Test
    fun corvinMoonlitMonarchGrowsWhenIdle() = scenario {
        alice.battlefield(corvinMoonlitMonarch)
        advanceTo(TurnStep.End)
        resolveAll()
        expect { corvinMoonlitMonarch.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun corvinMoonlitMonarchSpreadsCounters() = scenario {
        alice.battlefield(corvinMoonlitMonarch, elf, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.battlefield(bear, counters = listOf(p1p1 to 1))
        alice.activates(corvinMoonlitMonarch)
        resolveAll()
        expect {
            bear.counters(p1p1) shouldBe 2
            elf.counters(p1p1) shouldBe 0
        }
    }

    @Test
    fun plimboFindsThePathSearchesBasics() = scenario {
        alice.battlefield("Forest", "Forest", "Island", "Island")
        alice.hand(plimboFindsThePath)
        alice.library("Plains", "Swamp", "Plains", bear)
        alice.casts(plimboFindsThePath, x = 2)
        resolveAll()
        alice.chooses("Plains", "Swamp")
        resolveAll()
        expect {
            alice.hand has "Plains"
            alice.hand has "Swamp"
            plimboFindsThePath.power shouldBe 2
        }
    }

    @Test
    fun brontorTheUnhaltableCheapAndTrampling() = scenario {
        alice.battlefield(giant, "Forest", "Forest", "Forest", "Forest")
        alice.hand(brontorTheUnhaltable)
        alice.casts(brontorTheUnhaltable)
        resolveAll()
        expect { giant hasKeyword Keyword.Trample }
    }

    @Test
    fun weiLanfengNeverLonelyMakesBaoAndUntapsTokens() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest")
        alice.hand(weiLanfengNeverLonely)
        alice.casts(weiLanfengNeverLonely)
        resolveAll()
        expect { "Bao".power shouldBe 3 }
        change(Changes.tap(state, card("Bao"), true))
        advanceTo(TurnStep.End)
        resolveAll()
        expect { "Bao".tapped shouldBe false }
    }

    @Test
    fun snibbetTheJunctionAddsPerPower() = scenario {
        alice.battlefield(snibbetTheJunction, bear, ogre, elf)
        alice.activates(snibbetTheJunction, color = Color.Green)
        resolveAll()
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        expect { alice.pool.size shouldBe 3 }
    }

    @Test
    fun tindraTheKeeperRegrowsLands() = scenario {
        alice.battlefield(tindraTheKeeper, "Forest", "Forest")
        alice.graveyard("Island")
        alice.activates(tindraTheKeeper, target = "Island")
        resolveAll()
        expect { alice.hand has "Island" }
    }

    // CR 702.11b: hexproof against the opponent's spells
    @Test
    fun tindraTheKeeperHexproofLands() = scenario {
        alice.battlefield(tindraTheKeeper, "Forest")
        bob.battlefield("Mountain")
        bob.hand(quake)
        turn(active = bob)
        assertFailsWith<IllegalArgumentException> { bob.casts(quake, target = "Forest") }
    }

    @Test
    fun ashaAetherHermitTradesACardForAnEnchantment() = scenario {
        alice.battlefield("Forest", "Forest", "Forest")
        alice.hand(ashaAetherHermit, bear)
        alice.library(ogre, charm)
        alice.casts(ashaAetherHermit)
        resolveAll()
        may(true)
        chooseIfOffered(bear)
        resolveAll()
        alice.chooses(charm)
        resolveAll()
        expect { alice.hand has charm }
    }

    @Test
    fun kotaroScrappyMuttSetsUpAFight() = scenario {
        alice.battlefield("Forest", "Forest", giant)
        alice.hand(kotaroScrappyMutt)
        bob.battlefield(ogre)
        alice.casts(kotaroScrappyMutt)
        resolveAll()
        // the only creature of hers is chosen without asking (CR 603.3d)
        targetsIfAsked(ogre)
        resolveAll()
        expect { bob.graveyard has ogre }
    }

    @Test
    fun corvinElderBloodsireSacrificesForMenace() = scenario {
        alice.battlefield(corvinElderBloodsire, bear, "Plains", "Plains")
        alice.activates(corvinElderBloodsire, choosing = listOf(bear))
        resolveAll()
        expect {
            corvinElderBloodsire.counters(p1p1) shouldBe 1
            corvinElderBloodsire hasKeyword Keyword.Menace
            alice.life shouldBe 21
        }
    }

    @Test
    fun nefrahTheDuneFangShrinksByManaValue() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Forest", "Forest")
        alice.hand(nefrahTheDuneFang)
        alice.graveyard(ogre)
        bob.battlefield(giant)
        alice.casts(nefrahTheDuneFang)
        resolveAll()
        targetsIfAsked(giant)
        resolveAll()
        expect { giant.counters(CounterKind.PowerToughness(-1, -1)) shouldBe 3 }
    }

    @Test
    fun golvakGildedSentinelDrawsPerArtifactColor() = scenario {
        alice.battlefield("Plains", "Island", "Swamp", "Mountain", "Forest", redRelic, greenRelic)
        alice.hand(golvakGildedSentinel)
        alice.library(bear, bear, bear)
        alice.casts(golvakGildedSentinel)
        resolveAll()
        expect { alice.hand.size shouldBe 2 }
    }

    @Test
    fun pipwickValeHeroCountersNewcomers() = scenario {
        alice.battlefield(pipwickValeHero, "Forest")
        alice.hand(elf)
        alice.casts(elf)
        resolveAll()
        targetsIfAsked(elf)
        resolveAll()
        expect { elf.counters(p1p1) shouldBe 1 }
    }

    @Test
    fun tamsinGemOfTheFoundryMakesHastyThopters() = scenario {
        alice.battlefield(tamsinGemOfTheFoundry, "Mountain")
        alice.hand(shock)
        alice.casts(shock, target = "bob")
        resolveAll()
        expect { "Thopter Token" hasKeyword Keyword.Haste }
    }

    @Test
    fun gorgaHeartOfStoneMakesSculptures() = scenario {
        alice.battlefield(gorgaHeartOfStone, "Mountain")
        alice.hand(shock)
        alice.casts(shock, target = "bob")
        resolveAll()
        expect { alice.battlefield.size shouldBe 3 }
    }

    @Test
    fun gorgaTheSeveringGazeDestroysWithSixLands() = scenario {
        alice.battlefield("Swamp", "Swamp", "Forest", "Swamp", "Swamp", "Forest")
        alice.hand(gorgaTheSeveringGaze)
        bob.battlefield(idol)
        alice.casts(gorgaTheSeveringGaze)
        resolveAll()
        targetsIfAsked(idol)
        resolveAll()
        expect {
            bob.graveyard has idol
            bob.battlefield has "Treasure Token"
        }
    }

    @Test
    fun vortanUndyingScourgeUntapsOnSpells() = scenario {
        alice.battlefield(vortanUndyingScourge, tapped = true)
        alice.battlefield("Forest")
        alice.hand(elf)
        alice.casts(elf)
        resolveAll()
        expect { vortanUndyingScourge.tapped shouldBe false }
    }
}
