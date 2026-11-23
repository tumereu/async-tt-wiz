package arcanum.engine.sets.snc

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.option
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import arcanum.engine.sets.ltr.permitted
import arcanum.engine.sets.ltr.playOut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Streets of New Capenna chunk 09 in play: copies of every card exiled with an enchantment cast for free, one chosen keyword for two
 * creatures, -X/-0 counting different mana values, the greatest power or toughness among others, a graveyard permission with a
 * sacrifice and an exile replacement, a return limited to lesser mana value than the sacrificed creature and to once a turn, an attack
 * trigger on attacking a planeswalker, and a reflexive destroy limited by the excess damage. */
class SncChunk09EffectsScenariosTest {
    private val bombardment = Cards.invent("Snc09 Spellstorm Archive", "{4}{R}{R}", "Enchantment",
        "Whenever you cast your first instant or sorcery spell each turn, exile an instant or sorcery card at random from your graveyard. Then copy each card exiled with Snc09 Spellstorm Archive. You may cast any number of the copies without paying their mana costs.")
    private val brawlers = Cards.invent("Snc09 Gala Bruisers", "{3}{W}{W}", "Creature — Human Warrior",
        "Whenever Snc09 Gala Bruisers attacks, Snc09 Gala Bruisers and up to one other target creature you control both gain your choice of first strike or lifelink until end of turn.",
        "3/5")
    private val arbiter = Cards.invent("Snc09 Omniscient Magistrate", "{4}{U}{U}", "Creature — Avatar", """
        Flying
        Whenever Snc09 Omniscient Magistrate enters or attacks, draw two cards, then discard a card.
        Whenever you discard a card, target creature an opponent controls gets -X/-0 until your next turn, where X is the number of different mana values among cards in your graveyard.
    """, "5/4")
    private val muscle = Cards.invent("Snc09 Hired Bruiser Rhino", "{4}{G}", "Creature — Rhino Warrior",
        "Whenever Snc09 Hired Bruiser Rhino attacks or blocks, it gets +X/+X until end of turn, where X is the greatest power and/or toughness among other creatures you control.",
        "4/4")
    private val maestros = Cards.invent("Snc09 Virtuoso Supremacy", "{U}{B}{R}", "Enchantment",
        "Once during each of your turns, you may cast an instant or sorcery spell from your graveyard by sacrificing a creature in addition to paying its other costs. If a spell cast this way would be put into your graveyard, exile it instead.")
    private val riveteers = Cards.invent("Snc09 Forgers Supremacy", "{B}{R}{G}", "Enchantment",
        "Whenever you sacrifice a creature, you may return target creature card with lesser mana value from your graveyard to the battlefield tapped. Do this only once each turn.")
    private val rigo = Cards.invent("Snc09 Pello, Alley Mentor", "{G/W}{W}{W/U}", "Legendary Creature — Cat Citizen", """
        Snc09 Pello, Alley Mentor enters with a shield counter on it.
        Whenever you attack a player or planeswalker with one or more creatures with power 1 or less, draw a card.
    """, "2/2")
    private val inferno = Cards.invent("Snc09 Unchain the Blaze", "{1}{B}{R}{G}", "Instant",
        "Snc09 Unchain the Blaze deals 7 damage to target creature or planeswalker. When it deals excess damage this way, destroy target artifact or enchantment an opponent controls with mana value less than or equal to that amount of excess damage.")

    private val study = Cards.invent("Snc09 Quick Study", "{U}", "Instant", "Draw a card.")
    private val jolt = Cards.invent("Snc09 Ember Jolt", "{R}", "Instant", "Snc09 Ember Jolt deals 1 damage to each opponent.")
    private val flare = Cards.invent("Snc09 Ember Flare", "{R}", "Sorcery", "Snc09 Ember Flare deals 2 damage to each opponent.")
    private val altar = Cards.invent("Snc09 Bone Altar", "{1}", "Artifact", "Sacrifice a creature: You gain 1 life.")
    private val walker = Cards.invent("Snc09 Ward Planeswalker", "{3}{W}", "Legendary Planeswalker — Tamiyo", "[+1]: You gain 1 life.", loyalty = "4")
    private val kitten = Cards.invent("Snc09 Alley Kitten", "{W}", "Creature — Cat", "", "1/1")
    private val cub = Cards.invent("Snc09 Alley Cub", "{1}{G}", "Creature — Cat", "", "2/2")
    private val wall = Cards.invent("Snc09 Brick Wall", "{1}{W}", "Creature — Wall", "", "1/5")
    private val tough = Cards.invent("Snc09 Street Tough", "{2}{R}", "Creature — Human Warrior", "", "3/3")
    private val brute = Cards.invent("Snc09 Dock Brute", "{3}{R}", "Creature — Ogre", "", "4/4")
    private val relic = Cards.invent("Snc09 Brass Relic", "{4}", "Artifact", "")
    private val idol = Cards.invent("Snc09 Gilded Idol", "{5}", "Artifact", "")
    private val charm = Cards.invent("Snc09 Copper Charm", "{2}", "Artifact", "")

    // CR 607.2a, 707.12, 603.2: the first instant exiles the graveyard instant, whose copy is cast for free; the second instant doesn't trigger
    @Test
    fun bombardmentCastsACopyOfTheExiledCardOnlyForTheFirstSpell() = scenario {
        alice.battlefield(bombardment, "Mountain", "Mountain")
        alice.hand(jolt, flare)
        alice.graveyard(study)
        alice.library("Plains", "Island")
        alice.casts(jolt, paying = listOf("Mountain"))
        resolveAll()
        if (pending is Decision.ChooseObjects) answer(Answer.Objects((pending as Decision.ChooseObjects).options))
        resolveAll()
        expect {
            alice.exile has study
            alice.hand has "Plains"
            bob.life shouldBe 19
        }
        alice.casts(flare, paying = listOf("Mountain#1"))
        resolveAll()
        expect {
            alice.hand lacks "Island"
            bob.life shouldBe 17
        }
    }

    // CR 608.2d: one keyword chosen as it resolves, gained by both
    @Test
    fun brawlersGiveTheChosenKeywordToBoth() = scenario {
        alice.battlefield(brawlers, cub)
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(brawlers)
        resolveAll()
        if (pending is Decision.Targets) targets(cub)
        resolveAll()
        option("lifelink")
        expect {
            brawlers hasKeyword Keyword.Lifelink.kind
            cub hasKeyword Keyword.Lifelink.kind
            brawlers lacksKeyword Keyword.FirstStrike.kind
        }
    }

    // CR 202.3 (a land's mana value is 0 and counts), 611.2c (X fixed as it resolves), 611.2a (until your next turn)
    @Test
    fun arbiterCountsDifferentManaValuesUntilYourNextTurn() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(arbiter)
        alice.graveyard("Plains", cub, kitten)
        alice.library(tough, "Swamp")
        bob.battlefield(brute)
        bob.library("Forest")
        alice.casts(arbiter)
        resolveAll()
        alice.chooses(tough)
        resolveAll()
        // Plains 0, cub 2, kitten 1, tough 3: four mana values
        expect { card(brute).power shouldBe 0 }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        expect { card(brute).power shouldBe 0 }
    }

    // CR 611.2c: the greatest of the other creatures' powers and toughnesses
    @Test
    fun muscleGetsTheGreatestPowerOrToughness() = scenario {
        alice.battlefield(muscle, wall, tough)
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(muscle)
        resolveAll()
        expect {
            card(muscle).power shouldBe 9
            card(muscle).toughness shouldBe 9
        }
    }

    // CR 601.2f (sacrifice as an additional cost), 614.1a (exiled instead of the graveyard), once each turn
    @Test
    fun maestrosCastsFromTheGraveyardOnceBySacrificing() = scenario {
        alice.battlefield(maestros, kitten, cub, "Island", "Island")
        alice.graveyard(study, jolt)
        alice.library("Plains")
        alice.casts(study, way = permitted(study), paying = listOf("Island"), choosing = listOf(kitten))
        resolveAll()
        expect {
            alice.graveyard has kitten
            alice.exile has study
            alice.hand has "Plains"
        }
        assertFalse(Engine.options(state, alice.id).filterIsInstance<Input.Cast>().any { it.card == card(jolt) })
    }

    // CR 603.10a (lesser than the sacrificed creature as it last existed), 603.2h (only once each turn)
    @Test
    fun riveteersReturnsALesserCardOnceATurn() = scenario {
        alice.battlefield(riveteers, altar, tough, brute)
        alice.graveyard(kitten, cub)
        alice.activates(altar, choosing = listOf(tough))
        resolveAll()
        val options = decision<Decision.Targets>().options
        assertTrue(target(cub) in options)
        assertFalse(target(tough) in options)
        targets(cub)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        resolveAll()
        expect {
            alice.battlefield has cub
            card(cub).tapped shouldBe true
        }
        alice.activates(altar, choosing = listOf(brute))
        resolveAll()
        if (pending is Decision.Targets) targets(kitten)
        resolveAll()
        if (pending is Decision.May) answer(Answer.YesNo(true))
        resolveAll()
        expect { alice.graveyard has kitten }
    }

    // CR 508.1b: attacking a planeswalker with a creature of power 1 counts; a power 2 attacker alone doesn't
    @Test
    fun rigoDrawsWhenAttackingAPlaneswalker() = scenario(players = 3) {
        alice.battlefield(rigo, kitten, counters = emptyList())
        alice.library("Plains")
        bob.battlefield(walker, counters = listOf(CounterKind.Loyalty to 4))
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(kitten, defender = walker)
        resolveAll()
        expect { alice.hand has "Plains" }
    }

    // CR 508.1b: no attacker with power 1 or less, no draw
    @Test
    fun rigoDoesntDrawForBiggerAttackers() = scenario(players = 3) {
        alice.battlefield(rigo, cub)
        alice.library("Plains")
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(cub, defender = "carol")
        resolveAll()
        expect { alice.hand lacks "Plains" }
    }

    // CR 120.4a, 603.12: 7 damage to a 3/3 is 4 excess, so only an artifact of mana value 4 or less can be destroyed
    @Test
    fun infernoDestroysUpToTheExcess() = scenario {
        alice.battlefield("Swamp", "Mountain", "Forest", "Forest")
        alice.hand(inferno)
        bob.battlefield(tough, relic, idol, charm)
        alice.casts(inferno, target = tough)
        resolveAll()
        val options = decision<Decision.Targets>().options
        assertTrue(target(relic) in options)
        assertTrue(target(charm) in options)
        assertFalse(target(idol) in options)
        targets(relic)
        resolveAll()
        expect {
            bob.graveyard has tough
            bob.graveyard has relic
            bob.battlefield has idol
            bob.battlefield has charm
        }
    }
}
