package arcanum.engine.sets.mom

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.CardRef
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.fin.resolveAll
import arcanum.engine.state.TargetRef
import arcanum.parser.OracleCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** MOM chunk 12: extra damage to a white or blue target, damage to a battle, combat damage passed on to a planeswalker or a battle that
 * player protects, damage prevented by its source's colour, a discard of any number of lands doubled into damage, the activated abilities
 * of opponents' creatures, and the cards seen through excess damage played from exile. */
class MomChunk12DamageLookAndPermissionsScenariosTest {
    private val siege = Cards.inventFaces("Transform",
        OracleCard("Siege of the Ashen Span", "{2}{R}", "Battle — Siege", "", defense = "5"),
        OracleCard("Ashen Span Warden", null, "Creature — Elemental", "", power = "4", toughness = "4", colorIndicator = "R"),
    )
    private val volley = Cards.invent("Runestone Volley", "{R}", "Sorcery", """
        This spell can't be countered.
        Runestone Volley deals 1 damage to target creature or planeswalker. It deals 5 damage instead if that target is white and/or blue.
    """.trimIndent())
    private val adept = Cards.invent("Tidecaller Adept", "{1}{U}", "Creature — Merfolk Wizard", "", "3/3")
    private val grazer = Cards.invent("Mossback Trampler", "{1}{G}", "Creature — Beast", "", "3/3")
    private val spearman = Cards.invent("Cragborn Spearman", "{4}{R}", "Creature — Ogre Spirit", """
        Reach
        {T}: This creature deals 2 damage to target player or battle.
    """.trimIndent(), "5/4")
    private val raptorhorn = Cards.invent("Stampeding Raptorhorn", "{2}{R}{R}", "Creature — Dinosaur", """
        Trample, haste
        {2}{R}: This creature gets +2/+0 until end of turn.
        Whenever this creature deals combat damage to an opponent, it deals that much damage to target planeswalker that player controls or battle that player protects.
    """.trimIndent(), "4/4")
    private val bobWalker = Cards.invent("Brakka, Storm Herald", "{2}{R}{R}", "Legendary Planeswalker — Brakka", "[+1]: Draw a card.")
    private val carolWalker = Cards.invent("Sefa, Tide Herald", "{2}{U}{U}", "Legendary Planeswalker — Sefa", "[+1]: Draw a card.")
    private val wave = Cards.invent("Wave of Sanctuary", "{W}", "Instant",
        "You and permanents you control gain hexproof until end of turn. Prevent all damage that black and/or red sources would deal to creatures you control this turn.")
    private val squall = Cards.invent("Cinder Squall", "{R}", "Instant", "Cinder Squall deals 2 damage to each creature.")
    private val thornburst = Cards.invent("Thornburst", "{G}", "Instant", "Thornburst deals 2 damage to each creature.")
    private val grumblor = Cards.invent("Grumblor and Pip", "{2}{G}{U}{R}", "Legendary Creature — Cyclops Homunculus", """
        Whenever Grumblor and Pip enters or attacks, draw a card, then you may discard any number of land cards. When you discard one or more cards this way, Grumblor and Pip deals twice that much damage to target creature.
        {1}{U}: Put Grumblor and Pip into its owner's library third from the top.
    """.trimIndent(), "6/5")
    private val brute = Cards.invent("Gravelhide Brute", "{3}{G}", "Creature — Beast", "", "4/4")
    private val vessa = Cards.invent("Vessa and Orinel", "{1}{W}{W}{B}", "Legendary Creature — Vampire Angel", """
        Flying, vigilance
        Activated abilities of creatures your opponents control can't be activated.
        Vessa and Orinel has all activated abilities of all creatures your opponents control. You may spend mana as though it were mana of any color to activate those abilities.
    """.trimIndent(), "3/4")
    private val pikeman = Cards.invent("Ember Pikeman", "{1}{R}", "Creature — Goblin Soldier", "{R}: This creature gets +1/+0 until end of turn.", "1/1")
    private val forgecraft = Cards.invent("Kelvara's Forgecraft", "{1}{R}{R}", "Sorcery",
        "Kelvara's Forgecraft deals 5 damage to target creature, planeswalker, or battle. Look at the top X cards of your library, where X is the excess damage dealt this way. You may exile one of those cards. Put the rest on the bottom of your library in a random order. You may play the exiled card this turn.")

    private fun Scenario.libraries() = players.forEach { it.library("Island", "Island", "Island") }

    // CR 608.2b, 105.2: the target is blue as the spell resolves, so it deals 5 damage instead of 1
    @Test
    fun volleyDealsFiveToABlueTarget() = scenario {
        alice.battlefield("Mountain")
        alice.hand(volley)
        bob.battlefield(adept)
        alice.casts(volley, target = adept)
        resolveAll()
        expect { bob.graveyard has adept }
    }

    // CR 608.2b: a green target is dealt only 1 damage
    @Test
    fun volleyDealsOneToAGreenTarget() = scenario {
        alice.battlefield("Mountain")
        alice.hand(volley)
        bob.battlefield(grazer)
        alice.casts(volley, target = grazer)
        resolveAll()
        expect {
            bob.battlefield has grazer
            grazer.view.obj?.damage shouldBe 1
        }
    }

    // CR 115.4, 310.6: damage dealt to a battle removes that many defense counters
    @Test
    fun spearmanDamagesABattle() = scenario {
        alice.battlefield(spearman)
        alice.battlefield(siege, counters = listOf(CounterKind.Defense to 5))
        alice.activates(spearman, target = siege)
        resolveAll()
        expect { siege.counters(CounterKind.Defense) shouldBe 3 }
    }

    // CR 510.2, 310.8, 115.1: the trigger's target is a planeswalker the damaged player controls or a battle that player protects; not
    // another opponent's planeswalker or a battle another opponent protects
    @Test
    fun raptorhornPassesItsDamageToWhatThatPlayerProtects() = scenario(players = 3) {
        alice.battlefield(raptorhorn)
        alice.battlefield(siege, counters = listOf(CounterKind.Defense to 5))
        alice.battlefield(siege, counters = listOf(CounterKind.Defense to 5))
        bob.battlefield(bobWalker, counters = listOf(CounterKind.Loyalty to 3))
        carol.battlefield(carolWalker, counters = listOf(CounterKind.Loyalty to 3))
        libraries()
        val bobsSiege = card(siege, index = 0)
        val carolsSiege = card(siege, index = 1)
        passPriority()
        repeat(2) {
            val protector = decision<Decision.ChoosePlayer>()
            answer(Answer.Player(if (protector.source == bobsSiege) bob.id else carol.id))
        }
        advanceTo(TurnStep.DeclareAttackers)
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(raptorhorn), TargetRef.Player(bob.id)))))
        advanceTo(TurnStep.CombatDamage)
        val decision = decision<Decision.Targets>()
        assertEquals(setOf(TargetRef.Object(card(bobWalker)), TargetRef.Object(bobsSiege)), decision.options.toSet())
        answer(Answer.Targets(listOf(TargetRef.Object(bobsSiege))))
        resolveAll()
        expect {
            bob.life shouldBe 16
            CardRef(bobsSiege, "Bob's Siege").counters(CounterKind.Defense) shouldBe 1
            CardRef(carolsSiege, "Carol's Siege").counters(CounterKind.Defense) shouldBe 5
        }
    }

    // CR 615.1a, 120.1: damage from a red source to a creature you control is prevented
    @Test
    fun waveOfSanctuaryPreventsDamageFromARedSource() = scenario {
        alice.battlefield("Plains", "Mountain", adept)
        alice.hand(wave, squall)
        bob.battlefield(grazer)
        alice.casts(wave)
        resolveAll()
        alice.casts(squall)
        resolveAll()
        expect {
            adept.view.obj?.damage shouldBe 0
            grazer.view.obj?.damage shouldBe 2
        }
    }

    // CR 615.1a: a green source isn't black or red: its damage is dealt
    @Test
    fun waveOfSanctuaryLetsDamageFromAGreenSource() = scenario {
        alice.battlefield("Plains", "Forest", adept)
        alice.hand(wave, thornburst)
        alice.casts(wave)
        resolveAll()
        alice.casts(thornburst)
        resolveAll()
        expect { adept.view.obj?.damage shouldBe 2 }
    }

    // CR 701.9a, 603.12: two land cards discarded: a reflexive trigger deals twice that many, 4 damage
    @Test
    fun grumblorDealsTwiceTheDiscardedLands() = scenario {
        alice.battlefield("Forest", "Island", "Mountain", "Forest", "Island")
        alice.hand(grumblor, "Plains", "Swamp")
        alice.library("Mountain")
        bob.battlefield(brute)
        alice.casts(grumblor)
        resolveAll()
        alice.chooses("Plains", "Swamp")
        targets(brute)
        resolveAll()
        expect {
            alice.hand has "Mountain"
            alice.graveyard has "Plains"
            alice.graveyard has "Swamp"
            bob.graveyard has brute
        }
    }

    // CR 603.12: discarding no cards doesn't trigger the reflexive ability
    @Test
    fun grumblorDiscardingNothingDealsNoDamage() = scenario {
        alice.battlefield("Forest", "Island", "Mountain", "Forest", "Island")
        alice.hand(grumblor, "Plains")
        alice.library("Mountain")
        bob.battlefield(brute)
        alice.casts(grumblor)
        resolveAll()
        answer(Answer.Objects(emptyList()))
        resolveAll()
        expect {
            alice.hand has "Plains"
            brute.view.obj?.damage shouldBe 0
            stack.isEmpty()
        }
    }

    // CR 613.1f, 609.4b: it has the opponent's creature's ability and pays its {R} with a Plains
    @Test
    fun vessaActivatesAnOpponentsCreaturesAbility() = scenario {
        alice.battlefield(vessa, "Plains")
        bob.battlefield(pikeman, "Mountain")
        alice.activates(vessa, paying = listOf("Plains"))
        resolveAll()
        expect {
            vessa.power shouldBe 4
            pikeman.power shouldBe 1
        }
    }

    // CR 602.5: the opponent can't activate their creature's ability
    @Test
    fun vessaStopsTheOpponentsActivations() = scenario {
        alice.battlefield(vessa)
        bob.battlefield(pikeman, "Mountain")
        alice.passes()
        assertFailsWith<IllegalArgumentException> { bob.activates(pikeman, paying = listOf("Mountain")) }
    }

    // CR 120.4a: 5 damage to a 2/2 is 3 excess: three cards are seen; the exiled land may be played this turn (CR 305.1)
    @Test
    fun forgecraftLooksAtTheExcessAndPlaysTheExiledCard() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(forgecraft)
        alice.library("Island", "Forest", "Plains", "Swamp")
        bob.battlefield(pikeman)
        alice.casts(forgecraft, target = pikeman)
        resolveAll()
        alice.chooses("Forest")
        resolveAll()
        expect {
            alice.exile has "Forest"
            alice.library.size shouldBe 3
            alice.library.names.first() shouldBe "Swamp"
        }
        alice.plays("Forest")
        expect { alice.battlefield has "Forest" }
    }
}
